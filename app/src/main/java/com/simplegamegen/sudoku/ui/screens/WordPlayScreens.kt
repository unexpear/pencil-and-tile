package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.HintButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.SectionTitle
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.wordplay.AcrosticGame
import com.simplegamegen.sudoku.wordplay.Cryptogram
import com.simplegamegen.sudoku.wordplay.ScrambleGame
import com.simplegamegen.sudoku.wordplay.ScrambleStatus
import java.util.Locale
import com.simplegamegen.sudoku.ui.i18n.say

private val KeyRows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

/** QWERTY letter keys; [state] may dim or mark keys. */
@Composable
internal fun LetterKeys(onKey: (Char) -> Unit, dim: (Char) -> Boolean = { false }, extra: @Composable (() -> Unit)? = null) {
    val c = LocalGameLook.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyRows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
                row.forEach { ch ->
                    Surface(onClick = { onKey(ch) }, modifier = Modifier.weight(1f, fill = false).width(33.dp).height(46.dp)
                        .semantics { contentDescription = say("Letter $ch") },
                        color = if (dim(ch)) c.surfaceAlt else c.surface, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, c.outline)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(ch.toString(), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (dim(ch)) c.muted else c.text)
                        }
                    }
                }
                if (r == 2 && extra != null) extra()
            }
        }
    }
}

// ---------------- Cryptogram ----------------

