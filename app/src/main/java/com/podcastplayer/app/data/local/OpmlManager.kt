package com.podcastplayer.app.data.local

import android.util.Xml
import com.podcastplayer.app.domain.model.Podcast
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream

data class OpmlExportSummary(val podcastCount: Int, val queueCount: Int)
data class OpmlImportData(val podcasts: List<Podcast>, val queues: List<QueueStorage.QueuePayload>)

object OpmlManager {

    private const val NS_PODCASTPLAYER = "https://podcastplayer.com/opml"

    fun writeOpml(
        podcasts: List<Podcast>,
        queues: List<QueueStorage.QueuePayload>,
        outputStream: OutputStream,
    ): Result<OpmlExportSummary> {
        return try {
            val exportable = podcasts.filter { it.feedUrl != null }
            val exportableQueues = queues.filter { it.podcastIds.isNotEmpty() }

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

            if (exportableQueues.isNotEmpty()) {
                serializer.startTag(NS_PODCASTPLAYER, "queues")
                for (queue in exportableQueues) {
                    serializer.startTag(NS_PODCASTPLAYER, "queue")
                    serializer.attribute(null, "id", queue.id)
                    serializer.attribute(null, "name", queue.name)
                    serializer.attribute(null, "createdAt", queue.createdAt.toString())
                    for (podcastId in queue.podcastIds) {
                        serializer.startTag(NS_PODCASTPLAYER, "item")
                        serializer.attribute(null, "id", podcastId)
                        serializer.endTag(NS_PODCASTPLAYER, "item")
                    }
                    serializer.endTag(NS_PODCASTPLAYER, "queue")
                }
                serializer.endTag(NS_PODCASTPLAYER, "queues")
            }

            serializer.endTag(null, "body")
            serializer.endTag(null, "opml")
            serializer.endDocument()
            serializer.flush()

            Result.success(OpmlExportSummary(exportable.size, exportableQueues.size))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun readOpml(inputStream: InputStream): Result<OpmlImportData> {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)

            val podcasts = mutableListOf<Podcast>()
            val queues = mutableListOf<QueueStorage.QueuePayload>()

            var currentQueueId: String? = null
            var currentQueueName: String? = null
            var currentQueueCreatedAt: Long? = null
            val currentQueueItems = mutableListOf<String>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val ns = parser.namespace
                val tag = parser.name

                if (eventType == XmlPullParser.START_TAG) {
                    when {
                        tag == "outline" && ns.isNullOrEmpty() -> {
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
                        tag == "queue" && ns == NS_PODCASTPLAYER -> {
                            currentQueueId = parser.getAttributeValue(null, "id")
                            currentQueueName = parser.getAttributeValue(null, "name")
                            currentQueueCreatedAt = parser.getAttributeValue(null, "createdAt")?.toLongOrNull()
                            currentQueueItems.clear()
                        }
                        tag == "item" && ns == NS_PODCASTPLAYER -> {
                            val podcastId = parser.getAttributeValue(null, "id")
                            if (!podcastId.isNullOrBlank()) currentQueueItems.add(podcastId)
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (tag == "queue" && ns == NS_PODCASTPLAYER) {
                        val id = currentQueueId
                        val name = currentQueueName
                        val createdAt = currentQueueCreatedAt
                        if (id != null && name != null && createdAt != null) {
                            queues.add(
                                QueueStorage.QueuePayload(
                                    id = id,
                                    name = name,
                                    createdAt = createdAt,
                                    podcastIds = currentQueueItems.toList(),
                                )
                            )
                        }
                        currentQueueId = null
                        currentQueueName = null
                        currentQueueCreatedAt = null
                        currentQueueItems.clear()
                    }
                }

                eventType = parser.next()
            }

            Result.success(OpmlImportData(podcasts, queues))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
