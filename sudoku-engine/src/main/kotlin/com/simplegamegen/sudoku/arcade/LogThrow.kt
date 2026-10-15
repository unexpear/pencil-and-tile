package com.simplegamegen.sudoku.arcade

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.min

/**
 * Log Throw: daggers thrown into a spinning log. Motion is a closed-form function of time,
 * so the same stage spec always produces the same angles no matter the frame rate.
 *
 * Angles are radians. 0 is the impact point on the near rim (the bottom of the log),
 * increasing clockwise. A thrown dagger always meets that point. Knives already in
 * the wood occupy a real arc there, from the blade's width at the impact radius.
 */
object LogThrow {
    const val FLIGHT_MS = 100L
    const val BURST_MS = 900L
    const val LEVEL_COUNT = 48
    /** Chord length of a blade where the log's radius is 1. */
    const val BLADE_WIDTH = 0.16
    const val RADIUS = 1.0
    /** How close a dagger's impact must be to a pickup's centre to collect it. */
    const val PICKUP_REACH = 0.22
    const val KNIFE_POINTS = 1
    const val STAGE_POINTS = 10
    const val PICKUP_POINTS = 5

    const val TAU = PI * 2

    /** Full centre-to-centre angle at which two blades of [blade] touch at [radius]. */
    fun angularWidth(blade: Double = BLADE_WIDTH, radius: Double = RADIUS): Double {
        require(blade > 0 && radius > blade / 2)
        return 2 * asin((blade / 2) / radius)
    }

    fun norm(angle: Double): Double {
        val m = angle % TAU
        return if (m < 0) m + TAU else m
    }

    /** Shortest arc between two angles, in 0…π. */
    fun separation(a: Double, b: Double): Double {
        val d = abs(norm(a) - norm(b))
        return min(d, TAU - d)
    }

    /**
     * True when two blades whose centres sit at [a] and [b] overlap on the rim,
     * including an exact edge touch.
     */
    fun collides(a: Double, b: Double, blade: Double = BLADE_WIDTH, radius: Double = RADIUS): Boolean =
        separation(a, b) <= angularWidth(blade, radius) + 1e-12

    fun score(knives: Int, stages: Int, pickups: Int): Int {
        require(knives >= 0 && stages >= 0 && pickups >= 0)
        return knives * KNIFE_POINTS + stages * STAGE_POINTS + pickups * PICKUP_POINTS
    }

    /** Stars for a cleared level. A level with no pickups is a clean three. */
    fun starsFor(taken: Int, total: Int): Int {
        require(taken in 0..total)
        return when {
            total == 0 -> 3
            taken == total -> 3
            taken * 2 >= total -> 2
            else -> 1
        }
    }

    fun endlessSpec(stage: Int): StageSpec {
        require(stage >= 1)
        val pattern = Pattern.entries[(stage - 1) % Pattern.entries.size]
        val speed = (1.05 + (stage - 1) * 0.05).coerceAtMost(2.6)
        val knives = (5 + (stage - 1) / 2).coerceAtMost(10)
        val slack = (0.55 - (stage - 1) * 0.012).coerceAtLeast(0.16)
        val obs = ((stage - 1) / 2).coerceAtMost(8)
        val picks = when {
            stage % 4 == 0 -> 2
            stage % 2 == 0 -> 1
            else -> 0
        }
        val wood = if (stage % 6 == 0) Wood.entries[1 + (stage / 6) % (Wood.entries.size - 1)] else Wood.OAK
        return spaced(
            knives = knives, obstacles = obs, slack = slack, pattern = pattern, speed = speed,
            pickups = picks, requirePickups = false, wood = wood, boss = false, challenge = null, salt = stage,
        ).also { it.validate() }
    }

    fun levelSpec(level: Int): StageSpec {
        require(level in 1..LEVEL_COUNT)
        val boss = level % 5 == 0
        val challenge = if (!boss && level % 4 == 0) Challenge.entries[(level / 4 - 1) % Challenge.entries.size] else null
        val spec = when {
            boss -> bossSpec(level)
            challenge != null -> challengeSpec(level, challenge)
            else -> normalSpec(level)
        }
        spec.validate()
        return spec
    }

