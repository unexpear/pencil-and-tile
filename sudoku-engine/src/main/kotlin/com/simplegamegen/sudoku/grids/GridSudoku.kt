package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/** Rules that apply across the whole grid, beyond rows, columns and regions. */
enum class GridRule(val label: String) {
    DIAGONALS("Both diagonals"),
    ANTI_KNIGHT("Anti-knight"),
    ANTI_KING("Anti-king"),
    NON_CONSECUTIVE("Non-consecutive"),
}

enum class Parity { NONE, ODD, EVEN }

/** Killer cage: distinct digits in [cells] adding up to [sum]. */
data class SumCage(val cells: List<Int>, val sum: Int)

/**
 * A square Sudoku of any size from 4 to 16 with any region layout. Each row, column and region
 * holds 1..[size] once; [extras] are further groups of distinct digits (Windoku windows, say).
 */
data class GridRules(
    val size: Int,
    val regions: List<Int>,
    val extras: List<List<Int>> = emptyList(),
    val rules: Set<GridRule> = emptySet(),
    val cages: List<SumCage> = emptyList(),
    val parity: List<Parity> = List(size * size) { Parity.NONE },
) {
    val cells: Int get() = size * size

    init {
        require(size in MIN_SIZE..MAX_SIZE && regions.size == cells && parity.size == cells)
        require(GridValidator.layoutProblems(size, regions.toIntArray(), extras, cages).none { it.error }) { "Invalid grid layout" }
    }

    /** Groups whose digits must all differ. */
    val units: List<List<Int>> by lazy {
        val rows = (0 until size).map { r -> (0 until size).map { r * size + it } }
        val cols = (0 until size).map { c -> (0 until size).map { it * size + c } }
        val regs = (0 until size).map { g -> regions.indices.filter { regions[it] == g } }
        val diags = if (GridRule.DIAGONALS in rules) listOf((0 until size).map { it * size + it }, (0 until size).map { it * size + size - 1 - it }) else emptyList()
        rows + cols + regs + diags + extras + cages.map { it.cells }
    }

    /** Cells that may not share a digit with each cell. */
    val peers: Array<IntArray> by lazy {
        val sets = Array(cells) { HashSet<Int>() }
        units.forEach { u -> u.forEach { a -> u.forEach { b -> if (a != b) sets[a] += b } } }
        fun around(offsets: List<Pair<Int, Int>>) {
            for (i in 0 until cells) for ((dr, dc) in offsets) {
                val r = i / size + dr; val c = i % size + dc
                if (r in 0 until size && c in 0 until size) sets[i] += r * size + c
            }
        }
        if (GridRule.ANTI_KNIGHT in rules) around(listOf(1 to 2, 2 to 1, -1 to 2, -2 to 1, 1 to -2, 2 to -1, -1 to -2, -2 to -1))
        if (GridRule.ANTI_KING in rules) around(listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1))
        Array(cells) { sets[it].toIntArray() }
    }

    /** Orthogonal neighbours, for the non-consecutive rule. */
    val sides: Array<IntArray> by lazy {
        Array(cells) { i ->
            val r = i / size; val c = i % size
            listOfNotNull((i - size).takeIf { r > 0 }, (i + size).takeIf { r < size - 1 }, (i - 1).takeIf { c > 0 }, (i + 1).takeIf { c < size - 1 }).toIntArray()
        }
    }

    /** True when [grid] (0 = empty) breaks no rule among its filled cells. */
    fun consistent(grid: IntArray): Boolean = conflicts(grid).isEmpty()

    /** Filled cells that clash with another filled cell or break a cage, parity or non-consecutive rule. */
    fun conflicts(grid: IntArray): Set<Int> {
        val bad = HashSet<Int>()
        for (i in 0 until cells) {
            val v = grid[i]; if (v == 0) continue
            if (peers[i].any { grid[it] == v }) bad += i
            if (parity[i] == Parity.ODD && v % 2 == 0 || parity[i] == Parity.EVEN && v % 2 == 1) bad += i
            if (GridRule.NON_CONSECUTIVE in rules && sides[i].any { grid[it] != 0 && kotlin.math.abs(grid[it] - v) == 1 }) bad += i
        }
        for (cage in cages) {
            val vals = cage.cells.map { grid[it] }
            val sum = vals.sum()
            if (sum > cage.sum || (vals.none { it == 0 } && sum != cage.sum)) bad += cage.cells.filter { grid[it] != 0 }
        }
        return bad
    }

    fun solved(grid: IntArray): Boolean = grid.none { it == 0 } && consistent(grid)

    companion object {
        const val MIN_SIZE = 4
        const val MAX_SIZE = 16

        /** Box shapes (rows × columns) that tile an [n]×[n] grid, squarest first. */
        fun boxShapes(n: Int): List<Pair<Int, Int>> = (1..n).filter { n % it == 0 }.map { it to n / it }
            .filter { (r, c) -> r > 1 && c > 1 }.sortedBy { (r, c) -> kotlin.math.abs(r - c) }

        /** Region map of plain boxes [br]×[bc]. */
        fun boxRegions(n: Int, br: Int, bc: Int): List<Int> = List(n * n) { i -> (i / n / br) * (n / bc) + (i % n) / bc }

        fun standard(n: Int, rules: Set<GridRule> = emptySet()): GridRules {
            val (br, bc) = boxShapes(n).first()
            return GridRules(n, boxRegions(n, br, bc), rules = rules)
        }
    }
}

