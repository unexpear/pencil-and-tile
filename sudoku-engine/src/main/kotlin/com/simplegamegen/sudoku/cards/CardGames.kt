package com.simplegamegen.sudoku.cards

import kotlin.random.Random

/** Cards use the Solitaire numbering: suit * 13 + rank - 1 (spades, hearts, diamonds, clubs). */
fun rankOf(card: Int) = card % 13 + 1
fun suitOf(card: Int) = card / 13

// ---------------- Spider ----------------

data class SpiderMove(val from: Int, val count: Int, val to: Int)

/**
 * Spider Solitaire with two decks. [suits] is 1, 2 or 4. [hidden] counts face-down
 * cards at the bottom of each column. Completed K–A runs of one suit leave the table.
 */
data class SpiderGame(
    val suits: Int,
    val seed: Long,
    val columns: List<List<Int>>,
    val hidden: List<Int>,
    val stock: List<Int>,
    val completed: List<Int> = emptyList(),
    val moves: Int = 0,
) {
    init {
        require(suits in listOf(1, 2, 4) && columns.size == 10 && hidden.size == 10)
        columns.indices.forEach { require(hidden[it] in 0..maxOf(0, columns[it].size - 1)) }
        val all = columns.flatten() + stock
        require(all.all { it in 0..51 && suitOf(it) < suits } && stock.size % 10 == 0)
        require(all.size + completed.size * 13 == 104 && completed.all { it in 0 until suits } && moves >= 0)
        // Every rank of every suit appears the right number of times.
        val each = 8 / suits
        (0 until suits).forEach { s -> (1..13).forEach { r ->
            require(all.count { suitOf(it) == s && rankOf(it) == r } + completed.count { it == s } == each)
        } }
    }

    val won: Boolean get() = completed.size == 8
    val canDeal: Boolean get() = stock.isNotEmpty() && columns.none { it.isEmpty() }

    /** Longest movable run at the bottom of [col]: descending by one, same suit, face up. */
    fun runLength(col: Int): Int {
        val c = columns[col]
        if (c.isEmpty()) return 0
        var n = 1
        while (n < c.size - hidden[col]) {
            val upper = c[c.size - n - 1]; val lower = c[c.size - n]
            if (suitOf(upper) != suitOf(lower) || rankOf(upper) != rankOf(lower) + 1) break
            n++
        }
        return n
    }

    fun canMove(m: SpiderMove): Boolean {
        if (won || m.from !in 0..9 || m.to !in 0..9 || m.from == m.to || m.count !in 1..runLength(m.from)) return false
        val first = columns[m.from][columns[m.from].size - m.count]
        val target = columns[m.to]
        return target.isEmpty() || rankOf(target.last()) == rankOf(first) + 1
    }

    fun move(m: SpiderMove): SpiderGame? {
        if (!canMove(m)) return null
        val cols = columns.map { it.toMutableList() }.toMutableList()
        val moving = cols[m.from].takeLast(m.count)
        repeat(m.count) { cols[m.from].removeAt(cols[m.from].lastIndex) }
        cols[m.to].addAll(moving)
        return settle(cols, stock, moves + 1)
    }

    fun deal(): SpiderGame? {
        if (won || !canDeal) return null
        val cols = columns.map { it.toMutableList() }.toMutableList()
        stock.take(10).forEachIndexed { i, card -> cols[i] += card }
        return settle(cols, stock.drop(10), moves + 1)
    }

    /** Removes finished runs and turns up newly exposed cards. */
    private fun settle(cols: MutableList<MutableList<Int>>, stock: List<Int>, moves: Int): SpiderGame {
        val hide = hidden.toMutableList()
        val done = completed.toMutableList()
        cols.forEachIndexed { i, c ->
            hide[i] = minOf(hide[i], maxOf(0, c.size - 1))
            val tail = c.takeLast(13)
            if (tail.size == 13 && c.size - 13 >= hide[i] && tail.indices.all { rankOf(tail[it]) == 13 - it && suitOf(tail[it]) == suitOf(tail[0]) }) {
                repeat(13) { c.removeAt(c.lastIndex) }
                done += suitOf(tail[0])
                hide[i] = minOf(hide[i], maxOf(0, c.size - 1))
            }
        }
        return copy(columns = cols, hidden = hide, stock = stock, completed = done, moves = moves)
    }

    fun legalMoves(): List<SpiderMove> = (0..9).flatMap { from -> (1..runLength(from)).flatMap { n -> (0..9).map { SpiderMove(from, n, it) } } }.filter(::canMove)

    /** A useful move: builds on the same suit, reveals a card, or fills a gap; falls back to dealing. */
    fun hint(): SpiderMove? = legalMoves().maxByOrNull { m ->
        val src = columns[m.from]; val first = src[src.size - m.count]
        val target = columns[m.to]
        var score = 0
        if (target.isNotEmpty() && suitOf(target.last()) == suitOf(first)) score += 30
        if (m.count == src.size - hidden[m.from] && hidden[m.from] > 0) score += 20
        if (target.isEmpty()) score -= if (m.count == src.size) 100 else 10
        if (m.count < runLength(m.from)) score -= 5
        score + m.count
    }?.takeIf { m -> !(columns[m.to].isEmpty() && m.count == columns[m.from].size) }

    companion object {
        fun deal(seed: Long, suits: Int): SpiderGame {
            require(suits in listOf(1, 2, 4))
            val deck = (0 until 8).flatMap { set -> (0 until 13).map { r -> (set % suits) * 13 + r } }.shuffled(Random(seed))
            var at = 0
            val cols = (0 until 10).map { i -> val n = if (i < 4) 6 else 5; deck.subList(at, at + n).toList().also { at += n } }
            return SpiderGame(suits, seed, cols, cols.map { it.size - 1 }, deck.drop(at))
        }
    }
}

