package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.tabletop.CollectionGuide
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

@Composable
fun CollectionGuideScreen(nav: NavController) {
    val look = LocalGameLook.current
    val c = look.colors
    val entries = CollectionGuide.entries
    GameScaffold(title = "Games and possibilities", onBack = { nav.popBackStack() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(entries.size to "games", entries.sumOf { it.variants } to "rule variants", entries.sumOf { it.setups } to "setting combos").forEach { (n, label) ->
                Surface(Modifier.weight(1f), color = c.surfaceAlt, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) {
                        Text(n.toString(), style = MaterialTheme.typography.headlineSmall, color = c.accent)
                        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
                    }
                }
            }
        }
        Text("A rule variant changes how you play. A setting changes the difficulty, size or theme. Neither counts unique boards. " +
            "You can keep starting games without reaching a final level; finite content can eventually repeat.",
            style = MaterialTheme.typography.bodyMedium, color = c.muted)
        entries.forEach { game ->
            Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).background(c.surfaceAlt, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                        GameArt(GameId.entries.firstOrNull { game.title.startsWith(it.title) } ?: GameId.SUDOKU, Modifier.size(50.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(game.title, style = MaterialTheme.typography.titleMedium)
                        Text((if (game.variants == 1) "1 rule variant" else "${game.variants} rule variants") + " · ${game.setups} setting combinations",
                            style = MaterialTheme.typography.labelMedium, color = c.accent)
                        Text(game.description, style = MaterialTheme.typography.bodyMedium)
                        Text(game.unique, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
        }
    }
}
