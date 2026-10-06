package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.tabletop.RaumMove
import com.simplegamegen.sudoku.tabletop.Raumschach
import com.simplegamegen.sudoku.tabletop.RaumschachAi
import com.simplegamegen.sudoku.tabletop.RaumschachCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RaumschachTest {
    @Test fun `opening matches Maack's array and White moves first`() {
        val g = Raumschach.start(1, 0)
        assertEquals(20, g.board.count { it > 0 })
        assertEquals(20, g.board.count { it < 0 })
        assertEquals(Raumschach.KING, g.board[Raumschach.idx(0, 0, 2)])
        assertEquals(Raumschach.UNICORN, g.board[Raumschach.idx(1, 0, 1)])
        assertEquals(Raumschach.UNICORN, g.board[Raumschach.idx(1, 0, 4)])
        assertEquals(-Raumschach.UNICORN, g.board[Raumschach.idx(3, 4, 0)])
        assertEquals(-Raumschach.UNICORN, g.board[Raumschach.idx(3, 4, 3)])
        assertEquals(-Raumschach.KING, g.board[Raumschach.idx(4, 4, 2)])
        assertEquals("Bb1", Raumschach.name(Raumschach.idx(1, 0, 1)))
        assertEquals("Da5", Raumschach.name(Raumschach.idx(3, 4, 0)))
        assertFalse(g.inCheck())
        assertTrue(g.legalMoves().isNotEmpty())
        assertTrue(g.movesFrom(Raumschach.idx(0, 0, 2)).isEmpty(), "the king starts behind its own men")
    }

    @Test fun `pieces from the center match the three axes`() {
        val center = Raumschach.idx(2, 2, 2)
        fun lone(kind: Int) = Raumschach(0, 1, MutableList(125) { 0 }.also {
            it[center] = kind
            it[Raumschach.idx(0, 0, 0)] = -Raumschach.KING
            it[Raumschach.idx(4, 4, 4)] = Raumschach.KING
        })
        assertEquals(12, lone(Raumschach.ROOK).movesFrom(center).size)
        assertEquals(24, lone(Raumschach.BISHOP).movesFrom(center).size)
        assertEquals(16, lone(Raumschach.UNICORN).movesFrom(center).size)
        assertEquals(52, lone(Raumschach.QUEEN).movesFrom(center).size)
        assertEquals(26, lone(Raumschach.KING).movesFrom(center).size)
        assertEquals(24, lone(Raumschach.KNIGHT).movesFrom(center).size)
    }

    @Test fun `a pawn moves rookwise and captures five bishopwise squares`() {
        val pawn = Raumschach.idx(2, 2, 2)
        val board = MutableList(125) { 0 }
        board[pawn] = Raumschach.PAWN
        board[Raumschach.idx(4, 4, 4)] = Raumschach.KING
        board[Raumschach.idx(0, 0, 0)] = -Raumschach.KING
        val quiet = listOf(Raumschach.idx(2, 3, 2), Raumschach.idx(3, 2, 2))
        val caps = listOf(
            Raumschach.idx(2, 3, 1), Raumschach.idx(2, 3, 3),
            Raumschach.idx(3, 2, 1), Raumschach.idx(3, 3, 2), Raumschach.idx(3, 2, 3),
        )
        val tri = listOf(Raumschach.idx(3, 3, 1), Raumschach.idx(3, 3, 3))
        for (i in caps + tri) board[i] = -Raumschach.PAWN
        val g = Raumschach(0, 1, board)
        val tos = g.movesFrom(pawn).map { it.to }.toSet()
        assertEquals((quiet + caps).toSet(), tos)
        assertTrue(tri.none { it in tos })
        val stepped = g.play(pawn, quiet[0])!!
        assertEquals(Raumschach.PAWN, stepped.board[quiet[0]])
        assertFalse(stepped.board.any { it == Raumschach.QUEEN })
    }

    @Test fun `unicorns are blocked and pawns promote to a queen on E5`() {
        val u = Raumschach.idx(0, 0, 0)
        val board = MutableList(125) { 0 }
        board[u] = Raumschach.UNICORN
        board[Raumschach.idx(2, 2, 2)] = -Raumschach.PAWN
        board[Raumschach.idx(4, 4, 3)] = Raumschach.KING
        board[Raumschach.idx(4, 3, 4)] = -Raumschach.KING
        val g = Raumschach(0, 1, board)
        val far = Raumschach.idx(4, 4, 4)
        assertTrue(g.movesFrom(u).none { it.to == far }, "the pawn on Cc3 blocks Ee5")
        assertTrue(g.movesFrom(u).any { it.to == Raumschach.idx(2, 2, 2) })

        val pawn = Raumschach.idx(4, 3, 2)
        val promo = board.toMutableList()
        promo[u] = 0
        promo[Raumschach.idx(2, 2, 2)] = 0
        promo[pawn] = Raumschach.PAWN
        val after = Raumschach(0, 1, promo).play(pawn, Raumschach.idx(4, 4, 2))!!
        assertEquals(Raumschach.QUEEN, after.board[Raumschach.idx(4, 4, 2)])
    }

    @Test fun `a corner king dies to a protected unicorn-step queen`() {
        val board = MutableList(125) { 0 }
        board[Raumschach.idx(0, 0, 0)] = -Raumschach.KING
        board[Raumschach.idx(1, 1, 2)] = Raumschach.QUEEN
        board[Raumschach.idx(2, 2, 2)] = Raumschach.KING
        val before = Raumschach(0, 1, board, turn = 1)
        val from = Raumschach.idx(1, 1, 2)
        val to = Raumschach.idx(1, 1, 1)
        assertTrue(before.movesFrom(from).any { it.to == to })
        val after = before.play(from, to)!!
        assertTrue(after.ended)
        assertEquals(1, after.winner)
        assertTrue(after.inCheck())
        assertTrue(after.legalMoves().isEmpty())
    }

    @Test fun `stalemate and repetition are draws and saves round-trip`() {
        val board = MutableList(125) { 0 }
        board[Raumschach.idx(0, 0, 0)] = -Raumschach.KING
        for (d in listOf(
            Triple(1, 0, 0), Triple(0, 1, 0), Triple(0, 0, 1),
            Triple(1, 1, 0), Triple(1, 0, 1), Triple(0, 1, 1), Triple(1, 1, 1),
        )) board[Raumschach.idx(d.third, d.second, d.first)] = -Raumschach.PAWN
        board[Raumschach.idx(4, 4, 4)] = Raumschach.KING
        board[Raumschach.idx(2, 2, 2)] = Raumschach.PAWN
        val g = Raumschach(0, 3, board)
        val moved = g.play(Raumschach.idx(2, 2, 2), Raumschach.idx(2, 3, 2))!!
        assertTrue(moved.ended && moved.draw)

        var live = Raumschach.start(9, 2)
        assertEquals(live, RaumschachCodec.decode(RaumschachCodec.encode(live)))
        live = live.play(live.legalMoves().first())!!
        assertEquals(live, RaumschachCodec.decode(RaumschachCodec.encode(live)))
        assertNull(RaumschachCodec.decode("bad"))
    }

    @Test fun `the computer only plays a legal move`() {
        var g = Raumschach.start(4, 0)
        repeat(6) {
            if (g.ended) return
            val move = if (g.turn == 1) g.legalMoves().first() else RaumschachAi.choose(g)
            assertTrue(move in g.legalMoves())
            g = g.play(move)!!
            assertFalse(g.inCheck(-g.turn))
        }
        for (setting in 1..3) {
            val opening = Raumschach.start(setting.toLong(), setting)
            val move = RaumschachAi.choose(opening)
            assertTrue(move in opening.legalMoves(), "setting $setting")
        }
    }

    @Test fun `a short game never leaves the mover in check`() {
        var g = Raumschach.start(2, 0)
        val random = Random(2)
        repeat(25) {
            if (g.ended) return
            val move = g.legalMoves().random(random)
            g = g.play(move)!!
            assertFalse(g.inCheck(-g.turn))
            assertEquals(1, g.board.count { it == Raumschach.KING })
            assertEquals(1, g.board.count { it == -Raumschach.KING })
        }
    }
}
