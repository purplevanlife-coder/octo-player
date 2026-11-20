# Octo fork: assessment for Discover tab and Music Space reskin

*Coder assessment, 2026-11-19. Read-only session: no app code changed, no builds run, nothing pushed.*

> ## ⚠️ Read this first: the design reference was NOT readable
> This session's sandbox only allowed `~/workspace/octo-fork-work`. Every attempt to open
> `~/workspace/music-space-ui-reference/` (`MUSIC_SPACE_REFERENCE.md`, `client-src/App.tsx`, `theme.css`, the README) was **blocked by permissions**.
> So this document covers the **Octo side** in full: features, plumbing and architecture.
> The **Music Space side** (what Discover shows; the colours, cards and tab layout) is written as a plan with blanks.
> **Next step:** re-run the design half with that folder allowed, or copy it into this repo, e.g. `docs/music-space-ref/`, git-ignored.
> That is about 30 minutes of work and fills in sections (c) and (d) concretely.

---

## (a) The two phases, as I understand them

| Phase | What | Done when |
|---|---|---|
| **1. Discover tab** | A new bottom-bar tab, modeled on the Music Space's Discover, built from the Subsonic/Octo data the app already fetches (similar songs, top songs, artist info, stations, Made for you, charts). | Collin opens Discover on his phone and it behaves like the Music Space's Discover, loads from cache instantly, and nothing else in the app has changed. |
| **2. Reskin** | Restyle the whole app to look very like the Music Space: colours, type, cards, tab bar, player bar. Translate the React/CSS into Compose; don't port it. | It looks like the Music Space, and **every item in section (b) still works**. That list is the acceptance checklist. |

Order matters. Phase 1 is additive and low-risk. Phase 2 touches every screen.

---

## (b) Inventory: every user-facing Octo feature on Android (the Phase 2 checklist)

Built from the code: `ui/nav/Routes.kt` (34 destinations), `ui/settings/SettingsIndex.kt` (every setting), the song and collection menus, the player, and the system integrations.

### Navigation shell
- [ ] Floating glass bottom bar with **4 tabs**: Home, Search, Library, Settings. A selected-tab pill glides between tabs; tapping the current tab again goes back to its root.
- [ ] Each tab keeps its own back stack, and Back on a non-Home root returns to Home.
- [ ] A round now-playing button with a progress ring, the song's artwork and a pulse while paused.
- [ ] Tapping that button folds the tabs into one circle and grows a **mini player**: art, scrolling title, prev / play / next. For a live station it shows stop and change-station.
- [ ] Mini-player gestures: swipe sideways to skip, swipe up or tap to open the full player, with haptic ticks.
- [ ] Tab badge dot, e.g. "server update waiting" on Settings.
- [ ] Ambient glow from the artwork behind pages, with a per-page toggle.

### Home (18 shelves you can reorder and hide, plus cards)
- [ ] Shelves: Pinned · Recently played · Made for you · Radio (Octo stations) · Live radio · Year mixes · Genre mixes · Artist mixes · Country mixes · Recently added · Most played · Favorite albums · Charts · Artists · Something different · Not played in 6 months · Albums you never finished · Unplayed albums.
- [ ] **Home sections editor**: drag to reorder, show or hide, reset to default.
- [ ] Resume card, What's new card, copy/import progress cards, rediscovery.
- [ ] Pin any album, artist or playlist to Home, and move a pin to the front.

### Search
- [ ] One search across library and server, filtered by All / Songs / Albums / Artists / Playlists.
- [ ] "Not in your library" section with songs, albums and artists Octo found online, kept separate from partly-owned albums.
- [ ] Recent searches. Settings also appear in search results and jump to their row.
- [ ] Take in music that is "arriving" (on the server but not yet in the local copy).

### Library
- [ ] Hub linking to Playlists, Favourites, Artists, Albums, Songs, Genres → Genre, Folders, Downloads, History (by day / most played), and Library health.
- [ ] Paged lists with letter jump, sorting, song filter row, multi-select with a selection bar.
- [ ] **Favourites**: liked songs, favourite albums, favourite artists.
- [ ] **Library health**: checks, per-check songs, fix plans, fix all, re-check, recently removed and trash, phone-file health.
- [ ] Merged phone and server library: local music scan, music folder exclusions, "library songs only" toggle.

### Album, artist, online pages
- [ ] Album page: discs, extras, "songs you have" marks, add the rest, download, share, find FLAC upgrades, genre link.
- [ ] Artist page: top songs, biography, similar artists (from `getArtistInfo2` / `getTopSongs`).
- [ ] Online album and online artist pages for music not in the library, with **Add to library (+)**.
- [ ] Server list page for stations and Made-for-you lists. A station keeps going; a list ends.
- [ ] Charts page, with country choice and pinned countries.

