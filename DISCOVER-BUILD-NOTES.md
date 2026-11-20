# Discover tab v1: build notes

*Branch `feature/discover-v1`, 2026-11-20. Built by reading the code only: **no Gradle was run and nothing was pushed.** The first real compile and test run will be the CI `build-debug` workflow (`assembleDebug` plus `:shared:core:allTests :app:testDebugUnitTest`).*

## What was built

1. **A 5th tab.** The order is Home · Discover · Search · Library · Settings, with Discover at index 1. It has its own back stack, keeps its state, and uses a Phosphor compass icon.
2. **The Discover engine**, as pure functions in `discover/DiscoverEngine.kt`:
   - **Seed weighting** from play totals, recent plays, favourite artists, liked songs and 4★/5★ songs.
   - **Similar-artist chains**, walked 2 hops via `getArtistInfo2`. Artists outside the library come first; library artists only fill a short row; seeds and dismissed artists never appear.
   - **Genre lanes** from the server's genre tags.
   - **Section building** and **dismissal filtering**.
3. **`DiscoverRepository`** gathers the inputs, makes a batch and keeps it on disk:
   - Signals come from `PlayHistory`, `FavouritesDao`, `UserDao` and the new `CatalogDao.topRatedTracks`.
   - Chains go through `ArtistExtrasSource`.
   - Songs come from `getSimilarSongs2` (strongest seeds and chain artists) and `getTopSongs` (chain artists), resolved through `Discovery.resolveInPlace`, so library songs show as library songs and the rest as finds.
   - Made for you and Stations come from `Discovery`, Charts from `ChartLists`.
4. **Persistent cache** in a new, separate Room database, `discover.db`:
   - `discover_batch` + `discover_section`: the batch, per server + account, with when it was made.
   - `discover_dismissed`: "Not interested" picks, per server + account.
   - `artist_extras_cache`: `ArtistExtrasSource` now keeps artist info and top songs on disk, per server + account and artist, with a timestamp.
5. **The screen** (`ui/discover/DiscoverScreen.kt`), built from existing parts in Octo's current look:
   - **For You** as `SongRow`s with **+ Add to library**, folded to 8 rows with "Show all".
   - **Artists to explore** as `ArtistCircle`s, opening `ArtistRoute` or `OnlineArtistRoute`.
   - **Genre lanes** and **Charts** as `SongCard` rows. The Charts title opens `ChartsRoute`.
   - **Made for you** and **Stations** as `StationCard`s, opening `ServerListRoute` or playing as Home does.
   - An "Updated N ago" line.
   - Pull-to-refresh.
6. **"Not interested"** on every pick:
   - **Songs:** a new item in the existing long-press song menu, shown only where Discover passes a handler.
   - **Artists:** a long press opens the existing choice sheet.
   - Both have Undo. Dismissals persist and are filtered out of every later batch, and out of the batch on screen at once.

### Sections (v1), in order, each shown only when it has content
For You → Artists to explore → lanes (Country, Folk, Bluegrass, Blues, Indie, Rock, Phonk, Hip-Hop, Electronic, Soul, Jazz, Oldies, Latin, Reggae) → Made for you → Stations → Charts.

## Files

**Added**
- `app/.../discover/DiscoverModel.kt`: lanes, section kinds, `DiscoverSection`, `DiscoverBatch`, `Dismissed`.
- `app/.../discover/DiscoverEngine.kt`: seeds, chains, lanes, sections, dismissals, staleness.
- `app/.../discover/DiscoverTables.kt`: entities, `DiscoverDao`, `DiscoverDatabase`.
- `app/.../discover/DiscoverRepository.kt`
- `app/.../di/DiscoverModule.kt`
- `app/.../ui/discover/DiscoverScreen.kt`, `DiscoverViewModel.kt`, `DiscoverShellViewModel.kt`
- `app/.../ui/nav/TabOrder.kt`: every tab index, named.
- `design/src/main/res/drawable/sym_discover.xml`, `sym_discover_filled.xml`
- Tests: `app/src/test/.../discover/` (`SeedWeightsTest`, `ChainTest`, `LanesTest`, `SectionsTest`, `DismissalsTest`, `DiscoverFixtures`), `ui/nav/TabOrderTest`, `ui/discover/DiscoverUiTest`, `ui/menu/NotInterestedTest`.

