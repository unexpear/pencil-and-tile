package com.simplegamegen.sudoku.duels

import kotlin.random.Random

/**
 * Classic Mastermind. The computer hides a code of [PEGS] pegs chosen from [COLORS] colors.
 * Duplicates are allowed. You have [GUESSES] guesses. After each guess, [KeyPegs.black] counts
 * pegs of the right color in the right place, and [KeyPegs.white] counts pegs of the right color
 * in the wrong place. Those are only counts: feedback never says which peg earned which key.
 *
 * You guess. The computer only sets the code. [setting] changes how constrained that code is:
 * Easy is a permutation of four colors, and those four change from game to game.
 * Medium is four different colors from all six, Hard repeats a color without being four of a kind,
 * and Expert is any classic code. [palette] is the four Easy colors; other levels ignore it.
 */
data class Mastermind(
    val setting: Int,
    val seed: Long,
    val secret: List<Int>,
    val guesses: List<List<Int>> = emptyList(),
    val feedback: List<KeyPegs> = emptyList(),
    val draft: List<Int> = emptyList(),
    val palette: List<Int> = listOf(0, 1, 2, 3),
) {
    init {
        require(setting in NAMES.indices)
        require(palette.size == PEGS && palette.toSet().size == PEGS && palette.all { it in 0 until COLORS })
        require(fits(setting, secret, palette))
        require(guesses.size == feedback.size && guesses.size <= GUESSES)
        require(draft.size <= PEGS && draft.all { it in 0 until COLORS })
        guesses.forEachIndexed { i, guess ->
            require(fits(setting, guess, palette)) { "illegal guess" }
            require(feedback[i] == score(secret, guess))
            if (i < guesses.lastIndex) require(guess != secret)
        }
        if (over) require(draft.isEmpty())
    }

    val won: Boolean get() = guesses.lastOrNull() == secret
    val lost: Boolean get() = !won && guesses.size >= GUESSES
    val over: Boolean get() = won || lost
    val guessesLeft: Int get() = GUESSES - guesses.size

    /** Why [draft] cannot be submitted, or null when Guess is legal. */
    fun problem(): String? = when {
        over -> "The game is over."
        draft.size != PEGS -> "Fill all four pegs."
        !fits(setting, draft, palette) -> CONSTRAINTS[setting]
        else -> null
    }

    /** Adds [color] to the end of the draft. Null when that peg is not allowed. */
    fun place(color: Int): Mastermind? {
        if (over || color !in 0 until COLORS || draft.size >= PEGS) return null
        if (setting == 0 && color !in palette) return null
        return copy(draft = draft + color)
    }

    /** Takes the last draft peg back. */
    fun backspace(): Mastermind? {
        if (over || draft.isEmpty()) return null
        return copy(draft = draft.dropLast(1))
    }

    /** Scores the draft and clears it. Null when the guess is illegal or the game is over. */
    fun submit(): Mastermind? {
        if (problem() != null) return null
        return copy(guesses = guesses + listOf(draft), feedback = feedback + score(secret, draft), draft = emptyList())
    }

    companion object {
        const val PEGS = 4
        const val COLORS = 6
        const val GUESSES = 10
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")
        val COLOR_NAMES = listOf("Red", "Orange", "Yellow", "Green", "Blue", "Purple")
        val CONSTRAINTS = listOf(
            "Four colors, each used once. The colors in play are marked.",
            "Four different colors, chosen from all six",
            "A color repeats, but not all four pegs are the same",
            "Any code, including repeats",
        )

        fun start(seed: Long, setting: Int): Mastermind {
            require(setting in NAMES.indices)
            val random = Random(seed)
            val palette = if (setting == 0) (0 until COLORS).shuffled(random).take(PEGS).sorted() else (0 until PEGS).toList()
            val secret = when (setting) {
                0 -> palette.shuffled(random)
                1 -> (0 until COLORS).shuffled(random).take(PEGS)
                2 -> hard(random)
                else -> List(PEGS) { random.nextInt(COLORS) }
            }
            return Mastermind(setting, seed, secret, palette = palette)
        }

        /** A secret for [setting], chosen from [seed]. The same seed always gives the same code. */
        fun secret(seed: Long, setting: Int): List<Int> = start(seed, setting).secret

        /** True when [code] is a legal secret, and a legal guess, at [setting]. */
        fun fits(setting: Int, code: List<Int>, palette: List<Int> = listOf(0, 1, 2, 3)): Boolean {
            if (setting !in NAMES.indices || code.size != PEGS || code.any { it !in 0 until COLORS }) return false
            val distinct = code.toSet().size
            return when (setting) {
                0 -> code.toSet() == palette.toSet()
                1 -> distinct == PEGS
                2 -> distinct in 2 until PEGS
                else -> true
            }
        }

        /**
         * Black then white counts. Matching positions are removed before the misplaced colors
         * are paired, so a repeated color cannot claim more keys than the code holds.
         */
        fun score(secret: List<Int>, guess: List<Int>): KeyPegs {
            require(secret.size == PEGS && guess.size == PEGS)
            var black = 0
            val left = ArrayList<Int>(PEGS)
            val pending = ArrayList<Int>(PEGS)
            for (i in secret.indices) {
                if (secret[i] == guess[i]) black++ else {
                    left += secret[i]
                    pending += guess[i]
                }
            }
            var white = 0
            for (color in pending) {
                val at = left.indexOf(color)
                if (at >= 0) {
                    white++
                    left.removeAt(at)
                }
            }
            return KeyPegs(black, white)
        }

        private fun hard(random: Random): List<Int> = when (random.nextInt(3)) {
            0 -> {
                val colors = (0 until COLORS).shuffled(random).take(3)
                (colors + colors[0]).shuffled(random)
            }
            1 -> {
                val colors = (0 until COLORS).shuffled(random).take(2)
                listOf(colors[0], colors[0], colors[1], colors[1]).shuffled(random)
            }
            else -> {
                val colors = (0 until COLORS).shuffled(random).take(2)
                listOf(colors[0], colors[0], colors[0], colors[1]).shuffled(random)
            }
        }
    }
}

