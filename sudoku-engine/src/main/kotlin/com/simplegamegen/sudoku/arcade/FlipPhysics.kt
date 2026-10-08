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
 * Knives are rigid. The release is an impulse at the grip that matches the hand speed there
 * (impulse-momentum for a rigid body). While the knife was swinging with the hand,
 * ω = V / r, so the distance travelled in one full turn is d = 2π r and does not depend on
 * how hard the throw is. See J. Thiel, "The physics of knife throwing",
 * https://www.knifethrowing.info/physics_of_knife_throwing.html formulas 1–3.
 * For a finger flick the pivot is the grip, so r = k² / lever with k the radius of gyration.
 * A long hammer grip instead uses the arm (about 0.32 m, their measured ~2 m per turn).
 *
 * A bottle follows Dekker et al., "Water bottle flipping physics", Am. J. Phys. 86, 733 (2018),
 * https://doi.org/10.1119/1.5052441 . Water spread along the bottle raises the moment of inertia.
 * Gravity has no torque about the centre of mass, so Iω stays constant and the spin falls.
 * A full bottle has no air gap, so the water cannot move.
 */
object FlipPhysics {
    const val GRAVITY = 9.81f
    const val DT = 1f / 240f

    /** Wood block the point can stick in, and the bottle can land on. Metres, along the toss. */
    const val BLOCK_X0 = 0f
    const val BLOCK_X1 = 0.58f

    private const val G = -GRAVITY
    /** Air at about 1.2 kg/m³. A tumbling body has Cd near 1; the area is set per model. */
    private const val RHO_AIR = 1.2f
    private const val DRAG_CD = 1.0f
    private const val MAX_TIME = 2.8f
    private const val MAX_BOUNCES = 10

    /**
     * A landing is one full turn back to cap-up, not a half turn (which would be cap-down)
     * and not a second revolution. One radian of slack either side of 2π.
     */
    private const val FLIP_MIN = (2.0 * PI - 1.0).toFloat()
    private const val FLIP_MAX = (2.0 * PI + 1.2).toFloat()

    /**
     * How fast the water height moves toward the spread state.
     * Dekker et al. measure h(t); they do not give dh/dt. Fig. 4 shows ω falling across
     * the whole flight and essentially finished by landing, and a run lasts about a second.
     * τ = 0.32 s is that empirical timescale: most of the spread has happened by ~1 s
     * (1 − e^(−1/τ) ≈ 0.96) and it is still moving at mid-flight.
     */
    private const val SLOSH_TAU = 0.32f

    /**
     * Effective radius of the swing that sets the bottle's spin, ω = −V / R.
     * Thiel's formula 2. His measured hammer grip is about 0.32 m (a full turn every ~2 m).
     * A bottle flip is a wrist flick, not that throw: Dekker's flights are one turn in about
     * a second, so ω is of order 10 rad/s while the toss that reaches this block is a couple
     * of metres per second. R = 0.16 m is that estimate, between the neck-to-balance distance
     * (~0.14 m) and a forearm. The same R is used at every fill, because Dekker compare fills
     * at a given ω₀ (their Sec. IV: "for a given ω₀ one wishes to reduce ω").
     */
    private const val BOTTLE_SWING = 0.16f

    /** Base of the bottle this far above the block when it leaves the hand. A table-height flip. */
    private const val BOTTLE_LIFT = 0.36f

    /** Pine, compression perpendicular to the grain, mid of the Wood Handbook range 3–6 MPa. */
    private const val WOOD_PRESSURE = 4.0e6f
    /** A sharp tip, about 1.5 mm by 0.5 mm. An estimate, not a measured knife. */
    private const val TIP_AREA = 1.5e-3f * 0.5e-3f
    /** How deep the point must sink before it stays. An estimate. */
    private const val EMBED = 0.006f

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
            val dragK = if (drag) 0.5f * RHO_AIR * DRAG_CD * 0.01f / 0.2f else 0f
            val az = G - dragK * abs(vz) * vz
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
     * The gesture sets the centre-of-mass velocity. The spin is the swing's, ω = −V / R
     * with [BOTTLE_SWING] the same at every fill (Dekker et al. Sec. IV). A negative ω
     * carries the base forward toward the block. Fill does not change this release; it
     * changes I(h), the balance, and whether the landing is stable.
     */
    private fun looseBottle(body: FlipBody, theta: Float, gripSpeed: Float, aim: Float, x: Float, z: Float): Release {
        val omega = -gripSpeed / BOTTLE_SWING
        return Release(x, z, gripSpeed * cos(aim), gripSpeed * sin(aim), theta, omega)
    }

