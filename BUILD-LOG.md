# Octo fork — build log

Fork of `winters27/octo-player` → `purplevanlife-coder/octo-player`.
Coordinator: Muse (supervising Claude Code as primary coder, Codex as second engineer).
Started: 2026-11-19 (America/Los_Angeles).

## Decisions applied (made by Collin — not open questions)

1. **Discover tab position:** second tab. Order: Home · Discover · Search · Library · Settings.
2. **Reskin = a NEW "Music Space" theme** alongside Octo's existing themes and accent picker.
   Never a replacement. Every feature in ASSESSMENT.md section (b) must survive.
3. **Order:** Phase 1 Discover first, then Phase 2 UI reskin.
4. **Navigation must feel nice and smooth:** fluid transitions, no jank, no blank/reload
   flashes when moving between tabs and screens.
5. **Cache everything:** persistent on-disk cache, render from cache first, background
   refresh. (The in-memory-only artist-info cache flagged in the assessment gets fixed
   as part of Phase 1.)

## Discover spec (from music-space-ui-reference/MUSIC_SPACE_REFERENCE.md §6)

- Genre lanes: For You, Country, Folk, Bluegrass, Blues, Indie, Rock, Phonk, Hip-Hop,
  Electronic, Soul, Jazz, Oldies, Latin, Reggae.
- Seeds: artists from Collin's own library; walk "similar artists" chains
  (getArtistInfo2) outward to artists he doesn't already have.
- Weighting: by in-app play stats — what he actually plays steers Discover.
- Batches: each lane's picks are generated in the background and persisted; the tab
  shows the latest batch. Dismissed picks never appear again (dismissal memory).
- Data plumbing already in Octo: getSimilarSongs2 (Discovery.radio), getTopSongs +
  getArtistInfo2 (ArtistExtrasSource), Discovery.stations()/madeForYou()/octoLists(),
  charts (ChartLists), listening signals (PlayShelves/PlayTotals/History, likes, ratings).

## Constraints

- NO local Gradle builds (daemon IPC fails on this VM). CI only: `build-debug` workflow.
- Keep changes small and isolated so upstream merges stay feasible.
- Debug APK package: `app.winters.octo.debug` (installs beside release Octo).

## Progress

### Prep (2026-11-19)
- Branch `feature/discover-v1` created from main.
- CI: unit-test step added to `build-debug.yml` (`:shared:core:allTests :app:testDebugUnitTest`,
  mirroring upstream `android-release.yml`) — assessment flag (e)(3).
- Updater: `OCTO_REPOSITORY` in shared/core `PlayerUpdater.kt` redirected from
  `winters27/octo` to `purplevanlife-coder/octo-player`, so the fork never offers
  upstream player updates — assessment flag (e)(5). The fork publishes no releases
  in the updater's manifest format, so checks find nothing until/unless the fork
  publishes its own.

## CI runs

