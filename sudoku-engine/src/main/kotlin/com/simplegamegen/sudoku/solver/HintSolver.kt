package com.simplegamegen.sudoku.solver

import com.simplegamegen.sudoku.constraints.Constraint
import com.simplegamegen.sudoku.model.Board

/** Simplest-first logical techniques the hint engine explains. */
enum class Technique {
    NAKED_SINGLE,
    HIDDEN_SINGLE,
    /** No forced move found — revealing a cell from the known solution. */
    REVEAL,
}

data class Hint(
    val cell: Int,
    val value: Int,
    val technique: Technique,
    val explanation: String,
)

/**
 * Next-move finder: naked singles, then hidden singles across every named
 * unit (rows, columns, boxes, diagonals, regions, cages), then a solution
 * reveal. Returns null for full or contradictory boards.
 */
object HintSolver {

    fun nextHint(
        board: Board,
        constraints: List<Constraint>,
        solution: Board? = null,
    ): Hint? {
        if (board.isFull()) return null
        if (constraints.any { it.findConflicts(board).isNotEmpty() }) return null
        // A locally legal player mistake can still contradict the unique
        // solution. Do not turn that mistake into a misleading forced hint.
        if (solution != null && board.cells.indices.any {
                board.cells[it] != 0 && board.cells[it] != solution.cells[it]
            }
        ) return null

        // Naked singles: only one candidate fits.
        for (i in board.cells.indices) {
            if (board.cells[i] != 0) continue
            val cands = Solver.candidates(board, i, constraints)
            if (cands.size == 1) {
                return Hint(
                    cell = i,
                    value = cands.first(),
                    technique = Technique.NAKED_SINGLE,
                    explanation = "${label(i, board.size)} can only be ${cands.first()} — " +
                        "every other digit is blocked by its row, column or box.",
                )
            }
        }

        // Hidden singles: the only home for a digit inside some unit.
        for ((name, unit) in constraints.flatMap { it.namedUnits() }) {
            for (d in 1..board.size) {
                var spot = -1
                var count = 0
                for (i in unit) {
                    if (board.cells[i] != 0) continue
                    if (constraints.none { it.violates(board, i, d) }) {
                        spot = i
                        count++
                        if (count > 1) break
                    }
                }
                if (count == 1) {
                    return Hint(
                        cell = spot,
                        value = d,
                        technique = Technique.HIDDEN_SINGLE,
                        explanation = "$d must go in ${label(spot, board.size)} — " +
                            "it is the only cell in $name that can take it.",
                    )
                }
            }
        }

        // No forced move: reveal from the known (or freshly solved) solution.
        val sol = solution ?: Solver.solve(board, constraints) ?: return null
        for (i in board.cells.indices) {
            if (board.cells[i] == 0) {
                return Hint(
                    cell = i,
                    value = sol.cells[i],
                    technique = Technique.REVEAL,
                    explanation = "No forced move spotted — revealing " +
                        "${label(i, board.size)} = ${sol.cells[i]}.",
                )
            }
        }
        return null
    }

    private fun label(index: Int, size: Int): String =
        "R${index / size + 1}C${index % size + 1}"
}
