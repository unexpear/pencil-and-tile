package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Towers: fill the grid with heights 1 through the grid size, once in every row and column.
 * A clue beside a row or column is how many towers you can see from that side.
 * A tower is hidden when a taller one stands closer to you.
 */
data class TowersGame(
    val setting: Int,
    val seed: Long,
    val size: Int,
    val solution: List<Int>,
    val top: List<Int>,
    val bottom: List<Int>,
    val left: List<Int>,
    val right: List<Int>,
    val entered: List<Int> = List(solution.size) { 0 },
    val hints: Int = 0,
) {
    init {
        require(setting in SIZES.indices && size == SIZES[setting])
        require(solution.size == size * size && solution.all { it in 1..size } && entered.size == solution.size && entered.all { it in 0..size })
        require(top.size == size && bottom.size == size && left.size == size && right.size == size)
        require(listOf(top, bottom, left, right).all { side -> side.all { it in 0..size } } && hints >= 0)
    }

    val complete: Boolean get() = entered == solution

    fun cycle(cell: Int): TowersGame = if (complete || cell !in entered.indices) this
        else copy(entered = entered.toMutableList().also { it[cell] = (it[cell] + 1) % (size + 1) })

    fun hint(): TowersGame? {
        if (complete) return null
        val i = entered.indices.firstOrNull { entered[it] != solution[it] } ?: return null
        return copy(entered = entered.toMutableList().also { it[i] = solution[i] }, hints = hints + 1)
    }

    companion object {
        val SIZES = listOf(4, 4, 5, 5)

        fun generate(seed: Long, setting: Int): TowersGame {
            val random = Random(seed)
            val n = SIZES[setting]
            val keep = listOf(n * 4, n * 3, n * 2, n + 2)[setting]
            repeat(30) {
                checkpoint()
                val grid = latin(n, random)
                val top = IntArray(n) { col -> TowersRules.seen((0 until n).map { grid[it * n + col] }) }
                val bottom = IntArray(n) { col -> TowersRules.seen((n - 1 downTo 0).map { grid[it * n + col] }) }
                val left = IntArray(n) { row -> TowersRules.seen((0 until n).map { grid[row * n + it] }) }
                val right = IntArray(n) { row -> TowersRules.seen((n - 1 downTo 0).map { grid[row * n + it] }) }
                val slots = buildList {
                    for (i in 0 until n) { add(0 to i); add(1 to i); add(2 to i); add(3 to i) }
                }.shuffled(random).toMutableList()
                val show = Array(4) { IntArray(n) { i -> listOf(top, bottom, left, right)[it][i] } }
                fun snapshot() = show.map { it.toList() }
                while (slots.size > keep) {
                    val (side, index) = slots.removeAt(slots.lastIndex)
                    val saved = show[side][index]
                    show[side][index] = 0
                    if (TowersRules.count(n, show[0].toList(), show[1].toList(), show[2].toList(), show[3].toList(), 2) != 1) {
                        show[side][index] = saved
                    }
                }
                if (TowersRules.count(n, show[0].toList(), show[1].toList(), show[2].toList(), show[3].toList(), 2) == 1) {
                    return TowersGame(setting, seed, n, grid.toList(), show[0].toList(), show[1].toList(), show[2].toList(), show[3].toList())
                }
            }
            error("Couldn't build a unique Towers")
        }

        private fun latin(n: Int, random: Random): IntArray {
            val grid = IntArray(n * n)
            val rowUsed = Array(n) { BooleanArray(n + 1) }
            val colUsed = Array(n) { BooleanArray(n + 1) }
            fun fill(i: Int): Boolean {
                if (i == n * n) return true
                val r = i / n; val c = i % n
                for (value in (1..n).shuffled(random)) {
                    if (rowUsed[r][value] || colUsed[c][value]) continue
                    grid[i] = value
                    rowUsed[r][value] = true
                    colUsed[c][value] = true
                    if (fill(i + 1)) return true
                    rowUsed[r][value] = false
                    colUsed[c][value] = false
                }
                return false
            }
            check(fill(0))
            return grid
        }
    }
}

object TowersRules {
    fun seen(line: List<Int>): Int {
        var tallest = 0
        var count = 0
        for (height in line) if (height > tallest) { tallest = height; count++ }
        return count
    }

    fun count(n: Int, top: List<Int>, bottom: List<Int>, left: List<Int>, right: List<Int>, limit: Int): Int {
        val grid = IntArray(n * n)
        val rowUsed = Array(n) { BooleanArray(n + 1) }
        val colUsed = Array(n) { BooleanArray(n + 1) }
        var solutions = 0
        var nodes = 0
        fun visible(values: List<Int>, clue: Int) = clue == 0 || seen(values) == clue
        fun rec(i: Int) {
            if (solutions >= limit) return
            if (++nodes > 100_000) { solutions = limit; return }
            if (i == n * n) {
                val ok = (0 until n).all { col ->
                    val line = (0 until n).map { grid[it * n + col] }
                    visible(line, top[col]) && visible(line.asReversed(), bottom[col])
                }
                if (ok) solutions++
                return
            }
            val r = i / n
            val c = i % n
            for (value in 1..n) {
                if (rowUsed[r][value] || colUsed[c][value]) continue
                grid[i] = value
                rowUsed[r][value] = true
                colUsed[c][value] = true
                val rowOk = c < n - 1 || visible((0 until n).map { grid[r * n + it] }, left[r]) &&
                    visible((n - 1 downTo 0).map { grid[r * n + it] }, right[r])
                if (rowOk) rec(i + 1)
                rowUsed[r][value] = false
                colUsed[c][value] = false
            }
        }
        rec(0)
        return solutions
    }
}

object TowersCodec {
    fun encode(g: TowersGame) = listOf(
        "1", g.setting.toString(), g.seed.toString(), g.size.toString(), g.solution.joinToString(","),
        g.top.joinToString(","), g.bottom.joinToString(","), g.left.joinToString(","), g.right.joinToString(","),
        g.entered.joinToString(","), g.hints.toString(),
    ).joinToString("\n")

    fun decode(text: String): TowersGame? = try {
        val l = text.split('\n')
        require(l.size == 11 && l[0] == "1")
        fun nums(s: String) = s.split(',').map(String::toInt)
        TowersGame(l[1].toInt(), l[2].toLong(), l[3].toInt(), nums(l[4]), nums(l[5]), nums(l[6]), nums(l[7]), nums(l[8]), nums(l[9]), l[10].toInt())
    } catch (_: IllegalArgumentException) { null }
}
