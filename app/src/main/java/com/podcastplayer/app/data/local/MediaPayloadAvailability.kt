package com.podcastplayer.app.data.local

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Present-day readability of a persisted local media reference. Not stored in Room. */
sealed interface MediaPayloadAvailability {
    val reference: String

    data class Available(
        override val reference: String,
        val canonicalKey: String = CanonicalMediaReference.keyOf(reference),
        val sizeBytes: Long? = null,
    ) : MediaPayloadAvailability

    data class Missing(override val reference: String) : MediaPayloadAvailability
    data class PermissionRequired(override val reference: String) : MediaPayloadAvailability
    data class Unreadable(override val reference: String, val reason: String? = null) : MediaPayloadAvailability
}

/**
 * Probes the exact persisted reference instead of inferring access from broad media permission.
 * Same-install MediaStore rows are commonly readable without library-wide access.
 */
class MediaPayloadProbe(private val context: Context) {
    fun probe(reference: String?): MediaPayloadAvailability {
        val value = reference?.trim().orEmpty()
        if (value.isEmpty()) return MediaPayloadAvailability.Missing(value)
        return if (value.startsWith("content://", ignoreCase = true)) {
            probeContentUri(value)
        } else {
            probeFile(value)
        }
    }

    private fun probeContentUri(reference: String): MediaPayloadAvailability {
        return try {
            val uri = Uri.parse(reference)
            val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
                ?: return MediaPayloadAvailability.Missing(reference)
            descriptor.use {
                val size = it.length.takeIf { length -> length >= 0L }
                if (size == 0L) {
                    MediaPayloadAvailability.Unreadable(reference, "Media file is empty")
                } else {
                    MediaPayloadAvailability.Available(reference, sizeBytes = size)
                }
            }
        } catch (_: SecurityException) {
            MediaPayloadAvailability.PermissionRequired(reference)
        } catch (_: FileNotFoundException) {
            MediaPayloadAvailability.Missing(reference)
        } catch (error: IOException) {
            MediaPayloadAvailability.Unreadable(reference, error.message)
        } catch (error: RuntimeException) {
            MediaPayloadAvailability.Unreadable(reference, error.message)
        }
    }

    private fun probeFile(reference: String): MediaPayloadAvailability {
        val file = if (reference.startsWith("file://", ignoreCase = true)) {
            Uri.parse(reference).path?.let(::File)
        } else {
            File(reference)
        } ?: return MediaPayloadAvailability.Missing(reference)

        if (!file.exists() || !file.isFile) return MediaPayloadAvailability.Missing(reference)
        if (file.length() == 0L) {
            return MediaPayloadAvailability.Unreadable(reference, "Media file is empty")
        }
        return try {
            file.inputStream().use { input -> input.read(ByteArray(1)) }
            MediaPayloadAvailability.Available(reference, sizeBytes = file.length())
        } catch (_: FileNotFoundException) {
            MediaPayloadAvailability.Missing(reference)
        } catch (error: SecurityException) {
            MediaPayloadAvailability.PermissionRequired(reference)
        } catch (error: IOException) {
            MediaPayloadAvailability.Unreadable(reference, error.message)
        }
    }
}

