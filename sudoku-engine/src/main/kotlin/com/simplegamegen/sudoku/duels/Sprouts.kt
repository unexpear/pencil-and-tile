package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** A point in the unit square. */
data class Pt(val x: Float, val y: Float)

/** Join spots [a] and [b] (equal for a loop), optionally steering the curve through [via]. */
data class SproutMove(val a: Int, val b: Int, val via: Pt? = null)

/**
 * Sprouts. Each move draws a curve between two spots (or from a spot back to itself)
 * without crossing any curve, then adds a new spot on it. A spot may have at most
 * three curve ends. The player who makes the last move wins.
 *
 * Curves are routed on an [N]×[N] grid: every drawn curve and every other spot is an
 * obstacle, so a route exists exactly when the two spots share a region.
 */
data class SproutsGame(
    val setting: Int,
    val seed: Long,
    val dots: List<Pt>,
    val degree: List<Int>,
    val curves: List<List<Pt>> = emptyList(),
    val turn: Int = 1,
    val winner: Int = 0,
) {
    init {
        require(setting in 0..3 && dots.size == degree.size && dots.isNotEmpty() && degree.all { it in 0..3 })
        require(dots.all { it.x in 0f..1f && it.y in 0f..1f } && curves.all { c -> c.size >= 2 && c.all { it.x in 0f..1f && it.y in 0f..1f } })
        require(turn in listOf(1, -1) && winner in -1..1 && degree.sum() == curves.size * 2)
    }

    val over: Boolean get() = winner != 0
    fun lives(i: Int) = 3 - degree[i]

    private fun cell(p: Pt): Int = cellAt(p.x, p.y)

    /** Grid cells covered by curves (drawn 4-connected so nothing can slip between them). */
    private val curveCells: BooleanArray by lazy {
        val blocked = BooleanArray(N * N)
        curves.forEach { c -> c.zipWithNext().forEach { (p, q) -> rasterize(p, q, blocked) } }
        blocked
    }

    private fun dotBlock(i: Int, into: BooleanArray) {
        val c = cell(dots[i]); val cx = c % N; val cy = c / N
        for (dy in -1..1) for (dx in -1..1) { val x = cx + dx; val y = cy + dy; if (x in 0 until N && y in 0 until N) into[y * N + x] = true }
    }

    /** Shortest grid route; null when the spots are in different regions. */
    fun route(m: SproutMove): List<Pt>? {
        if (m.a !in dots.indices || m.b !in dots.indices) return null
        if (m.a == m.b && (degree[m.a] > 1 || m.via == null)) return null
        if (m.a != m.b && (degree[m.a] > 2 || degree[m.b] > 2)) return null
        val blocked = curveCells.copyOf()
        dots.indices.filter { it != m.a && it != m.b }.forEach { dotBlock(it, blocked) }
        val start = cell(dots[m.a]); val goal = cell(dots[m.b])
        val cells = if (m.via == null) {
            blocked[goal] = false
            bfs(start, goal, blocked)
        } else {
            val mid = cell(m.via)
            if (blocked[mid] || mid == start || mid == goal) return null
            blocked[goal] = true
            val first = bfs(start, mid, blocked) ?: return null
            first.forEach { blocked[it] = true }
            blocked[mid] = false; blocked[start] = m.a != m.b
            blocked[goal] = false
            // Neighbors of the start used by the first leg stay blocked so a loop closes from another side.
            val second = bfs(mid, goal, blocked) ?: return null
            first + second.drop(1)
        } ?: return null
        if (cells.size < 5) return null
        val pts = cells.map { Pt((it % N + 0.5f) / N, (it / N + 0.5f) / N) }.toMutableList()
        pts[0] = dots[m.a]; pts[pts.lastIndex] = dots[m.b]
        return smooth(pts)
    }

    /**
     * Turns the grid staircase into a gentle curve: drop points on straight runs, then cut
     * corners. Cut points lie on segments between free cells, so the curve stays clear.
     */
    private fun smooth(path: List<Pt>): List<Pt> {
        var pts = path.filterIndexed { i, p ->
            i == 0 || i == path.lastIndex || run {
                val a = path[i - 1]; val b = path[i + 1]
                abs((p.x - a.x) * (b.y - p.y) - (p.y - a.y) * (b.x - p.x)) > 1e-6f
            }
        }
        repeat(3) {
            val next = mutableListOf(pts.first())
            for (i in 0 until pts.lastIndex) {
                val a = pts[i]; val b = pts[i + 1]
                next += Pt(a.x * 0.75f + b.x * 0.25f, a.y * 0.75f + b.y * 0.25f)
                next += Pt(a.x * 0.25f + b.x * 0.75f, a.y * 0.25f + b.y * 0.75f)
            }
            next += pts.last()
            pts = next
        }
        return pts
    }

    private fun bfs(start: Int, goal: Int, blocked: BooleanArray): List<Int>? {
        val prev = IntArray(N * N) { -2 }
        val queue = IntArray(N * N); var head = 0; var tail = 0
        queue[tail++] = start; prev[start] = -1
        while (head < tail) {
            val c = queue[head++]
            if (c == goal) break
            val x = c % N; val y = c / N
            for (n in intArrayOf(if (x > 0) c - 1 else -1, if (x < N - 1) c + 1 else -1, if (y > 0) c - N else -1, if (y < N - 1) c + N else -1)) {
                if (n < 0 || prev[n] != -2 || (blocked[n] && n != goal)) continue
                prev[n] = c; queue[tail++] = n
            }
        }
        if (prev[goal] == -2) return null
        val path = mutableListOf<Int>(); var c = goal
        while (c != -1) { path += c; c = prev[c] }
        return path.reversed()
    }

    fun play(m: SproutMove): SproutsGame? {
        if (over) return null
        val path = route(m) ?: return null
        // The new spot goes halfway along the curve's length.
        val lengths = path.zipWithNext().runningFold(0f) { acc, (p, q) -> acc + kotlin.math.hypot(q.x - p.x, q.y - p.y) }
        val midIndex = lengths.indexOfFirst { it >= lengths.last() / 2 }.coerceIn(1, path.lastIndex - 1)
        val newDot = path[midIndex]
        val deg = degree.toMutableList()
        deg[m.a] += 1; deg[m.b] += 1; deg += 2
        val next = copy(dots = dots + newDot, degree = deg, curves = curves + listOf(path.subList(0, midIndex + 1), path.subList(midIndex, path.size)), turn = -turn)
        return if (next.legalMoves().isEmpty()) next.copy(winner = turn) else next
    }

    /** One representative route per possible pairing; loops use a nearby waypoint. */
    fun legalMoves(): List<SproutMove> {
        val out = mutableListOf<SproutMove>()
        for (a in dots.indices) {
            if (degree[a] > 2) continue
            for (b in a + 1 until dots.size) if (degree[b] <= 2) {
                val m = SproutMove(a, b)
                if (route(m) != null) out += m
            }
            if (degree[a] <= 1) loopVia(a)?.let { out += SproutMove(a, a, it) }
        }
        return out
    }

    private fun loopVia(a: Int): Pt? {
        val p = dots[a]
        for (r in listOf(4, 6, 3, 8)) for (k in 0 until 8) {
            val angle = k * Math.PI / 4
            val via = Pt((p.x + r * cos(angle).toFloat() / N).coerceIn(0.01f, 0.99f), (p.y + r * sin(angle).toFloat() / N).coerceIn(0.01f, 0.99f))
            if (route(SproutMove(a, a, via)) != null) return via
        }
        return null
    }

    companion object {
        const val N = 48
        val NAMES = listOf("Easy · 2 spots", "Medium · 3 spots", "Hard · 4 spots", "Expert · 5 spots")
        fun cellAt(x: Float, y: Float): Int {
            val cx = floor(x * N).toInt().coerceIn(0, N - 1); val cy = floor(y * N).toInt().coerceIn(0, N - 1)
            return cy * N + cx
        }

        private fun rasterize(p: Pt, q: Pt, into: BooleanArray) {
            val steps = maxOf(1, (maxOf(abs(q.x - p.x), abs(q.y - p.y)) * N * 4).toInt())
            var prev = cellAt(p.x, p.y)
            into[prev] = true
            for (s in 1..steps) {
                val t = s.toFloat() / steps
                val c = cellAt(p.x + (q.x - p.x) * t, p.y + (q.y - p.y) * t)
                if (c != prev && c % N != prev % N && c / N != prev / N) into[(prev / N) * N + c % N] = true
                into[c] = true
                prev = c
            }
        }

        fun start(seed: Long, setting: Int): SproutsGame {
            val random = Random(seed)
            val n = setting + 2
            val turn0 = random.nextDouble() * Math.PI * 2
            val dots = List(n) { k ->
                val a = turn0 + k * 2 * Math.PI / n
                val r = 0.26f + random.nextFloat() * 0.06f
                Pt((0.5f + r * cos(a).toFloat()), (0.5f + r * sin(a).toFloat()))
            }
            return SproutsGame(setting, seed, dots, List(n) { 0 })
        }
    }
}

