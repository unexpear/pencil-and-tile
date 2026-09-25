package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.Puzzle
import com.simplegamegen.sudoku.solver.HintSolver
import com.simplegamegen.sudoku.solver.Solver
import com.simplegamegen.sudoku.validation.Validator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GenerationRegressionTest {
    private fun checkPuzzle(p: Puzzle) {
        val constraints = Constraints.forVariant(
            p.size, p.boxRows, p.boxCols, p.variant, p.regions, p.cages,
            p.thermos, p.dots, p.arrows, p.sandwich,
        )
        assertTrue(Validator.isCompleteAndValid(p.solutionBoard(), constraints))
        assertEquals(1, Solver.countSolutions(p.toBoard(), constraints), "seed ${p.seed}")
        assertArrayEquals(p.solution, Solver.solve(p.toBoard(), constraints)!!.cells)
    }

    @Test
    fun `jigsaw retries unsatisfiable generated maps deterministically`() {
        for (seed in 0L..199L) {
            val p = Generator.generateJigsaw(4, Difficulty.EASY, seed)
            checkPuzzle(p)
            if (seed in listOf(21L, 33L, 66L)) {
                assertEquals(p, Generator.generateJigsaw(4, Difficulty.EASY, seed))
            }
        }
    }

    @Test
    fun `thermo always has a valid overlay across the failing seed range`() {
        for (seed in 0L..199L) {
            val p = Generator.generateThermo(4, Difficulty.EASY, seed)
            assertFalse(p.thermos.isNullOrEmpty())
            checkPuzzle(p)
            if (seed in listOf(18L, 106L)) {
                assertEquals(p, Generator.generateThermo(4, Difficulty.EASY, seed))
            }
        }
    }

    @Test
    fun `killer hint playout never infers required digits from partial cages`() {
        for (seed in 0L..4L) {
            val p = Generator.generateKiller(9, Difficulty.EASY, seed)
            val constraints = Constraints.killer(9, 3, 3, p.cages!!)
            val board = p.toBoard()
            repeat(board.cells.count { it == 0 }) {
                val hint = requireNotNull(HintSolver.nextHint(board, constraints, p.solutionBoard()))
                assertEquals(p.solution[hint.cell], hint.value, "seed $seed: ${hint.explanation}")
                board.cells[hint.cell] = hint.value
            }
            assertTrue(Validator.isCompleteAndValid(board, constraints))
        }
    }

    @Test
    fun `hint rejects a locally legal mistake instead of building on it`() {
        val p = Generator.generateClassic(4, Difficulty.HARD, 5L)
        val board = com.simplegamegen.sudoku.model.Board.empty(4)
        board.cells[0] = p.solution[0] % 4 + 1
        assertNull(HintSolver.nextHint(board, Constraints.classic(4, 2, 2), p.solutionBoard()))
    }
}
