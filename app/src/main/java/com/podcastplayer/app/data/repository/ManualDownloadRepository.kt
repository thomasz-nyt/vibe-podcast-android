package com.podcastplayer.app.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.ManualDownloadEntity
import com.podcastplayer.app.data.local.MediaIdentity
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.service.ManualDownloadWorker
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.guava.await

class ManualDownloadRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao
        get() = DatabaseProvider.getDatabase(appContext).manualDownloadDao()
    private val workManager
        get() = WorkManager.getInstance(appContext)

    fun observeAll(): Flow<List<ManualDownloadEntity>> = dao.observeAll()

    suspend fun enqueue(episode: Episode, podcastTitle: String?) {
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
        )
        dao.insert(request)

        try {
            enqueueWork(request, ExistingWorkPolicy.REPLACE)
        } catch (t: Throwable) {
            dao.deleteByRequestId(request.requestId)
            throw t
        }
    }

    /** Repairs the tiny DB-insert/WorkManager-enqueue gap if the process died between them. */
    suspend fun resumePending() {
        dao.getActive().forEach { request ->
            enqueueWork(request, ExistingWorkPolicy.KEEP)
        }
    }

    suspend fun remove(requestId: String) {
        dao.deleteByRequestId(requestId)
    }

    private suspend fun enqueueWork(
        request: ManualDownloadEntity,
        policy: ExistingWorkPolicy,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val work = OneTimeWorkRequestBuilder<ManualDownloadWorker>()
            .setInputData(workDataOf(ManualDownloadWorker.KEY_REQUEST_ID to request.requestId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(ManualDownloadWorker.WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            uniqueWorkName(request.episodeId),
            policy,
            work,
        ).result.await()
    }

    private fun uniqueWorkName(episodeId: String): String {
        return "$UNIQUE_WORK_PREFIX.${MediaIdentity.rss(episodeId).sha256}"
    }

    companion object {
        private const val UNIQUE_WORK_PREFIX = "vibe.manual-download"
    }
}
