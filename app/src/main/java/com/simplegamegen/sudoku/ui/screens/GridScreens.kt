package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.components.ZoomBox
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.grids.DraftVerdict
import com.simplegamegen.sudoku.grids.GridGame
import com.simplegamegen.sudoku.grids.GridRule
import com.simplegamegen.sudoku.grids.GridRules
import com.simplegamegen.sudoku.grids.GridStyle
import com.simplegamegen.sudoku.grids.Parity
import com.simplegamegen.sudoku.grids.SumCage
import com.simplegamegen.sudoku.ui.EditTool
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.GridEditorViewModel
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.ReportPlay
import com.simplegamegen.sudoku.ui.SessionClock
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.ConfirmDialog
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.HeaderAction
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.MenuAction
import com.simplegamegen.sudoku.ui.components.MessageLine
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.SectionTitle
import com.simplegamegen.sudoku.ui.components.ToolBar
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.sqrt
import com.simplegamegen.sudoku.ui.i18n.say

/** Records key and route for grid Sudoku (16×16 and custom grids), kept apart from classic Sudoku. */
const val GRID_RECORD = "SUDOKU_GRID"
const val GRID_ROUTE = "grid_play"

/** Route that opens the grid screen and starts a new puzzle: "size:style:level". */
fun gridStartRoute(size: Int, style: GridStyle, level: Int) = "$GRID_ROUTE?new=$size:${style.name}:$level"

private val RegionTints = listOf(0xFFF6D7A7, 0xFFBFE3D0, 0xFFC9D7F2, 0xFFF3C4C4, 0xFFE3D1F2, 0xFFD6EFB0, 0xFFF8E3A0, 0xFFB9E2EC,
    0xFFF2CFE2, 0xFFD9D2C3, 0xFFC4E8C2, 0xFFF4C9A8, 0xFFCFD8E8, 0xFFE8E0B4, 0xFFD0C4EC, 0xFFB8D8C8)

fun regionColor(region: Int): Color = if (region < 0) Color.Transparent else Color(RegionTints[region % RegionTints.size])

/** Label for a grid game: its name, or size and style. */
fun gridTitle(g: GridGame): String = g.name.ifBlank { "Sudoku ${g.size}×${g.size}" }

fun gridStyleOf(r: GridRules): String = buildList {
    if (r.cages.isNotEmpty()) add("Killer")
    if (r.regions != GridRules.boxShapes(r.size).firstOrNull()?.let { (br, bc) -> GridRules.boxRegions(r.size, br, bc) }) add("Jigsaw")
    if (GridRule.DIAGONALS in r.rules) add("Diagonal X")
    if (r.extras.isNotEmpty()) add("Extra groups")
    r.rules.filter { it != GridRule.DIAGONALS }.forEach { add(it.label) }
    if (r.parity.any { it != Parity.NONE }) add("Odd/even")
}.ifEmpty { listOf("Classic") }.joinToString(", ")

fun digitLabel(d: Int): String = d.toString()

/**
 * Draws any grid Sudoku: regions (tinted in the editor), extra groups, diagonals, cages,
 * odd/even marks, digits and notes. Reports taps, and drags as the squares crossed.
 */
