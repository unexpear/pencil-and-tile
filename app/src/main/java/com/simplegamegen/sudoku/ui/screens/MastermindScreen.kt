package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.KeyPegs
import com.simplegamegen.sudoku.duels.Mastermind
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.drawCodePeg
import com.simplegamegen.sudoku.ui.assets.drawKeyPeg
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

private val Board = Color(0xFF2C2C34)
private val Hole = Color(0xFF141418)

val MastermindSetup: (PuzzleFactory) -> PlaySetup<Mastermind> = { factory ->
    PlaySetup(
        rules = "Break the computer's secret code of four colored pegs, chosen from six colors. The same color may appear more than once. " +
            "You have ten guesses. Filled key pegs count colors in the right place. Open key pegs count colors that belong in the code but sit in the wrong place. " +
            "The keys are only counts. Tap a color, then Guess.",
        settingTitle = "Difficulty",
        settings = Mastermind.NAMES,
        describe = { Mastermind.CONSTRAINTS[it] },
        settingOf = { it.setting },
        inProgress = { !it.over && (it.guesses.isNotEmpty() || it.draft.isNotEmpty()) },
        subtitle = {
            val shown = if (it.over) it.guesses.size else it.guesses.size + 1
            "${Mastermind.NAMES[it.setting]} · guess $shown of ${Mastermind.GUESSES}"
        },
        create = { i -> { factory.custom("mastermind:$i", { seed -> Mastermind.start(seed, i) }) { it.secret.joinToString(",") } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            when {
                g.won -> Outcome(Result.WON, g.guesses.size.toLong())
                g.lost -> Outcome(Result.LOST)
                else -> null
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MastermindScreen(nav: NavController, vm: PlayViewModel<Mastermind>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.MASTERMIND, remember(factory) { MastermindSetup(factory) }) { g, s ->
        val playable = !g.over && !s.busy && !s.thinking
        if (!g.over) {
            val left = g.guessesLeft
            InfoChip(if (left == 1) "1 guess left" else "$left guesses left")
        }
        if (g.won) {
            val n = g.guesses.size
            Text(if (n == 1) "You broke the code in 1 guess!" else "You broke the code in $n guesses!",
                style = MaterialTheme.typography.titleMedium, color = c.success)
        }
        if (g.lost) Text("Out of guesses.", style = MaterialTheme.typography.titleMedium, color = c.muted)
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 6.dp
                    val peg = minOf((maxWidth - 20.dp - gap * 4) / 5, 52.dp)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Board).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (g.over) {
                            Text("Secret code", style = MaterialTheme.typography.labelMedium, color = Color(0xFFF4F1EA))
                            CodeRow(g.secret, peg)
                        } else HiddenRow(peg)
                        for (row in 0 until Mastermind.GUESSES) {
                            val guess = g.guesses.getOrNull(row)
                            val draft = if (guess == null && row == g.guesses.size && !g.over) g.draft else null
                            GuessRow(row, guess, draft, g.feedback.getOrNull(row), peg)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(16.dp)) { drawKeyPeg(open = false) }
            Text("Right place", style = MaterialTheme.typography.bodySmall, color = c.muted)
            Canvas(Modifier.size(16.dp)) { drawKeyPeg(open = true) }
            Text("Wrong place", style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        if (g.setting == 0) Text("Four colors, each used once. The colors in play are marked.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        if (playable) Text("Tap a color, then Guess.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Mastermind.COLOR_NAMES.forEachIndexed { color, name ->
                val allowed = g.setting != 0 || color in g.palette
                Box(Modifier.size(48.dp).alpha(if (allowed) 1f else 0.28f)
                    .clickable(enabled = playable && allowed && g.draft.size < Mastermind.PEGS, role = Role.Button) { vm.play { it.place(color) } }
                    .semantics { contentDescription = say(name) }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) { drawCodePeg(color) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.play { it.backspace() } }, enabled = playable && g.draft.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Delete") }
            Button(onClick = {
                val problem = g.problem()
                if (problem != null) vm.say(problem) else vm.play { it.submit() }
            }, enabled = playable && g.draft.size == Mastermind.PEGS, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Guess") }
        }
    }
}

@Composable
private fun HiddenRow(peg: Dp) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = say("Hidden code") },
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(Mastermind.PEGS) { Canvas(Modifier.size(peg)) { drawCircle(Hole, size.minDimension * 0.38f) } }
    }
}

@Composable
private fun CodeRow(secret: List<Int>, peg: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        secret.forEachIndexed { index, color ->
            Peg(color, peg, "Secret code, peg ${index + 1}, ${Mastermind.COLOR_NAMES[color]}")
        }
    }
}

@Composable
private fun GuessRow(row: Int, guess: List<Int>?, draft: List<Int>?, keys: KeyPegs?, peg: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (col in 0 until Mastermind.PEGS) {
                val color = guess?.getOrNull(col) ?: draft?.getOrNull(col)
                val spoken = if (color == null) "Guess ${row + 1}, peg ${col + 1}, empty"
                else "Guess ${row + 1}, peg ${col + 1}, ${Mastermind.COLOR_NAMES[color]}"
                Peg(color, peg, spoken)
            }
        }
        if (keys != null) KeyCluster(row, keys, peg)
    }
}

@Composable
private fun Peg(color: Int?, peg: Dp, spoken: String) {
    Box(Modifier.size(peg).semantics { contentDescription = say(spoken) }) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Hole, size.minDimension * 0.4f)
            if (color != null) drawCodePeg(color)
        }
    }
}

@Composable
private fun KeyCluster(row: Int, keys: KeyPegs, peg: Dp) {
    val spoken = if (keys.black == 0 && keys.white == 0) "Guess ${row + 1}, no matching colors"
    else "Guess ${row + 1}, ${keys.black} right place, ${keys.white} wrong place"
    Canvas(Modifier.size(peg).semantics { contentDescription = say(spoken) }) {
        val cell = size.minDimension / 2
        val ink = Color(0xFFF4F1EA)
        for (r in 0 until 2) for (c in 0 until 2) {
            val index = r * 2 + c
            val center = Offset((c + 0.5f) * cell, (r + 0.5f) * cell)
            val radius = cell * 0.28f
            drawCircle(Hole, cell * 0.38f, center)
            when {
                index < keys.black -> drawCircle(ink, radius, center)
                index < keys.black + keys.white -> drawCircle(ink, radius, center, style = Stroke(radius * 0.45f))
            }
        }
    }
}
