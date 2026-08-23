package com.podcastplayer.app.data.local

/**
 * Single source of truth for the display names we write into MediaStore and the
 * match keys used to recognize those files again later (JIT download reuse, the
 * post-reinstall restore flow, and the duplicate cleaner).
 *
 * Pure JVM — no Android dependencies — so the naming/matching rules are unit
 * testable. If you change how names are BUILT here, files written by older
 * versions must still MATCH, so only ever loosen [matchKey], never tighten it.
 */
object MediaNaming {

    const val MAX_DISPLAY_NAME_LENGTH = 120

    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|]")

    /**
     * The " (1)" / " (2)" suffix MediaStore appends to a display name's base when
     * a file with the same name already exists in the folder.
     */
    private val DUPLICATE_SUFFIX = Regex(" \\(\\d+\\)$")

    /** Mirrors the historical MediaStoreSaver sanitization exactly. */
    fun sanitize(name: String): String =
        name.replace(ILLEGAL_CHARS, "_").take(MAX_DISPLAY_NAME_LENGTH).ifBlank { "vibe-media" }

    /**
     * Display name for an RSS episode download: "<podcast> - <episode>.<ext>".
     * Same fallbacks as the historical DownloadManager implementation.
     */
    fun episodeDisplayName(
        podcastTitle: String?,
        episodeTitle: String,
        extension: String,
        identity: MediaIdentity? = null,
    ): String {
        val rawTitle = episodeTitle.trim()
        val base = when {
            podcastTitle.isNullOrBlank() && rawTitle.isBlank() -> "episode"
            podcastTitle.isNullOrBlank() -> rawTitle
            rawTitle.isBlank() -> podcastTitle
            else -> "$podcastTitle - $rawTitle"
        }
        return identifiedName(base, extension.ifBlank { "mp3" }, identity)
    }

    /**
     * Display name for a URL (yt-dlp) download: "<uploader> - <title>.<ext>".
     * Same fallbacks as the historical UrlDownloadService implementation.
     */
    fun urlDisplayName(
        uploader: String?,
        title: String,
        extension: String,
        identity: MediaIdentity? = null,
    ): String {
        val rawTitle = title.trim()
        val rawUploader = uploader?.trim()?.removePrefix("@")?.trim()
        val base = when {
            rawUploader.isNullOrBlank() && rawTitle.isBlank() -> "vibe-clip"
            rawUploader.isNullOrBlank() -> rawTitle
            rawTitle.isBlank() -> rawUploader
            else -> "$rawUploader - $rawTitle"
        }
        return identifiedName(base, extension.ifBlank { "mp4" }, identity)
    }

    fun addIdentity(displayName: String, identity: MediaIdentity): String {
        val extension = displayName.substringAfterLast('.', "")
        val title = titleFromDisplayName(displayName)
        return identifiedName(title, extension.ifBlank { "mp3" }, identity)
    }

    private fun identifiedName(base: String, extension: String, identity: MediaIdentity?): String {
        if (identity == null) return sanitize("$base.$extension")
        val safeExtension = sanitize(extension).take(10).ifBlank { "mp3" }
        val fixedLength = identity.suffix.length + safeExtension.length + 1
        val titleLimit = (MAX_DISPLAY_NAME_LENGTH - fixedLength).coerceAtLeast(1)
        val safeBase = base.replace(ILLEGAL_CHARS, "_").trim().ifBlank { "vibe-media" }.take(titleLimit)
        return "$safeBase${identity.suffix}.$safeExtension"
    }

    /**
     * Canonical key for deciding that two display names refer to the same media:
     * extension-agnostic, case-insensitive, and ignoring the " (n)" suffix
     * MediaStore appends on name collisions. Extension-agnostic so a re-download
     * that would produce "episode.m4a" still matches an existing "episode.mp3".
     */
    fun matchKey(displayName: String): String {
        return baseName(displayName).replace(DUPLICATE_SUFFIX, "").lowercase()
    }

    internal fun baseName(displayName: String): String {
        val trimmed = displayName.trim()
        val dot = trimmed.lastIndexOf('.')
        return if (dot > 0) trimmed.substring(0, dot) else trimmed
    }

    /** True when the name carries a MediaStore collision suffix like "title (2).mp3". */
    fun hasDuplicateSuffix(displayName: String): Boolean {
        val base = baseName(displayName)
        return DUPLICATE_SUFFIX.containsMatchIn(base)
    }

    /** Display title recovered from a file name: extension and " (n)" suffix stripped. */
    fun titleFromDisplayName(displayName: String): String {
        val trimmed = displayName.trim()
        val base = baseName(displayName)
        val withoutCollision = base.replace(DUPLICATE_SUFFIX, "")
        val identity = MediaIdentity.parse(displayName)
        return if (identity == null) {
            withoutCollision.ifBlank { trimmed }
        } else {
            withoutCollision.removeSuffix(identity.suffix).ifBlank { trimmed }
        }
    }
}
