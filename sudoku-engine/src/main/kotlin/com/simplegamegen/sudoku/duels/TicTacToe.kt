package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.math.abs
import kotlin.random.Random

/**
 * Tic-tac-toe (noughts and crosses) on a square board.
 * Players place marks that never move. The first to fill a whole row, column or diagonal wins:
 * 3 on 3×3, 4 on 4×4, and 5 on 5×5. A full board with no line is a draw.
 * This is not Five in a Row: the win length matches the board, so a shorter run does not count.
 *
 * [setting] picks the board and either a computer strength or pass-and-play.
 * Against the computer, you are X (player 1) and move first. O is player -1.
 */
data class TicTacToe(
    val setting: Int,
    val seed: Long,
    val cells: List<Int> = List(sizeOf(setting) * sizeOf(setting)) { 0 },
    val turn: Int = 1,
    val last: Int? = null,
    val winner: Int = 0,
) {
    val size: Int = sizeOf(setting)
    val need: Int = size
    val passAndPlay: Boolean = passOf(setting)
    val level: Int = levelOf(setting)

    init {
        require(setting in NAMES.indices && cells.size == size * size && cells.all { it in -1..1 })
        require(turn == 1 || turn == -1)
        require(winner in -1..1)
        require(last == null || (last in cells.indices && cells[last] != 0))
        val me = cells.count { it == 1 }
        val cpu = cells.count { it == -1 }
        require(me == cpu || me == cpu + 1)
        require(owns(cells, 1, size) == (winner == 1))
        require(owns(cells, -1, size) == (winner == -1))
        if (winner == 1) require(me == cpu + 1 && last != null && cells[last] == 1)
        if (winner == -1) require(me == cpu && last != null && cells[last] == -1)
        if (winner == 0 && me + cpu < cells.size) require(if (me == cpu) turn == 1 else turn == -1)
        if (winner == 0 && last != null) require(cells[last] == if (me == cpu) -1 else 1)
        if (me + cpu == 0) require(last == null && turn == 1 && winner == 0)
    }

    val over: Boolean get() = winner != 0 || cells.none { it == 0 }
    val draw: Boolean get() = over && winner == 0

    fun legalMoves(): List<Int> = if (over) emptyList() else cells.indices.filter { cells[it] == 0 }

    /** Places the mark of the player to move. Null when the square is illegal. */
    fun place(index: Int): TicTacToe? {
        if (over || index !in cells.indices || cells[index] != 0) return null
        val next = cells.toMutableList()
        next[index] = turn
        val won = owns(next, turn, size)
        val filled = next.none { it == 0 }
        return copy(cells = next, turn = if (won || filled) turn else -turn, last = index, winner = if (won) turn else 0)
    }

    /** True when putting [player] on [index] would complete a line, ignoring whose turn it is. */
    fun wouldWin(index: Int, player: Int): Boolean {
        if (over || index !in cells.indices || cells[index] != 0) return false
        val next = cells.toMutableList()
        next[index] = player
        return owns(next, player, size)
    }

    /**
     * True when [index] leaves two different squares where [player] would win on the next turn.
     * The move itself must not already be a win.
     */
    fun opensFork(index: Int, player: Int): Boolean {
        if (over || index !in cells.indices || cells[index] != 0 || wouldWin(index, player)) return false
        val next = cells.toMutableList()
        next[index] = player
        var threats = 0
        for (i in next.indices) if (next[i] == 0) {
            next[i] = player
            val win = owns(next, player, size)
            next[i] = 0
            if (win && ++threats >= 2) return true
        }
        return false
    }

    /** Full rows, columns and diagonals that belong to the winner. */
    fun winningLines(): List<List<Int>> {
        if (winner == 0) return emptyList()
        return linesOf(size).filter { line -> line.all { cells[it] == winner } }.map { it.toList() }
    }

    fun winningCells(): Set<Int> = winningLines().flatten().toSet()

    companion object {
        val LEVELS = listOf("Easy", "Medium", "Hard", "Expert")
        val NAMES: List<String> = buildList {
            for (size in intArrayOf(3, 4, 5)) for (level in LEVELS) add("${size}×${size} $level")
            for (size in intArrayOf(3, 4, 5)) add("${size}×${size} two players")
        }

        fun start(seed: Long, setting: Int) = TicTacToe(setting, seed)

        fun sizeOf(setting: Int): Int {
            require(setting in NAMES.indices)
            val group = if (setting >= 12) setting - 12 else setting / 4
            return intArrayOf(3, 4, 5)[group]
        }

        fun levelOf(setting: Int) = if (setting >= 12) 0 else setting % 4
        fun passOf(setting: Int) = setting >= 12
    }
}

