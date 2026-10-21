package com.podcastplayer.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PodcastDatabaseMigrationTest {
    private val databaseName = "migration-test"

    @Test
    fun migration5To7PreservesRequestsDownloadsAndProgress() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-five-seven"
        context.deleteDatabase(name)
        createDatabase(context, name, 5).use { db ->
            db.execSQL("INSERT INTO manual_downloads VALUES " +
                "('stable-worker-request','pending','show','Show','Pending',NULL,100,'https://audio',200," +
                "'RUNNING',42,NULL,123)")
            db.execSQL("INSERT INTO downloaded_episodes VALUES " +
                "('done','show','Done',NULL,100,'https://done',200,'/saved',10,20,'MANUAL')")
            db.execSQL("INSERT INTO playback_progress VALUES ('pending','show',1234,5000,0,100,100)")
        }
        val db = Room.databaseBuilder(context, PodcastDatabase::class.java, name)
            .addMigrations(PodcastDatabase.MIGRATION_5_6, PodcastDatabase.MIGRATION_6_7).build()
        try {
            val request = db.manualDownloadDao().getByEpisodeId("pending")!!
            assertEquals("stable-worker-request", request.requestId)
            assertEquals("RUNNING", request.status)
            assertEquals(42f, request.progressPercent)
            assertEquals("MANUAL", request.origin)
            assertEquals("/saved", db.downloadedEpisodeDao().getEpisodeById("done")!!.localPath)
            assertEquals(1234L, db.playbackProgressDao().getByEpisodeId("pending")!!.positionMs)
            assertNull(db.feedSnapshotDao().get("show", "https://feed"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migration3To7PinsExistingRowsAndCreatesManualDownloadQueue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).apply {
            execSQL(
                """
                CREATE TABLE downloaded_episodes (
                    id TEXT NOT NULL PRIMARY KEY,
                    podcastId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    pubDate INTEGER,
                    audioUrl TEXT NOT NULL,
                    duration INTEGER,
                    localPath TEXT NOT NULL,
                    fileSize INTEGER NOT NULL,
                    downloadDate INTEGER NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE playback_progress (
                    episodeId TEXT NOT NULL PRIMARY KEY,
                    podcastId TEXT NOT NULL,
                    positionMs INTEGER NOT NULL,
                    durationMs INTEGER NOT NULL,
                    completed INTEGER NOT NULL,
                    lastPlayedAtMs INTEGER NOT NULL,
                    updatedAtMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE url_downloads (
                    id TEXT NOT NULL PRIMARY KEY,
                    sourceUrl TEXT NOT NULL,
                    source TEXT NOT NULL,
                    title TEXT NOT NULL,
                    uploader TEXT,
                    thumbnailUrl TEXT,
                    mediaType TEXT NOT NULL,
                    localPath TEXT,
                    durationMs INTEGER,
                    fileSize INTEGER,
                    status TEXT NOT NULL,
                    progressPercent REAL NOT NULL,
                    errorMessage TEXT,
                    createdAtMs INTEGER NOT NULL,
                    completedAtMs INTEGER
                )
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO downloaded_episodes VALUES " +
                    "('episode','podcast','Title',NULL,NULL,'https://audio',NULL,'path',10,20)"
            )
            execSQL(
                "INSERT INTO url_downloads VALUES " +
                    "('url','https://video','youtube','Title',NULL,NULL,'audio','path',NULL,10," +
                    "'COMPLETED',100,NULL,20,30)"
            )
            version = 3
            close()
        }

        val database = Room.databaseBuilder(context, PodcastDatabase::class.java, databaseName)
            .addMigrations(
                PodcastDatabase.MIGRATION_3_4,
                PodcastDatabase.MIGRATION_4_5,
                PodcastDatabase.MIGRATION_5_6,
                PodcastDatabase.MIGRATION_6_7,
            )
            .build()

        val rss = database.downloadedEpisodeDao().getEpisodeById("episode")!!
        val url = database.urlDownloadDao().getById("url")!!
        assertEquals(DownloadOrigin.MANUAL.name, rss.origin)
        assertEquals(DownloadOrigin.MANUAL.name, url.origin)
        assertNull(url.podcastId)
        assertNull(url.episodePubDateMs)
        assertEquals(emptyList<ManualDownloadEntity>(), database.manualDownloadDao().getActive())
        database.close()
        Unit
    }

    @Test
    fun migration6To7RecoversOnlySavedForegroundDenialsOnce() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-six-seven"
        context.deleteDatabase(name)
        val denial = "startForegroundService() not allowed due to mAllowStartForeground false: service " +
            "com.podcastplayer.app.debug/androidx.work.impl.foreground.SystemForegroundService"
        fun request(id: String, error: String?, status: String = "FAILED", origin: String = "AUTO") =
            ManualDownloadEntity(
                requestId = "request-$id", episodeId = id, podcastId = "show", podcastTitle = "Show",
                title = "Episode $id", description = "Saved description", pubDate = 100,
                audioUrl = "https://example.com/$id.mp3", duration = 200, status = status,
                progressPercent = 42f, errorMessage = error, createdAtMs = 123, origin = origin,
            )
        val matching = listOf(
            request("one", denial),
            request("two", "ForegroundServiceStartNotAllowedException: $denial", origin = "MANUAL"),
            request("three", "Worker failed: $denial"),
        )
        val untouched = listOf(
            request("permission", "Missing foreground service permission"),
            request("http", "HTTP 403 Forbidden"),
            request("storage", "No space left on device"),
            request("other-service", "startForegroundService() not allowed: another.Service"),
            request("other-error", "Cannot start androidx.work.impl.foreground.SystemForegroundService"),
            request("no-error", null),
            request("queued", denial, status = "QUEUED"),
            request("running", denial, status = "RUNNING"),
        )
        createDatabase(context, name, 6).use { db ->
            (matching + untouched).forEach { row ->
                db.execSQL(
                    "INSERT INTO manual_downloads VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        row.requestId, row.episodeId, row.podcastId, row.podcastTitle, row.title,
                        row.description, row.pubDate, row.audioUrl, row.duration, row.status,
                        row.progressPercent, row.errorMessage, row.createdAtMs, row.origin,
                    ),
                )
            }
        }
        fun open() = Room.databaseBuilder(context, PodcastDatabase::class.java, name)
            .addMigrations(PodcastDatabase.MIGRATION_6_7).build()
        try {
            open().useDatabase { db ->
                val dao = db.manualDownloadDao()
                matching.forEach { row ->
                    assertEquals(
                        row.copy(status = "QUEUED", progressPercent = 0f, errorMessage = null),
                        dao.getByRequestId(row.requestId),
                    )
                }
                untouched.forEach { assertEquals(it, dao.getByRequestId(it.requestId)) }
                assertEquals(7, db.openHelper.readableDatabase.version)
            }
            // Simulate an interrupted startup before reconciliation enqueues the recovered rows.
            open().useDatabase { db ->
                matching.forEach { row ->
                    assertEquals("QUEUED", db.manualDownloadDao().getByRequestId(row.requestId)!!.status)
                }
                db.manualDownloadDao().updateState(matching.first().requestId, "FAILED", 17f, denial)
            }
            // Even a new failure with the same message must not trigger another automatic retry.
            open().useDatabase { db ->
                assertEquals(
                    matching.first().copy(progressPercent = 17f),
                    db.manualDownloadDao().getByRequestId(matching.first().requestId),
                )
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private inline fun <T> PodcastDatabase.useDatabase(block: (PodcastDatabase) -> T): T {
        return try {
            block(this)
        } finally {
            close()
        }
    }

    private fun createDatabase(context: Context, name: String, version: Int): android.database.sqlite.SQLiteDatabase {
        val schemaText = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.podcastplayer.app.data.local.PodcastDatabase/$version.json")
            .bufferedReader().use { it.readText() }
        val schema = org.json.JSONObject(schemaText).getJSONObject("database").getJSONArray("entities")
        return context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).apply {
            for (i in 0 until schema.length()) {
                val entity = schema.getJSONObject(i)
                val table = entity.getString("tableName")
                execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            this.version = version
        }
    }
}
