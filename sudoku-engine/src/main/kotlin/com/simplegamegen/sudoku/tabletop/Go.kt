package com.simplegamegen.sudoku.tabletop

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Go on a 19×19 board. You play black and move first. White is the computer.
 * Chinese area scoring: stones on the board count, and an empty region counts
 * for the one color that borders it. White receives [KOMI]. Positional superko.
 * [setting] is Easy…Expert.
 *
 * KataGo publishes a free pretrained network, but it only plays through KataGo's
 * own C++ search. This opponent is the on-phone stand-in: Easy mostly places at
 * random, and higher levels look further ahead.
 */
data class Go(
    val setting: Int,
    val seed: Long,
    val size: Int = 19,
    val board: List<Int> = List(size * size) { 0 },
    val turn: Int = BLACK,
    val ko: Int = -1,
    val last: Int? = null,
    val passes: Int = 0,
    val prisoners: Int = 0,
    val ended: Boolean = false,
    val winner: Int = 0,
    val blackArea: Int = 0,
    val whiteArea: Int = 0,
    val seen: List<Long> = emptyList(),
) {
    init {
        require(setting in NAMES.indices && size in 5..19 && board.size == size * size)
        require(board.all { it == 0 || it == BLACK || it == WHITE })
        require(turn == BLACK || turn == WHITE)
        require(ko == -1 || ko in board.indices)
        require(passes in 0..2 && prisoners >= 0 && winner in -1..1)
    }

    val over: Boolean get() = ended

    fun legalPlacements(): List<Int> {
        if (ended) return emptyList()
        return board.indices.filter { place(it) != null }
    }

    /** Places [point] for the side to move. Null when the point is illegal. */
    fun place(point: Int): Go? {
        if (ended || point !in board.indices || board[point] != 0 || point == ko) return null
        val next = board.toMutableList()
        next[point] = turn
        val doomed = LinkedHashSet<Int>()
        for (n in neighbors(point)) {
            if (next[n] != -turn) continue
            val (group, libs) = groupAndLibs(next, n)
            if (libs == 0) doomed.addAll(group)
        }
        for (stone in doomed) next[stone] = 0
        val (own, libs) = groupAndLibs(next, point)
        if (libs == 0) return null
        val history = if (seen.isEmpty()) listOf(hash(board)) else seen
        val key = hash(next)
        if (key in history) return null
        val newKo = if (doomed.size == 1 && own.size == 1 && libs == 1) doomed.first() else -1
        return copy(
            board = next,
            turn = -turn,
            ko = newKo,
            last = point,
            passes = 0,
            prisoners = prisoners + doomed.size,
            seen = history + key,
        )
    }

    fun pass(): Go? {
        if (ended) return null
        val nextPasses = passes + 1
        if (nextPasses < 2) return copy(turn = -turn, ko = -1, last = PASS, passes = nextPasses)
        val (black, white) = area(board, size)
        val winner = if (black > white + KOMI) BLACK else WHITE
        return copy(
            turn = -turn, ko = -1, last = PASS, passes = 2,
            ended = true, winner = winner, blackArea = black, whiteArea = white,
        )
    }

    fun neighbors(point: Int): List<Int> {
        val n = size
        val row = point / n
        val col = point % n
        return buildList {
            if (row > 0) add(point - n)
            if (row < n - 1) add(point + n)
            if (col > 0) add(point - 1)
            if (col < n - 1) add(point + 1)
        }
    }

    /** Stones in the group, and how many distinct empty neighbors it has. */
    fun groupAndLibs(on: List<Int>, start: Int): Pair<List<Int>, Int> {
        val color = on[start]
        if (color == 0) return emptyList<Int>() to neighbors(start).count { on[it] == 0 }
        val seen = LinkedHashSet<Int>()
        val libs = HashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.add(start)
        seen.add(start)
        while (stack.isNotEmpty()) {
            val point = stack.removeLast()
            for (n in neighbors(point)) {
                when (on[n]) {
                    0 -> libs.add(n)
                    color -> if (seen.add(n)) stack.add(n)
                }
            }
        }
        return seen.toList() to libs.size
    }

    /** An empty point whose every neighbor is [color]. The bot will not fill these. */
    fun eye(point: Int, color: Int, on: List<Int> = board): Boolean {
        if (point !in on.indices || on[point] != 0) return false
        val around = neighbors(point)
        return around.isNotEmpty() && around.all { on[it] == color }
    }

    companion object {
        const val BLACK = 1
        const val WHITE = -1
        const val PASS = -1
        const val KOMI = 7.5
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")

        fun start(seed: Long, setting: Int): Go {
            val opening = Go(setting, seed)
            return opening.copy(seen = listOf(hash(opening.board)))
        }

        fun hash(board: List<Int>): Long {
            var h = 1469598103934665603L
            for (p in board) {
                h = h xor (p + 1).toLong()
                h *= 1099511628211L
            }
            return h
        }

        /** Chinese area: stones, plus empty regions that touch only one color. */
        fun area(board: List<Int>, size: Int): Pair<Int, Int> {
            var black = board.count { it == BLACK }
            var white = board.count { it == WHITE }
            val seen = BooleanArray(board.size)
            fun around(point: Int): List<Int> {
                val n = size
                val row = point / n
                val col = point % n
                return listOfNotNull(
                    if (row > 0) point - n else null,
                    if (row < n - 1) point + n else null,
                    if (col > 0) point - 1 else null,
                    if (col < n - 1) point + 1 else null,
                )
            }
            for (start in board.indices) {
                if (board[start] != 0 || seen[start]) continue
                val region = ArrayList<Int>()
                val borders = HashSet<Int>()
                val stack = ArrayDeque<Int>()
                stack.add(start)
                seen[start] = true
                while (stack.isNotEmpty()) {
                    val point = stack.removeLast()
                    region.add(point)
                    for (n in around(point)) {
                        when (board[n]) {
                            0 -> if (!seen[n]) {
                                seen[n] = true
                                stack.add(n)
                            }
                            else -> borders.add(board[n])
                        }
                    }
                }
                if (borders.size == 1) {
                    if (BLACK in borders) black += region.size else white += region.size
                }
            }
            return black to white
        }
    }
}

