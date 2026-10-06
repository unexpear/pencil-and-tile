package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.simplegamegen.sudoku.duels.Rps
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val RpsRed = Color(0xFFE53935)
private val RpsBlue = Color(0xFF1565C0)
private val RpsPaper = Color(0xFFF6EBD2)
private val RpsEdge = Color(0xFFB9A07A)
private val RpsInk = Color(0xFFFFF8EC)
private val RpsCapture = Color(0xFF2E7D32)
private const val SQRT3 = 1.7320508f

private val RpsStrength = listOf(
    "Prefers a safe capture, and sometimes steps next to a type that beats it",
    "Takes safe captures and looks a move ahead",
    "Looks several moves ahead and keeps its pieces safe",
    "Searches deeper for a capture that stays safe",
)

val RpsSetup: (PuzzleFactory) -> PlaySetup<Rps> = { factory ->
    PlaySetup(
        rules = "Each side starts with rock, paper, and scissors on a shared hex board. " +
            "On your turn, move one piece exactly one hex in any of the six directions. " +
            "Land on an empty hex, or on an adjacent enemy your type beats: paper beats rock, rock beats scissors, and scissors beats paper. " +
            "You cannot jump, and you cannot land on your own piece, the same type, or a type that beats you. Landing captures. " +
            "Win by capturing every enemy piece, or by leaving the opponent with no legal move. " +
            "You play red from the bottom against the computer and move first. Or pass the phone for two players.",
        settingTitle = "Board",
        settings = Rps.NAMES,
        describe = { if (Rps.passOf(it)) "Pass the phone. Red moves first from the bottom." else RpsStrength[Rps.levelOf(it)] },
        settingOf = { it.setting },
        inProgress = { !it.over && it.lastFrom != null },
        subtitle = { rpsSubtitle(it) },
        create = { i -> { factory.custom("rps:$i", { seed -> Rps.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.over) null
            else if (g.passAndPlay) Outcome(Result.FINISHED)
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW })
        },
    )
}

private fun rpsSubtitle(g: Rps): String {
    val who = if (g.passAndPlay) "two players" else Rps.LEVELS[g.level]
    val red = g.cells.count { it > 0 }
    val blue = g.cells.count { it < 0 }
    return "${g.size}×${g.size} $who · red $red, blue $blue"
}

@Composable
fun RpsScreen(nav: NavController, vm: PlayViewModel<Rps>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var full by rememberSaveable { mutableStateOf(false) }
    PlayShell(
        nav, vm, GameId.RPS, remember(factory) { RpsSetup(factory) },
        scroll = false,
        tight = true,
        tools = { _, _ ->
            ToolButton(GameIcons.Fit, if (full) "Exit full screen" else "Full screen") { full = !full }
        },
    ) { g, s ->
        val human = g.passAndPlay || g.turn == 1
        val playable = !g.over && human && !s.thinking && !s.busy
        var selected by remember(g) { mutableStateOf<Int?>(null) }
        val dests = remember(g, selected) { selected?.let { g.destinations(it).toSet() } ?: emptySet() }
        val highlight = c.highlight
        PlayBoard(
            full, { full = false }, s.canUndo && !s.busy, vm::undo,
            above = {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InfoChip(rpsStatus(g, s.thinking), emphasized = true)
                    InfoChip(roster(if (g.passAndPlay) "Red" else "Red (you)", g, 1))
                    InfoChip(roster("Blue", g, -1))
                    if (Rps.KINDS.any { g.taken(1, it) > 0 }) InfoChip(took("Red", g, 1))
                    if (Rps.KINDS.any { g.taken(-1, it) > 0 }) InfoChip(took("Blue", g, -1))
                }
            },
            below = {
                val line = when {
                    g.over -> rpsWhy(g)
                    selected != null -> rpsHint(g.cells[selected!!])
                    else -> "Paper beats rock, rock beats scissors, and scissors beats paper."
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = if (g.over && g.winner == 1) c.success else c.muted)
            },
        ) {
            RpsBoard(
                g, playable, selected, dests, highlight,
                onSelect = { selected = it },
                onMove = { from, to -> vm.play { it.play(from, to) } },
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        }
    }
}

