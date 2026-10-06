package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A magnetic stone in the play ring (unit-circle coordinates). */
data class Stone(val x: Float, val y: Float)

/**
 * Magnetic cluster, in the style of Kluster: players take turns placing stones inside
 * the ring. A stone snaps when its pull circle meets another stone's body, and the whole
 * connected cluster goes back to the player who placed it. Placing your last stone wins.
 */
data class MagnetGame(
    val setting: Int,
    val seed: Long,
    val stones: List<Stone> = emptyList(),
    /** Stones left to place: index 0 is you, 1 is the computer. */
    val hands: List<Int> = listOf(HAND, HAND),
    val turn: Int = 1,
    /** Stones that snapped on the latest placement, for the message and animation. */
    val snapped: List<Stone> = emptyList(),
    val snappedBy: Int = 0,
    val winner: Int = 0,
) {
    init {
        require(setting in 0..3 && hands.size == 2 && hands.all { it >= 0 } && turn in listOf(1, -1) && winner in -1..1)
        require(stones.size + hands.sum() == 2 * HAND && stones.all { inside(it) })
    }

    val over: Boolean get() = winner != 0
    fun hand(player: Int) = hands[if (player == 1) 0 else 1]

    fun legal(p: Stone): Boolean = inside(p) && stones.none { dist(it, p) < 2 * RADIUS }

    /** Stones that would snap (the placed one included) if a stone went to [p]. */
    fun cluster(p: Stone): List<Stone> {
        val group = mutableListOf(p)
        val left = stones.toMutableList()
        var i = 0
        while (i < group.size) {
            val pulled = left.filter { reaches(it, group[i]) }
            group += pulled; left -= pulled.toSet()
            i++
        }
        return group
    }

    fun place(p: Stone): MagnetGame? {
        if (over || !legal(p)) return null
        val group = cluster(p)
        val hands = hands.toMutableList()
        val me = if (turn == 1) 0 else 1
        return if (group.size == 1) {
            hands[me] -= 1
            val won = hands[me] == 0
            copy(stones = stones + p, hands = hands, turn = if (won) turn else -turn, snapped = emptyList(), snappedBy = 0, winner = if (won) turn else 0)
        } else {
            // The placed stone comes straight back, along with every stone it pulled in.
            hands[me] += group.size - 1
            copy(stones = stones - group.drop(1).toSet(), hands = hands, turn = -turn, snapped = group, snappedBy = turn)
        }
    }

    companion object {
        const val HAND = 8
        const val RADIUS = 0.055f
        const val PULL = 0.2f
        fun dist(a: Stone, b: Stone) = hypot(a.x - b.x, a.y - b.y)
        /** The pull circle around [a] is touching the black body of [b], or the other way around. */
        fun reaches(a: Stone, b: Stone) = dist(a, b) < PULL + RADIUS
        fun inside(p: Stone) = hypot(p.x, p.y) <= 1f - RADIUS
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")
        fun start(seed: Long, setting: Int) = MagnetGame(setting, seed)
    }
}

object MagnetAi {
    fun choose(g: MagnetGame): Stone {
        checkpoint()
        val random = Random(g.seed * 104_729 + g.stones.size * 31 + g.hands.sum())
        val samples = listOf(12, 80, 160, 220)[g.setting]
        val randomPoints = List(samples) {
            val r = sqrt(random.nextFloat()) * (1f - MagnetGame.RADIUS)
            val a = random.nextFloat() * 6.2831855f
            Stone(r * cos(a), r * sin(a))
        }
        val grid = if (g.setting == 0) emptyList() else ring(if (g.setting == 1) 5 else 8)
        val pool = (grid + randomPoints).filter { g.legal(it) }
        if (pool.isEmpty()) {
            val origin = Stone(0f, 0f)
            return if (g.legal(origin)) origin else randomPoints.first()
        }
        if (g.setting == 0) {
            val calm = pool.filter { g.cluster(it).size == 1 }
            val use = if (calm.isNotEmpty() && random.nextInt(100) < 80) calm else pool
            return use.maxBy { gap(g, it) + random.nextFloat() * 0.05f }
        }
        // Prefer a spot that snaps nothing, keeps its distance, and does not sit in a gap the opponent can close.
        return pool.maxBy { p ->
            val snap = g.cluster(p).size
            val crowd = if (g.setting >= 2) g.stones.count { MagnetGame.dist(it, p) < MagnetGame.PULL * 2 } * 0.02f else 0f
            val trap = if (g.setting >= 2) bridges(g, p) else 0f
            -1000f * (snap - 1) + gap(g, p) - crowd - trap + if (g.setting == 3) edgeBonus(p) else 0f
        }
    }

    private fun gap(g: MagnetGame, p: Stone) = g.stones.minOfOrNull { MagnetGame.dist(it, p) } ?: 2f

    /** A point near the middle of two close stones is where the opponent can snap both. */
    private fun bridges(g: MagnetGame, p: Stone): Float {
        var danger = 0f
        val stones = g.stones
        for (i in stones.indices) for (j in i + 1 until stones.size) {
            val span = MagnetGame.dist(stones[i], stones[j])
            if (span > MagnetGame.PULL * 2 + MagnetGame.RADIUS) continue
            val mid = Stone((stones[i].x + stones[j].x) / 2f, (stones[i].y + stones[j].y) / 2f)
            if (MagnetGame.dist(p, mid) < MagnetGame.PULL) danger += 0.35f
        }
        return danger
    }

    private fun ring(steps: Int): List<Stone> = buildList {
        add(Stone(0f, 0f))
        for (band in 1..steps) {
            val radius = band / steps.toFloat() * (1f - MagnetGame.RADIUS)
            val count = 6 + band * 2
            for (k in 0 until count) {
                val angle = k * 6.2831855f / count
                add(Stone(radius * cos(angle), radius * sin(angle)))
            }
        }
    }

    /** Stones near the rim block less of the open centre. */
    private fun edgeBonus(p: Stone) = hypot(p.x, p.y) * 0.05f
}

object MagnetCodec {
    fun encode(g: MagnetGame) = listOf("1", g.setting.toString(), g.seed.toString(), g.stones.joinToString(";") { "${it.x},${it.y}" },
        g.hands.joinToString(","), g.turn.toString(), g.snapped.joinToString(";") { "${it.x},${it.y}" }, g.snappedBy.toString(), g.winner.toString()).joinToString("\n")
    fun decode(text: String): MagnetGame? = try {
        val l = text.split('\n'); require(l.size == 9 && l[0] == "1")
        fun stones(s: String) = if (s.isEmpty()) emptyList() else s.split(';').map { p -> p.split(',').let { require(it.size == 2); Stone(it[0].toFloat(), it[1].toFloat()) } }
        MagnetGame(l[1].toInt(), l[2].toLong(), stones(l[3]), l[4].split(',').map(String::toInt), l[5].toInt(), stones(l[6]), l[7].toInt(), l[8].toInt())
    } catch (_: IllegalArgumentException) { null }
}
