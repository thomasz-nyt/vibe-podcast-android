package com.podcastplayer.app.service

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.podcastplayer.app.data.local.AppSettings
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.DownloadOrigin
import com.podcastplayer.app.data.local.ManualDownloadEntity
import com.podcastplayer.app.data.local.MediaIdentity
import com.podcastplayer.app.data.local.toEpisode
import com.podcastplayer.app.data.repository.ManualDownloadRepository
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("RestrictedApi")
@RunWith(AndroidJUnit4::class)
class AutomaticDownloadsInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dao get() = DatabaseProvider.getDatabase(context).manualDownloadDao()

    @Before fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    @Test fun immediateAndSixHourSchedulesHonorSavedNetworkPreference() {
        for (cellular in listOf(false, true)) {
            val network = if (cellular) NetworkType.CONNECTED else NetworkType.UNMETERED
            val immediate = AutoDownloadWorker.immediateRequest(cellular).workSpec
            val periodic = AutoDownloadWorker.periodicRequest(cellular).workSpec
            assertEquals(network, immediate.constraints.requiredNetworkType)
            assertEquals(network, periodic.constraints.requiredNetworkType)
            assertEquals(0L, immediate.initialDelay)
            assertEquals(TimeUnit.HOURS.toMillis(6), periodic.intervalDuration)
            assertEquals(BackoffPolicy.EXPONENTIAL, immediate.backoffPolicy)
            assertEquals(30_000L, immediate.backoffDelayDuration)
        }
        val settings = AppSettings.getInstance(context)
        val original = settings.autoDownloadOnCellular.value
        try {
            settings.setAutoDownloadOnCellular(false)
            val repository = ManualDownloadRepository(context)
            assertEquals(NetworkType.UNMETERED,
                repository.workRequest(request("AUTO")).workSpec.constraints.requiredNetworkType)
            assertEquals(NetworkType.CONNECTED,
                repository.workRequest(request("MANUAL")).workSpec.constraints.requiredNetworkType)
        } finally { settings.setAutoDownloadOnCellular(original) }
    }

    @Test fun overlappingManualAndAutomaticRequestsKeepIdentityAndManualOwnership() = runBlocking {
        val repository = ManualDownloadRepository(context)
        val request = request("AUTO")
        try {
            repository.enqueue(request.toEpisode(), "Show", DownloadOrigin.AUTO)
            val original = dao.getByEpisodeId(request.episodeId)!!
            repository.enqueue(request.toEpisode(), "Show", DownloadOrigin.AUTO)
            assertEquals(original.requestId, dao.getByEpisodeId(request.episodeId)!!.requestId)
            repository.enqueue(request.toEpisode(), "Show", DownloadOrigin.MANUAL)
            repository.enqueue(request.toEpisode(), "Show", DownloadOrigin.AUTO)
            val promoted = dao.getByEpisodeId(request.episodeId)!!
            assertEquals(original.requestId, promoted.requestId)
            assertEquals("MANUAL", promoted.origin)
        } finally {
            dao.getByEpisodeId(request.episodeId)?.let { repository.remove(it.requestId) }
        }
    }

    @Test fun missingManualPayloadIsNeverReenqueuedAsAutomatic() = runBlocking {
        val request = request("AUTO")
        val downloads = DatabaseProvider.getDatabase(context).downloadedEpisodeDao()
        downloads.insertEpisode(com.podcastplayer.app.data.local.DownloadedEpisodeEntity(
            id = request.episodeId, podcastId = request.podcastId, title = request.title,
            description = null, pubDate = null, audioUrl = request.audioUrl, duration = null,
            localPath = "/missing-${request.episodeId}", fileSize = 1, downloadDate = 1,
            origin = DownloadOrigin.MANUAL.name,
        ))
        try {
            ManualDownloadRepository(context).enqueue(request.toEpisode(), "Show", DownloadOrigin.AUTO)
            assertNull(dao.getByEpisodeId(request.episodeId))
            assertEquals(DownloadOrigin.MANUAL.name,
                downloads.getEpisodeById(request.episodeId)!!.origin)
        } finally {
            downloads.deleteEpisodeById(request.episodeId)
        }
    }

    @Test fun pendingInsertionWithoutWorkIsReconciledAndFailureRemainsUntilExplicitRetryOrDismiss() = runBlocking {
        val repository = ManualDownloadRepository(context)
        val request = request("AUTO")
        dao.insert(request)
        try {
            repository.resumePending()
            val workName = "vibe.manual-download.${MediaIdentity.rss(request.episodeId).sha256}"
            assertTrue(WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).await().isNotEmpty())
            dao.updateState(request.requestId, "FAILED", 0f, "Network failed")
            repository.enqueue(request.toEpisode(), "Show", DownloadOrigin.AUTO)
            assertEquals("FAILED", dao.getByRequestId(request.requestId)!!.status)
            repository.retry(request.requestId)
            assertEquals("QUEUED", dao.getByRequestId(request.requestId)!!.status)
            repository.remove(request.requestId)
            assertNull(dao.getByRequestId(request.requestId))
        } finally { dao.deleteByRequestId(request.requestId) }
    }

    @Test fun exhaustedTransferRetainsActionableFailure() = runBlocking {
        val request = request("AUTO")
        dao.insert(request)
        try {
            val worker = TestListenableWorkerBuilder<ManualDownloadWorker>(context)
                .setInputData(workDataOf(ManualDownloadWorker.KEY_REQUEST_ID to request.requestId))
                .setRunAttemptCount(2)
                .build()
            assertEquals(ListenableWorker.Result.failure(), worker.doWork())
            val retained = dao.getByRequestId(request.requestId)!!
            assertEquals("FAILED", retained.status)
            assertNotNull(retained.errorMessage)
            assertEquals("AUTO", retained.origin)
        } finally { dao.deleteByRequestId(request.requestId) }
    }

    @Test fun retentionProtectsManualAndUpcomingDownloads() = runBlocking {
        val database = DatabaseProvider.getDatabase(context)
        val dao = database.downloadedEpisodeDao()
        val show = UUID.randomUUID().toString()
        val ids = listOf("$show-manual", "$show-upcoming", "$show-old", "$show-new")
        try {
            ids.forEachIndexed { index, id ->
                dao.insertEpisode(com.podcastplayer.app.data.local.DownloadedEpisodeEntity(
                    id = id, podcastId = show, title = id, description = null, pubDate = index.toLong(),
                    audioUrl = "https://audio", duration = null, localPath = "/missing-$id", fileSize = 1,
                    downloadDate = 1, origin = if (index == 0) "MANUAL" else "AUTO",
                ))
            }
            PlaybackDownloadProtection.episodeIds = setOf(ids[1])
            AutoDownloadRetentionManager(context).trimPodcast(show, 1)
            assertNotNull(dao.getEpisodeById(ids[0]))
            assertNotNull(dao.getEpisodeById(ids[1]))
            assertNull(dao.getEpisodeById(ids[2]))
            assertNotNull(dao.getEpisodeById(ids[3]))
        } finally {
            PlaybackDownloadProtection.episodeIds = emptySet()
            ids.forEach { dao.deleteEpisodeById(it) }
        }
    }

    private fun request(origin: String): ManualDownloadEntity {
        val id = UUID.randomUUID().toString()
        return ManualDownloadEntity(
            requestId = id, episodeId = id, podcastId = "download-test", podcastTitle = "Test",
            title = id, description = null, pubDate = null, audioUrl = "http://127.0.0.1:1/unavailable.mp3",
            duration = null, createdAtMs = 1, origin = origin,
        )
    }
}