    fun newGame(seed: Long): ThrowState = ThrowState(
        seed = seed, mode = Mode.ENDLESS, phase = Phase.MENU, level = 1,
        stars = List(LEVEL_COUNT) { 0 },
    )

    private fun normalSpec(n: Int): StageSpec {
        val pattern = Pattern.entries[(n - 1) % Pattern.entries.size]
        val knives = (4 + n / 8).coerceAtMost(8)
        val speed = 0.95 + n * 0.022
        val slack = (0.62 - n * 0.008).coerceAtLeast(0.18)
        val picks = when (n % 3) {
            0 -> 2
            2 -> 1
            else -> 0
        }
        return spaced(knives, n / 7, slack, pattern, speed, picks, false, Wood.OAK, false, null, n)
    }

    private fun bossSpec(n: Int): StageSpec {
        val wood = when (n) {
            5 -> Wood.IRONWOOD
            10 -> Wood.CHARRED
            15 -> Wood.APPLEWOOD
            20 -> Wood.BIRCH
            25 -> Wood.SPIRAL
            30 -> Wood.GOLDEN
            35 -> Wood.CRACKED
            40 -> Wood.HEARTWOOD
            else -> Wood.RINGED
        }
        val pattern = when (n) {
            5 -> Pattern.STEADY
            10 -> Pattern.REVERSE
            15 -> Pattern.WOBBLE
            20 -> Pattern.STOP_GO
            25 -> Pattern.ACCEL
            30 -> Pattern.REVERSAL
            35 -> Pattern.DECEL
            40 -> Pattern.STOP_GO
            else -> Pattern.REVERSAL
        }
        val knives = (6 + n / 10).coerceAtMost(10)
        val speed = 1.15 + n * 0.018
        return spaced(knives, 2 + n / 15, 0.22, pattern, speed, if (n % 10 == 5) 2 else 1, false, wood, true, null, n)
    }

    private fun challengeSpec(n: Int, kind: Challenge): StageSpec = when (kind) {
        Challenge.EXACT_GAPS -> packed(4, 0.05, Pattern.STEADY, 1.15, 0, false, Wood.OAK, kind, n)
        Challenge.FAST_REVERSE -> spaced(6, 3, 0.20, Pattern.REVERSAL, 2.15, 1, false, Wood.CHARRED, false, kind, n)
        Challenge.TINY_TARGET -> packed(1, 0.04, Pattern.STEADY, 1.05, 0, false, Wood.BIRCH, kind, n)
        Challenge.MUST_HIT -> packed(3, 0.07, Pattern.STEADY, 1.15, 3, true, Wood.APPLEWOOD, kind, n)
    }

    /** A few obstacles spaced through the wood, leaving one guaranteed window of half-width [slack]. */
    private fun spaced(
        knives: Int, obstacles: Int, slack: Double, pattern: Pattern, speed: Double,
        pickups: Int, requirePickups: Boolean, wood: Wood, boss: Boolean, challenge: Challenge?, salt: Int,
    ): StageSpec {
        val forbid = angularWidth()
        val gap = forbid + slack * 2
        val count = obstacles.coerceAtLeast(0).let { want ->
            val room = TAU - gap
            val fit = if (room <= forbid) 0 else (room / (forbid * 1.08)).toInt()
            want.coerceAtMost(fit)
        }
        val centers = if (count == 0) emptyList() else {
            val step = (TAU - gap) / count
            val start = norm(gap / 2)
            List(count) { i -> norm(start + step * (i + 0.5)) }
        }
        val picks = List(pickups) { i ->
            val mid = (pickups - 1) / 2.0
            PickupSpec(norm((i - mid) * slack * 0.65), if (i % 2 == 0) PickupKind.APPLE else PickupKind.COIN)
        }
        return StageSpec(
            knives, centers, picks, program(pattern, speed, salt), requirePickups, wood, pattern, boss, challenge,
        )
    }

