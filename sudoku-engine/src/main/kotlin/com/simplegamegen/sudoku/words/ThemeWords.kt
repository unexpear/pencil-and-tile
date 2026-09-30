package com.simplegamegen.sudoku.words

import com.simplegamegen.sudoku.wordplay.Lexicon

/**
 * Grows a word-search and hangman theme with single everyday words from Lone Letter's
 * WordNet category lists. The original theme words stay, so a saved answer is still eligible.
 */
internal object ThemeWords {
    private val groups = mapOf(
        "Nature" to listOf("trees", "flowers"),
        "Animals" to listOf("animals", "birds", "fish", "insects", "dogs"),
        "Everyday" to listOf("furniture", "kitchen", "clothes", "tools", "toys"),
        "Food" to listOf("fruit", "vegetables", "dishes", "drinks", "desserts", "cheeses", "herbs"),
        "Travel" to listOf("vehicles", "buildings"),
        "Sports" to listOf("sports"),
        "Music" to listOf("instruments", "dances"),
        "Ocean" to listOf("fish"),
        "Garden" to listOf("flowers", "trees", "herbs"),
        "Science" to listOf("metals"),
    )

    fun expand(title: String, hand: List<String>): List<String> {
        val ids = groups[title] ?: return hand
        return (hand + ids.flatMap { words[it].orEmpty() }).distinct()
    }

    private val words: Map<String, List<String>> by lazy {
        val wanted = groups.values.flatten().toSet()
        val stream = ThemeWords::class.java.getResourceAsStream("/categories/en.tsv") ?: error("Missing category list")
        val out = HashMap<String, List<String>>()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val tab = line.indexOf('\t')
                val tab2 = line.indexOf('\t', tab + 1)
                if (tab < 0 || tab2 < 0) return@forEach
                val id = line.substring(0, tab)
                if (id !in wanted) return@forEach
                val answers = ArrayList<String>()
                for (part in line.substring(tab2 + 1).split(',')) {
                    val colon = part.lastIndexOf(':')
                    if (colon <= 0) continue
                    val raw = part.substring(0, colon)
                    if (' ' in raw || raw.any { it !in 'a'..'z' }) continue
                    val word = raw.uppercase()
                    if (word.length !in 3..12 || !Lexicon.isWord(word)) continue
                    val score = part.substring(colon + 1).toIntOrNull() ?: continue
                    if (score > 0 || Lexicon.isCommon(word)) answers += word
                }
                out[id] = answers
            }
        }
        out
    }
}
