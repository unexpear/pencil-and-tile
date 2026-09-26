package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.SudokuApp
import com.simplegamegen.sudoku.data.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.SectionTitle
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.wordplay.MeaningEntry
import com.simplegamegen.sudoku.wordplay.MeaningGame
import com.simplegamegen.sudoku.wordplay.MeaningJudge
import com.simplegamegen.sudoku.wordplay.MeaningVerdict

val MeaningSetup: (PuzzleFactory) -> PlaySetup<MeaningGame> = { factory ->
    PlaySetup(
        rules = "Each round shows eight words, each used in a sentence. Words can have several meanings: explain the meaning used in this sentence, in your own words. " +
            "A right answer scores 2 (1 if you used a hint); a close one gets a second try and scores 1. After each word you see " +
            "the meaning used here. If the judge got it wrong, tap I was right.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level -> listOf("everyday words", "less common words", "harder words", "rare words")[level] + " · ${MeaningGame.ROUND} per round" },
        settingOf = { it.level.ordinal }, inProgress = { !it.complete },
        subtitle = { "${it.level.label} · word ${minOf(it.index + 1, it.words.size)} of ${it.words.size}" },
        create = { level -> { factory.custom("meaning:$level", { seed -> MeaningGame.generate(seed, LogicLevel.entries[level]) }) { it.words.joinToString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(if (g.score * 2 >= g.best) Result.WON else Result.FINISHED, g.score.toLong()) else null },
    )
}

@Composable
fun MeaningScreen(nav: NavController, vm: PlayViewModel<MeaningGame>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    val model = (LocalContext.current.applicationContext as SudokuApp).meaningModel
    val scope = rememberCoroutineScope()
    var judging by remember { mutableStateOf(false) }
    PlayShell(nav, vm, GameId.WORD_MEANING, remember(factory) { MeaningSetup(factory) }, undoable = false, tools = { g, s ->
        val waiting = !g.complete && g.answers.size <= g.index
        HintButton(GameId.WORD_MEANING, enabled = waiting && g.index !in g.hints && !s.busy) { vm.play("A hint is shown under the sentence.") { it.hint() } }
        ToolButton(GameIcons.Pass, "Show meaning", enabled = waiting && !s.busy) { vm.play { it.reveal() } }
    }) { g, s ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("Score ${g.score} of ${g.best}")
            if (!g.complete) InfoChip("Word ${g.index + 1} of ${g.words.size}")
        }
        val e = g.entry
        if (e != null) {
            val judged = g.answers.size > g.index
            WordCard(e, showHint = g.index in g.hints)
            // Prepare the model's view of this word's phrases while the player reads.
            LaunchedEffect(e.word) { withContext(Dispatchers.Default) { runCatching { model.judge.warm(e) } } }
            if (!judged) {
                var draft by rememberSaveable(g.seed, g.index) { mutableStateOf("") }
                fun submit() {
                    if (draft.isBlank() || judging) return
                    val guess = draft
                    judging = true
                    scope.launch {
                        // Word checks first; the model only helps when they can't decide. Without it, word checks decide alone.
                        val verdict = withContext(Dispatchers.Default) { runCatching { model.judge.judge(e, guess) }.getOrElse { MeaningJudge.judge(e, guess) } }
                        judging = false
                        val (next, _) = g.answer(guess) { _, _ -> verdict }
                        vm.play(when (verdict) {
                            MeaningVerdict.RIGHT -> "Right!"
                            MeaningVerdict.CLOSE -> if (next.tries == 1) "Close! Say a little more about what it means." else "Close: 1 point."
                            else -> "Not quite."
                        }) { next }
                        if (next.tries == 0) draft = ""
                    }
                }
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(120) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("What do you think it means?") }, minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
                if (g.tries == 1) Text("Close. One more try.", color = c.accent, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = ::submit, enabled = draft.isNotBlank() && !s.busy && !judging, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Check") }
            } else {
                Verdict(g, e)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    if (g.verdicts.last() != MeaningVerdict.RIGHT && g.answers.last().isNotBlank()) {
                        TextButton(onClick = { vm.play("Counted as right.") { it.claimRight() } }) { Text("I was right") }
                    }
                    Button(onClick = { vm.play { it.next() } }) { Text(if (g.index + 1 >= g.words.size) "See results" else "Next word") }
                }
            }
        } else {
            Surface(color = c.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Text("Round complete: ${g.score} of ${g.best} points.", Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, color = c.success)
            }
            SectionTitle("This round")
            g.words.forEachIndexed { i, w ->
                val entry = com.simplegamegen.sudoku.wordplay.MeaningBank.entry(w)!!
                val v = g.verdicts.getOrNull(i)
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(w + " · " + verdictLabel(v) + if (i in g.selfJudged) " (your call)" else "", fontWeight = FontWeight.Bold)
                    Text(entry.meaning, color = c.muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun verdictLabel(v: MeaningVerdict?) = when (v) { MeaningVerdict.RIGHT -> "Right"; MeaningVerdict.CLOSE -> "Close"; else -> "Missed" }

@Composable
private fun WordCard(e: MeaningEntry, showHint: Boolean) {
    val c = LocalGameLook.current.colors
    Surface(color = c.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Text(e.word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = c.text)
                Text(e.kind, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = c.muted)
            }
            // The sentence with the word picked out.
            val at = e.sentence.lowercase().indexOf(e.word.lowercase())
            val end = if (at < 0) -1 else (at + e.word.length until e.sentence.length).firstOrNull { !e.sentence[it].isLetter() } ?: e.sentence.length
            androidx.compose.material3.Text(buildAnnotatedString {
                if (at < 0) append(e.sentence) else {
                    append(e.sentence.substring(0, at))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.accent)) { append(e.sentence.substring(at, end)) }
                    append(e.sentence.substring(end))
                }
            }, style = MaterialTheme.typography.bodyLarge, color = c.text)
            if (showHint) Text("Hint: think of \"${e.right.first()}\".", color = c.muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Verdict(g: MeaningGame, e: MeaningEntry) {
    val c = LocalGameLook.current.colors
    val v = g.verdicts.last()
    val tint: Color = when (v) { MeaningVerdict.RIGHT -> c.success; MeaningVerdict.CLOSE -> c.accent; else -> c.danger }
    Surface(color = tint.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(when (v) { MeaningVerdict.RIGHT -> "Right!"; MeaningVerdict.CLOSE -> "Close"; else -> if (g.answers.last().isBlank()) "Here it is" else "Not quite" },
                style = MaterialTheme.typography.titleMedium, color = if (v == MeaningVerdict.WRONG) c.text else tint)
            if (g.answers.last().isNotBlank()) Text("You said: ${g.answers.last()}", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Text("In this sentence: ${e.meaning}", style = MaterialTheme.typography.bodyLarge, color = c.text)
        }
    }
}
