@file:OptIn(io.github.sceneview.ExperimentalSceneViewApi::class)

package com.simplegamegen.sudoku.ui.screens

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.simplegamegen.sudoku.arcade.BottleModel
import com.simplegamegen.sudoku.arcade.FlipBody
import com.simplegamegen.sudoku.arcade.Sample
import com.simplegamegen.sudoku.ui.assets.BoardView
import com.google.android.filament.Engine
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneScope
import io.github.sceneview.SceneView
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.SurfaceType
import io.github.sceneview.environment.rememberHDREnvironment
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.math.colorOf
import io.github.sceneview.node.CameraNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberFillLightNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberRenderer
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Filament view of one Knife Flip pose.
 *
 * FlipPhysics owns the motion. This file only places the matching glTF (tip along +Y,
 * centre of mass at the origin; the bottle's origin is the centre of its base) from the
 * sample's position and theta. Theta 0 points the tip or cap up; increasing theta swings
 * that end toward +X, which is a negative rotation about Z.
 */
@Composable
internal fun KnifeFlipWorld(
    body: FlipBody,
    sample: Sample,
    effect: FlipEffect,
    view: BoardView,
    shake: Float,
    modifier: Modifier = Modifier,
    onToss: ((speed: Float, aim: Float) -> Unit)? = null,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val camera = rememberCameraNode(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    // Kiara lights the room and fills the reflections. The photograph is not the background.
    val environment = rememberHDREnvironment(environmentLoader, HDRI, createSkybox = false)
        ?: rememberEnvironment(environmentLoader)
    val renderer = rememberRenderer(engine)
    val light = rememberMainLightNode(engine) {
        intensity = KEY_LUX
        lightDirection = KEY_DIR
        color = KEY_COLOR
    }
    val fill = rememberFillLightNode(engine) {
        intensity = FILL_LUX
        lightDirection = FILL_DIR
        color = FILL_COLOR
    }
    val toss = remember { TossDrag() }
    val room = rememberModelInstance(modelLoader, "knife-flip/room.glb")
    val hero = rememberModelInstance(modelLoader, if (body.bottle) "knife-flip/bottle.glb" else knifeAsset(body.id))
    val materialLoader = rememberMaterialLoader(engine)
    val waterMaterial = remember(materialLoader) { makeWaterMaterial(materialLoader) }
    val bodyLiquid = remember(engine) {
        liquidGeometry(engine, cylinderLiquid(SHOULDER, 0f), cylinderIndices(WATER_SIDES))
    }
    val neckLiquid = remember(engine) {
        liquidGeometry(engine, neckVertices(NECK_LIMIT, 0f), neckIndices())
    }
    val chips = List(CHIP_COUNT) { i -> rememberModelInstance(modelLoader, CHIP_ASSETS[i % CHIP_ASSETS.size]) }

    val deg = tipDegrees(sample.theta)
    SceneView(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        environment = environment,
        renderer = renderer,
        renderQuality = RenderQuality.Default,
        autoCenterContent = false,
        autoFitContent = false,
        mainLightNode = light,
        fillLightNode = fill,
        cameraNode = camera,
        cameraManipulator = null,
        onFrame = {
            environment.indirectLight?.intensity = IBL_LUX
            renderer.clearColor(0.55, 0.51, 0.45)
            placeKnifeCamera(camera, body.bottle, view, shake)
        },
        // The TextureView consumes touches, so the swipe cannot live on a Compose pointerInput.
        onTouchEvent = { event, _ -> onToss?.let { toss.onTouch(event, it) } ?: false },
    ) {
        room?.let { ModelNode(modelInstance = it, autoAnimate = false) }
        if (!body.bottle) {
            hero?.let {
                ModelNode(
                    modelInstance = it,
                    autoAnimate = false,
                    position = Position(x = sample.x, y = sample.z, z = 0f),
                    rotation = Rotation(z = deg),
                )
            }
        } else {
            val base = bottleBase(body, sample)
            Node(position = base, rotation = Rotation(z = deg)) {
                hero?.let { ModelNode(modelInstance = it, autoAnimate = false) }
                BottleWater(body, sample, waterMaterial, bodyLiquid, neckLiquid)
            }
        }
        if (effect.age >= 0f && effect.kind == FlipJuice.CHIPS) {
            val tip = tipPosition(body, sample)
            for (i in 0 until CHIP_COUNT) {
                val pose = chipPose(i, effect.age, tip)
                if (pose.y < 0.004f) continue
                val splinter = chips[i]
                key(i) {
                    splinter?.let {
                        ModelNode(
                            modelInstance = it,
                            autoAnimate = false,
                            position = pose,
                            rotation = Rotation(
                                x = i * 47f,
                                y = effect.age * 140f + i * 33f,
                                z = effect.age * 220f + i * 28f,
                            ),
                            scale = Scale(
                                x = 0.85f + (i % 3) * 0.18f,
                                y = 0.7f + (i % 4) * 0.15f,
                                z = 0.9f + (i % 5) * 0.12f,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Studio close-up for the model picker. The selected knife or fill fills the frame. */
@Composable
internal fun KnifeFlipPreview(body: FlipBody, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberHDREnvironment(environmentLoader, HDRI, createSkybox = false)
        ?: rememberEnvironment(environmentLoader)
    val renderer = rememberRenderer(engine)
    val camera = rememberCameraNode(engine)
    val light = rememberMainLightNode(engine) {
        intensity = PICKER_KEY_LUX
        lightDirection = KEY_DIR
        color = KEY_COLOR
    }
    val fill = rememberFillLightNode(engine) {
        intensity = PICKER_FILL_LUX
        lightDirection = FILL_DIR
        color = FILL_COLOR
    }
    val hero = rememberModelInstance(modelLoader, if (body.bottle) "knife-flip/bottle.glb" else knifeAsset(body.id))
    val materialLoader = rememberMaterialLoader(engine)
    val board = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFD7B07A), metallic = 0f, roughness = 0.58f)
    }
    val contact = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0x8C1E140C), metallic = 0f, roughness = 1f)
    }
    val waterMaterial = remember(materialLoader) { makeWaterMaterial(materialLoader) }
    val bodyLiquid = remember(engine) {
        liquidGeometry(engine, cylinderLiquid(SHOULDER, 0f), cylinderIndices(WATER_SIDES))
    }
    val neckLiquid = remember(engine) {
        liquidGeometry(engine, neckVertices(NECK_LIMIT, 0f), neckIndices())
    }
    // Spine on top, edge resting on the shelf. The old -pi/2 pose put the belly on top.
    val pose = remember(body.id, body.bottle) { previewPose(body) }
    val deg = tipDegrees(pose.theta)
    val sample = Sample(0f, pose.lift, 0f, pose.theta, 0f)
    SceneView(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        environment = environment,
        renderer = renderer,
        renderQuality = RenderQuality.Cinematic,
        autoCenterContent = false,
        autoFitContent = false,
        mainLightNode = light,
        fillLightNode = fill,
        cameraNode = camera,
        cameraManipulator = null,
        onFrame = {
            environment.indirectLight?.intensity = IBL_LUX
            renderer.clearColor(0.27, 0.26, 0.25)
            val aspect = camera.getViewPortAspect().takeIf { it > 0.2 } ?: (614.0 / 640.0)
            val fit = previewCamera(pose, body.bottle, aspect)
            camera.position = fit.eye
            camera.lookAt(fit.look)
            camera.setProjection(fovInDegrees = fit.vfov, near = 0.02f, far = 20f)
        },
    ) {
        CubeNode(
            size = Size(x = pose.boardWidth, y = 0.016f, z = pose.boardDepth),
            position = Position(x = pose.centerX, y = -0.008f, z = pose.centerZ),
            materialInstance = board,
        )
        if (!body.bottle) {
            CubeNode(
                size = Size(x = pose.boardWidth * 0.72f, y = 0.0012f, z = pose.boardDepth * 0.62f),
                position = Position(x = pose.centerX, y = 0.0011f, z = pose.centerZ),
                materialInstance = contact,
            )
        }
        if (!body.bottle) {
            hero?.let {
                ModelNode(
                    modelInstance = it,
                    autoAnimate = false,
                    position = Position(y = pose.lift),
                    rotation = Rotation(z = deg),
                )
            }
        } else {
            Node(position = Position(y = pose.lift), rotation = Rotation(z = deg)) {
                hero?.let { ModelNode(modelInstance = it, autoAnimate = false) }
                BottleWater(body, sample, waterMaterial, bodyLiquid, neckLiquid)
            }
        }
    }
}

internal fun placeKnifeCamera(camera: CameraNode, bottle: Boolean, view: BoardView, shake: Float) {
    val look = KnifeStage.look(bottle, view.name)
    val yaw = Math.toRadians(view.yaw.toDouble())
    val pitch = Math.toRadians(view.pitch.toDouble())
    val dist = view.distance / view.zoom.coerceIn(0.35f, 5f)
    val cp = cos(pitch).toFloat()
    val sp = sin(pitch).toFloat()
    val pan = dist / 900f
    camera.position = Position(
        x = look.x + dist * cp * sin(yaw).toFloat() + view.panX * pan + shake,
        y = look.y + dist * sp - view.panY * pan + shake * 0.35f,
        z = look.z + dist * cp * cos(yaw).toFloat(),
    )
    camera.lookAt(look)
    camera.setProjection(fovInDegrees = KnifeStage.PLAY_FOV, near = 0.04f, far = 40f)
}

/** Local glTF bounds. Y runs tip to handle, X is the edge, Z is the thickness. */
private fun meshBounds(body: FlipBody): FloatArray = when {
    body.bottle -> floatArrayOf(-0.0315f, 0f, -0.0315f, 0.0315f, 0.204f, 0.0315f)
    body.id == "chef" -> floatArrayOf(-0.048f, -0.13423f, -0.0066f, 0.000f, 0.19577f, 0.0066f)
    body.id == "pocket" -> floatArrayOf(-0.02845f, -0.07961f, -0.0082f, -0.0004f, 0.12039f, 0.008f)
    body.id == "butterfly" -> floatArrayOf(-0.027f, -0.11058f, -0.0074f, 0.002f, 0.13742f, 0.0074f)
    body.id == "cleaver" -> floatArrayOf(-0.090f, -0.18222f, -0.0064f, 0.000f, 0.11778f, 0.0064f)
    else -> floatArrayOf(-0.01378f, -0.14f, -0.0025f, 0.01378f, 0.14f, 0.0025f)
}

internal class PreviewPose(
    val theta: Float,
    val lift: Float,
    val centerX: Float,
    val centerZ: Float,
    val minZ: Float,
    val boardWidth: Float,
    val boardDepth: Float,
    val panelWidth: Float,
    val panelHeight: Float,
    val points: List<Position>,
)

internal class PreviewFit(val eye: Position, val look: Position, val vfov: Double)

/** Board corners. The picker camera has to keep this shelf inside the frame. */
private fun shelfPoints(pose: PreviewPose): List<Position> {
    val hx = pose.boardWidth / 2f
    val hz = pose.boardDepth / 2f
    val out = ArrayList<Position>(8)
    for (x in floatArrayOf(pose.centerX - hx, pose.centerX + hx)) {
        for (y in floatArrayOf(0f, -0.016f)) {
            for (z in floatArrayOf(pose.centerZ - hz, pose.centerZ + hz)) {
                out.add(Position(x, y, z))
            }
        }
    }
    return out
}

internal fun previewPose(body: FlipBody): PreviewPose {
    val box = meshBounds(body)
    // +X is the edge. +pi/2 puts that edge on the shelf and the spine on top.
    val theta = if (body.bottle) 0.22f else (PI / 2).toFloat()
    val clearance = if (body.bottle) 0.004f else 0.0008f
    var lowest = Float.POSITIVE_INFINITY
    val xs = floatArrayOf(box[0], box[3])
    val ys = floatArrayOf(box[1], box[4])
    for (x in xs) for (y in ys) {
        val wy = (-sin(theta) * x + cos(theta) * y)
        if (wy < lowest) lowest = wy
    }
    val lift = clearance - lowest
    val points = ArrayList<Position>(8)
    for (x in xs) for (y in ys) for (z in floatArrayOf(box[2], box[5])) {
        points.add(
            Position(
                x = cos(theta) * x + sin(theta) * y,
                y = -sin(theta) * x + cos(theta) * y + lift,
                z = z,
            ),
        )
    }
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    for (p in points) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
        if (p.z < minZ) minZ = p.z
        if (p.z > maxZ) maxZ = p.z
    }
    val spanX = maxX - minX
    val spanZ = maxZ - minZ
    return PreviewPose(
        theta = theta,
        lift = lift,
        centerX = (minX + maxX) / 2f,
        centerZ = (minZ + maxZ) / 2f,
        minZ = minZ,
        boardWidth = spanX + 0.018f,
        boardDepth = 0.040f,
        panelWidth = spanX + 0.13f,
        panelHeight = maxY + 0.08f,
        points = points,
    )
}

/**
 * Orbit distance from the posed bounds and the field of view.
 * Knives target about 80% of the frame width, with the shelf inside the frame.
 * Bottles target 70% of the height.
 * [aspect] is width / height. The horizontal field stays 28°.
 */
internal fun previewCamera(pose: PreviewPose, bottle: Boolean, aspect: Double): PreviewFit {
    val hfov = Math.toRadians(28.0)
    val vfov = 2.0 * atan(tan(hfov / 2.0) / aspect)
    val tanH = tan(hfov / 2.0)
    val tanV = tan(vfov / 2.0)
    val yaw = Math.toRadians(if (bottle) 28.0 else 14.0)
    val pitch = Math.toRadians(if (bottle) 12.0 else 8.0)
    val targetW = if (bottle) 0.55 else 0.84
    val targetH = if (bottle) 0.70 else 0.92
    val fitted = if (bottle) pose.points else pose.points + shelfPoints(pose)
    var lookX = 0.0
    var lookY = 0.0
    var lookZ = 0.0
    var minX = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    var minZ = Double.POSITIVE_INFINITY
    var maxZ = Double.NEGATIVE_INFINITY
    for (p in fitted) {
        if (p.x < minX) minX = p.x.toDouble()
        if (p.x > maxX) maxX = p.x.toDouble()
        if (p.y < minY) minY = p.y.toDouble()
        if (p.y > maxY) maxY = p.y.toDouble()
        if (p.z < minZ) minZ = p.z.toDouble()
        if (p.z > maxZ) maxZ = p.z.toDouble()
    }
    lookX = (minX + maxX) / 2.0
    lookY = (minY + maxY) / 2.0
    lookZ = (minZ + maxZ) / 2.0
    var dist = 0.6
    repeat(5) {
        var lo = 0.08
        var hi = 4.0
        repeat(22) {
            dist = (lo + hi) / 2.0
            val span = projectSpan(fitted, lookX, lookY, lookZ, yaw, pitch, dist, tanH, tanV)
            if (span == null || span[0] > targetW || span[1] > targetH) lo = dist else hi = dist
        }
        dist = hi
        val span = projectSpan(fitted, lookX, lookY, lookZ, yaw, pitch, dist, tanH, tanV) ?: return@repeat
        val depth = meanDepth(fitted, lookX, lookY, lookZ, yaw, pitch, dist)
        val shift = recenter(lookX, lookY, lookZ, yaw, pitch, dist, span[2], span[3], tanH, tanV, depth)
        lookX += shift[0]
        lookY += shift[1]
        lookZ += shift[2]
    }
    val cp = cos(pitch)
    val sp = sin(pitch)
    return PreviewFit(
        eye = Position(
            x = (lookX + dist * cp * sin(yaw)).toFloat(),
            y = (lookY + dist * sp).toFloat(),
            z = (lookZ + dist * cp * cos(yaw)).toFloat(),
        ),
        look = Position(lookX.toFloat(), lookY.toFloat(), lookZ.toFloat()),
        vfov = Math.toDegrees(vfov),
    )
}

private fun projectSpan(
    points: List<Position>,
    lookX: Double,
    lookY: Double,
    lookZ: Double,
    yaw: Double,
    pitch: Double,
    dist: Double,
    tanH: Double,
    tanV: Double,
): DoubleArray? {
    val eye = orbit(lookX, lookY, lookZ, yaw, pitch, dist)
    val basis = cameraBasis(eye, doubleArrayOf(lookX, lookY, lookZ))
    var minX = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    for (p in points) {
        val rx = p.x - eye[0]
        val ry = p.y - eye[1]
        val rz = p.z - eye[2]
        val depth = rx * basis[6] + ry * basis[7] + rz * basis[8]
        if (depth <= 0.02) return null
        val ndcX = ((rx * basis[0] + ry * basis[1] + rz * basis[2]) / depth) / tanH
        val ndcY = ((rx * basis[3] + ry * basis[4] + rz * basis[5]) / depth) / tanV
        if (ndcX < minX) minX = ndcX
        if (ndcX > maxX) maxX = ndcX
        if (ndcY < minY) minY = ndcY
        if (ndcY > maxY) maxY = ndcY
    }
    val cx = (minX + maxX) / 2.0
    val cy = (minY + maxY) / 2.0
    return doubleArrayOf((maxX - minX) / 2.0, (maxY - minY) / 2.0, cx, cy)
}

private fun meanDepth(
    points: List<Position>,
    lookX: Double,
    lookY: Double,
    lookZ: Double,
    yaw: Double,
    pitch: Double,
    dist: Double,
): Double {
    val eye = orbit(lookX, lookY, lookZ, yaw, pitch, dist)
    val basis = cameraBasis(eye, doubleArrayOf(lookX, lookY, lookZ))
    var sum = 0.0
    for (p in points) {
        sum += (p.x - eye[0]) * basis[6] + (p.y - eye[1]) * basis[7] + (p.z - eye[2]) * basis[8]
    }
    return sum / points.size
}

private fun recenter(
    lookX: Double,
    lookY: Double,
    lookZ: Double,
    yaw: Double,
    pitch: Double,
    dist: Double,
    cx: Double,
    cy: Double,
    tanH: Double,
    tanV: Double,
    depth: Double,
): DoubleArray {
    val eye = orbit(lookX, lookY, lookZ, yaw, pitch, dist)
    val basis = cameraBasis(eye, doubleArrayOf(lookX, lookY, lookZ))
    val sx = cx * tanH * depth
    val sy = cy * tanV * depth
    return doubleArrayOf(
        basis[0] * sx + basis[3] * sy,
        basis[1] * sx + basis[4] * sy,
        basis[2] * sx + basis[5] * sy,
    )
}

private fun orbit(lookX: Double, lookY: Double, lookZ: Double, yaw: Double, pitch: Double, dist: Double): DoubleArray {
    val cp = cos(pitch)
    val sp = sin(pitch)
    return doubleArrayOf(
        lookX + dist * cp * sin(yaw),
        lookY + dist * sp,
        lookZ + dist * cp * cos(yaw),
    )
}

/** right xyz, up xyz, view xyz. */
private fun cameraBasis(eye: DoubleArray, look: DoubleArray): DoubleArray {
    var vx = look[0] - eye[0]
    var vy = look[1] - eye[1]
    var vz = look[2] - eye[2]
    val vl = sqrt(vx * vx + vy * vy + vz * vz)
    vx /= vl; vy /= vl; vz /= vl
    // right = view × worldUp
    var rx = vy * 0 - vz * 1
    var ry = vz * 0 - vx * 0
    var rz = vx * 1 - vy * 0
    val rl = sqrt(rx * rx + ry * ry + rz * rz)
    rx /= rl; ry /= rl; rz /= rl
    val ux = ry * vz - rz * vy
    val uy = rz * vx - rx * vz
    val uz = rx * vy - ry * vx
    return doubleArrayOf(rx, ry, rz, ux, uy, uz, vx, vy, vz)
}

private fun knifeAsset(id: String): String = when (id) {
    "chef" -> "knife-flip/chef.glb"
    "throwing" -> "knife-flip/throwing.glb"
    "pocket" -> "knife-flip/pocket.glb"
    "butterfly" -> "knife-flip/butterfly.glb"
    "cleaver" -> "knife-flip/cleaver.glb"
    else -> "knife-flip/throwing.glb"
}

private fun tipDegrees(theta: Float) = Math.toDegrees((-theta).toDouble()).toFloat()

private fun bottleBase(body: FlipBody, sample: Sample): Position {
    val h = body.comFromBase(sample.sigma)
    return Position(
        x = sample.x - h * sin(sample.theta),
        y = sample.z - h * cos(sample.theta),
        z = 0f,
    )
}

private fun waterHeight(body: FlipBody, sample: Sample) =
    BottleModel.waterHeight(body.fill, sample.sigma)

/** A full bottle has no air gap, so the surface stays level. */
private fun requestedTilt(body: FlipBody, sample: Sample): Float {
    if (body.mobility < 0.02f) return 0f
    return sample.sigma * (1f - body.fill) * 28f
}

private fun tipPosition(body: FlipBody, sample: Sample): Position {
    val reach = body.comFromTip(sample.sigma)
    return Position(
        x = sample.x + reach * sin(sample.theta),
        y = sample.z + reach * cos(sample.theta),
        z = 0f,
    )
}

/** Upward swipe, matching the old Canvas gesture: screen Y grows downward. */
private class TossDrag {
    private var originX = 0f
    private var originY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var started = 0L
    private var tracking = false

    fun onTouch(event: MotionEvent, onToss: (Float, Float) -> Unit): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                originX = event.x
                originY = event.y
                lastX = event.x
                lastY = event.y
                started = System.nanoTime()
                tracking = true
            }
            MotionEvent.ACTION_MOVE -> if (tracking) {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                tracking = false
                val dy = originY - lastY
                if (dy < 48f) return false
                val dx = lastX - originX
                val dt = ((System.nanoTime() - started) / 1_000_000_000f).coerceIn(0.06f, 0.4f)
                val speed = (hypot(dx.toDouble(), dy.toDouble()) / dt / 620f).toFloat().coerceIn(0.7f, 3.6f)
                val aim = atan2(dy.toDouble(), abs(dx).toDouble().coerceAtLeast(12.0)).toFloat().coerceIn(0.9f, 1.5f)
                onToss(speed, aim)
            }
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return false
    }
}

private fun chipPose(i: Int, age: Float, tip: Position): Position {
    val ang = i * 0.85f
    val speed = 0.16f + (i % 4) * 0.045f
    val y = tip.y + (0.22f + (i % 3) * 0.06f) * age - 1.6f * age * age
    return Position(
        x = tip.x + cos(ang) * speed * age,
        y = y,
        z = tip.z + sin(ang) * 0.07f * age,
    )
}

private const val HDRI = "knife-flip/kiara_interior_1k.hdr"

/**
 * Lux for the Kiara IBL, the key, and the fill. tools/knife-flip/assemble_stills.py writes the
 * same numbers into the headless batch, so the stills match the app.
 */
private const val IBL_LUX = 22_000f
private const val KEY_LUX = 42_000f
private const val FILL_LUX = 15_000f
private const val PICKER_KEY_LUX = 48_000f
private const val PICKER_FILL_LUX = 18_000f
private val KEY_DIR = Direction(x = -0.28f, y = -1f, z = -0.62f)
private val FILL_DIR = Direction(x = 0.45f, y = -0.4f, z = 0.55f)
private val KEY_COLOR = colorOf(r = 1f, g = 0.96f, b = 0.90f)
private val FILL_COLOR = colorOf(r = 0.82f, g = 0.88f, b = 1f)
/** How far the water climbs the glass. The centre of the surface stays at the fill height. */
private const val MENISCUS = 0.0028f
private const val SHOULDER = 0.148f
/** Water stops here, under the cap, so the plane cut cannot leave the neck. */
private const val NECK_LIMIT = 0.040f
private const val WATER_RADIUS = 0.0292f
private const val WATER_SIDES = 24
private const val LATHE_RINGS = 5
private const val LATHE_SIDES = 18
private const val CHIP_COUNT = 12
private val CHIP_ASSETS = arrayOf(
    "knife-flip/chip0.glb",
    "knife-flip/chip1.glb",
    "knife-flip/chip2.glb",
    "knife-flip/chip3.glb",
)

private fun Renderer.clearColor(r: Double, g: Double, b: Double) {
    clearOptions = Renderer.ClearOptions().apply {
        clear = true
        clearColor = doubleArrayOf(r, g, b, 1.0)
    }
}

private fun makeWaterMaterial(materialLoader: MaterialLoader): MaterialInstance =
    materialLoader.createColorInstance(
        // Opaque. A transparent volume is skipped by Filament's glass refraction and the bottle looks empty.
        Color(0xFF3EAFDF),
        metallic = 0f,
        roughness = 0.16f,
        reflectance = 0.35f,
    )

/** Vertical water with a plane-cut top. The walls stay put, so the tilt cannot leave the bottle. */
@Composable
private fun SceneScope.BottleWater(
    body: FlipBody,
    sample: Sample,
    material: MaterialInstance,
    bodyLiquid: Geometry,
    neckLiquid: Geometry,
) {
    val height = waterHeight(body, sample)
    val inNeck = height > SHOULDER + 0.004f
    val bodyTop = if (inNeck) SHOULDER else height.coerceAtLeast(0.008f)
    val bodyTilt = if (inNeck) 0f else clippedLiquidTilt(
        requestedTilt(body, sample), bodyTop, WATER_RADIUS, 0.004f, SHOULDER,
    )
    val neckHeight = (height - SHOULDER).coerceIn(0.004f, NECK_LIMIT)
    val neckTilt = if (!inNeck) 0f else clippedLiquidTilt(
        requestedTilt(body, sample), neckHeight, neckRadius(neckHeight), 0.003f, NECK_LIMIT,
    )
    val bodyMeniscus = if (inNeck) 0f else (SHOULDER - bodyTop).coerceIn(0f, MENISCUS)
    val neckMeniscus = if (!inNeck) 0f else (NECK_LIMIT - neckHeight).coerceIn(0f, 0.0016f)
    SideEffect {
        bodyLiquid.setVertices(engine, cylinderLiquid(bodyTop, bodyTilt, bodyMeniscus))
        neckLiquid.setVertices(engine, neckVertices(neckHeight, neckTilt, neckMeniscus))
    }
    MeshNode(
        primitiveType = RenderableManager.PrimitiveType.TRIANGLES,
        vertexBuffer = bodyLiquid.vertexBuffer,
        indexBuffer = bodyLiquid.indexBuffer,
        materialInstance = material,
    )
    if (inNeck) {
        Node(position = Position(y = SHOULDER)) {
            MeshNode(
                primitiveType = RenderableManager.PrimitiveType.TRIANGLES,
                vertexBuffer = neckLiquid.vertexBuffer,
                indexBuffer = neckLiquid.indexBuffer,
                materialInstance = material,
            )
        }
    }
}

private fun liquidGeometry(engine: Engine, vertices: List<Geometry.Vertex>, indices: List<Int>): Geometry =
    Geometry.Builder().vertices(vertices).indices(indices).build(engine)

private fun cylinderLiquid(top: Float, tiltRad: Float, meniscus: Float = MENISCUS): List<Geometry.Vertex> {
    val slope = tan(tiltRad)
    val capNormal = normalizeDir(Direction(x = -slope, y = 1f, z = 0f))
    val lower = ArrayList<Geometry.Vertex>()
    val lowerCap = ArrayList<Geometry.Vertex>()
    val upper = ArrayList<Geometry.Vertex>()
    val upperCap = ArrayList<Geometry.Vertex>()
    for (side in 0..WATER_SIDES) {
        val theta = side * (Math.PI * 2.0 / WATER_SIDES).toFloat()
        val x = WATER_RADIUS * cos(theta)
        val z = WATER_RADIUS * sin(theta)
        val radial = normalizeDir(Direction(x = x, y = 0f, z = z))
        val bottom = Position(x, 0.001f, z)
        val yTop = top + x * slope + meniscusLift(x, z, WATER_RADIUS, meniscus)
        val crest = Position(x, yTop, z)
        lower += Geometry.Vertex(bottom, radial)
        lowerCap += Geometry.Vertex(bottom, Direction(y = -1f))
        upper += Geometry.Vertex(crest, radial)
        upperCap += Geometry.Vertex(crest, capNormal)
    }
    return buildList {
        addAll(lower)
        addAll(upper)
        add(Geometry.Vertex(Position(y = 0.001f), Direction(y = -1f)))
        addAll(lowerCap)
        add(Geometry.Vertex(Position(y = top), capNormal))
        addAll(upperCap)
    }
}

private fun cylinderIndices(sideCount: Int): List<Int> = buildList {
    for (side in 0 until sideCount) {
        val topLeft = side + sideCount + 1
        val topRight = topLeft + 1
        val lowerCenter = 2 * (sideCount + 1)
        val upperCenter = lowerCenter + sideCount + 2
        add(side); add(topRight); add(side + 1)
        add(side); add(topLeft); add(topRight)
        add(lowerCenter); add(lowerCenter + side + 1); add(lowerCenter + side + 2)
        add(upperCenter); add(upperCenter + side + 2); add(upperCenter + side + 1)
    }
}

private fun neckVertices(height: Float, tiltRad: Float, meniscus: Float = 0.0016f): List<Geometry.Vertex> {
    val slope = tan(tiltRad)
    val capNormal = normalizeDir(Direction(x = -slope, y = 1f, z = 0f))
    val stride = LATHE_SIDES + 1
    val rings = List(LATHE_RINGS) { i ->
        val y = height * i / (LATHE_RINGS - 1)
        y to neckRadius(y)
    }
    val wall = ArrayList<Geometry.Vertex>()
    rings.forEachIndexed { index, (y0, radius) ->
        val tilted = index == rings.lastIndex
        for (side in 0..LATHE_SIDES) {
            val theta = side * (Math.PI * 2.0 / LATHE_SIDES).toFloat()
            val x = radius * cos(theta)
            val z = radius * sin(theta)
            val y = if (tilted) y0 + x * slope + meniscusLift(x, z, radius, meniscus) else y0
            wall += Geometry.Vertex(Position(x, y, z), normalizeDir(Direction(x = x, y = 0f, z = z)))
        }
    }
    val bottomR = rings.first().second
    val topY = rings.last().first
    val topR = rings.last().second
    return buildList {
        addAll(wall)
        add(Geometry.Vertex(Position(y = 0f), Direction(y = -1f)))
        for (side in 0..LATHE_SIDES) {
            val theta = side * (Math.PI * 2.0 / LATHE_SIDES).toFloat()
            add(Geometry.Vertex(Position(bottomR * cos(theta), 0f, bottomR * sin(theta)), Direction(y = -1f)))
        }
        add(Geometry.Vertex(Position(y = topY), capNormal))
        for (side in 0..LATHE_SIDES) {
            val theta = side * (Math.PI * 2.0 / LATHE_SIDES).toFloat()
            val x = topR * cos(theta)
            val z = topR * sin(theta)
            add(Geometry.Vertex(Position(x, topY + x * slope + meniscusLift(x, z, topR, meniscus), z), capNormal))
        }
    }.also {
        check(wall.size == LATHE_RINGS * stride)
    }
}

private fun neckIndices(): List<Int> = buildList {
    val stride = LATHE_SIDES + 1
    for (ring in 0 until LATHE_RINGS - 1) {
        for (side in 0 until LATHE_SIDES) {
            val a = ring * stride + side
            val c = a + stride
            add(a); add(c + 1); add(a + 1)
            add(a); add(c); add(c + 1)
        }
    }
    val lowerCenter = LATHE_RINGS * stride
    val upperCenter = lowerCenter + stride + 1
    for (side in 0 until LATHE_SIDES) {
        add(lowerCenter); add(lowerCenter + side + 1); add(lowerCenter + side + 2)
        add(upperCenter); add(upperCenter + side + 2); add(upperCenter + side + 1)
    }
}

private fun neckRadius(y: Float): Float {
    val samples = floatArrayOf(0f, 0.0292f, 0.018f, 0.0162f, 0.034f, 0.0112f, 0.040f, 0.0106f)
    val yy = y.coerceIn(0f, NECK_LIMIT)
    var i = 0
    while (i + 2 < samples.size && samples[i + 2] < yy) i += 2
    val y0 = samples[i]
    val r0 = samples[i + 1]
    val y1 = samples[i + 2]
    val r1 = samples[i + 3]
    val t = if (y1 == y0) 0f else (yy - y0) / (y1 - y0)
    return r0 + (r1 - r0) * t
}

/** Concave meniscus: zero on the axis, [meniscus] at [radius]. */
private fun meniscusLift(x: Float, z: Float, radius: Float, meniscus: Float): Float {
    if (meniscus <= 0f || radius <= 1e-4f) return 0f
    val t = ((x * x + z * z) / (radius * radius)).coerceIn(0f, 1f)
    return meniscus * t
}

private fun normalizeDir(v: Direction): Direction {
    val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z).coerceAtLeast(1e-6f)
    return Direction(x = v.x / len, y = v.y / len, z = v.z / len)
}
