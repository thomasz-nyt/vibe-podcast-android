package com.podcastplayer.app.data.local

import android.util.Xml
import com.podcastplayer.app.domain.model.Podcast
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream

object OpmlManager {

    private const val NS_PODCASTPLAYER = "https://podcastplayer.com/opml"

    fun writeOpml(podcasts: List<Podcast>, outputStream: OutputStream): Result<Int> {
        return try {
            val exportable = podcasts.filter { it.feedUrl != null }
            val serializer = Xml.newSerializer()
            serializer.setOutput(outputStream, "UTF-8")
            serializer.startDocument("UTF-8", true)

            serializer.startTag(null, "opml")
            serializer.attribute(null, "version", "2.0")
            serializer.attribute("xmlns", "podcastplayer", NS_PODCASTPLAYER)

            serializer.startTag(null, "head")
            serializer.startTag(null, "title")
            serializer.text("Vibe Podcast Subscriptions")
            serializer.endTag(null, "title")
            serializer.endTag(null, "head")

            serializer.startTag(null, "body")
            for (podcast in exportable) {
                serializer.startTag(null, "outline")
                serializer.attribute(null, "type", "rss")
                serializer.attribute(null, "text", podcast.title)
                serializer.attribute(null, "xmlUrl", podcast.feedUrl!!)
                serializer.attribute(NS_PODCASTPLAYER, "id", podcast.id)
                serializer.attribute(NS_PODCASTPLAYER, "artist", podcast.artist)
                podcast.artworkUrl?.let {
                    serializer.attribute(NS_PODCASTPLAYER, "artworkUrl", it)
                }
                serializer.endTag(null, "outline")
            }
            serializer.endTag(null, "body")

            serializer.endTag(null, "opml")
            serializer.endDocument()
            serializer.flush()

            Result.success(exportable.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun readOpml(inputStream: InputStream): Result<List<Podcast>> {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            val podcasts = mutableListOf<Podcast>()
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "outline") {
                    val type = parser.getAttributeValue(null, "type")
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")

                    if (type?.equals("rss", ignoreCase = true) == true && !xmlUrl.isNullOrBlank()) {
                        val customId = parser.getAttributeValue(NS_PODCASTPLAYER, "id")
                        val customArtist = parser.getAttributeValue(NS_PODCASTPLAYER, "artist")
                        val customArtwork = parser.getAttributeValue(NS_PODCASTPLAYER, "artworkUrl")

                        val text = parser.getAttributeValue(null, "text")
                            ?: parser.getAttributeValue(null, "title")
                            ?: ""

                        podcasts.add(
                            Podcast(
                                id = customId ?: xmlUrl,
                                title = text,
                                artist = customArtist ?: "",
                                artworkUrl = customArtwork,
                                feedUrl = xmlUrl,
                            )
                        )
                    }
                }
                eventType = parser.next()
            }

            Result.success(podcasts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
