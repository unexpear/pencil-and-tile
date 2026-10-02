package com.simplegamegen.sudoku.logic

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/** Number-logic puzzles that aren't square Sudoku boards: Samurai, KenKen and Kakuro. */
enum class LogicKind(val title: String) { SAMURAI("Samurai Sudoku"), KENKEN("Calcudoku"), KAKURO("Kakuro"), FUTOSHIKI("Futoshiki") }

enum class LogicLevel(val label: String) { EASY("Easy"), MEDIUM("Medium"), HARD("Hard"), EXPERT("Expert") }

enum class CageOp(val symbol: String) { NONE(""), ADD("+"), SUB("−"), MUL("×"), DIV("÷") }

/** A KenKen cage: its cells combine with [op] to make [target]. */
data class MathCage(val cells: List<Int>, val op: CageOp, val target: Int) {
    val label: String get() = "$target${op.symbol}"
}

/** A Kakuro run of white cells whose distinct digits add up to [sum] (0 while generating). */
data class SumRun(val cells: List<Int>, val sum: Int, val across: Boolean)

/** Layout and rules, without any particular solution. */
data class LogicRules(
    val kind: LogicKind,
    val rows: Int,
    val cols: Int,
    val maxDigit: Int,
    val open: List<Boolean>,
    val cages: List<MathCage> = emptyList(),
    val runs: List<SumRun> = emptyList(),
    /** Futoshiki signs: each pair (a, b) of adjacent cells means digit(a) < digit(b). */
    val less: List<Pair<Int, Int>> = emptyList(),
) {
    val cellCount: Int get() = rows * cols

    /** Groups of cells whose digits must all differ. */
    val units: List<List<Int>> = when (kind) {
        LogicKind.SAMURAI -> SAMURAI_ORIGINS.flatMap { (r0, c0) ->
            val rowsU = (0 until 9).map { r -> (0 until 9).map { c -> (r0 + r) * cols + c0 + c } }
            val colsU = (0 until 9).map { c -> (0 until 9).map { r -> (r0 + r) * cols + c0 + c } }
            val boxes = (0 until 9).map { b -> (0 until 9).map { k -> (r0 + b / 3 * 3 + k / 3) * cols + c0 + b % 3 * 3 + k % 3 } }
            rowsU + colsU + boxes
        }.distinctBy { it.sorted() }
        LogicKind.KENKEN, LogicKind.FUTOSHIKI -> (0 until rows).map { r -> (0 until cols).map { r * cols + it } } +
            (0 until cols).map { c -> (0 until rows).map { it * cols + c } }
        LogicKind.KAKURO -> runs.map { it.cells }
    }

    init {
        require(rows in 2..21 && cols in 2..21 && maxDigit in 2..9 && open.size == rows * cols)
        require(units.all { u -> u.all { it in open.indices && open[it] } && u.size <= maxDigit })
        when (kind) {
            LogicKind.SAMURAI -> require(rows == 21 && cols == 21 && maxDigit == 9 && open == samuraiOpen())
            LogicKind.KENKEN -> {
                require(rows == cols && maxDigit == rows && open.all { it })
                if (cages.isNotEmpty()) require(cages.flatMap { it.cells }.sorted() == open.indices.toList())
                cages.forEach { cage ->
                    require(cage.cells.isNotEmpty() && cage.target > 0 && connected(cage.cells, cols))
                    when (cage.op) {
                        CageOp.NONE -> require(cage.cells.size == 1)
                        CageOp.SUB, CageOp.DIV -> require(cage.cells.size == 2)
                        else -> require(cage.cells.size >= 2)
                    }
                }
            }
            LogicKind.FUTOSHIKI -> {
                require(rows == cols && maxDigit == rows && open.all { it } && cages.isEmpty() && runs.isEmpty())
                require(less.all { (a, b) -> a in open.indices && b in open.indices &&
                    (kotlin.math.abs(a - b) == cols || (kotlin.math.abs(a - b) == 1 && a / cols == b / cols)) })
                require(less.map { setOf(it.first, it.second) }.toSet().size == less.size)
            }
            LogicKind.KAKURO -> {
                require(maxDigit == 9 && runs.isNotEmpty())
                runs.forEach { run ->
                    require(run.cells.size in 2..9 && run.sum >= 0)
                    val step = if (run.across) 1 else cols
                    require(run.cells.zipWithNext().all { (a, b) -> b - a == step && (!run.across || a / cols == b / cols) })
                }
                val across = runs.filter { it.across }.flatMap { it.cells }
                val down = runs.filter { !it.across }.flatMap { it.cells }
                val whites = open.indices.filter { open[it] }
                require(across.sorted() == whites && down.sorted() == whites)
            }
        }
    }

    companion object {
        /** Top-left corners of the five overlapping 9×9 grids. */
        val SAMURAI_ORIGINS = listOf(0 to 0, 0 to 12, 6 to 6, 12 to 0, 12 to 12)
        fun samuraiOpen(): List<Boolean> = List(21 * 21) { i ->
            val r = i / 21; val c = i % 21
            SAMURAI_ORIGINS.any { (r0, c0) -> r in r0 until r0 + 9 && c in c0 until c0 + 9 }
        }
        private fun connected(cells: List<Int>, cols: Int): Boolean {
            val set = cells.toSet(); val seen = mutableSetOf(cells.first()); val queue = ArrayDeque(listOf(cells.first()))
            while (queue.isNotEmpty()) {
                val c = queue.removeFirst()
                listOf(c - cols, c + cols, if (c % cols > 0) c - 1 else -1, if (c % cols < cols - 1) c + 1 else -1)
                    .filter { it in set && seen.add(it) }.forEach(queue::addLast)
            }
            return seen.size == set.size
        }
    }
}

