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
import com.podcastplayer.app.data.local.MediaNaming
import com.podcastplayer.app.data.local.MediaIdentity
import com.podcastplayer.app.data.local.MediaPayloadAvailability
import com.podcastplayer.app.data.local.MediaPayloadProbe
import com.podcastplayer.app.data.local.MediaStoreSaver
import com.podcastplayer.app.data.local.MediaStoreScanner
import com.podcastplayer.app.data.repository.UrlDownloadRepository
import com.podcastplayer.app.data.repository.UrlDownloadStatus
import com.podcastplayer.app.domain.model.MediaType
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    // Last integer percent (0-100) reported per download id, so the yt-dlp progress
    // callback (which fires many times per second) only does work when the rounded
    // percent actually changes.
    private val lastReportedPercent = ConcurrentHashMap<String, Int>()

    // Serializes the Room write + notification update for progress ticks so they always
    // apply in the order they were emitted, even though the yt-dlp callback launches a
    // new coroutine per tick.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val progressDispatcher = Dispatchers.IO.limitedParallelism(1)

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_PUMP
        when (action) {
            ACTION_START_PUMP -> startPumpIfNeeded()
            ACTION_CANCEL -> {
                val id = intent?.getStringExtra(EXTRA_DOWNLOAD_ID)
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
            repository.requeueInterrupted()
            sweepOrphanWorkdirs()
            while (!stopRequested) {
                val nextId = nextQueuedId() ?: break
                processOne(nextId)
            }
            // Nothing left to do — stop the service.
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Delete per-id workdirs (`<downloadDir>/<id>/`) left behind by a process death
     * mid-download (crash, OOM kill) that skipped [processOne]'s `finally` cleanup.
     * Runs once at the start of a pump, before any [processOne] call in this pump
     * begins, so it can never race with an in-progress download's own workdir.
     *
     * A workdir is swept when its id has no row in an active state (QUEUED /
     * EXTRACTING_METADATA / DOWNLOADING — [requeueInterrupted] above already reset any
     * rows that were interrupted mid-flight back to QUEUED) AND it isn't referenced by
     * a COMPLETED row's `localPath` (the normal completion path always publishes to
     * MediaStore or renames the file out to `<downloadDir>/<id>.<ext>`, i.e. outside the
     * workdir — but this guards the rare case where that rename failed).
     */
    private suspend fun sweepOrphanWorkdirs() {
        val outDir = repository.downloadDir
        val dirs = outDir.listFiles { file -> file.isDirectory } ?: return
        if (dirs.isEmpty()) return

        val byId = repository.observeAll().first().associateBy { it.id }
        for (dir in dirs) {
            val row = byId[dir.name]
            if (row != null && row.status in ACTIVE_STATUSES) continue
            val referencedByCompleted = row != null &&
                row.status == UrlDownloadStatus.COMPLETED.name &&
                row.localPath?.let { File(it).parentFile == dir } == true
            if (referencedByCompleted) continue
            cleanupWorkdir(dir)
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
        lastReportedPercent.remove(id)

        val outDir = repository.downloadDir
        val workdir = File(outDir, id)
        // Set when a completed row's file ends up inside `workdir` (only happens if the
        // rename-to-final-name below fails) so cleanup never deletes a referenced file.
        var keepFile: File? = null

        try {
            // Mark extracting (a quick metadata pass) — repo already populated metadata
            // when the row was enqueued, but we do a status flip for the UI.
            repository.markExtracting(id)
            updateNotification(buildProgressNotification(entity.title, 0f, "Preparing…"))

            // Reuse an already-on-disk copy (same install, or a previous install once
            // the media read permission is granted) instead of re-running yt-dlp.
            val requestedType = MediaType.fromTag(entity.mediaType)
            val expectedName = MediaNaming.urlDisplayName(
                uploader = entity.uploader,
                title = entity.title,
                extension = if (requestedType == MediaType.VIDEO) "mp4" else "mp3",
                identity = MediaIdentity.url(entity.id),
            )
            when (val reusable = MediaStoreScanner(applicationContext)
                .findExisting(isVideo = requestedType == MediaType.VIDEO, expectedDisplayName = expectedName)
            ) {
                is MediaStoreScanner.FindExistingResult.Found -> {
                    when (MediaPayloadProbe(applicationContext).probe(reusable.item.uriString)) {
                        is MediaPayloadAvailability.Available -> {
                            repository.markCompleted(id, reusable.item.uriString, reusable.item.sizeBytes)
                            updateNotification(buildCompletedNotification(entity.title))
                            return
                        }
                        is MediaPayloadAvailability.PermissionRequired -> {
                            repository.markFailed(id, "Media access is required to reuse the existing file.")
                            return
                        }
                        is MediaPayloadAvailability.Missing,
                        is MediaPayloadAvailability.Unreadable -> Unit
                    }
                }
                is MediaStoreScanner.FindExistingResult.Failed -> {
                    repository.markFailed(id, "Could not inspect existing media: ${reusable.message}")
                    return
                }
                is MediaStoreScanner.FindExistingResult.PermissionRequired,
                MediaStoreScanner.FindExistingResult.NotFound -> Unit
            }

            if (!PodcastApplication.youtubeDlReady) {
                repository.markFailed(id, "Downloader not ready. Reopen the app and try again.")
                return
            }

            workdir.mkdirs()
            val request = repository.buildDownloadRequest(entity, workdir)

            repository.markDownloading(id, 0f)

            // yt-dlp progress: 0..100, eta seconds, raw line. Fires many times per
            // second; only act when the rounded percent actually changes, and run the
            // Room write + notification update on a serialized dispatcher so ticks are
            // applied in order (the callback launches a new coroutine per tick, which
            // could otherwise complete out of order and make progress jump backward).
            YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                val percent = progress.toInt().coerceIn(0, 100)
                if (lastReportedPercent.put(id, percent) == percent) return@execute
                serviceScope.launch(progressDispatcher) {
                    repository.markDownloading(id, progress)
                    updateNotification(
                        buildProgressNotification(
                            title = entity.title,
                            progress = progress,
                            status = "Downloading… $percent%",
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

            // Try to publish to MediaStore (Android 10+) so VLC, Files, and the
            // Gallery can discover the file. Falls back to app-private storage on
            // older devices.
            val displayName = buildDisplayName(entity.id, entity.title, entity.uploader, produced.extension)
            val saved = publishToMediaStore(produced, requestedType, displayName)

            if (saved != null) {
                repository.markCompleted(id, saved.uri.toString(), saved.sizeBytes)
                produced.delete()
            } else {
                // Move into a flat name to keep paths predictable across reboots.
                val finalFile = File(outDir, "${id}.${produced.extension}")
                if (finalFile.exists()) finalFile.delete()
                val moved = produced.renameTo(finalFile)
                val output = if (moved) finalFile else produced
                if (!moved) keepFile = output // still inside workdir; don't let cleanup delete it
                repository.markCompleted(id, output.absolutePath, output.length())
            }

            updateNotification(
                buildCompletedNotification(entity.title)
            )
        } catch (e: YoutubeDL.CanceledException) {
            repository.markCanceled(id)
        } catch (e: Throwable) {
            Log.e(TAG, "Download $id failed", e)
            repository.markFailed(id, e.message ?: e.javaClass.simpleName)
        } finally {
            // Runs on every path — success, failure, cancel, and early returns — so a
            // partial/interrupted download never leaks its workdir.
            cleanupWorkdir(workdir, keep = keepFile)
            activeJobs.remove(id)
            processIds.remove(id)
            lastReportedPercent.remove(id)
        }
    }

    /**
     * Delete [workdir] and everything in it, except [keep] (a file that a COMPLETED
     * row's `localPath` still points to — only non-null in the rare case where moving
     * the produced file out of the workdir failed). Safe to call on a dir that doesn't
     * exist or is already empty.
     */
    private fun cleanupWorkdir(workdir: File, keep: File? = null) {
        workdir.listFiles()?.forEach { file -> if (file != keep) file.delete() }
        if (keep == null) workdir.delete()
    }

    /**
     * Publish [produced] into MediaStore (audio or video). Returns null on API < 29
     * or if MediaStore write fails, so the caller can fall back to a local file.
     */
    private fun publishToMediaStore(
        produced: File,
        mediaType: MediaType,
        displayName: String,
    ): MediaStoreSaver.SavedMedia? {
        if (!MediaStoreSaver.isSupported()) return null
        val ext = produced.extension.lowercase()
        return when (mediaType) {
            MediaType.AUDIO -> {
                val mime = when (ext) {
                    "mp3" -> "audio/mpeg"
                    "m4a", "aac" -> "audio/mp4"
                    "ogg", "oga" -> "audio/ogg"
                    "opus" -> "audio/opus"
                    "wav" -> "audio/wav"
                    else -> "audio/mpeg"
                }
                MediaStoreSaver.saveAudioFromFile(applicationContext, displayName, mime, produced)
            }
            MediaType.VIDEO -> {
                val mime = when (ext) {
                    "mp4", "m4v" -> "video/mp4"
                    "webm" -> "video/webm"
                    "mkv" -> "video/x-matroska"
                    else -> "video/mp4"
                }
                MediaStoreSaver.saveVideoFromFile(applicationContext, displayName, mime, produced)
            }
        }
    }

    /**
     * Display name written to MediaStore. Prefixes the uploader (channel / poster)
     * when available so files browsed from VLC / Gallery read e.g.
     * "Lex Fridman - Joe Rogan #1.mp4" rather than just the bare video title.
     * Building/sanitizing live in [MediaNaming] so reuse matching stays in sync.
     */
    private fun buildDisplayName(id: String, title: String, uploader: String?, extension: String): String =
        MediaNaming.urlDisplayName(uploader, title, extension, MediaIdentity.url(id))

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

        private val ACTIVE_STATUSES = setOf(
            UrlDownloadStatus.QUEUED.name,
            UrlDownloadStatus.EXTRACTING_METADATA.name,
            UrlDownloadStatus.DOWNLOADING.name,
        )

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
