package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.wordplay.MeaningVerdict.CLOSE
import com.simplegamegen.sudoku.wordplay.MeaningVerdict.RIGHT
import com.simplegamegen.sudoku.wordplay.MeaningVerdict.WRONG
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeaningTest {
    private fun judge(word: String, guess: String) = MeaningJudge.judge(MeaningBank.entry(word)!!, guess)

    @Test fun `plain answers in the player's own words`() {
        val cases = listOf(
            Triple("gregarious", "likes being around other people", RIGHT),
            Triple("gregarious", "friendly and outgoing", RIGHT),
            Triple("gregarious", "loves parties", RIGHT),
            Triple("gregarious", "very angry", WRONG),
            Triple("gregarious", "likes to be alone", WRONG),
            Triple("ephemeral", "short lived", RIGHT),
            Triple("ephemeral", "doesn't last long", RIGHT),
            Triple("ephemeral", "lasts a long time", WRONG),
            Triple("candid", "honest and direct", RIGHT),
            Triple("candid", "says what they think", RIGHT),
            Triple("candid", "a type of candy", WRONG),
            Triple("meticulous", "careful about small details", RIGHT),
            Triple("meticulous", "careful", CLOSE),
            Triple("meticulous", "messy", WRONG),
            Triple("benevolent", "kind and generous", RIGHT),
            Triple("benevolent", "evil", WRONG),
            Triple("frugal", "cheap", CLOSE),
            Triple("frugal", "fruit", WRONG),
            Triple("obsolete", "old fashioned and not used anymore", RIGHT),
            Triple("obsolete", "brand new", WRONG),
        )
        cases.forEach { (word, guess, want) -> assertEquals(want, judge(word, guess), "$word: $guess") }
    }

    @Test fun `negation flips a phrase`() {
        // The cases a similarity model gets wrong: "no energy" sits close to "full of energy".
        assertEquals(RIGHT, judge("lethargic", "has no energy"))
        assertEquals(RIGHT, judge("lethargic", "tired and slow"))
        assertEquals(WRONG, judge("lethargic", "full of energy"))
        assertEquals(CLOSE, judge("lethargic", "not full of energy"), "a negated opposite is on the way there")
        assertEquals(RIGHT, judge("frugal", "doesn't like spending money"))
        assertEquals(RIGHT, judge("brave", "not scared of anything"))
        assertEquals(WRONG, judge("brave", "not strong or courageous"))
        assertEquals(RIGHT, judge("shallow", "not deep"))
        assertEquals(WRONG, judge("ancient", "not old"))
    }

    @Test fun `repeating the word explains nothing`() {
        assertEquals(WRONG, judge("vanish", "vanish"))
        assertEquals(WRONG, judge("vanish", "to vanish"))
        assertEquals(WRONG, judge("vanish", ""))
    }

    @Test fun `every entry accepts its own phrases and uses the word in its sentence`() {
        assertTrue(MeaningBank.entries.size >= 60)
        (0..3).forEach { lvl -> assertTrue(MeaningBank.entries.count { it.level == lvl } >= MeaningGame.ROUND, "level $lvl") }
        MeaningBank.entries.forEach { e ->
            e.right.forEach { assertEquals(RIGHT, MeaningJudge.judge(e, it), "${e.word}: $it") }
            e.wrong.forEach { assertEquals(WRONG, MeaningJudge.judge(e, it), "${e.word}: $it") }
        }
    }

    @Test fun `a close answer gets one more try`() {
        var g = MeaningGame(LogicLevel.HARD, 1, listOf("meticulous", "lethargic"))
        val (afterClose, v1) = g.answer("careful")
        assertEquals(CLOSE, v1)
        assertEquals(0, afterClose.answers.size, "a first close answer isn't recorded yet")
        val (afterSecond, v2) = afterClose.answer("pays attention to small details")
        assertEquals(RIGHT, v2)
        g = afterSecond.next()
        assertEquals("lethargic", g.entry!!.word)
        val (wrong, v3) = g.answer("full of energy")
        assertEquals(WRONG, v3)
        val claimed = wrong.claimRight()!!
        assertEquals(RIGHT, claimed.verdicts.last())
        assertEquals(setOf(1), claimed.selfJudged)
        assertTrue(claimed.next().complete)
        assertEquals(4, claimed.score)
    }

    @Test fun `hints cost a point and saves round trip`() {
        val g = MeaningGame.generate(3, LogicLevel.MEDIUM)
        assertEquals(MeaningGame.ROUND, g.words.size)
        val hinted = g.hint()!!
        assertNull(hinted.hint(), "one hint per word")
        val entry = hinted.entry!!
        val answered = hinted.answer(entry.right.first()).first
        assertEquals(1, answered.score)
        val revealed = answered.next().reveal()!!
        assertEquals("", revealed.answers.last())
        assertEquals(revealed, MeaningCodec.decode(MeaningCodec.encode(revealed)))
        val tricky = MeaningGame(LogicLevel.EASY, 1, listOf("brave")).answer("not scared | really\nbold").first
        assertNotNull(MeaningCodec.decode(MeaningCodec.encode(tricky)))
        assertEquals(tricky, MeaningCodec.decode(MeaningCodec.encode(tricky)))
    }
}