private val LINES: Map<Int, List<IntArray>> = intArrayOf(3, 4, 5).associateWith { size ->
    buildList {
        for (row in 0 until size) add(IntArray(size) { col -> row * size + col })
        for (col in 0 until size) add(IntArray(size) { row -> row * size + col })
        add(IntArray(size) { i -> i * size + i })
        add(IntArray(size) { i -> i * size + (size - 1 - i) })
    }
}

private fun linesOf(size: Int): List<IntArray> = LINES.getValue(size)

private fun owns(cells: List<Int>, player: Int, size: Int): Boolean =
    linesOf(size).any { line -> line.all { cells[it] == player } }

private fun ownsBoard(board: IntArray, player: Int, size: Int): Boolean =
    linesOf(size).any { line -> line.all { board[it] == player } }

object TicTacToeAi {
    /** Center, then corners, then edges. Ties in solved 3×3 play keep this order. */
    private val ORDER = intArrayOf(4, 0, 2, 6, 8, 1, 3, 5, 7)
    private val scores = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    /**
     * An empty square for the player about to move.
     * Easy usually takes a win and sometimes a block, then plays near the middle.
     * Medium and above always take a win or a block, and take a double threat.
     * Hard and Expert on 3×3 play perfectly. Larger boards are searched, with a short time cap.
     */
    fun choose(g: TicTacToe): Int {
        checkpoint()
        require(!g.over)
        val legal = g.legalMoves()
        require(legal.isNotEmpty())
        if (g.size == 3 && g.level >= 2) return perfect(g)
        val win = legal.firstOrNull { g.wouldWin(it, g.turn) }
        val block = legal.firstOrNull { g.wouldWin(it, -g.turn) }
        val random = Random(g.seed xor (g.cells.count { it != 0 }.toLong() shl 8) xor g.turn.toLong())
        if (g.level == 0) return easy(g, legal, win, block, random)
        if (win != null) return win
        if (block != null) return block
        val fork = legal.firstOrNull { g.opensFork(it, g.turn) }
        if (fork != null) return fork
        if (g.level >= 2) {
            val stop = legal.firstOrNull { g.opensFork(it, -g.turn) }
            if (stop != null) return stop
        }
        return search(g, legal)
    }

    private fun easy(g: TicTacToe, legal: List<Int>, win: Int?, block: Int?, random: Random): Int {
        if (win != null && random.nextInt(100) < 76) return win
        if (block != null && random.nextInt(100) < 44) return block
        if (random.nextInt(100) < 22) return legal.random(random)
        val board = g.cells.toIntArray()
        val ranked = legal.sortedByDescending { quiet(board, it, g.turn, g.size) }
        return noisy(ranked, random)
    }

    private fun noisy(cells: List<Int>, random: Random): Int {
        val weights = intArrayOf(5, 2, 1)
        val n = minOf(weights.size, cells.size)
        var total = 0
        for (i in 0 until n) total += weights[i]
        var roll = random.nextInt(total.coerceAtLeast(1))
        for (i in 0 until n) {
            roll -= weights[i]
            if (roll < 0) return cells[i]
        }
        return cells.first()
    }

    private fun perfect(g: TicTacToe): Int {
        val board = g.cells.toIntArray()
        var bestMove = -1
        var bestScore = Int.MIN_VALUE / 4
        for (move in ORDER) {
            if (board[move] != 0) continue
            board[move] = g.turn
            val raw = if (ownsBoard(board, g.turn, 3)) 100 else -exact(board, -g.turn)
            board[move] = 0
            val shaped = shape(raw)
            if (shaped > bestScore) {
                bestScore = shaped
                bestMove = move
            }
        }
        return bestMove
    }

    /** Score for the player about to move. Positive is a win, and a quicker win scores higher. */
    private fun exact(board: IntArray, turn: Int): Int {
        checkpoint()
        val key = pack(board, turn)
        scores[key]?.let { return it }
        var any = false
        var best = Int.MIN_VALUE / 4
        for (move in ORDER) {
            if (board[move] != 0) continue
            any = true
            board[move] = turn
            val raw = if (ownsBoard(board, turn, 3)) 100 else -exact(board, -turn)
            board[move] = 0
            val shaped = shape(raw)
            if (shaped > best) best = shaped
        }
        if (!any) best = 0
        scores[key] = best
        return best
    }

    private fun shape(raw: Int) = when {
        raw > 0 -> raw - 1
        raw < 0 -> raw + 1
        else -> 0
    }

