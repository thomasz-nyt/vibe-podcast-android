package com.podcastplayer.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DownloadedEpisodeEntity::class,
        PlaybackProgressEntity::class,
        UrlDownloadEntity::class,
        ManualDownloadEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun downloadedEpisodeDao(): DownloadedEpisodeDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun urlDownloadDao(): UrlDownloadDao
    abstract fun manualDownloadDao(): ManualDownloadDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS playback_progress (
                        episodeId TEXT NOT NULL,
                        podcastId TEXT NOT NULL,
                        positionMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        completed INTEGER NOT NULL,
                        lastPlayedAtMs INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL,
                        PRIMARY KEY(episodeId)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Adds the [url_downloads] table (issue #33) for media items downloaded
         * from pasted/shared URLs (YouTube, X, etc.).
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS url_downloads (
                        id TEXT NOT NULL,
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
                        completedAtMs INTEGER,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        /** Existing downloads are deliberately grandfathered as pinned/manual. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE downloaded_episodes ADD COLUMN origin TEXT NOT NULL DEFAULT 'MANUAL'"
                )
                db.execSQL(
                    "ALTER TABLE url_downloads ADD COLUMN origin TEXT NOT NULL DEFAULT 'MANUAL'"
                )
                db.execSQL("ALTER TABLE url_downloads ADD COLUMN podcastId TEXT")
                db.execSQL("ALTER TABLE url_downloads ADD COLUMN episodePubDateMs INTEGER")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS manual_downloads (
                        requestId TEXT NOT NULL,
                        episodeId TEXT NOT NULL,
                        podcastId TEXT NOT NULL,
                        podcastTitle TEXT,
                        title TEXT NOT NULL,
                        description TEXT,
                        pubDate INTEGER,
                        audioUrl TEXT NOT NULL,
                        duration INTEGER,
                        status TEXT NOT NULL,
                        progressPercent REAL NOT NULL,
                        errorMessage TEXT,
                        createdAtMs INTEGER NOT NULL,
                        PRIMARY KEY(requestId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_manual_downloads_episodeId " +
                        "ON manual_downloads (episodeId)"
                )
            }
        }
    }
}
