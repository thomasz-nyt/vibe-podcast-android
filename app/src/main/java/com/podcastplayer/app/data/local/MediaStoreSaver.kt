package com.podcastplayer.app.data.local

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Saves audio / video files into the shared MediaStore so other apps (VLC, Files,
 * Gallery, etc.) can discover them. On API < 29 we don't write to MediaStore
 * directly (would require WRITE_EXTERNAL_STORAGE + a runtime permission flow);
 * callers should fall back to app-private storage in that case.
 *
 * Identity is provided by the caller via [displayName] — duplicate names get an
 * "(2)" suffix from the platform. The returned [SavedMedia.uri] is a content://
 * URI suitable for both ExoPlayer (`MediaItem.setUri`) and ContentResolver.delete.
 */
object MediaStoreSaver {

    const val AUDIO_SUBDIR = "Podcasts/VibePodcast"
    const val VIDEO_SUBDIR = "Movies/VibePodcast"

    /** True if the device supports scoped-storage MediaStore writes without runtime permission. */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    data class SavedMedia(
        val uri: Uri,
        /**
         * Absolute path on disk if obtainable — useful for callers that still want
         * to inspect file size, but should not be used to open the file (use [uri]).
         */
        val path: String?,
        val sizeBytes: Long,
    )

    /**
     * Writes audio bytes from [source] into MediaStore.Audio. Returns null on
     * failure (or on API < 29 — caller must fall back).
     */
    fun saveAudio(
        context: Context,
        displayName: String,
        mimeType: String,
        source: (OutputStream) -> Unit,
    ): SavedMedia? = save(
        context = context,
        collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        displayName = displayName,
        mimeType = mimeType,
        relativePath = AUDIO_SUBDIR,
        source = source,
    )

    fun saveVideo(
        context: Context,
        displayName: String,
        mimeType: String,
        source: (OutputStream) -> Unit,
    ): SavedMedia? = save(
        context = context,
        collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        displayName = displayName,
        mimeType = mimeType,
        relativePath = VIDEO_SUBDIR,
        source = source,
    )

    /** Convenience: stream a [File] into MediaStore audio. */
    fun saveAudioFromFile(
        context: Context,
        displayName: String,
        mimeType: String,
        file: File,
    ): SavedMedia? = saveAudio(context, displayName, mimeType) { out ->
        file.inputStream().use { it.copyTo(out) }
    }

    fun saveVideoFromFile(
        context: Context,
        displayName: String,
        mimeType: String,
        file: File,
    ): SavedMedia? = saveVideo(context, displayName, mimeType) { out ->
        file.inputStream().use { it.copyTo(out) }
    }

    /**
     * Delete a MediaStore entry by its content URI. Safe to call with non-content
     * URIs — returns false in that case so the caller can fall back to File.delete.
     */
    fun deleteByUri(context: Context, contentUri: String): Boolean {
        if (!contentUri.startsWith("content://")) return false
        return try {
            context.contentResolver.delete(Uri.parse(contentUri), null, null) > 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun save(
        context: Context,
        collection: Uri,
        displayName: String,
        mimeType: String,
        relativePath: String,
        source: (OutputStream) -> Unit,
    ): SavedMedia? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sanitize(displayName))
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values) ?: return null
        var size = 0L
        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                source(out)
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            // Best-effort size lookup.
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) size = c.getLong(0)
            }
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            return null
        }

        // Mark visible.
        val finalize = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(uri, finalize, null, null)

        return SavedMedia(uri = uri, path = resolveAbsolutePath(relativePath, displayName), sizeBytes = size)
    }

    private fun resolveAbsolutePath(relativePath: String, displayName: String): String? {
        return try {
            val root = Environment.getExternalStorageDirectory()
            File(File(root, relativePath), sanitize(displayName)).absolutePath
        } catch (_: Throwable) {
            null
        }
    }

    // Delegated so writers (this) and readers (MediaStoreScanner matching) can
    // never drift apart on what a stored name looks like.
    private fun sanitize(name: String): String = MediaNaming.sanitize(name)

    /**
     * Decide whether a [localPath] string points at a MediaStore entry (content://)
     * or a regular file on disk.
     */
    fun isContentUri(localPath: String?): Boolean =
        localPath != null && localPath.startsWith("content://")
}
