package com.simplegamegen.sudoku.ui.blot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ---------------- Blotwords theme engine: drawing ----------------

/** The outline of a letter square. */
fun tileShape(tile: BlotTile, side: Dp): Shape = when (tile) {
    BlotTile.ROUNDED -> RoundedCornerShape(side * 0.16f)
    BlotTile.SQUARE -> RoundedCornerShape(side * 0.04f)
    BlotTile.BUBBLE -> CircleShape
}

/** True when a theme's filled squares keep moving after they settle (sea water does; ink and stamps don't). */
fun BlotTheme.markMovesWhileIdle(): Boolean = assets[AssetSlot.MARK] == null && mark == BlotMark.WAVES

/** Draws [image] centred in [area], cropped to fill it. */
fun DrawScope.drawPicture(image: ImageBitmap, area: Rect, alpha: Float = 1f) {
    val side = min(image.width, image.height)
    val src = IntOffset((image.width - side) / 2, (image.height - side) / 2)
    drawImage(image, src, IntSize(side, side), IntOffset(area.left.toInt(), area.top.toInt()),
        IntSize(area.width.toInt().coerceAtLeast(1), area.height.toInt().coerceAtLeast(1)), alpha = alpha)
}

// ---------------- Filled squares ----------------

/**
 * Draws a filled square in [area]. [progress] runs from 0 (just inked) to 1 (settled) and may overshoot on a
 * spring; [fill] says how it arrives; [phase] loops 0..1 for squares that keep moving; [seed] keeps each
 * square's own wobble. A [picture] replaces the drawn mark.
 */
fun DrawScope.drawMark(mark: BlotMark, fill: FillStyle, area: Rect, c: BlotColors, seed: Int, progress: Float, phase: Float, picture: ImageBitmap? = null,
    depth: Float = 0f) {
    if (progress <= 0f) return
    val p = progress.coerceIn(0f, 1.2f)
    val settled = p.coerceAtMost(1f)
    // Ink blocks bloom out from the middle whatever the arrival style; the block is the square itself.
    if (picture == null && mark == BlotMark.BLOCK) { drawInkBlock(area, c, seed, p, depth); return }
    // Ink spreads in its own way; every other arrival is a transform around the settled square.
    if (picture == null && mark == BlotMark.BLOT && fill == FillStyle.SPLASH) { drawSpreadingInk(area, c, seed, p); return }
    fun body(alpha: Float = 1f) {
        when {
            picture != null -> drawPicture(picture, area, alpha)
            mark == BlotMark.BLOT || mark == BlotMark.BLOCK -> drawSpreadingInk(area, c, seed, 1f, alpha)
            mark == BlotMark.WAVES -> drawWater(area, c, seed, phase, alpha, level = 1f)
            else -> drawStamp(area, c, alpha)
        }
    }
    when (fill) {
        FillStyle.SPLASH -> scale(p, area.center) { body() }
        FillStyle.RISE -> if (picture == null && mark == BlotMark.WAVES) drawWater(area, c, seed, phase, 1f, level = settled)
            else clipRect(area.left, area.bottom - area.height * settled, area.right, area.bottom) { body() }
        FillStyle.STAMP -> scale(1f + (1f - settled) * 0.35f, area.center) { body(settled) }
        FillStyle.FADE -> body(settled)
        FillStyle.SPIN -> rotate((1f - settled) * 200f, area.center) { scale(p, area.center) { body() } }
        FillStyle.DROP -> translate(top = -(1f - settled) * area.height * 0.9f) { body(settled) }
    }
}

/**
 * A square soaked right through with ink, filling it edge to edge so a word's squares join into one bar. It blooms
 * from the middle into a softly uneven square; [depth] draws the block's side, hatched like a pen sketch.
 */
