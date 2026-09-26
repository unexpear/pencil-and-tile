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
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import com.simplegamegen.sudoku.wordplay.Monster
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------- Wordsworn art, drawn in code: the monsters, their dungeon, bites and splatter ----------------

private val Eye = Color(0xFF231A2E)
val Blood = Color(0xFFB3121E)
val InkSplat = Color(0xFF2A1F4A)

/** Set while drawing a monster: true for the blink at the end of each idle loop. */
private var blinking = false

private fun DrawScope.eyes(cx: Float, cy: Float, gap: Float, r: Float, hurt: Boolean) {
    for (side in listOf(-1f, 1f)) {
        val c = Offset(cx + side * gap, cy)
        if (blinking && !hurt) {
            // Shut for a moment: a curved lid line.
            val lid = Path(); lid.moveTo(c.x - r * 1.2f, c.y); lid.quadraticTo(c.x, c.y + r * 0.8f, c.x + r * 1.2f, c.y)
            drawPath(lid, Eye, style = Stroke(r * 0.4f, cap = StrokeCap.Round))
        } else if (hurt) {
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

/** Ink outline shared by every monster, so they read like drawings in a storybook. */
private val Outline = Color(0xFF1E1526)

/** A round body with a soft highlight up top, shading round the rim and an ink outline. */
private fun DrawScope.shadedCircle(color: Color, r: Float, c: Offset) {
    drawCircle(color, r, c)
    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.3f)),
        c + Offset(-r * 0.2f, -r * 0.25f), r * 1.3f), r, c)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.4f), Color.Transparent), c + Offset(-r * 0.35f, -r * 0.42f), r * 0.6f), r, c)
    drawCircle(Outline, r, c, style = Stroke(r * 0.06f))
}

