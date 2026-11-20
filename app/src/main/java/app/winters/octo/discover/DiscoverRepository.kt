package app.winters.octo.discover

import android.content.Context
import android.util.Log
import app.winters.octo.catalog.ArtistEntity
import app.winters.octo.catalog.ArtworkRef
import app.winters.octo.catalog.CatalogDao
import app.winters.octo.catalog.FavouritesDao
import app.winters.octo.catalog.TrackEntity
import app.winters.octo.catalog.UserDao
import app.winters.octo.covers.AheadCoverBucket
import app.winters.octo.data.Session
import app.winters.octo.data.SessionRepository
import app.winters.octo.data.SessionState
import app.winters.octo.data.runsOcto
import app.winters.octo.discovery.ArtistExtrasSource
import app.winters.octo.discovery.CHARTS_ROW
import app.winters.octo.discovery.ChartAnswer
import app.winters.octo.discovery.ChartLists
import app.winters.octo.discovery.Discovery
import app.winters.octo.discovery.Station
import app.winters.octo.discovery.ownSongs
import app.winters.octo.discovery.serverArtistIdOf
import app.winters.octo.listening.PlayHistory
import app.winters.octo.listening.TotalsAnswer
import app.winters.octo.server.serverSourceId
import app.winters.octo.subsonic.CHART_OVERALL
import app.winters.octo.subsonic.Song
import app.winters.octo.subsonic.SubsonicException
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// The format sections are written in; a batch of another is made anew.
internal const val BATCH_FORMAT = 1

// How many of the listener's signals are read: the most played songs, the
// latest plays, liked songs and highly rated songs.
private const val PLAYED_READ = 300
private const val RECENT_READ = 200
private const val LIKED_READ = 500
private const val RATED_READ = 300
private const val RATED_FROM = 4

// The longest a read of the play counts may hold up a batch.
private const val SIGNALS_WAIT_MS = 20_000L

// Similar songs read for the strongest seeds, and per chain artist: their
// top songs and songs like them, for this many chain artists.
private const val SEED_SONGS_FROM = 4
private const val SEED_SIMILAR_SONGS = 15
private const val CHAIN_SONGS_FROM = 10
private const val CHAIN_TOP_SONGS = 5
private const val CHAIN_SIMILAR_SONGS = 6

// Songs like a chain artist count for this much of the artist's own top songs.
private const val SIMILAR_SONG_SHARE = 0.6

// Server calls in flight at once while a batch is made.
private const val CALLS_AT_ONCE = 4

// How long after start (or a switch of server) a missing or stale batch
// is made, so it never competes with the app opening.
private const val FIRST_MAKE_DELAY_MS = 3_000L
private const val STALE_MAKE_DELAY_MS = 8_000L

// Covers fetched ahead into the shared Coil disk cache after a batch.
private const val COVERS_AHEAD = 40

// The batch Discover shows, for one server and account.
data class OwnedBatch(val owner: String, val batch: DiscoverBatch?)

// Which server and account Discover's batch and dismissals are kept under.
fun ownerOf(session: Session): String =
    "${session.id.ifEmpty { serverSourceId(session.client.primaryUrl) }}|${session.client.username}"

fun ownerOf(state: SessionState): String? = (state as? SessionState.SignedIn)?.session?.let(::ownerOf)

// The picks turned down, from their rows.
fun dismissedOf(rows: List<DiscoverDismissedEntity>): Dismissed = Dismissed(
    songIds = rows.filter { it.kind == DismissKind.SONG }.mapTo(HashSet()) { it.itemKey },
    songKeys = rows.filter { it.kind == DismissKind.SONG_KEY }.mapTo(HashSet()) { it.itemKey },
    artistKeys = rows.filter { it.kind == DismissKind.ARTIST }.mapTo(HashSet()) { it.itemKey },
)

