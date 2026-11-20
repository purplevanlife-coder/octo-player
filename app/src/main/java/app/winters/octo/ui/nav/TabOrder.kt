package app.winters.octo.ui.nav

import androidx.navigation3.runtime.NavKey

// The tabs, in the order the bar draws them and MainShell keeps their back
// stacks: Home · Discover · Search · Library · Settings. Every place that
// picks a tab by position reads it from here, so adding or moving a tab is
// one change. Settings stays last (MainShell routes sign-ins, family links
// and imports to `stacks.last()`); Home stays first (Back from another
// tab's root lands there).
object TabOrder {
    const val HOME = 0
    const val DISCOVER = 1
    const val SEARCH = 2
    const val LIBRARY = 3
    const val SETTINGS = 4

    // Each tab's root page, by index.
    val roots: List<NavKey> = listOf(HomeRoute, DiscoverRoute, SearchRoute, LibraryRoute, SettingsRoute)

    val count: Int get() = roots.size
}
