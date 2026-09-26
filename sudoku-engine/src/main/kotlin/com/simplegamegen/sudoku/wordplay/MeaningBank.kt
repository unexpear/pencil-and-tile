package com.simplegamegen.sudoku.wordplay

/**
 * English words for Word Meaning. Definitions, synonyms and antonyms come from
 * Open English WordNet (CC BY 4.0, derived from Princeton WordNet), joined with original context clues
 * pinned to specific dictionary senses in contexts_en.tsv and hand-written answer extras by
 * tools/meaning/build_meaning_bank.py into resources/meaning/en.tsv. Levels run 0 (everyday) to 3 (rare).
 */
object MeaningBank {
    val entries: List<MeaningEntry> = parse(checkNotNull(MeaningBank::class.java.getResourceAsStream("/meaning/en.tsv")) { "meaning/en.tsv is missing" }
        .bufferedReader(Charsets.UTF_8).use { it.readText() })

    private val byWord = entries.associateBy { it.word }
    fun entry(word: String): MeaningEntry? = byWord[word]

    fun parse(tsv: String): List<MeaningEntry> = tsv.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
        val c = line.split('\t')
        require(c.size == 8) { "bad meaning line: ${line.take(60)}" }
        fun list(s: String) = s.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        MeaningEntry(c[0], c[1], c[3], c[4], list(c[5]), list(c[6]), list(c[7]), c[2].toInt())
    }.toList()

    init {
        require(byWord.size == entries.size) { "duplicate Word Meaning entries" }
        entries.forEach { e -> require(e.level in 0..3 && e.right.isNotEmpty() && e.meaning.isNotBlank()) { e.word } }
    }
}
