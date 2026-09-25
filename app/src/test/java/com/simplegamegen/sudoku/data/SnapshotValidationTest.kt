package com.simplegamegen.sudoku.data

import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.Difficulty
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SnapshotValidationTest {
    private val p = Generator.generateClassic(4, Difficulty.EASY, 1L)
    private val saved = PlaySnapshot(p, p.givens.toList(), List(16) { emptySet() }, 12, 0)

    @Test
    fun `valid snapshots restore`() = assertTrue(SnapshotValidation.isValid(saved))

    @Test
    fun `out of range current digits are rejected`() {
        for (digit in listOf(-1, 5)) {
            val cells = saved.current.toMutableList().also { it[0] = digit }
            assertFalse(SnapshotValidation.isValid(saved.copy(current = cells)))
        }
    }

    @Test
    fun `fixed cells cannot be erased or altered`() {
        val index = p.givens.indexOfFirst { it != 0 }
        for (digit in listOf(0, p.givens[index] % 4 + 1)) {
            val cells = saved.current.toMutableList().also { it[index] = digit }
            assertFalse(SnapshotValidation.isValid(saved.copy(current = cells)))
        }
    }

    @Test
    fun `player mistakes in editable cells remain loadable`() {
        val index = p.givens.indexOfFirst { it == 0 }
        val cells = saved.current.toMutableList().also { it[index] = p.solution[index] % 4 + 1 }
        assertTrue(SnapshotValidation.isValid(saved.copy(current = cells)))
    }

    @Test
    fun `invalid lengths notes and counters are rejected`() {
        assertFalse(SnapshotValidation.isValid(saved.copy(current = emptyList())))
        assertFalse(SnapshotValidation.isValid(saved.copy(notes = emptyList())))
        assertFalse(SnapshotValidation.isValid(saved.copy(notes = List(16) { setOf(5) })))
        assertFalse(SnapshotValidation.isValid(saved.copy(elapsedSeconds = -1)))
        assertFalse(SnapshotValidation.isValid(saved.copy(hintsUsed = -1)))
    }

    @Test
    fun `invalid solution and unsupported geometry are rejected`() {
        assertFalse(SnapshotValidation.isValid(saved.copy(puzzle = p.copy(solution = IntArray(16) { 1 }))))
        assertFalse(SnapshotValidation.isValid(saved.copy(puzzle = p.copy(boxRows = 1, boxCols = 4))))
    }
}
