package com.simplegamegen.sudoku.words

import java.util.zip.GZIPInputStream

/**
 * Extra English built by tools/words/build_open_content.py from the cached Open English WordNet
 * and Tatoeba CC0 sentences. Lemmas and clues are WordNet; sayings are Tatoeba. The reviewed
 * Word Meaning bank is a separate file and is not loaded here.
 */
internal object OpenContent {
    /** WordNet lemmas the letter games already accept. */
    val lemmas: Set<String> by lazy { lines("/words/lemmas.txt.gz").toHashSet() }

    /** One short definition per everyday word. The definition does not contain the word. */
    val clues: List<Pair<String, String>> by lazy {
        lines("/words/clues.tsv.gz").map { line ->
            val tab = line.indexOf('\t')
            require(tab > 0) { "bad clue line" }
            line.substring(0, tab) to line.substring(tab + 1)
        }
    }

    /** Public-domain sentences for Cryptogram and Dropquote. */
    val sayings: List<String> by lazy { lines("/words/sayings.txt.gz") }

    private fun lines(path: String): List<String> {
        val stream = OpenContent::class.java.getResourceAsStream(path) ?: error("Missing $path")
        return GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).useLines { seq ->
            seq.filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        }
    }
}
