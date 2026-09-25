package com.simplegamegen.sudoku.tabletop

import kotlin.math.abs
import kotlin.math.sign

/** English draughts: forward men, short kings, forced captures, crown ends turn. */
data class CheckersState(
    val board: List<Int>, override val turn: Int = 1, val forced: Int? = null,
    val quiet: Int = 0, val positions: List<String> = emptyList(), override val result: Result = Result.PLAYING,
) : TableState {
    private fun choices(captures: Boolean): List<Move> = buildList {
        for (cell in board.indices) {
            if (board[cell].sign != turn || (forced != null && forced != cell)) continue
            val rows = if (abs(board[cell]) == 2) listOf(-1, 1) else listOf(-turn)
            for (dy in rows) for (dx in listOf(-1, 1)) {
                val step = if (captures) 2 else 1
                val y = cell / 8 + dy * step; val x = cell % 8 + dx * step
                if (y !in 0..7 || x !in 0..7 || board[y * 8 + x] != 0) continue
                if (captures && board[(cell / 8 + dy) * 8 + cell % 8 + dx].sign != -turn) continue
                add(Move(Op.MOVE, cell, y * 8 + x))
            }
        }
    }
    override fun legalMoves(): List<Move> {
        if (result != Result.PLAYING) return emptyList()
        val jumps = choices(true)
        return if (jumps.isNotEmpty() || forced != null) jumps else choices(false)
    }
    override fun play(move: Move): CheckersState? {
        if (move !in legalMoves()) return null
        val next = board.toMutableList(); val piece = next[move.from]
        val capture = abs(move.from / 8 - move.to / 8) == 2
        next[move.from] = 0
        if (capture) next[(move.from / 8 + move.to / 8) / 2 * 8 + (move.from % 8 + move.to % 8) / 2] = 0
        val crowned = abs(piece) == 1 && move.to / 8 == if (turn == 1) 0 else 7
        next[move.to] = if (crowned) 2 * turn else piece
        val candidate = copy(board = next, forced = move.to, quiet = if (capture || abs(piece) == 1) 0 else quiet + 1)
        if (capture && !crowned && candidate.choices(true).isNotEmpty()) return candidate
        val key = position(next, -turn)
        val following = candidate.copy(turn = -turn, forced = null, positions = (positions + key).takeLast(81))
        val result = when {
            following.legalMoves().isEmpty() -> if (turn == 1) Result.WON else Result.LOST
            following.quiet >= 80 || following.positions.count { it == key } >= 3 -> Result.DRAW
            else -> Result.PLAYING
        }
        return following.copy(result = result)
    }
    companion object {
        private fun position(board: List<Int>, turn: Int) = board.joinToString(",") + ":$turn"
        fun new(): CheckersState {
            val board = List(64) { cell -> if ((cell / 8 + cell % 8) % 2 == 0) 0 else when (cell / 8) { in 0..2 -> -1; in 5..7 -> 1; else -> 0 } }
            return CheckersState(board, positions = listOf(position(board, 1)))
        }
    }
}
