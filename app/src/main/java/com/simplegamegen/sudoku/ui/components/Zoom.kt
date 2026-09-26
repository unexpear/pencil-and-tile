package com.simplegamegen.sudoku.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.i18n.tr
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

/** Zoom and pan state for one board. [scale] 1 means fitted to the screen. */
class ZoomState(scale: Float = 1f, x: Float = 0f, y: Float = 0f) {
    var scale by mutableFloatStateOf(scale)
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var size by mutableStateOf(IntSize.Zero)

    val zoomed: Boolean get() = scale > 1.01f

    /** Zooms by [factor] around [focus] (in board pixels), keeping the board on screen. */
    fun zoomBy(factor: Float, focus: Offset = Offset(size.width / 2f, size.height / 2f)) {
        val next = (scale * factor).coerceIn(MIN, MAX)
        val applied = next / scale
        x = focus.x - (focus.x - x) * applied
        y = focus.y - (focus.y - y) * applied
        scale = next
        clamp()
    }

    fun panBy(delta: Offset) { x += delta.x; y += delta.y; clamp() }

    fun reset() { scale = 1f; x = 0f; y = 0f }

    /** The scaled board may not leave a gap at any edge. */
    private fun clamp() {
        val minX = size.width - size.width * scale
        val minY = size.height - size.height * scale
        x = x.coerceIn(minOf(minX, 0f), 0f)
        y = y.coerceIn(minOf(minY, 0f), 0f)
    }

    companion object {
        const val MIN = 1f
        const val MAX = 4f
    }
}

@Composable
fun rememberZoomState(key: Any? = null): ZoomState = rememberSaveable(key, saver = androidx.compose.runtime.saveable.Saver<ZoomState, List<Float>>(
    save = { listOf(it.scale, it.x, it.y) }, restore = { ZoomState(it[0], it[1], it[2]) })) { ZoomState() }

/**
 * Lets any board be zoomed and moved: pinch to zoom, drag with two fingers to move, or use the
 * buttons above it. Move board mode reserves single-finger gestures for panning;
 * otherwise they still reach the board for play.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoomBox(modifier: Modifier = Modifier, state: ZoomState = rememberZoomState(), controls: Boolean = true, content: @Composable () -> Unit) {
    var moveBoard by rememberSaveable { mutableStateOf(false) }
    val dragToPan = moveBoard && state.zoomed
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (controls) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.zoomed) {
                FilterChip(selected = moveBoard, onClick = { moveBoard = !moveBoard }, label = { Text("Move board") })
                // Arrow buttons move a third of the view, for when a two-finger drag is awkward.
                ZoomButton(GameIcons.Chevron, tr("Move left"), rotation = 180f) { state.panBy(Offset(state.size.width / 3f, 0f)) }
                ZoomButton(GameIcons.Chevron, tr("Move up"), rotation = -90f) { state.panBy(Offset(0f, state.size.height / 3f)) }
                ZoomButton(GameIcons.Chevron, tr("Move down"), rotation = 90f) { state.panBy(Offset(0f, -state.size.height / 3f)) }
                ZoomButton(GameIcons.Chevron, tr("Move right")) { state.panBy(Offset(-state.size.width / 3f, 0f)) }
                Box(Modifier.size(6.dp))
                ZoomButton(GameIcons.Fit, tr("Fit the board")) { state.reset(); moveBoard = false }
                ZoomButton(GameIcons.Minus, tr("Zoom out")) { state.zoomBy(1 / 1.5f) }
            }
            ZoomButton(GameIcons.Plus, tr("Zoom in"), enabled = state.scale < ZoomState.MAX) { state.zoomBy(1.5f) }
        }
        if (dragToPan) Text("Drag to move the board. Turn off Move board to play.")
        Box(Modifier.clipToBounds().onSizeChanged { state.size = it }
            .pointerInput(state, dragToPan) {
                awaitEachGesture {
                    var claimed = dragToPan
                    // Consume in Initial so the page's vertical scroller and board taps cannot
                    // steal a pan. Keep ownership until all fingers lift after a pinch.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.filter { it.pressed }
                        if (down.size >= 2) claimed = true
                        if (claimed) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (down.size >= 2 && zoom != 1f) state.zoomBy(zoom, centroid)
                            if (down.isNotEmpty() && pan != Offset.Zero) state.panBy(pan)
                            event.changes.forEach { it.consume() }
                        }
                        if (down.isEmpty()) break
                    }
                }
            }) {
            Box(Modifier.graphicsLayer {
                scaleX = state.scale; scaleY = state.scale
                translationX = state.x; translationY = state.y
                transformOrigin = TransformOrigin(0f, 0f)
            }) { content() }
        }
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, label: String, enabled: Boolean = true, rotation: Float = 0f, onClick: () -> Unit) {
    val c = LocalGameLook.current.colors
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = c.surface.copy(alpha = 0.88f), border = BorderStroke(1.dp, c.outline),
        modifier = Modifier.size(34.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (enabled) c.text else c.muted,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation })
        }
    }
}