/** A puzzle with its givens and its unique solution. */
data class LogicPuzzle(val rules: LogicRules, val level: LogicLevel, val seed: Long, val givens: List<Int>, val solution: List<Int>) {
    val kind: LogicKind get() = rules.kind

    init {
        require(givens.size == rules.cellCount && solution.size == rules.cellCount)
        rules.open.forEachIndexed { i, open ->
            if (open) require(solution[i] in 1..rules.maxDigit && (givens[i] == 0 || givens[i] == solution[i]))
            else require(solution[i] == 0 && givens[i] == 0)
        }
        require(LogicSolver.satisfies(rules, solution.toIntArray()))
    }
}

object LogicSolver {
    data class Result(val solutions: List<IntArray>, val complete: Boolean) {
        val count: Int get() = solutions.size
    }

    private val COMBOS: Array<Array<IntArray>> = Array(10) { k ->
        Array(46) { sum -> (0 until 512).filter { m -> Integer.bitCount(m) == k && digitSum(m) == sum }.toIntArray() }
    }
    /** UNION[k][sum][used]: digits that appear in some k-digit combination adding to sum and avoiding used. */
    private val UNION: Array<Array<IntArray>> = Array(10) { Array(46) { IntArray(512) } }.also { table ->
        for (m in 0 until 512) {
            val k = Integer.bitCount(m); val sum = digitSum(m); val free = 511 and m.inv()
            var u = free
            while (true) { table[k][sum][u] = table[k][sum][u] or m; if (u == 0) break; u = (u - 1) and free }
        }
    }
    private fun digitSum(mask: Int): Int = (0 until 9).sumOf { if (mask shr it and 1 == 1) it + 1 else 0 }
    private fun bit(d: Int) = 1 shl (d - 1)

    /** Whether [grid] is a complete, rule-abiding solution. */
    fun satisfies(rules: LogicRules, grid: IntArray): Boolean {
        if (rules.open.indices.any { rules.open[it] != (grid[it] in 1..rules.maxDigit) }) return false
        if (rules.units.any { u -> u.map { grid[it] }.toSet().size != u.size }) return false
        if (rules.cages.any { !cageValue(it, it.cells.map { c -> grid[c] }) }) return false
        if (rules.less.any { (a, b) -> grid[a] >= grid[b] }) return false
        return rules.runs.all { it.sum == 0 || it.cells.sumOf { c -> grid[c] } == it.sum }
    }

