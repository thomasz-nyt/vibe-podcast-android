package com.podcastplayer.app.domain.model

data class Podcast(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val feedUrl: String?
)
