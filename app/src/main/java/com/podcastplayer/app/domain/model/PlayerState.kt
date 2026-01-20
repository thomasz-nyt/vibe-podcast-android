package com.podcastplayer.app.domain.model

enum class PlaybackState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    ERROR
}

data class PlayerState(
    val state: PlaybackState = PlaybackState.IDLE,
    val currentEpisode: Episode? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f
)