**Changed (kept small)**
- `ui/nav/Routes.kt`: adds `DiscoverRoute`.
- `ui/nav/MainShell.kt`:
  - Discover's stack and `entry<>`.
  - `searchTab` → `TabOrder.SEARCH` (2) and the Home index → `TabOrder.HOME`.
  - A guard so a saved tab index from a 4-tab build is never out of range.
  - Creates `DiscoverShellViewModel` so the kept batch is read at app start.
- `ui/nav/BottomBar.kt`: the Discover tab, and the 62dp pill capped at a column's width (`tabCapsuleWidth`).
- `ambient/AmbientSource.kt`: Discover glows as Home does.
- `ui/menu/SongMenu.kt`, `SongMenuPopup.kt`: `SongAction.NotInterested`, `SongMenuContext.onNotInterested`, and `songActions(notInterested = …)`.
- `ui/common/Items.kt`: `SongCard` takes an optional `menuContext`; an online `ArtistCircle` takes an optional `onLongClick`. The defaults keep every caller as before.
- `discovery/ArtistExtras.kt`:
  - Disk cache, background re-read when stale, and pull-to-refresh skipping the kept copy.
  - `similarToServerArtist()` for the second hop.
  - `@Serializable` on `ArtistExtras` and `SimilarArtist`.
- `catalog/CatalogDao.kt`: `topRatedTracks(min, limit)`.
- `design/.../OctoIcons.kt`, `res/values/strings.xml` (`tab_discover`), `tools/icons/icons.json`.

## Design choices (and why)

- **A separate `discover.db`, not catalog tables.** Adding tables to `CatalogDatabase` would mean bumping `CATALOG_VERSION` to 21 with a migration. It would also collide with any upstream catalog change, which makes merges painful. A separate database is additive and has no migration. The trade-off: the catalog's backup/restore doesn't include Discover. That's fine for caches, but means dismissals aren't backed up (see open questions).
- **Owner key = kept-server id + username.** This follows "everything the phone keeps for the account is kept under it" (`Session.id`). The artist-extras cache keeps `ArtistExtrasSource`'s existing key, `username@baseUrl`, so its behaviour didn't change.
- **First frame has content.** `DiscoverShellViewModel` lives in `MainShell`, so the repository starts with the app and reads the kept batch from disk within milliseconds. The tab's ViewModel takes the repository's current value as its `StateFlow` initial value, so the first frame draws the batch, with no loading flash. Tab state stays when switching away and back:
  - The per-tab view-model store and saveable state keep it.
  - The `LazyListState` is saveable.
  - `WhileSubscribed` keeps the last value.
- **Refresh policy.** A batch is fresh for **6 hours**. A missing or stale batch is made in the background 3s (none kept) or 8s (stale) after start or a server switch, so it never competes with the app opening. It is also made when the tab resumes stale, or on a pull.
  - A pull shows its spinner until that batch lands. A background refresh only adds "· Finding more…" to the Updated line.
  - **A batch that comes back empty never replaces a non-empty one**, so Discover never goes blank while the server is out of reach.
  - Made for you, Stations and Charts that fail to load keep the previous batch's copy.
- **Artist extras.** Fresh for 3 days; after that the kept copy shows at once and a background read replaces it. Rows not read for 60 days are pruned.
  - A pull on an artist page still asks the server, and falls back to the kept copy if the server can't answer.