private fun DrawScope.drawInkBlock(area: Rect, c: BlotColors, seed: Int, progress: Float, depth: Float) {
    val r = Random(seed * 131 + 7)
    val face = Rect(area.left, area.top, area.right - depth, area.bottom - depth)
    val grow = easeOut(progress.coerceAtMost(1f))
    val over = if (progress > 1f) progress else 1f
    val ink = c.mark
    val dark = Color(ink.red * 0.55f, ink.green * 0.55f, ink.blue * 0.55f)
    if (depth > 0f && grow > 0.85f) {
        val side = Rect(face.left + depth, face.top + depth, face.right + depth, face.bottom + depth)
        drawRoundRect(dark, side.topLeft, side.size, CornerRadius(face.width * 0.1f))
        clipRect(side.left, side.top, side.right, side.bottom) {
            for (k in 0..5) {
                val x = face.left + face.width * (0.18f + 0.16f * k)
                drawLine(ink.copy(alpha = 0.5f), Offset(x, face.bottom), Offset(x + depth, side.bottom), 1.5f)
                val y = face.top + face.height * (0.18f + 0.16f * k)
                drawLine(ink.copy(alpha = 0.5f), Offset(face.right, y), Offset(side.right, y + depth), 1.5f)
            }
        }
    }
    // A squircle whose edge wobbles a little, like ink that stopped at the paper's fibres.
    val half = face.width / 2 * (0.3f + 0.7f * grow) * over
    val n = 24
    val pts = (0 until n).map { k ->
        val a = 2 * PI * k / n
        val cx = cos(a); val sy = sin(a)
        val sx = Math.signum(cx) * Math.pow(abs(cx), 0.25).toFloat()
        val ty = Math.signum(sy) * Math.pow(abs(sy), 0.25).toFloat()
        val j = if (grow >= 1f) 0.985f + 0.03f * r.nextFloat() else 0.9f + 0.2f * r.nextFloat()
        Offset(face.center.x + (sx * half * j).toFloat(), face.center.y + (ty * half * j).toFloat())
    }
    val path = Path()
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
    path.moveTo(mid(pts.last(), pts[0]).x, mid(pts.last(), pts[0]).y)
    for (k in 0 until n) { val pt = pts[k]; val m = mid(pt, pts[(k + 1) % n]); path.quadraticTo(pt.x, pt.y, m.x, m.y) }
    path.close()
    drawPath(path, ink)
    // Grain: a few paler strokes, so the ink reads as ink rather than flat paint.
    if (grow >= 1f) repeat(3) {
        val x = face.left + face.width * (0.2f + 0.6f * r.nextFloat()); val y = face.top + face.height * (0.2f + 0.6f * r.nextFloat())
        drawLine(c.detail.copy(alpha = 0.25f), Offset(x, y), Offset(x + face.width * 0.08f, y - face.height * 0.05f), 2f, cap = StrokeCap.Round)
    }
    // Two specks thrown off as it lands.
    val fly = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
    if (fly > 0f) repeat(2) {
        val a = r.nextDouble(0.0, 2 * PI)
        val d = face.width * (0.55f + 0.12f * fly)
        drawCircle(ink.copy(alpha = fly), face.width * 0.035f, Offset(face.center.x + (d * cos(a)).toFloat(), face.center.y + (d * sin(a)).toFloat()))
    }
}

/**
 * Ink soaking into paper: lobes bleed outward at their own speeds until the blot settles, then it stays still.
 * Splatter flies out just as it lands.
 */
private fun DrawScope.drawSpreadingInk(area: Rect, c: BlotColors, seed: Int, progress: Float, alpha: Float = 1f) {
    val r = Random(seed * 7919 + 13)
    val center = area.center
    val full = area.minDimension * 0.47f
    val n = 14
    val pts = (0 until n).map { k ->
        val a = 2 * PI * k / n + r.nextDouble(-0.12, 0.12)
        val target = full * (0.84f + 0.16f * r.nextFloat())
        // Each lobe has its own speed, so the edge creeps out unevenly and catches up as it settles.
        val speed = 0.75f + 0.6f * r.nextFloat()
        val grown = if (progress >= 1f) progress else (progress * speed).coerceAtMost(1f)
        val rr = target * easeOut(grown.coerceAtMost(1.1f))
        Offset(center.x + (rr * cos(a)).toFloat(), center.y + (rr * sin(a)).toFloat())
    }
    val path = Path()
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
    path.moveTo(mid(pts.last(), pts[0]).x, mid(pts.last(), pts[0]).y)
    for (k in 0 until n) {
        val pt = pts[k]; val m = mid(pt, pts[(k + 1) % n])
        path.quadraticTo(pt.x, pt.y, m.x, m.y)
    }
    path.close()
    val grownIn = min(1f, progress)
    // A faint halo where the ink has soaked into the paper around the blot.
    scale(1.08f, center) { drawPath(path, c.mark.copy(alpha = 0.12f * alpha * grownIn)) }
    drawPath(path, c.mark.copy(alpha = alpha))
    // Ink pools a little darker at the edge as it dries.
    drawPath(path, Color(c.mark.red * 0.6f, c.mark.green * 0.6f, c.mark.blue * 0.6f).copy(alpha = 0.5f * alpha * grownIn),
        style = Stroke(full * 0.07f, join = StrokeJoin.Round))
    // A soft sheen so the ink looks wet.
    drawCircle(c.detail.copy(alpha = 0.22f * alpha * grownIn), full * 0.28f * grownIn, Offset(center.x - full * 0.32f, center.y - full * 0.34f))
    // Splatter flies out as the blot lands, then stays put.
    val fly = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
    repeat(3) {
        val a = r.nextDouble(0.0, 2 * PI)
        val d = full * (0.7f + (0.32f + 0.1f * r.nextFloat()) * fly)
        val size = full * (0.05f + 0.06f * r.nextFloat())
        if (fly > 0f) drawCircle(c.mark.copy(alpha = fly * alpha), size, Offset(center.x + (d * cos(a)).toFloat(), center.y + (d * sin(a)).toFloat()))
    }
    // About one blot in three runs down in a drip once it has landed.
    val drip = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
    if (r.nextFloat() < 0.35f && drip > 0f) {
        val x = center.x + full * (r.nextFloat() - 0.5f) * 0.6f
        val top = center.y + full * 0.6f
        val len = full * (0.3f + 0.25f * r.nextFloat()) * drip
        drawLine(c.mark.copy(alpha = alpha), Offset(x, top), Offset(x, top + len), full * 0.11f, cap = StrokeCap.Round)
        drawCircle(c.mark.copy(alpha = alpha), full * 0.09f, Offset(x, top + len))
    }
}

