package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.ConnectFour
import com.simplegamegen.sudoku.duels.ConnectFourAi
import com.simplegamegen.sudoku.duels.FiveRow
import com.simplegamegen.sudoku.duels.FiveRowAi
import com.simplegamegen.sudoku.duels.TicTacToe
import com.simplegamegen.sudoku.duels.TicTacToeAi
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
import com.simplegamegen.sudoku.tabletop.Raumschach
import com.simplegamegen.sudoku.tabletop.RaumschachAi
import com.simplegamegen.sudoku.tabletop.ReversiState
import com.simplegamegen.sudoku.tabletop.TableAi
import com.simplegamegen.sudoku.tabletop.TriD
import com.simplegamegen.sudoku.tabletop.TriDAi
import com.simplegamegen.sudoku.tabletop.TriMove
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    @Test fun `tic tac toe medium and above take the winning mark`() {
        val cells = listOf(1, -1, 1, 0, -1, 0, 1, 0, 0)
        val game = TicTacToe(1, 2, cells, turn = -1, last = 6)
        for (setting in 1..3) {
            val started = System.nanoTime()
            val move = TicTacToeAi.choose(game.copy(setting = setting))
            val millis = (System.nanoTime() - started) / 1_000_000
            assertEquals(7, move, "setting $setting")
            assertEquals(-1, checkNotNull(game.place(move)).winner)
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

    @Test fun `tri-d medium and above take a hanging queen and keep their own`() {
        val queen = TriD.Sq(2, 4, 4)
        val hanging = tri(mapOf(
            queen to TriD.QUEEN,
            TriD.Sq(2, 6, 4) to -TriD.QUEEN,
            TriD.Sq(4, 1, 2) to TriD.KING,
            TriD.Sq(4, 8, 6) to -TriD.KING,
        ), turn = -1)
        assertTrue(hanging.legalMoves().any { it is TriMove.Slide && it.to == queen })
        assertTrue(TriDAi.choose(hanging.copy(setting = 0)) in hanging.legalMoves())
        for (setting in 1..3) {
            val started = System.nanoTime()
            val move = TriDAi.choose(hanging.copy(setting = setting))
            val millis = (System.nanoTime() - started) / 1_000_000
            assertTrue(move is TriMove.Slide && move.to == queen, "setting $setting played $move")
            assertTrue(millis < 2_000, "setting $setting took ${millis}ms")
        }

        val pawn = TriD.Sq(3, 5, 4)
        val bait = tri(mapOf(
            TriD.Sq(2, 4, 4) to TriD.QUEEN,
            pawn to -TriD.PAWN,
            TriD.Sq(3, 7, 6) to -TriD.ROOK,
            TriD.Sq(4, 1, 2) to TriD.KING,
            TriD.Sq(1, 8, 6) to -TriD.KING,
        ))
        val grab = bait.legalMoves().filterIsInstance<TriMove.Slide>().first { it.to == pawn }
        val lost = bait.perform(grab)
        assertEquals(TriD.QUEEN, lost.pieceAt(pawn))
        assertTrue(lost.legalMoves().any { it is TriMove.Slide && it.to == pawn })
        for (setting in 1..3) {
            val move = TriDAi.choose(bait.copy(setting = setting, seed = 3))
            assertFalse(move is TriMove.Slide && move.to == pawn, "setting $setting hung the queen with $move")
        }
    }

    @Test fun `tri-d medium and above take a mate`() {
        val king = TriD.Sq(4, 8, 6)
        val game = tri(mapOf(
            TriD.Sq(4, 6, 4) to TriD.QUEEN,
            king to -TriD.KING,
            TriD.Sq(1, 1, 2) to TriD.KING,
        ))
        assertTrue(game.legalMoves().any { game.perform(it).let { next -> next.ended && next.winner == 1 } })
        for (setting in 1..3) {
            val move = TriDAi.choose(game.copy(setting = setting))
            val next = game.perform(move)
            assertTrue(next.ended && next.winner == 1, "setting $setting played $move")
        }
    }

    @Test fun `raumschach medium and above take a hanging queen and keep their own`() {
        val queen = Raumschach.idx(2, 2, 2)
        val hanging = raum(mapOf(
            queen to Raumschach.QUEEN,
            Raumschach.idx(2, 4, 2) to -Raumschach.ROOK,
            Raumschach.idx(0, 0, 0) to Raumschach.KING,
            Raumschach.idx(4, 4, 4) to -Raumschach.KING,
        ), turn = -1)
        assertTrue(hanging.legalMoves().any { it.to == queen })
        assertTrue(RaumschachAi.choose(hanging.copy(setting = 0)) in hanging.legalMoves())
        for (setting in 1..3) {
            val started = System.nanoTime()
            val move = RaumschachAi.choose(hanging.copy(setting = setting))
            val millis = (System.nanoTime() - started) / 1_000_000
            assertEquals(queen, move.to, "setting $setting")
            assertTrue(millis < 2_000, "setting $setting took ${millis}ms")
        }

        val pawn = Raumschach.idx(2, 2, 2)
        val from = Raumschach.idx(2, 1, 2)
        val bait = raum(mapOf(
            from to Raumschach.QUEEN,
            pawn to -Raumschach.PAWN,
            Raumschach.idx(2, 4, 2) to -Raumschach.ROOK,
            Raumschach.idx(0, 0, 0) to Raumschach.KING,
            Raumschach.idx(4, 4, 0) to -Raumschach.KING,
        ))
        val grab = bait.legalMoves().first { it.from == from && it.to == pawn }
        val lost = bait.perform(grab)
        assertEquals(Raumschach.QUEEN, lost.board[pawn])
        assertTrue(lost.legalMoves().any { it.to == pawn })
        for (setting in 1..3) {
            val move = RaumschachAi.choose(bait.copy(setting = setting))
            assertFalse(move.from == from && move.to == pawn, "setting $setting hung the queen")
        }
    }

    @Test fun `raumschach medium and above take a mate`() {
        val board = MutableList(125) { 0 }
        board[Raumschach.idx(0, 0, 0)] = -Raumschach.KING
        board[Raumschach.idx(1, 1, 2)] = Raumschach.QUEEN
        board[Raumschach.idx(2, 2, 2)] = Raumschach.KING
        board[Raumschach.idx(4, 4, 4)] = Raumschach.PAWN
        val game = Raumschach(0, 1, board)
        assertTrue(game.legalMoves().any { game.perform(it).let { next -> next.ended && next.winner == 1 } })
        for (setting in 1..3) {
            val move = RaumschachAi.choose(game.copy(setting = setting))
            val next = game.perform(move)
            assertTrue(next.ended && next.winner == 1, "setting $setting")
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

    private fun tri(men: Map<TriD.Sq, Int>, turn: Int = 1): TriD {
        val board = MutableList(480) { 0 }
        for ((sq, piece) in men) board[sq.key] = piece
        return TriD(0, 1, board, turn = turn, fresh = emptyList(), castling = 0)
    }

    private fun raum(men: Map<Int, Int>, turn: Int = 1): Raumschach {
        val board = MutableList(125) { 0 }
        for ((index, piece) in men) board[index] = piece
        return Raumschach(0, 1, board, turn = turn)
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
