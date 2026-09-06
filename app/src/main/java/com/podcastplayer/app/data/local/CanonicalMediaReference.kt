package com.podcastplayer.app.data.local

import java.io.File
import java.net.URI
import java.util.Locale

/**
 * Stable comparison key for local media references.
 *
 * MediaStore's `external` collection is a synthetic view whose primary-volume rows can also be
 * addressed through `external_primary`. Android treats those spellings as aliases, so reference
 * protection and restore dedupe must do the same. Named removable volumes remain distinct.
 *
 * This parser deliberately uses only JVM APIs so identity behavior is unit-testable without Android.
 * The original reference remains the value used to open, update, or delete the media.
 */
object CanonicalMediaReference {
    fun keyOf(reference: String): String {
        val value = reference.trim()
        if (value.isEmpty()) return "empty:"

        return when {
            value.startsWith("content://", ignoreCase = true) -> contentKey(value)
            value.startsWith("file://", ignoreCase = true) -> fileUriKey(value)
            "://" !in value -> "file:${File(value).absolutePath}"
            else -> "uri:$value"
        }
    }

    fun equivalent(left: String, right: String): Boolean = keyOf(left) == keyOf(right)

    private fun contentKey(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull() ?: return "uri:$value"
        if (!uri.host.equals("media", ignoreCase = true)) return "uri:$value"

        val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotBlank)
        if (segments.size < 4) return "uri:$value"
        val rowId = segments.last().toLongOrNull() ?: return "uri:$value"
        val volume = when (segments.first().lowercase(Locale.US)) {
            "external", "external_primary" -> "external_primary"
            else -> segments.first().lowercase(Locale.US)
        }
        val collection = segments[1].lowercase(Locale.US)
        val table = segments.drop(2).dropLast(1).joinToString("/").lowercase(Locale.US)
        return "mediastore:$volume:$collection:$table:$rowId"
    }

    private fun fileUriKey(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull()
        val path = uri?.path?.takeIf(String::isNotBlank) ?: value.removePrefix("file://")
        return "file:${File(path).absolutePath}"
    }
}
