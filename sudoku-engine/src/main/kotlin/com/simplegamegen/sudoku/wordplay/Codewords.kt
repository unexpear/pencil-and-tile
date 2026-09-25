package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

// ---------------- Code cracker ----------------

/**
 * Code cracker (codeword): a crossword grid with no clues where each letter is replaced
 * by a number. [grid] holds letters with '#' for blocks; [code] gives each letter's
 * number (0 when unused). [guesses] holds the player's letter for number n at n - 1.
 */
data class CodeCracker(
    val level: LogicLevel,
    val seed: Long,
    val size: Int,
    val grid: String,
    val code: List<Int>,
    val guesses: String = "_".repeat(code.count { it > 0 }),
    val given: Set<Int> = emptySet(),
    val hints: Int = 0,
) {
    val count: Int get() = guesses.length

    init {
        require(size in 5..15 && grid.length == size * size && grid.all { it == '#' || it in 'A'..'Z' })
        require(code.size == 26 && code.filter { it > 0 }.sorted() == (1..count).toList())
        require(grid.filter { it != '#' }.toSet() == (0 until 26).filter { code[it] > 0 }.map { 'A' + it }.toSet())
        require(guesses.all { it == '_' || it in 'A'..'Z' } && guesses.filter { it != '_' }.let { it.toSet().size == it.length })
        require(given.all { it in 1..count && guesses[it - 1] == letterOf(it) } && hints >= 0)
    }

    fun numberAt(cell: Int): Int = if (grid[cell] == '#') 0 else code[grid[cell] - 'A']
    fun letterOf(number: Int): Char = 'A' + code.indexOf(number)
    fun guessOf(number: Int): Char? = guesses[number - 1].takeIf { it != '_' }
    val complete: Boolean get() = (1..count).all { guessOf(it) == letterOf(it) }
    fun mistakes(): Set<Int> = (1..count).filter { n -> guessOf(n)?.let { it != letterOf(n) } == true }.toSet()
    /** Letters already placed somewhere, for the A–Z tracker. */
    val usedLetters: Set<Char> get() = guesses.filter { it != '_' }.toSet()

    /** Puts [letter] on every square numbered [number]; a letter moves off any other number. */
    fun guess(number: Int, letter: Char?): CodeCracker {
        if (complete || number !in 1..count || number in given) return this
        if (letter != null && (letter !in 'A'..'Z' || given.any { letterOf(it) == letter })) return this
        val next = guesses.toCharArray()
        if (letter != null) next.indices.filter { next[it] == letter }.forEach { next[it] = '_' }
        next[number - 1] = letter ?: '_'
        return copy(guesses = String(next))
    }

    fun hint(preferred: Int?): CodeCracker? {
        if (complete) return null
        val n = preferred?.takeIf { it in 1..count && guessOf(it) != letterOf(it) }
            ?: grid.indices.map(::numberAt).firstOrNull { it > 0 && guessOf(it) != letterOf(it) } ?: return null
        return reveal(n).copy(hints = hints + 1)
    }

    private fun reveal(n: Int): CodeCracker {
        val letter = letterOf(n)
        val next = guesses.toCharArray()
        next.indices.filter { next[it] == letter }.forEach { next[it] = '_' }
        next[n - 1] = letter
        return copy(guesses = String(next), given = given + n)
    }

    companion object {
        fun sizeOf(level: LogicLevel) = listOf(9, 11, 13, 13)[level.ordinal]
        fun reveals(level: LogicLevel) = listOf(5, 3, 2, 1)[level.ordinal]

        /** Packs bundled words into a connected crossword-style grid. */
        fun layout(size: Int, random: Random): String {
            val words = WordBank.words.filter { it.length in 3..size && it.all { c -> c in 'A'..'Z' } }.sorted()
            var best = ""; var bestFill = -1
            repeat(16) {
                checkpoint()
                val grid = CharArray(size * size) { '#' }
                val used = mutableSetOf<String>()
                fun at(r: Int, c: Int) = if (r in 0 until size && c in 0 until size) grid[r * size + c] else '#'
                fun fits(word: String, start: Int, down: Boolean): Int {
                    val r0 = start / size; val c0 = start % size
                    val dr = if (down) 1 else 0; val dc = 1 - dr
                    if (r0 + dr * (word.length - 1) >= size || c0 + dc * (word.length - 1) >= size) return -1
                    if (at(r0 - dr, c0 - dc) != '#' || at(r0 + dr * word.length, c0 + dc * word.length) != '#') return -1
                    var crossings = 0
                    for (i in word.indices) {
                        val r = r0 + dr * i; val c = c0 + dc * i
                        val here = grid[r * size + c]
                        if (here != '#') {
                            if (here != word[i]) return -1
                            crossings++
                        } else if (at(r - dc, c - dr) != '#' || at(r + dc, c + dr) != '#') return -1
                    }
                    return if (crossings == word.length) -1 else crossings
                }
                fun place(word: String, start: Int, down: Boolean): Boolean {
                    val before = grid.copyOf()
                    val dr = if (down) 1 else 0; val dc = 1 - dr
                    word.indices.forEach { grid[(start / size + dr * it) * size + start % size + dc * it] = word[it] }
                    used += word
                    // Undo placements that would make an unplanned run of letters.
                    if (runsAreWords(String(grid), size, used)) return true
                    before.copyInto(grid); used -= word
                    return false
                }
                val first = words.filter { it.length in size - 3..size }.ifEmpty { words }.random(random)
                place(first, (size / 2) * size + (size - first.length) / 2, false)
                var stale = 0
                while (stale < 3) {
                    checkpoint()
                    var placed = false
                    for (word in words.shuffled(random)) {
                        if (word in used) continue
                        var bestSpot = -1; var bestDown = false; var bestCross = 0
                        for (down in listOf(false, true)) for (start in grid.indices) {
                            val x = fits(word, start, down)
                            if (x > bestCross || (x == bestCross && x > 0 && random.nextBoolean())) { bestCross = x; bestSpot = start; bestDown = down }
                        }
                        if (bestCross > 0 && place(word, bestSpot, bestDown)) placed = true
                    }
                    if (placed) stale = 0 else stale++
                }
                val fill = grid.count { it != '#' } + grid.filter { it != '#' }.toSet().size * 3
                if (fill > bestFill && runsAreWords(String(grid), size, used)) { best = String(grid); bestFill = fill }
            }
            check(best.isNotEmpty())
            return best
        }

        /** Every across and down run of two or more letters is a placed word. */
        fun runsAreWords(grid: String, size: Int, words: Set<String>): Boolean = runs(grid, size).all { it.second in words }

        /** (cells, text) for every run of at least two letters. */
        fun runs(grid: String, size: Int): List<Pair<List<Int>, String>> = buildList {
            for (down in listOf(false, true)) for (line in 0 until size) {
                val cells = (0 until size).map { if (down) it * size + line else line * size + it }
                var run = mutableListOf<Int>()
                for (cell in cells + -1) {
                    if (cell >= 0 && grid[cell] != '#') run += cell
                    else { if (run.size >= 2) add(run.toList() to run.map { grid[it] }.joinToString("")); run = mutableListOf() }
                }
            }
        }

        fun generate(seed: Long, level: LogicLevel): CodeCracker {
            val random = Random(seed)
            val size = sizeOf(level)
            val grid = layout(size, random)
            val letters = grid.filter { it != '#' }.toSet().toList().shuffled(random)
            val code = MutableList(26) { 0 }
            letters.forEachIndexed { i, ch -> code[ch - 'A'] = i + 1 }
            var game = CodeCracker(level, seed, size, grid, code)
            (1..game.count).shuffled(random).take(reveals(level)).forEach { game = game.reveal(it) }
            return game
        }
    }
}

