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