/** A problem found while checking a grid; errors block play, warnings don't. */
data class GridIssue(val error: Boolean, val message: String, val cells: List<Int> = emptyList())

object GridValidator {
    fun layoutProblems(size: Int, regions: IntArray, extras: List<List<Int>>, cages: List<SumCage>): List<GridIssue> {
        val issues = mutableListOf<GridIssue>()
        val n = size; val cells = n * n
        if (regions.size != cells) return listOf(GridIssue(true, "The region map doesn't match the grid size."))
        val unassigned = regions.indices.filter { regions[it] !in 0 until n }
        if (unassigned.isNotEmpty()) issues += GridIssue(true, "${unassigned.size} squares aren't in a region yet.", unassigned)
        for (g in 0 until n) {
            val members = regions.indices.filter { regions[it] == g }
            if (members.size != n) issues += GridIssue(true, "Region ${g + 1} has ${members.size} squares; every region needs exactly $n.", members)
            else if (!connected(members, n)) issues += GridIssue(false, "Region ${g + 1} is split into separate pieces. That's allowed, but unusual.", members)
        }
        extras.forEachIndexed { k, group ->
            if (group.size > n) issues += GridIssue(true, "Extra group ${k + 1} has more than $n squares, so it can't hold different digits.", group)
            if (group.size != group.toSet().size || group.any { it !in 0 until cells }) issues += GridIssue(true, "Extra group ${k + 1} is damaged.", group)
        }
        val caged = HashMap<Int, Int>()
        cages.forEachIndexed { k, cage ->
            if (cage.cells.any { it !in 0 until cells }) { issues += GridIssue(true, "Cage ${k + 1} is damaged."); return@forEachIndexed }
            cage.cells.forEach { c -> caged[c]?.let { issues += GridIssue(true, "A square is in two cages.", listOf(c)) }; caged[c] = k }
            val m = cage.cells.size
            val min = m * (m + 1) / 2; val max = (n - m + 1..n).sum()
            when {
                m > n -> issues += GridIssue(true, "Cage ${k + 1} has more squares than there are digits.", cage.cells)
                cage.sum !in min..max -> issues += GridIssue(true, "Cage ${k + 1} can't add up to ${cage.sum}: its ${m} different digits make between $min and $max.", cage.cells)
            }
            if (!connected(cage.cells, n)) issues += GridIssue(false, "Cage ${k + 1} is split into separate pieces.", cage.cells)
        }
        return issues
    }

