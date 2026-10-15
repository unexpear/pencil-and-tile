package com.simplegamegen.sudoku.arcade

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
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
    /** Smooth disc. [inner] is the color at ([cx], [cy]); [outer] is the color at [radius], then held. */
    data class Radial(val x: Float, val y: Float, val w: Float, val h: Float, val cx: Float, val cy: Float, val radius: Float, val inner: Long, val outer: Long) : DrawOp
    data class Circle(val x: Float, val y: Float, val r: Float, val color: Long, val stroke: Float = 0f) : DrawOp
    data class RoundRect(val x: Float, val y: Float, val w: Float, val h: Float, val radius: Float, val color: Long, val stroke: Float = 0f) : DrawOp
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Long, val stroke: Float) : DrawOp
    data class Poly(val pts: List<Float>, val color: Long, val stroke: Float = 0f) : DrawOp
    /** Draw [inner] only inside the polygon. */
    data class Clip(val pts: List<Float>, val inner: List<DrawOp>) : DrawOp
    /** Filled polygon. Color runs from [c0] at ([x0], [y0]) to [c1] at ([x1], [y1]). */
    data class Shade(val pts: List<Float>, val x0: Float, val y0: Float, val c0: Long, val x1: Float, val y1: Float, val c1: Long) : DrawOp
    /** [startDeg] follows Android: 0 is 3 o'clock, positive is clockwise. */
    data class Wedge(val cx: Float, val cy: Float, val r: Float, val startDeg: Float, val sweep: Float, val color: Long) : DrawOp
    data class Label(val text: String, val x: Float, val y: Float, val size: Float, val color: Long, val bold: Boolean = false, val align: Int = 1) : DrawOp
}

data class HotSpot(val id: String, val x: Float, val y: Float, val w: Float, val h: Float, val label: String)

data class LogThrowFrame(val ops: List<DrawOp>, val hots: List<HotSpot>)

object LogThrowArt {
    const val LEVELS_PER_PAGE = 16

    fun pageCount(): Int = (LogThrow.LEVEL_COUNT + LEVELS_PER_PAGE - 1) / LEVELS_PER_PAGE

