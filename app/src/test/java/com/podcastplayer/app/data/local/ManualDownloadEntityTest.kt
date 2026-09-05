package com.podcastplayer.app.data.local

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualDownloadEntityTest {

    @Test
    fun toEpisodeRestoresPersistedDownloadInput() {
        val request = ManualDownloadEntity(
            requestId = "request",
            episodeId = "episode",
            podcastId = "podcast",
            podcastTitle = "Podcast title",
            title = "Episode title",
            description = "Description",
            pubDate = 1234L,
            audioUrl = "https://example.com/episode.mp3",
            duration = 5678L,
            createdAtMs = 9999L,
        )

        val episode = request.toEpisode()

        assertEquals("episode", episode.id)
        assertEquals("podcast", episode.podcastId)
        assertEquals("Episode title", episode.title)
        assertEquals("Description", episode.description)
        assertEquals(Date(1234L), episode.pubDate)
        assertEquals("https://example.com/episode.mp3", episode.audioUrl)
        assertEquals(5678L, episode.duration)
    }

    @Test
    fun toEpisodePreservesNullableMetadata() {
        val request = ManualDownloadEntity(
            requestId = "request",
            episodeId = "episode",
            podcastId = "podcast",
            podcastTitle = null,
            title = "Episode title",
            description = null,
            pubDate = null,
            audioUrl = "https://example.com/episode.mp3",
            duration = null,
            createdAtMs = 9999L,
        )

        val episode = request.toEpisode()

        assertNull(episode.description)
        assertNull(episode.pubDate)
        assertNull(episode.duration)
    }
}
