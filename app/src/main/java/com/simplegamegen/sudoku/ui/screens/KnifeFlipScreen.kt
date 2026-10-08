package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.arcade.FlipBody
import com.simplegamegen.sudoku.arcade.FlipEnd
import com.simplegamegen.sudoku.arcade.FlipModels
import com.simplegamegen.sudoku.arcade.FlipPhysics
import com.simplegamegen.sudoku.arcade.KnifeFlip
import com.simplegamegen.sudoku.arcade.Sample
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.BoardView
import com.simplegamegen.sudoku.ui.assets.Glassy
import com.simplegamegen.sudoku.ui.assets.KnifeMeshes
import com.simplegamegen.sudoku.ui.assets.KnifeSurface
import com.simplegamegen.sudoku.ui.assets.Mesh
import com.simplegamegen.sudoku.ui.assets.Polished
import com.simplegamegen.sudoku.ui.assets.Pose
import com.simplegamegen.sudoku.ui.assets.Satin
import com.simplegamegen.sudoku.ui.assets.TableFrame
import com.simplegamegen.sudoku.ui.assets.boxMesh
import com.simplegamegen.sudoku.ui.assets.drawBoardSlab
import com.simplegamegen.sudoku.ui.assets.drawMesh
import com.simplegamegen.sudoku.ui.assets.latheMesh
import com.simplegamegen.sudoku.ui.assets.quad
import com.simplegamegen.sudoku.ui.assets.rotation
import com.simplegamegen.sudoku.ui.assets.tableFrame
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The table is only a little bigger than the block. A knife's toss stays low, so its camera
 * stops just above that arc. A bottle's arc is much higher, so that camera is taller.
 * Zoom 1 is this fit; Fit the board puts it back.
 */
internal object KnifeStage {
    const val N = 6
    const val ROWS = 2.7f
    const val BLOCK_LEFT = 0.68f
    const val BLOCK_TOP = 0.78f
    const val BLOCK_Y = 1.35f
    /** Just above a practised knife arc (the tip stays under about 3.4 squares). */
    const val KNIFE_PEAK = 3.7f
    /** Just above a practised bottle arc (about 7.5 squares). */
    const val BOTTLE_PEAK = 7.8f

    val knifeViews = listOf(
        BoardView("Side", yaw = 32f, pitch = 34f, distance = 2.4f),
        BoardView("Corner", yaw = 58f, pitch = 32f, distance = 2.5f),
        BoardView("Top", yaw = 0f, pitch = 70f, distance = 2.6f),
    )
    val bottleViews = listOf(
        BoardView("Side", yaw = 30f, pitch = 26f, distance = 2.6f),
        BoardView("Corner", yaw = 56f, pitch = 26f, distance = 2.7f),
        BoardView("Top", yaw = 0f, pitch = 66f, distance = 2.8f),
    )

    fun peak(bottle: Boolean) = if (bottle) BOTTLE_PEAK else KNIFE_PEAK
    fun views(bottle: Boolean) = if (bottle) bottleViews else knifeViews
}

private val BlockMesh = boxMesh(FlipPhysics.BLOCK_X1 * KnifeMeshes.SQUARES_PER_METRE, 1.5f, KnifeStage.BLOCK_TOP)
private val MugMesh = latheMesh(listOf(0f to 0.11f, 0.03f to 0.15f, 0.26f to 0.155f, 0.30f to 0.17f), segments = 14)
private val ChipMesh = boxMesh(0.11f, 0.05f, 0.035f)

private val BladeSteel = Color(0xFFE4EAF1)
private val EdgeSteel = Color(0xFF4A515C)
private val TangSteel = Color(0xFF4E545E)
private val WoodHandle = Color(0xFF8B5A34)
private val BolsterBrass = Color(0xFFC6A15A)
private val Scales = Color(0xFF7E8791)
private val G10 = Color(0xFFC2A36B)
private val BlockWood = Color(0xFF8E5A32)
private val TableWood = Color(0xFFC4A574)
private val Wall = Color(0xFFE6D9C8)
private val WindowDay = Color(0xFFC5D8E6)
private val MugClay = Color(0xFFD7CFC4)
private val Plastic = Color(0xFFB7D0DC)
private val Water = Color(0xFF2E86C7)
private val Cap = Color(0xFF1F6F78)

