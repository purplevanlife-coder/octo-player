# Phase 3 notes

*Branch `feature/discover-v1`, 2026-11-20. This file covers sections **2 and 3** of
`PHASE3-PLAYER-DISCOVER-BRIEF.md` (Discover playlists, Refresh, the Takeout seed). Sections
1, 4 and 5 (player and shell) are being done in a separate session, which adds its own parts.
No Gradle was run and nothing was pushed. The pure engine and its tests were also compiled and
run outside Gradle (details under "Risks for CI").*

## 2. Discover: curated playlists + Refresh

### What landed
- **Model:** a new `SectionKind.Playlist`. Each playlist section carries:
  - a title;
  - a one-line explainer (`subtitle`);
  - its songs: 8 to 20, or up to 30 for the listening-history playlist.

  Keys are `pl:<theme>#g<generation>`, for example `pl:lane:Country#g3`. `themeOfKey()` reads the
  theme back out. `DiscoverBatch` gains `generation` (default 0), and `Pick` gains `via` (the seed
  whose chain led to it). Both have defaults, so every existing caller and test is unchanged.
- **Engine:** `discover/DiscoverPlaylists.kt`, pure Kotlin like `DiscoverEngine`. It has these
  templates; a playlist with fewer than 8 songs is never emitted:
  | Family | Templates | Pool |
  |---|---|---|
  | History | "From your listening history" | the Takeout songs that are in the library, by play count (§3) |
  | Because | "Because you play {seed}" ×6 strongest seeds | picks whose `via` is that seed |
  | Rediscover | "Heavy rotation, rediscovered" | the 6 strongest seeds' other library songs, minus the most-played and history songs |
  | Fresh | "Fresh finds" | non-library picks |
  | Mood | Night drive (Electronic+Phonk), Front porch (Folk+Country+Bluegrass), Old souls (Oldies+Soul+Blues+Jazz), Turn it up (Rock+Hip-Hop+Phonk), Slow morning (Folk+Indie+Soul+Jazz) | lane unions |
  | Lane | "{Lane} finds" ×14 | one genre lane |
  | Deep | "Deep cuts: {seed}" | what that seed's Because playlist left over (lower-scored picks) |
  | Library | "Already in your library" | library songs the chains point back to |
  | Sampler | "One from each" | one pick per chain artist |

  That is up to about 38 candidates. Each explainer says only how the list was made ("… from
  along your chains", "Songs from artists like X"). There is no "Artists on the rise" and no
  invented claims.
- **Placement:** "Playlists for you" heads the tab, right after the header and updated line, as
  two-up tiles (2×2 cover mosaic, title, explainer). For You, Artists to explore, the lanes, Made
  for you, Stations and Charts are all still built and shown below, exactly as in v1.
- **Playlist page:** tapping a tile opens the playlist's page.
  - Header: the title, the song count, and Play / Shuffle.
  - Below it: the explainer, then `SongRow`s with + Add and the Discover "Not interested" menu,
    as in For You.
  - Plays go through the existing `playTracks` path, under the playlist's name.
  - Dismissing a song from the page filters it from the whole batch at once (the existing
    `without(dismissed)` path).
- **Refresh button:** "Refresh" with the existing sync icon, in the title line's trailing slot (as
  Home's "Edit Home"), in a glass capsule. While a batch is being made it shows a spinner and is
  disabled, and the updated line shows the existing "· Finding more…". Pull-to-refresh is still
  there and has the same meaning.
- **Generations:** the generation is stored on the batch row in `discover.db`.
  - Refresh or a pull makes generation N+1.
  - Start-up, the 6 h staleness remake and tab-resume remakes all keep the same generation: new
    data, same curation.
  - A Refresh pressed while a background batch is being made is queued and runs right after,
    instead of being dropped.
  - The existing rule still holds: an empty batch never replaces a good one.
  - A format-1 batch (v1, no playlists) still shows on first launch and is remade behind the tab.

### Rotation scheme
1. `playlistCandidates(input, g)` builds every eligible template in a fixed order: by family,
   then each family's own order.
2. `choosePlaylists(…)` pins the History playlist, since it's the tab's headline. The other
   candidates form a **ring that takes the families in turns** (first of each family, then second
   of each, …), so any stretch of the ring mixes kinds.
3. Generation g shows the stretch of `slots = 12 − pinned` starting at `(g × slots) mod ringSize`.
   When the ring is bigger than the slot count, the stretches for g and g+1 are offset by
   `slots`, which is between 0 and ringSize, so **the theme sets always differ**.
4. A manual refresh also passes the replaced batch's themes as `previous`. If changed inputs
   would still land on the same set, the stretch steps forward until it differs.
5. **Songs within a pool** rotate as well. A pool larger than its playlist starts
   `g × size/2` further in (wrapping), then the per-artist cap (2; 3 for lanes, 4 for
   history/rediscover) is applied with artists taking turns. A pool no bigger than the playlist
   keeps its songs and order. The history playlist is put back in play-count order after
   selection.
6. Everything is deterministic: there are no hash-order or random inputs, and every sort has a
   stable tie-break. The same inputs and generation give the same batch.

Tests in `PlaylistsTest`:
- (a) generations g and g+1 never repeat a theme set (checked for g = 0…20), and their keys don't
  overlap;
- (b) exactly 12 playlists, each with ≥ 8 songs, across generations;
- (c) determinism, including with the input order reversed;
- plus: the ≥ 8 rule, the history pin and its play order, the headliner rotation, the song-offset
  rotation, dismissed songs and seed artists' own songs left out, and the `previous` step-past.

### Deviations from the brief, and why
- **The playlist page is not a new nav route.** `MainShell.kt` registers every `entry<>`, and it
  was off-limits this round, so the page is drawn inside `DiscoverScreen`:
  - the open key is kept in `rememberSaveable`, so it survives rotation and process death;
  - `BackHandler` closes it, the same pattern `FoldersScreen` uses.

  It is resolved against the kept batch. If a new generation lands while the page is open, the
  last-seen copy stays on screen (minus new dismissals) instead of the page vanishing. If it's
  restored into a batch without that key, it closes. Side effect: tapping the Discover tab again
  doesn't close the page (Back does). Moving it to a real `DiscoverPlaylistRoute` later is a
  one-line `entry<>` plus a route.
- **Keys carry the generation, so "key sets differ" is automatic.** The meaningful property is
  that the **theme** sets differ, and that is what the tests pin.
- **The history playlist is pinned** rather than rotated, as the headline the brief asks for. Its
  songs still rotate by generation.
- **"Deep cuts: {artist}"** uses the lower-scored half of that seed's chain picks (whatever Because
  you play didn't take). It is not the seed's own little-played library songs: per-song play
  counts beyond the top 300 aren't read.
- The same song may appear in more than one playlist (for example a Country find in both
  "Country finds" and "Front porch"). There is no cross-playlist de-duplication.

## 3. Discover seeded from the Takeout listening history

### What landed
- **`TakeoutSeedSource`** (`@Singleton`) reads `assets/discover_takeout_seed.json` lazily, once,
  into memory. It is parsed by `parseTakeoutSeed()` (kotlinx.serialization, `ignoreUnknownKeys`;
  only `artists` and `tracks` are read).
  - A missing, malformed or wrongly shaped file gives an empty seed. It never crashes and is never
    logged.
  - Nothing from it is written to disk or sent to the server, beyond the artist-name searches
    below.
- **Artist matching:** `matchTakeoutArtists` matches by `artistKey` (the codebase's `matchKey`:
  case, accents, punctuation and bracketed extras ignored).
  - Candidates come from a bounded `artistsWithKeys(searchKey(name))` lookup, chunked by 400.
  - Matched artists carry the library id and name, so they seed chains like any library artist.
  - Two spellings of one artist count once, with their plays added together.
- **Weighting:** a new `SeedSignals.history`, weighted `SeedWeights.HISTORY × ln(1 + plays)` with
  `HISTORY = 3.0` (in-app play totals are 4.0):
  | Artist | Takeout plays | Weight | ≈ in-app plays at the same weight |
  |---|---|---|---|
  | #1 (Bones) | 3,882 | 24.8 | ~490 |
  | #300 | 32 | 10.5 | ~13 |

  The #300 artist still sits well above every like/rating signal combined (max 5.0). In-app and
  Takeout plays for the same artist add up. The library id wins over the empty one.
- **Seed choice:**
  - The top 60 artists are weighed.
  - The 8 strongest **with library ids** walk the chains.
  - Up to **3 strongest history-only artists** (not in the library) get a chain through the
    server: `search3` by name, then exact-artist match (`sameArtistName`), then
    `similarToServerArtist` and `similarSongs`. Their picks carry `via = that artist`.

  So "Because you play {history artist}" appears only when the server actually returned songs for
  them. If the server doesn't know the artist, there's no playlist. The strongest artists'
  own songs are kept out of the finds, as in v1.
- **History songs:** `matchTakeoutTracks` finds library songs by `songKey` (artist + title via
  `matchKey`), falling back to `sameSong` (guests/versions) with the length ignored.
  - Candidates come from `tracksWithKeys(titleKeys(title))`, chunked by 400.
  - Results are ordered by Takeout play count, each library song once.
  - Fewer than 8 matches means no "From your listening history" playlist.
- **Labelling:** the history playlist's line is "The songs you've played most over the years, from
  your library". Because-you-play playlists for history-only seeds say "Artists like X, from your
  listening history". Under the updated line, "Seeded by your library and listening history" shows
  **only** when the history playlist exists. No mention of Google, Takeout or file names in the UI.
- **Tests:**
  - `TakeoutSeedTest`: name matching (`$UICIDEBOY$` ↔ `$uicideboy$`), merged spellings, weighting
    order (top Takeout artist > weak in-app signal; the tail still registers; not above a heavily
    played in-app artist), play sums, track matching and order, the guest fallback, one-per-song,
    and the ≥ 8 rule.
  - `TakeoutSeedParseTest`: fixture parsing and ordering, malformed/empty/wrong-shape input giving
    an empty seed, and the **bundled file** parsing to 300 artists / 500 tracks with no links in it.

## Risks for CI (sections 2 and 3)
1. **First compile of this code.** The pure files (`DiscoverModel`, `DiscoverEngine`,
   `DiscoverPlaylists`, the matching half of `DiscoverTakeout`) and `PlaylistsTest`,
   `TakeoutSeedTest` and the existing Discover engine tests were compiled and run outside Gradle,
   with `kotlin-compiler-embeddable` 2.0.20 and small stubs for `TrackEntity`, `matchKey` and
   `sameSong`. Result: 48/48 passing (it caught one real bug, now fixed).

   **Not compiled locally:** `DiscoverRepository`, `DiscoverTables`, `DiscoverModule`, the screen
   and view model, `TakeoutSeedSource`, the parse half (needs the serialization plugin), and the
   Robolectric migration test. Those were checked by reading only.
2. **Real `matchKey` / `sameSong` vs the stubs.**
   `TakeoutSeedTest.aHistorySongCreditingAGuestStillFindsTheLibrarySong` relies on `sameSong`
   treating "Artist feat. Guest" as "Artist" (its documented behaviour). If CI disagrees, that one
   assertion is the place to look.
3. **Room:**
   - `DISCOVER_DB_VERSION` is now 2, with `@ColumnInfo(defaultValue = "0")` on
     `DiscoverBatchEntity.generation` to match the migration's `DEFAULT 0`.
   - `DiscoverMigrationTest` hand-writes the v1 schema, because v1's exported JSON was never
     committed. If Room's validation reports a mismatch there, compare it against the generated
     `app/schemas/app.winters.octo.discover.DiscoverDatabase/2.json`.
   - CI will write `1.json`/`2.json` schema exports. Commit `2.json` from the CI build.
4. **Whole-table gate:** a local simulation of `WholeTableGateTest`'s regexes over `src/main/java`
   found no new callers. The new bounded read is named `artistSongsLimited` so it doesn't match the
   per-list `artistTracks(` rule, and its comment avoids the literal call text.
5. **Compose details:**
   - `DiscoverScreen` returns early when the playlist page is open; the effects and
     `remember`s are all above the return.
   - The Refresh button lives in `PageHeader`'s `trailing` slot.

## Known gaps
- The playlist page is in-screen, not a nav route (see Deviations). A tab re-tap doesn't close it.
- No per-playlist "Not interested in this playlist", and no saving a playlist to the library or
  server.
- First-batch cost rises a little: about 6 extra server calls for history-only chains, plus 2
  more seed similar-song asks and 2 more chain artists (around 50–60 calls, still 4 in flight).
  Worth timing on the phone.
- Matching depends on the library's spelling. A Takeout artist spelled very differently from the
  library ("Suicideboys" vs `$UICIDEBOY$`) won't match and is treated as history-only.
- The history playlist needs ≥ 8 of the 500 Takeout tracks to be in the library. On a small or
  mismatched library it simply doesn't appear.
- Dismissals are still not included in backup/restore (unchanged from v1).

---

# Sections 1, 4, 5 and 6: player redesign, top bar, bubble, swipe

*Same branch, 2026-11-20, a separate session from sections 2 and 3 (which are untouched). No
Gradle was run and nothing was pushed. The pure logic and its tests were compiled and run
outside Gradle (details under "Risks for CI"); the Compose code was checked by reading only.*

## The theme gate used

- **`theme/ThemeLayout.kt`** (new, pure): `playerLayoutFor(theme, upright, live)` gives
  `PlayerLayout.Upright | Circle`. `shellLayoutFor(theme)` gives a `ShellLayout(dock,
  floatingNowPlaying, swipePages)`:
  - `ClassicShell` (bottom bar, round button in the bar, tap-only tabs) for the five themes;
  - `MusicSpaceShell` (top bar, floating bubble, swipeable pages) for Music Space.
- Composables read the active theme from **`LocalActiveTheme`** (provided by `ThemeRoot`,
  beside `LocalOctoFonts` / `LocalOctoGlass`) and ask these functions. That's the same kind of
  seam as `fontsFor` / `glassFor`.
- `PlayerOverlay` is wrapped in `OverArt`, which swaps surfaces but not `LocalActiveTheme`, so
  the player sees the real theme.
- **Every change below is behind this gate.** Under the five other themes, `screenPadding`,
  `BackButton`, `Feedback`, the bar, the pill and the player all take the old values and code
  paths: `LocalTopBarRoom` is 0 dp, the bottom room is still 120 dp, the single `NavDisplay`
  keeps its tab motion, and `BottomBar` keeps its defaults.
- Tests: `ThemeLayoutTest` (all six themes × upright/sideways × song/station).

## 1. Circle player (Music Space, upright, songs)

### What landed
- **`player/CirclePlayer.kt`** (new). Used in `PlayerContent`'s upright branch when the gate
  says Circle; otherwise `UprightPlayer` runs unchanged. Top to bottom:
  - **Top line:** close chevron (`Collapse` icon, calls `onClose`), the album name (centred,
    taps open the album, carries the screen reader's Close player action as before), and
    **More**, which opens the song menu (Cast, Sound, share, and so on).
  - **Title block:** title and artist centred. The artist opens the artist page. The rating
    line keeps its fixed height. A long title still scrolls.
  - **Circle:** the artwork is about 74 % of the screen width
    (`CircleSize.fit(screenWidthDp, heightDp)`), clipped to a circle. Missing art shows the
    tertiary circle with a music-note glyph, so there's never a hole. Square animated loops
    still play, clipped to the circle. Paused, the art settles back to 0.94.
  - **Ring:** a faint full track plus a cream (`ForestGlass.Text`) progress arc with a dot at
    its end. The ring is open at the bottom (60° gap):
    - elapsed time sits at the bottom-left end, total length at the bottom-right;
    - the quality badge and speed mark sit between the ends, under the art (speed is still
      tappable and opens the speed card).
  - **Control row:** shuffle (on/off glow) · previous · **big cream round play/pause with dark
    ink** · next · heart. For a song not in the library, the heart becomes the add button,
    exactly as beside the upright title.
  - **Secondary row:** lyrics · up next · sleep timer (with its minutes badge) · repeat.
  - **Volume line** last, when the screen is tall enough.
- **Ring seek:** a pointer handler on the circle's box runs in `PointerEventPass.Initial`. It
  claims a touch **only** when the touch lands in the ring's band: up to 10 dp inside the
  line, 24 dp outside it. Then:
  - tapping seeks there;
  - dragging moves round the ring and seeks on release (like `LineSlider`'s non-live mode);
  - a haptic tick marks the grab.

  Touches on the artwork aren't claimed, so the art keeps its sideways swipe-to-skip. Because
  the ring consumes its moves, the player's pull-down-to-close cancels while seeking. Seeking
  goes through the same plumbing as `Progress()`: `rememberPositionMs`, `fractionAt`, then
  `position.longValue = target; model.seekTo(target)`.
- **Accessibility:** the ring has `progressBarRangeInfo` (updated once a second) and a
  `setProgress` action.
- **Background:** `CircleBackground(plainColors)`, a 3-stop vertical gradient from
  `ArtworkPalette`'s existing `PlayerColors` (mesh main, deep, base). It is drawn in the same
  `hazeSource` box as the immersive background, so the player's glass still frosts it.
  - Stops are desaturated to 70 % and darkened, then darkened further until relative
    luminance is ≤ 0.18, which keeps white at ≥ 4.6:1 on every stop.
  - With no artwork (or before it loads), it uses the Forest page gradient
    (`#1E2819 → #131A10 → #0A0E08`).
  - Ink is forced to white under the circle player, so the status and navigation bar icons go
    light.
- **Pure logic** in `player/CircleMath.kt`:
  - `Ring`: angle→fraction, gap snapping, the band test, the wrap guard when a drag crosses
    the gap, sweep;
  - `CircleSize.fit`;
  - `CircleBackdrop`: stops, luminance, contrast.
- **Tests:** `CircleMathTest` (14).
- **Reuse:** to share code, `PlayerOverlay.kt`'s private helpers became `internal`. No
  behaviour changed. The helpers: `LikeButton`, `ActionButton`, `SleepCircle`,
  `TransportButton`, `Volume`, `QualityBadge`, `SpeedMark`, `settleSwipe`, `risesIn`,
  `swallowTouches`, `LocalPlayerLoop` / `PlayerLoopState`, `TitleStyle`, `TopLineHeight`,
  `PlayerSideMargin`, `ArtistLineAlpha`.

### Everything still reachable
Every feature from the upright player is still reachable in the circle player:

| Upright player | Circle player |
|---|---|
| Line seek | Ring seek. Lyrics tap-to-seek and notification seek are untouched. |
| Times | Ring ends |
| Quality badge, speed mark | Under the ring |
| Heart / add, song menu | Heart in the control row, menu at top right |
| Transport | Control row |
| Volume | Last line; dropped only on short screens, as the upright rhythm already does |
| Lyrics, queue, sleep, shuffle, repeat | Split across the two rows |
| Rating | Under the artist |
| Album line | Top line |
| Art swipe-to-skip, pull-down close, Back, a11y Close player | Kept |
| Lyrics mode | Same fold: the blocks fade and swallow touches, and the art stays laid out |

`LyricsLayer`, the queue sheet, the lyrics chooser and the sleep/speed card are the same
composables, opened by the same callbacks.

### Deviations
- **Landscape keeps the existing side-by-side player.** So do **live stations**: they have no
  length to ring round and they have their own live bar and buttons.
- **Top-line name = album name, not queue name.** The playback layer exposes no queue or
  context name, and adding one would mean new playback plumbing, which the brief rules out.
- **No tall-loop backdrop under the circle player.** The gradient replaces it. Square loops
  still play inside the circle.
- **No "−remaining" time.** The right-hand time is the total length, as in the reference.

## 4. Tab bar at the top (Music Space)

- `BottomBar(dock = BarDock.Top, nowPlaying = false)` docks it under the status bar:
  `statusBarsPadding` plus `BarTopGap` 8 dp. The bar is 56 dp, with the same 20 dp sides.
  - It keeps the same tabs, icons, order and destinations, the same gliding pill and the same
    marks.
  - The tab capsule now takes the whole width: the column math uses `maxWidth` instead of
    `maxWidth − 56 − 10`.
- **Surface:** `BarGlass(topDocked = true)`. Same Music Space surface, but the shadow falls
  **down** (+8 instead of −8) and the hairline runs along the **bottom** edge. Pure helpers
  `barShadowY` / `barHairlineY` are pinned in `ThemeGlassTest`. Bottom-docked calls are
  unchanged.
- **Content padding:** `ui/common/Layout.kt` gains `LocalTopBarRoom`, which `MainShell`
  provides as 64 dp (8 dp gap + 56 dp bar) under Music Space and 0 otherwise.
  - Read by `screenPadding()` (top), `BackButton`, and the pages that pad their own top:
    Search, History, Favourites, Downloads, Library page, Library health ×2, Recently removed.
  - With the bar at the top, `screenPadding`'s bottom room drops from 120 dp to 88 dp
    (`screenBottomRoom`), just enough for the last row to scroll clear of the bubble's first
    place. Search's bottom room follows the same rule.
  - Home's "Home / Edit Home" header starts 8 dp below the bar.
- **Snackbar** (`Feedback`) drops the bar's height from its bottom offset.
- **Downloads pill** moves to bottom-left above the navigation bar, clear of the bubble's
  default spot.
- The now-playing capsule leaves the bar entirely, and the bar's small-player mode isn't used
  (`playerShown` is forced false).

## 5. Floating now-playing bubble (Music Space)

- **`ui/nav/NowPlayingBubble.kt`** draws `NowPlayingFace` (the bar's own face: art, progress
  ring, paused breathing, cast mark, play sign with nothing loaded) on the Music Space bar
  surface (`BarGlass`, circle, shadow downward).
  - A song with no art gets the note glyph.
  - It sits in a full-screen, touch-transparent layer inside system bars plus cutout (not the
    keyboard, so typing doesn't move it).
  - It is shown with the exact `barShown(playerOpen, stack.lastOrNull())` rule. So it hides
    under the full player and over sign-in pages, exactly when the old capsule hid.
  - **It is not hidden when nothing is loaded:** the old round button wasn't either. With
    nothing loaded it shows the play sign and a tap shuffles the library, so that feature
    stays reachable.
- **Gestures (shipped: drag + long-press adjust mode; pinch was not shipped):**
  - **Tap:** opens the full player (the circle one), or shuffles the library with nothing
    loaded.
  - **Drag:** moves it. It is clamped on screen with a 12 dp margin and stays where dropped.
  - **Long-press:** haptic, then an *adjust row* appears beside the bubble: **− (smaller)**,
    **play/pause**, **+ (bigger)**, with a sage ring on the bubble. Steps are 12 dp within
    48–128 dp (default 60). A resize keeps the centre where it was, clamped.
  - **Closing the adjust row:** a tap anywhere else closes it. That tap is swallowed, so it
    never lands on the page. The row also closes after 5 quiet seconds or on a tap of the
    bubble.
  - **Why not pinch:** on a 48–64 dp bubble the second finger lands outside it, and its pointer
    goes to the page under it, not to the bubble. So pinch can't work reliably at these sizes.
- **Tap vs drag disambiguation:** the tap detector sits outside the drag detector in the
  modifier chain. The drag sees each move first and consumes it once past touch slop, and
  `detectTapGestures` cancels on consumed moves, so a drag never opens the player or toggles
  playback. Holding still for the long-press timeout and then dragging moves the bubble after
  the adjust row opens, which is harmless.
- **Accessibility:** a click action (Open player / Shuffle your library) and custom actions:
  Play/Pause, Next, Previous, Make the bubble bigger, Make the bubble smaller.
- **Play/pause:** the old round button had no direct play/pause (its tap grew the bar's small
  player, which had one). In bubble form, play/pause lives in the adjust row and in the a11y
  actions, and the full player is one tap away.
- **Persistence:** `BubbleStore` is its own small `preferencesDataStore("now_playing_bubble")`,
  the `PlaylistsPagePrefs` pattern, not `discover.db` and not `PlayerPrefs` (which is in
  backups).
  - It stores x/y as fractions of the travel room, plus the size in dp. All key names live in
    one place: `BubbleKeys`.
  - The place is written on drop or resize only, never per frame.
  - `BubbleViewModel.place` starts null, and the bubble isn't drawn until the kept place is
    read, so there's no first-frame jump.
  - First run: x = y = 1, the bottom-right corner, where the round button sits.
- **Pure logic:** `ui/nav/BubbleMath.kt` (size clamp and steps, offset ↔ fraction, clamp,
  no-room case, centre-keeping resize, `BubblePlace.sane()`). Tests: `BubbleMathTest` (9).
- **Touches elsewhere:** the bubble's layer has no pointer input except on the bubble itself.
  Compose doesn't share pointer input between overlapping siblings by default, so touches on
  the bubble stay with the bubble, and touches anywhere else reach the pages (scroll, swipe,
  and the top bar's taps). If it's dragged over the bar it can always be dragged away.

## 6. Swipe between the five pages (Music Space)

- **Approach shipped:** a `HorizontalPager` whose page *n* hosts **tab n's own
  `NavDisplay`**. Each `NavDisplay` takes the tab's existing `rememberDecoratedNavEntries`
  output: the per-tab back stack, the saveable-state decorator and the view-model decorator,
  all still created once per tab at the top of `MainShell`, exactly as v1 does.
  - So a tab's stack, scroll position and view models survive switching by tap or by swipe,
    the same way they already survived tab taps (an entry leaving composition saves into its
    holder).
  - Back within a page pops that page's own stack. Other themes keep the single `NavDisplay`
    with the tab motion.
- **Sync** (`ui/nav/PagerSync.kt`, pure; `PagerSyncTest`, 6). `selected` stays the one source
  of truth:
  - A tab chosen any other way (bar tap, Back-to-Home, family link, import, search ask) sets
    `selected`. A `collectLatest` on `selected` then animates the pager there
    (`animateScrollToPage`; an instant `scrollToPage` with motion reduced). While that glide
    runs, the pill goes straight to the tapped tab.
  - While a **finger** is turning pages (`isScrollInProgress && !gliding`), the pill follows
    `currentPage`, and the page the swipe comes to rest on (`settledPage`) becomes `selected`.
  - A finger that catches a glide midway takes over, and its resting page decides.
  - When the shell is first entered (Music Space chosen), the pager jumps to `selected`
    without a glide.
- **What's composed:**
  - The selected tab's page always draws.
  - While a finger turns, so do the page under the finger and its neighbours.
  - Pages a tap-glide only passes over draw an empty ground instead of building their tabs,
    which keeps a glide across the bar smooth.
  - `beyondViewportPageCount = 0`.
- **Nested scroll:** I relied on the pager's standard nested-scroll behaviour. A horizontal
  drag that starts on a `LazyRow` shelf scrolls the shelf, and the pager only gets what the
  shelf doesn't consume; vertical lists are untouched.
- **Not pages:** `userScrollEnabled = !playerOpen`. The player overlay and sheets sit above the
  pager in z-order and take their own touches.

## Risks for CI (sections 1, 4, 5, 6)

1. **Compiled and run here (outside Gradle, `kotlin-compiler-embeddable` 2.0.20 + JUnit 4):**

   | Test class | Tests | Result | Stubs used |
   |---|---|---|---|
   | `CircleMathTest` | 14 | pass | none |
   | `BubbleMathTest` | 9 | pass | none |
   | `PagerSyncTest` | 6 | pass | a `TabOrder` stub |
   | `ThemeLayoutTest` | 4 | pass | `Themes.kt` without its serialization annotation |

   **Not compiled locally:** every Compose file (`CirclePlayer`, `NowPlayingBubble`,
   `MainShell`, `BottomBar`, `BarGlass`, `Layout` and the page edits), `BubbleStore`, and the
   `ThemeGlassTest` addition.
2. **API names most worth checking at compile time:**
   - `HorizontalPager(beyondViewportPageCount =, key =)` and `rememberPagerState(initialPage) { count }`;
   - `awaitFirstDown(requireUnconsumed, pass)`;
   - `semantics { setProgress {…}; progressBarRangeInfo }`;
   - `detectDragGestures(onDragStart, onDragEnd, onDragCancel) { change, amount -> }`;
   - `CustomAccessibilityAction(label) { … }`;
   - the nested `pointerInput` / `awaitEachGesture` loops.
3. **`BarShownTest`** now expects **3** sites of `visible = barShown(playerOpen,
   stack.lastOrNull()),` (bar, downloads pill, bubble). If CI fails there, that string count
   is the cause. The other `MainShell` source scans (`BackOneTest`, `JoinOpeningsTest`) still
   match: no `removeLast`, and the join lines are untouched.
4. **Pager + NavDisplay + shared elements:** several `NavDisplay`s can be composed at once
   during a swipe. Each registers its own back handler, enabled only when its stack is deeper
   than 1. A Back press *during* a drag could reach the neighbour's handler if that tab has a
   deeper stack. At rest only one is composed.
5. **`LocalActiveTheme` in `MainShell`/`PlayerOverlay`** is a `staticCompositionLocalOf`.
   Switching theme recomposes the whole shell, which already happens because the palette
   changes.

## Known gaps

- Nothing has been seen on a phone. Worth checking first:
  - the ring band vs the art swipe;
  - the bubble's tap-vs-drag feel;
  - shelf-vs-pager handoff on Home's `LazyRow`s;
  - the top bar over the Home header.
- Lists that hard-code a 140 dp bottom room (Favourites, Library lists, History, Downloads,
  Health, Recently removed, Search results) keep it under Music Space. It is end-of-list
  scroll room, not a visible gap, but it is more than the 88 dp the shared padding now uses.
- Top line shows the album, not a queue or context name (no such data in playback).
- The bubble's adjust row uses the Add icon for "+" and a drawn bar for "−" (there is no
  plus/minus pair in `OctoIcons`).
- The full player no longer has a shared-element flight from the bar under Music Space: the
  bubble has no `sharedElement` counterpart, so the player fades in. Under other themes the
  flight from the small player is unchanged.
- Desktop and iOS are untouched.