    fun cageValue(cage: MathCage, values: List<Int>): Boolean = when (cage.op) {
        CageOp.NONE -> values.single() == cage.target
        CageOp.ADD -> values.sum() == cage.target
        CageOp.MUL -> values.fold(1) { a, b -> a * b } == cage.target
        CageOp.SUB -> kotlin.math.abs(values[0] - values[1]) == cage.target
        CageOp.DIV -> maxOf(values[0], values[1]) == cage.target * minOf(values[0], values[1])
    }

    /**
     * Counts solutions up to [limit]. `complete = false` means the node budget ran out,
     * which proves nothing about uniqueness. [random] shuffles digit order (for filling).
     */
    fun solve(rules: LogicRules, start: IntArray, limit: Int = 2, maxNodes: Long = 2_000_000, random: Random? = null,
        preferExtremes: Boolean = false): Result {
        require(start.size == rules.cellCount && limit > 0)
        checkpoint()
        return Search(rules, start.copyOf(), limit, maxNodes, random, preferExtremes).run()
    }

    private class Search(val r: LogicRules, val grid: IntArray, val limit: Int, val maxNodes: Long, val random: Random?,
        val preferExtremes: Boolean) {
        val full = (1 shl r.maxDigit) - 1
        val openCells = r.open.indices.filter { r.open[it] }.toIntArray()
        val cellUnits = Array(r.cellCount) { IntArray(0) }
        val unitUsed = IntArray(r.units.size)
        val cellCage = IntArray(r.cellCount) { -1 }
        val cellRuns = Array(r.cellCount) { IntArray(0) }
        // Kakuro units are its runs, in the same order, so unitUsed doubles as each run's used digits.
        /** Per cell: (other cell, true when this cell must be the smaller one). */
        val cellLess = Array(r.cellCount) { emptyList<Pair<Int, Boolean>>() }
        val runSum = IntArray(r.runs.size)
        val runEmpty = IntArray(r.runs.size) { r.runs[it].cells.size }
        val found = mutableListOf<IntArray>()
        var nodes = 0L
        var stopped = false

        init {
            val lists = Array(r.cellCount) { mutableListOf<Int>() }
            r.units.forEachIndexed { u, cells -> cells.forEach { lists[it] += u } }
            lists.forEachIndexed { i, l -> cellUnits[i] = l.toIntArray() }
            r.cages.forEachIndexed { k, cage -> cage.cells.forEach { cellCage[it] = k } }
            val runLists = Array(r.cellCount) { mutableListOf<Int>() }
            r.runs.forEachIndexed { k, run -> run.cells.forEach { runLists[it] += k } }
            runLists.forEachIndexed { i, l -> cellRuns[i] = l.toIntArray() }
            val lessLists = Array(r.cellCount) { mutableListOf<Pair<Int, Boolean>>() }
            r.less.forEach { (a, b) -> lessLists[a] += b to true; lessLists[b] += a to false }
            lessLists.forEachIndexed { i, l -> cellLess[i] = l }
        }

        fun run(): Result {
            // Reject contradictory starting grids before searching.
            for (cell in openCells) {
                val v = grid[cell]
                if (v == 0) continue
                if (v !in 1..r.maxDigit) return Result(emptyList(), true)
                grid[cell] = 0
                val ok = allowed(cell, v)
                grid[cell] = v
                if (!ok) return Result(emptyList(), true)
                cellUnits[cell].forEach { unitUsed[it] = unitUsed[it] or bit(v) }
                cellRuns[cell].forEach { runSum[it] += v; runEmpty[it]-- }
            }
            visit()
            return Result(found.toList(), !stopped || found.size >= limit)
        }

        fun allowed(cell: Int, d: Int): Boolean {
            for (u in cellUnits[cell]) for (other in r.units[u]) if (other != cell && grid[other] == d) return false
            return extraOk(cell, d)
        }

        fun extraOk(cell: Int, d: Int): Boolean {
            val k = cellCage[cell]
            if (k >= 0 && !cageOk(r.cages[k], cell, d)) return false
            for (run in cellRuns[cell]) if (!runOk(r.runs[run], cell, d)) return false
            for ((other, smaller) in cellLess[cell]) {
                val v = grid[other]
                if (smaller) { if (if (v != 0) d >= v else d >= r.maxDigit) return false }
                else if (if (v != 0) d <= v else d <= 1) return false
            }
            return true
        }

        fun cageOk(cage: MathCage, cell: Int, d: Int): Boolean {
            val n = r.maxDigit
            var empties = 0; var sum = d; var product = d; var other = 0
            for (c in cage.cells) if (c != cell) { val v = grid[c]; if (v == 0) empties++ else { sum += v; product *= v; other = v } }
            return when (cage.op) {
                CageOp.NONE -> d == cage.target
                CageOp.ADD -> { val rem = cage.target - sum; rem in empties..empties * n }
                CageOp.MUL -> {
                    if (cage.target % product != 0) false else {
                        val q = cage.target / product
                        if (empties == 0) q == 1 else { var cap = 1L; repeat(empties) { cap *= n }; q <= cap }
                    }
                }
                CageOp.SUB -> if (other != 0) kotlin.math.abs(d - other) == cage.target
                    else d + cage.target <= n || d - cage.target >= 1
                CageOp.DIV -> if (other != 0) maxOf(d, other) == cage.target * minOf(d, other)
                    else d * cage.target <= n || d % cage.target == 0
            }
        }

        fun runOk(run: SumRun, cell: Int, d: Int): Boolean {
            if (run.sum == 0) return true
            var used = bit(d); var sum = d; var empties = 0
            for (c in run.cells) if (c != cell) { val v = grid[c]; if (v == 0) empties++ else { used = used or bit(v); sum += v } }
            val rem = run.sum - sum
            if (empties == 0) return rem == 0
            if (rem < 0 || rem > 45) return false
            return COMBOS[empties][rem].any { it and used == 0 }
        }

        fun candidates(cell: Int): Int {
            var used = 0
            for (u in cellUnits[cell]) used = used or unitUsed[u]
            var mask = full and used.inv()
            for (run in cellRuns[cell]) {
                val sum = r.runs[run].sum
                if (sum == 0) continue
                val rem = sum - runSum[run]
                mask = if (rem !in 0..45) 0 else mask and UNION[runEmpty[run]][rem][unitUsed[run]]
            }
            if (cellCage[cell] >= 0 || cellLess[cell].isNotEmpty()) {
                var m = mask; var out = 0
                while (m != 0) {
                    val low = m and -m; val d = Integer.numberOfTrailingZeros(low) + 1
                    if (extraOk(cell, d)) out = out or low
                    m = m xor low
                }
                mask = out
            }
            return mask
        }

        fun visit() {
            checkpoint()
            if (nodes >= maxNodes) { stopped = true; return }
            nodes++
            var best = -1; var bestMask = 0; var bestCount = Int.MAX_VALUE
            for (cell in openCells) {
                if (grid[cell] != 0) continue
                val m = candidates(cell)
                val c = Integer.bitCount(m)
                if (c == 0) return
                if (c < bestCount) { best = cell; bestMask = m; bestCount = c; if (c == 1) break }
            }
            if (best < 0) {
                found += grid.copyOf()
                if (found.size >= limit) stopped = true
                return
            }
            val digits = (1..r.maxDigit).filter { bestMask shr (it - 1) and 1 == 1 }
            val order = when {
                random == null -> digits
                // Low and high digits give sums with fewer digit combinations, so clues pin more cells.
                preferExtremes -> digits.sortedByDescending { kotlin.math.abs(it - 5) + random.nextDouble() * 2.5 }
                else -> digits.shuffled(random)
            }
            for (d in order) {
                grid[best] = d
                cellUnits[best].forEach { unitUsed[it] = unitUsed[it] or bit(d) }
                cellRuns[best].forEach { runSum[it] += d; runEmpty[it]-- }
                visit()
                cellRuns[best].forEach { runSum[it] -= d; runEmpty[it]++ }
                cellUnits[best].forEach { unitUsed[it] = unitUsed[it] and bit(d).inv() }
                grid[best] = 0
                if (stopped) return
            }
        }
    }
}

