package com.podcastplayer.app.data.local

import android.Manifest
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import java.security.MessageDigest

/**
 * Reads back the media this app (or a previous install of it) wrote into the
 * shared `Podcasts/VibePodcast` and `Movies/VibePodcast` folders.
 *
 * Ownership matters here: after an uninstall/reinstall the app is a *different
 * owner* of those files. Same-install rows remain queryable without broad media
 * permission. Cross-install access may require [requiredReadPermissions]; query
 * failures are surfaced explicitly instead of being reported as an empty scan.
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
    ) {
        val canonicalKey: String
            get() = CanonicalMediaReference.keyOf(uriString)
    }

    sealed interface ScanResult {
        data class Success(val items: List<FoundMedia>) : ScanResult
        data class PermissionRequired(val permissions: Set<String>) : ScanResult
        data class Failed(val message: String) : ScanResult
    }

    sealed interface FindExistingResult {
        data class Found(val item: FoundMedia) : FindExistingResult
        data object NotFound : FindExistingResult
        data class PermissionRequired(val permissions: Set<String>) : FindExistingResult
        data class Failed(val message: String) : FindExistingResult
    }

    fun hasReadPermission(): Boolean = requiredReadPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** Everything in both Vibe folders, audio and video. */
    fun scanAll(): ScanResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ScanResult.Success(emptyList())
        val audio = scanCollection(isVideo = false)
        if (audio !is ScanResult.Success) return audio
        val video = scanCollection(isVideo = true)
        if (video !is ScanResult.Success) return video
        return ScanResult.Success((audio.items + video.items).distinctBy { it.canonicalKey })
    }

    /**
     * Best existing file matching [expectedDisplayName] (per [MediaNaming.matchKey]).
     * Prefers an exact name match, then un-suffixed names, then the oldest copy.
     */
    fun findExisting(isVideo: Boolean, expectedDisplayName: String): FindExistingResult {
        val expectedIdentity = MediaIdentity.parse(expectedDisplayName) ?: return FindExistingResult.NotFound
        return when (val scan = scanCollection(isVideo)) {
            is ScanResult.Success -> {
                val item = scan.items
                    .filter { it.sizeBytes > 0L && MediaIdentity.parse(it.displayName) == expectedIdentity }
                    .minWithOrNull(
                        compareBy(
                            { it.displayName != expectedDisplayName },
                            { MediaNaming.hasDuplicateSuffix(it.displayName) },
                            { it.dateAddedSec },
                        ),
                    )
                item?.let { FindExistingResult.Found(it) } ?: FindExistingResult.NotFound
            }
            is ScanResult.PermissionRequired -> FindExistingResult.PermissionRequired(scan.permissions)
            is ScanResult.Failed -> FindExistingResult.Failed(scan.message)
        }
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

    fun createWriteRequest(uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || uris.isEmpty()) return null
        return MediaStore.createWriteRequest(context.contentResolver, uris)
    }

    sealed interface MutationResult {
        data object Success : MutationResult
        data class NeedsConsent(val pendingIntent: PendingIntent) : MutationResult
        data object Failed : MutationResult
    }

    /** Delete one row, surfacing Android 10's per-item recoverable consent request. */
    fun delete(uriString: String): MutationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return try {
                if (context.contentResolver.delete(Uri.parse(uriString), null, null) > 0) {
                    MutationResult.Success
                } else {
                    MutationResult.Failed
                }
            } catch (_: Throwable) {
                MutationResult.Failed
            }
        }
        return deleteScoped(uriString)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteScoped(uriString: String): MutationResult {
        return try {
            if (context.contentResolver.delete(Uri.parse(uriString), null, null) > 0) {
                MutationResult.Success
            } else {
                MutationResult.Failed
            }
        } catch (error: RecoverableSecurityException) {
            MutationResult.NeedsConsent(error.userAction.actionIntent)
        } catch (_: Throwable) {
            MutationResult.Failed
        }
    }

    /** Rename a MediaStore row without changing its content or relative folder. */
    fun rename(uriString: String, displayName: String): MutationResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return MutationResult.Failed
        return renameScoped(uriString, displayName)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun renameScoped(uriString: String, displayName: String): MutationResult {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, MediaNaming.sanitize(displayName))
        }
        return try {
            if (context.contentResolver.update(Uri.parse(uriString), values, null, null) > 0) {
                MutationResult.Success
            } else {
                MutationResult.Failed
            }
        } catch (error: RecoverableSecurityException) {
            MutationResult.NeedsConsent(error.userAction.actionIntent)
        } catch (_: Throwable) {
            MutationResult.Failed
        }
    }

    /** SHA-256 of a scanned item; callers limit this to same-size candidate sets. */
    fun sha256(uriString: String): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Best-effort direct delete of a single MediaStore entry by content URI. Returns
     * true if the file was removed (the current install owns it), false if it needs
     * user consent (non-owned) or the delete failed. Callers batch the `false` URIs
     * into a single [createDeleteRequest] consent dialog.
     */
    fun deleteDirect(uriString: String): Boolean = MediaStoreSaver.deleteByUri(context, uriString)

    private fun scanCollection(isVideo: Boolean): ScanResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ScanResult.Success(emptyList())

        val folder = if (isVideo) MediaStoreSaver.VIDEO_SUBDIR else MediaStoreSaver.AUDIO_SUBDIR
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val volumes = runCatching { MediaStore.getExternalVolumeNames(context) }
            .getOrElse { error -> return scanFailure(error) }
            .ifEmpty { setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY) }

        val results = mutableListOf<FoundMedia>()
        for (volume in volumes) {
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(volume)
            } else {
                MediaStore.Audio.Media.getContentUri(volume)
            }
            try {
                val cursor = context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                    arrayOf("$folder%"),
                    null,
                ) ?: return ScanResult.Failed("Media provider returned no cursor")
                cursor.use {
                    val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    while (it.moveToNext()) {
                        val displayName = it.getString(nameCol) ?: continue
                        results += FoundMedia(
                            uriString = ContentUris.withAppendedId(collection, it.getLong(idCol)).toString(),
                            displayName = displayName,
                            sizeBytes = it.getLong(sizeCol),
                            dateAddedSec = it.getLong(dateCol),
                            isVideo = isVideo,
                        )
                    }
                }
            } catch (error: Throwable) {
                return scanFailure(error)
            }
        }
        return ScanResult.Success(results.distinctBy { it.canonicalKey })
    }

    private fun scanFailure(error: Throwable): ScanResult = when (error) {
        is SecurityException -> ScanResult.PermissionRequired(requiredReadPermissions().toSet())
        else -> ScanResult.Failed(error.message ?: error.javaClass.simpleName)
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
