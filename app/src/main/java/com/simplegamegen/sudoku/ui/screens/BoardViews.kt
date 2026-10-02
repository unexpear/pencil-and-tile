package com.simplegamegen.sudoku.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.simplegamegen.sudoku.ui.assets.BoardView
import com.simplegamegen.sudoku.ui.assets.BoardViews
import com.simplegamegen.sudoku.ui.assets.TableFrame
import com.simplegamegen.sudoku.ui.assets.tableFrame
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import java.util.Locale

/** Saved cameras for the chess and Go boards. The numbers on screen are what a later build can bake in. */
/** [defaults] are this game's built-in views, which Reset views restores. */
internal class BoardCamera(private val prefs: SharedPreferences, private val defaults: List<BoardView> = BoardViews.defaults) {
    var views by mutableStateOf(load())
    var index by mutableIntStateOf(0)
    var free by mutableStateOf(false)
    var live by mutableStateOf(views.first())
    var details by mutableStateOf(prefs.getBoolean(DETAILS, false))

    val selectedName: String get() = views[index.coerceIn(views.indices)].name

    fun select(i: Int) {
        index = i
        free = false
        live = views[i]
    }

    fun armFree() { free = true }

    fun drag(dx: Float, dy: Float) {
        val yaw = wrap(live.yaw + dx * 0.28f)
        val pitch = (live.pitch - dy * 0.18f).coerceIn(16f, 90f)
        if (yaw == live.yaw && pitch == live.pitch) return
        live = live.copy(yaw = yaw, pitch = pitch)
    }

    fun pinch(zoom: Float) {
        if (zoom == 1f) return
        val distance = (live.distance / zoom).coerceIn(1.05f, 4f)
        if (distance == live.distance) return
        live = live.copy(distance = distance)
    }

    fun save() {
        val view = live.copy(name = "View ${views.size + 1}")
        views = views + view
        index = views.lastIndex
        free = false
        live = view
        persist()
    }

    fun replace() {
        val i = index.coerceIn(views.indices)
        val next = views[i].copy(yaw = live.yaw, pitch = live.pitch, distance = live.distance)
        views = views.toMutableList().also { it[i] = next }
        live = next
        free = false
        persist()
    }

    fun remove() {
        if (index < defaults.size || views.size <= defaults.size) return
        views = views.filterIndexed { i, _ -> i != index }
        index = index.coerceAtMost(views.lastIndex)
        free = false
        live = views[index]
        persist()
    }

    val canRemove: Boolean get() = index >= defaults.size && views.size > defaults.size

    fun reset() {
        views = defaults
        index = 0
        free = false
        live = views[0]
        details = false
        prefs.edit().remove(VIEWS).putBoolean(DETAILS, false).apply()
    }

    fun toggleDetails() {
        details = !details
        prefs.edit().putBoolean(DETAILS, details).apply()
    }

    private fun persist() {
        prefs.edit().putString(VIEWS, views.joinToString("\n") { encode(it) }).apply()
    }

    private fun load(): List<BoardView> {
        val raw = prefs.getString(VIEWS, null) ?: return defaults
        return raw.lineSequence().mapNotNull(::decode).toList().ifEmpty { defaults }
    }

    private fun encode(view: BoardView) =
        listOf(view.name, num(view.yaw), num(view.pitch), num(view.distance)).joinToString("|")

    private fun decode(line: String): BoardView? {
        val parts = line.split('|')
        if (parts.size != 4 || parts[0].isBlank()) return null
        val yaw = parts[1].toFloatOrNull() ?: return null
        val pitch = parts[2].toFloatOrNull() ?: return null
        val distance = parts[3].toFloatOrNull() ?: return null
        return BoardView(parts[0], yaw, pitch, distance)
    }

    companion object {
        private const val VIEWS = "views"
        private const val DETAILS = "details"
        private fun num(value: Float) = String.format(Locale.US, "%.2f", value)
        private fun wrap(deg: Float): Float {
            var d = deg % 360f
            if (d > 180f) d -= 360f
            if (d <= -180f) d += 360f
            return d
        }
    }
}

