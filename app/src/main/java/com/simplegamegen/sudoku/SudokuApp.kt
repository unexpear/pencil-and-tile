package com.simplegamegen.sudoku

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.simplegamegen.sudoku.data.GameRepository
import com.simplegamegen.sudoku.data.StatsStore
import com.simplegamegen.sudoku.data.SudokuDatabase
import com.simplegamegen.sudoku.data.DataStoreWordGames
import com.simplegamegen.sudoku.data.CollectionStore
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.DataStorePlayer
import com.simplegamegen.sudoku.data.PlayerService
import kotlinx.coroutines.MainScope
import com.simplegamegen.sudoku.ui.theme.DataStoreAppearance

private val Context.statsDataStore: DataStore<Preferences> by preferencesDataStore("stats")
private val Context.wordGamesDataStore: DataStore<Preferences> by preferencesDataStore("word_games")
private val Context.collectionDataStore: DataStore<Preferences> by preferencesDataStore("collection")
private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore("appearance")
private val Context.playerDataStore: DataStore<Preferences> by preferencesDataStore("player")

class SudokuApp : Application() {
    val wordGames by lazy { DataStoreWordGames(wordGamesDataStore) }
    val collection by lazy { CollectionStore(collectionDataStore) }
    val puzzles by lazy { PuzzleFactory(collection) }
    val appearance by lazy { DataStoreAppearance(appearanceDataStore) }
    /** On-device sentence model for Word Meaning, loaded on first use. */
    val meaningModel by lazy { com.simplegamegen.sudoku.data.MeaningModel(this) }
    val player by lazy { PlayerService(DataStorePlayer(playerDataStore), MainScope()) }

    val database: SudokuDatabase by lazy {
        Room.databaseBuilder(this, SudokuDatabase::class.java, "sudoku.db").build()
    }

    val statsStore: StatsStore by lazy {
        StatsStore(statsDataStore)
    }

    val repository: GameRepository by lazy {
        GameRepository(database, statsStore)
    }
}
