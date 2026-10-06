package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.duels.ShipKind
import com.simplegamegen.sudoku.ui.assets.BoardView
import com.simplegamegen.sudoku.ui.assets.Mesh
import com.simplegamegen.sudoku.ui.assets.decodeShipMesh
import com.simplegamegen.sudoku.ui.assets.shipMeshAsset
import com.simplegamegen.sudoku.ui.assets.tableFrame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** The CC-BY hulls occupy the classic fleet, and a phone-width sea stays tappable. */
class BattleshipBoardTest {
    @Test fun `each hull covers its squares and the hit pin stands taller than the miss pin`() {
        ShipKind.entries.forEach { kind ->
            val mesh = load(shipMeshAsset(kind).substringAfterLast('/').removeSuffix(".bmesh"))
            val (x0, x1) = span(mesh, 0)
            val (y0, y1) = span(mesh, 1)
            val (z0, z1) = span(mesh, 2)
            assertTrue(x1 - x0 > kind.length * 0.85f, "$kind length ${x1 - x0}")
            assertTrue(x0 > 0f && x1 < kind.length, "$kind sits inside its squares")
            assertTrue(y1 - y0 < 0.9f && y0 < 0f && y1 > 0f, "$kind stays inside one square width")
            assertEquals(0f, z0, 0.02f, "$kind rests on the water")
            assertTrue(z1 in 0.25f..1.05f, "$kind height $z1")
            assertTrue(mesh.tris.size >= 300, "$kind is a solid mesh")
        }
        val miss = load("pin_miss")
        val hit = load("pin_hit")
        assertTrue(span(hit, 2).second > span(miss, 2).second)
        assertTrue(span(miss, 2).second > 0.2f)
        assertTrue(span(hit, 0).second < 0.2f && span(miss, 0).second < 0.2f)
    }

    @Test fun `a phone-width sea keeps a near square large enough to tap`() {
        val frame = tableFrame(
            n = 10, maxWidthPx = 1080f, peakZ = 1.15f, maxHeightPx = 1080f,
            view = BoardView("Behind", yaw = 0f, pitch = 68f, distance = 1.38f), margin = 0.62f,
        )
        val cell = frame.unitAt(4.5f, 9.5f, 0.22f)
        assertTrue(cell > 70f, "cell is $cell px")
    }

    @Test fun `the credits name the battleship models and their licence`() {
        val notice = File("src/main/assets/licenses/battleship-pieces-cc-by.txt").readText()
        assertTrue(notice.contains("MZimb"))
        assertTrue(notice.contains("5190846"))
        assertTrue(notice.contains("Mattwall"))
        assertTrue(notice.contains("4244260"))
        assertTrue(notice.contains("Creative Commons"))
        assertTrue(notice.contains("Aircraft Carrier"))
        val credits = File("src/main/java/com/simplegamegen/sudoku/ui/screens/CreditsScreen.kt").readText()
        assertTrue(credits.contains("Original Battleship Pieces"))
        assertTrue(credits.contains("licenses/battleship-pieces-cc-by.txt"))
    }

    private fun load(name: String) = decodeShipMesh(File("src/main/assets/models/battleship/$name.bmesh").readBytes())

    private fun span(mesh: Mesh, axis: Int): Pair<Float, Float> {
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        for (i in 0 until mesh.count) {
            val v = mesh.pos[i * 3 + axis]
            lo = min(lo, v)
            hi = max(hi, v)
        }
        return lo to hi
    }
}