@Composable
fun GridBoard(
    rules: GridRules?,
    size: Int,
    regions: List<Int>,
    digits: List<Int>,
    givens: List<Int>,
    modifier: Modifier = Modifier,
    notes: List<Int> = emptyList(),
    selected: Int? = null,
    marked: Set<Int> = emptySet(),
    bad: Set<Int> = emptySet(),
    tintRegions: Boolean = false,
    /** Strength of the region tint (full in the editor, soft for jigsaw play). */
    tintAlpha: Float = 0.75f,
    extras: List<List<Int>> = rules?.extras.orEmpty(),
    cages: List<SumCage> = rules?.cages.orEmpty(),
    parity: List<Parity> = rules?.parity.orEmpty(),
    diagonals: Boolean = rules?.let { GridRule.DIAGONALS in it.rules } ?: false,
    /** When false the board only takes taps, so the page can still scroll over it. */
    drags: Boolean = false,
    onStroke: (List<Int>, Boolean) -> Unit = { _, _ -> },
) {
    val look = LocalGameLook.current
    val c = look.colors
    val paper = if (look.dark) c.surfaceAlt else Color(0xFFFFFEFA)
    val ink = if (look.dark) c.text else Color(0xFF2A2A2E)
    val thin = if (look.dark) c.outline else Color(0xFFC9C4B8)
    val measurer = rememberTextMeasurer()
    val latest by rememberUpdatedState(onStroke)
    val selectedDigit = selected?.let { digits.getOrNull(it) }?.takeIf { it != 0 }
    val seen = selected?.let { sel -> rules?.peers?.getOrNull(sel)?.toSet() }.orEmpty()
    ZoomBox(Modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min(maxWidth, 560.dp)
            Canvas(Modifier.size(side).semantics { contentDescription = say("Sudoku grid $size by $size, ${digits.count { it != 0 }} squares filled") }
                .pointerInput(size, drags) {
                    if (!drags) {
                        detectTapGestures { o ->
                            val cell = this.size.width.toFloat() / size
                            val r = (o.y / cell).toInt(); val col = (o.x / cell).toInt()
                            if (r in 0 until size && col in 0 until size) latest(listOf(r * size + col), true)
                        }
                        return@pointerInput
                    }
                    awaitEachGesture {
                        val cell = this.size.width.toFloat() / size
                        fun at(o: Offset): Int? {
                            val r = (o.y / cell).toInt(); val col = (o.x / cell).toInt()
                            return if (r in 0 until size && col in 0 until size) r * size + col else null
                        }
                        val down = awaitFirstDown()
                        val first = at(down.position) ?: return@awaitEachGesture
                        down.consume()
                        val stroke = mutableListOf(first)
                        var moved = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            at(change.position)?.let { if (it != stroke.last()) { if (it !in stroke) stroke += it; moved = true; latest(listOf(it), false) } }
                        }
                        if (!moved) latest(listOf(first), true)
                    }
                }) {
                val cell = this.size.width / size
                val n = size
                fun origin(i: Int) = Offset((i % n) * cell, (i / n) * cell)
                drawRect(paper)
                for (i in 0 until n * n) {
                    val o = origin(i)
                    if (tintRegions) drawRect(regionColor(regions.getOrElse(i) { -1 }).copy(alpha = if (look.dark) tintAlpha * 0.45f else tintAlpha), o, Size(cell, cell))
                    if (!tintRegions && extras.any { i in it }) drawRect(c.accent.copy(alpha = 0.12f), o, Size(cell, cell))
                    when {
                        i == selected -> drawRect(c.highlight.copy(alpha = 0.5f), o, Size(cell, cell))
                        i in marked -> drawRect(c.accent.copy(alpha = 0.28f), o, Size(cell, cell))
                        selectedDigit != null && digits.getOrNull(i) == selectedDigit -> drawRect(c.highlight.copy(alpha = 0.25f), o, Size(cell, cell))
                        i in seen -> drawRect(c.highlight.copy(alpha = 0.1f), o, Size(cell, cell))
                    }
                    if (i in bad) drawRect(c.danger.copy(alpha = 0.22f), o, Size(cell, cell))
                    when (parity.getOrNull(i)) {
                        Parity.ODD -> drawCircle(c.muted.copy(alpha = 0.25f), cell * 0.42f, o + Offset(cell / 2, cell / 2))
                        Parity.EVEN -> drawRect(c.muted.copy(alpha = 0.25f), o + Offset(cell * 0.1f, cell * 0.1f), Size(cell * 0.8f, cell * 0.8f))
                        else -> {}
                    }
                }
                if (diagonals) {
                    drawLine(c.accent.copy(alpha = 0.45f), Offset.Zero, Offset(cell * n, cell * n), strokeWidth = 2f)
                    drawLine(c.accent.copy(alpha = 0.45f), Offset(cell * n, 0f), Offset(0f, cell * n), strokeWidth = 2f)
                }
                // Thin lines, then thick region borders.
                for (k in 1 until n) {
                    drawLine(thin, Offset(k * cell, 0f), Offset(k * cell, n * cell), strokeWidth = 1f)
                    drawLine(thin, Offset(0f, k * cell), Offset(n * cell, k * cell), strokeWidth = 1f)
                }
                val thick = if (n >= 12) 2.2f else 3f
                for (i in 0 until n * n) {
                    val o = origin(i); val g = regions.getOrElse(i) { -1 }
                    if (i % n < n - 1 && regions.getOrElse(i + 1) { -1 } != g) drawLine(ink, o + Offset(cell, 0f), o + Offset(cell, cell), strokeWidth = thick)
                    if (i / n < n - 1 && regions.getOrElse(i + n) { -1 } != g) drawLine(ink, o + Offset(0f, cell), o + Offset(cell, cell), strokeWidth = thick)
                }
                drawRect(ink, style = Stroke(thick * 1.3f))
                // Cages: dashed outlines inside the squares, sum in the first square.
                val caged = HashMap<Int, Int>().also { m -> cages.forEachIndexed { k, cage -> cage.cells.forEach { m[it] = k } } }
                val inset = cell * 0.1f
                val dash = PathEffect.dashPathEffect(floatArrayOf(cell * 0.12f, cell * 0.08f))
                cages.forEachIndexed { k, cage ->
                    for (i in cage.cells) {
                        val o = origin(i)
                        fun same(j: Int, ok: Boolean) = ok && caged[j] == k
                        val up = same(i - n, i >= n); val down = same(i + n, i < n * n - n); val left = same(i - 1, i % n > 0); val right = same(i + 1, i % n < n - 1)
                        val x0 = o.x + if (left) 0f else inset; val x1 = o.x + cell - if (right) 0f else inset
                        val y0 = o.y + if (up) 0f else inset; val y1 = o.y + cell - if (down) 0f else inset
                        if (!up) drawLine(ink, Offset(x0, o.y + inset), Offset(x1, o.y + inset), 1.3f, pathEffect = dash)
                        if (!down) drawLine(ink, Offset(x0, o.y + cell - inset), Offset(x1, o.y + cell - inset), 1.3f, pathEffect = dash)
                        if (!left) drawLine(ink, Offset(o.x + inset, y0), Offset(o.x + inset, y1), 1.3f, pathEffect = dash)
                        if (!right) drawLine(ink, Offset(o.x + cell - inset, y0), Offset(o.x + cell - inset, y1), 1.3f, pathEffect = dash)
                    }
                    val first = cage.cells.minOrNull() ?: return@forEachIndexed
                    val label = measurer.measure(AnnotatedString(cage.sum.toString()), TextStyle(fontSize = (cell * 0.22f / density).sp, color = ink, fontWeight = FontWeight.Bold))
                    drawRect(paper, origin(first) + Offset(inset * 0.6f, inset * 0.6f), Size(label.size.width.toFloat() + 2f, label.size.height.toFloat()))
                    drawText(label, topLeft = origin(first) + Offset(inset * 0.7f, inset * 0.6f))
                }
                // Digits and notes.
                val big = TextStyle(fontSize = (cell * (if (n > 9) 0.42f else 0.56f) / density).sp, fontWeight = FontWeight.Bold)
                val k = ceil(sqrt(n.toDouble())).toInt()
                val small = TextStyle(fontSize = (cell / k * 0.62f / density).sp, color = c.muted)
                for (i in 0 until n * n) {
                    val d = digits.getOrElse(i) { 0 }
                    val o = origin(i)
                    if (d != 0) {
                        val color = when { i in bad -> c.danger; givens.getOrElse(i) { 0 } != 0 -> ink; else -> c.accent }
                        val t = measurer.measure(AnnotatedString(digitLabel(d)), big.copy(color = color))
                        drawText(t, topLeft = o + Offset((cell - t.size.width) / 2, (cell - t.size.height) / 2))
                    } else {
                        val mask = notes.getOrElse(i) { 0 }
                        if (mask != 0) for (v in 1..n) if (mask and (1 shl (v - 1)) != 0) {
                            val t = measurer.measure(AnnotatedString(v.toString()), small)
                            val sub = cell / k
                            drawText(t, topLeft = o + Offset(((v - 1) % k) * sub + (sub - t.size.width) / 2, ((v - 1) / k) * sub + (sub - t.size.height) / 2))
                        }
                    }
                }
            }
        }
    }
}

