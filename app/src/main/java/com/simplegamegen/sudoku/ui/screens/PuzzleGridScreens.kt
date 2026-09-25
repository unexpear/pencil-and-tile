package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.HintButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.simplegamegen.sudoku.grids.HitoriGame
import com.simplegamegen.sudoku.grids.HitoriSolver
import com.simplegamegen.sudoku.grids.Mark
import com.simplegamegen.sudoku.grids.Nonogram
import com.simplegamegen.sudoku.grids.Shade
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.wordplay.CodeCracker
import com.simplegamegen.sudoku.wordplay.Dropquote
import kotlin.math.abs
import kotlin.math.floor
import com.simplegamegen.sudoku.ui.i18n.say

/** Paper and ink for printed-style grids, matching the logic puzzles. */
private class GridInk(val paper: Color, val ink: Color, val line: Color, val block: Color)

@Composable
private fun gridInk(): GridInk {
    val look = LocalGameLook.current
    val c = look.colors
    return if (look.dark) GridInk(c.surfaceAlt, c.text.copy(alpha = 0.92f), c.outline, Color(0xFF0B0B0D))
    else GridInk(Color(0xFFFFFEFA), Color(0xFF2A2A2E), Color(0xFFC9C4B8), Color(0xFF2A2A2E))
}

// ---------------- Nonograms ----------------

