package com.simplegamegen.sudoku.ui.assets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.simplegamegen.sudoku.ui.i18n.say
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------- 3D dice on a felt tray ----------------

private const val DieSize = 0.9f
private const val Spacing = 1.3f
private val DieIvory = Color(0xFFF7F1E4)
private val PipInk = Color(0xFF1C1B22)
private val Felt = Color(0xFF2E6B4A)
private val Rim = Color(0xFF6B4528)
private val HeldGold = Color(0xFFF2C14E)

private val DieMesh by lazy { roundedCubeMesh(DieSize, DieSize * 0.16f) }

/** A die's faces: each outward normal (x, y, z) with the number on it. Opposite faces add up to seven. */
private class DieFaces(val normals: List<FloatArray>, val values: List<Int>)

/** All 24 ways a die can lie, found by turning the standard die about two axes. */
private val Orientations: List<DieFaces> by lazy {
    val start = DieFaces(
        listOf(floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f)),
        listOf(1, 6, 3, 4, 5, 2),
    )
    fun turn(d: DieFaces, m: (FloatArray) -> FloatArray) = DieFaces(d.normals.map(m), d.values)
    fun key(d: DieFaces) = d.normals.zip(d.values).joinToString { (n, v) -> "${n.joinToString { it.roundToInt().toString() }}=$v" }
    val seen = LinkedHashMap<String, DieFaces>()
    val queue = ArrayDeque(listOf(start))
    while (queue.isNotEmpty()) {
        val d = queue.removeFirst()
        if (seen.put(key(d), d) != null) continue
        queue += turn(d) { n -> floatArrayOf(n[0], -n[2], n[1]) }
        queue += turn(d) { n -> floatArrayOf(-n[1], n[0], n[2]) }
    }
    seen.values.toList()
}

/** A way for the die to lie with [top] up; [variant] picks which of the four sides faces you. */
private fun orientation(top: Int, variant: Int): DieFaces {
    val fits = Orientations.filter { d -> d.values[d.normals.indexOfFirst { it[2] > 0.5f }] == top }
    return fits[((variant % fits.size) + fits.size) % fits.size]
}

/** Pip spots on a face, in face units from −1 to 1. */
private fun pips(value: Int): List<Pair<Float, Float>> {
    val a = 0.55f
    return when (value) {
        1 -> listOf(0f to 0f)
        2 -> listOf(-a to -a, a to a)
        3 -> listOf(-a to -a, 0f to 0f, a to a)
        4 -> listOf(-a to -a, a to -a, -a to a, a to a)
        5 -> listOf(-a to -a, a to -a, 0f to 0f, -a to a, a to a)
        else -> listOf(-a to -a, a to -a, -a to 0f, a to 0f, -a to a, a to a)
    }
}

/**
 * A row of dice on a felt tray, seen from in front and above. Each die is a rounded ivory cube with sunken pips;
 * a held die sits a little higher on a gold ring. When [rollKey] changes, the dice that aren't held tumble and land.
 */
@Composable
fun DiceTray(faces: List<Int>, held: List<Boolean>, enabled: Boolean, rollKey: Any?, onDie: (Int) -> Unit) {
    val density = LocalDensity.current
    val tumble = remember { Animatable(1f) }
    LaunchedEffect(rollKey) {
        if (rollKey == null) return@LaunchedEffect
        tumble.snapTo(0f)
        tumble.animateTo(1f, tween(750, easing = LinearEasing))
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val count = faces.size.coerceAtLeast(1)
        val wide = count * Spacing + 0.4f
        val n = ceil(wide).toInt()
        val pad = (n - wide) / 2f + 0.2f
        val rows = 1.9f
        val maxW = with(density) { maxWidth.toPx() }
        val frame = tableFrame(n, maxW, peakZ = DieSize + 0.6f, maxHeightPx = maxW * 0.42f,
            view = BoardView("Tray", yaw = 0f, pitch = 56f, distance = 1.55f), margin = 0.15f, rows = rows)
        fun spot(i: Int) = Offset(pad + Spacing * (i + 0.5f), rows / 2f)
        Box(Modifier.size(with(density) { frame.width.toDp() }, with(density) { frame.height.toDp() })) {
            faces.forEachIndexed { i, face ->
                val at = frame.at(spot(i).x, spot(i).y, DieSize)
                Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp).semantics {
                    contentDescription = say(if (held.getOrElse(i) { false }) "Die showing $face, held" else "Die showing $face")
                    if (enabled) onClick { onDie(i); true }
                })
            }
            Canvas(Modifier.matchParentSize().pointerInput(enabled, faces, held) {
                detectTapGestures { pos ->
                    if (!enabled) return@detectTapGestures
                    val hit = faces.indices.minByOrNull { i ->
                        val c = frame.at(spot(i).x, spot(i).y, DieSize * 0.6f)
                        (c - pos).getDistance()
                    } ?: return@detectTapGestures
                    val c = frame.at(spot(hit).x, spot(hit).y, DieSize * 0.6f)
                    if ((c - pos).getDistance() < frame.unitAt(spot(hit).x, spot(hit).y, 0f) * 0.8f) onDie(hit)
                }
            }) {
                drawTray(frame, n.toFloat(), rows)
                // Far to near isn't needed in a single row; left to right is fine.
                faces.forEachIndexed { i, face ->
                    val isHeld = held.getOrElse(i) { false }
                    val p = spot(i)
                    val t = if (isHeld) 1f else tumble.value
                    drawDie(frame, p.x, p.y, face, i, isHeld, t)
                }
            }
        }
    }
}

