package app.winters.octo.ui.nav

import app.winters.octo.liveradio.LiveLineup
import app.winters.octo.liveradio.liveShown
import app.winters.octo.playback.LiveNow
import app.winters.octo.playback.NowPlaying
import app.winters.octo.playback.radioId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Music Space's bottom player bar draws its progress line for a song only:
// a station has no length to run along, and nothing loaded has nothing to show.
class PlayerBarTest {
    private val song = NowPlaying(trackId = "t1", title = "Nightcall", artist = "Kavinsky", durationMs = 258_000)

    @Test
    fun aSongShowsTheProgressLine() {
        assertTrue(playerBarShowsProgress(song))
        assertTrue(playerBarShowsProgress(song.copy(isPlaying = true, playWhenReady = true)))
        assertTrue(playerBarShowsProgress(song.copy(isPlaying = false, playWhenReady = false)))
    }

    @Test
    fun aLiveStationShowsNoLine() {
        val id = radioId("http://s/a", "A")
        val live = LiveNow(liveShown(id, null)!!, LiveLineup(listOf(id), 0), null)
        assertFalse(playerBarShowsProgress(NowPlaying(trackId = id, title = "A", live = live, playWhenReady = true)))
    }

    @Test
    fun nothingLoadedShowsNoLine() {
        assertFalse(playerBarShowsProgress(NowPlaying()))
    }
}