| Run id | Branch | Commit | Result | Notes |
|---|---|---|---|---|
| 44644606689 | main | (stock) | success | Pre-fork-work baseline, 2026-11-19; APK verified, package app.winters.octo.debug |
| 44668189551 | feature/discover-v1 | prep (51cedd31 remote) | **failure** | New unit-test step caught a real break: all 14 failures were `PlayerUpdaterTest` — its pretend GitHub only served `/repos/winters27/octo/releases`, but the updater now reads the fork repo. 1,403 other tests passed. Fixed in commit 09aadcb4 (test serves the fork path). |
| 44670710951 | feature/discover-v1 | Discover v1 (04b76a8f remote) | **cancelled** | Everything compiled (KSP + app + unit-test sources, zero errors) and shared-core tests passed (incl. the fixed PlayerUpdaterTest — no FAILED lines, build advanced). `:app:testDebugUnitTest` then ran 15 min without finishing; coordinator cancelled it to bisect rather than wait blind. Workflow split into per-suite steps with a 40-min cap on app tests and always-uploaded test reports (commit 201fe86f) so any stall is pinpointed by which test classes completed. |
| 44673134804 | feature/discover-v1 | + CI split (b8ee95f8 remote) | **failure** | Shared-core tests **passed** (5m). App tests: 725 classes / 3,531 tests completed with exactly **one failure** — upstream's `WholeTableGateTest` caught `DiscoverRepository` calling the whole-table `users.likedTracks()` (the gate forbids new whole-table reads; every other signal read was already bounded). All 9 new Discover test classes passed. The step then hit its 40-min cap a whisker from the end (last class finished 04:58:29, cap 05:00:15) — the suite is simply big. Fixes: bounded `UserDao.likedTracksLimited(limit)` (same SQL + LIMIT, newest-first) used by the repository; app-test cap raised to 60 min. |
| 44678219131 | feature/discover-v1 | + gate fix (18d9662e remote) | **failure** | Shared tests passed; app suite ran to completion inside the 60-min cap: 726 classes / 3,531 tests, still exactly one failure — the gate moved to `UserDao.kt`: it scans raw text, and the explanatory comment on the new DAO method contained the literal whole-table call text, tripping the "streams now" rule for that file. Fixed by rewording the comment; a local simulation of the gate (its own regexes/sets run over the source tree) then reported zero callers and zero violations. |
| 44683285029 | feature/discover-v1 | + comment fix (609d9a1a remote) | **SUCCESS** | **Phase 1 green.** Shared-core tests passed; app suite: 726 classes, **3,531 tests, 0 failures, 0 errors** (18 skipped); debug APK built and uploaded. APK verified as the fork build: package `app.winters.octo.debug`, versionName `0.2.0-debug`, and the dex contains the Discover classes (`DiscoverScreen`, 81 references to `app/winters/octo/discover` in classes18.dex). Delivered to `~/workspace/your_files/octo-player-debug/app-debug.apk` (97,926,559 bytes), replacing the stock baseline. **Not yet device-tested — only Collin's phone test proves it works.** |
| 44690338173 | feature/discover-v1 | Phase 2 foundation, intermediate log commit (0acaf020 remote) | **cancelled** | Dispatched on the first Phase 2 push, then cancelled ~1 min in when the final log commit superseded it. No steps failed; shared-core tests were still running. |
| 44690480834 | feature/discover-v1 | Phase 2 foundation (d9dc52cd remote) | **SUCCESS** | **Phase 2 foundation green.** Remote tree 387cc962 is byte-identical to local HEAD 0365fdf9's tree (mirror push verified). All steps passed (06:49→07:48 UTC): shared-core tests, app unit tests, APK build. App suite: 727 classes, **3,534 tests, 0 failures, 0 errors** (18 skipped) — Phase 1's 3,531 plus the new theme tests. The hand-computed contrast margins held: `MusicSpaceThemeTest` 8/8, `ThemeFontsTest` 2/2, `ThemePickerTest` 3/3, `ThemeApplierTest` 4/4, `ThemesTest` 5/5, `ResolvedFixtureTest` 2/2, all with 0 failures. APK verified: package `app.winters.octo.debug`, versionName `0.2.0-debug`, valid Android Debug signature (CI runner key), dex contains `MusicSpace` (15 refs), `ForestGlass`, and Phase 1's `DiscoverScreen`. Delivered as a NEW file `~/workspace/your_files/octo-player-debug/app-music-space-theme-debug.apk` (97,927,795 bytes); the Discover v1 APK is untouched (md5 57666872e96dd84f421b30f927d3f540). **Not yet device-tested — only Collin's phone test proves it works.** |
| 44802066138 | feature/discover-v1 | Phase 3 (a5f6235e remote) | **failure** | Compile error only: duplicate `TimesRoom` (CirclePlayer vs LivePlayerParts). Shared-core tests passed. Fixed by rename to `RingTimesRoom`. |
| 44803775878 | feature/discover-v1 | + TimesRoom fix (ba0dbc4a remote) | **failure** | Runner infrastructure: Compose Gradle plugin artifact could not be resolved from the plugin repository; failed in 18 s before compiling. No code change; re-dispatched unchanged. |
| 44803983093 | feature/discover-v1 | (same tree) | **SUCCESS** | **Phase 3 green.** Shared-core + app suites: **6,456 tests, 0 failures, 0 errors** (18 skipped). APK delivered as `app-music-space-player-debug.apk` (98,687,705 bytes); see the Phase 3 section. |
| 44818647041 | feature/discover-v1 | Phase 4 (8b987332 remote) | **failure** | Compile error only: `changedToUpOrCanceled` unresolved — removed in this Compose version. Fixed by reading the release from the change's `pressed` state (0270018b remote). |
| 44820558060 | feature/discover-v1 | + pressed-state fix (0270018b remote) | **failure** | Compiled; app suite 3,617 tests with exactly 2 failures, both upstream concurrency tests untouched by the diff: `TotalsReadHoldTest`, `LeaveStopsBuildsTest`. Judged flakes; re-dispatched unchanged. |
| 44829046266 | feature/discover-v1 | (same tree) | **failure** | Same pattern, different test: 1 failure, `MixThreadingTest` (threading). Re-dispatched unchanged. |
| 44836232252 | feature/discover-v1 | (same tree) | **failure** | Same pattern again: 1 failure, `LikedHiddenRaceTest` (the same race test that flaked in the Phase 2 components round). Re-dispatched unchanged. |
| 44842767691 | feature/discover-v1 | (same tree) | **SUCCESS** | **Phase 4 green.** Shared-core + app suites: **6,469 tests, 0 failures, 0 errors** (18 skipped). APK delivered as `app-music-space-player2-debug.apk` (98,736,597 bytes); see the Phase 4 section. |

