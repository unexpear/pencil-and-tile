package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.logic.LogicKind
import com.simplegamegen.sudoku.logic.LogicRules
import com.simplegamegen.sudoku.logic.LogicSolver
import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/** Hitori cell states chosen by the player. */
object Shade { const val NONE = 0; const val SHADED = 1; const val CIRCLED = 2 }

/**
 * Hitori: shade squares so that no number repeats among unshaded squares in any row
 * or column, shaded squares never touch side by side, and all unshaded squares stay
 * connected. [shaded] is the unique solution.
 */
data class HitoriGame(
    val setting: Int,
    val seed: Long,
    val numbers: List<Int>,
    val shaded: List<Boolean>,
    val marks: List<Int> = List(numbers.size) { Shade.NONE },
    val hints: Int = 0,
) {
    val size: Int get() = SIZES[setting]

    init {
        require(setting in SIZES.indices && numbers.size == size * size && shaded.size == numbers.size && marks.size == numbers.size)
        require(numbers.all { it in 1..size } && marks.all { it in 0..2 } && hints >= 0)
        require(HitoriSolver.valid(size, numbers, shaded))
    }

    val complete: Boolean get() = marks.indices.all { (marks[it] == Shade.SHADED) == shaded[it] }
    fun mistakes(): Set<Int> = marks.indices.filter { (marks[it] == Shade.SHADED && !shaded[it]) || (marks[it] == Shade.CIRCLED && shaded[it]) }.toSet()

    /** Visible rule breaks: touching shaded squares and repeated numbers left unshaded. */
    fun conflicts(): Set<Int> {
        val bad = mutableSetOf<Int>()
        val n = size
        marks.indices.filter { marks[it] == Shade.SHADED }.forEach { i ->
            HitoriSolver.neighbors(i, n).filter { marks[it] == Shade.SHADED }.forEach { bad += it; bad += i }
        }
        for (line in 0 until n) listOf((0 until n).map { line * n + it }, (0 until n).map { it * n + line }).forEach { cells ->
            cells.filter { marks[it] == Shade.CIRCLED }.groupBy { numbers[it] }.values.filter { it.size > 1 }.forEach { bad += it }
        }
        return bad
    }

    /** Cycles a square: unmarked → shaded → circled (kept) → unmarked. */
    fun cycle(i: Int): HitoriGame = if (complete || i !in marks.indices) this
        else copy(marks = marks.toMutableList().also { it[i] = (it[i] + 1) % 3 })

    fun set(i: Int, mark: Int): HitoriGame = if (complete || i !in marks.indices || mark !in 0..2) this
        else copy(marks = marks.toMutableList().also { it[i] = mark })

    fun hint(preferred: Int? = null): HitoriGame? {
        if (complete) return null
        fun wrong(i: Int) = (marks[i] == Shade.SHADED) != shaded[i] || (marks[i] == Shade.CIRCLED && shaded[i])
        val i = preferred?.takeIf { it in marks.indices && wrong(it) } ?: marks.indices.filter(::wrong).firstOrNull { shaded[it] } ?: marks.indices.first(::wrong)
        return copy(marks = marks.toMutableList().also { it[i] = if (shaded[i]) Shade.SHADED else Shade.CIRCLED }, hints = hints + 1)
    }

    companion object {
        val SIZES = listOf(5, 6, 7, 8)

        fun generate(seed: Long, setting: Int): HitoriGame {
            val random = Random(seed)
            val n = SIZES[setting]
            val latin = LogicRules(LogicKind.KENKEN, n, n, n, List(n * n) { true })
            repeat(60) {
                checkpoint()
                val square = LogicSolver.solve(latin, IntArray(n * n), limit = 1, random = random).solutions.first()
                // A maximal pattern: no further square can be shaded, so there's no room for extra shading.
                val black = BooleanArray(n * n)
                for (i in (0 until n * n).shuffled(random)) {
                    if (HitoriSolver.neighbors(i, n).any { black[it] }) continue
                    black[i] = true
                    if (!HitoriSolver.whitesConnected(n, black)) black[i] = false
                }
                repeat(25) {
                    // Each shaded square copies a number from an unshaded square in its row or column.
                    val numbers = square.copyOf()
                    for (i in (0 until n * n).filter { black[it] }) {
                        val r = i / n; val c = i % n
                        val sources = ((0 until n).map { r * n + it } + (0 until n).map { it * n + c }).filter { it != i && !black[it] }
                        numbers[i] = square[sources.random(random)]
                    }
                    val result = HitoriSolver.count(n, numbers.toList(), 2)
                    if (result == 1) return HitoriGame(setting, seed, numbers.toList(), black.toList())
                }
            }
            error("Couldn't build a unique Hitori")
        }
    }
}

