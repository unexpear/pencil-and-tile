package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.Mancala
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.BoardView
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.Satin
import com.simplegamegen.sudoku.ui.assets.TableFrame
import com.simplegamegen.sudoku.ui.assets.drawMesh
import com.simplegamegen.sudoku.ui.assets.drawRing
import com.simplegamegen.sudoku.ui.assets.sphereMesh
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
        order.forEach { hole ->
            val at = frame.at(hole.x, hole.y, MancalaBoard.top)
            val open = playable && hole.index in legal
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp).semantics {
                contentDescription = say(spoken(hole))
                if (open) onClick { onSow(hole.index); true }
            })
        }
        Canvas(Modifier.matchParentSize().pointerInput(g, playable) {
            detectTapGestures { pos ->
                if (!playable) return@detectTapGestures
                val hit = order.asReversed().firstOrNull { hole ->
                    hole.index in legal && ovalHit(frame, pos, hole.x, hole.y, MancalaBoard.top, hole.rx + 0.08f, hole.ry + 0.08f)
                } ?: return@detectTapGestures
                onSow(hit.index)
            }
        }) {
            drawMancalaWood(frame)
            order.forEach { hole ->
                val count = g.pits[hole.index]
                val open = hole.index in legal
                val landed = g.last == hole.index
                val bowl = when {
                    landed -> lerp(PitColor, c.highlight, 0.42f)
                    open && playable -> lerp(PitColor, c.highlight, 0.22f)
                    else -> PitColor
                }
                drawOval(frame, hole.x, hole.y, MancalaBoard.top + 0.012f, hole.rx, hole.ry, lerp(Wood, Color.Black, 0.45f))
                drawOval(frame, hole.x, hole.y, MancalaBoard.top + 0.02f, hole.rx * 0.86f, hole.ry * 0.86f, bowl)
                if (landed) {
                    drawOval(
                        frame, hole.x, hole.y, MancalaBoard.top + 0.03f, hole.rx, hole.ry, c.highlight,
                        strokePx = frame.cell * 0.045f,
                    )
                }
            }
            val pebbles = buildList {
                for (hole in holes) addAll(pebblesIn(hole, g.pits[hole.index]))
            }.sortedByDescending { frame.depth(it.x, it.y, it.z) }
            pebbles.forEach { pebble ->
                drawMesh(frame, PebbleMesh, pebble.x, pebble.y, pebble.z, pebble.color, Satin)
            }
            order.forEach { hole ->
                if (playable && hole.index in legal) {
                    drawRing(frame, hole.x, hole.y, MancalaBoard.top + 0.42f, c.highlight, radiusScale = 0.42f)
                }
                val countY = when {
                    hole.store -> 2.72f
                    hole.y > 1.8f -> hole.y + hole.ry + 0.28f
                    else -> hole.y - hole.ry - 0.28f
                }
                val ink = if (playable && hole.index in legal) c.highlight else Seed
                countText(measurer, frame, g.pits[hole.index].toString(), hole.x, countY, ink)
            }
            countText(measurer, frame, you, MancalaBoard.hole(Mancala.YOU).x, 3.28f, Seed, bold = false)
            countText(measurer, frame, computer, MancalaBoard.hole(Mancala.CPU).x, 3.28f, Seed, bold = false)
        }
    }
}

private data class Pebble(val x: Float, val y: Float, val z: Float, val color: Color)

private fun pebblesIn(hole: MancalaBoard.Hole, count: Int): List<Pebble> {
    if (count <= 0) return emptyList()
    val cap = if (hole.store) 21 else 8
    val shown = count.coerceAtMost(cap)
    return List(shown) { i ->
        val layer = i / 7
        val k = i % 7
        val ang = k * (PI * 2.0 / 6.0) + layer * 0.55
        val ring = if (k == 0) 0.0 else 0.62
        val dx = cos(ang) * ring * hole.rx * 0.78
        val dy = sin(ang) * ring * hole.ry * 0.72
        val z = MancalaBoard.top + layer * PebbleRadius * 1.35f
        Pebble((hole.x + dx).toFloat(), (hole.y + dy).toFloat(), z, PebbleColors[(i + hole.index) % PebbleColors.size])
    }
}

private fun DrawScope.drawMancalaWood(frame: TableFrame) {
    val top = MancalaBoard.top
    val x0 = 0f
    val x1 = MancalaBoard.width
    val y0 = 0f
    val y1 = MancalaBoard.depth
    val dark = lerp(Wood, Color.Black, 0.34f)
    val side = lerp(Wood, Color.Black, 0.16f)
    data class Face(val nx: Float, val ny: Float, val cx: Float, val cy: Float, val color: Color, val quad: List<Offset>)
    val faces = listOf(
        Face(0f, 1f, (x0 + x1) / 2f, y1, dark, listOf(frame.at(x0, y1, top), frame.at(x1, y1, top), frame.at(x1, y1, 0f), frame.at(x0, y1, 0f))),
        Face(0f, -1f, (x0 + x1) / 2f, y0, dark, listOf(frame.at(x1, y0, top), frame.at(x0, y0, top), frame.at(x0, y0, 0f), frame.at(x1, y0, 0f))),
        Face(1f, 0f, x1, (y0 + y1) / 2f, side, listOf(frame.at(x1, y0, top), frame.at(x1, y1, top), frame.at(x1, y1, 0f), frame.at(x1, y0, 0f))),
        Face(-1f, 0f, x0, (y0 + y1) / 2f, side, listOf(frame.at(x0, y1, top), frame.at(x0, y0, top), frame.at(x0, y0, 0f), frame.at(x0, y1, 0f))),
    )
    faces.filter { frame.facing(it.nx, it.ny, 0f, it.cx, it.cy, top / 2f) }
        .sortedByDescending { frame.depth(it.cx, it.cy, top / 2f) }
        .forEach { drawPath(pathOf(it.quad), it.color) }
    drawPath(pathOf(listOf(frame.at(x0, y0, top), frame.at(x1, y0, top), frame.at(x1, y1, top), frame.at(x0, y1, top))), Wood)
    val grain = lerp(Wood, Color.Black, 0.28f).copy(alpha = 0.28f)
    val w = (frame.cell * 0.012f).coerceAtLeast(0.7f)
    for (k in 1..4) {
        val y = y0 + k * (y1 - y0) / 5f
        drawLine(grain, frame.at(x0 + 0.15f, y, top + 0.004f), frame.at(x1 - 0.15f, y, top + 0.004f), strokeWidth = w)
    }
}

private fun DrawScope.drawOval(
    frame: TableFrame, x: Float, y: Float, z: Float, rx: Float, ry: Float, color: Color, strokePx: Float = 0f,
) {
    val pts = ovalPoints(frame, x, y, z, rx, ry)
    val path = pathOf(pts)
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
    val px = (frame.unitAt(x, y, MancalaBoard.top) * if (bold) 0.38f else 0.28f).coerceIn(11f, 26f)
    val layout = measurer.measure(
        text,
        TextStyle(color = color, fontSize = px.toSp(), fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium),
    )
    val at = frame.at(x, y, MancalaBoard.top + 0.05f)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
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

private val Wood = Color(0xFF8D6E63)
private val PitColor = Color(0xFF2C1810)
private val Seed = Color(0xFFF3E5C4)
private val PebbleColors = listOf(
    Color(0xFFE8D9B5),
    Color(0xFF8FB8DE),
    Color(0xFFE59A8C),
    Color(0xFF9CC79A),
    Color(0xFFD8B4E2),
)
private const val PebbleRadius = 0.055f
private val PebbleMesh by lazy { sphereMesh(PebbleRadius, rings = 8) }
