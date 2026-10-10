package com.simplegamegen.sudoku.ui.screens

import androidx.compose.ui.geometry.Offset
import com.simplegamegen.sudoku.ui.assets.TableFrame

/**
 * Filament frustum that matches [TableFrame.at].
 *
 * Board space is X across, Y toward the near side, Z up. Filament is Y-up, so a board point
 * (x, y, z) is placed at (x, z, y). [mancalaLens] is the glFrustum (or glOrtho) whose
 * screen pixels are [mancalaProject]. The unit test checks that against [TableFrame.at],
 * which is also what the tap ovals use.
 */
internal class MancalaLens(
    val ortho: Boolean,
    val left: Double,
    val right: Double,
    val bottom: Double,
    val top: Double,
    val near: Double,
    val far: Double,
    val eyeX: Float,
    val eyeY: Float,
    val eyeZ: Float,
    val forwardX: Float,
    val forwardY: Float,
    val forwardZ: Float,
    val upX: Float,
    val upY: Float,
    val upZ: Float,
)

internal fun mancalaLens(frame: TableFrame, near: Double = 0.08, far: Double = 80.0): MancalaLens? {
    val camera = frame.orbitCamera() ?: return null
    val width = frame.width.toDouble()
    val height = frame.height.toDouble()
    if (width < 2.0 || height < 2.0) return null
    val spanX: Double
    val spanY: Double
    if (camera.ortho) {
        spanX = width / camera.scale
        spanY = height / camera.scale
    } else {
        val pixelsPerUnit = (camera.focal * camera.scale).toDouble()
        if (pixelsPerUnit < 1.0) return null
        spanX = near * width / pixelsPerUnit
        spanY = near * height / pixelsPerUnit
    }
    val sumX = spanX * (1.0 - 2.0 * camera.ox / width)
    val sumY = spanY * (2.0 * camera.oy / height - 1.0)
    return MancalaLens(
        ortho = camera.ortho,
        left = (sumX - spanX) / 2.0,
        right = (sumX + spanX) / 2.0,
        bottom = (sumY - spanY) / 2.0,
        top = (sumY + spanY) / 2.0,
        near = near,
        far = far,
        eyeX = camera.camX,
        eyeY = camera.camZ,
        eyeZ = camera.camY,
        forwardX = camera.fx,
        forwardY = camera.fz,
        forwardZ = camera.fy,
        upX = camera.ux,
        upY = camera.uz,
        upZ = camera.uy,
    )
}

/** Where Filament should draw a board point, in the same pixels as [TableFrame.at]. */
internal fun mancalaProject(frame: TableFrame, x: Float, y: Float, z: Float): Offset? {
    val lens = mancalaLens(frame) ?: return null
    val camera = frame.orbitCamera() ?: return null
    val dx = x - camera.camX
    val dy = y - camera.camY
    val dz = z - camera.camZ
    val xEye = dx * camera.rx + dy * camera.ry + dz * camera.rz
    val yEye = dx * camera.ux + dy * camera.uy + dz * camera.uz
    val zEye = -(dx * camera.fx + dy * camera.fy + dz * camera.fz)
    val spanX = lens.right - lens.left
    val spanY = lens.top - lens.bottom
    val ndcX: Double
    val ndcY: Double
    if (lens.ortho) {
        ndcX = 2.0 * xEye / spanX - (lens.right + lens.left) / spanX
        ndcY = 2.0 * yEye / spanY - (lens.top + lens.bottom) / spanY
    } else {
        if (zEye >= -1e-4) return null
        ndcX = (2.0 * lens.near / spanX * xEye + (lens.right + lens.left) / spanX * zEye) / -zEye
        ndcY = (2.0 * lens.near / spanY * yEye + (lens.top + lens.bottom) / spanY * zEye) / -zEye
    }
    return Offset(
        ((ndcX * 0.5 + 0.5) * frame.width).toFloat(),
        ((0.5 - ndcY * 0.5) * frame.height).toFloat(),
    )
}
