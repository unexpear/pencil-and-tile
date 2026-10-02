package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
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
import com.simplegamegen.sudoku.tabletop.Chess
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.chessRise
import com.simplegamegen.sudoku.ui.assets.coversCell
import com.simplegamegen.sudoku.ui.assets.drawBoardLabel
import com.simplegamegen.sudoku.ui.assets.drawBoardFrame
import com.simplegamegen.sudoku.ui.assets.drawSquareGrain
import com.simplegamegen.sudoku.ui.assets.ChessEbony
import com.simplegamegen.sudoku.ui.assets.ChessIvory
import com.simplegamegen.sudoku.ui.assets.drawChessMan
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.drawFlatDisk
import com.simplegamegen.sudoku.ui.assets.drawDot
import com.simplegamegen.sudoku.ui.assets.drawRing
import com.simplegamegen.sudoku.ui.assets.drawSquareTop
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
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
    val camera = rememberBoardCamera()
    var full by rememberSaveable { mutableStateOf(false) }
    PlayShell(
        nav, vm, GameId.CHESS, remember(factory) { ChessSetup(factory) },
        scroll = false,
        tools = { _, _ ->
            ToolButton(GameIcons.Fit, if (full) "Exit full screen" else "Full screen") { full = !full }
        },
    ) { g, s ->
        DuelStatus(g.over, g.winner, g.turn, s.thinking, "You (white)", "Computer (black)")
        if (!g.ended && g.inCheck()) InfoChip("Check!", emphasized = true)
        val playable = !g.ended && g.turn == 1 && !s.thinking && !s.busy
        var selected by remember(g) { mutableStateOf<Int?>(null) }
        val legal = remember(g) { g.legalMoves() }
        // What each side has taken, above and below the board, the computer's side on top as on the board.
        Captures(g, byYou = false)
        PlayBoard(full, { full = false }, s.canUndo && !s.busy, vm::undo) {
            ChessTable(camera, g, legal, selected, playable, onSelect = { selected = it }, onPlay = { move ->
                vm.play { it.play(move) }
                selected = null
            })
        }
        Captures(g, byYou = true)
    }
}

