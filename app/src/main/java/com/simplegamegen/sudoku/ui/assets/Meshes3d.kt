package com.simplegamegen.sudoku.ui.assets

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------- Real 3D pieces ----------------
//
// Each piece is a small triangle mesh: positions and smooth normals in squares, z up. To draw one, every vertex is
// placed on the board and projected through the board's camera, lit (a key light from the camera's upper left, a
// fill from the right, a rim that separates dark pieces from dark squares, and a polished highlight), and the
// triangles that face the camera are painted far to near with per-vertex colours, so the shading is smooth.

/** A triangle mesh. Positions and normals are x, y, z triples; [tris] index them three at a time. */
internal class Mesh(val pos: FloatArray, val nrm: FloatArray, val tris: ShortArray) {
    val count get() = pos.size / 3
}

/** How shiny a surface is. */
internal class Finish(val spec: Float, val shine: Float, val rim: Float = 0.18f)

internal val Polished = Finish(spec = 0.55f, shine = 48f)
internal val Satin = Finish(spec = 0.2f, shine = 22f)
internal val Glassy = Finish(spec = 0.8f, shine = 90f, rim = 0.1f)

private class Builder {
    val pos = ArrayList<Float>()
    val nrm = ArrayList<Float>()
    val tris = ArrayList<Short>()
    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int {
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
        pos += x; pos += y; pos += z; nrm += nx / l; nrm += ny / l; nrm += nz / l
        return pos.size / 3 - 1
    }
    fun tri(a: Int, b: Int, c: Int) { tris += a.toShort(); tris += b.toShort(); tris += c.toShort() }
    fun build() = Mesh(pos.toFloatArray(), nrm.toFloatArray(), tris.toShortArray())
}

/**
 * A turned solid: [profile] gives (height, radius) from the base up, in squares. Normals follow the profile, so
 * curves are smooth; a corner of more than about 50 degrees is kept sharp. The top is closed if it has a radius.
 */
internal fun latheMesh(profile: List<Pair<Float, Float>>, segments: Int = 28): Mesh {
    val b = Builder()
    val turn = (2 * PI / segments).toFloat()
    // Each profile point gets the normal of the edge below and above it; a sharp corner keeps both.
    fun edge(i: Int): Pair<Float, Float> {
        val (z0, r0) = profile[i]; val (z1, r1) = profile[i + 1]
        val dz = z1 - z0; val dr = r1 - r0
        val l = sqrt(dz * dz + dr * dr).coerceAtLeast(1e-5f)
        return dz / l to -dr / l              // (radial, up)
    }
    val rows = ArrayList<IntArray>()          // vertex rows, two per profile point where a corner is sharp
    for (i in 0 until profile.lastIndex) {
        val e = edge(i)
        val below = if (i > 0) edge(i - 1) else e
        val above = if (i + 1 < profile.lastIndex) edge(i + 1) else e
        val smoothLow = below.first * e.first + below.second * e.second > 0.64f
        val smoothHigh = above.first * e.first + above.second * e.second > 0.64f
        val nLow = if (smoothLow) (below.first + e.first) to (below.second + e.second) else e
        val nHigh = if (smoothHigh) (above.first + e.first) to (above.second + e.second) else e
        for ((k, n) in listOf(i to nLow, (i + 1) to nHigh)) {
            val (z, r) = profile[k]
            rows += IntArray(segments + 1) { s ->
                val a = s * turn
                b.vertex(cos(a) * r, sin(a) * r, z, cos(a) * n.first, sin(a) * n.first, n.second)
            }
        }
        val lo = rows[rows.size - 2]; val hi = rows[rows.size - 1]
        for (s in 0 until segments) { b.tri(lo[s], lo[s + 1], hi[s + 1]); b.tri(lo[s], hi[s + 1], hi[s]) }
    }
    val (zt, rt) = profile.last()
    if (rt > 0.004f) {
        val center = b.vertex(0f, 0f, zt, 0f, 0f, 1f)
        val rim = IntArray(segments + 1) { s -> val a = s * turn; b.vertex(cos(a) * rt, sin(a) * rt, zt, 0f, 0f, 1f) }
        for (s in 0 until segments) b.tri(center, rim[s], rim[s + 1])
    }
    return b.build()
}

/** A ball of radius [r] centred at height [r]. */
internal fun sphereMesh(r: Float, rings: Int = 10): Mesh =
    latheMesh((0..rings).map { k -> val a = (PI * k / rings - PI / 2).toFloat(); (r + sin(a) * r) to (cos(a) * r).coerceAtLeast(0.0005f) }, segments = 16)

/** A box [w] across (x), [d] deep (y) and [h] tall, standing on z = 0 and centred on x and y; flat faces. */
internal fun boxMesh(w: Float, d: Float, h: Float): Mesh {
    val b = Builder()
    val x = w / 2; val y = d / 2
    fun face(nx: Float, ny: Float, nz: Float, vararg c: Float) {
        val v = IntArray(4) { k -> b.vertex(c[k * 3], c[k * 3 + 1], c[k * 3 + 2], nx, ny, nz) }
        b.tri(v[0], v[1], v[2]); b.tri(v[0], v[2], v[3])
    }
    face(0f, 0f, 1f, -x, -y, h, x, -y, h, x, y, h, -x, y, h)
    face(1f, 0f, 0f, x, -y, 0f, x, y, 0f, x, y, h, x, -y, h)
    face(-1f, 0f, 0f, -x, y, 0f, -x, -y, 0f, -x, -y, h, -x, y, h)
    face(0f, 1f, 0f, x, y, 0f, -x, y, 0f, -x, y, h, x, y, h)
    face(0f, -1f, 0f, -x, -y, 0f, x, -y, 0f, x, -y, h, -x, -y, h)
    return b.build()
}