private fun roster(label: String, g: Rps, side: Int) =
    "$label: ${g.count(side, Rps.ROCK)} rock, ${g.count(side, Rps.PAPER)} paper, ${g.count(side, Rps.SCISSORS)} scissors"

private fun took(label: String, g: Rps, side: Int) =
    "$label took ${g.taken(side, Rps.ROCK)} rock, ${g.taken(side, Rps.PAPER)} paper, ${g.taken(side, Rps.SCISSORS)} scissors"

private fun rpsStatus(g: Rps, thinking: Boolean): String = when {
    g.over && g.winner == 1 -> if (g.passAndPlay) "Red wins" else "You won!"
    g.over && g.winner == -1 -> if (g.passAndPlay) "Blue wins" else "Computer won"
    g.passAndPlay && g.turn == 1 -> "Red's turn"
    g.passAndPlay -> "Blue's turn"
    g.turn == 1 -> "Your turn"
    thinking -> "Computer is thinking…"
    else -> "Computer's turn"
}

private fun rpsWhy(g: Rps): String {
    val gone = g.cells.none { Rps.sideOf(it) == -g.winner }
    return when {
        gone && g.winner == 1 -> "Every blue piece was captured."
        gone -> "Every red piece was captured."
        g.winner == 1 -> "Blue has no legal move."
        else -> "Red has no legal move."
    }
}

private fun rpsHint(cell: Int): String = when (abs(cell)) {
    Rps.ROCK -> "Rock selected. Tap a highlighted hex."
    Rps.PAPER -> "Paper selected. Tap a highlighted hex."
    else -> "Scissors selected. Tap a highlighted hex."
}

@Composable
private fun RpsBoard(
    g: Rps,
    playable: Boolean,
    selected: Int?,
    dests: Set<Int>,
    highlight: Color,
    onSelect: (Int?) -> Unit,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spoken = buildList {
        add("Rock Paper Scissors board")
        add("${g.size} by ${g.size}")
        add(if (g.turn == 1) "Red to move" else "Blue to move")
        add("Red has ${g.count(1, Rps.ROCK)} rock, ${g.count(1, Rps.PAPER)} paper, ${g.count(1, Rps.SCISSORS)} scissors")
        add("Blue has ${g.count(-1, Rps.ROCK)} rock, ${g.count(-1, Rps.PAPER)} paper, ${g.count(-1, Rps.SCISSORS)} scissors")
    }.joinToString(" · ")
    Canvas(
        modifier.semantics { contentDescription = say(spoken) }
            .pointerInput(g, playable, selected) {
                detectTapGestures { pos ->
                    if (!playable) return@detectTapGestures
                    val layout = rpsLayout(size.width.toFloat(), size.height.toFloat(), g.size)
                    val hit = nearestRps(layout, pos) ?: return@detectTapGestures
                    val from = selected
                    when {
                        from != null && hit in g.destinations(from) -> onMove(from, hit)
                        Rps.sideOf(g.cells[hit]) == g.turn && g.destinations(hit).isNotEmpty() -> onSelect(if (from == hit) null else hit)
                        else -> onSelect(null)
                    }
                }
            },
    ) {
        val layout = rpsLayout(size.width, size.height, g.size)
        for (index in g.cells.indices) {
            val at = rpsCenter(layout, index)
            val fill = when {
                index == selected -> highlight.copy(alpha = 0.55f)
                index in dests -> highlight.copy(alpha = 0.28f)
                else -> RpsPaper
            }
            hexagon(at, layout.radius, fill)
            hexagon(at, layout.radius, RpsEdge, Stroke(layout.radius * 0.06f))
            if (index == g.lastFrom || index == g.lastTo) {
                hexagon(at, layout.radius * 0.92f, highlight, Stroke(layout.radius * 0.09f))
            }
        }
        for (index in g.cells.indices) {
            if (index in dests && g.cells[index] == 0) {
                drawCircle(highlight, layout.radius * 0.22f, rpsCenter(layout, index))
            }
        }
        for (index in g.cells.indices) {
            val cell = g.cells[index]
            if (cell == 0) continue
            val at = rpsCenter(layout, index)
            drawRpsPiece(at, layout.radius, cell)
            if (index in dests) {
                drawCircle(RpsCapture, layout.radius * 0.84f, at, style = Stroke(layout.radius * 0.1f))
            }
        }
    }
}

