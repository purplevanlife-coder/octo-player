package app.winters.octo.discover

import app.winters.octo.catalog.TrackEntity
import app.winters.octo.discovery.Station
import kotlinx.serialization.Serializable

// The Discover tab's content: one generated batch of sections, kept on disk
// per server and account (DiscoverTables.kt) so the tab paints at once and
// refreshes behind itself.

// The Music Space's genre lanes, in the order Discover shows them. "For You"
// is a section of its own (SectionKind.ForYou), not a lane.
@Serializable
enum class DiscoverLane(val title: String) {
    Country("Country"),
    Folk("Folk"),
    Bluegrass("Bluegrass"),
    Blues("Blues"),
    Indie("Indie"),
    Rock("Rock"),
    Phonk("Phonk"),
    HipHop("Hip-Hop"),
    Electronic("Electronic"),
    Soul("Soul"),
    Jazz("Jazz"),
    Oldies("Oldies"),
    Latin("Latin"),
    Reggae("Reggae"),
}

// What a section holds and how the screen draws it.
@Serializable
enum class SectionKind {
    // Mixed song picks, as rows with the add button.
    ForYou,

    // Artists along the similar-artist chains, as round cards.
    Artists,

    // One genre lane's songs, as a row of song cards.
    Lane,

    // The server's lists and stations (Octo only), as station cards.
    MadeForYou,
    Stations,

    // The chart of the moment, as a row of song cards.
    Charts,

    // One curated playlist (DiscoverPlaylists.kt): a named, themed list of
    // songs, drawn as a tile that opens the list. Its key names its theme
    // and the batch's generation ("pl:<theme>#g<n>").
    Playlist,
}

// An artist Discover found: in the library when `libraryId` is set,
// otherwise only on the server, under `serverId`. `via` is the library
// artist whose chain led here, for the card's line.
@Serializable
data class DiscoverArtist(
    val name: String,
    val artwork: String?,
    val libraryId: String?,
    val serverId: String,
    val via: String? = null,
)

// One section of a batch. Only the list its kind uses is filled.
@Serializable
data class DiscoverSection(
    val key: String,
    val kind: SectionKind,
    val title: String,
    val subtitle: String? = null,
    val songs: List<TrackEntity> = emptyList(),
    val artists: List<DiscoverArtist> = emptyList(),
    val lists: List<Station> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && artists.isEmpty() && lists.isEmpty()
}

// A whole batch, when it was made (wall-clock millis), and its generation:
// how many times the listener asked for new picks (Refresh, or a pull).
// A batch made again because it went stale keeps its generation; only a
// refresh asked for makes the next one, with other playlists.
data class DiscoverBatch(val sections: List<DiscoverSection>, val generatedAt: Long, val generation: Long = 0)

// Picks turned down with "Not interested", for one server and account:
// songs by id and by their title and artist (so the same song found again
// under another id stays away), and artists by name key.
data class Dismissed(
    val songIds: Set<String> = emptySet(),
    val songKeys: Set<String> = emptySet(),
    val artistKeys: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = songIds.isEmpty() && songKeys.isEmpty() && artistKeys.isEmpty()
}
