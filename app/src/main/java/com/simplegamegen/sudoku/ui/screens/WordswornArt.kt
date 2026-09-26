package com.simplegamegen.sudoku.ui.screens

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.simplegamegen.sudoku.wordplay.Monster
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------- Wordsworn art, drawn in code: the monsters, their dungeon, bites and splatter ----------------

private val Eye = Color(0xFF231A2E)
val Blood = Color(0xFFB3121E)
val InkSplat = Color(0xFF2A1F4A)

private fun DrawScope.eyes(cx: Float, cy: Float, gap: Float, r: Float, hurt: Boolean) {
    for (side in listOf(-1f, 1f)) {
        val c = Offset(cx + side * gap, cy)
        if (hurt) {
            // Squeezed shut: two little crosses.
            drawLine(Eye, Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r), r * 0.45f, cap = StrokeCap.Round)
            drawLine(Eye, Offset(c.x - r, c.y + r), Offset(c.x + r, c.y - r), r * 0.45f, cap = StrokeCap.Round)
        } else {
            drawCircle(Color.White, r * 1.3f, c)
            drawCircle(Eye, r * 0.75f, Offset(c.x + r * 0.15f, c.y + r * 0.15f))
            drawCircle(Color.White, r * 0.28f, Offset(c.x + r * 0.35f, c.y - r * 0.2f))
        }
    }
}

private fun DrawScope.mouth(cx: Float, cy: Float, w: Float, open: Boolean, color: Color = Eye) {
    val p = Path()
    p.moveTo(cx - w, cy)
    p.quadraticTo(cx, cy + w * (if (open) 1.1f else 0.6f), cx + w, cy)
    if (open) { p.close(); drawPath(p, color) } else drawPath(p, color, style = Stroke(w * 0.25f, cap = StrokeCap.Round))
}

/** A row of sharp teeth along a mouth line, for the monsters that bite. */
private fun DrawScope.fangs(cx: Float, cy: Float, w: Float, n: Int) {
    for (k in 0 until n) {
        val x = cx - w + (2 * w) * (k + 0.5f) / n
        val t = Path(); t.moveTo(x - w / n * 0.8f, cy); t.lineTo(x, cy + w * 0.35f); t.lineTo(x + w / n * 0.8f, cy); t.close()
        drawPath(t, Color.White)
    }
}

