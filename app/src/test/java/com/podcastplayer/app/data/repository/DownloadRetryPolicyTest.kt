package com.podcastplayer.app.data.repository

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRetryPolicyTest {
    @Test fun transientFailuresHaveTwoRetriesThenStop() {
        for (error in listOf(IOException(), SocketTimeoutException(), HttpStatusException(503, "feed"),
            HttpStatusException(429, "feed"))) {
            assertTrue(DownloadRetryPolicy.shouldRetry(error, 0))
            assertTrue(DownloadRetryPolicy.shouldRetry(error, 1))
            assertFalse(DownloadRetryPolicy.shouldRetry(error, 2))
        }
    }

    @Test fun permissionAndPermanentHttpFailuresDoNotRetry() {
        for (error in listOf(SecurityException(), HttpStatusException(404, "feed"),
            HttpStatusException(401, "feed"), IllegalArgumentException())) {
            assertFalse(DownloadRetryPolicy.shouldRetry(error, 0))
        }
    }
}
