package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.abs
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
        val age = burst?.let { ((clock - it.at) / 1_000_000_000f).coerceAtLeast(0f) } ?: 0f
        val effect = burst?.let { FlipEffect(it.kind, age) } ?: FlipEffect.None
        val visual = juiceSample(shown, effect)
        val kick = if (effect.kind == FlipJuice.CHIPS || effect.kind == FlipJuice.CLATTER) {
            val fade = (1f - effect.age / 0.22f).coerceAtLeast(0f)
            sin(effect.age * 70f) * fade * 7f
        } else 0f
        Box(Modifier.fillMaxWidth().weight(1f)) {
            BoardWithViews(
                camera,
                n = KnifeStage.N,
                peakZ = KnifeStage.peak(g.body.bottle),
                margin = 0.12f,
                rows = KnifeStage.ROWS,
                fitToView = true,
            ) {
                KnifeFlipWorld(
                    g.body,
                    visual,
                    effect,
                    camera.live,
                    shake = kick * 0.0022f,
                    onToss = if (g.phase == KnifeFlip.Phase.READY) {
                        { speed, aim -> vm.play { it.toss(speed, aim) } }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxSize()
                        .semantics { contentDescription = say("Knife flip block. Swipe up to toss.") },
                )
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


@Composable
private fun KnifeChoicePreview(index: Int) {
    val body = FlipModels.option(index).body
    KnifeFlipPreview(
        body,
        Modifier.fillMaxWidth().height(260.dp)
            .semantics { contentDescription = describeOption(index) },
    )
}

