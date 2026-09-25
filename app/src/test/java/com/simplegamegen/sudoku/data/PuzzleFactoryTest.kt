package com.simplegamegen.sudoku.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.simplegamegen.sudoku.model.*
import com.simplegamegen.sudoku.words.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PuzzleFactoryTest {
    @TempDir lateinit var directory: File

    @Test fun `all five games keep generating without a finite level list or recent duplicates`() = runBlocking {
        val store = TestCollection()
        val sudoku = mutableSetOf<String>()
        val crossword = mutableSetOf<String>()
        val search = mutableSetOf<String>()
        val mahjong = mutableSetOf<String>()
        val recentWords = ArrayDeque<String>()
        repeat(36) { index ->
            val factory = PuzzleFactory(store) // restarting the factory retains history
            val hangman = factory.hangman(0, WordDifficulty.EASY)
            assertFalse(hangman.answer in recentWords)
            recentWords.addLast(hangman.answer)
            if (recentWords.size >= Hangman.words(0, WordDifficulty.EASY).size) recentWords.removeFirst()
            if (index < 12) {
                assertTrue(sudoku.add(factory.sudoku(4, Difficulty.EASY, VariantType.CLASSIC).givens.joinToString()))
                assertTrue(crossword.add(WordSaveCodec.encode(WordProgress(factory.word(WordGame.CROSSWORD, 0, WordDifficulty.EASY)))))
                assertTrue(search.add(WordSaveCodec.encode(WordProgress(factory.word(WordGame.WORD_SEARCH, 0, WordDifficulty.EASY)))))
                assertTrue(mahjong.add(factory.mahjong(WordDifficulty.EASY).tiles.toString()))
            }
        }
        assertTrue(store.history.values.all { it.size <= 32 })
    }

    @Test fun `real datastore persists seeds histories and separate saves across reopen`() = runBlocking {
        val file = File(directory, "collection.preferences_pb")
        val firstJob = SupervisorJob()
        val first = CollectionStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob), produceFile = { file }))
        val seeds = (1..16).map { async { first.nextSeed() } }.awaitAll()
        assertEquals(16, seeds.distinct().size)
        assertTrue(first.remember("test", "fingerprint"))
        assertFalse(first.remember("test", "fingerprint"))
        first.save("HANGMAN", "hangman-save")
        first.save("MAHJONG", "mahjong-save")
        firstJob.cancelAndJoin()
        val secondJob = SupervisorJob()
        try {
            val second = CollectionStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob), produceFile = { file }))
            assertEquals(seeds.max() + 1, second.nextSeed())
            assertEquals(listOf("fingerprint"), second.recent("test"))
            assertEquals("hangman-save", second.load("HANGMAN"))
            assertEquals("mahjong-save", second.load("MAHJONG"))
            repeat(40) { assertTrue(second.remember("bounded", "p$it")) }
            assertEquals(32, second.recent("bounded").size)
        } finally { secondJob.cancelAndJoin() }
    }
}