/** Digit buttons 1..[n] in rows of up to 9, plus an optional trailing button. */
@Composable
fun DigitPad(n: Int, enabled: Boolean = true, label: (Int) -> String = ::digitLabel, onDigit: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    val perRow = if (n <= 9) n else (n + 1) / 2
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..n).chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d ->
                    Surface(onClick = { onDigit(d) }, enabled = enabled, modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = say("Digit $d") },
                        shape = MaterialTheme.shapes.small, color = c.surface, border = BorderStroke(1.dp, c.outline)) {
                        Box(contentAlignment = Alignment.Center) { Text(label(d), fontSize = if (n > 9) 16.sp else 20.sp, fontWeight = FontWeight.Bold, color = if (enabled) c.text else c.muted) }
                    }
                }
                repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------- Play ----------------

@Composable
fun GridScreen(nav: NavController, vm: PlayViewModel<GridGame>, factory: PuzzleFactory, request: String?) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalGameLook.current.colors
    val g = s.game
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var notesMode by rememberSaveable { mutableStateOf(false) }
    var checked by remember { mutableStateOf(emptySet<Int>()) }
    var consumed by rememberSaveable { mutableStateOf(false) }
    // A start request from the Sudoku screen waits until the saved game has loaded.
    LaunchedEffect(s.busy, request) {
        if (!consumed && request != null && !s.busy) {
            consumed = true
            val parts = request.split(':')
            val size = parts.getOrNull(0)?.toIntOrNull() ?: 16
            val style = parts.getOrNull(1)?.let { n -> GridStyle.entries.firstOrNull { it.name == n } } ?: GridStyle.CLASSIC
            val level = parts.getOrNull(2)?.toIntOrNull() ?: 1
            selected = null
            vm.start { factory.custom("grid:$size:$style:$level", { seed -> GridGame.generate(seed, size, level, style) }) { it.givens.joinToString(",") } }
        }
    }
    val levelLabel = g?.let { "${it.size}×${it.size} · ${gridStyleOf(it.rules)} · ${LevelNames[it.level]}" } ?: ""
    ReportPlay(GameId.SUDOKU, key = g?.let { "${it.seed}:${it.givens.hashCode()}" }, level = levelLabel, levelIndex = g?.level ?: 0,
        inProgress = g != null && !g.complete, outcome = if (g?.complete == true) Outcome(Result.WON) else null, record = GRID_RECORD)
    val playable = g != null && !g.complete && !s.busy
    fun input(d: Int) {
        val cell = selected ?: return
        checked = emptySet()
        vm.play(if (g?.enter(cell, d)?.complete == true) "Solved!" else null) { if (notesMode) it.toggleNote(cell, d) else it.enter(cell, d) }
    }
    GameScaffold(
        title = g?.let(::gridTitle) ?: "Sudoku grids", subtitle = if (g != null) levelLabel else null, game = GameId.SUDOKU, tutorial = GameId.SUDOKU,
        onBack = { nav.popBackStack() }, busy = s.busy, showClock = false,
        actions = { SessionClock(GameId.SUDOKU, record = GRID_RECORD) },
        menu = listOf(
            MenuAction("New puzzle", GameIcons.Plus) { nav.navigate("sudoku") { popUpTo("home") } },
            MenuAction("Custom grids", GameIcons.Grid) { nav.navigate("grids") },
        ),
        bottomBar = if (g == null) null else {
            {
                ToolBar {
                    ToolButton(GameIcons.Undo, "Undo", enabled = s.canUndo && !s.busy, onClick = vm::undo)
                    ToolButton(GameIcons.Eraser, "Erase", enabled = playable && selected != null) { selected?.let { cell -> vm.play { it.erase(cell) } } }
                    ToolButton(GameIcons.Pencil, if (notesMode) "Notes on" else "Notes", enabled = playable, active = notesMode) { notesMode = !notesMode }
                    ToolButton(GameIcons.Check, "Check", enabled = playable) {
                        checked = g.mistakes()
                        vm.say(if (checked.isEmpty()) "No mistakes so far." else if (checked.size == 1) "1 square is wrong, marked in red." else "${checked.size} squares are wrong, marked in red.")
                    }
                    HintButton(GameId.SUDOKU, enabled = playable, record = GRID_RECORD) { checked = emptySet(); vm.play("One square revealed.") { it.hint(selected) } }
                }
            }
        },
    ) {
        if (g == null) {
            if (!s.busy) Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No grid puzzle yet", style = MaterialTheme.typography.titleLarge)
                    Text("Start a 16×16 puzzle from the Sudoku screen, or build your own grid.", color = c.muted)
                    Button(onClick = { nav.navigate("sudoku") }, modifier = Modifier.fillMaxWidth()) { Text("Open Sudoku") }
                    OutlinedButton(onClick = { nav.navigate("grids") }, modifier = Modifier.fillMaxWidth()) { Text("Custom grids") }
                }
            }
            MessageLine(s.message)
        } else {
        val conflicts = g.conflicts()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.filled} / ${g.rules.cells} filled")
            if (g.hints > 0) InfoChip(if (g.hints == 1) "1 hint" else "${g.hints} hints", icon = GameIcons.Hint)
            if (conflicts.isNotEmpty()) InfoChip("${conflicts.size} conflicts", emphasized = true)
        }
        if (g.complete) WinBanner("Solved!")
        val jigsaw = g.rules.regions != GridRules.boxShapes(g.size).firstOrNull()?.let { (br, bc) -> GridRules.boxRegions(g.size, br, bc) }
        GridBoard(g.rules, g.size, g.rules.regions, g.entries, g.givens, notes = g.notes, selected = selected, bad = conflicts + checked,
            tintRegions = jigsaw, tintAlpha = 0.35f) { cells, tap ->
            if (tap) selected = cells.first().takeIf { it != selected }
        }
        if (!g.complete) DigitPad(g.size, enabled = playable && selected != null, onDigit = ::input)
        if (g.rules.rules.isNotEmpty() || g.rules.extras.isNotEmpty() || g.rules.parity.any { it != Parity.NONE } || g.rules.cages.isNotEmpty())
            Text(gridRulesText(g.rules), style = MaterialTheme.typography.bodySmall, color = c.muted)
        MessageLine(s.message)
        }
    }
}

