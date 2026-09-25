package com.simplegamegen.sudoku.tabletop

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.math.abs
import kotlin.math.sign
import kotlin.random.Random

/** Bounded alpha-beta for perfect-information games. Strength is not a solved-game claim. */
object TableAi {
    fun choose(state: TableState, level: Int, seed: Long): Move? {
        require(level in 0..3)
        checkpoint()
        val choices = state.legalMoves(); if (choices.isEmpty()) return null
        val rng = Random(seed)
        if (level == 0) return choices.random(rng)
        if (state is DominoState) {
            // Deliberately uses only this player's hand and the public chain, never the other hand.
            return choices.shuffled(rng).maxByOrNull { move ->
                if (move.op != Op.MOVE) 0 else {
                    val tile = DominoState.tiles[move.from]
                    val remaining = state.hands[state.player] - move.from
                    val after = state.play(move)!!
                    val flexibility = remaining.count { id -> DominoState.tiles[id].let { it.a == after.chain.first().a || it.b == after.chain.first().a || it.a == after.chain.last().b || it.b == after.chain.last().b } }
                    tile.pips + (if (level >= 2) flexibility * 4 else 0) + (if (level >= 3 && tile.a == tile.b) 5 else 0)
                }
            }
        }
        require(state is CheckersState || state is ReversiState)
        var nodes = 0
        val budget = listOf(0, 1500, 6000, 18000)[level]
        fun evaluate(s: TableState): Int {
            when (s.result) { Result.WON -> return 100_000; Result.LOST -> return -100_000; Result.DRAW -> return 0; else -> Unit }
            return when (s) {
                is CheckersState -> s.board.mapIndexed { i, p -> p.sign * (if (abs(p) == 2) 175 else if (p != 0) 100 + (if (p > 0) 7 - i / 8 else i / 8) * 4 else 0) }.sum()
                is ReversiState -> s.board.mapIndexed { i, p -> p * when {
                    i in listOf(0, 7, 56, 63) -> 120
                    i in listOf(9, 14, 49, 54) -> -25
                    i / 8 in listOf(0, 7) || i % 8 in listOf(0, 7) -> 12
                    else -> 2
                } }.sum() + (s.legalMoves().size * s.turn * 3)
                else -> 0
            }
        }
        fun search(s: TableState, depth: Int, alpha: Int, beta: Int): Int {
            checkpoint()
            if (++nodes >= budget || depth <= 0 || s.result != Result.PLAYING) return evaluate(s)
            var a = alpha; var b = beta; var best = if (s.turn == 1) Int.MIN_VALUE else Int.MAX_VALUE
            for (move in s.legalMoves()) {
                val next = s.play(move)!!
                val score = search(next, depth - 1, a, b)
                if (s.turn == 1) { best = maxOf(best, score); a = maxOf(a, best) } else { best = minOf(best, score); b = minOf(b, best) }
                if (a >= b || nodes >= budget) break
            }
            return if (best == Int.MIN_VALUE || best == Int.MAX_VALUE) evaluate(s) else best
        }
        // Iterative deepening: retain a whole completed iteration on budget exhaustion.
        var best = choices.first()
        val ordered = choices.shuffled(rng)
        for (depth in 1..(level + 1)) {
            var iterationBest = best; var value = Int.MIN_VALUE
            for (move in ordered) {
                val score = search(state.play(move)!!, depth - 1, -200_000, 200_000) * state.turn
                if (score > value) { value = score; iterationBest = move }
                if (nodes >= budget) break
            }
            if (nodes >= budget) break
            best = iterationBest
        }
        return best
    }
}