object GoAi {
    private val BUDGET = intArrayOf(0, 0, 8, 14)

    /** A legal placement, or [Go.PASS]. */
    fun choose(g: Go): Int {
        checkpoint()
        val random = Random(g.seed xor g.board.fold(0) { n, p -> n * 31 + p }.toLong() xor g.turn.toLong())
        val moves = g.legalPlacements()
        if (moves.isEmpty()) return Go.PASS
        val calm = moves.filter { !g.eye(it, g.turn) }
        val pool = calm.ifEmpty { moves }
        if (g.setting == 0) {
            val captures = pool.filter { captures(g, it) > 0 }
            return if (captures.isNotEmpty() && random.nextInt(10) < 7) captures.random(random) else pool.random(random)
        }
        val ranked = pool.sortedByDescending { heuristic(g, it) }
        if (g.setting == 1) {
            val best = heuristic(g, ranked.first())
            return ranked.filter { heuristic(g, it) == best }.take(4).random(random)
        }
        val width = BUDGET[g.setting]
        val replies = if (g.setting >= 3) 5 else 1
        var bestMove = ranked.first()
        var bestScore = Int.MIN_VALUE
        for (move in ranked.take(width)) {
            checkpoint()
            val next = g.place(move) ?: continue
            val score = if (replies == 1) outlook(next) else -answer(next, replies)
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }
        return bestMove
    }

    private fun answer(g: Go, width: Int): Int {
        val moves = g.legalPlacements().filter { !g.eye(it, g.turn) }
        if (moves.isEmpty()) return outlook(g)
        return moves.sortedByDescending { heuristic(g, it) }.take(width).maxOf { move ->
            outlook(g.place(move) ?: g)
        }
    }

    /** Area advantage for the side that just moved, seen from the side about to move. */
    private fun outlook(g: Go): Int {
        val (black, white) = Go.area(g.board, g.size)
        val blackLead = black - white
        return -g.turn * blackLead
    }

    private fun heuristic(g: Go, point: Int): Int {
        val taken = captures(g, point)
        val next = g.place(point) ?: return Int.MIN_VALUE
        val (own, libs) = next.groupAndLibs(next.board, point)
        var score = taken * 40 + libs * 3 - own.size
        val center = g.size / 2
        val row = point / g.size
        val col = point % g.size
        score -= (kotlin.math.abs(row - center) + kotlin.math.abs(col - center))
        if (g.eye(point, -g.turn)) score += 25
        return score
    }

    private fun captures(g: Go, point: Int): Int {
        val next = g.board.toMutableList()
        next[point] = g.turn
        var taken = HashSet<Int>()
        for (n in g.neighbors(point)) {
            if (next[n] != -g.turn) continue
            val (group, libs) = g.groupAndLibs(next, n)
            if (libs == 0) taken.addAll(group)
        }
        return taken.size
    }
}

object GoCodec {
    fun encode(g: Go) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.size.toString(),
        g.board.joinToString(","),
        g.turn.toString(),
        g.ko.toString(),
        g.last?.toString() ?: "",
        g.passes.toString(),
        g.prisoners.toString(),
        if (g.ended) "1" else "0",
        g.winner.toString(),
        g.blackArea.toString(),
        g.whiteArea.toString(),
        g.seen.joinToString(","),
    ).joinToString("\n")

    fun decode(text: String): Go? = try {
        val lines = text.split('\n')
        require(lines.size == 15 && lines[0] == "1")
        Go(
            setting = lines[1].toInt(),
            seed = lines[2].toLong(),
            size = lines[3].toInt(),
            board = lines[4].split(',').map { it.toInt() },
            turn = lines[5].toInt(),
            ko = lines[6].toInt(),
            last = lines[7].toIntOrNull(),
            passes = lines[8].toInt(),
            prisoners = lines[9].toInt(),
            ended = lines[10] == "1",
            winner = lines[11].toInt(),
            blackArea = lines[12].toInt(),
            whiteArea = lines[13].toInt(),
            seen = if (lines[14].isEmpty()) emptyList() else lines[14].split(',').map { it.toLong() },
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
