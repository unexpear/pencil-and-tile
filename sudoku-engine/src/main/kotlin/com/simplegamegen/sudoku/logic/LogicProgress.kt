package com.simplegamegen.sudoku.logic

/** Player entries on top of a puzzle. Givens are never editable. */
data class LogicProgress(
    val puzzle: LogicPuzzle,
    val entries: List<Int> = puzzle.givens,
    val notes: List<Set<Int>> = List(puzzle.rules.cellCount) { emptySet() },
    val hints: Int = 0,
) {
    init {
        val r = puzzle.rules
        require(entries.size == r.cellCount && notes.size == r.cellCount && hints >= 0)
        entries.forEachIndexed { i, v ->
            if (!r.open[i]) require(v == 0 && notes[i].isEmpty())
            else if (puzzle.givens[i] != 0) require(v == puzzle.givens[i] && notes[i].isEmpty())
            else require(v in 0..r.maxDigit && notes[i].all { it in 1..r.maxDigit })
        }
    }

    val complete: Boolean get() = entries == puzzle.solution
    fun editable(cell: Int): Boolean = cell in entries.indices && puzzle.rules.open[cell] && puzzle.givens[cell] == 0

    fun enter(cell: Int, value: Int): LogicProgress {
        if (!editable(cell) || value !in 0..puzzle.rules.maxDigit || complete) return this
        val nextNotes = if (value == 0) notes else notes.toMutableList().also { list ->
            // Placing a digit clears it from the notes of every cell that shares a unit.
            list[cell] = emptySet()
            puzzle.rules.units.filter { cell in it }.flatten().forEach { peer -> if (peer != cell) list[peer] = list[peer] - value }
        }
        return copy(entries = entries.toMutableList().also { it[cell] = value }, notes = nextNotes)
    }

    fun toggleNote(cell: Int, value: Int): LogicProgress {
        if (!editable(cell) || entries[cell] != 0 || value !in 1..puzzle.rules.maxDigit || complete) return this
        return copy(notes = notes.toMutableList().also { it[cell] = if (value in it[cell]) it[cell] - value else it[cell] + value })
    }

    fun erase(cell: Int): LogicProgress = if (!editable(cell) || complete) this
        else copy(entries = entries.toMutableList().also { it[cell] = 0 }, notes = notes.toMutableList().also { it[cell] = emptySet() })

    /** Reveals [preferred] if it is empty or wrong, otherwise the first such cell. */
    fun hint(preferred: Int?): Pair<LogicProgress, Int>? {
        if (complete) return null
        val cell = preferred?.takeIf { editable(it) && entries[it] != puzzle.solution[it] }
            ?: entries.indices.firstOrNull { editable(it) && entries[it] != puzzle.solution[it] } ?: return null
        val next = enter(cell, puzzle.solution[cell])
        return next.copy(hints = hints + 1) to cell
    }

    /** Filled cells that differ from the solution. */
    fun mistakes(): Set<Int> = entries.indices.filter { editable(it) && entries[it] != 0 && entries[it] != puzzle.solution[it] }.toSet()

    /** Cells visibly breaking a rule: repeats, full cages with the wrong result, runs over or off their sum. */
    fun conflicts(): Set<Int> {
        val r = puzzle.rules
        val bad = mutableSetOf<Int>()
        r.units.forEach { unit ->
            unit.filter { entries[it] != 0 }.groupBy { entries[it] }.values.filter { it.size > 1 }.forEach { bad += it }
        }
        r.cages.forEach { cage ->
            val values = cage.cells.map { entries[it] }
            if (values.none { it == 0 } && !LogicSolver.cageValue(cage, values)) bad += cage.cells
        }
        r.runs.forEach { run ->
            val values = run.cells.map { entries[it] }
            val sum = values.sum()
            if (sum > run.sum || (values.none { it == 0 } && sum != run.sum)) bad += run.cells.filter { entries[it] != 0 }
        }
        r.less.forEach { (a, b) -> if (entries[a] != 0 && entries[b] != 0 && entries[a] >= entries[b]) { bad += a; bad += b } }
        return bad
    }
}

/** Versioned text save: the full puzzle plus progress, validated on load. */
object LogicSaveCodec {
    private const val VERSION = "2"

    fun encode(p: LogicProgress): String {
        val z = p.puzzle; val r = z.rules
        return listOf(
            VERSION, r.kind.name, z.level.name, z.seed.toString(), r.rows.toString(), r.cols.toString(), r.maxDigit.toString(),
            r.open.joinToString("") { if (it) "1" else "0" },
            z.givens.joinToString(","), z.solution.joinToString(","),
            r.cages.joinToString(";") { "${it.cells.joinToString(" ")}:${it.op.name}:${it.target}" },
            r.runs.joinToString(";") { "${it.cells.joinToString(" ")}:${it.sum}:${if (it.across) "A" else "D"}" },
            p.entries.joinToString(","),
            p.notes.mapIndexedNotNull { i, s -> if (s.isEmpty()) null else "$i:${s.sorted().joinToString(" ")}" }.joinToString(";"),
            p.hints.toString(),
            r.less.joinToString(";") { "${it.first} ${it.second}" },
        ).joinToString("\n")
    }