    /** [windows] blade-sized openings, with the rest of the rim packed solid. */
    private fun packed(
        windows: Int, slack: Double, pattern: Pattern, speed: Double,
        pickups: Int, requirePickups: Boolean, wood: Wood, challenge: Challenge, salt: Int,
    ): StageSpec {
        val forbid = angularWidth()
        val centers = List(windows) { i -> TAU * i / windows }
        val obstacles = packedObstacles(centers, slack, forbid)
        val picks = List(pickups.coerceAtMost(windows)) { i ->
            PickupSpec(centers[i], if (i % 2 == 0) PickupKind.APPLE else PickupKind.COIN)
        }
        return StageSpec(
            windows, obstacles, picks, program(pattern, speed, salt), requirePickups, wood, pattern, false, challenge,
        )
    }

    private fun packedObstacles(centers: List<Double>, slack: Double, forbid: Double): List<Double> {
        val keepOut = forbid + slack
        val step = forbid * 1.06
        val out = ArrayList<Double>()
        var a = step / 2
        while (a < TAU - step * 0.35) {
            if (centers.none { separation(a, it) < keepOut }) out += norm(a)
            a += step
        }
        return out
    }

    fun program(pattern: Pattern, speed: Double, salt: Int = 1): RotationProgram {
        val w = speed
        val segs = when (pattern) {
            Pattern.STEADY -> listOf(SpinSeg(2400, w, w, Ease.LINEAR))
            Pattern.REVERSE -> listOf(SpinSeg(2400, -w, -w, Ease.LINEAR))
            Pattern.ACCEL -> listOf(
                SpinSeg(1100, w * 0.35, w * 1.55, Ease.IN),
                SpinSeg(700, w * 1.55, w * 0.35, Ease.OUT),
            )
            Pattern.DECEL -> listOf(
                SpinSeg(900, w * 1.45, w * 0.2, Ease.OUT),
                SpinSeg(500, w * 0.2, w * 0.2, Ease.LINEAR),
                SpinSeg(800, w * 0.2, w * 1.45, Ease.IN),
            )
            Pattern.STOP_GO -> listOf(
                SpinSeg(1500, w, w, Ease.LINEAR),
                SpinSeg(420, w, 0.0, Ease.OUT),
                SpinSeg(320, 0.0, 0.0, Ease.LINEAR),
                SpinSeg(480, 0.0, w, Ease.IN),
            )
            // Rock back and forth by a large arc, then creep forward so every window comes around.
            Pattern.WOBBLE -> listOf(
                SpinSeg(1600, w * 2.4, -w * 2.4, Ease.SMOOTH),
                SpinSeg(1600, -w * 2.4, w * 2.4, Ease.SMOOTH),
                SpinSeg(900, w * 0.8, w * 0.8, Ease.LINEAR),
            )
            Pattern.REVERSAL -> reversal(w, salt)
        }
        return RotationProgram(segs)
    }

    /** Irregular forward and backward coasts. The sign pattern is fixed for [salt]. */
    private fun reversal(speed: Double, salt: Int): List<SpinSeg> {
        val segs = ArrayList<SpinSeg>()
        var w = speed
        repeat(6) { i ->
            val sign = if (mix(salt, i) % 2 == 0) 1.0 else -1.0
            val mag = 0.7 + (mix(salt, i + 5) % 50) / 100.0
            val next = sign * speed * mag
            val dur = 280 + mix(salt, i + 11) % 220
            segs += SpinSeg(dur, w, next, Ease.SMOOTH)
            w = next
        }
        // A forward coast so a full turn still comes around, whatever the signs did.
        segs += SpinSeg(1400, speed, speed, Ease.LINEAR)
        return segs
    }

    private fun mix(a: Int, b: Int): Int {
        var x = a * 0x9E3779B9.toInt() xor b * 0x85EBCA6B.toInt()
        x = x xor (x ushr 16)
        x *= 0x7FEB352D
        x = x xor (x ushr 15)
        return x and 0x7FFFFFFF
    }
}

enum class Ease { LINEAR, IN, OUT, SMOOTH }

enum class Pattern { STEADY, REVERSE, ACCEL, DECEL, STOP_GO, WOBBLE, REVERSAL }

