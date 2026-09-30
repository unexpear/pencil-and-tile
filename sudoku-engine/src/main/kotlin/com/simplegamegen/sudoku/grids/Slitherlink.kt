package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Slitherlink: a number says how many sides of its square belong to the loop.
 * The loop is one closed line and does not branch. Blank squares have no number.
 */
data class SlitherlinkGame(
    val setting: Int,
    val seed: Long,
    val size: Int,
    /** -1 is blank, otherwise 0–3. Row by row. */
    val clue: List<Int>,
    val solutionH: String,
    val solutionV: String,
    val markH: String = "0".repeat(solutionH.length),
    val markV: String = "0".repeat(solutionV.length),
    val hints: Int = 0,
) {
    init {
        require(setting in SIZES.indices && size == SIZES[setting])
        require(clue.size == size * size && clue.all { it in -1..3 })
        require(solutionH.length == (size + 1) * size && solutionV.length == size * (size + 1))
        require(markH.length == solutionH.length && markV.length == solutionV.length)
        require(solutionH.all { it in "01" } && solutionV.all { it in "01" } && markH.all { it in "01" } && markV.all { it in "01" })
        require(SlitherlinkRules.loop(size, solutionH, solutionV))
        require(hints >= 0)
    }

    val complete: Boolean get() = markH == solutionH && markV == solutionV

    fun toggleH(row: Int, col: Int): SlitherlinkGame? {
        if (complete || row !in 0..size || col !in 0 until size) return null
        val i = row * size + col
        return copy(markH = markH.replaceRange(i, i + 1, if (markH[i] == '1') "0" else "1"))
    }

    fun toggleV(row: Int, col: Int): SlitherlinkGame? {
        if (complete || row !in 0 until size || col !in 0..size) return null
        val i = row * (size + 1) + col
        return copy(markV = markV.replaceRange(i, i + 1, if (markV[i] == '1') "0" else "1"))
    }

    fun hint(): SlitherlinkGame? {
        if (complete) return null
        val h = solutionH.indices.firstOrNull { solutionH[it] != markH[it] } ?: -1
        if (h >= 0) return copy(markH = markH.replaceRange(h, h + 1, solutionH[h].toString()), hints = hints + 1)
        val v = solutionV.indices.firstOrNull { solutionV[it] != markV[it] } ?: -1
        if (v >= 0) return copy(markV = markV.replaceRange(v, v + 1, solutionV[v].toString()), hints = hints + 1)
        return null
    }

    companion object {
        val SIZES = listOf(5, 5, 6, 7)

        fun generate(seed: Long, setting: Int): SlitherlinkGame {
            val random = Random(seed)
            val n = SIZES[setting]
            val keep = listOf(1.0, 1.0, 0.9, 0.75)[setting]
            repeat(40) {
                checkpoint()
                val loop = SlitherlinkRules.grow(n, random)
                if (!SlitherlinkRules.loop(n, loop.first, loop.second)) return@repeat
                val full = (0 until n * n).map { cell -> SlitherlinkRules.sides(n, loop.first, loop.second, cell / n, cell % n) }
                if (full.any { it > 3 }) return@repeat
                val show = full.mapIndexed { i, value -> if (random.nextDouble() < keep) value else -1 }
                if (show.none { it >= 0 }) return@repeat
                if (SlitherlinkRules.count(n, show, 2) == 1) {
                    return SlitherlinkGame(setting, seed, n, show, loop.first, loop.second)
                }
            }
            error("Couldn't build a unique Slitherlink")
        }
    }
}

object SlitherlinkRules {
    fun grow(n: Int, random: Random): Pair<String, String> {
        val h = Array(n + 1) { BooleanArray(n) }
        val v = Array(n) { BooleanArray(n + 1) }
        val r1 = random.nextInt(n - 1)
        val c1 = random.nextInt(n - 1)
        val r2 = r1 + 1 + random.nextInt(n - 1 - r1)
        val c2 = c1 + 1 + random.nextInt(n - 1 - c1)
        for (c in c1..c2) { h[r1][c] = true; h[r2 + 1][c] = true }
        for (r in r1..r2) { v[r][c1] = true; v[r][c2 + 1] = true }
        return h.joinToString("") { row -> row.joinToString("") { if (it) "1" else "0" } } to
            v.joinToString("") { row -> row.joinToString("") { if (it) "1" else "0" } }
    }

    fun sides(n: Int, horizontal: String, vertical: String, row: Int, col: Int): Int {
        fun h(r: Int, c: Int) = horizontal[r * n + c] == '1'
        fun v(r: Int, c: Int) = vertical[r * (n + 1) + c] == '1'
        return listOf(h(row, col), h(row + 1, col), v(row, col), v(row, col + 1)).count { it }
    }

