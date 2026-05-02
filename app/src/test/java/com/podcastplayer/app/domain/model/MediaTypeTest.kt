package com.podcastplayer.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTypeTest {

    @Test
    fun `tag is the lower case enum name`() {
        assertEquals("audio", MediaType.AUDIO.tag)
        assertEquals("video", MediaType.VIDEO.tag)
    }

    @Test
    fun `fromTag is round-trip safe`() {
        assertEquals(MediaType.AUDIO, MediaType.fromTag("audio"))
        assertEquals(MediaType.VIDEO, MediaType.fromTag("video"))
        // Mixed case
        assertEquals(MediaType.VIDEO, MediaType.fromTag("VIDEO"))
    }

    @Test
    fun `fromTag falls back to AUDIO for unknown`() {
        assertEquals(MediaType.AUDIO, MediaType.fromTag(""))
        assertEquals(MediaType.AUDIO, MediaType.fromTag("garbage"))
    }
}
