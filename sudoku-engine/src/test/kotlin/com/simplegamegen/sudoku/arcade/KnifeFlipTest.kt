package com.simplegamegen.sudoku.arcade

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

class KnifeFlipTest {
    @Test fun `flight time matches gravity`() {
        val drop = sqrt(2f / FlipPhysics.GRAVITY)
        val z = FlipPhysics.comHeight(1f, 0f, drop, drag = false)
        assertEquals(0f, z, 0.0015f, "a metre of free fall takes sqrt(2h/g)")
        val risen = FlipPhysics.comHeight(0.2f, 3f, 0.25f, drag = false)
        val expected = 0.2f + 3f * 0.25f + 0.5f * -FlipPhysics.GRAVITY * 0.25f * 0.25f
        assertEquals(expected, risen, 0.0015f)
    }

    @Test fun `a different centre of mass changes the spin`() {
        val mass = 0.22f
        val length = 0.30f
        val inertia = 0.0014f
        val grip = 0.26f
        val balanced = rod("balanced", mass, length, inertia, com = 0.15f, grip = grip)
        val handleHeavy = rod("handle", mass, length, inertia, com = 0.24f, grip = grip)
        val aim = 1.25f
        val speed = 2.4f
        val a = FlipPhysics.flick(balanced, 1.2f, speed, aim, 0.1f, 0.25f)
        val b = FlipPhysics.flick(handleHeavy, 1.2f, speed, aim, 0.1f, 0.25f)
        assertTrue(abs(a.omega) > abs(b.omega) * 1.4f, "lever ${balanced.gripFromTip - 0.15f} vs ${grip - 0.24f}: ω ${a.omega} vs ${b.omega}")
        assertTrue(a.omega > 0f && b.omega > 0f, "an upward flick rotates the point downward")

        val throwing = FlipPhysics.flick(FlipModels.throwing, 1.2f, speed, aim, 0.1f, 0.24f)
        val pocket = FlipPhysics.flick(FlipModels.pocket, 1.2f, speed, aim, 0.1f, 0.24f)
        val cleaver = FlipPhysics.flick(FlipModels.cleaver, 1.2f, speed, aim, 0.1f, 0.24f)
        assertTrue(abs(throwing.omega - pocket.omega) > 1f, "throwing ${throwing.omega} pocket ${pocket.omega}")
        val throwSpeed = hypot(throwing.vx, throwing.vz)
        val cleaverSpeed = hypot(cleaver.vx, cleaver.vz)
        assertTrue(throwSpeed > cleaverSpeed * 1.15f, "a heavier cleaver leaves more slowly: $throwSpeed vs $cleaverSpeed")
        assertTrue(FlipModels.cleaver.balance < 0.45f, "a cleaver is blade-heavy")
        assertTrue(FlipModels.throwing.balance in 0.47f..0.53f, "a throwing knife is balanced")
        assertTrue(FlipModels.pocket.balance > 0.58f, "a pocket knife is handle-heavy")
        assertTrue(FlipModels.chef.balance > FlipModels.throwing.balance)
    }

    @Test fun `the point sticks and the handle does not`() {
        val knife = FlipModels.throwing
        val tip = justAbove(knife, theta = Math.PI.toFloat(), along = 0f)
        val stuck = FlipPhysics.simulate(knife, tip.copy(vz = -2.2f, vx = 0.15f, omega = 0.4f))
        assertEquals(FlipEnd.STUCK, stuck.end, "tip-down at ${tip.theta}")

        val handle = justAbove(knife, theta = 0f, along = knife.length)
        val bounced = FlipPhysics.simulate(knife, handle.copy(vz = -2.2f, vx = 0.15f, omega = -0.4f))
        assertNotEquals(FlipEnd.STUCK, bounced.end, bounced.end.name)

        val flat = justAbove(knife, theta = (Math.PI / 2).toFloat(), along = knife.length / 2f)
        val slid = FlipPhysics.simulate(knife, flat.copy(vz = -2.2f, vx = 0.4f, omega = 0.2f))
        assertNotEquals(FlipEnd.STUCK, slid.end, slid.end.name)
    }

