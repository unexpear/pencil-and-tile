package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.tabletop.Go
import com.simplegamegen.sudoku.tabletop.GoAi
import com.simplegamegen.sudoku.tabletop.GoCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoTest {
    private fun at(size: Int, row: Int, col: Int) = row * size + col

    private fun board(size: Int, stones: Map<Int, Int>) = List(size * size) { stones[it] ?: 0 }

    private fun counted(g: Go) = g.pass()!!.pass()!!.count()!!

    @Test fun `surrounding a stone captures it`() {
        val size = 5
        val white = at(size, 1, 1)
        val stones = mapOf(
            at(size, 0, 1) to Go.BLACK,
            at(size, 1, 0) to Go.BLACK,
            at(size, 1, 2) to Go.BLACK,
            white to Go.WHITE,
        )
        val g = Go(0, 1, size, board(size, stones))
        val next = g.place(at(size, 2, 1))!!
        assertEquals(0, next.board[white])
        assertEquals(Go.BLACK, next.board[at(size, 2, 1)])
        assertEquals(1, next.prisoners)
        assertEquals(Go.WHITE, next.turn)
    }

    @Test fun `suicide is rejected`() {
        val size = 5
        val center = at(size, 1, 1)
        val stones = mapOf(
            at(size, 0, 1) to Go.WHITE,
            at(size, 1, 0) to Go.WHITE,
            at(size, 1, 2) to Go.WHITE,
            at(size, 2, 1) to Go.WHITE,
        )
        val g = Go(0, 1, size, board(size, stones))
        assertNull(g.place(center))
    }

    @Test fun `simple ko forbids the immediate recapture`() {
        val size = 5
        val captured = at(size, 1, 1)
        val played = at(size, 2, 1)
        val stones = mapOf(
            at(size, 0, 1) to Go.BLACK,
            at(size, 1, 0) to Go.BLACK,
            at(size, 1, 2) to Go.BLACK,
            captured to Go.WHITE,
            at(size, 2, 0) to Go.WHITE,
            at(size, 2, 2) to Go.WHITE,
            at(size, 3, 1) to Go.WHITE,
        )
        val next = Go(0, 1, size, board(size, stones)).place(played)!!
        assertEquals(captured, next.ko)
        assertNull(next.place(captured))
        val elsewhere = next.place(at(size, 0, 4))!!
        assertEquals(-1, elsewhere.ko)
        val afterPass = elsewhere.pass()!!
        assertNotNull(afterPass.place(captured))
    }

    @Test fun `two passes score surrounded empty points for black`() {
        val size = 5
        val stones = (0 until size).associate { at(size, it, 0) to Go.BLACK }
        var g = Go(0, 1, size, board(size, stones))
        g = g.pass()!!
        g = g.pass()!!.count()!!
        assertTrue(g.ended)
        assertEquals(25, g.blackArea)
        assertEquals(0, g.whiteArea)
        assertEquals(Go.BLACK, g.winner)
        val empty = Go.start(1, 0).pass()!!.pass()!!.count()!!
        assertEquals(Go.WHITE, empty.winner)
        assertEquals(0, empty.blackArea)
    }

    @Test fun `a move captures every neighboring group with no liberties`() {
        val size = 5
        val left = at(size, 1, 1)
        val right = at(size, 3, 1)
        val stones = mapOf(
            at(size, 0, 1) to Go.BLACK, at(size, 1, 0) to Go.BLACK, at(size, 1, 2) to Go.BLACK, left to Go.WHITE,
            at(size, 3, 0) to Go.BLACK, at(size, 3, 2) to Go.BLACK, at(size, 4, 1) to Go.BLACK, right to Go.WHITE,
        )
        val next = Go(0, 1, size, board(size, stones)).place(at(size, 2, 1))!!
        assertEquals(0, next.board[left])
        assertEquals(0, next.board[right])
        assertEquals(2, next.prisoners)
        assertEquals(-1, next.ko)
    }

    @Test fun `filling the last liberty is legal when it captures`() {
        val size = 5
        val white = at(size, 0, 0)
        val stones = mapOf(white to Go.WHITE, at(size, 0, 1) to Go.BLACK, at(size, 1, 1) to Go.BLACK)
        val next = Go(0, 1, size, board(size, stones)).place(at(size, 1, 0))!!
        assertEquals(0, next.board[white])
        assertEquals(Go.BLACK, next.board[at(size, 1, 0)])
    }

    @Test fun `a corner play with no capture is suicide`() {
        val size = 5
        val stones = mapOf(at(size, 0, 1) to Go.WHITE, at(size, 1, 0) to Go.WHITE)
        assertNull(Go(0, 1, size, board(size, stones)).place(at(size, 0, 0)))
    }

    @Test fun `an earlier board cannot be played again`() {
        val size = 5
        val repeated = board(size, mapOf(at(size, 0, 0) to Go.BLACK))
        val g = Go(0, 1, size, board(size, emptyMap()), seen = listOf(Go.hash(repeated)))
        assertNull(g.place(at(size, 0, 0)))
        assertNotNull(g.place(at(size, 2, 2)))
    }

    @Test fun `empty points between both colors score for neither and komi decides a close count`() {
        val size = 5
        val stones = mutableMapOf<Int, Int>()
        for (row in 0 until size) {
            stones[at(size, row, 0)] = Go.BLACK
            stones[at(size, row, 1)] = Go.BLACK
            stones[at(size, row, 3)] = Go.WHITE
            stones[at(size, row, 4)] = Go.WHITE
        }
        val split = counted(Go(0, 1, size, board(size, stones)))
        assertEquals(10, split.blackArea)
        assertEquals(10, split.whiteArea)
        assertEquals(Go.WHITE, split.winner)

        val ahead = MutableList(size * size) { Go.WHITE }
        repeat(18) { ahead[it] = Go.BLACK }
        val blackWins = counted(Go(0, 1, size, ahead))
        assertEquals(18, blackWins.blackArea)
        assertEquals(7, blackWins.whiteArea)
        assertEquals(Go.BLACK, blackWins.winner)
    }

    @Test fun `a stone left inside the opponent's area keeps that area from scoring`() {
        val size = 5
        val white = at(size, 0, 0)
        val stones = mapOf(white to Go.WHITE, at(size, 0, 1) to Go.BLACK, at(size, 1, 1) to Go.BLACK)
        val sitting = Go(0, 1, size, board(size, stones)).pass()!!.pass()!!
        assertTrue(white in sitting.dead)
        val countedOff = sitting.count()!!
        assertEquals(0, countedOff.board[white])
        assertEquals(25, countedOff.blackArea)
        val kept = sitting.mark(white)!!.count()!!
        assertEquals(2, kept.blackArea)
        assertEquals(1, kept.whiteArea)
        val resumed = sitting.resume()!!
        assertFalse(resumed.counting)
        assertNotNull(resumed.place(at(size, 1, 0)))
        val captured = counted(Go(0, 1, size, board(size, stones)).place(at(size, 1, 0))!!)
        assertEquals(0, captured.board[white])
        assertEquals(25, captured.blackArea)
        assertEquals(Go.BLACK, captured.winner)
    }

    @Test fun `two eyes are read as alive and a stone with one liberty is read as dead`() {
        val size = 7
        val stones = mutableMapOf<Int, Int>()
        for (point in 0 until size * size) stones[point] = Go.WHITE
        for (col in 1..5) {
            stones[at(size, 1, col)] = Go.BLACK
            stones[at(size, 3, col)] = Go.BLACK
        }
        stones[at(size, 2, 1)] = Go.BLACK
        stones[at(size, 2, 3)] = Go.BLACK
        stones[at(size, 2, 5)] = Go.BLACK
        stones[at(size, 2, 2)] = 0
        stones[at(size, 2, 4)] = 0
        val living = Go(0, 1, size, board(size, stones)).pass()!!.pass()!!
        assertTrue(living.dead.isEmpty())
        val deadWhite = at(size, 0, 0)
        val trapped = mapOf(deadWhite to Go.WHITE, at(size, 0, 1) to Go.BLACK, at(size, 1, 1) to Go.BLACK)
        val settled = Go(0, 1, size, board(size, trapped)).pass()!!.pass()!!
        assertEquals(listOf(deadWhite), settled.dead)
    }

    @Test fun `computer returns a legal move at each strength`() {
        val black = Go.start(3, 0).place(40)!!
        for (setting in Go.NAMES.indices) {
            val g = black.copy(setting = setting)
            val move = GoAi.choose(g)
            if (move == Go.PASS) assertTrue(g.pass() != null)
            else assertNotNull(g.place(move), "setting $setting")
        }
    }

    @Test fun `save codec round-trips and rejects junk`() {
        val g = Go.start(9, 2).place(20)!!.place(21)!!
        assertEquals(g, GoCodec.decode(GoCodec.encode(g)))
        assertNull(GoCodec.decode("bad"))
    }
}
