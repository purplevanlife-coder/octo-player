package app.winters.octo.theme

import androidx.compose.ui.text.font.FontFamily
import app.winters.octo.design.CoverFontFamily
import app.winters.octo.design.MusicSpaceFonts
import app.winters.octo.design.OctoDefaultFonts
import app.winters.octo.design.OctoType
import app.winters.octo.design.fontsFor
import org.junit.Assert.assertEquals
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

    // The stand-ins until Playfair Display and DM Sans are bundled: the
    // platform serif and the Inter Display the covers carry. No face is
    // fetched at run time.
    @Test
    fun musicSpaceSetsHeadingsInASerifAndBodyInTheBundledSans() {
        val fonts = fontsFor(ThemeChoice.MusicSpace)
        assertSame(MusicSpaceFonts, fonts)
        assertEquals(FontFamily.Serif, fonts.display)
        assertEquals(CoverFontFamily, fonts.body)
        for (style in styles) {
            assertEquals(style.copy(fontFamily = FontFamily.Serif), fonts.displayStyle(style))
            assertEquals(style.copy(fontFamily = CoverFontFamily), fonts.bodyStyle(style))
            // Only the face changes; the scale stays OctoType's.
            assertEquals(style.fontSize, fonts.displayStyle(style).fontSize)
            assertEquals(style.fontWeight, fonts.bodyStyle(style).fontWeight)
        }
    }
}
