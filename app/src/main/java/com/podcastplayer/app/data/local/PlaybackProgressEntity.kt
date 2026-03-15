package com.podcastplayer.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores per-episode playback position and basic listening history.
 */
@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey
    val episodeId: String,
    val podcastId: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    /** epoch millis */
    val lastPlayedAtMs: Long,
    /** epoch millis */
    val updatedAtMs: Long = lastPlayedAtMs
)
