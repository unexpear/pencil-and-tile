package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.solver.checkpoint
import java.util.ArrayDeque
import kotlin.random.Random

/** A rectangle on the Klotski board. The big block is 2×2 and has id 0. */
data class KlotskiBlock(
    val id: Int,
    val row: Int,
    val col: Int,
    val height: Int,
    val width: Int,
) {
    init {
        require(id >= 0 && height in 1..2 && width in 1..2 && row >= 0 && col >= 0)
    }

    fun cells(): List<Pair<Int, Int>> = buildList {
        for (r in row until row + height) for (c in col until col + width) add(r to c)
    }
}

/**
 * Klotski on a 4×5 board. Slide one block one cell at a time into empty space.
 * The 2×2 block wins when it sits on the bottom opening.
 */
data class Klotski(
    val setting: Int,
    val seed: Long,
    val blocks: List<KlotskiBlock>,
    val solutionLength: Int,
    val moves: Int = 0,
    val hints: Int = 0,
) {
    init {
        require(setting in 0 until LEVELS && solutionLength >= 0 && moves >= 0 && hints >= 0)
        require(blocks.isNotEmpty() && blocks[0].id == 0 && blocks[0].width == 2 && blocks[0].height == 2)
        require(blocks.map { it.id } == blocks.indices.toList())
        require(KlotskiRules.legal(blocks))
    }

    val complete: Boolean get() = KlotskiRules.won(blocks[0])

    /** Slides [id] one cell. [dr] and [dc] are −1, 0, or 1, and one of them is 0. */
    fun slide(id: Int, dr: Int, dc: Int): Klotski? {
        if (complete) return null
        val next = KlotskiRules.slide(blocks, id, dr, dc) ?: return null
        return copy(blocks = next, moves = moves + 1)
    }

    /** Slides [id] up to [cells] steps. Each cell counts as a move. */
    fun slideBy(id: Int, dr: Int, dc: Int, cells: Int): Klotski? {
        if (complete || cells <= 0 || (dr == 0) == (dc == 0)) return null
        var game = this
        var stepped = 0
        repeat(cells) {
            val next = game.slide(id, dr, dc) ?: return if (stepped == 0) null else game
            game = next
            stepped++
        }
        return game
    }

    fun hint(): Klotski? {
        if (complete) return null
        val step = KlotskiRules.nextMove(blocks) ?: return null
        return slide(step.first, step.second, step.third)?.copy(hints = hints + 1)
    }

    companion object {
        const val COLS = 4
        const val ROWS = 5
        val LEVELS = LogicLevel.entries.size
        private val LENGTH = listOf(4..10, 12..24, 28..48)

        fun generate(seed: Long, level: LogicLevel): Klotski {
            if (level == LogicLevel.EXPERT) {
                val classic = KlotskiRules.classic()
                val dist = KlotskiRules.distance(classic)
                check(dist > 0)
                return Klotski(level.ordinal, seed, classic, dist)
            }
            val random = Random(seed)
            val want = LENGTH[level.ordinal]
            var best: Pair<List<KlotskiBlock>, Int>? = null
            repeat(8) {
                checkpoint()
                val picked = KlotskiRules.pickAtDistance(KlotskiRules.goal(), want, random) ?: return@repeat
                if (KlotskiRules.won(picked.first[0])) return@repeat
                val dist = KlotskiRules.distance(picked.first, want.last)
                if (dist < 2) return@repeat
                if (dist in want) return Klotski(level.ordinal, seed, picked.first, dist)
                if (best == null || dist > best!!.second) best = picked.first to dist
            }
            val fallback = best ?: parked()
            check(fallback.second >= 2)
            return Klotski(level.ordinal, seed, fallback.first, fallback.second)
        }

        /** A few slides off the opening, used when a search misses its band. */
        private fun parked(): Pair<List<KlotskiBlock>, Int> {
            var blocks = KlotskiRules.goal()
            blocks = KlotskiRules.slide(blocks, 5, -1, 0) ?: blocks
            blocks = KlotskiRules.slide(blocks, 6, -1, 0) ?: blocks
            blocks = KlotskiRules.slide(blocks, 7, -1, 0) ?: blocks
            blocks = KlotskiRules.slide(blocks, 0, -1, 0) ?: blocks
            val dist = KlotskiRules.distance(blocks)
            return blocks to dist
        }
    }
}

