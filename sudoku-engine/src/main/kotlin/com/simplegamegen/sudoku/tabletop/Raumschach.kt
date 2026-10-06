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
 * passant and no castling. A White pawn that reaches rank 5 of level E, or a Black pawn that
 * reaches rank 1 of level A, promotes to a queen, rook, bishop, unicorn, or knight, as the
 * International Raumschach Federation states. This is Dawson's 5×5×5 normal form, not Maack's
 * 8×8×8 game, where a pawn may move in every direction and promotion is on ranks 1 and 8 of
 * every level.
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
        require(board.count { it == KING } <= 1 && board.count { it == -KING } <= 1)
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
        return perform(move)
    }

    /** Applies a move already known to be legal. */
    internal fun perform(move: RaumMove): Raumschach = apply(move).finish()

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
        next[move.to] = crowned(piece, move.to, move.promo)
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
            if (board[to] == 0) addAll(pawnMoves(from, to, side))
        }
        val caps = if (side == 1) WHITE_CAP else WHITE_CAP.map { D(-it.f, -it.r, -it.l) }
        for (d in caps) {
            val to = at(l + d.l, r + d.r, f + d.f) ?: continue
            if (sideOf(board[to]) == -side) addAll(pawnMoves(from, to, side))
        }
    }

    private fun pawnMoves(from: Int, to: Int, side: Int): List<RaumMove> {
        if (!promotes(side, to)) return listOf(RaumMove(from, to))
        return PROMO.map { RaumMove(from, to, it) }
    }

    private fun crowned(piece: Int, to: Int, promo: Int): Int {
        if (abs(piece) != PAWN || !promotes(sideOf(piece), to)) return piece
        val kind = if (promo in PROMO) promo else QUEEN
        return kind * sideOf(piece)
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
        private val PROMO = listOf(QUEEN, ROOK, BISHOP, UNICORN, KNIGHT)

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

data class RaumMove(val from: Int, val to: Int, val promo: Int = Raumschach.QUEEN)

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
    private val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 20_000, 330)

    /**
     * A legal move for the side about to move.
     * Easy develops and usually takes a mate or a free piece.
     * Medium always takes a mate and will not hand a piece to a cheaper attacker.
     * Hard and Expert search with a piece-square evaluation and stop after a short time cap.
     */
    fun choose(g: Raumschach): RaumMove {
        checkpoint()
        val legal = g.legalMoves()
        require(legal.isNotEmpty())
        if (legal.size == 1) return legal.first()
        val random = Random(g.seed xor g.board.fold(0L) { n, p -> n * 31 + p } xor (g.turn.toLong() shl 8) xor g.halfmove.toLong())
        if (g.passAndPlay) return legal.random(random)
        val ordered = legal.sortedByDescending { quiet(g, it) }
        val mates = ordered.take(18).filter { move -> g.perform(move).let { it.ended && it.winner == g.turn } }
        if (mates.isNotEmpty() && (g.setting > 0 || random.nextInt(100) < 85)) return mates.first()
        if (g.setting == 0) return noisy(ordered, random)
        val varied = collapse(ordered)
        // Every legal step is scored by the material it leaves. A short search then chooses
        // among the steps that do not drop a piece, so a save or a free capture is not crowded
        // out by a developing move.
        val guard = HashMap<RaumMove, Int>(varied.size)
        var bestGuard = Int.MIN_VALUE / 4
        for (move in varied) {
            val score = guarded(g, move)
            guard[move] = score
            if (score > bestGuard) bestGuard = score
        }
        if (g.setting == 1) {
            val ties = varied.filter { guard[it] == bestGuard }
            return if (ties.size == 1) ties.first() else ties.random(random)
        }
        val depth = if (g.setting == 2) 2 else 3
        val width = if (g.setting == 2) 8 else 10
        val deadline = System.nanoTime() + (if (g.setting == 2) 100L else 180L) * 1_000_000L
        val sound = varied.filter { guard[it]!! >= bestGuard - 200 }
        val use = (varied.filter { guard[it] == bestGuard } + sound).distinct().take(width)
        var pick = use.first()
        for (ply in 1..depth) {
            if (System.nanoTime() > deadline) break
            var local = pick
            var best = Int.MIN_VALUE / 4
            var a = Int.MIN_VALUE / 4
            var stopped = false
            for (move in listOf(pick) + use.filter { it != pick }) {
                if (System.nanoTime() > deadline && ply > 1) { stopped = true; break }
                val next = g.perform(move)
                val score = if (next.ended) standing(next, g.turn) else -scoreFor(next, ply - 1, -20_000, -a, deadline)
                if (System.nanoTime() > deadline && ply > 1) { stopped = true; break }
                if (score > best) { best = score; local = move }
                if (best > a) a = best
            }
            if (stopped) break
            pick = local
        }
        return pick
    }

    private fun noisy(moves: List<RaumMove>, random: Random): RaumMove {
        val weights = intArrayOf(5, 2, 1)
        val n = minOf(weights.size, moves.size)
        var total = 0
        for (i in 0 until n) total += weights[i]
        var roll = random.nextInt(total.coerceAtLeast(1))
        for (i in 0 until n) {
            roll -= weights[i]
            if (roll < 0) return moves[i]
        }
        return moves.first()
    }

    /** One move per from-to. Queen promotion sorts first, so it is the one that remains. */
    private fun collapse(moves: List<RaumMove>): List<RaumMove> {
        val seen = HashSet<Int>()
        val out = ArrayList<RaumMove>(moves.size)
        for (move in moves) if (seen.add(move.from * 125 + move.to)) out += move
        return out
    }

    /** Capture value, a step toward the enemy king, and a queen promotion, without looking at the reply. */
    private fun quiet(g: Raumschach, move: RaumMove): Int {
        val attacker = abs(g.board[move.from])
        val victim = abs(g.board[move.to])
        var score = if (victim != 0) VALUE[victim] * 8 - VALUE[attacker] else 0
        val piece = g.board[move.from]
        score += place(piece, move.to) - place(piece, move.from) / 2
        if (attacker == Raumschach.PAWN && promotes(piece, move.to)) score += VALUE[Raumschach.QUEEN]
        val king = g.board.indexOfFirst { it == Raumschach.KING * -g.turn }
        if (king >= 0) {
            val dist = abs(file(move.to) - file(king)) + abs(rank(move.to) - rank(king)) + abs(level(move.to) - level(king))
            score += (8 - dist).coerceAtLeast(0) * 2
        }
        return score
    }

    /** Material after the move, minus the most valuable piece the opponent can take at once. */
    private fun guarded(g: Raumschach, move: RaumMove): Int {
        val next = g.perform(move)
        val base = standing(next, g.turn)
        if (next.ended) return base
        var worst = 0
        for (reply in next.legalMoves()) {
            val victim = next.board[reply.to]
            if (sideOf(victim) == g.turn) worst = maxOf(worst, VALUE[abs(victim)])
        }
        return base - worst
    }

    /** Higher is better for [player]. */
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
            val v = VALUE[abs(p)] + place(p, i)
            score += if (sideOf(p) == player) v else -v
        }
        return score
    }

    private fun place(piece: Int, index: Int): Int {
        val type = abs(piece)
        val forward = if (piece > 0) rank(index) + level(index) else (4 - rank(index)) + (4 - level(index))
        val center = 6 - (abs(file(index) - 2) + abs(rank(index) - 2) + abs(level(index) - 2))
        return when (type) {
            Raumschach.PAWN -> forward * 4
            Raumschach.KNIGHT, Raumschach.UNICORN -> center * 2
            Raumschach.BISHOP -> center
            Raumschach.ROOK -> if (forward >= 4) 4 else 0
            Raumschach.QUEEN -> center
            Raumschach.KING -> if (forward <= 2) 8 else -forward
            else -> 0
        }
    }

    private fun promotes(piece: Int, to: Int): Boolean {
        if (abs(piece) != Raumschach.PAWN) return false
        return if (piece > 0) level(to) == 4 && rank(to) == 4 else level(to) == 0 && rank(to) == 0
    }

    private fun scoreFor(g: Raumschach, depth: Int, alpha0: Int, beta0: Int, deadline: Long): Int {
        checkpoint()
        if (System.nanoTime() > deadline || g.ended || depth <= 0) return standing(g, g.turn)
        val moves = collapse(g.legalMoves().sortedByDescending { quiet(g, it) }).take(8)
        if (moves.isEmpty()) return standing(g, g.turn)
        var alpha = alpha0
        var best = Int.MIN_VALUE / 4
        for (move in moves) {
            if (System.nanoTime() > deadline) return best
            val next = g.perform(move)
            val score = if (next.ended) standing(next, g.turn) else -scoreFor(next, depth - 1, -beta0, -alpha, deadline)
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta0) break
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