### Phase 2 foundation — Music Space theme (2026-11-20, Claude Code session, ~16 min)

Per Collin's overnight instruction (2026-11-19 ~22:35): with Discover green and the APK
made, go straight into the UI phase. Five commits (1672a2fa…587812e6), +510/−7, 17 files.
Full write-up: `PHASE2-FOUNDATION-NOTES.md`.

- **Music Space is a sixth theme** (`ThemeChoice.MusicSpace`, dark), appended after Octo,
  Moss, Dusk, Daylight, Paper; selectable in Settings → Appearance, persists by enum name,
  fills the night slot under Match. The existing five are unchanged (ordinals, names,
  colours; the colour-snapshot fixture still pins only the original five).
- **Palette from theme.css "Forest Glass"**, single source `ForestGlass.kt`. Adaptations
  the token system forced: surfaces must be solid, so the glass colours are composited
  (card #23291E, raised #2F3529); the theme accent is the light sage #AEBFA5 because the
  darker sage #7D9168 fails the 4.5:1 contrast rule on card/raised surfaces (kept for
  filled pills in the components round); amber → complement (stars/hearts), warm tan →
  third colour, hairline border exact, sheets use the near-opaque player-bar olive.
- **Type tokens** (`OctoFonts`/`LocalOctoFonts`) added; existing themes name no face, so
  their text is unchanged. No component reads the tokens yet — applying them is the next
  round. Font files are NOT bundled (download needed approval in the coder sandbox):
  Music Space currently uses the system serif + bundled Inter Display; the notes give the
  exact drop-in file names for Playfair Display / DM Sans. Caveat skipped — the app has
  no handwritten-text slot.
- **Picker fix:** at 2× text size "Music Space" can't fit its card whole; names that
  overflow now step their size down (floor 0.6×), names that fit are untouched, with a
  test pinning the existing five at full size.
- **Not expressible per-theme today** (recorded in ForestGlass.kt + notes): translucent
  surfaces, soft colour variants, the 18dp radius, the blur/glass recipe, the type scale,
  and the danger red #F0927F — the error colour is shared by all Octo apps, so changing
  it needs a deliberate decision, not a theme tweak.
- New tests: MusicSpaceThemeTest, ThemeFontsTest; updated ThemesTest, ThemeApplierTest,
  ThemePickerTest, ResolvedFixtureTest. Contrast margins hand-computed; three are close
  to their thresholds — CI is the first real check.