object CodeCrackerCodec {
    fun encode(g: CodeCracker) = listOf("1", g.level.name, g.seed.toString(), g.size.toString(), g.grid, g.code.joinToString(","),
        g.guesses, g.given.sorted().joinToString(","), g.hints.toString()).joinToString("\n")
    fun decode(text: String): CodeCracker? = try {
        val l = text.split('\n'); require(l.size == 9 && l[0] == "1")
        CodeCracker(LogicLevel.valueOf(l[1]), l[2].toLong(), l[3].toInt(), l[4], l[5].split(',').map(String::toInt), l[6],
            l[7].split(',').filter(String::isNotEmpty).map(String::toInt).toSet(), l[8].toInt())
    } catch (_: IllegalArgumentException) { null }
}

// ---------------- Dropquote ----------------

/**
 * Dropquote: a saying laid out in a grid, with each column's letters jumbled above it.
 * [cells] holds the answer letters with '#' for word gaps; [typed] uses '_' for empty squares.
 */
data class Dropquote(
    val level: LogicLevel,
    val seed: Long,
    val quote: String,
    val author: String,
    val width: Int,
    val cells: String,
    val typed: String = cells.map { if (it == '#') '#' else '_' }.joinToString(""),
    val hints: Int = 0,
) {
    val rows: Int get() = cells.length / width

    init {
        require(width in 3..16 && cells.isNotEmpty() && cells.length % width == 0 && cells.all { it == '#' || it in 'A'..'Z' })
        require(quote.isNotBlank() && author.isNotBlank() && '\n' !in quote + author)
        require(typed.length == cells.length && typed.indices.all { (typed[it] == '#') == (cells[it] == '#') && (typed[it] == '#' || typed[it] == '_' || typed[it] in 'A'..'Z') })
        require(hints >= 0)
        // Each column can only hold its own letters.
        (0 until width).forEach { c -> require(left(c).values.all { it >= 0 }) }
    }

    fun column(c: Int) = (0 until rows).map { it * width + c }
    /** The column's letters in alphabetical order, as shown above the grid. */
    fun pool(c: Int): String = column(c).map { cells[it] }.filter { it != '#' }.sorted().joinToString("")
    /** How many of each letter are still unplaced in column [c]. */
    fun left(c: Int): Map<Char, Int> {
        val counts = pool(c).groupingBy { it }.eachCount().toMutableMap()
        column(c).map { typed[it] }.filter { it in 'A'..'Z' }.forEach { counts[it] = (counts[it] ?: 0) - 1 }
        return counts
    }
    val complete: Boolean get() = typed == cells
    fun mistakes(): Set<Int> = typed.indices.filter { typed[it] in 'A'..'Z' && typed[it] != cells[it] }.toSet()

    /** Drops [letter] into [cell] if its column still has one; null clears the square. */
    fun place(cell: Int, letter: Char?): Dropquote {
        if (complete || cell !in cells.indices || cells[cell] == '#') return this
        val c = cell % width
        val next = typed.toCharArray()
        if (letter == null) { if (typed[cell] == '_') return this; next[cell] = '_'; return copy(typed = String(next)) }
        if (typed[cell] == letter || (left(c)[letter] ?: 0) <= 0) return this
        next[cell] = letter
        return copy(typed = String(next))
    }

    fun hint(preferred: Int?): Dropquote? {
        if (complete) return null
        val cell = preferred?.takeIf { it in cells.indices && cells[it] != '#' && typed[it] != cells[it] }
            ?: typed.indices.first { cells[it] != '#' && typed[it] != cells[it] }
        val next = typed.toCharArray()
        val c = cell % width
        // Free the letter if another square in the column is holding it wrongly.
        if ((left(c)[cells[cell]] ?: 0) <= 0) column(c).firstOrNull { it != cell && typed[it] == cells[cell] && cells[it] != typed[it] }?.let { next[it] = '_' }
        next[cell] = cells[cell]
        return copy(typed = String(next), hints = hints + 1)
    }

    companion object {
        fun widthOf(level: LogicLevel) = listOf(9, 11, 12, 13)[level.ordinal]
        fun letterRange(level: LogicLevel) = listOf(12..32, 24..50, 36..70, 50..120)[level.ordinal]

        /** Word-wraps [text] into rows of at most [width]; words never split, gaps become '#'. */
        fun wrap(text: String, width: Int): List<String>? {
            val words = text.uppercase().split(' ').map { w -> w.filter { it in 'A'..'Z' } }.filter { it.isNotEmpty() }
            if (words.isEmpty() || words.any { it.length > width }) return null
            val rows = mutableListOf<String>(); var row = ""
            for (w in words) {
                row = when {
                    row.isEmpty() -> w
                    row.length + 1 + w.length <= width -> "$row#$w"
                    else -> { rows += row; w }
                }
            }
            rows += row
            return rows
        }

        fun generate(seed: Long, level: LogicLevel): Dropquote {
            val random = Random(seed)
            val width = widthOf(level)
            val range = letterRange(level)
            val fits = Quotes.all.filter { (q, _) -> q.count { it.isLetter() } in range && wrap(q, width) != null }
            val (quote, author) = fits.ifEmpty { Quotes.all.filter { wrap(it.first, width) != null } }.random(random)
            val rows = checkNotNull(wrap(quote, width))
            // Trim to the longest row so no column is empty.
            val used = rows.maxOf { it.length }
            return Dropquote(level, seed, quote, author, used, rows.joinToString("") { it.padEnd(used, '#') })
        }
    }
}

object DropquoteCodec {
    fun encode(g: Dropquote) = listOf("1", g.level.name, g.seed.toString(), g.quote, g.author, g.width.toString(), g.cells, g.typed, g.hints.toString()).joinToString("\n")
    fun decode(text: String): Dropquote? = try {
        val l = text.split('\n'); require(l.size == 9 && l[0] == "1")
        Dropquote(LogicLevel.valueOf(l[1]), l[2].toLong(), l[3], l[4], l[5].toInt(), l[6], l[7], l[8].toInt())
    } catch (_: IllegalArgumentException) { null }
}
