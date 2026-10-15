package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.simplegamegen.sudoku.ui.assets.tableFrame
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
    const val peakZ = 0.8f
    const val margin = 0.62f
    /** Bowl depth at the centre. The Blender board uses these same numbers. */
    const val pitDepth = 0.26f
    const val storeDepth = 0.32f
    /** Glass marble radius. The marble glTF is a unit sphere scaled by this. */
    const val stoneRadius = 0.150f
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
    var sowing by remember { mutableStateOf(false) }
    LaunchedEffect(g) {
        val before = shown.value
        sowing = false
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
        sowing = true
        val started = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - started).toFloat() / duration).coerceIn(0f, 1f)
            poses = anim.sample(t)
            if (t >= 1f) break
        }
        sowing = false
        poses = restingPoses(g.pits)
    }
    // Store names and counts sit above the 3D view, each over its own store, so they cover nothing.
    Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        StoreTag("$computer ${g.pits[Mancala.CPU]}")
        StoreTag("$you ${g.pits[Mancala.YOU]}")
    }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
    val density = LocalDensity.current
    val widthPx = with(density) { maxWidth.toPx() }
    // Only as tall as the board needs in the tallest saved view, so the board fills the width.
    val fitPx = remember(widthPx) {
        MancalaBoard.views.maxOf { view ->
            tableFrame(
                MancalaBoard.width.toInt(), widthPx, MancalaBoard.peakZ, view = view,
                margin = MancalaBoard.margin, rows = MancalaBoard.depth,
            ).height
        }
    }
    val boardHeight = with(density) { fitPx.toDp() }.coerceAtMost((maxHeight - 96.dp).coerceAtLeast(120.dp))
    BoardWithViews(
        camera,
        n = MancalaBoard.width.toInt(),
        peakZ = MancalaBoard.peakZ,
        margin = MancalaBoard.margin,
        rows = MancalaBoard.depth,
        height = boardHeight,
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
        // Rings and counts live in the 3D scene. Only invisible accessibility targets sit on top.
        Box(Modifier.fillMaxSize()) {
            MancalaWorld(
                frame, poses, playable, legal,
                onSow = { pit -> gate.trySow(pit, onSow) },
                modifier = Modifier.fillMaxSize(),
                // While stones fly, each number counts the stones that have landed in its hole.
                counts = if (sowing) liveCounts(poses) else g.pits.toList(),
                last = g.last,
            )
            order.forEach { hole ->
                val at = frame.at(hole.x, hole.y, MancalaBoard.top)
                val open = playable && hole.index in legal
                Box(Modifier.pinAt(at.x, at.y).size(1.dp).semantics {
                    contentDescription = say(spoken(hole))
                    if (open) onClick { onSow(hole.index); true }
                })
            }
        }
    }
    }
    }
}

@Composable
private fun StoreTag(text: String) {
    androidx.compose.material3.Text(
        text,
        color = Seed,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .background(Color(0xE0120C08), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Centre a child on a pixel of the board without covering the rest of the texture view. */
private fun Modifier.pinAt(x: Float, y: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints())
    // Keep labels on screen. The store labels sat past the edges on a phone.
    val maxX = if (constraints.hasBoundedWidth) constraints.maxWidth - placeable.width else Int.MAX_VALUE
    val left = (x.roundToInt() - placeable.width / 2).coerceIn(0, maxX.coerceAtLeast(0))
    layout(0, 0) {
        placeable.place(left, y.roundToInt() - placeable.height / 2)
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

private val Seed = Color(0xFFF6E7C4)

/** One sow per tap. The 3D view and the canvas overlay can both see the same finger. */
private fun liveCounts(poses: List<StonePose>): List<Int> {
    val counts = MutableList(MancalaBoard.holes.size) { 0 }
    MancalaBoard.holes.forEach { h ->
        counts[h.index] = poses.count { p ->
            val dx = (p.x - h.x) / h.rx
            val dy = (p.y - h.y) / h.ry
            dx * dx + dy * dy <= 1f
        }
    }
    return counts
}

private class SowGate {
    private var at = 0L
    fun trySow(pit: Int, onSow: (Int) -> Unit) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - at < 400L) return
        at = now
        onSow(pit)
    }
}
