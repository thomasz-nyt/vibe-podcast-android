package com.podcastplayer.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_episodes")
data class DownloadedEpisodeEntity(
    @PrimaryKey
    val id: String,
    val podcastId: String,
    val title: String,
    val description: String?,
    val pubDate: Long?,
    val audioUrl: String,
    val duration: Long?,
    val localPath: String,
    val fileSize: Long,
    val downloadDate: Long
)
