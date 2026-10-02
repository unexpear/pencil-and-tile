# Supported games and variation counts

Current implementation: **60 games, 70 rule variants, 424 selectable setting
combinations**. A difficulty, size or theme is a setting, not a distinct ruleset.
New games are generated on demand without a final level. Finite content can
eventually repeat; generator coverage has not been exhaustively enumerated.

| Game | Rule variants | Setting combinations | Distinct boards/deals/answers |
| --- | ---: | ---: | --- |
| Sudoku | 8 | 104 | Procedural, including 16×16 Classic, X, Jigsaw and Killer; custom grids unlimited |
| Samurai Sudoku | 1 | 4 | Procedural; exact generated-puzzle total not measured |
| Calcudoku | 1 | 4 | 4×4–7×7 by level; cage layouts and operations vary; total not measured |
| Kakuro | 1 | 4 | 6×6–12×12 by level; patterns and fills vary; total not measured |
| Futoshiki | 1 | 4 | 4×4–7×7 by level; sign layouts vary; each proven to have one solution |
| Nonograms | 1 | 4 | Pictures generated per game; each line-solvable, so it has one answer |
| Hitori | 1 | 4 | 5×5–8×8; each proven to have exactly one shading |
| Bridges | 1 | 4 | Islands on 7×7 to 9×9. One layout of non-crossing bridges |
| Slitherlink | 1 | 4 | One loop on 5×5 to 7×7 squares. A number is how many sides the loop uses |
| Towers | 1 | 4 | 4×4 and 5×5 skylines. Side clues say how many towers are visible |
| Lights | 1 | 4 | 5×5 to 7×7 rooms. One lamp placement lights every white square |
| Crossword | 1 | 4 | Original clues plus short WordNet definitions; exact connected-grid total not measured |
| Word search | 1 | 48 | 12 themes; category themes also use everyday WordNet words; layouts vary; total not measured |
| Hangman | 1 | 48 | **1,696 distinct eligible answers** across the theme lists, including everyday words from matching WordNet categories |
| Mahjong Solitaire | 1 | 4 | 4 layout/difficulty presets, shuffled faces; exact deal total not measured |
| Solitaire | 2 | 2 | Draw 1 / Draw 3; 52! theoretical deck orders, sampled by the generator |
| Minesweeper | 1 | 4 | 4 presets; theoretical layout counts below, actual coverage not measured |
| Checkers | 1 | 4 | **1 standard opening**, 4 computer strengths, varying match continuations |
| Chess | 1 | 4 | **1 standard opening**, 4 computer strengths. You play white. Castling, en passant, queen promotion, threefold repetition, and the 50-move draw |
| Go | 1 | 4 | **1 empty 19×19 board**, 4 computer strengths. You play black. Area scoring, positional superko, white receives 7.5 |
| FreeCell | 1 | 1 | Classic FreeCell. Seeded 52-card deals; not every deal is winnable |
| Sliding blocks | 1 | 4 | 6×6 boards. Each kept only after a search proves the target can reach the exit |
| Klotski | 1 | 4 | 4×5 boards. The 2×2 block must reach the bottom opening. Expert is the classic layout, 116 squares |
| Yacht | 1 | 4 | Five dice, thirteen boxes, up to three rolls. Four computer strengths. The higher total wins |
| Shut the Box | 1 | 4 | Nine tiles. Flip a set that adds up to the dice. One die once every tile left is 6 or less |
| Ten Thousand | 1 | 4 | Six dice to 10,000. The first bank is at least 500. One more turn after 10,000 |
| Ship, Captain, Crew | 1 | 4 | Five dice, three rolls, five hands. A 6, then a 5, then a 4; the other two dice are cargo |
| Shogi | 1 | 4 | 9×9. You play Sente. Promotion, drops, no pawn-drop mate. Four repeats draw unless one side checked every move. Both kings in camp can be counted |
| Reversi | 1 | 4 | **1 standard opening**, 4 computer strengths, varying match continuations |
| Cryptogram | 1 | 4 | Original sayings plus Tatoeba sentences, each in a fresh letter code |
| Word scramble | 1 | 4 | Rounds of 8 everyday words; any real anagram counts |
| Acrostic | 1 | 4 | Everyday hidden words; original clues plus short WordNet definitions |
| Code cracker | 1 | 4 | Each grid is a fresh sample of everyday words and a new number code |
| Dropquote | 1 | 4 | Same sayings as Cryptogram, laid out in columns |
| Common Threads | 1 | 4 | Built from rules each game; checked to have exactly one way to sort the sixteen words |
| Word Meaning | 1 | 4 | 337 reviewed sentences, plus lexicon examples and single-meaning Tatoeba sentences; answers judged on the device |
| Five Letters | 1 | 4 | Hidden words are WordNet lemmas; Easy and Medium use the everyday ones. Guesses are every accepted five-letter word |
| Blotwords | 1 | 5 | A fixed 12-step Discover trail, then grids built backwards per game (5×5 to 6×6); every grid can be finished |
| Spider Solitaire | 3 | 3 | 1, 2 or 4 suits; shuffled two-deck deals, not certified winnable |
| Pyramid Solitaire | 1 | 4 | 52! theoretical deck orders, sampled; not every deal clears |
| Memory | 1 | 4 | 6–15 pairs of Mahjong pictures, shuffled per game |
| Dots and Boxes | 1 | 4 | 1 empty board per size; matches differ by play |
| Sprouts | 1 | 4 | 2–5 starting spots placed per game |
| Magnetic cluster | 1 | 4 | 1 empty ring; matches differ by play |
| 2048 | 1 | 4 | Seeded spawns; no final level |
| Connect Four | 1 | 4 | Standard 7×6 gravity board; four in a row |
| Mastermind | 1 | 4 | 4 pegs from 6 colors, 10 guesses × 4 code styles |
| Battleship | 1 | 4 | Classic 10×10 fleet × 4 computer strengths |
| Tetras | 1 | 4 | Seeded 7-piece bags; no final level |
| Letterfall | 1 | 4 | 7×7 boards; orthogonal words of 3+ letters, gravity, combos, and a move-limited target score |
| Mancala | 1 | 4 | Six pits a side, four stones; sow, extra turns, and captures |
| Five in a row | 1 | 4 | 11×11 board; five or more in a line |
| Word ladder | 1 | 4 | One-letter steps between everyday English words; 3 to 5 letters |
| Honeycomb | 1 | 4 | Seven letters, one required. Everyday words score their length; other accepted words score 1 |
| Letter Draw | 1 | 4 | A handful of letters and a clock. Reach a target length; the rack always has such a word |
| Dominoes | 1 | 4 | 137,680,171,200 theoretical pairs of opening hands; boneyard order adds variety |

