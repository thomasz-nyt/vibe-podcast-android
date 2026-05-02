package com.podcastplayer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.podcastplayer.app.MainActivity
import com.podcastplayer.app.PodcastApplication
import com.podcastplayer.app.data.repository.UrlDownloadRepository
import com.podcastplayer.app.data.repository.UrlDownloadStatus
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service that drains the URL download queue (issue #33).
 *
 * Lifecycle:
 * - Started via [enqueue], which inserts a row in the DB and (re)kicks the service.
 * - Pulls newly-queued items via the repository's flow and processes them
 *   serially. Concurrency is intentionally 1 so we don't oversubscribe the bundled
 *   yt-dlp / ffmpeg processes on lower-end devices.
 * - Notifies the user via a single foreground notification that updates with progress.
 * - Stops itself when there are no more in-flight items.
 *
 * The repository owns DB state; this service just drives yt-dlp and reports back.
 */
class UrlDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notifications by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }
    private val repository by lazy { UrlDownloadRepository(applicationContext) }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val processIds = ConcurrentHashMap<String, String>()
    private val pumpMutex = Mutex()
    @Volatile private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_PUMP
        when (action) {
            ACTION_START_PUMP -> startPumpIfNeeded()
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_DOWNLOAD_ID)
                if (id != null) cancelDownload(id)
            }
        }
        return START_STICKY
    }

    private fun startPumpIfNeeded() {
        // Promote to foreground BEFORE starting any work so we don't get killed.
        startInForeground(idle())
        serviceScope.launch { pumpQueue() }
    }

    /**
     * Drains the queue: while there are non-terminal rows, process them one at a time.
     */
    private suspend fun pumpQueue() {
        pumpMutex.withLock {
            while (!stopRequested) {
                val nextId = nextQueuedId() ?: break
                processOne(nextId)
            }
            // Nothing left to do — stop the service.
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun nextQueuedId(): String? {
        // Snapshot the latest list and find the oldest queued row.
        val current = repository.observeAll().first()
        return current
            .filter { it.status == UrlDownloadStatus.QUEUED.name }
            .minByOrNull { it.createdAtMs }
            ?.id
    }

    private suspend fun processOne(id: String) {
        val entity = repository.get(id) ?: return
        val processId = "url-dl-$id"
        processIds[id] = processId

        try {
            // Mark extracting (a quick metadata pass) — repo already populated metadata
            // when the row was enqueued, but we do a status flip for the UI.
            repository.markExtracting(id)
            updateNotification(buildProgressNotification(entity.title, 0f, "Preparing…"))

            if (!PodcastApplication.youtubeDlReady) {
                repository.markFailed(id, "Downloader not ready. Reopen the app and try again.")
                return
            }

            val outDir = repository.downloadDir
            val workdir = File(outDir, id).apply { mkdirs() }

            val request = repository.buildDownloadRequest(entity, workdir)

            repository.markDownloading(id, 0f)

            // yt-dlp progress: 0..100, eta seconds, raw line.
            YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                // Coroutine launch so we don't block the streaming process.
                serviceScope.launch {
                    repository.markDownloading(id, progress)
                    updateNotification(
                        buildProgressNotification(
                            title = entity.title,
                            progress = progress,
                            status = "Downloading… ${progress.toInt()}%",
                        )
                    )
                }
            }

            // Locate the produced file. yt-dlp wrote into [workdir]; pick the largest
            // playable file (the original media, not metadata sidecars).
            val produced = pickProducedFile(workdir)
            if (produced == null || !produced.exists()) {
                repository.markFailed(id, "Download finished but no output file was produced.")
                return
            }

            // Move into a flat name to keep paths predictable across reboots.
            val finalFile = File(outDir, "${id}.${produced.extension}")
            if (finalFile.exists()) finalFile.delete()
            val moved = produced.renameTo(finalFile)
            val output = if (moved) finalFile else produced

            repository.markCompleted(id, output)
            // Best-effort cleanup of leftover sidecar files.
            workdir.listFiles()?.forEach { it.delete() }
            workdir.delete()

            updateNotification(
                buildCompletedNotification(entity.title)
            )
        } catch (e: YoutubeDL.CanceledException) {
            repository.markCanceled(id)
        } catch (e: Throwable) {
            Log.e(TAG, "Download $id failed", e)
            repository.markFailed(id, e.message ?: e.javaClass.simpleName)
        } finally {
            activeJobs.remove(id)
            processIds.remove(id)
        }
    }

    private fun pickProducedFile(dir: File): File? {
        val files = dir.listFiles()?.toList().orEmpty()
        if (files.isEmpty()) return null
        // Prefer mp4/mp3/m4a/webm in that order; fall back to largest.
        val preferred = listOf("mp4", "mp3", "m4a", "webm", "opus", "aac")
        val byPref = preferred.firstNotNullOfOrNull { ext ->
            files.firstOrNull { it.extension.equals(ext, ignoreCase = true) }
        }
        return byPref ?: files.maxByOrNull { it.length() }
    }

    private fun cancelDownload(id: String) {
        val processId = processIds[id]
        if (processId != null) {
            try {
                YoutubeDL.getInstance().destroyProcessById(processId)
            } catch (_: Throwable) {}
        }
        serviceScope.launch { repository.markCanceled(id) }
    }

    override fun onDestroy() {
        stopRequested = true
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── notifications ───────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = notifications.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "URL downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Progress for videos saved from URLs"
                    setShowBadge(false)
                }
                notifications.createNotificationChannel(channel)
            }
        }
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(notification: Notification) {
        notifications.notify(NOTIFICATION_ID, notification)
    }

    private fun idle(): Notification = baseBuilder()
        .setContentTitle("Vibe — preparing download")
        .setContentText("Setting up…")
        .setProgress(100, 0, true)
        .setOngoing(true)
        .build()

    private fun buildProgressNotification(title: String, progress: Float, status: String): Notification {
        return baseBuilder()
            .setContentTitle(title.ifBlank { "Downloading" })
            .setContentText(status)
            .setProgress(100, progress.toInt().coerceIn(0, 100), false)
            .setOngoing(true)
            .build()
    }

    private fun buildCompletedNotification(title: String): Notification {
        return baseBuilder()
            .setContentTitle("Saved — $title")
            .setContentText("Tap to open the home screen.")
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .setSilent(true)
    }

    companion object {
        private const val TAG = "UrlDownloadService"
        private const val CHANNEL_ID = "url_downloads_channel"
        private const val NOTIFICATION_ID = 4242

        const val ACTION_START_PUMP = "com.podcastplayer.app.action.START_PUMP"
        const val ACTION_CANCEL = "com.podcastplayer.app.action.CANCEL"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun startPump(context: Context) {
            val intent = Intent(context, UrlDownloadService::class.java)
                .setAction(ACTION_START_PUMP)
            // Foreground services started from background require startForegroundService on O+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context, downloadId: String) {
            val intent = Intent(context, UrlDownloadService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            context.startService(intent)
        }
    }
}
