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

class PlayerController internal constructor(
    private val context: Context,
    private val sessionToken: SessionToken = SessionToken(context, ComponentName(context, PlayerService::class.java)),
) : PlaybackController {
    private val connection = ReconnectableConnection(
        connect = {
            val future = MediaController.Builder(context, sessionToken)
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        this@PlayerController.onDisconnected(controller)
                    }
                }).buildAsync()
            try {
                future.await().also { controller ->
                    listeners.keys.forEach { attachListener(controller, it) }
                }
            } catch (error: Exception) {
                MediaController.releaseFuture(future)
                throw error
            }
        },
        isConnected = { controller: MediaController -> controller.isConnected },
        release = { controller -> controller.release() },
    )
    val connectedController get() = connection.state
    private var lastSnapshot: ControllerSnapshot? = null
    private var recoveryItems: List<MediaItem> = emptyList()
    private var recoveryIndex = 0
    private var recoveryPosition = 0L
    private var recoverySpeed = 1f

    private fun onDisconnected(controller: MediaController) {
        if (connection.state.value !== controller) return
        connection.invalidate(controller)
        val snapshot = lastSnapshot ?: return
        listeners.keys.forEach { listener ->
            listener.onSnapshotChanged(snapshot.copy(playbackError = "Player disconnected"))
        }
    }
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
        val extras = Bundle().apply {
            putString(EXTRA_MEDIA_TYPE, episode.mediaType.tag)
            putString(EXTRA_ORIGINAL_URL, episode.audioUrl)
        }
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
        if (requestId != latestPlaybackRequest) return null
        PlaybackDownloadProtection.episodeIds = setOf(episode.id)
        val startMs = resumePositionFor(episode.id) ?: 0L
        if (requestId != latestPlaybackRequest) return null
        val controller = awaitController()
        if (requestId != latestPlaybackRequest) return null
        val item = episodeToMediaItem(episode, artworkUrl)
        if (requestId != latestPlaybackRequest) return null
        controller.setMediaItem(item, startMs)
        controller.prepare()
        return startMs
    }

    override suspend fun prepareEpisodes(
        episodes: List<Episode>,
        defaultArtworkUrl: String?,
        requestId: Long,
    ): Long? {
        if (episodes.isEmpty() || requestId != latestPlaybackRequest) return null
        PlaybackDownloadProtection.episodeIds = episodes.mapTo(hashSetOf()) { it.id }
        val startMs = resumePositionFor(episodes.first().id) ?: 0L
        if (requestId != latestPlaybackRequest) return null
        val items = episodes.map { episode ->
            episodeToMediaItem(episode, episode.imageUrl ?: defaultArtworkUrl)
        }
        val controller = awaitController()
        if (requestId != latestPlaybackRequest) return null
        controller.setMediaItems(items, 0, startMs)
        controller.prepare()
        return startMs
    }

    override suspend fun play(requestId: Long?) {
        if (requestId != null && requestId != latestPlaybackRequest) return
        val controller = awaitController()
        if (requestId != null && requestId != latestPlaybackRequest) return
        restoreRecoveryPlaylist(controller)
        if (controller.mediaItemCount == 0) error("Choose an episode to play")
        if (controller.playbackState == Player.STATE_IDLE || controller.playerError != null) controller.prepare()
        controller.play()
    }

    override suspend fun pause() {
        val request = latestPlaybackRequest
        val controller = awaitController()
        if (request == latestPlaybackRequest) controller.pause()
    }
    override suspend fun seekTo(position: Long) = awaitController().seekTo(position)
    override suspend fun skipToPrevious() = navigate(next = false)
    override suspend fun skipToNext() = navigate(next = true)

    private suspend fun navigate(next: Boolean) {
        val request = latestPlaybackRequest
        val controller = awaitController()
        if (request != latestPlaybackRequest) return
        restoreRecoveryPlaylist(controller)
        if (next) controller.seekToNextMediaItem() else controller.seekToPreviousMediaItem()
    }

    override suspend fun recover(requestId: Long) {
        val controller = awaitController()
        if (requestId != latestPlaybackRequest) return
        restoreRecoveryPlaylist(controller)
        val items = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it) }
        val index = controller.currentMediaItemIndex.coerceAtLeast(0)
        val position = controller.currentPosition.coerceAtLeast(0)
        val speed = controller.playbackParameters.speed
        val playable = withContext(Dispatchers.IO) { items.map { withStreamingFallback(it) } }
        if (requestId != latestPlaybackRequest) return
        if (playable.isEmpty()) {
            restoreLastSessionIfNeeded()
            if (requestId != latestPlaybackRequest) return
            if (controller.mediaItemCount == 0) error("Choose an episode to play")
        } else {
            controller.setMediaItems(playable, index, position)
            controller.playbackParameters = controller.playbackParameters.withSpeed(speed)
        }
        controller.prepare()
        controller.play()
    }

    private fun restoreRecoveryPlaylist(controller: MediaController) {
        if (controller.mediaItemCount != 0 || recoveryItems.isEmpty()) return
        controller.setMediaItems(recoveryItems, recoveryIndex, recoveryPosition)
        controller.playbackParameters = controller.playbackParameters.withSpeed(recoverySpeed)
    }

    private fun withStreamingFallback(item: MediaItem): MediaItem {
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        if (!isLocalReference(uri) || payloadProbe.probe(uri) is MediaPayloadAvailability.Available) return item
        val original = item.mediaMetadata.extras?.getString(EXTRA_ORIGINAL_URL)
        return if (original?.startsWith("http") == true) item.buildUpon().setUri(original).build() else item
    }

    override suspend fun setPlaybackSpeed(speed: Float) {
        val request = latestPlaybackRequest
        val controller = awaitController()
        if (request != latestPlaybackRequest) return
        controller.playbackParameters = controller.playbackParameters.withSpeed(speed)
    }

    override suspend fun stop() {
        val request = latestPlaybackRequest
        awaitController().run {
            if (request != latestPlaybackRequest) return
            stop()
            clearMediaItems()
            recoveryItems = emptyList()
            PlaybackDownloadProtection.episodeIds = emptySet()
        }
    }

    override suspend fun snapshot(): ControllerSnapshot = snapshotOf(awaitController())

    private fun snapshotOf(controller: MediaController): ControllerSnapshot {
        val item = controller.currentMediaItem
        val episode = item?.let(::mediaItemToEpisode)
        if (controller.mediaItemCount > 0) {
            recoveryItems = (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it) }
            recoveryIndex = controller.currentMediaItemIndex.coerceAtLeast(0)
            recoveryPosition = controller.currentPosition.coerceAtLeast(0)
            recoverySpeed = controller.playbackParameters.speed
            PlaybackDownloadProtection.episodeIds = recoveryItems.drop(recoveryIndex).mapTo(hashSetOf()) { it.mediaId }
        }
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
        ).also { lastSnapshot = it }
    }

    override suspend fun addListener(listener: PlaybackControllerListener) {
        // Register before connecting so recovery after an initial connection failure reattaches it too.
        listeners[listener] = object : Player.Listener {}
        val controller = awaitController()
        attachListener(controller, listener)
        listener.onSnapshotChanged(snapshotOf(controller))
    }

    private fun attachListener(controller: MediaController, listener: PlaybackControllerListener) {
        val adapter = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (controller.isConnected) listener.onSnapshotChanged(snapshotOf(controller))
            }
        }
        listeners.put(listener, adapter)?.let(controller::removeListener)
        controller.addListener(adapter)
    }

    override fun removeListener(listener: PlaybackControllerListener) {
        val adapter = listeners.remove(listener) ?: return
        connection.state.value?.removeListener(adapter)
    }

    override suspend fun restoreLastSessionIfNeeded(): Episode? {
        val request = latestPlaybackRequest
        val controller = awaitController()
        if (request != latestPlaybackRequest) return null
        if (controller.mediaItemCount == 0) {
            val session = playbackSessionStorage.load() ?: return null
            val playable = withContext(Dispatchers.IO) {
                session.items.map(::withStreamingFallback).withIndex().filter { (_, item) ->
                    val uri = item.localConfiguration?.uri?.toString().orEmpty()
                    !isLocalReference(uri) || payloadProbe.probe(uri) is MediaPayloadAvailability.Available
                }
            }
            if (request != latestPlaybackRequest) return null
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
            audioUrl = item.mediaMetadata.extras?.getString(EXTRA_ORIGINAL_URL) ?: uri,
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

    fun release() = connection.invalidate()
    suspend fun awaitController(): MediaController = withContext(Dispatchers.Main.immediate) { connection.await() }

    companion object {
        @Volatile private var instance: PlayerController? = null
        const val EXTRA_ORIGINAL_URL = "com.podcastplayer.app.originalUrl"
        private const val EXTRA_MEDIA_TYPE = "com.podcastplayer.app.mediaType"

        fun getInstance(context: Context): PlayerController = instance ?: synchronized(this) {
            instance ?: PlayerController(context.applicationContext).also { instance = it }
        }
    }
}
