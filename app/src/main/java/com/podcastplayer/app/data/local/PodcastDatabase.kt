package com.podcastplayer.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadedEpisodeEntity::class, PlaybackProgressEntity::class],
    version = 2
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun downloadedEpisodeDao(): DownloadedEpisodeDao
    abstract fun playbackProgressDao(): PlaybackProgressDao

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
    }
}