- **Weights.** play totals `4·ln(1+plays)` > recent plays `2.5·ln(1+n)` > favourite artist `1.5` > highly rated songs (`0.5` per star above 3, at most 2) ≈ liked songs (`0.5` each, at most 1.5).
  - The tiers hold for any single signal, and several signals add up.
  - Logs keep one heavily played artist from drowning the others.
  - Top 8 seeds. The 2nd hop starts from the best 4 first-hop artists outside the library, at half weight.
- **For You / lanes** put songs **not in the library first**, then by score. At most 2 per artist in For You and 3 in a lane, taken in turns. Seed artists' own songs are left out, since this is new-music discovery. A lane needs at least 3 songs.
- **Genre lanes:**
  - **Whole-word matching**, most specific lane first: "Country Rock" → Country, "Indie Rock" → Indie, "Dubstep" → Electronic, not Reggae.
  - **Era-based Oldies:** pre-1980 songs whose genre is Rock, Soul or none go to Oldies.
  - **The genre and year come from the server's raw `Song`**, because a find's `TrackEntity` drops both. Octo's existing *genre mixes* are server-built Octo playlists, not per-song classification, so they didn't fit the lanes and weren't reused.
- **Artwork** goes through the app's own Coil `SingletonImageLoader`. After each batch, the first 40 server covers are prefetched at `AheadCoverBucket`, the way `CoversAhead` does it, so the disk cache is shared with every `Artwork`.
- **Server calls** run 4 at a time. One source failing, such as a non-Octo server or a missing Last.fm agent, only drops its own section.

## Left out of v1 (on purpose)

- **The Music Space's AI-music filter.** It was Spotify-specific and admitted to be best-effort; nothing in Subsonic data supports it.
- **Per-lane "See all" pages and lane-specific generation**, such as asking the server for genre-seeded songs. Lanes are built from the same pool as For You, so thin data means fewer lanes.
- **A "turned down" list to review or undo old dismissals.** Undo exists only in the moment. The table keeps a `label` so this can be added later.
- **A Settings switch to show or hide Discover**, and Android Auto / home-widget Discover entries.
- **WorkManager-scheduled refresh** while the app is closed. Refresh runs while the app is running, on top of the 6h staleness.
- **The visual reskin**: that's Phase 2.

## Open questions / things to check on the first CI run and on the phone

1. **Compile check.** This is the first build of the whole thing, and it was written without a compiler. Expect possibly a few small compile fixes. The spots I'm least sure of are noted inline (explicit generic types added where inference was in doubt).
2. **Room schema export.** `DiscoverDatabase` has `exportSchema = true`, so the CI build will write `app/schemas/app.winters.octo.discover.DiscoverDatabase/1.json`. Commit that file from a local or CI build once one exists. Any future version bump **must migrate `discover_dismissed`**; it holds the user's choices, not cache.
3. **Bar fit on the Pixel.** At 5 tabs on a ~412dp screen, each column is about 59dp, so the pill is 59dp (it was 62dp). Please eyeball it on the phone: the glide animation is unchanged, but the pill is slightly narrower.
4. **Plain Navidrome vs. the Octo proxy.** Without Navidrome's Last.fm agent, the chains and similar songs come back empty and only Charts / Made for you / Stations (Octo only) remain. The empty-state note says so. Which server does Collin actually use?
5. **First generation time.** The first batch costs around 40–50 server calls (4 in flight). Later batches reuse the 3-day artist-extras cache. Worth timing on the phone.
6. **Kept finds.** A kept batch plays finds by id, the same as Home's kept Charts shelf. Finds stay playable for 30 days after they were last seen (`Downloads.FORGET_MS`), well beyond the 6h refresh.
7. **Discover data isn't cleared when a server is removed**, and isn't included in backup/restore. Should dismissals be backed up?
8. **Unrelated to my work:** `BUILD-LOG.md` had uncommitted edits from the coordinator (a CI-run row and notes). I left them uncommitted. Another session also committed `09aadcb4` (PlayerUpdaterTest fix) onto this branch while I worked. I didn't touch it.
