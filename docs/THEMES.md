# Themes and game art

The app draws every screen and game piece from one theme. Players choose a
theme and dark mode under **Appearance** (palette icon on the home screen), and
can create, edit, share and import their own.

## Built-in themes

| Theme | Look | Home layout |
| --- | --- | --- |
| Game table (default) | Navy and amber, green felt, ivory tiles, wooden checkers board | Colored tiles |
| Minimal | Neutral surfaces with one teal accent | List |
| Playful | Bright per-game colors, rounded shapes, violet felt, game-colored headers | Colored tiles |

Each theme has a light and a dark palette. Dark mode follows the system unless
the player picks Light or Dark.

## Adding a built-in theme (developers)

1. Add a `ThemeSpec` to `BuiltInThemes` in
   `app/src/main/java/com/simplegamegen/sudoku/ui/theme/ThemeModel.kt`, giving
   every `ThemeToken` in both the `light` and `dark` palettes (construction fails
   if one is missing), 30 `gameColors` in `GameId` order, a corner radius
   (0–28), a home layout and the two header/tile options.
2. Add it to `BuiltInThemes.all`. It then appears on the Appearance screen and
   can be used as the base for player themes.
3. Keep the `id` stable: saved selections and shared codes refer to it.

`ThemeModelTest` checks that every built-in theme is complete.

## Player themes

A player theme starts from a built-in theme and overrides only the colors marked
`editable` in `ThemeToken` (accent, header, table, tile face, Mahjong tile back,
card back, board squares, checkers and selection), plus corner roundness, home
layout, colored tiles and game-colored headers. Overrides apply in both light and
dark mode; surfaces and text colors come from the base theme so they stay readable.

Themes are stored locally in the `appearance` DataStore and included in Android
backup like other saves.

### Share codes

**Copy** on a player theme puts a one-line code on the clipboard:

```
SGT1|Night felt|playful|7|LIST|0|1|CARD_BACK=FFABCDEF,TABLE=FF123456
```

Fields: version, name, base theme id, corner radius, home layout, colored tiles
(0/1), game-colored headers (0/1), then `TOKEN=AARRGGBB` overrides. **Import**
accepts only complete, valid codes with a known base, editable tokens and
in-range values; anything else is rejected without changes.

## Game art

All pieces are drawn in code (`ui/assets/`) with no image files or third-party art:

- **Cards:** traditional red/black suits, standard pip layouts for 2–10, rank
  and suit indices in both corners, and double-headed J/Q/K court portraits. The
  card back uses the theme's card-back colors.
- **Mahjong:** layered ivory tiles with a themed back, traditional dots, bamboo
  (including the one-bamboo bird), characters 一–九 with 萬, winds 東南西北 and the
  red 中, green 發 and white dragons. Characters also carry a small numeral.
- **Dominoes:** bevelled tiles with sunk pips, a center groove and a brass spinner.
- **Checkers and Reversi:** ridged checkers with gold crowns for kings; glossy
  black and white discs on a green board.
- **Minesweeper:** raised covered squares, classic number colors (lightened in
  dark mode), drawn flags and mines.
