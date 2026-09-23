package com.podcastplayer.app.data.repository

import com.podcastplayer.app.data.local.FeedSnapshot
import com.podcastplayer.app.data.local.PlaybackProgressEntity
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

data class QueueShowResult(
    val podcastId: String,
    val title: String,
    val episode: Episode? = null,
    val cachedAtMs: Long? = null,
    val message: String? = null,
)

data class QueuePlaybackResult(val shows: List<QueueShowResult>) {
    val episodes: List<Episode> get() = shows.mapNotNull { it.episode }
}

/** Select first, then resolve that exact identity. Cached feeds must have a readable local payload. */
class QueuePlaybackBuilder(
    private val fetch: suspend (String, String) -> Result<List<Episode>>,
    private val saved: suspend (String, String) -> FeedSnapshot?,
    private val progress: suspend (String) -> List<PlaybackProgressEntity>,
    private val downloads: suspend (String) -> List<Episode>,
    private val online: () -> Boolean = { true },
) {
    suspend fun build(podcasts: List<Podcast>): QueuePlaybackResult = coroutineScope {
        val slots = Semaphore(4)
        QueuePlaybackResult(podcasts.map { podcast -> async { buildShow(podcast, slots) } }.awaitAll())
    }

    private suspend fun buildShow(podcast: Podcast, slots: Semaphore): QueueShowResult {
        fun unavailable(message: String, cachedAtMs: Long? = null) =
            QueueShowResult(podcast.id, podcast.title, cachedAtMs = cachedAtMs, message = message)
        val feed = podcast.feedUrl ?: return unavailable("No feed URL")
        return try {
            val fresh = if (online()) {
                withTimeoutOrNull(FEED_REFRESH_LIMIT_MS) {
                    slots.withPermit { fetch(feed, podcast.id) }
                } ?: Result.failure(Exception("Feed refresh timed out"))
            } else Result.failure(Exception("Offline"))
            fresh.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val cache = if (fresh.isFailure) saved(feed, podcast.id) else null
            val failure = fresh.exceptionOrNull()?.message ?: "Feed unavailable"
            val episodes = fresh.getOrNull() ?: cache?.episodes
                ?: return unavailable("$failure; no saved snapshot")
            val completed = progress(podcast.id).filter { it.completed }.mapTo(hashSetOf()) { it.episodeId }
            val selected = episodes.filterNot { it.id in completed }
                .maxByOrNull { it.pubDate?.time ?: Long.MIN_VALUE }
                ?: return unavailable("No unfinished episodes", cache?.fetchedAtMs)
            val local = downloads(podcast.id).firstOrNull { it.id == selected.id }
            if (fresh.isFailure && local == null) {
                return unavailable(
                    "$failure; newest unfinished episode is not downloaded or its file is unavailable",
                    cache?.fetchedAtMs,
                )
            }
            QueueShowResult(
                podcastId = podcast.id,
                title = podcast.title,
                episode = selected.copy(
                    imageUrl = selected.imageUrl ?: podcast.artworkUrl,
                    isDownloaded = local != null,
                    localPath = local?.localPath,
                ),
                cachedAtMs = cache?.fetchedAtMs,
                message = if (cache != null) "$failure; using downloaded episode from saved feed" else null,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            unavailable(error.message ?: "Show unavailable")
        }
    }

    private companion object {
        const val FEED_REFRESH_LIMIT_MS = 8_000L
    }
}
