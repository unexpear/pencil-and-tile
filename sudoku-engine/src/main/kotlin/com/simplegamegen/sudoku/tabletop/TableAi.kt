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
        val choices = state.legalMoves()
        if (choices.isEmpty()) return null
        val rng = Random(seed)
        if (state is DominoState) return domino(state, choices, level, rng)
        if (level == 0) return easy(state, choices, rng)
        require(state is CheckersState || state is ReversiState)
        var nodes = 0
        val budget = listOf(0, 4_000, 12_000, 24_000)[level]
        val deadline = System.nanoTime() + listOf(0L, 40L, 90L, 160L)[level] * 1_000_000L
        fun evaluate(s: TableState): Int {
            when (s.result) { Result.WON -> return 100_000; Result.LOST -> return -100_000; Result.DRAW -> return 0; else -> Unit }
            return when (s) {
                is CheckersState -> checkersValue(s)
                is ReversiState -> reversiValue(s)
                else -> 0
            }
        }
        fun search(s: TableState, depth: Int, alpha: Int, beta: Int): Int {
            checkpoint()
            if (++nodes >= budget || System.nanoTime() > deadline || depth <= 0 || s.result != Result.PLAYING) return evaluate(s)
            var a = alpha
            var b = beta
            var best = if (s.turn == 1) Int.MIN_VALUE else Int.MAX_VALUE
            val moves = ordered(s)
            for (move in moves) {
                val next = s.play(move)!!
                val score = search(next, depth - 1, a, b)
                if (s.turn == 1) { best = maxOf(best, score); a = maxOf(a, best) } else { best = minOf(best, score); b = minOf(b, best) }
                if (a >= b || nodes >= budget || System.nanoTime() > deadline) break
            }
            return if (best == Int.MIN_VALUE || best == Int.MAX_VALUE) evaluate(s) else best
        }
        var best = choices.first()
        val root = ordered(state)
        val maxDepth = listOf(0, 3, 4, 5)[level]
        for (depth in 1..maxDepth) {
            if (nodes >= budget || System.nanoTime() > deadline) break
            var iterationBest = best
            var value = Int.MIN_VALUE
            var finished = true
            val seq = listOf(best).filter { it in root } + root.filter { it != best }
            for (move in seq) {
                if (System.nanoTime() > deadline && depth > 1) { finished = false; break }
                val score = search(state.play(move)!!, depth - 1, -200_000, 200_000) * state.turn
                if (System.nanoTime() > deadline && depth > 1) { finished = false; break }
                if (score > value) { value = score; iterationBest = move }
                if (nodes >= budget) { finished = false; break }
            }
            if (!finished) break
            best = iterationBest
        }
        return best
    }

    /** Uses only this player's hand and the public chain, never the other hand. */
    private fun domino(state: DominoState, choices: List<Move>, level: Int, rng: Random): Move {
        val ranked = choices.sortedByDescending { move ->
            if (move.op != Op.MOVE) 0 else {
                val tile = DominoState.tiles[move.from]
                val remaining = state.hands[state.player] - move.from
                val after = state.play(move)!!
                val flexibility = remaining.count { id ->
                    DominoState.tiles[id].let {
                        val ends = listOf(after.chain.first().a, after.chain.last().b)
                        it.a in ends || it.b in ends
                    }
                }
                when (level) {
                    0 -> tile.pips
                    1 -> tile.pips + flexibility * 2
                    else -> tile.pips + flexibility * 4 + if (level >= 3 && tile.a == tile.b) 8 else 0
                }
            }
        }
        if (level == 0) return noisy(ranked, rng)
        return ranked.first()
    }

    private fun easy(state: TableState, choices: List<Move>, rng: Random): Move {
        val ranked = when (state) {
            is CheckersState -> choices.sortedByDescending { checkersEasy(state, it) }
            is ReversiState -> choices.sortedByDescending { reversiEasy(state, it) }
            else -> choices
        }
        return noisy(ranked, rng)
    }

    private fun noisy(ranked: List<Move>, rng: Random): Move {
        if (ranked.size <= 1) return ranked.first()
        val weights = intArrayOf(5, 2, 1)
        val n = minOf(weights.size, ranked.size)
        var total = 0
        for (i in 0 until n) total += weights[i]
        var roll = rng.nextInt(total)
        for (i in 0 until n) {
            roll -= weights[i]
            if (roll < 0) return ranked[i]
        }
        return ranked.first()
    }

    private fun checkersEasy(state: CheckersState, move: Move): Int {
        val jumped = abs(move.from / 8 - move.to / 8) == 2
        val victim = if (!jumped) 0 else abs(state.board[(move.from / 8 + move.to / 8) / 2 * 8 + (move.from % 8 + move.to % 8) / 2])
        val piece = state.board[move.from]
        val advance = if (piece > 0) move.from / 8 - move.to / 8 else move.to / 8 - move.from / 8
        val next = state.play(move) as CheckersState
        val hung = next.turn == -state.turn && next.legalMoves().any { abs(it.from / 8 - it.to / 8) == 2 }
        return victim * 120 + advance * 4 + (if (abs(piece) == 2) 6 else 0) + (if (hung) -50 else 10)
    }

    private fun reversiEasy(state: ReversiState, move: Move): Int {
        val cell = move.from
        val corner = cell == 0 || cell == 7 || cell == 56 || cell == 63
        val xSquare = cell == 9 || cell == 14 || cell == 49 || cell == 54
        val edge = cell / 8 == 0 || cell / 8 == 7 || cell % 8 == 0 || cell % 8 == 7
        return (if (corner) 100 else 0) + state.flips(cell).size * 4 + (if (edge) 8 else 0) - if (xSquare) 30 else 0
    }

    private fun ordered(state: TableState): List<Move> {
        val moves = state.legalMoves()
        return when (state) {
            is CheckersState -> moves.sortedByDescending { checkersEasy(state, it) }
            is ReversiState -> moves.sortedByDescending { reversiEasy(state, it) }
            else -> moves
        }
    }

    private fun checkersValue(s: CheckersState): Int {
        var score = 0
        for (i in s.board.indices) {
            val p = s.board[i]
            if (p == 0) continue
            val king = abs(p) == 2
            val advance = if (p > 0) 7 - i / 8 else i / 8
            val material = if (king) 240 else 100 + advance * 6
            val home = if (!king && ((p > 0 && i / 8 == 7) || (p < 0 && i / 8 == 0))) 10 else 0
            score += p.sign * (material + home)
        }
        return score + s.legalMoves().size * s.turn * 2
    }

    private fun reversiValue(s: ReversiState): Int {
        val empties = s.board.count { it == 0 }
        val disc = if (empties > 20) 2 else if (empties > 8) 6 else 14
        var score = 0
        for (i in s.board.indices) {
            val p = s.board[i]
            if (p == 0) continue
            val weight = when (i) {
                0, 7, 56, 63 -> 160
                9 -> if (s.board[0] == 0) -40 else 10
                14 -> if (s.board[7] == 0) -40 else 10
                49 -> if (s.board[56] == 0) -40 else 10
                54 -> if (s.board[63] == 0) -40 else 10
                else -> if (i / 8 == 0 || i / 8 == 7 || i % 8 == 0 || i % 8 == 7) 20 else disc
            }
            score += p * weight
        }
        return score + s.legalMoves().size * s.turn * 8
    }
}
