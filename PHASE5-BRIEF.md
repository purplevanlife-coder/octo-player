# Phase 5 brief — bottom player bar, bubble retired

Branch `feature/discover-v1`. Collin device-tested Phase 4, then on
2026-11-20 ~18:00 PST rejected the Phase 3 circle player as the EVERYDAY
player: "the circle player isn't the right thing... I just want [the
player] to be down at the bottom. And I want it to have a skip pause
whatever." Muse recommended the design below and Collin said "Yeah. Do
it." This brief is that approved spec. Read BUILD-LOG.md, PHASE3-NOTES.md
and PHASE4-NOTES.md first for how the shell got here. Do NOT run Gradle
(it cannot work on this machine); CI is the check. Do NOT push — the
coordinator reviews the diff and pushes.

## The change, in one paragraph

Under the Music Space theme ONLY, the floating now-playing bubble and its
playing pill are retired, and a proper player bar is docked at the BOTTOM
of every screen: artwork thumbnail, song title + artist, Previous /
Play-Pause / Next right on the bar, and a thin progress line along the
bar's top edge. Tapping the bar opens the full player, exactly as tapping
the bar's capsule does under the classic shell. The tab bar stays docked
at the TOP (Phase 3, unchanged — Collin never objected to it), the pages
still turn by swipe, and every other theme's shell stays byte-for-byte
what it was.

## 1. ShellLayout (theme/ThemeLayout.kt)

- Replace the `floatingNowPlaying` field with `bottomPlayerBar`:
  - `ClassicShell = ShellLayout(dock = Bottom, bottomPlayerBar = false, swipePages = false)`
  - `MusicSpaceShell = ShellLayout(dock = Top, bottomPlayerBar = true, swipePages = true)`
- Update the file's comments: Music Space's now-playing control is a bar
  docked at the bottom, not a floating bubble.
- Update `app/src/test/java/app/winters/octo/theme/ThemeLayoutTest.kt`:
  the classic test asserts `bottomPlayerBar == false`; rename
  `musicSpaceDocksTheBarAtTheTopFloatsTheBubbleAndSwipes` to say the tab
  bar docks at the top, the player bar sits at the bottom, and the pages
  swipe. The `playerLayoutFor` tests are unchanged.

## 2. New composable: ui/nav/PlayerBar.kt — BottomPlayerBar

Model it closely on the classic bar's `PlayerCapsule` in BottomBar.kt
(same NowPlaying fields, same words, same live-station handling), widened
into a bar of its own:

- `val PlayerBarHeight = 64.dp` in this file.
- Signature in the house style:
  `BottomPlayerBar(haze: HazeState, now: NowPlaying, positionMs: () -> Long, actions: PlayerBarActions, artModifier: Modifier, artShape: Shape, modifier: Modifier = Modifier)`
  with `class PlayerBarActions(val onOpenPlayer: () -> Unit, val onPlayPause: () -> Unit, val onNext: () -> Unit, val onPrevious: () -> Unit)`.
- Outer modifier (applied by the caller in MainShell, not inside): the
  caller aligns it BottomCenter; inside, apply
  `.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = BarBottomGap).widthIn(max = 520.dp).fillMaxWidth().height(PlayerBarHeight)` — the same margins as BottomBar.
- Surface: `BarGlass(haze, shape = CircleShape)` with default
  `topDocked = false` — under Music Space that yields the theme's
  near-opaque player-bar olive with the hairline along its TOP edge and
  the shadow cast upward, which is exactly a bottom bar's treatment. Do
  not pass a film (the plain surface, as the bubble did).
