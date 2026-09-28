package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.ReportPlay
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.data.Outcome
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simplegamegen.sudoku.logic.LogicGuide
import com.simplegamegen.sudoku.logic.LogicKind
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.logic.LogicProgress
import com.simplegamegen.sudoku.logic.LogicRules
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.LogicState
import com.simplegamegen.sudoku.ui.LogicViewModel
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.ConfirmDialog
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.HeaderAction
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.MenuAction
import com.simplegamegen.sudoku.ui.components.MessageLine
import com.simplegamegen.sudoku.ui.components.NewGameSheet
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.StartCard
import com.simplegamegen.sudoku.ui.components.ToolBar
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.i18n.say

private val GridNames = listOf("Top left", "Top right", "Center", "Bottom left", "Bottom right")

@Composable
fun LogicScreen(nav: NavController, vm: LogicViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val look = LocalGameLook.current
    val id = GameId.of(vm.kind)
    val progress = s.progress
    var level by rememberSaveable { mutableStateOf(LogicLevel.EASY) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var rules by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(progress?.puzzle) { progress?.puzzle?.let { level = it.level } }
    fun start() { if (progress != null && !progress.complete) confirm = true else { sheet = false; vm.newGame(level) } }
    val playable = progress != null && !s.busy && !progress.complete
    ReportPlay(id, key = progress?.puzzle?.seed?.toString(), level = progress?.puzzle?.level?.label ?: "",
        levelIndex = progress?.puzzle?.level?.ordinal ?: 0, inProgress = progress != null && !progress.complete,
        outcome = if (progress?.complete == true) Outcome(Result.WON) else null)
    GameScaffold(
        title = vm.kind.title, game = id, tutorial = id, onBack = { nav.popBackStack() }, busy = s.busy,
        subtitle = progress?.let { "${it.puzzle.level.label} · ${LogicGuide.levelDescription(vm.kind, it.puzzle.level)}" },
        actions = { HeaderAction(GameIcons.Rules, "Rules") { rules = true } },
        menu = if (progress == null) emptyList() else listOf(
            MenuAction("New puzzle", GameIcons.Plus, enabled = !s.busy) { sheet = true },
            MenuAction("Check for mistakes", GameIcons.Check, enabled = playable, onClick = vm::check),
            MenuAction("Save now", GameIcons.Save, enabled = !s.busy, onClick = vm::retrySave),
        ),
        bottomBar = if (progress == null) null else {
            {
                ToolBar {
                    ToolButton(GameIcons.Undo, "Undo", enabled = !s.busy && s.canUndo, onClick = vm::undo)
                    ToolButton(GameIcons.Eraser, "Erase", enabled = playable && s.selected != null, onClick = vm::erase)
                    ToolButton(GameIcons.Pencil, if (s.notesMode) "Notes on" else "Notes", enabled = playable, active = s.notesMode, onClick = vm::toggleNotes)
                    ToolButton(GameIcons.Check, "Check", enabled = playable, onClick = vm::check)
                    HintButton(id, enabled = playable, onClick = vm::hint)
                }
            }
        },
    ) {
        if (progress == null) GameFamilyPicker(nav, id)
        when {
            progress == null && s.busy -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            progress == null -> StartCard(vm.kind.title, LogicGuide.rules(vm.kind), art = { GameArt(id, Modifier.size(64.dp)) }, tutorial = id) {
                LevelPicker(vm.kind, level) { level = it }
                MessageLine(s.message)
                Button(onClick = { vm.newGame(level) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start puzzle") }
            }
            else -> {
                val conflicts = remember(progress) { progress.conflicts() }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val open = progress.puzzle.rules.open.indices.count { progress.editable(it) }
                    val filled = progress.puzzle.rules.open.indices.count { progress.editable(it) && progress.entries[it] != 0 }
                    InfoChip("$filled / $open filled")
                    if (progress.hints > 0) InfoChip((if (progress.hints == 1) "1 hint" else "${progress.hints} hints"), icon = GameIcons.Hint)
                    if (conflicts.isNotEmpty()) InfoChip("${conflicts.size} clashing", emphasized = true)
                }
                if (progress.complete) Surface(color = look.colors.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, look.colors.success), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Solved!", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = look.colors.success)
                        Button(onClick = { vm.newGame(progress.puzzle.level) }, enabled = !s.busy) { Text("Next puzzle") }
                    }
                }
                when (vm.kind) {
                    LogicKind.SAMURAI -> SamuraiBoard(progress, s, conflicts, vm::select, vm::setFocus)
                    LogicKind.KENKEN -> KenKenBoard(progress, s, conflicts, vm::select)
                    LogicKind.KAKURO -> KakuroBoard(progress, s, conflicts, vm::select)
                    LogicKind.FUTOSHIKI -> FutoshikiBoard(progress, s, conflicts, vm::select)
                }
                MessageLine(s.message)
                if (playable) DigitPad(progress.puzzle.rules.maxDigit, s.notesMode, vm::input)
            }
        }
    }
    if (sheet) NewGameSheet("New puzzle", startLabel = "Start puzzle", game = id, onDismiss = { sheet = false }, onStart = ::start) {
        GameFamilyPicker(nav, id)
        LevelPicker(vm.kind, level) { level = it }
    }
    if (rules) AlertDialog(onDismissRequest = { rules = false }, title = { Text("How to play ${vm.kind.title}") },
        text = { Text(LogicGuide.rules(vm.kind)) }, confirmButton = { TextButton(onClick = { rules = false }) { Text("Got it") } },
        containerColor = look.colors.surface)
    if (confirm) ConfirmDialog("Start a new puzzle?", "This replaces your saved ${vm.kind.title} puzzle.", "New puzzle",
        onConfirm = { confirm = false; sheet = false; vm.newGame(level) }, onDismiss = { confirm = false })
}

