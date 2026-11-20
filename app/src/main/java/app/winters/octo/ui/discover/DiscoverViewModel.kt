package app.winters.octo.ui.discover

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.winters.octo.catalog.TrackEntity
import app.winters.octo.data.SessionRepository
import app.winters.octo.data.SessionState
import app.winters.octo.discover.DiscoverArtist
import app.winters.octo.discover.DiscoverBatch
import app.winters.octo.discover.DiscoverRepository
import app.winters.octo.discover.DiscoverSection
import app.winters.octo.discover.SectionKind
import app.winters.octo.discover.has
import app.winters.octo.discover.themeOfKey
import app.winters.octo.discover.Dismissed
import app.winters.octo.discover.OwnedBatch
import app.winters.octo.discover.without
import app.winters.octo.discovery.Station
import app.winters.octo.discovery.isRadio
import app.winters.octo.playback.ListSpec
import app.winters.octo.playback.NOTHING_TO_PLAY
import app.winters.octo.playback.OnlineKind
import app.winters.octo.playback.PlaybackConnection
import app.winters.octo.playback.QueueSource
import app.winters.octo.playback.Shown
import app.winters.octo.playback.encoded
import app.winters.octo.ui.common.Feedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

// Where the playing of Discover's songs says it came from.
internal const val DISCOVER_SOURCE = "Discover"

// How long a pull waits for its batch to start being made.
private const val PULL_START_WAIT_MS = 1_000L

// What the Discover tab shows.
sealed interface DiscoverUi {
    // No server signed in: nothing to discover from.
    data object SignedOut : DiscoverUi

    // Signed in, the kept batch not read yet (only for a moment at start).
    data object Reading : DiscoverUi

    // Signed in, with no batch made yet: the first one is on its way
    // (`making`) or could not be made.
    data class NoBatch(val making: Boolean) : DiscoverUi

    // A batch, with the picks turned down since it was made left out.
    data class Ready(val batch: DiscoverBatch, val making: Boolean) : DiscoverUi
}

// The playlist the detail page shows for `key`: the batch's own, as shown
// (turned-down picks already out), or, once a new batch has landed without
// it (another generation's keys) or its last song was turned down, the copy
// last seen there, `last`, without what has been turned down since, so
// the page never vanishes under the listener. Null with no page open, or
// nothing to show for it.
internal fun playlistShown(key: String?, shown: DiscoverBatch?, last: DiscoverSection?, dismissed: Dismissed): DiscoverSection? {
    if (key == null) return null
    shown?.sections?.firstOrNull { it.key == key && it.kind == SectionKind.Playlist }?.let { return it }
    return last?.takeIf { it.key == key }?.let { it.copy(songs = it.songs.filterNot(dismissed::has)) }
}

// The line under Discover's updated line saying what its picks are seeded
// by: the listening history only when a playlist actually came from it.
internal fun seededLine(batch: DiscoverBatch): String? =
    if (batch.sections.any { it.kind == SectionKind.Playlist && themeOfKey(it.key) == "history" }) "Seeded by your library and listening history" else null

// What the tab shows for what the repository holds now. A batch kept for
// another server than the one in use is not shown.
internal fun discoverUi(owner: String?, kept: OwnedBatch?, dismissed: Dismissed, making: Boolean): DiscoverUi = when {
    owner == null -> DiscoverUi.SignedOut
    kept == null || kept.owner != owner -> DiscoverUi.Reading
    kept.batch == null -> DiscoverUi.NoBatch(making)
    else -> DiscoverUi.Ready(kept.batch.without(dismissed), making)
}

// The Discover tab. Its state starts from what the repository already read
// from disk at app start, so the tab's first frame has the batch, never a
// blank or a loading flash; it changes when a new batch lands or a pick is
// turned down.
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val repository: DiscoverRepository,
    private val playback: PlaybackConnection,
    private val feedback: Feedback,
    sessions: SessionRepository,
) : ViewModel() {
    val ui: StateFlow<DiscoverUi> =
        combine(repository.owner, repository.batch, repository.dismissed, repository.makingNow, ::discoverUi)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                discoverUi(repository.owner.value, repository.batch.value, repository.dismissed.value, repository.makingNow.value),
            )

    // Whether the signed-in server runs Octo, for the note under an empty tab.
    val runsOcto: StateFlow<Boolean> = sessions.state
        .map { (it as? SessionState.SignedIn)?.session?.isOcto == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // A pull asked for the batch being made now: the pull's spinner shows
    // only for that, not for a batch made behind the tab.
    var pulled by mutableStateOf(false)
        private set

    // The spinner stays until the batch the pull started has landed (or
    // could not be made); one that never starts lets it go after a second.
    fun pull() {
        if (pulled) return
        pulled = true
        repository.refresh()
        viewModelScope.launch {
            withTimeoutOrNull(PULL_START_WAIT_MS) { repository.makingNow.first { it } }
            repository.makingNow.first { !it }
            pulled = false
        }
    }

    fun onShown() = repository.refreshIfStale()

    // The Refresh button: the next generation, other playlists, made behind
    // the batch on screen (the updated line says "Finding more…").
    fun refresh() = repository.refresh()

    // The picks turned down, for a playlist page kept open over a new batch.
    val dismissed: StateFlow<Dismissed> = repository.dismissed

    // Plays a section's songs as shown, from the one tapped.
    fun play(songs: List<TrackEntity>, index: Int) =
        playback.playTracks(songs.map { it.id }, index, source = DISCOVER_SOURCE)

    // A curated playlist, in order from `index` or shuffled, under its name.
    fun playPlaylist(playlist: DiscoverSection, index: Int = 0, shuffle: Boolean = false) =
        playback.playTracks(playlist.songs.map { it.id }, index, shuffle = shuffle, source = playlist.title)

    fun notInterested(track: TrackEntity) {
        repository.dismissSong(track)
        feedback.undoable("You won't see “${track.title}” in Discover again") { repository.undismissSong(track) }
    }

    fun notInterested(artist: DiscoverArtist) {
        repository.dismissArtist(artist)
        feedback.undoable("You won't see ${artist.name} in Discover again") { repository.undismissArtist(artist) }
    }

    // The station or list whose songs are on the way, if any.
    var startingStation by mutableStateOf<String?>(null)
        private set

    // A station's songs, or a Made for you list, played as Home plays them.
    fun playStation(station: Station) {
        if (startingStation != null) return
        startingStation = station.id
        playback.playList(
            ListSpec.Online(if (station.isRadio) OnlineKind.Station else OnlineKind.List, station.id),
            source = station.name,
            word = if (station.isRadio) QueueSource.Station(station.id, station.name).encoded() else null,
            handlesRefusal = true,
            shown = Shown(station.name, null, station.artwork),
        ) { answer ->
            startingStation = null
            when (answer.refusal) {
                null -> Unit
                NOTHING_TO_PLAY -> feedback.show("Nothing is playing on ${station.name} right now")
                else -> {
                    Log.w("Octo", "discover station failed to start: ${answer.refusal}")
                    feedback.show("Could not start ${station.name}")
                }
            }
        }
    }
}
