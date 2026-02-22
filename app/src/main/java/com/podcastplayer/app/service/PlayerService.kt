package com.podcastplayer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.podcastplayer.app.MainActivity
import com.podcastplayer.app.R
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.PlaybackProgressEntity
import com.podcastplayer.app.domain.model.Episode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var notificationManager: PlayerNotificationManager? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)
    private var persistJob: Job? = null
    private var pendingSeekToMs: Long? = null

    private val playbackProgressDao by lazy { DatabaseProvider.getDatabase(this).playbackProgressDao() }
    private val playbackSessionStorage by lazy { PlaybackSessionStorage(this) }

    private val CHANNEL_ID = "podcast_player_channel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
        setupNotification()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            setHandleAudioBecomingNoisy(true)
        }

        player?.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val episodeId = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return

                    serviceScope.launch(Dispatchers.IO) {
                        val saved = playbackProgressDao.getByEpisodeId(episodeId)
                        pendingSeekToMs = saved?.takeIf { !it.completed }?.positionMs
                    }
                    persistPlaybackSession(isCompleted = false)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val seek = pendingSeekToMs
                        if (seek != null && seek > 0) {
                            pendingSeekToMs = null
                            player?.seekTo(seek)
                        }
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        persistProgress(markCompleted = true)
                        persistPlaybackSession(isCompleted = true)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        startPersistLoop()
                    } else {
                        stopPersistLoop()
                        persistProgress(markCompleted = false)
                    }
                    persistPlaybackSession(isCompleted = false)
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    // e.g. user scrubs; record sooner.
                    persistProgress(markCompleted = false)
                    persistPlaybackSession(isCompleted = false)
                }
            }
        )

        mediaSession = MediaSession.Builder(this, player!!).build()
    }

    private fun startPersistLoop() {
        if (persistJob?.isActive == true) return
        persistJob = serviceScope.launch {
            while (true) {
                persistProgress(markCompleted = false)
                persistPlaybackSession(isCompleted = false)
                delay(5_000)
            }
        }
    }

    private fun stopPersistLoop() {
        persistJob?.cancel()
        persistJob = null
    }

    private fun persistPlaybackSession(isCompleted: Boolean) {
        val p = player ?: return
        val currentIndex = p.currentMediaItemIndex
        val items = p.currentMediaItems
        if (items.isEmpty() || currentIndex !in items.indices) return

        playbackSessionStorage.save(
            items = items,
            currentIndex = currentIndex,
            currentPositionMs = p.currentPosition,
            wasPlaying = p.playWhenReady && p.playbackState != Player.STATE_ENDED,
            playbackSpeed = p.playbackParameters.speed,
            isCompleted = isCompleted
        )
    }

    private fun persistProgress(markCompleted: Boolean) {
        val p = player ?: return
        val episodeId = p.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        val podcastId = p.mediaMetadata?.artist?.toString().orEmpty()

        val positionMs = p.currentPosition.coerceAtLeast(0)
        val durationMs = p.duration.coerceAtLeast(0)

        // If duration is unknown, avoid persisting nonsense completion.
        val shouldComplete = markCompleted || (
            durationMs > 0 && positionMs >= (durationMs - 2_000)
        )

        val now = System.currentTimeMillis()
        val entity = PlaybackProgressEntity(
            episodeId = episodeId,
            podcastId = podcastId,
            positionMs = if (shouldComplete) durationMs else positionMs,
            durationMs = durationMs,
            completed = shouldComplete,
            lastPlayedAtMs = now,
            updatedAtMs = now
        )

        serviceScope.launch(Dispatchers.IO) {
            playbackProgressDao.upsert(entity)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Podcast Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for podcast playback"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val descriptionAdapter = object : PlayerNotificationManager.MediaDescriptionAdapter {
            override fun getCurrentContentTitle(player: Player): CharSequence {
                return player.mediaMetadata.title ?: "Now playing"
            }

            override fun createCurrentContentIntent(player: Player): PendingIntent? {
                return pendingIntent
            }

            override fun getCurrentContentText(player: Player): CharSequence? {
                return player.mediaMetadata.artist
            }

            override fun getCurrentLargeIcon(
                player: Player,
                callback: PlayerNotificationManager.BitmapCallback
            ) = null
        }

        val listener = object : PlayerNotificationManager.NotificationListener {
            override fun onNotificationPosted(
                notificationId: Int,
                notification: Notification,
                ongoing: Boolean
            ) {
                if (ongoing) {
                    startForeground(notificationId, notification)
                }
            }

            override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        notificationManager = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, CHANNEL_ID)
            .setMediaDescriptionAdapter(descriptionAdapter)
            .setNotificationListener(listener)
            .setChannelImportance(NotificationManager.IMPORTANCE_LOW)
            .setSmallIconResourceId(R.drawable.ic_launcher_foreground)
            .build().apply {
                setMediaSessionToken(mediaSession?.sessionCompatToken!!)
                setPlayer(player)
            }
    }

    fun playEpisode(episode: Episode, artworkUrl: String?) {
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastId)
            .setDescription(episode.description)
            .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
            .build()

        val mediaUri = episode.localPath?.takeIf { episode.isDownloaded }?.let {
            Uri.fromFile(File(it))
        } ?: Uri.parse(episode.audioUrl)
        val mediaItem = MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(mediaUri)
            .setMediaMetadata(metadata)
            .build()

        player?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        stopPersistLoop()
        persistProgress(markCompleted = false)
        persistPlaybackSession(isCompleted = false)
        serviceJob.cancel()
        notificationManager?.setPlayer(null)
        notificationManager = null
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
