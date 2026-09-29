package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import kotlin.random.Random

// ---------------- Letterfall ----------------
//
// Spell a word from tiles that share a side. Those tiles clear, the letters above fall,
// and new letters drop in from the top. A move limit and a target score decide the game.

data class Letterfall(
    val level: LogicLevel,
    val seed: Long,
    val rows: Int,
    val cols: Int,
    /** [rows] × [cols] letters, row by row from the top. */
    val letters: String,
    val score: Int = 0,
    val movesLeft: Int = movesOf(level),
    val movesUsed: Int = 0,
    /**
     * Combo already built. A word that uses a fresh tile scores at min(4, combo + 1);
     * any other word scores at ×1 and the combo returns to 1. The first word is ×1.
     */
    val combo: Int = 1,
    /** Cells that fell or were dealt by the last clear. */
    val fresh: Set<Int> = emptySet(),
    val lastWord: String? = null,
    val lastPoints: Int = 0,
    val found: Int = 0,
    /** Words cleared this game, in the order they were played. Repeats are allowed. */
    val words: List<String> = emptyList(),
    val won: Boolean = false,
    val lost: Boolean = false,
    /** How many refill letters have been dealt, so the next ones stay fixed for this seed. */
    val draw: Int = 0,
) {
    init {
        require(rows in 3..8 && cols in 3..8 && letters.length == rows * cols && letters.all { it in 'A'..'Z' })
        require(score >= 0 && movesLeft >= 0 && movesUsed >= 0 && found >= 0 && draw >= 0 && lastPoints >= 0)
        require(combo in 1..MAX_COMBO && fresh.all { it in letters.indices } && !(won && lost))
        require(words.size == found && words.all { word -> word.all { it in 'A'..'Z' } })
    }

    val target: Int get() = targetOf(level)
    val over: Boolean get() = won || lost

    /** The combo the next word scores when it uses a tile that just fell or arrived. */
    fun nextCombo(): Int = if (fresh.isEmpty()) 1 else minOf(MAX_COMBO, combo + 1)

    /** Why [path] can't be played, or null when it is a legal word. */
    fun problem(path: List<Int>): String? {
        if (over) return "The game is over."
        if (path.size < MIN_WORD) return "Words need at least $MIN_WORD letters."
        if (!connected(path, rows, cols)) return "Tiles must touch by a side, and each tile is used once."
        if (!Lexicon.isWord(wordOf(path))) return "Not in the word list."
        return null
    }

    fun wordOf(path: List<Int>): String = path.joinToString("") { letters[it].toString() }

    /** ×1, or the rising combo when [path] uses a tile from the last clear. */
    fun multiplierFor(path: List<Int>): Int =
        if (path.any { it in fresh }) minOf(MAX_COMBO, combo + 1) else 1

    /** Plays [path]. Null when [problem] rejects it. */
    fun play(path: List<Int>): Letterfall? {
        if (problem(path) != null) return null
        val word = wordOf(path)
        val multiplier = multiplierFor(path)
        val gained = pointsFor(word, multiplier)
        val cleared = letters.toCharArray()
        path.forEach { cleared[it] = HOLE }
        val settled = settle(cleared, rows, cols, seed, draw)
        val nextScore = score + gained
        val nextMoves = movesLeft - 1
        val didWin = nextScore >= target
        return copy(
            letters = settled.letters,
            score = nextScore,
            movesLeft = nextMoves,
            movesUsed = movesUsed + 1,
            combo = multiplier,
            fresh = settled.fresh,
            lastWord = word,
            lastPoints = gained,
            found = found + 1,
            words = words + word,
            won = didWin,
            lost = !didWin && nextMoves == 0,
            draw = draw + settled.drawn,
        )
    }

    companion object {
        const val MIN_WORD = 3
        const val MAX_COMBO = 4
        const val HOLE = '.'

        /** J, Q, X and Z, then K, V, W and Y. */
        const val RARE = "JQXZ"
        const val UNCOMMON = "KVWY"

        /** Roughly English, a little kinder to vowels. Same weights as Letter Sprawl. */
        private const val WEIGHTED =
            "EEEEEEEEEEEEAAAAAAAAAIIIIIIIIOOOOOOOONNNNNNRRRRRRTTTTTTLLLLSSSSSUUUUDDDDGGGBBCCMMPPFFHHVVWWYYKJXQZ"

        fun sizeOf(@Suppress("UNUSED_PARAMETER") level: LogicLevel) = 7
        fun movesOf(level: LogicLevel) = listOf(24, 20, 18, 15)[level.ordinal]
        fun targetOf(level: LogicLevel) = listOf(800, 1200, 1700, 2300)[level.ordinal]

        fun lengthPoints(length: Int): Int = when (length) {
            3 -> 50
            4 -> 100
            5 -> 160
            6 -> 250
            7 -> 360
            else -> if (length > 7) 360 + (length - 7) * 120 else 0
        }

        fun rarePoints(word: String): Int {
            var total = 0
            for (ch in word) total += when (ch) {
                in RARE -> 40
                in UNCOMMON -> 15
                else -> 0
            }
            return total
        }

        fun pointsFor(word: String, multiplier: Int): Int {
            require(multiplier in 1..MAX_COMBO)
            return (lengthPoints(word.length) + rarePoints(word)) * multiplier
        }

        /** The letter dealt at refill index [index] for [seed]. */
        fun tile(seed: Long, index: Int): Char =
            WEIGHTED[Random(seed + index * 0x9E3779B97L).nextInt(WEIGHTED.length)]

        fun neighbours(i: Int, rows: Int, cols: Int): List<Int> {
            val r = i / cols
            val c = i % cols
            val out = ArrayList<Int>(4)
            if (r > 0) out += i - cols
            if (r < rows - 1) out += i + cols
            if (c > 0) out += i - 1
            if (c < cols - 1) out += i + 1
            return out
        }

        /** Orthogonal, in bounds, and with no repeated tile. */
        fun connected(path: List<Int>, rows: Int, cols: Int): Boolean =
            path.isNotEmpty() && path.toSet().size == path.size && path.all { it in 0 until rows * cols } &&
                path.zipWithNext().all { (a, b) -> b in neighbours(a, rows, cols) }

        /**
         * Drops letters onto the holes marked [HOLE], then fills the top of each column.
         * A cell is fresh when its letter is new or fell from somewhere else.
         */
        fun settle(grid: CharArray, rows: Int, cols: Int, seed: Long, draw: Int): Settled {
            require(grid.size == rows * cols)
            val next = CharArray(grid.size)
            val fresh = HashSet<Int>()
            var drawn = 0
            for (c in 0 until cols) {
                val kept = ArrayList<Pair<Char, Int>>(rows)
                for (r in 0 until rows) {
                    val ch = grid[r * cols + c]
                    if (ch != HOLE) kept += ch to r
                }
                val holes = rows - kept.size
                for (r in 0 until holes) {
                    next[r * cols + c] = tile(seed, draw + drawn)
                    drawn++
                    fresh += r * cols + c
                }
                kept.forEachIndexed { k, (ch, oldRow) ->
                    val r = holes + k
                    next[r * cols + c] = ch
                    if (oldRow != r) fresh += r * cols + c
                }
            }
            return Settled(String(next), fresh, drawn)
        }

        /** True when some orthogonal path spells a word of at least [MIN_WORD] letters. */
        fun hasWord(letters: String, rows: Int, cols: Int): Boolean {
            val used = BooleanArray(letters.length)
            var nodes = 0
            fun dfs(i: Int, prefix: String): Boolean {
                if (++nodes > 40_000) return false
                val word = prefix + letters[i]
                if (!Lexicon.hasPrefix(word)) return false
                if (word.length >= MIN_WORD && Lexicon.isWord(word)) return true
                if (word.length >= 8) return false
                used[i] = true
                for (n in neighbours(i, rows, cols)) if (!used[n] && dfs(n, word)) return true
                used[i] = false
                return false
            }
            for (i in letters.indices) if (dfs(i, "")) return true
            return false
        }

        fun generate(seed: Long, level: LogicLevel): Letterfall {
            val rows = sizeOf(level)
            val cols = sizeOf(level)
            val random = Random(seed)
            val cells = rows * cols
            var best: String? = null
            repeat(40) {
                val letters = String(CharArray(cells) { WEIGHTED.random(random) })
                val vowels = letters.count { it in "AEIOU" }
                if (vowels < cells / 5 || vowels > cells * 3 / 5) return@repeat
                if (hasWord(letters, rows, cols)) return game(level, seed, letters)
                if (best == null) best = letters
            }
            val fallback = best?.takeIf { hasWord(it, rows, cols) } ?: "CAT".padEnd(cells, 'E')
            return game(level, seed, fallback)
        }

        private fun game(level: LogicLevel, seed: Long, letters: String) =
            Letterfall(level, seed, sizeOf(level), sizeOf(level), letters, movesLeft = movesOf(level))
    }
}