/**
 * A carved piece: the outline [poly] (u forward, v up, in squares) cut [half] thick on each side, with a rounded
 * bevel [bevel] so it catches the light like a real carving. The thickness is along local y.
 */
internal fun carvedMesh(poly: List<Pair<Float, Float>>, half: Float, bevel: Float): Mesh {
    val b = Builder()
    val n = poly.size
    // Make the outline run anticlockwise.
    var area = 0f
    for (i in 0 until n) { val (u0, v0) = poly[i]; val (u1, v1) = poly[(i + 1) % n]; area += u0 * v1 - u1 * v0 }
    val pts = if (area < 0f) poly.reversed() else poly
    // Outward normal at each point: the average of its two edges' normals.
    val normals = pts.indices.map { i ->
        val (ua, va) = pts[(i - 1 + n) % n]; val (ub, vb) = pts[i]; val (uc, vc) = pts[(i + 1) % n]
        fun out(du: Float, dv: Float): Pair<Float, Float> { val l = sqrt(du * du + dv * dv).coerceAtLeast(1e-5f); return dv / l to -du / l }
        val e1 = out(ub - ua, vb - va); val e2 = out(uc - ub, vc - vb)
        val su = e1.first + e2.first; val sv = e1.second + e2.second
        val l = sqrt(su * su + sv * sv).coerceAtLeast(1e-5f)
        su / l to sv / l
    }
    val inset = pts.indices.map { i -> (pts[i].first - normals[i].first * bevel) to (pts[i].second - normals[i].second * bevel) }
    // Four layers across the thickness: the inset face edge, the full outline twice, the inset back edge.
    val layers = listOf(half to 0.75f, (half - bevel) to 0f, -(half - bevel) to 0f, -half to -0.75f)
    val rows = layers.map { (y, tilt) ->
        val outline = if (tilt == 0f) pts else inset
        IntArray(n) { i ->
            val (nu, nv) = normals[i]
            b.vertex(outline[i].first, y, outline[i].second, nu * (1f - abs(tilt)), tilt, nv * (1f - abs(tilt)))
        }
    }
    for (layer in 0 until rows.lastIndex) {
        val a = rows[layer]; val c = rows[layer + 1]
        for (i in 0 until n) { val j = (i + 1) % n; b.tri(a[i], a[j], c[j]); b.tri(a[i], c[j], c[i]) }
    }
    // The two flat faces, triangulated by clipping ears.
    for ((y, ny) in listOf(half to 1f, -half to -1f)) {
        val ids = IntArray(n) { i -> b.vertex(inset[i].first, y, inset[i].second, 0f, ny, 0f) }
        for ((p, q, r) in earClip(inset)) b.tri(ids[p], ids[q], ids[r])
    }
    return b.build()
}

/**
 * A rectangular plate in the x–z plane, [half] thick along y, with a round hole.
 * The outer boundary runs anticlockwise when seen from +y. The hole wall faces into the opening.
 */
internal fun holedPlate(
    x0: Float, z0: Float, x1: Float, z1: Float,
    half: Float,
    holeX: Float, holeZ: Float, holeR: Float,
    segments: Int = 16,
): Mesh {
    val b = Builder()
    val outer = arrayOf(x0 to z0, x1 to z0, x1 to z1, x0 to z1)
    val turn = (2.0 * PI / segments).toFloat()
    fun ring(y: Float, ny: Float): IntArray = IntArray(segments) { s ->
        val a = s * turn
        b.vertex(holeX + cos(a) * holeR, y, holeZ + sin(a) * holeR, 0f, ny, 0f)
    }
    fun corners(y: Float, ny: Float): IntArray = IntArray(4) { i ->
        b.vertex(outer[i].first, y, outer[i].second, 0f, ny, 0f)
    }
    val frontRing = ring(half, 1f)
    val frontCorner = corners(half, 1f)
    val backRing = ring(-half, -1f)
    val backCorner = corners(-half, -1f)
    // Each quarter of the hole stitches to one outer edge.
    val per = segments / 4
    fun cap(ringV: IntArray, cornerV: IntArray, flip: Boolean) {
        for (q in 0 until 4) {
            val c0 = cornerV[q]
            val c1 = cornerV[(q + 1) % 4]
            for (k in 0 until per) {
                val i0 = q * per + k
                val i1 = (i0 + 1) % segments
                if (!flip) {
                    b.tri(c0, ringV[i0], ringV[i1])
                    b.tri(c0, ringV[i1], c1)
                } else {
                    b.tri(c0, ringV[i1], ringV[i0])
                    b.tri(c0, c1, ringV[i1])
                }
            }
        }
    }
    cap(frontRing, frontCorner, flip = false)
    cap(backRing, backCorner, flip = true)
    fun wall(a0: Int, a1: Int, b1: Int, b0: Int, outward: Boolean) {
        if (outward) {
            b.tri(a0, a1, b1); b.tri(a0, b1, b0)
        } else {
            b.tri(a0, b1, a1); b.tri(a0, b0, b1)
        }
    }
    for (i in 0 until 4) {
        val j = (i + 1) % 4
        wall(frontCorner[i], frontCorner[j], backCorner[j], backCorner[i], outward = true)
    }
    for (s in 0 until segments) {
        val t = (s + 1) % segments
        // The hole's wall faces the centre, so the material's outside is the inward direction.
        wall(frontRing[s], frontRing[t], backRing[t], backRing[s], outward = false)
    }
    return b.build()
}

/**
 * One station of a blade, already in mesh space (z toward the tip).
 * [spine] and [edge] are the profile in x. [spineHalf] is half the thickness at the spine
 * (or at the centre ridge when [diamond] is set). [edgeHalf] is half the thickness at the cutting edge.
 * [grind] is how far from the spine, as a fraction of the blade's height, the flat stops and the bevel starts.
 */
