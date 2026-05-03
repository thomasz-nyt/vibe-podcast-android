package com.podcastplayer.app.data.repository

import java.net.URI
import java.security.MessageDigest

/**
 * Coarse classification of a URL used for badging, telemetry, and deciding
 * which yt-dlp options are appropriate.
 */
enum class UrlSource(val displayName: String, val tag: String) {
    YOUTUBE("YouTube", "youtube"),
    X("X", "x"),
    OTHER("Web", "other");

    companion object {
        private val YOUTUBE_HOSTS = setOf(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "music.youtube.com",
            "youtu.be",
        )
        private val X_HOSTS = setOf(
            "x.com",
            "www.x.com",
            "twitter.com",
            "www.twitter.com",
            "mobile.twitter.com",
        )

        fun classify(rawUrl: String): UrlSource {
            val host = parseHost(rawUrl)
            return when {
                host in YOUTUBE_HOSTS -> YOUTUBE
                host in X_HOSTS -> X
                else -> OTHER
            }
        }

        /** JVM-safe host parser (used in unit tests; on Android `URI` is also available). */
        internal fun parseHost(rawUrl: String): String {
            return try {
                URI(rawUrl.trim()).host?.lowercase().orEmpty()
            } catch (e: Exception) {
                ""
            }
        }

        fun fromTag(tag: String): UrlSource = entries.firstOrNull { it.tag == tag } ?: OTHER
    }
}

/**
 * Possible states for a URL download.
 *
 * Stored as a `String` (the enum name) on [com.podcastplayer.app.data.local.UrlDownloadEntity.status]
 * to keep the schema migration-friendly.
 */
enum class UrlDownloadStatus {
    /** Newly enqueued; waiting for the download service to pick it up. */
    QUEUED,

    /** Service is running yt-dlp `--dump-json` to fetch title/thumbnail/etc. */
    EXTRACTING_METADATA,

    /** yt-dlp is downloading + (for audio) transcoding via ffmpeg. */
    DOWNLOADING,

    /** File written, DB row finalized. */
    COMPLETED,

    /** Terminal failure; user can retry by re-enqueuing. */
    FAILED,

    /** User canceled before completion. */
    CANCELED,
}

/**
 * Helpers for shape-checking and identifying URLs across the app.
 */
object UrlValidator {

    private val URL_REGEX = Regex(
        """https?://[\w\-._~:/?#\[\]@!\$&'()*+,;=%]+""",
        RegexOption.IGNORE_CASE,
    )

    /** Returns the first http(s) URL found in [text], or null if none. */
    fun extractFirstUrl(text: CharSequence?): String? {
        if (text.isNullOrBlank()) return null
        return URL_REGEX.find(text.toString())?.value
    }

    /** True when [text] looks like a single, supported (YouTube/X) URL. */
    fun isSupportedUrl(text: CharSequence?): Boolean {
        val url = extractFirstUrl(text) ?: return false
        return UrlSource.classify(url) != UrlSource.OTHER
    }

    /** Stable ID for a URL + media type pair (used as the Room primary key). */
    fun stableId(url: String, mediaTypeTag: String): String {
        val canonical = canonicalize(url) + "|" + mediaTypeTag
        val md = MessageDigest.getInstance("SHA-1")
            .digest(canonical.toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }

    /** Strip tracking params and normalize for ID stability. */
    fun canonicalize(url: String): String {
        return try {
            val u = URI(url.trim())
            val host = u.host?.lowercase()?.removePrefix("www.").orEmpty()
            val path = u.path.orEmpty()
            val query = u.rawQuery.orEmpty()

            // Keep only meaningful query params; drop fbclid/utm_*/etc.
            val keptKeys = setOf("v", "list", "t", "start")
            val kept = if (query.isBlank()) {
                ""
            } else {
                query.split("&")
                    .mapNotNull { piece ->
                        val eq = piece.indexOf('=')
                        val key = if (eq < 0) piece else piece.substring(0, eq)
                        if (key in keptKeys) piece else null
                    }
                    .sorted()
                    .joinToString("&")
            }
            val q = if (kept.isNotEmpty()) "?$kept" else ""
            "${u.scheme}://$host$path$q"
        } catch (e: Exception) {
            url.trim()
        }
    }
}
