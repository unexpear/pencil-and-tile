package com.simplegamegen.sudoku.ui

import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.*
import com.simplegamegen.sudoku.logic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogicViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private val models = mutableListOf<LogicViewModel>()
    @BeforeEach fun setup() { dispatcher = StandardTestDispatcher(); Dispatchers.setMain(dispatcher) }
    @AfterEach fun cleanup() { models.forEach { it.viewModelScope.cancel() }; Dispatchers.resetMain() }
    private fun model(kind: LogicKind, store: TestCollection) = LogicViewModel(kind, store, PuzzleFactory(store, dispatcher)).also { models += it }
    private fun firstOpen(p: LogicProgress) = p.entries.indices.first { p.editable(it) }

    @Test fun `each kind generates a verified puzzle and saves separately`() = runTest {
        val store = TestCollection()
        for (kind in LogicKind.entries) {
            val vm = model(kind, store)
            runCurrent(); vm.newGame(LogicLevel.EASY); runCurrent()
            val p = vm.state.value.progress!!
            assertEquals(kind, p.puzzle.kind)
            assertTrue(LogicVerifier.verify(p.puzzle))
            assertEquals(p, LogicSaveCodec.decode(store.saves["LOGIC_${kind.name}"]!!))
        }
        assertEquals(LogicKind.entries.size, store.saves.keys.count { it.startsWith("LOGIC_") })
    }

    @Test fun `entry, notes, undo, check, hint and restore`() = runTest {
        val store = TestCollection()
        val vm = model(LogicKind.KENKEN, store)
        runCurrent(); vm.newGame(LogicLevel.MEDIUM); runCurrent()
        val p = vm.state.value.progress!!
        val cell = firstOpen(p); val right = p.puzzle.solution[cell]; val wrong = right % p.puzzle.rules.maxDigit + 1
        vm.select(cell); vm.toggleNotes(); vm.input(2); vm.toggleNotes(); runCurrent()
        assertEquals(setOf(2), vm.state.value.progress!!.notes[cell])
        vm.input(wrong); runCurrent()
        vm.check()
        assertEquals(setOf(cell), vm.state.value.checked)
        vm.undo(); runCurrent()
        assertEquals(0, vm.state.value.progress!!.entries[cell])
        assertTrue(vm.state.value.checked.isEmpty())
        vm.hint(); runCurrent()
        assertEquals(right, vm.state.value.progress!!.entries[cell])
        assertEquals(1, vm.state.value.progress!!.hints)
        val restored = model(LogicKind.KENKEN, store); runCurrent()
        assertEquals(vm.state.value.progress, restored.state.value.progress)
    }

    @Test fun `hints finish the puzzle and lock it`() = runTest {
        val store = TestCollection()
        val vm = model(LogicKind.KAKURO, store)
        runCurrent(); vm.newGame(LogicLevel.EASY); runCurrent()
        repeat(200) { if (vm.state.value.progress!!.complete) return@repeat; vm.hint(); runCurrent() }
        val done = vm.state.value.progress!!
        assertTrue(done.complete)
        assertTrue(vm.state.value.message!!.startsWith("Solved"))
        vm.select(firstOpen(done)); vm.erase(); runCurrent()
        assertEquals(done, vm.state.value.progress)
    }

    @Test fun `samurai focus switches grids and clears selection`() = runTest {
        val store = TestCollection()
        val vm = model(LogicKind.SAMURAI, store)
        runCurrent(); vm.newGame(LogicLevel.EASY); runCurrent()
        vm.select(firstOpen(vm.state.value.progress!!))
        vm.setFocus(4)
        assertEquals(4, vm.state.value.focus); assertNull(vm.state.value.selected)
        vm.setFocus(9)
        assertEquals(4, vm.state.value.focus)
        vm.select(0); assertEquals(0, vm.state.value.selected) // top-left corner is part of a grid
        vm.select(9); assertEquals(0, vm.state.value.selected, "taps on the gap between the top grids are ignored")
    }

    @Test fun `corrupt, wrong-kind saves and storage errors are recoverable`() = runTest {
        val kenkenSave = run {
            val p = LogicGenerator.kenken(LogicLevel.EASY, 3)
            LogicSaveCodec.encode(LogicProgress(p))
        }
        val store = TestCollection().also { it.saves["LOGIC_KAKURO"] = kenkenSave; it.saves["LOGIC_SAMURAI"] = "junk" }
        val kakuro = model(LogicKind.KAKURO, store); val samurai = model(LogicKind.SAMURAI, store)
        runCurrent()
        assertNull(kakuro.state.value.progress); assertNotNull(kakuro.state.value.message)
        assertNull(samurai.state.value.progress); assertFalse(samurai.state.value.busy)
        kakuro.newGame(LogicLevel.EASY); runCurrent()
        store.failSave = true
        kakuro.hint(); runCurrent()
        assertTrue(kakuro.state.value.message!!.contains("save"))
        store.failSave = false
        kakuro.retrySave(); runCurrent()
        assertEquals(kakuro.state.value.progress, LogicSaveCodec.decode(store.saves["LOGIC_KAKURO"]!!))
    }
}
