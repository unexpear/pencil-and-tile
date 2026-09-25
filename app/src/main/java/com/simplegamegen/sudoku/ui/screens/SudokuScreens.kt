package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.grids.GridStyle
import com.simplegamegen.sudoku.ui.i18n.LocalCatalog
import com.simplegamegen.sudoku.ui.i18n.tr
import com.simplegamegen.sudoku.ui.playerRecords
import com.simplegamegen.sudoku.ui.PlayModeChoice
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.ReportPlay
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.data.Outcome
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simplegamegen.sudoku.model.ArrowShaft
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.KropkiDot
import com.simplegamegen.sudoku.model.SandwichClues
import com.simplegamegen.sudoku.model.VariantType
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.GameViewModel
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.HeaderAction
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.MenuAction
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.SectionTitle
import com.simplegamegen.sudoku.ui.components.ToolBar
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.min
import kotlin.math.sqrt
import com.simplegamegen.sudoku.ui.i18n.say

fun variantLabel(variant: VariantType): String = when (variant) {
    VariantType.CLASSIC -> "Classic"
    VariantType.DIAGONAL_X -> "Diagonal X"
    VariantType.JIGSAW -> "Jigsaw"
    VariantType.KILLER -> "Killer"
    VariantType.THERMO -> "Thermo"
    VariantType.KROPKI -> "Kropki"
    VariantType.ARROW -> "Arrow"
    VariantType.SANDWICH -> "Sandwich"
}

fun variantHint(variant: VariantType): String = when (variant) {
    VariantType.CLASSIC -> "Every row, column and box holds each digit once."
    VariantType.DIAGONAL_X -> "Both main diagonals must also hold each digit once."
    VariantType.JIGSAW -> "Boxes are replaced by irregular regions with thick borders."
    VariantType.KILLER -> "Each outlined cage sums to its corner number, with no repeats."
    VariantType.THERMO -> "Digits strictly increase along each thermometer from its bulb."
    VariantType.KROPKI -> "White dots join consecutive digits; black dots join a 1:2 pair."
    VariantType.ARROW -> "Digits along each arrow add up to its circled digit."
    VariantType.SANDWICH -> "Outside clues give the sum between the 1 and the largest digit."
}

fun difficultyLabel(d: Difficulty) = d.name.lowercase().replaceFirstChar { it.uppercase() }

fun formatTime(totalSeconds: Long): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

