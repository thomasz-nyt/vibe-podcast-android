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
    val localPath: String? = null,
    /**
     * The kind of media. Defaults to [MediaType.AUDIO] so existing podcast flows are
     * unaffected. URL-downloaded videos (issue #33) set this to [MediaType.VIDEO]; the
     * player UI uses it to decide whether to render a video surface.
     */
    val mediaType: MediaType = MediaType.AUDIO,
)
