package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LetterfallTest {
    private fun board(
        letters: String,
        cols: Int = 3,
        level: LogicLevel = LogicLevel.EASY,
        score: Int = 0,
        movesLeft: Int = 8,
        combo: Int = 1,
        fresh: Set<Int> = emptySet(),
    ) = Letterfall(level, 1, letters.length / cols, cols, letters, score = score, movesLeft = movesLeft, combo = combo, fresh = fresh)

    @Test fun `paths must be orthogonal, in bounds, and free of repeats`() {
        val cols = 3
        assertTrue(Letterfall.connected(listOf(0, 1, 2), 3, cols))
        assertTrue(Letterfall.connected(listOf(0, 3, 6), 3, cols))
        assertFalse(Letterfall.connected(listOf(0, 4), 3, cols), "diagonal")
        assertFalse(Letterfall.connected(listOf(2, 3), 3, cols), "row does not wrap")
        assertFalse(Letterfall.connected(listOf(0, 1, 0), 3, cols), "repeat")
        assertFalse(Letterfall.connected(listOf(0, 2), 3, cols), "gap")
        assertTrue(2 !in Letterfall.neighbours(3, 3, cols))
    }

    @Test fun `real words are accepted and everything else is rejected`() {
        val g = board("CATXXXXXX")
        assertEquals("CAT", g.wordOf(listOf(0, 1, 2)))
        assertNull(g.problem(listOf(0, 1, 2)))
        assertNotNull(g.play(listOf(0, 1, 2)))
        assertEquals("Words need at least 3 letters.", g.problem(listOf(0, 1)))
        assertEquals("Tiles must touch by a side, and each tile is used once.", g.problem(listOf(0, 4, 8)))
        assertEquals("Not in the word list.", g.problem(listOf(6, 7, 8)))
        assertNull(g.play(listOf(0, 4, 2)))
        assertNull(g.play(listOf(6, 7, 8)))
    }

    @Test fun `cleared tiles fall and new letters refill from the top`() {
        // A B C / X X X / G H I, with the centre X cleared.
        val grid = "ABCXXXGHI".toCharArray()
        grid[4] = Letterfall.HOLE
        val settled = Letterfall.settle(grid, 3, 3, seed = 7, draw = 0)
        val dealt = Letterfall.tile(7, 0)
        assertEquals(1, settled.drawn)
        assertEquals("A${dealt}C", settled.letters.substring(0, 3))
        assertEquals("XBX", settled.letters.substring(3, 6))
        assertEquals("GHI", settled.letters.substring(6, 9))
        assertEquals(setOf(1, 4), settled.fresh)
        assertFalse(7 in settled.fresh, "H stayed put")
    }

    @Test fun `a word that uses a fallen tile raises the combo and a settled word resets it`() {
        // CAT / XXX / DOG. Clearing DOG drops CAT onto the middle row.
        val start = board("CATXXXDOG")
        val first = start.play(listOf(6, 7, 8))!!
        assertEquals("DOG", first.lastWord)
        assertEquals(50, first.lastPoints)
        assertEquals(1, first.combo)
        assertEquals("CAT", first.letters.substring(3, 6))
        assertTrue(setOf(3, 4, 5).all { it in first.fresh })

        val second = first.play(listOf(3, 4, 5))!!
        assertEquals("CAT", second.lastWord)
        assertEquals(Letterfall.pointsFor("CAT", 2), second.lastPoints)
        assertEquals(100, second.lastPoints)
        assertEquals(2, second.combo)
        assertEquals(150, second.score)

        val rich = board("CATXXXXXX", combo = 3, fresh = setOf(3))
        val reset = rich.play(listOf(0, 1, 2))!!
        assertEquals(1, reset.combo)
        assertEquals(50, reset.lastPoints)

        val capped = board("CATXXXXXX", combo = 4, fresh = setOf(0))
        val again = capped.play(listOf(0, 1, 2))!!
        assertEquals(4, again.combo)
        assertEquals(200, again.lastPoints)
    }

    @Test fun `length and rare letters set the score`() {
        assertEquals(50, Letterfall.pointsFor("CAT", 1))
        assertEquals(100, Letterfall.pointsFor("CAT", 2))
        assertEquals(100 + 40 * 3, Letterfall.pointsFor("JAZZ", 1))
        assertEquals(50 + 15 + 15, Letterfall.pointsFor("SKY", 1))
        assertEquals(360 + 120, Letterfall.lengthPoints(8))
    }

    @Test fun `hitting the target wins and running out of moves loses`() {
        val target = Letterfall.targetOf(LogicLevel.EASY)
        val win = board("CATXXXXXX", score = target - 50, movesLeft = 1).play(listOf(0, 1, 2))!!
        assertTrue(win.won)
        assertFalse(win.lost)
        assertEquals(target, win.score)
        assertEquals("The game is over.", win.problem(listOf(0, 1, 2)))
        assertNull(win.play(listOf(3, 4, 5)))

        val lose = board("CATXXXXXX", score = 0, movesLeft = 1).play(listOf(0, 1, 2))!!
        assertTrue(lose.lost)
        assertFalse(lose.won)
        assertEquals(0, lose.movesLeft)
        assertEquals(50, lose.score)
        assertNull(lose.play(listOf(0, 1, 2)))
    }

    @Test fun `codec round trip keeps a mid-game board`() {
        val g = board("CATXXXDOG", score = 50, movesLeft = 6, combo = 2, fresh = setOf(1, 4)).play(listOf(6, 7, 8))!!
        assertEquals(g, LetterfallCodec.decode(LetterfallCodec.encode(g)))
        assertNull(LetterfallCodec.decode("nope"))
        assertNull(LetterfallCodec.decode(LetterfallCodec.encode(g).replace("CAT", "cat")))
    }

    @Test fun `generated boards are full, seeded, and hold a word`() {
        for (level in LogicLevel.entries) {
            val g = Letterfall.generate(level.ordinal * 19L + 4, level)
            assertEquals(7, g.rows)
            assertEquals(7, g.cols)
            assertEquals(Letterfall.movesOf(level), g.movesLeft)
            assertEquals(Letterfall.targetOf(level), g.target)
            assertTrue(Letterfall.hasWord(g.letters, g.rows, g.cols))
            assertEquals(g.letters, Letterfall.generate(level.ordinal * 19L + 4, level).letters)
            assertFalse(g.over)
        }
    }
}
