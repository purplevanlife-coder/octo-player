package app.winters.octo.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.winters.octo.design.BarGlass
import app.winters.octo.design.OctoIcons
import app.winters.octo.playback.NowPlaying
import app.winters.octo.player.fractionAt
import app.winters.octo.player.rememberPositionMs
import app.winters.octo.theme.ForestGlass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// What the bubble's touches do, on the same calls as the bar's round button.
class BubbleActions(
    val onOpenPlayer: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    // Where it was dropped or how big it was made, to keep.
    val onKeep: (BubblePlace) -> Unit,
)

// Music Space's now-playing button, free of the bar: the round artwork with
// its progress ring (NowPlayingFace, as on the bar), floating over the pages
// wherever it was last left.
//
// - A tap opens the full player. With nothing loaded it shows the play sign
//   and a tap shuffles the library, as the bar's round button does.
// - A drag moves it; it stays where it is dropped, kept on screen with a
//   margin, and the place is kept as fractions of the screen.
// - A long press opens its adjust row beside it: smaller, play or pause,
//   bigger. A tap anywhere else (or a few quiet seconds) closes the row.
//   That tap is taken by the row's closing, so it never lands on the page.
//
// A drag that moves past the touch slop is never a tap: the drag consumes
// the touch, which cancels the tap and the long press, so moving the bubble
// never opens the player or toggles playback. Touches anywhere off the
// bubble reach the page under it (overlapping siblings do not share touches
// in Compose), so the pages still scroll and swipe round it.
@Composable
fun NowPlayingBubble(
    haze: HazeState,
    now: NowPlaying,
    positionMs: () -> Long,
    place: BubblePlace,
    actions: BubbleActions,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(actions)
    val hasSong = now.trackId != null
    // What is shown: the kept place, or while it is being changed, what the
    // finger has made of it (so a drop never jumps back while it is stored).
    var shown by remember { mutableStateOf(place) }
    LaunchedEffect(place) { shown = place }
    var drag by remember { mutableStateOf<Offset?>(null) }
    var adjusting by remember { mutableStateOf(false) }
    // The adjust row closes itself after a few quiet seconds.
    LaunchedEffect(adjusting, shown.sizeDp) {
        if (adjusting) {
            delay(AdjustQuietMs)
            adjusting = false
        }
    }
    LaunchedEffect(hasSong) { if (!hasSong) adjusting = false }
    val position = rememberPositionMs(now, positionMs, everyMs = RingStepMs)
    val progress = { now.fractionAt(position.longValue) }

    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(BubbleInsets)) {
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        val margin = with(density) { BubbleMath.MARGIN_DP.dp.toPx() }
        val sizeDp = BubbleMath.clampSize(shown.sizeDp)
        val sizePx = with(density) { sizeDp.dp.toPx() }
        val rest = Offset(
            BubbleMath.offsetOf(shown.x, areaW, sizePx, margin),
            BubbleMath.offsetOf(shown.y, areaH, sizePx, margin),
        )
        val corner = drag ?: rest
        val latestRest by rememberUpdatedState(rest)
        val geometry by rememberUpdatedState(Triple(areaW, areaH, sizePx))

        fun keep(next: BubblePlace) {
            shown = next
            current.onKeep(next)
        }

        fun resize(bigger: Boolean) {
            val newSize = BubbleMath.stepped(sizeDp, bigger)
            if (newSize == sizeDp) return
            val newPx = with(density) { newSize.dp.toPx() }
            val x = BubbleMath.resizedOffset(rest.x, sizePx, newPx, areaW, margin)
            val y = BubbleMath.resizedOffset(rest.y, sizePx, newPx, areaH, margin)
            keep(BubblePlace(BubbleMath.fractionOf(x, areaW, newPx, margin), BubbleMath.fractionOf(y, areaH, newPx, margin), newSize))
        }

        // While adjusting, a touch anywhere off the bubble and its row only
        // closes the row.
        if (adjusting) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Initial)
                            down.consume()
                            adjusting = false
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .semantics { contentDescription = "Close the bubble's adjust buttons" },
            )
        }

        Box(
            Modifier
                .offset { IntOffset(corner.x.roundToInt(), corner.y.roundToInt()) }
                .size(sizeDp.dp)
                // Outside the drag in the chain, so the drag sees each move
                // first and, once it consumes it, the tap is cancelled.
                .pointerInput(hasSong) {
                    detectTapGestures(
                        onTap = {
                            if (adjusting) {
                                adjusting = false
                            } else if (hasSong) {
                                current.onOpenPlayer()
                            } else {
                                current.onPlayPause()
                            }
                        },
                        onLongPress = {
                            if (hasSong) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                adjusting = true
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { drag = latestRest },
                        onDragEnd = {
                            val (w, h, s) = geometry
                            drag?.let { dropped ->
                                keep(shown.copy(x = BubbleMath.fractionOf(dropped.x, w, s, margin), y = BubbleMath.fractionOf(dropped.y, h, s, margin)))
                            }
                            drag = null
                        },
                        onDragCancel = {
                            val (w, h, s) = geometry
                            drag?.let { dropped ->
                                keep(shown.copy(x = BubbleMath.fractionOf(dropped.x, w, s, margin), y = BubbleMath.fractionOf(dropped.y, h, s, margin)))
                            }
                            drag = null
                        },
                    ) { change, amount ->
                        change.consume()
                        val (w, h, s) = geometry
                        val from = drag ?: latestRest
                        drag = Offset(
                            BubbleMath.clampOffset(from.x + amount.x, w, s, margin),
                            BubbleMath.clampOffset(from.y + amount.y, h, s, margin),
                        )
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = nowPlayingWords(now)
                    nowPlayingState(now).takeIf(String::isNotEmpty)?.let { stateDescription = it }
                    onClick(label = if (hasSong) "Open player" else "Shuffle your library") {
                        if (hasSong) current.onOpenPlayer() else current.onPlayPause()
                        true
                    }
                    if (hasSong) {
                        customActions = listOf(
                            CustomAccessibilityAction(if (now.isPlaying) "Pause" else "Play") { current.onPlayPause(); true },
                            CustomAccessibilityAction("Next") { current.onNext(); true },
                            CustomAccessibilityAction("Previous") { current.onPrevious(); true },
                            CustomAccessibilityAction("Make the bubble bigger") { resize(bigger = true); true },
                            CustomAccessibilityAction("Make the bubble smaller") { resize(bigger = false); true },
                        )
                    }
                },
        ) {
            BarGlass(haze, Modifier.matchParentSize(), shape = CircleShape, topDocked = true) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    NowPlayingFace(now, progress, size = sizeDp.dp)
                    // A song with no artwork shows a note in its circle, as
                    // the full player's does, never an empty hole.
                    if (hasSong && now.artwork == null) {
                        Icon(
                            painterResource(OctoIcons.Songs),
                            contentDescription = null,
                            tint = Color(ForestGlass.Dim).copy(alpha = 0.6f),
                            modifier = Modifier.size(sizeDp.dp * 0.36f),
                        )
                    }
                }
            }
            if (adjusting) {
                Box(Modifier.matchParentSize().border(2.dp, Color(ForestGlass.Accent2), CircleShape))
            }
        }

        if (adjusting) {
            // The row beside the bubble: above it in the lower half of the
            // screen, under it in the upper half, held on screen.
            val rowW = with(density) { (AdjustChipSize * 3 + AdjustGap * 2).toPx() }
            val rowH = with(density) { AdjustChipSize.toPx() }
            val gap = with(density) { AdjustGap.toPx() }
            val x = (corner.x + sizePx / 2 - rowW / 2).coerceIn(margin, (areaW - rowW - margin).coerceAtLeast(margin))
            val above = corner.y - gap - rowH
            val y = if (shown.y > 0.5f && above >= 0f) above else corner.y + sizePx + gap
            Row(
                Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                horizontalArrangement = Arrangement.spacedBy(AdjustGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdjustChip(OctoIcons.Close, "Make the bubble smaller", enabled = sizeDp > BubbleMath.MIN_SIZE_DP, glyph = "−") { resize(bigger = false) }
                AdjustChip(if (now.isPlaying) OctoIcons.Pause else OctoIcons.Play, if (now.isPlaying) "Pause" else "Play", enabled = true) { current.onPlayPause() }
                AdjustChip(OctoIcons.AddToLibrary, "Make the bubble bigger", enabled = sizeDp < BubbleMath.MAX_SIZE_DP) { resize(bigger = true) }
            }
        }
    }
}

// A round button in the bubble's adjust row. `glyph` draws a plain minus in
// place of the icon (there is no minus icon in the set).
@Composable
private fun AdjustChip(@DrawableRes icon: Int, description: String, enabled: Boolean, glyph: String? = null, onPress: () -> Unit) {
    Box(
        Modifier
            .size(AdjustChipSize)
            .background(Color(ForestGlass.PlayerBar), CircleShape)
            .border(1.dp, Color(ForestGlass.GlassEdge), CircleShape)
            .pointerInput(enabled) { if (enabled) detectTapGestures(onTap = { onPress() }) }
            .semantics {
                role = Role.Button
                contentDescription = description
                if (enabled) {
                    onClick {
                        onPress()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = Color(ForestGlass.Text).copy(alpha = if (enabled) 1f else 0.35f)
        if (glyph != null) {
            Box(Modifier.size(width = 16.dp, height = 2.dp).background(tint, CircleShape))
        } else {
            Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

private val AdjustChipSize = 44.dp
private val AdjustGap = 8.dp
private const val AdjustQuietMs = 5_000L

// The room the bubble moves in: inside the status and navigation bars and
// any camera cutout, but not the keyboard, so typing never shifts it.
private val BubbleInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
