package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.tabletop.Go
import com.simplegamegen.sudoku.tabletop.GoAi
import com.simplegamegen.sudoku.tabletop.GoCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoTest {
    private fun at(size: Int, row: Int, col: Int) = row * size + col

    private fun board(size: Int, stones: Map<Int, Int>) = List(size * size) { stones[it] ?: 0 }

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
        g = g.pass()!!
        assertTrue(g.ended)
        assertEquals(25, g.blackArea)
        assertEquals(0, g.whiteArea)
        assertEquals(Go.BLACK, g.winner)
        val empty = Go.start(1, 0).pass()!!.pass()!!
        assertEquals(Go.WHITE, empty.winner)
        assertEquals(0, empty.blackArea)
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
