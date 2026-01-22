package com.podcastplayer.app.data.remote

import com.podcastplayer.app.domain.model.Episode
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RssParser {

    fun parseEpisodes(inputStream: InputStream, podcastId: String): List<Episode> {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
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

    private fun parseDate(dateString: String): Date? {
        return try {
            val formats = listOf(
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss z"
            )
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.ENGLISH)
                    return sdf.parse(dateString)
                } catch (e: Exception) {
                }
            }
            null
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
}
