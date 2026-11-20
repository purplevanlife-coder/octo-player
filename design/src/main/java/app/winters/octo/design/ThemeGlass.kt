package app.winters.octo.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.winters.octo.theme.ForestGlass
import app.winters.octo.theme.ThemeChoice

// A drop shadow in CSS terms (`0 y blur color`); Blur.kt converts the blur.
@Immutable
data class GlassDrop(val color: Color, val blur: Float, val y: Float)

// The bottom bar's own surface: a near-opaque fill, a hairline along its top
// edge and a soft shadow cast upward. Never frosted.
@Immutable
data class PlayerBarSurface(val fill: Color, val edge: Color, val drop: GlassDrop)

// The glass a theme draws its shared glass surfaces in (Glaze and the
// floating glass, the panels, the sheets), the same way OctoFonts gives its
// type. Every value left at its default keeps the global constants those
// surfaces have always drawn with, so every theme but Music Space keeps
// exactly its current glass. Surface values only: what blurs, what is hit
// and what lies over what stay as they are.
@Immutable
data class OctoGlass(
    // The glaze's film over what it frosts. Null keeps GlazeTint's dim.
    val film: Color? = null,
    // A hairline round the glass, over its lit rim. Null draws none.
    val edge: Color? = null,
    // How hard the glaze frosts and deepens what is behind it, by default.
    // Callers that ask for their own frost (menus, dark glass) keep it.
    val frost: Float = GlazeFrost,
    val saturation: Float = GlazeSaturation,
    // The shadow under floating glass, panels and sheets. Null keeps each
    // surface's own shadows.
    val drop: GlassDrop? = null,
    // The bottom bar's own surface. Null keeps the bar the floating glaze
    // it has always been.
    val playerBar: PlayerBarSurface? = null,
)

val OctoDefaultGlass = OctoGlass()

// Music Space's `.glass` recipe (theme.css), and its `.player-bar`.
val MusicSpaceGlass = OctoGlass(
    film = Color(ForestGlass.GlassFill),
    edge = Color(ForestGlass.GlassEdge),
    frost = ForestGlass.GlassBlur,
    saturation = ForestGlass.GlassSaturation,
    drop = GlassDrop(Color(ForestGlass.GlassShadow), blur = ForestGlass.GlassShadowBlur, y = ForestGlass.GlassShadowY),
    playerBar = PlayerBarSurface(
        fill = Color(ForestGlass.PlayerBar),
        edge = Color(ForestGlass.GlassEdge),
        drop = GlassDrop(Color(ForestGlass.GlassShadow), blur = ForestGlass.PlayerBarShadowBlur, y = ForestGlass.PlayerBarShadowY),
    ),
)

fun glassFor(theme: ThemeChoice): OctoGlass = if (theme == ThemeChoice.MusicSpace) MusicSpaceGlass else OctoDefaultGlass

// The glass of the theme showing, provided once at the window's root.
val LocalOctoGlass = staticCompositionLocalOf { OctoDefaultGlass }

// The glass to draw here. Over art (the player, a cover) everything draws
// in Octo's dark look, so its glass is Octo's too.
val ThemeGlass: OctoGlass
    @Composable
    @ReadOnlyComposable
    get() = if (LocalOverArtOn.current) OctoDefaultGlass else LocalOctoGlass.current
