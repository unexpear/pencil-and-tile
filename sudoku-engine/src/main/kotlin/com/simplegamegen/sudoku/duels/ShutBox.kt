package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Shut the box. Nine tiles, 1 through 9. Roll two dice, or one die once every
 * remaining tile is 6 or less, and flip a set of tiles that adds up to the roll.
 * If no set adds up, the score is the sum of the tiles still up. Shutting every
 * tile scores 0. You and the computer each play your own box. The lower score wins.
 * [setting] is how carefully the computer chooses a set.
 */
data class ShutBox(
    val setting: Int,
    val seed: Long,
    val drawn: Int,
    val youUp: List<Boolean>,
    val cpuUp: List<Boolean>,
    val dice: List<Int>,
    val youScore: Int = -1,
    val cpuScore: Int = -1,
    val turn: Int = 1,
    val ended: Boolean = false,
) {
    init {
        require(setting in LEVELS.indices && drawn >= 0 && (turn == 1 || turn == -1))
        require(youUp.size == TILES && cpuUp.size == TILES)
        require(youScore in -1..MAX && cpuScore in -1..MAX)
        require(youScore < 0 || youScore == sumOf(youUp))
        require(cpuScore < 0 || cpuScore == sumOf(cpuUp))
        require(ended == (youScore >= 0 && cpuScore >= 0))
        if (!ended && turn == 1) require(youScore < 0 && cpuScore < 0 && cpuUp.all { it })
        if (!ended && turn == -1) require(youScore >= 0 && cpuScore < 0)
        if (!ended) {
            val up = if (turn == 1) youUp else cpuUp
            require(dice.size == diceNeeded(up) && dice.all { it in 1..6 } && drawn >= dice.size)
        }
    }

    val winner: Int get() = when {
        !ended -> 0
        youScore < cpuScore -> 1
        youScore > cpuScore -> -1
        else -> 0
    }

    fun options(): List<List<Int>> = options(if (turn == 1) youUp else cpuUp, dice.sum())

    /** Flips [tiles]. Null when they are not a set that adds up to the roll. */
    fun cover(tiles: List<Int>): ShutBox? {
        if (ended || turn != 1 || options().none { it == tiles.sorted() }) return null
        return apply(tiles.sorted())
    }

    /** Ends the round when the roll matches no tiles. */
    fun take(): ShutBox? {
        if (ended || turn != 1 || options().isNotEmpty()) return null
        return finish(sumOf(youUp))
    }

    internal fun apply(tiles: List<Int>): ShutBox {
        val up = (if (turn == 1) youUp else cpuUp).toMutableList()
        tiles.forEach { up[it - 1] = false }
        val board = up.toList()
        if (sumOf(board) == 0) return finish(0, board)
        val rolled = deal(diceNeeded(board))
        return if (turn == 1) copy(youUp = board, dice = rolled.faces, drawn = rolled.drawn)
        else copy(cpuUp = board, dice = rolled.faces, drawn = rolled.drawn)
    }

    private fun finish(score: Int, board: List<Boolean>? = null): ShutBox {
        val yours = if (turn == 1) board ?: youUp else youUp
        val theirs = if (turn == -1) board ?: cpuUp else cpuUp
        val youS = if (turn == 1) score else youScore
        val cpuS = if (turn == -1) score else cpuScore
        if (youS >= 0 && cpuS >= 0) {
            return copy(youUp = yours, cpuUp = theirs, youScore = youS, cpuScore = cpuS, ended = true)
        }
        val rolled = deal(2)
        return copy(
            youUp = yours, cpuUp = List(TILES) { true }, dice = rolled.faces, drawn = rolled.drawn,
            youScore = youS, cpuScore = -1, turn = -1, ended = false,
        )
    }

    private fun deal(count: Int): Rolled {
        val random = Random(seed).also { stream -> repeat(drawn) { stream.nextInt(1, 7) } }
        return Rolled(List(count) { random.nextInt(1, 7) }, drawn + count)
    }

    private data class Rolled(val faces: List<Int>, val drawn: Int)

    companion object {
        const val TILES = 9
        const val MAX = 45
        val LEVELS = listOf("Easy", "Medium", "Hard", "Expert")

        fun start(seed: Long, setting: Int): ShutBox {
            val random = Random(seed)
            return ShutBox(
                setting, seed, drawn = 2, youUp = List(TILES) { true }, cpuUp = List(TILES) { true },
                dice = List(2) { random.nextInt(1, 7) },
            )
        }

        fun sumOf(up: List<Boolean>): Int = (1..TILES).filter { up[it - 1] }.sum()

        fun diceNeeded(up: List<Boolean>): Int {
            val highest = (1..TILES).lastOrNull { up[it - 1] } ?: 0
            return if (highest <= 6) 1 else 2
        }

        fun options(up: List<Boolean>, target: Int): List<List<Int>> {
            val tiles = (1..TILES).filter { up[it - 1] }
            val found = mutableListOf<List<Int>>()
            fun walk(index: Int, left: Int, taken: List<Int>) {
                if (left == 0) { if (taken.isNotEmpty()) found += taken; return }
                if (index == tiles.size || tiles[index] > left) return
                walk(index + 1, left - tiles[index], taken + tiles[index])
                walk(index + 1, left, taken)
            }
            if (target > 0) walk(0, target, emptyList())
            return found
        }
    }
}

