package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.arcade.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ArcadeGamesTest {
    @Test fun `2048 merges once per pair, scores merges and spawns reproducibly`() {
        val g = Game2048(1, 1, listOf(2, 2, 2, 2, 4, 4, 8, 0, 0, 0, 0, 0, 2, 0, 2, 4))
        val left = g.slide(Slide.LEFT)!!
        assertEquals(listOf(4, 4), left.tiles.subList(0, 2))
        assertEquals(listOf(8, 8), left.tiles.subList(4, 6))
        assertEquals(4 + 4 + 8 + 4, left.score)
        assertEquals(1, left.spawns)
        assertEquals(left, g.slide(Slide.LEFT), "same seed, same spawn")
        assertNull(Game2048(1, 1, listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)).slide(Slide.LEFT))
        assertTrue(Game2048(1, 1, listOf(2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2)).over)
        for (s in Game2048.SIZES.indices) assertEquals(2, Game2048.start(5, s).tiles.count { it != 0 })
    }

    @Test fun `2048 reaches its goal and plays until stuck`() {
        val almost = Game2048(0, 3, listOf(256, 256, 0, 0, 0, 0, 0, 0, 0))
        assertTrue(almost.slide(Slide.LEFT)!!.reached)
        var g = Game2048.start(11, 1)
        var turns = 0
        while (!g.over && turns < 5000) {
            g = Slide.entries.firstNotNullOfOrNull { g.slide(it) } ?: break
            turns++
        }
        assertTrue(g.over)
        assertEquals(g, Game2048Codec.decode(Game2048Codec.encode(g)))
        assertNull(Game2048Codec.decode(Game2048Codec.encode(g).replace(",2,", ",3,")))
    }

    @Test fun `every tetras shape has four cells in each rotation and bags hold each piece once`() {
        Piece.SHAPES.forEach { rotations -> rotations.forEach { assertEquals(4, it.size) } }
        for (bag in 0 until 5) assertEquals((0..6).toSet(), (0 until 7).map { TetrasGame.typeAt(9, bag * 7 + it) }.toSet())
        val g = TetrasGame.start(9, 0)
        assertEquals(TetrasGame.typeAt(9, 0), g.piece!!.type)
        assertEquals(3, g.next.size)
    }

    @Test fun `pieces move, rotate with wall kicks and stop at walls`() {
        var g = TetrasGame.start(4, 0)
        repeat(12) { g = g.shift(-1) ?: g }
        assertTrue(g.piece!!.cells().any { it.first == 0 })
        assertNull(g.shift(-1))
        val rotated = g.rotate()
        if (g.piece!!.type != 1) assertNotNull(rotated, "rotation against the wall kicks instead of failing")
    }

    @Test fun `clearing lines scores by count and level`() {
        // Fill the bottom four rows except the first column, then drop an upright I into it.
        val board = List(TetrasGame.W * TetrasGame.H) { i -> if (i / TetrasGame.W >= 16 && i % TetrasGame.W != 0) 2 else 0 }
        val game = TetrasGame(0, 1, board, Piece(0, 1, -2, 0))
        val dropped = game.hardDrop()!!
        assertEquals(4, dropped.lines)
        assertTrue(dropped.board.subList(16 * 10, 200).all { it == 0 }, "the four full rows are gone")
        assertEquals(800 + 2 * 16, dropped.score)
        val fast = TetrasGame.start(1, 3)
        assertEquals(9, fast.level); assertTrue(fast.interval < TetrasGame.start(1, 0).interval)
    }

    @Test fun `stacking to the top ends the game and saves round-trip`() {
        var g = TetrasGame.start(7, 2)
        var steps = 0
        while (!g.over && steps < 500) { g = g.hardDrop() ?: g.tick(); steps++ }
        assertTrue(g.over)
        assertNull(g.hardDrop()); assertSame(g, g.tick())
        assertEquals(g, TetrasCodec.decode(TetrasCodec.encode(g)))
        val mid = TetrasGame.start(7, 1).tick().shift(1)!!.rotate()!!
        assertEquals(mid, TetrasCodec.decode(TetrasCodec.encode(mid)))
        assertNull(TetrasCodec.decode(TetrasCodec.encode(mid).replace("\n1\n", "\n9\n")))
    }
}