internal class BladeSample(
    val z: Float,
    val spine: Float,
    val edge: Float,
    val spineHalf: Float,
    val edgeHalf: Float,
    val grind: Float = 0.22f,
    val diamond: Boolean = false,
)

/** The ground body and a separate darker lip along the cutting edge. */
internal class GroundBlade(val body: Mesh, val edge: Mesh)

/**
 * A blade with a thick spine, a flat, and a wedge down to a thin edge. The grind shoulder is a hard edge
 * so the highlight breaks there. Thickness can taper from station to station (distal taper).
 * An optional round hole is cut through, with walls.
 */
internal fun groundBlade(
    samples: List<BladeSample>,
    holeX: Float = 0f,
    holeZ: Float = 0f,
    holeR: Float = 0f,
): GroundBlade {
    val body = Builder()
    val lip = Builder()
    val grind = samples.first().grind.coerceIn(0.08f, 0.45f)
    val diamond = samples.first().diamond
    // t runs from the spine (0) to the cutting edge (1). The lip overlaps the body slightly so the seam does not gap.
    val bodyT = if (diamond) floatArrayOf(0f, 0.16f, 0.34f, 0.5f, 0.66f, 0.84f, 1f)
        else floatArrayOf(
            0f, grind * 0.5f, grind, grind,
            grind + (1f - grind) * 0.28f,
            grind + (1f - grind) * 0.55f,
            grind + (1f - grind) * 0.78f,
        )
    val bodyBevel = BooleanArray(bodyT.size) { c -> diamond || c >= 3 }
    fun buildSide(
        target: Builder,
        ts: FloatArray,
        bevel: BooleanArray,
        wallStart: Boolean,
        wallEnd: Boolean,
        caps: Boolean,
    ) {
        val cols = ts.size
        val n = samples.size
        val id = Array(n) { Array(cols) { IntArray(2) } }
        for (i in 0 until n) for (c in 0 until cols) for (side in 0..1) {
            val s = samples[i]
            val t = ts[c]
            val x = s.spine + (s.edge - s.spine) * t
            val yMag = sectionHalf(s, t)
            val y = if (side == 0) yMag else -yMag
            val (nx, ny, nz) = sectionNormal(samples, i, t, bevel[c], side == 0)
            id[i][c][side] = target.vertex(x, y, s.z, nx, ny, nz)
        }
        for (i in 0 until n - 1) for (c in 0 until cols - 1) for (side in 0..1) {
            if (insideHole(samples, i, c, ts, holeX, holeZ, holeR)) continue
            val a = id[i][c][side]
            val b = id[i + 1][c][side]
            val d = id[i][c + 1][side]
            val e = id[i + 1][c + 1][side]
            if (side == 0) {
                target.tri(a, d, e); target.tri(a, e, b)
            } else {
                target.tri(a, e, d); target.tri(a, b, e)
            }
        }
        // Spine or second edge, and the cutting-edge wall, so the blade is a closed solid.
        fun wall(col: Int, outwardX: Float) {
            for (i in 0 until n - 1) {
                val s0 = samples[i]; val s1 = samples[i + 1]
                val t = ts[col]
                val x0 = s0.spine + (s0.edge - s0.spine) * t
                val x1 = s1.spine + (s1.edge - s1.spine) * t
                val h0 = sectionHalf(s0, t); val h1 = sectionHalf(s1, t)
                val zm = (s0.z + s1.z) * 0.5f
                val xm = (x0 + x1) * 0.5f
                if (holeR > 0f && (xm - holeX) * (xm - holeX) + (zm - holeZ) * (zm - holeZ) < holeR * holeR) continue
                val nz = s1.z - s0.z
                val nx = outwardX
                val a = target.vertex(x0, h0, s0.z, nx, 0f, nz)
                val b = target.vertex(x1, h1, s1.z, nx, 0f, nz)
                val d = target.vertex(x0, -h0, s0.z, nx, 0f, nz)
                val e = target.vertex(x1, -h1, s1.z, nx, 0f, nz)
                target.tri(a, b, e); target.tri(a, e, d)
            }
        }
        val spineOut = if (samples[0].spine < samples[0].edge) -1f else 1f
        if (wallStart) wall(0, spineOut)
        if (wallEnd) wall(cols - 1, -spineOut)
        if (caps) {
            // Heel is the first sample and the tip is the last, with +z toward the tip.
            for (end in intArrayOf(0, n - 1)) {
                val sign = if (end == 0) -1f else 1f
                val fan = IntArray(cols * 2)
                var k = 0
                for (c in 0 until cols) fan[k++] = id[end][c][0]
                for (c in cols - 1 downTo 0) fan[k++] = id[end][c][1]
                val cx = (samples[end].spine + samples[end].edge) * 0.5f
                val center = target.vertex(cx, 0f, samples[end].z, 0f, 0f, sign)
                for (i in 0 until fan.size - 1) {
                    if (sign > 0f) target.tri(center, fan[i], fan[i + 1]) else target.tri(center, fan[i + 1], fan[i])
                }
            }
        }
        if (holeR > 0f && caps) holeWalls(target, samples, ts, holeX, holeZ, holeR)
    }
    buildSide(body, bodyT, bodyBevel, wallStart = !diamond, wallEnd = false, caps = true)
    if (diamond) {
        buildSide(lip, floatArrayOf(0f, 0.14f), booleanArrayOf(true, true), wallStart = true, wallEnd = false, caps = false)
        buildSide(lip, floatArrayOf(0.86f, 1f), booleanArrayOf(true, true), wallStart = false, wallEnd = true, caps = false)
    } else {
        buildSide(lip, floatArrayOf(0.78f, 0.9f, 1f), booleanArrayOf(true, true, true), wallStart = false, wallEnd = true, caps = false)
    }
    return GroundBlade(body.build(), lip.build())
}

