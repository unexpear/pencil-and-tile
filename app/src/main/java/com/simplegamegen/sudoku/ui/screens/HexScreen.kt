package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.Hex
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val HexRed = Color(0xFFE53935)
private val HexBlue = Color(0xFF1565C0)
private val HexPaper = Color(0xFFF6EBD2)
private val HexEdge = Color(0xFFB9A07A)
private const val SQRT3 = 1.7320508f

private val HexStrength = listOf(
    "Places near the stones, and sometimes misses a connection",
    "Takes a win, blocks yours, and swaps a strong opening",
    "Looks a couple of moves ahead",
    "Searches further for the shorter connection",
)

val HexSetup: (PuzzleFactory) -> PlaySetup<Hex> = { factory ->
    PlaySetup(
        rules = "Take turns placing one stone on an empty hex. Stones never move or get captured. " +
            "Red joins the top and bottom edges. Blue joins the left and right. " +
            "Six neighbors touch; a square diagonal does not connect. " +
            "After the first stone, the second player may swap: the stone flips across the long diagonal and becomes theirs. " +
            "You play red against the computer and move first. Or pass the phone for two players. A full board always has a winner.",
        settingTitle = "Board",
        settings = Hex.NAMES,
        describe = { if (Hex.passOf(it)) "Pass the phone. Red moves first and joins top to bottom." else HexStrength[Hex.levelOf(it)] },
        settingOf = { it.setting },
        inProgress = { !it.over && it.cells.any { cell -> cell != 0 } },
        subtitle = { hexSubtitle(it) },
        create = { i -> { factory.custom("hex:$i", { seed -> Hex.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.over) null
            else if (g.passAndPlay) Outcome(Result.FINISHED)
        else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW })
        },
    )
}

private fun hexSubtitle(g: Hex): String {
    val red = g.cells.count { it == 1 }
    val blue = g.cells.count { it == -1 }
    val who = if (g.passAndPlay) "two players" else Hex.LEVELS[g.level]
    return "${g.size}×${g.size} $who · red $red, blue $blue"
}

