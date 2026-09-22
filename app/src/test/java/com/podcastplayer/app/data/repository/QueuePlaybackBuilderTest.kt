package com.podcastplayer.app.data.repository

import com.podcastplayer.app.data.local.FeedSnapshot
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePlaybackBuilderTest {
    private fun episode(id: String, time: Long = 2L) = Episode(
        id, "show", id, null, Date(time), "https://example.com/$id.mp3", null,
    )
    private val show = Podcast("show", "Show", "Artist", null, "https://example.com/feed")
    private val newest = episode("new")
    private val old = episode("old", 1L)
    private fun local(episode: Episode) = episode.copy(isDownloaded = true, localPath = "/download/${episode.id}")

    @Test fun onlinePrefersExactDownloadAndPreservesStreamingUrl() = runTest {
        val result = QueuePlaybackBuilder(
            fetch = { _, _ -> Result.success(listOf(newest, old)) }, saved = { _, _ -> null },
            progress = { emptyList() }, downloads = { listOf(local(newest), local(old)) },
        ).build(listOf(show))
        assertEquals(local(newest), result.episodes.single())
    }

    @Test fun coldOfflineUsesTimestampedSnapshotWithoutFetching() = runTest {
        val result = QueuePlaybackBuilder(
            fetch = { _, _ -> error("Offline must not fetch") },
            saved = { _, _ -> FeedSnapshot(listOf(newest, old), 123L) },
            progress = { emptyList() }, downloads = { listOf(local(newest)) }, online = { false },
        ).build(listOf(show))
        assertEquals(local(newest), result.episodes.single())
        assertEquals(123L, result.shows.single().cachedAtMs)
        assertTrue(result.shows.single().message!!.contains("Offline"))
    }

    @Test fun failedRefreshNeverSubstitutesOlderDownloadedEpisode() = runTest {
        val result = QueuePlaybackBuilder(
            fetch = { _, _ -> Result.failure(Exception("Timeout")) },
            saved = { _, _ -> FeedSnapshot(listOf(newest, old), 123L) },
            progress = { emptyList() }, downloads = { listOf(local(old)) },
        ).build(listOf(show))
        assertTrue(result.episodes.isEmpty())
        assertNotNull(result.shows.single().message)
    }

    @Test fun onlineMissingFileStreamsButOfflineMissingFileIsUnavailable() = runTest {
        for (online in listOf(true, false)) {
            val result = QueuePlaybackBuilder(
                fetch = { _, _ -> Result.success(listOf(newest)) },
                saved = { _, _ -> FeedSnapshot(listOf(newest), 123L) },
                progress = { emptyList() }, downloads = { emptyList() }, online = { online },
            ).build(listOf(show))
            assertEquals(if (online) 1 else 0, result.episodes.size)
            if (online) assertFalse(result.episodes.single().isDownloaded)
        }
    }

    @Test fun mixedShowsRetainQueueOrderAndReportUnavailableShows() = runTest {
        val shows = listOf("b", "missing", "a").map { show.copy(id = it) }
        val result = QueuePlaybackBuilder(
            fetch = { _, id -> if (id == "missing") Result.failure(Exception("404"))
                else Result.success(listOf(newest.copy(id = id, podcastId = id))) },
            saved = { _, _ -> null }, progress = { emptyList() }, downloads = { emptyList() },
        ).build(shows)
        assertEquals(listOf("b", "a"), result.episodes.map { it.id })
        assertEquals(listOf("b", "missing", "a"), result.shows.map { it.podcastId })
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotConvertedIntoUnavailableShow() = runTest {
        QueuePlaybackBuilder(
            fetch = { _, _ -> Result.failure(CancellationException()) }, saved = { _, _ -> null },
            progress = { emptyList() }, downloads = { emptyList() },
        ).build(listOf(show))
    }
}
