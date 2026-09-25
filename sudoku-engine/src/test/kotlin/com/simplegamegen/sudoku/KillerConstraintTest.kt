package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.KillerConstraint
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.Cage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KillerConstraintTest {

    /** Cages on a 4x4 (digits 1..4): sums must fit distinct-digit bounds. */
    private fun cages(): List<Cage> = listOf(
        Cage(intArrayOf(0, 1, 4), 6), // must be {1,2,3}
        Cage(intArrayOf(2, 3), 7), // {3,4}
        Cage(intArrayOf(5, 6, 7), 8), // e.g. {1,3,4}
        Cage(intArrayOf(8, 9, 10, 11), 10), // {1,2,3,4}
        Cage(intArrayOf(12, 13, 14, 15), 10), // {1,2,3,4}
    )

    @Test
    fun `rejects repeat inside cage`() {
        val kc = KillerConstraint(4, cages())
        val board = Board.empty(4)
        board[0] = 2
        assertTrue(kc.violates(board, 1, 2))
        assertFalse(kc.violates(board, 1, 1))
    }

    @Test
    fun `rejects partial sum over target`() {
        val kc = KillerConstraint(4, cages())
        val board = Board.empty(4)
        board[0] = 3
        board[1] = 2 // partial 5 of 6, one cell left
        assertTrue(kc.violates(board, 4, 4)) // 9 > 6
        assertFalse(kc.violates(board, 4, 1)) // exactly 6
    }

    @Test
    fun `min-max pruning rejects unreachable totals`() {
        // Cage {2,3} sum 7: possibilities {1,6},{2,5},{3,4},{...} — 6 impossible on 4x4 (max digit 4).
        val kc = KillerConstraint(4, cages())
        val board = Board.empty(4)
        assertTrue(kc.violates(board, 2, 1)) // 1 needs 6, impossible with digits 1..4 distinct
        assertFalse(kc.violates(board, 2, 3)) // 3 + 4 works
    }

    @Test
    fun `full cage must hit sum exactly`() {
        val kc = KillerConstraint(4, cages())
        val board = Board.empty(4)
        board[0] = 1
        board[1] = 2
        assertTrue(kc.violates(board, 4, 2)) // 1+2+2=5 != 6 (also a repeat)
        assertTrue(kc.violates(board, 4, 4)) // 1+2+4=7 != 6
        assertFalse(kc.violates(board, 4, 3)) // 1+2+3=6
    }

    @Test
    fun `findConflicts flags duplicate and wrong-sum cages`() {
        val kc = KillerConstraint(4, cages())
        val board = Board.empty(4)
        board[0] = 2
        board[1] = 2
        val dup = kc.findConflicts(board)
        assertTrue(dup.contains(0))
        assertTrue(dup.contains(1))

        val full = Board.empty(4)
        full[0] = 1
        full[1] = 2
        full[4] = 4 // 1+2+4=7 != 6
        val bad = kc.findConflicts(full)
        assertTrue(bad.containsAll(listOf(0, 1, 4)))
    }

    @Test
    fun `constructor validates coverage and feasibility`() {
        // Overlap.
        assertThrows<IllegalArgumentException> {
            KillerConstraint(4, listOf(Cage(intArrayOf(0, 1), 3), Cage(intArrayOf(1, 2), 5)))
        }
        // Gap (cell 15 uncovered).
        assertThrows<IllegalArgumentException> {
            KillerConstraint(4, listOf(Cage((0 until 15).toList().toIntArray(), 100)))
        }
        // Impossible sum: 2 cells can hold 3..7 on a 4x4.
        val gap = (2 until 16).map { Cage(intArrayOf(it), it) }
        assertThrows<IllegalArgumentException> {
            KillerConstraint(4, listOf(Cage(intArrayOf(0, 1), 8)) + gap)
        }
    }

    @Test
    fun `minMaxSum respects digit range`() {
        assertEquals(1 to 4, KillerConstraint.minMaxSum(1, emptySet(), 4))
        assertEquals(3 to 7, KillerConstraint.minMaxSum(2, emptySet(), 4))
        assertEquals(3 to 5, KillerConstraint.minMaxSum(2, setOf(4), 4))
        assertEquals(
            Int.MAX_VALUE to Int.MIN_VALUE,
            KillerConstraint.minMaxSum(2, setOf(1, 2, 4), 4),
        )
    }
}
