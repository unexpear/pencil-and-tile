package com.simplegamegen.sudoku.ui.assets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * One saved camera. [yaw] 0 looks from the near side (your side) toward the far side.
 * [pitch] 90 is straight down. [distance] is in board widths: smaller is a stronger perspective.
 * [zoom] magnifies the fitted picture without moving the camera, so perspective and tilt stay put.
 * [panX] and [panY] slide that picture in pixels. Zoom 1 and pan 0 are the fitted view, which
 * Reset views restores.
 */
internal data class BoardView(
    val name: String,
    val yaw: Float,
    val pitch: Float,
    val distance: Float,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    val fitted: Boolean get() = abs(zoom - 1f) < 0.01f && abs(panX) < 0.5f && abs(panY) < 0.5f
}

internal const val MIN_BOARD_ZOOM = 1f
internal const val MAX_BOARD_ZOOM = 4f

internal object BoardViews {
    /** Player's seat on a phone: steep enough that squares stay large, still looking along the table. */
    val behind = BoardView("Behind", yaw = 0f, pitch = 70f, distance = 1.4f)
    val corner = BoardView("Corner", yaw = 28f, pitch = 62f, distance = 1.55f)
    val top = BoardView("Top", yaw = 0f, pitch = 90f, distance = 1.75f)
    val defaults = listOf(behind, corner, top)

    /** Cameras shipped before the phone seat. Reset replaces a list that is still exactly these. */
    val previous = listOf(
        BoardView("Behind", yaw = 0f, pitch = 52f, distance = 1.55f),
        BoardView("Corner", yaw = 36f, pitch = 50f, distance = 1.7f),
        BoardView("Top", yaw = 0f, pitch = 90f, distance = 2f),
    )

    fun line(view: BoardView) =
        "yaw ${one(view.yaw)}° · pitch ${one(view.pitch)}° · distance ${one(view.distance)} · zoom ${one(view.zoom)} · pan ${one(view.panX)}, ${one(view.panY)}"

    private fun one(value: Float) = String.format(Locale.US, "%.1f", value)
}

/** A saved line. Older lines have only name, yaw, pitch and distance; those load fitted. */
internal fun encodeBoardView(view: BoardView): String = listOf(
    view.name,
    boardNum(view.yaw),
    boardNum(view.pitch),
    boardNum(view.distance),
    boardNum(view.zoom),
    boardNum(view.panX),
    boardNum(view.panY),
).joinToString("|")

internal fun decodeBoardView(line: String): BoardView? {
    val parts = line.split('|')
    if (parts[0].isBlank() || (parts.size != 4 && parts.size != 7)) return null
    val yaw = parts[1].toFloatOrNull() ?: return null
    val pitch = parts[2].toFloatOrNull() ?: return null
    val distance = parts[3].toFloatOrNull() ?: return null
    if (parts.size == 4) return BoardView(parts[0], yaw, pitch, distance)
    val zoom = parts[4].toFloatOrNull() ?: return null
    val panX = parts[5].toFloatOrNull() ?: return null
    val panY = parts[6].toFloatOrNull() ?: return null
    if (!zoom.isFinite() || zoom <= 0f || !panX.isFinite() || !panY.isFinite()) return null
    return BoardView(parts[0], yaw, pitch, distance, zoom, panX, panY)
}

private fun boardNum(value: Float) = String.format(Locale.US, "%.2f", value)

/**
 * Magnifies around a screen point (the pinch centroid), in pixels. The camera does not move:
 * [zoom] only scales the fitted picture, and pan keeps the focus where it was.
 */
internal fun magnifyAround(
    zoom: Float,
    panX: Float,
    panY: Float,
    factor: Float,
    focusX: Float,
    focusY: Float,
    width: Float,
    height: Float,
): Triple<Float, Float, Float> {
    val safe = if (zoom.isFinite() && zoom > 0f) zoom else 1f
    val next = (safe * factor).coerceIn(MIN_BOARD_ZOOM, MAX_BOARD_ZOOM)
    if (next == safe) return Triple(safe, panX, panY)
    val applied = next / safe
    return Triple(
        next,
        (focusX - width / 2f) * (1f - applied) + panX * applied,
        (focusY - height / 2f) * (1f - applied) + panY * applied,
    )
}

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
    /** 1 is a flat oblique board. Smaller means the far edge narrows, as from the player's seat. */
    private val farScale: Float,
    private val shift: Offset,
    val width: Float,
    val height: Float,
    private val cam: OrbitCam? = null,
) {
    val orbit: Boolean get() = cam != null

    /** The orbit camera behind [at], when this frame is a turned board rather than the flat oblique. */
    internal fun orbitCamera(): OrbitCam? = cam

    fun at(x: Float, y: Float, z: Float): Offset {
        val o = cam
        if (o == null) {
            val span = if (farScale >= 0.999f) 1f else {
                val along = (y / n).coerceIn(0f, 1f)
                farScale + (1f - farScale) * along
            }
            val sx = if (farScale >= 0.999f) x + y * shear else n / 2f + (x - n / 2f) * span
            return Offset(sx * cell, y * cell * yScale - z * rise) + shift
        }
        val dx = x - o.camX
        val dy = y - o.camY
        val dz = z - o.camZ
        val sx: Float
        val sy: Float
        if (o.ortho) {
            sx = dx * o.rx + dy * o.ry + dz * o.rz
            sy = -(dx * o.ux + dy * o.uy + dz * o.uz)
        } else {
            val depth = (dx * o.fx + dy * o.fy + dz * o.fz).coerceAtLeast(0.25f)
            val focal = o.focal / depth
            sx = (dx * o.rx + dy * o.ry + dz * o.rz) * focal
            sy = -(dx * o.ux + dy * o.uy + dz * o.uz) * focal
        }
        return Offset(sx * o.scale + o.ox, sy * o.scale + o.oy)
    }

    /** Larger means farther from the camera, so it should be drawn first. */
    fun depth(x: Float, y: Float, z: Float): Float {
        val o = cam ?: return y * 10f + x
        val dx = x - o.camX
        val dy = y - o.camY
        val dz = z - o.camZ
        val along = dx * o.fx + dy * o.fy + dz * o.fz
        val down = -(dx * o.ux + dy * o.uy + dz * o.uz)
        return along - down * 0.002f
    }

    /** Pixel width of one square at this point. Far squares are smaller. */
    fun unitAt(x: Float, y: Float, z: Float): Float {
        if (cam == null) return cell
        val a = at(x - 0.5f, y, z)
        val b = at(x + 0.5f, y, z)
        return sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)).coerceAtLeast(1f)
    }

    fun facing(nx: Float, ny: Float, nz: Float, px: Float, py: Float, pz: Float): Boolean {
        val o = cam ?: return true
        return (o.camX - px) * nx + (o.camY - py) * ny + (o.camZ - pz) * nz > 0.02f
    }

    /** Direction from a point toward the camera, in the board plane. */
    fun lightAngle(x: Float, y: Float): Float {
        val o = cam ?: return 0f
        return atan2(o.camY - y, o.camX - x)
    }

    /** The camera's right, up and forward (into the scene) directions in board space, nine numbers. */
    fun basis(): FloatArray {
        val o = cam ?: return ObliqueBasis
        return floatArrayOf(o.rx, o.ry, o.rz, o.ux, o.uy, o.uz, o.fx, o.fy, o.fz)
    }

    /** Unit direction from a point toward the camera. */
    fun toCamera(x: Float, y: Float, z: Float): FloatArray {
        val o = cam ?: return floatArrayOf(-ObliqueBasis[6], -ObliqueBasis[7], -ObliqueBasis[8])
        if (o.ortho) return floatArrayOf(0f, 0f, 1f)
        val dx = o.camX - x; val dy = o.camY - y; val dz = o.camZ - z
        val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        return floatArrayOf(dx / l, dy / l, dz / l)
    }

    fun square(col: Int, row: Int, z: Float) = listOf(
        at(col.toFloat(), row.toFloat(), z),
        at(col + 1f, row.toFloat(), z),
        at(col + 1f, row + 1f, z),
        at(col.toFloat(), row + 1f, z),
    )
}

