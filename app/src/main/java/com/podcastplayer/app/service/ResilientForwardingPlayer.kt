package com.podcastplayer.app.service

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Applies the same restart/retry behavior to app, notification, and lock-screen controls. */
@UnstableApi
class ResilientForwardingPlayer(player: Player) : ForwardingPlayer(player) {
    override fun play() {
        when {
            playbackState == Player.STATE_ENDED -> seekToDefaultPosition()
            playbackState == Player.STATE_IDLE && mediaItemCount > 0 -> prepare()
        }
        super.play()
    }
}
