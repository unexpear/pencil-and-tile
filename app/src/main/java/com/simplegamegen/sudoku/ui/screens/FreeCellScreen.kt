package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.cards.FreeCellGame
import com.simplegamegen.sudoku.cards.FreeCellMove
import com.simplegamegen.sudoku.cards.FreeCellPile
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.tabletop.SolitaireState
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.CardSlot
import com.simplegamegen.sudoku.ui.assets.PlayingCard
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.components.ZoomBox
import com.simplegamegen.sudoku.ui.i18n.say

private data class FreeCellSel(val from: FreeCellPile, val index: Int, val count: Int)

val FreeCellSetup: (PuzzleFactory) -> PlaySetup<FreeCellGame> = { factory ->
    PlaySetup(
        rules = "Deal all 52 cards face up into eight columns. Four free cells hold one card each; four foundations build " +
            "Ace to King by suit. Move a card onto a free cell, onto its foundation, or onto a tableau card of the opposite " +
            "color and one rank higher. A built descending alternating pile may move together when you have enough free cells " +
            "and empty columns. Empty columns take any card or pile. Not every deal can be won. " +
            "Tap a card to pick it up, then tap where it goes, or tap it again to send it to the best spot.",
        settingTitle = "Rules",
        settings = listOf("Standard"),
        describe = { "Classic FreeCell · 4 free cells, 8 columns" },
        settingOf = { 0 },
        inProgress = { !it.won },
        subtitle = { "${it.foundations.count { f -> f != null && com.simplegamegen.sudoku.cards.rankOf(f) == 13 }} of 4 suits · ${it.moves} moves" },
        create = { _ -> { factory.custom("freecell", { seed -> FreeCellGame.deal(seed) }) { it.columns.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> if (g.won) Outcome(Result.WON, g.moves.toLong()) else null },
    )
}

@Composable
fun FreeCellScreen(nav: NavController, vm: PlayViewModel<FreeCellGame>, factory: PuzzleFactory) {
    var sel by remember { mutableStateOf<FreeCellSel?>(null) }
    var hint by remember { mutableStateOf<FreeCellMove?>(null) }
    PlayShell(nav, vm, GameId.FREECELL, remember(factory) { FreeCellSetup(factory) }, tools = { g, s ->
        HintButton(GameId.FREECELL, enabled = !g.won && !s.busy) {
            hint = g.hint()
            vm.say(hint?.let { describeHint(g, it) } ?: "No moves left. Undo or start a new game.")
        }
    }) { g, _ ->
        LaunchedEffect(g) { sel = null; hint = null }
        fun tryMove(m: FreeCellMove): Boolean {
            if (!g.canMove(m)) return false
            vm.play { it.move(m) }
            return true
        }
        fun autoPlace(from: FreeCellPile, index: Int, count: Int) {
            val options = g.legalMoves().filter { it.from == from && it.fromIndex == index && it.count == count }
            val best = options.maxByOrNull { m ->
                when (m.to) {
                    FreeCellPile.FOUNDATION -> 100
                    FreeCellPile.TABLEAU -> if (g.columns[m.toIndex].isEmpty()) 10 else 50
                    FreeCellPile.FREE -> 5
                }
            }
            if (best != null) tryMove(best) else sel = null
        }
        fun tap(pile: FreeCellPile, index: Int, count: Int?) {
            val cur = sel
            if (cur != null && (cur.from != pile || cur.index != index)) {
                if (tryMove(FreeCellMove(cur.from, cur.index, pile, index, cur.count))) return
            }
            if (count == null) {
                // Empty destination (free / foundation / column gap) already tried above.
                if (cur != null && cur.from == pile && cur.index == index) sel = null
                return
            }
            if (cur == FreeCellSel(pile, index, count)) {
                autoPlace(pile, index, count)
                return
            }
            when (pile) {
                FreeCellPile.FREE -> if (g.free[index] != null) sel = FreeCellSel(pile, index, 1) else sel = null
                FreeCellPile.TABLEAU -> if (count <= g.runLength(index)) sel = FreeCellSel(pile, index, count) else sel = null
                FreeCellPile.FOUNDATION -> sel = null
            }
        }
        val home = g.foundations.count { it != null && com.simplegamegen.sudoku.cards.rankOf(it) == 13 }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip("$home / 4 suits home")
            InfoChip("${g.emptyFrees()} free")
            InfoChip("${g.moves} moves")
        }
        if (g.won) WinBanner("All four foundations complete!")
        TablePanel(padding = PaddingValues(6.dp)) {
            ZoomBox(Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 4.dp
                    val cw = (maxWidth - gap * 7) / 8
                    val ch = cw * 1.4f
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                repeat(4) { i ->
                                    val card = g.free[i]
                                    val chosen = sel?.let { it.from == FreeCellPile.FREE && it.index == i } == true
                                    val hinted = hint?.let {
                                        (it.from == FreeCellPile.FREE && it.fromIndex == i) ||
                                            (it.to == FreeCellPile.FREE && it.toIndex == i)
                                    } == true
                                    Box(
                                        Modifier.clickable { tap(FreeCellPile.FREE, i, if (card != null) 1 else null) }
                                            .semantics {
                                                contentDescription = say(
                                                    if (card != null) "Free cell ${i + 1}, ${SolitaireState.label(card)}"
                                                    else "Empty free cell ${i + 1}",
                                                )
                                            },
                                    ) {
                                        if (card != null) PlayingCard(card, width = cw, selected = chosen, hinted = hinted)
                                        else CardSlot(width = cw, highlighted = hinted)
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                repeat(4) { i ->
                                    val card = g.foundations[i]
                                    val hinted = hint?.let { it.to == FreeCellPile.FOUNDATION && it.toIndex == i } == true
                                    Box(
                                        Modifier.clickable { tap(FreeCellPile.FOUNDATION, i, null) }
                                            .semantics {
                                                contentDescription = say(
                                                    if (card != null) "Foundation ${i + 1}, ${SolitaireState.label(card)}"
                                                    else "Empty foundation ${i + 1}",
                                                )
                                            },
                                    ) {
                                        if (card != null) PlayingCard(card, width = cw, hinted = hinted)
                                        else CardSlot(width = cw, highlighted = hinted)
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            g.columns.forEachIndexed { col, pile ->
                                val up = cw * 0.48f
                                val tops = pile.indices.map { i -> up * i }
                                val height = (tops.lastOrNull() ?: 0.dp) + ch
                                val hintHere = hint?.let {
                                    (it.from == FreeCellPile.TABLEAU && it.fromIndex == col) ||
                                        (it.to == FreeCellPile.TABLEAU && it.toIndex == col)
                                } == true
                                Box(
                                    Modifier.width(cw).height(maxOf(height, ch))
                                        .clickable { tap(FreeCellPile.TABLEAU, col, null) }
                                        .semantics { contentDescription = say("Column ${col + 1}, ${pile.size} cards") },
                                ) {
                                    if (pile.isEmpty()) CardSlot(width = cw, highlighted = hintHere)
                                    pile.forEachIndexed { i, card ->
                                        val count = pile.size - i
                                        val chosen = sel?.let {
                                            it.from == FreeCellPile.TABLEAU && it.index == col && count <= it.count
                                        } == true
                                        val hinted = hint?.let {
                                            (it.from == FreeCellPile.TABLEAU && it.fromIndex == col && count <= it.count) ||
                                                (it.to == FreeCellPile.TABLEAU && it.toIndex == col && i == pile.lastIndex)
                                        } == true
                                        PlayingCard(
                                            card,
                                            Modifier.offset(y = tops[i])
                                                .clickable { tap(FreeCellPile.TABLEAU, col, count) }
                                                .semantics {
                                                    contentDescription = say("Column ${col + 1}, ${SolitaireState.label(card)}")
                                                },
                                            width = cw,
                                            selected = chosen,
                                            hinted = hinted,
                                        )
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

private fun describeHint(g: FreeCellGame, m: FreeCellMove): String {
    val card = when (m.from) {
        FreeCellPile.FREE -> g.free[m.fromIndex]!!
        FreeCellPile.TABLEAU -> g.columns[m.fromIndex][g.columns[m.fromIndex].size - m.count]
        FreeCellPile.FOUNDATION -> g.foundations[m.fromIndex]!!
    }
    val label = SolitaireState.label(card)
    return when (m.to) {
        FreeCellPile.FOUNDATION -> "Try moving $label to a foundation."
        FreeCellPile.FREE -> "Try parking $label in a free cell."
        FreeCellPile.TABLEAU -> "Try moving $label to column ${m.toIndex + 1}."
    }
}
