package com.podcastplayer.app.data.repository

import android.content.Context
import com.podcastplayer.app.PodcastApplication
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.UrlDownloadDao
import com.podcastplayer.app.data.local.UrlDownloadEntity
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.MediaType
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

/**
 * Repository for the "Add from URL" feature (issue #33).
 *
 * Owns:
 * - the on-disk download directory
 * - URL → metadata extraction via yt-dlp (`getInfo`)
 * - persistence of [UrlDownloadEntity] rows
 *
 * The actual byte-level download is performed by [com.podcastplayer.app.service.UrlDownloadService]
 * using the request returned by [buildDownloadRequest]. The repository is the single
 * source of truth for state — the service mutates rows through these APIs.
 */
class UrlDownloadRepository(private val context: Context) {

    private val dao: UrlDownloadDao
        get() = DatabaseProvider.getDatabase(context).urlDownloadDao()

    /** Where downloaded media lives. App-private to keep ToS exposure minimal. */
    val downloadDir: File
        get() = File(context.filesDir, "url_downloads").apply { mkdirs() }

    fun observeAll(): Flow<List<UrlDownloadEntity>> = dao.observeAll()

    /** Just the COMPLETED items, newest first — what the home screen surfaces. */
    fun observeCompleted(): Flow<List<UrlDownloadEntity>> =
        dao.observeByStatus(UrlDownloadStatus.COMPLETED.name)

    /** Items currently in flight (queued / extracting / downloading). */
    fun observeInFlight(): Flow<List<UrlDownloadEntity>> = dao.observeAll().map { all ->
        all.filter {
            it.status in IN_FLIGHT_STATUSES
        }
    }

    suspend fun get(id: String): UrlDownloadEntity? = dao.getById(id)

    fun observe(id: String): Flow<UrlDownloadEntity?> = dao.observeById(id)

