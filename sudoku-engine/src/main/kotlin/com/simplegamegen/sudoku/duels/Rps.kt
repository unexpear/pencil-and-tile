package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.math.abs
import kotlin.random.Random

/**
 * Rock–paper–scissors on a shared flat hex board.
 * Each side starts with the same mix of pieces on opposite edges.
 * A piece steps one hex, onto an empty cell or onto an adjacent enemy it beats.
 * Paper beats rock, rock beats scissors, scissors beats paper. Landing captures. No jumping.
 * The side that takes every enemy piece, or leaves the opponent with no legal move, wins.
 *
 * [setting] picks a 5×5 or 7×7 board and either a computer strength or pass-and-play.
 * Against the computer, you are red (positive pieces), start at the bottom, and move first.
 * Cell values: 0 empty, ±1 rock, ±2 paper, ±3 scissors. Positive is red.
 */
data class Rps(
    val setting: Int,
    val seed: Long,
    val cells: List<Int> = opening(setting),
    val turn: Int = 1,
    val lastFrom: Int? = null,
    val lastTo: Int? = null,
    val winner: Int = 0,
) {
    val size: Int = sizeOf(setting)
    val passAndPlay: Boolean = passOf(setting)
    val level: Int = levelOf(setting)

    init {
        require(setting in NAMES.indices && cells.size == size * size && cells.all { abs(it) in 0..SCISSORS })
        require(turn == 1 || turn == -1)
        require(winner in -1..1)
        val quota = quotaOf(size)
        for (side in intArrayOf(1, -1)) for (kind in KINDS) require(cells.count { it == side * kind } <= quota)
        if (lastFrom == null || lastTo == null) require(lastFrom == null && lastTo == null)
        else {
            require(lastFrom in cells.indices && lastTo in cells.indices && lastFrom != lastTo)
            require(lastTo in neighbors(size, lastFrom) && cells[lastFrom] == 0)
            val mover = if (winner != 0) winner else -turn
            require(cells[lastTo] != 0 && cells[lastTo].sign == mover)
        }
        if (winner == 0) require(options(turn).isNotEmpty())
        else require(turn == winner && options(-winner).isEmpty() && cells.any { it.sign == winner })
    }

    val over: Boolean get() = winner != 0

    fun count(side: Int, kind: Int) = cells.count { it == side * kind }

    /** How many of [kind] [side] has captured. */
    fun taken(side: Int, kind: Int) = quotaOf(size) - count(-side, kind)

    fun legalMoves(): List<RpsMove> = if (over) emptyList() else options(turn)

    /** Empty cells and enemy pieces this piece can land on. Empty when it is not that piece's turn. */
    fun destinations(from: Int): List<Int> {
        if (over || from !in cells.indices || cells[from].sign != turn) return emptyList()
        return landings(cells, from)
    }

    fun play(move: RpsMove): Rps? = play(move.from, move.to)

    /** Moves the piece on [from] onto [to]. Null when the step is illegal. */
    fun play(from: Int, to: Int): Rps? {
        if (over || RpsMove(from, to) !in options(turn)) return null
        val next = cells.toMutableList()
        next[to] = next[from]
        next[from] = 0
        val stuck = options(-turn, next).isEmpty()
        return copy(
            cells = next,
            turn = if (stuck) turn else -turn,
            lastFrom = from,
            lastTo = to,
            winner = if (stuck) turn else 0,
        )
    }

    /** Legal steps for [player] on [board], including when the game is already over. */
    fun options(player: Int, board: List<Int> = cells): List<RpsMove> {
        val out = ArrayList<RpsMove>()
        for (from in board.indices) {
            if (board[from].sign != player) continue
            for (to in landings(board, from)) out += RpsMove(from, to)
        }
        return out
    }

    private fun landings(board: List<Int>, from: Int): List<Int> {
        val piece = board[from]
        if (piece == 0) return emptyList()
        return neighbors(size, from).filter { to ->
            val occ = board[to]
            occ == 0 || (occ.sign != piece.sign && beats(piece, occ))
        }
    }

    companion object {
        const val ROCK = 1
        const val PAPER = 2
        const val SCISSORS = 3
        val KINDS = intArrayOf(ROCK, PAPER, SCISSORS)
        val LEVELS = listOf("Easy", "Medium", "Hard", "Expert")
        val NAMES: List<String> = buildList {
            for (size in intArrayOf(5, 7)) for (level in LEVELS) add("${size}×${size} $level")
            for (size in intArrayOf(5, 7)) add("${size}×${size} two players")
        }

        /** Same six neighbors as Hex: N, NE, W, E, SW, SE. A square diagonal does not touch. */
        val DELTAS = arrayOf(-1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0)

        fun start(seed: Long, setting: Int) = Rps(setting, seed)

        fun sizeOf(setting: Int): Int {
            require(setting in NAMES.indices)
            val group = if (setting >= 8) setting - 8 else setting / 4
            return intArrayOf(5, 7)[group]
        }

        fun levelOf(setting: Int) = if (setting >= 8) 0 else setting % 4
        fun passOf(setting: Int) = setting >= 8
        fun quotaOf(size: Int) = if (size <= 5) 1 else 2

        /** True when [attacker] beats [defender]. Sides are ignored; kinds are the absolute values. */
        fun beats(attacker: Int, defender: Int): Boolean = when (abs(attacker)) {
            ROCK -> abs(defender) == SCISSORS
            PAPER -> abs(defender) == ROCK
            SCISSORS -> abs(defender) == PAPER
            else -> false
        }

        fun sideOf(cell: Int) = when {
            cell > 0 -> 1
            cell < 0 -> -1
            else -> 0
        }

        fun kindName(cell: Int): String = when (abs(cell)) {
            ROCK -> "rock"
            PAPER -> "paper"
            SCISSORS -> "scissors"
            else -> ""
        }

        fun neighbors(size: Int, index: Int): IntArray = graph(size)[index]

        fun opening(setting: Int): List<Int> {
            val n = sizeOf(setting)
            val cells = MutableList(n * n) { 0 }
            fun put(row: Int, col: Int, side: Int, kind: Int) { cells[row * n + col] = side * kind }
            if (n <= 5) {
                for (i in 0..2) {
                    put(0, 1 + i, -1, KINDS[i])
                    put(n - 1, 1 + i, 1, KINDS[i])
                }
            } else {
                val back = intArrayOf(ROCK, PAPER, SCISSORS)
                val front = intArrayOf(SCISSORS, ROCK, PAPER)
                for (i in 0..2) {
                    put(0, 2 + i, -1, back[i])
                    put(1, 2 + i, -1, front[i])
                    put(n - 2, 2 + i, 1, front[i])
                    put(n - 1, 2 + i, 1, back[i])
                }
            }
            return cells
        }

        private fun built(size: Int): Array<IntArray> = Array(size * size) { index ->
            val row = index / size
            val col = index % size
            DELTAS.mapNotNull { (dr, dc) ->
                val rr = row + dr
                val cc = col + dc
                if (rr in 0 until size && cc in 0 until size) rr * size + cc else null
            }.toIntArray()
        }

        private val graphs = mapOf(5 to built(5), 7 to built(7))
        private fun graph(size: Int): Array<IntArray> = graphs.getValue(size)
    }
}

