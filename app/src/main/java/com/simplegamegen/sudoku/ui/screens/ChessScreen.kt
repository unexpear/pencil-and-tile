package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.tabletop.Chess
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.abs

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
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cell = maxWidth / 8
                    Column(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .border(4.dp, Color(0xFF3E2723), MaterialTheme.shapes.small),
                    ) {
                        for (row in 0 until 8) Row {
                            for (col in 0 until 8) {
                                val i = row * 8 + col
                                val piece = g.board[i]
                                val dest = legal.firstOrNull { it.from == selected && it.to == i }
                                val movable = playable && legal.any { it.from == i }
                                val interactive = playable && (dest != null || movable || selected != null)
                                val light = (row + col) % 2 == 0
                                Box(
                                    Modifier
                                        .size(cell)
                                        .background(if (light) c.boardLight else c.boardDark)
                                        .clickable(enabled = interactive, role = Role.Button) {
                                            when {
                                                dest != null -> {
                                                    vm.play { it.play(dest) }
                                                    selected = null
                                                }
                                                movable -> selected = if (selected == i) null else i
                                                selected != null -> selected = null
                                            }
                                        }
                                        .semantics {
                                            contentDescription = say(
                                                "Row ${8 - row}, column ${col + 1}, " + when {
                                                    piece > 0 -> "your ${Chess.pieceName(piece)}"
                                                    piece < 0 -> "computer ${Chess.pieceName(piece)}"
                                                    dest != null -> "legal move"
                                                    else -> "empty"
                                                },
                                            )
                                            if (selected == i) stateDescription = say("Selected")
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected == i || g.lastFrom == i || g.lastTo == i) {
                                        Canvas(Modifier.fillMaxSize()) {
                                            drawRect(c.highlight.copy(alpha = 0.45f))
                                        }
                                    }
                                    if (piece != 0) {
                                        ChessGlyph(piece, cell.value)
                                    } else if (dest != null) {
                                        Box(
                                            Modifier
                                                .size(cell * 0.28f)
                                                .background(c.highlight.copy(alpha = 0.85f), CircleShape),
                                        )
                                    }
                                    if (piece > 0 && movable && selected == null) {
                                        Box(
                                            Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(3.dp)
                                                .size(6.dp)
                                                .background(c.highlight, CircleShape),
                                        )
                                    }
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

@Composable
private fun ChessGlyph(piece: Int, cellSp: Float) {
    val white = piece > 0
    val symbol = when (abs(piece)) {
        Chess.KING -> if (white) "♔" else "♚"
        Chess.QUEEN -> if (white) "♕" else "♛"
        Chess.ROOK -> if (white) "♖" else "♜"
        Chess.BISHOP -> if (white) "♗" else "♝"
        Chess.KNIGHT -> if (white) "♘" else "♞"
        Chess.PAWN -> if (white) "♙" else "♟"
        else -> ""
    }
    val c = LocalGameLook.current.colors
    Text(
        symbol,
        fontSize = (cellSp * 0.55f).coerceIn(18f, 36f).sp,
        fontWeight = FontWeight.Bold,
        color = if (white) c.playerOne else c.playerTwo,
    )
}
