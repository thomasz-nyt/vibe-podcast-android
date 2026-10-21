package com.podcastplayer.app.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select

/** Shares foreground promotion state between download startup and progress updates. */
internal class DownloadForegroundSession(
    private val promote: suspend (Int) -> Unit,
    private val isStartDenied: (Throwable) -> Boolean,
    private val onStartDenied: (Throwable) -> Unit,
    private val backgroundLimitMs: Long = 9 * 60 * 1_000L,
) {
    private val startDenied = CompletableDeferred<Unit>()

    suspend fun update(percent: Int) {
        if (startDenied.isCompleted) return
        try {
            promote(percent)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!isStartDenied(error)) throw error
            startDenied.complete(Unit)
            onStartDenied(error)
        }
    }

    suspend fun <T> run(onBackgroundTimeout: suspend () -> T, download: suspend () -> T): T = coroutineScope {
        val transfer = async {
            // Try promotion before waiting for a slot to allow long foreground transfers.
            update(0)
            download()
        }
        val backgroundDeadline = async {
            // Count from worker startup, including time waiting for a download slot. A denied
            // progress update must not reset the normal background execution window.
            delay(backgroundLimitMs)
            startDenied.await()
        }
        try {
            select {
                transfer.onAwait { it }
                backgroundDeadline.onAwait {
                    // WorkManager may move a worker to its foreground bookkeeping before the
                    // platform rejects the service start. Enforce our own background limit.
                    transfer.cancelAndJoin()
                    onBackgroundTimeout()
                }
            }
        } finally {
            backgroundDeadline.cancel()
        }
    }
}
