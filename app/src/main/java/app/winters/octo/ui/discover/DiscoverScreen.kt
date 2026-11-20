package app.winters.octo.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.winters.octo.design.OctoShapes
import app.winters.octo.ui.common.Artwork
import app.winters.octo.ui.common.ArtworkFill
import app.winters.octo.ui.common.playShuffleItems
import app.winters.octo.ui.home.songCountLine
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import app.winters.octo.design.OctoColors
import app.winters.octo.design.OctoIcons
import app.winters.octo.design.OctoType
import app.winters.octo.design.bodyType
import app.winters.octo.design.glassPanel
import app.winters.octo.discover.DiscoverArtist
import app.winters.octo.discover.DiscoverBatch
import app.winters.octo.discover.DiscoverSection
import app.winters.octo.discover.SectionKind
import app.winters.octo.discovery.OnlineArtist
import app.winters.octo.discovery.Station
import app.winters.octo.discovery.isRadio
import app.winters.octo.ui.common.ArtistCircle
import app.winters.octo.ui.common.Choice
import app.winters.octo.ui.common.ChoiceRequest
import app.winters.octo.ui.common.LocalChoiceSheet
import app.winters.octo.ui.common.PageHeader
import app.winters.octo.ui.common.QuietButton
import app.winters.octo.ui.common.SectionTitle
import app.winters.octo.ui.common.SongCard
import app.winters.octo.ui.common.SongRow
import app.winters.octo.ui.common.screenPadding
import app.winters.octo.ui.home.StationCard
import app.winters.octo.ui.menu.NOT_INTERESTED
import app.winters.octo.ui.menu.SongMenuContext
import app.winters.octo.ui.nav.ArtistRoute
import app.winters.octo.ui.nav.ChartsRoute
import app.winters.octo.ui.nav.OnlineArtistRoute
import app.winters.octo.ui.nav.ServerListRoute

// For You shows this many rows until "Show all".
private const val FOR_YOU_FOLDED = 8

// The Discover tab: new music picked from what is played, headed by the
// batch's curated playlists (each opening its own page), then as the Music
// Space's Discover lays it out (For You, artists along the chains, the genre
// lanes), with the server's own lists and chart after. Built from Home's
// parts in Octo's look. It draws the batch kept on disk from its first
// frame; Refresh (or a pull) makes the next generation while this one stays.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(onOpen: (NavKey) -> Unit, vm: DiscoverViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val runsOcto by vm.runsOcto.collectAsStateWithLifecycle()
    val dismissed by vm.dismissed.collectAsStateWithLifecycle()
    // Kept with the tab's page, so coming back finds it where it was.
    val list = rememberLazyListState()

    LifecycleResumeEffect(Unit) {
        vm.onShown()
        onPauseOrDispose { }
    }

    // A curated playlist's page, opened in Discover's own place (by its key,
    // kept across a restart) and closed by Back. It shows the batch's copy;
    // when a new batch lands without it, the copy last seen stays.
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastOpen by remember { mutableStateOf<DiscoverSection?>(null) }
    val shownBatch = (ui as? DiscoverUi.Ready)?.batch
    val playlist = playlistShown(openKey, shownBatch, lastOpen, dismissed)
    LaunchedEffect(playlist) { if (playlist != null) lastOpen = playlist }
    // Kept open across a restart but no longer in the batch read back: closed.
    LaunchedEffect(openKey, ui) { if (openKey != null && playlist == null && ui !is DiscoverUi.Reading) openKey = null }
    BackHandler(enabled = openKey != null) { openKey = null }
    if (openKey != null && playlist != null) {
        PlaylistPage(playlist, vm, onBack = { openKey = null })
        return
    }

    PullToRefreshBox(
        isRefreshing = vm.pulled,
        onRefresh = vm::pull,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = screenPadding()) {
            item(key = "header") {
                PageHeader(
                    "Discover",
                    onBack = null,
                    trailing = { if (ui !is DiscoverUi.SignedOut) RefreshButton(busy = making(ui), onClick = vm::refresh) },
                )
            }
            when (val shown = ui) {
                DiscoverUi.SignedOut -> note("signed-out", "Nothing to discover yet", "Sign in to a server, and Discover finds new music from what you play.")
                // Only for a moment at start, before the kept batch is read.
                DiscoverUi.Reading -> Unit
                is DiscoverUi.NoBatch ->
                    if (shown.making) {
                        note("making", "Finding new music for you", "Looking along the artists you play. This takes a moment the first time.")
                    } else {
                        emptyNote(runsOcto)
                    }
                is DiscoverUi.Ready -> {
                    item(key = "updated") { UpdatedLine(shown.batch, shown.making) }
                    if (shown.batch.sections.isEmpty()) emptyNote(runsOcto)
                    // The curated playlists head the tab; the rest follow as before.
                    playlistGrid(shown.batch.sections.filter { it.kind == SectionKind.Playlist }) { openKey = it.key }
                    shown.batch.sections.forEach { section(it, vm, onOpen) }
                }
            }
        }
    }
}

