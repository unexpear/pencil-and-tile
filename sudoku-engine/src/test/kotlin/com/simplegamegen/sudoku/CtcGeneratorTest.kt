package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.constraints.KropkiConstraint
import com.simplegamegen.sudoku.constraints.SandwichConstraint
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.VariantType
import com.simplegamegen.sudoku.solver.Solver
import com.simplegamegen.sudoku.validation.Validator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CtcGeneratorTest {

    private fun checkUnique(puzzleSize: Int, variant: VariantType, seed: Long, gen: () -> com.simplegamegen.sudoku.model.Puzzle) {
        val puzzle = gen()
        assertEquals(variant, puzzle.variant)
        assertEquals(puzzleSize, puzzle.size)
        val constraints = Constraints.forVariant(
            puzzle.size, puzzle.boxRows, puzzle.boxCols, variant,
            puzzle.regions, puzzle.cages, puzzle.thermos, puzzle.dots,
            puzzle.arrows, puzzle.sandwich,
        )
        assertEquals(1, Solver.countSolutions(puzzle.toBoard(), constraints, limit = 2))
        val solved = Solver.solve(puzzle.toBoard(), constraints)!!
        assertArrayEquals(puzzle.solution, solved.cells)
        assertTrue(Validator.isCompleteAndValid(solved, constraints))
    }

    @Test
    fun `thermo puzzle is unique with increasing overlays`() {
        checkUnique(9, VariantType.THERMO, 41L) {
            Generator.generateThermo(9, Difficulty.EASY, 41L)
        }
        val puzzle = Generator.generateThermo(9, Difficulty.EASY, 41L)
        puzzle.thermos!!.forEach { path ->
            val digits = path.map { puzzle.solution[it] }
            assertEquals(digits.sorted(), digits, "thermo $path not increasing in solution")
            assertEquals(digits.toSet().size, digits.size)
        }
        // Deterministic.
        assertEquals(puzzle, Generator.generateThermo(9, Difficulty.EASY, 41L))
    }

    @Test
    fun `kropki puzzle is unique with matching dots`() {
        checkUnique(9, VariantType.KROPKI, 42L) {
            Generator.generateKropki(9, Difficulty.EASY, 42L)
        }
        val puzzle = Generator.generateKropki(9, Difficulty.EASY, 42L)
        puzzle.dots!!.forEach { dot ->
            val va = puzzle.solution[dot.a]
            val vb = puzzle.solution[dot.b]
            assertTrue(
                KropkiConstraint.satisfies(va, vb, dot.black),
                "dot $dot broken in solution ($va,$vb)",
            )
        }
    }

    @Test
    fun `arrow puzzle is unique with matching sums`() {
        checkUnique(9, VariantType.ARROW, 43L) {
            Generator.generateArrow(9, Difficulty.EASY, 43L)
        }
        val puzzle = Generator.generateArrow(9, Difficulty.EASY, 43L)
        puzzle.arrows!!.forEach { arrow ->
            assertEquals(
                puzzle.solution[arrow.circle],
                arrow.shaft.sumOf { puzzle.solution[it] },
            )
        }
    }

    @Test
    fun `sandwich puzzle is unique with matching clues`() {
        checkUnique(9, VariantType.SANDWICH, 44L) {
            Generator.generateSandwich(9, Difficulty.EASY, 44L)
        }
        val puzzle = Generator.generateSandwich(9, Difficulty.EASY, 44L)
        val clues = puzzle.sandwich!!
        for (c in 0 until 9) {
            val col = List(9) { r -> puzzle.solution[r * 9 + c] }
            if (clues.top[c] != 0) assertEquals(clues.top[c], SandwichConstraint.crustSum(col))
            if (clues.bottom[c] != 0) assertEquals(clues.bottom[c], SandwichConstraint.crustSum(col))
        }
        for (r in 0 until 9) {
            val row = List(9) { c -> puzzle.solution[r * 9 + c] }
            if (clues.left[r] != 0) assertEquals(clues.left[r], SandwichConstraint.crustSum(row))
            if (clues.right[r] != 0) assertEquals(clues.right[r], SandwichConstraint.crustSum(row))
        }
    }

    @Test
    fun `ctc variants generate on 6x6`() {
        checkUnique(6, VariantType.THERMO, 45L) {
            Generator.generateThermo(6, Difficulty.EASY, 45L)
        }
        checkUnique(6, VariantType.KROPKI, 46L) {
            Generator.generateKropki(6, Difficulty.EASY, 46L)
        }
    }
}
