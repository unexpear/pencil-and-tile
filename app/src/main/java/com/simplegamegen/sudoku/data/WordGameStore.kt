package com.simplegamegen.sudoku.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.simplegamegen.sudoku.words.WordGame
import com.simplegamegen.sudoku.words.WordProgress
import com.simplegamegen.sudoku.words.WordSaveCodec
import kotlinx.coroutines.flow.first

interface WordGameStore {
    suspend fun load(game: WordGame): WordProgress?
    suspend fun save(progress: WordProgress)
}

class DataStoreWordGames(private val dataStore: DataStore<Preferences>) : WordGameStore {
    override suspend fun load(game: WordGame): WordProgress? {
        val encoded = dataStore.data.first()[stringPreferencesKey(game.name)] ?: return null
        val saved = WordSaveCodec.decode(encoded)
        check(saved != null && saved.puzzle.game == game) { "Invalid word game save" }
        return saved
    }

    override suspend fun save(progress: WordProgress) {
        dataStore.edit { it[stringPreferencesKey(progress.puzzle.game.name)] = WordSaveCodec.encode(progress) }
    }
}
