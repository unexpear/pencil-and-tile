package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Ten Thousand, the six-dice push-your-luck game. A 1 scores 100 and a 5 scores 50.
 * Three of a kind scores 1000 for ones and 100 times the face otherwise; four, five
 * and six of a kind score two, four and eight times that. 1 through 6 scores 1500,
 * three pairs score 1500, and two threes score 2500. Every die you keep has to be
 * part of the score.
 *
 * Roll, keep a scoring set, then bank or roll what is left. The first bank of the
 * game has to be at least [OPENING]. A roll that scores nothing loses the points
 * of that turn, not the points already banked. Scoring every die you just rolled
 * means you roll all six again before you can bank. The first player to reach
 * [GOAL] gives the other one more turn. The higher bank wins.
 */
data class TenThousand(
    val setting: Int,
    val seed: Long,
    val drawn: Int,
    val you: Int,
    val cpu: Int,
    val pending: Int,
    val live: List<Int>,
    val aside: List<Int>,
    val turn: Int,
    val fresh: Boolean,
    val mustRoll: Boolean,
    val reply: Boolean,
    val busted: Int,
    val ended: Boolean,
) {
    init {
        require(setting in LEVELS.indices && drawn >= 0 && you >= 0 && cpu >= 0 && pending >= 0)
        require(turn == 1 || turn == -1)
        require(live.size <= DICE && aside.size <= DICE * 6)
        require(live.all { it in 1..6 } && aside.all { it in 1..6 })
        require(busted in -1..1)
        require(!mustRoll || (live.isEmpty() && pending > 0 && !fresh))
        require(!fresh || (live.isNotEmpty() && !mustRoll))
        require(!reply || you >= GOAL || cpu >= GOAL)
        require(!ended || you >= GOAL || cpu >= GOAL)
        if (ended) require(pending == 0 && !fresh && !mustRoll)
    }

    val winner: Int get() = when {
        !ended -> 0
        you > cpu -> 1
        you < cpu -> -1
        else -> 0
    }

    fun canBank(): Boolean {
        if (ended || fresh || mustRoll || pending <= 0) return false
        val banked = if (turn == 1) you else cpu
        return banked > 0 || pending >= OPENING
    }

    fun roll(): TenThousand? = if (!ended && turn == 1 && !fresh) throwDice() else null

    /** Keeps [indexes] from the current roll. Null when those dice do not score on their own. */
    fun score(indexes: List<Int>): TenThousand? {
        if (ended || turn != 1 || !fresh || indexes.isEmpty() || indexes.distinct().size != indexes.size) return null
        if (indexes.any { it !in live.indices } || pointsOf(indexes.map { live[it] }) == null) return null
        return take(indexes)
    }

    fun bank(): TenThousand? = if (turn == 1 && canBank()) banked() else null

    internal fun throwDice(): TenThousand {
        val count = if (live.isEmpty()) DICE else live.size
        val random = Random(seed).also { stream -> repeat(drawn) { stream.nextInt(1, 7) } }
        val faces = List(count) { random.nextInt(1, 7) }
        val kept = if (live.isEmpty() && !mustRoll && pending == 0) emptyList() else aside
        val rolled = copy(live = faces, aside = kept, fresh = true, mustRoll = false, drawn = drawn + count, busted = 0)
        return if (scoringMasks(faces).isEmpty()) rolled.bust() else rolled
    }

    internal fun take(indexes: List<Int>): TenThousand {
        val pts = pointsOf(indexes.map { live[it] })!!
        val keep = indexes.toSet()
        val left = live.filterIndexed { i, _ -> i !in keep }
        return copy(
            pending = pending + pts, live = left, aside = aside + indexes.map { live[it] },
            fresh = false, mustRoll = left.isEmpty(), busted = 0,
        )
    }

    internal fun banked(): TenThousand {
        val total = (if (turn == 1) you else cpu) + pending
        val youS = if (turn == 1) total else you
        val cpuS = if (turn == -1) total else cpu
        val reached = total >= GOAL
        val done = reply || reached && (if (turn == 1) cpu else you) >= GOAL
        if (done || reply) {
            return copy(you = youS, cpu = cpuS, pending = 0, live = emptyList(), aside = emptyList(), fresh = false, mustRoll = false, reply = false, busted = 0, ended = true)
        }
        return copy(
            you = youS, cpu = cpuS, pending = 0, live = emptyList(), aside = emptyList(),
            fresh = false, mustRoll = false, turn = -turn, reply = reached, busted = 0, ended = false,
        )
    }

    internal fun bust(): TenThousand {
        val done = reply
        return copy(
            pending = 0, live = emptyList(), aside = emptyList(), fresh = false, mustRoll = false,
            turn = if (done) turn else -turn, reply = if (done) false else reply, busted = turn, ended = done,
        )
    }

    companion object {
        const val DICE = 6
        const val OPENING = 500
        const val GOAL = 10_000
        val LEVELS = listOf("Easy", "Medium", "Hard", "Expert")

        fun start(seed: Long, setting: Int) = TenThousand(
            setting, seed, drawn = 0, you = 0, cpu = 0, pending = 0, live = emptyList(), aside = emptyList(),
            turn = 1, fresh = false, mustRoll = false, reply = false, busted = 0, ended = false,
        )

        /** Points for [dice] when every die is part of the score, or null when one is left over. */
        fun pointsOf(dice: List<Int>): Int? {
            if (dice.isEmpty() || dice.any { it !in 1..6 }) return null
            val count = IntArray(7)
            dice.forEach { count[it]++ }
            if (dice.size == DICE && (1..6).all { count[it] == 1 }) return 1500
            if (dice.size == DICE && (1..6).count { count[it] == 2 } == 3) return 1500
            if (dice.size == DICE && (1..6).count { count[it] == 3 } == 2) return 2500
            var points = 0
            for (face in 1..6) {
                val n = count[face]
                if (n >= 3) {
                    val base = if (face == 1) 1000 else face * 100
                    points += base * when (n) { 3 -> 1; 4 -> 2; 5 -> 4; else -> 8 }
                    count[face] = 0
                }
            }
            points += count[1] * 100 + count[5] * 50
            count[1] = 0
            count[5] = 0
            return if ((1..6).any { count[it] > 0 }) null else points
        }

        fun scoringMasks(dice: List<Int>): List<List<Int>> {
            val found = mutableListOf<List<Int>>()
            val n = dice.size
            for (mask in 1 until (1 shl n)) {
                val indexes = (0 until n).filter { mask shr it and 1 == 1 }
                if (pointsOf(indexes.map { dice[it] }) != null) found += indexes
            }
            return found
        }
    }
}

