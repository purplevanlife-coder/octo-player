package app.winters.octo.discover

import app.winters.octo.catalog.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistsTest {
    // Six seeds, each leading to fifteen artists with two songs apiece, the
    // artists' genres turning through all fourteen lanes; a few library
    // songs among them; forty history songs and a rediscover pool.
    private val lanes = listOf(
        "Country", "Folk", "Bluegrass", "Blues", "Indie", "Rock", "Phonk",
        "Hip-Hop", "Electronic", "Soul", "Jazz", "Oldies", "Latin", "Reggae",
    )
    private val seeds = (1..6).map { "Seed $it" }

    private val picks: List<Pick> = seeds.flatMapIndexed { s, seed ->
        (1..15).flatMap { a ->
            val artist = "S${s + 1} Artist $a"
            val genre = lanes[(s * 15 + a) % lanes.size]
            (1..2).map { n ->
                val score = 100.0 - a * 2 - n
                if (a == 15) owned(n, artist, genre, score).copy(via = seed) else find(n, artist, genre, score).copy(via = seed)
            }
        }
    }

    private val history: List<TrackEntity> = (1..40).map { song("lib:h$it", "History $it", "Old Favourite ${it % 10}") }
    private val rediscover: List<TrackEntity> = (1..24).map { song("lib:r$it", "Other $it", "Top Artist ${it % 4}") }

    private val input = PlaylistInput(
        picks = picks,
        seeds = seeds,
        seedKeys = seeds.mapTo(HashSet(), ::artistKey),
        history = history,
        rediscover = rediscover,
    )

    private fun themes(generation: Long, previous: Set<String> = emptySet()) =
        buildPlaylists(input, generation, previous).mapTo(HashSet()) { themeOfKey(it.key)!! }

    @Test
    fun theFixturesMakeMorePlaylistsThanABatchShows() {
        val candidates = playlistCandidates(input, 0)
        assertTrue("template pool ${candidates.size}", candidates.size > PLAYLISTS_SHOWN)
        assertTrue(candidates.all { it.songs.size >= PLAYLIST_MIN })
        // Every kind is there.
        assertEquals(PlaylistFamily.entries.toSet(), candidates.mapTo(HashSet()) { it.family })
    }

    @Test
    fun noBatchHoldsTwoPlaylistsBasedOnTheSameMusicianOrGenre() {
        for (generation in 0L..7L) {
            val chosen = choosePlaylists(playlistCandidates(input, generation), generation)
            // Every pair's claims are disjoint: no shared musician, genre
            // or template between any two playlists in the batch.
            for ((i, a) in chosen.withIndex()) {
                for (b in chosen.drop(i + 1)) {
                    assertTrue("${a.theme} meets ${b.theme} in generation $generation", a.claims.intersect(b.claims).isEmpty())
                }
            }
            // In particular: a seed's Because and Deep cuts never share a
            // batch, and no lane finds list shares its genre with a mood
            // mix in the batch.
            val themes = chosen.mapTo(HashSet()) { it.theme }
            for (seed in seeds) {
                val key = artistKey(seed)
                assertFalse("generation $generation", "because:$key" in themes && "deep:$key" in themes)
            }
            val moodGenres = chosen.filter { it.family == PlaylistFamily.Mood }.flatMap { it.claims }.filter { it.startsWith("genre:") }.toSet()
            val laneGenres = chosen.filter { it.family == PlaylistFamily.Lane }.flatMap { it.claims }.filter { it.startsWith("genre:") }.toSet()
            assertTrue("generation $generation", moodGenres.intersect(laneGenres).isEmpty())
        }
    }

    @Test
    fun theHistoryPlaylistsDominantArtistBlocksThatArtistsBecause() {
        // The history's songs are mostly one of the seeds: that seed's
        // Because playlist exists among the candidates, but the history
        // playlist claims the artist first, so it cannot share the batch.
        val mostlySeedOne = (1..16).map { song("lib:hs$it", "His $it", "Seed 1") } +
            (1..8).map { song("lib:ho$it", "Other $it", "Someone Else $it") }
        val withHistory = input.copy(history = mostlySeedOne)
        val candidates = playlistCandidates(withHistory, 0)
        assertTrue(candidates.any { it.theme == "because:${artistKey("Seed 1")}" })
        val chosen = choosePlaylists(candidates, 0).mapTo(HashSet()) { it.theme }
        assertTrue("history" in chosen)
        assertFalse("because:${artistKey("Seed 1")}" in chosen)
    }

    @Test
    fun aBatchShowsTwentyPlaylistsEachWithEnoughSongs() {
        for (generation in 0L..7L) {
            val playlists = buildPlaylists(input, generation)
            assertEquals(PLAYLISTS_SHOWN, playlists.size)
            assertTrue(playlists.all { it.kind == SectionKind.Playlist && it.songs.size >= PLAYLIST_MIN && it.songs.size <= HISTORY_PLAYLIST_SIZE })
            assertTrue(playlists.all { it.subtitle != null && it.key.endsWith("#g$generation") })
            assertEquals("keys are unique", playlists.size, playlists.map { it.key }.toSet().size)
        }
    }

    @Test
    fun theNextGenerationNeverRepeatsTheSetJustShown() {
        for (generation in 0L..20L) {
            assertNotEquals("generation $generation → ${generation + 1}", themes(generation), themes(generation + 1))
        }
        // And so the keys differ too.
        val before = buildPlaylists(input, 4).map { it.key }.toSet()
        val after = buildPlaylists(input, 5).map { it.key }.toSet()
        assertTrue(before.intersect(after).isEmpty())
    }

    @Test
    fun whatWasShownLastIsAvoidedEvenWhenTheRingWouldLandOnItAgain() {
        // Asked to differ from exactly what this generation would show, it moves on.
        val wouldShow = themes(3)
        assertNotEquals(wouldShow, themes(3, previous = wouldShow))
    }

    @Test
    fun theSameInputsAndGenerationMakeTheSameBatch() {
        assertEquals(buildPlaylists(input, 6), buildPlaylists(input, 6))
        assertEquals(buildPlaylists(input, 0), buildPlaylists(input.copy(picks = input.picks.reversed()), 0))
    }

    @Test
    fun theListeningHistoryHeadsEveryGeneration() {
        for (generation in 0L..5L) {
            val first = buildPlaylists(input, generation).first()
            assertEquals("history", themeOfKey(first.key))
            assertEquals("From your listening history", first.title)
        }
    }

    @Test
    fun theSeedsWhoHeadlineTurnWithTheGeneration() {
        val headliners = (0L..5L).map { g -> buildPlaylists(input, g).firstOrNull { themeOfKey(it.key)!!.startsWith("because:") }?.title }
        assertTrue("headliners $headliners", headliners.filterNotNull().toSet().size > 1)
    }

    @Test
    fun songsInsideAPlaylistTurnWithTheGeneration() {
        val pool = (1..60).map { song("lib:p$it", "Pool $it", "Artist $it") }
        val g0 = turned(pool, 2, PLAYLIST_SIZE, 0).map { it.id }
        val g1 = turned(pool, 2, PLAYLIST_SIZE, 1).map { it.id }
        assertEquals(PLAYLIST_SIZE, g0.size)
        assertEquals((1..20).map { "lib:p$it" }, g0)
        assertNotEquals(g0.toSet(), g1.toSet())
        // A pool no bigger than the playlist keeps its songs and order.
        val small = pool.take(12)
        assertEquals(small, turned(small, 2, PLAYLIST_SIZE, 5))
    }

    @Test
    fun aPlaylistNeedsEightSongs() {
        val seven = PlaylistInput(history = history.take(7))
        assertTrue(playlistCandidates(seven, 0).none { it.theme == "history" })
        val eight = PlaylistInput(history = history.take(8))
        assertEquals(listOf("history"), playlistCandidates(eight, 0).map { it.theme })
        // A lane with seven songs is no playlist; one with eight is.
        val lane = { n: Int -> PlaylistInput(picks = (1..n).map { find(it, "Bluegrass Band $it", genre = "Bluegrass") }) }
        assertTrue(playlistCandidates(lane(7), 0).none { it.theme == "lane:Bluegrass" })
        assertTrue(playlistCandidates(lane(8), 0).any { it.theme == "lane:Bluegrass" })
    }

    @Test
    fun withFewerPlaylistsThanSlotsAllOfThemShow() {
        val few = PlaylistInput(history = history, rediscover = rediscover)
        assertEquals(setOf("history", "rediscover"), buildPlaylists(few, 0).mapTo(HashSet()) { themeOfKey(it.key)!! })
        assertEquals(buildPlaylists(few, 0).map { it.title }, buildPlaylists(few, 1).map { it.title })
    }

    @Test
    fun turnedDownPicksAndSeedsOwnSongsStayOut() {
        val turnedDown = picks.first().track
        val dismissed = Dismissed(songIds = setOf(turnedDown.id), artistKeys = setOf(artistKey("S2 Artist 1")))
        val withSeedSong = input.copy(
            picks = input.picks + find(1, "Seed 1", genre = "Country").copy(via = "Seed 1"),
            dismissed = dismissed,
        )
        val songs = (0L..5L).flatMap { g -> buildPlaylists(withSeedSong, g).flatMap { it.songs } }
        assertFalse(songs.any { it.id == turnedDown.id })
        assertFalse(songs.any { it.artist == "S2 Artist 1" })
        assertFalse(songs.any { it.artist == "Seed 1" })
    }

    @Test
    fun becauseYouPlayHoldsOnlyWhatCameThroughThatSeed() {
        val because = playlistCandidates(input, 0).first { it.theme == "because:${artistKey("Seed 3")}" }
        assertEquals("Because you play Seed 3", because.title)
        assertTrue(because.songs.all { it.artist.startsWith("S3 ") })
        // A seed known only from the listening history says so.
        val fromHistory = playlistCandidates(input.copy(historySeeds = setOf(artistKey("Seed 3"))), 0).first { it.theme == because.theme }
        assertEquals("Artists like Seed 3, from your listening history", fromHistory.subtitle)
        // Deep cuts take what Because you play left.
        val deep = playlistCandidates(input, 0).first { it.theme == "deep:${artistKey("Seed 3")}" }
        assertTrue(deep.songs.none { it in because.songs })
    }

    @Test
    fun theHistoryPlaylistKeepsPlayOrder() {
        val songs = playlistCandidates(PlaylistInput(history = history), 0).single().songs
        val order = history.map { it.id }
        assertEquals(songs.map { it.id }.sortedBy(order::indexOf), songs.map { it.id })
    }

    @Test
    fun keysNameTheThemeAndGeneration() {
        assertEquals("pl:lane:Country#g7", playlistKey("lane:Country", 7))
        assertEquals("lane:Country", themeOfKey("pl:lane:Country#g7"))
        assertEquals(null, themeOfKey("for-you"))
        val batch = DiscoverBatch(buildPlaylists(input, 2) + DiscoverSection("for-you", SectionKind.ForYou, "For You"), 0, 2)
        assertEquals(themes(2), batch.playlistThemes())
    }
}