@Composable
private fun LevelPicker(kind: LogicKind, level: LogicLevel, onSelect: (LogicLevel) -> Unit) {
    OptionGroup("Difficulty", LogicLevel.entries, level, { it.label }, onSelect = onSelect)
    Text(LogicGuide.levelDescription(kind, level), style = MaterialTheme.typography.bodySmall, color = LocalGameLook.current.colors.muted)
}

/** Colors shared by the three boards. */
private class BoardInk(val paper: Color, val ink: Color, val thin: Color, val block: Color)

@Composable
private fun boardInk(): BoardInk {
    val look = LocalGameLook.current
    val c = look.colors
    return if (look.dark) BoardInk(c.surfaceAlt, c.text.copy(alpha = 0.9f), c.outline, Color(0xFF0B0B0D))
    else BoardInk(Color(0xFFFFFEFA), Color(0xFF2A2A2E), c.outline, Color(0xFF2A2A2E))
}

/** Background for an open cell given selection, peers, matching digits and errors. */
@Composable
private fun cellBackground(i: Int, p: LogicProgress, s: LogicState, peers: Set<Int>, conflicts: Set<Int>, paper: Color): Color {
    val c = LocalGameLook.current.colors
    val selectedValue = s.selected?.let { p.entries[it] } ?: 0
    return when {
        i in s.checked -> c.danger.copy(alpha = 0.3f).compositeOver(paper)
        i in conflicts -> c.danger.copy(alpha = 0.18f).compositeOver(paper)
        i == s.selected -> c.accent.copy(alpha = 0.34f).compositeOver(paper)
        selectedValue != 0 && p.entries[i] == selectedValue -> c.highlight.copy(alpha = 0.34f).compositeOver(paper)
        i in peers -> c.accent.copy(alpha = 0.09f).compositeOver(paper)
        else -> paper
    }
}

/** Digit or pencil marks for one open cell. */
@Composable
private fun CellContent(i: Int, p: LogicProgress, conflicts: Set<Int>, side: Dp, ink: Color) {
    val c = LocalGameLook.current.colors
    val v = p.entries[i]
    val given = p.puzzle.givens[i] != 0
    if (v != 0) Text(v.toString(), fontSize = (side.value * 0.52f).sp, fontWeight = if (given) FontWeight.Bold else FontWeight.Medium,
        color = when { i in conflicts -> c.danger; given -> ink; else -> c.accent })
    else if (p.notes[i].isNotEmpty()) NotesGrid(p.notes[i], p.puzzle.rules.maxDigit, c.muted)
}

private fun describe(i: Int, cols: Int, p: LogicProgress, extra: String = ""): String {
    val v = p.entries[i]
    return "Row ${i / cols + 1}, column ${i % cols + 1}, " + (if (v == 0) "empty" else "digit $v") +
        (if (p.puzzle.givens[i] != 0) ", given" else "") + extra
}

// ---------------- KenKen ----------------

