package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.components.ZoomBox
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.DotsGame
import com.simplegamegen.sudoku.duels.MagnetGame
import com.simplegamegen.sudoku.duels.Pt
import com.simplegamegen.sudoku.duels.SproutMove
import com.simplegamegen.sudoku.duels.SproutsGame
import com.simplegamegen.sudoku.duels.Stone
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.abs
import kotlin.math.hypot
import com.simplegamegen.sudoku.ui.i18n.say

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuelStatus(over: Boolean, winner: Int, turn: Int, thinking: Boolean, you: String, cpu: String) {
    val c = LocalGameLook.current.colors
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            over -> InfoChip(when (winner) { 1 -> "You won!"; -1 -> "Computer won"; else -> "Draw" }, emphasized = true)
            turn == 1 -> InfoChip("Your turn", emphasized = true)
            else -> InfoChip(if (thinking) "Computer is thinking…" else "Computer's turn")
        }
        InfoChip(you); InfoChip(cpu)
    }
    if (over) Text(when (winner) { 1 -> "Well played! Start a new game to go again."; -1 -> "The computer won this one."; else -> "It's a draw." },
        color = if (winner == 1) c.success else c.muted, style = MaterialTheme.typography.titleMedium)
}

// ---------------- Dots and Boxes ----------------

val DotsSetup: (PuzzleFactory) -> PlaySetup<DotsGame> = { factory ->
    PlaySetup(
        rules = "Take turns drawing a line between two neighboring dots. Whoever completes the fourth side of a box claims it and " +
            "moves again. When every line is drawn, the player with more boxes wins. Tap one dot, then a neighboring dot. Turn off Tap two dots to tap between them instead.",
        settingTitle = "Level", settings = DotsGame.NAMES,
        describe = { listOf("Small board; the computer sometimes misses boxes", "Avoids handing you boxes", "Gives away as little as possible; plays the ending perfectly", "Largest board; searches the ending exactly")[it] },
        settingOf = { it.setting }, inProgress = { !it.over && it.drawn.isNotEmpty() },
        subtitle = { "${DotsGame.NAMES[it.setting]} · you ${it.score(1)}, computer ${it.score(-1)}" },
        create = { i -> { factory.custom("dots:$i", { seed -> DotsGame.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (!g.over) null else Outcome(when { g.score(1) > g.score(-1) -> Result.WON; g.score(1) < g.score(-1) -> Result.LOST; else -> Result.DRAW }, g.score(1).toLong()) },
    )
}

@Composable
fun DotsScreen(nav: NavController, vm: PlayViewModel<DotsGame>, factory: PuzzleFactory) {
    val look = LocalGameLook.current
    val c = look.colors
    var tapDots by rememberSaveable { mutableStateOf(true) }
    PlayShell(nav, vm, GameId.DOTS, remember(factory) { DotsSetup(factory) }) { g, s ->
        val winner = if (!g.over) 0 else g.score(1).compareTo(g.score(-1))
        DuelStatus(g.over, winner, g.turn, s.thinking, "You ${g.score(1)}", "Computer ${g.score(-1)}")
        val latest by rememberUpdatedState(g)
        val playable = !g.over && g.turn == 1 && !s.thinking
        var selectedDot by remember(g.seed, g.drawn, tapDots) { mutableStateOf<Int?>(null) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Tap two dots", Modifier.weight(1f))
            Switch(checked = tapDots, onCheckedChange = { tapDots = it },
                modifier = Modifier.semantics { contentDescription = say("Tap two dots") })
        }
        Text(if (!tapDots) "Tap between neighboring dots to draw a line."
            else "Tap two neighboring dots to connect. Tap a selected dot again to cancel.",
            style = MaterialTheme.typography.bodySmall, color = c.muted)
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)
                    .semantics { contentDescription = say("Dots and boxes board, ${latest.drawn.size} of ${latest.edgeCount} lines drawn") }
                    .pointerInput(playable, tapDots, g.seed) {
                        detectTapGestures { pos ->
                            if (!playable) return@detectTapGestures
                            val game = latest
                            val step = size.width / (game.cols + 0.6f); val o = step * 0.3f
                            val x = (pos.x - o) / step; val y = (pos.y - o) / step
                            if (tapDots) {
                                val row = Math.round(y); val col = Math.round(x)
                                if (row !in 0..game.rows || col !in 0..game.cols || hypot(x - col, y - row) > 0.35f) return@detectTapGestures
                                val dot = row * (game.cols + 1) + col
                                val first = selectedDot
                                val edge = first?.let { dotsEdgeBetween(game, it, dot) }
                                when {
                                    first == dot -> selectedDot = null
                                    edge != null -> { selectedDot = null; vm.play { it.draw(edge) } }
                                    else -> selectedDot = dot
                                }
                                return@detectTapGestures
                            }
                            // Nearest line: horizontals sit on whole-number rows, verticals on whole-number columns.
                            val candidates = buildList {
                                val r = Math.round(y); val cc = x.toInt()
                                if (r in 0..game.rows && cc in 0 until game.cols) add(game.h(r, cc) to abs(y - r))
                                val col = Math.round(x); val rr = y.toInt()
                                if (col in 0..game.cols && rr in 0 until game.rows) add(game.v(rr, col) to abs(x - col))
                            }
                            candidates.filter { it.second < 0.35f && it.first !in game.drawn }.minByOrNull { it.second }?.let { (e, _) -> vm.play { it.draw(e) } }
                        }
                    }) {
                    val step = size.width / (g.cols + 0.6f); val o = step * 0.3f
                    fun p(r: Int, col: Int) = Offset(o + col * step, o + r * step)
                    g.owner.forEachIndexed { b, who ->
                        if (who == 0) return@forEachIndexed
                        val tl = p(b / g.cols, b % g.cols)
                        drawRect((if (who == 1) c.playerOne else c.playerTwo).copy(alpha = 0.55f), tl + Offset(3f, 3f), Size(step - 6f, step - 6f))
                    }
                    for (e in 0 until g.edgeCount) {
                        val (a, b) = if (e < g.horizontals) { val r = e / g.cols; val col = e % g.cols; p(r, col) to p(r, col + 1) }
                            else { val k = e - g.horizontals; val r = k / (g.cols + 1); val col = k % (g.cols + 1); p(r, col) to p(r + 1, col) }
                        if (e in g.drawn) drawLine(if (e == g.last) c.highlight else c.onTable, a, b, strokeWidth = step * 0.09f, cap = StrokeCap.Round)
                        else drawLine(c.onTable.copy(alpha = 0.15f), a, b, strokeWidth = step * 0.03f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                    }
                    for (r in 0..g.rows) for (col in 0..g.cols) drawCircle(c.onTable, step * 0.08f, p(r, col))
                    selectedDot?.let { dot ->
                        drawCircle(c.highlight, step * 0.16f, p(dot / (g.cols + 1), dot % (g.cols + 1)), style = Stroke(step * 0.05f))
                    }
                }
            }
        }
    }
}

