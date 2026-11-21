# Phase 4 notes — bubble/pill rework + Discover top-1000, dedupe, carousel

Branch `feature/discover-v1`. Collin device-tested Phase 3 on 2026-11-20 and gave
six items, all in one build. Brief: `PHASE4-BRIEF.md`. Code written and reviewed
directly by the coordinator (Claude Code was at its 5-hour cap for most of the
round); pure logic was verified with local kotlinc harnesses before CI, since
Gradle cannot run on this VM.

**CI result: run 44842767691 SUCCESS — 6,469 tests, 0 failures, 0 errors
(18 skipped)**, up from Phase 3's 6,456. Delivered as
`app-music-space-player2-debug.apk` (see BUILD-LOG.md → APK deliveries).
**Built, not ready — only Collin's phone test proves the gestures.**

## 1. Bubble resize — why the old one failed, and the new mechanism

Phase 3 shipped two stacked `pointerInput`s on the bubble — `detectTapGestures`
(tap + `onLongPress` opening the adjust row) layered over `detectDragGestures` —
plus, while adjusting, a full-screen scrim consuming **every** pointer down in
`PointerEventPass.Initial`. Two failure modes, both confirmed by reading the
Phase 3 code:

- The scrim's Initial-pass consumption cancels the adjust chips'
  `detectTapGestures`, which require an unconsumed down — so the − / + chips in
  the adjust row were dead on-device. The row could open but its buttons could
  never fire.
- The split tap/drag detectors fight over the same touch: any drift past touch
  slop before the long-press timeout turns the hold into a drag and cancels
  the long-press, so even opening the row was unreliable with a real finger.

Phase 4 replaces the whole stack with **one** `pointerInput` feeding a pure
Kotlin gesture machine (`ui/nav/BubbleGesture.kt`):

- `BubbleGestureMachine(longPressMs, slopPx)` consumes timestamped
  down/move/up events and emits `Tap`, `LongPress`, `DragStart/Drag/DragEnd`.
  A quick, still release is a Tap; holding still past the timeout emits exactly
  one LongPress (releasing afterwards is *not* a Tap); moving past slop starts
  a Drag whether or not a LongPress already fired, and a dragged gesture never
  ends as a Tap.
- The down position is routed through `BubbleMath.regionAt(...)` (see item 2):
  a touch that starts on a pill button can only ever become that button's tap —
  wandering off it produces nothing, never a drag, never a long-press.
- The adjust row's chips are now plain `clickable`s and **the scrim is gone
  entirely**: outside taps fall through to the page, and the row closes on a
  bubble tap or after 5 s without interaction.
- Unchanged: size bounds 48–128 dp, 12 dp steps, centre-keeping resize,
  `BubbleStore` persistence (screen fractions + size), the nothing-loaded
  shuffle-the-library play, and the accessibility actions (Play/Pause, Next,
  Previous, Make bigger, Make smaller).

Tests: `BubbleGestureTest` (11 tests — tap/long-press/drag classification,
button-region routing, pill geometry) plus the existing `BubbleMathTest`,
verified in a local kotlinc harness (20/20) before CI and green in CI.

## 2. Playing pill

When a track is loaded **and playing**, the round bubble expands into a pill:
the artwork disc stays anchored at the left (its tap still opens the full
circle player) and Previous / Play-Pause / Next buttons appear to its right.
Paused or nothing loaded, it shrinks back to the round bubble; the empty state
keeps its shuffle-the-library play behaviour.

- Geometry is pure math in `BubbleMath`: button size = disc × 0.75 clamped to
  44–64 dp; pill width = disc + 3 buttons (60 dp disc → 195 dp pill; 48 dp →
  180 dp; 128 dp → 320 dp). The current form's footprint drives the
  travel/clamp/fraction math, so dragging in pill form clamps by pill width
  and the persisted position keeps the control in the same screen region
  across form changes.
- Width animates with a spring (`animateDpAsState`); the disc never moves, so
  the pill grows rightward from it — no jump.
- The buttons are draw-only regions of the single gesture handler (item 1),
  not separate clickables: pressing one cannot start a drag or open the
  player, by construction of the gesture machine, and the routing is
  unit-tested.
- Long-press resize works in both forms; resizing changes the disc size and
  the pill scales from it.

## 3. Visibility ring

An always-on **2 dp stroke in the Music Space cream** (`ForestGlass.Text`)
around the floating control, over the existing shadow/elevation. It follows
the shape — a circle on the bubble, a stadium outline on the pill — so it
reads as a deliberate border against light album art and dark screens alike.
While the resize row is open the ring switches to the sage adjust colour
(`ForestGlass.Accent2`). No glow or blur effects.

