package com.simplegamegen.sudoku.ui.screens

import kotlin.math.atan
import kotlin.math.min

/**
 * Radians of free-surface tilt that still fit in the glass.
 *
 * The column is not rotated. The top is a plane, so the walls stay at [radius]. The angle is
 * clipped so the low side stays above [floor] and the high side stays under [ceiling].
 *
 * Kept out of [KnifeFlipWorld] so unit tests do not load SceneView's Java 21 math types.
 */
internal fun clippedLiquidTilt(
    requestedDegrees: Float,
    center: Float,
    radius: Float,
    floor: Float,
    ceiling: Float,
): Float {
    val room = min(center - floor, ceiling - center)
    if (requestedDegrees == 0f || room <= 0.001f || radius <= 0.001f) return 0f
    val maxRad = atan((room / radius).toDouble())
    val requested = Math.toRadians(requestedDegrees.toDouble())
    return requested.coerceIn(-maxRad, maxRad).toFloat()
}
