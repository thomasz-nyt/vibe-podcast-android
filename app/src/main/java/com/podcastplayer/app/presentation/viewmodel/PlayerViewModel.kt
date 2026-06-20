package com.podcastplayer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.podcastplayer.app.data.local.AppSettings
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlaybackState
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.service.ControllerSnapshot
import com.podcastplayer.app.service.PlaybackController
import com.podcastplayer.app.service.PlaybackControllerListener
import com.podcastplayer.app.service.PlaybackSessionStorage
import com.podcastplayer.app.util.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private var requestGeneration = 0L
    private var pendingRequestId: Long? = null
    private var queueEpisodes: Map<String, Episode> = emptyMap()
    private var queueDefaultArtworkUrl: String? = null

    private val controllerListener = PlaybackControllerListener(::applySnapshot)

    init {
        viewModelScope.launch {
            try {
                playerController.addListener(controllerListener)
                playerController.restoreLastSessionIfNeeded()
                applySnapshot(playerController.snapshot())
            } catch (error: Exception) {
                Logger.e("Unable to connect playback controller", error)
                _playerState.value = _playerState.value.copy(
                    state = PlaybackState.ERROR,
                    playRequested = false,
                    isBuffering = false,
                    playbackError = error.message ?: "Unable to connect to player",
                )
            }
        }
    }

    fun playEpisode(episode: Episode, artworkUrl: String?) {
        queueEpisodes = emptyMap()
        queueDefaultArtworkUrl = null
        startEpisodeRequest(episode, artworkUrl) { requestId ->
            playerController.prepareEpisode(episode, artworkUrl, requestId)
        }
    }

    fun playEpisodesQueue(episodes: List<Episode>, defaultArtworkUrl: String?) {
        if (episodes.isEmpty()) return
        queueEpisodes = episodes.associateBy(Episode::id)
        queueDefaultArtworkUrl = defaultArtworkUrl
        val first = episodes.first()
        startEpisodeRequest(first, defaultArtworkUrl) { requestId ->
            playerController.prepareEpisodes(episodes, defaultArtworkUrl, requestId)
        }
    }

    private fun startEpisodeRequest(
        episode: Episode,
        artworkUrl: String?,
        prepare: suspend (Long) -> Long?,
    ) {
        val requestId = ++requestGeneration
        pendingRequestId = requestId
        playerController.beginPlaybackRequest(requestId)
        updateCurrentEpisode(episode, artworkUrl)
        _playerState.value = _playerState.value.copy(
            state = PlaybackState.LOADING,
            currentEpisode = episode,
            playRequested = true,
            isBuffering = true,
            playbackError = null,
        )
        viewModelScope.launch {
            try {
                val startMs = prepare(requestId) ?: return@launch
                if (requestId != requestGeneration) return@launch
                applyDefaultSpeedIfNeeded()
                if (requestId != requestGeneration) return@launch
                playerController.play(requestId)
                pendingRequestId = null
                if (startMs > 0L) _resumedFromMs.value = startMs
                applySnapshot(playerController.snapshot())
            } catch (error: Exception) {
                if (requestId != requestGeneration) return@launch
                pendingRequestId = null
                Logger.e("Playback request failed", error)
                _playerState.value = _playerState.value.copy(
                    state = PlaybackState.ERROR,
                    playRequested = false,
                    isBuffering = false,
                    playbackError = error.message ?: "Playback failed",
                )
            }
        }
    }

    private suspend fun applyDefaultSpeedIfNeeded() {
        val speed = appSettings?.defaultPlaybackSpeed?.value ?: return
        if (kotlin.math.abs(speed - 1f) < 0.01f) return
        playerController.setPlaybackSpeed(speed)
    }

    fun consumeResumeNotice() { _resumedFromMs.value = null }

    fun togglePlayPause() {
        if (_playerState.value.playRequested) {
            val cancellationId = ++requestGeneration
            pendingRequestId = null
            playerController.beginPlaybackRequest(cancellationId)
            _playerState.value = _playerState.value.copy(
                state = if (_currentEpisode.value == null) PlaybackState.IDLE else PlaybackState.PAUSED,
                playRequested = false,
                isBuffering = false,
                playbackError = null,
            )
            viewModelScope.launch {
                runCatching { playerController.pause() }
                    .onFailure { Logger.e("Pause failed", it) }
            }
            return
        }

        val requestId = ++requestGeneration
        playerController.beginPlaybackRequest(requestId)
        _playerState.value = _playerState.value.copy(
            state = PlaybackState.LOADING,
            playRequested = true,
            isBuffering = true,
            playbackError = null,
        )
        viewModelScope.launch {
            try {
                playerController.play(requestId)
                applySnapshot(playerController.snapshot())
            } catch (error: Exception) {
                Logger.e("Play failed", error)
                _playerState.value = _playerState.value.copy(
                    state = PlaybackState.ERROR,
                    playRequested = false,
                    isBuffering = false,
                    playbackError = error.message ?: "Playback failed",
                )
            }
        }
    }

    fun playNext() = navigateQueue(next = true)
    fun playPrevious() = navigateQueue(next = false)

    private fun navigateQueue(next: Boolean) {
        if (next && !_hasNext.value || !next && !_hasPrevious.value) return
        val requestId = ++requestGeneration
        playerController.beginPlaybackRequest(requestId)
        _playerState.value = _playerState.value.copy(
            state = PlaybackState.LOADING,
            playRequested = true,
            isBuffering = true,
            playbackError = null,
        )
        viewModelScope.launch {
            if (next) playerController.skipToNext() else playerController.skipToPrevious()
            playerController.play(requestId)
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
            playerController.pause()
            _sleepTimerRemaining.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemaining.value = null
    }

    private fun applySnapshot(snapshot: ControllerSnapshot) {
        if (pendingRequestId != null) return
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

        val buffering = snapshot.playWhenReady && snapshot.playbackState == Player.STATE_BUFFERING
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
            playRequested = snapshot.playWhenReady && snapshot.playbackState != Player.STATE_ENDED,
            isBuffering = buffering,
            playbackError = snapshot.playbackError,
        )
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
        val requestId = ++requestGeneration
        pendingRequestId = null
        playerController.beginPlaybackRequest(requestId)
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
        positionTickerJob?.cancel()
        playerController.removeListener(controllerListener)
        super.onCleared()
    }
}