object SproutsAi {
    fun choose(g: SproutsGame): SproutMove {
        checkpoint()
        val random = Random(g.seed * 7_127 + g.curves.size)
        val moves = variants(g, random).shuffled(random)
        if (g.setting == 0) return moves.first()
        val sample = moves.take(listOf(0, 20, 12, 18)[g.setting])
        // Win now if possible.
        sample.firstOrNull { m -> g.play(m)?.winner == g.turn }?.let { return it }
        if (g.setting == 1) return sample.first()
        // Avoid moves that let the opponent win straight away.
        val replies = listOf(0, 0, 8, 14)[g.setting]
        return sample.firstOrNull { m ->
            checkpoint()
            val after = g.play(m) ?: return@firstOrNull false
            variants(after, random).shuffled(random).take(replies).none { r -> after.play(r)?.winner == after.turn }
        } ?: sample.first()
    }

    /** Legal moves plus a few steered versions, since the side a curve passes on matters. */
    private fun variants(g: SproutsGame, random: Random): List<SproutMove> {
        val base = g.legalMoves()
        if (g.setting < 2) return base
        val extra = base.filter { it.a != it.b }.flatMap { m ->
            List(2) { Pt(random.nextFloat() * 0.9f + 0.05f, random.nextFloat() * 0.9f + 0.05f) }.map { SproutMove(m.a, m.b, it) }
                .filter { g.route(it) != null }
        }
        return base + extra
    }
}

object SproutsCodec {
    private fun pts(list: List<Pt>) = list.joinToString(";") { "${it.x},${it.y}" }
    private fun parse(s: String) = if (s.isEmpty()) emptyList() else s.split(';').map { p -> p.split(',').let { require(it.size == 2); Pt(it[0].toFloat(), it[1].toFloat()) } }
    fun encode(g: SproutsGame) = (listOf("1", g.setting.toString(), g.seed.toString(), pts(g.dots), g.degree.joinToString(","),
        g.turn.toString(), g.winner.toString()) + g.curves.map(::pts)).joinToString("\n")
    fun decode(text: String): SproutsGame? = try {
        val l = text.split('\n'); require(l.size >= 7 && l[0] == "1")
        SproutsGame(l[1].toInt(), l[2].toLong(), parse(l[3]), l[4].split(',').map(String::toInt), l.drop(7).map(::parse), l[5].toInt(), l[6].toInt())
    } catch (_: IllegalArgumentException) { null }
}