@Composable
private fun ChessTable(
    camera: BoardCamera,
    g: Chess,
    legal: List<com.simplegamegen.sudoku.tabletop.ChessMove>,
    selected: Int?,
    playable: Boolean,
    onSelect: (Int?) -> Unit,
    onPlay: (com.simplegamegen.sudoku.tabletop.ChessMove) -> Unit,
) {
    val c = LocalGameLook.current.colors
    val measurer = rememberTextMeasurer()
    BoardWithViews(camera, n = 8, peakZ = 1.5f, margin = 0.62f) { frame ->
        val top = 0.22f
        val order = (0 until 64).sortedByDescending { frame.depth((it % 8) + 0.5f, (it / 8) + 0.5f, top) }
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
                            dest != null -> onPlay(dest)
                            movable -> onSelect(if (selected == i) null else i)
                        }
                        true
                    }
                })
            }
            Canvas(Modifier.matchParentSize().pointerInput(g, selected, playable) {
                detectTapGestures { pos ->
                    if (!playable) return@detectTapGestures
                    val hit = order.asReversed().firstOrNull { i ->
                        val piece = g.board[i]
                        val lift = if (selected == i) 0.4f else 0f
                        val tall = if (piece == 0) top else top + lift + chessRise(piece)
                        coversCell(frame, pos, i % 8, i / 8, top, tall, frame.unitAt(i % 8 + 0.5f, i / 8 + 0.5f, top) * 0.32f)
                    } ?: return@detectTapGestures
                    val dest = legal.firstOrNull { it.from == selected && it.to == hit }
                    val movable = legal.any { it.from == hit }
                    when {
                        dest != null -> onPlay(dest)
                        movable -> onSelect(if (selected == hit) null else hit)
                        selected != null -> onSelect(null)
                    }
                }
            }) {
                drawBoardFrame(frame, top, Color(0xFF5B3A24), border = 0.5f)
                order.forEach { i ->
                    val row = i / 8
                    val col = i % 8
                    val light = (row + col) % 2 == 0
                    val base = if (light) c.boardLight else c.boardDark
                    val dest = legal.firstOrNull { it.from == selected && it.to == i }
                    val marked = selected == i || g.lastFrom == i || g.lastTo == i || dest != null
                    drawSquareTop(frame, col, row, top, if (marked) lerp(base, c.highlight, 0.45f) else base)
                    drawSquareGrain(frame, col, row, top, base)
                    if (dest != null && g.board[i] == 0) drawDot(frame, col + 0.5f, row + 0.5f, top, c.highlight)
                }
                val from = g.lastFrom
                val to = g.lastTo
                if (from != null && to != null) {
                    drawLine(
                        c.highlight,
                        frame.at(from % 8 + 0.5f, from / 8 + 0.5f, top + 0.08f),
                        frame.at(to % 8 + 0.5f, to / 8 + 0.5f, top + 0.08f),
                        strokeWidth = frame.cell * 0.07f,
                        cap = StrokeCap.Round,
                    )
                }
                // Files and ranks are inlaid in the frame.
                val ink = Color(0xFFF1DFC0)
                for (col in 0 until 8) {
                    val file = ('a' + col).toString()
                    drawBoardLabel(measurer, frame, file, col + 0.5f, -0.27f, top, ink)
                    drawBoardLabel(measurer, frame, file, col + 0.5f, 8.27f, top, ink)
                }
                for (row in 0 until 8) {
                    val rank = (8 - row).toString()
                    drawBoardLabel(measurer, frame, rank, -0.27f, row + 0.5f, top, ink)
                    drawBoardLabel(measurer, frame, rank, 8.27f, row + 0.5f, top, ink)
                }
                order.forEach { i ->
                    val piece = g.board[i]
                    val row = i / 8
                    val col = i % 8
                    if (piece != 0) {
                        val lift = if (selected == i) 0.4f else 0f
                        drawChessMan(frame, col + 0.5f, row + 0.5f, top, piece, if (piece > 0) ChessIvory else ChessEbony, lift)
                    }
                    val dest = legal.firstOrNull { it.from == selected && it.to == i }
                    if (selected == i || (dest != null && piece != 0)) {
                        drawRing(frame, col + 0.5f, row + 0.5f, top, c.highlight)
                    }
                }
                if (!g.ended && g.inCheck()) {
                    val king = g.board.indexOfFirst { it == 6 * g.turn }
                    if (king >= 0) {
                        drawFlatDisk(
                            frame, king % 8 + 0.5f, king / 8 + 0.5f, top + 0.07f, 0.48f,
                            Color(0xFFE53935), strokePx = frame.cell * 0.09f,
                        )
                    }
                }
        }
    }
}

private val PieceValue = mapOf(1 to 1, 2 to 3, 3 to 3, 4 to 5, 5 to 9)

/**
 * The men one side has taken, as small figures on a tray, and the material lead if that side is ahead.
 * Promotions can leave a side with more of a kind than it began with; those count as nothing taken.
 */
@Composable
private fun Captures(g: Chess, byYou: Boolean) {
    val c = LocalGameLook.current.colors
    val sign = if (byYou) -1 else 1                       // you take black men (negative codes)
    val taken = (1..5).flatMap { kind ->
        val start = Chess.OPENING.count { it == sign * kind }
        val now = g.board.count { it == sign * kind }
        List((start - now).coerceAtLeast(0)) { kind }
    }
    fun worth(side: Int) = (1..5).sumOf { k -> (Chess.OPENING.count { it == side * k } - g.board.count { it == side * k }).coerceAtLeast(0) * PieceValue.getValue(k) }
    val lead = worth(sign) - worth(-sign)
    Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (taken.isNotEmpty()) Text(if (byYou) "You took" else "Computer took", style = MaterialTheme.typography.bodySmall, color = c.muted)
        if (taken.isNotEmpty()) Surface(color = if (byYou) Color(0xFF6D5A4A) else Color(0xFF3A2F2B), shape = RoundedCornerShape(8.dp),
            modifier = Modifier.semantics { contentDescription = say(taken.joinToString(", ") { Chess.pieceName(sign * it) }) }) {
            androidx.compose.material3.Text(taken.joinToString("") { chessGlyph(it) }, Modifier.padding(horizontal = 6.dp),
                fontSize = 20.sp, color = if (byYou) Color(0xFF1C1512) else ChessIvory)
        }
        if (lead > 0) androidx.compose.material3.Text("+$lead", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.text)
    }
}

/** The solid figure for a kind of man, so it reads in either colour. */
private fun chessGlyph(kind: Int) = when (kind) { 1 -> "♟"; 2 -> "♞"; 3 -> "♝"; 4 -> "♜"; 5 -> "♛"; else -> "♚" }
