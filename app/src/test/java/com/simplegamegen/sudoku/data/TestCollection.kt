package com.simplegamegen.sudoku.data

import kotlinx.coroutines.CompletableDeferred

class TestCollection : PuzzleLedger, ArcadeStore {
    var sequence = 0L
    val history = mutableMapOf<String, List<String>>()
    val saves = mutableMapOf<String, String>()
    var writes = 0
    var failSave = false
    var saveGate: CompletableDeferred<Unit>? = null
    override suspend fun nextSeed(): Long = ++sequence
    override suspend fun recent(key: String): List<String> = history[key].orEmpty()
    override suspend fun remember(key: String, fingerprint: String, window: Int): Boolean {
        val old = history[key].orEmpty()
        if (fingerprint in old) return false
        history[key] = (old + fingerprint).takeLast(window)
        return true
    }
    override suspend fun load(key: String): String? = saves[key]
    override suspend fun save(key: String, encoded: String) {
        saveGate?.await()
        if (failSave) error("storage unavailable")
        saves[key] = encoded
        writes++
    }
}
