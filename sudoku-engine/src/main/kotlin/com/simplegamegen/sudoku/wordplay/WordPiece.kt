package com.simplegamegen.sudoku.wordplay

import java.text.Normalizer

/**
 * BERT uncased tokenizer (normalise, split on spaces and punctuation, then greedy WordPiece), matching
 * the tokenizer shipped with all-MiniLM-L6-v2 so the on-device model sees the ids it was trained on.
 */
class WordPiece(vocab: List<String>) {
    private val ids: Map<String, Int> = vocab.withIndex().associate { (i, t) -> t to i }
    private val unk = ids.getValue("[UNK]")
    val cls = ids.getValue("[CLS]")
    val sep = ids.getValue("[SEP]")

    /** Token ids with [CLS] and [SEP], at most [max] long. */
    fun encode(text: String, max: Int = 128): IntArray {
        val out = ArrayList<Int>()
        out += cls
        for (word in words(normalize(text))) {
            out += pieces(word)
            if (out.size >= max - 1) break
        }
        val body = out.take(max - 1)
        return (body + sep).toIntArray()
    }

    private fun normalize(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            val code = ch.code
            when {
                code == 0 || code == 0xFFFD || (Character.isISOControl(ch) && ch !in "\t\n\r") -> {}
                ch.isWhitespace() -> sb.append(' ')
                isCjk(code) -> sb.append(' ').append(ch).append(' ')
                else -> sb.append(ch)
            }
        }
        // Lower case and drop accents (combining marks after NFD), as BertNormalizer does.
        return Normalizer.normalize(sb.toString().lowercase(), Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    }

    private fun isCjk(c: Int) = c in 0x4E00..0x9FFF || c in 0x3400..0x4DBF || c in 0xF900..0xFAFF || c in 0x20000..0x2A6DF

    private fun isPunct(ch: Char): Boolean {
        val c = ch.code
        if (c in 33..47 || c in 58..64 || c in 91..96 || c in 123..126) return true
        return when (Character.getType(ch).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION -> true
            else -> false
        }
    }

    private fun words(text: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() } }
        for (ch in text) when {
            ch == ' ' -> flush()
            isPunct(ch) -> { flush(); out += ch.toString() }
            else -> cur.append(ch)
        }
        flush()
        return out
    }

    private fun pieces(word: String): List<Int> {
        if (word.length > 100) return listOf(unk)
        val out = ArrayList<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: Int? = null
            while (start < end) {
                val piece = (if (start > 0) "##" else "") + word.substring(start, end)
                found = ids[piece]
                if (found != null) break
                end--
            }
            if (found == null) return listOf(unk)
            out += found
            start = end
        }
        return out
    }
}

/** Turns a sentence into a unit-length vector; the app provides one backed by the on-device model. */
fun interface SentenceEmbedder {
    fun embed(text: String): FloatArray
}

/**
 * Word checks first; when they can't decide, compares the guess with the entry's right phrases and its
 * wrong ones by meaning. Negation is left to the word checks, which handle it better than similarity does.
 */
class EmbeddingJudge(private val embedder: SentenceEmbedder) {
    private val cache = object : LinkedHashMap<String, FloatArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > 600
    }

    private fun vector(text: String): FloatArray = synchronized(cache) { cache[text] } ?: embedder.embed(text).also { synchronized(cache) { cache[text] = it } }

    /** Embeds an entry's phrases ahead of time so judging is quick. */
    fun warm(entry: MeaningEntry) { (listOf(entry.meaning) + entry.right + entry.wrong).forEach { vector(it) } }

    fun similarity(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }

    fun judge(entry: MeaningEntry, guess: String): MeaningVerdict {
        val first = MeaningJudge.judge(entry, guess)
        if (first != MeaningVerdict.UNSURE) return first
        val g = vector(guess.trim())
        val right = (listOf(entry.meaning) + entry.right).maxOf { similarity(vector(it), g) }
        val wrong = entry.wrong.maxOfOrNull { similarity(vector(it), g) } ?: 0f
        return when {
            right >= RIGHT_AT && right - wrong >= MARGIN_RIGHT -> MeaningVerdict.RIGHT
            right >= CLOSE_AT && right - wrong >= 0f -> MeaningVerdict.CLOSE
            else -> MeaningVerdict.WRONG
        }
    }

    companion object {
        const val RIGHT_AT = 0.55f
        const val CLOSE_AT = 0.40f
        const val MARGIN_RIGHT = 0.10f
    }
}
