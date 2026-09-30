package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.tabletop.Go
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Positions that have tripped Go programs: diagonal links, edge and corner captures,
 * snapback versus ko, false eyes, seki, and a normal opening.
 */
class GoFaultsTest {
    private fun at(size: Int, row: Int, col: Int) = row * size + col

    private fun board(size: Int, stones: Map<Int, Int>) = List(size * size) { stones[it] ?: 0 }

    private fun play(size: Int, stones: Map<Int, Int>, row: Int, col: Int, turn: Int = Go.BLACK) =
        Go(0, 1, size, board(size, stones), turn = turn).place(at(size, row, col))

    @Test fun `the tutorial capture takes the white stone and leaves black`() {
        val size = 5
        val stones = mapOf(
            at(size, 0, 1) to Go.BLACK,
            at(size, 1, 0) to Go.BLACK,
            at(size, 1, 2) to Go.BLACK,
            at(size, 1, 1) to Go.WHITE,
        )
        val next = play(size, stones, 2, 1)!!
        assertEquals(0, next.board[at(size, 1, 1)])
        assertEquals(Go.BLACK, next.board[at(size, 2, 1)])
        assertEquals(1, next.prisoners)
    }

    @Test fun `stones that only touch on a corner are not one group`() {
        val size = 5
        val stones = mapOf(
            at(size, 1, 1) to Go.WHITE,
            at(size, 2, 2) to Go.WHITE,
            at(size, 0, 1) to Go.BLACK,
            at(size, 1, 0) to Go.BLACK,
            at(size, 1, 2) to Go.BLACK,
        )
        val next = play(size, stones, 2, 1)!!
        assertEquals(0, next.board[at(size, 1, 1)])
        assertEquals(Go.WHITE, next.board[at(size, 2, 2)])
    }

    @Test fun `an edge pair is captured when its last liberty is filled`() {
        val size = 5
        val stones = mapOf(
            at(size, 0, 1) to Go.WHITE,
            at(size, 0, 2) to Go.WHITE,
            at(size, 0, 0) to Go.BLACK,
            at(size, 0, 3) to Go.BLACK,
            at(size, 1, 1) to Go.BLACK,
        )
        val next = play(size, stones, 1, 2)!!
        assertEquals(0, next.board[at(size, 0, 1)])
        assertEquals(0, next.board[at(size, 0, 2)])
        assertEquals(2, next.prisoners)
    }

    @Test fun `playing on a stone or off the board does nothing`() {
        val size = 5
        val stones = mapOf(at(size, 2, 2) to Go.BLACK)
        val g = Go(0, 1, size, board(size, stones), turn = Go.WHITE)
        assertNull(g.place(at(size, 2, 2)))
        assertNull(g.place(-1))
        assertNull(g.place(size * size))
    }

    @Test fun `a stone may step into atari when the new stone still has a liberty`() {
        val size = 5
        val stones = mapOf(
            at(size, 1, 1) to Go.WHITE,
            at(size, 0, 1) to Go.BLACK,
            at(size, 1, 0) to Go.BLACK,
            at(size, 2, 1) to Go.BLACK,
        )
        val next = play(size, stones, 1, 2, turn = Go.WHITE)!!
        assertEquals(Go.WHITE, next.board[at(size, 1, 1)])
        assertEquals(Go.WHITE, next.board[at(size, 1, 2)])
    }

    @Test fun `filling your last liberty is suicide`() {
        val size = 5
        val stones = mutableMapOf<Int, Int>()
        for (row in 1..3) for (col in 1..3) if (!(row == 2 && col == 2)) stones[at(size, row, col)] = Go.BLACK
        listOf(0 to 1, 0 to 2, 0 to 3, 1 to 0, 1 to 4, 2 to 0, 2 to 4, 3 to 0, 3 to 4, 4 to 1, 4 to 2, 4 to 3)
            .forEach { (row, col) -> stones[at(size, row, col)] = Go.WHITE }
        assertNull(play(size, stones, 2, 2))
    }

    @Test fun `two stones taken together are not a ko`() {
        val size = 5
        val a = at(size, 0, 1)
        val b = at(size, 0, 2)
        val stones = mapOf(
            a to Go.WHITE, b to Go.WHITE,
            at(size, 0, 0) to Go.BLACK, at(size, 0, 3) to Go.BLACK,
            at(size, 1, 1) to Go.BLACK,
        )
        val next = play(size, stones, 1, 2)!!
        assertEquals(-1, next.ko)
        assertEquals(0, next.board[a])
        assertEquals(0, next.board[b])
        val back = next.place(a)
        assertNotNull(back)
        assertEquals(Go.WHITE, back!!.board[a])
    }

