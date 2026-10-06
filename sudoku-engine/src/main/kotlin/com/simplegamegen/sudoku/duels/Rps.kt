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
     * Easy prefers a safe capture and usually steps clear of a type that beats it, with some noise, and sometimes misses a one-move win.
     * Medium and above always take an immediate win. Medium looks one reply ahead.
     * Hard searches wider and deeper. Expert searches further, orders moves more carefully, and follows captures past the horizon.
     * Expert stops by about 180ms so a phone stays responsive.
     */
    fun choose(g: Rps): RpsMove {
        checkpoint()
        require(!g.over)
        val legal = g.legalMoves()
        require(legal.isNotEmpty())
        if (legal.size == 1) return legal.first()
        val wins = legal.filter { wins(g, it) }
        if (g.level == 0) return easy(g, legal, wins)
        if (wins.isNotEmpty()) return wins.first()
        val move = Search(g).best(legal)
        return if (move in legal) move else legal.first()
    }

    private fun easy(g: Rps, legal: List<RpsMove>, wins: List<RpsMove>): RpsMove {
        val random = Random(mix(g))
        if (wins.isNotEmpty() && random.nextInt(100) < 85) return wins.first()
        val ranked = legal.sortedWith(
            compareByDescending<RpsMove> { easyScore(g, it) }.thenBy { it.from }.thenBy { it.to },
        )
        val safeCaps = ranked.filter { g.cells[it.to] != 0 && !hangs(g, it) }
        if (safeCaps.isNotEmpty() && random.nextInt(100) < 78) return noisy(safeCaps, random)
        val calm = ranked.filter { !hangs(g, it) }
        val pool = if (calm.isNotEmpty() && random.nextInt(100) < 82) calm else ranked
        return noisy(pool, random)
    }

    private fun easyScore(g: Rps, move: RpsMove): Int {
        val fromRow = move.from / g.size
        val toRow = move.to / g.size
        val forward = if (g.turn == 1) fromRow - toRow else toRow - fromRow
        var score = forward * 2
        if (g.cells[move.to] != 0) score += 12
        if (hangs(g, move)) score -= 14
        return score
    }

    /** Prefers the front of an already ranked list, but not always the first move. */
    private fun noisy(moves: List<RpsMove>, random: Random): RpsMove {
        val weights = intArrayOf(5, 2, 1)
        val n = minOf(weights.size, moves.size)
        var total = 0
        for (i in 0 until n) total += weights[i]
        var roll = random.nextInt(total)
        for (i in 0 until n) {
            roll -= weights[i]
            if (roll < 0) return moves[i]
        }
        return moves.first()
    }

    private fun mix(g: Rps): Long =
        g.seed xor (g.cells.count { it != 0 }.toLong() shl 1) xor ((g.lastTo ?: -1).toLong() shl 8) xor g.turn.toLong()

    private fun wins(g: Rps, move: RpsMove): Boolean {
        val next = g.cells.toMutableList()
        next[move.to] = next[move.from]
        next[move.from] = 0
        return g.options(-g.turn, next).isEmpty()
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
        val neigh = Array(board.size) { Rps.neighbors(n, it) }
        val level = g.level
        val maxDepth = when (level) { 1 -> 2; 2 -> 4; else -> 6 }
        val width = when (level) { 1 -> 14; 2 -> 16; else -> 22 }
        val nodeCap = when (level) { 1 -> 6_000; 2 -> 30_000; else -> 80_000 }
        val qLimit = when (level) { 3 -> 2; 2 -> 1; else -> 0 }
        var deadline = Long.MAX_VALUE
        var nodes = 0
        var aborted = false
        val killers = IntArray(16) { -1 }
        val moves = IntArray(STRIDE * 12)
        val scores = IntArray(STRIDE * 12)

        fun best(legal: List<RpsMove>): RpsMove {
            deadline = System.nanoTime() + when (level) { 1 -> 50_000_000L; 2 -> 110_000_000L; else -> 180_000_000L }
            val packed = IntArray(legal.size) { pack(legal[it].from, legal[it].to) }
            val rootScore = IntArray(packed.size)
            for (i in packed.indices) rootScore[i] = rank(g.turn, packed[i], -1)
            sort(packed, rootScore, 0, packed.size)
            var bestMove = packed[0]
            var depth = 1
            while (depth <= maxDepth) {
                aborted = false
                var alpha = Int.MIN_VALUE / 4
                val beta = Int.MAX_VALUE / 4
                var bestScore = Int.MIN_VALUE / 4
                var local = bestMove
                // Score every move once, then spend the deeper budgets on the best of them.
                val limit = if (depth == 1) packed.size else minOf(width, packed.size)
                for (i in 0 until limit) {
                    if (stop()) break
                    val move = packed[i]
                    val captured = apply(move)
                    val scored = if (countMoves(-g.turn) == 0) 90_000 + depth
                    else -value(-g.turn, depth - 1, -beta, -alpha, 0)
                    undo(move, captured)
                    rootScore[i] = scored
                    if (scored > bestScore) {
                        bestScore = scored
                        local = move
                    }
                    if (bestScore > alpha) alpha = bestScore
                }
                if (aborted) break
                bestMove = local
                sort(packed, rootScore, 0, limit)
                depth++
            }
            val chosen = RpsMove(bestMove ushr 6, bestMove and 63)
            return if (chosen in legal) chosen else legal.first()
        }

        private fun value(player: Int, depth: Int, alpha0: Int, beta: Int, ply: Int): Int {
            checkpoint()
            nodes++
            if (stop()) return eval(player)
            if (depth <= 0) return if (qLimit > 0) qsearch(player, qLimit, alpha0, beta, ply) else eval(player)
            val base = ply * STRIDE
            val end = gen(player, base, capturesOnly = false)
            if (end == base) return -90_000 - depth
            order(base, end, player, ply)
            var alpha = alpha0
            var best = Int.MIN_VALUE / 4
            val limit = minOf(end, base + width)
            for (i in base until limit) {
                if (stop()) return best
                val move = moves[i]
                val captured = apply(move)
                val scored = if (countMoves(-player) == 0) 90_000 + depth
                else -value(-player, depth - 1, -beta, -alpha, ply + 1)
                undo(move, captured)
                if (scored > best) best = scored
                if (best > alpha) alpha = best
                if (alpha >= beta) {
                    killers[ply] = move
                    break
                }
            }
            return best
        }

        /** Capture-only extension. A quiet evaluation is always allowed, so a bad capture can be declined. */
        private fun qsearch(player: Int, q: Int, alpha0: Int, beta: Int, ply: Int): Int {
            nodes++
            val stand = eval(player)
            if (stand >= beta || q == 0 || stop()) return stand
            var alpha = if (stand > alpha0) stand else alpha0
            var best = stand
            val base = ply * STRIDE
            val end = gen(player, base, capturesOnly = true)
            if (end == base) return stand
            order(base, end, player, ply)
            val limit = minOf(end, base + 8)
            for (i in base until limit) {
                if (stop()) return best
                val move = moves[i]
                val captured = apply(move)
                val scored = if (countMoves(-player) == 0) 90_000 + q
                else -qsearch(-player, q - 1, -beta, -alpha, ply + 1)
                undo(move, captured)
                if (scored > best) best = scored
                if (best > alpha) alpha = best
                if (alpha >= beta) break
            }
            return best
        }

        private fun eval(player: Int): Int {
            var material = 0
            var forward = 0
            var hanging = 0
            for (i in board.indices) {
                val cell = board[i]
                if (cell == 0) continue
                val sign = if (cell.sign == player) 1 else -1
                material += sign * 120
                val row = i / n
                val adv = if (cell > 0) n - 1 - row else row
                forward += sign * adv * 3
                if (attacked(i, cell)) hanging -= sign * 70
            }
            val mobility = countMoves(player) * 5 - countMoves(-player) * 5
            return material + forward + hanging + mobility + 8
        }

        private fun attacked(index: Int, piece: Int): Boolean =
            neigh[index].any { at ->
                val occ = board[at]
                occ != 0 && occ.sign != piece.sign && Rps.beats(occ, piece)
            }

        private fun gen(player: Int, base: Int, capturesOnly: Boolean): Int {
            var m = base
            for (from in board.indices) {
                val piece = board[from]
                if (piece == 0 || piece.sign != player) continue
                val around = neigh[from]
                for (k in around.indices) {
                    val to = around[k]
                    val occ = board[to]
                    val capture = occ != 0 && occ.sign != piece.sign && Rps.beats(piece, occ)
                    if (capture || (!capturesOnly && occ == 0)) moves[m++] = pack(from, to)
                }
            }
            return m
        }

        private fun countMoves(player: Int): Int {
            var count = 0
            for (from in board.indices) {
                val piece = board[from]
                if (piece == 0 || piece.sign != player) continue
                val around = neigh[from]
                for (k in around.indices) {
                    val occ = board[around[k]]
                    if (occ == 0 || (occ.sign != piece.sign && Rps.beats(piece, occ))) count++
                }
            }
            return count
        }

        private fun order(base: Int, end: Int, player: Int, ply: Int) {
            val killer = killers[ply]
            for (i in base until end) scores[i] = rank(player, moves[i], killer)
            sort(moves, scores, base, end)
        }

        private fun rank(player: Int, move: Int, killer: Int): Int {
            val from = move ushr 6
            val to = move and 63
            val piece = board[from]
            val occ = board[to]
            var score = 0
            if (move == killer) score += 280
            if (occ != 0) score += 520
            if (hangingLanding(from, to, piece)) score -= 360
            val forward = if (player == 1) from / n - to / n else to / n - from / n
            return score + forward * 8
        }

        private fun hangingLanding(from: Int, to: Int, piece: Int): Boolean =
            neigh[to].any { at ->
                if (at == from) return@any false
                val occ = board[at]
                occ != 0 && occ.sign != piece.sign && Rps.beats(occ, piece)
            }

        private fun sort(keys: IntArray, weight: IntArray, start: Int, end: Int) {
            for (i in start + 1 until end) {
                val key = keys[i]
                val w = weight[i]
                var j = i
                while (j > start && (weight[j - 1] < w || (weight[j - 1] == w && keys[j - 1] > key))) {
                    keys[j] = keys[j - 1]
                    weight[j] = weight[j - 1]
                    j--
                }
                keys[j] = key
                weight[j] = w
            }
        }

        private fun apply(move: Int): Int {
            val from = move ushr 6
            val to = move and 63
            val captured = board[to]
            board[to] = board[from]
            board[from] = 0
            return captured
        }

        private fun undo(move: Int, captured: Int) {
            val from = move ushr 6
            val to = move and 63
            board[from] = board[to]
            board[to] = captured
        }

        private fun stop(): Boolean {
            if (nodes > nodeCap || System.nanoTime() > deadline) {
                aborted = true
                return true
            }
            return false
        }

        private fun pack(from: Int, to: Int) = (from shl 6) or to

        companion object {
            const val STRIDE = 96
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
