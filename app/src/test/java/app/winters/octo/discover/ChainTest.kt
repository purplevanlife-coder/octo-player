package app.winters.octo.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainTest {
    @Test
    fun placesFurtherDownCountLessAndSeveralSeedsAddUp() {
        val scored = scoreChain(
            listOf(
                ChainLink(listOf(outside("First"), outside("Shared")), weight = 10.0, via = "Seed A"),
                ChainLink(listOf(outside("Shared")), weight = 3.0, via = "Seed B"),
            ),
        )
        assertEquals(10.0, scored.getValue(artistKey("First")).score, 1e-9)
        // Second on A's list (10 / 1.2), first on B's (3).
        assertEquals(10.0 / 1.2 + 3.0, scored.getValue(artistKey("Shared")).score, 1e-9)
        // The seed that gave it the most is the one it is shown via.
        assertEquals("Seed A", scored.getValue(artistKey("Shared")).via)
    }

    @Test
    fun theLibrarysCopyOfAnArtistWins() {
        val scored = scoreChain(
            listOf(
                ChainLink(listOf(outside("Both")), 1.0, "A"),
                ChainLink(listOf(inLibrary("Both")), 1.0, "B"),
            ),
        )
        assertEquals("lib-Both", scored.getValue(artistKey("Both")).artist.libraryId)
    }

    @Test
    fun artistsOutsideTheLibraryComeFirstAndLibraryOnesOnlyFill() {
        val scored = scoreChain(
            listOf(ChainLink(listOf(inLibrary("Owned big"), outside("New 1"), outside("New 2"), inLibrary("Owned small")), 10.0, "Seed")),
        )
        // Plenty found outside: no library artist at all.
        val plenty = rankChain(scored, emptySet(), Dismissed(), fill = 2)
        assertEquals(listOf("New 1", "New 2"), plenty.map { it.artist.name })
        // Too few outside: library artists fill the row, after them, best first.
        val few = rankChain(scored, emptySet(), Dismissed(), fill = 3)
        assertEquals(listOf("New 1", "New 2", "Owned big"), few.map { it.artist.name })
    }

    @Test
    fun seedsAndDismissedArtistsNeverCome() {
        val scored = scoreChain(
            listOf(ChainLink(listOf(outside("The Seed"), outside("Turned Down"), outside("Kept")), 5.0, "Seed")),
        )
        val ranked = rankChain(
            scored,
            seedKeys = setOf(artistKey("The Seed")),
            dismissed = Dismissed(artistKeys = setOf(artistKey("turned down"))),
            fill = 0,
        )
        assertEquals(listOf("Kept"), ranked.map { it.artist.name })
    }

    @Test
    fun theSecondHopStartsFromTheBestOutsideTheLibrary() {
        val scored = scoreChain(
            listOf(
                ChainLink(listOf(inLibrary("Owned"), outside("Best"), outside("Seed Two"), outside("Next"), outside("Gone"), outside("Last")), 10.0, "Seed One"),
            ),
        )
        val from = secondHopFrom(scored, setOf(artistKey("Seed Two")), Dismissed(artistKeys = setOf(artistKey("Gone"))), count = 2)
        assertEquals(listOf("Best", "Next"), from.map { it.artist.name })
        assertTrue(from.none { it.inLibrary })
    }

    @Test
    fun noOneInParticularIsNeverAPick() {
        val scored = scoreChain(listOf(ChainLink(listOf(outside("Various Artists"), outside("Real")), 1.0, "Seed")))
        assertEquals(listOf("Real"), scored.values.map { it.artist.name })
    }
}
