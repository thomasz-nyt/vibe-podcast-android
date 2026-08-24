package com.podcastplayer.app.presentation.viewmodel

import com.podcastplayer.app.data.local.ManualDownloadEntity
import com.podcastplayer.app.data.local.ManualDownloadStatus
import com.podcastplayer.app.data.local.PlaybackProgressEntity
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Exercises [buildUnplayedEpisodesForQueue], the pure function backing
 * [PodcastViewModel.buildUnplayedEpisodesForPodcastQueue] (docs/specs/004-podcast-queue-play.md).
 *
 * This tests the extracted top-level function directly rather than constructing a
 * [PodcastViewModel] instance: the ViewModel's other constructor dependencies
 * ([com.podcastplayer.app.data.local.SavedPodcastsStorage], [com.podcastplayer.app.data.local.QueueStorage])
 * wrap real Android `Context.getSharedPreferences(...)` calls, and this project's test
 * setup has neither Robolectric nor a mocking library (see [ResilientForwardingPlayerTest]
 * / [PlayerViewModelTest]'s hand-rolled fakes) to fake a `Context`. Keeping the queue-building
 * logic dependency-free (feed fetch + progress lookup passed in as lambdas) lets it be
 * tested without either.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun picksLatestUnplayedEpisodePerPodcastInQueueOrder() = runTest(mainDispatcherRule.dispatcher) {
        val podcastA = podcast("a", feedUrl = "https://feed.example/a")
        val podcastB = podcast("b", feedUrl = "https://feed.example/b")

        // Podcast A: ep-a2 is the newest overall (3_000) but completed, so it must be
        // excluded; the newest UNPLAYED is ep-a3 (2_000), not ep-a1 (1_000).
        val epA1 = episode("ep-a1", podcastA.id, pubDateMs = 1_000L)
        val epA2 = episode("ep-a2", podcastA.id, pubDateMs = 3_000L)
        val epA3 = episode("ep-a3", podcastA.id, pubDateMs = 2_000L)

        // Podcast B: both unplayed; newest is ep-b2 (1_500).
        val epB1 = episode("ep-b1", podcastB.id, pubDateMs = 500L)
        val epB2 = episode("ep-b2", podcastB.id, pubDateMs = 1_500L)

        val episodesByFeed = mapOf(
            podcastA.feedUrl to listOf(epA1, epA2, epA3),
            podcastB.feedUrl to listOf(epB1, epB2),
        )
        val progressByPodcast = mapOf(
            podcastA.id to listOf(progress(epA2.id, podcastA.id, completed = true)),
            podcastB.id to listOf(progress(epB1.id, podcastB.id, completed = false)),
        )

        // Queue order is B then A; result must follow queue order (one episode each),
        // not alphabetical podcast id order or feed-fetch completion order.
        val result = buildUnplayedEpisodesForQueue(
            podcasts = listOf(podcastB, podcastA),
            fetchEpisodes = { feedUrl, _ -> Result.success(episodesByFeed.getValue(feedUrl)) },
            fetchProgress = { podcastId -> progressByPodcast.getValue(podcastId) },
        )

        assertEquals(listOf(epB2.id, epA3.id), result.map { it.id })
    }

    @Test
    fun skipsPodcastsWithNoEligibleEpisodeAndToleratesFetchFailures() = runTest(mainDispatcherRule.dispatcher) {
        val noFeed = podcast("no-feed", feedUrl = null)
        val failingFeed = podcast("failing", feedUrl = "https://feed.example/failing")
        val allCompleted = podcast("all-done", feedUrl = "https://feed.example/done")
        val ok = podcast("ok", feedUrl = "https://feed.example/ok")
        val epDone = episode("ep-done", allCompleted.id, pubDateMs = 1_000L)
        val epOk = episode("ep-ok", ok.id, pubDateMs = 1_000L)

        val result = buildUnplayedEpisodesForQueue(
            podcasts = listOf(noFeed, failingFeed, allCompleted, ok),
            fetchEpisodes = { feedUrl, _ ->
                when (feedUrl) {
                    failingFeed.feedUrl -> Result.failure(Exception("boom"))
                    allCompleted.feedUrl -> Result.success(listOf(epDone))
                    else -> Result.success(listOf(epOk))
                }
            },
            fetchProgress = { podcastId ->
                if (podcastId == allCompleted.id) listOf(progress(epDone.id, podcastId, completed = true))
                else emptyList()
            },
        )

        // no-feed skipped, failing skipped, all-completed skipped → only ok remains.
        assertEquals(listOf(epOk.id), result.map { it.id })
    }

    @Test
    fun picksLatestDatedEpisodeOverUndated() = runTest(mainDispatcherRule.dispatcher) {
        val podcast = podcast("a", feedUrl = "https://feed.example/a")
        val dated = episode("ep-dated", podcast.id, pubDateMs = 1_000L)
        val undated = episode("ep-undated", podcast.id, pubDateMs = null)

        val result = buildUnplayedEpisodesForQueue(
            podcasts = listOf(podcast),
            // Listed undated-first on purpose: an undated episode must never win "newest"
            // over a dated one, regardless of input order.
            fetchEpisodes = { _, _ -> Result.success(listOf(undated, dated)) },
            fetchProgress = { emptyList() },
        )

        assertEquals(listOf(dated.id), result.map { it.id })
    }

    @Test
    fun allNullDatesFallsBackToFeedFirst() = runTest(mainDispatcherRule.dispatcher) {
        val podcast = podcast("a", feedUrl = "https://feed.example/a")
        // Feeds are conventionally newest-first; with no dates to compare, the first
        // episode in document order is taken as the newest.
        val feedNewest = episode("ep-first", podcast.id, pubDateMs = null)
        val feedOlder = episode("ep-second", podcast.id, pubDateMs = null)

        val result = buildUnplayedEpisodesForQueue(
            podcasts = listOf(podcast),
            fetchEpisodes = { _, _ -> Result.success(listOf(feedNewest, feedOlder)) },
            fetchProgress = { emptyList() },
        )

        assertEquals(listOf(feedNewest.id), result.map { it.id })
    }

    @Test
    fun manualDownloadSnapshotRestoresActiveProgressAndLatestFailure() {
        val requests = listOf(
            manualDownload("queued", "episode-queued", ManualDownloadStatus.QUEUED, progress = 0f, createdAt = 1),
            manualDownload("running", "episode-running", ManualDownloadStatus.RUNNING, progress = 42f, createdAt = 2),
            manualDownload(
                "old-failure",
                "episode-old-failure",
                ManualDownloadStatus.FAILED,
                error = "old error",
                createdAt = 3,
            ),
            manualDownload(
                "new-failure",
                "episode-new-failure",
                ManualDownloadStatus.FAILED,
                error = "new error",
                createdAt = 4,
            ),
        )

        val snapshot = buildManualDownloadUiSnapshot(requests)

        assertEquals(mapOf("episode-queued" to 0f, "episode-running" to 0.42f), snapshot.progressByEpisodeId)
        assertEquals(listOf("old-failure", "new-failure"), snapshot.failedRequestIds)
        assertEquals("2 downloads failed. Latest error: new error", snapshot.failureMessage)
    }

    @Test
    fun manualDownloadSnapshotClampsProgressAndIgnoresUnknownStates() {
        val requests = listOf(
            manualDownload("low", "episode-low", ManualDownloadStatus.RUNNING, progress = -1f, createdAt = 1),
            manualDownload("high", "episode-high", ManualDownloadStatus.RUNNING, progress = 101f, createdAt = 2),
            manualDownload("unknown", "episode-unknown", status = null, progress = 50f, createdAt = 3),
        )

        val snapshot = buildManualDownloadUiSnapshot(requests)

        assertEquals(mapOf("episode-low" to 0f, "episode-high" to 1f), snapshot.progressByEpisodeId)
        assertEquals(emptyList<String>(), snapshot.failedRequestIds)
        assertNull(snapshot.failureMessage)
    }

    private fun podcast(id: String, feedUrl: String?) = Podcast(
        id = id,
        title = id,
        artist = "artist",
        artworkUrl = null,
        feedUrl = feedUrl,
    )

    private fun episode(id: String, podcastId: String, pubDateMs: Long?) = Episode(
        id = id,
        podcastId = podcastId,
        title = id,
        description = null,
        pubDate = pubDateMs?.let(::Date),
        audioUrl = "https://example.com/$id.mp3",
        duration = null,
    )

    private fun progress(episodeId: String, podcastId: String, completed: Boolean) = PlaybackProgressEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        positionMs = 0L,
        durationMs = 0L,
        completed = completed,
        lastPlayedAtMs = 0L,
    )

    private fun manualDownload(
        requestId: String,
        episodeId: String,
        status: ManualDownloadStatus?,
        progress: Float = 0f,
        createdAt: Long,
        error: String? = null,
    ) = ManualDownloadEntity(
        requestId = requestId,
        episodeId = episodeId,
        podcastId = "podcast",
        podcastTitle = "Podcast",
        title = episodeId,
        description = null,
        pubDate = null,
        audioUrl = "https://example.com/$episodeId.mp3",
        duration = null,
        status = status?.name ?: "UNKNOWN",
        progressPercent = progress,
        errorMessage = error,
        createdAtMs = createdAt,
    )
}
