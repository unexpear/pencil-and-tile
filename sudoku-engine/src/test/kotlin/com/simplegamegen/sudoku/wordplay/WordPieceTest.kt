package com.simplegamegen.sudoku.wordplay

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class WordPieceTest {
    private val tokenizer = WordPiece(File("../app/src/main/assets/models/minilm/vocab.txt").readLines())

    @Test fun `ids match the reference tokenizer`() {
        // Expected ids come from the Hugging Face tokenizer shipped with all-MiniLM-L6-v2.
        val cases = mapOf(
            "The cat sits on the mat" to intArrayOf(101, 1996, 4937, 7719, 2006, 1996, 13523, 102),
            "doesn't last long" to intArrayOf(101, 2987, 1005, 1056, 2197, 2146, 102),
            "Well-meaning, kind & GENEROUS!" to intArrayOf(101, 2092, 1011, 3574, 1010, 2785, 1004, 12382, 999, 102),
            "café naïve résumé" to intArrayOf(101, 7668, 15743, 13746, 102),
            "unbelievably ephemeral happiness" to intArrayOf(101, 4895, 8671, 2666, 3567, 6321, 4958, 29122, 21673, 8404, 102),
            "it's 42 degrees... really?" to intArrayOf(101, 2009, 1005, 1055, 4413, 5445, 1012, 1012, 1012, 2428, 1029, 102),
            "supercalifragilisticexpialidocious" to intArrayOf(101, 3565, 9289, 10128, 29181, 24411, 4588, 10288, 19312, 21273, 10085, 6313, 102),
        )
        cases.forEach { (text, ids) -> assertArrayEquals(ids, tokenizer.encode(text), text) }
    }

    @Test fun `long input is cut but keeps its end marker`() {
        val ids = tokenizer.encode("word ".repeat(400), max = 32)
        assertEquals(32, ids.size)
        assertEquals(tokenizer.sep, ids.last())
    }

    @Test fun `the similarity judge only steps in when word checks can't decide`() {
        val entry = MeaningBank.entry("gregarious")!!
        var calls = 0
        // A fake model: everything about company or parties points one way, everything else the other.
        val judge = EmbeddingJudge { text ->
            calls++
            val social = listOf("sociable", "company", "people", "parties", "party", "mingle", "social", "outgoing", "friendly", "extrovert").any { it in text.lowercase() }
            if (social) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f)
        }
        assertEquals(MeaningVerdict.RIGHT, judge.judge(entry, "friendly and outgoing"))
        assertEquals(0, calls, "a clear word match needs no model")
        assertEquals(MeaningVerdict.RIGHT, judge.judge(entry, "enjoys a good party"))
        assertEquals(MeaningVerdict.WRONG, judge.judge(entry, "made of glass"))
    }
}
