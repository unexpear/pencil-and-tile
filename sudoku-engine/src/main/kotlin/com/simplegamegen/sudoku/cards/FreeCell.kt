package com.simplegamegen.sudoku.cards

import kotlin.random.Random

/** Where a FreeCell card sits: free cells 0–3, tableau 0–7, foundations 0–3. */
enum class FreeCellPile { FREE, TABLEAU, FOUNDATION }

/**
 * One legal action: move [count] cards from [from]/[fromIndex] onto [to]/[toIndex].
 * Tableau moves of more than one card need a built alternating run and enough helpers (supermove).
 */
data class FreeCellMove(
    val from: FreeCellPile,
    val fromIndex: Int,
    val to: FreeCellPile,
    val toIndex: Int,
    val count: Int = 1,
)

fun isRed(card: Int): Boolean = suitOf(card) == 1 || suitOf(card) == 2

/**
 * Classic FreeCell: 52 cards, 8 tableau columns (4×7 and 4×6, all face up),
 * 4 free cells, 4 foundations built Ace→King by suit. Seeded deals; not every deal is winnable.
 */
data class FreeCellGame(
    val seed: Long,
    val free: List<Int?>,
    val foundations: List<Int?>,
    val columns: List<List<Int>>,
    val moves: Int = 0,
) {
    init {
        require(free.size == 4 && foundations.size == 4 && columns.size == 8 && moves >= 0)
        val cards = free.filterNotNull() + foundations.filterNotNull().flatMap { top ->
            val s = suitOf(top)
            (1..rankOf(top)).map { r -> s * 13 + r - 1 }
        } + columns.flatten()
        require(cards.size == 52 && cards.toSet().size == 52 && cards.all { it in 0..51 })
        foundations.forEach { top ->
            if (top != null) require(rankOf(top) in 1..13)
        }
    }

    val won: Boolean get() = foundations.all { it != null && rankOf(it) == 13 }

    fun emptyFrees(): Int = free.count { it == null }
    fun emptyColumns(): Int = columns.count { it.isEmpty() }

    /** Longest movable run at the bottom of [col]: descending by one, alternating color. */
    fun runLength(col: Int): Int {
        val c = columns[col]
        if (c.isEmpty()) return 0
        var n = 1
        while (n < c.size) {
            val upper = c[c.size - n - 1]
            val lower = c[c.size - n]
            if (isRed(upper) == isRed(lower) || rankOf(upper) != rankOf(lower) + 1) break
            n++
        }
        return n
    }

    /**
     * How many cards may move as a group when [toEmptyColumn] is the destination.
     * Standard FreeCell helper formula: (1 + free cells) × 2^(empty columns),
     * excluding the destination column when it is empty.
     */
    fun moveLimit(toEmptyColumn: Boolean): Int {
        val helpers = if (toEmptyColumn) emptyColumns() - 1 else emptyColumns()
        return (1 + emptyFrees()) * (1 shl helpers.coerceAtLeast(0))
    }

    fun cardAt(from: FreeCellPile, index: Int): Int? = when (from) {
        FreeCellPile.FREE -> free.getOrNull(index)
        FreeCellPile.TABLEAU -> columns.getOrNull(index)?.lastOrNull()
        FreeCellPile.FOUNDATION -> foundations.getOrNull(index)
    }

    fun canMove(m: FreeCellMove): Boolean {
        if (won) return false
        if (m.count < 1) return false
        when (m.from) {
            FreeCellPile.FREE -> {
                if (m.fromIndex !in 0..3 || m.count != 1 || free[m.fromIndex] == null) return false
            }
            FreeCellPile.TABLEAU -> {
                if (m.fromIndex !in 0..7 || m.count > runLength(m.fromIndex)) return false
            }
            FreeCellPile.FOUNDATION -> return false
        }
        val moving = cardsMoving(m) ?: return false
        val first = moving.first()
        return when (m.to) {
            FreeCellPile.FREE -> m.toIndex in 0..3 && m.count == 1 && free[m.toIndex] == null &&
                !(m.from == FreeCellPile.FREE && m.fromIndex == m.toIndex)
            FreeCellPile.FOUNDATION -> m.toIndex in 0..3 && m.count == 1 && canFoundation(first, m.toIndex)
            FreeCellPile.TABLEAU -> m.toIndex in 0..7 &&
                !(m.from == FreeCellPile.TABLEAU && m.fromIndex == m.toIndex) &&
                canTableau(first, m.toIndex, m.count)
        }
    }

    private fun canFoundation(card: Int, slot: Int): Boolean {
        val top = foundations[slot]
        return if (top == null) rankOf(card) == 1
        else suitOf(card) == suitOf(top) && rankOf(card) == rankOf(top) + 1
    }

    private fun canTableau(first: Int, col: Int, count: Int): Boolean {
        val target = columns[col]
        val empty = target.isEmpty()
        if (count > moveLimit(empty)) return false
        if (empty) return true
        val top = target.last()
        return isRed(first) != isRed(top) && rankOf(first) == rankOf(top) - 1
    }

    private fun cardsMoving(m: FreeCellMove): List<Int>? = when (m.from) {
        FreeCellPile.FREE -> free[m.fromIndex]?.let { listOf(it) }
        FreeCellPile.TABLEAU -> {
            val c = columns[m.fromIndex]
            if (m.count > c.size) null else c.takeLast(m.count)
        }
        FreeCellPile.FOUNDATION -> null
    }

    fun move(m: FreeCellMove): FreeCellGame? {
        if (!canMove(m)) return null
        val moving = cardsMoving(m)!!
        val nextFree = free.toMutableList()
        val nextFound = foundations.toMutableList()
        val nextCols = columns.map { it.toMutableList() }.toMutableList()
        when (m.from) {
            FreeCellPile.FREE -> nextFree[m.fromIndex] = null
            FreeCellPile.TABLEAU -> repeat(m.count) { nextCols[m.fromIndex].removeAt(nextCols[m.fromIndex].lastIndex) }
            FreeCellPile.FOUNDATION -> error("unreachable")
        }
        when (m.to) {
            FreeCellPile.FREE -> nextFree[m.toIndex] = moving.single()
            FreeCellPile.FOUNDATION -> nextFound[m.toIndex] = moving.single()
            FreeCellPile.TABLEAU -> nextCols[m.toIndex].addAll(moving)
        }
        return copy(free = nextFree, foundations = nextFound, columns = nextCols, moves = moves + 1)
    }

    fun legalMoves(): List<FreeCellMove> {
        val out = mutableListOf<FreeCellMove>()
        for (i in 0..3) if (free[i] != null) {
            for (t in 0..3) out += FreeCellMove(FreeCellPile.FREE, i, FreeCellPile.FREE, t)
            for (t in 0..3) out += FreeCellMove(FreeCellPile.FREE, i, FreeCellPile.FOUNDATION, t)
            for (t in 0..7) out += FreeCellMove(FreeCellPile.FREE, i, FreeCellPile.TABLEAU, t)
        }
        for (c in 0..7) {
            val n = runLength(c)
            for (count in 1..n) {
                for (t in 0..3) if (count == 1) {
                    out += FreeCellMove(FreeCellPile.TABLEAU, c, FreeCellPile.FREE, t, 1)
                    out += FreeCellMove(FreeCellPile.TABLEAU, c, FreeCellPile.FOUNDATION, t, 1)
                }
                for (t in 0..7) out += FreeCellMove(FreeCellPile.TABLEAU, c, FreeCellPile.TABLEAU, t, count)
            }
        }
        return out.filter(::canMove)
    }

    /** Prefers foundation builds, then freeing a column, then any legal move. */
    fun hint(): FreeCellMove? = legalMoves().maxByOrNull { m ->
        var score = 0
        when (m.to) {
            FreeCellPile.FOUNDATION -> score += 100
            FreeCellPile.FREE -> score += 5
            FreeCellPile.TABLEAU -> {
                val dest = columns[m.toIndex]
                if (dest.isEmpty()) score += if (m.from == FreeCellPile.TABLEAU && m.count == columns[m.fromIndex].size) -20 else 10
                else score += 20
            }
        }
        if (m.from == FreeCellPile.TABLEAU && m.count == columns[m.fromIndex].size) score += 15
        score + m.count
    }

    companion object {
        fun deal(seed: Long): FreeCellGame {
            val deck = (0..51).shuffled(Random(seed))
            var at = 0
            val cols = (0 until 8).map { i ->
                val n = if (i < 4) 7 else 6
                deck.subList(at, at + n).toList().also { at += n }
            }
            return FreeCellGame(seed, List(4) { null }, List(4) { null }, cols)
        }
    }
}

object FreeCellCodec {
    fun encode(g: FreeCellGame) = listOf(
        "1",
        g.seed.toString(),
        g.free.joinToString(",") { it?.toString() ?: "-" },
        g.foundations.joinToString(",") { it?.toString() ?: "-" },
        g.columns.joinToString(";") { it.joinToString(",") },
        g.moves.toString(),
    ).joinToString("\n")

    fun decode(text: String): FreeCellGame? = try {
        val l = text.split('\n')
        require(l.size == 6 && l[0] == "1")
        fun slots(s: String) = s.split(',').map { if (it == "-") null else it.toInt() }
        fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map(String::toInt)
        FreeCellGame(l[1].toLong(), slots(l[2]), slots(l[3]), l[4].split(';').map(::ints), l[5].toInt())
    } catch (_: IllegalArgumentException) {
        null
    }
}
