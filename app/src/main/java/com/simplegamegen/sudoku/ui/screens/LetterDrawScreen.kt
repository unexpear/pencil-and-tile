package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.wordplay.LetterDraw
import kotlinx.coroutines.delay

private val DrawLevels = listOf(
    "8 letters · 4 or more · a minute and a half",
    "8 letters · 5 or more · one minute",
    "9 letters · 6 or more · 45 seconds",
    "9 letters · 7 or more · 30 seconds",
)

val LetterDrawSetup: (PuzzleFactory) -> PlaySetup<LetterDraw> = { factory ->
    PlaySetup(
        rules = "You are dealt a handful of letters. Spell the longest word you can before the clock runs out. " +
            "Each letter can be used only as often as it was dealt. A real word of the target length wins at once. " +
            "When time runs out, a longest accepted word from the rack is shown.",
        settingTitle = "Level",
        settings = LogicLevel.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
        describe = { DrawLevels[it] },
        settingOf = { it.level.ordinal },
        inProgress = { !it.over && (it.used > 0 || it.typed.isNotEmpty() || it.answer.isNotEmpty()) },
        subtitle = { "${LetterDraw.targetOf(it.level)} letters · ${it.left} seconds left" },
        create = { i -> { factory.custom("draw:$i", { seed -> LetterDraw.generate(seed, LogicLevel.entries[i]) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            when {
                g.won -> Outcome(Result.WON, g.answer.length.toLong())
                g.lost -> Outcome(Result.LOST, g.answer.length.toLong())
                else -> null
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LetterDrawScreen(nav: NavController, vm: PlayViewModel<LetterDraw>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.LETTER_DRAW, remember(factory) { LetterDrawSetup(factory) }) { g, s ->
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val life by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
        val latest by rememberUpdatedState(g)
        if (!g.over && life.isAtLeast(Lifecycle.State.RESUMED)) LaunchedEffect(g.seed) {
            while (true) {
                delay(1000)
                val ends = latest.used + 1 >= latest.limit
                vm.play(if (ends) "Time's up!" else null, record = false, persist = ends || latest.used % 5 == 4) { it.tick(1) }
                if (ends) break
            }
        }
        val playable = !g.over && !s.busy
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("Aim ${g.target}", emphasized = true)
            InfoChip(if (g.answer.isEmpty()) "No word yet" else g.answer)
            InfoChip("${g.left} seconds left", emphasized = g.left <= 10 && !g.over)
        }
        if (g.won) Text("You reached ${g.answer.length} letters.", style = MaterialTheme.typography.titleMedium, color = c.success)
        if (g.lost) Text("Time's up. A longest word is ${g.best}.", style = MaterialTheme.typography.titleMedium, color = c.muted)
        Text(g.typed.ifEmpty { " " }, style = MaterialTheme.typography.headlineSmall, color = c.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            g.letters.forEachIndexed { index, letter ->
                val usedEarlier = g.letters.take(index).count { it == letter }
                val spent = g.letters.count { it == letter } - g.spare(letter)
                val available = usedEarlier >= spent
                Surface(
                    onClick = { vm.play(record = false) { it.type(letter) } },
                    enabled = playable && available,
                    color = if (available) c.surface else c.surfaceAlt,
                    contentColor = if (available) c.text else c.muted,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.semantics { contentDescription = say("Letter $letter") },
                ) {
                    Text(letter.toString(), Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { vm.play(record = false) { it.backspace() } }, enabled = playable && g.typed.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Delete") }
            Button(onClick = {
                val problem = g.problem()
                if (problem != null) vm.say(problem) else vm.play(null) { it.submit() }
            }, enabled = playable && g.typed.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Enter") }
        }
    }
}