    @Test fun `fills change the mass the balance and the spin`() {
        val bottles = FlipModels.bottles
        assertEquals(listOf(0.25f, 1f / 3f, 0.5f, 0.75f, 1f), bottles.map { it.fill })
        assertTrue(bottles.all { it.fill >= 0.25f })
        for (i in 0 until bottles.lastIndex) {
            assertTrue(bottles[i].mass < bottles[i + 1].mass, bottles[i].label)
            assertTrue(bottles[i].comFromBase(0f) <= bottles[i + 1].comFromBase(0f) + 0.002f)
        }
        val third = bottles[1]
        val full = bottles[4]
        val quarter = bottles[0]
        assertTrue(third.mobility > full.mobility)
        assertTrue(third.inertiaSpread > third.inertiaPooled * 1.4f, "sloshing raises inertia")
        assertEquals(full.inertiaPooled, full.inertiaSpread, 1e-6f)
        val w3 = abs(FlipPhysics.coastOmega(third, 16f, 0.35f))
        val wQ = abs(FlipPhysics.coastOmega(quarter, 16f, 0.35f))
        val wF = abs(FlipPhysics.coastOmega(full, 16f, 0.35f))
        assertTrue(w3 < wF * 0.8f, "1/3 slows to $w3, full stays $wF")
        assertTrue(w3 < wQ, "1/3 ($w3) slows more than 1/4 ($wQ)")
    }

    @Test fun `fills in the published band land more often than a full bottle`() {
        // A short flick: a couple of metres per second, steep enough to come down on the block.
        // Dekker et al. Sec. IV put a good flip around 20%–40%, not at one exact fraction.
        // 1/4 sits nearer their lowest centre of mass (eq. 16) and 1/3 nearer the biggest slowdown (eq. 14).
        val speeds = listOf(2.2f, 2.4f, 2.6f, 2.8f, 3.0f)
        val aims = listOf(1.25f, 1.35f, 1.45f, 1.55f)
        val rates = FlipModels.bottles.map { bottle ->
            var lands = 0
            var tries = 0
            for (speed in speeds) for (aim in aims) {
                tries++
                val held = FlipPhysics.hold(bottle)
                val release = FlipPhysics.flick(bottle, held.theta, speed, aim, held.x, held.z)
                if (FlipPhysics.simulate(bottle, release).end == FlipEnd.LANDED) lands++
            }
            bottle.fill to lands.toFloat() / tries
        }
        val report = rates.joinToString("\n") { (fill, rate) -> "fill $fill rate $rate" }
        val byFill = rates.toMap()
        val quarter = byFill.getValue(0.25f)
        val third = byFill.getValue(1f / 3f)
        val half = byFill.getValue(0.5f)
        val three = byFill.getValue(0.75f)
        val full = byFill.getValue(1f)
        assertTrue(quarter > full && third > full, "full should land less often\n$report")
        assertTrue(quarter > three && third > three, "3/4 should land less often\n$report")
        assertTrue(third > half, "1/2 matches the slowdown but sits higher\n$report")
        assertTrue(quarter >= 0.15f && third >= 0.10f, "the 20%–40% band should have a real window\n$report")
    }

    @Test fun `a practised toss sticks the throwing knife and lands a third-full bottle`() {
        val knife = FlipModels.throwing
        val (ks, ka) = KnifeFlip.KNIFE_TOSS
        val held = FlipPhysics.hold(knife)
        val toss = FlipPhysics.flick(knife, held.theta, ks, ka, held.x, held.z)
        val flight = FlipPhysics.simulate(knife, toss)
        assertEquals(FlipEnd.STUCK, flight.end, "ω ${toss.omega} vz ${toss.vz} vx ${toss.vx} t ${flight.time} θ ${flight.final.theta}")

        val bottle = FlipModels.bottles[1]
        val (bs, ba) = KnifeFlip.BOTTLE_TOSS
        val hand = FlipPhysics.hold(bottle)
        val flip = FlipPhysics.flick(bottle, hand.theta, bs, ba, hand.x, hand.z)
        val landed = FlipPhysics.simulate(bottle, flip)
        assertEquals(FlipEnd.LANDED, landed.end, "ω ${flip.omega} Δθ ${landed.final.theta - hand.theta} t ${landed.time}")
        val full = FlipModels.bottles[4]
        val fullHand = FlipPhysics.hold(full)
        val same = FlipPhysics.flick(full, fullHand.theta, bs, ba, fullHand.x, fullHand.z)
        assertNotEquals(FlipEnd.LANDED, FlipPhysics.simulate(full, same).end)
    }

