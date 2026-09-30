package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.Mancala
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

private val Wood = Color(0xFF6D4C41)
private val WoodDark = Color(0xFF4E342E)
private val Pit = Color(0xFF3E2723)
private val Seed = Color(0xFFF3E5C4)

private val MancalaStrength = listOf(
    "Sows a pit at random",
    "Takes a capture or another turn when it sees one",
    "Looks a few sows ahead",
    "Looks further ahead for the bigger store",
)

val MancalaSetup: (PuzzleFactory) -> PlaySetup<Mancala> = { factory ->
    PlaySetup(
        rules = "Six pits on your side, four stones in each, and a store at the right. Tap a pit to sow its stones one at a time to the right, " +
            "through your store and along the computer's pits. You skip the computer's store. " +
            "If the last stone lands in your store, you go again. If it lands in an empty pit on your side, you capture that stone and every stone in the pit opposite. " +
            "When one side has no stones left, the other side keeps whatever is still in its pits. Most stones in your store wins.",
        settingTitle = "Computer strength",
        settings = Mancala.NAMES,
        describe = { MancalaStrength[it] },
        settingOf = { it.setting },
        inProgress = { !it.ended && it.last != null },
        subtitle = { "${Mancala.NAMES[it.setting]} · you ${it.pits[Mancala.YOU]}, computer ${it.pits[Mancala.CPU]}" },
        create = { i -> { factory.custom("mancala:$i", { seed -> Mancala.start(seed, i) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g ->
            if (!g.ended) null
            else Outcome(when (g.winner) { 1 -> Result.WON; -1 -> Result.LOST; else -> Result.DRAW }, g.pits[Mancala.YOU].toLong())
        },
    )
}

@Composable
fun MancalaScreen(nav: NavController, vm: PlayViewModel<Mancala>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    PlayShell(nav, vm, GameId.MANCALA, remember(factory) { MancalaSetup(factory) }) { g, s ->
        DuelStatus(g.over, g.winner, g.turn, s.thinking, "You ${g.pits[Mancala.YOU]}", "Computer ${g.pits[Mancala.CPU]}")
        val playable = !g.ended && g.turn == 1 && !s.thinking && !s.busy
        if (!g.ended && g.turn == 1 && g.last == Mancala.YOU) {
            Text("Landed in your store. Go again.", style = MaterialTheme.typography.bodyLarge, color = c.success)
        }
        TablePanel {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Store(g.pits[Mancala.CPU], "Computer's store, ${g.pits[Mancala.CPU]} stones", Modifier.width(52.dp).height(112.dp), g.last == Mancala.CPU)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (shown in 0 until Mancala.PITS) {
                            val pit = 12 - shown
                            PitWell(g.pits[pit], "Computer's pit ${shown + 1}, ${g.pits[pit]} stones", g.last == pit, enabled = false, onSow = {}, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (pit in 0 until Mancala.PITS) {
                            val open = playable && g.pits[pit] > 0
                            PitWell(
                                g.pits[pit],
                                "Your pit ${pit + 1}, ${g.pits[pit]} stones" + if (open) ", sow" else "",
                                g.last == pit,
                                enabled = open,
                                onSow = { vm.play { it.sow(pit) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Store(g.pits[Mancala.YOU], "Your store, ${g.pits[Mancala.YOU]} stones", Modifier.width(52.dp).height(112.dp), g.last == Mancala.YOU)
            }
        }
        if (!g.ended && playable) Text("Tap one of your pits. Stones sow to the right.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
    }
}

@Composable
private fun Store(count: Int, spoken: String, modifier: Modifier, landed: Boolean) {
    val ring = LocalGameLook.current.colors.highlight
    Box(modifier.clip(RoundedCornerShape(12.dp)).semantics { contentDescription = say(spoken) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(WoodDark, cornerRadius = CornerRadius(12.dp.toPx()))
            drawRoundRect(Pit, topLeft = Offset(8f, 8f), size = Size(size.width - 16f, size.height - 16f), cornerRadius = CornerRadius(10.dp.toPx()))
            if (landed) drawRoundRect(ring, style = Stroke(4f), cornerRadius = CornerRadius(12.dp.toPx()))
        }
        Text("$count", style = MaterialTheme.typography.titleLarge, color = Seed)
    }
}

@Composable
private fun PitWell(count: Int, spoken: String, landed: Boolean, enabled: Boolean, onSow: () -> Unit, modifier: Modifier) {
    val ring = LocalGameLook.current.colors.highlight
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(26.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onSow)
            .semantics { contentDescription = say(spoken) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Wood, size.minDimension * 0.5f)
            drawCircle(Pit, size.minDimension * 0.38f)
            val shown = count.coerceAtMost(6)
            if (shown > 0) {
                val radius = size.minDimension * 0.08f
                for (i in 0 until shown) {
                    val angle = (i * 360f / shown) * (Math.PI / 180.0)
                    val at = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * 0.16f,
                        center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * 0.16f,
                    )
                    drawCircle(Seed, radius, at)
                }
            }
            if (landed) drawCircle(ring, size.minDimension * 0.46f, style = Stroke(size.minDimension * 0.06f))
        }
        Text("$count", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}
