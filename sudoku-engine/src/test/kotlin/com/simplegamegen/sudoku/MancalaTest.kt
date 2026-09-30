package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.Mancala
import com.simplegamegen.sudoku.duels.MancalaAi
import com.simplegamegen.sudoku.duels.MancalaCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class MancalaTest {
    @Test fun `sowing moves right and landing in your store grants another turn`() {
        val game = Mancala.start(1, 0)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), game.legalPits())
        val sown = game.sow(0)!!
        assertEquals(listOf(0, 5, 5, 5, 5, 4, 0), sown.pits.take(7))
        assertEquals(-1, sown.turn)
        val again = game.sow(2)!!
        assertEquals(0, again.pits[2])
        assertEquals(1, again.pits[Mancala.YOU])
        assertEquals(1, again.turn)
        assertFalse(again.ended)
        assertEquals(again, MancalaCodec.decode(MancalaCodec.encode(again)))
    }

    @Test fun `a last stone in an empty pit captures the pit opposite`() {
        val pits = listOf(0, 2, 0, 0, 1, 0, 20, 6, 3, 0, 0, 0, 0, 16)
        val next = Mancala(0, 1, pits).sow(4)!!
        assertEquals(0, next.pits[5])
        assertEquals(0, next.pits[7])
        assertEquals(27, next.pits[Mancala.YOU])
        assertEquals(-1, next.turn)
    }

    @Test fun `the computer's stones skip your store`() {
        val pits = listOf(4, 0, 0, 0, 0, 0, 20, 0, 4, 0, 0, 0, 2, 18)
        val next = Mancala(1, 2, pits, turn = -1).sow(12)!!
        assertEquals(20, next.pits[Mancala.YOU])
        assertEquals(19, next.pits[Mancala.CPU])
        assertEquals(5, next.pits[0])
    }

    @Test fun `emptying your side ends the game and sweeps the other pits`() {
        val pits = listOf(0, 0, 0, 0, 0, 1, 20, 0, 4, 3, 0, 0, 0, 20)
        val next = Mancala(0, 3, pits).sow(5)!!
        assertTrue(next.ended)
        assertEquals(21, next.pits[Mancala.YOU])
        assertEquals(27, next.pits[Mancala.CPU])
        assertEquals(-1, next.winner)
        assertTrue((0 until Mancala.HOLES).filter { it != Mancala.YOU && it != Mancala.CPU }.all { next.pits[it] == 0 })
        assertNull(next.sow(0))
        assertEquals(next, MancalaCodec.decode(MancalaCodec.encode(next)))
    }

    @Test fun `an easy match puts every stone in a store`() {
        var game = Mancala.start(4, 0)
        val random = Random(4)
        var guard = 0
        while (!game.ended && guard++ < 300) {
            val pit = if (game.turn == 1) game.legalPits().random(random) else MancalaAi.choose(game)
            game = game.sow(pit)!!
        }
        assertTrue(game.ended)
        assertEquals(Mancala.STONES, game.pits[Mancala.YOU] + game.pits[Mancala.CPU])
    }

    @Test fun `medium takes a capture, and expert's opening sow is legal`() {
        val pits = listOf(8, 0, 1, 1, 1, 1, 15, 1, 1, 1, 1, 1, 0, 16)
        assertEquals(11, MancalaAi.choose(Mancala(1, 9, pits, turn = -1)))
        assertTrue(MancalaAi.choose(Mancala.start(2, 3)) in 0 until Mancala.PITS)
    }

    @Test fun `broken saves and the other side's pits are rejected`() {
        assertNull(MancalaCodec.decode("bad"))
        assertNull(Mancala.start(1, 0).sow(7))
        assertNull(Mancala.start(1, 0).sow(-1))
    }
}