@Composable
fun HexScreen(nav: NavController, vm: PlayViewModel<Hex>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var full by rememberSaveable { mutableStateOf(false) }
    PlayShell(
        nav, vm, GameId.HEX, remember(factory) { HexSetup(factory) },
        scroll = false,
        tight = true,
        tools = { _, _ ->
            ToolButton(GameIcons.Fit, if (full) "Exit full screen" else "Full screen") { full = !full }
        },
    ) { g, s ->
        val human = g.passAndPlay || g.turn == 1
        val playable = !g.over && human && !s.thinking && !s.busy
        val winning = remember(g) { g.winningPath().toSet() }
        val red = g.cells.count { it == 1 }
        val blue = g.cells.count { it == -1 }
        PlayBoard(
            full, { full = false }, s.canUndo && !s.busy, vm::undo,
            above = {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InfoChip(hexStatus(g, s.thinking), emphasized = true)
                    InfoChip(if (g.passAndPlay) "Red $red" else "Red (you) $red")
                    InfoChip("Blue $blue")
                    if (g.swapped) InfoChip("Swapped")
                }
            },
            below = {
                if (playable && g.canSwap) {
                    Button(
                        onClick = { vm.play { it.swap() } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Swap the opening stone") }
                    Text(
                        "Or tap an empty hex. Swap flips that stone across the long diagonal and turns it blue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                } else if (!g.over && playable) {
                    Text("Tap the nearest empty hex.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                } else if (g.over && g.winner != 0) {
                    Text(
                        if (g.winner == 1) "Red joins the top and bottom." else "Blue joins the left and right.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.success,
                    )
                }
            },
        ) {
            HexBoard(
                g, playable, winning,
                onPlace = { index -> vm.play { it.place(index) } },
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        }
    }
}

private fun hexStatus(g: Hex, thinking: Boolean): String = when {
    g.over && g.winner == 1 -> if (g.passAndPlay) "Red connects" else "You won!"
    g.over && g.winner == -1 -> if (g.passAndPlay) "Blue connects" else "Computer won"
    g.passAndPlay && g.turn == 1 -> "Red's turn"
    g.passAndPlay -> "Blue's turn"
    g.turn == 1 -> "Your turn"
    thinking -> "Computer is thinking…"
    else -> "Computer's turn"
}

@Composable
private fun HexBoard(g: Hex, playable: Boolean, winning: Set<Int>, onPlace: (Int) -> Unit, modifier: Modifier = Modifier) {
    val spoken = buildList {
        add("Hex board")
        add("${g.size} by ${g.size}")
        add(if (g.turn == 1) "Red to move" else "Blue to move")
        add("${g.cells.count { it == 1 }} red")
        add("${g.cells.count { it == -1 }} blue")
        if (g.canSwap) add("Swap is available")
    }.joinToString(" · ")
    Canvas(
        modifier.semantics { contentDescription = say(spoken) }
            .pointerInput(g, playable) {
                detectTapGestures { pos ->
                    if (!playable) return@detectTapGestures
                    val layout = hexLayout(size.width.toFloat(), size.height.toFloat(), g.size)
                    val hit = nearestHex(layout, pos) ?: return@detectTapGestures
                    if (g.cells[hit] != 0) return@detectTapGestures
                    onPlace(hit)
                }
            },
    ) {
        val layout = hexLayout(size.width, size.height, g.size)
        for (index in g.cells.indices) {
            val at = hexCenter(layout, index)
            hexagon(at, layout.radius, HexPaper)
            hexagon(at, layout.radius, HexEdge, Stroke(layout.radius * 0.06f))
        }
        for (index in g.cells.indices) edgeMarks(layout, index, hexCenter(layout, index))
        for (index in g.cells.indices) {
            val who = g.cells[index]
            if (who == 0) continue
            val at = hexCenter(layout, index)
            val stone = if (who == 1) HexRed else HexBlue
            drawCircle(Color.Black.copy(alpha = 0.18f), layout.radius * 0.58f, at + Offset(1.2f, 1.6f))
            drawCircle(stone, layout.radius * 0.56f, at)
            drawCircle(Color.White.copy(alpha = 0.35f), layout.radius * 0.22f, at + Offset(-layout.radius * 0.16f, -layout.radius * 0.18f))
            if (index in winning) drawCircle(Color.White, layout.radius * 0.72f, at, style = Stroke(layout.radius * 0.1f))
            if (index == g.last) drawCircle(Color.White, layout.radius * 0.16f, at)
        }
    }
}

private data class HexLayout(val radius: Float, val origin: Offset, val n: Int)

private fun hexLayout(width: Float, height: Float, n: Int): HexLayout {
    // Extra radius outside the vertex bounds, so the colored edge strokes are not clipped.
    val extra = 0.34f
    val widthFactor = (3f * n + 1f) / 2f + extra * 2f
    val heightFactor = SQRT3 * (3f * n - 1f) / 2f + extra * 2f
    val radius = min(width / widthFactor, height / heightFactor).coerceAtLeast(1f)
    val boardW = radius * widthFactor
    val boardH = radius * heightFactor
    val origin = Offset((width - boardW) / 2f + extra * radius, (height - boardH) / 2f + extra * radius)
    return HexLayout(radius, origin, n)
}

private fun hexCenter(layout: HexLayout, index: Int): Offset {
    val row = index / layout.n
    val col = index % layout.n
    val x = layout.origin.x + layout.radius + layout.radius * 1.5f * col
    val y = layout.origin.y + layout.radius * SQRT3 / 2f + layout.radius * SQRT3 * (row + col / 2f)
    return Offset(x, y)
}

private fun nearestHex(layout: HexLayout, pos: Offset): Int? {
    var best = -1
    var bestD = Float.MAX_VALUE
    val limit = layout.radius * 1.15f
    for (index in 0 until layout.n * layout.n) {
        val at = hexCenter(layout, index)
        val d = hypot(pos.x - at.x, pos.y - at.y)
        if (d < bestD) {
            bestD = d
            best = index
        }
    }
    return if (best >= 0 && bestD <= limit) best else null
}

private fun DrawScope.hexagon(center: Offset, radius: Float, color: Color, style: DrawStyle = Fill) {
    val path = Path()
    for (i in 0 until 6) {
        val p = vertex(center, radius, 60 * i)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color, style = style)
}

private fun vertex(center: Offset, radius: Float, deg: Int): Offset {
    val angle = Math.toRadians(deg.toDouble())
    return Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
}

private fun DrawScope.edgeMarks(layout: HexLayout, index: Int, center: Offset) {
    val row = index / layout.n
    val col = index % layout.n
    val outer = layout.radius * 1.08f
    val stroke = Stroke(layout.radius * 0.22f, cap = StrokeCap.Round)
    if (row == 0) edgeSide(center, outer, 240, 300, HexRed, stroke)
    if (row == layout.n - 1) edgeSide(center, outer, 60, 120, HexRed, stroke)
    if (col == 0) {
        edgeSide(center, outer, 120, 180, HexBlue, stroke)
        edgeSide(center, outer, 180, 240, HexBlue, stroke)
    }
    if (col == layout.n - 1) {
        edgeSide(center, outer, 0, 60, HexBlue, stroke)
        edgeSide(center, outer, 300, 360, HexBlue, stroke)
    }
}

private fun DrawScope.edgeSide(center: Offset, radius: Float, from: Int, to: Int, color: Color, stroke: Stroke) {
    val a = vertex(center, radius, from)
    val b = vertex(center, radius, to)
    drawLine(color, a, b, stroke.width, cap = stroke.cap)
}
