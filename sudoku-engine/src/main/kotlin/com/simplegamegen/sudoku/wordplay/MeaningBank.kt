package com.simplegamegen.sudoku.wordplay

/**
 * English words for Word Meaning. Definitions, synonyms and antonyms come from
 * Open English WordNet (CC BY 4.0, derived from Princeton WordNet). The reviewed
 * clues in meaning/en.tsv stay pinned to the sentences in contexts_en.tsv.
 * meaning/open.tsv adds words whose sentence is that same lexicon grab: the WordNet
 * example when it is a full sentence, otherwise one Tatoeba sentence for a word
 * with a single meaning. Levels run 0 (everyday) to 3 (rare). New words are level 1 or 2.
 */
object MeaningBank {
    private val reviewed = read("/meaning/en.tsv")
    private val added = read("/meaning/open.tsv").filter { extra -> reviewed.none { it.word == extra.word } }
    val entries: List<MeaningEntry> = reviewed + added

    private val byWord = entries.associateBy { it.word }
    fun entry(word: String): MeaningEntry? = byWord[word]

    fun parse(tsv: String): List<MeaningEntry> = tsv.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
        val c = line.split('\t')
        require(c.size == 8) { "bad meaning line: ${line.take(60)}" }
        fun list(s: String) = s.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        MeaningEntry(c[0], c[1], c[3], c[4], list(c[5]), list(c[6]), list(c[7]), c[2].toInt())
    }.toList()

    private fun read(path: String): List<MeaningEntry> {
        val stream = MeaningBank::class.java.getResourceAsStream(path) ?: if (path.endsWith("open.tsv")) return emptyList() else error("Missing $path")
        return parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    init {
        require(byWord.size == entries.size) { "duplicate Word Meaning entries" }
        entries.forEach { e -> require(e.level in 0..3 && e.right.isNotEmpty() && e.meaning.isNotBlank()) { e.word } }
    }
}
