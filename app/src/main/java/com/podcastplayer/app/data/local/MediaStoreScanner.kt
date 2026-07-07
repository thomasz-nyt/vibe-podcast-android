package com.podcastplayer.app.data.local

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

/**
 * Reads back the media this app (or a previous install of it) wrote into the
 * shared `Podcasts/VibePodcast` and `Movies/VibePodcast` folders.
 *
 * Ownership matters here: after an uninstall/reinstall the app is a *different
 * owner* of those files. Without [requiredReadPermissions] granted, MediaStore
 * queries silently return only rows the current install contributed — which
 * makes every method below degrade gracefully: same-install dedupe keeps
 * working permission-free, and cross-install restore lights up once the user
 * grants access.
 */
class MediaStoreScanner(private val context: Context) {

    /**
     * A media row found in one of the Vibe folders. [uriString] (not [Uri]) so
     * downstream matching/grouping logic stays JVM-unit-testable.
     */
    data class FoundMedia(
        val uriString: String,
        val displayName: String,
        val sizeBytes: Long,
        val dateAddedSec: Long,
        val isVideo: Boolean,
    )

    fun hasReadPermission(): Boolean = requiredReadPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** Everything in both Vibe folders, audio and video. */
    fun scanAll(): List<FoundMedia> = scanCollection(isVideo = false) + scanCollection(isVideo = true)

    /**
     * Best existing file matching [expectedDisplayName] (per [MediaNaming.matchKey]),
     * or null. Prefers an exact name match, then un-suffixed names, then the oldest
     * copy — so a reused link points at the original, not a "(2)" duplicate.
     */
    fun findExisting(isVideo: Boolean, expectedDisplayName: String): FoundMedia? {
        val key = MediaNaming.matchKey(expectedDisplayName)
        return scanCollection(isVideo)
            .filter { it.sizeBytes > 0L && MediaNaming.matchKey(it.displayName) == key }
            .minWithOrNull(
                compareBy(
                    { it.displayName != expectedDisplayName },
                    { MediaNaming.hasDuplicateSuffix(it.displayName) },
                    { it.dateAddedSec },
                ),
            )
    }

    /**
     * System consent dialog for batch-deleting [uris] (which this install may not
     * own). API 30+ only — returns null below that, where non-owned deletes would
     * need a per-file consent loop that isn't worth supporting.
     */
    fun createDeleteRequest(uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return null
        return MediaStore.createDeleteRequest(context.contentResolver, uris)
    }

    private fun scanCollection(isVideo: Boolean): List<FoundMedia> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val folder = if (isVideo) MediaStoreSaver.VIDEO_SUBDIR else MediaStoreSaver.AUDIO_SUBDIR

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
        )

        val results = mutableListOf<FoundMedia>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$folder%"),
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    results += FoundMedia(
                        uriString = ContentUris.withAppendedId(collection, id).toString(),
                        displayName = cursor.getString(nameCol) ?: continue,
                        sizeBytes = cursor.getLong(sizeCol),
                        dateAddedSec = cursor.getLong(dateCol),
                        isVideo = isVideo,
                    )
                }
            }
        } catch (_: Throwable) {
            // Query failure (odd OEM providers) — behave as "nothing found".
        }
        return results
    }

    companion object {
        fun requiredReadPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
    }
}
