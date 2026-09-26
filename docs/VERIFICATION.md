# Fix verification — September 23, 2026

The review fixes cover startup save replacement, incorrect Killer hints,
unsatisfiable generated Jigsaw layouts, missing Thermo overlays, overlapping
generation, stale hints, Jigsaw pencil-mark cleanup, invalid saved boards, and
lost elapsed time. Persistence writes are ordered; recoverable failures are
reported without replacing a working board.

## Checks completed

- Clean build: debug APK, unsigned release APK, unsigned release app bundle.
- 84 engine tests and 22 app tests passed. The app tests passed for both debug
  and release (106 distinct tests, 128 executions).
- Debug and release Android lint: zero errors; 13 dependency/tool version
  notices each. No checks were suppressed to obtain this result.
- Regression coverage includes 200 Jigsaw and 200 Thermo generation seeds,
  Killer hint sequences, overlapping operations, corrupt saves, queued writes,
  save failures, and timer checkpoints.
- Release APK passed `zipalign -v -c -P 16 4`; inspected packaged 64-bit native
  libraries have 16 KB-aligned LOAD segments. Runtime testing on a 16 KB device
  remains necessary.
- Android 16 / API 36 Pixel 5 emulator: fresh install and launch, disabled
  Continue before a game exists, new 4×4 Classic generation, number entry,
  returning home, force-stop, relaunch, and Continue succeeded. All 16 cell
  contents matched before and after restart, including the entered value;
  elapsed time resumed rather than resetting. No AndroidRuntime errors appeared.
- Home and restored game screenshots were visually inspected for layout and
  system-bar overlap. The separate test emulator was stopped afterward.

Logs, test-device files, and screenshots are in ignored `build/review-checks/`.
Gradle test and lint reports are under each module's `build/` directory.

## Release limits

The bundle is unsigned. Nothing was uploaded to Google Play. Physical devices,
older Android versions, tablets, 16 KB runtime behavior, backup/restore and Play
internal-track installation still need release testing. Final identity, signing,
privacy policy and store listing details remain owner decisions; see
[Google Play preparation](GOOGLE_PLAY.md).

## Crossword and word-search addition

- Added independent offline modes: connected 9×9 mini crosswords with original
  English clues, and six-word 8×8 searches in four themes.
- Full-board DataStore saves preserve crossword letters, found words, selected
  word paths and hint counts. Word-save format 2 still reads format 1.
- 117 distinct tests now pass: 90 engine tests and 27 app tests, with the app
  tests executed in both debug and release (144 executions, zero failures).
- Seed coverage includes 200 crosswords and 800 word searches. Tests verify
  connected crossword entries, numbered clues, no unclued adjacent runs,
  valid selections, completion, corruption rejection, independent restoration,
  write ordering and storage-error recovery. Alternate word occurrences retain
  the exact selected path across a save/load round trip.
- Android 16 emulator checks exercised crossword generation, keyboard answer
  entry, correct-answer feedback, word selection and found-word feedback.
  Updating and restarting the app retained the crossword answer and word-search
  progress, as well as the pre-existing Sudoku save.
- Visual checks found and corrected feedback moving the grid between taps and
  highlighting a different occurrence of a selected word. Grid cells remain
  48 dp and scroll sideways on narrow displays.

