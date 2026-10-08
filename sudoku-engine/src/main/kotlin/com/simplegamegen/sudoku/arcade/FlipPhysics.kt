package com.simplegamegen.sudoku.arcade

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rigid-body toss used by Knife Flip.
 *
 * Distances are metres, mass kilograms, time seconds. Gravity is 9.81 m/s² downward.
 * Rotation is in the vertical plane: [theta] 0 points the tip (or the bottle cap) straight up,
 * and increasing [theta] swings that end toward +x. The body rotates about its centre of mass.
 * A fixed step of 1/240 s keeps the motion deterministic.
 *
 * Knives are rigid. A bottle's water can slosh: while it is spinning the free surface lets the
 * water climb, which raises the moment of inertia and, by conservation of angular momentum, slows
 * the spin. A full bottle has no air gap, so the water cannot move.
 */
object FlipPhysics {
    const val GRAVITY = 9.81f
    const val DT = 1f / 240f

    /** Wood block the point can stick in, and the bottle can land on. Metres, along the toss. */
    const val BLOCK_X0 = 0f
    const val BLOCK_X1 = 0.58f

    private const val G = -GRAVITY
    private const val AIR = 0.08f
    private const val AIR_SPIN = 0.045f
    private const val MAX_TIME = 2.8f
    private const val MAX_BOUNCES = 10

    /** How far the point may lean from straight down and still bite. */
    private const val STICK_COS = 0.8192f // cos 35°
    private const val STICK_MIN = 0.85f
    private const val STICK_MAX = 8.5f

    /** One flip, not a drop and not two turns. Radians of rotation before landing. */
    private const val FLIP_MIN = 4.9f
    private const val FLIP_MAX = 8.0f

    private const val SLOSH_TAU = 0.06f
    private const val SLOSH_RATE = 8f
    /** Extra spin loss while water is free to move, scaled by how much water can move. */
    private const val SLOSH_DAMP = 0.85f
    /** A loose hold on the neck. A rigid grip would spin any bottle too fast to land. */
    private const val LOOSE_GRIP = 0.26f
    /** Scales hand speed into bottle centre-of-mass speed. Heavier fills leave more slowly. */
    private const val BOTTLE_THROW = 0.12f
    /** Extra height of the bottle centre of mass above its base, in the hand. */
    private const val BOTTLE_LIFT = 0.36f

    /**
     * Centre-of-mass height under the same integrator as a toss, with collisions ignored.
     * [drag] off matches ½gt² exactly, apart from float rounding.
     */
    fun comHeight(z0: Float, vz0: Float, seconds: Float, drag: Boolean = false): Float {
        var z = z0
        var vz = vz0
        var t = 0f
        while (t + 1e-6f < seconds) {
            val h = minOf(DT, seconds - t)
            val az = G - (if (drag) AIR else 0f) * vz
            z += vz * h + 0.5f * az * h * h
            vz += az * h
            t += h
        }
        return z
    }

    /**
     * Same hand speed at the grip for every model. [aim] is the direction of that speed,
     * in radians up from +x (π/2 is straight up). Heavier models and models whose balance
     * sits nearer the grip leave the hand with a different spin.
     */
    fun flick(body: FlipBody, theta: Float, gripSpeed: Float, aim: Float, x: Float, z: Float): Release {
        // A bottle is held loosely at the neck. The arm throws the centre of mass; only a fraction of
        // the moment gets through the loose grip. The fraction is the same at every fill, so a heavier
        // bottle, a higher balance, and a larger inertia still change the flight. A knife is held firmly,
        // so the grip point itself leaves at [gripSpeed].
        if (body.bottle) return looseBottle(body, theta, gripSpeed, aim, x, z)
        val com = body.comFromTip(0f)
        val lever = body.gripFromTip - com
        val dirX = sin(theta)
        val dirZ = cos(theta)
        val rx = -lever * dirX
        val rz = -lever * dirZ
        val ux = cos(aim)
        val uz = sin(aim)
        val tau = ux * rz - uz * rx
        val inertia = body.inertia(0f)
        val denom = 1f / body.mass + tau * tau / inertia
        val impulse = gripSpeed / denom
        return Release(
            x, z,
            ux * impulse / body.mass,
            uz * impulse / body.mass,
            theta,
            impulse * tau / inertia,
        )
    }