/** Fills [path] in [color] with the same highlight, shading and outline as [shadedCircle]. */
private fun DrawScope.shadedPath(path: Path, color: Color, light: Offset, r: Float) {
    drawPath(path, color)
    drawPath(path, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent, Color.Black.copy(alpha = 0.25f)), light, r * 1.6f))
    drawPath(path, Outline, style = Stroke(r * 0.05f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/**
 * Draws [monster] in [area]; [hurt] (0..1) squeezes its eyes and tints it; [phase] (0..1) loops for idle motion.
 * [angry] is its second stage: a red aura pulses behind it and a cross-shaped vein throbs on its head.
 */
fun DrawScope.drawMonster(monster: Monster, area: Rect, hurt: Float, phase: Float, angry: Boolean = false) {
    val s = area.minDimension
    val cx = area.center.x
    val wave = sin(phase * 2 * PI).toFloat()
    val bob = wave * s * 0.02f
    val cy = area.center.y + bob
    val ouch = hurt > 0.3f
    blinking = phase in 0.9f..0.95f
    // A shadow on the floor that shrinks as the monster bobs up.
    val shadowW = s * (0.56f - wave * 0.03f)
    drawOval(Color.Black.copy(alpha = 0.35f), Offset(cx - shadowW / 2, area.center.y + s * 0.25f), Size(shadowW, s * 0.09f))
    if (angry) {
        val pulse = 0.5f + 0.5f * sin(phase * 4 * PI).toFloat()
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF3B30).copy(alpha = 0.45f + 0.2f * pulse), Color.Transparent), Offset(cx, cy), s * 0.5f), s * 0.5f, Offset(cx, cy))
    }
    // Breathing: a gentle squash and stretch from the feet up.
    scale(1f - wave * 0.015f, 1f + wave * 0.025f, Offset(cx, area.center.y + s * 0.35f)) {
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
                drawPath(horn, Color(0xFFFFD166)); drawPath(horn, Outline, style = Stroke(s * 0.012f))
            }
            shadedCircle(body, s * 0.26f, Offset(cx, cy))
            for (side in listOf(-1f, 1f)) drawCircle(Color(0xFFFF9E8A).copy(alpha = 0.6f), s * 0.035f, Offset(cx + side * s * 0.16f, cy + s * 0.05f))
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
            shadedCircle(fur, s * 0.25f, Offset(cx, cy))
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
            for (k in -2..2) drawOval(ink, Offset(cx + k * s * 0.12f - s * 0.04f, cy + s * 0.2f), Size(s * 0.08f, s * (0.1f + 0.04f * ((k + 5) % 3))))
            shadedPath(p, ink, Offset(cx - s * 0.12f, cy - s * 0.18f), s * 0.34f)
            drawOval(Color.White.copy(alpha = 0.25f), Offset(cx - s * 0.2f, cy - s * 0.24f), Size(s * 0.14f, s * 0.08f))
            eyes(cx, cy - s * 0.04f, s * 0.11f, s * 0.05f, ouch)
            mouth(cx, cy + s * 0.1f, s * 0.1f, true, Color(0xFF12163A))
            fangs(cx, cy + s * 0.1f, s * 0.09f, 5)
        }
        Monster.SPELLING_BEE -> {
            val flap = sin(phase * 8 * PI).toFloat() * s * 0.03f
            for (side in listOf(-1f, 1f)) drawOval(Color(0xFFD7F0FF).copy(alpha = 0.85f), Offset(cx + side * s * 0.12f - s * 0.1f, cy - s * 0.34f - flap), Size(s * 0.2f, s * 0.24f))
            val beeBody = Path().apply { addOval(Rect(cx - s * 0.3f, cy - s * 0.2f, cx + s * 0.3f, cy + s * 0.22f)) }
            drawPath(beeBody, Color(0xFFFFC93C))
            for (k in 0..1) drawRect(Color(0xFF3A2E1E), Offset(cx - s * 0.06f + k * s * 0.14f, cy - s * 0.19f), Size(s * 0.06f, s * 0.4f))
            drawPath(beeBody, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent, Color.Black.copy(alpha = 0.22f)),
                Offset(cx - s * 0.12f, cy - s * 0.1f), s * 0.45f))
            drawPath(beeBody, Outline, style = Stroke(s * 0.015f))
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
                drawPath(ear, skin); drawPath(ear, Outline, style = Stroke(s * 0.012f))
            }
            shadedCircle(skin, s * 0.24f, Offset(cx, cy))
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
            for (k in 0..3) shadedCircle(worm, s * (0.1f - k * 0.008f), Offset(cx + wiggle * (k % 2 * 2 - 1), cy + s * 0.08f - k * s * 0.1f))
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
            shadedPath(body, red, Offset(cx - s * 0.05f, cy), s * 0.3f)
            val head = Path(); head.moveTo(cx - s * 0.1f, cy - s * 0.02f); head.lineTo(cx - s * 0.36f, cy - s * 0.16f); head.lineTo(cx - s * 0.06f, cy - s * 0.3f); head.close()
            shadedPath(head, red, Offset(cx - s * 0.2f, cy - s * 0.2f), s * 0.2f)
            fangs(cx - s * 0.26f, cy - s * 0.14f, s * 0.06f, 3)
            eyes(cx - s * 0.18f, cy - s * 0.2f, s * 0.0f, s * 0.04f, ouch)
            drawLine(Color(0xFFFFD166), Offset(cx - s * 0.06f, cy - s * 0.3f), Offset(cx + s * 0.02f, cy - s * 0.4f), s * 0.02f, cap = StrokeCap.Round)
        }
        Monster.PAGE_MITE -> {
            // A torn corner of a page that scuttles on six legs, with a line of text across its back.
            val paper = Color(0xFFF4ECD8)
            for (k in 0..2) for (side in listOf(-1f, 1f)) {
                val step = sin(phase * 8 * PI + k).toFloat() * s * 0.02f
                val y = cy + s * (0.02f + k * 0.08f)
                drawLine(Outline, Offset(cx + side * s * 0.16f, y), Offset(cx + side * s * 0.32f, y + s * 0.08f + step), s * 0.018f, cap = StrokeCap.Round)
            }
            val page = Path(); page.moveTo(cx - s * 0.26f, cy + s * 0.2f); page.lineTo(cx - s * 0.2f, cy - s * 0.16f)
            page.lineTo(cx + s * 0.1f, cy - s * 0.2f); page.lineTo(cx + s * 0.26f, cy - s * 0.04f); page.lineTo(cx + s * 0.22f, cy + s * 0.22f); page.close()
            shadedPath(page, paper, Offset(cx - s * 0.1f, cy - s * 0.1f), s * 0.28f)
            // A folded corner and a few lines of print.
            val fold = Path(); fold.moveTo(cx + s * 0.1f, cy - s * 0.2f); fold.lineTo(cx + s * 0.12f, cy - s * 0.06f); fold.lineTo(cx + s * 0.26f, cy - s * 0.04f); fold.close()
            drawPath(fold, Color(0xFFD9CBB0)); drawPath(fold, Outline, style = Stroke(s * 0.01f))
            for (k in 0..3) drawLine(Color(0xFF8A7A62), Offset(cx - s * 0.16f, cy + s * (0.08f + k * 0.03f)), Offset(cx + s * (0.12f - k * 0.03f), cy + s * (0.08f + k * 0.03f)), s * 0.008f)
            for (side in listOf(-1f, 1f)) drawLine(Outline, Offset(cx + side * s * 0.05f, cy - s * 0.17f), Offset(cx + side * s * 0.12f, cy - s * 0.32f), s * 0.012f, cap = StrokeCap.Round)
            eyes(cx - s * 0.02f, cy - s * 0.05f, s * 0.07f, s * 0.04f, ouch)
            mouth(cx - s * 0.02f, cy + s * 0.03f, s * 0.05f, true)
            fangs(cx - s * 0.02f, cy + s * 0.03f, s * 0.045f, 2)
        }
        Monster.GLOOM_MOTH -> {
            // A dusky moth with eye-spotted wings that beat slowly, and feathery antennae.
            val flap = (0.8f + 0.2f * sin(phase * 6 * PI).toFloat())
            val wing = Color(0xFF6E5A7E)
            for (side in listOf(-1f, 1f)) {
                val w = Path(); w.moveTo(cx, cy - s * 0.04f)
                w.cubicTo(cx + side * s * 0.46f * flap, cy - s * 0.42f, cx + side * s * 0.52f * flap, cy + s * 0.06f, cx + side * s * 0.1f, cy + s * 0.08f)
                w.cubicTo(cx + side * s * 0.38f * flap, cy + s * 0.14f, cx + side * s * 0.3f * flap, cy + s * 0.34f, cx, cy + s * 0.12f)
                w.close()
                shadedPath(w, wing, Offset(cx + side * s * 0.2f, cy - s * 0.16f), s * 0.3f)
                val spot = Offset(cx + side * s * 0.26f * flap, cy - s * 0.12f)
                drawCircle(Color(0xFFFFD166), s * 0.06f, spot); drawCircle(Outline, s * 0.03f, spot); drawCircle(Color.White, s * 0.01f, spot + Offset(-s * 0.01f, -s * 0.01f))
            }
            drawOval(Color(0xFF4A3B57), Offset(cx - s * 0.07f, cy - s * 0.12f), Size(s * 0.14f, s * 0.36f))
            drawOval(Outline, Offset(cx - s * 0.07f, cy - s * 0.12f), Size(s * 0.14f, s * 0.36f), style = Stroke(s * 0.01f))
            for (side in listOf(-1f, 1f)) {
                val tip = Offset(cx + side * s * 0.14f, cy - s * 0.34f)
                drawLine(Outline, Offset(cx + side * s * 0.03f, cy - s * 0.12f), tip, s * 0.012f)
                for (k in 1..4) { val t = k / 5f; val at = Offset(cx + side * s * (0.03f + 0.11f * t), cy - s * (0.12f + 0.22f * t))
                    drawLine(Outline, at, at + Offset(side * s * 0.03f, s * 0.02f), s * 0.008f) }
            }
            eyes(cx, cy - s * 0.04f, s * 0.045f, s * 0.035f, ouch)
            mouth(cx, cy + s * 0.04f, s * 0.035f, ouch)
        }
        Monster.QUILL_WRAITH -> {
            // A pale sheet of a ghost with a wavy hem, holding a dripping quill.
            val ghost = Color(0xFFD7EEF0)
            val drift = sin(phase * 2 * PI).toFloat() * s * 0.02f
            val p = Path(); p.moveTo(cx - s * 0.24f, cy + s * 0.24f)
            p.cubicTo(cx - s * 0.3f, cy - s * 0.1f, cx - s * 0.2f, cy - s * 0.36f, cx, cy - s * 0.36f)
            p.cubicTo(cx + s * 0.2f, cy - s * 0.36f, cx + s * 0.3f, cy - s * 0.1f, cx + s * 0.24f, cy + s * 0.24f)
            for (k in 0..3) { val x0 = cx + s * 0.24f - k * s * 0.12f
                p.quadraticTo(x0 - s * 0.03f, cy + s * 0.32f + drift * (if (k % 2 == 0) 1 else -1), x0 - s * 0.12f, cy + s * 0.24f) }
            p.close()
            shadedPath(p, ghost.copy(alpha = 0.92f), Offset(cx - s * 0.1f, cy - s * 0.2f), s * 0.3f)
            // The quill, with ink dripping from its nib.
            val qx = cx + s * 0.28f; val qy = cy - s * 0.04f
            val feather = Path(); feather.moveTo(qx, qy + s * 0.12f); feather.quadraticTo(qx + s * 0.12f, qy - s * 0.12f, qx + s * 0.06f, qy - s * 0.3f)
            feather.quadraticTo(qx - s * 0.04f, qy - s * 0.1f, qx, qy + s * 0.12f); feather.close()
            drawPath(feather, Color(0xFFFFF6E0)); drawPath(feather, Outline, style = Stroke(s * 0.01f))
            drawLine(Outline, Offset(qx, qy + s * 0.12f), Offset(qx + s * 0.05f, qy - s * 0.26f), s * 0.01f)
            val drip = (phase * 3f) % 1f
            drawCircle(Color(0xFF2A1F4A), s * 0.018f, Offset(qx, qy + s * (0.15f + 0.15f * drip)))
            eyes(cx, cy - s * 0.12f, s * 0.08f, s * 0.045f, ouch)
            drawOval(Color(0xFF2A1F4A), Offset(cx - s * 0.05f, cy - s * 0.02f), Size(s * 0.1f, s * (if (ouch) 0.05f else 0.09f)))
        }
        Monster.RUST_GOLEM -> {
            // Stacked rusty blocks with rivets, a moss tuft on top and glowing eyes.
            val rust = Color(0xFFB5652E)
            val dark = Color(0xFF7A3F1C)
            for (side in listOf(-1f, 1f)) {
                val arm = Rect(cx + side * s * 0.3f - s * 0.07f, cy - s * 0.06f, cx + side * s * 0.3f + s * 0.07f, cy + s * 0.22f)
                drawRoundRect(dark, arm.topLeft, arm.size, CornerRadius(s * 0.03f)); drawRoundRect(Outline, arm.topLeft, arm.size, CornerRadius(s * 0.03f), style = Stroke(s * 0.012f))
            }
            val body = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(cx - s * 0.24f, cy - s * 0.1f, cx + s * 0.24f, cy + s * 0.26f, CornerRadius(s * 0.04f))) }
            shadedPath(body, rust, Offset(cx - s * 0.1f, cy - s * 0.02f), s * 0.3f)
            val head = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(cx - s * 0.16f, cy - s * 0.36f, cx + s * 0.16f, cy - s * 0.1f, CornerRadius(s * 0.04f))) }
            shadedPath(head, rust, Offset(cx - s * 0.06f, cy - s * 0.3f), s * 0.2f)
            for (x in listOf(-0.19f, 0.19f)) for (y in listOf(-0.05f, 0.21f)) drawCircle(dark, s * 0.015f, Offset(cx + x * s, cy + y * s))
            for (k in 0..4) drawCircle(Color(0xFF6B8E3A), s * (0.03f + 0.01f * (k % 2)), Offset(cx - s * 0.08f + k * s * 0.04f, cy - s * 0.37f))
            val glow = if (ouch) Color(0xFFFFFFFF) else Color(0xFFFFD166)
            for (side in listOf(-1f, 1f)) {
                drawCircle(glow.copy(alpha = 0.35f), s * 0.05f, Offset(cx + side * s * 0.07f, cy - s * 0.24f))
                drawRect(glow, Offset(cx + side * s * 0.07f - s * 0.035f, cy - s * 0.255f), Size(s * 0.07f, s * 0.03f))
            }
            drawLine(dark, Offset(cx - s * 0.08f, cy - s * 0.15f), Offset(cx + s * 0.08f, cy - s * 0.15f), s * 0.02f, cap = StrokeCap.Round)
        }
        Monster.WORD_EATER -> {
            val purple = Color(0xFF6B3FA3)
            for (side in listOf(-1f, 1f)) shadedCircle(purple, s * 0.07f, Offset(cx + side * s * 0.26f, cy - s * 0.3f))
            shadedCircle(purple, s * 0.34f, Offset(cx, cy))
            val chomp = (sin(phase * 4 * PI).toFloat() + 1f) / 2f
            val mouthRect = Rect(cx - s * 0.22f, cy + s * 0.02f, cx + s * 0.22f, cy + s * (0.12f + 0.12f * chomp))
            drawRoundRect(Color(0xFF2A123D), mouthRect.topLeft, mouthRect.size, CornerRadius(s * 0.08f))
            fangs(cx, mouthRect.top, s * 0.2f, 6)
            eyes(cx, cy - s * 0.12f, s * 0.13f, s * 0.06f, ouch)
            val fall = phase * s * 0.3f
            for (k in 0..2) drawCircle(Color(0xFFFFD166).copy(alpha = 0.8f), s * 0.025f, Offset(cx - s * 0.1f + k * s * 0.1f, cy - s * 0.45f + (fall + k * s * 0.1f) % (s * 0.3f)))
        }
    }
    }
    blinking = false
    if (angry) {
        // A throbbing cross-shaped vein, up and to the side of its head.
        val v = Offset(cx + s * 0.2f, cy - s * 0.3f)
        val beat = 1f + 0.15f * sin(phase * 8 * PI).toFloat()
        for (k in 0..3) {
            val a = PI / 4 + k * PI / 2
            val d = Offset((cos(a) * s * 0.035f * beat).toFloat(), (sin(a) * s * 0.035f * beat).toFloat())
            drawLine(Color(0xFFE5484D), v + d * 0.4f, v + d * 1.6f, s * 0.02f, cap = StrokeCap.Round)
        }
    }
    if (hurt > 0f) drawCircle(Color(0xFFFF5252).copy(alpha = 0.25f * hurt), s * 0.4f, Offset(cx, cy))
}

