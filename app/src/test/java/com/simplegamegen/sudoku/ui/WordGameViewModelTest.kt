package com.simplegamegen.sudoku.ui

import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.WordGameStore
import com.simplegamegen.sudoku.words.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class WordGameViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private val models = mutableListOf<WordGameViewModel>()
    @BeforeEach fun setup() { dispatcher = StandardTestDispatcher(); Dispatchers.setMain(dispatcher) }
    @AfterEach fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun model(game: WordGame, store: FakeStore) = WordGameViewModel(game, store, dispatcher).also { models += it }

    private class FakeStore : WordGameStore {
        val saved = mutableMapOf<WordGame, WordProgress>()
        val writes = mutableListOf<WordProgress>()
        var gate: CompletableDeferred<Unit>? = null
        var failSave = false
        var failLoad = false
        override suspend fun load(game: WordGame): WordProgress? {
            if (failLoad) error("unreadable")
            return saved[game]
        }
        override suspend fun save(progress: WordProgress) {
            gate?.await()
            if (failSave) error("storage failed")
            saved[progress.puzzle.game] = progress
            writes += progress
        }
    }

    @Test fun `startup restores both modes independently without replacing saves`() = runTest {
        val store = FakeStore()
        val crossword = WordProgress(WordPuzzles.crossword(12))
        val search = WordProgress(WordPuzzles.wordSearch(9, 2))
        store.saved[WordGame.CROSSWORD] = crossword
        store.saved[WordGame.WORD_SEARCH] = search
        val a = model(WordGame.CROSSWORD, store)
        val b = model(WordGame.WORD_SEARCH, store)
        runCurrent()
        assertEquals(crossword, a.state.value.progress)
        assertEquals(search, b.state.value.progress)
        assertTrue(store.writes.isEmpty())
        a.newGame()
        runCurrent()
        assertEquals(search, store.saved[WordGame.WORD_SEARCH])
    }

    @Test fun `crossword answer check hint and completion persist`() = runTest {
        val store = FakeStore()
        val vm = model(WordGame.CROSSWORD, store)
        runCurrent(); vm.newGame(); runCurrent()
        val puzzle = vm.state.value.progress!!.puzzle
        vm.checkAnswer()
        assertTrue(vm.state.value.message!!.contains("empty"))
        vm.hint(); runCurrent()
        assertEquals(1, store.saved[WordGame.CROSSWORD]!!.hints)
        puzzle.entries.forEachIndexed { index, entry -> vm.selectEntry(index); vm.answer(entry.answer) }
        runCurrent()
        assertTrue(vm.state.value.progress!!.complete)
        assertTrue(store.saved[WordGame.CROSSWORD]!!.complete)
        val completed = vm.state.value.progress
        vm.answer("WRONG")
        assertEquals(completed, vm.state.value.progress)
    }

    @Test fun `word search hint endpoints duplicates and completion work`() = runTest {
        val store = FakeStore()
        val vm = model(WordGame.WORD_SEARCH, store)
        runCurrent(); vm.newGame(1); runCurrent()
        val puzzle = vm.state.value.progress!!.puzzle
        vm.hint()
        assertEquals(puzzle.entries.first().cells.first(), vm.state.value.startCell)
        vm.tapCell(puzzle.entries.first().cells.last())
        assertEquals(1, vm.state.value.progress!!.found.size)
        for (entry in puzzle.entries) { vm.tapCell(entry.cells.last()); vm.tapCell(entry.cells.first()) }
        runCurrent()
        assertTrue(vm.state.value.progress!!.complete)
        assertTrue(store.saved[WordGame.WORD_SEARCH]!!.complete)
    }

    @Test fun `queued old saves cannot replace newer answers or a new game`() = runTest {
        val store = FakeStore()
        val vm = model(WordGame.CROSSWORD, store)
        runCurrent(); vm.newGame(); runCurrent()
        store.gate = CompletableDeferred()
        vm.hint(); runCurrent()
        vm.newGame(); runCurrent()
        val latest = vm.state.value.progress
        store.gate!!.complete(Unit); runCurrent()
        assertEquals(latest, store.saved[WordGame.CROSSWORD])
    }

    @Test fun `load and save failures are visible and retry can recover`() = runTest {
        val store = FakeStore().also { it.failLoad = true }
        val vm = model(WordGame.CROSSWORD, store)
        runCurrent()
        assertFalse(vm.state.value.busy)
        assertNotNull(vm.state.value.message)
        store.failSave = true
        vm.newGame(); runCurrent()
        assertTrue(vm.state.value.message!!.contains("save"))
        assertNotNull(vm.state.value.progress)
        store.failSave = false
        vm.retrySave(); runCurrent()
        assertEquals(vm.state.value.progress, store.saved[WordGame.CROSSWORD])
    }

    @Test fun `selected difficulty and theme survive generation and restoration`() = runTest {
        for (game in WordGame.entries) {
            val store = FakeStore()
            val vm = model(game, store)
            runCurrent()
            vm.newGame(theme = 11, difficulty = WordDifficulty.EXPERT)
            runCurrent()
            val progress = vm.state.value.progress!!
            assertEquals(WordDifficulty.EXPERT, progress.puzzle.difficulty)
            assertEquals(12, progress.puzzle.size)
            if (game == WordGame.WORD_SEARCH) assertEquals("Science", progress.puzzle.title)
            val restored = model(game, store)
            runCurrent()
            assertEquals(progress, restored.state.value.progress)
            vm.newGame(theme = 4, difficulty = WordDifficulty.EASY)
            runCurrent()
            assertEquals(WordDifficulty.EASY, store.saved[game]!!.puzzle.difficulty)
            assertEquals(WordDifficulty.EXPERT, restored.state.value.progress!!.puzzle.difficulty)
        }
    }
}