/**
 * Paper for the board: a fine grain of specks and fibres and edges that darken like old paper. [seed] keeps
 * each sheet the same between frames.
 */
fun DrawScope.drawPaperGrain(ink: Color, seed: Int) {
    val r = Random(seed)
    val w = size.width; val h = size.height
    val count = (w * h / 700f).toInt().coerceIn(200, 1800)
    for (tone in listOf(0.035f, 0.06f)) {
        val specks = List(count / 2) { Offset(r.nextFloat() * w, r.nextFloat() * h) }
        drawPoints(specks, PointMode.Points, ink.copy(alpha = tone), 1.6f, StrokeCap.Round)
    }
    repeat(60) {
        val x = r.nextFloat() * w; val y = r.nextFloat() * h
        val len = 6f + 16f * r.nextFloat(); val a = r.nextDouble(0.0, PI)
        drawLine(ink.copy(alpha = 0.05f), Offset(x, y), Offset(x + (cos(a) * len).toFloat(), y + (sin(a) * len).toFloat()), 1f)
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, ink.copy(alpha = 0.08f)), Offset(w / 2, h / 2), maxOf(w, h) * 0.72f))
}

/** Faint speed lines bursting out from the middle of the page, strongest at the edges. */
fun DrawScope.drawSpeedLines(ink: Color, seed: Int) {
    val r = Random(seed)
    val c = Offset(size.width / 2, size.height / 2)
    val far = maxOf(size.width, size.height)
    repeat(46) {
        val a = r.nextDouble(0.0, 2 * PI)
        val start = far * (0.42f + 0.2f * r.nextFloat())
        val wide = far * (0.006f + 0.014f * r.nextFloat())
        val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
        val side = Offset(-dir.y, dir.x)
        val p = Path().apply {
            moveTo(c.x + dir.x * start, c.y + dir.y * start)
            lineTo(c.x + dir.x * far + side.x * wide, c.y + dir.y * far + side.y * wide)
            lineTo(c.x + dir.x * far - side.x * wide, c.y + dir.y * far - side.y * wide)
            close()
        }
        drawPath(p, ink.copy(alpha = 0.05f + 0.04f * r.nextFloat()))
    }
}

private fun easeOut(t: Float) = if (t >= 1f) t else 1f - (1f - t) * (1f - t)

/**
 * A square of sea that never quite sits still: the surface sloshes, two waves roll through and bubbles rise
 * and pop. [level] is how full it is (the water rises in when a square is inked).
 */
private fun DrawScope.drawWater(area: Rect, c: BlotColors, seed: Int, phase: Float, alpha: Float, level: Float) {
    if (level <= 0f) return
    val r = Random(seed * 104729 + 7)
    val w = area.width
    val h = area.height
    val corner = CornerRadius(area.minDimension * 0.2f)
    val tp = phase * 2 * PI + seed
    // The surface: a wavy line near the top that tilts back and forth.
    val surfaceY = area.bottom - h * 0.95f * level
    val water = Path()
    water.moveTo(area.left, area.bottom)
    val steps = 14
    for (i in 0..steps) {
        val x = area.left + w * i / steps
        val tilt = sin(tp).toFloat() * h * 0.035f * (i / steps.toFloat() - 0.5f) * 2
        val ripple = sin(i / steps.toFloat() * 2 * PI * 1.5 + tp * 2).toFloat() * h * 0.022f
        water.lineTo(x, surfaceY + tilt + ripple)
    }
    water.lineTo(area.right, area.bottom); water.close()
    // Keep the square's rounded corners by cutting the water to them.
    val rounded = Path().apply { addRoundRect(RoundRect(area, corner)) }
    val inside = Path().apply { op(rounded, water, PathOperation.Intersect) }
    drawPath(inside, Brush.verticalGradient(listOf(c.mark.copy(alpha = 0.72f * alpha), c.mark.copy(alpha = alpha)), surfaceY, area.bottom))
    if (level < 0.5f) return
    // Two waves rolling through the water.
    for (k in 0..1) {
        val y = area.top + h * (0.42f + 0.26f * k)
        if (y < surfaceY) continue
        val line = Path()
        line.moveTo(area.left + w * 0.14f, y)
        for (i in 1..16) {
            val x = area.left + w * (0.14f + 0.72f * i / 16f)
            line.lineTo(x, y + sin(i / 16f * 2 * PI + k + tp).toFloat() * h * 0.04f)
        }
        drawPath(line, c.detail.copy(alpha = 0.6f * alpha), style = Stroke(area.minDimension * 0.045f, cap = StrokeCap.Round))
    }
    // Bubbles drift up and pop at the surface, each on its own clock.
    repeat(3) {
        val x = area.left + w * (0.2f + 0.6f * r.nextFloat()) + sin(tp * 2 + it).toFloat() * w * 0.03f
        val t = (phase * (0.8f + 0.4f * r.nextFloat()) + r.nextFloat()) % 1f
        val y = area.bottom - (area.bottom - surfaceY) * (0.1f + 0.85f * t)
        val a = 0.85f * (1f - t * t) * alpha
        drawCircle(c.detail.copy(alpha = a), area.minDimension * (0.04f + 0.025f * it), Offset(x, y), style = Stroke(area.minDimension * 0.022f))
    }
}