/** Seeded generators; every result is proven to have exactly one solution. */
object LogicGenerator {
    fun generate(kind: LogicKind, level: LogicLevel, seed: Long): LogicPuzzle = when (kind) {
        LogicKind.SAMURAI -> samurai(level, seed)
        LogicKind.KENKEN -> kenken(level, seed)
        LogicKind.KAKURO -> kakuro(level, seed)
        LogicKind.FUTOSHIKI -> futoshiki(level, seed)
    }

    fun futoshiki(level: LogicLevel, seed: Long): LogicPuzzle {
        val random = Random(seed)
        val n = 4 + level.ordinal
        val base = LogicRules(LogicKind.FUTOSHIKI, n, n, n, List(n * n) { true })
        val square = LogicSolver.solve(base, IntArray(n * n), limit = 1, random = random).solutions.first()
        val pairs = (0 until n * n).flatMap { c -> listOfNotNull((c + 1).takeIf { c % n < n - 1 }, (c + n).takeIf { it < n * n }).map { c to it } }
        fun oriented(a: Int, b: Int) = if (square[a] < square[b]) a to b else b to a
        // Easier levels start with more signs and a few givens; harder ones start bare.
        val signs = pairs.shuffled(random).take(pairs.size * listOf(40, 30, 22, 16)[level.ordinal] / 100)
            .map { (a, b) -> oriented(a, b) }.toMutableList()
        val givens = IntArray(n * n)
        (0 until n * n).shuffled(random).take(listOf(n, n / 2, 1, 0)[level.ordinal]).forEach { givens[it] = square[it] }
        while (true) {
            checkpoint()
            val rules = base.copy(less = signs.toList())
            val result = LogicSolver.solve(rules, givens, limit = 2, maxNodes = 400_000)
            if (!result.complete) {
                // Too open to prove quickly: pin a cell, which shrinks the search.
                givens.indices.filter { givens[it] == 0 }.random(random).let { givens[it] = square[it] }
                continue
            }
            if (result.count == 1) return LogicPuzzle(rules, level, seed, givens.toList(), square.toList())
            val other = result.solutions.first { !it.contentEquals(square) }
            // Add a sign the other solution breaks, or pin a cell if no unused sign does.
            val used = signs.map { setOf(it.first, it.second) }.toSet()
            val breaking = pairs.filter { (a, b) -> setOf(a, b) !in used && (square[a] < square[b]) != (other[a] < other[b]) }
            if (breaking.isNotEmpty()) signs += breaking.random(random).let { (a, b) -> oriented(a, b) }
            else square.indices.first { square[it] != other[it] }.let { givens[it] = square[it] }
        }
    }

