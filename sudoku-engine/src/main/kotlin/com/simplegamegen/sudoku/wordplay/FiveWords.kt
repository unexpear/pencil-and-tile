package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.words.OpenContent

/**
 * Five-letter words from the shared lists. Guesses are every accepted word.
 * Easy and Medium hide an everyday WordNet lemma; Hard and Expert hide any playable lemma.
 */
object FiveWords {
    val valid: Set<String> by lazy { Lexicon.all.filter { it.length == 5 }.toHashSet() }

    val everyday: List<String> by lazy {
        valid.filter { it in Lexicon.common && it in OpenContent.lemmas }.sorted()
    }

    val answers: List<String> by lazy {
        valid.filter { it in OpenContent.lemmas }.sorted()
    }
}
