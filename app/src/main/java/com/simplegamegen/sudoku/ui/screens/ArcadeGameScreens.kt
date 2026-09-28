package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.components.ZoomBox
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.simplegamegen.sudoku.arcade.Game2048
import com.simplegamegen.sudoku.arcade.Piece
import com.simplegamegen.sudoku.arcade.Slide
import com.simplegamegen.sudoku.arcade.TetrasGame
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.abs
import kotlinx.coroutines.delay
import com.simplegamegen.sudoku.ui.i18n.say

/** A large square control with a drawn icon, rotated as needed. */
@Composable
private fun PadButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, rotation: Float = 0f, onClick: () -> Unit) {
    val c = LocalGameLook.current.colors
    Surface(onClick = onClick, modifier = modifier.height(58.dp).semantics { contentDescription = say(label) },
        color = c.accentSoft, contentColor = c.accent, shape = MaterialTheme.shapes.medium) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp).rotate(rotation)) }
    }
}

// ---------------- 2048 ----------------

private fun tileColor(v: Int): Color = when (v) {
    2 -> Color(0xFFEEE4DA); 4 -> Color(0xFFEDE0C8); 8 -> Color(0xFFF2B179); 16 -> Color(0xFFF59563)
    32 -> Color(0xFFF67C5F); 64 -> Color(0xFFF65E3B); 128 -> Color(0xFFEDCF72); 256 -> Color(0xFFEDCC61)
    512 -> Color(0xFFEDC850); 1024 -> Color(0xFFEDC53F); 2048 -> Color(0xFFEDC22E); else -> Color(0xFF3C3A32)
}

