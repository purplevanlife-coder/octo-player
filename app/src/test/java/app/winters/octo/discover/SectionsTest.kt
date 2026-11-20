package app.winters.octo.discover

import app.winters.octo.discovery.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionsTest {
    private val station = Station("st-1", "Daily Mix", null)
    private val list = Station("pl-1", "Deep Cuts", null, songCount = 30)

    @Test
    fun sectionsComeInTheMusicSpacesOrderAndEmptyOnesAreLeftOut() {
        val picks = (1..3).map { find(it, "Picker", genre = "Country") } + (1..3).map { find(it, "Jazzer", genre = "Jazz") }
        val sections = buildSections(
            SectionInput(
                picks = picks,
                artists = listOf(ChainCandidate(outside("Explorer"), 1.0, "Seed")),
                madeForYou = listOf(list),
                stations = listOf(station),
                charts = listOf(song("c1", "Hit", "Star")),
            ),
        )
        assertEquals(
            listOf("for-you", "artists", "lane:Country", "lane:Jazz", "made-for-you", "stations", "charts"),
            sections.map { it.key },
        )
        assertTrue(sections.none { it.isEmpty })
        // Nothing gathered: no sections at all.
        assertTrue(buildSections(SectionInput()).isEmpty())
    }

    @Test
    fun forYouPutsNewSongsFirstAndSpreadsArtists() {
        val picks = listOf(
            owned(1, "Owner", score = 100.0),
            find(1, "Alpha", score = 9.0),
            find(2, "Alpha", score = 8.0),
            find(3, "Alpha", score = 7.0),
            find(1, "Beta", score = 5.0),
        )
        val forYou = buildSections(SectionInput(picks = picks)).first { it.kind == SectionKind.ForYou }
        // Finds first, at most two by one artist, artists taking turns;
        // the library song, however strong, after them.
        assertEquals(
            listOf("find:Alpha-1", "find:Beta-1", "find:Alpha-2", "lib:Owner-1"),
            forYou.songs.map { it.id },
        )
    }

    @Test
    fun seedsOwnSongsAndDismissedPicksStayOut() {
        val picks = listOf(
            find(1, "The Seed"),
            find(1, "Fresh"),
            find(2, "Fresh"),
            find(1, "Unwanted"),
        )
        val dismissed = Dismissed(songIds = setOf("find:Fresh-2"), artistKeys = setOf(artistKey("Unwanted")))
        val sections = buildSections(SectionInput(picks = picks, seedKeys = setOf(artistKey("The Seed")), dismissed = dismissed))
        assertEquals(listOf("find:Fresh-1"), sections.single().songs.map { it.id })
    }

    @Test
    fun aSongDismissedByNameStaysOutUnderAnotherId() {
        val turnedDown = song("find:old-id", "Same Song", "Band")
        val again = Pick(song("find:new-id", "Same Song", "Band"), null, null, 1.0)
        val sections = buildSections(SectionInput(picks = listOf(again), dismissed = Dismissed(songKeys = setOf(songKey(turnedDown)))))
        assertTrue(sections.isEmpty())
    }

    @Test
    fun aSongFoundTwiceIsShownOnceAtItsStrongest() {
        val weak = Pick(song("find:a", "Twice", "Band"), null, null, 1.0)
        val strong = Pick(song("find:b", "Twice", "Band"), null, null, 5.0)
        val forYou = buildSections(SectionInput(picks = listOf(weak, strong))).single()
        assertEquals(listOf("find:b"), forYou.songs.map { it.id })
    }

    @Test
    fun aLaneNeedsEnoughSongsAndUsesTheServersGenreOrTheSongsOwn() {
        val picks = (1..2).map { find(it, "Few", genre = "Blues") } +
            (1..3).map { n -> Pick(song("lib:r$n", "Tagged $n", "Rocker", genre = "Rock"), null, null, 1.0) }
        val keys = buildSections(SectionInput(picks = picks)).map { it.key }
        assertFalse("two songs make no lane", "lane:Blues" in keys)
        assertTrue("the song's own genre places it", "lane:Rock" in keys)
    }

    @Test
    fun aLaneTakesAtMostThreeByOneArtist() {
        val picks = (1..6).map { find(it, "Prolific", genre = "Folk") } + find(1, "Other", genre = "Folk")
        val lane = buildSections(SectionInput(picks = picks)).single { it.key == "lane:Folk" }
        assertEquals(3, lane.songs.count { it.artist == "Prolific" })
        assertEquals(4, lane.songs.size)
    }

    @Test
    fun dismissedArtistsLeaveArtistsToExploreAndTheChart() {
        val sections = buildSections(
            SectionInput(
                artists = listOf(ChainCandidate(outside("Gone"), 2.0, "Seed"), ChainCandidate(outside("Stays"), 1.0, "Seed")),
                charts = listOf(song("c1", "Hit", "Gone"), song("c2", "Other hit", "Stays")),
                dismissed = Dismissed(artistKeys = setOf(artistKey("Gone"))),
            ),
        )
        assertEquals(listOf("Stays"), sections.single { it.kind == SectionKind.Artists }.artists.map { it.name })
        assertEquals(listOf("c2"), sections.single { it.kind == SectionKind.Charts }.songs.map { it.id })
    }

    @Test
    fun spreadTakesTurnsAndStopsAtTheSize() {
        val picks = listOf(find(1, "A"), find(2, "A"), find(3, "A"), find(1, "B"), find(1, "C"))
        assertEquals(
            listOf("find:A-1", "find:B-1", "find:C-1", "find:A-2"),
            spread(picks, perArtist = 2, size = 10).map { it.track.id },
        )
        assertEquals(2, spread(picks, perArtist = 2, size = 2).size)
    }
}
