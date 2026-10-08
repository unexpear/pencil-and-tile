package com.simplegamegen.sudoku.ui.screens

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class StoneSpot(val x: Float, val y: Float, val z: Float)

/**
 * How far the carved bowl drops below [MancalaBoard.top] at (x, y).
 * The same cosine is displaced in tools/mancala/generate_board.py.
 */
internal fun bowlDrop(hole: MancalaBoard.Hole, x: Float, y: Float): Float {
    val dx = (x - hole.x) / hole.rx
    val dy = (y - hole.y) / hole.ry
    val t2 = dx * dx + dy * dy
    if (t2 >= 1f) return 0f
    val profile = 0.5f * (1f + cos(PI.toFloat() * sqrt(t2)))
    val depth = if (hole.store) MancalaBoard.storeDepth else MancalaBoard.pitDepth
    return depth * profile
}

/** Centres of [count] marbles resting in [hole], bottom layer first. */
internal fun stonesIn(hole: MancalaBoard.Hole, count: Int): List<StoneSpot> {
    if (count <= 0) return emptyList()
    val radius = MancalaBoard.stoneRadius
    val gap = radius * 2.08f
    val rowH = gap * 0.8660254f
    val out = ArrayList<StoneSpot>(count)
    var layer = 0
    while (out.size < count && layer < 16) {
        val shrink = (1f - layer * 0.04f).coerceAtLeast(0.38f)
        val limitX = (hole.rx - radius * 0.62f) * shrink
        val limitY = (hole.ry - radius * 0.62f) * shrink
        if (limitX < radius * 0.35f || limitY < radius * 0.35f) break
        val shiftX = if (layer % 2 == 1) gap * 0.5f else 0f
        val shiftY = if (layer % 2 == 1) rowH * 0.28f else 0f
        val rows = (limitY * 2f / rowH).toInt() + 3
        val cols = (limitX * 2f / gap).toInt() + 3
        val found = ArrayList<StoneSpot>()
        for (row in -rows..rows) {
            for (col in -cols..cols) {
                val rawX = hole.x + shiftX + col * gap + if (row % 2 == 0) 0f else gap * 0.5f
                val rawY = hole.y + shiftY + row * rowH
                val (jx, jy) = jitter(hole.index, layer, found.size)
                val x = rawX + jx
                val y = rawY + jy
                val dx = (x - hole.x) / limitX
                val dy = (y - hole.y) / limitY
                if (dx * dx + dy * dy > 1f) continue
                val z = MancalaBoard.top - bowlDrop(hole, x, y) + radius * 0.92f + layer * gap * 0.86f
                found.add(StoneSpot(x, y, z))
            }
        }
        if (found.isEmpty()) break
        found.sortBy { spot ->
            val dx = (spot.x - hole.x) / hole.rx
            val dy = (spot.y - hole.y) / hole.ry
            dx * dx + dy * dy
        }
        for (spot in found) {
            if (out.size >= count) break
            out.add(spot)
        }
        layer++
    }
    var extra = 0
    while (out.size < count) {
        out.add(StoneSpot(hole.x, hole.y, MancalaBoard.top + radius + extra * gap * 0.86f))
        extra++
    }
    return out
}

private fun jitter(hole: Int, layer: Int, index: Int): Pair<Float, Float> {
    val n = hole * 131 + layer * 17 + index * 9
    val radius = MancalaBoard.stoneRadius
    val jx = ((n % 11) - 5) / 5f * radius * 0.07f
    val jy = (((n / 11) % 11) - 5) / 5f * radius * 0.07f
    return jx to jy
}

internal fun restingPoses(pits: List<Int>): List<StonePose> {
    val poses = ArrayList<StonePose>(MancalaStonesTotal)
    for (hole in MancalaBoard.holes) {
        stonesIn(hole, pits[hole.index]).forEachIndexed { slot, spot ->
            poses.add(StonePose(spot.x, spot.y, spot.z, marbleTint(hole.index, slot)))
        }
    }
    return poses
}

internal fun marbleTint(hole: Int, slot: Int): Int = (hole * 5 + slot) % MARBLE_TINTS

internal const val MARBLE_TINTS = 6
private const val MancalaStonesTotal = 48
