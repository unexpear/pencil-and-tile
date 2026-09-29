package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.components.WordDefinitionCard
import com.simplegamegen.sudoku.ui.components.rememberLexiconLookup
import com.simplegamegen.sudoku.ui.components.rememberShownWord
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.words.sprawlScoredWord
import com.simplegamegen.sudoku.wordplay.LexiconSense
import com.simplegamegen.sudoku.wordplay.Sprawl
import com.simplegamegen.sudoku.wordplay.SprawlSolver
import com.simplegamegen.sudoku.wordplay.sprawlFace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val SprawlSetup: (PuzzleFactory) -> PlaySetup<Sprawl> = { factory ->
    PlaySetup(
        rules = "Find words by chaining touching letters: sideways, up, down or diagonally, without using a square twice. " +
            "Drag across the letters, or tap them one by one and press Enter. The QU square counts as two letters. Longer words " +
            "score more: 1 point for three letters, 2 for four, then 3, 5, 8, and 13 for eight or more. Reach the goal to win, " +
            "then keep going or tap Finish to see the everyday words you missed.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level -> LogicLevel.entries[level].let { "${Sprawl.sizeOf(it)}×${Sprawl.sizeOf(it)} grid · words of ${Sprawl.minLengthOf(it)}+ letters" } },
        settingOf = { it.level.ordinal }, inProgress = { !it.finished },
        subtitle = { "${it.level.label} · ${it.score} of ${it.goal} points" },
        create = { level -> { factory.custom("sprawl:$level", { seed -> Sprawl.generate(seed, LogicLevel.entries[level]) }) { it.letters } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.finished) Outcome(if (g.reached) Result.WON else Result.LOST, g.score.toLong()) else null },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SprawlScreen(nav: NavController, vm: PlayViewModel<Sprawl>, factory: PuzzleFactory, lookup: ((String) -> List<LexiconSense>)? = null) {
    val c = LocalGameLook.current.colors
    var path by remember { mutableStateOf(listOf<Int>()) }
    val words = rememberLexiconLookup(lookup)
    PlayShell(nav, vm, GameId.LETTER_SPRAWL, remember(factory) { SprawlSetup(factory) }, undoable = false, tools = { g, s ->
        HintButton(GameId.LETTER_SPRAWL, enabled = !g.finished && !s.busy) {
            vm.play { it.hint() }
        }
        ToolButton(GameIcons.Check, "Finish", enabled = !g.finished && !s.busy) {
            path = emptyList()
            vm.play(if (g.reached) "Well done: ${g.score} points!" else "${g.score} of ${g.goal} points. Here are the words you missed.") { it.finish() }
        }
    }) { g, s ->
        // The word lists load off the main thread the first time.
        val ready by produceState(false, g.letters) { value = withContext(Dispatchers.Default) { g.goal; true } }
        val board by rememberUpdatedState(g)
        val shown = rememberShownWord(g.seed)
        // Drag keeps this closure, so the board is read when the finger lifts, not when the gesture started.
        fun submitPath(p: List<Int>) {
            val game = board
            val word = sprawlScoredWord(game, p)
            if (word == null) {
                val problem = game.problem(p)
                if (problem != null && p.size > 1) vm.say(if (problem == "Already found.") "${game.wordOf(p)}: already found." else problem)
                return
            }
            val next = game.take(p) ?: return
            val points = Sprawl.pointsFor(word)
            vm.play(if (!game.reached && next.reached) "$word +$points. Goal reached! Keep going, or tap Finish." else "$word +$points") {
                shown.value = word
                next
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(if (ready) "${g.score} of ${g.goal} points" else "…", emphasized = g.reached)
            InfoChip(if (g.found.size == 1) "1 word" else "${g.found.size} words")
            if (g.hints > 0) InfoChip(if (g.hints == 1) "1 hint" else "${g.hints} hints", icon = GameIcons.Hint)
        }
        if (g.finished) WinBanner(if (g.reached) "Goal reached: ${g.score} points!" else "${g.score} of ${g.goal} points")
        else if (g.reached) Text("Goal reached! Keep going, or tap Finish.", style = MaterialTheme.typography.bodyMedium, color = c.success)
        g.hinted?.let { Text("Hint: a word starting ${it}…", style = MaterialTheme.typography.bodyMedium, color = c.accent) }

        // The word being traced.
        Surface(color = c.surfaceAlt, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(if (path.isEmpty()) " " else g.wordOf(path), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = c.text, modifier = Modifier.weight(1f))
                if (path.isNotEmpty()) {
                    Surface(onClick = { path = emptyList() }, color = c.surface, shape = RoundedCornerShape(10.dp)) {
                        Text("Clear", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = c.text)
                    }
                    Box(Modifier.size(8.dp))
                    Surface(onClick = { submitPath(path); path = emptyList() }, color = c.accent, shape = RoundedCornerShape(10.dp)) {
                        Text("Enter", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = c.onAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val hintPath = if (ready) g.hintPath() else emptyList()
        val traced by rememberUpdatedState(path)
        val grid by rememberUpdatedState(g)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min((maxWidth - 16.dp) / g.size, 76.dp)
            val gap = 6.dp
            Box(Modifier.size(side * g.size + gap * (g.size - 1))
                .pointerInput(g.letters, g.finished) {
                    if (g.finished) return@pointerInput
                    val step = (side + gap).toPx()
                    val half = side.toPx() / 2
                    // A square counts only near its middle, so diagonal drags don't clip the corners of others.
                    fun cellAt(o: Offset): Int? {
                        val col = (o.x / step).toInt(); val row = (o.y / step).toInt()
                        if (col !in 0 until g.size || row !in 0 until g.size) return null
                        val cx = col * step + half; val cy = row * step + half
                        val dx = o.x - cx; val dy = o.y - cy
                        return if (dx * dx + dy * dy <= (half * 0.8f) * (half * 0.8f)) row * g.size + col else null
                    }
                    detectDragGestures(
                        onDragStart = { o -> cellAt(o)?.let { path = listOf(it) } },
                        onDragEnd = { val p = traced; path = emptyList(); if (p.size >= 2) submitPath(p) },
                        onDragCancel = { path = emptyList() },
                    ) { change, _ ->
                        val cell = cellAt(change.position) ?: return@detectDragGestures
                        val p = traced
                        when {
                            p.isEmpty() -> path = listOf(cell)
                            p.size >= 2 && cell == p[p.size - 2] -> path = p.dropLast(1)
                            cell !in p && cell in SprawlSolver.neighbours(p.last(), grid.size) -> path = p + cell
                        }
                    }
                }) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (r in 0 until g.size) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (k in 0 until g.size) {
                            val i = r * g.size + k
                            val on = i in path
                            val last = path.lastOrNull() == i
                            val face = sprawlFace(g.letters[i])
                            Box(Modifier.size(side)
                                .background(when { on -> c.accent; i in hintPath -> c.highlight; else -> c.surface }, RoundedCornerShape(side * 0.2f))
                                .border(if (last) 3.dp else 1.dp, if (on) c.accent else c.outline, RoundedCornerShape(side * 0.2f))
                                .pointerInput(i, g.finished) {
                                    detectTapGestures {
                                        if (g.finished) return@detectTapGestures
                                        val p = traced
                                        path = when {
                                            p.isNotEmpty() && p.last() == i -> p.dropLast(1)
                                            i in p -> p.subList(0, p.indexOf(i) + 1)
                                            p.isEmpty() || i in SprawlSolver.neighbours(p.last(), grid.size) -> p + i
                                            else -> listOf(i)
                                        }
                                    }
                                }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = say("Row ${r + 1}, column ${k + 1}, $face" + if (on) ", picked" else "")
                                },
                                contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Text(face.lowercase().replaceFirstChar { it.uppercase() }, fontSize = (side.value * (if (face.length > 1) 0.36f else 0.46f)).sp,
                                    fontWeight = FontWeight.Bold, color = if (on) c.onAccent else c.text)
                            }
                        }
                    }
                }
                // The trace line.
                if (path.size >= 2) Canvas(Modifier.size(side * g.size + gap * (g.size - 1))) {
                    val step = (side + gap).toPx()
                    val half = side.toPx() / 2
                    for ((a, b) in path.zipWithNext()) {
                        drawLine(c.onAccent.copy(alpha = 0.7f), Offset((a % g.size) * step + half, (a / g.size) * step + half),
                            Offset((b % g.size) * step + half, (b / g.size) * step + half), side.toPx() * 0.1f, cap = StrokeCap.Round)
                    }
                }
            }
        }

        // Below the grid, so a definition doesn't shove the letters while a path is being traced.
        shown.value?.let { lemma -> WordDefinitionCard(lemma, words, onDismiss = { shown.value = null }) }
        // Found words, longest first; after Finish, the everyday words that were missed.
        if (g.found.isNotEmpty()) {
            Text("Found", style = MaterialTheme.typography.titleSmall, color = c.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.found.sortedWith(compareBy({ -it.length }, { it })).forEach { w ->
                    InfoChip("${w.lowercase()} ${Sprawl.pointsFor(w)}", onClick = { shown.value = w })
                }
            }
        }
        if (g.finished && ready) {
            val missed = g.everyday.filter { it !in g.found }
            if (missed.isNotEmpty()) {
                Text("Everyday words you missed", style = MaterialTheme.typography.titleSmall, color = c.text)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    missed.take(60).forEach { w -> Text(w.lowercase(), style = MaterialTheme.typography.bodyMedium, color = c.muted) }
                }
            }
        }
        if (!g.finished && s.message == null) Text("Drag across touching letters, or tap them and press Enter.",
            style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}