object ShutAi {
    fun step(g: ShutBox): ShutBox {
        require(!g.ended && g.turn == -1)
        checkpoint()
        val choices = g.options()
        if (choices.isEmpty()) return g.copy(cpuScore = ShutBox.sumOf(g.cpuUp), ended = true)
        return g.apply(choose(g, choices))
    }

    fun choose(g: ShutBox, choices: List<List<Int>>): List<Int> {
        if (g.setting == 0) return choices.random(Random(g.seed xor g.drawn.toLong()))
        if (g.setting == 1) return choices.maxBy { it.max() * 10 + it.size }
        if (g.setting == 2) return choices.maxBy { flexibility(without(g.cpuUp, it)) }
        val memo = HashMap<Int, Double>()
        return choices.minBy { expected(mask(without(g.cpuUp, it)), memo) }
    }

    private fun without(up: List<Boolean>, tiles: List<Int>): List<Boolean> {
        val next = up.toMutableList()
        tiles.forEach { next[it - 1] = false }
        return next
    }

    private fun mask(up: List<Boolean>): Int = (0 until ShutBox.TILES).fold(0) { bits, i -> if (up[i]) bits or (1 shl i) else bits }

    private fun flexibility(up: List<Boolean>): Int {
        val limit = if (ShutBox.diceNeeded(up) == 1) 6 else 12
        return (1..limit).count { ShutBox.options(up, it).isNotEmpty() }
    }

    /** Expected remaining score with perfect play. Lower is better. */
    private fun expected(bits: Int, memo: HashMap<Int, Double>): Double {
        if (bits == 0) return 0.0
        memo[bits]?.let { return it }
        val up = List(ShutBox.TILES) { bits and (1 shl it) != 0 }
        val faces = ShutBox.diceNeeded(up)
        val total = if (faces == 1) 6 else 36
        var sum = 0.0
        for (roll in 0 until total) {
            val target = if (faces == 1) roll + 1 else roll / 6 + roll % 6 + 2
            val choices = ShutBox.options(up, target)
            sum += if (choices.isEmpty()) ShutBox.sumOf(up).toDouble()
            else choices.minOf { expected(mask(without(up, it)), memo) }
        }
        val value = sum / total
        memo[bits] = value
        return value
    }
}

object ShutBoxCodec {
    fun encode(g: ShutBox) = listOf(
        "1", g.setting.toString(), g.seed.toString(), g.drawn.toString(),
        bits(g.youUp), bits(g.cpuUp), g.dice.joinToString(","),
        g.youScore.toString(), g.cpuScore.toString(), g.turn.toString(), if (g.ended) "1" else "0",
    ).joinToString("\n")

    fun decode(text: String): ShutBox? = try {
        val lines = text.split('\n')
        require(lines.size == 11 && lines[0] == "1")
        ShutBox(
            lines[1].toInt(), lines[2].toLong(), lines[3].toInt(), flags(lines[4]), flags(lines[5]),
            if (lines[6].isEmpty()) emptyList() else lines[6].split(',').map { it.toInt() },
            lines[7].toInt(), lines[8].toInt(), lines[9].toInt(), lines[10] == "1",
        )
    } catch (_: IllegalArgumentException) { null }

    private fun bits(up: List<Boolean>) = up.joinToString("") { if (it) "1" else "0" }
    private fun flags(line: String) = line.map { it == '1' }
}
