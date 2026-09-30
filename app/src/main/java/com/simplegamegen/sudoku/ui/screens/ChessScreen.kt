package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.tabletop.Chess
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.chessRise
import com.simplegamegen.sudoku.ui.assets.coversCell
import com.simplegamegen.sudoku.ui.assets.drawBoardSlab
import com.simplegamegen.sudoku.ui.assets.drawChessMan
import com.simplegamegen.sudoku.ui.assets.drawDot
import com.simplegamegen.sudoku.ui.assets.drawSquareTop
import com.simplegamegen.sudoku.ui.assets.tableFrame
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.abs
import kotlin.math.roundToInt

private val ChessStrength = listOf(
    "Moves almost at random",
    "Takes material when it sees it",
    "Looks two moves ahead",
    "Looks three moves ahead",
)

val ChessSetup: (PuzzleFactory) -> PlaySetup<Chess> = { factory ->
    PlaySetup(
        rules = "Standard chess on an 8×8 board. You play white and move first. " +
            "Capture the computer's king by checkmate — put it in check with no legal escape. " +
            "If a side has no legal move and is not in check, the game is a draw (stalemate). " +
            "The same position three times, or 50 moves each with no capture and no pawn move, is also a draw. " +
            "Pawns promote to a queen. Castling and en passant follow the usual rules. " +
            "Tap one of your pieces, then a highlighted square.",
        settingTitle = "Computer strength",
        settings = Chess.NAMES,
        describe = { ChessStrength[it] },
        settingOf = { it.setting },
        inProgress = { !it.ended && (it.lastFrom != null || it.board != Chess.OPENING) },
        subtitle = {
            val check = if (it.inCheck() && !it.ended) " · check" else ""
            "${Chess.NAMES[it.setting]}$check"
        },
        create = { i -> { factory.custom("chess:$i", { seed -> Chess.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW })
        },
    )
}

@Composable
fun ChessScreen(nav: NavController, vm: PlayViewModel<Chess>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.CHESS, remember(factory) { ChessSetup(factory) }) { g, s ->
        DuelStatus(g.over, g.winner, g.turn, s.thinking, "You (white)", "Computer (black)")
        if (!g.ended && g.inCheck()) {
            InfoChip("Check!", emphasized = true)
        }
        val playable = !g.ended && g.turn == 1 && !s.thinking && !s.busy
        var selected by remember(g) { mutableStateOf<Int?>(null) }
        val legal = remember(g) { g.legalMoves() }
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                val display = LocalDensity.current
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val frame = tableFrame(8, with(display) { maxWidth.toPx() }, peakZ = 1.5f)
                    val top = 0.22f
                    val order = (0 until 64).sortedBy { (it / 8) + (it % 8) }
                    Box(Modifier.size(with(display) { frame.width.toDp() }, with(display) { frame.height.toDp() })) {
                        order.forEach { i ->
                            val row = i / 8
                            val col = i % 8
                            val piece = g.board[i]
                            val dest = legal.firstOrNull { it.from == selected && it.to == i }
                            val movable = playable && legal.any { it.from == i }
                            val at = frame.at(col + 0.5f, row + 0.5f, top)
                            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp).semantics {
                                contentDescription = say(
                                    "Row ${8 - row}, column ${col + 1}, " + when {
                                        piece > 0 -> "your ${Chess.pieceName(piece)}"
                                        piece < 0 -> "computer ${Chess.pieceName(piece)}"
                                        dest != null -> "legal move"
                                        else -> "empty"
                                    },
                                )
                                if (selected == i) stateDescription = say("Selected")
                                if (playable && (dest != null || movable)) onClick {
                                    when {
                                        dest != null -> { vm.play { it.play(dest) }; selected = null }
                                        movable -> selected = if (selected == i) null else i
                                    }
                                    true
                                }
                            })
                        }
                        Canvas(Modifier.matchParentSize().pointerInput(g, selected, playable) {
                            detectTapGestures { pos ->
                                if (!playable) return@detectTapGestures
                                val hit = order.asReversed().firstOrNull { i ->
                                    val row = i / 8
                                    val col = i % 8
                                    val piece = g.board[i]
                                    val tall = if (piece == 0) top else top + chessRise(piece)
                                    coversCell(frame, pos, col, row, top, tall, frame.cell * 0.32f)
                                } ?: return@detectTapGestures
                                val dest = legal.firstOrNull { it.from == selected && it.to == hit }
                                val movable = legal.any { it.from == hit }
                                when {
                                    dest != null -> { vm.play { it.play(dest) }; selected = null }
                                    movable -> selected = if (selected == hit) null else hit
                                    selected != null -> selected = null
                                }
                            }
                        }) {
                            val wood = Color(0xFF3E2723)
                            drawBoardSlab(frame, top, wood)
                            order.forEach { i ->
                                val row = i / 8
                                val col = i % 8
                                val light = (row + col) % 2 == 0
                                val marked = selected == i || g.lastFrom == i || g.lastTo == i
                                drawSquareTop(frame, col, row, top, if (marked) lerp(if (light) c.boardLight else c.boardDark, c.highlight, 0.45f) else if (light) c.boardLight else c.boardDark)
                                val piece = g.board[i]
                                val dest = legal.firstOrNull { it.from == selected && it.to == i }
                                if (piece != 0) {
                                    drawChessMan(frame, col + 0.5f, row + 0.5f, top, piece, if (piece > 0) c.playerOne else c.playerTwo, measurer)
                                } else if (dest != null) {
                                    drawDot(frame, col + 0.5f, row + 0.5f, top, c.highlight)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!g.ended && playable) {
            Text("Tap a piece, then a square.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("♔ You", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Text("♚ Computer", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
    }
}
