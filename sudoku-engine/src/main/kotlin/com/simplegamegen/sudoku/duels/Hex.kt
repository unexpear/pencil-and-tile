package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.math.abs
import kotlin.random.Random

/**
 * Hex (Piet Hein 1942, John Nash 1948) on an N×N rhombus of hexes.
 * Player 1 (red) joins the top and bottom edges. Player -1 (blue) joins the left and right.
 * Stones never move or get captured. Six hex neighbors touch. A full board always has a winner.
 *
 * The swap (pie) rule: after the first stone, the second player may take it instead of placing.
 * The stone is mirrored across the long diagonal (row and column swap) and becomes theirs.
 * [setting] picks the board (7, 9 or 11) and either a computer strength or pass-and-play.
 * Against the computer, you are red and move first.
 */
data class Hex(
    val setting: Int,
    val seed: Long,
    val cells: List<Int> = List(sizeOf(setting) * sizeOf(setting)) { 0 },
    val turn: Int = 1,
    val last: Int? = null,
    val winner: Int = 0,
    val swapped: Boolean = false,
) {
    val size: Int = sizeOf(setting)
    val passAndPlay: Boolean = passOf(setting)
    val level: Int = levelOf(setting)

    init {
        require(setting in NAMES.indices && cells.size == size * size && cells.all { it in -1..1 })
        require(turn == 1 || turn == -1)
        require(winner in -1..1)
        require(last == null || (last in cells.indices && cells[last] != 0))
        val red = cells.count { it == 1 }
        val blue = cells.count { it == -1 }
        if (swapped) require(blue == red || blue == red + 1) else require(red == blue || red == blue + 1)
        require(connects(cells, size, 1) == (winner == 1))
        require(connects(cells, size, -1) == (winner == -1))
        if (winner == 1) require(if (swapped) red == blue else red == blue + 1)
        if (winner == -1) require(if (swapped) blue == red + 1 else red == blue)
        if (!over) {
            if (swapped) require(if (blue == red) turn == -1 else turn == 1)
            else require(if (red == blue) turn == 1 else turn == -1)
        }
        if (red + blue == 0) require(!swapped && last == null && turn == 1 && winner == 0)
    }

    val over: Boolean get() = winner != 0 || cells.none { it == 0 }
    val draw: Boolean get() = over && winner == 0

    /** Second player may take the opening stone, once, instead of placing. */
    val canSwap: Boolean get() = !over && !swapped && turn == -1 &&
        cells.count { it == 1 } == 1 && cells.count { it == -1 } == 0

    fun legalMoves(): List<Int> {
        if (over) return emptyList()
        val open = cells.indices.filter { cells[it] == 0 }
        return if (canSwap) listOf(SWAP) + open else open
    }

    /** Places the stone of the player to move. Null when the cell is illegal. */
    fun place(index: Int): Hex? {
        if (over || index !in cells.indices || cells[index] != 0) return null
        val next = cells.toMutableList()
        next[index] = turn
        val won = connects(next, size, turn)
        return copy(cells = next, turn = if (won) turn else -turn, last = index, winner = if (won) turn else 0)
    }

    /**
     * Pie rule. The opening stone is mirrored across the long diagonal and repainted
     * in the second player's color. That uses their turn.
     */
    fun swap(): Hex? {
        if (!canSwap) return null
        val from = last ?: return null
        val to = mirror(size, from)
        val next = cells.toMutableList()
        next[from] = 0
        next[to] = -1
        return copy(cells = next, turn = 1, last = to, swapped = true, winner = 0)
    }

    fun apply(move: Int): Hex? = if (move == SWAP) swap() else place(move)

    /** One chain from the winner's edge to the other, for the highlight. */
    fun winningPath(): List<Int> {
        if (winner == 0) return emptyList()
        return path(cells, size, winner)
    }

    companion object {
        const val SWAP = -1
        val LEVELS = listOf("Easy", "Medium", "Hard", "Expert")
        val NAMES: List<String> = buildList {
            for (size in intArrayOf(11, 9, 7)) for (level in LEVELS) add("${size}×${size} $level")
            for (size in intArrayOf(11, 9, 7)) add("${size}×${size} two players")
        }

        /** Axial neighbors: N, NE, W, E, SW, SE. Not the square diagonals (r±1, c±1). */
        val DELTAS = arrayOf(-1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0)

        fun start(seed: Long, setting: Int) = Hex(setting, seed)

        fun sizeOf(setting: Int): Int {
            require(setting in NAMES.indices)
            val group = if (setting >= 12) setting - 12 else setting / 4
            return intArrayOf(11, 9, 7)[group]
        }

        fun levelOf(setting: Int) = if (setting >= 12) 0 else setting % 4
        fun passOf(setting: Int) = setting >= 12

        fun mirror(size: Int, index: Int): Int {
            val row = index / size
            val col = index % size
            return col * size + row
        }

        fun neighbors(size: Int, index: Int): IntArray = graph(size)[index]

        fun connects(cells: List<Int>, size: Int, player: Int): Boolean =
            connectionCost(cells.toIntArray(), size, graph(size), player) == 0

        private fun built(size: Int): Array<IntArray> = Array(size * size) { index ->
            val row = index / size
            val col = index % size
            DELTAS.mapNotNull { (dr, dc) ->
                val rr = row + dr
                val cc = col + dc
                if (rr in 0 until size && cc in 0 until size) rr * size + cc else null
            }.toIntArray()
        }

        private val graphs = mapOf(7 to built(7), 9 to built(9), 11 to built(11))

        private fun graph(size: Int): Array<IntArray> = graphs.getValue(size)
    }
}

