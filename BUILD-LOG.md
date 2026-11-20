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

## APK deliveries

- Stock baseline: `~/workspace/your_files/octo-player-debug/app-debug.apk` (from run 44644606689).

## Blockers / where work stopped

- (none yet)
