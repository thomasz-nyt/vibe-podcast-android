package com.podcastplayer.app.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.ManualDownloadEntity
import com.podcastplayer.app.data.repository.DownloadManager
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 31)
@RunWith(AndroidJUnit4::class)
class DownloadForegroundInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database get() = DatabaseProvider.getDatabase(context)
    private val dao get() = database.manualDownloadDao()

    @Test fun deniedForegroundStartStillDownloadsAndDoesNotRepeatPromotion() = runBlocking {
        downloadWithDeniedPromotion(denyFromCall = 1)
    }

    @Test fun deniedProgressNotificationDoesNotDiscardDownload() = runBlocking {
        downloadWithDeniedPromotion(denyFromCall = 2)
    }

    @Test fun deniedManualStartKeepsManualOwnership() = runBlocking {
        downloadWithDeniedPromotion(denyFromCall = 1, origin = "MANUAL")
    }

    @Test fun missingForegroundPermissionRemainsAnActionableFailure() = runBlocking {
        val request = request("http://127.0.0.1:1/unavailable.mp3")
        dao.insert(request)
        try {
            val worker = TestListenableWorkerBuilder<ManualDownloadWorker>(context)
                .setInputData(workDataOf(ManualDownloadWorker.KEY_REQUEST_ID to request.requestId))
                .setForegroundUpdater { _, _, _ ->
                    Futures.immediateFailedFuture<Void>(SecurityException("Missing foreground service permission"))
                }
                .build()
            assertEquals(ListenableWorker.Result.failure(), worker.doWork())
            val retained = dao.getByRequestId(request.requestId)!!
            assertEquals("FAILED", retained.status)
            assertEquals("Missing foreground service permission", retained.errorMessage)
        } finally {
            dao.deleteByRequestId(request.requestId)
        }
    }

    private suspend fun downloadWithDeniedPromotion(denyFromCall: Int, origin: String = "AUTO") = coroutineScope {
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val payload = ByteArray(32_768) { (it % 127).toByte() }
            val request = request("http://127.0.0.1:${server.localPort}/episode.mp3").copy(origin = origin)
            val calls = AtomicInteger()
            dao.insert(request)
            val response = async(Dispatchers.IO) {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 200 OK\r\nContent-Length: ${payload.size}\r\n" +
                            "Content-Type: audio/mpeg\r\nConnection: close\r\n\r\n").toByteArray())
                        write(payload)
                        flush()
                    }
                }
            }
            try {
                val worker = TestListenableWorkerBuilder<ManualDownloadWorker>(context)
                    .setInputData(workDataOf(ManualDownloadWorker.KEY_REQUEST_ID to request.requestId))
                    // Platform denial must not become terminal even after the network retry limit.
                    .setRunAttemptCount(5)
                    .setForegroundUpdater { _, _, _ ->
                        if (calls.incrementAndGet() >= denyFromCall) {
                            Futures.immediateFailedFuture<Void>(
                                ForegroundServiceStartNotAllowedException(
                                    "startForegroundService() not allowed: service " +
                                        "com.podcastplayer.app.debug/" +
                                        "androidx.work.impl.foreground.SystemForegroundService",
                                ),
                            )
                        } else {
                            Futures.immediateVoidFuture()
                        }
                    }
                    .build()
                assertEquals(ListenableWorker.Result.success(), worker.doWork())
                response.await()
                assertNull(dao.getByRequestId(request.requestId))
                val downloaded = database.downloadedEpisodeDao().getEpisodeById(request.episodeId)
                assertNotNull(downloaded)
                assertEquals(payload.size.toLong(), downloaded!!.fileSize)
                assertEquals(origin, downloaded.origin)
                context.contentResolver.openInputStream(Uri.parse(downloaded.localPath))!!.use { input ->
                    assertArrayEquals(payload, input.readBytes())
                }
                assertEquals(denyFromCall, calls.get())
            } finally {
                server.close()
                response.cancel()
                dao.deleteByRequestId(request.requestId)
                DownloadManager(context).deleteEpisode(request.episodeId)
            }
        }
    }

    private fun request(url: String): ManualDownloadEntity {
        val id = UUID.randomUUID().toString()
        return ManualDownloadEntity(
            requestId = id,
            episodeId = id,
            podcastId = "foreground-download-test",
            podcastTitle = "Test",
            title = id,
            description = null,
            pubDate = null,
            audioUrl = url,
            duration = null,
            createdAtMs = 1,
            origin = "AUTO",
        )
    }
}
