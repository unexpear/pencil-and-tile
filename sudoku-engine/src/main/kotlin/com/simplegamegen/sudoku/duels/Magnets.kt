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
 * the ring. A stone that lands within [PULL] of others snaps to them, and the whole
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
            val pulled = left.filter { dist(it, group[i]) < PULL }
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
        fun inside(p: Stone) = hypot(p.x, p.y) <= 1f - RADIUS
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")
        fun start(seed: Long, setting: Int) = MagnetGame(setting, seed)
    }
}

object MagnetAi {
    fun choose(g: MagnetGame): Stone {
        checkpoint()
        val random = Random(g.seed * 104_729 + g.stones.size * 31 + g.hands.sum())
        val samples = listOf(6, 80, 300, 900)[g.setting]
        val points = List(samples) {
            val r = sqrt(random.nextFloat()) * (1f - MagnetGame.RADIUS)
            val a = random.nextFloat() * 6.2831855f
            Stone(r * cos(a), r * sin(a))
        }.filter { g.legal(it) }.ifEmpty { listOf(Stone(0f, 0f)) }
        if (g.setting == 0) return points.minBy { g.cluster(it).size }
        // Prefer a spot that snaps nothing and keeps the most distance from other stones;
        // stronger players also prefer spots that leave the opponent less room.
        return points.maxBy { p ->
            val snap = g.cluster(p).size
            val gap = g.stones.minOfOrNull { MagnetGame.dist(it, p) } ?: 2f
            val crowd = if (g.setting >= 2) g.stones.count { MagnetGame.dist(it, p) < MagnetGame.PULL * 2 } * 0.01f else 0f
            -1000f * (snap - 1) + gap - crowd + (if (g.setting == 3) edgeBonus(p) else 0f)
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
