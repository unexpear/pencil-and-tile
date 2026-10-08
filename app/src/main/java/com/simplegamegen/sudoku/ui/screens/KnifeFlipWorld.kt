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
import kotlin.math.min
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
        intensity = 18_000f
        lightDirection = Direction(x = -0.35f, y = -1f, z = -0.25f)
        color = colorOf(r = 1f, g = 0.94f, b = 0.84f)
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
        fillLightNode = null,
        cameraNode = camera,
        cameraManipulator = null,
        onFrame = {
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
        intensity = 22_000f
        lightDirection = Direction(x = -0.4f, y = -1f, z = -0.45f)
        color = colorOf(r = 1f, g = 0.95f, b = 0.88f)
    }
    val hero = rememberModelInstance(modelLoader, if (body.bottle) "knife-flip/bottle.glb" else knifeAsset(body.id))
    val materialLoader = rememberMaterialLoader(engine)
    val plinth = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF7A5230), metallic = 0f, roughness = 0.55f)
    }
    val backdrop = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFC9BBA8), metallic = 0f, roughness = 0.85f)
    }
    val waterMaterial = remember(materialLoader) { makeWaterMaterial(materialLoader) }
    val bodyLiquid = remember(engine) {
        liquidGeometry(engine, cylinderLiquid(SHOULDER, 0f), cylinderIndices(WATER_SIDES))
    }
    val neckLiquid = remember(engine) {
        liquidGeometry(engine, neckVertices(NECK_LIMIT, 0f), neckIndices())
    }
    val theta = if (body.bottle) 0.22f else 1.15f
    val deg = tipDegrees(theta)
    val sample = Sample(0f, 0f, 0f, theta, 0f)
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
        fillLightNode = null,
        cameraNode = camera,
        cameraManipulator = null,
        onFrame = {
            renderer.clearColor(0.79, 0.73, 0.66)
            val eye = previewEye(body)
            camera.position = eye
            camera.lookAt(Position(y = body.length * if (body.bottle) 0.45f else 0.02f))
            camera.setProjection(fovInDegrees = 32.0, near = 0.02f, far = 20f)
        },
    ) {
        CubeNode(
            size = Size(x = 1.6f, y = 1.4f, z = 0.04f),
            position = Position(z = -0.55f, y = 0.35f),
            materialInstance = backdrop,
        )
        CubeNode(
            size = Size(x = 0.04f, y = 1.4f, z = 1.4f),
            position = Position(x = -0.7f, y = 0.35f),
            materialInstance = backdrop,
        )
        CubeNode(
            size = Size(x = 0.42f, y = 0.012f, z = 0.22f),
            position = Position(y = if (body.bottle) -0.006f else -body.length * 0.16f),
            materialInstance = plinth,
        )
        if (!body.bottle) {
            hero?.let {
                ModelNode(
                    modelInstance = it,
                    autoAnimate = false,
                    rotation = Rotation(z = deg),
                )
            }
        } else {
            Node(rotation = Rotation(z = deg)) {
                hero?.let { ModelNode(modelInstance = it, autoAnimate = false) }
                BottleWater(body, sample, waterMaterial, bodyLiquid, neckLiquid)
            }
        }
    }
}

internal fun placeKnifeCamera(camera: CameraNode, bottle: Boolean, view: BoardView, shake: Float) {
    val look = if (bottle) Position(x = 0.30f, y = 0.36f, z = 0f) else Position(x = 0.34f, y = 0.18f, z = 0f)
    val yaw = Math.toRadians(view.yaw.toDouble())
    val pitch = Math.toRadians(view.pitch.toDouble())
    val dist = (if (bottle) 0.72f else 0.48f) * view.distance / view.zoom.coerceIn(0.35f, 5f)
    val cp = cos(pitch).toFloat()
    val sp = sin(pitch).toFloat()
    val pan = dist / 900f
    camera.position = Position(
        x = look.x + dist * cp * sin(yaw).toFloat() + view.panX * pan + shake,
        y = look.y + dist * sp - view.panY * pan + shake * 0.35f,
        z = look.z + dist * cp * cos(yaw).toFloat(),
    )
    camera.lookAt(look)
    camera.setProjection(fovInDegrees = 36.0, near = 0.04f, far = 40f)
}

private fun previewEye(body: FlipBody): Position {
    val dist = body.length * if (body.bottle) 2.1f else 2.55f
    val yaw = Math.toRadians(54.0)
    val pitch = Math.toRadians(if (body.bottle) 16.0 else 20.0)
    val cp = cos(pitch).toFloat()
    return Position(
        x = dist * cp * sin(yaw).toFloat(),
        y = body.length * 0.15f + dist * sin(pitch).toFloat(),
        z = dist * cp * cos(yaw).toFloat(),
    )
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

/**
 * Radians of free-surface tilt that still fit in the glass.
 *
 * The column is not rotated. The top is a plane, so the walls stay at [radius]. The angle is
 * clipped so the low side stays above [floor] and the high side stays under [ceiling].
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
        Color(0xB34AA3C8),
        metallic = 0.02f,
        roughness = 0.08f,
        reflectance = 0.5f,
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
    SideEffect {
        bodyLiquid.setVertices(engine, cylinderLiquid(bodyTop, bodyTilt))
        neckLiquid.setVertices(engine, neckVertices(neckHeight, neckTilt))
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

private fun cylinderLiquid(top: Float, tiltRad: Float): List<Geometry.Vertex> {
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
        val yTop = top + x * slope
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

private fun neckVertices(height: Float, tiltRad: Float): List<Geometry.Vertex> {
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
            val y = if (tilted) y0 + x * slope else y0
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
            add(Geometry.Vertex(Position(x, topY + x * slope, z), capNormal))
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

private fun normalizeDir(v: Direction): Direction {
    val len = sqrt(v.x * v.x + v.y * v.y + v.z * v.z).coerceAtLeast(1e-6f)
    return Direction(x = v.x / len, y = v.y / len, z = v.z / len)
}
