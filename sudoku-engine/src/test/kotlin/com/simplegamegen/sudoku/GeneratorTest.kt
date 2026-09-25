package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.solver.Solver
import com.simplegamegen.sudoku.validation.Validator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GeneratorTest {

    @Test
    fun `easy 9x9 has unique solution matching stored solution`() {
        val puzzle = Generator.generateClassic(9, Difficulty.EASY, seed = 42L)
        assertEquals(9, puzzle.size)
        assertEquals(42L, puzzle.seed)
        val constraints = Constraints.classic(9, 3, 3)
        val board = puzzle.toBoard()
        assertEquals(1, Solver.countSolutions(board, constraints, limit = 2))
        val solved = Solver.solve(board, constraints)!!
        assertArrayEquals(puzzle.solution, solved.cells)
        assertTrue(Validator.isCompleteAndValid(solved, constraints))
    }

    @Test
    fun `medium 9x9 is unique and harder than easy band`() {
        val easy = Generator.generateClassic(9, Difficulty.EASY, seed = 7L)
        val medium = Generator.generateClassic(9, Difficulty.MEDIUM, seed = 7L)
        val constraints = Constraints.classic(9, 3, 3)
        assertEquals(1, Solver.countSolutions(medium.toBoard(), constraints, limit = 2))
        assertTrue(medium.givensCount() <= easy.givensCount())
    }

    @Test
    fun `same seed is deterministic`() {
        val a = Generator.generateClassic(9, Difficulty.EASY, seed = 123L)
        val b = Generator.generateClassic(9, Difficulty.EASY, seed = 123L)
        assertArrayEquals(a.givens, b.givens)
        assertArrayEquals(a.solution, b.solution)
    }

    @Test
    fun `different seeds differ`() {
        val a = Generator.generateClassic(9, Difficulty.EASY, seed = 1L)
        val b = Generator.generateClassic(9, Difficulty.EASY, seed = 2L)
        assertFalse(a.givens.contentEquals(b.givens))
    }

    @Test
    fun `6x6 and 4x4 generate unique puzzles`() {
        for (size in listOf(6, 4)) {
            val (br, bc) = Board.boxDims(size)
            val puzzle = Generator.generateClassic(size, Difficulty.EASY, seed = 99L)
            val constraints = Constraints.classic(size, br, bc)
            assertEquals(1, Solver.countSolutions(puzzle.toBoard(), constraints, limit = 2))
            val solved = Solver.solve(puzzle.toBoard(), constraints)!!
            assertArrayEquals(puzzle.solution, solved.cells)
        }
    }

    @Test
    fun `9x9 dig keeps 180-degree symmetry`() {
        val puzzle = Generator.generateClassic(9, Difficulty.MEDIUM, seed = 5L)
        val size = 9
        for (r in 0 until size) {
            for (c in 0 until size) {
                val a = puzzle.givens[r * size + c]
                val b = puzzle.givens[(size - 1 - r) * size + (size - 1 - c)]
                assertEquals(a == 0, b == 0, "symmetry broken at ($r,$c)")
            }
        }
    }
}
