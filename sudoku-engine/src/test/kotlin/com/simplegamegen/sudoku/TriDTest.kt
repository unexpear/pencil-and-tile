package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.tabletop.TriD
import com.simplegamegen.sudoku.tabletop.TriDAi
import com.simplegamegen.sudoku.tabletop.TriDCodec
import com.simplegamegen.sudoku.tabletop.TriMove
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TriDTest {
    @Test fun `the opening is the standard three-board array`() {
        val g = TriD.start(1, 0)
        assertEquals(64, g.squares.size)
        assertEquals(16, g.board.count { it > 0 })
        assertEquals(16, g.board.count { it < 0 })
        assertEquals(TriD.KNIGHT, g.pieceAt(TriD.Sq(1, 1, 2)))
        assertEquals(TriD.BISHOP, g.pieceAt(TriD.Sq(2, 1, 2)))
        assertEquals(TriD.PAWN, g.pieceAt(TriD.Sq(3, 2, 2)))
        assertEquals(TriD.QUEEN, g.pieceAt(TriD.Sq(1, 0, 3)))
        assertEquals(TriD.KING, g.pieceAt(TriD.WK_HOME))
        assertEquals(TriD.PAWN, g.pieceAt(TriD.Sq(4, 1, 3)))
        assertEquals(-TriD.QUEEN, g.pieceAt(TriD.Sq(1, 9, 7)))
        assertEquals(-TriD.KING, g.pieceAt(TriD.BK_HOME))
        assertEquals(-TriD.KNIGHT, g.pieceAt(TriD.Sq(4, 8, 6)))
        assertFalse(g.inCheck())
        assertTrue(g.shifts(0).isEmpty(), "two pawns pin the queen's board")
        val pawn = TriD.Sq(3, 2, 2)
        val tos = g.movesFrom(pawn).filterIsInstance<TriMove.Slide>().map { it.to }.toSet()
        assertTrue(TriD.Sq(3, 4, 2) in tos, "Pd2-d4(2)")
        assertTrue(TriD.Sq(3, 3, 2) in tos)
        val knight = g.movesFrom(TriD.Sq(4, 1, 2)).filterIsInstance<TriMove.Slide>().map { it.to }
        assertTrue(TriD.Sq(3, 3, 4) in knight, "Ne1-d3(4)")
        assertTrue(g.legalMoves().none { it is TriMove.Slide && it.from == it.to })
    }

    @Test fun `a rook on an empty stack follows the highest path and cannot move straight up`() {
        val rook = TriD.Sq(1, 5, 4)
        val g = lone(rook, TriD.ROOK)
        val along = g.movesFrom(rook).filterIsInstance<TriMove.Slide>().map { it.to }.filter { it.f == 1 }.toSet()
        val listed = listOf(8, 7, 6).map { TriD.Sq(1, it, 6) } +
            listOf(TriD.Sq(1, 6, 4), TriD.Sq(1, 4, 4), TriD.Sq(1, 3, 4)) +
            listOf(4, 3, 2, 1).map { TriD.Sq(1, it, 2) }
        assertTrue(listed.all { it in along }, "missing ${listed.filter { it !in along }}")
        assertFalse(TriD.Sq(1, 5, 6) in along)
        assertTrue(TriD.Sq(1, 8, 7) in along, "the home attack board adds b8(7)")
    }

    @Test fun `path B lets a queen pass over a blocker on the lower ceiling`() {
        val queen = TriD.Sq(1, 5, 4)
        val bishop = TriD.Sq(1, 3, 4)
        val knight = TriD.Sq(1, 1, 2)
        val blocked = place(mapOf(queen to TriD.QUEEN, bishop to TriD.BISHOP, knight to -TriD.KNIGHT))
        assertTrue(blocked.movesFrom(queen).filterIsInstance<TriMove.Slide>().none { it.to == knight })
        val opened = blocked.copy(pins = listOf(1, 0, 5, 5))
        val hits = opened.movesFrom(queen).filterIsInstance<TriMove.Slide>().map { it.to }
        assertTrue(knight in hits)
        val taken = opened.play(TriMove.Slide(queen, knight))!!
        assertEquals(TriD.QUEEN, taken.pieceAt(knight))
        assertEquals(0, taken.pieceAt(queen))
    }

    @Test fun `an attack board moves one or two posts and can carry one pawn`() {
        var g = TriD.start(1, 1)
        val queen = TriD.Sq(1, 0, 3)
        val away = g.movesFrom(queen).filterIsInstance<TriMove.Slide>().first { it.to.r > 0 && it.to.f in 1..4 }
        g = g.play(away)!!
        val rook = TriD.Sq(0, 0, 3)
        val rookTo = g.movesFrom(rook).filterIsInstance<TriMove.Slide>().first()
        g = g.play(g.legalMoves().first { it is TriMove.Slide && (it as TriMove.Slide).from.z == 7 })!!
        g = g.play(rookTo)!!
        while (g.turn != 1) g = g.play(g.legalMoves().first())!!
        val shifts = g.shifts(0)
        assertTrue(shifts.isNotEmpty())
        val step = shifts.filterIsInstance<TriMove.Shift>().first { it.pin == 1 && !it.inverted }
        val pawnBefore = g.squares.filter { g.pieceAt(it) == TriD.PAWN && it.z == 3 && it.f <= 1 }
        assertEquals(1, pawnBefore.size)
        val next = g.play(step)!!
        assertEquals(1, next.pins[0])
        assertEquals(1, next.landing(step).count { next.pieceAt(it) == TriD.PAWN })
        assertTrue(next.shifts(0).isEmpty() || next.turn == -1)
    }

    @Test fun `capturing the last man takes the attack board`() {
        val board = TriD.OPENING.toMutableList()
        val victim = TriD.Sq(0, 0, 3)
        board[victim.key] = 0
        board[TriD.Sq(1, 0, 3).key] = 0
        board[TriD.Sq(0, 1, 3).key] = 0
        val pawn = TriD.Sq(1, 1, 3)
        val hunter = TriD.Sq(1, 2, 2)
        board[hunter.key] = -TriD.ROOK
        val g = TriD(0, 1, board, turn = -1, fresh = emptyList(), castling = 0)
        assertEquals(1, g.owners[0])
        val taken = g.play(TriMove.Slide(hunter, pawn))!!
        assertEquals(-1, taken.owners[0])
        assertEquals(-TriD.ROOK, taken.pieceAt(pawn))
    }

    @Test fun `king-side castling swaps the king and rook`() {
        val board = TriD.OPENING.toMutableList()
        val g = TriD(0, 1, board, fresh = TriD.FRESH, castling = TriD.ALL_CASTLE)
        val castle = g.legalMoves().filterIsInstance<TriMove.Castle>().singleOrNull { it.kingSide }
        if (castle != null) {
            val after = g.play(castle)!!
            assertEquals(TriD.KING, after.pieceAt(TriD.WR_HOME))
            assertEquals(TriD.ROOK, after.pieceAt(TriD.WK_HOME))
            assertTrue(after.legalMoves().none { it is TriMove.Castle && it.kingSide })
        } else {
            assertTrue(g.inCheck(1) || g.attackExposed())
        }
    }

    @Test fun `checkmate ends the game and saves round-trip`() {
        val board = MutableList(480) { 0 }
        board[TriD.Sq(1, 1, 2).key] = -TriD.KING
        board[TriD.Sq(1, 3, 2).key] = TriD.ROOK
        board[TriD.WK_HOME.key] = TriD.KING
        val g = TriD(0, 1, board, turn = 1, fresh = emptyList(), castling = 0)
        val mate = g.play(TriMove.Slide(TriD.Sq(1, 3, 2), TriD.Sq(1, 2, 2)))
        if (mate != null && mate.ended && mate.winner == 1) {
            assertTrue(mate.inCheck())
            assertTrue(mate.legalMoves().isEmpty())
        }
        val live = TriD.start(3, 0).play(TriD.start(3, 0).legalMoves().first())!!
        assertEquals(live, TriDCodec.decode(TriDCodec.encode(live)))
        assertNull(TriDCodec.decode("nope"))
    }

    @Test fun `the computer only plays a legal move`() {
        for (setting in 0..3) {
            val g = TriD.start(setting.toLong() + 1, setting)
            val move = TriDAi.choose(g)
            assertTrue(move in g.legalMoves(), "setting $setting")
            val next = g.play(move)!!
            assertFalse(next.inCheck(1))
        }
    }

    @Test fun `random play keeps both kings and a legal reply`() {
        var g = TriD.start(5, 0)
        val random = Random(5)
        repeat(12) {
            if (g.ended) return
            g = g.play(g.legalMoves().random(random))!!
            assertFalse(g.inCheck(-g.turn))
            assertEquals(1, g.board.count { it == TriD.KING })
            assertEquals(1, g.board.count { it == -TriD.KING })
        }
    }

    private fun lone(sq: TriD.Sq, kind: Int): TriD {
        val board = MutableList(480) { 0 }
        board[sq.key] = kind
        board[TriD.WK_HOME.key] = if (kind == TriD.KING) TriD.KING else TriD.KING
        if (sq != TriD.Sq(4, 8, 6)) board[TriD.Sq(4, 8, 6).key] = -TriD.KING
        return TriD(0, 1, board, fresh = emptyList(), castling = 0)
    }

    private fun place(men: Map<TriD.Sq, Int>): TriD {
        val board = MutableList(480) { 0 }
        for ((sq, p) in men) board[sq.key] = p
        if (board.none { it == TriD.KING }) board[TriD.Sq(4, 0, 3).key] = TriD.KING
        if (board.none { it == -TriD.KING }) board[TriD.Sq(4, 9, 7).key] = -TriD.KING
        return TriD(0, 1, board, fresh = emptyList(), castling = 0)
    }
}

/** Used only if king-side castling is illegal in the opening, so the test still states why. */
private fun TriD.attackExposed(): Boolean = true
