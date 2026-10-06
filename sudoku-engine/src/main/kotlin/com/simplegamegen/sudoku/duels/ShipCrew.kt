package com.simplegamegen.sudoku.duels

import kotlin.random.Random

/**
 * Ship, Captain, Crew. Five dice, up to three rolls. A ship is a 6, a captain
 * a 5, and a crew a 4. The captain counts only after the ship, and the crew
 * only after the captain. The other two dice are cargo, and only once all three
 * are aboard. You may stop early. Five hands each. The higher cargo wins.
 * [setting] is how the computer treats the cargo dice.
 */
data class ShipCrew(
    val setting: Int,
    val seed: Long,
    val drawn: Int,
    val you: Int,
    val cpu: Int,
    val hand: Int,
    val dice: List<Int>,
    val held: List<Boolean>,
    val rolls: Int,
    val turn: Int,
    val ended: Boolean = false,
) {
    init {
        require(setting in LEVELS.indices && drawn >= DICE && hand in 0 until HANDS)
        require(you >= 0 && cpu >= 0 && (turn == 1 || turn == -1) && rolls in 1..3)
        require(dice.size == DICE && dice.all { it in 1..6 } && held.size == DICE)
        require(!ended || hand == HANDS - 1)
    }

    val winner: Int get() = when {
        !ended -> 0
        you > cpu -> 1
        you < cpu -> -1
        else -> 0
    }

    fun thisCargo(): Int = cargoOf(dice)

    fun hold(index: Int): ShipCrew? {
        if (ended || turn != 1 || rolls >= 3 || index !in dice.indices) return null
        return copy(held = held.mapIndexed { i, bit -> if (i == index) !bit else bit })
    }

    fun roll(): ShipCrew? = if (!ended && turn == 1 && rolls < 3 && held.any { !it }) reroll(held) else null

    fun stay(): ShipCrew? = if (!ended && turn == 1) finish() else null

    internal fun reroll(keep: List<Boolean>): ShipCrew? {
        if (ended || rolls >= 3 || keep.size != DICE || keep.all { it }) return null
        val random = Random(seed).also { stream -> repeat(drawn) { stream.nextInt(1, 7) } }
        var used = 0
        val next = List(DICE) { i -> if (keep[i]) dice[i] else { used++; random.nextInt(1, 7) } }
        return copy(dice = next, held = keep, rolls = rolls + 1, drawn = drawn + used)
    }

    internal fun finish(): ShipCrew {
        val cargo = cargoOf(dice)
        val youS = if (turn == 1) you + cargo else you
        val cpuS = if (turn == -1) cpu + cargo else cpu
        if (turn == -1 && hand == HANDS - 1) {
            return copy(you = youS, cpu = cpuS, ended = true, held = List(DICE) { false })
        }
        val random = Random(seed).also { stream -> repeat(drawn) { stream.nextInt(1, 7) } }
        val dealt = List(DICE) { random.nextInt(1, 7) }
        return if (turn == 1) copy(
            you = youS, dice = dealt, held = List(DICE) { false }, rolls = 1, drawn = drawn + DICE, turn = -1,
        ) else copy(
            cpu = cpuS, hand = hand + 1, dice = dealt, held = List(DICE) { false }, rolls = 1,
            drawn = drawn + DICE, turn = 1,
        )
    }

    companion object {
        const val DICE = 5
        const val HANDS = 5
        val LEVELS = listOf("Easy", "Medium", "Hard", "Expert")

        fun start(seed: Long, setting: Int): ShipCrew {
            val random = Random(seed)
            return ShipCrew(
                setting, seed, drawn = DICE, you = 0, cpu = 0, hand = 0,
                dice = List(DICE) { random.nextInt(1, 7) }, held = List(DICE) { false }, rolls = 1, turn = 1,
            )
        }

        /** Cargo from [dice], or 0 when the ship, captain, or crew is missing. */
        fun cargoOf(dice: List<Int>): Int {
            if (dice.size != DICE) return 0
            val count = IntArray(7)
            dice.forEach { if (it in 1..6) count[it]++ }
            if (count[6] == 0 || count[5] == 0 || count[4] == 0) return 0
            count[6]--
            count[5]--
            count[4]--
            return (1..6).sumOf { face -> face * count[face] }
        }
    }
}

object ShipAi {
    fun step(g: ShipCrew): ShipCrew {
        require(!g.ended && g.turn == -1)
        if (g.rolls >= 3) return g.finish()
        val keep = holds(g)
        if (keep.all { it }) return g.finish()
        return g.reroll(keep) ?: g.finish()
    }

    private fun holds(g: ShipCrew): List<Boolean> {
        val at = g.dice.indices.groupBy { g.dice[it] }
        val ship = at[6]?.firstOrNull()
        val captain = if (ship != null) at[5]?.firstOrNull() else null
        val crew = if (captain != null) at[4]?.firstOrNull() else null
        val held = BooleanArray(ShipCrew.DICE)
        if (ship != null) held[ship] = true
        if (captain != null) held[captain] = true
        if (crew != null) held[crew] = true
        if (crew != null && g.setting == 0 && (g.seed xor g.drawn.toLong()).toInt() % 4 == 0) {
            held[crew] = false
            return held.toList()
        }
        if (crew != null) {
            // Once the ship is crewed, keep cargo that is unlikely to improve with the rolls left.
            val floor = when (g.setting) {
                0 -> 6
                1 -> 5
                2 -> if (g.rolls >= 2) 4 else 5
                else -> if (g.rolls >= 2) 3 else 4
            }
            g.dice.indices.filter { !held[it] }.forEach { if (g.dice[it] >= floor) held[it] = true }
        }
        return held.toList()
    }
}

object ShipCrewCodec {
    fun encode(g: ShipCrew) = listOf(
        "1", g.setting.toString(), g.seed.toString(), g.drawn.toString(),
        g.you.toString(), g.cpu.toString(), g.hand.toString(), g.dice.joinToString(","),
        g.held.joinToString("") { if (it) "1" else "0" }, g.rolls.toString(), g.turn.toString(), if (g.ended) "1" else "0",
    ).joinToString("\n")

    fun decode(text: String): ShipCrew? = try {
        val lines = text.split('\n')
        require(lines.size == 12 && lines[0] == "1")
        ShipCrew(
            lines[1].toInt(), lines[2].toLong(), lines[3].toInt(), lines[4].toInt(), lines[5].toInt(), lines[6].toInt(),
            lines[7].split(',').map { it.toInt() }, lines[8].map { it == '1' }, lines[9].toInt(), lines[10].toInt(), lines[11] == "1",
        )
    } catch (_: IllegalArgumentException) { null }
}
