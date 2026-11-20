package app.winters.octo.ui.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotInterestedTest {
    @Test
    fun onlyADiscoverPickOffersNotInterested() {
        assertFalse(SongAction.NotInterested in songActions(find = true, radio = true))
        assertFalse(SongAction.NotInterested in songActions(find = false, radio = true))
        assertTrue(SongAction.NotInterested in songActions(find = true, radio = true, notInterested = true))
        assertTrue(SongAction.NotInterested in songActions(find = false, radio = true, notInterested = true))
    }

    @Test
    fun itSitsWithWhatTakesASongAway() {
        val groups = songMenuGroups(songActions(find = true, radio = false, notInterested = true))
        assertEquals(listOf(SongAction.NotInterested), groups.last())
        // A context with no handler offers nothing new.
        assertEquals(null, SongMenuContext().onNotInterested)
    }
}