    fun samurai(level: LogicLevel, seed: Long): LogicPuzzle {
        val random = Random(seed)
        val rules = LogicRules(LogicKind.SAMURAI, 21, 21, 9, LogicRules.samuraiOpen())
        val fill = LogicSolver.solve(rules, IntArray(441), limit = 1, maxNodes = 500_000, random = random).solutions.firstOrNull()
            ?: error("Couldn't fill the Samurai grid")
        val givens = fill.copyOf()
        val floor = when (level) { LogicLevel.EASY -> 215; LogicLevel.MEDIUM -> 185; LogicLevel.HARD -> 160; LogicLevel.EXPERT -> 0 }
        var count = rules.open.count { it }
        for (cell in rules.open.indices.filter { rules.open[it] }.shuffled(random)) {
            checkpoint()
            if (count <= floor) break
            val partner = 440 - cell
            if (givens[cell] == 0) continue
            val trial = givens.copyOf(); trial[cell] = 0; trial[partner] = 0
            val result = LogicSolver.solve(rules, trial, limit = 2, maxNodes = 40_000)
            if (result.complete && result.count == 1) {
                count -= if (partner == cell || givens[partner] == 0) 1 else 2
                givens[cell] = 0; givens[partner] = 0
            }
        }
        return LogicPuzzle(rules, level, seed, givens.toList(), fill.toList())
    }

