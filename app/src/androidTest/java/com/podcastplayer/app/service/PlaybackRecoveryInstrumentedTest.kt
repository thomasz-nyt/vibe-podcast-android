package com.podcastplayer.app.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podcastplayer.app.domain.model.Episode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackRecoveryInstrumentedTest {
    @Test
    fun reconnectAfterServiceLossPreservesPlaylistPositionSpeedAndOriginalUrl() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val media = wav(context, 10)
        val controller = withContext(Dispatchers.Main) {
            PlayerController(context, SessionToken(context,
                ComponentName(context, PlaybackLifecycleTestService::class.java)))
        }
        var updates = 0
        val listener = PlaybackControllerListener { updates++ }
        try {
            withContext(Dispatchers.Main) {
                controller.addListener(listener)
                controller.beginPlaybackRequest(1)
                controller.prepareEpisodes(listOf(episode("first", media), episode("second", media)), null, 1)
                controller.setPlaybackSpeed(1.5f)
                controller.seekTo(2_000)
            }
            waitFor { controller.snapshot().playbackState == Player.STATE_READY }
            val old = withContext(Dispatchers.Main) { controller.awaitController() }
            val count = PlaybackLifecycleTestService.creations
            withContext(Dispatchers.Main) {
                controller.snapshot() // Capture the latest playlist/position before session loss.
                PlaybackLifecycleTestService.instance!!.disconnect()
            }
            waitFor { controller.connectedController.value == null && PlaybackLifecycleTestService.instance == null }
            val before = updates
            withContext(Dispatchers.Main) { controller.recover(1) }
            waitFor { controller.snapshot().playbackState == Player.STATE_READY }
            withContext(Dispatchers.Main) {
                val fresh = controller.awaitController()
                assertNotSame(old, fresh)
                assertTrue(PlaybackLifecycleTestService.creations > count)
                assertEquals(2, fresh.mediaItemCount)
                assertEquals("first", fresh.currentMediaItem?.mediaId)
                assertTrue(fresh.currentPosition >= 2_000)
                assertEquals(1.5f, fresh.playbackParameters.speed, 0.01f)
                assertEquals("https://example.com/first.wav", controller.snapshot().currentEpisode?.audioUrl)
                assertTrue(updates > before)
                controller.beginPlaybackRequest(2)
                controller.skipToNext()
                controller.play(2)
            }
            waitFor { controller.snapshot().currentEpisode?.id == "second" }
        } finally {
            withContext(Dispatchers.Main) {
                controller.removeListener(listener)
                controller.release()
                PlaybackLifecycleTestService.instance?.disconnect()
            }
            media.delete()
        }
    }

    @Test
    fun naturalAdvanceCompletesOutgoingEpisodeButManualSkipDoesNot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val media = wav(context, 2)
        val prefix = java.util.UUID.randomUUID().toString()
        val first = episode("$prefix-first", media)
        val second = episode("$prefix-second", media)
        val third = episode("$prefix-third", media)
        val progress = com.podcastplayer.app.data.local.DatabaseProvider.getDatabase(context).playbackProgressDao()
        val controller = withContext(Dispatchers.Main) { PlayerController(context) }
        val activity = androidx.test.core.app.ActivityScenario.launch(com.podcastplayer.app.MainActivity::class.java)
        try {
            withContext(Dispatchers.Main) {
                controller.beginPlaybackRequest(1)
                controller.prepareEpisodes(listOf(first, second, third), null, 1)
                controller.play(1)
            }
            waitFor { controller.snapshot().currentEpisode?.id == second.id }
            withTimeout(5_000) {
                while (progress.getByEpisodeId(first.id)?.completed != true) delay(50)
            }
            withContext(Dispatchers.Main) {
                controller.beginPlaybackRequest(2)
                controller.skipToNext()
                controller.pause()
            }
            waitFor { controller.snapshot().currentEpisode?.id == third.id }
            withTimeout(5_000) {
                while (progress.getByEpisodeId(second.id) == null) delay(50)
            }
            assertEquals(false, progress.getByEpisodeId(second.id)!!.completed)
        } finally {
            withContext(Dispatchers.Main) { controller.stop(); controller.release() }
            activity.close()
            listOf(first, second, third).forEach { progress.deleteByEpisodeId(it.id) }
            media.delete()
        }
    }

    private suspend fun waitFor(condition: suspend () -> Boolean) = withTimeout(15_000) {
        while (!withContext(Dispatchers.Main) { condition() }) delay(50)
    }

    private fun episode(id: String, file: File) = Episode(
        id = id, podcastId = "lifecycle-test", title = id, description = null, pubDate = null,
        audioUrl = "https://example.com/$id.wav", duration = 10_000,
        isDownloaded = true, localPath = file.absolutePath,
    )

    private fun wav(context: Context, seconds: Int): File {
        val bytes = seconds * 8_000 * 2
        val buffer = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(bytes)
        return File(context.cacheDir, "lifecycle-test.wav").apply { writeBytes(buffer.array()) }
    }
}
