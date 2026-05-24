package com.podcastplayer.app.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.DownloadedEpisodeDao
import com.podcastplayer.app.data.local.DownloadedEpisodeEntity
import com.podcastplayer.app.data.local.MediaStoreSaver
import com.podcastplayer.app.domain.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
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
        onProgress: (Float) -> Unit = {}
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
                val saved = downloadIntoMediaStore(episode, displayName, onProgress)
                    ?: return@withContext Result.failure(
                        java.io.IOException("Could not save audio to MediaStore"),
                    )
                val entity = episode.toEntity(localPath = saved.uri.toString(), fileSize = saved.sizeBytes)
                dao.insertEpisode(entity)
                Result.success(saved.uri.toString())
            } else {
                // Pre-Q: fall back to app-private external dir (existing behavior).
                // Files won't be visible to other apps, but neither would they without
                // the legacy WRITE_EXTERNAL_STORAGE permission flow.
                val localFile = File(downloadDir, legacyFileName)
                if (localFile.exists()) return@withContext Result.success(localFile.absolutePath)
                downloadIntoFile(episode, localFile, onProgress)
                val entity = episode.toEntity(
                    localPath = localFile.absolutePath,
                    fileSize = localFile.length(),
                )
                dao.insertEpisode(entity)
                Result.success(localFile.absolutePath)
            }
        } catch (e: Exception) {
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
        if (totalBytes > 0) onProgress(1f)
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
            if (totalBytes > 0 && saved != null) onProgress(1f)
            return saved
        } finally {
            connection.disconnect()
        }
    }

    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: (Float) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead: Int
        var downloaded = 0L
        while (input.read(buffer).also { bytesRead = it } >= 0) {
            output.write(buffer, 0, bytesRead)
            downloaded += bytesRead
            if (totalBytes > 0) {
                onProgress(downloaded.toFloat() / totalBytes.toFloat())
            }
        }
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

    private fun openWithRedirects(initialUrl: String): HttpURLConnection {
        var url = URL(initialUrl)
        var redirects = 0
        val visited = mutableListOf(url.toString())
        while (true) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "*/*")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("HTTP $code without Location header from $url")
                }
                if (++redirects > MAX_REDIRECTS) {
                    throw IOException(
                        "Exceeded $MAX_REDIRECTS redirects. Chain: ${visited.joinToString(" -> ")}"
                    )
                }
                url = try {
                    URL(url, location)
                } catch (e: Exception) {
                    throw IOException("Invalid redirect target '$location' from $url", e)
                }
                visited += url.toString()
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("HTTP $code from $url")
            }
            return conn
        }
    }

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

    suspend fun deleteEpisode(episodeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val episode = dao.getEpisodeById(episodeId)
            if (episode != null) {
                deleteLocalPayload(episode.localPath)
                dao.deleteEpisodeById(episodeId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAllDownloads(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val episodes = dao.getAllEpisodes().first()
            episodes.forEach { episode -> deleteLocalPayload(episode.localPath) }
            dao.deleteAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete either a MediaStore content row or a local file, depending on the path scheme. */
    private fun deleteLocalPayload(localPath: String) {
        if (MediaStoreSaver.deleteByUri(context, localPath)) return
        val file = File(localPath)
        if (file.exists()) file.delete()
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
     * browse the Podcasts folder from another app. Sanitization (illegal chars,
     * length cap) happens in [MediaStoreSaver].
     */
    private fun buildDisplayName(episode: Episode, podcastTitle: String?): String {
        val extension = guessExtension(episode.audioUrl) ?: "mp3"
        val rawTitle = episode.title.trim()
        val base = when {
            podcastTitle.isNullOrBlank() && rawTitle.isBlank() -> "episode"
            podcastTitle.isNullOrBlank() -> rawTitle
            rawTitle.isBlank() -> podcastTitle
            else -> "$podcastTitle - $rawTitle"
        }
        return "$base.$extension"
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

    private fun Episode.toEntity(localPath: String, fileSize: Long): DownloadedEpisodeEntity {
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
            downloadDate = System.currentTimeMillis()
        )
    }

    companion object {
        private const val MAX_REDIRECTS = 20
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
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
