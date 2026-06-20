package com.podcastplayer.app.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.MediaType
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class PlayerController private constructor(private val context: Context) : PlaybackController {

    private val sessionToken = SessionToken(context, ComponentName(context, PlayerService::class.java))
    private val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
    private val playbackSessionStorage = PlaybackSessionStorage(context)
    private val playbackProgressDao by lazy { DatabaseProvider.getDatabase(context).playbackProgressDao() }
    private val listeners = ConcurrentHashMap<PlaybackControllerListener, Player.Listener>()

    @Volatile
    private var latestPlaybackRequest = 0L

    override fun beginPlaybackRequest(requestId: Long) {
        latestPlaybackRequest = requestId
    }

    private fun episodeToMediaItem(episode: Episode, artworkUrl: String?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastId)
            .setDescription(episode.description)
            .setArtworkUri(artworkUrl?.let(Uri::parse))
            .build()
        val mediaUri = episode.localPath?.takeIf { episode.isDownloaded }?.let(::resolveLocalUri)
            ?: Uri.parse(episode.audioUrl)
        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()
    }

    private suspend fun resumePositionFor(episodeId: String): Long? = withContext(Dispatchers.IO) {
        playbackProgressDao.getByEpisodeId(episodeId)
            ?.takeIf { !it.completed && it.positionMs > 0L }
            ?.positionMs
    }

    override suspend fun prepareEpisode(episode: Episode, artworkUrl: String?, requestId: Long): Long? {
        val startMs = resumePositionFor(episode.id) ?: 0L
        if (requestId != latestPlaybackRequest) return null
        val controller = controllerFuture.await()
        if (requestId != latestPlaybackRequest) return null
        controller.setMediaItem(episodeToMediaItem(episode, artworkUrl), startMs)
        controller.prepare()
        return startMs
    }

    override suspend fun prepareEpisodes(
        episodes: List<Episode>,
        defaultArtworkUrl: String?,
        requestId: Long,
    ): Long? {
        if (episodes.isEmpty()) return null
        val startMs = resumePositionFor(episodes.first().id) ?: 0L
        if (requestId != latestPlaybackRequest) return null
        val items = episodes.map { episode ->
            episodeToMediaItem(episode, episode.imageUrl ?: defaultArtworkUrl)
        }
        val controller = controllerFuture.await()
        if (requestId != latestPlaybackRequest) return null
        controller.setMediaItems(items, 0, startMs)
        controller.prepare()
        return startMs
    }

    override suspend fun play(requestId: Long?) {
        if (requestId != null && requestId != latestPlaybackRequest) return
        controllerFuture.await().play()
    }

    override suspend fun pause() = controllerFuture.await().pause()
    override suspend fun seekTo(position: Long) = controllerFuture.await().seekTo(position)
    override suspend fun skipToPrevious() = controllerFuture.await().seekToPreviousMediaItem()
    override suspend fun skipToNext() = controllerFuture.await().seekToNextMediaItem()

    override suspend fun setPlaybackSpeed(speed: Float) {
        val controller = controllerFuture.await()
        controller.playbackParameters = controller.playbackParameters.withSpeed(speed)
    }

    override suspend fun stop() {
        controllerFuture.await().run {
            stop()
            clearMediaItems()
        }
    }

    override suspend fun snapshot(): ControllerSnapshot = snapshotOf(controllerFuture.await())

    private fun snapshotOf(controller: MediaController): ControllerSnapshot {
        val item = controller.currentMediaItem
        val episode = item?.let(::mediaItemToEpisode)
        return ControllerSnapshot(
            currentEpisode = episode,
            artworkUrl = item?.mediaMetadata?.artworkUri?.toString(),
            playbackState = controller.playbackState,
            playWhenReady = controller.playWhenReady,
            isPlaying = controller.isPlaying,
            playbackError = controller.playerError?.message,
            currentPosition = controller.currentPosition.coerceAtLeast(0L),
            duration = controller.duration.coerceAtLeast(0L),
            hasPrevious = controller.hasPreviousMediaItem(),
            hasNext = controller.hasNextMediaItem(),
            playbackSpeed = controller.playbackParameters.speed,
        )
    }

    override suspend fun addListener(listener: PlaybackControllerListener) {
        val controller = controllerFuture.await()
        withContext(Dispatchers.Main.immediate) {
            val media3Listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    listener.onSnapshotChanged(snapshotOf(controller))
                }
            }
            listeners.put(listener, media3Listener)?.let(controller::removeListener)
            controller.addListener(media3Listener)
            listener.onSnapshotChanged(snapshotOf(controller))
        }
    }

    override fun removeListener(listener: PlaybackControllerListener) {
        val media3Listener = listeners.remove(listener) ?: return
        controllerFuture.addListener(
            { controllerFuture.get().removeListener(media3Listener) },
            ContextCompat.getMainExecutor(context),
        )
    }

    override suspend fun restoreLastSessionIfNeeded(): Episode? {
        val controller = controllerFuture.await()
        if (controller.mediaItemCount == 0) {
            val session = playbackSessionStorage.load() ?: return null
            if (session.items.isEmpty()) return null
            controller.setMediaItems(session.items, session.currentIndex, session.currentPositionMs)
            controller.prepare()
            controller.playbackParameters = controller.playbackParameters.withSpeed(session.playbackSpeed)
            if (session.wasPlaying && !session.isCompleted) controller.play() else controller.pause()
        }
        return controller.currentMediaItem?.let(::mediaItemToEpisode)
    }

    private fun mediaItemToEpisode(item: MediaItem): Episode {
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        val isLocal = uri.startsWith("file://") || uri.startsWith("content://")
        return Episode(
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

    private fun resolveLocalUri(localPath: String): Uri =
        if (localPath.startsWith("content://")) Uri.parse(localPath) else Uri.fromFile(File(localPath))

    private fun inferMediaType(uri: String): MediaType {
        if (uri.startsWith("content://")) {
            val mime = runCatching { context.contentResolver.getType(Uri.parse(uri)) }.getOrNull()
            if (mime?.startsWith("video/") == true) return MediaType.VIDEO
            if (mime?.startsWith("audio/") == true) return MediaType.AUDIO
        }
        val extension = uri.substringAfterLast('.', "").substringBefore('?').lowercase()
        return if (extension in setOf("mp4", "webm", "mkv", "mov", "avi", "m4v")) {
            MediaType.VIDEO
        } else {
            MediaType.AUDIO
        }
    }

    fun release() = MediaController.releaseFuture(controllerFuture)
    suspend fun awaitController(): MediaController = controllerFuture.await()

    companion object {
        @Volatile private var instance: PlayerController? = null

        fun getInstance(context: Context): PlayerController = instance ?: synchronized(this) {
            instance ?: PlayerController(context.applicationContext).also { instance = it }
        }
    }
}