private fun sectionHalf(s: BladeSample, t: Float): Float {
    val tt = t.coerceIn(0f, 1f)
    return if (s.diamond) {
        val u = abs(tt - 0.5f) * 2f
        s.spineHalf + (s.edgeHalf - s.spineHalf) * u
    } else if (tt <= s.grind) s.spineHalf
    else {
        val u = (tt - s.grind) / (1f - s.grind).coerceAtLeast(0.05f)
        s.spineHalf + (s.edgeHalf - s.spineHalf) * u
    }
}

/** Outward normal of one face. [bevel] uses the wedge slope; the flat beside the spine does not. */
private fun sectionNormal(samples: List<BladeSample>, i: Int, t: Float, bevel: Boolean, positiveY: Boolean): Triple<Float, Float, Float> {
    val s = samples[i]
    val eps = 0.04f
    val t2 = (t + eps).coerceAtMost(1f)
    val y0 = if (bevel) sectionHalf(s, t) else s.spineHalf
    val y1 = if (bevel) sectionHalf(s, t2) else s.spineHalf
    val x0 = s.spine + (s.edge - s.spine) * t
    val x1 = s.spine + (s.edge - s.spine) * t2
    val dydx = if (abs(x1 - x0) < 1e-6f) 0f else (y1 - y0) / (x1 - x0)
    val i0 = (i - 1).coerceAtLeast(0)
    val i1 = (i + 1).coerceAtMost(samples.lastIndex)
    val dydz = if (abs(samples[i1].z - samples[i0].z) < 1e-6f) 0f
        else (sectionHalf(samples[i1], t) - sectionHalf(samples[i0], t)) / (samples[i1].z - samples[i0].z)
    val ny = if (positiveY) 1f else -1f
    val nx = if (positiveY) -dydx else dydx
    val nz = if (positiveY) -dydz else dydz
    return Triple(nx, ny, nz)
}

private fun insideHole(samples: List<BladeSample>, i: Int, c: Int, ts: FloatArray, holeX: Float, holeZ: Float, holeR: Float): Boolean {
    if (holeR <= 0f || i >= samples.lastIndex || c >= ts.lastIndex) return false
    val s0 = samples[i]; val s1 = samples[i + 1]
    val t = (ts[c] + ts[c + 1]) * 0.5f
    val x0 = s0.spine + (s0.edge - s0.spine) * t
    val x1 = s1.spine + (s1.edge - s1.spine) * t
    val x = (x0 + x1) * 0.5f
    val z = (s0.z + s1.z) * 0.5f
    val dx = x - holeX; val dz = z - holeZ
    return dx * dx + dz * dz < holeR * holeR
}

private fun holeWalls(target: Builder, samples: List<BladeSample>, ts: FloatArray, holeX: Float, holeZ: Float, holeR: Float) {
    val n = samples.size
    val cols = ts.size
    fun inn(i: Int, c: Int) = i in 0 until n - 1 && c in 0 until cols - 1 && insideHole(samples, i, c, ts, holeX, holeZ, holeR)
    fun point(i: Int, c: Int): Pair<Float, Float> {
        val s = samples[i]
        val t = ts[c]
        return s.spine + (s.edge - s.spine) * t to s.z
    }
    fun quad(i0: Int, c0: Int, i1: Int, c1: Int) {
        val (x0, z0) = point(i0, c0)
        val (x1, z1) = point(i1, c1)
        val mx = (x0 + x1) * 0.5f - holeX
        val mz = (z0 + z1) * 0.5f - holeZ
        val h0 = sectionHalf(samples[i0], ts[c0])
        val h1 = sectionHalf(samples[i1], ts[c1])
        val a = target.vertex(x0, h0, z0, -mx, 0f, -mz)
        val b = target.vertex(x1, h1, z1, -mx, 0f, -mz)
        val d = target.vertex(x0, -h0, z0, -mx, 0f, -mz)
        val e = target.vertex(x1, -h1, z1, -mx, 0f, -mz)
        target.tri(a, b, e); target.tri(a, e, d)
    }
    for (i in 0 until n - 1) for (c in 0 until cols - 1) {
        if (!inn(i, c)) continue
        if (!inn(i - 1, c)) quad(i, c, i, c + 1)
        if (!inn(i + 1, c)) quad(i + 1, c, i + 1, c + 1)
        if (!inn(i, c - 1)) quad(i, c, i + 1, c)
        if (!inn(i, c + 1)) quad(i, c + 1, i + 1, c + 1)
    }
}

/** One station of a rounded handle. [x] is the centre, [rx] the half-height in the blade plane, [ry] the half-thickness. */
internal class OvalStation(val z: Float, val x: Float, val rx: Float, val ry: Float)

