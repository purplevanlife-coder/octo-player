package app.winters.octo.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import app.winters.octo.theme.ThemeChoice

// The faces a theme sets its words in: display for headings and titles,
// body for everything read in rows and paragraphs. Null keeps the face
// OctoType has always drawn in (the platform's), so every theme but Music
// Space names none and draws exactly as before. Android only: the desktop
// keeps its own type (DesktopMetrics.kt), so this file is not shared.
@Immutable
data class OctoFonts(
    val display: FontFamily? = null,
    val body: FontFamily? = null,
) {
    // `style` in this theme's display or body face, or as it is where the
    // theme names none.
    fun displayStyle(style: TextStyle): TextStyle = if (display == null) style else style.copy(fontFamily = display)

    fun bodyStyle(style: TextStyle): TextStyle = if (body == null) style else style.copy(fontFamily = body)
}

val OctoDefaultFonts = OctoFonts()

// Music Space's faces. The reference sets headings in Playfair Display and
// body in DM Sans (both OFL). Until those files are bundled under
// res/font, these are the closest faces already on board: the platform
// serif for the headings and Inter Display, which the covers already
// carry, for the body. Swapping a family here is the whole change; see
// PHASE2-FOUNDATION-NOTES.md for the file names. Nothing is fetched at
// run time.
val MusicSpaceDisplayFamily: FontFamily = FontFamily.Serif
val MusicSpaceBodyFamily: FontFamily = CoverFontFamily

val MusicSpaceFonts = OctoFonts(display = MusicSpaceDisplayFamily, body = MusicSpaceBodyFamily)

fun fontsFor(theme: ThemeChoice): OctoFonts = if (theme == ThemeChoice.MusicSpace) MusicSpaceFonts else OctoDefaultFonts

// The faces of the theme showing, provided once at the window's root.
val LocalOctoFonts = staticCompositionLocalOf { OctoDefaultFonts }
