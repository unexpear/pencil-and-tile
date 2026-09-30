package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.grids.BridgesGame
import com.simplegamegen.sudoku.grids.BridgesCodec
import com.simplegamegen.sudoku.grids.LightsGame
import com.simplegamegen.sudoku.grids.LightsCodec
import com.simplegamegen.sudoku.grids.LightsRules
import com.simplegamegen.sudoku.grids.SlitherlinkGame
import com.simplegamegen.sudoku.grids.SlitherlinkCodec
import com.simplegamegen.sudoku.grids.TowersGame
import com.simplegamegen.sudoku.grids.TowersCodec
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.abs

private fun levelName(i: Int) = LevelNames[i]

val BridgesSetup: (PuzzleFactory) -> PlaySetup<BridgesGame> = { factory ->
    PlaySetup(
        rules = "Each island shows how many bridges touch it. Bridges run straight across or down, one or two on the same line, " +
            "and never cross. Every island is joined into one group. Tap one island, then another in its line of sight. " +
            "Tap again for a double bridge, and once more to clear it.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { BridgesGame.SIZES[it].let { n -> "$n×$n · ${listOf(5, 5, 6, 6)[it]} islands" } },
        settingOf = { it.setting }, inProgress = { !it.complete && it.built.any { n -> n > 0 } },
        subtitle = { "${levelName(it.setting)} · ${it.size}×${it.size}" },
        create = { level -> { factory.custom("bridges:$level", { seed -> BridgesGame.generate(seed, level) }) { it.clue.joinToString(",") } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@Composable
fun BridgesScreen(nav: NavController, vm: PlayViewModel<BridgesGame>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var selected by remember { mutableIntStateOf(-1) }
    PlayShell(nav, vm, GameId.BRIDGES, remember(factory) { BridgesSetup(factory) }, tools = { g, s ->
        HintButton(GameId.BRIDGES, enabled = !g.complete && !s.busy) { vm.play("One bridge fixed.") { it.hint() } }
    }) { g, s ->
        if (g.complete) WinBanner("Every island is linked.")
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val n = g.size
                val side = min(maxWidth / n, 46.dp)
                Box(Modifier.size(side * n)) {
                    Canvas(Modifier.matchParentSize()) {
                        val cell = size.width / n
                        for (i in 0 until g.edges) if (g.built[i] > 0) {
                            val (a, b) = g.edge(i)
                            val start = Offset((a % n + 0.5f) * cell, (a / n + 0.5f) * cell)
                            val end = Offset((b % n + 0.5f) * cell, (b / n + 0.5f) * cell)
                            val shift = if (a / n == b / n) Offset(0f, cell * 0.08f) else Offset(cell * 0.08f, 0f)
                            drawLine(c.accent, start, end, strokeWidth = cell * 0.08f)
                            if (g.built[i] == 2) drawLine(c.accent, start + shift, end + shift, strokeWidth = cell * 0.08f)
                        }
                    }
                    Column {
                        for (r in 0 until n) Row {
                            for (col in 0 until n) {
                                val i = r * n + col
                                val island = g.clue[i] > 0
                                Box(Modifier.size(side).clickable(enabled = island && !g.complete && !s.busy, role = Role.Button) {
                                    if (selected < 0 || selected == i) selected = if (selected == i) -1 else i
                                    else {
                                        val next = g.cycle(selected, i)
                                        if (next == null) vm.say("Those islands can't be linked.")
                                        else vm.play { g.cycle(selected, i) }
                                        selected = -1
                                    }
                                }.semantics { contentDescription = say(if (island) "Island ${g.clue[i]}" else "Water") }, contentAlignment = Alignment.Center) {
                                    if (island) {
                                        val on = i == selected
                                        Box(Modifier.size(side * 0.78f).background(if (on) c.accent else c.surface, CircleShape).border(2.dp, c.accent, CircleShape),
                                            contentAlignment = Alignment.Center) {
                                            Text(g.clue[i].toString(), color = if (on) c.onAccent else c.text, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!g.complete) Text("Tap two islands in a straight line.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

val SlitherlinkSetup: (PuzzleFactory) -> PlaySetup<SlitherlinkGame> = { factory ->
    PlaySetup(
        rules = "Draw one closed loop along the grid lines. A number tells how many sides of that square the loop uses. " +
            "Blank squares can use any number of sides. The loop does not branch or cross itself. Tap a grid line to add or remove it.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { SlitherlinkGame.SIZES[it].let { n -> "$n×$n squares" } },
        settingOf = { it.setting }, inProgress = { !it.complete && (it.markH.any { ch -> ch == '1' } || it.markV.any { ch -> ch == '1' }) },
        subtitle = { "${levelName(it.setting)} · ${it.size}×${it.size}" },
        create = { level -> { factory.custom("slither:$level", { seed -> SlitherlinkGame.generate(seed, level) }) { it.clue.joinToString(",") } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@Composable
fun SlitherlinkScreen(nav: NavController, vm: PlayViewModel<SlitherlinkGame>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    val measurer = rememberTextMeasurer()
    PlayShell(nav, vm, GameId.SLITHERLINK, remember(factory) { SlitherlinkSetup(factory) }, tools = { g, s ->
        HintButton(GameId.SLITHERLINK, enabled = !g.complete && !s.busy) { vm.play("One line fixed.") { it.hint() } }
    }) { g, s ->
        if (g.complete) WinBanner("The loop is complete.")
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val n = g.size
                val side = min(maxWidth / n, 52.dp)
                Canvas(Modifier.size(side * n).pointerInput(g.seed, g.markH, g.markV) {
                    detectTapGestures { pos ->
                        val cell = side.toPx()
                        val edge = nearestLine(pos, n, cell) ?: return@detectTapGestures
                        vm.play { if (edge.first) it.toggleH(edge.second, edge.third) else it.toggleV(edge.second, edge.third) }
                    }
                }) {
                    val cell = size.width / n
                    for (r in 0..n) for (col in 0 until n) if (g.markH[r * n + col] == '1') {
                        drawLine(c.accent, Offset(col * cell, r * cell), Offset((col + 1) * cell, r * cell), strokeWidth = cell * 0.12f)
                    }
                    for (r in 0 until n) for (col in 0..n) if (g.markV[r * (n + 1) + col] == '1') {
                        drawLine(c.accent, Offset(col * cell, r * cell), Offset(col * cell, (r + 1) * cell), strokeWidth = cell * 0.12f)
                    }
                    for (r in 0..n) for (col in 0..n) drawCircle(c.text, cell * 0.06f, Offset(col * cell, r * cell))
                    for (r in 0 until n) for (col in 0 until n) {
                        val clue = g.clue[r * n + col]
                        if (clue >= 0) {
                            val layout = measurer.measure(clue.toString(), TextStyle(color = c.text, fontWeight = FontWeight.Bold, fontSize = (cell * 0.45f).sp))
                            drawText(layout, topLeft = Offset(col * cell + (cell - layout.size.width) / 2, r * cell + (cell - layout.size.height) / 2))
                        }
                    }
                }
            }
        }
        if (!g.complete) Text("Tap a grid line. A number is how many sides the loop uses.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

private fun nearestLine(pos: Offset, n: Int, cell: Float): Triple<Boolean, Int, Int>? {
    var best = cell * 0.34f
    var pick: Triple<Boolean, Int, Int>? = null
    for (r in 0..n) for (c in 0 until n) {
        val dist = abs(pos.y - r * cell).takeIf { pos.x in c * cell..(c + 1) * cell } ?: continue
        if (dist < best) { best = dist; pick = Triple(true, r, c) }
    }
    for (r in 0 until n) for (c in 0..n) {
        val dist = abs(pos.x - c * cell).takeIf { pos.y in r * cell..(r + 1) * cell } ?: continue
        if (dist < best) { best = dist; pick = Triple(false, r, c) }
    }
    return pick
}

val TowersSetup: (PuzzleFactory) -> PlaySetup<TowersGame> = { factory ->
    PlaySetup(
        rules = "Fill every square with a height from 1 up to the grid size. Each height appears once in every row and column. " +
            "A clue at the side is how many towers you can see looking in from there. A taller tower hides the shorter ones behind it. " +
            "Tap a square to raise its height.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { TowersGame.SIZES[it].let { n -> "$n×$n skyline" } },
        settingOf = { it.setting }, inProgress = { !it.complete && it.entered.any { h -> h > 0 } },
        subtitle = { "${levelName(it.setting)} · ${it.size}×${it.size}" },
        create = { level -> { factory.custom("towers:$level", { seed -> TowersGame.generate(seed, level) }) { it.solution.joinToString(",") } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@Composable
fun TowersScreen(nav: NavController, vm: PlayViewModel<TowersGame>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.TOWERS, remember(factory) { TowersSetup(factory) }, tools = { g, s ->
        HintButton(GameId.TOWERS, enabled = !g.complete && !s.busy) { vm.play("One height filled.") { it.hint() } }
    }) { g, s ->
        if (g.complete) WinBanner("The skyline matches every clue.")
        val n = g.size
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = min(maxWidth / (n + 2), 48.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Box(Modifier.size(side))
                        for (col in 0 until n) Clue(g.top[col], side)
                        Box(Modifier.size(side))
                    }
                    for (r in 0 until n) Row {
                        Clue(g.left[r], side)
                        for (col in 0 until n) {
                            val i = r * n + col
                            val value = g.entered[i]
                            Box(Modifier.size(side).background(c.surface).border(0.5.dp, c.outline)
                                .clickable(enabled = !g.complete && !s.busy, role = Role.Button) { vm.play { it.cycle(i) } }
                                .semantics { contentDescription = say("Row ${r + 1}, column ${col + 1}" + if (value == 0) "" else ", $value") },
                                contentAlignment = Alignment.Center) {
                                Text(if (value == 0) "" else value.toString(), fontWeight = FontWeight.Bold, color = c.text)
                            }
                        }
                        Clue(g.right[r], side)
                    }
                    Row {
                        Box(Modifier.size(side))
                        for (col in 0 until n) Clue(g.bottom[col], side)
                    }
                }
            }
        }
    }
}

@Composable
private fun Clue(value: Int, side: androidx.compose.ui.unit.Dp) {
    val c = LocalGameLook.current.colors
    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
        if (value > 0) Text(value.toString(), color = c.muted, fontWeight = FontWeight.Bold)
    }
}

val LightsSetup: (PuzzleFactory) -> PlaySetup<LightsGame> = { factory ->
    PlaySetup(
        rules = "Place lamps on white squares so every white square is lit. A lamp shines up, down, left and right until it hits a black square. " +
            "Two lamps cannot see each other. A number in a black square is how many lamps touch that square. Tap a white square to place or remove a lamp.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { LightsGame.SIZES[it].let { n -> "$n×$n room" } },
        settingOf = { it.setting }, inProgress = { !it.complete && it.lamps.any { on -> on } },
        subtitle = { "${levelName(it.setting)} · ${it.size}×${it.size}" },
        create = { level -> { factory.custom("lights:$level", { seed -> LightsGame.generate(seed, level) }) { it.wall.joinToString(",") } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@Composable
fun LightsScreen(nav: NavController, vm: PlayViewModel<LightsGame>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.LIGHTS, remember(factory) { LightsSetup(factory) }, tools = { g, s ->
        HintButton(GameId.LIGHTS, enabled = !g.complete && !s.busy) { vm.play("One lamp fixed.") { it.hint() } }
    }) { g, s ->
        if (g.complete) WinBanner("Every square is lit.")
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val n = g.size
                val side = min(maxWidth / n, 52.dp)
                Column(Modifier.border(2.dp, c.text)) {
                    for (r in 0 until n) Row {
                        for (col in 0 until n) {
                            val i = r * n + col
                            val black = g.wall[i] >= 0
                            val lit = !black && LightsRules.lit(n, g.wall, g.lamps, i)
                            Box(Modifier.size(side).background(when {
                                black -> c.text
                                lit -> c.accent.copy(alpha = 0.35f)
                                else -> c.surface
                            }).border(0.5.dp, c.outline)
                                .clickable(enabled = !black && !g.complete && !s.busy, role = Role.Button) { vm.play { it.toggle(i) } }
                                .semantics { contentDescription = say(if (black) "Black ${g.wall[i]}" else if (g.lamps[i]) "Lamp" else "White square") },
                                contentAlignment = Alignment.Center) {
                                if (black && g.wall[i] >= 0) Text(g.wall[i].toString(), color = c.background, fontWeight = FontWeight.Bold)
                                if (g.lamps[i]) Box(Modifier.size(side * 0.42f).background(c.accent, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}
