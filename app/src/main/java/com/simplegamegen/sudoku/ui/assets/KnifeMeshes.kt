package com.simplegamegen.sudoku.ui.assets

import com.simplegamegen.sudoku.arcade.FlipBody
import com.simplegamegen.sudoku.arcade.FlipModels

/**
 * Procedural knives and a water bottle, in metres. A knife's origin is its centre of mass and local +z
 * points at the tip. The bottle's origin is the centre of its base and local +z points at the cap.
 * [SQUARES_PER_METRE] is the scale used when the mesh is placed on the board.
 *
 * Silhouettes follow ordinary published cutlery sizes (an 8-inch chef's knife, a centre-balanced
 * throwing knife, and an open folder). They are not traced from a product drawing, and no
 * downloaded model is used. An unknown knife id uses the chef's mesh.
 */
internal object KnifeMeshes {
    const val SQUARES_PER_METRE = 8f

    /** (height, radius) from the base. The neck stays open so the water shows through. */
    private val bottleBody = listOf(
        0.000f to 0.024f,
        0.008f to 0.0315f,
        0.148f to 0.0315f,
        0.164f to 0.018f,
        0.180f to 0.0125f,
        0.192f to 0.012f,
    )

    private val bottleCap = listOf(
        0.188f to 0.013f,
        0.194f to 0.0152f,
        0.204f to 0.0146f,
    )

    val chef: List<KnifePart> = chefParts()
    val throwing: List<KnifePart> = throwingParts()
    val pocket: List<KnifePart> = pocketParts()

    val bottle: Mesh = latheMesh(bottleBody, segments = 24)
    val cap: Mesh = latheMesh(bottleCap, segments = 20)

    private val water: List<List<Mesh>> = listOf(0.25f, 1f / 3f, 0.5f, 0.75f, 1f).map { fill ->
        listOf(0f, 0.5f, 1f).map { sigma -> waterMesh(fill, sigma) }
    }

    fun parts(body: FlipBody): List<KnifePart> = when (body.id) {
        "throwing" -> throwing
        "pocket" -> pocket
        else -> chef
    }

    fun water(fillIndex: Int, sigma: Float): Mesh {
        val bucket = when {
            sigma < 0.34f -> 0
            sigma < 0.67f -> 1
            else -> 2
        }
        return water[fillIndex.coerceIn(0, water.lastIndex)][bucket]
    }

    private fun waterMesh(fill: Float, sigma: Float): Mesh {
        val pooled = (fill * 0.150f).coerceAtLeast(0.02f)
        val climbed = (0.055f + fill * 0.115f).coerceAtMost(0.172f)
        val height = pooled + (climbed - pooled) * sigma
        val radius = 0.0265f * (1f - 0.14f * sigma)
        return latheMesh(
            listOf(
                0.006f to radius * 0.72f,
                0.016f to radius,
                height to radius * (0.96f - 0.08f * sigma),
            ),
            segments = 18,
        )
    }
}

internal enum class KnifeSurface { BLADE, EDGE, HANDLE, BOLSTER, TANG, SCALES, G10 }

internal class KnifePart(val mesh: Mesh, val surface: KnifeSurface)

// --- Chef's knife: 203 mm blade, 48 mm heel, 2.3 mm spine, 330 mm overall. ---

private val chefSpine = listOf(0f to 0f, 0.04f to -0.003f, 0.12f to -0.006f, 0.203f to -0.008f)
private val chefEdge = listOf(
    0f to 0f, 0.018f to 0.012f, 0.05f to 0.028f, 0.10f to 0.040f, 0.15f to 0.048f, 0.185f to 0.040f, 0.203f to 0.014f,
)

