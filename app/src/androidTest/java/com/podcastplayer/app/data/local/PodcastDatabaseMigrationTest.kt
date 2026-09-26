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
    fun migration5To6PreservesRequestsDownloadsAndProgress() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-five-six"
        context.deleteDatabase(name)
        val schemaText = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.podcastplayer.app.data.local.PodcastDatabase/5.json").bufferedReader().use { it.readText() }
        val schema = org.json.JSONObject(schemaText).getJSONObject("database").getJSONArray("entities")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            for (i in 0 until schema.length()) {
                val entity = schema.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            db.execSQL("INSERT INTO manual_downloads VALUES " +
                "('stable-worker-request','pending','show','Show','Pending',NULL,100,'https://audio',200," +
                "'RUNNING',42,NULL,123)")
            db.execSQL("INSERT INTO downloaded_episodes VALUES " +
                "('done','show','Done',NULL,100,'https://done',200,'/saved',10,20,'MANUAL')")
            db.execSQL("INSERT INTO playback_progress VALUES ('pending','show',1234,5000,0,100,100)")
            db.version = 5
        }
        val db = Room.databaseBuilder(context, PodcastDatabase::class.java, name)
            .addMigrations(PodcastDatabase.MIGRATION_5_6).build()
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
    fun migration3To5PinsExistingRowsAndCreatesManualDownloadQueue() = runBlocking {
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
}
