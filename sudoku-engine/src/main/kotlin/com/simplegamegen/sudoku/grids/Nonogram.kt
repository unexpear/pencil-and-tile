package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/** Player marks on a picture-logic square. */
object Mark { const val EMPTY = 0; const val FILLED = 1; const val CROSSED = 2 }

/**
 * Nonogram (picture logic). Clues give the lengths of filled runs in each row and
 * column. Every generated picture can be solved line by line, so its answer is unique.
 */
data class Nonogram(
    val setting: Int,
    val seed: Long,
    val width: Int,
    val height: Int,
    val solution: List<Boolean>,
    val marks: List<Int> = List(width * height) { Mark.EMPTY },
    val hints: Int = 0,
) {
    init {
        require(setting in SIZES.indices && width in 2..20 && height in 2..20 && solution.size == width * height)
        require(marks.size == solution.size && marks.all { it in 0..2 } && hints >= 0)
    }

    fun row(r: Int) = (0 until width).map { r * width + it }
    fun col(c: Int) = (0 until height).map { it * width + c }
    val rowClues: List<List<Int>> get() = (0 until height).map { clue(row(it).map { c -> solution[c] }) }
    val colClues: List<List<Int>> get() = (0 until width).map { clue(col(it).map { c -> solution[c] }) }

    val complete: Boolean get() = solution.indices.all { (marks[it] == Mark.FILLED) == solution[it] }
    fun lineDone(cells: List<Int>) = cells.all { (marks[it] == Mark.FILLED) == solution[it] }
    fun mistakes(): Set<Int> = solution.indices.filter { (marks[it] == Mark.FILLED && !solution[it]) || (marks[it] == Mark.CROSSED && solution[it]) }.toSet()

    /** Sets every cell in [cells] to [mark]; returns this game when nothing changes or it's solved. */
    fun paint(cells: Collection<Int>, mark: Int): Nonogram {
        if (complete || mark !in 0..2) return this
        val next = marks.toMutableList()
        cells.filter { it in next.indices }.forEach { next[it] = mark }
        return if (next == marks) this else copy(marks = next)
    }

    /** Fixes one wrong or unknown square, preferring [preferred]. */
    fun hint(preferred: Int? = null): Nonogram? {
        if (complete) return null
        fun wrong(i: Int) = (marks[i] == Mark.FILLED) != solution[i] || (marks[i] == Mark.CROSSED && solution[i])
        val i = preferred?.takeIf { it in marks.indices && wrong(it) } ?: marks.indices.filter(::wrong).firstOrNull { solution[it] }
            ?: marks.indices.first(::wrong)
        return copy(marks = marks.toMutableList().also { it[i] = if (solution[i]) Mark.FILLED else Mark.CROSSED }, hints = hints + 1)
    }

    companion object {
        val SIZES = listOf(5, 8, 10, 12)
        fun clue(line: List<Boolean>): List<Int> {
            val runs = mutableListOf<Int>(); var n = 0
            line.forEach { if (it) n++ else if (n > 0) { runs += n; n = 0 } }
            if (n > 0) runs += n
            return runs
        }

        /**
         * Narrows one line: [known] holds -1 unknown, 0 empty, 1 filled. Returns the refined
         * line, or null when the clue can't fit.
         */
        fun solveLine(clue: List<Int>, known: IntArray): IntArray? {
            val n = known.size; val k = clue.size
            fun emptyOk(i: Int) = known[i] != 1
            fun fillOk(i: Int) = known[i] != 0
            // fwd[j][i]: the first j blocks fit in cells [0, i), leaving cell i free to start the rest.
            val fwd = Array(k + 1) { BooleanArray(n + 1) }
            fwd[0][0] = true
            for (i in 1..n) fwd[0][i] = fwd[0][i - 1] && emptyOk(i - 1)
            for (j in 1..k) {
                val len = clue[j - 1]
                for (i in 1..n) {
                    var ok = fwd[j][i - 1] && emptyOk(i - 1)
                    val s = i - len
                    if (!ok && s >= 0 && (s until i).all(::fillOk)) {
                        ok = if (s == 0) j == 1 else emptyOk(s - 1) && fwd[j - 1][s - 1]
                    }
                    fwd[j][i] = ok
                }
            }
            // bwd[j][i]: blocks j..k-1 fit in cells [i, n).
            val bwd = Array(k + 1) { BooleanArray(n + 1) }
            bwd[k][n] = true
            for (i in n - 1 downTo 0) bwd[k][i] = bwd[k][i + 1] && emptyOk(i)
            for (j in k - 1 downTo 0) {
                val len = clue[j]
                for (i in n - 1 downTo 0) {
                    var ok = bwd[j][i + 1] && emptyOk(i)
                    val e = i + len
                    if (!ok && e <= n && (i until e).all(::fillOk)) {
                        ok = if (e == n) j == k - 1 else emptyOk(e) && bwd[j + 1][e + 1]
                    }
                    bwd[j][i] = ok
                }
            }
            if (!fwd[k][n]) return null
            val canEmpty = BooleanArray(n); val canFill = IntArray(n + 1)
            for (i in 0 until n) if (emptyOk(i)) for (j in 0..k) if (fwd[j][i] && bwd[j][i + 1]) { canEmpty[i] = true; break }
            for (j in 0 until k) {
                val len = clue[j]
                for (s in 0..n - len) {
                    val e = s + len
                    if (!(s until e).all(::fillOk)) continue
                    val before = if (s == 0) j == 0 else emptyOk(s - 1) && fwd[j][s - 1]
                    val after = if (e == n) j == k - 1 else emptyOk(e) && bwd[j + 1][e + 1]
                    if (before && after) { canFill[s]++; canFill[e]-- }
                }
            }
            val out = IntArray(n); var run = 0
            for (i in 0 until n) {
                run += canFill[i]
                val f = run > 0; val e = canEmpty[i]
                out[i] = when { f && e -> -1; f -> 1; e -> 0; else -> return null }
            }
            return out
        }

        /** True when repeated line-by-line reasoning fills the whole picture. */
        fun lineSolvable(width: Int, height: Int, rows: List<List<Int>>, cols: List<List<Int>>): Boolean {
            val grid = IntArray(width * height) { -1 }
            var changed = true
            while (changed) {
                checkpoint()
                changed = false
                for (r in 0 until height) {
                    val cells = (0 until width).map { r * width + it }
                    val line = solveLine(rows[r], IntArray(width) { grid[cells[it]] }) ?: return false
                    cells.forEachIndexed { i, c -> if (grid[c] != line[i]) { grid[c] = line[i]; changed = true } }
                }
                for (c in 0 until width) {
                    val cells = (0 until height).map { it * width + c }
                    val line = solveLine(cols[c], IntArray(height) { grid[cells[it]] }) ?: return false
                    cells.forEachIndexed { i, cell -> if (grid[cell] != line[i]) { grid[cell] = line[i]; changed = true } }
                }
            }
            return grid.none { it == -1 }
        }

        fun generate(seed: Long, setting: Int): Nonogram {
            val random = Random(seed)
            val n = SIZES[setting]
            repeat(400) {
                checkpoint()
                // Random noise smoothed into blobs looks more like a picture than static.
                val noise = BooleanArray(n * n) { random.nextDouble() < 0.58 }
                val cells = BooleanArray(n * n) { i ->
                    val r = i / n; val c = i % n
                    var filled = 0; var total = 0
                    for (dr in -1..1) for (dc in -1..1) {
                        val rr = r + dr; val cc = c + dc
                        if (rr in 0 until n && cc in 0 until n) { total++; if (noise[rr * n + cc]) filled++ }
                    }
                    if (random.nextInt(8) == 0) !noise[i] else filled * 2 > total
                }
                val solution = cells.toList()
                if (solution.count { it } < n * n / 4) return@repeat
                val g = Nonogram(setting, seed, n, n, solution)
                if (lineSolvable(n, n, g.rowClues, g.colClues)) return g
            }
            error("No line-solvable picture found")
        }
    }
}

object NonogramCodec {
    fun encode(g: Nonogram) = listOf("1", g.setting.toString(), g.seed.toString(), g.width.toString(), g.height.toString(),
        g.solution.joinToString("") { if (it) "1" else "0" }, g.marks.joinToString(""), g.hints.toString()).joinToString("\n")
    fun decode(text: String): Nonogram? = try {
        val l = text.split('\n'); require(l.size == 8 && l[0] == "1")
        require(l[5].all { it in "01" } && l[6].all { it in "012" })
        Nonogram(l[1].toInt(), l[2].toLong(), l[3].toInt(), l[4].toInt(), l[5].map { it == '1' }, l[6].map { it.digitToInt() }, l[7].toInt())
    } catch (_: IllegalArgumentException) { null }
}
