package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.theme.readableOn
import com.simplegamegen.sudoku.wordplay.WordLadder

private val LadderLength = listOf(
    "Three-letter words and a short ladder, with room to wander",
    "Four-letter words and a middling ladder",
    "Four-letter words, a longer ladder, and fewer spare steps",
    "Five-letter words and a tight step limit",
)

val WordLadderSetup: (PuzzleFactory) -> PlaySetup<WordLadder> = { factory ->
    PlaySetup(
        rules = "You are given two English words of the same length. Change one letter at a time so that every step is still a real word. " +
            "Reach the target word. The shortest route is shown, and you have a few extra steps if you wander. " +
            "Tap a letter in your word, then tap the letter that replaces it. A hint plays the next step on a shortest route.",
        settingTitle = "Level",
        settings = WordLadder.NAMES,
        describe = { LadderLength[it] },
        settingOf = { it.setting },
        inProgress = { !it.over && it.steps > 0 },
        subtitle = { "${WordLadder.NAMES[it.setting]} · ${it.steps} steps · ${it.left} left" },
        create = { i -> { factory.custom("ladder:$i", { seed -> WordLadder.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            when {
                g.won -> Outcome(Result.WON, g.steps.toLong())
                g.lost -> Outcome(Result.LOST)
                else -> null
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordLadderScreen(nav: NavController, vm: PlayViewModel<WordLadder>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    PlayShell(nav, vm, GameId.WORD_LADDER, rememberSetup(factory), tools = { g, s ->
        HintButton(GameId.WORD_LADDER, enabled = !g.over && !s.busy && g.hint() != null) {
            selected = -1
            vm.play("One step along a shortest route.") { it.hint() }
        }
    }) { g, s ->
        if (selected >= g.current.length) selected = -1
        val playable = !g.over && !s.busy
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(g.target, emphasized = true)
            InfoChip("Shortest ${g.par}")
            InfoChip("${g.left} steps left", emphasized = g.left <= 2 && !g.over)
        }
        if (g.won) Text("You reached ${g.target}.", style = MaterialTheme.typography.titleMedium, color = c.success)
        if (g.lost) Text("Out of steps.", style = MaterialTheme.typography.titleMedium, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            g.current.forEachIndexed { index, letter ->
                val on = index == selected
                Surface(
                    onClick = { if (playable) selected = index },
                    enabled = playable,
                    color = if (on) c.accent else c.surface,
                    contentColor = if (on) readableOn(c.accent) else c.text,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = say("Letter ${index + 1}, $letter" + if (on) ", selected" else "")
                    },
                ) {
                    Text(letter.toString(), modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        if (playable && selected < 0) Text("Tap a letter in the word.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        if (selected in g.current.indices) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (letter in 'A'..'Z') {
                    val word = g.replacement(selected, letter)
                    Button(
                        onClick = {
                            if (word == null) vm.say("Not a word.")
                            else {
                                selected = -1
                                vm.play { it.play(word) }
                            }
                        },
                        enabled = playable && letter != g.current[selected],
                        modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = say("Change to $letter") },
                    ) { Text(letter.toString()) }
                }
            }
        }
        if (g.trail.size > 1) {
            Text(g.trail.joinToString(" → "), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun rememberSetup(factory: PuzzleFactory) = remember(factory) { WordLadderSetup(factory) }
