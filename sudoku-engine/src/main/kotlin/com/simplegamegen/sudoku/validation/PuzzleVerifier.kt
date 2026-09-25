package com.simplegamegen.sudoku.validation

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.model.Puzzle
import com.simplegamegen.sudoku.solver.SearchReport
import com.simplegamegen.sudoku.solver.SolutionVerdict
import com.simplegamegen.sudoku.solver.Solver

data class PuzzleVerification(val valid: Boolean, val reason: String, val search: SearchReport? = null)

object PuzzleVerifier {
    fun verify(puzzle: Puzzle, maxNodes: Long = 1_000_000): PuzzleVerification {
        val p = puzzle
        val constraints = try {
            Constraints.forVariant(p.size, p.boxRows, p.boxCols, p.variant, p.regions, p.cages,
                p.thermos, p.dots, p.arrows, p.sandwich)
        } catch (_: IllegalArgumentException) { return PuzzleVerification(false, "Invalid variant rules") }
        if (p.givens.any { it !in 0..p.size } || p.solution.any { it !in 1..p.size })
            return PuzzleVerification(false, "Invalid digits")
        if (p.givens.indices.any { p.givens[it] != 0 && p.givens[it] != p.solution[it] })
            return PuzzleVerification(false, "Givens disagree with solution")
        if (!Validator.isCompleteAndValid(p.solutionBoard(), constraints))
            return PuzzleVerification(false, "Solution violates variant rules")
        val report = Solver.analyze(p.toBoard(), constraints, maxNodes)
        return PuzzleVerification(report.verdict == SolutionVerdict.UNIQUE, report.verdict.name, report)
    }
}
