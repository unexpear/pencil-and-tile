package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.tabletop.TriD
import com.simplegamegen.sudoku.tabletop.TriMove
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.BoardView
import com.simplegamegen.sudoku.ui.assets.ChessEbony
import com.simplegamegen.sudoku.ui.assets.ChessIvory
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.chessRise
import com.simplegamegen.sudoku.ui.assets.drawBoardLabel
import com.simplegamegen.sudoku.ui.assets.drawChessMan
import com.simplegamegen.sudoku.ui.assets.drawDot
import com.simplegamegen.sudoku.ui.assets.drawFlatDisk
import com.simplegamegen.sudoku.ui.assets.drawFloatSquare
import com.simplegamegen.sudoku.ui.assets.drawRing
import com.simplegamegen.sudoku.ui.assets.floatSquareHit
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.roundToInt

private val TriDViews = listOf(
    BoardView("Stack", yaw = 18f, pitch = 48f, distance = 1.85f),
    BoardView("Side", yaw = 76f, pitch = 34f, distance = 2.05f),
    BoardView("Top", yaw = 0f, pitch = 84f, distance = 1.95f),
)

private val TriDStrength = listOf(
    "Moves almost at random",
    "Takes material when it sees it",
    "Looks a move ahead",
    "Looks two moves ahead",
    "Pass the phone. White moves first.",
)

private const val TriDRules =
    "Tri-dimensional chess follows Charles Roth's Federation Revised Standard 5.0 (2012), his reading of Andrew Bartmess's Federation Standard for the television board. There is no Paramount ruleset. " +
        "This app leaves out Bartmess's rook-pawn sideways step, which Roth treats as optional, and it does not use Michael Grant's attack-board rules. " +
        "Three fixed 4×4 main boards and four movable 2×2 attack boards. You play White and move first, or pass the phone. " +
        "Pieces use ordinary chess moves in file and rank and may finish on any level. They cannot move straight up. " +
        "The path is the highest square between them that is not above the higher board. When that board is a main board with an attack board just above it, a higher path is also allowed. Knights jump. " +
        "An attack board you own may move instead of a piece when it is empty or carries only one of your pawns, and you still have a pawn: slide it one or two ranks along its file, and you may flip it. A three-rank gap between posts is too far. " +
        "Capturing the last enemy man on an attack board takes that board. An empty board stays with its owner. " +
        "Pawns move one rank forward, or two on their first move, and capture one file diagonally onto any level. They promote to a queen, rook, bishop, or knight on rank 8 or 9 for White and rank 0 or 1 for Black. " +
        "Castling and en passant follow Roth's rules. Checkmate wins. Stalemate, the same position three times, or 50 moves each with no capture and no pawn move, is a draw. Tap a piece, then a highlighted square. When a pawn promotes, choose the piece."