/** One line per extra rule of a grid. */
fun gridRulesText(r: GridRules): String = buildList {
    add("Each row, column and outlined region holds 1–${r.size} once.")
    if (GridRule.DIAGONALS in r.rules) add("Both long diagonals also hold each digit once.")
    if (r.extras.isNotEmpty()) add("Shaded groups hold different digits.")
    if (GridRule.ANTI_KNIGHT in r.rules) add("Squares a chess knight's move apart can't share a digit.")
    if (GridRule.ANTI_KING in r.rules) add("Diagonally touching squares can't share a digit.")
    if (GridRule.NON_CONSECUTIVE in r.rules) add("Side-by-side squares can't hold consecutive digits.")
    if (r.cages.isNotEmpty()) add("Dashed cages add up to their totals without repeating a digit.")
    if (r.parity.any { it == Parity.ODD }) add("Circles hold odd digits.")
    if (r.parity.any { it == Parity.EVEN }) add("Squares hold even digits.")
}.joinToString("\n")

// ---------------- Custom grid list ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GridListScreen(nav: NavController, vm: GridEditorViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalGameLook.current.colors
    var newSize by rememberSaveable { mutableStateOf<Int?>(null) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<Int?>(null) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    GameScaffold(title = "Custom grids", subtitle = "Design your own Sudoku", game = GameId.SUDOKU, onBack = { nav.popBackStack() },
        actions = { HeaderAction(GameIcons.Paste, "Import a grid code") { importing = true } }) {
        Text("Build any grid from 4×4 to 16×16: draw your own regions, add cages, odd/even squares, extra groups and special rules. " +
            "The checker makes sure a grid has exactly one answer before you play it.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        Button(onClick = { newSize = 9 }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(GameIcons.Plus, null); Text("  New grid") }
        if (s.drafts.isEmpty()) Text("No saved grids yet.", color = c.muted, modifier = Modifier.padding(vertical = 16.dp))
        s.drafts.forEachIndexed { i, d ->
            val verdict = s.statuses.getOrNull(i)
            val ready = verdict == DraftVerdict.UNIQUE
            Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.name.ifBlank { "Untitled grid" }, style = MaterialTheme.typography.titleMedium)
                    Text("${d.size}×${d.size} · " + (d.toRules()?.let(::gridStyleOf) ?: "Unfinished layout") + " · ${d.givens.count { it != 0 }} starting digits",
                        style = MaterialTheme.typography.bodySmall, color = c.muted)
                    val (label, why) = verdictLabel(verdict)
                    InfoChip(label, emphasized = ready, icon = if (ready) GameIcons.Check else null)
                    if (why.isNotEmpty()) Text(why, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val game = vm.playable(d)
                                if (game == null) vm.say("This grid doesn't have exactly one answer yet.")
                                else { vm.sendToPlay(game); nav.navigate(GRID_ROUTE) { popUpTo(GRID_ROUTE) { inclusive = true } } }
                            }
                        }, enabled = ready) { Text("Play") }
                        OutlinedButton(onClick = { vm.edit(i); nav.navigate("grid_edit") }) { Text("Edit") }
                        OutlinedButton(onClick = { vm.shareCode(d, verdict)?.let { clipboard.setText(AnnotatedString(it)); vm.say("Grid code copied. Share it so others can import it.") } },
                            enabled = ready) { Text("Share") }
                        TextButton(onClick = { deleting = i }) { Text("Delete", color = c.danger) }
                    }
                }
            }
        }
        MessageLine(s.message)
    }
    newSize?.let { size ->
        AlertDialog(onDismissRequest = { newSize = null }, title = { Text("Grid size") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Digits run from 1 up to the size. Sizes without a box shape (5, 7, 11, 13) need drawn regions.", color = c.muted)
                    OptionGroup("Size", (4..16).toList(), size, { "$it×$it" }) { newSize = it }
                }
            },
            confirmButton = { TextButton(onClick = { vm.newDraft(size); newSize = null; nav.navigate("grid_edit") }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { newSize = null }) { Text("Cancel") } }, containerColor = c.surface)
    }
    if (importing) {
        var code by rememberSaveable { mutableStateOf("") }
        var error by rememberSaveable { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { importing = false }, title = { Text("Import a grid") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste a grid code that starts with SGG1.", color = c.muted)
                    OutlinedTextField(code, { code = it; error = false }, label = { Text("Grid code") }, maxLines = 4, isError = error)
                    if (error) Text("That code isn't a valid grid. Check it and try again.", color = c.danger)
                    TextButton(onClick = { code = clipboard.getText()?.text.orEmpty() }) { Text("Paste from clipboard") }
                }
            },
            confirmButton = { TextButton(onClick = { if (vm.import(code)) { importing = false; nav.navigate("grid_edit") } else error = true }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { importing = false }) { Text("Cancel") } }, containerColor = c.surface)
    }
    deleting?.let { i ->
        ConfirmDialog("Delete this grid?", "It will be removed from this device.", "Delete", onConfirm = { vm.delete(i); deleting = null },
            onDismiss = { deleting = null }, dismiss = "Keep")
    }
}

