package com.simplegamegen.sudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.WordDefinitionCard
import com.simplegamegen.sudoku.ui.components.rememberLexiconLookup
import com.simplegamegen.sudoku.ui.components.rememberShownWord
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.words.letterfallScoredWord
import com.simplegamegen.sudoku.wordplay.Letterfall
import com.simplegamegen.sudoku.wordplay.LexiconSense
import kotlin.math.roundToInt

val LetterfallSetup: (PuzzleFactory) -> PlaySetup<Letterfall> = { factory ->
    PlaySetup(
        rules = "Join tiles that share a side (not diagonally) into a word of at least 3 letters, using each tile once. " +
            "Drag across them, or tap them and press Enter. The word clears, tiles above drop, and new letters fall from the top. " +
            "Longer words and rare letters score more, and a combo (up to ×4) builds when you use a tile that just fell. " +
            "Reach the target before you run out of moves.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level ->
            LogicLevel.entries[level].let {
                "${Letterfall.sizeOf(it)}×${Letterfall.sizeOf(it)} grid · ${Letterfall.movesOf(it)} moves · ${Letterfall.targetOf(it)} points"
            }
        },
        settingOf = { it.level.ordinal }, inProgress = { !it.over },
        subtitle = { "${it.level.label} · ${it.score} of ${it.target} points" },
        create = { level -> { factory.custom("letterfall:$level", { seed -> Letterfall.generate(seed, LogicLevel.entries[level]) }) { it.letters } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            when {
                g.won -> Outcome(Result.WON, g.score.toLong())
                g.lost -> Outcome(Result.LOST, g.score.toLong())
                else -> null
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LetterfallScreen(nav: NavController, vm: PlayViewModel<Letterfall>, factory: PuzzleFactory, lookup: ((String) -> List<LexiconSense>)? = null) {
    val c = LocalGameLook.current.colors
    var path by remember { mutableStateOf(listOf<Int>()) }
    var shake by remember { mutableIntStateOf(0) }
    val nudge = remember { Animatable(0f) }
    val words = rememberLexiconLookup(lookup)
    LaunchedEffect(shake) {
        if (shake == 0) return@LaunchedEffect
        listOf(14f, -14f, 10f, -10f, 4f, 0f).forEach { nudge.animateTo(it, tween(45)) }
    }
    PlayShell(nav, vm, GameId.LETTERFALL, remember(factory) { LetterfallSetup(factory) }) { g, s ->
        val board by rememberUpdatedState(g)
        val shown = rememberShownWord(g.seed)
        LaunchedEffect(g.letters) { path = emptyList() }
        fun submitPath(p: List<Int>) {
            val game = board
            val word = letterfallScoredWord(game, p)
            if (word == null) {
                if (p.size > 1) {
                    shake++
                    game.problem(p)?.let(vm::say)
                }
                return
            }
            val next = game.play(p) ?: return
            val note = buildString {
                append("$word +${next.lastPoints}")
                if (next.combo > 1) append(" · ×${next.combo}")
                if (next.won) append(" · Target reached!")
                else if (next.lost) append(" · Out of moves.")
            }
            vm.play(note) {
                shown.value = word
                next
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.score} of ${g.target} points", emphasized = g.won)
            InfoChip(if (g.movesLeft == 1) "1 move" else "${g.movesLeft} moves")
            InfoChip("Next ×${g.nextCombo()}")
            InfoChip(if (g.found == 1) "1 word" else "${g.found} words")
        }
        if (g.won) EndBanner("Target reached: ${g.score} points!", won = true)
        else if (g.lost) EndBanner("Out of moves: ${g.score} of ${g.target} points", won = false)

        Surface(color = c.surfaceAlt, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    if (path.isEmpty()) " " else g.wordOf(path),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f),
                )
                if (path.isNotEmpty() && !g.over) {
                    Surface(onClick = { path = emptyList() }, color = c.surface, shape = RoundedCornerShape(10.dp)) {
                        Text("Clear", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = c.text)
                    }
                    Box(Modifier.size(8.dp))
                    Surface(onClick = { val p = path; path = emptyList(); submitPath(p) }, color = c.accent, shape = RoundedCornerShape(10.dp)) {
                        Text("Enter", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = c.onAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val traced by rememberUpdatedState(path)
        val grid by rememberUpdatedState(g)
        BoxWithConstraints(Modifier.fillMaxWidth().offset { IntOffset(nudge.value.roundToInt(), 0) }, contentAlignment = Alignment.Center) {
            val gap = 4.dp
            val side = min((maxWidth - gap * (g.cols - 1)) / g.cols, 58.dp)
            Box(Modifier.size(side * g.cols + gap * (g.cols - 1))
                .pointerInput(g.letters, g.over) {
                    if (g.over) return@pointerInput
                    val step = (side + gap).toPx()
                    fun cellAt(o: Offset): Int? {
                        val col = (o.x / step).toInt()
                        val row = (o.y / step).toInt()
                        if (col !in 0 until g.cols || row !in 0 until g.rows) return null
                        return row * g.cols + col
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
                            cell !in p && cell in Letterfall.neighbours(p.last(), grid.rows, grid.cols) -> path = p + cell
                        }
                    }
                }) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (r in 0 until g.rows) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (k in 0 until g.cols) {
                            val i = r * g.cols + k
                            val on = i in path
                            val last = path.lastOrNull() == i
                            val fell = i in g.fresh
                            val ch = g.letters[i].toString()
                            val rare = g.letters[i] in Letterfall.RARE || g.letters[i] in Letterfall.UNCOMMON
                            Box(Modifier.size(side)
                                .background(when {
                                    on -> c.accent
                                    fell -> c.highlight.copy(alpha = 0.55f)
                                    else -> c.surface
                                }, RoundedCornerShape(side * 0.2f))
                                .border(if (last || fell) 2.dp else 1.dp, when {
                                    on -> c.accent
                                    fell -> c.highlight
                                    else -> c.outline
                                }, RoundedCornerShape(side * 0.2f))
                                .pointerInput(i, g.over) {
                                    detectTapGestures {
                                        if (g.over) return@detectTapGestures
                                        val p = traced
                                        path = when {
                                            p.isNotEmpty() && p.last() == i -> p.dropLast(1)
                                            i in p -> p.subList(0, p.indexOf(i) + 1)
                                            p.isEmpty() || i in Letterfall.neighbours(p.last(), grid.rows, grid.cols) -> p + i
                                            else -> listOf(i)
                                        }
                                    }
                                }
                                .semantics {
                                    role = Role.Button
                                    val bits = mutableListOf("Row ${r + 1}, column ${k + 1}, $ch")
                                    if (on) bits += "picked"
                                    if (fell) bits += "just fell"
                                    contentDescription = say(bits.joinToString(", "))
                                },
                                contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Text(
                                    ch, fontSize = (side.value * 0.46f).sp, fontWeight = FontWeight.Bold,
                                    color = when {
                                        on -> c.onAccent
                                        rare -> c.accent
                                        else -> c.text
                                    },
                                )
                            }
                        }
                    }
                }
                if (path.size >= 2) Canvas(Modifier.size(side * g.cols + gap * (g.cols - 1))) {
                    val step = (side + gap).toPx()
                    val half = side.toPx() / 2
                    for ((a, b) in path.zipWithNext()) {
                        drawLine(
                            c.onAccent.copy(alpha = 0.7f),
                            Offset((a % g.cols) * step + half, (a / g.cols) * step + half),
                            Offset((b % g.cols) * step + half, (b / g.cols) * step + half),
                            side.toPx() * 0.1f, cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }

        shown.value?.let { lemma -> WordDefinitionCard(lemma, words, onDismiss = { shown.value = null }) }
        if (g.words.isNotEmpty()) {
            Text("Found", style = MaterialTheme.typography.titleSmall, color = c.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.words.asReversed().distinct().take(24).forEach { w ->
                    InfoChip(w.lowercase(), onClick = { shown.value = w })
                }
            }
        }
        if (!g.over && s.message == null) Text(
            "Drag across tiles that share a side, or tap them and press Enter.",
            style = MaterialTheme.typography.bodyMedium, color = c.muted,
        )
    }
}

@Composable
private fun EndBanner(text: String, won: Boolean) {
    val c = LocalGameLook.current.colors
    val color = if (won) c.success else c.danger
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, color = color)
    }
}
