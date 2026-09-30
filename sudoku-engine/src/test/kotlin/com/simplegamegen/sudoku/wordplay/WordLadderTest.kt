package com.simplegamegen.sudoku.wordplay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordLadderTest {
    @Test fun `each level builds a ladder the dictionary can finish`() {
        for (setting in WordLadder.NAMES.indices) {
            val game = WordLadder.start(setting + 11L, setting)
            assertEquals(0, game.steps)
            assertTrue(game.par >= 2)
            assertTrue(game.limit >= game.par)
            assertTrue(Lexicon.isCommon(game.start) && Lexicon.isCommon(game.target))
            var walked = game
            var guard = 0
            while (!walked.won && guard++ < game.par) walked = walked.hint()!!
            assertTrue(walked.won)
            assertEquals(game.par, walked.steps)
        }
    }

    @Test fun `only a real word one letter away is a step`() {
        val game = WordLadder.start(3, 1)
        assertNull(game.play(game.current))
        assertNull(game.play("ZZZZ"))
        val neighbor = LadderWords.neighbors(game.current).first { it != game.target }
        val stepped = game.play(neighbor)!!
        assertEquals(neighbor, stepped.current)
        assertEquals(1, stepped.steps)
        assertNull(stepped.play("NOTAWORD"))
        val changed = game.current.toCharArray()
        changed[0] = if (changed[0] == 'A') 'B' else 'A'
        changed[1] = if (changed[1] == 'A') 'B' else 'A'
        assertNull(game.play(String(changed)))
    }

    @Test fun `cold to warm is a real four-step ladder`() {
        assertTrue(Lexicon.isWord("COLD") && Lexicon.isWord("CORD") && Lexicon.isWord("WARM"))
        val dist = LadderWords.distance("COLD", "WARM")
        assertTrue(dist in 2..6)
        val game = WordLadder(1, 1, "COLD", "WARM", listOf("COLD"), dist, dist + 2)
        val next = game.hint()!!
        assertEquals(1, WordLadder.changes(game.current, next.current))
        assertTrue(next.steps < game.limit)
    }
}
