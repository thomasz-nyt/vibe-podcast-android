package com.podcastplayer.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.PlaybackState
import com.podcastplayer.app.domain.model.PlayerState
import com.podcastplayer.app.service.PlaybackSessionStorage
import com.podcastplayer.app.service.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val playerController: PlayerController,
    private val playbackSessionStorage: PlaybackSessionStorage
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

    private var sleepTimerJob: Job? = null

    private var queueEpisodes: Map<String, Episode> = emptyMap()
    private var queueDefaultArtworkUrl: String? = null

    init {
        playerController.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val episodeId = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
                val episode = queueEpisodes[episodeId] ?: return
                updateCurrentEpisode(episode, queueDefaultArtworkUrl)
            }
        })

        viewModelScope.launch {
            playerController.restoreLastSessionIfNeeded()
            refreshFromController()
            startPositionUpdates()
        }
    }

    fun playEpisode(episode: Episode, artworkUrl: String?) {
        viewModelScope.launch {
            queueEpisodes = emptyMap()
            queueDefaultArtworkUrl = null
            updateCurrentEpisode(episode, artworkUrl)
            _playerState.value = _playerState.value.copy(
                state = PlaybackState.LOADING,
                currentEpisode = episode
            )
            try {
                playerController.playEpisode(episode, artworkUrl)
                _playerState.value = _playerState.value.copy(
                    state = PlaybackState.PLAYING
                )
                refreshFromController()
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
            queueEpisodes = episodes.associateBy { it.id }
            queueDefaultArtworkUrl = defaultArtworkUrl

            val first = episodes.first()
            updateCurrentEpisode(first, defaultArtworkUrl)
            _playerState.value = _playerState.value.copy(
                state = PlaybackState.LOADING,
                currentEpisode = first
            )

            try {
                playerController.playEpisodes(episodes, defaultArtworkUrl)
                _playerState.value = _playerState.value.copy(state = PlaybackState.PLAYING)
                refreshFromController()
            } catch (e: Exception) {
                _playerState.value = _playerState.value.copy(state = PlaybackState.ERROR)
            }
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            when (_playerState.value.state) {
                PlaybackState.PLAYING -> {
                    playerController.pause()
                    _playerState.value = _playerState.value.copy(state = PlaybackState.PAUSED)
                }
                PlaybackState.PAUSED, PlaybackState.IDLE -> {
                    playerController.play()
                    _playerState.value = _playerState.value.copy(state = PlaybackState.PLAYING)
                }
                else -> Unit
            }
            refreshFromController()
        }
    }

    fun playNext() {
        viewModelScope.launch {
            if (!_hasNext.value) return@launch
            playerController.skipToNext()
            playerController.play()
            _playerState.value = _playerState.value.copy(state = PlaybackState.PLAYING)
            refreshFromController()
        }
    }

    fun playPrevious() {
        viewModelScope.launch {
            if (!_hasPrevious.value) return@launch
            playerController.skipToPrevious()
            playerController.play()
            _playerState.value = _playerState.value.copy(state = PlaybackState.PLAYING)
            refreshFromController()
        }
    }

    fun seekTo(position: Long) {
        viewModelScope.launch {
            playerController.seekTo(position)
            _playerState.value = _playerState.value.copy(
                currentPosition = position.coerceAtLeast(0)
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

    private suspend fun refreshFromController() {
        val episode = playerController.getCurrentEpisode()
        _currentEpisode.value = episode
        _currentArtworkUrl.value = playerController.getCurrentArtworkUrl() ?: episode?.imageUrl
        _hasPrevious.value = playerController.hasPrevious()
        _hasNext.value = playerController.hasNext()

        val playbackState = when {
            playerController.isPlaying() -> PlaybackState.PLAYING
            episode != null -> PlaybackState.PAUSED
            else -> PlaybackState.IDLE
        }

        _playerState.value = _playerState.value.copy(
            state = playbackState,
            currentEpisode = episode,
            currentPosition = playerController.getCurrentPosition().coerceAtLeast(0L),
            duration = playerController.getDuration().coerceAtLeast(0L)
        )
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (true) {
                try {
                    refreshFromController()
                    kotlinx.coroutines.delay(1000)
                } catch (e: Exception) {
                    kotlinx.coroutines.delay(2000)
                }
            }
        }
    }

    private fun updateCurrentEpisode(episode: Episode, artworkUrl: String?) {
        _currentEpisode.value = episode
        _currentArtworkUrl.value = episode.imageUrl ?: artworkUrl
        _playerState.value = _playerState.value.copy(currentEpisode = episode)
    }

    fun clearPlayer() {
        viewModelScope.launch {
            playerController.stop()
            playbackSessionStorage.clear()
            _currentEpisode.value = null
            _currentArtworkUrl.value = null
            queueEpisodes = emptyMap()
            _playerState.value = PlayerState(PlaybackState.IDLE, null, 0L, 0L, 1f)
            _hasPrevious.value = false
            _hasNext.value = false
        }
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        super.onCleared()
    }
}
