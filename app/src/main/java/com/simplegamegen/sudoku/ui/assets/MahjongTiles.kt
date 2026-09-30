package com.simplegamegen.sudoku.ui.assets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplegamegen.sudoku.mahjong.MahjongRules
import com.simplegamegen.sudoku.mahjong.MahjongTile as StackTile
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.roundToInt

private val TileBlue = Color(0xFF1F5FA8)
private val TileGreen = Color(0xFF2E7D32)
private val TileGreenDark = Color(0xFF1B5E20)
private val TileRed = Color(0xFFC62828)
private val TileInk = Color(0xFF1B1B1F)
private val Numerals = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")
private val Winds = listOf("東", "南", "西", "北")

/** Spoken name for accessibility and messages. */
fun mahjongName(face: Int): String = when (face) {
    in 0..8 -> "${face + 1} of dots"
    in 9..17 -> "${face - 8} of bamboo"
    in 18..26 -> "${face - 17} of characters"
    27 -> "East wind"; 28 -> "South wind"; 29 -> "West wind"; 30 -> "North wind"
    31 -> "Red dragon"; 32 -> "Green dragon"
    else -> "White dragon"
}

/** Width-to-height ratio of a tile face. */
const val TILE_ASPECT = 1.32f

/**
 * A layered Mahjong tile: themed back, ivory side and a face with traditional art.
 * The composable is [width] + [depth] wide so stacked tiles show their thickness.
 */
@Composable
fun MahjongTile(face: Int, modifier: Modifier = Modifier, width: Dp = 46.dp, depth: Dp = 5.dp,
    free: Boolean = true, selected: Boolean = false, hinted: Boolean = false) {
    val look = LocalGameLook.current
    val measurer = rememberTextMeasurer()
    val c = look.colors
    Canvas(modifier.size(width + depth, width * TILE_ASPECT + depth)) {
        val d = depth.toPx()
        drawMahjongBrick(face, Offset.Zero, size.width - d, size.height - d, d, measurer, c.pieceFace, c.pieceEdge, c.tileBack, c.highlight, free, selected, hinted)
    }
}

internal fun DrawScope.drawMahjongBrick(
    face: Int, origin: Offset, w: Float, h: Float, d: Float, measurer: TextMeasurer,
    faceColor: Color, edgeColor: Color, backColor: Color, highlight: Color,
    free: Boolean, selected: Boolean, hinted: Boolean,
) {
    val dx = d * 0.72f
    val dy = d
    fun out(p: Offset) = p + Offset(dx, dy)
    val tr = origin + Offset(w, 0f)
    val br = origin + Offset(w, h)
    val bl = origin + Offset(0f, h)
    drawPath(quad(tr, out(tr), out(br), br), lerp(edgeColor, Color.Black, 0.28f))
    drawPath(quad(bl, br, out(br), out(bl)), lerp(edgeColor, backColor, 0.35f))
    val corner = CornerRadius(w * 0.12f)
    drawRoundRect(Brush.linearGradient(listOf(faceColor, lerp(faceColor, edgeColor, 0.35f)), origin, origin + Offset(w, h)),
        topLeft = origin, size = Size(w, h), cornerRadius = corner)
    drawRoundRect(edgeColor, topLeft = origin, size = Size(w, h), cornerRadius = corner, style = Stroke(maxOf(1f, w * 0.025f)))
    val inner = Rect(origin.x + w * 0.1f, origin.y + h * 0.08f, origin.x + w * 0.9f, origin.y + h * 0.92f)
    drawTileFace(face, inner, measurer)
    if (!free) drawRoundRect(Color.Black.copy(alpha = 0.28f), topLeft = origin, size = Size(w, h), cornerRadius = corner)
    if (selected || hinted) {
        val sw = w * 0.08f
        drawRoundRect(highlight, topLeft = origin + Offset(sw / 2, sw / 2), size = Size(w - sw, h - sw), cornerRadius = corner,
            style = Stroke(sw, pathEffect = if (hinted && !selected) PathEffect.dashPathEffect(floatArrayOf(sw * 1.6f, sw)) else null))
    }
}

