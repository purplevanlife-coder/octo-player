package app.winters.octo.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedWeightsTest {
    @Test
    fun eachSignalAloneRanksByItsTier() {
        // One of each: a play, a recent play, a favourite artist, a liked
        // song, a five-star song. Play totals weigh most, then recent plays,
        // then favourites, then the songs' own marks.
        val seeds = weighSeeds(
            SeedSignals(
                plays = listOf(ref("Played") to 1),
                recent = listOf(ref("Recent")),
                likedArtists = listOf(ref("Favourite")),
                likedSongs = listOf(ref("Liked song")),
                rated = listOf(ref("Rated") to 5),
            ),
        )
        assertEquals(listOf("Played", "Recent", "Favourite", "Rated", "Liked song"), seeds.map { it.artist.name })
    }

    @Test
    fun signalsAddUpAndManyPlaysWin() {
        val seeds = weighSeeds(
            SeedSignals(
                plays = listOf(ref("Heavy") to 40, ref("Light") to 2),
                recent = listOf(ref("Light"), ref("Light"), ref("Light")),
                likedArtists = listOf(ref("Light"), ref("Loved")),
                likedSongs = List(10) { ref("Loved") },
                rated = listOf(ref("Loved") to 5, ref("Loved") to 5),
            ),
        )
        assertEquals(listOf("Heavy", "Light", "Loved"), seeds.map { it.artist.name })
        // Loved: favourite 1.5 + liked songs capped at 1.5 + two five-star songs 2.0.
        assertEquals(5.0, seeds.single { it.artist.name == "Loved" }.weight, 1e-9)
    }

    @Test
    fun oneArtistByNameWhateverTheSignalCalledThem() {
        val seeds = weighSeeds(
            SeedSignals(
                plays = listOf(ArtistRef("", "Radiohead") to 3),
                recent = listOf(ArtistRef("lib-radiohead", "radiohead")),
            ),
        )
        assertEquals(1, seeds.size)
        // The first id that is not empty is kept, to open the artist by.
        assertEquals("lib-radiohead", seeds.single().artist.id)
    }

    @Test
    fun noOneInParticularNeverSeeds() {
        val seeds = weighSeeds(
            SeedSignals(plays = listOf(ref("Various Artists") to 50, ref("Unknown Artist") to 50, ref("") to 50, ref("Real") to 1)),
        )
        assertEquals(listOf("Real"), seeds.map { it.artist.name })
    }

    @Test
    fun lowRatingsAndZeroPlaysCountForNothing() {
        val seeds = weighSeeds(SeedSignals(plays = listOf(ref("Zero") to 0), rated = listOf(ref("Meh") to 3, ref("Bad") to 1)))
        assertTrue(seeds.isEmpty())
    }

    @Test
    fun atMostTheLimitHeaviestFirstThenByName() {
        val seeds = weighSeeds(SeedSignals(plays = (1..12).map { ref("A$it") to 5 } + listOf(ref("Top") to 99)), limit = 4)
        assertEquals(4, seeds.size)
        assertEquals("Top", seeds.first().artist.name)
        // Equal weights fall back to the name, so the order never wobbles.
        assertEquals(listOf("A1", "A10", "A11"), seeds.drop(1).map { it.artist.name })
    }
}
