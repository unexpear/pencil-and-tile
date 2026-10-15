package com.simplegamegen.sudoku.arcade

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Sweeps the whole swipe range the screen can produce (speed 0.7–3.6, aim 0.9–1.5 rad).
 * Success has to be earned: most throws fail, only a band works, and past the block fails.
 */
class KnifeFlipSweepTest {
    private val speeds = KnifeFlip.SWIPE_SPEEDS
    private val aims = KnifeFlip.SWIPE_AIMS

    private fun grid(body: FlipBody) = speeds.map { v -> aims.map { a -> KnifeFlip.succeeds(body, v, a) } }

    private fun rate(g: List<List<Boolean>>) = g.flatten().count { it }.toFloat() / g.flatten().size

    @Test fun `most knife throws fail but every knife has a working band`() {
        val report = StringBuilder()
        for (knife in FlipModels.knives) {
            val g = grid(knife)
            val r = rate(g)
            report.append("${knife.label} ${"%.1f".format(r * 100)}%\n")
            assertTrue(r <= 0.30f, "${knife.label}: ${r * 100}% of swipes stick, too easy")
            assertTrue(r >= 0.02f, "${knife.label}: ${r * 100}% of swipes stick, no real window")
            // Only a band: the softest and the hardest swipes never stick.
            assertFalse(g.first().any { it }, "${knife.label}: the weakest swipe stuck")
            val rowsWithWins = g.indices.filter { i -> g[i].any { it } }
            assertTrue(rowsWithWins.size < speeds.size / 2, "${knife.label}: sticks across ${rowsWithWins.size} of ${speeds.size} speeds")
        }
        println("KNIFE SWEEP\n$report")
    }

    @Test fun `bottles are hard and the published fills beat a full bottle`() {
        val rates = FlipModels.bottles.map { rate(grid(it)) }
        println("BOTTLE SWEEP " + FlipModels.fillLabels.zip(rates).joinToString { (l, r) -> "$l ${"%.1f".format(r * 100)}%" })
        rates.forEach { assertTrue(it <= 0.15f, "a bottle should not land on most swipes: $rates") }
        assertTrue(rates[0] > rates[4] && rates[1] > rates[4], "1/4 and 1/3 should beat full: $rates")
        assertTrue(rates[0] > 0f && rates[1] > 0f, "the published band has a window: $rates")
    }

    @Test fun `throwing past the block always fails`() {
        for (o in FlipModels.options) {
            val held = FlipPhysics.hold(o.body)
            for (v in listOf(4.5f, 5.5f, 6.5f)) for (a in listOf(0.5f, 0.7f, 0.9f)) {
                val r = FlipPhysics.flick(o.body, held.theta, v, a, held.x, held.z)
                val end = FlipPhysics.simulate(o.body, r).end
                assertTrue(end != FlipEnd.STUCK && end != FlipEnd.LANDED, "${o.label} $v/$a past the block ended $end")
            }
        }
    }

    @Test fun `overspin and underspin fail around a working throw`() {
        for (knife in FlipModels.knives) {
            val (v, a) = KnifeFlip.practised(FlipModels.knives.indexOf(knife))
            assertTrue(KnifeFlip.succeeds(knife, v, a), "${knife.label} practised $v/$a")
            assertFalse(KnifeFlip.succeeds(knife, v * 0.6f, a), "${knife.label} underspun still stuck")
            assertFalse(KnifeFlip.succeeds(knife, v * 1.6f, a), "${knife.label} overspun still stuck")
        }
    }

    @Test fun `the toss button misses sometimes`() {
        val throwing = FlipModels.knives.indexOf(FlipModels.throwing)
        val results = (0 until 40).map { n ->
            val (v, a) = KnifeFlip.handThrow(throwing, 11L, n)
            KnifeFlip.succeeds(FlipModels.throwing, v, a)
        }
        assertTrue(results.any { !it }, "a hand throw should miss sometimes")
        assertTrue(results.any { it }, "a hand throw should stick sometimes")
        println("TOSS BUTTON throwing knife ${results.count { it }}/40")
    }
}
