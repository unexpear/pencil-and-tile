package com.simplegamegen.sudoku.generator

import com.simplegamegen.sudoku.constraints.CagePartitioner
import com.simplegamegen.sudoku.constraints.Constraint
import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.constraints.JigsawMaps
import com.simplegamegen.sudoku.difficulty.Rater
import com.simplegamegen.sudoku.model.ArrowShaft
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.Cage
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.KropkiDot
import com.simplegamegen.sudoku.model.Puzzle
import com.simplegamegen.sudoku.model.SandwichClues
import com.simplegamegen.sudoku.model.VariantType
import com.simplegamegen.sudoku.solver.Solver
import kotlin.random.Random

/**
 * Random full solution, then 180-degree symmetric dig keeping only
 * removals that preserve a unique solution. Shared by every variant —
 * each one just supplies its own constraint set (and Jigsaw its regions).
 */
object Generator {

    fun generateClassic(
        size: Int = 9,
        difficulty: Difficulty = Difficulty.EASY,
        seed: Long = Random.nextLong(),
    ): Puzzle {
        val rng = Random(seed)
        val (boxRows, boxCols) = Board.boxDims(size)
        val constraints = Constraints.classic(size, boxRows, boxCols)
        val solution = fillEmpty(size, boxRows, boxCols, constraints, rng, "Classic $size")
        val givens = dig(solution, constraints, Rater.targetClues(size, difficulty), rng)
        return Puzzle(
            size = size,
            boxRows = boxRows,
            boxCols = boxCols,
            givens = givens,
            solution = solution.cells.copyOf(),
            variant = VariantType.CLASSIC,
            difficulty = difficulty,
            seed = seed,
        )
    }

    /** X-Sudoku: Classic rules plus both main diagonals. */
    fun generateDiagonalX(
        size: Int = 9,
        difficulty: Difficulty = Difficulty.EASY,
        seed: Long = Random.nextLong(),
    ): Puzzle {
        val rng = Random(seed)
        val (boxRows, boxCols) = Board.boxDims(size)
        val constraints = Constraints.diagonalX(size, boxRows, boxCols)
        val solution = fillEmpty(size, boxRows, boxCols, constraints, rng, "DiagonalX $size")
        val givens = dig(solution, constraints, Rater.targetClues(size, difficulty), rng)
        return Puzzle(
            size = size,
            boxRows = boxRows,
            boxCols = boxCols,
            givens = givens,
            solution = solution.cells.copyOf(),
            variant = VariantType.DIAGONAL_X,
            difficulty = difficulty,
            seed = seed,
        )
    }

    /**
     * Jigsaw: rows + columns + irregular regions (boxes replaced).
     * Pass [regions] to replay a fixed layout, otherwise a random
     * contiguous map is grown from [seed].
     */
    fun generateJigsaw(
        size: Int = 9,
        difficulty: Difficulty = Difficulty.EASY,
        seed: Long = Random.nextLong(),
        regions: IntArray? = null,
    ): Puzzle {
        val rng = Random(seed)
        val (boxRows, boxCols) = Board.boxDims(size)
        var map = regions?.copyOf() ?: JigsawMaps.randomMap(size, rng)
        var constraints = Constraints.jigsaw(size, map)
        var candidate = Solver.solve(Board(size, boxRows, boxCols), constraints, true, rng)
        // Connected, equal-size regions need not admit a Sudoku solution.
        // Preserve explicit layouts; retry only layouts generated here.
        if (regions == null) {
            var attempts = 1
            while (candidate == null && attempts++ < 20) {
                map = JigsawMaps.randomMap(size, rng)
                constraints = Constraints.jigsaw(size, map)
                candidate = Solver.solve(Board(size, boxRows, boxCols), constraints, true, rng)
            }
            if (candidate == null) {
                map = JigsawMaps.standardBoxMap(size, boxRows, boxCols)
                constraints = Constraints.jigsaw(size, map)
                candidate = fillEmpty(size, boxRows, boxCols, constraints, rng, "Jigsaw $size")
            }
        }
        val solution = requireNotNull(candidate) { "The supplied Jigsaw regions have no solution" }
        val givens = dig(
            solution,
            constraints,
            Rater.targetClues(size, difficulty, VariantType.JIGSAW),
            rng,
        )
        return Puzzle(
            size = size,
            boxRows = boxRows,
            boxCols = boxCols,
            givens = givens,
            solution = solution.cells.copyOf(),
            variant = VariantType.JIGSAW,
            difficulty = difficulty,
            seed = seed,
            regions = map,
        )
    }

