package com.simplegamegen.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.ArcadeStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/** Text save format for one game's state. */
interface GameCodec<S> {
    fun encode(state: S): String
    fun decode(text: String): S?
}

/** Plays the computer's side. [move] runs off the main thread. */
interface ComputerPlayer<S> {
    fun needsMove(state: S): Boolean
    fun move(state: S): S
    val pauseMs: Long get() = 450
}

data class PlaySession<S>(
    val game: S? = null,
    val busy: Boolean = true,
    val thinking: Boolean = false,
    val canUndo: Boolean = false,
    val message: String? = null,
)

/**
 * Save, undo, messages and computer turns for games whose rules live in an
 * immutable engine state. Each human action is one undo step; the computer's
 * replies are undone with it.
 */
class PlayViewModel<S : Any>(
    private val key: String,
    private val store: ArcadeStore,
    private val codec: GameCodec<S>,
    private val computer: ComputerPlayer<S>? = null,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutable = MutableStateFlow(PlaySession<S>())
    val state: StateFlow<PlaySession<S>> = mutable
    private val undoStack = ArrayDeque<S>()
    private var revision = 0L
    private var computerJob: Job? = null
    private var saveJob: Job? = null
    private var saveEpoch = 0

    init {
        OpenBoards.watch(viewModelScope) { forget() }
        viewModelScope.launch {
            val epoch = saveEpoch
            try {
                val restored = store.load(key)?.let { checkNotNull(codec.decode(it)) }
                if (epoch != saveEpoch) return@launch
                mutable.value = PlaySession(restored, busy = false)
                computerTurn()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = PlaySession(busy = false, message = "Couldn't restore this game. Start a new one to recover.") }
        }
    }

    /** Drops the board without writing it. */
    suspend fun forget() {
        saveEpoch++
        revision++
        drainChildren(viewModelScope)
        undoStack.clear()
        mutable.value = PlaySession(busy = false)
    }

    /** Replaces the game with a freshly created one. */
    fun start(create: suspend () -> S) {
        if (mutable.value.busy) return
        invalidate()
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val game = create()
                undoStack.clear()
                mutable.value = PlaySession(game, busy = false)
                save()
                computerTurn()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, message = "Couldn't create a game. Please try again.") }
        }
    }

    /**
     * Applies a human action. Returning null or the same state means the action
     * didn't apply. [record] adds an undo step; [persist] writes the save.
     */
    fun play(message: String? = null, record: Boolean = true, persist: Boolean = true, transform: (S) -> S?) {
        val s = mutable.value
        val game = s.game ?: return
        if (s.busy || s.thinking) return
        val next = transform(game) ?: return
        if (next == game) return
        invalidate()
        if (record) { undoStack.addLast(game); if (undoStack.size > 300) undoStack.removeFirst() }
        mutable.value = s.copy(game = next, canUndo = undoStack.isNotEmpty(), message = message)
        if (persist) save()
        computerTurn()
    }

    fun undo() {
        val s = mutable.value
        if (s.busy || undoStack.isEmpty()) return
        invalidate()
        mutable.value = s.copy(game = undoStack.removeLast(), canUndo = undoStack.isNotEmpty(), thinking = false, message = null)
        save()
        computerTurn()
    }

    fun say(message: String?) { mutable.value = mutable.value.copy(message = message) }
    fun retrySave() = save(confirm = true)
    /** Writes the current state now (for real-time games that skip saving on every tick). */
    fun flush() = save()

    private fun invalidate() {
        revision++
        computerJob?.cancel()
        if (mutable.value.thinking) mutable.value = mutable.value.copy(thinking = false)
    }

    private fun computerTurn() {
        val ai = computer ?: return
        val game = mutable.value.game ?: return
        if (!ai.needsMove(game)) return
        val version = revision
        mutable.value = mutable.value.copy(thinking = true)
        computerJob = viewModelScope.launch {
            try {
                var current = game
                while (ai.needsMove(current)) {
                    delay(ai.pauseMs)
                    val snapshot = current
                    current = runInterruptible(worker) { ai.move(snapshot) }
                    if (version != revision) return@launch
                    mutable.value = mutable.value.copy(game = current)
                    save()
                }
                mutable.value = mutable.value.copy(thinking = false)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                if (version == revision) mutable.value = mutable.value.copy(thinking = false, message = "The computer couldn't move. Undo to try again.")
            }
        }
    }

    private fun save(confirm: Boolean = false) {
        val epoch = saveEpoch
        val encoded = mutable.value.game?.let(codec::encode) ?: return
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