private fun DrawScope.drawRpsPiece(center: Offset, radius: Float, cell: Int) {
    val team = if (cell > 0) RpsRed else RpsBlue
    val r = radius * 0.72f
    drawCircle(Color.Black.copy(alpha = 0.18f), r, center + Offset(1.1f, 1.5f))
    drawCircle(team, r, center)
    when (abs(cell)) {
        Rps.ROCK -> {
            drawCircle(RpsInk, r * 0.58f, center + Offset(-r * 0.04f, r * 0.04f))
            drawLine(team, center + Offset(-r * 0.26f, -r * 0.16f), center + Offset(r * 0.02f, r * 0.2f), r * 0.16f, StrokeCap.Round)
            drawLine(team, center + Offset(r * 0.2f, -r * 0.26f), center + Offset(r * 0.16f, r * 0.12f), r * 0.13f, StrokeCap.Round)
        }
        Rps.PAPER -> {
            val w = r * 0.92f
            val h = r * 1.12f
            val o = Offset(center.x - w / 2f, center.y - h / 2f)
            drawRoundRect(RpsInk, o, Size(w, h), CornerRadius(r * 0.16f, r * 0.16f))
            val fold = r * 0.32f
            val ear = Path().apply {
                moveTo(o.x + w - fold, o.y)
                lineTo(o.x + w, o.y + fold)
                lineTo(o.x + w - fold, o.y + fold)
                close()
            }
            drawPath(ear, team)
        }
        else -> {
            val sw = r * 0.16f
            drawCircle(RpsInk, r * 0.26f, center + Offset(-r * 0.26f, r * 0.3f), style = Stroke(sw))
            drawCircle(RpsInk, r * 0.26f, center + Offset(r * 0.26f, r * 0.3f), style = Stroke(sw))
            drawLine(RpsInk, center + Offset(-r * 0.06f, r * 0.06f), center + Offset(r * 0.48f, -r * 0.52f), sw, StrokeCap.Round)
            drawLine(RpsInk, center + Offset(r * 0.06f, r * 0.06f), center + Offset(-r * 0.48f, -r * 0.52f), sw, StrokeCap.Round)
            drawCircle(team, r * 0.09f, center)
        }
    }
}

private data class RpsLayout(val radius: Float, val origin: Offset, val n: Int)

private fun rpsLayout(width: Float, height: Float, n: Int): RpsLayout {
    val extra = 0.34f
    val widthFactor = (3f * n + 1f) / 2f + extra * 2f
    val heightFactor = SQRT3 * (3f * n - 1f) / 2f + extra * 2f
    val radius = min(width / widthFactor, height / heightFactor).coerceAtLeast(1f)
    val boardW = radius * widthFactor
    val boardH = radius * heightFactor
    val origin = Offset((width - boardW) / 2f + extra * radius, (height - boardH) / 2f + extra * radius)
    return RpsLayout(radius, origin, n)
}

private fun rpsCenter(layout: RpsLayout, index: Int): Offset {
    val row = index / layout.n
    val col = index % layout.n
    val x = layout.origin.x + layout.radius + layout.radius * 1.5f * col
    val y = layout.origin.y + layout.radius * SQRT3 / 2f + layout.radius * SQRT3 * (row + col / 2f)
    return Offset(x, y)
}

private fun nearestRps(layout: RpsLayout, pos: Offset): Int? {
    var best = -1
    var bestD = Float.MAX_VALUE
    val limit = layout.radius * 1.15f
    for (index in 0 until layout.n * layout.n) {
        val at = rpsCenter(layout, index)
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
        val p = rpsVertex(center, radius, 60 * i)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, color, style = style)
}

private fun rpsVertex(center: Offset, radius: Float, deg: Int): Offset {
    val angle = Math.toRadians(deg.toDouble())
    return Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
}
