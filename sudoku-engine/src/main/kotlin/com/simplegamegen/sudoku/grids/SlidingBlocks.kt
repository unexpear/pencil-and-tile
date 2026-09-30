package com.simplegamegen.sudoku.grids

import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.solver.checkpoint
import java.util.ArrayDeque
import kotlin.random.Random

/** One vehicle on the sliding-blocks board. Length is 2 or 3 cells. */
data class BlockCar(
    val id: Int,
    val row: Int,
    val col: Int,
    val length: Int,
    val horizontal: Boolean,
) {
    init {
        require(id >= 0 && length in 2..3 && row >= 0 && col >= 0)
    }

    fun cells(): List<Pair<Int, Int>> = if (horizontal) {
        (0 until length).map { row to (col + it) }
    } else {
        (0 until length).map { (row + it) to col }
    }
}

/**
 * Sliding blocks: a jammed grid of vehicles. Slide one out through the exit.
 * Vehicles are 2 or 3 cells, horizontal or vertical. A move slides one vehicle
 * any number of free cells along its axis. The target (id 0) is horizontal and
 * wins when its front reaches the exit on the right of [exitRow].
 */
data class SlidingBlocks(
    val setting: Int,
    val seed: Long,
    val size: Int,
    val exitRow: Int,
    val cars: List<BlockCar>,
    val solutionLength: Int,
    val moves: Int = 0,
    val hints: Int = 0,
) {
    init {
        require(setting in 0 until LEVELS && size == SIZE && exitRow in 0 until size)
        require(cars.isNotEmpty() && cars[0].id == 0 && cars[0].horizontal && cars[0].row == exitRow)
        require(cars.map { it.id }.toSet().size == cars.size)
        require(solutionLength >= 0 && moves >= 0 && hints >= 0)
        require(SlidingBlocksRules.legalLayout(size, cars))
    }

    val target: BlockCar get() = cars[0]
    val complete: Boolean get() = SlidingBlocksRules.won(size, target)

    /** Slides [carId] by [steps] cells along its axis (+ forward for right/down). */
    fun slide(carId: Int, steps: Int): SlidingBlocks? {
        if (complete || steps == 0) return null
        val next = SlidingBlocksRules.slide(size, cars, carId, steps) ?: return null
        return copy(cars = next, moves = moves + 1)
    }

    /** Slides [carId] as far as possible in the given sign direction (−1 or +1). */
    fun slideMax(carId: Int, sign: Int): SlidingBlocks? = slideBy(carId, sign, size)

    /**
     * Slides [carId] up to [cells] steps in [sign]'s direction (−1 or +1).
     * Stops at the first blocked cell, so a short drag does not jump the whole gap.
     */
    fun slideBy(carId: Int, sign: Int, cells: Int): SlidingBlocks? {
        if (complete || sign == 0 || cells <= 0) return null
        val s = sign.coerceIn(-1, 1)
        val span = SlidingBlocksRules.freeSpan(size, cars, carId) ?: return null
        val room = if (s > 0) span.second else span.first
        val steps = cells.coerceAtMost(room)
        if (steps == 0) return null
        return slide(carId, s * steps)
    }

    /** Plays the next vehicle move on a shortest solution from here. */
    fun hint(): SlidingBlocks? {
        if (complete) return null
        val step = SlidingBlocksRules.nextMove(size, exitRow, cars) ?: return null
        return slide(step.first, step.second)?.copy(hints = hints + 1)
    }

    companion object {
        const val SIZE = 6
        val LEVELS = LogicLevel.entries.size
        /** Shortest solution kept per difficulty, proved by search. */
        private val LENGTH = listOf(2..6, 3..8, 6..12, 8..14)
        /** Total vehicles (including the target) aimed for per level. */
        private val FLEET = listOf(4..5, 6..7, 8..9, 9..11)

        fun generate(seed: Long, level: LogicLevel): SlidingBlocks {
            val setting = level.ordinal
            val random = Random(seed)
            val want = LENGTH[setting]
            val fleet = FLEET[setting]
            var best: Pair<List<BlockCar>, Int>? = null
            repeat(12) {
                checkpoint()
                val solved = placeFleet(fleet.random(random), random) ?: return@repeat
                val picked = SlidingBlocksRules.pickAtDistance(SIZE, solved, want, random, 40_000) ?: return@repeat
                val cars = picked.first
                if (SlidingBlocksRules.won(SIZE, cars[0])) return@repeat
                val dist = SlidingBlocksRules.distance(SIZE, cars[0].row, cars, want.last)
                if (dist < 2) return@repeat
                if (dist in want) return SlidingBlocks(setting, seed, SIZE, cars[0].row, cars, dist)
                if (best == null || dist > best!!.second) best = cars to dist
            }
            val fallback = best ?: easySolvable(random)
            val cars = fallback.first
            val dist = fallback.second
            check(dist >= 2)
            return SlidingBlocks(setting, seed, SIZE, cars[0].row, cars, dist)
        }

        /**
         * Keeps [cars] only when a shortest path exists. Stores that length.
         * Returns null for unsolvable layouts (also used by tests).
         */
        fun accept(setting: Int, seed: Long, size: Int, cars: List<BlockCar>): SlidingBlocks? {
            if (size != SIZE || cars.isEmpty() || !cars[0].horizontal) return null
            if (!SlidingBlocksRules.legalLayout(size, cars)) return null
            val exitRow = cars[0].row
            val dist = SlidingBlocksRules.distance(size, exitRow, cars)
            if (dist < 0) return null
            return SlidingBlocks(setting, seed, size, exitRow, cars, dist)
        }

        /** Tiny solved scramble used when placement fails entirely. */
        private fun easySolvable(random: Random): Pair<List<BlockCar>, Int> {
            val exitRow = 2
            val solved = listOf(
                BlockCar(0, exitRow, SIZE - 2, 2, true),
                BlockCar(1, 0, 3, 2, false),
                BlockCar(2, 4, 1, 3, true),
                BlockCar(3, 1, 1, 2, false),
            )
            check(SlidingBlocksRules.legalLayout(SIZE, solved))
            val pick = SlidingBlocksRules.pickAtDistance(SIZE, solved, 2..8, random)
            if (pick != null) {
                val cars = pick.first
                val dist = SlidingBlocksRules.distance(SIZE, cars[0].row, cars)
                if (dist > 0) return cars to dist
            }
            val cars = listOf(
                BlockCar(0, exitRow, 0, 2, true),
                BlockCar(1, exitRow, 2, 2, false),
            )
            val dist = SlidingBlocksRules.distance(SIZE, exitRow, cars)
            check(dist >= 2)
            return cars to dist
        }

        /** Solved layout: target flush with the exit, then [count] − 1 blockers. */
        private fun placeFleet(count: Int, random: Random): List<BlockCar>? {
            val exitRow = (1..SIZE - 2).random(random)
            val targetLen = 2
            val cars = mutableListOf(BlockCar(0, exitRow, SIZE - targetLen, targetLen, true))
            var nextId = 1
            var guard = 0
            while (cars.size < count && guard++ < 300) {
                val horizontal = random.nextBoolean()
                val len = if (random.nextFloat() < 0.7f) 2 else 3
                val row = if (horizontal) (0 until SIZE).random(random)
                else (0..SIZE - len).random(random)
                val col = if (horizontal) (0..SIZE - len).random(random)
                else (0 until SIZE).random(random)
                // Do not cover the target's solved cells.
                val car = BlockCar(nextId, row, col, len, horizontal)
                if (car.cells().any { (r, c) -> r == exitRow && c >= SIZE - targetLen }) continue
                val trial = cars + car
                if (SlidingBlocksRules.legalLayout(SIZE, trial)) {
                    cars += car
                    nextId++
                }
            }
            return cars.takeIf { it.size == count && SlidingBlocksRules.won(SIZE, it[0]) }
        }
    }
}

