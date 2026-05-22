package com.podcastplayer.app.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.podcastplayer.app.data.local.DatabaseProvider
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class PlayerController private constructor(private val context: Context) {

    private val sessionToken = SessionToken(
        context,
        ComponentName(context, PlayerService::class.java)
    )

    private val controllerFuture = MediaController.Builder(context, sessionToken)
        .buildAsync()

    private val executor = Executors.newSingleThreadExecutor()
    private val playbackSessionStorage = PlaybackSessionStorage(context)
    private val playbackProgressDao by lazy {
        DatabaseProvider.getDatabase(context).playbackProgressDao()
    }

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

        val mediaUri = episode.localPath?.takeIf { episode.isDownloaded }?.let { resolveLocalUri(it) }
            ?: Uri.parse(episode.audioUrl)

        return androidx.media3.common.MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Saved listen-position for [episodeId], or null if the user hasn't started it
     * or already finished it. Looked up before `prepare()` so resume is baked into
     * the MediaItem rather than racing the player's STATE_READY callback.
     */
    private suspend fun resumePositionFor(episodeId: String): Long? = withContext(Dispatchers.IO) {
        val saved = playbackProgressDao.getByEpisodeId(episodeId) ?: return@withContext null
        if (saved.completed || saved.positionMs <= 0L) null else saved.positionMs
    }

    suspend fun playEpisode(episode: com.podcastplayer.app.domain.model.Episode, artworkUrl: String?): Long {
        val controller = controllerFuture.await()
        val startMs = resumePositionFor(episode.id) ?: 0L
        controller.setMediaItem(episodeToMediaItem(episode, artworkUrl), startMs)
        controller.prepare()
        controller.play()
        return startMs
    }

    suspend fun playEpisodes(
        episodes: List<com.podcastplayer.app.domain.model.Episode>,
        defaultArtworkUrl: String?
    ): Long {
        if (episodes.isEmpty()) return 0L

        val controller = controllerFuture.await()
        val items = episodes.map { episode ->
            episodeToMediaItem(
                episode = episode,
                artworkUrl = episode.imageUrl ?: defaultArtworkUrl
            )
        }

        val firstEpisodeId = episodes.first().id
        val startMs = resumePositionFor(firstEpisodeId) ?: 0L

        controller.setMediaItems(items, /* startIndex= */ 0, /* startPositionMs= */ startMs)
        controller.prepare()
        controller.play()
        return startMs
    }

    suspend fun play() {
        controllerFuture.await().play()
    }

    suspend fun pause() {
        val controller = controllerFuture.await()
        controller.pause()
    }

    suspend fun seekTo(position: Long) {
        val controller = controllerFuture.await()
        controller.seekTo(position)
    }

    suspend fun skipToPrevious() {
        val controller = controllerFuture.await()
        controller.seekToPreviousMediaItem()
    }

    suspend fun skipToNext() {
        val controller = controllerFuture.await()
        controller.seekToNextMediaItem()
    }

    suspend fun hasPrevious(): Boolean {
        val controller = controllerFuture.await()
        return controller.hasPreviousMediaItem()
    }

    suspend fun hasNext(): Boolean {
        val controller = controllerFuture.await()
        return controller.hasNextMediaItem()
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

    suspend fun stop() {
        val controller = controllerFuture.await()
        controller.stop()
        controller.clearMediaItems()
    }

    suspend fun isPlaying(): Boolean {
        return controllerFuture.await().isPlaying
    }

    suspend fun getCurrentEpisode(): com.podcastplayer.app.domain.model.Episode? {
        val item = controllerFuture.await().currentMediaItem ?: return null
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        val isLocal = uri.startsWith("file://") || uri.startsWith("content://")
        return com.podcastplayer.app.domain.model.Episode(
            id = item.mediaId,
            podcastId = item.mediaMetadata.artist?.toString().orEmpty(),
            title = item.mediaMetadata.title?.toString().orEmpty(),
            description = item.mediaMetadata.description?.toString(),
            pubDate = null,
            audioUrl = uri,
            duration = null,
            imageUrl = item.mediaMetadata.artworkUri?.toString(),
            isDownloaded = isLocal,
            localPath = if (isLocal) {
                if (uri.startsWith("content://")) uri else item.localConfiguration?.uri?.path
            } else null,
            mediaType = inferMediaType(uri),
        )
    }

    /** Build a media URI from a stored path — handles file paths AND content:// URIs. */
    private fun resolveLocalUri(localPath: String): Uri =
        if (localPath.startsWith("content://")) Uri.parse(localPath) else Uri.fromFile(File(localPath))

    /**
     * Best-effort inference of [com.podcastplayer.app.domain.model.MediaType] from a file
     * URI's extension, used when restoring a session (issue #33). Defaults to AUDIO.
     */
    private fun inferMediaType(uri: String): com.podcastplayer.app.domain.model.MediaType {
        val ext = uri.substringAfterLast('.', "").substringBefore('?').lowercase()
        val videoExts = setOf("mp4", "webm", "mkv", "mov", "avi", "m4v")
        return if (ext in videoExts) {
            com.podcastplayer.app.domain.model.MediaType.VIDEO
        } else {
            com.podcastplayer.app.domain.model.MediaType.AUDIO
        }
    }

    suspend fun restoreLastSessionIfNeeded(): com.podcastplayer.app.domain.model.Episode? {
        val controller = controllerFuture.await()
        if (controller.mediaItemCount > 0) {
            return getCurrentEpisode()
        }

        val session = playbackSessionStorage.load() ?: return null
        if (session.items.isEmpty()) return null

        controller.setMediaItems(session.items, session.currentIndex, session.currentPositionMs)
        controller.prepare()
        controller.playbackParameters = controller.playbackParameters.withSpeed(session.playbackSpeed)

        if (session.wasPlaying && !session.isCompleted) {
            controller.play()
        } else {
            controller.pause()
        }

        return getCurrentEpisode()
    }

    suspend fun getCurrentArtworkUrl(): String? {
        return controllerFuture.await().currentMediaItem?.mediaMetadata?.artworkUri?.toString()
    }

    fun release() {
        MediaController.releaseFuture(controllerFuture)
        executor.shutdown()
    }

    /**
     * Suspends until the underlying [MediaController] is available, then returns it.
     *
     * Used by the video player surface (issue #33) to bind an Android `PlayerView`
     * to the same Player instance that drives audio playback.
     */
    suspend fun awaitController(): MediaController = controllerFuture.await()

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
