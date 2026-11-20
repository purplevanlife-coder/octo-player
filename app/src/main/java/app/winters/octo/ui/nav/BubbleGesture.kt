package app.winters.octo.ui.nav

import kotlin.math.hypot

// Which part of the floating now-playing control a touch is on: the
// artwork disc, or one of the playing pill's transport buttons.
enum class BubbleRegion { Art, Previous, PlayPause, Next }

// What one touch on the control turned into, as the machine reads it.
sealed interface BubbleGestureEvent {
    // A quick touch that never travelled: on the disc it opens the player
    // (or closes the adjust row); on a button it presses that button.
    data class Tap(val region: BubbleRegion) : BubbleGestureEvent

    // The disc held still past the long-press time: open the adjust row.
    data object LongPress : BubbleGestureEvent

    // The disc started to move: the control is being dragged.
    data object DragStart : BubbleGestureEvent

    // The drag moved by this much since the last move.
    data class Drag(val dx: Float, val dy: Float) : BubbleGestureEvent

    // The drag was let go: keep the control where it lies.
    data object DragEnd : BubbleGestureEvent

    // The drag was taken away mid-move: keep it where it lies, as a drop.
    data object DragCancel : BubbleGestureEvent
}

// One touch on the bubble or pill, made sense of. The control used to run
// two gesture detectors side by side (one for taps and long presses, one
// for drags) and, while its adjust row was open, a full-screen layer that
// swallowed every touch before the row's own buttons could read it — so
// the row's bigger and smaller buttons never fired on a phone, and a held
// finger that drifted a little became a drag and lost its long press.
// This machine replaces all of that: the control's single pointer handler
// feeds it downs, moves, the long-press moment and the release, and does
// exactly what it is told back. Pure, so the rules are tested as written:
//
// - A touch on a transport button can only ever be that button's tap: it
//   never drags the control and never long-presses, and a touch that
//   wanders off the button is nothing at all.
// - A touch on the disc is a tap if it is quick and stays put, a long
//   press if it is held still (once — holding longer does not repeat it,
//   and letting go after one is not a tap), and a drag once it travels
//   past the slop, whether or not a long press came first.
class BubbleGestureMachine(private val longPressMs: Long, private val slopPx: Float) {
    private enum class Phase { Idle, Down, LongPressed, Dragging, Dead }

    private var phase = Phase.Idle
    private var region = BubbleRegion.Art
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAtMs = 0L

    // A new touch, on `region`, at (`x`, `y`). Nothing has happened yet.
    fun down(region: BubbleRegion, x: Float, y: Float, atMs: Long): List<BubbleGestureEvent> {
        this.region = region
        downX = x
        downY = y
        lastX = x
        lastY = y
        downAtMs = atMs
        phase = Phase.Down
        return emptyList()
    }

    private fun travelled(x: Float, y: Float): Boolean = hypot(x - downX, y - downY) > slopPx

    // The finger moved to (`x`, `y`).
    fun move(x: Float, y: Float, atMs: Long): List<BubbleGestureEvent> {
        val out = when (phase) {
            Phase.Down, Phase.LongPressed -> when {
                !travelled(x, y) -> emptyList()
                region != BubbleRegion.Art -> {
                    // A button touch that wanders is spent, never a drag.
                    phase = Phase.Dead
                    emptyList()
                }
                else -> {
                    phase = Phase.Dragging
                    listOf(BubbleGestureEvent.DragStart, BubbleGestureEvent.Drag(x - lastX, y - lastY))
                }
            }
            Phase.Dragging -> listOf(BubbleGestureEvent.Drag(x - lastX, y - lastY))
            else -> emptyList()
        }
        lastX = x
        lastY = y
        return out
    }

    // The long-press moment arrived (the handler calls this when its wait
    // for the next pointer event times out at `downAtMs + longPressMs`).
    fun timeout(atMs: Long): List<BubbleGestureEvent> {
        if (phase != Phase.Down || region != BubbleRegion.Art || atMs - downAtMs < longPressMs) return emptyList()
        phase = Phase.LongPressed
        return listOf(BubbleGestureEvent.LongPress)
    }

    // The finger was let go.
    fun up(atMs: Long): List<BubbleGestureEvent> {
        val out = when (phase) {
            Phase.Down -> listOf(BubbleGestureEvent.Tap(region))
            Phase.Dragging -> listOf(BubbleGestureEvent.DragEnd)
            else -> emptyList()
        }
        phase = Phase.Idle
        return out
    }

    // The touch was taken away (another handler, a system gesture).
    fun cancel(): List<BubbleGestureEvent> {
        val out = if (phase == Phase.Dragging) listOf(BubbleGestureEvent.DragCancel) else emptyList()
        phase = Phase.Idle
        return out
    }
}
