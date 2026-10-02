package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.HintButton
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.cards.MemoryGame
import com.simplegamegen.sudoku.cards.PyramidGame
import com.simplegamegen.sudoku.cards.SpiderGame
import com.simplegamegen.sudoku.cards.SpiderMove
import com.simplegamegen.sudoku.cards.rankOf
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.tabletop.SolitaireState
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.CardSlot
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.MahjongTile
import com.simplegamegen.sudoku.ui.assets.PlayingCard
import com.simplegamegen.sudoku.ui.assets.TILE_ASPECT
import com.simplegamegen.sudoku.ui.assets.drawCardBack
import com.simplegamegen.sudoku.ui.assets.mahjongName
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlinx.coroutines.delay
import com.simplegamegen.sudoku.ui.i18n.say

@Composable
internal fun WinBanner(text: String) {
    val c = LocalGameLook.current.colors
    Surface(color = c.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, color = c.success)
    }
}

// ---------------- Spider ----------------

val SpiderSetup: (PuzzleFactory) -> PlaySetup<SpiderGame> = { factory ->
    PlaySetup(
        rules = "Build runs down from King to Ace in one suit; a finished run leaves the table. You may place a card on any card " +
            "one rank higher, but only same-suit runs move together. Tap the stock to deal a card onto every column (no column may be empty). " +
            "Tap a card to pick it up, then tap where it goes, or tap it again to move it to the best spot.",
        settingTitle = "Suits", settings = listOf("1 suit", "2 suits", "4 suits"),
        describe = { listOf("Spades only · the gentlest start", "Spades and hearts", "All four suits · the classic challenge")[it] },
        settingOf = { listOf(1, 2, 4).indexOf(it.suits) }, inProgress = { !it.won },
        subtitle = { (if (it.suits == 1) "1 suit" else "${it.suits} suits") + " · ${it.completed.size} of 8 runs" },
        create = { i -> { factory.custom("spider:$i", { seed -> SpiderGame.deal(seed, listOf(1, 2, 4)[i]) }) { it.columns.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.won) Outcome(Result.WON, g.moves.toLong()) else null },
    )
}

@Composable
fun SpiderScreen(nav: NavController, vm: PlayViewModel<SpiderGame>, factory: PuzzleFactory) {
    var sel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var hint by remember { mutableStateOf<SpiderMove?>(null) }
    PlayShell(nav, vm, GameId.SPIDER, remember(factory) { SpiderSetup(factory) }, tools = { g, s ->
        HintButton(GameId.SPIDER, enabled = !g.won && !s.busy) {
            hint = g.hint()
            vm.say(hint?.let { "Try moving ${SolitaireState.label(g.columns[it.from][g.columns[it.from].size - it.count])} to column ${it.to + 1}." }
                ?: if (g.canDeal) "No useful move. Deal from the stock." else "No moves left. Undo or start a new game.")
        }
    }) { g, _ ->
        LaunchedEffect(g) { sel = null; hint = null }
        fun tap(col: Int, count: Int?) {
            val cur = sel
            if (cur != null && cur.first != col) {
                val m = SpiderMove(cur.first, cur.second, col)
                if (g.canMove(m)) { vm.play { it.move(m) }; return }
            }
            if (count == null) { sel = null; return }
            if (cur == col to count) {
                // Second tap: move to the best target (same suit, then any card, then a gap).
                val first = g.columns[col][g.columns[col].size - count]
                val target = (0..9).filter { g.canMove(SpiderMove(col, count, it)) }.maxByOrNull { t ->
                    val top = g.columns[t].lastOrNull()
                    when { top == null -> 0; top / 13 == first / 13 -> 2; else -> 1 }
                }
                if (target != null) vm.play { it.move(SpiderMove(col, count, target)) } else sel = null
                return
            }
            if (count <= g.runLength(col)) sel = col to count else sel = null
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.completed.size} / 8 runs")
            InfoChip("${g.stock.size / 10} deals left")
            InfoChip("${g.moves} moves")
        }
        if (g.won) WinBanner("All eight runs complete!")
        TablePanel(padding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
            ZoomBox(Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 3.dp
                    val cw = (maxWidth - gap * 9) / 10
                    val ch = cw * 1.4f
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clickable(enabled = g.canDeal) { vm.play { it.deal() } }
                                .semantics { contentDescription = say("Stock, ${g.stock.size / 10} deals left") }) {
                                if (g.stock.isNotEmpty()) PlayingCard(0, width = cw, faceUp = false) else CardSlot(width = cw)
                            }
                            Box(Modifier.width(cw))
                            repeat(8) { i ->
                                val suit = g.completed.getOrNull(i)
                                if (suit != null) PlayingCard(suit * 13 + 12, width = cw) else CardSlot(width = cw)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            g.columns.forEachIndexed { col, pile ->
                                val hidden = g.hidden[col]
                                val down = cw * 0.22f; val up = cw * 0.5f
                                val tops = pile.indices.map { i -> down * minOf(i, hidden) + up * maxOf(0, i - hidden) }
                                val height = (tops.lastOrNull() ?: 0.dp) + ch
                                val hintHere = hint?.let { it.from == col || it.to == col } == true
                                Box(Modifier.width(cw).height(maxOf(height, ch)).clickable { tap(col, null) }
                                    .semantics { contentDescription = say("Column ${col + 1}, ${pile.size - hidden} face up") }) {
                                    if (pile.isEmpty()) CardSlot(width = cw, highlighted = hintHere)
                                    pile.forEachIndexed { i, card ->
                                        val faceUp = i >= hidden
                                        val count = pile.size - i
                                        val chosen = sel?.let { it.first == col && count <= it.second } == true
                                        val hinted = hint?.let { (it.from == col && count <= it.count) || (it.to == col && i == pile.lastIndex) } == true
                                        PlayingCard(card, Modifier.offset(y = tops[i]).clickable { tap(col, if (faceUp) count else null) }
                                            .semantics { contentDescription = say(if (faceUp) "Column ${col + 1}, ${SolitaireState.label(card)}" else "Face-down card") },
                                            width = cw, faceUp = faceUp, selected = chosen, hinted = hinted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Pyramid ----------------

val PyramidSetup: (PuzzleFactory) -> PlaySetup<PyramidGame> = { factory ->
    PlaySetup(
        rules = "Clear the pyramid by removing pairs of uncovered cards that add up to 13: Ace 1, Jack 11, Queen 12. Kings count 13 and " +
            "go on their own. A card is uncovered when no card overlaps it from the row below. Tap the stock to turn a card; " +
            "the top waste card can pair with the pyramid.",
        settingTitle = "Passes through the stock", settings = listOf("Unlimited", "3 passes", "2 passes", "1 pass"),
        describe = { listOf("Recycle the waste as often as you like", "Recycle the waste twice", "Recycle the waste once", "No recycling · hardest")[it] },
        settingOf = { it.setting }, inProgress = { !it.won && !it.stuck },
        subtitle = { "${it.cleared} of 28 cleared" },
        create = { i -> { factory.custom("pyramid:$i", { seed -> PyramidGame.deal(seed, i) }) { it.pyramid.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> when { g.won -> Outcome(Result.WON, g.moves.toLong()); g.stuck -> Outcome(Result.LOST); else -> null } },
    )
}

@Composable
fun PyramidScreen(nav: NavController, vm: PlayViewModel<PyramidGame>, factory: PuzzleFactory) {
    var sel by remember { mutableStateOf<Int?>(null) }
    var hint by remember { mutableStateOf<Pair<Int, Int?>?>(null) }
    PlayShell(nav, vm, GameId.PYRAMID, remember(factory) { PyramidSetup(factory) }, tools = { g, s ->
        HintButton(GameId.PYRAMID, enabled = !g.won && !s.busy) {
            hint = g.pairs().firstOrNull()
            vm.say(if (hint != null) "The highlighted cards make 13." else if (g.draw() != null) "No pairs. Turn a stock card." else "No moves left.")
        }
    }) { g, _ ->
        LaunchedEffect(g) { sel = null; hint = null }
        fun tap(source: Int) {
            val card = g.cardAt(source) ?: return
            if (rankOf(card) == 13) { vm.play { it.remove(source) }; return }
            val cur = sel
            if (cur != null && cur != source) {
                val next = g.remove(cur, source)
                if (next != null) { vm.play { next }; return }
            }
            sel = if (cur == source) null else source
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.cleared} / 28 cleared")
            InfoChip("${g.stock.size} in stock")
            InfoChip(when (g.recycles) { -1 -> "Unlimited passes"; 0 -> "Last pass"; else -> "${g.recycles} recycles left" })
        }
        if (g.won) WinBanner("Pyramid cleared!")
        else if (g.stuck) Text("No more moves. Undo or start a new game.", color = LocalGameLook.current.colors.danger)
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 4.dp
                    val cw = minOf((maxWidth - gap * 6) / 7, 56.dp)
                    val ch = cw * 1.4f
                    val step = ch * 0.5f
                    val total = maxWidth
                    Box(Modifier.fillMaxWidth().height(step * 6 + ch)) {
                        for (r in 0..6) for (c in 0..r) {
                            val i = PyramidGame.index(r, c)
                            val card = g.pyramid[i] ?: continue
                            val x = (total - cw * (r + 1) - gap * r) / 2 + (cw + gap) * c
                            val open = g.exposed(i)
                            PlayingCard(card, Modifier.offset(x, step * r).clickable(enabled = open) { tap(i) }
                                .semantics { contentDescription = say("${SolitaireState.label(card)}${if (open) ", uncovered" else ", covered"}") },
                                width = cw, selected = sel == i, hinted = hint?.let { it.first == i || it.second == i } == true)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clickable(enabled = g.draw() != null) { vm.play { it.draw() } }
                    .semantics { contentDescription = say(if (g.stock.isEmpty()) "Recycle the waste" else "Stock, ${g.stock.size} cards") }) {
                    if (g.stock.isNotEmpty()) PlayingCard(0, width = 56.dp, faceUp = false)
                    else CardSlot(width = 56.dp, label = if (g.recycles != 0 && g.waste.isNotEmpty()) "↻" else null)
                }
                val top = g.waste.lastOrNull()
                Box(Modifier.clickable(enabled = top != null) { tap(PyramidGame.WASTE) }) {
                    if (top != null) PlayingCard(top, width = 56.dp, selected = sel == PyramidGame.WASTE,
                        hinted = hint?.let { it.first == PyramidGame.WASTE || it.second == PyramidGame.WASTE } == true)
                    else CardSlot(width = 56.dp)
                }
                Text("Tap two cards that make 13.\nKings go on their own.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------- Memory ----------------

val MemorySetup: (PuzzleFactory) -> PlaySetup<MemoryGame> = { factory ->
    PlaySetup(
        rules = "All tiles start face down. Turn over two at a time; matching pictures stay up. Find every pair in as few turns as you can.",
        settingTitle = "Board", settings = MemoryGame.SIZES.map { (c, r) -> "$c × $r" },
        describe = { MemoryGame.SIZES[it].let { (c, r) -> "${c * r / 2} pairs of Mahjong pictures" } },
        settingOf = { it.setting }, inProgress = { !it.won && (it.matched.isNotEmpty() || it.moves > 0) },
        subtitle = { "${it.matched.size / 2} of ${it.faces.size / 2} pairs · ${it.moves} turns" },
        create = { i -> { factory.custom("memory:$i", { seed -> MemoryGame.deal(seed, i) }) { it.faces.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.won) Outcome(Result.WON, g.moves.toLong()) else null },
    )
}

@Composable
fun MemoryScreen(nav: NavController, vm: PlayViewModel<MemoryGame>, factory: PuzzleFactory) {
    val look = LocalGameLook.current
    PlayShell(nav, vm, GameId.MEMORY, remember(factory) { MemorySetup(factory) }, undoable = false) { g, _ ->
        LaunchedEffect(g.open) { if (g.mismatch) { delay(900); vm.play(record = false) { it.hide() } } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("${g.matched.size / 2} / ${g.faces.size / 2} pairs")
            InfoChip("${g.moves} turns")
        }
        if (g.won) WinBanner("All pairs found in ${g.moves} turns!")
        TablePanel {
            ZoomBox(Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 6.dp
                    // Tiles are tall, so the board is laid the wide way round (a 3 × 4 deal shows 4 across); where a tile
                    // sits doesn't matter to the game, and this way the whole board fits on a phone without scrolling.
                    val across = maxOf(g.cols, g.faces.size / g.cols)
                    val w = minOf((maxWidth - gap * (across - 1)) / across - 4.dp, 72.dp)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
                        g.faces.indices.chunked(across).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                row.forEach { i ->
                                    val up = i in g.matched || i in g.open
                                    Box(Modifier.clickable(enabled = !up && !g.won) { vm.play(record = false) { it.flip(i) } }
                                        .semantics { contentDescription = say(if (up) mahjongName(g.faces[i]) + if (i in g.matched) ", matched" else "" else "Face-down tile") }) {
                                        if (up) MahjongTile(g.faces[i], width = w, depth = 4.dp, free = true, selected = i in g.open && !g.mismatch,
                                            hinted = false)
                                        else Canvas(Modifier.size(w + 6.dp, w * TILE_ASPECT + 6.dp).padding(end = 6.dp, bottom = 6.dp)) {
                                            drawRect(Color.Black.copy(alpha = 0.28f), Offset(5f, 6f), Size(size.width, size.height))
                                            drawCardBack(look.colors.cardBack, look.colors.cardBackPattern)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
