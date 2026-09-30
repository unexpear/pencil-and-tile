package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.tabletop.Go
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.coversCell
import com.simplegamegen.sudoku.ui.assets.drawBoardSlab
import com.simplegamegen.sudoku.ui.assets.drawSquareTop
import com.simplegamegen.sudoku.ui.assets.drawStone
import com.simplegamegen.sudoku.ui.assets.tableFrame
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.roundToInt

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
            "Pass when you are done. Two passes in a row stop play. Mark every group that cannot live, then count. " +
            "Resume if you want to keep playing. " +
            "Your score is your stones plus every empty region that touches only your color. White receives 7.5 points. " +
            "Tap an empty intersection to play.",
        settingTitle = "Computer strength",
        settings = Go.NAMES,
        describe = { GoStrength[it] },
        settingOf = { it.setting },
        inProgress = { !it.ended && (it.counting || it.board.any { stone -> stone != 0 } || it.passes > 0) },
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
        if (!g.counting) DuelStatus(g.over, g.winner, g.turn, s.thinking, "You (black)", "Computer (white)")
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("You (black)")
                InfoChip("Computer (white)")
            }
            InfoChip("Read from this board. Tap a group to change it.", emphasized = true)
        }
        if (g.ended) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip("You ${g.blackArea}", emphasized = g.winner == Go.BLACK)
                InfoChip("Computer ${g.whiteArea} + 7.5", emphasized = g.winner == Go.WHITE)
            }
        }
        val playable = !g.ended && !g.counting && g.turn == Go.BLACK && !s.thinking && !s.busy
        val marking = g.counting && !s.busy
        val legal = remember(g) { g.legalPlacements().toSet() }
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                val display = LocalDensity.current
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val n = g.size
                    val frame = tableFrame(n, with(display) { maxWidth.toPx() }, peakZ = 0.5f)
                    val top = 0.18f
                    val order = (0 until n * n).sortedBy { (it / n) + (it % n) }
                    Box(Modifier.size(with(display) { frame.width.toDp() }, with(display) { frame.height.toDp() })) {
                        order.forEach { i ->
                            val row = i / n
                            val col = i % n
                            val stone = g.board[i]
                            val canPlay = playable && i in legal
                            val canMark = marking && stone != 0
                            val at = frame.at(col + 0.5f, row + 0.5f, top)
                            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp).semantics {
                                contentDescription = say(
                                    "Row ${row + 1}, column ${col + 1}, " + when (stone) {
                                        Go.BLACK -> "your stone"
                                        Go.WHITE -> "computer stone"
                                        else -> "empty"
                                    } + if (i in g.dead) ", marked dead" else "",
                                )
                                if (canPlay) onClick { vm.play { it.place(i) }; true }
                                if (canMark) onClick { vm.play { it.mark(i) }; true }
                            })
                        }
                        Canvas(Modifier.matchParentSize().pointerInput(g, playable, marking) {
                            detectTapGestures { pos ->
                                val hit = order.asReversed().firstOrNull { i ->
                                    coversCell(frame, pos, i % n, i / n, top, if (g.board[i] == 0) top else top + 0.2f, frame.cell * 0.4f)
                                } ?: return@detectTapGestures
                                when {
                                    playable && hit in legal -> vm.play { it.place(hit) }
                                    marking && g.board[hit] != 0 -> vm.play { it.mark(hit) }
                                }
                            }
                        }) {
                            drawBoardSlab(frame, top, Color(0xFF6D4C41))
                            order.forEach { i -> drawSquareTop(frame, i % n, i / n, top, c.boardLight) }
                            val ink = c.text.copy(alpha = 0.55f)
                            for (i in 0 until n) {
                                drawLine(ink, frame.at(0.5f, i + 0.5f, top + 0.01f), frame.at(n - 0.5f, i + 0.5f, top + 0.01f), strokeWidth = 1.5f)
                                drawLine(ink, frame.at(i + 0.5f, 0.5f, top + 0.01f), frame.at(i + 0.5f, n - 0.5f, top + 0.01f), strokeWidth = 1.5f)
                            }
                            if (n == 19) {
                                for (row in listOf(3, 9, 15)) for (col in listOf(3, 9, 15)) {
                                    drawCircle(ink, frame.cell * 0.08f, frame.at(col + 0.5f, row + 0.5f, top + 0.02f))
                                }
                            }
                            order.forEach { i ->
                                if (g.board[i] != 0) {
                                    val stone = if (g.board[i] == Go.BLACK) c.playerOne else c.playerTwo
                                    drawStone(frame, (i % n) + 0.5f, (i / n) + 0.5f, top, stone, mark = g.last == i, markColor = if (g.board[i] == Go.BLACK) c.playerTwo else c.playerOne, dead = i in g.dead)
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
        if (g.counting) {
            Text(
                "Tap a group that cannot live. Count when the marks are right. Resume to keep playing.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
            Button(
                onClick = { vm.play { it.count() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Count the board") }
            OutlinedButton(
                onClick = { vm.play { it.resume() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Resume play") }
        }
    }
}