Sudoku's 88 combinations are seven variants × three sizes × four difficulties,
plus Killer's one size × four difficulties. Killer also has its own home tile, but
it is counted once, as a Sudoku variant. Word search and Hangman each offer
12 themes × four levels. Hangman's count is the union of eligible answer strings,
so repeated words across themes or levels are counted only once.

## Theoretical spaces are not shipped-content counts

- A 52-card deck has 52! ≈ **8.07 × 10^67** orders. Not every Solitaire deal
  is winnable. This seeded generator is not claimed to reach every deck order.
- Two labeled Dominoes players receiving seven unordered tiles each have
  C(28,7) × C(21,7) = **137,680,171,200** possible hand pairs. The remaining
  14 tiles can also have different draw orders. The app samples this space.
- For a fixed Minesweeper first click, the board excludes that cell and its
  neighbors. If that excludes k squares, the theoretical placement count is
  C(total cells − k, mine count). For an interior opening (k = 9):

| Preset | Board / mines | Theoretical layouts for that fixed opening |
| --- | --- | ---: |
| Easy | 8×8 / 8 | C(55,8) = **1,217,566,350** |
| Medium | 9×9 / 10 | C(72,10) = **536,211,932,256** |
| Hard | 16×16 / 40 | C(247,40) ≈ **2.214 × 10^46** |
| Expert | 30×16 / 99 | C(471,99) ≈ **6.870 × 10^103** |

Corner/edge openings exclude fewer cells and have different counts. A mine
priority shuffle is retained for restoration; different shuffles can produce
the same mine layout. Recent-shuffle avoidance does not prove unique minefields.
Theoretical counts exceed the layouts a finite seeded generator can necessarily
reach. No exact unique-output count or infinite-uniqueness guarantee is claimed.