// Whether a batch is being made now, for the Refresh button.
private fun making(ui: DiscoverUi): Boolean = when (ui) {
    is DiscoverUi.Ready -> ui.making
    is DiscoverUi.NoBatch -> ui.making
    else -> false
}

// When the batch was made, under the title, and that a new one is on its
// way; then what its picks are seeded by.
@Composable
private fun UpdatedLine(batch: DiscoverBatch, making: Boolean) {
    val words = updatedLine(batch.generatedAt, System.currentTimeMillis()) + if (making) " · Finding more…" else ""
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(words, style = bodyType(OctoType.caption), color = OctoColors.TextMuted)
        seededLine(batch)?.let { Text(it, style = bodyType(OctoType.caption), color = OctoColors.TextMuted) }
    }
}

// Refresh, at the end of the title's line: asks for the next generation
// (other playlists, other songs). While a batch is being made it turns a
// small spinner in place of its icon and does nothing more; the batch on
// screen stays until the new one lands.
@Composable
private fun RefreshButton(busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .glassPanel(CircleShape)
            .clickable(enabled = !busy, role = Role.Button, onClickLabel = "Find new playlists", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(color = OctoColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(painterResource(OctoIcons.Sync), contentDescription = null, tint = OctoColors.TextPrimary, modifier = Modifier.size(18.dp))
        }
        Text("Refresh", style = OctoType.label, color = OctoColors.TextPrimary, maxLines = 1)
    }
}