    private fun pack(board: IntArray, turn: Int): Int {
        var packed = 0
        for (i in 0 until 9) {
            val mark = when (board[i]) { 1 -> 1; -1 -> 2; else -> 0 }
            packed = packed or (mark shl (i * 2))
        }
        return if (turn == -1) packed or (1 shl 18) else packed
    }

    private fun search(g: TicTacToe, legal: List<Int>): Int {
        val board = g.cells.toIntArray()
        val width = when (g.size) { 3 -> 9; 4 -> 12; else -> 8 }
        val maxDepth = when (g.level) {
            1 -> 2
            2 -> if (g.size >= 5) 3 else 4
            else -> if (g.size >= 5) 4 else 6
        }
        val millis = when (g.level) { 1 -> 40L; 2 -> 90L; else -> 160L }
        val ordered = legal.sortedByDescending { quiet(board, it, g.turn, g.size) }.take(width)
        var best = ordered.first()
        val deadline = System.nanoTime() + millis * 1_000_000L
        for (depth in 1..maxDepth) {
            var local = best
            var bestScore = Int.MIN_VALUE / 4
            var stopped = false
            var alpha = Int.MIN_VALUE / 4
            val beta = Int.MAX_VALUE / 4
            val seq = listOf(best) + ordered.filter { it != best }
            for (index in seq) {
                if (depth > 1 && System.nanoTime() > deadline) { stopped = true; break }
                board[index] = g.turn
                val score = if (ownsBoard(board, g.turn, g.size)) 10_000 - depth
                else -reply(board, -g.turn, g.size, depth - 1, -beta, -alpha, deadline, width)
                board[index] = 0
                if (System.nanoTime() > deadline && depth > 1) { stopped = true; break }
                if (score > bestScore) {
                    bestScore = score
                    local = index
                }
                if (bestScore > alpha) alpha = bestScore
            }
            if (stopped) break
            best = local
        }
        return best
    }

    private fun reply(board: IntArray, player: Int, size: Int, depth: Int, alpha0: Int, beta0: Int, deadline: Long, width: Int): Int {
        checkpoint()
        if (System.nanoTime() > deadline || depth <= 0) return evaluate(board, player, size)
        val open = board.indices.filter { board[it] == 0 }
        if (open.isEmpty()) return 0
        val ordered = open.sortedByDescending { quiet(board, it, player, size) }.take(width)
        var alpha = alpha0
        var best = Int.MIN_VALUE / 4
        for (index in ordered) {
            board[index] = player
            val score = when {
                ownsBoard(board, player, size) -> 10_000 - depth
                depth <= 1 || System.nanoTime() > deadline -> evaluate(board, player, size)
                else -> -reply(board, -player, size, depth - 1, -beta0, -alpha, deadline, width)
            }
            board[index] = 0
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta0) break
        }
        return best
    }

    private fun quiet(board: IntArray, index: Int, player: Int, size: Int): Int {
        board[index] = player
        val score = if (ownsBoard(board, player, size)) 10_000 else evaluate(board, player, size)
        board[index] = 0
        return score
    }

    private fun evaluate(board: IntArray, player: Int, size: Int): Int {
        val weights = when (size) {
            3 -> intArrayOf(0, 3, 28, 400)
            4 -> intArrayOf(0, 2, 14, 90, 800)
            else -> intArrayOf(0, 1, 8, 36, 180, 1600)
        }
        var score = 0
        for (line in linesOf(size)) {
            var mine = 0
            var opp = 0
            for (index in line) when (board[index]) {
                player -> mine++
                -player -> opp++
            }
            if (mine > 0 && opp == 0) score += weights[mine]
            else if (opp > 0 && mine == 0) score -= weights[opp]
        }
        val mid = (size - 1) / 2f
        for (index in board.indices) if (board[index] != 0) {
            val toward = (size - (abs(index / size - mid) + abs(index % size - mid))).toInt()
            score += if (board[index] == player) toward else -toward
        }
        return score
    }
}

object TicTacToeCodec {
    fun encode(g: TicTacToe) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.cells.joinToString(","),
        g.turn.toString(),
        g.last?.toString() ?: "",
        g.winner.toString(),
    ).joinToString("\n")

    fun decode(text: String): TicTacToe? = try {
        val lines = text.split('\n')
        require(lines.size == 7 && lines[0] == "1")
        TicTacToe(
            lines[1].toInt(),
            lines[2].toLong(),
            lines[3].split(',').map { it.toInt() },
            lines[4].toInt(),
            lines[5].toIntOrNull(),
            lines[6].toInt(),
        )
    } catch (_: IllegalArgumentException) { null } catch (_: IllegalStateException) { null }
}
