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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simplegamegen.sudoku.tabletop.CheckersState
import com.simplegamegen.sudoku.tabletop.DominoState
import com.simplegamegen.sudoku.tabletop.GameGuide
import com.simplegamegen.sudoku.tabletop.MinesState
import com.simplegamegen.sudoku.tabletop.Move
import com.simplegamegen.sudoku.tabletop.Op
import com.simplegamegen.sudoku.tabletop.ReversiState
import com.simplegamegen.sudoku.tabletop.SolitaireState
import com.simplegamegen.sudoku.tabletop.TableGame
import com.simplegamegen.sudoku.tabletop.Result as GameResult
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.TableViewModel
import com.simplegamegen.sudoku.ui.assets.CardSlot
import com.simplegamegen.sudoku.ui.assets.CheckerPiece
import com.simplegamegen.sudoku.ui.assets.DominoTile
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.PlayingCard
import com.simplegamegen.sudoku.ui.assets.drawDisc
import com.simplegamegen.sudoku.ui.assets.drawFlag
import com.simplegamegen.sudoku.ui.assets.drawMine
import com.simplegamegen.sudoku.ui.assets.mineNumberColor
import com.simplegamegen.sudoku.ui.components.ConfirmDialog
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.HeaderAction
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.MenuAction
import com.simplegamegen.sudoku.ui.components.MessageLine
import com.simplegamegen.sudoku.ui.components.NewGameSheet
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.StartCard
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ToolBar
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.describeMove
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.i18n.say

