package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/**
 * Five dice and a thirteen-box score sheet. On a turn you roll up to three times,
 * holding any dice between rolls, then fill one open box. Each box is used once.
 * Ones through Sixes add that face; 63 or more there scores 35 more. Three and four
 * of a kind add all five dice. A full house scores 25, a small straight 30, a large
 * straight 40, and five of a kind scores 50. Chance adds all five dice.
 *
 * After the five-of-a-kind box scores 50, each later five of a kind adds 100. It must
 * fill the matching upper box when that box is open. Otherwise it scores as a match
 * in any open lower box, or 0 in an open upper box if the lower boxes are full.
 * A five of a kind scored as 0 does not add 100 and does not fill another box as a match.
 *
 * You and the computer each fill a sheet. You roll first. The higher total wins.
 * [setting] is the computer's strength.
 */
data class Yacht(
    val setting: Int,
    val seed: Long,
    val drawn: Int,
    val dice: List<Int>,
    val held: List<Boolean>,
    val rolls: Int,
    val you: List<Int>,
    val cpu: List<Int>,
    val youExtra: Int = 0,
    val cpuExtra: Int = 0,
    val turn: Int = 1,
    val ended: Boolean = false,
) {
    init {
        require(setting in LEVELS.indices)
        require(drawn >= 0 && dice.size == DICE && dice.all { it in 1..6 } && held.size == DICE)
        require(rolls in 0..3 && (rolls == 0 || drawn >= DICE))
        require(you.size == BOXES && cpu.size == BOXES)
        you.forEachIndexed { box, score -> if (score >= 0) require(plausible(box, score)) }
        cpu.forEachIndexed { box, score -> if (score >= 0) require(plausible(box, score)) }
        require(youExtra in 0..12 && cpuExtra in 0..12)
        require(youExtra == 0 || you[YACHT] == 50)
        require(cpuExtra == 0 || cpu[YACHT] == 50)
        require(turn == 1 || turn == -1)
        val yours = you.count { it >= 0 }
        val theirs = cpu.count { it >= 0 }
        val full = you.all { it >= 0 } && cpu.all { it >= 0 }
        require(ended == full)
        if (!ended && turn == 1) require(yours == theirs && rolls in 1..3)
        if (!ended && turn == -1) require(yours == theirs + 1 && rolls in 0..3)
    }

    val winner: Int get() = when {
        !ended -> 0
        yourTotal() > cpuTotal() -> 1
        yourTotal() < cpuTotal() -> -1
        else -> 0
    }

    fun yourTotal(): Int = total(you, youExtra)
    fun cpuTotal(): Int = total(cpu, cpuExtra)

    /** Flips whether die [index] stays for the next roll. */
    fun hold(index: Int): Yacht? {
        if (ended || turn != 1 || rolls !in 1..2 || index !in dice.indices) return null
        return copy(held = held.mapIndexed { i, bit -> if (i == index) !bit else bit })
    }

    /** Rolls every die that is not held. */
    fun roll(): Yacht? = if (turn == 1) reroll(held) else null

    /** Fills [box] with this roll. Null when that box is not a legal score. */
    fun score(box: Int): Yacht? {
        if (ended || turn != 1 || rolls !in 1..3 || box !in legalBoxes(you, dice)) return null
        return write(1, box)
    }

    internal fun dealIn(): Yacht {
        require(!ended && turn == -1 && rolls == 0)
        val random = stream()
        return copy(dice = List(DICE) { random.nextInt(1, 7) }, held = List(DICE) { false }, rolls = 1, drawn = drawn + DICE)
    }

    internal fun reroll(keep: List<Boolean>): Yacht? {
        if (ended || rolls !in 1..2 || keep.size != DICE || keep.all { it }) return null
        val random = stream()
        var used = 0
        val next = List(DICE) { i -> if (keep[i]) dice[i] else { used++; random.nextInt(1, 7) } }
        return copy(dice = next, held = keep, rolls = rolls + 1, drawn = drawn + used)
    }

    internal fun write(player: Int, box: Int): Yacht {
        val sheet = (if (player == 1) you else cpu).toMutableList()
        val gain = if (dice.distinct().size == 1 && sheet[YACHT] == 50) 1 else 0
        sheet[box] = points(dice, sheet, box)
        val stored = sheet.toList()
        val youS = if (player == 1) stored else you
        val cpuS = if (player == -1) stored else cpu
        val youE = youExtra + if (player == 1) gain else 0
        val cpuE = cpuExtra + if (player == -1) gain else 0
        if (youS.all { it >= 0 } && cpuS.all { it >= 0 }) {
            return copy(you = youS, cpu = cpuS, youExtra = youE, cpuExtra = cpuE, ended = true, held = List(DICE) { false })
        }
        if (player == 1) {
            return copy(you = youS, cpu = cpuS, youExtra = youE, cpuExtra = cpuE, turn = -1, rolls = 0, held = List(DICE) { false }, ended = false)
        }
        val random = stream()
        return copy(
            you = youS, cpu = cpuS, youExtra = youE, cpuExtra = cpuE, turn = 1,
            dice = List(DICE) { random.nextInt(1, 7) }, held = List(DICE) { false },
            rolls = 1, drawn = drawn + DICE, ended = false,
        )
    }

    private fun stream(): Random = Random(seed).also { random -> repeat(drawn) { random.nextInt(1, 7) } }

    companion object {
        const val DICE = 5
        const val BOXES = 13
        const val THREE = 6
        const val FOUR = 7
        const val FULL = 8
        const val SMALL = 9
        const val LARGE = 10
        const val YACHT = 11
        const val CHANCE = 12
        val LEVELS = listOf("Easy", "Medium", "Hard", "Expert")
        val NAMES = listOf(
            "Ones", "Twos", "Threes", "Fours", "Fives", "Sixes",
            "Three of a kind", "Four of a kind", "Full house", "Small straight", "Large straight", "Yacht", "Chance",
        )

        fun start(seed: Long, setting: Int): Yacht {
            val random = Random(seed)
            return Yacht(
                setting, seed, drawn = DICE, dice = List(DICE) { random.nextInt(1, 7) },
                held = List(DICE) { false }, rolls = 1,
                you = List(BOXES) { -1 }, cpu = List(BOXES) { -1 },
            )
        }

        fun upperSum(sheet: List<Int>): Int = (0..5).sumOf { (sheet.getOrNull(it) ?: 0).coerceAtLeast(0) }
        fun upperBonus(sheet: List<Int>): Int = if (upperSum(sheet) >= 63) 35 else 0
        fun total(sheet: List<Int>, extra: Int): Int = sheet.sumOf { it.coerceAtLeast(0) } + upperBonus(sheet) + extra * 100

        /** Open boxes this roll may fill. A later five of a kind is not free to pick any box. */
        fun legalBoxes(sheet: List<Int>, dice: List<Int>): List<Int> {
            val open = sheet.indices.filter { sheet[it] < 0 }
            if (dice.distinct().size != 1 || sheet.getOrNull(YACHT) != 50) return open
            val upper = dice[0] - 1
            if (sheet[upper] < 0) return listOf(upper)
            val lower = (THREE..CHANCE).filter { sheet[it] < 0 }
            if (lower.isNotEmpty()) return lower
            return (0..5).filter { sheet[it] < 0 }
        }

        fun points(dice: List<Int>, sheet: List<Int>, box: Int): Int {
            val joker = dice.distinct().size == 1 && sheet.getOrNull(YACHT) == 50 && sheet[dice[0] - 1] >= 0 && box >= THREE
            if (joker) return when (box) {
                FULL -> 25
                SMALL -> 30
                LARGE -> 40
                else -> dice.sum()
            }
            return natural(box, dice)
        }

        /** How useful filling [box] is. Higher is better. Scratching a rare box costs more than scratching Ones. */
        internal fun worth(dice: List<Int>, sheet: List<Int>, box: Int): Int {
            val pts = points(dice, sheet, box)
            var value = pts
            if (box <= 5) {
                if (upperSum(sheet) < 63 && upperSum(sheet) + pts >= 63) value += 35
                value += (pts / (box + 1)) * 2
            }
            if (pts == 0) value -= when (box) {
                YACHT -> 80
                LARGE -> 60
                FULL -> 45
                SMALL -> 30
                FOUR -> 18
                THREE -> 12
                CHANCE -> 25
                else -> (box + 1) * 2
            } else if (box == CHANCE && pts < 15) value -= 20
            return value
        }

        private fun natural(box: Int, dice: List<Int>): Int {
            val count = counts(dice)
            val sum = dice.sum()
            return when (box) {
                in 0..5 -> count[box + 1] * (box + 1)
                THREE -> if (count.any { it >= 3 }) sum else 0
                FOUR -> if (count.any { it >= 4 }) sum else 0
                FULL -> if (count.any { it == 3 } && count.any { it == 2 }) 25 else 0
                SMALL -> if (isSmall(dice)) 30 else 0
                LARGE -> if (isLarge(dice)) 40 else 0
                YACHT -> if (count.any { it == 5 }) 50 else 0
                else -> sum
            }
        }

        private fun plausible(box: Int, score: Int): Boolean = when (box) {
            in 0..5 -> score in 0..(box + 1) * 5 && score % (box + 1) == 0
            THREE, FOUR -> score == 0 || score in 5..30
            FULL -> score == 0 || score == 25
            SMALL -> score == 0 || score == 30
            LARGE -> score == 0 || score == 40
            YACHT -> score == 0 || score == 50
            else -> score in 5..30
        }

        private fun counts(dice: List<Int>): IntArray {
            val count = IntArray(7)
            dice.forEach { count[it]++ }
            return count
        }

        private fun isSmall(dice: List<Int>): Boolean {
            val faces = dice.toSet()
            return listOf(1..4, 2..5, 3..6).any { run -> run.all { it in faces } }
        }

        private fun isLarge(dice: List<Int>): Boolean {
            val faces = dice.toSet()
            return (1..5).all { it in faces } || (2..6).all { it in faces }
        }
    }
}

