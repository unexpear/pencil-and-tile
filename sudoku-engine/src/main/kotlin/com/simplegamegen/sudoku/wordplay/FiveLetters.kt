package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import kotlin.random.Random

// ---------------- Five Letters ----------------

/** How one letter of a guess compares with the answer. */
enum class Mark { RIGHT, ELSEWHERE, ABSENT }

/**
 * Five Letters: find the hidden five-letter word. Each guess marks every letter as in the right
 * spot, in the word but elsewhere, or not in the word. [revealed] holds positions shown by hints.
 */
data class FiveLetters(
    val level: LogicLevel,
    val seed: Long,
    val answer: String,
    val guesses: List<String> = emptyList(),
    val revealed: Set<Int> = emptySet(),
    val hints: Int = 0,
) {
    init {
        require(answer.length == 5 && answer.all { it in 'A'..'Z' })
        require(guesses.size <= limitOf(level) && guesses.all { g -> g.length == 5 && g.all { it in 'A'..'Z' } })
        require(revealed.all { it in 0..4 } && hints >= 0)
    }

    val limit: Int get() = limitOf(level)
    val won: Boolean get() = guesses.lastOrNull() == answer
    val lost: Boolean get() = !won && guesses.size >= limit
    val over: Boolean get() = won || lost
    /** Hard and Expert: letters already found must be used again. */
    val strict: Boolean get() = level >= LogicLevel.HARD

    fun marks(guess: String): List<Mark> = score(guess, answer)

    /** Best mark seen so far for each letter, for the keyboard. */
    fun keyMarks(): Map<Char, Mark> {
        val out = HashMap<Char, Mark>()
        guesses.forEach { g -> g.toList().zip(marks(g)).forEach { (ch, m) -> if (out[ch] == null || m.ordinal < out[ch]!!.ordinal) out[ch] = m } }
        return out
    }

    /** Why [guess] can't be played, or null when it can. */
    fun problem(guess: String): String? {
        if (over) return "The game is over."
        if (guess.length != 5 || guess.any { it !in 'A'..'Z' }) return "Guesses have five letters."
        if (level != LogicLevel.EASY && guess !in FiveWords.valid) return "Not in the word list."
        if (strict) {
            guesses.forEach { g ->
                g.toList().zip(marks(g)).forEachIndexed { i, (ch, m) -> if (m == Mark.RIGHT && guess[i] != ch) return "Letter ${i + 1} must be $ch." }
            }
            val needed = guesses.flatMap { g -> g.toList().zip(marks(g)).filter { it.second == Mark.ELSEWHERE }.map { it.first } }.toSet()
            needed.firstOrNull { it !in guess }?.let { return "The guess must use $it." }
            revealed.firstOrNull { guess[it] != answer[it] }?.let { return "Letter ${it + 1} must be ${answer[it]}." }
        }
        return null
    }

    fun guess(word: String): FiveLetters? = if (problem(word) == null) copy(guesses = guesses + word) else null

    /** Shows one letter of the answer in place, starting with positions not yet found. */
    fun hint(): FiveLetters? {
        if (over) return null
        val found = guesses.flatMap { g -> marks(g).withIndex().filter { it.value == Mark.RIGHT }.map { it.index } }.toSet()
        val next = (0..4).firstOrNull { it !in revealed && it !in found } ?: return null
        return copy(revealed = revealed + next, hints = hints + 1)
    }

    companion object {
        fun limitOf(level: LogicLevel) = listOf(7, 6, 6, 5)[level.ordinal]

        /** Two passes, so a repeated letter is only marked as often as the answer holds it. */
        fun score(guess: String, answer: String): List<Mark> {
            val marks = MutableList(5) { Mark.ABSENT }
            val left = IntArray(26)
            for (i in 0..4) if (guess[i] == answer[i]) marks[i] = Mark.RIGHT else left[answer[i] - 'A']++
            for (i in 0..4) if (marks[i] != Mark.RIGHT && left[guess[i] - 'A'] > 0) { marks[i] = Mark.ELSEWHERE; left[guess[i] - 'A']-- }
            return marks
        }

        fun generate(seed: Long, level: LogicLevel): FiveLetters {
            val random = Random(seed)
            // Easier levels draw from the most everyday words.
            val pool = if (level <= LogicLevel.MEDIUM) FiveWords.everyday else FiveWords.answers
            return FiveLetters(level, seed, pool.random(random))
        }
    }
}

object FiveLettersCodec {
    fun encode(g: FiveLetters) = listOf("1", g.level.name, g.seed.toString(), g.answer, g.guesses.joinToString(","),
        g.revealed.sorted().joinToString(","), g.hints.toString()).joinToString("\n")

    fun decode(text: String): FiveLetters? = try {
        val l = text.split('\n'); require(l.size == 7 && l[0] == "1")
        FiveLetters(LogicLevel.valueOf(l[1]), l[2].toLong(), l[3], if (l[4].isEmpty()) emptyList() else l[4].split(','),
            if (l[5].isEmpty()) emptySet() else l[5].split(',').map { it.toInt() }.toSet(), l[6].toInt())
    } catch (_: IllegalArgumentException) { null }
}
