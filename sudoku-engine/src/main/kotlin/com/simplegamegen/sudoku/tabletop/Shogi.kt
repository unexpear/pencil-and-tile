package com.simplegamegen.sudoku.tabletop

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.math.abs
import kotlin.random.Random

/**
 * Standard shogi. You play Sente and move first. Row 0 is Gote's back rank;
 * row 8 is yours. Positive pieces are yours. A piece of 10 or more is promoted.
 * Captured pieces return to the hand unpromoted and can be dropped.
 * [setting] is the computer's strength (Easy…Expert).
 *
 * A side with no legal move loses. The same position four times is a draw, unless one
 * side gave check on every move of that repeat, in which case that side loses.
 * When both kings are in the enemy camp, the side to move may count pieces.
 * A pawn drop that checkmates is illegal.
 */
data class Shogi(
    val setting: Int,
    val seed: Long,
    val board: List<Int> = OPENING,
    val turn: Int = 1,
    val senteHand: List<Int> = emptyList(),
    val goteHand: List<Int> = emptyList(),
    val lastFrom: Int? = null,
    val lastTo: Int? = null,
    val winner: Int = 0,
    val ended: Boolean = false,
    val history: List<String> = emptyList(),
) {
    init {
        require(setting in NAMES.indices && board.size == 81)
        require(board.all { pieceOk(it) })
        require(turn == 1 || turn == -1)
        require(winner in -1..1)
        require(senteHand.all { it in 1..7 } && goteHand.all { it in 1..7 })
        require(lastFrom == null || lastFrom == -1 || lastFrom in board.indices)
        require(lastTo == null || lastTo in board.indices)
    }

    val over: Boolean get() = ended

    fun hand(side: Int) = if (side == 1) senteHand else goteHand

    fun inCheck(): Boolean = inCheck(turn)

    /** Both kings have entered the enemy camp, so the side to move may count pieces. */
    fun canImpasse(): Boolean {
        if (ended) return false
        val senteKing = board.indexOfFirst { it == KING }
        val goteKing = board.indexOfFirst { it == -KING }
        return senteKing >= 0 && goteKing >= 0 && inZone(senteKing, 1) && inZone(goteKing, -1)
    }

    /**
     * The side to move counts. A rook or bishop in that camp or in hand is 5.
     * Every other piece except the king is 1. Under 24, the declarer loses.
     * If only the other side is under 24, that side loses. If both have 24 or more, the game is a draw.
     */
    fun declareImpasse(): Shogi? {
        if (!canImpasse()) return null
        val mine = campPoints(turn)
        val theirs = campPoints(-turn)
        val result = when {
            mine < 24 -> -turn
            theirs < 24 -> turn
            else -> 0
        }
        return copy(ended = true, winner = result)
    }

    private fun campPoints(side: Int): Int {
        var points = 0
        for (index in board.indices) {
            val piece = board[index]
            if (piece.sign != side || baseOf(piece) == KING || !inZone(index, side)) continue
            points += if (baseOf(piece) == ROOK || baseOf(piece) == BISHOP) 5 else 1
        }
        for (type in hand(side)) points += if (type == ROOK || type == BISHOP) 5 else 1
        return points
    }

    fun legalMoves(): List<ShogiMove> = moves(pawnMate = true)

    fun play(move: ShogiMove): Shogi? {
        if (ended || move !in legalMoves()) return null
        return apply(move).finish()
    }

    private fun moves(pawnMate: Boolean): List<ShogiMove> {
        if (ended) return emptyList()
        val raw = boardMoves() + drops()
        return raw.filter { move ->
            val next = apply(move)
            if (next.inCheck(turn)) return@filter false
            if (!pawnMate || move.drop != PAWN) return@filter true
            val reply = next.copy(turn = -turn)
            !(reply.inCheck(-turn) && reply.moves(pawnMate = false).isEmpty())
        }
    }

    private fun finish(): Shogi {
        val nextTurn = -turn
        val placed = copy(turn = nextTurn)
        val key = placed.key()
        val checked = if (placed.inCheck(nextTurn)) "1" else "0"
        // The position before the first move counts, so returning to it three times is the fourth occurrence.
        val prior = if (history.isEmpty()) listOf("${this.key()}#${if (inCheck(turn)) "1" else "0"}") else history
        val record = prior + "$key#$checked"
        val repeats = record.count { it.substringBefore('#') == key }
        val replies = placed.moves(pawnMate = true)
        val lost = replies.isEmpty()
        val checker = if (!lost && repeats >= 4) perpetualLoser(record, key) else 0
        val stop = lost || repeats >= 4
        val result = when {
            lost -> turn
            checker != 0 -> -checker
            else -> 0
        }
        return placed.copy(history = record, ended = stop, winner = if (stop) result else 0)
    }

    /** The side that gave check on every one of its moves in the repeated cycle, or 0. */
    private fun perpetualLoser(record: List<String>, key: String): Int {
        val seen = record.indices.filter { record[it].substringBefore('#') == key }
        val start = seen[seen.size - 4]
        var senteMoves = 0
        var senteChecks = 0
        var goteMoves = 0
        var goteChecks = 0
        for (index in (start + 1)..seen.last()) {
            val entry = record[index]
            val toMove = entry.substringBefore('#').substringAfterLast('|').toInt()
            val mover = -toMove
            val gaveCheck = entry.endsWith("#1")
            if (mover == 1) {
                senteMoves++
                if (gaveCheck) senteChecks++
            } else {
                goteMoves++
                if (gaveCheck) goteChecks++
            }
        }
        val sente = senteMoves > 0 && senteChecks == senteMoves
        val gote = goteMoves > 0 && goteChecks == goteMoves
        return when {
            sente && !gote -> 1
            gote && !sente -> -1
            else -> 0
        }
    }

    private fun boardMoves(): List<ShogiMove> = buildList {
        for (from in board.indices) {
            val piece = board[from]
            if (piece.sign != turn) continue
            for (to in targets(from, piece)) addChoice(from, to, piece)
        }
    }

    private fun drops(): List<ShogiMove> = buildList {
        val mine = hand(turn)
        if (mine.isEmpty()) return@buildList
        for (type in mine.distinct()) {
            for (to in board.indices) {
                if (board[to] != 0 || !dropSquare(type, to, turn)) continue
                if (type == PAWN && unpromotedPawnOn(to % 9)) continue
                add(ShogiMove(-1, to, drop = type))
            }
        }
    }

    private fun MutableList<ShogiMove>.addChoice(from: Int, to: Int, piece: Int) {
        val base = baseOf(piece)
        val zone = inZone(from, turn) || inZone(to, turn)
        val can = !promoted(piece) && base != GOLD && base != KING && zone
        val must = mustPromote(base, to, turn)
        if (can && must) add(ShogiMove(from, to, promote = true))
        else if (can) {
            add(ShogiMove(from, to, promote = false))
            add(ShogiMove(from, to, promote = true))
        } else add(ShogiMove(from, to, promote = false))
    }

    private fun targets(from: Int, piece: Int): List<Int> {
        val side = piece.sign
        val kind = baseOf(piece)
        val row = from / 9
        val col = from % 9
        val forward = -side
        return when {
            promoted(piece) && kind in GOLD_LIKE -> steps(row, col, goldSteps(forward), side)
            promoted(piece) && kind == BISHOP -> slides(row, col, DIAGONAL, side) + steps(row, col, ORTHO, side)
            promoted(piece) && kind == ROOK -> slides(row, col, ORTHO, side) + steps(row, col, DIAGONAL, side)
            kind == PAWN -> stepIf(row, col, forward, 0, side)
            kind == LANCE -> ray(row, col, forward, 0, side)
            kind == KNIGHT -> listOf(-1, 1).mapNotNull { sideStep -> square(row + forward * 2, col + sideStep) }
                .filter { board[it].sign != side }
            kind == SILVER -> steps(row, col, silverSteps(forward), side)
            kind == GOLD -> steps(row, col, goldSteps(forward), side)
            kind == KING -> steps(row, col, KING_STEPS, side)
            kind == BISHOP -> slides(row, col, DIAGONAL, side)
            kind == ROOK -> slides(row, col, ORTHO, side)
            else -> emptyList()
        }
    }

    private fun stepIf(row: Int, col: Int, dr: Int, dc: Int, side: Int): List<Int> {
        val to = square(row + dr, col + dc) ?: return emptyList()
        return if (board[to].sign == side) emptyList() else listOf(to)
    }

    private fun steps(row: Int, col: Int, deltas: List<Pair<Int, Int>>, side: Int): List<Int> = buildList {
        for ((dr, dc) in deltas) {
            val to = square(row + dr, col + dc) ?: continue
            if (board[to].sign != side) add(to)
        }
    }

    private fun ray(row: Int, col: Int, dr: Int, dc: Int, side: Int): List<Int> = buildList {
        var r = row + dr
        var c = col + dc
        while (r in 0..8 && c in 0..8) {
            val to = r * 9 + c
            val occupant = board[to].sign
            if (occupant == side) break
            add(to)
            if (occupant != 0) break
            r += dr
            c += dc
        }
    }

    private fun slides(row: Int, col: Int, dirs: List<Pair<Int, Int>>, side: Int): List<Int> =
        dirs.flatMap { (dr, dc) -> ray(row, col, dr, dc, side) }

    private fun apply(move: ShogiMove): Shogi {
        val next = board.toMutableList()
        var sente = senteHand
        var gote = goteHand
        if (move.drop != 0) {
            if (turn == 1) sente = without(sente, move.drop) else gote = without(gote, move.drop)
            next[move.to] = move.drop * turn
        } else {
            val moving = next[move.from]
            val captured = next[move.to]
            next[move.from] = 0
            val placed = if (move.promote) moving.sign * (baseOf(moving) + PROMOTED) else moving
            next[move.to] = placed
            if (captured != 0 && baseOf(captured) != KING) {
                val gained = baseOf(captured)
                if (turn == 1) sente = sente + gained else gote = gote + gained
            }
        }
        return copy(board = next, senteHand = sente, goteHand = gote, lastFrom = move.from, lastTo = move.to)
    }

    private fun inCheck(side: Int): Boolean {
        val king = board.indexOfFirst { it == KING * side }
        if (king < 0) return true
        val enemy = -side
        return board.indices.any { from ->
            val piece = board[from]
            piece.sign == enemy && king in targets(from, piece)
        }
    }

    /** An unpromoted pawn of the side to move already stands on this file. */
    private fun unpromotedPawnOn(col: Int) = (0 until 9).any { row -> board[row * 9 + col] == PAWN * turn }

    private fun key(): String =
        board.joinToString(",") + "|" + senteHand.sorted().joinToString(",") + "|" +
            goteHand.sorted().joinToString(",") + "|$turn"

    companion object {
        const val PAWN = 1
        const val LANCE = 2
        const val KNIGHT = 3
        const val SILVER = 4
        const val GOLD = 5
        const val BISHOP = 6
        const val ROOK = 7
        const val KING = 8
        const val PROMOTED = 10
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")
        private val GOLD_LIKE = setOf(PAWN, LANCE, KNIGHT, SILVER)
        private val ORTHO = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        private val DIAGONAL = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        private val KING_STEPS = ORTHO + DIAGONAL

        val OPENING: List<Int> = List(81) { index ->
            val row = index / 9
            val col = index % 9
            when {
                row == 0 && col == 0 || row == 0 && col == 8 || row == 8 && col == 0 || row == 8 && col == 8 ->
                    if (row == 0) -LANCE else LANCE
                row == 0 && (col == 1 || col == 7) -> -KNIGHT
                row == 8 && (col == 1 || col == 7) -> KNIGHT
                row == 0 && (col == 2 || col == 6) -> -SILVER
                row == 8 && (col == 2 || col == 6) -> SILVER
                row == 0 && (col == 3 || col == 5) -> -GOLD
                row == 8 && (col == 3 || col == 5) -> GOLD
                row == 0 && col == 4 -> -KING
                row == 8 && col == 4 -> KING
                row == 1 && col == 1 -> -ROOK
                row == 1 && col == 7 -> -BISHOP
                row == 2 -> -PAWN
                row == 6 -> PAWN
                row == 7 && col == 1 -> BISHOP
                row == 7 && col == 7 -> ROOK
                else -> 0
            }
        }

        fun baseOf(piece: Int): Int {
            val n = abs(piece)
            return if (n > PROMOTED) n - PROMOTED else n
        }

        fun promoted(piece: Int) = abs(piece) > PROMOTED

        fun pieceName(piece: Int): String {
            val name = when (baseOf(piece)) {
                PAWN -> "pawn"
                LANCE -> "lance"
                KNIGHT -> "knight"
                SILVER -> "silver"
                GOLD -> "gold"
                BISHOP -> "bishop"
                ROOK -> "rook"
                else -> "king"
            }
            return if (promoted(piece)) "promoted $name" else name
        }

        fun glyph(piece: Int): String = when {
            promoted(piece) && baseOf(piece) == PAWN -> "と"
            promoted(piece) && baseOf(piece) == LANCE -> "杏"
            promoted(piece) && baseOf(piece) == KNIGHT -> "圭"
            promoted(piece) && baseOf(piece) == SILVER -> "全"
            promoted(piece) && baseOf(piece) == BISHOP -> "馬"
            promoted(piece) && baseOf(piece) == ROOK -> "龍"
            baseOf(piece) == PAWN -> "歩"
            baseOf(piece) == LANCE -> "香"
            baseOf(piece) == KNIGHT -> "桂"
            baseOf(piece) == SILVER -> "銀"
            baseOf(piece) == GOLD -> "金"
            baseOf(piece) == BISHOP -> "角"
            baseOf(piece) == ROOK -> "飛"
            piece > 0 -> "王"
            else -> "玉"
        }

        private fun pieceOk(piece: Int): Boolean {
            val n = abs(piece)
            if (n == 0) return true
            val base = if (n > PROMOTED) n - PROMOTED else n
            if (base !in 1..8) return false
            if (n > PROMOTED && (base == GOLD || base == KING)) return false
            return n == base || n == base + PROMOTED
        }

        private fun square(row: Int, col: Int) = if (row in 0..8 && col in 0..8) row * 9 + col else null

        private fun inZone(index: Int, side: Int): Boolean {
            val row = index / 9
            return if (side == 1) row <= 2 else row >= 6
        }

        private fun mustPromote(base: Int, to: Int, side: Int): Boolean {
            val row = to / 9
            val last = if (side == 1) 0 else 8
            val next = if (side == 1) 1 else 7
            return when (base) {
                PAWN, LANCE -> row == last
                KNIGHT -> row == last || row == next
                else -> false
            }
        }

        private fun dropSquare(type: Int, to: Int, side: Int): Boolean {
            val row = to / 9
            val last = if (side == 1) 0 else 8
            val next = if (side == 1) 1 else 7
            return when (type) {
                PAWN, LANCE -> row != last
                KNIGHT -> row != last && row != next
                else -> true
            }
        }

        private fun goldSteps(forward: Int) = listOf(forward to 0, forward to -1, forward to 1, 0 to -1, 0 to 1, -forward to 0)

        private fun silverSteps(forward: Int) = listOf(forward to 0, forward to -1, forward to 1, -forward to -1, -forward to 1)

        private fun without(hand: List<Int>, type: Int): List<Int> {
            val at = hand.indexOf(type)
            return hand.filterIndexed { index, _ -> index != at }
        }
    }
}

