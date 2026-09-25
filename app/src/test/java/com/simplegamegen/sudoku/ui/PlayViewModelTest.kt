package com.simplegamegen.sudoku.ui

import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.arcade.Game2048
import com.simplegamegen.sudoku.arcade.Game2048Codec
import com.simplegamegen.sudoku.arcade.Slide
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.TestCollection
import com.simplegamegen.sudoku.duels.DotsCodec
import com.simplegamegen.sudoku.duels.DotsGame
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private val models = mutableListOf<PlayViewModel<*>>()
    @BeforeEach fun setup() { dispatcher = StandardTestDispatcher(); Dispatchers.setMain(dispatcher) }
    @AfterEach fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }

    private val codec2048 = codecOf(Game2048Codec::encode, Game2048Codec::decode)
    private val dotsCodec = codecOf(DotsCodec::encode, DotsCodec::decode)
    private fun solo(store: TestCollection) = PlayViewModel("PLAY_G2048", store, codec2048, worker = dispatcher).also { models += it }
    private fun duel(store: TestCollection) = PlayViewModel("PLAY_DOTS", store, dotsCodec, DotsComputer, dispatcher).also { models += it }

    @Test fun `new games save, moves record undo steps and saves restore`() = runTest {
        val store = TestCollection()
        val factory = PuzzleFactory(store, dispatcher)
        val vm = solo(store)
        runCurrent()
        vm.start { factory.custom("2048:1", { Game2048.start(it, 1) }) { it.seed.toString() } }
        runCurrent()
        val first = vm.state.value.game!!
        assertEquals(first, Game2048Codec.decode(store.saves["PLAY_G2048"]!!))
        val dir = Slide.entries.first { first.slide(it) != null }
        vm.play { it.slide(dir) }; runCurrent()
        assertTrue(vm.state.value.canUndo)
        val restored = solo(store); runCurrent()
        assertEquals(vm.state.value.game, restored.state.value.game)
        vm.undo(); runCurrent()
        assertEquals(first, vm.state.value.game)
        assertFalse(vm.state.value.canUndo)
        vm.play(record = false) { it.slide(dir) }; runCurrent()
        assertFalse(vm.state.value.canUndo, "unrecorded actions add no undo step")
        vm.play { null }
        assertNotNull(vm.state.value.game, "actions that don't apply change nothing")
    }

    @Test fun `the computer replies, and undo takes back your move with its reply`() = runTest {
        val store = TestCollection()
        val vm = duel(store)
        runCurrent()
        vm.start { DotsGame.start(3, 1) }; runCurrent()
        val before = vm.state.value.game!!
        vm.play { it.draw(0) }
        assertTrue(vm.state.value.thinking)
        advanceUntilIdle()
        val after = vm.state.value.game!!
        assertEquals(1, after.turn); assertEquals(2, after.drawn.size)
        assertFalse(vm.state.value.thinking)
        assertEquals(after, DotsCodec.decode(store.saves["PLAY_DOTS"]!!))
        vm.undo(); advanceUntilIdle()
        assertEquals(before, vm.state.value.game)
    }

    @Test fun `moves are ignored while the computer thinks and a new game cancels its reply`() = runTest {
        val store = TestCollection()
        val vm = duel(store)
        runCurrent()
        vm.start { DotsGame.start(5, 2) }; runCurrent()
        vm.play { it.draw(1) }
        val thinking = vm.state.value.game!!
        vm.play { it.draw(2) }
        assertEquals(thinking, vm.state.value.game, "no moves during the computer's turn")
        vm.start { DotsGame.start(6, 0) }
        advanceUntilIdle()
        val fresh = vm.state.value.game!!
        assertTrue(fresh.drawn.isEmpty() && fresh.setting == 0, "the old reply never lands on the new game")
    }

    @Test fun `corrupt saves are reported and storage errors can be retried`() = runTest {
        val store = TestCollection().also { it.saves["PLAY_G2048"] = "garbage" }
        val vm = solo(store)
        runCurrent()
        assertNull(vm.state.value.game); assertFalse(vm.state.value.busy); assertNotNull(vm.state.value.message)
        vm.start { Game2048.start(1, 0) }; runCurrent()
        store.failSave = true
        vm.play { g -> Slide.entries.firstNotNullOfOrNull { g.slide(it) } }; runCurrent()
        assertTrue(vm.state.value.message!!.contains("save"))
        store.failSave = false
        vm.retrySave(); runCurrent()
        assertEquals(vm.state.value.game, Game2048Codec.decode(store.saves["PLAY_G2048"]!!))
        assertEquals("Progress saved.", vm.state.value.message)
    }
}
