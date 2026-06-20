package com.podcastplayer.app.service

import com.podcastplayer.app.domain.model.Episode

data class ControllerSnapshot(
    val currentEpisode: Episode? = null,
    val artworkUrl: String? = null,
    val playbackState: Int,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val playbackError: String? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val playbackSpeed: Float = 1f,
)

fun interface PlaybackControllerListener {
    fun onSnapshotChanged(snapshot: ControllerSnapshot)
}

/** Testable boundary around the app's Media3 controller. */
interface PlaybackController {
    fun beginPlaybackRequest(requestId: Long)
    suspend fun prepareEpisode(episode: Episode, artworkUrl: String?, requestId: Long): Long?
    suspend fun prepareEpisodes(episodes: List<Episode>, defaultArtworkUrl: String?, requestId: Long): Long?
    suspend fun play(requestId: Long? = null)
    suspend fun pause()
    suspend fun seekTo(position: Long)
    suspend fun skipToPrevious()
    suspend fun skipToNext()
    suspend fun setPlaybackSpeed(speed: Float)
    suspend fun stop()
    suspend fun restoreLastSessionIfNeeded(): Episode?
    suspend fun snapshot(): ControllerSnapshot
    suspend fun addListener(listener: PlaybackControllerListener)
    fun removeListener(listener: PlaybackControllerListener)
}
