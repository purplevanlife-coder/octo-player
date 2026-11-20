package app.winters.octo.discover

import app.winters.octo.catalog.TrackEntity

// Discover's curated playlists: named, themed lists of songs made from the
// pools a batch already gathers (the chains' picks, the seeds, the
// listening history, the most played artists' other songs). Each theme
// below is a template; a batch shows PLAYLISTS_SHOWN of them. Which ones,
// and which songs inside each, turn with the batch's generation, so a
// Refresh brings other playlists and other songs, while the same inputs
// and generation always make the same playlists (cache and tests stay
// steady). Pure, like DiscoverEngine; DiscoverRepository feeds it.

// How many playlists a batch shows when it has that many themes to choose
// from, and how many songs a playlist needs to be shown at all.
const val PLAYLISTS_SHOWN = 12
const val PLAYLIST_MIN = 8

// A playlist's usual size, the listening history's (a longer list), and
// how many songs one artist may have in a mixed one.
const val PLAYLIST_SIZE = 20
const val HISTORY_PLAYLIST_SIZE = 30
const val PLAYLIST_PER_ARTIST = 2

// The strongest seeds each get a "Because you play" (and maybe a "Deep
// cuts") playlist.
const val BECAUSE_SEEDS = 6

// The kinds of playlist, in the order a batch shows them.
enum class PlaylistFamily { History, Because, Rediscover, Fresh, Mood, Lane, Deep, Library, Sampler }

// What the playlists are made from. `picks` are the chains' song picks as
// gathered (each with the seed it came `via`); `seeds` the seeds' names,
// strongest first; `historySeeds` the keys of those that come from the
// listening history alone (not in the library), for the playlists' lines;
// `seedKeys` every seed's key, whose own songs are no discovery;
// `history` the listening history's songs the library has, most played
// first; `rediscover` the most played artists' other library songs.
data class PlaylistInput(
    val picks: List<Pick> = emptyList(),
    val seeds: List<String> = emptyList(),
    val historySeeds: Set<String> = emptySet(),
    val seedKeys: Set<String> = emptySet(),
    val dismissed: Dismissed = Dismissed(),
    val history: List<TrackEntity> = emptyList(),
    val rediscover: List<TrackEntity> = emptyList(),
)

// A playlist that can be made: its theme (the same in every generation),
// kind, name, honest one-line explainer and songs.
data class PlaylistCandidate(
    val theme: String,
    val family: PlaylistFamily,
    val title: String,
    val subtitle: String,
    val songs: List<TrackEntity>,
)

// A playlist section's key: its theme and the generation it was made in.
fun playlistKey(theme: String, generation: Long): String = "pl:$theme#g$generation"

// The theme a playlist section's key names, or null for any other section.
fun themeOfKey(key: String): String? = if (key.startsWith("pl:")) key.removePrefix("pl:").substringBefore("#g") else null

// The themes of a batch's playlists, to make the next generation's differ.
fun DiscoverBatch.playlistThemes(): Set<String> =
    sections.filter { it.kind == SectionKind.Playlist }.mapNotNullTo(LinkedHashSet()) { themeOfKey(it.key) }

// The mood mixes: several lanes' picks together, under a plain name and a
// line that says which lanes they are.
private class Mood(val id: String, val title: String, val subtitle: String, val lanes: Set<DiscoverLane>)

private val Moods = listOf(
    Mood("night-drive", "Night drive", "Electronic and phonk from along your chains", setOf(DiscoverLane.Electronic, DiscoverLane.Phonk)),
    Mood(
        "front-porch",
        "Front porch",
        "Folk, country and bluegrass from along your chains",
        setOf(DiscoverLane.Folk, DiscoverLane.Country, DiscoverLane.Bluegrass),
    ),
    Mood(
        "old-souls",
        "Old souls",
        "Oldies, soul, blues and jazz from along your chains",
        setOf(DiscoverLane.Oldies, DiscoverLane.Soul, DiscoverLane.Blues, DiscoverLane.Jazz),
    ),
    Mood("turn-it-up", "Turn it up", "Rock, hip-hop and phonk from along your chains", setOf(DiscoverLane.Rock, DiscoverLane.HipHop, DiscoverLane.Phonk)),
    Mood(
        "slow-morning",
        "Slow morning",
        "Folk, indie, soul and jazz from along your chains",
        setOf(DiscoverLane.Folk, DiscoverLane.Indie, DiscoverLane.Soul, DiscoverLane.Jazz),
    ),
)

