package com.simplegamegen.sudoku.words

import kotlin.random.Random

data class HangmanPuzzle(val answer: String, val theme: Int, val difficulty: WordDifficulty, val seed: Long) {
    init { require(answer in Hangman.words(theme, difficulty)) }
    val mistakeLimit: Int get() = 8 - difficulty.ordinal
}

object Hangman {
    fun words(theme: Int, difficulty: WordDifficulty): List<String> {
        require(theme in WordCatalog.searches.indices)
        val lengths = when (difficulty) {
            WordDifficulty.EASY -> 3..5
            WordDifficulty.MEDIUM -> 4..7
            WordDifficulty.HARD -> 5..12
            WordDifficulty.EXPERT -> 6..12
        }
        return WordCatalog.searches[theme].second.filter { it.length in lengths }
    }

    fun generate(seed: Long, theme: Int, difficulty: WordDifficulty, excluded: Set<String> = emptySet()): HangmanPuzzle {
        val choices = words(theme, difficulty).filter { it !in excluded }
        require(choices.isNotEmpty()) { "All words are temporarily excluded" }
        return HangmanPuzzle(choices.random(Random(seed)), theme, difficulty, seed)
    }
}

data class HangmanProgress(val puzzle: HangmanPuzzle, val guesses: String = "", val hints: Int = 0) {
    init {
        require(guesses.all { it in 'A'..'Z' } && guesses.toSet().size == guesses.length)
        require(hints in 0..guesses.count { it in puzzle.answer })
        var wrong = 0
        val seen = mutableSetOf<Char>()
        guesses.forEach { letter ->
            require(wrong < puzzle.mistakeLimit && !puzzle.answer.all { it in seen }) { "Guesses after game ended" }
            seen += letter
            if (letter !in puzzle.answer) wrong++
        }
    }
    val mistakes: Int get() = guesses.count { it !in puzzle.answer }
    val won: Boolean get() = puzzle.answer.all { it in guesses }
    val lost: Boolean get() = mistakes >= puzzle.mistakeLimit
    val complete: Boolean get() = won || lost
    val display: String get() = puzzle.answer.map { if (it in guesses || lost) it else '_' }.joinToString(" ")

    fun guess(letter: Char): HangmanProgress {
        val upper = letter.uppercaseChar()
        if (complete || upper !in 'A'..'Z' || upper in guesses) return this
        return copy(guesses = guesses + upper)
    }
    fun hint(): HangmanProgress {
        if (complete) return this
        val letter = puzzle.answer.first { it !in guesses }
        return copy(guesses = guesses + letter, hints = hints + 1)
    }
}

object HangmanSaveCodec {
    fun encode(p: HangmanProgress): String = listOf("1", p.puzzle.difficulty.name, p.puzzle.theme.toString(),
        p.puzzle.answer, p.puzzle.seed.toString(), p.guesses, p.hints.toString()).joinToString("\n")
    fun decode(text: String): HangmanProgress? = try {
        require(text.length < 1000)
        val parts = text.split('\n')
        require(parts.size == 7 && parts[0] == "1")
        HangmanProgress(HangmanPuzzle(parts[3], parts[2].toInt(), WordDifficulty.valueOf(parts[1]), parts[4].toLong()),
            parts[5], parts[6].toInt())
    } catch (_: IllegalArgumentException) { null }
}