data class Settled(val letters: String, val fresh: Set<Int>, val drawn: Int)

object LetterfallCodec {
    fun encode(g: Letterfall) = listOf(
        "1", g.level.name, g.seed.toString(), g.rows.toString(), g.cols.toString(), g.letters,
        g.score.toString(), g.movesLeft.toString(), g.movesUsed.toString(), g.combo.toString(),
        g.fresh.sorted().joinToString(","), g.lastWord ?: "", g.lastPoints.toString(), g.found.toString(),
        g.words.joinToString(","), if (g.won) "1" else "0", if (g.lost) "1" else "0", g.draw.toString(),
    ).joinToString("\n")

    fun decode(text: String): Letterfall? = try {
        val l = text.split('\n')
        require(l.size == 18 && l[0] == "1")
        Letterfall(
            level = LogicLevel.valueOf(l[1]), seed = l[2].toLong(), rows = l[3].toInt(), cols = l[4].toInt(),
            letters = l[5], score = l[6].toInt(), movesLeft = l[7].toInt(), movesUsed = l[8].toInt(),
            combo = l[9].toInt(), fresh = if (l[10].isEmpty()) emptySet() else l[10].split(',').map { it.toInt() }.toSet(),
            lastWord = l[11].ifEmpty { null }, lastPoints = l[12].toInt(), found = l[13].toInt(),
            words = if (l[14].isEmpty()) emptyList() else l[14].split(','),
            won = l[15] == "1", lost = l[16] == "1", draw = l[17].toInt(),
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
