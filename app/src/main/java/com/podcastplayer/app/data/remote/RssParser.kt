package com.podcastplayer.app.data.remote

import com.podcastplayer.app.domain.model.Episode
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class RssParser(
    /**
     * How to obtain a pull parser. The default goes through the platform
     * factory (KXmlParser on device); JVM unit tests inject a real kxml2
     * parser directly because the mockable android.jar stubs the factory.
     */
    private val newPullParser: () -> XmlPullParser = {
        XmlPullParserFactory.newInstance().newPullParser()
    },
) {

    fun parsePodcast(inputStream: InputStream, feedUrl: String): PodcastFeedMetadata {
        val parser = newPullParser()
        parser.setInput(inputStream, null)

        var inChannel = false
        var inItem = false
        var currentTag: String? = null
        val currentText = StringBuilder()
        var title = ""
        var author = ""
        var imageUrl: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "channel", "feed" -> inChannel = true
                        "item", "entry" -> inItem = true
                        "itunes:image" -> if (inChannel && !inItem) {
                            imageUrl = parser.getAttributeValue(null, "href") ?: imageUrl
                        }
                        "image" -> if (inChannel && !inItem) {
                            parser.getAttributeValue(null, "href")?.let { imageUrl = it }
                        }
                        "media:thumbnail" -> if (inChannel && !inItem) {
                            imageUrl = parser.getAttributeValue(null, "url") ?: imageUrl
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inChannel && !inItem && currentTag != null) {
                        currentText.append(parser.text)
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (inChannel && !inItem) {
                        when (parser.name) {
                            "title" -> if (title.isBlank()) title = currentText.toString().trim()
                            "itunes:author", "author", "name" ->
                                if (author.isBlank()) author = currentText.toString().trim()
                            "url" -> if (imageUrl.isNullOrBlank()) imageUrl = currentText.toString().trim()
                        }
                    }
                    when (parser.name) {
                        "item", "entry" -> inItem = false
                        "channel", "feed" -> inChannel = false
                    }
                    currentText.clear()
                    currentTag = null
                }
            }
            eventType = parser.next()
        }

        return PodcastFeedMetadata(
            title = title.ifBlank { feedUrl },
            author = author,
            artworkUrl = imageUrl?.takeIf { it.isNotBlank() },
        )
    }

    fun parseEpisodes(inputStream: InputStream, podcastId: String): List<Episode> {
        val parser = newPullParser()
        parser.setInput(inputStream, null)

        val episodes = mutableListOf<Episode>()
        var inItem = false
        var title = ""
        var description: String? = null
        var pubDate: Date? = null
        var audioUrl = ""
        var duration: Long? = null
        var imageUrl: String? = null
        var guid: String? = null
        var currentTag: String? = null
        val currentText = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "item") {
                        inItem = true
                        title = ""
                        description = null
                        pubDate = null
                        audioUrl = ""
                        duration = null
                        imageUrl = null
                        guid = null
                    }
                    if (inItem && currentTag == "enclosure") {
                        audioUrl = parser.getAttributeValue(null, "url") ?: audioUrl
                    }
                    if (inItem && (currentTag == "itunes:image" || currentTag == "media:thumbnail" || currentTag == "media:content")) {
                        imageUrl = parser.getAttributeValue(null, "href")
                            ?: parser.getAttributeValue(null, "url")
                            ?: imageUrl
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inItem && currentTag != null && currentTag != "item") {
                        currentText.append(parser.text)
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (inItem) {
                        when (parser.name) {
                            "item" -> {
                                val resolvedId = guid?.takeIf { it.isNotBlank() }
                                    ?: audioUrl.takeIf { it.isNotBlank() }
                                    ?: "${podcastId}_${episodes.size}"
                                episodes.add(
                                    Episode(
                                        id = resolvedId,
                                        podcastId = podcastId,
                                        title = title.trim(),
                                        description = description?.trim(),
                                        pubDate = pubDate,
                                        audioUrl = audioUrl,
                                        duration = duration,
                                        imageUrl = imageUrl
                                    )
                                )
                                inItem = false
                            }

                            "title" -> {
                                title = currentText.toString()
                            }

                            "description" -> {
                                description = currentText.toString()
                            }

                            "guid" -> {
                                guid = currentText.toString().trim()
                            }

                            "pubDate" -> {
                                pubDate = parseDate(currentText.toString().trim())
                            }

                            "itunes:duration" -> {
                                duration = parseDuration(currentText.toString().trim())
                            }
                        }
                    }
                    currentText.clear()
                    currentTag = null
                }
            }
            eventType = parser.next()
        }

        return episodes
    }

    /**
     * Parses a `<pubDate>` value.
     *
     * The queue-play feature picks the *latest* unplayed episode per podcast by
     * comparing this value, so a feed whose dates fail to parse silently falls
     * back to feed order — normally harmless (feeds list newest-first) but wrong
     * for feeds that don't. RFC 822 (the RSS 2.0 spec'd format) covers most
     * feeds; the ISO-8601 fallback covers Atom-style feeds and the non-compliant
     * RSS generators that emit ISO timestamps in `<pubDate>` anyway.
     */
    private fun parseDate(dateString: String): Date? {
        val trimmed = dateString.trim()
        if (trimmed.isBlank()) return null

        for (format in RFC_822_FORMATS) {
            try {
                return SimpleDateFormat(format, Locale.ENGLISH).parse(trimmed)
            } catch (e: Exception) {
                // Try the next format.
            }
        }

        return parseIso8601(trimmed)
    }

    private fun parseIso8601(value: String): Date? {
        for (formatter in ISO_8601_FORMATTERS) {
            try {
                return Date.from(OffsetDateTime.parse(value, formatter).toInstant())
            } catch (e: Exception) {
                // Try the next formatter.
            }
        }
        return try {
            // No offset at all (some feeds emit naive local timestamps) — assume
            // UTC rather than dropping the date entirely.
            Date.from(LocalDateTime.parse(value).toInstant(ZoneOffset.UTC))
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDuration(duration: String): Long? {
        return try {
            val parts = duration.split(":").map { it.trim() }
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val seconds = parts[2].toLong()
                    (hours * 3600 + minutes * 60 + seconds) * 1000
                }
                2 -> {
                    val minutes = parts[0].toLong()
                    val seconds = parts[1].toLong()
                    (minutes * 60 + seconds) * 1000
                }
                1 -> parts[0].toLong() * 1000
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        // RFC 822 (the RSS 2.0 spec's format) plus common non-compliant variants real
        // feeds emit — some omit the weekday, the seconds, or the timezone entirely.
        //
        // ORDER IS LOAD-BEARING: SimpleDateFormat.parse ignores trailing input, so a
        // less-specific pattern placed earlier would swallow a fuller timestamp and
        // silently drop the tail. e.g. the no-timezone "…HH:mm:ss" would "match"
        // "…08:00:00 +0000" and lose the offset. So: most specific (seconds + zone)
        // first; no-timezone forms (parsed as device-local) strictly last.
        private val RFC_822_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss z",
            // No seconds, but zoned.
            "EEE, dd MMM yyyy HH:mm Z",
            "EEE, dd MMM yyyy HH:mm z",
            // No timezone at all (naive local time). Must stay last.
            "EEE, dd MMM yyyy HH:mm:ss",
            "dd MMM yyyy HH:mm:ss",
        )

        // XXX accepts "Z" or "+HH:MM"; XX accepts "Z" or "+HHMM" (no colon).
        // Tried in sequence so either offset style resolves.
        private val ISO_8601_FORMATTERS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]XXX", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]XX", Locale.ENGLISH),
        )
    }
}

data class PodcastFeedMetadata(
    val title: String,
    val author: String,
    val artworkUrl: String?,
)
