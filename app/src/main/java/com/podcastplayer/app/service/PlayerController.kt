package com.podcastplayer.app.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.MediaPayloadAvailability
import com.podcastplayer.app.data.local.MediaPayloadProbe
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
    private val payloadProbe = MediaPayloadProbe(context)
    private val playbackProgressDao by lazy { DatabaseProvider.getDatabase(context).playbackProgressDao() }
    private val listeners = ConcurrentHashMap<PlaybackControllerListener, Player.Listener>()
    private val mediaTypeCache = ConcurrentHashMap<String, MediaType>()

    @Volatile
    private var latestPlaybackRequest = 0L

    override fun beginPlaybackRequest(requestId: Long) {
        latestPlaybackRequest = requestId
    }

    private suspend fun episodeToMediaItem(episode: Episode, artworkUrl: String?): MediaItem {
        // Stash the already-known media type in the metadata extras so reading it back
        // in mediaItemToEpisode() is a free field read instead of a contentResolver IPC.
        val extras = Bundle().apply { putString(EXTRA_MEDIA_TYPE, episode.mediaType.tag) }
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastId)
            .setDescription(episode.description)
            .setArtworkUri(artworkUrl?.let(Uri::parse))
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(resolvePlayableUri(episode))
            .setMediaMetadata(metadata)
            .build()
    }

    private suspend fun resolvePlayableUri(episode: Episode): Uri = withContext(Dispatchers.IO) {
        val localPath = episode.localPath?.takeIf { episode.isDownloaded }
            ?: return@withContext Uri.parse(episode.audioUrl)
        when (val availability = payloadProbe.probe(localPath)) {
            is MediaPayloadAvailability.Available -> resolveLocalUri(availability.reference)
            is MediaPayloadAvailability.Missing -> {
                if (episode.id.startsWith("url:")) {
                    throw java.io.FileNotFoundException("Downloaded URL media is missing")
                }
                Uri.parse(episode.audioUrl)
            }
            is MediaPayloadAvailability.PermissionRequired ->
                throw SecurityException("Media access is required for this download")
            is MediaPayloadAvailability.Unreadable ->
                throw java.io.IOException(availability.reason ?: "Downloaded media cannot be read")
        }
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
            val playable = withContext(Dispatchers.IO) {
                session.items.withIndex().filter { (_, item) ->
                    val uri = item.localConfiguration?.uri?.toString().orEmpty()
                    !isLocalReference(uri) || payloadProbe.probe(uri) is MediaPayloadAvailability.Available
                }
            }
            if (playable.isEmpty()) {
                playbackSessionStorage.clear()
                return null
            }
            val restoredIndex = playable.indexOfFirst { it.index >= session.currentIndex }
                .takeIf { it >= 0 } ?: playable.lastIndex
            val restoredPosition = if (playable[restoredIndex].index == session.currentIndex) {
                session.currentPositionMs
            } else {
                0L
            }
            controller.setMediaItems(playable.map { it.value }, restoredIndex, restoredPosition)
            controller.prepare()
            controller.playbackParameters = controller.playbackParameters.withSpeed(session.playbackSpeed)
            if (session.wasPlaying && !session.isCompleted) controller.play() else controller.pause()
        }
        return controller.currentMediaItem?.let(::mediaItemToEpisode)
    }

    private fun mediaItemToEpisode(item: MediaItem): Episode {
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        val isLocal = isLocalReference(uri)
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
            mediaType = resolveMediaType(item, uri),
        )
    }

    private fun resolveLocalUri(localPath: String): Uri =
        if (localPath.startsWith("content://")) Uri.parse(localPath) else Uri.fromFile(File(localPath))

    private fun isLocalReference(uri: String): Boolean =
        uri.startsWith("file://") || uri.startsWith("content://")

    /**
     * Resolve the [MediaType] for [item]/[uri] without hitting the main-thread
     * contentResolver IPC on every call. [snapshotOf] runs on every Player event AND
     * a 1 Hz position ticker, so this is invoked far more often than once per media item.
     *
     * Prefers the type stashed in the MediaMetadata extras by [episodeToMediaItem] (a
     * free field read). Falls back to [inferMediaType] for items that didn't go through
     * that path (e.g. restored from [PlaybackSessionStorage]), memoizing the result per
     * URI so the resolver is hit at most once per distinct media item.
     */
    private fun resolveMediaType(item: MediaItem, uri: String): MediaType {
        item.mediaMetadata.extras?.getString(EXTRA_MEDIA_TYPE)?.let { tag ->
            return MediaType.fromTag(tag)
        }
        if (uri.isBlank()) return MediaType.AUDIO
        return mediaTypeCache.getOrPut(uri) { inferMediaType(uri) }
    }

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
        private const val EXTRA_MEDIA_TYPE = "com.podcastplayer.app.mediaType"

        fun getInstance(context: Context): PlayerController = instance ?: synchronized(this) {
            instance ?: PlayerController(context.applicationContext).also { instance = it }
        }
    }
}
