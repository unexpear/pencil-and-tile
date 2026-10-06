package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.TicTacToe
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

private val Paper = Color(0xFFF6EBD2)
private val Ink = Color(0xFF3E2723)
private val Cross = Color(0xFF1565C0)
private val Nought = Color(0xFFE53935)
private val WinTint = Color(0xFFFFF3C4)
private val WinRing = Color(0xFFF9A825)

private val TicStrength = listOf(
    "Places almost at random, and sometimes misses a line",
    "Takes a line and blocks yours",
    "Looks ahead for two threats at once",
    "Plays perfectly on 3×3, and searches on larger boards",
)

val TicTacToeSetup: (PuzzleFactory) -> PlaySetup<TicTacToe> = { factory ->
    PlaySetup(
        rules = "Take turns placing one mark on an empty square. On 3×3, three in a row wins. " +
            "On 4×4 you need four, and on 5×5 you need five, across, down or diagonally. " +
            "Marks stay where they are put. A full board with no line is a draw. " +
            "You play X against the computer and move first. Or pass the phone for two players.",
        settingTitle = "Board",
        settings = TicTacToe.NAMES,
        describe = { if (TicTacToe.passOf(it)) "Pass the phone. X moves first." else TicStrength[TicTacToe.levelOf(it)] },
        settingOf = { it.setting },
        inProgress = { !it.over && it.cells.any { cell -> cell != 0 } },
        subtitle = { ticSubtitle(it) },
        create = { i -> { factory.custom("tictactoe:$i", { seed -> TicTacToe.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.over) null
            else if (g.passAndPlay) Outcome(Result.FINISHED)
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW })
        },
    )
}

private fun ticSubtitle(g: TicTacToe): String {
    val who = if (g.passAndPlay) "two players" else TicTacToe.LEVELS[g.level]
    val x = g.cells.count { it == 1 }
    val o = g.cells.count { it == -1 }
    return "${g.size}×${g.size} $who · X $x, O $o"
}

@Composable
fun TicTacToeScreen(nav: NavController, vm: PlayViewModel<TicTacToe>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var full by rememberSaveable { mutableStateOf(false) }
    PlayShell(
        nav, vm, GameId.TIC_TAC_TOE, remember(factory) { TicTacToeSetup(factory) },
        scroll = false,
        tight = true,
        tools = { _, _ ->
            ToolButton(GameIcons.Fit, if (full) "Exit full screen" else "Full screen") { full = !full }
        },
    ) { g, s ->
        val human = g.passAndPlay || g.turn == 1
        val playable = !g.over && human && !s.thinking && !s.busy
        val winning = remember(g) { g.winningCells() }
        val x = g.cells.count { it == 1 }
        val o = g.cells.count { it == -1 }
        PlayBoard(
            full, { full = false }, s.canUndo && !s.busy, vm::undo,
            above = {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InfoChip(ticStatus(g, s.thinking), emphasized = true)
                    InfoChip(if (g.passAndPlay) "X marks $x" else "Your X $x")
                    InfoChip(if (g.passAndPlay) "O marks $o" else "Computer O $o")
                }
            },
            below = {
                val line = ticHint(g)
                if (line.isNotEmpty()) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (g.over && g.winner == 1 && !g.passAndPlay) c.success else c.muted,
                    )
                }
            },
        ) {
            TicBoard(
                g, playable, winning, c.highlight,
                onPlace = { index -> vm.play { it.place(index) } },
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

private fun ticStatus(g: TicTacToe, thinking: Boolean): String = when {
    g.over && g.winner == 1 -> if (g.passAndPlay) "X wins" else "You won!"
    g.over && g.winner == -1 -> if (g.passAndPlay) "O wins" else "Computer won"
    g.over -> "Draw"
    g.passAndPlay && g.turn == 1 -> "X's turn"
    g.passAndPlay -> "O's turn"
    g.turn == 1 -> "Your turn"
    thinking -> "Computer is thinking…"
    else -> "Computer's turn"
}

private fun ticHint(g: TicTacToe): String = when {
    g.over && g.winner == 1 && !g.passAndPlay -> "Well played! Start a new game to go again."
    g.over && g.winner == -1 && !g.passAndPlay -> "The computer won this one."
    g.over && g.draw -> "It's a draw."
    g.over && g.winner == 1 -> "X has a line."
    g.over && g.winner == -1 -> "O has a line."
    g.passAndPlay && g.turn == 1 -> "X's turn. Tap an empty square."
    g.passAndPlay -> "O's turn. Tap an empty square."
    !g.over && g.turn == 1 -> "Tap an empty square."
    else -> ""
}

@Composable
private fun TicBoard(
    g: TicTacToe,
    playable: Boolean,
    winning: Set<Int>,
    highlight: Color,
    onPlace: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val cell = side / g.size
        Column(Modifier.size(side).clip(RoundedCornerShape(12.dp))) {
            for (row in 0 until g.size) {
                Row {
                    for (col in 0 until g.size) {
                        val index = row * g.size + col
                        val who = g.cells[index]
                        val state = when (who) {
                            1 -> if (g.passAndPlay) "X" else "your X"
                            -1 -> if (g.passAndPlay) "O" else "computer's O"
                            else -> if (playable) "empty, place a mark" else "empty"
                        }
                        Box(
                            Modifier.size(cell)
                                .clickable(enabled = playable && who == 0, role = Role.Button) { onPlace(index) }
                                .semantics { contentDescription = say("Row ${row + 1}, column ${col + 1}, $state") },
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRect(if (index in winning) WinTint else Paper)
                                val grid = size.minDimension * 0.035f
                                if (col > 0) drawLine(Ink, Offset(0f, 0f), Offset(0f, size.height), grid)
                                if (row > 0) drawLine(Ink, Offset(0f, 0f), Offset(size.width, 0f), grid)
                                val mark = size.minDimension * 0.08f
                                val inset = size.minDimension * 0.22f
                                when (who) {
                                    1 -> {
                                        drawLine(Cross, Offset(inset, inset), Offset(size.width - inset, size.height - inset), mark, StrokeCap.Round)
                                        drawLine(Cross, Offset(size.width - inset, inset), Offset(inset, size.height - inset), mark, StrokeCap.Round)
                                    }
                                    -1 -> drawCircle(Nought, size.minDimension * 0.28f, style = Stroke(mark))
                                }
                                if (index in winning) {
                                    drawCircle(WinRing, size.minDimension * 0.4f, style = Stroke(size.minDimension * 0.06f))
                                } else if (index == g.last) {
                                    drawCircle(highlight, size.minDimension * 0.4f, style = Stroke(size.minDimension * 0.05f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
