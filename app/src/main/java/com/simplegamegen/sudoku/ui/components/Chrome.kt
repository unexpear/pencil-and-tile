package com.simplegamegen.sudoku.ui.components

import com.simplegamegen.sudoku.ui.i18n.tr
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayModeChoice
import com.simplegamegen.sudoku.ui.SessionClock
import com.simplegamegen.sudoku.ui.playerRecords
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.theme.readableOn
import com.simplegamegen.sudoku.ui.i18n.say

/** Lets each screen tell the activity whether its status and navigation bars sit on dark colors. */
val LocalSystemBars = staticCompositionLocalOf<(statusOnDark: Boolean, navOnDark: Boolean) -> Unit> { { _, _ -> } }

@Composable
fun SystemBarsFor(status: Color, navigation: Color) {
    val apply = LocalSystemBars.current
    LaunchedEffect(status, navigation) { apply(status.luminance() < 0.5f, navigation.luminance() < 0.5f) }
}

/** Opens a game's optional tutorial; provided by the navigation host. */
val LocalOpenTutorial = staticCompositionLocalOf<((GameId) -> Unit)?> { null }

data class MenuAction(val label: String, val icon: ImageVector, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * Shared screen frame: themed header with back arrow and actions, optional
 * bottom tool bar, and a content column that scrolls by default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    game: GameId? = null,
    subtitle: String? = null,
    background: Color = MaterialTheme.colorScheme.background,
    actions: @Composable RowScope.() -> Unit = {},
    menu: List<MenuAction> = emptyList(),
    busy: Boolean = false,
    snackbar: SnackbarHostState? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    scroll: Boolean = true,
    /** Shows a Tutorial action in the header for this game. */
    tutorial: GameId? = null,
    /** Shows the running clock of a timed game in the header. */
    showClock: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    itemSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = LocalGameLook.current
    val openTutorial = LocalOpenTutorial.current
    val header = look.headerColor(game)
    val onHeader = look.onHeader(game)
    SystemBarsFor(header, if (bottomBar != null) look.colors.surface else background)
    Scaffold(
        modifier = modifier,
        containerColor = background,
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (game != null) Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.16f), MaterialTheme.shapes.small),
                                contentAlignment = Alignment.Center) { GameArt(game, Modifier.size(34.dp)) }
                            Column {
                                val longTitle = title.length > 13
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = if (longTitle) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
                                subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = onHeader.copy(alpha = 0.8f)) }
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) { Icon(GameIcons.Back, contentDescription = tr("Back")) }
                    },
                    actions = {
                        if (tutorial != null && showClock && title.length <= 13) SessionClock(tutorial)
                        if (tutorial != null && openTutorial != null) HeaderAction(GameIcons.School, "How to play: tutorial and rules") { openTutorial(tutorial) }
                        actions()
                        if (menu.isNotEmpty()) OverflowMenu(menu)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = header, titleContentColor = onHeader,
                        navigationIconContentColor = onHeader, actionIconContentColor = onHeader,
                    ),
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = look.colors.highlight, trackColor = header)
            }
        },
        bottomBar = { bottomBar?.invoke() },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            content = content,
        )
    }
}

@Composable
private fun OverflowMenu(items: List<MenuAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(GameIcons.More, contentDescription = tr("More options")) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) }, enabled = item.enabled,
                    leadingIcon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { open = false; item.onClick() },
                )
            }
        }
    }
}

/** Header icon action with an accessible label. */
@Composable
fun HeaderAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = tr(label), tint = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.4f))
    }
}

/** Bottom tool bar: equal-width icon buttons with labels, never wrapping. */
@Composable
fun ToolBar(content: @Composable RowScope.() -> Unit) {
    val look = LocalGameLook.current
    Surface(color = look.colors.surface) {
        Column {
            HorizontalDivider(color = look.colors.outline)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, content = content)
        }
    }
}

@Composable
fun RowScope.ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val look = LocalGameLook.current
    val tint = when {
        !enabled -> look.colors.muted.copy(alpha = 0.45f)
        active -> look.colors.accent
        else -> look.colors.text
    }
    Surface(
        onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 56.dp)
            .semantics { if (active) stateDescription = say("On") },
        color = if (active) look.colors.accentSoft else Color.Transparent, shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
        }
    }
}

