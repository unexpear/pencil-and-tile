package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.Hex
import com.simplegamegen.sudoku.duels.HexAi
import com.simplegamegen.sudoku.duels.HexCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class HexTest {
    private fun at(row: Int, col: Int, size: Int = 7) = row * size + col

    /** 7×7 Easy, the smallest board. */
    private fun played(vararg moves: Int, setting: Int = 8): Hex {
        var game = Hex.start(1, setting)
        for (move in moves) game = checkNotNull(game.place(move)) { "move $move" }
        return game
    }

    @Test fun `six neighbors touch and the square diagonals do not`() {
        val size = 7
        val mid = at(3, 3)
        val around = Hex.neighbors(size, mid).toSet()
        assertEquals(6, around.size)
        assertEquals(setOf(at(2, 3), at(2, 4), at(3, 2), at(3, 4), at(4, 2), at(4, 3)), around)
        assertFalse(at(2, 2) in around)
        assertFalse(at(4, 4) in around)
        assertEquals(2, Hex.neighbors(size, at(0, 0)).size)
        assertEquals(3, Hex.neighbors(size, at(0, size - 1)).size)
        assertEquals(4, Hex.neighbors(size, at(0, 3)).size)
        assertEquals(4, Hex.neighbors(size, at(3, 0)).size)
    }

    @Test fun `red joins top to bottom and blue joins left to right`() {
        val red = (0 until 6).flatMap { row -> listOf(at(row, 0), at(row, 2)) } + at(6, 0)
        // Red column 0, with blue answering in column 2. Red's last stone reaches the bottom.
        val down = played(*red.toIntArray())
        assertEquals(1, down.winner)
        assertTrue(down.over)
        assertEquals((0 until 7).map { at(it, 0) }, down.winningPath())
        assertTrue(down.winningPath().first() / 7 == 0 && down.winningPath().last() / 7 == 6)
        assertNull(down.place(at(6, 1)))

        val blueMoves = buildList {
            for (col in 0 until 6) {
                add(at(0, col))
                add(at(3, col))
            }
            add(at(0, 6))
        }
        val across = played(*blueMoves.toIntArray())
        assertEquals(-1, across.turn)
        val won = checkNotNull(across.place(at(3, 6)))
        assertEquals(-1, won.winner)
        assertEquals((0 until 7).map { at(3, it) }, won.winningPath())
        assertEquals(0, won.winningPath().first() % 7)
        assertEquals(6, won.winningPath().last() % 7)
    }

    @Test fun `edge cases do not award a chain that fails to meet both edges`() {
        val topOnly = played(*buildList {
            for (col in 0 until 6) {
                add(at(0, col))
                add(at(2, col))
            }
            add(at(0, 6))
        }.toIntArray())
        assertEquals(0, topOnly.winner)
        assertFalse(Hex.connects(topOnly.cells, 7, 1))
        assertFalse(Hex.connects(topOnly.cells, 7, -1))

        // A file of blue stones touches only the left edge. Red's answers stay in a corner.
        val leftOnly = played(*buildList {
            val fillers = listOf(at(6, 6), at(6, 5), at(5, 6), at(5, 5), at(4, 6), at(4, 5), at(3, 6))
            for (row in 0 until 6) {
                add(fillers[row])
                add(at(row, 0))
            }
            add(fillers[6])
        }.toIntArray())
        assertEquals(0, leftOnly.winner)
        assertFalse(Hex.connects(leftOnly.cells, 7, -1))
        assertFalse(Hex.connects(leftOnly.cells, 7, 1))

        // The main diagonal steps (r+1, c+1), which is not a hex neighbor, so it does not connect.
        val diagonal = played(*buildList {
            val blue = (1..6).map { at(0, it) }
            for (i in 0 until 6) {
                add(at(i, i))
                add(blue[i])
            }
            add(at(6, 6))
        }.toIntArray())
        assertEquals(0, diagonal.winner)
        assertFalse(Hex.connects(diagonal.cells, 7, 1))

        // Blue sits on the straight file, so red walks around it.
        val around = played(*buildList {
            val reds = listOf(at(0, 0), at(1, 0), at(2, 0), at(2, 1), at(3, 1), at(4, 0), at(5, 0), at(6, 0))
            val blues = listOf(at(3, 0), at(5, 5), at(5, 6), at(6, 5), at(6, 6), at(4, 6), at(4, 5))
            for (i in blues.indices) {
                add(reds[i])
                add(blues[i])
            }
            add(reds.last())
        }.toIntArray())
        assertEquals(1, around.winner)
        assertTrue(at(3, 0) !in around.winningPath())
        assertTrue(around.winningPath().zipWithNext().all { (a, b) -> b in Hex.neighbors(7, a) })

        // A corner stone is on two edges, and only the owner uses it.
        val corner = MutableList(49) { 0 }
        corner[at(0, 0)] = 1
        assertFalse(Hex.connects(corner, 7, 1))
        assertFalse(Hex.connects(corner, 7, -1))
    }

    @Test fun `a full board with no winner is impossible`() {
        val random = Random(11)
        for (size in intArrayOf(7, 9, 11)) {
            repeat(if (size == 11) 8 else 20) {
                val cells = List(size * size) { if (random.nextBoolean()) 1 else -1 }
                val red = Hex.connects(cells, size, 1)
                val blue = Hex.connects(cells, size, -1)
                assertTrue(red || blue, "size $size had no chain")
                assertNotEquals(red, blue, "size $size let both players connect")
            }
        }
        val checker = List(49) { if (it % 2 == 0) 1 else -1 }
        assertTrue(Hex.connects(checker, 7, 1) != Hex.connects(checker, 7, -1))
        assertThrows(IllegalArgumentException::class.java) {
            Hex(8, 1, checker, turn = -1, winner = 0)
        }
        var game = Hex.start(9, 8)
        val rng = Random(9)
        var guard = 0
        while (!game.over && guard++ < 80) {
            val move = game.legalMoves().random(rng)
            game = checkNotNull(game.apply(move))
        }
        assertTrue(game.over)
        assertNotEquals(0, game.winner)
        assertFalse(game.draw)
        assertEquals(game, HexCodec.decode(HexCodec.encode(game)))
    }

    @Test fun `swap mirrors the opening stone onto the second player's color`() {
        val opening = Hex.start(4, 8).place(at(1, 4))!!
        assertTrue(opening.canSwap)
        assertEquals(-1, opening.turn)
        assertTrue(Hex.SWAP in opening.legalMoves())
        val swapped = checkNotNull(opening.swap())
        assertEquals(0, swapped.cells[at(1, 4)])
        assertEquals(-1, swapped.cells[at(4, 1)])
        assertEquals(at(4, 1), swapped.last)
        assertEquals(1, swapped.turn)
        assertTrue(swapped.swapped)
        assertFalse(swapped.canSwap)
        assertNull(swapped.swap())
        assertEquals(swapped, HexCodec.decode(HexCodec.encode(swapped)))

        val diagonal = Hex.start(4, 8).place(at(2, 2))!!.swap()!!
        assertEquals(-1, diagonal.cells[at(2, 2)])
        assertEquals(1, diagonal.cells.count { it != 0 })
        assertEquals(1, diagonal.turn)

        assertNull(Hex.start(4, 8).swap())
        val answered = opening.place(at(0, 0))!!
        assertFalse(answered.canSwap)
        assertNull(answered.swap())
        val continued = swapped.place(at(0, 6))!!
        assertEquals(1, continued.cells[at(0, 6)])
        assertEquals(-1, continued.turn)
        assertFalse(continued.canSwap)
    }

    @Test fun `broken saves and illegal cells are rejected`() {
        val game = Hex.start(3, 0).place(60)!!
        assertEquals(11, game.size)
        assertNull(game.place(60))
        assertNull(game.place(-2))
        assertNull(game.place(121))
        assertNull(HexCodec.decode("bad"))
        assertNull(HexCodec.decode(HexCodec.encode(game).replaceFirst("1", "9")))
        assertEquals(game, HexCodec.decode(HexCodec.encode(game)))
        assertEquals(15, Hex.NAMES.size)
        assertEquals(11, Hex.sizeOf(0))
        assertEquals(7, Hex.sizeOf(8))
        assertTrue(Hex.passOf(12) && Hex.passOf(14) && !Hex.passOf(3))
    }

    @Test fun `medium and stronger take a win, block a threat, and swap the center`() {
        val win = played(*buildList {
            for (col in 0 until 6) {
                add(at(0, col))
                add(at(3, col))
            }
            add(at(0, 6))
        }.toIntArray())
        assertEquals(-1, win.turn)
        assertEquals(-1, checkNotNull(win.place(at(3, 6))).winner)
        val finishes = setOf(at(2, 6), at(3, 6))
        for (setting in listOf(9, 10, 11)) assertTrue(HexAi.choose(win.copy(setting = setting)) in finishes)

        val block = played(
            at(0, 0), at(2, 2),
            at(1, 0), at(2, 3),
            at(2, 0), at(2, 4),
            at(4, 0), at(2, 5),
            at(5, 0), at(2, 6),
            at(6, 0), at(1, 6),
            at(1, 1),
        )
        assertEquals(-1, block.turn)
        val threatened = block.cells.toMutableList().also { it[at(3, 0)] = 1 }
        assertTrue(Hex.connects(threatened, 7, 1))
        assertFalse(Hex.connects(block.cells, 7, 1))
        for (setting in listOf(9, 10, 11)) assertEquals(at(3, 0), HexAi.choose(block.copy(setting = setting)))

        val center = Hex.start(6, 1).place(5 * 11 + 5)!!
        assertTrue(center.canSwap)
        for (setting in listOf(1, 2, 3)) assertEquals(Hex.SWAP, HexAi.choose(center.copy(setting = setting)))
    }

    @Test fun `every strength returns a legal move quickly on 11 by 11`() {
        for (setting in 0..3) {
            var game = Hex.start(20 + setting.toLong(), setting)
            val limit = if (setting >= 2) 4 else 10
            var steps = 0
            while (!game.over && steps < limit) {
                val move = if (game.turn == -1 || game.passAndPlay) HexAi.choose(game) else game.legalMoves().first { it >= 0 }
                assertTrue(move in game.legalMoves(), "setting $setting move $move")
                game = checkNotNull(game.apply(move))
                steps++
            }
        }
        var mid = Hex.start(8, 3)
        repeat(6) {
            if (mid.over) return@repeat
            val step = if (mid.turn == -1) HexAi.choose(mid) else mid.legalMoves().first { it >= 0 }
            mid = checkNotNull(mid.apply(step))
        }
        val started = System.nanoTime()
        val move = HexAi.choose(mid)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue(move in mid.legalMoves())
        assertTrue(millis < 2_000, "expert took ${millis}ms")
        val pass = Hex.start(1, 12)
        assertTrue(pass.passAndPlay)
        assertTrue(HexAi.choose(pass.place(60)!!) in pass.place(60)!!.legalMoves())
    }
}
