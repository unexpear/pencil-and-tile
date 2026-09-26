package com.simplegamegen.sudoku.wordplay

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlotMixTest {
    @Test fun `hard and expert grids often bring knots, wilds, seals and odd shapes`() {
        for (tier in listOf(BlotTier.MEDIUM, BlotTier.HARD, BlotTier.EXPERT)) {
            val games = (1L..20L).map { BlotGenerator.generate(tier, it) }
            val seals = games.count { g -> g.start.any { it in 'a'..'z' } }
            val knots = games.count { KNOT in it.start }
            val wilds = games.count { WILD in it.start }
            val shaped = games.count { it.holes.isNotEmpty() }
            println("$tier: seals $seals, knots $knots, wilds $wilds, shaped $shaped of 20")
            assertTrue(tier == BlotTier.MEDIUM || seals >= 3, "$tier seals")
            val echoes = games.count { ECHO in it.start }
            println("$tier echoes $echoes")
            assertTrue(shaped >= 3, "$tier shapes")
            val arrows = games.count { g -> g.start.any { it in ARROWS } }
            val pieces = games.count { it.pieces.isNotEmpty() }
            println("$tier arrows $arrows, pieces $pieces")
            assertTrue(tier != BlotTier.EXPERT || arrows >= 2, "expert arrows")
            assertTrue(tier == BlotTier.MEDIUM || pieces >= 1, "$tier pieces")
            for (g in games) {
                // Put back where they came from, the pieces make the grid the plan was made for.
                val ready = g.pieces.indices.fold(g) { acc, k -> acc.place(k, g.pieces[k].home)!! }
                assertTrue(BlotGenerator.replays(ready.cells, g.width, g.words, g.plan, g.wrap), "$tier seed ${g.seed} replays")
            }
        }
    }

    @Test fun `the daily puzzle is the same for everyone on a day and harder at the weekend`() {
        val monday = BlotDaily.puzzle(20260928, 1)
        assertTrue(monday == BlotDaily.puzzle(20260928, 1))
        assertTrue(monday.daily == 20260928 && monday.tier == BlotTier.EASY)
        assertTrue(BlotDaily.tierFor(7) == BlotTier.EXPERT)
        assertTrue(BlotCodec.decode(BlotCodec.encode(monday)) == monday)
        val today = java.time.LocalDate.of(2026, 9, 26)
        assertTrue(BlotDaily.streak(setOf(20260924, 20260925, 20260926), today) == 3)
        assertTrue(BlotDaily.streak(setOf(20260924, 20260925), today) == 2, "today not done yet keeps yesterday's streak")
        assertTrue(BlotDaily.streak(setOf(20260920), today) == 0)
    }
}
