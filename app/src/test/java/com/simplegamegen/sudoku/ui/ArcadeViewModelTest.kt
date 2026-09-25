package com.simplegamegen.sudoku.ui

import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.*
import com.simplegamegen.sudoku.mahjong.*
import com.simplegamegen.sudoku.words.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArcadeViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private val models = mutableListOf<ArcadeViewModel>()
    @BeforeEach fun setup() { dispatcher = StandardTestDispatcher(); Dispatchers.setMain(dispatcher) }
    @AfterEach fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun model(game: ArcadeGame, store: TestCollection) = ArcadeViewModel(game, store, PuzzleFactory(store, dispatcher), dispatcher).also { models += it }

    @Test fun `hangman guesses hints next games and restoration are independent from mahjong`() = runTest {
        val store = TestCollection()
        val vm = model(ArcadeGame.HANGMAN, store)
        runCurrent(); vm.newGame(0, WordDifficulty.EASY); runCurrent()
        val first = vm.state.value.hangman!!.puzzle
        vm.guess(first.answer.first()); runCurrent()
        val writes = store.writes
        vm.guess(first.answer.first()); runCurrent()
        assertEquals(writes, store.writes)
        vm.hint(); runCurrent()
        assertEquals(1, vm.state.value.hangman!!.hints)
        val restored = model(ArcadeGame.HANGMAN, store)
        runCurrent()
        assertEquals(vm.state.value.hangman, restored.state.value.hangman)
        val mahjong = model(ArcadeGame.MAHJONG, store)
        runCurrent(); mahjong.newGame(0, WordDifficulty.EASY); runCurrent()
        assertEquals(restored.state.value.hangman, HangmanSaveCodec.decode(store.saves["HANGMAN"]!!))
        vm.newGame(0, WordDifficulty.EASY); runCurrent()
        assertNotEquals(first.answer, vm.state.value.hangman!!.puzzle.answer)
    }

    @Test fun `mahjong safe hints matching undo restart and restoration work`() = runTest {
        val store = TestCollection()
        val vm = model(ArcadeGame.MAHJONG, store)
        runCurrent(); vm.newGame(0, WordDifficulty.MEDIUM); runCurrent()
        val deal = vm.state.value.mahjong!!.deal
        vm.hint(); runCurrent()
        val hint = vm.state.value.highlighted!!
        assertTrue(MahjongRules.legal(deal.tiles, emptySet(), hint))
        vm.selectTile(hint.a); vm.selectTile(hint.b); runCurrent()
        assertEquals(2, vm.state.value.mahjong!!.removed.size)
        val restored = model(ArcadeGame.MAHJONG, store)
        runCurrent()
        assertEquals(vm.state.value.mahjong, restored.state.value.mahjong)
        vm.undo(); runCurrent()
        assertTrue(vm.state.value.mahjong!!.moves.isEmpty())
        vm.selectTile(hint.a); vm.selectTile(hint.b); runCurrent()
        vm.restart(); runCurrent()
        assertEquals(deal, vm.state.value.mahjong!!.deal)
        assertTrue(vm.state.value.mahjong!!.moves.isEmpty())
    }

    @Test fun `stale hint cannot highlight an older position`() = runTest {
        val store = TestCollection()
        val vm = model(ArcadeGame.MAHJONG, store)
        runCurrent(); vm.newGame(0, WordDifficulty.EASY); runCurrent()
        val pair = vm.state.value.mahjong!!.deal.solution.first()
        vm.hint()
        vm.selectTile(pair.a); vm.selectTile(pair.b)
        runCurrent()
        assertNull(vm.state.value.highlighted)
        assertFalse(vm.state.value.hintBusy)
        assertEquals(2, vm.state.value.mahjong!!.removed.size)
    }

    @Test fun `queued writes preserve newest board and errors can recover`() = runTest {
        val store = TestCollection()
        val vm = model(ArcadeGame.MAHJONG, store)
        runCurrent(); vm.newGame(0, WordDifficulty.EASY); runCurrent()
        store.saveGate = CompletableDeferred()
        vm.hint(); runCurrent()
        vm.newGame(0, WordDifficulty.HARD); runCurrent()
        val newest = vm.state.value.mahjong
        store.saveGate!!.complete(Unit); runCurrent()
        assertEquals(newest, MahjongSaveCodec.decode(store.saves["MAHJONG"]!!))
        store.failSave = true
        vm.hint(); runCurrent()
        assertTrue(vm.state.value.message!!.contains("save"))
        store.failSave = false
        vm.retrySave(); runCurrent()
        assertEquals(vm.state.value.mahjong, MahjongSaveCodec.decode(store.saves["MAHJONG"]!!))
    }

    @Test fun `corrupt saves are reported and do not crash startup`() = runTest {
        val store = TestCollection().also { it.saves["MAHJONG"] = "bad" }
        val vm = model(ArcadeGame.MAHJONG, store)
        runCurrent()
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.mahjong)
        assertNotNull(vm.state.value.message)
        vm.newGame(0, WordDifficulty.EASY); runCurrent()
        assertNotNull(vm.state.value.mahjong)
    }
}
