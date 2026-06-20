package com.podcastplayer.app.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.podcastplayer.app.data.local.AppSettings
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.QueueStorage
import com.podcastplayer.app.data.local.SavedPodcastsStorage
import com.podcastplayer.app.data.remote.RssParser
import com.podcastplayer.app.data.remote.iTunesApi
import com.podcastplayer.app.data.repository.DownloadManager
import com.podcastplayer.app.data.repository.PodcastRepository
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import java.util.concurrent.TimeUnit

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
 * we kick off a download via [DownloadManager]. This keeps the worker bounded
 * even when a feed has years of backlog.
 */
class AutoDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            runAutoDownload(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            // Periodic work: retry on next run, no point hammering on transient failures.
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "vibe.autodownload"

        /** Default cadence. Picked in product chat — 24h hits new daily-show drops next morning. */
        private val DEFAULT_INTERVAL_HOURS = 24L

        /** Drop episodes older than this when auto-downloading; avoid pulling a 5-year backlog. */
        private const val MAX_AGE_DAYS = 14L

        /**
         * Schedule the worker on app start. KEEP policy means we don't reset the
         * timer on every cold start — once enrolled, it runs on its own schedule.
         * Reads the cellular preference from [AppSettings].
         */
        fun enqueuePeriodic(context: Context) {
            val cellular = AppSettings.getInstance(context).autoDownloadOnCellular.value
            enqueueInternal(context, cellular, ExistingPeriodicWorkPolicy.KEEP)
        }

        /**
         * Force a re-schedule with the given network constraint. Used when the
         * user flips the "auto-download on cellular" setting so the next run
         * picks up the new policy.
         */
        fun reschedule(context: Context, allowCellular: Boolean) {
            enqueueInternal(context, allowCellular, ExistingPeriodicWorkPolicy.UPDATE)
        }

        private fun enqueueInternal(
            context: Context,
            allowCellular: Boolean,
            policy: ExistingPeriodicWorkPolicy,
        ) {
            val networkType = if (allowCellular) NetworkType.CONNECTED else NetworkType.UNMETERED

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoDownloadWorker>(
                DEFAULT_INTERVAL_HOURS, TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                policy,
                request,
            )
        }

        /**
         * Drive the auto-download loop directly. Exposed so the UI's "Refresh now"
         * action can trigger an immediate run.
         */
        suspend fun runAutoDownload(context: Context) {
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

            val repository = PodcastRepository(iTunesApi.create(), RssParser())
            val downloadManager = DownloadManager(context)
            val downloadedDao = DatabaseProvider.getDatabase(context).downloadedEpisodeDao()

            val cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_AGE_DAYS)

            for (podcast in candidates) {
                val feedUrl = podcast.feedUrl ?: continue
                val episodes = repository.getEpisodes(feedUrl, podcast.id).getOrNull()
                    ?: continue
                val progressByEpisodeId = DatabaseProvider.getDatabase(context)
                    .playbackProgressDao()
                    .getByPodcastId(podcast.id)
                    .associateBy { it.episodeId }

                val freshEpisodes = episodes.filter { ep ->
                    val pubMs = ep.pubDate?.time ?: 0L
                    pubMs >= cutoffMs && progressByEpisodeId[ep.id]?.completed != true
                }

                for (episode in freshEpisodes) {
                    if (downloadedDao.isEpisodeDownloaded(episode.id)) continue
                    val withArtwork = ensureArtwork(episode, podcast)
                    // Result is ignored — periodic work; we'll try again next interval.
                    downloadManager.downloadEpisode(withArtwork, podcastTitle = podcast.title)
                }
            }
        }

        private fun ensureArtwork(episode: Episode, podcast: Podcast): Episode {
            return if (episode.imageUrl != null) episode
            else episode.copy(imageUrl = podcast.artworkUrl)
        }
    }
}
