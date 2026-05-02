package com.podcastplayer.app.data.remote

internal fun upgradeITunesArtwork(url: String?): String? =
    url?.replace("/100x100bb.", "/600x600bb.")