@Composable
fun TableScreen(nav: NavController, vm: TableViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val look = LocalGameLook.current
    val id = GameId.of(vm.game)
    val match = s.match
    var sheet by rememberSaveable { mutableStateOf(false) }
    var rules by rememberSaveable { mutableStateOf(false) }
    var setting by rememberSaveable { mutableIntStateOf(0) }
    var confirm by rememberSaveable { mutableStateOf("") }
    var flagMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(match?.seed) { match?.let { setting = it.setting } }
    val state = match?.state
    val playing = state?.result == GameResult.PLAYING
    val canAct = match != null && !s.busy
    fun start() { if (match != null && playing) confirm = "new" else { sheet = false; vm.newGame(setting) } }
    ReportPlay(id, key = match?.seed?.toString(), level = match?.let { vm.game.settings[it.setting] } ?: "", levelIndex = match?.setting ?: 0,
        inProgress = playing && match?.moves?.isNotEmpty() == true,
        outcome = when (state?.result) {
            GameResult.WON -> Outcome(Result.WON); GameResult.LOST -> Outcome(Result.LOST); GameResult.DRAW -> Outcome(Result.DRAW); else -> null
        })
    GameScaffold(
        title = vm.game.title, game = id, tutorial = id, onBack = { nav.popBackStack() },
        subtitle = match?.let { GameGuide.settingDescription(vm.game, it.setting) },
        busy = s.busy || s.thinking,
        actions = { HeaderAction(GameIcons.Rules, "Rules") { rules = true } },
        menu = if (match == null) emptyList() else listOf(
            MenuAction("New game", GameIcons.Plus, enabled = !s.busy) { sheet = true },
            MenuAction("Restart", GameIcons.Restart, enabled = match.moves.isNotEmpty()) { confirm = "restart" },
            MenuAction("Save now", GameIcons.Save) { vm.save(true) },
        ),
        bottomBar = if (match == null) null else {
            {
                ToolBar {
                    if (state is MinesState) {
                        ToolButton(GameIcons.Search, "Reveal", active = !flagMode) { flagMode = false }
                        ToolButton(GameIcons.Flag, "Flag", active = flagMode) { flagMode = true }
                    }
                    ToolButton(GameIcons.Undo, "Undo", enabled = canAct && match.moves.isNotEmpty(), onClick = vm::undo)
                    HintButton(id, enabled = canAct && playing && state?.turn == 1 && !s.thinking, onClick = vm::hint)
                    if (state !is MinesState) ToolButton(GameIcons.Restart, "Restart", enabled = canAct && match.moves.isNotEmpty()) { confirm = "restart" }
                    ToolButton(GameIcons.Plus, "New", enabled = !s.busy) { sheet = true }
                }
            }
        },
    ) {
        when {
            match == null && s.busy -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            match == null -> StartCard(vm.game.title, GameGuide.rules(vm.game), art = { GameArt(id, Modifier.size(64.dp)) }, tutorial = id) {
                SettingPicker(vm.game, setting) { setting = it }
                MessageLine(s.message)
                Button(onClick = { vm.newGame(setting) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start game") }
            }
            else -> {
                val st = match.state
                StatusRow(vm.game, st, s.thinking)
                if (st.result != GameResult.PLAYING) ResultBanner(vm.game, st.result, onNext = { vm.newGame(match.setting) }, onNew = { sheet = true })
                when (st) {
                    is SolitaireState -> SolitaireBoard(st, s.hint, vm::play)
                    is MinesState -> MinesBoard(st, s.hint, flagMode, vm::play)
                    is CheckersState -> CheckersBoard(st, s.hint, vm::play)
                    is ReversiState -> ReversiBoard(st, s.hint, vm::play)
                    is DominoState -> DominoBoard(st, s.hint, vm::play)
                }
                if (vm.game.opponent && st.turn == -1 && !s.thinking && st.result == GameResult.PLAYING)
                    Button(onClick = vm::resumeComputer, modifier = Modifier.fillMaxWidth()) { Text("Resume computer") }
                MessageLine(s.message ?: s.hint?.let { "Try: ${describeMove(st, it)}" })
            }
        }
    }
    if (sheet) NewGameSheet("New game", game = id, onDismiss = { sheet = false }, onStart = ::start) {
        SettingPicker(vm.game, setting) { setting = it }
        Text(GameGuide.settingDescription(vm.game, setting), style = MaterialTheme.typography.bodyMedium, color = look.colors.muted)
    }
    if (rules) AlertDialog(onDismissRequest = { rules = false }, title = { Text("How to play ${vm.game.title}") },
        text = { Text(GameGuide.rules(vm.game)) }, confirmButton = { TextButton(onClick = { rules = false }) { Text("Got it") } },
        containerColor = look.colors.surface)
    if (confirm.isNotEmpty()) ConfirmDialog(
        title = if (confirm == "restart") "Restart this game?" else "Start a new game?",
        text = if (confirm == "restart") "Your moves will be cleared and the game returns to its starting position." else "This replaces your saved ${vm.game.title} game.",
        confirm = if (confirm == "restart") "Restart" else "New game",
        onConfirm = { if (confirm == "restart") vm.restart() else { sheet = false; vm.newGame(setting) }; confirm = "" },
        onDismiss = { confirm = "" },
    )
}

@Composable
private fun SettingPicker(game: TableGame, setting: Int, onSelect: (Int) -> Unit) {
    val title = when { game == TableGame.SOLITAIRE -> "Draw mode"; game.opponent -> "Computer strength"; else -> "Board size" }
    OptionGroup(title, game.settings.indices.toList(), setting, { game.settings[it] }, onSelect = onSelect)
}

@Composable
private fun StatusRow(game: TableGame, state: com.simplegamegen.sudoku.tabletop.TableState, thinking: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (game.opponent && state.result == GameResult.PLAYING)
            InfoChip(if (state.turn == 1) "Your turn" else if (thinking) "Computer is thinking…" else "Computer's turn", emphasized = state.turn == 1)
        when (state) {
            is SolitaireState -> { InfoChip("${state.foundations.sumOf { it.size }} / 52 home"); InfoChip("${state.stock.size} in stock") }
            is MinesState -> { InfoChip("${state.preset.mines - state.flags.size} mines left", icon = GameIcons.Mine)
                InfoChip("${state.revealed.size} open") }
            is ReversiState -> { InfoChip("You ${state.board.count { it == 1 }}"); InfoChip("Computer ${state.board.count { it == -1 }}") }
            is DominoState -> InfoChip("Boneyard ${state.boneyard.size}")
            is CheckersState -> InfoChip("You ${state.board.count { it > 0 }} · Computer ${state.board.count { it < 0 }}")
        }
    }
}

@Composable
private fun ResultBanner(game: TableGame, result: GameResult, onNext: () -> Unit, onNew: () -> Unit) {
    val look = LocalGameLook.current
    val (title, color) = when (result) {
        GameResult.WON -> (if (game == TableGame.MINES) "Minefield cleared!" else if (game.opponent) "You won!" else "Solved!") to look.colors.success
        GameResult.LOST -> (if (game == TableGame.MINES) "Boom — you hit a mine" else "The computer won") to look.colors.danger
        else -> "It's a draw" to look.colors.accent
    }
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, color), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = color)
                Text(if (game == TableGame.MINES && result == GameResult.LOST) "Undo to step back, or try another board." else "Play again with the same settings?",
                    style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
            }
            OutlinedButton(onClick = onNew) { Text("Change") }
            Button(onClick = onNext) { Text("Next game") }
        }
    }
}

