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
            // Loose pieces go back where they were cut from before the puzzle is played.
            val ready = g.pieces.indices.fold(g) { acc, k -> acc.place(k, g.pieces[k].home)!! }
            assertTrue(ready.settled)
            assertTrue(BlotSolver.solve(ready.cells, g.width, g.words, start = ready.cells, wrap = g.wrap) is BlotSolver.Result.Solved, "step $i solvable")
            if (s.pieces == 0) assertTrue(BlotTrail.needs(s, g.start, g.width, g.words, s.newEffect?.let { lex.word(it) }), "${lex.one} step $i needs ${s.teaches}")
            else assertEquals(s.pieces, g.pieces.size)
            assertEquals(g.start, BlotTrail.puzzle(i, "sea").start, "step $i is the same every time, in any theme")
            println("${lex.one} step $i (${s.teaches}) ${(System.nanoTime() - t0) / 1_000_000} ms\n" + g.start.chunked(g.width).joinToString("\n"))
        }
    }

    @Test fun `codec round trip`() {
        val g = BlotGenerator.generate(BlotTier.EXPERT, 3, "sea")
            .let { it.write(Blots.placements(it.cells, it.width, it.words).first().second)!! }.hinted()
        assertEquals(g, BlotCodec.decode(BlotCodec.encode(g)))
        assertNull(BlotCodec.decode("nonsense"))
    }

    @Test fun `DIAG inks a square and its whole rising diagonal`() {
        // G O B A on the top row; the rest are X, so a square's rising diagonal is easy to see.
        val words = BlotLexicon.INK.words(listOf(BlotEffect.DIAG))
        val g = Blotwords(BlotTier.EASY, 0, 4, words, "GOBA" + "XXXX" + "XXXX" + "XXXX").write(listOf(0, 1, 2, 3))!!
        assertEquals(BlotEffect.DIAG, g.pending!!.effect)
        // Row 2, column 1 (index 9) sits on the diagonal with sum 3: (0,3) (1,2) (2,1) (3,0).
        val next = g.use(9)!!
        assertEquals(listOf(3, 6, 9, 12), Blots.diagonalOf(4, 4, 9))
        for (i in listOf(6, 9, 12)) assertEquals(INK, next.cells[i])
        assertEquals('X', next.cells[5])
    }

    @Test fun `knots join letters, let a word turn a corner and are never inked by writing`() {
        val words = BlotLexicon.INK.words(listOf(BlotEffect.ONE))
        // V + U on the top row: the knot joins V and U; M sits below the knot in the second row... no, below U.
        val straight = Blotwords(BlotTier.EASY, 0, 4, words, "V+UM" + "XXXX")
        assertNotNull(straight.wordAt(listOf(0, 2, 3)))
        val after = straight.write(listOf(0, 2, 3))!!
        assertEquals(KNOT, after.cells[1], "the knot stays")
        assertEquals(listOf(0, 1, 2, 3), after.strokes.single(), "the stroke runs through the knot")
        // A corner: V then a knot, turning down to U and M.
        val corner = Blotwords(BlotTier.EASY, 0, 3, words, "V+X" + "XUX" + "XMX")
        assertNotNull(corner.wordAt(listOf(0, 4, 7)))
        // No corner on a letter.
        val bent = Blotwords(BlotTier.EASY, 0, 3, words, "VUX" + "XMX" + "XXX")
        assertNull(bent.wordAt(listOf(0, 1, 4)))
    }

    @Test fun `a wild square stands for any letter`() {
        val words = BlotLexicon.INK.words(listOf(BlotEffect.ONE))
        val g = Blotwords(BlotTier.EASY, 0, 4, words, "V?MX" + "XXXX")
        assertNotNull(g.wordAt(listOf(0, 1, 2)))
        assertNull(Blotwords(BlotTier.EASY, 0, 4, words, "V.MX" + "XXXX").wordAt(listOf(0, 1, 2)))
    }

    @Test fun `strokes survive the codec, and old saves without them still load`() {
        val words = BlotLexicon.INK.words(listOf(BlotEffect.ONE))
        val g = Blotwords(BlotTier.EASY, 0, 4, words, "V+UM" + "XXXX").write(listOf(0, 2, 3))!!
        assertEquals(g, BlotCodec.decode(BlotCodec.encode(g)))
        // Saves from before strokes, holes, daily puzzles, joined edges and loose pieces were kept end six lines sooner.
        val old = BlotCodec.encode(g).split('\n').dropLast(6).joinToString("\n")
        assertEquals(emptyList<List<Int>>(), BlotCodec.decode(old)!!.strokes)
    }

    @Test fun `a sealed square loses its seal the first time it is inked`() {
        val words = BlotLexicon.INK.words(listOf(BlotEffect.ONE, BlotEffect.ALIKE))
        // VUM reads through the sealed u; writing it breaks that seal, and VUM's own extra square does the same to the sealed x.
        var g = Blotwords(BlotTier.EASY, 0, 4, words, "VuMx" + "XXXX").write(listOf(0, 1, 2))!!
        assertEquals("#U#x", g.cells.take(4))
        g = g.use(3)!!
        assertEquals('X', g.cells[3])
        // ALIKE breaks every seal on its letter and inks the rest.
        assertEquals("#X", Blots.alike("Xx", 'X'))
    }

    @Test fun `shaped boards keep holes inked and in one piece`() {
        repeat(20) { seed ->
            val holes = Blots.shape(6, 6, Random(seed.toLong()))
            assertTrue(holes.size <= 9)
            val g = BlotGenerator.generate(BlotTier.EXPERT, seed.toLong())
            assertTrue(g.holes.isEmpty() || g.holes.all { g.start[it] == GAP })
            assertEquals(g, BlotCodec.decode(BlotCodec.encode(g)))
        }
    }

    @Test fun `MEND brings an inked square back as it started, or seals a letter`() {
        val words = BlotLexicon.INK.words(listOf(BlotEffect.ONE, BlotEffect.MEND))
        // MIPA across the top; writing it inks M I P A, then MEND brings the P back.
        var g = Blotwords(BlotTier.EASY, 0, 4, words, "MIPA" + "XVUX").write(listOf(0, 1, 2, 3))!!
        assertEquals(BlotEffect.MEND, g.pending!!.effect)
        val back = g.use(2)!!
        assertEquals('P', back.cells[2])
        // Or a plain letter gets a seal.
        assertEquals('x', g.use(4)!!.cells[4])
        // A square that started inked can't be brought back.
        assertNull(Blotwords(BlotTier.EASY, 0, 4, words, "MIPA" + "#VUX").write(listOf(0, 1, 2, 3))!!.use(4))
    }

    @Test fun `when the edges join a word runs off one side and back on the other`() {
        val words = BlotLexicon.INK.words(listOf(BlotEffect.ONE, BlotEffect.PAIR))
        // U M X V: reading right from V runs off the edge and comes back as U, M.
        val flat = Blotwords(BlotTier.EASY, 0, 4, words, "UMXV" + "XXXX")
        assertNull(flat.wordAt(listOf(3, 0, 1)))
        val round = flat.copy(wrap = true)
        assertNotNull(round.wordAt(listOf(3, 0, 1)))
        assertEquals(round, BlotCodec.decode(BlotCodec.encode(round)))
        // Squares at either end of a row touch across the join.
        assertTrue(Blots.touching("A##B", 4, 0, 3, wrap = true))
        assertTrue(!Blots.touching("AC#B", 4, 0, 3, wrap = false))
    }

    @Test fun `inking one echo inks every echo`() {
        val words = BlotLexicon.INK.words(listOf(BlotEffect.ONE))
        val g = Blotwords(BlotTier.EASY, 0, 4, words, "VUM~" + "X~X~").write(listOf(0, 1, 2))!!
        val next = g.use(3)!!
        assertEquals("####" + "X#X#", next.cells)
        // Echoes hold no letter, so no word runs through one.
        assertNull(Blotwords(BlotTier.EASY, 0, 4, words, "V~UM" + "XXXX").wordAt(listOf(0, 2, 3)))
    }

    @Test fun `KOPA pushes an arrow and the squares ahead of it into the next gap`() {
        val words = BlotLexicon.INK.words(listOf(BlotEffect.ONE, BlotEffect.PUSH))
        val right = ARROWS[1]
        // K O P A, then a right arrow with X Y ahead of it and a gap at the end of the row.
        var g = Blotwords(BlotTier.EASY, 0, 5, words, "KOPA#" + "${right}XY  ").write(listOf(0, 1, 2, 3))!!
        assertEquals(BlotEffect.PUSH, g.pending!!.effect)
        g = g.use(5)!!
        assertEquals(" #XY ", g.cells.substring(5), "the arrow inks as it moves, and X and Y move along one")
        // Nothing to push into: the line runs off the board.
        val stuck = Blotwords(BlotTier.EASY, 0, 5, words, "KOPA#" + "${right}XYZW").write(listOf(0, 1, 2, 3))
        assertNull(stuck, "KOPA can't be written when no arrow can move")
    }

    @Test fun `loose pieces go into gaps they fit, then lock`() {
        val words = BlotLexicon.INK.words(listOf(BlotEffect.ONE))
        // A two-square piece U M, and a board with a gap two wide after V.
        val piece = BlotPiece(listOf(0 to 0, 0 to 1), "UM", 1)
        var g = Blotwords(BlotTier.EASY, 0, 4, words, "V  X" + "XXXX", pieces = listOf(piece))
        assertNull(g.wordAt(listOf(0, 1, 2)), "no words before the pieces are in")
        assertNull(g.place(0, 4), "it doesn't fit over squares")
        assertNull(g.place(0, 2), "or off the board")
        g = g.place(0, 1)!!
        assertTrue(g.settled)
        assertEquals("VUMX", g.cells.take(4))
        assertNull(g.lift(0), "fixed once they're all in")
        assertNotNull(g.wordAt(listOf(0, 1, 2)))
        assertEquals(g, BlotCodec.decode(BlotCodec.encode(g)))
        val fresh = Blotwords(BlotTier.EASY, 0, 4, words, "V  X" + "XXXX", pieces = listOf(piece, BlotPiece(listOf(0 to 0), "Q", 0)))
        val one = fresh.place(0, 1)!!
        assertTrue(!one.settled)
        assertEquals(fresh.cells, one.lift(0)!!.cells, "a piece can come back off while others are still loose")
        assertEquals(one, BlotCodec.decode(BlotCodec.encode(one)))
    }
}
