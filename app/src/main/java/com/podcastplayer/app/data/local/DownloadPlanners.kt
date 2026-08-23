package com.podcastplayer.app.data.local

/** JVM-safe representation of a scanned shared-media item. */
data class MediaFileCandidate(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val isVideo: Boolean,
    val identity: MediaIdentity? = MediaIdentity.parse(displayName),
    val sha256: String? = null,
    val isProtected: Boolean = false,
)

data class RestoreEpisodeCandidate(
    val episodeId: String,
    val identity: MediaIdentity,
    val legacyMatchKey: String,
)

data class LegacyRestoreSuggestion(
    val episodeId: String,
    val file: MediaFileCandidate,
)

data class RestorePlan(
    val exactMatches: Map<String, MediaFileCandidate>,
    val legacySuggestions: List<LegacyRestoreSuggestion>,
    val unidentified: List<MediaFileCandidate>,
)

object RestorePlanner {
    fun plan(
        episodes: List<RestoreEpisodeCandidate>,
        files: List<MediaFileCandidate>,
    ): RestorePlan {
        val available = files.filter { it.sizeBytes > 0L }.toMutableList()
        val exact = linkedMapOf<String, MediaFileCandidate>()

        episodes.forEach { episode ->
            val matching = available.filter { it.identity == episode.identity }
            val pick = matching.maxWithOrNull(stableKeepComparator()) ?: return@forEach
            exact[episode.episodeId] = pick
            available.removeAll(matching.toSet())
        }

        val suggestions = mutableListOf<LegacyRestoreSuggestion>()
        episodes.filterNot { it.episodeId in exact }.forEach { episode ->
            val legacy = available.filter {
                it.identity == null && !it.isVideo && MediaNaming.matchKey(it.displayName) == episode.legacyMatchKey
            }
            // Repeated titles or multiple legacy copies are ambiguous: a suggestion is
            // only made when both sides resolve one-to-one.
            if (legacy.size == 1 && episodes.count { it.legacyMatchKey == episode.legacyMatchKey } == 1) {
                suggestions += LegacyRestoreSuggestion(episode.episodeId, legacy.single())
                available.remove(legacy.single())
            }
        }

        return RestorePlan(exact, suggestions, available)
    }
}

data class DuplicateReviewItem(
    val file: MediaFileCandidate,
    val selectedByDefault: Boolean,
    val enabled: Boolean = !file.isProtected,
)

data class ConfirmedDuplicateGroup(
    val reason: Reason,
    val keep: MediaFileCandidate,
    val items: List<DuplicateReviewItem>,
) {
    enum class Reason { STABLE_ID, IDENTICAL_CONTENT }
}

data class AmbiguousLegacyGroup(
    val normalizedTitle: String,
    val items: List<DuplicateReviewItem>,
) {
    /**
     * Whether [uriString] may be toggled in the delete selection without allowing
     * every physical file in this ambiguous group to be selected at once.
     * Selected items remain toggleable so the user can always deselect them.
     */
    fun canToggleDeletion(uriString: String, selectedUris: Set<String>): Boolean {
        val item = items.firstOrNull { it.file.uriString == uriString } ?: return false
        if (!item.enabled) return false
        if (uriString in selectedUris) return true
        return items.any { other ->
            other.file.uriString != uriString && other.file.uriString !in selectedUris
        }
    }
}

data class DuplicateCleanupPlan(
    val confirmed: List<ConfirmedDuplicateGroup>,
    val ambiguous: List<AmbiguousLegacyGroup>,
) {
    val defaultDeleteUris: List<String>
        get() = confirmed.flatMap { group ->
            group.items.filter { it.selectedByDefault && it.enabled }.map { it.file.uriString }
        }

    /**
     * Restrict a requested delete selection to eligible plan items and always
     * retain at least one physical file from every ambiguous group.
     */
    fun sanitizeDeleteUris(requestedUris: Collection<String>): List<String> {
        val requested = requestedUris.toSet()
        val allItems = confirmed.flatMap { it.items } + ambiguous.flatMap { it.items }
        val selected = allItems.asSequence()
            .filter { it.enabled && it.file.uriString in requested }
            .mapTo(linkedSetOf()) { it.file.uriString }

        ambiguous.forEach { group ->
            if (group.items.isNotEmpty() && group.items.all { it.file.uriString in selected }) {
                val keep = group.items.map { it.file }.maxWithOrNull(stableKeepComparator())
                keep?.let { selected.remove(it.uriString) }
            }
        }
        return selected.toList()
    }
}