    /**
     * Same throw for every fill: impulse grows with mass so a light bottle is not fired across the room,
     * then a loose neck grip passes [LOOSE_GRIP] of r × J into the spin.
     */
    private fun looseBottle(body: FlipBody, theta: Float, gripSpeed: Float, aim: Float, x: Float, z: Float): Release {
        val throwV = gripSpeed * (BOTTLE_THROW / body.mass)
        val ux = cos(aim)
        val uz = sin(aim)
        val lever = body.gripFromTip - body.comFromTip(0f)
        val omega = (body.mass * throwV) * lever / body.inertia(0f) * LOOSE_GRIP
        return Release(x, z, throwV * ux, throwV * uz, theta, omega)
    }

    /** Spin left after [seconds] in the air, starting from [omega]. No collision. */
    fun coastOmega(body: FlipBody, omega: Float, seconds: Float): Float {
        var s = State(0f, 8f, 0f, 0f, 0.3f, omega, 0f)
        var t = 0f
        while (t + 1e-6f < seconds) {
            val h = minOf(DT, seconds - t)
            s = step(body, s, h, drag = true)
            t += h
        }
        return s.omega
    }

    fun hold(body: FlipBody): Release {
        val theta = if (body.bottle) BOTTLE_THETA else KNIFE_THETA
        val z = if (body.bottle) body.comFromBase(0f) + BOTTLE_LIFT else 0.24f
        val x = if (body.bottle) 0.12f else 0.10f
        return Release(x, z, 0f, 0f, theta, 0f)
    }

    fun simulate(body: FlipBody, release: Release): Flight {
        var s = State(release.x, release.z, release.vx, release.vz, release.theta, release.omega, release.sigma)
        var t = 0f
        var bounces = 0
        var reason = FlipEnd.MISS
        val samples = ArrayList<Sample>(200)
        fun take() { samples += s.sample(t) }
        take()
        var since = 0f
        while (t < MAX_TIME && bounces <= MAX_BOUNCES) {
            val next = step(body, s, DT, drag = true)
            val hit = lowest(body, next)
            if (hit.z <= 0f && pointSpeed(body, next, hit).z < 0.02f) {
                var lo = 0f
                var hi = DT
                repeat(10) {
                    val mid = (lo + hi) * 0.5f
                    val trial = step(body, s, mid, drag = true)
                    if (lowest(body, trial).z <= 0f) hi = mid else lo = mid
                }
                val at = step(body, s, hi, drag = true)
                val resolved = resolve(body, at, release.theta)
                t += hi
                samples += resolved.state.sample(t)
                if (resolved.end != null) return Flight(resolved.end, t, samples, resolved.state.sample(t))
                reason = resolved.reason
                s = resolved.state
                bounces++
                since = 0f
                continue
            }
            s = next
            t += DT
            since += DT
            if (since >= 1f / 60f) { take(); since = 0f }
            if (s.z > 12f || s.x < -1.2f || s.x > 2.2f) return Flight(FlipEnd.OFF, t, samples, s.sample(t))
        }
        if (samples.isEmpty() || samples.last().t < t) samples += s.sample(t)
        return Flight(reason, t, samples, s.sample(t))
    }

    private data class Hit(val x: Float, val z: Float, val along: Float, val kind: HitKind)
    private enum class HitKind { TIP, BLADE, HANDLE, BASE, SIDE, CAP }

    private data class Resolved(val state: State, val end: FlipEnd?, val reason: FlipEnd)