- **CI result (2026-11-20, coordinator follow-up):** the foundation commits were
  mirror-pushed (remote tip d9dc52cd, tree identical to local) and run 44690480834 came
  back **SUCCESS** — the flagged contrast margins all held, no theme-value fixes were
  needed. APK delivered as `app-music-space-theme-debug.apk` (see CI runs table and APK
  deliveries). Still outstanding from `PHASE2-FOUNDATION-NOTES.md`: the components round
  (apply `LocalOctoFonts` in components, per-theme glass recipe, player-bar surface,
  per-theme corner radius, sage fill-accent role) and bundling the real Playfair Display /
  DM Sans font files. The theme is selectable and persists, but most screens do not read
  the new type tokens yet — the visible change in this build is the palette, launch
  screens and theme picker.

### Phase 2 components — fonts, type tokens, glass, player bar (2026-11-20, Claude Code session)

Per `PHASE2-COMPONENTS-BRIEF.md`. Four code commits (4db58259…ac110939) plus the notes
commit. Full write-up: `PHASE2-COMPONENTS-NOTES.md`. No Gradle run, nothing pushed.

- **Real faces:** Playfair Display (500/600/700) and DM Sans (400/500/600/700) are now
  Music Space's display/body families (static TTFs in `design/res/font`, OFL licences in
  `design/licenses`, credited in the README). Stand-ins removed.
- **Type tokens applied:** `displayType()` / `bodyType()` wrap `OctoType` styles and change
  only the face. Main reach is through shared components (PageHeader, SectionTitle,
  SongTitle, Items.kt rows and cards), plus album/artist hero names, Home, Library,
  Discover, Search, queue, downloads, and the mini/full player title + artist. The other
  five themes get the identical style object back (pinned by tests).
- **Per-theme glass:** `LocalOctoGlass` / `OctoGlass` (like `LocalOctoFonts`). Music Space
  feeds the `.glass` recipe (film, 1dp cream edge, frost 16, saturation 1.25, shadow
  28/8) into Glaze/FloatingGlaze defaults, glassPanel/raisedPanel and GlassSheet. The five
  others keep the old global constants (every changed path falls back verbatim). Over art
  (the player) all themes use Octo's glass.
- **Player bar:** under Music Space the bar's tab and now-playing capsules sit on the
  near-opaque `.player-bar` olive with a top hairline and an upward shadow, and **no
  backdrop blur**. Other themes: the same FloatingGlaze as before.
- **Skipped stretch:** per-theme 18 dp radius. There's no card/sheet radius token
  (about 96 hard-coded corner sites), so it isn't low-risk.
- **Tests:** ThemeFontsTest updated; new ThemeTypeTest, ThemeGlassTest. `resolved.txt`
  byte-identical; palette untouched, so the foundation contrast margins are unchanged;
  new glass surfaces hand-checked at ≥ 8.8:1 for `--dim`.
