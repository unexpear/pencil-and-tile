package com.simplegamegen.sudoku.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.ShipCrew
import com.simplegamegen.sudoku.duels.ShutBox
import com.simplegamegen.sudoku.duels.TenThousand
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

private val ShutStrength = listOf(
    "Flips any set that adds up.",
    "Flips the highest tile it can.",
    "Leaves a box that can still answer more rolls.",
    "Plays the box for the lowest expected score.",
)
private val TenStrength = listOf(
    "Banks as soon as the turn is worth keeping.",
    "Pushes while several dice are left.",
    "Banks when another roll is worth less than the turn.",
    "Keeps the set that leaves the better next roll.",
)
private val ShipStrength = listOf(
    "Rerolls everything except the ship, captain and crew.",
    "Keeps cargo showing 4 or more.",
    "Keeps cargo showing 4 or more, and stops on the last roll.",
    "Rerolls a 4 when two rolls are still left.",
)

val ShutBoxSetup: (PuzzleFactory) -> PlaySetup<ShutBox> = { factory ->
    PlaySetup(
        rules = "Shut the Box is nine tiles, 1 through 9. Roll two dice, or one die once every tile still up is 6 or less. " +
            "Tap a set of tiles that adds up to the roll. If nothing adds up, you score the tiles still up. Shutting every tile scores 0. " +
            "You and the computer each play a box. The lower score wins.",
        settingTitle = "Computer strength",
        settings = ShutBox.LEVELS,
        describe = { ShutStrength[it] },
        settingOf = { it.setting },
        inProgress = { it.youScore < 0 && it.drawn > 2 },
        subtitle = { "${ShutBox.LEVELS[it.setting]} · ${if (it.youScore < 0) "—" else it.youScore} to ${if (it.cpuScore < 0) "—" else it.cpuScore}" },
        create = { i -> { factory.custom("shutbox:$i", { seed -> ShutBox.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW }, g.youScore.toLong())
        },
    )
}

val TenSetup: (PuzzleFactory) -> PlaySetup<TenThousand> = { factory ->
    PlaySetup(
        rules = "Ten Thousand uses six dice. A 1 scores 100 and a 5 scores 50. Three of a kind scores 1000 for ones and 100 times the face otherwise. " +
            "Four, five and six of a kind score two, four and eight times that. 1 through 6 scores 1500, three pairs score 1500, and two threes score 2500. " +
            "Keep a scoring set, then bank or roll the rest. The first bank has to be at least 500. A roll that scores nothing loses the points of that turn. " +
            "Scoring every die means you roll all six again before you can bank. Reach 10,000 and the other player gets one more turn. The higher bank wins.",
        settingTitle = "Computer strength",
        settings = TenThousand.LEVELS,
        describe = { TenStrength[it] },
        settingOf = { it.setting },
        inProgress = { it.you > 0 || it.cpu > 0 || it.pending > 0 || it.fresh },
        subtitle = { "${TenThousand.LEVELS[it.setting]} · ${it.you} to ${it.cpu}" },
        create = { i -> { factory.custom("tenthousand:$i", { seed -> TenThousand.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW }, g.you.toLong())
        },
    )
}

val ShipSetup: (PuzzleFactory) -> PlaySetup<ShipCrew> = { factory ->
    PlaySetup(
        rules = "Ship, Captain, Crew uses five dice and up to three rolls. A ship is a 6, a captain is a 5, and a crew is a 4. " +
            "The captain counts only after the ship, and the crew only after the captain. The other two dice are then cargo. " +
            "You may stop early. You each play five hands. The higher cargo wins.",
        settingTitle = "Computer strength",
        settings = ShipCrew.LEVELS,
        describe = { ShipStrength[it] },
        settingOf = { it.setting },
        inProgress = { it.you > 0 || it.cpu > 0 || it.hand > 0 || it.rolls > 1 },
        subtitle = { "${ShipCrew.LEVELS[it.setting]} · hand ${it.hand + 1} · ${it.you} to ${it.cpu}" },
        create = { i -> { factory.custom("shipcrew:$i", { seed -> ShipCrew.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW }, g.you.toLong())
        },
    )
}

@Composable
fun ShutBoxScreen(nav: NavController, vm: PlayViewModel<ShutBox>, factory: PuzzleFactory) {
    PlayShell(nav, vm, GameId.SHUT_BOX, remember(factory) { ShutBoxSetup(factory) }) { g, s ->
        val playable = g.turn == 1 && !g.ended && !s.busy && !s.thinking
        var picked by remember(g.dice, g.youUp, g.turn, g.drawn) { mutableStateOf(setOf<Int>()) }
        DuelStatus(g.ended, g.winner, g.turn, s.thinking, scoreName("You", g.youScore), scoreName("Computer", g.cpuScore))
        if (!g.ended) DiceStrip(g.dice, List(g.dice.size) { false }, false) { }
        if (g.ended) {
            Text("Your box", style = MaterialTheme.typography.labelLarge)
            Tiles(g.youUp, emptySet(), false) { }
            Text("Computer's box", style = MaterialTheme.typography.labelLarge)
            Tiles(g.cpuUp, emptySet(), false) { }
        } else {
            Text(if (g.turn == 1) "Your box" else "Computer's box", style = MaterialTheme.typography.labelLarge)
            val board = if (g.turn == 1) g.youUp else g.cpuUp
            Tiles(board, picked, playable && g.options().isNotEmpty()) { tile ->
                picked = if (tile in picked) picked - tile else picked + tile
            }
        }
        val choices = if (playable) g.options() else emptyList()
        if (playable && choices.isEmpty()) {
            Text("No tiles add up to this roll.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { vm.play { it.take() } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Take the score") }
        } else if (playable) {
            Text("Tap tiles that add up to the roll.", style = MaterialTheme.typography.bodyMedium)
            val ready = choices.any { it == picked.toList().sorted() }
            Button(
                onClick = { vm.play { it.cover(picked.toList()) } },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Cover") }
        }
    }
}

@Composable
fun TenThousandScreen(nav: NavController, vm: PlayViewModel<TenThousand>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.TEN_THOUSAND, remember(factory) { TenSetup(factory) }) { g, s ->
        val playable = g.turn == 1 && !g.ended && !s.busy && !s.thinking
        var picked by remember(g.live, g.fresh, g.pending, g.drawn) { mutableStateOf(setOf<Int>()) }
        DuelStatus(g.ended, g.winner, g.turn, s.thinking, "You ${g.you}", "Computer ${g.cpu}")
        if (g.pending > 0 || g.turn == 1 && !g.ended) InfoChip("This turn ${g.pending}", emphasized = g.pending > 0)
        if (g.busted == 1) Text("Bust. This turn's points are lost.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        if (g.mustRoll) Text("All six scored. Roll again before you can bank.", style = MaterialTheme.typography.bodyMedium)
        if (g.you == 0 && g.turn == 1 && !g.ended) Text("The first bank has to be at least 500.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        if (g.aside.isNotEmpty()) {
            Text("Set aside", style = MaterialTheme.typography.labelLarge, color = c.muted)
            DiceStrip(g.aside, List(g.aside.size) { true }, false) { }
        }
        if (g.live.isNotEmpty()) DiceStrip(g.live, g.live.indices.map { it in picked }, playable && g.fresh) { picked = if (it in picked) picked - it else picked + it }
        if (playable) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!g.fresh) {
                    Button(onClick = { vm.play { it.roll() } }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Roll") }
                }
                if (g.fresh) {
                    val ready = TenThousand.pointsOf(picked.map { g.live[it] }) != null
                    Button(
                        onClick = { vm.play { it.score(picked.toList()) } },
                        enabled = ready,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Score") }
                }
                if (g.canBank()) {
                    OutlinedButton(onClick = { vm.play { it.bank() } }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Bank") }
                }
            }
        }
    }
}

@Composable
fun ShipCrewScreen(nav: NavController, vm: PlayViewModel<ShipCrew>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.SHIP_CREW, remember(factory) { ShipSetup(factory) }) { g, s ->
        val playable = g.turn == 1 && !g.ended && !s.busy && !s.thinking
        DuelStatus(g.ended, g.winner, g.turn, s.thinking, "Cargo ${g.you}", "Computer ${g.cpu}")
        InfoChip("Hand ${g.hand + 1} of 5")
        InfoChip("Roll ${g.rolls} of 3")
        val count = IntArray(7).also { tally -> g.dice.forEach { tally[it]++ } }
        val ship = count[6] > 0
        val captain = ship && count[5] > 0
        val crew = captain && count[4] > 0
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("Ship", emphasized = ship)
            InfoChip("Captain", emphasized = captain)
            InfoChip("Crew", emphasized = crew)
        }
        Text(if (crew) "This hand ${g.thisCargo()}" else "This hand 0", style = MaterialTheme.typography.bodyMedium, color = c.muted)
        DiceStrip(g.dice, g.held, playable && g.rolls < 3) { vm.play { state -> state.hold(it) } }
        if (playable) {
            Text("Tap a die to hold it, then roll the rest.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (g.rolls < 3) {
                    Button(
                        onClick = { vm.play { it.roll() } },
                        enabled = g.held.any { !it },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Roll again") }
                }
                OutlinedButton(onClick = { vm.play { it.stay() } }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Take cargo") }
            }
        }
    }
}

private fun scoreName(who: String, score: Int) = if (score < 0) who else "$who $score"

@Composable
private fun Tiles(up: List<Boolean>, picked: Set<Int>, enabled: Boolean, onTile: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1..5, 6..9).forEach { range ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                range.forEach { tile ->
                    val open = up[tile - 1]
                    val on = tile in picked
                    Box(
                        Modifier.weight(1f).heightIn(min = 52.dp)
                            .alpha(if (open) 1f else 0.35f)
                            .background(if (on) c.highlight.copy(alpha = 0.35f) else c.surface, RoundedCornerShape(10.dp))
                            .border(2.dp, if (on) c.accent else c.outline, RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled && open, role = Role.Button) { onTile(tile) }
                            .semantics { contentDescription = say(if (open) "Shut tile $tile" else "Shut tile $tile, down") },
                        contentAlignment = Alignment.Center,
                    ) { Text(tile.toString(), style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}

@Composable
private fun DiceStrip(faces: List<Int>, marked: List<Boolean>, enabled: Boolean, onDie: (Int) -> Unit) {
    val accent = LocalGameLook.current.colors.accent
    TablePanel {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val gap = 8.dp
            val die = minOf((maxWidth - gap * (faces.size - 1)) / faces.size, 72.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                faces.forEachIndexed { i, face ->
                    val spoken = if (marked.getOrElse(i) { false }) "Die showing $face, held" else "Die showing $face"
                    Box(
                        Modifier.size(die)
                            .clickable(enabled = enabled, role = Role.Button) { onDie(i) }
                            .semantics { contentDescription = say(spoken) },
                    ) { Canvas(Modifier.fillMaxSize()) { drawFaceDie(face, marked.getOrElse(i) { false }, accent) } }
                }
            }
        }
    }
}

private fun DrawScope.drawFaceDie(face: Int, marked: Boolean, accent: Color) {
    val spots = listOf(
        emptyList(),
        listOf(0.5f to 0.5f),
        listOf(0.28f to 0.28f, 0.72f to 0.72f),
        listOf(0.28f to 0.28f, 0.5f to 0.5f, 0.72f to 0.72f),
        listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.28f to 0.72f, 0.72f to 0.72f),
        listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.5f to 0.5f, 0.28f to 0.72f, 0.72f to 0.72f),
        listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.28f to 0.5f, 0.72f to 0.5f, 0.28f to 0.72f, 0.72f to 0.72f),
    )
    val depth = size.minDimension * 0.12f
    val top = Size(size.width - depth, size.height - depth)
    drawRect(Color(0xFFD9D0C4), Offset(depth * 0.55f, depth), Size(top.width, top.height))
    drawRoundRect(Color(0xFFFFFBF2), size = top, cornerRadius = CornerRadius(top.minDimension * 0.16f))
    drawRoundRect(
        if (marked) accent else Color(0xFF1A1A1A), size = top,
        cornerRadius = CornerRadius(top.minDimension * 0.16f),
        style = Stroke(if (marked) top.minDimension * 0.07f else top.minDimension * 0.035f),
    )
    val pip = top.minDimension * if (face == 1) 0.12f else 0.075f
    spots[face].forEach { (x, y) -> drawCircle(Color(0xFF1A1A1A), pip, Offset(top.width * x, top.height * y)) }
}