val CryptogramSetup: (PuzzleFactory) -> PlaySetup<Cryptogram> = { factory ->
    PlaySetup(
        rules = "Each letter of a saying has been swapped for another letter. Tap a coded letter, then type the letter you " +
            "think it stands for; every copy of that coded letter updates. No letter stands for itself.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { listOf("Short saying, 3 letters given", "Medium saying, 1 letter given", "Longer saying, no letters given", "Longest sayings, no letters given")[it] },
        settingOf = { it.level.ordinal }, inProgress = { !it.complete },
        create = { level -> { factory.custom("cryptogram:$level", { seed -> Cryptogram.generate(seed, LogicLevel.entries[level]) }) { it.quote } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CryptogramScreen(nav: NavController, vm: PlayViewModel<Cryptogram>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var selected by rememberSaveable { mutableStateOf<Char?>(null) }
    var checked by remember { mutableStateOf(emptySet<Char>()) }
    PlayShell(nav, vm, GameId.CRYPTOGRAM, remember(factory) { CryptogramSetup(factory) }, tools = { g, s ->
        ToolButton(GameIcons.Check, "Check", enabled = !g.complete && !s.busy) {
            checked = g.mistakes()
            vm.say(if (checked.isEmpty()) "No wrong letters so far." else (if (checked.size == 1) "1 letter is wrong, marked in red." else "${checked.size} letters are wrong, marked in red."))
        }
        HintButton(GameId.CRYPTOGRAM, enabled = !g.complete && !s.busy) { checked = emptySet(); vm.play("One letter revealed.") { it.hint(selected) } }
    }) { g, _ ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.letters.count { g.guessOf(it) != null }} / ${g.letters.size} letters")
            if (g.hints > 0) InfoChip("${g.hints} hints", icon = GameIcons.Hint)
        }
        if (g.complete) Surface(color = c.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Solved!", style = MaterialTheme.typography.titleMedium, color = c.success)
                Text("“${g.quote}” — ${g.author}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                g.encoded.split(' ').forEach { word ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        word.forEach { ch ->
                            if (ch in 'A'..'Z') {
                                val guess = g.guessOf(ch)
                                val isSelected = selected == ch
                                val given = ch in g.given
                                Column(Modifier.width(22.dp).background(if (isSelected) c.accentSoft else c.surface, MaterialTheme.shapes.extraSmall)
                                    .clickable(role = Role.Button) { selected = if (isSelected) null else ch }
                                    .semantics { contentDescription = say("Coded $ch, " + (guess?.let { "guessed $it" } ?: "unsolved")) },
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(guess?.toString() ?: " ", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                                        color = when { ch in checked -> c.danger; given -> c.text; else -> c.accent })
                                    Box(Modifier.width(18.dp).height(2.dp).background(if (isSelected) c.accent else c.outline))
                                    Text(ch.toString(), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = c.muted)
                                }
                            } else Text(ch.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
        if (!g.complete) {
            Text(selected?.let { "Coded $it · type its letter" } ?: "Tap a coded letter to choose it.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            LetterKeys(onKey = { ch -> selected?.let { sel -> checked = emptySet(); vm.play { it.guess(sel, ch) } } },
                dim = { ch -> g.letters.any { g.guessOf(it) == ch } }) {
                OutlinedButton(onClick = { selected?.let { sel -> vm.play { it.guess(sel, null) } } }, modifier = Modifier.height(46.dp)) { Text("Clear") }
            }
        }
    }
}

// ---------------- Word scramble ----------------

val ScrambleSetup: (PuzzleFactory) -> PlaySetup<ScrambleGame> = { factory ->
    PlaySetup(
        rules = "Unjumble each word by tapping its letters in order. Any real word using all the letters counts. " +
            "Stuck? Shuffle the tiles, reveal the next letter, or skip and come back later. Eight words per round.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level -> ScrambleGame.lengths(LogicLevel.entries[level]).let { "${it.first}–${it.last} letter words · ${ScrambleGame.ROUND} per round" } },
        settingOf = { it.level.ordinal }, inProgress = { !it.complete },
        subtitle = { "${it.level.label} · ${it.solved} of ${it.words.size} solved" },
        create = { level -> { factory.custom("scramble:$level", { seed -> ScrambleGame.generate(seed, LogicLevel.entries[level]) }) { it.words.joinToString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(if (g.solved == g.words.size) Result.WON else Result.FINISHED, g.solved.toLong()) else null },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScrambleScreen(nav: NavController, vm: PlayViewModel<ScrambleGame>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    fun submitIfFull(g: ScrambleGame) {
        if (g.typed.length != g.word.length) return
        val (next, ok) = g.submit()
        if (ok) vm.play("Correct!") { next } else vm.say("Not a word from the list. Try again.")
    }
    PlayShell(nav, vm, GameId.SCRAMBLE, remember(factory) { ScrambleSetup(factory) }, tools = { g, s ->
        ToolButton(GameIcons.Restart, "Shuffle", enabled = !g.complete && !s.busy) { vm.play(record = false) { it.shuffle() } }
        HintButton(GameId.SCRAMBLE, enabled = !g.complete && !s.busy) { vm.play("One letter revealed.") { it.hint() } }
        ToolButton(GameIcons.Pass, "Skip", enabled = !g.complete && !s.busy) { vm.play("Skipped. It comes back at the end.") { it.skip() } }
    }) { g, _ ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.solved} solved")
            InfoChip("Word ${g.index + 1} of ${g.words.size}")
            if (g.hints > 0) InfoChip("${g.hints} hints", icon = GameIcons.Hint)
        }
        if (!g.complete) {
            // Answer slots.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                g.word.indices.forEach { i ->
                    val ch = g.typed.getOrNull(i)
                    val hinted = i < g.revealed[g.index]
                    Box(Modifier.size(38.dp, 48.dp).background(if (hinted) c.highlightSoft else c.surface, MaterialTheme.shapes.small)
                        .border(1.5.dp, if (ch != null) c.accent else c.outline, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                        Text(ch?.toString() ?: "", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.text)
                    }
                }
            }
            // Letter tiles.
            val used = g.usedPositions()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                g.jumble.forEachIndexed { i, ch ->
                    Surface(onClick = { val next = g.type(i); if (next != g) { vm.play(record = false) { next }; submitIfFull(next) } },
                        enabled = i !in used, modifier = Modifier.size(40.dp, 52.dp).semantics { contentDescription = say("Tile $ch") },
                        color = if (i in used) c.surfaceAlt else c.accent, contentColor = if (i in used) c.muted else c.onAccent,
                        shape = MaterialTheme.shapes.small) {
                        Box(contentAlignment = Alignment.Center) { Text(ch.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                OutlinedButton(onClick = { vm.play(record = false) { it.backspace() } }) { Text("Back") }
                OutlinedButton(onClick = { vm.play(record = false) { it.clear() } }) { Text("Clear") }
            }
        } else {
            Surface(color = c.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Text("Round complete: ${g.solved} of ${g.words.size} solved.", Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, color = c.success)
            }
        }
        SectionTitle("This round")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            g.words.forEachIndexed { i, w ->
                val st = g.status[i]
                val label = if (st == ScrambleStatus.OPEN && !g.complete) "?".repeat(w.length) else w
                InfoChip(label + when (st) { ScrambleStatus.SOLVED -> " ✓"; ScrambleStatus.REVEALED -> " (shown)"; ScrambleStatus.SKIPPED -> " (skipped)"; else -> "" },
                    emphasized = i == g.index && !g.complete)
            }
        }
    }
}

// ---------------- Acrostic ----------------

val AcrosticSetup: (PuzzleFactory) -> PlaySetup<AcrosticGame> = { factory ->
    PlaySetup(
        rules = "Answer every clue. Read down the first letters of the answers to spell a hidden word. " +
            "Tap a clue, type its answer and press Enter. Hint reveals one letter of the chosen answer.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level -> AcrosticGame.keywordLengths(LogicLevel.entries[level]).let { k -> "${k.first}–${k.last} clues · answers of " +
            AcrosticGame.answerLengths(LogicLevel.entries[level]).let { "${it.first}–${it.last} letters" } } },
        settingOf = { it.level.ordinal }, inProgress = { !it.complete },
        create = { level -> { factory.custom("acrostic:$level", { seed -> AcrosticGame.generate(seed, LogicLevel.entries[level]) }) { it.keyword } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.complete) Outcome(Result.WON) else null },
    )
}

@Composable
fun AcrosticScreen(nav: NavController, vm: PlayViewModel<AcrosticGame>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var checked by remember { mutableStateOf(emptySet<Int>()) }
    PlayShell(nav, vm, GameId.ACROSTIC, remember(factory) { AcrosticSetup(factory) }, tools = { g, s ->
        ToolButton(GameIcons.Check, "Check", enabled = !g.complete && !s.busy) {
            checked = g.mistakes()
            vm.say(if (checked.isEmpty()) "No wrong letters so far." else (if (checked.size == 1) "1 answer has a wrong letter." else "${checked.size} answers have a wrong letter."))
        }
        HintButton(GameId.ACROSTIC, enabled = !g.complete && !s.busy) { checked = emptySet(); vm.play("One letter revealed.") { it.hint(selected) } }
    }) { g, _ ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.entries.indices.count { g.solved(it) }} / ${g.entries.size} answers")
            if (g.hints > 0) InfoChip("${g.hints} hints", icon = GameIcons.Hint)
        }
        Surface(color = if (g.complete) c.success.copy(alpha = 0.14f) else c.accentSoft, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(if (g.complete) "Hidden word found!" else "Hidden word", style = MaterialTheme.typography.labelLarge, color = c.muted)
                Text(g.entries.indices.joinToString(" ") { i -> g.typed[i][0].takeIf { it != '_' }?.toString() ?: "_" },
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = if (g.complete) c.success else c.accent)
            }
        }
        g.entries.forEachIndexed { i, e ->
            val active = i == selected && !g.complete
            Surface(onClick = { selected = i }, color = if (active) c.accentSoft else c.surface, shape = MaterialTheme.shapes.medium,
                border = BorderStroke(if (active) 2.dp else 1.dp, if (active) c.accent else c.outline), modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = say("Clue ${i + 1}: ${e.clue}"); stateDescription = say(if (g.solved(i)) "solved" else "${e.answer.length} letters") }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i + 1}. ${e.clue} (${e.answer.length})", style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (g.solved(i)) TextDecoration.LineThrough else null, color = if (g.solved(i)) c.muted else c.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        g.typed[i].forEachIndexed { k, ch ->
                            Box(Modifier.size(30.dp).background(if (k == 0) c.highlightSoft else c.surfaceAlt, MaterialTheme.shapes.extraSmall)
                                .border(1.dp, if (i in checked && ch != '_' && ch != e.answer[k]) c.danger else c.outline, MaterialTheme.shapes.extraSmall),
                                contentAlignment = Alignment.Center) {
                                Text(if (ch == '_') "" else ch.toString(), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (active) {
                        var draft by rememberSaveable(i, g.seed) { mutableStateOf("") }
                        fun enter() { if (draft.isNotEmpty()) { checked = emptySet(); vm.play { it.answer(i, draft) }; draft = "" } }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = draft, singleLine = true, modifier = Modifier.weight(1f), label = { Text("Answer") },
                                onValueChange = { draft = it.uppercase(Locale.ROOT).filter { ch -> ch in 'A'..'Z' }.take(e.answer.length) },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { enter() }))
                            Button(onClick = ::enter, enabled = draft.isNotEmpty(), modifier = Modifier.heightIn(min = 52.dp)) { Text("Enter") }
                        }
                    }
                }
            }
        }
    }
}
