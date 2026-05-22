package com.podcastplayer.app.domain.model

data class Podcast(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val feedUrl: String?,
    /**
     * When true, the auto-download worker will fetch the latest episode for this
     * podcast on its periodic run. Gson treats missing fields as default, so
     * older serialized rows safely deserialize to `false`.
     */
    val autoDownload: Boolean = false,
)
