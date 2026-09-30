# Generation and verification

All ten production modes use `PuzzleFactory`. It allocates a persistent seed,
generates and verifies a puzzle, then reserves its fingerprint before returning
it. CPU-heavy board generation runs off the UI thread. The recent window is at
most 32 per game/settings; Hangman
uses at most the eligible word-pool size minus one. There is no last level, but
finite vocabulary and board spaces can eventually repeat. Generation retries
up to 12 candidates within a 30-second cancellable operation. Failure preserves
the current game and offers another attempt.

## Sudoku

The solver combines minimum-remaining-values branching and forced single
propagation. Search reports include visited nodes, forced placements, solutions
found and whether the search exhausted the possibilities. Uniqueness requires
exactly one solution and an exhausted search; a node limit produces INCOMPLETE,
never a false unique or impossible result. Inputs are copied before searching.

The production verifier independently checks digit ranges, givens, the stored
solution against the active variant constraints, and uniqueness with a bounded
search. The factory applies this gate to all eight Sudoku variants.

## Samurai Sudoku, Calcudoku and Kakuro

`logic/` holds one model (`LogicRules` + `LogicPuzzle`), one solver and three
seeded generators. The solver does a most-constrained-cell search with digit
bitmasks. It checks cages with exact arithmetic bounds, and runs with a
precomputed table of which digits can still complete a run, given its empty
squares, remaining sum and used digits. It counts solutions up to a limit and
reports budget exhaustion separately.

- **Samurai:** random full fill, then symmetric removal of given pairs while the
  puzzle stays uniquely solvable, down to the level's floor.
- **Calcudoku:** random Latin square and random cage partition, with operations
  chosen per level. If two solutions exist, a cell where they differ becomes a
  single-cell cage, and the remaining cells are regrouped.
- **Kakuro:** a symmetric black-square pattern that never creates one-square
  runs, and a fill biased toward low and high digits, whose sums have fewer
  combinations. If two solutions exist, the digits of the runs involved are
  re-rolled, with an occasional extra black square.

`LogicVerifier` re-proves uniqueness before a puzzle replaces a save. Saves use
the versioned `LogicSaveCodec` and are validated on load.

## Futoshiki

Futoshiki uses the same logic solver with "less than" pairs as an extra
constraint. The generator starts from a random Latin square with a
level-dependent share of signs (and givens on easier levels). While two
solutions exist, it adds a sign that the other solution breaks, or pins a cell.

## Newer games

