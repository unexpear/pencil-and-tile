package com.simplegamegen.sudoku.tabletop

import com.simplegamegen.sudoku.words.Hangman
import com.simplegamegen.sudoku.words.WordDifficulty
import com.simplegamegen.sudoku.words.WordPuzzles

data class GameCapacity(val title: String, val variants: Int, val setups: Int, val description: String, val unique: String)
object CollectionGuide {
    val hangmanWords: Int get() = WordPuzzles.themes.indices.flatMap { theme -> WordDifficulty.entries.flatMap { Hangman.words(theme, it) } }.distinct().size
    val entries: List<GameCapacity> get() = listOf(
        GameCapacity("Sudoku", 8, 104, "8 rule variants. Seven support 4×4, 6×6 and 9×9 × 4 levels; Classic, X, Jigsaw and Killer also come as 16×16. Plus custom grids from 4×4 to 16×16.", "Exact generated-puzzle count not measured; no finite level list. Custom grids are unlimited."),
        GameCapacity("Samurai Sudoku", 1, 4, "5 overlapping 9×9 grids × 4 levels; each puzzle is proven to have one solution.", "Exact generated-puzzle count not measured; no finite level list."),
        GameCapacity("Calcudoku", 1, 4, "4×4 to 7×7 grids with arithmetic cages; one size per level.", "Cage layouts and operations vary per puzzle; exact unique-grid count not measured."),
        GameCapacity("Kakuro", 1, 4, "6×6 to 12×12 cross-sum boards; one size per level.", "Patterns and fills vary per puzzle; exact unique-board count not measured."),
        GameCapacity("Futoshiki", 1, 4, "4×4 to 7×7 grids with greater-than signs; one size per level.", "Sign layouts vary per puzzle; each is proven to have one solution."),
        GameCapacity("Nonograms", 1, 4, "5×5, 8×8, 10×10 and 12×12 pictures from row and column clues.", "Pictures are generated per game; each can be solved line by line, so it has one answer."),
        GameCapacity("Hitori", 1, 4, "5×5 to 8×8 number grids; one size per level.", "Numbers and shading vary per puzzle; each is proven to have one solution."),
        GameCapacity("Crossword", 1, 4, "4 difficulty levels; 120 original clues.", "Many connected layouts; exact unique-grid count not measured."),
        GameCapacity("Word search", 1, 48, "12 themes × 4 levels; 20 words per theme.", "Word selections, placements and filler vary; exact unique-grid count not measured."),
        GameCapacity("Hangman", 1, 48, "12 themes × 4 levels; 8/7/6/5 mistakes allowed.", "$hangmanWords distinct answers across eligible word pools. Theme/difficulty choices do not create new words."),
        GameCapacity("Cryptogram", 1, 4, "Public-domain sayings in a letter-swap code × 4 levels.", "${com.simplegamegen.sudoku.wordplay.QuotesInfo.count} sayings; each new puzzle uses a fresh random code."),
        GameCapacity("Word scramble", 1, 4, "Rounds of 8 jumbled words; word length grows by level.", "Words come from the bundled vocabulary; any real anagram counts."),
        GameCapacity("Acrostic", 1, 4, "Clue answers whose first letters spell a hidden word × 4 levels.", "Hidden words and clues come from the bundled vocabulary."),
        GameCapacity("Code cracker", 1, 4, "9×9 to 13×13 clue-free grids where numbers stand for letters × 4 levels.", "Grids are packed from the bundled vocabulary with a fresh number code each game."),
        GameCapacity("Dropquote", 1, 4, "Public-domain sayings whose letters drop into columns × 4 levels.", "${com.simplegamegen.sudoku.wordplay.QuotesInfo.count} sayings; longer sayings and wider grids at higher levels."),
        GameCapacity("Common Threads", 1, 4, "Sixteen words in four hidden groups × 4 levels; categories, compound words, hidden words and anagrams.", "Built from rules each game and checked to have exactly one way to sort the words; decoys grow with the level."),
        GameCapacity("Word Meaning", 1, 4, "Explain eight words in your own words × 4 levels, from everyday to rare.", "${com.simplegamegen.sudoku.wordplay.MeaningBank.entries.size} words with sentences; answers judged on the device."),
        GameCapacity("Five Letters", 1, 4, "Guess a five-letter word × 4 levels (7 to 5 guesses, strict reuse on Hard and Expert).", "${com.simplegamegen.sudoku.wordplay.FiveWords.answers.size} answers and ${com.simplegamegen.sudoku.wordplay.FiveWords.valid.size} accepted guesses."),
        GameCapacity("Blotwords", 1, 5, "Ink a letter grid with invented command words: a 12-step Discover trail, then 4 levels adding VUM, DRIF, ZUV and KEL.", "Built backwards from a fully inked grid each game, so every puzzle can be finished; the hint follows a known solution."),
        GameCapacity("Mahjong Solitaire", 1, 4, "4 difficulty/layout presets; 24/48/72/96 tiles.", "Verified clearable deals with shuffled tile faces. Exact unique-deal count not measured."),
        GameCapacity("Solitaire", 2, 2, "Klondike Draw 1 and Draw 3; unlimited redeals.", "52! ≈ 8.07 × 10⁶⁷ theoretical deck orders; our seeded generator samples a subset, not all orders. Winnability is not guaranteed."),
        GameCapacity("Spider Solitaire", 3, 3, "Two decks with 1, 2 or 4 suits; 10 columns and 5 deals.", "Shuffled two-deck deals; winnability is not guaranteed."),
        GameCapacity("Pyramid Solitaire", 1, 4, "Pairs totaling 13; unlimited, 3, 2 or 1 pass through the stock.", "52! theoretical deck orders, sampled by the generator; not every deal can be cleared."),
        GameCapacity("Memory", 1, 4, "3×4, 4×4, 4×5 and 5×6 boards of Mahjong pictures.", "Pictures and layouts are shuffled per game."),
        GameCapacity("Minesweeper", 1, 4, "8×8/8, 9×9/10, 16×16/40 and 30×16/99 mines.", "For a fixed first click excluding k safe squares: C(cells − k, mines) theoretical layouts. Exact generator coverage not measured."),
        GameCapacity("Checkers", 1, 4, "English/American rules × 4 computer strengths.", "1 standard starting board. Different move sequences and opponent choices create different matches."),
        GameCapacity("Reversi", 1, 4, "Standard 8×8 rules × 4 computer strengths.", "1 standard starting board. Different move sequences and opponent choices create different matches."),
        GameCapacity("Dominoes", 1, 4, "Double-six Draw, 1 hand × 4 computer strengths.", "C(28,7) × C(21,7) theoretical ordered-player opening hands; boneyard order adds variation. Exact generator coverage not measured."),
        GameCapacity("Dots and Boxes", 1, 4, "3×3 to 6×6 boxes; board size and computer strength rise together.", "1 empty starting board; matches differ by play."),
        GameCapacity("Sprouts", 1, 4, "2 to 5 starting spots against the computer.", "Starting spots are placed per game; curve routes differ by play."),
        GameCapacity("Magnetic cluster", 1, 4, "8 magnetic stones each × 4 computer strengths.", "1 empty ring; matches differ by play."),
        GameCapacity("2048", 1, 4, "3×3 to 6×6 boards with goal tiles 512 to 8192.", "Tile spawns are seeded per game; there is no final level."),
        GameCapacity("Tetras", 1, 4, "Falling blocks with 4 starting speeds; 7-piece bags.", "Piece order is seeded per game; there is no final level."),
    )
}
