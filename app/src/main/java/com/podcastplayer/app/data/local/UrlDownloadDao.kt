package com.podcastplayer.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UrlDownloadDao {

    @Query("SELECT * FROM url_downloads ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<UrlDownloadEntity>>

    @Query("SELECT * FROM url_downloads WHERE status = :status ORDER BY createdAtMs DESC")
    fun observeByStatus(status: String): Flow<List<UrlDownloadEntity>>

    @Query("SELECT * FROM url_downloads WHERE id = :id")
    suspend fun getById(id: String): UrlDownloadEntity?

    @Query("SELECT * FROM url_downloads WHERE id = :id")
    fun observeById(id: String): Flow<UrlDownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UrlDownloadEntity)

    @Query("UPDATE url_downloads SET status = :status, progressPercent = :progress, errorMessage = :error WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, progress: Float, error: String?)

    @Query(
        """
        UPDATE url_downloads
        SET status = :status,
            localPath = :localPath,
            fileSize = :fileSize,
            progressPercent = 100,
            completedAtMs = :completedAtMs,
            errorMessage = NULL
        WHERE id = :id
        """
    )
    suspend fun markCompleted(id: String, status: String, localPath: String, fileSize: Long, completedAtMs: Long)

    @Query("UPDATE url_downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun markFailed(id: String, status: String, error: String?)

    @Query("DELETE FROM url_downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM url_downloads")
    suspend fun deleteAll()
}
