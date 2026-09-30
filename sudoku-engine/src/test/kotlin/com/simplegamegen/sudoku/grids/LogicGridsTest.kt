package com.simplegamegen.sudoku.grids

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LogicGridsTest {
    @Test fun `bridges puzzles are unique and linked`() {
        for (setting in BridgesGame.SIZES.indices) {
            val game = BridgesGame.generate(setting + 2L, setting)
            assertTrue(!game.complete)
            val solved = game.copy(built = game.solution)
            assertTrue(solved.complete)
            assertEquals(solved, BridgesCodec.decode(BridgesCodec.encode(solved)))
            assertNotNull(game.hint())
        }
    }

    @Test fun `a grown slitherlink is one loop`() {
        val loop = SlitherlinkRules.grow(5, kotlin.random.Random(1))
        assertTrue(SlitherlinkRules.loop(5, loop.first, loop.second), "${loop.first} ${loop.second}")
    }

    @Test fun `slitherlink puzzles are one loop`() {
        for (setting in SlitherlinkGame.SIZES.indices) {
            val game = SlitherlinkGame.generate(setting + 3L, setting)
            assertTrue(SlitherlinkRules.loop(game.size, game.solutionH, game.solutionV))
            val solved = game.copy(markH = game.solutionH, markV = game.solutionV)
            assertTrue(solved.complete)
            assertEquals(solved, SlitherlinkCodec.decode(SlitherlinkCodec.encode(solved)))
        }
    }

    @Test fun `towers puzzles show the right skylines`() {
        for (setting in TowersGame.SIZES.indices) {
            val game = TowersGame.generate(setting + 4L, setting)
            val n = game.size
            for (row in 0 until n) {
                val line = (0 until n).map { game.solution[row * n + it] }
                if (game.left[row] > 0) assertEquals(game.left[row], TowersRules.seen(line))
                if (game.right[row] > 0) assertEquals(game.right[row], TowersRules.seen(line.asReversed()))
            }
            assertTrue(game.copy(entered = game.solution).complete)
            assertEquals(game, TowersCodec.decode(TowersCodec.encode(game)))
        }
    }

    @Test fun `lights puzzles light every white square exactly once as a solution`() {
        for (setting in LightsGame.SIZES.indices) {
            val game = LightsGame.generate(setting + 5L, setting)
            assertTrue(LightsRules.valid(game.size, game.wall, game.solution))
            assertTrue(game.copy(lamps = game.solution).complete)
            assertEquals(game, LightsCodec.decode(LightsCodec.encode(game)))
        }
    }
}
