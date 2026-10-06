package com.simplegamegen.sudoku.ui

import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.GameStore
import com.simplegamegen.sudoku.data.PlaySnapshot
import com.simplegamegen.sudoku.data.StatsStore
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.*
import com.simplegamegen.sudoku.solver.Hint
import com.simplegamegen.sudoku.solver.Technique
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private lateinit var dispatcher: TestDispatcher
    private val models = mutableListOf<GameViewModel>()
    private val p = Generator.generateClassic(4, Difficulty.EASY, 1L)
    private fun snapshot(puzzle: Puzzle = p) = PlaySnapshot(
        puzzle, puzzle.givens.toList(), List(puzzle.size * puzzle.size) { emptySet() }, 0, 0,
    )
    private fun track(vm: GameViewModel) = vm.also { models.add(it) }
    private fun model(store: FakeStore) = track(GameViewModel(store, dispatcher, { _, _, _, _ -> p }))

    @BeforeEach
    fun setup() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun cleanup() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    @Test
    fun `slow startup save check does not overwrite a freshly generated board`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore().also { it.hasSaveGate = gate }
        val vm = model(store)
        runCurrent()
        vm.newGame(4, Difficulty.EASY, VariantType.CLASSIC)
        runCurrent()
        assertTrue(vm.state.value.hasGame)
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.state.value.hasGame)
        assertEquals(p.givens.toList(), vm.state.value.current)
        assertEquals(4, vm.state.value.size)
    }

    @Test
    fun `startup preserves save and does not count a new game`() = runTest {
        val original = snapshot().copy(elapsedSeconds = 100)
        val store = FakeStore(original)
        val vm = model(store)
        runCurrent()
        assertSame(original, store.saved)
        assertEquals(0, store.starts)
        assertEquals(0, store.saves)
        assertTrue(vm.state.value.hasSave)
        assertFalse(vm.state.value.hasGame)
        vm.loadGame()
        runCurrent()
        assertEquals(100, vm.state.value.elapsedSeconds)
        assertEquals(original.current, vm.state.value.current)
    }

    @Test
    fun `slow earlier generation cannot replace a newer game`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore()
        var calls = 0
        val vm = track(GameViewModel(store, dispatcher, { _, _, _, _ ->
            if (++calls == 1) {
                withContext(NonCancellable) { gate.await() }
                p.copy(seed = 100)
            } else p.copy(seed = 200)
        }))
        vm.newGame(4, Difficulty.EASY, VariantType.CLASSIC)
        runCurrent()
        vm.newGame(4, Difficulty.EASY, VariantType.CLASSIC)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(200L, vm.state.value.seed)
        assertEquals(200L, store.saved!!.puzzle.seed)
        assertEquals(1, store.starts)
    }

    @Test
    fun `generation failure keeps the previous game playable and saved`() = runTest {
        val store = FakeStore(snapshot())
        val vm = track(GameViewModel(store, dispatcher, { _, _, _, _ -> error("failed") }))
        vm.loadGame()
        runCurrent()
        vm.newGame(4, Difficulty.EASY, VariantType.THERMO)
        runCurrent()
        assertFalse(vm.state.value.generating)
        assertTrue(vm.state.value.hasGame)
        assertEquals(p.givens.toList(), vm.state.value.current)
        assertNotNull(vm.state.value.message)
        assertEquals(p, store.saved!!.puzzle)
        assertEquals(0, store.starts)
    }

    @Test
    fun `stale hint is discarded after a move`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore(snapshot())
        val cells = p.givens.indices.filter { p.givens[it] == 0 }
        val vm = track(GameViewModel(store, dispatcher, findHint = { _, _, _ ->
            withContext(NonCancellable) { gate.await() }
            Hint(cells[0], p.solution[cells[0]], Technique.REVEAL, "old hint")
        }))
        vm.loadGame()
        runCurrent()
        vm.requestHint()
        runCurrent()
        vm.selectCell(cells[1])
        vm.inputNumber(p.solution[cells[1]])
        gate.complete(Unit)
        runCurrent()
        assertEquals(0, vm.state.value.current[cells[0]])
        assertEquals(0, vm.state.value.hintsUsed)
        assertFalse(vm.state.value.hintBusy)
    }

    @Test
    fun `hint from a larger game cannot index into a new smaller game`() = runTest {
        val large = Generator.generateClassic(9, Difficulty.EASY, 1L)
        val gate = CompletableDeferred<Unit>()
        val store = FakeStore(snapshot(large))
        val vm = track(GameViewModel(store, dispatcher, { _, _, _, _ -> p }, { _, _, _ ->
            withContext(NonCancellable) { gate.await() }
            Hint(80, 1, Technique.REVEAL, "old hint")
        }))
        vm.loadGame()
        runCurrent()
        vm.requestHint()
        runCurrent()
        vm.newGame(4, Difficulty.EASY, VariantType.CLASSIC)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(16, vm.state.value.current.size)
        assertEquals(p.givens.toList(), vm.state.value.current)
        assertEquals(0, vm.state.value.hintsUsed)
    }

    @Test
    fun `jigsaw retains notes in the same rectangle but different region`() = runTest {
        val jigsaw = Generator.generateJigsaw(4, Difficulty.HARD, 3L)
        val vm = model(FakeStore(snapshot(jigsaw)))
        vm.loadGame()
        runCurrent()
        vm.selectCell(15)
        vm.toggleNotesMode()
        vm.inputNumber(1)
        vm.toggleNotesMode()
        vm.selectCell(10)
        vm.inputNumber(1)
        assertEquals(setOf(1), vm.state.value.notes[15])
        vm.undo()
        assertEquals(0, vm.state.value.current[10])
        assertEquals(setOf(1), vm.state.value.notes[15])
    }

    @Test
    fun `timer checkpoints without moves and stops while not playing`() = runTest {
        val store = FakeStore(snapshot())
        val vm = model(store)
        vm.loadGame()
        runCurrent()
        vm.setPlaying(true)
        advanceTimeBy(12_000)
        runCurrent()
        assertEquals(12, vm.state.value.elapsedSeconds)
        assertEquals(10, store.saved!!.elapsedSeconds)
        vm.setPlaying(false)
        runCurrent()
        assertEquals(12, store.saved!!.elapsedSeconds)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(12, vm.state.value.elapsedSeconds)
        vm.setPlaying(true)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(13, vm.state.value.elapsedSeconds)
        vm.setPlaying(false)
        runCurrent()
    }

    @Test
    fun `writes complete in move order even when the first save is slow`() = runTest {
        val store = FakeStore(snapshot())
        val vm = model(store)
        vm.loadGame()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        store.saveGate = gate
        val cells = p.givens.indices.filter { p.givens[it] == 0 }
        vm.selectCell(cells[0])
        vm.inputNumber(p.solution[cells[0]])
        runCurrent()
        vm.selectCell(cells[1])
        vm.inputNumber(p.solution[cells[1]])
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(vm.state.value.current, store.saved!!.current)
    }

    @Test
    fun `winning clear cannot be overtaken by an older save or clear a newer game`() = runTest {
        val almostDone = p.copy(givens = p.solution.copyOf().also { it[0] = 0; it[1] = 0 })
        val store = FakeStore(snapshot(almostDone))
        val vm = model(store)
        vm.loadGame()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        store.saveGate = gate
        vm.selectCell(0)
        vm.inputNumber(p.solution[0])
        runCurrent()
        vm.selectCell(1)
        vm.inputNumber(p.solution[1])
        assertTrue(vm.state.value.won)
        vm.newGame(4, Difficulty.EASY, VariantType.CLASSIC)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, store.wins)
        assertEquals(p.givens.toList(), store.saved!!.current)
        assertFalse(vm.state.value.won)
    }

    @Test
    fun `corrupt current board is cleared without crashing`() = runTest {
        val store = FakeStore(snapshot().copy(current = List(16) { 10 }))
        val vm = model(store)
        vm.loadGame()
        runCurrent()
        assertNull(store.saved)
        assertFalse(vm.state.value.hasGame)
        assertFalse(vm.state.value.hasSave)
        assertFalse(vm.state.value.generating)
    }

    @Test
    fun `save failure is visible and a subsequent move can save again`() = runTest {
        val store = FakeStore(snapshot())
        val vm = model(store)
        vm.loadGame()
        runCurrent()
        store.failSave = true
        val cell = p.givens.indexOfFirst { it == 0 }
        vm.selectCell(cell)
        vm.inputNumber(p.solution[cell])
        runCurrent()
        assertNotNull(vm.state.value.message)
        store.failSave = false
        vm.erase()
        runCurrent()
        assertEquals(vm.state.value.current, store.saved!!.current)
    }

    @Test fun `forget drops the board and does not write it back`() = runTest {
        val store = FakeStore()
        val vm = model(store)
        runCurrent()
        vm.newGame(4, Difficulty.EASY, VariantType.CLASSIC)
        runCurrent()
        assertTrue(vm.state.value.hasGame)
        assertNotNull(store.saved)
        vm.forget()
        assertFalse(vm.state.value.hasGame)
        assertFalse(vm.state.value.hasSave)
        assertEquals(4, vm.state.value.size)
        store.clear()
        advanceTimeBy(6_000)
        runCurrent()
        assertNull(store.saved)
        assertFalse(vm.state.value.hasGame)
    }

    private class FakeStore(var saved: PlaySnapshot? = null) : GameStore {
        override val statRows = flowOf(emptyList<StatsStore.StatRow>())
        var starts = 0
        var wins = 0
        var saves = 0
        var saveGate: CompletableDeferred<Unit>? = null
        var hasSaveGate: CompletableDeferred<Unit>? = null
        var failSave = false
        override suspend fun save(snapshot: PlaySnapshot) {
            saveGate?.await()
            if (failSave) error("disk unavailable")
            saved = snapshot
            saves++
        }
        override suspend fun load() = saved
        override suspend fun hasSave(): Boolean {
            hasSaveGate?.await()
            return saved != null
        }
        override suspend fun clear() { saved = null }
        override suspend fun recordStart(variant: VariantType, size: Int, difficulty: Difficulty) { starts++ }
        override suspend fun recordWin(variant: VariantType, size: Int, difficulty: Difficulty, elapsedSec: Long) { wins++ }
    }
}
