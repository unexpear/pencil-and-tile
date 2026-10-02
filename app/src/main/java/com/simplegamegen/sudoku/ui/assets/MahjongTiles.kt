package com.simplegamegen.sudoku.ui.assets

import com.simplegamegen.sudoku.ui.screens.rememberBoardCamera
import com.simplegamegen.sudoku.ui.screens.BoardWithViews
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
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
    val dx = d * 0.5f
    val dy = d
    fun out(p: Offset, t: Float = 1f) = p + Offset(dx * t, dy * t)
    val tr = origin + Offset(w, 0f)
    val br = origin + Offset(w, h)
    val bl = origin + Offset(0f, h)
    val corner = CornerRadius(w * 0.12f)
    // A soft shadow on the table, cast down and to the right.
    drawRoundRect(Color.Black.copy(alpha = 0.16f), origin + Offset(dx * 1.5f, dy * 1.5f), Size(w, h), corner)
    // The thickness: an ivory layer under the face and the coloured back beneath it, like a real tile.
    val ivory = lerp(faceColor, edgeColor, 0.3f)
    val split = 0.5f
    drawPath(quad(tr, out(tr, split), out(br, split), br), lerp(ivory, Color.Black, 0.22f))
    drawPath(quad(out(tr, split), out(tr), out(br), out(br, split)), lerp(backColor, Color.Black, 0.3f))
    drawPath(quad(bl, br, out(br, split), out(bl, split)), lerp(ivory, Color.Black, 0.08f))
    drawPath(quad(out(bl, split), out(br, split), out(br), out(bl)), lerp(backColor, Color.Black, 0.12f))
    drawLine(lerp(backColor, Color.Black, 0.45f), out(tr), out(br), strokeWidth = maxOf(1f, w * 0.015f))
    drawLine(lerp(backColor, Color.Black, 0.45f), out(bl), out(br), strokeWidth = maxOf(1f, w * 0.015f))
    // The face, lit from the upper left, with a bevel that catches the light.
    drawRoundRect(Brush.linearGradient(listOf(lerp(faceColor, Color.White, 0.25f), faceColor, lerp(faceColor, edgeColor, 0.3f)), origin, origin + Offset(w, h)),
        topLeft = origin, size = Size(w, h), cornerRadius = corner)
    val bevel = maxOf(1f, w * 0.035f)
    drawRoundRect(Color.White.copy(alpha = 0.55f), origin + Offset(bevel, bevel), Size(w - bevel * 2, h - bevel * 2), corner, style = Stroke(bevel * 0.8f))
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

/** The 3D tile: a green back layer under an ivory top, each a rounded slab, in squares (a tile is one wide). */
private const val TileThick = 0.5f
private const val BackShare = 0.38f
private val TileBack by lazy { slabMesh(0.96f, TILE_ASPECT * 0.97f, TileThick * BackShare, 0.1f, 0.02f) }
private val TileTop by lazy { slabMesh(0.96f, TILE_ASPECT * 0.97f, TileThick * (1 - BackShare), 0.1f, 0.05f) }

/** Mahjong's own camera presets: from the front, from a corner, and straight down. */
internal val MahjongViews = listOf(
    BoardView("Front", yaw = 0f, pitch = 60f, distance = 1.7f),
    BoardView("Corner", yaw = 32f, pitch = 54f, distance = 1.8f),
    BoardView("Top", yaw = 0f, pitch = 90f, distance = 2f),
)

/** A matched pair on its way out: the tiles and when they left. */
private class Leaving(val tiles: List<StackTile>)