    fun connected(cells: List<Int>, n: Int): Boolean {
        if (cells.isEmpty()) return true
        val set = cells.toHashSet(); val seen = hashSetOf(cells.first()); val queue = ArrayDeque(listOf(cells.first()))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            listOf(c - n, c + n, if (c % n > 0) c - 1 else -1, if (c % n < n - 1) c + 1 else -1).filter { it in set && seen.add(it) }.forEach(queue::addLast)
        }
        return seen.size == set.size
    }
}

/** Counts solutions with constraint propagation (naked and hidden singles) and fewest-candidates branching. */
object GridSolver {
    data class Result(val solutions: List<IntArray>, val complete: Boolean) {
        val count: Int get() = solutions.size
    }

    /**
     * Solves by deduction only (naked and hidden singles, cage and parity limits), never guessing.
     * Returns the finished grid, or null when those steps get stuck.
     */
    fun solveByLogic(rules: GridRules, start: IntArray): IntArray? = Search(rules, 1, 0, null).logic(start)

    fun solve(rules: GridRules, start: IntArray, limit: Int = 2, maxNodes: Long = 400_000, random: Random? = null): Result {
        require(start.size == rules.cells && limit > 0)
        checkpoint()
        return Search(rules, limit, maxNodes, random).run(start)
    }

    private class Search(val r: GridRules, val limit: Int, val maxNodes: Long, val random: Random?) {
        val n = r.size
        val all = (1 shl n) - 1
        val found = mutableListOf<IntArray>()
        var nodes = 0L
        var stopped = false
        val nonConsecutive = GridRule.NON_CONSECUTIVE in r.rules
        val unitsArr = r.units.map { it.toIntArray() }
        val cellCages = IntArray(r.cells) { -1 }.also { a -> r.cages.forEachIndexed { k, c -> c.cells.forEach { a[it] = k } } }
        val parityMask = IntArray(r.cells) { i ->
            when (r.parity[i]) {
                Parity.ODD -> (0 until n).filter { it % 2 == 0 }.fold(0) { m, b -> m or (1 shl b) }
                Parity.EVEN -> (0 until n).filter { it % 2 == 1 }.fold(0) { m, b -> m or (1 shl b) }
                Parity.NONE -> all
            }
        }

        fun logic(start: IntArray): IntArray? {
            val cand = IntArray(r.cells) { parityMask[it] }
            val grid = IntArray(r.cells)
            for (i in 0 until r.cells) if (start[i] != 0 && (start[i] !in 1..n || !assign(grid, cand, i, start[i]))) return null
            return if (propagate(grid, cand) && grid.none { it == 0 } && r.solved(grid)) grid else null
        }

        fun run(start: IntArray): Result {
            val cand = IntArray(r.cells) { parityMask[it] }
            val grid = IntArray(r.cells)
            for (i in 0 until r.cells) if (start[i] != 0) {
                if (start[i] !in 1..n || !assign(grid, cand, i, start[i])) return Result(emptyList(), true)
            }
            if (propagate(grid, cand)) search(grid, cand)
            return Result(found.toList(), !stopped)
        }

        /** Places [v] in [i] and removes it from peers. Returns false on a contradiction. */
        fun assign(grid: IntArray, cand: IntArray, i: Int, v: Int): Boolean {
            val bit = 1 shl (v - 1)
            if (cand[i] and bit == 0 && grid[i] != v) return false
            if (grid[i] == v) return true
            if (grid[i] != 0) return false
            grid[i] = v; cand[i] = bit
            for (p in r.peers[i]) {
                if (grid[p] == v) return false
                if (grid[p] == 0) { cand[p] = cand[p] and bit.inv(); if (cand[p] == 0) return false }
            }
            if (nonConsecutive) for (s in r.sides[i]) {
                val near = (if (v > 1) 1 shl (v - 2) else 0) or (if (v < n) 1 shl v else 0)
                if (grid[s] != 0 && (1 shl (grid[s] - 1)) and near != 0) return false
                if (grid[s] == 0) { cand[s] = cand[s] and near.inv(); if (cand[s] == 0) return false }
            }
            val k = cellCages[i]
            if (k >= 0 && !cageOk(grid, cand, k)) return false
            return true
        }

