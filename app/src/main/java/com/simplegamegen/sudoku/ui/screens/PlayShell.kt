package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.ReportPlay
import com.simplegamegen.sudoku.ui.PlaySession
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.ConfirmDialog
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.HeaderAction
import com.simplegamegen.sudoku.ui.components.MenuAction
import com.simplegamegen.sudoku.ui.components.MessageLine
import com.simplegamegen.sudoku.ui.components.NewGameSheet
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.StartCard
import com.simplegamegen.sudoku.ui.components.ToolBar
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

/** Describes one game's settings for the shared shell. */
class PlaySetup<S>(
    val rules: String,
    val settingTitle: String,
    val settings: List<String>,
    val describe: (Int) -> String,
    val settingOf: (S) -> Int,
    /** True while the game can still be played (replacing it asks first). */
    val inProgress: (S) -> Boolean,
    val subtitle: (S) -> String? = { null },
    val create: (Int) -> (suspend () -> S),
    /** Identifies one particular game (its seed) for the player's records. */
    val identity: (S) -> String,
    /** How the game ended, or null while it's still going. */
    val outcome: (S) -> Outcome?,
)

/**
 * Shared frame for PlayViewModel games: header, rules, start card, new-game sheet,
 * replace confirmation and a tool bar that always starts with Undo and ends with New.
 */
@Composable
fun <S : Any> PlayShell(
    nav: NavController,
    vm: PlayViewModel<S>,
    id: GameId,
    setup: PlaySetup<S>,
    tools: @Composable RowScope.(S, PlaySession<S>) -> Unit = { _, _ -> },
    undoable: Boolean = true,
    scroll: Boolean = true,
    /** Less padding, so a 3D board can use the phone's width and height. */
    tight: Boolean = false,
    content: @Composable ColumnScope.(S, PlaySession<S>) -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val look = LocalGameLook.current
    val game = s.game
    var setting by rememberSaveable { mutableIntStateOf(0) }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var rules by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(game != null) { game?.let { setting = setup.settingOf(it) } }
    fun begin() { sheet = false; confirm = false; vm.start(setup.create(setting)) }
    fun request() { if (game != null && setup.inProgress(game)) confirm = true else begin() }
    ReportPlay(id, key = game?.let(setup.identity), level = game?.let { setup.settings[setup.settingOf(it)] } ?: "",
        levelIndex = game?.let(setup.settingOf) ?: 0, inProgress = game != null && setup.inProgress(game), outcome = game?.let(setup.outcome))
    GameScaffold(
        title = id.title, game = id, tutorial = id, onBack = { nav.popBackStack() }, busy = s.busy || s.thinking,
        scroll = game == null || scroll,
        subtitle = game?.let { setup.subtitle(it) ?: setup.settings[setup.settingOf(it)] },
        actions = { HeaderAction(GameIcons.Rules, "Rules") { rules = true } },
        menu = if (game == null) emptyList() else listOf(
            MenuAction("New game", GameIcons.Plus, enabled = !s.busy) { sheet = true },
            MenuAction("Save now", GameIcons.Save, enabled = !s.busy, onClick = vm::retrySave),
        ),
        contentPadding = if (tight) PaddingValues(horizontal = 2.dp, vertical = 2.dp) else PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        itemSpacing = if (tight) 4.dp else 12.dp,
        bottomBar = if (game == null) null else {
            {
                ToolBar {
                    if (undoable) ToolButton(GameIcons.Undo, "Undo", enabled = !s.busy && s.canUndo, onClick = vm::undo)
                    tools(game, s)
                    ToolButton(GameIcons.Plus, "New", enabled = !s.busy) { sheet = true }
                }
            }
        },
    ) {
        if (game == null) GameFamilyPicker(nav, id)
        when {
            game == null && s.busy -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            game == null -> StartCard(id.title, setup.rules, art = { GameArt(id, Modifier.size(96.dp)) }, tutorial = id) {
                OptionGroup(setup.settingTitle, setup.settings.indices.toList(), setting, { setup.settings[it] }) { setting = it }
                Text(setup.describe(setting), style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
                MessageLine(s.message)
                Button(onClick = ::begin, enabled = !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start") }
            }
            else -> {
                content(game, s)
                MessageLine(s.message)
            }
        }
    }
    if (sheet) NewGameSheet("New game", startLabel = "Start", game = id, onDismiss = { sheet = false }, onStart = ::request) {
        GameFamilyPicker(nav, id)
        OptionGroup(setup.settingTitle, setup.settings.indices.toList(), setting, { setup.settings[it] }) { setting = it }
        Text(setup.describe(setting), style = MaterialTheme.typography.bodyMedium, color = look.colors.muted)
    }
    if (rules) AlertDialog(onDismissRequest = { rules = false }, title = { Text("How to play ${id.title}") },
        text = { Text(setup.rules) }, confirmButton = { TextButton(onClick = { rules = false }) { Text("Got it") } },
        containerColor = look.colors.surface)
    if (confirm) ConfirmDialog("Start a new game?", "This replaces your saved ${id.title} game.", "New game",
        onConfirm = ::begin, onDismiss = { confirm = false })
}

/**
 * The board fills the play area. [full] covers the header and tool bar so the same board
 * uses the whole screen, with a way back and undo.
 */
@Composable
fun ColumnScope.PlayBoard(
    full: Boolean,
    onExit: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    bar: @Composable RowScope.() -> Unit = {},
    /** Status, captures, or hands. Shown in the play column and again in full screen. */
    above: @Composable ColumnScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
    board: @Composable BoxScope.() -> Unit,
) {
    if (full) {
        val colors = LocalGameLook.current.colors
        Dialog(
            onDismissRequest = onExit,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Column(
                Modifier.fillMaxSize().background(colors.background).systemBarsPadding().padding(horizontal = 2.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onExit, modifier = Modifier.weight(1.3f).heightIn(min = 48.dp)) {
                        Text("Exit full screen", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (canUndo) OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Undo") }
                    bar()
                }
                above()
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center, content = board)
                below()
            }
        }
    }
    above()
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center, content = board)
    below()
}

val LevelNames = listOf("Easy", "Medium", "Hard", "Expert")
