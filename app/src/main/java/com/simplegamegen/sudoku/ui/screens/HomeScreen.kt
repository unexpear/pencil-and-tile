package com.simplegamegen.sudoku.ui.screens

import androidx.compose.ui.res.stringResource
import com.simplegamegen.sudoku.R
import com.simplegamegen.sudoku.ui.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simplegamegen.sudoku.tabletop.CollectionGuide
import com.simplegamegen.sudoku.ui.GameGroup
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.GameViewModel
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.SectionTitle
import com.simplegamegen.sudoku.ui.components.SystemBarsFor
import com.simplegamegen.sudoku.ui.theme.HomeLayout
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.theme.readableOn
import com.simplegamegen.sudoku.ui.i18n.say

@Composable
fun HomeScreen(nav: NavController, vm: GameViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val look = LocalGameLook.current
    val c = look.colors
    SystemBarsFor(c.topBar, c.background)
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(c.topBar).statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // The brand name stays the same in every language.
                    androidx.compose.material3.Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = c.onTopBar)
                    Text("${CollectionGuide.entries.size} games · play offline, keep your progress", style = MaterialTheme.typography.bodyMedium, color = c.onTopBar.copy(alpha = 0.8f))
                }
                IconButton(onClick = { nav.navigate("tutorials") }) { Icon(GameIcons.School, contentDescription = tr("How to play"), tint = c.onTopBar) }
                IconButton(onClick = { nav.navigate("guide") }) { Icon(GameIcons.Rules, contentDescription = tr("Games and possibilities"), tint = c.onTopBar) }
                IconButton(onClick = { nav.navigate("profile") }) { Icon(GameIcons.Person, contentDescription = tr("Profile"), tint = c.onTopBar) }
                IconButton(onClick = { nav.navigate("settings") }) { Icon(GameIcons.Settings, contentDescription = tr("Settings"), tint = c.onTopBar) }
            }
        }
        Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.hasSave || (state.hasGame && !state.won)) {
                Surface(onClick = { vm.loadGame(); nav.navigate("game") }, color = c.accent, contentColor = c.onAccent,
                    shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(48.dp).background(Color.White.copy(alpha = 0.14f), MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                            GameArt(GameId.SUDOKU, Modifier.size(40.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Continue Sudoku", style = MaterialTheme.typography.titleMedium)
                            Text(if (state.hasGame) "${variantLabel(state.variant)} ${state.size}×${state.size} · ${formatTime(state.elapsedSeconds.toLong())}" else "Your saved puzzle",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(GameIcons.Chevron, contentDescription = null)
                    }
                }
            }
            GameGroup.entries.forEach { group ->
                SectionTitle(group.title, Modifier.padding(top = 8.dp))
                // Base games only; their variants sit under them.
                val games = GameId.entries.filter { it.group == group && it.parent == null }
                if (look.spec.homeLayout == HomeLayout.LIST) {
                    Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outline)) {
                        Column {
                            games.forEachIndexed { i, g ->
                                GameRow(g, showDivider = i > 0) { nav.navigate(g.route) }
                                g.variants.forEach { v -> GameRow(v, showDivider = true, variant = true) { nav.navigate(v.route) } }
                            }
                        }
                    }
                } else {
                    games.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { g -> GameTile(g, Modifier.weight(1f), onVariant = { nav.navigate(it.route) }) { nav.navigate(g.route) } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    // Keeps scrolled tiles from showing through the transparent status bar.
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(c.topBar))
    }
}

@Composable
private fun GameTile(game: GameId, modifier: Modifier, onVariant: (GameId) -> Unit, onClick: () -> Unit) {
    val look = LocalGameLook.current
    val c = look.colors
    val bg = if (look.spec.coloredTiles) look.gameColor(game) else c.surface
    val fg = if (look.spec.coloredTiles) readableOn(bg) else c.text
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 150.dp).semantics { contentDescription = say(game.title) + ", " + say(game.blurb) },
        color = bg, contentColor = fg, shape = MaterialTheme.shapes.large,
        border = if (look.spec.coloredTiles) null else BorderStroke(1.dp, c.outline)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(84.dp)
                .background(if (look.spec.coloredTiles) Color.White.copy(alpha = 0.16f) else c.surfaceAlt, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center) { GameArt(game, Modifier.size(76.dp)) }
            Column {
                Text(game.title, style = MaterialTheme.typography.titleMedium)
                Text(game.blurb, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f))
            }
            // Variants of this game, each opening its own game.
            game.variants.forEach { v ->
                Surface(onClick = { onVariant(v) }, color = fg.copy(alpha = 0.12f), contentColor = fg, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = say(v.title) + ", " + say(v.blurb) }) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(v.title, style = MaterialTheme.typography.labelLarge)
                            Text(v.blurb, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.75f))
                        }
                        Icon(GameIcons.Chevron, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GameRow(game: GameId, showDivider: Boolean, variant: Boolean = false, onClick: () -> Unit) {
    val look = LocalGameLook.current
    val c = look.colors
    Column {
        if (showDivider) Box(Modifier.fillMaxWidth().padding(start = 84.dp).height(1.dp).background(c.outline))
        Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().semantics { contentDescription = say(game.title) + ", " + say(game.blurb) }) {
            // Variants are indented under their base game.
            Row(Modifier.padding(start = if (variant) 40.dp else 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(58.dp).background(c.surfaceAlt, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                    GameArt(game, Modifier.size(54.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(game.title, style = MaterialTheme.typography.titleMedium)
                    Text(game.blurb, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                Icon(GameIcons.Chevron, contentDescription = null, tint = c.muted)
            }
        }
    }
}
