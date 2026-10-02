package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.assets.DiceTray
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.Yacht
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

private val YachtStrength = listOf(
    "The computer takes a strong roll and rerolls a weak one.",
    "The computer holds pairs and short straights.",
    "The computer studies the next roll.",
    "The computer studies the next roll and keeps a chase going.",
)

private val DieFace = Color(0xFFFFFBF2)
private val DieInk = Color(0xFF1A1A1A)
private val DieSide = Color(0xFFD9D0C4)
private val PipSpots = listOf(
    emptyList(),
    listOf(0.5f to 0.5f),
    listOf(0.28f to 0.28f, 0.72f to 0.72f),
    listOf(0.28f to 0.28f, 0.5f to 0.5f, 0.72f to 0.72f),
    listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.28f to 0.72f, 0.72f to 0.72f),
    listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.5f to 0.5f, 0.28f to 0.72f, 0.72f to 0.72f),
    listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.28f to 0.5f, 0.72f to 0.5f, 0.28f to 0.72f, 0.72f to 0.72f),
)

val YachtSetup: (PuzzleFactory) -> PlaySetup<Yacht> = { factory ->
    PlaySetup(
        rules = "Yacht is five dice and thirteen boxes. Roll up to three times on your turn. Tap a die to hold it, then roll the rest. " +
            "Tap an open box to score the roll. Each box is used once. Ones through Sixes add that face, and 63 or more there scores 35 more. " +
            "Three and four of a kind add all five dice. A full house scores 25, a small straight 30, a large straight 40, and five of a kind scores 50. " +
            "Chance adds all five dice. After that box scores 50, each later five of a kind adds 100. It fills the matching upper box when that box is open. " +
            "Otherwise it scores as a match in any open lower box, or 0 in an open upper box if the lower boxes are full. " +
            "You and the computer each fill a sheet. The higher total wins.",
        settingTitle = "Computer strength",
        settings = Yacht.LEVELS,
        describe = { YachtStrength[it] },
        settingOf = { it.setting },
        inProgress = { g -> !g.ended && (g.you.any { it >= 0 } || g.rolls > 1 || g.held.any { it }) },
        subtitle = { "${Yacht.LEVELS[it.setting]} · ${it.yourTotal()}–${it.cpuTotal()}" },
        create = { i -> { factory.custom("yacht:$i", { seed -> Yacht.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW }, g.yourTotal().toLong())
        },
    )
}

@Composable
fun YachtScreen(nav: NavController, vm: PlayViewModel<Yacht>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.YACHT, remember(factory) { YachtSetup(factory) }) { g, s ->
        val playable = !g.ended && g.turn == 1 && !s.busy && !s.thinking
        val canHold = playable && g.rolls < 3
        val legal = if (playable) Yacht.legalBoxes(g.you, g.dice).toSet() else emptySet()
        DuelStatus(g.ended, g.winner, g.turn, s.thinking, "You ${g.yourTotal()}", "Computer ${g.cpuTotal()}")
        if (g.rolls in 1..3) InfoChip("Roll ${g.rolls} of 3")
        // The dice tumble on a felt tray when they're rolled; tap one to hold it.
        DiceTray(g.dice, g.held, canHold, rollKey = g.rolls to g.dice) { i -> vm.play { it.hold(i) } }
        if (playable && g.rolls < 3) {
            Button(
                onClick = { vm.play { it.roll() } },
                enabled = g.held.any { !it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Roll again") }
        }
        if (playable && g.rolls < 3 && g.held.all { it }) {
            Text("Release a die to roll it again.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
        if (playable) {
            Text(
                if (g.rolls < 3) "Tap a die to hold it. Tap an open box to score this roll." else "Tap an open box to score this roll.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted,
            )
        }
        jokerNote(g, playable)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.text) }
        Column(
            Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).border(1.dp, c.outline, RoundedCornerShape(12.dp))
                .semantics { contentDescription = say("Yacht score sheet") },
        ) {
            SheetHead()
            Text("Upper section", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = c.muted)
            for (box in 0..5) ScoreBox(g, box, legal, playable) { vm.play { it.score(box) } }
            ScoreValue("Upper bonus", Yacht.upperBonus(g.you), Yacht.upperBonus(g.cpu), bold = false)
            Text("Lower section", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = c.muted)
            for (box in Yacht.THREE..Yacht.CHANCE) ScoreBox(g, box, legal, playable) { vm.play { it.score(box) } }
            ScoreValue("Extra five of a kind", g.youExtra * 100, g.cpuExtra * 100, bold = false)
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline))
            ScoreValue("Total", g.yourTotal(), g.cpuTotal(), bold = true)
        }
    }
}