    fun loop(n: Int, horizontal: String, vertical: String): Boolean {
        val next = Array(n + 1) { Array(n + 1) { mutableListOf<Pair<Int, Int>>() } }
        fun h(r: Int, c: Int) = horizontal[r * n + c] == '1'
        fun v(r: Int, c: Int) = vertical[r * (n + 1) + c] == '1'
        for (r in 0..n) for (c in 0 until n) if (h(r, c)) {
            next[r][c] += r to c + 1
            next[r][c + 1] += r to c
        }
        for (r in 0 until n) for (c in 0..n) if (v(r, c)) {
            next[r][c] += r + 1 to c
            next[r + 1][c] += r to c
        }
        val used = next.sumOf { row -> row.count { it.size > 0 } }
        if (used == 0 || next.any { row -> row.any { it.size != 0 && it.size != 2 } }) return false
        val start = (0..n).firstNotNullOf { r -> (0..n).firstOrNull { c -> next[r][c].size == 2 }?.let { r to it } }
        var count = 0
        var prev = start
        var cur = next[start.first][start.second][0]
        val seen = mutableSetOf(start)
        while (cur != start) {
            if (!seen.add(cur)) return false
            val options = next[cur.first][cur.second]
            val step = if (options[0] != prev) options[0] else options[1]
            prev = cur
            cur = step
            count++
            if (count > used) return false
        }
        return seen.size == used
    }

    fun count(n: Int, clue: List<Int>, limit: Int): Int {
        val hCount = (n + 1) * n
        val vCount = n * (n + 1)
        val total = hCount + vCount
        val state = IntArray(total) { -1 }
        var solutions = 0
        var nodes = 0
        fun on(i: Int) = state[i] == 1
        fun hIndex(r: Int, c: Int) = r * n + c
        fun vIndex(r: Int, c: Int) = hCount + r * (n + 1) + c
        fun cellSides(cell: Int): List<Int> {
            val r = cell / n; val c = cell % n
            return listOf(hIndex(r, c), hIndex(r + 1, c), vIndex(r, c), vIndex(r, c + 1))
        }
        fun vertex(r: Int, c: Int) = listOfNotNull(
            hIndex(r, c - 1).takeIf { c > 0 },
            hIndex(r, c).takeIf { c < n },
            vIndex(r - 1, c).takeIf { r > 0 },
            vIndex(r, c).takeIf { r < n },
        )
        fun propagate(): Boolean {
            var changed = true
            while (changed) {
                changed = false
                for (cell in clue.indices) if (clue[cell] >= 0) {
                    val sides = cellSides(cell)
                    val known = sides.count { on(it) }
                    val free = sides.filter { state[it] < 0 }
                    if (known > clue[cell] || known + free.size < clue[cell]) return false
                    if (known == clue[cell]) free.forEach { state[it] = 0; changed = true }
                    if (known + free.size == clue[cell]) free.forEach { state[it] = 1; changed = true }
                }
                for (r in 0..n) for (c in 0..n) {
                    val edges = vertex(r, c)
                    val known = edges.count { on(it) }
                    val free = edges.filter { state[it] < 0 }
                    if (known > 2) return false
                    if (known == 2) free.forEach { if (state[it] != 0) { state[it] = 0; changed = true } }
                    if (known == 1 && free.size == 1 && state[free[0]] != 1) { state[free[0]] = 1; changed = true }
                    if (known == 0 && free.size == 1 && state[free[0]] != 0) { state[free[0]] = 0; changed = true }
                    if (known == 1 && free.isEmpty()) return false
                }
            }
            return true
        }
        fun rec() {
            if (solutions >= limit) return
            if (++nodes > 20_000) return
            val saved = state.copyOf()
            if (!propagate()) { saved.copyInto(state); return }
            val pick = state.indexOfFirst { it < 0 }
            if (pick < 0) {
                val h = state.take(hCount).joinToString("") { if (it == 1) "1" else "0" }
                val v = state.drop(hCount).joinToString("") { if (it == 1) "1" else "0" }
                if (loop(n, h, v) && clue.indices.all { clue[it] < 0 || sides(n, h, v, it / n, it % n) == clue[it] }) solutions++
                saved.copyInto(state)
                return
            }
            for (value in listOf(1, 0)) {
                state[pick] = value
                rec()
                if (solutions >= limit) break
            }
            saved.copyInto(state)
        }
        rec()
        return solutions
    }
}

object SlitherlinkCodec {
    fun encode(g: SlitherlinkGame) = listOf(
        "1", g.setting.toString(), g.seed.toString(), g.size.toString(),
        g.clue.joinToString(","), g.solutionH, g.solutionV, g.markH, g.markV, g.hints.toString(),
    ).joinToString("\n")

    fun decode(text: String): SlitherlinkGame? = try {
        val l = text.split('\n')
        require(l.size == 10 && l[0] == "1")
        SlitherlinkGame(l[1].toInt(), l[2].toLong(), l[3].toInt(), l[4].split(',').map(String::toInt), l[5], l[6], l[7], l[8], l[9].toInt())
    } catch (_: IllegalArgumentException) { null }
}
