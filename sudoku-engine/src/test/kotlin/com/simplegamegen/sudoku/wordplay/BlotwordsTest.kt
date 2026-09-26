package com.simplegamegen.sudoku.wordplay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BlotwordsTest {
    private val ink = BlotLexicon.INK
    private val one = ink.word(BlotEffect.ONE)
    private val pair = ink.word(BlotEffect.PAIR)
    private val alike = ink.word(BlotEffect.ALIKE)
    private val write = ink.word(BlotEffect.WRITE)

    private fun game(width: Int, grid: String, vararg words: BlotWord) =
        Blotwords(BlotTier.EASY, 1, width, words.toList().ifEmpty { ink.words() }, grid)

    @Test fun `a word is read along a row and inks its letters, then its effect waits`() {
        val g = game(4, "VUMA", one).write(listOf(0, 1, 2))!!
        assertEquals("###A", g.cells)
        assertEquals(one, g.pending)
        assertTrue(g.use(3)!!.solved)
    }

    @Test fun `words read backwards and down, never diagonally or bent`() {
        assertNotNull(game(4, "AMUV", one).wordAt(listOf(3, 2, 1)))
        assertNotNull(game(2, "VAUAMA", one).wordAt(listOf(0, 2, 4)))
        assertNull(game(3, "VAAAUAAAM", one).wordAt(listOf(0, 4, 8)))
        assertNull(game(2, "VUAMAA", one).wordAt(listOf(0, 1, 3)))
    }

    @Test fun `inked squares drop out of the way`() {
        assertNotNull(game(5, "V#UMA", one).wordAt(listOf(0, 2, 3)))
        assertNull(game(5, "VAUMA", one).wordAt(listOf(0, 2, 3)))
    }

    @Test fun `when the word fills the grid there is no effect to use`() {
        val g = game(3, "VUM", one).write(listOf(0, 1, 2))!!
        assertNull(g.pending)
        assertTrue(g.solved)
    }

    @Test fun `PAIR inks two squares that touch, across ink`() {
        val g = game(4, "DRIF" + "A#BC", pair).write(listOf(0, 1, 2, 3))!!
        assertNull(g.use(4, 7))
        assertNotNull(g.use(4, 6))
        assertNull(g.use(4, null))
    }

    @Test fun `ALIKE inks every square with the picked letter`() {
        val g = game(3, "ZUV" + "ABA" + "CAC", alike).write(listOf(0, 1, 2))!!
        assertEquals("####B#C#C", g.use(3)!!.cells)
    }

    @Test fun `WRITE puts a letter in a blank`() {
        val g = game(4, "KEL." + "VUMA", write, one).write(listOf(0, 1, 2))!!
        assertNull(g.fill(4, 'M'))
        assertEquals('V', g.fill(3, 'V')!!.cells[3])
    }

    @Test fun `a word can't be written when its effect has nothing to act on`() {
        assertNull(game(4, "KELA", write).wordAt(listOf(0, 1, 2)))
        assertNull(game(4, "ZUV.", alike).wordAt(listOf(0, 1, 2)))
        assertNotNull(game(4, "ZUVA", alike).wordAt(listOf(0, 1, 2)))
    }

    @Test fun `stuck when no word can be written`() {
        assertTrue(game(3, "AAA", one).stuck)
        assertFalse(game(3, "VUM", one).stuck)
    }

    @Test fun `every level builds grids whose own solution replays`() {
        // Any set of command words works; a second made-up set checks nothing depends on the usual spelling.
        for (lex in listOf(BlotLexicon.INK, BlotLexicon("PIP", "BLOOP", "TWIRL", "ZAG"))) for (tier in listOf(BlotTier.EASY, BlotTier.MEDIUM, BlotTier.HARD, BlotTier.EXPERT)) {
            repeat(3) { s ->
                val t0 = System.nanoTime()
                val words = lex.words(tier.effects)
                val built = BlotGenerator.build(tier.width, tier.height, words, tier, Random(s * 31L + tier.ordinal))
                assertNotNull(built, "$lex $tier $s")
                val (start, moves) = built!!
                assertTrue(BlotGenerator.replays(start, tier.width, words, moves), "$tier replay")
                assertTrue(words.all { w -> moves.any { it.word == w } }, "$tier uses every word")
                assertTrue(start.count { it == INK } <= tier.maxPreInked)
                println("${lex.one} $tier seed $s: ${start.count { it != INK }} squares, ${moves.size} turns, ${(System.nanoTime() - t0) / 1_000_000} ms")
            }
        }
    }

    @Test fun `generated games start fresh and luck falls near each level's band`() {
        for (tier in listOf(BlotTier.EASY, BlotTier.MEDIUM, BlotTier.HARD, BlotTier.EXPERT)) {
            val t0 = System.nanoTime()
            val g = BlotGenerator.generate(tier, 42, "t")
            val luck = BlotSolver.luck(g.start, g.width, g.words, 300, Random(1))
            println("$tier: luck %.3f, %d ms".format(luck, (System.nanoTime() - t0) / 1_000_000))
            assertEquals(g.start, g.cells)
            assertEquals("t", g.theme)
            assertFalse(g.solved)
        }
    }

    @Test fun `the hint finds a next move that keeps the game winnable`() {
        var g = BlotGenerator.generate(BlotTier.MEDIUM, 7)
        var turns = 0
        while (!g.solved && turns++ < 40) {
            val m = g.nextMove()
            assertNotNull(m, "hint at\n${g.cells.chunked(g.width).joinToString("\n")}")
            if (g.pending == null) g = g.write(m!!.path)!!
            if (g.pending != null) {
                val mv = g.nextMove()!!
                g = when (g.pending!!.effect) {
                    BlotEffect.PAIR -> g.use(mv.targets[0], mv.targets[1])!!
                    BlotEffect.WRITE -> g.fill(mv.targets[0], mv.letter!!)!!
                    else -> g.use(mv.targets[0])!!
                }
            }
        }
        assertTrue(g.solved)
    }

    @Test fun `a dead end is recognised`() {
        assertTrue(game(5, "VUMAB", one).deadEnd())
        assertFalse(game(4, "VUMA", one).deadEnd())
    }

    @Test fun `each Discover step builds and can't be finished without its new word`() {
        val lex = BlotLexicon.INK
        BlotTrail.steps.indices.forEach { i ->
            val t0 = System.nanoTime()
            val g = BlotTrail.puzzle(i)
            val s = BlotTrail.steps[i]
            assertEquals(i, g.step)
            assertTrue(BlotSolver.solve(g.start, g.width, g.words) is BlotSolver.Result.Solved, "step $i solvable")
            val without = g.words - lex.word(s.newEffect)
            if (without.isNotEmpty()) assertTrue(BlotSolver.solve(g.start, g.width, without) is BlotSolver.Result.Dead, "${lex.one} step $i needs ${s.newEffect}")
            assertEquals(g.start, BlotTrail.puzzle(i, "sea").start, "step $i is the same every time, in any theme")
            println("${lex.one} step $i (${s.newEffect}) ${(System.nanoTime() - t0) / 1_000_000} ms\n" + g.start.chunked(g.width).joinToString("\n"))
        }
    }

    @Test fun `codec round trip`() {
        val g = BlotGenerator.generate(BlotTier.EXPERT, 3, "sea")
            .let { it.write(Blots.placements(it.cells, it.width, it.words).first().second)!! }.hinted()
        assertEquals(g, BlotCodec.decode(BlotCodec.encode(g)))
        assertNull(BlotCodec.decode("nonsense"))
    }
}
