package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.theme.readableOn
import com.simplegamegen.sudoku.wordplay.Honeycomb
import com.simplegamegen.sudoku.wordplay.HoneycombCodec

private val HoneycombLevels = listOf(
    "Words of 4 or more · a smaller share of the everyday score",
    "Words of 4 or more · a larger share",
    "Words of 5 or more · a larger share",
    "Words of 5 or more · most of the everyday score",
)

val HoneycombSetup: (PuzzleFactory) -> PlaySetup<Honeycomb> = { factory ->
    PlaySetup(
        rules = "Seven letters sit in a honeycomb. Every word must use the middle letter, and only these seven letters. " +
            "A letter may be used more than once. Everyday words score their length. Any other accepted word scores 1. " +
            "A word that uses all seven letters scores 7 more. Reach the target score.",
        settingTitle = "Level",
        settings = LogicLevel.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
        describe = { HoneycombLevels[it] },
        settingOf = { it.level.ordinal },
        inProgress = { !it.won && it.found.isNotEmpty() },
        subtitle = { "${it.score} / ${it.target}" },
        create = { i -> { factory.custom("honeycomb:$i", { seed -> Honeycomb.generate(seed, LogicLevel.entries[i]) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.won) Outcome(Result.WON, g.score.toLong()) else null },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HoneycombScreen(nav: NavController, vm: PlayViewModel<Honeycomb>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var typed by rememberSaveable { mutableStateOf("") }
    PlayShell(nav, vm, GameId.HONEYCOMB, remember(factory) { HoneycombSetup(factory) }, tools = { g, s ->
        HintButton(GameId.HONEYCOMB, enabled = !g.over && !s.busy) {
            val hint = g.hintWord()
            if (hint != null) vm.say("Try ${hint.take(2)}…")
        }
    }) { g, s ->
        val playable = !g.over && !s.busy
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.score} / ${g.target}", emphasized = true)
            InfoChip("${g.found.size} words")
        }
        if (g.won) Text("Target reached.", style = MaterialTheme.typography.titleMedium, color = c.success)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HiveRow(g, g.outer.substring(0, 2), playable) { typed += it }
            HiveRow(g, g.outer.substring(2, 3) + g.center + g.outer.substring(3, 4), playable, centerAt = 1) { typed += it }
            HiveRow(g, g.outer.substring(4, 6), playable) { typed += it }
        }
        Text(typed.ifEmpty { " " }, style = MaterialTheme.typography.headlineSmall, color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { if (typed.isNotEmpty()) typed = typed.dropLast(1) }, enabled = playable && typed.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Delete") }
            Button(onClick = {
                val problem = g.problem(typed)
                if (problem != null) vm.say(problem) else {
                    val word = typed
                    typed = ""
                    vm.play("${g.points(word)} points.") { it.play(word) }
                }
            }, enabled = playable && typed.length >= g.minLength, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Enter") }
        }
        if (g.found.isNotEmpty()) {
            Text("Found", style = MaterialTheme.typography.titleSmall, color = c.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.found.sorted().forEach { word -> InfoChip("$word ${g.points(word)}") }
            }
        }
    }
}

@Composable
private fun HiveRow(game: Honeycomb, letters: String, playable: Boolean, centerAt: Int = -1, onLetter: (Char) -> Unit) {
    val c = LocalGameLook.current.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        letters.forEachIndexed { index, letter ->
            val middle = index == centerAt
            Surface(
                onClick = { if (playable) onLetter(letter) },
                enabled = playable,
                shape = CircleShape,
                color = if (middle) c.accent else c.surface,
                contentColor = if (middle) readableOn(c.accent) else c.text,
                modifier = Modifier.semantics {
                    role = Role.Button
                    contentDescription = say(if (middle) "Middle letter $letter" else "Letter $letter")
                },
            ) {
                Text(letter.toString(), Modifier.padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
