package app.winters.octo.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The floating control's one touch handler, as its machine rules it: a
// button touch is only ever that button, a held disc is a long press
// exactly once, and anything that travels is a drag and never a tap. (On
// the phone the Phase 3 split detectors plus the adjust row's swallowing
// layer left the resize buttons dead; these are the rules that replace
// them.)
class BubbleGestureTest {
    private fun machine() = BubbleGestureMachine(longPressMs = 500, slopPx = 20f)

    @Test
    fun aQuickStillTouchOnTheDiscIsATap() {
        val m = machine()
        assertTrue(m.down(BubbleRegion.Art, 50f, 50f, 0).isEmpty())
        assertTrue(m.move(54f, 48f, 60).isEmpty())
        assertEquals(listOf(BubbleGestureEvent.Tap(BubbleRegion.Art)), m.up(120))
    }

    @Test
    fun aQuickStillTouchOnAButtonIsThatButtonsTap() {
        for (region in listOf(BubbleRegion.Previous, BubbleRegion.PlayPause, BubbleRegion.Next)) {
            val m = machine()
            m.down(region, 150f, 50f, 0)
            assertEquals(listOf(BubbleGestureEvent.Tap(region)), m.up(90))
        }
    }

    @Test
    fun aButtonTouchThatWandersIsNothingAndNeverDrags() {
        val m = machine()
        m.down(BubbleRegion.Next, 150f, 50f, 0)
        assertTrue(m.move(220f, 90f, 80).isEmpty())
        assertTrue(m.move(300f, 120f, 120).isEmpty())
        assertTrue(m.up(160).isEmpty())
    }

    @Test
    fun holdingTheDiscStillIsOneLongPressAndLettingGoIsNotATap() {
        val m = machine()
        m.down(BubbleRegion.Art, 50f, 50f, 0)
        assertTrue(m.timeout(300).isEmpty())
        assertEquals(listOf(BubbleGestureEvent.LongPress), m.timeout(500))
        // Holding longer does not fire it again.
        assertTrue(m.timeout(900).isEmpty())
        assertTrue(m.up(950).isEmpty())
    }

    @Test
    fun holdingAButtonNeverLongPresses() {
        val m = machine()
        m.down(BubbleRegion.PlayPause, 150f, 50f, 0)
        assertTrue(m.timeout(600).isEmpty())
        assertEquals(listOf(BubbleGestureEvent.Tap(BubbleRegion.PlayPause)), m.up(700))
    }

    @Test
    fun travellingPastTheSlopIsADragWhoseStepsAddUpAndNeverATap() {
        val m = machine()
        m.down(BubbleRegion.Art, 50f, 50f, 0)
        val first = m.move(90f, 50f, 50)
        assertEquals(BubbleGestureEvent.DragStart, first.first())
        val steps = first.filterIsInstance<BubbleGestureEvent.Drag>() + m.move(120f, 70f, 90).filterIsInstance<BubbleGestureEvent.Drag>()
        assertEquals(70f, steps.sumOf { it.dx.toDouble() }.toFloat(), 1e-4f)
        assertEquals(20f, steps.sumOf { it.dy.toDouble() }.toFloat(), 1e-4f)
        assertEquals(listOf(BubbleGestureEvent.DragEnd), m.up(140))
    }

    @Test
    fun aDragCanStillStartAfterALongPress() {
        val m = machine()
        m.down(BubbleRegion.Art, 50f, 50f, 0)
        assertEquals(listOf(BubbleGestureEvent.LongPress), m.timeout(500))
        val moved = m.move(100f, 50f, 560)
        assertEquals(BubbleGestureEvent.DragStart, moved.first())
        assertEquals(listOf(BubbleGestureEvent.DragEnd), m.up(700))
    }

    @Test
    fun aCancelledDragStillDropsWhereItLies() {
        val m = machine()
        m.down(BubbleRegion.Art, 50f, 50f, 0)
        m.move(120f, 50f, 50)
        assertEquals(listOf(BubbleGestureEvent.DragCancel), m.cancel())
        // And a cancelled tap is nothing.
        val m2 = machine()
        m2.down(BubbleRegion.Art, 50f, 50f, 0)
        assertTrue(m2.cancel().isEmpty())
    }

    @Test
    fun jitterWithinTheSlopThenReleaseIsStillATap() {
        val m = machine()
        m.down(BubbleRegion.Art, 50f, 50f, 0)
        assertTrue(m.move(60f, 58f, 40).isEmpty())
        assertTrue(m.move(44f, 55f, 80).isEmpty())
        assertEquals(listOf(BubbleGestureEvent.Tap(BubbleRegion.Art)), m.up(110))
    }

    @Test
    fun regionsSplitThePillAndReadTheDiscAloneInTheRoundForm() {
        // Round form: 60 px disc, everything is the disc.
        assertEquals(BubbleRegion.Art, BubbleMath.regionAt(59f, 30f, 60f, 60f, 60f, pill = false))
        // Pill form: 60 px disc + three 45 px buttons in a 195 px pill.
        assertEquals(BubbleRegion.Art, BubbleMath.regionAt(30f, 30f, 195f, 60f, 60f, pill = true))
        assertEquals(BubbleRegion.Previous, BubbleMath.regionAt(80f, 30f, 195f, 60f, 60f, pill = true))
        assertEquals(BubbleRegion.PlayPause, BubbleMath.regionAt(125f, 30f, 195f, 60f, 60f, pill = true))
        assertEquals(BubbleRegion.Next, BubbleMath.regionAt(170f, 30f, 195f, 60f, 60f, pill = true))
        // A touch off the pill's height reads as the disc, never a button.
        assertEquals(BubbleRegion.Art, BubbleMath.regionAt(170f, 90f, 195f, 60f, 60f, pill = true))
    }

    @Test
    fun thePillIsTheDiscPlusThreeButtons() {
        assertEquals(60f, BubbleMath.formWidthDp(60f, pill = false), 1e-4f)
        // Buttons are three quarters of the disc: 60 + 3 × 45.
        assertEquals(195f, BubbleMath.pillWidthDp(60f), 1e-4f)
        assertEquals(BubbleMath.pillWidthDp(60f), BubbleMath.formWidthDp(60f, pill = true), 1e-4f)
        // At the smallest disc the buttons hold at a finger's minimum...
        assertEquals(48f + 3 * 44f, BubbleMath.pillWidthDp(48f), 1e-4f)
        // ...and at the largest they stop growing before the disc does.
        assertEquals(128f + 3 * 64f, BubbleMath.pillWidthDp(128f), 1e-4f)
    }
}
