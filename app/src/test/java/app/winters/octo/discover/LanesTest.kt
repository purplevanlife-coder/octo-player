package app.winters.octo.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanesTest {
    @Test
    fun eachLaneFromItsTag() {
        mapOf(
            "Country" to DiscoverLane.Country,
            "Folk" to DiscoverLane.Folk,
            "Americana" to DiscoverLane.Folk,
            "Bluegrass" to DiscoverLane.Bluegrass,
            "Blues" to DiscoverLane.Blues,
            "Indie" to DiscoverLane.Indie,
            "Alternative" to DiscoverLane.Indie,
            "Rock" to DiscoverLane.Rock,
            "Punk" to DiscoverLane.Rock,
            "Phonk" to DiscoverLane.Phonk,
            "Hip-Hop" to DiscoverLane.HipHop,
            "Rap" to DiscoverLane.HipHop,
            "Electronic" to DiscoverLane.Electronic,
            "House" to DiscoverLane.Electronic,
            "Soul" to DiscoverLane.Soul,
            "R&B" to DiscoverLane.Soul,
            "Jazz" to DiscoverLane.Jazz,
            "Oldies" to DiscoverLane.Oldies,
            "Latin" to DiscoverLane.Latin,
            "Reggaeton" to DiscoverLane.Latin,
            "Reggae" to DiscoverLane.Reggae,
            "Dub" to DiscoverLane.Reggae,
        ).forEach { (tag, lane) -> assertEquals(tag, lane, laneOf(tag, null)) }
    }

    @Test
    fun theMostSpecificLaneWins() {
        assertEquals(DiscoverLane.Country, laneOf("Country Rock", null))
        assertEquals(DiscoverLane.Indie, laneOf("Indie Rock", null))
        assertEquals(DiscoverLane.Folk, laneOf("Folk Rock", null))
        assertEquals(DiscoverLane.Blues, laneOf("Blues Rock", null))
        assertEquals(DiscoverLane.Bluegrass, laneOf("Progressive Bluegrass", null))
        assertEquals(DiscoverLane.Phonk, laneOf("Drift Phonk", null))
        assertEquals(DiscoverLane.Oldies, laneOf("Rock & Roll", null))
    }

    @Test
    fun tagsMatchWholeWords() {
        // "dub" is Reggae, but Dubstep is Electronic.
        assertEquals(DiscoverLane.Electronic, laneOf("Dubstep", null))
        // "rap" is not inside "Trapeze", nor "rock" inside "Rockabilly"'s lane.
        assertNull(laneOf("Trapeze", null))
        assertEquals(DiscoverLane.Oldies, laneOf("Rockabilly", null))
    }

    @Test
    fun oldSongsWithNoClearerLaneAreOldies() {
        assertEquals(DiscoverLane.Oldies, laneOf("Rock", 1965))
        assertEquals(DiscoverLane.Oldies, laneOf(null, 1958))
        assertEquals(DiscoverLane.Oldies, laneOf("Soul", 1971))
        // A lane of its own stays its own, however old.
        assertEquals(DiscoverLane.Blues, laneOf("Blues", 1962))
        assertEquals(DiscoverLane.Jazz, laneOf("Jazz", 1959))
        assertEquals(DiscoverLane.Rock, laneOf("Rock", 1994))
    }

    @Test
    fun noLaneForWhatFitsNone() {
        assertNull(laneOf("Pop", 2020))
        assertNull(laneOf(null, null))
        assertNull(laneOf("", 2001))
    }
}