    fun kenken(level: LogicLevel, seed: Long): LogicPuzzle {
        val random = Random(seed)
        val n = 4 + level.ordinal
        val maxCage = listOf(2, 3, 4, 4)[level.ordinal]
        val latin = LogicRules(LogicKind.KENKEN, n, n, n, List(n * n) { true })
        repeat(40) {
            checkpoint()
            val square = LogicSolver.solve(latin, IntArray(n * n), limit = 1, random = random).solutions.first()
            var groups = partition(n, maxCage, random)
            for (step in 0 until n * n) {
                val rules = latin.copy(cages = groups.map { label(it, square, level, random) })
                val result = LogicSolver.solve(rules, IntArray(n * n), limit = 2, maxNodes = 400_000)
                if (!result.complete) break
                if (result.count == 1) return LogicPuzzle(rules, level, seed, List(n * n) { 0 }, square.toList())
                // Pin the first cell where another solution disagrees with ours.
                val other = result.solutions.first { !it.contentEquals(square) }
                val cell = square.indices.first { square[it] != other[it] }
                groups = groups.flatMap { g -> if (cell in g) listOf(listOf(cell)) + components(g - cell, n) else listOf(g) }
            }
        }
        error("Couldn't build a unique KenKen")
    }

    private fun partition(n: Int, maxCage: Int, random: Random): List<List<Int>> {
        val owner = IntArray(n * n) { -1 }
        val groups = mutableListOf<MutableList<Int>>()
        for (start in (0 until n * n).shuffled(random)) {
            if (owner[start] >= 0) continue
            val target = if (random.nextInt(10) == 0) 1 else 2 + random.nextInt(maxCage - 1)
            val group = mutableListOf(start); owner[start] = groups.size
            while (group.size < target) {
                val next = group.flatMap { neighbors(it, n) }.filter { owner[it] < 0 }.distinct()
                if (next.isEmpty()) break
                val pick = next.random(random); owner[pick] = groups.size; group += pick
            }
            groups += group
        }
        return groups.map { it.sorted() }
    }

    private fun neighbors(c: Int, cols: Int) = listOfNotNull(
        (c - cols).takeIf { it >= 0 }, (c + cols).takeIf { it < cols * cols },
        (c - 1).takeIf { c % cols > 0 }, (c + 1).takeIf { c % cols < cols - 1 })

    private fun components(cells: List<Int>, n: Int): List<List<Int>> {
        val left = cells.toMutableSet(); val out = mutableListOf<List<Int>>()
        while (left.isNotEmpty()) {
            val comp = mutableListOf(left.first()); left.remove(comp[0]); var i = 0
            while (i < comp.size) { neighbors(comp[i], n).filter { left.remove(it) }.forEach { comp += it }; i++ }
            out += comp.sorted()
        }
        return out
    }

    private fun label(cells: List<Int>, square: IntArray, level: LogicLevel, random: Random): MathCage {
        val v = cells.map { square[it] }
        if (cells.size == 1) return MathCage(cells, CageOp.NONE, v[0])
        val product = v.fold(1) { a, b -> a * b }
        val options = buildList {
            add(MathCage(cells, CageOp.ADD, v.sum()))
            if (cells.size == 2) {
                add(MathCage(cells, CageOp.SUB, kotlin.math.abs(v[0] - v[1])))
                if (level != LogicLevel.EASY && maxOf(v[0], v[1]) % minOf(v[0], v[1]) == 0)
                    repeat(2) { add(MathCage(cells, CageOp.DIV, maxOf(v[0], v[1]) / minOf(v[0], v[1]))) }
            }
            if (level != LogicLevel.EASY && product <= 2_000) add(MathCage(cells, CageOp.MUL, product))
        }
        return options.random(random)
    }

