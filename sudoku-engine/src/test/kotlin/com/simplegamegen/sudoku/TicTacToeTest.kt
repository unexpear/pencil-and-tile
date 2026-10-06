package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.TicTacToe
import com.simplegamegen.sudoku.duels.TicTacToeAi
import com.simplegamegen.sudoku.duels.TicTacToeCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TicTacToeTest {
    private fun at(row: Int, col: Int, size: Int = 3) = row * size + col

    private fun played(vararg moves: Int, setting: Int = 0): TicTacToe {
        var game = TicTacToe.start(1, setting)
        for (move in moves) game = checkNotNull(game.place(move)) { "move $move" }
        return game
    }

    @Test fun `three in a row wins on 3 by 3 and a shorter run does not`() {
        val row = played(0, 3, 1, 4, 2)
        assertEquals(1, row.winner)
        assertTrue(row.over)
        assertEquals(listOf(0, 1, 2), row.winningLines().single())
        assertNull(row.place(5))

        val down = played(0, 1, 3, 2, 6)
        assertEquals(1, down.winner)
        assertEquals(listOf(0, 3, 6), down.winningLines().single())

        val diagonal = played(0, 1, 4, 2, 8)
        assertEquals(1, diagonal.winner)
        assertEquals(setOf(0, 4, 8), diagonal.winningCells())

        val anti = played(2, 0, 4, 1, 6)
        assertEquals(1, anti.winner)
        assertEquals(setOf(2, 4, 6), anti.winningCells())

        val short = played(0, 4, 1, 5)
        assertEquals(0, short.winner)
        assertFalse(short.over)
        assertTrue(short.winningLines().isEmpty())
    }

    @Test fun `4 by 4 needs four and 5 by 5 needs five`() {
        val three = MutableList(16) { 0 }
        three[0] = 1; three[1] = 1; three[2] = 1
        three[4] = -1; three[5] = -1
        val pending = TicTacToe(4, 1, three, turn = -1, last = 2, winner = 0)
        assertEquals(4, pending.size)
        assertEquals(4, pending.need)
        assertEquals(0, pending.winner)
        assertFalse(pending.wouldWin(3, -1))
        assertTrue(pending.wouldWin(3, 1))
        assertEquals(0, checkNotNull(pending.place(3)).winner)

        val almost = MutableList(16) { 0 }
        almost[0] = 1; almost[1] = 1; almost[2] = 1
        almost[4] = -1; almost[5] = -1; almost[6] = -1
        val ready = TicTacToe(4, 2, almost, turn = 1, last = 6, winner = 0)
        val across = checkNotNull(ready.place(3))
        assertEquals(1, across.winner)
        assertEquals(listOf(0, 1, 2, 3), across.winningLines().single())

        val diagonal = MutableList(16) { 0 }
        for (i in 0 until 4) diagonal[i * 4 + i] = 1
        diagonal[1] = -1; diagonal[2] = -1; diagonal[7] = -1
        val down = TicTacToe(4, 3, diagonal, turn = 1, last = 15, winner = 1)
        assertEquals(listOf(0, 5, 10, 15), down.winningLines().single())

        val four = MutableList(25) { 0 }
        for (col in 0 until 4) four[col] = 1
        four[5] = -1; four[6] = -1; four[7] = -1
        val five = TicTacToe(8, 1, four, turn = -1, last = 3, winner = 0)
        assertEquals(5, five.need)
        assertEquals(0, five.winner)
        assertTrue(five.wouldWin(4, 1))
        assertFalse(five.wouldWin(4, -1))
        val file = MutableList(25) { 0 }
        for (row in 0 until 5) file[row * 5] = 1
        file[1] = -1; file[2] = -1; file[3] = -1; file[6] = -1
        val won = TicTacToe(8, 4, file, turn = 1, last = 20, winner = 1)
        assertEquals(listOf(0, 5, 10, 15, 20), won.winningLines().single())
    }

    @Test fun `a full board with no line is a draw and broken saves are rejected`() {
        val cells = listOf(1, -1, 1, 1, 1, -1, -1, 1, -1)
        val draw = TicTacToe(0, 9, cells, turn = 1, last = 7, winner = 0)
        assertTrue(draw.over)
        assertTrue(draw.draw)
        assertEquals(0, draw.winner)
        assertTrue(draw.legalMoves().isEmpty())
        assertNull(draw.place(0))
        assertEquals(draw, TicTacToeCodec.decode(TicTacToeCodec.encode(draw)))

        val mid = TicTacToe.start(4, 14).place(12)!!
        assertEquals(5, mid.size)
        assertTrue(mid.passAndPlay)
        assertEquals(mid, TicTacToeCodec.decode(TicTacToeCodec.encode(mid)))
        assertNull(mid.place(12))
        assertNull(mid.place(-1))
        assertNull(mid.place(25))
        assertNull(TicTacToeCodec.decode("bad"))
        assertNull(TicTacToeCodec.decode(TicTacToeCodec.encode(mid).replaceFirst("1", "9")))
        assertEquals(15, TicTacToe.NAMES.size)
        assertEquals(3, TicTacToe.sizeOf(0))
        assertEquals(4, TicTacToe.sizeOf(5))
        assertEquals(5, TicTacToe.sizeOf(11))
        assertEquals(3, TicTacToe.levelOf(3))
        assertTrue(TicTacToe.passOf(12) && TicTacToe.passOf(14) && !TicTacToe.passOf(3))
        assertEquals(3, TicTacToe.sizeOf(12))
        assertEquals(5, TicTacToe.sizeOf(14))
    }

    @Test fun `medium and stronger take a win and block a threat`() {
        val win = TicTacToe(
            1, 2,
            listOf(1, -1, 1, 0, -1, 0, 1, 0, 0),
            turn = -1, last = 6, winner = 0,
        )
        assertTrue(win.wouldWin(7, -1))
        assertTrue(win.wouldWin(3, 1))
        for (setting in listOf(1, 2, 3)) {
            assertEquals(at(2, 1), TicTacToeAi.choose(win.copy(setting = setting)), "setting $setting")
        }
        val wide = MutableList(16) { 0 }
        wide[0] = -1; wide[1] = -1; wide[2] = -1
        wide[4] = 1; wide[5] = 1; wide[6] = 1; wide[8] = 1
        val four = TicTacToe(5, 2, wide, turn = -1, last = 8, winner = 0)
        for (setting in listOf(5, 6, 7)) assertEquals(3, TicTacToeAi.choose(four.copy(setting = setting)))
        val tall = MutableList(25) { 0 }
        for (col in 0 until 4) tall[col] = -1
        tall[5] = 1; tall[6] = 1; tall[7] = 1; tall[8] = 1; tall[10] = 1
        val five = TicTacToe(9, 2, tall, turn = -1, last = 10, winner = 0)
        for (setting in listOf(9, 10, 11)) assertEquals(4, TicTacToeAi.choose(five.copy(setting = setting)))

        val threat = TicTacToe(
            1, 3,
            listOf(1, 1, 0, -1, 0, 0, 0, 0, 0),
            turn = -1, last = 1, winner = 0,
        )
        assertTrue(threat.wouldWin(2, 1))
        assertFalse(threat.legalMoves().any { threat.wouldWin(it, -1) })
        for (setting in listOf(1, 2, 3)) assertEquals(2, TicTacToeAi.choose(threat.copy(setting = setting)), "setting $setting")
    }

    @Test fun `medium and stronger open two threats at once on 4 by 4`() {
        val cells = MutableList(16) { 0 }
        cells[1] = -1; cells[2] = -1; cells[4] = -1; cells[8] = -1
        cells[5] = 1; cells[6] = 1; cells[9] = 1; cells[11] = 1; cells[13] = 1
        val game = TicTacToe(5, 6, cells, turn = -1, last = 13, winner = 0)
        assertFalse(game.legalMoves().any { game.wouldWin(it, -1) || game.wouldWin(it, 1) })
        assertTrue(game.opensFork(0, -1))
        for (setting in listOf(5, 6, 7)) {
            val move = TicTacToeAi.choose(game.copy(setting = setting))
            assertTrue(game.opensFork(move, -1), "setting $setting played $move")
        }
    }

    @Test fun `expert on 3 by 3 plays perfectly and every strength stays legal`() {
        val expert = TicTacToe.start(1, 3)
        assertEquals(4, TicTacToeAi.choose(expert))
        val center = expert.place(4)!!
        for (setting in listOf(2, 3)) {
            val reply = TicTacToeAi.choose(center.copy(setting = setting))
            assertTrue(reply in setOf(0, 2, 6, 8), "setting $setting replied $reply")
        }
        assertEquals(4, TicTacToeAi.choose(expert.place(0)!!.copy(setting = 3)))
        assertEquals(4, TicTacToeAi.choose(expert.place(1)!!.copy(setting = 3)))

        hold(TicTacToe.start(2, 3), ai = -1)
        hold(TicTacToe.start(2, 3), ai = 1)

        for (setting in 0..14) {
            var game = TicTacToe.start(20 + setting.toLong(), setting)
            val limit = if (setting in 8..11) 4 else 8
            var steps = 0
            while (!game.over && steps < limit) {
                val move = if (game.turn == -1 || game.passAndPlay) TicTacToeAi.choose(game) else game.legalMoves().first()
                assertTrue(move in game.legalMoves(), "setting $setting move $move")
                game = checkNotNull(game.place(move))
                steps++
            }
        }
        var mid = TicTacToe.start(8, 11)
        repeat(6) {
            if (mid.over) return@repeat
            val step = if (mid.turn == -1) TicTacToeAi.choose(mid) else mid.legalMoves().first()
            mid = checkNotNull(mid.place(step))
        }
        val started = System.nanoTime()
        val move = TicTacToeAi.choose(mid)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue(move in mid.legalMoves())
        assertTrue(millis < 2_000, "expert took ${millis}ms")
    }

    /** [ai] never loses when the other side tries every legal reply. */
    private fun hold(game: TicTacToe, ai: Int) {
        if (game.over) {
            assertNotEquals(-ai, game.winner)
            return
        }
        if (game.turn == ai) hold(checkNotNull(game.place(TicTacToeAi.choose(game))), ai)
        else game.legalMoves().forEach { hold(checkNotNull(game.place(it)), ai) }
    }
}
