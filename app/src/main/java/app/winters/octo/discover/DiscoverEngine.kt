package app.winters.octo.discover

import app.winters.octo.catalog.TrackEntity
import app.winters.octo.catalog.isFind
import app.winters.octo.catalog.matchKey
import app.winters.octo.discovery.SimilarArtist
import app.winters.octo.discovery.Station
import kotlin.math.ln

// Discover's rules, apart from where their inputs come from, so each can be
// tested on its own: which library artists seed it, how far along the
// similar-artist chains each candidate is, which genre lane a song belongs
// in, and how a batch's sections are made and kept clear of dismissed
// picks. DiscoverRepository gathers the inputs and runs them.

// How many seeds start the chains, and how many of the best first-hop
// artists outside the library the chains walk one hop further from.
const val SEED_COUNT = 8
const val SECOND_HOP_FROM = 4

// A second hop counts for half of the artist it came through.
const val SECOND_HOP_DECAY = 0.5

// How far down a similar-artist list a place counts less: the artist at
// place r (from 0) gets 1 / (1 + RANK_DECAY * r) of the parent's weight.
const val RANK_DECAY = 0.2

// Artists to explore: at most this many; library artists only fill the
// row up to ARTIST_FILL when too few outside the library were found.
const val ARTISTS_SHOWN = 20
const val ARTIST_FILL = 6

// For You's size and how many songs one artist may have there; a lane's.
const val FOR_YOU_SIZE = 25
const val FOR_YOU_PER_ARTIST = 2
const val LANE_SIZE = 20
const val LANE_PER_ARTIST = 3

// A lane shows only with at least this many songs.
const val LANE_MIN = 3

// Songs from before this year with no clearer lane are Oldies.
const val OLDIES_BEFORE = 1980

// An artist as a listening signal names them: the library's id (may be
// empty) and the name.
data class ArtistRef(val id: String, val name: String)

// Everything that says which library artists are listened to. Each list
// holds one entry per thing counted: `plays` an artist's total plays,
// `recent` one per recent play, `likedArtists` one per favourite artist,
// `likedSongs` one per liked song, `rated` one per rated song.
data class SeedSignals(
    val plays: List<Pair<ArtistRef, Int>> = emptyList(),
    val recent: List<ArtistRef> = emptyList(),
    val likedArtists: List<ArtistRef> = emptyList(),
    val likedSongs: List<ArtistRef> = emptyList(),
    val rated: List<Pair<ArtistRef, Int>> = emptyList(),
)

data class Seed(val artist: ArtistRef, val weight: Double)

// How much each signal weighs. Play totals weigh most, then recent plays,
// then favourite artists, then liked and highly rated songs, so an artist
// carried by one signal alone ranks by its tier, and one carried by
// several adds them up. Counts grow on a log, so one artist played a
// thousand times does not drown every other seed.
object SeedWeights {
    const val PLAYS = 4.0
    const val RECENT = 2.5
    const val LIKED_ARTIST = 1.5
    const val LIKED_SONG = 0.5
    const val LIKED_SONGS_MOST = 1.5

    // Per star above three (four stars 0.5, five stars 1.0), at most RATED_MOST.
    const val RATED_STAR = 0.5
    const val RATED_MOST = 2.0
}

// Names that are no one in particular, never a seed or a pick.
private val NoOne = setOf("", "various artists", "various", "unknown artist", "unknown", "[unknown]", "va")

fun artistKey(name: String): String = matchKey(name)

// A song's identity apart from its id, for dismissals and repeats.
fun songKey(title: String, artist: String): String = matchKey(artist) + "|" + matchKey(title)

fun songKey(track: TrackEntity): String = songKey(track.title, track.artist)

private fun isSomeone(name: String): Boolean = name.trim().lowercase() !in NoOne && artistKey(name).isNotEmpty()

