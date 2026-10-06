package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.Rps
import com.simplegamegen.sudoku.duels.RpsAi
import com.simplegamegen.sudoku.duels.RpsCodec
import com.simplegamegen.sudoku.duels.RpsMove
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RpsTest {
    private fun at(row: Int, col: Int, size: Int) = row * size + col

    /** A legal mid-game position. [level] 0–3 keeps the board size and sets the computer strength. */
    private fun placed(size: Int, pieces: List<Pair<Int, Int>>, level: Int = 0, turn: Int = 1): Rps {
        val setting = if (size <= 5) level else 4 + level
        val cells = MutableList(size * size) { 0 }
        for ((index, piece) in pieces) cells[index] = piece
        return Rps(setting, 1, cells, turn)
    }

    @Test fun `paper beats rock, rock beats scissors, scissors beats paper`() {
        assertTrue(Rps.beats(Rps.PAPER, Rps.ROCK))
        assertTrue(Rps.beats(Rps.ROCK, Rps.SCISSORS))
        assertTrue(Rps.beats(Rps.SCISSORS, Rps.PAPER))
        assertTrue(Rps.beats(-Rps.PAPER, Rps.ROCK))
        assertFalse(Rps.beats(Rps.ROCK, Rps.PAPER))
        assertFalse(Rps.beats(Rps.ROCK, Rps.ROCK))
        assertFalse(Rps.beats(Rps.PAPER, Rps.SCISSORS))
        assertFalse(Rps.beats(Rps.SCISSORS, Rps.ROCK))
        assertFalse(Rps.beats(Rps.SCISSORS, Rps.SCISSORS))
        assertFalse(Rps.beats(Rps.PAPER, Rps.PAPER))
    }

    @Test fun `six neighbors touch and a piece steps onto exactly those hexes`() {
        val size = 7
        val mid = at(3, 3, size)
        val around = Rps.neighbors(size, mid).toSet()
        assertEquals(6, around.size)
        assertEquals(setOf(at(2, 3, size), at(2, 4, size), at(3, 2, size), at(3, 4, size), at(4, 2, size), at(4, 3, size)), around)
        assertFalse(at(2, 2, size) in around)
        assertFalse(at(4, 4, size) in around)
        assertEquals(2, Rps.neighbors(5, at(0, 0, 5)).size)

        val game = placed(size, pieces = listOf(mid to Rps.ROCK, at(0, 0, size) to -Rps.SCISSORS))
        assertEquals(around, game.destinations(mid).toSet())
        val step = game.play(mid, at(2, 3, size))
        assertNotNull(step)
        assertEquals(0, step!!.cells[mid])
        assertEquals(Rps.ROCK, step.cells[at(2, 3, size)])
        assertEquals(-1, step.turn)
        assertEquals(0, step.winner)
        assertEquals(mid, step.lastFrom)
        assertEquals(at(2, 3, size), step.lastTo)
        assertNull(game.play(mid, at(2, 2, size)))
        assertNull(game.play(mid, at(4, 4, size)))
        assertNull(game.play(mid, at(1, 3, size)))
    }

    @Test fun `a piece captures only an adjacent enemy it beats`() {
        val size = 5
        val rock = at(2, 2, size)
        val beside = at(2, 3, size)
        val far = at(2, 4, size)
        val paperLeft = at(0, 0, size)

        val capture = placed(size, pieces = listOf(rock to Rps.ROCK, beside to -Rps.SCISSORS, paperLeft to -Rps.PAPER))
        assertTrue(beside in capture.destinations(rock))
        val taken = capture.play(rock, beside)!!
        assertEquals(0, taken.winner)
        assertEquals(-1, taken.turn)
        assertEquals(Rps.ROCK, taken.cells[beside])
        assertEquals(0, taken.cells[rock])
        assertEquals(-Rps.PAPER, taken.cells[paperLeft])
        assertEquals(0, taken.count(-1, Rps.SCISSORS))
        assertEquals(1, taken.taken(1, Rps.SCISSORS))
        assertEquals(taken, RpsCodec.decode(RpsCodec.encode(taken)))

        val loses = placed(size, pieces = listOf(rock to Rps.ROCK, beside to -Rps.PAPER))
        assertFalse(beside in loses.destinations(rock))
        assertNull(loses.play(rock, beside))

        val same = placed(size, pieces = listOf(rock to Rps.ROCK, beside to -Rps.ROCK))
        assertFalse(beside in same.destinations(rock))
        assertNull(same.play(rock, beside))

        val own = placed(size, pieces = listOf(rock to Rps.ROCK, beside to Rps.PAPER, paperLeft to -Rps.SCISSORS))
        assertFalse(beside in own.destinations(rock))
        assertNull(own.play(rock, beside))

        val jump = placed(size, pieces = listOf(rock to Rps.ROCK, far to -Rps.SCISSORS))
        assertFalse(far in jump.destinations(rock))
        assertNull(jump.play(rock, far))
        assertTrue(beside in jump.destinations(rock))

        val scissors = placed(size, pieces = listOf(rock to Rps.SCISSORS, beside to -Rps.PAPER, paperLeft to -Rps.ROCK))
        assertNotNull(scissors.play(rock, beside))
        val paper = placed(size, pieces = listOf(rock to Rps.PAPER, beside to -Rps.ROCK, paperLeft to -Rps.SCISSORS))
        assertNotNull(paper.play(rock, beside))
        assertNull(paper.play(rock, paperLeft))
    }

    @Test fun `capturing the last enemy wins, and so does leaving them with no move`() {
        val size = 5
        val rock = at(2, 2, size)
        val prey = at(2, 3, size)
        val last = placed(size, pieces = listOf(rock to Rps.ROCK, prey to -Rps.SCISSORS))
        val won = last.play(rock, prey)!!
        assertEquals(1, won.winner)
        assertTrue(won.over)
        assertEquals(1, won.turn)
        assertEquals(0, won.cells.count { it < 0 })
        assertTrue(won.legalMoves().isEmpty())
        assertNull(won.play(prey, rock))
        assertEquals(won, RpsCodec.decode(RpsCodec.encode(won)))

        val board = 7
        val scissors = at(0, 0, board)
        val blocker = at(1, 0, board)
        val mover = at(0, 2, board)
        val gap = at(0, 1, board)
        val stuck = placed(board, pieces = listOf(scissors to -Rps.SCISSORS, blocker to Rps.ROCK, mover to Rps.ROCK))
        assertTrue(gap in stuck.destinations(mover))
        assertTrue(stuck.legalMoves().any { it.from == mover && it.to == gap })
        val walled = stuck.play(mover, gap)!!
        assertEquals(1, walled.winner)
        assertEquals(-Rps.SCISSORS, walled.cells[scissors])
        assertEquals(Rps.ROCK, walled.cells[gap])
        assertEquals(Rps.ROCK, walled.cells[blocker])
        assertTrue(walled.options(-1).isEmpty())
        assertEquals(walled, RpsCodec.decode(RpsCodec.encode(walled)))
    }

    @Test fun `both sides open with the same mix and no piece already touching an enemy`() {
        assertEquals(10, Rps.NAMES.size)
        for (setting in Rps.NAMES.indices) {
            val game = Rps.start(setting.toLong(), setting)
            assertEquals(1, game.turn)
            assertFalse(game.over)
            assertNull(game.lastFrom)
            assertEquals(Rps.passOf(setting), game.passAndPlay)
            val quota = Rps.quotaOf(game.size)
            for (kind in Rps.KINDS) {
                assertEquals(quota, game.count(1, kind))
                assertEquals(quota, game.count(-1, kind))
                assertEquals(0, game.taken(1, kind))
            }
            for (i in game.cells.indices) if (game.cells[i] != 0) {
                for (n in Rps.neighbors(game.size, i)) if (game.cells[n] != 0) {
                    assertEquals(Rps.sideOf(game.cells[i]), Rps.sideOf(game.cells[n]), "setting $setting cell $i")
                }
            }
            assertTrue(game.legalMoves().isNotEmpty())
            assertEquals(game, RpsCodec.decode(RpsCodec.encode(game)))
            val enemy = game.cells.indexOfFirst { it < 0 }
            assertNull(game.play(enemy, Rps.neighbors(game.size, enemy).first { game.cells[it] == 0 }))
        }
        assertEquals(5, Rps.sizeOf(0))
        assertEquals(5, Rps.sizeOf(3))
        assertEquals(7, Rps.sizeOf(4))
        assertEquals(7, Rps.sizeOf(9))
        assertEquals(3, Rps.levelOf(7))
        assertTrue(Rps.passOf(8))
        assertFalse(Rps.passOf(7))
    }

    @Test fun `damaged games and saves are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Rps(99, 1) }
        val extra = Rps.opening(0).toMutableList()
        extra[at(2, 2, 5)] = Rps.ROCK
        assertThrows(IllegalArgumentException::class.java) { Rps(0, 1, extra) }
        val alive = placed(5, pieces = listOf(at(2, 2, 5) to Rps.ROCK, at(0, 0, 5) to -Rps.SCISSORS))
        assertThrows(IllegalArgumentException::class.java) { alive.copy(winner = 1) }
        assertNull(RpsCodec.decode("bad"))
        assertNull(RpsCodec.decode(""))
        val encoded = RpsCodec.encode(alive).split('\n').toMutableList()
        encoded[0] = "2"
        assertNull(RpsCodec.decode(encoded.joinToString("\n")))
        encoded[0] = "1"
        encoded[5] = "0"
        assertNull(RpsCodec.decode(encoded.joinToString("\n")))
        val won = placed(5, pieces = listOf(at(2, 2, 5) to Rps.ROCK, at(2, 3, 5) to -Rps.SCISSORS)).play(at(2, 2, 5), at(2, 3, 5))!!
        val tampered = RpsCodec.encode(won).split('\n').toMutableList()
        tampered[6] = tampered[6] + "0"
        assertNull(RpsCodec.decode(tampered.joinToString("\n")))
    }

    @Test fun `the computer only plays a legal move and takes a forced win`() {
        val prey = placed(5, pieces = listOf(at(2, 2, 5) to Rps.ROCK, at(2, 3, 5) to -Rps.SCISSORS))
        val winning = RpsMove(at(2, 2, 5), at(2, 3, 5))
        for (level in 1..3) {
            val game = prey.copy(setting = level)
            assertEquals(winning, RpsAi.choose(game))
            assertEquals(RpsAi.choose(game), RpsAi.choose(game))
        }

        val rock = at(3, 2, 7)
        val threat = at(1, 2, 7)
        val exposed = placed(7, level = 1, pieces = listOf(rock to Rps.ROCK, threat to -Rps.PAPER))
        for (level in 1..3) {
            val game = exposed.copy(setting = 4 + level)
            val move = RpsAi.choose(game)
            assertTrue(move in game.legalMoves(), "level $level $move")
            val next = game.play(move)!!
            assertTrue(next.legalMoves().none { it.to == move.to }, "level $level stepped onto a capture")
        }

        for (setting in Rps.NAMES.indices) {
            var game = Rps.start(setting * 19L + 3, setting)
            val plies = if (game.size == 5) 8 else 4
            repeat(plies) {
                if (game.over) return@repeat
                val move = RpsAi.choose(game)
                assertTrue(move in game.legalMoves(), "setting $setting ply $it $move")
                game = game.play(move)!!
                assertEquals(game, RpsCodec.decode(RpsCodec.encode(game)))
            }
        }
        val again = Rps.start(4, 2)
        assertEquals(RpsAi.choose(again), RpsAi.choose(Rps.start(4, 2)))
        assertNotEquals(0, again.legalMoves().size)
        val expert = Rps.start(1, 7)
        val started = System.nanoTime()
        assertTrue(RpsAi.choose(expert) in expert.legalMoves())
        assertTrue(System.nanoTime() - started < 350_000_000L, "expert move stayed under the phone cap")
    }

    @Test fun `easy prefers a safe capture and medium refuses a capture that hangs`() {
        val captureTo = at(3, 4, 7)
        val safe = placed(7, pieces = listOf(
            at(3, 3, 7) to Rps.ROCK,
            captureTo to -Rps.SCISSORS,
            at(0, 0, 7) to -Rps.ROCK,
            at(0, 1, 7) to -Rps.PAPER,
            at(6, 6, 7) to Rps.PAPER,
        ))
        assertTrue(captureTo in safe.destinations(at(3, 3, 7)))
        assertFalse(RpsAi.hangs(safe, RpsMove(at(3, 3, 7), captureTo)))
        var took = 0
        for (seed in 1..24) {
            val move = RpsAi.choose(safe.copy(seed = seed.toLong()))
            assertTrue(move in safe.legalMoves())
            if (move.to == captureTo) took++
        }
        assertTrue(took >= 16, "easy took the safe capture $took of 24 times")

        val safeTo = at(2, 3, 7)
        val hangTo = at(3, 4, 7)
        val bait = placed(7, level = 1, pieces = listOf(
            at(3, 3, 7) to Rps.ROCK,
            safeTo to -Rps.SCISSORS,
            hangTo to -Rps.SCISSORS,
            at(3, 5, 7) to -Rps.PAPER,
            at(6, 6, 7) to Rps.PAPER,
        ))
        assertFalse(RpsAi.hangs(bait, RpsMove(at(3, 3, 7), safeTo)))
        assertTrue(RpsAi.hangs(bait, RpsMove(at(3, 3, 7), hangTo)))
        for (level in 1..3) {
            val game = bait.copy(setting = 4 + level)
            val move = RpsAi.choose(game)
            assertTrue(move in game.legalMoves(), "level $level $move")
            assertEquals(safeTo, move.to, "level $level took $move instead of the safe capture")
            assertFalse(RpsAi.hangs(game, move), "level $level hung with $move")
        }
    }

    @Test fun `hard and expert outplay easy from the opening`() {
        for (strong in intArrayOf(2, 3)) {
            var score = 0
            for (seed in 1L..2L) {
                var g = Rps.start(seed, 0)
                var steps = 0
                while (!g.over && steps++ < 40) {
                    val level = if (g.turn == 1) strong else 0
                    val move = RpsAi.choose(g.copy(setting = level))
                    assertTrue(move in g.legalMoves(), "level $strong $move")
                    g = g.play(move)!!
                }
                score += when (g.winner) {
                    1 -> 4
                    -1 -> -4
                    else -> g.cells.count { it > 0 } - g.cells.count { it < 0 }
                }
            }
            assertTrue(score > 0, "level $strong score $score")
        }
    }
}