private const val INF = 1_000_000

/** Stones of [player] cost 0, empty cells cost 1, and the other color is blocked. 0 means the edges already meet. */
private fun connectionCost(board: IntArray, size: Int, neigh: Array<IntArray>, player: Int): Int {
    val vertical = player == 1
    val n = board.size
    val dist = IntArray(n) { INF }
    val settled = BooleanArray(n)
    val dq = ArrayDeque<Int>()
    for (i in 0 until n) {
        val start = if (vertical) i / size == 0 else i % size == 0
        if (!start || board[i] == -player) continue
        dist[i] = if (board[i] == player) 0 else 1
        if (dist[i] == 0) dq.addFirst(i) else dq.addLast(i)
    }
    var best = INF
    while (dq.isNotEmpty()) {
        val u = dq.removeFirst()
        if (settled[u]) continue
        settled[u] = true
        val goal = if (vertical) u / size == size - 1 else u % size == size - 1
        if (goal && dist[u] < best) best = dist[u]
        if (dist[u] >= best) continue
        for (v in neigh[u]) {
            if (board[v] == -player) continue
            val step = if (board[v] == player) 0 else 1
            val next = dist[u] + step
            if (next < dist[v]) {
                dist[v] = next
                if (step == 0) dq.addFirst(v) else dq.addLast(v)
            }
        }
    }
    return best
}

private fun path(cells: List<Int>, size: Int, player: Int): List<Int> {
    val n = cells.size
    val neigh = Array(n) { Hex.neighbors(size, it) }
    val vertical = player == 1
    val parent = IntArray(n) { -2 }
    val q = ArrayDeque<Int>()
    for (i in cells.indices) {
        val start = if (vertical) i / size == 0 else i % size == 0
        if (start && cells[i] == player) {
            parent[i] = -1
            q.add(i)
        }
    }
    var hit = -1
    while (q.isNotEmpty()) {
        val u = q.removeFirst()
        val goal = if (vertical) u / size == size - 1 else u % size == size - 1
        if (goal) { hit = u; break }
        for (v in neigh[u]) if (parent[v] == -2 && cells[v] == player) {
            parent[v] = u
            q.add(v)
        }
    }
    if (hit < 0) return emptyList()
    val out = ArrayList<Int>()
    var cursor = hit
    while (cursor >= 0) {
        out += cursor
        cursor = parent[cursor]
    }
    out.reverse()
    return out
}

