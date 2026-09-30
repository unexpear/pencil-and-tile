package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import kotlin.random.Random

/**
 * A handful of letters and a clock. Spell the longest word you can before time runs out.
 * Each letter may be used only as often as it was drawn. Reaching the target length wins.
 * When time runs out, [best] is a longest accepted word from the rack.
 */
data class LetterDraw(
    val level: LogicLevel,
    val seed: Long,
    val letters: String,
    val target: Int,
    val limit: Int,
    val used: Int = 0,
    val typed: String = "",
    val answer: String = "",
    val best: String,
    val won: Boolean = false,
    val lost: Boolean = false,
) {
    init {
        require(letters.length in 7..9 && letters.all { it in 'A'..'Z' })
        require(target in 4..8 && limit in 15..120 && used in 0..limit)
        require(typed.all { it in 'A'..'Z' } && withinRack(typed))
        require(answer.isEmpty() || (Lexicon.isWord(answer) && withinRack(answer)))
        require(best.isNotEmpty() && Lexicon.isWord(best) && withinRack(best) && best.length >= target)
        require(!(won && lost))
    }

    val over: Boolean get() = won || lost
    val left: Int get() = limit - used

    fun spare(letter: Char): Int = letters.count { it == letter } - typed.count { it == letter }
    private fun withinRack(word: String): Boolean =
        word.groupingBy { it }.eachCount().all { (letter, n) -> letters.count { it == letter } >= n }

    fun problem(word: String = typed): String? {
        if (over) return "The game is over."
        if (word.isEmpty()) return "Spell a word."
        if (!withinRack(word)) return "Not enough of those letters."
        if (!Lexicon.isWord(word)) return "Not in the word list."
        if (word.length <= answer.length) return "Already have a longer word."
        return null
    }

    fun type(letter: Char): LetterDraw? {
        if (over || letter !in 'A'..'Z' || spare(letter) <= 0) return null
        return copy(typed = typed + letter)
    }

    fun backspace(): LetterDraw? = if (over || typed.isEmpty()) null else copy(typed = typed.dropLast(1))

    /** Keeps [typed] when it is a real longer word. Reaching [target] wins at once. */
    fun submit(): LetterDraw? {
        val word = typed
        if (problem(word) != null) return null
        val next = copy(answer = word, typed = "")
        return if (word.length >= target) next.copy(won = true) else next
    }

    fun tick(seconds: Int = 1): LetterDraw {
        if (over || seconds <= 0) return this
        val next = (used + seconds).coerceAtMost(limit)
        if (next < limit) return copy(used = next)
        return if (answer.length >= target) copy(used = limit, won = true) else copy(used = limit, lost = true)
    }

    companion object {
        private const val VOWELS = "AAEEEIIIOOOU"
        private const val CONSONANTS = "BBCCDDDDFFGGGHHJKLLLLMMNNNNNPPQRRRRRRSSSSTTTTTTVWWXYYZ"

        fun secondsOf(level: LogicLevel) = listOf(90, 60, 45, 30)[level.ordinal]
        fun targetOf(level: LogicLevel) = listOf(4, 5, 6, 7)[level.ordinal]

        fun generate(seed: Long, level: LogicLevel): LetterDraw {
            val random = Random(seed)
            val size = if (level.ordinal >= LogicLevel.HARD.ordinal) 9 else 8
            val vowels = if (level == LogicLevel.EASY) 4 else 3
            val target = targetOf(level)
            repeat(48) {
                val drawn = (List(vowels) { VOWELS.random(random) } + List(size - vowels) { CONSONANTS.random(random) })
                    .shuffled(random).joinToString("")
                val best = longest(drawn)
                if (best != null && best.length >= target) {
                    return LetterDraw(level, seed, drawn, target, secondsOf(level), best = best)
                }
            }
            error("no letter draw for $level")
        }

        /** A longest accepted word that can be spelled from [letters]. */
        fun longest(letters: String): String? {
            var best: String? = null
            for (word in Lexicon.all) {
                if (word.length > letters.length) continue
                if (best != null && word.length < best.length) continue
                if (!fits(letters, word)) continue
                if (best == null || word.length > best.length || (word.length == best.length && Lexicon.isCommon(word) && !Lexicon.isCommon(best))) {
                    best = word
                }
            }
            return best
        }

        fun fits(letters: String, word: String): Boolean =
            word.groupingBy { it }.eachCount().all { (letter, n) -> letters.count { it == letter } >= n }
    }
}

object LetterDrawCodec {
    fun encode(g: LetterDraw) = listOf(
        "1", g.level.name, g.seed.toString(), g.letters, g.target.toString(), g.limit.toString(),
        g.used.toString(), g.typed, g.answer, g.best, if (g.won) "1" else "0", if (g.lost) "1" else "0",
    ).joinToString("\n")

    fun decode(text: String): LetterDraw? = try {
        val lines = text.split('\n')
        require(lines.size == 12 && lines[0] == "1")
        LetterDraw(
            LogicLevel.valueOf(lines[1]), lines[2].toLong(), lines[3], lines[4].toInt(), lines[5].toInt(),
            lines[6].toInt(), lines[7], lines[8], lines[9], lines[10] == "1", lines[11] == "1",
        )
    } catch (_: IllegalArgumentException) { null }
}
