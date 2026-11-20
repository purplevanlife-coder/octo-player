# Phase 2 components round: fonts, type tokens, glass, player bar

*Branch `feature/discover-v1`, 2026-11-20. Written by reading the code only: **no Gradle was run and nothing was pushed.** The CI `build-debug` workflow (`:shared:core:allTests`, `:app:testDebugUnitTest`, `:app:assembleDebug`) is the first real compile and test run. Brief: `PHASE2-COMPONENTS-BRIEF.md`.*

## What changed, per item

### 0–1. The real faces, wired in (`ThemeFonts.kt`)

- **Font files:** the coordinator had already placed the seven TTFs in `design/src/main/res/font/`. I re-checked that each one is TrueType and that its OS/2 weight class is right: Playfair 500/600/700, DM Sans 400/500/600/700.
- **Families:**
  - `MusicSpaceDisplayFamily` is now `FontFamily(Font(R.font.playfair_display_medium, Medium), …semibold, …bold)`.
  - `MusicSpaceBodyFamily` is the same for the four DM Sans files.
  - Both are declared like `CoverFontFamily`. The stand-ins (platform serif, Inter Display) are gone.
- **Black headings:** Playfair has no Black weight. `OctoType.display` and `OctoType.title` ask for Black (900), so Compose picks the nearest weight on file, which is Bold (700). Because 700 is already "bold", Compose does not synthesise extra boldness. The comment says this.
- **Licences:**
  - `design/licenses/OFL-playfair-display.txt` and `OFL-dm-sans.txt` are committed.
  - The only list of bundled fonts in the repo is the README's **Credits** section. Both families were added there.
  - There is no in-app licences screen.

### 2. Type tokens applied (`displayType` / `bodyType`)

- **The accessor:** two `@Composable @ReadOnlyComposable` functions in `ThemeFonts.kt`:
  - `displayType(style)` = `LocalOctoFonts.current.displayStyle(style)`
  - `bodyType(style)` = `LocalOctoFonts.current.bodyStyle(style)`
  - Call sites wrap the `OctoType` style they already used. Only `fontFamily` changes; size, weight, spacing and line height are untouched.
- **Why not a theme-aware `OctoType`:** `OctoType` is a plain object with about 570 reads. Making it composable would touch every one of them. Wrapping is opt-in and can be reviewed site by site.
- **New rule in `OctoFonts`:** a style that already names a face keeps it. That covers the mono codes, `FontFamily.Monospace` sites and the covers' Inter Display. This way no caller can lose a deliberate face.
- **Under the five other themes:** the accessors return the *same object* they were given.
- **Shared components (most of the coverage):**
  - `PageHeader`: every page and tab title (`ScreenTitle` goes through it). The title is now measured in the face it is drawn in, so the "shrink to fit one line" step stays right.
  - `SectionTitle`: Home shelves, Discover sections, Search, Library "Recently added", the Album "More by" shelf, and the Artist sections.
  - `SongTitle`: every song row, song card, queue line, download row, the mini player title and the full player title.
  - `Items.kt`: album/playlist cards, the song card's artist line, song row subtitles, artist rows and artist circles.
- **Per-screen edits:**
  - **Hero names:** `AlbumHeader` title and `ArtistHeader` name use display.
  - **Home:** the empty-state headline uses display. The words under it, the "Music on this phone" card, pinned shelf cards, station cards and the resume card use body.
  - **Library:** section rows use body.
  - **Discover:** the updated line, section subtitles and note cards use body.
  - **Search:** recent searches, the outside-search line, hints and list "about" lines use body.
  - **Queue:** artist and "Autoplay" use body.
  - **Downloads:** the artist line uses body.
  - **Mini player:** the artist line uses body.
  - **Full player:** the artist line uses body. The cap height and baseline that align the title buttons are now measured on `bodyType(TitleStyle)`, the face actually drawn.
- **Player titles use body, not display:** the reference sets the player in body type (only nine headings carry `.display-font`), so the player follows it.
- **Deliberately left alone:**
  - Track and rank numbers in fixed-width columns, so their width cannot shift.
  - `CopyCards.kt` and the form/settings rows, whose layouts align on cap heights measured from `OctoType`.
  - Chips and labels.
  - Settings has no shared title component beyond `PageHeader`, which is covered.

### 3. Per-theme glass recipe (`ThemeGlass.kt`)

- **The value:** `OctoGlass(film, edge, frost, saturation, drop, playerBar)`, plus `GlassDrop(color, blur, y)` in CSS units.
  - It is provided as `LocalOctoGlass provides glassFor(active)` in `ThemeRoot`, next to `LocalOctoFonts`.
  - Code reads it through `ThemeGlass`. Inside `OverArt` (the player, chrome over covers) that returns `OctoDefaultGlass`, matching how the surfaces there switch to Octo's dark look.
- **Defaults:** `OctoDefaultGlass` has every field null, with `frost = GlazeFrost` and `saturation = GlazeSaturation`.
- **Music Space:** `MusicSpaceGlass` takes `ForestGlass` values:
  - film `GlassFill`
  - edge `GlassEdge`
  - frost 16
  - saturation 1.25
  - drop `GlassShadow` / 28 / 8
