package com.podcastplayer.app.data.local

import com.podcastplayer.app.domain.model.Podcast
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPodcastsMigrationTest {
    @Test
    fun removesLegacyYoutubeNamespacesAndSubscriptionFeedsOnly() {
        assertTrue(isLegacy(podcast("youtube:UC123", "https://example.com/rss")))
        assertTrue(isLegacy(podcast("channel", "https://www.youtube.com/feeds/videos.xml?channel_id=UC123")))
        assertTrue(isLegacy(podcast("playlist", "https://youtube.com/playlist?list=PL123")))
        assertTrue(isLegacy(podcast("handle", "https://youtube.com/@creator")))

        assertFalse(isLegacy(podcast("rss", "https://feeds.example.com/show.xml")))
        assertFalse(isLegacy(podcast("video", "https://youtube.com/watch?v=abc")))
        assertFalse(isLegacy(podcast("url:42", null)))
    }

    private fun isLegacy(podcast: Podcast) = SavedPodcastsStorage.isLegacyYouTubeSubscription(podcast)
    private fun podcast(id: String, feed: String?) = Podcast(id, "Title", "Artist", null, feed)
}