/** Sudoku home: continue the saved game or configure a new one. */
@Composable
fun SudokuHubScreen(nav: NavController, vm: GameViewModel, initialVariant: VariantType? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val look = LocalGameLook.current
    var size by rememberSaveable { mutableIntStateOf(9) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.EASY) }
    var variant by rememberSaveable { mutableStateOf(initialVariant ?: VariantType.CLASSIC) }
    val killer = initialVariant == VariantType.KILLER
    GameScaffold(
        title = if (killer) "Killer Sudoku" else "Sudoku", game = if (killer) GameId.KILLER else GameId.SUDOKU, tutorial = if (killer) GameId.KILLER else GameId.SUDOKU,
        subtitle = if (killer) "Cages add up to their corner numbers" else "8 variants · 4×4 to 16×16 · custom grids",
        onBack = { nav.popBackStack() },
        actions = { HeaderAction(GameIcons.Stats, "Statistics") { nav.navigate("stats") } },
    ) {
        if (state.hasSave || (state.hasGame && !state.won)) {
            Surface(onClick = { vm.loadGame(); nav.navigate("game") }, color = look.colors.accent, contentColor = look.colors.onAccent,
                shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Continue your game", style = MaterialTheme.typography.titleMedium)
                        if (state.hasGame) Text("${variantLabel(state.variant)} ${state.size}×${state.size} · ${difficultyLabel(state.difficulty)} · ${formatTime(state.elapsedSeconds.toLong())}",
                            style = MaterialTheme.typography.bodyMedium)
                        else Text("Pick up where you left off", style = MaterialTheme.typography.bodyMedium)
                    }
                    androidx.compose.material3.Icon(GameIcons.Chevron, contentDescription = null)
                }
            }
        }
        val gridSession = playerRecords()?.sessions?.get(GRID_RECORD)
        if (gridSession != null) {
            Surface(onClick = { nav.navigate(GRID_ROUTE) }, color = look.colors.surface, border = BorderStroke(1.dp, look.colors.accent),
                shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Continue your big grid", style = MaterialTheme.typography.titleMedium)
                        Text(gridSession.level, style = MaterialTheme.typography.bodyMedium, color = look.colors.muted)
                    }
                    androidx.compose.material3.Icon(GameIcons.Chevron, contentDescription = null)
                }
            }
        }
        SectionTitle("New puzzle")
        // 16×16 grids play on the grid engine, which covers Classic, Diagonal X, Jigsaw and Killer at that size.
        val gridStyle = when (variant) {
            VariantType.CLASSIC -> GridStyle.CLASSIC; VariantType.DIAGONAL_X -> GridStyle.DIAGONAL
            VariantType.JIGSAW -> GridStyle.JIGSAW; VariantType.KILLER -> GridStyle.KILLER; else -> null
        }
        OptionGroup("Variant", VariantType.entries, variant, ::variantLabel) {
            variant = it
            if (it == VariantType.KILLER && size != 16) size = 9
            if (size == 16 && it !in listOf(VariantType.CLASSIC, VariantType.DIAGONAL_X, VariantType.JIGSAW, VariantType.KILLER)) size = 9
        }
        Text(variantHint(variant), style = MaterialTheme.typography.bodyMedium, color = look.colors.muted)
        OptionGroup("Size", listOf(9, 6, 4, 16), size, { "$it×$it" },
            enabled = { (variant != VariantType.KILLER || it == 9 || it == 16) && (it != 16 || gridStyle != null) }) { size = it }
        if (size == 16) Text("16×16 uses digits 1 to 16 in 4×4 boxes.", style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
        OptionGroup("Difficulty", Difficulty.entries, difficulty, ::difficultyLabel) { difficulty = it }
        if (size == 16) PlayModeChoice(GameId.SUDOKU, record = GRID_RECORD)
        else PlayModeChoice(if (variant == VariantType.KILLER) GameId.KILLER else GameId.SUDOKU)
        Button(onClick = {
            if (size == 16) nav.navigate(gridStartRoute(16, gridStyle ?: GridStyle.CLASSIC, difficulty.ordinal))
            else { vm.newGame(size, difficulty, variant); nav.navigate("game") }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start puzzle") }
        SectionTitle("Your own grids")
        Text("Draw any grid from 4×4 to 16×16 with your own regions, cages, odd/even squares, extra groups and rules. " +
            "Every grid is checked for exactly one answer before you play.", style = MaterialTheme.typography.bodyMedium, color = look.colors.muted)
        OutlinedButton(onClick = { nav.navigate("grids") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            androidx.compose.material3.Icon(GameIcons.Grid, null); Text("  Custom grid editor")
        }
    }
}

@Composable
fun GameScreen(nav: NavController, vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val look = LocalGameLook.current
    LifecycleStartEffect(vm) {
        vm.setPlaying(true)
        onStopOrDispose { vm.setPlaying(false) }
    }
    LaunchedEffect(vm) {
        if (!vm.state.value.hasGame && !vm.state.value.generating) vm.loadGame()
    }
    val snackbar = remember { SnackbarHostState() }
    val catalog = LocalCatalog.current
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(catalog.tr(it)); vm.consumeMessage() }
    }
    val playable = state.hasGame && !state.generating && !state.won
    val sudokuId = if (state.variant == VariantType.KILLER) GameId.KILLER else GameId.SUDOKU
    ReportPlay(sudokuId, key = if (state.hasGame && !state.generating) "${state.variant}:${state.size}:${state.seed}" else null,
        level = "${variantLabel(state.variant)} ${state.size}×${state.size} · ${difficultyLabel(state.difficulty)}",
        levelIndex = state.difficulty.ordinal, inProgress = playable, outcome = if (state.won && !state.generating) Outcome(Result.WON) else null,
        clock = state.elapsedSeconds.toLong())
    val timed = playerRecords()?.sessions?.get(sudokuId.name)?.timed ?: playerRecords()?.timedFor(sudokuId.name) ?: true
    GameScaffold(showClock = false,
        title = "Sudoku",
        subtitle = if (state.hasGame) "${variantLabel(state.variant)} ${state.size}×${state.size} · ${difficultyLabel(state.difficulty)}" else null,
        game = GameId.SUDOKU, tutorial = if (state.variant == VariantType.KILLER) GameId.KILLER else GameId.SUDOKU, onBack = { nav.popBackStack() }, busy = state.generating || state.hintBusy, snackbar = snackbar,
        menu = listOf(MenuAction("New puzzle", GameIcons.Plus) { nav.navigate("sudoku") { popUpTo("home") } },
            MenuAction("Statistics", GameIcons.Stats) { nav.navigate("stats") }),
        bottomBar = {
            ToolBar {
                ToolButton(GameIcons.Undo, "Undo", enabled = state.canUndo && !state.generating, onClick = vm::undo)
                ToolButton(GameIcons.Eraser, "Erase", enabled = playable && state.selected != null, onClick = vm::erase)
                ToolButton(GameIcons.Pencil, if (state.notesMode) "Notes on" else "Notes", enabled = playable, active = state.notesMode, onClick = vm::toggleNotesMode)
                HintButton(sudokuId, enabled = playable && !state.hintBusy, onClick = vm::requestHint)
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (timed && playerRecords()?.settings?.showTimer != false) InfoChip(formatTime(state.elapsedSeconds.toLong()), icon = GameIcons.Timer)
            if (state.hintsUsed > 0) InfoChip((if (state.hintsUsed == 1) "1 hint" else "${state.hintsUsed} hints"), icon = GameIcons.Hint)
            if (state.conflicts.isNotEmpty()) InfoChip("${state.conflicts.size} conflicts", emphasized = true)
        }
        when {
            state.generating -> Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GameArt(GameId.SUDOKU, Modifier.size(72.dp))
                    Text("Building a puzzle with one solution…", color = look.colors.muted)
                }
            }
            state.hasGame -> ZoomBox(Modifier.fillMaxWidth()) {
                SudokuBoard(
                    size = state.size, current = state.current, fixed = state.fixed, notes = state.notes,
                    regions = state.regions, cageIndex = state.cageIndex, cageSums = state.cageSums, thermos = state.thermos,
                    dots = state.dots, arrows = state.arrows, sandwich = state.sandwich,
                    highlightDiagonals = state.variant == VariantType.DIAGONAL_X,
                    selected = state.selected, conflicts = state.conflicts, onSelect = vm::selectCell,
                )
            }
            else -> Text("No saved game. Start a new puzzle from the Sudoku screen.", color = look.colors.muted)
        }
        if (playable) NumberPad(size = state.size, current = state.current, notesMode = state.notesMode, onNumber = vm::inputNumber)
    }
    if (state.won && !state.generating) {
        AlertDialog(
            onDismissRequest = {},
            icon = { GameArt(GameId.SUDOKU, Modifier.size(56.dp)) },
            title = { Text("Solved!") },
            text = {
                Text("${variantLabel(state.variant)} ${state.size}×${state.size} · ${difficultyLabel(state.difficulty)}\n" +
                    "Time ${formatTime(state.elapsedSeconds.toLong())} · " + (if (state.hintsUsed == 1) "1 hint" else "${state.hintsUsed} hints"))
            },
            confirmButton = { Button(onClick = { nav.navigate("sudoku") { popUpTo("home") } }) { Text("New puzzle") } },
            dismissButton = { OutlinedButton(onClick = { nav.popBackStack("home", false) }) { Text("Home") } },
            containerColor = look.colors.surface,
        )
    }
}

