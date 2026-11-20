package app.winters.octo.discover

import app.winters.octo.catalog.TrackEntity
import app.winters.octo.discovery.SimilarArtist

// Small songs and artists for Discover's tests. A find's id starts "find:".
internal fun song(id: String, title: String, artist: String, genre: String = "", year: Int? = null) = TrackEntity(
    id = id,
    sourceId = "server:test",
    nativeId = id.removePrefix("find:"),
    title = title,
    searchKey = title.lowercase(),
    sortKey = title.lowercase(),
    artist = artist,
    artistId = "",
    album = "",
    albumId = "",
    trackNo = null,
    discNo = null,
    year = year,
    durationMs = 200_000,
    addedAt = 0,
    mimeType = null,
    sizeBytes = null,
    artwork = null,
    uri = null,
    genre = genre,
)

internal fun find(n: Int, artist: String, genre: String? = null, score: Double = 1.0, year: Int? = null) =
    Pick(song("find:$artist-$n", "$artist song $n", artist), genre, year, score)

internal fun owned(n: Int, artist: String, genre: String? = null, score: Double = 1.0) =
    Pick(song("lib:$artist-$n", "$artist owned $n", artist), genre, null, score)

internal fun outside(name: String) = SimilarArtist(name, null, null, "s-$name")

internal fun inLibrary(name: String) = SimilarArtist(name, null, "lib-$name", "s-$name")

internal fun ref(name: String, id: String = "lib-$name") = ArtistRef(id, name)
