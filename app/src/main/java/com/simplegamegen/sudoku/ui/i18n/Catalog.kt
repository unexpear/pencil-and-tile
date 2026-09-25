package com.simplegamegen.sudoku.ui.i18n

/**
 * One language's translations, keyed by the English source text. Keys may contain numbered
 * placeholders ({0}, {1}) that match any text; a translation can put them in any order, so each
 * language keeps its own word order. Matched values are translated too (a level name inside a
 * sentence, say). Keys starting with "@context:" tell apart words that English spells the same.
 * Text joined with a separator (", ", " · ", a new line) is translated part by part, and a key "@sep:, "
 * gives the language's own list comma.
 */
class Catalog(entries: Map<String, String>) {
    private class Pattern(val regex: Regex, val slots: List<Int>, val target: String, val weight: Int)

    private val exact = HashMap<String, String>()
    private val patterns: List<Pattern>
    private val cache = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 4000
    }

    init {
        val found = mutableListOf<Pattern>()
        for ((key, value) in entries) {
            if (!PLACEHOLDER.containsMatchIn(key)) { exact[key] = value; continue }
            val literal = PLACEHOLDER.replace(key, "")
            // A key made only of placeholders and punctuation would match everything.
            if (literal.count { it.isLetter() } < 2) continue
            val slots = PLACEHOLDER.findAll(key).map { it.groupValues[1].toInt() }.toList()
            var regex = ""; var last = 0
            PLACEHOLDER.findAll(key).forEach { m ->
                regex += Regex.escape(key.substring(last, m.range.first)) + "(.+?)"
                last = m.range.last + 1
            }
            regex += Regex.escape(key.substring(last))
            found += Pattern(Regex("^$regex$", RegexOption.DOT_MATCHES_ALL), slots, value, literal.length)
        }
        patterns = found.sortedByDescending { it.weight }
    }

    val size: Int get() = exact.size + patterns.size

    fun translate(text: String): String = synchronized(cache) { cache[text] } ?: resolve(text, 0).also { synchronized(cache) { cache[text] = it } }

    private fun resolve(text: String, depth: Int): String {
        if (text.none { it.isLetter() }) return text
        exact[text]?.let { return it }
        if (text.startsWith(CONTEXT)) return exact[text] ?: stripContext(text)
        val trimmed = text.trim()
        if (trimmed != text && trimmed.isNotEmpty()) return text.replace(trimmed, resolve(trimmed, depth))
        val joined = SEPARATORS.firstOrNull { it in text }
        if (depth < 3) {
            // A placeholder that swallows a separator ("Row {0}" matching "Row 3, shaded") is a worse fit than
            // splitting the parts, so such matches are only used when nothing else works.
            var loose: String? = null
            for (p in patterns) {
                val m = p.regex.matchEntire(text) ?: continue
                val captured = m.groupValues.drop(1)
                if (joined != null && captured.any { v -> SEPARATORS.any { it in v } }) { if (loose == null) loose = fill(p, captured, depth); continue }
                return fill(p, captured, depth)
            }
            if (joined == null && loose != null) return loose
        }
        // Sentences built by joining parts: translate each part.
        if (joined != null) return text.split(joined).joinToString(exact["${CONTEXT}sep:$joined"] ?: joined) { resolve(it, depth + 1) }
        return text
    }

    private fun fill(p: Pattern, captured: List<String>, depth: Int): String {
        val values = p.slots.mapIndexed { i, slot -> slot to resolve(captured[i], depth + 1) }.toMap()
        return PLACEHOLDER.replace(p.target) { values[it.groupValues[1].toInt()] ?: it.value }
    }

    companion object {
        private val PLACEHOLDER = Regex("\\{(\\d)\\}")
        private val SEPARATORS = listOf("\n", " · ", " — ", ", ")
        const val CONTEXT = "@"

        /** English text for a key: drops a "@context:" prefix. */
        fun stripContext(key: String): String = if (key.startsWith(CONTEXT)) key.substringAfter(':') else key

        /** Parses "English<TAB>translation" lines; blank lines and lines starting with # are skipped. */
        fun parse(tsv: String): Map<String, String> = tsv.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") && '\t' in it }
            .associate { line ->
                val (k, v) = line.split('\t', limit = 2)
                unescape(k) to unescape(v.trimEnd('\r'))
            }

        /** Reads one language column from the master table (header row: en, zh, ja, es, de). Empty cells are skipped. */
        fun parseColumn(tsv: String, tag: String): Map<String, String> {
            val lines = tsv.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
            if (lines.isEmpty()) return emptyMap()
            val column = lines.first().split('\t').indexOf(tag)
            if (column <= 0) return emptyMap()
            return lines.drop(1).mapNotNull { line ->
                val cells = line.trimEnd('\r').split('\t')
                val value = cells.getOrNull(column)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                unescape(cells[0]) to unescape(value)
            }.toMap()
        }

        private fun unescape(s: String) =s.replace("\\n", "\n")
    }
}
