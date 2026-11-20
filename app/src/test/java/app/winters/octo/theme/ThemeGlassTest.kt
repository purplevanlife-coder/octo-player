package app.winters.octo.theme

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.winters.octo.design.BarLook
import app.winters.octo.design.GlassDrop
import app.winters.octo.design.GlazeFrost
import app.winters.octo.design.GlazeSaturation
import app.winters.octo.design.GlazeTint
import app.winters.octo.design.LocalOctoGlass
import app.winters.octo.design.MusicSpaceGlass
import app.winters.octo.design.OctoDefaultGlass
import app.winters.octo.design.OctoGlass
import app.winters.octo.design.OverArt
import app.winters.octo.design.PlayerBarSurface
import app.winters.octo.design.ThemeGlass
import app.winters.octo.design.barHairlineY
import app.winters.octo.design.barLookOf
import app.winters.octo.design.barShadowY
import app.winters.octo.design.glassFilm
import app.winters.octo.design.glassFor
import app.winters.octo.testing.RobolectricComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// The shared glass surfaces read their film, frost, hairline and shadow
// through the theme's glass. Only Music Space has a recipe of its own (the
// reference's `.glass` and `.player-bar`); every other theme gets the
// default, which is the global constants the surfaces have always used.
@RunWith(RobolectricTestRunner::class)
class ThemeGlassTest {
    @get:Rule
    val ui = RobolectricComposeRule()

    @Test
    fun theFirstFiveThemesKeepTheGlobalGlass() {
        for (theme in ThemeChoice.entries.filter { it != ThemeChoice.MusicSpace }) {
            val glass = glassFor(theme)
            assertSame(theme.name, OctoDefaultGlass, glass)
            assertNull(glass.film)
            assertNull(glass.edge)
            assertNull(glass.drop)
            assertNull(glass.playerBar)
            assertEquals(GlazeFrost, glass.frost)
            assertEquals(GlazeSaturation, glass.saturation)
        }
        assertEquals(OctoGlass(), OctoDefaultGlass)
    }

    @Test
    fun musicSpaceTakesTheForestGlassRecipe() {
        val glass = glassFor(ThemeChoice.MusicSpace)
        assertSame(MusicSpaceGlass, glass)
        assertEquals(Color(0x8C181F14), glass.film)
        assertEquals(Color(0x29F4EEDD), glass.edge)
        assertEquals(16f, glass.frost)
        assertEquals(1.25f, glass.saturation)
        assertEquals(GlassDrop(Color(0x59050804), blur = 28f, y = 8f), glass.drop)
        // The player bar: rgba(19, 26, 16, 0.94), a 1px top edge in
        // rgba(244, 238, 221, 0.16), and 0 -8px 24px rgba(5, 8, 4, 0.35).
        assertEquals(
            PlayerBarSurface(Color(0xF0131A10), Color(0x29F4EEDD), GlassDrop(Color(0x59050804), blur = 24f, y = -8f)),
            glass.playerBar,
        )
        assertNotEquals(OctoDefaultGlass, glass)
    }

    // The bottom bar is the floating glaze under every theme but Music
    // Space, which draws it on its unfrosted player-bar surface.
    @Test
    fun onlyMusicSpaceDrawsTheBarOnItsOwnSurface() {
        for (theme in ThemeChoice.entries) {
            val expected = if (theme == ThemeChoice.MusicSpace) BarLook.Surface else BarLook.Glaze
            assertEquals(theme.name, expected, barLookOf(glassFor(theme)))
        }
    }

    // Docked at the bottom the surface casts its shadow upward with the
    // hairline on top, exactly as before; docked at the top (Music Space's
    // shell) the shadow falls down onto the page and the hairline faces it.
    @Test
    fun theBarSurfaceFlipsItsShadowAndHairlineWhenDockedAtTheTop() {
        val drop = glassFor(ThemeChoice.MusicSpace).playerBar!!.drop
        assertEquals(-8f, barShadowY(drop, topDocked = false))
        assertEquals(8f, barShadowY(drop, topDocked = true))
        assertEquals(1f, barHairlineY(topDocked = false))
        assertEquals(-1f, barHairlineY(topDocked = true))
    }

    // The glaze's film follows the theme: the five keep the dim GlazeTint has
    // always been; Music Space has the recipe's olive film. Over art every
    // theme's glass is Octo's, as its surfaces are.
    @Test
    fun theFilmFollowsTheThemeAndArtKeepsOctosGlass() {
        val seen = mutableMapOf<ThemeChoice, Triple<Color, Color, OctoGlass>>()
        var theme by mutableStateOf(ThemeChoice.entries.first())
        ui.compose.setContent {
            CompositionLocalProvider(LocalOctoGlass provides glassFor(theme)) {
                val tint = GlazeTint
                val plain = glassFilm(0.16f)
                var overArt: OctoGlass? = null
                OverArt { overArt = ThemeGlass }
                seen[theme] = Triple(tint, plain, overArt!!)
                BasicText(theme.name)
            }
        }
        for (choice in ThemeChoice.entries) {
            ui.compose.runOnIdle { theme = choice }
            ui.compose.waitForIdle()
        }
        assertEquals(ThemeChoice.entries.toSet(), seen.keys)
        for ((choice, values) in seen) {
            val (tint, plain, overArt) = values
            if (choice == ThemeChoice.MusicSpace) {
                assertEquals(Color(0x8C181F14), tint)
            } else {
                assertEquals(choice.name, plain, tint)
            }
            assertSame(choice.name, OctoDefaultGlass, overArt)
        }
    }
}
