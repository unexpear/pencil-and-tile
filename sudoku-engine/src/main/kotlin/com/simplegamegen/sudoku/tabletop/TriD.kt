package com.simplegamegen.sudoku.tabletop

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Star Trek tri-dimensional chess, played to Charles Roth's "Federation Revised Standard 5.0"
 * (2012), his clarification of Andrew Bartmess's Federation Standard rules for the television board.
 *
 * Three fixed 4×4 main boards and four movable 2×2 attack boards. Files a–f, ranks 0–9, levels 1–7.
 * White's main board is level 2, ranks 1–4, files b–e. The center board is level 4, ranks 3–6.
 * Black's main board is level 6, ranks 5–8. White's attack boards start upright on the posts at
 * b1(2) and e1(2), so their squares are level 3. Black's start upright on b8(6) and e8(6), level 7.
 *
 * White's minor pieces and the four central pawns stand on the low board (knights on b1 and e1,
 * bishops on c1 and d1, pawns on rank 2). King, queen and rooks stand on the attack boards, with
 * the king and queen pawns directly above the knights: queen's board a0 rook, b0 queen, a1 and b1
 * pawns; king's board e0 king, f0 rook, e1 and f1 pawns. Black mirrors this on the high boards.
 *
 * A move changes file or rank by a normal chess step (a knight still leaps). It may end on any
 * level. It may not stay on the same file and rank. The path is the highest square, at each
 * file and rank between, that is not above the higher of the two boards. When that higher board
 * is a main board and an attack board sits one level above it, a second path with that higher
 * ceiling is also allowed (Roth's path A and path B). Knights ignore pieces in the way.
 *
 * Instead of moving a man, a player may move an attack board they own, if it is empty or carries
 * only one of their pawns, and they still have a pawn somewhere. The board slides one or two
 * posts along its own file (queen's boards stay on the b-file posts, king's boards on the e-file
 * posts), and it may invert, hanging from the post instead of standing on it. Inverting in place
 * is a move. A pawn riding the board has used its first move. Boards do not capture men on the
 * main boards. Capturing the last enemy man on an attack board takes ownership of that board.
 *
 * Pawns promote to a queen on rank 8 or 9 for White and rank 0 or 1 for Black, including when a
 * board carries them there. The optional rook-pawn sideways step is not used. Castling: king-side
 * swaps the king with its rook; queen-side, once the queen has left home, teleports the king to
 * the queen's square and the queen's rook to the king's square. Neither may have moved, and the
 * king may not be in check on the square it leaves or the square it lands. A pawn's first move
 * may be two ranks forward, and that pawn can be taken en passant on the next turn only. The
 * capturer lands on the one-step square of the highest path.
 *
 * Checkmate wins. Stalemate, threefold repetition, and 50 moves each with no capture and no pawn
 * move are draws. [setting] is Easy–Expert, then pass-and-play. Positive men are White.
 */
data class TriD(
    val setting: Int,
    val seed: Long,
    val board: List<Int> = OPENING,
    val turn: Int = 1,
    val pins: List<Int> = listOf(0, 0, 5, 5),
    val flip: Int = 0,
    val owners: List<Int> = listOf(1, 1, -1, -1),
    val fresh: List<Int> = FRESH,
    val castling: Int = ALL_CASTLE,
    val epLand: Int = -1,
    val epVictim: Int = -1,
    val lastFrom: Int = -1,
    val lastTo: Int = -1,
    val winner: Int = 0,
    val ended: Boolean = false,
    val halfmove: Int = 0,
    val history: List<String> = emptyList(),
) {
    val passAndPlay: Boolean get() = setting == PASS
    val squares: List<Sq> = MAIN + (0 until 4).flatMap { cells(it % 2, pins[it], flipped(it)) }

    init {
        require(setting in NAMES.indices && board.size == 480 && board.all { abs(it) in 0..6 })
        require(turn == 1 || turn == -1)
        require(winner in -1..1 && halfmove >= 0)
        require(pins.size == 4 && pins.all { it in PINS.indices } && flip in 0..15)
        require(owners.size == 4 && owners.all { it == 1 || it == -1 })
        require(squares.size == squares.toSet().size) { "attack boards overlap" }
        val live = squares.map { it.key }.toSet()
        for (i in board.indices) if (board[i] != 0) require(i in live) { "man off the boards" }
        require(board.count { it == KING } == 1 && board.count { it == -KING } == 1)
        require(castling in 0..ALL_CASTLE)
        require((epLand == -1) == (epVictim == -1))
        require(fresh.all { it in board.indices })
        if (ended && winner != 0) require(inCheck(turn))
    }

    val over: Boolean get() = ended
    val draw: Boolean get() = ended && winner == 0

    fun inCheck(): Boolean = inCheck(turn)

    fun legalMoves(): List<TriMove> {
        if (ended) return emptyList()
        return pieceMoves() + boardMoves()
    }

    fun movesFrom(sq: Sq): List<TriMove> {
        if (ended || sideOf(at(sq)) != turn) return emptyList()
        return pieceMoves().filter { fromOf(it) == sq }
    }

    fun shifts(board: Int): List<TriMove> {
        if (ended || board !in 0..3) return emptyList()
        return boardMoves().filter { (it as TriMove.Shift).board == board }
    }

    fun play(move: TriMove): TriD? {
        if (move !in legalMoves()) return null
        return perform(move)
    }

    /** Applies a move already known to be legal. */
    internal fun perform(move: TriMove): TriD = apply(move).finish()

    fun pieceAt(sq: Sq): Int = at(sq)

    fun onBoard(sq: Sq): Boolean = sq in squares

    /** Squares an attack board would cover after [move]. */
    fun landing(move: TriMove.Shift): List<Sq> = cells(move.board % 2, move.pin, move.inverted)

    fun label(board: Int): String {
        val line = if (board % 2 == 0) "Queen's board" else "King's board"
        val twins = (0 until 4).count { it % 2 == board % 2 && owners[it] == owners[board] }
        return if (twins > 1) "$line · ${PINS[pins[board]].rank}" else line
    }

    internal fun inCheck(side: Int): Boolean {
        val key = board.indexOfFirst { it == KING * side }
        if (key < 0) return true
        return attacked(Sq.of(key), -side)
    }

    private fun attacked(target: Sq, by: Int): Boolean {
        for (sq in squares) {
            val p = at(sq)
            if (sideOf(p) != by || sq == target) continue
            if (abs(p) == PAWN) {
                if (target.r - sq.r == by && abs(target.f - sq.f) == 1) return true
            } else if (canHit(sq, target, p)) return true
        }
        return false
    }

    private fun finish(): TriD {
        val next = -turn
        val placed = copy(turn = next)
        val key = placed.positionKey()
        val record = history + key
        val replies = placed.copy(history = record).legalMoves()
        val mate = replies.isEmpty() && placed.inCheck(next)
        val draw = !mate && (replies.isEmpty() || halfmove >= 100 || record.count { it == key } >= 3)
        return placed.copy(history = record, ended = mate || draw, winner = if (mate) turn else 0)
    }

    private fun positionKey(): String = listOf(
        turn, board.joinToString(","), pins.joinToString(","), flip, owners.joinToString(","),
        castling, epLand, epVictim, fresh.joinToString(","),
    ).joinToString("|")

    private fun pieceMoves(): List<TriMove> = buildList {
        for (sq in squares) {
            val piece = at(sq)
            if (sideOf(piece) != turn) continue
            addAll(pseudo(sq, piece).filter { !apply(it).inCheck(turn) })
        }
        addAll(castles())
    }

    private fun boardMoves(): List<TriMove> = buildList {
        if (board.none { abs(it) == PAWN && sideOf(it) == turn }) return@buildList
        for (i in 0 until 4) {
            if (owners[i] != turn) continue
            val men = cells(i % 2, pins[i], flipped(i)).map { at(it) }.filter { it != 0 }
            if (men.size > 1) continue
            if (men.size == 1 && (abs(men[0]) != PAWN || sideOf(men[0]) != turn)) continue
            val pin = pins[i]
            val inv = flipped(i)
            fun offer(np: Int, nf: Boolean) {
                if (np == pin && nf == inv) return
                if (!fits(i, np, nf)) return
                val move = TriMove.Shift(i, np, nf)
                if (!apply(move).inCheck(turn)) add(move)
            }
            offer(pin, !inv)
            for (d in intArrayOf(-2, -1, 1, 2)) {
                val np = pin + d
                if (np !in PINS.indices) continue
                offer(np, inv)
                offer(np, !inv)
            }
        }
    }

    private fun fits(index: Int, pin: Int, inverted: Boolean): Boolean {
        val covered = (0 until 4).flatMap { i ->
            cells(i % 2, if (i == index) pin else pins[i], if (i == index) inverted else flipped(i))
        }
        if (covered.size != covered.toSet().size) return false
        val leaving = cells(index % 2, pins[index], flipped(index)).map { it.key }.toSet()
        val arriving = cells(index % 2, pin, inverted)
        return arriving.all { at(it) == 0 || it.key in leaving }
    }

    private fun pseudo(from: Sq, piece: Int): List<TriMove> {
        val kind = abs(piece)
        val side = sideOf(piece)
        val out = mutableListOf<TriMove>()
        for (to in squares) {
            if (to == from) continue
            if (sideOf(at(to)) == side) continue
            if (kind == PAWN) {
                if (pawnQuiet(from, to, side) || pawnCapture(from, to, side)) out += TriMove.Slide(from, to)
            } else if (canHit(from, to, piece)) out += TriMove.Slide(from, to)
        }
        if (kind == PAWN && epLand >= 0) {
            val land = Sq.of(epLand)
            val victim = Sq.of(epVictim)
            if (land in squares && at(land) == 0 && at(victim) == -side * PAWN &&
                land.r - from.r == side && abs(land.f - from.f) == 1
            ) out += TriMove.Passant(from, land, victim)
        }
        return out
    }

    private fun castles(): List<TriMove> = buildList {
        for (kingSide in listOf(true, false)) {
            val bit = castleBit(turn, kingSide)
            if (castling and bit == 0) continue
            val king = homeKing(turn)
            val rook = if (kingSide) homeRook(turn) else homeQueenRook(turn)
            if (at(king) != KING * turn || at(rook) != ROOK * turn) continue
            if (!kingSide && at(homeQueen(turn)) != 0) continue
            if (attacked(king, -turn)) continue
            val move = TriMove.Castle(kingSide)
            if (!apply(move).inCheck(turn)) add(move)
        }
    }

    /** Geometry only. The man on [to] does not block; men between do. */
    private fun canHit(from: Sq, to: Sq, piece: Int): Boolean {
        val df = to.f - from.f
        val dr = to.r - from.r
        if (df == 0 && dr == 0) return false
        val kind = abs(piece)
        val adf = abs(df); val adr = abs(dr)
        val knight = adf * adr == 2 && adf + adr == 3
        val king = max(adf, adr) == 1
        val rook = df == 0 || dr == 0
        val bishop = adf == adr
        val shape = when (kind) {
            KNIGHT -> knight
            KING -> king
            ROOK -> rook
            BISHOP -> bishop
            QUEEN -> rook || bishop
            else -> false
        }
        if (!shape) return false
        if (kind == KNIGHT) return true
        val ceiling = max(from.z, to.z)
        if (clear(from, to, ceiling)) return true
        if (ceiling in MAIN_LEVELS && squares.any { it.z == ceiling + 1 }) return clear(from, to, ceiling + 1)
        return false
    }

    private fun pawnQuiet(from: Sq, to: Sq, side: Int): Boolean {
        if (to.f != from.f || at(to) != 0) return false
        val dr = to.r - from.r
        if (dr == side) return true
        if (dr != 2 * side || from.key !in fresh) return false
        val mid = highest(from.f, from.r + side, max(from.z, to.z)) ?: return false
        return at(mid) == 0
    }

    private fun pawnCapture(from: Sq, to: Sq, side: Int): Boolean =
        to.r - from.r == side && abs(to.f - from.f) == 1 && sideOf(at(to)) == -side

    private fun clear(from: Sq, to: Sq, ceiling: Int): Boolean {
        val df = to.f - from.f
        val dr = to.r - from.r
        val steps = max(abs(df), abs(dr))
        if (steps <= 1) return true
        val sf = df / steps
        val sr = dr / steps
        if (sf * steps != df || sr * steps != dr) return false
        for (i in 1 until steps) {
            val mid = highest(from.f + sf * i, from.r + sr * i, ceiling) ?: return false
            if (at(mid) != 0) return false
        }
        return true
    }

    private fun highest(f: Int, r: Int, ceiling: Int): Sq? {
        if (f !in 0..5 || r !in 0..9) return null
        var best: Sq? = null
        for (sq in squares) {
            if (sq.f == f && sq.r == r && sq.z <= ceiling && (best == null || sq.z > best.z)) best = sq
        }
        return best
    }

    private fun apply(move: TriMove): TriD = when (move) {
        is TriMove.Slide -> applySlide(move)
        is TriMove.Passant -> applyPassant(move)
        is TriMove.Castle -> applyCastle(move)
        is TriMove.Shift -> applyShift(move)
    }

    private fun applySlide(move: TriMove.Slide): TriD {
        val next = board.toMutableList()
        val piece = next[move.from.key]
        val captured = next[move.to.key]
        next[move.from.key] = 0
        next[move.to.key] = crowned(piece, move.to)
        val owners2 = owners.toMutableList()
        if (captured != 0) transfer(move.to, sideOf(captured), next, owners2)
        val double = abs(piece) == PAWN && abs(move.to.r - move.from.r) == 2 && move.to.f == move.from.f
        val mid = if (double) highest(move.from.f, move.from.r + sideOf(piece), max(move.from.z, move.to.z)) else null
        return copy(
            board = next,
            owners = owners2,
            fresh = fresh.filter { it != move.from.key && it != move.to.key },
            castling = rights(next),
            epLand = mid?.key ?: -1,
            epVictim = if (mid != null) move.to.key else -1,
            lastFrom = move.from.key,
            lastTo = move.to.key,
            halfmove = if (abs(piece) == PAWN || captured != 0) 0 else halfmove + 1,
        )
    }

    private fun applyPassant(move: TriMove.Passant): TriD {
        val next = board.toMutableList()
        val piece = next[move.from.key]
        next[move.from.key] = 0
        next[move.victim.key] = 0
        next[move.land.key] = crowned(piece, move.land)
        val owners2 = owners.toMutableList()
        transfer(move.victim, -sideOf(piece), next, owners2)
        return copy(
            board = next,
            owners = owners2,
            fresh = fresh.filter { it != move.from.key && it != move.victim.key },
            castling = rights(next),
            epLand = -1,
            epVictim = -1,
            lastFrom = move.from.key,
            lastTo = move.land.key,
            halfmove = 0,
        )
    }

    private fun applyCastle(move: TriMove.Castle): TriD {
        val next = board.toMutableList()
        val king = homeKing(turn)
        val rook = if (move.kingSide) homeRook(turn) else homeQueenRook(turn)
        val kingTo = if (move.kingSide) rook else homeQueen(turn)
        val rookTo = king
        next[king.key] = 0
        next[rook.key] = 0
        next[kingTo.key] = KING * turn
        next[rookTo.key] = ROOK * turn
        return copy(
            board = next,
            castling = rights(next),
            epLand = -1,
            epVictim = -1,
            lastFrom = king.key,
            lastTo = kingTo.key,
            halfmove = halfmove + 1,
        )
    }

    private fun applyShift(move: TriMove.Shift): TriD {
        val next = board.toMutableList()
        val track = move.board % 2
        val oldPin = pins[move.board]
        val oldFlip = flipped(move.board)
        var pawn = false
        val carried = mutableListOf<Pair<Pair<Int, Int>, Int>>()
        for (u in 0..1) for (v in 0..1) {
            val sq = spot(track, oldPin, oldFlip, u, v)
            val p = next[sq.key]
            if (p != 0) {
                carried += (u to v) to p
                next[sq.key] = 0
                if (abs(p) == PAWN) pawn = true
            }
        }
        for ((uv, p) in carried) {
            val dest = spot(track, move.pin, move.inverted, uv.first, uv.second)
            next[dest.key] = crowned(p, dest)
        }
        val pins2 = pins.toMutableList()
        pins2[move.board] = move.pin
        val bit = 1 shl move.board
        val flip2 = if (move.inverted) flip or bit else flip and bit.inv()
        val left = cells(track, oldPin, oldFlip).map { it.key }.toSet()
        val from = cells(track, oldPin, oldFlip).first()
        val to = cells(track, move.pin, move.inverted).first()
        return copy(
            board = next,
            pins = pins2,
            flip = flip2,
            fresh = fresh.filter { it !in left },
            castling = rights(next),
            epLand = -1,
            epVictim = -1,
            lastFrom = from.key,
            lastTo = to.key,
            halfmove = if (pawn) 0 else halfmove + 1,
        )
    }

    private fun transfer(victim: Sq, victimSide: Int, next: List<Int>, owners2: MutableList<Int>) {
        for (i in 0 until 4) {
            if (owners2[i] != victimSide) continue
            val cov = cells(i % 2, pins[i], flipped(i))
            if (cov.none { it == victim }) continue
            if (cov.none { sideOf(next[it.key]) == victimSide }) owners2[i] = -victimSide
        }
    }

    private fun crowned(piece: Int, to: Sq): Int {
        if (abs(piece) != PAWN) return piece
        val side = sideOf(piece)
        return if ((side == 1 && to.r >= 8) || (side == -1 && to.r <= 1)) QUEEN * side else piece
    }

    private fun rights(next: List<Int>): Int {
        var r = castling
        if (next[WK_HOME.key] != KING) r = r and (BK or BQ)
        if (next[WR_HOME.key] != ROOK) r = r and (ALL_CASTLE xor WK)
        if (next[WQR_HOME.key] != ROOK) r = r and (ALL_CASTLE xor WQ)
        if (next[BK_HOME.key] != -KING) r = r and (WK or WQ)
        if (next[BR_HOME.key] != -ROOK) r = r and (ALL_CASTLE xor BK)
        if (next[BQR_HOME.key] != -ROOK) r = r and (ALL_CASTLE xor BQ)
        return r
    }

    private fun flipped(i: Int) = flip and (1 shl i) != 0
    private fun at(sq: Sq) = if (sq.key in board.indices) board[sq.key] else 0

    private fun fromOf(move: TriMove): Sq? = when (move) {
        is TriMove.Slide -> move.from
        is TriMove.Passant -> move.from
        is TriMove.Castle -> homeKing(turn)
        is TriMove.Shift -> null
    }

    companion object {
        const val PAWN = 1
        const val KNIGHT = 2
        const val BISHOP = 3
        const val ROOK = 4
        const val QUEEN = 5
        const val KING = 6
        const val PASS = 4
        const val WK = 1
        const val WQ = 2
        const val BK = 4
        const val BQ = 8
        const val ALL_CASTLE = WK or WQ or BK or BQ
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert", "Two players")

        val WK_HOME = Sq(4, 0, 3)
        val WR_HOME = Sq(5, 0, 3)
        val WQ_HOME = Sq(1, 0, 3)
        val WQR_HOME = Sq(0, 0, 3)
        val BK_HOME = Sq(4, 9, 7)
        val BR_HOME = Sq(5, 9, 7)
        val BQ_HOME = Sq(1, 9, 7)
        val BQR_HOME = Sq(0, 9, 7)

        /** Posts along one attack-board file, from White's home to Black's. */
        val PINS = listOf(
            Pin(1, 2), Pin(3, 4), Pin(4, 2), Pin(5, 6), Pin(6, 4), Pin(8, 6),
        )
        private val MAIN_LEVELS = intArrayOf(2, 4, 6)
        private val MAIN: List<Sq> = buildList {
            for (f in 1..4) {
                for (r in 1..4) add(Sq(f, r, 2))
                for (r in 3..6) add(Sq(f, r, 4))
                for (r in 5..8) add(Sq(f, r, 6))
            }
        }

        val OPENING: List<Int> = MutableList(480) { 0 }.apply {
            fun put(sq: Sq, p: Int) { this[sq.key] = p }
            put(Sq(1, 1, 2), KNIGHT); put(Sq(2, 1, 2), BISHOP); put(Sq(3, 1, 2), BISHOP); put(Sq(4, 1, 2), KNIGHT)
            put(Sq(1, 2, 2), PAWN); put(Sq(2, 2, 2), PAWN); put(Sq(3, 2, 2), PAWN); put(Sq(4, 2, 2), PAWN)
            put(Sq(0, 0, 3), ROOK); put(Sq(1, 0, 3), QUEEN); put(Sq(0, 1, 3), PAWN); put(Sq(1, 1, 3), PAWN)
            put(WK_HOME, KING); put(WR_HOME, ROOK); put(Sq(4, 1, 3), PAWN); put(Sq(5, 1, 3), PAWN)
            put(Sq(1, 8, 6), -KNIGHT); put(Sq(2, 8, 6), -BISHOP); put(Sq(3, 8, 6), -BISHOP); put(Sq(4, 8, 6), -KNIGHT)
            put(Sq(1, 7, 6), -PAWN); put(Sq(2, 7, 6), -PAWN); put(Sq(3, 7, 6), -PAWN); put(Sq(4, 7, 6), -PAWN)
            put(Sq(0, 9, 7), -ROOK); put(Sq(1, 9, 7), -QUEEN); put(Sq(0, 8, 7), -PAWN); put(Sq(1, 8, 7), -PAWN)
            put(BK_HOME, -KING); put(BR_HOME, -ROOK); put(Sq(4, 8, 7), -PAWN); put(Sq(5, 8, 7), -PAWN)
        }

        val FRESH: List<Int> = OPENING.indices.filter { abs(OPENING[it]) == PAWN }

        fun start(seed: Long, setting: Int): TriD {
            val opening = TriD(setting, seed)
            return opening.copy(history = listOf(opening.copy(turn = opening.turn).positionKey()))
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

        fun name(sq: Sq) = "${'a' + sq.f}${sq.r}(${sq.z})"

        private fun homeKing(side: Int) = if (side == 1) WK_HOME else BK_HOME
        private fun homeRook(side: Int) = if (side == 1) WR_HOME else BR_HOME
        private fun homeQueen(side: Int) = if (side == 1) WQ_HOME else BQ_HOME
        private fun homeQueenRook(side: Int) = if (side == 1) WQR_HOME else BQR_HOME
        private fun castleBit(side: Int, kingSide: Boolean) = when {
            side == 1 && kingSide -> WK
            side == 1 -> WQ
            kingSide -> BK
            else -> BQ
        }

        private fun cells(track: Int, pin: Int, inverted: Boolean): List<Sq> =
            (0..1).flatMap { u -> (0..1).map { v -> spot(track, pin, inverted, u, v) } }

        private fun spot(track: Int, pin: Int, inverted: Boolean, u: Int, v: Int): Sq {
            val post = PINS[pin]
            val file = if (track == 0) 1 else 4
            val hang = if (track == 0) file - 1 else file + 1
            val dr = if (post.rank <= 4) -1 else 1
            return Sq(if (u == 0) file else hang, if (v == 0) post.rank else post.rank + dr, post.level + if (inverted) -1 else 1)
        }
    }

    data class Sq(val f: Int, val r: Int, val z: Int) {
        val key: Int get() = f + 6 * r + 60 * z
        companion object {
            fun of(key: Int) = Sq(key % 6, (key / 6) % 10, key / 60)
        }
    }

    data class Pin(val rank: Int, val level: Int)
}

sealed class TriMove {
    data class Slide(val from: TriD.Sq, val to: TriD.Sq) : TriMove()
    data class Passant(val from: TriD.Sq, val land: TriD.Sq, val victim: TriD.Sq) : TriMove()
    data class Castle(val kingSide: Boolean) : TriMove()
    data class Shift(val board: Int, val pin: Int, val inverted: Boolean) : TriMove()
}

private fun sideOf(piece: Int) = when {
    piece > 0 -> 1
    piece < 0 -> -1
    else -> 0
}

object TriDAi {
    private val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 20_000)
    private val BUDGET = intArrayOf(0, 250, 700, 1_600)

    fun choose(g: TriD): TriMove {
        checkpoint()
        val legal = g.legalMoves()
        require(legal.isNotEmpty())
        val random = Random(g.seed xor g.board.fold(0L) { n, p -> n * 31 + p } xor g.pins.fold(0L) { n, p -> n * 17 + p } xor g.turn.toLong())
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
            val next = g.perform(move)
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

    private fun one(g: TriD, move: TriMove) = standing(g.perform(move), g.turn)

    private fun standing(g: TriD, player: Int): Int {
        if (g.ended) return when (g.winner) {
            player -> 100_000
            -player -> -100_000
            else -> 0
        }
        var score = 0
        for (i in g.board.indices) {
            val p = g.board[i]
            if (p == 0) continue
            val v = VALUE[abs(p)]
            score += if (sideOf(p) == player) v else -v
        }
        for (i in 0 until 4) if (g.owners[i] == player) score += 15 else score -= 15
        return score
    }

    private fun score(g: TriD, player: Int, depth: Int, alpha0: Int, beta0: Int, nodes: IntArray, budget: Int): Int {
        checkpoint()
        if (++nodes[0] >= budget || g.ended || depth == 0) return standing(g, player)
        val moves = g.legalMoves()
        if (moves.isEmpty()) return standing(g, player)
        val mine = g.turn == player
        var alpha = alpha0
        var beta = beta0
        var best = if (mine) Int.MIN_VALUE / 4 else Int.MAX_VALUE / 4
        for (move in moves.sortedByDescending { one(g, it) }) {
            val next = g.perform(move)
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

object TriDCodec {
    fun encode(g: TriD) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.board.joinToString(","),
        g.turn.toString(),
        g.pins.joinToString(","),
        g.flip.toString(),
        g.owners.joinToString(","),
        g.fresh.joinToString(","),
        g.castling.toString(),
        g.epLand.toString(),
        g.epVictim.toString(),
        g.lastFrom.toString(),
        g.lastTo.toString(),
        g.winner.toString(),
        if (g.ended) "1" else "0",
        g.halfmove.toString(),
        g.history.joinToString(";"),
    ).joinToString("\n")

    fun decode(text: String): TriD? = try {
        val lines = text.split('\n')
        require(lines.size == 18 && lines[0] == "1")
        TriD(
            setting = lines[1].toInt(),
            seed = lines[2].toLong(),
            board = lines[3].split(',').map { it.toInt() },
            turn = lines[4].toInt(),
            pins = lines[5].split(',').map { it.toInt() },
            flip = lines[6].toInt(),
            owners = lines[7].split(',').map { it.toInt() },
            fresh = if (lines[8].isEmpty()) emptyList() else lines[8].split(',').map { it.toInt() },
            castling = lines[9].toInt(),
            epLand = lines[10].toInt(),
            epVictim = lines[11].toInt(),
            lastFrom = lines[12].toInt(),
            lastTo = lines[13].toInt(),
            winner = lines[14].toInt(),
            ended = lines[15] == "1",
            halfmove = lines[16].toInt(),
            history = if (lines[17].isEmpty()) emptyList() else lines[17].split(";"),
        )
    } catch (_: IllegalArgumentException) { null }
}
