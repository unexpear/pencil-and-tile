package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Bridges: islands show how many links they need. Links run straight up, down, left or right,
 * one or two on a line, never cross, and join every island into one group.
 */
data class BridgesGame(
    val setting: Int,
    val seed: Long,
    val size: Int,
    val clue: List<Int>,
    /** Line-of-sight pairs, each as two cell indexes with the first smaller. */
    val ends: List<Int>,
    val solution: List<Int>,
    val built: List<Int> = List(solution.size) { 0 },
    val hints: Int = 0,
) {
    val edges: Int get() = solution.size

    init {
        require(setting in SIZES.indices && size == SIZES[setting])
        require(clue.size == size * size && ends.size == edges * 2 && solution.size == edges && built.size == edges)
        require(solution.all { it in 0..2 } && built.all { it in 0..2 } && hints >= 0)
        require(BridgesRules.linked(size, clue, ends, solution))
    }

    val complete: Boolean get() = BridgesRules.linked(size, clue, ends, built)

    fun edge(i: Int): Pair<Int, Int> = ends[i * 2] to ends[i * 2 + 1]

    /** Cycles the link between two islands that can see each other: none, one, two, none. */
    fun cycle(a: Int, b: Int): BridgesGame? {
        if (complete) return null
        val i = index(a, b) ?: return null
        if ((1..2).any { count -> crosses(i, count) }) return null
        val next = built.toMutableList()
        next[i] = (built[i] + 1) % 3
        if (crosses(i, next[i])) return null
        return copy(built = next)
    }

    private fun index(a: Int, b: Int): Int? {
        val x = minOf(a, b); val y = maxOf(a, b)
        for (i in 0 until edges) if (ends[i * 2] == x && ends[i * 2 + 1] == y) return i
        return null
    }

    private fun crosses(edge: Int, count: Int): Boolean {
        if (count == 0) return false
        val (a, b) = edge(edge)
        for (j in 0 until edges) if (j != edge && built[j] > 0 && BridgesRules.crosses(size, a, b, ends[j * 2], ends[j * 2 + 1])) return true
        return false
    }

    fun hint(): BridgesGame? {
        if (complete) return null
        val i = built.indices.firstOrNull { built[it] != solution[it] } ?: return null
        return copy(built = built.toMutableList().also { it[i] = solution[i] }, hints = hints + 1)
    }

    companion object {
        val SIZES = listOf(7, 7, 8, 9)
        private val ISLANDS = listOf(5, 5, 6, 6)

        fun generate(seed: Long, setting: Int): BridgesGame {
            val random = Random(seed)
            val n = SIZES[setting]
            val want = ISLANDS[setting]
            repeat(80) {
                checkpoint()
                val islands = place(n, want, random)
                val sight = BridgesRules.sight(n, islands)
                if (sight.size > 12 || !BridgesRules.connects(islands, sight)) return@repeat
                val used = mutableMapOf<Pair<Int, Int>, Int>()
                val shuffled = sight.shuffled(random)
                val parent = islands.associateWith { it }.toMutableMap()
                fun find(x: Int): Int {
                    var v = x
                    while (parent[v] != v) v = parent.getValue(v)
                    return v
                }
                for ((a, b) in shuffled) {
                    if (BridgesRules.crossesAny(n, a, b, used)) continue
                    val pa = find(a); val pb = find(b)
                    if (pa == pb) continue
                    parent[pa] = pb
                    used[a to b] = 1
                }
                if (used.size != islands.size - 1) return@repeat
                fun puzzle(links: Map<Pair<Int, Int>, Int>): BridgesGame? {
                    val clue = IntArray(n * n)
                    for ((edge, count) in links) {
                        clue[edge.first] += count
                        clue[edge.second] += count
                    }
                    if (islands.any { clue[it] !in 1..8 }) return null
                    val ends = sight.flatMap { listOf(it.first, it.second) }
                    val solution = sight.map { links[it] ?: 0 }
                    if (BridgesRules.count(n, clue.toList(), ends, 2) != 1) return null
                    return BridgesGame(setting, seed, n, clue.toList(), ends, solution)
                }
                puzzle(used.toMap())?.let { return it }
                repeat(n) {
                    val (a, b) = shuffled.random(random)
                    val have = used[a to b] ?: 0
                    if (have < 2 && !BridgesRules.crossesAny(n, a, b, used.filterKeys { it != a to b })) used[a to b] = have + 1
                }
                puzzle(used)?.let { return it }
            }
            error("Couldn't build a unique Bridges")
        }

        private fun place(n: Int, want: Int, random: Random): List<Int> {
            val picked = mutableListOf<Int>()
            for (cell in (0 until n * n).shuffled(random)) {
                val r = cell / n; val c = cell % n
                if (picked.any { kotlin.math.abs(it / n - r) <= 1 && kotlin.math.abs(it % n - c) <= 1 }) continue
                picked += cell
                if (picked.size == want) break
            }
            return picked.sorted()
        }
    }
}

object BridgesRules {
    fun sight(n: Int, islands: List<Int>): List<Pair<Int, Int>> {
        val set = islands.toSet()
        val out = mutableListOf<Pair<Int, Int>>()
        for (i in islands.indices) for (j in i + 1 until islands.size) {
            val a = islands[i]; val b = islands[j]
            if (sees(n, set, a, b)) out += minOf(a, b) to maxOf(a, b)
        }
        return out
    }