object HitoriSolver {
    fun neighbors(i: Int, n: Int) = listOfNotNull((i - n).takeIf { it >= 0 }, (i + n).takeIf { it < n * n },
        (i - 1).takeIf { i % n > 0 }, (i + 1).takeIf { i % n < n - 1 })

    fun whitesConnected(n: Int, black: BooleanArray): Boolean {
        val start = (0 until n * n).firstOrNull { !black[it] } ?: return false
        val seen = BooleanArray(n * n); seen[start] = true
        val stack = ArrayDeque(listOf(start)); var count = 1
        while (stack.isNotEmpty()) for (m in neighbors(stack.removeLast(), n)) if (!black[m] && !seen[m]) { seen[m] = true; count++; stack.addLast(m) }
        return count == black.count { !it }
    }

    fun valid(n: Int, numbers: List<Int>, shaded: List<Boolean>): Boolean {
        val black = shaded.toBooleanArray()
        if ((0 until n * n).any { i -> black[i] && neighbors(i, n).any { black[it] } }) return false
        for (line in 0 until n) for (cells in listOf((0 until n).map { line * n + it }, (0 until n).map { it * n + line })) {
            val whites = cells.filter { !black[it] }.map { numbers[it] }
            if (whites.size != whites.toSet().size) return false
        }
        return whitesConnected(n, black)
    }

    /** Counts solutions up to [limit]. States: 0 unknown, 1 white, 2 black. */
    fun count(n: Int, numbers: List<Int>, limit: Int): Int {
        val lines = (0 until n).flatMap { line -> listOf((0 until n).map { line * n + it }, (0 until n).map { it * n + line }) }
        val cellLines = (0 until n * n).map { i -> lines.filter { i in it } }
        var found = 0

        fun propagate(state: IntArray): Boolean {
            var changed = true
            while (changed) {
                changed = false
                for (i in state.indices) {
                    if (state[i] == 2) for (m in neighbors(i, n)) {
                        if (state[m] == 2) return false
                        if (state[m] == 0) { state[m] = 1; changed = true }
                    }
                    if (state[i] == 1) for (line in cellLines[i]) for (m in line) if (m != i && numbers[m] == numbers[i]) {
                        if (state[m] == 1) return false
                        if (state[m] == 0) { state[m] = 2; changed = true }
                    }
                }
                // A square whose number is unique in its row and column never needs shading in a valid answer
                // only if shading it can't help; we leave it open and let connectivity decide.
                // Whites and unknowns together must stay connected.
                val black = BooleanArray(n * n) { state[it] == 2 }
                if (!whitesConnected(n, black)) return false
                // An unknown square whose shading would split the whites must be white.
                for (i in state.indices) if (state[i] == 0) {
                    black[i] = true
                    val splits = !whitesConnected(n, black)
                    black[i] = false
                    if (splits) { state[i] = 1; changed = true }
                }
            }
            return true
        }

        fun search(state: IntArray) {
            checkpoint()
            if (found >= limit || !propagate(state)) return
            val i = state.indices.filter { state[it] == 0 }.maxByOrNull { c -> cellLines[c].sumOf { l -> l.count { numbers[it] == numbers[c] } } }
            if (i == null) { if (valid(n, numbers, state.map { it == 2 })) found++; return }
            for (v in intArrayOf(2, 1)) { val next = state.copyOf(); next[i] = v; search(next); if (found >= limit) return }
        }
        search(IntArray(n * n))
        return found
    }
}

object HitoriCodec {
    fun encode(g: HitoriGame) = listOf("1", g.setting.toString(), g.seed.toString(), g.numbers.joinToString(","),
        g.shaded.joinToString("") { if (it) "1" else "0" }, g.marks.joinToString(""), g.hints.toString()).joinToString("\n")
    fun decode(text: String): HitoriGame? = try {
        val l = text.split('\n'); require(l.size == 7 && l[0] == "1" && l[4].all { it in "01" } && l[5].all { it in "012" })
        HitoriGame(l[1].toInt(), l[2].toLong(), l[3].split(',').map(String::toInt), l[4].map { it == '1' }, l[5].map { it.digitToInt() }, l[6].toInt())
    } catch (_: IllegalArgumentException) { null }
}
