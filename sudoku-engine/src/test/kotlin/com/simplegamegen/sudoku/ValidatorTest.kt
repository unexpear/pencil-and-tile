package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.validation.Validator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ValidatorTest {

    private fun constraints() = Constraints.classic(9, 3, 3)

    @Test
    fun `empty board has no conflicts`() {
        val board = Board.empty(9)
        assertTrue(Validator.findConflicts(board, constraints()).isEmpty())
        assertFalse(Validator.isCompleteAndValid(board, constraints()))
    }

    @Test
    fun `detects row duplicate`() {
        val board = Board.empty(9)
        board[0, 0] = 5
        board[0, 4] = 5
        val bad = Validator.findConflicts(board, constraints())
        assertTrue(bad.contains(0))
        assertTrue(bad.contains(4))
    }

    @Test
    fun `detects column duplicate`() {
        val board = Board.empty(9)
        board[0, 0] = 3
        board[5, 0] = 3
        val bad = Validator.findConflicts(board, constraints())
        assertTrue(bad.contains(0))
        assertTrue(bad.contains(5 * 9))
    }

    @Test
    fun `detects box duplicate`() {
        val board = Board.empty(9)
        board[0, 0] = 9
        board[1, 1] = 9
        val bad = Validator.findConflicts(board, constraints())
        assertTrue(bad.contains(0))
        assertTrue(bad.contains(10))
    }

    @Test
    fun `accepts complete valid grid`() {
        val solved = Board.fromRows(
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
        assertTrue(Validator.isCompleteAndValid(solved, constraints()))
    }

    @Test
    fun `valid placement check`() {
        val board = Board.empty(9)
        board[0, 0] = 5
        assertFalse(Validator.isValidPlacement(board, 0, 5, constraints())) // occupied
        assertFalse(Validator.isValidPlacement(board, 1, 5, constraints())) // row clash
        assertTrue(Validator.isValidPlacement(board, 1, 3, constraints()))
        assertFalse(Validator.isValidPlacement(board, 1, 0, constraints()))
        assertFalse(Validator.isValidPlacement(board, 1, 10, constraints()))
    }
}
