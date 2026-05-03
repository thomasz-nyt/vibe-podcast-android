package com.podcastplayer.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A media item that the user pasted/shared a URL for and asked the app to download
 * (issue #33). Distinct from [DownloadedEpisodeEntity] (RSS-podcast-backed downloads)
 * so that URL items can be queried, deleted, and surfaced separately on the home screen.
 *
 * Identity: [id] is a deterministic hash of `sourceUrl + mediaType` so that asking
 * for the same URL twice as the same media type is treated as a duplicate, while
 * the same URL as audio vs. video can coexist.
 */
@Entity(tableName = "url_downloads")
data class UrlDownloadEntity(
    @PrimaryKey
    val id: String,
    /** The original URL the user pasted/shared (post-normalization). */
    val sourceUrl: String,
    /** Coarse source bucket: `youtube`, `x`, or `other`. Used for badges and analytics. */
    val source: String,
    /** Title from yt-dlp metadata (or the URL if metadata extraction fails). */
    val title: String,
    /** Channel / poster, e.g. "Lex Fridman" or "@elonmusk". Nullable. */
    val uploader: String?,
    /** Remote thumbnail URL from the source (used for the home-screen card). */
    val thumbnailUrl: String?,
    /** "audio" or "video" — see [com.podcastplayer.app.domain.model.MediaType]. */
    val mediaType: String,
    /** Absolute path to the downloaded file once status == COMPLETED. */
    val localPath: String?,
    /** Duration in milliseconds (nullable when metadata is incomplete). */
    val durationMs: Long?,
    /** File size in bytes once downloaded; null while in flight. */
    val fileSize: Long?,
    /** One of [com.podcastplayer.app.data.repository.UrlDownloadStatus.name]. */
    val status: String,
    /** 0..100 (float). Reflects the most recent yt-dlp progress callback. */
    val progressPercent: Float,
    /** Last error message; null on success. */
    val errorMessage: String?,
    /** epoch millis */
    val createdAtMs: Long,
    /** epoch millis; null while pending/in-progress. */
    val completedAtMs: Long?,
)