/** The fixed oblique view looks from in front of the board and above it. */
private val ObliqueBasis = floatArrayOf(1f, 0f, 0f, 0f, -0.55f, 0.835f, 0f, -0.835f, -0.55f)

internal fun tableFrame(
    n: Int,
    maxWidthPx: Float,
    peakZ: Float,
    /** Seen from the player's seat: the far edge is narrower and the board uses more of the screen. */
    player: Boolean = false,
    maxHeightPx: Float = Float.POSITIVE_INFINITY,
    view: BoardView? = null,
    /** Extra board-widths around the board, so edge labels stay inside the view. */
    margin: Float = 0f,
    /** How deep the table is, front to back, when it isn't square. */
    rows: Float = n.toFloat(),
    /**
     * Fit the picture to this camera, including its yaw. The usual path fits the head-on view and
     * keeps that scale while turning, so a board does not resize as it spins. Knife Flip turns
     * this on so Side and Corner each fill the frame.
     */
    fitToView: Boolean = false,
): TableFrame {
    if (view != null) return orbitFrame(n, maxWidthPx, peakZ, maxHeightPx, view, margin, rows, fitToView)
    val shear = if (player) 0f else 0.22f
    val yScale = if (player) 0.9f else 0.52f
    val farScale = if (player) 0.78f else 1f
    val pad = if (player) 18f else 10f
    val riseRatio = if (player) 0.62f else 0.72f
    val widthUnits = if (player) n.toFloat() else n * (1f + shear)
    var cell = ((maxWidthPx - pad * 2) / widthUnits).coerceAtLeast(1f)
    if (maxHeightPx.isFinite()) {
        val heightUnits = n * yScale + peakZ * riseRatio
        val fit = ((maxHeightPx - pad * 2) / heightUnits).coerceAtLeast(1f)
        if (fit < cell) cell = fit
    }
    val rise = cell * riseRatio
    fun raw(x: Float, y: Float, z: Float): Offset {
        val span = if (!player) 1f else {
            val along = (y / n).coerceIn(0f, 1f)
            farScale + (1f - farScale) * along
        }
        val sx = if (!player) x + y * shear else n / 2f + (x - n / 2f) * span
        return Offset(sx * cell, y * cell * yScale - z * rise)
    }
    val edge = n.toFloat()
    val pts = listOf(
        raw(0f, 0f, peakZ), raw(edge, 0f, peakZ), raw(0f, edge, peakZ), raw(edge, edge, peakZ),
        raw(0f, edge, 0f), raw(edge, edge, 0f),
    )
    val minX = pts.minOf { it.x }
    val minY = pts.minOf { it.y }
    val shift = Offset(pad - minX, pad - minY)
    return TableFrame(
        n, cell, rise, shear, yScale, farScale, shift,
        pts.maxOf { it.x } - minX + pad * 2,
        pts.maxOf { it.y } - minY + pad * 2,
    )
}

internal class OrbitCam(
    val camX: Float, val camY: Float, val camZ: Float,
    val fx: Float, val fy: Float, val fz: Float,
    val rx: Float, val ry: Float, val rz: Float,
    val ux: Float, val uy: Float, val uz: Float,
    val focal: Float,
    val ortho: Boolean,
    val scale: Float,
    val ox: Float, val oy: Float,
)

private class OrbitBasis(
    val camX: Float, val camY: Float, val camZ: Float,
    val fx: Float, val fy: Float, val fz: Float,
    val rx: Float, val ry: Float, val rz: Float,
    val ux: Float, val uy: Float, val uz: Float,
    val focal: Float,
    val ortho: Boolean,
) {
    fun raw(x: Float, y: Float, z: Float): Offset {
        val dx = x - camX
        val dy = y - camY
        val dz = z - camZ
        return if (ortho) {
            Offset(dx * rx + dy * ry + dz * rz, -(dx * ux + dy * uy + dz * uz))
        } else {
            val depth = (dx * fx + dy * fy + dz * fz).coerceAtLeast(0.25f)
            val focal = this.focal / depth
            Offset((dx * rx + dy * ry + dz * rz) * focal, -(dx * ux + dy * uy + dz * uz) * focal)
        }
    }
}

