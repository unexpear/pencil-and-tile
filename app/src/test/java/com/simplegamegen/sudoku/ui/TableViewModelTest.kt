package com.simplegamegen.sudoku.ui

import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.TestCollection
import com.simplegamegen.sudoku.tabletop.*
import com.simplegamegen.sudoku.tabletop.Result as GameResult
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TableViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private val models = mutableListOf<TableViewModel>()
    @BeforeEach fun setup() { dispatcher = StandardTestDispatcher(); Dispatchers.setMain(dispatcher) }
    @AfterEach fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun model(game: TableGame, store: TestCollection) = TableViewModel(game, store, PuzzleFactory(store, dispatcher), dispatcher).also { models += it }

    @Test fun `all new games save independently and restore exact progress`() = runTest {
        val store = TestCollection()
        for (game in TableGame.entries) {
            val vm = model(game, store); runCurrent(); vm.newGame(0); runCurrent()
            val m = vm.state.value.match!!
            vm.play(if (game == TableGame.MINES) Move(Op.REVEAL, 0) else m.state.legalMoves().first()); runCurrent()
            assertFalse(vm.state.value.busy); assertFalse(vm.state.value.thinking)
            val restored = model(game, store); runCurrent()
            assertEquals(vm.state.value.match, restored.state.value.match)
        }
        assertEquals(5, store.saves.size)
    }
    @Test fun `undo rolls back human and computer replies while restart keeps the same deal`() = runTest {
        val vm = model(TableGame.CHECKERS, TestCollection()); runCurrent(); vm.newGame(1); runCurrent()
        val original = vm.state.value.match!!
        vm.play(original.state.legalMoves().first()); runCurrent()
        assertTrue(vm.state.value.match!!.moves.size >= 2)
        assertEquals(1, vm.state.value.match!!.state.turn)
        vm.undo(); runCurrent(); assertEquals(original, vm.state.value.match)
        vm.play(original.state.legalMoves().last()); runCurrent()
        vm.restart(); runCurrent(); assertEquals(original, vm.state.value.match)
    }
    @Test fun `saved computer turn resumes after process recreation`() = runTest {
        val pending = TableMatch.create(TableGame.REVERSI, 1, 44).let { it.play(it.state.legalMoves().first())!! }
        assertEquals(-1, pending.state.turn)
        val store = TestCollection().also { it.saves["TABLE_REVERSI"] = TableSaveCodec.encode(pending) }
        val vm = model(TableGame.REVERSI, store); runCurrent()
        assertEquals(1, vm.state.value.match!!.state.turn)
        assertTrue(vm.state.value.match!!.moves.size > pending.moves.size)
        assertFalse(vm.state.value.thinking)
    }
    @Test fun `new game cancels stale hint and pending computer work`() = runTest {
        val vm = model(TableGame.REVERSI, TestCollection()); runCurrent(); vm.newGame(1); runCurrent()
        vm.hint(); vm.newGame(2); runCurrent()
        assertNull(vm.state.value.hint); assertFalse(vm.state.value.thinking)
        assertEquals(2, vm.state.value.match!!.setting)
        val move = vm.state.value.match!!.state.legalMoves().first()
        vm.play(move); vm.newGame(3); runCurrent()
        assertEquals(3, vm.state.value.match!!.setting)
        assertTrue(vm.state.value.match!!.moves.isEmpty())
        assertEquals(1, vm.state.value.match!!.state.turn)
    }
    @Test fun `queued writes preserve latest game and failed save can retry`() = runTest {
        val store = TestCollection()
        val vm = model(TableGame.SOLITAIRE, store); runCurrent(); vm.newGame(0); runCurrent()
        store.saveGate = CompletableDeferred()
        vm.play(Move(Op.DRAW)); runCurrent()
        vm.newGame(1); runCurrent()
        store.saveGate!!.complete(Unit); runCurrent()
        assertEquals(vm.state.value.match, TableSaveCodec.decode(store.saves["TABLE_SOLITAIRE"]!!))
        store.failSave = true; vm.play(Move(Op.DRAW)); runCurrent()
        assertTrue(vm.state.value.message!!.contains("save"))
        store.failSave = false; vm.save(true); runCurrent()
        assertEquals("Progress saved.", vm.state.value.message)
        assertEquals(vm.state.value.match, TableSaveCodec.decode(store.saves["TABLE_SOLITAIRE"]!!))
    }
    @Test fun `corrupted or wrong mode save is reported without overwriting it`() = runTest {
        for (raw in listOf("broken", TableSaveCodec.encode(TableMatch.create(TableGame.CHECKERS, 0, 0)))) {
            val store = TestCollection().also { it.saves["TABLE_MINES"] = raw }
            val vm = model(TableGame.MINES, store); runCurrent()
            assertNull(vm.state.value.match); assertFalse(vm.state.value.busy)
            assertTrue(vm.state.value.message!!.contains("restore")); assertEquals(raw, store.saves["TABLE_MINES"])
            vm.newGame(0); runCurrent(); assertNotNull(vm.state.value.match)
        }
    }
    @Test fun `mines hints only explain deductions and undo restores covered state`() = runTest {
        val vm = model(TableGame.MINES, TestCollection()); runCurrent(); vm.newGame(0); runCurrent()
        val initial = vm.state.value.match
        vm.hint(); runCurrent(); assertTrue(vm.state.value.message!!.contains("opening")); assertEquals(initial, vm.state.value.match)
        vm.play(Move(Op.REVEAL, 0)); runCurrent()
        val played = vm.state.value.match
        vm.hint(); runCurrent(); assertEquals(played, vm.state.value.match)
        vm.undo(); runCurrent(); assertEquals(initial, vm.state.value.match)
    }
    @Test fun `factory keeps producing shuffled layouts while board games retain standard openings`() = runTest {
        val store = TestCollection(); val factory = PuzzleFactory(store, dispatcher)
        for (game in listOf(TableGame.SOLITAIRE, TableGame.MINES, TableGame.DOMINOES)) {
            val layouts = mutableSetOf<List<Int>>()
            repeat(35) { layouts += factory.table(game, 0).layout }
            assertEquals(35, layouts.size)
        }
        val first = factory.table(TableGame.CHECKERS, 0); val second = factory.table(TableGame.CHECKERS, 0)
        assertEquals(first.state, second.state); assertNotEquals(first.seed, second.seed)
        assertTrue(store.history.values.all { it.size <= 32 })
    }
}
