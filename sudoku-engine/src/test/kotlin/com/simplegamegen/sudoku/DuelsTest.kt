package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DuelsTest {
    private fun <T> timed(label: String, block: () -> T): T {
        val t = System.nanoTime(); return block().also { println("$label: ${(System.nanoTime() - t) / 1_000_000} ms") }
    }

    @Test fun `dots and boxes closes boxes, grants extra turns and ends with every edge`() {
        var g = DotsGame.start(1, 0)
        assertEquals(24, g.edgeCount)
        val box = g.boxEdges(0)
        g = g.draw(box[0])!!.draw(box[1])!!.draw(box[2])!!
        assertEquals(-1, g.turn)
        val closer = g.draw(box[3])!!
        assertEquals(-1, closer.owner[0]); assertEquals(-1, closer.turn, "closing a box keeps the turn")
        assertNull(closer.draw(box[3]))
        assertEquals(closer, DotsCodec.decode(DotsCodec.encode(closer)))
    }

    @Test fun `dots and boxes computer never gives boxes away while safe moves exist`() {
        for (setting in 1..3) {
            var g = DotsGame.start(3, setting)
            while (!g.over) {
                val safeExists = g.free.any { e -> g.boxesOf(e).none { g.sides(it) == 2 } } && g.free.none { e -> g.boxesOf(e).any { g.sides(it) == 3 } }
                val e = if (g.turn == -1) DotsAi.choose(g) else g.free.first()
                if (g.turn == -1 && safeExists && g.free.size > listOf(0, 0, 12, 16)[setting]) assertTrue(g.boxesOf(e).none { g.sides(it) == 2 })
                g = g.draw(e)!!
            }
            assertEquals(g.owner.size, g.score(1) + g.score(-1))
        }
    }

    @Test fun `dots and boxes expert beats a random player and searches the endgame quickly`() {
        var wins = 0
        for (seed in 1L..6L) {
            var g = DotsGame.start(seed, 3)
            val random = kotlin.random.Random(seed)
            while (!g.over) g = g.draw(if (g.turn == -1) DotsAi.choose(g) else g.free.random(random))!!
            if (g.score(-1) > g.score(1)) wins++
        }
        assertTrue(wins >= 5, "expert won $wins of 6")
        val late = generateSequence(DotsGame.start(2, 3)) { if (it.free.size > 16) it.draw(it.free.firstOrNull { e -> it.boxesOf(e).none { b -> it.sides(b) == 2 } }
            ?: it.free.first())!! else null }.last()
        timed("dots endgame ${late.free.size} edges") { DotsAi.choose(late.copy(turn = -1)) }
    }

    @Test fun `magnets snap clusters back to the placer and the last stone wins`() {
        var g = MagnetGame.start(1, 0)
        g = g.place(Stone(0f, 0f))!!
        assertEquals(listOf(7, 8), g.hands); assertEquals(-1, g.turn)
        assertNull(g.place(Stone(0.05f, 0f)), "stones can't overlap")
        assertNull(g.place(Stone(0.99f, 0f)), "stones stay inside the ring")
        g = g.place(Stone(0.5f, 0f))!!
        g = g.place(Stone(0.5f, 0.3f))!!
        // A stone between two others pulls both.
        val snap = g.place(Stone(0.5f, 0.15f))!!
        assertEquals(3, snap.snapped.size)
        assertEquals(g.hands[1] + 2, snap.hands[1])
        assertEquals(1, snap.stones.size)
        assertEquals(snap, MagnetCodec.decode(MagnetCodec.encode(snap)))
        var game = MagnetGame.start(5, 3)
        var turns = 0
        while (!game.over && turns < 400) { game = game.place(MagnetAi.choose(game))!!; turns++ }
        assertTrue(game.over); assertEquals(0, game.hand(game.winner))
    }

    @Test fun `sprouts routes avoid curves, spots gain degree and the last mover wins`() {
        var g = SproutsGame.start(1, 0)
        assertEquals(2, g.dots.size)
        val first = g.play(SproutMove(0, 1))!!
        assertEquals(3, first.dots.size); assertEquals(listOf(1, 1, 2), first.degree); assertEquals(-1, first.turn)
        assertNull(first.play(SproutMove(2, 2, Pt(0.5f, 0.5f))), "a spot with two ends can't loop")
        assertNull(g.play(SproutMove(0, 0)), "a loop needs a waypoint")
        // Two starting spots allow at most five moves.
        var moves = 0
        while (!g.over) { g = g.play(g.legalMoves().first())!!; moves++ }
        assertTrue(moves in 2..5, "played $moves")
        assertTrue(g.degree.all { it <= 3 })
        assertEquals(g, SproutsCodec.decode(SproutsCodec.encode(g)))
    }

    @Test fun `sprouts computer plays legal moves at every strength in reasonable time`() {
        for (setting in 0..3) {
            var g = SproutsGame.start(3, setting)
            var steps = 0
            timed("sprouts setting $setting game") {
                while (!g.over && steps < 3 * g.dots.size) {
                    val m = SproutsAi.choose(g)
                    g = requireNotNull(g.play(m)) { "illegal $m" }
                    steps++
                }
            }
            assertTrue(g.over)
            assertTrue(steps <= 3 * (setting + 2) - 1)
        }
    }
}