// ---------------- Editor ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GridEditorScreen(nav: NavController, vm: GridEditorViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val c = LocalGameLook.current.colors
    val d = s.draft
    val scope = rememberCoroutineScope()
    var cageSum by rememberSaveable { mutableStateOf<String?>(null) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    if (d == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    val check = s.check
    fun back() { if (s.dirty) leaving = true else { vm.close(); nav.popBackStack() } }
    GameScaffold(
        title = d.name.ifBlank { "Untitled grid" }, subtitle = "${d.size}×${d.size} editor", game = GameId.SUDOKU, onBack = ::back, busy = s.working || s.checking,
        actions = { HeaderAction(GameIcons.Save, "Save grid") { naming = true } },
        menu = listOf(
            MenuAction("Rename", GameIcons.Edit) { naming = true },
            MenuAction("Copy grid code", GameIcons.Copy, enabled = check?.playable == true) { vm.shareCode(d, check?.verdict)?.let { clipboard.setText(AnnotatedString(it)); vm.say("Grid code copied.") } },
        ),
        bottomBar = {
            ToolBar {
                ToolButton(GameIcons.Undo, "Undo", enabled = s.canUndo, onClick = vm::undo)
                ToolButton(GameIcons.Save, "Save") { naming = true }
                ToolButton(GameIcons.Play, "Play", enabled = check?.playable == true && !s.working) {
                    scope.launch {
                        val game = vm.playable(d)
                        if (game == null) vm.say("This grid doesn't have exactly one answer yet.")
                        else {
                            vm.save()
                            vm.sendToPlay(game)
                            nav.navigate(GRID_ROUTE) { popUpTo(GRID_ROUTE) { inclusive = true } }
                        }
                    }
                }
            }
        },
    ) {
        // Tools.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditTool.entries.forEach { tool ->
                Surface(onClick = { vm.setTool(tool) }, shape = CircleShape, color = if (s.tool == tool) c.accent else c.surface,
                    border = BorderStroke(1.dp, if (s.tool == tool) c.accent else c.outline)) {
                    Text(tool.label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = if (s.tool == tool) c.onAccent else c.text,
                        fontWeight = if (s.tool == tool) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Text(toolHelp(s.tool, d.size), style = MaterialTheme.typography.bodySmall, color = c.muted)
        val issueCells = check?.issues?.filter { it.error }?.flatMap { it.cells }?.toSet().orEmpty()
        val marked = s.picked.toSet() + (if (s.tool == EditTool.DIGITS && check?.verdict == DraftVerdict.MULTIPLE) check.ambiguous else emptyList())
        GridBoard(d.toRules(), d.size, d.regions, d.givens, d.givens, selected = s.selected, marked = marked, bad = issueCells,
            tintRegions = s.tool == EditTool.REGIONS, extras = d.extras, cages = d.cages, parity = d.parity, diagonals = GridRule.DIAGONALS in d.rules,
            drags = s.tool in setOf(EditTool.REGIONS, EditTool.CAGES, EditTool.GROUPS)) { cells, tap ->
            when (s.tool) {
                EditTool.REGIONS -> vm.change { it.paintRegion(cells, s.region) }
                EditTool.DIGITS -> if (tap) vm.select(cells.first().takeIf { it != s.selected })
                EditTool.CAGES, EditTool.GROUPS -> if (tap) vm.pick(cells.first()) else vm.pickAll(cells)
                EditTool.MARKS -> if (tap) cells.first().let { cell ->
                    vm.change { it.setParity(cell, Parity.entries[(it.parity[cell].ordinal + 1) % Parity.entries.size]) }
                }
                EditTool.RULES -> {}
            }
        }
        when (s.tool) {
            EditTool.REGIONS -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until d.size).forEach { g ->
                        val count = d.regions.count { it == g }
                        Surface(onClick = { vm.setRegion(g) }, shape = MaterialTheme.shapes.small, color = regionColor(g),
                            border = BorderStroke(if (s.region == g) 3.dp else 1.dp, if (s.region == g) c.text else c.outline),
                            modifier = Modifier.size(width = 46.dp, height = 40.dp).semantics { contentDescription = say("Region ${g + 1}, $count squares") }) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("$count", fontWeight = FontWeight.Bold, color = if (count == d.size) Color(0xFF1B5E20) else Color(0xFF2A2A2E))
                            }
                        }
                    }
                    Surface(onClick = { vm.setRegion(-1) }, shape = MaterialTheme.shapes.small, color = c.surface,
                        border = BorderStroke(if (s.region == -1) 3.dp else 1.dp, if (s.region == -1) c.text else c.outline),
                        modifier = Modifier.size(width = 64.dp, height = 40.dp)) { Box(contentAlignment = Alignment.Center) { Text("Erase", fontSize = 12.sp) } }
                }
                Text("Each number is how many squares that region has; every region needs ${d.size}.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GridRules.boxShapes(d.size).forEach { (br, bc) -> OutlinedButton(onClick = { vm.change { it.withBoxes(br, bc) } }) { Text("$br×$bc boxes") } }
                    OutlinedButton(onClick = vm::jigsaw, enabled = !s.working) { Text("Random jigsaw") }
                    OutlinedButton(onClick = { vm.change { it.clearRegions() } }) { Text("Clear regions") }
                }
            }
            EditTool.DIGITS -> {
                DigitPad(d.size, enabled = s.selected != null) { digit -> s.selected?.let { cell -> vm.change { it.setGiven(cell, if (it.givens[cell] == digit) 0 else digit) } } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { s.selected?.let { cell -> vm.change { it.setGiven(cell, 0) } } }, enabled = s.selected != null) { Text("Erase digit") }
                    OutlinedButton(onClick = { vm.change { it.clearGivens() } }) { Text("Clear digits") }
                    OutlinedButton(onClick = { vm.autoGivens(1) }, enabled = !s.working) { Text("Fill in digits for me") }
                    OutlinedButton(onClick = vm::makeUnique, enabled = !s.working && check?.verdict == DraftVerdict.MULTIPLE) { Text("Make the answer unique") }
                }
            }
            EditTool.CAGES -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { cageSum = "" }, enabled = s.picked.isNotEmpty()) { Text("Make cage (${s.picked.size})") }
                OutlinedButton(onClick = vm::clearPicked, enabled = s.picked.isNotEmpty()) { Text("Clear selection") }
                OutlinedButton(onClick = { s.picked.firstOrNull()?.let { cell -> vm.change { it.removeCageAt(cell) }; vm.clearPicked() } }, enabled = s.picked.isNotEmpty()) { Text("Remove cage") }
            }
            EditTool.GROUPS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::makeGroup, enabled = s.picked.size >= 2) { Text("Make group (${s.picked.size})") }
                OutlinedButton(onClick = vm::clearPicked, enabled = s.picked.isNotEmpty()) { Text("Clear selection") }
                OutlinedButton(onClick = { s.picked.firstOrNull()?.let { cell -> vm.change { it.removeExtraAt(cell) }; vm.clearPicked() } }, enabled = s.picked.isNotEmpty()) { Text("Remove group") }
                if (d.size == 9) OutlinedButton(onClick = { vm.change { it.withWindows() } }) { Text("Add Windoku windows") }
            }
            EditTool.MARKS -> {}
            EditTool.RULES -> GridRule.entries.forEach { rule ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.label, style = MaterialTheme.typography.bodyLarge)
                        Text(ruleHelp(rule), style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    androidx.compose.material3.Switch(checked = rule in d.rules, onCheckedChange = { vm.change { it.toggleRule(rule) } })
                }
            }
        }
        CheckPanel(check, s.checking)
        MessageLine(s.message)
    }
    cageSum?.let { value ->
        AlertDialog(onDismissRequest = { cageSum = null }, title = { Text("Cage total") },
            text = {
                OutlinedTextField(value, { cageSum = it.filter(Char::isDigit).take(3) }, label = { Text("Total") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = { TextButton(onClick = { value.toIntOrNull()?.takeIf { it > 0 }?.let { vm.makeCage(it) }; cageSum = null }) { Text("Make cage") } },
            dismissButton = { TextButton(onClick = { cageSum = null }) { Text("Cancel") } }, containerColor = c.surface)
    }
    if (naming) {
        var name by rememberSaveable { mutableStateOf(d.name) }
        AlertDialog(onDismissRequest = { naming = false }, title = { Text("Save grid") },
            text = { OutlinedTextField(name, { name = it.take(40).replace("\n", " ") }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.save(name.trim().ifEmpty { "Untitled grid" }); naming = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } }, containerColor = c.surface)
    }
    if (leaving) ConfirmDialog("Leave without saving?", "Your changes to this grid will be lost.", "Leave",
        onConfirm = { leaving = false; vm.close(); nav.popBackStack() }, onDismiss = { leaving = false }, dismiss = "Keep editing")
}

/** Status for a saved grid: a short label and why it isn't playable. */
fun verdictLabel(v: DraftVerdict?): Pair<String, String> = when (v) {
    null -> "Checking…" to ""
    DraftVerdict.UNIQUE -> "Ready to play" to ""
    DraftVerdict.BROKEN -> "Not playable yet" to "The layout isn't finished: check the region sizes and cages."
    DraftVerdict.CLASHES -> "Not playable yet" to "Some starting digits break the rules."
    DraftVerdict.NO_SOLUTION -> "Not playable yet" to "No answer fits every rule."
    DraftVerdict.MULTIPLE -> "Not playable yet" to "It has more than one answer. Add starting digits."
    DraftVerdict.TOO_HARD -> "Not playable yet" to "Too open to check. Add a few starting digits."
}

private fun toolHelp(tool: EditTool, n: Int) = when (tool) {
    EditTool.REGIONS -> "Pick a region colour, then tap or drag across squares to paint them. Every region needs exactly $n squares."
    EditTool.DIGITS -> "Tap a square, then a digit to place a starting digit. Tap the same digit again to remove it."
    EditTool.CAGES -> "Tap or drag to pick squares, then make a cage and enter its total. Cage digits can't repeat."
    EditTool.MARKS -> "Tap a square to cycle: odd (circle), even (square), none."
    EditTool.GROUPS -> "Pick squares that must all hold different digits, like Windoku windows, then make a group."
    EditTool.RULES -> "Switch on rules that apply to the whole grid."
}

private fun ruleHelp(rule: GridRule) = when (rule) {
    GridRule.DIAGONALS -> "Both long diagonals hold each digit once."
    GridRule.ANTI_KNIGHT -> "Squares a chess knight's move apart can't share a digit."
    GridRule.ANTI_KING -> "Diagonally touching squares can't share a digit."
    GridRule.NON_CONSECUTIVE -> "Side-by-side squares can't hold consecutive digits."
}

@Composable
private fun CheckPanel(check: com.simplegamegen.sudoku.grids.DraftCheck?, checking: Boolean) {
    val c = LocalGameLook.current.colors
    val (title, color, detail) = when {
        checking || check == null -> Triple("Checking…", c.muted, "Looking for answers to this grid.")
        check.verdict == DraftVerdict.BROKEN -> Triple("Layout not finished", c.danger, "Fix the problems below.")
        check.verdict == DraftVerdict.CLASHES -> Triple("Starting digits clash", c.danger, "Two starting digits break a rule.")
        check.verdict == DraftVerdict.NO_SOLUTION -> Triple("No answer", c.danger, "No way to fill this grid follows every rule. Remove a digit or a rule.")
        check.verdict == DraftVerdict.MULTIPLE -> Triple("More than one answer", c.highlight,
            "Highlighted squares differ between two answers. Add starting digits there, or use “Make the answer unique”.")
        check.verdict == DraftVerdict.TOO_HARD -> Triple("Couldn't finish checking", c.highlight, "This grid is too open to check quickly. Add a few starting digits.")
        check.logicSolvable -> Triple("Ready to play", c.success, "Exactly one answer, and it can be solved step by step without guessing.")
        else -> Triple("Ready to play", c.success, "Exactly one answer. It needs harder techniques than simple deductions.")
    }
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            check?.issues?.forEach { issue ->
                Text((if (issue.error) "• " else "◦ ") + issue.message, style = MaterialTheme.typography.bodySmall,
                    color = if (issue.error) c.danger else c.muted, textAlign = TextAlign.Start)
            }
        }
    }
}
