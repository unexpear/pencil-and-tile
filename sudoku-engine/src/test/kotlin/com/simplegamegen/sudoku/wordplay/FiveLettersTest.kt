package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.wordplay.Mark.ABSENT
import com.simplegamegen.sudoku.wordplay.Mark.ELSEWHERE
import com.simplegamegen.sudoku.wordplay.Mark.RIGHT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FiveLettersTest {
    @Test fun `repeated letters are marked only as often as the answer has them`() {
        assertEquals(listOf(RIGHT, RIGHT, RIGHT, RIGHT, RIGHT), FiveLetters.score("APPLE", "APPLE"))
        // BREAD has one E, taken by the right-spot E; the other two Es in GEESE are absent.
        assertEquals(listOf(ABSENT, ABSENT, RIGHT, ABSENT, ABSENT), FiveLetters.score("GEESE", "BREAD"))
        // CRANE's only E is matched by EERIE's last E, so the other Es are absent; R is elsewhere.
        assertEquals(listOf(ABSENT, ABSENT, ELSEWHERE, ABSENT, RIGHT), FiveLetters.score("EERIE", "CRANE"))
        // A right-spot letter uses up the answer's copy before any "elsewhere".
        assertEquals(listOf(ABSENT, RIGHT, ABSENT, ABSENT, ABSENT), FiveLetters.score("LLXXX", "PLANT"))
    }

    @Test fun `word lists are clean`() {
        (FiveWords.answers + FiveWords.valid).forEach { assertTrue(it.length == 5 && it.all { c -> c in 'A'..'Z' }, it) }
        assertTrue(FiveWords.answers.all { it in FiveWords.valid })
        assertTrue(FiveWords.everyday.size >= 250 && FiveWords.valid.size >= 1500, "${FiveWords.everyday.size} / ${FiveWords.valid.size}")
        assertTrue(FiveWords.everyday.all { it in FiveWords.answers && Lexicon.isCommon(it) })
        assertTrue(FiveWords.answers.all { it in FiveWords.valid })
    }

    @Test fun `guessing wins, loses and checks the list`() {
        val g = FiveLetters(LogicLevel.MEDIUM, 1, "CRANE")
        assertEquals("Not in the word list.", g.problem("QZXVW"))
        assertNull(FiveLetters(LogicLevel.EASY, 1, "CRANE").problem("QZXVW"), "Easy takes any five letters")
        val won = g.guess("TRAIN")!!.guess("CRANE")!!
        assertTrue(won.won && won.over)
        var lost = FiveLetters(LogicLevel.EXPERT, 1, "CRANE")
        repeat(lost.limit) { lost = lost.copy(guesses = lost.guesses + "PIZZA") }
        assertTrue(lost.lost && lost.over)
    }

    @Test fun `hard mode keeps found letters`() {
        // CHAIR against CRANE: C and A are in place, R is in the word elsewhere.
        val g = FiveLetters(LogicLevel.HARD, 1, "CRANE").guess("CHAIR")!!
        assertEquals("Letter 1 must be C.", g.problem("TRAIN"))
        assertEquals("The guess must use R.", g.problem("CLASP"))
        assertNull(g.problem("CRANE"))
    }

    @Test fun `hints reveal letters not yet found and saves round trip`() {
        val g = FiveLetters(LogicLevel.MEDIUM, 3, "CRANE").guess("CRISP")!!
        val h = g.hint()!!
        assertEquals(setOf(2), h.revealed, "C and R are already found")
        assertEquals(h, FiveLettersCodec.decode(FiveLettersCodec.encode(h)))
        assertNotNull(FiveLetters.generate(9, LogicLevel.EXPERT))
    }
}
