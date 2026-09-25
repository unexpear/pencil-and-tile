package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.simplegamegen.sudoku.ui.theme.GamePalette
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.wordplay.FiveLetters
import com.simplegamegen.sudoku.wordplay.Mark

val FiveLettersSetup: (PuzzleFactory) -> PlaySetup<FiveLetters> = { factory ->
    PlaySetup(
        rules = "Find the hidden five-letter word. Type a guess and press Enter. A filled square with a bar means the letter is in the " +
            "right spot; a square with a dot means the word has that letter elsewhere; a faded square means it isn't in the word. " +
            "Hard and Expert make you reuse the letters you've found.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level ->
            val l = LogicLevel.entries[level]
            "${FiveLetters.limitOf(l)} guesses · " + listOf("any five letters", "words from the list", "found letters must be reused", "found letters must be reused")[level]
        },
        settingOf = { it.level.ordinal }, inProgress = { !it.over },
        subtitle = { "${it.level.label} · guess ${minOf(it.guesses.size + 1, it.limit)} of ${it.limit}" },
        create = { level -> { factory.custom("five:$level", { seed -> FiveLetters.generate(seed, LogicLevel.entries[level]) }) { it.answer } } },
        identity = { it.seed.toString() },
        outcome = { g -> when { g.won -> Outcome(Result.WON, (g.limit - g.guesses.size + 1).toLong()); g.lost -> Outcome(Result.LOST); else -> null } },
    )
}

@Composable
fun FiveLettersScreen(nav: NavController, vm: PlayViewModel<FiveLetters>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    var typed by rememberSaveable { mutableStateOf("") }
    PlayShell(nav, vm, GameId.FIVE_LETTERS, remember(factory) { FiveLettersSetup(factory) }, undoable = false, tools = { g, s ->
        HintButton(GameId.FIVE_LETTERS, enabled = !g.over && !s.busy && g.hint() != null) { vm.play("One letter shown.") { it.hint() } }
    }) { g, s ->
        fun enter() {
            val problem = g.problem(typed)
            if (problem != null) { vm.say(problem); return }
            val next = g.guess(typed) ?: return
            typed = ""
            vm.play(when { next.won -> "Solved in ${next.guesses.size}!"; next.lost -> "The word was ${next.answer}."; else -> null }) { next }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.limit - g.guesses.size} guesses left")
            g.revealed.sorted().forEach { InfoChip("Letter ${it + 1}: ${g.answer[it]}", icon = GameIcons.Hint) }
        }
        if (g.won) Surface(color = c.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Text("Solved in ${g.guesses.size}!", Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, color = c.success)
        }
        if (g.lost) Surface(color = c.danger.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Text("The word was ${g.answer}.", Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf((maxWidth - 24.dp) / 5, if (g.limit > 6) 46.dp else 52.dp)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (r in 0 until g.limit) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val guess = g.guesses.getOrNull(r)
                    val marks = guess?.let(g::marks)
                    for (i in 0..4) {
                        val ch = guess?.get(i) ?: if (r == g.guesses.size && !g.over) typed.getOrNull(i) else null
                        LetterSquare(ch, marks?.get(i), side, current = r == g.guesses.size && !g.over)
                    }
                }
            }
        }
        if (!g.over) FiveKeyboard(g.keyMarks(), enabled = !s.busy,
            onKey = { ch -> if (typed.length < 5) typed += ch },
            onBack = { typed = typed.dropLast(1) },
            onEnter = ::enter)
    }
}

@Composable
private fun LetterSquare(ch: Char?, mark: Mark?, side: Dp, current: Boolean) {
    val c = LocalGameLook.current.colors
    val (fill, ink) = markColors(c, mark)
    val spoken = ch?.let { "$it, " + when (mark) { Mark.RIGHT -> "right spot"; Mark.ELSEWHERE -> "in the word, wrong spot"; Mark.ABSENT -> "not in the word"; null -> "typed" } } ?: "empty"
    Box(Modifier.size(side).background(fill, MaterialTheme.shapes.small)
        .border(if (mark == null) 2.dp else 0.dp, if (current && ch != null) c.accent else c.outline, MaterialTheme.shapes.small)
        .semantics { contentDescription = say(spoken) }, contentAlignment = Alignment.Center) {
        if (ch != null) androidx.compose.material3.Text(ch.toString(), fontSize = (side.value * 0.46f).sp, fontWeight = FontWeight.Bold, color = ink)
        // Shapes as well as colors, so the marks read without color.
        when (mark) {
            Mark.RIGHT -> Box(Modifier.align(Alignment.BottomCenter).padding(bottom = side * 0.1f).width(side * 0.5f).height(3.dp).background(ink, CircleShape))
            Mark.ELSEWHERE -> Box(Modifier.align(Alignment.TopEnd).padding(side * 0.1f).size(side * 0.14f).background(ink, CircleShape))
            else -> {}
        }
    }
}

private fun markColors(c: GamePalette, mark: Mark?) = when (mark) {
    Mark.RIGHT -> c.success to c.surface
    Mark.ELSEWHERE -> c.highlight to c.text
    Mark.ABSENT -> c.muted to c.surface
    null -> c.surface to c.text
}

@Composable
private fun FiveKeyboard(marks: Map<Char, Mark>, enabled: Boolean, onKey: (Char) -> Unit, onBack: () -> Unit, onEnter: () -> Unit) {
    val c = LocalGameLook.current.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM").forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                if (r == 2) Key("Enter", c.accent, c.onAccent, 1.6f, enabled, onClick = onEnter)
                row.forEach { ch ->
                    val (fill, ink) = if (marks[ch] == null) c.surfaceAlt to c.text else markColors(c, marks[ch])
                    Key(ch.toString(), fill, ink, 1f, enabled) { onKey(ch) }
                }
                if (r == 2) Key("⌫", c.surfaceAlt, c.text, 1.4f, enabled, spoken = "Delete", onClick = onBack)
            }
        }
    }
}

@Composable
private fun Key(label: String, fill: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color, weight: Float, enabled: Boolean,
    spoken: String = label, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, color = fill, contentColor = ink, shape = MaterialTheme.shapes.small,
        modifier = Modifier.width((30 * weight).dp).height(44.dp).semantics { contentDescription = say(spoken) }) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = if (label.length > 1) 13.sp else 17.sp, fontWeight = FontWeight.Bold) }
    }
}
