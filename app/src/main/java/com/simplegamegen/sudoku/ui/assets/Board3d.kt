package com.simplegamegen.sudoku.ui.assets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * A board seen from above and in front. [cell] is one square across.
 * Z grows up off the table. The front of the board is increasing Y.
 */
internal class TableFrame(
    val n: Int,
    val cell: Float,
    val rise: Float,
    private val shear: Float,
    private val yScale: Float,
    private val shift: Offset,
    val width: Float,
    val height: Float,
) {
    fun at(x: Float, y: Float, z: Float) =
        Offset(x * cell + y * cell * shear, y * cell * yScale - z * rise) + shift

    fun square(col: Int, row: Int, z: Float) = listOf(
        at(col.toFloat(), row.toFloat(), z),
        at(col + 1f, row.toFloat(), z),
        at(col + 1f, row + 1f, z),
        at(col.toFloat(), row + 1f, z),
    )
}

internal fun tableFrame(n: Int, maxWidthPx: Float, peakZ: Float): TableFrame {
    val shear = 0.22f
    val yScale = 0.52f
    val pad = 10f
    val cell = ((maxWidthPx - pad * 2) / (n * (1f + shear))).coerceAtLeast(1f)
    val rise = cell * 0.72f
    fun raw(x: Float, y: Float, z: Float) = Offset(x * cell + y * cell * shear, y * cell * yScale - z * rise)
    val edge = n.toFloat()
    val pts = listOf(
        raw(0f, 0f, peakZ), raw(edge, 0f, peakZ), raw(0f, edge, peakZ), raw(edge, edge, peakZ),
        raw(0f, edge, 0f), raw(edge, edge, 0f),
    )
    val minX = pts.minOf { it.x }
    val minY = pts.minOf { it.y }
    val shift = Offset(pad - minX, pad - minY)
    return TableFrame(
        n, cell, rise, shear, yScale, shift,
        pts.maxOf { it.x } - minX + pad * 2,
        pts.maxOf { it.y } - minY + pad * 2,
    )
}

internal fun DrawScope.drawBoardSlab(frame: TableFrame, top: Float, wood: Color) {
    val n = frame.n.toFloat()
    val dark = lerp(wood, Color.Black, 0.28f)
    drawPath(quad(frame.at(0f, n, top), frame.at(n, n, top), frame.at(n, n, 0f), frame.at(0f, n, 0f)), dark)
    drawPath(quad(frame.at(n, 0f, top), frame.at(n, n, top), frame.at(n, n, 0f), frame.at(n, 0f, 0f)), lerp(wood, Color.Black, 0.12f))
}

internal fun DrawScope.drawSquareTop(frame: TableFrame, col: Int, row: Int, z: Float, color: Color) {
    val q = frame.square(col, row, z)
    drawPath(quad(q[0], q[1], q[2], q[3]), color)
}

/** A thick disc standing on the square. A king is two discs. */
internal fun DrawScope.drawPuck(frame: TableFrame, x: Float, y: Float, baseZ: Float, color: Color, king: Boolean) {
    val rx = frame.cell * 0.34f
    val ry = rx * 0.42f
    val step = if (king) 0.34f else 0.22f
    drawOnePuck(frame, x, y, baseZ, baseZ + step, rx, ry, color)
    if (king) drawOnePuck(frame, x, y, baseZ + step * 0.85f, baseZ + step * 1.7f, rx * 0.92f, ry * 0.92f, color)
}

private fun DrawScope.drawOnePuck(frame: TableFrame, x: Float, y: Float, z0: Float, z1: Float, rx: Float, ry: Float, color: Color) {
    val top = frame.at(x, y, z1)
    val base = frame.at(x, y, z0)
    val shade = lerp(color, Color.Black, 0.35f)
    val drop = (base.y - top.y).coerceAtLeast(0f)
    drawOval(shade, Offset(base.x - rx, base.y - ry), Size(rx * 2, ry * 2))
    drawRect(shade, Offset(top.x - rx, top.y), Size(rx * 2, drop))
    drawOval(color, Offset(top.x - rx, top.y - ry), Size(rx * 2, ry * 2))
    val hi = lerp(color, Color.White, if (color.luminance() > 0.5f) 0.55f else 0.28f)
    drawOval(hi, Offset(top.x - rx * 0.45f, top.y - ry * 0.72f), Size(rx * 0.7f, ry * 0.55f))
}

