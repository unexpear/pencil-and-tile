package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Connect Four on a standard 7×6 board. Discs fall to the lowest empty cell in a column.
 * Four in a row — across, down or diagonal — wins. A full board with no line of four is a draw.
 * [setting] is the computer's strength. Player 1 is you and moves first; -1 is the computer.
 */
data class ConnectFour(
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
        for (col in 0 until COLS) {
            var holeBelow = false
            for (row in ROWS - 1 downTo 0) {
                val filled = cells[row * COLS + col] != 0
                require(!filled || !holeBelow) { "floating disc" }
                if (!filled) holeBelow = true
            }
        }
        val me = cells.count { it == 1 }
        val cpu = cells.count { it == -1 }
        require(me == cpu || me == cpu + 1)
        require(anyLine(1) == (winner == 1) && anyLine(-1) == (winner == -1))
        if (winner == 1) require(me == cpu + 1)
        if (winner == -1) require(me == cpu)
        if (winner == 0 && me + cpu < CELLS) require(if (me == cpu) turn == 1 else turn == -1)
    }

    val over: Boolean get() = winner != 0 || cells.none { it == 0 }
    val draw: Boolean get() = over && winner == 0

    fun canDrop(col: Int) = !over && col in 0 until COLS && cells[col] == 0
    fun legalColumns(): List<Int> = (0 until COLS).filter { canDrop(it) }

    /** Lowest empty row in [col], or null when the column is full or the game is over. */
    fun landingRow(col: Int): Int? {
        if (!canDrop(col)) return null
        for (row in ROWS - 1 downTo 0) if (cells[row * COLS + col] == 0) return row
        return null
    }

    /** Drops the current player's disc into [col]. Null when the column is illegal. */
    fun drop(col: Int): ConnectFour? {
        val row = landingRow(col) ?: return null
        val index = row * COLS + col
        val next = cells.toMutableList().also { it[index] = turn }
        val won = lineAt(index, turn) { next[it] }
        val filled = next.none { it == 0 }
        return copy(cells = next, turn = if (won || filled) turn else -turn, last = index, winner = if (won) turn else 0)
    }

    /** True when dropping [player]'s disc into [col] would complete a line, ignoring whose turn it is. */
    fun wouldWin(col: Int, player: Int): Boolean {
        if (over || col !in 0 until COLS) return false
        val row = (ROWS - 1 downTo 0).firstOrNull { cells[it * COLS + col] == 0 } ?: return false
        val index = row * COLS + col
        return lineAt(index, player) { if (it == index) player else cells[it] }
    }

    /** Cells that belong to a winning line, for the highlight. */
    fun winningCells(): Set<Int> {
        if (winner == 0) return emptySet()
        val found = mutableSetOf<Int>()
        for (i in cells.indices) if (cells[i] == winner) {
            val row = i / COLS
            val col = i % COLS
            for ((dr, dc) in DIRS) {
                val line = mutableListOf(i)
                var rr = row + dr
                var cc = col + dc
                while (rr in 0 until ROWS && cc in 0 until COLS && cells[rr * COLS + cc] == winner) {
                    line += rr * COLS + cc
                    rr += dr; cc += dc
                }
                rr = row - dr; cc = col - dc
                while (rr in 0 until ROWS && cc in 0 until COLS && cells[rr * COLS + cc] == winner) {
                    line += rr * COLS + cc
                    rr -= dr; cc -= dc
                }
                if (line.size >= 4) found += line
            }
        }
        return found
    }

    private fun anyLine(player: Int) = cells.indices.any { cells[it] == player && lineAt(it, player) { cells[it] } }

    companion object {
        const val COLS = 7
        const val ROWS = 6
        const val CELLS = COLS * ROWS
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")
        val DIRS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        fun start(seed: Long, setting: Int) = ConnectFour(setting, seed)
    }
}

private fun lineAt(index: Int, player: Int, cell: (Int) -> Int): Boolean {
    val row = index / ConnectFour.COLS
    val col = index % ConnectFour.COLS
    for ((dr, dc) in ConnectFour.DIRS) {
        var count = 1
        var rr = row + dr
        var cc = col + dc
        while (rr in 0 until ConnectFour.ROWS && cc in 0 until ConnectFour.COLS && cell(rr * ConnectFour.COLS + cc) == player) {
            count++; rr += dr; cc += dc
        }
        rr = row - dr; cc = col - dc
        while (rr in 0 until ConnectFour.ROWS && cc in 0 until ConnectFour.COLS && cell(rr * ConnectFour.COLS + cc) == player) {
            count++; rr -= dr; cc -= dc
        }
        if (count >= 4) return true
    }
    return false
}

object ConnectFourAi {
    private val ORDER = intArrayOf(3, 2, 4, 1, 5, 0, 6)
    private val WINDOWS: List<IntArray> = buildList {
        for (r in 0 until ConnectFour.ROWS) for (c in 0..ConnectFour.COLS - 4) add(IntArray(4) { r * ConnectFour.COLS + c + it })
        for (c in 0 until ConnectFour.COLS) for (r in 0..ConnectFour.ROWS - 4) add(IntArray(4) { (r + it) * ConnectFour.COLS + c })
        for (r in 0..ConnectFour.ROWS - 4) for (c in 0..ConnectFour.COLS - 4) add(IntArray(4) { (r + it) * ConnectFour.COLS + c + it })
        for (r in 0..ConnectFour.ROWS - 4) for (c in 3 until ConnectFour.COLS) add(IntArray(4) { (r + it) * ConnectFour.COLS + (c - it) })
    }

