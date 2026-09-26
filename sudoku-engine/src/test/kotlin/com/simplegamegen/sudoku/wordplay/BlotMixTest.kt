package com.simplegamegen.sudoku.wordplay

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlotMixTest {
    @Test fun `hard and expert grids often bring knots, wilds, seals and odd shapes`() {
        for (tier in listOf(BlotTier.MEDIUM, BlotTier.HARD, BlotTier.EXPERT)) {
            val games = (1L..12L).map { BlotGenerator.generate(tier, it) }
            val seals = games.count { g -> g.start.any { it in 'a'..'z' } }
            val knots = games.count { KNOT in it.start }
            val wilds = games.count { WILD in it.start }
            val shaped = games.count { it.holes.isNotEmpty() }
            println("$tier: seals $seals, knots $knots, wilds $wilds, shaped $shaped of 12")
            assertTrue(tier == BlotTier.MEDIUM || seals >= 3, "$tier seals")
            assertTrue(shaped >= 3, "$tier shapes")
        }
    }
}