/**
 * The pile in 3D, on a roomy felt table, with the same cameras as the chess board (presets, a free camera to turn and
 * pinch, saved views). Every tile is a real brick: a green back under an ivory top with its face painted on. A
 * matched pair lifts out of the pile, drifts together and bursts into sparkles.
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
    val camera = rememberBoardCamera("mahjong_views", MahjongViews)
    val visible = tiles.filter { it.id !in removed }
    // Watch for pairs leaving the pile, so they can be shown going.
    var before by remember { mutableStateOf(removed) }
    var leaving by remember { mutableStateOf<Leaving?>(null) }
    val out = remember { Animatable(1f) }
    LaunchedEffect(removed) {
        val gone = removed - before
        before = removed
        if (gone.isEmpty() || gone.size > 2) return@LaunchedEffect
        leaving = Leaving(tiles.filter { it.id in gone })
        out.snapTo(0f)
        out.animateTo(1f, tween(1150, easing = LinearEasing))
        leaving = null
    }
    if (tiles.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxX = tiles.maxOf { it.x }
        val maxY = tiles.maxOf { it.y }
        val maxZ = tiles.maxOf { it.z }
        val wide = (maxX + 2) * 0.5f + 0.5f
        val deep = (maxY + 2) * 0.5f * TILE_ASPECT + 0.5f
        val n = kotlin.math.ceil(wide).toInt()
        val offX = (n - (maxX + 2) * 0.5f) / 2f
        val offY = 0.25f
        // A roomy table: the pile has space round it, and the camera can zoom in when it's needed.
        BoardWithViews(camera, n, peakZ = (maxZ + 1) * TileThick + 1.5f, margin = 0.25f, rows = deep, height = maxWidth * 1.15f) { frame ->
            fun cx(t: StackTile) = offX + t.x * 0.5f + 0.5f
            fun cy(t: StackTile) = offY + t.y * 0.5f * TILE_ASPECT + TILE_ASPECT / 2f
            fun base(t: StackTile) = t.z * TileThick
            fun face(x: Float, y: Float, z: Float): List<Offset> {
                val hw = 0.42f; val hd = TILE_ASPECT * 0.45f
                return listOf(frame.at(x - hw, y - hd, z), frame.at(x + hw, y - hd, z), frame.at(x + hw, y + hd, z), frame.at(x - hw, y + hd, z))
            }
            fun face(t: StackTile) = face(cx(t), cy(t), base(t) + TileThick)
            fun front(t: StackTile): List<Offset> {
                val y = cy(t) + TILE_ASPECT * 0.485f
                return listOf(frame.at(cx(t) - 0.48f, y, base(t) + TileThick), frame.at(cx(t) + 0.48f, y, base(t) + TileThick),
                    frame.at(cx(t) + 0.48f, y, base(t)), frame.at(cx(t) - 0.48f, y, base(t)))
            }
            // Lower layers first; in a layer, far tiles before near ones.
            val order = visible.sortedWith(compareBy<StackTile>({ it.z }, { -frame.depth(cx(it), cy(it), base(it)) }))
            val c = look.colors
            order.forEach { tile ->
                val at = frame.at(cx(tile), cy(tile), base(tile) + TileThick)
                val free = MahjongRules.free(tiles, removed, tile.id)
                Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp)
                    .semantics {
                        contentDescription = say("${mahjongName(tile.face)}, ${if (free) "free" else "blocked"}")
                        if (tile.id == selected) stateDescription = say("Selected")
                        if (free && enabled) onClick { onSelect(tile.id); true }
                    })
            }
            Canvas(Modifier.matchParentSize().pointerInput(frame, removed, enabled) {
                detectTapGestures { pos ->
                    if (!enabled) return@detectTapGestures
                    val hit = order.asReversed().firstOrNull { tile ->
                        val f = face(tile); val fr = front(tile)
                        pointInQuad(pos, f[0], f[1], f[2], f[3]) || pointInQuad(pos, fr[0], fr[1], fr[2], fr[3])
                    } ?: return@detectTapGestures
                    onSelect(hit.id)
                }
            }) {
                // The felt the tiles lie on, with a little room all round.
                val g = 0.6f
                drawPath(quad(frame.at(-g, -g, 0f), frame.at(n + g, -g, 0f), frame.at(n + g, deep + g, 0f), frame.at(-g, deep + g, 0f)), c.table)
                order.forEach { tile ->
                    val free = MahjongRules.free(tiles, removed, tile.id)
                    drawTile3d(frame, tile.face, cx(tile), cy(tile), base(tile), 1f, measurer, c.tileBack, c.pieceFace)
                    val q = face(tile).let { f -> quad(f[0], f[1], f[2], f[3]) }
                    if (!free) drawPath(q, Color.Black.copy(alpha = 0.26f))
                    if (tile.id == selected || tile.id in hinted) {
                        val w = frame.unitAt(cx(tile), cy(tile), base(tile)) * 0.07f
                        drawPath(q, c.highlight, style = Stroke(w, pathEffect = if (tile.id in hinted && tile.id != selected) PathEffect.dashPathEffect(floatArrayOf(w * 1.6f, w)) else null))
                        if (tile.id == selected) drawPath(q, c.highlight.copy(alpha = 0.18f))
                    }
                }
                // The pair that's leaving: pulled up out of the pile, drawn together, then gone in a burst of sparkles.
                leaving?.let { l ->
                    val t = out.value
                    val mx = l.tiles.map(::cx).average().toFloat()
                    val my = l.tiles.map(::cy).average().toFloat()
                    val top = l.tiles.maxOf { base(it) } + TileThick
                    // Pulled straight up out of the pile, then drawn together above it, then gone.
                    val rise = (t / 0.4f).coerceIn(0f, 1f)
                    val lift = (1 - (1 - rise) * (1 - rise)) * 2.2f
                    val gather = ((t - 0.25f) / 0.4f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                    val fade = 1f - ((t - 0.66f) / 0.14f).coerceIn(0f, 1f)
                    l.tiles.forEach { tile ->
                        val x = cx(tile) + (mx - cx(tile)) * gather * 0.85f
                        val y = cy(tile) + (my - cy(tile)) * gather * 0.85f
                        if (fade > 0f) drawTile3d(frame, tile.face, x, y, base(tile) + lift, fade, measurer, c.tileBack, c.pieceFace)
                    }
                    if (t > 0.64f) drawSparkles(frame, mx, my, top + 2.2f, (t - 0.64f) / 0.36f, c.highlight, l.tiles.first().id)
                }
            }
        }
    }
}

/** One tile at ([x], [y]) standing on height [z]: its two layers and its face. */
private fun DrawScope.drawTile3d(frame: TableFrame, face: Int, x: Float, y: Float, z: Float, alpha: Float, measurer: TextMeasurer, back: Color, ivory: Color) {
    if (alpha >= 0.999f) drawDiscOnPlane(frame, floatArrayOf(x + 0.08f, y + 0.1f, z + 0.002f), floatArrayOf(0.55f, 0f, 0f), floatArrayOf(0f, 0.75f, 0f), 1f, Color.Black.copy(alpha = 0.18f))
    drawMesh(frame, TileBack, Pose(x, y, z), back, Satin, alpha = alpha)
    drawMesh(frame, TileTop, Pose(x, y, z + TileThick * BackShare), ivory, Satin, alpha = alpha)
    val hw = 0.42f; val hd = TILE_ASPECT * 0.45f; val zt = z + TileThick
    val q = listOf(frame.at(x - hw, y - hd, zt), frame.at(x + hw, y - hd, zt), frame.at(x + hw, y + hd, zt), frame.at(x - hw, y + hd, zt))
    drawOnQuad(q, 100f, 132f, alpha) { drawTileFace(face, Rect(8f, 8f, 92f, 124f), measurer) }
}

