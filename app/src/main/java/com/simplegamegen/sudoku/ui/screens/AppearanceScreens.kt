package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.ui.i18n.LocalCatalog
import com.simplegamegen.sudoku.ui.i18n.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.simplegamegen.sudoku.ui.AppearanceViewModel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.assets.CheckerPiece
import com.simplegamegen.sudoku.ui.assets.DominoTile
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.MahjongTile
import com.simplegamegen.sudoku.ui.assets.PlayingCard
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.HeaderAction
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.SectionTitle
import com.simplegamegen.sudoku.ui.theme.BuiltInThemes
import com.simplegamegen.sudoku.ui.theme.CustomTheme
import com.simplegamegen.sudoku.ui.theme.DarkMode
import com.simplegamegen.sudoku.ui.theme.GameTheme
import com.simplegamegen.sudoku.ui.theme.HomeLayout
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.theme.ThemeCodec
import com.simplegamegen.sudoku.ui.theme.ThemeSpec
import com.simplegamegen.sudoku.ui.theme.ThemeToken
import com.simplegamegen.sudoku.ui.theme.readableOn
import com.simplegamegen.sudoku.ui.i18n.say

@Composable
fun AppearanceScreen(nav: NavController, vm: AppearanceViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val look = LocalGameLook.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    var importing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val catalog = LocalCatalog.current
    LaunchedEffect(message, notice) {
        (message ?: notice)?.let { snackbar.showSnackbar(catalog.tr(it)); vm.consumeMessage(); notice = null }
    }
    GameScaffold(title = "Appearance", onBack = { nav.popBackStack() }, snackbar = snackbar,
        actions = { HeaderAction(GameIcons.Paste, "Import a theme code") { importing = true } }) {
        OptionGroup("Dark mode", DarkMode.entries, settings.darkMode, {
            when (it) { DarkMode.SYSTEM -> "Follow system"; DarkMode.LIGHT -> "Light"; DarkMode.DARK -> "Dark" }
        }, onSelect = vm::setDarkMode)
        SectionTitle("Themes")
        settings.themes.forEach { spec ->
            val selected = spec.id == settings.selected.id
            ThemeCard(spec, selected, look.dark, onSelect = { vm.select(spec.id) },
                onEdit = settings.custom(spec.id)?.let { { nav.navigate("theme_edit/${spec.id}") } },
                onShare = settings.custom(spec.id)?.let { custom -> {
                    clipboard.setText(AnnotatedString(ThemeCodec.encode(custom)))
                    notice = "Theme code copied. Share it so others can import it."
                } },
                onDelete = settings.custom(spec.id)?.let { { deleting = spec.id } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { nav.navigate("theme_edit/new") }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(GameIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp)); Text("  Create theme")
            }
            OutlinedButton(onClick = { importing = true }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Import code") }
        }
        Text("Custom themes start from a built-in theme. Pick your own table, card back, tile, board and accent colors, then share the code.",
            style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
    }
    if (importing) {
        var code by rememberSaveable { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text("Import a theme") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste a theme code that starts with SGT1.")
                    OutlinedTextField(value = code, onValueChange = { code = it; error = false }, label = { Text("Theme code") },
                        isError = error, supportingText = { if (error) Text("That code isn't a valid theme. Check it and try again.") }, maxLines = 4)
                    TextButton(onClick = { clipboard.getText()?.text?.let { code = it; error = false } }) { Text("Paste from clipboard") }
                }
            },
            confirmButton = { TextButton(onClick = { if (vm.import(code)) { importing = false; notice = "Theme imported" } else error = true }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { importing = false }) { Text("Cancel") } },
            containerColor = look.colors.surface,
        )
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this theme?") },
            text = { Text("${settings.themes.firstOrNull { it.id == id }?.name ?: "This theme"} will be removed from this device.") },
            confirmButton = { TextButton(onClick = { vm.delete(id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
            containerColor = look.colors.surface,
        )
    }
}

@Composable
private fun ThemeCard(spec: ThemeSpec, selected: Boolean, dark: Boolean, onSelect: () -> Unit,
    onEdit: (() -> Unit)?, onShare: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val look = LocalGameLook.current
    Surface(onClick = onSelect, shape = MaterialTheme.shapes.large, color = look.colors.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) look.colors.accent else look.colors.outline),
        modifier = Modifier.fillMaxWidth().semantics { if (selected) stateDescription = say("Selected") }) {
        Column {
            GameTheme(spec, dark) { ThemePreview(Modifier.fillMaxWidth()) }
            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.name, style = MaterialTheme.typography.titleMedium)
                    Text(spec.description, style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
                }
                onShare?.let { IconButton(onClick = it) { Icon(GameIcons.Copy, contentDescription = tr("Copy theme code")) } }
                onEdit?.let { IconButton(onClick = it) { Icon(GameIcons.Edit, contentDescription = tr("Edit theme")) } }
                onDelete?.let { IconButton(onClick = it) { Icon(GameIcons.Delete, contentDescription = tr("Delete theme")) } }
                if (selected) Icon(GameIcons.Check, contentDescription = tr("Selected"), tint = look.colors.accent, modifier = Modifier.padding(end = 10.dp))
            }
        }
    }
}

