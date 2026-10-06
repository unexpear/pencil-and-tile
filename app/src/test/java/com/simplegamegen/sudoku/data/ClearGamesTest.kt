package com.simplegamegen.sudoku.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.VariantType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ClearGamesTest {
    @TempDir lateinit var directory: File

    @Test fun `play saves go and the player's own things stay`() = runBlocking {
        val job = SupervisorJob()
        val data = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(directory, "collection.preferences_pb") },
        )
        try {
            val store = CollectionStore(data)
            store.save("PLAY_CHESS", "board")
            store.save("TABLE_CHECKERS", "match")
            store.save("LOGIC_KENKEN", "puzzle")
            store.save("HANGMAN", "word")
            store.save("MAHJONG", "deal")
            store.save("PLAY_SUDOKU_GRID", "grid")
            store.save("blotwords:trail", "4")
            store.save("blotwords:known", "INK")
            store.save("blotwords:daily", "20261006")
            store.save("wordsworn:core-unlock:SCRIBE", "1")
            store.save("GRID_DRAFTS", "draft")
            store.save("blotwords:themes", "[]")
            store.save("blotwords:theme", "ink")
            store.save("wordsworn:blood", "0")
            assertTrue(store.remember("sudoku:CLASSIC:9:EASY", "fingerprint"))
            val seed = store.nextSeed()
            store.clearPlaySaves()
            listOf(
                "PLAY_CHESS", "TABLE_CHECKERS", "LOGIC_KENKEN", "HANGMAN", "MAHJONG", "PLAY_SUDOKU_GRID",
                "blotwords:trail", "blotwords:known", "blotwords:daily", "wordsworn:core-unlock:SCRIBE",
            ).forEach { assertNull(store.load(it), it) }
            assertEquals("draft", store.load("GRID_DRAFTS"))
            assertEquals("[]", store.load("blotwords:themes"))
            assertEquals("ink", store.load("blotwords:theme"))
            assertEquals("0", store.load("wordsworn:blood"))
            assertEquals(listOf("fingerprint"), store.recent("sudoku:CLASSIC:9:EASY"))
            assertEquals(seed + 1, store.nextSeed())
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun `sudoku statistics and word saves are emptied`() = runBlocking {
        val job = SupervisorJob()
        val statsData = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(directory, "stats.preferences_pb") },
        )
        val wordsData = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { File(directory, "words.preferences_pb") },
        )
        try {
            val stats = StatsStore(statsData)
            stats.recordStart(VariantType.CLASSIC, 9, Difficulty.EASY)
            stats.recordWin(VariantType.CLASSIC, 9, Difficulty.EASY, 40)
            assertEquals(1, stats.stats.first().single().won)
            stats.clear()
            assertTrue(stats.stats.first().isEmpty())
            wordsData.edit {
                it[stringPreferencesKey("CROSSWORD")] = "saved"
                it[stringPreferencesKey("WORD_SEARCH")] = "saved"
            }
            DataStoreWordGames(wordsData).clear()
            assertNull(wordsData.data.first()[stringPreferencesKey("CROSSWORD")])
            assertNull(wordsData.data.first()[stringPreferencesKey("WORD_SEARCH")])
        } finally {
            job.cancelAndJoin()
        }
    }
}