        /** Trims cage candidates to digits that can still reach the total. */
        fun cageOk(grid: IntArray, cand: IntArray, k: Int): Boolean {
            val cage = r.cages[k]
            var sum = 0; var used = 0; val open = ArrayList<Int>(cage.cells.size)
            for (c in cage.cells) if (grid[c] != 0) { sum += grid[c]; used = used or (1 shl (grid[c] - 1)) } else open += c
            val rest = cage.sum - sum
            if (open.isEmpty()) return rest == 0
            if (rest <= 0) return false
            val free = (1..n).filter { used and (1 shl (it - 1)) == 0 }
            if (free.size < open.size) return false
            val m = open.size
            val lo = free.take(m).sum(); val hi = free.takeLast(m).sum()
            if (rest !in lo..hi) return false
            for (c in open) {
                var mask = cand[c]
                for (v in 1..n) if (mask and (1 shl (v - 1)) != 0) {
                    // v plus the smallest / largest others must be able to reach the rest.
                    val others = free.filter { it != v }
                    val min = v + others.take(m - 1).sum(); val max = v + others.takeLast(m - 1).sum()
                    if (rest !in min..max) mask = mask and (1 shl (v - 1)).inv()
                }
                if (m == 1) mask = mask and (if (rest in 1..n) 1 shl (rest - 1) else 0)
                if (mask == 0) return false
                cand[c] = mask
            }
            return true
        }

        /** Repeats naked and hidden singles until nothing changes. */
        fun propagate(grid: IntArray, cand: IntArray): Boolean {
            var changed = true
            while (changed) {
                changed = false
                for (i in 0 until r.cells) if (grid[i] == 0) {
                    val m = cand[i]
                    if (m == 0) return false
                    if (m and (m - 1) == 0) { if (!assign(grid, cand, i, Integer.numberOfTrailingZeros(m) + 1)) return false; changed = true }
                }
                for (u in unitsArr) {
                    if (u.size != n) continue
                    var seen = 0; var twice = 0; var placed = 0
                    for (c in u) {
                        if (grid[c] != 0) { placed = placed or (1 shl (grid[c] - 1)); continue }
                        twice = twice or (seen and cand[c]); seen = seen or cand[c]
                    }
                    if ((seen or placed) != all) return false
                    var once = seen and twice.inv() and placed.inv()
                    while (once != 0) {
                        val bit = once and -once; once = once xor bit
                        val digit = Integer.numberOfTrailingZeros(bit) + 1
                        // An earlier placement in this pass may already have used or removed the digit.
                        val cell = u.firstOrNull { grid[it] == 0 && cand[it] and bit != 0 }
                        if (cell == null) { if (u.none { grid[it] == digit }) return false; continue }
                        if (!assign(grid, cand, cell, digit)) return false
                        changed = true
                    }
                }
            }
            return true
        }

