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

    val chef: Mesh = knife(0.0045f, outline(FlipModels.chef, chefShape))
    val throwing: Mesh = knife(0.0042f, outline(FlipModels.throwing, throwingShape))
    val pocket: Mesh = knife(0.0055f, outline(FlipModels.pocket, pocketShape))
    val butterfly: Mesh = knife(0.0048f, outline(FlipModels.butterfly, butterflyShape))
    val cleaver: Mesh = knife(0.0052f, outline(FlipModels.cleaver, cleaverShape))

    val bottle: Mesh = latheMesh(bottleBody, segments = 24)
    val cap: Mesh = latheMesh(bottleCap, segments = 20)

    private val water: List<List<Mesh>> = listOf(0.25f, 1f / 3f, 0.5f, 0.75f, 1f).map { fill ->
        listOf(0f, 0.5f, 1f).map { sigma -> waterMesh(fill, sigma) }
    }

    fun knife(body: FlipBody): Mesh = when (body.id) {
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

    private fun knife(half: Float, outline: List<Pair<Float, Float>>): Mesh =
        carvedMesh(outline, half, bevel = 0.0011f)

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

/** Side view: across the blade, then distance from the point. Local +z is the point, and the origin is the balance. */
private fun outline(body: FlipBody, shape: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    val com = body.comFromTip(0f)
    return shape.map { (across, fromTip) -> across to (com - fromTip) }
}

private val chefShape = listOf(
    0.000f to 0.000f,
    0.012f to 0.028f,
    0.024f to 0.095f,
    0.028f to 0.165f,
    0.016f to 0.205f,
    0.013f to 0.245f,
    0.014f to 0.310f,
    0.000f to 0.330f,
    -0.014f to 0.310f,
    -0.013f to 0.245f,
    -0.009f to 0.205f,
    -0.005f to 0.120f,
    -0.002f to 0.035f,
)

private val throwingShape = listOf(
    0.000f to 0.000f,
    0.012f to 0.055f,
    0.009f to 0.130f,
    0.013f to 0.185f,
    0.011f to 0.250f,
    0.000f to 0.280f,
    -0.011f to 0.250f,
    -0.013f to 0.185f,
    -0.009f to 0.130f,
    -0.012f to 0.055f,
)

private val pocketShape = listOf(
    0.000f to 0.000f,
    0.008f to 0.035f,
    0.006f to 0.082f,
    0.015f to 0.098f,
    0.017f to 0.155f,
    0.013f to 0.192f,
    0.000f to 0.200f,
    -0.013f to 0.192f,
    -0.017f to 0.155f,
    -0.011f to 0.098f,
    -0.003f to 0.040f,
)

private val butterflyShape = listOf(
    0.000f to 0.000f,
    0.009f to 0.040f,
    0.007f to 0.105f,
    0.012f to 0.125f,
    0.008f to 0.165f,
    0.013f to 0.190f,
    0.011f to 0.238f,
    0.000f to 0.250f,
    -0.011f to 0.238f,
    -0.013f to 0.190f,
    -0.008f to 0.165f,
    -0.010f to 0.125f,
    -0.004f to 0.045f,
)

private val cleaverShape = listOf(
    0.002f to 0.000f,
    0.042f to 0.018f,
    0.050f to 0.090f,
    0.044f to 0.150f,
    0.018f to 0.172f,
    0.015f to 0.220f,
    0.016f to 0.282f,
    0.000f to 0.295f,
    -0.014f to 0.282f,
    -0.012f to 0.220f,
    -0.006f to 0.172f,
    -0.004f to 0.030f,
)
