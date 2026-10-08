package com.simplegamegen.sudoku.ui.assets

import com.simplegamegen.sudoku.arcade.FlipBody
import com.simplegamegen.sudoku.arcade.FlipModels

/**
 * Procedural knives and a water bottle, in metres. A knife's origin is its centre of mass and local +z
 * points at the tip. The bottle's origin is the centre of its base and local +z points at the cap.
 * [SQUARES_PER_METRE] is the scale used when the mesh is placed on the board.
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

    val chef: List<KnifePart> = listOf(
        KnifePart(slab(FlipModels.chef, chefBlade, 0.0018f), KnifeSurface.BLADE),
        KnifePart(slab(FlipModels.chef, chefBolster, 0.007f), KnifeSurface.BOLSTER),
        KnifePart(slab(FlipModels.chef, chefHandle, 0.012f), KnifeSurface.HANDLE),
    )
    val throwing: List<KnifePart> = listOf(
        KnifePart(slab(FlipModels.throwing, throwingBlade, 0.0032f), KnifeSurface.BLADE),
        KnifePart(slab(FlipModels.throwing, throwingTang, 0.0046f), KnifeSurface.TANG),
    )
    val pocket: List<KnifePart> = listOf(
        KnifePart(slab(FlipModels.pocket, pocketBlade, 0.0014f), KnifeSurface.BLADE),
        KnifePart(slab(FlipModels.pocket, pocketBolster, 0.006f), KnifeSurface.BOLSTER),
        KnifePart(slab(FlipModels.pocket, pocketScales, 0.011f), KnifeSurface.SCALES),
    )
    val butterfly: List<KnifePart> = listOf(
        KnifePart(slab(FlipModels.butterfly, butterflyBlade, 0.0016f), KnifeSurface.BLADE),
        KnifePart(shiftY(slab(FlipModels.butterfly, butterflyHandle, 0.004f), 0.007f), KnifeSurface.SCALES),
        KnifePart(shiftY(slab(FlipModels.butterfly, butterflyHandle, 0.004f), -0.007f), KnifeSurface.SCALES),
    )
    val cleaver: List<KnifePart> = listOf(
        KnifePart(cleaverBlade(), KnifeSurface.BLADE),
        KnifePart(slab(FlipModels.cleaver, cleaverHandle, 0.012f), KnifeSurface.HANDLE),
    )

    val bottle: Mesh = latheMesh(bottleBody, segments = 24)
    val cap: Mesh = latheMesh(bottleCap, segments = 20)

    private val water: List<List<Mesh>> = listOf(0.25f, 1f / 3f, 0.5f, 0.75f, 1f).map { fill ->
        listOf(0f, 0.5f, 1f).map { sigma -> waterMesh(fill, sigma) }
    }

    fun parts(body: FlipBody): List<KnifePart> = when (body.id) {
        "chef" -> chef
        "throwing" -> throwing
        "pocket" -> pocket
        "butterfly" -> butterfly
        "cleaver" -> cleaver
        else -> throwing
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

internal enum class KnifeSurface { BLADE, HANDLE, BOLSTER, TANG, SCALES }

internal class KnifePart(val mesh: Mesh, val surface: KnifeSurface)

/** Side view: across the blade, then distance from the point. Local +z is the point, and the origin is the balance. */
private fun outline(body: FlipBody, shape: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    val com = body.comFromTip(0f)
    return shape.map { (across, fromTip) -> across to (com - fromTip) }
}

private fun slab(body: FlipBody, shape: List<Pair<Float, Float>>, half: Float): Mesh =
    carvedMesh(outline(body, shape), half, bevel = (half * 0.22f).coerceAtMost(0.0025f))

private fun shiftY(mesh: Mesh, dy: Float): Mesh {
    val pos = mesh.pos.copyOf()
    var i = 1
    while (i < pos.size) {
        pos[i] += dy
        i += 3
    }
    return Mesh(pos, mesh.nrm.copyOf(), mesh.tris)
}

/** Tall rectangular blade with a hanging hole, origin at the cleaver's balance. */
private fun cleaverBlade(): Mesh {
    val com = FlipModels.cleaver.comFromTip(0f)
    val x0 = -0.008f
    val x1 = 0.092f
    val zTip = com - 0f
    val zHeel = com - 0.175f
    val holeFromTip = 0.048f
    val holeAcross = 0.062f
    return holedPlate(
        x0, zHeel, x1, zTip,
        half = 0.0022f,
        holeX = holeAcross, holeZ = com - holeFromTip, holeR = 0.011f,
    )
}

private val chefBlade = listOf(
    0.000f to 0.000f,
    0.010f to 0.022f,
    0.026f to 0.090f,
    0.032f to 0.160f,
    0.014f to 0.198f,
    -0.006f to 0.198f,
    -0.004f to 0.090f,
    -0.001f to 0.020f,
)

private val chefBolster = listOf(
    0.016f to 0.192f,
    0.018f to 0.214f,
    -0.014f to 0.214f,
    -0.012f to 0.192f,
)

private val chefHandle = listOf(
    0.013f to 0.210f,
    0.015f to 0.255f,
    0.014f to 0.318f,
    0.000f to 0.330f,
    -0.014f to 0.318f,
    -0.014f to 0.255f,
    -0.012f to 0.210f,
)

private val throwingBlade = listOf(
    0.000f to 0.000f,
    0.011f to 0.040f,
    0.010f to 0.130f,
    0.006f to 0.148f,
    -0.006f to 0.148f,
    -0.010f to 0.130f,
    -0.011f to 0.040f,
)

private val throwingTang = listOf(
    0.008f to 0.142f,
    0.014f to 0.175f,
    0.012f to 0.255f,
    0.000f to 0.280f,
    -0.012f to 0.255f,
    -0.014f to 0.175f,
    -0.008f to 0.142f,
)

private val pocketBlade = listOf(
    0.000f to 0.000f,
    0.007f to 0.028f,
    0.006f to 0.078f,
    0.002f to 0.092f,
    -0.003f to 0.078f,
    -0.001f to 0.020f,
)

private val pocketBolster = listOf(
    0.012f to 0.086f,
    0.014f to 0.108f,
    -0.010f to 0.108f,
    -0.008f to 0.086f,
)

private val pocketScales = listOf(
    0.016f to 0.102f,
    0.018f to 0.155f,
    0.014f to 0.192f,
    0.000f to 0.200f,
    -0.014f to 0.192f,
    -0.016f to 0.155f,
    -0.014f to 0.102f,
)

private val butterflyBlade = listOf(
    0.000f to 0.000f,
    0.008f to 0.030f,
    0.007f to 0.100f,
    0.003f to 0.118f,
    -0.004f to 0.100f,
    -0.002f to 0.025f,
)

private val butterflyHandle = listOf(
    0.010f to 0.122f,
    0.012f to 0.175f,
    0.010f to 0.235f,
    0.000f to 0.250f,
    -0.010f to 0.235f,
    -0.012f to 0.175f,
    -0.010f to 0.122f,
)

private val cleaverHandle = listOf(
    0.014f to 0.182f,
    0.016f to 0.240f,
    0.014f to 0.285f,
    0.000f to 0.295f,
    -0.014f to 0.285f,
    -0.014f to 0.240f,
    -0.012f to 0.182f,
)
