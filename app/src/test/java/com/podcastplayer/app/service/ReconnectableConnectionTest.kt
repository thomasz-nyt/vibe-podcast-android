package com.podcastplayer.app.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectableConnectionTest {
    private data class Session(var connected: Boolean = true)

    @Test fun disconnectedControllerIsReopenedAndPublished() = runTest {
        var count = 0
        val connection = ReconnectableConnection(
            connect = { count++; Session() }, isConnected = { it.connected }, release = { it.connected = false },
        )
        val first = connection.await()
        first.connected = false
        val second = connection.await()
        assertTrue(first !== second)
        assertSame(second, connection.state.value)
        connection.invalidate(first) // A late old disconnect must not release the new controller.
        assertSame(second, connection.await())
        assertEquals(2, count)
    }

    @Test fun simultaneousWaitersShareOneConnection() = runTest {
        val ready = CompletableDeferred<Session>()
        var count = 0
        val connection = ReconnectableConnection(
            connect = { count++; ready.await() }, isConnected = { it.connected }, release = {},
        )
        val one = async { connection.await() }
        val two = async { connection.await() }
        runCurrent()
        ready.complete(Session())
        assertSame(one.await(), two.await())
        assertEquals(1, count)
    }

    @Test fun connectionTimesOutAtTenSecondsAndNextAttemptCanSucceed() = runTest {
        var hang = true
        val connection = ReconnectableConnection(
            connect = { if (hang) CompletableDeferred<Session>().await() else Session() },
            isConnected = { it.connected }, release = {},
        )
        val result = async { runCatching { connection.await() } }
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(result.await().isFailure)
        hang = false
        assertTrue(connection.await().connected)
    }
}
