package com.simplegamegen.sudoku.constraints

import com.simplegamegen.sudoku.model.ArrowShaft
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.Cage
import com.simplegamegen.sudoku.model.KropkiDot
import com.simplegamegen.sudoku.model.SandwichClues
import com.simplegamegen.sudoku.model.VariantType

/**
 * A Sudoku rule plugin. Variants compose by combining constraints:
 * Classic = rows/cols + boxes, X = Classic + diagonals,
 * Jigsaw = rows/cols + irregular regions (boxes replaced).
 * The solver, generator and validator stay generic over this interface.
 */
interface Constraint {
    /** True if placing [value] at [index] breaks this rule on [board]. */
    fun violates(board: Board, index: Int, value: Int): Boolean

    /** All cell indices currently involved in a violation (ignores empties). */
    fun findConflicts(board: Board): Set<Int>

    /**
     * Named units for hidden-single hunting: groups where each digit must
     * appear exactly once. Defaults to none (e.g. Thermo, Kropki, Arrow,
     * Sandwich have no such units).
     */
    fun namedUnits(): List<Pair<String, IntArray>> = emptyList()
}

/** Rows + columns. Shared by Classic, X and Jigsaw. */
class RowColumnConstraint(private val size: Int) : Constraint {

    override fun violates(board: Board, index: Int, value: Int): Boolean {
        val row = index / size
        val col = index % size
        for (c in 0 until size) {
            val i = row * size + c
            if (i != index && board.cells[i] == value) return true
        }
        for (r in 0 until size) {
            val i = r * size + col
            if (i != index && board.cells[i] == value) return true
        }
        return false
    }

    override fun findConflicts(board: Board): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (r in 0 until size) {
            collectDups(IntArray(size) { c -> r * size + c }, board, bad)
        }
        for (c in 0 until size) {
            collectDups(IntArray(size) { r -> r * size + c }, board, bad)
        }
        return bad
    }

    companion object {
        internal fun collectDups(unit: IntArray, board: Board, bad: MutableSet<Int>) {
            val seen = mutableMapOf<Int, MutableList<Int>>()
            for (i in unit) {
                val v = board.cells[i]
                if (v != 0) seen.getOrPut(v) { mutableListOf() }.add(i)
            }
            for ((_, cells) in seen) {
                if (cells.size > 1) bad.addAll(cells)
            }
        }
    }

    override fun namedUnits(): List<Pair<String, IntArray>> {
        val units = mutableListOf<Pair<String, IntArray>>()
        for (r in 0 until size) units.add("row ${r + 1}" to IntArray(size) { c -> r * size + c })
        for (c in 0 until size) units.add("column ${c + 1}" to IntArray(size) { r -> r * size + c })
        return units
    }
}

/** Classic boxes (boxRows x boxCols). Not used by Jigsaw. */
class BoxConstraint(
    private val size: Int,
    private val boxRows: Int,
    private val boxCols: Int,
) : Constraint {

    override fun violates(board: Board, index: Int, value: Int): Boolean {
        for (peer in peersOf(index)) {
            if (peer != index && board.cells[peer] == value) return true
        }
        return false
    }

    override fun findConflicts(board: Board): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (unit in allUnits()) {
            RowColumnConstraint.collectDups(unit, board, bad)
        }
        return bad
    }

    fun peersOf(index: Int): IntArray {
        val row = index / size
        val col = index % size
        val boxRow0 = (row / boxRows) * boxRows
        val boxCol0 = (col / boxCols) * boxCols
        val peers = IntArray(size - 1)
        var k = 0
        for (r in boxRow0 until boxRow0 + boxRows) {
            for (c in boxCol0 until boxCol0 + boxCols) {
                val i = r * size + c
                if (i != index) peers[k++] = i
            }
        }
        return peers
    }

    private fun allUnits(): List<IntArray> {
        val units = mutableListOf<IntArray>()
        for (br in 0 until size step boxRows) {
            for (bc in 0 until size step boxCols) {
                val unit = IntArray(size)
                var k = 0
                for (r in br until br + boxRows) {
                    for (c in bc until bc + boxCols) {
                        unit[k++] = r * size + c
                    }
                }
                units.add(unit)
            }
        }
        return units
    }

    override fun namedUnits(): List<Pair<String, IntArray>> =
        allUnits().map { "box" to it }
}

/**
 * Both main diagonals must hold 1..size (X-Sudoku).
 * Cells off both diagonals are unaffected by this constraint.
 */
class DiagonalConstraint(private val size: Int) : Constraint {

    fun onDiagonal(index: Int): Boolean {
        val row = index / size
        val col = index % size
        return row == col || row + col == size - 1
    }

    override fun violates(board: Board, index: Int, value: Int): Boolean {
        val row = index / size
        val col = index % size
        if (row == col) {
            for (k in 0 until size) {
                val i = k * size + k
                if (i != index && board.cells[i] == value) return true
            }
        }
        if (row + col == size - 1) {
            for (k in 0 until size) {
                val i = k * size + (size - 1 - k)
                if (i != index && board.cells[i] == value) return true
            }
        }
        return false
    }

    override fun findConflicts(board: Board): Set<Int> {
        val bad = mutableSetOf<Int>()
        RowColumnConstraint.collectDups(IntArray(size) { k -> k * size + k }, board, bad)
        RowColumnConstraint.collectDups(IntArray(size) { k -> k * size + (size - 1 - k) }, board, bad)
        return bad
    }

