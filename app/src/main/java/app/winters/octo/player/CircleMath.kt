package app.winters.octo.player

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// The circle player's arithmetic, kept pure so it can be pinned in tests:
// the progress ring's geometry and its angle-to-fraction seek, the size the
// circle takes on a screen, and the background gradient grown from the
// artwork's colours.

// The ring is open at the bottom, like the reference: it starts at the
// bottom left, runs up over the top and down to the bottom right. Angles are
// in degrees as Compose draws them: 0 at three o'clock, growing clockwise.
object Ring {
    // The open part at the bottom, centred on six o'clock, where the times
    // and the quality label sit.
    const val GAP_DEGREES = 60f

    // Where the ring starts (bottom left: the elapsed time's end) and how
    // far it runs (to the bottom right: the length's end).
    const val START_DEGREES = 90f + GAP_DEGREES / 2f
    const val SWEEP_DEGREES = 360f - GAP_DEGREES

    // How far in from the ring's line, and out past it, a touch still
    // counts as on the ring, in dp. In is kept short of the artwork's edge,
    // which keeps its own swipe to skip.
    const val TOUCH_IN_DP = 10f
    const val TOUCH_OUT_DP = 24f

    // The room from the artwork's edge to the ring's line, and from there to
    // the edge of the circle's box, in dp; and the ring's thickness.
    const val ART_GAP_DP = 12f
    const val OUTER_DP = 10f
    const val STROKE_DP = 4f

    // From the artwork's edge to the box's edge: the room the ring takes
    // either side of the artwork.
    const val ROOM_DP = ART_GAP_DP + OUTER_DP

    // A drag that jumps more than this much of the ring in one move has
    // crossed the open gap at the bottom; it holds at the end it came from.
    const val WRAP_JUMP = 0.5f

    // The angle of a point (dx, dy) from the centre, in [0, 360).
    fun angleOf(dx: Float, dy: Float): Float {
        val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return (degrees % 360f + 360f) % 360f
    }

    // How far round the ring an angle is: 0 at its start, 1 at its end.
    // An angle in the open gap goes to the nearer end.
    fun fractionAtAngle(degrees: Float): Float {
        val along = ((degrees - START_DEGREES) % 360f + 360f) % 360f
        if (along <= SWEEP_DEGREES) return along / SWEEP_DEGREES
        val pastEnd = along - SWEEP_DEGREES
        return if (pastEnd <= GAP_DEGREES / 2f) 1f else 0f
    }

    // The fraction for a touch at (x, y) on a ring centred at (cx, cy).
    fun fractionAt(x: Float, y: Float, cx: Float, cy: Float): Float = fractionAtAngle(angleOf(x - cx, y - cy))

    // Whether a touch at distance `distance` from the centre is on a ring of
    // radius `radius`: no more than `inside` in from its line, nor more than
    // `outside` out past it.
    fun onRing(distance: Float, radius: Float, inside: Float, outside: Float): Boolean =
        distance >= radius - inside && distance <= radius + outside

    fun onRing(x: Float, y: Float, cx: Float, cy: Float, radius: Float, inside: Float, outside: Float): Boolean =
        onRing(hypot(x - cx, y - cy), radius, inside, outside)

    // The fraction while dragging: where the finger is, unless that jumped
    // across the open gap from one end to the other, which holds the end the
    // drag was nearest.
    fun dragFraction(previous: Float, raw: Float): Float =
        if (abs(raw - previous) > WRAP_JUMP) (if (previous >= 0.5f) 1f else 0f) else raw

    // The angle the progress arc sweeps for a fraction of the song.
    fun sweepFor(fraction: Float): Float = SWEEP_DEGREES * fraction.coerceIn(0f, 1f)
}

// How big the circle's artwork is on a screen, and whether the volume line
// still fits under it. The ring takes Ring.ROOM_DP more either side.
data class CircleFit(val artDp: Float, val volume: Boolean) {
    // The artwork with its ring round it.
    val sideDp: Float get() = artDp + 2 * Ring.ROOM_DP
}

