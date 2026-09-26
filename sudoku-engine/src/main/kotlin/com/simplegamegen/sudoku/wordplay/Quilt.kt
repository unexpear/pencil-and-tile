package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import kotlin.random.Random

// ---------------- Word Quilt ----------------
//
// The grid is sewn from patches, and each patch holds a set of letters. Place every patch's letters in its own
// squares so that each run of two or more letters, across and down, is a word. Any arrangement that does that
// wins; puzzles are chosen to have very few, so there is usually one to find.

const val QUILT_HOLE = '#'
const val QUILT_EMPTY = '.'

data class Quilt(
    val level: LogicLevel,
    val seed: Long,
    val width: Int,
    /** The shape: [QUILT_HOLE] for holes, anything else is a square. */
    val shape: String,
    /** The patch each square belongs to (-1 for holes). */
    val patches: List<Int>,
    /** The answer the puzzle was built from. */
    val answer: String,
    /** Letters placed so far ([QUILT_EMPTY] for none); given squares start filled. */
    val cells: String,
    val given: Set<Int> = emptySet(),
    val hints: Int = 0,
) {
    init {
        require(shape.length % width == 0 && patches.size == shape.length && answer.length == shape.length && cells.length == shape.length)
    }

    val height: Int get() = shape.length / width
    val squares: List<Int> get() = shape.indices.filter { shape[it] != QUILT_HOLE }

    /** The letters each patch holds, sorted. */
    val letters: Map<Int, String> by lazy { squares.groupBy { patches[it] }.mapValues { (_, cs) -> cs.map { answer[it] }.sorted().joinToString("") } }

    /** Letters of [patch] not yet placed. */
    fun remaining(patch: Int): String {
        val left = letters.getValue(patch).toMutableList()
        squares.filter { patches[it] == patch && cells[it] != QUILT_EMPTY }.forEach { left.remove(cells[it]) }
        return left.joinToString("")
    }

    fun place(at: Int, letter: Char): Quilt? {
        if (at !in squares || at in given || solved) return null
        val back = if (cells[at] != QUILT_EMPTY) cells[at].toString() else ""
        if (letter != QUILT_EMPTY && letter !in remaining(patches[at]) + back) return null
        return copy(cells = cells.substring(0, at) + letter + cells.substring(at + 1))
    }

    fun clear(at: Int): Quilt? = if (cells[at] == QUILT_EMPTY || at in given) null else place(at, QUILT_EMPTY)

    /** Every run of two or more squares across and down, as lists of squares. */
    val runs: List<List<Int>> by lazy { QuiltRuns.of(shape, width) }

    fun runWord(run: List<Int>): String? = if (run.all { cells[it] != QUILT_EMPTY }) run.map { cells[it] }.joinToString("") else null

    /** Runs that are full but not words, for marking. */
    fun badRuns(): List<List<Int>> = runs.filter { r -> runWord(r)?.let { !Lexicon.isWord(it) } ?: false }

    val full: Boolean get() = squares.all { cells[it] != QUILT_EMPTY }
    val solved: Boolean get() = full && runs.all { r -> runWord(r)?.let(Lexicon::isWord) == true }
    val over: Boolean get() = solved

    /** Puts one square of the answer in place, clearing anything in the way. */
    fun hint(): Quilt? {
        if (solved) return null
        val at = squares.firstOrNull { cells[it] != answer[it] && it !in given } ?: return null
        var g = this
        // Free the answer's letter in this patch if it's sitting elsewhere.
        val holder = squares.firstOrNull { patches[it] == patches[at] && it != at && g.cells[it] == answer[at] && g.cells[it] != answer[it] && it !in given }
        if (answer[at] !in g.remaining(patches[at]) && holder != null) g = g.copy(cells = g.cells.substring(0, holder) + QUILT_EMPTY + g.cells.substring(holder + 1))
        g = g.copy(cells = g.cells.substring(0, at) + answer[at] + g.cells.substring(at + 1))
        return g.copy(given = given + at, hints = hints + 1)
    }

    companion object {
        /** Shapes by level: [QUILT_HOLE] marks holes. */
        fun shapeOf(level: LogicLevel): List<String> = when (level) {
            LogicLevel.EASY -> listOf("....", "....", "....")
            LogicLevel.MEDIUM -> listOf("....", "....", "....", "....")
            LogicLevel.HARD -> listOf(".....", ".#.#.", ".....", ".#.#.", ".....")
            LogicLevel.EXPERT -> listOf("......", ".#..#.", "......", ".#..#.", "......")
        }

        fun patchSizeOf(level: LogicLevel) = listOf(2 to 2, 2 to 3, 3 to 3, 3 to 4)[level.ordinal]
        fun givensOf(level: LogicLevel) = listOf(2, 1, 1, 0)[level.ordinal]

        fun generate(seed: Long, level: LogicLevel): Quilt {
            val random = Random(seed)
            val rows = shapeOf(level)
            val width = rows[0].length
            val shape = rows.joinToString("")
            repeat(40) {
                val answer = QuiltFill.fill(shape, width, random) ?: return@repeat
                val patches = QuiltPatches.split(shape, width, patchSizeOf(level), random) ?: return@repeat
                val squares = shape.indices.filter { shape[it] != QUILT_HOLE }
                val given = squares.shuffled(random).take(givensOf(level)).toSet()
                val cells = String(CharArray(shape.length) { i -> if (shape[i] == QUILT_HOLE) QUILT_HOLE else if (i in given) answer[i] else QUILT_EMPTY })
                val q = Quilt(level, seed, width, shape, patches, answer, cells, given)
                // Keep puzzles with few ways to finish, so there's one to find.
                if (QuiltSolver.count(q, 3, 60_000) in 1..2) return q
            }
            // Fall back to a smaller, surely-fillable shape.
            return generate(seed + 1, if (level == LogicLevel.EASY) LogicLevel.EASY else LogicLevel.entries[level.ordinal - 1])
        }
    }
}

