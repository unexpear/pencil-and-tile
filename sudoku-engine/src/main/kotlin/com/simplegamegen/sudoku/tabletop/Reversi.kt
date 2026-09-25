package com.simplegamegen.sudoku.tabletop

data class ReversiState(val board: List<Int>, override val turn: Int = 1, override val result: Result = Result.PLAYING) : TableState {
    fun flips(cell: Int, player: Int = turn): List<Int> = buildList {
        if (cell !in board.indices || board[cell] != 0) return@buildList
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val line = mutableListOf<Int>(); var x = cell % 8 + dx; var y = cell / 8 + dy
            while (x in 0..7 && y in 0..7 && board[y * 8 + x] == -player) { line += y * 8 + x; x += dx; y += dy }
            if (line.isNotEmpty() && x in 0..7 && y in 0..7 && board[y * 8 + x] == player) addAll(line)
        }
    }
    override fun legalMoves(): List<Move> = if (result != Result.PLAYING) emptyList() else board.indices.filter { flips(it).isNotEmpty() }.map { Move(Op.MOVE, it) }
    override fun play(move: Move): ReversiState? {
        if (move !in legalMoves()) return null
        val next = board.toMutableList(); (flips(move.from) + move.from).forEach { next[it] = turn }
        val opponent = copy(board = next, turn = -turn)
        if (opponent.legalMoves().isNotEmpty()) return opponent
        val again = opponent.copy(turn = turn)
        if (again.legalMoves().isNotEmpty()) return again
        return again.copy(result = when { next.sum() > 0 -> Result.WON; next.sum() < 0 -> Result.LOST; else -> Result.DRAW })
    }
    companion object {
        fun new() = ReversiState(List(64) { when (it) { 27, 36 -> -1; 28, 35 -> 1; else -> 0 } })
    }
}
