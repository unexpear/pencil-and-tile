package com.simplegamegen.sudoku.tabletop

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Raumschach, Ferdinand Maack's 5×5×5 chess (1907), as recorded by Thomas Rayner Dawson
 * and restated by the International Raumschach Federation.
 *
 * Levels A–E, files a–e, ranks 1–5. White occupies A and B and moves first; Black occupies
 * D and E; C starts empty. Each side has the usual men plus two unicorns.
 * White's unicorns start on Bb1 and Be1. Black's start on Da5 and Dd5.
 *
 * A rook slides through faces (one axis). A bishop slides through edges (two axes equally).
 * A unicorn slides through corners (all three axes equally). The queen is rook, bishop and
 * unicorn. The king is one queen step. A knight leaps (0,1,2) on any two axes and may jump.
 * A pawn steps one square rookwise forward — up a rank, or up a level for White — onto an
 * empty cell, and captures one square bishopwise forward. There is no double step, no en
 * passant and no castling. A White pawn promotes to a queen on rank 5 of level E; a Black
 * pawn promotes to a queen on rank 1 of level A. (Maack allowed any piece; this app promotes
 * to a queen only, the same choice as its flat chess.)
 *
 * Checkmate wins. Stalemate, the same position three times, and 50 moves by each side with
 * no capture and no pawn move are draws. [setting] is Easy–Expert, then pass-and-play.
 * Positive men are White; negative are Black. Empty is 0.
 */