    fun decode(text: String): LogicProgress? = try {
        require(text.length <= 60_000)
        val l = text.split('\n')
        // Version 1 had no Futoshiki signs line.
        require((l[0] == "1" && l.size == 15) || (l[0] == VERSION && l.size == 16))
        val rows = l[4].toInt(); val cols = l[5].toInt()
        require(rows in 2..21 && cols in 2..21)
        fun ints(s: String) = s.split(',').map(String::toInt)
        fun cells(s: String) = s.split(' ').map(String::toInt)
        val open = l[7].map { when (it) { '1' -> true; '0' -> false; else -> throw IllegalArgumentException("bad cell") } }
        val cages = if (l[10].isEmpty()) emptyList() else l[10].split(';').map { part ->
            val f = part.split(':'); require(f.size == 3)
            MathCage(cells(f[0]), CageOp.valueOf(f[1]), f[2].toInt())
        }
        val runs = if (l[11].isEmpty()) emptyList() else l[11].split(';').map { part ->
            val f = part.split(':'); require(f.size == 3 && f[2] in listOf("A", "D"))
            SumRun(cells(f[0]), f[1].toInt(), f[2] == "A")
        }
        val less = if (l.size < 16 || l[15].isEmpty()) emptyList() else l[15].split(';').map { part ->
            val f = cells(part); require(f.size == 2); f[0] to f[1]
        }
        val rules = LogicRules(LogicKind.valueOf(l[1]), rows, cols, l[6].toInt(), open, cages, runs, less)
        val puzzle = LogicPuzzle(rules, LogicLevel.valueOf(l[2]), l[3].toLong(), ints(l[8]), ints(l[9]))
        val notes = MutableList(rows * cols) { emptySet<Int>() }
        if (l[13].isNotEmpty()) l[13].split(';').forEach { part ->
            val f = part.split(':'); require(f.size == 2)
            val i = f[0].toInt(); require(i in notes.indices && notes[i].isEmpty())
            notes[i] = cells(f[1]).toSet()
        }
        LogicProgress(puzzle, ints(l[12]), notes, l[14].toInt())
    } catch (_: IllegalArgumentException) { null } catch (_: IndexOutOfBoundsException) { null }
}

object LogicGuide {
    fun rules(kind: LogicKind): String = when (kind) {
        LogicKind.SAMURAI -> "Five 9×9 Sudoku grids overlap at their corner boxes. Every row, column and 3×3 box of each grid " +
            "holds 1–9 once, so digits in a shared corner box count for both grids. Tap a grid on the map to work on it."
        LogicKind.KENKEN -> "Fill the grid so every row and column holds each digit once. The digits in each outlined cage must " +
            "combine to its target with the shown operation: + add, − subtract, × multiply, ÷ divide (larger by smaller). " +
            "A cage without an operation is simply that digit. Digits may repeat inside a cage if they are in different rows and columns."
        LogicKind.FUTOSHIKI -> "Fill the grid so every row and column holds each digit once. Every sign between two squares must " +
            "hold: the square at the open side of < or > has the larger digit. Some squares may start filled."
        LogicKind.KAKURO -> "Fill the white squares with 1–9. Each run of white squares must add up to the clue on its left " +
            "(across) or above it (down), and no digit may repeat within a run."
    }

    fun levelDescription(kind: LogicKind, level: LogicLevel): String = when (kind) {
        LogicKind.SAMURAI -> "5 overlapping 9×9 grids · " + listOf("about 215 givens", "about 185 givens", "about 160 givens", "as few givens as unique allows")[level.ordinal]
        LogicKind.KENKEN -> "${4 + level.ordinal}×${4 + level.ordinal} · cages up to ${listOf(2, 3, 4, 4)[level.ordinal]} cells · " +
            if (level == LogicLevel.EASY) "+ and − only" else "+ − × ÷"
        LogicKind.FUTOSHIKI -> "${4 + level.ordinal}×${4 + level.ordinal} · " +
            listOf("many signs and givens", "fewer signs", "sparse signs", "no givens, fewest signs")[level.ordinal]
        LogicKind.KAKURO -> "${5 + 2 * level.ordinal}×${5 + 2 * level.ordinal} play area · runs of 2–9 squares"
    }
}
