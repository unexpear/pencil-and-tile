package com.simplegamegen.sudoku.arcade

import kotlin.random.Random

enum class Slide { UP, DOWN, LEFT, RIGHT }

// ---------------- 2048 ----------------

/**
 * 2048 on a [size]×[size] board. [spawns] counts new tiles so far, which makes
 * every spawn reproducible from the seed. [goal] is the winning tile for the size.
 */
data class Game2048(
    val setting: Int,
    val seed: Long,
    val tiles: List<Int>,
    val score: Int = 0,
    val spawns: Int = 0,
    val reached: Boolean = false,
) {
    val size: Int get() = SIZES[setting]
    val goal: Int get() = GOALS[setting]

    init {
        require(setting in SIZES.indices && tiles.size == size * size && score >= 0 && spawns >= 0)
        require(tiles.all { it == 0 || (it >= 2 && it and (it - 1) == 0) })
    }

    val best: Int get() = tiles.max()
    val over: Boolean get() = tiles.none { it == 0 } && Slide.entries.none { slideOnly(it) != null }

    /** Slides and merges; returns null when nothing moves. */
    fun slide(dir: Slide): Game2048? = slideOnly(dir)?.spawn()

    private fun slideOnly(dir: Slide): Game2048? {
        val n = size
        val next = tiles.toMutableList()
        var gained = 0
        for (line in 0 until n) {
            val cells = (0 until n).map { k ->
                when (dir) {
                    Slide.LEFT -> line * n + k
                    Slide.RIGHT -> line * n + (n - 1 - k)
                    Slide.UP -> k * n + line
                    Slide.DOWN -> (n - 1 - k) * n + line
                }
            }
            val values = cells.map { tiles[it] }.filter { it != 0 }
            val merged = mutableListOf<Int>()
            var i = 0
            while (i < values.size) {
                if (i + 1 < values.size && values[i] == values[i + 1]) { merged += values[i] * 2; gained += values[i] * 2; i += 2 }
                else { merged += values[i]; i++ }
            }
            cells.forEachIndexed { k, cell -> next[cell] = merged.getOrElse(k) { 0 } }
        }
        if (next == tiles) return null
        return copy(tiles = next, score = score + gained, reached = reached || next.any { it >= goal })
    }

    private fun spawn(): Game2048 {
        val empty = tiles.indices.filter { tiles[it] == 0 }
        if (empty.isEmpty()) return this
        val random = Random(seed * 1_000_003 + spawns)
        val cell = empty[random.nextInt(empty.size)]
        return copy(tiles = tiles.toMutableList().also { it[cell] = if (random.nextInt(10) == 0) 4 else 2 }, spawns = spawns + 1)
    }

    companion object {
        val SIZES = listOf(3, 4, 5, 6)
        val GOALS = listOf(512, 2048, 4096, 8192)
        fun start(seed: Long, setting: Int): Game2048 =
            Game2048(setting, seed, List(SIZES[setting].let { it * it }) { 0 }).spawn().spawn()
    }
}

object Game2048Codec {
    fun encode(g: Game2048) = listOf("1", g.setting.toString(), g.seed.toString(), g.tiles.joinToString(","), g.score.toString(),
        g.spawns.toString(), if (g.reached) "1" else "0").joinToString("\n")
    fun decode(text: String): Game2048? = try {
        val l = text.split('\n'); require(l.size == 7 && l[0] == "1" && l[6] in listOf("0", "1"))
        Game2048(l[1].toInt(), l[2].toLong(), l[3].split(',').map(String::toInt), l[4].toInt(), l[5].toInt(), l[6] == "1")
    } catch (_: IllegalArgumentException) { null }
}

// ---------------- Tetras ----------------

/** One falling piece: [type] 0–6 (I, O, T, S, Z, J, L), rotation 0–3, and board position of its 4×4 box. */
data class Piece(val type: Int, val rotation: Int, val x: Int, val y: Int) {
    fun cells(): List<Pair<Int, Int>> = SHAPES[type][rotation].map { (dx, dy) -> x + dx to y + dy }

    companion object {
        private fun shape(vararg rows: String) = rows.flatMapIndexed { y, row -> row.mapIndexedNotNull { x, ch -> if (ch == '#') x to y else null } }
        private fun rotations(vararg first: String): List<List<Pair<Int, Int>>> {
            val base = shape(*first)
            val size = if (first.size == 4) 4 else 3
            return (0 until 4).runningFold(base) { cells, _ -> cells.map { (x, y) -> size - 1 - y to x } }.take(4)
        }
        val SHAPES: List<List<List<Pair<Int, Int>>>> = listOf(
            rotations("....", "####", "....", "...."),
            List(4) { shape(".##", ".##") },
            rotations(".#.", "###", "..."),
            rotations(".##", "##.", "..."),
            rotations("##.", ".##", "..."),
            rotations("#..", "###", "..."),
            rotations("..#", "###", "..."),
        )
        val NAMES = listOf("I", "O", "T", "S", "Z", "J", "L")
    }
}

