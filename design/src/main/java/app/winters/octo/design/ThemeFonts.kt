package app.winters.octo.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    // theme names none. A style that already names its own face (the mono
    // codes, the covers' Inter Display) keeps it: only words drawn in the
    // platform face take the theme's.
    fun displayStyle(style: TextStyle): TextStyle = inFace(display, style)

    fun bodyStyle(style: TextStyle): TextStyle = inFace(body, style)

    private fun inFace(face: FontFamily?, style: TextStyle): TextStyle =
        if (face == null || style.fontFamily != null) style else style.copy(fontFamily = face)
}

val OctoDefaultFonts = OctoFonts()

// Music Space's faces, as the reference sets them: headings in Playfair
// Display, body in DM Sans (both OFL; licences in design/licenses). Static
// instances from this module's font resources, declared like
// CoverFontFamily. Playfair has no Black, so OctoType's Black headings
// draw in its Bold (the nearest heavier weight on file). Nothing is
// fetched at run time.
val MusicSpaceDisplayFamily: FontFamily = FontFamily(
    Font(R.font.playfair_display_medium, FontWeight.Medium),
    Font(R.font.playfair_display_semibold, FontWeight.SemiBold),
    Font(R.font.playfair_display_bold, FontWeight.Bold),
)
val MusicSpaceBodyFamily: FontFamily = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)

val MusicSpaceFonts = OctoFonts(display = MusicSpaceDisplayFamily, body = MusicSpaceBodyFamily)

fun fontsFor(theme: ThemeChoice): OctoFonts = if (theme == ThemeChoice.MusicSpace) MusicSpaceFonts else OctoDefaultFonts

// The faces of the theme showing, provided once at the window's root.
val LocalOctoFonts = staticCompositionLocalOf { OctoDefaultFonts }

// The theme-aware way to read an OctoType style: `displayType(OctoType.title)`
// for page titles, shelf headers and hero names, `bodyType(OctoType.bodySmall)`
// for song rows and body text. Only the face changes, never the size, weight
// or spacing, and under every theme but Music Space the style comes back as
// it went in, so those themes draw exactly as before.
@Composable
@ReadOnlyComposable
fun displayType(style: TextStyle): TextStyle = LocalOctoFonts.current.displayStyle(style)

@Composable
@ReadOnlyComposable
fun bodyType(style: TextStyle): TextStyle = LocalOctoFonts.current.bodyStyle(style)
