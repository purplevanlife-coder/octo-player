package app.winters.octo.theme

import app.winters.octo.player.immersive.contrastRatio
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The Music Space theme: registered after the first five, picked and stored
// like them, and drawn in the Forest Glass values of the reference's
// theme.css. The other themes' colors are pinned by ResolvedFixtureTest.
class MusicSpaceThemeTest {
    // A CSS rgba() as ARGB.
    private fun rgba(r: Int, g: Int, b: Int, a: Double) = ((a * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

    private fun hex(argb: Int) = "%08X".format(argb)

    private val p = resolvePalette(ThemeChoice.MusicSpace, AccentChoice.Octo, null, false)

    @Test
    fun itIsRegisteredAfterTheFirstFiveAsADarkTheme() {
        assertEquals(ThemeChoice.MusicSpace, ThemeChoice.entries.last())
        assertEquals(5, ThemeChoice.MusicSpace.ordinal)
        assertEquals("Music Space", ThemeChoice.MusicSpace.label)
        assertFalse(ThemeChoice.MusicSpace.light)
        assertEquals(ThemeChoice.MusicSpace, ThemeChoice.named("MusicSpace"))
    }

    @Test
    fun itIsPickedAndStoredLikeTheOthers() {
        val picked = pickTheme(ThemeSettings(), ThemeChoice.MusicSpace)
        assertEquals(ThemeSettings(theme = ThemeChoice.MusicSpace, light = ThemeChoice.Daylight, dark = ThemeChoice.MusicSpace), picked)
        assertEquals(ThemeChoice.MusicSpace, activeTheme(picked, deviceDark = false))
        // Read back from the stored names, as ThemeStore does.
        assertEquals(picked, themeSettings(picked.theme.name, picked.light.name, picked.dark.name, picked.match))
        // With Match on it is the night theme; it can never fill the day slot.
        val matched = setMatch(picked, on = true, deviceDark = true)
        assertEquals(ThemeChoice.MusicSpace, activeTheme(matched, deviceDark = true))
        assertEquals(ThemeChoice.Daylight, activeTheme(matched, deviceDark = false))
        assertEquals(ThemeChoice.Daylight, themeSettings("MusicSpace", "MusicSpace", "MusicSpace", true).light)
        assertEquals("By day: Daylight. By night: Music Space.", matchHelp(matched))
    }

    @Test
    fun theReferenceValuesAreTheStylesheets() {
        assertEquals(hex(0xFF131A10.toInt()), hex(ForestGlass.Bg))
        assertEquals(hex(rgba(26, 33, 22, 0.58)), hex(ForestGlass.Surface))
        assertEquals(hex(rgba(238, 232, 210, 0.13)), hex(ForestGlass.SurfaceAlt))
        assertEquals(hex(0xFFF4EEDD.toInt()), hex(ForestGlass.Text))
        assertEquals(hex(0xFFCDC7AE.toInt()), hex(ForestGlass.Dim))
        assertEquals(hex(rgba(244, 238, 221, 0.18)), hex(ForestGlass.Border))
        assertEquals(hex(0xFF7D9168.toInt()), hex(ForestGlass.Accent))
        assertEquals(hex(rgba(125, 145, 104, 0.28)), hex(ForestGlass.AccentSoft))
        assertEquals(hex(0xFFAEBFA5.toInt()), hex(ForestGlass.Accent2))
        assertEquals(hex(0xFFEECFA3.toInt()), hex(ForestGlass.Hand))
        assertEquals(hex(0xFFF0DFBA.toInt()), hex(ForestGlass.NoteInk))
        assertEquals(hex(0xFFEEC27A.toInt()), hex(ForestGlass.Amber))
        assertEquals(hex(rgba(238, 194, 122, 0.18)), hex(ForestGlass.AmberSoft))
        assertEquals(hex(0xFFF0927F.toInt()), hex(ForestGlass.Danger))
        assertEquals(hex(rgba(19, 26, 16, 0.94)), hex(ForestGlass.PlayerBar))
        assertEquals(hex(rgba(24, 31, 20, 0.55)), hex(ForestGlass.GlassFill))
        assertEquals(hex(rgba(244, 238, 221, 0.16)), hex(ForestGlass.GlassEdge))
        assertEquals(hex(rgba(5, 8, 4, 0.35)), hex(ForestGlass.GlassShadow))
        assertEquals(16f, ForestGlass.GlassBlur)
        assertEquals(1.25f, ForestGlass.GlassSaturation)
        assertEquals(18f, ForestGlass.Radius)
    }

    @Test
    fun theKeyRolesResolveToForestGlass() {
        assertEquals(ThemeChoice.MusicSpace, p.theme)
        assertFalse(p.light)
        assertEquals(hex(ForestGlass.Bg), hex(p.page))
        assertEquals(hex(ForestGlass.Text), hex(p.text))
        assertEquals(hex(ForestGlass.Text), hex(p.ink))
        assertEquals(hex(ForestGlass.Dim), hex(p.secondary))
        assertEquals(hex(alphaArgb(ForestGlass.Dim, 800)), hex(p.muted))
        assertEquals(hex(ForestGlass.Border), hex(p.edge))
        assertEquals(hex(ForestGlass.Accent2), hex(p.accents.accent))
        assertEquals(hex(ForestGlass.Amber), hex(p.complement))
        assertEquals(hex(ForestGlass.Hand), hex(p.third))
        assertEquals(3, p.glow.size)
    }

    @Test
    fun theLayersAreTheGlassLaidOverWhatItSitsOn() {
        // Raised: the cream pane over the forest; card: the olive glass over that.
        assertEquals(hex(overArgb(ForestGlass.SurfaceAlt, ForestGlass.Bg)), hex(p.raised))
        assertEquals("FF2F3529", hex(p.raised))
        assertEquals(hex(overArgb(ForestGlass.Surface, p.raised)), hex(p.card))
        assertEquals("FF23291E", hex(p.card))
        // The sheet: the player bar's near-opaque dark olive, with the faint
        // trace of the glass accent every theme's sheet takes.
        assertEquals(ForestGlass.PlayerBar ushr 24, p.sheet ushr 24)
        assertEquals(hex(alphaArgb(mixArgb(ForestGlass.Bg, ForestGlass.Accent2, 50), 940)), hex(p.sheet))
    }

    // Why the accent is --accent2: --accent falls under the 4.5:1 every
    // accent keeps on the page, the card and the raised layer.
    @Test
    fun theLightSageIsTheAccentBecauseTheSageDoesNotReadOnTheGlass() {
        assertTrue(contrastRatio(ForestGlass.Accent, p.card) < 4.5)
        assertTrue(contrastRatio(ForestGlass.Accent, p.raised) < 4.5)
        for (layer in listOf(p.page, p.card, p.raised)) {
            assertTrue("accent on ${hex(layer)}", contrastRatio(p.accents.accent, layer) >= 4.5)
        }
    }

    // FormColorsTest reads only the fixture's five themes; the error red
    // reads on Music Space's layers too.
    @Test
    fun errorWordsReadOnEveryLayer() {
        for (layer in listOf(p.page, p.card, p.raised, overArgb(p.sheet, p.page))) {
            assertTrue("error words on ${hex(layer)}", contrastRatio(p.errorWords, layer) >= 4.5)
        }
    }

    // The fixture pins every role; these are the headline colors, by name,
    // for anyone reading this test for what did not change.
    @Test
    fun theOtherThemesKeepTheirColors() {
        val pages = ThemeChoice.entries.filter { it != ThemeChoice.MusicSpace }.associateWith { hex(it.base.page) to hex(it.base.accent) }
        assertEquals(
            mapOf(
                ThemeChoice.Octo to ("FF0C0C0D" to "FF97B1B9"),
                ThemeChoice.Moss to ("FF0E1512" to "FF9ACB86"),
                ThemeChoice.Dusk to ("FF120E18" to "FFE7A0C4"),
                ThemeChoice.Daylight to ("FFE7ECEF" to "FF2E6577"),
                ThemeChoice.Paper to ("FFEBE2D2" to "FF8F3F1D"),
            ),
            pages,
        )
    }
}
