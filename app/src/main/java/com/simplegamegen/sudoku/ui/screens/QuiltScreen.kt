package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.wordplay.QUILT_EMPTY
import com.simplegamegen.sudoku.wordplay.QUILT_HOLE
import com.simplegamegen.sudoku.wordplay.Quilt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val QuiltSetup: (PuzzleFactory) -> PlaySetup<Quilt> = { factory ->
    PlaySetup(
        rules = "The grid is sewn from patches, and each patch holds a set of letters, shown in its corner. Place every patch's " +
            "letters in its own squares so that each run of two or more letters, across and down, is a word. Tap a square, then " +
            "one of its patch's letters; tap a filled square to take its letter back. A full run that isn't a word is underlined " +
            "in red. Any arrangement that makes every run a word wins.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level -> LogicLevel.entries[level].let { l -> Quilt.shapeOf(l).let { "${it[0].length}×${it.size} grid · patches of ${Quilt.patchSizeOf(l).first}–${Quilt.patchSizeOf(l).second}" } } },
        settingOf = { it.level.ordinal }, inProgress = { !it.solved },
        subtitle = { "${it.level.label} · ${it.squares.count { i -> it.cells[i] != QUILT_EMPTY }} of ${it.squares.size} squares" },
        create = { level -> { factory.custom("quilt:$level", { seed -> Quilt.generate(seed, LogicLevel.entries[level]) }) { it.answer } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.solved) Outcome(Result.WON) else null },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuiltScreen(nav: NavController, vm: PlayViewModel<Quilt>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var selected by remember { mutableStateOf<Int?>(null) }
    PlayShell(nav, vm, GameId.WORD_QUILT, remember(factory) { QuiltSetup(factory) }, tools = { g, s ->
        HintButton(GameId.WORD_QUILT, enabled = !g.solved && !s.busy) { selected = null; vm.play("One square placed.") { it.hint() } }
    }) { g, s ->
        val bad by produceState(emptyList<List<Int>>(), g.cells) { value = withContext(Dispatchers.Default) { g.badRuns() } }
        val badSquares = bad.flatten().toSet()
        if (g.solved) WinBanner("Every run is a word. Quilt finished!")
        Row2(g, bad)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min((maxWidth - 8.dp) / g.width, 64.dp)
            Box(Modifier.size(side * g.width, side * g.height)) {
                for (i in g.shape.indices) {
                    val r = i / g.width; val k = i % g.width
                    if (g.shape[i] == QUILT_HOLE) continue
                    val patch = g.patches[i]
                    val first = g.squares.first { g.patches[it] == patch } == i
                    val ch = g.cells[i]
                    val on = selected == i
                    val fixed = i in g.given
                    Box(Modifier.padding(start = side * k, top = side * r).size(side)
                        .background(when { on -> c.accent.copy(alpha = 0.3f); fixed -> c.surfaceAlt; else -> c.surface })
                        .clickable(role = Role.Button, enabled = !g.solved && !fixed) {
                            selected = if (on) null else i
                        }
                        .semantics {
                            contentDescription = say("Row ${r + 1}, column ${k + 1}, " + (if (ch == QUILT_EMPTY) "empty" else ch.toString()) +
                                ", patch letters ${g.remaining(patch).ifEmpty { "none left" }}" + (if (fixed) ", given" else ""))
                        }, contentAlignment = Alignment.Center) {
                        if (ch != QUILT_EMPTY) androidx.compose.material3.Text(ch.toString(), fontSize = (side.value * 0.5f).sp,
                            fontWeight = if (fixed) FontWeight.Black else FontWeight.Bold, color = if (i in badSquares) c.danger else c.text)
                        if (first) androidx.compose.material3.Text(g.remaining(patch), Modifier.align(Alignment.TopStart).padding(3.dp),
                            fontSize = (side.value * 0.17f).sp, color = c.accent, fontWeight = FontWeight.Bold)
                    }
                }
                // Seams: thin lines inside a patch, thick ones between patches.
                Canvas(Modifier.size(side * g.width, side * g.height)) {
                    val u = size.width / g.width
                    for (i in g.shape.indices) {
                        if (g.shape[i] == QUILT_HOLE) continue
                        val r = i / g.width; val k = i % g.width
                        fun edge(other: Int?, a: Offset, b: Offset) {
                            val same = other != null && g.shape[other] != QUILT_HOLE && g.patches[other] == g.patches[i]
                            drawLine(if (same) c.outline.copy(alpha = 0.5f) else c.text, a, b, if (same) 1.5f else 5f, cap = StrokeCap.Round)
                        }
                        val x = k * u; val y = r * u
                        edge(if (r > 0) i - g.width else null, Offset(x, y), Offset(x + u, y))
                        edge(if (r < g.height - 1) i + g.width else null, Offset(x, y + u), Offset(x + u, y + u))
                        edge(if (k > 0) i - 1 else null, Offset(x, y), Offset(x, y + u))
                        edge(if (k < g.width - 1) i + 1 else null, Offset(x + u, y), Offset(x + u, y + u))
                    }
                    // Runs that are full but not words get a red underline.
                    for (run in bad) {
                        val a = run.first(); val b = run.last()
                        val across = a / g.width == b / g.width
                        if (across) drawLine(c.danger, Offset((a % g.width) * u + 6f, (a / g.width + 1) * u - 6f), Offset((b % g.width + 1) * u - 6f, (a / g.width + 1) * u - 6f), 4f)
                        else drawLine(c.danger, Offset((a % g.width + 1) * u - 6f, (a / g.width) * u + 6f), Offset((a % g.width + 1) * u - 6f, (b / g.width + 1) * u - 6f), 4f)
                    }
                    for (i in g.shape.indices) if (g.shape[i] == QUILT_HOLE) {
                        drawRect(c.text.copy(alpha = 0.85f), Offset((i % g.width) * u, (i / g.width) * u), Size(u, u))
                    }
                }
            }
        }
        // The chosen square's patch letters.
        val at = selected
        if (at != null && !g.solved) {
            val patch = g.patches[at]
            Text("Patch letters", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.remaining(patch).toList().distinct().forEach { ch ->
                    Surface(onClick = { g.place(at, ch)?.let { next -> vm.play { next }; selected = null } }, color = c.accent, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(48.dp).semantics { contentDescription = say("Place $ch") }) {
                        Box(contentAlignment = Alignment.Center) { androidx.compose.material3.Text(ch.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.onAccent) }
                    }
                }
                if (g.cells[at] != QUILT_EMPTY) Surface(onClick = { g.clear(at)?.let { next -> vm.play { next } } }, color = c.surfaceAlt, shape = RoundedCornerShape(10.dp)) {
                    Text("Take back", Modifier.padding(14.dp), color = c.text)
                }
            }
        } else if (!g.solved && s.message == null) Text("Tap a square, then one of its patch's letters.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

@Composable
private fun Row2(g: Quilt, bad: List<List<Int>>) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val words = g.runs.count { r -> g.runWord(r) != null && r !in bad }
        InfoChip("$words of ${g.runs.size} words")
        if (g.hints > 0) InfoChip(if (g.hints == 1) "1 hint" else "${g.hints} hints", icon = GameIcons.Hint)
    }
}
