# Puzzle interaction and word-context fixes

Verified September 26, 2026. These changes were subsequently uploaded as version
1.1.1 (code 3) to the existing Alpha track and submitted for review. Google quick
checks and approval were still pending; see `RELEASE_READINESS.md`.

## Findings and scope

- Word Meaning imported short dictionary examples before curated contexts. The
  importer now binds each reviewed sentence to its exact WordNet sense. All 337
  word IDs, difficulty levels, definitions, and grading phrases remain unchanged,
  preserving saved rounds. See `tools/meaning/README.md` for editorial checks.
- Crossword nested a horizontal scroller inside the shared zoom surface. Its grid
  now starts fitted, and the shared surface owns movement on both axes. The
  explicit Move board mode consumes single-finger drags before page scrolling or
  cell taps; ordinary play and two-finger gestures remain available.
- Dots and Boxes had an unwrapped score row. Scores now wrap at large text sizes.
  Two-dot selection is the default, with a switch for the existing line-tap mode.
  Its instruction remains stable while choosing the first and second dots.
- Crossword now shows the active clue above the grid, sizes answer slots to fit,
  separates Across and Down lists, and uses original crossword-and-pencil art.

## Automated verification

- Engine: 286 tests passed, including complete context-bank checks and grading for
  words with multiple meanings.
- App: 96 tests passed, including all Dots edge mappings in both directions,
  invalid/claimed pairs, and vertical/horizontal zoom bounds.
- Context source/importer: 3 Python tests passed.
- Debug assembly and Android lint passed. The final APK contains the new control
  translations. No release version or signing configuration was changed.

## Emulator verification

Android API 36, 1080 x 1920 viewport:

- Restored an existing Word Meaning round with its score and new full sentence.
- Crossword: selected clue visible above the grid; entered RIVER using the
  keyboard action and observed the solved answer; switched Across/Down lists.
- At 1.5x zoom, Move board dragged the grid about 199 pixels vertically and 168
  horizontally while the clue/header stayed fixed and the selected answer stayed
  unchanged. Fit restored the grid; ordinary page scrolling reached answer entry.
- Inspected the Crossword home artwork and play layout.
- Dots: first tap selected without drawing; neighboring tap drew; same-dot tap
  cancelled; the line-tap switch worked; the computer responded and scored six.
  The final instruction kept identical board bounds before/after dot selection.
  A later two-dot move completed a box and displayed You 1 / Computer 6.
- At font scale 2.0, Computer 6 wrapped onto its own fully visible score chip.

Physical-device multi-touch and every puzzle screen were not exhaustively tested.
The shared pan implementation was manually exercised on Crossword.

## API references

- [Compose pointer event handling](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)
- [Compose flow layouts](https://developer.android.com/develop/ui/compose/layouts/flow)
- [Compose drawing](https://developer.android.com/develop/ui/compose/graphics/draw/overview)
