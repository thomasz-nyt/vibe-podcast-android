package com.podcastplayer.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for URL utilities used by the "Add from URL" feature (issue #33).
 *
 * Pure JVM (no Android dependencies) — `Uri.parse` works on the JVM via the
 * Robolectric-free shim in modern AGP. If the host JVM lacks `android.net.Uri`
 * the relevant assertions are skipped via try/catch in [UrlValidator] itself.
 */
class UrlValidatorTest {

    @Test
    fun `extractFirstUrl returns null for blank input`() {
        assertNull(UrlValidator.extractFirstUrl(null))
        assertNull(UrlValidator.extractFirstUrl(""))
        assertNull(UrlValidator.extractFirstUrl("   "))
    }

    @Test
    fun `extractFirstUrl pulls a URL out of share-style text`() {
        val text = "Check this out https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s 🔥"
        val extracted = UrlValidator.extractFirstUrl(text)
        assertNotNull(extracted)
        assertTrue(extracted!!.startsWith("https://www.youtube.com"))
    }

    @Test
    fun `extractFirstUrl handles youtu_be short links`() {
        val extracted = UrlValidator.extractFirstUrl("Hey: https://youtu.be/abc-123")
        assertEquals("https://youtu.be/abc-123", extracted)
    }

    @Test
    fun `extractFirstUrl handles X com links`() {
        val extracted = UrlValidator.extractFirstUrl("https://x.com/elonmusk/status/1234567890")
        assertEquals("https://x.com/elonmusk/status/1234567890", extracted)
    }

    @Test
    fun `isSupportedUrl recognizes YouTube and X`() {
        assertTrue(UrlValidator.isSupportedUrl("https://www.youtube.com/watch?v=abc"))
        assertTrue(UrlValidator.isSupportedUrl("https://youtu.be/abc"))
        assertTrue(UrlValidator.isSupportedUrl("https://x.com/some/post"))
        assertTrue(UrlValidator.isSupportedUrl("https://twitter.com/some/post"))
    }

    @Test
    fun `isSupportedUrl rejects unrelated URLs`() {
        assertFalse(UrlValidator.isSupportedUrl("https://example.com/article"))
        assertFalse(UrlValidator.isSupportedUrl("https://vimeo.com/12345"))
        assertFalse(UrlValidator.isSupportedUrl("not a url"))
    }

    @Test
    fun `stableId returns the same hash for the same url and media type`() {
        val a = UrlValidator.stableId("https://youtu.be/abc", "audio")
        val b = UrlValidator.stableId("https://youtu.be/abc", "audio")
        assertEquals(a, b)
    }

    @Test
    fun `stableId differentiates audio vs video for the same URL`() {
        val audio = UrlValidator.stableId("https://youtu.be/abc", "audio")
        val video = UrlValidator.stableId("https://youtu.be/abc", "video")
        assertNotEquals(audio, video)
    }

    @Test
    fun `canonicalize strips tracking params but keeps v and t`() {
        val raw = "https://www.youtube.com/watch?v=abc&utm_source=tweet&utm_medium=share&t=120"
        val canon = UrlValidator.canonicalize(raw)
        assertTrue(canon.contains("v=abc"))
        assertTrue(canon.contains("t=120"))
        assertFalse(canon.contains("utm_"))
    }
}
