package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.ConnectFour
import com.simplegamegen.sudoku.duels.ConnectFourAi
import com.simplegamegen.sudoku.duels.FiveRow
import com.simplegamegen.sudoku.duels.FiveRowAi
import com.simplegamegen.sudoku.duels.Mancala
import com.simplegamegen.sudoku.duels.MancalaAi
import com.simplegamegen.sudoku.duels.ShipAi
import com.simplegamegen.sudoku.duels.ShipCrew
import com.simplegamegen.sudoku.tabletop.CheckersState
import com.simplegamegen.sudoku.tabletop.Chess
import com.simplegamegen.sudoku.tabletop.ChessAi
import com.simplegamegen.sudoku.tabletop.Go
import com.simplegamegen.sudoku.tabletop.GoAi
import com.simplegamegen.sudoku.tabletop.Move
import com.simplegamegen.sudoku.tabletop.Op
import com.simplegamegen.sudoku.tabletop.ReversiState
import com.simplegamegen.sudoku.tabletop.TableAi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpponentStrengthTest {
    @Test fun `connect four medium and above force two threats at once`() {
        val cells = MutableList(ConnectFour.CELLS) { 0 }
        val bottom = 5 * ConnectFour.COLS
        val above = 4 * ConnectFour.COLS
        for (col in intArrayOf(0, 1, 3, 5, 6)) cells[bottom + col] = 1
        for (col in intArrayOf(2, 4)) {
            cells[bottom + col] = -1
            cells[above + col] = -1
        }
        val game = ConnectFour(1, 4, cells, turn = -1)
        assertTrue((0 until ConnectFour.COLS).none { game.wouldWin(it, -1) })
        for (setting in 1..3) {
            val started = System.nanoTime()
            val col = ConnectFourAi.choose(game.copy(setting = setting))
            val millis = (System.nanoTime() - started) / 1_000_000
            val next = checkNotNull(game.copy(setting = setting).drop(col))
            assertEquals(0, next.winner, "setting $setting column $col")
            val threats = next.legalColumns().count { next.wouldWin(it, -1) }
            assertTrue(threats >= 2, "setting $setting column $col opened $threats threats")
            assertTrue(millis < 2_000, "setting $setting took ${millis}ms")
        }
    }

    @Test fun `five in a row medium and above build an open four`() {
        val cells = MutableList(FiveRow.CELLS) { 0 }
        val row = 5
        for (col in 2..4) cells[row * FiveRow.SIZE + col] = -1
        cells[0] = 1
        cells[1] = 1
        cells[2] = 1
        cells[FiveRow.SIZE] = 1
        val game = FiveRow(1, 3, cells, turn = -1)
        for (setting in 1..3) {
            val started = System.nanoTime()
            val move = FiveRowAi.choose(game.copy(setting = setting))
            val millis = (System.nanoTime() - started) / 1_000_000
            val next = checkNotNull(game.place(move))
            assertEquals(0, next.winner, "setting $setting")
            val threats = (0 until FiveRow.CELLS).count { next.wouldWin(it, -1) }
            assertTrue(threats >= 2, "setting $setting move $move opened $threats threats")
            assertTrue(millis < 2_000, "setting $setting took ${millis}ms")
        }
    }

    @Test fun `mancala expert answers the opening within the time cap`() {
        val game = Mancala.start(2, 3)
        val started = System.nanoTime()
        val pit = MancalaAi.choose(game)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue(pit in game.legalPits())
        assertTrue(millis < 2_000, "expert took ${millis}ms")
    }

    @Test fun `chess medium and above take a hanging queen`() {
        val board = MutableList(64) { 0 }
        board[60] = Chess.KING
        board[4] = -Chess.KING
        board[0] = -Chess.ROOK
        board[32] = Chess.QUEEN
        val game = Chess(setting = 1, seed = 1, board = board, turn = -1, castling = 0)
        assertTrue(ChessAi.choose(game.copy(setting = 0)) in game.legalMoves())
        for (setting in 1..3) {
            val move = ChessAi.choose(game.copy(setting = setting))
            assertEquals(32, move.to, "setting $setting kept the queen")
            assertTrue(move in game.legalMoves())
        }
    }

    @Test fun `checkers medium and above take the king`() {
        // Both captures run downhill from the top rank, so white cannot jump back off the board.
        val board = MutableList(64) { 0 }
        board[1] = -1
        board[10] = 2
        board[5] = -1
        board[14] = 1
        board[60] = 1
        val state = CheckersState(board, turn = -1)
        assertTrue(Move(Op.MOVE, 1, 19) in state.legalMoves())
        for (level in 1..3) {
            val move = checkNotNull(TableAi.choose(state, level, 3))
            assertEquals(1, move.from, "level $level")
            assertEquals(19, move.to, "level $level")
        }
    }

    @Test fun `reversi medium and above take a corner`() {
        val board = MutableList(64) { 0 }
        board[1] = 1
        board[2] = -1
        board[12] = 1
        board[13] = -1
        val state = ReversiState(board, turn = -1)
        assertTrue(state.legalMoves().any { it.from == 0 })
        assertTrue(state.legalMoves().size > 1)
        for (level in 1..3) {
            val move = checkNotNull(TableAi.choose(state, level, 4))
            assertEquals(0, move.from, "level $level moves ${state.legalMoves()}")
        }
    }

    @Test fun `go medium and above save a stone in atari`() {
        val size = 7
        fun at(row: Int, col: Int) = row * size + col
        val board = MutableList(size * size) { 0 }
        board[at(3, 3)] = Go.WHITE
        board[at(3, 2)] = Go.BLACK
        board[at(3, 4)] = Go.BLACK
        board[at(2, 3)] = Go.BLACK
        val liberty = at(4, 3)
        for (setting in 1..3) {
            val game = Go(setting, 1, size, board, turn = Go.WHITE)
            assertEquals(liberty, GoAi.choose(game), "setting $setting")
        }
    }

    @Test fun `ship expert keeps a low cargo die on the last roll and hard rolls it again`() {
        val dice = listOf(6, 5, 4, 3, 1)
        val expert = ShipAi.step(crew(dice, setting = 3, rolls = 2))
        assertEquals(listOf(true, true, true, true, false), expert.held)
        val hard = ShipAi.step(crew(dice, setting = 2, rolls = 2))
        assertEquals(listOf(true, true, true, false, false), hard.held)
        val medium = ShipAi.step(crew(dice, setting = 1, rolls = 2))
        assertEquals(listOf(true, true, true, false, false), medium.held)
    }

    private fun crew(dice: List<Int>, setting: Int, rolls: Int) = ShipCrew(
        setting = setting,
        seed = 1,
        drawn = ShipCrew.DICE,
        you = 0,
        cpu = 0,
        hand = 0,
        dice = dice,
        held = List(ShipCrew.DICE) { false },
        rolls = rolls,
        turn = -1,
    )
}
