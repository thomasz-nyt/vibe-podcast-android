package com.podcastplayer.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.MediaType
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

/** One row is one complete, successful parse, including an empty feed. Replacement is atomic. */
@Entity(tableName = "feed_snapshots", primaryKeys = ["podcastId", "feedUrl"])
data class FeedSnapshotEntity(
    val podcastId: String,
    val feedUrl: String,
    val fetchedAtMs: Long,
    val episodesJson: String,
)

@Dao
interface FeedSnapshotDao {
    @Query("SELECT * FROM feed_snapshots WHERE podcastId = :podcastId AND feedUrl = :feedUrl")
    suspend fun get(podcastId: String, feedUrl: String): FeedSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(snapshot: FeedSnapshotEntity)
}

data class FeedSnapshot(val episodes: List<Episode>, val fetchedAtMs: Long)

class FeedSnapshotStorage(private val dao: FeedSnapshotDao) {
    suspend fun save(podcastId: String, feedUrl: String, episodes: List<Episode>, fetchedAtMs: Long) {
        val json = JSONArray()
        episodes.forEach { episode ->
            json.put(JSONObject().apply {
                put("id", episode.id)
                put("title", episode.title)
                put("description", episode.description)
                put("pubDate", episode.pubDate?.time)
                put("audioUrl", episode.audioUrl)
                put("duration", episode.duration)
                put("imageUrl", episode.imageUrl)
                put("mediaType", episode.mediaType.tag)
            })
        }
        dao.replace(FeedSnapshotEntity(podcastId, feedUrl, fetchedAtMs, json.toString()))
    }

    suspend fun load(podcastId: String, feedUrl: String): FeedSnapshot? {
        val row = dao.get(podcastId, feedUrl) ?: return null
        val json = JSONArray(row.episodesJson)
        val episodes = (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            Episode(
                id = item.getString("id"), podcastId = podcastId, title = item.getString("title"),
                description = item.nullableString("description"),
                pubDate = if (item.has("pubDate")) Date(item.getLong("pubDate")) else null,
                audioUrl = item.getString("audioUrl"),
                duration = if (item.has("duration")) item.getLong("duration") else null,
                imageUrl = item.nullableString("imageUrl"),
                mediaType = MediaType.fromTag(item.optString("mediaType", "audio")),
            )
        }
        return FeedSnapshot(episodes, row.fetchedAtMs)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