    fun frame(state: ThrowState, w: Float, h: Float, copy: LogThrowCopy = LogThrowCopy(), paused: Boolean = false, page: Int = 0): LogThrowFrame {
        val b = Board(w, h)
        val ops = ArrayList<DrawOp>()
        val hots = ArrayList<HotSpot>()
        b.background(ops)
        when (state.phase) {
            Phase.MENU -> menu(b, state, copy, ops, hots)
            Phase.SELECT -> levels(b, state, copy, page, ops, hots)
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

    private fun levels(b: Board, state: ThrowState, copy: LogThrowCopy, page: Int, ops: MutableList<DrawOp>, hots: MutableList<HotSpot>) {
        val pages = pageCount()
        val p = page.coerceIn(0, pages - 1)
        ops += DrawOp.Label(copy.choose, b.w / 2, b.h * 0.055f, b.w * 0.05f, 0xFFF6E7C1, bold = true)
        ops += DrawOp.Label(copy.fill(copy.starTotal, state.starTotal, LogThrow.LEVEL_COUNT * 3), b.w / 2, b.h * 0.095f, b.w * 0.032f, 0xFFE7C98A)
        val cols = 4
        val rows = 4
        val gap = b.w * 0.028f
        val top = b.h * 0.13f
        val bottom = b.h * 0.82f
        val cell = minOf((b.w - gap * (cols + 1)) / cols, (bottom - top - gap * (rows + 1)) / rows)
        val gridW = cols * cell + (cols - 1) * gap
        val x0 = (b.w - gridW) / 2
        val start = p * LEVELS_PER_PAGE
        for (slot in 0 until LEVELS_PER_PAGE) {
            val n = start + slot + 1
            if (n > LogThrow.LEVEL_COUNT) break
            val spec = LogThrow.levelSpec(n)
            val col = slot % cols
            val row = slot / cols
            val x = x0 + col * (cell + gap)
            val y = top + row * (cell + gap)
            val open = state.unlocked(n)
            val stars = state.stars.getOrElse(n - 1) { 0 }
            val face = if (!open) 0xFF3A2A22 else if (spec.boss) 0xFF6A3A18 else if (spec.challenge != null) 0xFF3E4A28 else 0xFF6B4428
            ops += DrawOp.RoundRect(x, y, cell, cell, cell * 0.16f, face)
            if (spec.boss) ops += DrawOp.RoundRect(x + cell * 0.07f, y + cell * 0.07f, cell * 0.86f, cell * 0.86f, cell * 0.12f, 0xFFE7C98A, stroke = cell * 0.03f)
            ops += DrawOp.Label("$n", x + cell / 2, y + cell * 0.42f, cell * 0.30f, if (open) 0xFFFFF6E4 else 0xFF8A7564, bold = true)
            if (!open) {
                ops += DrawOp.Label(copy.locked, x + cell / 2, y + cell * 0.70f, cell * 0.14f, 0xFFB7A090)
            } else {
                val label = when (stars) {
                    0 -> copy.noStars
                    1 -> copy.oneStar
                    else -> copy.fill(copy.stars, stars)
                }
                repeat(3) { s ->
                    val cx = x + cell * (0.30f + s * 0.20f)
                    val cy = y + cell * 0.72f
                    ops += DrawOp.Circle(cx, cy, cell * 0.055f, if (s < stars) 0xFFF2C14E else 0xFF2A1A12, stroke = if (s < stars) 0f else cell * 0.012f)
                }
                val bits = listOf(copy.level + " $n", label) + if (spec.boss) listOf(copy.boss) else emptyList()
                hots += HotSpot("L$n", x, y, cell, cell, bits.joinToString(" · "))
            }
        }
        val margin = b.w * 0.04f
        val arrowW = b.w * 0.15f
        val gapW = b.w * 0.03f
        val bh = b.h * 0.062f
        val by = b.h * 0.845f
        val modesW = b.w - margin * 2 - arrowW * 2 - gapW * 2
        glyph(ops, margin, by, arrowW, bh, "‹", p > 0)
        if (p > 0) hots += HotSpot("prev", margin, by, arrowW, bh, "Back")
        val modes = button(ops, margin + arrowW + gapW, by, modesW, bh, copy.modes)
        hots += HotSpot("modes", modes[0], modes[1], modes[2], modes[3], copy.modes)
        val nextX = margin + arrowW + gapW + modesW + gapW
        glyph(ops, nextX, by, arrowW, bh, "›", p < pages - 1)
        if (p < pages - 1) hots += HotSpot("next", nextX, by, arrowW, bh, "Next")
        val dotsY = b.h * 0.955f
        for (i in 0 until pages) {
            val cx = b.w / 2 + (i - (pages - 1) / 2f) * b.w * 0.045f
            ops += DrawOp.Circle(cx, dotsY, b.w * 0.012f, if (i == p) 0xFFF2C14E else 0xFF5A4034)
        }
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
        if (state.phase == Phase.CLEAR) burst(b, spec.program.angleAt(state.eventMs), age, palette(spec.wood), state.stuck, ops)
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
        ops += DrawOp.VGrad(0f, 0f, w, h, 0xFF2C1C12, 0xFF3A2416)
        val plankH = h * 0.078f
        var y = 0f
        var i = 0
        while (y < h) {
            val face = if (i % 2 == 0) 0xFF3C2618 else 0xFF4A3020
            ops += DrawOp.RoundRect(0f, y, w, plankH + 1f, 0f, face)
            val grain = if (i % 2 == 0) 0x18301810L else 0x221A0E08L
            ops += DrawOp.Line(w * 0.02f, y + plankH * 0.32f, w * 0.98f, y + plankH * 0.36f, grain, h * 0.0016f)
            ops += DrawOp.Line(w * 0.06f, y + plankH * 0.68f, w * 0.90f, y + plankH * 0.62f, 0x14000000, h * 0.0014f)
            ops += DrawOp.Line(0f, y + plankH, w, y + plankH, 0xFF1A100C, h * 0.0028f)
            ops += DrawOp.Line(0f, y + plankH + h * 0.0015f, w, y + plankH + h * 0.0015f, 0x334A3428, h * 0.0012f)
            val nailY = y + plankH * 0.5f
            val nx1 = w * (0.07f + (i % 3) * 0.015f)
            val nx2 = w * (0.90f - (i % 2) * 0.03f)
            ops += DrawOp.Circle(nx1, nailY, w * 0.007f, 0xFF241810)
            ops += DrawOp.Circle(nx1 - w * 0.001f, nailY - w * 0.0015f, w * 0.003f, 0xFF9A9084)
            ops += DrawOp.Circle(nx2, nailY, w * 0.007f, 0xFF241810)
            ops += DrawOp.Circle(nx2 - w * 0.001f, nailY - w * 0.0015f, w * 0.003f, 0xFF9A9084)
            y += plankH
            i++
        }
        wallKnot(ops, w * 0.16f, h * 0.20f, w * 0.035f)
        wallKnot(ops, w * 0.84f, h * 0.63f, w * 0.028f)
        val vx = w / 2f
        val vy = h / 2f
        val reach = hypot((w / 2f).toDouble(), (h / 2f).toDouble()).toFloat()
        ops += DrawOp.Radial(0f, 0f, w, h, vx, vy, reach, 0x00000000, 0xC0120C08)
    }

    private fun wallKnot(ops: MutableList<DrawOp>, x: Float, y: Float, s: Float) {
        ops += DrawOp.Circle(x, y, s, 0xFF2A1A10)
        ops += DrawOp.Circle(x, y, s * 0.62f, 0xFF6A4428, stroke = s * 0.14f)
        ops += DrawOp.Circle(x, y, s * 0.28f, 0xFF3A2414)
        ops += DrawOp.Circle(x - s * 0.18f, y - s * 0.16f, s * 0.12f, 0x44FFFFFF)
    }

    private data class Palette(val bark: Long, val inner: Long, val heart: Long, val edge: Long, val ring: Long, val knot: Long)

    private fun palette(wood: Wood) = when (wood) {
        Wood.OAK -> Palette(0xFF4A2C18, 0xFF6E4324, 0xFFF0D2A0, 0xFFC48448, 0xFFA86B38, 0xFF5A3418)
        Wood.IRONWOOD -> Palette(0xFF2C241C, 0xFF4A3A2C, 0xFFC8A078, 0xFF8A6848, 0xFF6E5038, 0xFF2A1C14)
        Wood.CHARRED -> Palette(0xFF241610, 0xFF4A3024, 0xFF7A5040, 0xFF5A3424, 0xFF4A2C1C, 0xFF140C0A)
        Wood.APPLEWOOD -> Palette(0xFF5A2A22, 0xFF8A4030, 0xFFF2C2A0, 0xFFD47858, 0xFFC45A48, 0xFF6A2820)
        Wood.BIRCH -> Palette(0xFFD8D0C4, 0xFFEFE8DC, 0xFFF7F1E6, 0xFFE4D8C4, 0xFFD2C4B0, 0xFF2A241C)
        Wood.SPIRAL -> Palette(0xFF5A3418, 0xFF8A5A30, 0xFFE8C48A, 0xFFC48A48, 0xFF8A4E22, 0xFF4A2810)
        Wood.GOLDEN -> Palette(0xFF6A4A16, 0xFFC4963A, 0xFFFFE3A4, 0xFFE0B050, 0xFFC48A28, 0xFF6A4010)
        Wood.CRACKED -> Palette(0xFF3A2418, 0xFF5A3824, 0xFFD8B48A, 0xFFA07048, 0xFF7A5030, 0xFF1A100C)
        Wood.HEARTWOOD -> Palette(0xFF4A1E18, 0xFF7A3428, 0xFFE8A090, 0xFFC06048, 0xFF9A4030, 0xFF4A1814)
        Wood.RINGED -> Palette(0xFF3E2A18, 0xFF6A4830, 0xFFF6E0B8, 0xFFD0A060, 0xFF8A5A30, 0xFF3A2414)
    }

    private fun log(b: Board, theta: Double, wood: Wood, alpha: Float, ops: MutableList<DrawOp>) {
        if (alpha <= 0f) return
        val p = palette(wood)
        val r = b.radius
        val saved = b.cy
        b.cy += r * 0.06f
        rim(b, theta, wood, 1.02, 0x55000000, 0f, ops)
        b.cy = saved
        rim(b, theta, wood, 1.04, 0xFF120C0A, 0f, ops)
        rim(b, theta, wood, 1.0, p.bark, 0f, ops)
        rim(b, theta, wood, 0.965, p.edge, 0f, ops)
        rim(b, theta, wood, 1.0, 0xFF1A100C, r * 0.014f, ops)
        rim(b, theta, wood, 1.012, 0x66E8D0B0, r * 0.008f, ops)
        barkHash(b, theta, wood, ops)
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.90f, p.inner)
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.865f, p.heart)
        ops += DrawOp.Circle(b.cx + r * 0.045f, b.cy + r * 0.05f, r * 0.72f, 0x18000000)
        ops += DrawOp.Circle(b.cx - r * 0.04f, b.cy - r * 0.05f, r * 0.50f, 0x16FFFFFF)
        growthRings(b, theta, wood, p, ops)
        radialCracks(b, theta, wood, ops)
        features(b, theta, wood, p, ops)
    }