    private fun resolve(body: FlipBody, s0: State, theta0: Float): Resolved {
        val hit = lowest(body, s0)
        val onBlock = hit.x in BLOCK_X0..BLOCK_X1
        if (!onBlock) {
            val bounced = bounce(body, s0, hit, 0.35f, 0.25f)
            return Resolved(bounced, null, FlipEnd.OFF)
        }
        if (!body.bottle) {
            val kind = knifeContact(body, s0)
            val speed = hypot(pointSpeed(body, s0, hitAt(body, s0, 0f)).x, pointSpeed(body, s0, hitAt(body, s0, 0f)).z)
            val down = -cos(s0.theta)
            val stick = kind == HitKind.TIP && down >= STICK_COS && speed in STICK_MIN..STICK_MAX
            if (stick) {
                val planted = plantKnife(body, s0)
                return Resolved(planted, FlipEnd.STUCK, FlipEnd.STUCK)
            }
            val why = when {
                kind == HitKind.HANDLE -> FlipEnd.HANDLE
                kind == HitKind.BLADE -> FlipEnd.FLAT
                speed < STICK_MIN -> FlipEnd.WEAK
                speed > STICK_MAX -> FlipEnd.HARD
                else -> FlipEnd.FLAT
            }
            return Resolved(bounce(body, s0, hit, 0.42f, 0.32f), null, why)
        }
        val base = hit.kind == HitKind.BASE
        val bounced = splash(body, bounce(body, s0, hit, 0.22f, 0.48f), base)
        if (base && upright(body, bounced, theta0)) {
            val settled = settleBottle(body, bounced)
            return Resolved(settled, FlipEnd.LANDED, FlipEnd.LANDED)
        }
        val why = if (base) FlipEnd.TIPPED else FlipEnd.TIPPED
        return Resolved(bounced, null, why)
    }

    /** Point-first means the tip is strictly the lowest part, not the flat or the handle. */
    private fun knifeContact(body: FlipBody, s: State): HitKind {
        val tip = point(body, s, 0f)
        val behind = point(body, s, 0.045f)
        val butt = point(body, s, body.length)
        val tipLowest = tip.z < behind.z - 0.004f && tip.z <= butt.z + 0.001f
        if (tipLowest && lowest(body, s).along <= body.tipZone) return HitKind.TIP
        val handleFrom = body.length - body.handleZone
        if (lowest(body, s).along >= handleFrom) return HitKind.HANDLE
        return HitKind.BLADE
    }

    private fun upright(body: FlipBody, s: State, theta0: Float): Boolean {
        val tilt = wrap(s.theta)
        val h = body.comFromBase(s.sigma).coerceAtLeast(0.02f)
        val alpha = atan(body.baseRadius / h)
        if (abs(tilt) >= alpha) return false
        val reach = hypot(body.baseRadius, h)
        val barrier = body.mass * GRAVITY * reach * (1f - cos((alpha - abs(tilt)).coerceAtLeast(0f)))
        val spin = 0.5f * body.inertia(s.sigma) * s.omega * s.omega
        val turned = abs(s.theta - theta0)
        return spin <= barrier * 1.08f && turned in FLIP_MIN..FLIP_MAX && abs(s.vz) < 0.85f
    }

    private fun plantKnife(body: FlipBody, s: State): State {
        val tip = point(body, s, 0f)
        return s.copy(
            x = s.x - tip.x + tip.x.coerceIn(BLOCK_X0 + 0.01f, BLOCK_X1 - 0.01f),
            z = s.z - tip.z - 0.004f,
            vx = 0f, vz = 0f, omega = 0f,
        )
    }

    /**
     * A base hit dumps the water that had climbed the walls. That splash takes spin with it
     * and drops the balance back toward the base. A full bottle has no free surface, so nothing
     * is lost here and the same arrival keeps turning.
     */
    private fun splash(body: FlipBody, s: State, base: Boolean): State {
        if (!base || body.mobility < 1e-3f) return s
        val absorb = (0.72f * body.mobility * (body.waterMass / 0.12f).coerceAtMost(1.4f)).coerceIn(0f, 0.88f)
        return s.copy(omega = s.omega * (1f - absorb), sigma = s.sigma * (1f - absorb))
    }

    private fun settleBottle(body: FlipBody, s: State): State {
        val h = body.comFromBase(s.sigma)
        return s.copy(z = h, vx = 0f, vz = 0f, omega = 0f, theta = wrap(s.theta) * 0.15f)
    }

    /**
     * Impulse from a horizontal surface. [e] is restitution, [mu] friction.
     * The normal is +z. Rotation follows r × J with the same sign as [flick].
     */
    private fun bounce(body: FlipBody, s: State, hit: Hit, e: Float, mu: Float): State {
        val r = relative(body, s, hit.along, hit.kind)
        val vc = pointVelocity(s, r.first, r.second)
        val inertia = body.inertia(s.sigma).coerceAtLeast(1e-8f)
        val rx = r.first
        val rz = r.second
        val normalDenom = 1f / body.mass + rx * rx / inertia
        val jn = if (vc.z < 0f) -(1f + e) * vc.z / normalDenom else 0f
        val tangentDenom = 1f / body.mass + rz * rz / inertia
        val jtWant = -vc.x / tangentDenom
        val cap = mu * jn
        val jt = jtWant.coerceIn(-cap, cap)
        val omega = s.omega + (jt * rz - jn * rx) / inertia
        val lift = if (hit.z < 0f) -hit.z + 0.0008f else 0f
        return s.copy(
            z = s.z + lift,
            vx = s.vx + jt / body.mass,
            vz = s.vz + jn / body.mass,
            omega = omega,
        )
    }