/** A handle swept through rounded sections, with smooth normals and closed ends. */
internal fun ovalLoft(stations: List<OvalStation>, segments: Int = 16): Mesh {
    val b = Builder()
    val n = stations.size
    val id = Array(n) { IntArray(segments) }
    for (i in 0 until n) {
        val s = stations[i]
        for (k in 0 until segments) {
            val a = (2.0 * PI * k / segments).toFloat()
            val c = cos(a); val sn = sin(a)
            val px = s.x + s.rx * c
            val py = s.ry * sn
            val i0 = (i - 1).coerceAtLeast(0)
            val i1 = (i + 1).coerceAtMost(n - 1)
            val dz = (stations[i1].z - stations[i0].z).let { if (abs(it) < 1e-5f) 1f else it }
            val drx = (stations[i1].rx - stations[i0].rx) / dz
            val dry = (stations[i1].ry - stations[i0].ry) / dz
            val dx = (stations[i1].x - stations[i0].x) / dz
            // Normal tilts as the section grows or the centreline bends.
            b.vertex(px, py, s.z, c * s.ry - dx * 0f, sn * s.rx, -(c * s.ry * drx + sn * s.rx * dry))
            id[i][k] = b.pos.size / 3 - 1
        }
    }
    for (i in 0 until n - 1) for (k in 0 until segments) {
        val k1 = (k + 1) % segments
        b.tri(id[i][k], id[i][k1], id[i + 1][k1])
        b.tri(id[i][k], id[i + 1][k1], id[i + 1][k])
    }
    for (end in intArrayOf(0, n - 1)) {
        val s = stations[end]
        val sign = if (end == 0) -1f else 1f
        val center = b.vertex(s.x, 0f, s.z, 0f, 0f, sign)
        for (k in 0 until segments) {
            val k1 = (k + 1) % segments
            if (sign > 0f) b.tri(center, id[end][k], id[end][k1]) else b.tri(center, id[end][k1], id[end][k])
        }
    }
    return b.build()
}

/** A pin along local y, for rivets, pivots and a thumb stud. */
internal fun yRod(x: Float, z: Float, y0: Float, y1: Float, radius: Float, segments: Int = 12): Mesh {
    val b = Builder()
    val turn = (2.0 * PI / segments).toFloat()
    val lo = IntArray(segments) { s ->
        val a = s * turn
        b.vertex(x + cos(a) * radius, y0, z + sin(a) * radius, cos(a), 0f, sin(a))
    }
    val hi = IntArray(segments) { s ->
        val a = s * turn
        b.vertex(x + cos(a) * radius, y1, z + sin(a) * radius, cos(a), 0f, sin(a))
    }
    for (s in 0 until segments) {
        val t = (s + 1) % segments
        b.tri(lo[s], hi[s], hi[t]); b.tri(lo[s], hi[t], lo[t])
    }
    val c0 = b.vertex(x, y0, z, 0f, -1f, 0f)
    val c1 = b.vertex(x, y1, z, 0f, 1f, 0f)
    for (s in 0 until segments) {
        val t = (s + 1) % segments
        b.tri(c0, lo[t], lo[s])
        b.tri(c1, hi[s], hi[t])
    }
    return b.build()
}

private fun abs(f: Float) = if (f < 0) -f else f

/** Triangles of a simple anticlockwise polygon. */
private fun earClip(pts: List<Pair<Float, Float>>): List<Triple<Int, Int, Int>> {
    val idx = pts.indices.toMutableList()
    val out = ArrayList<Triple<Int, Int, Int>>()
    fun cross(o: Int, a: Int, c: Int): Float {
        val (ox, oy) = pts[o]; val (ax, ay) = pts[a]; val (cx, cy) = pts[c]
        return (ax - ox) * (cy - oy) - (ay - oy) * (cx - ox)
    }
    fun inside(p: Int, a: Int, b: Int, c: Int) = cross(a, b, p) >= 0 && cross(b, c, p) >= 0 && cross(c, a, p) >= 0
    var guard = 0
    while (idx.size > 3 && guard++ < 1000) {
        var cut = false
        for (k in idx.indices) {
            val a = idx[(k - 1 + idx.size) % idx.size]; val b = idx[k]; val c = idx[(k + 1) % idx.size]
            if (cross(a, b, c) <= 0f) continue
            if (idx.any { it != a && it != b && it != c && inside(it, a, b, c) }) continue
            out += Triple(a, b, c); idx.removeAt(k); cut = true; break
        }
        if (!cut) break
    }
    if (idx.size == 3) out += Triple(idx[0], idx[1], idx[2])
    return out
}

/**
 * Height of the sloped face at local [y]. The tip is toward negative y and is the low end,
 * so the face tilts back toward the owner.
 */
internal fun shogiFaceZ(localY: Float): Float {
    val t = ((localY + 0.42f) / 0.84f).coerceIn(0f, 1f)
    return 0.18f + 0.28f * t
}

/** A little extra height in the middle of the face, so the wood catches a highlight. */
internal fun shogiCrown(x: Float, y: Float): Float {
    val dx = x / 0.30f
    val dy = (y - 0.02f) / 0.36f
    val falloff = (1f - (dx * dx + dy * dy)).coerceIn(0f, 1f)
    return shogiFaceZ(y) + 0.02f * falloff
}

private fun shogiPlan(): List<Pair<Float, Float>> {
    val corners = listOf(
        0f to -0.44f,
        0.36f to -0.02f,
        0.27f to 0.42f,
        -0.27f to 0.42f,
        -0.36f to -0.02f,
    )
    val radii = listOf(0.055f, 0.10f, 0.075f, 0.075f, 0.10f)
    val out = ArrayList<Pair<Float, Float>>()
    val n = corners.size
    val steps = 6
    for (i in 0 until n) {
        val prev = corners[(i + n - 1) % n]
        val cur = corners[i]
        val next = corners[(i + 1) % n]
        fun toward(from: Pair<Float, Float>, to: Pair<Float, Float>): Pair<Float, Float> {
            val dx = to.first - from.first
            val dy = to.second - from.second
            val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-5f)
            return dx / l to dy / l
        }
        val back = toward(cur, prev)
        val fore = toward(cur, next)
        val cut = radii[i]
        val start = (cur.first + back.first * cut) to (cur.second + back.second * cut)
        val end = (cur.first + fore.first * cut) to (cur.second + fore.second * cut)
        for (s in 0 until steps) {
            val t = s / steps.toFloat()
            val u = 1f - t
            out += (u * u * start.first + 2f * u * t * cur.first + t * t * end.first) to
                (u * u * start.second + 2f * u * t * cur.second + t * t * end.second)
        }
    }
    return out
}

