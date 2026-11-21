# Phase 5 notes — bottom player bar, bubble retired

Branch `feature/discover-v1`. Brief: `PHASE5-BRIEF.md`. Code written by
Claude Code on 2026-11-21; Gradle cannot run on this VM, so CI is the
check. **CI result: run 44854313870 SUCCESS, first run — 6,452 tests,
0 failures, 0 errors (18 skipped)**, counted from the run's test reports
(1,071 suites across the shared-core and app suites). Delivered as
`app-bottom-player-debug.apk` (see BUILD-LOG.md → APK deliveries).
**Built, not ready — only Collin's phone test proves the bar.**

## What Collin asked

After device-testing Phase 4, Collin rejected the circle player as the
EVERYDAY player on 2026-11-20 (~18:00 PST): "the circle player isn't the
right thing... I just want [the player] to be down at the bottom. And I
want it to have a skip pause whatever." Muse recommended a docked bottom
player bar; Collin said "Yeah. Do it."

## The change

Under the Music Space theme ONLY:

- The floating now-playing bubble and its playing pill are gone.
- A player bar is docked at the bottom of every screen (`BottomPlayerBar`,
  `ui/nav/PlayerBar.kt`): artwork, song title + artist, Previous /
  Play-Pause / Next on the bar, and a thin progress line along its top
  edge. Tapping the bar off the buttons opens the full player.
- The tab bar stays docked at the TOP (Phase 3, unchanged) and pages still
  turn by swipe.
- `ShellLayout.floatingNowPlaying` became `bottomPlayerBar`
  (`ClassicShell` false, `MusicSpaceShell` true). Every other theme's
  shell is unchanged: `ClassicShell` composes exactly what it did before,
  and the classic `BottomBar` call evaluates to the same arguments.

## The bar's design, and how it maps to PlayerCapsule

`BottomPlayerBar` is the classic bar's `PlayerCapsule` widened into a bar
of its own:

