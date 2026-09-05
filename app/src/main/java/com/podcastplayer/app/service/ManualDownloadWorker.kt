package com.podcastplayer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.podcastplayer.app.MainActivity
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.DownloadOrigin
import com.podcastplayer.app.data.local.ManualDownloadEntity
import com.podcastplayer.app.data.local.ManualDownloadStatus
import com.podcastplayer.app.data.local.toEpisode
import com.podcastplayer.app.data.repository.DownloadManager
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Runs user-requested RSS downloads outside the UI process lifecycle. */
class ManualDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val dao
        get() = DatabaseProvider.getDatabase(applicationContext).manualDownloadDao()

    override suspend fun doWork(): Result {
        val requestId = inputData.getString(KEY_REQUEST_ID) ?: return Result.failure()
        val request = dao.getByRequestId(requestId) ?: return Result.success()

        // Promote before waiting for a concurrency slot so WorkManager may keep a large queued
        // download alive beyond the normal execution window.
        setForeground(createForegroundInfo(request, 0))
        return downloadSlots.withPermit {
            runDownload(requestId, request)
        }
    }

    private suspend fun runDownload(requestId: String, request: ManualDownloadEntity): Result {
        dao.updateState(
            requestId = requestId,
            status = ManualDownloadStatus.RUNNING.name,
            progressPercent = 0f,
            errorMessage = null,
        )

        return try {
            val downloadResult = coroutineScope {
                val progressUpdates = Channel<Int>(Channel.CONFLATED)
                val progressWriter = launch(Dispatchers.IO) {
                    for (percent in progressUpdates) {
                        dao.updateState(
                            requestId = requestId,
                            status = ManualDownloadStatus.RUNNING.name,
                            progressPercent = percent.toFloat(),
                            errorMessage = null,
                        )
                        setForeground(createForegroundInfo(request, percent))
                    }
                }
                val result = DownloadManager(applicationContext).downloadEpisode(
                    episode = request.toEpisode(),
                    podcastTitle = request.podcastTitle,
                    origin = DownloadOrigin.MANUAL,
                ) { progress ->
                    progressUpdates.trySend((progress * 100f).toInt().coerceIn(0, 100))
                }
                progressUpdates.close()
                progressWriter.join()
                result
            }

            downloadResult.fold(
                onSuccess = {
                    dao.deleteByRequestId(requestId)
                    Result.success()
                },
                onFailure = { error -> handleFailure(requestId, error) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            handleFailure(requestId, t)
        }
    }

    private suspend fun handleFailure(requestId: String, error: Throwable): Result {
        if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            dao.updateState(
                requestId = requestId,
                status = ManualDownloadStatus.QUEUED.name,
                progressPercent = 0f,
                errorMessage = null,
            )
            return Result.retry()
        }

        val message = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        dao.updateState(
            requestId = requestId,
            status = ManualDownloadStatus.FAILED.name,
            progressPercent = 0f,
            errorMessage = message,
        )
        return Result.failure()
    }

    private fun createForegroundInfo(request: ManualDownloadEntity, percent: Int): ForegroundInfo {
        ensureNotificationChannel()
        val notification = buildNotification(request.title, percent)
        val notificationId = NOTIFICATION_ID_BASE + (request.requestId.hashCode() and 0x0fffffff)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Episode downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress for podcast episode downloads"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(title: String, percent: Int): Notification {
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.ifBlank { "Downloading episode" })
            .setContentText(if (percent > 0) "Downloading… $percent%" else "Waiting to download…")
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val KEY_REQUEST_ID = "request_id"
        const val WORK_TAG = "vibe.manual-download"

        private const val MAX_RETRY_ATTEMPTS = 2
        private const val MAX_CONCURRENT_DOWNLOADS = 2
        private const val CHANNEL_ID = "episode_downloads_channel"
        private const val NOTIFICATION_ID_BASE = 5_000
        private val downloadSlots = Semaphore(MAX_CONCURRENT_DOWNLOADS)
    }
}
