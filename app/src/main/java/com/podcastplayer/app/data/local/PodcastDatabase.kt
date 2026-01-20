package com.podcastplayer.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadedEpisodeEntity::class],
    version = 1
)
abstract class PodcastDatabase : RoomDatabase() {
    abstract fun downloadedEpisodeDao(): DownloadedEpisodeDao
}
