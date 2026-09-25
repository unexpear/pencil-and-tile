package com.simplegamegen.sudoku.validation

import com.simplegamegen.sudoku.constraints.Constraint
import com.simplegamegen.sudoku.model.Board

/**
 * Live + final validation. Live: [findConflicts] drives red highlighting.
 * Final: [isCompleteAndValid] gates the win state.
 */
object Validator {

    fun findConflicts(board: Board, constraints: List<Constraint>): Set<Int> {
        val bad = board.cells.indices.filterTo(mutableSetOf()) { board.cells[it] !in 0..board.size }
        if (bad.isNotEmpty()) return bad
        for (c in constraints) bad.addAll(c.findConflicts(board))
        return bad
    }

    fun isValidPlacement(board: Board, index: Int, value: Int, constraints: List<Constraint>): Boolean {
        if (index !in board.cells.indices || value !in 1..board.size) return false
        if (board.cells[index] != 0) return false
        return constraints.none { it.violates(board, index, value) }
    }

    fun isCompleteAndValid(board: Board, constraints: List<Constraint>): Boolean {
        if (!board.isFull()) return false
        return findConflicts(board, constraints).isEmpty()
    }
}