// The library artists that seed Discover, heaviest first (then by name, so
// the order is steady), at most `limit`. Artists are one artist by name,
// whatever id each signal knew them by; the first id that is not empty is
// kept.
fun weighSeeds(signals: SeedSignals, limit: Int = SEED_COUNT): List<Seed> {
    class Tally(var ref: ArtistRef) {
        var plays = 0
        var recent = 0
        var likedArtist = false
        var likedSongs = 0
        var ratedStars = 0.0
    }
    val tallies = LinkedHashMap<String, Tally>()
    fun tally(ref: ArtistRef): Tally? {
        if (!isSomeone(ref.name)) return null
        val t = tallies.getOrPut(artistKey(ref.name)) { Tally(ref) }
        if (t.ref.id.isEmpty() && ref.id.isNotEmpty()) t.ref = ref
        return t
    }
    signals.plays.forEach { (ref, plays) -> if (plays > 0) tally(ref)?.let { it.plays += plays } }
    signals.recent.forEach { tally(it)?.let { t -> t.recent++ } }
    signals.likedArtists.forEach { tally(it)?.likedArtist = true }
    signals.likedSongs.forEach { tally(it)?.let { t -> t.likedSongs++ } }
    signals.rated.forEach { (ref, rating) -> if (rating > 3) tally(ref)?.let { it.ratedStars += (rating - 3) * SeedWeights.RATED_STAR } }
    return tallies.values
        .map { t ->
            val weight = SeedWeights.PLAYS * ln(1.0 + t.plays) +
                SeedWeights.RECENT * ln(1.0 + t.recent) +
                (if (t.likedArtist) SeedWeights.LIKED_ARTIST else 0.0) +
                minOf(SeedWeights.LIKED_SONGS_MOST, t.likedSongs * SeedWeights.LIKED_SONG) +
                minOf(SeedWeights.RATED_MOST, t.ratedStars)
            Seed(t.ref, weight)
        }
        .filter { it.weight > 0 }
        .sortedWith(compareByDescending<Seed> { it.weight }.thenBy { artistKey(it.artist.name) })
        .take(limit)
}

// One similar-artist list read along a chain: the artists like `via`'s
// artist (or the artist reached from it), and the weight it hands on.
data class ChainLink(val similar: List<SimilarArtist>, val weight: Double, val via: String)

// An artist reached along the chains, with how strongly, and the seed whose
// chain gave the most of that.
data class ChainCandidate(val artist: SimilarArtist, val score: Double, val via: String) {
    val key: String get() = artistKey(artist.name)
    val inLibrary: Boolean get() = artist.libraryId != null
}

// Every artist the links reach, each once by name, its score the sum of
// what each link gives it (an artist several seeds lead to ranks higher).
fun scoreChain(links: List<ChainLink>): Map<String, ChainCandidate> {
    val out = LinkedHashMap<String, ChainCandidate>()
    val best = HashMap<String, Double>()
    for (link in links) {
        link.similar.forEachIndexed { rank, artist ->
            if (!isSomeone(artist.name)) return@forEachIndexed
            val key = artistKey(artist.name)
            val gives = link.weight / (1.0 + RANK_DECAY * rank)
            val was = out[key]
            // The library's copy of an artist wins over a server-only one.
            val shown = when {
                was == null -> artist
                was.artist.libraryId == null && artist.libraryId != null -> artist
                else -> was.artist
            }
            val via = if (was == null || gives > (best[key] ?: 0.0)) link.via.also { best[key] = gives } else was.via
            out[key] = ChainCandidate(shown, (was?.score ?: 0.0) + gives, via)
        }
    }
    return out
}

// The artists worth a second hop: the best outside the library, none of
// them a seed or dismissed.
fun secondHopFrom(scored: Map<String, ChainCandidate>, seedKeys: Set<String>, dismissed: Dismissed, count: Int = SECOND_HOP_FROM): List<ChainCandidate> =
    scored.values
        .filter { !it.inLibrary && it.key !in seedKeys && it.key !in dismissed.artistKeys }
        .sortedWith(byScore)
        .take(count)

// Artists to explore, best first: never a seed or a dismissed artist;
// those outside the library first, and library artists only to fill the
// row to `fill` when too few outside it were found.
fun rankChain(
    scored: Map<String, ChainCandidate>,
    seedKeys: Set<String>,
    dismissed: Dismissed,
    limit: Int = ARTISTS_SHOWN,
    fill: Int = ARTIST_FILL,
): List<ChainCandidate> {
    val open = scored.values.filter { it.key !in seedKeys && it.key !in dismissed.artistKeys }
    val (inside, outside) = open.partition { it.inLibrary }
    val picked = outside.sortedWith(byScore).take(limit)
    if (picked.size >= fill) return picked
    return picked + inside.sortedWith(byScore).take(minOf(fill, limit) - picked.size)
}

private val byScore = compareByDescending<ChainCandidate> { it.score }.thenBy { it.key }

