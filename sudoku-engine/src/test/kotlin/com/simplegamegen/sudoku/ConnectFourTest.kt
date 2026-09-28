package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.ConnectFour
import com.simplegamegen.sudoku.duels.ConnectFourAi
import com.simplegamegen.sudoku.duels.ConnectFourCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectFourTest {
    private fun play(vararg cols: Int): ConnectFour {
        var game = ConnectFour.start(1, 0)
        for (col in cols) game = checkNotNull(game.drop(col)) { "column $col" }
        return game
    }

    @Test fun `discs fall to the lowest empty cell and turns alternate`() {
        var game = ConnectFour.start(1, 0)
        assertEquals(7, ConnectFour.COLS)
        assertEquals(6, ConnectFour.ROWS)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), game.legalColumns())
        game = game.drop(0)!!
        assertEquals(1, game.cells[5 * 7])
        assertEquals(0, game.cells[4 * 7])
        assertEquals(-1, game.turn)
        game = game.drop(0)!!
        assertEquals(-1, game.cells[4 * 7])
        assertEquals(1, game.turn)
        assertEquals(game, ConnectFourCodec.decode(ConnectFourCodec.encode(game)))
    }

    @Test fun `four across, down or diagonal wins and further drops are refused`() {
        val across = play(0, 0, 1, 1, 2, 2, 3)
        assertEquals(1, across.winner)
        assertTrue(across.over)
        assertEquals(setOf(5 * 7, 5 * 7 + 1, 5 * 7 + 2, 5 * 7 + 3), across.winningCells())
        assertNull(across.drop(4))

        val down = play(3, 2, 3, 2, 3, 2, 3)
        assertEquals(1, down.winner)
        assertTrue((2..5).all { down.cells[it * 7 + 3] == 1 })

        val diagonal = play(0, 1, 1, 2, 6, 2, 2, 3, 3, 3, 3)
        assertEquals(1, diagonal.winner)
        assertTrue(diagonal.winningCells().containsAll(listOf(5 * 7, 4 * 7 + 1, 3 * 7 + 2, 2 * 7 + 3)))

        val other = play(6, 6, 3, 3, 6, 3, 2, 4, 4, 2, 5, 3, 4, 5, 5, 2, 6)
        assertEquals(1, other.winner)
        assertTrue(other.winningCells().size >= 4)
    }

    @Test fun `a full board with no line of four is a draw`() {
        val rows = listOf("YCCYCYC", "YCYYCCC", "CYCCYCY", "CYCYCYY", "YYCYCYC", "YCYCCYY")
        val cells = rows.flatMap { row -> row.map { if (it == 'Y') 1 else -1 } }
        val game = ConnectFour(0, 1, cells, turn = -1, winner = 0)
        assertTrue(game.draw)
        assertTrue(game.over)
        assertTrue(game.legalColumns().isEmpty())
        assertNull(game.drop(3))
        assertEquals(game, ConnectFourCodec.decode(ConnectFourCodec.encode(game)))
    }

    @Test fun `full columns, out of range columns and broken saves are rejected`() {
        var game = ConnectFour.start(2, 1)
        repeat(6) { game = game.drop(0)!! }
        assertNull(game.drop(0))
        assertNull(game.drop(-1))
        assertNull(game.drop(7))
        assertNull(ConnectFourCodec.decode("bad"))
        assertNull(ConnectFourCodec.decode(ConnectFourCodec.encode(game).replaceFirst("1", "2")))
        val floating = MutableList(42) { 0 }.also { it[0] = 1 }
        val text = listOf("1", "0", "1", floating.joinToString(","), "1", "0", "0").joinToString("\n")
        assertNull(ConnectFourCodec.decode(text))
    }

    @Test fun `medium and stronger take a win and block an immediate threat`() {
        // Computer to move, with three of its discs on the bottom and the fourth column open.
        val win = play(4, 0, 5, 1, 6, 2, 4)
        assertEquals(-1, win.turn)
        assertTrue(win.wouldWin(3, -1))
        for (setting in 1..3) assertEquals(3, ConnectFourAi.choose(win.copy(setting = setting)))

        // You threaten four across; the computer must occupy that column.
        val threat = play(0, 4, 1, 5, 2)
        assertEquals(-1, threat.turn)
        assertTrue(threat.wouldWin(3, 1))
        for (setting in 1..3) assertEquals(3, ConnectFourAi.choose(threat.copy(setting = setting)))
    }

    @Test fun `every strength plays legal columns and easy and medium finish a game`() {
        for (setting in 0..3) {
            var game = ConnectFour.start(20 + setting.toLong(), setting)
            var steps = 0
            val limit = if (setting >= 2) 6 else 42
            while (!game.over && steps < limit) {
                val col = if (game.turn == -1) ConnectFourAi.choose(game) else game.legalColumns().first()
                assertTrue(col in game.legalColumns(), "setting $setting")
                game = checkNotNull(game.drop(col))
                steps++
            }
            if (setting < 2) assertTrue(game.over, "setting $setting did not finish")
        }
    }

    @Test fun `expert picks a column quickly from the opening`() {
        val game = ConnectFour.start(1, 3).drop(3)!!
        val started = System.nanoTime()
        val col = ConnectFourAi.choose(game)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertNotNull(game.drop(col))
        assertTrue(millis < 2_000, "expert took ${millis}ms")
    }
}
