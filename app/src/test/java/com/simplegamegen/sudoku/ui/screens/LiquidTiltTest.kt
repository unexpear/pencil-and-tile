package com.simplegamegen.sudoku.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.tan

class LiquidTiltTest {
    @Test fun aLevelSurfaceStaysLevel() {
        assertEquals(0f, clippedLiquidTilt(0f, 0.07f, 0.0292f, 0.004f, 0.148f), 0f)
    }

    @Test fun theTiltStaysInsideTheGlass() {
        val center = 0.068f
        val radius = 0.0292f
        val floor = 0.004f
        val ceiling = 0.148f
        val tilt = clippedLiquidTilt(80f, center, radius, floor, ceiling)
        val rise = radius * abs(tan(tilt))
        assertTrue(center - rise >= floor - 1e-4f)
        assertTrue(center + rise <= ceiling + 1e-4f)
        // A modest slosh, the old 19 degree ask, still fits a third-full bottle.
        val modest = clippedLiquidTilt(19f, center, radius, floor, ceiling)
        assertEquals(Math.toRadians(19.0).toFloat(), modest, 0.01f)
    }

    @Test fun aBrimFullNeckCannotTiltOut() {
        assertEquals(0f, clippedLiquidTilt(18f, 0.040f, 0.0106f, 0.003f, 0.040f), 0f)
    }
}
