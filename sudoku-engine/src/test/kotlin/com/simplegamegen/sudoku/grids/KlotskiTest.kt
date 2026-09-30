package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KlotskiTest {
    @Test fun `the classic opening is the familiar layout and can be solved`() {
        val classic = KlotskiRules.classic()
        assertTrue(KlotskiRules.legal(classic))
        assertFalse(KlotskiRules.won(classic[0]))
        val dist = KlotskiRules.distance(classic)
        assertEquals(116, dist)
        val game = Klotski(3, 1L, classic, dist)
        val step = game.hint()
        assertNotNull(step)
        assertEquals(1, step!!.moves)
        assertEquals(game, KlotskiCodec.decode(KlotskiCodec.encode(game)))
    }

    @Test fun `a finished big block is a win and an easy board is short`() {
        assertTrue(KlotskiRules.won(KlotskiRules.goal()[0]))
        assertEquals(0, KlotskiRules.distance(KlotskiRules.goal()))
        val easy = Klotski.generate(3L, LogicLevel.EASY)
        assertTrue(easy.solutionLength >= 2)
        assertEquals(easy.solutionLength, KlotskiRules.distance(easy.blocks, easy.solutionLength))
        assertNull(KlotskiCodec.decode("nope"))
    }
}