    override fun namedUnits(): List<Pair<String, IntArray>> = listOf(
        "main diagonal" to IntArray(size) { k -> k * size + k },
        "anti-diagonal" to IntArray(size) { k -> k * size + (size - 1 - k) },
    )
}

/**
 * Irregular regions (Jigsaw). [regions] maps each cell to a region id in
 * 0 until size; every id must appear exactly [size] times and be
 * 4-connected. Replaces [BoxConstraint], rows/cols still apply.
 */
class RegionConstraint(
    val size: Int,
    val regions: IntArray,
) : Constraint {

    init {
        validate(size, regions)
    }

    override fun violates(board: Board, index: Int, value: Int): Boolean {
        val region = regions[index]
        for (i in regions.indices) {
            if (i != index && regions[i] == region && board.cells[i] == value) return true
        }
        return false
    }

    override fun findConflicts(board: Board): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (id in 0 until size) {
            val unit = regions.indices.filter { regions[it] == id }.toIntArray()
            RowColumnConstraint.collectDups(unit, board, bad)
        }
        return bad
    }

    override fun namedUnits(): List<Pair<String, IntArray>> =
        (0 until size).map { id ->
            "region ${id + 1}" to regions.indices.filter { regions[it] == id }.toIntArray()
        }

    companion object {
        fun validate(size: Int, regions: IntArray) {
            require(regions.size == size * size) {
                "regions.size (${regions.size}) must equal size*size (${size * size})"
            }
            require(regions.all { it in 0 until size }) {
                "region ids must be in 0 until $size"
            }
            for (id in 0 until size) {
                val cells = regions.indices.filter { regions[it] == id }
                require(cells.size == size) {
                    "region $id has ${cells.size} cells, expected $size"
                }
                // 4-connected flood fill from the first cell must reach them all.
                val seen = mutableSetOf(cells.first())
                val queue = ArrayDeque(seen)
                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst()
                    val r = cur / size
                    val c = cur % size
                    val neighbours = listOf(
                        if (r > 0) cur - size else -1,
                        if (r < size - 1) cur + size else -1,
                        if (c > 0) cur - 1 else -1,
                        if (c < size - 1) cur + 1 else -1,
                    )
                    for (n in neighbours) {
                        if (n >= 0 && regions[n] == id && seen.add(n)) queue.add(n)
                    }
                }
                require(seen.size == size) {
                    "region $id is not contiguous (${seen.size}/$size reachable)"
                }
            }
        }
    }
}

/** Factory for the active constraint set per variant. */
object Constraints {
    fun classic(size: Int, boxRows: Int, boxCols: Int): List<Constraint> =
        listOf(RowColumnConstraint(size), BoxConstraint(size, boxRows, boxCols))

    fun diagonalX(size: Int, boxRows: Int, boxCols: Int): List<Constraint> =
        classic(size, boxRows, boxCols) + DiagonalConstraint(size)

    fun jigsaw(size: Int, regions: IntArray): List<Constraint> =
        listOf(RowColumnConstraint(size), RegionConstraint(size, regions.copyOf()))

    fun killer(size: Int, boxRows: Int, boxCols: Int, cages: List<Cage>): List<Constraint> =
        classic(size, boxRows, boxCols) + KillerConstraint(size, cages)

    fun thermo(size: Int, boxRows: Int, boxCols: Int, thermos: List<List<Int>>): List<Constraint> =
        classic(size, boxRows, boxCols) + ThermoConstraint(size, thermos)

    fun kropki(size: Int, boxRows: Int, boxCols: Int, dots: List<KropkiDot>): List<Constraint> =
        classic(size, boxRows, boxCols) + KropkiConstraint(size, dots)

    fun arrow(size: Int, boxRows: Int, boxCols: Int, arrows: List<ArrowShaft>): List<Constraint> =
        classic(size, boxRows, boxCols) + ArrowConstraint(size, arrows)

    fun sandwich(size: Int, boxRows: Int, boxCols: Int, clues: SandwichClues): List<Constraint> =
        classic(size, boxRows, boxCols) + SandwichConstraint(size, clues)

    fun forVariant(
        size: Int,
        boxRows: Int,
        boxCols: Int,
        variant: VariantType,
        regions: IntArray?,
        cages: List<Cage>? = null,
        thermos: List<List<Int>>? = null,
        dots: List<KropkiDot>? = null,
        arrows: List<ArrowShaft>? = null,
        sandwich: SandwichClues? = null,
    ): List<Constraint> = when (variant) {
        VariantType.CLASSIC -> classic(size, boxRows, boxCols)
        VariantType.DIAGONAL_X -> diagonalX(size, boxRows, boxCols)
        VariantType.JIGSAW -> {
            requireNotNull(regions) { "Jigsaw needs a regions map" }
            jigsaw(size, regions)
        }
        VariantType.KILLER -> {
            requireNotNull(cages) { "Killer needs cages" }
            killer(size, boxRows, boxCols, cages)
        }
        VariantType.THERMO -> {
            requireNotNull(thermos) { "Thermo needs thermometers" }
            thermo(size, boxRows, boxCols, thermos)
        }
        VariantType.KROPKI -> {
            requireNotNull(dots) { "Kropki needs dots" }
            kropki(size, boxRows, boxCols, dots)
        }
        VariantType.ARROW -> {
            requireNotNull(arrows) { "Arrow needs arrows" }
            arrow(size, boxRows, boxCols, arrows)
        }
        VariantType.SANDWICH -> {
            requireNotNull(sandwich) { "Sandwich needs clues" }
            sandwich(size, boxRows, boxCols, sandwich)
        }
    }
}