/** A small token on an empty square, for a legal move. */
internal fun DrawScope.drawDot(frame: TableFrame, x: Float, y: Float, z: Float, color: Color) {
    val c = frame.at(x, y, z + 0.08f)
    val r = frame.cell * 0.12f
    drawCircle(color, r, c)
}
internal fun DrawScope.drawStone(frame: TableFrame, x: Float, y: Float, z: Float, color: Color, mark: Boolean, markColor: Color, dead: Boolean = false) {
    val rx = frame.cell * 0.4f
    val ry = rx * 0.46f
    val top = frame.at(x, y, z + 0.16f)
    val base = frame.at(x, y, z)
    val shade = lerp(color, Color.Black, 0.4f)
    val face = if (dead) lerp(color, Color.White, 0.55f) else color
    drawOval(shade, Offset(base.x - rx, base.y - ry * 0.2f), Size(rx * 2, ry * 1.5f + (base.y - top.y).coerceAtLeast(0f)))
    drawOval(face, Offset(top.x - rx, top.y - ry), Size(rx * 2, ry * 2))
    if (!dead) drawOval(lerp(color, Color.White, 0.45f), Offset(top.x - rx * 0.38f, top.y - ry * 0.7f), Size(rx * 0.55f, ry * 0.4f))
    if (mark) drawCircle(markColor, rx * 0.22f, top)
    if (dead) {
        drawLine(Color(0xFFB71C1C), Offset(top.x - rx * 0.45f, top.y - ry * 0.45f), Offset(top.x + rx * 0.45f, top.y + ry * 0.45f), strokeWidth = rx * 0.16f)
        drawLine(Color(0xFFB71C1C), Offset(top.x - rx * 0.45f, top.y + ry * 0.45f), Offset(top.x + rx * 0.45f, top.y - ry * 0.45f), strokeWidth = rx * 0.16f)
    }
}

internal fun DrawScope.drawChessMan(
    frame: TableFrame, x: Float, y: Float, baseZ: Float, piece: Int, color: Color, measurer: TextMeasurer,
) {
    val height = chessRise(piece)
    val top = frame.at(x, y, baseZ + height)
    val base = frame.at(x, y, baseZ)
    val rx = frame.cell * 0.3f
    val neck = frame.cell * 0.16f
    val side = frame.cell * 0.07f
    val dark = lerp(color, Color.Black, 0.32f)
    drawPath(quad(
        Offset(base.x + rx, base.y),
        Offset(base.x + rx + side, base.y + side),
        Offset(top.x + neck + side, top.y + side * 0.4f),
        Offset(top.x + neck, top.y),
    ), dark)
    drawPath(quad(
        Offset(base.x - rx, base.y),
        Offset(base.x + rx, base.y),
        Offset(top.x + neck, top.y),
        Offset(top.x - neck, top.y),
    ), color)
    val symbol = chessSymbol(piece)
    val ink = if (color.luminance() > 0.55f) Color(0xFF1A1A1A) else Color(0xFFF6F1E4)
    val layout = measurer.measure(symbol, TextStyle(color = ink, fontSize = (frame.cell * 0.34f / density).sp, fontWeight = FontWeight.Bold))
    val mid = Offset((base.x + top.x) / 2f, (base.y + top.y) / 2f)
    drawText(layout, topLeft = mid - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

internal fun chessRise(piece: Int) = when (abs(piece)) {
    1 -> 0.62f
    2 -> 0.78f
    3 -> 0.86f
    4 -> 0.8f
    5 -> 1.05f
    else -> 1.18f
}

internal fun chessSymbol(piece: Int): String = when (abs(piece)) {
    1 -> if (piece > 0) "♙" else "♟"
    2 -> if (piece > 0) "♘" else "♞"
    3 -> if (piece > 0) "♗" else "♝"
    4 -> if (piece > 0) "♖" else "♜"
    5 -> if (piece > 0) "♕" else "♛"
    else -> if (piece > 0) "♔" else "♚"
}

internal fun coversCell(frame: TableFrame, pos: Offset, col: Int, row: Int, topZ: Float, pieceTop: Float, rx: Float): Boolean {
    val q = frame.square(col, row, topZ)
    if (pointInQuad(pos, q[0], q[1], q[2], q[3])) return true
    if (pieceTop <= topZ) return false
    val base = frame.at(col + 0.5f, row + 0.5f, topZ)
    val crown = frame.at(col + 0.5f, row + 0.5f, pieceTop)
    return pointInQuad(
        pos,
        Offset(base.x - rx, base.y + rx * 0.3f),
        Offset(base.x + rx, base.y + rx * 0.3f),
        Offset(crown.x + rx, crown.y - rx * 0.2f),
        Offset(crown.x - rx, crown.y - rx * 0.2f),
    )
}