enum class Wood { OAK, IRONWOOD, CHARRED, APPLEWOOD, BIRCH, SPIRAL, GOLDEN, CRACKED, HEARTWOOD, RINGED }

enum class Challenge { EXACT_GAPS, FAST_REVERSE, TINY_TARGET, MUST_HIT }

enum class PickupKind { APPLE, COIN }

enum class Phase { MENU, SELECT, AIM, FLIGHT, FAIL, CLEAR }

enum class Mode { ENDLESS, LEVELS }

/** One piece of a spin: angular velocity eases from [omegaStart] to [omegaEnd] over [durationMs]. */
data class SpinSeg(val durationMs: Int, val omegaStart: Double, val omegaEnd: Double, val ease: Ease) {
    init { require(durationMs > 0 && omegaStart.isFinite() && omegaEnd.isFinite()) }

    /** Radians added over the first [ms] of this segment. */
    fun delta(ms: Long): Double {
        val u = (ms.toDouble() / durationMs).coerceIn(0.0, 1.0)
        val dw = omegaEnd - omegaStart
        val area = when (ease) {
            Ease.LINEAR -> u * u / 2
            Ease.IN -> u * u * u / 3
            Ease.OUT -> u * u - u * u * u / 3
            Ease.SMOOTH -> u * u * u - u * u * u * u / 2
        }
        return durationMs / 1000.0 * (omegaStart * u + dw * area)
    }

    fun omega(ms: Long): Double {
        val u = (ms.toDouble() / durationMs).coerceIn(0.0, 1.0)
        val e = when (ease) {
            Ease.LINEAR -> u
            Ease.IN -> u * u
            Ease.OUT -> 1 - (1 - u) * (1 - u)
            Ease.SMOOTH -> u * u * (3 - 2 * u)
        }
        return omegaStart + (omegaEnd - omegaStart) * e
    }
}

data class RotationProgram(val segments: List<SpinSeg>, val loop: Boolean = true) {
    init { require(segments.isNotEmpty()) }
    val durationMs: Int = segments.sumOf { it.durationMs }
    val netRadians: Double = integrate(durationMs.toLong())

    fun angleAt(timeMs: Long): Double {
        val t = timeMs.coerceAtLeast(0)
        val d = durationMs.toLong()
        val loops = if (loop) t / d else 0L
        val into = if (loop) t % d else min(t, d)
        return loops * netRadians + integrate(into)
    }

    fun omegaAt(timeMs: Long): Double {
        val t = timeMs.coerceAtLeast(0)
        val into = if (loop) t % durationMs else min(t, durationMs.toLong())
        if (!loop && t >= durationMs) return segments.last().omega(segments.last().durationMs.toLong())
        var left = into
        for (seg in segments) {
            val dur = seg.durationMs.toLong()
            if (left <= dur) return seg.omega(left)
            left -= dur
        }
        return segments.last().omegaEnd
    }

    private fun integrate(ms: Long): Double {
        var left = ms
        var acc = 0.0
        for (seg in segments) {
            if (left <= 0) break
            val take = min(left, seg.durationMs.toLong())
            acc += seg.delta(take)
            left -= take
        }
        return acc
    }
}

data class PickupSpec(val angle: Double, val kind: PickupKind)

data class StageSpec(
    val knives: Int,
    val obstacles: List<Double>,
    val pickups: List<PickupSpec>,
    val program: RotationProgram,
    val requirePickups: Boolean,
    val wood: Wood,
    val pattern: Pattern,
    val boss: Boolean,
    val challenge: Challenge?,
) {
    fun validate() {
        require(knives in 1..16)
        val width = LogThrow.angularWidth()
        obstacles.forEach { a ->
            obstacles.forEach { b ->
                if (a != b) require(LogThrow.separation(a, b) > width - 1e-9) { "obstacles overlap" }
            }
        }
        val open = (0 until 720).any { i ->
            val a = LogThrow.TAU * i / 720
            obstacles.none { LogThrow.collides(a, it) }
        }
        require(open) { "no gap for a dagger" }
        pickups.forEach { p -> require(obstacles.none { LogThrow.collides(p.angle, it) }) { "pickup sits on a dagger" } }
        if (requirePickups) require(pickups.isNotEmpty())
    }
}