    /** Radius multiplier in log-local angle. Boss woods change the outline, not only the colour. */
    private fun silhouette(wood: Wood, a: Double): Double {
        val wobble = 1.0 + 0.012 * sin(a * 7) + 0.006 * sin(a * 13)
        return when (wood) {
            Wood.SPIRAL -> {
                val frac = ((a / LogThrow.TAU) * 10.0).let { it - it.toInt() }
                when {
                    frac < 0.12 -> 0.96 + 0.20 * (frac / 0.12)
                    frac < 0.32 -> 1.16
                    frac < 0.44 -> 1.16 - 0.20 * ((frac - 0.32) / 0.12)
                    else -> 0.96
                }
            }
            Wood.CHARRED -> {
                val frac = ((a / LogThrow.TAU) * 12.0).let { it - it.toInt() }
                val fromTip = if (frac < 0.5) frac else 1.0 - frac
                if (fromTip < 0.16) 1.24 - 0.24 * (fromTip / 0.16) else 1.0
            }
            Wood.HEARTWOOD -> 1.0 + 0.08 * cos(a * 5)
            Wood.APPLEWOOD -> wobble + 0.22 * exp(-(wrapDist(a, 2.1) * wrapDist(a, 2.1)) / 0.045)
            Wood.CRACKED -> {
                val d = wrapDist(a, 0.7)
                val notch = if (d < 0.36) (1.0 - d / 0.36) * 0.34 else 0.0
                wobble - notch
            }
            else -> wobble
        }
    }