/** For the home-screen art: a still monster. */
object MonsterArt {
    fun DrawScope.drawMonsterAt(monster: Monster, area: Rect) = drawMonster(monster, area, 0f, 0f)
}

// ---------------- The dungeon ----------------

/** A dim stone dungeon: bevelled bricks with moss, flagstones in perspective, two flickering torches and dust in their light. */
fun DrawScope.drawDungeon(phase: Float) {
    val w = size.width; val h = size.height
    val floorTop = h * 0.74f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2E2733), Color(0xFF1A161E)), 0f, floorTop), size = Size(w, floorTop))
    // Bricks, offset every other row, each a slightly different stone with a lit top and a shadowed base.
    val rows = 6; val bh = floorTop / rows; val bw = w / 5.5f
    val rnd = Random(7)
    for (row in 0 until rows) for (col in -1..6) {
        val x = col * bw + (if (row % 2 == 1) bw / 2 else 0f)
        val tone = 0.85f + 0.3f * rnd.nextFloat()
        val base = Color(0xFF3F3845)
        val stone = Color(base.red * tone, base.green * tone, base.blue * tone).copy(alpha = 0.95f - row * 0.05f)
        val topLeft = Offset(x + 2f, row * bh + 2f); val sz = Size(bw - 4f, bh - 4f)
        drawRoundRect(stone, topLeft, sz, CornerRadius(5f))
        drawLine(Color.White.copy(alpha = 0.07f), topLeft + Offset(4f, 2f), topLeft + Offset(sz.width - 4f, 2f), 3f)
        drawLine(Color.Black.copy(alpha = 0.3f), topLeft + Offset(4f, sz.height - 1f), topLeft + Offset(sz.width - 4f, sz.height - 1f), 3f)
        // Moss creeping over the lower bricks.
        if (row >= rows - 2 && rnd.nextFloat() < 0.45f) repeat(4) {
            drawCircle(Color(0xFF4F6B3A).copy(alpha = 0.5f), bh * (0.06f + 0.05f * rnd.nextFloat()),
                topLeft + Offset(sz.width * rnd.nextFloat(), sz.height * (0.1f + 0.3f * rnd.nextFloat())))
        }
    }
    // Flagstones: rows grow toward you, seams run to a point in the middle of the wall.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF41362D), Color(0xFF231C17)), floorTop, h), Offset(0f, floorTop), Size(w, h - floorTop))
    val seam = Color.Black.copy(alpha = 0.35f)
    var y = floorTop; var step = (h - floorTop) * 0.18f
    while (y < h) { drawLine(seam, Offset(0f, y), Offset(w, y), 2f); y += step; step *= 1.45f }
    for (k in -4..4) drawLine(seam, Offset(w / 2 + k * w * 0.06f, floorTop), Offset(w / 2 + k * w * 0.32f, h), 2f)
    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, floorTop + 1f), Offset(w, floorTop + 1f), 2f)
    // Torches: an iron bracket, a layered flame that flickers, a warm pool of light and dust drifting through it.
    for (x in listOf(w * 0.1f, w * 0.9f)) {
        val flick = 1f + 0.12f * sin(phase * 2 * PI * 5 + x).toFloat() + 0.06f * sin(phase * 2 * PI * 13 + x).toFloat()
        val fy = h * 0.5f
        drawCircle(Brush.radialGradient(listOf(Color(0x77FFB347), Color(0x00FFB347)), Offset(x, fy), w * 0.32f * flick), w * 0.32f * flick, Offset(x, fy))
        drawRect(Color(0xFF2B2622), Offset(x - 5f, fy + 6f), Size(10f, h * 0.14f))
        drawRect(Color(0xFF4A3F36), Offset(x - 11f, fy + 2f), Size(22f, 8f))
        for ((scale, color) in listOf(1f to Color(0xFFE2531B), 0.72f to Color(0xFFFF9F1C), 0.42f to Color(0xFFFFE08A))) {
            val fh = 34f * flick * scale; val fw = 12f * flick * scale
            val flame = Path()
            flame.moveTo(x - fw, fy + 2f)
            flame.quadraticTo(x - fw * 0.8f, fy - fh * 0.5f, x + sin(phase * 2 * PI * 3 + x).toFloat() * 3f, fy - fh)
            flame.quadraticTo(x + fw * 0.8f, fy - fh * 0.5f, x + fw, fy + 2f)
            flame.close()
            drawPath(flame, color)
        }
        val motes = Random(x.toInt())
        val dust = List(7) {
            val drift = (phase + motes.nextFloat()) % 1f
            Offset(x + (motes.nextFloat() - 0.5f) * w * 0.3f + sin((drift + it) * 2 * PI).toFloat() * 6f, fy + h * 0.25f - drift * h * 0.45f)
        }
        drawPoints(dust, PointMode.Points, Color(0xFFFFE3A8).copy(alpha = 0.55f), 3f, StrokeCap.Round)
    }
    // Shadows gathering in the corners.
    drawRect(Brush.radialGradient(listOf(Color(0x00000000), Color(0xAA000000)), Offset(w / 2, h * 0.5f), maxOf(w, h) * 0.75f))
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

