package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlidingBlocksTest {
    @Test
    fun `unsolvable packed board is rejected`() {
        val cars = packedJam()
        assertFalse(SlidingBlocksRules.won(SlidingBlocks.SIZE, cars[0]))
        assertTrue(SlidingBlocksRules.allMoves(SlidingBlocks.SIZE, cars).isEmpty())
        assertEquals(-1, SlidingBlocksRules.distance(SlidingBlocks.SIZE, cars[0].row, cars))
        assertNull(SlidingBlocks.accept(0, 1L, SlidingBlocks.SIZE, cars))
    }

    @Test
    fun `generated boards are solvable and length matches BFS`() {
        val lengths = mutableListOf<Int>()
        for (level in LogicLevel.entries) {
            val game = SlidingBlocks.generate(level.ordinal + 11L, level)
            assertFalse(game.complete)
            lengths += game.solutionLength
            val bfs = SlidingBlocksRules.distance(game.size, game.exitRow, game.cars)
            assertEquals(game.solutionLength, bfs, "level $level")
            assertTrue(game.solutionLength >= 2, "level $level length ${game.solutionLength}")
            var cursor: SlidingBlocks? = game
            repeat(bfs) {
                cursor = cursor!!.hint()
                assertNotNull(cursor)
            }
            assertTrue(cursor!!.complete)
        }
        assertTrue(lengths.zipWithNext().all { (a, b) -> a <= b }, "lengths $lengths")
    }

    @Test
    fun `codec round trips`() {
        val game = SlidingBlocks.generate(42L, LogicLevel.MEDIUM)
        val moved = game.hint()!!
        val text = SlidingBlocksCodec.encode(moved)
        assertEquals(moved, SlidingBlocksCodec.decode(text))
        assertNull(SlidingBlocksCodec.decode("bogus"))
    }

    @Test
    fun `a clear path wins in one slide`() {
        val cars = listOf(
            BlockCar(0, 2, 0, 2, true),
            BlockCar(1, 0, 0, 2, true),
            BlockCar(2, 4, 3, 3, true),
        )
        val game = SlidingBlocks.accept(0, 7L, 6, cars)!!
        assertEquals(1, game.solutionLength)
        val won = game.slide(0, 4)!!
        assertTrue(won.complete)
        val one = game.slideBy(0, 1, 1)!!
        assertFalse(one.complete)
        assertEquals(1, one.target.col)
    }

    /** Every cell filled with length-3 horizontals; target not at the exit. */
    private fun packedJam(): List<BlockCar> {
        val cars = ArrayList<BlockCar>()
        var id = 0
        for (row in 0 until SlidingBlocks.SIZE) {
            cars += BlockCar(id++, row, 0, 3, true)
            cars += BlockCar(id++, row, 3, 3, true)
        }
        return cars
    }
}
