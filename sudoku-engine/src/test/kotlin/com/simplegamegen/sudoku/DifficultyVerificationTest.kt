package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.difficulty.Rater
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.VariantType
import com.simplegamegen.sudoku.solver.Solver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DifficultyVerificationTest {

    @Test
    fun `technique score does not decrease from easy to hard`() {
        val easyAvg = (1L..3L).map {
            Rater.techniqueScore(Generator.generateClassic(9, Difficulty.EASY, it))
        }.average()
        val hardAvg = (1L..3L).map {
            Rater.techniqueScore(Generator.generateClassic(9, Difficulty.HARD, it))
        }.average()
        assertTrue(easyAvg <= hardAvg, "easy=$easyAvg hard=$hardAvg")
    }

    @Test
    fun `dependency separates easy from hard`() {
        val easyAvg = (1L..3L).map {
            Rater.dependency(Generator.generateClassic(9, Difficulty.EASY, it))
        }.average()
        val hardAvg = (1L..3L).map {
            Rater.dependency(Generator.generateClassic(9, Difficulty.HARD, it))
        }.average()
        assertTrue(easyAvg > hardAvg, "easy=$easyAvg hard=$hardAvg")
    }

    @Test
    fun `verify labels known puzzles honestly`() {
        // Seed 1 EASY: wide-open (dep ~46) -> EASY.
        assertEquals(
            Difficulty.EASY,
            Rater.verify(Generator.generateClassic(9, Difficulty.EASY, 1L)),
        )
        // Seed 1 EXPERT: ladder stuck (needs search) -> EXPERT.
        assertEquals(
            Difficulty.EXPERT,
            Rater.verify(Generator.generateClassic(9, Difficulty.EXPERT, 1L)),
        )
        // Killer dispatches to cage structure, not the ladder.
        assertEquals(
            Difficulty.EASY,
            Rater.verify(Generator.generateKiller(9, Difficulty.EASY, 1L)),
        )
    }

    @Test
    fun `verified classic easy hits its band and stays unique`() {
        val puzzle = Generator.generateVerified(VariantType.CLASSIC, 9, Difficulty.EASY, 1L)
        assertEquals(Difficulty.EASY, Rater.verify(puzzle))
        val constraints = Constraints.classic(9, 3, 3)
        assertEquals(1, Solver.countSolutions(puzzle.toBoard(), constraints, limit = 2))
    }

    @Test
    fun `verified jigsaw easy hits its band`() {
        val puzzle = Generator.generateVerified(VariantType.JIGSAW, 9, Difficulty.EASY, 2L, maxAttempts = 12)
        assertEquals(Difficulty.EASY, Rater.verify(puzzle))
    }

    @Test
    fun `verified killer easy rates easy on cages`() {
        val puzzle = Generator.generateVerified(VariantType.KILLER, 9, Difficulty.EASY, 3L)
        assertEquals(Difficulty.EASY, Rater.verify(puzzle))
    }

    @Test
    fun `verified fallback always returns a unique puzzle`() {
        // EXPERT on a tiny attempt budget: may miss the band, must stay valid.
        val puzzle = Generator.generateVerified(VariantType.CLASSIC, 9, Difficulty.EXPERT, 9L, maxAttempts = 2)
        val constraints = Constraints.classic(9, 3, 3)
        assertEquals(1, Solver.countSolutions(puzzle.toBoard(), constraints, limit = 2))
        assertArrayEquals(puzzle.solution, Solver.solve(puzzle.toBoard(), constraints)!!.cells)
    }
}
