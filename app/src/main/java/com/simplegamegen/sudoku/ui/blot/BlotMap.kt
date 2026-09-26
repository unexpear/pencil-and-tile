package com.simplegamegen.sudoku.ui.blot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplegamegen.sudoku.ui.i18n.say
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ---------------- The Discover map ----------------
//
// The Discover puzzles sit on one patch of land as numbered blocks. Solving a puzzle inks its block and the land
// around it, a creature moves in, and every few puzzles the settlement gains something new.

/** The land, row by row: a digit or letter is a puzzle (1-9, then a-q for 10-26), '#' is land, '.' is sea. */
private val LAND = listOf(
    ".1##2##.",
    "##3##4##",
    "#5###6#.",
    ".##7##8#",
    "9##a###.",
    "#b##c#d#",
    ".##e##f#",
    "#g##h#i.",
    ".#j##k#.",
    "#l##m#..",
    ".n##o#..",
    "#p##q#..",
)

private data class MapTile(val col: Int, val row: Int, val puzzle: Int, val owner: Int)

/** Every land tile, with the puzzle it is (or -1) and the puzzle whose land it belongs to (the nearest one). */
private val TILES: List<MapTile> by lazy {
    val puzzles = LAND.flatMapIndexed { r, line -> line.mapIndexedNotNull { c, ch ->
        val n = when (ch) { in '1'..'9' -> ch - '1'; in 'a'..'q' -> 9 + (ch - 'a'); else -> -1 }
        if (n >= 0) Triple(c, r, n) else null
    } }
    LAND.flatMapIndexed { r, line -> line.mapIndexedNotNull { c, ch ->
        if (ch == '.') null else {
            val own = puzzles.find { it.first == c && it.second == r }
            if (own != null) MapTile(c, r, own.third, own.third)
            else MapTile(c, r, -1, puzzles.minBy { abs(it.first - c) + abs(it.second - r) * 1.1 + it.third * 0.01 }.third)
        }
    } }
}
private val COLS = LAND.maxOf { it.length }

/**
 * The map: [solved] puzzles are done (the first [solved] ones), the next one can be played, the rest wait.
 * [fresh] is a puzzle just solved, whose land inks over and whose creature walks in while you watch.
 */
