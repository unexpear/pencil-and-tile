package com.simplegamegen.sudoku.model

/** Difficulty bands shared by generator, rater and UI. */
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT,
}

/**
 * Sudoku family. Only [CLASSIC] is fully implemented in Phase 1.
 * The rest reserve API surface for Phases 2-4.
 */
enum class VariantType {
    CLASSIC,
    DIAGONAL_X,
    JIGSAW,
    KILLER,
    THERMO,
    KROPKI,
    ARROW,
    SANDWICH,
}

/** Mutable board. `cells` is row-major, 0 == empty. Values are 1..size. */
class Board(
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val cells: IntArray,
) {
    init {
        require(size == boxRows * boxCols) {
            "size ($size) must equal boxRows ($boxRows) * boxCols ($boxCols)"
        }
        require(cells.size == size * size) {
            "cells.size (${cells.size}) must equal size*size (${size * size})"
        }
        require(cells.all { it in 0..size }) {
            "cell values must be in 0..$size"
        }
    }

    constructor(size: Int, boxRows: Int, boxCols: Int) : this(
        size, boxRows, boxCols, IntArray(size * size),
    )

    fun index(row: Int, col: Int): Int = row * size + col

    fun rowOf(index: Int): Int = index / size

    fun colOf(index: Int): Int = index % size

    operator fun get(row: Int, col: Int): Int = cells[index(row, col)]

    operator fun set(row: Int, col: Int, value: Int) {
        cells[index(row, col)] = value
    }

    operator fun get(index: Int): Int = cells[index]

    operator fun set(index: Int, value: Int) {
        cells[index] = value
    }

    fun copy(): Board = Board(size, boxRows, boxCols, cells.copyOf())

    fun givensCount(): Int = cells.count { it != 0 }

    fun isFull(): Boolean = cells.all { it != 0 }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Board) return false
        return size == other.size &&
            boxRows == other.boxRows &&
            boxCols == other.boxCols &&
            cells.contentEquals(other.cells)
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + boxRows
        result = 31 * result + boxCols
        result = 31 * result + cells.contentHashCode()
        return result
    }

    override fun toString(): String = buildString {
        for (r in 0 until size) {
            for (c in 0 until size) {
                val v = get(r, c)
                append(if (v == 0) '.' else ('0' + v))
                if (c < size - 1) append(' ')
            }
            if (r < size - 1) append('\n')
        }
    }

    companion object {
        /** Box dimensions for supported Classic sizes. */
        fun boxDims(size: Int): Pair<Int, Int> = when (size) {
            9 -> 3 to 3
            6 -> 2 to 3
            4 -> 2 to 2
            else -> throw IllegalArgumentException("Unsupported Classic size: $size (use 9, 6 or 4)")
        }

        fun empty(size: Int): Board {
            val (br, bc) = boxDims(size)
            return Board(size, br, bc)
        }

        fun fromRows(rows: Array<IntArray>): Board {
            require(rows.isNotEmpty()) { "rows must not be empty" }
            val size = rows.size
            require(rows.all { it.size == size }) { "board must be square" }
            val (br, bc) = boxDims(size)
            val cells = IntArray(size * size)
            rows.forEachIndexed { r, row -> row.forEachIndexed { c, v -> cells[r * size + c] = v } }
            return Board(size, br, bc, cells)
        }
    }
}

/**
 * Killer cage: [cells] (grid indices) must hold distinct digits summing to
 * [sum]. Lives in the model package so UI and engine share it freely.
 */
data class Cage(val cells: IntArray, val sum: Int) {
    init {
        require(cells.isNotEmpty()) { "cage must have cells" }
        require(sum > 0) { "cage sum must be positive" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Cage) return false
        return sum == other.sum && cells.contentEquals(other.cells)
    }

    override fun hashCode(): Int = 31 * sum + cells.contentHashCode()
}

/**
 * Kropki dot between two orthogonally adjacent cells: white means the
 * digits are consecutive, black means one is double the other.
 */
data class KropkiDot(val a: Int, val b: Int, val black: Boolean) {
    init {
        require(a != b) { "kropki dot needs two distinct cells" }
    }
}

/** Arrow: digits along [shaft] sum to the digit in [circle]. */
data class ArrowShaft(val circle: Int, val shaft: List<Int>) {
    init {
        require(shaft.isNotEmpty()) { "arrow needs a shaft" }
        require(circle !in shaft) { "circle must not be part of its shaft" }
    }
}

/**
 * Sandwich (outside) clues: for each row/column, the sum of the digits
 * between 1 and [size]. 0 means no clue on that side.
 */