// ---------------- Solitaire ----------------

@Composable
private fun SolitaireBoard(s: SolitaireState, hint: Move?, play: (Move) -> Unit) {
    var sel by remember(s) { mutableStateOf<Pair<Int, Int>?>(null) }
    val legal = remember(s) { s.legalMoves() }
    fun tryMoveTo(to: Int): Boolean {
        val cur = sel ?: return false
        val move = Move(Op.MOVE, cur.first, to, cur.second)
        if (move !in legal) return false
        play(move); sel = null; return true
    }
    fun tapPile(pile: Int, count: Int?) {
        val cur = sel
        if (cur != null && cur.first != pile && tryMoveTo(pile)) return
        if (count == null) { sel = null; return }
        if (cur == pile to count) {
            val home = legal.firstOrNull { it.op == Op.MOVE && it.from == pile && it.count == 1 && it.to >= 8 }
            if (count == 1 && home != null) { play(home); sel = null } else sel = null
            return
        }
        sel = pile to count
    }
    val hintMove = hint?.takeIf { it.op == Op.MOVE }
    TablePanel(padding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 4.dp
                val cw = (maxWidth - gap * 6) / 7
                val ch = cw * 1.4f
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Box(Modifier.size(cw, ch).clickable(enabled = Move(Op.DRAW) in legal) { sel = null; play(Move(Op.DRAW)) }
                            .semantics { contentDescription = say(if (s.stock.isEmpty()) "Recycle waste into stock" else "Stock, ${s.stock.size} cards. Draw ${s.drawCount}") }) {
                            if (s.stock.isNotEmpty()) PlayingCard(0, width = cw, faceUp = false, hinted = hint?.op == Op.DRAW)
                            else CardSlot(width = cw, label = if (s.waste.isNotEmpty()) "↻" else null, highlighted = hint?.op == Op.DRAW)
                        }
                        Box(Modifier.size(cw * 2 + gap, ch)) {
                            val shown = s.waste.takeLast(if (s.drawCount == 3) 3 else 1)
                            if (shown.isEmpty()) CardSlot(width = cw)
                            shown.forEachIndexed { i, card ->
                                val top = i == shown.lastIndex
                                PlayingCard(card, Modifier.offset(x = cw * 0.45f * i).then(if (top) Modifier.clickable { tapPile(7, 1) }
                                    .semantics { contentDescription = say("Waste, ${SolitaireState.label(card)}") } else Modifier),
                                    width = cw, selected = top && sel?.first == 7, hinted = top && hintMove?.from == 7)
                            }
                        }
                        repeat(4) { i ->
                            val pile = s.foundations[i]
                            Box(Modifier.clickable { tapPile(8 + i, if (pile.isEmpty()) null else 1) }
                                .semantics { contentDescription = say("Foundation ${i + 1}, ${pile.lastOrNull()?.let(SolitaireState::label) ?: "empty"}") }) {
                                if (pile.isEmpty()) CardSlot(width = cw, suit = i, highlighted = hintMove?.to == 8 + i)
                                else PlayingCard(pile.last(), width = cw, selected = sel?.first == 8 + i, hinted = hintMove?.to == 8 + i || hintMove?.from == 8 + i)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(7) { col ->
                            val pile = s.columns[col]
                            val hidden = s.hidden[col]
                            val down = cw * 0.2f; val up = cw * 0.46f
                            val tops = pile.indices.map { i -> down * minOf(i, hidden) + up * maxOf(0, i - hidden) }
                            val height = (tops.lastOrNull() ?: 0.dp) + ch
                            Box(Modifier.width(cw).height(maxOf(height, ch)).clickable { tapPile(col, null) }
                                .semantics { contentDescription = say("Column ${col + 1}, ${if (pile.isEmpty()) "empty" else "${pile.size - hidden} face up, $hidden face down"}") }) {
                                if (pile.isEmpty()) CardSlot(width = cw, label = "K", highlighted = hintMove?.to == col)
                                pile.forEachIndexed { i, card ->
                                    val faceUp = i >= hidden
                                    val count = pile.size - i
                                    val chosen = sel?.let { it.first == col && count <= it.second } == true
                                    val hinted = (hintMove != null && hintMove.from == col && count <= hintMove.count) || (hintMove?.to == col && i == pile.lastIndex)
                                    PlayingCard(card, Modifier.offset(y = tops[i]).clickable { tapPile(col, if (faceUp) count else null) }
                                        .semantics { contentDescription = say(if (faceUp) "Column ${col + 1}, ${SolitaireState.label(card)}" else "Face-down card") },
                                        width = cw, faceUp = faceUp, selected = chosen, hinted = hinted)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(if (sel == null) "Tap a card, then tap where it goes. Tap a selected card again to send it home."
            else "${SolitaireState.label(s.pile(sel!!.first).takeLast(sel!!.second).first())} selected — tap a column or foundation.",
            style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- Minesweeper ----------------

@Composable
private fun MinesBoard(s: MinesState, hint: Move?, flagMode: Boolean, play: (Move) -> Unit) {
    val look = LocalGameLook.current
    val c = look.colors
    ZoomBox(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fit = maxWidth / s.preset.width
            val cell = if (fit >= 34.dp) min(fit, 46.dp) else 40.dp
            val scroll = rememberScrollState()
            Box(Modifier.fillMaxWidth().then(if (fit < 34.dp) Modifier.horizontalScroll(scroll) else Modifier), contentAlignment = Alignment.Center) {
                Column(Modifier.clip(MaterialTheme.shapes.small).border(2.dp, c.outline, MaterialTheme.shapes.small)) {
                    for (row in 0 until s.preset.height) Row {
                        for (col in 0 until s.preset.width) {
                            val i = row * s.preset.width + col
                            val open = i in s.revealed
                            val showMine = s.result == GameResult.LOST && i in s.mines
                            val hinted = hint?.from == i
                            Box(Modifier.size(cell).clickable(enabled = s.result == GameResult.PLAYING) {
                                play(Move(if (flagMode) Op.FLAG else if (open) Op.CHORD else Op.REVEAL, i))
                            }.semantics {
                                contentDescription = say("Row ${row + 1}, column ${col + 1}")
                                stateDescription = say(when { open -> if (s.number(i) == 0) "open, blank" else "open, ${s.number(i)}"
                                    i in s.flags -> "flagged"; else -> "covered" } + if (hinted) ", hint" else "")
                            }) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val w = size.width
                                    if (open || showMine) {
                                        drawRect(if (i == s.exploded) c.danger else c.surface)
                                        drawRect(c.outline, style = Stroke(1f))
                                    } else {
                                        drawRect(c.surfaceAlt)
                                        val e = w * 0.09f
                                        drawRect(Color.White.copy(alpha = if (look.dark) 0.12f else 0.75f), size = size.copy(height = e))
                                        drawRect(Color.White.copy(alpha = if (look.dark) 0.12f else 0.75f), size = size.copy(width = e))
                                        drawRect(Color.Black.copy(alpha = 0.22f), topLeft = Offset(0f, size.height - e), size = size.copy(height = e))
                                        drawRect(Color.Black.copy(alpha = 0.22f), topLeft = Offset(w - e, 0f), size = size.copy(width = e))
                                    }
                                    if (hinted) drawRect(c.highlight, style = Stroke(w * 0.12f))
                                }
                                when {
                                    showMine -> Canvas(Modifier.fillMaxSize().padding(cell * 0.14f)) { drawMine() }
                                    i in s.flags -> Canvas(Modifier.fillMaxSize().padding(cell * 0.12f)) { drawFlag() }
                                    open && s.number(i) > 0 -> Text(s.number(i).toString(), Modifier.align(Alignment.Center),
                                        color = mineNumberColor(s.number(i), look.dark), fontWeight = FontWeight.Black, fontSize = (cell.value * 0.5f).sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Text(if (flagMode) "Flag mode: tap to place or remove a flag." else "Reveal mode: tap to open. Tap a number to open its unflagged neighbors.",
        style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
}

// ---------------- Checkers ----------------

@Composable
private fun CheckersBoard(s: CheckersState, hint: Move?, play: (Move) -> Unit) {
    val look = LocalGameLook.current
    val c = look.colors
    var selected by remember(s) { mutableStateOf(s.forced) }
    val legal = remember(s) { s.legalMoves() }
    if (s.forced != null) InfoChip("Keep jumping with the same piece", emphasized = true)
    ZoomBox(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = maxWidth / 8
            Column(Modifier.clip(MaterialTheme.shapes.small).border(4.dp, lerp(c.boardDark, Color.Black, 0.35f), MaterialTheme.shapes.small)) {
                for (row in 0 until 8) Row {
                    for (col in 0 until 8) {
                        val i = row * 8 + col
                        val piece = s.board[i]
                        val dest = legal.firstOrNull { it.from == selected && it.to == i }
                        val movable = legal.any { it.from == i }
                        val interactive = s.turn == 1 && s.result == GameResult.PLAYING && (dest != null || movable)
                        Box(Modifier.size(cell).background(if ((row + col) % 2 == 1) c.boardDark else c.boardLight)
                            .clickable(enabled = interactive) { if (dest != null) { play(dest); selected = null } else selected = i }
                            .semantics {
                                contentDescription = say("Row ${row + 1}, column ${col + 1}, " + when {
                                    piece > 0 -> "your ${if (piece == 2) "king" else "man"}"; piece < 0 -> "computer ${if (piece == -2) "king" else "man"}"
                                    dest != null -> "legal move"; else -> "empty" })
                                if (selected == i) stateDescription = say("Selected")
                            }, contentAlignment = Alignment.Center) {
                            if (selected == i || hint?.from == i || hint?.to == i)
                                Canvas(Modifier.fillMaxSize()) { drawRect(c.highlight, style = Stroke(size.width * 0.08f)) }
                            if (piece != 0) CheckerPiece(if (piece > 0) 1 else -1, kotlin.math.abs(piece) == 2, size = cell * 0.86f)
                            else if (dest != null) Box(Modifier.size(cell * 0.3f).background(c.highlight.copy(alpha = 0.85f), CircleShape))
                            if (piece > 0 && movable && selected == null && s.turn == 1)
                                Box(Modifier.align(Alignment.BottomEnd).padding(3.dp).size(6.dp).background(c.highlight, CircleShape))
                        }
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CheckerPiece(1, false, size = 20.dp); Text("You move first, up the board", style = MaterialTheme.typography.bodySmall, color = c.muted)
        CheckerPiece(-1, false, size = 20.dp); Text("Computer", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

// ---------------- Reversi ----------------

private val ReversiGreen = Color(0xFF2E7D4F)
private val ReversiLine = Color(0xFF1B4D32)

@Composable
private fun ReversiBoard(s: ReversiState, hint: Move?, play: (Move) -> Unit) {
    val look = LocalGameLook.current
    val legal = remember(s) { s.legalMoves() }
    ZoomBox(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = maxWidth / 8
            Column(Modifier.clip(MaterialTheme.shapes.small).background(ReversiGreen).border(4.dp, Color(0xFF3E2A1C), MaterialTheme.shapes.small)) {
                for (row in 0 until 8) Row {
                    for (col in 0 until 8) {
                        val i = row * 8 + col
                        val move = Move(Op.MOVE, i)
                        val playable = s.turn == 1 && move in legal
                        Box(Modifier.size(cell).clickable(enabled = playable) { play(move) }.semantics {
                            contentDescription = say("Row ${row + 1}, column ${col + 1}, " + when (s.board[i]) { 1 -> "black"; -1 -> "white"; else -> if (playable) "legal move" else "empty" })
                        }) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRect(ReversiLine, style = Stroke(1.5f))
                                when {
                                    s.board[i] != 0 -> drawDisc(s.board[i])
                                    playable -> drawCircle(Color.Black.copy(alpha = 0.28f), size.width * 0.14f)
                                }
                                if (hint?.from == i) drawCircle(look.colors.highlight, size.width * 0.36f, style = Stroke(size.width * 0.08f))
                            }
                        }
                    }
                }
            }
        }
    }
    Text("You play black. Tap a dot to place a disc; trapped white discs flip.", style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
}

// ---------------- Dominoes ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DominoBoard(s: DominoState, hint: Move?, play: (Move) -> Unit) {
    val look = LocalGameLook.current
    val c = look.colors
    var selected by remember(s) { mutableStateOf<Int?>(null) }
    val legal = remember(s) { s.legalMoves() }
    fun choose(id: Int) {
        val moves = legal.filter { it.op == Op.MOVE && it.from == id }
        when {
            moves.isEmpty() -> selected = null
            moves.size == 1 -> { play(moves.first()); selected = null }
            else -> selected = if (selected == id) null else id
        }
    }
    TablePanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Computer", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 6.dp))
            if (s.result == GameResult.PLAYING) repeat(s.hands[1].size) { DominoBack(14.dp) }
            else s.hands[1].forEach { DominoTile(DominoState.tiles[it].a, DominoState.tiles[it].b, unit = 14.dp) }
        }
        Surface(color = c.tableInset, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)) {
            if (s.chain.isEmpty()) Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                Text("You lead — play any tile from your hand.", color = c.onTable)
            } else FlowRow(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.chain.forEach { t -> DominoTile(t.a, t.b, horizontal = t.a != t.b, unit = 22.dp) }
            }
        }
        if (s.chain.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("Left end ${s.chain.first().a}", onTable = true); InfoChip("Right end ${s.chain.last().b}", onTable = true)
        }
        Text("Your hand", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.hands[0].forEach { id ->
                val t = DominoState.tiles[id]
                val playable = s.turn == 1 && legal.any { it.op == Op.MOVE && it.from == id }
                DominoTile(t.a, t.b, unit = 32.dp, selected = selected == id || (hint != null && hint.from == id && hint.op == Op.MOVE), dimmed = !playable && s.result == GameResult.PLAYING,
                    modifier = Modifier.clickable(enabled = playable) { choose(id) }
                        .semantics { contentDescription = say("Domino ${t.a} and ${t.b}${if (playable) ", playable" else ""}") })
            }
        }
        val sel = selected
        if (sel != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { play(Move(Op.MOVE, sel, 0)) }, modifier = Modifier.weight(1f)) { Text("Play left (${s.chain.first().a})") }
            Button(onClick = { play(Move(Op.MOVE, sel, 1)) }, modifier = Modifier.weight(1f)) { Text("Play right (${s.chain.last().b})") }
        }
        if (s.turn == 1 && Move(Op.DRAW) in legal) Button(onClick = { play(Move(Op.DRAW)) }, modifier = Modifier.fillMaxWidth()) { Text("No match — draw from the boneyard") }
        if (s.turn == 1 && Move(Op.PASS) in legal) Button(onClick = { play(Move(Op.PASS)) }, modifier = Modifier.fillMaxWidth()) { Text("No match and boneyard empty — pass") }
        if (s.result != GameResult.PLAYING) Text("Pips left — you ${s.hands[0].sumOf { DominoState.tiles[it].pips }}, computer ${s.hands[1].sumOf { DominoState.tiles[it].pips }}")
    }
}

@Composable
private fun DominoBack(unit: Dp) {
    val c = LocalGameLook.current.colors
    Canvas(Modifier.size(unit, unit * 2)) {
        drawRoundRect(c.tileBack, cornerRadius = CornerRadius(size.width * 0.18f))
        drawRoundRect(Color.Black.copy(alpha = 0.3f), cornerRadius = CornerRadius(size.width * 0.18f), style = Stroke(1.5f))
        drawCircle(Color.White.copy(alpha = 0.35f), size.width * 0.14f)
    }
}