## 4. Discover — top 1000 seeds, actually sourced

- The bundled asset `app/src/main/assets/discover_takeout_seed.json` was
  regenerated with the same Takeout parse: **version 2, top 1000 artists /
  top 1000 tracks**, title/artist/plays only, 96,707 bytes, no URLs or ids.
  Totals reconcile exactly with `listening-summary.json`: 65,625 plays,
  21,407 unique tracks, 6,825 unique artists. The copy inside the delivered
  APK was read back and verified at 1000/1000.
- Sourcing, not just bundling: the weighed-artist window rose from the top 60
  to the **top 150** Takeout artists (`SEEDS_WEIGHED`, log weighting
  unchanged), and history-only server name-search chains rose from the top 3
  to the **top 5** unmatched artists (`HISTORY_CHAINS`). Track matching
  already scanned the whole asset, so the history playlist's pool now covers
  all 1000 tracks.
- Honest cost: up to two extra server name-search chains per Discover
  refresh; the batch is still built once per generation and cached, so this
  is a refresh-time cost, not a per-open cost.

## 5. Twenty playlists per batch, hard artist/genre dedupe

- Batch size: **20 playlists** (`PLAYLISTS_SHOWN` 12 → 20): the pinned
  "From your listening history" playlist plus 19 from the rotating ring.
  Determinism and the next-generation guarantee (Refresh never repeats the
  set just shown) are preserved and tested for generations 0–7.
- Every candidate playlist now carries a **claim set**:
  `theme:<template instance>` for its own template, `artist:<key>` for its
  primary artist (the seed artist for Because/Deep playlists; the *dominant*
  artist — most frequent, stable tie-break — for the history playlist), and
  `genre:<lane>` for each genre it is based on (a lane playlist claims its
  lane; a mood playlist claims **every** constituent lane genre).
- `choosePlaylists` takes the pinned history playlist first (it claims
  first), then walks the whole ring taking only candidates whose claims are
  disjoint from everything already chosen. A **repair pass** follows: if the
  batch comes up short, it tries trading each non-pinned playlist for
  whatever its claims kept out, keeping any trade that fills more slots.
  This was necessary, not decorative — greedy walks stalled at 18 because
  three mood playlists claiming ten genres between them starved the lane
  playlists. The rule is never violated to fill a slot; a shallow pool would
  yield a smaller batch instead.
- Tests (`PlaylistsTest`): pairwise claim-disjoint batches for generations
  0–7, exactly 20 playlists per batch with a deep pool, Because vs Deep for
  the same artist never co-occur, mood genres never collide with lane
  genres, and a history playlist dominated by one artist blocks that
  artist's Because playlist. Local harness: 50/50 before CI.

## 6. Carousel

The "Playlists for you" section is now a horizontal **carousel**: a
`LazyRow` of 272 dp cards with `rememberSnapFlingBehavior` so cards snap
into place with the next card peeking. Cards reuse the existing
`PlaylistTile` (cover mosaic forward, title + subtitle visible). Tapping a
card opens the same playlist detail page as Phase 3 — Play / Shuffle /
Not-interested unchanged — and Refresh stays in the header. Rendering is
still cache-first: the kept batch paints before any refresh completes.
The sections below the carousel are unchanged.

## CI runs this round

Five runs on the Phase 4 tree (see BUILD-LOG.md table). The first failed to
compile (`changedToUpOrCanceled` no longer exists in this Compose version;
the release is now read from the change's `pressed` state). The next three
each failed on exactly one or two **upstream concurrency tests** —
`TotalsReadHoldTest`, `LeaveStopsBuildsTest`, `MixThreadingTest`,
`LikedHiddenRaceTest` — a different one each run, all in listening/playback/
library code this diff does not touch, the same flake pattern the Phase 2
components round recorded. The fifth run was fully green.

## Honest gaps

- **Nothing here has been seen on a phone.** The gesture machine is
  unit-tested as pure logic and the Compose wiring compiles and passes the
  suite, but real-finger behaviour — slop feel, long-press timing, pill
  button accuracy at 48 dp — can only be proven by Collin's device test.
- No `discover.db` change was needed: no new migration, dismissals
  untouched.
- Carried over from Phase 3: the `DiscoverDatabase/2.json` schema export is
  still uncommitted.
- The fork still has **no fixed keystore**: this build is signed with the
  CI runner's per-run debug key, so installing it over the Phase 3 build
  will likely require uninstalling first, which loses that build's local
  Discover dismissals.
