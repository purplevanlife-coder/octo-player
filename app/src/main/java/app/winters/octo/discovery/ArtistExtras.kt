package app.winters.octo.discovery

import app.winters.octo.catalog.ArtistEntity
import app.winters.octo.catalog.CatalogDao
import app.winters.octo.catalog.SongIdentity
import app.winters.octo.catalog.SourceDao
import app.winters.octo.catalog.TrackEntity
import app.winters.octo.catalog.isFind
import app.winters.octo.catalog.matchKey
import app.winters.octo.catalog.onlineArtwork
import app.winters.octo.catalog.searchKey
import app.winters.octo.data.SessionRepository
import app.winters.octo.data.SessionState
import app.winters.octo.server.serverSourceId
import app.winters.octo.subsonic.ArtistInfo
import app.winters.octo.subsonic.Song
import app.winters.octo.subsonic.SubsonicClient
import app.winters.octo.subsonic.SubsonicException
import android.util.Log
import app.winters.octo.discover.ArtistExtrasCacheEntity
import app.winters.octo.discover.DiscoverDao
import app.winters.octo.discover.isStale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// How many of an artist's top songs their page shows.
const val TOP_SONGS = 10

// The server extension that lets top songs be asked for by artist id.
private const val TOP_SONGS_BY_ID = "topSongsByArtistId"

// An artist like the one on the page: in the library when `libraryId` is
// set, otherwise only on the server, under `serverId`.
@Serializable
data class SimilarArtist(val name: String, val artwork: String?, val libraryId: String?, val serverId: String)

// What the signed-in server adds to a library artist's page. Any part can be
// empty, and the page shows only the parts that are not.
@Serializable
data class ArtistExtras(
    val topSongs: List<TrackEntity> = emptyList(),
    val about: String? = null,
    val similar: List<SimilarArtist> = emptyList(),
)

// The extras as "Library songs only" shows them: top songs in the library
// (with those the server owns, `owned`, that the phone's copy lacks), and
// similar artists the library has.
fun ArtistExtras.inLibraryOnly(owned: Set<String> = emptySet()): ArtistExtras =
    copy(topSongs = librarySongsOnly(topSongs, true) { isFind(it.id) && it.id !in owned }, similar = similar.filter { it.libraryId != null })

// How long kept extras are believed before they are read again behind the
// page (the kept copy still shows at once), and when an unread one goes.
const val EXTRAS_FRESH_MS = 3L * 24 * 60 * 60 * 1000
private const val EXTRAS_FORGET_MS = 60L * 24 * 60 * 60 * 1000

// Kept extras are this JSON; a row that no longer reads is read again.
private val ExtrasJson = Json { ignoreUnknownKeys = true }

