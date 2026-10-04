package com.simplegamegen.sudoku.ui.assets

import androidx.compose.ui.geometry.Offset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test fun `the seat view keeps squares large on a tall phone and still recedes`() {
        val seat = tableFrame(8, 1080f, peakZ = 1.5f, view = BoardViews.behind, maxHeightPx = 1400f, margin = 0.46f)
        val low = tableFrame(8, 1080f, peakZ = 1.5f, view = BoardViews.behind.copy(pitch = 40f, distance = 1.5f), maxHeightPx = 1400f, margin = 0.46f)
        assertTrue(seat.cell > low.cell)
        assertTrue(span(seat, 0f) < span(seat, 8f) * 0.92f)
        assertOnSquare(seat)
    }

    @Test fun `a tap between go points hits the nearest intersection`() {
        val frame = tableFrame(19, 1000f, peakZ = 0.55f, view = BoardView("Above", 0f, 76f, 1.28f), maxHeightPx = 1400f, margin = 0.68f)
        val center = frame.at(9.5f, 9.5f, 0.18f)
        assertTrue(nearestCell(frame, center, 19, 0.18f) == 9 * 19 + 9)
        val unit = frame.unitAt(9.5f, 9.5f, 0.18f)
        assertTrue(nearestCell(frame, Offset(center.x + unit * 0.35f, center.y), 19, 0.18f) == 9 * 19 + 9)
        assertTrue(nearestCell(frame, Offset(-400f, -400f), 19, 0.18f) == null)
    }

    @Test fun `yaw turns the camera onto the right-hand side`() {
        val frame = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.behind.copy(yaw = 90f), maxHeightPx = 700f)
        assertTrue(frame.depth(0f, 4f, 0f) > frame.depth(8f, 4f, 0f))
        assertOnSquare(frame)
    }

    @Test fun `the phone seat cameras stay the close views`() {
        assertEquals(0f, BoardViews.behind.yaw)
        assertEquals(70f, BoardViews.behind.pitch)
        assertEquals(1.4f, BoardViews.behind.distance)
        assertEquals(1f, BoardViews.behind.zoom)
        assertEquals(0f, BoardViews.behind.panX)
        assertEquals(0f, BoardViews.behind.panY)
        assertEquals(28f, BoardViews.corner.yaw)
        assertEquals(62f, BoardViews.corner.pitch)
        assertEquals(1.55f, BoardViews.corner.distance)
    }

    @Test fun `orbiting keeps one square about the same size and the board centered`() {
        val home = tableFrame(8, 1080f, peakZ = 1.5f, view = BoardViews.behind, maxHeightPx = 1400f, margin = 0.46f)
        val homeSize = home.unitAt(4f, 4f, 0f)
        val homeCenter = home.at(4f, 4f, 0f)
        assertTrue(abs(homeCenter.x - home.width / 2f) < home.width * 0.02f)
        for (yaw in listOf(-75f, -40f, 20f, 55f, 90f)) {
            val turned = tableFrame(8, 1080f, peakZ = 1.5f, view = BoardViews.behind.copy(yaw = yaw), maxHeightPx = 1400f, margin = 0.46f)
            val size = turned.unitAt(4f, 4f, 0f)
            assertTrue(abs(size - homeSize) / homeSize < 0.06f, "yaw $yaw")
            val center = turned.at(4f, 4f, 0f)
            assertTrue(abs(center.x - homeCenter.x) < 1.5f && abs(center.y - homeCenter.y) < 1.5f, "yaw $yaw center")
            assertOnSquare(turned)
        }
    }

    @Test fun `zoom magnifies the picture and leaves the camera where it is`() {
        val fitted = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.behind, maxHeightPx = 700f)
        val zoomed = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.behind.copy(zoom = 2f), maxHeightPx = 700f)
        val panned = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.behind.copy(panX = 30f, panY = -12f), maxHeightPx = 700f)
        assertTrue(abs(zoomed.unitAt(4f, 4f, 0f) / fitted.unitAt(4f, 4f, 0f) - 2f) < 0.02f)
        assertTrue(abs(span(fitted, 0f) / span(fitted, 8f) - span(zoomed, 0f) / span(zoomed, 8f)) < 0.01f)
        val lift = { frame: TableFrame -> frame.at(4f, 4f, 0f).y - frame.at(4f, 4f, 1f).y }
        assertTrue(abs(lift(zoomed) / lift(fitted) - 2f) < 0.02f)
        assertEquals(fitted.depth(1f, 7f, 0f), zoomed.depth(1f, 7f, 0f))
        assertEquals(fitted.basis().toList(), zoomed.basis().toList())
        val spot = fitted.at(3f, 5f, 0.2f)
        val slid = panned.at(3f, 5f, 0.2f)
        assertTrue(abs(slid.x - spot.x - 30f) < 0.5f && abs(slid.y - spot.y + 12f) < 0.5f)
        val middle = Offset(fitted.width / 2f, fitted.height / 2f)
        val before = fitted.at(2f, 6f, 0f)
        val after = zoomed.at(2f, 6f, 0f)
        assertTrue(abs((after.x - middle.x) / (before.x - middle.x) - 2f) < 0.02f)
        assertTrue(abs((after.y - middle.y) / (before.y - middle.y) - 2f) < 0.02f)
    }

    @Test fun `a pinch keeps the board point under the fingers still`() {
        val before = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.behind, maxHeightPx = 700f)
        val focus = before.at(6f, 7f, 0f)
        val (zoom, panX, panY) = magnifyAround(1f, 0f, 0f, 1.6f, focus.x, focus.y, before.width, before.height)
        val after = tableFrame(8, 400f, peakZ = 1.5f, view = BoardViews.behind.copy(zoom = zoom, panX = panX, panY = panY), maxHeightPx = 700f)
        val moved = after.at(6f, 7f, 0f)
        assertTrue(abs(moved.x - focus.x) < 0.6f && abs(moved.y - focus.y) < 0.6f)
        assertTrue(after.unitAt(4f, 4f, 0f) > before.unitAt(4f, 4f, 0f) * 1.4f)
    }

    @Test fun `old saved lines without zoom or pan still load fitted`() {
        assertEquals(BoardViews.behind, decodeBoardView("Behind|0.00|70.00|1.40"))
        assertEquals(BoardViews.previous, BoardViews.previous.map { decodeBoardView(encodeBoardView(it)) })
        val legacy = decodeBoardView("Corner|36.00|50.00|1.70")
        assertEquals(BoardViews.previous[1].name, legacy!!.name)
        assertEquals(1f, legacy.zoom)
        assertEquals(0f, legacy.panX)
        assertEquals(0f, legacy.panY)
        assertNull(decodeBoardView("Broken|nope"))
        assertNull(decodeBoardView("Only|1|2"))
    }

    @Test fun `saved views round-trip zoom and pan`() {
        val view = BoardView("Seat", yaw = 12.5f, pitch = 64f, distance = 1.5f, zoom = 1.75f, panX = 18.5f, panY = -6.25f)
        assertEquals(view, decodeBoardView(encodeBoardView(view)))
        assertTrue(view.zoom != 1f)
        assertEquals(BoardViews.behind.copy(zoom = 1f, panX = 0f, panY = 0f), BoardViews.behind)
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
