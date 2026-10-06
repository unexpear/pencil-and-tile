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
 * own C++ search. This opponent stays on the phone: it prefers captures, stays
 * off the edge, and counts nearby stones. Higher levels look further ahead.
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
    val counting: Boolean = false,
    val dead: List<Int> = emptyList(),
) {
    init {
        require(setting in NAMES.indices && size in 5..19 && board.size == size * size)
        require(board.all { it == 0 || it == BLACK || it == WHITE })
        require(turn == BLACK || turn == WHITE)
        require(ko == -1 || ko in board.indices)
        require(passes in 0..2 && prisoners >= 0 && winner in -1..1)
        require(dead.all { it in board.indices && board[it] != 0 })
        if (counting) require(!ended)
    }

    val over: Boolean get() = ended

    fun legalPlacements(): List<Int> {
        if (ended) return emptyList()
        return board.indices.filter { place(it) != null }
    }

    /** Places [point] for the side to move. Null when the point is illegal. */
    fun place(point: Int): Go? {
        if (ended || counting || point !in board.indices || board[point] != 0 || point == ko) return null
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
        if (ended || counting) return null
        val nextPasses = passes + 1
        if (nextPasses < 2) return copy(turn = -turn, ko = -1, last = PASS, passes = nextPasses)
        return copy(turn = -turn, ko = -1, last = PASS, passes = 2, counting = true, dead = GoLife.dead(board, size))
    }

    /** Toggles the whole group at [point] as dead. Used only while counting. */
    fun mark(point: Int): Go? {
        if (!counting || point !in board.indices || board[point] == 0) return null
        val group = groupAndLibs(board, point).first.toSet()
        val marked = dead.toSet()
        val next = if (group.all { it in marked }) dead.filter { it !in group } else (dead + group).distinct().sorted()
        return copy(dead = next)
    }

    /** Removes the marked stones and scores the board that remains. */
    fun count(): Go? {
        if (!counting) return null
        val gone = dead.toSet()
        val cleared = board.mapIndexed { i, stone -> if (i in gone) 0 else stone }
        val (black, white) = area(cleared, size)
        val winner = if (black > white + KOMI) BLACK else WHITE
        return copy(
            board = cleared, counting = false, dead = emptyList(), ended = true,
            winner = winner, blackArea = black, whiteArea = white,
        )
    }

    /** Leaves the counting step and continues, with the same side to move. */
    fun resume(): Go? = if (!counting) null else copy(counting = false, passes = 0, dead = emptyList(), ko = -1)

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
        val (stones, libs) = groupPoints(on, start)
        return stones to libs.size
    }

    /** Stones in the group at [start], and the empty points beside them. */
    fun groupPoints(start: Int): Pair<List<Int>, List<Int>> = groupPoints(board, start)

    private fun groupPoints(on: List<Int>, start: Int): Pair<List<Int>, List<Int>> {
        val color = on[start]
        if (color == 0) return emptyList<Int>() to neighbors(start).filter { on[it] == 0 }
        val seen = LinkedHashSet<Int>()
        val libs = LinkedHashSet<Int>()
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
        return seen.toList() to libs.toList()
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
    /**
     * A legal placement, or [Go.PASS].
     * Easy takes a capture or saves a stone in atari more often than not, and otherwise plays near the stones.
     * Medium and above answer atari, avoid self-atari, and build toward the centre.
     * Hard and Expert compare a few replies and stop if the clock runs long.
     */
    fun choose(g: Go): Int {
        checkpoint()
        val random = Random(g.seed xor g.board.fold(0) { n, p -> n * 31 + p }.toLong() xor g.turn.toLong())
        val moves = g.legalPlacements()
        if (moves.isEmpty()) return Go.PASS
        val calm = moves.filter { !g.eye(it, g.turn) }
        if (calm.isEmpty()) return Go.PASS
        val saves = atariLiberties(g).filter { it in calm }
        val captures = calm.filter { captures(g, it) > 0 }
        val local = localCandidates(g, calm)
        if (g.setting == 0) {
            if (saves.isNotEmpty() && random.nextInt(100) < 68) return saves.random(random)
            if (captures.isNotEmpty() && random.nextInt(100) < 74) return captures.maxBy { captures(g, it) }
            val safe = local.filter { point -> captures(g, point) > 0 || !selfAtari(g, point) }
            val pool = (if (safe.isNotEmpty()) safe else local).sortedByDescending { heuristic(g, it, saves.toSet()) }
            return noisy(pool, random)
        }
        if (saves.isNotEmpty() && captures.none { captures(g, it) >= 2 }) {
            val save = saves.maxBy { heuristic(g, it, saves.toSet()) }
            if (g.setting == 1) return save
        }
        val pool = (local + saves + captures).distinct()
        val saveSet = saves.toSet()
        val ranked = pool.sortedByDescending { heuristic(g, it, saveSet) }
        if (g.setting == 1) {
            val safe = ranked.filter { captures(g, it) > 0 || !selfAtari(g, it) }
            val use = if (safe.isNotEmpty()) safe else ranked
            val best = heuristic(g, use.first(), saveSet)
            return use.filter { heuristic(g, it, saveSet) >= best - 2 }.take(3).random(random)
        }
        val width = if (g.setting >= 3) 10 else 8
        val replies = if (g.setting >= 3) 4 else 2
        val deadline = System.nanoTime() + (if (g.setting >= 3) 140L else 80L) * 1_000_000L
        var bestMove = ranked.first()
        var bestScore = Int.MIN_VALUE
        for (move in ranked.take(width)) {
            if (System.nanoTime() > deadline) break
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

    private fun noisy(points: List<Int>, random: Random): Int {
        if (points.isEmpty()) return Go.PASS
        val weights = intArrayOf(5, 2, 1)
        val n = minOf(weights.size, points.size)
        var total = 0
        for (i in 0 until n) total += weights[i]
        var roll = random.nextInt(total.coerceAtLeast(1))
        for (i in 0 until n) {
            roll -= weights[i]
            if (roll < 0) return points[i]
        }
        return points.first()
    }

    /** Empty liberties of our groups that have only one liberty left. */
    private fun atariLiberties(g: Go): List<Int> {
        val seen = HashSet<Int>()
        val out = ArrayList<Int>()
        for (point in g.board.indices) {
            if (g.board[point] != g.turn || !seen.add(point)) continue
            val (group, libs) = g.groupAndLibs(g.board, point)
            seen.addAll(group)
            if (libs != 1) continue
            val liberty = group.asSequence().flatMap { g.neighbors(it) }.firstOrNull { g.board[it] == 0 }
            if (liberty != null) out += liberty
        }
        return out.distinct()
    }

    private fun selfAtari(g: Go, point: Int): Boolean {
        val next = g.place(point) ?: return true
        val (_, libs) = next.groupAndLibs(next.board, point)
        return libs <= 1 && captures(g, point) == 0
    }

    /** Points next to stones, or the opening star points when the board is empty. */
    private fun localCandidates(g: Go, calm: List<Int>): List<Int> {
        val calmSet = calm.toHashSet()
        val stones = g.board.indices.filter { g.board[it] != 0 }
        if (stones.isEmpty()) {
            val stars = starPoints(g.size).filter { it in calmSet }
            if (stars.isNotEmpty()) return stars
        }
        val near = LinkedHashSet<Int>()
        for (stone in stones) {
            val row = stone / g.size
            val col = stone % g.size
            for (dr in -2..2) for (dc in -2..2) {
                val r = row + dr
                val c = col + dc
                if (r !in 0 until g.size || c !in 0 until g.size) continue
                val point = r * g.size + c
                if (point in calmSet) near += point
            }
        }
        return if (near.isNotEmpty()) near.toList() else calm
    }

    private fun starPoints(size: Int): List<Int> {
        val mid = size / 2
        val edge = if (size >= 13) 3 else 2
        val coords = listOf(edge, mid, size - 1 - edge).distinct()
        return coords.flatMap { row -> coords.map { col -> row * size + col } }
    }

    private fun answer(g: Go, width: Int): Int {
        val moves = g.legalPlacements().filter { !g.eye(it, g.turn) }
        if (moves.isEmpty()) return outlook(g)
        val saves = atariLiberties(g).toSet()
        return moves.sortedByDescending { heuristic(g, it, saves) }.take(width).maxOf { move ->
            outlook(g.place(move) ?: g)
        }
    }

    /** Area advantage for the side that just moved, seen from the side about to move. */
    private fun outlook(g: Go): Int {
        val (black, white) = Go.area(g.board, g.size)
        val blackLead = black - white
        return -g.turn * blackLead
    }

    private fun heuristic(g: Go, point: Int, saves: Set<Int> = emptySet()): Int {
        val taken = captures(g, point)
        val next = g.place(point) ?: return Int.MIN_VALUE
        val (own, libs) = next.groupAndLibs(next.board, point)
        var score = taken * 80 + libs * 6 - own.size + nearby(g, point)
        if (libs <= 1 && taken == 0) score -= 70
        if (point in saves) score += 90
        val center = g.size / 2
        val row = point / g.size
        val col = point % g.size
        score -= (kotlin.math.abs(row - center) + kotlin.math.abs(col - center))
        if (g.eye(point, -g.turn)) score += 25
        return score
    }

    /** Nearby empty points and stones, so the player builds toward the middle instead of the edge. */
    private fun nearby(g: Go, point: Int): Int {
        val row = point / g.size
        val col = point % g.size
        var score = 0
        for (dr in -2..2) for (dc in -2..2) {
            if (dr == 0 && dc == 0) continue
            val r = row + dr
            val c = col + dc
            if (r !in 0 until g.size || c !in 0 until g.size) continue
            val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
            score += when (g.board[r * g.size + c]) {
                0 -> 3 - dist
                g.turn -> 1
                else -> -2
            }
        }
        if (row == 0 || col == 0 || row == g.size - 1 || col == g.size - 1) score -= 6
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
        if (g.counting) "1" else "0",
        g.dead.joinToString(","),
    ).joinToString("\n")

    fun decode(text: String): Go? = try {
        val lines = text.split('\n')
        require((lines.size == 15 || lines.size == 17) && lines[0] == "1")
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
            counting = lines.size == 17 && lines[15] == "1",
            dead = if (lines.size == 17 && lines[16].isNotEmpty()) lines[16].split(',').map { it.toInt() } else emptyList(),
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