object SpiderCodec {
    fun encode(g: SpiderGame) = listOf("1", g.suits.toString(), g.seed.toString(), g.columns.joinToString(";") { it.joinToString(",") },
        g.hidden.joinToString(","), g.stock.joinToString(","), g.completed.joinToString(","), g.moves.toString()).joinToString("\n")
    fun decode(text: String): SpiderGame? = try {
        val l = text.split('\n'); require(l.size == 8 && l[0] == "1")
        fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map(String::toInt)
        SpiderGame(l[1].toInt(), l[2].toLong(), l[3].split(';').map(::ints), ints(l[4]), ints(l[5]), ints(l[6]), l[7].toInt())
    } catch (_: IllegalArgumentException) { null }
}

// ---------------- Pyramid ----------------

/**
 * Pyramid Solitaire: remove pairs of exposed cards totaling 13 (Kings alone).
 * Positions 0–27 are the pyramid, row by row; [WASTE] is the top waste card.
 * [recycles] is how many more times the waste may be turned over (-1 = unlimited).
 */
data class PyramidGame(
    val seed: Long,
    val setting: Int,
    val pyramid: List<Int?>,
    val stock: List<Int>,
    val waste: List<Int> = emptyList(),
    val recycles: Int,
    val moves: Int = 0,
) {
    init {
        require(pyramid.size == 28 && setting in 0..3 && recycles >= -1 && moves >= 0)
        val cards = pyramid.filterNotNull() + stock + waste
        require(cards.all { it in 0..51 } && cards.toSet().size == cards.size)
    }

    val won: Boolean get() = pyramid.all { it == null }
    val cleared: Int get() = pyramid.count { it == null }

    fun exposed(i: Int): Boolean {
        if (i !in 0..27 || pyramid[i] == null) return false
        val (r, c) = position(i)
        return r == 6 || (pyramid[index(r + 1, c)] == null && pyramid[index(r + 1, c + 1)] == null)
    }

    fun cardAt(source: Int): Int? = when {
        source == WASTE -> waste.lastOrNull()
        exposed(source) -> pyramid[source]
        else -> null
    }

    /** Removes one King, or two exposed cards that total 13. */
    fun remove(a: Int, b: Int? = null): PyramidGame? {
        if (won) return null
        val ca = cardAt(a) ?: return null
        if (b == null) return if (rankOf(ca) == 13) take(listOf(a)) else null
        if (a == b) return null
        val cb = cardAt(b) ?: return null
        return if (rankOf(ca) + rankOf(cb) == 13) take(listOf(a, b)) else null
    }

    private fun take(sources: List<Int>): PyramidGame {
        val p = pyramid.toMutableList(); var w = waste
        sources.forEach { if (it == WASTE) w = w.dropLast(1) else p[it] = null }
        return copy(pyramid = p, waste = w, moves = moves + 1)
    }

    /** Turns one stock card, or turns the waste over when the stock is empty and a pass remains. */
    fun draw(): PyramidGame? = when {
        won -> null
        stock.isNotEmpty() -> copy(stock = stock.drop(1), waste = waste + stock.first(), moves = moves + 1)
        waste.isNotEmpty() && recycles != 0 -> copy(stock = waste, waste = emptyList(), recycles = if (recycles > 0) recycles - 1 else -1, moves = moves + 1)
        else -> null
    }

    fun pairs(): List<Pair<Int, Int?>> {
        val sources = (0..27).filter { exposed(it) } + listOfNotNull(WASTE.takeIf { waste.isNotEmpty() })
        return sources.filter { rankOf(cardAt(it)!!) == 13 }.map { it to null } +
            sources.flatMapIndexed { i, a -> sources.drop(i + 1).filter { b -> rankOf(cardAt(a)!!) + rankOf(cardAt(b)!!) == 13 }.map { a to it } }
    }

    val stuck: Boolean get() = !won && pairs().isEmpty() && draw() == null

    companion object {
        const val WASTE = 28
        val RECYCLES = listOf(-1, 2, 1, 0)
        fun index(r: Int, c: Int) = r * (r + 1) / 2 + c
        fun position(i: Int): Pair<Int, Int> { var r = 0; while (index(r + 1, 0) <= i) r++; return r to i - index(r, 0) }

        fun deal(seed: Long, setting: Int): PyramidGame {
            val deck = (0..51).shuffled(Random(seed))
            return PyramidGame(seed, setting, deck.take(28), deck.drop(28), recycles = RECYCLES[setting])
        }
    }
}

