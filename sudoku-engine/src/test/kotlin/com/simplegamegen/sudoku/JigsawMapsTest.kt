package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.constraints.JigsawMaps
import com.simplegamegen.sudoku.constraints.RegionConstraint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class JigsawMapsTest {

    @Test
    fun `standard box map is a valid region layout`() {
        for (size in listOf(9, 6, 4)) {
            val (br, bc) = when (size) {
                9 -> 3 to 3
                6 -> 2 to 3
                else -> 2 to 2
            }
            val map = JigsawMaps.standardBoxMap(size, br, bc)
            // Constructor validates — must not throw.
            RegionConstraint(size, map)
        }
    }

    @Test
    fun `random maps are valid across seeds and sizes`() {
        val rng = Random(20260922L)
        for (size in listOf(9, 6, 4)) {
            repeat(10) {
                val map = JigsawMaps.randomMap(size, rng)
                RegionConstraint(size, map) // validates counts + contiguity
                for (id in 0 until size) {
                    assertEquals(size, map.count { it == id }, "size $size region $id")
                }
            }
        }
    }

    @Test
    fun `random maps vary`() {
        val rng = Random(7L)
        val maps = List(5) { JigsawMaps.randomMap(9, rng).toList() }
        assertTrue(maps.toSet().size > 1, "expected varied layouts")
    }
}
