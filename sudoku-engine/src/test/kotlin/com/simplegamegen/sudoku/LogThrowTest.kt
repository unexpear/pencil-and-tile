package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.arcade.Challenge
import com.simplegamegen.sudoku.arcade.Ease
import com.simplegamegen.sudoku.arcade.LogThrow
import com.simplegamegen.sudoku.arcade.LogThrowCodec
import com.simplegamegen.sudoku.arcade.Pattern
import com.simplegamegen.sudoku.arcade.Phase
import com.simplegamegen.sudoku.arcade.RotationProgram
import com.simplegamegen.sudoku.arcade.SpinSeg
import com.simplegamegen.sudoku.arcade.StuckKnife
import com.simplegamegen.sudoku.arcade.ThrowState
import com.simplegamegen.sudoku.arcade.Wood
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class LogThrowTest {
    @Test fun `blade width becomes an angle at the rim and an exact touch is a hit`() {
        val width = LogThrow.angularWidth(0.16, 1.0)
        assertTrue(width > 0.16 && width < 0.17)
        assertTrue(LogThrow.angularWidth(0.16, 1.0) > LogThrow.angularWidth(0.16, 1.25), "a wider log makes the same blade a smaller angle")
        assertTrue(LogThrow.collides(0.0, 0.0))
        assertTrue(LogThrow.collides(0.0, width))
        assertFalse(LogThrow.collides(0.0, width + 1e-4))
        assertTrue(LogThrow.collides(0.2, 0.2 - width))
        assertTrue(LogThrow.collides(0.0, LogThrow.TAU - width))
        assertFalse(LogThrow.collides(0.0, LogThrow.TAU - width - 1e-3))
        assertEquals(0.0, LogThrow.separation(0.0, LogThrow.TAU), 1e-9)
        assertEquals(LogThrow.TAU / 2, LogThrow.separation(0.0, kotlin.math.PI), 1e-9)
    }

    @Test fun `rotation segments integrate in closed form and loop`() {
        val linear = SpinSeg(1000, 2.0, 2.0, Ease.LINEAR)
        assertEquals(2.0, linear.delta(1000), 1e-9)
        assertEquals(1.0, linear.delta(500), 1e-9)
        val accel = SpinSeg(1000, 0.0, 3.0, Ease.IN)
        assertEquals(1.0, accel.delta(1000), 1e-9)
        assertEquals(0.125, accel.delta(500), 1e-9)
        assertEquals(0.75, accel.omega(500), 1e-9)
        val decel = SpinSeg(1000, 0.0, 3.0, Ease.OUT)
        assertEquals(2.0, decel.delta(1000), 1e-9)
        val smooth = SpinSeg(1000, 0.0, 4.0, Ease.SMOOTH)
        assertEquals(2.0, smooth.delta(1000), 1e-9)
        assertEquals(2.0, smooth.omega(500), 1e-9)
        val loop = RotationProgram(listOf(SpinSeg(1000, Math.PI, Math.PI, Ease.LINEAR)))
        assertEquals(Math.PI, loop.angleAt(1000), 1e-9)
        assertEquals(Math.PI * 2, loop.angleAt(2000), 1e-9)
        assertEquals(Math.PI * 1.5, loop.angleAt(1500), 1e-9)
        assertEquals(Math.PI, loop.omegaAt(1500), 1e-6)
        val once = RotationProgram(listOf(SpinSeg(1000, 1.0, 1.0, Ease.LINEAR)), loop = false)
        assertEquals(1.0, once.angleAt(1000), 1e-9)
        assertEquals(1.0, once.angleAt(5000), 1e-9)
    }

    @Test fun `a thrown dagger sticks or clashes from the angle at arrival and frame size does not matter`() {
        val run = LogThrow.newGame(7).beginEndless()
        val safe = nextThrow(run, preferPickup = false)
        val stuck = run.copy(timeMs = safe).throwKnife()!!.advance(LogThrow.FLIGHT_MS)
        assertEquals(Phase.AIM, stuck.phase)
        assertEquals(1, stuck.thrown)
        assertEquals(1, stuck.stuck.count { it.player })
        assertEquals(LogThrow.KNIFE_POINTS, stuck.score)
        val coarse = run.copy(timeMs = safe).throwKnife()!!.advance(LogThrow.FLIGHT_MS)
        val fine = run.copy(timeMs = safe).throwKnife()!!.let { flying ->
            var s = flying
            repeat(10) { s = s.advance(10) }
            s
        }
        assertEquals(coarse.stuck.filter { it.player }.map { it.local }, fine.stuck.filter { it.player }.map { it.local })
        val same = stuck.let { s ->
            var t = s.timeMs
            var found: Long? = null
            while (t < s.timeMs + 20_000) {
                if (s.preview(t).blocked) { found = t; break }
                t += 4
            }
            assertTrue(found != null, "the stuck dagger comes back around")
            s.copy(timeMs = found!!).throwKnife()!!.advance(LogThrow.FLIGHT_MS)
        }
        assertEquals(Phase.FAIL, same.phase)
        assertEquals(1, same.stuck.count { it.player }, "the clashing dagger does not stick")
        assertTrue(same.best >= same.score)
    }

    @Test fun `score counts knives stages and pickups and the best survives a new run`() {
        assertEquals(1 * 3 + 10 * 2 + 5 * 4, LogThrow.score(3, 2, 4))
        val cleared = solve(LogThrow.newGame(3).beginEndless())
        assertEquals(Phase.CLEAR, cleared.phase)
        val spec = LogThrow.endlessSpec(1)
        assertEquals(LogThrow.score(spec.knives, 1, cleared.pickupsTaken), cleared.score)
        assertEquals(spec.pickups.size, cleared.pickupsTaken, "the solver takes the fruit in the window")
        val failed = cleared.copy(phase = Phase.FAIL, eventMs = cleared.timeMs)
        // Fail banks best from the current score. Simulate the real path:
        val after = LogThrow.newGame(3).beginEndless().let { start ->
            val mid = solve(start)
            // Force a clash after the clear is rewound into play with the score kept.
            val playing = mid.copy(phase = Phase.AIM, thrown = mid.thrown - 1, stuck = mid.stuck.dropLast(1),
                knivesStuck = mid.knivesStuck - 1, stagesCleared = 0, pickupsTaken = (mid.pickupsTaken - 1).coerceAtLeast(0))
            val bad = playing.copy(timeMs = 0).throwKnife()!!
            // May or may not clash at t=0. Drive a known clash instead.
            val doomed = playing.copy(
                stuck = playing.stuck + com.simplegamegen.sudoku.arcade.StuckKnife(LogThrow.norm(-playing.spec().program.angleAt(playing.timeMs + LogThrow.FLIGHT_MS)), false),
            )
            doomed.throwKnife()!!.advance(LogThrow.FLIGHT_MS)
        }
        assertEquals(Phase.FAIL, after.phase)
        assertTrue(after.best >= after.score)
        val retry = after.again()
        assertEquals(Phase.AIM, retry.phase)
        assertEquals(1, retry.level)
        assertEquals(0, retry.score)
        assertEquals(after.best, retry.best)
        assertEquals(0, failed.earned)
    }

    @Test fun `endless stages raise the complication and stay throwable`() {
        val first = LogThrow.endlessSpec(1)
        val later = LogThrow.endlessSpec(24)
        assertTrue(later.knives >= first.knives)
        assertTrue(later.obstacles.size >= first.obstacles.size)
        assertTrue(later.program.segments.first().omegaStart > first.program.segments.first().omegaStart || later.pattern != first.pattern)
        val patterns = (1..14).map { LogThrow.endlessSpec(it).pattern }.toSet()
        assertEquals(Pattern.entries.toSet(), patterns)
        for (stage in 1..18) {
            val spec = LogThrow.endlessSpec(stage)
            spec.validate()
            val solved = solve(LogThrow.newGame(1).beginEndless().jumpTo(stage))
            assertEquals(Phase.CLEAR, solved.phase, "endless stage $stage (${spec.pattern})")
        }
    }

    @Test fun `forty eight levels unlock in order with bosses challenges and stars`() {
        assertEquals(48, LogThrow.LEVEL_COUNT)
        val bosses = (1..48).filter { LogThrow.levelSpec(it).boss }
        assertEquals(listOf(5, 10, 15, 20, 25, 30, 35, 40, 45), bosses)
        assertEquals(9, bosses.map { LogThrow.levelSpec(it).wood }.distinct().size)
        assertFalse(bosses.any { LogThrow.levelSpec(it).wood == Wood.OAK })
        val challenges = (1..48).mapNotNull { n -> LogThrow.levelSpec(n).challenge?.let { n to it } }
        assertTrue(challenges.size >= 8)
        assertEquals(Challenge.entries.toSet(), challenges.map { it.second }.toSet())
        assertTrue(challenges.none { it.first % 5 == 0 }, "bosses are not also filed as challenges")
        val must = challenges.filter { it.second == Challenge.MUST_HIT }
        assertTrue(must.all { LogThrow.levelSpec(it.first).requirePickups })
        for (n in 1..48) {
            val spec = LogThrow.levelSpec(n)
            spec.validate()
            assertNotEquals(0.0, spec.program.netRadians, 1e-6)
        }
        val game = LogThrow.newGame(11)
        assertTrue(game.unlocked(1))
        assertFalse(game.unlocked(2))
        assertNull(game.beginLevel(2))
        val level1 = solve(game.beginLevels().beginLevel(1)!!)
        assertEquals(Phase.CLEAR, level1.phase)
        assertEquals(3, level1.earned)
        assertEquals(3, level1.stars[0])
        val map = level1.advance(LogThrow.BURST_MS)
        assertEquals(Phase.SELECT, map.phase)
        assertTrue(map.unlocked(2))
        assertFalse(map.unlocked(3))
        val replay = solve(map.beginLevel(1)!!)
        assertEquals(3, replay.stars[0], "a worse clear cannot erase a better star")
        // A clear that misses half the fruit is two stars. Level 3 has no fruit (3 % 3 == 0 is two, 2 has one).
        assertEquals(3, LogThrow.starsFor(0, 0))
        assertEquals(3, LogThrow.starsFor(2, 2))
        assertEquals(2, LogThrow.starsFor(1, 2))
        assertEquals(1, LogThrow.starsFor(0, 2))
        for (n in listOf(1, 4, 5, 8, 12, 15, 16, 20, 24, 30, 36, 40, 45, 48)) {
            val start = LogThrow.newGame(4).beginLevels().let { hub ->
                val opened = (1 until n).fold(hub) { s, i -> s.copy(stars = s.stars.mapIndexed { idx, v -> if (idx == i - 1) 1 else v }) }
                opened.beginLevel(n)!!
            }
            assertEquals(Phase.CLEAR, solve(start).phase, "level $n ${LogThrow.levelSpec(n).pattern} ${LogThrow.levelSpec(n).challenge}")
        }
    }

    @Test fun `saves round trip and a broken save is discarded`() {
        var state = LogThrow.newGame(99).beginEndless()
        state = solve(state)
        val restored = LogThrowCodec.decode(LogThrowCodec.encode(state))
        assertEquals(state.phase, restored!!.phase)
        assertEquals(state.score, restored.score)
        assertEquals(state.level, restored.level)
        assertEquals(state.stuck.filter { it.player }.size, restored.stuck.filter { it.player }.size)
        val levels = LogThrow.newGame(5).beginLevels().beginLevel(1)!!.let { solve(it) }.advance(LogThrow.BURST_MS)
        val back = LogThrowCodec.decode(LogThrowCodec.encode(levels))
        assertEquals(Phase.SELECT, back!!.phase)
        assertEquals(3, back.stars[0])
        assertEquals(0, back.stars.drop(1).sum())
        val flying = levels.beginLevel(2)!!.throwKnife()!!
        val saved = LogThrowCodec.decode(LogThrowCodec.encode(flying))
        assertEquals(Phase.AIM, saved!!.phase)
        assertEquals(0, saved.thrown)
        assertNull(LogThrowCodec.decode("nope"))
        assertNull(LogThrowCodec.decode(LogThrowCodec.encode(state).replace("1\n", "9\n")))
        assertNull(LogThrowCodec.decode(LogThrowCodec.encode(state).replace("ENDLESS", "SIDEWAYS")))
    }

    @Test fun `a miss keeps the clock running so the dagger can bounce`() {
        val start = LogThrow.newGame(1).beginEndless()
        val doomed = start.copy(
            stuck = start.stuck + StuckKnife(LogThrow.norm(-start.spec().program.angleAt(start.timeMs + LogThrow.FLIGHT_MS)), false),
        )
        val failed = doomed.throwKnife()!!.advance(LogThrow.FLIGHT_MS)
        assertEquals(Phase.FAIL, failed.phase)
        val later = failed.advance(240)
        assertEquals(Phase.FAIL, later.phase)
        assertEquals(failed.timeMs + 240, later.timeMs)
        assertEquals(failed, failed.advance(0))
        val menu = LogThrow.newGame(1)
        assertEquals(menu, menu.advance(500))
    }

    private fun ThrowState.jumpTo(stage: Int): ThrowState {
        val spec = LogThrow.endlessSpec(stage)
        return copy(level = stage, timeMs = 0, thrown = 0, stuck = spec.obstacles.map { com.simplegamegen.sudoku.arcade.StuckKnife(LogThrow.norm(it), false) },
            taken = emptySet(), phase = Phase.AIM)
    }

    /** Greedy player: throw at the next opening, preferring fruit while any remains. */
    private fun solve(start: ThrowState): ThrowState {
        var s = start
        repeat(start.spec().knives + 2) {
            if (s.phase == Phase.CLEAR || s.phase == Phase.FAIL) return s
            val whenThrow = nextThrow(s, preferPickup = s.taken.size < s.spec().pickups.size)
            s = s.copy(timeMs = whenThrow).throwKnife()!!.advance(LogThrow.FLIGHT_MS)
        }
        return s
    }

    private fun nextThrow(state: ThrowState, preferPickup: Boolean): Long {
        var t = state.timeMs
        val end = t + 16_000
        var plain: Long? = null
        while (t <= end) {
            val hit = state.preview(t)
            if (!hit.blocked) {
                if (hit.pickups.isNotEmpty() && preferPickup) return t
                if (plain == null) plain = t
                if (!preferPickup) return t
            }
            t += 8
        }
        return plain ?: error("no opening on ${state.mode} ${state.level} ${state.spec().pattern}")
    }
}
