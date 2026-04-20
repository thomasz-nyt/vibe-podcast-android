package com.podcastplayer.app.service

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

data class StoredPlaybackSession(
    val items: List<MediaItem>,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val wasPlaying: Boolean,
    val playbackSpeed: Float,
    val isCompleted: Boolean
)

class PlaybackSessionStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(
        items: List<MediaItem>,
        currentIndex: Int,
        currentPositionMs: Long,
        wasPlaying: Boolean,
        playbackSpeed: Float,
        isCompleted: Boolean
    ) {
        if (items.isEmpty() || currentIndex !in items.indices) return

        val payload = JSONObject().apply {
            put("currentIndex", currentIndex)
            put("currentPositionMs", currentPositionMs.coerceAtLeast(0L))
            put("wasPlaying", wasPlaying)
            put("playbackSpeed", playbackSpeed)
            put("isCompleted", isCompleted)
            put("items", JSONArray().apply {
                items.forEach { item ->
                    put(JSONObject().apply {
                        put("mediaId", item.mediaId)
                        put("uri", item.localConfiguration?.uri?.toString().orEmpty())
                        put("title", item.mediaMetadata.title?.toString())
                        put("artist", item.mediaMetadata.artist?.toString())
                        put("description", item.mediaMetadata.description?.toString())
                        put("artworkUri", item.mediaMetadata.artworkUri?.toString())
                    })
                }
            })
        }

        prefs.edit { putString(KEY_SESSION, payload.toString()) }
    }

    fun load(): StoredPlaybackSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val itemsArray = json.optJSONArray("items") ?: return null
            val items = mutableListOf<MediaItem>()
            for (i in 0 until itemsArray.length()) {
                val itemJson = itemsArray.optJSONObject(i) ?: continue
                val uri = itemJson.optString("uri")
                val mediaId = itemJson.optString("mediaId")
                if (uri.isBlank() || mediaId.isBlank()) continue

                val metadata = MediaMetadata.Builder()
                    .setTitle(itemJson.optString("title").ifBlank { null })
                    .setArtist(itemJson.optString("artist").ifBlank { null })
                    .setDescription(itemJson.optString("description").ifBlank { null })
                    .setArtworkUri(itemJson.optString("artworkUri").ifBlank { null }?.let(android.net.Uri::parse))
                    .build()

                items += MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(uri)
                    .setMediaMetadata(metadata)
                    .build()
            }

            if (items.isEmpty()) return null

            val index = json.optInt("currentIndex", 0).coerceIn(0, items.lastIndex)
            StoredPlaybackSession(
                items = items,
                currentIndex = index,
                currentPositionMs = json.optLong("currentPositionMs", 0L).coerceAtLeast(0L),
                wasPlaying = json.optBoolean("wasPlaying", false),
                playbackSpeed = json.optDouble("playbackSpeed", 1.0).toFloat().coerceAtLeast(0.5f),
                isCompleted = json.optBoolean("isCompleted", false)
            )
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    companion object {
        private const val PREFS_NAME = "player_session"
        private const val KEY_SESSION = "last_session"
    }
}
