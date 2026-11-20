# Phase 2 foundation: the "Music Space" theme (tokens only)

*Branch `feature/discover-v1`, 2026-11-20. Written by reading the code only: **no Gradle was run and nothing was pushed.** The CI `build-debug` workflow (`:shared:core:allTests`, `:app:testDebugUnitTest`, `:app:assembleDebug`) is the first real compile and test run.*

## What was added

1. **A sixth theme, `ThemeChoice.MusicSpace`** (label "Music Space", a dark theme), appended after Octo, Moss, Dusk, Daylight and Paper.
2. **`ForestGlass`** (`shared/core/.../theme/ForestGlass.kt`): every value from `theme.css`'s Forest Glass palette, as ARGB ints. It is the one source the theme is built from. Values with no token yet are kept here for the components round.
3. **The theme's base colours** in `ResolvedPalette.kt` (`ThemeChoice.MusicSpace -> ThemeBase(...)`). Everything else (hover, tonal, selected, fill, focus ring, ink alphas, the over-art family, glows) is derived by the existing resolver, the same as for every other theme.
4. **Launch screens**: `Theme.Octo.Splash.MusicSpace`, plus the Match pairs `DaylightMusicSpace` and `PaperMusicSpace` (`values-v33/themes.xml`, night/notnight colours), wired in `ThemeApplier`.
5. **Type tokens**: `OctoFonts` and `LocalOctoFonts` (`design/.../ThemeFonts.kt`, Android only), with `fontsFor(theme)`. They are provided once in `ThemeRoot`.
   - The five existing themes name no face (null), so their text is unchanged.
   - Music Space has a display face and a body face (see *Fonts*).
   - **No component reads `LocalOctoFonts` yet.** Applying it is the next round's job, because changing `OctoType` globally would restyle every screen.
6. **Theme picker name fit** (`ThemePicker.kt`). "Music Space" is the first two-word name. At 2× text size, next to the check mark, neither word fits the card's 57 dp strip, so the existing "drawn whole in ≤2 lines" test would fail.
   - Now a name that overflows steps its size down (0.1 steps, floor 0.6×).
   - A name that fits keeps the caption size. All five existing names fit today, and a new test pins that they stay at 12 sp.

## How it registers

- **Selectable:** `ThemePicker` builds its cards from `ThemeChoice.entries`. Music Space appears automatically as the 6th card, filling the second row of three: Daylight · Paper · Music Space.
- **Persists:** `ThemeStore` and backups store the enum **name** (`"MusicSpace"`), exactly as for the others.
  - An older build or the iOS app reading that name falls back to the default (Octo) under the existing unknown-name rule.
- **Match:** it is dark (`light = false`), so it fills the night slot. Picking it while Match is on makes it the night theme. It can never land in the day slot (the existing rule coerces it to Daylight). The Match line reads "By day: Daylight. By night: Music Space."
- **Accent picker, glass tint, cover-art accent:** unchanged code paths. The resolver derives each accent's form for Music Space as it does for Moss or Dusk.
- **Order:** the first five keep their ordinals and names. `ThemesTest.storedNamesNeverChange` now lists six and separately pins the first five.

## Token mapping (CSS variable → Octo token)

