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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.nativeCanvas
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
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.roundToInt

private val RaumViews = listOf(
    BoardView("Stack", yaw = 12f, pitch = 62f, distance = 1.48f),
    BoardView("Side", yaw = 78f, pitch = 46f, distance = 1.65f),
    BoardView("Top", yaw = 0f, pitch = 84f, distance = 1.7f),
)

private val RaumStrength = listOf(
    "Moves almost at random",
    "Takes material when it sees it",
    "Looks two moves ahead",
    "Looks three moves ahead",
    "Pass the phone. White moves first.",
)

private const val RaumRules =
    "Raumschach on a 5×5×5 cube, the normal form kept by the International Raumschach Federation from Dawson's reading of Maack. This is not Maack's 8×8×8 game, where a pawn may move in every direction. " +
        "Levels are A to E, files a to e, ranks 1 to 5. You play White and move first, or pass the phone. A rook moves through the faces, a bishop through the edges, and a unicorn through the corners. " +
        "The queen uses all three, the king one step of the queen, and the knight leaps two squares on one axis and one on another. " +
        "A pawn steps one empty square forward through a face — up a rank, or up a level for White — and captures one square diagonally forward through an edge. " +
        "It does not capture through a corner, and it has no double step, no en passant, and no castling. " +
        "A pawn promotes to a queen, rook, bishop, unicorn, or knight on White's rank 5 of level E, or Black's rank 1 of level A. " +
        "Checkmate wins. Stalemate, the same position three times, or 50 moves each with no capture and no pawn move, is a draw. Tap a piece, then a highlighted square. When a pawn promotes, choose the piece."

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
        var ask by remember(g) { mutableStateOf<List<RaumMove>?>(null) }
        var floor by rememberSaveable { mutableStateOf(-1) }
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
            below = {
                val colors = LocalGameLook.current.colors
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = floor < 0,
                        onClick = { floor = -1 },
                        label = { Text("All levels") },
                        colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface),
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    for (level in 0 until 5) {
                        FilterChip(
                            selected = floor == level,
                            onClick = { floor = if (floor == level) -1 else level },
                            label = { Text(('A' + level).toString()) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface),
                            modifier = Modifier.heightIn(min = 48.dp).semantics {
                                contentDescription = say("Level ${'A' + level}")
                            },
                        )
                    }
                }
            },
        ) {
            RaumTable(camera, g, legal, selected, floor, playable, onSelect = { selected = it }, onFloor = { floor = it }, onPlay = { move ->
                val choices = legal.filter { it.from == move.from && it.to == move.to }
                if (choices.size > 1) ask = choices
                else {
                    vm.play { it.play(move) }
                    selected = null
                }
            })
        }
        val pending = ask
        if (pending != null) {
            val order = listOf(Raumschach.QUEEN, Raumschach.ROOK, Raumschach.BISHOP, Raumschach.UNICORN, Raumschach.KNIGHT)
            AlertDialog(
                onDismissRequest = { ask = null },
                title = { Text("Promote the pawn") },
                text = { Text("Choose the piece.") },
                confirmButton = {
                    Column {
                        pending.sortedBy { order.indexOf(it.promo) }.forEach { move ->
                            TextButton(
                                onClick = {
                                    vm.play { it.play(move) }
                                    ask = null
                                    selected = null
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(raumPromo(move.promo)) }
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

private fun raumPromo(kind: Int) = when (kotlin.math.abs(kind)) {
    Raumschach.QUEEN -> "Queen"
    Raumschach.ROOK -> "Rook"
    Raumschach.BISHOP -> "Bishop"
    Raumschach.UNICORN -> "Unicorn"
    Raumschach.KNIGHT -> "Knight"
    else -> "Queen"
}

@Composable
private fun RaumTable(
    camera: BoardCamera,
    g: Raumschach,
    legal: List<RaumMove>,
    selected: Int?,
    floor: Int,
    playable: Boolean,
    onSelect: (Int?) -> Unit,
    onFloor: (Int) -> Unit,
    onPlay: (RaumMove) -> Unit,
) {
    val c = LocalGameLook.current.colors
    val measurer = rememberTextMeasurer()
    val forward = remember(legal, selected, floor) {
        val dests = legal.filter { it.from == selected }.map { it.to }.toSet()
        (0 until 125).filter { i ->
            floor < 0 || Raumschach.level(i) == floor || i in dests || i == selected
        }.toSet()
    }
    BoardWithViews(camera, n = 5, peakZ = 9.4f, margin = 0.85f, rows = 10.4f) { frame ->
        val order = (0 until 125).sortedByDescending { i ->
            val (x, y, z) = raumAt(i)
            frame.depth(x, y, z)
        }
        fun hitAt(pos: androidx.compose.ui.geometry.Offset, cells: List<Int>): Int? =
            cells.asReversed().firstOrNull { i ->
                val (x, y, z) = raumAt(i)
                val piece = g.board[i]
                val lift = if (selected == i) RaumLift else 0f
                val tall = if (piece == 0) z else z + lift + chessRise(piece)
                floatSquareHit(frame, pos, x, y, z, tall, frame.unitAt(x, y, z) * 0.38f)
            }
        fun tap(i: Int) {
            val dest = legal.firstOrNull { it.from == selected && it.to == i }
            val movable = legal.any { it.from == i }
            when {
                dest != null -> onPlay(dest)
                i !in forward -> {
                    onFloor(Raumschach.level(i))
                    onSelect(if (movable) i else null)
                }
                movable -> onSelect(if (selected == i) null else i)
                else -> onSelect(null)
            }
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
                if (playable && (dest != null || movable || i !in forward)) onClick {
                    tap(i)
                    true
                }
            })
        }
        Canvas(Modifier.matchParentSize().pointerInput(g, selected, floor, playable) {
            detectTapGestures { pos ->
                if (!playable) return@detectTapGestures
                val front = order.filter { it in forward }
                val back = order.filter { it !in forward }
                val hit = hitAt(pos, front) ?: hitAt(pos, back) ?: return@detectTapGestures
                tap(hit)
            }
        }) {
            val ink = Color(0xFFF1DFC0)
            fun squares(cells: List<Int>) {
                cells.forEach { i ->
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
            }
            fun men(cells: List<Int>) {
                cells.forEach { i ->
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
                        dest -> drawDot(frame, x, y, z, c.highlight, radius = 0.2f)
                    }
                }
            }
            val ghost = order.filter { it !in forward }
            val solid = order.filter { it in forward }
            if (ghost.isNotEmpty()) {
                val layer = drawContext.canvas.nativeCanvas
                layer.saveLayerAlpha(null, (0.42f * 255).toInt())
                squares(ghost)
                men(ghost)
                layer.restore()
            }
            squares(solid)
            val labeled = if (floor >= 0) floor else 0
            for (file in 0 until 5) {
                val (x, y, z) = raumAt(Raumschach.idx(labeled, 0, file))
                drawBoardLabel(measurer, frame, ('a' + file).toString(), x, y + 0.72f, z, ink)
            }
            for (rank in 0 until 5) {
                val (x, y, z) = raumAt(Raumschach.idx(labeled, rank, 0))
                drawBoardLabel(measurer, frame, (rank + 1).toString(), x - 0.78f, y, z, ink)
            }
            for (level in 0 until 5) {
                val (x, y, z) = raumAt(Raumschach.idx(level, 2, 4))
                val mark = if (floor == level) c.highlight else ink
                drawBoardLabel(measurer, frame, ('A' + level).toString(), x + 0.82f, y, z, mark)
            }
            men(solid)
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

/**
 * Level A sits toward the player. Each higher floor steps back by more than a square and sits
 * high enough that a piece can lift without meeting the board above.
 */
private fun raumAt(i: Int): Triple<Float, Float, Float> {
    val file = Raumschach.file(i)
    val rank = Raumschach.rank(i)
    val level = Raumschach.level(i)
    val x = file + 0.5f
    val y = (4 - rank) + (4 - level) * 1.15f + 0.5f
    val z = level * 1.85f + 0.22f
    return Triple(x, y, z)
}

private const val RaumLift = 0.42f
