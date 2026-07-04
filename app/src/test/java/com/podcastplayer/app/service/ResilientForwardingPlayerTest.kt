package com.podcastplayer.app.service

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class ResilientForwardingPlayerTest {
    @Test
    fun playAfterCompletionSeeksToBeginning() {
        val calls = mutableListOf<String>()
        val player = ResilientForwardingPlayer(fakePlayer(Player.STATE_ENDED, 1, calls))

        player.play()

        assertEquals(listOf("seekToDefaultPosition", "play"), calls)
    }

    @Test
    fun playFromIdleWithItemPreparesBeforeRetry() {
        val calls = mutableListOf<String>()
        val player = ResilientForwardingPlayer(fakePlayer(Player.STATE_IDLE, 1, calls))

        player.play()

        assertEquals(listOf("prepare", "play"), calls)
    }

    @Test
    fun normalPausedPlaybackDoesNotChangePosition() {
        val calls = mutableListOf<String>()
        val player = ResilientForwardingPlayer(fakePlayer(Player.STATE_READY, 1, calls))

        player.play()

        assertEquals(listOf("play"), calls)
    }

    private fun fakePlayer(state: Int, itemCount: Int, calls: MutableList<String>): Player {
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getPlaybackState" -> state
                "getMediaItemCount" -> itemCount
                "seekToDefaultPosition", "prepare", "play" -> {
                    calls += method.name
                    null
                }
                else -> defaultValue(method.returnType)
            }
        } as Player
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }
}