| theme.css | Value | Octo token | How |
|---|---|---|---|
| `--bg` | `#131A10` | `page` → `OctoColors.Background`, Material `background` | exact |
| `--surface-alt` | `rgba(238,232,210,.13)` | `raised` → `OctoColors.SurfaceRaised` | **composited** over `--bg` → `#2F3529` |
| `--surface` | `rgba(26,33,22,.58)` | `card` → `OctoColors.BackgroundTertiary`, Material `surface` | **composited** over raised → `#23291E` (see note 1) |
| `.player-bar` | `rgba(19,26,16,.94)` | `sheet` + `sheetAlpha = 940` → sheets, menus, Material `surfaceBright` | `--bg` at 0.94; also gets the 5 % glass-accent trace every theme's sheet has. There is no separate player-bar token (note 4). |
| `--text` | `#F4EEDD` | `text`, `ink` → `TextPrimary`, `OctoInk.*` (ink alphas 85/60/25/10/5 %) | exact |
| `--dim` | `#CDC7AE` | `secondary` → `TextSecondary`; `muted` = `--dim` at 80 % → `TextMuted` | exact, muted derived the way Octo derives its own |
| `--border` | `rgba(244,238,221,.18)` | `edge` → `OctoColors.Edge`, Material `outline` | exact, kept translucent |
| `--accent2` | `#AEBFA5` | `accent` → `OctoColors.Accent` (hover, tonal, selected, fill, focus ring derived) | exact (see note 2) |
| `--amber` | `#EEC27A` | `complement` → `Complement`, `Star`, `Highlight` (heart, count pills), Material `secondary` | exact |
| `--hand` | `#EECFA3` | `third` → `Third` (playing bars, empty art), Material `tertiary` | exact |
| — | `#2F5A2C`, `#7A6236`, `#143426` | `glow` (the theme's resting ambient glow) | **new values**, forest tones standing in for the reference's forest photo; the resolver darkens them as needed so every word still reads |
| `.display-font` (Playfair) | — | `OctoFonts.display` | stand-in face (see *Fonts*) |
| body (DM Sans) | — | `OctoFonts.body` | stand-in face |
| `--space-*` | aliases | same as their targets above | — |

## What the token system couldn't express (kept in `ForestGlass`, not drawn)

1. **See-through surfaces.** Octo's `page`/`card`/`raised` are solid. They are tested for contrast and layer steps, and the blur comes from Haze at component level. The glass is therefore laid over what it sits on.
   - The raw `--surface` over `--bg` gives `#171E13`, too close to the page for the required lightness step (OKLab ΔL ≥ 0.035; it measures about 0.02). So the card is `--surface` over the raised pane, about 0.064 above the page.
   - On the phone this reads as glass on glass, not glass on a photo.
2. **`--accent` `#7D9168` (sage) as the accent.** Every accent must read at 4.5:1 on page, card and raised. The sage measures about 5.2 on the page, 4.35 on the card and 3.7 on raised, so the light sage `--accent2` is the accent.
   - No solid card light enough for the layer step can carry the sage at 4.5:1.
   - Cream words on a sage fill measure about 3:1, below AA for small text. The reference's own pills use a darker sage glass (`rgba(84,104,66,.6)`) for that reason.
   - The sage is kept as `ForestGlass.Accent` for filled pills, buttons and chips next round.
3. **`--accent-soft`, `--amber-soft`, `--note-ink`, `--note-bg`.** No roles exist for these. The nearest are the derived `AccentTonal` / `AccentSelected`, which are not the same colours.
4. **Player bar surface.** No distinct token exists. `.player-bar` is approximated by `sheet` (same colour and alpha). The bar's real surface is drawn in `BottomBar.kt` / `NowPlayingFace.kt`, which this round does not touch.
5. **`--danger` `#F0927F`.** Error and destructive reds are global, one per light or dark (`ERROR_WORDS_DARK #F97C7C`, `OctoColors.Destructive`). They are a cross-app contract (`FormColors.kt`, pinned in tests and shared with iOS). Music Space keeps `#F97C7C`, which reads ≥ 4.5:1 on all its layers (tested).
6. **`--radius` 18 dp.** `OctoRadius` / `OctoShapes` are global constants (10/12/6 dp etc.), not per theme. No theme radius token exists, so nothing was added.
7. **The `.glass` recipe** (blur 16, saturate 1.25, fill `rgba(24,31,20,.55)`, edge `rgba(244,238,221,.16)`, shadow `0 8 28 rgba(5,8,4,.35)`).
   - The Glaze, DarkGlass and LCD glass numbers are global constants. `glassFilm` / `glassRim` / `shadowColor` vary only with light/dark.
   - The nearest per-theme levers are `edge`, `sheet`/`sheetAlpha` and `shadow`, and those are set.
   - The rest is in `ForestGlass.Glass*`.
   - `Blur.kt` already converts CSS blur to Android's (`backdropBlur(16f)`), so these drop straight in.
8. **The type scale** (`--text-xs` … `--text-5xl`). Octo keeps its own `OctoType` sizes. Changing them would change every screen's layout, which is out of scope.
9. **Caveat (handwriting).** The app has no accent or handwritten type slot, so it was **skipped** this round as instructed. `ForestGlass.Hand` and `NoteInk` hold its colours.
10. **The forest photo** behind everything is an asset, not a token. The resting glow is the closest stand-in.

## Fonts: status

- **Not bundled.** Fetching from Google Fonts needs approval in this environment (the request was blocked), so no files were added. **Nothing is fetched at run time.**
- **Stand-ins now (Music Space only):**
  - display = `FontFamily.Serif`, the platform serif (Noto Serif on Android).
  - body = `CoverFontFamily`, the Inter Display already bundled in `design/src/main/res/font/`.
- **To drop the real faces in** (all OFL):
  1. Add `playfair_display_medium.ttf`, `playfair_display_semibold.ttf`, `playfair_display_bold.ttf`, `dm_sans_regular.ttf`, `dm_sans_medium.ttf`, `dm_sans_semibold.ttf`, `dm_sans_bold.ttf` to `design/src/main/res/font/`. Use static instances; or variable files with `FontVariation`, minSdk 29 is fine.
  2. In `ThemeFonts.kt`, change only `MusicSpaceDisplayFamily` / `MusicSpaceBodyFamily` to `FontFamily(Font(R.font.…, FontWeight.…), …)`.
  3. Update the two expectations in `ThemeFontsTest`.
  4. Commit each font's `OFL.txt` alongside, and add them to the app's licence list.
  5. If a handwriting slot is wanted later, add `hand` to `OctoFonts` with `caveat_*.ttf`.

## Tests

- **`shared/core` `MusicSpaceThemeTest`** (new) covers:
  - registration: last entry, ordinal 5, label, dark, `named("MusicSpace")`.
  - pick, store and read-back, and both Match slots.
  - every `ForestGlass` value against its CSS `rgba()`.
  - the key resolved roles (page, text, ink, secondary, muted, edge, accent, complement, third, glow count).
  - the composited layers (`#2F3529`, `#23291E`) and the sheet.
  - why the accent is `--accent2`.
  - error words on every layer.
  - headline pins of the five other themes.
- **`ResolvedFixtureTest`**:
  - The fixture is now generated over the **original five explicitly**. `resolved.txt` is **byte-for-byte unchanged**; that is the proof every role of every existing theme, accent, cover seed and glass setting is unchanged.
  - The file is shared with the iOS tests and `FormColorsTest`, both of which expect exactly five themes.
  - A new test asserts the five are "every theme but Music Space".
- **`ThemesTest`**: the stored-names test lists six and pins the first five's order.
- **`ThemeApplierTest`**: launch styles for Music Space and its two Match pairs.
- **`ThemePickerTest`**: new `theNamesThatFitKeepTheirSize`. The existing tests now also cover the Music Space card at 1× and 2×.
- **`ThemeFontsTest`** (new):
  - the first five name no face and `displayStyle` / `bodyStyle` return styles unchanged.
  - Music Space uses the serif and Inter Display, with only the face changing.
- **Existing tests that loop over `ThemeChoice.entries` now include Music Space automatically:** contrast of every role on every layer, layer steps, selected rows, off buttons, glass tint, glows and meshes, ambient looks, first-frame accent, theme store, and the picker.
  - I checked the new base against each of these **by hand** (Python and Gradle both blocked here). The tightest margins:
    - text on the selected row ≈ 7.6 (needs 7)
    - secondary on raised ≈ 7.45 (needs 7)
    - off-button words on the page ≈ 4.0 (must stay under 4.5)
    - offTrack on card ≈ 3.3 (needs 3)
  - If CI flags one of these, adjust `inkSecondary` / `offTrack` / `card` in the Music Space `ThemeBase`; nothing else depends on them.

## Things to know

- **Desktop** shares `ThemeChoice`, so its theme row shows Music Space with the same palette, in desktop type with no fonts. Desktop tests are not in CI and were not run.
- **iOS** has its own Swift enum and is untouched.
- **Write-without-compiler spots I'm least sure of:**
  - `ThemePicker`'s step-down: a state write in `onTextLayout`, and `TextUnit * Float`.
  - The Robolectric width estimate behind it. If names already fit, the step-down never fires.

## What the next round (shared components) should pick up first

1. **Apply the type tokens.** Use `LocalOctoFonts.current.displayStyle(...)` in shelf headers, page titles and hero names, and `bodyStyle(...)` in song rows and body text. A theme-aware `OctoType` accessor may be cleaner than editing call sites. Also decide whether to bundle the real fonts first (above).
2. **A per-theme glass recipe.** Feed `ForestGlass.Glass*` (blur 16 → `backdropBlur`, saturation 1.25, film, rim 0.16, shadow 28/8 at 0.35) into Glaze, `GlassPanel` and `GlassSheet` through a small per-theme value rather than the global constants. This is the single biggest visual step toward the reference.
3. **The player bar surface** in `BottomBar.kt` / `NowPlayingFace.kt`: near-opaque `ForestGlass.PlayerBar`, hairline top edge, upward shadow.
   - Heed the reference's own warning: no backdrop filter on the bar, because it trapped the full player.
   - Change the chrome only, never the playback behaviour.
4. **A per-theme corner token** (18 dp) for cards, sheets and song-list panels, as a theme shape value; `OctoRadius` stays for the other themes.
5. **A fill-accent role for the sage** (`#7D9168` / pill glass `rgba(84,104,66,.6)`, tan pill `rgba(146,118,74,.55)`) for chips, segmented controls and filled buttons, with `#FDF6E3` words checked for contrast on each fill.
6. **Collin's call:** keep the shared error red, or take `--danger` `#F0927F` for Music Space only (that touches the cross-app `FormColors` contract).
