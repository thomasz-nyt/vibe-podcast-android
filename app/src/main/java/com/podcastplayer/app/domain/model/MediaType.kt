package com.podcastplayer.app.domain.model

/**
 * The kind of media a piece of content represents.
 *
 * Existing podcast episodes default to [AUDIO]. URL-downloaded items (issue #33)
 * may be [AUDIO] (extracted MP3) or [VIDEO] (MP4 with both video and audio tracks).
 */
enum class MediaType {
    AUDIO,
    VIDEO;

    /** Lower-case enum name; the form persisted in the DB and surfaced as a `tag`. */
    val tag: String get() = name.lowercase()

    companion object {
        /** Reverse of [tag]. Defaults to [AUDIO] for unknown values. */
        fun fromTag(tag: String): MediaType =
            entries.firstOrNull { it.tag == tag.lowercase() } ?: AUDIO
    }
}
