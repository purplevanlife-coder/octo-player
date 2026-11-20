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
