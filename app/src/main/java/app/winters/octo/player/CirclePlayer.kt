package app.winters.octo.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import app.winters.octo.animated.LoopPlay
import app.winters.octo.animated.LoopVideo
import app.winters.octo.catalog.isFind
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoType
import app.winters.octo.design.bodyType
import app.winters.octo.playback.NowPlaying
import app.winters.octo.player.immersive.accentInk
import app.winters.octo.player.immersive.mutedInk
import app.winters.octo.subsonic.AnimatedClip
import app.winters.octo.theme.ForestGlass
import app.winters.octo.ui.common.AddToLibraryButton
import app.winters.octo.ui.common.Artwork
import app.winters.octo.ui.common.AxisDrag
import app.winters.octo.ui.common.LocalOwnedFinds
import app.winters.octo.ui.common.LocalPressSpot
import app.winters.octo.ui.common.RatingStars
import app.winters.octo.ui.common.SongTitle
import app.winters.octo.ui.common.SwipeSkip
import app.winters.octo.ui.common.asClock
import app.winters.octo.ui.common.detectAxisDrags
import app.winters.octo.ui.common.fadingMarquee
import app.winters.octo.ui.common.pressSpot
import app.winters.octo.ui.common.swipeSkip
import app.winters.octo.ui.menu.LocalSongMenu
import app.winters.octo.ui.menu.SongMenuContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Music Space's full player, upright, for a song (playerLayoutFor): the
// album's name small at the top between the close chevron and the song's
// menu; the song's name and artist centred; the artwork as a large circle
// with the song's progress as a ring round it, which is also where to seek;
// the times at the ring's two ends; then shuffle, back, a big round play,
// next and the heart; then the switches (lyrics, up next, the sleep timer,
// repeat) and the volume line. Everything the upright player can do is
// here: the speed mark and the quality label sit under the ring, between
// its ends, and Cast, Sound and the rest stay in the song's menu.
//
// In lyrics mode (`folded`) it folds away like the upright player: the
// circle fades, and the blocks fade to `shown` and take no touches, all
// staying laid out so the artwork can still fly back to the bubble.
@Composable
internal fun AnimatedVisibilityScope.CirclePlayer(
    now: NowPlaying,
    model: PlayerViewModel,
    calm: Boolean,
    folded: Boolean,
    shown: () -> Float,
    artModifier: Modifier,
    onClose: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenSpeed: () -> Unit,
) {
    val look = risesIn()
        .graphicsLayer { alpha = shown() }
        .then(if (folded) Modifier.clearAndSetSemantics { }.pointerInput(Unit) { swallowTouches() } else Modifier)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The player keeps side margins; the circle is sized from the
        // screen's whole width, as the reference measures it.
        val fit = CircleSize.fit((maxWidth + PlayerSideMargin * 2).value, maxHeight.value)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircleTopLine(now, model, onClose, onOpenAlbum)
            Spacer(Modifier.weight(1f))
            Box(look) { CircleTitle(now, calm || folded, onOpenArtist, onClose, model) }
            Spacer(Modifier.height(12.dp))
            CircleStage(now, model, fit.artDp.dp, folded, calm, onOpenSpeed, artModifier)
            Spacer(Modifier.weight(1f))
            Box(look) { CircleControls(now, model) }
            Spacer(Modifier.height(8.dp))
            Box(look) { CircleSwitches(now, model, onOpenQueue, onOpenSleep) }
            if (fit.volume) {
                Spacer(Modifier.height(8.dp))
                Box(look) { Volume(model) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// The line across the top: the close chevron, the album's name (which
// opens the album, closing the player on the way), and the song's menu.
// The player still closes on back and by pulling it down, as before.
@Composable
private fun CircleTopLine(now: NowPlaying, model: PlayerViewModel, onClose: () -> Unit, onOpenAlbum: (String) -> Unit) {
    val menu = LocalSongMenu.current
    Row(Modifier.fillMaxWidth().height(TopLineHeight), verticalAlignment = Alignment.CenterVertically) {
        TopIcon(OctoIcons.Collapse, CLOSE_PLAYER, onClose)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val album by model.albumLabel.collectAsStateWithLifecycle()
            album?.let {
                val albumId = now.albumId?.takeIf { id -> id.isNotEmpty() && now.trackId?.let(::isFind) != true }
                Text(
                    it,
                    style = OctoType.caption,
                    color = mutedInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable(
                            enabled = albumId != null,
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "Open album",
                        ) { albumId?.let(onOpenAlbum) }
                        .closesPlayer(onClose)
                        .padding(vertical = 8.dp),
                )
            }
        }
        Box(
            Modifier
                .size(TopLineHeight)
                // The song's menu floats beside this button.
                .pressSpot(LocalPressSpot.current)
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    now.trackId?.let { menu.open(it, SongMenuContext(fromPlayer = true)) }
                }
                .semantics { contentDescription = "More" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(OctoIcons.More), contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TopIcon(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(TopLineHeight)
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(28.dp))
    }
}

// The song's name and artist, centred, and the rating line under them kept
// the same height whether it has one or not, so the circle keeps its place
// from song to song. A long name scrolls through (unless `calm`).
@Composable
private fun CircleTitle(now: NowPlaying, calm: Boolean, onOpenArtist: (String) -> Unit, onClose: () -> Unit, model: PlayerViewModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = now,
            contentKey = { it.trackId },
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "circle song title",
        ) { song ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SongTitle(
                    song.title.orEmpty(),
                    explicit = song.explicit,
                    style = TitleStyle,
                    color = LocalContentColor.current,
                    markColor = accentInk,
                    titleModifier = Modifier.fadingMarquee(song.trackId, scroll = !calm).closesPlayer(onClose),
                    overflow = TextOverflow.Clip,
                )
                Text(
                    song.starting ?: song.artist.orEmpty(),
                    style = bodyType(OctoType.body),
                    color = LocalContentColor.current.copy(alpha = ArtistLineAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable(
                        enabled = !song.artistId.isNullOrEmpty(),
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                    ) { song.artistId?.takeIf { it.isNotEmpty() }?.let(onOpenArtist) },
                )
            }
        }
        Box(Modifier.padding(top = 2.dp).height(12.dp)) {
            val rating by model.rating.collectAsStateWithLifecycle()
            RatingStars(rating, size = 12.dp)
        }
    }
}

// The circle: the artwork, the ring round it, the times at the ring's ends
// and, between them under the artwork, the quality label and the speed.
// Touching the ring seeks to where it is touched, and dragging round it
// moves through the song, landing where the finger lifts. Touching the
// artwork itself swipes it sideways to skip, as before.
@Composable
private fun CircleStage(
    now: NowPlaying,
    model: PlayerViewModel,
    art: Dp,
    folded: Boolean,
    calm: Boolean,
    onOpenSpeed: () -> Unit,
    artModifier: Modifier,
) {
    val side = art + (Ring.ROOM_DP * 2).dp
    val prefs by model.prefs.collectAsStateWithLifecycle()
    val fold by animateFloatAsState(if (folded) 1f else 0f, if (calm) snap() else tween(300), label = "circle folded")
    val radiusDp = art / 2 + Ring.ART_GAP_DP.dp
    // The arc moves on each step a pixel of the ring's length takes.
    val ringLength = with(LocalDensity.current) { (radiusDp * (2 * PI * Ring.SWEEP_DEGREES / 360f).toFloat()).toPx() }
    val position = rememberPositionMs(now, model::positionMs, lengthPx = ringLength)
    val duration = now.durationMs.coerceAtLeast(0)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction = { if (dragging) dragFraction else now.fractionAt(position.longValue) }
    val seek: (Float) -> Unit by rememberUpdatedState<(Float) -> Unit>({ f ->
        val target = (f.coerceIn(0f, 1f) * now.durationMs).toLong()
        position.longValue = target
        model.seekTo(target)
    })
    val haptics = LocalHapticFeedback.current
    // Only the times recompose, once a second (or as a drag moves them).
    val shownSeconds by remember(duration) {
        derivedStateOf {
            val ms = if (dragging) (dragFraction * duration).toLong() else position.longValue
            (ms.coerceIn(0, duration) / 1000).toInt()
        }
    }
    val ringColor = Color(ForestGlass.Text)
    Box(
        Modifier
            .size(width = side, height = side + RingTimesRoom)
            .graphicsLayer { alpha = 1f - fold },
    ) {
        Box(
            Modifier
                .size(side)
                .pointerInput(folded, duration) {
                    if (folded || duration <= 0) return@pointerInput
                    val radius = radiusDp.toPx()
                    val inside = Ring.TOUCH_IN_DP.dp.toPx()
                    val outside = Ring.TOUCH_OUT_DP.dp.toPx()
                    awaitEachGesture {
                        // Claimed before the artwork (and the pull that closes
                        // the player) see it, but only when it lands on the ring.
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        if (!Ring.onRing(down.position.x, down.position.y, cx, cy, radius, inside, outside)) return@awaitEachGesture
                        down.consume()
                        var f = Ring.fractionAt(down.position.x, down.position.y, cx, cy)
                        dragFraction = f
                        dragging = true
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    seek(f)
                                    break
                                }
                                f = Ring.dragFraction(f, Ring.fractionAt(change.position.x, change.position.y, cx, cy))
                                dragFraction = f
                                change.consume()
                            }
                        } finally {
                            dragging = false
                        }
                    }
                }
                .semantics {
                    contentDescription = "Song position"
                    // Read once a second, with the times, not on every step.
                    val heard = if (duration > 0) (shownSeconds * 1000f / duration).coerceIn(0f, 1f) else 0f
                    progressBarRangeInfo = ProgressBarRangeInfo(heard, 0f..1f)
                    setProgress { target ->
                        seek(target)
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The ring: a faint full track and the played arc in cream, with
            // a dot at the arc's end to take hold of.
            Canvas(Modifier.matchParentSize().graphicsLayer()) {
                val stroke = Ring.STROKE_DP.dp.toPx()
                val r = radiusDp.toPx()
                val topLeft = Offset(center.x - r, center.y - r)
                val arc = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                drawArc(ringColor.copy(alpha = 0.22f), Ring.START_DEGREES, Ring.SWEEP_DEGREES, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
                val f = fraction()
                val sweep = Ring.sweepFor(f)
                if (sweep > 0f) {
                    drawArc(ringColor, Ring.START_DEGREES, sweep, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                val end = Math.toRadians((Ring.START_DEGREES + sweep).toDouble())
                val dot = if (dragging) 9.dp.toPx() else 6.dp.toPx()
                drawCircle(ringColor, dot, Offset(center.x + r * cos(end).toFloat(), center.y + r * sin(end).toFloat()))
            }
            CircleArtSwipe(now, model, art, folded, artModifier)
        }
        // The times at the ring's two ends: played on the left, the whole
        // length on the right.
        val times = OctoType.caption.copy(fontFeatureSettings = "tnum")
        val half = side / 2
        val endX = radiusDp * cos(Math.toRadians(Ring.START_DEGREES.toDouble())).toFloat()
        val endY = half + radiusDp * sin(Math.toRadians(Ring.START_DEGREES.toDouble())).toFloat() + 8.dp
        Text(shownSeconds.asClock(), style = times, color = mutedInk, modifier = Modifier.centredAt(half + endX, endY))
        Text((duration / 1000).toInt().asClock(), style = times, color = mutedInk, modifier = Modifier.centredAt(half - endX, endY))
        // Under the artwork, between the ring's ends.
        Row(
            Modifier.centredAt(half, half + radiusDp + 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            now.quality?.let { QualityBadge(it, Modifier) }
            if (prefs.speed != 1f) SpeedMark(prefs.speed, onOpenSpeed)
        }
    }
}

// The room under the circle for the times and the quality label.
// (Named apart from LivePlayerParts' TimesRoom, which is a different 24.dp.)
private val RingTimesRoom = 16.dp

// Places what it is given with its top centre at (x, y) from the top left
// of its parent, whatever its own size.
private fun Modifier.centredAt(x: Dp, y: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx()) }
}

// The circular artwork, swiped sideways to skip as the upright player's is:
// it follows the finger a little way, and a tick marks the point past which
// letting go skips.
@Composable
private fun CircleArtSwipe(now: NowPlaying, model: PlayerViewModel, art: Dp, folded: Boolean, artModifier: Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var swipe by remember { mutableFloatStateOf(0f) }
    val loop = LocalPlayerLoop.current
    BoxWithConstraints(Modifier.size(art), contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth.toFloat()
        Box(
            Modifier
                .pointerInput(folded, width) {
                    if (folded) return@pointerInput
                    val distance = 88.dp.toPx()
                    val fling = 900.dp.toPx()
                    var armed = false
                    var settling: Job? = null
                    detectAxisDrags(
                        horizontal = AxisDrag(
                            onMove = { offset ->
                                settling?.cancel()
                                swipe = offset * 0.55f
                                val past = abs(offset) >= distance
                                if (past != armed) {
                                    armed = past
                                    if (past) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                }
                            },
                            onEnd = { offset, velocity ->
                                val skip = swipeSkip(offset, velocity, distance, fling)
                                if (skip != SwipeSkip.Stay && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                armed = false
                                settling = scope.launch { settleSwipe(skip, width, { swipe }, { swipe = it }, model::next, model::previous) }
                            },
                        ),
                    )
                }
                .graphicsLayer {
                    translationX = swipe
                    alpha = (1f - 0.5f * (abs(swipe) / (width * 0.5f)).coerceAtMost(1f)) * (1f - loop.coverAway())
                },
        ) {
            CircleArt(now.artwork, art, now.isPlaying, artModifier, loop.square, loop.play)
        }
    }
}

// The artwork clipped to a circle. It sits full size while music plays and
// settles back a little when paused. With no artwork it is the app's own
// empty cover with a note in it, never a hole.
@Composable
private fun CircleArt(
    ref: String?,
    side: Dp,
    playing: Boolean,
    artModifier: Modifier,
    loop: AnimatedClip?,
    loopPlay: LoopPlay,
) {
    val scale by animateFloatAsState(if (playing) 1f else 0.94f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "circle art size")
    Box(artModifier.size(side), contentAlignment = Alignment.Center) {
        Box(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            contentAlignment = Alignment.Center,
        ) {
            LoopVideo(loop, loopPlay, Modifier.size(side).clip(CircleShape)) {
                Artwork(ref, side, shape = CircleShape, keptFirst = true, rim = loop == null)
                if (ref == null) {
                    Icon(
                        painterResource(OctoIcons.Songs),
                        contentDescription = null,
                        tint = Color(ForestGlass.Dim).copy(alpha = 0.6f),
                        modifier = Modifier.size(side * 0.3f),
                    )
                }
            }
        }
    }
}

// Shuffle, back, the big round play, next, and the heart (or the add button
// for a song not in the library), on the same calls as the upright
// player's.
@Composable
private fun CircleControls(now: NowPlaying, model: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(icon = OctoIcons.Shuffle, description = "Shuffle", on = now.shuffle, onClick = model::toggleShuffle)
        TransportButton(OctoIcons.Previous, "Previous", 40.dp, model::previous, nudge = (-8).dp)
        BigPlayButton(now.isPlaying, model::togglePlayPause)
        TransportButton(OctoIcons.Next, "Next", 40.dp, model::next, nudge = 8.dp)
        HeartOrAdd(now, model)
    }
}

// The heart for Liked songs, or, for a song not in the library that has
// not been brought in, the add button: as beside the upright player's name.
@Composable
private fun HeartOrAdd(now: NowPlaying, model: PlayerViewModel) {
    if (now.trackId != null && isFind(now.trackId) && now.trackId !in LocalOwnedFinds.current) {
        val find by model.find.collectAsStateWithLifecycle()
        find?.takeIf { it.id == now.trackId }?.let { AddToLibraryButton(it, size = TitleButtonSize, iconSize = 24.dp) }
            ?: Spacer(Modifier.size(TitleButtonSize))
    } else {
        LikeButton(model)
    }
}

// The big round play button: a filled light circle with the play or pause
// sign in dark ink, giving a little under a press.
@Composable
private fun BigPlayButton(playing: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.45f, 600f), label = "big play press")
    Box(
        Modifier
            .size(BigPlaySize)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .background(Color(ForestGlass.Text), CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (playing) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = if (playing) OctoIcons.Pause else OctoIcons.Play,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
            label = "big play icon",
        ) { icon ->
            Icon(painterResource(icon), contentDescription = null, tint = Color(ForestGlass.Bg), modifier = Modifier.size(36.dp))
        }
    }
}