    @Test fun `one full turn covers the same distance at any throw speed`() {
        // Thiel formulas 1–3: d = 2π V / ω, and V = r ω, so d does not depend on speed.
        val knife = FlipModels.throwing
        val slow = FlipPhysics.flick(knife, 1.2f, 1.4f, 1.2f, 0.1f, 0.24f)
        val fast = FlipPhysics.flick(knife, 1.2f, 2.8f, 1.2f, 0.1f, 0.24f)
        val dSlow = (2.0 * PI).toFloat() * hypot(slow.vx, slow.vz) / slow.omega
        val dFast = (2.0 * PI).toFloat() * hypot(fast.vx, fast.vz) / fast.omega
        assertEquals(dSlow, dFast, 0.02f, "d=$dSlow vs $dFast")
        assertTrue(dSlow > 0.2f && dSlow < 4f, "a flick's turn distance is $dSlow m")
    }

    @Test fun `published bottle fill has its optimum near a quarter to a third`() {
        val bestG = (1..99).maxBy { -BottleModel.slowdownG(it / 100f) } / 100f
        assertTrue(bestG in 0.38f..0.44f, "G(f) is smallest at $bestG, Dekker et al. eq. 14 gives ~0.41")
        assertEquals(0.18f, BottleModel.lowestComFill(), 0.01f)
        assertTrue(BottleModel.pooledComFraction(BottleModel.lowestComFill()) < BottleModel.pooledComFraction(0.5f))
        val third = 1f / 3f
        assertTrue(third in 0.20f..0.41f, "1/3 sits in the paper's 20%–40% window")
        assertTrue(BottleModel.slowdownG(third) < BottleModel.slowdownG(0.25f))
        assertTrue(BottleModel.slowdownG(third) < BottleModel.slowdownG(1f))
        assertEquals(1f, BottleModel.slowdownG(1f), 1e-4f)
    }

    @Test fun `spreading water keeps angular momentum`() {
        val third = FlipModels.bottles[1]
        val start = 12f
        val sample = FlipPhysics.coastSample(third, start, 0.45f, drag = false)
        val expected = start * third.inertia(0f) / third.inertia(sample.sigma)
        assertEquals(expected, sample.omega, 0.05f, "σ ${sample.sigma} ω ${sample.omega} vs $expected")
        assertTrue(sample.sigma > 0.5f, "water should have climbed, σ=${sample.sigma}")
        val full = FlipPhysics.coastSample(FlipModels.bottles[4], start, 0.45f, drag = false)
        assertEquals(start, full.omega, 0.02f, "a full bottle cannot slosh")
        assertEquals(0f, full.sigma, 1e-3f)
    }

    @Test fun `a round keeps a streak until a miss and the save round-trips`() {
        val throwing = FlipModels.knives.indexOf(FlipModels.throwing)
        var g = KnifeFlip.start(7L, throwing)
        val (speed, aim) = KnifeFlip.KNIFE_TOSS
        g = g.toss(speed, aim)!!
        g = g.settle()
        assertEquals(1, g.streak)
        assertEquals(KnifeFlip.Phase.READY, g.phase)
        g = g.toss(speed, aim)!!
        g = g.settle()
        assertEquals(2, g.score)
        val bad = g.toss(4.8f, 0.4f)!!
        val missed = bad.settle()
        assertEquals(KnifeFlip.Phase.ENDED, missed.phase)
        assertEquals(2, missed.score)
        assertEquals(g.copy(phase = KnifeFlip.Phase.FLYING, release = bad.release, throwNum = bad.throwNum, verdict = ""), bad)
        assertEquals(missed, KnifeFlipCodec.decode(KnifeFlipCodec.encode(missed)))
        assertEquals(null, KnifeFlipCodec.decode("nope"))
    }

    private fun justAbove(body: FlipBody, theta: Float, along: Float): Release {
        val fromCom = body.comFromTip(0f) - along
        val z = 0.012f - fromCom * kotlin.math.cos(theta)
        val x = 0.28f
        return Release(x, z, 0f, 0f, theta, 0f)
    }

    private fun rod(id: String, mass: Float, length: Float, inertia: Float, com: Float, grip: Float) = FlipBody(
        id, id, bottle = false, length = length, mass = mass,
        comPooled = com, comSpread = com, inertiaPooled = inertia, inertiaSpread = inertia,
        gripFromTip = grip, tipZone = 0.028f, handleZone = 0.08f, baseRadius = 0f,
        mobility = 0f, waterMass = 0f, fill = 0f,
    )
}
