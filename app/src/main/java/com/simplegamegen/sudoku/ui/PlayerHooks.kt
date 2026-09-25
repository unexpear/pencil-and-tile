package com.simplegamegen.sudoku.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PlayerRecords
import com.simplegamegen.sudoku.data.PlayerService
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.ToolButton
import kotlinx.coroutines.delay

/** The app's player records; null in previews and tests. */
val LocalPlayer = staticCompositionLocalOf<PlayerService?> { null }

@Composable
fun playerRecords(): PlayerRecords? = LocalPlayer.current?.let { it.records.collectAsStateWithLifecycle().value }

/**
 * Reports the game on screen to the player's records: opens a session while it's in progress,
 * runs its clock while the screen is visible, and records the result once when it ends.
 * [key] identifies the particular game (its seed); [clock] is for games that keep their own time.
 */
@Composable
fun ReportPlay(id: GameId, key: String?, level: String, levelIndex: Int, inProgress: Boolean, outcome: Outcome?, clock: Long? = null,
    /** Record under another name when one game has two separate saves (16×16 and custom grids are Sudoku too). */
    record: String = id.name) {
    val player = LocalPlayer.current ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    val levelNow by rememberUpdatedState(level)
    val indexNow by rememberUpdatedState(levelIndex)
    LaunchedEffect(key, inProgress) {
        if (key != null && inProgress) player.update { r, now -> r.ensure(record, key, levelNow, indexNow, now) }
    }
    LaunchedEffect(key, outcome) {
        if (key != null && outcome != null) player.update { r, now -> r.finish(record, key, outcome, now) }
    }
    if (clock != null) LaunchedEffect(key, clock) {
        if (key != null) player.update(urgent = false) { r, now -> r.clock(record, key, clock, now) }
    } else LaunchedEffect(key, inProgress, resumed) {
        if (key != null && inProgress && resumed) while (true) {
            delay(1000)
            player.update(urgent = false) { r, now -> r.tick(record, key, 1, now) }
        }
    }
    DisposableEffect(key) { onDispose { player.flush() } }
}

/** mm:ss, or h:mm:ss past an hour. */
fun formatSeconds(total: Long): String {
    val h = total / 3600; val m = total / 60 % 60; val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** The running clock for [id] when it's a timed game and the timer is shown. */
@Composable
fun SessionClock(id: GameId, modifier: Modifier = Modifier, record: String = id.name) {
    val records = playerRecords() ?: return
    val session = records.sessions[record] ?: return
    if (!session.timed || !records.settings.showTimer) return
    Text(formatSeconds(session.seconds), modifier.padding(horizontal = 8.dp),
        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
}

/** Hint tool button: pays from the hint wallet when hints are earned, and shows the balance. */
@Composable
fun RowScope.HintButton(id: GameId, label: String = "Hint", enabled: Boolean = true, record: String = id.name, onClick: () -> Unit) {
    val player = LocalPlayer.current
    val records = playerRecords()
    val shown = if (records?.settings?.earnHints == true) "$label · ${records.wallet}" else label
    ToolButton(GameIcons.Hint, shown, enabled = enabled) { if (player == null) onClick() else player.useHint(record, onClick) }
}

/** Timed or untimed choice for the next game of [id]. */
@Composable
fun PlayModeChoice(id: GameId, record: String = id.name) {
    val player = LocalPlayer.current ?: return
    val records = playerRecords() ?: return
    OptionGroup("Play mode", listOf(true, false), records.timedFor(record), { if (it) "Timed" else "Untimed" }) { timed ->
        player.update { r, _ -> r.copy(timedChoice = r.timedChoice + (record to timed)) }
    }
}