@Composable
fun SudokuBoard(
    size: Int,
    current: List<Int>,
    fixed: List<Boolean>,
    notes: List<Set<Int>>,
    regions: List<Int>?,
    cageIndex: List<Int>?,
    cageSums: List<Int>?,
    thermos: List<List<Int>>?,
    dots: List<KropkiDot>?,
    arrows: List<ArrowShaft>?,
    sandwich: SandwichClues?,
    highlightDiagonals: Boolean,
    selected: Int?,
    conflicts: Set<Int>,
    onSelect: (Int) -> Unit,
) {
    val look = LocalGameLook.current
    val p = look.colors
    val paper = if (look.dark) p.surfaceAlt else Color(0xFFFFFEFA).compositeOver(p.surface)
    val gridInk = if (look.dark) p.text.copy(alpha = 0.85f) else Color(0xFF2A2A2E)
    val thin = p.outline
    val overlayInk = p.muted.copy(alpha = 0.45f)
    val boxRows = if (size == 9) 3 else 2
    val boxCols = if (size == 9) 3 else if (size == 6) 3 else 2
    fun box(r: Int, c: Int): Int = regions?.getOrNull(r * size + c) ?: ((r / boxRows) * (size / boxCols) + (c / boxCols))
    fun cage(r: Int, c: Int): Int? = if (r in 0 until size && c in 0 until size) cageIndex?.getOrNull(r * size + c) else null
    val selectedValue = selected?.let { current.getOrNull(it) } ?: 0
    val cageFirstCell = remember(cageIndex) {
        val first = mutableMapOf<Int, Int>()
        cageIndex?.forEachIndexed { i, id -> first.putIfAbsent(id, i) }
        first.entries.associate { (id, cell) -> cell to id }
    }
    fun linksOf(paths: List<List<Int>>?): Map<Int, List<Int>> {
        val map = mutableMapOf<Int, MutableList<Int>>()
        paths?.forEach { path ->
            path.forEachIndexed { k, cell ->
                if (k > 0) map.getOrPut(cell) { mutableListOf() }.add(path[k - 1])
                if (k < path.size - 1) map.getOrPut(cell) { mutableListOf() }.add(path[k + 1])
            }
        }
        return map
    }
    val thermoLinks = remember(thermos) { linksOf(thermos) }
    val thermoBulbs = remember(thermos) { thermos?.map { it.first() }?.toSet() ?: emptySet() }
    val arrowLinks = remember(arrows) { linksOf(arrows?.map { listOf(it.circle) + it.shaft }) }
    val arrowCircles = remember(arrows) { arrows?.map { it.circle }?.toSet() ?: emptySet() }
    val kropkiAt = remember(dots) {
        val map = mutableMapOf<Int, MutableList<Pair<Int, Boolean>>>()
        dots?.forEach { d ->
            val (from, to) = if (d.a < d.b) d.a to d.b else d.b to d.a
            map.getOrPut(from) { mutableListOf() }.add(to to d.black)
        }
        map
    }

    @Composable
    fun CellBox(r: Int, c: Int, modifier: Modifier) {
        val i = r * size + c
        val v = current.getOrElse(i) { 0 }
        val isFixed = fixed.getOrElse(i) { false }
        val onDiag = r == c || r + c == size - 1
        val sr = selected?.div(size); val sc = selected?.rem(size)
        val peer = selected != null && selected != i && (sr == r || sc == c || box(sr!!, sc!!) == box(r, c))
        val bg = when {
            conflicts.contains(i) -> p.danger.copy(alpha = 0.22f).compositeOver(paper)
            selected == i -> p.accent.copy(alpha = 0.34f).compositeOver(paper)
            selectedValue != 0 && v == selectedValue -> p.highlight.copy(alpha = 0.34f).compositeOver(paper)
            peer -> p.accent.copy(alpha = 0.09f).compositeOver(paper)
            highlightDiagonals && onDiag -> p.highlight.copy(alpha = 0.14f).compositeOver(paper)
            else -> paper
        }
        val thickTop = r == 0 || box(r, c) != box(r - 1, c)
        val thickLeft = c == 0 || box(r, c) != box(r, c - 1)
        val myCage = cage(r, c)
        Box(
            modifier = modifier
                .background(bg)
                .semantics(mergeDescendants = true) {
                    contentDescription = say("Row ${r + 1}, column ${c + 1}")
                    stateDescription = say(buildString {
                        append(if (v == 0) "Empty" else "Digit $v")
                        if (isFixed) append(", given")
                        if (selected == i) append(", selected")
                        if (i in conflicts) append(", conflict")
                        val marks = notes.getOrElse(i) { emptySet() }
                        if (marks.isNotEmpty()) append(", notes ${marks.sorted().joinToString()}")
                        regions?.getOrNull(i)?.let { append(", region ${it + 1}") }
                        cageIndex?.getOrNull(i)?.let { cage -> cageSums?.getOrNull(cage)?.let { append(", cage ${cage + 1}, sum $it") } }
                    })
                }
                .clickable(role = Role.Button, onClickLabel = "Select cell") { onSelect(i) },
            contentAlignment = Alignment.Center,
        ) {
            if (thermoLinks.containsKey(i) || i in thermoBulbs || arrowLinks.containsKey(i) || i in arrowCircles || kropkiAt.containsKey(i)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = this.size.width; val h = this.size.height
                    val cx = w / 2; val cy = h / 2
                    val minD = min(w, h)
                    fun edgeToward(n: Int): Offset {
                        val dx = (n % size - c).toFloat(); val dy = (n / size - r).toFloat()
                        val len = sqrt(dx * dx + dy * dy)
                        return Offset(cx + dx / len * w / 2, cy + dy / len * h / 2)
                    }
                    thermoLinks[i]?.forEach { n -> drawLine(overlayInk, Offset(cx, cy), edgeToward(n), strokeWidth = minD * 0.30f, cap = StrokeCap.Round) }
                    if (i in thermoBulbs) drawCircle(overlayInk, radius = minD * 0.34f, center = Offset(cx, cy))
                    arrowLinks[i]?.forEach { n -> drawLine(overlayInk, Offset(cx, cy), edgeToward(n), strokeWidth = minD * 0.12f, cap = StrokeCap.Round) }
                    if (i in arrowCircles) drawCircle(overlayInk, radius = minD * 0.40f, center = Offset(cx, cy), style = Stroke(width = minD * 0.07f))
                    kropkiAt[i]?.forEach { (other, black) ->
                        val at = edgeToward(other)
                        drawCircle(if (black) gridInk else paper, radius = minD * 0.16f, center = at)
                        drawCircle(gridInk, radius = minD * 0.16f, center = at, style = Stroke(width = minD * 0.04f))
                    }
                }
            }
            if (myCage != null) {
                // Killer cages: dashed outline just inside each cage edge, as printed puzzles show them.
                Canvas(Modifier.fillMaxSize()) {
                    val inset = this.size.minDimension * 0.1f
                    val w = this.size.width; val h = this.size.height
                    val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(w * 0.09f, w * 0.07f))
                    val stroke = this.size.minDimension * 0.03f
                    val ink = gridInk.copy(alpha = 0.7f)
                    val top = cage(r - 1, c) != myCage; val bottom = cage(r + 1, c) != myCage
                    val left = cage(r, c - 1) != myCage; val right = cage(r, c + 1) != myCage
                    val x0 = if (left) inset else 0f; val x1 = if (right) w - inset else w
                    val y0 = if (top) inset else 0f; val y1 = if (bottom) h - inset else h
                    if (top) drawLine(ink, Offset(x0, inset), Offset(x1, inset), stroke, pathEffect = dash)
                    if (bottom) drawLine(ink, Offset(x0, h - inset), Offset(x1, h - inset), stroke, pathEffect = dash)
                    if (left) drawLine(ink, Offset(inset, y0), Offset(inset, y1), stroke, pathEffect = dash)
                    if (right) drawLine(ink, Offset(w - inset, y0), Offset(w - inset, y1), stroke, pathEffect = dash)
                }
            }
            if (v != 0) {
                Text(
                    text = v.toString(), fontSize = (if (size == 9) 22 else 28).sp,
                    fontWeight = if (isFixed) FontWeight.Bold else FontWeight.Medium,
                    color = when { i in conflicts -> p.danger; isFixed -> gridInk; else -> p.accent },
                )
            } else {
                val marks = notes.getOrElse(i) { emptySet() }
                if (marks.isNotEmpty()) NotesGrid(marks, size, p.muted)
            }
            cageFirstCell[i]?.let { cageId ->
                cageSums?.getOrNull(cageId)?.let { sum ->
                    Text(sum.toString(), fontSize = 9.sp, color = p.muted, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 1.dp, top = 1.dp).background(bg).padding(horizontal = 1.dp))
                }
            }
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(if (thickTop) 2.dp else 0.5.dp).background(if (thickTop) gridInk else thin))
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(if (thickLeft) 2.dp else 0.5.dp).background(if (thickLeft) gridInk else thin))
            if (r == size - 1) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(gridInk))
            if (c == size - 1) Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(2.dp).background(gridInk))
        }
    }

    @Composable
    fun ClueCell(value: Int, modifier: Modifier) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (value != 0) Text(text = value.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.muted)
        }
    }

    val shape = MaterialTheme.shapes.small
    val frame = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).border(BorderStroke(2.dp, gridInk), shape)
    if (sandwich != null) {
        Column(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight())
                for (c in 0 until size) ClueCell(sandwich.top[c], Modifier.weight(1f).fillMaxHeight())
                Box(modifier = Modifier.weight(1f).fillMaxHeight())
            }
            for (r in 0 until size) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ClueCell(sandwich.left[r], Modifier.weight(1f).fillMaxHeight())
                    for (c in 0 until size) CellBox(r, c, Modifier.weight(1f).fillMaxHeight())
                    ClueCell(sandwich.right[r], Modifier.weight(1f).fillMaxHeight())
                }
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight())
                for (c in 0 until size) ClueCell(sandwich.bottom[c], Modifier.weight(1f).fillMaxHeight())
                Box(modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    } else {
        Column(modifier = frame) {
            for (r in 0 until size) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until size) CellBox(r, c, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
fun NotesGrid(marks: Set<Int>, size: Int, color: Color) {
    val cols = kotlin.math.ceil(kotlin.math.sqrt(size.toDouble())).toInt()
    val rows = (size + cols - 1) / cols
    Column(modifier = Modifier.fillMaxSize().padding(1.dp)) {
        for (r in 0 until rows) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until cols) {
                    val n = r * cols + c + 1
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (n <= size && n in marks) Text(text = n.toString(), fontSize = 9.sp, color = color, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** Digit keys showing how many of each digit are still to place. */
@Composable
fun NumberPad(size: Int, current: List<Int>, notesMode: Boolean, onNumber: (Int) -> Unit) {
    val look = LocalGameLook.current
    val counts = remember(current) { current.groupingBy { it }.eachCount() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in (1..size).chunked(if (size == 9) 5 else size)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in row) {
                    val left = size - (counts[n] ?: 0)
                    val done = left <= 0
                    Surface(
                        onClick = { onNumber(n) }, modifier = Modifier.weight(1f).height(60.dp)
                            .semantics { contentDescription = say("Digit $n, $left left") },
                        shape = MaterialTheme.shapes.medium,
                        color = if (notesMode) look.colors.surface else if (done) look.colors.surfaceAlt else look.colors.accentSoft,
                        border = BorderStroke(1.dp, if (notesMode) look.colors.accent else look.colors.outline),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(n.toString(), fontSize = if (notesMode) 18.sp else 24.sp, fontWeight = FontWeight.SemiBold,
                                color = if (done) look.colors.muted else look.colors.accent)
                            Text(if (done) "done" else "$left", fontSize = 10.sp, color = look.colors.muted)
                        }
                    }
                }
                if (row.size < 5 && size == 9) repeat(5 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun StatsScreen(nav: NavController, vm: GameViewModel) {
    val rows by vm.statRows.collectAsStateWithLifecycle(initialValue = emptyList())
    val look = LocalGameLook.current
    GameScaffold(title = "Sudoku statistics", game = GameId.SUDOKU, onBack = { nav.popBackStack() }) {
        if (rows.isEmpty()) {
            Text("Finish a puzzle to see wins and best times here.", color = look.colors.muted)
        } else {
            val played = rows.sumOf { it.played }; val won = rows.sumOf { it.won }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Played", played.toString(), Modifier.weight(1f))
                StatTile("Solved", won.toString(), Modifier.weight(1f))
                StatTile("Rate", if (played <= 0) "–" else "${won * 100 / played}%", Modifier.weight(1f))
            }
            rows.forEach { row ->
                Surface(color = look.colors.surface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, look.colors.outline)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${variantLabel(row.variant)} ${row.size}×${row.size}", style = MaterialTheme.typography.titleSmall)
                            Text(difficultyLabel(row.difficulty), style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${row.won} / ${row.played} solved", style = MaterialTheme.typography.bodyMedium)
                            row.bestTimeSec?.let { Text("Best ${formatTime(it)}", style = MaterialTheme.typography.bodySmall, color = look.colors.accent) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    val look = LocalGameLook.current
    Surface(modifier, color = look.colors.surfaceAlt, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = look.colors.muted)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