private fun DrawScope.drawTileFace(face: Int, box: Rect, measurer: TextMeasurer) {
    when (face) {
        in 0..8 -> drawDots(face + 1, box)
        in 9..17 -> drawBamboo(face - 8, box)
        in 18..26 -> {
            val n = face - 17
            glyph(measurer, Numerals[n - 1], TileInk, Offset(box.center.x, box.top + box.height * 0.27f), box.height * 0.36f)
            glyph(measurer, "萬", TileRed, Offset(box.center.x, box.top + box.height * 0.72f), box.height * 0.4f)
            glyph(measurer, n.toString(), TileBlue, Offset(box.left + box.width * 0.08f, box.top + box.height * 0.04f), box.height * 0.14f, corner = true)
        }
        in 27..30 -> glyph(measurer, Winds[face - 27], TileInk, box.center, box.height * 0.6f)
        31 -> glyph(measurer, "中", TileRed, box.center, box.height * 0.62f)
        32 -> glyph(measurer, "發", TileGreen, box.center, box.height * 0.6f)
        else -> {
            val r = Rect(box.left + box.width * 0.1f, box.top + box.height * 0.12f, box.right - box.width * 0.1f, box.bottom - box.height * 0.12f)
            drawRect(TileBlue, r.topLeft, r.size, style = Stroke(box.width * 0.08f))
            val i = box.width * 0.13f
            drawRect(TileBlue, r.topLeft + Offset(i, i), Size(r.width - 2 * i, r.height - 2 * i), style = Stroke(box.width * 0.03f))
        }
    }
}

private fun DrawScope.glyph(measurer: TextMeasurer, text: String, color: Color, at: Offset, height: Float, corner: Boolean = false) {
    val layout = measurer.measure(text, TextStyle(color = color, fontSize = (height / density / 1.18f).sp,
        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif))
    val topLeft = if (corner) at else at - Offset(layout.size.width / 2f, layout.size.height / 2f)
    drawText(layout, topLeft = topLeft)
}

private fun DrawScope.dot(center: Offset, r: Float, color: Color) {
    drawCircle(color, r, center)
    drawCircle(Color.White, r * 0.74f, center)
    drawCircle(color, r * 0.56f, center)
    drawCircle(Color.White, r * 0.18f, center)
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        drawCircle(Color.White, r * 0.07f, center + Offset((kotlin.math.cos(a) * r * 0.37f).toFloat(), (kotlin.math.sin(a) * r * 0.37f).toFloat()))
    }
}

private fun DrawScope.drawDots(n: Int, b: Rect) {
    val B = TileBlue; val G = TileGreen; val R = TileRed
    fun at(x: Float, y: Float) = Offset(b.left + x * b.width, b.top + y * b.height)
    if (n == 1) {
        val c = b.center; val r = b.width * 0.46f
        drawCircle(G, r, c); drawCircle(Color.White, r * 0.86f, c)
        for (k in 0 until 12) {
            val a = Math.toRadians(k * 30.0)
            drawCircle(B, r * 0.12f, c + Offset((kotlin.math.cos(a) * r * 0.66f).toFloat(), (kotlin.math.sin(a) * r * 0.66f).toFloat()))
        }
        drawCircle(R, r * 0.42f, c); drawCircle(Color.White, r * 0.3f, c); drawCircle(R, r * 0.18f, c)
        return
    }
    val spec: List<Triple<Float, Float, Color>> = when (n) {
        2 -> listOf(Triple(.5f, .26f, G), Triple(.5f, .74f, B))
        3 -> listOf(Triple(.22f, .18f, B), Triple(.5f, .5f, R), Triple(.78f, .82f, G))
        4 -> listOf(Triple(.26f, .26f, B), Triple(.74f, .26f, G), Triple(.26f, .74f, G), Triple(.74f, .74f, B))
        5 -> listOf(Triple(.24f, .2f, B), Triple(.76f, .2f, G), Triple(.5f, .5f, R), Triple(.24f, .8f, G), Triple(.76f, .8f, B))
        6 -> listOf(Triple(.28f, .17f, G), Triple(.72f, .17f, G), Triple(.28f, .55f, R), Triple(.72f, .55f, R), Triple(.28f, .84f, R), Triple(.72f, .84f, R))
        7 -> listOf(Triple(.2f, .12f, G), Triple(.5f, .24f, G), Triple(.8f, .36f, G),
            Triple(.3f, .62f, R), Triple(.7f, .62f, R), Triple(.3f, .88f, R), Triple(.7f, .88f, R))
        8 -> listOf(.12f, .37f, .63f, .88f).flatMap { y -> listOf(Triple(.3f, y, B), Triple(.7f, y, B)) }
        else -> listOf(.17f to B, .5f to R, .83f to G).flatMap { (y, col) ->
            listOf(Triple(.2f, y, col), Triple(.5f, y, col), Triple(.8f, y, col)) }
    }
    val r = b.width * when (n) { 2 -> 0.21f; 3 -> 0.17f; 4, 5 -> 0.18f; 6 -> 0.15f; else -> 0.13f }
    spec.forEach { (x, y, col) -> dot(at(x, y), r, col) }
}

