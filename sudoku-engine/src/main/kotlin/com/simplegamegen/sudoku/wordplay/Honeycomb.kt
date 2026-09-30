package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import kotlin.random.Random

/**
 * Seven letters, one of them required. Spell words from those letters.
 * Everyday words score their length. Any other accepted word scores 1.
 * A word that uses all seven letters scores 7 more.
 */
data class Honeycomb(
    val level: LogicLevel,
    val seed: Long,
    val center: Char,
    val outer: String,
    val minLength: Int,
    val target: Int,
    val found: List<String> = emptyList(),
    val score: Int = 0,
    val won: Boolean = false,
) {
    init {
        require(center in 'A'..'Z' && outer.length == 6 && outer.all { it in 'A'..'Z' && it != center })
        require(outer.toSet().size == 6 && minLength in 4..5 && target > 0 && score >= 0)
        require(found.distinct() == found && found.all { points(it) > 0 })
    }

    val over: Boolean get() = won
    private fun allowed(): Set<Char> = outer.toSet() + center

    fun problem(word: String): String? {
        val clean = word.trim().uppercase()
        if (over) return "The game is over."
        if (clean.length < minLength) return "Words need at least $minLength letters."
        if (center !in clean) return "Use the middle letter."
        if (clean.any { it !in allowed() }) return "Use only the seven letters."
        if (clean in found) return "Already found."
        if (!Lexicon.isWord(clean)) return "Not in the word list."
        return null
    }

    /** Length for an everyday word, otherwise 1. All seven letters add 7. */
    fun points(word: String): Int {
        val clean = word.trim().uppercase()
        if (clean.length < minLength || center !in clean || clean.any { it !in allowed() } || !Lexicon.isWord(clean)) return 0
        val tier = if (Lexicon.isCommon(clean)) clean.length else 1
        return tier + if (clean.toSet().size == 7) 7 else 0
    }

    fun play(word: String): Honeycomb? {
        if (problem(word) != null) return null
        val clean = word.trim().uppercase()
        val next = score + points(clean)
        return copy(found = found + clean, score = next, won = next >= target)
    }

    /** The shortest everyday word still missing, for a hint. */
    fun hintWord(): String? = answers(center, outer, minLength)
        .filter { Lexicon.isCommon(it) && it !in found }
        .minByOrNull { it.length }

    companion object {
        private val cache = HashMap<String, List<String>>()

        fun answers(center: Char, outer: String, minLength: Int): List<String> {
            val key = "$center$outer$minLength"
            synchronized(cache) {
                cache[key]?.let { return it }
            }
            val letters = outer.toSet() + center
            val found = Lexicon.all.filter { word ->
                word.length >= minLength && center in word && word.all { it in letters }
            }
            synchronized(cache) { cache[key] = found }
            return found
        }

        fun generate(seed: Long, level: LogicLevel): Honeycomb {
            val random = Random(seed)
            val minLength = if (level.ordinal >= LogicLevel.HARD.ordinal) 5 else 4
            val share = listOf(0.30, 0.45, 0.60, 0.75)[level.ordinal]
            val pangrams = Lexicon.common.filter { it.toSet().size == 7 && it.length >= 7 }.sorted().shuffled(random)
            require(pangrams.isNotEmpty()) { "no seven-letter everyday words" }
            for (word in pangrams.take(48)) {
                val letters = word.toSet()
                val vowels = letters.filter { it in "AEIOU" }.ifEmpty { letters.toList() }
                for (center in vowels.shuffled(random)) {
                    val outer = (letters - center).sorted().joinToString("")
                    val common = answers(center, outer, minLength).filter { Lexicon.isCommon(it) }
                    if (common.size < 8) continue
                    val total = common.sumOf { candidate ->
                        candidate.length + if (candidate.toSet().size == 7) 7 else 0
                    }
                    val target = maxOf(12, (total * share).toInt())
                    if (total >= target) return Honeycomb(level, seed, center, outer, minLength, target)
                }
            }
            error("no honeycomb for $level")
        }
    }
}

object HoneycombCodec {
    fun encode(g: Honeycomb) = listOf(
        "1", g.level.name, g.seed.toString(), g.center.toString(), g.outer,
        g.minLength.toString(), g.target.toString(), g.found.joinToString(","),
        g.score.toString(), if (g.won) "1" else "0",
    ).joinToString("\n")

    fun decode(text: String): Honeycomb? = try {
        val lines = text.split('\n')
        require(lines.size == 10 && lines[0] == "1")
        Honeycomb(
            LogicLevel.valueOf(lines[1]), lines[2].toLong(), lines[3].single(), lines[4],
            lines[5].toInt(), lines[6].toInt(),
            if (lines[7].isEmpty()) emptyList() else lines[7].split(','),
            lines[8].toInt(), lines[9] == "1",
        )
    } catch (_: IllegalArgumentException) { null }
}
