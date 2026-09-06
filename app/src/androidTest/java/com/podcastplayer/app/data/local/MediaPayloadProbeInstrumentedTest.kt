package com.podcastplayer.app.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaPayloadProbeInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val probe = MediaPayloadProbe(context)

    @Test
    fun filePayloadDistinguishesAvailableMissingAndEmpty() {
        val available = File(context.cacheDir, "availability-test.mp3").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val empty = File(context.cacheDir, "availability-empty.mp3").apply { writeBytes(byteArrayOf()) }
        val missing = File(context.cacheDir, "availability-missing.mp3").apply { delete() }

        try {
            assertTrue(probe.probe(available.absolutePath) is MediaPayloadAvailability.Available)
            assertTrue(probe.probe(missing.absolutePath) is MediaPayloadAvailability.Missing)
            assertTrue(probe.probe(empty.absolutePath) is MediaPayloadAvailability.Unreadable)
        } finally {
            available.delete()
            empty.delete()
        }
    }

    @Test
    fun mediaStorePrimaryAndSyntheticUrisResolveToTheSameReadableAsset() {
        val saved = MediaStoreSaver.saveAudio(
            context = context,
            displayName = "Vibe availability test.mp3",
            mimeType = "audio/mpeg",
        ) { output -> output.write(byteArrayOf(1, 2, 3, 4)) }
        requireNotNull(saved)

        val primary = saved.uri.toString()
        val synthetic = primary.replace("/external_primary/", "/external/")
        try {
            assertEquals(CanonicalMediaReference.keyOf(primary), CanonicalMediaReference.keyOf(synthetic))
            assertTrue(probe.probe(primary) is MediaPayloadAvailability.Available)
            assertTrue(probe.probe(synthetic) is MediaPayloadAvailability.Available)
            val scan = MediaStoreScanner(context).scanAll()
            assertTrue(scan is MediaStoreScanner.ScanResult.Success)
            val item = (scan as MediaStoreScanner.ScanResult.Success).items
                .first { it.canonicalKey == CanonicalMediaReference.keyOf(primary) }
            assertEquals(CanonicalMediaReference.keyOf(primary), item.canonicalKey)
        } finally {
            context.contentResolver.delete(saved.uri, null, null)
        }
    }
}
