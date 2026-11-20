package app.winters.octo.ui.nav

import kotlin.math.abs

// How Music Space's swipeable tab pages and the bar stay in step. Pager page
// n is tab n, in TabOrder's order. `selected` (MainShell's) stays the one
// truth for which tab is in front: a tap on the bar, Back, a family link or
// a search asked for elsewhere all set it, and the pager glides to it; a
// swipe that settles on a page sets it to that page.
object PagerSync {
    // The tab a pager page is, held to the tabs there are.
    fun tabOf(page: Int): Int = page.coerceIn(0, TabOrder.count - 1)

    // The tab the bar marks. While a finger is turning the pages, the pill
    // follows the page most in view; otherwise (at rest, or gliding to a
    // tapped tab) it is the selected tab, so a tap's pill goes straight to
    // where it was tapped rather than stopping at each tab on the way.
    fun barTab(selected: Int, currentPage: Int, fingerTurning: Boolean): Int =
        if (fingerTurning) tabOf(currentPage) else selected

    // Whether the pages are being turned by a finger (a drag or the fling
    // after it), as opposed to gliding after a tap.
    fun fingerTurning(scrolling: Boolean, gliding: Boolean): Boolean = scrolling && !gliding

    // The tab a page that the pages came to rest on makes selected: that
    // page's, unless the rest came at the end of a glide (then it is the
    // tab the glide went to, which is already selected).
    fun selectedAfterRest(selected: Int, settledPage: Int, gliding: Boolean): Int =
        if (gliding) selected else tabOf(settledPage)

    // Whether the pages must glide to the selected tab.
    fun mustGlide(selected: Int, currentPage: Int): Boolean = tabOf(currentPage) != selected

    // Whether page `page` draws its tab's pages. The tab in front always
    // does; while a finger turns the pages, so do the page most in view and
    // its neighbours, which come into view (a finger that catches a glide
    // part way finds the pages under it drawn). Gliding past tabs after a
    // tap, the ones in between show only the page's ground, so a glide
    // across the bar stays smooth and builds nothing it only passes.
    fun pageShowsContent(page: Int, selected: Int, currentPage: Int, fingerTurning: Boolean): Boolean =
        page == selected || (fingerTurning && abs(page - currentPage) <= 1)
}