val KnifeFlipSetup: (PuzzleFactory) -> PlaySetup<KnifeFlip> = { factory ->
    PlaySetup(
        rules = "Swipe up to toss a knife or a water bottle, or tap Toss. A knife scores when the point sticks in the block. " +
            "A bottle scores when it lands upright on its base. Each clean landing raises the streak, and a miss ends the round. " +
            "The model changes the weight, the balance and the spin. A bottle between a quarter and a third full is the easiest to land.",
        settingTitle = "Knife",
        settings = FlipModels.options.map { it.label },
        describe = { describeOption(it) },
        preview = { index -> KnifeChoicePreview(index) },
        settingOf = { it.option },
        inProgress = { it.phase != KnifeFlip.Phase.ENDED },
        subtitle = { "Streak ${it.streak}" },
        create = { i -> { factory.custom("knife:$i", { seed -> KnifeFlip.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (g.phase != KnifeFlip.Phase.ENDED) null
            else Outcome(if (g.score > 0) Result.WON else Result.LOST, g.score.toLong())
        },
    )
}

private fun describeOption(index: Int): String {
    val body = FlipModels.option(index).body
    val grams = (body.mass * 1000f).toInt()
    if (!body.bottle) {
        val cm = (body.length * 100f).toInt()
        val pct = (body.balance * 100f).toInt()
        return "$grams g, $cm cm, balance $pct% from the tip."
    }
    val label = FlipModels.fillLabels[FlipModels.bottles.indexOf(body)]
    return if (body.fill >= 0.99f) "$grams g. Full, so the water cannot slosh."
    else "$grams g. $label full, and the water can slosh."
}

@Composable
fun KnifeFlipScreen(nav: NavController, vm: PlayViewModel<KnifeFlip>, factory: PuzzleFactory) {
    val look = LocalGameLook.current
    val knifeCamera = rememberBoardCamera("knife_flip_knife_views", KnifeStage.knifeViews)
    val bottleCamera = rememberBoardCamera("knife_flip_bottle_views", KnifeStage.bottleViews)
    PlayShell(
        nav, vm, GameId.KNIFE_FLIP, remember(factory) { KnifeFlipSetup(factory) },
        undoable = false, scroll = false, tight = true,
        tools = { g, s ->
            ToolButton(GameIcons.Play, "Toss", enabled = g.phase == KnifeFlip.Phase.READY && !s.busy) {
                val (speed, aim) = KnifeFlip.practised(g.option)
                vm.play { it.toss(speed, aim) }
            }
        },
    ) { g, _ ->
        val line = statusLine(g)
        Text(
            line,
            modifier = Modifier.padding(horizontal = 8.dp),
            color = when {
                g.phase == KnifeFlip.Phase.ENDED -> look.colors.danger
                g.verdict == FlipEnd.STUCK.name || g.verdict == FlipEnd.LANDED.name -> look.colors.success
                else -> look.colors.text
            },
            style = MaterialTheme.typography.titleMedium,
        )
        var flying by remember(g.seed) { mutableStateOf<Sample?>(null) }
        var rested by remember(g.seed) { mutableStateOf<Sample?>(null) }
        var burst by remember(g.seed) { mutableStateOf<FlipBurst?>(null) }
        var clock by remember(g.seed) { mutableStateOf(0L) }
        LaunchedEffect(g.phase, g.throwNum, g.seed) {
            val release = g.release
            if (g.phase != KnifeFlip.Phase.FLYING || release == null) {
                flying = null
                return@LaunchedEffect
            }
            burst = null
            val flight = FlipPhysics.simulate(g.body, release)
            val start = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val t = (now - start) / 1_000_000_000f
                flying = flight.at(t.coerceAtMost(flight.time))
                if (t >= flight.time) break
            }
            rested = flight.final
            flying = null
            val kind = when (flight.end) {
                FlipEnd.STUCK -> FlipJuice.CHIPS
                FlipEnd.LANDED -> FlipJuice.BOUNCE
                else -> FlipJuice.CLATTER
            }
            burst = FlipBurst(kind, System.nanoTime())
            vm.play(record = false) { it.settle() }
        }
        LaunchedEffect(burst) {
            val started = burst ?: return@LaunchedEffect
            while ((System.nanoTime() - started.at) / 1_000_000_000f < 0.7f) {
                clock = withFrameNanos { it }
            }
            if (burst === started) burst = null
        }
        val shown = flying ?: rested ?: holdSample(g.body)
        val camera = if (g.body.bottle) bottleCamera else knifeCamera
        Box(Modifier.fillMaxWidth().weight(1f)) {
            BoardWithViews(
                camera,
                n = KnifeStage.N,
                peakZ = KnifeStage.peak(g.body.bottle),
                margin = 0.12f,
                rows = KnifeStage.ROWS,
                fitToView = true,
            ) { frame ->
                Canvas(
                    Modifier.matchParentSize()
                        .semantics { contentDescription = say("Knife flip block. Swipe up to toss.") }
                        .pointerInput(g.phase, g.option, g.throwNum) {
                            if (g.phase != KnifeFlip.Phase.READY) return@pointerInput
                            var origin = Offset.Zero
                            var last = Offset.Zero
                            var started = 0L
                            detectDragGestures(
                                onDragStart = { origin = it; last = it; started = System.nanoTime() },
                                onDrag = { change, _ -> last = change.position; change.consume() },
                                onDragEnd = {
                                    val dy = origin.y - last.y
                                    if (dy < 48f) return@detectDragGestures
                                    val dx = last.x - origin.x
                                    val dt = ((System.nanoTime() - started) / 1_000_000_000f).coerceIn(0.06f, 0.4f)
                                    val speed = (hypot(dx, dy) / dt / 620f).coerceIn(0.7f, 3.6f)
                                    val aim = atan2(dy, abs(dx).coerceAtLeast(12f)).coerceIn(0.9f, 1.5f)
                                    vm.play { it.toss(speed, aim) }
                                },
                            )
                        },
                ) {
                    val age = burst?.let { ((clock - it.at) / 1_000_000_000f).coerceAtLeast(0f) } ?: 0f
                    val effect = burst?.let { FlipEffect(it.kind, age) } ?: FlipEffect.None
                    val visual = juiceSample(shown, effect)
                    val kick = if (effect.kind == FlipJuice.CHIPS || effect.kind == FlipJuice.CLATTER) {
                        val fade = (1f - effect.age / 0.22f).coerceAtLeast(0f)
                        sin(effect.age * 70f) * fade * 7f
                    } else 0f
                    translate(kick, kick * 0.35f) {
                        paintKnifeFlip(frame, g.body, visual, effect)
                    }
                }
            }
            Column(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                Text(
                    g.streak.toString(),
                    color = when (g.verdict) {
                        FlipEnd.STUCK.name, FlipEnd.LANDED.name -> look.colors.success
                        else -> look.colors.text
                    },
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text("Streak", color = look.colors.muted, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

internal fun holdSample(body: FlipBody): Sample {
    val held = FlipPhysics.hold(body)
    return Sample(0f, held.x, held.z, held.theta, 0f)
}

internal fun knifePlayFrame(bottle: Boolean, view: BoardView, width: Float, height: Float): TableFrame =
    tableFrame(
        KnifeStage.N, width, KnifeStage.peak(bottle),
        view = view, maxHeightPx = height, margin = 0.12f, rows = KnifeStage.ROWS, fitToView = true,
    )

internal enum class FlipJuice { CHIPS, BOUNCE, CLATTER }

internal class FlipBurst(val kind: FlipJuice, val at: Long)

internal class FlipEffect(val kind: FlipJuice, val age: Float) {
    companion object {
        val None = FlipEffect(FlipJuice.CLATTER, -1f)
    }
}

private fun juiceSample(sample: Sample, effect: FlipEffect): Sample {
    if (effect.age < 0f) return sample
    return when (effect.kind) {
        FlipJuice.BOUNCE -> {
            val hop = (1f - effect.age / 0.55f).coerceAtLeast(0f)
            sample.copy(z = sample.z + hop * abs(sin(effect.age * 16f)) * 0.045f)
        }
        FlipJuice.CLATTER -> {
            val kick = sin(effect.age * 46f) * (1f - effect.age / 0.45f).coerceAtLeast(0f)
            sample.copy(x = sample.x + kick * 0.012f, theta = sample.theta + kick * 0.18f)
        }
        FlipJuice.CHIPS -> sample
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.paintKnifeFlip(
    frame: TableFrame,
    body: FlipBody,
    sample: Sample,
    effect: FlipEffect = FlipEffect.None,
) {
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFFD7E6F0), Color(0xFFE7DCCE), Color(0xFFCDB892)),
        ),
    )
    val back = KnifeStage.ROWS - 0.02f
    fun panel(x0: Float, z0: Float, x1: Float, z1: Float, y: Float, color: Color) {
        drawPath(
            quad(frame.at(x0, y, z0), frame.at(x1, y, z0), frame.at(x1, y, z1), frame.at(x0, y, z1)),
            color,
        )
    }
    panel(0f, 0.06f, KnifeStage.N.toFloat(), 4.4f, back, Wall)
    panel(3.55f, 1.7f, 5.15f, 3.55f, back - 0.02f, WindowDay)
    panel(4.28f, 1.7f, 4.36f, 3.55f, back - 0.03f, Wall.copy(alpha = 0.85f))
    panel(3.55f, 2.55f, 5.15f, 2.63f, back - 0.03f, Wall.copy(alpha = 0.85f))
    drawBoardSlab(frame, 0.06f, TableWood, KnifeStage.ROWS)
    val span = FlipPhysics.BLOCK_X1 * KnifeMeshes.SQUARES_PER_METRE
    val blockX = KnifeStage.BLOCK_LEFT + span / 2f
    oval(frame, 0.4f, 0.48f, 0.28f, 0.12f, 0.07f).let { drawPath(it, Color.Black.copy(alpha = 0.22f)) }
    drawMesh(frame, MugMesh, Pose(0.4f, 0.48f, 0.06f), MugClay, Satin, warmth = 0.35f)
    oval(frame, blockX, KnifeStage.BLOCK_Y, span * 0.46f, 0.62f, 0.07f).let { drawPath(it, Color.Black.copy(alpha = 0.3f)) }
    drawMesh(frame, BlockMesh, Pose(blockX, KnifeStage.BLOCK_Y, 0f), BlockWood, Satin, warmth = 0.4f)
    val grain = Color(0xFF5C3A22).copy(alpha = 0.28f)
    val top = KnifeStage.BLOCK_TOP + 0.012f
    val x0 = KnifeStage.BLOCK_LEFT + 0.08f
    val x1 = KnifeStage.BLOCK_LEFT + span - 0.08f
    var gy = KnifeStage.BLOCK_Y - 0.62f
    while (gy < KnifeStage.BLOCK_Y + 0.62f) {
        drawLine(grain, frame.at(x0, gy, top), frame.at(x1, gy + 0.05f, top), strokeWidth = 1.4f)
        gy += 0.2f
    }
    drawToss(frame, body, sample)
    if (effect.age < 0f) return
    when (effect.kind) {
        FlipJuice.CHIPS -> drawChips(frame, body, sample, effect.age)
        FlipJuice.CLATTER -> drawClatter(frame, sample, effect.age)
        FlipJuice.BOUNCE -> Unit
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClatter(
    frame: TableFrame,
    sample: Sample,
    age: Float,
) {
    val fade = (1f - age / 0.35f).coerceAtLeast(0f)
    if (fade <= 0f) return
    val scale = KnifeMeshes.SQUARES_PER_METRE
    val cx = KnifeStage.BLOCK_LEFT + sample.x * scale
    val cz = KnifeStage.BLOCK_TOP + sample.z * scale
    val ink = Color(0xFF3A342C).copy(alpha = 0.55f * fade)
    for (i in 0 until 6) {
        val ang = i * 1.05f + age * 2f
        val inner = 0.18f + age * 0.7f
        val outer = inner + 0.28f
        drawLine(
            ink,
            frame.at(cx + cos(ang) * inner, KnifeStage.BLOCK_Y, cz + sin(ang) * inner * 0.35f),
            frame.at(cx + cos(ang) * outer, KnifeStage.BLOCK_Y, cz + sin(ang) * outer * 0.35f),
            strokeWidth = 2.2f,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChips(
    frame: TableFrame,
    body: FlipBody,
    sample: Sample,
    age: Float,
) {
    val scale = KnifeMeshes.SQUARES_PER_METRE
    val tip = body.comFromTip(0f) * scale
    val tx = KnifeStage.BLOCK_LEFT + sample.x * scale + sin(sample.theta) * tip
    val fade = (1f - age / 0.55f).coerceAtLeast(0f)
    if (fade <= 0f) return
    for (i in 0 until 12) {
        val ang = i * 0.85f
        val speed = 0.9f + (i % 4) * 0.28f
        val x = tx + cos(ang) * speed * age
        val y = KnifeStage.BLOCK_Y + sin(ang) * 0.45f * age
        val z = KnifeStage.BLOCK_TOP + (1.15f + (i % 3) * 0.35f) * age - 3.2f * age * age
        if (z < 0.08f) continue
        drawMesh(
            frame, ChipMesh,
            Pose(x, y, z, rot = rotation(0.4f, 1f, 0.2f, age * 9f + i)),
            BlockWood, Satin, warmth = 0.3f, alpha = fade,
        )
    }
}

private fun statusLine(g: KnifeFlip): String = when (g.verdict) {
    FlipEnd.STUCK.name -> "Stuck. Streak ${g.streak}."
    FlipEnd.LANDED.name -> "Landed upright. Streak ${g.streak}."
    FlipEnd.HANDLE.name -> "The handle hit first."
    FlipEnd.FLAT.name -> "The flat of the blade hit."
    FlipEnd.WEAK.name -> "Too soft to stick."
    FlipEnd.HARD.name -> "Too hard. It bounced out."
    FlipEnd.TIPPED.name -> "The bottle tipped over."
    FlipEnd.OFF.name -> "It missed the block."
    FlipEnd.MISS.name -> "It bounced off."
    else -> if (g.phase == KnifeFlip.Phase.READY) "Swipe up, or tap Toss." else "In the air."
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawToss(
    frame: com.simplegamegen.sudoku.ui.assets.TableFrame,
    body: FlipBody,
    sample: Sample,
) {
    val scale = KnifeMeshes.SQUARES_PER_METRE
    val comX = KnifeStage.BLOCK_LEFT + sample.x * scale
    val comZ = KnifeStage.BLOCK_TOP + sample.z * scale
    val rot = rotation(0f, 1f, 0f, sample.theta)
    drawContactShadow(frame, body, sample)
    if (!body.bottle) {
        val pose = Pose(comX, KnifeStage.BLOCK_Y, comZ, rot = rot, scale = scale)
        val rim = (frame.cell * if (sample.z < 0.35f) 0.09f else 0.04f).coerceIn(2.5f, 7f)
        for (part in KnifeMeshes.parts(body)) {
            if (part.surface == KnifeSurface.EDGE || part.mesh.count < 140) continue
            drawSilhouette(frame, part.mesh, pose, Color(0xFF12141A), rim)
        }
        for (part in KnifeMeshes.parts(body)) {
            val (color, finish) = when (part.surface) {
                KnifeSurface.BLADE -> BladeSteel to Polished
                KnifeSurface.EDGE -> EdgeSteel to Polished
                KnifeSurface.TANG -> TangSteel to Polished
                KnifeSurface.BOLSTER -> BolsterBrass to Polished
                KnifeSurface.HANDLE -> WoodHandle to Satin
                KnifeSurface.SCALES -> Scales to Satin
                KnifeSurface.G10 -> G10 to Satin
            }
            drawMesh(frame, part.mesh, pose, color, finish)
        }
        return
    }
    val h = body.comFromBase(sample.sigma)
    val pose = Pose(
        comX - scale * h * sin(sample.theta),
        KnifeStage.BLOCK_Y,
        comZ - scale * h * cos(sample.theta),
        rot = rot,
        scale = scale,
    )
    val fillIndex = FlipModels.bottles.indexOfFirst { abs(it.fill - body.fill) < 0.01f }.coerceAtLeast(0)
    drawMesh(frame, KnifeMeshes.water(fillIndex, sample.sigma), pose, Water, Glassy, alpha = 0.92f)
    drawMesh(frame, KnifeMeshes.bottle, pose, Plastic, Glassy, alpha = 0.56f)
    drawMesh(frame, KnifeMeshes.cap, pose, Cap, Satin)
}

/** A dark disc on the block, stretched along the blade, darker as the body comes down. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawContactShadow(
    frame: TableFrame,
    body: FlipBody,
    sample: Sample,
) {
    val fade = (1f - sample.z / 0.85f).coerceIn(0f, 1f)
    if (fade < 0.04f) return
    val scale = KnifeMeshes.SQUARES_PER_METRE
    val cx = KnifeStage.BLOCK_LEFT + sample.x * scale
    val along = if (body.bottle) 0.42f + sample.z * 0.2f
        else (body.length * scale * 0.46f * abs(sin(sample.theta))).coerceAtLeast(0.38f)
    val across = 0.2f + sample.z * 0.12f
    oval(frame, cx, KnifeStage.BLOCK_Y, along * 1.35f, across * 1.5f, KnifeStage.BLOCK_TOP + 0.01f)
        .let { drawPath(it, Color.Black.copy(alpha = 0.28f * fade)) }
    oval(frame, cx, KnifeStage.BLOCK_Y, along, across, KnifeStage.BLOCK_TOP + 0.02f)
        .let { drawPath(it, Color.Black.copy(alpha = 0.55f * fade)) }
    if (!body.bottle && sample.z < 0.4f) {
        val tip = body.comFromTip(0f) * scale
        val tx = cx + sin(sample.theta) * tip
        oval(frame, tx, KnifeStage.BLOCK_Y, 0.28f, 0.16f, KnifeStage.BLOCK_TOP + 0.03f)
            .let { drawPath(it, Color.Black.copy(alpha = 0.7f * fade)) }
    }
}

private fun oval(frame: TableFrame, cx: Float, cy: Float, rx: Float, ry: Float, z: Float): Path {
    val path = Path()
    val steps = 16
    for (i in 0..steps) {
        val a = (i.toFloat() / steps) * (2f * Math.PI.toFloat())
        val at = frame.at(cx + cos(a) * rx, cy + sin(a) * ry, z)
        if (i == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
    }
    path.close()
    return path
}

/** Dark rim around a part so a thin blade still reads against the block. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSilhouette(
    frame: TableFrame,
    mesh: Mesh,
    pose: Pose,
    color: Color,
    width: Float,
) {
    val pts = ArrayList<Offset>(mesh.pos.size / 3)
    var i = 0
    val p = mesh.pos
    while (i < p.size) {
        val w = pose.apply(p[i], p[i + 1], p[i + 2])
        pts += frame.at(w[0], w[1], w[2])
        i += 3
    }
    val hull = convexHull(pts)
    if (hull.size < 3) return
    val path = Path()
    hull.forEachIndexed { k, o -> if (k == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    path.close()
    drawPath(path, color, style = Stroke(width = width))
}

private fun convexHull(points: List<Offset>): List<Offset> {
    val pts = points.sortedWith(compareBy({ it.x }, { it.y }))
    if (pts.size < 3) return pts
    fun cross(o: Offset, a: Offset, b: Offset) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = ArrayList<Offset>()
    for (p in pts) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) lower.removeAt(lower.lastIndex)
        lower += p
    }
    val upper = ArrayList<Offset>()
    for (p in pts.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) upper.removeAt(upper.lastIndex)
        upper += p
    }
    if (lower.isNotEmpty()) lower.removeAt(lower.lastIndex)
    if (upper.isNotEmpty()) upper.removeAt(upper.lastIndex)
    return lower + upper
}

/** Studio close-up used by the model picker. The model fills the frame at a three-quarter angle. */
internal data class ChoiceShot(val frame: TableFrame, val x: Float, val y: Float, val z: Float, val theta: Float)

internal fun choiceShot(body: FlipBody, width: Float, height: Float): ChoiceShot {
    val span = body.length * KnifeMeshes.SQUARES_PER_METRE
    val n: Int
    val rows: Float
    val peak: Float
    val yaw: Float
    val pitch: Float
    val theta: Float
    val zFrac: Float
    if (body.bottle) {
        n = 3
        rows = span * 0.85f
        peak = span * 1.35f
        yaw = 52f
        pitch = 18f
        theta = 0.22f
        zFrac = 0.04f
    } else {
        // Laid mostly flat and turned, so the blade face, the edge and the handle all show.
        n = kotlin.math.ceil(span * 1.05f).toInt().coerceIn(2, 5)
        rows = (span * 0.42f).coerceAtLeast(0.8f)
        peak = (span * 0.48f).coerceAtLeast(0.9f)
        yaw = 58f
        pitch = 22f
        theta = 1.28f
        zFrac = 0.46f
    }
    val x = n / 2f
    val y = rows / 2f
    val z = peak * zFrac
    fun frame(zoom: Float, panX: Float, panY: Float) = tableFrame(
        n, width, peak,
        view = BoardView("Preview", yaw = yaw, pitch = pitch, distance = 1.08f, zoom = zoom, panX = panX, panY = panY),
        maxHeightPx = height, margin = 0.02f, rows = rows,
    )
    val fitted = frame(1f, 0f, 0f)
    val box = choiceBounds(body, fitted, Pose(x, y, z, rot = rotation(0f, 1f, 0f, theta), scale = KnifeMeshes.SQUARES_PER_METRE))
    val bw = (box[2] - box[0]).coerceAtLeast(1f)
    val bh = (box[3] - box[1]).coerceAtLeast(1f)
    val zoom = (0.84f / maxOf(bw / width, bh / height)).coerceIn(1.1f, 3.6f)
    val panX = (width / 2f - (box[0] + box[2]) / 2f) * zoom
    val panY = (height / 2f - (box[1] + box[3]) / 2f) * zoom
    return ChoiceShot(frame(zoom, panX, panY), x, y, z, theta)
}

/** Screen box of the model at zoom 1, so the preview can magnify it until it fills the frame. */
private fun choiceBounds(body: FlipBody, frame: TableFrame, pose: Pose): FloatArray {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    fun take(mesh: Mesh) {
        val p = mesh.pos
        var i = 0
        while (i < p.size) {
            val w = pose.apply(p[i], p[i + 1], p[i + 2])
            val s = frame.at(w[0], w[1], w[2])
            if (s.x < minX) minX = s.x
            if (s.y < minY) minY = s.y
            if (s.x > maxX) maxX = s.x
            if (s.y > maxY) maxY = s.y
            i += 3
        }
    }
    if (body.bottle) {
        take(KnifeMeshes.bottle); take(KnifeMeshes.cap)
    } else {
        for (part in KnifeMeshes.parts(body)) take(part.mesh)
    }
    return floatArrayOf(minX, minY, maxX, maxY)
}

@Composable
private fun KnifeChoicePreview(index: Int) {
    val body = FlipModels.option(index).body
    Canvas(
        Modifier.fillMaxWidth().height(260.dp)
            .semantics { contentDescription = describeOption(index) },
    ) {
        paintChoice(body)
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.paintChoice(body: FlipBody) {
    drawRect(Color(0xFFE7DFD2))
    val shot = choiceShot(body, size.width, size.height)
    val frame = shot.frame
    val span = body.length * KnifeMeshes.SQUARES_PER_METRE
    oval(frame, shot.x, shot.y, span * 0.16f, span * 0.05f, (shot.z - span * 0.08f).coerceAtLeast(0.02f))
        .let { drawPath(it, Color.Black.copy(alpha = 0.35f)) }
    val rot = rotation(0f, 1f, 0f, shot.theta)
    val scale = KnifeMeshes.SQUARES_PER_METRE
    if (!body.bottle) {
        val pose = Pose(shot.x, shot.y, shot.z, rot = rot, scale = scale)
        for (part in KnifeMeshes.parts(body)) {
            val (color, finish) = when (part.surface) {
                KnifeSurface.BLADE -> BladeSteel to Polished
                KnifeSurface.EDGE -> EdgeSteel to Polished
                KnifeSurface.TANG -> TangSteel to Polished
                KnifeSurface.BOLSTER -> BolsterBrass to Polished
                KnifeSurface.HANDLE -> WoodHandle to Satin
                KnifeSurface.SCALES -> Scales to Satin
                KnifeSurface.G10 -> G10 to Satin
            }
            drawMesh(frame, part.mesh, pose, color, finish)
        }
        return
    }
    val pose = Pose(shot.x, shot.y, shot.z, rot = rot, scale = scale)
    val fillIndex = FlipModels.bottles.indexOfFirst { abs(it.fill - body.fill) < 0.01f }.coerceAtLeast(0)
    drawMesh(frame, KnifeMeshes.water(fillIndex, 0f), pose, Water, Glassy, alpha = 0.92f)
    drawMesh(frame, KnifeMeshes.bottle, pose, Plastic, Glassy, alpha = 0.56f)
    drawMesh(frame, KnifeMeshes.cap, pose, Cap, Satin)
}