// Makes and keeps Discover's batches. The batch for the server in use is
// read from disk as soon as the app starts (MainShell's
// DiscoverShellViewModel creates this), so the tab paints it on its first
// frame; a missing or stale one (DISCOVER_FRESH_MS) is made again in the
// background a little after, and on a pull. Making one reads the
// listener's signals from the library, walks the similar-artist chains
// through ArtistExtrasSource (kept on disk), asks the server for songs
// along them, and builds the sections (DiscoverEngine.kt). A server that
// gives nothing (plain Navidrome with no Last.fm agent) just has fewer
// sections; a batch that came back empty never replaces one that was not.
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DiscoverRepository @Inject constructor(
    private val sessions: SessionRepository,
    private val dao: DiscoverDao,
    private val catalog: CatalogDao,
    private val users: UserDao,
    private val favourites: FavouritesDao,
    private val history: PlayHistory,
    private val extras: ArtistExtrasSource,
    private val discovery: Discovery,
    private val charts: ChartLists,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val making = Mutex()

    // The server and account in use, or null with none signed in.
    val owner: StateFlow<String?> = sessions.state.map(::ownerOf).distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ownerOf(sessions.state.value))

    private val _batch = MutableStateFlow<OwnedBatch?>(null)

    // The batch for the server in use as kept, once read; null before that
    // or with none signed in.
    val batch: StateFlow<OwnedBatch?> = _batch.asStateFlow()

    private val _making = MutableStateFlow(false)

    // Whether a batch is being made now.
    val makingNow: StateFlow<Boolean> = _making.asStateFlow()

    // The picks turned down on the server in use, kept up to date.
    val dismissed: StateFlow<Dismissed> = owner
        .flatMapLatest { o -> if (o == null) flowOf(emptyList<DiscoverDismissedEntity>()) else dao.dismissed(o) }
        .map(::dismissedOf)
        .stateIn(scope, SharingStarted.Eagerly, Dismissed())

    init {
        scope.launch {
            owner.collect { o ->
                if (o == null) {
                    _batch.value = null
                    return@collect
                }
                val kept = safely("read") { load(o) }
                _batch.value = OwnedBatch(o, kept)
                val due = kept == null || isStale(kept.generatedAt, System.currentTimeMillis())
                if (due) {
                    // Behind the app's start, or the switch, not in its way.
                    scope.launch {
                        delay(if (kept == null) FIRST_MAKE_DELAY_MS else STALE_MAKE_DELAY_MS)
                        if (owner.value == o) make(o)
                    }
                }
            }
        }
    }

    // Nothing to do: creating the repository starts its read. Called by
    // the shell so that happens at start.
    fun warm() = Unit

    // The tab is on screen: a stale batch is made again behind it.
    fun refreshIfStale() {
        val o = owner.value ?: return
        val shown = _batch.value?.takeIf { it.owner == o } ?: return
        val at = shown.batch?.generatedAt
        if (at == null || isStale(at, System.currentTimeMillis())) refresh()
    }

    // A pull: a new batch is made in the background, the current one
    // staying on screen until it lands. A pull while one is made does nothing.
    fun refresh() {
        val o = owner.value ?: return
        scope.launch { make(o) }
    }

    fun dismissSong(track: TrackEntity) {
        val o = owner.value ?: return
        val now = System.currentTimeMillis()
        scope.launch {
            safely("dismiss") {
                dao.dismiss(
                    listOf(
                        DiscoverDismissedEntity(o, DismissKind.SONG, track.id, track.title, now),
                        DiscoverDismissedEntity(o, DismissKind.SONG_KEY, songKey(track), track.title, now),
                    ),
                )
            }
        }
    }

    fun undismissSong(track: TrackEntity) {
        val o = owner.value ?: return
        scope.launch {
            safely("undismiss") {
                dao.undismiss(o, DismissKind.SONG, track.id)
                dao.undismiss(o, DismissKind.SONG_KEY, songKey(track))
            }
        }
    }

    fun dismissArtist(artist: DiscoverArtist) {
        val o = owner.value ?: return
        val now = System.currentTimeMillis()
        scope.launch { safely("dismiss") { dao.dismiss(listOf(DiscoverDismissedEntity(o, DismissKind.ARTIST, artistKey(artist.name), artist.name, now))) } }
    }

    fun undismissArtist(artist: DiscoverArtist) {
        val o = owner.value ?: return
        scope.launch { safely("undismiss") { dao.undismiss(o, DismissKind.ARTIST, artistKey(artist.name)) } }
    }

    // Makes a batch for `owner` and keeps it, unless one is being made, the
    // server changed meanwhile, or it came back empty over one that was not.
    private suspend fun make(owner: String) {
        if (!making.tryLock()) return
        _making.value = true
        try {
            val session = (sessions.state.value as? SessionState.SignedIn)?.session ?: return
            if (ownerOf(session) != owner) return
            val before = _batch.value?.takeIf { it.owner == owner }?.batch
            val made = safely("make") { makeBatch(session, owner, before) } ?: return
            if (this.owner.value != owner) return
            if (made.sections.isEmpty() && before != null && before.sections.isNotEmpty()) return
            safely("keep") { keep(owner, made) }
            _batch.value = OwnedBatch(owner, made)
            scope.launch { safely("covers") { coversAhead(made) } }
        } finally {
            _making.value = false
            making.unlock()
        }
    }

    private suspend fun makeBatch(session: Session, owner: String, before: DiscoverBatch?): DiscoverBatch = coroutineScope {
        val client = session.client
        val sourceId = serverSourceId(client.primaryUrl)
        val dismissed = dismissedOf(dao.dismissedNow(owner))
        val gate = Semaphore(CALLS_AT_ONCE)
        suspend fun <T> call(what: suspend () -> T): T? = gate.withPermit { answerOf(what) }

        // The server's lists and chart, read while the chains are walked.
        val madeForYou = async<List<Station>?> { if (session.runsOcto) call { discovery.madeForYou() } else emptyList() }
        val stations = async<List<Station>?> { if (session.runsOcto) call { discovery.stations().filter { it.streamUrl == null } } else emptyList() }
        val chart = async {
            when (val answer = call { charts.answer(CHART_OVERALL) }) {
                is ChartAnswer.Got -> answer.list.songs.take(CHARTS_ROW).map { it.track }
                ChartAnswer.None -> emptyList()
                is ChartAnswer.Failed, null -> null
            }
        }

        val seeds = weighSeeds(signals())
        val seedKeys = seeds.mapTo(HashSet()) { artistKey(it.artist.name) }
        val entities: List<ArtistEntity?> = seeds.map { seed ->
            async { seed.artist.id.takeIf(String::isNotEmpty)?.let { id -> safely("seed") { catalog.artist(id).first() } } }
        }.awaitAll()

        // First hop: artists like each seed.
        val firstLinks = seeds.zip(entities).map { (seed, entity) ->
            async {
                val similar = entity?.let { e -> call { extras.forArtist(e) }?.similar }.orEmpty()
                ChainLink(similar, seed.weight, seed.artist.name)
            }
        }.awaitAll()
        // Second hop: artists like the best of those outside the library.
        val secondLinks = secondHopFrom(scoreChain(firstLinks), seedKeys, dismissed).map { c ->
            async {
                val similar = call { extras.similarToServerArtist(c.artist.serverId, c.artist.name) }.orEmpty()
                ChainLink(similar, c.score * SECOND_HOP_DECAY, c.via)
            }
        }.awaitAll()
        val chain = rankChain(scoreChain(firstLinks + secondLinks), seedKeys, dismissed)

        // Songs: like the strongest seeds, and from the chain's artists.
        val asks = ArrayList<kotlinx.coroutines.Deferred<List<Pair<Song, Double>>>>()
        seeds.zip(entities).take(SEED_SONGS_FROM).forEach { (seed, entity) ->
            val serverId = entity?.let { serverArtistIdOf(it.id, sourceId) } ?: return@forEach
            asks += async { ranked(call { client.similarSongs(serverId, SEED_SIMILAR_SONGS) }, seed.weight) }
        }
        chain.filter { !it.inLibrary }.take(CHAIN_SONGS_FROM).forEach { c ->
            asks += async { ranked(call { client.topSongs(c.artist.name, CHAIN_TOP_SONGS) }?.let { ownSongs(it, c.artist.name) }, c.score) }
            asks += async { ranked(call { client.similarSongs(c.artist.serverId, CHAIN_SIMILAR_SONGS) }, c.score * SIMILAR_SONG_SHARE) }
        }
        val found = asks.awaitAll().flatten()
        val tracks = if (found.isEmpty()) emptyList() else answerOf { discovery.resolveInPlace(found.map { it.first }) }.orEmpty()
        val picks = found.zip(tracks).mapNotNull { (sent, track) ->
            track?.let { Pick(it, sent.first.genre?.takeIf(String::isNotBlank), sent.first.year?.takeIf { y -> y > 0 }, sent.second) }
        }

        // A list that could not be read keeps what the last batch had.
        fun kept(kind: SectionKind): List<Station> = before?.sections?.firstOrNull { it.kind == kind }?.lists.orEmpty()
        val sections = buildSections(
            SectionInput(
                picks = picks,
                artists = chain,
                seedKeys = seedKeys,
                dismissed = dismissed,
                madeForYou = madeForYou.await() ?: kept(SectionKind.MadeForYou),
                stations = stations.await() ?: kept(SectionKind.Stations),
                charts = chart.await() ?: before?.sections?.firstOrNull { it.kind == SectionKind.Charts }?.songs.orEmpty(),
            ),
        )
        DiscoverBatch(sections, System.currentTimeMillis())
    }

    // Songs as one list sent them, each with its weight: `weight` for the
    // first, less for each one after.
    private fun ranked(songs: List<Song>?, weight: Double): List<Pair<Song, Double>> =
        songs.orEmpty().mapIndexed { i, song -> song to weight / (1.0 + RANK_DECAY * i) }

    // What the library says the listener plays and loves. A read that
    // cannot be made (the play counts still building) gives nothing.
    private suspend fun signals(): SeedSignals {
        fun ref(track: TrackEntity) = ArtistRef(track.artistId, track.artist)
        val played = safely("plays") {
            withTimeoutOrNull(SIGNALS_WAIT_MS) { (history.mostPlayed(null, PLAYED_READ).first() as? TotalsAnswer.Value)?.value }
        }.orEmpty()
        val recent = safely("recent") { withTimeoutOrNull(SIGNALS_WAIT_MS) { history.recentPlays(RECENT_READ).first() } }.orEmpty()
        val likedArtists = safely("liked artists") { favourites.likedArtists().first() }.orEmpty()
        val likedSongs = safely("liked songs") { users.likedTracks().first() }.orEmpty().take(LIKED_READ)
        val rated = safely("rated") { catalog.topRatedTracks(RATED_FROM, RATED_READ) }.orEmpty()
        return SeedSignals(
            plays = played.map { ref(it.track) to it.plays },
            recent = recent.map { ref(it.track) },
            likedArtists = likedArtists.map { ArtistRef(it.artist.id, it.artist.name) },
            likedSongs = likedSongs.map(::ref),
            rated = rated.map { ref(it) to it.rating },
        )
    }

    // The batch kept for `owner`, or null when there is none (or it was
    // written in another format).
    private suspend fun load(owner: String): DiscoverBatch? {
        val head = dao.batch(owner) ?: return null
        if (head.format != BATCH_FORMAT) return null
        val sections = dao.sections(owner).mapNotNull { row ->
            try {
                json.decodeFromString(DiscoverSection.serializer(), row.payload)
            } catch (e: Exception) {
                null
            }
        }
        return DiscoverBatch(sections, head.generatedAt)
    }

    private suspend fun keep(owner: String, batch: DiscoverBatch) {
        val rows = batch.sections.mapIndexed { i, s ->
            DiscoverSectionEntity(owner, i, s.key, s.kind.name, s.title, json.encodeToString(DiscoverSection.serializer(), s))
        }
        dao.replaceBatch(DiscoverBatchEntity(owner, batch.generatedAt, BATCH_FORMAT), rows)
    }

    // The batch's first covers, fetched into Coil's disk cache through the
    // app's own loader (the same cache every Artwork reads), at the size
    // that answers every smaller ask, as CoversAhead does.
    private suspend fun coversAhead(batch: DiscoverBatch) {
        val loader = SingletonImageLoader.get(context)
        batch.sections.asSequence()
            .flatMap { s -> s.songs.map { it.artwork } + s.artists.map { it.artwork } + s.lists.map { it.artwork } }
            .filterNotNull().distinct()
            .mapNotNull { ArtworkRef.decode(it) as? ArtworkRef.Server }
            .take(COVERS_AHEAD)
            .forEach { art ->
                loader.execute(ImageRequest.Builder(context).data(art).size(AheadCoverBucket).precision(Precision.INEXACT).build())
            }
    }

    // A server call's answer, or null when the server could not give one
    // (or anything else went wrong with it): one source failing only leaves
    // its own part of the batch out.
    private suspend fun <T> answerOf(call: suspend () -> T): T? =
        try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SubsonicException) {
            null
        } catch (e: Exception) {
            Log.w("Octo", "discover read failed: ${e.javaClass.simpleName}")
            null
        }

    // A step that may fail without stopping the rest, logged when it does.
    private suspend fun <T> safely(what: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Octo", "discover $what failed: ${e.javaClass.simpleName}")
            null
        }
}
