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
import com.podcastplayer.app.data.local.MediaStoreSaver
import com.podcastplayer.app.data.local.MediaStoreScanner
import com.podcastplayer.app.domain.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

class DownloadManager(private val context: Context) {

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

            // Don't re-download an episode we've already saved. We key on the DB
            // row rather than file existence because the path may be a content://
            // URI from MediaStore.
            val existing = dao.getEpisodeById(episode.id)
            if (existing != null) {
                return@withContext Result.success(existing.localPath)
            }

            if (MediaStoreSaver.isSupported()) {
                // Reuse an already-on-disk copy instead of re-downloading — covers
                // both "downloaded earlier this install" edge cases and files left
                // behind by a previous install (visible once the user grants the
                // media read permission; without it this quietly finds nothing).
                val reusable = MediaStoreScanner(context).findExisting(
                    isVideo = false,
                    expectedDisplayName = displayName,
                )
                if (reusable != null) {
                    onProgress(1f)
                    val entity = episode.toEntity(
                        localPath = reusable.uriString,
                        fileSize = reusable.sizeBytes,
                        origin = origin,
                    )
                    dao.insertEpisode(entity)
                    return@withContext Result.success(reusable.uriString)
                }

                val saved = runInterruptible {
                    downloadIntoMediaStore(episode, displayName, onProgress)
                }
                currentCoroutineContext().ensureActive()
                saved
                    ?: return@withContext Result.failure(
                        java.io.IOException("Could not save audio to MediaStore"),
                    )
                val entity = episode.toEntity(
                    localPath = saved.uri.toString(),
                    fileSize = saved.sizeBytes,
                    origin = origin,
                )
                dao.insertEpisode(entity)
                Result.success(saved.uri.toString())
            } else {
                // Pre-Q: fall back to app-private external dir (existing behavior).
                // Files won't be visible to other apps, but neither would they without
                // the legacy WRITE_EXTERNAL_STORAGE permission flow.
                val localFile = File(downloadDir, legacyFileName)
                if (localFile.exists()) {
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
                val entity = episode.toEntity(
                    localPath = localFile.absolutePath,
                    fileSize = localFile.length(),
                    origin = origin,
                )
                dao.insertEpisode(entity)
                Result.success(localFile.absolutePath)
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

    suspend fun isEpisodeDownloaded(episodeId: String): Boolean {
        return dao.isEpisodeDownloaded(episodeId)
    }

    suspend fun getDownloadedEpisodes(podcastId: String): List<Episode> {
        return dao.getEpisodesByPodcast(podcastId).first().map { it.toDomain() }
    }

    fun getDownloadedEpisodesFlow(podcastId: String): Flow<List<Episode>> {
        return dao.getEpisodesByPodcast(podcastId).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getAllDownloadedEpisodesFlow(): Flow<List<Episode>> {
        return dao.getAllEpisodes().map { list ->
            list.map { it.toDomain() }
        }
    }

    /** Raw entities, useful when callers need file-size or other metadata that
     *  the domain [Episode] doesn't carry. */
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
    ): Unit = withContext(Dispatchers.IO) {
        dao.insertEpisode(
            episode.toEntity(
                localPath = localPath,
                fileSize = fileSize,
                origin = DownloadOrigin.MANUAL,
            )
        )
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

    private fun DownloadedEpisodeEntity.toDomain(): Episode {
        return Episode(
            id = id,
            podcastId = podcastId,
            title = title,
            description = description,
            pubDate = pubDate?.let { Date(it) },
            audioUrl = audioUrl,
            duration = duration,
            imageUrl = null,
            isDownloaded = true,
            localPath = localPath
        )
    }
}
