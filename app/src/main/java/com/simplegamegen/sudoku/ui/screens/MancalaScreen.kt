package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.ui.text.drawText

/**
 * Kalah on a wooden board. Your six pits are the near row and sow to the right, into your store.
 * The far row runs back toward the computer's store on the left. Opposite pits share an x.
 */
internal object MancalaBoard {
    const val width = 8f
    const val depth = 3.6f
    const val top = 0.36f
    const val peakZ = 1.05f
    const val margin = 0.42f
    /** Bowl depth at the centre. The Blender board uses these same numbers. */
    const val pitDepth = 0.16f
    const val storeDepth = 0.18f
    /** Glass marble radius. The marble glTF is a unit sphere scaled by this. */
    const val stoneRadius = 0.060f
    val views = listOf(
        BoardView("Behind", yaw = 0f, pitch = 64f, distance = 1.48f),
        BoardView("Corner", yaw = 32f, pitch = 56f, distance = 1.62f),
        BoardView("Top", yaw = 0f, pitch = 90f, distance = 1.72f),
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
    val camera = rememberBoardCamera("mancala_views", MancalaBoard.views, emptyList())
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
    val measurer = rememberTextMeasurer()
    val you = say("You")
    val computer = say("Computer")
    val holes = MancalaBoard.holes
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
        Box(Modifier.fillMaxSize()) {
            MancalaWorld(frame, poses, Modifier.fillMaxSize())
            order.forEach { hole ->
                val at = frame.at(hole.x, hole.y, MancalaBoard.top)
                val open = playable && hole.index in legal
                Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp).semantics {
                    contentDescription = say(spoken(hole))
                    if (open) onClick { onSow(hole.index); true }
                })
            }
            Canvas(Modifier.matchParentSize().pointerInput(g, playable, legal) {
                detectTapGestures { pos ->
                    if (!playable) return@detectTapGestures
                    val hit = order.asReversed().firstOrNull { hole ->
                        hole.index in legal && ovalHit(frame, pos, hole.x, hole.y, MancalaBoard.top, hole.rx + 0.08f, hole.ry + 0.08f)
                    } ?: return@detectTapGestures
                    onSow(hit.index)
                }
            }) {
                order.forEach { hole ->
                    val open = hole.index in legal
                    if (g.last == hole.index) {
                        drawOval(
                            frame, hole.x, hole.y, MancalaBoard.top + 0.02f, hole.rx, hole.ry, c.highlight,
                            strokePx = frame.cell * 0.045f,
                        )
                    }
                    if (playable && open) {
                        drawRing(frame, hole.x, hole.y, MancalaBoard.top + 0.04f, c.highlight, radiusScale = 0.42f)
                    }
                    val countY = when {
                        hole.store -> 2.72f
                        hole.y > 1.8f -> hole.y + hole.ry + 0.28f
                        else -> hole.y - hole.ry - 0.28f
                    }
                    val ink = if (playable && open) c.highlight else Seed
                    countText(measurer, frame, g.pits[hole.index].toString(), hole.x, countY, ink)
                }
                countText(measurer, frame, you, MancalaBoard.hole(Mancala.YOU).x, 3.28f, Seed, bold = false)
                countText(measurer, frame, computer, MancalaBoard.hole(Mancala.CPU).x, 3.28f, Seed, bold = false)
            }
        }
    }
}

private fun DrawScope.drawOval(
    frame: TableFrame, x: Float, y: Float, z: Float, rx: Float, ry: Float, color: Color, strokePx: Float = 0f,
) {
    val path = pathOf(ovalPoints(frame, x, y, z, rx, ry))
    if (strokePx > 0f) drawPath(path, color, style = Stroke(strokePx)) else drawPath(path, color)
}

private fun DrawScope.countText(
    measurer: androidx.compose.ui.text.TextMeasurer,
    frame: TableFrame,
    text: String,
    x: Float,
    y: Float,
    color: Color,
    bold: Boolean = true,
) {
    val px = (frame.unitAt(x, y, MancalaBoard.top) * if (bold) 0.38f else 0.28f).coerceIn(12f, 28f)
    val layout = measurer.measure(
        text,
        TextStyle(color = color, fontSize = px.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
    )
    val at = frame.at(x, y, MancalaBoard.top + 0.05f)
    val left = at.x - layout.size.width / 2f
    val top = at.y - layout.size.height / 2f
    val padX = px * 0.38f
    val padY = px * 0.16f
    drawRoundRect(
        Color(0xE0120C08),
        topLeft = Offset(left - padX, top - padY),
        size = Size(layout.size.width + padX * 2f, layout.size.height + padY * 2f),
        cornerRadius = CornerRadius(px * 0.35f, px * 0.35f),
    )
    drawText(layout, topLeft = Offset(left, top))
}

private fun ovalPoints(frame: TableFrame, x: Float, y: Float, z: Float, rx: Float, ry: Float): List<Offset> {
    val steps = 18
    val turn = (PI * 2.0 / steps).toFloat()
    return List(steps) { i ->
        val a = i * turn
        frame.at(x + cos(a) * rx, y + sin(a) * ry, z)
    }
}

private fun ovalHit(frame: TableFrame, pos: Offset, x: Float, y: Float, z: Float, rx: Float, ry: Float): Boolean {
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