    /**
     * Look up basic metadata (title, thumbnail, uploader, duration) for [rawUrl].
     *
     * Blocking yt-dlp call — must run off the main thread. Returns null if extraction
     * fails (network issue, unsupported URL, age-gated content, etc.).
     */
    suspend fun fetchMetadata(rawUrl: String): UrlMetadata? = withContext(Dispatchers.IO) {
        if (!PodcastApplication.youtubeDlReady) return@withContext null
        try {
            val request = YoutubeDLRequest(rawUrl).apply {
                addOption("--no-playlist")
                addOption("--socket-timeout", "30")
            }
            val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)
            UrlMetadata(
                title = info.title.orEmpty().ifBlank { rawUrl },
                uploader = info.uploader,
                thumbnailUrl = info.thumbnail,
                // VideoInfo.duration is seconds, defaults to 0 when absent (e.g. live streams).
                durationMs = info.duration.takeIf { it > 0 }?.let { it.toLong() * 1000L },
            )
        } catch (e: YoutubeDLException) {
            null
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Inserts a [UrlDownloadEntity] in [UrlDownloadStatus.QUEUED] state.
     *
     * If an entity with the same `(url, mediaType)` already exists in a non-terminal
     * state, this is a no-op and returns its existing id. If it exists in
     * [UrlDownloadStatus.FAILED] or [UrlDownloadStatus.CANCELED], the row is reset
     * and re-queued.
     */
    suspend fun enqueue(
        rawUrl: String,
        mediaType: MediaType,
        prefetchedMetadata: UrlMetadata? = null,
    ): String = withContext(Dispatchers.IO) {
        val mediaTag = mediaType.tag
        val id = UrlValidator.stableId(rawUrl, mediaTag)
        val source = UrlSource.classify(rawUrl)

        val existing = dao.getById(id)
        if (existing != null && existing.status in IN_FLIGHT_STATUSES + UrlDownloadStatus.COMPLETED.name) {
            return@withContext id
        }

        val metadata = prefetchedMetadata ?: fetchMetadata(rawUrl)
        val entity = UrlDownloadEntity(
            id = id,
            sourceUrl = rawUrl,
            source = source.tag,
            title = metadata?.title ?: rawUrl,
            uploader = metadata?.uploader,
            thumbnailUrl = metadata?.thumbnailUrl,
            mediaType = mediaTag,
            localPath = null,
            durationMs = metadata?.durationMs,
            fileSize = null,
            status = UrlDownloadStatus.QUEUED.name,
            progressPercent = 0f,
            errorMessage = null,
            createdAtMs = System.currentTimeMillis(),
            completedAtMs = null,
        )
        dao.upsert(entity)
        id
    }

    suspend fun markExtracting(id: String) = updateProgress(id, UrlDownloadStatus.EXTRACTING_METADATA, 0f)
    suspend fun markDownloading(id: String, progress: Float) =
        updateProgress(id, UrlDownloadStatus.DOWNLOADING, progress)

    private suspend fun updateProgress(id: String, status: UrlDownloadStatus, progress: Float) {
        dao.updateProgress(id, status.name, progress, null)
    }

    suspend fun markFailed(id: String, message: String?) {
        dao.markFailed(id, UrlDownloadStatus.FAILED.name, message)
    }

    suspend fun markCanceled(id: String) {
        dao.markFailed(id, UrlDownloadStatus.CANCELED.name, null)
    }

    suspend fun markCompleted(id: String, file: File) {
        dao.markCompleted(
            id = id,
            status = UrlDownloadStatus.COMPLETED.name,
            localPath = file.absolutePath,
            fileSize = if (file.exists()) file.length() else 0L,
            completedAtMs = System.currentTimeMillis(),
        )
    }

    /** Delete the row + the underlying file (if any). */
    suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getById(id)
            entity?.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            dao.deleteById(id)
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Build the yt-dlp request for the actual byte download. Used by the
     * download service so the request format is centralized here.
     */
    fun buildDownloadRequest(entity: UrlDownloadEntity, outputTemplate: File): YoutubeDLRequest {
        val mediaType = MediaType.fromTag(entity.mediaType)
        val request = YoutubeDLRequest(entity.sourceUrl)
        request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("--socket-timeout", "30")
        request.addOption("-o", "${outputTemplate.absolutePath}/%(id)s.%(ext)s")

        when (mediaType) {
            MediaType.AUDIO -> {
                // bestaudio + ffmpeg-mux to mp3 for portability and small size
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0") // best
                request.addOption("-f", "bestaudio/best")
            }

            MediaType.VIDEO -> {
                // Single-file mp4 (h264 + aac) where available, falling back to best.
                // Avoids requiring a remux step we'd have to script ourselves.
                request.addOption(
                    "-f",
                    "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                )
                request.addOption("--merge-output-format", "mp4")
            }
        }
        return request
    }

    /**
     * Map a completed [UrlDownloadEntity] to an [Episode] so the existing player
     * pipeline can play it. The synthetic `podcastId` ([SYNTHETIC_PODCAST_ID]) is
     * used to keep these grouped on the home screen and out of real podcast lookups.
     */
    fun toEpisode(entity: UrlDownloadEntity): Episode? {
        val path = entity.localPath ?: return null
        return Episode(
            id = "url:${entity.id}",
            podcastId = SYNTHETIC_PODCAST_ID,
            title = entity.title,
            description = entity.uploader,
            pubDate = entity.completedAtMs?.let { Date(it) },
            audioUrl = entity.sourceUrl,
            duration = entity.durationMs,
            imageUrl = entity.thumbnailUrl,
            isDownloaded = true,
            localPath = path,
            mediaType = MediaType.fromTag(entity.mediaType),
        )
    }

    companion object {
        const val SYNTHETIC_PODCAST_ID = "vibe-url-downloads"

        private val IN_FLIGHT_STATUSES = setOf(
            UrlDownloadStatus.QUEUED.name,
            UrlDownloadStatus.EXTRACTING_METADATA.name,
            UrlDownloadStatus.DOWNLOADING.name,
        )
    }
}

/**
 * Lightweight container for the metadata fields the UI surfaces during the
 * "preview before download" step.
 */
data class UrlMetadata(
    val title: String,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationMs: Long?,
)