/** Draws [monster] in [area]; [hurt] (0..1) squeezes its eyes and tints it; [phase] (0..1) loops for idle motion. */
fun DrawScope.drawMonster(monster: Monster, area: Rect, hurt: Float, phase: Float) {
    val s = area.minDimension
    val cx = area.center.x
    val bob = sin(phase * 2 * PI).toFloat() * s * 0.02f
    val cy = area.center.y + bob
    val ouch = hurt > 0.3f
    when (monster) {
        Monster.TYPO_IMP -> {
            val body = Color(0xFFE8703A)
            val tail = Path(); tail.moveTo(cx + s * 0.18f, cy + s * 0.18f)
            tail.quadraticTo(cx + s * 0.42f, cy + s * 0.2f, cx + s * 0.36f, cy - s * 0.02f)
            drawPath(tail, body, style = Stroke(s * 0.04f, cap = StrokeCap.Round))
            drawCircle(Color(0xFFB0412A), s * 0.04f, Offset(cx + s * 0.36f, cy - s * 0.04f))
            for (side in listOf(-1f, 1f)) {
                val horn = Path(); horn.moveTo(cx + side * s * 0.12f, cy - s * 0.18f)
                horn.lineTo(cx + side * s * 0.2f, cy - s * 0.36f); horn.lineTo(cx + side * s * 0.2f, cy - s * 0.14f); horn.close()
                drawPath(horn, Color(0xFFFFD166))
            }
            drawCircle(body, s * 0.26f, Offset(cx, cy))
            eyes(cx, cy - s * 0.04f, s * 0.1f, s * 0.045f, ouch)
            mouth(cx, cy + s * 0.1f, s * 0.08f, true)
            fangs(cx, cy + s * 0.1f, s * 0.07f, 4)
        }
        Monster.DUST_BUNNY -> {
            val fur = Color(0xFFB8B2C4)
            for (side in listOf(-1f, 1f)) drawOval(fur, Offset(cx + side * s * 0.12f - s * 0.06f, cy - s * 0.42f), Size(s * 0.12f, s * 0.3f))
            for (k in 0 until 14) {
                val a = 2 * PI * k / 14
                drawCircle(fur, s * 0.07f, Offset(cx + (cos(a) * s * 0.24f).toFloat(), cy + (sin(a) * s * 0.24f).toFloat()))
            }
            drawCircle(fur, s * 0.25f, Offset(cx, cy))
            eyes(cx, cy - s * 0.03f, s * 0.09f, s * 0.045f, ouch)
            drawCircle(Color(0xFFF48FB1), s * 0.025f, Offset(cx, cy + s * 0.05f))
            // Two big rabbit teeth.
            drawRect(Color.White, Offset(cx - s * 0.035f, cy + s * 0.09f), Size(s * 0.03f, s * 0.05f))
            drawRect(Color.White, Offset(cx + s * 0.005f, cy + s * 0.09f), Size(s * 0.03f, s * 0.05f))
        }
        Monster.INKBLOT_SLIME -> {
            val ink = Color(0xFF2E3A8C)
            val p = Path()
            p.moveTo(cx - s * 0.32f, cy + s * 0.26f)
            p.cubicTo(cx - s * 0.36f, cy - s * 0.1f, cx - s * 0.2f, cy - s * 0.34f, cx, cy - s * 0.32f)
            p.cubicTo(cx + s * 0.2f, cy - s * 0.34f, cx + s * 0.36f, cy - s * 0.1f, cx + s * 0.32f, cy + s * 0.26f)
            p.close()
            drawPath(p, ink)
            for (k in -2..2) drawOval(ink, Offset(cx + k * s * 0.12f - s * 0.04f, cy + s * 0.2f), Size(s * 0.08f, s * (0.1f + 0.04f * ((k + 5) % 3))))
            drawOval(Color.White.copy(alpha = 0.25f), Offset(cx - s * 0.2f, cy - s * 0.24f), Size(s * 0.14f, s * 0.08f))
            eyes(cx, cy - s * 0.04f, s * 0.11f, s * 0.05f, ouch)
            mouth(cx, cy + s * 0.1f, s * 0.1f, true, Color(0xFF12163A))
            fangs(cx, cy + s * 0.1f, s * 0.09f, 5)
        }
        Monster.SPELLING_BEE -> {
            val flap = sin(phase * 8 * PI).toFloat() * s * 0.03f
            for (side in listOf(-1f, 1f)) drawOval(Color(0xFFD7F0FF).copy(alpha = 0.85f), Offset(cx + side * s * 0.12f - s * 0.1f, cy - s * 0.34f - flap), Size(s * 0.2f, s * 0.24f))
            drawOval(Color(0xFFFFC93C), Offset(cx - s * 0.3f, cy - s * 0.2f), Size(s * 0.6f, s * 0.42f))
            for (k in 0..1) drawRect(Color(0xFF3A2E1E), Offset(cx - s * 0.06f + k * s * 0.14f, cy - s * 0.2f), Size(s * 0.06f, s * 0.42f))
            val sting = Path(); sting.moveTo(cx + s * 0.29f, cy - s * 0.03f); sting.lineTo(cx + s * 0.44f, cy + s * 0.01f); sting.lineTo(cx + s * 0.29f, cy + s * 0.05f); sting.close()
            drawPath(sting, Color(0xFF3A2E1E))
            eyes(cx - s * 0.16f, cy - s * 0.04f, s * 0.07f, s * 0.04f, ouch)
            mouth(cx - s * 0.16f, cy + s * 0.08f, s * 0.05f, ouch)
            for (side in listOf(-1f, 1f)) drawLine(Color(0xFF3A2E1E), Offset(cx - s * 0.16f + side * s * 0.05f, cy - s * 0.18f),
                Offset(cx - s * 0.16f + side * s * 0.1f, cy - s * 0.32f), s * 0.015f, cap = StrokeCap.Round)
        }
        Monster.GRAMMAR_GREMLIN -> {
            val skin = Color(0xFF5DAE5A)
            for (side in listOf(-1f, 1f)) {
                val ear = Path(); ear.moveTo(cx + side * s * 0.18f, cy - s * 0.1f)
                ear.lineTo(cx + side * s * 0.44f, cy - s * 0.24f); ear.lineTo(cx + side * s * 0.22f, cy + s * 0.04f); ear.close()
                drawPath(ear, skin)
            }
            drawCircle(skin, s * 0.24f, Offset(cx, cy))
            eyes(cx, cy - s * 0.04f, s * 0.09f, s * 0.045f, ouch)
            for (side in listOf(-1f, 1f)) drawCircle(Color(0xFF3A3A3A), s * 0.075f, Offset(cx + side * s * 0.09f, cy - s * 0.04f), style = Stroke(s * 0.015f))
            drawLine(Color(0xFF3A3A3A), Offset(cx - s * 0.015f, cy - s * 0.04f), Offset(cx + s * 0.015f, cy - s * 0.04f), s * 0.015f)
            mouth(cx, cy + s * 0.12f, s * 0.1f, true)
            fangs(cx, cy + s * 0.12f, s * 0.09f, 6)
        }
        Monster.BOOKWORM -> {
            drawRoundRect(Color(0xFF8B5A34), Offset(cx - s * 0.36f, cy + s * 0.12f), Size(s * 0.72f, s * 0.2f), CornerRadius(s * 0.03f))
            drawRoundRect(Color(0xFFFFF6E0), Offset(cx - s * 0.33f, cy + s * 0.1f), Size(s * 0.32f, s * 0.16f), CornerRadius(s * 0.02f))
            drawRoundRect(Color(0xFFFFF6E0), Offset(cx + s * 0.01f, cy + s * 0.1f), Size(s * 0.32f, s * 0.16f), CornerRadius(s * 0.02f))
            val worm = Color(0xFFE88BB0)
            val wiggle = sin(phase * 2 * PI).toFloat() * s * 0.03f
            for (k in 0..3) drawCircle(worm, s * (0.1f - k * 0.008f), Offset(cx + wiggle * (k % 2 * 2 - 1), cy + s * 0.08f - k * s * 0.1f))
            eyes(cx, cy - s * 0.24f, s * 0.05f, s * 0.035f, ouch)
            mouth(cx, cy - s * 0.17f, s * 0.05f, true)
            fangs(cx, cy - s * 0.17f, s * 0.045f, 3)
        }
        Monster.PAPER_DRAGON -> {
            val red = Color(0xFFD64545)
            val paper = Color(0xFFFFF6E0)
            val wing = Path(); wing.moveTo(cx, cy - s * 0.05f); wing.lineTo(cx + s * 0.4f, cy - s * 0.35f); wing.lineTo(cx + s * 0.28f, cy + s * 0.05f); wing.close()
            drawPath(wing, paper); drawPath(wing, red, style = Stroke(s * 0.012f))
            val body = Path(); body.moveTo(cx - s * 0.3f, cy + s * 0.28f); body.lineTo(cx + s * 0.3f, cy + s * 0.28f); body.lineTo(cx, cy - s * 0.1f); body.close()
            drawPath(body, red)
            val head = Path(); head.moveTo(cx - s * 0.1f, cy - s * 0.02f); head.lineTo(cx - s * 0.36f, cy - s * 0.16f); head.lineTo(cx - s * 0.06f, cy - s * 0.3f); head.close()
            drawPath(head, red)
            fangs(cx - s * 0.26f, cy - s * 0.14f, s * 0.06f, 3)
            eyes(cx - s * 0.18f, cy - s * 0.2f, s * 0.0f, s * 0.04f, ouch)
            drawLine(Color(0xFFFFD166), Offset(cx - s * 0.06f, cy - s * 0.3f), Offset(cx + s * 0.02f, cy - s * 0.4f), s * 0.02f, cap = StrokeCap.Round)
        }
        Monster.WORD_EATER -> {
            val purple = Color(0xFF6B3FA3)
            drawCircle(purple, s * 0.34f, Offset(cx, cy))
            val chomp = (sin(phase * 4 * PI).toFloat() + 1f) / 2f
            val mouthRect = Rect(cx - s * 0.22f, cy + s * 0.02f, cx + s * 0.22f, cy + s * (0.12f + 0.12f * chomp))
            drawRoundRect(Color(0xFF2A123D), mouthRect.topLeft, mouthRect.size, CornerRadius(s * 0.08f))
            fangs(cx, mouthRect.top, s * 0.2f, 6)
            eyes(cx, cy - s * 0.12f, s * 0.13f, s * 0.06f, ouch)
            for (side in listOf(-1f, 1f)) drawCircle(purple, s * 0.07f, Offset(cx + side * s * 0.26f, cy - s * 0.3f))
            val fall = phase * s * 0.3f
            for (k in 0..2) drawCircle(Color(0xFFFFD166).copy(alpha = 0.8f), s * 0.025f, Offset(cx - s * 0.1f + k * s * 0.1f, cy - s * 0.45f + (fall + k * s * 0.1f) % (s * 0.3f)))
        }
    }
    if (hurt > 0f) drawCircle(Color(0xFFFF5252).copy(alpha = 0.25f * hurt), s * 0.4f, Offset(cx, cy))
}

