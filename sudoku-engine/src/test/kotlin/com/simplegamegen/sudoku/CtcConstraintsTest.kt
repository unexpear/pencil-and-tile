package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.ArrowConstraint
import com.simplegamegen.sudoku.constraints.KropkiConstraint
import com.simplegamegen.sudoku.constraints.SandwichConstraint
import com.simplegamegen.sudoku.constraints.ThermoConstraint
import com.simplegamegen.sudoku.model.ArrowShaft
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.KropkiDot
import com.simplegamegen.sudoku.model.SandwichClues
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CtcConstraintsTest {

    // ---- Thermo ----

    @Test
    fun `thermo enforces increase along path`() {
        val tc = ThermoConstraint(9, listOf(listOf(0, 1, 2)))
        val board = Board.empty(9)
        board[0] = 2
        board[2] = 5
        assertTrue(tc.violates(board, 1, 2)) // not > predecessor
        assertTrue(tc.violates(board, 1, 5)) // not < successor
        assertFalse(tc.violates(board, 1, 3))
        assertFalse(tc.violates(board, 1, 4))
    }

    @Test
    fun `thermo flags out-of-order pairs`() {
        val tc = ThermoConstraint(9, listOf(listOf(0, 1, 2)))
        val board = Board.empty(9)
        board[0] = 4
        board[1] = 2
        val bad = tc.findConflicts(board)
        assertTrue(bad.containsAll(listOf(0, 1)))
        assertFalse(bad.contains(2))
    }

    @Test
    fun `thermo rejects bad paths`() {
        assertThrows<IllegalArgumentException> { ThermoConstraint(9, listOf(listOf(0))) }
        assertThrows<IllegalArgumentException> { ThermoConstraint(9, listOf(listOf(0, 0, 1))) }
        assertThrows<IllegalArgumentException> { ThermoConstraint(9, listOf(listOf(0, 81))) }
    }

    // ---- Kropki ----

    @Test
    fun `kropki white needs consecutive`() {
        val kc = KropkiConstraint(9, listOf(KropkiDot(0, 1, black = false)))
        val board = Board.empty(9)
        board[0] = 4
        assertFalse(kc.violates(board, 1, 3))
        assertFalse(kc.violates(board, 1, 5))
        assertTrue(kc.violates(board, 1, 6))
    }

    @Test
    fun `kropki black needs ratio`() {
        val kc = KropkiConstraint(9, listOf(KropkiDot(0, 9, black = true)))
        val board = Board.empty(9)
        board[0] = 3
        assertFalse(kc.violates(board, 9, 6))
        assertTrue(kc.violates(board, 9, 5))
    }

    @Test
    fun `kropki flags broken dots`() {
        val kc = KropkiConstraint(9, listOf(KropkiDot(0, 1, black = false)))
        val board = Board.empty(9)
        board[0] = 1
        board[1] = 3
        assertEquals(setOf(0, 1), kc.findConflicts(board))
    }

    @Test
    fun `kropki rejects non-adjacent dots`() {
        assertThrows<IllegalArgumentException> {
            KropkiConstraint(9, listOf(KropkiDot(0, 2, black = false)))
        }
        assertThrows<IllegalArgumentException> {
            KropkiConstraint(9, listOf(KropkiDot(0, 10, black = true)))
        }
    }

    // ---- Arrow ----

    @Test
    fun `arrow enforces shaft sum`() {
        // circle 0, shaft 1,2 — sum must equal circle.
        val ac = ArrowConstraint(9, listOf(ArrowShaft(0, listOf(1, 2))))
        val board = Board.empty(9)
        board[0] = 6 // circle
        board[1] = 4
        assertTrue(ac.violates(board, 2, 3)) // 4+3=7 > 6
        assertFalse(ac.violates(board, 2, 2)) // 4+2=6
    }

    @Test
    fun `arrow prunes against max digit when circle empty`() {
        val ac = ArrowConstraint(9, listOf(ArrowShaft(0, listOf(1, 2))))
        val board = Board.empty(9)
        board[1] = 8
        board[2] = 8 // 16 > 9 impossible even with circle 9
        assertTrue(ac.findConflicts(board).containsAll(listOf(1, 2)))
    }

    @Test
    fun `arrow flags wrong full sum`() {
        val ac = ArrowConstraint(9, listOf(ArrowShaft(0, listOf(1, 2))))
        val board = Board.empty(9)
        board[0] = 5
        board[1] = 2
        board[2] = 2 // 2+2=4 != 5
        assertEquals(setOf(0, 1, 2), ac.findConflicts(board))
    }

    // ---- Sandwich ----

    @Test
    fun `sandwich crust sum measures between 1 and 9`() {
        // 1 at pos 0, 9 at pos 4: crust = positions 1..3.
        assertEquals(2 + 3 + 4, SandwichConstraint.crustSum(listOf(1, 2, 3, 4, 9, 5, 6, 7, 8)))
        assertEquals(0, SandwichConstraint.crustSum(listOf(1, 9, 2, 3, 4, 5, 6, 7, 8)))
    }

    @Test
    fun `sandwich only judges complete lines`() {
        val clues = SandwichClues(
            IntArray(9), IntArray(9), intArrayOf(10, 0, 0, 0, 0, 0, 0, 0, 0), IntArray(9),
        )
        val sc = SandwichConstraint(9, clues)
        val board = Board.empty(9)
        board[0, 0] = 1
        // Partial line: quiet.
        assertTrue(sc.findConflicts(board).isEmpty())
        assertFalse(sc.violates(board, 1, 5))
        // Complete the row wrong: 1 _ _ _ _ _ _ _ 9 with crust != 10.
        val row = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9) // crust 2+..+8 = 35
        row.forEachIndexed { c, v -> board[0, c] = v }
        assertTrue(sc.findConflicts(board).isNotEmpty())
    }

    @Test
    fun `sandwich accepts correct complete line`() {
        // Row: 1,9 adjacent -> crust 0... use clue accordingly with 0? 0 = no clue.
        // Instead: 3,1,5,9,... crust = 5.
        val clues = SandwichClues(
            IntArray(9), IntArray(9), intArrayOf(5, 0, 0, 0, 0, 0, 0, 0, 0), IntArray(9),
        )
        val sc = SandwichConstraint(9, clues)
        val board = Board.empty(9)
        val row = intArrayOf(3, 1, 5, 9, 2, 4, 6, 7, 8) // between 1 and 9: {5} = 5
        row.forEachIndexed { c, v -> board[0, c] = v }
        assertTrue(sc.findConflicts(board).isEmpty())
    }
}
