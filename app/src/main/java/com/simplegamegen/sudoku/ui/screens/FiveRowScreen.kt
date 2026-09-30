package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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
import com.simplegamegen.sudoku.duels.FiveRow
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

private val Board = Color(0xFFC4A574)
private val Line = Color(0xFF3E2723)
private val DarkStone = Color(0xFF1A1A1A)
private val LightStone = Color(0xFFF7F4EF)

private val FiveStrength = listOf(
    "Places almost at random",
    "Takes a line of five and blocks yours",
    "Looks a couple of moves ahead",
    "Searches further around the stones",
)

val FiveRowSetup: (PuzzleFactory) -> PlaySetup<FiveRow> = { factory ->
    PlaySetup(
        rules = "Take turns placing one stone on an empty square of the 11×11 board. Stones stay where they are put. " +
            "Get five or more in a line — across, down or diagonal — before the computer does. " +
            "A full board with no line of five is a draw. You play the dark stones and move first. Pinch to zoom if the board is small.",
        settingTitle = "Computer strength",
        settings = FiveRow.NAMES,
        describe = { FiveStrength[it] },
        settingOf = { it.setting },
        inProgress = { !it.over && it.cells.any { cell -> cell != 0 } },
        subtitle = {
            "${FiveRow.NAMES[it.setting]} · you ${it.cells.count { cell -> cell == 1 }}, computer ${it.cells.count { cell -> cell == -1 }}"
        },
        create = { i -> { factory.custom("fiverow:$i", { seed -> FiveRow.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.over) null
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW })
        },
    )
}

@Composable
fun FiveRowScreen(nav: NavController, vm: PlayViewModel<FiveRow>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.FIVE_ROW, remember(factory) { FiveRowSetup(factory) }) { g, s ->
        val you = g.cells.count { it == 1 }
        val cpu = g.cells.count { it == -1 }
        DuelStatus(g.over, g.winner, g.turn, s.thinking, "You $you", "Computer $cpu")
        val playable = !g.over && g.turn == 1 && !s.thinking && !s.busy
        val winning = remember(g) { g.winningCells() }
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cell = maxWidth / FiveRow.SIZE
                    Column(Modifier.clip(RoundedCornerShape(8.dp))) {
                        for (row in 0 until FiveRow.SIZE) {
                            Row {
                                for (col in 0 until FiveRow.SIZE) {
                                    val index = row * FiveRow.SIZE + col
                                    val who = g.cells[index]
                                    val state = when (who) {
                                        1 -> "your stone"
                                        -1 -> "computer's stone"
                                        else -> if (playable) "empty, place a stone" else "empty"
                                    }
                                    Box(
                                        Modifier.size(cell)
                                            .clickable(enabled = playable && who == 0, role = Role.Button) { vm.play { it.place(index) } }
                                            .semantics { contentDescription = say("Row ${row + 1}, column ${col + 1}, $state") },
                                    ) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            drawRect(Board)
                                            drawLine(Line.copy(alpha = 0.55f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.5f)
                                            drawLine(Line.copy(alpha = 0.55f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = 1.5f)
                                            if (who != 0) {
                                                val stone = if (who == 1) DarkStone else LightStone
                                                drawCircle(Color.Black.copy(alpha = 0.25f), size.minDimension * 0.34f, Offset(center.x + 1.5f, center.y + 2f))
                                                drawCircle(stone, size.minDimension * 0.34f)
                                                if (who == -1) drawCircle(Line, size.minDimension * 0.34f, style = Stroke(1.5f))
                                            }
                                            val ring = when {
                                                index in winning -> Color.White
                                                index == g.last -> c.highlight
                                                else -> null
                                            }
                                            if (ring != null) drawCircle(ring, size.minDimension * 0.4f, style = Stroke(size.minDimension * 0.07f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!g.over && playable) Text("Tap an empty square.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}