/** For the home-screen art: a still monster. */
object MonsterArt {
    fun DrawScope.drawMonsterAt(monster: Monster, area: Rect) = drawMonster(monster, area, 0f, 0f)
}

// ---------------- The dungeon ----------------

/** A dim stone dungeon: brick wall, a flagstone floor and two torches that flicker with [phase]. */
fun DrawScope.drawDungeon(phase: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2B2530), Color(0xFF1A161E)), 0f, h))
    // Bricks, offset every other row.
    val bh = h / 7f; val bw = w / 6f
    for (row in 0 until 6) for (col in -1..6) {
        val x = col * bw + (if (row % 2 == 1) bw / 2 else 0f)
        drawRoundRect(Color(0xFF3A3340).copy(alpha = 0.85f - row * 0.06f), Offset(x + 2f, row * bh + 2f), Size(bw - 4f, bh - 4f), CornerRadius(4f))
    }
    // The floor.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3B3129), Color(0xFF241D18)), h * 0.78f, h), Offset(0f, h * 0.78f), Size(w, h * 0.22f))
    // Torches: a bracket, a flame that flickers, and a warm pool of light.
    for (x in listOf(w * 0.1f, w * 0.9f)) {
        val flick = 1f + 0.12f * sin(phase * 2 * PI * 5 + x).toFloat() + 0.06f * sin(phase * 2 * PI * 13 + x).toFloat()
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFB347), Color(0x00FFB347)), Offset(x, h * 0.3f), w * 0.3f * flick), w * 0.3f * flick, Offset(x, h * 0.3f))
        drawRect(Color(0xFF5A4636), Offset(x - 4f, h * 0.32f), Size(8f, h * 0.14f))
        val flame = Path()
        flame.moveTo(x - 9f * flick, h * 0.32f)
        flame.quadraticTo(x - 6f, h * 0.24f, x, h * 0.32f - 28f * flick)
        flame.quadraticTo(x + 6f, h * 0.24f, x + 9f * flick, h * 0.32f)
        flame.close()
        drawPath(flame, Color(0xFFFF9F1C))
        drawCircle(Color(0xFFFFE08A), 5f * flick, Offset(x, h * 0.3f))
    }
    // Shadows gathering in the corners.
    drawRect(Brush.radialGradient(listOf(Color(0x00000000), Color(0x99000000)), Offset(w / 2, h * 0.5f), maxOf(w, h) * 0.75f))
}