### Playlists
- [ ] Playlists, playlist folders (incl. move to folder), and playlist page edits (reorder, remove, rename, duplicate, delete).
- [ ] **Live lists**: lists built from rules, with an editor. Can start from a page's filters.
- [ ] Import M3U and playlists, "keep downloaded", designed covers or album mosaics, add to the last-used playlist.
- [ ] **Import** from Spotify, Apple Music and others (TuneMyMusic file, links, share-to-import), with fetching of missing songs.

### Song menu (long-press, context-dependent)
- [ ] Play next · Add to queue · Start radio · Add to playlist / last playlist · Remove from playlist · Select · Like · Rate (stars) · Download / Request a copy · Keep offline · Find FLAC · Share link · Share file · Set as ringtone/sound · Go to album · Go to artist · Info (song details) · Find songs · Try another version (online finds) · Delete from phone · Remove from my library · Delete from server disk · Cast · Sound.
- [ ] Swipe a row right to Play next or Add to queue (configurable).

### Collection menu (album / artist / playlist)
- [ ] Play · Shuffle · Play next · Add to queue · Add to playlist · Download · Find FLAC · Start radio · Favourite / unfavourite · Pin to Home / Unpin / Move to front · Go to artist · Rename · Duplicate · Delete · Move to folder.

### Full player
- [ ] Opens with a shared-element artwork flight from the bar. Pull down to close. Landscape layout.
- [ ] Artwork: swipe to skip, shrinks when paused, **animated cover loops** (square and tall).
- [ ] Immersive background (wash, mesh, classic; live drift; moves with BPM) plus brightness, saturation and contrast caps.
- [ ] Title, artist and album links; heart or + add; star rating line; More menu.
- [ ] Scrubber with times, live bar for stations, volume line.
- [ ] Switch row: **Lyrics**, **Up next (queue sheet with edits)**, **Sleep timer**, Shuffle, Repeat (off / all / one).
- [ ] Speed and pitch sheet, stream-versions sheet, quality badge, output/cast sheet.
- [ ] **Lyrics**: flowing word-by-word synced lyrics with emphasis, glow, lift, ripple and curve, a classic style, an online lookup, choosing lyrics candidates, and per-output timing offset.

### Playback & sound engine (must keep working under any skin)
- [ ] Gapless / Crossfade / **Automix** (curves, filter sweeps, tempo match), skip silence, autoplay of similar songs.
- [ ] Song radio with Discovery / Adventure / Variety / Favorites sliders.
- [ ] Live radio: stations near you (Radio Browser), server radio stations.
- [ ] **Sound page**: EQ with presets, loudness/ReplayGain, balance, crossfeed, headphone correction, per-output sound.
- [ ] Headphone/Bluetooth auto-resume.
- [ ] Casting: Chromecast and DLNA/UPnP renderers.
- [ ] Offline: downloads, cache size, fetch-ahead on Wi-Fi/mobile, Wi-Fi-only downloads, keep Liked downloaded, download quality and format, choosing which copy plays.
- [ ] Audio quality per account (home/away) and per app (Wi-Fi/mobile).
- [ ] Queue sync with the server (hand-off between devices), play reporting, ListenBrainz scrobbling.

### Server, account, family, admin
- [ ] Multiple servers with one-tap switching, sign-in, edit connection, client certificates, home/away connection, change password.
- [ ] Sign in on another device by QR, family link join.
- [ ] **Family**: plan, requests, devices, members (manager).
- [ ] **Octo admin** page and server-update panel.
- [ ] Shared links, scan library now, sync now, "listening now", music folder limit.

