package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.tabletop.Chess
import com.simplegamegen.sudoku.tabletop.ChessAi
import com.simplegamegen.sudoku.tabletop.ChessCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChessTest {
    private fun emptyBoard() = MutableList(64) { 0 }

    private fun place(board: MutableList<Int>, square: Int, piece: Int) {
        board[square] = piece
    }

    private fun game(
        board: List<Int>,
        turn: Int = 1,
        castling: Int = 0,
        ep: Int = -1,
        setting: Int = 0,
        seed: Long = 1,
        halfmove: Int = 0,
    ) = Chess(setting, seed, board, turn, castling, ep, halfmove = halfmove)

    @Test fun `opening has twenty legal white moves and knights develop`() {
        val open = Chess.start(1, 0)
        assertEquals(20, open.legalMoves().size)
        // Ng1-f3
        val nf3 = open.play(62, 45)!!
        assertEquals(Chess.KNIGHT, nf3.board[45])
        assertEquals(0, nf3.board[62])
        assertEquals(-1, nf3.turn)
        assertEquals(20, nf3.legalMoves().size)
        assertEquals(nf3, ChessCodec.decode(ChessCodec.encode(nf3)))
    }

    @Test fun `you cannot move into check`() {
        val board = emptyBoard()
        place(board, 60, Chess.KING) // e1
        place(board, 4, -Chess.KING) // e8
        place(board, 28, -Chess.ROOK) // e5, pins the e-file
        val g = game(board)
        assertNull(g.play(60, 52)) // e1-e2 into check
        assertTrue(g.legalMoves().none { it.from == 60 && it.to == 52 })
        assertNotNull(g.play(60, 59)) // e1-d1 off the file
    }

    @Test fun `checkmate is detected as a win for the mating side`() {
        // Ka8, Kb6, Qb7 — white plays Qa7# (king protects the queen)
        val board = emptyBoard()
        place(board, 0, -Chess.KING) // a8
        place(board, 17, Chess.KING) // b6
        place(board, 9, Chess.QUEEN) // b7
        val next = game(board).play(9, 8)!! // Qa7#
        assertTrue(next.ended)
        assertEquals(1, next.winner)
        assertTrue(next.legalMoves().isEmpty())
        assertTrue(next.inCheck())
        assertEquals(next, ChessCodec.decode(ChessCodec.encode(next)))
    }

    @Test fun `stalemate is a draw`() {
        // Ka8, Ka6, Qc8 — black plays Qc7, leaving white with no legal move and not in check
        val board = emptyBoard()
        place(board, 0, Chess.KING) // a8
        place(board, 16, -Chess.KING) // a6
        place(board, 2, -Chess.QUEEN) // c8
        val next = game(board, turn = -1).play(2, 10)!! // Qc7
        assertTrue(next.ended)
        assertEquals(0, next.winner)
        assertFalse(next.inCheck())
    }

    @Test fun `pawn promotes to queen only`() {
        val board = emptyBoard()
        place(board, 8, Chess.PAWN) // a7
        place(board, 60, Chess.KING)
        place(board, 4, -Chess.KING)
        val next = game(board).play(8, 0)!!
        assertEquals(Chess.QUEEN, next.board[0])
        assertEquals(0, next.board[8])
    }

    @Test fun `castling kingside moves king and rook`() {
        val board = emptyBoard()
        place(board, 60, Chess.KING)
        place(board, 63, Chess.ROOK)
        place(board, 4, -Chess.KING)
        val next = game(board, castling = Chess.WK).play(60, 62)!!
        assertEquals(Chess.KING, next.board[62])
        assertEquals(Chess.ROOK, next.board[61])
        assertEquals(0, next.board[60])
        assertEquals(0, next.board[63])
        assertEquals(0, next.castling and Chess.WK)
    }

    @Test fun `en passant captures the pawn that double-stepped`() {
        val board = emptyBoard()
        place(board, 36, Chess.PAWN) // e5
        place(board, 35, -Chess.PAWN) // d5
        place(board, 60, Chess.KING)
        place(board, 4, -Chess.KING)
        val next = game(board, ep = 27).play(36, 27)!! // exd6 e.p.
        assertEquals(Chess.PAWN, next.board[27])
        assertEquals(0, next.board[35])
        assertEquals(0, next.board[36])
    }

    @Test fun `fifty quiet moves each is a draw and a pawn move resets the count`() {
        val board = emptyBoard()
        place(board, 60, Chess.KING)
        place(board, 4, -Chess.KING)
        val drawn = game(board, halfmove = 99).play(60, 61)!!
        assertTrue(drawn.ended)
        assertEquals(0, drawn.winner)
        val withPawn = emptyBoard()
        place(withPawn, 60, Chess.KING)
        place(withPawn, 4, -Chess.KING)
        place(withPawn, 52, Chess.PAWN)
        val reset = game(withPawn, halfmove = 99).play(52, 44)!!
        assertFalse(reset.ended)
        assertEquals(0, reset.halfmove)
        val checking = emptyBoard()
        place(checking, 56, Chess.ROOK)
        place(checking, 63, Chess.KING)
        place(checking, 0, -Chess.KING)
        val checkedDraw = game(checking, halfmove = 99).play(56, 48)!!
        assertTrue(checkedDraw.ended)
        assertEquals(0, checkedDraw.winner)
        assertTrue(checkedDraw.inCheck())
    }

    @Test fun `the same position three times is a draw`() {
        val board = emptyBoard()
        place(board, 60, Chess.KING)
        place(board, 4, -Chess.KING)
        val start = game(board).let { it.copy(history = listOf(it.positionKey())) }
        val back = start.play(60, 61)!!.play(4, 5)!!.play(61, 60)!!.play(5, 4)!!
        assertFalse(back.ended)
        val third = back.play(60, 61)!!.play(4, 5)!!.play(61, 60)!!.play(5, 4)!!
        assertTrue(third.ended)
        assertEquals(0, third.winner)
    }

    @Test fun `computer returns a legal move at each strength`() {
        for (setting in Chess.NAMES.indices) {
            val g = Chess.start(setting.toLong() + 10, setting).play(52, 36)!! // e2-e4
            assertEquals(-1, g.turn)
            val move = ChessAi.choose(g)
            assertTrue(move in g.legalMoves(), "setting $setting move $move")
            assertNotNull(g.play(move))
        }
    }

    @Test fun `save codec round-trips and rejects junk`() {
        val g = Chess.start(9, 2).play(52, 36)!!.play(12, 28)!!
        assertEquals(g, ChessCodec.decode(ChessCodec.encode(g)))
        val oldSave = ChessCodec.encode(g).lineSequence().take(11).joinToString("\n")
        val loaded = ChessCodec.decode(oldSave)!!
        assertEquals(g.board, loaded.board)
        assertEquals(0, loaded.halfmove)
        assertTrue(loaded.history.isEmpty())
        assertNull(ChessCodec.decode("bad"))
        assertNull(Chess.start(1, 0).play(0, 1))
    }
}
