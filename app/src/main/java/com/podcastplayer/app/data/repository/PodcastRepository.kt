package com.podcastplayer.app.data.repository

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.podcastplayer.app.PodcastApplication
import com.podcastplayer.app.data.remote.RssParser
import com.podcastplayer.app.data.remote.iTunesApi
import com.podcastplayer.app.data.remote.upgradeITunesArtwork
import com.podcastplayer.app.domain.model.MediaType
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.domain.model.PodcastDto
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PodcastRepository(private val iTunesApi: iTunesApi, private val rssParser: RssParser) {

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

    suspend fun getEpisodes(feedUrl: String, podcastId: String): Result<List<Episode>> {
        return withContext(Dispatchers.IO) {
            try {
                if (UrlSource.classify(feedUrl) == UrlSource.YOUTUBE) {
                    return@withContext getYoutubeEpisodes(feedUrl, podcastId)
                }
                val url = URL(feedUrl)
                url.openStream().use { inputStream ->
                    val episodes = rssParser.parseEpisodes(inputStream, podcastId)
                    Result.success(episodes)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun getYoutubeEpisodes(url: String, podcastId: String): Result<List<Episode>> {
        if (!PodcastApplication.youtubeDlReady) {
            return Result.failure(Exception("YouTube subscription reader is still starting. Try again shortly."))
        }
        return try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--flat-playlist")
                addOption("--dump-single-json")
                addOption("--playlist-end", "25")
                addOption("--socket-timeout", "30")
            }
            val response = YoutubeDL.getInstance().execute(request)
            val root = JsonParser().parse(response.out).asJsonObject
            val entries = root.getAsJsonArray("entries") ?: return Result.success(emptyList())
            val uploader = root.stringOrNull("uploader") ?: root.stringOrNull("channel")
            val episodes = entries.mapNotNull { element ->
                val item = element.asJsonObject
                val id = item.stringOrNull("id") ?: item.stringOrNull("url") ?: return@mapNotNull null
                val webpageUrl = item.stringOrNull("webpage_url")
                    ?: item.stringOrNull("url")?.takeIf { it.startsWith("http", ignoreCase = true) }
                    ?: "https://www.youtube.com/watch?v=$id"
                Episode(
                    id = "youtube:${stableHash(webpageUrl)}",
                    podcastId = podcastId,
                    title = item.stringOrNull("title") ?: webpageUrl,
                    description = uploader,
                    pubDate = item.dateOrNull(),
                    audioUrl = webpageUrl,
                    duration = item.longOrNull("duration")?.let { it * 1000L },
                    imageUrl = item.stringOrNull("thumbnail"),
                    mediaType = MediaType.VIDEO,
                )
            }
            Result.success(episodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPodcastFromFeed(feedUrl: String): Result<Podcast> {
        return withContext(Dispatchers.IO) {
            try {
                val normalized = feedUrl.trim()
                val url = URL(normalized)
                url.openStream().use { inputStream ->
                    val metadata = rssParser.parsePodcast(inputStream, normalized)
                    Result.success(
                        Podcast(
                            id = "rss:${stableHash(normalized)}",
                            title = metadata.title,
                            artist = metadata.author,
                            artworkUrl = upgradeITunesArtwork(metadata.artworkUrl),
                            feedUrl = normalized,
                        )
                    )
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

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.longOrNull(name: String): Long? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asLong }.getOrNull()
    }

    private fun JsonObject.dateOrNull(): Date? {
        longOrNull("timestamp")?.let { return Date(it * 1000L) }
        longOrNull("release_timestamp")?.let { return Date(it * 1000L) }
        val uploadDate = stringOrNull("upload_date") ?: return null
        return runCatching {
            SimpleDateFormat("yyyyMMdd", Locale.US).parse(uploadDate)
        }.getOrNull()
    }
}