val Setup2048: (PuzzleFactory) -> PlaySetup<Game2048> = { factory ->
    PlaySetup(
        rules = "Swipe or use the arrows to slide every tile. Two tiles with the same number merge into one with their sum. " +
            "A new 2 or 4 appears after each move. Reach the goal tile, then keep going for a high score until the board locks up.",
        settingTitle = "Board", settings = Game2048.SIZES.map { "$it × $it" },
        describe = { "Goal tile ${Game2048.GOALS[it]}" },
        settingOf = { it.setting }, inProgress = { !it.over && it.score > 0 },
        subtitle = { "${it.size} × ${it.size} · goal ${it.goal}" },
        create = { i -> { factory.custom("2048:$i", { seed -> Game2048.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.over) Outcome(if (g.reached) Result.WON else Result.LOST, g.score.toLong()) else null },
    )
}

@Composable
fun Game2048Screen(nav: NavController, vm: PlayViewModel<Game2048>, factory: PuzzleFactory) {
    val measurer = rememberTextMeasurer()
    val look = LocalGameLook.current
    fun go(dir: Slide) = vm.play { it.slide(dir) }
    PlayShell(nav, vm, GameId.G2048, remember(factory) { Setup2048(factory) }) { g, _ ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("Score ${g.score}", emphasized = true)
            InfoChip("Best tile ${g.best}")
            if (g.reached) InfoChip("Goal reached!", icon = GameIcons.Check)
        }
        if (g.over) Text("No moves left. Final score ${g.score}.", color = look.colors.danger, style = MaterialTheme.typography.titleMedium)
        val latest by rememberUpdatedState(g)
        ZoomBox(Modifier.fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)
                .semantics { contentDescription = say("2048 board") + ". " + latest.tiles.chunked(latest.size).joinToString(". ") { row -> row.joinToString(" ") { if (it == 0) say("empty") else "$it" } } }
                .pointerInput(Unit) {
                    var total = Offset.Zero
                    detectDragGestures(onDragStart = { total = Offset.Zero }, onDragEnd = {
                        if (maxOf(abs(total.x), abs(total.y)) > 40f)
                            go(if (abs(total.x) > abs(total.y)) (if (total.x > 0) Slide.RIGHT else Slide.LEFT) else (if (total.y > 0) Slide.DOWN else Slide.UP))
                    }) { change, drag -> change.consume(); total += drag }
                }) {
                val n = g.size
                val gap = size.width * 0.025f
                val cell = (size.width - gap * (n + 1)) / n
                drawRoundRect(Color(0xFFBBADA0), cornerRadius = CornerRadius(gap * 2))
                g.tiles.forEachIndexed { i, v ->
                    val o = Offset(gap + (i % n) * (cell + gap), gap + (i / n) * (cell + gap))
                    drawRoundRect(if (v == 0) Color(0xFFCDC1B4) else tileColor(v), o, Size(cell, cell), CornerRadius(gap * 1.4f))
                    if (v != 0) drawRoundRect(Color.White.copy(alpha = if (v <= 4) 0.45f else 0.22f), o, Size(cell, cell * 0.16f), CornerRadius(gap * 1.4f, gap * 0.3f))
                    if (v != 0) {
                        val digits = v.toString().length
                        val style = TextStyle(color = if (v <= 4) Color(0xFF776E65) else Color(0xFFF9F6F2), fontWeight = FontWeight.Bold,
                            fontSize = (cell * (if (digits <= 2) 0.46f else if (digits == 3) 0.38f else 0.3f) / density).sp)
                        val l = measurer.measure(v.toString(), style)
                        drawText(l, topLeft = o + Offset((cell - l.size.width) / 2, (cell - l.size.height) / 2))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PadButton(GameIcons.Back, "Slide left", Modifier.weight(1f), 0f) { go(Slide.LEFT) }
            PadButton(GameIcons.Back, "Slide up", Modifier.weight(1f), 90f) { go(Slide.UP) }
            PadButton(GameIcons.Back, "Slide down", Modifier.weight(1f), -90f) { go(Slide.DOWN) }
            PadButton(GameIcons.Back, "Slide right", Modifier.weight(1f), 180f) { go(Slide.RIGHT) }
        }
    }
}

// ---------------- Tetras ----------------

private val PieceColors = listOf(Color(0xFF3FC6E0), Color(0xFFF2C94C), Color(0xFFA066D3), Color(0xFF5BBF5B), Color(0xFFE5534B), Color(0xFF4A7BD9), Color(0xFFF08A3C))

private fun DrawScope.block(o: Offset, s: Float, color: Color) {
    val inset = s * 0.07f
    val origin = o + Offset(inset, inset)
    val side = s - inset * 2
    drawRect(color, origin, Size(side, side))
    val e = side * 0.16f
    drawRect(lerp(color, Color.White, 0.4f), origin, Size(side, e))
    drawRect(lerp(color, Color.White, 0.22f), origin, Size(e, side))
    drawRect(lerp(color, Color.Black, 0.35f), origin + Offset(0f, side - e), Size(side, e))
    drawRect(lerp(color, Color.Black, 0.22f), origin + Offset(side - e, 0f), Size(e, side))
}

val TetrasSetup: (PuzzleFactory) -> PlaySetup<TetrasGame> = { factory ->
    PlaySetup(
        rules = "Blocks fall one at a time. Move and rotate them to fill whole rows; full rows disappear and score. " +
            "Tap the board to rotate, drag sideways to move, swipe down to drop, or use the buttons. The game speeds up every 10 rows.",
        settingTitle = "Starting speed", settings = LevelNames,
        describe = { "Starts at level ${TetrasGame.START_LEVELS[it]}" },
        settingOf = { it.setting }, inProgress = { !it.over && it.index > 1 },
        subtitle = { "Level ${it.level} · ${it.lines} lines" },
        create = { i -> { factory.custom("tetras:$i", { seed -> TetrasGame.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.over) Outcome(Result.FINISHED, g.score.toLong()) else null },
    )
}

@Composable
fun TetrasScreen(nav: NavController, vm: PlayViewModel<TetrasGame>, factory: PuzzleFactory) {
    val look = LocalGameLook.current
    var paused by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.flush() }
    }
    // Saves when a piece locks; plain gravity steps aren't worth a write.
    fun act(transform: (TetrasGame) -> TetrasGame?) {
        val before = vm.state.value.game
        vm.play(record = false, persist = false, transform = transform)
        val after = vm.state.value.game
        if (before != null && after != null && (after.index != before.index || after.over != before.over)) vm.flush()
    }
    PlayShell(nav, vm, GameId.TETRAS, remember(factory) { TetrasSetup(factory) }, undoable = false, tools = { g, _ ->
        ToolButton(if (paused) GameIcons.Pass else GameIcons.Timer, if (paused) "Resume" else "Pause", enabled = !g.over, active = paused) { paused = !paused; vm.flush() }
        ToolButton(GameIcons.Back, "Left", enabled = !g.over && !paused) { act { it.shift(-1) } }
        ToolButton(GameIcons.Restart, "Turn", enabled = !g.over && !paused) { act { it.rotate() } }
        ToolButton(GameIcons.Pass, "Right", enabled = !g.over && !paused) { act { it.shift(1) } }
        ToolButton(GameIcons.Draw, "Drop", enabled = !g.over && !paused) { act { it.hardDrop() } }
    }) { g, s ->
        val running = resumed && !paused && !g.over && !s.busy
        val interval = g.interval
        LaunchedEffect(running, g.level) {
            while (running) { delay(interval); act { it.tick() } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("Score ${g.score}", emphasized = true)
            InfoChip("Lines ${g.lines}")
            InfoChip("Level ${g.level}")
        }
        if (g.over) Text("Game over. Final score ${g.score}.", color = look.colors.danger, style = MaterialTheme.typography.titleMedium)
        else if (paused) Text("Paused", color = look.colors.muted, style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = minOf((maxWidth - 88.dp) / TetrasGame.W, 28.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Canvas(Modifier.size(cell * TetrasGame.W, cell * TetrasGame.H)
                    .semantics { contentDescription = say("Tetras board, ${g.piece?.let { Piece.NAMES[it.type] } ?: "no"} piece falling") }
                    .pointerInput(paused) {
                        detectTapGestures { if (!paused) act { it.rotate() } }
                    }
                    .pointerInput(paused) {
                        var carried = 0f; var down = 0f
                        detectDragGestures(onDragStart = { carried = 0f; down = 0f }, onDragEnd = { if (down > 120f && !paused) act { it.hardDrop() } }) { change, drag ->
                            change.consume()
                            if (paused) return@detectDragGestures
                            carried += drag.x; down += drag.y
                            val step = size.width / TetrasGame.W.toFloat()
                            if (step < 1f) return@detectDragGestures
                            while (abs(carried) >= step) { act { it.shift(if (carried > 0) 1 else -1) }; carried -= step * kotlin.math.sign(carried) }
                        }
                    }) {
                    val s = size.width / TetrasGame.W
                    drawRect(Color(0xFF2A241C))
                    drawRect(Color(0xFF12141A), Offset(s * 0.35f, s * 0.35f), Size(size.width - s * 0.7f, size.height - s * 0.7f))
                    for (x in 1 until TetrasGame.W) drawLine(Color(0xFF23252D), Offset(x * s, 0f), Offset(x * s, size.height))
                    for (y in 1 until TetrasGame.H) drawLine(Color(0xFF23252D), Offset(0f, y * s), Offset(size.width, y * s))
                    g.board.forEachIndexed { i, v -> if (v != 0) block(Offset(i % TetrasGame.W * s, i / TetrasGame.W * s), s, PieceColors[v - 1]) }
                    val falling = g.piece
                    if (falling != null) {
                        g.ghost()?.cells()?.forEach { (x, y) -> if (y >= 0) drawRect(PieceColors[falling.type].copy(alpha = 0.45f), Offset(x * s + s * 0.18f, y * s + s * 0.18f), Size(s * 0.64f, s * 0.64f)) }
                        falling.cells().forEach { (x, y) -> if (y >= 0) block(Offset(x * s, y * s), s, PieceColors[falling.type]) }
                    }
                }
                Column(Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Next", style = MaterialTheme.typography.labelLarge, color = look.colors.muted)
                    g.next.forEach { type ->
                        Canvas(Modifier.size(64.dp, 40.dp).semantics { contentDescription = say("Next piece ${Piece.NAMES[type]}") }) {
                            val cells = Piece.SHAPES[type][0]
                            val piece = size.height / 3.2f
                            val minX = cells.minOf { it.first }; val maxX = cells.maxOf { it.first }
                            val minY = cells.minOf { it.second }; val maxY = cells.maxOf { it.second }
                            val o = Offset((size.width - (maxX - minX + 1) * piece) / 2, (size.height - (maxY - minY + 1) * piece) / 2)
                            cells.forEach { (x, y) -> block(o + Offset((x - minX) * piece, (y - minY) * piece), piece, PieceColors[type]) }
                        }
                    }
                }
            }
        }
    }
}
