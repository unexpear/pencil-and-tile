package com.simplegamegen.sudoku.arcade
import org.junit.jupiter.api.Test
class ScratchProbeTest {
    @Test fun probe() {
        for ((i, o) in FlipModels.options.withIndex()) for (n in 0 until 6) {
            val h = KnifeFlip.handThrow(i, 12345L, n)
            val g = KnifeFlip.start(12345L, i).toss(h.speed, h.aim, h.spin) ?: continue
            val f = FlipPhysics.simulate(o.body, g.release!!)
            val last = f.samples.takeLast(3).map { "%.2f:%.3f,%.3f,%.2f,%.2f".format(it.t, it.x, it.z, it.theta, it.roll) }
            println("PROBE $i ${o.label} ${f.end} time=${f.time} n=${f.samples.size} final=${f.final} last=$last at=${f.at(f.time)}")
        }
    }
}