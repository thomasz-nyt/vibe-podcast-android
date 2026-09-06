package com.podcastplayer.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalMediaReferenceTest {

    @Test
    fun `external and external primary aliases share a primary audio identity`() {
        val synthetic = "content://media/external/audio/media/42"
        val primary = "content://media/external_primary/audio/media/42"

        assertEquals(CanonicalMediaReference.keyOf(primary), CanonicalMediaReference.keyOf(synthetic))
        assertTrue(CanonicalMediaReference.equivalent(primary, synthetic))
    }

    @Test
    fun `collection volume and row remain identity boundaries`() {
        val audio = "content://media/external/audio/media/42"

        assertFalse(CanonicalMediaReference.equivalent(audio, "content://media/external/video/media/42"))
        assertFalse(CanonicalMediaReference.equivalent(audio, "content://media/external/audio/media/43"))
        assertFalse(CanonicalMediaReference.equivalent(audio, "content://media/ABCD-1234/audio/media/42"))
    }

    @Test
    fun `non MediaStore providers are not rewritten`() {
        val first = "content://example.provider/items/42"
        val second = "content://example.provider/items/43"

        assertFalse(CanonicalMediaReference.equivalent(first, second))
        assertEquals("uri:$first", CanonicalMediaReference.keyOf(first))
    }

    @Test
    fun `file URI and absolute path share a file identity`() {
        assertTrue(CanonicalMediaReference.equivalent("file:///tmp/episode.mp3", "/tmp/episode.mp3"))
    }
}