data class StuckKnife(val local: Double, val player: Boolean)

data class Chip(val born: Long, val dir: Double, val speed: Double, val spin: Double, val kind: Int)

data class ThrowState(
    val seed: Long,
    val mode: Mode,
    val phase: Phase,
    val level: Int,
    val timeMs: Long = 0,
    val flightStartMs: Long = 0,
    val eventMs: Long = 0,
    val stuck: List<StuckKnife> = emptyList(),
    val thrown: Int = 0,
    val taken: Set<Int> = emptySet(),
    val knivesStuck: Int = 0,
    val stagesCleared: Int = 0,
    val pickupsTaken: Int = 0,
    val best: Int = 0,
    val stars: List<Int> = emptyList(),
    val earned: Int = 0,
    val attempt: Int = 0,
    val fx: List<Chip> = emptyList(),
) {
    val score: Int get() = LogThrow.score(knivesStuck, stagesCleared, pickupsTaken)
    val clearedLevels: Int get() = stars.count { it > 0 }
    val starTotal: Int get() = stars.sum()

    fun spec(): StageSpec = if (mode == Mode.ENDLESS) LogThrow.endlessSpec(level) else LogThrow.levelSpec(level)

    fun unlocked(n: Int): Boolean = n == 1 || (n in 2..LogThrow.LEVEL_COUNT && stars.getOrElse(n - 2) { 0 } > 0)

    fun throwKnife(): ThrowState? {
        if (phase != Phase.AIM) return null
        if (thrown >= spec().knives) return null
        return copy(phase = Phase.FLIGHT, flightStartMs = timeMs, thrown = thrown + 1)
    }

    fun advance(dtMs: Long): ThrowState {
        if (dtMs <= 0) return this
        if (phase == Phase.MENU || phase == Phase.SELECT) return this
        // A miss is over, but the clock keeps moving so the bounce and sparks can play out.
        if (phase == Phase.FAIL) return copy(timeMs = timeMs + dtMs).decay()
        var s = this
        var left = dtMs
        var guard = 0
        while (left > 0 && guard++ < 6) {
            when (s.phase) {
                Phase.FLIGHT -> {
                    val arrive = s.flightStartMs + LogThrow.FLIGHT_MS
                    if (s.timeMs >= arrive) {
                        s = s.resolve()
                        continue
                    }
                    if (s.timeMs + left < arrive) return s.copy(timeMs = s.timeMs + left).decay()
                    val step = arrive - s.timeMs
                    s = s.copy(timeMs = arrive).resolve()
                    left -= step
                }
                Phase.CLEAR -> {
                    val end = s.eventMs + LogThrow.BURST_MS
                    if (s.timeMs >= end) {
                        s = s.finishBurst()
                        continue
                    }
                    if (s.timeMs + left < end) return s.copy(timeMs = s.timeMs + left).decay()
                    val step = end - s.timeMs
                    s = s.copy(timeMs = end).finishBurst()
                    left -= step
                }
                Phase.AIM -> return s.copy(timeMs = s.timeMs + left).decay()
                else -> return s
            }
        }
        return s.decay()
    }

    fun beginEndless(): ThrowState = copy(
        mode = Mode.ENDLESS, phase = Phase.AIM, level = 1, timeMs = 0, flightStartMs = 0, eventMs = 0,
        stuck = LogThrow.endlessSpec(1).obstacleKnives(), thrown = 0, taken = emptySet(),
        knivesStuck = 0, stagesCleared = 0, pickupsTaken = 0, earned = 0, attempt = attempt + 1, fx = emptyList(),
    )

    fun beginLevels(): ThrowState = copy(
        mode = Mode.LEVELS, phase = Phase.SELECT, timeMs = 0, flightStartMs = 0, eventMs = 0,
        stuck = emptyList(), thrown = 0, taken = emptySet(), earned = 0, fx = emptyList(),
    )

    fun beginLevel(n: Int): ThrowState? {
        if (n !in 1..LogThrow.LEVEL_COUNT || !unlocked(n)) return null
        if (phase != Phase.SELECT && phase != Phase.FAIL && phase != Phase.MENU) return null
        val spec = LogThrow.levelSpec(n)
        return copy(
            mode = Mode.LEVELS, phase = Phase.AIM, level = n, timeMs = 0, flightStartMs = 0, eventMs = 0,
            stuck = spec.obstacleKnives(), thrown = 0, taken = emptySet(),
            knivesStuck = 0, stagesCleared = 0, pickupsTaken = 0, earned = 0, attempt = attempt + 1, fx = emptyList(),
        )
    }

    /** Endless game over: keep the best and start stage 1 again. */
    fun again(): ThrowState = beginEndless().copy(best = best)

    /** Leave a level or a failed throw for the level map. Stars already earned stay. */
    fun toLevels(): ThrowState = copy(
        mode = Mode.LEVELS, phase = Phase.SELECT, timeMs = 0, flightStartMs = 0, eventMs = 0,
        stuck = emptyList(), thrown = 0, taken = emptySet(), earned = 0, fx = emptyList(),
        knivesStuck = 0, stagesCleared = 0, pickupsTaken = 0,
    )

    fun toMenu(): ThrowState = copy(
        phase = Phase.MENU, timeMs = 0, flightStartMs = 0, eventMs = 0,
        stuck = emptyList(), thrown = 0, taken = emptySet(), earned = 0, fx = emptyList(),
        knivesStuck = 0, stagesCleared = 0, pickupsTaken = 0,
    )

    /** Drop an in-flight dagger so a save is always a settled position. */
    fun stable(): ThrowState = if (phase == Phase.FLIGHT) copy(phase = Phase.AIM, thrown = thrown - 1, flightStartMs = 0) else this

    /** What a dagger thrown at [throwAt] meets when it arrives. */
    fun preview(throwAt: Long): Impact {
        val spec = spec()
        val arrive = throwAt + LogThrow.FLIGHT_MS
        val theta = spec.program.angleAt(arrive)
        val blocked = stuck.any { LogThrow.collides(0.0, LogThrow.norm(theta + it.local)) }
        val hits = spec.pickups.indices.filter { i ->
            i !in taken && LogThrow.separation(0.0, LogThrow.norm(theta + spec.pickups[i].angle)) <= LogThrow.PICKUP_REACH
        }
        return Impact(arrive, theta, blocked, hits)
    }

    private fun resolve(): ThrowState {
        val spec = spec()
        val hit = preview(flightStartMs)
        if (hit.blocked) return fail()
        val local = LogThrow.norm(-hit.theta)
        val stuck1 = stuck + StuckKnife(local, true)
        val taken1 = taken + hit.pickups
        val knives1 = knivesStuck + 1
        val picks1 = pickupsTaken + hit.pickups.size
        val done = thrown >= spec.knives
        if (done && spec.requirePickups && taken1.size < spec.pickups.size) return fail()
        val stages1 = if (done && mode == Mode.ENDLESS) stagesCleared + 1 else stagesCleared
        val earned1 = if (done && mode == Mode.LEVELS) LogThrow.starsFor(taken1.size, spec.pickups.size) else 0
        val stars1 = if (earned1 > 0) stars.mapIndexed { i, old -> if (i == level - 1) maxOf(old, earned1) else old } else stars
        val chips = burstChips(timeMs, if (done) 14 else 8)
        return copy(
            phase = if (done) Phase.CLEAR else Phase.AIM,
            eventMs = if (done) timeMs else eventMs,
            stuck = stuck1, taken = taken1, knivesStuck = knives1, stagesCleared = stages1,
            pickupsTaken = picks1, earned = if (done) earned1 else earned, stars = stars1,
            fx = (fx + chips).takeLast(24),
        )
    }

    private fun fail(): ThrowState {
        val banked = if (mode == Mode.ENDLESS) maxOf(best, score) else best
        return copy(
            phase = Phase.FAIL, eventMs = timeMs, best = banked, earned = 0,
            fx = (fx + burstChips(timeMs, 10, spark = true)).takeLast(24),
        )
    }

    private fun finishBurst(): ThrowState = when (mode) {
        Mode.ENDLESS -> {
            val next = level + 1
            copy(
                phase = Phase.AIM, level = next, timeMs = 0, flightStartMs = 0, eventMs = 0,
                stuck = LogThrow.endlessSpec(next).obstacleKnives(), thrown = 0, taken = emptySet(),
                earned = 0, fx = emptyList(),
            )
        }
        Mode.LEVELS -> toLevels()
    }

    private fun decay(): ThrowState {
        val live = fx.filter { timeMs - it.born < 760 }
        return if (live.size == fx.size) this else copy(fx = live)
    }

    private fun burstChips(now: Long, count: Int, spark: Boolean = false): List<Chip> = List(count) { i ->
        val dir = LogThrow.norm(i * 2.399963229728653 + if (spark) 0.4 else 0.0)
        Chip(now, dir, 0.45 + (i % 4) * 0.12, (i - count / 2.0) * 0.35, if (spark) 1 else i % 3)
    }
}