/** A neat rubber stamp: a filled square with an inner ring and a dot. */
private fun DrawScope.drawStamp(area: Rect, c: BlotColors, alpha: Float) {
    val corner = CornerRadius(area.minDimension * 0.18f)
    drawRoundRect(c.mark.copy(alpha = alpha), area.topLeft, area.size, corner)
    val inset = area.minDimension * 0.16f
    drawRoundRect(c.detail.copy(alpha = 0.55f * alpha), Offset(area.left + inset, area.top + inset),
        Size(area.width - inset * 2, area.height - inset * 2), CornerRadius(area.minDimension * 0.1f), style = Stroke(area.minDimension * 0.05f))
    drawCircle(c.detail.copy(alpha = 0.7f * alpha), area.minDimension * 0.07f, area.center)
}

// ---------------- Tracing trail ----------------

fun DrawScope.drawTrail(style: TrailStyle, points: List<Offset>, width: Float, color: Color,
    /** Segments (by the index of their end point) that run off an edge and come back on the other side. */
    jumps: Set<Int> = emptySet(), travel: Offset = Offset.Zero, stub: Float = 0f) {
    if (points.isEmpty()) return
    // A ring round every picked letter, so the word being traced reads as one connected chain.
    if (style != TrailStyle.NONE) for (p in points) drawCircle(color, width * 1.9f, p, style = Stroke(width * 0.3f))
    if (points.size < 2) return
    when (style) {
        TrailStyle.LINE -> {
            val path = Path()
            points.forEachIndexed { n, o ->
                when {
                    n == 0 -> path.moveTo(o.x, o.y)
                    // Across the join: out past one edge, then in from the other.
                    n in jumps -> {
                        val out = points[n - 1] + travel * stub
                        path.lineTo(out.x, out.y)
                        val back = o - travel * stub
                        path.moveTo(back.x, back.y); path.lineTo(o.x, o.y)
                    }
                    else -> path.lineTo(o.x, o.y)
                }
            }
            drawPath(path, color.copy(alpha = 0.5f), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // An arrowhead at the last letter shows which way the word is going.
            val a = if (points.lastIndex in jumps) points.last() - travel else points[points.size - 2]; val b = points.last()
            val dx = b.x - a.x; val dy = b.y - a.y; val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val ux = dx / len; val uy = dy / len
            val tip = Offset(b.x + ux * width * 2.6f, b.y + uy * width * 2.6f)
            val head = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - ux * width * 1.4f - uy * width, tip.y - uy * width * 1.4f + ux * width)
                lineTo(tip.x - ux * width * 1.4f + uy * width, tip.y - uy * width * 1.4f - ux * width)
                close()
            }
            drawPath(head, color)
        }
        TrailStyle.DOTS -> for (i in 1 until points.size) {
            val a = points[i - 1]; val b = if (i in jumps) a + travel * stub else points[i]
            for (k in 0..4) {
                val t = k / 4f
                drawCircle(color.copy(alpha = 0.6f), width * 0.32f, Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
        }
        TrailStyle.NONE -> {}
    }
}

/**
 * The ink that joins the squares of a written word: drawn behind the marks, it shows in the gaps between them
 * so each word stays visibly one stroke. [t] (0..1) draws it along the word as the word is written.
 */
fun DrawScope.drawInkBridge(style: TrailStyle, points: List<Offset>, t: Float, width: Float, color: Color,
    jumps: Set<Int> = emptySet(), travel: Offset = Offset.Zero, stub: Float = 0f) {
    if (points.size < 2 || t <= 0f || style == TrailStyle.NONE) return
    val reach = t.coerceIn(0f, 1f) * (points.size - 1)
    for (k in 0 until points.size - 1) {
        val part = (reach - k).coerceIn(0f, 1f)
        if (part <= 0f) break
        // Across the join the bar runs off the edge, and in again on the other side.
        if (k + 1 in jumps) {
            drawLine(color, points[k], points[k] + travel * stub * part, width, cap = StrokeCap.Butt)
            drawLine(color, points[k + 1] - travel * stub * part, points[k + 1], width, cap = StrokeCap.Butt)
            continue
        }
        val a = points[k]; val b = points[k + 1]
        val end = Offset(a.x + (b.x - a.x) * part, a.y + (b.y - a.y) * part)
        if (style == TrailStyle.LINE) drawLine(color, a, end, width, cap = StrokeCap.Round)
        else for (d in 0..6) {
            val f = d / 6f * part
            drawCircle(color, width * 0.4f, Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f))
        }
    }
}