| | PlayerCapsule (classic) | BottomPlayerBar (Music Space) |
|---|---|---|
| Height | 56 dp (`BarHeight`) | 64 dp (`PlayerBarHeight`) |
| Margins | 20 dp sides, `BarBottomGap` bottom, ≤ 520 dp wide | same |
| Surface | `BarGlass`, `CircleShape` | `BarGlass`, `CircleShape`, `topDocked = false`, plain film — the theme's near-opaque olive, hairline on the TOP edge, shadow cast upward |
| Artwork | 36 dp in a 44 dp box + progress ring | 44 dp in a 48 dp box, no ring (the line replaces it), same `Artwork(..., outside = isOutsideLibrary(...))` call, `CastingMark` at bottom-end when casting, note glyph when a song has no art |
| Text | `SongTitle` + `fadingMarquee`, artist line `starting ?: artist` at 11 sp | identical |
| Transport | 36 dp buttons, 28 dp icons | 40 dp buttons, 26 dp icons, `TextPrimary` (0.3 alpha when disabled) |
| Station | Prev/Next station only when `lineup.shows`, enabled per `hasPrevious`/`hasNext`; Stop while `playWhenReady` | identical (same wordings) |
| Tap | container `clickable`, "Open player" | same; transport buttons are their own clickables inside (private `BarButton`, a copy — BottomBar's `CapsuleButton` stays private) |

Progress line: `rememberPositionMs(now, positionMs, everyMs = 500)` and
`now.fractionAt(...)`, drawn inside the bar's clip, inset 22 dp at each end
so it clears the stadium's curved ends: a 2.5 dp track in cream at 0.18
alpha with the played part in sage (`ForestGlass.Accent2`). The position
is read inside `drawBehind`, so the 500 ms steps redraw only the line, not
the bar. Whether to draw it is the pure `playerBarShowsProgress(now)`,
unit-tested in `PlayerBarTest`.

Semantics: the whole bar reads `nowPlayingWords(now)` with
`nowPlayingState(now)` as its state (reused from NowPlayingFace.kt), role
Button, click labelled "Open player" (or "Shuffle your library" when
empty). Each transport button has its own description.

## Empty-state and live decisions

- **Nothing loaded:** the bar still shows — it is the always-available
  control surface and keeps the entry point the bubble's empty state and
  the classic round button always offered. Play glyph (22 dp) in the art
  spot, "Shuffle your library" as the title, no artist line, no
  Previous/Next, middle button Play. Tapping the bar or Play calls
  `playback.togglePlayPause` (the bubble's empty-tap call). No line.
- **Live station:** PlayerCapsule's controls exactly; no progress line,
  since a station has no length.

## Bubble removal inventory

Deleted:
- `ui/nav/NowPlayingBubble.kt` (bubble + pill + adjust row)
- `ui/nav/BubbleGesture.kt` (gesture machine)
- `ui/nav/BubbleMath.kt` (incl. `BubblePlace`, `BubbleKeys`)
- `ui/nav/BubbleStore.kt` (store + `BubbleViewModel`)
- `test/.../ui/nav/BubbleGestureTest.kt`, `BubbleMathTest.kt`

MainShell: the `bubbles` view model, `bubblePlace`, `bubbleActions` and the
`NowPlayingBubble` block are gone; no imports were bubble-only (all
same-package). No DI module or string resource referenced the bubble
store (it was a self-contained `@HiltViewModel`). The bubble's kept place
in preferences is simply never read again; nothing migrates it.

Comments updated: `ThemeLayout.kt`, `Layout.kt` (`screenBottomRoom` now
names the player bar — no value change), `BottomBar.kt` (`nowPlaying`
doc), `CirclePlayer.kt` (one comment line: the art flies back to "the
player bar" — comment only, no code touched), `BarShownTest.kt` (wording
only; its assertion still counts 3 `barShown` sites: the bar, the
downloads pill and the player bar).

Grep of `app/src/{main,test}` for `Bubble`/`bubble`/`floatingNowPlaying`:
the only remaining hits are upstream's alphabet-scroller letter bubble in
`ui/common/Sorting.kt` (`BubbleSize`, present on `main`, unrelated to the
now-playing bubble), plus upstream's `TooltipBubble` and the "Bubblegum"
background in desktop/shared — none are ours and none were touched.
`NowPlayingFace.kt` stays (the classic round button uses it).

## The full player — unchanged by decision

`playerLayoutFor`, PlayerOverlay and CirclePlayer are untouched (apart
from the one comment word above). The circle-artwork player remains the
full player under Music Space (upright, songs). Collin's complaint was the
circle player as the everyday control surface; the approved
recommendation keeps the circle artwork in the full player while the
bottom bar becomes the everyday control. Queue, lyrics, favourite,
shuffle/repeat, volume, speed and ring seek are as Phase 3/4 left them.
The shared-element artwork flight now runs between the player bar's
artwork and the full player (under Music Space the tab bar's capsule is
never composed, so only one shared element is ever live — the Phase 3
arrangement).

## Downloads pill

Under the top-docked shell it sat bottom-left at `BarBottomGap`, clear of
the bubble's first place. The player bar now owns that strip, so in the
`topDocked` branch it sits at `BarBottomGap + PlayerBarHeight + 10.dp`
when `bottomPlayerBar` (just above the bar). The classic branch is
untouched.

## Page bottom room

No change: pages under the top-docked shell already keep
`TopDockedBottomRoom` (88 dp) clear at the bottom, and the bar takes
64 + 14 = 78 dp of it.

## Tests

- `ThemeLayoutTest`: classic asserts `bottomPlayerBar == false`; the Music
  Space test is renamed
  `musicSpaceDocksTheTabBarAtTheTopThePlayerBarAtTheBottomAndSwipes`.
  `playerLayoutFor` tests unchanged.
- New `PlayerBarTest` (3 tests): song → line; live station → no line;
  nothing loaded → no line.
- Net test count vs Phase 4: −(BubbleGestureTest 11 + BubbleMathTest 9)
  + PlayerBarTest 3 = −17, which reconciles exactly with CI's count:
  6,469 (Phase 4) → 6,452.

## Honest gaps

- **Nothing here has been seen on a phone** — CI compiled it and the full
  suite passed first try, and the delivered APK was verified (package,
  signature, dex contains BottomPlayerBar and zero bubble classes, seed
  asset still 1000/1000), but the bar's feel is unproven until Collin
  uses it.
- Touch accuracy of the 40 dp transport buttons beside the whole-bar tap,
  and how the 64 dp bar sits over each page's last row, need Collin's
  device test.
- The fork still has **no fixed keystore**: the CI build is signed with the
  runner's per-run debug key, so installing over the Phase 4 build will
  likely require uninstalling first (losing that build's local Discover
  dismissals).
