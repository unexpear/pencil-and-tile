package com.simplegamegen.sudoku.ui.assets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val PipInk = Color(0xFF17171A)
private val Brass = Color(0xFFC9A04A)

/** Pip positions on a 3×3 grid (0 = top-left, 8 = bottom-right). */
private val PIPS = mapOf(0 to emptyList(), 1 to listOf(4), 2 to listOf(0, 8), 3 to listOf(0, 4, 8),
    4 to listOf(0, 2, 6, 8), 5 to listOf(0, 2, 4, 6, 8), 6 to listOf(0, 2, 3, 5, 6, 8))

/** An ivory domino with sunk pips, a center groove and a brass spinner. [unit] is one square half. */
@Composable
fun DominoTile(a: Int, b: Int, modifier: Modifier = Modifier, horizontal: Boolean = false, unit: Dp = 26.dp,
    selected: Boolean = false, dimmed: Boolean = false) {
    val look = LocalGameLook.current
    val c = look.colors
    Canvas(modifier.size(if (horizontal) unit * 2 else unit, if (horizontal) unit else unit * 2)) {
        val corner = CornerRadius(min(size.width, size.height) * 0.16f)
        val depth = min(size.width, size.height) * 0.06f
        drawRoundRect(lerp(c.pieceEdge, Color.Black, 0.25f), topLeft = Offset(0f, depth), size = Size(size.width, size.height - depth), cornerRadius = corner)
        drawRoundRect(Brush.linearGradient(listOf(c.pieceFace, lerp(c.pieceFace, c.pieceEdge, 0.4f)), Offset.Zero, Offset(size.width, size.height)),
            size = Size(size.width, size.height - depth), cornerRadius = corner)
        drawRoundRect(c.pieceEdge, size = Size(size.width, size.height - depth), cornerRadius = corner, style = Stroke(maxOf(1f, size.minDimension * 0.04f)))
        val faceH = size.height - depth
        val half = if (horizontal) Size(size.width / 2, faceH) else Size(size.width, faceH / 2)
        val groove = if (horizontal) listOf(Offset(size.width / 2, faceH * 0.14f), Offset(size.width / 2, faceH * 0.86f))
            else listOf(Offset(size.width * 0.14f, faceH / 2), Offset(size.width * 0.86f, faceH / 2))
        drawLine(lerp(c.pieceEdge, Color.Black, 0.3f), groove[0], groove[1], strokeWidth = size.minDimension * 0.05f)
        drawLine(Color.White.copy(alpha = 0.7f), groove[0] + Offset(1.5f, 1.5f), groove[1] + Offset(1.5f, 1.5f), strokeWidth = size.minDimension * 0.02f)
        listOf(a to Offset.Zero, b to if (horizontal) Offset(size.width / 2, 0f) else Offset(0f, faceH / 2)).forEach { (n, origin) ->
            val cell = half.minDimension
            val pad = cell * 0.24f
            val step = (cell - 2 * pad) / 2
            val base = origin + Offset((half.width - cell) / 2, (half.height - cell) / 2)
            PIPS.getValue(n).forEach { i ->
                val p = base + Offset(pad + (i % 3) * step, pad + (i / 3) * step)
                drawCircle(PipInk, cell * 0.095f, p)
                drawCircle(Color.White.copy(alpha = 0.35f), cell * 0.03f, p + Offset(cell * 0.025f, cell * 0.025f))
            }
        }
        drawCircle(Brass, size.minDimension * 0.07f, Offset(size.width / 2, faceH / 2))
        drawCircle(Color.White.copy(alpha = 0.5f), size.minDimension * 0.025f, Offset(size.width / 2 - 1.5f, faceH / 2 - 1.5f))
        if (selected) drawRoundRect(c.highlight, size = Size(size.width, faceH), cornerRadius = corner, style = Stroke(size.minDimension * 0.1f))
        if (dimmed) drawRoundRect(Color.Black.copy(alpha = 0.28f), size = Size(size.width, faceH), cornerRadius = corner)
    }
}

/** A ridged checker; kings carry a gold crown. [player] 1 is the human side. */
@Composable
fun CheckerPiece(player: Int, king: Boolean, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val c = LocalGameLook.current.colors
    val base = if (player > 0) c.playerOne else c.playerTwo
    Canvas(modifier.size(size)) { drawChecker(base, king) }
}

