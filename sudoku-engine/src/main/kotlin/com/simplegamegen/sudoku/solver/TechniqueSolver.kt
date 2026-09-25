package com.simplegamegen.sudoku.solver

import com.simplegamegen.sudoku.constraints.Constraint
import com.simplegamegen.sudoku.model.Board

/**
 * Human-style solving techniques, ordered by difficulty. Weights assume
 * visible candidates (app play with notes): naked singles are the easiest
 * move, which flips the classic paper-and-pencil order.
 */
enum class SolveTechnique(val weight: Double) {
    NAKED_SINGLE(1.0),
    HIDDEN_SINGLE(1.2),
    NAKED_PAIR(2.0),
    HIDDEN_PAIR(2.4),
    POINTING(2.6),
    CLAIMING(2.8),
}

data class SolveReport(
    val solved: Boolean,
    /** Hardest technique applied (10.0 when the ladder gets stuck). */
    val maxWeight: Double,
    val counts: Map<SolveTechnique, Int>,
    /** Mean available placements over the first steps (dependency metric). */
    val dependencyAvg: Double,
    val placements: Int,
)

/**
 * Verification solver: simulates human-like solving with persistent
 * candidate marks, recording which techniques a puzzle demands. Used by
 * [com.simplegamegen.sudoku.difficulty.Rater] to score difficulty instead
 * of counting clues. Generic over [Constraint], so every variant rates
 * through the same code.
 */
object TechniqueSolver {

