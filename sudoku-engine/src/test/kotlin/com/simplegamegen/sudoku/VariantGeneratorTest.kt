package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.difficulty.Rater
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.VariantType
import com.simplegamegen.sudoku.solver.Solver
import com.simplegamegen.sudoku.validation.Validator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VariantGeneratorTest {

    @Test
    fun `diagonal X puzzle is unique and diagonals are permutations`() {
        val puzzle = Generator.generateDiagonalX(9, Difficulty.EASY, seed = 11L)
        assertEquals(VariantType.DIAGONAL_X, puzzle.variant)
        val constraints = Constraints.forVariant(9, 3, 3, puzzle.variant, puzzle.regions)
        assertEquals(1, Solver.countSolutions(puzzle.toBoard(), constraints, limit = 2))
        val solved = Solver.solve(puzzle.toBoard(), constraints)!!
        assertArrayEquals(puzzle.solution, solved.cells)
        val main = IntArray(9) { k -> solved[k, k] }.sorted()
        val anti = IntArray(9) { k -> solved[k, 8 - k] }.sorted()
        assertEquals((1..9).toList(), main)
        assertEquals((1..9).toList(), anti)
        assertTrue(Validator.isCompleteAndValid(solved, constraints))
    }

    @Test
    fun `diagonal X is deterministic per seed`() {
        val a = Generator.generateDiagonalX(9, Difficulty.EASY, seed = 99L)
        val b = Generator.generateDiagonalX(9, Difficulty.EASY, seed = 99L)
        assertArrayEquals(a.givens, b.givens)
        assertArrayEquals(a.solution, b.solution)
    }

    @Test
    fun `jigsaw puzzle is unique and respects regions`() {
        val puzzle = Generator.generateJigsaw(9, Difficulty.EASY, seed = 21L)
        assertEquals(VariantType.JIGSAW, puzzle.variant)
        assertNotNull(puzzle.regions)
        val constraints = Constraints.forVariant(9, 3, 3, puzzle.variant, puzzle.regions)
        assertEquals(1, Solver.countSolutions(puzzle.toBoard(), constraints, limit = 2))
        val solved = Solver.solve(puzzle.toBoard(), constraints)!!
        assertArrayEquals(puzzle.solution, solved.cells)
        // Every region holds 1..9.
        val regions = puzzle.regions!!
        for (id in 0 until 9) {
            val values = regions.indices.filter { regions[it] == id }.map { solved.cells[it] }.sorted()
            assertEquals((1..9).toList(), values, "region $id")
        }
        assertTrue(Validator.isCompleteAndValid(solved, constraints))
    }

    @Test
    fun `jigsaw replays a fixed regions map`() {
        val first = Generator.generateJigsaw(6, Difficulty.EASY, seed = 5L)
        val replay = Generator.generateJigsaw(
            6, Difficulty.EASY, seed = 6L, regions = first.regions,
        )
        assertArrayEquals(first.regions, replay.regions)
    }

    @Test
    fun `jigsaw clue bands sit above classic`() {
        val classic = Rater.targetClues(9, Difficulty.EASY)
        val jigsaw = Rater.targetClues(9, Difficulty.EASY, VariantType.JIGSAW)
        assertTrue(jigsaw.first > classic.first)
        // Rater classifies with the variant bands.
        assertEquals(
            Difficulty.EASY,
            Rater.estimateByClues(9, jigsaw.first, VariantType.JIGSAW),
        )
    }
}
