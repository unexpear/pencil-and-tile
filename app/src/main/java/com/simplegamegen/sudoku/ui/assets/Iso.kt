package com.simplegamegen.sudoku.ui.assets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/** Isometric point. X grows to the right, Y toward the viewer, Z up. */
internal fun iso(x: Float, y: Float, z: Float, cell: Float, rise: Float) =
    Offset((x - y) * cell, (x + y) * cell * 0.5f - z * rise)

internal fun quad(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    lineTo(d.x, d.y)
    close()
}

internal fun pointInQuad(p: Offset, a: Offset, b: Offset, c: Offset, d: Offset) =
    pointInTri(p, a, b, c) || pointInTri(p, a, c, d)

private fun pointInTri(p: Offset, a: Offset, b: Offset, c: Offset): Boolean {
    val v0x = c.x - a.x
    val v0y = c.y - a.y
    val v1x = b.x - a.x
    val v1y = b.y - a.y
    val v2x = p.x - a.x
    val v2y = p.y - a.y
    val dot00 = v0x * v0x + v0y * v0y
    val dot01 = v0x * v1x + v0y * v1y
    val dot02 = v0x * v2x + v0y * v2y
    val dot11 = v1x * v1x + v1y * v1y
    val dot12 = v1x * v2x + v1y * v2y
    val den = dot00 * dot11 - dot01 * dot01
    if (den == 0f) return false
    val inv = 1f / den
    val u = (dot11 * dot02 - dot01 * dot12) * inv
    val v = (dot00 * dot12 - dot01 * dot02) * inv
    return u >= -0.001f && v >= -0.001f && u + v <= 1.001f
}
