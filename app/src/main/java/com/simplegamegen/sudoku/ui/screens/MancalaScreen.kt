package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.Mancala
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.BoardView
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.TableFrame
import com.simplegamegen.sudoku.ui.assets.drawRing
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kalah on a wooden board. Your six pits are the near row and sow to the right, into your store.
 * The far row runs back toward the computer's store on the left. Opposite pits share an x.
 */
internal object MancalaBoard {
    const val width = 8f
    const val depth = 3.6f
    const val top = 0.52f
    const val peakZ = 1.15f
    const val margin = 0.42f
    /** Bowl depth at the centre. The Blender board uses these same numbers. */
    const val pitDepth = 0.26f
    const val storeDepth = 0.32f
    /** Glass marble radius. The marble glTF is a unit sphere scaled by this. */
    const val stoneRadius = 0.100f
    val views = listOf(
        BoardView("Behind", yaw = 0f, pitch = 42f, distance = 1.32f),
        BoardView("Corner", yaw = 28f, pitch = 38f, distance = 1.42f),
        BoardView("Top", yaw = 0f, pitch = 90f, distance = 1.58f),
    )

    data class Hole(val index: Int, val x: Float, val y: Float, val rx: Float, val ry: Float) {
        val store: Boolean get() = index == Mancala.YOU || index == Mancala.CPU
    }

    val holes: List<Hole> = buildList {
        val step = 0.92f
        val x0 = 1.70f
        for (i in 0 until Mancala.PITS) {
            val x = x0 + i * step
            add(Hole(i, x, 2.55f, 0.38f, 0.38f))
            add(Hole(12 - i, x, 1.05f, 0.38f, 0.38f))
        }
        add(Hole(Mancala.YOU, 7.28f, 1.8f, 0.46f, 1.2f))
        add(Hole(Mancala.CPU, 0.72f, 1.8f, 0.46f, 1.2f))
    }

    fun hole(index: Int): Hole = holes.first { it.index == index }
}

private val MancalaStrength = listOf(
    "Sows a pit at random",
    "Takes a capture or another turn when it sees one",
    "Looks a few sows ahead",
    "Looks further ahead for the bigger store",
)

