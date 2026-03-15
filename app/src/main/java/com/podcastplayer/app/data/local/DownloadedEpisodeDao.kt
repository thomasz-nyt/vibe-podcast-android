package com.podcastplayer.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedEpisodeDao {

    @Query("SELECT * FROM downloaded_episodes WHERE podcastId = :podcastId")
    fun getEpisodesByPodcast(podcastId: String): Flow<List<DownloadedEpisodeEntity>>

    @Query("SELECT * FROM downloaded_episodes ORDER BY downloadDate DESC")
    fun getAllEpisodes(): Flow<List<DownloadedEpisodeEntity>>

    @Query("SELECT * FROM downloaded_episodes WHERE id = :episodeId")
    suspend fun getEpisodeById(episodeId: String): DownloadedEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: DownloadedEpisodeEntity)

    @Delete
    suspend fun deleteEpisode(episode: DownloadedEpisodeEntity)

    @Query("DELETE FROM downloaded_episodes WHERE id = :episodeId")
    suspend fun deleteEpisodeById(episodeId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_episodes WHERE id = :episodeId)")
    suspend fun isEpisodeDownloaded(episodeId: String): Boolean

    @Query("DELETE FROM downloaded_episodes")
    suspend fun deleteAll()
}