data class RpsMove(val from: Int, val to: Int)

object RpsAi {
    /**
     * A legal move for the player about to move.
     * Easy wanders and often grabs a capture. Medium takes a safe capture or a step that is not next to a beater.
     * Hard and Expert look ahead, with a short time cap so a phone stays responsive.
     */
    fun choose(g: Rps): RpsMove {
        checkpoint()
        require(!g.over)
        val legal = g.legalMoves()
        require(legal.isNotEmpty())
        if (legal.size == 1) return legal.first()
        val wins = legal.filter { wins(g, it) }
        val random = Random(g.seed xor (g.cells.count { it != 0 }.toLong() shl 1) xor ((g.lastTo ?: -1).toLong() shl 8) xor g.turn.toLong())
        if (g.level == 0) {
            if (wins.isNotEmpty() && random.nextInt(100) < 55) return wins.first()
            val captures = legal.filter { g.cells[it.to] != 0 }
            if (captures.isNotEmpty() && random.nextInt(100) < 60) return captures.random(random)
            return legal.random(random)
        }
        if (wins.isNotEmpty()) return wins.first()
        if (g.level == 1) return quiet(g, legal)
        return Search(g).best(legal)
    }

    private fun wins(g: Rps, move: RpsMove): Boolean {
        val next = g.cells.toMutableList()
        next[move.to] = next[move.from]
        next[move.from] = 0
        return g.options(-g.turn, next).isEmpty()
    }

    private fun quiet(g: Rps, legal: List<RpsMove>): RpsMove {
        val safeCaps = legal.filter { g.cells[it.to] != 0 && !hangs(g, it) }
        if (safeCaps.isNotEmpty()) return bestOf(g, safeCaps)
        val safe = legal.filter { !hangs(g, it) }
        return bestOf(g, if (safe.isNotEmpty()) safe else legal)
    }

    private fun bestOf(g: Rps, moves: List<RpsMove>): RpsMove =
        moves.maxWith(compareBy<RpsMove> { score(g, it) }.thenByDescending { it.to }.thenByDescending { it.from })

    private fun score(g: Rps, move: RpsMove): Int {
        val fromRow = move.from / g.size
        val toRow = move.to / g.size
        val forward = if (g.turn == 1) fromRow - toRow else toRow - fromRow
        return forward + if (g.cells[move.to] != 0) 5 else 0
    }

    /** True when the landing hex is next to an enemy that beats the piece that just arrived. */
    fun hangs(g: Rps, move: RpsMove): Boolean {
        val piece = g.cells[move.from]
        return Rps.neighbors(g.size, move.to).any { n ->
            if (n == move.from) return@any false
            val occ = g.cells[n]
            occ != 0 && occ.sign != piece.sign && Rps.beats(occ, piece)
        }
    }