    private fun wrapDist(a: Double, b: Double): Double {
        var d = abs(a - b) % LogThrow.TAU
        if (d > LogThrow.TAU / 2) d = LogThrow.TAU - d
        return d
    }

    private fun rim(b: Board, theta: Double, wood: Wood, scale: Double, color: Long, stroke: Float, ops: MutableList<DrawOp>) {
        val n = 120
        val pts = ArrayList<Float>(n * 2)
        for (i in 0 until n) {
            val a = LogThrow.TAU * i / n
            val rr = b.radius * silhouette(wood, a) * scale
            val world = a + theta
            pts += b.cx - (sin(world) * rr).toFloat()
            pts += b.cy + (cos(world) * rr).toFloat()
        }
        ops += DrawOp.Poly(pts, color, stroke)
    }

    private fun barkHash(b: Board, theta: Double, wood: Wood, ops: MutableList<DrawOp>) {
        val n = 72
        for (i in 0 until n) {
            if (i % 3 != 0) continue
            val a = LogThrow.TAU * i / n
            val s = silhouette(wood, a)
            val r0 = b.radius * s * 0.905
            val r1 = b.radius * s * 0.99
            val world = a + theta
            val tilt = if (i % 4 == 0) 0.03 else -0.02
            val x0 = b.cx - (sin(world) * r0).toFloat()
            val y0 = b.cy + (cos(world) * r0).toFloat()
            val x1 = b.cx - (sin(world + tilt) * r1).toFloat()
            val y1 = b.cy + (cos(world + tilt) * r1).toFloat()
            val color = if (i % 6 == 0) 0x55FFFFFFL else 0x66000000L
            ops += DrawOp.Line(x0, y0, x1, y1, color, b.radius * 0.012f)
        }
    }

    private fun growthRings(b: Board, theta: Double, wood: Wood, p: Palette, ops: MutableList<DrawOp>) {
        val count = if (wood == Wood.RINGED) 12 else 6
        val maxF = when (wood) {
            Wood.CRACKED -> 0.60f
            Wood.RINGED -> 0.80f
            else -> 0.74f
        }
        val minF = 0.14f
        for (i in 1..count) {
            val frac = minF + (maxF - minF) * i / (count + 1f)
            val color = if (i % 2 == 0) p.ring else p.edge
            val stroke = b.radius * if (wood == Wood.RINGED) 0.008f else 0.013f
            ring(b, theta, frac, i * 0.7, color, stroke, ops)
        }
        ops += DrawOp.Circle(b.cx, b.cy, b.radius * 0.045f, p.knot)
    }

    /** One closed growth ring. Squash follows the log-local angle, so the oval turns with the log. */
    private fun ring(b: Board, theta: Double, frac: Float, phase: Double, color: Long, stroke: Float, ops: MutableList<DrawOp>) {
        val n = 96
        val pts = ArrayList<Float>(n * 2)
        for (i in 0 until n) {
            val a = LogThrow.TAU * i / n
            val irreg = 1.0 + 0.018 * sin(2 * a + phase) + 0.007 * sin(3 * a + phase * 1.3)
            val rr = b.radius * frac * irreg
            val world = a + theta
            pts += b.cx - (sin(world) * rr).toFloat()
            pts += b.cy + (cos(world) * rr).toFloat()
        }
        ops += DrawOp.Poly(pts, color, stroke)
    }

    private fun radialCracks(b: Board, theta: Double, wood: Wood, ops: MutableList<DrawOp>) {
        val count = when (wood) {
            Wood.CRACKED -> 5
            Wood.CHARRED -> 4
            Wood.BIRCH -> 2
            else -> 3
        }
        val reach = if (wood == Wood.CRACKED) 0.84f else 0.70f
        val width = b.radius * if (wood == Wood.CRACKED) 0.016f else 0.008f
        val ink = if (wood == Wood.BIRCH) 0x663A3028 else 0xFF2A1A10
        repeat(count) { i ->
            val a0 = 0.55 + i * (LogThrow.TAU / count) * 0.96
            val kink = if (i % 2 == 0) 0.16 else -0.13
            val mid = 0.36 + (i % 3) * 0.07
            val p0 = at(b, theta, a0, 0.10)
            val p1 = at(b, theta, a0 + kink, mid)
            val p2 = at(b, theta, a0 + kink * 0.35, reach.toDouble())
            ops += DrawOp.Line(p0.first, p0.second, p1.first, p1.second, ink, width)
            ops += DrawOp.Line(p1.first, p1.second, p2.first, p2.second, ink, width * 0.75f)
        }
    }

    private fun at(b: Board, theta: Double, angle: Double, frac: Double): Pair<Float, Float> {
        val world = angle + theta
        val rr = b.radius * frac
        val x = b.cx - (sin(world) * rr).toFloat()
        val y = b.cy + (cos(world) * rr).toFloat()
        return x to y
    }

