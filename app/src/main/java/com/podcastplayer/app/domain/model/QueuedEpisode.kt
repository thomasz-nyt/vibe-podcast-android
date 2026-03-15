package com.podcastplayer.app.domain.model

data class QueuedEpisode(
    val episode: Episode,
    val artworkUrl: String?
)