    /** Spin left after [seconds] in the air, starting from [omega]. No collision. Drag on. */
    fun coastOmega(body: FlipBody, omega: Float, seconds: Float): Float = coast(body, omega, seconds, drag = true).omega

    /**
     * Same coast with drag chosen by the caller, so a test can check I₁ω₁ = I₂ω₂
     * with nothing else taking angular momentum.
     */
    fun coastSample(body: FlipBody, omega: Float, seconds: Float, drag: Boolean): SpinState =
        coast(body, omega, seconds, drag).let { SpinState(it.omega, it.sigma) }

    private fun coast(body: FlipBody, omega: Float, seconds: Float, drag: Boolean): State {
        var s = State(0f, 8f, 0f, 0f, 0.3f, omega, 0f)
        var t = 0f
        while (t + 1e-6f < seconds) {
            val h = minOf(DT, seconds - t)
            s = step(body, s, h, drag)
            t += h
        }
        return s
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
            val tipV = pointSpeed(body, s0, hitAt(body, s0, 0f))
            val speed = hypot(tipV.x, tipV.z)
            // The tip has to arrive first (geometry in knifeContact) and carry enough energy
            // to crush a few millimetres of softwood. There is no upper speed: a harder
            // point-first throw sinks deeper. Adamovich's filmed throws are about 50 km/h.
            val stick = kind == HitKind.TIP && speed >= embedSpeed(body.mass)
            if (stick) {
                val planted = plantKnife(body, s0)
                return Resolved(planted, FlipEnd.STUCK, FlipEnd.STUCK)
            }
            val why = when {
                kind == HitKind.HANDLE -> FlipEnd.HANDLE
                kind == HitKind.BLADE -> FlipEnd.FLAT
                speed < embedSpeed(body.mass) -> FlipEnd.WEAK
                else -> FlipEnd.FLAT
            }
            return Resolved(bounce(body, s0, hit, 0.42f, 0.32f), null, why)
        }
        val base = hit.kind == HitKind.BASE
        // Plastic on wood, restitution about 0.2. Dekker et al. leave the landing unmodelled
        // (Sec. V). A base that is already inside the tip-over angle, with too little spin
        // left to climb the rim, stays. Anything else bounces.
        if (base && upright(body, s0, theta0)) {
            val settled = settleBottle(body, s0.copy(sigma = 0f))
            return Resolved(settled, FlipEnd.LANDED, FlipEnd.LANDED)
        }
        val bounced = bounce(body, s0, hit, 0.22f, 0.45f)
        return Resolved(bounced, null, FlipEnd.TIPPED)
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

    /**
     * Upright means the centre of mass is still over the base, and the spin left after the
     * flight cannot carry it over the rim.
     *
     * The tip-over angle is α = atan(R / h_cm), with h_cm from Dekker eq. (15) for water
     * back at the base. The potential barrier to that angle, rotating about the rim, is
     * m g L (1 − cos(α − |tilt|)), L = hypot(R, h_cm).
     *
     * Rotational KE uses the slowed ω and the pooled inertia. Putting the water back at the
     * base without speeding the spin up is an inelastic splash: Dekker do not model it, and
     * conserving L through the collapse would undo the slowdown that the flight just produced.
     * The rebound from plastic on wood (e ≈ 0.22) must not hop the bottle by more than h_cm.
     */
    private fun upright(body: FlipBody, s: State, theta0: Float): Boolean {
        val tilt = wrap(s.theta)
        val h = body.comFromBase(0f).coerceAtLeast(0.02f)
        val alpha = atan(body.baseRadius / h)
        if (abs(tilt) >= alpha) return false
        val reach = hypot(body.baseRadius, h)
        val barrier = body.mass * GRAVITY * reach * (1f - cos((alpha - abs(tilt)).coerceAtLeast(0f)))
        val spin = 0.5f * body.inertia(0f) * s.omega * s.omega
        val turned = abs(s.theta - theta0)
        val rebound = 0.22f * abs(s.vz)
        val hop = rebound * rebound / (2f * GRAVITY)
        return spin <= barrier && turned in FLIP_MIN..FLIP_MAX && hop <= h
    }

    /**
     * Speed whose kinetic energy equals the work to push the tip [EMBED] into softwood.
     * Pressure is the order of pine compressed across the grain, about 3–6 MPa
     * (USDA Forest Products Laboratory, Wood Handbook, mechanical properties of wood).
     * The tip area and the embed depth are estimates for a sharp point that stays in.
     */
    internal fun embedSpeed(mass: Float): Float {
        val energy = WOOD_PRESSURE * TIP_AREA * EMBED
        return sqrt(2f * energy / mass.coerceAtLeast(0.02f))
    }