// ---------------- The books' scenes ----------------

/** Where each book's fights happen: the dusty stacks under the library, an overgrown garden, the Word Eater's tower. */
fun DrawScope.drawBookScene(book: Int, phase: Float) {
    when (book) {
        0 -> { drawDungeon(phase); drawShelves() }
        1 -> drawGarden(phase)
        else -> drawTower(phase)
    }
}

/** Two tall bookcases stand against the dungeon wall. */
private fun DrawScope.drawShelves() {
    val w = size.width; val h = size.height
    val r = Random(21)
    for (x0 in listOf(w * 0.2f, w * 0.64f)) {
        val cw = w * 0.16f; val top = h * 0.12f; val bottom = h * 0.74f
        drawRect(Color(0xFF3B2618), Offset(x0, top), Size(cw, bottom - top))
        val rows = 4; val rh = (bottom - top) / rows
        for (row in 0 until rows) {
            var x = x0 + 4f
            val y = top + row * rh
            while (x < x0 + cw - 8f) {
                val bw = 5f + r.nextFloat() * 7f; val bh = rh * (0.6f + 0.3f * r.nextFloat())
                val spine = listOf(Color(0xFF7A2E2E), Color(0xFF2E4A7A), Color(0xFF3F6B3A), Color(0xFF8A6A2E), Color(0xFF5A3F7A))[r.nextInt(5)]
                drawRect(spine.copy(alpha = 0.85f), Offset(x, y + rh - bh - 3f), Size(bw, bh))
                x += bw + 1.5f
            }
            drawRect(Color(0xFF24170E), Offset(x0, y + rh - 3f), Size(cw, 4f))
        }
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(x0, top), Size(cw, bottom - top))
    }
}