private fun chefParts(): List<KnifePart> {
    val body = FlipModels.chef
    val blade = groundBlade(bladeSamples(body, 0.203f, 36, chefSpine, chefEdge, spineHeel = 0.00115f, spineTip = 0.00035f, edgeHalf = 0.00012f, grind = 0.28f))
    val tang = loft(body, listOf(
        floatArrayOf(0.328f, -0.001f, 0.012f, 0.0015f),
        floatArrayOf(0.270f, 0.002f, 0.017f, 0.0016f),
        floatArrayOf(0.220f, 0.002f, 0.015f, 0.0015f),
    ))
    val scale = loft(body, listOf(
        floatArrayOf(0.326f, 0.000f, 0.009f, 0.0055f),
        floatArrayOf(0.300f, 0.003f, 0.013f, 0.0065f),
        floatArrayOf(0.260f, 0.004f, 0.0145f, 0.0072f),
        floatArrayOf(0.228f, 0.002f, 0.012f, 0.006f),
    ))
    val bolster = loft(body, listOf(
        floatArrayOf(0.222f, 0.002f, 0.016f, 0.009f),
        floatArrayOf(0.208f, 0.003f, 0.024f, 0.010f),
        floatArrayOf(0.194f, 0.004f, 0.022f, 0.008f),
    ))
    val rivets = listOf(0.246f, 0.278f, 0.308f).map { from ->
        KnifePart(yRod(0.003f, z(body, from), -0.015f, 0.015f, 0.0036f), KnifeSurface.BOLSTER)
    }
    return listOf(
        KnifePart(tang, KnifeSurface.TANG),
        KnifePart(blade.body, KnifeSurface.BLADE),
        KnifePart(blade.edge, KnifeSurface.EDGE),
        KnifePart(shiftY(scale, 0.0048f), KnifeSurface.HANDLE),
        KnifePart(shiftY(scale, -0.0048f), KnifeSurface.HANDLE),
        KnifePart(bolster, KnifeSurface.BOLSTER),
    ) + rivets
}

// --- Throwing knife: one piece of steel, 280 mm, balanced at the middle. ---

private val throwHalf = listOf(
    0f to 0.001f, 0.03f to 0.008f, 0.08f to 0.014f, 0.13f to 0.013f, 0.15f to 0.007f,
    0.17f to 0.015f, 0.22f to 0.014f, 0.26f to 0.011f, 0.28f to 0.004f,
)

private fun throwingParts(): List<KnifePart> {
    val body = FlipModels.throwing
    val spine = throwHalf.map { it.first to -it.second }
    val edge = throwHalf
    val blade = groundBlade(bladeSamples(body, 0.280f, 40, spine, edge, spineHeel = 0.0025f, spineTip = 0.0008f, edgeHalf = 0.00015f, grind = 0.5f, diamond = true))
    val wraps = (0 until 8).map { k ->
        val from = 0.172f + k * 0.0115f
        val half = curve(throwHalf, from + 0.0025f) + 0.0012f
        KnifePart(
            loft(body, listOf(
                floatArrayOf(from, 0f, half, 0.0034f),
                floatArrayOf(from + 0.0048f, 0f, half, 0.0034f),
            ), segments = 10),
            KnifeSurface.HANDLE,
        )
    }
    return listOf(KnifePart(blade.body, KnifeSurface.BLADE), KnifePart(blade.edge, KnifeSurface.EDGE)) + wraps
}

// --- Pocket knife, shown open: 90 mm drop-point blade, curved G10 handle, pivot and thumb stud. ---

private val pocketSpine = listOf(0f to 0.001f, 0.02f to 0.008f, 0.055f to 0.011f, 0.090f to 0.008f)
private val pocketEdge = listOf(0f to 0f, 0.02f to -0.006f, 0.05f to -0.012f, 0.078f to -0.008f, 0.090f to -0.001f)

