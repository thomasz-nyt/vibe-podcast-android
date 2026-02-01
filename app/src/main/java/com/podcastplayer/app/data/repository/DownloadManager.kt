package com.podcastplayer.app.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.podcastplayer.app.data.local.DatabaseProvider
import com.podcastplayer.app.data.local.DownloadedEpisodeDao
import com.podcastplayer.app.data.local.DownloadedEpisodeEntity
import com.podcastplayer.app.domain.model.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = buildSafeFileName(episode)
            val localFile = File(downloadDir, fileName)

            if (localFile.exists()) {
                return@withContext Result.success(localFile.absolutePath)
            }

            val connection = URL(episode.audioUrl).openConnection()
            val totalBytes = connection.contentLengthLong

            connection.getInputStream().use { input ->
                FileOutputStream(localFile).use { output ->
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
            }

            if (totalBytes > 0) {
                onProgress(1f)
            }

            val entity = episode.toEntity(localFile.absolutePath)
            dao.insertEpisode(entity)
            Result.success(localFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
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

    suspend fun deleteEpisode(episodeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val episode = dao.getEpisodeById(episodeId)
            if (episode != null) {
                val file = File(episode.localPath)
                if (file.exists()) {
                    file.delete()
                }
                dao.deleteEpisodeById(episodeId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDownloadedEpisode(episodeId: String): DownloadedEpisodeEntity? {
        return dao.getEpisodeById(episodeId)
    }

    private fun buildSafeFileName(episode: Episode): String {
        val source = episode.id.takeIf { it.isNotBlank() } ?: episode.audioUrl
        val extension = guessExtension(episode.audioUrl) ?: "mp3"
        val hash = MessageDigest.getInstance("MD5")
            .digest(source.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$hash.$extension"
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

    private fun Episode.toEntity(localPath: String): DownloadedEpisodeEntity {
        return DownloadedEpisodeEntity(
            id = id,
            podcastId = podcastId,
            title = title,
            description = description,
            pubDate = pubDate?.time,
            audioUrl = audioUrl,
            duration = duration,
            localPath = localPath,
            fileSize = File(localPath).length(),
            downloadDate = System.currentTimeMillis()
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