object SlidingBlocksRules {
    fun won(size: Int, target: BlockCar): Boolean =
        target.horizontal && target.col + target.length == size

    fun legalLayout(size: Int, cars: List<BlockCar>): Boolean {
        val seen = HashSet<Int>()
        for (car in cars) {
            for ((r, c) in car.cells()) {
                if (r !in 0 until size || c !in 0 until size) return false
                val key = r * size + c
                if (!seen.add(key)) return false
            }
        }
        return true
    }

    /** How many free cells the car can move back (first) and forward (second). */
    fun freeSpan(size: Int, cars: List<BlockCar>, carId: Int): Pair<Int, Int>? {
        val car = cars.firstOrNull { it.id == carId } ?: return null
        val occupied = occupancy(size, cars, except = carId)
        var back = 0
        var forward = 0
        if (car.horizontal) {
            while (car.col - back - 1 >= 0 && !occupied[car.row * size + (car.col - back - 1)]) back++
            while (car.col + car.length + forward < size &&
                !occupied[car.row * size + (car.col + car.length + forward)]
            ) forward++
        } else {
            while (car.row - back - 1 >= 0 && !occupied[(car.row - back - 1) * size + car.col]) back++
            while (car.row + car.length + forward < size &&
                !occupied[(car.row + car.length + forward) * size + car.col]
            ) forward++
        }
        return back to forward
    }