object KlotskiRules {
    private val DIRS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    fun won(target: KlotskiBlock): Boolean = target.row == Klotski.ROWS - 2 && target.col == 1

    fun legal(blocks: List<KlotskiBlock>): Boolean {
        val seen = HashSet<Int>()
        for (block in blocks) {
            for ((r, c) in block.cells()) {
                if (r !in 0 until Klotski.ROWS || c !in 0 until Klotski.COLS) return false
                if (!seen.add(r * Klotski.COLS + c)) return false
            }
        }
        return seen.size == Klotski.ROWS * Klotski.COLS - 2
    }

    /** The familiar opening. The big block starts at the top. */
    fun classic(): List<KlotskiBlock> = listOf(
        KlotskiBlock(0, 0, 1, 2, 2),
        KlotskiBlock(1, 0, 0, 2, 1),
        KlotskiBlock(2, 0, 3, 2, 1),
        KlotskiBlock(3, 2, 0, 2, 1),
        KlotskiBlock(4, 2, 3, 2, 1),
        KlotskiBlock(5, 2, 1, 1, 2),
        KlotskiBlock(6, 3, 1, 1, 1),
        KlotskiBlock(7, 3, 2, 1, 1),
        KlotskiBlock(8, 4, 0, 1, 1),
        KlotskiBlock(9, 4, 3, 1, 1),
    )

    /** A finished board: the big block is already on the opening. */
    fun goal(): List<KlotskiBlock> = listOf(
        KlotskiBlock(0, 3, 1, 2, 2),
        KlotskiBlock(1, 0, 0, 2, 1),
        KlotskiBlock(2, 0, 3, 2, 1),
        KlotskiBlock(3, 2, 0, 2, 1),
        KlotskiBlock(4, 2, 3, 2, 1),
        KlotskiBlock(5, 1, 1, 1, 2),
        KlotskiBlock(6, 2, 1, 1, 1),
        KlotskiBlock(7, 2, 2, 1, 1),
        KlotskiBlock(8, 4, 0, 1, 1),
        KlotskiBlock(9, 4, 3, 1, 1),
    )

    fun slide(blocks: List<KlotskiBlock>, id: Int, dr: Int, dc: Int): List<KlotskiBlock>? {
        if (id !in blocks.indices || (dr == 0) == (dc == 0) || (dr != 0 && dc != 0)) return null
        if (dr !in -1..1 || dc !in -1..1) return null
        val block = blocks[id]
        val nr = block.row + dr
        val nc = block.col + dc
        val taken = HashSet<Int>()
        for (other in blocks) if (other.id != id) for ((r, c) in other.cells()) taken.add(r * Klotski.COLS + c)
        for (r in nr until nr + block.height) for (c in nc until nc + block.width) {
            if (r !in 0 until Klotski.ROWS || c !in 0 until Klotski.COLS) return null
            if (r * Klotski.COLS + c in taken) return null
        }
        return blocks.map { if (it.id == id) it.copy(row = nr, col = nc) else it }
    }

    fun moves(blocks: List<KlotskiBlock>): List<Triple<Int, Int, Int>> = buildList {
        for (block in blocks) for ((dr, dc) in DIRS) {
            if (slide(blocks, block.id, dr, dc) != null) add(Triple(block.id, dr, dc))
        }
    }

    fun distance(blocks: List<KlotskiBlock>, maxDist: Int = 120): Int {
        if (won(blocks[0])) return 0
        return search(blocks, maxDist).second
    }

    fun nextMove(blocks: List<KlotskiBlock>): Triple<Int, Int, Int>? {
        if (won(blocks[0])) return null
        return search(blocks, 120).first
    }

