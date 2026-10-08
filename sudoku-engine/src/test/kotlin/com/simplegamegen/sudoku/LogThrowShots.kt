package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.arcade.DrawOp
import com.simplegamegen.sudoku.arcade.LogThrow
import com.simplegamegen.sudoku.arcade.LogThrowArt
import com.simplegamegen.sudoku.arcade.Phase
import com.simplegamegen.sudoku.arcade.StuckKnife
import com.simplegamegen.sudoku.arcade.ThrowState
import org.junit.jupiter.api.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.min

/** Renders the boards the game draws, when the artifact folder is present. */
class LogThrowShots {
    @Test fun `render log throw boards`() {
        val root = File("/opt/cursor/artifacts")
        if (!root.isDirectory) return
        val dir = File(root, "log-throw-v2")
        dir.mkdirs()
        val w = 1080
        val h = 1920
        val stars = List(LogThrow.LEVEL_COUNT) { i -> if (i < 6) 3 else if (i == 6) 2 else if (i == 7) 1 else 0 }
        val menu = LogThrow.newGame(1).copy(best = 1840, stars = stars)
        val endless = stick(LogThrow.newGame(2).beginEndless(), 3)
        val reversal = stick(LogThrow.newGame(2).beginEndless().atStage(7), 2)
        val boss = stick(opened(10), 3)
        val challenge = stick(opened(4), 1)
        val select = menu.beginLevels()
        val failed = clash(LogThrow.newGame(8).beginEndless())
        val cleared = solve(LogThrow.newGame(9).beginEndless()).advance(420)
        val shots = listOf(
            "menu" to menu,
            "endless" to endless,
            "reversal" to reversal,
            "boss" to boss,
            "challenge" to challenge,
            "levels" to select,
            "fail" to failed,
            "clear" to cleared,
        )
        val images = shots.map { (name, state) ->
            val image = raster(LogThrowArt.frame(state, w.toFloat(), h.toFloat()).ops, w, h)
            ImageIO.write(image, "png", File(dir, "$name.png"))
            name to image
        }
        contact(dir, images)
    }

    private fun raster(ops: List<DrawOp>, w: Int, h: Int): BufferedImage {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        ops.forEach { op -> draw(g, op) }
        g.dispose()
        return image
    }

