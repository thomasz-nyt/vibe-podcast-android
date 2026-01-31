package com.podcastplayer.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE episodeId = :episodeId")
    suspend fun getByEpisodeId(episodeId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE podcastId = :podcastId")
    fun observeByPodcastId(podcastId: String): Flow<List<PlaybackProgressEntity>>

    @Query("SELECT * FROM playback_progress WHERE podcastId = :podcastId")
    suspend fun getByPodcastId(podcastId: String): List<PlaybackProgressEntity>

    @Query("DELETE FROM playback_progress WHERE episodeId = :episodeId")
    suspend fun deleteByEpisodeId(episodeId: String)
}