// ---------------- Celebration ----------------

/** Pieces floating up over the board as the grid is finished; [t] runs 0..1 over the celebration. */
fun DrawScope.drawParty(style: PartyStyle, amount: Int, t: Float, seed: Int, unit: Float, c: BlotColors, picture: ImageBitmap?) {
    if (style == PartyStyle.NONE || t <= 0f || t >= 1f) return
    val rnd = Random(seed)
    repeat(amount) { n ->
        val x = size.width * rnd.nextFloat()
        val start = rnd.nextFloat() * 0.35f
        val drift = rnd.nextFloat()
        val local = ((t - start) / (1f - start)).coerceIn(0f, 1f)
        if (local <= 0f) return@repeat
        val y = size.height * (1.05f - local * 1.2f)
        val alpha = (1f - local) * 0.9f
        val r = unit * (0.08f + 0.08f * drift)
        val wobble = sin((local * 6 + n) * 1.7f) * unit * 0.15f
        val at = Offset(x + wobble, y)
        when (style) {
            PartyStyle.BUBBLES -> drawCircle(c.detail.copy(alpha = alpha), r, at, style = Stroke(r * 0.3f))
            PartyStyle.DROPS -> drawDrop(at, r, c.mark.copy(alpha = alpha))
            PartyStyle.CONFETTI -> rotate(local * 540f + n * 40f, at) {
                drawRect(listOf(c.accent, c.detail, c.mark)[n % 3].copy(alpha = alpha), Offset(at.x - r, at.y - r * 0.5f), Size(r * 2, r))
            }
            PartyStyle.STARS -> rotate(local * 180f, at) { drawStar(at, r * 1.4f, Color(0xFFFFD166).copy(alpha = alpha)) }
            PartyStyle.HEARTS -> drawHeart(at, r * 1.3f, c.accent.copy(alpha = alpha))
            PartyStyle.PICTURE -> if (picture != null) drawPicture(picture, Rect(at.x - r * 2, at.y - r * 2, at.x + r * 2, at.y + r * 2), alpha)
                else drawStar(at, r * 1.4f, c.accent.copy(alpha = alpha))
            PartyStyle.NONE -> {}
        }
    }
}

private fun DrawScope.drawDrop(at: Offset, r: Float, color: Color) {
    val path = Path()
    path.moveTo(at.x, at.y - r * 2f)
    path.cubicTo(at.x + r * 0.4f, at.y - r * 0.9f, at.x + r, at.y - r * 0.3f, at.x + r, at.y + r * 0.2f)
    path.cubicTo(at.x + r, at.y + r * 1.1f, at.x - r, at.y + r * 1.1f, at.x - r, at.y + r * 0.2f)
    path.cubicTo(at.x - r, at.y - r * 0.3f, at.x - r * 0.4f, at.y - r * 0.9f, at.x, at.y - r * 2f)
    drawPath(path, color)
}

private fun DrawScope.drawHeart(at: Offset, r: Float, color: Color) {
    val path = Path()
    path.moveTo(at.x, at.y + r)
    path.cubicTo(at.x - r * 1.6f, at.y - r * 0.2f, at.x - r * 0.8f, at.y - r * 1.4f, at.x, at.y - r * 0.5f)
    path.cubicTo(at.x + r * 0.8f, at.y - r * 1.4f, at.x + r * 1.6f, at.y - r * 0.2f, at.x, at.y + r)
    drawPath(path, color)
}

// ---------------- Creatures ----------------

/**
 * The theme's creature: a picture or a drawn one, moving the theme's way. [bounce] (0 to 1 and back) plays the
 * word reaction; [happy] makes it cheer.
 */
