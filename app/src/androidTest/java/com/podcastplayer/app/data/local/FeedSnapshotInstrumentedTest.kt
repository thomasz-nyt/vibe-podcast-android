package com.podcastplayer.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.podcastplayer.app.data.remote.RssParser
import com.podcastplayer.app.data.remote.iTunesApi
import com.podcastplayer.app.data.repository.PodcastRepository
import com.podcastplayer.app.domain.model.Episode
import java.net.ServerSocket
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedSnapshotInstrumentedTest {
    @Test fun snapshotSurvivesReopenAndFailedRefreshRetainsLastSuccessfulParse() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "feed-snapshot-test"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, PodcastDatabase::class.java, name).build()
        try {
            val storage = FeedSnapshotStorage(db.feedSnapshotDao())
            val episode = Episode("one", "show", "Title", null, Date(100), "https://audio", null)
            val server = ServerSocket(0)
            val feed = "http://127.0.0.1:${server.localPort}/feed"
            storage.save("show", feed, listOf(episode), 123)
            db.close()
            db = Room.databaseBuilder(context, PodcastDatabase::class.java, name).build()
            val reopened = FeedSnapshotStorage(db.feedSnapshotDao())
            assertEquals(FeedSnapshot(listOf(episode), 123), reopened.load("show", feed))
            assertNull(reopened.load("show", "https://different-feed"))
            val response = async(Dispatchers.IO) {
                server.use {
                    it.accept().use { socket ->
                        socket.getOutputStream().write(
                            "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                .toByteArray(),
                        )
                    }
                }
            }
            val repository = PodcastRepository(iTunesApi.create(), RssParser(), reopened)
            assertTrue(repository.getEpisodes(feed, "show", forceRefresh = true).isFailure)
            response.await()
            assertEquals(FeedSnapshot(listOf(episode), 123), reopened.load("show", feed))
            reopened.save("show", feed, emptyList(), 456)
            assertEquals(FeedSnapshot(emptyList(), 456), reopened.load("show", feed))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
