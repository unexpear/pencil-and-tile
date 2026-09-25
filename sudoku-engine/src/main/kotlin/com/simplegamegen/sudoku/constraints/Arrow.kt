package com.simplegamegen.sudoku.constraints

import com.simplegamegen.sudoku.model.ArrowShaft
import com.simplegamegen.sudoku.model.Board

/**
 * Arrows: the digits along each shaft sum to the digit in its circle.
 * Prunes partial shafts against the (filled or max-possible) circle, which
 * keeps solution counting fast.
 */
class ArrowConstraint(
    val size: Int,
    val arrows: List<ArrowShaft>,
) : Constraint {

    init {
        require(arrows.isNotEmpty()) { "need at least one arrow" }
        arrows.forEachIndexed { k, arrow ->
            require(arrow.circle in 0 until size * size) { "arrow $k circle out of bounds" }
            require(arrow.shaft.all { it in 0 until size * size }) { "arrow $k shaft out of bounds" }
            require(arrow.shaft.toSet().size == arrow.shaft.size) { "arrow $k shaft repeats a cell" }
        }
    }

    override fun violates(board: Board, index: Int, value: Int): Boolean {
        for (arrow in arrows) {
            if (index != arrow.circle && index !in arrow.shaft) continue
            val circleVal = if (index == arrow.circle) value else board.cells[arrow.circle]
            var partial = 0
            var empty = 0
            for (cell in arrow.shaft) {
                val v = if (cell == index) value else board.cells[cell]
                if (v == 0) empty++ else partial += v
            }
            val cap = if (circleVal != 0) circleVal else size
            if (partial > cap) return true
            if (circleVal != 0) {
                if (empty == 0) {
                    if (partial != circleVal) return true
                } else {
                    // Remaining shaft cells hold at least 1 each.
                    if (partial + empty > circleVal) return true
                }
            }
        }
        return false
    }

    override fun findConflicts(board: Board): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (arrow in arrows) {
            val circleVal = board.cells[arrow.circle]
            val filled = arrow.shaft.filter { board.cells[it] != 0 }
            val partial = filled.sumOf { board.cells[it] }
            if (circleVal != 0 && (partial > circleVal || (filled.size == arrow.shaft.size && partial != circleVal))) {
                bad.add(arrow.circle)
                bad.addAll(filled)
            } else if (circleVal == 0 && partial > size) {
                bad.addAll(filled)
            }
        }
        return bad
    }
}
