package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HoneycombTest {
    @Test fun `every level hides a winnable hive and everyday words score more`() {
        for (level in LogicLevel.entries) {
            val game = Honeycomb.generate(level.ordinal + 3L, level)
            assertEquals(game, HoneycombCodec.decode(HoneycombCodec.encode(game)))
            val common = Honeycomb.answers(game.center, game.outer, game.minLength).filter { Lexicon.isCommon(it) }
            assertTrue(common.size >= 8, "$level ${game.center}${game.outer}")
            val plain = common.first { it.toSet().size < 7 }
            val rare = Honeycomb.answers(game.center, game.outer, game.minLength).firstOrNull { !Lexicon.isCommon(it) }
            assertTrue(game.points(plain) == plain.length)
            if (rare != null) assertEquals(1 + if (rare.toSet().size == 7) 7 else 0, game.points(rare))
            assertEquals("Use the middle letter.", game.problem(plain.replace(game.center, outerLetter(game))))
            var play = game
            for (word in common) {
                play = play.play(word) ?: play
                if (play.won) break
            }
            assertTrue(play.won, "$level score ${play.score} / ${play.target}")
        }
    }

    private fun outerLetter(game: Honeycomb): Char = game.outer.first { it != game.center }

    @Test fun `a repeat and a missing middle are refused`() {
        val game = Honeycomb.generate(2, LogicLevel.EASY)
        val word = Honeycomb.answers(game.center, game.outer, game.minLength).first { Lexicon.isCommon(it) }
        val played = game.play(word)!!
        assertNull(played.play(word))
        assertEquals("Already found.", played.problem(word))
        assertNotNull(game.hintWord())
    }
}