/**
 * A boxwood shogi piece. Rounded pentagon, tall at the base, low at the tip, with a bevel
 * where the sides meet the face so the edge catches the light.
 */
internal fun shogiWedge(): Mesh {
    val b = Builder()
    val plan = shogiPlan()
    val n = plan.size
    val edge = Array(n) { i ->
        val a = plan[i]
        val c = plan[(i + 1) % n]
        val ex = c.first - a.first
        val ey = c.second - a.second
        val len = sqrt(ex * ex + ey * ey).coerceAtLeast(1e-4f)
        var nx = ey / len
        var ny = -ex / len
        val mx = (a.first + c.first) / 2f
        val my = (a.second + c.second) / 2f
        if (nx * mx + ny * my < 0f) {
            nx = -nx
            ny = -ny
        }
        nx to ny
    }
    val outward = Array(n) { i ->
        val p = edge[(i + n - 1) % n]
        val q = edge[i]
        val x = p.first + q.first
        val y = p.second + q.second
        val l = sqrt(x * x + y * y).coerceAtLeast(1e-4f)
        x / l to y / l
    }
    val bevel = 0.042f
    fun inset(i: Int): Pair<Float, Float> {
        val (x, y) = plan[i]
        val (nx, ny) = outward[i]
        return (x - nx * bevel) to (y - ny * bevel)
    }
    val sole = IntArray(n) { i ->
        val (x, y) = plan[i]
        val (nx, ny) = outward[i]
        b.vertex(x, y, 0f, nx, ny, 0.2f)
    }
    val wall = IntArray(n) { i ->
        val (x, y) = plan[i]
        val (nx, ny) = outward[i]
        b.vertex(x, y, (shogiFaceZ(y) - 0.07f).coerceAtLeast(0.08f), nx, ny, 0.04f)
    }
    val rim = IntArray(n) { i ->
        val (x, y) = inset(i)
        val (nx, ny) = outward[i]
        b.vertex(x, y, shogiCrown(x, y), nx * 0.65f, ny * 0.65f - 0.22f, 0.72f)
    }
    fun link(lo: IntArray, hi: IntArray) {
        for (i in 0 until n) {
            val j = (i + 1) % n
            b.tri(lo[i], lo[j], hi[j])
            b.tri(lo[i], hi[j], hi[i])
        }
    }
    link(sole, wall)
    link(wall, rim)
    val cap = IntArray(n) { i ->
        val (x, y) = inset(i)
        val tiltX = x * 0.28f
        val tiltY = -0.28f + (y - 0.02f) * 0.18f
        b.vertex(x, y, shogiCrown(x, y), tiltX, tiltY, 0.95f)
    }
    val center = b.vertex(0f, 0.04f, shogiCrown(0f, 0.04f), 0f, -0.28f, 0.96f)
    for (i in 0 until n) b.tri(center, cap[i], cap[(i + 1) % n])
    val under = IntArray(n) { i ->
        val (x, y) = plan[i]
        b.vertex(x, y, 0f, 0f, 0f, -1f)
    }
    val bc = b.vertex(0f, 0f, 0f, 0f, 0f, -1f)
    for (i in 0 until n) b.tri(bc, under[(i + 1) % n], under[i])
    return b.build()
}

// ---------------- Drawing ----------------

private val paint = Paint().apply { isAntiAlias = true }

/**
 * Where a mesh stands on the board and how it is turned: first by [rot] (a 3×3 rotation, row by row, or null), then
 * about the vertical so its local x points along ([fx], [fy]), then scaled by [scale] and moved to ([x], [y], [z]).
 */
internal class Pose(val x: Float, val y: Float, val z: Float, val fx: Float = 1f, val fy: Float = 0f, val rot: FloatArray? = null, val scale: Float = 1f) {
    /** A local point or direction turned into board space (without the move, if [direction]). */
    fun apply(lx: Float, ly: Float, lz: Float, direction: Boolean = false): FloatArray {
        val m = rot
        val ax = if (m == null) lx else m[0] * lx + m[1] * ly + m[2] * lz
        val ay = if (m == null) ly else m[3] * lx + m[4] * ly + m[5] * lz
        val az = if (m == null) lz else m[6] * lx + m[7] * ly + m[8] * lz
        val s = if (direction) 1f else scale
        val wx = (ax * fx - ay * fy) * s
        val wy = (ax * fy + ay * fx) * s
        val wz = az * s
        return if (direction) floatArrayOf(wx, wy, wz) else floatArrayOf(x + wx, y + wy, z + wz)
    }
}

/** A rotation of [angle] radians about the axis ([ax], [ay], [az]), as nine numbers. */
internal fun rotation(ax: Float, ay: Float, az: Float, angle: Float): FloatArray {
    val l = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(1e-5f)
    val x = ax / l; val y = ay / l; val z = az / l
    val c = cos(angle); val s = sin(angle); val t = 1 - c
    return floatArrayOf(
        t * x * x + c, t * x * y - s * z, t * x * z + s * y,
        t * x * y + s * z, t * y * y + c, t * y * z - s * x,
        t * x * z - s * y, t * y * z + s * x, t * z * z + c,
    )
}

/**
 * Draws [mesh] standing at ([x], [y], [z]) on the board, turned so its local x points along ([fx], [fy]), scaled by
 * [scale], in [color] with a [finish]. Hidden faces are skipped and the rest painted far to near.
 */
internal fun DrawScope.drawMesh(
    frame: TableFrame, mesh: Mesh, x: Float, y: Float, z: Float, color: Color, finish: Finish,
    fx: Float = 1f, fy: Float = 0f, scale: Float = 1f, shade: Float = 1f,
) = drawMesh(frame, mesh, Pose(x, y, z, fx, fy, null, scale), color, finish, shade)

