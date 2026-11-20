package app.winters.octo.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TakeoutSeedParseTest {
    private val fixture = """
        {"version":1,"source":"listening history","dateRange":"2021 to 2026","totalPlays":100,"uniqueTracks":3,"uniqueArtists":3,
         "artists":[{"name":"Second","plays":20},{"name":"First","plays":70},{"name":"  ","plays":5},{"name":"Never","plays":0}],
         "tracks":[{"title":"Low","artist":"Second","plays":4},{"title":"High","artist":"First","plays":9},{"title":"","artist":"First","plays":3}]}
    """.trimIndent()

    @Test
    fun theSeedFileReadsMostPlayedFirstWithEmptyEntriesLeftOut() {
        val seed = parseTakeoutSeed(fixture)
        assertEquals(listOf(TakeoutArtist("First", 70), TakeoutArtist("Second", 20)), seed.artists)
        assertEquals(listOf(TakeoutTrack("High", "First", 9), TakeoutTrack("Low", "Second", 4)), seed.tracks)
        assertFalse(seed.isEmpty)
    }

    @Test
    fun aFileThatDoesNotReadIsNoSeed() {
        assertTrue(parseTakeoutSeed(null).isEmpty)
        assertTrue(parseTakeoutSeed("").isEmpty)
        assertTrue(parseTakeoutSeed("{not json").isEmpty)
        assertTrue(parseTakeoutSeed("[1, 2, 3]").isEmpty)
        assertTrue(parseTakeoutSeed("""{"artists":[{"name":"No plays field"}]}""").isEmpty)
        // A file with neither list is an empty seed too.
        assertTrue(parseTakeoutSeed("""{"version":1}""").isEmpty)
    }

    // The bundled file reads, and holds titles, artists and play counts only.
    @Test
    fun theBundledSeedReadsAndHoldsNoLinks() {
        val file = File(File(System.getProperty("octo.mainSources")!!).parentFile, "assets/$TAKEOUT_SEED_FILE")
        val text = file.readText()
        val seed = parseTakeoutSeed(text)
        assertEquals(1000, seed.artists.size)
        assertEquals(1000, seed.tracks.size)
        assertTrue(seed.artists.zipWithNext().all { (a, b) -> a.plays >= b.plays })
        assertFalse("no links in the seed", text.contains("http") || text.contains("youtube.com/watch") || text.contains("\"url\""))
    }
}
