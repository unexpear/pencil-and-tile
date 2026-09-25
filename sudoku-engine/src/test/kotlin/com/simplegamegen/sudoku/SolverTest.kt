package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.solver.Solver
import com.simplegamegen.sudoku.validation.Validator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SolverTest {

    private val puzzle = arrayOf(
        intArrayOf(5, 3, 0, 0, 7, 0, 0, 0, 0),
        intArrayOf(6, 0, 0, 1, 9, 5, 0, 0, 0),
        intArrayOf(0, 9, 8, 0, 0, 0, 0, 6, 0),
        intArrayOf(8, 0, 0, 0, 6, 0, 0, 0, 3),
        intArrayOf(4, 0, 0, 8, 0, 3, 0, 0, 1),
        intArrayOf(7, 0, 0, 0, 2, 0, 0, 0, 6),
        intArrayOf(0, 6, 0, 0, 0, 0, 2, 8, 0),
        intArrayOf(0, 0, 0, 4, 1, 9, 0, 0, 5),
        intArrayOf(0, 0, 0, 0, 8, 0, 0, 7, 9),
    )

    private val solution = arrayOf(
        intArrayOf(5, 3, 4, 6, 7, 8, 9, 1, 2),
        intArrayOf(6, 7, 2, 1, 9, 5, 3, 4, 8),
        intArrayOf(1, 9, 8, 3, 4, 2, 5, 6, 7),
        intArrayOf(8, 5, 9, 7, 6, 1, 4, 2, 3),
        intArrayOf(4, 2, 6, 8, 5, 3, 7, 9, 1),
        intArrayOf(7, 1, 3, 9, 2, 4, 8, 5, 6),
        intArrayOf(9, 6, 1, 5, 3, 7, 2, 8, 4),
        intArrayOf(2, 8, 7, 4, 1, 9, 6, 3, 5),
        intArrayOf(3, 4, 5, 2, 8, 6, 1, 7, 9),
    )

    @Test
    fun `solves known 9x9 puzzle`() {
        val board = Board.fromRows(puzzle)
        val constraints = Constraints.classic(9, 3, 3)
        val solved = Solver.solve(board, constraints)
        assertNotNull(solved)
        assertEquals(Board.fromRows(solution), solved)
        assertTrue(Validator.isCompleteAndValid(solved!!, constraints))
    }

    @Test
    fun `preserves givens while solving`() {
        val board = Board.fromRows(puzzle)
        val constraints = Constraints.classic(9, 3, 3)
        val solved = Solver.solve(board, constraints)!!
        for (i in board.cells.indices) {
            if (board.cells[i] != 0) assertEquals(board.cells[i], solved.cells[i])
        }
    }

    @Test
    fun `rejects board with initial conflict`() {
        val bad = Board.fromRows(puzzle)
        bad.cells[0] = 6 // clashes with row (6 at index 8 area) — duplicate in row 0
        bad.cells[1] = 6 // force duplicate 6 in row 0
        val constraints = Constraints.classic(9, 3, 3)
        assertTrue(Validator.findConflicts(bad, constraints).isNotEmpty())
        assertNull(Solver.solve(bad, constraints))
        assertEquals(0, Solver.countSolutions(bad, constraints))
    }

    @Test
    fun `solves 4x4 and 6x6 empty boards`() {
        for (size in listOf(4, 6)) {
            val (br, bc) = Board.boxDims(size)
            val empty = Board(size, br, bc)
            val constraints = Constraints.classic(size, br, bc)
            val solved = Solver.solve(empty, constraints)
            assertNotNull(solved, "size $size should solve")
            assertTrue(Validator.isCompleteAndValid(solved!!, constraints))
        }
    }

    @Test
    fun `candidates respect row col box`() {
        val board = Board.fromRows(puzzle)
        val constraints = Constraints.classic(9, 3, 3)
        // cell (0,2) must be 1,2 or 4 given the puzzle state
        val cands = Solver.candidates(board, 2, constraints).toSet()
        assertEquals(setOf(1, 2, 4), cands)
    }
}