val TriDSetup: (PuzzleFactory) -> PlaySetup<TriD> = { factory ->
    PlaySetup(
        rules = TriDRules,
        settingTitle = "Computer strength",
        settings = TriD.NAMES,
        describe = { TriDStrength[it] },
        settingOf = { it.setting },
        inProgress = { !it.ended && (it.lastFrom >= 0 || it.board != TriD.OPENING || it.pins != listOf(0, 0, 5, 5)) },
        subtitle = {
            val who = if (it.passAndPlay) "two players" else TriD.NAMES[it.setting]
            val check = if (it.inCheck() && !it.ended) " · Check!" else ""
            "$who$check"
        },
        create = { i -> { factory.custom("trid:$i", { seed -> TriD.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else if (g.passAndPlay) Outcome(Result.FINISHED)
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW })
        },
    )
}

@Composable
fun TriDScreen(nav: NavController, vm: PlayViewModel<TriD>, factory: PuzzleFactory) {
    val camera = rememberBoardCamera("trid_views", TriDViews, emptyList())
    var full by rememberSaveable { mutableStateOf(false) }
    PlayShell(
        nav, vm, GameId.TRID, remember(factory) { TriDSetup(factory) },
        scroll = false,
        tight = true,
        tools = { _, _ ->
            ToolButton(GameIcons.Fit, if (full) "Exit full screen" else "Full screen") { full = !full }
        },
    ) { g, s ->
        val human = g.passAndPlay || g.turn == 1
        val playable = !g.ended && human && !s.thinking && !s.busy
        var pick by remember(g) { mutableStateOf<TriPick?>(null) }
        var ask by remember(g) { mutableStateOf<List<TriMove>?>(null) }
        val legal = remember(g) { g.legalMoves() }
        val shifts = remember(legal) { legal.filterIsInstance<TriMove.Shift>().groupBy { it.board } }
        PlayBoard(
            full, { full = false }, s.canUndo && !s.busy, vm::undo,
            above = {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InfoChip(triStatus(g, s.thinking), emphasized = true)
                    InfoChip(if (g.passAndPlay) "White" else "You (white)")
                    InfoChip(if (g.passAndPlay) "Black" else "Computer (black)")
                    if (!g.ended && g.inCheck()) InfoChip("Check!", emphasized = true)
                }
            },
            below = {
                if (playable && shifts.isNotEmpty()) {
                    val colors = LocalGameLook.current.colors
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        shifts.keys.sorted().forEach { board ->
                            FilterChip(
                                selected = pick == TriPick.Board(board),
                                onClick = { pick = if (pick == TriPick.Board(board)) null else TriPick.Board(board) },
                                label = { Text(g.label(board)) },
                                colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface),
                                modifier = Modifier.heightIn(min = 40.dp),
                            )
                        }
                        val board = (pick as? TriPick.Board)?.index
                        if (board != null) {
                            shifts[board].orEmpty().groupBy { it.pin to it.inverted }.values.forEach { group ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        if (group.size > 1) ask = group
                                        else {
                                            vm.play { it.play(group.first()) }
                                            pick = null
                                        }
                                    },
                                    label = { Text(shiftName(g, group.first())) },
                                    modifier = Modifier.heightIn(min = 40.dp),
                                )
                            }
                        }
                    }
                }
            },
        ) {
            TriDTable(camera, g, legal, shifts, pick, playable, onPick = { pick = it }, onPlay = { move ->
                val choices = triChoices(legal, move)
                if (choices.size > 1) ask = choices
                else {
                    vm.play { it.play(move) }
                    pick = null
                }
            })
        }
        val pending = ask
        if (pending != null) {
            val order = listOf(TriD.QUEEN, TriD.ROOK, TriD.BISHOP, TriD.KNIGHT)
            AlertDialog(
                onDismissRequest = { ask = null },
                title = { Text("Promote the pawn") },
                text = { Text("Choose the piece.") },
                confirmButton = {
                    Column {
                        pending.sortedBy { order.indexOf(triPromo(it)) }.forEach { move ->
                            TextButton(
                                onClick = {
                                    vm.play { it.play(move) }
                                    ask = null
                                    pick = null
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(triPromoWord(triPromo(move))) }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { ask = null }) { Text("Cancel") }
                },
            )
        }
    }
}

private fun triChoices(legal: List<TriMove>, move: TriMove): List<TriMove> = when (move) {
    is TriMove.Slide -> legal.filter { it is TriMove.Slide && it.from == move.from && it.to == move.to }
    is TriMove.Passant -> legal.filter { it is TriMove.Passant && it.from == move.from && it.land == move.land }
    is TriMove.Shift -> legal.filter { it is TriMove.Shift && it.board == move.board && it.pin == move.pin && it.inverted == move.inverted }
    else -> listOf(move)
}

private fun triPromo(move: TriMove): Int = when (move) {
    is TriMove.Slide -> move.promo
    is TriMove.Passant -> move.promo
    is TriMove.Shift -> move.promo
    is TriMove.Castle -> TriD.QUEEN
}

private fun triPromoWord(kind: Int) = when (kotlin.math.abs(kind)) {
    TriD.QUEEN -> "Queen"
    TriD.ROOK -> "Rook"
    TriD.BISHOP -> "Bishop"
    TriD.KNIGHT -> "Knight"
    else -> "Queen"
}

