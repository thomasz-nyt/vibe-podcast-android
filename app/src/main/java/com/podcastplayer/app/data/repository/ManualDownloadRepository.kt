package com.podcastplayer.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.podcastplayer.app.data.local.AppSettings
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.DownloadOrigin
import com.podcastplayer.app.data.local.ManualDownloadEntity
import com.podcastplayer.app.data.local.ManualDownloadStatus
import com.podcastplayer.app.data.local.MediaIdentity
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.service.AutoDownloadWorker
import com.podcastplayer.app.service.ManualDownloadWorker
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ManualDownloadRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao
        get() = DatabaseProvider.getDatabase(appContext).manualDownloadDao()
    private val workManager
        get() = WorkManager.getInstance(appContext)

    fun observeAll(): Flow<List<ManualDownloadEntity>> = dao.observeAll()

    fun isOnline(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun scheduleScan() = AutoDownloadWorker.enqueueImmediate(appContext, replace = true)

    suspend fun enqueue(
        episode: Episode,
        podcastTitle: String?,
        origin: DownloadOrigin = DownloadOrigin.MANUAL,
    ) = enqueueMutex.withLock {
        val existing = dao.getByEpisodeId(episode.id)
        val savedDownload = DatabaseProvider.getDatabase(appContext)
            .downloadedEpisodeDao().getEpisodeById(episode.id)
        if (origin == DownloadOrigin.MANUAL) {
            DatabaseProvider.getDatabase(appContext).downloadedEpisodeDao().pin(episode.id)
            dao.pin(episode.id)
        } else if (savedDownload?.origin == DownloadOrigin.MANUAL.name) {
            // A missing manual payload still belongs to the user. Automatic repair must not
            // overwrite that row as AUTO or let retention prune it later.
            return@withLock
        }
        if (DownloadManager(appContext).isEpisodeDownloaded(episode.id)) return@withLock
        if (origin == DownloadOrigin.AUTO && existing?.status == ManualDownloadStatus.FAILED.name) return@withLock
        if (existing != null && (origin == DownloadOrigin.AUTO ||
                existing.status != ManualDownloadStatus.FAILED.name)) {
            // A manual tap promotes ownership and removes an automatic network restriction.
            val promoted = if (origin == DownloadOrigin.MANUAL) existing.copy(origin = origin.name) else existing
            enqueueWork(promoted, if (promoted.origin != existing.origin) ExistingWorkPolicy.REPLACE
                else ExistingWorkPolicy.KEEP)
            return@withLock
        }
        val request = ManualDownloadEntity(
            requestId = UUID.randomUUID().toString(),
            episodeId = episode.id,
            podcastId = episode.podcastId,
            podcastTitle = podcastTitle,
            title = episode.title,
            description = episode.description,
            pubDate = episode.pubDate?.time,
            audioUrl = episode.audioUrl,
            duration = episode.duration,
            createdAtMs = System.currentTimeMillis(),
            origin = origin.name,
        )
        dao.insert(request)

        // Keep durable input on failure; resumePending repairs the DB/WorkManager enqueue gap.
        enqueueWork(request, ExistingWorkPolicy.REPLACE)
    }

    /** Repairs the tiny DB-insert/WorkManager-enqueue gap if the process died between them. */
    suspend fun resumePending() = enqueueMutex.withLock {
        dao.getActive().forEach { request ->
            enqueueWork(request, ExistingWorkPolicy.KEEP)
        }
    }

    suspend fun remove(requestId: String) = enqueueMutex.withLock {
        val request = dao.getByRequestId(requestId) ?: return@withLock
        dao.deleteByRequestId(requestId)
        workManager.cancelUniqueWork(uniqueWorkName(request.episodeId)).result.await()
    }

    suspend fun retry(requestId: String) = enqueueMutex.withLock {
        val request = dao.getByRequestId(requestId) ?: return@withLock
        dao.updateState(requestId, ManualDownloadStatus.QUEUED.name, 0f, null)
        enqueueWork(request, ExistingWorkPolicy.REPLACE)
    }

    suspend fun updateAutomaticConstraints() = enqueueMutex.withLock {
        dao.getActive().filter { it.origin == DownloadOrigin.AUTO.name }.forEach {
            enqueueWork(it, ExistingWorkPolicy.REPLACE)
        }
    }

    private suspend fun enqueueWork(
        request: ManualDownloadEntity,
        policy: ExistingWorkPolicy,
    ) {
        val work = workRequest(request)
        workManager.enqueueUniqueWork(
            uniqueWorkName(request.episodeId),
            policy,
            work,
        ).result.await()
    }

    internal fun workRequest(request: ManualDownloadEntity): androidx.work.OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (request.origin == DownloadOrigin.AUTO.name &&
                    !AppSettings.getInstance(appContext).autoDownloadOnCellular.value) NetworkType.UNMETERED
                else NetworkType.CONNECTED,
            )
            .build()
        return OneTimeWorkRequestBuilder<ManualDownloadWorker>()
            .setInputData(workDataOf(ManualDownloadWorker.KEY_REQUEST_ID to request.requestId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(ManualDownloadWorker.WORK_TAG)
            .build()
    }

    private fun uniqueWorkName(episodeId: String): String {
        return "$UNIQUE_WORK_PREFIX.${MediaIdentity.rss(episodeId).sha256}"
    }

    companion object {
        private val enqueueMutex = Mutex()
        private const val UNIQUE_WORK_PREFIX = "vibe.manual-download"
    }
}