private fun jokerNote(g: Yacht, playable: Boolean): String? {
    if (!playable || g.dice.distinct().size != 1 || g.you[Yacht.YACHT] != 50) return null
    val upper = g.dice[0] - 1
    return when {
        g.you[upper] < 0 -> "Five of a kind adds 100. It fills the matching upper box."
        (Yacht.THREE..Yacht.CHANCE).any { g.you[it] < 0 } -> "Five of a kind adds 100. Pick an open lower box."
        else -> "Five of a kind adds 100. Pick an open upper box."
    }
}

@Composable
private fun SheetHead() {
    val c = LocalGameLook.current.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("", Modifier.weight(1.6f), style = MaterialTheme.typography.labelLarge, color = c.muted)
        Text("You", Modifier.weight(0.7f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = c.muted)
        Text("Computer", Modifier.weight(0.9f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = c.muted)
    }
}

@Composable
private fun ScoreBox(g: Yacht, box: Int, legal: Set<Int>, playable: Boolean, onScore: () -> Unit) {
    val c = LocalGameLook.current.colors
    val name = Yacht.NAMES[box]
    val yours = g.you[box]
    val theirs = g.cpu[box]
    val tappable = playable && box in legal
    val preview = if (tappable) Yacht.points(g.dice, g.you, box) else -1
    val youText = when {
        yours >= 0 -> yours.toString()
        tappable -> preview.toString()
        else -> "·"
    }
    val cpuText = if (theirs >= 0) theirs.toString() else "·"
    val youSpoken = if (youText == "·") "empty" else youText
    val cpuSpoken = if (cpuText == "·") "empty" else cpuText
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (tappable) 52.dp else 44.dp)
            .background(if (preview > 0) c.highlight.copy(alpha = 0.16f) else Color.Transparent)
            .then(if (tappable) Modifier.clickable(role = Role.Button, onClick = onScore) else Modifier)
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = say("$name, $youSpoken, $cpuSpoken") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, Modifier.weight(1.6f), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        Text(youText, Modifier.weight(0.7f), textAlign = TextAlign.Center, color = if (yours >= 0) c.text else c.muted,
            style = MaterialTheme.typography.bodyLarge)
        Text(cpuText, Modifier.weight(0.9f), textAlign = TextAlign.Center, color = if (theirs >= 0) c.text else c.muted,
            style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ScoreValue(name: String, you: Int, cpu: Int, bold: Boolean) {
    val c = LocalGameLook.current.colors
    val style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp)
            .semantics { contentDescription = say("$name, $you, $cpu") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, Modifier.weight(1.6f), style = style, fontWeight = weight, maxLines = 2)
        Text(you.toString(), Modifier.weight(0.7f), textAlign = TextAlign.Center, style = style, fontWeight = weight)
        Text(cpu.toString(), Modifier.weight(0.9f), textAlign = TextAlign.Center, style = style, fontWeight = weight)
    }
}

private fun DrawScope.drawYachtDie(face: Int, held: Boolean, accent: Color) {
    val depth = size.minDimension * 0.12f
    val top = Size(size.width - depth, size.height - depth)
    drawRect(DieSide, Offset(depth * 0.55f, depth), Size(top.width, top.height))
    drawRoundRect(DieFace, size = top, cornerRadius = CornerRadius(top.minDimension * 0.16f))
    drawRoundRect(
        if (held) accent else DieInk,
        size = top,
        cornerRadius = CornerRadius(top.minDimension * 0.16f),
        style = Stroke(if (held) top.minDimension * 0.07f else top.minDimension * 0.035f),
    )
    val spots = PipSpots[face]
    val pip = top.minDimension * if (spots.size == 1) 0.12f else 0.075f
    spots.forEach { (x, y) -> drawCircle(DieInk, pip, Offset(top.width * x, top.height * y)) }
}
