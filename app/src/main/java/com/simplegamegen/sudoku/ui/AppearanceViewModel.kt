package com.simplegamegen.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.ui.theme.AppearanceSettings
import com.simplegamegen.sudoku.ui.theme.AppearanceStore
import com.simplegamegen.sudoku.ui.theme.BuiltInThemes
import com.simplegamegen.sudoku.ui.theme.CustomTheme
import com.simplegamegen.sudoku.ui.theme.DarkMode
import com.simplegamegen.sudoku.ui.theme.ThemeCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class AppearanceViewModel(private val store: AppearanceStore) : ViewModel() {
    private val mutable = MutableStateFlow(AppearanceSettings())
    val settings: StateFlow<AppearanceSettings> = mutable
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            try {
                store.settings.collect { mutable.value = it }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _message.value = "Couldn't read your theme settings. Using the default theme." }
        }
    }

    fun select(id: String) = update { it.copy(selectedId = id) }
    fun setDarkMode(mode: DarkMode) = update { it.copy(darkMode = mode) }

    /** Adds a new theme or replaces the one with the same id, then selects it. */
    fun save(theme: CustomTheme) = update { s ->
        val others = s.customThemes.filter { it.id != theme.id }
        val index = s.customThemes.indexOfFirst { it.id == theme.id }
        val list = if (index < 0) others + theme else others.toMutableList().apply { add(index, theme) }
        s.copy(customThemes = list, selectedId = theme.id)
    }

    fun delete(id: String) = update { s ->
        s.copy(customThemes = s.customThemes.filter { it.id != id },
            selectedId = if (s.selectedId == id) BuiltInThemes.default.id else s.selectedId)
    }

    /** Returns true when the code was valid and the theme was added. */
    fun import(code: String): Boolean {
        val theme = ThemeCodec.decode(code, newId()) ?: return false
        save(theme)
        return true
    }

    fun newId(): String = "custom-${System.currentTimeMillis().toString(36)}-${Random.nextInt(1 shl 20).toString(36)}"
    fun consumeMessage() { _message.value = null }

    private fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch {
            try {
                store.update(transform)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { _message.value = "Couldn't save your theme settings. Try again." }
        }
    }
}