data class ShogiMove(val from: Int, val to: Int, val promote: Boolean = false, val drop: Int = 0)

object ShogiAi {
    private val VALUE = intArrayOf(0, 100, 320, 350, 450, 520, 850, 1100, 20_000)

    /**
     * A legal move for the side about to move.
     * Easy prefers a capture or a step forward, and usually takes a mate.
     * Medium always takes a mate and keeps a piece out of a cheaper reply.
     * Hard and Expert search a short list of forcing moves and stop on a time cap.
     */
    fun choose(g: Shogi): ShogiMove {
        checkpoint()
        val legal = g.legalMoves()
        require(legal.isNotEmpty())
        if (legal.size == 1) return legal.first()
        val random = Random(g.seed xor g.board.fold(0L) { n, p -> n * 31 + p } xor g.turn.toLong() xor g.senteHand.size.toLong())
        val ordered = legal.sortedByDescending { rank(g, it) }
        val mates = ordered.take(24).filter { move -> g.play(move)?.let { it.ended && it.winner == g.turn } == true }
        if (mates.isNotEmpty() && (g.setting > 0 || random.nextInt(100) < 80)) return mates.first()
        if (g.setting == 0) return noisy(ordered, random)
        if (g.setting == 1) {
            val considered = ordered.take(16)
            val scored = considered.sortedByDescending { guarded(g, it) }
            val best = guarded(g, scored.first())
            return scored.filter { guarded(g, it) >= best - 15 }.take(4).random(random)
        }
        val width = if (g.setting == 2) 8 else 10
        val depth = if (g.setting == 2) 2 else 3
        val deadline = System.nanoTime() + (if (g.setting == 2) 80L else 150L) * 1_000_000L
        val consider = ordered.take(width)
        var pick = consider.first()
        for (ply in 1..depth) {
            if (System.nanoTime() > deadline) break
            var local = pick
            var best = Int.MIN_VALUE / 4
            var stopped = false
            for (move in listOf(pick) + consider.filter { it != pick }) {
                if (System.nanoTime() > deadline && ply > 1) { stopped = true; break }
                val next = g.play(move) ?: continue
                val score = if (next.ended) standing(next, g.turn) else -search(next, ply - 1, deadline)
                if (System.nanoTime() > deadline && ply > 1) { stopped = true; break }
                if (score > best) { best = score; local = move }
            }
            if (stopped) break
            pick = local
        }
        return pick
    }

