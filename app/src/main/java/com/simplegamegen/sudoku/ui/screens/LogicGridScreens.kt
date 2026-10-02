package com.simplegamegen.sudoku.ui.screens

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.simplegamegen.sudoku.ui.assets.drawDiscOnPlane
import com.simplegamegen.sudoku.ui.assets.drawMesh
import com.simplegamegen.sudoku.ui.assets.boxMesh
import com.simplegamegen.sudoku.ui.assets.Satin
import com.simplegamegen.sudoku.ui.assets.Pose
import com.simplegamegen.sudoku.ui.assets.TableFrame
import com.simplegamegen.sudoku.ui.assets.BoardView
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
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
import com.simplegamegen.sudoku.ui.assets.iso
import com.simplegamegen.sudoku.ui.assets.pointInQuad
import com.simplegamegen.sudoku.ui.assets.quad
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.abs
import kotlin.math.roundToInt

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
                // Only the stretch of sea the islands use is shown, so a small puzzle fills the screen.
                val isles = (0 until n * n).filter { g.clue[it] > 0 }
                val r0 = isles.minOf { it / n }; val r1 = isles.maxOf { it / n }
                val c0 = isles.minOf { it % n }; val c1 = isles.maxOf { it % n }
                val rows = r1 - r0 + 1; val cols = c1 - c0 + 1
                val side = min(maxWidth / cols, 64.dp)
                Box(Modifier.size(side * cols, side * rows).clip(RoundedCornerShape(12.dp)).background(Color(0xFFCFE6EF))) {
                    Canvas(Modifier.matchParentSize()) {
                        val cell = size.width / cols
                        // Faint dots where islands could be, so the grid can be read.
                        for (r in 0 until rows) for (col in 0 until cols) drawCircle(Color(0xFF7FA9BC), cell * 0.04f, Offset((col + 0.5f) * cell, (r + 0.5f) * cell))
                        for (i in 0 until g.edges) if (g.built[i] > 0) {
                            val (a, b) = g.edge(i)
                            val start = Offset((a % n - c0 + 0.5f) * cell, (a / n - r0 + 0.5f) * cell)
                            val end = Offset((b % n - c0 + 0.5f) * cell, (b / n - r0 + 0.5f) * cell)
                            val shift = if (a / n == b / n) Offset(0f, cell * 0.08f) else Offset(cell * 0.08f, 0f)
                            drawLine(c.accent, start, end, strokeWidth = cell * 0.08f)
                            if (g.built[i] == 2) drawLine(c.accent, start + shift, end + shift, strokeWidth = cell * 0.08f)
                        }
                    }
                    Column {
                        for (r in r0..r1) Row {
                            for (col in c0..c1) {
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
                // A small margin round the grid so the dots and lines on its edge aren't cut in half.
                val side = min((maxWidth - 16.dp) / n, 52.dp)
                Canvas(Modifier.size(side * n + 16.dp).pointerInput(g.seed, g.markH, g.markV) {
                    detectTapGestures { pos ->
                        val cell = side.toPx()
                        val edge = nearestLine(pos - Offset(8.dp.toPx(), 8.dp.toPx()), n, cell) ?: return@detectTapGestures
                        vm.play { if (edge.first) it.toggleH(edge.second, edge.third) else it.toggleV(edge.second, edge.third) }
                    }
                }) {
                    val pad = 8.dp.toPx()
                    val cell = (size.width - pad * 2) / n
                    translate(pad, pad) {
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
                            // The cell is in pixels; a font size in sp is scaled by density again, so divide it out.
                            val layout = measurer.measure(clue.toString(), TextStyle(color = c.text, fontWeight = FontWeight.Bold, fontSize = (cell * 0.45f / density).sp))
                            drawText(layout, topLeft = Offset(col * cell + (cell - layout.size.width) / 2, r * cell + (cell - layout.size.height) / 2))
                        }
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
            "Tap a tower to raise it.",
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
    PlayShell(nav, vm, GameId.TOWERS, remember(factory) { TowersSetup(factory) }, tools = { g, s ->
        HintButton(GameId.TOWERS, enabled = !g.complete && !s.busy) { vm.play("One height filled.") { it.hint() } }
    }) { g, s ->
        if (g.complete) WinBanner("The skyline matches every clue.")
        // Pinch to zoom; the camera chips under the city turn it.
        TowersCity(g, enabled = !g.complete && !s.busy) { index -> vm.play { it.cycle(index) } }
    }
}

/** How tall one storey is, in squares. */
private const val Storey = 0.5f

private val TowerViews = listOf(
    BoardView("Street", yaw = 0f, pitch = 58f, distance = 1.6f),
    BoardView("Corner", yaw = 40f, pitch = 42f, distance = 1.9f),
    BoardView("Top", yaw = 0f, pitch = 90f, distance = 2f),
)

/**
 * The puzzle as a little city in 3D: every entered height is a real building, one storey per level, with windows on
 * each side, a roof and a parapet. Clues stand on round badges round the plot. The same cameras as the chess board:
 * presets, a free camera to turn it, and pinch to zoom.
 */
@Composable
private fun TowersCity(g: TowersGame, enabled: Boolean, onTap: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    val measurer = rememberTextMeasurer()
    val camera = rememberBoardCamera("towers_views", TowerViews)
    val n = g.size
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        BoardWithViews(camera, n, peakZ = n * Storey + 0.3f, margin = 1.55f, height = maxWidth * 1.05f) { frame ->
            fun h(i: Int) = g.entered[i] * Storey
            fun roof(i: Int): List<Offset> {
                val x = (i % n).toFloat(); val y = (i / n).toFloat(); val z = h(i) + 0.02f
                return listOf(frame.at(x + 0.06f, y + 0.06f, z), frame.at(x + 0.94f, y + 0.06f, z), frame.at(x + 0.94f, y + 0.94f, z), frame.at(x + 0.06f, y + 0.94f, z))
            }
            // Far buildings first, so near ones stand in front of them.
            val order = (0 until n * n).sortedByDescending { frame.depth((it % n) + 0.5f, (it / n) + 0.5f, h(it) / 2) }
            order.forEach { i ->
                val row = i / n; val col = i % n; val height = g.entered[i]
                val top = frame.at(col + 0.5f, row + 0.5f, h(i) + 0.05f)
                Box(Modifier.offset { IntOffset(top.x.roundToInt(), top.y.roundToInt()) }.size(1.dp).semantics {
                    contentDescription = say("Row ${row + 1}, column ${col + 1}" + if (height == 0) ", empty" else ", height $height")
                    if (enabled) onClick { onTap(i); true }
                })
            }
            Canvas(Modifier.matchParentSize().pointerInput(g.entered, enabled, frame) {
                detectTapGestures { pos ->
                    if (!enabled) return@detectTapGestures
                    // The nearest building whose roof or side is under the finger.
                    val hit = order.asReversed().firstOrNull { i ->
                        val x = (i % n).toFloat(); val y = (i / n).toFloat(); val z = h(i)
                        val r = roof(i)
                        if (pointInQuad(pos, r[0], r[1], r[2], r[3])) return@firstOrNull true
                        if (z <= 0f) return@firstOrNull false
                        listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f).let { cs ->
                            (0 until 4).any { k ->
                                val (ax, ay) = cs[k]; val (bx, by) = cs[(k + 1) % 4]
                                pointInQuad(pos, frame.at(x + ax, y + ay, z), frame.at(x + bx, y + by, z), frame.at(x + bx, y + by, 0f), frame.at(x + ax, y + ay, 0f))
                            }
                        }
                    } ?: return@detectTapGestures
                    onTap(hit)
                }
            }) {
                // The plot: a lawn with paving between the lots, and a kerb round it.
                val k = 0.25f
                drawPath(quad(frame.at(-k, -k, 0f), frame.at(n + k, -k, 0f), frame.at(n + k, n + k, 0f), frame.at(-k, n + k, 0f)), Color(0xFF8C8273))
                for (i in 0 until n * n) {
                    val x = (i % n).toFloat(); val y = (i / n).toFloat()
                    drawPath(quad(frame.at(x + 0.05f, y + 0.05f, 0.002f), frame.at(x + 0.95f, y + 0.05f, 0.002f), frame.at(x + 0.95f, y + 0.95f, 0.002f),
                        frame.at(x + 0.05f, y + 0.95f, 0.002f)), Color(0xFF4F8A55))
                }
                order.forEach { i -> drawBuilding(frame, i % n, i / n, g.entered[i], n, measurer, c) }
                // Clue badges round the plot, each with a short line to its row or column.
                val clueStyle = TextStyle(color = c.text, fontSize = (frame.cell * 0.36f / density).sp, fontWeight = FontWeight.Bold)
                for (i in 0 until n) {
                    listOf(
                        Triple(g.top[i], frame.at(i + 0.5f, -1.2f, 0f), frame.at(i + 0.5f, -0.3f, 0f)),
                        Triple(g.bottom[i], frame.at(i + 0.5f, n + 1.2f, 0f), frame.at(i + 0.5f, n + 0.3f, 0f)),
                        Triple(g.left[i], frame.at(-1.2f, i + 0.5f, 0f), frame.at(-0.3f, i + 0.5f, 0f)),
                        Triple(g.right[i], frame.at(n + 1.2f, i + 0.5f, 0f), frame.at(n + 0.3f, i + 0.5f, 0f)),
                    ).forEach { (value, at, toward) ->
                        if (value > 0) {
                            val r = frame.cell * 0.3f
                            drawLine(c.muted.copy(alpha = 0.6f), at, toward, strokeWidth = maxOf(1f, r * 0.12f), cap = StrokeCap.Round)
                            drawCircle(c.surface, r, at)
                            drawCircle(c.muted.copy(alpha = 0.85f), r, at, style = Stroke(maxOf(1f, r * 0.14f)))
                            val label = measurer.measure("$value", clueStyle)
                            drawText(label, topLeft = at - Offset(label.size.width / 2f, label.size.height / 2f))
                        }
                    }
                }
            }
        }
    }
}

private val Buildings = HashMap<Int, com.simplegamegen.sudoku.ui.assets.Mesh>()
private val RoofMesh by lazy { boxMesh(0.88f, 0.88f, 0.06f) }

/** One building of [height] storeys on lot ([col], [row]): walls, windows on the sides you can see, a roof and its number. */
private fun DrawScope.drawBuilding(frame: TableFrame, col: Int, row: Int, height: Int, n: Int, measurer: TextMeasurer, c: com.simplegamegen.sudoku.ui.theme.GamePalette) {
    val x = col + 0.5f; val y = row + 0.5f
    if (height == 0) {
        drawPath(quad(frame.at(x - 0.41f, y - 0.41f, 0.004f), frame.at(x + 0.41f, y - 0.41f, 0.004f), frame.at(x + 0.41f, y + 0.41f, 0.004f),
            frame.at(x - 0.41f, y + 0.41f, 0.004f)), Color(0xFF6FA572))
        return
    }
    val tall = height * Storey
    val wall = lerp(Color(0xFFC9A27A), Color(0xFF7D8CA8), (height - 1f) / (n - 1).coerceAtLeast(1))
    drawDiscOnPlane(frame, floatArrayOf(x + 0.12f, y + 0.1f, 0.003f), floatArrayOf(0.6f, 0f, 0f), floatArrayOf(0f, 0.6f, 0f), 1f, Color.Black.copy(alpha = 0.2f))
    drawMesh(frame, Buildings.getOrPut(height) { boxMesh(0.82f, 0.82f, tall) }, Pose(x, y, 0f), wall, Satin)
    // Windows on each wall that faces the camera: two per storey, some lit.
    val sides = listOf(floatArrayOf(0f, 1f), floatArrayOf(0f, -1f), floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f))
    for ((k, s) in sides.withIndex()) {
        val nx = s[0]; val ny = s[1]
        val fx = x + nx * 0.411f; val fy = y + ny * 0.411f
        if (!frame.facing(nx, ny, 0f, fx, fy, tall / 2)) continue
        val ux = -ny; val uy = nx
        val light = if (nx + ny > 0f) 0.85f else 1f
        for (floor in 0 until height) for (w in 0..1) {
            val u0 = -0.28f + w * 0.34f; val u1 = u0 + 0.22f
            val z0 = floor * Storey + Storey * 0.3f; val z1 = floor * Storey + Storey * 0.75f
            val lit = (floor * 3 + w + col * 5 + row * 7 + k) % 4 == 0
            val glass = if (lit) Color(0xFFFFE6A6) else lerp(Color(0xFF2B3A52), Color(0xFF7FA6C9), 0.25f * light)
            drawPath(quad(frame.at(fx + ux * u0, fy + uy * u0, z0), frame.at(fx + ux * u1, fy + uy * u1, z0),
                frame.at(fx + ux * u1, fy + uy * u1, z1), frame.at(fx + ux * u0, fy + uy * u0, z1)), glass)
        }
    }
    drawMesh(frame, RoofMesh, Pose(x, y, tall), lerp(wall, Color.White, 0.35f), Satin)
    val r = listOf(frame.at(x - 0.3f, y - 0.3f, tall + 0.062f), frame.at(x + 0.3f, y - 0.3f, tall + 0.062f), frame.at(x + 0.3f, y + 0.3f, tall + 0.062f), frame.at(x - 0.3f, y + 0.3f, tall + 0.062f))
    drawPath(quad(r[0], r[1], r[2], r[3]), lerp(wall, Color.White, 0.6f))
    val label = measurer.measure("$height", TextStyle(color = Color(0xFF1E1A14), fontSize = (frame.unitAt(x, y, tall) * 0.34f / density).sp, fontWeight = FontWeight.Bold))
    val at = frame.at(x, y, tall + 0.07f)
    drawText(label, topLeft = at - Offset(label.size.width / 2f, label.size.height / 2f))
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