// The curated playlists, two tiles a row, under their own title.
private fun LazyListScope.playlistGrid(playlists: List<DiscoverSection>, onOpen: (DiscoverSection) -> Unit) {
    if (playlists.isEmpty()) return
    item(key = "title:playlists") {
        Column(Modifier.padding(top = 18.dp)) {
            SectionTitle("Playlists for you")
            Text(
                "${playlists.size} playlists from what you play. Refresh for others.",
                style = bodyType(OctoType.caption),
                color = OctoColors.TextMuted,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp),
            )
        }
    }
    playlists.chunked(2).forEach { pair ->
        item(key = "playlists:${pair.first().key}") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                pair.forEach { PlaylistTile(it, Modifier.weight(1f)) { onOpen(it) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// One playlist: a square of its first covers (four when it has four
// different ones), its name, and its line.
@Composable
private fun PlaylistTile(playlist: DiscoverSection, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val covers = remember(playlist) { playlist.songs.mapNotNull { it.artwork }.distinct().take(4) }
    Column(modifier.clickable(role = Role.Button, onClickLabel = "Open ${playlist.title}", onClick = onClick)) {
        if (covers.size < 4) {
            ArtworkFill(covers.firstOrNull())
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(OctoShapes.ArtM)) {
                val half = maxWidth / 2
                Column {
                    covers.chunked(2).forEach { row ->
                        Row { row.forEach { Artwork(it, half, shape = RectangleShape, rim = false) } }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.title,
            style = bodyType(OctoType.bodySmall.copy(fontWeight = FontWeight.SemiBold)),
            color = OctoColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        playlist.subtitle?.let {
            Text(it, style = bodyType(OctoType.caption), color = OctoColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// A curated playlist's own page: its name with Play and Shuffle, its line,
// and its songs as rows with the add button and Not interested, as For You.
// A song turned down here leaves the playlist (and every section) at once.
@Composable
private fun PlaylistPage(playlist: DiscoverSection, vm: DiscoverViewModel, onBack: () -> Unit) {
    val menu = remember(vm) { pickMenu(vm) }
    val songs = playlist.songs
    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding()) {
        item(key = "header") {
            PageHeader(
                playlist.title,
                onBack = onBack,
                count = songCountLine(songs.size),
                tail = playShuffleItems(
                    onPlay = { vm.playPlaylist(playlist) },
                    onShuffle = { vm.playPlaylist(playlist, shuffle = true) },
                    enabled = songs.isNotEmpty(),
                ),
            )
        }
        playlist.subtitle?.let { line ->
            item(key = "line") {
                Text(line, style = bodyType(OctoType.caption), color = OctoColors.TextMuted, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
            }
        }
        if (songs.isEmpty()) {
            note("emptied", "Nothing left here", "Every song in this playlist was turned down. Refresh Discover for new playlists.")
        }
        itemsIndexed(songs, key = { _, track -> track.id }) { index, track ->
            SongRow(track, menuContext = menu, offerAdd = true, onClick = { vm.playPlaylist(playlist, index) })
        }
    }
}

private fun LazyListScope.section(section: DiscoverSection, vm: DiscoverViewModel, onOpen: (NavKey) -> Unit) {
    when (section.kind) {
        SectionKind.ForYou -> forYou(section, vm)
        SectionKind.Artists -> artistShelf(section, vm, onOpen)
        SectionKind.Lane -> songShelf(section, vm)
        SectionKind.Charts -> songShelf(section, vm, onTitle = { onOpen(ChartsRoute) })
        SectionKind.MadeForYou, SectionKind.Stations -> stationShelf(section, vm, onOpen)
        // Drawn together at the top, as the playlist grid.
        SectionKind.Playlist -> Unit
    }
}

// The menu of a Discover pick: the song's own, ending with Not interested.
private fun pickMenu(vm: DiscoverViewModel) = SongMenuContext(onNotInterested = vm::notInterested)

// For You as song rows, each with the add button for a song not in the
// library. Folded to its first rows until "Show all".
private fun LazyListScope.forYou(section: DiscoverSection, vm: DiscoverViewModel) {
    item(key = "title:${section.key}") { Titles(section) }
    item(key = "rows:${section.key}") {
        var open by rememberSaveable { mutableStateOf(false) }
        val menu = remember(vm) { pickMenu(vm) }
        val songs = section.songs
        val shown = if (open) songs else songs.take(FOR_YOU_FOLDED)
        Column {
            shown.forEachIndexed { index, track ->
                key(track.id) {
                    SongRow(track, menuContext = menu, offerAdd = true, onClick = { vm.play(songs, index) })
                }
            }
            if (songs.size > FOR_YOU_FOLDED) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    QuietButton(if (open) "Show fewer" else "Show all ${songs.size}") { open = !open }
                }
            }
        }
    }
}

// A row of songs as cards: a genre lane, or the chart. Tapping one plays
// the row from that song; a long press opens its menu.
private fun LazyListScope.songShelf(section: DiscoverSection, vm: DiscoverViewModel, onTitle: (() -> Unit)? = null) {
    item(key = "title:${section.key}") { Titles(section, onTitle) }
    item(key = "row:${section.key}") {
        val menu = remember(vm) { pickMenu(vm) }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(section.songs, key = { _, track -> track.id }) { index, track ->
                SongCard(track, onClick = { vm.play(section.songs, index) }, menuContext = menu)
            }
        }
    }
}

// Artists along the chains: a library one opens in the library, any other
// as the server has them. A long press offers Not interested.
private fun LazyListScope.artistShelf(section: DiscoverSection, vm: DiscoverViewModel, onOpen: (NavKey) -> Unit) {
    item(key = "title:${section.key}") { Titles(section) }
    item(key = "row:${section.key}") {
        val sheet = LocalChoiceSheet.current
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(section.artists, key = { it.libraryId ?: "online:${it.serverId}" }) { artist ->
                ArtistCircle(
                    OnlineArtist(artist.serverId, artist.name, 0, artist.artwork),
                    outside = artist.libraryId == null,
                    onLongClick = { sheet.show(notInterestedAsk(artist) { vm.notInterested(artist) }) },
                ) {
                    onOpen(artist.libraryId?.let(::ArtistRoute) ?: OnlineArtistRoute(artist.serverId))
                }
            }
        }
    }
}

private fun notInterestedAsk(artist: DiscoverArtist, onPick: () -> Unit) =
    ChoiceRequest(artist.name, listOf(Choice(NOT_INTERESTED, detail = "Keep ${artist.name} out of Discover")), selected = -1) { onPick() }

// The server's lists or stations: a tap opens what it has lined up, the
// play button plays it, as on Home.
private fun LazyListScope.stationShelf(section: DiscoverSection, vm: DiscoverViewModel, onOpen: (NavKey) -> Unit) {
    item(key = "title:${section.key}") { Titles(section) }
    item(key = "row:${section.key}") {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(section.lists, key = { it.id }) { station: Station ->
                StationCard(
                    station,
                    starting = station.id == vm.startingStation,
                    onOpen = { onOpen(ServerListRoute(station.id, station.name, station.artwork, radio = station.isRadio)) },
                    onPlay = { vm.playStation(station) },
                )
            }
        }
    }
}

// A section's title, as a button to its own page when it has one, and its
// line under it.
@Composable
private fun Titles(section: DiscoverSection, onTitle: (() -> Unit)? = null) {
    Column(Modifier.padding(top = 18.dp)) {
        if (onTitle != null) {
            Row(
                Modifier.clickable(role = Role.Button, onClickLabel = "Open ${section.title}", onClick = onTitle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(section.title)
                // Drawn into the title's own end margin, close to the words, as on Home.
                Icon(
                    painterResource(OctoIcons.Chevron),
                    contentDescription = null,
                    tint = OctoColors.TextMuted,
                    modifier = Modifier.offset(x = (-16).dp).size(22.dp),
                )
            }
        } else {
            SectionTitle(section.title)
        }
        section.subtitle?.let {
            Text(it, style = bodyType(OctoType.caption), color = OctoColors.TextMuted, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp))
        }
    }
}

// A card with a heading and a line, for a tab with nothing to show.
private fun LazyListScope.note(key: String, heading: String, words: String) {
    item(key = key) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .fillMaxWidth()
                .glassPanel(RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(heading, style = bodyType(OctoType.bodySmall), color = OctoColors.TextPrimary)
            Text(words, style = bodyType(OctoType.caption), color = OctoColors.TextSecondary)
        }
    }
}

// Nothing found: why that can be, and what helps.
private fun LazyListScope.emptyNote(runsOcto: Boolean) = note(
    "empty",
    "Nothing to suggest yet",
    if (runsOcto) {
        "Play some music, then pull down to look again."
    } else {
        "Play some music, then pull down to look again. Discover finds artists and songs through your server's " +
            "similar-artist data; on Navidrome that needs its Last.fm (or another) agent turned on."
    },
)

// "Updated just now", "Updated 5 min ago", "Updated 3 h ago", "Updated yesterday", "Updated 4 days ago".
internal fun updatedLine(generatedAt: Long, now: Long): String {
    val minutes = ((now - generatedAt).coerceAtLeast(0)) / 60_000
    return when {
        minutes < 1 -> "Updated just now"
        minutes < 60 -> "Updated $minutes min ago"
        minutes < 24 * 60 -> "Updated ${minutes / 60} h ago"
        minutes < 48 * 60 -> "Updated yesterday"
        else -> "Updated ${minutes / (24 * 60)} days ago"
    }
}
