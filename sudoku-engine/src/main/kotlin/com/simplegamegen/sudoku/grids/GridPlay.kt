package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * A grid Sudoku being played. [entries] holds the player's digits (givens included), [notes]
 * candidate bitmasks. [name] is shown for custom grids.
 */
data class GridGame(
    val rules: GridRules,
    val givens: List<Int>,
    val solution: List<Int>,
    val level: Int,
    val seed: Long,
    val name: String = "",
    val entries: List<Int> = givens,
    val notes: List<Int> = List(givens.size) { 0 },
    val hints: Int = 0,
) {
    val size: Int get() = rules.size

    init {
        require(givens.size == rules.cells && solution.size == rules.cells && entries.size == rules.cells && notes.size == rules.cells)
        require(rules.solved(solution.toIntArray()) && givens.indices.all { givens[it] == 0 || givens[it] == solution[it] })
        require(entries.indices.all { entries[it] in 0..size && (givens[it] == 0 || entries[it] == givens[it]) })
        require(level in 0..3 && hints >= 0 && '\n' !in name && '|' !in name)
    }

    val complete: Boolean get() = entries == solution
    val filled: Int get() = entries.count { it != 0 }
    fun conflicts(): Set<Int> = rules.conflicts(entries.toIntArray())
    fun mistakes(): Set<Int> = entries.indices.filter { entries[it] != 0 && entries[it] != solution[it] }.toSet()

    fun enter(cell: Int, digit: Int): GridGame {
        if (complete || cell !in entries.indices || givens[cell] != 0 || digit !in 1..size) return this
        val next = entries.toMutableList().also { it[cell] = if (it[cell] == digit) 0 else digit }
        // Placing a digit clears it from the notes of the squares it sees.
        val bit = 1 shl (digit - 1)
        val cleared = notes.toMutableList()
        if (next[cell] != 0) { cleared[cell] = 0; rules.peers[cell].forEach { cleared[it] = cleared[it] and bit.inv() } }
        return copy(entries = next, notes = cleared)
    }

    fun toggleNote(cell: Int, digit: Int): GridGame {
        if (complete || cell !in entries.indices || entries[cell] != 0 || digit !in 1..size) return this
        return copy(notes = notes.toMutableList().also { it[cell] = it[cell] xor (1 shl (digit - 1)) })
    }

    fun erase(cell: Int): GridGame {
        if (complete || cell !in entries.indices || givens[cell] != 0) return this
        if (entries[cell] == 0 && notes[cell] == 0) return this
        return copy(entries = entries.toMutableList().also { it[cell] = 0 }, notes = notes.toMutableList().also { it[cell] = 0 })
    }

    /** Fixes [preferred] if it's empty or wrong, else the first empty or wrong square. */
    fun hint(preferred: Int?): GridGame? {
        if (complete) return null
        fun open(i: Int) = entries[i] != solution[i]
        val cell = preferred?.takeIf { it in entries.indices && open(it) } ?: entries.indices.firstOrNull { entries[it] != 0 && open(it) }
            ?: entries.indices.first(::open)
        return copy(entries = entries.toMutableList().also { it[cell] = solution[cell] }, notes = notes.toMutableList().also { it[cell] = 0 }, hints = hints + 1)
    }

    companion object {
        /** A random standard, diagonal, jigsaw or killer grid of [size]. */
        fun generate(seed: Long, size: Int, level: Int, style: GridStyle): GridGame {
            val random = Random(seed)
            repeat(20) {
                checkpoint()
                val boxes = GridRules.standard(size)
                val plain = GridRules(size, boxes.regions, rules = if (style == GridStyle.DIAGONAL) setOf(GridRule.DIAGONALS) else emptySet())
                val solution = GridGenerator.solution(plain, random) ?: return@repeat
                // Jigsaw regions are carved out of a solved grid, so it stays a valid answer.
                val base = if (style == GridStyle.JIGSAW) GridRules(size, GridGenerator.jigsawFromSolution(size, boxes.regions, solution, random)) else plain
                if (style == GridStyle.KILLER) {
                    // Big killer grids get harder through larger cages, and stay solvable by deduction so they build quickly.
                    val big = size > 9
                    val maxCage = if (big && level >= 2) 5 else 4
                    val rules = base.copy(cages = GridGenerator.cages(size, solution, random, maxSize = maxCage))
                    // Killer grids start as empty as possible; givens stay only where the cages alone aren't enough.
                    val givens = GridGenerator.dig(rules, solution, 0, random, symmetric = false, logicOnly = level <= 1 || big)
                    return GridGame(rules, givens.toList(), solution.toList(), level, seed)
                }
                val target = (size * size * GridGenerator.givenShare(size, level)).toInt()
                val givens = GridGenerator.dig(base, solution, target, random, logicOnly = level <= 1)
                return GridGame(base, givens.toList(), solution.toList(), level, seed)
            }
            error("Couldn't build a $size×$size grid")
        }

        /** A custom grid: solves the player's layout and givens, or fills in givens for an empty layout. */
        fun fromDraft(rules: GridRules, givens: IntArray, name: String, level: Int, seed: Long): GridGame? {
            val check = GridSolver.solve(rules, givens, limit = 2, maxNodes = 1_500_000)
            if (check.count == 1 && check.complete) return GridGame(rules, givens.toList(), check.solutions.single().toList(), level, seed, name)
            return null
        }
    }
}

