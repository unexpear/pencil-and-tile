package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Five in a row on an 11×11 board. Stones do not move. Five or more in a line,
 * across, down or diagonal, wins. A full board with no line of five is a draw.
 * [setting] is the computer's strength. You place the dark stones and move first.
 */
data class FiveRow(
    val setting: Int,
    val seed: Long,
    val cells: List<Int> = List(CELLS) { 0 },
    val turn: Int = 1,
    val last: Int? = null,
    val winner: Int = 0,
) {
    init {
        require(setting in NAMES.indices && cells.size == CELLS && cells.all { it in -1..1 })
        require(turn == 1 || turn == -1)
        require(winner in -1..1)
        require(last == null || (last in cells.indices && cells[last] != 0))
        val me = cells.count { it == 1 }
        val cpu = cells.count { it == -1 }
        require(me == cpu || me == cpu + 1)
        require(hasLine(1) == (winner == 1) && hasLine(-1) == (winner == -1))
        if (winner == 1) require(me == cpu + 1)
        if (winner == -1) require(me == cpu)
        if (winner == 0 && me + cpu < CELLS) require(if (me == cpu) turn == 1 else turn == -1)
    }

    val over: Boolean get() = winner != 0 || cells.none { it == 0 }
    val draw: Boolean get() = over && winner == 0

    fun place(index: Int): FiveRow? {
        if (over || index !in cells.indices || cells[index] != 0) return null
        val next = cells.toMutableList()
        next[index] = turn
        val won = lined(index, turn) { next[it] }
        val filled = next.none { it == 0 }
        return copy(cells = next, turn = if (won || filled) turn else -turn, last = index, winner = if (won) turn else 0)
    }

    fun wouldWin(index: Int, player: Int): Boolean {
        if (over || index !in cells.indices || cells[index] != 0) return false
        return lined(index, player) { if (it == index) player else cells[it] }
    }

    fun winningCells(): Set<Int> {
        if (winner == 0) return emptySet()
        val found = mutableSetOf<Int>()
        for (index in cells.indices) if (cells[index] == winner) {
            val row = index / SIZE
            val col = index % SIZE
            for ((dr, dc) in DIRS) {
                val line = mutableListOf(index)
                var r = row + dr
                var c = col + dc
                while (r in 0 until SIZE && c in 0 until SIZE && cells[r * SIZE + c] == winner) {
                    line += r * SIZE + c
                    r += dr; c += dc
                }
                r = row - dr; c = col - dc
                while (r in 0 until SIZE && c in 0 until SIZE && cells[r * SIZE + c] == winner) {
                    line += r * SIZE + c
                    r -= dr; c -= dc
                }
                if (line.size >= WIN) found += line
            }
        }
        return found
    }

    private fun hasLine(player: Int) = cells.indices.any { cells[it] == player && lined(it, player) { cells[it] } }

    companion object {
        const val SIZE = 11
        const val CELLS = SIZE * SIZE
        const val WIN = 5
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")
        val DIRS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        fun start(seed: Long, setting: Int) = FiveRow(setting, seed)
    }
}

private fun lined(index: Int, player: Int, cell: (Int) -> Int): Boolean {
    val row = index / FiveRow.SIZE
    val col = index % FiveRow.SIZE
    for ((dr, dc) in FiveRow.DIRS) {
        var count = 1
        var r = row + dr
        var c = col + dc
        while (r in 0 until FiveRow.SIZE && c in 0 until FiveRow.SIZE && cell(r * FiveRow.SIZE + c) == player) {
            count++; r += dr; c += dc
        }
        r = row - dr; c = col - dc
        while (r in 0 until FiveRow.SIZE && c in 0 until FiveRow.SIZE && cell(r * FiveRow.SIZE + c) == player) {
            count++; r -= dr; c -= dc
        }
        if (count >= FiveRow.WIN) return true
    }
    return false
}

object FiveRowAi {
    private val WEIGHT = intArrayOf(0, 1, 12, 80, 500)

    /** An empty square for the player about to move. */
    fun choose(g: FiveRow): Int {
        checkpoint()
        val win = (0 until FiveRow.CELLS).firstOrNull { g.wouldWin(it, g.turn) }
        val block = (0 until FiveRow.CELLS).firstOrNull { g.wouldWin(it, -g.turn) }
        val random = Random(g.seed xor (g.cells.count { it != 0 }.toLong() shl 8) xor g.turn.toLong())
        val near = candidates(g)
        require(near.isNotEmpty())
        return when (g.setting) {
            0 -> when {
                win != null && random.nextInt(100) < 45 -> win
                block != null && random.nextInt(100) < 25 -> block
                else -> near.random(random)
            }
            1 -> win ?: block ?: bestByEval(g, near, random)
            else -> win ?: block ?: search(g, near, if (g.setting == 2) 2 else 3)
        }
    }

