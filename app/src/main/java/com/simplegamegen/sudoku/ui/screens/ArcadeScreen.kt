package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.ReportPlay
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.data.Outcome
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simplegamegen.sudoku.mahjong.MahjongProgress
import com.simplegamegen.sudoku.mahjong.MahjongRules
import com.simplegamegen.sudoku.ui.ArcadeGame
import com.simplegamegen.sudoku.ui.ArcadeState
import com.simplegamegen.sudoku.ui.ArcadeViewModel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.MahjongTile
import com.simplegamegen.sudoku.ui.assets.TILE_ASPECT
import com.simplegamegen.sudoku.ui.assets.mahjongName
import com.simplegamegen.sudoku.ui.components.ConfirmDialog
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.MenuAction
import com.simplegamegen.sudoku.ui.components.MessageLine
import com.simplegamegen.sudoku.ui.components.NewGameSheet
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.StartCard
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ToolBar
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.words.HangmanProgress
import com.simplegamegen.sudoku.words.WordDifficulty
import com.simplegamegen.sudoku.words.WordPuzzles
import com.simplegamegen.sudoku.ui.i18n.say

@Composable
fun ArcadeScreen(nav: NavController, vm: ArcadeViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val id = if (vm.game == ArcadeGame.HANGMAN) GameId.HANGMAN else GameId.MAHJONG
    val hasGame = s.hangman != null || s.mahjong != null
    val complete = s.hangman?.complete == true || s.mahjong?.complete == true
    var sheet by rememberSaveable { mutableStateOf(false) }
    var confirmNew by rememberSaveable { mutableStateOf(false) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }
    var difficulty by rememberSaveable { mutableStateOf(WordDifficulty.EASY) }
    var theme by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(s.hangman?.puzzle, s.mahjong?.deal) {
        s.hangman?.puzzle?.let { difficulty = it.difficulty; theme = it.theme }
        s.mahjong?.deal?.let { difficulty = it.difficulty }
    }
    fun start() { if (hasGame && !complete) confirmNew = true else { sheet = false; vm.newGame(theme, difficulty) } }
    val hang = s.hangman; val mj = s.mahjong
    ReportPlay(id, key = hang?.puzzle?.seed?.toString() ?: mj?.deal?.seed?.toString(),
        level = (hang?.puzzle?.difficulty ?: mj?.deal?.difficulty)?.label ?: "", levelIndex = (hang?.puzzle?.difficulty ?: mj?.deal?.difficulty)?.ordinal ?: 0,
        inProgress = hasGame && !complete,
        outcome = when {
            hang?.won == true -> Outcome(Result.WON, hang.mistakes.toLong()); hang?.lost == true -> Outcome(Result.LOST)
            mj?.complete == true -> Outcome(Result.WON); else -> null
        })
    val subtitle = s.hangman?.let { "${WordPuzzles.themes[it.puzzle.theme]} · ${it.puzzle.difficulty.label}" }
        ?: s.mahjong?.let { "${it.deal.difficulty.label} · ${it.deal.tiles.size} tiles" }
    GameScaffold(
        title = vm.game.title, subtitle = subtitle, game = id, tutorial = id, onBack = { nav.popBackStack() }, busy = s.busy || s.hintBusy,
        menu = if (!hasGame) emptyList() else listOfNotNull(
            MenuAction("New game", GameIcons.Plus, enabled = !s.busy) { sheet = true },
            s.mahjong?.let { p -> MenuAction("Restart deal", GameIcons.Restart, enabled = p.moves.isNotEmpty()) { confirmRestart = true } },
            MenuAction("Save now", GameIcons.Save, enabled = !s.busy, onClick = vm::retrySave),
        ),
        bottomBar = if (!hasGame) null else {
            {
                ToolBar {
                    s.mahjong?.let { p ->
                        ToolButton(GameIcons.Undo, "Undo", enabled = !s.busy && p.moves.isNotEmpty(), onClick = vm::undo)
                        HintButton(id, if (s.hintBusy) "Checking…" else "Safe hint", enabled = !s.busy && !p.complete && !s.hintBusy, onClick = vm::hint)
                        ToolButton(GameIcons.Restart, "Restart", enabled = !s.busy && p.moves.isNotEmpty()) { confirmRestart = true }
                    }
                    s.hangman?.let { p -> HintButton(id, "Reveal letter", enabled = !s.busy && !p.complete, onClick = vm::hint) }
                    ToolButton(GameIcons.Plus, "New", enabled = !s.busy) { sheet = true }
                }
            }
        },
    ) {
        when {
            s.busy && !hasGame -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            !hasGame -> StartCard(vm.game.title,
                if (vm.game == ArcadeGame.HANGMAN) "Guess the hidden word one letter at a time before the drawing is finished."
                else "Match pairs of identical free tiles until the board is clear. Every deal has a verified clearing route.",
                art = { GameArt(id, Modifier.size(64.dp)) }, tutorial = id) {
                ArcadeOptions(vm.game, difficulty, theme, { difficulty = it }, { theme = it })
                MessageLine(s.message)
                Button(onClick = { vm.newGame(theme, difficulty) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start game") }
            }
            else -> {
                s.hangman?.let { HangmanPlay(it, s.busy, vm::guess, onNext = { vm.newGame(theme, difficulty) }) }
                s.mahjong?.let { MahjongPlay(it, s, vm::selectTile, onNext = { vm.newGame(theme, difficulty) }) }
                MessageLine(s.message)
            }
        }
    }
    if (sheet) NewGameSheet("New game", game = id, onDismiss = { sheet = false }, onStart = ::start) {
        ArcadeOptions(vm.game, difficulty, theme, { difficulty = it }, { theme = it })
    }
    if (confirmNew || confirmRestart) ConfirmDialog(
        title = if (confirmRestart) "Restart this deal?" else "Start a new game?",
        text = if (confirmRestart) "Your moves are cleared. The same solvable deal remains." else "This replaces your current ${vm.game.title} game.",
        confirm = if (confirmRestart) "Restart" else "New game",
        onConfirm = {
            if (confirmRestart) vm.restart() else { sheet = false; vm.newGame(theme, difficulty) }
            confirmNew = false; confirmRestart = false
        },
        onDismiss = { confirmNew = false; confirmRestart = false },
    )
}

@Composable
private fun ArcadeOptions(game: ArcadeGame, difficulty: WordDifficulty, theme: Int, onDifficulty: (WordDifficulty) -> Unit, onTheme: (Int) -> Unit) {
    val look = LocalGameLook.current
    OptionGroup("Difficulty", WordDifficulty.entries, difficulty, { it.label }, onSelect = onDifficulty)
    if (game == ArcadeGame.HANGMAN) {
        Text("${8 - difficulty.ordinal} wrong guesses allowed", style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
        OptionGroup("Theme", WordPuzzles.themes.indices.toList(), theme, { WordPuzzles.themes[it] }, onSelect = onTheme)
    } else {
        Text("${listOf(24, 48, 72, 96)[difficulty.ordinal]} tiles on ${listOf(1, 2, 2, 3)[difficulty.ordinal]} layer(s)",
            style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
    }
}

// ---------------- Hangman ----------------

@Composable
private fun HangmanPlay(p: HangmanProgress, busy: Boolean, guess: (Char) -> Unit, onNext: () -> Unit) {
    val look = LocalGameLook.current
    val c = look.colors
    val left = p.puzzle.mistakeLimit - p.mistakes
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoChip((if (left == 1) "1 guess left" else "$left guesses left"), emphasized = left <= 2 && !p.complete)
        if (p.hints > 0) InfoChip("${p.hints} revealed", icon = GameIcons.Hint)
    }
    Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(12.dp)
            .semantics { contentDescription = say("${p.mistakes} of ${p.puzzle.mistakeLimit} wrong guesses") }) {
            val u = size.height / 10
            val cx = size.width / 2
            val wood = Color(0xFF8B5A34); val woodDark = Color(0xFF5E3B20)
            val ink = if (look.dark) c.text else Color(0xFF2A2A2E)
            drawRoundRect(woodDark, Offset(cx - 4 * u, 9.2f * u), androidx.compose.ui.geometry.Size(6 * u, 0.6f * u))
            drawLine(wood, Offset(cx - 2.6f * u, 9.4f * u), Offset(cx - 2.6f * u, 0.6f * u), strokeWidth = 0.55f * u)
            drawLine(wood, Offset(cx - 2.9f * u, 0.8f * u), Offset(cx + 1.3f * u, 0.8f * u), strokeWidth = 0.55f * u)
            drawLine(wood, Offset(cx - 2.6f * u, 2.4f * u), Offset(cx - 1.2f * u, 0.8f * u), strokeWidth = 0.35f * u)
            drawLine(Color(0xFFC9A36A), Offset(cx + u, 0.8f * u), Offset(cx + u, 2f * u), strokeWidth = 0.18f * u)
            val parts = if (p.lost) 6 else p.mistakes * 6 / p.puzzle.mistakeLimit
            val body = if (p.lost) c.danger else ink
            val w = 0.28f * u
            val cap = StrokeCap.Round
            if (parts >= 1) drawCircle(body, 0.75f * u, Offset(cx + u, 2.75f * u), style = Stroke(w))
            if (parts >= 2) drawLine(body, Offset(cx + u, 3.5f * u), Offset(cx + u, 6.2f * u), strokeWidth = w, cap = cap)
            if (parts >= 3) drawLine(body, Offset(cx + u, 4.2f * u), Offset(cx, 5.3f * u), strokeWidth = w, cap = cap)
            if (parts >= 4) drawLine(body, Offset(cx + u, 4.2f * u), Offset(cx + 2 * u, 5.3f * u), strokeWidth = w, cap = cap)
            if (parts >= 5) drawLine(body, Offset(cx + u, 6.2f * u), Offset(cx + 0.2f * u, 7.8f * u), strokeWidth = w, cap = cap)
            if (parts >= 6) drawLine(body, Offset(cx + u, 6.2f * u), Offset(cx + 1.8f * u, 7.8f * u), strokeWidth = w, cap = cap)
        }
    }
    // Word slots.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        p.puzzle.answer.forEach { ch ->
            val shown = ch in p.guesses
            Column(Modifier.width(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (shown || p.lost) ch.toString() else " ", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    color = if (!shown && p.lost) c.danger else c.text)
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (shown) c.accent else c.outline, MaterialTheme.shapes.extraSmall))
            }
        }
    }
    if (p.complete) Surface(color = (if (p.won) c.success else c.danger).copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (p.won) "You got it!" else "The word was ${p.puzzle.answer}", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, color = if (p.won) c.success else c.danger)
            Button(onClick = onNext, enabled = !busy) { Text("Next word") }
        }
    }
    // Keyboard.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM").forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
                row.forEach { letter ->
                    val used = letter in p.guesses
                    val hit = used && letter in p.puzzle.answer
                    val bg = when { hit -> c.success.copy(alpha = 0.22f); used -> c.surfaceAlt; else -> c.surface }
                    Surface(onClick = { guess(letter) }, enabled = !p.complete && !used && !busy,
                        modifier = Modifier.weight(1f, fill = false).width(33.dp).height(46.dp)
                            .semantics { contentDescription = say("Letter $letter"); if (used) stateDescription = say(if (hit) "in the word" else "not in the word") },
                        color = bg, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, if (hit) c.success else c.outline)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(letter.toString(), fontWeight = FontWeight.Bold, fontSize = 17.sp,
                                color = when { hit -> c.success; used -> c.muted.copy(alpha = 0.6f); else -> c.text })
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Mahjong ----------------

@Composable
private fun MahjongPlay(p: MahjongProgress, s: ArcadeState, select: (Int) -> Unit, onNext: () -> Unit) {
    val look = LocalGameLook.current
    val removed = p.removed
    val pairs = MahjongRules.pairs(p.deal.tiles, removed)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoChip("${p.deal.tiles.size - removed.size} tiles left")
        InfoChip("${pairs.size} pairs open", emphasized = pairs.isEmpty() && !p.complete)
        if (p.hints > 0) InfoChip("${p.hints} hints", icon = GameIcons.Hint)
    }
    if (p.complete) Surface(color = look.colors.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Board cleared!", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = look.colors.success)
            Button(onClick = onNext, enabled = !s.busy) { Text("Next deal") }
        }
    } else if (pairs.isEmpty()) Text("No free pairs match. Undo a move or restart the deal.", color = look.colors.danger)
    TablePanel(padding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val tiles = p.deal.tiles
                val columns = (tiles.maxOf { it.x } + 2) / 2f
                val maxZ = tiles.maxOf { it.z }
                val fitWidth = maxWidth / (columns + 0.12f * (maxZ + 1) + 0.02f)
                val w = if (fitWidth >= 32.dp) minOf(fitWidth, 56.dp) else 40.dp
                val depth = w * 0.12f
                val faceH = w * TILE_ASPECT
                val lift = depth
                val boardW = w * columns + depth + lift * maxZ
                val boardH = faceH * ((tiles.maxOf { it.y } + 2) / 2f) + depth + lift * maxZ
                Box(Modifier.fillMaxWidth().then(if (fitWidth < 32.dp) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                    contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.size(boardW, boardH)) {
                        tiles.filter { it.id !in removed }.sortedWith(compareBy({ it.z }, { it.y }, { it.x })).forEach { tile ->
                            val free = MahjongRules.free(tiles, removed, tile.id)
                            val chosen = tile.id == s.selected
                            val hinted = tile.id == s.highlighted?.a || tile.id == s.highlighted?.b
                            MahjongTile(tile.face,
                                Modifier.offset(x = w / 2 * tile.x + lift * (maxZ - tile.z), y = faceH / 2 * tile.y + lift * (maxZ - tile.z))
                                    .clickable(enabled = free && !p.complete) { select(tile.id) }
                                    .semantics { contentDescription = say("${mahjongName(tile.face)}, ${if (free) "free" else "blocked"}"); if (chosen) stateDescription = say("Selected") },
                                width = w, depth = depth, free = free, selected = chosen, hinted = hinted)
                        }
                    }
                }
            }
        }
        Text(if (s.selected != null) "Now tap a matching free tile." else "Tap two identical free tiles. Free tiles have nothing on top and an open left or right side.",
            style = MaterialTheme.typography.bodySmall)
    }
}
