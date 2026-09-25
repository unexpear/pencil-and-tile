package com.simplegamegen.sudoku.constraints

import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.KropkiDot

/**
 * Kropki dots between orthogonally adjacent cells: white = consecutive
 * digits, black = one digit double the other. No negative constraint:
 * the absence of a dot says nothing.
 */
class KropkiConstraint(
    val size: Int,
    val dots: List<KropkiDot>,
) : Constraint {

    init {
        dots.forEach { dot ->
            require(dot.a in 0 until size * size && dot.b in 0 until size * size) {
                "kropki dot cells out of bounds"
            }
            val ra = dot.a / size
            val ca = dot.a % size
            val rb = dot.b / size
            val cb = dot.b % size
            require(kotlin.math.abs(ra - rb) + kotlin.math.abs(ca - cb) == 1) {
                "kropki dot must join orthogonally adjacent cells"
            }
        }
    }

    override fun violates(board: Board, index: Int, value: Int): Boolean {
        for (dot in dots) {
            val other = when (index) {
                dot.a -> dot.b
                dot.b -> dot.a
                else -> continue
            }
            val w = board.cells[other]
            if (w != 0 && !satisfies(value, w, dot.black)) return true
        }
        return false
    }

    override fun findConflicts(board: Board): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (dot in dots) {
            val va = board.cells[dot.a]
            val vb = board.cells[dot.b]
            if (va != 0 && vb != 0 && !satisfies(va, vb, dot.black)) {
                bad.add(dot.a)
                bad.add(dot.b)
            }
        }
        return bad
    }

    companion object {
        fun satisfies(v1: Int, v2: Int, black: Boolean): Boolean =
            if (black) {
                v1 == 2 * v2 || v2 == 2 * v1
            } else {
                kotlin.math.abs(v1 - v2) == 1
            }
    }
}