/** A small scene rendered with the current theme: header, felt, card, tile, domino and checker. */
@Composable
fun ThemePreview(modifier: Modifier = Modifier) {
    val look = LocalGameLook.current
    val c = look.colors
    Column(modifier.background(c.background)) {
        Row(Modifier.fillMaxWidth().background(look.headerColor(GameId.SOLITAIRE)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Solitaire", Modifier.weight(1f), color = look.onHeader(GameId.SOLITAIRE), fontWeight = FontWeight.SemiBold)
            Box(Modifier.size(14.dp).background(c.accent, CircleShape))
        }
        Row(Modifier.fillMaxWidth().padding(10.dp).background(c.table, MaterialTheme.shapes.medium).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayingCard(12, width = 34.dp)
            PlayingCard(0, width = 34.dp, faceUp = false)
            MahjongTile(32, width = 30.dp, depth = 4.dp)
            DominoTile(5, 2, unit = 18.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CheckerPiece(1, true, size = 24.dp); CheckerPiece(-1, false, size = 24.dp)
            }
        }
        Row(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameId.entries.take(5).forEach { id ->
                Box(Modifier.size(28.dp).background(if (look.spec.coloredTiles) look.gameColor(id) else c.surfaceAlt, MaterialTheme.shapes.small))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeEditorScreen(nav: NavController, vm: AppearanceViewModel, themeId: String?) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val look = LocalGameLook.current
    val existing = themeId?.let(settings::custom)
    var draft by remember(existing?.id) {
        mutableStateOf(existing ?: CustomTheme.from(settings.selected, vm.newId(), "My theme"))
    }
    var name by remember(existing?.id) { mutableStateOf(draft.name) }
    var picking by remember { mutableStateOf<ThemeToken?>(null) }
    val nameOk = name.isNotBlank() && name.length <= CustomTheme.MAX_NAME && name.none { it == '|' || it == '\n' || it == '\t' }
    fun saveDraft() { vm.save(draft.copy(name = name.trim())); nav.popBackStack() }
    val resolved = remember(draft) { runCatching { draft.resolve() }.getOrNull() }
    GameScaffold(title = if (existing == null) "Create theme" else "Edit theme", onBack = { nav.popBackStack() },
        actions = { HeaderAction(GameIcons.Save, "Save theme", enabled = nameOk) { saveDraft() } }) {
        resolved?.let { spec ->
            Surface(shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, look.colors.outline)) {
                GameTheme(spec, look.dark) { ThemePreview(Modifier.fillMaxWidth()) }
            }
        }
        OutlinedTextField(value = name, onValueChange = { name = it.take(CustomTheme.MAX_NAME) },
            label = { Text("Theme name") }, singleLine = true, isError = !nameOk, modifier = Modifier.fillMaxWidth(),
            supportingText = { if (!nameOk) Text("Use 1–${CustomTheme.MAX_NAME} characters without | or line breaks.") })
        OptionGroup("Start from", BuiltInThemes.all, BuiltInThemes.byId(draft.baseId) ?: BuiltInThemes.default, { it.name }) { base ->
            draft = draft.copy(baseId = base.id, cornerRadius = base.cornerRadius, homeLayout = base.homeLayout,
                coloredTiles = base.coloredTiles, gameHeaders = base.gameHeaders)
        }
        SectionTitle("Colors")
        val base = resolved ?: BuiltInThemes.default
        Surface(color = look.colors.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, look.colors.outline)) {
            Column {
                ThemeToken.entries.filter { it.editable }.forEach { token ->
                    val value = Color(base.colors(look.dark)[token])
                    Row(Modifier.fillMaxWidth().clickable { picking = token }.padding(horizontal = 14.dp, vertical = 10.dp)
                        .semantics { contentDescription = say("${token.label}, change color") },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(32.dp).background(value, CircleShape).border(1.dp, look.colors.outline, CircleShape))
                        Text(token.label, Modifier.weight(1f))
                        if (token in draft.overrides) TextButton(onClick = { draft = draft.copy(overrides = draft.overrides - token) }) { Text("Reset") }
                        else Text("Theme default", style = MaterialTheme.typography.bodySmall, color = look.colors.muted)
                    }
                }
            }
        }
        SectionTitle("Shape and layout")
        Text("Corner roundness: ${draft.cornerRadius} dp", style = MaterialTheme.typography.labelLarge, color = look.colors.muted)
        Slider(value = draft.cornerRadius.toFloat(), onValueChange = { draft = draft.copy(cornerRadius = it.toInt()) },
            valueRange = 0f..ThemeSpec.MAX_RADIUS.toFloat(), steps = ThemeSpec.MAX_RADIUS - 1)
        OptionGroup("Home screen", HomeLayout.entries, draft.homeLayout, { if (it == HomeLayout.GRID) "Tiles" else "List" }) {
            draft = draft.copy(homeLayout = it)
        }
        SwitchRow("Colorful game tiles", draft.coloredTiles) { draft = draft.copy(coloredTiles = it) }
        SwitchRow("Game-colored headers", draft.gameHeaders) { draft = draft.copy(gameHeaders = it) }
        Button(onClick = ::saveDraft, enabled = nameOk, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (existing == null) "Save and use theme" else "Save changes")
        }
    }
    picking?.let { token ->
        val start = Color((resolved ?: BuiltInThemes.default).colors(look.dark)[token])
        ColorPickerDialog(token.label, start, onDismiss = { picking = null }) { color ->
            draft = draft.copy(overrides = draft.overrides + (token to (color.toArgb().toLong() and 0xFFFFFFFFL)))
            picking = null
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val Swatches = listOf(
    0xFF173B55, 0xFF0F6E56, 0xFF2E6B4F, 0xFF1F4A37, 0xFF3F5E2A, 0xFF639922, 0xFF1D9E75, 0xFF2F6F73,
    0xFF185FA5, 0xFF378ADD, 0xFF534AB7, 0xFF7F77DD, 0xFF5B3A6E, 0xFF993556, 0xFFD4537E, 0xFF8C2F2F,
    0xFFC62828, 0xFFE24B4A, 0xFFD85A30, 0xFFEF9F27, 0xFFE9AA45, 0xFFFAC775, 0xFF8B5A34, 0xFF6B4E2E,
    0xFFE9D8B4, 0xFFFBF6E9, 0xFFFFFFFF, 0xFFD3D1C7, 0xFF888780, 0xFF444441, 0xFF2B2B2B, 0xFF101211,
).map(::Color)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerDialog(title: String, start: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val look = LocalGameLook.current
    var chosen by remember { mutableStateOf(start) }
    var hex by remember { mutableStateOf(start.toHex()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(44.dp).background(chosen, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                    Text(chosen.toHex(), color = readableOn(chosen), fontWeight = FontWeight.SemiBold)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Swatches.forEach { swatch ->
                        Box(Modifier.size(36.dp).background(swatch, CircleShape)
                            .border(if (swatch == chosen) 3.dp else 1.dp, if (swatch == chosen) look.colors.accent else look.colors.outline, CircleShape)
                            .clickable { chosen = swatch; hex = swatch.toHex() }
                            .semantics { contentDescription = say("Color ${swatch.toHex()}") })
                    }
                }
                OutlinedTextField(value = hex, singleLine = true, label = { Text("Hex color") },
                    onValueChange = { v ->
                        hex = v.take(7)
                        parseHex(hex)?.let { chosen = it }
                    }, isError = parseHex(hex) == null)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(chosen) }) { Text("Use color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = look.colors.surface,
    )
}

private fun Color.toHex(): String = "#" + (toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

private fun parseHex(text: String): Color? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6 || digits.any { it !in "0123456789abcdefABCDEF" }) return null
    return Color(0xFF000000 or digits.toLong(16))
}
