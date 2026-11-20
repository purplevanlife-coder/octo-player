package app.winters.octo.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

// The circle player's ring seeks where it is touched, and its background
// stays dark enough for white words over any cover.
class CircleMathTest {
    private val delta = 1e-4f

    // A point on a ring of radius 100 round (0, 0) at `degrees`.
    private fun at(degrees: Float): Pair<Float, Float> {
        val r = Math.toRadians(degrees.toDouble())
        return (100 * cos(r)).toFloat() to (100 * sin(r)).toFloat()
    }

    private fun fractionAtDegrees(degrees: Float): Float {
        val (x, y) = at(degrees)
        return Ring.fractionAt(x, y, 0f, 0f)
    }

    @Test
    fun theRingRunsFromBottomLeftOverTheTopToBottomRight() {
        assertEquals(120f, Ring.START_DEGREES, delta)
        assertEquals(300f, Ring.SWEEP_DEGREES, delta)
        // Its start, bottom left, is the song's start.
        assertEquals(0f, fractionAtDegrees(120f), delta)
        // Nine o'clock: a fifth of the way (60 of 300 degrees).
        assertEquals(0.2f, fractionAtDegrees(180f), delta)
        // Twelve o'clock (270 degrees, y up is negative): half way.
        assertEquals(0.5f, fractionAtDegrees(270f), delta)
        // Three o'clock: four fifths.
        assertEquals(0.8f, fractionAtDegrees(0f), delta)
        // Its end, bottom right (60 degrees): the song's end.
        assertEquals(1f, fractionAtDegrees(60f), delta)
    }

    @Test
    fun aTouchInTheOpenGapGoesToTheNearerEnd() {
        // Just right of six o'clock is nearer the end; just left, the start.
        assertEquals(1f, fractionAtDegrees(80f), delta)
        assertEquals(1f, Ring.fractionAtAngle(90f), delta)
        assertEquals(0f, fractionAtDegrees(100f), delta)
    }

    @Test
    fun anglesWrapAndAreMeasuredClockwiseFromThreeOclock() {
        assertEquals(0f, Ring.angleOf(1f, 0f), delta)
        assertEquals(90f, Ring.angleOf(0f, 1f), delta)
        assertEquals(180f, Ring.angleOf(-1f, 0f), delta)
        assertEquals(270f, Ring.angleOf(0f, -1f), delta)
        assertEquals(Ring.fractionAtAngle(30f), Ring.fractionAtAngle(390f), delta)
        assertEquals(Ring.fractionAtAngle(200f), Ring.fractionAtAngle(-160f), delta)
    }

    @Test
    fun theFractionAlwaysStaysBetweenZeroAndOne() {
        var degrees = -720f
        while (degrees <= 720f) {
            val f = Ring.fractionAtAngle(degrees)
            assertTrue("$degrees -> $f", f in 0f..1f)
            degrees += 7.5f
        }
    }

    @Test
    fun onlyTouchesNearTheRingsLineCountAsOnIt() {
        assertTrue(Ring.onRing(distance = 100f, radius = 100f, inside = 10f, outside = 24f))
        assertTrue(Ring.onRing(distance = 123f, radius = 100f, inside = 10f, outside = 24f))
        assertTrue(Ring.onRing(distance = 91f, radius = 100f, inside = 10f, outside = 24f))
        // The artwork inside, and well outside the ring, are not.
        assertFalse(Ring.onRing(distance = 89f, radius = 100f, inside = 10f, outside = 24f))
        assertFalse(Ring.onRing(distance = 40f, radius = 100f, inside = 10f, outside = 24f))
        assertFalse(Ring.onRing(distance = 130f, radius = 100f, inside = 10f, outside = 24f))
        assertTrue(Ring.onRing(x = 100f, y = 100f, cx = 0f, cy = 100f, radius = 100f, inside = 5f, outside = 5f))
    }

    // The band reaching in never reaches the artwork, which keeps its own
    // swipe to skip.
    @Test
    fun theRingsTouchBandStopsShortOfTheArtwork() {
        assertTrue(Ring.TOUCH_IN_DP < Ring.ART_GAP_DP)
        assertEquals(Ring.ART_GAP_DP + Ring.OUTER_DP, Ring.ROOM_DP, delta)
    }

    @Test
    fun aDragAcrossTheGapHoldsTheEndItCameFrom() {
        // Near the end, the finger slips round the bottom to the start side.
        assertEquals(1f, Ring.dragFraction(previous = 0.97f, raw = 0.02f), delta)
        // Near the start, slipping the other way.
        assertEquals(0f, Ring.dragFraction(previous = 0.03f, raw = 0.99f), delta)
        // An ordinary move follows the finger.
        assertEquals(0.42f, Ring.dragFraction(previous = 0.4f, raw = 0.42f), delta)
    }