/** A misty garden gone wild: hedges, hanging vines, ink-blot flowers and fireflies. */
private fun DrawScope.drawGarden(phase: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF6FA38E), Color(0xFF3E6B5A), Color(0xFF223D33))))
    // A pale moon behind the leaves.
    drawCircle(Color(0xFFE7F2DC).copy(alpha = 0.55f), w * 0.12f, Offset(w * 0.72f, h * 0.24f))
    val r = Random(5)
    // Ruined arches far off, then hedges nearer, darker and larger.
    for (k in 0..2) {
        val x = w * (0.1f + 0.35f * k)
        drawArc(Color(0xFF2F5446).copy(alpha = 0.6f), 180f, 180f, false, Offset(x, h * 0.3f), Size(w * 0.22f, h * 0.4f), style = Stroke(w * 0.03f))
    }
    for (layer in 0..1) {
        val base = h * (0.62f + layer * 0.12f)
        val color = if (layer == 0) Color(0xFF2C5A45) else Color(0xFF1C3D2F)
        val p = Path(); p.moveTo(0f, h); p.lineTo(0f, base)
        var x = 0f
        while (x < w) { val bump = w * (0.08f + 0.06f * r.nextFloat()); p.quadraticTo(x + bump / 2, base - h * (0.06f + 0.05f * r.nextFloat()), x + bump, base); x += bump }
        p.lineTo(w, h); p.close(); drawPath(p, color)
    }
    // Vines hanging from the top edge, swaying.
    for (k in 0..6) {
        val x = w * (0.05f + 0.15f * k); val len = h * (0.2f + 0.25f * ((k * 37) % 10) / 10f)
        val sway = sin(phase * 2 * PI + k).toFloat() * w * 0.01f
        val v = Path(); v.moveTo(x, 0f); v.quadraticTo(x + sway * 2, len / 2, x + sway, len)
        drawPath(v, Color(0xFF1F3A2C), style = Stroke(w * 0.008f))
        for (j in 1..4) drawOval(Color(0xFF2F6A4A), Offset(x + sway * j / 4 - w * 0.012f, len * j / 5f), Size(w * 0.024f, w * 0.014f))
    }
    // Ink-blot flowers.
    for (k in 0..5) {
        val at = Offset(w * (0.08f + 0.17f * k), h * (0.8f + 0.06f * (k % 2)))
        for (p in 0..4) { val a = p * 2 * PI / 5; drawCircle(Color(0xFF3A2E6E), w * 0.014f, at + Offset((cos(a) * w * 0.016f).toFloat(), (sin(a) * w * 0.016f).toFloat())) }
        drawCircle(Color(0xFFFFD166), w * 0.008f, at)
    }
    // Fireflies drifting.
    val f = Random(9)
    repeat(10) {
        val t = (phase + f.nextFloat()) % 1f
        val at = Offset(w * f.nextFloat() + sin((t + it) * 2 * PI).toFloat() * w * 0.03f, h * (0.3f + 0.5f * f.nextFloat()) - t * h * 0.1f)
        val glow = 0.4f + 0.6f * sin(t * 2 * PI * 3).toFloat().coerceAtLeast(0f)
        drawCircle(Color(0xFFFFF3A0).copy(alpha = 0.25f * glow), w * 0.018f, at); drawCircle(Color(0xFFFFF3A0).copy(alpha = glow), w * 0.005f, at)
    }
    drawRect(Brush.radialGradient(listOf(Color(0x00000000), Color(0x99000000)), Offset(w / 2, h * 0.5f), maxOf(w, h) * 0.75f))
}