@Composable
fun BlotMap(theme: BlotTheme, tc: BlotColors, solved: Int, total: Int, fresh: Int?, onPlay: (Int) -> Unit) {
    // Debug builds can open any puzzle, for testing the later ones.
    val context = androidx.compose.ui.platform.LocalContext.current
    val testing = remember { context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    val measurer = rememberTextMeasurer()
    val loop = rememberInfiniteTransition(label = "map")
    val time by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "life")
    val arrive = remember(fresh) { Animatable(if (fresh == null) 1f else 0f) }
    LaunchedEffect(fresh) { if (fresh != null) arrive.animateTo(1f, tween(1800, easing = LinearEasing)) }
    val rows = LAND.size
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val tile = min(maxWidth / (COLS + 0.6f), 64.dp)
        Canvas(Modifier.fillMaxWidth().height(tile * (rows + 0.8f))
            .semantics { contentDescription = say("Map: $solved of $total puzzles solved") }
            .pointerInput(solved) {
                detectTapGestures { at ->
                    val step = tile.toPx(); val left = (size.width - step * COLS) / 2
                    val c = ((at.x - left) / step).toInt(); val r = ((at.y - step * 0.4f) / step).toInt()
                    val hit = TILES.firstOrNull { it.col == c && it.row == r && it.puzzle >= 0 } ?: return@detectTapGestures
                    if ((hit.puzzle <= solved || testing) && hit.puzzle < total) onPlay(hit.puzzle)
                }
            }) {
            val step = tile.toPx()
            val depth = step * 0.16f
            val left = (size.width - step * COLS) / 2
            val top = step * 0.4f
            fun rectOf(t: MapTile) = Rect(left + t.col * step, top + t.row * step, left + (t.col + 1) * step, top + (t.row + 1) * step)
            // A soft shadow under the whole island.
            for (t in TILES) drawRoundRect(Color.Black.copy(alpha = 0.05f), rectOf(t).topLeft + Offset(depth * 0.8f, depth * 1.2f), Size(step, step), CornerRadius(step * 0.12f))
            // Back rows first, so each block's side sits under the block below it.
            for (t in TILES.sortedWith(compareBy({ it.row }, { it.col }))) {
                val r = rectOf(t)
                val done = t.owner < solved
                // The newly solved puzzle's land inks over, its own block first, then spreading out.
                val inked = when {
                    t.owner == fresh -> ((arrive.value * 2f) - (if (t.puzzle >= 0) 0f else 0.4f + 0.1f * ((t.col + t.row) % 3))).coerceIn(0f, 1f)
                    done -> 1f
                    else -> 0f
                }
                drawMapBlock(r, depth, tc, inked)
                if (t.puzzle >= 0) {
                    val open = t.puzzle <= solved && t.puzzle < total
                    val label = (t.puzzle + 1).toString()
                    val color = when {
                        inked > 0.5f -> tc.paper.copy(alpha = 0.55f)
                        open -> tc.letter
                        else -> tc.letter.copy(alpha = 0.25f)
                    }
                    val layout = measurer.measure(label, TextStyle(color = color, fontSize = (step * 0.42f / density).sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif))
                    val face = Rect(r.left, r.top, r.right - depth, r.bottom - depth)
                    drawText(layout, topLeft = Offset(face.center.x - layout.size.width / 2, face.center.y - layout.size.height / 2))
                    // The next puzzle to play glows gently.
                    if (t.puzzle == solved && t.puzzle < total) {
                        val pulse = 0.5f + 0.5f * sin(time * 2 * PI * 6).toFloat()
                        drawRoundRect(tc.accent.copy(alpha = 0.35f + 0.4f * pulse), face.topLeft, face.size, CornerRadius(step * 0.1f), style = Stroke(step * 0.06f))
                    }
                }
            }
            // The settlement grows every three puzzles, then its creatures.
            val land = TILES.filter { it.owner < solved && (it.owner != fresh || arrive.value >= 1f) }
            if (land.isNotEmpty()) {
                val props = listOf(3, 6, 9, 12, 15, 18, 21, 24).filter { solved >= it }
                props.forEachIndexed { k, _ ->
                    val spot = land.filter { it.puzzle < 0 }.let { plain -> plain.getOrNull((k * 5 + 2) % plain.size.coerceAtLeast(1)) } ?: return@forEachIndexed
                    val face = rectOf(spot).let { Rect(it.left, it.top, it.right - depth, it.bottom - depth) }
                    drawLandmark(k, theme, face, tc, time)
                }
            }
            for (k in 0 until solved) {
                val home = TILES.filter { it.owner == k }
                if (home.isEmpty()) continue
                val walkIn = if (k == fresh) arrive.value.let { ((it - 0.5f) / 0.5f).coerceIn(0f, 1f) } else 1f
                if (walkIn <= 0f) continue
                // Each creature ambles between the squares of its own patch, hopping as it goes.
                val t = (time * home.size * 1.5f + k * 0.37f) % home.size
                val a = home[t.toInt() % home.size]; val b = home[(t.toInt() + 1) % home.size]
                val f = t - t.toInt()
                val ca = rectOf(a).let { Offset(it.center.x - depth / 2, it.center.y - depth / 2) }
                val cb = rectOf(b).let { Offset(it.center.x - depth / 2, it.center.y - depth / 2) }
                val hop = abs(sin(f * PI)).toFloat() * step * 0.12f
                var at = Offset(ca.x + (cb.x - ca.x) * f, ca.y + (cb.y - ca.y) * f - hop)
                // Walking in from off the edge of the map.
                if (walkIn < 1f) at = Offset(at.x - (1f - walkIn) * size.width * 0.6f, at.y)
                drawCreature(theme, at, step * 0.34f, tc, time * 20 + k, facingRight = cb.x >= ca.x)
            }
        }
    }
}

/** One map block: a face and a hatched side, white or inked by [inked] (0..1). */
private fun DrawScope.drawMapBlock(r: Rect, depth: Float, tc: BlotColors, inked: Float) {
    val face = Rect(r.left, r.top, r.right - depth, r.bottom - depth)
    val radius = CornerRadius(face.width * 0.1f)
    fun mix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)
    val top = mix(tc.tile, tc.mark, inked)
    val side = mix(Color(tc.tile.red * 0.8f, tc.tile.green * 0.78f, tc.tile.blue * 0.76f), Color(tc.mark.red * 0.55f, tc.mark.green * 0.55f, tc.mark.blue * 0.55f), inked)
    val edge = tc.mark.copy(alpha = 0.6f)
    val sideRect = Rect(face.left + depth, face.top + depth, face.right + depth, face.bottom + depth)
    drawRoundRect(side, sideRect.topLeft, face.size, radius)
    clipRect(sideRect.left, sideRect.top, sideRect.right, sideRect.bottom) {
        for (k in 0..5) {
            val x = face.left + face.width * (0.18f + 0.16f * k)
            drawLine(edge, Offset(x, face.bottom), Offset(x + depth, sideRect.bottom), 1.5f)
            val y = face.top + face.height * (0.18f + 0.16f * k)
            drawLine(edge, Offset(face.right, y), Offset(sideRect.right, y + depth), 1.5f)
        }
    }
    drawRoundRect(edge, sideRect.topLeft, face.size, radius, style = Stroke(1.5f))
    drawRoundRect(top, face.topLeft, face.size, radius)
    drawRoundRect(edge, face.topLeft, face.size, radius, style = Stroke(1.5f))
    // Little tufts on inked land, as if something grows there now.
    if (inked >= 1f) for (k in 0..1) {
        val x = face.left + face.width * (0.25f + 0.45f * k); val y = face.top + face.height * (0.3f + 0.4f * k)
        drawLine(tc.detail.copy(alpha = 0.6f), Offset(x, y), Offset(x + face.width * 0.05f, y - face.height * 0.08f), 1.5f, cap = StrokeCap.Round)
        drawLine(tc.detail.copy(alpha = 0.6f), Offset(x + face.width * 0.05f, y - face.height * 0.08f), Offset(x + face.width * 0.1f, y), 1.5f, cap = StrokeCap.Round)
    }
}

