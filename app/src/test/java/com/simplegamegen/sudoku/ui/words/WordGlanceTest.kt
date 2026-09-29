package com.simplegamegen.sudoku.ui.words

import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.wordplay.Card
import com.simplegamegen.sudoku.wordplay.Edge
import com.simplegamegen.sudoku.wordplay.EnglishLexicon
import com.simplegamegen.sudoku.wordplay.Letterfall
import com.simplegamegen.sudoku.wordplay.Hero
import com.simplegamegen.sudoku.wordplay.Lexicon
import com.simplegamegen.sudoku.wordplay.LexiconSense
import com.simplegamegen.sudoku.wordplay.Piece
import com.simplegamegen.sudoku.wordplay.Power
import com.simplegamegen.sudoku.wordplay.Quilt
import com.simplegamegen.sudoku.wordplay.Splay
import com.simplegamegen.sudoku.wordplay.Sprawl
import com.simplegamegen.sudoku.wordplay.Wordsworn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordGlanceTest {
    @Test fun `lookup is passed through and only a few senses are shown`() {
        val senses = listOf(
            LexiconSense("noun", "a feline", "The cat sat.", "oewn"),
            LexiconSense("verb", "to hoist"),
            LexiconSense("noun", "a whip"),
            LexiconSense("noun", "a fourth sense", "Later example.", "tatoeba_cc0"),
        )
        val lexicon = FakeLexicon(mapOf("CAT" to senses))
        val card = lexicon.glance("CAT")
        assertEquals(listOf("CAT"), lexicon.lookedUp)
        assertEquals("Cat", card?.lemma)
        assertEquals(3, card?.senses?.size)
        assertEquals(4, card?.totalSenses)
        assertEquals(1, card?.moreSenses)
        assertEquals("The cat sat.", card?.example)
        assertEquals("oewn", card?.exampleSource)
        assertEquals(listOf("a feline", "to hoist", "a whip"), card?.senses?.map { it.definition })
    }

    @Test fun `an example on a later sense is kept when the shown ones have none`() {
        val senses = listOf(
            LexiconSense("noun", "one"),
            LexiconSense("verb", "two"),
            LexiconSense("adjective", "three"),
            LexiconSense("adverb", "four", "A cat ran home.", "tatoeba_cc0"),
        )
        val card = glance("cat", senses)
        assertEquals("A cat ran home.", card?.example)
        assertEquals("tatoeba_cc0", card?.exampleSource)
        assertEquals(3, card?.senses?.size)
    }

    @Test fun `an unknown word still glances and a blank one does not`() {
        val lexicon = FakeLexicon(emptyMap())
        val missing = lexicon.glance("zzzzqqq")
        assertEquals("Zzzzqqq", missing?.lemma)
        assertTrue(missing?.senses?.isEmpty() == true)
        assertNull(missing?.example)
        assertEquals(listOf("zzzzqqq"), lexicon.lookedUp)
        assertNull(lexicon.glance("   "))
        assertNull(lexicon.glance("..."))
        assertEquals(listOf("zzzzqqq"), lexicon.lookedUp)
    }

    @Test fun `sprawl reports a word only when the path is accepted`() {
        val game = Sprawl(LogicLevel.EASY, 1L, 3, "CATORNSED")
        assertEquals("CAT", sprawlScoredWord(game, listOf(0, 1, 2)))
        assertNull(sprawlScoredWord(game.copy(found = listOf("CAT")), listOf(0, 1, 2)))
        assertNull(sprawlScoredWord(game, listOf(0, 2)))
        assertNull(sprawlScoredWord(game, listOf(0)))
        val quad = Sprawl(LogicLevel.EASY, 1L, 3, "QADORNEST")
        assertEquals("QUAD", quad.wordOf(listOf(0, 1, 2)))
        if (Lexicon.isWord("QUAD")) assertEquals("QUAD", sprawlScoredWord(quad, listOf(0, 1, 2)))
    }

    @Test fun `letterfall reports a word only when the path is accepted`() {
        val game = Letterfall(LogicLevel.EASY, 1L, 3, 3, "CATXXXXXX", movesLeft = 4)
        assertEquals("CAT", letterfallScoredWord(game, listOf(0, 1, 2)))
        assertNull(letterfallScoredWord(game, listOf(0, 1)))
        assertNull(letterfallScoredWord(game, listOf(0, 4, 8)))
        assertNull(letterfallScoredWord(game, listOf(6, 7, 8)))
    }

    @Test fun `quilt reports a run only when it becomes a word`() {
        val empty = quilt("....")
        val at = quilt("AT..")
        assertTrue(Lexicon.isWord("AT"))
        assertEquals(listOf("AT"), quiltWordsJustCompleted(empty, at))
        assertEquals("AT", primaryGlanceWord(quiltWordsJustCompleted(empty, at)))
        assertTrue(quiltWordsJustCompleted(at, at).isEmpty())
        assertTrue(quiltWordsJustCompleted(at, quilt("A...")).isEmpty())
        val broken = quiltWordsJustCompleted(at, quilt("XQ.."))
        assertTrue("AT" !in broken)
        if (!Lexicon.isWord("XQ")) assertTrue(broken.isEmpty())

        val it = quilt("IT..")
        if (Lexicon.isWord("IT")) assertEquals(listOf("IT"), quiltWordsJustCompleted(at, it))

        val both = quilt("ATON")
        val finished = quiltWordsJustCompleted(empty, both)
        assertTrue("AT" in finished)
        if (Lexicon.isWord("ON")) assertTrue("ON" in finished)
        assertEquals("AT", primaryGlanceWord(listOf("AT", "ON", "TO")))
        assertEquals("CAT", primaryGlanceWord(listOf("AT", "CAT", "ON")))
        assertNull(primaryGlanceWord(emptyList()))

        val kept = quiltWordsJustCompleted(at, both)
        assertTrue("AT" !in kept)
        assertEquals(emptyList<String>(), quiltWordsJustCompleted(empty, quilt("....", width = 4)))
    }

    @Test fun `wordsworn reports a spell the fight accepts`() {
        val game = fight("CATZ")
        val cat = listOf(Piece.Hand(0), Piece.Hand(1), Piece.Hand(2))
        assertEquals("CAT", wordswornScoredWord(game, cat))
        assertTrue(game.play(cat, Splay.RIGHT) != null)
        assertNull(wordswornScoredWord(game, listOf(Piece.Hand(0))))
        assertTrue(!Lexicon.isWord("CAZ"))
        assertNull(wordswornScoredWord(game, listOf(Piece.Hand(0), Piece.Hand(1), Piece.Hand(3))))
        assertNull(wordswornScoredWord(Wordsworn.start(1L, LogicLevel.EASY), cat))
    }

    private fun quilt(cells: String, width: Int = 2) = Quilt(
        level = LogicLevel.EASY,
        seed = 1L,
        width = width,
        shape = ".".repeat(cells.length),
        patches = List(cells.length) { 0 },
        answer = "A".repeat(cells.length),
        cells = cells,
    )

    private fun fight(letters: String): Wordsworn {
        val cards = letters.map { Card(it, Edge(), Edge(), Power.NONE) }
        return Wordsworn(
            level = LogicLevel.EASY,
            seed = 1L,
            hero = Hero.KNIGHT,
            offer = null,
            monsterHp = 30,
            hp = 20,
            maxHp = 20,
            deck = cards,
            hand = cards.indices.toList(),
        )
    }

    private class FakeLexicon(private val senses: Map<String, List<LexiconSense>>) : EnglishLexicon {
        val lookedUp = mutableListOf<String>()
        override fun lookup(lemma: String): List<LexiconSense> {
            lookedUp += lemma
            return senses[lemma].orEmpty()
        }
        override fun playable(lemma: String): Boolean = error("letter games keep Lexicon membership")
    }
}