    /** A layout whose shortest path into the opening falls in [want]. */
    fun pickAtDistance(
        start: List<KlotskiBlock>,
        want: IntRange,
        random: Random,
        nodeCap: Int = 80_000,
    ): Pair<List<KlotskiBlock>, Int>? {
        val queue = ArrayDeque<List<KlotskiBlock>>()
        val dist = HashMap<Long, Int>()
        queue.add(start)
        dist[key(start)] = 0
        val hits = ArrayList<Pair<List<KlotskiBlock>, Int>>()
        while (queue.isNotEmpty() && dist.size < nodeCap && hits.size < 24) {
            checkpoint()
            val blocks = queue.removeFirst()
            val soFar = dist.getValue(key(blocks))
            if (soFar > want.last) continue
            for ((id, dr, dc) in moves(blocks)) {
                val next = slide(blocks, id, dr, dc) ?: continue
                val code = key(next)
                if (code in dist) continue
                val step = soFar + 1
                dist[code] = step
                if (step in want && !won(next[0])) hits.add(next to step)
                queue.add(next)
            }
        }
        return hits.randomOrNull(random)
    }

    private fun search(start: List<KlotskiBlock>, maxDist: Int): Pair<Triple<Int, Int, Int>?, Int> {
        val queue = ArrayDeque<List<KlotskiBlock>>()
        val dist = HashMap<Long, Int>()
        val parent = HashMap<Long, Long>()
        val how = HashMap<Long, Triple<Int, Int, Int>>()
        val root = key(start)
        queue.add(start)
        dist[root] = 0
        var found: Long? = null
        while (queue.isNotEmpty()) {
            checkpoint()
            val blocks = queue.removeFirst()
            val code = key(blocks)
            val soFar = dist.getValue(code)
            if (won(blocks[0])) {
                found = code
                break
            }
            if (soFar >= maxDist) continue
            for ((id, dr, dc) in moves(blocks)) {
                val next = slide(blocks, id, dr, dc) ?: continue
                val nextCode = key(next)
                if (nextCode in dist) continue
                dist[nextCode] = soFar + 1
                parent[nextCode] = code
                how[nextCode] = Triple(id, dr, dc)
                queue.add(next)
            }
        }
        val end = found ?: return null to -1
        var cursor = end
        var first = how.getValue(cursor)
        while (parent.getValue(cursor) != root) {
            cursor = parent.getValue(cursor)
            first = how.getValue(cursor)
        }
        if (end == root) return null to 0
        return first to dist.getValue(end)
    }

    private fun key(blocks: List<KlotskiBlock>): Long {
        val big = blocks.first { it.width == 2 && it.height == 2 }
        val vertical = blocks.filter { it.height == 2 && it.width == 1 }.map { it.row * Klotski.COLS + it.col }.sorted()
        val horizontal = blocks.filter { it.height == 1 && it.width == 2 }.map { it.row * Klotski.COLS + it.col }.sorted()
        val small = blocks.filter { it.height == 1 && it.width == 1 }.map { it.row * Klotski.COLS + it.col }.sorted()
        var code = (big.row * Klotski.COLS + big.col).toLong()
        for (spot in vertical + horizontal + small) code = code * 20L + spot
        return code
    }
}

object KlotskiCodec {
    fun encode(g: Klotski) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.solutionLength.toString(),
        g.moves.toString(),
        g.hints.toString(),
        g.blocks.joinToString(";") { "${it.id},${it.row},${it.col},${it.height},${it.width}" },
    ).joinToString("\n")

    fun decode(text: String): Klotski? = try {
        val lines = text.split('\n')
        require(lines.size == 7 && lines[0] == "1")
        val blocks = lines[6].split(";").map { part ->
            val n = part.split(",").map { it.toInt() }
            KlotskiBlock(n[0], n[1], n[2], n[3], n[4])
        }
        Klotski(lines[1].toInt(), lines[2].toLong(), blocks, lines[3].toInt(), lines[4].toInt(), lines[5].toInt())
    } catch (_: IllegalArgumentException) {
        null
    }
}
