package app.winters.octo.discover

import app.winters.octo.catalog.TrackEntity
import app.winters.octo.discovery.sameSong
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The listener's listening history from before the app, bundled as
// assets/discover_takeout_seed.json: artists and songs with how often each
// was played there, and nothing else (no links, no ids). Read once into
// memory (TakeoutSeedSource), never written anywhere or logged. Discover
// uses it as a seed signal (SeedSignals.history) and for the "From your
// listening history" playlist. These are its rules, apart from the file.

// The file's name in the app's assets.
const val TAKEOUT_SEED_FILE = "discover_takeout_seed.json"

@Serializable
data class TakeoutArtist(val name: String, val plays: Int)

@Serializable
data class TakeoutTrack(val title: String, val artist: String, val plays: Int)

// What the file holds, as far as Discover reads it (anything else in it is
// passed over).
@Serializable
data class TakeoutSeed(
    val version: Int = 1,
    val artists: List<TakeoutArtist> = emptyList(),
    val tracks: List<TakeoutTrack> = emptyList(),
) {
    val isEmpty: Boolean get() = artists.isEmpty() && tracks.isEmpty()

    companion object {
        val None = TakeoutSeed()
    }
}

private val SeedJson = Json { ignoreUnknownKeys = true }

// The seed in `text`, most played first, with nameless or unplayed entries
// left out. A file that does not read (missing, cut short, another shape)
// is no seed at all, never a crash.
fun parseTakeoutSeed(text: String?): TakeoutSeed {
    if (text.isNullOrBlank()) return TakeoutSeed.None
    val read = try {
        SeedJson.decodeFromString(TakeoutSeed.serializer(), text)
    } catch (e: Exception) {
        return TakeoutSeed.None
    }
    return read.copy(
        artists = read.artists.filter { it.plays > 0 && artistKey(it.name).isNotEmpty() }.sortedByDescending { it.plays },
        tracks = read.tracks.filter { it.plays > 0 && artistKey(it.artist).isNotEmpty() && artistKey(it.title).isNotEmpty() }
            .sortedByDescending { it.plays },
    )
}

// The history's artists as seed signals: each that the library has (by
// name, as artistKey reads names: case, accents, punctuation and bracketed
// extras ignored) under the library's id and name, so its chain can be
// walked; the others under their own name with no id. Each artist once,
// with all its plays.
fun matchTakeoutArtists(seed: List<TakeoutArtist>, library: List<ArtistRef>): List<Pair<ArtistRef, Int>> {
    val known = LinkedHashMap<String, ArtistRef>()
    library.forEach { ref -> if (ref.id.isNotEmpty()) known.putIfAbsent(artistKey(ref.name), ref) }
    val plays = LinkedHashMap<String, Pair<ArtistRef, Int>>()
    for (artist in seed) {
        val key = artistKey(artist.name)
        if (key.isEmpty()) continue
        val was = plays[key]
        val ref = was?.first ?: known[key] ?: ArtistRef("", artist.name.trim())
        plays[key] = ref to (was?.second ?: 0) + artist.plays
    }
    return plays.values.toList()
}

// The history's songs the library has, most played first, each library
// song once: matched by artist and title as songKey reads them, or failing
// that as sameSong does (which also knows guests and versions). Lengths are
// not known in the history, so they never count.
fun matchTakeoutTracks(seed: List<TakeoutTrack>, library: List<TrackEntity>): List<TrackEntity> {
    val byKey = LinkedHashMap<String, TrackEntity>()
    library.sortedBy { it.id }.forEach { byKey.putIfAbsent(songKey(it), it) }
    val byTitle = library.groupBy { artistKey(it.title) }
    val out = LinkedHashMap<String, TrackEntity>()
    for (track in seed.sortedByDescending { it.plays }) {
        val found = byKey[songKey(track.title, track.artist)]
            ?: byTitle[artistKey(track.title)].orEmpty().sortedBy { it.id }.firstOrNull { sameSong(track.title, track.artist, 0, it) }
        if (found != null) out.putIfAbsent(found.id, found)
    }
    return out.values.toList()
}
