package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.wordplay.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WordPlayTest {
    @Test fun `cryptograms use a derangement, reveal givens by level and solve by guessing`() {
        for (level in LogicLevel.entries) for (seed in 1L..20L) {
            val c = Cryptogram.generate(seed, level)
            assertEquals(c, Cryptogram.generate(seed, level))
            assertTrue(c.cipher.indices.none { c.cipher[it] == 'A' + it })
            assertEquals(listOf(3, 1, 0, 0)[level.ordinal].coerceAtMost(c.letters.size), c.given.length)
            // Decoding the cipher recovers the quote.
            assertEquals(c.quote.uppercase(), c.encoded.map { if (it in 'A'..'Z') c.plainOf(it) else it }.joinToString(""))
            if (level.ordinal <= LogicLevel.MEDIUM.ordinal && c.author == "Tatoeba") {
                val counts = IntArray(26)
                c.quote.uppercase().forEach { if (it in 'A'..'Z') counts[it - 'A']++ }
                assertTrue(counts.count { it >= 2 } >= 4, c.quote)
            }
        }
        var c = Cryptogram.generate(3, LogicLevel.HARD)
        val letters = c.letters.toList()
        val a = letters[0]; val b = letters[1]
        c = c.guess(a, c.plainOf(b))
        assertEquals(setOf(a), c.mistakes())
        c = c.guess(b, c.plainOf(b))
        assertNull(c.guessOf(a), "a plain letter moves to the latest cipher letter")
        letters.forEach { c = c.guess(it, c.plainOf(it)) }
        assertTrue(c.complete)
        assertSame(c, c.guess(a, 'Z'))
        assertEquals(c, CryptogramCodec.decode(CryptogramCodec.encode(c)))
    }

    @Test fun `cryptogram hints fill letters and givens are locked`() {
        var c = Cryptogram.generate(8, LogicLevel.EASY)
        val g = c.given.first()
        assertSame(c, c.guess(g, null))
        var steps = 0
        while (!c.complete) { c = c.hint(null)!!; steps++ }
        assertEquals(steps, c.hints)
        assertNull(c.hint(null))
        assertNull(CryptogramCodec.decode(CryptogramCodec.encode(c).replace(c.cipher, "A".repeat(26))))
    }

    @Test fun `scramble rounds accept the word or a real anagram and track hints and skips`() {
        for (level in LogicLevel.entries) {
            val g = ScrambleGame.generate(4, level)
            assertEquals(ScrambleGame.ROUND, g.words.size)
            g.words.indices.forEach { i ->
                assertTrue(g.words[i].length in ScrambleGame.lengths(level))
                assertNotEquals(g.words[i], g.jumbles[i])
            }
        }
        var g = ScrambleGame.generate(9, LogicLevel.MEDIUM)
        val first = g.word
        // Type the answer by tapping jumble positions.
        first.forEach { ch -> g = g.type(g.jumble.indices.first { it !in g.usedPositions() && g.jumble[it] == ch }) }
        val (afterSubmit, ok) = g.submit()
        assertTrue(ok); assertEquals(1, afterSubmit.solved); assertNotEquals(first, afterSubmit.word)
        g = afterSubmit.hint()
        assertEquals(g.word.take(1), g.typed)
        assertSame(g, g.backspace(), "hinted letters stay")
        g = g.skip()
        assertEquals(ScrambleStatus.SKIPPED, g.status[1])
        var done = g
        while (!done.complete) done = done.hint()
        assertEquals(1, done.solved)
        assertEquals(done, ScrambleCodec.decode(ScrambleCodec.encode(done)))
        val shuffled = ScrambleGame.generate(9, LogicLevel.MEDIUM).shuffle()
        assertEquals(1, shuffled.shuffles)
        assertNull(ScrambleCodec.decode("1\nEASY"))
    }

    @Test fun `acrostic first letters spell the keyword and answers solve the puzzle`() {
        for (level in LogicLevel.entries) for (seed in 1L..10L) {
            val g = AcrosticGame.generate(seed, level)
            assertEquals(g.keyword, g.entries.joinToString("") { it.answer.take(1) })
            assertTrue(g.keyword.length in AcrosticGame.keywordLengths(level))
            g.entries.forEach { assertTrue(it.answer.length in AcrosticGame.answerLengths(level)) }
        }
        var g = AcrosticGame.generate(2, LogicLevel.MEDIUM)
        g = g.answer(0, "zz")
        assertEquals(setOf(0), g.mistakes())
        g.entries.forEachIndexed { i, e -> g = g.answer(i, e.answer.lowercase()) }
        assertTrue(g.complete)
        assertEquals(g, AcrosticCodec.decode(AcrosticCodec.encode(g)))
        var h = AcrosticGame.generate(2, LogicLevel.MEDIUM)
        while (!h.complete) h = h.hint(null)!!
        assertEquals(h.entries.sumOf { it.answer.length }, h.hints)
        assertNull(AcrosticCodec.decode(AcrosticCodec.encode(h).replace(h.keyword, "QQQQ")))
    }
}