object QuiltRuns {
    fun of(shape: String, width: Int): List<List<Int>> {
        val h = shape.length / width
        val out = ArrayList<List<Int>>()
        fun scan(lines: List<List<Int>>) {
            for (line in lines) {
                var cur = ArrayList<Int>()
                for (i in line) { if (shape[i] == QUILT_HOLE) { if (cur.size >= 2) out += cur; cur = ArrayList() } else cur.add(i) }
                if (cur.size >= 2) out += cur
            }
        }
        scan((0 until h).map { r -> (0 until width).map { r * width + it } })
        scan((0 until width).map { c -> (0 until h).map { it * width + c } })
        return out
    }
}

/** Fills a shape with everyday words, letter by letter, keeping every run a word prefix. */
object QuiltFill {
    private val prefixes: Map<Int, Set<String>> by lazy {
        val out = HashMap<Int, HashSet<String>>()
        for (w in Lexicon.common) {
            if (w.length !in 2..6) continue
            val set = out.getOrPut(w.length) { HashSet() }
            for (k in 1..w.length) set += w.substring(0, k)
        }
        out
    }

    private const val ORDER = "EARIOTNSLCUDPMHGBFYWKVXZJQ"

    fun fill(shape: String, width: Int, random: Random, budget: Int = 200_000): String? {
        val runs = QuiltRuns.of(shape, width)
        val runsOf = shape.indices.associateWith { i -> runs.filter { i in it } }
        val squares = shape.indices.filter { shape[it] != QUILT_HOLE }
        val grid = CharArray(shape.length) { if (shape[it] == QUILT_HOLE) QUILT_HOLE else QUILT_EMPTY }
        var nodes = 0
        fun fits(i: Int): Boolean = runsOf.getValue(i).all { run ->
            val upTo = run.indexOf(i)
            val prefix = String(CharArray(upTo + 1) { grid[run[it]] })
            prefixes[run.size]?.contains(prefix) == true
        }
        fun go(k: Int): Boolean {
            if (k == squares.size) return true
            if (++nodes > budget) return false
            val i = squares[k]
            // Common letters first, with a little shuffle for variety.
            val letters = ORDER.toList().chunked(6).flatMap { it.shuffled(random) }
            for (ch in letters) {
                grid[i] = ch
                if (fits(i) && go(k + 1)) return true
            }
            grid[i] = QUILT_EMPTY
            return false
        }
        // Words must also differ from each other, so the grid isn't one word repeated.
        repeat(6) {
            nodes = 0
            if (go(0)) {
                val words = runs.map { r -> r.map { grid[it] }.joinToString("") }
                if (words.toSet().size == words.size) return String(grid)
            }
        }
        return null
    }
}

