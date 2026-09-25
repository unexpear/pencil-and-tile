package com.simplegamegen.sudoku.tabletop

data class MinePreset(val width: Int, val height: Int, val mines: Int)
data class MineDeduction(val cell: Int, val mine: Boolean)
data class MinesState(
    val preset: MinePreset, val order: List<Int>, val mines: Set<Int> = emptySet(),
    val revealed: Set<Int> = emptySet(), val flags: Set<Int> = emptySet(), val exploded: Int? = null,
) : TableState {
    override val turn = 1
    val started get() = mines.isNotEmpty()
    override val result get() = when {
        exploded != null -> Result.LOST
        revealed.size == order.size - preset.mines -> Result.WON
        else -> Result.PLAYING
    }
    fun neighbors(cell: Int): List<Int> = buildList {
        for (dy in -1..1) for (dx in -1..1) {
            val x = cell % preset.width + dx; val y = cell / preset.width + dy
            if ((dx != 0 || dy != 0) && x in 0 until preset.width && y in 0 until preset.height) add(y * preset.width + x)
        }
    }
    fun number(cell: Int) = neighbors(cell).count { it in mines }
    override fun legalMoves(): List<Move> = if (result != Result.PLAYING) emptyList() else buildList {
        for (cell in order.indices) {
            if (cell !in revealed) { add(Move(Op.FLAG, cell)); if (cell !in flags) add(Move(Op.REVEAL, cell)) }
            else if (number(cell) > 0 && neighbors(cell).count { it in flags } == number(cell) && neighbors(cell).any { it !in flags && it !in revealed }) add(Move(Op.CHORD, cell))
        }
    }
    override fun play(move: Move): MinesState? {
        if (result != Result.PLAYING || move.from !in order.indices || move.to != 0 || move.count != 1) return null
        val cell = move.from
        if (move.op == Op.FLAG && cell !in revealed) return copy(flags = if (cell in flags) flags - cell else flags + cell)
        if (move.op == Op.REVEAL && cell !in revealed && cell !in flags) {
            val board = if (started) this else copy(mines = order.filter { it != cell && it !in neighbors(cell) }.take(preset.mines).toSet())
            return board.open(listOf(cell))
        }
        if (move.op == Op.CHORD && cell in revealed && number(cell) > 0 && neighbors(cell).count { it in flags } == number(cell)) {
            val targets = neighbors(cell).filter { it !in flags && it !in revealed }
            if (targets.isNotEmpty()) return open(targets)
        }
        return null
    }
    private fun open(cells: List<Int>): MinesState {
        val hit = cells.firstOrNull { it in mines }
        if (hit != null) return copy(exploded = hit)
        val seen = revealed.toMutableSet(); val queue = ArrayDeque(cells)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell in flags || !seen.add(cell)) continue
            if (number(cell) == 0) neighbors(cell).filter { it !in seen && it !in mines }.forEach(queue::addLast)
        }
        return copy(revealed = seen)
    }
    /** Uses only visible clues. User flags are not assumed correct. No hidden-cell peeking. */
    fun deduction(): MineDeduction? {
        if (!started || result != Result.PLAYING) return null
        val constraints = revealed.map { cell -> neighbors(cell).filter { it !in revealed }.toSet() to number(cell) }.filter { it.first.isNotEmpty() } +
            listOf(order.indices.filter { it !in revealed }.toSet() to preset.mines)
        for ((cells, n) in constraints) {
            if (n == 0) cells.firstOrNull { it !in flags }?.let { return MineDeduction(it, false) }
            if (n == cells.size) cells.firstOrNull { it !in flags }?.let { return MineDeduction(it, true) }
        }
        for ((a, na) in constraints) for ((b, nb) in constraints) if (a.size < b.size && b.containsAll(a)) {
            val diff = b - a; val count = nb - na
            if (count == 0 || count == diff.size) diff.firstOrNull { it !in flags }?.let { return MineDeduction(it, count > 0) }
        }
        return null
    }
    companion object {
        val presets = listOf(MinePreset(8, 8, 8), MinePreset(9, 9, 10), MinePreset(16, 16, 40), MinePreset(30, 16, 99))
        fun new(setting: Int, order: List<Int>) = MinesState(presets[setting], order)
    }
}
