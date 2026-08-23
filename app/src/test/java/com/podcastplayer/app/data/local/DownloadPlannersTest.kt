package com.podcastplayer.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPlannersTest {

    @Test
    fun `restore uses exact identity and leaves legacy match for confirmation`() {
        val exactIdentity = MediaIdentity.rss("exact")
        val episodes = listOf(
            RestoreEpisodeCandidate("exact", exactIdentity, "show - repeated"),
            RestoreEpisodeCandidate("legacy", MediaIdentity.rss("legacy"), "show - legacy"),
        )
        val exact = file("exact", "Repeated${exactIdentity.suffix}.mp3")
        val legacy = file("legacy", "Show - Legacy.mp3")

        val plan = RestorePlanner.plan(episodes, listOf(exact, legacy))

        assertEquals(exact, plan.exactMatches["exact"])
        assertEquals(listOf("legacy"), plan.legacySuggestions.map { it.episodeId })
        assertTrue(plan.unidentified.isEmpty())
    }

    @Test
    fun `repeated legacy titles are ambiguous and never suggested`() {
        val episodes = listOf(
            RestoreEpisodeCandidate("one", MediaIdentity.rss("one"), "show - update"),
            RestoreEpisodeCandidate("two", MediaIdentity.rss("two"), "show - update"),
        )
        val legacy = file("legacy", "Show - Update.mp3")

        val plan = RestorePlanner.plan(episodes, listOf(legacy))

        assertTrue(plan.legacySuggestions.isEmpty())
        assertEquals(listOf(legacy), plan.unidentified)
    }

    @Test
    fun `stable ID duplicates are confirmed and protected references are disabled`() {
        val identity = MediaIdentity.rss("same")
        val referenced = file("ref", "Episode${identity.suffix}.mp3", size = 50, protected = true)
        val duplicate = file("dup", "Episode${identity.suffix} (1).mp3", size = 100)

        val plan = DuplicateCleanupPlanner.plan(listOf(referenced, duplicate))

        assertEquals(1, plan.confirmed.size)
        assertFalse(plan.confirmed.single().items.first { it.file == referenced }.enabled)
        assertTrue(duplicate.uriString in plan.defaultDeleteUris)
    }

    @Test
    fun `byte identical files with different titles are confirmed`() {
        val first = file("one", "One.mp3", hash = "abc")
        val second = file("two", "Two.mp3", hash = "abc")

        val group = DuplicateCleanupPlanner.plan(listOf(first, second)).confirmed.single()

        assertEquals(ConfirmedDuplicateGroup.Reason.IDENTICAL_CONTENT, group.reason)
        assertEquals(1, group.items.count { it.selectedByDefault })
    }

    @Test
    fun `same legacy title with different content is ambiguous and unselected`() {
        val first = file("one", "Episode.mp3", size = 100, hash = "aaa")
        val second = file("two", "Episode (1).mp3", size = 100, hash = "bbb")

        val plan = DuplicateCleanupPlanner.plan(listOf(first, second))

        assertTrue(plan.confirmed.isEmpty())
        assertEquals(1, plan.ambiguous.size)
        assertTrue(plan.ambiguous.single().items.none { it.selectedByDefault })
    }

    @Test
    fun `ambiguous selection always leaves one item available`() {
        val plan = DuplicateCleanupPlanner.plan(
            listOf(
                file("one", "Episode.mp3", size = 100, hash = "aaa"),
                file("two", "Episode (1).mp3", size = 100, hash = "bbb"),
                file("three", "Episode (2).mp3", size = 100, hash = "ccc"),
            ),
        )
        val group = plan.ambiguous.single()

        assertTrue(group.canToggleDeletion("one", emptySet()))
        assertTrue(group.canToggleDeletion("two", setOf("one")))
        assertFalse(group.canToggleDeletion("three", setOf("one", "two")))
        assertTrue(group.canToggleDeletion("one", setOf("one", "two")))
        assertTrue(group.canToggleDeletion("three", setOf("two")))
    }

    @Test
    fun `protected ambiguous item allows every unprotected item to be selected`() {
        val plan = DuplicateCleanupPlanner.plan(
            listOf(
                file("protected", "Episode.mp3", size = 90, hash = "aaa", protected = true),
                file("one", "Episode (1).mp3", size = 100, hash = "bbb"),
                file("two", "Episode (2).mp3", size = 110, hash = "ccc"),
            ),
        )
        val group = plan.ambiguous.single()

        assertFalse(group.canToggleDeletion("protected", emptySet()))
        assertTrue(group.canToggleDeletion("one", emptySet()))
        assertTrue(group.canToggleDeletion("two", setOf("one")))
        assertEquals(listOf("one", "two"), plan.sanitizeDeleteUris(listOf("protected", "one", "two")))
    }

    @Test
    fun `sanitization deterministically keeps one ambiguous file`() {
        val small = file("small", "Episode.mp3", size = 100, hash = "aaa")
        val large = file("large", "Episode (1).mp3", size = 200, hash = "bbb")
        val plan = DuplicateCleanupPlanner.plan(listOf(small, large))

        assertEquals(listOf("small"), plan.sanitizeDeleteUris(listOf("small", "large")))
    }

    @Test
    fun `sanitization rejects protected kept and unknown files`() {
        val identity = MediaIdentity.rss("same")
        val referenced = file("ref", "Episode${identity.suffix}.mp3", protected = true)
        val duplicate = file("dup", "Episode${identity.suffix} (1).mp3")
        val plan = DuplicateCleanupPlanner.plan(listOf(referenced, duplicate))

        assertEquals(listOf("dup"), plan.sanitizeDeleteUris(listOf("ref", "dup", "unknown")))
    }

    @Test
    fun `keep selection prefers largest then unsuffixed then oldest`() {
        val identity = MediaIdentity.rss("same")
        val small = file("small", "Ep${identity.suffix}.mp3", size = 50, date = 1)
        val suffixed = file("suffix", "Ep${identity.suffix} (1).mp3", size = 100, date = 1)
        val old = file("old", "Ep${identity.suffix}.mp3", size = 100, date = 1)
        val new = file("new", "Ep${identity.suffix}.mp3", size = 100, date = 2)

        val keep = DuplicateCleanupPlanner.plan(listOf(small, suffixed, new, old)).confirmed.single().keep

        assertEquals(old, keep)
    }

    @Test
    fun `retention is per podcast ordered by publication and preserves pinned`() {
        val items = listOf(
            retention("pinned", 1, pinned = true),
            retention("new", 30),
            retention("middle", 20, source = RetentionCandidate.Source.URL),
            retention("old", 10),
        )

        val prune = AutoDownloadRetentionPlanner.itemsToPrune(items, limit = 2)

        assertEquals(listOf("old"), prune.map { it.id })
        assertFalse(prune.any { it.id == "pinned" })
    }

    @Test
    fun `unlimited retention prunes nothing and eligible selection is newest first`() {
        val items = listOf(retention("one", 1), retention("two", 2))
        assertTrue(AutoDownloadRetentionPlanner.itemsToPrune(items, AutoDownloadRetentionPlanner.UNLIMITED).isEmpty())

        val selected = AutoDownloadRetentionPlanner.selectEligibleEpisodes(
            listOf(
                RestoreEpisodeCandidateForRetention("old", 1),
                RestoreEpisodeCandidateForRetention("new", 2),
            ),
            limit = 1,
        )
        assertEquals(listOf("new"), selected.map { it.id })
    }

    private fun file(
        uri: String,
        name: String,
        size: Long = 100,
        date: Long = 1,
        hash: String? = null,
        protected: Boolean = false,
    ) = MediaFileCandidate(uri, name, size, date, false, sha256 = hash, isProtected = protected)

    private fun retention(
        id: String,
        publication: Long,
        pinned: Boolean = false,
        source: RetentionCandidate.Source = RetentionCandidate.Source.RSS,
    ) = RetentionCandidate(id, "podcast", publication, publication, pinned, source)
}
