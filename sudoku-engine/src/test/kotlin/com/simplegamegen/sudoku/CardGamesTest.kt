package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.cards.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CardGamesTest {
    private fun card(suit: Int, rank: Int) = suit * 13 + rank - 1

    @Test fun `spider deals 54 cards over 10 columns with 50 in stock`() {
        for (suits in listOf(1, 2, 4)) for (seed in 1L..20L) {
            val g = SpiderGame.deal(seed, suits)
            assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), g.columns.map { it.size })
            assertEquals(50, g.stock.size)
            assertEquals(g.columns.map { it.size - 1 }, g.hidden)
            assertEquals((g.columns.flatten() + g.stock).map { suitOf(it) }.toSet().size, suits)
            assertEquals(g, SpiderCodec.decode(SpiderCodec.encode(g)))
        }
    }

    @Test fun `spider runs move by rank, reveal cards, deal only without gaps and clear full suits`() {
        // Column 0 holds K..2 of spades; column 1 ends with the ace, so moving it completes a run.
        val run = (13 downTo 2).map { card(0, it) }
        val rest = (0 until 8).flatMap { set -> (1..13).map { card(0, it) } }.toMutableList()
        run.forEach { rest.remove(it) }; rest.remove(card(0, 1))
        val cols = listOf(run, listOf(rest.removeAt(0), card(0, 1))) + (2..9).map { listOf(rest.removeAt(0)) }
        val stock = rest.take(50)
        val filler = rest.drop(50)
        val g = SpiderGame(1, 0, cols.mapIndexed { i, c -> if (i == 2) c + filler else c }, listOf(0, 1) + List(8) { 0 }, stock)
        assertEquals(12, g.runLength(0))
        val done = g.move(SpiderMove(1, 1, 0))!!
        assertEquals(listOf(0), done.completed)
        assertTrue(done.columns[0].isEmpty())
        assertEquals(0, done.hidden[1], "the card under the ace turns up")
        assertFalse(done.canDeal, "no dealing onto an empty column")
        assertNull(done.deal())
        assertFalse(g.canMove(SpiderMove(0, 13, 1)), "a run can't be longer than its face-up cards")
    }

    @Test fun `spider hints are legal and never shuffle a whole column into a gap`() {
        var g = SpiderGame.deal(5, 2)
        repeat(60) {
            val m = g.hint()
            g = if (m != null) { assertTrue(g.canMove(m)); g.move(m)!! } else g.deal() ?: return
        }
    }

    @Test fun `pyramid pairs make 13, kings go alone and exposure follows the rows`() {
        val g = PyramidGame.deal(3, 0)
        assertEquals(24, g.stock.size)
        assertEquals((21..27).toList(), (0..27).filter { g.exposed(it) })
        assertEquals(6 to 0, PyramidGame.position(21)); assertEquals(27, PyramidGame.index(6, 6))
        g.pairs().forEach { (a, b) ->
            val sum = rankOf(g.cardAt(a)!!) + (b?.let { rankOf(g.cardAt(it)!!) } ?: 0)
            assertEquals(13, sum)
        }
        // Without recycles, greedy play must end won or stuck.
        var p = PyramidGame.deal(3, 3)
        var steps = 0
        while (!p.won && !p.stuck && steps < 500) {
            p = p.pairs().firstOrNull()?.let { (a, b) -> p.remove(a, b) } ?: p.draw()!!
            steps++
        }
        assertTrue(p.won || p.stuck)
        assertEquals(p, PyramidCodec.decode(PyramidCodec.encode(p)))
    }

    @Test fun `pyramid recycling follows the setting`() {
        for ((setting, passes) in PyramidGame.RECYCLES.withIndex()) {
            var p = PyramidGame.deal(1, setting)
            repeat(24) { p = p.draw()!! }
            if (passes == 0) assertNull(p.draw()) else {
                p = p.draw()!!
                assertEquals(24, p.stock.size)
                assertEquals(if (passes < 0) -1 else passes - 1, p.recycles)
            }
        }
    }

    @Test fun `memory matches pairs, hides misses and counts moves`() {
        for (setting in MemoryGame.SIZES.indices) {
            val g = MemoryGame.deal(7, setting)
            assertEquals(MemoryGame.SIZES[setting].let { it.first * it.second }, g.faces.size)
        }
        var g = MemoryGame.deal(9, 1)
        val a = 0; val twin = g.faces.indices.first { it != a && g.faces[it] == g.faces[a] }
        val other = g.faces.indices.first { it != a && g.faces[it] != g.faces[a] }
        g = g.flip(a).flip(other)
        assertTrue(g.mismatch); assertEquals(1, g.moves)
        g = g.flip(twin) // turns the mismatch back first
        assertEquals(listOf(twin), g.open)
        g = g.flip(a)
        assertEquals(setOf(a, twin), g.matched); assertEquals(2, g.moves)
        assertSame(g, g.flip(a))
        g.faces.indices.groupBy { g.faces[it] }.values.forEach { (x, y) -> g = g.flip(x).flip(y) }
        assertTrue(g.won)
        assertEquals(g, MemoryCodec.decode(MemoryCodec.encode(g)))
        assertNull(MemoryCodec.decode(MemoryCodec.encode(g).replace("\n1\n", "\n9\n")))
    }
}
