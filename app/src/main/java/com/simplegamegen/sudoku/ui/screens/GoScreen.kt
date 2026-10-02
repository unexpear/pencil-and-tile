package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.tabletop.Go
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.coversCell
import com.simplegamegen.sudoku.ui.assets.drawBoardLabel
import com.simplegamegen.sudoku.ui.assets.drawBoardFrame
import com.simplegamegen.sudoku.ui.assets.drawDot
import com.simplegamegen.sudoku.ui.assets.drawSquareTop
import com.simplegamegen.sudoku.ui.assets.drawStone
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
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
    // A 19×19 board has small points, so Go starts from high above, where every point is big enough to tap.
    val camera = rememberBoardCamera("go_views", GoViews)
    var full by rememberSaveable { mutableStateOf(false) }
    PlayShell(
        nav, vm, GameId.GO, remember(factory) { GoSetup(factory) },
        scroll = false,
        tools = { _, _ ->
            ToolButton(GameIcons.Fit, if (full) "Exit full screen" else "Full screen") { full = !full }
        },
    ) { g, s ->
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
        var selected by remember(g) { mutableStateOf<Int?>(null) }
        val group = remember(g, selected) {
            val at = selected
            if (at == null || g.board.getOrNull(at) == 0) emptyList<Int>() to emptyList()
            else g.groupPoints(at)
        }
        PlayBoard(
            full, { full = false }, s.canUndo && !s.busy, vm::undo,
            bar = {
                if (playable) OutlinedButton(onClick = { vm.play { it.pass() } }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Pass") }
            },
        ) {
            GoTable(camera, g, legal, playable, marking, group.first.toSet(), group.second.toSet(), onPlace = { i -> vm.play { it.place(i) } }, onMark = { i -> vm.play { it.mark(i) } }, onSelect = { selected = it })
        }
        if (playable) {
            OutlinedButton(onClick = { vm.play { it.pass() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Pass") }
        }
        if (g.counting) {
            Text(
                "Tap a group that cannot live. Count when the marks are right. Resume to keep playing.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
            Button(onClick = { vm.play { it.count() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Count the board") }
            OutlinedButton(onClick = { vm.play { it.resume() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Resume play") }
        }
    }
}

@Composable
private fun GoTable(
    camera: BoardCamera,
    g: Go,
    legal: Set<Int>,
    playable: Boolean,
    marking: Boolean,
    group: Set<Int>,
    liberties: Set<Int>,
    onPlace: (Int) -> Unit,
    onMark: (Int) -> Unit,
    onSelect: (Int?) -> Unit,
) {
    val c = LocalGameLook.current.colors
    val measurer = rememberTextMeasurer()
    val n = g.size
    BoardWithViews(camera, n = n, peakZ = 0.55f, margin = 0.9f) { frame ->
        val top = 0.18f
        val order = (0 until n * n).sortedByDescending { frame.depth((it % n) + 0.5f, (it / n) + 0.5f, top) }
            order.forEach { i ->
                val row = i / n
                val col = i % n
                val stone = g.board[i]
                val canPlay = playable && i in legal
                val canMark = marking && stone != 0
                val canSelect = stone != 0 && !marking
                val at = frame.at(col + 0.5f, row + 0.5f, top)
                Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp).semantics {
                    contentDescription = say(
                        "Row ${row + 1}, column ${col + 1}, " + when (stone) {
                            Go.BLACK -> "your stone"
                            Go.WHITE -> "computer stone"
                            else -> "empty"
                        } + (if (i in g.dead) ", marked dead" else "") + (if (i in liberties) ", liberty" else ""),
                    )
                    if (i in group) stateDescription = say("Selected")
                    if (canPlay) onClick { onPlace(i); true }
                    if (canMark) onClick { onMark(i); true }
                    if (canSelect && !canPlay) onClick { onSelect(if (i in group) null else i); true }
                })
            }
            Canvas(Modifier.matchParentSize().pointerInput(g, playable, marking, group) {
                detectTapGestures { pos ->
                    val hit = order.asReversed().firstOrNull { i ->
                        coversCell(frame, pos, i % n, i / n, top, if (g.board[i] == 0) top else top + 0.2f, frame.unitAt(i % n + 0.5f, i / n + 0.5f, top) * 0.4f)
                    } ?: return@detectTapGestures
                    when {
                        playable && hit in legal -> onPlace(hit)
                        marking && g.board[hit] != 0 -> onMark(hit)
                        g.board[hit] != 0 -> onSelect(if (hit in group) null else hit)
                        else -> onSelect(null)
                    }
                }
            }) {
                drawBoardFrame(frame, top, Color(0xFF8A5A33), border = 0.85f)
                // One sheet of wood, so no seams show between points; only a selected group's squares are tinted.
                drawPath(Path().apply {
                    val q = listOf(frame.at(0f, 0f, top), frame.at(n.toFloat(), 0f, top), frame.at(n.toFloat(), n.toFloat(), top), frame.at(0f, n.toFloat(), top))
                    moveTo(q[0].x, q[0].y); q.drop(1).forEach { lineTo(it.x, it.y) }; close()
                }, GoWood)
                order.forEach { i ->
                    if (i in group || i in liberties) drawSquareTop(frame, i % n, i / n, top, lerp(GoWood, c.highlight, 0.4f))
                }
                val ink = Color(0xFF2B1D12).copy(alpha = 0.8f)
                for (i in 0 until n) {
                    drawLine(ink, frame.at(0.5f, i + 0.5f, top + 0.01f), frame.at(n - 0.5f, i + 0.5f, top + 0.01f), strokeWidth = 1.5f)
                    drawLine(ink, frame.at(i + 0.5f, 0.5f, top + 0.01f), frame.at(i + 0.5f, n - 0.5f, top + 0.01f), strokeWidth = 1.5f)
                }
                val stars = when (n) { 19 -> listOf(3, 9, 15); 13 -> listOf(3, 6, 9); 9 -> listOf(2, 4, 6); else -> emptyList() }
                run {
                    for (row in stars) for (col in stars) {
                        if (n == 9 && (row == 4) != (col == 4)) continue
                        drawCircle(ink, frame.unitAt(col + 0.5f, row + 0.5f, top) * 0.08f, frame.at(col + 0.5f, row + 0.5f, top + 0.02f))
                    }
                }
                val label = Color(0xFFF1DFC0)
                for (i in 0 until n) {
                    val mark = (i + 1).toString()
                    drawBoardLabel(measurer, frame, mark, i + 0.5f, -0.5f, top, label)
                    drawBoardLabel(measurer, frame, mark, i + 0.5f, n + 0.5f, top, label)
                    drawBoardLabel(measurer, frame, mark, -0.55f, i + 0.5f, top, label)
                    drawBoardLabel(measurer, frame, mark, n + 0.55f, i + 0.5f, top, label)
                }
                liberties.forEach { i -> drawDot(frame, (i % n) + 0.5f, (i / n) + 0.5f, top, c.highlight) }
                order.forEach { i ->
                    if (g.board[i] != 0) {
                        // Slate and shell, whatever the theme: Go stones are black and white.
                        val stone = if (g.board[i] == Go.BLACK) Color(0xFF1F1F23) else Color(0xFFF2EFE6)
                        val marked = g.last == i || i in group
                        val markColor = if (i in group) c.highlight else if (g.board[i] == Go.BLACK) Color(0xFFF2EFE6) else Color(0xFF1F1F23)
                        drawStone(frame, (i % n) + 0.5f, (i / n) + 0.5f, top, stone, mark = marked, markColor = markColor, dead = i in g.dead)
                    }
                }
            }
        }
    }

/** Kaya-coloured board wood. */
private val GoWood = Color(0xFFE2B46C)

private val GoViews = listOf(
    com.simplegamegen.sudoku.ui.assets.BoardView("Above", yaw = 0f, pitch = 72f, distance = 1.6f),
    com.simplegamegen.sudoku.ui.assets.BoardViews.behind,
    com.simplegamegen.sudoku.ui.assets.BoardViews.top,
)
