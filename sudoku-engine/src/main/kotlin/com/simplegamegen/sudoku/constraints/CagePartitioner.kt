package com.simplegamegen.sudoku.constraints

import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.Cage
import kotlin.random.Random

/**
 * Splits a solved grid into contiguous cages whose sums are read off the
 * solution (so the solution always satisfies them). Growth is a random walk
 * that never repeats a solution digit inside a cage; leftover cells that
 * cannot join anywhere become singles (budgeted by [maxSingles]).
 */
object CagePartitioner {

    fun partition(
        solution: Board,
        rng: Random = Random.Default,
        maxCageSize: Int = 4,
        maxSingles: Int = 1,
        maxAttempts: Int = 60,
    ): List<Cage> {
        require(maxCageSize in 2..solution.size)
        repeat(maxAttempts) {
            tryPartition(solution, Random(rng.nextLong()), maxCageSize, maxSingles)?.let { return it }
        }
        // Practically unreachable: worst case is all-singles, always valid.
        return fallbackSingles(solution)
    }

    private fun tryPartition(
        solution: Board,
        rng: Random,
        maxCageSize: Int,
        maxSingles: Int,
    ): List<Cage>? {
        val size = solution.size
        val total = size * size
        val cageOf = IntArray(total) { -1 }
        val cages = mutableListOf<MutableList<Int>>()
        var singles = 0

        fun neighbours(cell: Int): List<Int> {
            val r = cell / size
            val c = cell % size
            return buildList {
                if (r > 0) add(cell - size)
                if (r < size - 1) add(cell + size)
                if (c > 0) add(cell - 1)
                if (c < size - 1) add(cell + 1)
            }
        }

        val unassigned = (0 until total).shuffled(rng).toMutableList()
        while (unassigned.isNotEmpty()) {
            val seed = unassigned.removeLast()
            // Target size biased to 2-3 cells for good Killer structure.
            val target = when (rng.nextInt(10)) {
                in 0..3 -> 2
                in 4..7 -> 3
                8 -> 4
                else -> maxCageSize
            }.coerceAtMost(maxCageSize)
            val cage = mutableListOf(seed)
            val digits = mutableSetOf(solution.cells[seed])
            while (cage.size < target) {
                val frontier = cage
                    .flatMap { neighbours(it) }
                    .filter { cageOf[it] == -1 && solution.cells[it] !in digits }
                if (frontier.isEmpty()) break
                val next = frontier.random(rng)
                unassigned.remove(next)
                cage.add(next)
                digits.add(solution.cells[next])
            }
            if (cage.size == 1) {
                // Try to fold the single into an adjacent cage instead.
                val host = neighbours(seed)
                    .map { cageOf[it] }
                    .filter { it != -1 && cages[it].size < maxCageSize }
                    .firstOrNull { id ->
                        val d = solution.cells[seed]
                        cages[id].none { solution.cells[it] == d }
                    }
                if (host != null) {
                    cages[host].add(seed)
                    cageOf[seed] = host
                    continue
                }
                singles++
                if (singles > maxSingles) return null // over budget — retry
            }
            val id = cages.size
            cages.add(cage)
            cage.forEach { cageOf[it] = id }
        }
        if (singles > maxSingles) return null
        return cages.map { cells ->
            Cage(cells.toIntArray(), cells.sumOf { solution.cells[it] })
        }
    }

    private fun fallbackSingles(solution: Board): List<Cage> =
        List(solution.size * solution.size) { i ->
            Cage(intArrayOf(i), solution.cells[i])
        }
}
