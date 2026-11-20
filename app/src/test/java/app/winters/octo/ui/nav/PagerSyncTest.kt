package app.winters.octo.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Music Space's swiped pages and the bar agree on which tab is in front,
// whichever of them moved.
class PagerSyncTest {
    @Test
    fun pagerPagesAreTabsInTheBarsOrder() {
        for (tab in 0 until TabOrder.count) assertEquals(tab, PagerSync.tabOf(tab))
        assertEquals(TabOrder.HOME, PagerSync.tabOf(-1))
        assertEquals(TabOrder.SETTINGS, PagerSync.tabOf(TabOrder.count + 3))
    }

    @Test
    fun aSwipeMovesThePillWithThePageAndSelectsWhereItRests() {
        // Home in front, a finger pulls Discover half into view.
        val turning = PagerSync.fingerTurning(scrolling = true, gliding = false)
        assertTrue(turning)
        assertEquals(TabOrder.DISCOVER, PagerSync.barTab(TabOrder.HOME, currentPage = TabOrder.DISCOVER, fingerTurning = turning))
        // Let go on Discover: Discover is selected.
        assertEquals(TabOrder.DISCOVER, PagerSync.selectedAfterRest(TabOrder.HOME, settledPage = TabOrder.DISCOVER, gliding = false))
        // A swipe that springs back leaves Home selected.
        assertEquals(TabOrder.HOME, PagerSync.selectedAfterRest(TabOrder.HOME, settledPage = TabOrder.HOME, gliding = false))
    }

    @Test
    fun aTapGlidesThePagesWhileThePillGoesStraightThere() {
        // Settings tapped from Home: selected first, then the pages glide.
        assertTrue(PagerSync.mustGlide(selected = TabOrder.SETTINGS, currentPage = TabOrder.HOME))
        val turning = PagerSync.fingerTurning(scrolling = true, gliding = true)
        assertFalse(turning)
        // Passing Search on the way, the pill stays on Settings.
        assertEquals(TabOrder.SETTINGS, PagerSync.barTab(TabOrder.SETTINGS, currentPage = TabOrder.SEARCH, fingerTurning = turning))
        // Arrived: nothing more to glide, and the rest keeps Settings.
        assertFalse(PagerSync.mustGlide(selected = TabOrder.SETTINGS, currentPage = TabOrder.SETTINGS))
        assertEquals(TabOrder.SETTINGS, PagerSync.selectedAfterRest(TabOrder.SETTINGS, settledPage = TabOrder.SETTINGS, gliding = true))
    }

    @Test
    fun atRestThePillIsTheSelectedTab() {
        assertEquals(TabOrder.LIBRARY, PagerSync.barTab(TabOrder.LIBRARY, currentPage = TabOrder.LIBRARY, fingerTurning = false))
        assertFalse(PagerSync.fingerTurning(scrolling = false, gliding = false))
        assertFalse(PagerSync.fingerTurning(scrolling = false, gliding = true))
    }

    @Test
    fun onlyTheFrontTabAndTheNeighboursAFingerRevealsDrawTheirPages() {
        // At rest, or gliding: only the selected tab's pages are drawn, even
        // for the tabs a glide passes.
        for (page in 0 until TabOrder.count) {
            assertEquals(page == TabOrder.SEARCH, PagerSync.pageShowsContent(page, TabOrder.SEARCH, currentPage = TabOrder.SEARCH, fingerTurning = false))
            assertEquals(page == TabOrder.SETTINGS, PagerSync.pageShowsContent(page, TabOrder.SETTINGS, currentPage = TabOrder.DISCOVER, fingerTurning = false))
        }
        // A finger turning from Search: Discover and Library come into view.
        assertTrue(PagerSync.pageShowsContent(TabOrder.DISCOVER, TabOrder.SEARCH, TabOrder.SEARCH, fingerTurning = true))
        assertTrue(PagerSync.pageShowsContent(TabOrder.LIBRARY, TabOrder.SEARCH, TabOrder.SEARCH, fingerTurning = true))
        assertTrue(PagerSync.pageShowsContent(TabOrder.SEARCH, TabOrder.SEARCH, TabOrder.SEARCH, fingerTurning = true))
        assertFalse(PagerSync.pageShowsContent(TabOrder.HOME, TabOrder.SEARCH, TabOrder.SEARCH, fingerTurning = true))
        assertFalse(PagerSync.pageShowsContent(TabOrder.SETTINGS, TabOrder.SEARCH, TabOrder.SEARCH, fingerTurning = true))
    }

    // Settings tapped from Home, and a finger catches the glide over
    // Discover: the pages under the finger are drawn, not blank.
    @Test
    fun aFingerThatCatchesAGlideFindsThePagesUnderItDrawn() {
        assertTrue(PagerSync.pageShowsContent(TabOrder.DISCOVER, TabOrder.SETTINGS, currentPage = TabOrder.DISCOVER, fingerTurning = true))
        assertTrue(PagerSync.pageShowsContent(TabOrder.HOME, TabOrder.SETTINGS, currentPage = TabOrder.DISCOVER, fingerTurning = true))
        assertTrue(PagerSync.pageShowsContent(TabOrder.SEARCH, TabOrder.SETTINGS, currentPage = TabOrder.DISCOVER, fingerTurning = true))
        assertFalse(PagerSync.pageShowsContent(TabOrder.LIBRARY, TabOrder.SETTINGS, currentPage = TabOrder.DISCOVER, fingerTurning = true))
    }
}
