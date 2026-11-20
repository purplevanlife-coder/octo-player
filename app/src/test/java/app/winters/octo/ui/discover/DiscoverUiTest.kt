package app.winters.octo.ui.discover

import app.winters.octo.discover.DiscoverBatch
import app.winters.octo.discover.DiscoverSection
import app.winters.octo.discover.Dismissed
import app.winters.octo.discover.OwnedBatch
import app.winters.octo.discover.SectionKind
import app.winters.octo.discover.song
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverUiTest {
    private val batch = DiscoverBatch(
        listOf(DiscoverSection("for-you", SectionKind.ForYou, "For You", songs = listOf(song("find:a", "A", "Band"), song("find:b", "B", "Band")))),
        generatedAt = 0,
    )

    @Test
    fun whatTheTabShows() {
        assertEquals(DiscoverUi.SignedOut, discoverUi(null, OwnedBatch("me", batch), Dismissed(), making = false))
        // Kept for another server: not shown, while this one's is read.
        assertEquals(DiscoverUi.Reading, discoverUi("me", OwnedBatch("other", batch), Dismissed(), making = false))
        assertEquals(DiscoverUi.Reading, discoverUi("me", null, Dismissed(), making = false))
        assertEquals(DiscoverUi.NoBatch(making = true), discoverUi("me", OwnedBatch("me", null), Dismissed(), making = true))
        assertEquals(DiscoverUi.Ready(batch, making = false), discoverUi("me", OwnedBatch("me", batch), Dismissed(), making = false))
    }

    @Test
    fun aPickTurnedDownSinceTheBatchWasMadeIsNotShown() {
        val shown = discoverUi("me", OwnedBatch("me", batch), Dismissed(songIds = setOf("find:a")), making = false) as DiscoverUi.Ready
        assertEquals(listOf("find:b"), shown.batch.sections.single().songs.map { it.id })
    }

    @Test
    fun theUpdatedLineSaysHowLongAgo() {
        val minute = 60_000L
        assertEquals("Updated just now", updatedLine(0, 30_000))
        assertEquals("Updated 5 min ago", updatedLine(0, 5 * minute))
        assertEquals("Updated 3 h ago", updatedLine(0, 3 * 60 * minute + 10 * minute))
        assertEquals("Updated yesterday", updatedLine(0, 30 * 60 * minute))
        assertEquals("Updated 4 days ago", updatedLine(0, 4 * 24 * 60 * minute))
        // A clock turned back reads as just now, not as the future.
        assertEquals("Updated just now", updatedLine(10 * minute, 0))
    }
}
