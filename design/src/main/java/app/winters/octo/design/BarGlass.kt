package app.winters.octo.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs

// Which surface the bottom bar is drawn on in `glass`: the floating glaze,
// or the theme's own player-bar surface.
enum class BarLook { Glaze, Surface }

fun barLookOf(glass: OctoGlass): BarLook = if (glass.playerBar == null) BarLook.Glaze else BarLook.Surface

// The surface's shadow and hairline follow where the bar is docked: at the
// bottom the shadow is cast upward and the hairline runs along the top edge,
// as the reference draws it; docked at the top (Music Space's shell), the
// shadow falls downward onto the page and the hairline runs along the
// bottom edge, the side that faces the page.
fun barShadowY(drop: GlassDrop, topDocked: Boolean): Float = if (topDocked) abs(drop.y) else drop.y

fun barHairlineY(topDocked: Boolean): Float = if (topDocked) -1f else 1f

// The glass of the bottom bar's parts. Where the theme's glass names a
// player-bar surface (Music Space) the part sits on that near-opaque fill,
// with a hairline along its top edge and a soft shadow cast upward, and
// nothing behind it is frosted: the reference's own warning is that a
// backdrop filter on its bar trapped the full player. Every other theme
// keeps the floating glaze exactly as before. Chrome only: `modifier`
// (size, place, gestures) and `content` are laid out the same either way.
// `film` still carries a trace of the song's colour when the bar is chosen
// to glow; the plain film is the glaze's and is not laid over the surface.
@Composable
fun BarGlass(
    backdrop: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    film: Color = GlazeTint,
    // Docked at the top of the screen rather than the bottom.
    topDocked: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val bar = ThemeGlass.playerBar
    if (bar == null) {
        FloatingGlaze(backdrop, modifier, shape = shape, film = film, content = content)
    } else {
        val plain = GlazeTint
        Box(modifier) {
            Box(
                Modifier
                    .matchParentSize()
                    .dropShadow(shape, Shadow(radius = shadowBlur(bar.drop.blur), color = bar.drop.color, offset = DpOffset(0.dp, barShadowY(bar.drop, topDocked).dp)))
                    .clip(shape)
                    .background(bar.fill)
                    .then(if (film != plain) Modifier.background(film) else Modifier)
                    // The hairline along the edge that faces the page.
                    .innerShadow(shape, Shadow(radius = 0.dp, color = bar.edge, offset = DpOffset(0.dp, barHairlineY(topDocked).dp))),
            )
            content()
        }
    }
}