val MancalaSetup: (PuzzleFactory) -> PlaySetup<Mancala> = { factory ->
    PlaySetup(
        rules = "Six pits on your side, four stones in each, and a store at the right. Tap a pit to sow its stones one at a time to the right, " +
            "through your store and along the computer's pits. You skip the computer's store. " +
            "If the last stone lands in your store, you go again. If it lands in an empty pit on your side, you capture that stone and every stone in the pit opposite. " +
            "When one side has no stones left, the other side keeps whatever is still in its pits. Most stones in your store wins.",
        settingTitle = "Computer strength",
        settings = Mancala.NAMES,
        describe = { MancalaStrength[it] },
        settingOf = { it.setting },
        inProgress = { !it.ended && it.last != null },
        subtitle = { "${Mancala.NAMES[it.setting]} · you ${it.pits[Mancala.YOU]}, computer ${it.pits[Mancala.CPU]}" },
        create = { i -> { factory.custom("mancala:$i", { seed -> Mancala.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW }, g.pits[Mancala.YOU].toLong())
        },
    )
}

@Composable
fun MancalaScreen(nav: NavController, vm: PlayViewModel<Mancala>, factory: PuzzleFactory) {
    val camera = rememberBoardCamera("mancala_views_v2", MancalaBoard.views, emptyList())
    var full by rememberSaveable { mutableStateOf(false) }
    PlayShell(
        nav, vm, GameId.MANCALA, remember(factory) { MancalaSetup(factory) },
        scroll = false,
        tight = true,
        tools = { _, _ ->
            ToolButton(GameIcons.Fit, if (full) "Exit full screen" else "Full screen") { full = !full }
        },
    ) { g, s ->
        val playable = !g.ended && g.turn == 1 && !s.thinking && !s.busy
        val legal = remember(g) { g.legalPits() }
        PlayBoard(
            full, { full = false }, s.canUndo && !s.busy, vm::undo,
            above = {
                DuelStatus(
                    g.over, g.winner, g.turn, s.thinking,
                    "You ${g.pits[Mancala.YOU]}", "Computer ${g.pits[Mancala.CPU]}",
                    oneLine = true,
                )
                if (!g.ended && g.turn == 1 && g.last == Mancala.YOU) {
                    Text("Landed in your store. Go again.", style = MaterialTheme.typography.bodyLarge, color = LocalGameLook.current.colors.success)
                }
            },
            below = {
                if (playable) {
                    Text(
                        "Tap one of your pits. Stones sow to the right.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalGameLook.current.colors.muted,
                    )
                }
            },
        ) {
            MancalaTable(camera, g, legal, playable) { pit -> vm.play { it.sow(pit) } }
        }
    }
}


@Composable
private fun MancalaTable(
    camera: BoardCamera,
    g: Mancala,
    legal: List<Int>,
    playable: Boolean,
    onSow: (Int) -> Unit,
) {
    val c = LocalGameLook.current.colors
    val you = say("You")
    val computer = say("Computer")
    val holes = MancalaBoard.holes
    val gate = remember { SowGate() }
    var poses by remember { mutableStateOf(restingPoses(g.pits)) }
    val shown = remember { mutableStateOf(g) }
    LaunchedEffect(g) {
        val before = shown.value
        if (before == g) {
            poses = restingPoses(g.pits)
            return@LaunchedEffect
        }
        val anim = animateSow(before, g)
        shown.value = g
        if (anim == null) {
            poses = restingPoses(g.pits)
            return@LaunchedEffect
        }
        val duration = anim.durationMs * 1_000_000L
        val started = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - started).toFloat() / duration).coerceIn(0f, 1f)
            poses = anim.sample(t)
            if (t >= 1f) break
        }
        poses = restingPoses(g.pits)
    }
    BoardWithViews(
        camera,
        n = MancalaBoard.width.toInt(),
        peakZ = MancalaBoard.peakZ,
        margin = MancalaBoard.margin,
        rows = MancalaBoard.depth,
    ) { frame ->
        val order = holes.sortedByDescending { frame.depth(it.x, it.y, MancalaBoard.top) }
        fun spoken(hole: MancalaBoard.Hole): String {
            val count = g.pits[hole.index]
            val open = playable && hole.index in legal
            return when (hole.index) {
                Mancala.YOU -> "Your store, $count stones"
                Mancala.CPU -> "Computer's store, $count stones"
                in 0 until Mancala.PITS -> "Your pit ${hole.index + 1}, $count stones" + if (open) ", sow" else ""
                else -> "Computer's pit ${13 - hole.index}, $count stones"
            }
        }
        // Labels and rings are small views. A full-size canvas here repaints the Filament
        // texture and the carved board disappears behind the table colour.
        Box(Modifier.fillMaxSize()) {
            MancalaWorld(
                frame, poses, playable, legal,
                onSow = { pit -> gate.trySow(pit, onSow) },
                modifier = Modifier.fillMaxSize(),
            )
            order.forEach { hole ->
                val at = frame.at(hole.x, hole.y, MancalaBoard.top)
                val open = playable && hole.index in legal
                Box(Modifier.pinAt(at.x, at.y).size(1.dp).semantics {
                    contentDescription = say(spoken(hole))
                    if (open) onClick { onSow(hole.index); true }
                })
                if (g.last == hole.index) {
                    LastOval(frame, hole, c.highlight)
                }
                if (playable && open) {
                    SowRing(frame, hole, c.highlight)
                }
                val countY = when {
                    hole.store -> 2.72f
                    hole.y > 1.8f -> hole.y + hole.ry + 0.28f
                    else -> hole.y - hole.ry - 0.28f
                }
                val ink = if (playable && open) c.highlight else Seed
                CountPill(frame, g.pits[hole.index].toString(), hole.x, countY, ink)
            }
            CountPill(frame, you, MancalaBoard.hole(Mancala.YOU).x, 3.28f, Seed, bold = false)
            CountPill(frame, computer, MancalaBoard.hole(Mancala.CPU).x, 3.28f, Seed, bold = false)
        }
    }
}

/** Centre a child on a pixel of the board without covering the rest of the texture view. */
private fun Modifier.pinAt(x: Float, y: Float): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) {
        placeable.place(x.roundToInt() - placeable.width / 2, y.roundToInt() - placeable.height / 2)
    }
}

