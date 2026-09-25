package com.simplegamegen.sudoku.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

interface PlayerStorage {
    suspend fun read(): String?
    suspend fun write(text: String)
}

class DataStorePlayer(private val data: DataStore<Preferences>) : PlayerStorage {
    private val key = stringPreferencesKey("player")
    override suspend fun read(): String? = data.data.first()[key]
    override suspend fun write(text: String) { data.edit { it[key] = text } }
}

/**
 * Holds the player's records for the whole app. Changes apply in memory straight away and are
 * saved in order; timer ticks are batched. Changes made before the saved records have loaded
 * are replayed on top of them.
 */
class PlayerService(
    private val storage: PlayerStorage,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val batchMs: Long = 10_000,
) {
    private val mutable = MutableStateFlow(PlayerRecords())
    val records: StateFlow<PlayerRecords> = mutable.asStateFlow()
    private val loadedFlow = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = loadedFlow.asStateFlow()

    /** Set when a hint was refused because the wallet is empty; the UI shows a dialog and clears it. */
    private val outOfHintsFlow = MutableStateFlow(false)
    val outOfHints: StateFlow<Boolean> = outOfHintsFlow.asStateFlow()

    private val pending = mutableListOf<(PlayerRecords, Long) -> PlayerRecords>()
    private var saveJob: Job? = null
    private var writeChain: Job? = null

    init {
        scope.launch {
            val stored = PlayerCodec.decode(runCatching { storage.read() }.getOrNull())
            val stamp = now()
            mutable.value = pending.fold(stored) { r, change -> change(r, stamp) }
            pending.clear()
            loadedFlow.value = true
            if (mutable.value != stored) save(urgent = true)
        }
    }

    /** Applies [change]; [urgent] saves straight away, otherwise within [batchMs]. */
    fun update(urgent: Boolean = true, change: (PlayerRecords, Long) -> PlayerRecords) {
        if (!loadedFlow.value) pending += change
        val next = change(mutable.value, now())
        if (next == mutable.value) return
        mutable.value = next
        if (loadedFlow.value) save(urgent)
    }

    /** Runs [action] if a hint is available, paying for it when hints are earned. */
    fun useHint(game: String, action: () -> Unit) {
        val next = mutable.value.spendHint(game)
        if (next == null) { outOfHintsFlow.value = true; return }
        update { r, _ -> r.spendHint(game) ?: r }
        action()
    }

    fun dismissOutOfHints() { outOfHintsFlow.value = false }

    /** Saves any batched changes now (the app is going to the background). */
    fun flush() { if (loadedFlow.value && saveJob?.isActive == true) save(urgent = true) }

    private fun save(urgent: Boolean) {
        if (!urgent && saveJob?.isActive == true) return
        saveJob?.cancel()
        saveJob = scope.launch {
            if (!urgent) delay(batchMs)
            // Writes stay in order even when a later save starts before an earlier one ends.
            val previous = writeChain
            writeChain = coroutineContext[Job]
            previous?.join()
            runCatching { storage.write(PlayerCodec.encode(mutable.value)) }
        }
    }
}