object TenAi {
    fun step(g: TenThousand): TenThousand {
        require(!g.ended && g.turn == -1)
        checkpoint()
        if (!g.fresh) return if (g.canBank() && bankNow(g)) g.banked() else g.throwDice()
        val masks = TenThousand.scoringMasks(g.live)
        if (masks.isEmpty()) return g.bust()
        return g.take(choose(g, masks))
    }

    private fun choose(g: TenThousand, masks: List<List<Int>>): List<Int> {
        fun value(mask: List<Int>): Int {
            val pts = TenThousand.pointsOf(g.live.pick(mask)) ?: 0
            val left = if (mask.size == g.live.size) TenThousand.DICE else g.live.size - mask.size
            return when (g.setting) {
                0 -> pts * 20 - mask.size
                1 -> pts * 10 + if (left >= 3) 40 else left * 8
                else -> pts + outlook(left).toInt()
            }
        }
        val ranked = masks.sortedByDescending { value(it) }
        if (g.setting > 0) return ranked.first()
        val random = Random(g.seed xor g.drawn.toLong() xor g.pending.toLong())
        if (ranked.size > 1 && random.nextInt(100) < 20) return ranked[1]
        return ranked.first()
    }

    private fun bankNow(g: TenThousand): Boolean {
        if (!g.canBank()) return false
        val left = if (g.mustRoll) TenThousand.DICE else g.live.size
        val ahead = g.cpu >= g.you
        return when (g.setting) {
            0 -> g.pending >= 800 || (g.pending >= TenThousand.OPENING && (left <= 2 || ahead))
            1 -> g.pending >= 1_000 || (left <= 2 && g.pending >= 700) || outlook(left) + 200 < g.pending
            2 -> outlook(left) + 80 < g.pending
            else -> outlook(left) < g.pending
        }
    }

    /** Average points of the best set on one roll of [count] dice. A roll with no score counts as 0. */
    private fun outlook(count: Int): Double {
        if (count !in 1..TenThousand.DICE) return 0.0
        cached[count]?.let { return it }
        val faces = IntArray(count)
        var total = 0.0
        var seen = 0
        fun fill(index: Int) {
            if (index == count) {
                val dice = faces.toList()
                val best = TenThousand.scoringMasks(dice).maxOfOrNull { TenThousand.pointsOf(dice.pick(it)) ?: 0 } ?: 0
                total += best
                seen++
                return
            }
            for (face in 1..6) {
                faces[index] = face
                fill(index + 1)
            }
        }
        fill(0)
        val value = total / seen
        cached[count] = value
        return value
    }

    private val cached = arrayOfNulls<Double>(7)
    private fun List<Int>.pick(indexes: List<Int>) = indexes.map { this[it] }
}

object TenThousandCodec {
    fun encode(g: TenThousand) = listOf(
        "1", g.setting.toString(), g.seed.toString(), g.drawn.toString(),
        g.you.toString(), g.cpu.toString(), g.pending.toString(),
        g.live.joinToString(","), g.aside.joinToString(","), g.turn.toString(),
        if (g.fresh) "1" else "0", if (g.mustRoll) "1" else "0", if (g.reply) "1" else "0",
        g.busted.toString(), if (g.ended) "1" else "0",
    ).joinToString("\n")

    fun decode(text: String): TenThousand? = try {
        val lines = text.split('\n')
        require(lines.size == 15 && lines[0] == "1")
        TenThousand(
            lines[1].toInt(), lines[2].toLong(), lines[3].toInt(), lines[4].toInt(), lines[5].toInt(), lines[6].toInt(),
            faces(lines[7]), faces(lines[8]), lines[9].toInt(), lines[10] == "1", lines[11] == "1", lines[12] == "1",
            lines[13].toInt(), lines[14] == "1",
        )
    } catch (_: IllegalArgumentException) { null }

    private fun faces(line: String) = if (line.isEmpty()) emptyList() else line.split(',').map { it.toInt() }
}
