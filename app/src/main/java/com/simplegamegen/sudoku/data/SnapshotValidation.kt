package com.simplegamegen.sudoku.data

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.validation.Validator

object SnapshotValidation {
    /** Reject broken saves without rejecting ordinary mistakes in editable cells. */
    fun isValid(snapshot: PlaySnapshot): Boolean = try {
        val p = snapshot.puzzle
        val (rows, cols) = Board.boxDims(p.size)
        val count = p.size * p.size
        require(p.boxRows == rows && p.boxCols == cols)
        require(snapshot.elapsedSeconds >= 0 && snapshot.hintsUsed >= 0)
        require(snapshot.current.size == count && snapshot.notes.size == count)
        require(snapshot.current.all { it in 0..p.size })
        require(snapshot.notes.all { marks -> marks.all { it in 1..p.size } })
        require(p.givens.all { it in 0..p.size })
        require(p.givens.indices.all { i ->
            p.givens[i] == 0 ||
                (p.givens[i] == p.solution[i] && snapshot.current[i] == p.givens[i])
        })
        val constraints = Constraints.forVariant(
            p.size, p.boxRows, p.boxCols, p.variant, p.regions, p.cages,
            p.thermos, p.dots, p.arrows, p.sandwich,
        )
        Validator.isCompleteAndValid(p.solutionBoard(), constraints)
    } catch (_: IllegalArgumentException) {
        false
    }
}