    /** Chooses a legal column for the player about to move. Stronger settings look further ahead. */
    fun choose(g: ConnectFour): Int {
        checkpoint()
        val legal = g.legalColumns()
        require(legal.isNotEmpty())
        val win = legal.firstOrNull { g.wouldWin(it, g.turn) }
        val block = legal.firstOrNull { g.wouldWin(it, -g.turn) }
        val random = Random(g.seed * 7919 + g.cells.count { it != 0 } * 31 + g.turn)
        return when (g.setting) {
            0 -> when {
                win != null && random.nextInt(100) < 55 -> win
                block != null && random.nextInt(100) < 30 -> block
                else -> legal.random(random)
            }
            1 -> win ?: block ?: weighted(legal, random)
            else -> win ?: Search(g).best(if (g.setting == 2) 4 else 6)
        }
    }

    /** Prefers the middle columns when several drops are equally quiet. */
    private fun weighted(cols: List<Int>, random: Random): Int {
        val weights = cols.map { 1 + (3 - kotlin.math.abs(it - 3)).coerceAtLeast(0) }
        var pick = random.nextInt(weights.sum())
        for (i in cols.indices) {
            pick -= weights[i]
            if (pick < 0) return cols[i]
        }
        return cols.last()
    }

    private class Search(g: ConnectFour) {
        val board = g.cells.toIntArray()
        val height = IntArray(ConnectFour.COLS) { col -> (0 until ConnectFour.ROWS).count { board[it * ConnectFour.COLS + col] != 0 } }
        val me = g.turn

        fun play(col: Int, player: Int): Int {
            val row = ConnectFour.ROWS - 1 - height[col]
            val index = row * ConnectFour.COLS + col
            board[index] = player
            height[col]++
            return index
        }

        fun undo(col: Int) {
            height[col]--
            board[(ConnectFour.ROWS - 1 - height[col]) * ConnectFour.COLS + col] = 0
        }

        fun best(depth: Int): Int {
            var bestCol = ORDER.first { height[it] < ConnectFour.ROWS }
            var bestVal = Int.MIN_VALUE / 4
            var alpha = Int.MIN_VALUE / 4
            val beta = Int.MAX_VALUE / 4
            for (col in ORDER) {
                if (height[col] >= ConnectFour.ROWS) continue
                val index = play(col, me)
                val value = if (lineAt(index, me) { board[it] }) 100_000 + depth else -value(-me, depth - 1, -beta, -alpha, index)
                undo(col)
                if (value > bestVal) { bestVal = value; bestCol = col }
                if (bestVal > alpha) alpha = bestVal
            }
            return bestCol
        }

        fun value(turn: Int, depth: Int, alpha: Int, beta: Int, lastIndex: Int): Int {
            checkpoint()
            if (lastIndex >= 0 && board[lastIndex] == -turn && lineAt(lastIndex, -turn) { board[it] }) return -(100_000 + depth + 1)
            if ((0 until ConnectFour.COLS).all { height[it] >= ConnectFour.ROWS }) return 0
            if (depth == 0) return heuristic(turn)
            var best = Int.MIN_VALUE / 4
            var a = alpha
            for (col in ORDER) {
                if (height[col] >= ConnectFour.ROWS) continue
                val index = play(col, turn)
                val scored = if (lineAt(index, turn) { board[it] }) 100_000 + depth else -value(-turn, depth - 1, -beta, -a, index)
                undo(col)
                if (scored > best) best = scored
                if (best > a) a = best
                if (a >= beta) break
            }
            return best
        }

        fun heuristic(player: Int): Int {
            var score = 0
            for (row in 0 until ConnectFour.ROWS) {
                val centre = board[row * ConnectFour.COLS + 3]
                if (centre == player) score += 4 else if (centre == -player) score -= 4
            }
            for (window in WINDOWS) {
                var mine = 0
                var opp = 0
                for (i in window) when (board[i]) { player -> mine++; -player -> opp++ }
                if (mine > 0 && opp > 0) continue
                val n = if (mine > 0) mine else -opp
                val weight = when (kotlin.math.abs(n)) { 1 -> 1; 2 -> 6; 3 -> 30; else -> 0 }
                score += if (n > 0) weight else -weight
            }
            return score
        }
    }
}

object ConnectFourCodec {
    fun encode(g: ConnectFour) = listOf(
        "1", g.setting.toString(), g.seed.toString(), g.cells.joinToString(","),
        g.turn.toString(), g.last?.toString() ?: "", g.winner.toString(),
    ).joinToString("\n")

    fun decode(text: String): ConnectFour? = try {
        val lines = text.split('\n')
        require(lines.size == 7 && lines[0] == "1")
        ConnectFour(
            lines[1].toInt(), lines[2].toLong(), lines[3].split(',').map(String::toInt),
            lines[4].toInt(), lines[5].toIntOrNull(), lines[6].toInt(),
        )
    } catch (_: IllegalArgumentException) { null }
}
