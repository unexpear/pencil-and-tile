package com.simplegamegen.sudoku.difficulty

import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.Puzzle
import com.simplegamegen.sudoku.model.VariantType
import com.simplegamegen.sudoku.solver.TechniqueSolver

/**
 * Clue-density bands per board size. Jigsaw bands sit a few clues higher:
 * without boxes the grid is less constrained, so uniqueness needs more
 * givens. Phase 5 refines this with technique-based scoring calibrated
 * against real solve data.
 */
object Rater {

    /** Target clue ranges per size/difficulty (inclusive). */
    fun targetClues(
        size: Int,
        difficulty: Difficulty,
        variant: VariantType = VariantType.CLASSIC,
    ): IntRange {
        val base = when (size) {
            9 -> when (difficulty) {
                Difficulty.EASY -> 46..50
                Difficulty.MEDIUM -> 38..44
                Difficulty.HARD -> 32..37
                Difficulty.EXPERT -> 27..31
            }
            6 -> when (difficulty) {
                Difficulty.EASY -> 24..26
                Difficulty.MEDIUM -> 20..23
                Difficulty.HARD -> 17..19
                Difficulty.EXPERT -> 14..16
            }
            4 -> when (difficulty) {
                Difficulty.EASY -> 10..12
                Difficulty.MEDIUM -> 8..9
                Difficulty.HARD -> 6..7
                Difficulty.EXPERT -> 5..6
            }
            else -> throw IllegalArgumentException("Unsupported size: $size")
        }
        if (variant != VariantType.JIGSAW) return base
        val total = size * size
        val first = minOf(total - 2, base.first + 4)
        val last = minOf(total - 1, base.last + 5)
        return minOf(first, last)..last
    }

    /** Classify a finished puzzle by its clue count. */
    fun estimateByClues(
        size: Int,
        givensCount: Int,
        variant: VariantType = VariantType.CLASSIC,
    ): Difficulty {        // Nearest band by clue count: more clues == easier.
        val bands = Difficulty.entries.map { it to targetClues(size, it, variant) }
        bands.forEach { (d, range) -> if (givensCount in range) return d }
        // Outside bands (generation stopped early on uniqueness): snap to edge.
        return when {
            givensCount > targetClues(size, Difficulty.EASY, variant).last -> Difficulty.EASY
            givensCount < targetClues(size, Difficulty.EXPERT, variant).first -> Difficulty.EXPERT
            else -> {
                // Between two bands — pick the closer one by clue distance.
                val mid = { d: Difficulty ->
                    val r = targetClues(size, d, variant)
                    (r.first + r.last) / 2.0
                }
                Difficulty.entries.minByOrNull { kotlin.math.abs(mid(it) - givensCount) }!!
            }
        }
    }

    // ---- Killer: difficulty comes from cage structure, not clue count ----

    /** Biggest cage allowed per difficulty (authentic Killer has no givens). */
    fun targetMaxCageSize(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 4
        Difficulty.HARD -> 4
        Difficulty.EXPERT -> 5
    }

    /** Singles budget per difficulty (a single-cell cage is a free given). */
    fun targetMaxSingles(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 3
        Difficulty.MEDIUM -> 1
        Difficulty.HARD -> 0
        Difficulty.EXPERT -> 0
    }

    /**
     * Rough Killer classification: any givens beyond a nudge drop it to
     * EASY, otherwise larger average cages mean harder.
     */
    fun estimateKiller(avgCageSize: Double, givensCount: Int): Difficulty {
        if (givensCount > 2) return Difficulty.EASY
        return when {
            avgCageSize < 2.8 -> Difficulty.EASY
            avgCageSize < 3.4 -> Difficulty.MEDIUM
            avgCageSize < 4.0 -> Difficulty.HARD
            else -> Difficulty.EXPERT
        }
    }

    // ---- Technique-based verification scoring ----

    /**
     * Verification score: hardest human-style technique the simulated solve
     * demands (10.0 when the ladder gets stuck and search would be needed).
     * Replaces clue counting for difficulty decisions.
     */
    fun techniqueScore(puzzle: Puzzle): Double {
        val constraints = Constraints.forVariant(
            puzzle.size, puzzle.boxRows, puzzle.boxCols, puzzle.variant,
            puzzle.regions, puzzle.cages, puzzle.thermos, puzzle.dots,
            puzzle.arrows, puzzle.sandwich,
        )
        return TechniqueSolver.solve(puzzle.toBoard(), constraints).maxWeight
    }

    /**
     * Dependency metric: mean available placements over the opening steps.
     * High = many parallel paths (easy feel); low = funnelled (hard feel).
     * Discriminates inside bands where [techniqueScore] ties.
     */
    fun dependency(puzzle: Puzzle, steps: Int = 25): Double {
        val constraints = Constraints.forVariant(
            puzzle.size, puzzle.boxRows, puzzle.boxCols, puzzle.variant,
            puzzle.regions, puzzle.cages, puzzle.thermos, puzzle.dots,
            puzzle.arrows, puzzle.sandwich,
        )
        return TechniqueSolver.solve(puzzle.toBoard(), constraints, depSteps = steps).dependencyAvg
    }

    /**
     * Verification verdict: the honest difficulty band for a finished puzzle.
     *
     * Calibration (9x9 generator probe, seeds 1-3/band): max-technique ties
     * at singles through HARD, so Classic 9x9 splits EASY..HARD on
     * dependency (EASY ~32-48, MEDIUM ~23-44, HARD ~16-22, EXPERT ~7-20);
     * a stuck ladder (score 10, needs search) is always EXPERT. Jigsaw and
     * small grids do not discriminate on either axis (Jigsaw EASY/MEDIUM
     * overlap at dep 34-43), so clue bands rule there with the technique
     * score as a promotion gate. Killer rates on cage structure.
     * Thresholds stay provisional until player solve-time data refits them.
     */
    fun verify(puzzle: Puzzle): Difficulty {
        if (puzzle.variant == VariantType.KILLER) {
            val avg = puzzle.cages!!.map { it.cells.size }.average()
            return estimateKiller(avg, puzzle.givensCount())
        }
        val score = techniqueScore(puzzle)
        if (score >= 5.0) return Difficulty.EXPERT
        if (puzzle.size >= 9 && puzzle.variant != VariantType.JIGSAW) {
            return when (dependency(puzzle)) {
                in 30.0..Double.MAX_VALUE -> Difficulty.EASY
                in 20.0..Double.MAX_VALUE -> Difficulty.MEDIUM
                in 12.0..Double.MAX_VALUE -> Difficulty.HARD
                else -> Difficulty.EXPERT
            }
        }
        return estimateByClues(puzzle.size, puzzle.givensCount(), puzzle.variant)
    }

    /**
     * Band from verification score alone (technique axis). Full verdicts
     * should use [verify], which adds the dependency axis.
     */
    fun estimateBand(score: Double): Difficulty = when {
        score < 1.5 -> Difficulty.EASY
        score < 2.5 -> Difficulty.MEDIUM
        score < 5.0 -> Difficulty.HARD
        else -> Difficulty.EXPERT
    }
}