// ---------------- Splatter and bites ----------------

/** A splat left where a hit landed: a round drop with a few flecks. [fade] runs 1 (fresh) to 0 (gone). */
fun DrawScope.drawSplat(at: Offset, r: Float, seed: Int, color: Color, fade: Float) {
    if (fade <= 0f) return
    val rnd = Random(seed)
    val c = color.copy(alpha = 0.85f * fade)
    val blob = Path()
    val n = 10
    for (k in 0..n) {
        val a = 2 * PI * k / n
        val rr = r * (0.7f + 0.4f * rnd.nextFloat())
        val p = Offset(at.x + (cos(a) * rr).toFloat(), at.y + (sin(a) * rr * 0.6f).toFloat())
        if (k == 0) blob.moveTo(p.x, p.y) else blob.lineTo(p.x, p.y)
    }
    blob.close()
    drawPath(blob, c)
    repeat(5) {
        val a = rnd.nextDouble(0.0, 2 * PI)
        val d = r * (1.3f + rnd.nextFloat())
        drawCircle(c, r * (0.1f + 0.12f * rnd.nextFloat()), Offset(at.x + (cos(a) * d).toFloat(), at.y + (sin(a) * d * 0.6f).toFloat()))
    }
}

/** Droplets flying out from [at] as [t] runs 0..1, falling under gravity. */
fun DrawScope.drawDroplets(at: Offset, t: Float, seed: Int, color: Color, spread: Float) {
    if (t <= 0f || t >= 1f) return
    val rnd = Random(seed)
    repeat(16) {
        val a = rnd.nextDouble(-PI, 0.0)
        val speed = spread * (0.4f + 0.6f * rnd.nextFloat())
        val p = Offset(at.x + (cos(a) * speed * t).toFloat(), at.y + (sin(a) * speed * t).toFloat() + spread * 1.2f * t * t)
        drawCircle(color.copy(alpha = 1f - t * 0.6f), spread * (0.025f + 0.02f * rnd.nextFloat()), p)
    }
}