    private fun draw(g: Graphics2D, op: DrawOp) {
        when (op) {
            is DrawOp.VGrad -> {
                g.paint = GradientPaint(op.x, op.y, color(op.top), op.x, op.y + op.h, color(op.bottom))
                g.fill(java.awt.geom.Rectangle2D.Float(op.x, op.y, op.w, op.h))
            }
            is DrawOp.Circle -> {
                g.paint = color(op.color)
                val e = Ellipse2D.Float(op.x - op.r, op.y - op.r, op.r * 2, op.r * 2)
                if (op.stroke > 0f) { g.stroke = BasicStroke(op.stroke); g.draw(e) } else g.fill(e)
            }
            is DrawOp.RoundRect -> {
                g.paint = color(op.color)
                val e = RoundRectangle2D.Float(op.x, op.y, op.w, op.h, op.radius * 2, op.radius * 2)
                if (op.stroke > 0f) { g.stroke = BasicStroke(op.stroke); g.draw(e) } else g.fill(e)
            }
            is DrawOp.Line -> {
                g.paint = color(op.color)
                g.stroke = BasicStroke(op.stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(Line2D.Float(op.x1, op.y1, op.x2, op.y2))
            }
            is DrawOp.Poly -> {
                val path = polygon(op.pts) ?: return
                g.paint = color(op.color)
                if (op.stroke > 0f) { g.stroke = BasicStroke(op.stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); g.draw(path) } else g.fill(path)
            }
            is DrawOp.Shade -> {
                val path = polygon(op.pts) ?: return
                val dx = op.x1 - op.x0
                val dy = op.y1 - op.y0
                val x1 = if (dx * dx + dy * dy < 0.25f) op.x0 + 1f else op.x1
                val y1 = if (dx * dx + dy * dy < 0.25f) op.y0 else op.y1
                g.paint = GradientPaint(op.x0, op.y0, color(op.c0), x1, y1, color(op.c1))
                g.fill(path)
            }
            is DrawOp.Wedge -> {
                g.paint = color(op.color)
                g.fill(Arc2D.Double((op.cx - op.r).toDouble(), (op.cy - op.r).toDouble(), (op.r * 2).toDouble(), (op.r * 2).toDouble(), -op.startDeg.toDouble(), -op.sweep.toDouble(), Arc2D.PIE))
            }
            is DrawOp.Label -> {
                g.paint = color(op.color)
                g.font = Font(Font.SANS_SERIF, if (op.bold) Font.BOLD else Font.PLAIN, op.size.toInt().coerceAtLeast(12))
                val width = g.fontMetrics.stringWidth(op.text)
                val x = when (op.align) { 0 -> op.x; 2 -> op.x - width; else -> op.x - width / 2f }
                g.drawString(op.text, x, op.y)
            }
        }
    }

    private fun color(argb: Long): Color {
        val a = ((argb ushr 24) and 0xFF).toInt()
        val r = ((argb ushr 16) and 0xFF).toInt()
        val gg = ((argb ushr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return Color(r, gg, b, a)
    }

    private fun polygon(pts: List<Float>): Path2D.Float? {
        if (pts.size < 4) return null
        val path = Path2D.Float()
        path.moveTo(pts[0], pts[1])
        var i = 2
        while (i + 1 < pts.size) { path.lineTo(pts[i], pts[i + 1]); i += 2 }
        path.closePath()
        return path
    }

    private fun contact(dir: File, shots: List<Pair<String, BufferedImage>>) {
        val cols = 4
        val rows = 2
        val thumbW = 360
        val thumbH = 640
        val label = 36
        val sheet = BufferedImage(cols * thumbW, rows * (thumbH + label), BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = Color(20, 16, 12)
        g.fillRect(0, 0, sheet.width, sheet.height)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 22)
        g.color = Color(246, 231, 193)
        shots.forEachIndexed { i, (name, image) ->
            val x = (i % cols) * thumbW
            val y = (i / cols) * (thumbH + label)
            g.drawImage(image, x, y, thumbW, thumbH, null)
            g.drawString(name, x + 12, y + thumbH + 26)
        }
        g.dispose()
        ImageIO.write(sheet, "png", File(dir, "contact.png"))
    }

    private fun opened(level: Int): ThrowState {
        val hub = LogThrow.newGame(4).beginLevels()
        val unlocked = (1 until level).fold(hub) { s, i -> s.copy(stars = s.stars.mapIndexed { idx, v -> if (idx == i - 1) 1 else v }) }
        return unlocked.beginLevel(level)!!
    }

    private fun ThrowState.atStage(stage: Int): ThrowState {
        val spec = LogThrow.endlessSpec(stage)
        return copy(level = stage, timeMs = 0, thrown = 0, phase = Phase.AIM, taken = emptySet(),
            stuck = spec.obstacles.map { StuckKnife(LogThrow.norm(it), false) })
    }

    private fun stick(start: ThrowState, n: Int): ThrowState {
        var s = start
        repeat(min(n, start.spec().knives - 1)) {
            val t = nextThrow(s, true)
            s = s.copy(timeMs = t).throwKnife()!!.advance(LogThrow.FLIGHT_MS)
        }
        return s
    }

    private fun clash(start: ThrowState): ThrowState {
        val stuck = stick(start, 1)
        var t = stuck.timeMs
        while (t < stuck.timeMs + 20_000 && !stuck.preview(t).blocked) t += 4
        val failed = stuck.copy(timeMs = t).throwKnife()!!.advance(LogThrow.FLIGHT_MS)
        return failed.copy(timeMs = failed.eventMs + 240)
    }

    private fun solve(start: ThrowState): ThrowState {
        var s = start
        repeat(start.spec().knives + 2) {
            if (s.phase == Phase.CLEAR || s.phase == Phase.FAIL) return s
            val t = nextThrow(s, s.taken.size < s.spec().pickups.size)
            s = s.copy(timeMs = t).throwKnife()!!.advance(LogThrow.FLIGHT_MS)
        }
        return s
    }

    private fun nextThrow(state: ThrowState, preferPickup: Boolean): Long {
        var t = state.timeMs
        val end = t + 16_000
        var plain: Long? = null
        while (t <= end) {
            val hit = state.preview(t)
            if (!hit.blocked) {
                if (hit.pickups.isNotEmpty() && preferPickup) return t
                if (plain == null) plain = t
                if (!preferPickup) return t
            }
            t += 8
        }
        return plain ?: t
    }
}
