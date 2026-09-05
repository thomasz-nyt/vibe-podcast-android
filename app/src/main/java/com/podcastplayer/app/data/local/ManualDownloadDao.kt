package com.podcastplayer.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualDownloadDao {

    @Query("SELECT * FROM manual_downloads ORDER BY createdAtMs ASC")
    fun observeAll(): Flow<List<ManualDownloadEntity>>

    @Query("SELECT * FROM manual_downloads WHERE requestId = :requestId")
    suspend fun getByRequestId(requestId: String): ManualDownloadEntity?

    @Query(
        """
        SELECT * FROM manual_downloads
        WHERE status IN ('QUEUED', 'RUNNING')
        ORDER BY createdAtMs ASC
        """
    )
    suspend fun getActive(): List<ManualDownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: ManualDownloadEntity)

    @Query(
        """
        UPDATE manual_downloads
        SET status = :status, progressPercent = :progressPercent, errorMessage = :errorMessage
        WHERE requestId = :requestId
        """
    )
    suspend fun updateState(
        requestId: String,
        status: String,
        progressPercent: Float,
        errorMessage: String?,
    )

    @Query("DELETE FROM manual_downloads WHERE requestId = :requestId")
    suspend fun deleteByRequestId(requestId: String)
}
