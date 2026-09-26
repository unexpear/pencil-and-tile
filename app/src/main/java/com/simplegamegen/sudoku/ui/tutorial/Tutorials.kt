package com.simplegamegen.sudoku.ui.tutorial

import com.simplegamegen.sudoku.ui.GameId

/** Every game's own tutorial. */
object Tutorials {
    fun of(id: GameId): Tutorial = when (id) {
        GameId.SUDOKU -> NumberTutorials.sudoku
        GameId.KILLER -> NumberTutorials.killer
        GameId.SAMURAI -> NumberTutorials.samurai
        GameId.KENKEN -> NumberTutorials.kenken
        GameId.KAKURO -> NumberTutorials.kakuro
        GameId.FUTOSHIKI -> NumberTutorials.futoshiki
        GameId.NONOGRAM -> NumberTutorials.nonogram
        GameId.HITORI -> NumberTutorials.hitori
        GameId.CROSSWORD -> WordTutorials.crossword
        GameId.WORD_SEARCH -> WordTutorials.wordSearch
        GameId.HANGMAN -> WordTutorials.hangman
        GameId.CRYPTOGRAM -> WordTutorials.cryptogram
        GameId.SCRAMBLE -> WordTutorials.scramble
        GameId.ACROSTIC -> WordTutorials.acrostic
        GameId.CODE_CRACKER -> WordTutorials.codeCracker
        GameId.DROPQUOTE -> WordTutorials.dropquote
        GameId.THREADS -> WordTutorials.threads
        GameId.FIVE_LETTERS -> WordTutorials.fiveLetters
        GameId.WORD_MEANING -> WordTutorials.wordMeaning
        GameId.BLOTWORDS -> WordTutorials.blotwords
        GameId.LETTER_SPRAWL -> WordTutorials.sprawl
        GameId.WORDSWORN -> WordTutorials.wordsworn
        GameId.WORD_QUILT -> WordTutorials.quilt
        GameId.LONE_LETTER -> WordTutorials.loneLetter
        GameId.MAHJONG -> TableTutorials.mahjong
        GameId.SOLITAIRE -> TableTutorials.solitaire
        GameId.SPIDER -> TableTutorials.spider
        GameId.PYRAMID -> TableTutorials.pyramid
        GameId.DOMINOES -> TableTutorials.dominoes
        GameId.MEMORY -> TableTutorials.memory
        GameId.MINES -> BoardTutorials.mines
        GameId.CHECKERS -> BoardTutorials.checkers
        GameId.REVERSI -> BoardTutorials.reversi
        GameId.DOTS -> BoardTutorials.dots
        GameId.SPROUTS -> BoardTutorials.sprouts
        GameId.MAGNETS -> BoardTutorials.magnets
        GameId.G2048 -> ArcadeTutorials.g2048
        GameId.TETRAS -> ArcadeTutorials.tetras
    }
}