private fun DrawScope.stick(center: Offset, length: Float, thick: Float, color: Color) {
    val dark = if (color == TileGreen) TileGreenDark else color.copy(red = color.red * 0.7f, green = color.green * 0.7f, blue = color.blue * 0.7f)
    val top = Offset(center.x - thick / 2, center.y - length / 2)
    drawRoundRect(color, top, Size(thick, length), CornerRadius(thick / 2))
    drawLine(Color.White.copy(alpha = 0.55f), Offset(center.x - thick * 0.15f, top.y + thick * 0.4f),
        Offset(center.x - thick * 0.15f, top.y + length - thick * 0.4f), strokeWidth = thick * 0.18f)
    for (f in listOf(0.08f, 0.5f, 0.92f)) {
        val y = top.y + length * f
        drawLine(dark, Offset(center.x - thick * 0.62f, y), Offset(center.x + thick * 0.62f, y), strokeWidth = thick * 0.28f)
    }
}

private fun DrawScope.drawBamboo(n: Int, b: Rect) {
    fun at(x: Float, y: Float) = Offset(b.left + x * b.width, b.top + y * b.height)
    val t = b.width * 0.14f
    val long = b.height * 0.4f; val short = b.height * 0.27f
    val G = TileGreen; val R = TileRed; val B = TileBlue
    when (n) {
        1 -> drawBird(b)
        2 -> listOf(.26f, .74f).forEach { stick(at(.5f, it), long, t, G) }
        3 -> { stick(at(.5f, .26f), long, t, G); stick(at(.28f, .74f), long, t, G); stick(at(.72f, .74f), long, t, G) }
        4 -> listOf(.26f, .74f).forEach { y -> stick(at(.28f, y), long, t, if (y < .5f) G else B); stick(at(.72f, y), long, t, if (y < .5f) B else G) }
        5 -> { listOf(.24f, .76f).forEach { y -> stick(at(.2f, y), long, t, G); stick(at(.8f, y), long, t, if (y < .5f) B else G) }
            stick(at(.5f, .5f), long, t, R) }
        6 -> listOf(.26f, .74f).forEach { y -> listOf(.2f, .5f, .8f).forEach { x -> stick(at(x, y), long, t, if (y < .5f) G else B) } }
        7 -> { stick(at(.5f, .15f), short, t, R)
            listOf(.5f, .84f).forEach { y -> listOf(.2f, .5f, .8f).forEach { x -> stick(at(x, y), short, t, G) } } }
        8 -> listOf(.26f, .74f).forEach { y -> listOf(.14f, .38f, .62f, .86f).forEach { x ->
            rotate(if (x < .5f) 14f else -14f, pivot = at(x, y)) { stick(at(x, y), long * 0.95f, t * 0.9f, if (y < .5f) G else B) } } }
        else -> listOf(.16f, .5f, .84f).forEach { y -> listOf(.2f, .5f, .8f).forEach { x ->
            stick(at(x, y), short, t, if (x == .5f) R else if (y < .5f) B else G) } }
    }
}