private fun orbitBasis(cx: Float, cy: Float, lookZ: Float, yawDeg: Float, pitchDeg: Float, dist: Float): OrbitBasis {
    val yaw = Math.toRadians(yawDeg.toDouble())
    val pitch = Math.toRadians(pitchDeg.toDouble())
    val cosP = cos(pitch).toFloat()
    val sinP = sin(pitch).toFloat()
    val cosY = cos(yaw).toFloat()
    val sinY = sin(yaw).toFloat()
    val camX = cx + dist * cosP * sinY
    val camY = cy + dist * cosP * cosY
    val camZ = lookZ + dist * sinP
    val ortho = pitchDeg >= 89f
    val fx: Float
    val fy: Float
    val fz: Float
    val rx: Float
    val ry: Float
    val rz: Float
    val ux: Float
    val uy: Float
    val uz: Float
    if (ortho) {
        fx = 0f; fy = 0f; fz = -1f
        rx = cosY; ry = -sinY; rz = 0f
        ux = -sinY; uy = -cosY; uz = 0f
    } else {
        var fxx = cx - camX
        var fyy = cy - camY
        var fzz = lookZ - camZ
        val fl = sqrt(fxx * fxx + fyy * fyy + fzz * fzz).coerceAtLeast(0.001f)
        fxx /= fl; fyy /= fl; fzz /= fl
        fx = fxx; fy = fyy; fz = fzz
        var rxx = -fy
        var ryy = fx
        var rzz = 0f
        val rl = sqrt(rxx * rxx + ryy * ryy).coerceAtLeast(0.001f)
        rxx /= rl; ryy /= rl; rzz /= rl
        rx = rxx; ry = ryy; rz = rzz
        ux = fy * rz - fz * ry
        uy = fz * rx - fx * rz
        uz = fx * ry - fy * rx
    }
    return OrbitBasis(camX, camY, camZ, fx, fy, fz, rx, ry, rz, ux, uy, uz, dist, ortho)
}

/** How the head-on picture fits the screen. Turning reuses this scale so a square does not resize. */
private class FrontalFit(val scale: Float, val ox: Float, val oy: Float, val frameW: Float, val frameH: Float)

private fun frontalFit(
    raw: (Float, Float, Float) -> Offset,
    n: Int,
    rows: Float,
    peakZ: Float,
    margin: Float,
    maxWidthPx: Float,
    maxHeightPx: Float,
): FrontalFit {
    val edge = n.toFloat()
    val deep = rows
    val gutter = margin.coerceAtLeast(0f)
    val pts = listOf(
        raw(0f, 0f, 0f), raw(edge, 0f, 0f), raw(0f, deep, 0f), raw(edge, deep, 0f),
        raw(0f, 0f, peakZ), raw(edge, 0f, peakZ), raw(0f, deep, peakZ), raw(edge, deep, peakZ),
        raw(-gutter, deep / 2f, 0f), raw(edge + gutter, deep / 2f, 0f),
        raw(edge / 2f, -gutter, 0f), raw(edge / 2f, deep + gutter, 0f),
        raw(-gutter * 0.75f, -gutter * 0.75f, 0f), raw(edge + gutter * 0.75f, -gutter * 0.75f, 0f),
        raw(-gutter * 0.75f, deep + gutter * 0.75f, 0f), raw(edge + gutter * 0.75f, deep + gutter * 0.75f, 0f),
    )
    val minX = pts.minOf { it.x }
    val maxX = pts.maxOf { it.x }
    val minY = pts.minOf { it.y }
    val maxY = pts.maxOf { it.y }
    val rawW = (maxX - minX).coerceAtLeast(0.01f)
    val rawH = (maxY - minY).coerceAtLeast(0.01f)
    val pad = 8f
    val availW = (maxWidthPx - pad * 2).coerceAtLeast(1f)
    val availH = if (maxHeightPx.isFinite()) (maxHeightPx - pad * 2).coerceAtLeast(1f) else Float.POSITIVE_INFINITY
    val scale = minOf(availW / rawW, availH / rawH)
    val drawnW = rawW * scale
    val drawnH = rawH * scale
    val frameW = if (maxHeightPx.isFinite()) maxWidthPx else drawnW + pad * 2
    val frameH = if (maxHeightPx.isFinite()) maxHeightPx else drawnH + pad * 2
    return FrontalFit(
        scale,
        (frameW - drawnW) / 2f - minX * scale,
        (frameH - drawnH) / 2f - minY * scale,
        frameW,
        frameH,
    )
}

private fun orbitFrame(
    n: Int, maxWidthPx: Float, peakZ: Float, maxHeightPx: Float, view: BoardView, margin: Float,
    rows: Float = n.toFloat(), fitToView: Boolean = false,
): TableFrame {
    val cx = n / 2f
    val cy = rows / 2f
    val lookZ = peakZ * 0.35f
    val pitchDeg = view.pitch.coerceIn(16f, 90f)
    val dist = view.distance.coerceIn(1.05f, 4f) * maxOf(n.toFloat(), rows)
    val actual = orbitBasis(cx, cy, lookZ, view.yaw, pitchDeg, dist)
    // Head-on fit (yaw 0) sets the square size and where the board sits. Turning keeps both,
    // so a square does not grow and shrink and the board does not slide off center.
    // [fitToView] instead fits the turned camera, so that preset fills the frame.
    val frontal = if (fitToView || view.yaw == 0f) actual else orbitBasis(cx, cy, lookZ, 0f, pitchDeg, dist)
    val fit = frontalFit(frontal::raw, n, rows, peakZ, margin, maxWidthPx, maxHeightPx)
    val anchor = frontal.raw(cx, cy, 0f)
    val anchorScreen = Offset(anchor.x * fit.scale + fit.ox, anchor.y * fit.scale + fit.oy)
    val here = actual.raw(cx, cy, 0f)
    var ox = anchorScreen.x - here.x * fit.scale
    var oy = anchorScreen.y - here.y * fit.scale
    val zoom = if (view.zoom.isFinite() && view.zoom > 0f) view.zoom.coerceIn(MIN_BOARD_ZOOM, MAX_BOARD_ZOOM) else 1f
    val scale = fit.scale * zoom
    ox = (ox - fit.frameW / 2f) * zoom + fit.frameW / 2f + view.panX
    oy = (oy - fit.frameH / 2f) * zoom + fit.frameH / 2f + view.panY
    val basis = OrbitCam(
        actual.camX, actual.camY, actual.camZ, actual.fx, actual.fy, actual.fz,
        actual.rx, actual.ry, actual.rz, actual.ux, actual.uy, actual.uz,
        actual.focal, actual.ortho, scale, ox, oy,
    )
    val mid = Offset(here.x * scale + ox, here.y * scale + oy)
    val stepRaw = actual.raw(cx + 1f, cy, 0f)
    val step = Offset(stepRaw.x * scale + ox, stepRaw.y * scale + oy)
    val cell = sqrt((step.x - mid.x) * (step.x - mid.x) + (step.y - mid.y) * (step.y - mid.y)).coerceAtLeast(1f)
    return TableFrame(n, cell, cell * 0.62f, 0f, 1f, 1f, Offset.Zero, fit.frameW, fit.frameH, basis)
}

