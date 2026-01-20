package com.podcastplayer.app.domain.model

import java.util.Date

data class Episode(
    val id: String,
    val podcastId: String,
    val title: String,
    val description: String?,
    val pubDate: Date?,
    val audioUrl: String,
    val duration: Long?,
    val imageUrl: String? = null,
    val isDownloaded: Boolean = false,
    val localPath: String? = null
)
