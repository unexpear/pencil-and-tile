package com.simplegamegen.sudoku.ui

import com.simplegamegen.sudoku.duels.DotsGame
import com.simplegamegen.sudoku.ui.screens.dotsEdgeBetween
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DotsInputTest {
    @Test fun `two dots select every legal edge in either direction`() {
        DotsGame.SIZES.indices.forEach { setting ->
            val game = DotsGame.start(1, setting)
            val width = game.cols + 1
            for (r in 0..game.rows) for (c in 0 until game.cols) {
                val a = r * width + c
                assertEquals(game.h(r, c), dotsEdgeBetween(game, a, a + 1))
                assertEquals(game.h(r, c), dotsEdgeBetween(game, a + 1, a))
            }
            for (r in 0 until game.rows) for (c in 0..game.cols) {
                val a = r * width + c
                assertEquals(game.v(r, c), dotsEdgeBetween(game, a, a + width))
                assertEquals(game.v(r, c), dotsEdgeBetween(game, a + width, a))
            }
        }
    }

    @Test fun `same diagonal distant wrapped and claimed pairs cannot draw`() {
        val game = DotsGame.start(1, 0)
        listOf(0 to 0, 0 to 5, 0 to 2, 3 to 4, -1 to 0, 15 to 16).forEach { (a, b) ->
            assertNull(dotsEdgeBetween(game, a, b), "$a to $b")
        }
        assertNull(dotsEdgeBetween(game.draw(game.h(0, 0))!!, 0, 1))
    }
}
