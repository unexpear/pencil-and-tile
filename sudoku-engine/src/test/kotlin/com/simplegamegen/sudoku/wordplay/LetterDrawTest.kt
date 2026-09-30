package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LetterDrawTest {
    @Test fun `every level deals a rack that can reach the target`() {
        for (level in LogicLevel.entries) {
            val game = LetterDraw.generate(level.ordinal + 5L, level)
            assertEquals(game, LetterDrawCodec.decode(LetterDrawCodec.encode(game)))
            assertTrue(game.best.length >= game.target)
            assertTrue(LetterDraw.fits(game.letters, game.best))
            assertEquals(LetterDraw.secondsOf(level), game.limit)
        }
    }

    @Test fun `a long enough word wins and the clock can lose`() {
        val game = LetterDraw.generate(1, LogicLevel.EASY)
        var typed = game
        for (letter in game.best) typed = typed.type(letter)!!
        if (typed.spare(game.best[0]) <= 0) assertNull(typed.type(game.best[0]))
        val won = typed.submit()!!
        assertTrue(won.won && won.answer == game.best)
        assertEquals("The game is over.", won.problem())

        val quiet = game.tick(game.limit)
        assertTrue(quiet.lost)
        assertEquals(quiet, quiet.tick(1))
    }
}