    private fun candidates(g: FiveRow): List<Int> {
        val stones = g.cells.indices.filter { g.cells[it] != 0 }
        if (stones.isEmpty()) return listOf(FiveRow.CELLS / 2)
        val near = mutableSetOf<Int>()
        for (stone in stones) {
            val row = stone / FiveRow.SIZE
            val col = stone % FiveRow.SIZE
            for (dr in -2..2) for (dc in -2..2) {
                val r = row + dr
                val c = col + dc
                if (r in 0 until FiveRow.SIZE && c in 0 until FiveRow.SIZE && g.cells[r * FiveRow.SIZE + c] == 0) {
                    near += r * FiveRow.SIZE + c
                }
            }
        }
        return near.toList()
    }

    private fun bestByEval(g: FiveRow, near: List<Int>, random: Random): Int {
        val board = g.cells.toIntArray()
        val scored = near.map { it to moveValue(board, it, g.turn) }
        val best = scored.maxOf { it.second }
        return scored.filter { it.second == best }.map { it.first }.random(random)
    }

    private fun search(g: FiveRow, near: List<Int>, depth: Int): Int {
        val board = g.cells.toIntArray()
        val ordered = near.sortedByDescending { moveValue(board, it, g.turn) }.take(10)
        var best = ordered.first()
        var bestScore = Int.MIN_VALUE / 4
        for (index in ordered) {
            board[index] = g.turn
            val score = if (lined(index, g.turn) { board[it] }) 10000
            else -reply(board, -g.turn, depth - 1, bestScore, 10000)
            board[index] = 0
            if (score > bestScore) {
                bestScore = score
                best = index
            }
        }
        return best
    }

    private fun reply(board: IntArray, player: Int, depth: Int, alpha0: Int, beta0: Int): Int {
        checkpoint()
        val open = candidatesOn(board)
        if (open.isEmpty()) return 0
        val ordered = open.sortedByDescending { moveValue(board, it, player) }.take(8)
        var alpha = alpha0
        var beta = beta0
        var best = Int.MIN_VALUE / 4
        for (index in ordered) {
            board[index] = player
            val score = when {
                lined(index, player) { board[it] } -> 10000 - (3 - depth)
                depth <= 1 -> evaluate(board, player)
                else -> -reply(board, -player, depth - 1, -beta, -alpha)
            }
            board[index] = 0
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta) break
        }
        return best
    }

    private fun candidatesOn(board: IntArray): List<Int> {
        val near = mutableSetOf<Int>()
        for (stone in board.indices) if (board[stone] != 0) {
            val row = stone / FiveRow.SIZE
            val col = stone % FiveRow.SIZE
            for (dr in -1..1) for (dc in -1..1) {
                val r = row + dr
                val c = col + dc
                if (r in 0 until FiveRow.SIZE && c in 0 until FiveRow.SIZE && board[r * FiveRow.SIZE + c] == 0) {
                    near += r * FiveRow.SIZE + c
                }
            }
        }
        return near.toList()
    }

    private fun moveValue(board: IntArray, index: Int, player: Int): Int {
        board[index] = player
        val score = if (lined(index, player) { board[it] }) 10000 else evaluate(board, player)
        board[index] = 0
        return score
    }

    private fun evaluate(board: IntArray, player: Int): Int {
        var score = 0
        fun window(cells: List<Int>) {
            var mine = 0
            var opp = 0
            for (index in cells) when (board[index]) {
                player -> mine++
                -player -> opp++
            }
            if (mine > 0 && opp == 0) score += WEIGHT[mine]
            else if (opp > 0 && mine == 0) score -= WEIGHT[opp]
        }
        val size = FiveRow.SIZE
        for (row in 0 until size) for (col in 0..size - FiveRow.WIN) {
            window(List(FiveRow.WIN) { row * size + col + it })
            window(List(FiveRow.WIN) { (col + it) * size + row })
        }
        for (row in 0..size - FiveRow.WIN) for (col in 0..size - FiveRow.WIN) {
            window(List(FiveRow.WIN) { (row + it) * size + col + it })
        }
        for (row in 0..size - FiveRow.WIN) for (col in FiveRow.WIN - 1 until size) {
            window(List(FiveRow.WIN) { (row + it) * size + col - it })
        }
        return score
    }
}

object FiveRowCodec {
    fun encode(g: FiveRow) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.cells.joinToString(","),
        g.turn.toString(),
        g.last?.toString() ?: "",
        g.winner.toString(),
    ).joinToString("\n")

    fun decode(text: String): FiveRow? = try {
        val lines = text.split('\n')
        require(lines.size == 7 && lines[0] == "1")
        FiveRow(
            lines[1].toInt(),
            lines[2].toLong(),
            lines[3].split(',').map { it.toInt() },
            lines[4].toInt(),
            lines[5].toIntOrNull(),
            lines[6].toInt(),
        )
    } catch (_: IllegalArgumentException) { null }
}
