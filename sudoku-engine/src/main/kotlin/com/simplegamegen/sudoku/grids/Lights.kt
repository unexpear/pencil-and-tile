package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Lights: place lamps on the white squares so every white square is lit.
 * A lamp shines across and down until it reaches a black square.
 * Two lamps cannot see each other. A number in a black square is how many lamps touch it.
 */
data class LightsGame(
    val setting: Int,
    val seed: Long,
    val size: Int,
    /** -1 white, 0–4 a numbered black square. */
    val wall: List<Int>,
    val solution: List<Boolean>,
    val lamps: List<Boolean> = List(solution.size) { false },
    val hints: Int = 0,
) {
    init {
        require(setting in SIZES.indices && size == SIZES[setting])
        require(wall.size == size * size && solution.size == wall.size && lamps.size == wall.size)
        require(wall.all { it in -1..4 } && hints >= 0)
        require(solution.indices.none { solution[it] && wall[it] >= 0 })
        require(LightsRules.valid(size, wall, solution))
    }

    val complete: Boolean get() = LightsRules.valid(size, wall, lamps)

    fun toggle(cell: Int): LightsGame? {
        if (complete || cell !in lamps.indices || wall[cell] >= 0) return null
        return copy(lamps = lamps.toMutableList().also { it[cell] = !it[cell] })
    }

    fun hint(): LightsGame? {
        if (complete) return null
        val i = lamps.indices.firstOrNull { wall[it] < 0 && lamps[it] != solution[it] } ?: return null
        return copy(lamps = lamps.toMutableList().also { it[i] = solution[i] }, hints = hints + 1)
    }

    companion object {
        val SIZES = listOf(5, 6, 6, 7)

        fun generate(seed: Long, setting: Int): LightsGame {
            val random = Random(seed)
            val n = SIZES[setting]
            repeat(40) {
                checkpoint()
                val black = BooleanArray(n * n)
                val lamps = BooleanArray(n * n)
                // Odd rows are walls, so each even row is its own lit strip.
                for (row in 1 until n step 2) for (col in 0 until n) black[row * n + col] = true
                for (row in 0 until n step 2) {
                    black[row * n] = true
                    if (n > 1) lamps[row * n + 1] = true
                }
                val wall = IntArray(n * n) { cell ->
                    if (!black[cell]) -1
                    else LightsRules.neighbors(cell, n).count { lamps[it] }
                }
                if (LightsRules.valid(n, wall.toList(), lamps.toList()) && LightsRules.count(n, wall.toList(), 2) == 1) {
                    return LightsGame(setting, seed, n, wall.toList(), lamps.toList())
                }
            }
            error("Couldn't build a unique Lights")
        }
    }
}

object LightsRules {
    fun neighbors(cell: Int, n: Int) = listOfNotNull(
        (cell - n).takeIf { it >= 0 },
        (cell + n).takeIf { it < n * n },
        (cell - 1).takeIf { cell % n > 0 },
        (cell + 1).takeIf { cell % n < n - 1 },
    )

    fun sees(n: Int, wall: List<Int>, a: Int, b: Int): Boolean {
        if (a == b) return false
        val ar = a / n; val ac = a % n; val br = b / n; val bc = b % n
        if (ar != br && ac != bc) return false
        val cells = if (ar == br) (minOf(ac, bc) + 1 until maxOf(ac, bc)).map { ar * n + it }
        else (minOf(ar, br) + 1 until maxOf(ar, br)).map { it * n + ac }
        return cells.none { wall[it] != -1 }
    }

    fun lit(n: Int, wall: List<Int>, lamps: List<Boolean>, cell: Int): Boolean {
        if (wall[cell] >= 0) return true
        if (lamps[cell]) return true
        return lamps.indices.any { lamps[it] && sees(n, wall, cell, it) }
    }

    fun valid(n: Int, wall: List<Int>, lamps: List<Boolean>): Boolean {
        if (lamps.indices.any { lamps[it] && wall[it] != -1 }) return false
        val on = lamps.indices.filter { lamps[it] }
        for (i in on.indices) for (j in i + 1 until on.size) if (sees(n, wall, on[i], on[j])) return false
        if ((0 until n * n).any { !lit(n, wall, lamps, it) }) return false
        return wall.indices.none { cell -> wall[cell] >= 0 && neighbors(cell, n).count { lamps[it] } != wall[cell] }
    }

    /** Lamp placements on the white squares of [black], up to [limit]. */
    fun solve(n: Int, black: BooleanArray, limit: Int): List<BooleanArray> {
        val wall = black.map { if (it) -2 else -1 }
        return placements(n, wall, limit)
    }

    fun count(n: Int, wall: List<Int>, limit: Int): Int = placements(n, wall, limit).size

    private fun placements(n: Int, wall: List<Int>, limit: Int): List<BooleanArray> {
        val whites = wall.indices.filter { wall[it] < 0 }
        val lamps = BooleanArray(n * n)
        val out = mutableListOf<BooleanArray>()
        var nodes = 0
        fun rec(k: Int) {
            if (out.size >= limit) return
            if (++nodes > 80_000) return
            if (k == whites.size) {
                if (valid(n, wall, lamps.toList())) out += lamps.copyOf()
                return
            }
            val cell = whites[k]
            val seesLamp = lamps.indices.any { lamps[it] && sees(n, wall, cell, it) }
            val wallFull = neighbors(cell, n).any { wall[it] >= 0 && neighbors(it, n).count { lamps[it] } >= wall[it] }
            if (!seesLamp && !wallFull) {
                lamps[cell] = true
                if (stillPossible(n, wall, lamps, whites, k)) rec(k + 1)
                lamps[cell] = false
            }
            if (stillPossible(n, wall, lamps, whites, k)) rec(k + 1)
        }
        rec(0)
        return out
    }

    private fun stillPossible(n: Int, wall: List<Int>, lamps: BooleanArray, whites: List<Int>, decided: Int): Boolean {
        for (cell in wall.indices) if (wall[cell] >= 0) {
            val around = neighbors(cell, n)
            val on = around.count { lamps[it] }
            val free = around.count { wall[it] == -1 && whites.indexOf(it) > decided }
            if (on > wall[cell] || on + free < wall[cell]) return false
        }
        return true
    }
}

object LightsCodec {
    fun encode(g: LightsGame) = listOf(
        "1", g.setting.toString(), g.seed.toString(), g.size.toString(), g.wall.joinToString(","),
        g.solution.joinToString("") { if (it) "1" else "0" }, g.lamps.joinToString("") { if (it) "1" else "0" }, g.hints.toString(),
    ).joinToString("\n")

    fun decode(text: String): LightsGame? = try {
        val l = text.split('\n')
        require(l.size == 8 && l[0] == "1")
        LightsGame(
            l[1].toInt(), l[2].toLong(), l[3].toInt(), l[4].split(',').map(String::toInt),
            l[5].map { it == '1' }, l[6].map { it == '1' }, l[7].toInt(),
        )
    } catch (_: IllegalArgumentException) { null }
}
