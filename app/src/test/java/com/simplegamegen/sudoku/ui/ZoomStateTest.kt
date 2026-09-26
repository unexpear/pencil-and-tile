package com.simplegamegen.sudoku.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.simplegamegen.sudoku.ui.components.ZoomState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZoomStateTest {
    @Test fun `vertical and horizontal panning reach both edges without exposing gaps`() {
        val state = ZoomState().apply { size = IntSize(300, 200) }
        state.zoomBy(2f)
        assertEquals(-150f, state.x)
        assertEquals(-100f, state.y)
        state.panBy(Offset(-1000f, -1000f))
        assertEquals(-300f, state.x)
        assertEquals(-200f, state.y)
        state.panBy(Offset(1000f, 1000f))
        assertEquals(0f, state.x)
        assertEquals(0f, state.y)
        state.reset()
        state.panBy(Offset(-100f, -100f))
        assertEquals(1f, state.scale)
        assertEquals(0f, state.x)
        assertEquals(0f, state.y)
    }
}
