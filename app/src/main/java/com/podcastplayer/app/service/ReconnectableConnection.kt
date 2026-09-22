package com.podcastplayer.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Serializes connection attempts; a failed or disconnected connection is never reused. */
internal class ReconnectableConnection<T : Any>(
    private val connect: suspend () -> T,
    private val isConnected: (T) -> Boolean,
    private val release: (T) -> Unit,
    private val timeoutMs: Long = 10_000,
) {
    private val mutex = Mutex()
    private val current = MutableStateFlow<T?>(null)
    val state = current.asStateFlow()

    suspend fun await(): T = withTimeout(timeoutMs) {
        mutex.withLock {
            current.value?.takeIf(isConnected)?.let { return@withLock it }
            current.value?.let(release)
            current.value = null
            connect().also {
                if (!isConnected(it)) {
                    release(it)
                    error("Player disconnected during connection")
                }
                current.value = it
            }
        }
    }

    fun invalidate(connection: T? = current.value) {
        if (current.value !== connection) return
        current.value = null
        connection?.let(release)
    }
}
