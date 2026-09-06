package com.podcastplayer.app.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.DownloadedEpisodeDao
import com.podcastplayer.app.data.local.DownloadedEpisodeEntity
import com.podcastplayer.app.data.local.DownloadOrigin
import com.podcastplayer.app.data.local.MediaIdentity
import com.podcastplayer.app.data.local.MediaNaming
import com.podcastplayer.app.data.local.MediaPayloadAvailability
import com.podcastplayer.app.data.local.MediaPayloadProbe
import com.podcastplayer.app.data.local.MediaStoreSaver
import com.podcastplayer.app.data.local.MediaStoreScanner
import com.podcastplayer.app.domain.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.Date
import java.util.Locale

data class ResolvedDownloadedEpisode(
    val entity: DownloadedEpisodeEntity,
    val episode: Episode,
    val availability: MediaPayloadAvailability,
)

class DownloadManager(private val context: Context) {

    private val payloadProbe = MediaPayloadProbe(context)
    private val availabilityRefresh = MutableStateFlow(0L)

    private val dao: DownloadedEpisodeDao
        get() = DatabaseProvider.getDatabase(context).downloadedEpisodeDao()

    private val downloadDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS), "episodes").apply {
            mkdirs()
        }

    suspend fun downloadEpisode(
        episode: Episode,
        podcastTitle: String? = null,
        origin: DownloadOrigin = DownloadOrigin.MANUAL,
        onProgress: (Float) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Path-on-disk name still uses a hash for the legacy app-private path
            // (deterministic, collision-free, FS-safe). The MediaStore display name
            // is built from the human-readable title so users browsing in VLC /
            // Files see context, not a hex string.
            val legacyFileName = buildHashedFileName(episode)
            val displayName = buildDisplayName(episode, podcastTitle)

            // A Room row is metadata, not proof that its payload still exists. Missing
            // rows continue through reuse/network repair; blocked references keep their
            // metadata and fail explicitly rather than silently creating duplicates.
            val existing = dao.getEpisodeById(episode.id)
            when (val availability = existing?.let { payloadProbe.probe(it.localPath) }) {
                is MediaPayloadAvailability.Available ->
                    return@withContext Result.success(availability.reference)
                is MediaPayloadAvailability.PermissionRequired ->
                    return@withContext Result.failure(MediaPermissionRequiredException(availability.reference))
                is MediaPayloadAvailability.Missing,
                is MediaPayloadAvailability.Unreadable,
                null -> Unit // Keep metadata and attempt a verified replacement.
            }

            if (MediaStoreSaver.isSupported()) {
                // Reuse an already-on-disk copy instead of re-downloading. Scanner failures
                // remain distinguishable from a real miss so a provider problem never causes
                // another download or overwrites the existing metadata row.
                when (val reusable = MediaStoreScanner(context).findExisting(
                    isVideo = false,
                    expectedDisplayName = displayName,
                )) {
                    is MediaStoreScanner.FindExistingResult.Found -> {
                        onProgress(1f)
                        val entity = episode.toEntity(
                            localPath = reusable.item.uriString,
                            fileSize = reusable.item.sizeBytes,
                            origin = origin,
                        )
                        dao.insertEpisode(entity)
                        return@withContext Result.success(reusable.item.uriString)
                    }
                    is MediaStoreScanner.FindExistingResult.PermissionRequired -> {
                        if (existing != null) {
                            return@withContext Result.failure(MediaPermissionRequiredException(existing.localPath))
                        }
                    }
                    is MediaStoreScanner.FindExistingResult.Failed ->
                        return@withContext Result.failure(MediaScanException(reusable.message))
                    MediaStoreScanner.FindExistingResult.NotFound -> Unit
                }

                val saved = runInterruptible {
                    downloadIntoMediaStore(episode, displayName, onProgress)
                }
                currentCoroutineContext().ensureActive()
                saved
                    ?: return@withContext Result.failure(
                        java.io.IOException("Could not save audio to MediaStore"),
                    )
                val savedReference = saved.uri.toString()
                when (val availability = payloadProbe.probe(savedReference)) {
                    is MediaPayloadAvailability.Available -> {
                        val entity = episode.toEntity(
                            localPath = savedReference,
                            fileSize = availability.sizeBytes ?: saved.sizeBytes,
                            origin = origin,
                        )
                        dao.insertEpisode(entity)
                        Result.success(savedReference)
                    }
                    else -> {
                        MediaStoreSaver.deleteByUri(context, savedReference)
                        Result.failure(availability.asDownloadException())
                    }
                }
            } else {
                // Pre-Q: fall back to app-private external dir (existing behavior).
                // Files won't be visible to other apps, but neither would they without
                // the legacy WRITE_EXTERNAL_STORAGE permission flow.
                val localFile = File(downloadDir, legacyFileName)
                if (payloadProbe.probe(localFile.absolutePath) is MediaPayloadAvailability.Available) {
                    dao.insertEpisode(
                        episode.toEntity(
                            localPath = localFile.absolutePath,
                            fileSize = localFile.length(),
                            origin = origin,
                        )
                    )
                    return@withContext Result.success(localFile.absolutePath)
                }
                runInterruptible {
                    downloadIntoFile(episode, localFile, onProgress)
                }
                currentCoroutineContext().ensureActive()
                when (val availability = payloadProbe.probe(localFile.absolutePath)) {
                    is MediaPayloadAvailability.Available -> {
                        val entity = episode.toEntity(
                            localPath = localFile.absolutePath,
                            fileSize = availability.sizeBytes ?: localFile.length(),
                            origin = origin,
                        )
                        dao.insertEpisode(entity)
                        Result.success(localFile.absolutePath)
                    }
                    else -> {
                        localFile.delete()
                        Result.failure(availability.asDownloadException())
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Result.failure(e)
        }
    }

    private fun downloadIntoFile(
        episode: Episode,
        localFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val connection = openWithRedirects(episode.audioUrl)
        val totalBytes = connection.contentLengthLong
        try {
            connection.inputStream.use { input ->
                FileOutputStream(localFile).use { output ->
                    copyWithProgress(input, output, totalBytes, onProgress)
                }
            }
        } catch (e: Exception) {
            if (localFile.exists()) localFile.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadIntoMediaStore(
        episode: Episode,
        fileName: String,
        onProgress: (Float) -> Unit,
    ): MediaStoreSaver.SavedMedia? {
        val mime = guessAudioMime(fileName)
        val connection = openWithRedirects(episode.audioUrl)
        val totalBytes = connection.contentLengthLong
        try {
            val saved = MediaStoreSaver.saveAudio(context, fileName, mime) { output ->
                connection.inputStream.use { input ->
                    copyWithProgress(input, output, totalBytes, onProgress)
                }
            }
            return saved
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Copies [input] to [output], reporting progress via [onProgress].
     *
     * Only fires [onProgress] when the integer percent (0-100) changes, rather than
     * on every buffer-sized chunk (thousands of emissions per episode otherwise —
     * see PodcastViewModel's downloadProgress StateFlow, which rebuilds its backing
     * map on every emission). Always emits a final `onProgress(1f)` once the input
     * is fully drained, including when [totalBytes] is unknown (<= 0), so callers
     * observing progress see a guaranteed completion signal either way.
     */
    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: (Float) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead: Int
        var downloaded = 0L
        var lastReportedPercent = -1
        while (input.read(buffer).also { bytesRead = it } >= 0) {
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedIOException("Episode download interrupted")
            }
            output.write(buffer, 0, bytesRead)
            downloaded += bytesRead
            if (totalBytes > 0) {
                val percent = (downloaded * 100L / totalBytes).toInt().coerceIn(0, 100)
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    onProgress(percent / 100f)
                }
            }
        }
        onProgress(1f)
    }

    private fun guessAudioMime(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav" -> "audio/wav"
            else -> "audio/mpeg"
        }
    }

    private fun openWithRedirects(initialUrl: String): HttpURLConnection =
        HttpConnections.openWithRedirects(initialUrl, connectTimeoutMs = 30_000, readTimeoutMs = 30_000)

    suspend fun isEpisodeDownloaded(episodeId: String): Boolean = withContext(Dispatchers.IO) {
        dao.getEpisodeById(episodeId)?.let(::resolve)?.availability is MediaPayloadAvailability.Available
    }

    suspend fun getDownloadedEpisodes(podcastId: String): List<Episode> = withContext(Dispatchers.IO) {
        dao.getEpisodesByPodcast(podcastId).first().map(::resolve).mapNotNull { resolved ->
            resolved.episode.takeIf { resolved.availability is MediaPayloadAvailability.Available }
        }
    }

    fun refreshAvailability() {
        availabilityRefresh.value += 1L
    }

    fun getResolvedDownloadsFlow(podcastId: String): Flow<List<ResolvedDownloadedEpisode>> =
        combine(dao.getEpisodesByPodcast(podcastId), availabilityRefresh) { list, _ ->
            list.map(::resolve)
        }.flowOn(Dispatchers.IO)

    fun getAllResolvedDownloadsFlow(): Flow<List<ResolvedDownloadedEpisode>> =
        combine(dao.getAllEpisodes(), availabilityRefresh) { list, _ ->
            list.map(::resolve)
        }.flowOn(Dispatchers.IO)

    fun getDownloadedEpisodesFlow(podcastId: String): Flow<List<Episode>> =
        getResolvedDownloadsFlow(podcastId).map { list ->
            list.mapNotNull { resolved ->
                resolved.episode.takeIf { resolved.availability is MediaPayloadAvailability.Available }
            }
        }

    fun getAllDownloadedEpisodesFlow(): Flow<List<Episode>> =
        getAllResolvedDownloadsFlow().map { list ->
            list.mapNotNull { resolved ->
                resolved.episode.takeIf { resolved.availability is MediaPayloadAvailability.Available }
            }
        }

    /** Raw entities, useful for restore, cleanup, retention, and file-size metadata. */
    fun getAllDownloadedEntitiesFlow(): Flow<List<DownloadedEpisodeEntity>> = dao.getAllEpisodes()

    suspend fun getAllDownloadedEntities(): List<DownloadedEpisodeEntity> = dao.getAllEpisodesOnce()

    /**
     * Delete one downloaded episode (row + file). Returns the content URI that
     * couldn't be deleted directly (a MediaStore entry owned by a previous install)
     * and needs the system consent dialog — empty if fully handled.
     */
    suspend fun deleteEpisode(episodeId: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val episode = dao.getEpisodeById(episodeId) ?: return@withContext emptyList()
            val consent = deleteLocalPayload(episode.localPath)
            dao.deleteEpisodeById(episodeId)
            listOfNotNull(consent)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Forget every RSS download WITHOUT touching the media files. The files stay in
     * the shared Vibe folders, where the restore flow (and just-in-time download
     * reuse) can relink them later with zero network traffic.
     */
    suspend fun clearAllRowsKeepingFiles(): Unit = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    /** Same as [deleteEpisode] but for every RSS download; returns all consent-needed URIs. */
    suspend fun deleteAllDownloads(): List<String> = withContext(Dispatchers.IO) {
        try {
            val episodes = dao.getAllEpisodes().first()
            val consent = episodes.mapNotNull { deleteLocalPayload(it.localPath) }
            dao.deleteAll()
            consent
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Delete the on-disk payload for [localPath]. For a `content://` MediaStore entry
     * we try a direct delete; if that fails (the file is owned by a previous install),
     * the URI is RETURNED so the caller can route it through the system consent dialog
     * — we must NOT fall back to `File(contentUri).delete()`, which silently no-ops on
     * a content URI and was why deletes left files on disk. Returns null when the file
     * was removed (or was a plain file path handled here).
     */
    private fun deleteLocalPayload(localPath: String): String? {
        if (localPath.startsWith("content://")) {
            return if (MediaStoreSaver.deleteByUri(context, localPath)) null else localPath
        }
        val file = File(localPath)
        if (file.exists()) file.delete()
        return null
    }

    suspend fun getDownloadedEpisode(episodeId: String): DownloadedEpisodeEntity? {
        return dao.getEpisodeById(episodeId)
    }

    private fun buildHashedFileName(episode: Episode): String {
        val source = episode.id.takeIf { it.isNotBlank() } ?: episode.audioUrl
        val extension = guessExtension(episode.audioUrl) ?: "mp3"
        val hash = MessageDigest.getInstance("MD5")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$hash.$extension"
    }

    /**
     * Human-readable display name for the MediaStore row, so users see e.g.
     * "Lex Fridman - Joe Rogan Interview.mp3" instead of a hex hash when they
     * browse the Podcasts folder from another app. Building and sanitizing live
     * in [MediaNaming] so the restore/reuse matching computes identical names.
     */
    private fun buildDisplayName(episode: Episode, podcastTitle: String?): String {
        val extension = guessExtension(episode.audioUrl) ?: "mp3"
        return MediaNaming.episodeDisplayName(
            podcastTitle = podcastTitle,
            episodeTitle = episode.title,
            extension = extension,
            identity = MediaIdentity.rss(episode.id),
        )
    }

    /**
     * Record an episode as downloaded WITHOUT downloading — the media already
     * exists on disk (found by the restore flow via [MediaStoreScanner]).
     */
    suspend fun registerExistingDownload(
        episode: Episode,
        localPath: String,
        fileSize: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val availability = payloadProbe.probe(localPath)
        if (availability !is MediaPayloadAvailability.Available) return@withContext false
        dao.insertEpisode(
            episode.toEntity(
                localPath = localPath,
                fileSize = availability.sizeBytes ?: fileSize,
                origin = DownloadOrigin.MANUAL,
            )
        )
        true
    }

    private fun guessExtension(url: String): String? {
        return try {
            val lastSegment = Uri.parse(url).lastPathSegment ?: return null
            val ext = lastSegment.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext.isBlank()) null else ext.takeIf { it.length in 1..5 }
        } catch (e: Exception) {
            null
        }
    }

    private fun Episode.toEntity(
        localPath: String,
        fileSize: Long,
        origin: DownloadOrigin,
    ): DownloadedEpisodeEntity {
        return DownloadedEpisodeEntity(
            id = id,
            podcastId = podcastId,
            title = title,
            description = description,
            pubDate = pubDate?.time,
            audioUrl = audioUrl,
            duration = duration,
            localPath = localPath,
            fileSize = fileSize,
            downloadDate = System.currentTimeMillis(),
            origin = origin.name,
        )
    }

    private fun resolve(entity: DownloadedEpisodeEntity): ResolvedDownloadedEpisode {
        val availability = payloadProbe.probe(entity.localPath)
        return ResolvedDownloadedEpisode(
            entity = entity,
            episode = entity.toDomain(availability),
            availability = availability,
        )
    }

    private fun DownloadedEpisodeEntity.toDomain(availability: MediaPayloadAvailability): Episode {
        val available = availability is MediaPayloadAvailability.Available
        return Episode(
            id = id,
            podcastId = podcastId,
            title = title,
            description = description,
            pubDate = pubDate?.let { Date(it) },
            audioUrl = audioUrl,
            duration = duration,
            imageUrl = null,
            isDownloaded = available,
            localPath = availability.reference.takeIf { available },
        )
    }
}

class MediaPermissionRequiredException(reference: String) :
    java.io.IOException("Media access is required for $reference")

class MediaUnreadableException(reference: String, reason: String?) :
    java.io.IOException(reason?.let { "Media at $reference cannot be read: $it" } ?: "Media at $reference cannot be read")

class MediaScanException(message: String) : java.io.IOException("Media scan failed: $message")

private fun MediaPayloadAvailability.asDownloadException(): Exception = when (this) {
    is MediaPayloadAvailability.PermissionRequired -> MediaPermissionRequiredException(reference)
    is MediaPayloadAvailability.Unreadable -> MediaUnreadableException(reference, reason)
    is MediaPayloadAvailability.Missing -> java.io.FileNotFoundException(reference)
    is MediaPayloadAvailability.Available -> IllegalStateException("Available media cannot be an error")
}