@Composable
internal fun rememberBoardCamera(store: String = "board_views", defaults: List<BoardView> = BoardViews.defaults): BoardCamera {
    val context = LocalContext.current
    return remember {
        BoardCamera(context.getSharedPreferences(store, Context.MODE_PRIVATE), defaults)
    }
}

/** One row of cameras, an optional readout, and the board in whatever space is left. */
@Composable
internal fun BoardWithViews(
    camera: BoardCamera,
    n: Int,
    peakZ: Float,
    margin: Float = 0.72f,
    /** How deep the table is, front to back, when it isn't square. */
    rows: Float = n.toFloat(),
    /** A fixed height for the board, for screens that scroll; otherwise it takes the space that's left. */
    height: androidx.compose.ui.unit.Dp? = null,
    content: @Composable BoxScope.(TableFrame) -> Unit,
) {
    val colors = LocalGameLook.current.colors
    val chipScroll = rememberScrollState()
    val actionScroll = rememberScrollState()
    // The board first, then the camera controls under it, where a thumb reaches them on a phone.
    Column(if (height == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box((if (height == null) Modifier.weight(1f) else Modifier.height(height)).fillMaxWidth()) { BoardFrame(camera, n, peakZ, margin, rows, content) }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(chipScroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            camera.views.forEachIndexed { i, view ->
                FilterChip(
                    selected = !camera.free && camera.index == i,
                    onClick = { camera.select(i) },
                    label = { Text(view.name) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface),
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
            FilterChip(
                selected = camera.free,
                onClick = camera::armFree,
                label = { Text("Free camera") },
                colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface),
                modifier = Modifier.heightIn(min = 40.dp),
            )
            FilterChip(
                selected = camera.details,
                onClick = camera::toggleDetails,
                label = { Text("View details") },
                colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface),
                modifier = Modifier.heightIn(min = 40.dp),
            )
        }
        if (camera.details) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(actionScroll),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionContainer {
                    Text(BoardViews.line(camera.live), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
                TextButton(onClick = camera::save, modifier = Modifier.heightIn(min = 40.dp)) { Text("Save view") }
                TextButton(onClick = camera::replace, modifier = Modifier.heightIn(min = 40.dp)) { Text("Replace ${camera.selectedName}") }
                if (camera.canRemove) TextButton(onClick = camera::remove, modifier = Modifier.heightIn(min = 40.dp)) { Text("Remove view") }
                TextButton(onClick = camera::reset, modifier = Modifier.heightIn(min = 40.dp)) { Text("Reset views") }
            }
        }
        if (camera.free) {
            Text("Drag to look around. Pinch to move closer.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
    }
}

@Composable
private fun BoardFrame(
    camera: BoardCamera,
    n: Int,
    peakZ: Float,
    margin: Float,
    rows: Float,
    content: @Composable BoxScope.(TableFrame) -> Unit,
) {
    val current = rememberUpdatedState(camera)
    BoxWithConstraints(
        // Pinch zooms in any view; dragging turns the board only with the free camera, so taps on pieces still work.
        Modifier.fillMaxSize().pointerInput(Unit) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            var dragged = false
                            var total = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    val zoom = event.calculateZoom()
                                    if (zoom != 1f) current.value.pinch(zoom)
                                    event.changes.forEach { it.consume() }
                                    dragged = true
                                } else if (current.value.free) {
                                    val pan = event.calculatePan()
                                    total += pan
                                    if (!dragged && total.getDistance() > slop) dragged = true
                                    if (dragged && pan != Offset.Zero) {
                                        current.value.drag(pan.x, pan.y)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    },
        contentAlignment = Alignment.Center,
    ) {
        val display = LocalDensity.current
        val frame = tableFrame(
            n,
            with(display) { maxWidth.toPx() },
            peakZ,
            view = camera.live,
            maxHeightPx = with(display) { maxHeight.toPx() },
            margin = margin,
            rows = rows,
        )
        Box(Modifier.size(with(display) { frame.width.toDp() }, with(display) { frame.height.toDp() })) { content(frame) }
    }
}
