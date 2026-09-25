package com.simplegamegen.sudoku.constraints

import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.Cage

/**
 * Killer rule: no repeats inside a cage, and the cage total must stay
 * reachable — partial sums may not exceed the target, and the remaining
 * empties must be fillable with distinct digits ([minMaxSum] bounds).
 * This doubles as solver pruning, so Killer counting stays fast.
 */
class KillerConstraint(
    val size: Int,
    val cages: List<Cage>,
) : Constraint {

    private val cageOf = IntArray(size * size) { -1 }

    init {
        require(cages.isNotEmpty()) { "need at least one cage" }
        cages.forEachIndexed { id, cage ->
            cage.cells.forEach { cell ->
                require(cell in 0 until size * size) { "cage $id cell $cell out of bounds" }
                require(cageOf[cell] == -1) { "cell $cell is in multiple cages" }
                cageOf[cell] = id
            }
        }
        require(cageOf.all { it != -1 }) { "cages must cover every cell" }
        cages.forEachIndexed { id, cage ->
            val (lo, hi) = minMaxSum(cage.cells.size, emptySet(), size)
            require(cage.sum in lo..hi) {
                "cage $id sum ${cage.sum} impossible for ${cage.cells.size} cells (needs $lo..$hi)"
            }
        }
    }

    fun cageId(index: Int): Int = cageOf[index]

    /** Cage index per cell, row-major. Handy for UI + generator. */
    fun cageIndexMap(): IntArray = cageOf.copyOf()

    override fun violates(board: Board, index: Int, value: Int): Boolean {
        val cage = cages[cageOf[index]]
        val used = HashSet<Int>(cage.cells.size + 1)
        used.add(value)
        var partial = value
        var filled = 1
        for (peer in cage.cells) {
            if (peer == index) continue
            val v = board.cells[peer]
            if (v != 0) {
                if (!used.add(v)) return true // repeat (also catches value dup)
                if (v == value) return true
                partial += v
                filled++
            }
        }
        if (partial > cage.sum) return true
        val remaining = cage.cells.size - filled
        if (remaining == 0) return partial != cage.sum
        val (lo, hi) = minMaxSum(remaining, used, size)
        if (lo == Int.MAX_VALUE) return true // not enough distinct digits left
        return partial + lo > cage.sum || partial + hi < cage.sum
    }

    override fun findConflicts(board: Board): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (cage in cages) {
            val filled = cage.cells.filter { board.cells[it] != 0 }
            val values = filled.map { board.cells[it] }
            if (values.size != values.toSet().size) {
                // All cells carrying a duplicated digit.
                val dup = values.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
                filled.filter { board.cells[it] in dup }.forEach { bad.add(it) }
            }
            val partial = values.sum()
            if (partial > cage.sum) {
                bad.addAll(filled)
            } else if (filled.size == cage.cells.size && partial != cage.sum) {
                bad.addAll(cage.cells.toList())
            }
        }
        return bad
    }

    override fun namedUnits(): List<Pair<String, IntArray>> =
        // Only a full-size cage must contain every digit. Smaller cages
        // enforce distinctness, but cannot support hidden-single reasoning.
        cages.filter { it.cells.size == size }
            .map { "cage summing to ${it.sum}" to it.cells.copyOf() }

    companion object {
        /**
         * Min/max sum of [k] distinct digits in 1..[maxDigit] excluding [used].
         * Returns (MAX_VALUE, MIN_VALUE) when impossible.
         */
        fun minMaxSum(k: Int, used: Set<Int>, maxDigit: Int): Pair<Int, Int> {
            if (k <= 0) return 0 to 0
            val avail = (1..maxDigit).filter { it !in used }
            if (avail.size < k) return Int.MAX_VALUE to Int.MIN_VALUE
            return avail.take(k).sum() to avail.takeLast(k).sum()
        }
    }
}
