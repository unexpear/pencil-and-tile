package com.simplegamegen.sudoku.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppearanceSettings(
    val selectedId: String = BuiltInThemes.default.id,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val customThemes: List<CustomTheme> = emptyList(),
) {
    val themes: List<ThemeSpec> get() = BuiltInThemes.all + customThemes.map { it.resolve() }
    val selected: ThemeSpec get() = themes.firstOrNull { it.id == selectedId } ?: BuiltInThemes.default
    fun custom(id: String): CustomTheme? = customThemes.firstOrNull { it.id == id }
}

interface AppearanceStore {
    val settings: Flow<AppearanceSettings>
    suspend fun update(transform: (AppearanceSettings) -> AppearanceSettings)
}

class DataStoreAppearance(private val data: DataStore<Preferences>) : AppearanceStore {
    private val selectedKey = stringPreferencesKey("theme")
    private val darkKey = stringPreferencesKey("dark_mode")
    private val customKey = stringPreferencesKey("custom_themes")

    override val settings: Flow<AppearanceSettings> = data.data.map(::read)

    private fun read(prefs: Preferences) = AppearanceSettings(
        selectedId = prefs[selectedKey] ?: BuiltInThemes.default.id,
        darkMode = prefs[darkKey]?.let { runCatching { DarkMode.valueOf(it) }.getOrNull() } ?: DarkMode.SYSTEM,
        // Unreadable entries are skipped rather than breaking the whole list.
        customThemes = prefs[customKey]?.split('\n')?.mapNotNull(ThemeCodec::decodeStored).orEmpty(),
    )

    override suspend fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        data.edit { prefs ->
            val next = transform(read(prefs))
            prefs[selectedKey] = next.selectedId
            prefs[darkKey] = next.darkMode.name
            prefs[customKey] = next.customThemes.joinToString("\n", transform = ThemeCodec::encodeStored)
        }
    }
}