    fun generateFullSolution(size: Int, boxRows: Int, boxCols: Int, rng: Random = Random.Default): Board {
        val constraints = Constraints.classic(size, boxRows, boxCols)
        return fillEmpty(size, boxRows, boxCols, constraints, rng, "Classic $size")
    }

    /**
     * Verified generation: generate candidates with derived seeds and keep
     * the first whose [Rater.verify] verdict matches [difficulty]. Falls
     * back to the closest band (by band distance) when nothing hits within
     * [maxAttempts], so it always returns a valid unique puzzle. Every
     * candidate is unique by construction — verification only relabels.
     */
    fun generateVerified(
        variant: VariantType = VariantType.CLASSIC,
        size: Int = 9,
        difficulty: Difficulty = Difficulty.EASY,
        seed: Long = Random.nextLong(),
        maxAttempts: Int = 10,
    ): Puzzle {
        fun generate(seed: Long): Puzzle = when (variant) {
            VariantType.CLASSIC -> generateClassic(size, difficulty, seed)
            VariantType.DIAGONAL_X -> generateDiagonalX(size, difficulty, seed)
            VariantType.JIGSAW -> generateJigsaw(size, difficulty, seed)
            VariantType.KILLER -> generateKiller(size, difficulty, seed)
            VariantType.THERMO -> generateThermo(size, difficulty, seed)
            VariantType.KROPKI -> generateKropki(size, difficulty, seed)
            VariantType.ARROW -> generateArrow(size, difficulty, seed)
            VariantType.SANDWICH -> generateSandwich(size, difficulty, seed)
        }
        var best = generate(seed)
        if (Rater.verify(best) == difficulty) return best
        var bestDistance = bandDistance(Rater.verify(best), difficulty)
        for (attempt in 1 until maxAttempts) {
            val attemptSeed = seed xor (attempt * 0x9E3779B97F4A7C15u.toLong())
            val candidate = generate(attemptSeed)
            val verdict = Rater.verify(candidate)
            if (verdict == difficulty) return candidate
            val distance = bandDistance(verdict, difficulty)
            if (distance < bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }

    private fun bandDistance(actual: Difficulty, target: Difficulty): Int =
        kotlin.math.abs(actual.ordinal - target.ordinal)

    // ---- CTC pack: Classic solution + solution-consistent decoration ----

    /** Thermo: random increasing walks (length 3-5) grown off the solution. */
    fun generateThermo(
        size: Int = 9,
        difficulty: Difficulty = Difficulty.EASY,
        seed: Long = Random.nextLong(),
    ): Puzzle {
        val rng = Random(seed)
        val (boxRows, boxCols) = Board.boxDims(size)
        val solution = fillEmpty(
            size, boxRows, boxCols,
            Constraints.classic(size, boxRows, boxCols), rng, "Thermo $size",
        )
        val thermos = placeThermos(solution, rng, thermoCount(size, difficulty))
        require(thermos.isNotEmpty()) { "Thermo overlay failed for size $size" }
        val constraints = Constraints.thermo(size, boxRows, boxCols, thermos)
        val givens = dig(solution, constraints, Rater.targetClues(size, difficulty), rng)
        return Puzzle(
            size = size, boxRows = boxRows, boxCols = boxCols,
            givens = givens, solution = solution.cells.copyOf(),
            variant = VariantType.THERMO, difficulty = difficulty, seed = seed,
            thermos = thermos,
        )
    }

    /** Kropki: a subset of the solution's consecutive/ratio adjacencies. */
    fun generateKropki(
        size: Int = 9,
        difficulty: Difficulty = Difficulty.EASY,
        seed: Long = Random.nextLong(),
    ): Puzzle {
        val rng = Random(seed)
        val (boxRows, boxCols) = Board.boxDims(size)
        val solution = fillEmpty(
            size, boxRows, boxCols,
            Constraints.classic(size, boxRows, boxCols), rng, "Kropki $size",
        )
        val dots = placeDots(solution, rng, dotCount(size, difficulty))
        val constraints = Constraints.kropki(size, boxRows, boxCols, dots)
        val givens = dig(solution, constraints, Rater.targetClues(size, difficulty), rng)
        return Puzzle(
            size = size, boxRows = boxRows, boxCols = boxCols,
            givens = givens, solution = solution.cells.copyOf(),
            variant = VariantType.KROPKI, difficulty = difficulty, seed = seed,
            dots = dots,
        )
    }

    /** Arrow: straight shafts whose solution digits sum to the circle digit. */
    fun generateArrow(
        size: Int = 9,
        difficulty: Difficulty = Difficulty.EASY,
        seed: Long = Random.nextLong(),
    ): Puzzle {
        val rng = Random(seed)
        val (boxRows, boxCols) = Board.boxDims(size)
        val solution = fillEmpty(
            size, boxRows, boxCols,
            Constraints.classic(size, boxRows, boxCols), rng, "Arrow $size",
        )
        val arrows = placeArrows(solution, rng, arrowCount(size, difficulty))
        require(arrows.isNotEmpty()) { "Arrow overlay failed for size $size" }
        val constraints = Constraints.arrow(size, boxRows, boxCols, arrows)
        val givens = dig(solution, constraints, Rater.targetClues(size, difficulty), rng)
        return Puzzle(
            size = size, boxRows = boxRows, boxCols = boxCols,
            givens = givens, solution = solution.cells.copyOf(),
            variant = VariantType.ARROW, difficulty = difficulty, seed = seed,
            arrows = arrows,
        )
    }

    /** Sandwich: a subset of the solution's outside crust sums. */
    fun generateSandwich(
        size: Int = 9,
        difficulty: Difficulty = Difficulty.EASY,
        seed: Long = Random.nextLong(),
    ): Puzzle {
        val rng = Random(seed)
        val (boxRows, boxCols) = Board.boxDims(size)
        val solution = fillEmpty(
            size, boxRows, boxCols,
            Constraints.classic(size, boxRows, boxCols), rng, "Sandwich $size",
        )
        val clues = placeSandwich(solution, rng, clueCount(size, difficulty))
        val constraints = Constraints.sandwich(size, boxRows, boxCols, clues)
        val givens = dig(solution, constraints, Rater.targetClues(size, difficulty), rng)
        return Puzzle(
            size = size, boxRows = boxRows, boxCols = boxCols,
            givens = givens, solution = solution.cells.copyOf(),
            variant = VariantType.SANDWICH, difficulty = difficulty, seed = seed,
            sandwich = clues,
        )
    }

    private fun thermoCount(size: Int, difficulty: Difficulty): Int {
        val base = when (difficulty) {
            Difficulty.EASY -> 6
            Difficulty.MEDIUM -> 5
            Difficulty.HARD -> 4
            Difficulty.EXPERT -> 3
        }
        return if (size >= 9) base else (base * size / 9).coerceAtLeast(2)
    }

    private fun dotCount(size: Int, difficulty: Difficulty): Int {
        val base = when (difficulty) {
            Difficulty.EASY -> 14
            Difficulty.MEDIUM -> 12
            Difficulty.HARD -> 10
            Difficulty.EXPERT -> 8
        }
        return if (size >= 9) base else (base * size / 9).coerceAtLeast(4)
    }

    private fun arrowCount(size: Int, difficulty: Difficulty): Int {
        val base = when (difficulty) {
            Difficulty.EASY -> 4
            Difficulty.MEDIUM -> 3
            Difficulty.HARD -> 3
            Difficulty.EXPERT -> 2
        }
        return if (size >= 9) base else (base * size / 9).coerceAtLeast(1)
    }

    private fun clueCount(size: Int, difficulty: Difficulty): Int {
        val base = when (difficulty) {
            Difficulty.EASY -> 10
            Difficulty.MEDIUM -> 8
            Difficulty.HARD -> 6
            Difficulty.EXPERT -> 5
        }
        return if (size >= 9) base else (base * size / 9).coerceAtLeast(3)
    }

    private fun neighbours(size: Int, cell: Int): List<Int> {
        val r = cell / size
        val c = cell % size
        return buildList {
            if (r > 0) add(cell - size)
            if (r < size - 1) add(cell + size)
            if (c > 0) add(cell - 1)
            if (c < size - 1) add(cell + 1)
        }
    }

    private fun placeThermos(solution: Board, rng: Random, count: Int): List<List<Int>> {
        val size = solution.size
        val used = BooleanArray(size * size)
        val thermos = mutableListOf<List<Int>>()
        for (start in (0 until size * size).shuffled(rng)) {
            if (thermos.size >= count) break
            if (used[start]) continue
            val path = mutableListOf(start)
            var cur = start
            val targetLen = rng.nextInt(3, 6).coerceAtMost(size)
            while (path.size < targetLen) {
                val next = neighbours(size, cur)
                    .filter { !used[it] && it !in path && solution.cells[it] > solution.cells[cur] }
                if (next.isEmpty()) break
                cur = next.random(rng)
                path.add(cur)
            }
            if (path.size >= 3) {
                path.forEach { used[it] = true }
                thermos.add(path.toList())
            }
        }
        if (thermos.isEmpty()) {
            // Exhaustively look for a short increasing path after a random
            // walk misses one. A two-cell path is a valid final fallback.
            for (start in (0 until size * size).shuffled(rng)) {
                for (next in neighbours(size, start).shuffled(rng)) {
                    if (solution.cells[next] <= solution.cells[start]) continue
                    val end = neighbours(size, next).firstOrNull {
                        solution.cells[it] > solution.cells[next]
                    }
                    if (end != null) return listOf(listOf(start, next, end))
                }
            }
            for (start in 0 until size * size) {
                val next = neighbours(size, start).firstOrNull {
                    solution.cells[it] > solution.cells[start]
                }
                if (next != null) return listOf(listOf(start, next))
            }
        }
        return thermos
    }

    private fun placeDots(solution: Board, rng: Random, count: Int): List<KropkiDot> {
        val size = solution.size
        val candidates = mutableListOf<KropkiDot>()
        for (r in 0 until size) {
            for (c in 0 until size) {
                val a = r * size + c
                if (c < size - 1) dotBetween(solution, a, a + 1)?.let { candidates.add(it) }
                if (r < size - 1) dotBetween(solution, a, a + size)?.let { candidates.add(it) }
            }
        }
        candidates.shuffle(rng)
        return candidates.take(count)
    }

    private fun dotBetween(solution: Board, a: Int, b: Int): KropkiDot? {
        val va = solution.cells[a]
        val vb = solution.cells[b]
        val black = va == 2 * vb || vb == 2 * va
        val white = kotlin.math.abs(va - vb) == 1
        if (!black && !white) return null
        // A pair both consecutive and ratio (e.g. 1-2) reads as white.
        return KropkiDot(a, b, black = black && !white)
    }

    private fun placeArrows(solution: Board, rng: Random, count: Int): List<ArrowShaft> {
        val size = solution.size
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, -1 to 1, 1 to -1, 1 to 1)
        val candidates = mutableListOf<ArrowShaft>()
        for (circle in 0 until size * size) {
            val target = solution.cells[circle]
            if (target < 3) continue // need at least 1+2
            val cr = circle / size
            val cc = circle % size
            for ((dr, dc) in dirs) {
                for (len in 2..3) {
                    val shaft = (1..len).map { k -> Pair(cr + k * dr, cc + k * dc) }
                    if (shaft.any { (r, c) -> r !in 0 until size || c !in 0 until size }) break
                    val cells = shaft.map { (r, c) -> r * size + c }
                    if (circle in cells) continue
                    if (cells.sumOf { solution.cells[it] } == target) {
                        candidates.add(ArrowShaft(circle, cells))
                    }
                }
            }
        }
        candidates.shuffle(rng)
        val picked = mutableListOf<ArrowShaft>()
        val used = mutableSetOf<Int>()
        for (cand in candidates) {
            if (picked.size >= count) break
            val cells = cand.shaft + cand.circle
            if (cells.any { it in used }) continue
            picked.add(cand)
            used.addAll(cells)
        }
        return picked
    }

    private fun placeSandwich(solution: Board, rng: Random, count: Int): SandwichClues {
        val size = solution.size
        fun crust(line: List<Int>): Int {
            val values = line.map { solution.cells[it] }
            val i1 = values.indexOf(1)
            val iMax = values.indexOf(size)
            val seg = values.subList(minOf(i1, iMax) + 1, maxOf(i1, iMax))
            return seg.sum()
        }
        val options = mutableListOf<Triple<Char, Int, Int>>() // (side, line, sum)
        for (c in 0 until size) {
            val col = List(size) { r -> r * size + c }
            val sum = crust(col)
            if (sum > 0) {
                options.add(Triple('T', c, sum))
                options.add(Triple('B', c, sum))
            }
        }
        for (r in 0 until size) {
            val row = List(size) { c -> r * size + c }
            val sum = crust(row)
            if (sum > 0) {
                options.add(Triple('L', r, sum))
                options.add(Triple('R', r, sum))
            }
        }
        options.shuffle(rng)
        val top = IntArray(size)
        val bottom = IntArray(size)
        val left = IntArray(size)
        val right = IntArray(size)
        for ((side, line, sum) in options.take(count)) {
            when (side) {
                'T' -> top[line] = sum
                'B' -> bottom[line] = sum
                'L' -> left[line] = sum
                else -> right[line] = sum
            }
        }
        return SandwichClues(top, bottom, left, right)
    }

    /**
     * Killer: fresh Classic solution, random cage overlay with sums read off
     * the solution, then keep re-partitioning until the empty grid has a
     * unique solution. Authentic Killer shows no givens; if uniqueness will
     * not land, a couple of givens are revealed as a last resort.
     */
    fun generateKiller(
        size: Int = 9,
        difficulty: Difficulty = Difficulty.EASY,
        seed: Long = Random.nextLong(),
        maxPartitionAttempts: Int = 25,
    ): Puzzle {
        val rng = Random(seed)
        val (boxRows, boxCols) = Board.boxDims(size)
        val solution = fillEmpty(
            size, boxRows, boxCols,
            Constraints.classic(size, boxRows, boxCols), rng, "Killer $size",
        )
        val maxCage = Rater.targetMaxCageSize(difficulty)
        val maxSingles = Rater.targetMaxSingles(difficulty)

        var cages: List<Cage>? = null
        var unique = false
        for (attemptIndex in 0 until maxPartitionAttempts) {
            val attempt = CagePartitioner.partition(
                solution, Random(rng.nextLong()), maxCage, maxSingles,
            )
            val constraints = Constraints.killer(size, boxRows, boxCols, attempt)
            if (cages == null) cages = attempt // fallback candidate
            if (Solver.countSolutions(Board(size, boxRows, boxCols), constraints, limit = 2) == 1) {
                cages = attempt
                unique = true
                break
            }
        }
        val finalCages = cages ?: error("Partitioner failed for Killer $size")
        val constraints = Constraints.killer(size, boxRows, boxCols, finalCages)

        // Reveal givens only if the cage layout alone is ambiguous.
        val givensBoard = Board(size, boxRows, boxCols)
        if (!unique && Solver.countSolutions(givensBoard, constraints, limit = 2) != 1) {
            val order = (0 until size * size).shuffled(rng)
            for (i in order) {
                givensBoard.cells[i] = solution.cells[i]
                if (Solver.countSolutions(givensBoard, constraints, limit = 2) == 1) break
            }
        }

        return Puzzle(
            size = size,
            boxRows = boxRows,
            boxCols = boxCols,
            givens = givensBoard.cells.copyOf(),
            solution = solution.cells.copyOf(),
            variant = VariantType.KILLER,
            difficulty = difficulty,
            seed = seed,
            cages = finalCages,
        )
    }

    private fun fillEmpty(
        size: Int,
        boxRows: Int,
        boxCols: Int,
        constraints: List<Constraint>,
        rng: Random,
        label: String,
    ): Board {
        val empty = Board(size, boxRows, boxCols)
        return Solver.solve(empty, constraints, randomOrder = true, rng = rng)
            ?: error("Failed to generate full solution for $label")
    }

    /** Symmetric dig; returns the dug cells (solution board is left intact). */
    private fun dig(
        solution: Board,
        constraints: List<Constraint>,
        target: IntRange,
        rng: Random,
    ): IntArray {
        val size = solution.size
        val work = solution.copy()

        // Symmetric pairs (center maps to itself on odd sizes).
        val pairs = mutableListOf<Pair<Int, Int>>()
        val seen = BooleanArray(size * size)
        for (i in 0 until size * size) {
            if (seen[i]) continue
            val r = i / size
            val c = i % size
            val mirror = (size - 1 - r) * size + (size - 1 - c)
            seen[i] = true
            seen[mirror] = true
            pairs.add(i to mirror)
        }
        pairs.shuffle(rng)

        for ((a, b) in pairs) {
            if (work.givensCount() <= target.first) break
            val savedA = work.cells[a]
            val savedB = work.cells[b]
            if (savedA == 0) continue
            work.cells[a] = 0
            if (b != a) work.cells[b] = 0
            if (Solver.countSolutions(work, constraints, limit = 2) != 1) {
                work.cells[a] = savedA // restore — uniqueness would break
                if (b != a) work.cells[b] = savedB
            }
        }
        return work.cells.copyOf()
    }
}