/** Filled keys ([black]) and open keys ([white]). Positions are not recorded. */
data class KeyPegs(val black: Int, val white: Int) {
    init {
        require(black >= 0 && white >= 0 && black + white <= Mastermind.PEGS)
    }
}

object MastermindCodec {
    fun encode(g: Mastermind) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.secret.joinToString(","),
        g.guesses.joinToString(";") { it.joinToString(",") },
        g.feedback.joinToString(";") { "${it.black},${it.white}" },
        g.draft.joinToString(","),
        g.palette.joinToString(","),
    ).joinToString("\n")

    fun decode(text: String): Mastermind? = try {
        val lines = text.split('\n')
        require(lines.size in 7..8 && lines[0] == "1")
        Mastermind(
            lines[1].toInt(),
            lines[2].toLong(),
            pegs(lines[3]),
            rows(lines[4]),
            keys(lines[5]),
            if (lines[6].isEmpty()) emptyList() else pegs(lines[6]),
            if (lines.size == 8) pegs(lines[7]) else listOf(0, 1, 2, 3),
        )
    } catch (_: IllegalArgumentException) { null }

    private fun pegs(line: String) = line.split(',').map { it.toInt() }

    private fun rows(line: String): List<List<Int>> =
        if (line.isEmpty()) emptyList() else line.split(';').map { pegs(it) }

    private fun keys(line: String): List<KeyPegs> = if (line.isEmpty()) emptyList() else line.split(';').map { pair ->
        val parts = pair.split(',')
        require(parts.size == 2)
        KeyPegs(parts[0].toInt(), parts[1].toInt())
    }
}