// Every playlist the input can make for `generation`, each with at least
// PLAYLIST_MIN songs, in the order a batch shows them (by kind, then each
// kind's own order). The songs inside each turn with the generation.
fun playlistCandidates(input: PlaylistInput, generation: Long): List<PlaylistCandidate> {
    val dismissed = input.dismissed
    val seen = HashSet<String>()
    // The chains' picks: each song once at its strongest, never by a seed,
    // never turned down.
    val picks = input.picks
        .filter { !dismissed.has(it.track) && it.artist !in input.seedKeys && isSomeone(it.track.artist) }
        .sortedWith(byStrength)
        .filter { seen.add(songKey(it.track)) }
    val out = ArrayList<PlaylistCandidate>()
    fun add(theme: String, family: PlaylistFamily, title: String, subtitle: String, songs: List<TrackEntity>) {
        if (songs.size >= PLAYLIST_MIN) out += PlaylistCandidate(theme, family, title, subtitle, songs)
    }

    // The listening history's songs, in their play order.
    val history = ownSongs(input.history, dismissed)
    add(
        "history",
        PlaylistFamily.History,
        "From your listening history",
        "The songs you've played most over the years, from your library",
        turned(history, 4, HISTORY_PLAYLIST_SIZE, generation, keepOrder = true),
    )

    // Because you play (and, with enough left over, Deep cuts) for the strongest seeds.
    val deep = ArrayList<Triple<String, String, List<TrackEntity>>>()
    for (seed in input.seeds.distinctBy(::artistKey).take(BECAUSE_SEEDS)) {
        val key = artistKey(seed)
        val pool = picks.filter { it.via != null && artistKey(it.via) == key }.map { it.track }
        val because = turned(pool, PLAYLIST_PER_ARTIST, PLAYLIST_SIZE, generation)
        if (because.size < PLAYLIST_MIN) continue
        val line = if (key in input.historySeeds) "Artists like $seed, from your listening history" else "Songs from artists like $seed"
        add("because:$key", PlaylistFamily.Because, "Because you play $seed", line, because)
        val taken = because.mapTo(HashSet()) { it.id }
        deep += Triple(key, seed, turned(pool.filter { it.id !in taken }, PLAYLIST_PER_ARTIST, PLAYLIST_SIZE, generation))
    }

    add(
        "rediscover",
        PlaylistFamily.Rediscover,
        "Heavy rotation, rediscovered",
        "Other songs by the artists you play most",
        turned(ownSongs(input.rediscover, dismissed), 4, PLAYLIST_SIZE, generation),
    )
    add(
        "fresh",
        PlaylistFamily.Fresh,
        "Fresh finds",
        "Songs new to your library, from along your chains",
        turned(picks.filter { it.outside }.map { it.track }, PLAYLIST_PER_ARTIST, PLAYLIST_SIZE, generation),
    )

    val byLane = picks.groupBy { laneOf(it.genre ?: it.track.genre.ifEmpty { null }, it.year ?: it.track.year) }
    for (mood in Moods) {
        val pool = picks.filter { laneOf(it.genre ?: it.track.genre.ifEmpty { null }, it.year ?: it.track.year) in mood.lanes }.map { it.track }
        add("mood:${mood.id}", PlaylistFamily.Mood, mood.title, mood.subtitle, turned(pool, PLAYLIST_PER_ARTIST, PLAYLIST_SIZE, generation))
    }
    for (lane in DiscoverLane.entries) {
        val pool = byLane[lane].orEmpty().map { it.track }
        add("lane:${lane.name}", PlaylistFamily.Lane, "${lane.title} finds", "${lane.title} from along your chains", turned(pool, 3, PLAYLIST_SIZE, generation))
    }
    for ((key, seed, songs) in deep) add("deep:$key", PlaylistFamily.Deep, "Deep cuts: $seed", "Further along the chain from $seed", songs)

    add(
        "library",
        PlaylistFamily.Library,
        "Already in your library",
        "Your own songs that your chains lead back to",
        turned(picks.filterNot { it.outside }.map { it.track }, PLAYLIST_PER_ARTIST, PLAYLIST_SIZE, generation),
    )
    add(
        "sampler",
        PlaylistFamily.Sampler,
        "One from each",
        "One song from each artist along your chains",
        turned(picks.map { it.track }, 1, PLAYLIST_SIZE, generation),
    )
    return out
}

