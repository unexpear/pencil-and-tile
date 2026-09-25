package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.logic.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LogicPuzzlesTest {
    private fun timed(label: String, block: () -> LogicPuzzle): LogicPuzzle {
        val start = System.nanoTime()
        return block().also { println("$label generated in ${(System.nanoTime() - start) / 1_000_000} ms") }
    }

    @Test fun `every kind and level generates a verified unique puzzle`() {
        for (kind in LogicKind.entries) for (level in LogicLevel.entries) for (seed in 1L..2L) {
            val p = timed("$kind $level #$seed") { LogicGenerator.generate(kind, level, seed) }
            assertEquals(kind, p.kind); assertEquals(level, p.level)
            assertTrue(LogicVerifier.verify(p), "$kind $level $seed not unique")
            assertTrue(LogicSolver.satisfies(p.rules, p.solution.toIntArray()))
            assertEquals(p, LogicGenerator.generate(kind, level, seed), "generation must be deterministic")
        }
    }

    @Test fun `samurai layout has five overlapping grids and difficulty removes more givens`() {
        val rules = LogicRules(LogicKind.SAMURAI, 21, 21, 9, LogicRules.samuraiOpen())
        assertEquals(369, rules.open.count { it })
        assertEquals(5 * 27 - 4, rules.units.size) // the four shared corner boxes are counted once
        val easy = LogicGenerator.samurai(LogicLevel.EASY, 7).givens.count { it != 0 }
        val expert = LogicGenerator.samurai(LogicLevel.EXPERT, 7).givens.count { it != 0 }
        assertTrue(expert < easy, "expert $expert should have fewer givens than easy $easy")
    }

    @Test fun `kenken grows with level, partitions the grid and uses only permitted operations`() {
        for (level in LogicLevel.entries) {
            val p = LogicGenerator.kenken(level, 3)
            val n = 4 + level.ordinal
            assertEquals(n, p.rules.rows)
            assertEquals((0 until n * n).toList(), p.rules.cages.flatMap { it.cells }.sorted())
            assertTrue(p.givens.all { it == 0 })
            if (level == LogicLevel.EASY) assertTrue(p.rules.cages.all { it.op in setOf(CageOp.NONE, CageOp.ADD, CageOp.SUB) })
            p.rules.cages.forEach { assertTrue(LogicSolver.cageValue(it, it.cells.map { c -> p.solution[c] })) }
        }
    }

    @Test fun `kakuro runs are 2 to 9 distinct digits matching their clue`() {
        for (level in LogicLevel.entries) {
            val p = LogicGenerator.kakuro(level, 5)
            assertEquals(6 + 2 * level.ordinal, p.rules.rows)
            p.rules.runs.forEach { run ->
                assertTrue(run.cells.size in 2..9)
                val digits = run.cells.map { p.solution[it] }
                assertEquals(digits.size, digits.toSet().size)
                assertEquals(run.sum, digits.sum())
            }
            assertFalse(p.rules.open[0]); assertTrue((0 until p.rules.cols).none { p.rules.open[it] })
        }
    }

    @Test fun `futoshiki signs are adjacent, hold in the solution and conflict when broken`() {
        for (level in LogicLevel.entries) {
            val p = LogicGenerator.futoshiki(level, 21)
            val n = 4 + level.ordinal
            assertEquals(n, p.rules.rows)
            assertTrue(p.rules.less.isNotEmpty())
            p.rules.less.forEach { (a, b) -> assertTrue(p.solution[a] < p.solution[b]) }
        }
        val p = LogicGenerator.futoshiki(LogicLevel.EXPERT, 5)
        val (a, b) = p.rules.less.first { (x, y) -> p.givens[x] == 0 && p.givens[y] == 0 }
        val broken = LogicProgress(p).enter(a, p.rules.maxDigit).enter(b, 1)
        assertTrue(broken.conflicts().containsAll(listOf(a, b)))
    }

    @Test fun `version 1 saves without a signs line still load`() {
        val p = LogicGenerator.kakuro(LogicLevel.EASY, 3)
        val v2 = LogicSaveCodec.encode(LogicProgress(p))
        val v1 = v2.split('\n').dropLast(1).toMutableList().also { it[0] = "1" }.joinToString("\n")
        assertEquals(LogicProgress(p), LogicSaveCodec.decode(v1))
    }

    @Test fun `solver reports ambiguity, contradictions and budget exhaustion honestly`() {
        val latin = LogicRules(LogicKind.KENKEN, 4, 4, 4, List(16) { true })
        assertEquals(2, LogicSolver.solve(latin, IntArray(16)).count)
        val bad = IntArray(16).also { it[0] = 1; it[1] = 1 }
        val contradiction = LogicSolver.solve(latin, bad)
        assertEquals(0, contradiction.count); assertTrue(contradiction.complete)
        val samurai = LogicRules(LogicKind.SAMURAI, 21, 21, 9, LogicRules.samuraiOpen())
        assertFalse(LogicSolver.solve(samurai, IntArray(441), limit = 2, maxNodes = 5).complete)
    }

    @Test fun `progress entry, notes, hints, conflicts and completion`() {
        val p = LogicGenerator.kenken(LogicLevel.EASY, 11)
        var progress = LogicProgress(p)
        val cell = 0; val right = p.solution[0]; val wrong = right % 4 + 1
        progress = progress.toggleNote(cell, 2).toggleNote(cell, 3)
        assertEquals(setOf(2, 3), progress.notes[cell])
        progress = progress.enter(cell, wrong)
        assertEquals(setOf(cell), progress.mistakes())
        assertTrue(progress.notes[cell].isEmpty())
        progress = progress.enter(1, wrong)
        assertTrue(progress.conflicts().containsAll(listOf(0, 1)), "same digit twice in a row must conflict")
        progress = progress.erase(1)
        val (hinted, at) = progress.hint(cell)!!
        assertEquals(cell, at); assertEquals(right, hinted.entries[cell]); assertEquals(1, hinted.hints)
        var solved = hinted
        while (!solved.complete) solved = solved.hint(null)!!.first
        assertTrue(solved.conflicts().isEmpty())
        assertNull(solved.hint(null))
        assertSame(solved, solved.enter(cell, wrong), "a finished puzzle is locked")
    }

    @Test fun `placing a digit clears it from peer notes but not from other cells`() {
        val p = LogicGenerator.samurai(LogicLevel.EXPERT, 2)
        val empty = p.givens.indices.first { p.rules.open[it] && p.givens[it] == 0 }
        val peer = p.rules.units.first { empty in it }.first { it != empty && p.givens[it] == 0 }
        val far = p.givens.indices.first { i -> p.rules.open[i] && p.givens[i] == 0 && p.rules.units.none { empty in it && i in it } }
        var progress = LogicProgress(p).toggleNote(peer, 5).toggleNote(far, 5)
        progress = progress.enter(empty, 5)
        assertFalse(5 in progress.notes[peer]); assertTrue(5 in progress.notes[far])
    }

    @Test fun `saves round-trip and reject tampering`() {
        for (kind in LogicKind.entries) {
            val p = LogicGenerator.generate(kind, LogicLevel.MEDIUM, 4)
            val cell = p.givens.indices.first { p.rules.open[it] && p.givens[it] == 0 }
            val other = p.givens.indices.last { p.rules.open[it] && p.givens[it] == 0 }
            val progress = LogicProgress(p).enter(cell, p.solution[cell]).toggleNote(other, 1).copy(hints = 2)
            val text = LogicSaveCodec.encode(progress)
            assertEquals(progress, LogicSaveCodec.decode(text))
            val lines = text.split('\n')
            fun with(i: Int, v: String) = lines.toMutableList().also { it[i] = v }.joinToString("\n")
            assertNull(LogicSaveCodec.decode(with(0, "9")))
            assertNull(LogicSaveCodec.decode(with(1, "CHESS")))
            assertNull(LogicSaveCodec.decode(with(9, lines[9].replaceFirst(Regex("[1-9]"), "0"))))
            assertNull(LogicSaveCodec.decode(with(12, lines[12].split(',').toMutableList().also { list ->
                val given = p.givens.indexOfFirst { it != 0 }
                if (given >= 0) list[given] = ((p.givens[given] % p.rules.maxDigit) + 1).toString() else list[0] = "99"
            }.joinToString(","))))
            assertNull(LogicSaveCodec.decode(text.dropLast(3)))
            assertNull(LogicSaveCodec.decode(""))
        }
    }
}
