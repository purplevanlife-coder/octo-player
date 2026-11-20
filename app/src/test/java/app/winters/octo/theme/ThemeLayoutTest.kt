package app.winters.octo.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

// Music Space alone lays out its own player and shell; every other theme
// keeps Octo's, so nothing about them moves.
class ThemeLayoutTest {
    private val others = ThemeChoice.entries.filter { it != ThemeChoice.MusicSpace }

    @Test
    fun theFirstFiveThemesKeepTheClassicShell() {
        for (theme in others) {
            val shell = shellLayoutFor(theme)
            assertSame(theme.name, ClassicShell, shell)
            assertEquals(theme.name, BarDock.Bottom, shell.dock)
            assertEquals(theme.name, false, shell.floatingNowPlaying)
            assertEquals(theme.name, false, shell.swipePages)
        }
    }

    @Test
    fun musicSpaceDocksTheBarAtTheTopFloatsTheBubbleAndSwipes() {
        val shell = shellLayoutFor(ThemeChoice.MusicSpace)
        assertSame(MusicSpaceShell, shell)
        assertEquals(BarDock.Top, shell.dock)
        assertEquals(true, shell.floatingNowPlaying)
        assertEquals(true, shell.swipePages)
    }

    @Test
    fun onlyMusicSpaceUprightWithASongGetsTheCirclePlayer() {
        for (theme in ThemeChoice.entries) {
            for (upright in listOf(true, false)) {
                for (live in listOf(true, false)) {
                    val expected = if (theme == ThemeChoice.MusicSpace && upright && !live) PlayerLayout.Circle else PlayerLayout.Upright
                    assertEquals("$theme upright=$upright live=$live", expected, playerLayoutFor(theme, upright, live))
                }
            }
        }
    }

    @Test
    fun sidewaysMusicSpaceKeepsTheSideBySidePlayer() {
        assertEquals(PlayerLayout.Upright, playerLayoutFor(ThemeChoice.MusicSpace, upright = false, live = false))
    }
}
