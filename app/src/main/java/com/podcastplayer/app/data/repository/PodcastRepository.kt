package com.podcastplayer.app.data.repository

import com.podcastplayer.app.data.remote.RssParser
import com.podcastplayer.app.data.remote.iTunesApi
import com.podcastplayer.app.data.remote.upgradeITunesArtwork
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import com.podcastplayer.app.domain.model.PodcastDto
import java.net.URL
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

    private fun PodcastDto.toDomain(): Podcast {
        return Podcast(
            id = collectionId.toString(),
            title = collectionName,
            artist = artistName,
            artworkUrl = upgradeITunesArtwork(artworkUrl600 ?: artworkUrl100),
            feedUrl = feedUrl
        )
    }
}
