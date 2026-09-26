package com.simplegamegen.sudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.simplegamegen.sudoku.ui.formatSeconds
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.wordplay.LoneAnswers
import com.simplegamegen.sudoku.wordplay.LoneCategories
import com.simplegamegen.sudoku.wordplay.LoneLetter
import com.simplegamegen.sudoku.wordplay.LoneMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

val LoneLetterSetup: (PuzzleFactory) -> PlaySetup<LoneLetter> = { factory ->
    PlaySetup(
        rules = "Each round rolls one letter and deals a list of categories. Write an answer for each category that starts with " +
            "the letter, then tap Done (or wait for the clock). Answers are in English, and a leading \"the\", \"a\" or \"an\" doesn't count. " +
            "Your answers are compared with the computer players': an answer scores 1 point only if nobody else wrote the same thing, " +
            "plus a bonus point for every further word that starts with the letter, like \"Big Blue Bus\". If an answer of real words " +
            "isn't on our list, you can count it yourself. Three rounds; the highest total wins.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level ->
            LogicLevel.entries[level].let {
                val clock = LoneLetter.secondsOf(it).let { s -> if (s == 0) "no clock" else "${formatSeconds(s.toLong())} a round" }
                "${LoneLetter.categoriesOf(it)} categories · ${LoneLetter.rivalCount(it)} rivals · $clock"
            }
        },
        settingOf = { it.level.ordinal }, inProgress = { !it.over },
        subtitle = { "${it.level.label} · round ${it.current + 1} of ${it.rounds.size}" },
        create = { level -> { factory.custom("loneletter:$level", { seed -> LoneLetter.generate(seed, LogicLevel.entries[level]) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.over) Outcome(if (g.won) Result.WON else Result.LOST, g.score.toLong()) else null },
    )
}

/** Each computer player's colour. */
private val RivalColors = listOf(Color(0xFFE0784A), Color(0xFF4F8EF7), Color(0xFF3FA37A))
private val Good = Color(0xFF3FA37A)
private val Bad = Color(0xFFE5484D)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoneLetterScreen(nav: NavController, vm: PlayViewModel<LoneLetter>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var focused by remember { mutableIntStateOf(0) }
    PlayShell(nav, vm, GameId.LONE_LETTER, remember(factory) { LoneLetterSetup(factory) }, undoable = false, tools = { g, s ->
        HintButton(GameId.LONE_LETTER, enabled = g.playing && !s.busy && focused !in g.rounds[g.current].hinted) {
            vm.play(record = false) { it.hint(focused) }
        }
        if (g.playing) ToolButton(GameIcons.Check, "Done", enabled = !s.busy) {
            vm.play(record = false) { it.stop() }
        } else if (!g.over) ToolButton(GameIcons.Play, "Next round", enabled = !s.busy) {
            focused = 0
            vm.play(record = false) { it.next() }
        }
    }) { g, s ->
        // The category lists load off the main thread the first time.
        val ready by produceState(false) { value = withContext(Dispatchers.Default) { LoneCategories.all; true } }
        if (!ready) { Text("…", color = c.muted); return@PlayShell }
        val index = g.current
        val round = g.rounds[index]

        // The clock runs while the round is open and the screen is showing.
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val life by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
        val latest by rememberUpdatedState(g)
        if (g.playing && g.limit > 0 && life.isAtLeast(Lifecycle.State.RESUMED)) LaunchedEffect(index) {
            while (true) {
                delay(1000)
                val used = latest.used + 1
                val ends = used >= latest.limit
                vm.play(if (ends) "Time's up!" else null, record = false, persist = ends || used % 5 == 0) { it.tick(1) }
                if (ends) break
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LetterDie(round.letter, index)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Text("Round ${index + 1} of ${g.rounds.size}", style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.Bold)
                val left = g.timeLeft
                if (g.playing && left != null) ClockBar(left, g.limit)
                else if (g.playing) Text("No clock: take your time.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoChip("You ${g.score}", emphasized = true)
                    LoneLetter.RIVALS.take(LoneLetter.rivalCount(g.level)).forEachIndexed { k, name -> InfoChip("$name ${g.rivalScore(k)}") }
                }
            }
        }

        if (g.over) {
            val best = g.rivalScores.withIndex().maxByOrNull { it.value }
            when {
                g.won && best != null && best.value == g.score -> WinBanner("A tie at ${g.score} points: you share the win!")
                g.won -> WinBanner("You win with ${g.score} points!")
                else -> Surface(color = c.surfaceAlt, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Text("${LoneLetter.RIVALS[best!!.index]} wins with ${best.value} points", Modifier.padding(14.dp),
                        style = MaterialTheme.typography.titleMedium, color = c.text)
                }
            }
        }

        if (g.playing) {
            Text("Everything starts with ${round.letter}.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            val focus = LocalFocusManager.current
            round.categories.forEachIndexed { i, id ->
                val answer = round.mine[i]
                val off = answer.isNotBlank() && !LoneAnswers.startsWith(answer, round.letter)
                OutlinedTextField(value = answer, onValueChange = { text -> vm.play(record = false) { it.answer(i, text) } },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) focused = i },
                    label = { Text("${i + 1}. ${say(LoneCategories[id].label)}") }, singleLine = true, isError = off,
                    placeholder = { androidx.compose.material3.Text("${round.letter}…") },
                    supportingText = when {
                        off -> ({ Text("Doesn't start with ${round.letter}", color = Bad) })
                        i in round.hinted -> ({ Text("Try ${round.hinted.getValue(i)}…", color = c.accent) })
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words,
                        imeAction = if (i == round.categories.lastIndex) ImeAction.Done else ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { focus.clearFocus() }))
            }
            if (s.message == null) Text("Tap Done when you're ready. Answers only score if nobody else wrote them.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted)
        } else {
            // The round's answers side by side, with what each one scored.
            val rivals = LoneLetter.rivalCount(g.level)
            Text("Round ${index + 1}: you scored ${g.roundScore(index)}", style = MaterialTheme.typography.titleSmall, color = c.text)
            round.categories.forEachIndexed { i, id ->
                Surface(color = c.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(LoneCategories[id].label, style = MaterialTheme.typography.labelLarge, color = c.muted)
                        MyAnswer(g, index, i, onCount = { vm.play("Counted.", record = false) { it.count(index, i) } })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (k in 0 until rivals) RivalAnswer(k, round.rivals[k][i], g.rivalMark(index, k, i), g.points(index, i, k))
                        }
                    }
                }
            }
        }
    }
}

