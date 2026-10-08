@file:OptIn(io.github.sceneview.ExperimentalSceneViewApi::class)

package com.simplegamegen.sudoku.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
            renderer.clearColor(0.20, 0.13, 0.09)
            placeMancalaCamera(camera, frameState.value)
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
        set("roughnessFactor", 0.04f)
        set("metallicFactor", 0f)
        set("reflectance", 0.55f)
        set("transmissionFactor", 0.88f)
        set("ior", 1.5f)
        set("thicknessFactor", 0.12f)
        set("volumeThicknessFactor", 0.12f)
        return instance
    }
    return materialLoader.createColorInstance(color, metallic = 0.05f, roughness = 0.08f, reflectance = 0.55f)
}

private fun Renderer.clearColor(r: Double, g: Double, b: Double) {
    clearOptions = Renderer.ClearOptions().apply {
        clear = true
        clearColor = doubleArrayOf(r, g, b, 1.0)
    }
}

private const val HDRI = "knife-flip/kiara_interior_1k.hdr"
private const val IBL_LUX = 24_000f
private const val KEY_LUX = 38_000f
private const val FILL_LUX = 14_000f
private val KEY_DIR = Direction(x = -0.35f, y = -1f, z = -0.42f)
private val FILL_DIR = Direction(x = 0.55f, y = -0.35f, z = 0.4f)
private val KEY_COLOR = io.github.sceneview.math.colorOf(r = 1f, g = 0.95f, b = 0.86f)
private val FILL_COLOR = io.github.sceneview.math.colorOf(r = 0.78f, g = 0.86f, b = 1f)

private val MARBLE_COLORS = listOf(
    Color(0xFFE7A23A),
    Color(0xFF3C7FDB),
    Color(0xFF2EAE6A),
    Color(0xFFD4536A),
    Color(0xFF8E57C8),
    Color(0xFFD7EEF2),
)
