package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.grids.*
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.wordplay.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PuzzleGridsTest {
    @Test fun `line solver narrows single lines`() {
        assertArrayEquals(intArrayOf(-1, 1, 1, -1), Nonogram.solveLine(listOf(3), IntArray(4) { -1 }))
        assertArrayEquals(intArrayOf(1, 0, 1), Nonogram.solveLine(listOf(1, 1), IntArray(3) { -1 }))
        assertArrayEquals(intArrayOf(0, 0, 0), Nonogram.solveLine(emptyList(), IntArray(3) { -1 }))
        assertArrayEquals(intArrayOf(0, 0, 1, 1), Nonogram.solveLine(listOf(2), intArrayOf(-1, -1, -1, 1)))
        assertNull(Nonogram.solveLine(listOf(2, 2), IntArray(4) { -1 }))
        assertNull(Nonogram.solveLine(listOf(1), intArrayOf(1, -1, 1)))
    }

    @Test fun `nonograms are line solvable, deterministic and playable`() {
        for (setting in Nonogram.SIZES.indices) for (seed in 1L..8L) {
            val g = Nonogram.generate(seed, setting)
            assertEquals(g, Nonogram.generate(seed, setting))
            assertEquals(Nonogram.SIZES[setting], g.width)
            assertTrue(Nonogram.lineSolvable(g.width, g.height, g.rowClues, g.colClues))
        }
        var g = Nonogram.generate(4, 1)
        val wrong = g.solution.indices.first { !g.solution[it] }
        g = g.paint(listOf(wrong), Mark.FILLED)
        assertEquals(setOf(wrong), g.mistakes())
        g = g.paint(listOf(wrong), Mark.CROSSED)
        assertTrue(g.mistakes().isEmpty())
        assertEquals(g, NonogramCodec.decode(NonogramCodec.encode(g)))
        g = g.paint(g.solution.indices.filter { g.solution[it] }, Mark.FILLED)
        assertTrue(g.complete)
        assertSame(g, g.paint(listOf(0), Mark.CROSSED))
        assertNull(NonogramCodec.decode(NonogramCodec.encode(g).replaceFirst("1\n", "2\n")))
    }

    @Test fun `nonogram hints converge`() {
        var g = Nonogram.generate(2, 0)
        var steps = 0
        while (!g.complete) { g = g.hint()!!; steps++ }
        assertEquals(steps, g.hints)
        assertNull(g.hint())
    }

    @Test fun `hitori puzzles have exactly one valid shading`() {
        for (setting in HitoriGame.SIZES.indices) for (seed in 1L..6L) {
            val g = HitoriGame.generate(seed, setting)
            assertEquals(g, HitoriGame.generate(seed, setting))
            val n = g.size
            assertTrue(HitoriSolver.valid(n, g.numbers, g.shaded))
            assertEquals(1, HitoriSolver.count(n, g.numbers, 2))
            // Every shaded number repeats in its row or column.
            g.shaded.indices.filter { g.shaded[it] }.forEach { i ->
                val r = i / n; val c = i % n
                assertTrue((0 until n).any { it != c && g.numbers[r * n + it] == g.numbers[i] } || (0 until n).any { it != r && g.numbers[it * n + c] == g.numbers[i] })
            }
        }
        // Extra shading counts too: a Latin square has many answers, a repeated pair two.
        assertTrue(HitoriSolver.count(3, listOf(1, 2, 3, 2, 3, 1, 3, 1, 2), 5) > 1)
        assertEquals(2, HitoriSolver.count(2, listOf(1, 1, 2, 3), 5).coerceAtMost(2))
    }

    @Test fun `hitori marks, conflicts, hints and saves`() {
        var g = HitoriGame.generate(7, 1)
        val n = g.size
        val black = g.shaded.indexOf(true)
        g = g.cycle(black)
        assertTrue(g.mistakes().isEmpty())
        val neighbor = HitoriSolver.neighbors(black, n).first()
        g = g.set(neighbor, Shade.SHADED)
        assertTrue(neighbor in g.conflicts() && black in g.conflicts())
        assertTrue(neighbor in g.mistakes())
        assertEquals(g, HitoriCodec.decode(HitoriCodec.encode(g)))
        g = g.set(neighbor, Shade.NONE)
        var steps = 0
        while (!g.complete) { g = g.hint()!!; steps++ }
        assertEquals(steps, g.hints)
        assertSame(g, g.cycle(0))
        assertNull(HitoriCodec.decode(HitoriCodec.encode(g).replaceFirst("\n1\n", "\n7\n")))
    }

    @Test fun `code crackers are valid crossword grids with a full number code`() {
        for (level in LogicLevel.entries) for (seed in 1L..6L) {
            val g = CodeCracker.generate(seed, level)
            assertEquals(g, CodeCracker.generate(seed, level))
            assertEquals(CodeCracker.sizeOf(level), g.size)
            val runs = CodeCracker.runs(g.grid, g.size)
            assertTrue(runs.size >= 6, "enough words")
            val words = runs.map { it.second }.toSet()
            assertTrue(CodeCracker.runsAreWords(g.grid, g.size, words))
            assertEquals(CodeCracker.reveals(level).coerceAtMost(g.count), g.given.size)
            // Letters connect into one grid.
            val letters = g.grid.indices.filter { g.grid[it] != '#' }.toSet()
            val seen = mutableSetOf(letters.first()); val stack = ArrayDeque(listOf(letters.first()))
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                listOf(c - g.size, c + g.size, c - 1, c + 1).filter { it in letters && it !in seen && (it / g.size == c / g.size || it % g.size == c % g.size) }
                    .forEach { seen += it; stack += it }
            }
            assertEquals(letters, seen)
        }
    }

    @Test fun `code cracker guesses, locks givens and completes`() {
        var g = CodeCracker.generate(3, LogicLevel.MEDIUM)
        val given = g.given.first()
        assertSame(g, g.guess(given, 'Q'))
        val open = (1..g.count).filter { it !in g.given }
        val a = open[0]; val b = open[1]
        g = g.guess(a, g.letterOf(b))
        assertEquals(setOf(a), g.mistakes())
        g = g.guess(b, g.letterOf(b))
        assertNull(g.guessOf(a))
        assertEquals(g, CodeCrackerCodec.decode(CodeCrackerCodec.encode(g)))
        open.forEach { g = g.guess(it, g.letterOf(it)) }
        assertTrue(g.complete)
        var h = CodeCracker.generate(9, LogicLevel.EXPERT)
        while (!h.complete) h = h.hint(null)!!
        assertNull(h.hint(null))
    }

    @Test fun `dropquotes wrap words and only accept each column's letters`() {
        assertEquals(listOf("AB#CD", "EFG"), Dropquote.wrap("ab cd, efg!", 5))
        assertNull(Dropquote.wrap("abcdef", 5))
        for (level in LogicLevel.entries) for (seed in 1L..10L) {
            val g = Dropquote.generate(seed, level)
            assertEquals(g, Dropquote.generate(seed, level))
            assertTrue(g.width <= Dropquote.widthOf(level))
            assertEquals(g.quote.uppercase().filter { it in 'A'..'Z' }, g.cells.filter { it != '#' })
            assertTrue((0 until g.width).all { g.pool(it).isNotEmpty() })
        }
        var g = Dropquote.generate(5, LogicLevel.MEDIUM)
        val cell = g.cells.indexOfFirst { it != '#' }
        val foreign = ('A'..'Z').first { it !in g.pool(cell % g.width) }
        assertSame(g, g.place(cell, foreign))
        g = g.place(cell, g.cells[cell])
        assertEquals(g.cells[cell], g.typed[cell])
        assertEquals(g, DropquoteCodec.decode(DropquoteCodec.encode(g)))
        g = g.place(cell, null)
        assertEquals('_', g.typed[cell])
        g.cells.indices.filter { g.cells[it] != '#' }.forEach { g = g.place(it, g.cells[it]) }
        assertTrue(g.complete)
        assertNull(DropquoteCodec.decode(DropquoteCodec.encode(g).replace("\n${g.width}\n", "\n99\n")))
    }

    @Test fun `dropquote hints free wrongly used letters`() {
        var g = Dropquote.generate(11, LogicLevel.HARD)
        // Put a column's letters into the wrong squares, then hint.
        val col = (0 until g.width).first { c -> g.column(c).count { g.cells[it] != '#' } >= 2 && g.column(c).map { g.cells[it] }.filter { it != '#' }.toSet().size >= 2 }
        val squares = g.column(col).filter { g.cells[it] != '#' }
        g = g.place(squares[0], g.cells[squares[1]]).place(squares[1], g.cells[squares[0]])
        var steps = 0
        while (!g.complete) { g = g.hint(null)!!; steps++; assertTrue(steps < 400) }
        assertEquals(steps, g.hints)
    }
}