object HexAi {
    /**
     * A legal move for the player about to move. [Hex.SWAP] takes the opening stone.
     * Easy prefers a stone next to the fight, usually takes a win, and often answers a threat.
     * Medium and above always connect, answer a threat, and swap a strong opening.
     * Hard and Expert look further ahead, with a short time cap so a phone stays responsive.
     */
    fun choose(g: Hex): Int {
        checkpoint()
        require(!g.over)
        val legal = g.legalMoves()
        require(legal.isNotEmpty())
        val search = Search(g)
        val me = g.turn
        val wins = search.urgent(me)
        val blocks = search.urgent(-me)
        val random = Random(g.seed xor (g.cells.count { it != 0 }.toLong() shl 8) xor g.turn.toLong())
        if (g.level == 0) return easy(g, search, wins, blocks, random)
        if (wins.isNotEmpty()) return wins.first()
        if (blocks.isNotEmpty()) return blocks.first()
        if (g.canSwap && search.central(g.last!!)) return Hex.SWAP
        val maxDepth = when (g.level) { 1 -> 2; 2 -> 3; else -> 4 }
        val width = when (g.level) { 1 -> 10; 2 -> 12; else -> 14 }
        val millis = when (g.level) { 1 -> 50L; 2 -> 110L; else -> 170L }
        search.deadline = System.nanoTime() + millis * 1_000_000L
        var chosen = search.nearby(me, 1).first()
        var depth = 1
        while (depth <= maxDepth) {
            search.aborted = false
            var local = chosen
            var best = Int.MIN_VALUE / 4
            if (g.canSwap) {
                val swapped = g.swap()!!
                val next = Search(swapped)
                next.deadline = search.deadline
                val score = if (depth == 1) -next.eval(1) else -next.value(1, depth - 1, Int.MIN_VALUE / 4, Int.MAX_VALUE / 4, width)
                if (!next.aborted && score > best) { best = score; local = Hex.SWAP }
            }
            val pool = search.nearby(me, width)
            for (move in pool) {
                if (search.aborted) break
                checkpoint()
                search.board[move] = me
                val score = if (search.cost(me) == 0) 90_000 + depth else -search.value(-me, depth - 1, Int.MIN_VALUE / 4, Int.MAX_VALUE / 4, width)
                search.board[move] = 0
                if (search.aborted) break
                if (score > best) { best = score; local = move }
            }
            if (search.aborted && depth > 1) break
            chosen = local
            depth++
        }
        return if (chosen == Hex.SWAP || chosen in legal) chosen else legal.first()
    }

    private fun easy(g: Hex, search: Search, wins: List<Int>, blocks: List<Int>, random: Random): Int {
        if (wins.isNotEmpty() && random.nextInt(100) < 80) return wins.first()
        if (blocks.isNotEmpty() && random.nextInt(100) < 48) return blocks.first()
        if (g.canSwap && search.central(g.last!!) && random.nextInt(100) < 35) return Hex.SWAP
        val near = search.nearby(g.turn, 10)
        val weights = intArrayOf(5, 2, 1)
        val n = minOf(weights.size, near.size)
        var total = 0
        for (i in 0 until n) total += weights[i]
        var roll = random.nextInt(total.coerceAtLeast(1))
        for (i in 0 until n) {
            roll -= weights[i]
            if (roll < 0) return near[i]
        }
        return near.first()
    }

    private class Search(g: Hex) {
        val board = g.cells.toIntArray()
        val size = g.size
        val neigh = Array(board.size) { Hex.neighbors(size, it) }
        var deadline = Long.MAX_VALUE
        var aborted = false

        fun cost(player: Int) = connectionCost(board, size, neigh, player)

        /** Near the middle, where an opening stone is worth taking with the swap rule. */
        fun central(index: Int): Boolean {
            val mid = (size - 1) / 2.0
            val row = index / size
            val col = index % size
            return abs(row - mid) + abs(col - mid) <= size / 4.0
        }

