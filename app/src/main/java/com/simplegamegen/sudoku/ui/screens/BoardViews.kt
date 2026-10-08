package com.simplegamegen.sudoku.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.simplegamegen.sudoku.ui.assets.BoardView
import com.simplegamegen.sudoku.ui.assets.BoardViews
import com.simplegamegen.sudoku.ui.assets.TableFrame
import com.simplegamegen.sudoku.ui.assets.decodeBoardView
import com.simplegamegen.sudoku.ui.assets.encodeBoardView
import com.simplegamegen.sudoku.ui.assets.magnifyAround
import com.simplegamegen.sudoku.ui.assets.tableFrame
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

/** Saved cameras for the chess and Go boards. The numbers on screen are what a later build can bake in. */
/** [defaults] are this game's built-in views, which Reset views restores. */
internal class BoardCamera(
    private val prefs: SharedPreferences,
    private val defaults: List<BoardView> = BoardViews.defaults,
    /** When the saved list is still exactly this, it is the old built-in set and should become [defaults]. */
    private val replaced: List<BoardView> = BoardViews.previous,
) {
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

    /** Magnifies around [focus] (a point in the board view, in pixels). The camera stays put. */
    fun zoomBy(factor: Float, focus: Offset, width: Float, height: Float) {
        if (factor == 1f) return
        val (zoom, panX, panY) = magnifyAround(live.zoom, live.panX, live.panY, factor, focus.x, focus.y, width, height)
        if (zoom == live.zoom && panX == live.panX && panY == live.panY) return
        live = live.copy(zoom = zoom, panX = panX, panY = panY)
    }

    /** Slides the picture by [dx] and [dy] pixels. */
    fun slide(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        live = live.copy(panX = live.panX + dx, panY = live.panY + dy)
    }

    /** Puts zoom and pan back to the fitted view. The orbit camera is left as it is. */
    fun fit() {
        if (live.fitted) return
        live = live.copy(zoom = 1f, panX = 0f, panY = 0f)
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
        val next = live.copy(name = views[i].name)
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
        val saved = raw.lineSequence().mapNotNull(::decode).toList().ifEmpty { return defaults }
        if (replaced.isNotEmpty() && saved == replaced) {
            prefs.edit().remove(VIEWS).apply()
            return defaults
        }
        return saved
    }

    private fun encode(view: BoardView) = encodeBoardView(view)

    private fun decode(line: String): BoardView? = decodeBoardView(line)

    companion object {
        private const val VIEWS = "views"
        private const val DETAILS = "details"
        private fun wrap(deg: Float): Float {
            var d = deg % 360f
            if (d > 180f) d -= 360f
            if (d <= -180f) d += 360f
            return d
        }
    }
}

@Composable
internal fun rememberBoardCamera(
    store: String = "board_views",
    defaults: List<BoardView> = BoardViews.defaults,
    replaced: List<BoardView> = BoardViews.previous,
): BoardCamera {
    val context = LocalContext.current
    return remember {
        BoardCamera(context.getSharedPreferences(store, Context.MODE_PRIVATE), defaults, replaced)
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
    /** Fit each saved camera on its own, so a turned view still fills the frame. */
    fitToView: Boolean = false,
    /** A fixed height for the board, for screens that scroll; otherwise it takes the space that's left. */
    height: androidx.compose.ui.unit.Dp? = null,
    /** Camera chips. A second board that shares [camera] can hide them. */
    controls: Boolean = true,
    content: @Composable BoxScope.(TableFrame) -> Unit,
) {
    val colors = LocalGameLook.current.colors
    val chipScroll = rememberScrollState()
    val actionScroll = rememberScrollState()
    // The board first, then the camera controls under it, where a thumb reaches them on a phone.
    Column(if (height == null) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box((if (height == null) Modifier.weight(1f) else Modifier.height(height)).fillMaxWidth()) { BoardFrame(camera, n, peakZ, margin, rows, fitToView, content) }
        if (!controls) return@Column
        Row(
            Modifier.fillMaxWidth().horizontalScroll(chipScroll),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            camera.views.forEachIndexed { i, view ->
                FilterChip(
                    selected = !camera.free && camera.index == i,
                    onClick = { camera.select(i) },
                    label = { Text(view.name) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface),
                    modifier = Modifier.heightIn(min = 36.dp),
                )
            }
            FilterChip(
                selected = camera.free,
                onClick = camera::armFree,
                label = { Text("Free camera") },
                colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface),
                modifier = Modifier.heightIn(min = 36.dp),
            )
            FilterChip(
                selected = camera.details,
                onClick = camera::toggleDetails,
                label = { Text("View details") },
                colors = FilterChipDefaults.filterChipColors(containerColor = colors.surface),
                modifier = Modifier.heightIn(min = 36.dp),
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
                TextButton(onClick = camera::fit, modifier = Modifier.heightIn(min = 40.dp)) { Text("Fit the board") }
            }
        } else if (!camera.live.fitted) {
            TextButton(onClick = camera::fit, modifier = Modifier.heightIn(min = 40.dp)) { Text("Fit the board") }
        }
        if (camera.free) {
            Text("Drag to look around. Pinch to zoom. Slide with two fingers.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
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
    fitToView: Boolean,
    content: @Composable BoxScope.(TableFrame) -> Unit,
) {
    val current = rememberUpdatedState(camera)
    BoxWithConstraints(
        // Pinch zooms around the fingers in any view, and two fingers slide the picture.
        // One finger turns the board only with the free camera. A finger left down after a pinch must not turn it.
        Modifier.fillMaxSize().clipToBounds().pointerInput(Unit) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            var dragged = false
                            var sawTwo = false
                            var total = Offset.Zero
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    sawTwo = true
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    if (zoom != 1f) current.value.zoomBy(zoom, centroid, size.width.toFloat(), size.height.toFloat())
                                    if (pan != Offset.Zero) current.value.slide(pan.x, pan.y)
                                    event.changes.forEach { it.consume() }
                                } else if (!sawTwo && current.value.free) {
                                    val pan = event.calculatePan()
                                    total += pan
                                    if (!dragged && total.getDistance() > slop) dragged = true
                                    if (dragged && pan != Offset.Zero) {
                                        current.value.drag(pan.x, pan.y)
                                        event.changes.forEach { it.consume() }
                                    }
                                } else if (sawTwo) {
                                    event.changes.forEach { it.consume() }
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
            fitToView = fitToView,
        )
        Box(Modifier.size(with(display) { frame.width.toDp() }, with(display) { frame.height.toDp() })) { content(frame) }
    }
}
