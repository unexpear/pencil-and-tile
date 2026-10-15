package com.simplegamegen.sudoku.ui.screens

import android.net.Uri
import android.content.Intent
import com.simplegamegen.sudoku.ui.i18n.LocalLanguage
import androidx.compose.runtime.CompositionLocalProvider
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Finished
import com.simplegamegen.sudoku.data.Language
import com.simplegamegen.sudoku.data.PlayerRecords
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.data.Session
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.LocalPlayer
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.ConfirmDialog
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.HeaderAction
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.SectionTitle
import com.simplegamegen.sudoku.ui.components.SegmentedTabs
import com.simplegamegen.sudoku.ui.formatSeconds
import com.simplegamegen.sudoku.ui.playerRecords
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import java.text.DateFormat
import java.util.Date
import com.simplegamegen.sudoku.ui.i18n.say

/** How a game's score is read; games not listed have no score. */
private data class ScoreKind(val label: String, val lowerIsBetter: Boolean)

private val ScoreKinds = mapOf(
    GameId.G2048 to ScoreKind("Best score", false),
    GameId.TETRAS to ScoreKind("Best score", false),
    GameId.KNIFE_FLIP to ScoreKind("Best streak", false),
    GameId.LOG_THROW to ScoreKind("Best score", false),
    GameId.SCRAMBLE to ScoreKind("Most words solved", false),
    GameId.DOTS to ScoreKind("Most boxes", false),
    GameId.MEMORY to ScoreKind("Fewest moves", true),
    GameId.SPIDER to ScoreKind("Fewest moves", true),
    GameId.PYRAMID to ScoreKind("Fewest moves", true),
    GameId.FREECELL to ScoreKind("Fewest moves", true),
    GameId.SLIDING_BLOCKS to ScoreKind("Fewest moves", true),
    GameId.KLOTSKI to ScoreKind("Fewest moves", true),
    GameId.GO to ScoreKind("Most area", false),
    GameId.HANGMAN to ScoreKind("Fewest wrong guesses", true),
    GameId.MASTERMIND to ScoreKind("Fewest guesses", true),
    GameId.BATTLESHIP to ScoreKind("Fewest shots", true),
    GameId.WORD_LADDER to ScoreKind("Fewest steps", true),
    GameId.HONEYCOMB to ScoreKind("Most points", false),
    GameId.LETTER_DRAW to ScoreKind("Longest word", false),
    GameId.MANCALA to ScoreKind("Most stones", false),
    GameId.YACHT to ScoreKind("Most points", false),
    GameId.SHUT_BOX to ScoreKind("Lowest score", true),
    GameId.TEN_THOUSAND to ScoreKind("Most points", false),
    GameId.SHIP_CREW to ScoreKind("Most points", false),
)

private fun gameOf(name: String): GameId? = if (name == GRID_RECORD) GameId.SUDOKU else GameId.entries.firstOrNull { it.name == name }

/** Title for a record: grid Sudoku (16×16 and custom grids) has its own. */
private fun titleOf(name: String): String = if (name == GRID_RECORD) "Sudoku grids" else gameOf(name)?.title ?: name

/** Where "Continue" goes: Sudoku games resume on the board, others on their own screen. */
private fun continueRoute(record: String, id: GameId) = when {
    record == GRID_RECORD -> GRID_ROUTE
    id == GameId.SUDOKU || id == GameId.KILLER -> "game"
    else -> id.route
}

@Composable
fun ProfileScreen(nav: NavController) {
    val player = LocalPlayer.current
    val records = playerRecords() ?: PlayerRecords()
    val c = LocalGameLook.current.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editName by rememberSaveable { mutableStateOf(false) }
    GameScaffold(title = "Profile", onBack = { nav.popBackStack() },
        actions = { HeaderAction(GameIcons.Settings, "Settings") { nav.navigate("settings") } }) {
        Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).background(c.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Text(records.settings.name.trim().take(1).uppercase().ifEmpty { "?" }, color = c.onAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(records.settings.name, style = MaterialTheme.typography.titleLarge)
                        Text(if (records.settings.earnHints) "Hints are earned by winning" else "Hints are free", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    TextButton(onClick = { editName = true }) { Text("Edit") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${records.wins.size}", "wins", Modifier.weight(1f))
                    Stat("${records.history.size}", "finished", Modifier.weight(1f))
                    Stat(formatSeconds(records.secondsPlayed), "played", Modifier.weight(1f))
                    Stat("${records.wallet}", "hints", Modifier.weight(1f))
                }
            }
        }
        SegmentedTabs(listOf("Completed", "In progress", "High scores"), tab) { tab = it }
        when (tab) {
            0 -> CompletedList(records)
            1 -> ActiveList(records, onContinue = { record, id -> nav.navigate(continueRoute(record, id)) })
            else -> HighScores(records)
        }
    }
    if (editName) NameDialog(records.settings.name, onDismiss = { editName = false }) { name ->
        player?.update { r, _ -> r.copy(settings = r.settings.copy(name = name)) }
        editName = false
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val c = LocalGameLook.current.colors
    Column(modifier.background(c.surfaceAlt, MaterialTheme.shapes.medium).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1)
    }
}