        /** Empty cells next to [player] that would finish their chain. */
        fun urgent(player: Int): List<Int> {
            if (board.none { it == player }) return emptyList()
            val out = ArrayList<Int>(2)
            val seen = HashSet<Int>()
            for (i in board.indices) if (board[i] == player) {
                for (v in neigh[i]) if (board[v] == 0 && seen.add(v)) {
                    board[v] = player
                    val won = cost(player) == 0
                    board[v] = 0
                    if (won) out += v
                }
            }
            return out
        }

        fun nearby(player: Int, limit: Int): List<Int> {
            val near = LinkedHashSet<Int>()
            val stones = board.indices.filter { board[it] != 0 }
            if (stones.isEmpty()) {
                val mid = size / 2
                return board.indices.sortedBy { abs(it / size - mid) + abs(it % size - mid) }.take(limit)
            }
            for (i in stones) {
                for (v in neigh[i]) if (board[v] == 0) near += v
                for (v in neigh[i]) for (w in neigh[v]) if (board[w] == 0) near += w
            }
            if (near.isEmpty()) return board.indices.filter { board[it] == 0 }.take(limit)
            return near.sortedByDescending { quiet(it, player) }.take(limit)
        }

        private fun quiet(index: Int, player: Int): Int {
            var score = 0
            for (v in neigh[index]) when (board[v]) {
                player -> score += 4
                0 -> score += 1
                else -> score -= 1
            }
            val mid = (size - 1) / 2.0
            score += (size - (abs(index / size - mid) + abs(index % size - mid))).toInt()
            return score
        }

        fun eval(turn: Int): Int {
            val mine = cost(turn)
            val opp = cost(-turn)
            if (mine == 0) return 80_000
            if (opp == 0) return -80_000
            return (opp - mine) * 80 + centrality(turn) - centrality(-turn) + bridges(turn) - bridges(-turn)
        }

        private fun centrality(player: Int): Int {
            val mid = (size - 1) / 2.0
            var score = 0
            for (i in board.indices) if (board[i] == player) {
                score += (size - (abs(i / size - mid) + abs(i % size - mid))).toInt()
            }
            return score
        }

        /** An empty hex between two of your stones that do not already touch. */
        private fun bridges(player: Int): Int {
            var found = 0
            for (i in board.indices) if (board[i] == 0) {
                val friends = neigh[i].filter { board[it] == player }
                var bridge = false
                for (a in friends.indices) {
                    for (b in a + 1 until friends.size) {
                        if (friends[b] !in neigh[friends[a]]) { bridge = true; break }
                    }
                    if (bridge) break
                }
                if (bridge) found += 6
            }
            return found
        }

        fun value(turn: Int, depth: Int, alpha: Int, beta: Int, width: Int): Int {
            checkpoint()
            if (System.nanoTime() > deadline) { aborted = true; return eval(turn) }
            if (depth == 0) return eval(turn)
            val wins = urgent(turn)
            if (wins.isNotEmpty()) return 90_000 + depth
            val blocks = urgent(-turn)
            val moves = if (blocks.isNotEmpty()) blocks else nearby(turn, width)
            if (moves.isEmpty()) return 0
            var best = Int.MIN_VALUE / 4
            var a = alpha
            for (move in moves) {
                board[move] = turn
                val scored = if (cost(turn) == 0) 90_000 + depth else -value(-turn, depth - 1, -beta, -a, width)
                board[move] = 0
                if (aborted) return best
                if (scored > best) best = scored
                if (best > a) a = best
                if (a >= beta) break
            }
            return best
        }
    }
}

object HexCodec {
    fun encode(g: Hex) = listOf(
        "1", g.setting.toString(), g.seed.toString(), g.cells.joinToString(","),
        g.turn.toString(), g.last?.toString() ?: "", g.winner.toString(), if (g.swapped) "1" else "0",
    ).joinToString("\n")

    fun decode(text: String): Hex? = try {
        val lines = text.split('\n')
        require(lines.size == 8 && lines[0] == "1")
        Hex(
            lines[1].toInt(), lines[2].toLong(), lines[3].split(',').map(String::toInt),
            lines[4].toInt(), lines[5].toIntOrNull(), lines[6].toInt(), lines[7] == "1",
        )
    } catch (_: IllegalArgumentException) { null } catch (_: IllegalStateException) { null }
}