// A song found for Discover: as the app shows it, with the genre and year
// the server sent (a song found online keeps neither on its row), the
// artist it is by, and how strongly the chains point at it.
data class Pick(
    val track: TrackEntity,
    val genre: String?,
    val year: Int?,
    val score: Double,
) {
    val artist: String get() = artistKey(track.artist)
    val outside: Boolean get() = isFind(track.id)
}

// The lane a song belongs in from its genre tag, or null when none fits.
// Tags are matched on whole words, the most specific lane first, so
// "Country Rock" is Country, "Indie Rock" Indie and "Dubstep" Electronic,
// not Reggae. A song from before OLDIES_BEFORE with no lane, or only Rock
// or Soul, is Oldies.
fun laneOf(genre: String?, year: Int?): DiscoverLane? {
    val words = " " + (genre.orEmpty().lowercase().replace(NotWord, " ").replace(Spaces, " ").trim()) + " "
    val byTag = LaneWords.firstOrNull { (_, tags) -> tags.any { words.contains(" $it ") } }?.first
    val old = year != null && year in 1900 until OLDIES_BEFORE
    return if (old && (byTag == null || byTag == DiscoverLane.Rock || byTag == DiscoverLane.Soul)) DiscoverLane.Oldies else byTag
}

private val NotWord = Regex("[^a-z0-9&]+")
private val Spaces = Regex(" +")

// Each lane's words, checked in this order.
private val LaneWords: List<Pair<DiscoverLane, List<String>>> = listOf(
    DiscoverLane.Bluegrass to listOf("bluegrass", "newgrass"),
    DiscoverLane.Phonk to listOf("phonk", "drift phonk"),
    DiscoverLane.Oldies to listOf("oldies", "doo wop", "doowop", "rock and roll", "rock n roll", "rock & roll", "rockabilly"),
    DiscoverLane.Country to listOf("country", "honky tonk", "outlaw", "nashville", "red dirt"),
    DiscoverLane.Folk to listOf("folk", "americana", "singer songwriter", "acoustic"),
    DiscoverLane.Blues to listOf("blues", "delta"),
    DiscoverLane.Indie to listOf("indie", "alternative", "alt", "lo fi", "lofi", "shoegaze", "dream pop", "bedroom pop", "emo"),
    DiscoverLane.HipHop to listOf("hip hop", "hiphop", "rap", "trap", "drill", "grime", "boom bap"),
    DiscoverLane.Electronic to listOf(
        "electronic", "electronica", "edm", "house", "techno", "trance", "dubstep", "drum and bass", "drum & bass", "dnb",
        "synthwave", "synth", "ambient", "electro", "idm", "breakbeat", "downtempo", "chillwave",
    ),
    DiscoverLane.Soul to listOf("soul", "r&b", "rnb", "r b", "funk", "motown", "neo soul", "gospel"),
    DiscoverLane.Jazz to listOf("jazz", "swing", "bebop", "bossa nova", "bossa", "big band"),
    DiscoverLane.Latin to listOf(
        "latin", "latino", "reggaeton", "salsa", "bachata", "cumbia", "merengue", "regional mexican", "corrido", "corridos",
        "banda", "mariachi", "norteno", "tejano", "flamenco",
    ),
    DiscoverLane.Reggae to listOf("reggae", "dancehall", "ska", "dub", "rocksteady"),
    DiscoverLane.Rock to listOf("rock", "punk", "grunge", "metal", "hard rock", "classic rock", "post punk", "garage rock"),
)

// Whether a song or an artist was turned down.
fun Dismissed.has(track: TrackEntity): Boolean =
    track.id in songIds || songKey(track) in songKeys || artistKey(track.artist) in artistKeys

fun Dismissed.has(artist: DiscoverArtist): Boolean = artistKey(artist.name) in artistKeys

// What goes into a batch: the song picks and the chain's artists (as
// gathered), the seeds (whose own songs and names stay out of the
// picks), what was turned down, and the server's lists and chart.
data class SectionInput(
    val picks: List<Pick> = emptyList(),
    val artists: List<ChainCandidate> = emptyList(),
    val seedKeys: Set<String> = emptySet(),
    val dismissed: Dismissed = Dismissed(),
    val madeForYou: List<Station> = emptyList(),
    val stations: List<Station> = emptyList(),
    val charts: List<TrackEntity> = emptyList(),
)

