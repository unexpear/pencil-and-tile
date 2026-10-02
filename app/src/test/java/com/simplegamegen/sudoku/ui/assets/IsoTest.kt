package com.simplegamegen.sudoku.ui.assets

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class IsoTest {
    @Test fun `a point lifts as its height grows and the viewer faces increasing y`() {
        val ground = iso(0f, 0f, 0f, cell = 10f, rise = 8f)
        val up = iso(0f, 0f, 1f, cell = 10f, rise = 8f)
        val toward = iso(0f, 1f, 0f, cell = 10f, rise = 8f)
        assertTrue(up.y < ground.y)
        assertTrue(toward.y > ground.y)
    }

    @Test fun `a tap on a tower face hits that tower`() {
        val a = Offset(0f, 0f)
        val b = Offset(10f, 6f)
        val c = Offset(0f, 12f)
        val d = Offset(-10f, 6f)
        assertTrue(pointInQuad(Offset(0f, 6f), a, b, c, d))
        assertFalse(pointInQuad(Offset(30f, 6f), a, b, c, d))
    }

    @Test fun `the center of a square sits on that square`() {
        val frame = tableFrame(8, 400f, peakZ = 1.2f)
        val center = frame.at(2.5f, 3.5f, 0.22f)
        val face = frame.square(2, 3, 0.22f)
        assertTrue(pointInQuad(center, face[0], face[1], face[2], face[3]))
        assertTrue(frame.at(0f, 0f, 1f).y < frame.at(0f, 0f, 0f).y)
    }

    @Test fun `the player view narrows the far edge and still hits the square`() {
        val frame = tableFrame(8, 400f, peakZ = 1.5f, player = true, maxHeightPx = 800f)
        val center = frame.at(2.5f, 3.5f, 0.22f)
        val face = frame.square(2, 3, 0.22f)
        assertTrue(pointInQuad(center, face[0], face[1], face[2], face[3]))
        val far = frame.at(8f, 0f, 0f).x - frame.at(0f, 0f, 0f).x
        val near = frame.at(8f, 8f, 0f).x - frame.at(0f, 8f, 0f).x
        assertTrue(far < near * 0.9f)
        assertTrue(frame.height > tableFrame(8, 400f, peakZ = 1.5f).height)
    }

    @Test fun `behind your side the near edge is lower, wider, and closer`() {
        val frame = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.behind, maxHeightPx = 700f)
        assertTrue(frame.at(4f, 8f, 0f).y > frame.at(4f, 0f, 0f).y)
        assertTrue(frame.at(4f, 4f, 1f).y < frame.at(4f, 4f, 0f).y)
        assertTrue(span(frame, 0f) < span(frame, 8f))
        assertTrue(frame.depth(4f, 0f, 0f) > frame.depth(4f, 8f, 0f))
        assertOnSquare(frame)
    }

    @Test fun `the corner and the top each keep a square's center on that square`() {
        val corner = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.corner, maxHeightPx = 700f)
        assertOnSquare(corner)
        assertTrue(corner.at(4f, 4f, 1f).y < corner.at(4f, 4f, 0f).y)
        val top = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.top, maxHeightPx = 700f)
        assertOnSquare(top)
        assertTrue(top.at(4f, 8f, 0f).y > top.at(4f, 0f, 0f).y)
        assertTrue(abs(span(top, 0f) - span(top, 8f)) / span(top, 8f) < 0.05f)
    }

    @Test fun `the gutter keeps an edge label inside the view`() {
        val frame = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.behind, maxHeightPx = 700f, margin = 0.7f)
        val label = frame.at(4f, -0.4f, 0f)
        assertTrue(label.x > 0f && label.x < frame.width && label.y > 0f && label.y < frame.height)
        assertOnSquare(frame)
    }

    @Test fun `yaw turns the camera onto the right-hand side`() {
        val frame = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.behind.copy(yaw = 90f), maxHeightPx = 700f)
        assertTrue(frame.depth(0f, 4f, 0f) > frame.depth(8f, 4f, 0f))
        assertOnSquare(frame)
    }

    private fun assertOnSquare(frame: TableFrame) {
        val center = frame.at(2.5f, 3.5f, 0.22f)
        val face = frame.square(2, 3, 0.22f)
        assertTrue(pointInQuad(center, face[0], face[1], face[2], face[3]))
    }

    private fun span(frame: TableFrame, y: Float): Float {
        val a = frame.at(0f, y, 0f)
        val b = frame.at(8f, y, 0f)
        return sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
    }
}