internal fun DrawScope.drawBoardSlab(frame: TableFrame, top: Float, wood: Color, depth: Float = frame.n.toFloat()) {
    if (frame.orbit) drawOrbitSides(frame, top, wood, depth)
    else {
        val n = frame.n.toFloat()
        val dark = lerp(wood, Color.Black, 0.28f)
        drawPath(quad(frame.at(0f, depth, top), frame.at(n, depth, top), frame.at(n, depth, 0f), frame.at(0f, depth, 0f)), dark)
        drawPath(quad(frame.at(n, 0f, top), frame.at(n, depth, top), frame.at(n, depth, 0f), frame.at(n, 0f, 0f)), lerp(wood, Color.Black, 0.12f))
    }
    drawSlabTop(frame, top, wood, depth)
}

/** The playing surface, with a few grain lines so it reads as felt or wood rather than a thin edge. */
private fun DrawScope.drawSlabTop(frame: TableFrame, top: Float, wood: Color, depth: Float) {
    val n = frame.n.toFloat()
    if (frame.orbit && !frame.facing(0f, 0f, 1f, n / 2f, depth / 2f, top)) return
    drawPath(quad(frame.at(0f, 0f, top), frame.at(n, 0f, top), frame.at(n, depth, top), frame.at(0f, depth, top)), wood)
    val grain = lerp(wood, Color.Black, 0.28f).copy(alpha = 0.4f)
    val w = (frame.cell * 0.01f).coerceAtLeast(0.6f)
    var y = 0.4f
    while (y < depth - 0.2f) {
        drawLine(grain, frame.at(0.2f, y, top + 0.004f), frame.at(n - 0.2f, y + 0.06f, top + 0.004f), strokeWidth = w)
        y += 0.7f
    }
}

private fun DrawScope.drawOrbitSides(frame: TableFrame, top: Float, wood: Color, depth: Float) {
    if (frame.facing(0f, 0f, 1f, frame.n / 2f, depth / 2f, top) && frame.at(0f, 0f, 0f).y - frame.at(0f, 0f, top).y < 2f) return
    val n = frame.n.toFloat()
    val dark = lerp(wood, Color.Black, 0.28f)
    val side = lerp(wood, Color.Black, 0.12f)
    class Face(val nx: Float, val ny: Float, val cx: Float, val cy: Float, val color: Color, val x: FloatArray, val y: FloatArray, val z: FloatArray)
    val faces = listOf(
        Face(0f, 1f, n / 2f, depth, dark, floatArrayOf(0f, n, n, 0f), floatArrayOf(depth, depth, depth, depth), floatArrayOf(top, top, 0f, 0f)),
        Face(0f, -1f, n / 2f, 0f, dark, floatArrayOf(n, 0f, 0f, n), floatArrayOf(0f, 0f, 0f, 0f), floatArrayOf(top, top, 0f, 0f)),
        Face(1f, 0f, n, depth / 2f, side, floatArrayOf(n, n, n, n), floatArrayOf(0f, depth, depth, 0f), floatArrayOf(top, top, 0f, 0f)),
        Face(-1f, 0f, 0f, depth / 2f, side, floatArrayOf(0f, 0f, 0f, 0f), floatArrayOf(depth, 0f, 0f, depth), floatArrayOf(top, top, 0f, 0f)),
    )
    faces.filter { frame.facing(it.nx, it.ny, 0f, it.cx, it.cy, top / 2f) }
        .sortedByDescending { frame.depth(it.cx, it.cy, top / 2f) }
        .forEach { face ->
            drawPath(quad(
                frame.at(face.x[0], face.y[0], face.z[0]),
                frame.at(face.x[1], face.y[1], face.z[1]),
                frame.at(face.x[2], face.y[2], face.z[2]),
                frame.at(face.x[3], face.y[3], face.z[3]),
            ), face.color)
        }
}

/**
 * A board with a wooden frame [border] squares wide: a shadow on the table, the frame's sides, its top with a bevel
 * and faint grain. Squares and labels are drawn over it afterwards; labels sit on the frame.
 */
internal fun DrawScope.drawBoardFrame(frame: TableFrame, top: Float, wood: Color, border: Float = 0.5f) {
    val n = frame.n.toFloat()
    val lo = -border; val hi = n + border
    // A soft shadow on the table, a little toward you.
    for (k in 2 downTo 1) {
        val g = border + 0.05f * k
        drawPath(quad(frame.at(-g, -g + 0.12f, 0f), frame.at(n + g, -g + 0.12f, 0f), frame.at(n + g, n + g + 0.12f, 0f), frame.at(-g, n + g + 0.12f, 0f)),
            Color.Black.copy(alpha = 0.06f))
    }
    val dark = lerp(wood, Color.Black, 0.35f)
    val side = lerp(wood, Color.Black, 0.18f)
    class Face(val nx: Float, val ny: Float, val cx: Float, val cy: Float, val color: Color, val a: Pair<Float, Float>, val b: Pair<Float, Float>)
    val faces = listOf(
        Face(0f, 1f, n / 2f, hi, dark, lo to hi, hi to hi),
        Face(0f, -1f, n / 2f, lo, dark, hi to lo, lo to lo),
        Face(1f, 0f, hi, n / 2f, side, hi to hi, hi to lo),
        Face(-1f, 0f, lo, n / 2f, side, lo to lo, lo to hi),
    )
    faces.filter { frame.facing(it.nx, it.ny, 0f, it.cx, it.cy, top / 2f) }
        .sortedByDescending { frame.depth(it.cx, it.cy, top / 2f) }
        .forEach { f ->
            drawPath(quad(frame.at(f.a.first, f.a.second, top), frame.at(f.b.first, f.b.second, top),
                frame.at(f.b.first, f.b.second, 0f), frame.at(f.a.first, f.a.second, 0f)), f.color)
        }
    // The frame's top, lighter toward the outside edge, and a dark bevel where it meets the squares.
    drawPath(quad(frame.at(lo, lo, top), frame.at(hi, lo, top), frame.at(hi, hi, top), frame.at(lo, hi, top)), wood)
    val grain = lerp(wood, Color.Black, 0.25f).copy(alpha = 0.35f)
    val w = (frame.cell * 0.012f).coerceAtLeast(0.8f)
    for (k in 0 until 5) {
        val t = lo + (k + 0.5f) * border / 5f
        drawLine(grain, frame.at(lo + 0.1f, t, top), frame.at(hi - 0.1f, t, top), strokeWidth = w)
        drawLine(grain, frame.at(lo + 0.1f, n - t, top), frame.at(hi - 0.1f, n - t, top), strokeWidth = w)
    }
    drawPath(quad(frame.at(lo, lo, top), frame.at(hi, lo, top), frame.at(hi, hi, top), frame.at(lo, hi, top)),
        lerp(wood, Color.White, 0.25f).copy(alpha = 0.6f), style = Stroke((frame.cell * 0.03f).coerceAtLeast(1f)))
    val b = 0.06f
    drawPath(quad(frame.at(-b, -b, top), frame.at(n + b, -b, top), frame.at(n + b, n + b, top), frame.at(-b, n + b, top)), lerp(wood, Color.Black, 0.55f))
}

