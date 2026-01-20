package com.podcastplayer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.ui.PlayerNotificationManager
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.MainActivity
import com.podcastplayer.app.R

class PlayerService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var notificationManager: PlayerNotificationManager? = null
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

        mediaSession = MediaSession.Builder(this, player!!).build()
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

        val mediaItem = MediaItem.Builder()
            .setUri(episode.localPath?.takeIf { episode.isDownloaded } ?: episode.audioUrl)
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
        notificationManager?.setPlayer(null)
        notificationManager = null
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