    fun solve(
        board: Board,
        constraints: List<Constraint>,
        depSteps: Int = 25,
    ): SolveReport {
        val size = board.size
        val work = board.copy()
        val units = constraints.flatMap { it.namedUnits() }
        val boxUnits = units.filter { it.first == "box" }.map { it.second }
        val rowUnits = units.filter { it.first.startsWith("row ") }.map { it.second }
        val colUnits = units.filter { it.first.startsWith("column ") }.map { it.second }
        val boxOf = IntArray(size * size) { -1 }
        boxUnits.forEachIndexed { id, unit -> unit.forEach { boxOf[it] = id } }

        // Persistent candidate marks.
        val marks = Array(size * size) { i ->
            if (work.cells[i] != 0) mutableSetOf()
            else (1..size).filter { d -> constraints.none { it.violates(work, i, d) } }.toMutableSet()
        }
        fun reprune() {
            for (j in marks.indices) {
                if (work.cells[j] != 0) {
                    marks[j].clear()
                } else {
                    marks[j].removeIf { d -> constraints.any { it.violates(work, j, d) } }
                }
            }
        }

        val counts = mutableMapOf<SolveTechnique, Int>()
        var maxWeight = 0.0
        val depSamples = mutableListOf<Int>()
        var placements = 0
        fun use(t: SolveTechnique) {
            counts[t] = (counts[t] ?: 0) + 1
            if (t.weight > maxWeight) maxWeight = t.weight
        }

        fun nakedSingles(): List<Pair<Int, Int>> =
            marks.indices
                .filter { work.cells[it] == 0 && marks[it].size == 1 }
                .map { it to marks[it].first() }

        fun hiddenSingles(): List<Triple<Int, Int, String>> {
            val out = mutableListOf<Triple<Int, Int, String>>()
            for ((name, unit) in units) {
                for (d in 1..size) {
                    var spot = -1
                    var n = 0
                    for (i in unit) {
                        if (work.cells[i] == 0 && d in marks[i]) {
                            spot = i
                            if (++n > 1) break
                        }
                    }
                    if (n == 1) out.add(Triple(spot, d, name))
                }
            }
            return out
        }

        fun place(i: Int, v: Int) {
            work.cells[i] = v
            marks[i].clear()
            placements++
            reprune()
        }

        fun nakedPairs(): Boolean {
            var changed = false
            for ((_, unit) in units) {
                val empties = unit.filter { work.cells[it] == 0 && marks[it].size == 2 }
                val bySet = empties.groupBy { marks[it].toSet() }
                for ((pair, cells) in bySet) {
                    if (cells.size != 2) continue
                    for (i in unit) {
                        if (work.cells[i] == 0 && i !in cells && marks[i].removeAll(pair)) {
                            changed = true
                        }
                    }
                }
            }
            return changed
        }

        fun hiddenPairs(): Boolean {
            var changed = false
            for ((_, unit) in units) {
                val empties = unit.filter { work.cells[it] == 0 }
                for (a in 1..size) {
                    for (b in a + 1..size) {
                        val holders = empties.filter { a in marks[it] || b in marks[it] }
                        if (holders.size != 2) continue
                        for (i in holders) {
                            if (marks[i].retainAll(setOf(a, b))) changed = true
                        }
                    }
                }
            }
            return changed
        }

        fun pointing(): Boolean {
            if (boxUnits.isEmpty() || (rowUnits.isEmpty() && colUnits.isEmpty())) return false
            var changed = false
            for (box in boxUnits) {
                for (d in 1..size) {
                    val holders = box.filter { work.cells[it] == 0 && d in marks[it] }
                    if (holders.isEmpty()) continue
                    val rows = holders.map { it / size }.toSet()
                    if (rows.size == 1) {
                        val r = rows.first()
                        for (i in 0 until size) {
                            val cell = r * size + i
                            if (cell !in box && work.cells[cell] == 0 && marks[cell].remove(d)) {
                                changed = true
                            }
                        }
                    }
                    val cols = holders.map { it % size }.toSet()
                    if (cols.size == 1) {
                        val c = cols.first()
                        for (r in 0 until size) {
                            val cell = r * size + c
                            if (cell !in box && work.cells[cell] == 0 && marks[cell].remove(d)) {
                                changed = true
                            }
                        }
                    }
                }
            }
            return changed
        }

        fun claiming(): Boolean {
            if (boxUnits.isEmpty() || boxOf.any { it == -1 }) return false
            var changed = false
            val lines = rowUnits + colUnits
            for (line in lines) {
                for (d in 1..size) {
                    val holders = line.filter { work.cells[it] == 0 && d in marks[it] }
                    if (holders.isEmpty()) continue
                    val boxes = holders.map { boxOf[it] }.toSet()
                    if (boxes.size == 1) {
                        for (cell in boxUnits[boxes.first()]) {
                            if (cell !in line && work.cells[cell] == 0 && marks[cell].remove(d)) {
                                changed = true
                            }
                        }
                    }
                }
            }
            return changed
        }

        var guard = 0
        while (!work.isFull() && guard++ < size * size * 4) {
            if (depSamples.size < depSteps) {
                depSamples.add(nakedSingles().size + hiddenSingles().size)
            }
            val naked = nakedSingles()
            if (naked.isNotEmpty()) {
                naked.forEach { (i, v) ->
                    if (work.cells[i] == 0) {
                        place(i, v)
                        use(SolveTechnique.NAKED_SINGLE)
                    }
                }
                continue
            }
            val hidden = hiddenSingles()
            if (hidden.isNotEmpty()) {
                // Apply one per pass; placements reshape hidden singles.
                val (i, v, _) = hidden.first()
                if (work.cells[i] == 0) {
                    place(i, v)
                    use(SolveTechnique.HIDDEN_SINGLE)
                }
                continue
            }
            if (nakedPairs()) {
                use(SolveTechnique.NAKED_PAIR)
                continue
            }
            if (hiddenPairs()) {
                use(SolveTechnique.HIDDEN_PAIR)
                continue
            }
            if (pointing()) {
                use(SolveTechnique.POINTING)
                continue
            }
            if (claiming()) {
                use(SolveTechnique.CLAIMING)
                continue
            }
            break // ladder exhausted
        }

        val solved = work.isFull() &&
            constraints.all { it.findConflicts(work).isEmpty() }
        return SolveReport(
            solved = solved,
            maxWeight = if (solved) maxWeight else 10.0,
            counts = counts,
            dependencyAvg = if (depSamples.isEmpty()) 0.0 else depSamples.average(),
            placements = placements,
        )
    }
}
