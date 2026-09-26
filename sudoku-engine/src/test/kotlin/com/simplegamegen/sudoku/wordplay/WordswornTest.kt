package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordswornTest {
    private fun withHand(g: Wordsworn, letters: String): Wordsworn {
        val deck = letters.map { Tile(it, Wordsworn.powerOf(it)) }
        return g.copy(deck = deck, hand = deck.indices.toList(), draw = emptyList(), discard = emptyList())
    }

    @Test fun `a real word from the hand strikes the monster, then it acts`() {
        val g = withHand(Wordsworn.start(1, LogicLevel.EASY), "CATSRNLE")
        assertNull(g.play(listOf(0, 2)))                            // CT isn't a word
        val next = g.play(listOf(0, 1, 2))!!                       // CAT
        assertEquals("CAT", next.last!!.word)
        assertEquals(g.monsterHp - 5, next.monsterHp)              // C3 + A1 + T1
        assertEquals(1, next.turn)
    }

    @Test fun `long words and special tiles`() {
        val g = Wordsworn.start(1, LogicLevel.EASY)
        val tiles = listOf(Tile('S', 1, TileKind.SHIELD), Tile('T', 1), Tile('A', 1, TileKind.DOUBLE), Tile('R', 1), Tile('E', 1, TileKind.HEAL))
        val h = g.copy(deck = tiles, hand = tiles.indices.toList(), draw = emptyList())
        val p = h.preview(listOf(0, 1, 2, 3, 4))                   // STARE: 5 power, +50% for length, doubled
        assertEquals(14, p.damage)
        assertEquals(2, p.blocked)
        assertEquals(2, p.healed)
    }

    @Test fun `wild tiles take any letter`() {
        val tiles = listOf(Tile('C', 3), Tile('?', 0, TileKind.WILD), Tile('T', 1))
        val g = Wordsworn.start(1, LogicLevel.EASY).copy(deck = tiles, hand = listOf(0, 1, 2), draw = emptyList())
        assertEquals("Pick a letter for each wild tile.", g.problem(listOf(0, 1, 2)))
        assertNotNull(g.play(listOf(0, 1, 2), "A"))
    }

    @Test fun `beating a monster offers rewards, and choosing one starts the next fight`() {
        val g = withHand(Wordsworn.start(3, LogicLevel.EASY), "QUARTZES").copy(monsterHp = 1)
        val won = g.play(listOf(0, 1, 2, 3, 4, 5))!!                // QUARTZ
        assertTrue(won.choosing)
        val next = won.choose(0)!!
        assertEquals(1, next.fight)
        assertEquals(Wordsworn.HAND, next.hand.size)
        assertTrue(!next.choosing)
    }

    @Test fun `a bot playing the best word each turn wins easy runs more often than expert ones`() {
        val wins = LogicLevel.entries.associateWith { level ->
            (1L..12L).count { seed ->
                var g = Wordsworn.start(seed, level)
                var guard = 0
                while (!g.over && guard++ < 400) {
                    g = when {
                        g.choosing -> g.choose(0)!!
                        else -> g.bestWord()?.let { (_, picks, wild) -> g.play(picks, wild) } ?: g.swap(listOf(0, 1, 2)) ?: break
                    }
                }
                g.won
            }
        }
        println("bot wins out of 12: $wins")
        assertTrue(wins.getValue(LogicLevel.EASY) >= wins.getValue(LogicLevel.EXPERT))
        assertTrue(wins.getValue(LogicLevel.EASY) >= 8, "easy should be winnable")
    }

    @Test fun `codec round trip`() {
        val g = Wordsworn.start(9, LogicLevel.HARD).let { w -> w.bestWord()!!.let { (_, p, wild) -> w.play(p, wild)!! } }
        assertEquals(g.copy(last = null), WordswornCodec.decode(WordswornCodec.encode(g)))
        assertNull(WordswornCodec.decode("junk"))
    }
}