data class SandwichClues(
    val top: IntArray,
    val bottom: IntArray,
    val left: IntArray,
    val right: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SandwichClues) return false
        return top.contentEquals(other.top) && bottom.contentEquals(other.bottom) &&
            left.contentEquals(other.left) && right.contentEquals(other.right)
    }

    override fun hashCode(): Int {
        var result = top.contentHashCode()
        result = 31 * result + bottom.contentHashCode()
        result = 31 * result + left.contentHashCode()
        result = 31 * result + right.contentHashCode()
        return result
    }
}

/**
 * Generated puzzle: [givens] holds the playable starting grid (0 == empty),
 * [solution] the full grid it was dug from, [seed] for share/repro.
 * [regions] is the Jigsaw region map (region id per cell), null otherwise.
 * [cages] are the Killer cages, null otherwise. [thermos] are ordered
 * bulb-to-tip cell paths, [dots] Kropki dots, [arrows] arrow rules and
 * [sandwich] outside clues — each non-null for its variant.
 */
data class Puzzle(
    val size: Int,
    val boxRows: Int,
    val boxCols: Int,
    val givens: IntArray,
    val solution: IntArray,
    val variant: VariantType = VariantType.CLASSIC,
    val difficulty: Difficulty,
    val seed: Long,
    val regions: IntArray? = null,
    val cages: List<Cage>? = null,
    val thermos: List<List<Int>>? = null,
    val dots: List<KropkiDot>? = null,
    val arrows: List<ArrowShaft>? = null,
    val sandwich: SandwichClues? = null,
) {
    init {
        require(size == boxRows * boxCols)
        require(givens.size == size * size)
        require(solution.size == size * size)
        if (variant == VariantType.JIGSAW) {
            requireNotNull(regions) { "Jigsaw puzzles need a regions map" }
            require(regions.size == size * size)
        }
        if (variant == VariantType.KILLER) {
            require(!cages.isNullOrEmpty()) { "Killer puzzles need cages" }
            require(cages.sumOf { it.cells.size } == size * size) {
                "Killer cages must cover every cell exactly once"
            }
        }
        if (variant == VariantType.THERMO) {
            require(!thermos.isNullOrEmpty()) { "Thermo puzzles need thermometers" }
            thermos.forEach { require(it.all { cell -> cell in 0 until size * size }) }
        }
        if (variant == VariantType.KROPKI) {
            requireNotNull(dots) { "Kropki puzzles need dots" }
            dots.forEach { require(it.a in 0 until size * size && it.b in 0 until size * size) }
        }
        if (variant == VariantType.ARROW) {
            require(!arrows.isNullOrEmpty()) { "Arrow puzzles need arrows" }
            arrows.forEach {
                require(it.circle in 0 until size * size)
                require(it.shaft.all { cell -> cell in 0 until size * size })
            }
        }
        if (variant == VariantType.SANDWICH) {
            val clues = requireNotNull(sandwich) { "Sandwich puzzles need clues" }
            require(clues.top.size == size && clues.bottom.size == size &&
                clues.left.size == size && clues.right.size == size)
        }
    }

    fun givensCount(): Int = givens.count { it != 0 }

    fun toBoard(): Board = Board(size, boxRows, boxCols, givens.copyOf())

    fun solutionBoard(): Board = Board(size, boxRows, boxCols, solution.copyOf())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Puzzle) return false
        return size == other.size && boxRows == other.boxRows && boxCols == other.boxCols &&
            givens.contentEquals(other.givens) && solution.contentEquals(other.solution) &&
            variant == other.variant && difficulty == other.difficulty && seed == other.seed &&
            ((regions == null && other.regions == null) ||
                (regions != null && other.regions != null && regions.contentEquals(other.regions))) &&
            cages == other.cages && thermos == other.thermos && dots == other.dots &&
            arrows == other.arrows && sandwich == other.sandwich
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + boxRows
        result = 31 * result + boxCols
        result = 31 * result + givens.contentHashCode()
        result = 31 * result + solution.contentHashCode()
        result = 31 * result + variant.hashCode()
        result = 31 * result + difficulty.hashCode()
        result = 31 * result + seed.hashCode()
        result = 31 * result + (regions?.contentHashCode() ?: 0)
        result = 31 * result + (cages?.hashCode() ?: 0)
        result = 31 * result + (thermos?.hashCode() ?: 0)
        result = 31 * result + (dots?.hashCode() ?: 0)
        result = 31 * result + (arrows?.hashCode() ?: 0)
        result = 31 * result + (sandwich?.hashCode() ?: 0)
        return result
    }
}