/** Faint grain across one square, so the squares read as inlaid wood. */
internal fun DrawScope.drawSquareGrain(frame: TableFrame, col: Int, row: Int, z: Float, color: Color) {
    val w = (frame.unitAt(col + 0.5f, row + 0.5f, z) * 0.01f).coerceAtLeast(0.6f)
    val grain = lerp(color, Color.Black, 0.3f).copy(alpha = 0.18f)
    val seed = (col * 7 + row * 13) % 5
    for (k in 0 until 3) {
        val t = row + 0.2f + 0.3f * k + 0.04f * seed
        drawLine(grain, frame.at(col + 0.04f, t, z + 0.001f), frame.at(col + 0.96f, t + 0.05f * ((k + seed) % 3 - 1), z + 0.001f), strokeWidth = w)
    }
}

/** A short coordinate just outside the board. [x] and [y] are in squares. */
internal fun DrawScope.drawBoardLabel(
    measurer: TextMeasurer, frame: TableFrame, text: String, x: Float, y: Float, z: Float, color: Color,
) {
    val px = (frame.cell * 0.34f).coerceIn(8f, 20f)
    val layout = measurer.measure(
        text,
        TextStyle(color = color, fontSize = px.toSp(), fontWeight = FontWeight.Medium),
    )
    val at = frame.at(x, y, z)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

internal fun DrawScope.drawSquareTop(frame: TableFrame, col: Int, row: Int, z: Float, color: Color) {
    val q = frame.square(col, row, z)
    drawPath(quad(q[0], q[1], q[2], q[3]), color)
}

/** A floating square with a thin edge. (x, y) is the center, so stacked boards can sit between the grid lines. */
internal fun DrawScope.drawFloatSquare(frame: TableFrame, x: Float, y: Float, z: Float, color: Color, thick: Float = 0.16f) {
    val x0 = x - 0.5f; val x1 = x + 0.5f
    val y0 = y - 0.5f; val y1 = y + 0.5f
    val z0 = (z - thick).coerceAtLeast(0.02f)
    val side = lerp(color, Color.Black, 0.32f)
    data class Face(val nx: Float, val ny: Float, val cx: Float, val cy: Float, val cz: Float, val path: Path)
    val faces = listOf(
        Face(0f, -1f, x, y0, (z + z0) / 2f, quad(frame.at(x0, y0, z), frame.at(x1, y0, z), frame.at(x1, y0, z0), frame.at(x0, y0, z0))),
        Face(0f, 1f, x, y1, (z + z0) / 2f, quad(frame.at(x1, y1, z), frame.at(x0, y1, z), frame.at(x0, y1, z0), frame.at(x1, y1, z0))),
        Face(1f, 0f, x1, y, (z + z0) / 2f, quad(frame.at(x1, y0, z), frame.at(x1, y1, z), frame.at(x1, y1, z0), frame.at(x1, y0, z0))),
        Face(-1f, 0f, x0, y, (z + z0) / 2f, quad(frame.at(x0, y1, z), frame.at(x0, y0, z), frame.at(x0, y0, z0), frame.at(x0, y1, z0))),
    )
    faces.filter { frame.facing(it.nx, it.ny, 0f, it.cx, it.cy, it.cz) }
        .sortedByDescending { frame.depth(it.cx, it.cy, it.cz) }
        .forEach { drawPath(it.path, side) }
    drawPath(quad(frame.at(x0, y0, z), frame.at(x1, y0, z), frame.at(x1, y1, z), frame.at(x0, y1, z)), color)
}

/** True when [pos] hits the top of a floating square, or the piece standing on it. */
internal fun floatSquareHit(frame: TableFrame, pos: Offset, x: Float, y: Float, z: Float, pieceTop: Float, rx: Float): Boolean {
    val x0 = x - 0.5f; val x1 = x + 0.5f
    val y0 = y - 0.5f; val y1 = y + 0.5f
    if (pointInQuad(pos, frame.at(x0, y0, z), frame.at(x1, y0, z), frame.at(x1, y1, z), frame.at(x0, y1, z))) return true
    if (pieceTop <= z) return false
    val base = frame.at(x, y, z)
    val crown = frame.at(x, y, pieceTop)
    return pointInQuad(
        pos,
        Offset(base.x - rx, base.y + rx * 0.3f),
        Offset(base.x + rx, base.y + rx * 0.3f),
        Offset(crown.x + rx, crown.y - rx * 0.2f),
        Offset(crown.x - rx, crown.y - rx * 0.2f),
    )
}

/** A thick milled checker. A king is two discs with a gold crown standing on the top one. */
internal fun DrawScope.drawPuck(frame: TableFrame, x: Float, y: Float, baseZ: Float, color: Color, king: Boolean, lift: Float = 0f) {
    val foot = baseZ + lift
    contactShadow(frame, x, y, baseZ, 0.42f)
    val finish = if (color.luminance() > 0.55f) Satin else Polished
    drawMesh(frame, CheckerMesh, x, y, foot, color, finish)
    if (!king) return
    drawMesh(frame, CheckerMesh, x, y, foot + 0.15f, color, finish)
    checkerCrown(frame, x, y, foot + 0.32f)
}

/** Height of one disc, and of a king including the crown. Used so a tap on the piece counts. */
internal fun checkerRise(king: Boolean) = if (king) 0.48f else 0.22f

private val CheckerMesh by lazy {
    latheMesh(listOf(
        0f to 0.26f, 0.02f to 0.36f, 0.035f to 0.40f, 0.09f to 0.40f, 0.11f to 0.35f,
        0.125f to 0.31f, 0.14f to 0.35f, 0.155f to 0.28f, 0.175f to 0.26f, 0.19f to 0.24f,
    ), segments = 32)
}

private val CheckerBand by lazy {
    latheMesh(listOf(0f to 0.08f, 0.02f to 0.15f, 0.055f to 0.16f, 0.08f to 0.10f, 0.10f to 0.05f), segments = 20)
}

private val CheckerKnob by lazy { sphereMesh(0.042f) }

private fun DrawScope.checkerCrown(frame: TableFrame, x: Float, y: Float, z: Float) {
    val gold = Color(0xFFE8B923)
    drawMesh(frame, CheckerBand, x, y, z, gold, Polished)
    val ring = 0.12f
    (0 until 5).map { k -> (PI / 2 + k * 2 * PI / 5).toFloat() }
        .sortedByDescending { a -> frame.depth(x + cos(a) * ring, y + sin(a) * ring, z + 0.08f) }
        .forEach { a -> drawMesh(frame, CheckerKnob, x + cos(a) * ring, y + sin(a) * ring, z + 0.05f, gold, Polished) }
    drawMesh(frame, CheckerKnob, x, y, z + 0.07f, gold, Polished)
}

/** A soft shadow where a piece meets the board, a little away from the light. */
private fun DrawScope.contactShadow(frame: TableFrame, x: Float, y: Float, z: Float, radius: Float) {
    val away = frame.lightAngle(x, y) + PI.toFloat()
    val sx = x + cos(away) * radius * 0.12f
    val sy = y + sin(away) * radius * 0.12f
    drawFlatDisk(frame, sx, sy, z + 0.003f, radius * 1.08f, Color.Black.copy(alpha = 0.12f))
    drawFlatDisk(frame, sx, sy, z + 0.004f, radius * 0.86f, Color.Black.copy(alpha = 0.18f))
}

/** A circle lying on the board, so it stays round from above and flattens as the camera drops. */
internal fun DrawScope.drawFlatDisk(
    frame: TableFrame, x: Float, y: Float, z: Float, radius: Float, color: Color, strokePx: Float = 0f,
) {
    if (radius <= 0.004f) return
    val path = Path()
    val steps = 14
    val turn = (PI * 2.0 / steps).toFloat()
    for (i in 0 until steps) {
        val a = i * turn
        val p = frame.at(x + cos(a) * radius, y + sin(a) * radius, z)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    if (strokePx > 0f) drawPath(path, color, style = Stroke(strokePx)) else drawPath(path, color)
}

/** A destination marker lying on the board. Larger and rimmed so it reads on a tilted square. */
internal fun DrawScope.drawDot(frame: TableFrame, x: Float, y: Float, z: Float, color: Color, radius: Float = 0.24f) {
    drawFlatDisk(frame, x, y, z + 0.03f, radius + 0.05f, Color.Black.copy(alpha = 0.4f))
    drawFlatDisk(frame, x, y, z + 0.05f, radius, color)
}

/**
 * A camera-facing ring around a piece. Stays round when the board is tilted, for a selection or a capture.
 * [radiusScale] is in squares.
 */
internal fun DrawScope.drawRing(
    frame: TableFrame, x: Float, y: Float, z: Float, color: Color, radiusScale: Float = 0.5f,
) {
    val at = frame.at(x, y, z)
    val unit = frame.unitAt(x, y, z)
    val radius = unit * radiusScale
    val stroke = (unit * 0.1f).coerceAtLeast(2.8f)
    drawCircle(Color.Black.copy(alpha = 0.55f), radius, at, style = Stroke(stroke + unit * 0.05f))
    drawCircle(color, radius, at, style = Stroke(stroke))
}

/** A biconvex Go stone: polished slate or shell, a lens that catches the light. */
internal fun DrawScope.drawStone(frame: TableFrame, x: Float, y: Float, z: Float, color: Color, mark: Boolean, markColor: Color, dead: Boolean = false) {
    val face = if (dead) lerp(color, Color(0xFF9E9E9E), 0.5f) else color
    contactShadow(frame, x, y, z, 0.46f)
    drawMesh(frame, StoneMesh, x, y, z, face, if (color.luminance() > 0.5f) Satin else Polished)
    val top = frame.at(x, y, z + 0.19f)
    val unit = frame.unitAt(x, y, z)
    if (mark) drawCircle(markColor, unit * 0.11f, top, style = Stroke(unit * 0.05f))
    if (dead) {
        val rx = unit * 0.2f
        drawLine(Color(0xFFB71C1C), Offset(top.x - rx, top.y - rx), Offset(top.x + rx, top.y + rx), strokeWidth = rx * 0.3f, cap = StrokeCap.Round)
        drawLine(Color(0xFFB71C1C), Offset(top.x - rx, top.y + rx), Offset(top.x + rx, top.y - rx), strokeWidth = rx * 0.3f, cap = StrokeCap.Round)
    }
}

private val StoneMesh by lazy {
    // A lens: two shallow domes meeting at a rounded edge.
    latheMesh((0..12).map { k ->
        val a = (PI * k / 12 - PI / 2).toFloat()
        (0.095f + sin(a) * 0.095f) to (cos(a).coerceAtLeast(0f).pow(0.55f) * 0.46f).coerceAtLeast(0.001f)
    })
}

/** Polished boxwood and ebony, whatever the theme: chess men are white and black. */
internal val ChessIvory = Color(0xFFEFE2C4)
internal val ChessEbony = Color(0xFF3A2F2B)

/**
 * A Staunton man in 3D: a turned column with a carved knight's head, a slit mitre, battlements, a coronet or a cross.
 * Knights turn their profile to the camera, white to the left and black to the right, so they read from any view.
 */
internal fun DrawScope.drawChessMan(
    frame: TableFrame, x: Float, y: Float, baseZ: Float, piece: Int, color: Color, lift: Float = 0f,
) {
    val kind = abs(piece)
    val height = chessRise(piece)
    val maxR = chessRadius(kind)
    val foot = baseZ + lift
    contactShadow(frame, x, y, baseZ, maxR)
    val finish = if (color.luminance() > 0.5f) Satin else Polished
    drawMesh(frame, chessBody(kind), x, y, foot, color, finish)
    val basis = frame.basis()
    val rl = sqrt(basis[0] * basis[0] + basis[1] * basis[1]).coerceAtLeast(1e-4f)
    val rightX = basis[0] / rl; val rightY = basis[1] / rl
    when (kind) {
        2 -> {
            val dir = if (piece > 0) -1f else 1f
            drawMesh(frame, KnightHead, x, y, foot + height * 0.3f, color, finish, fx = rightX * dir, fy = rightY * dir, scale = 1.22f)
            // Eyes and nostrils, on whichever side faces the camera.
            val toCam = frame.toCamera(x, y, foot + height * 0.6f)
            val side = if (-(rightY * dir) * toCam[0] + (rightX * dir) * toCam[1] > 0f) 1f else -1f
            val sx = -rightY * dir * side; val sy = rightX * dir * side
            fun on(u: Float, v: Float) = frame.at(x + 1.22f * u * rightX * dir + sx * 0.15f, y + 1.22f * u * rightY * dir + sy * 0.15f, foot + height * 0.3f + 1.22f * 1.3f * v)
            val unit = frame.unitAt(x, y, foot)
            val ink = lerp(color, Color.Black, 0.78f)
            drawCircle(ink, unit * 0.026f, on(0.03f, 0.36f))
            drawCircle(Color.White.copy(alpha = 0.6f), unit * 0.009f, on(0.025f, 0.365f))
            drawCircle(ink.copy(alpha = 0.7f), unit * 0.015f, on(0.21f, 0.2f))
        }
        3 -> {
            // The mitre's slit, cut on the side facing you.
            val toward = frame.lightAngle(x, y)
            val r = maxR * 0.42f
            val a = frame.at(x + cos(toward + 0.7f) * r, y + sin(toward + 0.7f) * r, foot + height * 0.68f)
            val b = frame.at(x + cos(toward - 0.2f) * r * 0.8f, y + sin(toward - 0.2f) * r * 0.8f, foot + height * 0.86f)
            drawLine(lerp(color, Color.Black, 0.75f), a, b, strokeWidth = (frame.unitAt(x, y, foot) * 0.03f).coerceAtLeast(1.2f), cap = StrokeCap.Round)
        }
        4 -> {
            val top = foot + height
            drawFlatDisk(frame, x, y, top + 0.002f, maxR * 0.46f, lerp(color, Color.Black, 0.42f))
            (0 until 4).map { k -> (PI / 4 + k * PI / 2).toFloat() }.sortedByDescending { a ->
                frame.depth(x + cos(a) * maxR * 0.7f, y + sin(a) * maxR * 0.7f, top)
            }.forEach { a ->
                drawMesh(frame, Merlon, x + cos(a) * maxR * 0.68f, y + sin(a) * maxR * 0.68f, top, color, finish, fx = cos(a), fy = sin(a))
            }
        }
        5 -> {
            val ring = foot + height * 0.9f
            val r = maxR * 0.56f
            (0 until 8).map { k -> (k * PI / 4).toFloat() }.sortedByDescending { a -> frame.depth(x + cos(a) * r, y + sin(a) * r, ring) }
                .forEach { a -> drawMesh(frame, CoronetBall, x + cos(a) * r, y + sin(a) * r, ring - 0.01f, color, finish) }
            drawMesh(frame, CrownBall, x, y, foot + height - 0.01f, color, finish)
        }
        6 -> {
            val top = foot + height - 0.005f
            drawMesh(frame, CrossFoot, x, y, top, color, finish)
            drawMesh(frame, CrossUpright, x, y, top + 0.05f, color, finish, fx = rightX, fy = rightY)
            drawMesh(frame, CrossBar, x, y, top + 0.14f, color, finish, fx = rightX, fy = rightY)
        }
        7 -> {
            val dir = if (piece > 0) -1f else 1f
            drawMesh(frame, KnightHead, x, y, foot + height * 0.28f, color, finish, fx = rightX * dir, fy = rightY * dir, scale = 1.08f)
            drawMesh(
                frame, UnicornHorn,
                x + rightX * dir * 0.16f, y + rightY * dir * 0.16f, foot + height * 0.78f,
                color, finish, fx = rightX * dir, fy = rightY * dir,
            )
        }
    }
}

internal fun chessRadius(kind: Int) = when (kind) { 1 -> 0.29f; 4 -> 0.33f; 2, 7 -> 0.35f; else -> 0.37f }

private val Bodies = HashMap<Int, Mesh>()

/** The turned part of each man, built once. */
private fun chessBody(kind: Int): Mesh = Bodies.getOrPut(kind) {
    val h = chessRise(kind) * if (kind == 2 || kind == 7) 0.32f else 1f
    val r = chessRadius(kind)
    latheMesh(profile(kind).map { (a, b) -> a * h to b * r })
}

/** The knight's head and neck: an outline in profile, carved with a rounded edge. */
private val KnightHead by lazy {
    carvedMesh(listOf(
        -0.17f to 0f, -0.2f to 0.14f, -0.19f to 0.26f, -0.15f to 0.38f, -0.09f to 0.46f, -0.07f to 0.57f,
        -0.02f to 0.49f, 0.04f to 0.47f, 0.11f to 0.41f, 0.18f to 0.31f, 0.24f to 0.23f, 0.26f to 0.18f,
        0.22f to 0.13f, 0.12f to 0.13f, 0.06f to 0.07f, 0.1f to 0f,
    ).map { (u, v) -> u to v * 1.3f }, half = 0.12f, bevel = 0.04f)  // a tall neck reads as a horse from above too
}
private val Merlon by lazy { boxMesh(0.09f, 0.14f, 0.11f) }
private val CoronetBall by lazy { sphereMesh(0.04f) }
private val CrownBall by lazy { sphereMesh(0.06f) }
private val CrossFoot by lazy { sphereMesh(0.045f) }
private val CrossUpright by lazy { boxMesh(0.06f, 0.06f, 0.2f) }
private val CrossBar by lazy { boxMesh(0.17f, 0.06f, 0.06f) }
private val UnicornHorn by lazy { boxMesh(0.05f, 0.05f, 0.34f) }

/** Height from the base, radius as a share of the widest ring. A Staunton column. */
private fun profile(kind: Int): List<Pair<Float, Float>> = when (kind) {
    // A ball on a slim stem over a stepped base.
    1 -> listOf(0f to 1f, 0.07f to 1f, 0.1f to 0.86f, 0.15f to 0.8f, 0.2f to 0.62f, 0.42f to 0.38f, 0.48f to 0.62f, 0.53f to 0.64f,
        0.58f to 0.36f, 0.62f to 0.42f, 0.68f to 0.52f, 0.76f to 0.57f, 0.84f to 0.54f, 0.91f to 0.44f, 0.96f to 0.28f, 1f to 0.04f)
    // A tower that widens to its battlements.
    4 -> listOf(0f to 1f, 0.07f to 1f, 0.1f to 0.88f, 0.16f to 0.82f, 0.22f to 0.68f, 0.66f to 0.6f, 0.72f to 0.82f, 0.78f to 0.86f, 1f to 0.86f)
    // The knight's plinth; the head is drawn on it.
    2, 7 -> listOf(0f to 1f, 0.12f to 1f, 0.2f to 0.86f, 0.42f to 0.78f, 0.6f to 0.62f, 0.8f to 0.46f, 1f to 0.42f)
    // A stem, a collar and an egg-shaped mitre.
    3 -> listOf(0f to 1f, 0.06f to 1f, 0.09f to 0.86f, 0.14f to 0.8f, 0.18f to 0.6f, 0.48f to 0.34f, 0.52f to 0.58f, 0.56f to 0.6f,
        0.6f to 0.32f, 0.64f to 0.4f, 0.72f to 0.5f, 0.8f to 0.48f, 0.88f to 0.36f, 0.95f to 0.16f, 1f to 0.03f)
    // A flared crown over a tall stem.
    5 -> listOf(0f to 1f, 0.05f to 1f, 0.08f to 0.86f, 0.12f to 0.8f, 0.16f to 0.6f, 0.5f to 0.34f, 0.55f to 0.56f, 0.59f to 0.58f,
        0.63f to 0.34f, 0.8f to 0.46f, 0.88f to 0.62f, 0.9f to 0.6f, 0.92f to 0.4f, 1f to 0.3f)
    else -> listOf(0f to 1f, 0.05f to 1f, 0.08f to 0.86f, 0.12f to 0.8f, 0.16f to 0.6f, 0.5f to 0.34f, 0.55f to 0.56f, 0.59f to 0.58f,
        0.63f to 0.34f, 0.8f to 0.46f, 0.86f to 0.58f, 0.9f to 0.54f, 0.94f to 0.4f, 1f to 0.36f)
}

internal fun chessRise(piece: Int) = when (abs(piece)) {
    1 -> 0.66f
    2 -> 0.9f
    7 -> 0.98f
    3 -> 0.94f
    4 -> 0.78f
    5 -> 1.1f
    else -> 1.22f
}

private val ShogiWedge by lazy { shogiWedge() }
private val ShogiWood = Color(0xFFF3D7A6)
private val ShogiFinish = Finish(spec = 0.34f, shine = 28f, rim = 0.26f)
private val shogiInk by lazy {
    android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
    }
}

/** King largest, pawn smallest. [base] is the unpromoted kind, 1 through 8. */
internal fun shogiScale(base: Int) = when (base) {
    8 -> 1.08f
    7 -> 1.0f
    6 -> 0.97f
    5 -> 0.94f
    4 -> 0.92f
    3 -> 0.88f
    2 -> 0.86f
    else -> 0.78f
}

/** How tall the tallest wedge stands, for taps that land on the piece rather than its square. */
internal fun shogiRise() = 0.52f

/**
 * A boxwood shogi piece. [yours] points the tip toward the far side. The character sits on the sloped face,
 * red when [promoted]. [scale] is the piece's size: a king is larger than a pawn.
 */
internal fun DrawScope.drawShogiPiece(
    frame: TableFrame,
    x: Float,
    y: Float,
    baseZ: Float,
    yours: Boolean,
    glyph: String,
    promoted: Boolean,
    scale: Float,
    lift: Float = 0f,
) {
    val fx = if (yours) 1f else -1f
    val foot = baseZ + lift
    contactShadow(frame, x, y, baseZ, 0.38f * scale)
    val pose = Pose(x, y, foot, fx = fx, fy = 0f, scale = scale)
    drawMesh(frame, ShogiWedge, pose, ShogiWood, ShogiFinish)
    fun onFace(lx: Float, ly: Float): Offset {
        val at = pose.apply(lx, ly, shogiCrown(lx, ly) + 0.012f)
        return frame.at(at[0], at[1], at[2])
    }
    val quad = listOf(
        onFace(-0.20f, -0.16f),
        onFace(0.20f, -0.16f),
        onFace(0.20f, 0.26f),
        onFace(-0.20f, 0.26f),
    )
    val ink = if (promoted) Color(0xFFB71C1C) else Color(0xFF1C1208)
    drawOnQuad(quad, 200f, 260f) {
        val native = drawContext.canvas.nativeCanvas
        shogiInk.textSize = 168f
        val fm = shogiInk.fontMetrics
        val baseline = 130f - (fm.ascent + fm.descent) / 2f
        shogiInk.color = android.graphics.Color.argb(90, 40, 24, 8)
        native.drawText(glyph, 102f, baseline + 5f, shogiInk)
        shogiInk.color = ink.toArgb()
        native.drawText(glyph, 100f, baseline, shogiInk)
    }
}

internal fun chessSymbol(piece: Int): String = when (abs(piece)) {
    1 -> if (piece > 0) "♙" else "♟"
    2 -> if (piece > 0) "♘" else "♞"
    3 -> if (piece > 0) "♗" else "♝"
    4 -> if (piece > 0) "♖" else "♜"
    5 -> if (piece > 0) "♕" else "♛"
    else -> if (piece > 0) "♔" else "♚"
}

/**
 * The board point whose center is closest to [pos], measured in that point's own square.
 * [reach] is how many squares away a tap still counts, so a small far point stays easy to hit.
 */
internal fun nearestCell(frame: TableFrame, pos: Offset, n: Int, z: Float, reach: Float = 0.8f): Int? {
    var best = -1
    var bestScore = Float.MAX_VALUE
    for (i in 0 until n * n) {
        val col = i % n
        val row = i / n
        val at = frame.at(col + 0.5f, row + 0.5f, z)
        val dx = pos.x - at.x
        val dy = pos.y - at.y
        val unit = frame.unitAt(col + 0.5f, row + 0.5f, z)
        val score = sqrt(dx * dx + dy * dy) / unit
        if (score < bestScore) {
            bestScore = score
            best = i
        }
    }
    return if (best >= 0 && bestScore <= reach) best else null
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