val NonogramSetup: (PuzzleFactory) -> PlaySetup<Nonogram> = { factory ->
    PlaySetup(
        rules = "Paint the hidden picture. The numbers beside each row and above each column give the lengths of its runs of " +
            "filled squares, in order, with at least one gap between runs. Use Fill to paint and Cross to mark squares you know " +
            "are empty. Drag along a row or column to paint several squares at once. Every picture can be solved by logic alone.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { Nonogram.SIZES[it].let { n -> "$n×$n picture" } },
        settingOf = { it.setting }, inProgress = { !it.complete },
        subtitle = { "${LevelNames[it.setting]} · ${it.width}×${it.height}" },
        create = { level -> { factory.custom("nonogram:$level", { seed -> Nonogram.generate(seed, level) }) { g -> g.solution.joinToString("") { if (it) "1" else "0" } } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@Composable
fun NonogramScreen(nav: NavController, vm: PlayViewModel<Nonogram>, factory: PuzzleFactory) {
    var crossMode by rememberSaveable { mutableStateOf(false) }
    var checked by remember { mutableStateOf(emptySet<Int>()) }
    PlayShell(nav, vm, GameId.NONOGRAM, remember(factory) { NonogramSetup(factory) }, tools = { g, s ->
        ToolButton(GameIcons.Fill, "Fill", active = !crossMode) { crossMode = false }
        ToolButton(GameIcons.Close, "Cross", active = crossMode) { crossMode = true }
        ToolButton(GameIcons.Check, "Check", enabled = !g.complete && !s.busy) {
            checked = g.mistakes()
            vm.say(if (checked.isEmpty()) "No mistakes so far." else (if (checked.size == 1) "1 square is wrong, marked in red." else "${checked.size} squares are wrong, marked in red."))
        }
        HintButton(GameId.NONOGRAM, enabled = !g.complete && !s.busy) { checked = emptySet(); vm.play("One square fixed.") { it.hint() } }
    }) { g, _ ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.marks.count { it == Mark.FILLED }} / ${g.solution.count { it }} filled")
            if (g.hints > 0) InfoChip("${g.hints} hints", icon = GameIcons.Hint)
        }
        if (g.complete) WinBanner("Picture complete!")
        NonogramBoard(g, checked, markFor = { cell ->
            val now = g.marks[cell]
            if (crossMode) { if (now == Mark.CROSSED) Mark.EMPTY else Mark.CROSSED } else { if (now == Mark.FILLED) Mark.EMPTY else Mark.FILLED }
        }) { cells, mark ->
            checked = emptySet()
            val next = g.paint(cells, mark)
            if (next != g) vm.play(if (next.complete) "Solved!" else null) { it.paint(cells, mark) }
        }
        if (!g.complete) Text(if (crossMode) "Cross mode: tap or drag to mark empty squares." else "Fill mode: tap or drag to paint squares.",
            style = MaterialTheme.typography.bodyMedium, color = LocalGameLook.current.colors.muted)
    }
}

@Composable
private fun NonogramBoard(g: Nonogram, checked: Set<Int>, markFor: (Int) -> Int, onPaint: (List<Int>, Int) -> Unit) {
    val look = LocalGameLook.current
    val c = look.colors
    val ink = gridInk()
    val measurer = rememberTextMeasurer()
    val rowClues = remember(g.solution) { g.rowClues }
    val colClues = remember(g.solution) { g.colClues }
    val kr = maxOf(1, rowClues.maxOf { it.size })
    val kc = maxOf(1, colClues.maxOf { it.size })
    val unit = 0.62f
    var stroke by remember { mutableStateOf(emptyList<Int>()) }
    var strokeMark by remember { mutableIntStateOf(Mark.FILLED) }
    val latestMark by rememberUpdatedState(markFor)
    val latestPaint by rememberUpdatedState(onPaint)
    ZoomBox(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val cellDp = min(maxWidth / (g.width + kr * unit + 0.3f), 44.dp)
            val leftDp = cellDp * (kr * unit + 0.3f)
            val topDp = cellDp * (kc * unit + 0.3f)
            Canvas(Modifier.size(leftDp + cellDp * g.width, topDp + cellDp * g.height)
                .semantics { contentDescription = say("Nonogram ${g.width} by ${g.height}, ${g.marks.count { it == Mark.FILLED }} squares filled") }
                .pointerInput(g.width, g.height, cellDp) {
                    val cell = cellDp.toPx(); val left = leftDp.toPx(); val top = topDp.toPx()
                    fun at(p: Offset) = floor((p.y - top) / cell).toInt() to floor((p.x - left) / cell).toInt()
                    // One gesture for taps and strokes, starting at the square first touched.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val (r0, c0) = at(down.position)
                        if (r0 !in 0 until g.height || c0 !in 0 until g.width) return@awaitEachGesture
                        down.consume()
                        strokeMark = latestMark(r0 * g.width + c0)
                        stroke = listOf(r0 * g.width + c0)
                        var lifted = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) { lifted = true; break }
                            val (r1, c1) = at(change.position).let { (r, k) -> r.coerceIn(0, g.height - 1) to k.coerceIn(0, g.width - 1) }
                            // Strokes lock to the start square's row or column.
                            stroke = if (abs(r1 - r0) > abs(c1 - c0)) (minOf(r0, r1)..maxOf(r0, r1)).map { it * g.width + c0 }
                            else (minOf(c0, c1)..maxOf(c0, c1)).map { r0 * g.width + it }
                        }
                        if (lifted && stroke.isNotEmpty()) latestPaint(stroke, strokeMark)
                        stroke = emptyList()
                    }
                }) {
                val cell = cellDp.toPx(); val left = leftDp.toPx(); val top = topDp.toPx()
                val gw = cell * g.width; val gh = cell * g.height
                val clueStyle = TextStyle(fontSize = (cell * 0.46f / density).sp, fontWeight = FontWeight.Bold)
                // Clue bands.
                drawRect(c.surfaceAlt, Offset(0f, top), Size(left, gh))
                drawRect(c.surfaceAlt, Offset(left, 0f), Size(gw, top))
                for (r in 0 until g.height) {
                    val done = g.lineDone(g.row(r))
                    val clue = rowClues[r].ifEmpty { listOf(0) }
                    clue.reversed().forEachIndexed { k, n ->
                        val t = measurer.measure(n.toString(), clueStyle.copy(color = if (done) c.muted.copy(alpha = 0.55f) else c.text))
                        drawText(t, topLeft = Offset(left - cell * 0.15f - (k + 0.5f) * cell * unit - t.size.width / 2f, top + r * cell + (cell - t.size.height) / 2f))
                    }
                }
                for (col in 0 until g.width) {
                    val done = g.lineDone(g.col(col))
                    val clue = colClues[col].ifEmpty { listOf(0) }
                    clue.reversed().forEachIndexed { k, n ->
                        val t = measurer.measure(n.toString(), clueStyle.copy(color = if (done) c.muted.copy(alpha = 0.55f) else c.text))
                        drawText(t, topLeft = Offset(left + col * cell + (cell - t.size.width) / 2f, top - cell * 0.15f - (k + 0.5f) * cell * unit - t.size.height / 2f))
                    }
                }
                drawRect(ink.paper, Offset(left, top), Size(gw, gh))
                val preview = stroke.toSet()
                for (i in g.marks.indices) {
                    val x = left + (i % g.width) * cell; val y = top + (i / g.width) * cell
                    val mark = if (i in preview) strokeMark else g.marks[i]
                    val alpha = if (i in preview) 0.6f else 1f
                    if (i in checked) drawRect(c.danger.copy(alpha = 0.22f), Offset(x, y), Size(cell, cell))
                    when (mark) {
                        Mark.FILLED -> drawRect((if (i in checked) c.danger else ink.block).copy(alpha = alpha), Offset(x + cell * 0.06f, y + cell * 0.06f), Size(cell * 0.88f, cell * 0.88f))
                        Mark.CROSSED -> {
                            val col = (if (i in checked) c.danger else c.muted).copy(alpha = alpha)
                            drawLine(col, Offset(x + cell * 0.3f, y + cell * 0.3f), Offset(x + cell * 0.7f, y + cell * 0.7f), strokeWidth = cell * 0.07f, cap = StrokeCap.Round)
                            drawLine(col, Offset(x + cell * 0.7f, y + cell * 0.3f), Offset(x + cell * 0.3f, y + cell * 0.7f), strokeWidth = cell * 0.07f, cap = StrokeCap.Round)
                        }
                    }
                    if (mark == Mark.EMPTY && i in preview) drawRect(c.accent.copy(alpha = 0.18f), Offset(x, y), Size(cell, cell))
                }
                // Grid lines, heavier every five squares.
                for (k in 0..g.width) {
                    val heavy = k % 5 == 0 || k == g.width
                    drawLine(if (heavy) ink.ink else ink.line, Offset(left + k * cell, top), Offset(left + k * cell, top + gh), strokeWidth = if (heavy) 2.2f else 1f)
                }
                for (k in 0..g.height) {
                    val heavy = k % 5 == 0 || k == g.height
                    drawLine(if (heavy) ink.ink else ink.line, Offset(left, top + k * cell), Offset(left + gw, top + k * cell), strokeWidth = if (heavy) 2.2f else 1f)
                }
            }
        }
    }
}

