package com.podcastplayer.app.service

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Debug-only fixture for deterministic session/service loss in connected tests. */
class PlaybackLifecycleTestService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        creations++
        session = MediaSession.Builder(this, ExoPlayer.Builder(this).build())
            .setId("playback-lifecycle-test")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    fun disconnect() {
        session?.let {
            it.player.stop()
            it.release()
            it.player.release()
        }
        session = null
        stopSelf()
    }

    override fun onDestroy() {
        session?.let { it.release(); it.player.release() }
        session = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        var instance: PlaybackLifecycleTestService? = null
            private set
        var creations = 0
            private set
    }
}
