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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.simplegamegen.sudoku.tabletop.Shogi
import com.simplegamegen.sudoku.tabletop.ShogiMove
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.BoardViews
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.tableFrame
import com.simplegamegen.sudoku.ui.assets.coversCell
import com.simplegamegen.sudoku.ui.assets.drawBoardFrame
import com.simplegamegen.sudoku.ui.assets.drawBoardLabel
import com.simplegamegen.sudoku.ui.assets.drawDot
import com.simplegamegen.sudoku.ui.assets.drawFlatDisk
import com.simplegamegen.sudoku.ui.assets.drawRing
import com.simplegamegen.sudoku.ui.assets.drawShogiPiece
import com.simplegamegen.sudoku.ui.assets.drawSquareTop
import com.simplegamegen.sudoku.ui.assets.shogiRise
import com.simplegamegen.sudoku.ui.assets.shogiScale
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.roundToInt

private val ShogiStrength = listOf(
    "Moves almost at random",
    "Takes material when it sees it",
    "Looks one move ahead",
    "Looks a little further ahead",
)

val ShogiSetup: (PuzzleFactory) -> PlaySetup<Shogi> = { factory ->
    PlaySetup(
        rules = "Shogi on a 9×9 board. Files run 9 to 1 from left to right, and ranks run 1 to 9 from the top. " +
            "You play Sente from the bottom and move first. Pieces point the way they move. " +
            "Pawns and lances go forward, knights jump two forward and one to the side, silvers and golds have their usual steps, and the rook and bishop slide. " +
            "In the far three rows a piece may promote, and a pawn, lance, or knight must promote when it could not move again. Promoted characters are red. " +
            "Captured pieces return to your hand unpromoted and can be dropped on an empty square. " +
            "A pawn cannot be dropped on a file that already has one of your unpromoted pawns, or to give checkmate. " +
            "A side with no legal move loses. The same position four times is a draw, unless one side gave check on every move of that repeat, in which case that side loses. " +
            "When both kings are in the enemy camp, Count the pieces. A rook or bishop counts 5, every other piece except the king counts 1, in the camp and in hand. " +
            "Under 24, the side that counts loses. If only the other side is under 24, that side loses. If both have 24 or more, the game is a draw.",
        settingTitle = "Computer strength",
        settings = Shogi.NAMES,
        describe = { ShogiStrength[it] },
        settingOf = { it.setting },
        inProgress = { !it.ended && (it.lastTo != null || it.board != Shogi.OPENING || it.senteHand.isNotEmpty() || it.goteHand.isNotEmpty()) },
        subtitle = { Shogi.NAMES[it.setting] },
        create = { i -> { factory.custom("shogi:$i", { seed -> Shogi(setting = i, seed = seed) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW })
        },
    )
}

private val ShogiBoardWood = Color(0xFFE4B56A)
private val ShogiLine = Color(0xFF3E2723)
private val ShogiLabel = Color(0xFFF1DFC0)

@Composable
fun ShogiScreen(nav: NavController, vm: PlayViewModel<Shogi>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    val camera = rememberBoardCamera("shogi_views")
    var full by rememberSaveable { mutableStateOf(false) }
    PlayShell(
        nav, vm, GameId.SHOGI, remember(factory) { ShogiSetup(factory) },
        scroll = false,
        tools = { _, _ ->
            ToolButton(GameIcons.Fit, if (full) "Exit full screen" else "Full screen") { full = !full }
        },
    ) { g, s ->
        DuelStatus(g.over, g.winner, g.turn, s.thinking, "You (Sente)", "Computer (Gote)")
        if (!g.ended && g.inCheck()) InfoChip("Check!", emphasized = true)
        val playable = !g.ended && g.turn == 1 && !s.thinking && !s.busy
        var selected by remember(g) { mutableIntStateOf(-1) }
        var drop by remember(g) { mutableIntStateOf(0) }
        var ask by remember(g) { mutableStateOf<List<ShogiMove>?>(null) }
        val legal = remember(g) { g.legalMoves() }
        fun play(move: ShogiMove) {
            vm.play { it.play(move) }
            selected = -1
            drop = 0
            ask = null
        }
        fun tap(index: Int) {
            val choices = when {
                drop != 0 -> legal.filter { it.drop == drop && it.to == index }
                selected >= 0 -> legal.filter { it.from == selected && it.to == index && it.drop == 0 }
                else -> emptyList()
            }
            val piece = g.board[index]
            when {
                choices.size > 1 -> ask = choices
                choices.size == 1 -> play(choices.single())
                playable && piece > 0 && legal.any { it.from == index } -> {
                    selected = if (selected == index) -1 else index
                    drop = 0
                }
                else -> {
                    selected = -1
                    drop = 0
                }
            }
        }
        HandRow("Computer's captures", g.goteHand, 0, false) {}
        PlayBoard(full, { full = false }, s.canUndo && !s.busy, vm::undo, bar = {
            if (playable && g.canImpasse()) {
                Button(
                    onClick = { vm.play { it.declareImpasse() } },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("Count the pieces") }
            }
        }) {
            ShogiTable(camera, g, legal, selected, drop, playable, onTap = ::tap)
        }
        HandRow("Your captures", g.senteHand, drop, playable) { type ->
            drop = if (drop == type) 0 else type
            selected = -1
        }
        if (playable && g.canImpasse()) {
            Button(
                onClick = { vm.play { it.declareImpasse() } },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Count the pieces") }
        }
        if (ask != null) {
            AlertDialog(
                onDismissRequest = { ask = null },
                title = { Text("Promote?") },
                text = { Text("This piece can promote, or stay as it is.") },
                confirmButton = {
                    Button(onClick = { play(ask!!.first { it.promote }) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("Promote") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { play(ask!!.first { !it.promote }) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("Don't promote") }
                },
                containerColor = c.surface,
            )
        }
    }
}

@Composable
private fun ShogiTable(
    camera: BoardCamera,
    g: Shogi,
    legal: List<ShogiMove>,
    selected: Int,
    drop: Int,
    playable: Boolean,
    onTap: (Int) -> Unit,
) {
    val c = LocalGameLook.current.colors
    val measurer = rememberTextMeasurer()
    val targets = when {
        drop != 0 -> legal.filter { it.drop == drop }.map { it.to }.toSet()
        selected >= 0 -> legal.filter { it.from == selected && it.drop == 0 }.map { it.to }.toSet()
        else -> emptySet()
    }
    BoardWithViews(camera, n = 9, peakZ = 1.2f, margin = 0.7f) { frame ->
        val top = 0.18f
        val order = (0 until 81).sortedByDescending { frame.depth((it % 9) + 0.5f, (it / 9) + 0.5f, top) }
        order.forEach { i ->
            val row = i / 9
            val col = i % 9
            val piece = g.board[i]
            val dest = i in targets
            val movable = playable && legal.any { it.from == i }
            val at = frame.at(col + 0.5f, row + 0.5f, top)
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp).semantics {
                contentDescription = say(
                    "File ${9 - col}, rank ${row + 1}, " + when {
                        piece > 0 -> "your ${Shogi.pieceName(piece)}"
                        piece < 0 -> "computer ${Shogi.pieceName(piece)}"
                        dest -> "legal move"
                        else -> "empty"
                    },
                )
                if (selected == i) stateDescription = say("Selected")
                if (playable && (dest || movable)) onClick { onTap(i); true }
            })
        }
        Canvas(Modifier.matchParentSize().pointerInput(g, selected, drop, playable) {
            detectTapGestures { pos ->
                if (!playable) return@detectTapGestures
                val hit = order.asReversed().firstOrNull { i ->
                    val piece = g.board[i]
                    val lift = if (selected == i) 0.22f else 0f
                    val tall = if (piece == 0) top else top + lift + shogiRise()
                    coversCell(frame, pos, i % 9, i / 9, top, tall, frame.unitAt(i % 9 + 0.5f, i / 9 + 0.5f, top) * 0.34f)
                } ?: return@detectTapGestures
                onTap(hit)
            }
        }) {
            drawBoardFrame(frame, top, Color(0xFF5B3A24), border = 0.55f)
            drawPath(Path().apply {
                val q = listOf(frame.at(0f, 0f, top), frame.at(9f, 0f, top), frame.at(9f, 9f, top), frame.at(0f, 9f, top))
                moveTo(q[0].x, q[0].y)
                q.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }, ShogiBoardWood)
            order.forEach { i ->
                val marked = selected == i || g.lastFrom == i || g.lastTo == i || i in targets
                if (marked) drawSquareTop(frame, i % 9, i / 9, top + 0.002f, lerp(ShogiBoardWood, c.highlight, 0.45f))
            }
            val z = top + 0.008f
            val stroke = (frame.cell * 0.04f).coerceAtLeast(1f)
            for (i in 0..9) {
                val w = if (i == 0 || i == 9) stroke * 1.5f else stroke
                drawLine(ShogiLine, frame.at(i.toFloat(), 0f, z), frame.at(i.toFloat(), 9f, z), strokeWidth = w)
                drawLine(ShogiLine, frame.at(0f, i.toFloat(), z), frame.at(9f, i.toFloat(), z), strokeWidth = w)
            }
            for (x in listOf(3f, 6f)) for (y in listOf(3f, 6f)) {
                drawFlatDisk(frame, x, y, z + 0.004f, 0.05f, ShogiLine)
            }
            for (col in 0 until 9) {
                val file = (9 - col).toString()
                drawBoardLabel(measurer, frame, file, col + 0.5f, -0.3f, top, ShogiLabel)
                drawBoardLabel(measurer, frame, file, col + 0.5f, 9.3f, top, ShogiLabel)
            }
            for (row in 0 until 9) {
                val rank = (row + 1).toString()
                drawBoardLabel(measurer, frame, rank, -0.3f, row + 0.5f, top, ShogiLabel)
                drawBoardLabel(measurer, frame, rank, 9.3f, row + 0.5f, top, ShogiLabel)
            }
            val from = g.lastFrom
            val to = g.lastTo
            if (from != null && to != null) {
                drawLine(
                    c.highlight,
                    frame.at(from % 9 + 0.5f, from / 9 + 0.5f, top + 0.02f),
                    frame.at(to % 9 + 0.5f, to / 9 + 0.5f, top + 0.02f),
                    strokeWidth = frame.cell * 0.06f,
                )
            }
            order.forEach { i ->
                val piece = g.board[i]
                if (piece == 0) return@forEach
                val row = i / 9
                val col = i % 9
                val lift = if (selected == i) 0.22f else 0f
                drawShogiPiece(
                    frame, col + 0.5f, row + 0.5f, top,
                    yours = piece > 0,
                    glyph = Shogi.glyph(piece),
                    promoted = Shogi.promoted(piece),
                    scale = shogiScale(Shogi.baseOf(piece)),
                    lift = lift,
                )
                if (selected == i || (i in targets && piece != 0)) drawRing(frame, col + 0.5f, row + 0.5f, top, c.highlight)
            }
            targets.filter { g.board[it] == 0 }.forEach { i ->
                drawDot(frame, i % 9 + 0.5f, i / 9 + 0.5f, top, c.highlight)
            }
            if (!g.ended && g.inCheck()) {
                val king = g.board.indexOfFirst { it == Shogi.KING * g.turn }
                if (king >= 0) {
                    drawFlatDisk(
                        frame, king % 9 + 0.5f, king / 9 + 0.5f, top + 0.03f, 0.46f,
                        Color(0xFFE53935), strokePx = frame.cell * 0.08f,
                    )
                }
            }
        }
    }
}


@Composable
private fun HandRow(label: String, pieces: List<Int>, selected: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.muted)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pieces.isEmpty()) Text("empty", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            pieces.distinct().sorted().forEach { type ->
                val count = pieces.count { it == type }
                val on = selected == type
                OutlinedButton(
                    onClick = { onPick(type) },
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = say("Drop ${Shogi.pieceName(type)}")
                    },
                ) {
                    Canvas(Modifier.size(36.dp, 44.dp)) {
                        val frame = tableFrame(
                            1, size.width, peakZ = 0.7f, maxHeightPx = size.height,
                            view = BoardViews.corner, margin = 0.45f,
                        )
                        drawShogiPiece(
                            frame, 0.5f, 0.5f, 0f,
                            yours = true,
                            glyph = Shogi.glyph(type),
                            promoted = false,
                            scale = shogiScale(type),
                        )
                    }
                    Text("$count", color = if (on) c.highlight else c.text)
                }
            }
        }
    }
}
