package app.winters.octo.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DismissalsTest {
    private val keep = song("find:keep", "Keep", "Kept Band")
    private val drop = song("find:drop", "Drop", "Kept Band")
    private val byDropped = song("find:other", "Other", "Dropped Band")

    private val batch = DiscoverBatch(
        listOf(
            DiscoverSection("for-you", SectionKind.ForYou, "For You", songs = listOf(keep, drop, byDropped)),
            DiscoverSection("lane:Rock", SectionKind.Lane, "Rock", songs = listOf(drop)),
            DiscoverSection(
                "artists",
                SectionKind.Artists,
                "Artists to explore",
                artists = listOf(DiscoverArtist("Dropped Band", null, null, "s1"), DiscoverArtist("Kept Band", null, null, "s2")),
            ),
        ),
        generatedAt = 1_000,
    )

    @Test
    fun rowsReadBackAsWhatWasTurnedDown() {
        val rows = listOf(
            DiscoverDismissedEntity("o", DismissKind.SONG, "find:drop", "Drop", 1),
            DiscoverDismissedEntity("o", DismissKind.SONG_KEY, songKey(drop), "Drop", 1),
            DiscoverDismissedEntity("o", DismissKind.ARTIST, artistKey("Dropped Band"), "Dropped Band", 1),
        )
        val dismissed = dismissedOf(rows)
        assertEquals(setOf("find:drop"), dismissed.songIds)
        assertEquals(setOf(songKey(drop)), dismissed.songKeys)
        assertEquals(setOf(artistKey("Dropped Band")), dismissed.artistKeys)
        assertTrue(dismissedOf(emptyList()).isEmpty)
    }

    @Test
    fun aTurnedDownPickLeavesEverySectionAtOnce() {
        val shown = batch.without(Dismissed(songIds = setOf("find:drop"), artistKeys = setOf(artistKey("Dropped Band"))))
        assertEquals(listOf("find:keep"), shown.sections.single { it.key == "for-you" }.songs.map { it.id })
        // The lane held only the dropped song, so it goes.
        assertFalse(shown.sections.any { it.key == "lane:Rock" })
        assertEquals(listOf("Kept Band"), shown.sections.single { it.key == "artists" }.artists.map { it.name })
        assertEquals(batch.generatedAt, shown.generatedAt)
    }

    @Test
    fun nothingTurnedDownLeavesTheBatchAsItIs() {
        assertSame(batch, batch.without(Dismissed()))
    }

    @Test
    fun aDismissalMatchesByIdByNameOrByArtist() {
        assertTrue(Dismissed(songIds = setOf("find:drop")).has(drop))
        assertTrue(Dismissed(songKeys = setOf(songKey("drop", "KEPT BAND"))).has(drop))
        assertTrue(Dismissed(artistKeys = setOf(artistKey("Kept Band"))).has(keep))
        assertFalse(Dismissed(songIds = setOf("find:drop")).has(keep))
    }

    @Test
    fun aBatchIsStaleAfterItsWindowOrFromTheFuture() {
        val made = 10_000_000L
        assertFalse(isStale(made, made + DISCOVER_FRESH_MS - 1))
        assertTrue(isStale(made, made + DISCOVER_FRESH_MS))
        // A clock turned back: made again rather than trusted for ever.
        assertTrue(isStale(made, made - 1))
    }
}
