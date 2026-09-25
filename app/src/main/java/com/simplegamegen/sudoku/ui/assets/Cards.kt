package com.simplegamegen.sudoku.ui.assets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplegamegen.sudoku.tabletop.SolitaireState
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

/** Card suits in engine order: spades, hearts, diamonds, clubs. */
internal fun pathOf(d: String): Path = PathParser().parsePathString(d).toPath()

private fun circlePath(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

/** 24-unit suit silhouettes. */
object SuitShapes {
    val spade = pathOf("M12 1.8C12 1.8 2.8 8.7 2.8 14C2.8 16.6 4.8 18.4 7.2 18.4C8.9 18.4 10.3 17.6 11.1 16.4C10.9 18.6 10.2 20.4 8.6 22H15.4C13.8 20.4 13.1 18.6 12.9 16.4C13.7 17.6 15.1 18.4 16.8 18.4C19.2 18.4 21.2 16.6 21.2 14C21.2 8.7 12 1.8 12 1.8Z")
    val heart = pathOf("M12 21.5C12 21.5 2.5 15 2.5 8.6C2.5 5.5 4.9 3.2 7.8 3.2C9.6 3.2 11.1 4.2 12 5.6C12.9 4.2 14.4 3.2 16.2 3.2C19.1 3.2 21.5 5.5 21.5 8.6C21.5 15 12 21.5 12 21.5Z")
    val diamond = pathOf("M12 1.5Q15.6 7 20.2 12Q15.6 17 12 22.5Q8.4 17 3.8 12Q8.4 7 12 1.5Z")
    val club = pathOf(circlePath(12f, 6.6f, 4.4f) + circlePath(6.7f, 13.3f, 4.4f) + circlePath(17.3f, 13.3f, 4.4f) +
        "M10 9h4v6h-4zM11.2 13L10.3 17.5C10 19.4 9.3 20.7 8.3 22H15.7C14.7 20.7 14 19.4 13.7 17.5L12.8 13Z")
    fun of(suit: Int): Path = when (suit) { 0 -> spade; 1 -> heart; 2 -> diamond; else -> club }
}

val CardRed = Color(0xFFC62828)
val CardBlack = Color(0xFF1B1B1F)
private val CardIvory = Color(0xFFFFFEFA)
private val CourtGold = Color(0xFFE0A526)
private val CourtRed = Color(0xFFC0392B)
private val CourtBlue = Color(0xFF1F4E8C)
private val CourtSkin = Color(0xFFF3CFA8)
private val CourtHair = Color(0xFF6B4423)
private val CourtInk = Color(0xFF1B1B1F)

fun suitColor(card: Int): Color = if (SolitaireState.red(card)) CardRed else CardBlack

/** Draws a 24-unit suit shape centered at [center] with the given height. */
fun DrawScope.drawSuit(suit: Int, center: Offset, height: Float, color: Color, flipped: Boolean = false) {
    val s = height / 24f
    withTransform({
        translate(center.x - 12f * s, center.y - 12f * s)
        scale(s, s, pivot = Offset.Zero)
        if (flipped) rotate(180f, pivot = Offset(12f, 12f))
    }) { drawPath(SuitShapes.of(suit), color) }
}

private val RANKS = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")

/** Standard pip positions (fractions of the pip field) for 2–10. */
private fun pipLayout(rank: Int): List<Offset> {
    val l = 0.22f; val c = 0.5f; val r = 0.78f
    return when (rank) {
        2 -> listOf(Offset(c, 0f), Offset(c, 1f))
        3 -> listOf(Offset(c, 0f), Offset(c, .5f), Offset(c, 1f))
        4 -> listOf(Offset(l, 0f), Offset(r, 0f), Offset(l, 1f), Offset(r, 1f))
        5 -> listOf(Offset(l, 0f), Offset(r, 0f), Offset(c, .5f), Offset(l, 1f), Offset(r, 1f))
        6 -> listOf(Offset(l, 0f), Offset(r, 0f), Offset(l, .5f), Offset(r, .5f), Offset(l, 1f), Offset(r, 1f))
        7 -> pipLayout(6) + Offset(c, .25f)
        8 -> pipLayout(6) + Offset(c, .25f) + Offset(c, .75f)
        9 -> listOf(0f, 1f / 3, 2f / 3, 1f).flatMap { y -> listOf(Offset(l, y), Offset(r, y)) } + Offset(c, .5f)
        10 -> listOf(0f, 1f / 3, 2f / 3, 1f).flatMap { y -> listOf(Offset(l, y), Offset(r, y)) } + Offset(c, 1f / 6) + Offset(c, 5f / 6)
        else -> emptyList()
    }
}

/**
 * A real-looking playing card. [card] uses engine numbering (suit * 13 + rank - 1).
 * Face-down cards show the themed back.
 */
@Composable
fun PlayingCard(card: Int, modifier: Modifier = Modifier, width: Dp = 56.dp, faceUp: Boolean = true,
    selected: Boolean = false, hinted: Boolean = false) {
    val look = LocalGameLook.current
    val measurer = rememberTextMeasurer()
    val ring = look.colors.highlight
    Canvas(modifier.size(width, width * 1.4f)) {
        if (faceUp) drawCardFace(card, measurer) else drawCardBack(look.colors.cardBack, look.colors.cardBackPattern)
        if (selected || hinted) {
            val w = size.width * 0.07f
            drawRoundRect(ring, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(size.width * 0.09f), style = Stroke(w,
                    pathEffect = if (hinted && !selected) PathEffect.dashPathEffect(floatArrayOf(w * 2, w)) else null))
        }
    }
}

/** Empty pile outline with an optional ghost suit or label. */
@Composable
fun CardSlot(modifier: Modifier = Modifier, width: Dp = 56.dp, suit: Int? = null, label: String? = null, highlighted: Boolean = false) {
    val look = LocalGameLook.current
    val measurer = rememberTextMeasurer()
    val line = if (highlighted) look.colors.highlight else look.colors.onTable.copy(alpha = 0.45f)
    Canvas(modifier.size(width, width * 1.4f)) {
        val corner = CornerRadius(size.width * 0.09f)
        drawRoundRect(look.colors.tableInset, cornerRadius = corner)
        drawRoundRect(line, cornerRadius = corner, style = Stroke(size.width * (if (highlighted) 0.06f else 0.03f),
            pathEffect = if (highlighted) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        if (suit != null) drawSuit(suit, center, size.width * 0.5f, look.colors.onTable.copy(alpha = 0.3f))
        if (label != null) {
            val layout = measurer.measure(label, TextStyle(color = look.colors.onTable.copy(alpha = 0.55f),
                fontSize = (size.width * 0.26f / density).sp, fontWeight = FontWeight.Bold))
            drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }
    }
}

fun DrawScope.drawCardBack(back: Color, pattern: Color) {
    val corner = CornerRadius(size.width * 0.09f)
    drawRoundRect(CardIvory, cornerRadius = corner)
    drawRoundRect(Color(0x33000000), cornerRadius = corner, style = Stroke(1.2f))
    val inset = size.width * 0.07f
    val inner = Rect(inset, inset, size.width - inset, size.height - inset)
    drawRoundRect(back, topLeft = inner.topLeft, size = inner.size, cornerRadius = CornerRadius(size.width * 0.05f))
    clipRect(inner.left, inner.top, inner.right, inner.bottom) {
        val step = size.width * 0.16f
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(pattern, Offset(x, inner.top), Offset(x + size.height, inner.top + size.height), strokeWidth = size.width * 0.025f)
            drawLine(pattern, Offset(x + size.height, inner.top), Offset(x, inner.top + size.height), strokeWidth = size.width * 0.025f)
            x += step
        }
    }
    drawRoundRect(pattern, topLeft = inner.topLeft + Offset(inset * 0.5f, inset * 0.5f),
        size = Size(inner.width - inset, inner.height - inset), cornerRadius = CornerRadius(size.width * 0.04f), style = Stroke(size.width * 0.02f))
    drawCircle(back, radius = size.width * 0.16f, center = center)
    drawCircle(pattern, radius = size.width * 0.16f, center = center, style = Stroke(size.width * 0.03f))
    drawCircle(pattern, radius = size.width * 0.06f, center = center)
}

private fun DrawScope.drawCardFace(card: Int, measurer: TextMeasurer) {
    val w = size.width; val h = size.height
    val corner = CornerRadius(w * 0.09f)
    drawRoundRect(Brush.verticalGradient(listOf(CardIvory, Color(0xFFF4F1E8))), cornerRadius = corner)
    drawRoundRect(Color(0x40000000), cornerRadius = corner, style = Stroke(maxOf(1f, w * 0.02f)))
    val suit = SolitaireState.suit(card)
    val rank = card % 13 + 1
    val ink = suitColor(card)
    // Corner indices (top-left, and rotated bottom-right as on real cards).
    val indexStyle = TextStyle(color = ink, fontSize = (w * 0.3f / density).sp, fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Serif, letterSpacing = if (rank == 10) (-1).sp else 0.sp)
    val label = measurer.measure(RANKS[rank - 1], indexStyle)
    val colW = w * 0.24f
    fun index() {
        drawText(label, topLeft = Offset(colW / 2 + w * 0.02f - label.size.width / 2f, h * 0.015f))
        drawSuit(suit, Offset(colW / 2 + w * 0.02f, h * 0.015f + label.size.height + w * 0.08f), w * 0.17f, ink)
    }
    index()
    rotate(180f) { index() }
    // Pip field sits between the index columns.
    val field = Rect(w * 0.29f, h * 0.2f, w * 0.71f, h * 0.8f)
    when (rank) {
        1 -> drawSuit(suit, center, w * (if (suit == 0) 0.62f else 0.46f), ink)
        in 2..10 -> {
            val pip = w * (if (rank >= 9) 0.15f else 0.17f)
            for (p in pipLayout(rank)) {
                val at = Offset(field.left + p.x * field.width, field.top + p.y * field.height)
                drawSuit(suit, at, pip, ink, flipped = p.y > 0.5f)
            }
        }
        else -> drawCourt(rank, suit, ink, Rect(w * 0.26f, h * 0.09f, w * 0.74f, h * 0.91f))
    }
}

/** Double-headed court portrait in the traditional red, blue and gold. */
private fun DrawScope.drawCourt(rank: Int, suit: Int, ink: Color, frame: Rect) {
    drawRect(Color(0xFFFBF3DC), frame.topLeft, frame.size)
    clipRect(frame.left, frame.top, frame.right, frame.bottom) {
        val half = Rect(frame.left, frame.top, frame.right, frame.center.y)
        drawCourtHalf(rank, half, suit)
        rotate(180f, pivot = frame.center) { drawCourtHalf(rank, half, suit) }
    }
    drawLine(CourtGold, Offset(frame.left, frame.center.y), Offset(frame.right, frame.center.y), strokeWidth = size.width * 0.015f)
    drawRect(ink, frame.topLeft, frame.size, style = Stroke(size.width * 0.02f))
    drawSuit(suit, Offset(frame.left + frame.width * 0.2f, frame.top + frame.height * 0.08f), frame.width * 0.18f, ink)
}

private fun DrawScope.drawCourtHalf(rank: Int, box: Rect, suit: Int) {
    fun p(x: Float, y: Float) = Offset(box.left + x / 100f * box.width, box.top + y / 100f * box.height)
    fun path(vararg pts: Pair<Float, Float>) = Path().apply {
        moveTo(p(pts[0].first, pts[0].second).x, p(pts[0].first, pts[0].second).y)
        pts.drop(1).forEach { (x, y) -> lineTo(p(x, y).x, p(x, y).y) }
        close()
    }
    val robe = if ((rank + suit) % 2 == 0) CourtRed else CourtBlue
    val trim = if (robe == CourtRed) CourtBlue else CourtRed
    val line = Stroke(box.width * 0.018f)
    // Shoulders and robe.
    val body = path(10f to 100f, 16f to 78f, 34f to 66f, 50f to 70f, 66f to 66f, 84f to 78f, 90f to 100f)
    drawPath(body, robe); drawPath(body, CourtInk, style = line)
    val stripe = path(44f to 72f, 56f to 72f, 58f to 100f, 42f to 100f)
    drawPath(stripe, trim); drawPath(stripe, CourtInk, style = line)
    val collar = path(30f to 68f, 50f to 80f, 70f to 68f, 64f to 64f, 50f to 72f, 36f to 64f)
    drawPath(collar, CourtGold); drawPath(collar, CourtInk, style = line)
    // Held object: sword (king), flower (queen) or leaf (jack).
    when (rank) {
        13 -> { drawLine(Color(0xFF9AA3AD), p(80f, 100f), p(80f, 30f), strokeWidth = box.width * 0.045f)
            drawLine(CourtGold, p(72f, 64f), p(88f, 64f), strokeWidth = box.width * 0.05f) }
        12 -> { drawLine(Color(0xFF2E7D32), p(22f, 100f), p(22f, 62f), strokeWidth = box.width * 0.03f)
            drawCircle(CourtRed, box.width * 0.07f, p(22f, 58f)); drawCircle(CourtGold, box.width * 0.03f, p(22f, 58f)) }
        else -> { val leaf = path(18f to 96f, 14f to 70f, 22f to 52f, 28f to 72f)
            drawPath(leaf, Color(0xFF2E7D32)); drawPath(leaf, CourtInk, style = line) }
    }
    // Hair behind the face.
    when (rank) {
        12 -> { drawOval(CourtHair, p(30f, 34f), Size(box.width * 0.4f, box.height * 0.38f)) }
        11 -> { drawOval(CourtHair, p(35f, 30f), Size(box.width * 0.3f, box.height * 0.3f)) }
        else -> drawOval(Color(0xFF8C8C8C), p(34f, 32f), Size(box.width * 0.32f, box.height * 0.34f))
    }
    // Face.
    val face = Rect(p(39f, 32f), p(61f, 60f))
    drawOval(CourtSkin, face.topLeft, face.size); drawOval(CourtInk, face.topLeft, face.size, style = line)
    drawCircle(CourtInk, box.width * 0.018f, p(45f, 44f)); drawCircle(CourtInk, box.width * 0.018f, p(55f, 44f))
    drawLine(CourtRed, p(46f, 53f), p(54f, 53f), strokeWidth = box.width * 0.02f)
    if (rank == 13) { val beard = path(40f to 50f, 50f to 66f, 60f to 50f, 55f to 56f, 45f to 56f)
        drawPath(beard, Color(0xFF8C8C8C)); drawPath(beard, CourtInk, style = line) }
    // Headwear.
    val hat = when (rank) {
        13 -> path(34f to 34f, 35f to 12f, 42f to 22f, 50f to 8f, 58f to 22f, 65f to 12f, 66f to 34f)
        12 -> path(37f to 33f, 38f to 18f, 44f to 25f, 50f to 14f, 56f to 25f, 62f to 18f, 63f to 33f)
        else -> path(33f to 34f, 40f to 18f, 58f to 16f, 70f to 26f, 67f to 34f)
    }
    drawPath(hat, if (rank == 11) trim else CourtGold); drawPath(hat, CourtInk, style = line)
    if (rank != 11) { drawCircle(CourtRed, box.width * 0.03f, p(50f, 26f)) }
    else drawLine(CourtGold, p(60f, 18f), p(76f, 6f), strokeWidth = box.width * 0.03f)
}