enum class GridStyle(val label: String) { CLASSIC("Classic"), DIAGONAL("Diagonal X"), JIGSAW("Jigsaw"), KILLER("Killer") }

/** Text format for grid layouts (share codes start with SGG1) and saved games. */
object GridCodec {
    private const val LAYOUT = "SGG1"

    fun encodeRules(r: GridRules): String = listOf(
        LAYOUT, r.size.toString(),
        r.regions.joinToString(",") { it.toString(36) },
        r.rules.joinToString(",") { it.name },
        r.extras.joinToString(";") { g -> g.joinToString(",") },
        r.cages.joinToString(";") { c -> "${c.sum}:" + c.cells.joinToString(",") },
        r.parity.joinToString("") { when (it) { Parity.ODD -> "o"; Parity.EVEN -> "e"; Parity.NONE -> "." } },
    ).joinToString("|")

    fun decodeRules(text: String): GridRules? = try {
        val f = text.trim().split('|')
        require(f.size == 7 && f[0] == LAYOUT)
        val n = f[1].toInt()
        GridRules(
            size = n,
            regions = f[2].split(',').map { it.toInt(36) },
            rules = f[3].split(',').filter { it.isNotEmpty() }.map { GridRule.valueOf(it) }.toSet(),
            extras = f[4].split(';').filter { it.isNotEmpty() }.map { g -> g.split(',').map(String::toInt) },
            cages = f[5].split(';').filter { it.isNotEmpty() }.map { c -> val (s, cells) = c.split(':'); SumCage(cells.split(',').map(String::toInt), s.toInt()) },
            parity = f[6].map { when (it) { 'o' -> Parity.ODD; 'e' -> Parity.EVEN; '.' -> Parity.NONE; else -> throw IllegalArgumentException() } },
        )
    } catch (_: IllegalArgumentException) { null } catch (_: IndexOutOfBoundsException) { null }

    /** A shareable custom puzzle: layout plus givens. */
    fun encodePuzzle(r: GridRules, givens: List<Int>, name: String): String =
        encodeRules(r) + "\n" + givens.joinToString(",") + "\n" + name.replace('\n', ' ')

    fun decodePuzzle(text: String): Triple<GridRules, IntArray, String>? = try {
        val l = text.trim().split('\n')
        require(l.size in 2..3)
        val rules = decodeRules(l[0]) ?: throw IllegalArgumentException()
        val givens = l[1].split(',').map(String::toInt).toIntArray()
        require(givens.size == rules.cells && givens.all { it in 0..rules.size })
        Triple(rules, givens, l.getOrNull(2).orEmpty())
    } catch (_: IllegalArgumentException) { null }

    fun encode(g: GridGame): String = listOf("G1", encodeRules(g.rules), g.givens.joinToString(","), g.solution.joinToString(","), g.level.toString(),
        g.seed.toString(), g.name, g.entries.joinToString(","), g.notes.joinToString(","), g.hints.toString()).joinToString("\n")

    fun decode(text: String): GridGame? = try {
        val l = text.split('\n'); require(l.size == 10 && l[0] == "G1")
        fun ints(s: String) = s.split(',').map(String::toInt)
        GridGame(decodeRules(l[1]) ?: throw IllegalArgumentException(), ints(l[2]), ints(l[3]), l[4].toInt(), l[5].toLong(), l[6],
            ints(l[7]), ints(l[8]), l[9].toInt())
    } catch (_: IllegalArgumentException) { null }
}
