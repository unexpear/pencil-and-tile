package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SprawlTest {
    @Test fun `the word lists load and leave rude words out`() {
        assertTrue(Lexicon.isWord("CAT") && Lexicon.isCommon("CAT"))
        assertTrue(Lexicon.hasPrefix("CA"))
        assertFalse(Lexicon.isWord("SHIT"))
        assertFalse(Lexicon.isWord("QZX"))
    }

    @Test fun `words chain through touching squares only once each`() {
        // C A T / X X X / X X X
        val g = Sprawl(LogicLevel.EASY, 1, 3, "CATXXXXXX")
        assertNotNull(g.take(listOf(0, 1, 2)))
        assertNull(g.take(listOf(0, 2, 1)))                 // C to T doesn't touch
        assertEquals("Already found.", g.take(listOf(0, 1, 2))!!.problem(listOf(0, 1, 2)))
        assertTrue("CAT" in g.answers)
    }

    @Test fun `Q squares read as QU`() {
        assertEquals("QU", sprawlFace('Q'))
        assertEquals("QUIT", Sprawl(LogicLevel.EASY, 1, 3, "QITXXXXXX").wordOf(listOf(0, 1, 2)))
    }

    @Test fun `generated grids hold enough everyday words and a reachable goal`() {
        for (level in LogicLevel.entries) {
            val t0 = System.nanoTime()
            val g = Sprawl.generate(level.ordinal * 17L + 3, level)
            println("$level ${g.letters.chunked(g.size)} everyday ${g.everyday.size} goal ${g.goal} ${(System.nanoTime() - t0) / 1_000_000} ms")
            assertTrue(g.everyday.size >= 10)
            assertTrue(g.goal <= g.everyday.sumOf { Sprawl.pointsFor(it) })
            assertNotNull(g.hint())
        }
    }

    @Test fun `codec round trip`() {
        val g = Sprawl.generate(5, LogicLevel.MEDIUM).let { it.take(it.answers.values.first())!! }.hint()!!
        assertEquals(g, SprawlCodec.decode(SprawlCodec.encode(g)))
    }
}
