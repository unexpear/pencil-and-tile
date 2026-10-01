package com.simplegamegen.sudoku.ui

import com.simplegamegen.sudoku.logic.LogicKind
import com.simplegamegen.sudoku.tabletop.TableGame

enum class GameGroup(val title: String) {
    NUMBERS("Number puzzles"), WORDS("Word games"), TABLE("Cards and tiles"), BOARD("Board and strategy"),
    /** Paper-and-pencil games. */
    CLASSICS("Classics"), ARCADE("Arcade")
}

/** Home-screen games in display order; the order also indexes each theme's game colors. */
enum class GameId(val title: String, val route: String, val blurb: String, val group: GameGroup) {
    SUDOKU("Sudoku", "sudoku", "8 variants, 4 sizes", GameGroup.NUMBERS),
    KILLER("Killer Sudoku", "sudoku?variant=KILLER", "Cages that add up", GameGroup.NUMBERS),
    SAMURAI("Samurai Sudoku", "logic_SAMURAI", "5 linked grids", GameGroup.NUMBERS),
    KENKEN("Calcudoku", "logic_KENKEN", "Arithmetic cages", GameGroup.NUMBERS),
    KAKURO("Kakuro", "logic_KAKURO", "Cross sums", GameGroup.NUMBERS),
    FUTOSHIKI("Futoshiki", "logic_FUTOSHIKI", "Greater or less", GameGroup.NUMBERS),
    NONOGRAM("Nonograms", "play_NONOGRAM", "Paint the picture", GameGroup.NUMBERS),
    HITORI("Hitori", "play_HITORI", "Shade the repeats", GameGroup.NUMBERS),
    CROSSWORD("Crossword", "crossword", "Original clues", GameGroup.WORDS),
    WORD_SEARCH("Word search", "wordsearch", "12 themes", GameGroup.WORDS),
    HANGMAN("Hangman", "hangman", "Guess the word", GameGroup.WORDS),
    CRYPTOGRAM("Cryptogram", "play_CRYPTOGRAM", "Crack the code", GameGroup.WORDS),
    SCRAMBLE("Word scramble", "play_SCRAMBLE", "Unjumble words", GameGroup.WORDS),
    ACROSTIC("Acrostic", "play_ACROSTIC", "Hidden word clues", GameGroup.WORDS),
    CODE_CRACKER("Code cracker", "play_CODE_CRACKER", "Numbers into letters", GameGroup.WORDS),
    DROPQUOTE("Dropquote", "play_DROPQUOTE", "Drop the letters", GameGroup.WORDS),
    THREADS("Common Threads", "play_THREADS", "Four groups of four", GameGroup.WORDS),
    FIVE_LETTERS("Five Letters", "play_FIVE_LETTERS", "Guess the hidden word", GameGroup.WORDS),
    WORD_MEANING("Word Meaning", "play_WORD_MEANING", "Say what it means", GameGroup.WORDS),
    BLOTWORDS("Blotwords", "play_BLOTWORDS", "Ink the grid with words", GameGroup.WORDS),
    LETTER_SPRAWL("Letter Sprawl", "play_LETTER_SPRAWL", "Chain letters into words", GameGroup.WORDS),
    WORDSWORN("Wordsworn", "play_WORDSWORN", "Battle monsters with words", GameGroup.WORDS),
    WORD_QUILT("Word Quilt", "play_WORD_QUILT", "Stitch letters into words", GameGroup.WORDS),
    LONE_LETTER("Lone Letter", "play_LONE_LETTER", "One letter, many categories", GameGroup.WORDS),
    MAHJONG("Mahjong", "mahjong", "Match free tiles", GameGroup.TABLE),
    SOLITAIRE("Solitaire", "table_SOLITAIRE", "Klondike, Spider, Pyramid, FreeCell", GameGroup.TABLE),
    SPIDER("Spider", "play_SPIDER", "1, 2 or 4 suits", GameGroup.TABLE),
    PYRAMID("Pyramid", "play_PYRAMID", "Pairs that make 13", GameGroup.TABLE),
    DOMINOES("Dominoes", "table_DOMINOES", "Double-six draw", GameGroup.TABLE),
    MEMORY("Memory", "play_MEMORY", "Find the pairs", GameGroup.TABLE),
    MINES("Minesweeper", "table_MINES", "Clear the field", GameGroup.BOARD),
    CHECKERS("Checkers", "table_CHECKERS", "English draughts", GameGroup.BOARD),
    REVERSI("Reversi", "table_REVERSI", "Flip to win", GameGroup.BOARD),
    DOTS("Dots and Boxes", "play_DOTS", "Close the boxes", GameGroup.BOARD),
    SPROUTS("Sprouts", "play_SPROUTS", "Grow the last line", GameGroup.BOARD),
    MAGNETS("Magnetic cluster", "play_MAGNETS", "Don't let them snap", GameGroup.BOARD),
    G2048("2048", "play_G2048", "Slide and merge", GameGroup.ARCADE),
    TETRAS("Tetras", "play_TETRAS", "Falling blocks", GameGroup.ARCADE),
    CONNECT_FOUR("Connect Four", "play_CONNECT_FOUR", "Four in a row", GameGroup.CLASSICS),
    MASTERMIND("Mastermind", "play_MASTERMIND", "Break the code", GameGroup.CLASSICS),
    BATTLESHIP("Battleship", "play_BATTLESHIP", "Sink the fleet", GameGroup.CLASSICS),
    LETTERFALL("Letterfall", "play_LETTERFALL", "Spell words and let tiles fall", GameGroup.WORDS),
    MANCALA("Mancala", "play_MANCALA", "Sow and capture", GameGroup.CLASSICS),
    FIVE_ROW("Five in a row", "play_FIVE_ROW", "Get five in a line", GameGroup.CLASSICS),
    WORD_LADDER("Word ladder", "play_WORD_LADDER", "Change one letter", GameGroup.WORDS),
    HONEYCOMB("Honeycomb", "play_HONEYCOMB", "Seven letters, one required", GameGroup.WORDS),
    LETTER_DRAW("Letter Draw", "play_LETTER_DRAW", "Longest word on the clock", GameGroup.WORDS),
    BRIDGES("Bridges", "play_BRIDGES", "Link the islands", GameGroup.NUMBERS),
    SLITHERLINK("Slitherlink", "play_SLITHERLINK", "One loop on the lines", GameGroup.NUMBERS),
    TOWERS("Towers", "play_TOWERS", "See the skyline", GameGroup.NUMBERS),
    LIGHTS("Lights", "play_LIGHTS", "Light every square", GameGroup.NUMBERS),
    CHESS("Chess", "play_CHESS", "White vs computer", GameGroup.BOARD),
    FREECELL("FreeCell", "play_FREECELL", "Four free cells", GameGroup.TABLE),
    SLIDING_BLOCKS("Sliding blocks", "play_SLIDING_BLOCKS", "Slide one car out", GameGroup.NUMBERS),
    GO("Go", "play_GO", "Surround and capture", GameGroup.BOARD),
    KLOTSKI("Klotski", "play_KLOTSKI", "Slide the big block out", GameGroup.NUMBERS),
    YACHT("Yacht", "play_YACHT", "Five dice, thirteen boxes", GameGroup.CLASSICS),
    SHUT_BOX("Shut the Box", "play_SHUT_BOX", "Flip tiles that add up", GameGroup.CLASSICS),
    TEN_THOUSAND("Ten Thousand", "play_TEN_THOUSAND", "Six dice to 10,000", GameGroup.CLASSICS),
    SHIP_CREW("Ship, Captain, Crew", "play_SHIP_CREW", "Six, five, four, then cargo", GameGroup.CLASSICS);

    /** Variants are reached inside their family rather than duplicated on the home screen. */
    val parent: GameId? get() = when (this) {
        KILLER, SAMURAI -> SUDOKU
        SPIDER, PYRAMID, FREECELL -> SOLITAIRE
        else -> null
    }

    /** Related games in display order. */
    val variants: List<GameId> get() = entries.filter { it.parent == this }

    companion object {
        fun of(game: TableGame): GameId = when (game) {
            TableGame.SOLITAIRE -> SOLITAIRE
            TableGame.MINES -> MINES
            TableGame.CHECKERS -> CHECKERS
            TableGame.REVERSI -> REVERSI
            TableGame.DOMINOES -> DOMINOES
        }
        fun of(kind: LogicKind): GameId = when (kind) {
            LogicKind.SAMURAI -> SAMURAI
            LogicKind.KENKEN -> KENKEN
            LogicKind.KAKURO -> KAKURO
            LogicKind.FUTOSHIKI -> FUTOSHIKI
        }
    }
}
