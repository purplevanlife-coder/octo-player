package app.winters.octo.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.withTimeoutOrNull
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

// The control's shape and size on screen, in pixels, as the gesture
// handler needs them: the room it moves in, its own footprint in the form
// it is in now, and the disc's width within it.
private data class BubbleGeo(
    val areaW: Float,
    val areaH: Float,
    val margin: Float,
    val widthPx: Float,
    val heightPx: Float,
    val discPx: Float,
    val pill: Boolean,
)

// Music Space's now-playing control, free of the bar: the round artwork
// with its progress ring (NowPlayingFace, as on the bar), floating over
// the pages wherever it was last left. A cream ring runs round it in
// every form, so it reads against light artwork and dark pages alike.
//
// - While a song plays it opens into a pill: the disc at the left (its
//   tap opens the full player) and Previous, Play/Pause and Next beside
//   it. Paused, or with nothing loaded, it is the round bubble again;
//   with nothing loaded it shows the play sign and a tap shuffles the
//   library, as the bar's round button does.
// - A drag on the disc moves it; it stays where it is dropped, kept on
//   screen with a margin, and the place is kept as fractions of the
//   screen. Dragging never opens the player or presses a button.
// - A long press on the disc opens its adjust row beside it: smaller,
//   play or pause, bigger. A tap on the disc (or a few quiet seconds)
//   closes the row.
//
// Every touch is read by ONE handler that feeds BubbleGestureMachine, and
// the adjust row's buttons are plain clicks with nothing spread over the
// screen in front of them. Phase 3 ran two detectors side by side plus a
// full-screen layer that swallowed every touch while the row was open —
// the layer ate the row's own buttons, so resizing never worked on a
// phone. Touches anywhere off the control reach the page under it
// (overlapping siblings do not share touches in Compose), so the pages
// still scroll and swipe round it.
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
    // The pill only while a song is actually sounding; the moment it
    // pauses, the control settles back to the round bubble.
    val pill = hasSong && now.isPlaying
    // What is shown: the kept place, or while it is being changed, what
    // the finger has made of it (so a drop never jumps back while it is
    // stored).
    val shownState = remember { mutableStateOf(place) }
    var shown by shownState
    LaunchedEffect(place) { shown = place }
    val dragState = remember { mutableStateOf<Offset?>(null) }
    var drag by dragState
    val adjustingState = remember { mutableStateOf(false) }
    var adjusting by adjustingState
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

    val sizeDp = BubbleMath.clampSize(shown.sizeDp)
    // The width breathes between the disc and the pill; the disc stays
    // anchored at the left, so the pill grows out of it to the right.
    val widthDp by animateDpAsState(
        BubbleMath.formWidthDp(sizeDp, pill).dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "nowPlayingWidth",
    )
    val shape: Shape = if (pill) RoundedCornerShape(percent = 50) else CircleShape

    BoxWithConstraints(modifier.fillMaxSize().windowInsetsPadding(BubbleInsets)) {
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        val margin = with(density) { BubbleMath.MARGIN_DP.dp.toPx() }
        val sizePx = with(density) { sizeDp.dp.toPx() }
        val widthPx = with(density) { widthDp.toPx() }
        val rest = Offset(
            BubbleMath.offsetOf(shown.x, areaW, widthPx, margin),
            BubbleMath.offsetOf(shown.y, areaH, sizePx, margin),
        )
        val corner = drag ?: rest
        val geo by rememberUpdatedState(BubbleGeo(areaW, areaH, margin, widthPx, sizePx, sizePx, pill))
        val latestRest by rememberUpdatedState(rest)
        val hasSongNow by rememberUpdatedState(hasSong)

        fun keep(next: BubblePlace) {
            shown = next
            current.onKeep(next)
        }

        fun resize(bigger: Boolean) {
            val newSize = BubbleMath.stepped(sizeDp, bigger)
            if (newSize == sizeDp) return
            val newPx = with(density) { newSize.dp.toPx() }
            val newWidth = with(density) { BubbleMath.formWidthDp(newSize, pill).dp.toPx() }
            val x = BubbleMath.resizedOffset(rest.x, widthPx, newWidth, areaW, margin)
            val y = BubbleMath.resizedOffset(rest.y, sizePx, newPx, areaH, margin)
            keep(BubblePlace(BubbleMath.fractionOf(x, areaW, newWidth, margin), BubbleMath.fractionOf(y, areaH, newPx, margin), newSize))
        }

        Box(
            Modifier
                .offset { IntOffset(corner.x.roundToInt(), corner.y.roundToInt()) }
                .width(widthDp)
                .height(sizeDp.dp)
                // The one touch handler. It reads the live geometry and
                // state through their holders, so it never restarts
                // mid-gesture when the pill breathes or the place lands.
                .pointerInput(Unit) {
                    val machine = BubbleGestureMachine(viewConfiguration.longPressTimeoutMillis, viewConfiguration.touchSlop)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val g = geo
                        val region = BubbleMath.regionAt(down.position.x, down.position.y, g.widthPx, g.heightPx, g.discPx, g.pill)
                        fun handle(events: List<BubbleGestureEvent>) {
                            for (event in events) {
                                when (event) {
                                    is BubbleGestureEvent.Tap -> when (event.region) {
                                        BubbleRegion.Art -> {
                                            if (adjustingState.value) {
                                                adjustingState.value = false
                                            } else if (hasSongNow) {
                                                current.onOpenPlayer()
                                            } else {
                                                current.onPlayPause()
                                            }
                                        }
                                        BubbleRegion.Previous -> current.onPrevious()
                                        BubbleRegion.PlayPause -> current.onPlayPause()
                                        BubbleRegion.Next -> current.onNext()
                                    }
                                    BubbleGestureEvent.LongPress -> if (hasSongNow) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        adjustingState.value = true
                                    }
                                    BubbleGestureEvent.DragStart -> dragState.value = latestRest
                                    is BubbleGestureEvent.Drag -> {
                                        val gg = geo
                                        val from = dragState.value ?: latestRest
                                        dragState.value = Offset(
                                            BubbleMath.clampOffset(from.x + event.dx, gg.areaW, gg.widthPx, gg.margin),
                                            BubbleMath.clampOffset(from.y + event.dy, gg.areaH, gg.heightPx, gg.margin),
                                        )
                                    }
                                    BubbleGestureEvent.DragEnd, BubbleGestureEvent.DragCancel -> {
                                        val gg = geo
                                        dragState.value?.let { dropped ->
                                            keep(
                                                shownState.value.copy(
                                                    x = BubbleMath.fractionOf(dropped.x, gg.areaW, gg.widthPx, gg.margin),
                                                    y = BubbleMath.fractionOf(dropped.y, gg.areaH, gg.heightPx, gg.margin),
                                                ),
                                            )
                                        }
                                        dragState.value = null
                                    }
                                }
                            }
                        }
                        handle(machine.down(region, down.position.x, down.position.y, down.uptimeMillis))
                        // Only a disc touch can long-press; a button touch
                        // just waits for its release.
                        var awaitingLongPress = region == BubbleRegion.Art
                        var lastMs = down.uptimeMillis
                        var done = false
                        while (!done) {
                            val event = if (awaitingLongPress) {
                                withTimeoutOrNull((down.uptimeMillis + viewConfiguration.longPressTimeoutMillis - lastMs).coerceAtLeast(1)) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                awaitingLongPress = false
                                handle(machine.timeout(down.uptimeMillis + viewConfiguration.longPressTimeoutMillis))
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            lastMs = change.uptimeMillis
                            // A change that is no longer pressed is the
                            // release (or the touch taken away): either
                            // way the machine settles the gesture.
                            if (!change.pressed) {
                                handle(machine.up(change.uptimeMillis))
                                done = true
                            } else {
                                handle(machine.move(change.position.x, change.position.y, change.uptimeMillis))
                            }
                            change.consume()
                        }
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
            BarGlass(haze, Modifier.matchParentSize(), shape = shape, topDocked = true) {
                Row(Modifier.matchParentSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
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
                    if (pill) {
                        val buttonWidth = BubbleMath.pillButtonDp(sizeDp).dp
                        PillButton(OctoIcons.Previous, "Previous", buttonWidth)
                        PillButton(OctoIcons.Pause, "Pause", buttonWidth)
                        PillButton(OctoIcons.Next, "Next", buttonWidth)
                    }
                }
            }
            // The ring that keeps it visible on any ground: cream, all the
            // way round, in whichever form it is in. While the adjust row
            // is open it turns sage, the theme's adjusting colour.
            Box(
                Modifier
                    .matchParentSize()
                    .border(2.dp, Color(if (adjusting) ForestGlass.Accent2 else ForestGlass.Text), shape),
            )
        }

        if (adjusting) {
            // The row beside the control: above it in the lower half of
            // the screen, under it in the upper half, held on screen, and
            // centred on the control as it lies now.
            val rowW = with(density) { (AdjustChipSize * 3 + AdjustGap * 2).toPx() }
            val rowH = with(density) { AdjustChipSize.toPx() }
            val gap = with(density) { AdjustGap.toPx() }
            val x = (corner.x + widthPx / 2 - rowW / 2).coerceIn(margin, (areaW - rowW - margin).coerceAtLeast(margin))
            val above = corner.y - gap - rowH
            val y = if (shown.y > 0.5f && above >= 0f) above else corner.y + sizePx + gap
            Row(
                Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) },
                horizontalArrangement = Arrangement.spacedBy(AdjustGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdjustChip(OctoIcons.Close, "Make the bubble smaller", enabled = sizeDp > BubbleMath.MIN_SIZE_DP, glyph = "−") { resize(bigger = false) }
                AdjustChip(if (now.isPlaying) OctoIcons.Pause else OctoIcons.Play, if (now.isPlaying) "Pause" else "Play", enabled = true) { current.onPlayPause() }
                AdjustChip(OctoIcons.AddToLibrary, "Make the bubble bigger", enabled = sizeDp < BubbleMath.MAX_SIZE_DP, glyph = null) { resize(bigger = true) }
            }
        }
    }
}

// One of the playing pill's transport buttons. It draws only: the
// control's one touch handler reads the region a touch lands in
// (BubbleMath.regionAt) and presses the button, so a press can never
// start a drag or open the player by accident.
@Composable
private fun PillButton(@DrawableRes icon: Int, description: String, width: Dp) {
    Box(Modifier.width(width).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = description, tint = Color(ForestGlass.Text), modifier = Modifier.size(26.dp))
    }
}

// A round button in the bubble's adjust row. `glyph` draws a plain minus in
// place of the icon (there is no minus icon in the set). A plain click —
// nothing is spread over the screen in front of it (Phase 3's swallowing
// layer is what left these dead on the phone).
@Composable
private fun AdjustChip(@DrawableRes icon: Int, description: String, enabled: Boolean, glyph: String? = null, onPress: () -> Unit) {
    Box(
        Modifier
            .size(AdjustChipSize)
            .background(Color(ForestGlass.PlayerBar), CircleShape)
            .border(1.dp, Color(ForestGlass.GlassEdge), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description) { onPress() },
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
