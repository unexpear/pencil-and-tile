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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.theme.readableOn
import com.simplegamegen.sudoku.wordplay.EnglishThreads
import com.simplegamegen.sudoku.wordplay.ThreadRule
import com.simplegamegen.sudoku.wordplay.ThreadsGame
import kotlin.random.Random

/** What a thread's words have in common, as shown once it's found or hinted. */
fun threadLabel(ruleId: String): String = when (val r = EnglishThreads.lexicon.rule(ruleId)) {
    is ThreadRule.Tag -> r.label
    is ThreadRule.Compound -> if (r.partnerFirst) "${r.partner}___" else "___${r.partner}"
    is ThreadRule.Hidden -> r.label
    is ThreadRule.Anagram -> r.label
    null -> ruleId
}

val ThreadsSetup: (PuzzleFactory) -> PlaySetup<ThreadsGame> = { factory ->
    PlaySetup(
        rules = "Sixteen words hide four groups of four. Select four words that share something and tap Submit. " +
            "A right group locks in and shows what links it; a wrong one costs a life. Groups can be categories, words that " +
            "join another word (___BALL), hidden words or anagrams. Some words seem to fit two groups, but only one way works for all sixteen.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level ->
            listOf("four plain categories", "categories and one word-play group, a few decoys", "more word play and decoys",
                "hidden words, anagrams and many decoys")[level] + " · ${ThreadsGame.limitOf(LogicLevel.entries[level])} lives"
        },
        settingOf = { it.level.ordinal }, inProgress = { !it.over },
        subtitle = { "${it.level.label} · ${it.solved.size.coerceAtMost(4)} of 4 found" },
        create = { level -> { factory.custom("threads:$level", { seed -> ThreadsGame.generate(seed, LogicLevel.entries[level]) }) { g -> g.threads.joinToString { it.rule } } } },
        identity = { it.seed.toString() },
        outcome = { g -> when { g.complete -> Outcome(Result.WON, (g.limit - g.mistakes).toLong()); g.lost -> Outcome(Result.LOST); else -> null } },
    )
}

@Composable
fun ThreadsScreen(nav: NavController, vm: PlayViewModel<ThreadsGame>, factory: PuzzleFactory) {
    val look = LocalGameLook.current
    val c = look.colors
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    PlayShell(nav, vm, GameId.THREADS, remember(factory) { ThreadsSetup(factory) }, undoable = false, tools = { g, s ->
        ToolButton(GameIcons.Restart, "Shuffle", enabled = !g.over && !s.busy) { vm.play(record = false) { it.shuffle(Random.Default) } }
        ToolButton(GameIcons.Eraser, "Clear", enabled = picked.isNotEmpty() && !g.over) { picked = emptyList() }
        HintButton(GameId.THREADS, enabled = !g.over && !s.busy && g.threads.indices.any { it !in g.solved && it !in g.shown }) {
            vm.play("One connection shown.") { it.hint() }
        }
    }) { g, s ->
        val live = picked.filter { it in g.order }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            InfoChip("${g.solved.size.coerceAtMost(4)} of 4 found")
            LivesRow(g.limit - g.mistakes, g.limit)
            if (g.hints > 0) InfoChip("${g.hints} hints", icon = GameIcons.Hint)
        }
        // Found threads, easiest color first.
        g.solved.forEach { t -> ThreadBand(g, t) }
        if (g.complete) Surface(color = c.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Text(if (g.mistakes == 0) "All four threads, no mistakes!" else "All four threads found!", Modifier.padding(14.dp),
                style = MaterialTheme.typography.titleMedium, color = c.success)
        }
        if (g.lost) Surface(color = c.danger.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Text("Out of lives. The threads are shown above.", Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, color = c.text)
        }
        // Hinted connections that aren't found yet.
        g.shown.filter { it !in g.solved }.sorted().forEach { t -> InfoChip("Hint: ${threadLabel(g.threads[t].rule)}", icon = GameIcons.Hint) }
        val density = LocalDensity.current
        if (g.order.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 6.dp
            val tile = (maxWidth - gap * 3) / 4
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                g.order.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { word ->
                            val on = word in live
                            // Long words shrink so every tile keeps one line.
                            val size = (minOf(17f, tile.value / (word.length * 0.66f + 0.8f)) / density.fontScale).sp
                            Surface(
                                onClick = { picked = if (on) live - word else if (live.size < 4) live + word else live },
                                enabled = !g.over && !s.busy,
                                color = if (on) c.accent else c.surfaceAlt, contentColor = if (on) c.onAccent else c.text,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.width(tile).height(64.dp).semantics {
                                    contentDescription = word; stateDescription = say(if (on) "selected" else "not selected")
                                },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.Text(word, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!g.over) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            OutlinedButton(onClick = { picked = emptyList() }, enabled = live.isNotEmpty()) { Text("Deselect") }
            Button(onClick = {
                val (next, verdict) = g.guess(live.toSet())
                when (verdict) {
                    ThreadsGame.Verdict.RIGHT -> { picked = emptyList(); vm.play(if (next.complete) "Solved!" else "Found: ${threadLabel(next.threads[next.solved.last()].rule)}") { next } }
                    ThreadsGame.Verdict.ONE_AWAY -> vm.play(if (next.lost) "Out of lives." else "Three of those four belong together.") { next }
                    ThreadsGame.Verdict.WRONG -> vm.play(if (next.lost) "Out of lives." else "Not a group.") { next }
                    ThreadsGame.Verdict.REPEATED -> vm.say("You already tried those four.")
                    ThreadsGame.Verdict.INVALID -> vm.say("Pick four words.")
                }
            }, enabled = live.size == 4 && !s.busy) { Text("Submit") }
        }
    }
}

/** A found thread: its connection and its four words, on a band that darkens with difficulty. */
@Composable
private fun ThreadBand(g: ThreadsGame, index: Int) {
    val look = LocalGameLook.current
    val c = look.colors
    val strength = listOf(0.22f, 0.4f, 0.62f, 0.85f)[index]
    val fill = look.gameColor(GameId.THREADS).copy(alpha = strength).compositeOver(c.surface)
    val ink = readableOn(fill)
    Surface(color = fill, contentColor = ink, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Difficulty marks, so the bands don't depend on color alone.
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(index + 1) { Box(Modifier.size(6.dp).background(ink, CircleShape)) }
            }
            Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.Start) {
                Text(threadLabel(g.threads[index].rule), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                androidx.compose.material3.Text(g.threads[index].words.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LivesRow(left: Int, total: Int) {
    val c = LocalGameLook.current.colors
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = say("$left of $total lives left") },
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(Modifier.size(12.dp).then(if (i < left) Modifier.background(c.accent, CircleShape) else Modifier.border(1.5.dp, c.outline, CircleShape)))
        }
    }
}
