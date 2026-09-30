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
import com.simplegamegen.sudoku.grids.Klotski
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

private val BlockPalette = listOf(
    Color(0xFF8D6E63), Color(0xFF6D4C41), Color(0xFFA1887F), Color(0xFF5D4037),
    Color(0xFFBCAAA4), Color(0xFF795548), Color(0xFF4E342E), Color(0xFFD7CCC8),
    Color(0xFF3E2723),
)

private val LevelBlurb = listOf(
    "4×5 · a short path",
    "4×5 · a longer scramble",
    "4×5 · a deep scramble",
    "4×5 · the classic opening",
)

val KlotskiSetup: (PuzzleFactory) -> PlaySetup<Klotski> = { factory ->
    PlaySetup(
        rules = "Klotski is a 4×5 board with two empty squares. " +
            "Slide one block one square at a time, up, down, left or right, into empty space. " +
            "The big square wins when it sits on the opening at the bottom. Each square a block slides counts as one move. " +
            "Tap a block, then an arrow, or drag it.",
        settingTitle = "Difficulty",
        settings = LogicLevel.entries.map { it.label },
        describe = { LevelBlurb[it] },
        settingOf = { it.setting },
        inProgress = { !it.complete && it.moves > 0 },
        subtitle = { "${LogicLevel.entries[it.setting].label} · ${it.moves} moves · par ${it.solutionLength}" },
        create = { i ->
            {
                factory.custom("klotski:$i", { seed -> Klotski.generate(seed, LogicLevel.entries[i]) }) {
                    it.blocks.joinToString(";") { b -> "${b.row},${b.col}" }
                }
            }
        },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@Composable
fun KlotskiScreen(nav: NavController, vm: PlayViewModel<Klotski>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var selected by remember { mutableIntStateOf(-1) }
    PlayShell(nav, vm, GameId.KLOTSKI, remember(factory) { KlotskiSetup(factory) }, tools = { g, s ->
        HintButton(GameId.KLOTSKI, enabled = !g.complete && !s.busy) {
            vm.play("One block slid.") { it.hint() }
        }
    }) { g, s ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.moves} moves", emphasized = true)
            InfoChip("par ${g.solutionLength}")
        }
        if (g.complete) WinBanner("The big block reached the opening.")
        val latest by rememberUpdatedState(g)
        ZoomBox(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val side = min(maxWidth / Klotski.COLS, 72.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(width = side * Klotski.COLS, height = side * Klotski.ROWS)
                            .background(c.surface)
                            .border(2.dp, c.text)
                            .semantics { contentDescription = say("Klotski board") }
                            .pointerInput(g.seed, g.blocks, s.busy, g.complete) {
                                if (g.complete || s.busy) return@pointerInput
                                var total = Offset.Zero
                                var startId = -1
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        total = Offset.Zero
                                        val col = (pos.x / (size.width / Klotski.COLS)).toInt().coerceIn(0, Klotski.COLS - 1)
                                        val row = (pos.y / (size.height / Klotski.ROWS)).toInt().coerceIn(0, Klotski.ROWS - 1)
                                        startId = latest.blocks.firstOrNull { block ->
                                            block.cells().any { it.first == row && it.second == col }
                                        }?.id ?: -1
                                        if (startId >= 0) selected = startId
                                    },
                                    onDragEnd = {
                                        if (startId < 0) return@detectDragGestures
                                        val cell = size.width / Klotski.COLS
                                        if (maxOf(abs(total.x), abs(total.y)) < cell * 0.4f) return@detectDragGestures
                                        val horizontal = abs(total.x) > abs(total.y)
                                        val along = if (horizontal) total.x else total.y
                                        val sign = if (along > 0) 1 else -1
                                        val cells = (abs(along) / cell).toInt().coerceAtLeast(1)
                                        val dr = if (horizontal) 0 else sign
                                        val dc = if (horizontal) sign else 0
                                        vm.play { it.slideBy(startId, dr, dc, cells) }
                                    },
                                ) { change, drag -> change.consume(); total += drag }
                            },
                    ) {
                        Canvas(Modifier.matchParentSize()) {
                            val cw = size.width / Klotski.COLS
                            val ch = size.height / Klotski.ROWS
                            for (block in g.blocks) {
                                val big = block.id == 0
                                val fill = when {
                                    big -> c.accent
                                    block.id == selected -> c.highlight
                                    else -> BlockPalette[(block.id - 1) % BlockPalette.size]
                                }
                                val pad = cw * 0.08f
                                drawRoundRect(
                                    fill,
                                    Offset(block.col * cw + pad, block.row * ch + pad),
                                    Size(block.width * cw - pad * 2, block.height * ch - pad * 2),
                                    CornerRadius(cw * 0.16f),
                                )
                            }
                        }
                        Column {
                            for (r in 0 until Klotski.ROWS) Row {
                                for (col in 0 until Klotski.COLS) {
                                    val block = g.blocks.firstOrNull { it.cells().any { cell -> cell.first == r && cell.second == col } }
                                    Box(
                                        Modifier
                                            .size(side)
                                            .clickable(enabled = block != null && !g.complete && !s.busy, role = Role.Button) {
                                                selected = if (selected == block!!.id) -1 else block.id
                                            }
                                            .semantics {
                                                contentDescription = say(
                                                    when {
                                                        block == null -> "Empty"
                                                        block.id == 0 -> "Big block"
                                                        else -> "Block ${block.id}"
                                                    },
                                                )
                                            },
                                    )
                                }
                            }
                        }
                    }
                    Row {
                        for (col in 0 until Klotski.COLS) {
                            Box(Modifier.size(width = side, height = side * 0.45f), contentAlignment = Alignment.Center) {
                                if (col == 1 || col == 2) {
                                    Box(
                                        Modifier
                                            .size(width = side * 0.7f, height = side * 0.28f)
                                            .background(c.accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!g.complete) {
            Text(
                if (selected < 0) "Tap a block, then an arrow — or drag it."
                else if (selected == 0) "Big block selected. Slide it to the opening."
                else "Block selected. Slide it into an empty square.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.muted,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("←" to (0 to -1), "↑" to (-1 to 0), "↓" to (1 to 0), "→" to (0 to 1)).forEach { (label, dir) ->
                    OutlinedButton(
                        onClick = { vm.play { it.slide(selected, dir.first, dir.second) } },
                        enabled = !s.busy && selected >= 0,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(label) }
                }
            }
        }
    }
}
