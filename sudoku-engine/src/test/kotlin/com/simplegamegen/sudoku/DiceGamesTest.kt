package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.ShipAi
import com.simplegamegen.sudoku.duels.ShipCrew
import com.simplegamegen.sudoku.duels.ShipCrewCodec
import com.simplegamegen.sudoku.duels.ShutAi
import com.simplegamegen.sudoku.duels.ShutBox
import com.simplegamegen.sudoku.duels.ShutBoxCodec
import com.simplegamegen.sudoku.duels.TenAi
import com.simplegamegen.sudoku.duels.TenThousand
import com.simplegamegen.sudoku.duels.TenThousandCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiceGamesTest {
    @Test fun `shut the box only flips a set that adds up to the dice`() {
        val up = List(9) { true }
        val ways = ShutBox.options(up, 12)
        assertTrue(ways.all { it.sum() == 12 && it.distinct() == it })
        assertTrue(listOf(3, 9) in ways && listOf(4, 8) in ways && listOf(1, 2, 9) in ways)
        assertTrue(ways.none { 6 in it && it.count { tile -> tile == 6 } > 1 })
        val box = ShutBox(0, 1L, 2, up, up, listOf(6, 6))
        assertNull(box.cover(listOf(9)))
        assertNull(box.cover(listOf(6, 6)))
        val covered = box.cover(listOf(9, 3))!!
        assertTrue(!covered.youUp[8] && !covered.youUp[2] && covered.youUp[0])
        val low = up.toMutableList().also { it[6] = false; it[7] = false; it[8] = false }
        assertEquals(1, ShutBox.diceNeeded(low))
        assertEquals(2, ShutBox.diceNeeded(up))
    }

    @Test fun `a roll that matches nothing scores the tiles still up`() {
        val up = List(9) { false }.toMutableList().also { it[6] = true }
        val box = ShutBox(0, 1L, 2, up, List(9) { true }, listOf(2, 2), youScore = -1)
        assertTrue(box.options().isEmpty())
        val taken = box.take()!!
        assertEquals(7, taken.youScore)
        assertEquals(-1, taken.turn)
        val shut = ShutBox(0, 3L, 4, listOf(true, false, false, false, false, false, false, false, false), List(9) { true }, listOf(1))
        val closed = shut.cover(listOf(1))!!
        assertEquals(0, closed.youScore)
        assertEquals(-1, closed.turn)
    }

    @Test fun `the lower shut-the-box score wins and a save round-trips`() {
        val you = List(9) { false }.toMutableList().also { it[0] = true; it[1] = true }
        val cpu = List(9) { false }.toMutableList().also { it[2] = true }
        val done = ShutBox(1, 9L, 6, you, cpu, listOf(3), youScore = 3, cpuScore = 3, turn = 1, ended = true)
        assertEquals(0, done.winner)
        val better = done.copy(cpuScore = 8, cpuUp = List(9) { false }.toMutableList().also { it[7] = true })
        assertEquals(1, better.winner)
        val g = ShutBox.start(4, 2).cover(ShutBox.start(4, 2).options().first())!!
        assertEquals(g, ShutBoxCodec.decode(ShutBoxCodec.encode(g)))
        assertNull(ShutBoxCodec.decode("no"))
    }

    @Test fun `ten thousand does not score a die twice and specials replace face scores`() {
        assertEquals(1000, TenThousand.pointsOf(listOf(1, 1, 1)))
        assertEquals(2000, TenThousand.pointsOf(listOf(1, 1, 1, 1)))
        assertEquals(1000, TenThousand.pointsOf(listOf(5, 5, 5, 5)))
        assertEquals(100, TenThousand.pointsOf(listOf(5, 5)))
        assertEquals(500, TenThousand.pointsOf(listOf(5, 5, 5)))
        assertNull(TenThousand.pointsOf(listOf(2, 2, 2, 3)))
        assertNull(TenThousand.pointsOf(listOf(2, 3, 4, 6)))
        assertEquals(150, TenThousand.pointsOf(listOf(1, 5)))
        assertEquals(1500, TenThousand.pointsOf(listOf(1, 2, 3, 4, 5, 6)))
        assertEquals(1500, TenThousand.pointsOf(listOf(1, 1, 4, 4, 6, 6)))
        assertEquals(2500, TenThousand.pointsOf(listOf(2, 2, 2, 5, 5, 5)))
        assertEquals(400, TenThousand.pointsOf(listOf(2, 2, 2, 1, 1)))
        assertEquals(8000, TenThousand.pointsOf(List(6) { 1 }))
    }

    @Test fun `the first bank needs 500 and a useless roll loses the turn`() {
        val game = TenThousand.start(1, 0)
        val small = game.copy(pending = 100, you = 0, fresh = false, live = listOf(2, 3))
        assertNull(small.bank())
        val open = small.copy(pending = 500, live = emptyList())
        val banked = open.bank()!!
        assertEquals(500, banked.you)
        assertEquals(-1, banked.turn)
        val hot = game.copy(pending = 1000, mustRoll = true, you = 0)
        assertNull(hot.bank())
        val bust = game.copy(live = listOf(2, 3, 4, 6, 2, 3), fresh = true, pending = 400, you = 200)
        val lost = bust.bust()
        assertEquals(0, lost.pending)
        assertEquals(200, lost.you)
        assertEquals(-1, lost.turn)
        assertEquals(1, lost.busted)
    }

    @Test fun `reaching 10000 gives the other player one more turn`() {
        val near = TenThousand.start(2, 0).copy(you = 9600, pending = 500, fresh = false, live = emptyList())
        val reached = near.bank()!!
        assertEquals(10_100, reached.you)
        assertEquals(-1, reached.turn)
        assertTrue(reached.reply)
        assertTrue(!reached.ended)
        val reply = reached.copy(turn = -1, cpu = 9000, pending = 200, fresh = false, reply = true)
        val answer = reply.banked()
        assertTrue(answer.ended)
        assertEquals(9200, answer.cpu)
        assertEquals(1, answer.winner)
    }

    @Test fun `ten thousand saves round-trip`() {
        val g = TenThousand.start(6, 3).roll()!!
        assertEquals(g, TenThousandCodec.decode(TenThousandCodec.encode(g)))
        assertNull(TenThousandCodec.decode("0"))
    }

    @Test fun `cargo needs a ship before the captain and the crew`() {
        assertEquals(0, ShipCrew.cargoOf(listOf(5, 5, 4, 4, 3)))
        assertEquals(0, ShipCrew.cargoOf(listOf(6, 5, 3, 3, 2)))
        assertEquals(7, ShipCrew.cargoOf(listOf(6, 6, 5, 4, 1)))
        assertEquals(12, ShipCrew.cargoOf(listOf(6, 5, 4, 6, 6)))
        val ship = ShipCrew.start(3, 0)
        var stuck = ship
        repeat(5) { stuck = stuck.hold(it)!! }
        assertNull(stuck.roll())
        val last = ship.roll()!!.roll()!!
        assertEquals(3, last.rolls)
        assertNull(last.roll())
    }

    @Test fun `each dice game finishes against every strength`() {
        for (setting in 0..3) {
            var box = ShutBox.start(setting + 1L, setting)
            var guard = 0
            while (!box.ended && guard++ < 40) {
                box = if (box.turn == 1) {
                    val move = box.options()
                    if (move.isEmpty()) box.take()!! else box.cover(move.first())!!
                } else ShutAi.step(box)
            }
            assertTrue(box.ended, "shut $setting")
            assertEquals(box.youScore < box.cpuScore, box.winner == 1)

            var ten = TenThousand.start(setting + 11L, setting)
            guard = 0
            while (!ten.ended && guard++ < 600) {
                ten = if (ten.turn == 1) humanTen(ten) else TenAi.step(ten)
            }
            assertTrue(ten.ended, "ten $setting pending ${ten.pending} you ${ten.you} cpu ${ten.cpu}")
            assertTrue(ten.you >= TenThousand.GOAL || ten.cpu >= TenThousand.GOAL)

            var ship = ShipCrew.start(setting + 21L, setting)
            guard = 0
            while (!ship.ended && guard++ < 80) {
                ship = if (ship.turn == 1) {
                    if (ship.rolls >= 3 || ship.held.all { it }) ship.stay()!! else ship.roll() ?: ship.stay()!!
                } else ShipAi.step(ship)
            }
            assertTrue(ship.ended, "ship $setting")
            assertEquals(ShipCrew.HANDS, ship.hand + 1)
        }
    }

    private fun humanTen(g: TenThousand): TenThousand {
        if (!g.fresh) return if (g.canBank() && g.pending >= TenThousand.OPENING) g.bank()!! else g.roll()!!
        val mask = TenThousand.scoringMasks(g.live).maxBy { (TenThousand.pointsOf(it.map { i -> g.live[i] }) ?: 0) }
        return g.score(mask)!!
    }
}