fun DrawScope.drawChecker(base: Color, king: Boolean) {
    val r = size.minDimension / 2 * 0.92f
    val c = Offset(size.width / 2, size.height / 2)
    val light = base.luminance() > 0.5f
    val shade = lerp(base, Color.Black, 0.35f)
    val lift = lerp(base, Color.White, if (light) 0.5f else 0.22f)
    drawCircle(Color.Black.copy(alpha = 0.3f), r, c + Offset(0f, r * 0.1f))
    drawCircle(shade, r, c)
    drawCircle(base, r * 0.93f, c - Offset(0f, r * 0.05f))
    val top = c - Offset(0f, r * 0.05f)
    for (k in 0 until 24) {
        val a = k * Math.PI / 12
        val o = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(shade, top + o * (r * 0.78f), top + o * (r * 0.92f), strokeWidth = r * 0.05f)
    }
    drawCircle(shade, r * 0.7f, top, style = Stroke(r * 0.05f))
    drawCircle(Brush.radialGradient(listOf(lift, base), center = top - Offset(r * 0.25f, r * 0.25f), radius = r * 0.9f), r * 0.66f, top)
    if (king) {
        val crown = Path().apply {
            fun pt(x: Float, y: Float) = top + Offset(x * r, y * r)
            val p = listOf(pt(-.42f, .26f), pt(-.46f, -.24f), pt(-.2f, 0f), pt(0f, -.36f), pt(.2f, 0f), pt(.46f, -.24f), pt(.42f, .26f))
            moveTo(p[0].x, p[0].y); p.drop(1).forEach { lineTo(it.x, it.y) }; close()
        }
        drawPath(crown, Color(0xFFE0A526))
        drawPath(crown, Color(0xFF7A5210), style = Stroke(r * 0.05f))
        drawCircle(Color(0xFFC62828), r * 0.07f, top + Offset(0f, r * 0.08f))
    }
}

/** A Connect Four disc: 1 = red (you), -1 = yellow (the computer). */
fun DrawScope.drawCounter(player: Int, alpha: Float = 1f) {
    val r = size.minDimension / 2 * 0.78f
    val c = Offset(size.width / 2, size.height / 2)
    val (hi, lo) = if (player > 0) Color(0xFFFF8A80) to Color(0xFFC62828) else Color(0xFFFFF59D) to Color(0xFFF9A825)
    drawCircle(Color.Black.copy(alpha = 0.28f * alpha), r, c + Offset(0f, r * 0.08f))
    drawCircle(lo, r, c, alpha = alpha)
    drawCircle(Brush.radialGradient(listOf(hi, lo), center = c - Offset(r * 0.32f, r * 0.36f), radius = r * 1.15f), r * 0.92f, c, alpha = alpha)
}

/** A glossy Reversi disc: 1 = black, -1 = white. */
fun DrawScope.drawDisc(player: Int, alpha: Float = 1f) {
    val r = size.minDimension / 2 * 0.84f
    val c = Offset(size.width / 2, size.height / 2)
    drawCircle(Color.Black.copy(alpha = 0.35f * alpha), r, c + Offset(0f, r * 0.1f))
    val (hi, lo) = if (player > 0) Color(0xFF5A5A5E) to Color(0xFF0E0E10) else Color.White to Color(0xFFC9C9C4)
    drawCircle(lo, r, c, alpha = alpha)
    drawCircle(Brush.radialGradient(listOf(hi, lo), center = c - Offset(r * 0.35f, r * 0.4f), radius = r * 1.2f), r * 0.9f, c, alpha = alpha)
}

/** Red pennant flag on a black stand. */
fun DrawScope.drawFlag() {
    val s = size.minDimension
    val o = Offset((size.width - s) / 2, (size.height - s) / 2)
    fun p(x: Float, y: Float) = o + Offset(x * s, y * s)
    drawLine(Color(0xFF222222), p(.42f, .2f), p(.42f, .74f), strokeWidth = s * 0.07f)
    val flag = Path().apply { moveTo(p(.45f, .18f).x, p(.45f, .18f).y); lineTo(p(.78f, .33f).x, p(.78f, .33f).y); lineTo(p(.45f, .5f).x, p(.45f, .5f).y); close() }
    drawPath(flag, Color(0xFFD32F2F))
    drawRect(Color(0xFF222222), p(.26f, .72f), Size(s * .34f, s * .08f))
    drawRect(Color(0xFF222222), p(.34f, .66f), Size(s * .18f, s * .07f))
}

/** Classic spiked mine. */
fun DrawScope.drawMine() {
    val s = size.minDimension
    val c = Offset(size.width / 2, size.height / 2)
    val ink = Color(0xFF17171A)
    for (k in 0 until 8) rotate(k * 45f, pivot = c) { drawLine(ink, c - Offset(0f, s * .34f), c + Offset(0f, s * .34f), strokeWidth = s * .06f) }
    drawCircle(ink, s * .25f, c)
    drawCircle(Color.White, s * .07f, c - Offset(s * .08f, s * .08f))
}

/** Classic Minesweeper number colors, lightened for dark themes. */
fun mineNumberColor(n: Int, dark: Boolean): Color {
    val light = listOf(0xFF1565C0, 0xFF2E7D32, 0xFFC62828, 0xFF283593, 0xFF8D2A0E, 0xFF00838F, 0xFF212121, 0xFF616161)
    val night = listOf(0xFF64B5F6, 0xFF81C784, 0xFFEF9A9A, 0xFF9FA8DA, 0xFFFFAB91, 0xFF80DEEA, 0xFFE0E0E0, 0xFFBDBDBD)
    return Color((if (dark) night else light)[(n - 1).coerceIn(0, 7)])
}
