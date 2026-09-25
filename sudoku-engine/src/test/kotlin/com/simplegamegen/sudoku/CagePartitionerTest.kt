package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.CagePartitioner
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.Board
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class CagePartitionerTest {

    private fun assertContiguous(size: Int, cells: IntArray) {
        val set = cells.toSet()
        val seen = mutableSetOf(cells.first())
        val queue = ArrayDeque(seen)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val r = cur / size
            val c = cur % size
            listOf(
                if (r > 0) cur - size else -1,
                if (r < size - 1) cur + size else -1,
                if (c > 0) cur - 1 else -1,
                if (c < size - 1) cur + 1 else -1,
            ).filter { it in set && seen.add(it) }.forEach { queue.add(it) }
        }
        assertEquals(set.size, seen.size, "cage $cells is not contiguous")
    }

    @Test
    fun `partition covers grid with valid cages`() {
        val solution = Generator.generateFullSolution(9, 3, 3, Random(3L))
        val rng = Random(11L)
        repeat(5) {
            val cages = CagePartitioner.partition(solution, rng, maxCageSize = 4, maxSingles = 1)
            val covered = cages.flatMap { it.cells.toList() }.sorted()
            assertEquals((0 until 81).toList(), covered)
            cages.forEach { cage ->
                assertTrue(cage.cells.size in 1..4, "cage size ${cage.cells.size}")
                assertEquals(cage.sum, cage.cells.sumOf { solution.cells[it] })
                // No repeated solution digit inside a cage.
                val digits = cage.cells.map { solution.cells[it] }
                assertEquals(digits.size, digits.toSet().size)
                assertContiguous(9, cage.cells)
            }
            assertTrue(cages.count { it.cells.size == 1 } <= 1)
        }
    }

    @Test
    fun `partition works on 6x6`() {
        val solution = Generator.generateFullSolution(6, 2, 3, Random(8L))
        val cages = CagePartitioner.partition(solution, Random(9L), maxCageSize = 4, maxSingles = 2)
        assertEquals((0 until 36).toList(), cages.flatMap { it.cells.toList() }.sorted())
    }
}