    fun sees(n: Int, islands: Set<Int>, a: Int, b: Int): Boolean {
        if (a == b) return false
        val ar = a / n; val ac = a % n; val br = b / n; val bc = b % n
        if (ar != br && ac != bc) return false
        val cells = if (ar == br) {
            val c1 = minOf(ac, bc); val c2 = maxOf(ac, bc)
            (c1 + 1 until c2).map { ar * n + it }
        } else {
            val r1 = minOf(ar, br); val r2 = maxOf(ar, br)
            (r1 + 1 until r2).map { it * n + ac }
        }
        return cells.none { it in islands }
    }

    fun crosses(n: Int, a: Int, b: Int, c: Int, d: Int): Boolean {
        fun horizontal(x: Int, y: Int) = x / n == y / n
        val (h1, h2, v1, v2) = when {
            horizontal(a, b) && !horizontal(c, d) -> listOf(a, b, c, d)
            horizontal(c, d) && !horizontal(a, b) -> listOf(c, d, a, b)
            else -> return false
        }
        val hr = h1 / n
        val c1 = minOf(h1 % n, h2 % n); val c2 = maxOf(h1 % n, h2 % n)
        val vc = v1 % n
        val r1 = minOf(v1 / n, v2 / n); val r2 = maxOf(v1 / n, v2 / n)
        return vc in (c1 + 1 until c2) && hr in (r1 + 1 until r2)
    }

    fun crossesAny(n: Int, a: Int, b: Int, used: Map<Pair<Int, Int>, Int>): Boolean =
        used.any { (edge, count) -> count > 0 && crosses(n, a, b, edge.first, edge.second) }

    fun linked(n: Int, clue: List<Int>, ends: List<Int>, count: List<Int>): Boolean {
        val islands = clue.indices.filter { clue[it] > 0 }
        if (islands.isEmpty()) return false
        val degree = IntArray(clue.size)
        val open = mutableListOf<Pair<Int, Int>>()
        for (i in count.indices) {
            val a = ends[i * 2]; val b = ends[i * 2 + 1]
            if (count[i] !in 0..2) return false
            degree[a] += count[i]
            degree[b] += count[i]
            if (count[i] > 0) open += a to b
        }
        if (islands.any { degree[it] != clue[it] }) return false
        for (i in open.indices) for (j in i + 1 until open.size) {
            if (crosses(n, open[i].first, open[i].second, open[j].first, open[j].second)) return false
        }
        return connects(islands, open)
    }

    fun connects(islands: List<Int>, edges: List<Pair<Int, Int>>): Boolean {
        if (islands.isEmpty()) return false
        val parent = islands.associateWith { it }.toMutableMap()
        fun find(x: Int): Int {
            var v = x
            while (parent[v] != v) v = parent.getValue(v)
            return v
        }
        for ((a, b) in edges) {
            if (a !in parent || b !in parent) continue
            parent[find(a)] = find(b)
        }
        val root = find(islands.first())
        return islands.all { find(it) == root }
    }

    fun count(n: Int, clue: List<Int>, ends: List<Int>, limit: Int): Int {
        val edges = ends.size / 2
        val order = (0 until edges).sortedByDescending { i -> maxOf(clue[ends[i * 2]], clue[ends[i * 2 + 1]]) }
        val assign = IntArray(edges)
        val decided = BooleanArray(edges)
        var solutions = 0
        var nodes = 0
        fun degree(cell: Int): Int {
            var sum = 0
            for (i in 0 until edges) if (decided[i] && (ends[i * 2] == cell || ends[i * 2 + 1] == cell)) sum += assign[i]
            return sum
        }
        fun room(cell: Int): Int {
            var sum = 0
            for (i in 0 until edges) if (!decided[i] && (ends[i * 2] == cell || ends[i * 2 + 1] == cell)) sum += 2
            return sum
        }
        fun rec(k: Int) {
            if (solutions >= limit) return
            if (++nodes > 40_000) { solutions = limit; return }
            if (k == edges) {
                if (linked(n, clue, ends, assign.toList())) solutions++
                return
            }
            val edge = order[k]
            val a = ends[edge * 2]; val b = ends[edge * 2 + 1]
            for (value in 0..2) {
                assign[edge] = value
                decided[edge] = true
                val da = degree(a); val db = degree(b)
                val fits = da <= clue[a] && db <= clue[b] && da + room(a) >= clue[a] && db + room(b) >= clue[b]
                val cross = value > 0 && (0 until edges).any { other ->
                    other != edge && decided[other] && assign[other] > 0 && crosses(n, a, b, ends[other * 2], ends[other * 2 + 1])
                }
                if (fits && !cross) rec(k + 1)
            }
            decided[edge] = false
            assign[edge] = 0
        }
        rec(0)
        return solutions
    }
}

object BridgesCodec {
    fun encode(g: BridgesGame) = listOf(
        "1", g.setting.toString(), g.seed.toString(), g.size.toString(), g.clue.joinToString(","),
        g.ends.joinToString(","), g.solution.joinToString(""), g.built.joinToString(""), g.hints.toString(),
    ).joinToString("\n")

    fun decode(text: String): BridgesGame? = try {
        val l = text.split('\n')
        require(l.size == 9 && l[0] == "1")
        BridgesGame(
            l[1].toInt(), l[2].toLong(), l[3].toInt(), l[4].split(',').map(String::toInt),
            l[5].split(',').map(String::toInt), l[6].map { it.digitToInt() }, l[7].map { it.digitToInt() }, l[8].toInt(),
        )
    } catch (_: IllegalArgumentException) { null }
}