@Composable
private fun TriDTable(
    camera: BoardCamera,
    g: TriD,
    legal: List<TriMove>,
    shifts: Map<Int, List<TriMove.Shift>>,
    pick: TriPick?,
    playable: Boolean,
    onPick: (TriPick?) -> Unit,
    onPlay: (TriMove) -> Unit,
) {
    val c = LocalGameLook.current.colors
    val measurer = rememberTextMeasurer()
    val cells = remember(g.squares) { g.squares.map { TriCell(it, it.wx(), it.wy(), it.wz()) } }
    BoardWithViews(camera, n = 6, peakZ = 10.1f, margin = 0.7f, rows = 10f) { frame ->
        val order = cells.sortedByDescending { frame.depth(it.x, it.y, it.z) }
        order.forEach { cell ->
            val piece = g.pieceAt(cell.sq)
            val moves = if (pick is TriPick.Man && pick.sq == cell.sq) emptyList() else manMoves(legal, cell.sq, g.turn)
            val dest = destOn(g, legal, shifts, pick, cell.sq)
            val at = frame.at(cell.x, cell.y, cell.z)
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp).semantics {
                contentDescription = say(
                    "${TriD.name(cell.sq)}, " + when {
                        piece > 0 -> "White ${TriD.pieceName(piece)}"
                        piece < 0 -> "Black ${TriD.pieceName(piece)}"
                        dest != null -> "legal move"
                        else -> "empty"
                    },
                )
                if (pick is TriPick.Man && pick.sq == cell.sq) stateDescription = say("Selected")
                if (playable && (dest != null || moves.isNotEmpty())) onClick {
                    tapTri(g, legal, shifts, pick, cell.sq, onPick, onPlay)
                    true
                }
            })
        }
        Canvas(Modifier.matchParentSize().pointerInput(g, pick, playable) {
            detectTapGestures { pos ->
                if (!playable) return@detectTapGestures
                val hit = order.asReversed().firstOrNull { cell ->
                    val piece = g.pieceAt(cell.sq)
                    val lift = if (pick is TriPick.Man && pick.sq == cell.sq) TriLift else 0f
                    val tall = if (piece == 0) cell.z else cell.z + lift + chessRise(piece)
                    floatSquareHit(frame, pos, cell.x, cell.y, cell.z, tall, frame.unitAt(cell.x, cell.y, cell.z) * 0.34f)
                } ?: return@detectTapGestures
                tapTri(g, legal, shifts, pick, hit.sq, onPick, onPlay)
            }
        }) {
            val ink = Color(0xFFF1DFC0)
            order.forEach { cell ->
                val light = (cell.sq.f + cell.sq.r) % 2 == 1
                val base = if (light) c.boardLight else c.boardDark
                val marked = destOn(g, legal, shifts, pick, cell.sq) != null
                val selected = pick is TriPick.Man && pick.sq == cell.sq
                val last = cell.sq.key == g.lastFrom || cell.sq.key == g.lastTo
                val tint = when {
                    selected -> 0.62f
                    marked -> 0.34f
                    last -> 0.28f
                    else -> 0f
                }
                drawFloatSquare(frame, cell.x, cell.y, cell.z, if (tint > 0f) lerp(base, c.highlight, tint) else base)
            }
            for (file in 0 until 6) {
                val name = ('a' + file).toString()
                drawBoardLabel(measurer, frame, name, file + 0.5f, -0.35f, 0.2f, ink)
                drawBoardLabel(measurer, frame, name, file + 0.5f, 10.35f, 0.2f, ink)
            }
            for (rank in 0..9) {
                val name = rank.toString()
                val y = (9 - rank) + 0.5f
                drawBoardLabel(measurer, frame, name, -0.4f, y, 0.2f, ink)
                drawBoardLabel(measurer, frame, name, 6.4f, y, 0.2f, ink)
            }
            order.forEach { cell ->
                val piece = g.pieceAt(cell.sq)
                val lift = if (pick is TriPick.Man && pick.sq == cell.sq) TriLift else 0f
                if (piece != 0) {
                    drawChessMan(frame, cell.x, cell.y, cell.z, piece, if (piece > 0) ChessIvory else ChessEbony, lift)
                }
                val dest = destOn(g, legal, shifts, pick, cell.sq)
                when {
                    pick is TriPick.Man && pick.sq == cell.sq && piece != 0 -> drawRing(
                        frame, cell.x, cell.y, cell.z + lift + chessRise(piece) * 0.55f, c.highlight, radiusScale = 0.48f,
                    )
                    dest != null && piece != 0 -> drawRing(
                        frame, cell.x, cell.y, cell.z + chessRise(piece) * 0.5f, c.highlight,
                    )
                    dest != null -> drawDot(frame, cell.x, cell.y, cell.z, c.highlight, radius = 0.18f)
                }
            }
            if (g.lastFrom >= 0 && g.lastTo >= 0) {
                val from = TriD.Sq.of(g.lastFrom)
                val to = TriD.Sq.of(g.lastTo)
                drawLine(
                    c.highlight,
                    frame.at(from.wx(), from.wy(), from.wz() + 0.08f),
                    frame.at(to.wx(), to.wy(), to.wz() + 0.08f),
                    strokeWidth = frame.cell * 0.06f,
                    cap = StrokeCap.Round,
                )
            }
            if (!g.ended && g.inCheck()) {
                val king = g.board.indexOfFirst { it == TriD.KING * g.turn }
                if (king >= 0) {
                    val sq = TriD.Sq.of(king)
                    drawFlatDisk(frame, sq.wx(), sq.wy(), sq.wz() + 0.06f, 0.46f, Color(0xFFE53935), strokePx = frame.cell * 0.08f)
                }
            }
        }
    }
}

