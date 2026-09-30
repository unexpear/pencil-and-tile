package com.simplegamegen.sudoku.ui.assets

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
}