/** Converts two neighboring dots to an unclaimed edge; diagonals and row wrapping are invalid. */
internal fun dotsEdgeBetween(game: DotsGame, first: Int, second: Int): Int? {
    val width = game.cols + 1
    val count = (game.rows + 1) * width
    if (first !in 0 until count || second !in 0 until count) return null
    val r1 = first / width; val c1 = first % width
    val r2 = second / width; val c2 = second % width
    val edge = when {
        r1 == r2 && abs(c1 - c2) == 1 -> game.h(r1, minOf(c1, c2))
        c1 == c2 && abs(r1 - r2) == 1 -> game.v(minOf(r1, r2), c1)
        else -> return null
    }
    return edge.takeIf { it !in game.drawn }
}

// ---------------- Magnetic cluster ----------------

val MagnetSetup: (PuzzleFactory) -> PlaySetup<MagnetGame> = { factory ->
    PlaySetup(
        rules = "You and the computer each have ${MagnetGame.HAND} magnetic stones. Take turns placing one inside the ring. " +
            "If your stone lands within the faint pull circle of another stone, they snap together and you take the whole cluster back. " +
            "Place all your stones first to win. Tap once to preview a spot, tap again to place.",
        settingTitle = "Computer strength", settings = MagnetGame.NAMES,
        describe = { listOf("Picks spots almost at random", "Looks for open space", "Keeps its distance and crowds you", "Plans for the endgame space")[it] },
        settingOf = { it.setting }, inProgress = { !it.over && it.stones.isNotEmpty() },
        subtitle = { "${MagnetGame.NAMES[it.setting]} computer · you ${it.hand(1)} left, computer ${it.hand(-1)} left" },
        create = { i -> { factory.custom("magnets:$i", { seed -> MagnetGame.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.over) Outcome(if (g.winner == 1) Result.WON else Result.LOST) else null },
    )
}

@Composable
fun MagnetScreen(nav: NavController, vm: PlayViewModel<MagnetGame>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var preview by remember { mutableStateOf<Stone?>(null) }
    PlayShell(nav, vm, GameId.MAGNETS, remember(factory) { MagnetSetup(factory) }) { g, s ->
        LaunchedEffect(g.stones.size, g.turn) { preview = null }
        DuelStatus(g.over, g.winner, g.turn, s.thinking, "You: ${g.hand(1)} to place", "Computer: ${g.hand(-1)}")
        if (g.snapped.isNotEmpty() && !g.over) Text((if (g.snappedBy == 1) "Snap! ${g.snapped.size} stones went back into your hand." else "Snap! ${g.snapped.size} stones went back to the computer."),
            color = if (g.snappedBy == 1) c.danger else c.success)
        val latest by rememberUpdatedState(g)
        val pending by rememberUpdatedState(preview)
        val playable = !g.over && g.turn == 1 && !s.thinking
        val cluster = preview?.let { p -> if (g.legal(p)) g.cluster(p) else null }
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)
                    .semantics { contentDescription = say("Magnet ring with ${latest.stones.size} stones. Tap to preview a spot, tap again to place.") }
                    .pointerInput(playable) {
                        detectTapGestures { pos ->
                            if (!playable) return@detectTapGestures
                            val half = size.width / 2f
                            val p = Stone((pos.x - half) / half, (pos.y - half) / half)
                            val prev = pending
                            if (prev != null && hypot(prev.x - p.x, prev.y - p.y) < MagnetGame.RADIUS * 1.5f && latest.legal(prev)) {
                                vm.play { it.place(prev) }; preview = null
                            } else preview = p
                        }
                    }) {
                    val half = size.width / 2f
                    fun at(st: Stone) = Offset(half + st.x * half, half + st.y * half)
                    drawCircle(c.tableInset, half)
                    drawCircle(Color(0xFFD9C9A3), half, style = Stroke(half * 0.03f))
                    g.stones.forEach { st ->
                        drawCircle(c.onTable.copy(alpha = 0.12f), MagnetGame.PULL * half, at(st))
                        drawCircle(c.onTable.copy(alpha = 0.35f), MagnetGame.PULL * half, at(st), style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                    }
                    g.stones.forEach { st ->
                        val r = MagnetGame.RADIUS * half
                        drawCircle(Color.Black.copy(alpha = 0.35f), r, at(st) + Offset(0f, r * 0.2f))
                        drawCircle(Brush.radialGradient(listOf(Color(0xFF8A8F98), Color(0xFF2B2E33)), at(st) - Offset(r * 0.3f, r * 0.3f), r * 1.2f), r, at(st))
                        if (cluster != null && st in cluster) drawCircle(c.danger, r * 1.25f, at(st), style = Stroke(3f))
                    }
                    preview?.let { p ->
                        val ok = g.legal(p)
                        val r = MagnetGame.RADIUS * half
                        drawCircle((if (!ok) c.danger else if ((cluster?.size ?: 1) > 1) c.danger else c.highlight).copy(alpha = 0.7f), r, at(p))
                        drawCircle(Color.White, r, at(p), style = Stroke(2f))
                    }
                }
            }
        }
        Text(when {
            preview == null -> "Tap inside the ring to preview a spot."
            cluster == null -> "Not there: stones can't overlap or leave the ring."
            cluster.size > 1 -> (if (cluster.size == 2) "Careful: 1 stone would snap to it. Tap again to place anyway." else "Careful: ${cluster.size - 1} stones would snap to it. Tap again to place anyway.")
            else -> "Safe spot. Tap it again to place your stone."
        }, style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

// ---------------- Sprouts ----------------

val SproutsSetup: (PuzzleFactory) -> PlaySetup<SproutsGame> = { factory ->
    PlaySetup(
        rules = "Take turns drawing a line between two spots, or from a spot back to itself. Lines may not cross each other or pass " +
            "through a spot, and a new spot sprouts in the middle of each line. A spot can have at most three line ends. " +
            "Whoever draws the last possible line wins. Tap two spots, optionally tap a point to steer the line, then Draw.",
        settingTitle = "Level", settings = SproutsGame.NAMES,
        describe = { listOf("2 spots · easy computer", "3 spots · takes winning moves", "4 spots · avoids losing replies", "5 spots · looks further")[it] },
        settingOf = { it.setting }, inProgress = { !it.over && it.curves.isNotEmpty() },
        subtitle = { SproutsGame.NAMES[it.setting] },
        create = { i -> { factory.custom("sprouts:$i", { seed -> SproutsGame.start(seed, i) }) { it.dots.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.over) Outcome(if (g.winner == 1) Result.WON else Result.LOST) else null },
    )
}

@Composable
fun SproutsScreen(nav: NavController, vm: PlayViewModel<SproutsGame>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var first by remember { mutableStateOf<Int?>(null) }
    var second by remember { mutableStateOf<Int?>(null) }
    var via by remember { mutableStateOf<Pt?>(null) }
    PlayShell(nav, vm, GameId.SPROUTS, remember(factory) { SproutsSetup(factory) }) { g, s ->
        LaunchedEffect(g.curves.size) { first = null; second = null; via = null }
        val alive = g.degree.count { it < 3 }
        DuelStatus(g.over, g.winner, g.turn, s.thinking, "${g.dots.size} spots", "$alive with room")
        val latest by rememberUpdatedState(g)
        val playable = !g.over && g.turn == 1 && !s.thinking
        val move = first?.let { a -> second?.let { b -> SproutMove(minOf(a, b), maxOf(a, b), via) } }
        val route = move?.let { g.route(it) }
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)
                    .semantics { contentDescription = say("Sprouts board with ${latest.dots.size} spots and ${latest.curves.size / 2} lines") }
                    .pointerInput(playable) {
                        detectTapGestures { pos ->
                            if (!playable) return@detectTapGestures
                            val p = Pt(pos.x / size.width, pos.y / size.height)
                            val dot = latest.dots.indices.filter { latest.degree[it] < 3 }
                                .minByOrNull { hypot(latest.dots[it].x - p.x, latest.dots[it].y - p.y) }
                                ?.takeIf { hypot(latest.dots[it].x - p.x, latest.dots[it].y - p.y) < 0.06f }
                            when {
                                dot != null && first == null -> first = dot
                                dot != null && second == null -> second = dot
                                dot != null -> { first = dot; second = null; via = null }
                                first != null -> via = p
                            }
                        }
                    }) {
                    val w = size.width
                    fun at(p: Pt) = Offset(p.x * w, p.y * w)
                    g.curves.forEach { curve ->
                        val path = Path().apply { moveTo(at(curve[0]).x, at(curve[0]).y); curve.drop(1).forEach { lineTo(at(it).x, at(it).y) } }
                        drawPath(path, c.onTable, style = Stroke(w * 0.012f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    route?.let { r ->
                        val path = Path().apply { moveTo(at(r[0]).x, at(r[0]).y); r.drop(1).forEach { lineTo(at(it).x, at(it).y) } }
                        drawPath(path, c.highlight, style = Stroke(w * 0.012f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))))
                        drawCircle(c.highlight, w * 0.02f, at(r[r.size / 2]))
                    }
                    via?.let { drawCircle(c.highlight, w * 0.012f, at(it)) }
                    g.dots.forEachIndexed { i, d ->
                        val lives = g.lives(i)
                        val selected = i == first || i == second
                        drawCircle(if (lives == 0) c.onTable.copy(alpha = 0.35f) else Color.White, w * 0.022f, at(d))
                        drawCircle(if (selected) c.highlight else c.onTable, w * 0.022f, at(d), style = Stroke(if (selected) 4f else 2f))
                        // Small ticks show remaining line ends.
                        repeat(lives) { k -> drawCircle(c.accent, w * 0.005f, at(d) + Offset((k - (lives - 1) / 2f) * w * 0.012f, w * 0.038f)) }
                    }
                }
            }
        }
        Text(when {
            first == null -> "Tap a spot to start a line."
            second == null -> "Tap another spot, or the same spot for a loop."
            route == null && first == second && via == null -> "A loop needs a waypoint: tap an empty point near the spot."
            route == null -> "No room for that line. Try a different waypoint or spots."
            else -> "Tap an empty point to steer the line, or Draw it."
        }, style = MaterialTheme.typography.bodyMedium, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { move?.let { m -> vm.play { it.play(m) } } }, enabled = route != null && playable) { Text("Draw line") }
            OutlinedButton(onClick = { first = null; second = null; via = null }, enabled = first != null) { Text("Clear") }
        }
    }
}
