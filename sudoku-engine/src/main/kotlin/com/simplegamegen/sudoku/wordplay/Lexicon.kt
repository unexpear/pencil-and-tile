package com.simplegamegen.sudoku.wordplay

import java.util.zip.GZIPInputStream

/**
 * The big shared word lists (built by tools/words/build_dictionary.py): every accepted word from ENABLE
 * (public domain) and an everyday tier worked out from Open English WordNet. Rude words are left out of both.
 */
object Lexicon {
    /** Every accepted word, 2 to 15 letters, upper case and sorted. */
    val all: Array<String> by lazy { load("/words/all.txt.gz").toTypedArray() }

    /** Everyday words, for targets, answers and hints. */
    val common: Set<String> by lazy { load("/words/common.txt.gz").toHashSet() }

    private fun load(path: String): List<String> {
        val stream = Lexicon::class.java.getResourceAsStream(path) ?: error("Missing word list $path")
        return GZIPInputStream(stream).bufferedReader().useLines { lines -> lines.filter { it.isNotBlank() }.toList() }
    }

    fun isWord(word: String): Boolean = all.binarySearch(word) >= 0

    fun isCommon(word: String): Boolean = word in common

    /** True when some word starts with [prefix] (or is it). */
    fun hasPrefix(prefix: String): Boolean {
        val i = all.binarySearch(prefix)
        if (i >= 0) return true
        val at = -i - 1
        return at < all.size && all[at].startsWith(prefix)
    }
}