    private fun features(b: Board, theta: Double, wood: Wood, p: Palette, ops: MutableList<DrawOp>) {
        val r = b.radius
        when (wood) {
            Wood.IRONWOOD -> barrel(b, theta, ops)
            Wood.GOLDEN -> medallion(b, ops)
            Wood.BIRCH -> birchMarks(b, theta, ops)
            Wood.RINGED -> {
                ops += DrawOp.Circle(b.cx, b.cy, r * 0.86f, 0xFF2C1E14, stroke = r * 0.05f)
                ops += DrawOp.Circle(b.cx - r * 0.008f, b.cy - r * 0.008f, r * 0.86f, 0xFF8A6848, stroke = r * 0.012f)
            }
            Wood.OAK -> knot(b, theta, 1.15, 0.34f, r * 0.13f, p, ops)
            Wood.APPLEWOOD -> knot(b, theta, 2.1, 0.30f, r * 0.15f, p, ops)
            Wood.CHARRED -> embers(b, theta, ops)
            Wood.CRACKED -> notch(b, theta, p, ops)
            Wood.HEARTWOOD -> heart(b, theta, r * 0.55f, 0xFF9A3830, ops)
            Wood.SPIRAL -> spiralGrain(b, theta, ops)
        }
    }

    private fun barrel(b: Board, theta: Double, ops: MutableList<DrawOp>) {
        val r = b.radius
        hoop(b, r * 0.74f, r * 0.05f, ops)
        hoop(b, r * 0.50f, r * 0.038f, ops)
        repeat(8) { i ->
            val a = theta + LogThrow.TAU * i / 8.0
            val p = at(b, 0.0, a, 0.74)
            ops += DrawOp.Circle(p.first, p.second, r * 0.028f, 0xFF3A4046)
            ops += DrawOp.Circle(p.first - r * 0.006f, p.second - r * 0.006f, r * 0.012f, 0xFFE4EAF0)
        }
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.16f, 0xFF8E98A2)
        ops += DrawOp.Circle(b.cx - r * 0.03f, b.cy - r * 0.03f, r * 0.07f, 0xFFD8DEE4)
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.035f, 0xFF3E464C)
    }

    private fun hoop(b: Board, radius: Float, width: Float, ops: MutableList<DrawOp>) {
        ops += DrawOp.Circle(b.cx, b.cy, radius, 0xFF5C656C, stroke = width)
        ops += DrawOp.Circle(b.cx - width * 0.15f, b.cy - width * 0.18f, radius, 0xFFE4EAF0, stroke = width * 0.28f)
    }

    private fun medallion(b: Board, ops: MutableList<DrawOp>) {
        val r = b.radius
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.62f, 0xFF8C5E22, stroke = r * 0.028f)
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.62f, 0xFFFFE08A, stroke = r * 0.01f)
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.40f, 0xFF8C5E22, stroke = r * 0.02f)
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.18f, 0xFFFFE3A4)
        ops += DrawOp.Circle(b.cx - r * 0.04f, b.cy - r * 0.04f, r * 0.07f, 0x66FFFFFF)
        ops += DrawOp.Circle(b.cx, b.cy, r * 0.05f, 0xFF8C5E22, stroke = r * 0.012f)
    }

    private fun birchMarks(b: Board, theta: Double, ops: MutableList<DrawOp>) {
        val r = b.radius
        repeat(8) { i ->
            val a = 0.4 + i * 0.78
            val frac = 0.22 + (i % 4) * 0.14
            val p = at(b, theta, a, frac.toDouble())
            val len = r * 0.055f
            val tx = -cos(a + theta).toFloat()
            val ty = -sin(a + theta).toFloat()
            ops += DrawOp.Line(p.first - tx * len, p.second - ty * len, p.first + tx * len, p.second + ty * len, 0xFF2A241C, r * 0.012f)
        }
        val peel = at(b, theta, 2.4, 0.28)
        ops += DrawOp.Circle(peel.first, peel.second, r * 0.16f, 0xFFD8CBB8, stroke = r * 0.018f)
        ops += DrawOp.Circle(peel.first, peel.second, r * 0.09f, 0xFFF7F1E6)
    }

    private fun knot(b: Board, theta: Double, angle: Double, dist: Float, size: Float, p: Palette, ops: MutableList<DrawOp>) {
        val c = at(b, theta, angle, dist.toDouble())
        ops += DrawOp.Circle(c.first, c.second, size, p.knot)
        ops += DrawOp.Circle(c.first, c.second, size * 0.72f, p.edge, stroke = size * 0.12f)
        ops += DrawOp.Circle(c.first, c.second, size * 0.42f, p.ring, stroke = size * 0.1f)
        ops += DrawOp.Circle(c.first, c.second, size * 0.16f, 0xFF2A160C)
        ops += DrawOp.Circle(c.first - size * 0.18f, c.second - size * 0.16f, size * 0.1f, 0x55FFFFFF)
    }

    private fun embers(b: Board, theta: Double, ops: MutableList<DrawOp>) {
        val r = b.radius
        repeat(5) { i ->
            val p = at(b, theta, 0.4 + i * 1.2, 0.55 + (i % 2) * 0.12)
            ops += DrawOp.Circle(p.first, p.second, r * 0.028f, 0xFFE07030)
            ops += DrawOp.Circle(p.first, p.second, r * 0.012f, 0xFFFFE0A0)
        }
    }

    private fun notch(b: Board, theta: Double, p: Palette, ops: MutableList<DrawOp>) {
        val mouth = 0.40
        val a = 0.7
        val outerL = at(b, theta, a - mouth, 1.14)
        val midL = at(b, theta, a - mouth * 0.42, 0.70)
        val tip = at(b, theta, a, 0.34)
        val midR = at(b, theta, a + mouth * 0.42, 0.70)
        val outerR = at(b, theta, a + mouth, 1.14)
        ops += DrawOp.Poly(
            listOf(outerL.first, outerL.second, midL.first, midL.second, tip.first, tip.second, midR.first, midR.second, outerR.first, outerR.second),
            p.bark,
        )
        val c0 = at(b, theta, a - 0.05, 0.92)
        val c1 = at(b, theta, a, 0.40)
        val c2 = at(b, theta, a + 0.05, 0.92)
        ops += DrawOp.Poly(listOf(c0.first, c0.second, c1.first, c1.second, c2.first, c2.second), 0xFF140C08)
    }

    private fun heart(b: Board, theta: Double, scale: Float, fill: Long, ops: MutableList<DrawOp>) {
        val local = listOf(
            0f, 0.34f, 0.28f, 0.06f, 0.30f, -0.14f, 0.12f, -0.28f,
            0f, -0.10f, -0.12f, -0.28f, -0.30f, -0.14f, -0.28f, 0.06f,
        )
        val pts = spin(b, local, theta, scale)
        ops += DrawOp.Poly(pts, fill)
        ops += DrawOp.Poly(pts, 0xFF4A1814, b.radius * 0.012f)
    }

    private fun spiralGrain(b: Board, theta: Double, ops: MutableList<DrawOp>) {
        val r = b.radius
        var prev: Pair<Float, Float>? = null
        for (s in 0..40) {
            val u = s / 40.0
            val a = u * 5.4
            val p = at(b, theta, a, 0.08 + u * 0.62)
            prev?.let { q -> ops += DrawOp.Line(q.first, q.second, p.first, p.second, 0xFF6A3818, r * 0.014f) }
            prev = p
        }
    }

    private fun spin(b: Board, local: List<Float>, theta: Double, scale: Float): List<Float> {
        val ox = -sin(theta)
        val oy = cos(theta)
        val tx = -cos(theta)
        val ty = -sin(theta)
        val out = ArrayList<Float>(local.size)
        var i = 0
        while (i < local.size) {
            val lx = local[i] * scale
            val ly = local[i + 1] * scale
            out += (b.cx + tx * lx + ox * ly).toFloat()
            out += (b.cy + ty * lx + oy * ly).toFloat()
            i += 2
        }
        return out
    }

    private fun dagger(b: Board, world: Double, rim: Float, player: Boolean, ops: MutableList<DrawOp>) {
        val ox = -sin(world)
        val oy = cos(world)
        val tx = -cos(world)
        val ty = -sin(world)
        daggerDraw(ops, player, rim) { lx, ly ->
            val radial = rim + ly * rim
            val px = (b.cx + ox * radial + tx * lx * rim).toFloat()
            val py = (b.cy + oy * radial + ty * lx * rim).toFloat()
            px to py
        }
    }

    /** Same sprite as a stuck dagger. [length] is tip-to-guard. [turn] 0 points the tip up; positive turns clockwise. */
    private fun daggerIcon(ops: MutableList<DrawOp>, x: Float, y: Float, length: Float, turn: Double, player: Boolean) {
        val rim = length / 0.52f
        val ox = -sin(turn)
        val oy = cos(turn)
        val tx = -cos(turn)
        val ty = -sin(turn)
        daggerDraw(ops, player, rim) { lx, ly ->
            val px = (x + ox * ly * rim + tx * lx * rim).toFloat()
            val py = (y + oy * ly * rim + ty * lx * rim).toFloat()
            px to py
        }
    }

    private fun daggerDraw(ops: MutableList<DrawOp>, player: Boolean, rim: Float, map: (Float, Float) -> Pair<Float, Float>) {
        fun poly(local: List<Float>): List<Float> {
            val out = ArrayList<Float>(local.size)
            var i = 0
            while (i < local.size) {
                val p = map(local[i], local[i + 1])
                out += p.first
                out += p.second
                i += 2
            }
            return out
        }
        fun shade(local: List<Float>, ax: Float, ay: Float, c0: Long, bx: Float, by: Float, c1: Long) {
            val a = map(ax, ay)
            val b = map(bx, by)
            ops += DrawOp.Shade(poly(local), a.first, a.second, c0, b.first, b.second, c1)
        }
        val steelL = if (player) 0xFFF7FBFE else 0xFFC5CED6
        val steelD = if (player) 0xFF5A6670 else 0xFF3E484E
        val outline = 0xFF1A1E22
        val blade = listOf(0f, -0.52f, 0.055f, -0.14f, 0.040f, 0.05f, -0.040f, 0.05f, -0.055f, -0.14f)
        shade(blade, -0.055f, -0.22f, steelL, 0.055f, -0.22f, steelD)
        val fuller = listOf(0f, -0.42f, 0.020f, -0.28f, 0.014f, -0.08f, 0f, 0.0f, -0.014f, -0.08f, -0.020f, -0.28f)
        ops += DrawOp.Poly(poly(fuller), 0xFF4E585F)
        val ridge = poly(listOf(-0.004f, -0.40f, -0.004f, -0.08f))
        ops += DrawOp.Line(ridge[0], ridge[1], ridge[2], ridge[3], 0xCCFFFFFF, rim * 0.007f)
        ops += DrawOp.Poly(poly(blade), outline, rim * 0.016f)
        val guard = listOf(-0.20f, 0.012f, -0.155f, 0.108f, 0.155f, 0.108f, 0.20f, 0.012f)
        shade(guard, 0f, 0.012f, 0xFFF0D48A, 0f, 0.108f, 0xFF8C5E22)
        ops += DrawOp.Poly(poly(guard), outline, rim * 0.012f)
        for (i in 0 until 6) {
            val y0 = 0.108f + i * 0.034f
            val y1 = y0 + 0.032f
            val hw = 0.046f
            val band = listOf(-hw, y0, -hw * 0.92f, y1, hw * 0.92f, y1, hw, y0)
            val dark = if (i % 2 == 0) 0xFF3F2416 else 0xFF6A3E24
            val light = if (i % 2 == 0) 0xFF8B5434 else 0xFFC48A58
            shade(band, -hw, y0, light, hw, y0, dark)
            if (i % 2 == 1) {
                val cord = poly(listOf(-hw, (y0 + y1) / 2f, hw, (y0 + y1) / 2f))
                ops += DrawOp.Line(cord[0], cord[1], cord[2], cord[3], 0xFF5C3318, rim * 0.006f)
            }
        }
        val pom = ArrayList<Float>(16)
        for (k in 0 until 8) {
            val ang = LogThrow.TAU * k / 8.0 - LogThrow.TAU / 16.0
            pom += (cos(ang) * 0.075).toFloat()
            pom += (0.350 + sin(ang) * 0.046).toFloat()
        }
        shade(pom, 0f, 0.30f, 0xFFF0D48A, 0f, 0.40f, 0xFF8C5E22)
        ops += DrawOp.Poly(poly(pom), outline, rim * 0.012f)
        val hi = map(-0.018f, 0.325f)
        ops += DrawOp.Circle(hi.first, hi.second, rim * 0.016f, 0xFFFFF6D8)
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
        val gap = b.w * 0.08f
        val total = (shown - 1) * gap
        val length = minOf(b.w * 0.055f, b.h * 0.045f)
        val y = b.h * 0.845f
        for (i in 0 until shown) {
            val x = b.w / 2 - total / 2 + i * gap
            daggerIcon(ops, x, y, length, 0.0, true)
        }
    }

    private fun flying(b: Board, state: ThrowState, ops: MutableList<DrawOp>) {
        val p = ((state.timeMs - state.flightStartMs).toFloat() / LogThrow.FLIGHT_MS).coerceIn(0f, 1f)
        val ease = 1f - (1f - p) * (1f - p) * (1f - p)
        val y0 = b.h * 0.845f
        val y1 = b.cy + b.radius
        val y = y0 + (y1 - y0) * ease
        daggerIcon(ops, b.w / 2, y, b.radius * 0.52f, 0.0, true)
    }

    private fun bounce(b: Board, state: ThrowState, ops: MutableList<DrawOp>) {
        val age = (state.timeMs - state.eventMs).coerceAtLeast(0) / 700f
        val u = age.coerceIn(0f, 1f)
        val x = b.w / 2 + sin(u * 6.0) * b.w * 0.05
        val y = b.cy + b.radius + u * u * b.h * 0.20f
        daggerIcon(ops, x.toFloat(), y, b.radius * 0.52f, u * 4.2, true)
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

    private fun burst(b: Board, theta: Double, ageMs: Long, p: Palette, stuck: List<StuckKnife>, ops: MutableList<DrawOp>) {
        val u = (ageMs / LogThrow.BURST_MS.toFloat()).coerceIn(0f, 1f)
        val ease = 1f - (1f - u) * (1f - u)
        val grav = u * u * b.h * 0.32f
        val chunks = arrayOf(
            floatArrayOf(-0.95f, -0.45f, 0.70f, -0.72f, 1.05f, 0.10f, 0.40f, 0.85f, -0.55f, 0.62f),
            floatArrayOf(-0.70f, -0.80f, 0.85f, -0.40f, 0.60f, 0.55f, -0.20f, 0.95f, -0.90f, 0.15f),
            floatArrayOf(-1.00f, -0.20f, 0.15f, -0.85f, 0.95f, -0.15f, 0.70f, 0.70f, -0.25f, 0.80f, -0.85f, 0.35f),
            floatArrayOf(-0.55f, -0.90f, 0.80f, -0.55f, 0.90f, 0.25f, 0.10f, 0.90f, -0.75f, 0.30f),
        )
        for (i in 0 until 8) {
            val dir = theta + LogThrow.TAU * i / 8.0 + (i % 3 - 1) * 0.06
            val spread = b.radius * (0.40f + ease * (1.05f + (i % 4) * 0.08f))
            val cx = b.cx - (sin(dir) * spread).toFloat()
            val cy = b.cy + (cos(dir) * spread).toFloat() + grav * (0.45f + (i % 5) * 0.16f)
            val spin = (if (i % 2 == 0) 1.0 else -1.0) * (0.5 + u * 4.8) * (0.85 + (i % 3) * 0.12)
            val scale = b.radius * (0.22f + (i % 4) * 0.025f)
            val shape = chunks[i % chunks.size].toList()
            val pts = xform(shape, cx, cy, spin, scale)
            val wood = if (i % 2 == 0) p.heart else p.edge
            ops += DrawOp.Poly(barkLip(pts, cx, cy, scale * 0.22f), p.bark)
            ops += DrawOp.Poly(pts, wood)
            val grain = ArrayList<DrawOp>(6)
            for (k in 1..4) {
                val ringColor = if (k % 2 == 0) p.ring else p.edge
                grain += DrawOp.Poly(xform(ringLocal(0.18f + k * 0.16f), cx, cy, spin, scale), ringColor, scale * 0.055f)
            }
            grain += DrawOp.Circle(cx, cy, scale * 0.10f, p.knot)
            ops += DrawOp.Clip(pts, grain)
            ops += DrawOp.Poly(pts, 0xFF1A100C, scale * 0.04f)
        }
        val splinter = listOf(0f, -1f, 0.16f, -0.15f, 0f, 1f, -0.16f, -0.15f)
        repeat(14) { i ->
            val a = theta + i * 0.47
            val dist = b.radius * (0.15f + ease * (0.85f + (i % 5) * 0.12f))
            val x = b.cx - (sin(a) * dist).toFloat()
            val y = b.cy + (cos(a) * dist).toFloat() + grav * (0.35f + (i % 3) * 0.18f)
            val spin = u * (1.6 + (i % 4) * 0.45) * if (i % 2 == 0) 1.0 else -1.0
            val len = b.radius * 0.055f * (1f - u * 0.25f)
            val color = listOf(0xFFE8C48A, 0xFFC48448, 0xFFF6E7C1, 0xFF8A5A30)[i % 4]
            ops += DrawOp.Poly(xform(splinter, x, y, spin, len), color)
        }
        stuck.forEachIndexed { i, knife ->
            val a = theta + knife.local
            val out = b.radius * (1.02f + ease * 0.95f)
            val x = b.cx - (sin(a) * out).toFloat()
            val y = b.cy + (cos(a) * out).toFloat() + grav * (0.7f + (i % 3) * 0.12f)
            val tumble = a + (if (i % 2 == 0) 1.0 else -1.0) * u * 2.8
            daggerIcon(ops, x, y, b.radius * 0.52f, tumble, knife.player)
        }
    }

    private fun ringLocal(radius: Float): List<Float> {
        val n = 28
        val pts = ArrayList<Float>(n * 2)
        for (i in 0 until n) {
            val a = LogThrow.TAU * i / n
            pts += (cos(a) * radius).toFloat()
            pts += (sin(a) * radius).toFloat()
        }
        return pts
    }

    private fun xform(local: List<Float>, cx: Float, cy: Float, spin: Double, scale: Float): List<Float> {
        val cs = cos(spin).toFloat()
        val sn = sin(spin).toFloat()
        val out = ArrayList<Float>(local.size)
        var i = 0
        while (i < local.size) {
            val x = local[i] * scale
            val y = local[i + 1] * scale
            out += cx + x * cs - y * sn
            out += cy + x * sn + y * cs
            i += 2
        }
        return out
    }

    private fun barkLip(pts: List<Float>, cx: Float, cy: Float, extra: Float): List<Float> {
        val x0 = pts[0]
        val y0 = pts[1]
        val x1 = pts[2]
        val y1 = pts[3]
        val mx = (x0 + x1) / 2f - cx
        val my = (y0 + y1) / 2f - cy
        val len = hypot(mx.toDouble(), my.toDouble()).toFloat().coerceAtLeast(1f)
        val nx = mx / len * extra
        val ny = my / len * extra
        return listOf(x0, y0, x1, y1, x1 + nx, y1 + ny, x0 + nx, y0 + ny)
    }

    private fun glyph(ops: MutableList<DrawOp>, x: Float, y: Float, w: Float, h: Float, text: String, enabled: Boolean) {
        ops += DrawOp.RoundRect(x, y, w, h, h * 0.4f, if (enabled) 0xFFF2C14E else 0xFF4A3428)
        ops += DrawOp.Label(text, x + w / 2, y + h * 0.68f, h * 0.55f, if (enabled) 0xFF2A160C else 0xFF8A7564, bold = true)
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
