package app.winters.octo.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

// The now-playing bubble stays on screen wherever it is dragged or however
// it is sized, and its kept place means the same part of any screen.
class BubbleMathTest {
    private val d = 1e-4f

    @Test
    fun theSizeStaysWithinItsBounds() {
        assertEquals(48f, BubbleMath.clampSize(10f), d)
        assertEquals(128f, BubbleMath.clampSize(400f), d)
        assertEquals(72f, BubbleMath.clampSize(72f), d)
        assertEquals(BubbleMath.DEFAULT_SIZE_DP, BubbleMath.clampSize(Float.NaN), d)
        // The default is about the bar's round button, and inside the bounds.
        assertEquals(BubbleMath.DEFAULT_SIZE_DP, BubbleMath.clampSize(BubbleMath.DEFAULT_SIZE_DP), d)
        assertEquals(true, BubbleMath.DEFAULT_SIZE_DP in 56f..64f)
    }

    @Test
    fun steppingStopsAtTheEnds() {
        assertEquals(72f, BubbleMath.stepped(60f, bigger = true), d)
        assertEquals(48f, BubbleMath.stepped(60f, bigger = false), d)
        assertEquals(48f, BubbleMath.stepped(48f, bigger = false), d)
        assertEquals(128f, BubbleMath.stepped(124f, bigger = true), d)
    }

    @Test
    fun aDraggedBubbleStaysOnScreenWithItsMargin() {
        // A 400 px wide room, a 60 px bubble, 12 px margins: the corner goes
        // from 12 to 328.
        assertEquals(12f, BubbleMath.clampOffset(-200f, 400f, 60f, 12f), d)
        assertEquals(328f, BubbleMath.clampOffset(900f, 400f, 60f, 12f), d)
        assertEquals(100f, BubbleMath.clampOffset(100f, 400f, 60f, 12f), d)
        assertEquals(12f, BubbleMath.clampOffset(Float.NaN, 400f, 60f, 12f), d)
    }

    @Test
    fun fractionsAndOffsetsRoundTrip() {
        for (f in listOf(0f, 0.25f, 0.5f, 0.9f, 1f)) {
            val offset = BubbleMath.offsetOf(f, 1080f, 160f, 30f)
            assertEquals(f, BubbleMath.fractionOf(offset, 1080f, 160f, 30f), d)
        }
        assertEquals(30f, BubbleMath.offsetOf(0f, 1080f, 160f, 30f), d)
        assertEquals(1080f - 160f - 30f, BubbleMath.offsetOf(1f, 1080f, 160f, 30f), d)
        // Out of range fractions are held at the ends.
        assertEquals(BubbleMath.offsetOf(1f, 1080f, 160f, 30f), BubbleMath.offsetOf(3f, 1080f, 160f, 30f), d)
    }

    @Test
    fun turningThePhoneKeepsTheBubbleInTheSamePartOfTheScreen() {
        // Dropped at the bottom right of a tall screen...
        val tall = BubbleMath.fractionOf(BubbleMath.clampOffset(10_000f, 1080f, 160f, 30f), 1080f, 160f, 30f)
        assertEquals(1f, tall, d)
        // ...it is at the right of a wide one too, still fully on screen.
        val wide = BubbleMath.offsetOf(tall, 2400f, 160f, 30f)
        assertEquals(2400f - 160f - 30f, wide, d)
    }

    @Test
    fun withNoRoomToTravelTheBubbleSitsAtItsMargin() {
        assertEquals(0f, BubbleMath.travel(100f, 120f, 10f), d)
        assertEquals(10f, BubbleMath.offsetOf(0.7f, 100f, 120f, 10f), d)
        assertEquals(0f, BubbleMath.fractionOf(50f, 100f, 120f, 10f), d)
        assertEquals(10f, BubbleMath.clampOffset(50f, 100f, 120f, 10f), d)
    }

    @Test
    fun resizingKeepsTheCentreUntilAnEdgeStopsIt() {
        // Growing from 60 to 84 at corner 100 moves the corner back 12.
        assertEquals(88f, BubbleMath.resizedOffset(100f, 60f, 84f, 400f, 12f), d)
        // At the right edge, growing keeps it on screen.
        val atEdge = BubbleMath.clampOffset(900f, 400f, 60f, 12f)
        assertEquals(400f - 12f - 128f, BubbleMath.resizedOffset(atEdge, 60f, 128f, 400f, 12f), d)
    }

    @Test
    fun aStoredPlaceIsMadeSane() {
        assertEquals(BubblePlace(1f, 0f, 128f), BubblePlace(4f, -2f, 900f).sane())
        assertEquals(BubblePlace(1f, 1f, BubbleMath.DEFAULT_SIZE_DP), BubblePlace(Float.NaN, Float.NaN, Float.NaN).sane())
        // First run: the right edge, low down, at the default size.
        assertEquals(BubblePlace(1f, 1f, BubbleMath.DEFAULT_SIZE_DP), BubblePlace())
    }

    @Test
    fun theKeysAreDistinctAndNamedOnce() {
        val keys = listOf(BubbleKeys.X, BubbleKeys.Y, BubbleKeys.SIZE)
        assertEquals(keys.size, keys.toSet().size)
        assertNotEquals("player", BubbleKeys.STORE)
        assertNotEquals("discover", BubbleKeys.STORE)
    }
}
