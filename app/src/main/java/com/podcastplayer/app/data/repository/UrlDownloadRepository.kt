package com.podcastplayer.app.data.repository

import android.content.Context
import com.podcastplayer.app.PodcastApplication
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.CanonicalMediaReference
import com.podcastplayer.app.data.local.DownloadOrigin
import com.podcastplayer.app.data.local.MediaNaming
import com.podcastplayer.app.data.local.MediaPayloadAvailability
import com.podcastplayer.app.data.local.MediaPayloadProbe
import com.podcastplayer.app.data.local.MediaStoreSaver
import com.podcastplayer.app.data.local.UrlDownloadDao
import com.podcastplayer.app.data.local.UrlDownloadEntity
import com.podcastplayer.app.domain.model.Episode
import com.podcastplayer.app.domain.model.MediaType
import com.podcastplayer.app.service.UrlDownloadService
import com.podcastplayer.app.service.AutoDownloadRetentionManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

data class ResolvedUrlDownload(
    val entity: UrlDownloadEntity,
    val availability: MediaPayloadAvailability,
)

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

    private val payloadProbe = MediaPayloadProbe(context)
    private val availabilityRefresh = MutableStateFlow(0L)

    private val dao: UrlDownloadDao
        get() = DatabaseProvider.getDatabase(context).urlDownloadDao()

    /** Where downloaded media lives. App-private to keep ToS exposure minimal. */
    val downloadDir: File
        get() = File(context.filesDir, "url_downloads").apply { mkdirs() }

    fun observeAll(): Flow<List<UrlDownloadEntity>> = dao.observeAll()

    /** Just the COMPLETED items, newest first. Transfer completion is not payload truth. */
    fun refreshAvailability() {
        availabilityRefresh.value += 1L
    }

    fun observeResolvedCompleted(): Flow<List<ResolvedUrlDownload>> =
        combine(dao.observeByStatus(UrlDownloadStatus.COMPLETED.name), availabilityRefresh) { list, _ ->
            list.map { entity -> ResolvedUrlDownload(entity, payloadProbe.probe(entity.localPath)) }
        }.flowOn(Dispatchers.IO)

    /** Available completed items for legacy Home call sites. */
    fun observeCompleted(): Flow<List<UrlDownloadEntity>> = observeResolvedCompleted().map { list ->
        list.mapNotNull { resolved ->
            resolved.entity.takeIf { resolved.availability is MediaPayloadAvailability.Available }
        }
    }

    /** Items currently in flight (queued / extracting / downloading). */
    fun observeInFlight(): Flow<List<UrlDownloadEntity>> = dao.observeAll().map { all ->
        all.filter {
            it.status in IN_FLIGHT_STATUSES
        }
    }

    /** Failed or canceled items that need a visible retry/delete surface. */
    fun observeNeedsAttention(): Flow<List<UrlDownloadEntity>> = dao.observeAll().map { all ->
        all.filter {
            it.status == UrlDownloadStatus.FAILED.name ||
                it.status == UrlDownloadStatus.CANCELED.name
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
        origin: DownloadOrigin = DownloadOrigin.MANUAL,
        podcastId: String? = null,
        episodePubDateMs: Long? = null,
    ): String = withContext(Dispatchers.IO) {
        val mediaTag = mediaType.tag
        val id = UrlValidator.stableId(rawUrl, mediaTag)
        val source = UrlSource.classify(rawUrl)

        val existing = dao.getById(id)
        if (existing != null && existing.status in IN_FLIGHT_STATUSES) return@withContext id
        if (existing?.status == UrlDownloadStatus.COMPLETED.name) {
            when (payloadProbe.probe(existing.localPath)) {
                is MediaPayloadAvailability.Available,
                is MediaPayloadAvailability.PermissionRequired -> return@withContext id
                is MediaPayloadAvailability.Missing,
                is MediaPayloadAvailability.Unreadable -> Unit // Requeue a repair download below.
            }
        }

        val metadata = prefetchedMetadata ?: fetchMetadata(rawUrl)
        val entity = UrlDownloadEntity(
            id = id,
            sourceUrl = rawUrl,
            source = source.tag,
            title = metadata?.title ?: existing?.title ?: rawUrl,
            uploader = metadata?.uploader ?: existing?.uploader,
            thumbnailUrl = metadata?.thumbnailUrl ?: existing?.thumbnailUrl,
            mediaType = mediaTag,
            localPath = null,
            durationMs = metadata?.durationMs ?: existing?.durationMs,
            fileSize = null,
            status = UrlDownloadStatus.QUEUED.name,
            progressPercent = 0f,
            errorMessage = null,
            createdAtMs = System.currentTimeMillis(),
            completedAtMs = null,
            origin = if (existing?.origin == DownloadOrigin.MANUAL.name) {
                DownloadOrigin.MANUAL.name
            } else {
                origin.name
            },
            podcastId = podcastId ?: existing?.podcastId,
            episodePubDateMs = episodePubDateMs ?: existing?.episodePubDateMs,
        )
        dao.upsert(entity)
        id
    }

    fun startPump() {
        UrlDownloadService.startPump(context)
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

    suspend fun retry(id: String): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext false
        if (entity.status !in RETRYABLE_STATUSES) return@withContext false
        dao.resetForRetry(
            id = id,
            status = UrlDownloadStatus.QUEUED.name,
            progress = 0f,
            error = null,
        )
        true
    }

    suspend fun repairMissing(id: String): Boolean = withContext(Dispatchers.IO) {
        val entity = dao.getById(id) ?: return@withContext false
        if (entity.status != UrlDownloadStatus.COMPLETED.name || entity.sourceUrl.isBlank()) {
            return@withContext false
        }
        when (payloadProbe.probe(entity.localPath)) {
            is MediaPayloadAvailability.Missing,
            is MediaPayloadAvailability.Unreadable -> {
                dao.resetForRetry(
                    id = id,
                    status = UrlDownloadStatus.QUEUED.name,
                    progress = 0f,
                    error = null,
                )
                true
            }
            is MediaPayloadAvailability.Available,
            is MediaPayloadAvailability.PermissionRequired -> false
        }
    }

    suspend fun requeueInterrupted() = withContext(Dispatchers.IO) {
        dao.resetStatuses(
            fromStatuses = listOf(
                UrlDownloadStatus.EXTRACTING_METADATA.name,
                UrlDownloadStatus.DOWNLOADING.name,
            ),
            toStatus = UrlDownloadStatus.QUEUED.name,
            progress = 0f,
            error = "Interrupted before completion. Retrying…",
        )
    }

    suspend fun markCompleted(id: String, localPath: String, fileSize: Long) {
        val availability = payloadProbe.probe(localPath)
        val available = availability as? MediaPayloadAvailability.Available
            ?: throw java.io.IOException("Downloaded media is not readable: ${availability.javaClass.simpleName}")
        val entity = dao.getById(id)
        dao.markCompleted(
            id = id,
            status = UrlDownloadStatus.COMPLETED.name,
            localPath = available.reference,
            fileSize = available.sizeBytes ?: fileSize,
            completedAtMs = System.currentTimeMillis(),
        )
        if (entity?.origin == DownloadOrigin.AUTO.name && entity.podcastId != null) {
            AutoDownloadRetentionManager(context).trimPodcast(entity.podcastId)
        }
    }

    /**
     * Import a media file left behind by a previous install as a playable row.
     * Used by the restore flow for files that couldn't be matched back to a
     * subscribed podcast's episode (e.g. yt-dlp clips, or episodes of shows the
     * user hasn't re-subscribed to yet). The source URL is unknowable at this
     * point, so retry is impossible — but play/delete both work.
     *
     * Returns false when this file was already imported (id is deterministic
     * from the content URI, so repeat restores are no-ops).
     */
    suspend fun importRestored(
        displayName: String,
        uriString: String,
        sizeBytes: Long,
        isVideo: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val availability = payloadProbe.probe(uriString)
        if (availability !is MediaPayloadAvailability.Available) return@withContext false
        val id = restoredIdFor(uriString)
        if (dao.getById(id) != null) return@withContext false
        val now = System.currentTimeMillis()
        dao.upsert(
            UrlDownloadEntity(
                id = id,
                sourceUrl = "",
                source = UrlSource.OTHER.tag,
                title = MediaNaming.titleFromDisplayName(displayName),
                uploader = null,
                thumbnailUrl = null,
                mediaType = if (isVideo) MediaType.VIDEO.tag else MediaType.AUDIO.tag,
                localPath = availability.reference,
                durationMs = null,
                fileSize = availability.sizeBytes ?: sizeBytes,
                status = UrlDownloadStatus.COMPLETED.name,
                progressPercent = 100f,
                errorMessage = null,
                createdAtMs = now,
                completedAtMs = now,
            )
        )
        true
    }

    /**
     * Drop the restored-orphan row for [uriString], if one exists. Called when a
     * later restore pass matches that same file back to a real RSS episode, so
     * the item isn't listed twice (once as episode, once as orphan clip).
     */
    suspend fun removeRestoredFor(uriString: String) = withContext(Dispatchers.IO) {
        dao.deleteById(restoredIdFor(uriString))
    }

    private fun restoredIdFor(uriString: String): String {
        val canonicalReference = CanonicalMediaReference.keyOf(uriString)
        val hash = java.security.MessageDigest.getInstance("MD5")
            .digest(canonicalReference.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "restored-$hash"
    }

    /** Delete the row + the underlying file (if any). */
    /**
     * Delete one URL download (row + file). Returns the content URI that couldn't be
     * deleted directly (a MediaStore entry owned by a previous install) and needs the
     * system consent dialog — empty if fully handled.
     */
    suspend fun delete(id: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getById(id)
            val consent = entity?.localPath?.let { deleteLocalPayload(it) }
            dao.deleteById(id)
            listOfNotNull(consent)
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * Forget every URL download WITHOUT touching the media files — counterpart of
     * [DownloadManager.clearAllRowsKeepingFiles] for the keep-files "Remove all".
     */
    suspend fun clearAllRowsKeepingFiles(): Unit = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    /** Delete every URL download (rows + files); returns all consent-needed URIs. */
    suspend fun deleteAllReturningConsent(): List<String> = withContext(Dispatchers.IO) {
        try {
            val all = dao.observeAll().first()
            val consent = all.mapNotNull { it.localPath?.let(::deleteLocalPayload) }
            dao.deleteAll()
            consent
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * Delete the on-disk payload for [path]. Returns the content URI (needs consent)
     * when a non-owned MediaStore entry can't be deleted directly; null otherwise.
     * See [DownloadManager.deleteLocalPayload] for why we don't `File.delete` a URI.
     */
    private fun deleteLocalPayload(path: String): String? {
        if (path.startsWith("content://")) {
            return if (MediaStoreSaver.deleteByUri(context, path)) null else path
        }
        val file = File(path)
        if (file.exists()) file.delete()
        return null
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
    fun resolve(entity: UrlDownloadEntity): ResolvedUrlDownload =
        ResolvedUrlDownload(entity, payloadProbe.probe(entity.localPath))

    fun toEpisode(entity: UrlDownloadEntity): Episode? = toEpisode(resolve(entity))

    fun toEpisode(resolved: ResolvedUrlDownload): Episode? =
        toEpisodeMetadata(resolved).takeIf {
            resolved.availability is MediaPayloadAvailability.Available
        }

    fun toEpisodeMetadata(resolved: ResolvedUrlDownload): Episode {
        val available = resolved.availability is MediaPayloadAvailability.Available
        val entity = resolved.entity
        return Episode(
            id = "url:${entity.id}",
            podcastId = SYNTHETIC_PODCAST_ID,
            title = entity.title,
            description = entity.uploader,
            pubDate = entity.completedAtMs?.let { Date(it) },
            audioUrl = entity.sourceUrl,
            duration = entity.durationMs,
            imageUrl = entity.thumbnailUrl,
            isDownloaded = available,
            localPath = resolved.availability.reference.takeIf { available },
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

        private val RETRYABLE_STATUSES = setOf(
            UrlDownloadStatus.FAILED.name,
            UrlDownloadStatus.CANCELED.name,
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