    fun kakuro(level: LogicLevel, seed: Long): LogicPuzzle {
        val random = Random(seed)
        val size = 6 + 2 * level.ordinal
        val density = listOf(0.22, 0.26, 0.28, 0.3)[level.ordinal]
        repeat(200) {
            checkpoint()
            val white = BooleanArray(size * size) { i -> i / size > 0 && i % size > 0 }
            val interior = (size - 1) * (size - 1)
            var blacks = 0
            for (cell in white.indices.filter { white[it] }.shuffled(random)) {
                if (blacks >= interior * density) break
                val partner = (size - cell / size) * size + (size - cell % size)
                if (!white[cell] || !white[partner]) continue
                white[cell] = false; white[partner] = false
                if (keepsRuns(white, size, cell) && keepsRuns(white, size, partner)) blacks += if (cell == partner) 1 else 2
                else { white[cell] = true; white[partner] = true }
            }
            tidy(white, size)
            val minWhite = (size - 1) * (size - 1) * 40 / 100
            if (white.count { it } < minWhite) return@repeat
            var fill = LogicSolver.solve(kakuroRules(white, size, null), IntArray(size * size), limit = 1, maxNodes = 200_000,
                random = random, preferExtremes = true).solutions.firstOrNull() ?: return@repeat
            for (step in 1..80) {
                checkpoint()
                if (white.count { it } < minWhite) break
                val rules = kakuroRules(white, size, fill)
                val result = LogicSolver.solve(rules, IntArray(size * size), limit = 2, maxNodes = 300_000)
                if (!result.complete) break
                val solution = IntArray(size * size) { if (white[it]) fill[it] else 0 }
                if (result.count == 1) return LogicPuzzle(rules, level, seed, List(size * size) { 0 }, solution.toList())
                val other = result.solutions.first { !it.contentEquals(solution) }
                val differing = solution.indices.filter { solution[it] != other[it] }
                if (step % 10 == 0) {
                    // Occasionally reshape: prefer a cell whose removal leaves neighboring runs at least two long.
                    val cell = differing.firstOrNull { c -> white[c] = false; keepsRuns(white, size, c).also { white[c] = true } } ?: differing.first()
                    white[cell] = false
                    tidy(white, size)
                    continue
                }
                // Otherwise re-roll the digits of every run that took part in the ambiguity.
                val region = rules.runs.filter { run -> run.cells.any { it in differing } }.flatMap { it.cells }.toSet()
                val start = IntArray(size * size) { if (white[it] && it !in region) fill[it] else 0 }
                fill = LogicSolver.solve(kakuroRules(white, size, null), start, limit = 1, maxNodes = 100_000,
                    random = random, preferExtremes = true).solutions.firstOrNull() ?: break
            }
        }
        error("Couldn't build a unique Kakuro")
    }

    /** True when the white fragments beside a newly blackened [cell] are empty or at least two long. */
    private fun keepsRuns(white: BooleanArray, size: Int, cell: Int): Boolean {
        val r = cell / size; val c = cell % size
        fun length(dr: Int, dc: Int): Int {
            var n = 0; var rr = r + dr; var cc = c + dc
            while (rr in 0 until size && cc in 0 until size && white[rr * size + cc]) { n++; rr += dr; cc += dc }
            return n
        }
        return listOf(length(0, -1), length(0, 1), length(-1, 0), length(1, 0)).none { it == 1 }
    }

    /** Blackens cells until every run has 2–9 cells. */
    private fun tidy(white: BooleanArray, size: Int) {
        var changed = true
        while (changed) {
            changed = false
            for (across in listOf(true, false)) for (run in runsOf(white, size, across)) {
                if (run.size == 1) { white[run[0]] = false; changed = true }
                else if (run.size > 9) { white[run[run.size / 2]] = false; changed = true }
            }
        }
    }

    private fun runsOf(white: BooleanArray, size: Int, across: Boolean): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        for (line in 0 until size) {
            var current = mutableListOf<Int>()
            for (k in 0 until size) {
                val cell = if (across) line * size + k else k * size + line
                if (white[cell]) current += cell else { if (current.isNotEmpty()) out += current; current = mutableListOf() }
            }
            if (current.isNotEmpty()) out += current
        }
        return out
    }

    private fun kakuroRules(white: BooleanArray, size: Int, fill: IntArray?): LogicRules {
        val runs = listOf(true, false).flatMap { across ->
            runsOf(white, size, across).map { cells -> SumRun(cells, fill?.let { f -> cells.sumOf { f[it] } } ?: 0, across) }
        }
        return LogicRules(LogicKind.KAKURO, size, size, 9, white.toList(), runs = runs)
    }
}

/** Independent check used before a puzzle replaces a save. */
object LogicVerifier {
    fun verify(p: LogicPuzzle, maxNodes: Long = 2_000_000): Boolean {
        val result = LogicSolver.solve(p.rules, p.givens.toIntArray(), limit = 2, maxNodes = maxNodes)
        return result.complete && result.count == 1 && result.solutions[0].toList() == p.solution
    }
}
