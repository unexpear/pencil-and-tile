package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.solver.HintSolver
import com.simplegamegen.sudoku.solver.Technique
import com.simplegamegen.sudoku.validation.Validator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HintSolverTest {

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
    fun `single empty cell is a naked single`() {
        val board = solved.copy()
        board[4 * 9 + 4] = 0 // was 5
        val hint = HintSolver.nextHint(board, Constraints.classic(9, 3, 3), solved)!!
        assertEquals(4 * 9 + 4, hint.cell)
        assertEquals(5, hint.value)
        assertEquals(Technique.NAKED_SINGLE, hint.technique)
        assertTrue(hint.explanation.contains("R5C5"))
    }

    @Test
    fun `full board has no hint`() {
        assertNull(HintSolver.nextHint(solved, Constraints.classic(9, 3, 3), solved))
    }

    @Test
    fun `contradictory board has no hint`() {
        val board = solved.copy()
        board[0] = 6 // duplicate 6 in row 0
        assertNull(HintSolver.nextHint(board, Constraints.classic(9, 3, 3), solved))
    }

    @Test
    fun `hint playout solves generated puzzles correctly`() {
        var naked = 0
        var hidden = 0
        var reveal = 0
        for (seed in 1L..6L) {
            for (difficulty in listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)) {
                val puzzle = Generator.generateClassic(9, difficulty, seed)
                val constraints = Constraints.classic(9, 3, 3)
                val board = puzzle.toBoard()
                val solution = puzzle.solutionBoard()
                var steps = 0
                while (!board.isFull() && steps < 81) {
                    val hint = HintSolver.nextHint(board, constraints, solution)
                    assertNotNull(hint, "stuck at seed $seed $difficulty")
                    assertEquals(solution.cells[hint!!.cell], hint.value)
                    assertTrue(hint.explanation.isNotBlank())
                    when (hint.technique) {
                        Technique.NAKED_SINGLE -> naked++
                        Technique.HIDDEN_SINGLE -> hidden++
                        Technique.REVEAL -> reveal++
                    }
                    board.cells[hint.cell] = hint.value
                    steps++
                }
                assertTrue(Validator.isCompleteAndValid(board, constraints))
            }
        }
        assertTrue(naked > 0, "expected naked singles")
        assertTrue(hidden > 0, "expected hidden singles across EASY/MEDIUM/HARD seeds 1-6")
    }
}
