package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.simplegamegen.sudoku.ui.assets.Pose
import com.simplegamegen.sudoku.ui.assets.Satin
import com.simplegamegen.sudoku.ui.assets.boxMesh
import com.simplegamegen.sudoku.ui.assets.drawBoardSlab
import com.simplegamegen.sudoku.ui.assets.drawMesh
import com.simplegamegen.sudoku.ui.assets.rotation
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

private val KnifeViews = listOf(
    BoardView("Side", yaw = 0f, pitch = 22f, distance = 1.28f),
    BoardView("Corner", yaw = 34f, pitch = 32f, distance = 1.45f),
    BoardView("Top", yaw = 0f, pitch = 76f, distance = 1.55f),
)

private const val BOARD = 10
private const val BLOCK_LEFT = 2.35f
private const val BLOCK_TOP = 0.78f
private const val BLOCK_Y = 3f
private val BlockMesh = boxMesh(FlipPhysics.BLOCK_X1 * KnifeMeshes.SQUARES_PER_METRE, 2.15f, BLOCK_TOP)

private val Steel = Color(0xFFD5D8DE)
private val CleaverSteel = Color(0xFFB7BCC4)
private val HandleSteel = Color(0xFF8E939C)
private val Wood = Color(0xFF8B5A34)
private val Felt = Color(0xFF2E6B4F)
private val Plastic = Color(0xFFD7E7EE)
private val Water = Color(0xFF2E86C7)
private val Cap = Color(0xFF1F6F78)

val KnifeFlipSetup: (PuzzleFactory) -> PlaySetup<KnifeFlip> = { factory ->
    PlaySetup(
        rules = "Swipe up to toss a knife or a water bottle, or tap Toss. A knife scores when the point sticks in the block. " +
            "A bottle scores when it lands upright on its base. Each clean landing raises the streak, and a miss ends the round. " +
            "The model changes the weight, the balance and the spin. A bottle about one third full is the easiest to land.",
        settingTitle = "Knife",
        settings = FlipModels.options.map { it.label },
        describe = { describeOption(it) },
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
    val camera = rememberBoardCamera("knife_flip_views", KnifeViews)
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
        LaunchedEffect(g.phase, g.throwNum, g.seed) {
            val release = g.release
            if (g.phase != KnifeFlip.Phase.FLYING || release == null) {
                flying = null
                return@LaunchedEffect
            }
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
            vm.play(record = false) { it.settle() }
        }
        val shown = flying ?: rested ?: holdSample(g.body)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            BoardWithViews(camera, n = BOARD, peakZ = 6.2f, margin = 0.35f, rows = 6f) { frame ->
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
                    drawBoardSlab(frame, 0.06f, Felt)
                    val span = FlipPhysics.BLOCK_X1 * KnifeMeshes.SQUARES_PER_METRE
                    drawMesh(frame, BlockMesh, Pose(BLOCK_LEFT + span / 2f, BLOCK_Y, 0f), Wood, Satin)
                    drawToss(frame, g.body, shown)
                }
            }
        }
    }
}

private fun holdSample(body: FlipBody): Sample {
    val held = FlipPhysics.hold(body)
    return Sample(0f, held.x, held.z, held.theta, 0f)
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
    val comX = BLOCK_LEFT + sample.x * scale
    val comZ = BLOCK_TOP + sample.z * scale
    val rot = rotation(0f, 1f, 0f, sample.theta)
    if (!body.bottle) {
        val color = when (body.id) {
            "cleaver" -> CleaverSteel
            "pocket", "butterfly" -> HandleSteel
            else -> Steel
        }
        drawMesh(frame, KnifeMeshes.knife(body), Pose(comX, BLOCK_Y, comZ, rot = rot, scale = scale), color, Satin)
        return
    }
    val h = body.comFromBase(sample.sigma)
    val pose = Pose(
        comX - scale * h * sin(sample.theta),
        BLOCK_Y,
        comZ - scale * h * cos(sample.theta),
        rot = rot,
        scale = scale,
    )
    val fillIndex = FlipModels.bottles.indexOfFirst { abs(it.fill - body.fill) < 0.01f }.coerceAtLeast(0)
    drawMesh(frame, KnifeMeshes.water(fillIndex, sample.sigma), pose, Water, Glassy, alpha = 0.92f)
    drawMesh(frame, KnifeMeshes.bottle, pose, Plastic, Glassy, alpha = 0.42f)
    drawMesh(frame, KnifeMeshes.cap, pose, Cap, Satin)
}
