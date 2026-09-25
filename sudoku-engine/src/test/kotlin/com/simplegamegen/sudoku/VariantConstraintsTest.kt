package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.constraints.DiagonalConstraint
import com.simplegamegen.sudoku.constraints.JigsawMaps
import com.simplegamegen.sudoku.constraints.RegionConstraint
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.VariantType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VariantConstraintsTest {

    @Test
    fun `diagonal detects duplicates on main diagonal`() {
        val board = Board.empty(9)
        val diag = DiagonalConstraint(9)
        board[0, 0] = 7
        board[4, 4] = 7
        assertTrue(diag.violates(board, 4 * 9 + 4, 7))
        val bad = diag.findConflicts(board)
        assertTrue(bad.contains(0))
        assertTrue(bad.contains(4 * 9 + 4))
    }

    @Test
    fun `diagonal detects duplicates on anti-diagonal`() {
        val board = Board.empty(9)
        val diag = DiagonalConstraint(9)
        board[0, 8] = 3
        board[8, 0] = 3
        val bad = diag.findConflicts(board)
        assertTrue(bad.contains(8))
        assertTrue(bad.contains(8 * 9))
    }

    @Test
    fun `diagonal ignores off-diagonal cells`() {
        val board = Board.empty(9)
        val diag = DiagonalConstraint(9)
        board[0, 1] = 5
        board[1, 0] = 5 // same box-adjacent, but no shared diagonal
        assertTrue(diag.findConflicts(board).isEmpty())
        assertFalse(diag.onDiagonal(1))
        assertTrue(diag.onDiagonal(0))
        assertTrue(diag.onDiagonal(4 * 9 + 4)) // center is on both
    }

    @Test
    fun `region detects duplicates inside irregular region`() {
        // 4x4: two box-rows split differently — regions of 4 cells.
        val regions = intArrayOf(
            0, 0, 1, 1,
            0, 0, 1, 1,
            2, 2, 3, 3,
            2, 2, 3, 3,
        )
        val rc = RegionConstraint(4, regions)
        val board = Board.empty(4)
        board[0, 0] = 2
        board[1, 1] = 2 // same region 0, different row/col/box
        assertTrue(rc.violates(board, 1 * 4 + 1, 2))
        val bad = rc.findConflicts(board)
        assertTrue(bad.contains(0))
        assertTrue(bad.contains(5))
    }

    @Test
    fun `region rejects wrong cell counts`() {
        val bad = IntArray(16) { 0 } // all region 0
        assertThrows<IllegalArgumentException> { RegionConstraint(4, bad) }
    }

    @Test
    fun `region rejects disconnected layout`() {
        // Region 0 split into two separated pairs.
        val bad = intArrayOf(
            0, 1, 1, 1,
            2, 2, 2, 2,
            3, 3, 3, 3,
            0, 0, 0, 1,
        )
        assertThrows<IllegalArgumentException> { RegionConstraint(4, bad) }
    }

    @Test
    fun `forVariant composes expected constraint sets`() {
        val classic = Constraints.forVariant(9, 3, 3, VariantType.CLASSIC, null)
        assertEquals(2, classic.size)

        val x = Constraints.forVariant(9, 3, 3, VariantType.DIAGONAL_X, null)
        assertEquals(3, x.size)
        assertTrue(x.any { it is DiagonalConstraint })

        val map = JigsawMaps.standardBoxMap(9, 3, 3)
        val jigsaw = Constraints.forVariant(9, 3, 3, VariantType.JIGSAW, map)
        assertEquals(2, jigsaw.size)
        assertTrue(jigsaw.any { it is RegionConstraint })
        assertTrue(jigsaw.none { it.javaClass.simpleName == "BoxConstraint" })

        assertThrows<IllegalArgumentException> {
            Constraints.forVariant(9, 3, 3, VariantType.JIGSAW, null)
        }
        assertThrows<IllegalArgumentException> {
            Constraints.forVariant(9, 3, 3, VariantType.KILLER, null)
        }
        assertThrows<IllegalArgumentException> {
            Constraints.forVariant(9, 3, 3, VariantType.THERMO, null)
        }
    }
}
