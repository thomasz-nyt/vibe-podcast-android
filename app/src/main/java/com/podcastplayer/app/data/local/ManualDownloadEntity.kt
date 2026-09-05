package com.podcastplayer.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.podcastplayer.app.domain.model.Episode
import java.util.Date

enum class ManualDownloadStatus {
    QUEUED,
    RUNNING,
    FAILED,
}

/**
 * Durable input and UI state for an explicitly requested RSS download.
 *
 * WorkManager receives only [requestId], keeping its size-limited Data payload small. The unique
 * [episodeId] index means a retry replaces the previous request, while the per-attempt primary key
 * prevents a canceled worker from updating or deleting its replacement.
 */
@Entity(
    tableName = "manual_downloads",
    indices = [Index(value = ["episodeId"], unique = true)],
)
data class ManualDownloadEntity(
    @PrimaryKey
    val requestId: String,
    val episodeId: String,
    val podcastId: String,
    val podcastTitle: String?,
    val title: String,
    val description: String?,
    val pubDate: Long?,
    val audioUrl: String,
    val duration: Long?,
    val status: String = ManualDownloadStatus.QUEUED.name,
    val progressPercent: Float = 0f,
    val errorMessage: String? = null,
    val createdAtMs: Long,
)

fun ManualDownloadEntity.toEpisode(): Episode = Episode(
    id = episodeId,
    podcastId = podcastId,
    title = title,
    description = description,
    pubDate = pubDate?.let(::Date),
    audioUrl = audioUrl,
    duration = duration,
)
