package com.simplegamegen.sudoku.tabletop

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.math.abs
import kotlin.random.Random

/**
 * Standard chess. You play white and move first against the computer.
 * Pawns promote to queen only. Castling and en passant are supported.
 * [setting] is the computer's strength (Easy…Expert).
 *
 * Pieces: ±1 pawn, ±2 knight, ±3 bishop, ±4 rook, ±5 queen, ±6 king.
 * Positive is white (you); negative is black (computer). Row 0 is black's
 * back rank; row 7 is white's. Empty is 0.
 */
data class Chess(
    val setting: Int,
    val seed: Long,
    val board: List<Int> = OPENING,
    val turn: Int = 1,
    val castling: Int = ALL_CASTLE,
    val ep: Int = -1,
    val lastFrom: Int? = null,
    val lastTo: Int? = null,
    val winner: Int = 0,
    val ended: Boolean = false,
    val halfmove: Int = 0,
    val history: List<String> = emptyList(),
) {
    init {
        require(setting in NAMES.indices && board.size == 64 && board.all { abs(it) in 0..6 })
        require(turn == 1 || turn == -1)
        require(castling in 0..ALL_CASTLE)
        require(ep == -1 || ep in board.indices)
        require(winner in -1..1)
        require(lastFrom == null || lastFrom in board.indices)
        require(lastTo == null || lastTo in board.indices)
        require(halfmove >= 0)
        if (ended && winner != 0) require(inCheck(turn))
    }

    val over: Boolean get() = ended
    val draw: Boolean get() = ended && winner == 0

    /** True when the side about to move (or just mated) is in check. */
    fun inCheck(): Boolean = inCheck(turn)

    fun legalMoves(): List<ChessMove> {
        if (ended) return emptyList()
        return board.indices.flatMap { from -> movesFrom(from) }
    }

    fun movesFrom(from: Int): List<ChessMove> {
        if (ended || from !in board.indices) return emptyList()
        val piece = board[from]
        if (piece.sign != turn) return emptyList()
        return pseudoFrom(from, piece).filter { !leavesInCheck(it) }
    }

    /** Plays [move] for the side about to move. Null when illegal. */
    fun play(move: ChessMove): Chess? {
        if (move !in legalMoves()) return null
        return applyUnchecked(move).finishTurn()
    }

    fun play(from: Int, to: Int): Chess? = play(ChessMove(from, to))

    internal fun inCheck(side: Int): Boolean {
        val king = board.indexOfFirst { it == KING * side }
        if (king < 0) return true
        return attacked(king, -side)
    }

    private fun finishTurn(): Chess {
        val nextTurn = -turn
        val key = positionKey(nextTurn)
        val record = history + key
        val replies = copy(turn = nextTurn).legalMoves()
        val mate = replies.isEmpty() && inCheck(nextTurn)
        val draw = !mate && (replies.isEmpty() || halfmove >= 100 || record.count { it == key } >= 3)
        return copy(turn = nextTurn, history = record, ended = mate || draw, winner = if (mate) turn else 0)
    }

    private fun leavesInCheck(move: ChessMove): Boolean = applyUnchecked(move).inCheck(turn)

    /** Applies a pseudo-legal move without checking king safety or game end. */
    private fun applyUnchecked(move: ChessMove): Chess {
        val next = board.toMutableList()
        val piece = next[move.from]
        val type = abs(piece)
        var rights = castling
        var nextEp = -1

        if (type == KING) {
            rights = rights and if (turn == 1) (BK or BQ) else (WK or WQ)
            if (abs(move.to % 8 - move.from % 8) == 2) {
                val row = move.from / 8
                if (move.to % 8 == 6) {
                    next[row * 8 + 7] = 0
                    next[row * 8 + 5] = ROOK * turn
                } else {
                    next[row * 8] = 0
                    next[row * 8 + 3] = ROOK * turn
                }
            }
        }
        if (type == ROOK) {
            when (move.from) {
                63 -> rights = rights and (ALL_CASTLE xor WK)
                56 -> rights = rights and (ALL_CASTLE xor WQ)
                7 -> rights = rights and (ALL_CASTLE xor BK)
                0 -> rights = rights and (ALL_CASTLE xor BQ)
            }
        }
        when (move.to) {
            63 -> rights = rights and (ALL_CASTLE xor WK)
            56 -> rights = rights and (ALL_CASTLE xor WQ)
            7 -> rights = rights and (ALL_CASTLE xor BK)
            0 -> rights = rights and (ALL_CASTLE xor BQ)
        }

        if (type == PAWN && abs(move.to / 8 - move.from / 8) == 2) {
            nextEp = (move.from + move.to) / 2
        }
        if (type == PAWN && move.to == ep && next[move.to] == 0) {
            next[move.from / 8 * 8 + move.to % 8] = 0
        }

        val captured = next[move.to] != 0 || (type == PAWN && move.to == ep)
        next[move.from] = 0
        val promoted = type == PAWN && move.to / 8 == if (turn == 1) 0 else 7
        next[move.to] = if (promoted) QUEEN * turn else piece

        return copy(
            board = next,
            castling = rights,
            ep = nextEp,
            lastFrom = move.from,
            lastTo = move.to,
            halfmove = if (type == PAWN || captured) 0 else halfmove + 1,
        )
    }

    private fun pseudoFrom(from: Int, piece: Int): List<ChessMove> {
        val type = abs(piece)
        val side = piece.sign
        return when (type) {
            PAWN -> pawnMoves(from, side)
            KNIGHT -> leaps(from, KNIGHT_DELTAS, side)
            BISHOP -> slides(from, BISHOP_DIRS, side)
            ROOK -> slides(from, ROOK_DIRS, side)
            QUEEN -> slides(from, QUEEN_DIRS, side)
            KING -> kingMoves(from, side)
            else -> emptyList()
        }
    }

    private fun pawnMoves(from: Int, side: Int): List<ChessMove> = buildList {
        val row = from / 8
        val col = from % 8
        val step = -side
        val one = (row + step) * 8 + col
        if (row + step in 0..7 && board[one] == 0) {
            add(ChessMove(from, one))
            val start = if (side == 1) 6 else 1
            val two = (row + 2 * step) * 8 + col
            if (row == start && board[two] == 0) add(ChessMove(from, two))
        }
        for (dc in listOf(-1, 1)) {
            val c = col + dc
            if (c !in 0..7 || row + step !in 0..7) continue
            val to = (row + step) * 8 + c
            if (board[to].sign == -side || (to == ep && board[row * 8 + c] == PAWN * -side)) add(ChessMove(from, to))
        }
    }

    private fun leaps(from: Int, deltas: List<Pair<Int, Int>>, side: Int): List<ChessMove> = buildList {
        val row = from / 8
        val col = from % 8
        for ((dr, dc) in deltas) {
            val r = row + dr
            val c = col + dc
            if (r !in 0..7 || c !in 0..7) continue
            val to = r * 8 + c
            if (board[to].sign != side) add(ChessMove(from, to))
        }
    }

    private fun slides(from: Int, dirs: List<Pair<Int, Int>>, side: Int): List<ChessMove> = buildList {
        val row = from / 8
        val col = from % 8
        for ((dr, dc) in dirs) {
            var r = row + dr
            var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val to = r * 8 + c
                val hit = board[to]
                if (hit == 0) add(ChessMove(from, to))
                else {
                    if (hit.sign != side) add(ChessMove(from, to))
                    break
                }
                r += dr; c += dc
            }
        }
    }

    private fun kingMoves(from: Int, side: Int): List<ChessMove> = buildList {
        addAll(leaps(from, KING_DELTAS, side))
        val home = if (side == 1) 60 else 4
        if (from != home) return@buildList
        if (side == 1) {
            if (castling and WK != 0 && board[61] == 0 && board[62] == 0 &&
                board[63] == ROOK && !attacked(60, -1) && !attacked(61, -1) && !attacked(62, -1)
            ) add(ChessMove(60, 62))
            if (castling and WQ != 0 && board[59] == 0 && board[58] == 0 && board[57] == 0 &&
                board[56] == ROOK && !attacked(60, -1) && !attacked(59, -1) && !attacked(58, -1)
            ) add(ChessMove(60, 58))
        } else {
            if (castling and BK != 0 && board[5] == 0 && board[6] == 0 &&
                board[7] == -ROOK && !attacked(4, 1) && !attacked(5, 1) && !attacked(6, 1)
            ) add(ChessMove(4, 6))
            if (castling and BQ != 0 && board[3] == 0 && board[2] == 0 && board[1] == 0 &&
                board[0] == -ROOK && !attacked(4, 1) && !attacked(3, 1) && !attacked(2, 1)
            ) add(ChessMove(4, 2))
        }
    }

    /** True when [square] is attacked by [by] (1 white, -1 black). */
    fun attacked(square: Int, by: Int): Boolean {
        val row = square / 8
        val col = square % 8
        for ((dr, dc) in KNIGHT_DELTAS) {
            val r = row + dr
            val c = col + dc
            if (r in 0..7 && c in 0..7 && board[r * 8 + c] == KNIGHT * by) return true
        }
        for ((dr, dc) in KING_DELTAS) {
            val r = row + dr
            val c = col + dc
            if (r in 0..7 && c in 0..7 && board[r * 8 + c] == KING * by) return true
        }
        val pawnRow = row + by
        for (dc in listOf(-1, 1)) {
            val c = col + dc
            if (pawnRow in 0..7 && c in 0..7 && board[pawnRow * 8 + c] == PAWN * by) return true
        }
        for ((dr, dc) in BISHOP_DIRS) {
            var r = row + dr
            var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val hit = board[r * 8 + c]
                if (hit != 0) {
                    val t = abs(hit)
                    if (hit.sign == by && (t == BISHOP || t == QUEEN)) return true
                    break
                }
                r += dr; c += dc
            }
        }
        for ((dr, dc) in ROOK_DIRS) {
            var r = row + dr
            var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val hit = board[r * 8 + c]
                if (hit != 0) {
                    val t = abs(hit)
                    if (hit.sign == by && (t == ROOK || t == QUEEN)) return true
                    break
                }
                r += dr; c += dc
            }
        }
        return false
    }

    /** Pieces, side to move, castling, and en passant. Repeating this three times draws. */
    fun positionKey(side: Int = turn) = board.joinToString(",") + "|$side|$castling|$ep"

    companion object {
        const val PAWN = 1
        const val KNIGHT = 2
        const val BISHOP = 3
        const val ROOK = 4
        const val QUEEN = 5
        const val KING = 6
        const val WK = 1
        const val WQ = 2
        const val BK = 4
        const val BQ = 8
        const val ALL_CASTLE = WK or WQ or BK or BQ
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")
        val OPENING: List<Int> = listOf(
            -ROOK, -KNIGHT, -BISHOP, -QUEEN, -KING, -BISHOP, -KNIGHT, -ROOK,
            -PAWN, -PAWN, -PAWN, -PAWN, -PAWN, -PAWN, -PAWN, -PAWN,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0,
            PAWN, PAWN, PAWN, PAWN, PAWN, PAWN, PAWN, PAWN,
            ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK,
        )
        private val KNIGHT_DELTAS = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        private val KING_DELTAS = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
        private val BISHOP_DIRS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        private val ROOK_DIRS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        private val QUEEN_DIRS = BISHOP_DIRS + ROOK_DIRS

        fun start(seed: Long, setting: Int): Chess {
            val opening = Chess(setting, seed)
            return opening.copy(history = listOf(opening.positionKey()))
        }

        fun pieceName(code: Int): String = when (abs(code)) {
            PAWN -> "pawn"
            KNIGHT -> "knight"
            BISHOP -> "bishop"
            ROOK -> "rook"
            QUEEN -> "queen"
            KING -> "king"
            else -> "empty"
        }
    }
}

