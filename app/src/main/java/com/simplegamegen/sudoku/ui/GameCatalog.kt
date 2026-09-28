package com.simplegamegen.sudoku.ui

import com.simplegamegen.sudoku.logic.LogicKind
import com.simplegamegen.sudoku.tabletop.TableGame

enum class GameGroup(val title: String) {
    NUMBERS("Number puzzles"), WORDS("Word games"), TABLE("Cards and tiles"), BOARD("Board and strategy"), ARCADE("Arcade")
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
    SOLITAIRE("Solitaire", "table_SOLITAIRE", "Klondike, Spider, Pyramid", GameGroup.TABLE),
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
    TETRAS("Tetras", "play_TETRAS", "Falling blocks", GameGroup.ARCADE);

    /** Variants are reached inside their family rather than duplicated on the home screen. */
    val parent: GameId? get() = when (this) {
        KILLER, SAMURAI -> SUDOKU
        SPIDER, PYRAMID -> SOLITAIRE
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
