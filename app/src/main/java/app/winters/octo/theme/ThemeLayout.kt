package app.winters.octo.theme

// What the shell and the full player lay out differently per theme. Only
// Music Space has its own arrangement (the reference's player cards and its
// top-docked bar); every other theme gets the layout Octo has always had,
// so their screens stay exactly as they were. The composables read the
// active theme through LocalActiveTheme and ask these, the same seam the
// type (fontsFor) and glass (glassFor) tokens use.

// The full player's arrangement: the square artwork over its blocks, or
// Music Space's circular artwork with the progress as a ring around it.
enum class PlayerLayout { Upright, Circle }

// Where the tab bar sits.
enum class BarDock { Bottom, Top }

// The shell's arrangement: where the tab bar docks, whether the now-playing
// button floats free of the bar as a bubble, and whether the five tab pages
// turn by a sideways swipe as well as by the bar.
data class ShellLayout(
    val dock: BarDock,
    val floatingNowPlaying: Boolean,
    val swipePages: Boolean,
)

// Octo's own shell: the bar at the bottom with its round now-playing button,
// tabs changed by the bar only.
val ClassicShell = ShellLayout(dock = BarDock.Bottom, floatingNowPlaying = false, swipePages = false)

// Music Space: the tab bar at the top, the now-playing bubble free to move,
// and the pages turned by a swipe.
val MusicSpaceShell = ShellLayout(dock = BarDock.Top, floatingNowPlaying = true, swipePages = true)

fun shellLayoutFor(theme: ThemeChoice): ShellLayout = if (theme == ThemeChoice.MusicSpace) MusicSpaceShell else ClassicShell

// The circular player is for a song on a phone held upright under Music
// Space. Sideways (and on a wide fold), the existing side-by-side player
// stays. A live station has no length to ring round, so it keeps the
// existing player and its live bar too.
fun playerLayoutFor(theme: ThemeChoice, upright: Boolean, live: Boolean): PlayerLayout =
    if (theme == ThemeChoice.MusicSpace && upright && !live) PlayerLayout.Circle else PlayerLayout.Upright
