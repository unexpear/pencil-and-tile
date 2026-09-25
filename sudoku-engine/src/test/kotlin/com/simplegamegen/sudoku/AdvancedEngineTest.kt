package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.*
import com.simplegamegen.sudoku.solver.*
import com.simplegamegen.sudoku.validation.*
import com.simplegamegen.sudoku.words.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdvancedEngineTest {
    private val rules = Constraints.classic(4, 2, 2)

    @Test fun `search separates multiple unique impossible and unfinished proofs without mutating input`() {
        val empty = Board.empty(4)
        assertEquals(SolutionVerdict.INCOMPLETE, Solver.analyze(empty, rules, 0).verdict)
        assertEquals(SolutionVerdict.MULTIPLE, Solver.analyze(empty, rules).verdict)
        assertTrue(empty.cells.all { it == 0 })
        val puzzle = Generator.generateClassic(4, Difficulty.EASY, 4)
        val before = puzzle.givens.copyOf()
        val report = Solver.analyze(puzzle.toBoard(), rules)
        assertEquals(SolutionVerdict.UNIQUE, report.verdict)
        assertTrue(report.exhausted)
        assertTrue(report.forcedPlacements > 0)
        assertArrayEquals(before, puzzle.givens)
        val bad = Board.empty(4).also { it.cells[0] = 1; it.cells[1] = 1 }
        assertEquals(SolutionVerdict.UNSOLVABLE, Solver.analyze(bad, rules).verdict)
        bad.cells[0] = 99
        assertNull(Solver.solve(bad, rules))
        assertFalse(Validator.isCompleteAndValid(bad, rules))
        assertFalse(Validator.isValidPlacement(empty, -1, 1, rules))
        assertThrows(IllegalArgumentException::class.java) { Solver.countSolutions(empty, rules, 0) }
    }

    @Test fun `independent verifier proves all sudoku variants and rejects altered answers`() {
        for (variant in VariantType.entries) {
            val p = Generator.generateVerified(variant, if (variant == VariantType.KILLER) 9 else 4, Difficulty.EASY, 27, 1)
            assertTrue(PuzzleVerifier.verify(p).valid, variant.name)
            assertFalse(PuzzleVerifier.verify(p, 0).valid)
            val broken = p.solution.copyOf().also { it[0] = 0 }
            assertFalse(PuzzleVerifier.verify(p.copy(solution = broken)).valid)
        }
    }

    @Test fun `thread interruption aborts solver work`() {
        Thread.currentThread().interrupt()
        try { assertThrows(java.util.concurrent.CancellationException::class.java) { Solver.solve(Board.empty(4), rules) } }
        finally { Thread.interrupted() }
    }

    @Test fun `word verifier detects disconnected grids and unclued runs`() {
        val disconnected = WordPuzzle(WordGame.CROSSWORD, 4, "Bad", "CAT#########DOG#",
            listOf(WordEntry("CAT", "Feline", listOf(0, 1, 2)), WordEntry("DOG", "Canine", listOf(12, 13, 14))))
        assertFalse(WordVerifier.verify(disconnected))
        assertNull(WordSaveCodec.decode(WordSaveCodec.encode(WordProgress(disconnected))))
        for (level in WordDifficulty.entries) {
            assertTrue(WordVerifier.verify(WordPuzzles.crossword(73, level)))
            val search = WordPuzzles.wordSearch(73, 0, level)
            assertTrue(WordVerifier.verify(search))
            search.entries.forEach { assertTrue(it.cells in WordVerifier.occurrences(search, it.answer)) }
        }
    }
}