object CircleSize {
    // About three quarters of the screen's width, as the reference draws it.
    const val WIDTH_SHARE = 0.74f

    // The room everything but the circle takes, upright: the top line, the
    // song's name, artist and rating line, the ring's room round the
    // artwork, the times under it, the controls, the switches and the gaps
    // between them; and the volume line's own room.
    const val FIXED_DP = 356f
    const val VOLUME_DP = 36f

    // Never smaller than this, so the artwork still reads.
    const val MIN_ART_DP = 140f

    // The artwork's diameter for a screen `screenWidthDp` wide with
    // `heightDp` of room upright.
    fun fit(screenWidthDp: Float, heightDp: Float): CircleFit {
        val wanted = min(screenWidthDp * WIDTH_SHARE, screenWidthDp - 2 * Ring.ROOM_DP - 8f)
        val withVolume = heightDp - FIXED_DP - VOLUME_DP
        if (withVolume >= wanted) return CircleFit(wanted, volume = true)
        // A short screen: the circle shrinks first, down to most of what it
        // wants, then the volume line goes (the phone's buttons still set it).
        if (withVolume >= wanted * 0.85f) return CircleFit(withVolume, volume = true)
        val without = heightDp - FIXED_DP
        return CircleFit(max(MIN_ART_DP, min(wanted, without)), volume = false)
    }
}

// The background behind the circle player: a smooth vertical gradient from
// the artwork's own colours, darkened and calmed until the white words and
// controls read on every part of it. With no artwork it is Music Space's
// forest page.
object CircleBackdrop {
    // No stop is lighter than this (relative luminance), which keeps white
    // at 4.6:1 or better over all of it: (1 + 0.05) / (0.18 + 0.05).
    const val MAX_LUMINANCE = 0.18

    // How much of their colourfulness the stops keep, so a loud cover
    // tints the player rather than shouting.
    const val KEEP_SATURATION = 0.7f

    // The forest page, top to bottom: the glass olive over the page,
    // the page (--bg) and a deeper green-black.
    val Forest: List<Int> = listOf(0xFF1E2819.toInt(), 0xFF131A10.toInt(), 0xFF0A0E08.toInt())

    // Top to bottom: the artwork's main colour, its deep one, then the base
    // that sits under everything, each made safe for white. `main`, `deep`
    // and `base` null (no artwork) gives the forest page.
    fun stops(main: Int?, deep: Int?, base: Int?): List<Int> {
        if (main == null) return Forest
        val top = calm(main, darken = 0.25f)
        val middle = calm(deep ?: main, darken = 0.45f)
        val bottom = calm(base ?: deep ?: main, darken = 0.7f)
        return listOf(top, middle, bottom)
    }

    // A colour desaturated a little, darkened toward black by `darken`, and
    // then darkened further until it is no lighter than MAX_LUMINANCE.
    fun calm(argb: Int, darken: Float): Int {
        var c = desaturate(argb, KEEP_SATURATION)
        c = blend(c, 0xFF000000.toInt(), darken)
        var steps = 0
        while (luminance(c) > MAX_LUMINANCE && steps < 40) {
            c = blend(c, 0xFF000000.toInt(), 0.08f)
            steps++
        }
        return c
    }

    // WCAG relative luminance of an opaque colour.
    fun luminance(argb: Int): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    // WCAG contrast between two opaque colours.
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    // `amount` of the way from `from` to `to`, opaque.
    fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val a = from shr shift and 0xFF
            val b = to shr shift and 0xFF
            return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    // The colour with only `keep` of its distance from its own grey.
    fun desaturate(argb: Int, keep: Float): Int {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        val grey = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255)
        val greyArgb = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
        return blend(greyArgb, (0xFF shl 24) or (r shl 16) or (g shl 8) or b, keep)
    }
}