data class ChessMove(val from: Int, val to: Int)

private val Int.sign: Int get() = when {
    this > 0 -> 1
    this < 0 -> -1
    else -> 0
}

object ChessAi {
    private val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 20_000)
    private val CENTER = setOf(27, 28, 35, 36)
    private val BUDGET = intArrayOf(0, 800, 4_000, 12_000)

    /** A legal move for the side about to move. */
    fun choose(g: Chess): ChessMove {
        checkpoint()
        val legal = g.legalMoves()
        require(legal.isNotEmpty())
        val random = Random(g.seed xor g.board.fold(0L) { n, p -> n * 31 + p } xor (g.turn.toLong() shl 17) xor g.castling.toLong())
        if (g.setting == 0) return legal.random(random)
        val ordered = legal.sortedByDescending { onePly(g, it) }
        if (g.setting == 1) {
            val best = onePly(g, ordered.first())
            return ordered.filter { onePly(g, it) == best }.random(random)
        }
        val depth = if (g.setting == 2) 2 else 3
        val nodes = intArrayOf(0)
        val budget = BUDGET[g.setting]
        var alpha = Int.MIN_VALUE / 4
        var pick = ordered.first()
        var pickScore = Int.MIN_VALUE / 4
        for (move in ordered) {
            val next = g.play(move)!!
            val score = scoreFor(next, g.turn, depth - 1, alpha, Int.MAX_VALUE / 4, nodes, budget)
            if (score > pickScore) {
                pickScore = score
                pick = move
            }
            if (score > alpha) alpha = score
            if (nodes[0] >= budget) break
        }
        return pick
    }

    private fun onePly(g: Chess, move: ChessMove) = standing(g.play(move)!!, g.turn)

    /** Higher is better for [player]. */
    private fun standing(g: Chess, player: Int): Int {
        if (g.ended) return when (g.winner) {
            player -> 100_000
            -player -> -100_000
            else -> 0
        }
        var score = 0
        for (i in g.board.indices) {
            val p = g.board[i]
            if (p == 0) continue
            val v = VALUE[abs(p)] + if (i in CENTER) 8 else 0
            score += if (p.sign == player) v else -v
        }
        return score
    }

    private fun scoreFor(
        g: Chess, player: Int, depth: Int, alpha0: Int, beta0: Int, nodes: IntArray, budget: Int,
    ): Int {
        checkpoint()
        if (++nodes[0] >= budget || g.ended || depth == 0) return standing(g, player)
        val moves = g.legalMoves()
        if (moves.isEmpty()) return standing(g, player)
        val mine = g.turn == player
        var alpha = alpha0
        var beta = beta0
        var best = if (mine) Int.MIN_VALUE / 4 else Int.MAX_VALUE / 4
        for (move in moves.sortedByDescending { onePly(g, it) }) {
            val next = g.play(move)!!
            val score = scoreFor(next, player, depth - 1, alpha, beta, nodes, budget)
            if (mine) {
                if (score > best) best = score
                if (best > alpha) alpha = best
                if (alpha >= beta || nodes[0] >= budget) break
            } else {
                if (score < best) best = score
                if (best < beta) beta = best
                if (alpha >= beta || nodes[0] >= budget) break
            }
        }
        return best
    }
}

object ChessCodec {
    fun encode(g: Chess) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.board.joinToString(","),
        g.turn.toString(),
        g.castling.toString(),
        g.ep.toString(),
        g.lastFrom?.toString() ?: "",
        g.lastTo?.toString() ?: "",
        g.winner.toString(),
        if (g.ended) "1" else "0",
        g.halfmove.toString(),
        g.history.joinToString(";"),
    ).joinToString("\n")

    fun decode(text: String): Chess? = try {
        val lines = text.split('\n')
        require((lines.size == 11 || lines.size == 13) && lines[0] == "1")
        Chess(
            setting = lines[1].toInt(),
            seed = lines[2].toLong(),
            board = lines[3].split(',').map { it.toInt() },
            turn = lines[4].toInt(),
            castling = lines[5].toInt(),
            ep = lines[6].toInt(),
            lastFrom = lines[7].toIntOrNull(),
            lastTo = lines[8].toIntOrNull(),
            winner = lines[9].toInt(),
            ended = lines[10] == "1",
            halfmove = if (lines.size == 13) lines[11].toInt() else 0,
            history = if (lines.size == 13 && lines[12].isNotEmpty()) lines[12].split(";") else emptyList(),
        )
    } catch (_: IllegalArgumentException) { null }
}
