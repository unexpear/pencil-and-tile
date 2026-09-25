package com.simplegamegen.sudoku.constraints

import com.simplegamegen.sudoku.model.Board

/**
 * Thermometers: digits strictly increase along each bulb-to-tip path.
 * A placement breaks the rule when a filled predecessor is >= it or a
 * filled successor is <= it.
 */
class ThermoConstraint(
    val size: Int,
    val thermos: List<List<Int>>,
) : Constraint {

    init {
        require(thermos.isNotEmpty()) { "need at least one thermometer" }
        thermos.forEachIndexed { t, path ->
            require(path.size >= 2) { "thermometer $t needs at least 2 cells" }
            require(path.size <= size) { "thermometer $t longer than $size" }
            require(path.toSet().size == path.size) { "thermometer $t repeats a cell" }
            path.forEach { cell ->
                require(cell in 0 until size * size) { "thermometer $t cell $cell out of bounds" }
            }
        }
    }

    override fun violates(board: Board, index: Int, value: Int): Boolean {
        for (path in thermos) {
            val pos = path.indexOf(index)
            if (pos == -1) continue
            for (j in 0 until pos) {
                val w = board.cells[path[j]]
                if (w != 0 && w >= value) return true
            }
            for (j in pos + 1 until path.size) {
                val w = board.cells[path[j]]
                if (w != 0 && w <= value) return true
            }
        }
        return false
    }

    override fun findConflicts(board: Board): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (path in thermos) {
            for (i in path.indices) {
                val vi = board.cells[path[i]]
                if (vi == 0) continue
                for (j in i + 1 until path.size) {
                    val vj = board.cells[path[j]]
                    if (vj != 0 && vi >= vj) {
                        bad.add(path[i])
                        bad.add(path[j])
                    }
                }
            }
        }
        return bad
    }
}