// Which playlists a batch of `generation` shows, in the candidates' order.
// The `pinned` kinds always show (the listening history, the tab's
// headline). The rest stand in a ring that takes the kinds in turns, so
// any stretch of it mixes them, and each generation shows the stretch
// after the last one's: generation n starts at n × the free slots. So when
// there are more playlists than slots, generation n + 1 never shows the
// same set as generation n; and should what was gathered have changed so
// that it would anyway (`previous`, the themes the batch being replaced
// showed), the stretch moves on until it does not.
fun choosePlaylists(
    candidates: List<PlaylistCandidate>,
    generation: Long,
    previous: Set<String> = emptySet(),
    shown: Int = PLAYLISTS_SHOWN,
    pinned: Set<PlaylistFamily> = setOf(PlaylistFamily.History),
): List<PlaylistCandidate> {
    val place = candidates.withIndex().associate { (i, c) -> c.theme to i }
    val fixed = candidates.filter { it.family in pinned }.take(shown)
    val ring = inTurnsByFamily(candidates.filter { it.family !in pinned })
    val slots = shown - fixed.size
    val picked = if (ring.size <= slots) {
        ring
    } else {
        val fixedThemes = fixed.mapTo(HashSet()) { it.theme }
        fun stretch(start: Int) = (0 until slots).map { ring[(start + it) % ring.size] }
        var start = Math.floorMod((generation % ring.size) * slots, ring.size.toLong()).toInt()
        var window = stretch(start)
        var moves = 0
        while (previous.isNotEmpty() && moves < ring.size && window.mapTo(HashSet(fixedThemes)) { it.theme } == previous) {
            start = (start + 1) % ring.size
            window = stretch(start)
            moves++
        }
        window
    }
    return (fixed + picked).sortedBy { place.getValue(it.theme) }
}

// The batch's playlist sections for `generation`.
fun buildPlaylists(input: PlaylistInput, generation: Long, previous: Set<String> = emptySet()): List<DiscoverSection> =
    choosePlaylists(playlistCandidates(input, generation), generation, previous).map { c ->
        DiscoverSection(playlistKey(c.theme, generation), SectionKind.Playlist, c.title, c.subtitle, songs = c.songs)
    }

// The playlists taking their kinds in turns: the first of each kind, then
// the second of each, and so on.
private fun inTurnsByFamily(candidates: List<PlaylistCandidate>): List<PlaylistCandidate> {
    val byFamily = candidates.groupBy { it.family }
    val families = PlaylistFamily.entries.filter { it in byFamily }
    val out = ArrayList<PlaylistCandidate>(candidates.size)
    var round = 0
    while (out.size < candidates.size) {
        for (f in families) byFamily.getValue(f).getOrNull(round)?.let(out::add)
        round++
    }
    return out
}

// Library songs (the history's, the most played artists'): each once, not
// turned down, in their order.
private fun ownSongs(songs: List<TrackEntity>, dismissed: Dismissed): List<TrackEntity> {
    val seen = HashSet<String>()
    return songs.filter { !dismissed.has(it) && isSomeone(it.artist) && seen.add(it.id) && seen.add(songKey(it)) }
}

// Picks strongest first, then steady.
private val byStrength = compareByDescending<Pick> { it.score }.thenBy { songKey(it.track) }

// A playlist's songs from its pool (best first) for `generation`: a pool
// bigger than the playlist starts each generation half a playlist further
// in (wrapping round), so a Refresh brings other songs; then at most
// `perArtist` by one artist, the artists taking turns, at most `size`.
// `keepOrder` puts the songs taken back in the pool's order (the history's
// play counts) instead of in turns.
internal fun turned(pool: List<TrackEntity>, perArtist: Int, size: Int, generation: Long, keepOrder: Boolean = false): List<TrackEntity> {
    if (pool.isEmpty()) return emptyList()
    val step = maxOf(1, size / 2)
    val from = if (pool.size <= size) 0 else Math.floorMod((generation % pool.size) * step, pool.size.toLong()).toInt()
    val turned = pool.drop(from) + pool.take(from)
    val counts = HashMap<String, Int>()
    val rounds = ArrayList<MutableList<TrackEntity>>()
    for (track in turned) {
        val artist = artistKey(track.artist)
        val n = counts[artist] ?: 0
        if (n >= perArtist) continue
        counts[artist] = n + 1
        while (rounds.size <= n) rounds += ArrayList<TrackEntity>()
        rounds[n] += track
    }
    val taken = rounds.flatten().take(size)
    if (!keepOrder) return taken
    val order = pool.withIndex().associate { (i, t) -> t.id to i }
    return taken.sortedBy { order.getValue(it.id) }
}
