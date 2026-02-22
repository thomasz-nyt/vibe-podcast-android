package com.podcastplayer.app.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.guava.await

class PlayerController private constructor(context: Context) {

    private val sessionToken = SessionToken(
        context,
        ComponentName(context, PlayerService::class.java)
    )

    private val controllerFuture = MediaController.Builder(context, sessionToken)
        .buildAsync()

    private val executor = Executors.newSingleThreadExecutor()

    private fun episodeToMediaItem(
        episode: com.podcastplayer.app.domain.model.Episode,
        artworkUrl: String?
    ): androidx.media3.common.MediaItem {
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastId)
            .setDescription(episode.description)
            .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
            .build()

        val mediaUri = episode.localPath?.takeIf { episode.isDownloaded }?.let {
            Uri.fromFile(File(it))
        } ?: Uri.parse(episode.audioUrl)

        return androidx.media3.common.MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
    }

    suspend fun playEpisode(episode: com.podcastplayer.app.domain.model.Episode, artworkUrl: String?) {
        val controller = controllerFuture.await()
        controller.setMediaItem(episodeToMediaItem(episode, artworkUrl))
        controller.prepare()
        controller.play()
    }

    suspend fun playEpisodes(
        episodes: List<com.podcastplayer.app.domain.model.Episode>,
        defaultArtworkUrl: String?
    ) {
        if (episodes.isEmpty()) return

        val controller = controllerFuture.await()
        val items = episodes.map { episode ->
            episodeToMediaItem(
                episode = episode,
                artworkUrl = episode.imageUrl ?: defaultArtworkUrl
            )
        }

        controller.setMediaItems(items)
        controller.prepare()
        controller.play()
    }

    suspend fun pause() {
        val controller = controllerFuture.await()
        controller.pause()
    }

    suspend fun seekTo(position: Long) {
        val controller = controllerFuture.await()
        controller.seekTo(position)
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        val controller = controllerFuture.await()
        controller.playbackParameters = controller.playbackParameters.withSpeed(speed)
    }

    suspend fun getCurrentPosition(): Long {
        val controller = controllerFuture.await()
        return controller.currentPosition
    }

    suspend fun getDuration(): Long {
        val controller = controllerFuture.await()
        return controller.duration
    }

    suspend fun getPlaybackState(): Int {
        val controller = controllerFuture.await()
        return controller.playbackState
    }

    fun addListener(listener: Player.Listener) {
        controllerFuture.addListener(
            {
                try {
                    controllerFuture.get().addListener(listener)
                } catch (_: Exception) {
                }
            },
            executor
        )
    }

    fun release() {
        MediaController.releaseFuture(controllerFuture)
        executor.shutdown()
    }

    companion object {
        @Volatile
        private var instance: PlayerController? = null

        fun getInstance(context: Context): PlayerController {
            return instance ?: synchronized(this) {
                instance ?: PlayerController(context.applicationContext).also { instance = it }
            }
        }
    }
}
