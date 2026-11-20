package app.winters.octo.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeoutSeedTest {
    @Test
    fun historyArtistsMatchLibraryArtistsByName() {
        val seed = listOf(TakeoutArtist("Charley Crockett", 3325), TakeoutArtist("\$UICIDEBOY\$", 2049), TakeoutArtist("Nobody Here", 40))
        val library = listOf(ArtistRef("lib-2", "\$uicideboy\$"), ArtistRef("lib-1", "charley crockett"))
        val matched = matchTakeoutArtists(seed, library)
        // Matched ones seed chains under the library's id and name.
        assertEquals(
            listOf(ArtistRef("lib-1", "charley crockett") to 3325, ArtistRef("lib-2", "\$uicideboy\$") to 2049, ArtistRef("", "Nobody Here") to 40),
            matched,
        )
    }

    @Test
    fun oneArtistSpelledTwoWaysCountsOnce() {
        val matched = matchTakeoutArtists(listOf(TakeoutArtist("Bones", 3000), TakeoutArtist("BONES", 882)), emptyList())
        assertEquals(listOf(ArtistRef("", "Bones") to 3882), matched)
    }

    @Test
    fun aTopHistoryArtistOutranksAWeakInAppSignalAndTheTailStillRegisters() {
        val signals = SeedSignals(
            plays = listOf(ref("Weak") to 3),
            likedArtists = listOf(ref("Liked")),
            history = listOf(ArtistRef("lib-Bones", "Bones") to 3882, ArtistRef("", "Tail Artist") to 32),
        )
        val seeds = weighSeeds(signals, limit = 10)
        assertEquals(listOf("Bones", "Tail Artist", "Weak", "Liked"), seeds.map { it.artist.name })
        // The history's tail still sits above every like-or-rating signal put together.
        val tail = seeds.single { it.artist.name == "Tail Artist" }.weight
        assertTrue(tail > SeedWeights.LIKED_ARTIST + SeedWeights.LIKED_SONGS_MOST + SeedWeights.RATED_MOST)
        // About as much as a strongly played in-app artist, not more.
        val inApp = weighSeeds(SeedSignals(plays = listOf(ref("Daily") to 600))).single().weight
        assertTrue(seeds.first().weight < inApp)
    }

    @Test
    fun historyAndInAppPlaysAddUpForOneArtist() {
        val both = weighSeeds(SeedSignals(plays = listOf(ref("Caamp") to 50), history = listOf(ArtistRef("", "Caamp") to 1361))).single()
        // The library's id wins over the history's empty one.
        assertEquals("lib-Caamp", both.artist.id)
        assertEquals(SeedWeights.PLAYS * kotlin.math.ln(51.0) + SeedWeights.HISTORY * kotlin.math.ln(1362.0), both.weight, 1e-9)
    }

    @Test
    fun historySongsMatchLibrarySongsMostPlayedFirst() {
        val library = listOf(
            song("lib:1", "Keep the Blues Away", "Caamp"),
            song("lib:2", "Matte Black", "\$uicideboy\$"),
            song("lib:3", "Burn, Burn, Burn", "Zach Bryan"),
            song("lib:4", "Matte Black", "Someone Else"),
        )
        val seed = listOf(
            TakeoutTrack("Keep the Blues Away", "Caamp", 82),
            TakeoutTrack("Matte Black", "\$UICIDEBOY\$", 87),
            TakeoutTrack("Burn Burn Burn", "Zach Bryan", 78),
            TakeoutTrack("Not In The Library", "Caamp", 90),
        )
        assertEquals(listOf("lib:2", "lib:1", "lib:3"), matchTakeoutTracks(seed, library).map { it.id })
    }

    @Test
    fun aHistorySongCreditingAGuestStillFindsTheLibrarySong() {
        val library = listOf(song("lib:1", "Song X", "Artist"))
        assertEquals(listOf("lib:1"), matchTakeoutTracks(listOf(TakeoutTrack("Song X", "Artist feat. Guest", 10)), library).map { it.id })
    }

    @Test
    fun eachLibrarySongComesOnce() {
        val library = listOf(song("lib:1", "Same", "Band"))
        val seed = listOf(TakeoutTrack("Same", "Band", 10), TakeoutTrack("Same (Live)", "Band", 5))
        assertEquals(listOf("lib:1"), matchTakeoutTracks(seed, library).map { it.id })
    }

    @Test
    fun theHistoryPlaylistNeedsEightMatchedSongs() {
        val library = (1..10).map { song("lib:$it", "Song $it", "Band $it") }
        fun seed(n: Int) = (1..n).map { TakeoutTrack("Song $it", "Band $it", 100 - it) }
        val seven = matchTakeoutTracks(seed(7), library)
        assertEquals(7, seven.size)
        assertTrue(playlistCandidates(PlaylistInput(history = seven), 0).none { it.theme == "history" })
        val eight = matchTakeoutTracks(seed(8), library)
        val playlist = playlistCandidates(PlaylistInput(history = eight), 0).single { it.theme == "history" }
        assertEquals(eight, playlist.songs)
        assertEquals("From your listening history", playlist.title)
    }
}