Implementation references consulted:
[Compose text input](https://developer.android.com/develop/ui/compose/text/user-input),
[DataStore](https://developer.android.com/topic/libraries/architecture/datastore),
[Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

## More content and difficulty levels

- Added Easy, Medium, Hard and Expert settings for both word games. Difficulty
  changes board size and answer count; crosswords also vary permitted answer
  lengths, and searches vary permitted placement directions. The picker shows
  these rules before starting. Hints remain available at every level.
- Expanded the crossword bank to 120 clues and searches to 12 themes with 20
  words each. New searches sample a word list rather than always using the same
  answers. See the difficulty table in the README.
- Word-save format 3 preserves difficulty while reading formats 1 and 2. Older
  boards are labeled Original without modifying their content or progress.
- Full verification passed: 121 distinct tests (93 engine, 28 app), 149
  executions including release app tests, zero failures. Debug/release lint has
  zero errors and only the 13 existing dependency/tool-version notices. Debug
  APK and unsigned release AAB builds succeeded.
- Generator tests cover every theme/difficulty combination, word-list variety,
  placement directions and counts, plus connected crossword grids across all
  four levels. Save tests cover difficulty round trips and legacy restoration.
- Android 16 emulator: existing saves opened as Original; the expanded picker
  and descriptions rendered correctly; Science/Expert generated a 12×12 search
  with 12 words; Expert crossword generated a 12×12 board with 12 clues. The
  large search grid scrolled to row 12, column 12. Both modes retained Expert
  and their board sizes after force-stop/relaunch. No AndroidRuntime errors were
  reported. The isolated emulator was stopped after verification.

Additional implementation reference: [Compose filter chips](https://developer.android.com/develop/ui/compose/components/chip).

## Advanced engine, Hangman and Mahjong Solitaire

- Added Hangman (12 themes, four levels) and Mahjong Solitaire (24/48/72/96
  tiles), separate versioned saves, restart/undo, letter hints and proven safe
  Mahjong hints. All five production modes share ongoing seeded generation,
  persistent bounded recent history and verification before replacing a save.
- Sudoku search now propagates forced values and reports explicit proof status
  and search statistics. Independent gates check Sudoku uniqueness, crossword
  connectivity/runs, search-word occurrences and Mahjong clearing witnesses.
  Budget exhaustion is distinguished from impossibility. See [ENGINE.md](ENGINE.md).
- Full automated verification passed: **138 distinct tests** (103 engine and
  35 app), **173 executions** including release app tests, zero failures.
  Debug/release lint: zero errors, 13 existing dependency/tool-version notices
  each. Debug APK and unsigned release AAB builds succeeded.
- New coverage includes 120 seeded Mahjong deals across four levels, Hangman
  across all theme/level combinations, Mahjong alternate routes/dead ends/budget
  exhaustion, corrupted saves, ordered writes, stale hints, storage recovery,
  concurrent seed allocation and reopening real DataStore files.
- Android 16 emulator: Hangman guesses and hints rendered correctly and restored
  after force-stop; Mahjong matched a pair, restored 22 remaining tiles, undid
  that move, generated the 96-tile Expert board and verified a safe hint. Layered
  boards rendered and scrolled correctly. Existing Expert crossword, Expert
  Science word search and Classic 4×4 Sudoku saves still opened. No AndroidRuntime
  errors were reported. An emulator System UI startup ANR cleared after selecting
  Wait; it was not an app crash. The isolated emulator was stopped afterward.
- Finite content can eventually repeat. Difficulty is structural; broader device,
  accessibility and signed Play-track testing remain in the release checklist.

## Solitaire, Minesweeper, Checkers, Reversi and Dominoes

- Added five complete offline modes with separate replay-validated saves, Undo,
  Restart, hints and new-game controls. Solitaire supports Draw 1 / Draw 3;
  Minesweeper has four board presets; the three competitive games each have
  four computer strengths. Rules and generation limits are shown in the app.
- The in-app capacity guide and [GAME_CAPACITY.md](GAME_CAPACITY.md) distinguish
  **10 games, 18 rule variants and 210 setting combinations** from unique boards.
  The eligible Hangman answer union is **227 words**. Mathematical deal/layout
  spaces are explicitly not claims of exhaustive generator coverage.
- Final automated checks: **162 distinct tests** (119 engine, 43 app), **205
  executions** including release app tests, zero failures. Debug/release lint:
  zero errors and 13 existing version notices each. Debug APK and unsigned
  release AAB builds succeeded, including the final visual fixes.
- New rule tests cover 200 Solitaire deals, 120 safe Minesweeper openings,
  80 clue-deduction runs, Checkers forced multi-jumps/crowning/draws, 40 complete
  Reversi matches, 100 complete Dominoes hands, hidden-information isolation,
  legal computer choices, corrupt saves and all new game/settings round trips.
- App tests cover isolated saves, computer-turn restoration, Undo across human
  and computer actions, cancellation on replacement, save ordering/failure
  recovery, corrupted/wrong-mode saves and repeated generation.
- Android 16: all five new modes opened and accepted moves. Solitaire moved an
  8 onto a 9, uncovered the next card, drew an Ace and moved it to a foundation;
  Minesweeper flood-opened 44 safe cells and proved a mine from visible clues;
  Expert Checkers/Reversi/Dominoes opponents replied legally. Reversi reached
  3–3 after the opening exchange; Dominoes formed a matching two-tile chain.
  Updating/relaunching retained Solitaire's foundation/columns and Checkers'
  position. Solitaire selection no longer shifts destination buttons. Checkers
  now renders distinct circular pieces. The capacity guide displayed all totals.
- No AndroidRuntime errors were reported. The isolated emulator's recurring
  System UI startup ANR cleared with Wait; it was not an app crash. The emulator
  was stopped after verification. Larger-device/font-scale/accessibility and
  signed Play-track checks remain part of the release checklist.

## Wordsworn follow-up — 2026-09-26

- Preserved the current three-hero, three-book overhaul. Fixed the word tray's
  changing height by reserving space for cards, preview text and Clear. Long
  previews scroll inside their reserved area. Card letters and edge icons now
  fit the card geometry, including the crowded M card.
- Added complete-run replay checks for all 12 hero/difficulty combinations.
  Each run must reach an ending; replay equality is checked at offers, stage
  flips, periodic combat turns and endings. This extends the existing short
  save round-trip test without changing game rules or balance.
- Full checks passed: **353 distinct tests** (262 engine, 91 app), **444 test
  executions** including release app tests; no failures or skipped tests.
  Debug and release lint each report **0 errors, 15 warnings and 2 hints**.
  The notices concern dependency versions and existing code outside Wordsworn.
  The debug APK and signed release AAB both built successfully.
- Android 16 emulator, 1080×1920: selected SOME, switched splay, cleared and
  reselected the word without moving the remaining hand cards. Playing it
  reduced the Typo Imp to 1/8 health and left Wren at 17/20 with 1 ink.
  Force-stop/reopen restored those values and the next monster action.
  At 1.3× text size, selecting CAR also kept the hand stationary and the preview
  readable. Restored the original text size afterward. The crash log was empty.
- Version-1 Wordsworn saves remain incompatible with the new version-2 rules;
  this pass does not add migration. Automated run completion is not a claim
  that every difficulty is balanced or every seed is winnable. The bundle was
  built locally; no Play upload or live-track verification was performed.

Layout references: [Compose modifiers](https://developer.android.com/develop/ui/compose/modifiers-list)
and [constraints and modifier order](https://developer.android.com/develop/ui/compose/layouts/constraints-modifiers).

## Wordsworn rule-set 3 — September 26, 2026

- Compared the final publisher-authored tabletop rulebook and publisher FAQ;
  the final structure is three books with two encounters each. See
  [the rule comparison and implementation scope](WORDSWORN_DIRECTION.md).
- Added checks for six-fight campaigns, finite supplies, phase order, persistent
  hexes, both core resources, alternate cores, modifiers, mandatory rewards,
  shop replacement/refill/refresh, malformed replay input and version-2 saves.
  Separate app tests verify per-hero unlock persistence.
- Full suite: **377 distinct tests** (284 engine, 93 app), **470 executions**
  including release app tests; zero failures. Debug and release lint report
  **0 errors, 15 warnings and 2 hints**. Debug APK and signed release AAB built.
- Android 16 emulator, 1080×1920: the version-2 save restored Wren at 17/20,
  one ink and the Typo Imp at 1/8, with the earlier-rules notice. A new Easy run
  accepted extra provisions and pocket alphabet: 30 health and three wilds.
  Inspected the deck, both enemy stages, the upcoming boss and read-only shop.
- Played through the first encounter using words and the hex core. Completed
  the required reward replacement; purchased an item for two stars and observed
  immediate refill. Refreshed the letter row once, then force-stopped/reopened:
  health 29/30, one star, four items, replacement letter, shop stock and used
  refresh all restored. A paid letter required replacement and refilled its
  shop slot. Leaving the shop reached encounter two, the book boss.
- The inspection dialog remained readable and scrollable at 1.3× text size;
  restored 1.0× afterward. Hints now show a waiting label and discard results
  if the game changes while calculation is in progress. Pass feedback no longer
  promises one ink when a run can have several wilds.
- After rebuilding, a normal hint selected AORTA; starting another hint and
  immediately passing left the new hand unselected. The crash log was empty.
- These checks establish the tested solo framework, not card-for-card source
  equivalence, universal seed solvability, statistical balance, or Play approval.
  Version-1 saves remain unsupported. No upload or store publication performed.

Async UI reference: [Compose side effects](https://developer.android.com/develop/ui/compose/side-effects).