    private fun plantKnife(body: FlipBody, s: State): State {
        val tip = point(body, s, 0f)
        return s.copy(
            x = s.x - tip.x + tip.x.coerceIn(BLOCK_X0 + 0.01f, BLOCK_X1 - 0.01f),
            z = s.z - tip.z - 0.004f,
            vx = 0f, vz = 0f, omega = 0f,
        )
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
        if (body.bottle && body.fill < 0.999f) {
            // In the air the water spreads toward the full height (Dekker Fig. 1).
            // It falls back on impact, not before: their Fig. 4 keeps the slow spin through the descent.
            // I(h) then sets ω so that Iω is unchanged (their eq. 12). No extra spin sink.
            val sigma2 = (sigma + (1f - sigma) * (dt / SLOSH_TAU).coerceAtMost(1f)).coerceIn(0f, 1f)
            val i1 = body.inertia(sigma)
            val i2 = body.inertia(sigma2).coerceAtLeast(1e-8f)
            omega *= i1 / i2
            sigma = sigma2
        }
        val area = if (body.bottle) (PI.toFloat() * body.baseRadius * body.baseRadius) else body.length * 0.012f
        val k = if (drag) 0.5f * RHO_AIR * DRAG_CD * area / body.mass else 0f
        val speed = hypot(s.vx, s.vz)
        val ax = -k * speed * s.vx
        val az = G - k * speed * s.vz
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

data class SpinState(val omega: Float, val sigma: Float)

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
    fun comFromTip(sigma: Float) = if (!bottle) comPooled
        else length - BottleModel.comFromBase(fill, sigma)

    /** Bottles use Dekker's I(h), which is not linear in the water height. Knives are rigid. */
    fun inertia(sigma: Float) = if (!bottle) inertiaPooled else BottleModel.inertia(fill, sigma)
    fun comFromBase(sigma: Float) = length - comFromTip(sigma)

    /** Fraction of the length from the tip (or cap) to the balance point, water settled. */
    val balance: Float get() = comPooled / length
}

private data class Part(val mass: Float, val fromTip: Float, val span: Float, val across: Float = 0f)

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
        // Thin plate in the plane of the blade: I = m (a² + b²) / 12 about the centre,
        // then the parallel-axis shift to the knife's balance. b is the height across the blade.
        val own = p.mass.toDouble() * (p.span * p.span + p.across * p.across) / 12.0
        val d = p.fromTip - com
        inertia += own + p.mass.toDouble() * d * d
    }
    return FlipBody(
        id, label, bottle = false, length, mass, com, com, inertia.toFloat(), inertia.toFloat(),
        gripFromTip, tipZone, handleZone, baseRadius = 0f, mobility = 0f, waterMass = 0f, fill = 0f,
    )
}

/**
 * Dekker et al. one-dimensional bottle, with the shell's radial term from their eq. (2).
 * H and R match the drawn bottle. mb = 25 g and M = mw,full / mb = 20 are their typical 0.5 L bottle.
 * [fill] is f = h₀/H. Empty is not offered.
 *
 * G(f) = I₀/I_max from their eq. (14) is smallest near f = 0.41. The centre of mass is lowest at
 * f = (√(1+M) − 1) / M ≈ 0.18 (eq. 16). Together those put a good flip around 20%–40%, the range
 * they compare with the usual 1/4 to 1/3.
 */
object BottleModel {
    const val H = 0.204f
    const val MB = 0.025f
    const val M = 20f
    const val R = 0.0315f

    fun waterMass(fill: Float) = fill * M * MB

    /** Water height. σ = 0 is pooled at h₀ = f H. σ = 1 is spread over the whole bottle. */
    fun waterHeight(fill: Float, sigma: Float): Float {
        val h0 = fill * H
        return h0 + sigma.coerceIn(0f, 1f) * (H - h0)
    }

    /** Eq. (8), measured from the base. */
    fun comFromBase(fill: Float, sigma: Float): Float {
        val mw = waterMass(fill)
        val h = waterHeight(fill, sigma)
        return (MB * H / 2f + mw * h / 2f) / (MB + mw)
    }

    /**
     * Eq. (11), plus the 6 R² term of eq. (2) so the shell is not a zero-radius rod.
     * Their G(f) drops that term; [slowdownG] keeps the published 1D formula for the optimum.
     */
    fun inertia(fill: Float, sigma: Float): Float {
        val mw = waterMass(fill)
        val h = waterHeight(fill, sigma).coerceAtLeast(0.01f)
        val hcm = comFromBase(fill, sigma)
        val shell = MB * (6f * R * R + H * H) / 12f + MB * (H / 2f - hcm) * (H / 2f - hcm)
        val water = mw * h * h / 12f + mw * (h / 2f - hcm) * (h / 2f - hcm)
        return shell + water
    }

