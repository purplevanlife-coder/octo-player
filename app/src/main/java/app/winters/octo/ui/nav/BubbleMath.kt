package app.winters.octo.ui.nav

import kotlin.math.max

// Where the free-floating now-playing bubble sits and how big it is, under
// Music Space. The place is kept as fractions of the room the bubble can
// move in (0 at the left or top edge, 1 at the right or bottom), so turning
// the phone or a different screen keeps it in the same part of the screen;
// the size is in dp.
data class BubblePlace(
    val x: Float = BubbleMath.DEFAULT_X,
    val y: Float = BubbleMath.DEFAULT_Y,
    val sizeDp: Float = BubbleMath.DEFAULT_SIZE_DP,
) {
    // The same place with every value made sane: fractions in 0..1, the size
    // within its bounds. A stored value from anywhere goes through this.
    fun sane(): BubblePlace = BubblePlace(BubbleMath.clampFraction(x), BubbleMath.clampFraction(y), BubbleMath.clampSize(sizeDp))
}

object BubbleMath {
    // Small enough to stay out of the way while still a finger's target with
    // its art recognisable; large enough to read across a room. The default
    // is about the bar's round button.
    const val MIN_SIZE_DP = 48f
    const val MAX_SIZE_DP = 128f
    const val DEFAULT_SIZE_DP = 60f

    // One press of the bigger or smaller button.
    const val STEP_DP = 12f

    // How far from the screen's safe edges it always stays.
    const val MARGIN_DP = 12f

    // First run: the right edge, low down, where the bar's round button sits
    // in the other themes.
    const val DEFAULT_X = 1f
    const val DEFAULT_Y = 1f

    fun clampSize(dp: Float): Float = if (dp.isNaN()) DEFAULT_SIZE_DP else dp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP)

    fun clampFraction(f: Float): Float = if (f.isNaN()) 1f else f.coerceIn(0f, 1f)

    // The room along one axis the bubble's corner can travel: the whole
    // length less the bubble and a margin at each end. Never below 0.
    fun travel(area: Float, size: Float, margin: Float): Float = max(0f, area - size - 2 * margin)

    // The bubble's corner, along one axis, for a fraction of its travel.
    fun offsetOf(fraction: Float, area: Float, size: Float, margin: Float): Float =
        margin + clampFraction(fraction) * travel(area, size, margin)

    // The fraction of its travel a corner at `offset` is at. With no room to
    // travel (a bubble as big as the screen) it is the start.
    fun fractionOf(offset: Float, area: Float, size: Float, margin: Float): Float {
        val room = travel(area, size, margin)
        if (room <= 0f) return 0f
        return clampFraction((offset - margin) / room)
    }

    // A corner dragged to `offset`, kept on screen with its margin.
    fun clampOffset(offset: Float, area: Float, size: Float, margin: Float): Float =
        if (offset.isNaN()) margin else offset.coerceIn(margin, margin + travel(area, size, margin))

    // A bigger or smaller bubble, by one step, within its bounds.
    fun stepped(sizeDp: Float, bigger: Boolean): Float = clampSize(sizeDp + if (bigger) STEP_DP else -STEP_DP)

    // Resized about its centre: the corner that keeps the bubble's centre
    // where it was, then kept on screen.
    fun resizedOffset(offset: Float, oldSize: Float, newSize: Float, area: Float, margin: Float): Float =
        clampOffset(offset + (oldSize - newSize) / 2f, area, newSize, margin)
}

// The names the bubble's place and size are stored under, in one place.
object BubbleKeys {
    const val STORE = "now_playing_bubble"
    const val X = "bubble_x"
    const val Y = "bubble_y"
    const val SIZE = "bubble_size_dp"
}