@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your name") },
        text = { OutlinedTextField(name, { name = it.take(24) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onSave(name.trim().ifEmpty { "Player" }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = LocalGameLook.current.colors.surface)
}

@Composable
private fun GameRow(id: GameId, title: String, detail: String, trailing: @Composable () -> Unit) {
    val c = LocalGameLook.current.colors
    Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(c.surfaceAlt, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) { GameArt(id, Modifier.size(40.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            trailing()
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = LocalGameLook.current.colors.muted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
}

private fun resultLabel(r: Result) = when (r) { Result.WON -> "Won"; Result.LOST -> "Lost"; Result.DRAW -> "Draw"; Result.FINISHED -> "Finished" }

@Composable
private fun CompletedList(records: PlayerRecords) {
    var all by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    OptionGroup("Show", listOf(false, true), all, { if (it) "All finished games" else "Games you won" }) { all = it }
    val list = if (all) records.history else records.wins
    if (list.isEmpty()) Empty(if (all) "No finished games yet." else "No wins yet. Win a game and it will appear here.")
    list.take(200).forEach { f -> FinishedRow(f, dateFormat) }
}

@Composable
private fun FinishedRow(f: Finished, dateFormat: DateFormat) {
    val id = gameOf(f.game) ?: return
    val c = LocalGameLook.current.colors
    val parts = listOfNotNull(f.level.takeIf { it.isNotEmpty() }, dateFormat.format(Date(f.at)),
        if (f.hints > 0) (if (f.hints == 1) "1 hint" else "${f.hints} hints") else "No hints")
    GameRow(id, titleOf(f.game), parts.joinToString(" · ")) {
        Column(horizontalAlignment = Alignment.End) {
            Text(if (f.timed) formatSeconds(f.seconds) else "Untimed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(resultLabel(f.result) + (f.score?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium,
                color = if (f.result == Result.WON) c.success else c.muted)
        }
    }
}

@Composable
private fun ActiveList(records: PlayerRecords, onContinue: (String, GameId) -> Unit) {
    val sessions = records.sessions.values.sortedByDescending { it.lastPlayed }
    Text("Active and paused games. Each one is saved, so you can pick up where you left off.", style = MaterialTheme.typography.bodyMedium,
        color = LocalGameLook.current.colors.muted)
    if (sessions.isEmpty()) Empty("No games in progress. Games you start are saved here until you finish them.")
    val now = System.currentTimeMillis()
    sessions.forEach { s -> SessionRow(s, now, onContinue) }
}

@Composable
private fun SessionRow(s: Session, now: Long, onContinue: (String, GameId) -> Unit) {
    val id = gameOf(s.game) ?: return
    val when_ = DateUtils.getRelativeTimeSpanString(s.lastPlayed, now, DateUtils.MINUTE_IN_MILLIS).toString()
    val detail = listOfNotNull(s.level.takeIf { it.isNotEmpty() }, if (s.timed) "Timed ${formatSeconds(s.seconds)}" else "Untimed", when_).joinToString(" · ")
    GameRow(id, titleOf(s.game), detail) {
        Button(onClick = { onContinue(s.game, id) }, modifier = Modifier.semantics { contentDescription = say("Continue ${titleOf(s.game)}") }) { Text("Continue") }
    }
}

@Composable
private fun HighScores(records: PlayerRecords) {
    val c = LocalGameLook.current.colors
    val games = (GameId.entries.map { it.name } + GRID_RECORD).filter { name -> records.history.any { it.game == name } }
    if (games.isEmpty()) Empty("Finish games to set records. Timed wins record your best times.")
    games.forEach { name ->
        val id = gameOf(name) ?: return@forEach
        val finished = records.history.filter { it.game == name }
        val wins = finished.count { it.result == Result.WON }
        val (streak, bestStreak) = records.streaks(name)
        val times = records.bestTimes(name)
        val kind = ScoreKinds[id].takeIf { name != GRID_RECORD }
        val scores = kind?.let { records.bestScores(name, it.lowerIsBetter) }.orEmpty()
        Surface(color = c.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GameArt(id, Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(titleOf(name), style = MaterialTheme.typography.titleMedium)
                        Text("$wins won of ${finished.size} · streak $streak (best $bestStreak)", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    Icon(GameIcons.Trophy, null, tint = if (wins > 0) c.highlight else c.outline)
                }
                if (times.isNotEmpty()) {
                    Text("Best times", style = MaterialTheme.typography.labelLarge, color = c.accent)
                    times.forEach { (level, sec) -> ScoreLine(level.ifEmpty { "Any" }, formatSeconds(sec)) }
                }
                if (scores.isNotEmpty() && kind != null) {
                    Text(kind.label, style = MaterialTheme.typography.labelLarge, color = c.accent)
                    scores.forEach { (level, score) -> ScoreLine(level.ifEmpty { "Any" }, score.toString()) }
                }
            }
        }
    }
}

@Composable
private fun ScoreLine(level: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(level, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsScreen(nav: NavController, wipeGames: () -> Unit = {}) {
    val player = LocalPlayer.current
    val records = playerRecords() ?: PlayerRecords()
    val settings = records.settings
    val c = LocalGameLook.current.colors
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    fun change(transform: (com.simplegamegen.sudoku.data.PlayerSettings) -> com.simplegamegen.sudoku.data.PlayerSettings) {
        player?.update { r, _ -> r.copy(settings = transform(r.settings)) }
    }
    GameScaffold(title = "Settings", onBack = { nav.popBackStack() }) {
        SectionTitle("Language")
        Language.entries.forEach { lang ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(settings.language == lang, role = Role.RadioButton) { change { it.copy(language = lang) } },
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = settings.language == lang, onClick = null)
                // Each language's own name is drawn in that language, so 简体中文 uses Chinese glyphs.
                CompositionLocalProvider(LocalLanguage provides if (lang == Language.SYSTEM) LocalLanguage.current else lang) {
                    Text(lang.native, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        SectionTitle("Playing")
        OptionGroup("New games start", listOf(true, false), settings.timedByDefault, { if (it) "Timed" else "Untimed" }) { timed ->
            change { it.copy(timedByDefault = timed) }
        }
        Text("Each game's start screen also lets you choose. Only timed wins count towards best times.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        SettingSwitch("Show the timer while playing", settings.showTimer) { v -> change { it.copy(showTimer = v) } }
        SettingSwitch("Offer tutorials on start screens", settings.offerTutorials) { v -> change { it.copy(offerTutorials = v) } }
        SettingSwitch("Keep the screen on while the app is open", settings.keepScreenOn) { v -> change { it.copy(keepScreenOn = v) } }

        SectionTitle("Hints")
        SettingSwitch("Earn hints through play", settings.earnHints) { v -> change { it.copy(earnHints = v) } }
        Text(if (settings.earnHints) "Each hint uses one from your wallet. You have ${records.wallet}. Win a game to earn more: 1 on the easiest level up to 4 on the hardest, plus 1 for each tutorial you finish."
            else "Hints are free and unlimited. Switch this on to earn hints by winning games instead.",
            style = MaterialTheme.typography.bodySmall, color = c.muted)

        SectionTitle("Appearance")
        OutlinedButton(onClick = { nav.navigate("themes") }, modifier = Modifier.fillMaxWidth()) {
            Icon(GameIcons.Palette, null, Modifier.size(18.dp)); Text("  Themes and dark mode")
        }

        SectionTitle("Your data")
        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("Clear finished games and high scores") }
        Text("Games in progress, saves and your hint wallet are kept.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        OutlinedButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth()) { Text("Clear all started and finished games") }
        Text("Games in progress, finished games, high scores and Sudoku statistics are removed. Your name, settings, hint wallet, custom grids and themes stay.",
            style = MaterialTheme.typography.bodySmall, color = c.muted)

        SectionTitle("About")
        OutlinedButton(onClick = { nav.navigate("credits") }, modifier = Modifier.fillMaxWidth()) { Text("Credits and licenses") }
        val context = LocalContext.current
        OutlinedButton(onClick = {
            // Opens in the phone's browser; the app itself has no internet access.
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }
        }, modifier = Modifier.fillMaxWidth()) { Text("Privacy policy") }

        if (version.isNotEmpty()) Text("Version $version", style = MaterialTheme.typography.bodySmall, color = c.muted,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
    }
    if (confirmClear) ConfirmDialog("Clear your history?", "This removes every finished game and all high scores. It can't be undone.", "Clear",
        onConfirm = { player?.update { r, _ -> r.resetHistory() }; confirmClear = false }, onDismiss = { confirmClear = false }, dismiss = "Cancel", destructive = true)
    if (confirmWipe) ConfirmDialog(
        "Clear every started and finished game?",
        "This removes every game in progress, every finished game, high scores and Sudoku statistics. It can't be undone.",
        "Clear all games",
        onConfirm = { wipeGames(); confirmWipe = false }, onDismiss = { confirmWipe = false }, dismiss = "Cancel", destructive = true)
}

const val PRIVACY_URL = "https://github.com/unexpear/pencil-and-tile/blob/main/PRIVACY.md"

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Shown when a hint is refused because the wallet is empty. */
@Composable
fun OutOfHintsDialog(onSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(GameIcons.Lock, null) }, title = { Text("No hints left") },
        text = { Text("Win games to earn hints: 1 on the easiest level, up to 4 on the hardest. Finishing a tutorial earns 1 too. " +
            "You can also switch off “Earn hints through play” in Settings.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = { TextButton(onClick = onSettings) { Text("Settings") } },
        containerColor = LocalGameLook.current.colors.surface)
}
