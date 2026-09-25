package com.simplegamegen.sudoku.constraints

import kotlin.random.Random

/**
 * Jigsaw region layouts. Every map assigns each cell a region id in
 * 0 until size with exactly [size] contiguous cells per region
 * (enforced by [RegionConstraint]).
 */
object JigsawMaps {

    /** Classic 3x3 boxes expressed as a region map (baseline / fallback). */
    fun standardBoxMap(size: Int, boxRows: Int, boxCols: Int): IntArray {
        val boxesPerRow = size / boxCols
        return IntArray(size * size) { i ->
            val r = i / size
            val c = i % size
            (r / boxRows) * boxesPerRow + (c / boxCols)
        }
    }

    /**
     * Random contiguous layout via seeded multi-source growth: one seed per
     * region (spread across row-major chunks), then repeatedly attach a
     * random unassigned frontier cell to a neighbouring region with space.
     * Retried with fresh randomness; falls back to boxes after [maxAttempts].
     */
    fun randomMap(size: Int, rng: Random = Random.Default, maxAttempts: Int = 200): IntArray {
        repeat(maxAttempts) {
            tryGrow(size, Random(rng.nextLong()))?.let { return it }
        }
        // Practically unreachable, but guarantees a valid layout.
        val (br, bc) = boxDimsFor(size)
        return standardBoxMap(size, br, bc)
    }

    private fun boxDimsFor(size: Int): Pair<Int, Int> = when (size) {
        9 -> 3 to 3
        6 -> 2 to 3
        4 -> 2 to 2
        else -> throw IllegalArgumentException("Unsupported Jigsaw size: $size")
    }

    private fun tryGrow(size: Int, rng: Random): IntArray? {
        val total = size * size
        val regions = IntArray(total) { -1 }
        val counts = IntArray(size)

        // Spread seeds: one random cell per row-major chunk of `size` cells.
        for (id in 0 until size) {
            val chunk = IntArray(size) { k -> id * size + k }
            val seed = chunk.random(rng)
            if (regions[seed] != -1) return null // collision — retry
            regions[seed] = id
            counts[id] = 1
        }

        var assigned = size
        while (assigned < total) {
            val options = mutableListOf<Pair<Int, Int>>() // (cell, region)
            for (cell in 0 until total) {
                if (regions[cell] != -1) continue
                val r = cell / size
                val c = cell % size
                val neighbourRegions = linkedSetOf<Int>()
                if (r > 0) neighbourRegions.add(regions[cell - size])
                if (r < size - 1) neighbourRegions.add(regions[cell + size])
                if (c > 0) neighbourRegions.add(regions[cell - 1])
                if (c < size - 1) neighbourRegions.add(regions[cell + 1])
                neighbourRegions.remove(-1)
                for (id in neighbourRegions) {
                    if (counts[id] < size) options.add(cell to id)
                }
            }
            if (options.isEmpty()) return null // enclosed hole — retry
            val (cell, id) = options.random(rng)
            regions[cell] = id
            counts[id]++
            assigned++
        }
        return regions
    }
}