// ---------------- Hitori ----------------

val HitoriSetup: (PuzzleFactory) -> PlaySetup<HitoriGame> = { factory ->
    PlaySetup(
        rules = "Shade squares so that no number appears twice in any row or column among the unshaded squares. Shaded squares " +
            "may not touch side by side, and all unshaded squares must stay connected in one area. Tap a square to shade it, " +
            "tap again to circle it (a reminder that it stays white), and once more to clear it. Every puzzle has one answer.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { HitoriGame.SIZES[it].let { n -> "$n×$n grid" } },
        settingOf = { it.setting }, inProgress = { !it.complete },
        subtitle = { "${LevelNames[it.setting]} · ${it.size}×${it.size}" },
        create = { level -> { factory.custom("hitori:$level", { seed -> HitoriGame.generate(seed, level) }) { it.numbers.joinToString(",") } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@Composable
fun HitoriScreen(nav: NavController, vm: PlayViewModel<HitoriGame>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    val ink = gridInk()
    var checked by remember { mutableStateOf(emptySet<Int>()) }
    PlayShell(nav, vm, GameId.HITORI, remember(factory) { HitoriSetup(factory) }, tools = { g, s ->
        ToolButton(GameIcons.Check, "Check", enabled = !g.complete && !s.busy) {
            checked = g.mistakes()
            vm.say(if (checked.isEmpty()) "No mistakes so far." else (if (checked.size == 1) "1 square is wrong, marked in red." else "${checked.size} squares are wrong, marked in red."))
        }
        HintButton(GameId.HITORI, enabled = !g.complete && !s.busy) { checked = emptySet(); vm.play("One square fixed.") { it.hint() } }
    }) { g, _ ->
        val n = g.size
        val conflicts = g.conflicts()
        val split = !HitoriSolver.whitesConnected(n, BooleanArray(n * n) { g.marks[it] == Shade.SHADED })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.marks.count { it == Shade.SHADED }} shaded")
            if (g.hints > 0) InfoChip("${g.hints} hints", icon = GameIcons.Hint)
        }
        if (g.complete) WinBanner("Solved!")
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = min(maxWidth / n, 54.dp)
                Column(Modifier.border(2.dp, ink.ink)) {
                    for (r in 0 until n) Row {
                        for (k in 0 until n) {
                            val i = r * n + k
                            val mark = g.marks[i]
                            val bad = i in conflicts || i in checked
                            Box(Modifier.size(side).background(if (mark == Shade.SHADED) (if (bad) c.danger else ink.block) else if (bad) c.danger.copy(alpha = 0.16f) else ink.paper)
                                .border(0.5.dp, ink.line)
                                .clickable(role = Role.Button, enabled = !g.complete) { checked = emptySet(); vm.play { it.cycle(i) } }
                                .semantics { contentDescription = say("Row ${r + 1}, column ${k + 1}, ${g.numbers[i]}" + when (mark) { Shade.SHADED -> ", shaded"; Shade.CIRCLED -> ", circled"; else -> "" }) },
                                contentAlignment = Alignment.Center) {
                                if (mark == Shade.CIRCLED) Box(Modifier.size(side * 0.78f).border(2.dp, if (bad) c.danger else c.accent, CircleShape))
                                Text(g.numbers[i].toString(), fontSize = (side.value * 0.44f).sp, fontWeight = FontWeight.Bold,
                                    color = when { mark == Shade.SHADED -> ink.paper.copy(alpha = 0.55f); bad -> c.danger; else -> ink.ink })
                            }
                        }
                    }
                }
            }
        }
        if (split && !g.complete) Text("The shaded squares cut the white area apart.", style = MaterialTheme.typography.bodyMedium, color = c.danger)
        else if (!g.complete) Text("Tap: shade · tap again: circle (keep) · third tap: clear.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

// ---------------- Code cracker ----------------

val CodeCrackerSetup: (PuzzleFactory) -> PlaySetup<CodeCracker> = { factory ->
    PlaySetup(
        rules = "A crossword without clues: every letter has been replaced by a number, and the same number always means the same " +
            "letter. A few letters are given to start. Tap a square, then type the letter you think its number stands for; every " +
            "square with that number fills in. Every across and down run of letters is a real word.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level -> LogicLevel.entries[level].let { "${CodeCracker.sizeOf(it)}×${CodeCracker.sizeOf(it)} grid · " + (if (CodeCracker.reveals(it) == 1) "1 letter given" else "${CodeCracker.reveals(it)} letters given") } },
        settingOf = { it.level.ordinal }, inProgress = { !it.complete },
        subtitle = { "${it.level.label} · ${(1..it.count).count { n -> it.guessOf(n) != null }} of ${it.count} letters" },
        create = { level -> { factory.custom("codecracker:$level", { seed -> CodeCracker.generate(seed, LogicLevel.entries[level]) }) { it.grid } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodeCrackerScreen(nav: NavController, vm: PlayViewModel<CodeCracker>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    val ink = gridInk()
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var checked by remember { mutableStateOf(emptySet<Int>()) }
    PlayShell(nav, vm, GameId.CODE_CRACKER, remember(factory) { CodeCrackerSetup(factory) }, tools = { g, s ->
        ToolButton(GameIcons.Check, "Check", enabled = !g.complete && !s.busy) {
            checked = g.mistakes()
            vm.say(if (checked.isEmpty()) "No wrong letters so far." else (if (checked.size == 1) "1 number has the wrong letter, marked in red." else "${checked.size} numbers have the wrong letter, marked in red."))
        }
        HintButton(GameId.CODE_CRACKER, enabled = !g.complete && !s.busy) { checked = emptySet(); vm.play("One letter revealed.") { it.hint(selected) } }
    }) { g, _ ->
        val sel = selected?.takeIf { it in 1..g.count }
        if (g.complete) WinBanner("Code cracked!")
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = min(maxWidth / g.size, 42.dp)
                Column(Modifier.border(2.dp, ink.ink)) {
                    for (r in 0 until g.size) Row {
                        for (k in 0 until g.size) {
                            val i = r * g.size + k
                            val num = g.numberAt(i)
                            if (num == 0) Box(Modifier.size(side).background(ink.block))
                            else {
                                val guess = g.guessOf(num)
                                Box(Modifier.size(side).background(when { num == sel -> c.highlightSoft; num in checked -> c.danger.copy(alpha = 0.16f); else -> ink.paper })
                                    .border(0.5.dp, ink.line)
                                    .clickable(role = Role.Button) { selected = if (sel == num) null else num }
                                    .semantics { contentDescription = say("Number $num, " + (guess?.let { "letter $it" } ?: "unsolved")) }) {
                                    Text(num.toString(), Modifier.padding(start = 2.dp), fontSize = (side.value * 0.26f).sp, color = ink.ink.copy(alpha = 0.6f), lineHeight = (side.value * 0.3f).sp)
                                    if (guess != null) Text(guess.toString(), Modifier.align(Alignment.Center).padding(top = side * 0.12f), fontSize = (side.value * 0.5f).sp, fontWeight = FontWeight.Bold,
                                        color = when { num in checked -> c.danger; num in g.given -> ink.ink; else -> c.accent })
                                }
                            }
                        }
                    }
                }
            }
        }
        // The number key, as printed below the grid.
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (num in 1..g.count) {
                val guess = g.guessOf(num)
                Surface(onClick = { selected = if (sel == num) null else num }, color = if (num == sel) c.highlightSoft else c.surface,
                    border = BorderStroke(1.dp, if (num in checked) c.danger else c.outline), shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.size(width = 32.dp, height = 44.dp).semantics { contentDescription = say("Key $num, " + (guess?.let { "letter $it" } ?: "unsolved")) }) {
                    Box {
                        Text(num.toString(), Modifier.padding(start = 3.dp, top = 1.dp), fontSize = 10.sp, lineHeight = 11.sp, color = c.muted)
                        Text(guess?.toString() ?: "", Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp), fontSize = 17.sp, lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold, color = if (num in checked) c.danger else if (num in g.given) c.text else c.accent)
                    }
                }
            }
        }
        if (!g.complete) {
            Text(sel?.let { if (it in g.given) "Number $it is given." else "Number $it · type its letter" } ?: "Tap a square or number to choose it.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted)
            LetterKeys(onKey = { ch -> sel?.let { n -> checked = emptySet(); vm.play { it.guess(n, ch) } } }, dim = { it in g.usedLetters }) {
                OutlinedButton(onClick = { sel?.let { n -> vm.play { it.guess(n, null) } } }, modifier = Modifier.height(46.dp)) { Text("Clear") }
            }
        }
    }
}

// ---------------- Dropquote ----------------

val DropquoteSetup: (PuzzleFactory) -> PlaySetup<Dropquote> = { factory ->
    PlaySetup(
        rules = "A saying is hidden in the grid. The letters above each column belong somewhere in that column, in a different " +
            "order. Tap a square, then tap one of its column's letters to drop it in. Black squares separate words, and words " +
            "never break across lines. Sayings are proverbs or public-domain quotations.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level -> LogicLevel.entries[level].let { Dropquote.letterRange(it).let { r -> "${r.first}–${r.last} letters, up to ${Dropquote.widthOf(it)} columns" } } },
        settingOf = { it.level.ordinal }, inProgress = { !it.complete },
        create = { level -> { factory.custom("dropquote:$level", { seed -> Dropquote.generate(seed, LogicLevel.entries[level]) }) { it.quote } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@Composable
fun DropquoteScreen(nav: NavController, vm: PlayViewModel<Dropquote>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    val ink = gridInk()
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var checked by remember { mutableStateOf(emptySet<Int>()) }
    PlayShell(nav, vm, GameId.DROPQUOTE, remember(factory) { DropquoteSetup(factory) }, tools = { g, s ->
        ToolButton(GameIcons.Eraser, "Erase", enabled = !g.complete && selected?.let { g.typed.getOrNull(it)?.isLetter() } == true) {
            selected?.let { cell -> vm.play { it.place(cell, null) } }
        }
        ToolButton(GameIcons.Check, "Check", enabled = !g.complete && !s.busy) {
            checked = g.mistakes()
            vm.say(if (checked.isEmpty()) "No wrong letters so far." else (if (checked.size == 1) "1 letter is wrong, marked in red." else "${checked.size} letters are wrong, marked in red."))
        }
        HintButton(GameId.DROPQUOTE, enabled = !g.complete && !s.busy) { checked = emptySet(); vm.play("One letter placed.") { it.hint(selected) } }
    }) { g, _ ->
        val sel = selected?.takeIf { it in g.cells.indices && g.cells[it] != '#' }
        fun drop(col: Int, ch: Char) {
            val target = sel?.takeIf { it % g.width == col } ?: g.column(col).firstOrNull { g.cells[it] != '#' && g.typed[it] == '_' } ?: return
            val next = g.place(target, ch)
            if (next == g) return
            checked = emptySet()
            vm.play(if (next.complete) "Solved!" else null) { it.place(target, ch) }
            selected = next.let { n -> ((target + 1) until n.cells.length).firstOrNull { n.cells[it] != '#' && n.typed[it] == '_' } } ?: target
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.typed.count { it.isLetter() }} / ${g.cells.count { it != '#' }} letters")
            if (g.hints > 0) InfoChip("${g.hints} hints", icon = GameIcons.Hint)
        }
        if (g.complete) Surface(color = c.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Solved!", style = MaterialTheme.typography.titleMedium, color = c.success)
                Text("“${g.quote}” — ${g.author}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = min(maxWidth / g.width, 40.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Letter pools: each column's letters, with used ones faded.
                    Row(Modifier.background(c.surfaceAlt, MaterialTheme.shapes.small)) {
                        for (col in 0 until g.width) {
                            val left = g.left(col)
                            val pool = g.pool(col)
                            val seen = mutableMapOf<Char, Int>()
                            Column(Modifier.size(width = side, height = side * 0.78f * g.rows).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                pool.forEach { ch ->
                                    val k = seen.merge(ch, 1, Int::plus)!!
                                    val used = k > (left[ch] ?: 0)
                                    Box(Modifier.size(width = side, height = side * 0.78f)
                                        .clickable(enabled = !used && !g.complete, role = Role.Button) { drop(col, ch) }
                                        .semantics { contentDescription = say("Column ${col + 1} letter $ch" + if (used) ", used" else "") }, contentAlignment = Alignment.Center) {
                                        Text(ch.toString(), fontSize = (side.value * 0.46f).sp, fontWeight = FontWeight.Bold,
                                            color = if (used) c.muted.copy(alpha = 0.35f) else if (sel != null && sel % g.width == col) c.accent else c.text)
                                    }
                                }
                            }
                        }
                    }
                    Box(Modifier.size(width = side * g.width, height = 8.dp))
                    Column(Modifier.border(2.dp, ink.ink)) {
                        for (r in 0 until g.rows) Row {
                            for (col in 0 until g.width) {
                                val i = r * g.width + col
                                if (g.cells[i] == '#') Box(Modifier.size(side).background(ink.block))
                                else Box(Modifier.size(side)
                                    .background(when { i == sel -> c.highlightSoft; i in checked -> c.danger.copy(alpha = 0.16f); sel != null && sel % g.width == col -> c.accentSoft; else -> ink.paper })
                                    .border(0.5.dp, ink.line)
                                    .clickable(role = Role.Button, enabled = !g.complete) { selected = if (sel == i) null else i }
                                    .semantics { contentDescription = say("Row ${r + 1}, column ${col + 1}, " + (g.typed[i].takeIf { it.isLetter() }?.let { "letter $it" } ?: "empty")) },
                                    contentAlignment = Alignment.Center) {
                                    val ch = g.typed[i]
                                    if (ch.isLetter()) Text(ch.toString(), fontSize = (side.value * 0.52f).sp, fontWeight = FontWeight.Bold,
                                        color = if (i in checked) c.danger else ink.ink)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!g.complete) Text(if (sel == null) "Tap a square, then a letter above its column." else "Tap a letter in the highlighted column.",
            style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}