    private fun step(body: FlipBody, s: State, dt: Float, drag: Boolean): State {
        var omega = s.omega
        var sigma = s.sigma
        if (body.mobility > 1e-4f) {
            val target = (abs(omega) / SLOSH_RATE).coerceIn(0f, 1f) * body.mobility
            val sigma2 = sigma + (target - sigma) * (dt / SLOSH_TAU).coerceAtMost(1f)
            val i1 = body.inertia(sigma)
            val i2 = body.inertia(sigma2).coerceAtLeast(1e-8f)
            val damp = SLOSH_DAMP * body.mobility * (body.waterMass / 0.17f)
            omega = (i1 / i2) * omega * expNeg(damp * dt)
            sigma = sigma2.coerceIn(0f, 1f)
        }
        if (drag) omega *= expNeg(AIR_SPIN * dt)
        val k = if (drag) AIR else 0f
        val ax = -k * s.vx
        val az = G - k * s.vz
        return State(
            s.x + s.vx * dt + 0.5f * ax * dt * dt,
            s.z + s.vz * dt + 0.5f * az * dt * dt,
            s.vx + ax * dt,
            s.vz + az * dt,
            s.theta + omega * dt,
            omega,
            sigma,
        )
    }

    private fun lowest(body: FlipBody, s: State): Hit {
        if (!body.bottle) {
            var best = hitAt(body, s, 0f)
            var along = 0f
            val n = 18
            for (i in 1..n) {
                val a = body.length * i / n
                val p = hitAt(body, s, a)
                if (p.z < best.z) { best = p; along = a }
            }
            return best.copy(along = along, kind = HitKind.BLADE)
        }
        val base = body.length - body.comFromTip(s.sigma)
        val dirX = sin(s.theta)
        val dirZ = cos(s.theta)
        val bx = s.x - base * dirX
        val bz = s.z - base * dirZ
        val px = dirZ
        val pz = -dirX
        val r = body.baseRadius
        var best = Hit(bx + r * px, bz + r * pz, base, HitKind.BASE)
        fun keep(h: Hit) { if (h.z < best.z) best = h }
        keep(Hit(bx - r * px, bz - r * pz, base, HitKind.BASE))
        keep(Hit(bx, bz, base, HitKind.BASE))
        val cap = body.comFromTip(s.sigma)
        val cx = s.x + cap * dirX
        val cz = s.z + cap * dirZ
        keep(Hit(cx, cz, 0f, HitKind.CAP))
        val mid = base * 0.5f
        val mx = s.x - mid * dirX
        val mz = s.z - mid * dirZ
        keep(Hit(mx + r * px, mz + r * pz, mid, HitKind.SIDE))
        keep(Hit(mx - r * px, mz - r * pz, mid, HitKind.SIDE))
        return best
    }

    private fun hitAt(body: FlipBody, s: State, along: Float): Hit {
        val p = point(body, s, along)
        return Hit(p.x, p.z, along, HitKind.BLADE)
    }

    /** A point [along] metres from the tip, toward the handle. */
    private fun point(body: FlipBody, s: State, along: Float): Vec {
        val fromCom = body.comFromTip(s.sigma) - along
        return Vec(s.x + fromCom * sin(s.theta), s.z + fromCom * cos(s.theta))
    }

    private fun relative(body: FlipBody, s: State, along: Float, kind: HitKind): Pair<Float, Float> {
        if (!body.bottle || kind == HitKind.TIP || kind == HitKind.BLADE || kind == HitKind.HANDLE) {
            val fromCom = body.comFromTip(s.sigma) - along
            return fromCom * sin(s.theta) to fromCom * cos(s.theta)
        }
        val hit = lowest(body, s)
        return hit.x - s.x to hit.z - s.z
    }

    private fun pointSpeed(body: FlipBody, s: State, hit: Hit): Vec {
        val r = relative(body, s, hit.along, hit.kind)
        return pointVelocity(s, r.first, r.second)
    }