object DuplicateCleanupPlanner {
    fun plan(files: List<MediaFileCandidate>): DuplicateCleanupPlan {
        val valid = files.filter { it.sizeBytes > 0L }
        val parent = IntArray(valid.size) { it }
        val reasonByRoot = mutableMapOf<Int, ConfirmedDuplicateGroup.Reason>()

        fun root(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }

        fun union(a: Int, b: Int, reason: ConfirmedDuplicateGroup.Reason) {
            val ra = root(a)
            val rb = root(b)
            if (ra == rb) return
            parent[rb] = ra
            val existing = reasonByRoot.remove(rb) ?: reasonByRoot[ra]
            reasonByRoot[ra] = if (existing == ConfirmedDuplicateGroup.Reason.STABLE_ID ||
                reason == ConfirmedDuplicateGroup.Reason.STABLE_ID
            ) ConfirmedDuplicateGroup.Reason.STABLE_ID else reason
        }

        valid.indices.forEach { a ->
            for (b in a + 1 until valid.size) {
                val left = valid[a]
                val right = valid[b]
                when {
                    left.identity != null && left.identity == right.identity ->
                        union(a, b, ConfirmedDuplicateGroup.Reason.STABLE_ID)
                    left.sizeBytes == right.sizeBytes && left.sha256 != null && left.sha256 == right.sha256 ->
                        union(a, b, ConfirmedDuplicateGroup.Reason.IDENTICAL_CONTENT)
                }
            }
        }

        val confirmed = valid.indices.groupBy(::root).values
            .filter { it.size > 1 }
            .map { indices ->
                val members = indices.map(valid::get)
                val keep = members.maxWithOrNull(stableKeepComparator())!!
                val protectedExist = members.any { it.isProtected }
                val items = members.map { file ->
                    DuplicateReviewItem(
                        file = file,
                        selectedByDefault = !file.isProtected && file != keep &&
                            (!protectedExist || file.isProtected.not()),
                        enabled = !file.isProtected && file != keep,
                    )
                }
                ConfirmedDuplicateGroup(
                    reason = reasonByRoot[root(indices.first())]
                        ?: ConfirmedDuplicateGroup.Reason.IDENTICAL_CONTENT,
                    keep = keep,
                    items = items,
                )
            }

        val claimedUris = confirmed.flatMap { it.items }.map { it.file.uriString }.toSet()
        val ambiguous = valid.filter { it.uriString !in claimedUris && it.identity == null }
            .groupBy { it.isVideo to MediaNaming.matchKey(it.displayName) }
            .values
            .filter { group -> group.size > 1 && group.map { it.sha256 to it.sizeBytes }.distinct().size > 1 }
            .map { group ->
                AmbiguousLegacyGroup(
                    normalizedTitle = MediaNaming.matchKey(group.first().displayName),
                    items = group.map { DuplicateReviewItem(it, selectedByDefault = false) },
                )
            }

        return DuplicateCleanupPlan(confirmed, ambiguous)
    }
}

/** Prefer protected references, then the largest valid copy, un-suffixed, then oldest. */
private fun stableKeepComparator(): Comparator<MediaFileCandidate> = compareBy<MediaFileCandidate>(
    { it.isProtected },
    { it.sizeBytes },
    { !MediaNaming.hasDuplicateSuffix(it.displayName) },
    { -it.dateAddedSec },
    { it.uriString },
)

data class RetentionCandidate(
    val id: String,
    val podcastId: String,
    val publicationTimeMs: Long?,
    val completedTimeMs: Long,
    val isPinned: Boolean,
    val source: Source,
) {
    enum class Source { RSS, URL }
}

object AutoDownloadRetentionPlanner {
    const val UNLIMITED = -1

    fun selectEligibleEpisodes(
        episodes: List<RestoreEpisodeCandidateForRetention>,
        limit: Int,
    ): List<RestoreEpisodeCandidateForRetention> {
        val ordered = episodes.sortedWith(
            compareByDescending<RestoreEpisodeCandidateForRetention> { it.publicationTimeMs }
                .thenBy { it.id },
        )
        return if (limit == UNLIMITED) ordered else ordered.take(limit.coerceAtLeast(0))
    }

    fun itemsToPrune(items: List<RetentionCandidate>, limit: Int): List<RetentionCandidate> {
        if (limit == UNLIMITED) return emptyList()
        return items.filterNot { it.isPinned }
            .sortedWith(
                compareByDescending<RetentionCandidate> { it.publicationTimeMs ?: Long.MIN_VALUE }
                    .thenByDescending { it.completedTimeMs }
                    .thenBy { it.id },
            )
            .drop(limit.coerceAtLeast(0))
    }
}

data class RestoreEpisodeCandidateForRetention(
    val id: String,
    val publicationTimeMs: Long,
)
