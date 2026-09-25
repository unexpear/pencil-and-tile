package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.solver.SolveTechnique
import com.simplegamegen.sudoku.solver.TechniqueSolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TechniqueSolverTest {

    private val classic = Constraints.classic(9, 3, 3)

    private val solved = Board.fromRows(
        arrayOf(
            intArrayOf(5, 3, 4, 6, 7, 8, 9, 1, 2),
            intArrayOf(6, 7, 2, 1, 9, 5, 3, 4, 8),
            intArrayOf(1, 9, 8, 3, 4, 2, 5, 6, 7),
            intArrayOf(8, 5, 9, 7, 6, 1, 4, 2, 3),
            intArrayOf(4, 2, 6, 8, 5, 3, 7, 9, 1),
            intArrayOf(7, 1, 3, 9, 2, 4, 8, 5, 6),
            intArrayOf(9, 6, 1, 5, 3, 7, 2, 8, 4),
            intArrayOf(2, 8, 7, 4, 1, 9, 6, 3, 5),
            intArrayOf(3, 4, 5, 2, 8, 6, 1, 7, 9),
        ),
    )

    @Test
    fun `solves singles-only board with singles weights`() {
        val board = solved.copy()
        board[0] = 0
        board[40] = 0
        board[80] = 0
        val report = TechniqueSolver.solve(board, classic)
        assertTrue(report.solved)
        assertEquals(3, report.placements)
        assertTrue(report.maxWeight <= SolveTechnique.HIDDEN_SINGLE.weight)
        assertTrue((report.counts[SolveTechnique.NAKED_SINGLE] ?: 0) > 0)
        assertTrue(report.dependencyAvg > 0)
    }

    @Test
    fun `solved board reports zero work`() {
        val report = TechniqueSolver.solve(solved, classic)
        assertTrue(report.solved)
        assertEquals(0, report.placements)
        assertEquals(0.0, report.maxWeight)
    }

    @Test
    fun `empty board gets stuck`() {
        val report = TechniqueSolver.solve(Board.empty(9), classic)
        assertFalse(report.solved)
        assertEquals(10.0, report.maxWeight)
    }

    @Test
    fun `escargot needs search beyond the ladder`() {
        // AI Escargot — requires chains; documents where our ladder stops.
        val escargot = Board.fromRows(
            arrayOf(
                intArrayOf(1, 0, 0, 0, 0, 7, 0, 9, 0),
                intArrayOf(0, 3, 0, 0, 2, 0, 0, 0, 8),
                intArrayOf(0, 0, 9, 6, 0, 0, 5, 0, 0),
                intArrayOf(0, 0, 5, 3, 0, 0, 9, 0, 0),
                intArrayOf(0, 1, 0, 0, 8, 0, 0, 0, 2),
                intArrayOf(6, 0, 0, 0, 0, 4, 0, 0, 0),
                intArrayOf(3, 0, 0, 0, 0, 0, 0, 1, 0),
                intArrayOf(0, 4, 0, 0, 0, 0, 0, 0, 7),
                intArrayOf(0, 0, 7, 0, 0, 0, 3, 0, 0),
            ),
        )
        val report = TechniqueSolver.solve(escargot, classic)
        assertFalse(report.solved)
        assertEquals(10.0, report.maxWeight)
    }
}
