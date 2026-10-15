@file:OptIn(io.github.sceneview.ExperimentalSceneViewApi::class)

package com.simplegamegen.sudoku.ui.screens

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
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
import io.github.sceneview.math.Rotation
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
    /** Stones in each of the 14 holes, drawn as numbers set into the board. */
    counts: List<Int> = emptyList(),
    /** The hole the last stone landed in, ringed in pale wood. */
    last: Int? = null,
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
    // Gold inlay for the pits you can sow, and a paler ring for the last landing.
    val gold = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFFFD45A), metallic = 0f, roughness = 0.3f, reflectance = 0.5f) }
    val pale = remember(materialLoader) { materialLoader.createColorInstance(Color(0xFFF3E6C8), metallic = 0f, roughness = 0.45f, reflectance = 0.4f) }
    val numbers = remember { HashMap<Int, android.graphics.Bitmap>() }
    fun number(n: Int) = numbers.getOrPut(n) { countBitmap(n) }
    val frameState = rememberUpdatedState(frame)
    val playableState = rememberUpdatedState(playable)
    val legalState = rememberUpdatedState(legal)
    val sowState = rememberUpdatedState(onSow)
    SceneView(
        modifier = modifier,
        surfaceType = SurfaceType.Surface,
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
            // A transparent TextureView showed nothing on real phones. Clear to the table colour instead.
            renderer.clearColor(CLEAR_R, CLEAR_G, CLEAR_B, 1.0)
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
        // Rings and numbers are part of the scene, so they turn and scale with the board in every view.
        // Filament panics if a node is torn down mid-frame, so none of these ever leave the scene:
        // each ring stays and is hidden, and each pit keeps one card per count it has shown.
        MancalaBoard.holes.forEach { hole ->
            key("ring", hole.index) {
                val open = playable && hole.index in legal
                val shown = open || hole.index == last
                val ring = remember { arrayOfNulls<io.github.sceneview.node.Node>(1) }
                TorusNode(
                    majorRadius = 1f,
                    minorRadius = 0.22f,
                    majorSegments = 48,
                    minorSegments = 12,
                    materialInstance = if (open) gold else pale,
                    position = Position(hole.x, MancalaBoard.top + 0.13f, hole.y),
                    scale = Scale(hole.rx + 0.1f, 0.6f, hole.ry + 0.1f),
                    apply = { ring[0] = this; isVisible = shown },
                )
                SideEffect { ring[0]?.isVisible = shown }
            }
            key("count", hole.index) {
                val n = counts.getOrNull(hole.index) ?: 0
                val seen = remember { ArrayList<Int>() }
                if (n !in seen) seen.add(n)
                val cards = remember { HashMap<Int, io.github.sceneview.node.Node>() }
                val y = when {
                    hole.store -> MancalaBoard.depth - 0.3f
                    hole.y > 1.8f -> hole.y + hole.ry + 0.26f
                    else -> hole.y - hole.ry - 0.26f
                }
                seen.forEach { v ->
                    key(v) {
                        ImageNode(
                            bitmap = number(v),
                            size = io.github.sceneview.math.Size(COUNT_SIZE * 1.3f, COUNT_SIZE),
                            position = Position(hole.x, MancalaBoard.top + 0.13f, y),
                            // Lay the card flat on the wood; the node is upright by default.
                            rotation = Rotation(x = -90f),
                            apply = { cards[v] = this; isVisible = v == n },
                        )
                    }
                }
                SideEffect { cards.forEach { (v, node) -> node.isVisible = v == n } }
            }
        }
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
        set("transmissionFactor", 0.2f)
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

/**
 * The clear colour goes through the view's tone mapper, so the app beige (0xFFF6F1E7 on screen)
 * needs these linear values. They were measured on the release build in an emulator.
 */
private const val CLEAR_R = 1.72
private const val CLEAR_G = 1.40
private const val CLEAR_B = 0.97

/** Board units. The numbers are about as tall as a pit is wide, so they read in every view. */
private const val COUNT_SIZE = 0.44f

/** A number on a dark inlay, the way it is set into the wood. */
private fun countBitmap(n: Int): android.graphics.Bitmap {
    val w = 130
    val h = 100
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0201510.toInt() }
    canvas.drawRoundRect(android.graphics.RectF(4f, 4f, w - 4f, h - 4f), 26f, 26f, bg)
    val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF6E7C4.toInt()
        textSize = if (n >= 10) 70f else 80f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val baseline = h / 2f - (ink.descent() + ink.ascent()) / 2f
    canvas.drawText(n.toString(), w / 2f, baseline, ink)
    return bmp
}