// Discover's sections from what was gathered, in the order shown: For You,
// Artists to explore, the genre lanes, Made for you, Stations, Charts.
// A section with nothing in it is left out.
fun buildSections(input: SectionInput): List<DiscoverSection> {
    val dismissed = input.dismissed
    val seen = HashSet<String>()
    // Each song once (by title and artist), never by a seed, never dismissed.
    val picks = input.picks
        .filter { !dismissed.has(it.track) && it.artist !in input.seedKeys && isSomeone(it.track.artist) }
        .sortedWith(byPick)
        .filter { seen.add(songKey(it.track)) }

    val sections = ArrayList<DiscoverSection>()
    val forYou = spread(picks, FOR_YOU_PER_ARTIST, FOR_YOU_SIZE)
    if (forYou.isNotEmpty()) {
        sections += DiscoverSection("for-you", SectionKind.ForYou, "For You", "New to you, from what you play", songs = forYou.map { it.track })
    }
    val artists = input.artists
        .filter { it.key !in dismissed.artistKeys && it.key !in input.seedKeys }
        .map { DiscoverArtist(it.artist.name, it.artist.artwork, it.artist.libraryId, it.artist.serverId, it.via) }
    if (artists.isNotEmpty()) {
        sections += DiscoverSection("artists", SectionKind.Artists, "Artists to explore", "Along the chains from artists you play", artists = artists)
    }
    val byLane = picks.groupBy { laneOf(it.genre ?: it.track.genre.ifEmpty { null }, it.year ?: it.track.year) }
    for (lane in DiscoverLane.entries) {
        val songs = spread(byLane[lane].orEmpty(), LANE_PER_ARTIST, LANE_SIZE)
        if (songs.size >= LANE_MIN) sections += DiscoverSection("lane:${lane.name}", SectionKind.Lane, lane.title, songs = songs.map { it.track })
    }
    if (input.madeForYou.isNotEmpty()) sections += DiscoverSection("made-for-you", SectionKind.MadeForYou, "Made for you", lists = input.madeForYou)
    if (input.stations.isNotEmpty()) sections += DiscoverSection("stations", SectionKind.Stations, "Stations", lists = input.stations)
    val charts = input.charts.filterNot(dismissed::has)
    if (charts.isNotEmpty()) sections += DiscoverSection("charts", SectionKind.Charts, "Charts", songs = charts)
    return sections
}

// Songs not in the library first, then the strongest, then steady.
private val byPick = compareByDescending<Pick> { it.outside }.thenByDescending { it.score }.thenBy { songKey(it.track) }

// The picks in their order, at most `perArtist` from any one artist, taken
// in turns so no artist's songs bunch up at the front, at most `size`.
// Songs not in the library all come before library songs, each group
// taking its own turns (the per-artist count is shared).
internal fun spread(picks: List<Pick>, perArtist: Int, size: Int): List<Pick> {
    val counts = HashMap<String, Int>()
    fun turns(group: List<Pick>): List<Pick> {
        val rounds = ArrayList<MutableList<Pick>>()
        for (pick in group) {
            val n = counts[pick.artist] ?: 0
            if (n >= perArtist) continue
            counts[pick.artist] = n + 1
            val round = n.coerceAtMost(perArtist - 1)
            while (rounds.size <= round) rounds += ArrayList<Pick>()
            rounds[round] += pick
        }
        return rounds.flatten()
    }
    val (outside, inside) = picks.partition { it.outside }
    return (turns(outside) + turns(inside)).take(size)
}

// A batch as shown once some picks are turned down: they leave every
// section at once, and a section left empty goes.
fun DiscoverBatch.without(dismissed: Dismissed): DiscoverBatch {
    if (dismissed.isEmpty) return this
    val kept = sections.mapNotNull { section ->
        val left = section.copy(
            songs = section.songs.filterNot(dismissed::has),
            artists = section.artists.filterNot(dismissed::has),
        )
        left.takeUnless { it.isEmpty }
    }
    return copy(sections = kept)
}

// Whether a batch made at `generatedAt` is due to be made again at `now`.
fun isStale(generatedAt: Long, now: Long, windowMs: Long = DISCOVER_FRESH_MS): Boolean =
    now - generatedAt >= windowMs || now < generatedAt

// How long a batch is fresh: older ones are made again in the background.
const val DISCOVER_FRESH_MS = 6 * 60 * 60 * 1000L