@Composable
private fun CountPill(frame: TableFrame, text: String, x: Float, y: Float, color: Color, bold: Boolean = true) {
    val px = (frame.unitAt(x, y, MancalaBoard.top) * if (bold) 0.38f else 0.28f).coerceIn(12f, 28f)
    val density = LocalDensity.current
    val padX = with(density) { (px * 0.38f).toDp() }
    val padY = with(density) { (px * 0.16f).toDp() }
    val at = frame.at(x, y, MancalaBoard.top + 0.05f)
    Text(
        text,
        color = color,
        fontSize = px.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .pinAt(at.x, at.y)
            .background(Color(0xE0120C08), RoundedCornerShape(with(density) { (px * 0.35f).toDp() }))
            .padding(horizontal = padX, vertical = padY),
    )
}

@Composable
private fun SowRing(frame: TableFrame, hole: MancalaBoard.Hole, color: Color) {
    val z = MancalaBoard.top + 0.04f
    val at = frame.at(hole.x, hole.y, z)
    val unit = frame.unitAt(hole.x, hole.y, z)
    val radius = unit * 0.42f
    val stroke = (unit * 0.1f).coerceAtLeast(2.8f)
    val box = (radius + stroke + unit * 0.05f) * 2f
    val density = LocalDensity.current
    Canvas(Modifier.pinAt(at.x, at.y).size(with(density) { box.toDp() })) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color.Black.copy(alpha = 0.55f), radius, center, style = Stroke(stroke + unit * 0.05f))
        drawCircle(color, radius, center, style = Stroke(stroke))
    }
}

@Composable
private fun LastOval(frame: TableFrame, hole: MancalaBoard.Hole, color: Color) {
    val z = MancalaBoard.top + 0.02f
    val pts = ovalPoints(frame, hole.x, hole.y, z, hole.rx, hole.ry)
    val minX = pts.minOf { it.x }
    val minY = pts.minOf { it.y }
    val maxX = pts.maxOf { it.x }
    val maxY = pts.maxOf { it.y }
    val stroke = frame.cell * 0.045f
    val density = LocalDensity.current
    Canvas(
        Modifier
            .pinAt((minX + maxX) / 2f, (minY + maxY) / 2f)
            .size(with(density) { (maxX - minX + stroke).toDp() }, with(density) { (maxY - minY + stroke).toDp() }),
    ) {
        val local = pts.map { Offset(it.x - minX + stroke / 2f, it.y - minY + stroke / 2f) }
        drawPath(pathOf(local), color, style = Stroke(stroke))
    }
}

private fun ovalPoints(frame: TableFrame, x: Float, y: Float, z: Float, rx: Float, ry: Float): List<Offset> {
    val steps = 18
    val turn = (PI * 2.0 / steps).toFloat()
    return List(steps) { i ->
        val a = i * turn
        frame.at(x + cos(a) * rx, y + sin(a) * ry, z)
    }
}

internal fun ovalHit(frame: TableFrame, pos: Offset, x: Float, y: Float, z: Float, rx: Float, ry: Float): Boolean {
    val pts = ovalPoints(frame, x, y, z, rx, ry)
    var inside = false
    var j = pts.lastIndex
    for (i in pts.indices) {
        val pi = pts[i]
        val pj = pts[j]
        if ((pi.y > pos.y) != (pj.y > pos.y) &&
            pos.x < (pj.x - pi.x) * (pos.y - pi.y) / (pj.y - pi.y) + pi.x
        ) inside = !inside
        j = i
    }
    if (inside) return true
    val center = frame.at(x, y, z)
    val unit = frame.unitAt(x, y, z)
    val dx = pos.x - center.x
    val dy = pos.y - center.y
    return sqrt(dx * dx + dy * dy) <= unit * maxOf(rx, ry) * 0.9f
}

private fun pathOf(pts: List<Offset>): Path {
    val path = Path()
    pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
    path.close()
    return path
}

private val Seed = Color(0xFFF6E7C4)

/** One sow per tap. The 3D view and the canvas overlay can both see the same finger. */
private class SowGate {
    private var at = 0L
    fun trySow(pit: Int, onSow: (Int) -> Unit) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - at < 400L) return
        at = now
        onSow(pit)
    }
}
