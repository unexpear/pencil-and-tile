# Supported games and variation counts

Current implementation: **32 games, 42 rule variants, 313 selectable setting
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
| Crossword | 1 | 4 | 120 original clues; exact connected-grid total not measured |
| Word search | 1 | 48 | 12 themes × 20 words; selections/layouts/filler vary; total not measured |
| Hangman | 1 | 48 | **227 distinct eligible answers** across all themes and levels |
| Mahjong Solitaire | 1 | 4 | 4 layout/difficulty presets, shuffled faces; exact deal total not measured |
| Solitaire | 2 | 2 | Draw 1 / Draw 3; 52! theoretical deck orders, sampled by the generator |
| Minesweeper | 1 | 4 | 4 presets; theoretical layout counts below, actual coverage not measured |
| Checkers | 1 | 4 | **1 standard opening**, 4 computer strengths, varying match continuations |
| Reversi | 1 | 4 | **1 standard opening**, 4 computer strengths, varying match continuations |
| Cryptogram | 1 | 4 | Public-domain sayings in fresh random codes; sayings repeat eventually |
| Word scramble | 1 | 4 | Rounds of 8 words from the bundled vocabulary |
| Acrostic | 1 | 4 | Hidden words and clues from the bundled vocabulary |
| Code cracker | 1 | 4 | Grids packed from the bundled vocabulary with a fresh number code |
| Dropquote | 1 | 4 | Public-domain sayings; the same sayings as Cryptogram, laid out in columns |
| Spider Solitaire | 3 | 3 | 1, 2 or 4 suits; shuffled two-deck deals, not certified winnable |
| Pyramid Solitaire | 1 | 4 | 52! theoretical deck orders, sampled; not every deal clears |
| Memory | 1 | 4 | 6–15 pairs of Mahjong pictures, shuffled per game |
| Dots and Boxes | 1 | 4 | 1 empty board per size; matches differ by play |
| Sprouts | 1 | 4 | 2–5 starting spots placed per game |
| Magnetic cluster | 1 | 4 | 1 empty ring; matches differ by play |
| 2048 | 1 | 4 | Seeded spawns; no final level |
| Tetras | 1 | 4 | Seeded 7-piece bags; no final level |
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