    private class Search(val g: Rps) {
        val n = g.size
        val board = g.cells.toIntArray()
        var deadline = Long.MAX_VALUE
        var nodes = 0
        var width = 8

        fun best(legal: List<RpsMove>): RpsMove {
            deadline = System.nanoTime() + if (g.level >= 3) 90_000_000L else 45_000_000L
            width = if (g.level >= 3) 8 else 7
            val depth = if (g.level >= 3) 3 else 2
            val moves = ordered(g.turn).let { ranked ->
                val chosen = ranked.filter { it in legal }
                if (chosen.isEmpty()) legal else chosen
            }
            var bestMove = moves.first()
            var best = Int.MIN_VALUE / 4
            var alpha = Int.MIN_VALUE / 4
            val beta = Int.MAX_VALUE / 4
            for (move in moves.take(width)) {
                val captured = apply(move)
                val scored = if (gen(-g.turn).isEmpty()) 90_000 + depth else -value(-g.turn, depth - 1, -beta, -alpha)
                undo(move, captured)
                if (scored > best) {
                    best = scored
                    bestMove = move
                }
                if (best > alpha) alpha = best
            }
            return bestMove
        }

        private fun value(player: Int, depth: Int, alpha: Int, beta: Int): Int {
            checkpoint()
            nodes++
            if (nodes > 4_000 || System.nanoTime() > deadline || depth == 0) return eval(player)
            val moves = ordered(player)
            if (moves.isEmpty()) return -90_000 - depth
            var best = Int.MIN_VALUE / 4
            var a = alpha
            for (move in moves.take(width)) {
                val captured = apply(move)
                val scored = if (gen(-player).isEmpty()) 90_000 + depth else -value(-player, depth - 1, -beta, -a)
                undo(move, captured)
                if (scored > best) best = scored
                if (best > a) a = best
                if (a >= beta) break
            }
            return best
        }

        private fun eval(player: Int): Int {
            var score = gen(player).size * 3 - gen(-player).size * 3
            for (i in board.indices) {
                val cell = board[i]
                if (cell == 0) continue
                val sign = if (cell.sign == player) 1 else -1
                score += sign * 100
                val row = i / n
                val forward = if (cell > 0) n - 1 - row else row
                score += sign * forward * 2
                if (threatened(i, cell)) score -= sign * 25
            }
            return score
        }

        private fun threatened(index: Int, piece: Int): Boolean =
            Rps.neighbors(n, index).any { n -> board[n] != 0 && board[n].sign != piece.sign && Rps.beats(board[n], piece) }

        private fun gen(player: Int): List<RpsMove> {
            val out = ArrayList<RpsMove>()
            for (from in board.indices) {
                val piece = board[from]
                if (piece == 0 || piece.sign != player) continue
                for (to in Rps.neighbors(n, from)) {
                    val occ = board[to]
                    if (occ == 0 || (occ.sign != piece.sign && Rps.beats(piece, occ))) out += RpsMove(from, to)
                }
            }
            return out
        }

        private fun ordered(player: Int): List<RpsMove> = gen(player).sortedWith(
            compareByDescending<RpsMove> { board[it.to] != 0 }
                .thenBy { hangs(it) }
                .thenBy { it.from }
                .thenBy { it.to },
        )

        private fun hangs(move: RpsMove): Boolean {
            val piece = board[move.from]
            return Rps.neighbors(n, move.to).any { at ->
                if (at == move.from) return@any false
                val occ = board[at]
                occ != 0 && occ.sign != piece.sign && Rps.beats(occ, piece)
            }
        }

        private fun apply(move: RpsMove): Int {
            val captured = board[move.to]
            board[move.to] = board[move.from]
            board[move.from] = 0
            return captured
        }

        private fun undo(move: RpsMove, captured: Int) {
            board[move.from] = board[move.to]
            board[move.to] = captured
        }
    }
}

private val Int.sign: Int get() = Rps.sideOf(this)

object RpsCodec {
    fun encode(g: Rps) = listOf(
        "1", g.setting.toString(), g.seed.toString(), g.cells.joinToString(","),
        g.turn.toString(), g.lastFrom?.toString() ?: "", g.lastTo?.toString() ?: "", g.winner.toString(),
    ).joinToString("\n")

    fun decode(text: String): Rps? = try {
        val lines = text.split('\n')
        require(lines.size == 8 && lines[0] == "1")
        val from = lines[5].takeIf { it.isNotEmpty() }?.toInt()
        val to = lines[6].takeIf { it.isNotEmpty() }?.toInt()
        require((from == null) == (to == null))
        Rps(lines[1].toInt(), lines[2].toLong(), lines[3].split(',').map(String::toInt), lines[4].toInt(), from, to, lines[7].toInt())
    } catch (_: IllegalArgumentException) { null } catch (_: IllegalStateException) { null }
}
