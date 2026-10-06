package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.duels.Mancala
import com.simplegamegen.sudoku.ui.assets.tableFrame
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
}
