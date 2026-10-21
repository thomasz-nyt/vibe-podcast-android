package com.podcastplayer.app.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadForegroundSessionTest {
    @Test fun deniedStartStillTransfersAndDoesNotRepeatPromotion() = runTest {
        var promotions = 0
        var denials = 0
        val session = DownloadForegroundSession(
            promote = { promotions++; throw StartDenied() },
            isStartDenied = { it is StartDenied },
            onStartDenied = { denials++ },
        )
        val result = session.run(onBackgroundTimeout = { "retry" }) {
            session.update(50)
            session.update(100)
            "saved"
        }
        assertEquals("saved", result)
        assertEquals(1, promotions)
        assertEquals(1, denials)
    }

    @Test fun denialDuringProgressStillFinishesTransfer() = runTest {
        var promotions = 0
        val session = DownloadForegroundSession(
            promote = { if (++promotions == 2) throw StartDenied() },
            isStartDenied = { it is StartDenied },
            onStartDenied = {},
        )
        assertEquals("saved", session.run(onBackgroundTimeout = { "retry" }) {
            session.update(50)
            session.update(100)
            "saved"
        })
        assertEquals(2, promotions)
    }

    @Test fun backgroundDeadlineCancelsTransferBeforeRequestingRetry() = runTest {
        var transferStopped = false
        val session = DownloadForegroundSession(
            promote = { throw StartDenied() },
            isStartDenied = { it is StartDenied },
            onStartDenied = {},
            backgroundLimitMs = 100,
        )
        assertEquals("retry", session.run(onBackgroundTimeout = {
            assertTrue(transferStopped)
            "retry"
        }) {
            try {
                delay(200)
                "saved"
            } finally {
                transferStopped = true
            }
        })
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test fun foregroundTransferCanExceedBackgroundDeadline() = runTest {
        val session = DownloadForegroundSession(
            promote = {},
            isStartDenied = { it is StartDenied },
            onStartDenied = {},
            backgroundLimitMs = 100,
        )
        assertEquals("saved", session.run(onBackgroundTimeout = { "retry" }) {
            delay(200)
            "saved"
        })
    }

    @Test fun lateProgressDenialDoesNotResetBackgroundDeadline() = runTest {
        var promotions = 0
        val session = DownloadForegroundSession(
            promote = { if (++promotions == 2) throw StartDenied() },
            isStartDenied = { it is StartDenied },
            onStartDenied = {},
            backgroundLimitMs = 100,
        )
        assertEquals("retry", session.run(onBackgroundTimeout = { "retry" }) {
            delay(80)
            session.update(50)
            delay(80)
            "saved"
        })
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test(expected = SecurityException::class)
    fun permissionErrorsArePropagated() = runTest {
        val session = DownloadForegroundSession(
            promote = { throw SecurityException("Missing permission") },
            isStartDenied = { it is StartDenied },
            onStartDenied = {},
        )
        session.run<Unit>(onBackgroundTimeout = {}) {}
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsPropagated() = runTest {
        val session = DownloadForegroundSession(
            promote = { throw CancellationException("Work stopped") },
            isStartDenied = { true },
            onStartDenied = {},
        )
        session.run<Unit>(onBackgroundTimeout = {}) {}
    }

    private class StartDenied : IllegalStateException(
        "startForegroundService() not allowed: service " +
            "com.podcastplayer.app.debug/androidx.work.impl.foreground.SystemForegroundService",
    )
}
