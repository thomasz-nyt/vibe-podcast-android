package com.podcastplayer.app.data.repository

import com.podcastplayer.app.data.remote.RssParser
import com.podcastplayer.app.data.remote.iTunesApi
import com.podcastplayer.app.data.remote.upgradeITunesArtwork
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.domain.model.PodcastDto
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PodcastRepository(private val iTunesApi: iTunesApi, private val rssParser: RssParser) {

    private val episodesCache = TtlCache<String, List<Episode>>()
    private val podcastFeedCache = TtlCache<String, Podcast>()

    suspend fun searchPodcasts(query: String): Result<List<Podcast>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = iTunesApi.searchPodcasts(query)
                if (response.isSuccessful && response.body() != null) {
                    val podcasts = response.body()!!.results.map { it.toDomain() }
                    Result.success(podcasts)
                } else {
                    Result.failure(Exception("Failed to search podcasts: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Fetch + parse the RSS feed at [feedUrl]. Results are cached in-memory (keyed by
     * [feedUrl]) for [CACHE_TTL_MS], so re-opening an episode list doesn't re-download
     * the feed every time. Pass [forceRefresh] to bypass and repopulate the cache
     * (wired to the Episodes screen's manual Refresh action).
     */
    suspend fun getEpisodes(
        feedUrl: String,
        podcastId: String,
        forceRefresh: Boolean = false,
    ): Result<List<Episode>> {
        return withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                episodesCache.get(feedUrl, CACHE_TTL_MS)?.let { return@withContext Result.success(it) }
            }
            try {
                val connection = HttpConnections.openWithRedirects(
                    feedUrl,
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                )
                try {
                    connection.inputStream.use { inputStream ->
                        val episodes = rssParser.parseEpisodes(inputStream, podcastId)
                        episodesCache.put(feedUrl, episodes)
                        Result.success(episodes)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getPodcastFromFeed(feedUrl: String): Result<Podcast> {
        return withContext(Dispatchers.IO) {
            val normalized = feedUrl.trim()
            podcastFeedCache.get(normalized, CACHE_TTL_MS)?.let { return@withContext Result.success(it) }
            try {
                val connection = HttpConnections.openWithRedirects(
                    normalized,
                    connectTimeoutMs = CONNECT_TIMEOUT_MS,
                    readTimeoutMs = READ_TIMEOUT_MS,
                )
                try {
                    connection.inputStream.use { inputStream ->
                        val metadata = rssParser.parsePodcast(inputStream, normalized)
                        val podcast = Podcast(
                            id = "rss:${stableHash(normalized)}",
                            title = metadata.title,
                            artist = metadata.author,
                            artworkUrl = upgradeITunesArtwork(metadata.artworkUrl),
                            feedUrl = normalized,
                        )
                        podcastFeedCache.put(normalized, podcast)
                        Result.success(podcast)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun PodcastDto.toDomain(): Podcast {
        return Podcast(
            id = collectionId.toString(),
            title = collectionName,
            artist = artistName,
            artworkUrl = upgradeITunesArtwork(artworkUrl600 ?: artworkUrl100),
            feedUrl = feedUrl
        )
    }

    private fun stableHash(value: String): String {
        return MessageDigest.getInstance("SHA-1")
            .digest(value.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val CACHE_TTL_MS = 15 * 60 * 1000L
    }
}

/** Simple in-memory TTL cache. Entries older than the TTL passed to [get] are treated as a miss. */
private class TtlCache<K, V> {
    private val entries = ConcurrentHashMap<K, Pair<Long, V>>()

    fun get(key: K, ttlMs: Long): V? {
        val (timestamp, value) = entries[key] ?: return null
        if (System.currentTimeMillis() - timestamp > ttlMs) {
            entries.remove(key)
            return null
        }
        return value
    }

    fun put(key: K, value: V) {
        entries[key] = System.currentTimeMillis() to value
    }
}
