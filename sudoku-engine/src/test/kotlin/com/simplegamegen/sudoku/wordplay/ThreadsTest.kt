package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ThreadsTest {
    private val lexicon = EnglishThreads.lexicon

    @Test fun `rules test words the way their names say`() {
        assertTrue(lexicon.rule("before:BALL")!!.fits("FOOT"))
        assertTrue(lexicon.rule("after:SUN")!!.fits("FLOWER"))
        assertTrue(lexicon.rule("hidden:ANIMAL")!!.fits("SCATTER"))
        assertFalse(lexicon.rule("hidden:ANIMAL")!!.fits("CAT"), "a word doesn't hide itself")
        assertTrue(lexicon.rule("anagram:ANIMAL")!!.fits("ACT"))
        assertFalse(lexicon.rule("anagram:ANIMAL")!!.fits("DOG"), "an animal isn't an anagram of itself")
        assertTrue(lexicon.rule("hidden:BODY")!!.fits("PEARL"), "hidden rules are computed, so accidental hits count too")
    }

    @Test fun `a second way to split the words is found`() {
        val fruit = lexicon.rule("tag:FRUIT")!!; val color = lexicon.rule("tag:COLOR")!!
        val chess = lexicon.rule("tag:CHESS")!!; val planet = lexicon.rule("tag:PLANET")!!
        val rules = listOf(fruit, color, chess, planet)
        val clean = listOf("APPLE", "BANANA", "GRAPE", "MANGO", "RED", "BLUE", "GREEN", "PINK", "KING", "QUEEN", "ROOK", "PAWN", "MARS", "VENUS", "EARTH", "SATURN")
        assertEquals(1, ThreadsGame.splits(clean, rules))
        // LIME and PLUM are both fruits and colors, so swapping them gives a second answer.
        val twoWays = listOf("APPLE", "BANANA", "GRAPE", "LIME", "RED", "BLUE", "GREEN", "PLUM") + clean.drop(8)
        assertEquals(2, ThreadsGame.splits(twoWays, rules))
    }

    @Test fun `every level makes puzzles with exactly one answer`() {
        for (level in LogicLevel.entries) repeat(40) { i ->
            val g = ThreadsGame.generate(1000L * level.ordinal + i, level)
            val rules = g.threads.map { lexicon.rule(it.rule)!! }
            val words = g.threads.flatMap { it.words }
            assertEquals(1, ThreadsGame.splits(words, rules), "$level seed $i")
            g.threads.forEach { t -> t.words.forEach { assertTrue(lexicon.rule(t.rule)!!.fits(it)) } }
            val herrings = ThreadsGame.overlaps(words, rules)
            if (level == LogicLevel.EASY) assertEquals(0, herrings) else assertTrue(herrings > 0, "$level should have red herrings")
        }
    }

    @Test fun `generation is quick`() {
        val start = System.nanoTime()
        for (level in LogicLevel.entries) repeat(25) { ThreadsGame.generate(77L + it, level) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(ms < 20_000, "100 puzzles took $ms ms")
    }

    @Test fun `guessing locks groups and counts mistakes`() {
        var g = ThreadsGame.generate(5, LogicLevel.MEDIUM)
        val first = g.threads[0].words.toSet()
        val (afterRight, v1) = g.guess(first)
        assertEquals(ThreadsGame.Verdict.RIGHT, v1)
        assertEquals(listOf(0), afterRight.solved)
        assertEquals(12, afterRight.order.size)
        g = afterRight
        // Three from one group and one from another is "one away".
        val near = g.threads[1].words.take(3).toSet() + g.threads[2].words.first()
        val (afterNear, v2) = g.guess(near)
        assertEquals(ThreadsGame.Verdict.ONE_AWAY, v2)
        assertEquals(1, afterNear.mistakes)
        assertEquals(ThreadsGame.Verdict.REPEATED, afterNear.guess(near).second, "the same wrong four doesn't cost twice")
        assertEquals(ThreadsGame.Verdict.INVALID, afterNear.guess(first).second, "solved words can't be picked again")
    }

    @Test fun `running out of lives reveals the answer`() {
        var g = ThreadsGame.generate(9, LogicLevel.EXPERT)
        val mixes = List(g.limit) { k -> g.threads.map { it.words[(it.words.size - 1 - k) % 4] }.toSet() }
        mixes.forEach { g = g.guess(it).first }
        assertTrue(g.lost && g.over && !g.complete)
        assertEquals(4, g.solved.size)
        assertTrue(g.order.isEmpty())
    }

    @Test fun `hints show threads from easiest and saves round trip`() {
        val g = ThreadsGame.generate(11, LogicLevel.HARD)
        val hinted = g.hint()!!.hint()!!
        assertEquals(setOf(0, 1), hinted.shown)
        val played = hinted.guess(hinted.threads[0].words.toSet()).first.shuffle(Random(3))
        assertEquals(played, ThreadsCodec.decode(ThreadsCodec.encode(played)))
        assertNotNull(ThreadsCodec.decode(ThreadsCodec.encode(g)))
    }
}
