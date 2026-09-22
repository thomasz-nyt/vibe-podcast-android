package com.podcastplayer.app.service

/** Protects a just-selected playlist even before the service has persisted its session snapshot. */
internal object PlaybackDownloadProtection {
    @Volatile var episodeIds: Set<String> = emptySet()
}