- **Where it is fed in.** Each changed site keeps the old expression verbatim when the value is unset:
  - `GlazeTint` = `ThemeGlass.film ?: glassFilm(0.16f)`. This is the default film of `Glaze`, `FloatingGlaze`, `GlazeButton`, the bar, and the bar's song-colour trace.
  - `Glaze` / `FloatingGlaze`: the default `frost` / `saturation` parameters are now `ThemeGlass.frost` / `.saturation`. Callers that pass their own keep them: `PopupFrost` 24, `DarkGlaze` 32/1.4, the player's `GlazeClearFilm`.
    - `Glaze` draws the theme's 1dp hairline over its lit rim when `edge` is set.
    - `FloatingGlaze`'s pooled shadow (cast from the 12dp-inset shape) uses `drop` when set.
  - `glassPanel` / `raisedPanel`: `drop` replaces `elevation1`, and the edge hairline is drawn. The card fill stays the solid themed card (see Risks).
  - `GlassSheet`: `drop` replaces the 32-blur shadow, and `edge` colours the top-edge line. The fill is unchanged; `SheetFill` is already the near-opaque player-bar olive from the foundation round.
- **Not changed:** blur on/off, hit areas, clip shapes, layer order, `DarkGlaze`, `LcdGlass`, `StatusGlaze`, `GlazeSelected`.

### 4. Player bar surface (`BarGlass.kt`, `BottomBar.kt`)

- **The composable:** `BarGlass(backdrop, modifier, shape, film, content)`.
  - When `ThemeGlass.playerBar` is null (the five themes), it calls `FloatingGlaze` with exactly the old arguments.
  - Under Music Space it draws, in a `Box(modifier)`, with the same content placement:
    - the fill `ForestGlass.PlayerBar` (rgba(19,26,16,0.94))
    - a 1dp top hairline in `GlassEdge`
    - the upward shadow `0 -8px 24px rgba(5,8,4,0.35)`
    - **no `hazeBlur` / backdrop at all**
  - The new `ForestGlass.PlayerBarShadowBlur` (24) and `PlayerBarShadowY` (−8) come from `theme.css`'s `.player-bar`.
- **Song-colour bar glow:** if that preference is on, its film is laid over the surface. The plain film is not, so the bar shows the exact `.player-bar` colour.
- **Both parts of the bar use it:** the tab capsule and the now-playing capsule. The bar is one material across both pieces.
- **`NowPlayingFace.kt` needed no change:** it draws only the face, which sits on that surface.
- **Untouched:** gestures (swipe, tap, fold), sizes, offsets and playback calls are all in `modifier` or `content`.
- **Testable seam:** `barLookOf(glass)` (Glaze / Surface).

### Stretch: 18 dp corners: skipped

Skipped because it is not low-risk:

- Cards, sheets and song panels take their corners from about 96 hard-coded `RoundedCornerShape(n.dp)` sites, not from a token. `OctoShapes` has no card or sheet entry.
- A per-theme radius means either a new token threaded through those call sites, or changing global `OctoRadius` values that are pinned by tests and shared with the desktop.

The right shape for next round is an `OctoGlass.radius` (or a `LocalOctoShapes`) read by `glassPanel` callers and `GlassSheet`'s `SheetShape`. The sage fill-accent role is out of scope, as briefed.

## Wiring points

| What | Where |
|---|---|
| Faces | `design/.../ThemeFonts.kt` (`MusicSpace*Family`, `displayType`, `bodyType`) |
| Glass value | `design/.../ThemeGlass.kt` (`OctoGlass`, `glassFor`, `LocalOctoGlass`, `ThemeGlass`) |
| Bar surface | `design/.../BarGlass.kt`, used twice in `app/.../ui/nav/BottomBar.kt` |
| Provided | `app/.../theme/ThemeRoot.kt` (`LocalOctoFonts`, `LocalOctoGlass`) |
| Source values | `shared/core/.../theme/ForestGlass.kt` (+2 player-bar shadow constants) |

## Changed files

- **Design module:**
  - `ThemeFonts.kt`, `Glaze.kt`, `GlassPanel.kt`, `GlassSheet.kt`
  - new: `ThemeGlass.kt`, `BarGlass.kt`
  - 7 fonts and 2 licences
- **App:**
  - `ThemeRoot.kt`, `PageHeader.kt`, `Layout.kt`, `SongTitle.kt`, `Items.kt`
  - `AlbumScreen.kt`, `ArtistScreen.kt`, `HomeScreen.kt`, `PinnedShelf.kt`, `Stations.kt`, `ResumeCard.kt`, `LibraryScreen.kt`
  - `DiscoverScreen.kt`, `SearchScreen.kt`, `QueueSheet.kt`, `DownloadsScreen.kt`, `PlayerOverlay.kt`, `BottomBar.kt`
- **Shared:** `ForestGlass.kt`.
- **Tests:** `ThemeFontsTest` (updated) and new `ThemeTypeTest`, `ThemeGlassTest`.
- **Docs:** `README.md` (credits).

## Tests

