package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.grids.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Unusual grids built the way the editor builds them. Each must pass the same gate as the
 * editor: a sound layout, starting digits filled in, a proof of exactly one answer, and a
 * playable game made from it. Broken versions must be refused.
 */
class WeirdGridsTest {
    /** Parses a picture of regions: one letter per square, 'a' = region 0. */
    private fun regions(vararg rows: String): List<Int> = rows.joinToString("").map { it - 'a' }

    /** The editor's accept path: check the layout, fill in digits, prove one answer, make a game. */
    private fun accept(draft: GridDraft, seed: Long, share: Double = 0.4): GridGame {
        assertNotNull(draft.toRules(), "layout accepted: ${draft.check().issues}")
        val filled = draft.autoGivens(Random(seed), share)
        assertNotNull(filled, "${draft.name}: digits can be filled in")
        val check = filled!!.check(maxNodes = 3_000_000)
        assertEquals(DraftVerdict.UNIQUE, check.verdict, "${draft.name}: exactly one answer")
        val game = GridGame.fromDraft(filled.toRules()!!, filled.givens.toIntArray(), draft.name, 1, seed)
        assertNotNull(game, "${draft.name}: playable")
        assertTrue(game!!.rules.solved(game.solution.toIntArray()))
        // Round trip through a share code and back into the editor.
        val code = GridCodec.encodePuzzle(game.rules, game.givens, game.name)
        val (rules, givens, name) = GridCodec.decodePuzzle(code)!!
        assertEquals(DraftVerdict.UNIQUE, GridDraft(rules.size, rules.regions, givens.toList(), rules.rules, rules.extras, rules.cages, rules.parity, name).check().verdict)
        return game
    }

    @Test fun `5x5 with drawn regions (no box shape exists)`() {
        val d = GridDraft(5, regions("aabbb", "aabcb", "adccc", "ddcee", "ddeee"), name = "Five")
        accept(d, 1)
    }

    @Test fun `7x7 spiral-ish jigsaw, and the same shape with anti-knight has no answer`() {
        val shape = regions("aaaabbb", "cccabbb", "cdaaeeb", "cddeeef", "cddgeff", "cdggeff", "dggggff")
        val d = GridDraft(7, shape, name = "Seven")
        assertTrue(d.check().issues.none { it.error }, d.check().issues.toString())
        accept(d, 2)
        // Adding anti-knight makes this shape impossible; the editor must say so and refuse to fill it.
        val knights = d.toggleRule(GridRule.ANTI_KNIGHT)
        assertEquals(DraftVerdict.NO_SOLUTION, knights.check().verdict)
        assertNull(knights.autoGivens(Random(2), 0.4))
    }

    @Test fun `broken-diagonal regions (each region is scattered across the grid)`() {
        val n = 7
        val d = GridDraft(n, List(n * n) { i -> (i / n + i % n) % n }, name = "Stripes")
        val check = d.check()
        assertTrue(check.issues.all { !it.error }, "scattered regions are allowed")
        assertTrue(check.issues.any { "separate pieces" in it.message }, "but flagged as unusual")
        accept(d, 3)
    }

    @Test fun `8x8 with 2x4 boxes, diagonals and extra groups`() {
        val d = GridDraft.blank(8).withBoxes(2, 4).toggleRule(GridRule.DIAGONALS)
            .addExtra(listOf(9, 10, 17, 18)).addExtra(listOf(45, 46, 53, 54)).copy(name = "Eight")
        accept(d, 4)
    }

    @Test fun `Windoku 9x9 with odd and even squares`() {
        var d = GridDraft.blank(9).withWindows().copy(name = "Windoku")
        listOf(0, 10, 20, 30).forEach { d = d.setParity(it, Parity.ODD) }
        listOf(80, 70, 60).forEach { d = d.setParity(it, Parity.EVEN) }
        val game = accept(d, 5)
        assertTrue(game.solution[0] % 2 == 1 && game.solution[80] % 2 == 0)
        assertEquals(4, game.rules.extras.size)
    }

    @Test fun `non-consecutive 9x9 needs very few digits`() {
        val d = GridDraft.blank(9).toggleRule(GridRule.NON_CONSECUTIVE).copy(name = "Apart")
        val game = accept(d, 6, share = 0.2)
        assertTrue((0 until 81).all { i -> game.rules.sides[i].all { kotlin.math.abs(game.solution[it] - game.solution[i]) != 1 } })
    }

    @Test fun `killer 6x6 made only of cages`() {
        val base = GridGame.generate(8, 6, 0, GridStyle.CLASSIC)
        var d = GridDraft.blank(6).copy(name = "Cages")
        GridGenerator.cages(6, base.solution.toIntArray(), Random(8), maxSize = 3).forEach { d = d.addCage(it.cells, it.sum) }
        val made = d.fixUniqueness(Random(9))!!
        assertEquals(DraftVerdict.UNIQUE, made.check().verdict)
        assertNotNull(GridGame.fromDraft(made.toRules()!!, made.givens.toIntArray(), "Cages", 1, 1))
    }

    @Test fun `12x12 random jigsaw with both diagonals`() {
        val d = GridDraft.blank(12).withJigsaw(Random(10)).toggleRule(GridRule.DIAGONALS).copy(name = "Twelve")
        accept(d, 11, share = 0.5)
    }

    @Test fun `anti-king 9x9 with boxes`() {
        accept(GridDraft.blank(9).toggleRule(GridRule.ANTI_KING).copy(name = "Kings"), 14, share = 0.3)
    }

    @Test fun `16x16 jigsaw from the editor`() {
        accept(GridDraft.blank(16).withJigsaw(Random(12)).copy(name = "Big"), 13, share = 0.5)
    }

    @Test fun `broken and unsolvable grids are refused`() {
        // A region with too many squares.
        val lopsided = GridDraft(4, regions("aaab", "abbb", "cccd", "cddd").let { it.toMutableList().also { m -> m[3] = 0 } })
        assertEquals(DraftVerdict.BROKEN, lopsided.check().verdict)
        assertNull(lopsided.toRules())
        // A cage that can't reach its total.
        assertEquals(DraftVerdict.BROKEN, GridDraft.blank(4).addCage(listOf(0, 1), 9).check().verdict)
        // Clashing starting digits.
        assertEquals(DraftVerdict.CLASHES, GridDraft.blank(9).setGiven(0, 5).setGiven(8, 5).check().verdict)
        // Legal digits that leave no answer: 1–3 in a row force the last square to 4, but its column already has a 4.
        val stuck = GridDraft.blank(4).setGiven(0, 1).setGiven(1, 2).setGiven(2, 3).setGiven(7, 4)
        assertEquals(DraftVerdict.NO_SOLUTION, stuck.check().verdict)
        assertNull(stuck.autoGivens(Random(1), 0.5))
        assertNull(stuck.fixUniqueness(Random(1)))
        assertNull(GridGame.fromDraft(stuck.toRules()!!, stuck.givens.toIntArray(), "", 1, 1))
        // Anti-king with non-consecutive on 4×4 has no answer at all.
        val impossible = GridDraft.blank(4).toggleRule(GridRule.ANTI_KING).toggleRule(GridRule.NON_CONSECUTIVE)
        assertEquals(DraftVerdict.NO_SOLUTION, impossible.check().verdict)
        // Several answers can't be played.
        val open = GridDraft.blank(9)
        assertEquals(DraftVerdict.MULTIPLE, open.check().verdict)
        assertNull(GridGame.fromDraft(open.toRules()!!, open.givens.toIntArray(), "", 1, 1))
    }
}