@Composable
private fun KenKenBoard(p: LogicProgress, s: LogicState, conflicts: Set<Int>, onSelect: (Int) -> Unit) {
    val rules = p.puzzle.rules
    val n = rules.cols
    val ink = boardInk()
    val cageOf = remember(rules) { IntArray(rules.cellCount).also { a -> rules.cages.forEachIndexed { k, cage -> cage.cells.forEach { a[it] = k } } } }
    val labelAt = remember(rules) { rules.cages.associate { it.cells.min() to it.label } }
    val peers = s.selected?.let { sel -> rules.units.filter { sel in it }.flatten().toSet() } ?: emptySet()
    val shape = MaterialTheme.shapes.small
    ZoomBox(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = maxWidth / n
            Column(Modifier.clip(shape).border(2.5.dp, ink.ink, shape)) {
                for (r in 0 until n) Row {
                    for (c in 0 until n) {
                        val i = r * n + c
                        val bg = cellBackground(i, p, s, peers, conflicts, ink.paper)
                        Box(Modifier.size(side).background(bg).clickable(role = Role.Button) { onSelect(i) }
                            .semantics { contentDescription = say(describe(i, n, p, ", cage ${rules.cages[cageOf[i]].label}")) },
                            contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val w = size.width; val h = size.height; val thick = 1.5.dp.toPx()
                                drawRect(ink.thin, style = Stroke(0.5.dp.toPx()))
                                if (r == 0 || cageOf[i - n] != cageOf[i]) drawRect(ink.ink, Offset.Zero, Size(w, thick))
                                if (r == n - 1 || cageOf[i + n] != cageOf[i]) drawRect(ink.ink, Offset(0f, h - thick), Size(w, thick))
                                if (c == 0 || cageOf[i - 1] != cageOf[i]) drawRect(ink.ink, Offset.Zero, Size(thick, h))
                                if (c == n - 1 || cageOf[i + 1] != cageOf[i]) drawRect(ink.ink, Offset(w - thick, 0f), Size(thick, h))
                            }
                            labelAt[i]?.let { label ->
                                Text(label, Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp),
                                    fontSize = (side.value * 0.2f).coerceIn(9f, 13f).sp, fontWeight = FontWeight.Bold, color = ink.ink)
                            }
                            CellContent(i, p, conflicts, side, ink.ink)
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Futoshiki ----------------

@Composable
private fun FutoshikiBoard(p: LogicProgress, s: LogicState, conflicts: Set<Int>, onSelect: (Int) -> Unit) {
    val rules = p.puzzle.rules
    val n = rules.cols
    val ink = boardInk()
    val look = LocalGameLook.current
    val less = remember(rules) { rules.less.toSet() }
    val peers = s.selected?.let { sel -> rules.units.filter { sel in it }.flatten().toSet() } ?: emptySet()
    // Wide side of a sign faces the larger digit: "<" means left is smaller, "∧" means the upper square is smaller.
    fun sign(a: Int, b: Int, horizontal: Boolean): String? = when {
        a to b in less -> if (horizontal) "<" else "∧"
        b to a in less -> if (horizontal) ">" else "∨"
        else -> null
    }
    fun broken(a: Int, b: Int): Boolean {
        val va = p.entries[a]; val vb = p.entries[b]
        if (va == 0 || vb == 0) return false
        return (a to b in less && va >= vb) || (b to a in less && vb >= va)
    }
    ZoomBox(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = maxWidth / (n + (n - 1) * 0.42f)
            val gap = side * 0.42f
            val signSize = (side.value * 0.42f).sp
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                for (r in 0 until n) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (c in 0 until n) {
                            val i = r * n + c
                            Box(Modifier.size(side).clip(MaterialTheme.shapes.small)
                                .background(cellBackground(i, p, s, peers, conflicts, ink.paper))
                                .border(1.5.dp, ink.ink, MaterialTheme.shapes.small)
                                .clickable(role = Role.Button) { onSelect(i) }
                                .semantics { contentDescription = say(describe(i, n, p)) }, contentAlignment = Alignment.Center) {
                                CellContent(i, p, conflicts, side, ink.ink)
                            }
                            if (c < n - 1) Box(Modifier.size(gap, side), contentAlignment = Alignment.Center) {
                                sign(i, i + 1, true)?.let {
                                    Text(it, fontSize = signSize, fontWeight = FontWeight.Bold,
                                        color = if (broken(i, i + 1)) look.colors.danger else look.colors.text)
                                }
                            }
                        }
                    }
                    if (r < n - 1) Row {
                        for (c in 0 until n) {
                            val i = r * n + c
                            Box(Modifier.size(side, gap), contentAlignment = Alignment.Center) {
                                sign(i, i + n, false)?.let {
                                    Text(it, fontSize = signSize, fontWeight = FontWeight.Bold,
                                        color = if (broken(i, i + n)) look.colors.danger else look.colors.text)
                                }
                            }
                            if (c < n - 1) Box(Modifier.size(gap))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Kakuro ----------------

@Composable
private fun KakuroBoard(p: LogicProgress, s: LogicState, conflicts: Set<Int>, onSelect: (Int) -> Unit) {
    val rules = p.puzzle.rules
    val cols = rules.cols
    val ink = boardInk()
    val measurer = rememberTextMeasurer()
    // Across clues sit left of a run's first square; down clues sit above it.
    val acrossClue = remember(rules) { rules.runs.filter { it.across }.associate { it.cells.first() - 1 to it.sum } }
    val downClue = remember(rules) { rules.runs.filter { !it.across }.associate { it.cells.first() - cols to it.sum } }
    val peers = s.selected?.let { sel -> rules.runs.filter { sel in it.cells }.flatMap { it.cells }.toSet() } ?: emptySet()
    val shape = MaterialTheme.shapes.small
    ZoomBox(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = maxWidth / cols
            Column(Modifier.clip(shape).border(2.dp, ink.block, shape)) {
                for (r in 0 until rules.rows) Row {
                    for (c in 0 until cols) {
                        val i = r * cols + c
                        if (!rules.open[i]) {
                            val across = acrossClue[i]; val down = downClue[i]
                            Canvas(Modifier.size(side).semantics {
                                if (across != null || down != null) contentDescription =
                                    listOfNotNull(across?.let { "across sum $it" }, down?.let { "down sum $it" }).joinToString(", ")
                            }) {
                                drawRect(ink.block)
                                if (across == null && down == null) return@Canvas
                                drawLine(Color(0xFF8E8E93), Offset.Zero, Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                                val style = TextStyle(color = Color.White, fontSize = (side.value * 0.27f).sp, fontWeight = FontWeight.Bold)
                                across?.let {
                                    val l = measurer.measure(it.toString(), style)
                                    drawText(l, topLeft = Offset(size.width - l.size.width - size.width * 0.08f, size.height * 0.04f))
                                }
                                down?.let {
                                    val l = measurer.measure(it.toString(), style)
                                    drawText(l, topLeft = Offset(size.width * 0.08f, size.height - l.size.height - size.height * 0.02f))
                                }
                                drawRect(Color(0xFF3A3A3F), style = Stroke(0.5.dp.toPx()))
                            }
                        } else {
                            val bg = cellBackground(i, p, s, peers, conflicts, ink.paper)
                            Box(Modifier.size(side).background(bg).border(0.5.dp, ink.thin).clickable(role = Role.Button) { onSelect(i) }
                                .semantics { contentDescription = say(describe(i, cols, p)) }, contentAlignment = Alignment.Center) {
                                CellContent(i, p, conflicts, side, ink.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Samurai ----------------

@Composable
private fun SamuraiBoard(p: LogicProgress, s: LogicState, conflicts: Set<Int>, onSelect: (Int) -> Unit, onFocus: (Int) -> Unit) {
    val look = LocalGameLook.current
    val rules = p.puzzle.rules
    val ink = boardInk()
    val measurer = rememberTextMeasurer()
    val origins = LogicRules.SAMURAI_ORIGINS
    // Overview map: tap a grid to show it large below.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
    Surface(Modifier.weight(1f), color = look.colors.surface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, look.colors.outline)) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp)
            .semantics { contentDescription = say("Samurai map. Showing grid: ${GridNames[s.focus]}. Tap a grid to zoom in.") }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val cell = size.width / 21f
                    val r = pos.y / cell; val c = pos.x / cell
                    val grid = origins.indices.minBy { g -> val (r0, c0) = origins[g]; (r - (r0 + 4.5f)).let { it * it } + (c - (c0 + 4.5f)).let { it * it } }
                    onFocus(grid)
                }
            }) {
            val cell = size.width / 21f
            val style = TextStyle(fontSize = (cell * 0.62f / density).sp, fontWeight = FontWeight.SemiBold)
            for (i in 0 until 441) {
                if (!rules.open[i]) continue
                val o = Offset(i % 21 * cell, i / 21 * cell)
                val bg = when {
                    i in s.checked || i in conflicts -> look.colors.danger.copy(alpha = 0.25f).compositeOver(ink.paper)
                    i == s.selected -> look.colors.accent.copy(alpha = 0.4f).compositeOver(ink.paper)
                    else -> ink.paper
                }
                drawRect(bg, o, Size(cell, cell))
                drawRect(ink.thin, o, Size(cell, cell), style = Stroke(0.5f))
                val v = p.entries[i]
                if (v != 0) {
                    val l = measurer.measure(v.toString(), style.copy(color = if (p.puzzle.givens[i] != 0) ink.ink else look.colors.accent))
                    drawText(l, topLeft = o + Offset((cell - l.size.width) / 2, (cell - l.size.height) / 2))
                }
            }
            origins.forEachIndexed { g, (r0, c0) ->
                for (k in 0..3) {
                    drawLine(ink.ink, Offset((c0 + 3 * k) * cell, r0 * cell), Offset((c0 + 3 * k) * cell, (r0 + 9) * cell), strokeWidth = 1.2f)
                    drawLine(ink.ink, Offset(c0 * cell, (r0 + 3 * k) * cell), Offset((c0 + 9) * cell, (r0 + 3 * k) * cell), strokeWidth = 1.2f)
                }
                if (g == s.focus) drawRect(look.colors.highlight, Offset(c0 * cell, r0 * cell), Size(9 * cell, 9 * cell), style = Stroke(3.dp.toPx()))
            }
        }
    }
    // Grid picker laid out in the same X shape as the five grids.
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(listOf(0, null, 1), listOf(null, 2, null), listOf(3, null, 4)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { g ->
                    if (g == null) Box(Modifier.weight(1f).aspectRatio(1f)) else {
                        val on = g == s.focus
                        Surface(onClick = { onFocus(g) }, modifier = Modifier.weight(1f).aspectRatio(1f)
                            .semantics { contentDescription = say("${GridNames[g]} grid${if (on) ", showing" else ""}") },
                            shape = MaterialTheme.shapes.small, color = if (on) look.colors.accent else look.colors.surface,
                            border = BorderStroke(1.dp, if (on) look.colors.accent else look.colors.outline)) {
                            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                                val ink2 = if (on) look.colors.onAccent else look.colors.muted
                                drawRect(ink2, style = Stroke(1.5.dp.toPx()))
                                for (k in 1..2) {
                                    drawLine(ink2, Offset(size.width * k / 3, 0f), Offset(size.width * k / 3, size.height), strokeWidth = 1f)
                                    drawLine(ink2, Offset(0f, size.height * k / 3), Offset(size.width, size.height * k / 3), strokeWidth = 1f)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(GridNames[s.focus] + " grid", style = MaterialTheme.typography.labelLarge, color = look.colors.muted)
    }
    }
    // The focused 9×9 grid, full size.
    val (r0, c0) = origins[s.focus]
    val peers = s.selected?.let { sel -> rules.units.filter { sel in it }.flatten().toSet() } ?: emptySet()
    val shape = MaterialTheme.shapes.small
    ZoomBox(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = maxWidth / 9
            Column(Modifier.clip(shape).border(2.dp, ink.ink, shape)) {
                for (r in 0 until 9) Row {
                    for (c in 0 until 9) {
                        val i = (r0 + r) * 21 + c0 + c
                        val bg = cellBackground(i, p, s, peers, conflicts, ink.paper)
                        Box(Modifier.size(side).background(bg).clickable(role = Role.Button) { onSelect(i) }
                            .semantics { contentDescription = say("${GridNames[s.focus]} grid, " + describe(i, 21, p).replaceFirst(Regex("Row \\d+, column \\d+"), "row ${r + 1}, column ${c + 1}")) },
                            contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val w = size.width; val h = size.height
                                drawRect(ink.thin, style = Stroke(0.5.dp.toPx()))
                                val t = 1.5.dp.toPx()
                                if (r % 3 == 0) drawRect(ink.ink, Offset.Zero, Size(w, t))
                                if (c % 3 == 0) drawRect(ink.ink, Offset.Zero, Size(t, h))
                                if (r == 8) drawRect(ink.ink, Offset(0f, h - t), Size(w, t))
                                if (c == 8) drawRect(ink.ink, Offset(w - t, 0f), Size(t, h))
                            }
                            CellContent(i, p, conflicts, side, ink.ink)
                        }
                    }
                }
            }
        }
    }
}

/** Digit keys; in notes mode they toggle pencil marks. */
@Composable
private fun DigitPad(max: Int, notesMode: Boolean, onDigit: (Int) -> Unit) {
    val look = LocalGameLook.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in (1..max).chunked(if (max > 7) 5 else max)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in row) {
                    Surface(onClick = { onDigit(n) }, modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = say("Digit $n") },
                        shape = MaterialTheme.shapes.medium, color = if (notesMode) look.colors.surface else look.colors.accentSoft,
                        border = BorderStroke(1.dp, if (notesMode) look.colors.accent else look.colors.outline)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(n.toString(), fontSize = if (notesMode) 18.sp else 24.sp, fontWeight = FontWeight.SemiBold, color = look.colors.accent)
                        }
                    }
                }
                if (max > 7 && row.size < 5) repeat(5 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
