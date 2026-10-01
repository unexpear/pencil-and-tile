package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.Yacht
import com.simplegamegen.sudoku.duels.YachtAi
import com.simplegamegen.sudoku.duels.YachtCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YachtTest {
    private fun empty() = MutableList(Yacht.BOXES) { -1 }

    private fun game(
        dice: List<Int>,
        you: List<Int> = empty(),
        cpu: List<Int> = empty(),
        turn: Int = 1,
        rolls: Int = if (turn == 1) 1 else 3,
        youExtra: Int = 0,
        cpuExtra: Int = 0,
        ended: Boolean = false,
        setting: Int = 0,
    ) = Yacht(setting, 99L, 5, dice, List(5) { false }, rolls, you, cpu, youExtra, cpuExtra, turn, ended)

    /** A finished sheet. Chance starts at 5 so a filled Chance box is a legal score. */
    private fun filled(vararg set: Pair<Int, Int>): List<Int> {
        val sheet = MutableList(Yacht.BOXES) { 0 }
        sheet[Yacht.CHANCE] = 5
        set.forEach { (box, score) -> sheet[box] = score }
        return sheet
    }

    @Test fun `faces score only themselves and patterns score the published totals`() {
        val sheet = empty()
        assertEquals(12, Yacht.points(listOf(6, 1, 6, 2, 3), sheet, 5))
        assertEquals(21, Yacht.points(listOf(6, 6, 6, 1, 2), sheet, Yacht.THREE))
        assertEquals(0, Yacht.points(listOf(6, 6, 6, 1, 2), sheet, Yacht.FOUR))
        assertEquals(25, Yacht.points(listOf(6, 6, 6, 1, 1), sheet, Yacht.FULL))
        assertEquals(0, Yacht.points(listOf(6, 6, 6, 6, 6), sheet, Yacht.FULL))
        assertEquals(0, Yacht.points(listOf(3, 3, 3, 4, 5), sheet, Yacht.FULL))
        assertEquals(30, Yacht.points(listOf(1, 2, 3, 4, 6), sheet, Yacht.SMALL))
        assertEquals(30, Yacht.points(listOf(1, 2, 3, 4, 4), sheet, Yacht.SMALL))
        assertEquals(0, Yacht.points(listOf(1, 2, 3, 5, 6), sheet, Yacht.SMALL))
        assertEquals(0, Yacht.points(listOf(1, 2, 3, 4, 6), sheet, Yacht.LARGE))
        assertEquals(40, Yacht.points(listOf(1, 2, 3, 4, 5), sheet, Yacht.LARGE))
        assertEquals(40, Yacht.points(listOf(2, 3, 4, 5, 6), sheet, Yacht.LARGE))
        assertEquals(30, Yacht.points(listOf(2, 3, 4, 5, 6), sheet, Yacht.SMALL))
        assertEquals(50, Yacht.points(listOf(4, 4, 4, 4, 4), sheet, Yacht.YACHT))
        assertEquals(0, Yacht.points(listOf(4, 4, 4, 4, 6), sheet, Yacht.YACHT))
        assertEquals(22, Yacht.points(listOf(4, 4, 4, 4, 6), sheet, Yacht.FOUR))
        assertEquals(6, Yacht.points(listOf(1, 1, 1, 1, 2), sheet, Yacht.CHANCE))
    }

    @Test fun `63 in the upper boxes scores 35 more and 60 does not`() {
        val short = listOf(0, 6, 9, 12, 15, 18, 0, 0, 0, 0, 0, 0, 5)
        val made = listOf(3, 6, 9, 12, 15, 18, 0, 0, 0, 0, 0, 0, 5)
        assertEquals(60, Yacht.upperSum(short))
        assertEquals(0, Yacht.upperBonus(short))
        assertEquals(63, Yacht.upperSum(made))
        assertEquals(35, Yacht.upperBonus(made))
        assertEquals(63 + 35 + 5, Yacht.total(made, 0))
    }

    @Test fun `the first five of a kind can score 50 and a later one adds 100`() {
        val open = game(listOf(6, 6, 6, 6, 6))
        val first = open.score(Yacht.YACHT)!!
        assertEquals(50, first.you[Yacht.YACHT])
        assertEquals(0, first.youExtra)
        assertEquals(0, open.score(Yacht.FULL)!!.you[Yacht.FULL])

        val you = empty().also { it[Yacht.YACHT] = 50 }
        val cpu = empty().also { it[Yacht.CHANCE] = 15 }
        val again = game(listOf(6, 6, 6, 6, 6), you = you, cpu = cpu)
        assertEquals(listOf(5), Yacht.legalBoxes(again.you, again.dice))
        assertNull(again.score(Yacht.FULL))
        val scored = again.score(5)!!
        assertEquals(30, scored.you[5])
        assertEquals(1, scored.youExtra)
        assertEquals(50 + 30 + 100, scored.yourTotal())
    }

    @Test fun `a later five of a kind scores as a match in an open lower box`() {
        val you = empty().also { it[Yacht.YACHT] = 50; it[5] = 18 }
        val cpu = empty().also { it[Yacht.CHANCE] = 15; it[0] = 1 }
        val dice = listOf(6, 6, 6, 6, 6)
        val g = game(dice, you = you, cpu = cpu)
        assertTrue(Yacht.LARGE in Yacht.legalBoxes(g.you, dice))
        assertTrue(0 !in Yacht.legalBoxes(g.you, dice))
        assertEquals(40, Yacht.points(dice, g.you, Yacht.LARGE))
        assertEquals(25, Yacht.points(dice, g.you, Yacht.FULL))
        assertEquals(30, Yacht.points(dice, g.you, Yacht.FOUR))
        val scored = g.score(Yacht.LARGE)!!
        assertEquals(40, scored.you[Yacht.LARGE])
        assertEquals(1, scored.youExtra)
    }

    @Test fun `a scratched five of a kind is not a match in another box`() {
        val you = empty().also { it[Yacht.YACHT] = 0 }
        val cpu = empty().also { it[0] = 0 }
        val dice = listOf(6, 6, 6, 6, 6)
        val g = game(dice, you = you, cpu = cpu)
        assertEquals(0, Yacht.points(dice, g.you, Yacht.FULL))
        assertEquals(0, Yacht.points(dice, g.you, Yacht.LARGE))
        assertTrue(5 in Yacht.legalBoxes(g.you, dice))
        val scored = g.score(5)!!
        assertEquals(30, scored.you[5])
        assertEquals(0, scored.youExtra)
    }

    @Test fun `when every lower box is full a later five of a kind scores 0 up top`() {
        val cpu = empty()
        cpu[Yacht.YACHT] = 50
        cpu[5] = 30
        for (box in Yacht.THREE..Yacht.CHANCE) if (cpu[box] < 0) cpu[box] = if (box == Yacht.CHANCE) 15 else 0
        val you = empty()
        repeat(cpu.count { it >= 0 } + 1) { you[it] = if (it == Yacht.CHANCE) 8 else 0 }
        val dice = listOf(6, 6, 6, 6, 6)
        val g = game(dice, you = you, cpu = cpu, turn = -1, rolls = 3)
        assertEquals(listOf(0, 1, 2, 3, 4), Yacht.legalBoxes(g.cpu, dice))
        val next = YachtAi.step(g)
        assertEquals(0, next.cpu[0])
        assertEquals(1, next.cpuExtra)
        assertTrue(next.cpu[1] < 0)
    }

    @Test fun `the computer keeps a made five of a kind and chases with four`() {
        val cpu = empty().also { it[Yacht.CHANCE] = 12 }
        val you = empty().also { it[0] = 2; it[1] = 2 }
        val kept = YachtAi.step(game(listOf(6, 6, 6, 6, 6), you = you, cpu = cpu, turn = -1, rolls = 1, setting = 2))
        assertEquals(50, kept.cpu[Yacht.YACHT])
        assertEquals(0, kept.cpuExtra)

        val open = empty()
        val yours = empty().also { it[Yacht.CHANCE] = 10 }
        val dice = listOf(6, 6, 6, 6, 2)
        val easy = YachtAi.step(game(dice, you = yours, cpu = open, turn = -1, rolls = 2, setting = 0))
        assertEquals(1, easy.turn)
        assertEquals(24, easy.cpu[5])
        val medium = YachtAi.step(game(dice, you = yours, cpu = open, turn = -1, rolls = 2, setting = 1))
        assertEquals(-1, medium.turn)
        assertEquals(3, medium.rolls)
        assertEquals(listOf(true, true, true, true, false), medium.held)
        val hard = YachtAi.step(game(dice, you = yours, cpu = open, turn = -1, rolls = 2, setting = 2))
        assertEquals(-1, hard.turn)
        assertEquals(3, hard.rolls)
        assertEquals(listOf(true, true, true, true, false), hard.held)
    }

    @Test fun `a later five of a kind is spent on the highest matching lower box`() {
        val cpu = empty().also { it[Yacht.YACHT] = 50; it[5] = 18 }
        val you = empty().also { it[0] = 1; it[1] = 2; it[2] = 3 }
        val next = YachtAi.step(game(listOf(6, 6, 6, 6, 6), you = you, cpu = cpu, turn = -1, rolls = 3))
        assertEquals(40, next.cpu[Yacht.LARGE])
        assertEquals(1, next.cpuExtra)
    }

    @Test fun `the same seed deals the same dice and a held die stays`() {
        val first = Yacht.start(4, 0)
        val second = Yacht.start(4, 3)
        assertEquals(first.dice, second.dice)
        assertTrue(Yacht.start(1, 0).dice != Yacht.start(2, 0).dice)
        val held = first.hold(0)!!.hold(1)!!
        val again = second.hold(0)!!.hold(1)!!
        val rolled = held.roll()!!
        assertEquals(rolled.dice, again.roll()!!.dice)
        assertEquals(held.dice[0], rolled.dice[0])
        assertEquals(held.dice[1], rolled.dice[1])
        assertEquals(held.drawn + 3, rolled.drawn)
        var stuck = first
        repeat(5) { stuck = stuck.hold(it)!! }
        assertNull(stuck.roll())
        val last = first.roll()!!.roll()!!
        assertEquals(3, last.rolls)
        assertNull(last.roll())
    }

    @Test fun `a filled box cannot be scored again`() {
        val you = empty().also { it[0] = 3 }
        val cpu = empty().also { it[Yacht.CHANCE] = 8 }
        val g = game(listOf(1, 1, 1, 2, 3), you = you, cpu = cpu)
        assertNull(g.score(0))
        assertTrue(g.score(Yacht.CHANCE) != null)
    }

    @Test fun `the higher total wins and the 100 can decide it`() {
        val low = filled()
        val high = filled(Yacht.CHANCE to 30)
        val youWins = game(listOf(1, 2, 3, 4, 5), you = high, cpu = low, ended = true)
        assertEquals(1, youWins.winner)
        val level = game(listOf(1, 2, 3, 4, 5), you = low, cpu = low, ended = true)
        assertEquals(0, level.winner)
        val bonus = filled(Yacht.YACHT to 50)
        val ahead = filled(Yacht.YACHT to 50, Yacht.CHANCE to 30, Yacht.FOUR to 20)
        val swing = game(listOf(6, 6, 6, 6, 6), you = bonus, cpu = ahead, youExtra = 1, ended = true)
        assertEquals(155, swing.yourTotal())
        assertEquals(100, swing.cpuTotal())
        assertEquals(1, swing.winner)
        val without = game(listOf(6, 6, 6, 6, 6), you = bonus, cpu = ahead, ended = true)
        assertEquals(-1, without.winner)
    }

    @Test fun `saves round-trip and a broken save is rejected`() {
        val g = Yacht.start(8, 2).hold(2)!!
        assertEquals(g, YachtCodec.decode(YachtCodec.encode(g)))
        assertNull(YachtCodec.decode("no"))
        assertNull(YachtCodec.decode("2\n"))
        val lines = YachtCodec.encode(g).split('\n').toMutableList()
        lines[9] = "2"
        assertNull(YachtCodec.decode(lines.joinToString("\n")))
    }

    @Test fun `every strength plays a full sheet`() {
        for (setting in Yacht.LEVELS.indices) for (seed in 1..2L) {
            var g = Yacht.start(seed, setting)
            var guard = 0
            while (!g.ended && guard++ < 80) {
                g = if (g.turn == 1) {
                    val box = Yacht.legalBoxes(g.you, g.dice).maxBy { Yacht.points(g.dice, g.you, it) * 20 - it }
                    g.score(box)!!
                } else YachtAi.step(g)
            }
            assertTrue(g.ended, "setting $setting seed $seed")
            assertEquals(Yacht.BOXES, g.you.count { it >= 0 })
            assertEquals(Yacht.BOXES, g.cpu.count { it >= 0 })
            assertEquals(g.yourTotal() > g.cpuTotal(), g.winner == 1)
            assertEquals(g.yourTotal() < g.cpuTotal(), g.winner == -1)
            assertTrue(g.youExtra == 0 || g.you[Yacht.YACHT] == 50)
            assertTrue(g.cpuExtra == 0 || g.cpu[Yacht.YACHT] == 50)
        }
    }
}
