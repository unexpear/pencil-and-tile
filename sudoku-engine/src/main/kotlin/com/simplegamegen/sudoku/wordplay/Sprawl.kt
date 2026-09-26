package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import kotlin.random.Random

// ---------------- Letter Sprawl ----------------
//
// Chain touching letters (sideways, up, down or diagonally) into words without using a square twice.
// Longer words score more. Each level sets a goal from the everyday words hidden in the grid.

/** One square's letters: a single letter, or "QU" so Q is playable. */
fun sprawlFace(ch: Char): String = if (ch == 'Q') "QU" else ch.toString()

data class Sprawl(
    val level: LogicLevel,
    val seed: Long,
    val size: Int,
    /** size × size letters, row by row; 'Q' stands for the QU square. */
    val letters: String,
    val found: List<String> = emptyList(),
    val finished: Boolean = false,
    val hints: Int = 0,
    /** Squares of the latest hint's word start, shown until it's found. */
    val hinted: String? = null,
) {
    init {
        require(size in 3..6 && letters.length == size * size && letters.all { it in 'A'..'Z' } && hints >= 0)
    }

    val minLength: Int get() = minLengthOf(level)

    /** Every word the grid holds (at least [minLength] letters), with a path for each. */
    val answers: Map<String, List<Int>> by lazy { SprawlSolver.solve(letters, size, minLength) }
    val everyday: List<String> by lazy { answers.keys.filter { Lexicon.isCommon(it) }.sortedWith(compareBy({ -it.length }, { it })) }
    val goal: Int by lazy { goalOf(level, everyday) }
    val score: Int get() = found.sumOf { pointsFor(it) }
    val reached: Boolean get() = score >= goal
    val won: Boolean get() = finished && reached
    val over: Boolean get() = finished

    /** Why [path] can't be taken as a word, or null. */
    fun problem(path: List<Int>): String? {
        if (finished) return "The game is over."
        if (!SprawlSolver.connected(path, size)) return "Letters must touch, and each square is used once."
        val word = wordOf(path)
        if (word.length < minLength) return "Words need at least $minLength letters."
        if (word in found) return "Already found."
        if (!Lexicon.isWord(word)) return "Not in the word list."
        return null
    }

    fun wordOf(path: List<Int>): String = path.joinToString("") { sprawlFace(letters[it]) }

    fun take(path: List<Int>): Sprawl? {
        if (problem(path) != null) return null
        val word = wordOf(path)
        return copy(found = found + word, hinted = if (hinted != null && word.startsWith(hinted)) null else hinted)
    }

    fun finish(): Sprawl = copy(finished = true, hinted = null)

    /** Shows the first two letters of an everyday word not found yet, longest first. */
    fun hint(): Sprawl? {
        if (finished) return null
        val word = everyday.firstOrNull { it !in found } ?: return null
        return copy(hints = hints + 1, hinted = word.take(2))
    }

    /** The squares where the hinted start can be traced. */
    fun hintPath(): List<Int> {
        val start = hinted ?: return emptyList()
        val word = everyday.firstOrNull { it !in found && it.startsWith(start) } ?: return emptyList()
        val path = answers[word] ?: return emptyList()
        // Two letters of the word may be one QU square.
        var letters = 0
        return path.takeWhile { letters += sprawlFace(this.letters[it]).length; letters - sprawlFace(this.letters[it]).length < start.length }
    }

    companion object {
        fun sizeOf(level: LogicLevel) = listOf(4, 4, 5, 5)[level.ordinal]
        fun minLengthOf(level: LogicLevel) = listOf(3, 3, 4, 4)[level.ordinal]

        /** Points by length: 3 letters 1, 4 letters 2, then 3, 5, 8 and 13 for eight or more. */
        fun pointsFor(word: String): Int = when (word.length) {
            in 0..3 -> 1
            4 -> 2
            5 -> 3
            6 -> 5
            7 -> 8
            else -> 13
        }

        /** The goal: a share of the points in the grid's everyday words, rising with the level. */
        fun goalOf(level: LogicLevel, everyday: List<String>): Int {
            val share = listOf(0.25, 0.35, 0.4, 0.5)[level.ordinal]
            return maxOf(5, (everyday.sumOf { pointsFor(it) } * share).toInt())
        }

        /** Letter weights for the squares: roughly English, a little kinder to vowels. */
        private const val WEIGHTED = "EEEEEEEEEEEEAAAAAAAAAIIIIIIIIOOOOOOOONNNNNNRRRRRRTTTTTTLLLLSSSSSUUUUDDDDGGGBBCCMMPPFFHHVVWWYYKJXQZ"

        fun generate(seed: Long, level: LogicLevel): Sprawl {
            val random = Random(seed)
            val size = sizeOf(level)
            var best: Sprawl? = null
            repeat(60) {
                val letters = String(CharArray(size * size) { WEIGHTED.random(random) })
                val vowels = letters.count { it in "AEIOU" }
                if (vowels < size * size / 4 || vowels > size * size / 2 || letters.count { it == 'Q' } > 1) return@repeat
                val game = Sprawl(level, seed, size, letters)
                val words = game.everyday.size
                if (words >= wanted(level)) return game
                if (best == null || words > best!!.everyday.size) best = game
            }
            return best ?: Sprawl(level, seed, size, "SPRAWLINGTEAMSOR".take(size * size).padEnd(size * size, 'E'))
        }

        /** Everyday words a grid should hold at each level. */
        fun wanted(level: LogicLevel) = listOf(40, 30, 60, 50)[level.ordinal]
    }
}

object SprawlSolver {
    fun neighbours(i: Int, size: Int): List<Int> {
        val r = i / size; val c = i % size
        val out = ArrayList<Int>(8)
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr; val nc = c + dc
            if (nr in 0 until size && nc in 0 until size) out += nr * size + nc
        }
        return out
    }

    fun connected(path: List<Int>, size: Int): Boolean =
        path.isNotEmpty() && path.toSet().size == path.size && path.all { it in 0 until size * size } &&
            path.zipWithNext().all { (a, b) -> b in neighbours(a, size) }

    /** Every word in the grid with at least [minLength] letters, mapped to one path that spells it. */
    fun solve(letters: String, size: Int, minLength: Int): Map<String, List<Int>> {
        val out = HashMap<String, List<Int>>()
        val used = BooleanArray(size * size)
        val path = ArrayList<Int>()
        fun dfs(i: Int, prefix: String) {
            val word = prefix + sprawlFace(letters[i])
            if (!Lexicon.hasPrefix(word)) return
            used[i] = true; path += i
            if (word.length >= minLength && word !in out && Lexicon.isWord(word)) out[word] = path.toList()
            if (word.length < 15) for (n in neighbours(i, size)) if (!used[n]) dfs(n, word)
            used[i] = false; path.removeAt(path.lastIndex)
        }
        for (i in 0 until size * size) dfs(i, "")
        return out
    }
}

object SprawlCodec {
    fun encode(g: Sprawl) = listOf("1", g.level.name, g.seed.toString(), g.size.toString(), g.letters, g.found.joinToString(","),
        if (g.finished) "1" else "0", g.hints.toString(), g.hinted ?: "").joinToString("\n")

    fun decode(text: String): Sprawl? = try {
        val l = text.split('\n'); require(l.size == 9 && l[0] == "1")
        Sprawl(LogicLevel.valueOf(l[1]), l[2].toLong(), l[3].toInt(), l[4], if (l[5].isEmpty()) emptyList() else l[5].split(','),
            l[6] == "1", l[7].toInt(), l[8].ifEmpty { null })
    } catch (_: IllegalArgumentException) { null }
}