    @Test fun `a snapback captures the group and does not set ko`() {
        val size = 5
        val stones = mapOf(
            at(size, 1, 2) to Go.WHITE,
            at(size, 2, 2) to Go.WHITE,
            at(size, 2, 4) to Go.WHITE,
            at(size, 3, 3) to Go.WHITE,
            at(size, 0, 2) to Go.BLACK,
            at(size, 0, 3) to Go.BLACK,
            at(size, 1, 1) to Go.BLACK,
            at(size, 1, 4) to Go.BLACK,
            at(size, 2, 1) to Go.BLACK,
            at(size, 3, 2) to Go.BLACK,
        )
        val thrown = play(size, stones, 2, 3)!!
        assertEquals(Go.BLACK, thrown.board[at(size, 2, 3)])
        assertEquals(Go.WHITE, thrown.board[at(size, 1, 2)])
        val reply = thrown.place(at(size, 1, 3))
        assertNotNull(reply)
        val takenBack = reply!!
        assertEquals(0, takenBack.board[at(size, 2, 3)])
        assertEquals(-1, takenBack.ko)
        val snap = takenBack.place(at(size, 2, 3))!!
        assertEquals(0, snap.board[at(size, 1, 2)])
        assertEquals(0, snap.board[at(size, 2, 2)])
        assertEquals(0, snap.board[at(size, 1, 3)])
        assertEquals(Go.BLACK, snap.board[at(size, 2, 3)])
        assertEquals(-1, snap.ko)
    }

    @Test fun `a false eye does not keep a group alive`() {
        val size = 5
        // One-point dent on the side. The outer diagonal is white, so it is not a real eye.
        val eye = at(size, 1, 2)
        val stones = mutableMapOf(
            at(size, 1, 1) to Go.BLACK,
            at(size, 1, 3) to Go.BLACK,
            at(size, 2, 1) to Go.BLACK,
            at(size, 2, 2) to Go.BLACK,
            at(size, 2, 3) to Go.BLACK,
            at(size, 0, 1) to Go.WHITE,
            at(size, 0, 2) to Go.WHITE,
            at(size, 0, 3) to Go.WHITE,
            at(size, 1, 0) to Go.WHITE,
            at(size, 1, 4) to Go.WHITE,
            at(size, 2, 0) to Go.WHITE,
            at(size, 2, 4) to Go.WHITE,
            at(size, 3, 1) to Go.WHITE,
            at(size, 3, 2) to Go.WHITE,
            at(size, 3, 3) to Go.WHITE,
        )
        val settled = Go(0, 1, size, board(size, stones)).pass()!!.pass()!!
        assertFalse(eye in stones)
        assertTrue(settled.dead.contains(at(size, 1, 1)), "false-eye group should be readable as dead")
    }

    @Test fun `shared liberties in a seki are not marked dead and do not score`() {
        val size = 5
        val stones = mapOf(
            at(size, 1, 0) to Go.BLACK, at(size, 1, 1) to Go.BLACK,
            at(size, 2, 0) to Go.BLACK,
            at(size, 3, 0) to Go.BLACK, at(size, 3, 1) to Go.BLACK,
            at(size, 1, 3) to Go.WHITE, at(size, 1, 4) to Go.WHITE,
            at(size, 2, 4) to Go.WHITE,
            at(size, 3, 3) to Go.WHITE, at(size, 3, 4) to Go.WHITE,
            at(size, 0, 1) to Go.BLACK, at(size, 0, 2) to Go.BLACK, at(size, 0, 3) to Go.WHITE,
            at(size, 4, 1) to Go.BLACK, at(size, 4, 2) to Go.WHITE, at(size, 4, 3) to Go.WHITE,
            at(size, 2, 1) to Go.BLACK, at(size, 2, 3) to Go.WHITE,
        )
        val settled = Go(0, 1, size, board(size, stones)).pass()!!.pass()!!
        assertFalse(at(size, 1, 1) in settled.dead)
        assertFalse(at(size, 1, 3) in settled.dead)
        val scored = settled.count()!!
        assertEquals(0, scored.board[at(size, 2, 2)])
    }

    @Test fun `a normal opening on the star points just places stones`() {
        val n = 19
        val stars = listOf(3 to 3, 3 to 15, 15 to 3, 15 to 15, 9 to 3, 9 to 15, 3 to 9, 15 to 9)
        var g = Go.start(1, 0)
        for ((row, col) in stars) {
            val before = g.board.count { it != 0 }
            g = g.place(row * n + col)!!
            assertEquals(before + 1, g.board.count { it != 0 })
            assertEquals(0, g.prisoners)
            assertEquals(-1, g.ko)
        }
        assertEquals(Go.BLACK, g.turn)
        // Approach the first star, then a contact play, then extend.
        g = g.place(5 * n + 3)!!
        g = g.place(4 * n + 3)!!
        g = g.place(5 * n + 2)!!
        assertEquals(Go.BLACK, g.board[5 * n + 2])
        assertEquals(Go.WHITE, g.board[4 * n + 3])
        assertNull(g.place(3 * n + 3))
    }

    @Test fun `counting refuses a move and resume returns to play`() {
        val size = 5
        val g = Go.start(1, 0).pass()!!.pass()!!
        assertTrue(g.counting)
        assertNull(g.place(0))
        assertNull(g.pass())
        assertNull(g.count()!!.count())
        val again = g.resume()!!
        assertFalse(again.counting)
        assertNotNull(again.place(0))
    }
}