private fun StageSpec.obstacleKnives(): List<StuckKnife> = obstacles.map { StuckKnife(LogThrow.norm(it), false) }

data class Impact(val arriveMs: Long, val theta: Double, val blocked: Boolean, val pickups: List<Int>)

/**
 * Text save. Version 1. An in-flight dagger is rewound so the save is a settled board.
 * Broken text decodes as null.
 */
object LogThrowCodec {
    fun encode(g: ThrowState): String {
        val s = g.stable()
        val players = s.stuck.filter { it.player }.joinToString(",") { "%.6f".format(Locale.US, it.local) }
        return listOf(
            "1", s.seed.toString(), s.mode.name, s.phase.name, s.level.toString(), s.timeMs.toString(),
            s.eventMs.toString(), s.thrown.toString(), players, s.taken.sorted().joinToString(","),
            s.knivesStuck.toString(), s.stagesCleared.toString(), s.pickupsTaken.toString(), s.best.toString(),
            s.stars.joinToString(","), s.earned.toString(), s.attempt.toString(),
        ).joinToString("\n")
    }

    fun decode(text: String): ThrowState? = try {
        val l = text.split('\n')
        require(l.size == 17 && l[0] == "1")
        val mode = Mode.valueOf(l[2])
        val phase = Phase.valueOf(l[3])
        require(phase != Phase.FLIGHT)
        val level = l[4].toInt()
        val stars = if (l[14].isEmpty()) emptyList() else l[14].split(',').map { it.toInt() }
        require(stars.size == LogThrow.LEVEL_COUNT && stars.all { it in 0..3 })
        val taken = if (l[9].isEmpty()) emptySet() else l[9].split(',').map { it.toInt() }.toSet()
        val players = if (l[8].isEmpty()) emptyList() else l[8].split(',').map { it.toDouble() }
        var state = ThrowState(
            seed = l[1].toLong(), mode = mode, phase = phase, level = level,
            timeMs = l[5].toLong(), eventMs = l[6].toLong(), thrown = l[7].toInt(), taken = taken,
            knivesStuck = l[10].toInt(), stagesCleared = l[11].toInt(), pickupsTaken = l[12].toInt(),
            best = l[13].toInt(), stars = stars, earned = l[15].toInt(), attempt = l[16].toInt(),
        )
        if (phase == Phase.AIM || phase == Phase.FAIL || phase == Phase.CLEAR) {
            val spec = state.spec()
            val expected = if (phase == Phase.FAIL) state.thrown - 1 else state.thrown
            require(expected >= 0 && players.size == expected && expected <= spec.knives)
            require(taken.all { it in spec.pickups.indices })
            state = state.copy(stuck = spec.obstacleKnives() + players.map { StuckKnife(LogThrow.norm(it), true) })
        } else {
            require(players.isEmpty() && state.thrown == 0)
        }
        require(state.knivesStuck >= 0 && state.best >= 0 && state.level >= 1)
        state
    } catch (_: IllegalArgumentException) {
        null
    }
}
