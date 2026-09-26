package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoneLetterTest {
    private fun round(letter: Char, categories: List<String>, mine: List<String>, rivals: List<List<String>> = emptyList()) =
        LoneLetter(LogicLevel.EASY, 1, listOf(LoneRound(letter, categories, mine, rivals)), reviewing = rivals.isNotEmpty())

    @Test fun `categories load with everyday picks`() {
        assertEquals(32, LoneCategories.all.size)
        val animals = LoneCategories["animals"]
        assertTrue(animals.accepts("horse") && animals.accepts("Horses") && animals.accepts("an aardvark"))
        assertFalse(animals.accepts("teapot"))
        assertEquals("cat", animals.picksFor('C').first())
        assertTrue(LoneCategories["countries"].accepts("France") && LoneCategories["cities"].accepts("new york"))
        // Nothing the computer picks is rude, and every pick is accepted by its own category.
        for (c in LoneCategories.all) for ((letter, picks) in c.picks) for (p in picks) {
            assertTrue(c.accepts(p), "${c.id}: $p")
            assertEquals(letter, p[0].uppercaseChar())
        }
    }

    @Test fun `answers match without articles, case or plurals`() {
        assertEquals("big bus", LoneAnswers.tidy("The  Big-Bus!"))
        assertEquals(LoneAnswers.key("tomatoes"), LoneAnswers.key("Tomato"))
        assertEquals(LoneAnswers.key("cherries"), LoneAnswers.key("cherry"))
        assertEquals("glass", LoneAnswers.key("glass"))
        assertTrue(LoneAnswers.startsWith("the bee", 'B'))
        assertFalse(LoneAnswers.startsWith("the bee", 'T'))
    }

    @Test fun `only answers nobody else wrote score, with a bonus for each extra word on the letter`() {
        val g = round('B', listOf("animals", "colors", "fruit", "sports"), listOf("bear", "blue", "Big Blue Banana", "swimming"),
            listOf(listOf("bears", "brown", "", ""), listOf("bat", "", "", "badminton")))
        assertEquals(LoneMark.SAME, g.mark(0, 0))                // bear = bears
        assertEquals(LoneMark.GOOD, g.mark(0, 1))
        assertEquals(LoneMark.WRONG_LETTER, g.mark(0, 3))
        assertEquals(1, g.points(0, 1))
        // "Big Blue Banana" isn't on the fruit list; counted, it scores 1 + 2 for the extra B words.
        assertEquals(LoneMark.NOT_LISTED, g.mark(0, 2))
        val counted = g.count(0, 2)!!
        assertEquals(3, counted.points(0, 2))
        assertEquals(4, counted.score)
        assertEquals(LoneMark.SAME, g.rivalMark(0, 0, 0))
        assertEquals(listOf(1, 2), g.rivalScores)
    }

    @Test fun `only real words can be counted`() {
        val g = round('B', listOf("animals", "fruit"), listOf("blorptang", "bread"), listOf(listOf("", ""), listOf("", "")))
        assertNull(g.count(0, 0))
        assertFalse(g.countable(0, 0))
        assertTrue(g.countable(0, 1))
    }

    @Test fun `a game deals fresh letters and fitting categories`() {
        for (level in LogicLevel.entries) for (seed in 1L..20L) {
            val g = LoneLetter.generate(seed, level)
            assertEquals(LoneLetter.ROUNDS, g.rounds.size)
            assertEquals(LoneLetter.ROUNDS, g.rounds.map { it.letter }.toSet().size)
            for (r in g.rounds) {
                assertEquals(LoneLetter.categoriesOf(level), r.categories.toSet().size)
                assertTrue(r.categories.all { LoneCategories[it].answersFor(r.letter) >= 3 }, "$seed ${r.letter} ${r.categories}")
            }
            assertEquals(g, LoneLetter.generate(seed, level))
        }
    }

    @Test fun `rounds play through to a result`() {
        var g = LoneLetter.generate(7, LogicLevel.MEDIUM)
        assertTrue(g.playing)
        val r0 = g.rounds[0]
        g = g.answer(0, LoneCategories[r0.categories[0]].picksFor(r0.letter).lastOrNull() ?: "x")!!
        assertNull(g.next())
        // The clock runs out and scores the round.
        g = g.tick(LoneLetter.secondsOf(LogicLevel.MEDIUM))!!
        assertTrue(g.rounds[0].scored && g.reviewing && !g.playing)
        assertNull(g.answer(1, "anything"))
        assertEquals(0, g.current)
        // Computer answers are real and start with the letter.
        for (answers in g.rounds[0].rivals) for ((i, a) in answers.withIndex())
            if (a.isNotEmpty()) assertTrue(LoneCategories[r0.categories[i]].accepts(a) && LoneAnswers.startsWith(a, r0.letter), a)
        g = g.next()!!
        assertEquals(1, g.current)
        g = g.stop()!!.next()!!.stop()!!
        assertTrue(g.over)
        assertNull(g.next())
        assertEquals(g.score >= g.rivalScores.max(), g.won)
    }

    @Test fun `hints show the start of an everyday answer once per category`() {
        val g = LoneLetter.generate(3, LogicLevel.EASY)
        val h = g.hint(0)!!
        assertEquals(1, h.hints)
        val r = h.rounds[0]
        assertEquals(LoneCategories[r.categories[0]].picksFor(r.letter).first().take(2).uppercase(), r.hinted[0])
        assertNull(h.hint(0))
    }

    @Test fun `the codec round trips`() {
        var g = LoneLetter.generate(11, LogicLevel.HARD).answer(0, "The big one").let { it!!.hint(1)!! }.tick(9)!!
        assertEquals(g, LoneLetterCodec.decode(LoneLetterCodec.encode(g)))
        g = g.stop()!!
        assertEquals(g, LoneLetterCodec.decode(LoneLetterCodec.encode(g)))
        assertNull(LoneLetterCodec.decode("nonsense"))
        assertNotNull(LoneLetter.clean("a;b|c/d").let { assertEquals("abcd", it) })
    }
}
