package com.simplegamegen.sudoku.arcade

import kotlin.math.cos
import kotlin.math.sin

/** Words already translated by the app. Defaults are the English source. */
data class LogThrowCopy(
    val endless: String = "Endless",
    val endlessBlurb: String = "Stages speed up, reverse, and tighten",
    val levels: String = "Levels",
    val levelsBlurb: String = "48 logs. A boss every fifth",
    val best: String = "Best",
    val score: String = "Score",
    val stage: String = "Stage",
    val level: String = "Level",
    val dagger: String = "1 dagger",
    val daggers: String = "{0} daggers",
    val boss: String = "Boss",
    val paused: String = "Paused",
    val clang: String = "Clang",
    val burst: String = "The log bursts",
    val again: String = "Play again",
    val map: String = "Level map",
    val modes: String = "Modes",
    val tap: String = "Tap to throw",
    val choose: String = "Choose a log",
    val locked: String = "Locked",
    val noStars: String = "No stars",
    val oneStar: String = "1 star",
    val stars: String = "{0} stars",
    val starTotal: String = "{0} of {1} stars",
    val pattern: Map<Pattern, String> = mapOf(
        Pattern.STEADY to "Steady",
        Pattern.REVERSE to "Reverse",
        Pattern.ACCEL to "Speeding up",
        Pattern.DECEL to "Slowing down",
        Pattern.STOP_GO to "Stop and go",
        Pattern.WOBBLE to "Wobble",
        Pattern.REVERSAL to "Reversal",
    ),
    val wood: Map<Wood, String> = mapOf(
        Wood.OAK to "Oak",
        Wood.IRONWOOD to "Ironwood",
        Wood.CHARRED to "Charred",
        Wood.APPLEWOOD to "Applewood",
        Wood.BIRCH to "Birch",
        Wood.SPIRAL to "Spiral grain",
        Wood.GOLDEN to "Golden",
        Wood.CRACKED to "Cracked",
        Wood.HEARTWOOD to "Heartwood",
        Wood.RINGED to "Ringed",
    ),
    val challenge: Map<Challenge, String> = mapOf(
        Challenge.EXACT_GAPS to "Exact gaps",
        Challenge.FAST_REVERSE to "Fast reversal",
        Challenge.TINY_TARGET to "Tiny target",
        Challenge.MUST_HIT to "Hit every fruit",
    ),
)

fun LogThrowCopy.fill(template: String, vararg values: Any): String {
    var s = template
    values.forEachIndexed { i, v -> s = s.replace("{$i}", v.toString()) }
    return s
}

sealed interface DrawOp {
    data class VGrad(val x: Float, val y: Float, val w: Float, val h: Float, val top: Long, val bottom: Long) : DrawOp
    data class Circle(val x: Float, val y: Float, val r: Float, val color: Long, val stroke: Float = 0f) : DrawOp
    data class RoundRect(val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float, val color: Long, val stroke: Float = 0f) : DrawOp
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Long, val stroke: Float) : DrawOp
    data class Poly(val pts: List<Float>, val color: Long, val stroke: Float = 0f) : DrawOp
    /** [startDeg] follows Android: 0 is 3 o'clock, positive is clockwise. */
    data class Wedge(val cx: Float, val cy: Float, val r: Float, val startDeg: Float, val sweep: Float, val color: Long) : DrawOp
    data class Label(val text: String, val x: Float, val y: Float, val size: Float, val color: Long, val bold: Boolean = false, val align: Int = 1) : DrawOp
}

data class HotSpot(val id: String, val x: Float, val y: Float, val w: Float, val h: Float, val label: String)

data class LogThrowFrame(val ops: List<DrawOp>, val hots: List<HotSpot>)

object LogThrowArt {
    fun frame(state: ThrowState, w: Float, h: Float, copy: LogThrowCopy = LogThrowCopy(), paused: Boolean = false): LogThrowFrame {
        val b = Board(w, h)
        val ops = ArrayList<DrawOp>()
        val hots = ArrayList<HotSpot>()
        b.background(ops)
        when (state.phase) {
            Phase.MENU -> menu(b, state, copy, ops, hots)
            Phase.SELECT -> levels(b, state, copy, ops, hots)
            else -> play(b, state, copy, paused, ops, hots)
        }
        return LogThrowFrame(ops, hots)
    }

