package com.podcastplayer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.podcastplayer.app.data.local.AppSettings
import com.podcastplayer.app.data.repository.QueuePlaybackResult
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlaybackState
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.service.ControllerSnapshot
import com.podcastplayer.app.service.PlaybackController
import com.podcastplayer.app.service.PlaybackControllerListener
import com.podcastplayer.app.service.PlaybackSessionStorage
import com.podcastplayer.app.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val QUEUE_LOG_TAG = "VibeQueue"

class PlayerViewModel(
    private val playerController: PlaybackController,
    private val playbackSessionStorage: PlaybackSessionStorage?,
    private val appSettings: AppSettings? = null,
) : ViewModel() {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()
    private val _currentArtworkUrl = MutableStateFlow<String?>(null)
    val currentArtworkUrl: StateFlow<String?> = _currentArtworkUrl.asStateFlow()
    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()
    private val _hasPrevious = MutableStateFlow(false)
    val hasPrevious: StateFlow<Boolean> = _hasPrevious.asStateFlow()
    private val _hasNext = MutableStateFlow(false)
    val hasNext: StateFlow<Boolean> = _hasNext.asStateFlow()
    private val _resumedFromMs = MutableStateFlow<Long?>(null)
    val resumedFromMs: StateFlow<Long?> = _resumedFromMs.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var positionTickerJob: Job? = null
    private var requestJob: Job? = null
    private var bufferingJob: Job? = null
    private var recoveryUsed = false
    private var terminalError = false
    private val initialized = CompletableDeferred<Unit>()
    suspend fun awaitInitialization() = initialized.await()
    val playbackGeneration: Long get() = requestGeneration
    private var requestGeneration = 0L
    private var pendingRequestId: Long? = null
    private var queueEpisodes: Map<String, Episode> = emptyMap()
    private var queueDefaultArtworkUrl: String? = null

    private val controllerListener = PlaybackControllerListener(::applySnapshot)

    init {
        viewModelScope.launch {
            val generation = 0L
            try {
                playerController.addListener(controllerListener)
                if (generation == requestGeneration) playerController.restoreLastSessionIfNeeded()
                if (generation == requestGeneration) applySnapshot(playerController.snapshot())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == requestGeneration) fail(error)
            } finally {
                initialized.complete(Unit)
            }
        }
    }

    /** Reserve before feed IO so a slow queue cannot override any newer user action. */
    fun reservePlaybackRequest(): Long {
        requestJob?.cancel()
        bufferingJob?.cancel()
        pendingRequestId = null
        terminalError = false
        recoveryUsed = false
        val id = ++requestGeneration
        playerController.beginPlaybackRequest(id)
        return id
    }

    /** Feed resolution and playback belong to one request, so a newer action cancels both. */
    fun startQueuePlayback(
        defaultArtworkUrl: String?,
        resolve: suspend () -> QueuePlaybackResult,
        onResult: (QueuePlaybackResult) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val id = reservePlaybackRequest()
        pendingRequestId = id
        _playerState.value = _playerState.value.copy(
            state = PlaybackState.LOADING, playRequested = true, isBuffering = true, playbackError = null,
        )
        Logger.d("Queue request $id: resolving feeds", tag = QUEUE_LOG_TAG)
        requestJob = viewModelScope.launch {
            try {
                val result = withTimeout(12_000) { resolve() }
                if (id != requestGeneration) return@launch
                Logger.d("Queue request $id: resolved ${result.episodes.size} episodes, " +
                    "${result.shows.count { it.episode == null }} unavailable shows", tag = QUEUE_LOG_TAG)
                onResult(result)
                if (result.episodes.isEmpty()) {
                    fail(IllegalStateException("Queue has no playable episodes"))
                } else {
                    playEpisodesQueue(result.episodes, defaultArtworkUrl, id)
                }
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException && id == requestGeneration) {
                    Logger.w("Queue request $id: resolution timed out", tag = QUEUE_LOG_TAG)
                    fail(IllegalStateException("Queue resolution timed out after 12 seconds"))
                    onFailure("Queue resolution timed out. Check your connection or saved downloads.")
                } else {
                    Logger.d("Queue request $id: superseded", tag = QUEUE_LOG_TAG)
                    throw error
                }
            } catch (error: Exception) {
                if (id == requestGeneration) {
                    Logger.w("Queue request $id: resolution failed", tag = QUEUE_LOG_TAG)
                    fail(IllegalStateException("Queue could not be loaded"))
                    onFailure("Queue could not be loaded. Retry or check your connection.")
                }
            }
        }
    }

    fun playEpisode(episode: Episode, artworkUrl: String?) {
        queueEpisodes = emptyMap()
        queueDefaultArtworkUrl = null
        val id = reservePlaybackRequest()
        updateCurrentEpisode(episode, artworkUrl)
        launchRequest(id) {
            val start = playerController.prepareEpisode(episode, artworkUrl, id)
            if (id == requestGeneration && start != null) {
                applyDefaultSpeedIfNeeded()
                if (id == requestGeneration) playerController.play(id)
                if (start > 0) _resumedFromMs.value = start
            }
        }
    }

    fun playEpisodesQueue(
        episodes: List<Episode>,
        defaultArtworkUrl: String?,
        requestId: Long = reservePlaybackRequest(),
    ) {
        if (requestId != requestGeneration || episodes.isEmpty()) return
        queueEpisodes = episodes.associateBy(Episode::id)
        queueDefaultArtworkUrl = defaultArtworkUrl
        updateCurrentEpisode(episodes.first(), defaultArtworkUrl)
        launchRequest(requestId) {
            val start = playerController.prepareEpisodes(episodes, defaultArtworkUrl, requestId)
            if (requestId == requestGeneration && start != null) {
                Logger.d("Queue request $requestId: prepared first item", tag = QUEUE_LOG_TAG)
                applyDefaultSpeedIfNeeded()
                if (requestId == requestGeneration) playerController.play(requestId)
                if (start > 0) _resumedFromMs.value = start
                awaitQueueReady(requestId, episodes.first().id)
            } else if (requestId == requestGeneration) {
                error("Queue preparation was interrupted")
            }
        }
    }

    private suspend fun awaitQueueReady(requestId: Long, firstEpisodeId: String) {
        val ready = kotlinx.coroutines.withTimeoutOrNull(15_000) {
            while (requestId == requestGeneration) {
                val snapshot = playerController.snapshot()
                snapshot.playbackError?.let { error("Queue playback failed: $it") }
                if (snapshot.currentEpisode?.id == firstEpisodeId &&
                    snapshot.playbackState == Player.STATE_READY && snapshot.playWhenReady
                ) return@withTimeoutOrNull true
                delay(100)
            }
            false
        }
        if (requestId == requestGeneration && ready != true) {
            Logger.w("Queue request $requestId: first item did not become ready", tag = QUEUE_LOG_TAG)
            error("First queue episode did not become ready")
        }
        if (ready == true) Logger.d("Queue request $requestId: first item ready", tag = QUEUE_LOG_TAG)
    }

    private fun launchRequest(id: Long, action: suspend () -> Unit) {
        pendingRequestId = id
        _playerState.value = _playerState.value.copy(
            state = PlaybackState.LOADING, currentEpisode = _currentEpisode.value,
            playRequested = true, isBuffering = true, playbackError = null,
        )
        requestJob = viewModelScope.launch {
            try {
                try {
                    withTimeout(30_000) { action() }
                } catch (error: Exception) {
                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                    if (id != requestGeneration || recoveryUsed) throw error
                    recoveryUsed = true
                    withTimeout(30_000) { action() }
                }
                if (id != requestGeneration) return@launch
                pendingRequestId = null
                applySnapshot(playerController.snapshot())
            } catch (error: CancellationException) {
                if (error is TimeoutCancellationException && id == requestGeneration) fail(error)
                else throw error
            } catch (error: Exception) {
                if (id == requestGeneration) fail(error)
            } finally {
                if (pendingRequestId == id) pendingRequestId = null
            }
        }
    }

    private fun fail(error: Throwable) {
        terminalError = true
        pendingRequestId = null
        bufferingJob?.cancel()
        positionTickerJob?.cancel()
        Logger.e("Playback request failed", error)
        _playerState.value = _playerState.value.copy(
            state = PlaybackState.ERROR, playRequested = false, isBuffering = false,
            playbackError = "${error.message ?: "Playback unavailable"}. Tap Retry or choose another episode.",
        )
        val failedGeneration = requestGeneration
        viewModelScope.launch {
            if (failedGeneration == requestGeneration) runCatching { playerController.pause() }
        }
    }

    private fun watchBuffering() {
        if (bufferingJob?.isActive == true) return
        val id = requestGeneration
        bufferingJob = viewModelScope.launch {
            delay(30_000)
            if (id == requestGeneration) recoverOrFail(id, IllegalStateException("Playback timed out"))
        }
    }

    private fun recoverOrFail(id: Long, error: Throwable) {
        if (id != requestGeneration || terminalError) return
        bufferingJob?.cancel()
        bufferingJob = null
        if (recoveryUsed) { fail(error); return }
        recoveryUsed = true
        launchRequest(id) { playerController.recover(id) }
    }

    private suspend fun applyDefaultSpeedIfNeeded() {
        val speed = appSettings?.defaultPlaybackSpeed?.value ?: return
        playerController.setPlaybackSpeed(speed)
    }

    fun consumeResumeNotice() { _resumedFromMs.value = null }

    fun togglePlayPause() {
        val wasRequested = _playerState.value.playRequested
        val hadError = terminalError || _playerState.value.playbackError != null
        val id = reservePlaybackRequest()
        if (wasRequested) {
            _playerState.value = _playerState.value.copy(
                state = if (_currentEpisode.value == null) PlaybackState.IDLE else PlaybackState.PAUSED,
                playRequested = false, isBuffering = false, playbackError = null,
            )
            viewModelScope.launch { runCatching { playerController.pause() }.onFailure { fail(it) } }
        } else {
            launchRequest(id) {
                if (hadError) playerController.recover(id) else playerController.play(id)
            }
        }
    }

    fun playNext() = navigateQueue(next = true)
    fun playPrevious() = navigateQueue(next = false)

    private fun navigateQueue(next: Boolean) {
        if (next && !_hasNext.value || !next && !_hasPrevious.value) return
        val id = reservePlaybackRequest()
        var navigated = false
        launchRequest(id) {
            if (!navigated) {
                if (next) playerController.skipToNext() else playerController.skipToPrevious()
                navigated = true
            }
            if (id == requestGeneration) playerController.play(id)
        }
    }

    fun seekTo(position: Long) {
        _playerState.value = _playerState.value.copy(currentPosition = position.coerceAtLeast(0L))
        viewModelScope.launch { playerController.seekTo(position) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
        viewModelScope.launch { playerController.setPlaybackSpeed(speed) }
    }

    fun setSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = durationMs
        sleepTimerJob = viewModelScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                _sleepTimerRemaining.value = remaining.coerceAtLeast(0)
            }
            if (_playerState.value.playRequested) togglePlayPause()
            _sleepTimerRemaining.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemaining.value = null
    }

    private fun applySnapshot(snapshot: ControllerSnapshot) {
        if (pendingRequestId != null || terminalError) return
        val previous = _currentEpisode.value
        val rebuilt = snapshot.currentEpisode
        val episode = when {
            rebuilt == null -> null
            queueEpisodes[rebuilt.id] != null -> queueEpisodes.getValue(rebuilt.id)
            previous?.id == rebuilt.id -> rebuilt.copy(mediaType = previous.mediaType)
            else -> rebuilt
        }
        _currentEpisode.value = episode
        _currentArtworkUrl.value = episode?.imageUrl ?: snapshot.artworkUrl ?: queueDefaultArtworkUrl
        _hasPrevious.value = snapshot.hasPrevious
        _hasNext.value = snapshot.hasNext

        val buffering = snapshot.playbackError == null &&
            snapshot.playWhenReady && snapshot.playbackState == Player.STATE_BUFFERING
        val state = when {
            snapshot.playbackError != null -> PlaybackState.ERROR
            episode == null -> PlaybackState.IDLE
            buffering -> PlaybackState.LOADING
            snapshot.playWhenReady && snapshot.playbackState != Player.STATE_ENDED -> PlaybackState.PLAYING
            else -> PlaybackState.PAUSED
        }
        _playerState.value = PlayerState(
            state = state,
            currentEpisode = episode,
            currentPosition = snapshot.currentPosition,
            duration = snapshot.duration,
            playbackSpeed = snapshot.playbackSpeed,
            playRequested = snapshot.playbackError == null && snapshot.playWhenReady &&
                snapshot.playbackState != Player.STATE_ENDED,
            isBuffering = buffering,
            playbackError = snapshot.playbackError,
        )
        if (snapshot.playbackError != null && snapshot.playWhenReady &&
            requestGeneration > 0 && _currentEpisode.value != null) {
            recoverOrFail(requestGeneration, IllegalStateException(snapshot.playbackError))
        } else if (buffering) {
            watchBuffering()
        } else {
            bufferingJob?.cancel()
            bufferingJob = null
        }
        updatePositionTicker(snapshot.playWhenReady && snapshot.playbackState != Player.STATE_ENDED)
    }

    private fun updatePositionTicker(active: Boolean) {
        if (!active) {
            positionTickerJob?.cancel()
            positionTickerJob = null
            return
        }
        if (positionTickerJob?.isActive == true) return
        positionTickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                runCatching { playerController.snapshot() }
                    .onSuccess(::applySnapshot)
                    .onFailure { Logger.w("Position refresh failed", it) }
            }
        }
    }

    private fun updateCurrentEpisode(episode: Episode, artworkUrl: String?) {
        _currentEpisode.value = episode
        _currentArtworkUrl.value = episode.imageUrl ?: artworkUrl
    }

    fun clearPlayer() {
        reservePlaybackRequest()
        positionTickerJob?.cancel()
        viewModelScope.launch {
            playerController.stop()
            playbackSessionStorage?.clear()
        }
        _currentEpisode.value = null
        _currentArtworkUrl.value = null
        queueEpisodes = emptyMap()
        _playerState.value = PlayerState()
        _hasPrevious.value = false
        _hasNext.value = false
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        requestJob?.cancel()
        bufferingJob?.cancel()
        positionTickerJob?.cancel()
        playerController.removeListener(controllerListener)
        super.onCleared()
    }
}