private fun pocketParts(): List<KnifePart> {
    val body = FlipModels.pocket
    val blade = groundBlade(bladeSamples(body, 0.090f, 28, pocketSpine, pocketEdge, spineHeel = 0.0013f, spineTip = 0.0003f, edgeHalf = 0.0001f, grind = 0.2f))
    val handleSpec = listOf(
        floatArrayOf(0.200f, -0.004f, 0.010f, 0.0055f),
        floatArrayOf(0.172f, -0.006f, 0.014f, 0.0065f),
        floatArrayOf(0.140f, -0.004f, 0.015f, 0.007f),
        floatArrayOf(0.112f, 0.000f, 0.013f, 0.0062f),
        floatArrayOf(0.090f, 0.002f, 0.011f, 0.0055f),
    )
    val linerSpec = handleSpec.map { floatArrayOf(it[0], it[1], it[2] + 0.0012f, 0.0011f) }
    val scale = loft(body, handleSpec)
    val liner = loft(body, linerSpec)
    val pivot = yRod(0.001f, z(body, 0.096f), -0.010f, 0.010f, 0.0046f)
    val stud = yRod(0.006f, z(body, 0.070f), 0.0014f, 0.0046f, 0.0022f, segments = 10)
    val screw = yRod(-0.004f, z(body, 0.178f), -0.009f, 0.009f, 0.0024f, segments = 10)
    return listOf(
        KnifePart(shiftY(liner, 0.0024f), KnifeSurface.TANG),
        KnifePart(shiftY(liner, -0.0024f), KnifeSurface.TANG),
        KnifePart(blade.body, KnifeSurface.BLADE),
        KnifePart(blade.edge, KnifeSurface.EDGE),
        KnifePart(shiftY(scale, 0.0062f), KnifeSurface.G10),
        KnifePart(shiftY(scale, -0.0062f), KnifeSurface.G10),
        KnifePart(pivot, KnifeSurface.BOLSTER),
        KnifePart(stud, KnifeSurface.BOLSTER),
        KnifePart(screw, KnifeSurface.BOLSTER),
    )
}

private fun z(body: FlipBody, fromTip: Float) = body.comFromTip(0f) - fromTip

private fun loft(body: FlipBody, spec: List<FloatArray>, segments: Int = 16): Mesh =
    ovalLoft(spec.map { OvalStation(z(body, it[0]), it[1], it[2], it[3]) }, segments)

/** Heel first, so the mesh caps point the right way. */
private fun bladeSamples(
    body: FlipBody,
    length: Float,
    count: Int,
    spine: List<Pair<Float, Float>>,
    edge: List<Pair<Float, Float>>,
    spineHeel: Float,
    spineTip: Float,
    edgeHalf: Float,
    grind: Float,
    diamond: Boolean = false,
): List<BladeSample> = (0 until count).map { i ->
    val from = length * (1f - i / (count - 1f))
    val u = from / length
    BladeSample(
        z = z(body, from),
        spine = curve(spine, from),
        edge = curve(edge, from),
        spineHalf = spineTip + (spineHeel - spineTip) * u,
        edgeHalf = edgeHalf,
        grind = grind,
        diamond = diamond,
    )
}

private fun curve(controls: List<Pair<Float, Float>>, from: Float): Float {
    if (from <= controls.first().first) return controls.first().second
    if (from >= controls.last().first) return controls.last().second
    var i = 0
    while (i < controls.lastIndex && controls[i + 1].first < from) i++
    val p0 = controls[(i - 1).coerceAtLeast(0)]
    val p1 = controls[i]
    val p2 = controls[(i + 1).coerceAtMost(controls.lastIndex)]
    val p3 = controls[(i + 2).coerceAtMost(controls.lastIndex)]
    val span = (p2.first - p1.first).coerceAtLeast(1e-5f)
    val t = ((from - p1.first) / span).coerceIn(0f, 1f)
    val t2 = t * t
    val t3 = t2 * t
    fun c(a: Float, b: Float, c0: Float, d: Float) =
        0.5f * ((2f * b) + (-a + c0) * t + (2f * a - 5f * b + 4f * c0 - d) * t2 + (-a + 3f * b - 3f * c0 + d) * t3)
    return c(p0.second, p1.second, p2.second, p3.second)
}

private fun shiftY(mesh: Mesh, dy: Float): Mesh {
    val pos = mesh.pos.copyOf()
    var i = 1
    while (i < pos.size) {
        pos[i] += dy
        i += 3
    }
    return Mesh(pos, mesh.nrm.copyOf(), mesh.tris)
}