    fun hit(frame: LogThrowFrame, x: Float, y: Float): HotSpot? =
        frame.hots.firstOrNull { x >= it.x && y >= it.y && x <= it.x + it.w && y <= it.y + it.h }

    private fun menu(b: Board, state: ThrowState, copy: LogThrowCopy, ops: MutableList<DrawOp>, hots: MutableList<HotSpot>) {
        ops += DrawOp.Label("Log Throw", b.w / 2, b.h * 0.12f, b.w * 0.09f, 0xFFF6E7C1, bold = true)
        ops += DrawOp.Label(copy.best + " " + state.best, b.w / 2, b.h * 0.18f, b.w * 0.04f, 0xFFE7C98A)
        val cardW = b.w * 0.78f
        val cardH = b.h * 0.18f
        val x = (b.w - cardW) / 2
        card(ops, x, b.h * 0.30f, cardW, cardH, 0xFF6B3E22, 0xFFF3D7A1)
        ops += DrawOp.Label(copy.endless, b.w / 2, b.h * 0.36f, b.w * 0.055f, 0xFF2A160C, bold = true)
        ops += DrawOp.Label(copy.endlessBlurb, b.w / 2, b.h * 0.42f, b.w * 0.032f, 0xFF4A2C16)
        hots += HotSpot("endless", x, b.h * 0.30f, cardW, cardH, copy.endless)
        card(ops, x, b.h * 0.52f, cardW, cardH, 0xFF2F5A34, 0xFFE7F0D8)
        ops += DrawOp.Label(copy.levels, b.w / 2, b.h * 0.58f, b.w * 0.055f, 0xFF14240F, bold = true)
        ops += DrawOp.Label(copy.levelsBlurb, b.w / 2, b.h * 0.64f, b.w * 0.032f, 0xFF24361C)
        val total = copy.fill(copy.starTotal, state.starTotal, LogThrow.LEVEL_COUNT * 3)
        ops += DrawOp.Label(total, b.w / 2, b.h * 0.68f, b.w * 0.03f, 0xFF8A5A12)
        hots += HotSpot("levels", x, b.h * 0.52f, cardW, cardH, copy.levels)
        // A small log with one dagger, so the menu reads as the game.
        val saved = b.cy
        b.cy = b.h * 0.84f
        b.radius = b.w * 0.12f
        log(b, 0.4, Wood.OAK, 1f, ops)
        dagger(b, 0.0, b.radius, player = true, ops)
        b.cy = saved
    }

