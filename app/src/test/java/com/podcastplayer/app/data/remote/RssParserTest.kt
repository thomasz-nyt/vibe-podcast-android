package com.podcastplayer.app.data.remote

import com.podcastplayer.app.domain.model.Episode
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Coverage for [RssParser.parseEpisodes], focused on `pubDate` parsing.
 *
 * A podcast's queue-play feature picks the *latest* unplayed episode per
 * podcast by comparing `Episode.pubDate` (see
 * `PodcastViewModel.buildUnplayedEpisodesForPodcastQueue`). A feed whose dates
 * fail to parse falls back to feed order for that comparison — usually
 * harmless, but wrong for a feed that doesn't list items newest-first. These
 * tests pin down the date formats we're expected to handle.
 */
class RssParserTest {

    // Inject a real kxml2 parser: on the JVM the default XmlPullParserFactory
    // path resolves to the mockable android.jar, whose methods throw "Stub!".
    private val parser = RssParser(newPullParser = { org.kxml2.io.KXmlParser() })

    private fun episodesFor(pubDates: List<String>): List<Episode> {
        // Assembled with buildString rather than a trimIndent'd template:
        // trimIndent runs AFTER interpolation, so interpolated zero-indent
        // lines would stop the surrounding indentation from being stripped —
        // leaving whitespace before <?xml?>, a fatal prolog error for a
        // strict pull parser.
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<rss version=\"2.0\"><channel><title>Test Feed</title>\n")
            pubDates.forEachIndexed { index, pubDate ->
                append("<item>")
                append("<title>Episode $index</title>")
                append("<guid>ep-$index</guid>")
                append("<pubDate>$pubDate</pubDate>")
                append("<enclosure url=\"https://example.com/ep$index.mp3\" />")
                append("</item>\n")
            }
            append("</channel></rss>")
        }

        val stream = ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8))
        return parser.parseEpisodes(stream, "podcast-1")
    }

    @Test
    fun `parses RFC 822 dates with numeric offset`() {
        val episodes = episodesFor(listOf("Tue, 14 Jan 2025 08:00:00 +0000"))
        assertNotNull(episodes[0].pubDate)
        assertEquals(1736841600000L, episodes[0].pubDate!!.time)
    }

    @Test
    fun `parses RFC 822 dates with timezone name`() {
        val episodes = episodesFor(listOf("Tue, 14 Jan 2025 08:00:00 GMT"))
        assertNotNull(episodes[0].pubDate)
        assertEquals(1736841600000L, episodes[0].pubDate!!.time)
    }

    @Test
    fun `parses RFC 822 dates missing the weekday prefix`() {
        val episodes = episodesFor(listOf("14 Jan 2025 08:00:00 +0000"))
        assertNotNull(episodes[0].pubDate)
        assertEquals(1736841600000L, episodes[0].pubDate!!.time)
    }

    @Test
    fun `parses ISO-8601 dates from Atom-style or non-compliant feeds`() {
        val episodes = episodesFor(
            listOf(
                "2025-01-14T08:00:00Z",
                "2025-01-14T08:00:00+00:00",
                "2025-01-14T08:00:00.123Z",
                "2025-01-14T08:00:00+0000",
            ),
        )
        episodes.forEach { assertNotNull("expected a parsed date for ${it.title}", it.pubDate) }
        // All four represent the same instant (ignoring the .123s millis case).
        assertEquals(1736841600000L, episodes[0].pubDate!!.time)
        assertEquals(1736841600000L, episodes[1].pubDate!!.time)
        assertEquals(1736841600000L, episodes[3].pubDate!!.time)
    }

    @Test
    fun `garbage pubDate does not throw and yields null`() {
        val episodes = episodesFor(listOf("not a date at all", ""))
        assertNull(episodes[0].pubDate)
        assertNull(episodes[1].pubDate)
    }

    @Test
    fun `mixed RFC 822 and ISO-8601 dates still rank correctly by recency`() {
        // Reproduces the queue-play bug: a feed mixing date formats must still
        // let callers pick the true latest episode via max-by-pubDate, not
        // silently drop to feed-order tie-breaking because one format failed
        // to parse.
        val episodes = episodesFor(
            listOf(
                "Mon, 01 Jan 2024 08:00:00 +0000", // oldest, RFC 822
                "2025-06-01T08:00:00Z",             // newest, ISO-8601
                "Wed, 01 Jan 2025 08:00:00 +0000",  // middle, RFC 822
            ),
        )
        val latest = episodes.maxByOrNull { it.pubDate?.time ?: Long.MIN_VALUE }
        assertEquals("Episode 1", latest?.title)
    }

    @Test
    fun `parseDuration handles hh mm ss and bare seconds`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <item>
                        <title>A</title>
                        <guid>a</guid>
                        <itunes:duration>01:02:03</itunes:duration>
                        <enclosure url="https://example.com/a.mp3" />
                    </item>
                    <item>
                        <title>B</title>
                        <guid>b</guid>
                        <itunes:duration>90</itunes:duration>
                        <enclosure url="https://example.com/b.mp3" />
                    </item>
                </channel>
            </rss>
        """.trimIndent()
        val episodes = parser.parseEpisodes(
            ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)),
            "podcast-1",
        )
        assertEquals((1 * 3600 + 2 * 60 + 3) * 1000L, episodes[0].duration)
        assertEquals(90 * 1000L, episodes[1].duration)
        assertEquals(2, episodes.size)
    }
}