data class Raumschach(
    val setting: Int,
    val seed: Long,
    val board: List<Int> = OPENING,
    val turn: Int = 1,
    val lastFrom: Int = -1,
    val lastTo: Int = -1,
    val winner: Int = 0,
    val ended: Boolean = false,
    val halfmove: Int = 0,
    val history: List<String> = emptyList(),
) {
    val passAndPlay: Boolean get() = setting == PASS

    init {
        require(setting in NAMES.indices && board.size == 125 && board.all { abs(it) in 0..7 })
        require(turn == 1 || turn == -1)
        require(winner in -1..1 && halfmove >= 0)
        require(lastFrom == -1 || lastFrom in board.indices)
        require(lastTo == -1 || lastTo in board.indices)
        require(board.count { it == KING } == 1 && board.count { it == -KING } == 1)
        if (ended && winner != 0) require(inCheck(turn))
    }

    val over: Boolean get() = ended
    val draw: Boolean get() = ended && winner == 0

    fun inCheck(): Boolean = inCheck(turn)

    fun legalMoves(): List<RaumMove> {
        if (ended) return emptyList()
        return board.indices.flatMap { movesFrom(it) }
    }

    fun movesFrom(from: Int): List<RaumMove> {
        if (ended || from !in board.indices || sideOf(board[from]) != turn) return emptyList()
        return pseudo(from).filter { !leaves(it) }
    }

    fun play(move: RaumMove): Raumschach? {
        if (move !in legalMoves()) return null
        return apply(move).finish()
    }

    fun play(from: Int, to: Int): Raumschach? = play(RaumMove(from, to))

    internal fun inCheck(side: Int): Boolean {
        val king = board.indexOfFirst { it == KING * side }
        if (king < 0) return true
        return attacked(king, -side)
    }

    private fun finish(): Raumschach {
        val next = -turn
        val key = positionKey(next)
        val record = history + key
        val replies = copy(turn = next, history = record).legalMoves()
        val mate = replies.isEmpty() && inCheck(next)
        val draw = !mate && (replies.isEmpty() || halfmove >= 100 || record.count { it == key } >= 3)
        return copy(turn = next, history = record, ended = mate || draw, winner = if (mate) turn else 0)
    }

    private fun positionKey(side: Int) = side.toString() + ":" + board.joinToString(",")

    private fun leaves(move: RaumMove) = apply(move).inCheck(turn)

    private fun apply(move: RaumMove): Raumschach {
        val next = board.toMutableList()
        val piece = next[move.from]
        val captured = next[move.to] != 0
        next[move.from] = 0
        next[move.to] = if (abs(piece) == PAWN && promotes(turn, move.to)) QUEEN * turn else piece
        return copy(
            board = next,
            lastFrom = move.from,
            lastTo = move.to,
            halfmove = if (abs(piece) == PAWN || captured) 0 else halfmove + 1,
        )
    }

    private fun pseudo(from: Int): List<RaumMove> {
        val piece = board[from]
        val side = sideOf(piece)
        return when (abs(piece)) {
            PAWN -> pawn(from, side)
            KNIGHT -> leaps(from, side)
            ROOK -> slides(from, side, ROOK_DIRS, 4)
            BISHOP -> slides(from, side, BISHOP_DIRS, 4)
            UNICORN -> slides(from, side, UNI_DIRS, 4)
            QUEEN -> slides(from, side, QUEEN_DIRS, 4)
            KING -> slides(from, side, QUEEN_DIRS, 1)
            else -> emptyList()
        }
    }

    private fun pawn(from: Int, side: Int): List<RaumMove> = buildList {
        val f = file(from); val r = rank(from); val l = level(from)
        val quiet = if (side == 1) listOf(D(0, 1, 0), D(0, 0, 1)) else listOf(D(0, -1, 0), D(0, 0, -1))
        for (d in quiet) {
            val to = at(l + d.l, r + d.r, f + d.f) ?: continue
            if (board[to] == 0) add(RaumMove(from, to))
        }
        val caps = if (side == 1) WHITE_CAP else WHITE_CAP.map { D(-it.f, -it.r, -it.l) }
        for (d in caps) {
            val to = at(l + d.l, r + d.r, f + d.f) ?: continue
            if (sideOf(board[to]) == -side) add(RaumMove(from, to))
        }
    }

    private fun leaps(from: Int, side: Int): List<RaumMove> = buildList {
        val f = file(from); val r = rank(from); val l = level(from)
        for (d in KNIGHT_DELTAS) {
            val to = at(l + d.l, r + d.r, f + d.f) ?: continue
            if (sideOf(board[to]) != side) add(RaumMove(from, to))
        }
    }

    private fun slides(from: Int, side: Int, dirs: List<D>, limit: Int): List<RaumMove> = buildList {
        val f0 = file(from); val r0 = rank(from); val l0 = level(from)
        for (d in dirs) {
            var f = f0; var r = r0; var l = l0
            for (step in 1..limit) {
                f += d.f; r += d.r; l += d.l
                val to = at(l, r, f) ?: break
                val hit = board[to]
                if (hit == 0) add(RaumMove(from, to))
                else {
                    if (sideOf(hit) != side) add(RaumMove(from, to))
                    break
                }
                if (step == limit) break
            }
        }
    }

    private fun attacked(target: Int, by: Int): Boolean {
        val tf = file(target); val tr = rank(target); val tl = level(target)
        for (d in QUEEN_DIRS) {
            var f = tf; var r = tr; var l = tl
            var dist = 0
            while (true) {
                f += d.f; r += d.r; l += d.l
                dist++
                val i = at(l, r, f) ?: break
                val p = board[i]
                if (p == 0) continue
                if (sideOf(p) == by && sliderHits(abs(p), dist, d)) return true
                break
            }
        }
        for (d in KNIGHT_DELTAS) {
            val i = at(tl + d.l, tr + d.r, tf + d.f) ?: continue
            if (board[i] == KNIGHT * by) return true
        }
        val caps = if (by == 1) WHITE_CAP else WHITE_CAP.map { D(-it.f, -it.r, -it.l) }
        for (d in caps) {
            val i = at(tl - d.l, tr - d.r, tf - d.f) ?: continue
            if (board[i] == PAWN * by) return true
        }
        return false
    }

    private fun sliderHits(kind: Int, dist: Int, d: D): Boolean {
        val axes = (if (d.f != 0) 1 else 0) + (if (d.r != 0) 1 else 0) + (if (d.l != 0) 1 else 0)
        return when (kind) {
            ROOK -> axes == 1
            BISHOP -> axes == 2
            UNICORN -> axes == 3
            QUEEN -> true
            KING -> dist == 1
            else -> false
        }
    }

    companion object {
        const val PAWN = 1
        const val KNIGHT = 2
        const val BISHOP = 3
        const val ROOK = 4
        const val QUEEN = 5
        const val KING = 6
        const val UNICORN = 7
        const val PASS = 4
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert", "Two players")
        private val ROOK_DIRS = axis(1)
        private val BISHOP_DIRS = axis(2)
        private val UNI_DIRS = axis(3)
        private val QUEEN_DIRS = axis(1) + axis(2) + axis(3)
        private val WHITE_CAP = listOf(D(1, 1, 0), D(-1, 1, 0), D(0, 1, 1), D(1, 0, 1), D(-1, 0, 1))
        private val KNIGHT_DELTAS = knightDeltas()

        val OPENING: List<Int> = MutableList(125) { 0 }.apply {
            val back = intArrayOf(ROOK, KNIGHT, KING, KNIGHT, ROOK)
            val whiteMid = intArrayOf(BISHOP, UNICORN, QUEEN, BISHOP, UNICORN)
            val blackMid = intArrayOf(UNICORN, BISHOP, QUEEN, UNICORN, BISHOP)
            for (f in 0..4) {
                this[idx(0, 0, f)] = back[f]
                this[idx(0, 1, f)] = PAWN
                this[idx(1, 0, f)] = whiteMid[f]
                this[idx(1, 1, f)] = PAWN
                this[idx(4, 4, f)] = -back[f]
                this[idx(4, 3, f)] = -PAWN
                this[idx(3, 4, f)] = -blackMid[f]
                this[idx(3, 3, f)] = -PAWN
            }
        }

        fun start(seed: Long, setting: Int): Raumschach {
            val opening = Raumschach(setting, seed)
            return opening.copy(history = listOf(opening.positionKey(opening.turn)))
        }

        fun idx(level: Int, rank: Int, file: Int) = level * 25 + rank * 5 + file
        fun level(i: Int) = i / 25
        fun rank(i: Int) = (i / 5) % 5
        fun file(i: Int) = i % 5

        /** Level, file, rank, as in Cc3. */
        fun name(i: Int) = "${'A' + level(i)}${'a' + file(i)}${rank(i) + 1}"

        fun pieceName(code: Int): String = when (abs(code)) {
            PAWN -> "pawn"
            KNIGHT -> "knight"
            BISHOP -> "bishop"
            ROOK -> "rook"
            QUEEN -> "queen"
            KING -> "king"
            UNICORN -> "unicorn"
            else -> "empty"
        }

        private fun promotes(side: Int, to: Int) =
            if (side == 1) level(to) == 4 && rank(to) == 4 else level(to) == 0 && rank(to) == 0

        private fun at(level: Int, rank: Int, file: Int): Int? =
            if (level in 0..4 && rank in 0..4 && file in 0..4) idx(level, rank, file) else null

        private fun axis(n: Int): List<D> = buildList {
            for (l in -1..1) for (r in -1..1) for (f in -1..1) {
                if (l == 0 && r == 0 && f == 0) continue
                val axes = (if (l != 0) 1 else 0) + (if (r != 0) 1 else 0) + (if (f != 0) 1 else 0)
                if (axes == n) add(D(f, r, l))
            }
        }

        private fun knightDeltas(): List<D> = buildList {
            val axes = intArrayOf(0, 1, 2)
            for (i in 0..2) for (signA in intArrayOf(-1, 1)) for (signB in intArrayOf(-1, 1)) {
                val a = axes[i]; val b = axes[(i + 1) % 3]
                val v = IntArray(3)
                v[a] = signA * 1
                v[b] = signB * 2
                add(D(v[0], v[1], v[2]))
                if (a != b) {
                    v[a] = signA * 2
                    v[b] = signB * 1
                    add(D(v[0], v[1], v[2]))
                }
            }
        }.distinct()
    }
}