    private fun levels(b: Board, state: ThrowState, copy: LogThrowCopy, ops: MutableList<DrawOp>, hots: MutableList<HotSpot>) {
        ops += DrawOp.Label(copy.choose, b.w / 2, b.h * 0.06f, b.w * 0.05f, 0xFFF6E7C1, bold = true)
        ops += DrawOp.Label(copy.fill(copy.starTotal, state.starTotal, LogThrow.LEVEL_COUNT * 3), b.w / 2, b.h * 0.10f, b.w * 0.03f, 0xFFE7C98A)
        val cols = 4
        val rows = LogThrow.LEVEL_COUNT / cols
        val gap = b.w * 0.025f
        val top = b.h * 0.14f
        val bottom = b.h * 0.90f
        val cell = minOf((b.w - gap * (cols + 1)) / cols, (bottom - top - gap * (rows + 1)) / rows)
        val gridW = cols * cell + (cols - 1) * gap
        val x0 = (b.w - gridW) / 2
        for (i in 0 until LogThrow.LEVEL_COUNT) {
            val n = i + 1
            val spec = LogThrow.levelSpec(n)
            val col = i % cols
            val row = i / cols
            val x = x0 + col * (cell + gap)
            val y = top + row * (cell + gap)
            val open = state.unlocked(n)
            val stars = state.stars.getOrElse(i) { 0 }
            val face = if (!open) 0xFF3A2A22 else if (spec.boss) 0xFF6A3A18 else if (spec.challenge != null) 0xFF3E4A28 else 0xFF6B4428
            ops += DrawOp.RoundRect(x, y, cell, cell, cell * 0.18f, face)
            if (spec.boss) ops += DrawOp.RoundRect(x + cell * 0.08f, y + cell * 0.08f, cell * 0.84f, cell * 0.84f, cell * 0.14f, 0xFFE7C98A, stroke = cell * 0.035f)
            ops += DrawOp.Label("$n", x + cell / 2, y + cell * 0.42f, cell * 0.28f, if (open) 0xFFFFF6E4 else 0xFF8A7564, bold = true)
            if (!open) {
                ops += DrawOp.Label(copy.locked, x + cell / 2, y + cell * 0.72f, cell * 0.13f, 0xFFB7A090)
            } else {
                val label = when (stars) {
                    0 -> copy.noStars
                    1 -> copy.oneStar
                    else -> copy.fill(copy.stars, stars)
                }
                repeat(3) { s ->
                    val cx = x + cell * (0.32f + s * 0.18f)
                    val cy = y + cell * 0.74f
                    ops += DrawOp.Circle(cx, cy, cell * 0.055f, if (s < stars) 0xFFF2C14E else 0xFF2A1A12, stroke = if (s < stars) 0f else cell * 0.012f)
                }
                val bits = listOf(copy.level + " $n", label) + if (spec.boss) listOf(copy.boss) else emptyList()
                hots += HotSpot("L$n", x, y, cell, cell, bits.joinToString(" · "))
            }
        }
        val btn = button(ops, b.w * 0.18f, b.h * 0.925f, b.w * 0.64f, b.h * 0.055f, copy.modes)
        hots += HotSpot("modes", btn[0], btn[1], btn[2], btn[3], copy.modes)
    }

    private fun play(b: Board, state: ThrowState, copy: LogThrowCopy, paused: Boolean, ops: MutableList<DrawOp>, hots: MutableList<HotSpot>) {
        val spec = state.spec()
        val theta = spec.program.angleAt(state.timeMs)
        val age = (state.timeMs - state.eventMs).coerceAtLeast(0)
        val shake = if (state.phase == Phase.AIM && age < 180 && state.thrown > 0) {
            val u = 1f - age / 180f
            (sin(age / 18.0) * 5.0 * u).toFloat()
        } else 0f
        b.cx += shake
        log(b, theta, spec.wood, if (state.phase == Phase.CLEAR) 0f else 1f, ops)
        if (state.phase == Phase.CLEAR) burst(b, theta, age, palette(spec.wood), ops)
        else {
            spec.pickups.forEachIndexed { i, p ->
                if (i !in state.taken) pickup(b, theta + p.angle, p.kind, ops)
            }
            state.stuck.forEach { dagger(b, theta + it.local, b.radius, it.player, ops) }
        }
        chips(b, state, theta, ops)
        if (state.phase == Phase.FLIGHT) flying(b, state, ops)
        if (state.phase == Phase.FAIL) bounce(b, state, ops)
        b.cx -= shake
        hud(b, state, spec, copy, ops)
        val left = (spec.knives - state.thrown).coerceAtLeast(0)
        stack(b, left, ops)
        if (state.phase == Phase.AIM && !paused) {
            hots += HotSpot("throw", 0f, b.h * 0.16f, b.w, b.h * 0.78f, copy.tap)
            ops += DrawOp.Label(copy.tap, b.w / 2, b.h * 0.955f, b.w * 0.032f, 0xFFD9C4A4)
        }
        if (state.phase == Phase.FAIL) {
            ops += DrawOp.Label(copy.clang, b.w / 2, b.h * 0.22f, b.w * 0.07f, 0xFFFFF6E0, bold = true)
            val again = button(ops, b.w * 0.12f, b.h * 0.90f, b.w * 0.36f, b.h * 0.06f, copy.again)
            hots += HotSpot("again", again[0], again[1], again[2], again[3], copy.again)
            val other = if (state.mode == Mode.ENDLESS) copy.modes else copy.map
            val id = if (state.mode == Mode.ENDLESS) "modes" else "map"
            val map = button(ops, b.w * 0.52f, b.h * 0.90f, b.w * 0.36f, b.h * 0.06f, other)
            hots += HotSpot(id, map[0], map[1], map[2], map[3], other)
        }
        if (state.phase == Phase.CLEAR) {
            ops += DrawOp.Label(copy.burst, b.w / 2, b.h * 0.20f, b.w * 0.05f, 0xFFFFF1D0, bold = true)
        }
        if (paused) {
            ops += DrawOp.RoundRect(0f, 0f, b.w, b.h, 0f, 0xAA140E0A)
            ops += DrawOp.Label(copy.paused, b.w / 2, b.h * 0.5f, b.w * 0.08f, 0xFFFFF6E4, bold = true)
        }
    }