object PyramidCodec {
    fun encode(g: PyramidGame) = listOf("1", g.seed.toString(), g.setting.toString(), g.pyramid.joinToString(",") { it?.toString() ?: "-" },
        g.stock.joinToString(","), g.waste.joinToString(","), g.recycles.toString(), g.moves.toString()).joinToString("\n")
    fun decode(text: String): PyramidGame? = try {
        val l = text.split('\n'); require(l.size == 8 && l[0] == "1")
        fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map(String::toInt)
        PyramidGame(l[1].toLong(), l[2].toInt(), l[3].split(',').map { if (it == "-") null else it.toInt() }, ints(l[4]), ints(l[5]), l[6].toInt(), l[7].toInt())
    } catch (_: IllegalArgumentException) { null }
}

// ---------------- Memory ----------------

/** Concentration: [faces] holds each picture twice; [open] lists up to two unmatched face-up cards. */
data class MemoryGame(
    val setting: Int,
    val seed: Long,
    val faces: List<Int>,
    val matched: Set<Int> = emptySet(),
    val open: List<Int> = emptyList(),
    val moves: Int = 0,
) {
    init {
        require(setting in SIZES.indices && faces.size == SIZES[setting].let { it.first * it.second })
        require(faces.groupingBy { it }.eachCount().values.all { it == 2 } && faces.all { it in 0..33 })
        require(matched.all { it in faces.indices } && open.size <= 2 && open.all { it in faces.indices && it !in matched } && moves >= 0)
        require(matched.groupBy { faces[it] }.values.all { it.size == 2 })
    }

    val cols: Int get() = SIZES[setting].first
    val won: Boolean get() = matched.size == faces.size
    /** Two unmatched cards are showing and must be turned back before the next flip. */
    val mismatch: Boolean get() = open.size == 2

    fun flip(i: Int): MemoryGame {
        if (won || i !in faces.indices || i in matched || i in open) return this
        val base = if (mismatch) copy(open = emptyList()) else this
        val nowOpen = base.open + i
        if (nowOpen.size < 2) return base.copy(open = nowOpen)
        val (a, b) = nowOpen
        return if (faces[a] == faces[b]) base.copy(matched = matched + a + b, open = emptyList(), moves = moves + 1)
        else base.copy(open = nowOpen, moves = moves + 1)
    }

    fun hide(): MemoryGame = if (mismatch) copy(open = emptyList()) else this

    companion object {
        /** Columns × rows per setting. */
        val SIZES = listOf(3 to 4, 4 to 4, 4 to 5, 5 to 6)
        fun deal(seed: Long, setting: Int): MemoryGame {
            val random = Random(seed)
            val (c, r) = SIZES[setting]
            val pictures = (0..33).shuffled(random).take(c * r / 2)
            return MemoryGame(setting, seed, (pictures + pictures).shuffled(random))
        }
    }
}

object MemoryCodec {
    fun encode(g: MemoryGame) = listOf("1", g.setting.toString(), g.seed.toString(), g.faces.joinToString(","),
        g.matched.sorted().joinToString(","), g.open.joinToString(","), g.moves.toString()).joinToString("\n")
    fun decode(text: String): MemoryGame? = try {
        val l = text.split('\n'); require(l.size == 7 && l[0] == "1")
        fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map(String::toInt)
        MemoryGame(l[1].toInt(), l[2].toLong(), ints(l[3]), ints(l[4]).toSet(), ints(l[5]), l[6].toInt())
    } catch (_: IllegalArgumentException) { null }
}