    private fun noisy(moves: List<ShogiMove>, random: Random): ShogiMove {
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

    /** Capture, promotion, and a step toward the enemy king. Drops land nearer that king. */
    private fun rank(g: Shogi, move: ShogiMove): Int {
        val king = g.board.indexOfFirst { it == Shogi.KING * -g.turn }
        val kingRow = if (king < 0) 4 else king / 9
        val kingCol = if (king < 0) 4 else king % 9
        val row = move.to / 9
        val col = move.to % 9
        var score = 12 - (kotlin.math.abs(row - kingRow) + kotlin.math.abs(col - kingCol))
        if (move.drop != 0) return score + VALUE[move.drop] / 8
        val victim = g.board[move.to]
        if (victim != 0) score += value(victim) * 8
        if (move.promote) score += 350
        val fromRow = move.from / 9
        val forward = if (g.turn == 1) fromRow - row else row - fromRow
        return score + forward * 6
    }

    private fun guarded(g: Shogi, move: ShogiMove): Int {
        val next = g.play(move) ?: return -100_000
        val base = standing(next, g.turn)
        if (next.ended) return base
        var worst = 0
        for (reply in next.legalMoves().sortedByDescending { rank(next, it) }.take(12)) {
            if (reply.to !in next.board.indices) continue
            val victim = next.board[reply.to]
            if (victim.sign == g.turn) worst = maxOf(worst, value(victim))
        }
        return base - worst
    }

    private fun standing(g: Shogi, player: Int): Int {
        if (g.ended) return when (g.winner) {
            player -> 100_000
            -player -> -100_000
            else -> 0
        }
        var score = 0
        val king = g.board.indexOfFirst { it == Shogi.KING * player }
        for (i in g.board.indices) {
            val piece = g.board[i]
            if (piece == 0) continue
            var v = value(piece)
            if (king >= 0 && piece.sign == -player && Shogi.baseOf(piece) != Shogi.KING) {
                val dist = kotlin.math.abs(i / 9 - king / 9) + kotlin.math.abs(i % 9 - king % 9)
                v += (8 - dist).coerceAtLeast(0)
            }
            score += if (piece.sign == player) v else -v
        }
        for (piece in g.hand(player)) score += VALUE[piece]
        for (piece in g.hand(-player)) score -= VALUE[piece]
        return score
    }

    /** Score from the side about to move. */
    private fun search(g: Shogi, depth: Int, deadline: Long): Int {
        checkpoint()
        if (System.nanoTime() > deadline || g.ended || depth <= 0) return standing(g, g.turn)
        val moves = g.legalMoves().sortedByDescending { rank(g, it) }.take(6)
        if (moves.isEmpty()) return standing(g, g.turn)
        var best = Int.MIN_VALUE / 4
        for (move in moves) {
            if (System.nanoTime() > deadline) return best
            val next = g.play(move) ?: continue
            val score = if (next.ended) standing(next, g.turn) else -search(next, depth - 1, deadline)
            if (score > best) best = score
        }
        return best
    }

    private fun value(piece: Int): Int {
        val base = Shogi.baseOf(piece)
        if (!Shogi.promoted(piece)) return VALUE[base]
        return when (base) {
            Shogi.PAWN, Shogi.LANCE, Shogi.KNIGHT, Shogi.SILVER -> 500
            Shogi.BISHOP -> 1200
            Shogi.ROOK -> 1500
            else -> VALUE[base]
        }
    }
}

object ShogiCodec {
    fun encode(g: Shogi) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.board.joinToString(","),
        g.turn.toString(),
        g.senteHand.joinToString(","),
        g.goteHand.joinToString(","),
        g.lastFrom?.toString() ?: "",
        g.lastTo?.toString() ?: "",
        g.winner.toString(),
        if (g.ended) "1" else "0",
        g.history.joinToString(";"),
    ).joinToString("\n")

    fun decode(text: String): Shogi? = try {
        val lines = text.split('\n')
        require(lines.size == 12 && lines[0] == "1")
        Shogi(
            setting = lines[1].toInt(),
            seed = lines[2].toLong(),
            board = lines[3].split(',').map { it.toInt() },
            turn = lines[4].toInt(),
            senteHand = splitHand(lines[5]),
            goteHand = splitHand(lines[6]),
            lastFrom = lines[7].toIntOrNull(),
            lastTo = lines[8].toIntOrNull(),
            winner = lines[9].toInt(),
            ended = lines[10] == "1",
            history = if (lines[11].isEmpty()) emptyList() else lines[11].split(";"),
        )
    } catch (_: IllegalArgumentException) { null }

    private fun splitHand(text: String) = if (text.isEmpty()) emptyList() else text.split(',').map { it.toInt() }
}

private val Int.sign: Int get() = when {
    this > 0 -> 1
    this < 0 -> -1
    else -> 0
}