    @Test
    fun theArcSweepsItsShareOfTheRing() {
        assertEquals(0f, Ring.sweepFor(0f), delta)
        assertEquals(150f, Ring.sweepFor(0.5f), delta)
        assertEquals(300f, Ring.sweepFor(1f), delta)
        assertEquals(300f, Ring.sweepFor(1.4f), delta)
        assertEquals(0f, Ring.sweepFor(-0.2f), delta)
    }

    @Test
    fun theArtworkIsAboutThreeQuartersOfTheScreensWidthOnAPhone() {
        // A Pixel held upright: 412 dp wide, 840 dp of room.
        val fit = CircleSize.fit(screenWidthDp = 412f, heightDp = 840f)
        assertEquals(412f * 0.74f, fit.artDp, 0.01f)
        assertEquals(fit.artDp + 2 * Ring.ROOM_DP, fit.sideDp, 0.01f)
        assertTrue(fit.volume)
        // The ring still fits inside the screen.
        assertTrue(fit.sideDp < 412f)
    }

    @Test
    fun aShortScreenShrinksTheCircleThenDropsTheVolumeLine() {
        val slightly = CircleSize.fit(screenWidthDp = 412f, heightDp = 660f)
        assertTrue(slightly.volume)
        assertEquals(660f - CircleSize.FIXED_DP - CircleSize.VOLUME_DP, slightly.artDp, 0.01f)
        val short = CircleSize.fit(screenWidthDp = 412f, heightDp = 580f)
        assertFalse(short.volume)
        assertEquals(580f - CircleSize.FIXED_DP, short.artDp, 0.01f)
        // Never smaller than its floor.
        assertEquals(CircleSize.MIN_ART_DP, CircleSize.fit(screenWidthDp = 412f, heightDp = 300f).artDp, 0.01f)
        // A narrow screen keeps room for the ring either side.
        val narrow = CircleSize.fit(screenWidthDp = 200f, heightDp = 900f)
        assertTrue(narrow.sideDp <= 200f - 8f + 0.01f)
    }

    @Test
    fun withoutArtworkTheBackgroundIsTheForestPage() {
        assertEquals(CircleBackdrop.Forest, CircleBackdrop.stops(null, null, null))
        assertEquals(0xFF131A10.toInt(), CircleBackdrop.Forest[1])
    }

    @Test
    fun everyStopKeepsWhiteWordsReadableOverAnyCover() {
        val white = 0xFFFFFFFF.toInt()
        val covers = listOf(
            0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF00FFFF.toInt(), 0xFFFF4081.toInt(),
            0xFF8BC34A.toInt(), 0xFF000000.toInt(), 0xFF3F51B5.toInt(), 0xFFF5F5DC.toInt(),
        )
        for (main in covers) for (deep in covers) {
            val stops = CircleBackdrop.stops(main, deep, deep)
            assertEquals(3, stops.size)
            for (stop in stops) {
                assertTrue("${Integer.toHexString(main)} -> ${Integer.toHexString(stop)}", CircleBackdrop.contrast(white, stop) >= 4.5)
                assertEquals(0xFF, stop ushr 24)
            }
        }
        for (stop in CircleBackdrop.Forest) assertTrue(CircleBackdrop.contrast(white, stop) >= 4.5)
    }

    @Test
    fun theGradientStillCarriesTheCoversHue() {
        // A red cover gives a red-leaning top, not a grey one.
        val top = CircleBackdrop.stops(0xFFD03030.toInt(), null, null)[0]
        val r = top shr 16 and 0xFF
        val g = top shr 8 and 0xFF
        val b = top and 0xFF
        assertTrue("$r $g $b", r > g && r > b)
        // And it darkens toward the bottom.
        val stops = CircleBackdrop.stops(0xFFD03030.toInt(), 0xFF802020.toInt(), 0xFF301010.toInt())
        assertTrue(CircleBackdrop.luminance(stops[0]) >= CircleBackdrop.luminance(stops[2]))
    }

    @Test
    fun blendingAndGreyingAreExactAtTheirEnds() {
        val red = 0xFFFF0000.toInt()
        assertEquals(red, CircleBackdrop.blend(red, 0xFF000000.toInt(), 0f))
        assertEquals(0xFF000000.toInt(), CircleBackdrop.blend(red, 0xFF000000.toInt(), 1f))
        assertEquals(red, CircleBackdrop.desaturate(red, 1f))
        val grey = CircleBackdrop.desaturate(red, 0f)
        assertEquals(grey shr 16 and 0xFF, grey and 0xFF)
    }
}
