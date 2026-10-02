package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.tabletop.Shogi
import com.simplegamegen.sudoku.tabletop.ShogiAi
import com.simplegamegen.sudoku.tabletop.ShogiCodec
import com.simplegamegen.sudoku.tabletop.ShogiMove
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShogiTest {
    @Test fun `a pawn steps forward and captures straight ahead`() {
        val start = Shogi(setting = 0, seed = 1)
        val from = 6 * 9 + 4
        val to = 5 * 9 + 4
        assertTrue(ShogiMove(from, to) in start.legalMoves())
        val moved = start.play(ShogiMove(from, to))
        assertEquals(Shogi.PAWN, moved!!.board[to])
        val blocked = moved.copy(
            board = moved.board.toMutableList().also { it[4 * 9 + 4] = -Shogi.PAWN },
            turn = 1,
        )
        assertTrue(ShogiMove(to, 4 * 9 + 4) in blocked.legalMoves())
        val taken = blocked.play(ShogiMove(to, 4 * 9 + 4))
        assertEquals(listOf(Shogi.PAWN), taken!!.senteHand)
        assertEquals(Shogi.PAWN, taken.board[4 * 9 + 4])
    }

    @Test fun `drops obey the file, the edge, and pawn mate`() {
        val board = MutableList(81) { 0 }
        board[8 * 9 + 4] = Shogi.KING
        board[0 * 9 + 4] = -Shogi.KING
        board[6 * 9 + 0] = Shogi.PAWN
        val g = Shogi(setting = 0, seed = 2, board = board, senteHand = listOf(Shogi.PAWN, Shogi.LANCE, Shogi.KNIGHT, Shogi.GOLD))
        assertTrue(g.legalMoves().none { it.drop == Shogi.PAWN && it.to % 9 == 0 })
        assertTrue(g.legalMoves().none { it.drop == Shogi.PAWN && it.to / 9 == 0 })
        assertTrue(g.legalMoves().none { it.drop == Shogi.LANCE && it.to / 9 == 0 })
        assertTrue(g.legalMoves().none { it.drop == Shogi.KNIGHT && it.to / 9 <= 1 })
        assertNotNull(g.play(ShogiMove(-1, 4 * 9 + 3, drop = Shogi.GOLD)))
        val mate = Shogi(
            setting = 0, seed = 3,
            board = MutableList(81) { 0 }.also {
                it[1 * 9 + 0] = -Shogi.KING
                it[0] = -Shogi.PAWN
                it[1] = -Shogi.PAWN
                it[1 * 9 + 1] = -Shogi.PAWN
                it[2 * 9 + 1] = -Shogi.PAWN
                it[3 * 9 + 1] = Shogi.GOLD
                it[8 * 9 + 8] = Shogi.KING
            },
            senteHand = listOf(Shogi.PAWN, Shogi.GOLD),
        )
        assertTrue(mate.legalMoves().none { it.drop == Shogi.PAWN && it.to == 2 * 9 })
        assertNotNull(mate.play(ShogiMove(-1, 2 * 9, drop = Shogi.GOLD)))
    }

    @Test fun `a pawn on the last rank has to promote and a silver may choose`() {
        val board = MutableList(81) { 0 }
        board[8 * 9 + 8] = Shogi.KING
        board[0 * 9 + 8] = -Shogi.KING
        board[1 * 9 + 4] = Shogi.PAWN
        board[2 * 9 + 2] = Shogi.SILVER
        val g = Shogi(setting = 0, seed = 4, board = board)
        val pawn = g.legalMoves().filter { it.from == 1 * 9 + 4 }
        assertEquals(listOf(true), pawn.map { it.promote })
        val silver = g.legalMoves().filter { it.from == 2 * 9 + 2 && it.to == 1 * 9 + 2 }
        assertEquals(setOf(true, false), silver.map { it.promote }.toSet())
        val promoted = g.play(pawn.single())
        assertEquals(Shogi.PAWN + Shogi.PROMOTED, promoted!!.board[4])
    }

    @Test fun `a dragon steps diagonally and the computer stays legal`() {
        val board = MutableList(81) { 0 }
        board[4 * 9 + 4] = Shogi.ROOK + Shogi.PROMOTED
        board[8 * 9 + 0] = Shogi.KING
        board[0 * 9 + 8] = -Shogi.KING
        val g = Shogi(setting = 0, seed = 5, board = board)
        assertTrue(ShogiMove(4 * 9 + 4, 3 * 9 + 3) in g.legalMoves())
        var live = Shogi(setting = 3, seed = 9)
        repeat(2) {
            val move = ShogiAi.choose(live)
            live = live.play(move)!!
            if (!live.ended) {
                val reply = ShogiAi.choose(live)
                live = live.play(reply)!!
            }
        }
        assertEquals(live, ShogiCodec.decode(ShogiCodec.encode(live)))
    }

    @Test fun `four repeats draw, and checking on every move of the repeat loses`() {
        val quiet = MutableList(81) { 0 }
        quiet[8 * 9 + 8] = Shogi.KING
        quiet[0] = -Shogi.KING
        quiet[4 * 9 + 4] = Shogi.GOLD
        quiet[2 * 9 + 8] = -Shogi.GOLD
        var game = Shogi(setting = 0, seed = 6, board = quiet)
        val cycle = listOf(
            ShogiMove(4 * 9 + 4, 4 * 9 + 5),
            ShogiMove(2 * 9 + 8, 2 * 9 + 7),
            ShogiMove(4 * 9 + 5, 4 * 9 + 4),
            ShogiMove(2 * 9 + 7, 2 * 9 + 8),
        )
        var guard = 0
        while (!game.ended && guard < 32) game = game.play(cycle[guard++ % cycle.size])!!
        assertTrue(game.ended)
        assertEquals(0, game.winner)

        val checked = MutableList(81) { 0 }
        checked[8 * 9 + 8] = Shogi.KING
        checked[0 * 9 + 4] = -Shogi.KING
        checked[2 * 9 + 4] = Shogi.ROOK
        var hunt = Shogi(setting = 0, seed = 7, board = checked, turn = -1)
        val chase = listOf(
            ShogiMove(0 * 9 + 4, 0 * 9 + 3),
            ShogiMove(2 * 9 + 4, 2 * 9 + 3),
            ShogiMove(0 * 9 + 3, 0 * 9 + 4),
            ShogiMove(2 * 9 + 3, 2 * 9 + 4),
        )
        var chaseGuard = 0
        while (!hunt.ended && chaseGuard < 32) hunt = hunt.play(chase[chaseGuard++ % chase.size])!!
        assertTrue(hunt.ended)
        assertEquals(-1, hunt.winner)
    }

    @Test fun `counting pieces when both kings are in camp uses 5 and 1`() {
        val board = MutableList(81) { 0 }
        board[1 * 9 + 4] = Shogi.KING
        board[7 * 9 + 4] = -Shogi.KING
        val draw = Shogi(
            setting = 0, seed = 8, board = board,
            senteHand = listOf(Shogi.ROOK, Shogi.BISHOP) + List(14) { Shogi.PAWN },
            goteHand = listOf(Shogi.ROOK, Shogi.BISHOP) + List(14) { Shogi.PAWN },
        )
        assertTrue(draw.canImpasse())
        assertEquals(0, draw.declareImpasse()!!.winner)
        val short = draw.copy(senteHand = listOf(Shogi.PAWN))
        assertEquals(-1, short.declareImpasse()!!.winner)
    }

    @Test fun `an illegal move is refused`() {
        val start = Shogi(setting = 0, seed = 1)
        assertNull(start.play(ShogiMove(6 * 9 + 4, 6 * 9 + 5)))
        assertFalse(start.legalMoves().any { it.drop != 0 })
    }
}
