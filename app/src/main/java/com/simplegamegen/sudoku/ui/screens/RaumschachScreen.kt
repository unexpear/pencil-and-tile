package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import com.simplegamegen.sudoku.tabletop.RaumMove
import com.simplegamegen.sudoku.tabletop.Raumschach
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
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.roundToInt

private val RaumViews = listOf(
    BoardView("Stack", yaw = 14f, pitch = 50f, distance = 1.75f),
    BoardView("Side", yaw = 72f, pitch = 32f, distance = 1.95f),
    BoardView("Top", yaw = 0f, pitch = 84f, distance = 1.85f),
)

private val RaumStrength = listOf(
    "Moves almost at random",
    "Takes material when it sees it",
    "Looks a move ahead",
    "Looks two moves ahead",
    "Pass the phone. White moves first.",
)

private const val RaumRules =
    "Raumschach on a 5×5×5 cube, in Maack's array as kept by the International Raumschach Federation. Levels are A to E, files a to e, ranks 1 to 5. " +
        "You play White and move first, or pass the phone. A rook moves through the faces, a bishop through the edges, and a unicorn through the corners. " +
        "The queen uses all three, the king one step of the queen, and the knight leaps two squares on one axis and one on another. " +
        "A pawn steps one empty square forward through a face — up a rank, or up a level for White — and captures one square diagonally forward through an edge. " +
        "It does not capture through a corner, and it has no double step and no en passant. A pawn promotes to a queen on White's rank 5 of level E, or Black's rank 1 of level A. " +
        "Checkmate wins. Stalemate, the same position three times, or 50 moves each with no capture and no pawn move, is a draw. Tap a piece, then a highlighted square."

val RaumschachSetup: (PuzzleFactory) -> PlaySetup<Raumschach> = { factory ->
    PlaySetup(
        rules = RaumRules,
        settingTitle = "Computer strength",
        settings = Raumschach.NAMES,
        describe = { RaumStrength[it] },
        settingOf = { it.setting },
        inProgress = { !it.ended && (it.lastFrom >= 0 || it.board != Raumschach.OPENING) },
        subtitle = {
            val who = if (it.passAndPlay) "two players" else Raumschach.NAMES[it.setting]
            val check = if (it.inCheck() && !it.ended) " · Check!" else ""
            "$who$check"
        },
        create = { i -> { factory.custom("raumschach:$i", { seed -> Raumschach.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else if (g.passAndPlay) Outcome(Result.FINISHED)
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW })
        },
    )
}

@Composable
fun RaumschachScreen(nav: NavController, vm: PlayViewModel<Raumschach>, factory: PuzzleFactory) {
    val camera = rememberBoardCamera("raum_views", RaumViews, emptyList())
    var full by rememberSaveable { mutableStateOf(false) }
    PlayShell(
        nav, vm, GameId.RAUMSCHACH, remember(factory) { RaumschachSetup(factory) },
        scroll = false,
        tight = true,
        tools = { _, _ ->
            ToolButton(GameIcons.Fit, if (full) "Exit full screen" else "Full screen") { full = !full }
        },
    ) { g, s ->
        val human = g.passAndPlay || g.turn == 1
        val playable = !g.ended && human && !s.thinking && !s.busy
        var selected by remember(g) { mutableStateOf<Int?>(null) }
        val legal = remember(g) { g.legalMoves() }
        PlayBoard(
            full, { full = false }, s.canUndo && !s.busy, vm::undo,
            above = {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InfoChip(raumStatus(g, s.thinking), emphasized = true)
                    InfoChip(if (g.passAndPlay) "White" else "You (white)")
                    InfoChip(if (g.passAndPlay) "Black" else "Computer (black)")
                    if (!g.ended && g.inCheck()) InfoChip("Check!", emphasized = true)
                }
            },
        ) {
            RaumTable(camera, g, legal, selected, playable, onSelect = { selected = it }, onPlay = { move ->
                vm.play { it.play(move) }
                selected = null
            })
        }
    }
}