    /** Velocity of a point at (rx, rz) relative to the centre of mass. */
    private fun pointVelocity(s: State, rx: Float, rz: Float) = Vec(s.vx + s.omega * rz, s.vz - s.omega * rx)

    private fun wrap(theta: Float): Float {
        var a = theta % (2f * PI.toFloat())
        if (a > PI.toFloat()) a -= 2f * PI.toFloat()
        if (a < -PI.toFloat()) a += 2f * PI.toFloat()
        return a
    }

    private fun expNeg(x: Float): Float {
        val c = x.coerceIn(0f, 4f)
        return 1f / (1f + c + 0.5f * c * c)
    }

    const val KNIFE_THETA = 1.20f
    const val BOTTLE_THETA = 0.40f

    private data class Vec(val x: Float, val z: Float)
}

private data class State(
    val x: Float, val z: Float, val vx: Float, val vz: Float,
    val theta: Float, val omega: Float, val sigma: Float,
) {
    fun sample(t: Float) = Sample(t, x, z, theta, sigma)
}

enum class FlipEnd { STUCK, LANDED, HANDLE, FLAT, WEAK, HARD, TIPPED, OFF, MISS }

data class Release(
    val x: Float, val z: Float, val vx: Float, val vz: Float,
    val theta: Float, val omega: Float, val sigma: Float = 0f,
)

data class Sample(val t: Float, val x: Float, val z: Float, val theta: Float, val sigma: Float)

data class Flight(val end: FlipEnd, val time: Float, val samples: List<Sample>, val final: Sample) {
    fun at(seconds: Float): Sample {
        if (samples.isEmpty()) return final
        if (seconds <= samples.first().t) return samples.first()
        if (seconds >= samples.last().t) return final
        var lo = 0
        var hi = samples.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (samples[mid].t <= seconds) lo = mid else hi = mid
        }
        val a = samples[lo]
        val b = samples[hi]
        val span = (b.t - a.t).coerceAtLeast(1e-5f)
        val u = ((seconds - a.t) / span).coerceIn(0f, 1f)
        return Sample(seconds, a.x + (b.x - a.x) * u, a.z + (b.z - a.z) * u, a.theta + (b.theta - a.theta) * u, a.sigma + (b.sigma - a.sigma) * u)
    }
}

/**
 * A tossable object. [comFromTip] is measured from the knife's point, or from the bottle cap.
 * [inertia] is about the centre of mass, for rotation in the vertical plane.
 */
data class FlipBody(
    val id: String,
    val label: String,
    val bottle: Boolean,
    val length: Float,
    val mass: Float,
    val comPooled: Float,
    val comSpread: Float,
    val inertiaPooled: Float,
    val inertiaSpread: Float,
    val gripFromTip: Float,
    val tipZone: Float,
    val handleZone: Float,
    val baseRadius: Float,
    val mobility: Float,
    val waterMass: Float,
    val fill: Float,
) {
    fun comFromTip(sigma: Float) = comPooled + (comSpread - comPooled) * sigma.coerceIn(0f, 1f)
    fun inertia(sigma: Float) = inertiaPooled + (inertiaSpread - inertiaPooled) * sigma.coerceIn(0f, 1f)
    fun comFromBase(sigma: Float) = length - comFromTip(sigma)

    /** Fraction of the length from the tip (or cap) to the balance point, water settled. */
    val balance: Float get() = comPooled / length
}

private data class Part(val mass: Float, val fromTip: Float, val span: Float)

private fun knife(
    id: String,
    label: String,
    length: Float,
    parts: List<Part>,
    gripFromTip: Float,
    tipZone: Float = 0.028f,
    handleZone: Float = 0.09f,
): FlipBody {
    val mass = parts.sumOf { it.mass.toDouble() }.toFloat()
    val com = parts.sumOf { (it.mass * it.fromTip).toDouble() }.toFloat() / mass
    var inertia = 0.0
    for (p in parts) {
        val own = p.mass.toDouble() * p.span * p.span / 12.0
        val d = p.fromTip - com
        inertia += own + p.mass.toDouble() * d * d
    }
    return FlipBody(
        id, label, bottle = false, length, mass, com, com, inertia.toFloat(), inertia.toFloat(),
        gripFromTip, tipZone, handleZone, baseRadius = 0f, mobility = 0f, waterMass = 0f, fill = 0f,
    )
}