/** A bite across the whole area: upper and lower rows of teeth snap shut as [t] goes 0..0.5 and part again after. */
fun DrawScope.drawBite(t: Float, gums: Color) {
    if (t <= 0f || t >= 1f) return
    val close = if (t < 0.5f) t / 0.5f else (1f - t) / 0.5f
    val w = size.width; val h = size.height
    val reach = h * 0.42f * close
    val n = 9
    for (k in 0 until n) {
        val x0 = w * k / n; val x1 = w * (k + 1) / n
        val top = Path(); top.moveTo(x0, 0f); top.lineTo(x1, 0f); top.lineTo((x0 + x1) / 2, reach + h * 0.08f); top.close()
        val bottom = Path(); bottom.moveTo(x0, h); bottom.lineTo(x1, h); bottom.lineTo((x0 + x1) / 2, h - reach - h * 0.08f); bottom.close()
        drawPath(top, Color.White.copy(alpha = 0.95f)); drawPath(top, Color(0xFFBDB6A8), style = Stroke(2f))
        drawPath(bottom, Color.White.copy(alpha = 0.95f)); drawPath(bottom, Color(0xFFBDB6A8), style = Stroke(2f))
    }
    // Gums above and below the teeth.
    drawRect(gums.copy(alpha = 0.9f), Offset.Zero, Size(w, reach * 0.25f))
    drawRect(gums.copy(alpha = 0.9f), Offset(0f, h - reach * 0.25f), Size(w, reach * 0.25f))
}
