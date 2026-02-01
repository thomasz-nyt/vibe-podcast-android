package com.podcastplayer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlaybackState
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.service.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val playerController: PlayerController
) : ViewModel() {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentEpisode = MutableStateFlow<Episode?>(null)
    val currentEpisode: StateFlow<Episode?> = _currentEpisode.asStateFlow()

    private val _currentArtworkUrl = MutableStateFlow<String?>(null)
    val currentArtworkUrl: StateFlow<String?> = _currentArtworkUrl.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()

    private var sleepTimerJob: Job? = null


    fun playEpisode(episode: Episode, artworkUrl: String?) {
        viewModelScope.launch {
            _currentEpisode.value = episode
            _currentArtworkUrl.value = episode.imageUrl ?: artworkUrl
            _playerState.value = _playerState.value.copy(
                state = PlaybackState.LOADING,
                currentEpisode = episode
            )
            try {
                playerController.playEpisode(episode, artworkUrl)
                _playerState.value = _playerState.value.copy(
                    state = PlaybackState.PLAYING
                )
                startPositionUpdates()
            } catch (e: Exception) {
                _playerState.value = _playerState.value.copy(
                    state = PlaybackState.ERROR
                )
            }
        }
    }

    fun playEpisodesQueue(episodes: List<Episode>, defaultArtworkUrl: String?) {
        if (episodes.isEmpty()) return

        viewModelScope.launch {
            val first = episodes.first()
            _currentEpisode.value = first
            _currentArtworkUrl.value = first.imageUrl ?: defaultArtworkUrl
            _playerState.value = _playerState.value.copy(
                state = PlaybackState.LOADING,
                currentEpisode = first
            )

            try {
                playerController.playEpisodes(episodes, defaultArtworkUrl)
                _playerState.value = _playerState.value.copy(state = PlaybackState.PLAYING)
                startPositionUpdates()
            } catch (e: Exception) {
                _playerState.value = _playerState.value.copy(state = PlaybackState.ERROR)
            }
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            val currentState = _playerState.value.state
            val episode = _currentEpisode.value
            val playbackUrl = episode?.localPath?.takeIf { episode.isDownloaded } ?: episode?.audioUrl.orEmpty()
            when (currentState) {
                PlaybackState.PLAYING -> {
                    playerController.pause()
                    _playerState.value = _playerState.value.copy(
                        state = PlaybackState.PAUSED
                    )
                }
                PlaybackState.PAUSED -> {
                    if (playbackUrl.isNotBlank() && episode != null) {
                        playerController.playEpisode(episode, null)
                        _playerState.value = _playerState.value.copy(
                            state = PlaybackState.PLAYING
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun seekTo(position: Long) {
        viewModelScope.launch {
            playerController.seekTo(position)
            _playerState.value = _playerState.value.copy(
                currentPosition = position
            )
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            playerController.setPlaybackSpeed(speed)
            _playerState.value = _playerState.value.copy(
                playbackSpeed = speed
            )
        }
    }

    fun setSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = durationMs
        sleepTimerJob = viewModelScope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemaining.value = remaining.coerceAtLeast(0)
            }
            playerController.pause()
            _playerState.value = _playerState.value.copy(state = PlaybackState.PAUSED)
            _sleepTimerRemaining.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemaining.value = null
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (_playerState.value.state == PlaybackState.PLAYING) {
                try {
                    val position = playerController.getCurrentPosition()
                    val duration = playerController.getDuration()
                    _playerState.value = _playerState.value.copy(
                        currentPosition = position,
                        duration = duration
                    )
                    kotlinx.coroutines.delay(1000)
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        super.onCleared()
    }
}