/**
 * A 500 mL PET bottle. The container is 12.5 g; the water is [fill] of 500 g.
 * Empty is not offered. Around one third full the free surface can still travel the
 * whole cavity, so the inertia rises and the spin falls. A full bottle cannot slosh.
 */
fun waterBottle(fill: Float): FlipBody {
    require(fill in 0.2f..1f) { "fill must be at least about a quarter" }
    val height = 0.204f
    val radius = 0.0315f
    val cavity = 0.180f
    val plastic = 0.0125f
    val plasticCom = 0.098f
    val water = fill * 0.500f
    val waterHeight = fill * cavity
    val waterCom = waterHeight / 2f
    val mass = plastic + water
    val comBase = (plastic * plasticCom + water * waterCom) / mass
    val comFromCap = height - comBase
    fun cylinder(m: Float, h: Float, r: Float) = m * (3f * r * r + h * h) / 12f
    val iWater = cylinder(water, waterHeight.coerceAtLeast(0.01f), radius * 0.92f)
    val iShell = plastic * (radius * radius / 2f + height * height / 12f)
    val dWater = waterCom - comBase
    val dShell = plasticCom - comBase
    val pooled = iWater + water * dWater * dWater + iShell + plastic * dShell * dShell
    val spreadCom = cavity / 2f
    val spreadBase = (plastic * plasticCom + water * spreadCom) / mass
    val iSpreadWater = cylinder(water, cavity, radius * 0.92f)
    val dWS = spreadCom - spreadBase
    val dSS = plasticCom - spreadBase
    val spread = iSpreadWater + water * dWS * dWS + iShell + plastic * dSS * dSS
    val mobility = (1f - fill).coerceIn(0f, 1f)
    val same = mobility < 1e-3f
    return FlipBody(
        id = "bottle",
        label = "Water bottle",
        bottle = true,
        length = height,
        mass = mass,
        comPooled = comFromCap,
        comSpread = if (same) comFromCap else height - spreadBase,
        inertiaPooled = pooled,
        inertiaSpread = if (same) pooled else spread,
        gripFromTip = 0.026f,
        tipZone = 0.02f,
        handleZone = 0.04f,
        baseRadius = radius,
        mobility = mobility,
        waterMass = water,
        fill = fill,
    )
}

object FlipModels {
    val chef: FlipBody = knife(
        "chef", "Chef's knife", 0.330f,
        // 8 inch blade, 2.0 mm average, heavier at the heel; bolster; full tang handle.
        listOf(
            Part(0.110f, 0.112f, 0.203f),
            Part(0.040f, 0.206f, 0.022f),
            Part(0.078f, 0.272f, 0.120f),
        ),
        gripFromTip = 0.272f, handleZone = 0.115f,
    )
    val throwing: FlipBody = knife(
        "throwing", "Throwing knife", 0.280f,
        // One piece of steel, 22 × 5 mm, balanced at the middle.
        listOf(Part(0.240f, 0.140f, 0.280f)),
        gripFromTip = 0.246f, handleZone = 0.07f,
    )
    val pocket: FlipBody = knife(
        "pocket", "Pocket knife", 0.200f,
        // 90 mm blade, heavy scales and liners in the handle.
        listOf(
            Part(0.032f, 0.042f, 0.090f),
            Part(0.096f, 0.152f, 0.105f),
        ),
        gripFromTip = 0.158f, handleZone = 0.10f,
    )
    val butterfly: FlipBody = knife(
        "butterfly", "Butterfly knife", 0.250f,
        // Open balisong: blade plus two steel handles.
        listOf(
            Part(0.058f, 0.052f, 0.115f),
            Part(0.098f, 0.188f, 0.125f),
        ),
        gripFromTip = 0.198f, handleZone = 0.11f,
    )
    val cleaver: FlipBody = knife(
        "cleaver", "Cleaver", 0.295f,
        // 175 × 95 × 2.4 mm blade. The tall blade adds to the in-plane inertia.
        listOf(
            Part(0.311f, 0.088f, 0.199f),
            Part(0.070f, 0.232f, 0.115f),
        ),
        gripFromTip = 0.238f, tipZone = 0.035f, handleZone = 0.10f,
    )