/** The theme's little residents: ink octopuses, or fish when the squares are sea water. */
private fun DrawScope.drawCreature(theme: BlotTheme, at: Offset, s: Float, tc: BlotColors, wiggle: Float, facingRight: Boolean) {
    val outline = tc.paper
    if (theme.mark == BlotMark.WAVES) {
        // A round little fish.
        val dir = if (facingRight) 1f else -1f
        val tail = Path().apply {
            moveTo(at.x - dir * s * 0.45f, at.y)
            lineTo(at.x - dir * s * 0.8f, at.y - s * 0.3f + sin(wiggle.toDouble()).toFloat() * s * 0.08f)
            lineTo(at.x - dir * s * 0.8f, at.y + s * 0.3f)
            close()
        }
        drawPath(tail, tc.accent)
        drawOval(tc.accent, Offset(at.x - s * 0.5f, at.y - s * 0.35f), Size(s, s * 0.7f))
        drawOval(outline, Offset(at.x - s * 0.5f, at.y - s * 0.35f), Size(s, s * 0.7f), style = Stroke(s * 0.08f))
        drawCircle(Color.White, s * 0.13f, Offset(at.x + dir * s * 0.2f, at.y - s * 0.08f))
        drawCircle(Color(0xFF1E1526), s * 0.07f, Offset(at.x + dir * s * 0.23f, at.y - s * 0.08f))
        return
    }
    // A tiny ink octopus: a round head, three curling legs and two white eyes.
    for (k in -1..1) {
        val leg = Path().apply {
            moveTo(at.x + k * s * 0.25f, at.y + s * 0.2f)
            quadraticTo(at.x + k * s * 0.45f + sin((wiggle + k).toDouble()).toFloat() * s * 0.1f, at.y + s * 0.55f, at.x + k * s * 0.3f, at.y + s * 0.62f)
        }
        drawPath(leg, tc.paper, style = Stroke(s * 0.24f, cap = StrokeCap.Round))
        drawPath(leg, tc.detail, style = Stroke(s * 0.14f, cap = StrokeCap.Round))
    }
    // Lighter than the inked land they live on, with a paper outline so they stand out.
    drawCircle(tc.detail, s * 0.42f, at)
    drawCircle(Color.White.copy(alpha = 0.3f), s * 0.14f, Offset(at.x - s * 0.15f, at.y - s * 0.2f))
    drawCircle(outline.copy(alpha = 0.8f), s * 0.42f, at, style = Stroke(s * 0.07f))
    val look = if (facingRight) s * 0.06f else -s * 0.06f
    for (side in listOf(-1f, 1f)) {
        drawCircle(Color.White, s * 0.12f, Offset(at.x + side * s * 0.15f + look, at.y - s * 0.05f))
        drawCircle(Color(0xFF1E1526), s * 0.06f, Offset(at.x + side * s * 0.15f + look * 1.5f, at.y - s * 0.04f))
    }
}

