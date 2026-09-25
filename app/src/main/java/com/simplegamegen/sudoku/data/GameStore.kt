package com.simplegamegen.sudoku.data

import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.VariantType
import kotlinx.coroutines.flow.Flow

/** Persistence boundary, also used by deterministic game lifecycle tests. */
interface GameStore {
    val statRows: Flow<List<StatsStore.StatRow>>
    suspend fun save(snapshot: PlaySnapshot)
    suspend fun load(): PlaySnapshot?
    suspend fun clear()
    suspend fun hasSave(): Boolean
    suspend fun recordStart(variant: VariantType, size: Int, difficulty: Difficulty)
    suspend fun recordWin(variant: VariantType, size: Int, difficulty: Difficulty, elapsedSec: Long)
}