- Whole-bar tap opens the full player: the outer Box carries a
  `clickable(role = Role.Button, onClickLabel = "Open player")` like
  PlayerCapsule's, and the transport buttons are separate clickables
  inside that consume their own taps (copy PlayerCapsule's structure:
  container clickable + `CapsuleButton`-style inner buttons — write a
  private copy here rather than making BottomBar's private one public).
- Contents, left to right:
  - Artwork 44 dp in a `Box(Modifier.size(48.dp))` centred, drawn with
    `Artwork(now.artwork, 44.dp, artModifier, shape = artShape, outside = isOutsideLibrary(now.trackId))`
    (same call as PlayerCapsule). If `now.casting`, overlay `CastingMark`
    at the artwork's bottom-end, as NowPlayingFace does. If a song is
    loaded but has no artwork, draw the Songs icon note in its place, as
    the bubble did (ForestGlass.Dim at 0.6 alpha, 36% of the box).
  - Title/artist column, `Modifier.weight(1f)`, horizontal padding 10 dp:
    `SongTitle` with `fadingMarquee` exactly as PlayerCapsule, and the
    artist line showing `now.starting ?: now.artist` in the caption
    style (11 sp), also as PlayerCapsule.
  - Transport: for a song — Previous, Play/Pause (icon from
    `now.isPlaying`), Next. For a live station (`now.live != null`) —
    mirror PlayerCapsule exactly: Previous/Next station only when
    `live.lineup.shows`, enabled per `hasPrevious`/`hasNext`, and the
    middle button is Stop (`LiveStopIcon`) while `now.playWhenReady`,
    Play otherwise.
  - Buttons: 40 dp touch boxes, 26 dp icons, tint
    `OctoColors.TextPrimary` (0.3 alpha when disabled), semantics
    contentDescription "Previous" / "Play" / "Pause" / "Next" (and the
    station wordings from PlayerCapsule).
- Progress line along the TOP EDGE: read the position with
  `rememberPositionMs(now, positionMs, everyMs = 500)` and the fraction
  with `now.fractionAt(...)` (both already used by BottomBar/bubble).
  Draw it inside the bar's clip, inset 22 dp at each end so it clears the
  stadium's curved ends: a 2.5 dp-high track in
  `Color(ForestGlass.Text).copy(alpha = 0.18f)` with the played part over
  it in `Color(ForestGlass.Accent2)` (the sage), width = track width ×
  fraction coerced to 0f..1f. Read the fraction while drawing (a
  `drawWithContent`/Canvas or a Box whose fill is sized in a `layout`
  pass) so the 500 ms steps redraw only the line. For a live station
  (`now.live != null`) draw NO line — a station has no length. Factor
  that decision as a pure function `playerBarShowsProgress(now: NowPlaying): Boolean`
  in this file and unit-test it (song → true, live → false, nothing
  loaded → false).
- Nothing loaded (`now.trackId == null`): the bar still shows — it is the
  always-available control surface, and it keeps the entry point the
  bubble's empty state and the classic round button have always offered.
  Artwork spot shows the Play glyph (22 dp, as NowPlayingFace's empty
  state), the text column shows "Shuffle your library" as the title with
  no artist line, there are no Previous/Next buttons, and the middle
  button is Play. Tapping the bar OR the Play button in this state calls
  `actions.onPlayPause` (which is `playback.togglePlayPause` — the same
  call the bubble's empty tap made). No progress line.
- Semantics on the whole bar: `contentDescription = nowPlayingWords(now)`
  and `stateDescription` from `nowPlayingState(now)` when non-empty (both
  are in NowPlayingFace.kt, same package — reuse them), role Button, and
  an accessibility `onClick` labelled "Open player" (or "Shuffle your
  library" when empty) doing what a tap does.

## 3. MainShell wiring (ui/nav/MainShell.kt)

- Delete the bubble block: the `bubbles` view model, `bubblePlace`,
  `bubbleActions`, and the `if (shell.floatingNowPlaying)` AnimatedVisibility
  that composed `NowPlayingBubble`, plus now-unused imports.
- `BottomBar` call: `playerShown = barPlayer && hasTrack && !shell.bottomPlayerBar`
  and `nowPlaying = !shell.bottomPlayerBar`. (Under Music Space the top
  bar stays tabs-only, as Phase 3 made it; under classic themes nothing
  changes.)
- Add, after the DownloadsPill block, the player bar's block, mirroring
  the bar's own show/hide:
  ```kotlin
  if (shell.bottomPlayerBar) {
      AnimatedVisibility(
          visible = barShown(playerOpen, stack.lastOrNull()),
          enter = fadeIn(tween(300)),
          exit = fadeOut(tween(300)),
          modifier = Modifier.align(Alignment.BottomCenter),
      ) {
          BottomPlayerBar(
              haze = haze,
              now = now,
              positionMs = playback::positionMs,
              actions = PlayerBarActions(
                  onOpenPlayer = { playerOpen = true },
                  onPlayPause = playback::togglePlayPause,
                  onNext = playback::next,
                  onPrevious = playback::previous,
              ),
              artModifier = Modifier.sharedElement(rememberSharedContentState(ArtKey), this),
              artShape = RoundedCornerShape(PlayerArtCorner),
          )
      }
  }
  ```
  (BottomBar keeps receiving its own shared-element artModifier exactly
  as today: under Music Space its capsule is never composed, so only one
  shared element is ever live — the same arrangement Phase 3 shipped.)
- DownloadsPill: under the top-docked shell it currently sits at the
  bottom-left with `bottom = BarBottomGap`, which was clear of the
  bubble's first place. The player bar now owns that strip. In the
  `topDocked` branch use
  `bottom = if (shell.bottomPlayerBar) BarBottomGap + PlayerBarHeight + 10.dp else BarBottomGap`
  and rewrite that comment (no bubble any more; the pill sits above the
  player bar). The classic branch is untouched.
- Page bottom room needs NO change: pages under the top-docked shell
  already keep `TopDockedBottomRoom` (88 dp) clear at the bottom, and
  the player bar takes 64 + 14 = 78 dp of it. Say so in a comment where
  the bar is composed... actually the Layout.kt comment for
  `screenBottomRoom` says the 88 dp is "only enough for the last row to
  scroll clear of the now-playing bubble in its first place" — update
  that comment in ui/common/Layout.kt to name the player bar instead.
  No value changes.

## 4. Deletions

Delete these files outright (all bubble-only; BubblePlace and BubbleKeys
live in BubbleMath.kt, the store/view model in BubbleStore.kt):
- app/src/main/java/app/winters/octo/ui/nav/NowPlayingBubble.kt
- app/src/main/java/app/winters/octo/ui/nav/BubbleGesture.kt
- app/src/main/java/app/winters/octo/ui/nav/BubbleMath.kt
- app/src/main/java/app/winters/octo/ui/nav/BubbleStore.kt
- app/src/test/java/app/winters/octo/ui/nav/BubbleGestureTest.kt
- app/src/test/java/app/winters/octo/ui/nav/BubbleMathTest.kt

Then grep the whole tree (main + test + androidTest) for `Bubble`,
`bubble` and `floatingNowPlaying`: nothing may remain except prose in
notes/log files. In particular check `BarShownTest.kt` (its comments
mention the bubble — update the wording; its assertions are about
`barShown` and must not change) and any string resources or DI bindings
that referenced the bubble store. `NowPlayingFace.kt` STAYS (the classic
bar's round button uses it).

## 5. The full player — decision already made, do not change it

`playerLayoutFor` and PlayerOverlay/CirclePlayer are NOT touched. The
circle-artwork player remains the full player under Music Space
(upright, songs): Collin's complaint was the circle player as the
everyday control surface, and the recommendation he approved keeps the
circle artwork in the full player while the bottom bar becomes the
everyday control. All full-player functionality (queue, lyrics,
favourite, shuffle/repeat, volume, speed, ring seek) stays exactly as
Phase 3/4 left it.

## 6. Tests

- ThemeLayoutTest updated per §1.
- New `app/src/test/java/app/winters/octo/ui/nav/PlayerBarTest.kt`:
  `playerBarShowsProgress` — a song (trackId set, live null) → true; a
  live station → false; nothing loaded (trackId null) → false. Build
  NowPlaying instances the way the existing nav tests do (see
  NowPlayingFaceTest.kt for the fixture pattern).
- Everything else in the suite must pass unchanged. Discover, the theme,
  the pager and all ~150 existing features are untouched by this round.

## 7. Write-up

- Create PHASE5-NOTES.md in the repo root, in the style of PHASE4-NOTES.md:
  what Collin asked, the bar's design and how it maps to PlayerCapsule,
  the empty-state and live decisions, the bubble removal inventory, the
  full-player decision and its reasoning, the downloads-pill move, and
  honest gaps (nothing seen on a phone; no fixed keystore).
- Append the Phase 5 section to BUILD-LOG.md (leave the CI result and
  APK delivery lines for the coordinator to fill in — mark them
  "pending CI").
- Commit everything (code + tests + notes) in one or two tidy commits on
  feature/discover-v1 with messages in the style of the log's history.
  Do NOT push.
