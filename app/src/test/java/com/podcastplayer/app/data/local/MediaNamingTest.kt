package com.podcastplayer.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The naming + matching rules that let the app recognize its own files again:
 * just-in-time download reuse, the post-reinstall restore flow, and the
 * duplicate cleaner all depend on [MediaNaming.matchKey] agreeing with what
 * [MediaNaming.episodeDisplayName] / [MediaNaming.urlDisplayName] produce.
 */
class MediaNamingTest {

    @Test
    fun `episode display name combines podcast and episode titles`() {
        assertEquals(
            "The Daily - Monday Briefing.mp3",
            MediaNaming.episodeDisplayName("The Daily", "Monday Briefing", "mp3"),
        )
    }

    @Test
    fun `episode display name falls back when parts are missing`() {
        assertEquals("episode.mp3", MediaNaming.episodeDisplayName(null, "  ", "mp3"))
        assertEquals("Solo Title.mp3", MediaNaming.episodeDisplayName(null, "Solo Title", "mp3"))
        assertEquals("The Daily.mp3", MediaNaming.episodeDisplayName("The Daily", "", "mp3"))
    }

    @Test
    fun `url display name strips at-prefix from uploader`() {
        assertEquals(
            "elonmusk - Rocket landing.mp4",
            MediaNaming.urlDisplayName("@elonmusk", "Rocket landing", "mp4"),
        )
        assertEquals("vibe-clip.mp4", MediaNaming.urlDisplayName(null, "", ""))
    }

    @Test
    fun `illegal filesystem characters are replaced`() {
        val name = MediaNaming.episodeDisplayName("A/B", "What? \"Quotes\": <Test>", "mp3")
        assertFalse(name.any { it in "\\/:*?\"<>|" })
    }

    @Test
    fun `match key ignores extension case and duplicate suffix`() {
        val original = "The Daily - Monday Briefing.mp3"
        assertEquals(MediaNaming.matchKey(original), MediaNaming.matchKey("The Daily - Monday Briefing (1).mp3"))
        assertEquals(MediaNaming.matchKey(original), MediaNaming.matchKey("The Daily - Monday Briefing (12).mp3"))
        assertEquals(MediaNaming.matchKey(original), MediaNaming.matchKey("the daily - monday briefing.MP3"))
        assertEquals(MediaNaming.matchKey(original), MediaNaming.matchKey("The Daily - Monday Briefing.m4a"))
    }

    @Test
    fun `match key keeps parenthesized words that are part of the title`() {
        // "(Live)" is content, not a MediaStore collision suffix — only a
        // trailing " (digits)" group is stripped.
        val live = MediaNaming.matchKey("Show - Episode (Live).mp3")
        assertTrue(live.endsWith("(live)"))
        assertNotEquals(live, MediaNaming.matchKey("Show - Episode.mp3"))
    }

    @Test
    fun `different episodes produce different match keys`() {
        assertNotEquals(
            MediaNaming.matchKey(MediaNaming.episodeDisplayName("Show", "Episode 1", "mp3")),
            MediaNaming.matchKey(MediaNaming.episodeDisplayName("Show", "Episode 2", "mp3")),
        )
    }

    @Test
    fun `display name and match key round-trip through sanitization`() {
        // What the writer stores must be recognized by the reader computing the
        // expected name from raw metadata — the exact reinstall-restore scenario.
        val stored = MediaNaming.episodeDisplayName("My Show", "Ep: 42 — The \"Answer\"?", "mp3")
        val expectedAtRestoreTime =
            MediaNaming.episodeDisplayName("My Show", "Ep: 42 — The \"Answer\"?", "mp3")
        assertEquals(MediaNaming.matchKey(stored), MediaNaming.matchKey(expectedAtRestoreTime))
        // ...and survives the platform appending a collision suffix.
        val collided = stored.removeSuffix(".mp3") + " (1).mp3"
        assertEquals(MediaNaming.matchKey(stored), MediaNaming.matchKey(collided))
    }

    @Test
    fun `hasDuplicateSuffix flags only collision-suffixed names`() {
        assertTrue(MediaNaming.hasDuplicateSuffix("Episode (1).mp3"))
        assertTrue(MediaNaming.hasDuplicateSuffix("Episode (23).mp3"))
        assertFalse(MediaNaming.hasDuplicateSuffix("Episode.mp3"))
        assertFalse(MediaNaming.hasDuplicateSuffix("Episode (Live).mp3"))
    }

    @Test
    fun `titleFromDisplayName strips extension and collision suffix`() {
        assertEquals("Lex - Interview", MediaNaming.titleFromDisplayName("Lex - Interview (2).mp4"))
        assertEquals("Lex - Interview", MediaNaming.titleFromDisplayName("Lex - Interview.mp4"))
    }

    @Test
    fun `sanitize caps length and never returns blank`() {
        assertEquals(120, MediaNaming.sanitize("x".repeat(300)).length)
        assertEquals("vibe-media", MediaNaming.sanitize(""))
    }
}