    /** Eq. (14). Ratio of pooled inertia to fully spread inertia in the 1D model. */
    fun slowdownG(fill: Float): Float {
        val f = fill
        val num = M * M * f * f * f * f + 4f * M * f * f * f - 6f * M * f * f + 4f * M * f + 1f
        val den = (1f + M * f) * (1f + M * f)
        return num / den
    }

    /** Eq. (15), h_cm / H with the water still pooled. */
    fun pooledComFraction(fill: Float): Float = 0.5f * (1f + M * fill * fill) / (1f + M * fill)

    /** Eq. (16). */
    fun lowestComFill(): Float = (sqrt(1f + M) - 1f) / M
}

/**
 * A 500 mL PET bottle. The container is [BottleModel.MB]; the water is [fill] of 500 g.
 * Empty is not offered.
 */
fun waterBottle(fill: Float): FlipBody {
    require(fill in 0.2f..1f) { "fill must be at least about a quarter" }
    val same = fill >= 0.999f
    val comCap = BottleModel.H - BottleModel.comFromBase(fill, 0f)
    val comSpread = if (same) comCap else BottleModel.H - BottleModel.comFromBase(fill, 1f)
    val pooled = BottleModel.inertia(fill, 0f)
    val spread = if (same) pooled else BottleModel.inertia(fill, 1f)
    return FlipBody(
        id = "bottle",
        label = "Water bottle",
        bottle = true,
        length = BottleModel.H,
        mass = BottleModel.MB + BottleModel.waterMass(fill),
        comPooled = comCap,
        comSpread = comSpread,
        inertiaPooled = pooled,
        inertiaSpread = spread,
        gripFromTip = 0.022f,
        tipZone = 0.02f,
        handleZone = 0.04f,
        baseRadius = BottleModel.R,
        mobility = if (same) 0f else 1f - fill,
        waterMass = BottleModel.waterMass(fill),
        fill = fill,
    )
}

object FlipModels {
    /**
     * Stock thickness of the throwing knife, 5 mm. The 28 mm figure on the part is the
     * width (`across`) and is what the thin-plate inertia uses. Thickness is not in that
     * equation. `tools/knife-flip/generate_models.py` parses this declaration so the mesh
     * stays the same thickness.
     */
    const val THROWING_THICKNESS = 0.005f

    val chef: FlipBody = knife(
        "chef", "Chef's knife", 0.330f,
        // 200 × 48 mm blade, 2.5 mm spine, brass bolster, 125 mm wood scales on a full tang.
        listOf(
            Part(0.085f, 0.108f, 0.200f, across = 0.048f),
            Part(0.040f, 0.206f, 0.024f, across = 0.042f),
            Part(0.095f, 0.270f, 0.125f, across = 0.032f),
        ),
        gripFromTip = 0.272f, handleZone = 0.115f,
    )
    val throwing: FlipBody = knife(
        "throwing", "Throwing knife", 0.280f,
        // One piece of steel, 28 mm across and THROWING_THICKNESS thick, balanced at the middle.
        listOf(Part(0.240f, 0.140f, 0.280f, across = 0.028f)),
        gripFromTip = 0.246f, handleZone = 0.07f,
    )
    val pocket: FlipBody = knife(
        "pocket", "Pocket knife", 0.200f,
        // 85 × 24 mm drop-point blade, heavy liners and scales in the 110 mm handle.
        listOf(
            Part(0.030f, 0.040f, 0.085f, across = 0.024f),
            Part(0.098f, 0.145f, 0.110f, across = 0.026f),
        ),
        gripFromTip = 0.158f, handleZone = 0.10f,
    )
    val butterfly: FlipBody = knife(
        "butterfly", "Butterfly knife", 0.250f,
        // Open balisong: 100 × 22 mm clip-point blade plus two channel handles.
        listOf(
            Part(0.050f, 0.048f, 0.100f, across = 0.022f),
            Part(0.105f, 0.180f, 0.140f, across = 0.024f),
        ),
        gripFromTip = 0.195f, handleZone = 0.11f,
    )
    val cleaver: FlipBody = knife(
        "cleaver", "Cleaver", 0.300f,
        // 180 × 90 mm blade, 5 mm spine. The tall blade adds to the in-plane inertia.
        listOf(
            Part(0.330f, 0.090f, 0.180f, across = 0.090f),
            Part(0.075f, 0.240f, 0.120f, across = 0.032f),
        ),
        gripFromTip = 0.245f, tipZone = 0.040f, handleZone = 0.11f,
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
        val BOTTLE_TOSS = 2.60f to 1.45f
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
