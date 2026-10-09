@file:OptIn(io.github.sceneview.ExperimentalSceneViewApi::class)

package com.simplegamegen.sudoku.ui.screens

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.gltfio.MaterialProvider
import com.simplegamegen.sudoku.duels.Mancala
import com.simplegamegen.sudoku.ui.assets.TableFrame
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.environment.rememberHDREnvironment
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
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
import com.google.android.filament.Camera as FilamentCamera

/**
 * Carved Kalah board and glass marbles. The camera matches [TableFrame], so the
 * count labels and tap ovals drawn on top of this view land on the pits.
 */
@Composable
internal fun MancalaWorld(
    frame: TableFrame,
    stones: List<StonePose>,
    playable: Boolean,
    legal: List<Int>,
    onSow: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val camera = rememberCameraNode(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
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
    val board = rememberModelInstance(modelLoader, "mancala/board.glb")
    val marbles = remember(modelLoader) {
        modelLoader.createInstancedModel("mancala/marble.glb", Mancala.STONES)
    }
    val materialLoader = rememberMaterialLoader(engine)
    val glass = remember(materialLoader) { MARBLE_COLORS.map { marbleGlass(materialLoader, it) } }
    val frameState = rememberUpdatedState(frame)
    val playableState = rememberUpdatedState(playable)
    val legalState = rememberUpdatedState(legal)
    val sowState = rememberUpdatedState(onSow)
    SceneView(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
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
            renderer.clearColor(0.0, 0.0, 0.0, 0.0)
            placeMancalaCamera(camera, frameState.value)
        },
        // TextureView takes the touches, so the pit ovals cannot live only on the Compose canvas.
        onTouchEvent = { event, _ ->
            if (event.action != MotionEvent.ACTION_UP || !playableState.value) false
            else {
                val fr = frameState.value
                val hit = MancalaBoard.holes.asReversed().firstOrNull { hole ->
                    hole.index in legalState.value && ovalHit(
                        fr, Offset(event.x, event.y), hole.x, hole.y, MancalaBoard.top,
                        hole.rx + 0.08f, hole.ry + 0.08f,
                    )
                }
                if (hit == null) false
                else {
                    sowState.value(hit.index)
                    true
                }
            }
        },
    ) {
        board?.let { ModelNode(modelInstance = it, autoAnimate = false) }
        stones.forEachIndexed { index, stone ->
            val instance = marbles.getOrNull(index) ?: return@forEachIndexed
            key(index) {
                ModelNode(
                    modelInstance = instance,
                    autoAnimate = false,
                    position = Position(stone.x, stone.z, stone.y),
                    scale = Scale(MancalaBoard.stoneRadius, MancalaBoard.stoneRadius, MancalaBoard.stoneRadius),
                    apply = { setMaterialInstance(glass[stone.tint % glass.size]) },
                )
            }
        }
    }
}

private fun placeMancalaCamera(camera: CameraNode, frame: TableFrame) {
    val lens = mancalaLens(frame) ?: return
    camera.position = Position(lens.eyeX, lens.eyeY, lens.eyeZ)
    camera.lookAt(
        Position(lens.eyeX + lens.forwardX, lens.eyeY + lens.forwardY, lens.eyeZ + lens.forwardZ),
        Direction(x = lens.upX, y = lens.upY, z = lens.upZ),
    )
    camera.setProjection(
        if (lens.ortho) FilamentCamera.Projection.ORTHO else FilamentCamera.Projection.PERSPECTIVE,
        lens.left,
        lens.right,
        lens.bottom,
        lens.top,
        lens.near,
        lens.far,
    )
}

private fun marbleGlass(materialLoader: MaterialLoader, color: Color): MaterialInstance {
    val key = MaterialProvider.MaterialKey().apply {
        hasTransmission = true
        hasIOR = true
        hasClearCoat = true
    }
    val instance = materialLoader.createUbershaderInstance(key, emptyList(), "marble", "")
    if (instance != null) {
        val names = instance.material.parameters.map { it.name }.toSet()
        fun set(name: String, value: Float) {
            if (name in names) instance.setParameter(name, value)
        }
        if ("baseColorFactor" in names) {
            instance.setParameter("baseColorFactor", color.red, color.green, color.blue, 1f)
        }
        set("roughnessFactor", 0.05f)
        set("metallicFactor", 0f)
        set("reflectance", 0.5f)
        set("transmissionFactor", 0.55f)
        set("ior", 1.5f)
        set("thicknessFactor", 0.2f)
        set("volumeThicknessFactor", 0.2f)
        set("clearCoatFactor", 1f)
        set("clearCoatRoughnessFactor", 0.04f)
        return instance
    }
    return materialLoader.createColorInstance(color, metallic = 0.04f, roughness = 0.08f, reflectance = 0.5f)
}

private fun Renderer.clearColor(r: Double, g: Double, b: Double, a: Double) {
    clearOptions = Renderer.ClearOptions().apply {
        clear = true
        clearColor = doubleArrayOf(r, g, b, a)
    }
}

private const val HDRI = "knife-flip/kiara_interior_1k.hdr"
private const val IBL_LUX = 18_000f
private const val KEY_LUX = 28_000f
private const val FILL_LUX = 10_000f
private val KEY_DIR = Direction(x = -0.62f, y = -0.72f, z = -0.38f)
private val FILL_DIR = Direction(x = 0.55f, y = -0.28f, z = 0.48f)
private val KEY_COLOR = io.github.sceneview.math.colorOf(r = 1f, g = 0.95f, b = 0.86f)
private val FILL_COLOR = io.github.sceneview.math.colorOf(r = 0.78f, g = 0.86f, b = 1f)

private val MARBLE_COLORS = listOf(
    Color(0xFFD4891A),
    Color(0xFF2A6FD0),
    Color(0xFF1E9A52),
    Color(0xFFD04458),
    Color(0xFF7A45C0),
    Color(0xFFE7F6F8),
)