object YachtAi {
    /** One step of the computer's turn: the opening roll, one reroll, or the chosen box. */
    fun step(g: Yacht): Yacht {
        require(!g.ended && g.turn == -1)
        if (g.rolls == 0) return g.dealIn()
        checkpoint()
        val keep = keepOrNull(g)
        val box = best(g.dice, g.cpu)
        return if (keep == null) g.write(-1, box) else g.reroll(keep) ?: g.write(-1, box)
    }

    private fun best(dice: List<Int>, sheet: List<Int>): Int {
        val boxes = Yacht.legalBoxes(sheet, dice)
        require(boxes.isNotEmpty())
        return boxes.maxBy { Yacht.worth(dice, sheet, it) * 20 - it }
    }

    private fun keepOrNull(g: Yacht): List<Boolean>? {
        if (g.rolls >= 3) return null
        val sheet = g.cpu
        val dice = g.dice
        if (dice.distinct().size == 1 && sheet[Yacht.YACHT] != 0) return null
        if (g.setting == 0) {
            val best = Yacht.legalBoxes(sheet, dice).maxOf { Yacht.points(dice, sheet, it) }
            val four = counts(dice).any { it >= 4 }
            if (best >= 25 || (four && best >= 18)) return null
            return release(heuristic(dice, sheet, 0))
        }
        if (g.setting == 1) {
            if (finished(dice, sheet)) return null
            return release(heuristic(dice, sheet, 1))
        }
        val stand = Yacht.legalBoxes(sheet, dice).maxOf { Yacht.worth(dice, sheet, it) }
        var bestEv = stand.toDouble()
        var bestHold: List<Boolean>? = null
        var bestKept = -1
        for (mask in 0 until 31) {
            val hold = List(Yacht.DICE) { mask shr it and 1 == 1 }
            var ev = expected(dice, hold, sheet)
            if (g.setting >= 3 && g.rolls == 1) ev += chase(dice, hold, sheet)
            val kept = hold.count { it }
            if (ev > bestEv + 0.35) {
                bestEv = ev
                bestHold = hold
                bestKept = kept
            } else if (bestHold != null && ev >= bestEv - 0.05 && kept > bestKept && ev > stand + 0.35) {
                bestHold = hold
                bestKept = kept
            }
        }
        return bestHold
    }