private fun DrawScope.drawTray(frame: TableFrame, w: Float, d: Float) {
    val lip = 0.12f
    fun q(x0: Float, y0: Float, x1: Float, y1: Float, z: Float) = quad(frame.at(x0, y0, z), frame.at(x1, y0, z), frame.at(x1, y1, z), frame.at(x0, y1, z))
    // The wooden tray, its felt, and a darker band where the felt meets the rim.
    drawPath(q(-lip, -lip, w + lip, d + lip, 0f), Rim)
    drawPath(quad(frame.at(-lip, d + lip, 0f), frame.at(w + lip, d + lip, 0f), frame.at(w + lip, d + lip, -0.25f), frame.at(-lip, d + lip, -0.25f)),
        lerp(Rim, Color.Black, 0.35f))
    drawPath(q(0f, 0f, w, d, 0.001f), Felt)
    drawPath(q(0f, 0f, w, 0.18f, 0.002f), lerp(Felt, Color.Black, 0.25f))
}

private fun DrawScope.drawDie(frame: TableFrame, x: Float, y: Float, face: Int, index: Int, held: Boolean, t: Float) {
    val h = DieSize / 2
    // Each die lies a little askew, and tumbles in from a bounce while it rolls.
    val yaw = (((index * 37) % 9) - 4) * 0.05f
    val lift = if (held) 0.08f else abs(sin(t * PI.toFloat() * 2.3f)) * (1 - t) * 0.9f
    val spin = (1 - t) * (1 - t) * (5f + index)
    val axisAngle = index * 1.3f
    val rot = if (spin > 0.001f) rotation(cos(axisAngle), sin(axisAngle), 0f, spin) else null
    val pose = Pose(x, y, h + lift, cos(yaw), sin(yaw), rot)
    // A shadow on the felt, softer as the die rises.
    val shadow = (0.28f * (1f - lift)).coerceAtLeast(0.08f)
    if (held) {
        drawDiscOnPlane(frame, floatArrayOf(x, y, 0.004f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), 0.66f, HeldGold, steps = 36)
        drawDiscOnPlane(frame, floatArrayOf(x, y, 0.005f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), 0.56f, Felt, steps = 36)
    }
    drawDiscOnPlane(frame, floatArrayOf(x + 0.06f, y - 0.04f, 0.006f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), 0.5f, Color.Black.copy(alpha = shadow))
    drawMesh(frame, DieMesh, pose, DieIvory, Polished)
    // Pips on each face the camera can see.
    val sides = orientation(face, index)
    sides.normals.forEachIndexed { k, n ->
        val wn = pose.apply(n[0], n[1], n[2], direction = true)
        val c = pose.apply(n[0] * h * 1.002f, n[1] * h * 1.002f, n[2] * h * 1.002f)
        val v = frame.toCamera(c[0], c[1], c[2])
        if (wn[0] * v[0] + wn[1] * v[1] + wn[2] * v[2] < 0.12f) return@forEachIndexed
        // Two axes in the face, so the spots lie on it.
        val a = if (abs(n[2]) > 0.5f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 0f, 1f)
        val b = floatArrayOf(n[1] * a[2] - n[2] * a[1], n[2] * a[0] - n[0] * a[2], n[0] * a[1] - n[1] * a[0])
        val ua = pose.apply(a[0], a[1], a[2], direction = true)
        val ub = pose.apply(b[0], b[1], b[2], direction = true)
        val value = sides.values[k]
        val r = if (value == 1) h * 0.24f else h * 0.15f
        pips(value).forEach { (pu, pv) ->
            val s = h * 0.62f
            val center = floatArrayOf(c[0] + ua[0] * pu * s + ub[0] * pv * s, c[1] + ua[1] * pu * s + ub[1] * pv * s, c[2] + ua[2] * pu * s + ub[2] * pv * s)
            val facing = (wn[0] * v[0] + wn[1] * v[1] + wn[2] * v[2]).coerceIn(0f, 1f)
            // A sunken spot: a dark ring with a deeper centre.
            drawDiscOnPlane(frame, center, ua, ub, r, lerp(PipInk, DieIvory, 0.25f * (1 - facing)))
            drawDiscOnPlane(frame, floatArrayOf(center[0] + wn[0] * 0.001f, center[1] + wn[1] * 0.001f, center[2] + wn[2] * 0.001f), ua, ub, r * 0.62f,
                lerp(PipInk, Color.Black, 0.4f))
        }
    }
}
