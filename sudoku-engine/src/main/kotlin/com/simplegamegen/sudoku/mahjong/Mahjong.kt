package com.simplegamegen.sudoku.mahjong

import com.simplegamegen.sudoku.solver.checkpoint
import com.simplegamegen.sudoku.words.WordDifficulty
import java.math.BigInteger
import kotlin.math.abs
import kotlin.random.Random

/** Coordinates use half-tile units; a tile occupies a 2×2 rectangle. */
data class MahjongTile(val id: Int, val x: Int, val y: Int, val z: Int, val face: Int)
data class TilePair(val a: Int, val b: Int)
data class MahjongDeal(val tiles: List<MahjongTile>, val solution: List<TilePair>, val difficulty: WordDifficulty, val seed: Long) {
    init {
        require(tiles.size in 2..144 && tiles.size % 2 == 0)
        require(tiles.map { it.id } == tiles.indices.toList())
        require(tiles.all { it.x in 0..28 && it.y in 0..20 && it.z in 0..4 && it.face in 0..33 })
        require(tiles.none { a -> tiles.any { b -> a.id != b.id && a.z == b.z && abs(a.x - b.x) < 2 && abs(a.y - b.y) < 2 } })
        require(solution.size * 2 == tiles.size)
        require(MahjongRules.replay(tiles, solution)?.size == tiles.size) { "Deal must carry a valid clearing sequence" }
    }
}

object MahjongRules {
    fun free(tiles: List<MahjongTile>, removed: Set<Int>, id: Int): Boolean {
        val tile = tiles.getOrNull(id) ?: return false
        if (id in removed) return false
        val others = tiles.filter { it.id != id && it.id !in removed }
        if (others.any { it.z > tile.z && abs(it.x - tile.x) < 2 && abs(it.y - tile.y) < 2 }) return false
        val left = others.any { it.z == tile.z && it.x + 2 == tile.x && abs(it.y - tile.y) < 2 }
        val right = others.any { it.z == tile.z && it.x == tile.x + 2 && abs(it.y - tile.y) < 2 }
        return !left || !right
    }

    fun legal(tiles: List<MahjongTile>, removed: Set<Int>, pair: TilePair): Boolean = pair.a != pair.b &&
        free(tiles, removed, pair.a) && free(tiles, removed, pair.b) && tiles[pair.a].face == tiles[pair.b].face

    fun pairs(tiles: List<MahjongTile>, removed: Set<Int>): List<TilePair> {
        val available = tiles.filter { free(tiles, removed, it.id) }
        return buildList {
            for (a in available.indices) for (b in a + 1 until available.size)
                if (available[a].face == available[b].face) add(TilePair(available[a].id, available[b].id))
        }
    }

    fun replay(tiles: List<MahjongTile>, moves: List<TilePair>, initial: Set<Int> = emptySet()): Set<Int>? {
        if (initial.any { it !in tiles.indices }) return null
        val removed = initial.toMutableSet()
        for (pair in moves) {
            if (!legal(tiles, removed, pair)) return null
            removed += pair.a; removed += pair.b
        }
        return removed
    }
}

object MahjongGenerator {
    fun generate(seed: Long, difficulty: WordDifficulty): MahjongDeal {
        checkpoint()
        val random = Random(seed)
        val positions = mutableListOf<MahjongTile>()
        fun layer(cols: Int, rows: Int, x: Int, y: Int, z: Int) {
            for (r in 0 until rows) for (c in 0 until cols) positions += MahjongTile(positions.size, x + 2 * c, y + 2 * r, z, 0)
        }
        val offset = random.nextInt(2)
        when (difficulty) {
            WordDifficulty.EASY -> layer(6, 4, 0, 0, 0)
            WordDifficulty.MEDIUM -> { layer(8, 4, 0, 0, 0); layer(4, 4, 3 + offset, 0, 1) }
            WordDifficulty.HARD -> { layer(8, 6, 0, 0, 0); layer(6, 4, 1 + offset, 1 + offset, 1) }
            WordDifficulty.EXPERT -> { layer(10, 6, 0, 0, 0); layer(6, 4, 3 + offset, 1, 1); layer(4, 3, 5 + offset, 2, 2) }
        }
        val removed = mutableSetOf<Int>()
        val witness = mutableListOf<TilePair>()
        while (removed.size < positions.size) {
            checkpoint()
            // Every row starts even. Removing its two free ends preserves that
            // invariant; at least one highest-layer row can always be cleared.
            val rows = positions.filter { MahjongRules.free(positions, removed, it.id) }
                .groupBy { it.z to it.y }.values.filter { it.size >= 2 }
            check(rows.isNotEmpty())
            val row = rows.random(random).sortedBy { it.x }
            val pair = TilePair(row.first().id, row.last().id)
            witness += pair
            removed += pair.a; removed += pair.b
        }
        val kinds = (0..33).shuffled(random).take(if (difficulty == WordDifficulty.EASY) witness.size else witness.size / 2)
        val faces = List(witness.size) { kinds[it % kinds.size] }.shuffled(random)
        val tiles = positions.toMutableList()
        witness.forEachIndexed { i, pair ->
            tiles[pair.a] = tiles[pair.a].copy(face = faces[i])
            tiles[pair.b] = tiles[pair.b].copy(face = faces[i])
        }
        return MahjongDeal(tiles, witness, difficulty, seed)
    }
}

