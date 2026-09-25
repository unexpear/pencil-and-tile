package com.simplegamegen.sudoku.solver

import com.simplegamegen.sudoku.constraints.Constraint
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.validation.Validator
import kotlin.random.Random

enum class SolutionVerdict { UNSOLVABLE, UNIQUE, MULTIPLE, INCOMPLETE }

data class SearchReport(
    val solutions: Int,
    val firstSolution: Board?,
    val exhausted: Boolean,
    val nodes: Long,
    val forcedPlacements: Long,
) {
    val verdict: SolutionVerdict get() = when {
        solutions >= 2 -> SolutionVerdict.MULTIPLE
        !exhausted -> SolutionVerdict.INCOMPLETE
        solutions == 1 -> SolutionVerdict.UNIQUE
        else -> SolutionVerdict.UNSOLVABLE
    }
}

/** MRV search with forced-value propagation, reversible trails and explicit proof status. */
object Solver {
    fun solve(board: Board, constraints: List<Constraint>, randomOrder: Boolean = false, rng: Random = Random.Default): Board? =
        search(board, constraints, 1, Long.MAX_VALUE, randomOrder, rng).firstSolution

    fun countSolutions(board: Board, constraints: List<Constraint>, limit: Int = 2): Int {
        require(limit > 0)
        return search(board, constraints, limit, Long.MAX_VALUE, false, Random.Default).solutions
    }

    /** Budget exhaustion is INCOMPLETE, never evidence of uniqueness or impossibility. */
    fun analyze(board: Board, constraints: List<Constraint>, maxNodes: Long = 1_000_000): SearchReport =
        search(board, constraints, 2, maxNodes, false, Random.Default)

    fun candidates(board: Board, index: Int, constraints: List<Constraint>): List<Int> {
        if (index !in board.cells.indices || board.cells[index] != 0) return emptyList()
        return (1..board.size).filter { value -> constraints.none { it.violates(board, index, value) } }
    }

    private fun search(board: Board, constraints: List<Constraint>, limit: Int, maxNodes: Long,
        randomOrder: Boolean, rng: Random): SearchReport {
        require(maxNodes >= 0)
        checkpoint()
        if (Validator.findConflicts(board, constraints).isNotEmpty()) return SearchReport(0, null, true, 0, 0)
        val work = board.copy()
        var solutions = 0
        var first: Board? = null
        var nodes = 0L
        var forced = 0L
        var stopped = false
        fun visit() {
            checkpoint()
            if (nodes >= maxNodes) { stopped = true; return }
            nodes++
            val trail = mutableListOf<Int>()
            try {
                while (true) {
                    checkpoint()
                    var chosen = -1
                    var bestCount = Int.MAX_VALUE
                    for (cell in work.cells.indices) {
                        if (work.cells[cell] != 0) continue
                        var count = 0
                        for (value in 1..work.size) {
                            if (constraints.none { it.violates(work, cell, value) }) {
                                count++
                                if (count >= bestCount) break
                            }
                        }
                        if (count == 0) return
                        if (count < bestCount) { chosen = cell; bestCount = count }
                        if (bestCount == 1) break
                    }
                    if (chosen == -1) {
                        if (Validator.isCompleteAndValid(work, constraints)) {
                            solutions++
                            if (first == null) first = work.copy()
                            if (solutions >= limit) stopped = true
                        }
                        return
                    }
                    val values = candidates(work, chosen, constraints)
                    if (values.size == 1) {
                        work.cells[chosen] = values.single()
                        trail += chosen
                        forced++
                        continue
                    }
                    for (value in if (randomOrder) values.shuffled(rng) else values) {
                        work.cells[chosen] = value
                        visit()
                        work.cells[chosen] = 0
                        if (stopped) break
                    }
                    return
                }
            } finally {
                trail.forEach { work.cells[it] = 0 }
            }
        }
        visit()
        return SearchReport(solutions, first, !stopped, nodes, forced)
    }
}

/** Also lets Android's runInterruptible stop CPU work when a game is cancelled. */
fun checkpoint() {
    if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("Puzzle computation cancelled")
}