/** A burst of sparks and glints from a point above the table, falling and fading as [t] runs 0 to 1. */
private fun DrawScope.drawSparkles(frame: TableFrame, x: Float, y: Float, z: Float, t: Float, color: Color, seed: Int) {
    val center = frame.at(x, y, z)
    val unit = frame.unitAt(x, y, z)
    val r = java.util.Random(seed * 31L)
    val gold = Color(0xFFFFD166)
    // A ring of light that spreads and thins.
    val ring = unit * (0.4f + 2.2f * t)
    drawCircle(gold.copy(alpha = (1f - t) * 0.8f), ring, center, style = Stroke(unit * 0.12f * (1f - t) + 1f))
    repeat(36) { k ->
        val a = r.nextDouble() * 2 * Math.PI
        val speed = unit * (1.2f + 2.4f * r.nextFloat())
        val ease = 1 - (1 - t) * (1 - t)
        val px = center.x + (kotlin.math.cos(a) * speed * ease).toFloat()
        val py = center.y + (kotlin.math.sin(a) * speed * ease).toFloat() + unit * 1.4f * t * t
        val size = unit * (0.1f + 0.14f * r.nextFloat()) * (1f - t * 0.5f)
        val tint = when (k % 3) { 0 -> gold; 1 -> Color.White; else -> color }
        val alpha = (1f - t).coerceIn(0f, 1f)
        // A four-pointed glint: two thin crossed strokes and a bright core.
        drawLine(tint.copy(alpha = alpha), Offset(px - size * 2, py), Offset(px + size * 2, py), strokeWidth = size * 0.5f)
        drawLine(tint.copy(alpha = alpha), Offset(px, py - size * 2), Offset(px, py + size * 2), strokeWidth = size * 0.5f)
        drawCircle(Color.White.copy(alpha = alpha), size * 0.6f, Offset(px, py))
    }
    // A soft flash where the tiles vanished.
    val flash = (1f - t * 2f).coerceIn(0f, 1f)
    if (flash > 0f) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f * flash), Color.Transparent), center, unit * 1.4f), unit * 1.4f, center)
}
