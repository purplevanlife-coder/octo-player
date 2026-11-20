package app.winters.octo.ui.discover

import app.winters.octo.discover.DiscoverBatch
import app.winters.octo.discover.DiscoverSection
import app.winters.octo.discover.Dismissed
import app.winters.octo.discover.OwnedBatch
import app.winters.octo.discover.SectionKind
import app.winters.octo.discover.playlistKey
import app.winters.octo.discover.song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private val playlist = DiscoverSection(
        playlistKey("fresh", 3),
        SectionKind.Playlist,
        "Fresh finds",
        "Songs new to your library",
        songs = listOf(song("find:a", "A", "Band"), song("find:b", "B", "Band")),
    )

    @Test
    fun aPlaylistPageShowsTheBatchsCopyOrTheOneLastSeen() {
        val withIt = DiscoverBatch(listOf(playlist), generatedAt = 0, generation = 3)
        assertNull(playlistShown(null, withIt, playlist, Dismissed()))
        assertEquals(playlist, playlistShown(playlist.key, withIt, null, Dismissed()))
        // A new generation landed without it: the copy last seen stays, without what was turned down since.
        val next = DiscoverBatch(emptyList(), generatedAt = 1, generation = 4)
        val kept = playlistShown(playlist.key, next, playlist, Dismissed(songIds = setOf("find:a")))!!
        assertEquals(listOf("find:b"), kept.songs.map { it.id })
        // Nothing to show for a key no copy has (a page kept across a restart into another batch).
        assertNull(playlistShown(playlist.key, next, null, Dismissed()))
        // Only playlists open as one.
        assertNull(playlistShown("for-you", batch, null, Dismissed()))
    }

    @Test
    fun theSeededLineShowsOnlyWhenTheHistoryMadeAPlaylist() {
        assertNull(seededLine(DiscoverBatch(listOf(playlist), 0)))
        val history = playlist.copy(key = playlistKey("history", 0), title = "From your listening history")
        assertEquals("Seeded by your library and listening history", seededLine(DiscoverBatch(listOf(history, playlist), 0)))
    }
}
