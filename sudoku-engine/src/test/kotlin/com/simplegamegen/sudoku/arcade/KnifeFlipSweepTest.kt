package com.simplegamegen.sudoku.arcade

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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

    @Test fun `bottles land in a skill band and fuller bottles are harder`() {
        val rates = FlipModels.bottles.map { rate(grid(it)) }
        println("BOTTLE SWEEP " + FlipModels.fillLabels.zip(rates).joinToString { (l, r) -> "$l ${"%.1f".format(r * 100)}%" })
        // 1/4, 1/3 and 1/2: a real skill band, about 8–20% of the swipe range.
        rates.take(3).forEach { assertTrue(it in 0.08f..0.20f, "1/4 to 1/2 should land 8–20% of swipes: $rates") }
        // 3/4 and full: harder, but still possible.
        rates.drop(3).forEach { assertTrue(it > 0f && it < rates.take(3).min(), "3/4 and full should be harder but not impossible: $rates") }
    }

    @Test fun `a stuck knife is embedded tip first at a steep angle`() {
        var stuck = 0
        for (knife in FlipModels.knives) for (v in speeds) for (a in aims) {
            val held = FlipPhysics.hold(knife)
            val flight = FlipPhysics.simulate(knife, FlipPhysics.flick(knife, held.theta, v, a, held.x, held.z))
            if (flight.end != FlipEnd.STUCK) continue
            stuck++
            // theta 0 is tip up, so tip straight down is pi. The drawn pose is flight.final.
            val fromDown = abs(atan2(sin(flight.final.theta - PI.toFloat()), cos(flight.final.theta - PI.toFloat()))) * 180f / PI.toFloat()
            assertTrue(fromDown <= 90f - FlipPhysics.MIN_ENTRY_DEG + 0.5f, "${knife.label} $v/$a stuck ${"%.0f".format(fromDown)}° off vertical")
            // The point is in the wood: below the block surface, inside the block.
            val tipZ = flight.final.z + knife.comFromTip(0f) * cos(flight.final.theta)
            assertTrue(tipZ < 0f, "${knife.label} $v/$a tip not in the wood ($tipZ)")
        }
        assertTrue(stuck > 0)
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

    @Test fun `the toss button goes through the physics and fails often`() {
        val report = StringBuilder()
        for ((o, option) in FlipModels.options.withIndex()) {
            val n = 400
            val wins = (0 until n).count { i -> KnifeFlip.handSucceeds(o, 7L + i / 50, i) }
            val r = wins.toFloat() / n
            report.append("${option.label} ${"%.1f".format(r * 100)}%  ")
            if (!option.body.bottle) assertTrue(r in 0.20f..0.40f, "${option.label}: Toss works ${r * 100}% of the time, want 20–40%")
            else assertTrue(r < 0.40f, "${option.label}: Toss works ${r * 100}% of the time")
        }
        println("TOSS BUTTON $report")
    }
}
