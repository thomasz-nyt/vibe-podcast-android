package com.podcastplayer.app.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.media3.common.Player
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlaybackState
import com.podcastplayer.app.service.ControllerSnapshot
import com.podcastplayer.app.service.PlaybackController
import com.podcastplayer.app.service.PlaybackControllerListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun secondTapDuringPreparationCancelsAutoplayImmediately() = runTest(mainDispatcherRule.dispatcher) {
        val controller = FakePlaybackController()
        val viewModel = PlayerViewModel(controller, null)
        runCurrent()

        viewModel.playEpisode(episode("one"), null)
        runCurrent()
        assertTrue(viewModel.playerState.value.playRequested)
        assertEquals(PlaybackState.LOADING, viewModel.playerState.value.state)

        viewModel.togglePlayPause()
        assertFalse(viewModel.playerState.value.playRequested)
        assertEquals(PlaybackState.PAUSED, viewModel.playerState.value.state)
        controller.prepares.getValue("one").complete(0L)
        advanceUntilIdle()

        assertEquals(0, controller.playRequests.size)
        assertEquals(1, controller.pauseCount)
    }

    @Test
    fun staleEpisodePreparationCannotStartAfterNewSelection() = runTest(mainDispatcherRule.dispatcher) {
        val controller = FakePlaybackController()
        val viewModel = PlayerViewModel(controller, null)
        runCurrent()

        viewModel.playEpisode(episode("old"), null)
        runCurrent()
        viewModel.playEpisode(episode("new"), null)
        runCurrent()
        controller.prepares.getValue("new").complete(0L)
        runCurrent()
        controller.prepares.getValue("old").complete(0L)
        runCurrent()

        assertEquals(listOf(2L), controller.playRequests)
        assertEquals("new", viewModel.currentEpisode.value?.id)
        controller.emit(snapshot(episode("new"), Player.STATE_READY))
    }

    @Test
    fun mediaEventsDriveBufferingReadyFocusAndErrorState() = runTest(mainDispatcherRule.dispatcher) {
        val controller = FakePlaybackController()
        val viewModel = PlayerViewModel(controller, null)
        runCurrent()
        val episode = episode("one")

        controller.emit(snapshot(episode, Player.STATE_BUFFERING, playWhenReady = true))
        assertEquals(PlaybackState.LOADING, viewModel.playerState.value.state)
        assertTrue(viewModel.playerState.value.isBuffering)

        // isPlaying=false models transient audio-focus suppression; play intent remains visible.
        controller.emit(snapshot(episode, Player.STATE_READY, playWhenReady = true, isPlaying = false))
        assertEquals(PlaybackState.PLAYING, viewModel.playerState.value.state)
        assertTrue(viewModel.playerState.value.playRequested)

        controller.emit(snapshot(episode, Player.STATE_IDLE, playbackError = "network"))
        assertEquals(PlaybackState.ERROR, viewModel.playerState.value.state)
        assertEquals("network", viewModel.playerState.value.playbackError)
    }

    @Test
    fun listenerIsRegisteredAndRemovedWithViewModel() = runTest(mainDispatcherRule.dispatcher) {
        val controller = FakePlaybackController()
        val store = ViewModelStore()
        val viewModel = PlayerViewModel(controller, null)
        store.put("player", viewModel)
        runCurrent()
        assertEquals(1, controller.addCount)

        store.clear()
        assertEquals(1, controller.removeCount)
    }

    private fun episode(id: String) = Episode(
        id = id,
        podcastId = "podcast",
        title = id,
        description = null,
        pubDate = null,
        audioUrl = "https://example.com/$id.mp3",
        duration = null,
    )

    private fun snapshot(
        episode: Episode,
        state: Int,
        playWhenReady: Boolean = false,
        isPlaying: Boolean = false,
        playbackError: String? = null,
    ) = ControllerSnapshot(
        currentEpisode = episode,
        playbackState = state,
        playWhenReady = playWhenReady,
        isPlaying = isPlaying,
        playbackError = playbackError,
    )

    private class FakePlaybackController : PlaybackController {
        var latestRequest = 0L
        var listener: PlaybackControllerListener? = null
        var current = ControllerSnapshot(
            playbackState = Player.STATE_IDLE,
            playWhenReady = false,
            isPlaying = false,
        )
        val prepares = mutableMapOf<String, CompletableDeferred<Long>>()
        val playRequests = mutableListOf<Long>()
        var pauseCount = 0
        var addCount = 0
        var removeCount = 0

        override fun beginPlaybackRequest(requestId: Long) { latestRequest = requestId }
        override suspend fun prepareEpisode(episode: Episode, artworkUrl: String?, requestId: Long): Long? {
            val result = prepares.getOrPut(episode.id) { CompletableDeferred() }.await()
            if (requestId != latestRequest) return null
            current = snapshotFor(episode, Player.STATE_BUFFERING, true)
            return result
        }
        override suspend fun prepareEpisodes(
            episodes: List<Episode>, defaultArtworkUrl: String?, requestId: Long,
        ): Long? = prepareEpisode(episodes.first(), defaultArtworkUrl, requestId)
        override suspend fun play(requestId: Long?) {
            if (requestId != null && requestId != latestRequest) return
            requestId?.let(playRequests::add)
            current = current.copy(playbackState = Player.STATE_READY, playWhenReady = true, isPlaying = true)
            listener?.onSnapshotChanged(current)
        }
        override suspend fun pause() { pauseCount++ }
        override suspend fun seekTo(position: Long) = Unit
        override suspend fun skipToPrevious() = Unit
        override suspend fun skipToNext() = Unit
        override suspend fun setPlaybackSpeed(speed: Float) { current = current.copy(playbackSpeed = speed) }
        override suspend fun stop() = Unit
        override suspend fun restoreLastSessionIfNeeded(): Episode? = null
        override suspend fun snapshot(): ControllerSnapshot = current
        override suspend fun addListener(listener: PlaybackControllerListener) {
            addCount++
            this.listener = listener
        }
        override fun removeListener(listener: PlaybackControllerListener) {
            removeCount++
            if (this.listener === listener) this.listener = null
        }
        fun emit(snapshot: ControllerSnapshot) {
            current = snapshot
            listener?.onSnapshotChanged(snapshot)
        }
        private fun snapshotFor(episode: Episode, state: Int, requested: Boolean) = ControllerSnapshot(
            currentEpisode = episode,
            playbackState = state,
            playWhenReady = requested,
            isPlaying = false,
        )
    }
}
