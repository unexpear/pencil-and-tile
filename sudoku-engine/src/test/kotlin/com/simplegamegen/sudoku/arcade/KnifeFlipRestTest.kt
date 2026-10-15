package com.simplegamegen.sudoku.arcade

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/** Every failed throw has to finish lying on something: the block, the counter or the floor. */
class KnifeFlipRestTest {
    private fun assertRests(body: FlipBody, flight: Flight, what: String) {
        if (flight.end == FlipEnd.STUCK || flight.end == FlipEnd.LANDED) return
        val f = flight.final
        assertEquals(FlipPhysics.restHeight(body, f.x), f.z, 1e-4f, "$what ${flight.end}: z ${f.z} at x ${f.x}")
        assertTrue(abs(cos(f.theta)) < 1e-4f, "$what ${flight.end}: long axis level, theta ${f.theta}")
        val n = flight.samples.size
        val a = flight.samples[n - 2]
        val b = flight.samples[n - 1]
        val v = (abs(b.x - a.x) + abs(b.z - a.z)) / (b.t - a.t)
        assertTrue(v < 1e-3f, "$what ${flight.end}: still moving at $v m/s")
        val lowest = flight.samples.minOf { it.z - FlipPhysics.restHeight(body, it.x) }
        assertTrue(lowest > -0.5f, "$what never sinks deep into a surface ($lowest)")
        assertEquals(flight.final, flight.at(flight.time + 5f), "$what holds still after the end")
    }

    @Test fun `every failed swipe ends at rest`() {
        val seen = HashSet<FlipEnd>()
        for ((index, o) in FlipModels.options.withIndex()) {
            val held = FlipPhysics.hold(o.body)
            var speed = 0.7f
            while (speed <= 6f) {
                var aim = 0.2f
                while (aim <= 1.6f) {
                    val r = FlipPhysics.flick(o.body, held.theta, speed, aim, held.x, held.z)
                    val flight = FlipPhysics.simulate(o.body, r)
                    seen += flight.end
                    assertRests(o.body, flight, "option $index ${o.label} v=$speed aim=$aim")
                    aim += 0.1f
                }
                speed += 0.25f
            }
            for (n in 0 until 40) {
                val hand = KnifeFlip.handThrow(index, 7L, n)
                val g = KnifeFlip.start(7L, index).toss(hand.speed, hand.aim, hand.spin) ?: continue
                assertRests(o.body, FlipPhysics.simulate(o.body, g.release!!), "option $index hand $n")
            }
        }
        for (end in listOf(FlipEnd.HANDLE, FlipEnd.FLAT, FlipEnd.OFF)) {
            assertTrue(end in seen, "the sweep covers $end (saw $seen)")
        }
    }

    @Test fun `a finished round can be tossed again`() {
        val ended = KnifeFlip(0, 3L, 2, 0, KnifeFlip.Phase.ENDED, null, 5, FlipEnd.FLAT.name)
        val again = ended.toss(3f, 1f)
        assertTrue(again != null && again.phase == KnifeFlip.Phase.FLYING, "toss after a miss starts a new round")
        assertEquals(0, again!!.streak)
        assertEquals(0, again.score)
    }

    @Test fun `level angle is the nearest horizontal`() {
        assertEquals((PI / 2).toFloat(), FlipPhysics.nearestLevel(1.2f), 1e-5f)
        assertEquals((-PI / 2).toFloat(), FlipPhysics.nearestLevel(-1.0f), 1e-5f)
        assertEquals((3 * PI / 2).toFloat(), FlipPhysics.nearestLevel(4.4f), 1e-5f)
    }
}