data class RaumMove(val from: Int, val to: Int)

private data class D(val f: Int, val r: Int, val l: Int)

private fun sideOf(piece: Int) = when {
    piece > 0 -> 1
    piece < 0 -> -1
    else -> 0
}

private fun file(i: Int) = Raumschach.file(i)
private fun rank(i: Int) = Raumschach.rank(i)
private fun level(i: Int) = Raumschach.level(i)

object RaumschachAi {
    private val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 20_000, 300)
    private val BUDGET = intArrayOf(0, 400, 1_500, 4_000)

    fun choose(g: Raumschach): RaumMove {
        checkpoint()
        val legal = g.legalMoves()
        require(legal.isNotEmpty())
        val random = Random(g.seed xor g.board.fold(0L) { n, p -> n * 31 + p } xor (g.turn.toLong() shl 8) xor g.halfmove.toLong())
        if (g.setting == 0 || g.passAndPlay) return legal.random(random)
        val ordered = legal.sortedByDescending { one(g, it) }
        if (g.setting == 1) {
            val best = one(g, ordered.first())
            return ordered.filter { one(g, it) == best }.random(random)
        }
        val nodes = intArrayOf(0)
        val budget = BUDGET[g.setting.coerceAtMost(3)]
        var alpha = Int.MIN_VALUE / 4
        var pick = ordered.first()
        var pickScore = Int.MIN_VALUE / 4
        for (move in ordered) {
            val next = g.play(move)!!
            val score = score(next, g.turn, 1, alpha, Int.MAX_VALUE / 4, nodes, budget)
            if (score > pickScore) {
                pickScore = score
                pick = move
            }
            if (score > alpha) alpha = score
            if (nodes[0] >= budget) break
        }
        return pick
    }

    private fun one(g: Raumschach, move: RaumMove) = standing(g.play(move)!!, g.turn)

    private fun standing(g: Raumschach, player: Int): Int {
        if (g.ended) return when (g.winner) {
            player -> 100_000
            -player -> -100_000
            else -> 0
        }
        var score = 0
        for (i in g.board.indices) {
            val p = g.board[i]
            if (p == 0) continue
            var v = VALUE[abs(p)]
            val df = abs(Raumschach.file(i) - 2)
            val dr = abs(Raumschach.rank(i) - 2)
            val dl = abs(Raumschach.level(i) - 2)
            if (max(df, max(dr, dl)) <= 1) v += 6
            score += if (sideOf(p) == player) v else -v
        }
        return score
    }

    private fun score(g: Raumschach, player: Int, depth: Int, alpha0: Int, beta0: Int, nodes: IntArray, budget: Int): Int {
        checkpoint()
        if (++nodes[0] >= budget || g.ended || depth == 0) return standing(g, player)
        val moves = g.legalMoves()
        if (moves.isEmpty()) return standing(g, player)
        val mine = g.turn == player
        var alpha = alpha0
        var beta = beta0
        var best = if (mine) Int.MIN_VALUE / 4 else Int.MAX_VALUE / 4
        for (move in moves.sortedByDescending { one(g, it) }) {
            val next = g.play(move)!!
            val s = score(next, player, depth - 1, alpha, beta, nodes, budget)
            if (mine) {
                if (s > best) best = s
                if (best > alpha) alpha = best
                if (alpha >= beta || nodes[0] >= budget) break
            } else {
                if (s < best) best = s
                if (best < beta) beta = best
                if (alpha >= beta || nodes[0] >= budget) break
            }
        }
        return best
    }
}

object RaumschachCodec {
    fun encode(g: Raumschach) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.board.joinToString(","),
        g.turn.toString(),
        g.lastFrom.toString(),
        g.lastTo.toString(),
        g.winner.toString(),
        if (g.ended) "1" else "0",
        g.halfmove.toString(),
        g.history.joinToString(";"),
    ).joinToString("\n")

    fun decode(text: String): Raumschach? = try {
        val lines = text.split('\n')
        require(lines.size == 11 && lines[0] == "1")
        Raumschach(
            setting = lines[1].toInt(),
            seed = lines[2].toLong(),
            board = lines[3].split(',').map { it.toInt() },
            turn = lines[4].toInt(),
            lastFrom = lines[5].toInt(),
            lastTo = lines[6].toInt(),
            winner = lines[7].toInt(),
            ended = lines[8] == "1",
            halfmove = lines[9].toInt(),
            history = if (lines[10].isEmpty()) emptyList() else lines[10].split(";"),
        )
    } catch (_: IllegalArgumentException) { null }
}