/** Draws [mesh] at [pose]; see [Pose]. */
internal fun DrawScope.drawMesh(
    frame: TableFrame, mesh: Mesh, pose: Pose, color: Color, finish: Finish,
    shade: Float = 1f, alpha: Float = 1f, warmth: Float = 0f,
) {
    if (alpha <= 0.003f) return
    val n = mesh.count
    val basis = frame.basis()
    // Lights, in board space: a key from the camera's left and a little above, so the sides of a piece are modelled
    // and its flat tops don't burn out, and a fill from its right.
    val key = norm(-0.72f * basis[0] + 0.42f * basis[3] - 0.55f * basis[6], -0.72f * basis[1] + 0.42f * basis[4] - 0.55f * basis[7],
        -0.72f * basis[2] + 0.42f * basis[5] - 0.55f * basis[8])
    val fill = norm(0.8f * basis[0] + 0.1f * basis[3] - 0.6f * basis[6], 0.8f * basis[1] + 0.1f * basis[4] - 0.6f * basis[7],
        0.8f * basis[2] + 0.1f * basis[5] - 0.6f * basis[8])
    val wx = FloatArray(n); val wy = FloatArray(n); val wz = FloatArray(n)
    val screen = FloatArray(n * 2)
    // Android reads one colour per number in the vertex array, so the colour array is as long as that.
    val colors = IntArray(n * 2)
    val depth = FloatArray(n)
    val r = color.red; val g = color.green; val bl = color.blue
    val wn = FloatArray(n * 3)
    for (i in 0 until n) {
        val w = pose.apply(mesh.pos[i * 3], mesh.pos[i * 3 + 1], mesh.pos[i * 3 + 2])
        wx[i] = w[0]; wy[i] = w[1]; wz[i] = w[2]
        val lz = wz[i] - pose.z
        val p = frame.at(wx[i], wy[i], wz[i])
        screen[i * 2] = p.x; screen[i * 2 + 1] = p.y
        depth[i] = frame.depth(wx[i], wy[i], wz[i])
        val nn = pose.apply(mesh.nrm[i * 3], mesh.nrm[i * 3 + 1], mesh.nrm[i * 3 + 2], direction = true)
        val nx = nn[0]; val ny = nn[1]; val nz = nn[2]
        wn[i * 3] = nx; wn[i * 3 + 1] = ny; wn[i * 3 + 2] = nz
        val v = frame.toCamera(wx[i], wy[i], wz[i])
        val diffuse = max(0f, nx * key[0] + ny * key[1] + nz * key[2])
        val fillLight = max(0f, nx * fill[0] + ny * fill[1] + nz * fill[2]) * 0.35f
        val facing = max(0f, nx * v[0] + ny * v[1] + nz * v[2])
        val rim = (1f - facing).pow(3) * finish.rim
        // Blinn highlight: halfway between the key light and the eye.
        val h = norm(key[0] + v[0], key[1] + v[1], key[2] + v[2])
        val spec = max(0f, nx * h[0] + ny * h[1] + nz * h[2]).pow(finish.shine) * finish.spec
        // A little darker close to the board, where light is blocked.
        val occlusion = (0.78f + 0.22f * (lz / 0.12f).coerceIn(0f, 1f)) * shade
        // Warmth tints the key toward daylight. Zero leaves the original lighting untouched.
        val light = (0.34f + warmth * 0.08f + (0.66f - warmth * 0.04f) * diffuse + fillLight) * occlusion
        val specR = spec * (1f + warmth * 0.22f)
        val specB = spec * (1f - warmth * 0.35f)
        val cr = (r * light + specR + rim).coerceIn(0f, 1f)
        val cg = (g * light + spec + rim).coerceIn(0f, 1f)
        val cb = (bl * light + specB + rim * 1.05f).coerceIn(0f, 1f)
        colors[i] = Color(cr, cg, cb).toArgb()
    }
    // Faces that look toward the camera, far to near.
    val t = mesh.tris
    val keep = ArrayList<Int>(t.size / 3)
    for (k in 0 until t.size / 3) {
        val a = t[k * 3].toInt(); val b = t[k * 3 + 1].toInt(); val c = t[k * 3 + 2].toInt()
        val nx = wn[a * 3] + wn[b * 3] + wn[c * 3]
        val ny = wn[a * 3 + 1] + wn[b * 3 + 1] + wn[c * 3 + 1]
        val nz = wn[a * 3 + 2] + wn[b * 3 + 2] + wn[c * 3 + 2]
        val cx = (wx[a] + wx[b] + wx[c]) / 3; val cy = (wy[a] + wy[b] + wy[c]) / 3; val cz = (wz[a] + wz[b] + wz[c]) / 3
        val v = frame.toCamera(cx, cy, cz)
        if (nx * v[0] + ny * v[1] + nz * v[2] > -0.15f) keep += k
    }
    keep.sortByDescending { k -> depth[t[k * 3].toInt()] + depth[t[k * 3 + 1].toInt()] + depth[t[k * 3 + 2].toInt()] }
    val order = ShortArray(keep.size * 3)
    keep.forEachIndexed { j, k -> order[j * 3] = t[k * 3]; order[j * 3 + 1] = t[k * 3 + 1]; order[j * 3 + 2] = t[k * 3 + 2] }
    // A fading mesh is drawn whole into a layer and then faded, so its own faces don't show through each other.
    val canvas = drawContext.canvas.nativeCanvas
    val layered = alpha < 0.999f
    if (layered) canvas.saveLayerAlpha(null, (alpha * 255).toInt())
    canvas.drawVertices(NativeCanvas.VertexMode.TRIANGLES, screen.size, screen, 0, null, 0, colors, 0, order, 0, order.size, paint)
    if (layered) canvas.restore()
}

