package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.grids.SlidingBlocks
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.abs

private val CarPalette = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFF8D6E63), Color(0xFF7E57C2),
    Color(0xFF66BB6A), Color(0xFF29B6F6), Color(0xFFAB47BC), Color(0xFF78909C),
    Color(0xFFFFA726), Color(0xFF42A5F5),
)

private val LevelBlurb = listOf(
    "6×6 · few vehicles · short path",
    "6×6 · more vehicles",
    "6×6 · longer path",
    "6×6 · densest jam",
)

val SlidingBlocksSetup: (PuzzleFactory) -> PlaySetup<SlidingBlocks> = { factory ->
    PlaySetup(
        rules = "Vehicles fill a jammed grid. Each is two or three cells long and can only slide along its length. " +
            "Slide any vehicle any number of free cells. The marked target must reach the exit on the right of its row. " +
            "Tap a vehicle, then a direction, or drag it along its axis.",
        settingTitle = "Difficulty",
        settings = LogicLevel.entries.map { it.label },
        describe = { LevelBlurb[it] },
        settingOf = { it.setting },
        inProgress = { !it.complete && it.moves > 0 },
        subtitle = { "${LogicLevel.entries[it.setting].label} · ${it.moves} moves · par ${it.solutionLength}" },
        create = { i ->
            {
                factory.custom("sliding:$i", { seed -> SlidingBlocks.generate(seed, LogicLevel.entries[i]) }) {
                    it.cars.joinToString(";") { c -> "${c.id},${c.row},${c.col}" }
                }
            }
        },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@Composable
fun SlidingBlocksScreen(nav: NavController, vm: PlayViewModel<SlidingBlocks>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var selected by remember { mutableIntStateOf(-1) }
    PlayShell(nav, vm, GameId.SLIDING_BLOCKS, remember(factory) { SlidingBlocksSetup(factory) }, tools = { g, s ->
        HintButton(GameId.SLIDING_BLOCKS, enabled = !g.complete && !s.busy) {
            vm.play("One vehicle slid.") { it.hint() }
        }
    }) { g, s ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.moves} moves", emphasized = true)
            InfoChip("par ${g.solutionLength}")
        }
        if (g.complete) WinBanner("The target reached the exit.")
        val latest by rememberUpdatedState(g)
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val n = g.size
                val side = min(maxWidth / (n + 1), 52.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(side * n)
                            .background(c.surface)
                            .border(2.dp, c.text)
                            .semantics { contentDescription = say("Sliding blocks board") }
                            .pointerInput(g.seed, g.cars, s.busy, g.complete) {
                                if (g.complete || s.busy) return@pointerInput
                                var total = Offset.Zero
                                var startId = -1
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        total = Offset.Zero
                                        val cell = size.width / n
                                        val col = (pos.x / cell).toInt().coerceIn(0, n - 1)
                                        val row = (pos.y / cell).toInt().coerceIn(0, n - 1)
                                        startId = latest.cars.firstOrNull { car ->
                                            car.cells().any { it.first == row && it.second == col }
                                        }?.id ?: -1
                                        if (startId >= 0) selected = startId
                                    },
                                    onDragEnd = {
                                        if (startId < 0 || maxOf(abs(total.x), abs(total.y)) < 24f) return@detectDragGestures
                                        val car = latest.cars.firstOrNull { it.id == startId } ?: return@detectDragGestures
                                        val cell = size.width / n
                                        val along = if (car.horizontal) total.x else total.y
                                        if (abs(along) < 24f) return@detectDragGestures
                                        val sign = if (along > 0) 1 else -1
                                        val cells = (abs(along) / cell).toInt().coerceAtLeast(1)
                                        vm.play { it.slideBy(startId, sign, cells) }
                                    },
                                ) { change, drag -> change.consume(); total += drag }
                            },
                    ) {
                        Canvas(Modifier.matchParentSize()) {
                            val cell = size.width / n
                            for (i in 0..n) {
                                val p = i * cell
                                drawLine(c.outline, Offset(p, 0f), Offset(p, size.height), strokeWidth = 1f)
                                drawLine(c.outline, Offset(0f, p), Offset(size.width, p), strokeWidth = 1f)
                            }
                            for (car in g.cars) {
                                val target = car.id == 0
                                val fill = when {
                                    target -> c.accent
                                    car.id == selected -> c.highlight
                                    else -> CarPalette[(car.id - 1) % CarPalette.size]
                                }
                                val pad = cell * 0.08f
                                val origin = Offset(car.col * cell + pad, car.row * cell + pad)
                                val box = if (car.horizontal) Size(car.length * cell - pad * 2, cell - pad * 2)
                                else Size(cell - pad * 2, car.length * cell - pad * 2)
                                drawRoundRect(fill, origin, box, CornerRadius(cell * 0.22f))
                                if (target) {
                                    drawRoundRect(
                                        Color.White.copy(alpha = 0.35f),
                                        origin + Offset(box.width * 0.12f, box.height * 0.2f),
                                        Size(box.width * 0.2f, box.height * 0.6f),
                                        CornerRadius(cell * 0.08f),
                                    )
                                }
                            }
                        }
                        Column {
                            for (r in 0 until n) Row {
                                for (col in 0 until n) {
                                    val car = g.cars.firstOrNull { it.cells().any { cell -> cell.first == r && cell.second == col } }
                                    Box(
                                        Modifier
                                            .size(side)
                                            .clickable(enabled = car != null && !g.complete && !s.busy, role = Role.Button) {
                                                selected = if (selected == car!!.id) -1 else car.id
                                            }
                                            .semantics {
                                                contentDescription = say(
                                                    when {
                                                        car == null -> "Empty"
                                                        car.id == 0 -> "Target vehicle"
                                                        else -> "Vehicle ${car.id}"
                                                    },
                                                )
                                            },
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        Modifier
                            .size(width = side * 0.75f, height = side * n)
                            .padding(start = 4.dp)
                            .semantics { contentDescription = say("Exit") },
                    ) {
                        for (r in 0 until n) {
                            Box(Modifier.size(width = side * 0.7f, height = side), contentAlignment = Alignment.CenterStart) {
                                if (r == g.exitRow) {
                                    Box(
                                        Modifier
                                            .size(width = side * 0.55f, height = side * 0.7f)
                                            .background(c.accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                            .border(1.dp, c.accent, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("→", color = c.accent, style = MaterialTheme.typography.titleMedium) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!g.complete) {
            Text(
                if (selected < 0) "Tap a vehicle, then a direction — or drag it."
                else if (selected == 0) "Target selected. Slide it to the exit."
                else "Vehicle selected. Slide along its length.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("←" to -1 to true, "↑" to -1 to false, "↓" to 1 to false, "→" to 1 to true).forEach { (labelDir, horizontal) ->
                    val (label, dir) = labelDir
                    val car = g.cars.firstOrNull { it.id == selected }
                    val enabled = !s.busy && car != null && car.horizontal == horizontal
                    OutlinedButton(
                        onClick = { vm.play { it.slideBy(selected, dir, 1) } },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(label) }
                }
            }
        }
    }
}