/** Splits the squares into connected patches of the given sizes. */
object QuiltPatches {
    fun split(shape: String, width: Int, sizes: Pair<Int, Int>, random: Random): List<Int>? {
        val h = shape.length / width
        val patch = IntArray(shape.length) { -1 }
        val free = shape.indices.filter { shape[it] != QUILT_HOLE }.toMutableSet()
        fun neighbours(i: Int) = listOfNotNull(
            (i - width).takeIf { it >= 0 }, (i + width).takeIf { it < shape.length },
            (i - 1).takeIf { i % width > 0 }, (i + 1).takeIf { i % width < width - 1 },
        ).filter { shape[it] != QUILT_HOLE }
        var id = 0
        while (free.isNotEmpty()) {
            val start = free.minOrNull()!!
            val size = random.nextInt(sizes.first, sizes.second + 1)
            val group = mutableListOf(start)
            free.remove(start)
            while (group.size < size) {
                val next = group.flatMap(::neighbours).filter { it in free }.distinct().randomOrNull(random) ?: break
                group += next; free.remove(next)
            }
            // A lonely single square joins a neighbouring patch.
            if (group.size == 1) {
                val host = neighbours(start).map { patch[it] }.firstOrNull { it >= 0 }
                if (host != null) { patch[start] = host; continue }
            }
            group.forEach { patch[it] = id }
            id++
        }
        if (h < 1) return null
        return patch.toList()
    }
}

/** Counts ways to place the patch letters so every run is an accepted word, up to [cap]. */
object QuiltSolver {
    fun count(q: Quilt, cap: Int, budget: Int): Int {
        val squares = q.squares
        val runsOf = squares.associateWith { i -> q.runs.filter { i in it } }
        val grid = q.cells.toCharArray()
        val left = q.letters.mapValues { (p, _) -> q.remaining(p).toMutableList() }
        var found = 0; var nodes = 0
        val todo = squares.filter { grid[it] == QUILT_EMPTY }
        fun fits(i: Int): Boolean = runsOf.getValue(i).all { run ->
            val filled = run.takeWhile { grid[it] != QUILT_EMPTY }
            val word = String(CharArray(filled.size) { grid[filled[it]] })
            if (filled.size == run.size) Lexicon.isWord(word) else Lexicon.hasPrefix(word) || filled.isEmpty()
        }
        fun go(k: Int) {
            if (found >= cap || ++nodes > budget) return
            if (k == todo.size) { found++; return }
            val i = todo[k]
            val pool = left.getValue(q.patches[i])
            for (ch in pool.distinct()) {
                grid[i] = ch; pool.remove(ch)
                if (fits(i)) go(k + 1)
                pool.add(ch); grid[i] = QUILT_EMPTY
            }
        }
        go(0)
        return found
    }
}

object QuiltCodec {
    fun encode(g: Quilt) = listOf("1", g.level.name, g.seed.toString(), g.width.toString(), g.shape, g.patches.joinToString(","), g.answer,
        g.cells, g.given.sorted().joinToString(","), g.hints.toString()).joinToString("\n")

    fun decode(text: String): Quilt? = try {
        val l = text.split('\n'); require(l.size == 10 && l[0] == "1")
        Quilt(LogicLevel.valueOf(l[1]), l[2].toLong(), l[3].toInt(), l[4], l[5].split(',').map { it.toInt() }, l[6], l[7],
            if (l[8].isEmpty()) emptySet() else l[8].split(',').map { it.toInt() }.toSet(), l[9].toInt())
    } catch (_: IllegalArgumentException) { null }
}
