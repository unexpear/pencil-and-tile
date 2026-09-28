package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.tr
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.ReportPlay
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.data.Outcome
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.WordGameState
import com.simplegamegen.sudoku.ui.WordGameViewModel
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.ConfirmDialog
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.MenuAction
import com.simplegamegen.sudoku.ui.components.MessageLine
import com.simplegamegen.sudoku.ui.components.NewGameSheet
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.SectionTitle
import com.simplegamegen.sudoku.ui.components.StartCard
import com.simplegamegen.sudoku.ui.components.ToolBar
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.words.WordDifficulty
import com.simplegamegen.sudoku.words.WordGame
import com.simplegamegen.sudoku.words.WordProgress
import com.simplegamegen.sudoku.words.WordPuzzles
import java.util.Locale
import com.simplegamegen.sudoku.ui.i18n.say

/** Distinct marker colors for found words. */
private val FoundColors = listOf(0xFFF4B942, 0xFF7CC6A4, 0xFF8AB6F0, 0xFFF29C9C, 0xFFC5A3EE, 0xFFF6C38B, 0xFF9ED9E8, 0xFFE3E08A).map(::Color)

@Composable
fun WordGameScreen(nav: NavController, vm: WordGameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val look = LocalGameLook.current
    val progress = state.progress
    val crossword = vm.game == WordGame.CROSSWORD
    val id = if (crossword) GameId.CROSSWORD else GameId.WORD_SEARCH
    var theme by rememberSaveable { mutableIntStateOf(0) }
    var difficulty by rememberSaveable { mutableStateOf(WordDifficulty.EASY) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var confirmNew by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(progress?.puzzle) {
        progress?.puzzle?.let { puzzle ->
            puzzle.difficulty?.let { difficulty = it }
            WordPuzzles.themes.indexOf(puzzle.title).takeIf { it >= 0 }?.let { theme = it }
        }
    }
    fun start() { if (progress != null && !progress.complete) confirmNew = true else { sheet = false; vm.newGame(theme, difficulty) } }
    val title = if (crossword) "Crossword" else "Word search"
    ReportPlay(id, key = progress?.puzzle?.let { "${it.title}:${it.letters.hashCode()}" }, level = progress?.puzzle?.difficulty?.label ?: "Original",
        levelIndex = progress?.puzzle?.difficulty?.ordinal ?: 0, inProgress = progress != null && !progress.complete,
        outcome = if (progress?.complete == true) Outcome(Result.WON) else null)
    GameScaffold(
        title = title, game = id, tutorial = id, onBack = { nav.popBackStack() }, busy = state.busy,
        subtitle = progress?.puzzle?.let { p -> if (crossword) "${p.difficulty?.label ?: "Original"} · ${p.size}×${p.size}" else "${p.title} · ${p.difficulty?.label ?: "Original"}" },
        modifier = Modifier.imePadding(),
        menu = if (progress == null) emptyList() else listOf(
            MenuAction("New puzzle", GameIcons.Plus, enabled = !state.busy) { sheet = true },
            MenuAction("Save now", GameIcons.Save, enabled = !state.busy, onClick = vm::retrySave),
        ),
        bottomBar = if (progress == null) null else {
            {
                ToolBar {
                    if (crossword) {
                        ToolButton(GameIcons.Check, "Check", enabled = !state.busy, onClick = vm::checkAnswer)
                        HintButton(id, "Reveal letter", enabled = !state.busy && !progress.complete, onClick = vm::hint)
                        ToolButton(GameIcons.Eraser, "Clear", enabled = !state.busy && !progress.complete) { vm.answer("") }
                    } else {
                        HintButton(id, "Show a start", enabled = !state.busy && !progress.complete, onClick = vm::hint)
                    }
                    ToolButton(GameIcons.Plus, "New", enabled = !state.busy) { sheet = true }
                }
            }
        },
    ) {
        when {
            progress == null && state.busy -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            progress == null -> StartCard(title,
                if (crossword) "Solve a connected grid with original clues. Tap a clue or square, then type the answer."
                else "Find the hidden words. Tap the first letter of a word, then its last letter.",
                art = { GameArt(id, Modifier.size(96.dp)) }, tutorial = id) {
                WordOptions(crossword, difficulty, theme, { difficulty = it }, { theme = it })
                MessageLine(state.message)
                Button(onClick = { vm.newGame(theme, difficulty) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start puzzle") }
            }
            else -> {
                if (progress.complete) Surface(color = look.colors.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, look.colors.success), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Puzzle complete!", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = look.colors.success)
                        Button(onClick = { vm.newGame(theme, difficulty) }, enabled = !state.busy) { Text("Next puzzle") }
                    }
                }
                if (crossword) CrosswordPlay(progress, state, vm) else WordSearchPlay(progress, state, vm)
            }
        }
    }
    if (sheet) NewGameSheet("New puzzle", startLabel = "Start puzzle", game = id, onDismiss = { sheet = false }, onStart = ::start) {
        WordOptions(crossword, difficulty, theme, { difficulty = it }, { theme = it })
    }
    if (confirmNew) ConfirmDialog(
        title = "Start a new puzzle?",
        text = "This replaces your saved puzzle.",
        confirm = "New puzzle",
        onConfirm = { confirmNew = false; sheet = false; vm.newGame(theme, difficulty) },
        onDismiss = { confirmNew = false },
    )
}

@Composable
private fun WordOptions(crossword: Boolean, difficulty: WordDifficulty, theme: Int, onDifficulty: (WordDifficulty) -> Unit, onTheme: (Int) -> Unit) {
    val look = LocalGameLook.current
    OptionGroup("Difficulty", WordDifficulty.entries, difficulty, { it.label }, onSelect = onDifficulty)
    Text(difficulty.description(if (crossword) WordGame.CROSSWORD else WordGame.WORD_SEARCH), style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
    if (!crossword) OptionGroup("Theme", WordPuzzles.themes.indices.toList(), theme, { WordPuzzles.themes[it] }, onSelect = onTheme)
}

/** Fitted letter grid; shared zoom and pan controls reveal enlarged cells on either axis. */
@Composable
private fun LetterGrid(size: Int, cell: @Composable (index: Int, side: androidx.compose.ui.unit.Dp) -> Unit) {
    val look = LocalGameLook.current
    ZoomBox(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fit = maxWidth / size
            // Start fitted on both axes. ZoomBox owns all board movement, rather than
            // nesting a horizontal scroller around a board taller than its visible viewport.
            val side = min(fit, 48.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(7.dp).height(side * size).background(Color(0xFFE15B64)))
                Column(Modifier.clip(MaterialTheme.shapes.small).border(2.dp, look.colors.text.copy(alpha = 0.7f), MaterialTheme.shapes.small)) {
                    for (row in 0 until size) Row { for (col in 0 until size) cell(row * size + col, side) }
                }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrosswordPlay(progress: WordProgress, state: WordGameState, vm: WordGameViewModel) {
    val look = LocalGameLook.current
    val c = look.colors
    val puzzle = progress.puzzle
    val selected = puzzle.entries[state.selectedEntry]
    val solved = puzzle.entries.count { e -> e.cells.all { progress.current[it] == puzzle.letters[it] } }
    val paper = if (look.dark) c.surfaceAlt else Color(0xFFFFFEFA)
    val block = if (look.dark) Color(0xFF0B0B0D) else Color(0xFF1E1E22)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoChip("$solved / ${puzzle.entries.size} answers")
        if (progress.hints > 0) InfoChip("${progress.hints} revealed", icon = GameIcons.Hint)
    }
    // Keep the active clue before the board so changing squares does not require
    // scrolling past the grid to discover what was selected.
    Surface(color = c.surface, shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, c.accent), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.selectEntry((state.selectedEntry - 1 + puzzle.entries.size) % puzzle.entries.size) }) {
                Icon(GameIcons.Chevron, contentDescription = tr("Previous clue"), modifier = Modifier.rotate(180f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${puzzle.number(selected)} ${puzzle.direction(selected)} · ${selected.answer.length} letters",
                    style = MaterialTheme.typography.labelLarge, color = c.accent)
                Text(selected.clue, style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { vm.selectEntry((state.selectedEntry + 1) % puzzle.entries.size) }) {
                Icon(GameIcons.Chevron, contentDescription = tr("Next clue"))
            }
        }
    }
    LetterGrid(puzzle.size) { cell, side ->
        val blocked = puzzle.letters[cell] == '#'
        val letter = progress.current[cell]
        val number = puzzle.entries.firstOrNull { it.cells.first() == cell }?.let(puzzle::number)
        val inSelected = cell in selected.cells
        Box(Modifier.size(side).background(when { blocked -> block; inSelected -> c.accent.copy(alpha = 0.22f).compositeOver(paper); else -> paper })
            .border(0.5.dp, if (blocked) block else c.outline)
            .then(if (cell == selected.cells.first()) Modifier.border(2.dp, c.accent) else Modifier)
            .then(if (!blocked) Modifier.clickable(role = Role.Button) { vm.tapCell(cell) }
                .semantics(mergeDescendants = true) {
                    contentDescription = say("Row ${cell / puzzle.size + 1}, column ${cell % puzzle.size + 1}, " +
                        (if (letter == '_') "empty" else letter.toString()) + (number?.let { ", clue $it" } ?: "") + if (inSelected) ", selected answer" else "")
                } else Modifier)) {
            if (!blocked) {
                if (number != null) Text(number.toString(), Modifier.align(Alignment.TopStart).padding(start = 1.dp),
                    fontSize = (side.value * 0.23f).coerceIn(5.5f, 10f).sp, color = c.muted)
                if (letter != '_') Text(letter.toString(), Modifier.align(Alignment.Center), fontSize = (side.value * 0.5f).sp, fontWeight = FontWeight.SemiBold,
                    color = if (look.dark) c.text else Color(0xFF1E1E22))
            }
        }
    }
    // Answer slots fit even the longest word instead of overflowing a fixed 30dp row.
    Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.accent), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${puzzle.number(selected)} ${puzzle.direction(selected)} · ${selected.answer.length} letters",
                style = MaterialTheme.typography.labelLarge, color = c.accent)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val slot = min(36.dp, (maxWidth - 3.dp * (selected.cells.size - 1)) / selected.cells.size)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    selected.cells.forEach { cell ->
                        val ch = progress.current[cell]
                        Box(Modifier.size(slot).background(c.surfaceAlt, MaterialTheme.shapes.extraSmall)
                            .border(1.dp, c.outline, MaterialTheme.shapes.extraSmall), contentAlignment = Alignment.Center) {
                            Text(if (ch == '_') "" else ch.toString(), fontWeight = FontWeight.Bold, fontSize = (slot.value * 0.48f).sp)
                        }
                    }
                }
            }
            if (!progress.complete) {
                var draft by rememberSaveable(puzzle.letters, state.selectedEntry) { mutableStateOf("") }
                fun submit() { if (draft.length == selected.answer.length) { vm.answer(draft); draft = "" } }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        onValueChange = { draft = it.uppercase(Locale.ROOT).filter { ch -> ch in 'A'..'Z' }.take(selected.answer.length) },
                        label = { Text("Type the answer") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    Button(enabled = draft.length == selected.answer.length, onClick = ::submit,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Enter") }
                }
            }
            MessageLine(state.message)
        }
    }
    var clueDirection by rememberSaveable(puzzle.letters) { mutableStateOf(puzzle.direction(selected)) }
    LaunchedEffect(state.selectedEntry) { clueDirection = puzzle.direction(selected) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Across", "Down").forEach { direction ->
            FilterChip(selected = clueDirection == direction, onClick = { clueDirection = direction }, label = { Text(direction) })
        }
    }
    val direction = clueDirection
    Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outline)) {
        Column {
            puzzle.entries.withIndex().filter { puzzle.direction(it.value) == direction }.sortedBy { puzzle.number(it.value) }.forEach { (index, entry) ->
                val done = entry.cells.all { progress.current[it] == puzzle.letters[it] }
                val active = index == state.selectedEntry
                Row(Modifier.fillMaxWidth().background(if (active) c.accentSoft else Color.Transparent)
                    .clickable { vm.selectEntry(index) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(puzzle.number(entry).toString(), Modifier.width(24.dp), fontWeight = FontWeight.Bold, color = c.accent)
                    Text("${entry.clue} (${entry.answer.length})", Modifier.weight(1f), color = if (done) c.muted else c.text,
                        textDecoration = if (done) TextDecoration.LineThrough else null)
                    if (done) Icon(GameIcons.Check, contentDescription = tr("Solved"), tint = c.success, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordSearchPlay(progress: WordProgress, state: WordGameState, vm: WordGameViewModel) {
    val look = LocalGameLook.current
    val c = look.colors
    val puzzle = progress.puzzle
    val order = puzzle.entries.map { it.answer }.sorted()
    val colorOf = progress.found.associateWith { FoundColors[order.indexOf(it) % FoundColors.size] }
    val cellColor = HashMap<Int, Color>()
    progress.found.forEach { word -> (progress.foundPaths[word] ?: puzzle.entries.first { it.answer == word }.cells).forEach { cellColor.putIfAbsent(it, colorOf.getValue(word)) } }
    val paper = if (look.dark) c.surfaceAlt else Color(0xFFFFFEFA)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoChip("${progress.found.size} / ${puzzle.entries.size} found")
        if (progress.hints > 0) InfoChip("${progress.hints} hints", icon = GameIcons.Hint)
    }
    MessageLine(state.message ?: if (state.startCell != null) "Now tap the last letter." else "Tap the first letter of a word, then its last letter.")
    LetterGrid(puzzle.size) { cell, side ->
        val mark = cellColor[cell]
        val start = state.startCell == cell
        Box(Modifier.size(side).background(paper).clickable(role = Role.Button) { vm.tapCell(cell) }
            .semantics(mergeDescendants = true) {
                contentDescription = say("Row ${cell / puzzle.size + 1}, column ${cell % puzzle.size + 1}, ${puzzle.letters[cell]}" +
                    (if (start) ", selected start" else "") + if (mark != null) ", found word" else "")
            }, contentAlignment = Alignment.Center) {
            if (mark != null) Box(Modifier.size(side * 0.86f).background(mark.copy(alpha = if (look.dark) 0.55f else 0.7f), MaterialTheme.shapes.small))
            if (start) Box(Modifier.size(side * 0.86f).border(3.dp, c.accent, MaterialTheme.shapes.small))
            Text(puzzle.letters[cell].toString(), fontSize = (side.value * 0.48f).sp, fontWeight = FontWeight.SemiBold,
                color = if (look.dark) c.text else Color(0xFF1E1E22))
        }
    }
    SectionTitle("Words")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        order.forEach { word ->
            val found = word in progress.found
            Surface(color = if (found) colorOf.getValue(word).copy(alpha = 0.35f) else c.surface, shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, if (found) colorOf.getValue(word) else c.outline)) {
                Text(word, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.SemiBold,
                    textDecoration = if (found) TextDecoration.LineThrough else null, color = if (found) c.muted else c.text)
            }
        }
    }
}