- **`ThemeFontsTest`** (plain JUnit):
  - The five themes name no face, and *every* `OctoType` style (all 16) comes back as the same object from both accessors.
  - Music Space resolves `MusicSpaceDisplayFamily` / `MusicSpaceBodyFamily`, not the old stand-ins. Only `fontFamily` changes: size, weight and letter spacing are checked.
  - The families are exactly the bundled `R.font` files at the right weights (through `FontListFontFamily` / `ResourceFont`).
  - A style with its own face (Monospace, Inter Display) keeps it.
  - Applying a face twice is the same as applying it once.
- **`ThemeTypeTest`** (Robolectric Compose): with `LocalOctoFonts provides fontsFor(theme)` stepped through all six themes:
  - `displayType` / `bodyType` hand back the very same style for the five.
  - Under Music Space they return the style in the Music Space faces.
- **`ThemeGlassTest`** (Robolectric Compose):
  - The five get `OctoDefaultGlass` (nulls, `GlazeFrost`, `GlazeSaturation`).
  - Music Space's values match the CSS (film, edge, 16, 1.25, drop 28/8, player bar fill, edge and −8/24 shadow).
  - `barLookOf` is Glaze for five and Surface for Music Space.
  - `GlazeTint` equals `glassFilm(0.16f)` under the five and the olive film under Music Space.
  - `ThemeGlass` inside `OverArt` is `OctoDefaultGlass` for all six.
- **The colour fixture:** `resolved.txt` is unchanged (`git diff b8ec70a5 -- '**/resolved.txt'` is empty). No palette or `ThemeBase` value changed; the only `shared/core` change is two new `ForestGlass` constants.
- **Contrast margins from the foundation notes:** these come from `ResolvedPalette`, which this round does not touch, so they hold exactly as CI measured them in run 44690480834:
  - text on selected ≈ 7.6
  - secondary on raised ≈ 7.45
  - off-button words ≈ 4.0 (< 4.5)
  - offTrack on card ≈ 3.3

  No test computes contrast on glass. I checked the new surfaces by hand (WCAG, Python):

  | Words | Over | Contrast |
  |---|---|---|
  | `--text` | the `.glass` film composited over page, card, raised | 14.9 / 13.8 / 12.9 |
  | `--dim` | the same | 10.2 / 9.4 / 8.8 |
  | `--text` | the player bar over page / card / raised | ≈ 15.1–15.3 |
  | `--text` | the player bar over pure white (worst case) | 13.1 |
  | `--dim` | the player bar, same cases | ≥ 9.0 |

## Local checks run (no Gradle)

- `file` and an OS/2 table read on all seven TTFs: TrueType, with the right weight classes.
- A bracket and brace balance check on every changed `.kt` file. All balanced.
- An import audit: every file that calls `bodyType` / `displayType` imports it exactly once, and none imports it unused. `BottomBar` no longer imports `FloatingGlaze`.
- A name-clash check for the new top-level symbols across `app`, `design`, `desktop*` and `shared`. None found.
- Checked that no source-scanning test (the `WholeTableGateTest` style) reads the files touched here.
- **Not run:** compile, unit tests, APK build, any on-device look.

## Risks for the CI build

1. **`ThemeFontsTest` uses `FontListFontFamily` / `ResourceFont` casts.** Both are public Compose classes. If the Compose version keeps `ResourceFont` internal, that one test fails to compile; replace it with an `assertEquals(FontFamily(Font(...)…), …)` equality check.
2. **Robolectric Compose tests** (`ThemeTypeTest`, `ThemeGlassTest`) write a `mutableStateOf` theme in `runOnIdle` and record values from inside composition. This is the same pattern as `OfflineMarksTest`, but it is new code that has never run.
3. **Composable default parameters** read `ThemeGlass` (`frost: Float = ThemeGlass.frost`). That is legal for `@Composable` functions, but it is the first time this file does it.
4. **Glass look under Music Space:**
   - The chrome glaze frost goes from CSS 4 to 16, and a 1dp cream hairline appears on every glaze (back buttons, capsules, menus).
   - Visually intended, but unseen on a phone.
   - A desktop-window "unfocused" state still shows the hairline.
5. **Panel cards stay solid under Music Space.** Only shadow and edge come from `.glass`. `GlassFill` composited over the flat page measures almost the same as the page (the foundation note 1 problem), so a translucent card would vanish.

## Known gaps

- No on-device check of Playfair/DM Sans metrics in tight places. These are the most likely spots for a width change to show:
  - the page header's one-line fit (handled by measuring in the face)
  - the mini player's marquee
  - song row ellipsis
- Digits in fixed-width slots stay in the platform face: track and rank numbers, song lengths (`LengthSlot`) and the page header's count. Numbers inside body text ("12 albums") follow DM Sans.
- Not applied this round:
  - per-theme corner radius (above)
  - sage fill-accent role
  - `--danger` decision (Collin's call)
  - Caveat / handwriting slot
  - the forest photo
- `DownloadsPill`, `GlassPopup` content type and settings rows read no type tokens yet.
- Desktop and iOS are untouched (the design module is Android only).
