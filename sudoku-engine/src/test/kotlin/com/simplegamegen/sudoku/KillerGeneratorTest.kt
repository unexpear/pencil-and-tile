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

class KillerGeneratorTest {

    @Test
    fun `killer puzzle is unique with cage sums matching solution`() {
        val puzzle = Generator.generateKiller(9, Difficulty.EASY, seed = 31L)
        assertEquals(VariantType.KILLER, puzzle.variant)
        assertNotNull(puzzle.cages)
        val cages = puzzle.cages!!
        // Sums read off the solution.
        cages.forEach { cage ->
            assertEquals(cage.sum, cage.cells.sumOf { puzzle.solution[it] })
        }
        val constraints = Constraints.forVariant(9, 3, 3, puzzle.variant, null, cages)
        assertEquals(3, constraints.size) // rows/cols + boxes + killer
        assertEquals(1, Solver.countSolutions(puzzle.toBoard(), constraints, limit = 2))
        val solved = Solver.solve(puzzle.toBoard(), constraints)!!
        assertArrayEquals(puzzle.solution, solved.cells)
        assertTrue(Validator.isCompleteAndValid(solved, constraints))
    }

    @Test
    fun `killer is deterministic per seed`() {
        val a = Generator.generateKiller(9, Difficulty.EASY, seed = 77L)
        val b = Generator.generateKiller(9, Difficulty.EASY, seed = 77L)
        assertEquals(a, b)
    }

    @Test
    fun `killer difficulty params and rater agree`() {
        assertEquals(3, Rater.targetMaxCageSize(Difficulty.EASY))
        assertEquals(5, Rater.targetMaxCageSize(Difficulty.EXPERT))
        assertEquals(Difficulty.EASY, Rater.estimateKiller(2.5, givensCount = 0))
        assertEquals(Difficulty.MEDIUM, Rater.estimateKiller(3.0, givensCount = 0))
        assertEquals(Difficulty.HARD, Rater.estimateKiller(3.7, givensCount = 0))
        assertEquals(Difficulty.EXPERT, Rater.estimateKiller(4.2, givensCount = 0))
        assertEquals(Difficulty.EASY, Rater.estimateKiller(4.5, givensCount = 5))
    }
}