        fun search(grid: IntArray, cand: IntArray) {
            if (found.size >= limit || stopped) return
            if (++nodes > maxNodes) { stopped = true; return }
            if ((nodes and 1023L) == 0L) checkpoint()
            var best = -1; var bestCount = 99
            for (i in 0 until r.cells) if (grid[i] == 0) {
                val cnt = Integer.bitCount(cand[i])
                if (cnt < bestCount) { best = i; bestCount = cnt; if (cnt == 2) break }
            }
            if (best < 0) { if (r.solved(grid)) found += grid.copyOf(); return }
            // A digit with few places left in some unit can be a narrower branch than any square.
            if (bestCount > 2) {
                var unitCells: IntArray? = null; var unitDigit = 0; var fewest = bestCount
                for (u in unitsArr) {
                    if (u.size != n) continue
                    var placed = 0
                    for (c in u) if (grid[c] != 0) placed = placed or (1 shl (grid[c] - 1))
                    for (v in 1..n) {
                        val bit = 1 shl (v - 1)
                        if (placed and bit != 0) continue
                        var count = 0
                        for (c in u) if (grid[c] == 0 && cand[c] and bit != 0) count++
                        if (count < fewest) { fewest = count; unitDigit = v; unitCells = u }
                    }
                    if (fewest <= 2) break
                }
                val cells = unitCells
                if (cells != null) {
                    val bit = 1 shl (unitDigit - 1)
                    val places = cells.filter { grid[it] == 0 && cand[it] and bit != 0 }.let { if (random != null) it.shuffled(random) else it }
                    for (cell in places) {
                        val g = grid.copyOf(); val c = cand.copyOf()
                        if (assign(g, c, cell, unitDigit) && propagate(g, c)) search(g, c)
                        if (found.size >= limit || stopped) return
                    }
                    return
                }
            }
            val values = (1..n).filter { cand[best] and (1 shl (it - 1)) != 0 }.let { if (random != null) it.shuffled(random) else it }
            for (v in values) {
                val g = grid.copyOf(); val c = cand.copyOf()
                if (assign(g, c, best, v) && propagate(g, c)) search(g, c)
                if (found.size >= limit || stopped) return
            }
        }
    }
}

/** Builds puzzles for a layout: a random full grid, then givens removed while the answer stays unique. */
object GridGenerator {
    /** Share of squares left as givens, by level (Easy first). */
    fun givenShare(size: Int, level: Int): Double {
        val base = listOf(0.52, 0.44, 0.38, 0.32)[level.coerceIn(0, 3)]
        return if (size >= 12) base + 0.06 else base
    }

    /**
     * A random full grid. Random fills are usually quick but sometimes wander for a long time,
     * so many short tries beat one long one; the budget grows if they keep failing.
     */
    fun solution(rules: GridRules, random: Random, maxNodes: Long = 400_000, start: IntArray = IntArray(rules.cells)): IntArray? {
        var budget = 2_000L
        var spent = 0L
        while (spent < maxNodes * 4) {
            val result = GridSolver.solve(rules, start, limit = 1, maxNodes = budget, random = random)
            result.solutions.firstOrNull()?.let { return it }
            if (result.complete) return null
            spent += budget
            budget = (budget * 3 / 2).coerceAtMost(maxNodes)
        }
        return null
    }

    /** Removes givens from [solution] down towards [target] while the puzzle keeps one solution; [keep] stays put. */
    fun dig(rules: GridRules, solution: IntArray, target: Int, random: Random, keep: Set<Int> = emptySet(), symmetric: Boolean = true,
        logicOnly: Boolean = false): IntArray {
        val grid = solution.copyOf()
        val order = (0 until rules.cells).filter { it !in keep }.shuffled(random)
        var filled = rules.cells
        val tried = HashSet<Int>()
        for (i in order) {
            if (filled <= target) break
            if (i in tried || grid[i] == 0) continue
            val mirror = rules.cells - 1 - i
            val pair = if (symmetric && mirror != i && mirror !in keep && grid[mirror] != 0) listOf(i, mirror) else listOf(i)
            tried += pair
            val saved = pair.map { grid[it] }
            pair.forEach { grid[it] = 0 }
            // Easier levels must stay solvable by plain deduction; all levels must keep exactly one answer.
            val ok = if (logicOnly) GridSolver.solveByLogic(rules, grid) != null
                else GridSolver.solve(rules, grid, limit = 2, maxNodes = 60_000).let { it.count == 1 && it.complete }
            if (ok) filled -= pair.size
            else pair.forEachIndexed { k, c -> grid[c] = saved[k] }
        }
        return grid
    }