private fun norm(x: Float, y: Float, z: Float): FloatArray {
    val l = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-5f)
    return floatArrayOf(x / l, y / l, z / l)
}

// ---------------- More shapes ----------------

/**
 * A cube [size] across with rounded edges and corners of radius [round], centred on the origin. Each face is a grid
 * of [k] × [k] squares pushed out onto the rounded shape, so the light rolls smoothly over the edges.
 */
internal fun roundedCubeMesh(size: Float, round: Float, k: Int = 6): Mesh {
    val b = Builder()
    val h = size / 2
    val inner = h - round
    // Each face: its normal axis and two in-plane axes, chosen so triangles wind outward.
    val faces = listOf(
        floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f), floatArrayOf(0f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, 0f),
        floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f),
        floatArrayOf(0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f), floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
    )
    for (f in faces) {
        val ids = Array(k + 1) { i -> IntArray(k + 1) { j ->
            val u = -h + size * i / k; val v = -h + size * j / k
            val px = f[0] * h + f[3] * u + f[6] * v
            val py = f[1] * h + f[4] * u + f[7] * v
            val pz = f[2] * h + f[5] * u + f[8] * v
            val cx = px.coerceIn(-inner, inner); val cy = py.coerceIn(-inner, inner); val cz = pz.coerceIn(-inner, inner)
            var nx = px - cx; var ny = py - cy; var nz = pz - cz
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
            nx /= l; ny /= l; nz /= l
            b.vertex(cx + nx * round, cy + ny * round, cz + nz * round, nx, ny, nz)
        } }
        for (i in 0 until k) for (j in 0 until k) {
            b.tri(ids[i][j], ids[i + 1][j], ids[i + 1][j + 1]); b.tri(ids[i][j], ids[i + 1][j + 1], ids[i][j + 1])
        }
    }
    return b.build()
}

/**
 * A slab: a rounded rectangle [w] by [d] (corner radius [r]) standing [h] tall on z = 0, with a rounded bevel [bevel]
 * along its top edge and a flat top. Centred on x and y.
 */
internal fun slabMesh(w: Float, d: Float, h: Float, r: Float, bevel: Float): Mesh {
    val b = Builder()
    // The outline, anticlockwise, with its outward normals.
    val outline = ArrayList<FloatArray>()
    val corners = listOf(floatArrayOf(w / 2 - r, d / 2 - r, 0f), floatArrayOf(-w / 2 + r, d / 2 - r, 90f),
        floatArrayOf(-w / 2 + r, -d / 2 + r, 180f), floatArrayOf(w / 2 - r, -d / 2 + r, 270f))
    for (c in corners) for (s in 0..4) {
        val a = Math.toRadians((c[2] + s * 22.5f).toDouble()).toFloat()
        outline += floatArrayOf(c[0] + cos(a) * r, c[1] + sin(a) * r, cos(a), sin(a))
    }
    val n = outline.size
    // Layers up the side: the foot, the start of the bevel, the bevel's top edge pulled in.
    val rows = listOf(0f to 0f, (h - bevel) to 0f, h to 1f).map { (z, t) ->
        IntArray(n) { i ->
            val o = outline[i]
            val inset = bevel * t
            b.vertex(o[0] - o[2] * inset, o[1] - o[3] * inset, z, o[2] * (1 - 0.7f * t), o[3] * (1 - 0.7f * t), 0.9f * t)
        }
    }
    for (layer in 0 until rows.lastIndex) {
        val lo = rows[layer]; val hi = rows[layer + 1]
        for (i in 0 until n) { val j = (i + 1) % n; b.tri(lo[i], lo[j], hi[j]); b.tri(lo[i], hi[j], hi[i]) }
    }
    val center = b.vertex(0f, 0f, h, 0f, 0f, 1f)
    val top = IntArray(n) { i -> val o = outline[i]; b.vertex(o[0] - o[2] * bevel, o[1] - o[3] * bevel, h, 0f, 0f, 1f) }
    for (i in 0 until n) b.tri(center, top[i], top[(i + 1) % n])
    return b.build()
}

// ---------------- Painting on faces ----------------

/**
 * Paints flat art onto a quad in the scene: [art] draws in a [w] × [h] box (x right, y down) and the box is mapped
 * onto the four screen points [quad] (top left, top right, bottom right, bottom left), with perspective.
 */
internal fun DrawScope.drawOnQuad(quad: List<androidx.compose.ui.geometry.Offset>, w: Float, h: Float, alpha: Float = 1f, art: DrawScope.() -> Unit) {
    val m = android.graphics.Matrix()
    val ok = m.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0,
        floatArrayOf(quad[0].x, quad[0].y, quad[1].x, quad[1].y, quad[2].x, quad[2].y, quad[3].x, quad[3].y), 0, 4)
    if (!ok) return
    val canvas = drawContext.canvas.nativeCanvas
    if (alpha < 0.999f) canvas.saveLayerAlpha(null, (alpha * 255).toInt()) else canvas.save()
    canvas.concat(m)
    art()
    canvas.restore()
}

/** A flat disc lying in the plane spanned by [u] and [v] (unit directions) around [c], all in board space. */
internal fun DrawScope.drawDiscOnPlane(frame: TableFrame, c: FloatArray, u: FloatArray, v: FloatArray, r: Float, color: Color, steps: Int = 14) {
    val path = androidx.compose.ui.graphics.Path()
    for (i in 0 until steps) {
        val a = (2 * PI * i / steps).toFloat()
        val ca = cos(a) * r; val sa = sin(a) * r
        val p = frame.at(c[0] + u[0] * ca + v[0] * sa, c[1] + u[1] * ca + v[1] * sa, c[2] + u[2] * ca + v[2] * sa)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color)
}