/** The top of the Word Eater's tower at night: a purple sky, twinkling stars and loose letters drifting up. */
private fun DrawScope.drawTower(phase: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B1036), Color(0xFF3A1F5C), Color(0xFF5A2E6E))))
    val r = Random(13)
    repeat(40) {
        val at = Offset(w * r.nextFloat(), h * 0.6f * r.nextFloat())
        val tw = 0.4f + 0.6f * ((sin((phase + r.nextFloat()) * 2 * PI * 2).toFloat() + 1f) / 2f)
        drawCircle(Color.White.copy(alpha = 0.7f * tw), 1.2f + 1.5f * r.nextFloat(), at)
    }
    drawCircle(Color(0xFFFFF1C9), w * 0.07f, Offset(w * 0.2f, h * 0.2f))
    drawCircle(Color(0xFF1B1036).copy(alpha = 0.9f), w * 0.06f, Offset(w * 0.23f, h * 0.18f))
    // Far towers, then the stone parapet you stand on.
    for (k in 0..3) {
        val x = w * (0.05f + 0.25f * k); val tw = w * 0.1f; val top = h * (0.3f + 0.1f * (k % 2))
        drawRect(Color(0xFF24163E), Offset(x, top), Size(tw, h - top))
        val roof = Path(); roof.moveTo(x - 6f, top); roof.lineTo(x + tw / 2, top - h * 0.12f); roof.lineTo(x + tw + 6f, top); roof.close(); drawPath(roof, Color(0xFF2E1C4C))
        drawRect(Color(0xFFFFD166).copy(alpha = 0.7f), Offset(x + tw * 0.35f, top + h * 0.08f), Size(tw * 0.3f, h * 0.06f))
    }
    val floor = h * 0.74f
    drawRect(Color(0xFF3A3346), Offset(0f, floor), Size(w, h - floor))
    var x = 0f
    while (x < w) { drawRect(Color(0xFF4A4258), Offset(x, floor - h * 0.06f), Size(w * 0.08f, h * 0.06f)); x += w * 0.12f }
    drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, floor), Offset(w, floor), 2f)
    // Letters torn loose, floating up into the dark.
    val lr = Random(3)
    repeat(8) {
        val t = (phase * 0.5f + lr.nextFloat()) % 1f
        val at = Offset(w * lr.nextFloat(), floor - t * h * 0.7f)
        drawRoundRect(Color(0xFFF4ECD8).copy(alpha = 0.7f * (1f - t)), at, Size(w * 0.03f, w * 0.036f), CornerRadius(3f))
    }
    drawRect(Brush.radialGradient(listOf(Color(0x00000000), Color(0x88000000)), Offset(w / 2, h * 0.5f), maxOf(w, h) * 0.75f))
}

// ---------------- Heroes ----------------