    /** Irregular connected regions made by trading squares between neighbouring boxes. */
    fun jigsawRegions(n: Int, random: Random, swaps: Int = n * n * 6): List<Int> {
        val start = GridRules.boxShapes(n).firstOrNull()?.let { (br, bc) -> GridRules.boxRegions(n, br, bc) } ?: List(n * n) { it / n }
        val map = start.toIntArray()
        fun members(g: Int) = map.indices.filter { map[it] == g }
        repeat(swaps) {
            checkpoint()
            // Swap a pair of squares across a border so both regions keep their size.
            val a = random.nextInt(n * n)
            val nb = listOf(a - n, a + n, if (a % n > 0) a - 1 else -1, if (a % n < n - 1) a + 1 else -1).filter { it in map.indices && map[it] != map[a] }
            if (nb.isEmpty()) return@repeat
            val b0 = nb.random(random)
            val ga = map[a]; val gb = map[b0]
            // Find a square of gb... region B gives a square to A elsewhere to keep sizes: pick a square of A bordering B to hand over.
            val candidates = members(ga).filter { c -> c != a && listOf(c - n, c + n, if (c % n > 0) c - 1 else -1, if (c % n < n - 1) c + 1 else -1).any { it in map.indices && map[it] == gb } }
            if (candidates.isEmpty()) return@repeat
            val c = candidates.random(random)
            map[b0] = ga; map[c] = gb
            if (!GridValidator.connected(members(ga), n) || !GridValidator.connected(members(gb), n)) { map[b0] = gb; map[c] = ga }
        }
        return map.toList()
    }

    /**
     * Irregular regions that fit an existing full grid: squares with the same digit swap between
     * neighbouring regions, so every region still holds each digit once. Much faster than filling
     * an irregular grid from scratch.
     */
    fun jigsawFromSolution(n: Int, start: List<Int>, solution: IntArray, random: Random, swaps: Int = n * n * 40): List<Int> {
        val map = start.toIntArray()
        fun sides(c: Int) = listOf(c - n, c + n, if (c % n > 0) c - 1 else -1, if (c % n < n - 1) c + 1 else -1).filter { it in map.indices }
        fun members(g: Int) = map.indices.filter { map[it] == g }
        repeat(swaps) {
            if (it % 64 == 0) checkpoint()
            val a = random.nextInt(n * n)
            val ga = map[a]
            val gb = sides(a).map { map[it] }.filter { it != ga }.randomOrNull(random) ?: return@repeat
            val partners = map.indices.filter { b -> map[b] == gb && solution[b] == solution[a] && sides(b).any { map[it] == ga } }
            val b = partners.randomOrNull(random) ?: return@repeat
            map[a] = gb; map[b] = ga
            if (!GridValidator.connected(members(ga), n) || !GridValidator.connected(members(gb), n)) { map[a] = ga; map[b] = gb }
        }
        return map.toList()
    }

    /** Splits the grid into connected cages of 1–[maxSize] squares; sums come from [solution]. */
    fun cages(n: Int, solution: IntArray, random: Random, maxSize: Int = 4): List<SumCage> {
        val owner = IntArray(n * n) { -1 }
        val cages = mutableListOf<MutableList<Int>>()
        for (start in (0 until n * n).shuffled(random)) {
            if (owner[start] >= 0) continue
            val cage = mutableListOf(start); owner[start] = cages.size
            val want = 1 + random.nextInt(maxSize).let { if (it == 0 && random.nextBoolean()) 1 else it }
            while (cage.size < want) {
                val digits = cage.map { solution[it] }.toSet()
                val next = cage.flatMap { c -> listOf(c - n, c + n, if (c % n > 0) c - 1 else -1, if (c % n < n - 1) c + 1 else -1) }
                    .filter { it in owner.indices && owner[it] < 0 && solution[it] !in digits }.distinct()
                if (next.isEmpty()) break
                val pick = next.random(random); cage += pick; owner[pick] = cages.size
            }
            cages += cage
        }
        return cages.map { c -> SumCage(c.sorted(), c.sumOf { solution[it] }) }
    }
}
