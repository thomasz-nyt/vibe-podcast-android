package com.podcastplayer.app.service

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.podcastplayer.app.data.remote.upgradeITunesArtwork
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

/** Plain-data snapshot of one playlist item, captured off the [MediaItem]/[MediaMetadata]
 *  Media3 types so it can be built on the main thread (where Player access is required)
 *  and then serialized to JSON off-main without touching Player-adjacent objects again. */
data class PlaybackSessionItemSnapshot(
    val mediaId: String,
    val uri: String,
    val title: String?,
    val artist: String?,
    val description: String?,
    val artworkUri: String?,
)

/** Plain-data snapshot of the whole playback session, ready to be JSON-serialized and
 *  written to SharedPreferences off the main thread. [capturedAtMs] lets [save] callers
 *  guard against an older snapshot overwriting a newer one when writes are serialized. */
data class PlaybackSessionSnapshot(
    val items: List<PlaybackSessionItemSnapshot>,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val wasPlaying: Boolean,
    val playbackSpeed: Float,
    val isCompleted: Boolean,
    val capturedAtMs: Long = System.currentTimeMillis(),
)

class PlaybackSessionStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Serializes [snapshot] to JSON and writes it to SharedPreferences. Both steps are
     * plain CPU/disk work with no Player access, so callers should invoke this off the
     * main thread (see [com.podcastplayer.app.service.PlayerService]'s persist path).
     */
    fun save(snapshot: PlaybackSessionSnapshot) {
        if (snapshot.items.isEmpty() || snapshot.currentIndex !in snapshot.items.indices) return

        val payload = JSONObject().apply {
            put("currentIndex", snapshot.currentIndex)
            put("currentPositionMs", snapshot.currentPositionMs.coerceAtLeast(0L))
            put("wasPlaying", snapshot.wasPlaying)
            put("playbackSpeed", snapshot.playbackSpeed)
            put("isCompleted", snapshot.isCompleted)
            put("items", JSONArray().apply {
                snapshot.items.forEach { item ->
                    put(JSONObject().apply {
                        put("mediaId", item.mediaId)
                        put("uri", item.uri)
                        put("title", item.title)
                        put("artist", item.artist)
                        put("description", item.description)
                        put("artworkUri", item.artworkUri)
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
                    .setArtworkUri(
                        upgradeITunesArtwork(itemJson.optString("artworkUri").ifBlank { null })
                            ?.let(android.net.Uri::parse)
                    )
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