@Composable
fun Mascot(theme: BlotTheme, colors: BlotColors, happy: Boolean, modifier: Modifier, bounce: Float = 0f) {
    val picture = rememberAsset(theme, AssetSlot.CREATURE)
    val happyPicture = rememberAsset(theme, AssetSlot.CREATURE_HAPPY)
    if (picture == null && theme.mascot == BlotMascot.NONE) return
    val motion = theme.motion
    val loop = rememberInfiniteTransition(label = "mascot")
    val period = ((if (happy) 900 else 2600) * motion.pace.factor).toInt()
    val phase by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        val h = size.height
        val wave = sin(phase * 2 * PI).toFloat()
        val bottom = Offset(size.width / 2, h)
        val idleY = when (motion.idle) {
            IdleStyle.BOB -> wave * h * (if (happy) 0.06f else 0.025f)
            IdleStyle.BOUNCE -> -abs(wave) * h * (if (happy) 0.1f else 0.05f)
            else -> if (happy) wave * h * 0.05f else 0f
        }
        val idleTurn = if (motion.idle == IdleStyle.SWAY) wave * (if (happy) 10f else 6f) else 0f
        val hop = if (motion.react == ReactStyle.HOP) -bounce * h * 0.12f else 0f
        val turn = when (motion.react) {
            ReactStyle.SPIN -> bounce * 180f
            ReactStyle.WIGGLE -> sin(bounce * PI * 4).toFloat() * 14f * bounce
            else -> 0f
        }
        translate(top = idleY + hop) {
            rotate(idleTurn + turn, bottom) {
                val squash = 1f + (if (motion.react == ReactStyle.HOP) bounce * 0.06f else 0f)
                scale(1f / squash, squash, bottom) {
                    val shown = if (happy && happyPicture != null) happyPicture else picture
                    when {
                        shown != null -> drawPicture(shown, Rect(Offset.Zero, size))
                        theme.mascot == BlotMascot.OCTOPUS -> drawOctopus(Rect(Offset.Zero, size), happy, phase)
                        theme.mascot == BlotMascot.MERMAID -> drawMermaid(Rect(Offset.Zero, size), colors, happy, phase)
                        else -> {}
                    }
                }
            }
        }
    }
}

private val OctoBody = Color(0xFF8E5CC7)
private val OctoShade = Color(0xFF6B3FA3)
private val OctoSpot = Color(0xFFB794E6)
private val Cheek = Color(0xFFF48FB1)
private val EyeInk = Color(0xFF2A1F3D)

/** A round purple octopus with curly arms that sway; it cheers when the grid is done. */
fun DrawScope.drawOctopus(area: Rect, happy: Boolean, phase: Float = 0f) {
    val s = area.minDimension
    val cx = area.center.x
    val headY = area.top + s * 0.38f
    val headR = s * 0.27f
    val armW = s * 0.075f
    val arms = listOf(-0.30f, -0.18f, -0.06f, 0.06f, 0.18f, 0.30f)
    arms.forEachIndexed { i, dx ->
        val side = if (dx < 0) -1f else 1f
        val lift = if (happy && (i == 0 || i == arms.lastIndex)) -0.3f else 0f
        val sway = sin((phase + i / 6f) * 2 * PI).toFloat() * s * 0.035f
        val root = Offset(cx + dx * s * 0.8f, headY + headR * 0.7f)
        val tip = Offset(cx + dx * s * 1.55f + side * s * 0.04f + sway, area.top + s * (0.9f + lift) - abs(sway) * 0.5f)
        val path = Path()
        path.moveTo(root.x, root.y)
        path.cubicTo(root.x + side * s * 0.02f, root.y + s * 0.18f, tip.x - side * s * 0.1f, tip.y - s * 0.05f, tip.x, tip.y)
        path.quadraticTo(tip.x + side * s * 0.06f, tip.y + s * 0.02f, tip.x + side * s * 0.04f, tip.y - s * 0.05f)
        drawPath(path, OctoShade, style = Stroke(armW, cap = StrokeCap.Round))
        drawPath(path, OctoBody, style = Stroke(armW * 0.72f, cap = StrokeCap.Round))
    }
    drawOval(OctoBody, Offset(cx - headR, headY - headR * 1.08f), Size(headR * 2, headR * 2.05f))
    drawOval(OctoSpot.copy(alpha = 0.45f), Offset(cx - headR * 0.55f, headY - headR * 0.95f), Size(headR * 0.7f, headR * 0.45f))
    drawCircle(OctoSpot, headR * 0.1f, Offset(cx + headR * 0.5f, headY - headR * 0.45f))
    drawCircle(OctoSpot, headR * 0.07f, Offset(cx + headR * 0.28f, headY - headR * 0.7f))
    val eyeY = headY + headR * 0.05f
    for (side in listOf(-1f, 1f)) {
        val e = Offset(cx + side * headR * 0.38f, eyeY)
        if (happy) {
            val arc = Path(); arc.moveTo(e.x - headR * 0.16f, e.y + headR * 0.04f)
            arc.quadraticTo(e.x, e.y - headR * 0.16f, e.x + headR * 0.16f, e.y + headR * 0.04f)
            drawPath(arc, EyeInk, style = Stroke(s * 0.025f, cap = StrokeCap.Round))
        } else {
            drawCircle(Color.White, headR * 0.2f, e)
            drawCircle(EyeInk, headR * 0.11f, Offset(e.x + headR * 0.03f, e.y + headR * 0.03f))
            drawCircle(Color.White, headR * 0.04f, Offset(e.x + headR * 0.07f, e.y - headR * 0.02f))
        }
        drawCircle(Cheek.copy(alpha = 0.7f), headR * 0.1f, Offset(cx + side * headR * 0.62f, eyeY + headR * 0.3f))
    }
    val smile = Path()
    smile.moveTo(cx - headR * 0.16f, eyeY + headR * 0.3f)
    smile.quadraticTo(cx, eyeY + headR * (if (happy) 0.58f else 0.45f), cx + headR * 0.16f, eyeY + headR * 0.3f)
    drawPath(smile, EyeInk, style = Stroke(s * 0.022f, cap = StrokeCap.Round))
}

