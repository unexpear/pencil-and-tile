package com.simplegamegen.sudoku.grids

import kotlin.random.Random

/** What checking a draft found. */
enum class DraftVerdict { BROKEN, CLASHES, NO_SOLUTION, UNIQUE, MULTIPLE, TOO_HARD }

data class DraftCheck(
    val verdict: DraftVerdict,
    val issues: List<GridIssue>,
    /** For UNIQUE: true when plain deduction (singles) solves it without guessing. */
    val logicSolvable: Boolean = false,
    /** For MULTIPLE: two different answers, to show where they differ. */
    val answers: List<IntArray> = emptyList(),
) {
    val playable: Boolean get() = verdict == DraftVerdict.UNIQUE
    /** Squares where the first two answers differ. */
    val ambiguous: List<Int> get() = if (answers.size < 2) emptyList() else answers[0].indices.filter { answers[0][it] != answers[1][it] }
}

/**
 * A custom grid being edited. Unlike [GridRules] it may be incomplete or broken; [check] says
 * what's wrong. Region -1 means the square isn't in a region yet.
 */
data class GridDraft(
    val size: Int,
    val regions: List<Int>,
    val givens: List<Int> = List(size * size) { 0 },
    val rules: Set<GridRule> = emptySet(),
    val extras: List<List<Int>> = emptyList(),
    val cages: List<SumCage> = emptyList(),
    val parity: List<Parity> = List(size * size) { Parity.NONE },
    val name: String = "",
) {
    val cells: Int get() = size * size

    init {
        require(size in GridRules.MIN_SIZE..GridRules.MAX_SIZE && regions.size == cells && givens.size == cells && parity.size == cells)
        require(regions.all { it in -1 until size } && givens.all { it in 0..size })
    }

    fun paintRegion(cellsToPaint: Collection<Int>, region: Int): GridDraft =
        copy(regions = regions.toMutableList().also { m -> cellsToPaint.filter { it in 0 until cells }.forEach { m[it] = region.coerceIn(-1, size - 1) } })

    fun setGiven(cell: Int, digit: Int): GridDraft = if (cell !in 0 until cells || digit !in 0..size) this
        else copy(givens = givens.toMutableList().also { it[cell] = digit })

    fun setParity(cell: Int, p: Parity): GridDraft = if (cell !in 0 until cells) this else copy(parity = parity.toMutableList().also { it[cell] = p })

    fun toggleRule(rule: GridRule): GridDraft = copy(rules = if (rule in rules) rules - rule else rules + rule)

    /** Adds a cage over [cellsInCage], replacing any cage those squares were already in. */
    fun addCage(cellsInCage: Collection<Int>, sum: Int): GridDraft {
        val set = cellsInCage.toSet()
        return copy(cages = cages.filter { c -> c.cells.none { it in set } } + SumCage(set.sorted(), sum))
    }

    fun removeCageAt(cell: Int): GridDraft = copy(cages = cages.filter { cell !in it.cells })

    fun addExtra(group: Collection<Int>): GridDraft = copy(extras = extras + listOf(group.toSet().sorted()))
    fun removeExtraAt(cell: Int): GridDraft = copy(extras = extras.filter { cell !in it })

    fun clearGivens(): GridDraft = copy(givens = List(cells) { 0 })
    fun clearRegions(): GridDraft = copy(regions = List(cells) { -1 })

    fun withBoxes(br: Int, bc: Int): GridDraft = copy(regions = GridRules.boxRegions(size, br, bc))

    fun withJigsaw(random: Random): GridDraft = copy(regions = GridGenerator.jigsawRegions(size, random))

    /** Mirror of [cell] through the centre (for symmetric givens). */
    fun mirror(cell: Int): Int = cells - 1 - cell

    /** Classic Windoku windows for 9×9 grids. */
    fun withWindows(): GridDraft {
        if (size != 9) return this
        val windows = listOf(1 to 1, 1 to 5, 5 to 1, 5 to 5).map { (r0, c0) -> (0 until 9).map { k -> (r0 + k / 3) * 9 + c0 + k % 3 } }
        return copy(extras = (extras + windows).distinct())
    }

    /** Rules for play, or null while the layout is broken. */
    fun toRules(): GridRules? = try {
        GridRules(size, regions, extras, rules, cages, parity)
    } catch (_: IllegalArgumentException) { null }

    /** Checks layout, givens and how many solutions there are. */
    fun check(maxNodes: Long = 1_500_000): DraftCheck {
        val issues = GridValidator.layoutProblems(size, regions.toIntArray(), extras, cages).toMutableList()
        val rules = toRules() ?: return DraftCheck(DraftVerdict.BROKEN, issues)
        val grid = givens.toIntArray()
        val clashes = rules.conflicts(grid)
        if (clashes.isNotEmpty()) return DraftCheck(DraftVerdict.CLASHES, issues + GridIssue(true, "Some starting digits break the rules.", clashes.toList()))
        val result = GridSolver.solve(rules, grid, limit = 2, maxNodes = maxNodes)
        val verdict = when {
            result.count == 0 && result.complete -> DraftVerdict.NO_SOLUTION
            result.count == 1 && result.complete -> DraftVerdict.UNIQUE
            result.count >= 2 -> DraftVerdict.MULTIPLE
            else -> DraftVerdict.TOO_HARD
        }
        return DraftCheck(verdict, issues, verdict == DraftVerdict.UNIQUE && GridSolver.solveByLogic(rules, grid) != null, result.solutions)
    }

    /**
     * Fills in givens so the layout has one answer: keeps the player's digits, builds a full grid
     * around them and removes the rest down to about [share] of the squares.
     */
    fun autoGivens(random: Random, share: Double): GridDraft? {
        val rules = toRules() ?: return null
        val start = givens.toIntArray()
        if (rules.conflicts(start).isNotEmpty()) return null
        val solution = GridGenerator.solution(rules, random, maxNodes = 1_500_000, start = start) ?: return null
        val keep = givens.indices.filter { givens[it] != 0 }.toSet()
        val dug = GridGenerator.dig(rules, solution, (cells * share).toInt(), random, keep, symmetric = true)
        return copy(givens = dug.toList())
    }

    /** Adds givens from one answer where the answers differ, until the puzzle has one solution. */
    fun fixUniqueness(random: Random): GridDraft? {
        var draft = this
        repeat(cells) {
            val result = draft.check(maxNodes = 400_000)
            when (result.verdict) {
                DraftVerdict.UNIQUE -> return draft
                DraftVerdict.MULTIPLE -> {
                    val cell = result.ambiguous.random(random)
                    draft = draft.setGiven(cell, result.answers[0][cell])
                }
                else -> return null
            }
        }
        return null
    }

    companion object {
        fun blank(size: Int): GridDraft = GridDraft(size, GridRules.boxShapes(size).firstOrNull()?.let { (br, bc) -> GridRules.boxRegions(size, br, bc) } ?: List(size * size) { -1 })

        fun from(game: GridGame): GridDraft = GridDraft(game.size, game.rules.regions, game.givens, game.rules.rules, game.rules.extras, game.rules.cages, game.rules.parity, game.name)
    }
}