    private fun hud(b: Board, state: ThrowState, spec: StageSpec, copy: LogThrowCopy, ops: MutableList<DrawOp>) {
        val title = if (state.mode == Mode.ENDLESS) copy.stage + " " + state.level else copy.level + " " + state.level
        ops += DrawOp.Label(copy.score + " " + state.score, b.w * 0.06f, b.h * 0.045f, b.w * 0.034f, 0xFFF6E7C1, bold = true, align = 0)
        ops += DrawOp.Label(copy.best + " " + state.best, b.w * 0.94f, b.h * 0.045f, b.w * 0.034f, 0xFFE7C98A, align = 2)
        ops += DrawOp.Label(title, b.w / 2, b.h * 0.09f, b.w * 0.04f, 0xFFFFF6E4, bold = true)
        val detail = buildString {
            append(copy.pattern[spec.pattern] ?: spec.pattern.name)
            if (spec.boss) append(" · ").append(copy.boss).append(" · ").append(copy.wood[spec.wood] ?: "")
            spec.challenge?.let { append(" · ").append(copy.challenge[it] ?: "") }
        }
        ops += DrawOp.Label(detail, b.w / 2, b.h * 0.125f, b.w * 0.028f, 0xFFD7C2A2)
        val left = (spec.knives - state.thrown).coerceAtLeast(0)
        val knives = if (left == 1) copy.dagger else copy.fill(copy.daggers, left)
        ops += DrawOp.Label(knives, b.w / 2, b.h * 0.78f, b.w * 0.03f, 0xFFE6D3B4)
    }

    private class Board(val w: Float, val h: Float) {
        var cx = w / 2f
        var cy = h * 0.44f
        var radius = minOf(w, h) * 0.30f
    }

    private fun Board.background(ops: MutableList<DrawOp>) {
        ops += DrawOp.VGrad(0f, 0f, w, h, 0xFF1A120C, 0xFF3C2618)
        ops += DrawOp.Circle(cx, cy, radius * 1.8f, 0x33E7B56A)
        ops += DrawOp.Circle(cx, cy, radius * 1.15f, 0x22F0D09A)
        // Floor shadow and a few shavings so the workshop isn't a flat gradient.
        ops += DrawOp.RoundRect(w * 0.08f, h * 0.80f, w * 0.84f, h * 0.012f, 8f, 0x33000000)
        repeat(7) { i ->
            val x = w * (0.12f + i * 0.12f)
            val y = h * (0.16f + (i % 3) * 0.22f)
            ops += DrawOp.Line(x, y, x + w * 0.03f, y + h * 0.01f, 0x33C8A36A, w * 0.004f)
        }
    }

    private data class Palette(val bark: Long, val inner: Long, val heart: Long, val edge: Long, val ring: Long, val knot: Long, val band: Long?)

