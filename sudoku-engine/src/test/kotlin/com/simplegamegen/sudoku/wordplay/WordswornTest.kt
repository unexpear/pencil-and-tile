package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordswornTest {
    private fun card(ch: Char, hits: Int = 1, blocks: Int = 1, power: Power = Power.NONE, n: Int = 0) =
        Card(ch, Edge(blocks = blocks), Edge(hits = hits), power, n)

    /** A knight's fight with exactly these cards in hand and nothing else in the deck. */
    private fun fight(vararg cards: Card, level: LogicLevel = LogicLevel.MEDIUM): Wordsworn {
        val g = Wordsworn.start(1, level, rulesVersion = 2).choose(Hero.KNIGHT.ordinal)!!
        return g.copy(deck = cards.toList(), hand = cards.indices.toList(), draw = emptyList(), discard = emptyList(), worn = emptyList(), ink = 0)
    }

    private fun hand(vararg i: Int): List<Piece> = i.map { Piece.Hand(it) }

    @Test fun `choosing a hero starts the first fight with four cards`() {
        val g = Wordsworn.start(5, LogicLevel.MEDIUM, rulesVersion = 2)
        assertEquals(Offer.Heroes, g.offer)
        val f = g.choose(Hero.BARD.ordinal)!!
        assertTrue(f.fighting)
        assertEquals(4, f.hand.size)
        assertEquals(Wordsworn.schedule(LogicLevel.MEDIUM, 5, rulesVersion = 2)[0], f.monster)
        assertEquals(6, f.fights, "two books of three fights")
    }

    @Test fun `splaying right counts right edges and puts the first letter on top`() {
        val g = fight(card('C', hits = 3, blocks = 0, power = Power.HIT, n = 2), card('A', hits = 1, blocks = 2), card('T', hits = 2, blocks = 1, power = Power.BLOCK, n = 5))
        val right = g.preview(hand(0, 1, 2), Splay.RIGHT)
        assertEquals('C', right.top)
        assertEquals(3 + 1 + 2 + 2, right.hits, "right-edge icons, then C's +2 hits")
    }

    @Test fun `splaying left counts left edges and puts the last letter on top, which is then worn out`() {
        val g = fight(card('C', hits = 3, blocks = 0), card('A', hits = 1, blocks = 2), card('T', hits = 2, blocks = 1, power = Power.BLOCK, n = 5))
        val left = g.preview(hand(0, 1, 2), Splay.LEFT)
        assertEquals('T', left.top)
        assertEquals(0 + 2 + 1 + 5, left.blocks, "left-edge icons, then T's +5 blocks")
        val after = g.play(hand(0, 1, 2), Splay.LEFT)!!
        assertEquals(listOf(2), after.worn, "T is worn out for the rest of the fight")
    }

    @Test fun `a wild on top passes the top to the card under it, and an unused wild gives ink`() {
        val g = fight(card('A', power = Power.HIT, n = 4), card('T'))
        val p = g.preview(listOf(Piece.Wild('C'), Piece.Hand(0), Piece.Hand(1)), Splay.RIGHT)
        assertEquals('A', p.top)
        assertEquals(0, p.ink)
        assertEquals(1, g.preview(hand(0, 1), Splay.RIGHT).ink)
    }

    @Test fun `the weak-spot vowel on top makes the monster skip ahead, and it's worn out`() {
        val g = fight(card('T')).copy(monster = Monster.TYPO_IMP)       // weak spot O
        val to = listOf(Piece.Hand(0), Piece.Vowel)
        assertEquals(Piece.Vowel, g.topOf(to, Splay.LEFT))
        val after = g.play(to, Splay.LEFT)!!
        assertTrue(after.vowelWorn)
        assertTrue(after.last!!.skipped)
        assertEquals("The weak-spot vowel is worn out.", after.problem(listOf(Piece.Wild('G'), Piece.Vowel)))
    }

    @Test fun `beating the first stage flips the monster, stuns it and carries the extra damage`() {
        val g = fight(card('C', hits = 5), card('A'), card('T')).copy(monsterHp = 2, act = 0, monster = Monster.TYPO_IMP)
        val after = g.play(hand(0, 1, 2), Splay.RIGHT)!!
        assertEquals(2, after.stage)
        assertTrue(after.last!!.flipped && after.last!!.stunned)
        assertEquals(after.monsterMax - (after.last!!.damage - 2), after.monsterHp)
        assertEquals(g.hp - g.hexes, after.hp, "a stunned monster doesn't attack")
    }

    @Test fun `hexes hurt the monster each turn and fade by one`() {
        val g = fight(card('A', hits = 0), card('T', hits = 0)).copy(monsterHexes = 3)
        val hp = g.monsterHp
        val after = g.play(hand(0, 1), Splay.RIGHT)!!
        val special = g.monster.special
        if (special != Special.NO_HEX && special != Special.REGEN) assertEquals(hp - 3, after.monsterHp)
        assertEquals(2, after.monsterHexes)
    }

    @Test fun `items cost ink and work once a turn`() {
        val g = fight(card('A'), card('T'))
        val knife = g.items.indexOfFirst { it.kind == ItemKind.BUCKLER }
        assertNull(g.useItem(knife), "no ink yet")
        val rich = g.copy(ink = 3)
        val used = rich.useItem(knife)!!
        assertEquals(2, used.ink)
        assertEquals(2, used.prep.blocks)
        assertNull(used.useItem(knife), "once a turn")
    }

    @Test fun `blots hurt when held and are thrown away when played`() {
        val blot = Card('X', Edge(), Edge(), Power.NONE, blot = true)
        val g = fight(card('A', blocks = 0), card('T', blocks = 0), blot, card('E'))
        val held = g.play(hand(0, 1), Splay.RIGHT)!!
        assertTrue(held.last!!.hexTaken >= 2, "a blot left in the hand hurts 2")
        val played = g.play(hand(3, 2), Splay.RIGHT)          // EX
        assertNotNull(played)
        assertEquals(3, played!!.deck.size, "the blot is gone for good")
    }

    @Test fun `a beaten monster leads to paths, rewards, the shop and the next fight`() {
        var g = fight(card('C', hits = 99), card('A'), card('T')).copy(stage = 2, monsterHp = 1)
        g = g.play(hand(0, 1, 2), Splay.RIGHT)!!
        assertTrue(g.offer is Offer.Paths)
        var guard = 0
        while (g.offer != null && g.offer !is Offer.Shop && guard++ < 20) g = g.choose(0)!!
        val shop = g.offer as Offer.Shop
        val rest = shop.goods.indexOf(Goods.ForRest)
        g = g.copy(hp = 5, stars = 5)
        g = g.buy(rest)!!
        assertEquals(10, g.hp)
        val keep = shop.goods.indexOfFirst { it is Goods.ForKeepsake }
        if (keep >= 0) {
            g = g.buy(keep)!!
            assertTrue(g.offer is Offer.Keepsakes)
            g = g.choose(1)!!
            assertTrue(g.offer is Offer.Shop, "back in the shop after choosing the side")
            assertTrue(keep in (g.offer as Offer.Shop).sold)
        }
        g = g.choose(-1)!!
        assertEquals(1, g.fight)
        assertTrue(g.fighting)
    }

    @Test fun `a bot playing the best word each turn wins easy runs more often than expert ones`() {
        val wins = LogicLevel.entries.associateWith { level ->
            (1L..8L).count { seed -> bot(Wordsworn.start(seed, level, rulesVersion = 2).choose((seed % 3).toInt())!!).won }
        }
        println("bot wins out of 8: $wins")
        assertTrue(wins.getValue(LogicLevel.EASY) >= wins.getValue(LogicLevel.EXPERT))
        assertTrue(wins.getValue(LogicLevel.EASY) >= 5, "easy should be winnable")
    }

    @Test fun `complete runs replay exactly through fights rewards shops and endings`() {
        for (level in LogicLevel.entries) for (hero in Hero.entries) {
            val end = bot(Wordsworn.start(9 + hero.ordinal.toLong(), level).choose(hero.ordinal)!!) { state ->
                if (state.offer != null || state.over || state.last?.flipped == true || state.turn % 5 == 0)
                    assertEquals(state, WordswornCodec.decode(WordswornCodec.encode(state)), "${level.name}, ${hero.name}, fight ${state.fight}, turn ${state.turn}")
            }
            assertTrue(end.over, "run must finish rather than stall on an empty offer")
        }
    }

    private fun bot(start: Wordsworn, check: (Wordsworn) -> Unit = {}): Wordsworn {
        var g = start
        var guard = 0
        while (!g.over && guard++ < 600) {
            g = when (val o = g.offer) {
                null -> {
                    // Use any affordable item that adds hits or blocks, then the best word.
                    val k = g.items.indices.firstOrNull { g.itemProblem(it) == null && g.items[it].kind != ItemKind.GOLD_LEAF }
                    if (k != null) g.useItem(k)!! else g.bestPlay()?.let { (p, s) -> g.play(p, s) } ?: g.pass()!!
                }
                is Offer.Shop -> o.goods.indices.firstOrNull { it !in o.sold && g.stars >= o.goods[it].price && o.goods[it] != Goods.ForUpgrade &&
                    (o.goods[it] != Goods.ForRest || g.hp < g.maxHp - 5) }?.let { g.buy(it) } ?: g.choose(-1)!!
                is Offer.Replace -> g.choose(g.deck.indices.filter { !g.deck[it].blot }.minBy { i -> g.deck[i].let { it.left.blocks + it.right.hits } })!!
                Offer.Upgrade -> g.choose(g.deck.indices.first { !g.deck[it].upgraded && !g.deck[it].blot })!!
                is Offer.Keepsakes -> g.choose(if (o.sides.isEmpty()) -1 else 0)!!
                else -> g.choose(0) ?: error("stuck on $o")
            }
            check(g)
        }
        return g
    }

    @Test fun `a save is the moves replayed`() {
        var g = Wordsworn.start(9, LogicLevel.HARD).choose(1)!!
        repeat(3) { g = g.bestPlay()?.let { (p, s) -> g.play(p, s) } ?: g.pass()!! }
        g = g.hinted()
        assertEquals(g, WordswornCodec.decode(WordswornCodec.encode(g)))
        assertNull(WordswornCodec.decode("junk"))
        assertNull(WordswornCodec.decode("1\nEASY\n3\n0"), "old saves don't load")
    }
}