/** Saved custom grids: one draft per line pair. */
object GridDraftCodec {
    fun encode(d: GridDraft): String = listOf("D1", d.size, d.regions.joinToString(","), d.givens.joinToString(","), d.rules.joinToString(",") { it.name },
        d.extras.joinToString(";") { it.joinToString(",") }, d.cages.joinToString(";") { c -> "${c.sum}:" + c.cells.joinToString(",") },
        d.parity.joinToString("") { when (it) { Parity.ODD -> "o"; Parity.EVEN -> "e"; Parity.NONE -> "." } }, d.name.replace('\n', ' ').replace('\t', ' ')).joinToString("\t")

    fun decode(text: String): GridDraft? = try {
        val f = text.split('\t'); require(f.size == 9 && f[0] == "D1")
        fun ints(s: String) = s.split(',').filter { it.isNotEmpty() }.map(String::toInt)
        GridDraft(f[1].toInt(), ints(f[2]), ints(f[3]), f[4].split(',').filter { it.isNotEmpty() }.map { GridRule.valueOf(it) }.toSet(),
            f[5].split(';').filter { it.isNotEmpty() }.map(::ints),
            f[6].split(';').filter { it.isNotEmpty() }.map { c -> val (s, cells) = c.split(':'); SumCage(ints(cells), s.toInt()) },
            f[7].map { when (it) { 'o' -> Parity.ODD; 'e' -> Parity.EVEN; '.' -> Parity.NONE; else -> throw IllegalArgumentException() } }, f[8])
    } catch (_: IllegalArgumentException) { null } catch (_: IndexOutOfBoundsException) { null }
}