    private fun palette(wood: Wood) = when (wood) {
        Wood.OAK -> Palette(0xFF4A2C18, 0xFF6E4324, 0xFFF0D2A0, 0xFFC48448, 0xFFA86B38, 0xFF5A3418, null)
        Wood.IRONWOOD -> Palette(0xFF2C241C, 0xFF4A3A2C, 0xFFC8A078, 0xFF8A6848, 0xFF6E5038, 0xFF2A1C14, 0xFFC0C6CE)
        Wood.CHARRED -> Palette(0xFF1A1210, 0xFF3A2418, 0xFF5A3A28, 0xFF2A1A14, 0xFF3A241C, 0xFF140C0A, 0xFFE07030)
        Wood.APPLEWOOD -> Palette(0xFF5A2A22, 0xFF8A4030, 0xFFF2C2A0, 0xFFD47858, 0xFFC45A48, 0xFF6A2820, 0xFF7CB342)
        Wood.BIRCH -> Palette(0xFFD8D0C4, 0xFFEFE8DC, 0xFFF7F1E6, 0xFFE4D8C4, 0xFFD2C4B0, 0xFF2A241C, 0xFF2A241C)
        Wood.SPIRAL -> Palette(0xFF5A3418, 0xFF8A5A30, 0xFFE8C48A, 0xFFB87A40, 0xFF8A4E22, 0xFF4A2810, null)
        Wood.GOLDEN -> Palette(0xFF6A4A16, 0xFFC4963A, 0xFFFFE3A4, 0xFFE0B050, 0xFFC48A28, 0xFF6A4010, 0xFFFFE08A)
        Wood.CRACKED -> Palette(0xFF3A2418, 0xFF5A3824, 0xFFD8B48A, 0xFFA07048, 0xFF7A5030, 0xFF1A100C, 0xFF2A1A12)
        Wood.HEARTWOOD -> Palette(0xFF4A1E18, 0xFF7A3428, 0xFFE8A090, 0xFFC06048, 0xFF9A4030, 0xFF4A1814, null)
        Wood.RINGED -> Palette(0xFF3E2A18, 0xFF6A4830, 0xFFF6E0B8, 0xFFD0A060, 0xFF8A5A30, 0xFF3A2414, 0xFF2C1E14)
    }

