package com.simplegamegen.sudoku.ui

import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private class MemoryAppearance : AppearanceStore {
    val state = MutableStateFlow(AppearanceSettings())
    var fail = false
    override val settings: StateFlow<AppearanceSettings> = state
    override suspend fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        if (fail) error("disk full")
        state.value = transform(state.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private val models = mutableListOf<AppearanceViewModel>()
    @BeforeEach fun setup() { dispatcher = StandardTestDispatcher(); Dispatchers.setMain(dispatcher) }
    @AfterEach fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun model(store: MemoryAppearance) = AppearanceViewModel(store).also { models += it }

    @Test fun `selecting themes and dark mode persists`() = runTest {
        val store = MemoryAppearance(); val vm = model(store)
        vm.select("minimal"); vm.setDarkMode(DarkMode.DARK); runCurrent()
        assertEquals(BuiltInThemes.minimal, vm.settings.value.selected)
        assertEquals(DarkMode.DARK, store.state.value.darkMode)
    }

    @Test fun `saving edits replaces in place, selects, and deleting falls back to the default`() = runTest {
        val store = MemoryAppearance(); val vm = model(store)
        val first = CustomTheme.from(BuiltInThemes.table, "a", "One")
        val second = CustomTheme.from(BuiltInThemes.playful, "b", "Two")
        vm.save(first); vm.save(second); runCurrent()
        vm.save(first.copy(name = "One edited")); runCurrent()
        assertEquals(listOf("One edited", "Two"), store.state.value.customThemes.map { it.name })
        assertEquals("a", store.state.value.selectedId)
        vm.delete("a"); runCurrent()
        assertEquals(BuiltInThemes.default.id, store.state.value.selectedId)
        assertEquals(listOf("b"), store.state.value.customThemes.map { it.id })
    }

    @Test fun `imports accept valid codes only`() = runTest {
        val store = MemoryAppearance(); val vm = model(store)
        val code = ThemeCodec.encode(CustomTheme.from(BuiltInThemes.minimal, "x", "Shared").copy(overrides = mapOf(ThemeToken.ACCENT to 0xFF993556)))
        assertFalse(vm.import("not a theme")); runCurrent()
        assertTrue(store.state.value.customThemes.isEmpty())
        assertTrue(vm.import(code)); runCurrent()
        val imported = store.state.value.customThemes.single()
        assertEquals("Shared", imported.name)
        assertEquals(imported.id, store.state.value.selectedId)
        assertEquals(0xFF993556, vm.settings.value.selected.light[ThemeToken.ACCENT])
    }

    @Test fun `storage failures are reported without changing settings`() = runTest {
        val store = MemoryAppearance(); val vm = model(store)
        runCurrent(); store.fail = true
        vm.select("playful"); runCurrent()
        assertEquals(BuiltInThemes.default, vm.settings.value.selected)
        assertNotNull(vm.message.value)
    }
}