/**
 * Falling-block game on a 10×20 board. Cells hold 0 or piece type + 1.
 * Pieces come from seeded 7-bags so a save restores the same sequence.
 */
data class TetrasGame(
    val setting: Int,
    val seed: Long,
    val board: List<Int> = List(W * H) { 0 },
    val piece: Piece? = null,
    val index: Int = 0,
    val score: Int = 0,
    val lines: Int = 0,
    val over: Boolean = false,
) {
    init {
        require(setting in 0..3 && board.size == W * H && board.all { it in 0..7 } && index >= 0 && score >= 0 && lines >= 0)
        piece?.let { p -> require(p.type in 0..6 && p.rotation in 0..3 && fits(p)) }
    }

    val level: Int get() = START_LEVELS[setting] + lines / 10
    /** Milliseconds between gravity steps. */
    val interval: Long get() = maxOf(70L, 800L - level * 65L)
    val next: List<Int> get() = (1..3).map { typeAt(seed, index + it) }

    fun fits(p: Piece): Boolean = p.cells().all { (x, y) -> x in 0 until W && y < H && (y < 0 || board[y * W + x] == 0) }

    fun ghost(): Piece? = piece?.let { var g = it; while (fits(g.copy(y = g.y + 1))) g = g.copy(y = g.y + 1); g }

    fun shift(dx: Int): TetrasGame? {
        val p = piece ?: return null
        if (over) return null
        val moved = p.copy(x = p.x + dx)
        return if (fits(moved)) copy(piece = moved) else null
    }

    /** Rotates clockwise, trying small sideways and upward kicks near walls and stacks. */
    fun rotate(): TetrasGame? {
        val p = piece ?: return null
        if (over || p.type == 1) return null
        val turned = p.copy(rotation = (p.rotation + 1) % 4)
        for ((dx, dy) in listOf(0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1)) {
            val kicked = turned.copy(x = turned.x + dx, y = turned.y + dy)
            if (fits(kicked)) return copy(piece = kicked)
        }
        return null
    }

    /** Gravity step: moves down one row or locks the piece. */
    fun tick(): TetrasGame {
        if (over) return this
        val p = piece ?: return spawn()
        val down = p.copy(y = p.y + 1)
        return if (fits(down)) copy(piece = down) else lock(p)
    }

    fun softDrop(): TetrasGame? {
        val p = piece ?: return null
        if (over) return null
        val down = p.copy(y = p.y + 1)
        return if (fits(down)) copy(piece = down, score = score + 1) else lock(p)
    }

    fun hardDrop(): TetrasGame? {
        val p = piece ?: return null
        if (over) return null
        val g = ghost()!!
        return copy(score = score + 2 * (g.y - p.y)).lock(g)
    }

    private fun lock(p: Piece): TetrasGame {
        val cells = p.cells()
        if (cells.any { it.second < 0 }) return copy(piece = null, over = true)
        val grid = board.toMutableList()
        cells.forEach { (x, y) -> grid[y * W + x] = p.type + 1 }
        val full = (0 until H).filter { y -> (0 until W).all { grid[y * W + it] != 0 } }
        val kept = (0 until H).filter { it !in full }.flatMap { y -> grid.subList(y * W, y * W + W) }
        val cleared = List(full.size * W) { 0 } + kept
        val gained = listOf(0, 100, 300, 500, 800)[full.size] * (level + 1)
        return copy(board = cleared, piece = null, score = score + gained, lines = lines + full.size).spawn()
    }

    private fun spawn(): TetrasGame {
        val type = typeAt(seed, index)
        val p = Piece(type, 0, 3, if (type == 0) -1 else 0)
        return if (fits(p)) copy(piece = p, index = index + 1) else copy(piece = null, over = true)
    }

    companion object {
        const val W = 10
        const val H = 20
        val START_LEVELS = listOf(0, 3, 6, 9)
        fun typeAt(seed: Long, i: Int): Int = (0..6).shuffled(Random(seed * 31 + i / 7)).get(i % 7)
        fun start(seed: Long, setting: Int) = TetrasGame(setting, seed).spawn()
    }
}

object TetrasCodec {
    fun encode(g: TetrasGame) = listOf("1", g.setting.toString(), g.seed.toString(), g.board.joinToString(""),
        g.piece?.let { "${it.type},${it.rotation},${it.x},${it.y}" } ?: "", g.index.toString(), g.score.toString(),
        g.lines.toString(), if (g.over) "1" else "0").joinToString("\n")
    fun decode(text: String): TetrasGame? = try {
        val l = text.split('\n'); require(l.size == 9 && l[0] == "1" && l[8] in listOf("0", "1"))
        val piece = if (l[4].isEmpty()) null else l[4].split(',').map(String::toInt).let { require(it.size == 4); Piece(it[0], it[1], it[2], it[3]) }
        TetrasGame(l[1].toInt(), l[2].toLong(), l[3].map { it.digitToInt() }, piece, l[5].toInt(), l[6].toInt(), l[7].toInt(), l[8] == "1")
    } catch (_: IllegalArgumentException) { null } catch (_: IndexOutOfBoundsException) { null }
}