/** The traditional sparrow of the one-bamboo tile, simplified. */
private fun DrawScope.drawBird(b: Rect) {
    fun at(x: Float, y: Float) = Offset(b.left + x * b.width, b.top + y * b.height)
    fun poly(vararg p: Pair<Float, Float>) = Path().apply {
        moveTo(at(p[0].first, p[0].second).x, at(p[0].first, p[0].second).y)
        p.drop(1).forEach { (x, y) -> lineTo(at(x, y).x, at(x, y).y) }; close()
    }
    listOf(Triple(.12f, .5f, TileBlue), Triple(.18f, .72f, TileRed), Triple(.3f, .88f, TileGreen)).forEach { (x, y, col) ->
        drawPath(poly(.48f to .62f, x to y, x + .1f to y + .08f), col)
    }
    drawOval(TileGreen, at(.3f, .42f), Size(b.width * .5f, b.height * .32f))
    drawPath(poly(.38f to .5f, .66f to .46f, .58f to .66f), TileBlue)
    drawCircle(TileGreen, b.width * .15f, at(.72f, .32f))
    drawPath(poly(.84f to .3f, .98f to .34f, .84f to .38f), Color(0xFFE0A526))
    drawCircle(Color.White, b.width * .045f, at(.75f, .3f)); drawCircle(TileInk, b.width * .025f, at(.75f, .3f))
    drawPath(poly(.66f to .18f, .7f to .06f, .76f to .18f), TileRed)
    drawLine(TileInk, at(.5f, .74f), at(.46f, .9f), strokeWidth = b.width * .03f)
    drawLine(TileInk, at(.58f, .74f), at(.62f, .9f), strokeWidth = b.width * .03f)
}

/**
 * The pile seen from above and to the side. Each tile is a brick; a tile on a higher
 * layer sits up and to the left, on top of the one below.
 */
@Composable
fun MahjongBoard(
    tiles: List<StackTile>,
    removed: Set<Int>,
    selected: Int?,
    hinted: Set<Int>,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalGameLook.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val visible = tiles.filter { it.id !in removed }.sortedWith(compareBy({ it.z }, { it.y }, { it.x }))
    if (visible.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxX = tiles.maxOf { it.x }
        val maxY = tiles.maxOf { it.y }
        val maxZ = tiles.maxOf { it.z }
        val depthRatio = 0.34f
        val shear = 0.16f
        val dxR = depthRatio * 0.72f
        val dyR = depthRatio
        val xUnits = maxX * (1f + dxR) / 2f + maxY * shear + maxZ * dxR + 1f + dxR
        val yUnits = maxY * (TILE_ASPECT + dyR) / 2f + maxZ * dyR + TILE_ASPECT + dyR
        val faceW = minOf(with(density) { maxWidth.toPx() } * 0.96f / xUnits, with(density) { 54.dp.toPx() })
        val faceH = faceW * TILE_ASPECT
        val dx = faceW * dxR
        val dy = faceW * dyR
        val xStep = (faceW + dx) / 2f
        val yStep = (faceH + dy) / 2f
        val pad = faceW * 0.08f
        fun origin(tile: StackTile) = Offset(
            pad + tile.x * xStep + tile.y * faceW * shear + (maxZ - tile.z) * dx,
            pad + tile.y * yStep + (maxZ - tile.z) * dy,
        )
        val boardW = pad * 2 + xUnits * faceW
        val boardH = pad * 2 + yUnits * faceW
        val c = look.colors
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(with(density) { boardW.toDp() }, with(density) { boardH.toDp() })) {
            visible.forEach { tile ->
                val at = origin(tile)
                val free = MahjongRules.free(tiles, removed, tile.id)
                Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                    .size(with(density) { faceW.toDp() }, with(density) { faceH.toDp() })
                    .semantics {
                        contentDescription = say("${mahjongName(tile.face)}, ${if (free) "free" else "blocked"}")
                        if (tile.id == selected) stateDescription = say("Selected")
                        if (free && enabled) onClick { onSelect(tile.id); true }
                    })
            }
            Canvas(Modifier.matchParentSize().pointerInput(faceW, removed, enabled) {
                detectTapGestures { pos ->
                    if (!enabled) return@detectTapGestures
                    val hit = visible.asReversed().firstOrNull { tile ->
                        val at = origin(tile)
                        pos.x in at.x..at.x + faceW && pos.y in at.y..at.y + faceH
                    } ?: return@detectTapGestures
                    onSelect(hit.id)
                }
            }) {
                drawRoundRect(c.table, Offset(pad * 0.3f, pad * 0.3f), Size(boardW - pad * 0.6f, boardH - pad * 0.6f), CornerRadius(faceW * 0.2f))
                visible.forEach { tile ->
                    drawMahjongBrick(
                        tile.face, origin(tile), faceW, faceH, faceW * depthRatio, measurer,
                        c.pieceFace, c.pieceEdge, c.tileBack, c.highlight,
                        free = MahjongRules.free(tiles, removed, tile.id),
                        selected = tile.id == selected,
                        hinted = tile.id in hinted,
                    )
                }
            }
        }
        }
    }
}
