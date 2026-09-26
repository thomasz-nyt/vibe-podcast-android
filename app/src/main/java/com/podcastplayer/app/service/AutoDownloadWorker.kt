package com.podcastplayer.app.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.podcastplayer.app.data.local.AppSettings
import com.podcastplayer.app.data.local.AutoDownloadRetentionPlanner
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.DownloadOrigin
import com.podcastplayer.app.data.local.FeedSnapshotStorage
import com.podcastplayer.app.data.local.QueueStorage
import com.podcastplayer.app.data.local.RestoreEpisodeCandidateForRetention
import com.podcastplayer.app.data.local.SavedPodcastsStorage
import com.podcastplayer.app.data.remote.RssParser
import com.podcastplayer.app.data.remote.iTunesApi
import com.podcastplayer.app.data.repository.DownloadManager
import com.podcastplayer.app.data.repository.DownloadRetryPolicy
import com.podcastplayer.app.data.repository.ManualDownloadRepository
import com.podcastplayer.app.data.repository.PodcastRepository
import com.podcastplayer.app.data.repository.UrlDownloadRepository
import com.podcastplayer.app.data.repository.UrlMetadata
import com.podcastplayer.app.data.repository.UrlSource
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

/**
 * Periodic background worker that auto-downloads new episodes for podcasts the
 * user opted into.
 *
 * Sources of subscribed podcasts:
 *  - [SavedPodcastsStorage] entries with `autoDownload = true`
 *  - [QueueStorage] queues with `autoDownload = true` (transitively, all podcasts in them)
 *
 * For each candidate podcast we pull the RSS feed, then for episodes that are
 * (a) newer than [MAX_AGE_DAYS] days, (b) not already in `downloaded_episodes`,
 * we enqueue a durable transfer via [ManualDownloadRepository]. This keeps the worker bounded
 * even when a feed has years of backlog.
 */
class AutoDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!scanMutex.tryLock()) return Result.retry()
        val monitor = AutoDownloadMonitor(applicationContext)
        return try {
            runAutoDownload(applicationContext)
            monitor.recordSuccess()
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            monitor.recordFailure(error.message ?: "Feed check failed")
            if (error is java.io.IOException && runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            scanMutex.unlock()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "vibe.autodownload"
        const val IMMEDIATE_WORK_NAME = "vibe.autodownload.immediate"
        const val DEFAULT_INTERVAL_HOURS = 6L
        private val scanMutex = Mutex()

        fun enqueueIfStale(context: Context) {
            if (System.currentTimeMillis() - AutoDownloadMonitor(context).lastSuccess >=
                TimeUnit.HOURS.toMillis(DEFAULT_INTERVAL_HOURS)) enqueueImmediate(context)
        }

        fun enqueueImmediate(context: Context, replace: Boolean = false) {
            val cellular = AppSettings.getInstance(context).autoDownloadOnCellular.value
            val request = immediateRequest(cellular)
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request,
            )
        }

        internal fun immediateRequest(allowCellular: Boolean) = OneTimeWorkRequestBuilder<AutoDownloadWorker>()
            .setConstraints(networkConstraints(allowCellular))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        internal fun periodicRequest(allowCellular: Boolean) = PeriodicWorkRequestBuilder<AutoDownloadWorker>(
            DEFAULT_INTERVAL_HOURS, TimeUnit.HOURS,
        )
            .setConstraints(networkConstraints(allowCellular))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        internal fun networkConstraints(allowCellular: Boolean) = Constraints.Builder()
            .setRequiredNetworkType(if (allowCellular) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .build()

        /** Drop episodes older than this when auto-downloading; avoid pulling a 5-year backlog. */
        private const val MAX_AGE_DAYS = 14L

        /**
         * Schedule or update the six-hour cadence without resetting the existing enqueue time.
         * Reads the cellular preference from [AppSettings].
         */
        fun enqueuePeriodic(context: Context) {
            val cellular = AppSettings.getInstance(context).autoDownloadOnCellular.value
            enqueueInternal(context, cellular, ExistingPeriodicWorkPolicy.UPDATE)
        }

        /**
         * Force a re-schedule with the given network constraint. Used when the
         * user flips the "auto-download on cellular" setting so the next run
         * picks up the new policy.
         */
        fun reschedule(context: Context, allowCellular: Boolean) {
            enqueueInternal(context, allowCellular, ExistingPeriodicWorkPolicy.UPDATE)
            enqueueImmediate(context, replace = true)
        }

        private fun enqueueInternal(
            context: Context,
            allowCellular: Boolean,
            policy: ExistingPeriodicWorkPolicy,
        ) {
            val request = periodicRequest(allowCellular)

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                policy,
                request,
            )
        }

        /** Discover episodes; payload transfers run in their own durable workers. */
        private suspend fun runAutoDownload(context: Context) {
            val savedStorage = SavedPodcastsStorage(context)
            val queueStorage = QueueStorage(context)
            val saved = savedStorage.savedPodcasts.value
            val queues = queueStorage.queues.value

            // De-dupe podcasts that are auto-downloaded via either the podcast flag or
            // a queue that the podcast belongs to.
            val savedById = saved.associateBy { it.id }
            val fromPodcasts = saved.filter { it.autoDownload }
            val fromQueues = queues.filter { it.autoDownload }
                .flatMap { it.podcastIds }
                .mapNotNull { savedById[it] }
            val candidates = (fromPodcasts + fromQueues).distinctBy { it.id }
            if (candidates.isEmpty()) return

            val repository = PodcastRepository(iTunesApi.create(), RssParser(),
                FeedSnapshotStorage(
                    DatabaseProvider.getDatabase(context).feedSnapshotDao(),
                ),
            )
            val downloadManager = DownloadManager(context)
            val urlDownloadRepository = UrlDownloadRepository(context)
            val retentionLimit = AppSettings.getInstance(context).autoDownloadRetentionLimit.value
            val transfers = ManualDownloadRepository(context)
            transfers.resumePending()

            val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_AGE_DAYS)
            var queuedUrlDownloads = false

            val failures = mutableListOf<String>()
            var transientFailure = false
            for (podcast in candidates) {
                try {
                    val feedUrl = podcast.feedUrl ?: continue
                    val episodes = repository.getEpisodes(feedUrl, podcast.id, forceRefresh = true).getOrThrow()
                    val progressByEpisodeId = DatabaseProvider.getDatabase(context)
                        .playbackProgressDao()
                        .getByPodcastId(podcast.id)
                        .associateBy { it.episodeId }

                    val freshEpisodes = episodes.filter { ep ->
                        val pubMs = ep.pubDate?.time ?: 0L
                        pubMs >= cutoffMs && progressByEpisodeId[ep.id]?.completed != true
                    }

                    val selectedIds = AutoDownloadRetentionPlanner.selectEligibleEpisodes(
                        episodes = freshEpisodes.map {
                            RestoreEpisodeCandidateForRetention(it.id, requireNotNull(it.pubDate).time)
                        },
                        limit = retentionLimit,
                    ).mapTo(hashSetOf()) { it.id }

                    for (episode in freshEpisodes.filter { it.id in selectedIds }) {
                        if (UrlSource.classify(episode.audioUrl) == UrlSource.YOUTUBE) {
                            urlDownloadRepository.enqueue(
                                rawUrl = episode.audioUrl,
                                mediaType = episode.mediaType,
                                prefetchedMetadata = UrlMetadata(
                                    title = episode.title,
                                    uploader = episode.description,
                                    thumbnailUrl = episode.imageUrl,
                                    durationMs = episode.duration,
                                ),
                                origin = DownloadOrigin.AUTO,
                                podcastId = podcast.id,
                                episodePubDateMs = episode.pubDate?.time,
                            )
                            queuedUrlDownloads = true
                            continue
                        }
                        if (downloadManager.isEpisodeDownloaded(episode.id)) continue
                        val withArtwork = ensureArtwork(episode, podcast)
                        transfers.enqueue(
                            episode = withArtwork,
                            podcastTitle = podcast.title,
                            origin = DownloadOrigin.AUTO,
                        )

                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    transientFailure = transientFailure ||
                        DownloadRetryPolicy.isTransient(error)
                    failures += "${podcast.title}: ${error.message ?: "Feed unavailable"}"
                }
            }
            if (queuedUrlDownloads) urlDownloadRepository.startPump()
            if (failures.isNotEmpty()) {
                val message = failures.joinToString("; ")
                if (transientFailure) throw java.io.IOException(message) else throw IllegalStateException(message)
            }
        }

        private fun ensureArtwork(episode: Episode, podcast: Podcast): Episode {
            return if (episode.imageUrl != null) episode
            else episode.copy(imageUrl = podcast.artworkUrl)
        }
    }
}
