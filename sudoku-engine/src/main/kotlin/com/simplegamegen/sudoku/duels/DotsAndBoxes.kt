package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Dots and Boxes. Edges are numbered horizontals first ((rows + 1) × cols), then
 * verticals (rows × (cols + 1)). [owner] holds 1 (you), -1 (computer) or 0 per box.
 * Closing a box earns it and another turn. [setting] picks board size and strength together.
 */
data class DotsGame(
    val setting: Int,
    val seed: Long,
    val drawn: Set<Int> = emptySet(),
    val owner: List<Int> = List(SIZES[setting] * SIZES[setting]) { 0 },
    val turn: Int = 1,
    val last: Int? = null,
) {
    val rows: Int get() = SIZES[setting]
    val cols: Int get() = SIZES[setting]
    val horizontals: Int get() = (rows + 1) * cols
    val edgeCount: Int get() = horizontals + rows * (cols + 1)

    init {
        require(setting in SIZES.indices && owner.size == rows * cols && turn in listOf(1, -1))
        require(drawn.all { it in 0 until edgeCount } && owner.all { it in -1..1 })
        owner.indices.forEach { b -> require((owner[b] != 0) == boxEdges(b).all { it in drawn }) }
    }

    fun h(r: Int, c: Int) = r * cols + c
    fun v(r: Int, c: Int) = horizontals + r * (cols + 1) + c
    fun boxEdges(b: Int): List<Int> { val r = b / cols; val c = b % cols; return listOf(h(r, c), h(r + 1, c), v(r, c), v(r, c + 1)) }
    fun boxesOf(edge: Int): List<Int> = if (edge < horizontals) {
        val r = edge / cols; val c = edge % cols
        listOfNotNull((r - 1).takeIf { it >= 0 }?.let { it * cols + c }, r.takeIf { it < rows }?.let { it * cols + c })
    } else {
        val k = edge - horizontals; val r = k / (cols + 1); val c = k % (cols + 1)
        listOfNotNull((c - 1).takeIf { it >= 0 }?.let { r * cols + it }, c.takeIf { it < cols }?.let { r * cols + it })
    }

    val over: Boolean get() = drawn.size == edgeCount
    fun score(player: Int) = owner.count { it == player }
    val free: List<Int> get() = (0 until edgeCount).filter { it !in drawn }
    fun sides(b: Int) = boxEdges(b).count { it in drawn }

    fun draw(edge: Int): DotsGame? {
        if (over || edge !in 0 until edgeCount || edge in drawn) return null
        val now = drawn + edge
        val closed = boxesOf(edge).filter { b -> boxEdges(b).all { it in now } }
        val next = owner.toMutableList().also { o -> closed.forEach { o[it] = turn } }
        return copy(drawn = now, owner = next, turn = if (closed.isEmpty()) -turn else turn, last = edge)
    }

    companion object {
        val SIZES = listOf(3, 4, 5, 6)
        val NAMES = listOf("Easy · 3×3", "Medium · 4×4", "Hard · 5×5", "Expert · 6×6")
        fun start(seed: Long, setting: Int) = DotsGame(setting, seed)
    }
}

object DotsAi {
    /** Chooses the computer's edge. Stronger settings avoid handing over boxes and search the endgame exactly. */
    fun choose(g: DotsGame): Int {
        checkpoint()
        val random = Random(g.seed * 7919 + g.drawn.size)
        val free = g.free.shuffled(random)
        val closing = free.filter { e -> g.boxesOf(e).any { g.sides(it) == 3 } }
        if (g.setting == 0) {
            if (closing.isNotEmpty() && random.nextInt(100) < 86) return closing.first()
            val safe = free.filter { e -> g.boxesOf(e).none { g.sides(it) == 2 } }
            val pool = if (safe.isNotEmpty() && random.nextInt(100) < 70) safe else free
            return pool.first()
        }
        val exactLimit = listOf(0, 0, 12, 16)[g.setting]
        if (free.size <= exactLimit) return Endgame(g).best()
        if (closing.isNotEmpty()) return longestChain(g, closing)
        val safe = free.filter { e -> g.boxesOf(e).none { g.sides(it) == 2 } }
        if (safe.isNotEmpty()) return safe.minBy { control(g, it) }
        // Every move gives something away: give away the shortest chain.
        return free.minBy { e -> handout(g.draw(e)!!) }
    }

    /** Prefer the capture that continues into the longer chain of boxes. */
    private fun longestChain(g: DotsGame, closing: List<Int>): Int =
        closing.maxBy { edge -> handout(g.draw(edge)!!) * 10 + g.boxesOf(edge).count { g.sides(it) == 3 } }

    /**
     * Lower is better. A safe edge that touches a box of one side is a future gift,
     * so it waits until the looser edges are gone.
     */
    private fun control(g: DotsGame, edge: Int): Int {
        var score = 0
        for (box in g.boxesOf(edge)) score += if (g.sides(box) == 1) 2 else if (g.sides(box) == 0) 0 else 5
        return score
    }

    /** Boxes the opponent can grab in a row after this position. */
    private fun handout(g: DotsGame): Int {
        val taker = g.turn
        var state = g
        while (state.turn == taker) {
            val e = state.free.firstOrNull { x -> state.boxesOf(x).any { state.sides(it) == 3 } } ?: break
            state = state.draw(e)!!
        }
        return state.score(taker) - g.score(taker)
    }

    /** Exact search over the remaining edges: best net boxes for the player to move. */
    private class Endgame(val g: DotsGame) {
        val edges = g.free
        val index = edges.withIndex().associate { it.value to it.index }
        val memo = HashMap<Int, Int>()
        fun gain(mask: Int, i: Int): Int {
            val e = edges[i]
            return g.boxesOf(e).count { b -> g.boxEdges(b).all { x -> x != e && (x in g.drawn || (index[x]?.let { mask shr it and 1 == 1 } == true)) } }
        }
        fun value(mask: Int): Int {
            if (mask == (1 shl edges.size) - 1) return 0
            memo[mask]?.let { return it }
            checkpoint()
            var best = Int.MIN_VALUE
            for (i in edges.indices) {
                if (mask shr i and 1 == 1) continue
                val got = gain(mask, i)
                val rest = value(mask or (1 shl i))
                best = maxOf(best, if (got > 0) got + rest else -rest)
            }
            memo[mask] = best
            return best
        }
        fun best(): Int = edges.indices.maxBy { i -> val got = gain(0, i); val rest = value(1 shl i); if (got > 0) got + rest else -rest }.let { edges[it] }
    }
}

object DotsCodec {
    fun encode(g: DotsGame) = listOf("1", g.setting.toString(), g.seed.toString(), g.drawn.sorted().joinToString(","),
        g.owner.joinToString(","), g.turn.toString(), g.last?.toString() ?: "").joinToString("\n")
    fun decode(text: String): DotsGame? = try {
        val l = text.split('\n'); require(l.size == 7 && l[0] == "1")
        DotsGame(l[1].toInt(), l[2].toLong(), if (l[3].isEmpty()) emptySet() else l[3].split(',').map(String::toInt).toSet(),
            l[4].split(',').map(String::toInt), l[5].toInt(), l[6].toIntOrNull())
    } catch (_: IllegalArgumentException) { null }
}