data class MahjongProgress(val deal: MahjongDeal, val moves: List<TilePair> = emptyList(), val hints: Int = 0) {
    init { require(hints >= 0 && MahjongRules.replay(deal.tiles, moves) != null) }
    val removed: Set<Int> get() = moves.flatMap { listOf(it.a, it.b) }.toSet()
    val complete: Boolean get() = removed.size == deal.tiles.size
    fun play(pair: TilePair): MahjongProgress = if (MahjongRules.legal(deal.tiles, removed, pair)) copy(moves = moves + pair) else this
    fun undo(): MahjongProgress = if (moves.isEmpty()) this else copy(moves = moves.dropLast(1))
}

enum class MahjongVerdict { SOLVED, UNSOLVABLE, LIMIT_REACHED }
data class MahjongSearch(val verdict: MahjongVerdict, val path: List<TilePair>, val nodes: Int)

object MahjongSolver {
    /** Reuse a replayable proof when possible; otherwise search and memoize dead states. */
    fun solve(progress: MahjongProgress, maxNodes: Int = 100_000): MahjongSearch {
        require(maxNodes >= 0)
        checkpoint()
        val tiles = progress.deal.tiles
        val removed = progress.removed
        val remainingProof = progress.deal.solution.filter { it.a !in removed || it.b !in removed }
        if (MahjongRules.replay(tiles, remainingProof, removed)?.size == tiles.size)
            return MahjongSearch(MahjongVerdict.SOLVED, remainingProof, 0)
        val above = tiles.map { a -> mask(tiles.filter { it.z > a.z && abs(it.x - a.x) < 2 && abs(it.y - a.y) < 2 }.map { it.id }) }
        val left = tiles.map { a -> mask(tiles.filter { it.z == a.z && it.x + 2 == a.x && abs(it.y - a.y) < 2 }.map { it.id }) }
        val right = tiles.map { a -> mask(tiles.filter { it.z == a.z && it.x == a.x + 2 && abs(it.y - a.y) < 2 }.map { it.id }) }
        val dead = HashSet<BigInteger>()
        var nodes = 0
        var limited = false
        val path = mutableListOf<TilePair>()
        fun search(remaining: BigInteger): Boolean {
            checkpoint()
            if (remaining.signum() == 0) return true
            if (remaining in dead) return false
            if (nodes >= maxNodes) { limited = true; return false }
            nodes++
            val free = tiles.indices.filter { remaining.testBit(it) && remaining.and(above[it]).signum() == 0 &&
                (remaining.and(left[it]).signum() == 0 || remaining.and(right[it]).signum() == 0) }
            val choices = buildList {
                for (a in free.indices) for (b in a + 1 until free.size)
                    if (tiles[free[a]].face == tiles[free[b]].face) add(TilePair(free[a], free[b]))
            }.sortedByDescending { tiles[it.a].z + tiles[it.b].z }
            for (pair in choices) {
                path += pair
                if (search(remaining.clearBit(pair.a).clearBit(pair.b))) return true
                path.removeAt(path.lastIndex)
                if (limited) return false
            }
            dead += remaining
            return false
        }
        val solved = search(mask(tiles.indices.filter { it !in removed }))
        return MahjongSearch(if (solved) MahjongVerdict.SOLVED else if (limited) MahjongVerdict.LIMIT_REACHED else MahjongVerdict.UNSOLVABLE,
            if (solved) path.toList() else emptyList(), nodes)
    }
    private fun mask(ids: Iterable<Int>): BigInteger = ids.fold(BigInteger.ZERO) { mask, id -> mask.setBit(id) }
}

object MahjongSaveCodec {
    fun encode(p: MahjongProgress): String = listOf("1", p.deal.difficulty.name, p.deal.seed.toString(),
        p.deal.tiles.joinToString(";") { "${it.id},${it.x},${it.y},${it.z},${it.face}" },
        pairs(p.deal.solution), pairs(p.moves), p.hints.toString()).joinToString("\n")
    private fun pairs(moves: List<TilePair>) = moves.joinToString(";") { "${it.a},${it.b}" }
    fun decode(text: String): MahjongProgress? = try {
        require(text.length < 20000)
        val lines = text.split('\n')
        require(lines.size == 7 && lines[0] == "1")
        val tiles = lines[3].split(';').map { row ->
            val n = row.split(',').map(String::toInt); require(n.size == 5)
            MahjongTile(n[0], n[1], n[2], n[3], n[4])
        }
        fun moves(line: String) = line.split(';').filter(String::isNotEmpty).map { row ->
            val n = row.split(',').map(String::toInt); require(n.size == 2); TilePair(n[0], n[1])
        }
        MahjongProgress(MahjongDeal(tiles, moves(lines[4]), WordDifficulty.valueOf(lines[1]), lines[2].toLong()), moves(lines[5]), lines[6].toInt())
    } catch (_: IllegalArgumentException) { null }
}
