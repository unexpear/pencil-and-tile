package com.simplegamegen.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.ArcadeStore
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.logic.LogicKind
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.logic.LogicProgress
import com.simplegamegen.sudoku.logic.LogicRules
import com.simplegamegen.sudoku.logic.LogicSaveCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LogicState(
    val progress: LogicProgress? = null,
    val busy: Boolean = true,
    val selected: Int? = null,
    val notesMode: Boolean = false,
    /** Samurai only: which of the five grids is shown large. */
    val focus: Int = 2,
    /** Cells marked wrong by the last Check. */
    val checked: Set<Int> = emptySet(),
    val canUndo: Boolean = false,
    val message: String? = null,
)

/** Shared play logic for Samurai Sudoku, KenKen and Kakuro. */
class LogicViewModel(val kind: LogicKind, private val store: ArcadeStore, private val factory: PuzzleFactory) : ViewModel() {
    private val mutable = MutableStateFlow(LogicState())
    val state: StateFlow<LogicState> = mutable
    private val undoStack = ArrayDeque<LogicProgress>()
    private var saveJob: Job? = null
    private var saveEpoch = 0
    private val key = "LOGIC_${kind.name}"

    init {
        OpenBoards.watch(viewModelScope) { forget() }
        viewModelScope.launch {
            val epoch = saveEpoch
            try {
                val restored = store.load(key)?.let { checkNotNull(LogicSaveCodec.decode(it)).also { p -> check(p.puzzle.kind == kind) } }
                if (epoch != saveEpoch) return@launch
                mutable.value = LogicState(restored, busy = false)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = LogicState(busy = false, message = "Couldn't restore this puzzle. Start a new one to recover.") }
        }
    }

    /** Drops the puzzle without writing it. */
    suspend fun forget() {
        saveEpoch++
        drainChildren(viewModelScope)
        undoStack.clear()
        mutable.value = LogicState(busy = false)
    }

    fun newGame(level: LogicLevel) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val puzzle = factory.logic(kind, level)
                undoStack.clear()
                mutable.value = LogicState(LogicProgress(puzzle), busy = false)
                save()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, message = "Couldn't create a puzzle. Please try again.") }
        }
    }

    fun select(cell: Int) {
        val s = mutable.value; val p = s.progress ?: return
        if (s.busy || cell !in p.entries.indices || !p.puzzle.rules.open[cell]) return
        mutable.value = s.copy(selected = if (s.selected == cell) null else cell)
    }

    fun setFocus(grid: Int) {
        if (grid in LogicRules.SAMURAI_ORIGINS.indices) mutable.value = mutable.value.copy(focus = grid, selected = null)
    }

    fun toggleNotes() { mutable.value = mutable.value.copy(notesMode = !mutable.value.notesMode) }

    fun input(value: Int) {
        val s = mutable.value; val p = s.progress ?: return; val cell = s.selected ?: return
        if (s.busy) return
        apply(if (s.notesMode) p.toggleNote(cell, value) else p.enter(cell, value))
    }

    fun erase() {
        val s = mutable.value; val p = s.progress ?: return; val cell = s.selected ?: return
        if (!s.busy) apply(p.erase(cell))
    }

    fun undo() {
        val s = mutable.value
        if (s.busy || undoStack.isEmpty()) return
        mutable.value = s.copy(progress = undoStack.removeLast(), canUndo = undoStack.isNotEmpty(), checked = emptySet(), message = null)
        save()
    }

    fun hint() {
        val s = mutable.value; val p = s.progress ?: return
        if (s.busy) return
        val (next, cell) = p.hint(s.selected) ?: return
        apply(next, selected = cell, message = "One square revealed.")
    }

    fun check() {
        val s = mutable.value; val p = s.progress ?: return
        val wrong = p.mistakes()
        mutable.value = s.copy(checked = wrong, message = when {
            p.complete -> "Everything is correct."
            wrong.isEmpty() -> "No mistakes so far."
            else -> (if (wrong.size == 1) "1 square is wrong, marked in red." else "${wrong.size} squares are wrong, marked in red.")
        })
    }

    fun retrySave() = save(confirm = true)

    private fun apply(next: LogicProgress, selected: Int? = mutable.value.selected, message: String? = null) {
        val s = mutable.value; val p = s.progress ?: return
        if (next == p) return
        undoStack.addLast(p)
        if (undoStack.size > 200) undoStack.removeFirst()
        mutable.value = s.copy(progress = next, selected = selected, canUndo = true, checked = emptySet(),
            message = if (next.complete) "Solved! Every square is correct." else message)
        save()
    }

    private fun save(confirm: Boolean = false) {
        val epoch = saveEpoch
        val encoded = mutable.value.progress?.let(LogicSaveCodec::encode) ?: return
        val previous = saveJob
        saveJob = viewModelScope.launch {
            previous?.join()
            if (epoch != saveEpoch) return@launch
            try {
                store.save(key, encoded)
                if (confirm || mutable.value.message == SAVE_FAILED) mutable.value = mutable.value.copy(message = "Progress saved.")
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = mutable.value.copy(message = SAVE_FAILED) }
        }
    }

    private companion object { const val SAVE_FAILED = "Couldn't save progress. Tap Save to retry." }
}