    private fun finished(dice: List<Int>, sheet: List<Int>): Boolean {
        val count = counts(dice)
        if (count.any { it == 5 }) return true
        if (sheet[Yacht.LARGE] < 0 && Yacht.points(dice, sheet, Yacht.LARGE) == 40) return true
        if (sheet[Yacht.FULL] < 0 && Yacht.points(dice, sheet, Yacht.FULL) == 25) return true
        if (sheet[Yacht.SMALL] < 0 && Yacht.points(dice, sheet, Yacht.SMALL) == 30) return true
        if (count.any { it >= 4 } && sheet[Yacht.FOUR] < 0 && sheet[Yacht.YACHT] < 0) return false
        return count.any { it >= 4 } && sheet[Yacht.FOUR] < 0
    }

    private fun heuristic(dice: List<Int>, sheet: List<Int>, setting: Int): List<Boolean> {
        val count = counts(dice)
        val mode = (1..6).maxBy { count[it] * 10 + it }
        if (count[mode] >= 3) return dice.map { it == mode }
        if (setting >= 1) straightHold(dice)?.let { return it }
        if (count[mode] >= 2) return dice.map { it == mode }
        return List(Yacht.DICE) { false }
    }

    private fun straightHold(dice: List<Int>): List<Boolean>? {
        val at = dice.indices.groupBy { dice[it] }
        var best = emptyList<Int>()
        for (start in 1..4) {
            val run = mutableListOf<Int>()
            var face = start
            while (face <= 6 && at[face] != null) {
                run += at[face]!!.first()
                face++
            }
            if (run.size > best.size) best = run.toList()
        }
        if (best.size < 3) return null
        if (best.size == 5) best = best.dropLast(1)
        return List(Yacht.DICE) { it in best }
    }

