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

    suspend fun playEpisode(episode: com.podcastplayer.app.domain.model.Episode, artworkUrl: String?) {
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastId)
            .setDescription(episode.description)
            .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
            .build()

        val controller = controllerFuture.await()
        val mediaUri = episode.localPath?.takeIf { episode.isDownloaded }?.let {
            Uri.fromFile(File(it))
        } ?: Uri.parse(episode.audioUrl)
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
        controller.setMediaItem(mediaItem)
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