### Settings (12 pages, all searchable)
- [ ] Playback · Sound · Radio · Appearance (themes Octo/Moss/Dusk/Daylight/Paper, match system, accent colour, colour the glass, reduce motion, playlist covers, player background) · Library · Server and sync · Streaming and downloads · Audio quality · Lyrics · Scrobbling · Backup and restore · About (version, what's new, auto-update, early versions).

### Outside the app UI (no reskin needed, but nothing may break)
- [ ] Media notification with a heart, lock screen controls.
- [ ] **Home-screen widgets**: Now Playing and Quick Play.
- [ ] **Android Auto** (`playback/Car*`, ~25 files: tabs, search, shelves, voice).
- [ ] Launcher shortcuts, "Open with" for audio files, share-to-import.
- [ ] In-app updater, backup/restore of settings, playlists, likes and favourites.

---

## (c) How I'd build the Discover tab (Phase 1)

### What already exists
| Need | Existing plumbing |
|---|---|
| Similar songs / song radio | `Discovery.radio(seed)` uses `getSimilarSongs2`. The radio sliders live in `playback/RadioSeeds`. |
| Artist top songs, bio, similar artists | `ArtistExtrasSource.forArtist()` uses `getTopSongs` + `getArtistInfo2`, cached per server. |
| Found-online songs, albums, artists | `Discovery.search()`, `Discovery.artist()`, online album pages, **+ Add to library** button (`AddToLibraryButton`). |
| Stations, Made for you, Octo list groups | `Discovery.stations()`, `madeForYou()`, `octoLists()`. Home's `Stations.kt` and `StationCover` render them. |
| Charts | `ChartLists` (+ country), `SearchTopSongs.chart()`. |
| Listening signals for seeds | `listening/PlayShelves`, `PlayTotals`, `History`, likes and ratings. |
| Shelves / cards / menus | Home's shelf composables, `Artwork`, song and collection menus, the `ServerListRoute` page. |
| Local caching | Room catalog (`catalog/ServerListTables`, `OnlineDao`), the server-list drain/fill in `ServerLists`. |

### What is new
1. **`DiscoverRoute` + 5th tab.** Add it to `Routes.kt`, the `tabs` list in `BottomBar.kt`, and `stacks` + `entry<>` in `MainShell.kt`.
   Watch the hard-coded index assumptions: `searchTab = 1`, Settings = `stacks.last()`, Back → index 0. Put Discover at index 1 or 2 and fix those.
   The bar's tab width maths (`fullTabs / tabs.size`, a 62 dp pill) needs checking at 5 tabs on Collin's Pixel.
2. **`DiscoverViewModel` + `DiscoverRepository`.** This is the logic behind the Music Space's Discover: picking seeds from recent, top and liked artists, then building sections from similar songs, top songs and similar artists. *The exact sections and rules come from `MUSIC_SPACE_REFERENCE.md` (not read yet).*
3. **Caching, per the standing rule.** Persist every Discover section in Room, keyed per server and account, so the tab opens from cache instantly. Refresh in the background on a timer and on pull-to-refresh. Prefetch the artwork (Coil disk cache).
   The existing `ArtistExtrasSource` cache is **in-memory only**, so this layer is new work.
4. **Discover screen.** Built from existing parts (shelves, cards, song rows, menus, + Add). Phase 1 uses the current Octo look; Phase 2 restyles it with everything else.
5. **Tests.** Pure-Kotlin unit tests for seed picking and section building. There are 809 test files already, so the pattern is easy to follow.

**One dependency to confirm:** much of the discovery richness (finds, stations, Made for you) needs **the Octo proxy server**, not plain Navidrome. On plain Navidrome, `getSimilarSongs2`, `getTopSongs` and `getArtistInfo2` only return data when Navidrome's **Last.fm (or other) agent** is configured. See (e).

---

## (d) How I'd approach the Compose reskin (Phase 2)

### The good news: the app is built to be reskinned
- Visual tokens are centralised in the `:design` module: `OctoColors`, `OctoPalette`, `OctoTokens`, `OctoType`, `OctoTheme`, plus shared glass/glaze components, buttons, sliders and sheets.
- There's already a **theme system** (5 themes + accent + match-system) in `shared/core/.../theme/Themes.kt`.

### Plan
1. **Tokens first.** Convert `theme.css` variables (colours, radii, spacing, font sizes, shadows) into a new **"Music Space" `ThemeChoice` and palette**.
   Making it a theme rather than overwriting Octo's means the old look stays one tap away as a fallback.
2. **Components second.** Restyle the shared building blocks once: cards, shelf headers, song rows, buttons, sheets, chips/segmented controls, and the tab bar / player bar (`BottomBar.kt`, `NowPlayingFace.kt`).
   Most screens then change without anyone touching them.
3. **Layouts third, screen by screen.** Rearrange Home, Discover, Library, Search, Album, Artist and the full player toward `App.tsx`'s structure, ticking off section (b) after each.
4. **Verify against the checklist** on Collin's phone after each round. Screenshots go next to the Music Space.

### Where it won't translate 1:1 (general; specifics need the reference)
- **CSS → Compose:** `backdrop-filter` blur becomes the app's Haze glass. Blur on older Android is weaker.
  CSS grid and flex-wrap become `LazyVerticalGrid` and `FlowRow`. Hover states have no equivalent on touch.
  Web fonts must be bundled as font files.
- **Web vs phone layout:** a web UI often has a sidebar and wide columns. On a phone that becomes a bottom bar and single-column shelves.
  Features with no Music Space equivalent (Settings' 12 pages, Library health, Family, Admin, live lists, the lyrics engine, the immersive player) must be **styled to match, not removed**.
- **Octo's player is richer than a typical web player bar** (animated cover loops, immersive wash, flowing lyrics, Automix). The reskin should change its chrome, not drop its behaviour.
- **The 4-tab, soon 5-tab, Octo bar** may differ from the Music Space's tab set. Collin decides the final set (see (e)).

---

## (e) Risks, unknowns, decisions for Collin

### Blockers / unknowns
1. **I haven't seen the design reference** (permissions, see top). Needed before Phase 1 is specified and before any Phase 2 estimate is firm.
2. **Plain Navidrome or the Octo proxy?** If it's plain Navidrome, Discover depends on Navidrome's Last.fm agent being set up. Without it, similar songs and artist info come back empty, and the Octo-only shelves (stations, Made for you, found online) aren't available at all.
3. **Builds can only run in CI.** The Gradle daemon can't run on this VM, so every build is a push → GitHub Actions → install round-trip (several minutes each).
   The fork's `build-debug.yml` only runs `assembleDebug`, so **the unit tests don't run on our builds.** Upstream's `android-release.yml` does run `:shared:core:allTests :app:testDebugUnitTest`. I'd add that step to `build-debug.yml` early.
4. **Debug vs. installed build.** The CI debug APK is `app.winters.octo.debug`, a separate app from the installed `app.winters.octo`.
   It won't share his servers, library copy, downloads or settings. Either set it up fresh, use Backup → Restore, or set up signed release builds of the fork.
5. **The auto-updater points at upstream's public releases.** A fork built as `app.winters.octo` could be offered upstream updates. Those would fail on a signature mismatch at best, and confuse him at worst.
   **Recommend turning auto-update off or redirecting it in the fork.**
6. **Upstream drift.** Upstream Octo is very active. A deep reskin makes merging future upstream features painful. Decide now whether the fork tracks upstream or freezes.

### Decisions needed from Collin
- **Discover's tab position** (I'd suggest Home · Discover · Search · Library · Settings), and whether Phase 2 changes the tab set further.
- **Reskin as a new theme** (recommended: reversible, keeps Octo's themes) **or a replacement** of the Octo look.
- Whether **light themes** (Daylight, Paper) and the **accent colour** picker should survive the reskin. They're features on the checklist.
- The **Music Space background-playback rule**: Octo's playback service already keeps playing when the app is left, which satisfies it. Nothing in the reskin should change that.

---

## (f) Rough size

A "round" = edit → push → CI build → install → check on the phone.

| Phase | Work | Rounds |
|---|---|---|
| Prep | Read the design reference; add unit tests to CI; settle the install path and updater (e4–e5) | 1–2 |
| **1. Discover** | Route + 5th tab + bar fit · repository + Room cache + background refresh · screen from existing parts · unit tests · polish on device | **4–7** |
| **2. Reskin** | Tokens → new theme (1–2) · shared components + bar/mini player (3–4) · screens: Home, Discover, Search, Library and lists, Album/Artist/Online, Playlists/Live lists, full player and lyrics, sheets and menus, Settings, sign-in/Family/Admin (8–14) · checklist pass, fixes, light-theme pass (2–4) | **14–24** |

Phase 2's range is wide until the reference has been read. A reference that's close to Octo's existing structure lands near the low end.

---

## (g) Honest take

- **Phase 1 is very doable and fairly safe.** About 70% of the data plumbing already exists: similar songs, top songs, artist info, stations, charts and the Add-to-library flow. The real new work is the Discover logic, a persisted cache, and fitting a 5th tab into a carefully tuned floating bar.
- **Phase 2 is a big job, and it's mostly about not losing features rather than about the look.** The app is large (~1,450 Kotlin files, 34 destinations, around 150 features on the checklist) and highly polished, with custom glass, gestures, animated covers and a lyrics engine.
  The upside is a centralised `:design` module and an existing theme system. Doing it as a new theme, token-first and component-first, makes it manageable and reversible.
- **The biggest practical drag is the build loop, not the code.** CI is the only way to build, so each check costs minutes. Small, batched rounds and CI-run unit tests matter.
- **Before starting:** grant access to the reference folder, confirm Navidrome vs. Octo proxy, and settle the install/updater question. With those done I'm confident in Phase 1, and Phase 2 is a solid multi-week effort with a clear checklist to judge it by.