/** The round's letter on a die that tumbles through letters when a new round starts. */
@Composable
private fun LetterDie(letter: Char, round: Int) {
    val c = LocalGameLook.current.colors
    var shown by remember { mutableStateOf(letter) }
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(round, letter) {
        val faces = LoneLetter.lettersOf(LogicLevel.EXPERT)
        for (k in 0 until 9) { shown = faces[(k * 7 + round * 3 + letter.code) % faces.length]; delay(45L + k * 12L) }
        shown = letter
        bounce.snapTo(1.25f)
        bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(Modifier.size(84.dp).scale(bounce.value).semantics { contentDescription = say("Letter $letter") }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) { drawDie(c.accent, c.onAccent) }
        androidx.compose.material3.Text(shown.toString(), fontSize = 46.sp, fontWeight = FontWeight.Black, color = c.onAccent)
    }
}

private fun DrawScope.drawDie(face: Color, pip: Color) {
    val r = size.minDimension * 0.2f
    // A darker edge below reads as the die's side.
    drawRoundRect(face.copy(alpha = 0.55f), topLeft = Offset(0f, size.height * 0.06f), size = Size(size.width, size.height * 0.94f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    drawRoundRect(face, size = Size(size.width, size.height * 0.92f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    for ((x, y) in listOf(0.14f to 0.14f, 0.86f to 0.14f, 0.14f to 0.78f, 0.86f to 0.78f))
        drawCircle(pip.copy(alpha = 0.35f), size.minDimension * 0.035f, Offset(size.width * x, size.height * y))
}

/** Time left as a draining bar that turns red and pulses in the last twenty seconds. */
@Composable
private fun ClockBar(left: Int, limit: Int) {
    val c = LocalGameLook.current.colors
    val urgent = left <= 20
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(left) { if (urgent) { pulse.snapTo(1f); pulse.animateTo(0f, tween(600)) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.weight(1f).height(10.dp)) {
            val h = size.height
            drawLine(c.outline, Offset(h / 2, h / 2), Offset(size.width - h / 2, h / 2), h, cap = StrokeCap.Round)
            val share = left.toFloat() / limit
            if (share > 0f) drawLine(if (urgent) Bad else c.accent, Offset(h / 2, h / 2),
                Offset(h / 2 + (size.width - h) * share, h / 2), h * (1f + pulse.value * 0.4f), cap = StrokeCap.Round)
        }
        androidx.compose.material3.Text(formatSeconds(left.toLong()), fontWeight = FontWeight.Bold, color = if (urgent) Bad else c.text,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), modifier = Modifier.scale(1f + pulse.value * 0.15f))
    }
}

@Composable
private fun MyAnswer(g: LoneLetter, round: Int, category: Int, onCount: () -> Unit) {
    val c = LocalGameLook.current.colors
    val r = g.rounds[round]
    val answer = r.mine[category]
    val mark = g.mark(round, category)
    val points = g.points(round, category)
    // Points pop in when the round is revealed.
    val pop = remember(round) { Animatable(0f) }
    LaunchedEffect(round) { delay(category * 90L); pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Face(-1, Modifier.size(26.dp))
        androidx.compose.material3.Text(if (answer.isBlank()) "—" else answer, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = if (mark == LoneMark.GOOD) c.text else c.muted, modifier = Modifier.weight(1f),
            textDecoration = if (mark == LoneMark.SAME) TextDecoration.LineThrough else null)
        Box(Modifier.scale(pop.value)) {
            when (mark) {
                LoneMark.GOOD -> Badge("+$points", Good)
                LoneMark.SAME -> Badge("Same", Bad)
                LoneMark.WRONG_LETTER -> Badge("Wrong letter", Bad)
                LoneMark.NOT_LISTED -> Badge("Not on our list", c.muted)
                LoneMark.EMPTY -> Badge("0", c.muted)
            }
        }
    }
    if (mark == LoneMark.NOT_LISTED && g.countable(round, category)) Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Real words, just not on our list. Would your table count it?", style = MaterialTheme.typography.bodySmall,
            color = c.muted, modifier = Modifier.weight(1f))
        TextButton(onClick = onCount) { Text("Count it") }
    }
}

@Composable
private fun RivalAnswer(rival: Int, answer: String, mark: LoneMark, points: Int) {
    val c = LocalGameLook.current.colors
    val name = LoneLetter.RIVALS[rival]
    Row(Modifier.background(c.surfaceAlt, RoundedCornerShape(20.dp)).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
        .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Face(rival, Modifier.size(22.dp))
        androidx.compose.material3.Text(if (answer.isBlank()) "$name: —" else "$name: ${titled(answer)}", style = MaterialTheme.typography.bodyMedium,
            color = if (mark == LoneMark.GOOD) c.text else c.muted, textDecoration = if (mark == LoneMark.SAME) TextDecoration.LineThrough else null)
        if (mark == LoneMark.GOOD) androidx.compose.material3.Text("+$points", color = Good, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "new orleans" → "New Orleans". */
private fun titled(answer: String) = answer.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

@Composable
private fun Badge(text: String, color: Color) {
    Text(text, Modifier.border(1.5.dp, color, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
}

/** A little face for each player: you in the accent colour, Pip with a tuft, Juno with a bun, Otto with glasses. */
@Composable
private fun Face(who: Int, modifier: Modifier) {
    val c = LocalGameLook.current.colors
    val skin = if (who < 0) c.accent else RivalColors[who % RivalColors.size]
    Canvas(modifier) {
        val s = size.minDimension
        val center = Offset(size.width / 2, size.height / 2 + s * 0.04f)
        when (who) {
            0 -> for (k in -1..1) drawLine(skin, center + Offset(k * s * 0.08f, -s * 0.36f), center + Offset(k * s * 0.14f, -s * 0.5f), s * 0.06f, cap = StrokeCap.Round)
            1 -> drawCircle(skin, s * 0.14f, center + Offset(0f, -s * 0.4f))
        }
        drawCircle(skin, s * 0.4f, center)
        val eye = Color.White
        drawCircle(eye, s * 0.07f, center + Offset(-s * 0.14f, -s * 0.05f))
        drawCircle(eye, s * 0.07f, center + Offset(s * 0.14f, -s * 0.05f))
        if (who == 2) {
            drawCircle(Color.White, s * 0.12f, center + Offset(-s * 0.14f, -s * 0.05f), style = Stroke(s * 0.035f))
            drawCircle(Color.White, s * 0.12f, center + Offset(s * 0.14f, -s * 0.05f), style = Stroke(s * 0.035f))
        }
        // Smile.
        val w = s * 0.18f
        for (k in 0 until 5) {
            val a = k / 5f; val b = (k + 1) / 5f
            drawLine(eye, center + Offset(-w + 2 * w * a, s * 0.14f + sin(a * PI).toFloat() * s * 0.07f),
                center + Offset(-w + 2 * w * b, s * 0.14f + sin(b * PI).toFloat() * s * 0.07f), s * 0.04f, cap = StrokeCap.Round)
        }
    }
}