    val fills: List<Float> = listOf(0.25f, 1f / 3f, 0.50f, 0.75f, 1f)
    val fillLabels: List<String> = listOf("1/4", "1/3", "1/2", "3/4", "full")
    val bottles: List<FlipBody> = fills.map { waterBottle(it) }

    val knives: List<FlipBody> = listOf(chef, throwing, pocket, butterfly, cleaver)

    data class Option(val label: String, val body: FlipBody, val blurb: String)

    val options: List<Option> = knives.map { knife ->
        val grams = (knife.mass * 1000f).toInt()
        val cm = (knife.length * 100f).toInt()
        val pct = (knife.balance * 100f).toInt()
        Option(knife.label, knife, "$grams g · $cm cm · balance $pct% from the tip")
    } + bottles.mapIndexed { i, bottle ->
        val grams = (bottle.mass * 1000f).toInt()
        val label = "Bottle ${fillLabels[i]}"
        val blurb = if (bottle.mobility < 0.02f) "$grams g · full, so the water cannot slosh"
        else "$grams g · ${fillLabels[i]} full, and the water can slosh"
        Option(label, bottle, blurb)
    }

    fun option(index: Int): Option = options[index]
}

/** A round of flips. The score is the streak of clean landings. A miss ends it. */
data class KnifeFlip(
    val option: Int,
    val seed: Long,
    val score: Int,
    val streak: Int,
    val phase: Phase,
    val release: Release?,
    val throwNum: Int,
    val verdict: String,
) {
    enum class Phase { READY, FLYING, ENDED }

    val body: FlipBody get() = FlipModels.option(option).body
    val ended: Boolean get() = phase == Phase.ENDED

    fun toss(gripSpeed: Float, aim: Float): KnifeFlip? {
        if (phase != Phase.READY || gripSpeed < 0.65f) return null
        val held = FlipPhysics.hold(body)
        val release = FlipPhysics.flick(body, held.theta, gripSpeed, aim, held.x, held.z)
        return copy(phase = Phase.FLYING, release = release, throwNum = throwNum + 1, verdict = "")
    }

    fun settle(): KnifeFlip {
        val r = release ?: return this
        if (phase != Phase.FLYING) return this
        val flight = FlipPhysics.simulate(body, r)
        val ok = flight.end == FlipEnd.STUCK || flight.end == FlipEnd.LANDED
        return if (ok) copy(phase = Phase.READY, release = null, streak = streak + 1, score = streak + 1, verdict = flight.end.name)
        else copy(phase = Phase.ENDED, release = null, score = score, verdict = flight.end.name)
    }

    companion object {
        fun start(seed: Long, option: Int) = KnifeFlip(option, seed, 0, 0, Phase.READY, null, 0, "")

        /** A practised toss for the object in hand. The same motion is not right for every fill. */
        fun practised(option: Int): Pair<Float, Float> =
            if (FlipModels.option(option).body.bottle) BOTTLE_TOSS else KNIFE_TOSS

        val KNIFE_TOSS = 1.30f to 1.25f
        val BOTTLE_TOSS = 1.70f to 1.15f
    }
}

object KnifeFlipCodec {
    fun encode(g: KnifeFlip): String {
        val r = g.release
        val release = if (r == null) "-" else listOf(r.x, r.z, r.vx, r.vz, r.theta, r.omega, r.sigma).joinToString(",") { num(it) }
        return listOf("K1", g.option, g.seed, g.score, g.streak, g.phase.name, g.throwNum, g.verdict.ifEmpty { "-" }, release).joinToString("\n")
    }

    fun decode(text: String): KnifeFlip? = try {
        val lines = text.split('\n')
        if (lines[0] != "K1" || lines.size < 9) null
        else {
            val release = if (lines[8] == "-") null else lines[8].split(',').map { it.toFloat() }.let {
                Release(it[0], it[1], it[2], it[3], it[4], it[5], it.getOrElse(6) { 0f })
            }
            val option = lines[1].toInt()
            require(option in FlipModels.options.indices)
            KnifeFlip(option, lines[2].toLong(), lines[3].toInt(), lines[4].toInt(),
                KnifeFlip.Phase.valueOf(lines[5]), release, lines[6].toInt(), lines[7].let { if (it == "-") "" else it })
        }
    } catch (_: RuntimeException) { null }

    private fun num(v: Float) = java.lang.Float.toString(v)
}