private val BigPlaySize = 72.dp

// The slim row of switches: lyrics, up next, the sleep timer and repeat.
// Shuffle is in the row above.
@Composable
private fun CircleSwitches(now: NowPlaying, model: PlayerViewModel, onOpenQueue: () -> Unit, onOpenSleep: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
    ) {
        ActionButton(icon = OctoIcons.Lyrics, description = "Lyrics", on = false, state = "Hidden", onClick = model::toggleLyrics)
        ActionButton(icon = OctoIcons.Queue, description = "Up next", on = false, onClick = onOpenQueue)
        SleepCircle(model, onClick = onOpenSleep)
        ActionButton(
            icon = if (now.repeatMode == Player.REPEAT_MODE_ONE) OctoIcons.RepeatOne else OctoIcons.Repeat,
            description = "Repeat",
            on = now.repeatMode != Player.REPEAT_MODE_OFF,
            state = when (now.repeatMode) {
                Player.REPEAT_MODE_ALL -> "All songs"
                Player.REPEAT_MODE_ONE -> "This song"
                else -> "Off"
            },
            onClick = model::cycleRepeat,
        )
    }
}

// The player's background under Music Space: the artwork's colours as a
// smooth vertical gradient, darkened for white words (CircleBackdrop), or
// the forest page with no artwork. It eases from one song's to the next.
@Composable
internal fun CircleBackground(colors: PlayerColors, hasArt: Boolean, modifier: Modifier = Modifier) {
    val stops = if (!hasArt || colors == PlayerColors.Quiet) {
        CircleBackdrop.Forest
    } else {
        CircleBackdrop.stops(colors.mesh.getOrNull(0)?.toArgbInt(), colors.mesh.getOrNull(2)?.toArgbInt(), colors.base.toArgbInt())
    }
    val top by animateColorAsState(Color(stops[0]), tween(600), label = "circle top")
    val middle by animateColorAsState(Color(stops[1]), tween(600), label = "circle middle")
    val bottom by animateColorAsState(Color(stops[2]), tween(600), label = "circle bottom")
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, middle, bottom))))
}

private fun Color.toArgbInt(): Int {
    fun byte(v: Float) = (v * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (byte(red) shl 16) or (byte(green) shl 8) or byte(blue)
}
