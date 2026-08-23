package com.podcastplayer.app.data.local

import java.security.MessageDigest

/** Stable, versioned identity embedded in MediaStore display names. */
data class MediaIdentity(
    val kind: Kind,
    val sha256: String,
) {
    enum class Kind(val marker: Char) {
        RSS('r'),
        URL('u'),
    }

    init {
        require(sha256.length == SHA_256_HEX_LENGTH && sha256.all { it in "0123456789abcdef" })
    }

    val suffix: String get() = "[vibe1-${kind.marker}-$sha256]"

    companion object {
        private const val SHA_256_HEX_LENGTH = 64
        private val SUFFIX_REGEX = Regex("\\[vibe1-([ru])-([0-9a-f]{64})](?: \\(\\d+\\))?$")

        fun rss(stableEpisodeId: String): MediaIdentity = from(Kind.RSS, stableEpisodeId)

        fun url(canonicalUrlMediaId: String): MediaIdentity = from(Kind.URL, canonicalUrlMediaId)

        fun parse(displayName: String): MediaIdentity? {
            val base = MediaNaming.baseName(displayName)
            val match = SUFFIX_REGEX.find(base) ?: return null
            val kind = when (match.groupValues[1]) {
                "r" -> Kind.RSS
                "u" -> Kind.URL
                else -> return null
            }
            return MediaIdentity(kind, match.groupValues[2])
        }

        private fun from(kind: Kind, source: String): MediaIdentity {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return MediaIdentity(kind, digest)
        }
    }
}
