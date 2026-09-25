package com.simplegamegen.sudoku.constraints

import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.SandwichClues

/**
 * Sandwich (outside) clues: in a finished line, the digits strictly between
 * 1 and [Board.size] must sum to the clue. Only checkable once a line is
 * complete, so this constraint is quiet during play and exact at the end.
 * A clue of 0 means "no clue".
 */
class SandwichConstraint(
    val size: Int,
    val clues: SandwichClues,
) : Constraint {

    init {
        listOf(clues.top, clues.bottom, clues.left, clues.right).forEach { side ->
            require(side.size == size) { "sandwich clue sides must match board size" }
            require(side.all { it in 0..size * size }) { "sandwich clue out of range" }
        }
    }

    /** Lines (cell lists) carrying a non-zero clue. */
    fun cluedLines(): List<Pair<List<Int>, Int>> {
        val lines = mutableListOf<Pair<List<Int>, Int>>()
        for (c in 0 until size) {
            val col = List(size) { r -> r * size + c }
            if (clues.top[c] != 0) lines.add(col to clues.top[c])
            if (clues.bottom[c] != 0) lines.add(col to clues.bottom[c])
        }
        for (r in 0 until size) {
            val row = List(size) { c -> r * size + c }
            if (clues.left[r] != 0) lines.add(row to clues.left[r])
            if (clues.right[r] != 0) lines.add(row to clues.right[r])
        }
        return lines
    }

    override fun violates(board: Board, index: Int, value: Int): Boolean {
        // Only a line-completing placement can break (or prove) a clue.
        val row = index / size
        val col = index % size
        val rowCells = List(size) { c -> if (c == col) value else board.cells[row * size + c] }
        if (rowCells.all { it != 0 }) {
            if (clues.left[row] != 0 && crustSum(rowCells) != clues.left[row]) return true
            if (clues.right[row] != 0 && crustSum(rowCells) != clues.right[row]) return true
        }
        val colCells = List(size) { r -> if (r == row) value else board.cells[r * size + col] }
        if (colCells.all { it != 0 }) {
            if (clues.top[col] != 0 && crustSum(colCells) != clues.top[col]) return true
            if (clues.bottom[col] != 0 && crustSum(colCells) != clues.bottom[col]) return true
        }
        return false
    }

    override fun findConflicts(board: Board): Set<Int> {
        val bad = mutableSetOf<Int>()
        for ((line, clue) in cluedLines()) {
            val values = line.map { board.cells[it] }
            if (values.all { it != 0 } && crustSum(values) != clue) {
                bad.addAll(line)
            }
        }
        return bad
    }

    companion object {
        /** Sum of digits strictly between 1 and [size] in a complete line. */
        fun crustSum(line: List<Int>): Int {
            val size = line.size
            val i1 = line.indexOf(1)
            val iMax = line.indexOf(size)
            if (i1 == -1 || iMax == -1) return -1
            val from = minOf(i1, iMax) + 1
            val to = maxOf(i1, iMax)
            return line.subList(from, to).sum()
        }
    }
}
