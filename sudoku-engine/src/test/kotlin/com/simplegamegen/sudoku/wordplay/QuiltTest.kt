package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuiltTest {
    @Test fun `every level builds a quilt whose answer is made of words`() {
        for (level in LogicLevel.entries) {
            val t0 = System.nanoTime()
            val q = Quilt.generate(level.ordinal * 11L + 5, level)
            println("$level ${q.width}x${q.height} ${(System.nanoTime() - t0) / 1_000_000} ms\n" + q.answer.chunked(q.width).joinToString("\n") +
                "\npatches " + q.patches.chunked(q.width).joinToString(" ") { it.joinToString(",") })
            assertTrue(q.runs.all { r -> Lexicon.isWord(r.map { q.answer[it] }.joinToString("")) })
            assertTrue(q.letters.values.all { it.length >= 2 })
            assertFalse(q.solved)
        }
    }

    @Test fun `placing letters uses only the patch's own letters and solving checks every run`() {
        var q = Quilt.generate(3, LogicLevel.EASY)
        val first = q.squares.first { it !in q.given }
        val wrongLetter = ('A'..'Z').first { it !in q.letters.getValue(q.patches[first]) }
        assertNull(q.place(first, wrongLetter))
        for (i in q.squares) if (i !in q.given) q = q.place(i, q.answer[i])!!
        assertTrue(q.solved)
    }

    @Test fun `hints place answer letters`() {
        var q = Quilt.generate(8, LogicLevel.MEDIUM)
        repeat(q.squares.size) { q = q.hint() ?: q }
        assertTrue(q.solved)
    }

    @Test fun `codec round trip`() {
        val q = Quilt.generate(4, LogicLevel.HARD).let { it.place(it.squares.first { s -> s !in it.given }, it.answer[it.squares.first { s -> s !in it.given }])!! }
        assertEquals(q, QuiltCodec.decode(QuiltCodec.encode(q)))
        assertNotNull(q)
    }
}
