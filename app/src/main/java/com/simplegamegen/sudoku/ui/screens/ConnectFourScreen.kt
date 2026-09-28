package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.ConnectFour
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.drawCounter
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

private val BoardBlue = Color(0xFF1565C0)
private val BoardHole = Color(0xFF0D3A86)

val ConnectFourSetup: (PuzzleFactory) -> PlaySetup<ConnectFour> = { factory ->
    PlaySetup(
        rules = "Take turns dropping a disc into a column. It falls to the lowest empty space. Line up four of your discs across, " +
            "down or diagonally before the computer does. A full board with no line of four is a draw. You play red and move first. Tap a column to drop.",
        settingTitle = "Computer strength",
        settings = ConnectFour.NAMES,
        describe = {
            listOf(
                "Drops almost at random",
                "Takes a win and blocks yours",
                "Looks a few moves ahead",
                "Searches deeper for the best column",
            )[it]
        },
        settingOf = { it.setting },
        inProgress = { !it.over && it.cells.any { cell -> cell != 0 } },
        subtitle = { "${ConnectFour.NAMES[it.setting]} · you ${it.cells.count { cell -> cell == 1 }}, computer ${it.cells.count { cell -> cell == -1 }}" },
        create = { i -> { factory.custom("connect4:$i", { seed -> ConnectFour.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.over) null
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW })
        },
    )
}

@Composable
fun ConnectFourScreen(nav: NavController, vm: PlayViewModel<ConnectFour>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.CONNECT_FOUR, remember(factory) { ConnectFourSetup(factory) }) { g, s ->
        val you = g.cells.count { it == 1 }
        val cpu = g.cells.count { it == -1 }
        DuelStatus(g.over, g.winner, g.turn, s.thinking, "You $you", "Computer $cpu")
        val playable = !g.over && g.turn == 1 && !s.thinking
        val winning = remember(g) { g.winningCells() }
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cell = maxWidth / ConnectFour.COLS
                    Column {
                        Row {
                            for (col in 0 until ConnectFour.COLS) {
                                val open = g.canDrop(col)
                                val spoken = when {
                                    !open -> "Column ${col + 1}, full"
                                    playable -> "Column ${col + 1}, drop a disc"
                                    else -> "Column ${col + 1}"
                                }
                                Box(
                                    Modifier.size(cell, 48.dp)
                                        .clickable(enabled = playable && open, role = Role.Button) { vm.play { it.drop(col) } }
                                        .semantics { contentDescription = say(spoken) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${col + 1}", style = MaterialTheme.typography.titleMedium,
                                        color = if (playable && open) c.accent else c.muted)
                                }
                            }
                        }
                        Column(Modifier.clip(RoundedCornerShape(10.dp))) {
                            for (row in 0 until ConnectFour.ROWS) {
                                Row {
                                    for (col in 0 until ConnectFour.COLS) {
                                        val index = row * ConnectFour.COLS + col
                                        val who = g.cells[index]
                                        val landing = who == 0 && row == g.landingRow(col)
                                        val state = when (who) {
                                            1 -> "your disc"
                                            -1 -> "computer's disc"
                                            else -> if (landing && playable) "drop a disc" else "empty space"
                                        }
                                        Box(
                                            Modifier.size(cell)
                                                .clickable(enabled = playable && g.canDrop(col), role = Role.Button) { vm.play { it.drop(col) } }
                                                .semantics { contentDescription = say("Row ${row + 1}, column ${col + 1}, $state") },
                                        ) {
                                            Canvas(Modifier.fillMaxSize()) {
                                                drawRect(BoardBlue)
                                                drawCircle(BoardHole, size.minDimension * 0.42f)
                                                if (who != 0) drawCounter(who)
                                                val ring = when {
                                                    index in winning -> Color.White
                                                    index == g.last -> c.highlight
                                                    else -> null
                                                }
                                                if (ring != null) drawCircle(ring, size.minDimension * 0.36f, style = Stroke(size.minDimension * 0.06f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!g.over) Text("Tap a numbered column to drop your disc.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(18.dp)) { drawCounter(1) }
            Text("You", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Canvas(Modifier.size(18.dp)) { drawCounter(-1) }
            Text("Computer", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}
