package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.simplegamegen.sudoku.tabletop.Go
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

private val GoStrength = listOf(
    "Places almost at random, and takes a stone when it sees one",
    "Plays the move that looks best right now",
    "Looks at your reply",
    "Looks a little further ahead",
)

val GoSetup: (PuzzleFactory) -> PlaySetup<Go> = { factory ->
    PlaySetup(
        rules = "Go on a 19×19 board. You play black and move first. Stones do not move. " +
            "A connected group with no empty neighbor is captured and taken off. " +
            "You cannot fill your own last liberty, and you cannot repeat an earlier board. " +
            "Pass when you are done. Two passes in a row end the game. " +
            "Your score is your stones plus every empty region that touches only your color. White receives 7.5 points. " +
            "Capture stones that cannot live before you pass. Tap an empty intersection to play.",
        settingTitle = "Computer strength",
        settings = Go.NAMES,
        describe = { GoStrength[it] },
        settingOf = { it.setting },
        inProgress = { !it.ended && (it.board.any { stone -> stone != 0 } || it.passes > 0) },
        subtitle = {
            if (it.ended) "You ${it.blackArea} · computer ${it.whiteArea} + 7.5"
            else Go.NAMES[it.setting]
        },
        create = { i -> { factory.custom("go:$i", { seed -> Go.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else Outcome(when (g.winner) { Go.BLACK -> Result.WON; else -> Result.LOST }, g.blackArea.toLong())
        },
    )
}

@Composable
fun GoScreen(nav: NavController, vm: PlayViewModel<Go>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.GO, remember(factory) { GoSetup(factory) }) { g, s ->
        DuelStatus(g.over, g.winner, g.turn, s.thinking, "You (black)", "Computer (white)")
        if (g.ended) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("You ${g.blackArea}", emphasized = g.winner == Go.BLACK)
                InfoChip("Computer ${g.whiteArea} + 7.5", emphasized = g.winner == Go.WHITE)
            }
        }
        val playable = !g.ended && g.turn == Go.BLACK && !s.thinking && !s.busy
        val legal = remember(g) { g.legalPlacements().toSet() }
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val n = g.size
                    val cell = maxWidth / n
                    Box(
                        Modifier
                            .size(cell * n)
                            .border(3.dp, Color(0xFF6D4C41), MaterialTheme.shapes.small),
                    ) {
                        Canvas(Modifier.matchParentSize()) {
                            val step = size.width / n
                            val origin = step / 2f
                            val span = step * (n - 1)
                            drawRect(c.boardLight)
                            val ink = c.text.copy(alpha = 0.55f)
                            for (i in 0 until n) {
                                val p = origin + i * step
                                drawLine(ink, Offset(origin, p), Offset(origin + span, p), strokeWidth = 2f)
                                drawLine(ink, Offset(p, origin), Offset(p, origin + span), strokeWidth = 2f)
                            }
                            if (n == 19) {
                                for (row in listOf(3, 9, 15)) for (col in listOf(3, 9, 15)) {
                                    drawCircle(ink, step * 0.12f, Offset(origin + col * step, origin + row * step))
                                }
                            }
                            for (i in g.board.indices) if (g.board[i] != 0) {
                                val center = Offset(origin + (i % n) * step, origin + (i / n) * step)
                                val stone = if (g.board[i] == Go.BLACK) c.playerOne else c.playerTwo
                                drawCircle(stone, step * 0.42f, center)
                                if (g.board[i] == Go.WHITE) drawCircle(c.text, step * 0.42f, center, style = Stroke(2f))
                                if (g.last == i) drawCircle(if (g.board[i] == Go.BLACK) c.playerTwo else c.playerOne, step * 0.12f, center)
                            }
                        }
                        Column {
                            for (row in 0 until n) {
                                Row {
                                    for (col in 0 until n) {
                                        val i = row * n + col
                                        val stone = g.board[i]
                                        val canPlay = playable && i in legal
                                        Box(
                                            Modifier
                                                .size(cell)
                                                .clickable(enabled = canPlay, role = Role.Button) { vm.play { it.place(i) } }
                                                .semantics {
                                                    contentDescription = say(
                                                        "Row ${row + 1}, column ${col + 1}, " + when (stone) {
                                                            Go.BLACK -> "your stone"
                                                            Go.WHITE -> "computer stone"
                                                            else -> "empty"
                                                        },
                                                    )
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (playable) {
            OutlinedButton(
                onClick = { vm.play { it.pass() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Pass") }
        }
    }
}
