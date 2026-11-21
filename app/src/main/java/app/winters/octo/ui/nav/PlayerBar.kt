package app.winters.octo.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.winters.octo.design.BarGlass
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoType
import app.winters.octo.design.bodyType
import app.winters.octo.playback.NowPlaying
import app.winters.octo.player.LiveStopIcon
import app.winters.octo.player.fractionAt
import app.winters.octo.player.rememberPositionMs
import app.winters.octo.theme.ForestGlass
import app.winters.octo.ui.common.Artwork
import app.winters.octo.ui.common.LocalReduceMotion
import app.winters.octo.ui.common.SongTitle
import app.winters.octo.ui.common.fadingMarquee
import app.winters.octo.ui.common.isOutsideLibrary
import app.winters.octo.ui.output.CastingMark
import dev.chrisbanes.haze.HazeState

// The player bar's height: a little taller than the tab bar, so the
// artwork and the three transport buttons sit comfortably in it.
val PlayerBarHeight = 64.dp

// What the player bar's touches do: a tap on the bar opens the full
// player, and its buttons go back, play or pause, and on.
class PlayerBarActions(
    val onOpenPlayer: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
)

// Whether the bar draws its progress line: only for a song. A station has
// no length to run along, and with nothing loaded there is nothing to show.
fun playerBarShowsProgress(now: NowPlaying): Boolean = now.trackId != null && now.live == null

// Music Space's everyday control: a player bar docked at the bottom of every
// screen, under the tab bar's top-docked shell. It is the classic bar's
// small player (PlayerCapsule) widened into a bar of its own — the artwork,
// the song and its artist, and Previous, Play/Pause and Next right on the
// bar — with a thin progress line along its top edge. Tapping anywhere off
// the buttons opens the full player, as tapping the capsule does. With
// nothing loaded it stays, offering a shuffle of the library, so the
// control is always there. The caller aligns it at the bottom centre; it
// keeps the same margins as the tab bar has at the bottom.
@Composable
fun BottomPlayerBar(
    haze: HazeState,
    now: NowPlaying,
    positionMs: () -> Long,
    actions: PlayerBarActions,
    artModifier: Modifier,
    artShape: Shape,
    modifier: Modifier = Modifier,
) {
    val hasSong = now.trackId != null
    // The line steps twice a second; the value is read while drawing, so
    // each step redraws only the line.
    val position = rememberPositionMs(now, positionMs, everyMs = 500)
    val tapLabel = if (hasSong) "Open player" else "Shuffle your library"
    val onTap = if (hasSong) actions.onOpenPlayer else actions.onPlayPause

    BarGlass(
        haze,
        modifier
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = BarBottomGap)
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .height(PlayerBarHeight),
        shape = CircleShape,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = tapLabel,
                    onClick = onTap,
                )
                .semantics {
                    contentDescription = nowPlayingWords(now)
                    nowPlayingState(now).takeIf(String::isNotEmpty)?.let { stateDescription = it }
                },
        ) {
            Row(
                Modifier.matchParentSize().padding(start = 8.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (!hasSong) {
                        Icon(painterResource(OctoIcons.Play), contentDescription = null, tint = OctoColors.TextPrimary, modifier = Modifier.size(22.dp))
                    } else {
                        Artwork(now.artwork, 44.dp, artModifier, shape = artShape, outside = isOutsideLibrary(now.trackId))
                        // A song with no artwork shows a note in its place, never
                        // an empty hole.
                        if (now.artwork == null) {
                            Icon(
                                painterResource(OctoIcons.Songs),
                                contentDescription = null,
                                tint = Color(ForestGlass.Dim).copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp * 0.36f),
                            )
                        }
                    }
                    // Playing on a TV or speaker.
                    if (now.casting) CastingMark(Modifier.align(Alignment.BottomEnd).padding(2.dp))
                }
                // A title too long for the bar scrolls through and fades softly
                // at its edges, as the capsule's does, rather than being cut.
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    SongTitle(
                        if (hasSong) now.title.orEmpty() else "Shuffle your library",
                        explicit = hasSong && now.explicit,
                        style = OctoType.label,
                        color = OctoColors.TextPrimary,
                        titleModifier = Modifier.fadingMarquee(now.trackId, scroll = !LocalReduceMotion.current),
                        overflow = TextOverflow.Clip,
                    )
                    if (hasSong) {
                        Text(
                            // While a play starts, the words say so in the artist's place.
                            now.starting ?: now.artist.orEmpty(),
                            style = bodyType(OctoType.caption.copy(fontSize = 11.sp)),
                            color = OctoColors.TextMuted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.fadingMarquee(now.trackId, scroll = false),
                        )
                    }
                }
                val live = now.live
                when {
                    // Nothing loaded: Play alone, which shuffles the library.
                    !hasSong -> BarButton(OctoIcons.Play, "Play", actions.onPlayPause)
                    live == null -> {
                        BarButton(OctoIcons.Previous, "Previous", actions.onPrevious)
                        BarButton(if (now.isPlaying) OctoIcons.Pause else OctoIcons.Play, if (now.isPlaying) "Pause" else "Play", actions.onPlayPause)
                        BarButton(OctoIcons.Next, "Next", actions.onNext)
                    }
                    else -> {
                        // A station stops rather than pauses; Back and Next change
                        // station, while there is more than one to change to.
                        val lineup = live.lineup
                        if (lineup.shows) BarButton(OctoIcons.Previous, "Previous station", actions.onPrevious, enabled = lineup.hasPrevious)
                        BarButton(if (now.playWhenReady) LiveStopIcon else OctoIcons.Play, if (now.playWhenReady) "Stop" else "Play", actions.onPlayPause)
                        if (lineup.shows) BarButton(OctoIcons.Next, "Next station", actions.onNext, enabled = lineup.hasNext)
                    }
                }
            }
            if (playerBarShowsProgress(now)) {
                ProgressLine(now, position, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

// The thin line along the bar's top edge, inset so it clears the stadium's
// curved ends: a faint cream track with the played part over it in sage.
// The position is read while drawing, so its steps redraw only the line.
@Composable
private fun ProgressLine(now: NowPlaying, position: MutableLongState, modifier: Modifier) {
    val track = Color(ForestGlass.Text).copy(alpha = 0.18f)
    val played = Color(ForestGlass.Accent2)
    Box(
        modifier
            .padding(horizontal = ProgressInset)
            .fillMaxWidth()
            .height(ProgressHeight)
            .drawBehind {
                drawRect(track)
                val fraction = now.fractionAt(position.longValue).coerceIn(0f, 1f)
                if (fraction > 0f) drawRect(played, topLeft = Offset.Zero, size = Size(size.width * fraction, size.height))
            },
    )
}

private val ProgressInset = 22.dp
private val ProgressHeight = 2.5.dp

// One of the bar's transport buttons: its own click, so a press on it never
// opens the player behind it. A private copy of the capsule's button, sized
// for the taller bar.
@Composable
private fun BarButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .size(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (enabled) OctoColors.TextPrimary else OctoColors.TextPrimary.copy(alpha = 0.3f),
            modifier = Modifier.size(26.dp),
        )
    }
}