    fun slide(size: Int, cars: List<BlockCar>, carId: Int, steps: Int): List<BlockCar>? {
        if (steps == 0) return null
        val span = freeSpan(size, cars, carId) ?: return null
        if (steps > 0 && steps > span.second) return null
        if (steps < 0 && -steps > span.first) return null
        return cars.map { car ->
            if (car.id != carId) car
            else if (car.horizontal) car.copy(col = car.col + steps)
            else car.copy(row = car.row + steps)
        }.also { next ->
            // Target may sit flush with the exit (front on last column); still on-grid.
            if (!legalLayout(size, next)) return null
        }
    }

    /** Every legal one-move landing: (carId, steps). */
    fun allMoves(size: Int, cars: List<BlockCar>): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (car in cars) {
            val span = freeSpan(size, cars, car.id) ?: continue
            for (s in 1..span.first) out += car.id to -s
            for (s in 1..span.second) out += car.id to s
        }
        return out
    }

    /**
     * BFS outward from a solved layout and pick a random state whose distance
     * back to the exit lies in [band]. If the band is empty, returns the farthest
     * non-solved state found (still proven solvable).
     */
    fun pickAtDistance(
        size: Int,
        solved: List<BlockCar>,
        band: IntRange,
        random: Random,
        nodeCap: Int = 12_000,
        takeFarthest: Boolean = false,
    ): Pair<List<BlockCar>, Int>? {
        require(won(size, solved[0]))
        val queue = ArrayDeque<List<BlockCar>>()
        val dist = HashMap<String, Int>()
        queue.add(solved)
        dist[key(solved)] = 0
        val hits = ArrayList<Pair<List<BlockCar>, Int>>()
        var farthest: Pair<List<BlockCar>, Int>? = null
        var nodes = 0
        val cap = band.last.coerceAtLeast(1)
        while (queue.isNotEmpty()) {
            if (++nodes > nodeCap) break
            checkpoint()
            val cur = queue.removeFirst()
            val d = dist[key(cur)]!!
            if (d > 0 && (farthest == null || d > farthest.second)) farthest = cur to d
            if (d in band && !won(size, cur[0])) hits += cur to d
            if (d >= cap) continue
            for ((id, steps) in allMoves(size, cur)) {
                val next = slide(size, cur, id, steps) ?: continue
                val k = key(next)
                if (k in dist) continue
                dist[k] = d + 1
                queue.add(next)
            }
        }
        if (!takeFarthest && hits.isNotEmpty()) return hits.random(random)
        return farthest
    }

    /**
     * Shortest number of moves to clear the target, or −1 if impossible.
     * A move is one vehicle sliding any distance along its axis.
     */
    fun distance(size: Int, exitRow: Int, cars: List<BlockCar>, maxDist: Int = 40): Int {
        if (cars.isEmpty() || cars[0].row != exitRow) return -1
        if (won(size, cars[0])) return 0
        val start = key(cars)
        val queue = ArrayDeque<List<BlockCar>>()
        val dist = HashMap<String, Int>()
        queue.add(cars)
        dist[start] = 0
        var nodes = 0
        while (queue.isNotEmpty()) {
            if (++nodes > 80_000) return -1
            checkpoint()
            val cur = queue.removeFirst()
            val d = dist[key(cur)]!!
            if (d >= maxDist) continue
            for ((id, steps) in allMoves(size, cur)) {
                val next = slide(size, cur, id, steps) ?: continue
                val k = key(next)
                if (k in dist) continue
                if (won(size, next[0])) return d + 1
                dist[k] = d + 1
                queue.add(next)
            }
        }
        return -1
    }

    /** First move (carId, steps) on a shortest path, or null. */
    fun nextMove(size: Int, exitRow: Int, cars: List<BlockCar>): Pair<Int, Int>? {
        if (won(size, cars[0])) return null
        val start = key(cars)
        val queue = ArrayDeque<List<BlockCar>>()
        val prev = HashMap<String, Pair<String, Pair<Int, Int>>>()
        val seen = HashSet<String>()
        queue.add(cars)
        seen.add(start)
        var nodes = 0
        var goal: List<BlockCar>? = null
        while (queue.isNotEmpty()) {
            if (++nodes > 200_000) return null
            val cur = queue.removeFirst()
            val ck = key(cur)
            for ((id, steps) in allMoves(size, cur)) {
                val next = slide(size, cur, id, steps) ?: continue
                val nk = key(next)
                if (!seen.add(nk)) continue
                prev[nk] = ck to (id to steps)
                if (won(size, next[0])) {
                    goal = next
                    queue.clear()
                    break
                }
                queue.add(next)
            }
        }
        val end = goal ?: return null
        var cursor = key(end)
        var move: Pair<Int, Int>? = null
        while (cursor != start) {
            val (before, step) = prev[cursor] ?: return null
            move = step
            cursor = before
        }
        return move
    }

    private fun occupancy(size: Int, cars: List<BlockCar>, except: Int): BooleanArray {
        val grid = BooleanArray(size * size)
        for (car in cars) {
            if (car.id == except) continue
            for ((r, c) in car.cells()) grid[r * size + c] = true
        }
        return grid
    }

    private fun key(cars: List<BlockCar>): String =
        cars.sortedBy { it.id }.joinToString(";") { "${it.id}:${it.row},${it.col}" }
}

object SlidingBlocksCodec {
    fun encode(g: SlidingBlocks) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.size.toString(),
        g.exitRow.toString(),
        g.solutionLength.toString(),
        g.moves.toString(),
        g.hints.toString(),
        g.cars.joinToString(";") { "${it.id},${it.row},${it.col},${it.length},${if (it.horizontal) 1 else 0}" },
    ).joinToString("\n")

    fun decode(text: String): SlidingBlocks? = try {
        val l = text.split('\n')
        require(l.size == 9 && l[0] == "1")
        val cars = l[8].split(';').map { part ->
            val p = part.split(',')
            require(p.size == 5)
            BlockCar(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4] == "1")
        }
        SlidingBlocks(
            l[1].toInt(), l[2].toLong(), l[3].toInt(), l[4].toInt(),
            cars, l[5].toInt(), l[6].toInt(), l[7].toInt(),
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
