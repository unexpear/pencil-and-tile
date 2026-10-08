@file:OptIn(io.github.sceneview.ExperimentalSceneViewApi::class)

package com.simplegamegen.sudoku.ui.screens

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.simplegamegen.sudoku.arcade.BottleModel
import com.simplegamegen.sudoku.arcade.FlipBody
import com.simplegamegen.sudoku.arcade.Sample
import com.simplegamegen.sudoku.ui.assets.BoardView
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.environment.rememberHDREnvironment
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

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
    val environment = rememberHDREnvironment(environmentLoader, HDRI)
        ?: rememberEnvironment(environmentLoader)
    val light = rememberMainLightNode(engine) {
        intensity = 18_000f
        lightDirection = Direction(x = -0.35f, y = -1f, z = -0.25f)
        color = colorOf(r = 1f, g = 0.94f, b = 0.84f)
    }
    val toss = remember { TossDrag() }
    val room = rememberModelInstance(modelLoader, "knife-flip/room.glb")
    val hero = rememberModelInstance(modelLoader, if (body.bottle) "knife-flip/bottle.glb" else knifeAsset(body.id))
    val waterBody = rememberModelInstance(modelLoader, "knife-flip/water_body.glb")
    val waterNeck = rememberModelInstance(modelLoader, "knife-flip/water_neck.glb")
    val materialLoader = rememberMaterialLoader(engine)
    val chipMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF8A5A32), metallic = 0f, roughness = 0.62f)
    }
    val chipDark = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF6B4224), metallic = 0f, roughness = 0.7f)
    }

    val deg = tipDegrees(sample.theta)
    SceneView(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        environment = environment,
        renderQuality = RenderQuality.Default,
        autoCenterContent = false,
        autoFitContent = false,
        mainLightNode = light,
        fillLightNode = null,
        cameraNode = camera,
        cameraManipulator = null,
        onFrame = { placeKnifeCamera(camera, body.bottle, view, shake) },
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
                waterBody?.let {
                    ModelNode(
                        modelInstance = it,
                        autoAnimate = false,
                        scale = Scale(x = 1f, y = waterColumn(body, sample), z = 1f),
                        rotation = Rotation(z = waterTilt(body, sample)),
                    )
                }
                val neck = neckScale(body, sample)
                if (neck > 0.02f) {
                    waterNeck?.let {
                        ModelNode(
                            modelInstance = it,
                            autoAnimate = false,
                            position = Position(y = SHOULDER),
                            scale = Scale(x = 1f, y = neck, z = 1f),
                            rotation = Rotation(z = waterTilt(body, sample)),
                        )
                    }
                }
            }
        }
        if (effect.age >= 0f && effect.kind == FlipJuice.CHIPS) {
            val tip = tipPosition(body, sample)
            for (i in 0 until CHIP_COUNT) {
                val pose = chipPose(i, effect.age, tip)
                if (pose.y < 0.004f) continue
                key(i) {
                    CubeNode(
                        size = Size(x = 0.016f + (i % 3) * 0.004f, y = 0.004f, z = 0.007f + (i % 4) * 0.0015f),
                        position = pose,
                        rotation = Rotation(
                            x = i * 20f,
                            y = effect.age * 140f + i * 15f,
                            z = effect.age * 220f + i * 28f,
                        ),
                        materialInstance = if (i % 2 == 0) chipMaterial else chipDark,
                    )
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
    val environment = rememberHDREnvironment(environmentLoader, HDRI)
        ?: rememberEnvironment(environmentLoader)
    val camera = rememberCameraNode(engine)
    val light = rememberMainLightNode(engine) {
        intensity = 22_000f
        lightDirection = Direction(x = -0.4f, y = -1f, z = -0.45f)
        color = colorOf(r = 1f, g = 0.95f, b = 0.88f)
    }
    val hero = rememberModelInstance(modelLoader, if (body.bottle) "knife-flip/bottle.glb" else knifeAsset(body.id))
    val waterBody = rememberModelInstance(modelLoader, "knife-flip/water_body.glb")
    val waterNeck = rememberModelInstance(modelLoader, "knife-flip/water_neck.glb")
    val materialLoader = rememberMaterialLoader(engine)
    val plinth = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF7A5230), metallic = 0f, roughness = 0.55f)
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
        renderQuality = RenderQuality.Cinematic,
        autoCenterContent = false,
        autoFitContent = false,
        mainLightNode = light,
        fillLightNode = null,
        cameraNode = camera,
        cameraManipulator = null,
        onFrame = {
            val eye = previewEye(body)
            camera.position = eye
            camera.lookAt(Position(y = body.length * if (body.bottle) 0.45f else 0.02f))
            camera.setProjection(fovInDegrees = 32.0, near = 0.02f, far = 20f)
        },
    ) {
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
                waterBody?.let {
                    ModelNode(
                        modelInstance = it,
                        autoAnimate = false,
                        scale = Scale(x = 1f, y = waterColumn(body, sample), z = 1f),
                    )
                }
                val neck = neckScale(body, sample)
                if (neck > 0.02f) {
                    waterNeck?.let {
                        ModelNode(
                            modelInstance = it,
                            autoAnimate = false,
                            position = Position(y = SHOULDER),
                            scale = Scale(x = 1f, y = neck, z = 1f),
                        )
                    }
                }
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

/** Height of the cylindrical water, in metres. The mesh is 1 m tall, so this is also its Y scale. */
private fun waterColumn(body: FlipBody, sample: Sample): Float =
    min(BottleModel.waterHeight(body.fill, sample.sigma), SHOULDER)

/** How much of the neck mesh to show. 1 fills the exported neck. */
private fun neckScale(body: FlipBody, sample: Sample): Float {
    val extra = BottleModel.waterHeight(body.fill, sample.sigma) - SHOULDER
    return if (extra <= 0f) 0f else (extra / NECK_HEIGHT).coerceIn(0f, 1.4f)
}

/** A full bottle has no air gap, so the surface stays level. */
private fun waterTilt(body: FlipBody, sample: Sample): Float {
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
private const val NECK_HEIGHT = 0.056f
private const val CHIP_COUNT = 12