All newer games keep their rules in immutable engine states with versioned text
saves that are validated on load. The app's shared `PlayViewModel` adds saving,
undo (one step per human action, including the computer's reply) and computer
turns.

- **Cryptogram, Word scramble, Acrostic** (`wordplay/`) draw on the shared word
  lists. Cryptogram and Dropquote use the original sayings plus Tatoeba sentences.
  Crossword and acrostic clues add short WordNet definitions to the original bank.
  Scramble and code cracker sample everyday words. Cryptogram codes never map a letter to itself.
- **Spider, Pyramid, Memory** (`cards/`) are seeded deals. Spider removes
  finished same-suit runs automatically and offers heuristic hints.
- **2048 and Tetras** (`arcade/`) derive every tile spawn and piece from the seed
  and a counter, so saves restore exactly.
- **Dots and Boxes** (`duels/`): the computer avoids handing over boxes. When it
  must, it gives away the fewest, and it searches the last 12 (Hard) or 16
  (Expert) edges exactly with a memoized search.
- **Magnetic cluster**: the computer samples spots and prefers ones that snap
  nothing, with more samples at higher strengths.
- **Sprouts** routes curves on a 48×48 grid where existing curves and other
  spots are obstacles. A route exists exactly when two spots share a region.
  Routes are smoothed by corner-cutting, which stays inside free cells. The
  computer takes immediate wins and, at Hard and Expert, avoids moves that allow
  an immediate reply win.
- **Connect Four** (`duels/ConnectFour.kt`): a 7×6 gravity board. Easy drops
  mostly at random, Medium takes wins and blocks threats, and Hard and Expert
  search ahead (depth 4 and 6) with alpha-beta. Saves are versioned text.
- **Mastermind** (`duels/Mastermind.kt`): the computer hides 4 pegs chosen from
  6 colors, duplicates allowed, and you have 10 guesses. Feedback is two counts
  only: right color in the right place, and right color in the wrong place.
  Easy is a permutation of four colors, Medium is four different colors from
  all six, Hard repeats a color without being four of a kind, and Expert is any
  classic code. Saves are versioned text.
- **Battleship** (`duels/Battleship.kt`): a 10×10 fleet (carrier 5, battleship 4,
  cruiser 3, submarine 3, destroyer 2). Ships may touch and cannot overlap.
  You place, then fire first. Easy mostly shoots at random, Medium hunts the
  squares next to a hit, Hard adds checkerboard parity, and Expert counts where
  the remaining ships can still lie. Saves are versioned text.
- **Mancala** (`duels/Mancala.kt`): Kalah, six pits a side and four stones.
  Easy sows at random, Medium takes captures and extra turns, and Hard and
  Expert search ahead (depth 4 and 6). Saves are versioned text.
- **Five in a row** (`duels/FiveRow.kt`): an 11×11 board. Five or more in a
  line wins. Easy places near existing stones, Medium takes wins and blocks,
  and Hard and Expert search a short list of nearby squares. Saves are versioned text.
- **Chess** (`tabletop/Chess.kt`): standard chess. You play white. Pawns promote
  to a queen. Castling and en passant are included. Easy picks a legal move,
  Medium looks one move ahead, Hard and Expert search two and three moves with
  a node cap. The same position three times, or 50 moves each with no capture and no pawn move, draws.
  Saves are versioned text.
- **Go** (`tabletop/Go.kt`): 19×19. You play black. Chinese area scoring and
  positional superko. White receives 7.5. Easy places almost at random. Higher
  levels score a short list of moves and look further ahead, counting nearby stones so it plays toward the middle.
  KataGo's pretrained network is free to use, but it only runs inside KataGo's own program.
- **FreeCell** (`cards/FreeCell.kt`): its own table, separate from Klondike,
  Spider, and Pyramid. Four free cells, eight face-up columns, foundations Ace
  to King by suit. Not every deal is winnable.
- **Sliding blocks** (`grids/SlidingBlocks.kt`): vehicles slide along their
  length on a 6×6 grid. A board is kept only when a search proves the marked
  vehicle can reach the exit.
- **Klotski** (`grids/Klotski.kt`): a 4×5 board. The 2×2 block wins on the bottom
  opening. Each square a block slides counts as one move. Expert is the classic
  opening, 116 squares. Shorter levels are scrambles a search has proved solvable.
- **Word ladder** (`wordplay/WordLadder.kt`): change one letter at a time
  between two everyday words. Steps may use any accepted word. Easy is three
  letters; Expert is five. The par is the shortest route. Saves are versioned text.
- **Honeycomb** (`wordplay/Honeycomb.kt`): seven letters, one required. Everyday words score their length; any other accepted word scores 1; using all seven scores 7 more.
- **Letter Draw** (`wordplay/LetterDraw.kt`): a dealt rack and a clock. Each letter is used only as often as it was dealt. The target length wins; the rack is kept only when some accepted word reaches it.

## Grid Sudoku (16×16 and custom grids)

`grids/GridSudoku.kt` handles square Sudoku of any size from 4 to 16 with any
region map, extra groups, Killer cages, odd/even squares and whole-grid rules
(diagonals, anti-knight, anti-king, non-consecutive).

- **Solver:** bitmask candidates with naked and hidden singles and cage-sum
  limits, branching on the square with fewest candidates or the digit with
  fewest places in a unit, whichever is narrower. It counts solutions up to a
  limit and reports whether the search finished within its node budget.
- **Full grids:** random fills restart with short budgets that grow, because
  search times are heavy-tailed.
- **Jigsaw regions** for generated puzzles are carved out of a solved grid by
  swapping same-digit squares between neighbouring regions, so the grid stays a
  valid answer. The editor's random jigsaw trades squares between neighbouring
  boxes while keeping regions connected.
- **Digging:** givens are removed (in symmetric pairs) while the puzzle keeps
  one answer. Easy and Medium, and all Killer grids above 9×9, must also stay
  solvable by deduction alone, which is itself a proof of uniqueness.
- **Editor checks** (`GridDraft.check`): layout problems (region sizes, cage
  totals that no distinct digits can make, oversized groups), clashing digits,
  then the solver: no answer, one answer (and whether deduction alone solves
  it), several answers (with the squares that differ) or too open to decide.

## Nonograms, Hitori, Code cracker and Dropquote

- **Nonograms** (`grids/`): pictures are random noise smoothed into shapes. A
  picture is kept only if repeated line-by-line reasoning (a forward/backward
  pass over the possible run placements of each line) fills every square. That
  proves the answer is unique and needs no guessing.
- **Hitori**: the generator starts from a random Latin square, chooses a
  maximal pattern of non-touching shaded squares that keeps the white area
  connected, and copies a number into each shaded square from its row or column.
  A counting solver checks it (touching, repeat and cut-point propagation, then
  branching). Extra shading also counts as a different answer, so a puzzle is
  kept only when exactly one shading exists.
- **Code cracker**: words from the bundled vocabulary are packed around a long
  centre word, always crossing an existing word. A placement is undone if it
  would create a run of letters that isn't a placed word. The best of 16 layouts
  is kept, and letters get a shuffled number code.
- **Dropquote**: a saying is word-wrapped so words never split, then trimmed to
  its longest line. Each column only accepts its own letters.

## Word games and Hangman

Crossword verification discovers maximal across/down runs, compares them to the
clued entries and checks connectivity. Word-search verification scans the grid
independently for every target word and checks difficulty rules. Saved word grids
also pass this validation. Hangman selects from the bundled themed vocabulary,
validates ordered guesses and rejects progress continuing after a win or loss.
These difficulty settings are structural, not calibrated human difficulty ratings.

## Mahjong Solitaire

Tiles occupy two coordinate units in each direction. A tile is free when no
higher tile overlaps it and either horizontal side is open. Matching pairs are
removed. The seeded generator first constructs a legal geometry-clearing order,
then assigns matching faces along that route. An independent replay verifies
every generated deal and restored move history.

Hints reuse the original route when it remains valid. Otherwise a memoized
depth-first search uses precomputed blocker masks and a remaining-tile bit set.
The default search budget is 100,000 nodes. Results distinguish SOLVED,
UNSOLVABLE and LIMIT_REACHED. Only a proven clearing route yields a highlighted
safe pair. Legal choices can still trap a board; Undo and Restart support recovery.
The 24–96 tile layouts are compact Solitaire deals, not four-player Mahjong.

## Card and competitive board games

The five tabletop modes store the exact initial card/tile/mine-priority
permutation and an action log. Version 1 restoration validates the permutation
and replays every action through the rules engine. It does not depend on future
versions of Kotlin's random-number implementation reproducing an old shuffle.
Generated Solitaire and Dominoes deals avoid recent identical initial setups.
Minesweeper reserves mine positions on the first reveal, excluding that square
and its neighbors; repeat history tracks the initial priority permutation,
not a proof that resulting mine layouts cannot repeat.

Checkers and Reversi intentionally keep the standard starting position. A new
match obtains a new seed for computer tie-breaking, not a random illegal opening.
Computer search uses alpha-beta with iterative deepening and a 1,500/6,000/18,000
node budget for Medium/Hard/Expert; Easy chooses a seeded random legal move.
Dominoes increases pip-shedding, hand-flexibility and double-tile preferences
across levels. It never evaluates the opponent's hidden hand or boneyard order.
These strengths have not been calibrated to human ratings.

Solitaire's hints are legal-move suggestions, not a complete win solver.
Minesweeper uses visible neighbor counts, the total mine count and subset
deductions. It does not peek at hidden mines or assume flags are correct. If no
deduction is available, it says so. Shuffled Solitaire deals and Minesweeper's
later positions are not certified solvable without guessing.

Checkers supports compulsory capture chains, crowning that ends a turn,
threefold repetition and an automatic 40-moves-each no-progress draw. Reversi
passes automatically only when no move exists. Dominoes uses a documented
single-hand Draw rule selection: human leads, any opening tile, draw only when
unable to play, no reserved boneyard tiles, fewer pips wins a blocked hand.

Opponent work runs off the UI thread, can be cancelled and is checked against
the current game revision. Interrupted saved computer turns resume on load.
Undo removes the last player action (the whole capture chain in Checkers) and
following computer replies; Restart preserves
the deal. Moves and old queued saves cannot overwrite a replacement game.

## Storage and cancellation details

DataStore atomically reserves seeds and bounded history. Each game has its own
save. Ordered writes keep an old snapshot from overwriting newer progress;
failed writes can be retried. Hint revisions discard results after moves or game
replacement. Interruptible worker calls and engine checkpoints stop cancelled
searches instead of leaving cancelled CPU-heavy work running in the background.

References consulted:

- [Android DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [PreferenceDataStoreFactory](https://developer.android.com/reference/kotlin/androidx/datastore/preferences/core/PreferenceDataStoreFactory)
- [Kotlin runInterruptible](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-interruptible.html)
- [KDE Mahjong Solitaire rules](https://docs.kde.org/trunk_kf6/en/kmahjongg/kmahjongg/howto.html)
- [Bicycle Klondike](https://bicyclecards.com/how-to-play/klondike) (Draw 1,
  unlimited redeals and movable partial face-up runs are this app's stated options)
- [WCDF English Checkers rules](https://nccheckers.org/NCCA/WCDF%20Checker%20-%20Draughts%20-%20English%20Rules.htm)
- [World Othello Federation rules](https://www.worldothello.org/about/about-othello/othello-rules/official-rules/english)
- [Draw Dominoes rules and variations](https://www.pagat.com/domino/line/draw.html)
- [Simon Tatham's Mines rules](https://www.chiark.greenend.org.uk/~sgtatham/puzzles/java/mines.html)
- [Compose state ownership](https://developer.android.com/develop/ui/compose/state-hoisting)