/** What the settlement gains every three puzzles: camp things for ink, beach things for the sea. */
private fun DrawScope.drawLandmark(k: Int, theme: BlotTheme, face: Rect, tc: BlotColors, time: Float) {
    val s = face.width
    val c = face.center
    val paper = tc.paper
    val sea = theme.mark == BlotMark.WAVES
    when (k) {
        0 -> if (sea) { // a scallop shell
            for (f in -2..2) drawLine(paper, Offset(c.x, c.y + s * 0.2f), Offset(c.x + f * s * 0.1f, c.y - s * 0.15f), s * 0.05f, cap = StrokeCap.Round)
            drawArc(paper, 200f, 140f, false, Offset(c.x - s * 0.25f, c.y - s * 0.25f), Size(s * 0.5f, s * 0.4f), style = Stroke(s * 0.05f))
        } else { // a tent
            val tent = Path().apply { moveTo(c.x - s * 0.3f, c.y + s * 0.22f); lineTo(c.x, c.y - s * 0.26f); lineTo(c.x + s * 0.3f, c.y + s * 0.22f); close() }
            drawPath(tent, paper); drawLine(tc.mark, Offset(c.x, c.y - s * 0.26f), Offset(c.x, c.y + s * 0.22f), s * 0.04f)
        }
        1 -> if (sea) { // swaying seaweed
            for (f in -1..1) {
                val sway = sin((time * 2 * PI * 8) + f).toFloat() * s * 0.06f
                val weed = Path().apply { moveTo(c.x + f * s * 0.12f, c.y + s * 0.25f); quadraticTo(c.x + f * s * 0.12f + sway, c.y, c.x + f * s * 0.12f - sway, c.y - s * 0.28f) }
                drawPath(weed, paper, style = Stroke(s * 0.06f, cap = StrokeCap.Round))
            }
        } else { // a campfire that flickers
            val flick = 1f + 0.15f * sin(time * 2 * PI * 20).toFloat()
            drawLine(paper, Offset(c.x - s * 0.22f, c.y + s * 0.22f), Offset(c.x + s * 0.22f, c.y + s * 0.12f), s * 0.06f, cap = StrokeCap.Round)
            drawLine(paper, Offset(c.x + s * 0.22f, c.y + s * 0.22f), Offset(c.x - s * 0.22f, c.y + s * 0.12f), s * 0.06f, cap = StrokeCap.Round)
            val flame = Path().apply {
                moveTo(c.x - s * 0.14f, c.y + s * 0.12f)
                quadraticTo(c.x - s * 0.12f, c.y - s * 0.1f * flick, c.x, c.y - s * 0.3f * flick)
                quadraticTo(c.x + s * 0.12f, c.y - s * 0.1f * flick, c.x + s * 0.14f, c.y + s * 0.12f); close()
            }
            drawPath(flame, Color(0xFFFFB347)); drawPath(flame, paper, style = Stroke(s * 0.03f))
        }
        2 -> if (sea) { // a treasure chest
            drawRoundRect(Color(0xFFB07A3A), Offset(c.x - s * 0.25f, c.y - s * 0.1f), Size(s * 0.5f, s * 0.32f), CornerRadius(s * 0.04f))
            drawRoundRect(paper, Offset(c.x - s * 0.25f, c.y - s * 0.1f), Size(s * 0.5f, s * 0.32f), CornerRadius(s * 0.04f), style = Stroke(s * 0.035f))
            drawCircle(Color(0xFFFFD166), s * 0.05f, Offset(c.x, c.y + s * 0.02f))
        } else { // a round little tree
            drawLine(paper, Offset(c.x, c.y + s * 0.26f), Offset(c.x, c.y), s * 0.06f)
            drawCircle(paper, s * 0.2f, Offset(c.x, c.y - s * 0.08f), style = Stroke(s * 0.05f))
        }
        4 -> if (sea) { // a starfish
            for (a in 0 until 5) {
                val ang = a * 72.0 * PI / 180 - PI / 2
                drawLine(Color(0xFFFF8A65), c, Offset(c.x + (kotlin.math.cos(ang) * s * 0.26f).toFloat(), c.y + (kotlin.math.sin(ang) * s * 0.26f).toFloat()), s * 0.09f, cap = StrokeCap.Round)
            }
        } else { // a little well
            drawRoundRect(paper, Offset(c.x - s * 0.2f, c.y - s * 0.02f), Size(s * 0.4f, s * 0.24f), CornerRadius(s * 0.05f), style = Stroke(s * 0.05f))
            drawLine(paper, Offset(c.x - s * 0.2f, c.y - s * 0.02f), Offset(c.x - s * 0.2f, c.y - s * 0.28f), s * 0.04f)
            drawLine(paper, Offset(c.x + s * 0.2f, c.y - s * 0.02f), Offset(c.x + s * 0.2f, c.y - s * 0.28f), s * 0.04f)
            drawLine(paper, Offset(c.x - s * 0.26f, c.y - s * 0.28f), Offset(c.x + s * 0.26f, c.y - s * 0.28f), s * 0.05f, cap = StrokeCap.Round)
        }
        else -> { // a flag, or a sandcastle's flag, fluttering on top
            if (sea) drawRect(paper, Offset(c.x - s * 0.22f, c.y), Size(s * 0.44f, s * 0.22f), style = Stroke(s * 0.04f))
            drawLine(paper, Offset(c.x - s * 0.05f, c.y + s * 0.22f), Offset(c.x - s * 0.05f, c.y - s * 0.3f), s * 0.04f)
            val wave = sin(time * 2 * PI * 10).toFloat() * s * 0.04f
            val flag = Path().apply { moveTo(c.x - s * 0.05f, c.y - s * 0.3f); lineTo(c.x + s * 0.25f, c.y - s * 0.22f + wave); lineTo(c.x - s * 0.05f, c.y - s * 0.12f); close() }
            drawPath(flag, tc.accent)
        }
    }
}