/** A hero's portrait in a round frame. */
fun DrawScope.drawHero(hero: com.simplegamegen.sudoku.wordplay.Hero, phase: Float) {
    val s = size.minDimension; val c = center
    val bg = when (hero) {
        com.simplegamegen.sudoku.wordplay.Hero.KNIGHT -> Color(0xFF3E6FA8)
        com.simplegamegen.sudoku.wordplay.Hero.WITCH -> Color(0xFF6B3FA3)
        com.simplegamegen.sudoku.wordplay.Hero.BARD -> Color(0xFFC9892E)
    }
    drawCircle(Brush.radialGradient(listOf(bg.copy(alpha = 0.8f), bg), c - Offset(s * 0.1f, s * 0.15f), s * 0.6f), s / 2, c)
    val skin = Color(0xFFF1C7A0)
    val bob = sin(phase * 2 * PI).toFloat() * s * 0.01f
    val head = Offset(c.x, c.y + s * 0.08f + bob)
    // Shoulders.
    drawArc(Color(0xFF2A2233), 180f, 180f, true, Offset(c.x - s * 0.34f, c.y + s * 0.3f), Size(s * 0.68f, s * 0.5f))
    when (hero) {
        com.simplegamegen.sudoku.wordplay.Hero.KNIGHT -> {
            drawArc(Color(0xFF9AA3AD), 180f, 180f, true, Offset(c.x - s * 0.34f, c.y + s * 0.3f), Size(s * 0.68f, s * 0.5f))
            shadedCircle(Color(0xFFB9C1CA), s * 0.2f, head)
            drawRect(Color(0xFF2A2233), Offset(head.x - s * 0.13f, head.y - s * 0.03f), Size(s * 0.26f, s * 0.04f))
            drawLine(Color(0xFF7D8690), Offset(head.x, head.y + s * 0.02f), Offset(head.x, head.y + s * 0.17f), s * 0.015f)
            val plume = Path(); plume.moveTo(head.x, head.y - s * 0.19f)
            plume.cubicTo(head.x + s * 0.1f, head.y - s * 0.36f, head.x + s * 0.3f, head.y - s * 0.3f, head.x + s * 0.28f, head.y - s * 0.14f)
            plume.quadraticTo(head.x + s * 0.12f, head.y - s * 0.24f, head.x, head.y - s * 0.19f); plume.close()
            drawPath(plume, Color(0xFFD64545)); drawPath(plume, Outline, style = Stroke(s * 0.01f))
        }
        com.simplegamegen.sudoku.wordplay.Hero.WITCH -> {
            drawArc(Color(0xFF3A2455), 180f, 180f, true, Offset(c.x - s * 0.34f, c.y + s * 0.3f), Size(s * 0.68f, s * 0.5f))
            shadedCircle(skin, s * 0.17f, head)
            drawArc(Color(0xFF3B2A1E), 150f, 240f, false, Offset(head.x - s * 0.19f, head.y - s * 0.12f), Size(s * 0.38f, s * 0.36f), style = Stroke(s * 0.05f))
            for (side in listOf(-1f, 1f)) drawCircle(Outline, s * 0.02f, Offset(head.x + side * s * 0.06f, head.y))
            drawArc(Outline, 20f, 140f, false, Offset(head.x - s * 0.05f, head.y + s * 0.02f), Size(s * 0.1f, s * 0.07f), style = Stroke(s * 0.012f))
            val hat = Path(); hat.moveTo(head.x - s * 0.28f, head.y - s * 0.1f); hat.lineTo(head.x + s * 0.28f, head.y - s * 0.1f)
            hat.lineTo(head.x + s * 0.08f, head.y - s * 0.16f); hat.lineTo(head.x + s * 0.18f, head.y - s * 0.44f); hat.lineTo(head.x - s * 0.1f, head.y - s * 0.16f); hat.close()
            drawPath(hat, Color(0xFF2A1740)); drawPath(hat, Outline, style = Stroke(s * 0.01f))
            drawRect(Color(0xFFB266FF), Offset(head.x - s * 0.1f, head.y - s * 0.18f), Size(s * 0.18f, s * 0.035f))
            val spark = (phase * 2f) % 1f
            drawCircle(Color(0xFFD9B3FF).copy(alpha = 1f - spark), s * 0.02f, Offset(head.x - s * 0.3f, head.y - s * spark * 0.3f))
        }
        com.simplegamegen.sudoku.wordplay.Hero.BARD -> {
            drawArc(Color(0xFF2E7D6B), 180f, 180f, true, Offset(c.x - s * 0.34f, c.y + s * 0.3f), Size(s * 0.68f, s * 0.5f))
            // The lute's neck over one shoulder.
            drawLine(Color(0xFF8B5A34), Offset(c.x + s * 0.1f, c.y + s * 0.5f), Offset(c.x + s * 0.36f, c.y + s * 0.02f), s * 0.05f, cap = StrokeCap.Round)
            shadedCircle(skin, s * 0.17f, head)
            for (side in listOf(-1f, 1f)) drawCircle(Outline, s * 0.02f, Offset(head.x + side * s * 0.06f, head.y))
            drawArc(Outline, 20f, 140f, false, Offset(head.x - s * 0.06f, head.y + s * 0.03f), Size(s * 0.12f, s * 0.07f), style = Stroke(s * 0.012f))
            val cap = Path(); cap.moveTo(head.x - s * 0.2f, head.y - s * 0.08f)
            cap.quadraticTo(head.x - s * 0.05f, head.y - s * 0.32f, head.x + s * 0.2f, head.y - s * 0.12f); cap.close()
            drawPath(cap, Color(0xFF2E7D6B)); drawPath(cap, Outline, style = Stroke(s * 0.01f))
            val feather = Path(); feather.moveTo(head.x + s * 0.12f, head.y - s * 0.16f)
            feather.quadraticTo(head.x + s * 0.34f, head.y - s * 0.3f, head.x + s * 0.36f, head.y - s * 0.44f)
            feather.quadraticTo(head.x + s * 0.2f, head.y - s * 0.3f, head.x + s * 0.12f, head.y - s * 0.16f); feather.close()
            drawPath(feather, Color(0xFFFFD166)); drawPath(feather, Outline, style = Stroke(s * 0.008f))
        }
    }
    drawCircle(Outline, s / 2 - s * 0.02f, c, style = Stroke(s * 0.04f))
}

