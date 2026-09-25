package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.mahjong.*
import com.simplegamegen.sudoku.words.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ArcadeEngineTest {
    @Test fun `every mahjong difficulty generates deterministic legally clearable deals`() {
        for (level in WordDifficulty.entries) repeat(30) { seed ->
            val deal = MahjongGenerator.generate(seed.toLong(), level)
            assertEquals(listOf(24, 48, 72, 96)[level.ordinal], deal.tiles.size)
            assertEquals(deal, MahjongGenerator.generate(seed.toLong(), level))
            assertEquals(deal.tiles.size, MahjongRules.replay(deal.tiles, deal.solution)!!.size)
            val start = MahjongProgress(deal)
            assertEquals(MahjongVerdict.SOLVED, MahjongSolver.solve(start).verdict)
            val complete = MahjongProgress(deal, deal.solution)
            assertTrue(complete.complete)
            assertFalse(complete.undo().complete)
            assertEquals(complete, MahjongSaveCodec.decode(MahjongSaveCodec.encode(complete)))
        }
    }

    // The four A tiles allow a legal cross-match that can strand an A under its only partner.
    private fun branchingDeal() = MahjongDeal(listOf(
        MahjongTile(0, 0, 0, 0, 0), MahjongTile(1, 2, 0, 0, 1),
        MahjongTile(2, 4, 0, 0, 1), MahjongTile(3, 6, 0, 0, 0),
        MahjongTile(4, 0, 0, 1, 0), MahjongTile(5, 2, 0, 1, 0),
    ), listOf(TilePair(4, 5), TilePair(0, 3), TilePair(1, 2)), WordDifficulty.EASY, 1)

    @Test fun `mahjong checks coverage open sides matching and duplicate removal`() {
        val p = MahjongProgress(branchingDeal())
        assertFalse(MahjongRules.free(p.deal.tiles, emptySet(), 0))
        assertTrue(MahjongRules.free(p.deal.tiles, emptySet(), 3))
        assertEquals(p, p.play(TilePair(0, 3)))
        assertEquals(p, p.play(TilePair(4, 4)))
        assertEquals(p, p.play(TilePair(-1, 5)))
        val next = p.play(TilePair(4, 5))
        assertEquals(next, next.play(TilePair(4, 5)))
        assertEquals(p, next.undo())
    }

    @Test fun `mahjong solver proves dead ends finds alternative routes and reports budget limits honestly`() {
        val p = MahjongProgress(branchingDeal())
        val trapped = p.play(TilePair(5, 3))
        assertEquals(MahjongVerdict.UNSOLVABLE, MahjongSolver.solve(trapped).verdict)
        assertEquals(MahjongVerdict.LIMIT_REACHED, MahjongSolver.solve(trapped, 0).verdict)
        val alternative = p.play(TilePair(4, 3))
        val result = MahjongSolver.solve(alternative)
        assertEquals(MahjongVerdict.SOLVED, result.verdict)
        assertEquals(6, MahjongRules.replay(p.deal.tiles, result.path, alternative.removed)!!.size)
        assertTrue(result.nodes > 0)
        assertEquals(MahjongVerdict.SOLVED, MahjongSolver.solve(trapped.undo()).verdict)
    }

    @Test fun `mahjong corrupted witnesses and move histories are rejected`() {
        val deal = branchingDeal()
        assertThrows(IllegalArgumentException::class.java) { deal.copy(solution = deal.solution.reversed()) }
        assertThrows(IllegalArgumentException::class.java) { MahjongProgress(deal, listOf(TilePair(0, 3))) }
        val saved = MahjongSaveCodec.encode(MahjongProgress(deal))
        assertNull(MahjongSaveCodec.decode(saved.replace("4,5;0,3;1,2", "0,3;4,5;1,2")))
        assertNull(MahjongSaveCodec.decode("bad"))
    }

    @Test fun `hangman works at every level and theme with safe completion and save restore`() {
        for (level in WordDifficulty.entries) for (theme in WordPuzzles.themes.indices) repeat(10) { seed ->
            val puzzle = Hangman.generate(seed.toLong(), theme, level)
            assertEquals(puzzle, Hangman.generate(seed.toLong(), theme, level))
            assertTrue(Hangman.words(theme, level).size > 1)
            var p = HangmanProgress(puzzle)
            puzzle.answer.forEach { p = p.guess(it.lowercaseChar()) }
            assertTrue(p.won)
            assertEquals(p, p.guess('Z'))
            assertEquals(p, HangmanSaveCodec.decode(HangmanSaveCodec.encode(p)))
            val next = Hangman.generate(seed.toLong(), theme, level, setOf(puzzle.answer))
            assertNotEquals(puzzle.answer, next.answer)
        }
    }

    @Test fun `hangman repeated guesses hints and losses count correctly`() {
        val puzzle = Hangman.generate(8, 0, WordDifficulty.EXPERT)
        var p = HangmanProgress(puzzle).hint()
        assertEquals(1, p.hints)
        assertEquals(p, p.guess(p.guesses.first()))
        assertEquals(p, p.guess('!'))
        ('A'..'Z').filter { it !in puzzle.answer }.forEach { p = p.guess(it) }
        assertTrue(p.lost)
        assertEquals(puzzle.mistakeLimit, p.mistakes)
        assertEquals(p, p.hint())
        assertEquals(p, HangmanSaveCodec.decode(HangmanSaveCodec.encode(p)))
        assertThrows(IllegalArgumentException::class.java) { HangmanProgress(puzzle, "AA") }
        assertNull(HangmanSaveCodec.decode("bad"))
    }
}
