package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.grids.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GridSudokuTest {
    @Test fun `standard grids have the squarest boxes`() {
        assertEquals(listOf(4 to 4, 2 to 8, 8 to 2), GridRules.boxShapes(16))
        assertEquals(listOf(2 to 3, 3 to 2), GridRules.boxShapes(6))
        assertTrue(GridRules.boxShapes(7).isEmpty())
        val r = GridRules.standard(16)
        assertEquals((0 until 16).map { g -> 16 }, (0 until 16).map { g -> r.regions.count { it == g } })
    }

    @Test fun `solver finds the one answer of a known 9x9 puzzle`() {
        val puzzle = "530070000600195000098000060800060003400803001700020006060000280000419005000080079".map { it.digitToInt() }.toIntArray()
        val result = GridSolver.solve(GridRules.standard(9), puzzle, limit = 2)
        assertTrue(result.complete)
        assertEquals(1, result.count)
        assertEquals("534678912672195348198342567859761423426853791713924856961537284287419635345286179", result.solutions[0].joinToString(""))
        assertEquals(2, GridSolver.solve(GridRules.standard(9), IntArray(81), limit = 2).count)
    }

    @Test fun `16x16 puzzles generate quickly with one solution`() {
        for (level in 0..3) {
            val start = System.nanoTime()
            val g = GridGame.generate(level + 1L, 16, level, GridStyle.CLASSIC)
            val ms = (System.nanoTime() - start) / 1e6
            assertTrue(ms < 8000, "level $level took $ms ms")
            val check = GridSolver.solve(g.rules, g.givens.toIntArray(), limit = 2, maxNodes = 2_000_000)
            assertEquals(1, check.count)
            assertEquals(g.solution, check.solutions[0].toList())
        }
        val easy = GridGame.generate(9, 16, 0, GridStyle.CLASSIC).givens.count { it != 0 }
        val expert = GridGame.generate(9, 16, 3, GridStyle.CLASSIC).givens.count { it != 0 }
        assertTrue(easy > expert, "$easy givens on Easy vs $expert on Expert")
    }

    @Test fun `diagonal, jigsaw and killer styles work at several sizes`() {
        for (size in listOf(6, 9, 12)) for (style in GridStyle.entries) {
            val g = GridGame.generate(size * 7L + style.ordinal, size, 1, style)
            assertEquals(1, GridSolver.solve(g.rules, g.givens.toIntArray(), limit = 2, maxNodes = 2_000_000).count, "$size $style")
            if (style == GridStyle.DIAGONAL) assertTrue(GridRule.DIAGONALS in g.rules.rules)
            if (style == GridStyle.KILLER) assertEquals(size * size, g.rules.cages.sumOf { it.cells.size })
        }
    }

    @Test fun `easy and medium puzzles are solvable by deduction alone`() {
        for (size in listOf(6, 9, 16)) for (style in listOf(GridStyle.CLASSIC, GridStyle.DIAGONAL, GridStyle.JIGSAW)) for (level in 0..1) {
            val g = GridGame.generate(size * 31L + level, size, level, style)
            assertEquals(g.solution, GridSolver.solveByLogic(g.rules, g.givens.toIntArray())?.toList(), "$size $style level $level")
        }
        val killer = GridGame.generate(3, 9, 0, GridStyle.KILLER)
        assertNotNull(GridSolver.solveByLogic(killer.rules, killer.givens.toIntArray()))
        assertNull(GridSolver.solveByLogic(GridRules.standard(9), IntArray(81)), "an empty grid needs guessing")
    }

    @Test fun `jigsaw regions keep their size and stay connected, even for prime sizes`() {
        for (n in 4..16) {
            val map = GridGenerator.jigsawRegions(n, Random(n))
            for (g in 0 until n) {
                val cells = map.indices.filter { map[it] == g }
                assertEquals(n, cells.size)
                assertTrue(GridValidator.connected(cells, n), "region $g of $n")
            }
        }
        assertNotNull(GridRules(7, GridGenerator.jigsawRegions(7, Random(3))))
    }

    @Test fun `extra rules are respected by solutions`() {
        val random = Random(4)
        for (rule in GridRule.entries) {
            val rules = GridRules(9, GridRules.standard(9).regions, rules = setOf(rule))
            val sol = GridGenerator.solution(rules, random, maxNodes = 2_000_000)
            assertNotNull(sol, "$rule has a solution")
            assertTrue(rules.solved(sol!!), "$rule")
        }
        val grid = IntArray(81); grid[0] = 5; grid[1] = 6
        assertEquals(setOf(0, 1), GridRules(9, GridRules.standard(9).regions, rules = setOf(GridRule.NON_CONSECUTIVE)).conflicts(grid))
    }

    @Test fun `validator explains broken layouts`() {
        val regions = GridRules.standard(4).regions.toMutableList().also { it[0] = 1 }
        val issues = GridValidator.layoutProblems(4, regions.toIntArray(), listOf(listOf(0, 1, 2, 3, 4)), listOf(SumCage(listOf(0, 1), 30)))
        assertTrue(issues.any { it.error && "Region 1 has 3" in it.message })
        assertTrue(issues.any { it.error && "Extra group 1" in it.message })
        assertTrue(issues.any { it.error && "can't add up to 30" in it.message })
        val split = GridRules.standard(4).regions.toMutableList().also { it[0] = 1; it[2] = 0 }
        assertTrue(GridValidator.layoutProblems(4, split.toIntArray(), emptyList(), emptyList()).any { !it.error && "separate pieces" in it.message })
    }

    @Test fun `draft checks tell apart no, one and many solutions`() {
        var d = GridDraft.blank(4)
        assertEquals(DraftVerdict.MULTIPLE, d.check().verdict)
        assertTrue(d.check().ambiguous.isNotEmpty())
        d = d.setGiven(0, 1).setGiven(1, 1)
        assertEquals(DraftVerdict.CLASHES, d.check().verdict)
        assertEquals(DraftVerdict.BROKEN, GridDraft.blank(4).paintRegion(listOf(0), 1).check().verdict)
        val made = GridDraft.blank(9).autoGivens(Random(1), 0.4)!!
        assertEquals(DraftVerdict.UNIQUE, made.check().verdict)
        val fixed = GridDraft.blank(6).setGiven(0, 1).fixUniqueness(Random(2))!!
        assertEquals(DraftVerdict.UNIQUE, fixed.check().verdict)
        assertEquals(1, fixed.givens[0])
        val impossible = GridDraft.blank(4).setGiven(0, 1).setGiven(5, 2).setGiven(10, 1).setGiven(15, 2).setGiven(6, 3).setGiven(9, 3)
        assertTrue(impossible.check().verdict in setOf(DraftVerdict.NO_SOLUTION, DraftVerdict.CLASHES, DraftVerdict.UNIQUE, DraftVerdict.MULTIPLE))
    }

    @Test fun `odd-even marks and cages narrow the answer`() {
        val d = GridDraft.blank(4).setParity(0, Parity.EVEN).addCage(listOf(0, 1), 7)
        val rules = d.toRules()!!
        val sol = GridGenerator.solution(rules, Random(5))!!
        assertEquals(0, sol[0] % 2)
        assertEquals(7, sol[0] + sol[1])
    }

    @Test fun `playing enters digits, clears notes and hints`() {
        var g = GridGame.generate(11, 9, 1, GridStyle.CLASSIC)
        val empty = g.givens.indexOfFirst { it == 0 }
        val peer = g.rules.peers[empty].first { g.givens[it] == 0 }
        g = g.toggleNote(peer, g.solution[empty])
        g = g.enter(empty, g.solution[empty])
        assertEquals(0, g.notes[peer] and (1 shl (g.solution[empty] - 1)), "placing clears the note")
        assertSame(g, g.enter(g.givens.indexOfFirst { it != 0 }, 1), "givens are locked")
        while (!g.complete) g = g.hint(null)!!
        assertNull(g.hint(null))
    }

    @Test fun `codecs round-trip games, layouts and drafts`() {
        val g = GridGame.generate(12, 12, 2, GridStyle.KILLER).enter(0, 1)
        assertEquals(g, GridCodec.decode(GridCodec.encode(g)))
        val rules = GridDraft.blank(9).withWindows().toggleRule(GridRule.ANTI_KING).setParity(3, Parity.ODD).toRules()!!
        assertEquals(rules, GridCodec.decodeRules(GridCodec.encodeRules(rules)))
        val code = GridCodec.encodePuzzle(rules, List(81) { 0 }, "My grid")
        assertEquals("My grid", GridCodec.decodePuzzle(code)!!.third)
        assertNull(GridCodec.decodeRules("SGG1|9|nope"))
        val d = GridDraft.blank(7).copy(name = "Seven").addCage(listOf(0, 1), 5).addExtra(listOf(10, 11, 12))
        assertEquals(d, GridDraftCodec.decode(GridDraftCodec.encode(d)))
    }
}
