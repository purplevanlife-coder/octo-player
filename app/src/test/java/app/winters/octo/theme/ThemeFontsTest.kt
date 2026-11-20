package app.winters.octo.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import app.winters.octo.design.CoverFontFamily
import app.winters.octo.design.MusicSpaceBodyFamily
import app.winters.octo.design.MusicSpaceDisplayFamily
import app.winters.octo.design.MusicSpaceFonts
import app.winters.octo.design.OctoDefaultFonts
import app.winters.octo.design.OctoType
import app.winters.octo.design.R
import app.winters.octo.design.fontsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ThemeFontsTest {
    private val styles = listOf(OctoType.display, OctoType.title, OctoType.headline, OctoType.body, OctoType.caption)

    @Test
    fun theFirstFiveThemesNameNoFaceAndDrawAsBefore() {
        for (theme in ThemeChoice.entries.filter { it != ThemeChoice.MusicSpace }) {
            val fonts = fontsFor(theme)
            assertSame(theme.name, OctoDefaultFonts, fonts)
            assertNull(fonts.display)
            assertNull(fonts.body)
            for (style in styles) {
                assertEquals(theme.name, style, fonts.displayStyle(style))
                assertEquals(theme.name, style, fonts.bodyStyle(style))
            }
        }
    }

    // The real faces, bundled: Playfair Display for headings, DM Sans for
    // body, from the design module's font resources. No face is fetched
    // at run time.
    @Test
    fun musicSpaceSetsHeadingsInPlayfairDisplayAndBodyInDmSans() {
        val fonts = fontsFor(ThemeChoice.MusicSpace)
        assertSame(MusicSpaceFonts, fonts)
        assertSame(MusicSpaceDisplayFamily, fonts.display)
        assertSame(MusicSpaceBodyFamily, fonts.body)
        assertNotEquals(FontFamily.Serif, fonts.display)
        assertNotEquals(CoverFontFamily, fonts.body)
        for (style in styles) {
            assertEquals(style.copy(fontFamily = MusicSpaceDisplayFamily), fonts.displayStyle(style))
            assertEquals(style.copy(fontFamily = MusicSpaceBodyFamily), fonts.bodyStyle(style))
            // Only the face changes; the scale stays OctoType's.
            assertEquals(style.fontSize, fonts.displayStyle(style).fontSize)
            assertEquals(style.fontWeight, fonts.displayStyle(style).fontWeight)
            assertEquals(style.letterSpacing, fonts.displayStyle(style).letterSpacing)
            assertEquals(style.fontSize, fonts.bodyStyle(style).fontSize)
            assertEquals(style.fontWeight, fonts.bodyStyle(style).fontWeight)
        }
    }

    @Test
    fun musicSpaceFamiliesAreTheBundledFiles() {
        assertEquals(
            listOf(
                R.font.playfair_display_medium to FontWeight.Medium,
                R.font.playfair_display_semibold to FontWeight.SemiBold,
                R.font.playfair_display_bold to FontWeight.Bold,
            ),
            faces(MusicSpaceDisplayFamily),
        )
        assertEquals(
            listOf(
                R.font.dm_sans_regular to FontWeight.Normal,
                R.font.dm_sans_medium to FontWeight.Medium,
                R.font.dm_sans_semibold to FontWeight.SemiBold,
                R.font.dm_sans_bold to FontWeight.Bold,
            ),
            faces(MusicSpaceBodyFamily),
        )
    }

    // Every style in the scale, not just the headline ones, comes back as the
    // very same object under the five themes that name no face: the screens
    // that now read the theme's type draw exactly as before under them.
    @Test
    fun theFirstFiveThemesHandBackEveryStyleUntouched() {
        for (theme in ThemeChoice.entries.filter { it != ThemeChoice.MusicSpace }) {
            val fonts = fontsFor(theme)
            for (style in everyStyle) {
                assertSame(theme.name, style, fonts.displayStyle(style))
                assertSame(theme.name, style, fonts.bodyStyle(style))
            }
        }
    }

    // A style that names its own face (the mono codes, the covers' Inter
    // Display) keeps it under Music Space too; only words in the platform
    // face take the theme's.
    @Test
    fun aStyleWithItsOwnFaceKeepsIt() {
        val fonts = fontsFor(ThemeChoice.MusicSpace)
        for (own in listOf(FontFamily.Monospace, CoverFontFamily)) {
            val style = OctoType.caption.copy(fontFamily = own)
            assertSame(style, fonts.displayStyle(style))
            assertSame(style, fonts.bodyStyle(style))
        }
        for (style in everyStyle) {
            assertEquals(MusicSpaceDisplayFamily, fonts.displayStyle(style).fontFamily)
            assertEquals(MusicSpaceBodyFamily, fonts.bodyStyle(style).fontFamily)
            // Applying twice is applying once.
            assertEquals(fonts.bodyStyle(style), fonts.bodyStyle(fonts.bodyStyle(style)))
        }
    }

    private val everyStyle = with(OctoType) {
        listOf(
            display, title, headline, section, body, bodySmall, caption, label,
            formSection, formGroup, formName, formValue, formDescription, formEntry, formNote, formTag,
        )
    }

    private fun faces(family: FontFamily): List<Pair<Int, FontWeight>> =
        (family as FontListFontFamily).fonts.map { (it as ResourceFont).resId to it.weight }
}
