package com.podcastplayer.app.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    private const val DATABASE_NAME = "podcast_database"

    @Volatile
    private var instance: PodcastDatabase? = null

    fun getDatabase(context: Context): PodcastDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PodcastDatabase::class.java,
                DATABASE_NAME
            ).build().also { instance = it }
        }
    }
}
