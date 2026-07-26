package com.podcastplayer.app.service

import android.content.Context
import com.podcastplayer.app.data.local.AppSettings
import com.podcastplayer.app.data.local.AutoDownloadRetentionPlanner
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.DownloadOrigin
import com.podcastplayer.app.data.local.RetentionCandidate
import com.podcastplayer.app.data.repository.DownloadManager
import com.podcastplayer.app.data.repository.UrlDownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Applies the global limit independently to each podcast's completed AUTO items. */
class AutoDownloadRetentionManager(private val context: Context) {

    private val database get() = DatabaseProvider.getDatabase(context)

    suspend fun previewTrimCount(newLimit: Int): Int = withContext(Dispatchers.IO) {
        autoPodcastIds().sumOf { podcastId -> candidates(podcastId, newLimit).size }
    }

    suspend fun trimAll(newLimit: Int = currentLimit()): Int = withContext(Dispatchers.IO) {
        autoPodcastIds().sumOf { trimPodcast(it, newLimit) }
    }

    suspend fun trimPodcast(podcastId: String, limit: Int = currentLimit()): Int =
        withContext(Dispatchers.IO) {
            retentionMutex.withLock {
                val prune = candidates(podcastId, limit)
                val rss = DownloadManager(context)
                val urls = UrlDownloadRepository(context)
                prune.count { item ->
                    val consent = when (item.source) {
                        RetentionCandidate.Source.RSS -> rss.deleteEpisode(item.id)
                        RetentionCandidate.Source.URL -> urls.delete(item.id)
                    }
                    // AUTO files were written by this install. If an OEM provider
                    // unexpectedly asks for consent, do not claim it was pruned.
                    consent.isEmpty()
                }
            }
        }

    private suspend fun candidates(podcastId: String, limit: Int): List<RetentionCandidate> {
        val rss = database.downloadedEpisodeDao().getAutoEpisodesByPodcast(podcastId).map {
            RetentionCandidate(
                id = it.id,
                podcastId = it.podcastId,
                publicationTimeMs = it.pubDate,
                completedTimeMs = it.downloadDate,
                isPinned = it.origin != DownloadOrigin.AUTO.name,
                source = RetentionCandidate.Source.RSS,
            )
        }
        val url = database.urlDownloadDao().getCompletedAutoByPodcast(podcastId).map {
            RetentionCandidate(
                id = it.id,
                podcastId = requireNotNull(it.podcastId),
                publicationTimeMs = it.episodePubDateMs,
                completedTimeMs = it.completedAtMs ?: it.createdAtMs,
                isPinned = it.origin != DownloadOrigin.AUTO.name,
                source = RetentionCandidate.Source.URL,
            )
        }
        return AutoDownloadRetentionPlanner.itemsToPrune(rss + url, limit)
    }

    private suspend fun autoPodcastIds(): Set<String> = buildSet {
        addAll(database.downloadedEpisodeDao().getAutoPodcastIds())
        addAll(database.urlDownloadDao().getAutoPodcastIds())
    }

    private fun currentLimit(): Int = AppSettings.getInstance(context).autoDownloadRetentionLimit.value

    companion object {
        private val retentionMutex = Mutex()
    }
}