- **CI result (2026-11-20, coordinator follow-up):** the components commits were
  mirror-pushed as one remote commit (78892605, tree identical to local a99d3e93).
  First run 44752631533 failed on exactly one test, `LikedHiddenRaceTest`
  (a gate-timing race test in Favourites; it passed in the foundation round's
  reports and this round's diff touches no favourites, paging or catalog code),
  judged a flake under a slow runner. Re-run 44763664261 came back **SUCCESS**:
  6,394 tests across the shared-core and app suites, 0 failures, 0 errors
  (foundation round: 6,386). APK delivered as `app-music-space-components-debug.apk`
  (see APK deliveries). Still to come after this round: the sage fill-accent role
  and the 18 dp per-theme radius (needs a radius token first).

### Phase 3 — circle player, Discover playlists + Refresh, Takeout seeds, top bar, floating bubble, page swipe (2026-11-20, two Claude Code sessions + coordinator)

Collin's requests (2026-11-20 ~10:34–10:41 PST), six items in one round. Brief:
`PHASE3-PLAYER-DISCOVER-BRIEF.md`; full write-up: `PHASE3-NOTES.md`.

1. **Full player redesign** (reference image `~/workspace/user/files/3787_4_2oq6.jpg`):
   Music Space only, phone upright, songs. Circular artwork (~74% of width) with
   the progress as a **ring that is also the seek control** (tap/drag on the ring;
   artwork touches still swipe to skip), artwork-palette gradient background
   darkened to ≥ 4.6:1 for white text (Forest page gradient when no art), album
   name top line (playback carries no queue/context name), centred title/artist,
   control row shuffle · prev · big cream play/pause · next · heart, secondary
   rows keep lyrics, queue, sleep, repeat, volume, speed and everything else
   reachable. Landscape and live stations keep the existing player. The bar →
   player shared-artwork flight is replaced by a fade under Music Space.
2. **Discover: 12+ curated playlists + Refresh.** A batch always carries the
   "From your listening history" playlist plus 11 rotating playlist sections
   (12+ total), each 12–30 songs with an honest explainer line, a playlist
   detail page (drawn inside Discover — a new nav route would have meant
   touching MainShell's route registration in the Discover session; Back closes
   it, re-tapping the Discover tab does not), and a prominent **Refresh**
   button. Manual refresh (button or pull) builds the **next generation**:
   rotating templates and in-playlist song rotation mean two generations in a
   row never show the same set; same inputs + generation = same batch
   (deterministic). The 6h staleness refresh keeps the generation. discover.db
   migrated 1→2 (generation on the batch row); `discover_dismissed` untouched,
   with a migration test proving dismissals survive.
3. **Takeout seeds.** Coordinator-built asset
   `app/src/main/assets/discover_takeout_seed.json` (41 KB) from the full
   Takeout parse: top 300 artists / top 500 tracks by play count, title /
   artist / plays ONLY (65,625 plays, 2021-05-18 → 2026-11-15; no URLs or
   ids). Loaded once into memory; a missing/broken asset means an empty seed,
   never a crash, nothing logged. Takeout artists join seed weighting at
   `3.0 × ln(1 + plays)` (top artist ≈ 490 in-app plays; #300 ≈ 13), matched
   to library artists/songs by normalised name; the top 3 unmatched Takeout
   artists also seed a server name-search chain. "From your listening history"
   lists matched songs by play count, only when ≥ 8 match; UI copy says
   "Seeded by your library and listening history" — no Google/file mentions.
4. **Control bar at the TOP** (Music Space): the tab capsule docks under the
   status bar, shadow falling down; pages reserve its height at the top via a
   new `LocalTopBarRoom` seam (0 under other themes) and keep only 88 dp at
   the bottom. The now-playing capsule leaves the bar (see 5).
5. **Free-floating now-playing bubble** (Music Space): tap opens the player,
   drag moves it (dragging never opens/toggles), position persisted as screen
   fractions + size in its own small prefs store, clamped on-screen. Resize
   ships as **long-press → smaller / play-pause / bigger row** — pinch was
   rejected in-session because on a 48–64 dp bubble the second finger lands
   outside it. Bounds 48–128 dp. With nothing loaded it still shows, with the
   same shuffle-the-library play the old round button had.
6. **Swipe between pages** (Music Space): a `HorizontalPager` whose pages each
   host that tab's existing NavHost, so back stacks, scroll and view models
   survive; the bar pill follows the swipe and bar taps animate the pager.
   Shelf drags scroll shelves (standard pager nested scroll); the bubble
   consumes only its own touches.

- **CI result:** three runs. 44802066138 failed at `:app:compileDebugKotlin` —
  `CirclePlayer.kt` declared a private `TimesRoom` that clashed with
  `LivePlayerParts`' internal one in the same package; fixed by renaming it
  `RingTimesRoom` (16 dp, its own value). 44803775878 failed before compiling:
  the runner could not resolve the Compose Gradle plugin from the plugin
  repository — infrastructure, not code; re-dispatched unchanged. **44803983093
  SUCCESS:** shared-core + app suites, **6,456 tests, 0 failures, 0 errors**
  (18 skipped) — the components round's 6,394 plus 62 new (PlaylistsTest,
  TakeoutSeedTest/ParseTest, DiscoverMigrationTest, CircleMathTest,
  BubbleMathTest, PagerSyncTest, updated DiscoverUi/BarShown/ThemeGlass).
  APK verified: package `app.winters.octo.debug`, versionName `0.2.0-debug`,
  signature valid, `assets/discover_takeout_seed.json` (41,076 bytes) inside,
  dex contains CirclePlayer, DiscoverPlaylists, TakeoutSeed and the bubble
  store. Delivered as `app-music-space-player-debug.apk` (see APK deliveries).
  **Not yet device-tested — only Collin's phone test proves it works.**
- Still outstanding after this round: sage fill-accent role, 18 dp per-theme
  radius, the `DiscoverDatabase/2.json` schema export to commit from the CI
  build, and on-phone checks of ring/artwork gestures, bubble tap-vs-drag,
  shelf-vs-page swipe feel, and the top bar over the Home header.

### Phase 4 — bubble/pill rework, Discover top-1000 + dedupe + carousel (2026-11-20, coordinator-written)

Collin device-tested Phase 3 and gave six items in one round. Brief:
`PHASE4-BRIEF.md`; full write-up: `PHASE4-NOTES.md`. Claude Code was at its
5-hour cap, so the coordinator wrote and reviewed the code directly; pure
logic was verified in local kotlinc harnesses (Discover 50/50, bubble 20/20)
before CI. Two local commits: 57ef0842 (the round) + 93e492a8 (compile fix).

1. **Bubble resize fixed by replacing the gesture architecture.** Phase 3's
   two stacked `pointerInput`s plus the adjust-mode scrim (consuming every
   down in `PointerEventPass.Initial`) made the − / + chips' `detectTapGestures`
   dead and let drift cancel the long-press. Phase 4: ONE `pointerInput`
   feeding a pure `BubbleGestureMachine` (Tap / LongPress / Drag classified
   from timestamped events, unit-tested), chips as plain `clickable`s, scrim
   removed. Bounds 48–128 dp, 12 dp steps, `BubbleStore` persistence
   unchanged.
2. **Playing pill:** loaded + playing → the bubble expands (spring-animated,
   disc anchored left) into a pill with Previous / Play-Pause / Next as
   draw-only regions of the same gesture handler — a button touch can only
   ever be that button's tap, never a drag or a player-open. Paused/empty →
   round bubble, empty state keeps shuffle-the-library play. Geometry in
   `BubbleMath` (60 dp disc → 195 dp pill).
3. **Visibility ring:** always-on 2 dp Music Space cream stroke following
   the shape (circle → stadium), sage while adjusting, over the existing
   shadow. A border, not a glow.
4. **Top-1000 seeds:** asset regenerated to version 2 (top 1000 artists /
   top 1000 tracks, 96,707 bytes, totals reconcile 65,625 / 21,407 /
   6,825); weighed window top 60 → 150 artists, history-only server chains
   top 3 → 5, track matching scans all 1000.
5. **20 playlists per batch with hard dedupe:** every candidate claims
   `theme:` / `artist:` / `genre:` keys (history claims its dominant artist;
   moods claim every constituent lane genre); selection takes only
   claim-disjoint candidates, pinned history first, plus a repair pass that
   trades non-pinned playlists to fill slots without ever violating
   disjointness. Next-generation guarantee holds (tested, generations 0–7).
6. **Carousel:** "Playlists for you" is a snapping `LazyRow` of 272 dp
   cards (existing `PlaylistTile`); detail page, Play/Shuffle,
   Not-interested and Refresh unchanged; cache-first rendering unchanged.

- **CI result:** five runs (see table). Run 1 failed to compile
  (`changedToUpOrCanceled` removed in this Compose version; fixed via the
  change's `pressed` state). Runs 2–4 each failed on 1–2 rotating upstream
  concurrency tests (`TotalsReadHoldTest`, `LeaveStopsBuildsTest`,
  `MixThreadingTest`, `LikedHiddenRaceTest`) — none in code this diff
  touches; the same flake pattern as the components round. **44842767691
  SUCCESS: 6,469 tests, 0 failures, 0 errors** (18 skipped) — Phase 3's
  6,456 plus 13 net new (BubbleGestureTest, PlaylistsTest dedupe/batch,
  TakeoutSeedParseTest 1000/1000). APK verified: package
  `app.winters.octo.debug`, versionName `0.2.0-debug`, aapt2 + apksigner
  verified, seed asset read back from inside the APK at 1000/1000, dex
  contains BubbleGestureMachine, NowPlayingBubble, DiscoverPlaylists,
  TakeoutSeed, CirclePlayer. Delivered as `app-music-space-player2-debug.apk`
  (see APK deliveries). No discover.db change — dismissals untouched.
  **Not yet device-tested — only Collin's phone test proves the pill/bubble
  gestures.**

### Phase 5 — bottom player bar, bubble retired (2026-11-21, Claude Code session)

Collin rejected the circle player as the everyday control (2026-11-20 ~18:00
PST: "I just want [the player] to be down at the bottom... skip pause
whatever") and approved Muse's docked-bar design. Brief: `PHASE5-BRIEF.md`;
full write-up: `PHASE5-NOTES.md`. Gradle not run (cannot work on this VM).

1. **ShellLayout:** `floatingNowPlaying` → `bottomPlayerBar`
   (`ClassicShell` false, `MusicSpaceShell` true). Other themes unchanged.
2. **New `BottomPlayerBar` (`ui/nav/PlayerBar.kt`):** PlayerCapsule widened
   into a 64 dp bar docked at the bottom under Music Space — 44 dp artwork
   (casting mark, no-art note), title/artist marquee, Previous /
   Play-Pause / Next (station controls mirror PlayerCapsule), whole-bar tap
   opens the full player, 2.5 dp sage progress line along the top edge read
   in the draw phase (500 ms steps redraw only the line), no line for a
   station or nothing loaded (`playerBarShowsProgress`). Empty state:
   "Shuffle your library" + Play, tap shuffles.
3. **Bubble retired:** NowPlayingBubble, BubbleGesture, BubbleMath,
   BubbleStore and their two test files deleted; MainShell's bubble view
   model/actions/block removed.
4. **Downloads pill** (top-docked shell) lifted above the player bar;
   classic placement untouched. Page bottom room unchanged (88 dp covers
   the bar's 78 dp).
5. **Full player unchanged** by decision: circle player under Music Space
   upright, all Phase 3/4 functionality intact.

- **Tests:** ThemeLayoutTest updated; new PlayerBarTest (3 tests);
  BubbleGestureTest/BubbleMathTest removed with their code.
- **CI result:** pending CI.
- **APK delivery:** pending CI.

## Notes

- **Push route:** plain `git push` has no credential path on this VM. Commits are
  mirrored to GitHub through the Git Data API (api.github.com) with a script that
  recreates each local commit on the remote (blobs → tree on parent tree → commit →
  ref). Remote SHAs therefore differ from local SHAs; trees match. Remote branch tip
  after prep: 51cedd31.
- **Crew change (2026-11-19, per Collin):** Codex was pulled off the Octo fork for
  another project (and its model sign-in failed there too), so Claude Code builds
  solo and the coordinator does diff review directly.

## APK deliveries

- Stock baseline: `~/workspace/your_files/octo-player-debug/app-debug.apk` (from run 44644606689).
- **Discover v1: same path, replaced 2026-11-20 after verification** — from run 44683285029
  (feature/discover-v1 @ 609d9a1a remote), 97,926,559 bytes, package app.winters.octo.debug.
  Note: CI debug builds are signed with the runner's debug key, which differs per run —
  installing over the stock baseline may require uninstalling the old debug build first
  (same lesson as the Solar app). A fixed debug keystore is a worthwhile follow-up.
- **Phase 2 foundation (Music Space theme): `~/workspace/your_files/octo-player-debug/app-music-space-theme-debug.apk`**
  — from run 44690480834 (feature/discover-v1 @ d9dc52cd remote), 97,927,795 bytes,
  package app.winters.octo.debug, versionName 0.2.0-debug. The Discover v1 APK above
  stays in place untouched as the fallback. Same per-run debug-key caveat: installing
  this over the Discover build will likely require uninstalling the old debug build
  first (which loses its local data — the theme build's Discover dismissals are not
  backed up, see Phase 1 notes).
- **Phase 2 components (fonts, glass, player bar): `~/workspace/your_files/octo-player-debug/app-music-space-components-debug.apk`**
  — from run 44763664261 (feature/discover-v1 @ 78892605 remote), 98,249,558 bytes,
  package app.winters.octo.debug, versionName 0.2.0-debug, signature verified and all
  seven new font files confirmed inside `res/font`. The theme APK above stays in place
  untouched. Same per-run debug-key caveat as the theme build.
- **Phase 3 (circle player, Discover playlists/Refresh, Takeout seeds, top bar,
  floating bubble, page swipe): `~/workspace/your_files/octo-player-debug/app-music-space-player-debug.apk`**
  — from run 44803983093 (feature/discover-v1 @ ba0dbc4a remote), 98,687,705 bytes,
  package app.winters.octo.debug, versionName 0.2.0-debug, aapt2 + apksigner
  verified, Takeout seed asset confirmed inside, dex contains the new player,
  Discover playlists, Takeout seed and bubble classes. Earlier APKs stay in
  place untouched. Same per-run debug-key caveat: installing over the
  components build will likely require uninstalling it first (which loses that
  build's local Discover dismissals). The fork still has NO fixed keystore —
  setting one up remains a worthwhile follow-up (Collin's call).
- **Phase 4 (bubble gesture machine + playing pill + ring, Discover
  top-1000 seeds, 20 deduped playlists, carousel):
  `~/workspace/your_files/octo-player-debug/app-music-space-player2-debug.apk`**
  — from run 44842767691 (feature/discover-v1 @ 0270018b remote), 98,736,597
  bytes, SHA-256 82ddd953e340d2ea17b2f02b0f099fa362b6a62aa03d343c401d8b4f15e8a040,
  package app.winters.octo.debug, versionName 0.2.0-debug, aapt2 + apksigner
  verified, seed asset v2 confirmed inside at 1000 artists / 1000 tracks.
  Earlier APKs stay in place untouched. Same per-run debug-key caveat:
  installing over the Phase 3 build will likely require uninstalling it
  first (which loses that build's local Discover dismissals). The fork
  still has NO fixed keystore.

### Phase 1 — Discover v1 implemented (2026-11-19, Claude Code session, ~25 min)

8 commits on top of prep (24bf47d5…0abcc83c). Full write-up: `DISCOVER-BUILD-NOTES.md`.

- **Tab:** Discover at index 1 via a new `TabOrder` object (HOME 0, DISCOVER 1, SEARCH 2,
  LIBRARY 3, SETTINGS 4); `searchTab` and all positional uses now read from it; a guard
  keeps a saved 4-tab index in range. Bottom-bar pill capped at the column width (~59dp
  on a 412dp Pixel) so 5 tabs fit; glide animation unchanged.
- **Engine:** pure-Kotlin `DiscoverEngine` — seed weighting (play totals > recent plays >
  favourite artists > liked / 4–5★ songs, log-scaled, top 8 seeds), similar-artist chains
  2 hops via getArtistInfo2 (non-library first, seeds/dismissed excluded), genre lanes by
  whole-word genre match + era-based Oldies, section building, dismissal filtering.
- **Sections:** For You → Artists to explore → 14 lanes (Country…Reggae) → Made for you →
  Stations → Charts; each only when it has content; one failing source drops only its section.
- **Cache:** separate Room DB `discover.db` (batch + sections per server+account, 6h
  freshness; empty batch never replaces a good one; kept batch is read at app start so the
  tab's first frame has content). Artist extras (bio/top songs/similar) now persisted too,
  3-day freshness, 60-day prune. Artwork prefetched through the shared Coil disk cache.
- **Dismissals:** "Not interested" in the song long-press menu (Discover contexts only) and
  on artist cards, with Undo; persisted per server+account and filtered from all batches.
- **Tests:** 9 new test files (seed weights, chains, lanes, sections, dismissals, tab order,
  menu item, UI state) in `:app:testDebugUnitTest`.
- Deliberately out of v1: AI-music filter (no Subsonic signal), per-lane pages, dismissal
  review list, Settings toggle for the tab, WorkManager refresh, backup/restore of
  Discover data (separate DB — dismissals are NOT backed up; open question for Collin).

## Blockers / where work stopped

- (none yet)