    private fun expected(dice: List<Int>, hold: List<Boolean>, sheet: List<Int>): Double {
        val free = hold.indices.filter { !hold[it] }
        val work = dice.toMutableList()
        var sum = 0.0
        var seen = 0
        fun fill(index: Int) {
            if (index == free.size) {
                sum += Yacht.legalBoxes(sheet, work).maxOf { Yacht.worth(work, sheet, it) }
                seen++
                return
            }
            for (face in 1..6) {
                work[free[index]] = face
                fill(index + 1)
            }
        }
        fill(0)
        return sum / seen
    }

    /** A small extra for a keep that can still grow, used only when another roll remains after this one. */
    private fun chase(dice: List<Int>, hold: List<Boolean>, sheet: List<Int>): Double {
        val kept = dice.filterIndexed { i, _ -> hold[i] }
        if (kept.isEmpty()) return 2.0
        val count = counts(kept)
        var bonus = 0.0
        if (count.any { it >= 4 } && (sheet[Yacht.YACHT] < 0 || sheet[Yacht.FOUR] < 0)) bonus += 10
        else if (count.any { it >= 3 } && (sheet[Yacht.YACHT] < 0 || sheet[Yacht.FOUR] < 0 || sheet[Yacht.THREE] < 0)) bonus += 5
        else if (count.any { it >= 2 }) bonus += 2
        val run = runLength(kept)
        if (run >= 4 && (sheet[Yacht.LARGE] < 0 || sheet[Yacht.SMALL] < 0)) bonus += 8
        else if (run >= 3 && sheet[Yacht.SMALL] < 0) bonus += 3
        return bonus
    }

    private fun release(hold: List<Boolean>): List<Boolean> =
        if (hold.all { it }) hold.mapIndexed { i, bit -> i != hold.lastIndex && bit } else hold

    private fun counts(dice: List<Int>): IntArray {
        val count = IntArray(7)
        dice.forEach { if (it in 1..6) count[it]++ }
        return count
    }

    private fun runLength(faces: List<Int>): Int {
        val set = faces.toSet()
        var best = 0
        var len = 0
        for (face in 1..6) {
            if (face in set) { len++; if (len > best) best = len } else len = 0
        }
        return best
    }
}

object YachtCodec {
    fun encode(g: Yacht) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        g.drawn.toString(),
        g.dice.joinToString(","),
        g.held.joinToString(",") { if (it) "1" else "0" },
        g.rolls.toString(),
        g.you.joinToString(","),
        g.cpu.joinToString(","),
        g.youExtra.toString(),
        g.cpuExtra.toString(),
        g.turn.toString(),
        if (g.ended) "1" else "0",
    ).joinToString("\n")

    fun decode(text: String): Yacht? = try {
        val lines = text.split('\n')
        require(lines.size == 13 && lines[0] == "1")
        Yacht(
            lines[1].toInt(),
            lines[2].toLong(),
            lines[3].toInt(),
            lines[4].split(',').map { it.toInt() },
            lines[5].split(',').map { it == "1" },
            lines[6].toInt(),
            rows(lines[7]),
            rows(lines[8]),
            lines[9].toInt(),
            lines[10].toInt(),
            lines[11].toInt(),
            lines[12] == "1",
        )
    } catch (_: IllegalArgumentException) { null }

    private fun rows(line: String) = line.split(',').map { it.toInt() }
}
