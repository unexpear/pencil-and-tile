package com.simplegamegen.sudoku.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.VariantType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Offline-first stats: games played / won / best time per
 * variant+size+difficulty, stored as flat preference keys.
 */
class StatsStore(private val dataStore: DataStore<Preferences>) {

    data class StatRow(
        val variant: VariantType,
        val size: Int,
        val difficulty: Difficulty,
        val played: Long,
        val won: Long,
        val bestTimeSec: Long?,
    )

    private fun key(kind: String, variant: VariantType, size: Int, difficulty: Difficulty) =
        longPreferencesKey("$kind:${variant.name}:$size:${difficulty.name}")

    suspend fun recordStart(variant: VariantType, size: Int, difficulty: Difficulty) {
        dataStore.edit { prefs ->
            val k = key("played", variant, size, difficulty)
            prefs[k] = (prefs[k] ?: 0L) + 1
        }
    }

    suspend fun recordWin(variant: VariantType, size: Int, difficulty: Difficulty, elapsedSec: Long) {
        dataStore.edit { prefs ->
            val wonKey = key("won", variant, size, difficulty)
            prefs[wonKey] = (prefs[wonKey] ?: 0L) + 1
            val bestKey = key("best", variant, size, difficulty)
            val prev = prefs[bestKey]
            if (prev == null || elapsedSec < prev) prefs[bestKey] = elapsedSec
        }
    }

    val stats: Flow<List<StatRow>> = dataStore.data.map { prefs ->
        val rows = mutableListOf<StatRow>()
        for (variant in VariantType.entries) {
            for (size in listOf(9, 6, 4)) {
                for (difficulty in Difficulty.entries) {
                    val played = prefs[key("played", variant, size, difficulty)] ?: 0L
                    if (played == 0L) continue
                    rows.add(
                        StatRow(
                            variant = variant,
                            size = size,
                            difficulty = difficulty,
                            played = played,
                            won = prefs[key("won", variant, size, difficulty)] ?: 0L,
                            bestTimeSec = prefs[key("best", variant, size, difficulty)],
                        ),
                    )
                }
            }
        }
        rows.sortedWith(compareBy({ it.variant.ordinal }, { it.size }, { it.difficulty.ordinal }))
    }
}