private val Skin = Color(0xFFF1C7A4)
private val Hair = Color(0xFF2D3561)
private val HairShine = Color(0xFF4A5494)
private val Top = Color(0xFFF08A7E)
private val TailTop = Color(0xFF2BB3A5)
private val TailBottom = Color(0xFF2479B8)
private val Star = Color(0xFFFFD166)

/** A friendly cartoon mermaid waving from the waves, with a star in her hair; her tail swishes and bubbles rise. */
fun DrawScope.drawMermaid(area: Rect, c: BlotColors, happy: Boolean, phase: Float = 0f) {
    val s = area.minDimension
    val cx = area.center.x
    val top = area.top
    val tail = Path()
    tail.moveTo(cx - s * 0.13f, top + s * 0.55f)
    tail.cubicTo(cx - s * 0.16f, top + s * 0.78f, cx + s * 0.05f, top + s * 0.86f, cx + s * 0.22f, top + s * 0.8f)
    tail.lineTo(cx + s * 0.2f, top + s * 0.72f)
    tail.cubicTo(cx + s * 0.06f, top + s * 0.74f, cx - s * 0.0f, top + s * 0.66f, cx + s * 0.11f, top + s * 0.55f)
    tail.close()
    drawPath(tail, Brush.verticalGradient(listOf(TailTop, TailBottom), top + s * 0.55f, top + s * 0.85f))
    for ((x, y) in listOf(-0.07f to 0.64f, 0.0f to 0.7f, 0.08f to 0.75f, -0.03f to 0.58f)) {
        val arc = Path(); arc.moveTo(cx + s * (x - 0.03f), top + s * y)
        arc.quadraticTo(cx + s * x, top + s * (y + 0.035f), cx + s * (x + 0.03f), top + s * y)
        drawPath(arc, Color.White.copy(alpha = 0.45f), style = Stroke(s * 0.012f, cap = StrokeCap.Round))
    }
    val fin = Offset(cx + s * 0.18f, top + s * 0.76f)
    val swish = sin(phase * 2 * PI).toFloat() * 12f
    val finPath = Path()
    finPath.moveTo(fin.x, fin.y)
    finPath.cubicTo(fin.x + s * 0.08f, fin.y - s * 0.14f, fin.x + s * 0.2f, fin.y - s * 0.12f, fin.x + s * 0.2f, fin.y - s * 0.1f)
    finPath.cubicTo(fin.x + s * 0.14f, fin.y - s * 0.02f, fin.x + s * 0.2f, fin.y + s * 0.08f, fin.x + s * 0.22f, fin.y + s * 0.1f)
    finPath.cubicTo(fin.x + s * 0.12f, fin.y + s * 0.12f, fin.x + s * 0.05f, fin.y + s * 0.06f, fin.x, fin.y)
    rotate(swish, fin) { drawPath(finPath, Brush.linearGradient(listOf(TailTop, TailBottom), fin, Offset(fin.x + s * 0.2f, fin.y))) }
    drawRoundRect(Top, Offset(cx - s * 0.14f, top + s * 0.38f), Size(s * 0.28f, s * 0.2f), CornerRadius(s * 0.08f))
    val wave = (if (happy) -0.1f else 0f) + sin(phase * 4 * PI).toFloat() * 0.03f
    drawLine(Skin, Offset(cx + s * 0.12f, top + s * 0.42f), Offset(cx + s * 0.26f, top + s * (0.3f + wave)), s * 0.055f, cap = StrokeCap.Round)
    drawLine(Skin, Offset(cx - s * 0.12f, top + s * 0.42f), Offset(cx - s * 0.2f, top + s * 0.54f), s * 0.055f, cap = StrokeCap.Round)
    val headC = Offset(cx, top + s * 0.25f)
    val headR = s * 0.14f
    val hair = Path()
    hair.moveTo(headC.x - headR * 1.15f, headC.y)
    hair.cubicTo(headC.x - headR * 1.4f, headC.y + headR * 1.4f, headC.x - headR * 0.6f, headC.y + headR * 1.9f, headC.x - headR * 0.2f, headC.y + headR * 1.3f)
    hair.lineTo(headC.x + headR * 0.3f, headC.y + headR * 1.3f)
    hair.cubicTo(headC.x + headR * 0.8f, headC.y + headR * 1.9f, headC.x + headR * 1.45f, headC.y + headR * 1.3f, headC.x + headR * 1.15f, headC.y)
    hair.close()
    drawPath(hair, Hair)
    drawCircle(Skin, headR, Offset(headC.x, headC.y + headR * 0.08f))
    val cap = Path()
    cap.arcTo(Rect(headC.x - headR * 1.12f, headC.y - headR * 1.12f, headC.x + headR * 1.12f, headC.y + headR * 1.04f), 180f, 180f, true)
    cap.quadraticTo(headC.x + headR * 0.75f, headC.y - headR * 0.35f, headC.x + headR * 0.35f, headC.y - headR * 0.2f)
    cap.quadraticTo(headC.x + headR * 0.1f, headC.y - headR * 0.5f, headC.x - headR * 0.2f, headC.y - headR * 0.22f)
    cap.quadraticTo(headC.x - headR * 0.55f, headC.y - headR * 0.5f, headC.x - headR * 0.8f, headC.y - headR * 0.1f)
    cap.lineTo(headC.x - headR * 1.12f, headC.y - headR * 0.04f)
    cap.close()
    drawPath(cap, Hair)
    drawLine(HairShine, Offset(headC.x - headR * 0.6f, headC.y - headR * 0.9f), Offset(headC.x - headR * 0.1f, headC.y - headR * 1.05f), s * 0.018f, cap = StrokeCap.Round)
    val eyeY = headC.y + headR * 0.18f
    for (side in listOf(-1f, 1f)) {
        val e = Offset(headC.x + side * headR * 0.4f, eyeY)
        if (happy) {
            val arc = Path(); arc.moveTo(e.x - headR * 0.16f, e.y + headR * 0.04f)
            arc.quadraticTo(e.x, e.y - headR * 0.16f, e.x + headR * 0.16f, e.y + headR * 0.04f)
            drawPath(arc, EyeInk, style = Stroke(s * 0.016f, cap = StrokeCap.Round))
        } else {
            drawCircle(EyeInk, headR * 0.13f, e)
            drawCircle(Color.White, headR * 0.045f, Offset(e.x + headR * 0.04f, e.y - headR * 0.04f))
        }
        drawCircle(Cheek.copy(alpha = 0.6f), headR * 0.13f, Offset(headC.x + side * headR * 0.62f, eyeY + headR * 0.32f))
    }
    val smile = Path()
    smile.moveTo(headC.x - headR * 0.2f, eyeY + headR * 0.35f)
    smile.quadraticTo(headC.x, eyeY + headR * (if (happy) 0.7f else 0.55f), headC.x + headR * 0.2f, eyeY + headR * 0.35f)
    drawPath(smile, EyeInk, style = Stroke(s * 0.016f, cap = StrokeCap.Round))
    drawStar(Offset(headC.x + headR * 0.85f, headC.y - headR * 0.7f), headR * 0.32f, Star)
    val sea = Path()
    sea.moveTo(area.left, top + s * 0.86f)
    val steps = 24
    for (i in 1..steps) {
        val x = area.left + area.width * i / steps
        sea.lineTo(x, top + s * 0.86f + sin(i / steps.toFloat() * 4 * PI + phase * 2 * PI).toFloat() * s * 0.02f)
    }
    sea.lineTo(area.right, area.bottom); sea.lineTo(area.left, area.bottom); sea.close()
    drawPath(sea, c.mark.copy(alpha = 0.85f))
    for ((k, b) in listOf(Triple(-0.36f, 0.3f, 0.035f), Triple(-0.3f, 0.18f, 0.022f), Triple(0.36f, 0.5f, 0.03f), Triple(0.3f, 0.14f, 0.02f)).withIndex()) {
        val (x, y, r) = b
        val rise = ((phase + k * 0.25f) % 1f) * s * 0.12f
        drawCircle(c.detail, s * r, Offset(cx + s * x, top + s * y - rise), style = Stroke(s * 0.01f))
    }
}

private fun DrawScope.drawStar(center: Offset, r: Float, color: Color) {
    val path = Path()
    for (k in 0 until 10) {
        val a = -PI / 2 + k * PI / 5
        val rr = if (k % 2 == 0) r else r * 0.45f
        val p = Offset(center.x + (rr * cos(a)).toFloat(), center.y + (rr * sin(a)).toFloat())
        if (k == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}