    private fun log(b: Board, theta: Double, wood: Wood, alpha: Float, ops: MutableList<DrawOp>) {
        if (alpha <= 0f) return
        val p = palette(wood)
        val r = b.radius
        ops += DrawOp.Circle(b.cx, b.cy + r * 0.08f, r * 1.02f, 0x55000000)
        bark(b, theta, r * 1.08f, p.bark, ops)
        ops += DrawOp.Circle(b.cx, b.cy, r * 1.02f, p.inner)
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.96f, p.edge)
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.90f, p.heart)
        // Growth rings, rotated with the log. Centres drift so the rings aren't a bullseye.
        for (i in 1..6) {
            val k = i / 7f
            val ox = (cos(theta + i) * r * 0.03 * i).toFloat()
            val oy = (sin(theta * 0.8 + i) * r * 0.025 * i).toFloat()
            ops += DrawOp.Circle(b.cx + ox, b.cy + oy, r * (0.18f + k * 0.68f), p.ring, stroke = r * 0.012f)
        }
        // A couple of grain arcs.
        grain(b, theta, p.ring, ops)
        // Knot.
        val kx = b.cx + (cos(theta + 1.2) * r * 0.28).toFloat()
        val ky = b.cy + (sin(theta + 1.2) * r * 0.28).toFloat()
        ops += DrawOp.Circle(kx, ky, r * 0.07f, p.knot)
        ops += DrawOp.Circle(kx, ky, r * 0.035f, p.edge, stroke = r * 0.01f)
        p.band?.let { ops += DrawOp.Circle(b.cx, b.cy, r * 0.97f, it, stroke = r * 0.035f) }
        if (wood == Wood.CHARRED || wood == Wood.CRACKED) {
            repeat(5) { i ->
                val a = theta + i * 1.25
                ops += DrawOp.Line(
                    b.cx + (cos(a) * r * 0.15f).toFloat(), b.cy + (sin(a) * r * 0.15f).toFloat(),
                    b.cx + (cos(a + 0.25) * r * 0.88f).toFloat(), b.cy + (sin(a + 0.25) * r * 0.88f).toFloat(),
                    0xFF140C08, r * 0.014f,
                )
            }
        }
        if (wood == Wood.BIRCH) {
            repeat(4) { i ->
                val y = b.cy - r * 0.45f + i * r * 0.28f
                ops += DrawOp.Line(b.cx - r * 0.55f, y, b.cx + r * 0.5f, y + r * 0.04f, 0xFF2A241C, r * 0.018f)
            }
        }
        if (wood == Wood.SPIRAL) {
            val pts = ArrayList<Float>()
            for (s in 0..24) {
                val u = s / 24.0
                val a = theta + u * 5.2
                val rr = r * (0.12 + u * 0.7)
                pts += b.cx + (cos(a) * rr).toFloat()
                pts += b.cy + (sin(a) * rr).toFloat()
            }
            for (s in 0 until pts.size / 2 - 1) {
                ops += DrawOp.Line(pts[s * 2], pts[s * 2 + 1], pts[s * 2 + 2], pts[s * 2 + 3], 0xFF6A3818, r * 0.012f)
            }
        }
    }

    private fun bark(b: Board, theta: Double, radius: Float, color: Long, ops: MutableList<DrawOp>) {
        val pts = ArrayList<Float>(96)
        val n = 48
        for (i in 0 until n) {
            val a = LogThrow.TAU * i / n
            val wobble = 1.0 + 0.018 * sin(a * 7 + theta) + 0.01 * sin(a * 13 + 2)
            val rr = radius * wobble
            // a here is a math angle; map like the rim but the bark is the silhouette, rotated by theta.
            val world = a + theta
            pts += b.cx - (sin(world) * rr).toFloat()
            pts += b.cy + (cos(world) * rr).toFloat()
        }
        ops += DrawOp.Poly(pts, color)
    }

    private fun grain(b: Board, theta: Double, color: Long, ops: MutableList<DrawOp>) {
        val r = b.radius
        repeat(5) { i ->
            val a0 = theta + i * 1.1
            val pts = ArrayList<Float>()
            for (s in 0..8) {
                val u = s / 8.0
                val a = a0 + u * 0.8
                val rr = r * (0.25 + u * 0.5)
                pts += b.cx + (cos(a) * rr).toFloat()
                pts += b.cy + (sin(a) * rr * 0.92).toFloat()
            }
            // Stroke as a polyline of short segments.
            for (s in 0 until pts.size / 2 - 1) {
                ops += DrawOp.Line(pts[s * 2], pts[s * 2 + 1], pts[s * 2 + 2], pts[s * 2 + 3], 0x88604028, r * 0.008f)
            }
        }
    }

    private fun dagger(b: Board, world: Double, rim: Float, player: Boolean, ops: MutableList<DrawOp>) {
        val steel = if (player) 0xFFE8EEF2 else 0xFF9AA4AE
        val edge = if (player) 0xFFFFFFFF else 0xFFC5CED6
        val brass = if (player) 0xFFD4A24A else 0xFF8A7040
        val grip = if (player) 0xFF6B3A22 else 0xFF4A2C1C
        // ly > 0 sticks out of the log; ly < 0 is the blade in the wood.
        val blade = listOf(
            0f, -0.40f, 0.08f, -0.02f, 0.055f, 0.06f, -0.055f, 0.06f, -0.08f, -0.02f,
        )
        val guard = listOf(0.20f, 0.05f, 0.20f, 0.12f, -0.20f, 0.12f, -0.20f, 0.05f)
        val handle = listOf(0.055f, 0.11f, 0.06f, 0.30f, -0.06f, 0.30f, -0.055f, 0.11f)
        val pommel = listOf(0.09f, 0.29f, 0.09f, 0.36f, -0.09f, 0.36f, -0.09f, 0.29f)
        ops += DrawOp.Poly(place(b, blade, world, rim), steel)
        ops += DrawOp.Poly(place(b, guard, world, rim), brass)
        ops += DrawOp.Poly(place(b, handle, world, rim), grip)
        ops += DrawOp.Poly(place(b, pommel, world, rim), brass)
        val fuller = place(b, listOf(0f, -0.32f, 0f, 0.02f), world, rim)
        ops += DrawOp.Line(fuller[0], fuller[1], fuller[2], fuller[3], edge, rim * 0.012f)
    }

    /** Local points are (lx, ly) pairs as fractions of the radius. +ly is outward. */
    private fun place(b: Board, local: List<Float>, world: Double, rim: Float): List<Float> {
        val ox = -sin(world)
        val oy = cos(world)
        val tx = -cos(world)
        val ty = -sin(world)
        val out = ArrayList<Float>(local.size)
        var i = 0
        while (i < local.size) {
            val lx = local[i] * rim
            val ly = local[i + 1] * rim
            val radial = rim + ly
            out += (b.cx + ox * radial + tx * lx).toFloat()
            out += (b.cy + oy * radial + ty * lx).toFloat()
            i += 2
        }
        return out
    }

    private fun pickup(b: Board, world: Double, kind: PickupKind, ops: MutableList<DrawOp>) {
        val radial = b.radius * 0.98f
        val x = b.cx - (sin(world) * radial).toFloat()
        val y = b.cy + (cos(world) * radial).toFloat()
        val r = b.radius * 0.09f
        if (kind == PickupKind.APPLE) {
            ops += DrawOp.Circle(x, y, r, 0xFFD63A32)
            ops += DrawOp.Circle(x - r * 0.3f, y - r * 0.3f, r * 0.28f, 0x66FFFFFF)
            ops += DrawOp.Line(x, y - r, x, y - r * 1.55f, 0xFF6B3A1E, r * 0.18f)
            ops += DrawOp.Circle(x + r * 0.45f, y - r * 1.2f, r * 0.38f, 0xFF6FA84A)
        } else {
            ops += DrawOp.Circle(x, y, r, 0xFFF2C14E)
            ops += DrawOp.Circle(x, y, r * 0.62f, 0xFFC48A18, stroke = r * 0.12f)
        }
    }

    private fun stack(b: Board, count: Int, ops: MutableList<DrawOp>) {
        if (count <= 0) return
        val shown = count.coerceAtMost(12)
        val gap = b.w * 0.055f
        val total = (shown - 1) * gap
        val y = b.h * 0.86f
        for (i in 0 until shown) {
            val x = b.w / 2 - total / 2 + i * gap
            miniDagger(ops, x, y, b.w * 0.018f)
        }
    }

    private fun miniDagger(ops: MutableList<DrawOp>, x: Float, y: Float, s: Float) {
        ops += DrawOp.Poly(listOf(x, y - s * 2.2f, x + s * 0.35f, y - s * 0.2f, x - s * 0.35f, y - s * 0.2f), 0xFFE8EEF2)
        ops += DrawOp.Line(x - s * 0.8f, y, x + s * 0.8f, y, 0xFFD4A24A, s * 0.28f)
        ops += DrawOp.RoundRect(x - s * 0.28f, y, s * 0.56f, s * 1.3f, s * 0.15f, 0xFF6B3A22)
        ops += DrawOp.Circle(x, y + s * 1.45f, s * 0.32f, 0xFFD4A24A)
    }

    private fun flying(b: Board, state: ThrowState, ops: MutableList<DrawOp>) {
        val p = ((state.timeMs - state.flightStartMs).toFloat() / LogThrow.FLIGHT_MS).coerceIn(0f, 1f)
        val ease = 1f - (1f - p) * (1f - p) * (1f - p)
        val x = b.w / 2
        val y0 = b.h * 0.84f
        val y1 = b.cy + b.radius * 0.55f
        val y = y0 + (y1 - y0) * ease
        val s = b.radius * 0.22f
        miniDagger(ops, x, y, s)
    }

    private fun bounce(b: Board, state: ThrowState, ops: MutableList<DrawOp>) {
        val age = (state.timeMs - state.eventMs).coerceAtLeast(0) / 700f
        val u = age.coerceIn(0f, 1f)
        val x = b.w / 2 + sin(u * 6) * b.w * 0.04f
        val y = b.cy + b.radius + u * u * b.h * 0.22f
        val s = b.radius * 0.2f
        // Tilt by drawing the dagger offset; the tumble is a short line of sparks.
        miniDagger(ops, x.toFloat(), y, s)
        val ix = b.cx
        val iy = b.cy + b.radius
        repeat(6) { i ->
            val a = -0.6 + i * 0.24
            val len = b.radius * (0.12f + (1f - u) * 0.18f)
            ops += DrawOp.Line(ix, iy, ix + (sin(a) * len).toFloat(), iy + (cos(a) * len).toFloat(), 0xFFFFE7A0, b.radius * 0.012f)
        }
    }

    private fun chips(b: Board, state: ThrowState, theta: Double, ops: MutableList<DrawOp>) {
        state.fx.forEach { chip ->
            val life = ((state.timeMs - chip.born) / 700.0).coerceIn(0.0, 1.0)
            val dist = b.radius * (0.15f + life.toFloat() * chip.speed.toFloat())
            val x = b.cx - (sin(chip.dir) * dist).toFloat()
            val y = b.cy + (cos(chip.dir) * dist).toFloat() + (life * life * b.radius * 0.35f).toFloat()
            val color = if (chip.kind == 1) 0xFFFFE7A0 else listOf(0xFFC48448, 0xFFE8C48A, 0xFF8A5A30)[chip.kind % 3]
            val s = b.radius * 0.035f * (1f - life.toFloat() * 0.4f)
            val rot = chip.spin * life
            val pts = listOf(-s, -s * 0.4f, s, -s * 0.3f, s * 0.8f, s * 0.5f, -s * 0.7f, s * 0.4f)
            val placed = ArrayList<Float>()
            var i = 0
            while (i < pts.size) {
                val lx = pts[i]
                val ly = pts[i + 1]
                val cs = cos(rot).toFloat()
                val sn = sin(rot).toFloat()
                placed += x + lx * cs - ly * sn
                placed += y + lx * sn + ly * cs
                i += 2
            }
            ops += DrawOp.Poly(placed, color)
        }
    }

    private fun burst(b: Board, theta: Double, ageMs: Long, p: Palette, ops: MutableList<DrawOp>) {
        val u = (ageMs / LogThrow.BURST_MS.toFloat()).coerceIn(0f, 1f)
        val ease = 1f - (1f - u) * (1f - u)
        val pieces = 8
        for (i in 0 until pieces) {
            val a0 = theta + LogThrow.TAU * i / pieces
            val spread = ease * b.radius * 0.55f
            val cx = b.cx - (sin(a0 + 0.4) * spread).toFloat()
            val cy = b.cy + (cos(a0 + 0.4) * spread).toFloat()
            val start = Math.toDegrees(a0).toFloat() + 90f
            val sweep = 360f / pieces - 4f
            ops += DrawOp.Wedge(cx, cy, b.radius * (1f - ease * 0.15f), start, sweep, if (i % 2 == 0) p.heart else p.edge)
            ops += DrawOp.Wedge(cx, cy, b.radius * 0.45f, start, sweep, p.ring)
        }
        repeat(10) { i ->
            val a = theta + i
            val dist = b.radius * (1.1f + ease * 0.8f)
            ops += DrawOp.Circle(
                b.cx - (sin(a) * dist).toFloat(),
                b.cy + (cos(a) * dist).toFloat(),
                b.radius * 0.03f * (1f - ease * 0.5f),
                0xFFE8C48A,
            )
        }
    }

    private fun card(ops: MutableList<DrawOp>, x: Float, y: Float, w: Float, h: Float, edge: Long, face: Long) {
        ops += DrawOp.RoundRect(x, y, w, h, h * 0.16f, edge)
        ops += DrawOp.RoundRect(x + w * 0.015f, y + h * 0.06f, w * 0.97f, h * 0.88f, h * 0.12f, face)
    }

    private fun button(ops: MutableList<DrawOp>, x: Float, y: Float, w: Float, h: Float, label: String): List<Float> {
        ops += DrawOp.RoundRect(x, y, w, h, h * 0.4f, 0xFFF2C14E)
        ops += DrawOp.Label(label, x + w / 2, y + h * 0.66f, h * 0.42f, 0xFF2A160C, bold = true)
        return listOf(x, y, w, h)
    }
}
