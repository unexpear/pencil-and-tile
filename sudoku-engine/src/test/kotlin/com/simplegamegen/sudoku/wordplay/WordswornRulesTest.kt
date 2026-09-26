package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WordswornRulesTest {
    private fun start(hero: Hero = Hero.KNIGHT) = Wordsworn.start(42, LogicLevel.MEDIUM).choose(hero.ordinal)!!
    private fun fight(): Wordsworn {
        val cards = listOf(Card('A', Edge(), Edge(hits = 1), Power.NONE), Card('T', Edge(), Edge(hits = 1), Power.NONE))
        return start().copy(monster = Monster.TYPO_IMP, monsterHp = 8, deck = cards, hand = listOf(0, 1),
            draw = emptyList(), discard = emptyList(), worn = emptyList(), ink = 0)
    }
    private val at = listOf(Piece.Hand(0), Piece.Hand(1))
    private fun shop(): Wordsworn = start().let { it.copy(offer = it.shop, stars = 20) }

    @Test fun `all levels visit one guardian and a boss in each of three books`() {
        for (level in LogicLevel.entries) for (seed in 1L..30L) {
            val route = Wordsworn.schedule(level, seed)
            assertEquals(6, route.size)
            route.chunked(2).forEachIndexed { book, pair ->
                assertEquals(listOf(book, book), pair.map { it.book })
                assertFalse(pair[0].boss)
                assertTrue(pair[1].boss)
            }
        }
    }

    @Test fun `heroes start with two core abilities one item and a finite library`() {
        for (hero in Hero.entries) {
            val g = start(hero)
            assertEquals(20, g.maxHp)
            assertEquals(2, g.items.count { it.kind.core })
            assertEquals(1, g.items.count { !it.kind.core })
            assertEquals(10, g.deck.size)
            assertEquals(50, g.library.size + g.shop!!.goods.count { it is Goods.ForLetter })
            assertEquals(4, g.hand.size)
        }
    }

    @Test fun `hex counters persist without automatic damage or decay`() {
        val g = fight().copy(monsterHexes = 4, hexes = 3)
        val next = g.play(at, Splay.RIGHT)!!
        assertEquals(6, next.monsterHp)
        assertEquals(4, next.monsterHexes)
        assertEquals(3, next.hexes)
        assertEquals(14, next.hp, "Typo Imp explicitly adds player hexes to its 3-hit attack")
        assertEquals(0, next.last!!.hexTaken)
    }

    @Test fun `hexes do not modify enemies without a matching rule`() {
        val g = fight().copy(monster = Monster.DUST_BUNNY, hexes = 8)
        assertEquals(2, g.attackOf(g.intent))
    }

    @Test fun `stars are spent by core abilities and cannot go negative`() {
        val g = fight().copy(stars = 1)
        val index = g.items.indexOfFirst { it.kind == ItemKind.SEAL }
        val after = g.useItem(index)!!
        assertEquals(0, after.stars)
        assertEquals(3, after.prep.blocks)
        assertNull(after.useItem(index))
        assertNull(after.copy(usedItems = emptySet()).useItem(index))
    }

    @Test fun `prep HP loss bypasses block flips and can end combat`() {
        var g = start(Hero.WITCH).copy(monster = Monster.PAPER_DRAGON, monsterHp = 2, act = 1, monsterHexes = 5, ink = 2)
        val index = g.items.indexOfFirst { it.kind == ItemKind.LENS }
        val flipped = g.useItem(index)!!
        assertEquals(2, flipped.stage)
        assertEquals(flipped.monsterMax - 3, flipped.monsterHp)
        assertTrue(flipped.stunned)
        assertEquals(5, flipped.monsterHexes)
        g = g.copy(stage = 2, monsterHp = 2)
        val beaten = g.useItem(index)!!
        assertTrue(beaten.offer is Offer.Paths)
        assertEquals(g.hp, beaten.hp)
    }

    @Test fun `penalty on top skips to letter and returns to finite supply`() {
        val g = fight().copy(deck = listOf(Card('X', Edge(), Edge(), Power.NONE, blot = true),
            Card('I', Edge(), Edge(hits = 1), Power.INK, 2)), penaltyStock = listOf('Q'))
        assertEquals(Piece.Hand(1), g.topOf(at, Splay.RIGHT))
        val next = g.play(at, Splay.RIGHT)!! // XI
        assertEquals(listOf('I'), next.deck.map { it.letter })
        assertEquals(listOf(0), next.worn)
        assertEquals(listOf('Q', 'X'), next.penaltyStock)
        assertEquals(3, next.ink)
    }

    @Test fun `enemy fatigue happens before current word enters the discard pile`() {
        val g = fight().copy(monster = Monster.WORD_EATER, hp = 50)
        val next = g.play(at, Splay.RIGHT)!!
        assertEquals(listOf(0), next.worn, "the monster has no earlier discards to eat")
        assertEquals(listOf(1), next.hand)
    }

    @Test fun `temporary chosen letters do not consume an unused permanent wild`() {
        val g = fight().copy(prep = Prep(wilds = 1))
        val cat = listOf(Piece.Wild('C')) + at
        assertEquals(1, g.preview(cat, Splay.RIGHT).ink)
        assertEquals(2, g.copy(level = LogicLevel.EASY).preview(cat, Splay.RIGHT).ink)
    }

    @Test fun `battle cleanup does not grant unearned healing or stars`() {
        val g = fight().copy(stage = 2, monsterHp = 1, hp = 8, stars = 3, ink = 4, hexes = 2)
        val next = g.play(at, Splay.RIGHT)!!
        assertEquals(8, next.hp)
        assertEquals(3, next.stars)
        assertEquals(0, next.ink)
        assertEquals(0, next.hexes)
        val offer = next.offer as Offer.Paths
        assertTrue(offer.paths.all { p -> p.steps.any { it is Step.Cards && !it.add } })
    }

    @Test fun `shop purchase refills stock immediately and letter purchase must be resolved`() {
        val g = shop()
        val offered = (g.shop!!.goods[5] as Goods.ForLetter).card
        val nextCard = g.library.first()
        val paid = g.buy(5)!!
        assertEquals(19, paid.stars)
        assertEquals(nextCard, (paid.shop!!.goods[5] as Goods.ForLetter).card)
        assertNull(paid.choose(-1), "cannot abandon a paid card")
        val replaced = paid.choose(0)!!
        assertEquals(offered, replaced.deck[0])
        assertEquals(g.deck.size, replaced.deck.size)
        assertTrue(replaced.offer is Offer.Shop)
    }

    @Test fun `shop refresh is once per visit and conserves unowned cards`() {
        val g = shop()
        val before = g.library + g.shop!!.goods.filterIsInstance<Goods.ForLetter>().map { it.card }
        val next = g.refreshShop(2)!!
        val after = next.library + next.shop!!.goods.filterIsInstance<Goods.ForLetter>().map { it.card }
        assertEquals(before.groupingBy { it }.eachCount(), after.groupingBy { it }.eachCount())
        assertNull(next.refreshShop(0))
        assertEquals(g.stars, next.stars)
        assertEquals(g.shop!!.goods.take(5), next.shop!!.goods.take(5))
    }

    @Test fun `unbought shop cards persist into the next fight`() {
        val g = shop()
        val after = g.choose(-1)!!
        assertEquals(g.shop, after.shop)
        assertEquals(1, after.fight)
        assertTrue(after.monster.boss)
    }

    @Test fun `empty archive slots are safe and cannot be purchased`() {
        val g = shop().copy(itemStock = emptyList())
        val after = g.buy(0)!!
        assertEquals(Goods.Empty, after.shop!!.goods[0])
        assertNull(after.buy(0))
    }

    @Test fun `version 2 saves preserve original campaign and hex rules`() {
        val original = Wordsworn.start(42, LogicLevel.EASY, 2).choose(1)!!.pass()!!
        val restored = WordswornCodec.decode(WordswornCodec.encode(original))!!
        assertEquals(original, restored)
        assertEquals(2, restored.rulesVersion)
        assertEquals(3, restored.fights)
        assertEquals(1, restored.items.count { it.kind.core })
        assertTrue(WordswornCodec.encode(restored).startsWith("2\n"))
    }

    @Test fun `version 3 saves replay core item turns exactly`() {
        var g = start(Hero.WITCH)
        repeat(3) {
            if (g.fighting) {
                g = g.bestPlay()?.let { (pieces, splay) -> g.play(pieces, splay) } ?: g.pass()!!
                val item = g.items.indices.firstOrNull { g.itemProblem(it) == null }
                if (item != null) g = g.useItem(item)!!
            }
            assertEquals(g, WordswornCodec.decode(WordswornCodec.encode(g)))
        }
    }

    @Test fun `all alternate core combinations retain one star core and one hex core`() {
        for (hero in Hero.entries) for (loadout in 0..3) {
            val g = Wordsworn.start(4, LogicLevel.MEDIUM).choose(hero.ordinal + 3 * loadout)!!
            assertEquals(hero.cores(loadout), g.items.filter { it.kind.core }.map { it.kind })
            assertEquals(g, WordswornCodec.decode(WordswornCodec.encode(g)))
            val ready = g.copy(ink = 5, stars = 2, monsterHexes = 4, hp = 10,
                hand = g.hand.drop(1), worn = listOf(g.hand.first()))
            for (i in 0..1) {
                val after = ready.useItem(i)
                assertNotNull(after, "${hero.name}, loadout $loadout, core $i")
                assertTrue(after!!.stars >= 0 && after.monsterHexes >= 0 && after.ink >= 0)
                assertNull(after.useItem(i))
            }
        }
    }

    @Test fun `hints consider the ability on a weaker duplicate letter`() {
        val g = fight().copy(deck = listOf(
            Card('A', Edge(blocks = 9), Edge(), Power.NONE),
            Card('A', Edge(), Edge(hits = 1), Power.HIT, 20),
            Card('T', Edge(), Edge(hits = 1), Power.NONE)), hand = listOf(0, 1, 2))
        val hint = g.bestPlay()!!
        assertNull(g.problem(hint.first))
        assertTrue(g.preview(hint.first, hint.second).hits >= 20)
        assertEquals(Piece.Hand(1), g.topOf(hint.first, hint.second))
    }

    @Test fun `replay rejects malformed and out of phase tokens`() {
        for (bad in listOf("h", "xjunk", "c12", "c0 pQh0.h1", "c0 pRwAB.h0", "c0 pRvjunk.h0", "c0 r0")) {
            assertNull(WordswornCodec.decode("3\nMEDIUM\n42\n$bad"), bad)
        }
    }

    @Test fun `stage change retains enemy stars unless its own rule resets them`() {
        val g = fight().copy(monsterHp = 1, monsterStars = 4)
        val next = g.play(at, Splay.RIGHT)!!
        assertEquals(2, next.stage)
        assertEquals(4, next.monsterStars)
        assertTrue(next.last!!.stunned)
    }

    @Test fun `rule modifiers combine before setup and replay exactly`() {
        var g = Wordsworn.start(5, LogicLevel.MEDIUM)
        for (twist in WordswornTwist.entries) g = g.toggleTwist(twist.ordinal)!!
        g = g.choose(0)!!
        assertEquals(0, g.twistScore)
        assertEquals(25, g.maxHp)
        assertEquals(2, g.permanentWilds)
        assertEquals(2, g.deck.count { it.blot })
        assertEquals(g, WordswornCodec.decode(WordswornCodec.encode(g)))
        assertNull(g.toggleTwist(0))
    }

    @Test fun `mandatory reward choices cannot be skipped and health loss can end a run`() {
        val g = fight().copy(hp = 1, offer = Offer.Cards(listOf(Card('E', Edge(), Edge(), Power.NONE)), true), todo = listOf(Step.Hurt(3)))
        assertNull(g.choose(-1))
        val next = g.choose(0)!!
        assertTrue(next.lost)
        assertNull(next.offer)
    }
}
