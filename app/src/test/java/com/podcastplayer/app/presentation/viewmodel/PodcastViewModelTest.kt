package com.podcastplayer.app.presentation.viewmodel

import com.podcastplayer.app.data.local.PlaybackProgressEntity
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.Podcast
import java.util.Date
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun buildsPlaylistWithAllUnplayedEpisodesOldestFirstInQueueOrder() = runTest(mainDispatcherRule.dispatcher) {
        val podcastA = podcast("a", feedUrl = "https://feed.example/a")
        val podcastB = podcast("b", feedUrl = "https://feed.example/b")

        // Podcast A: ep-a2 is completed and must be excluded. ep-a1/ep-a3 are unplayed
        // and published out of chronological order, to verify oldest -> newest sorting.
        val epA1 = episode("ep-a1", podcastA.id, pubDateMs = 1_000L)
        val epA2 = episode("ep-a2", podcastA.id, pubDateMs = 3_000L)
        val epA3 = episode("ep-a3", podcastA.id, pubDateMs = 2_000L)

        // Podcast B: both episodes unplayed (no progress row at all for one of them).
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

        // Queue order is B then A; the result must follow queue order, not alphabetical
        // podcast id order or feed-fetch completion order.
        val result = buildUnplayedEpisodesForQueue(
            podcasts = listOf(podcastB, podcastA),
            fetchEpisodes = { feedUrl, _ -> Result.success(episodesByFeed.getValue(feedUrl)) },
            fetchProgress = { podcastId -> progressByPodcast.getValue(podcastId) },
        )

        assertEquals(listOf(epB1.id, epB2.id, epA1.id, epA3.id), result.map { it.id })
    }

    @Test
    fun skipsPodcastsWithNoFeedUrlAndToleratesFetchFailures() = runTest(mainDispatcherRule.dispatcher) {
        val noFeed = podcast("no-feed", feedUrl = null)
        val failingFeed = podcast("failing", feedUrl = "https://feed.example/failing")
        val ok = podcast("ok", feedUrl = "https://feed.example/ok")
        val epOk = episode("ep-ok", ok.id, pubDateMs = 1_000L)

        val result = buildUnplayedEpisodesForQueue(
            podcasts = listOf(noFeed, failingFeed, ok),
            fetchEpisodes = { feedUrl, _ ->
                if (feedUrl == failingFeed.feedUrl) Result.failure(Exception("boom"))
                else Result.success(listOf(epOk))
            },
            fetchProgress = { emptyList() },
        )

        assertEquals(listOf(epOk.id), result.map { it.id })
    }

    @Test
    fun episodesWithNoPubDateSortLast() = runTest(mainDispatcherRule.dispatcher) {
        val podcast = podcast("a", feedUrl = "https://feed.example/a")
        val dated = episode("ep-dated", podcast.id, pubDateMs = 1_000L)
        val undated = episode("ep-undated", podcast.id, pubDateMs = null)

        val result = buildUnplayedEpisodesForQueue(
            podcasts = listOf(podcast),
            // Listed undated-first on purpose, to confirm sorting (not input order) decides.
            fetchEpisodes = { _, _ -> Result.success(listOf(undated, dated)) },
            fetchProgress = { emptyList() },
        )

        assertEquals(listOf(dated.id, undated.id), result.map { it.id })
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
}
