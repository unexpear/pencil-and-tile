package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.duels.Mancala
import com.simplegamegen.sudoku.ui.assets.tableFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.hypot

class MancalaBoardTest {
    @Test fun `the near row is the player's pits and the stores sit at the ends`() {
        val player = (0 until Mancala.PITS).map { MancalaBoard.hole(it) }
        val far = (0 until Mancala.PITS).map { MancalaBoard.hole(12 - it) }
        val you = MancalaBoard.hole(Mancala.YOU)
        val cpu = MancalaBoard.hole(Mancala.CPU)
        assertTrue(player.zipWithNext().all { (a, b) -> b.x > a.x })
        assertTrue(player.zip(far).all { (near, across) -> near.x == across.x && near.y > across.y })
        assertTrue(you.x > player.last().x && cpu.x < player.first().x)
        assertTrue(player.zipWithNext().all { (a, b) -> b.x - a.x > a.rx + b.rx })
    }

    @Test fun `a phone seat keeps the pits large and apart`() {
        val view = MancalaBoard.views.first()
        val frame = tableFrame(
            MancalaBoard.width.toInt(),
            1080f,
            peakZ = MancalaBoard.peakZ,
            view = view,
            maxHeightPx = 1400f,
            margin = MancalaBoard.margin,
            rows = MancalaBoard.depth,
        )
        val near = MancalaBoard.hole(0)
        val far = MancalaBoard.hole(12)
        val next = MancalaBoard.hole(1)
        val unit = frame.unitAt(near.x, near.y, MancalaBoard.top)
        assertTrue(unit > 80f, "pit unit $unit")
        val nearAt = frame.at(near.x, near.y, MancalaBoard.top)
        val farAt = frame.at(far.x, far.y, MancalaBoard.top)
        val nextAt = frame.at(next.x, next.y, MancalaBoard.top)
        assertTrue(nearAt.y > farAt.y)
        assertTrue(nextAt.x > nearAt.x)
        val gap = hypot(nextAt.x - nearAt.x, nextAt.y - nearAt.y)
        assertTrue(gap > unit * (near.rx + next.rx) * 0.85f, "gap $gap unit $unit")
        assertTrue(hypot(nearAt.x - farAt.x, nearAt.y - farAt.y) > unit * 0.7f)
        for (hole in MancalaBoard.holes) {
            val at = frame.at(hole.x, hole.y, MancalaBoard.top)
            assertTrue(at.x > 0f && at.x < frame.width && at.y > 0f && at.y < frame.height, "hole ${hole.index}")
        }
    }

    @Test fun `the filament camera lands on the same pixels as the tap frame`() {
        for (view in MancalaBoard.views) {
            for ((width, height) in listOf(1080f to 1400f, 800f to 900f)) {
                val frame = tableFrame(
                    MancalaBoard.width.toInt(),
                    width,
                    peakZ = MancalaBoard.peakZ,
                    view = view,
                    maxHeightPx = height,
                    margin = MancalaBoard.margin,
                    rows = MancalaBoard.depth,
                )
                val samples = MancalaBoard.holes.map { it.x to it.y } + listOf(0f to 0f, 8f to 3.6f, 4f to 1.8f)
                for ((x, y) in samples) {
                    val expected = frame.at(x, y, MancalaBoard.top)
                    val projected = mancalaProject(frame, x, y, MancalaBoard.top)
                    assertNotNull(projected, view.name)
                    val dx = projected!!.x - expected.x
                    val dy = projected.y - expected.y
                    assertTrue(hypot(dx, dy) < 0.75f, "${view.name} ($x,$y) off by ${hypot(dx, dy)}")
                }
            }
        }
    }

    @Test fun `marbles sit in the bowls without stacking through each other`() {
        val pits = Mancala.OPENING
        var shown = 0
        for (hole in MancalaBoard.holes) {
            val stones = stonesIn(hole, pits[hole.index])
            shown += stones.size
            for (stone in stones) {
                val dx = (stone.x - hole.x) / hole.rx
                val dy = (stone.y - hole.y) / hole.ry
                assertTrue(dx * dx + dy * dy < 1f, "hole ${hole.index}")
                val floor = MancalaBoard.top - bowlDrop(hole, stone.x, stone.y)
                assertTrue(stone.z >= floor + MancalaBoard.stoneRadius * 0.8f)
            }
            for (i in stones.indices) {
                for (j in i + 1 until stones.size) {
                    val a = stones[i]
                    val b = stones[j]
                    val apart = hypot(hypot(a.x - b.x, a.y - b.y), a.z - b.z)
                    assertTrue(apart > MancalaBoard.stoneRadius * 1.7f, "hole ${hole.index} $apart")
                }
            }
        }
        assertEquals(Mancala.STONES, shown)
    }

    @Test fun `a sow animation starts on the old piles and ends on the rules`() {
        var game = Mancala.start(3L, 1)
        var guard = 0
        while (!game.ended && guard < 40) {
            guard++
            val pit = game.legalPits().first()
            val next = game.sow(pit)!!
            val anim = animateSow(game, next)
            assertNotNull(anim)
            assertEquals(Mancala.STONES, anim!!.flights.size)
            assertSamePiles(restingPoses(game.pits), anim.sample(0f))
            assertSamePiles(restingPoses(next.pits), anim.sample(1f))
            val drops = dropTargets(game, pit)
            assertEquals(game.pits[pit], drops.size)
            val skip = if (game.turn == 1) Mancala.CPU else Mancala.YOU
            assertTrue(skip !in drops)
            game = next
        }
    }

    private fun assertSamePiles(expected: List<StonePose>, actual: List<StonePose>) {
        assertEquals(expected.size, actual.size)
        val left = expected.map { Triple(it.x, it.y, it.z) }.toMutableList()
        for (pose in actual) {
            val match = left.indexOfFirst { (x, y, z) ->
                hypot(hypot(x - pose.x, y - pose.y), z - pose.z) < 0.02f
            }
            assertTrue(match >= 0, "missing ${pose.x},${pose.y},${pose.z}")
            left.removeAt(match)
        }
    }
}