// Reads what the server knows about library artists: their top songs, a
// biography and artists like them. Kept in memory while the app runs and on
// disk (Discover's database, artist_extras_cache) across starts, per server
// and user, so a page opens with what was read before and asks nothing
// again until that is EXTRAS_FRESH_MS old, when it is read again in the
// background. Nothing comes back without a server, or when it cannot be
// reached and nothing was kept.
@Singleton
class ArtistExtrasSource @Inject constructor(
    private val sessions: SessionRepository,
    private val sources: SourceDao,
    private val catalog: CatalogDao,
    private val discovery: Discovery,
    private val disk: DiscoverDao,
) {
    private val cache = ConcurrentHashMap<String, ArtistExtras>()
    private val extensions = ConcurrentHashMap<String, Set<String>>()

    // Artists whose next read skips what is kept (a pull on their page).
    private val forced = ConcurrentHashMap.newKeySet<String>()

    // Reads running behind a kept copy, by key, so one runs at a time.
    private val refreshing = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pruned = AtomicBoolean(false)

    // Drops what was kept for one artist, so the next read asks the server
    // again; what is on disk stays, in case the server cannot answer, and is
    // written over by the answer.
    fun forget(artistId: String) {
        cache.keys.removeAll { it.endsWith("|$artistId") }
        forced.add(artistId)
    }

    suspend fun forArtist(artist: ArtistEntity): ArtistExtras? {
        val client = (sessions.state.value as? SessionState.SignedIn)?.session?.client ?: return null
        val sourceId = serverSourceId(client.baseUrl)
        val server = "${client.username}@${client.baseUrl}"
        val key = "$server|${artist.id}"
        cache[key]?.let { return it }
        pruneOnce()
        val force = artist.id in forced
        if (!force) {
            kept(server, artist.id)?.let { (extras, at) ->
                cache[key] = extras
                if (isStale(at, System.currentTimeMillis(), EXTRAS_FRESH_MS)) refreshBehind(key) { fetch(client, sourceId, artist) }
                return extras
            }
        }
        val fresh = fetch(client, sourceId, artist) ?: return if (force) kept(server, artist.id)?.first else null
        forced.remove(artist.id)
        cache[key] = fresh
        keep(server, artist.id, fresh)
        return fresh
    }

    // Artists like one only on the server (by its id there), for Discover's
    // chains, kept like the rest under "online:<id>". Only `similar` is
    // filled. Null when the server cannot answer and nothing was kept.
    suspend fun similarToServerArtist(serverId: String, name: String): List<SimilarArtist>? {
        val client = (sessions.state.value as? SessionState.SignedIn)?.session?.client ?: return null
        val sourceId = serverSourceId(client.baseUrl)
        val server = "${client.username}@${client.baseUrl}"
        val id = "online:$serverId"
        val key = "$server|$id"
        cache[key]?.let { return it.similar }
        suspend fun read(): ArtistExtras? = answerOf { client.artistInfo(serverId) }?.let { info ->
            ArtistExtras(similar = similarArtists(sourceId, name, info))
        }
        kept(server, id)?.let { (extras, at) ->
            cache[key] = extras
            if (isStale(at, System.currentTimeMillis(), EXTRAS_FRESH_MS)) refreshBehind(key) { read() }
            return extras.similar
        }
        val fresh = read() ?: return null
        cache[key] = fresh
        keep(server, id, fresh)
        return fresh.similar
    }

    // Reads again behind a kept copy; the answer replaces it in memory and on disk.
    private fun refreshBehind(key: String, read: suspend () -> ArtistExtras?) {
        if (!refreshing.add(key)) return
        val (server, id) = key.substringBeforeLast('|') to key.substringAfterLast('|')
        scope.launch {
            try {
                read()?.let { fresh ->
                    cache[key] = fresh
                    keep(server, id, fresh)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Octo", "artist extras refresh failed: ${e.javaClass.simpleName}")
            } finally {
                refreshing.remove(key)
            }
        }
    }

    // What was kept for an artist on disk, and when it was read; null when
    // nothing was, or it no longer reads.
    private suspend fun kept(server: String, artistId: String): Pair<ArtistExtras, Long>? = try {
        disk.extras(server, artistId)?.let { row -> ExtrasJson.decodeFromString(ArtistExtras.serializer(), row.payload) to row.fetchedAt }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun keep(server: String, artistId: String, extras: ArtistExtras) {
        try {
            disk.putExtras(ArtistExtrasCacheEntity(server, artistId, ExtrasJson.encodeToString(ArtistExtras.serializer(), extras), System.currentTimeMillis()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Octo", "artist extras keep failed: ${e.javaClass.simpleName}")
        }
    }

    // Once a run, extras not read for a long while leave the disk.
    private fun pruneOnce() {
        if (!pruned.compareAndSet(false, true)) return
        scope.launch {
            try {
                disk.pruneExtras(System.currentTimeMillis() - EXTRAS_FORGET_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Octo", "artist extras prune failed: ${e.javaClass.simpleName}")
            }
        }
    }

    // What the server says about a library artist now.
    private suspend fun fetch(client: SubsonicClient, sourceId: String, artist: ArtistEntity): ArtistExtras? {
        val server = "${client.username}@${client.baseUrl}"
        val serverId = serverArtistId(client, sourceId, artist)
        val (info, songs) = coroutineScope {
            val info = async { serverId?.let { id -> answerOf { client.artistInfo(id) } } }
            val songs = async {
                val byId = serverId?.takeIf { TOP_SONGS_BY_ID in extensionsOf(server, client) }
                answerOf { client.topSongs(artist.name, TOP_SONGS, byId) }?.let { found ->
                    if (byId != null) found else ownSongs(found, artist.name)
                }
            }
            info.await() to songs.await()
        }
        // Neither answered: the server could not be reached, so ask again next time.
        if (info == null && songs == null) return null

        return ArtistExtras(
            topSongs = songs?.let { discovery.resolveFromServer(it) }.orEmpty().take(TOP_SONGS),
            about = info?.biography?.let(::cleanBiography),
            similar = info?.let { similarArtists(sourceId, artist.name, it) }.orEmpty(),
        )
    }

    // The artist's id on the server: the library's own when the artist came
    // from it, the server's artist of the same name when the library merged
    // them, or failing both, the one a search finds.
    private suspend fun serverArtistId(client: SubsonicClient, sourceId: String, artist: ArtistEntity): String? {
        serverArtistIdOf(artist.id, sourceId)?.let { return it }
        sources.artistsNamed(sourceId, searchKey(artist.name))
            .firstNotNullOfOrNull { row -> serverArtistIdOf(row.id, sourceId)?.takeIf { SongIdentity.sameArtistName(row.name, artist.name) } }
            ?.let { return it }
        val found = answerOf { client.search(artist.name, artists = 5, albums = 0, songs = 0).artist } ?: return null
        return found.firstOrNull { SongIdentity.sameArtistName(it.name, artist.name) }?.id
    }

    // Artists like this one, each opening in the library when it is there.
    private suspend fun similarArtists(sourceId: String, artistName: String, info: ArtistInfo): List<SimilarArtist> {
        val others = info.similarArtist.filter { it.id.isNotEmpty() && !SongIdentity.sameArtistName(it.name, artistName) }
        if (others.isEmpty()) return emptyList()
        val library = catalog.artistsWithKeys(others.map { searchKey(it.name) }.distinct()).associateBy { matchKey(it.name) }
        return others.map { other ->
            val known = library[matchKey(other.name)]
            SimilarArtist(
                name = known?.name ?: other.name,
                artwork = known?.artwork ?: onlineArtwork(sourceId, other.coverArt),
                libraryId = known?.id,
                serverId = other.id,
            )
        }.distinctBy { it.libraryId ?: it.serverId }
    }

    private suspend fun extensionsOf(server: String, client: SubsonicClient): Set<String> =
        extensions[server] ?: (answerOf { client.extensions() }?.mapTo(HashSet()) { it.name } ?: emptySet())
            .also { extensions[server] = it }

    // A server call's answer, or nothing when the server could not give one.
    private suspend fun <T> answerOf(call: suspend () -> T): T? =
        try {
            call()
        } catch (e: SubsonicException) {
            null
        }
}

// The server's own id for a library artist id of the form
// "<server source>:<id>". Nothing for other sources, or for an artist the
// library named itself because the server gave no id.
fun serverArtistIdOf(artistId: String, sourceId: String): String? =
    artistId.takeIf { it.startsWith("$sourceId:") }?.removePrefix("$sourceId:")?.takeIf { it.isNotEmpty() && !it.startsWith("artist:") }

// Top songs that are the artist's own, for a server asked by name, which
// can also send songs by someone else of a similar name.
internal fun ownSongs(songs: List<Song>, artistName: String): List<Song> =
    songs.filter { sameArtist(it.artist.orEmpty(), artistName) }