/** Small rounded status label. [onClick] makes it a button, for reopening a word's definition. */
@Composable
fun InfoChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, emphasized: Boolean = false, onTable: Boolean = false,
    onClick: (() -> Unit)? = null) {
    val look = LocalGameLook.current
    val (bg, fg) = when {
        emphasized -> look.colors.highlight to readableOn(look.colors.highlight)
        onTable -> look.colors.tableInset to look.colors.onTable
        else -> look.colors.surfaceAlt to look.colors.text
    }
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
    if (onClick == null) Surface(modifier, color = bg, contentColor = fg, shape = MaterialTheme.shapes.extraLarge, content = body)
    else Surface(onClick = onClick, modifier = modifier, color = bg, contentColor = fg, shape = MaterialTheme.shapes.extraLarge, content = body)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = LocalGameLook.current.colors.text)
}

/** Labeled chip picker for one setting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> OptionGroup(title: String, options: List<T>, selected: T, label: (T) -> String, enabled: (T) -> Boolean = { true }, onSelect: (T) -> Unit) {
    val look = LocalGameLook.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = look.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                val on = option == selected
                FilterChip(
                    selected = on, enabled = enabled(option), onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                    leadingIcon = if (on) {{ Icon(GameIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }} else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = look.colors.accent, selectedLabelColor = look.colors.onAccent,
                        selectedLeadingIconColor = look.colors.onAccent,
                    ),
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
        }
    }
}

/** Bottom sheet for starting a new game; [content] holds the settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameSheet(title: String, startLabel: String = "Start game", onDismiss: () -> Unit, onStart: () -> Unit,
    game: GameId? = null, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val look = LocalGameLook.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = look.colors.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
            if (game != null) PlayModeChoice(game)
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(startLabel) }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismiss: String = "Keep playing",
    destructive: Boolean = false) {
    val danger = LocalGameLook.current.colors.danger
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = if (destructive) danger else Color.Unspecified) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
        containerColor = LocalGameLook.current.colors.surface,
    )
}

/** Inline feedback line with a steady height so the board doesn't jump. */
@Composable
fun MessageLine(text: String?, modifier: Modifier = Modifier, onTable: Boolean = false) {
    val look = LocalGameLook.current
    Box(modifier.fillMaxWidth().heightIn(min = 24.dp), contentAlignment = Alignment.CenterStart) {
        if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium,
            color = if (onTable) look.colors.onTable else look.colors.accent)
    }
}

/** Felt play area for card, tile and board games. */
@Composable
fun TablePanel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(12.dp), content: @Composable ColumnScope.() -> Unit) {
    val look = LocalGameLook.current
    Surface(modifier.fillMaxWidth(), color = look.colors.table, contentColor = look.colors.onTable, shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, look.colors.tableInset)) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Welcome panel: the game's picture first, then one line, then the choices. Full rules stay in the header. */
@Composable
fun StartCard(title: String, text: String, art: @Composable () -> Unit, tutorial: GameId? = null, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalGameLook.current
    val openTutorial = LocalOpenTutorial.current
    val lead = text.substringBefore('\n').let { line ->
        val dot = line.indexOf(". ")
        if (dot in 12..110) line.substring(0, dot + 1) else line.take(110)
    }
    Surface(Modifier.fillMaxWidth(), color = look.colors.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, look.colors.outline)) {
        Column {
            Box(Modifier.fillMaxWidth().height(148.dp).background(if (tutorial != null) look.headerColor(tutorial) else look.colors.surfaceAlt),
                contentAlignment = Alignment.Center) { art() }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(lead, style = MaterialTheme.typography.bodyMedium, color = look.colors.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (tutorial != null && openTutorial != null && playerRecords()?.settings?.offerTutorials != false) TextButton(onClick = { openTutorial(tutorial) }) {
                    Icon(GameIcons.School, null, Modifier.size(18.dp))
                    Text("  Try the guided tutorial")
                }
                if (tutorial != null) PlayModeChoice(tutorial)
                content()
            }
        }
    }
}

/** Two or three equal tabs in a pill. */
@Composable
fun SegmentedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    Row(Modifier.fillMaxWidth().background(c.surfaceAlt, RoundedCornerShape(50)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            Surface(onClick = { onSelect(i) }, modifier = Modifier.weight(1f).heightIn(min = 44.dp), shape = RoundedCornerShape(50),
                color = if (i == selected) c.surface else Color.Transparent) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal, color = if (i == selected) c.text else c.muted,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
                }
            }
        }
    }
}

/** Provides the system-bar callback to the tree. */
@Composable
fun ProvideSystemBars(apply: (Boolean, Boolean) -> Unit, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalSystemBars provides apply, content = content)

