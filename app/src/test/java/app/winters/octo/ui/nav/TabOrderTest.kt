package app.winters.octo.ui.nav

import androidx.compose.ui.unit.dp
import app.winters.octo.ambient.AmbientArea
import app.winters.octo.ambient.areaOf
import org.junit.Assert.assertEquals
import org.junit.Test

class TabOrderTest {
    @Test
    fun theTabsAreHomeDiscoverSearchLibrarySettings() {
        assertEquals(listOf(HomeRoute, DiscoverRoute, SearchRoute, LibraryRoute, SettingsRoute), TabOrder.roots)
        assertEquals(HomeRoute, TabOrder.roots[TabOrder.HOME])
        assertEquals(DiscoverRoute, TabOrder.roots[TabOrder.DISCOVER])
        assertEquals(SearchRoute, TabOrder.roots[TabOrder.SEARCH])
        assertEquals(LibraryRoute, TabOrder.roots[TabOrder.LIBRARY])
        // Sign-ins, family links and imports go to the last tab: Settings.
        assertEquals(SettingsRoute, TabOrder.roots.last())
        assertEquals(TabOrder.SETTINGS, TabOrder.count - 1)
        // Back from another tab's root lands on the first: Home.
        assertEquals(0, TabOrder.HOME)
    }

    @Test
    fun theSelectedPillNeverOutgrowsItsTab() {
        // Five tabs on a 412dp-wide phone: (412 - 40 - 56 - 10 - 12) / 5.
        val pixel = (412.dp - 40.dp - BarHeight - 10.dp - 12.dp) / 5
        assertEquals(pixel, tabCapsuleWidth(pixel))
        // A wide bar keeps the pill at its own 62dp.
        assertEquals(62.dp, tabCapsuleWidth(90.dp))
        // A narrow phone's column is the most it takes.
        assertEquals(48.dp, tabCapsuleWidth(48.dp))
    }

    @Test
    fun discoverGlowsAsHomeDoes() {
        assertEquals(AmbientArea.Home, areaOf(DiscoverRoute))
    }
}
