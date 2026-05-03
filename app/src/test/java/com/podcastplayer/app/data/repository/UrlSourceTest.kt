package com.podcastplayer.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for the URL → [UrlSource] classifier (issue #33).
 */
class UrlSourceTest {

    @Test
    fun `youtube com classifies as YOUTUBE`() {
        assertEquals(UrlSource.YOUTUBE, UrlSource.classify("https://www.youtube.com/watch?v=abc"))
        assertEquals(UrlSource.YOUTUBE, UrlSource.classify("https://m.youtube.com/watch?v=abc"))
        assertEquals(UrlSource.YOUTUBE, UrlSource.classify("https://music.youtube.com/watch?v=abc"))
    }

    @Test
    fun `youtu_be short link classifies as YOUTUBE`() {
        assertEquals(UrlSource.YOUTUBE, UrlSource.classify("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `x com and twitter com classify as X`() {
        assertEquals(UrlSource.X, UrlSource.classify("https://x.com/account/status/1"))
        assertEquals(UrlSource.X, UrlSource.classify("https://twitter.com/account/status/1"))
        assertEquals(UrlSource.X, UrlSource.classify("https://mobile.twitter.com/account/status/1"))
    }

    @Test
    fun `unrelated hosts classify as OTHER`() {
        assertEquals(UrlSource.OTHER, UrlSource.classify("https://example.com/anything"))
        assertEquals(UrlSource.OTHER, UrlSource.classify("https://vimeo.com/123"))
    }

    @Test
    fun `malformed input falls back to OTHER`() {
        assertEquals(UrlSource.OTHER, UrlSource.classify(""))
        assertEquals(UrlSource.OTHER, UrlSource.classify("not a url"))
    }

    @Test
    fun `tag round-trips through fromTag`() {
        assertEquals(UrlSource.YOUTUBE, UrlSource.fromTag("youtube"))
        assertEquals(UrlSource.X, UrlSource.fromTag("x"))
        assertEquals(UrlSource.OTHER, UrlSource.fromTag("other"))
        assertEquals(UrlSource.OTHER, UrlSource.fromTag("unknown"))
    }
}
