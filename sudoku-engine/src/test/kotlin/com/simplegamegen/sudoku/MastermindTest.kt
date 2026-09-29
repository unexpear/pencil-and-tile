package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.KeyPegs
import com.simplegamegen.sudoku.duels.Mastermind
import com.simplegamegen.sudoku.duels.MastermindCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MastermindTest {
    private fun place(game: Mastermind, colors: List<Int>): Mastermind {
        var next = game
        for (color in colors) next = checkNotNull(next.place(color)) { "place $color" }
        return next
    }

    private fun guess(game: Mastermind, colors: List<Int>) = checkNotNull(place(game, colors).submit())

    @Test fun `key pegs are counts and a repeated color cannot take two keys`() {
        assertEquals(KeyPegs(4, 0), Mastermind.score(listOf(0, 1, 2, 3), listOf(0, 1, 2, 3)))
        assertEquals(KeyPegs(0, 4), Mastermind.score(listOf(0, 1, 2, 3), listOf(3, 2, 1, 0)))
        assertEquals(KeyPegs(1, 2), Mastermind.score(listOf(0, 1, 2, 3), listOf(0, 2, 1, 5)))
        assertEquals(KeyPegs(2, 0), Mastermind.score(listOf(0, 0, 1, 2), listOf(0, 1, 1, 1)))
        assertEquals(KeyPegs(1, 1), Mastermind.score(listOf(0, 0, 1, 2), listOf(0, 1, 3, 1)))
        assertEquals(KeyPegs(2, 2), Mastermind.score(listOf(0, 0, 1, 1), listOf(0, 1, 0, 1)))
        val secret = listOf(0, 1, 2, 3)
        assertEquals(KeyPegs(0, 3), Mastermind.score(secret, listOf(3, 2, 0, 5)))
        assertEquals(Mastermind.score(secret, listOf(3, 2, 0, 5)), Mastermind.score(secret, listOf(2, 3, 0, 5)))
        assertEquals(KeyPegs(1, 1), Mastermind.score(listOf(0, 4, 0, 2), listOf(0, 3, 4, 5)))
    }

    @Test fun `an exact guess wins and running out of guesses loses`() {
        val secret = listOf(1, 4, 2, 0)
        val won = guess(Mastermind(3, 1, secret), secret)
        assertTrue(won.won)
        assertFalse(won.lost)
        assertTrue(won.over)
        assertEquals(KeyPegs(4, 0), won.feedback.single())
        assertNull(won.place(0))
        assertNull(won.submit())
        assertNull(won.backspace())
        assertEquals("The game is over.", won.problem())

        var lost = Mastermind(3, 2, secret)
        repeat(Mastermind.GUESSES) { lost = guess(lost, listOf(5, 5, 5, 5)) }
        assertTrue(lost.lost)
        assertFalse(lost.won)
        assertEquals(Mastermind.GUESSES, lost.guesses.size)
        assertEquals(0, lost.guessesLeft)
        assertNull(lost.place(1))
        assertEquals(lost, MastermindCodec.decode(MastermindCodec.encode(lost)))
    }

    @Test fun `illegal colors lengths and difficulty guesses are refused`() {
        val easy = Mastermind.start(3, 0)
        assertNull(easy.place(-1))
        assertNull(easy.place(Mastermind.COLORS))
        assertNull(easy.submit())
        assertNull(easy.backspace())
        assertEquals("Fill all four pegs.", easy.place(0)!!.problem())

        val repeated = place(easy, listOf(0, 0, 1, 2))
        assertEquals(Mastermind.CONSTRAINTS[0], repeated.problem())
        assertNull(repeated.submit())
        assertEquals(listOf(0, 0, 1, 2), repeated.draft)

        val outside = place(easy, listOf(0, 1, 2, 5))
        assertNull(outside.submit())

        val full = place(easy, listOf(0, 1, 2, 3))
        assertNull(full.place(1))
        assertNotNull(full.submit())

        val medium = Mastermind.start(4, 1)
        assertNull(place(medium, listOf(0, 0, 1, 2)).submit())
        assertNotNull(place(medium, medium.secret).submit())

        val hard = Mastermind.start(5, 2)
        assertNull(place(hard, listOf(0, 1, 2, 3)).submit())
        assertNull(place(hard, listOf(4, 4, 4, 4)).submit())
        assertEquals(Mastermind.CONSTRAINTS[2], place(hard, listOf(4, 4, 4, 4)).problem())
        assertNotNull(place(hard, hard.secret).submit())

        val expert = Mastermind(3, 8, listOf(2, 2, 2, 2))
        assertNotNull(place(expert, listOf(0, 0, 0, 0)).submit())
        assertNotNull(place(expert, listOf(0, 1, 2, 3)).submit())

        assertThrows(IllegalArgumentException::class.java) { Mastermind(0, 1, listOf(0, 0, 1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { Mastermind(1, 1, listOf(0, 0, 1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { Mastermind(2, 1, listOf(0, 1, 2, 3)) }
    }

    @Test fun `each difficulty constrains the secret and the same seed repeats`() {
        repeat(40) { n ->
            val seed = n.toLong()
            val easy = Mastermind.start(seed, 0).secret
            assertEquals(setOf(0, 1, 2, 3), easy.toSet(), "easy $seed")
            val medium = Mastermind.start(seed, 1).secret
            assertEquals(4, medium.toSet().size, "medium $seed")
            assertTrue(medium.all { it in 0 until Mastermind.COLORS })
            val hard = Mastermind.start(seed, 2).secret
            assertTrue(hard.toSet().size in 2..3, "hard $hard")
            val expert = Mastermind.start(seed, 3).secret
            assertEquals(4, expert.size)
            assertTrue(expert.all { it in 0 until Mastermind.COLORS })
            for (setting in 0..3) {
                assertEquals(Mastermind.start(seed, setting), Mastermind.start(seed, setting))
                assertTrue(Mastermind.fits(setting, Mastermind.secret(seed, setting)))
            }
        }
        assertTrue((0 until 24).map { Mastermind.start(it.toLong(), 0).secret }.toSet().size > 1)
        val experts = (0 until 60L).map { Mastermind.start(it, 3).secret }
        assertTrue(experts.any { it.toSet().size == 4 })
        assertTrue(experts.any { it.toSet().size < 4 })
        assertEquals((0 until Mastermind.COLORS).toSet(), experts.flatten().toSet())
    }

    @Test fun `saves round-trip the secret guesses feedback and draft and reject corruption`() {
        var game = Mastermind(3, 11, listOf(0, 0, 1, 2))
        game = guess(game, listOf(0, 1, 0, 3))
        assertEquals(KeyPegs(1, 2), game.feedback.single())
        game = game.place(2)!!.place(2)!!
        assertEquals(game, MastermindCodec.decode(MastermindCodec.encode(game)))
        assertEquals(listOf(0, 0, 1, 2), MastermindCodec.decode(MastermindCodec.encode(game))!!.secret)

        val fresh = Mastermind.start(6, 1)
        assertEquals(fresh, MastermindCodec.decode(MastermindCodec.encode(fresh)))

        assertNull(MastermindCodec.decode("bad"))
        assertNull(MastermindCodec.decode(MastermindCodec.encode(game).replaceFirst("1", "2")))
        val lines = MastermindCodec.encode(game).split('\n').toMutableList()
        lines[5] = "2,1"
        assertNull(MastermindCodec.decode(lines.joinToString("\n")))
        lines[5] = "1,2"
        lines[3] = "5,5,5,5"
        assertNull(MastermindCodec.decode(lines.joinToString("\n")))
        assertNull(MastermindCodec.decode(listOf("1", "0", "5", "0,0,1,1", "", "", "").joinToString("\n")))
        assertNull(MastermindCodec.decode(listOf("1", "3", "5", "0,1", "", "", "").joinToString("\n")))
        assertNull(MastermindCodec.decode(listOf("1", "3", "5", "0,1,2,3", "0,1,2,3;5,5,5,5", "4,0;0,0", "").joinToString("\n")))
        assertNull(MastermindCodec.decode(listOf("1", "9", "1", "0,1,2,3", "", "", "").joinToString("\n")))
        val won = guess(Mastermind(3, 1, listOf(0, 1, 2, 3)), listOf(0, 1, 2, 3))
        val extra = MastermindCodec.encode(won).split('\n').toMutableList()
        extra[4] = extra[4] + ";5,5,5,5"
        extra[5] = extra[5] + ";0,0"
        assertNull(MastermindCodec.decode(extra.joinToString("\n")))
        extra[4] = "0,1,2,3"
        extra[5] = "4,0"
        extra[6] = "1"
        assertNull(MastermindCodec.decode(extra.joinToString("\n")))
    }
}
