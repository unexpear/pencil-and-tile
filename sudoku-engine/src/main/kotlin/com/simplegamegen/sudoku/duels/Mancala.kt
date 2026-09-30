package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Kalah, the usual two-row mancala. Six pits a side, four stones each.
 * Stones sow to the right, through your own store, and skip the other store.
 * Landing in your store gives another turn. Landing in an empty pit on your
 * side captures the stones opposite. When one side has no stones left, the
 * other side keeps what is still in its pits. Most stones in store wins.
 * [setting] is the computer's strength. You move first.
 */
data class Mancala(
    val setting: Int,
    val seed: Long,
    val pits: List<Int> = OPENING,
    val turn: Int = 1,
    val last: Int? = null,
    val winner: Int = 0,
    val ended: Boolean = false,
) {
    init {
        require(setting in NAMES.indices && pits.size == HOLES && pits.all { it >= 0 } && pits.sum() == STONES)
        require(turn == 1 || turn == -1)
        require(last == null || last in pits.indices)
        val cleared = (0 until PITS).all { pits[it] == 0 } && (CPU_PITS).all { pits[it] == 0 }
        require(ended == cleared)
        require(ended || (0 until PITS).any { pits[it] > 0 })
        require(ended || CPU_PITS.any { pits[it] > 0 })
        val lead = when {
            !ended -> 0
            pits[YOU] > pits[CPU] -> 1
            pits[YOU] < pits[CPU] -> -1
            else -> 0
        }
        require(winner == lead)
    }

    val over: Boolean get() = ended
    val draw: Boolean get() = ended && winner == 0

    fun legalPits(): List<Int> {
        if (ended) return emptyList()
        val side = if (turn == 1) 0 until PITS else CPU_PITS
        return side.filter { pits[it] > 0 }
    }

    /** Sows [pit] for the player about to move. Null when that pit cannot be sown. */
    fun sow(pit: Int): Mancala? {
        if (pit !in legalPits()) return null
        val next = pits.toMutableList()
        var hand = next[pit]
        next[pit] = 0
        var cursor = pit
        val skip = if (turn == 1) CPU else YOU
        while (hand > 0) {
            cursor = (cursor + 1) % HOLES
            if (cursor == skip) continue
            next[cursor]++
            hand--
        }
        val ownStore = if (turn == 1) YOU else CPU
        val own = if (turn == 1) 0 until PITS else CPU_PITS
        if (cursor in own && next[cursor] == 1) {
            val across = opposite(cursor)
            if (next[across] > 0) {
                next[ownStore] += next[cursor] + next[across]
                next[cursor] = 0
                next[across] = 0
            }
        }
        val youEmpty = (0 until PITS).all { next[it] == 0 }
        val cpuEmpty = CPU_PITS.all { next[it] == 0 }
        val done = youEmpty || cpuEmpty
        if (done) {
            next[YOU] += (0 until PITS).sumOf { next[it] }
            next[CPU] += CPU_PITS.sumOf { next[it] }
            for (i in 0 until PITS) next[i] = 0
            for (i in CPU_PITS) next[i] = 0
        }
        val again = !done && cursor == ownStore
        val lead = when {
            !done -> 0
            next[YOU] > next[CPU] -> 1
            next[YOU] < next[CPU] -> -1
            else -> 0
        }
        return copy(
            pits = next,
            turn = if (done || again) turn else -turn,
            last = cursor,
            winner = lead,
            ended = done,
        )
    }

    companion object {
        const val PITS = 6
        const val HOLES = 14
        const val YOU = 6
        const val CPU = 13
        const val STONES = 48
        val CPU_PITS = 7..12
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")
        val OPENING = List(HOLES) { if (it == YOU || it == CPU) 0 else 4 }

        fun start(seed: Long, setting: Int) = Mancala(setting, seed)
        fun opposite(pit: Int) = 12 - pit
    }
}

object MancalaAi {
    /** A pit for the player about to move. Stronger settings look further ahead. */
    fun choose(g: Mancala): Int {
        checkpoint()
        val legal = g.legalPits()
        require(legal.isNotEmpty())
        val random = Random(g.seed xor (g.pits.fold(0) { n, stones -> n * 31 + stones }.toLong() shl 1) xor g.turn.toLong())
        if (g.setting == 0) return legal.random(random)
        val ordered = legal.sortedByDescending { onePly(g, it) }
        if (g.setting == 1) {
            val best = onePly(g, ordered.first())
            return ordered.filter { onePly(g, it) == best }.random(random)
        }
        val depth = if (g.setting == 2) 4 else 6
        var alpha = Int.MIN_VALUE / 4
        var pick = ordered.first()
        var pickScore = Int.MIN_VALUE / 4
        for (pit in ordered) {
            val next = g.sow(pit)!!
            val score = scoreFor(next, g.turn, depth - 1, alpha, Int.MAX_VALUE / 4)
            if (score > pickScore) {
                pickScore = score
                pick = pit
            }
            if (score > alpha) alpha = score
        }
        return pick
    }

    private fun onePly(g: Mancala, pit: Int) = standing(g.sow(pit)!!, g.turn)

    /** Higher is better for [player]. */
    private fun standing(g: Mancala, player: Int): Int {
        val mine = if (player == 1) g.pits[Mancala.YOU] else g.pits[Mancala.CPU]
        val opp = if (player == 1) g.pits[Mancala.CPU] else g.pits[Mancala.YOU]
        val finish = when (g.winner) {
            player -> 400
            -player -> -400
            else -> 0
        }
        return finish + mine - opp
    }

    private fun scoreFor(g: Mancala, player: Int, depth: Int, alpha0: Int, beta0: Int): Int {
        checkpoint()
        if (g.ended || depth == 0) return standing(g, player)
        val moves = g.legalPits()
        if (moves.isEmpty()) return standing(g, player)
        val mine = g.turn == player
        var alpha = alpha0
        var beta = beta0
        var best = if (mine) Int.MIN_VALUE / 4 else Int.MAX_VALUE / 4
        for (pit in moves.sortedByDescending { onePly(g, it) }) {
            val next = g.sow(pit)!!
            val score = scoreFor(next, player, depth - 1, alpha, beta)
            if (mine) {
                if (score > best) best = score
                if (best > alpha) alpha = best
                if (alpha >= beta) break
            } else {
                if (score < best) best = score
                if (best < beta) beta = best
                if (alpha >= beta) break
            }
        }
        return best
    }
}

object MancalaCodec {
    fun encode(g: Mancala) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.pits.joinToString(","),
        g.turn.toString(),
        g.last?.toString() ?: "",
        if (g.ended) "1" else "0",
    ).joinToString("\n")

    fun decode(text: String): Mancala? = try {
        val lines = text.split('\n')
        require(lines.size == 7 && lines[0] == "1")
        val pits = lines[3].split(',').map { it.toInt() }
        val ended = lines[6] == "1"
        val winner = if (!ended || pits.size != Mancala.HOLES) 0 else when {
            pits[Mancala.YOU] > pits[Mancala.CPU] -> 1
            pits[Mancala.YOU] < pits[Mancala.CPU] -> -1
            else -> 0
        }
        Mancala(lines[1].toInt(), lines[2].toLong(), pits, lines[4].toInt(), lines[5].toIntOrNull(), winner, ended)
    } catch (_: IllegalArgumentException) { null }
}