// ---------------- Icons ----------------

enum class WsIcon { HIT, BLOCK, INK, STAR, HEX }

val WsHit = Color(0xFFD64545)
val WsBlock = Color(0xFF3F9A6B)
val WsInk = Color(0xFF3E6FD8)
val WsStar = Color(0xFFE7A92E)
val WsHex = Color(0xFF8A4FD1)

/** A small drawn icon: a sword for hits, a shield for blocks, an ink drop, a star, a hex swirl. */
fun DrawScope.drawWsIcon(icon: WsIcon, at: Offset, r: Float) {
    when (icon) {
        WsIcon.HIT -> {
            val blade = Path(); blade.moveTo(at.x, at.y - r); blade.lineTo(at.x + r * 0.28f, at.y + r * 0.35f); blade.lineTo(at.x - r * 0.28f, at.y + r * 0.35f); blade.close()
            drawPath(blade, WsHit); drawPath(blade, Outline, style = Stroke(r * 0.12f))
            drawLine(Outline, Offset(at.x - r * 0.5f, at.y + r * 0.42f), Offset(at.x + r * 0.5f, at.y + r * 0.42f), r * 0.22f, cap = StrokeCap.Round)
            drawLine(Color(0xFF6B4E2A), Offset(at.x, at.y + r * 0.5f), Offset(at.x, at.y + r), r * 0.2f, cap = StrokeCap.Round)
        }
        WsIcon.BLOCK -> {
            val sh = Path(); sh.moveTo(at.x - r * 0.8f, at.y - r * 0.75f); sh.lineTo(at.x + r * 0.8f, at.y - r * 0.75f)
            sh.quadraticTo(at.x + r * 0.8f, at.y + r * 0.45f, at.x, at.y + r); sh.quadraticTo(at.x - r * 0.8f, at.y + r * 0.45f, at.x - r * 0.8f, at.y - r * 0.75f); sh.close()
            drawPath(sh, WsBlock); drawPath(sh, Outline, style = Stroke(r * 0.12f))
            drawLine(Color.White.copy(alpha = 0.6f), Offset(at.x, at.y - r * 0.5f), Offset(at.x, at.y + r * 0.6f), r * 0.12f)
        }
        WsIcon.INK -> {
            val d = Path(); d.moveTo(at.x, at.y - r)
            d.cubicTo(at.x + r * 0.3f, at.y - r * 0.4f, at.x + r * 0.8f, at.y, at.x + r * 0.7f, at.y + r * 0.4f)
            d.cubicTo(at.x + r * 0.55f, at.y + r * 1.05f, at.x - r * 0.55f, at.y + r * 1.05f, at.x - r * 0.7f, at.y + r * 0.4f)
            d.cubicTo(at.x - r * 0.8f, at.y, at.x - r * 0.3f, at.y - r * 0.4f, at.x, at.y - r); d.close()
            drawPath(d, WsInk); drawPath(d, Outline, style = Stroke(r * 0.12f))
            drawCircle(Color.White.copy(alpha = 0.6f), r * 0.16f, Offset(at.x - r * 0.25f, at.y + r * 0.2f))
        }
        WsIcon.STAR -> {
            val p = Path()
            for (k in 0 until 10) {
                val a = -PI / 2 + k * PI / 5; val rr = if (k % 2 == 0) r else r * 0.45f
                val pt = Offset(at.x + (cos(a) * rr).toFloat(), at.y + (sin(a) * rr).toFloat())
                if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
            }
            p.close(); drawPath(p, WsStar); drawPath(p, Outline, style = Stroke(r * 0.12f))
        }
        WsIcon.HEX -> {
            drawCircle(WsHex, r * 0.85f, at); drawCircle(Outline, r * 0.85f, at, style = Stroke(r * 0.12f))
            val sw = Path(); sw.moveTo(at.x - r * 0.4f, at.y)
            sw.quadraticTo(at.x - r * 0.35f, at.y - r * 0.45f, at.x, at.y - r * 0.4f); sw.quadraticTo(at.x + r * 0.45f, at.y - r * 0.3f, at.x + r * 0.3f, at.y + r * 0.1f)
            sw.quadraticTo(at.x + r * 0.1f, at.y + r * 0.4f, at.x - r * 0.12f, at.y + r * 0.15f)
            drawPath(sw, Color.White.copy(alpha = 0.85f), style = Stroke(r * 0.14f, cap = StrokeCap.Round))
        }
    }
}
