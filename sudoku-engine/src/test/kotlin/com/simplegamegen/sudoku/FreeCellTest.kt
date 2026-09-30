package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.cards.FreeCellCodec
import com.simplegamegen.sudoku.cards.FreeCellGame
import com.simplegamegen.sudoku.cards.FreeCellMove
import com.simplegamegen.sudoku.cards.FreeCellPile
import com.simplegamegen.sudoku.cards.isRed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FreeCellTest {
    private fun card(suit: Int, rank: Int) = suit * 13 + rank - 1

    @Test fun `deal has 52 cards in eight columns`() {
        for (seed in 1L..30L) {
            val g = FreeCellGame.deal(seed)
            assertEquals(listOf(7, 7, 7, 7, 6, 6, 6, 6), g.columns.map { it.size })
            assertTrue(g.free.all { it == null })
            assertTrue(g.foundations.all { it == null })
            assertEquals(52, g.columns.flatten().toSet().size)
            assertEquals(g, FreeCellCodec.decode(FreeCellCodec.encode(g)))
        }
    }

    @Test fun `illegal move rejected`() {
        val g = FreeCellGame.deal(1)
        assertNull(g.move(FreeCellMove(FreeCellPile.TABLEAU, 0, FreeCellPile.FREE, 0, count = 2)))
        assertNull(g.move(FreeCellMove(FreeCellPile.FOUNDATION, 0, FreeCellPile.TABLEAU, 0)))
        val park = g.legalMoves().first { it.to == FreeCellPile.FREE }
        val occupied = g.move(park)!!
        val otherCol = (0..7).first { it != park.fromIndex || park.from != FreeCellPile.TABLEAU }
        assertFalse(occupied.canMove(FreeCellMove(FreeCellPile.TABLEAU, otherCol, FreeCellPile.FREE, park.toIndex)))
        for (from in 0..7) for (to in 0..7) if (from != to) {
            val a = g.columns[from].lastOrNull() ?: continue
            val b = g.columns[to].lastOrNull() ?: continue
            if (isRed(a) == isRed(b)) {
                assertFalse(g.canMove(FreeCellMove(FreeCellPile.TABLEAU, from, FreeCellPile.TABLEAU, to)))
            }
        }
    }

    @Test fun `legal foundation move works`() {
        val g = FreeCellGame(
            seed = 0,
            free = listOf(card(0, 1), null, null, null),
            foundations = listOf(null, null, null, null),
            columns = listOf(
                (2..13).map { card(0, it) },
                (1..13).map { card(1, it) },
                (1..13).map { card(2, it) },
                (1..13).map { card(3, it) },
                emptyList(), emptyList(), emptyList(), emptyList(),
            ),
        )
        val next = g.move(FreeCellMove(FreeCellPile.FREE, 0, FreeCellPile.FOUNDATION, 0))
        assertNotNull(next)
        assertEquals(card(0, 1), next!!.foundations[0])
        assertNull(next.free[0])
        val two = next.copy(
            free = listOf(card(0, 2), null, null, null),
            columns = next.columns.mapIndexed { i, c -> if (i == 0) c.drop(1) else c },
        )
        assertEquals(card(0, 2), two.move(FreeCellMove(FreeCellPile.FREE, 0, FreeCellPile.FOUNDATION, 0))!!.foundations[0])
    }

    @Test fun `save codec round-trip`() {
        var g = FreeCellGame.deal(42)
        g = g.move(g.legalMoves().first { it.to == FreeCellPile.FREE })!!
        assertEquals(g, FreeCellCodec.decode(FreeCellCodec.encode(g)))
        val bad = FreeCellCodec.encode(g).lines().toMutableList().also { it[0] = "9" }.joinToString("\n")
        assertNull(FreeCellCodec.decode(bad))
    }

    @Test fun `known short sequence wins`() {
        // Only Q♠ and K♠ remain off the foundations.
        val g = FreeCellGame(
            seed = 99,
            free = listOf(card(0, 12), card(0, 13), null, null),
            foundations = listOf(card(0, 11), card(1, 13), card(2, 13), card(3, 13)),
            columns = List(8) { emptyList() },
        )
        val q = g.move(FreeCellMove(FreeCellPile.FREE, 0, FreeCellPile.FOUNDATION, 0))!!
        val k = q.move(FreeCellMove(FreeCellPile.FREE, 1, FreeCellPile.FOUNDATION, 0))!!
        assertTrue(k.won)
        assertEquals(2, k.moves)
    }

    @Test fun `hint suggests a legal move`() {
        val g = FreeCellGame.deal(7)
        val h = g.hint()
        assertNotNull(h)
        assertTrue(g.canMove(h!!))
    }

    @Test fun `supermove needs free cells or empty columns`() {
        val run = listOf(card(1, 5), card(0, 4)) // ♥5, ♠4
        val parent = card(3, 6) // ♣6 — black, so ♥5 may land on it
        val under = card(1, 7) // ♥7 — same color as ♥5, so the movable run stops at 2
        val parked = listOf(card(0, 13), card(1, 13), card(2, 13), card(2, 12))
        val rest = (0..51).filter { it !in (run + parent + under + parked) }.toMutableList()
        assertEquals(44, rest.size)
        var at = 0
        fun take(n: Int) = rest.subList(at, at + n).toList().also { at += n }
        val cols = listOf(
            take(4) + under + run,
            take(6) + listOf(parent),
            take(6), take(6), take(6), take(6), take(5), take(5),
        )
        val packed = FreeCellGame(0, parked, List(4) { null }, cols)
        assertEquals(2, packed.runLength(0))
        assertEquals(1, packed.moveLimit(false)) // all free cells occupied, no empty columns
        assertFalse(packed.canMove(FreeCellMove(FreeCellPile.TABLEAU, 0, FreeCellPile.TABLEAU, 1, 2)))

        // Free one helper cell; put its card on column 7 so the deck stays whole.
        val helpers = packed.copy(
            free = listOf(null) + packed.free.drop(1),
            columns = packed.columns.mapIndexed { i, c -> if (i == 7) c + packed.free[0]!! else c },
        )
        assertEquals(2, helpers.moveLimit(false))
        assertTrue(helpers.canMove(FreeCellMove(FreeCellPile.TABLEAU, 0, FreeCellPile.TABLEAU, 1, 2)))
        val moved = helpers.move(FreeCellMove(FreeCellPile.TABLEAU, 0, FreeCellPile.TABLEAU, 1, 2))!!
        assertEquals(listOf(parent) + run, moved.columns[1].takeLast(3))
    }
}