private fun tapTri(
    g: TriD,
    legal: List<TriMove>,
    shifts: Map<Int, List<TriMove.Shift>>,
    pick: TriPick?,
    sq: TriD.Sq,
    onPick: (TriPick?) -> Unit,
    onPlay: (TriMove) -> Unit,
) {
    val aimed = destOn(g, legal, shifts, pick, sq)
    if (aimed != null) {
        onPlay(aimed)
        return
    }
    val piece = g.pieceAt(sq)
    val mine = if (piece > 0) 1 else if (piece < 0) -1 else 0
    if (mine == g.turn && manMoves(legal, sq, g.turn).isNotEmpty()) {
        onPick(if (pick is TriPick.Man && pick.sq == sq) null else TriPick.Man(sq))
    } else onPick(null)
}

private fun destOn(g: TriD, legal: List<TriMove>, shifts: Map<Int, List<TriMove.Shift>>, pick: TriPick?, sq: TriD.Sq): TriMove? {
    return when (pick) {
        is TriPick.Man -> manMoves(legal, pick.sq, g.turn).firstOrNull { land(it, g.turn) == sq }
        is TriPick.Board -> shifts[pick.index].orEmpty().firstOrNull { g.pieceAt(sq) == 0 && sq in g.landing(it) }
        null -> null
    }
}

private fun manMoves(legal: List<TriMove>, sq: TriD.Sq, turn: Int): List<TriMove> = legal.filter { move ->
    when (move) {
        is TriMove.Slide -> move.from == sq
        is TriMove.Passant -> move.from == sq
        is TriMove.Castle -> (turn == 1 && sq == TriD.WK_HOME) || (turn == -1 && sq == TriD.BK_HOME)
        is TriMove.Shift -> false
    }
}

private fun land(move: TriMove, turn: Int): TriD.Sq? = when (move) {
    is TriMove.Slide -> move.to
    is TriMove.Passant -> move.land
    is TriMove.Castle -> when {
        turn == 1 && move.kingSide -> TriD.WR_HOME
        turn == 1 -> TriD.WQ_HOME
        move.kingSide -> TriD.BR_HOME
        else -> TriD.BQ_HOME
    }
    is TriMove.Shift -> null
}

private fun shiftName(g: TriD, move: TriMove.Shift): String {
    val white = g.owners[move.board] == 1
    val fromRank = TriD.PINS[g.pins[move.board]].rank
    val toRank = TriD.PINS[move.pin].rank
    val ahead = if (white) toRank - fromRank else fromRank - toRank
    val flipped = move.inverted != ((g.flip and (1 shl move.board)) != 0)
    val step = when (ahead) {
        1 -> "Ahead 1"
        2 -> "Ahead 2"
        -1 -> "Back 1"
        -2 -> "Back 2"
        else -> "Flip"
    }
    return if (ahead == 0 || step == "Flip") "Flip" else if (flipped) "$step · Flip" else step
}

private fun triStatus(g: TriD, thinking: Boolean): String = when {
    g.ended && g.draw -> "Draw"
    g.ended && g.winner == 1 -> if (g.passAndPlay) "White wins" else "You won!"
    g.ended && g.winner == -1 -> if (g.passAndPlay) "Black wins" else "Computer won"
    g.passAndPlay && g.turn == 1 -> "White's turn"
    g.passAndPlay -> "Black's turn"
    g.turn == 1 -> "Your turn"
    thinking -> "Computer is thinking…"
    else -> "Computer's turn"
}

private fun TriD.Sq.wx() = f + 0.5f
private fun TriD.Sq.wy() = (9 - r) + 0.5f
private fun TriD.Sq.wz() = (z - 1) * 1.42f + 0.2f

private data class TriCell(val sq: TriD.Sq, val x: Float, val y: Float, val z: Float)

private sealed interface TriPick {
    data class Man(val sq: TriD.Sq) : TriPick
    data class Board(val index: Int) : TriPick
}

private const val TriLift = 0.18f