@Composable
private fun RaumTable(
    camera: BoardCamera,
    g: Raumschach,
    legal: List<RaumMove>,
    selected: Int?,
    playable: Boolean,
    onSelect: (Int?) -> Unit,
    onPlay: (RaumMove) -> Unit,
) {
    val c = LocalGameLook.current.colors
    val measurer = rememberTextMeasurer()
    BoardWithViews(camera, n = 5, peakZ = 7.6f, margin = 0.75f, rows = 7.6f) { frame ->
        val order = (0 until 125).sortedByDescending { i ->
            val (x, y, z) = raumAt(i)
            frame.depth(x, y, z)
        }
        order.forEach { i ->
            val (x, y, z) = raumAt(i)
            val piece = g.board[i]
            val dest = legal.firstOrNull { it.from == selected && it.to == i }
            val movable = playable && legal.any { it.from == i }
            val at = frame.at(x, y, z)
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp).semantics {
                contentDescription = say(
                    "${Raumschach.name(i)}, " + when {
                        piece > 0 -> "White ${Raumschach.pieceName(piece)}"
                        piece < 0 -> "Black ${Raumschach.pieceName(piece)}"
                        dest != null -> "legal move"
                        else -> "empty"
                    },
                )
                if (selected == i) stateDescription = say("Selected")
                if (playable && (dest != null || movable)) onClick {
                    when {
                        dest != null -> onPlay(dest)
                        movable -> onSelect(if (selected == i) null else i)
                        else -> onSelect(null)
                    }
                    true
                }
            })
        }
        Canvas(Modifier.matchParentSize().pointerInput(g, selected, playable) {
            detectTapGestures { pos ->
                if (!playable) return@detectTapGestures
                val hit = order.asReversed().firstOrNull { i ->
                    val (x, y, z) = raumAt(i)
                    val piece = g.board[i]
                    val lift = if (selected == i) RaumLift else 0f
                    val tall = if (piece == 0) z else z + lift + chessRise(piece)
                    floatSquareHit(frame, pos, x, y, z, tall, frame.unitAt(x, y, z) * 0.34f)
                } ?: return@detectTapGestures
                val dest = legal.firstOrNull { it.from == selected && it.to == hit }
                val movable = legal.any { it.from == hit }
                when {
                    dest != null -> onPlay(dest)
                    movable -> onSelect(if (selected == hit) null else hit)
                    else -> onSelect(null)
                }
            }
        }) {
            val ink = Color(0xFFF1DFC0)
            order.forEach { i ->
                val (x, y, z) = raumAt(i)
                val light = (Raumschach.file(i) + Raumschach.rank(i) + Raumschach.level(i)) % 2 == 0
                val base = if (light) c.boardLight else c.boardDark
                val dest = legal.any { it.from == selected && it.to == i }
                val tint = when {
                    selected == i -> 0.62f
                    dest -> 0.34f
                    g.lastFrom == i || g.lastTo == i -> 0.28f
                    else -> 0f
                }
                drawFloatSquare(frame, x, y, z, if (tint > 0f) lerp(base, c.highlight, tint) else base)
            }
            val near = raumAt(Raumschach.idx(0, 0, 0))
            for (file in 0 until 5) {
                val (x, _, z) = raumAt(Raumschach.idx(0, 0, file))
                drawBoardLabel(measurer, frame, ('a' + file).toString(), x, near.second + 0.85f, z, ink)
            }
            for (rank in 0 until 5) {
                val (_, y, z) = raumAt(Raumschach.idx(0, rank, 0))
                drawBoardLabel(measurer, frame, (rank + 1).toString(), -0.45f, y, z, ink)
            }
            for (level in 0 until 5) {
                val (_, y, z) = raumAt(Raumschach.idx(level, 0, 0))
                drawBoardLabel(measurer, frame, ('A' + level).toString(), -0.45f, y + 0.72f, z, ink)
            }
            order.forEach { i ->
                val piece = g.board[i]
                val (x, y, z) = raumAt(i)
                val lift = if (selected == i) RaumLift else 0f
                if (piece != 0) {
                    drawChessMan(frame, x, y, z, piece, if (piece > 0) ChessIvory else ChessEbony, lift)
                }
                val dest = legal.any { it.from == selected && it.to == i }
                when {
                    selected == i && piece != 0 -> drawRing(
                        frame, x, y, z + lift + chessRise(piece) * 0.55f, c.highlight, radiusScale = 0.48f,
                    )
                    dest && piece != 0 -> drawRing(frame, x, y, z + chessRise(piece) * 0.5f, c.highlight)
                    dest -> drawDot(frame, x, y, z, c.highlight, radius = 0.18f)
                }
            }
            if (g.lastFrom >= 0 && g.lastTo >= 0) {
                val (x0, y0, z0) = raumAt(g.lastFrom)
                val (x1, y1, z1) = raumAt(g.lastTo)
                drawLine(
                    c.highlight,
                    frame.at(x0, y0, z0 + 0.08f),
                    frame.at(x1, y1, z1 + 0.08f),
                    strokeWidth = frame.cell * 0.06f,
                    cap = StrokeCap.Round,
                )
            }
            if (!g.ended && g.inCheck()) {
                val king = g.board.indexOfFirst { it == Raumschach.KING * g.turn }
                if (king >= 0) {
                    val (x, y, z) = raumAt(king)
                    drawFlatDisk(frame, x, y, z + 0.06f, 0.46f, Color(0xFFE53935), strokePx = frame.cell * 0.08f)
                }
            }
        }
    }
}

private fun raumStatus(g: Raumschach, thinking: Boolean): String = when {
    g.ended && g.draw -> "Draw"
    g.ended && g.winner == 1 -> if (g.passAndPlay) "White wins" else "You won!"
    g.ended && g.winner == -1 -> if (g.passAndPlay) "Black wins" else "Computer won"
    g.passAndPlay && g.turn == 1 -> "White's turn"
    g.passAndPlay -> "Black's turn"
    g.turn == 1 -> "Your turn"
    thinking -> "Computer is thinking…"
    else -> "Computer's turn"
}

/** Level A sits toward the player. Higher levels step back so each floor's near edge stays visible. */
private fun raumAt(i: Int): Triple<Float, Float, Float> {
    val file = Raumschach.file(i)
    val rank = Raumschach.rank(i)
    val level = Raumschach.level(i)
    val x = file + 0.5f
    val y = (4 - rank) + (4 - level) * 0.62f + 0.5f
    val z = level * 1.48f + 0.22f
    return Triple(x, y, z)
}

private const val RaumLift = 0.18f
