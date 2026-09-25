package com.simplegamegen.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.ArcadeStore
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.mahjong.*
import com.simplegamegen.sudoku.words.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ArcadeGame(val title: String) { HANGMAN("Hangman"), MAHJONG("Mahjong Solitaire") }
data class ArcadeState(
    val hangman: HangmanProgress? = null,
    val mahjong: MahjongProgress? = null,
    val busy: Boolean = true,
    val hintBusy: Boolean = false,
    val selected: Int? = null,
    val highlighted: TilePair? = null,
    val message: String? = null,
)

class ArcadeViewModel(val game: ArcadeGame, private val store: ArcadeStore, private val factory: PuzzleFactory,
    private val worker: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
    private val mutable = MutableStateFlow(ArcadeState())
    val state: StateFlow<ArcadeState> = mutable
    private var saveJob: Job? = null
    private var hintJob: Job? = null
    private var revision = 0L
    init {
        viewModelScope.launch {
            try {
                val encoded = store.load(game.name)
                val hangman = if (game == ArcadeGame.HANGMAN && encoded != null) checkNotNull(HangmanSaveCodec.decode(encoded)) else null
                val mahjong = if (game == ArcadeGame.MAHJONG && encoded != null) checkNotNull(MahjongSaveCodec.decode(encoded)) else null
                mutable.value = ArcadeState(hangman, mahjong, busy = false)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = ArcadeState(busy = false, message = "Couldn't restore this game. You can start a new one.") }
        }
    }

    fun newGame(theme: Int, difficulty: WordDifficulty) {
        if (mutable.value.busy || theme !in WordPuzzles.themes.indices) return
        invalidateHint()
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val fresh = if (game == ArcadeGame.HANGMAN) ArcadeState(hangman = HangmanProgress(factory.hangman(theme, difficulty)), busy = false)
                    else ArcadeState(mahjong = MahjongProgress(factory.mahjong(difficulty)), busy = false)
                mutable.value = fresh
                save()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, message = "Couldn't create a verified game. Please try again.") }
        }
    }

    fun guess(letter: Char) {
        val s = mutable.value
        val p = s.hangman ?: return
        if (s.busy) return
        val next = p.guess(letter)
        if (next != p) { mutable.value = s.copy(hangman = next, message = null); save() }
    }

    fun selectTile(id: Int) {
        val s = mutable.value
        val p = s.mahjong ?: return
        if (s.busy || p.complete || !MahjongRules.free(p.deal.tiles, p.removed, id)) return
        val selected = s.selected
        invalidateHint()
        if (selected == null || selected == id) {
            mutable.value = mutable.value.copy(selected = if (selected == id) null else id, message = null)
            return
        }
        val next = p.play(TilePair(selected, id))
        mutable.value = mutable.value.copy(mahjong = next, selected = if (next == p) id else null,
            message = if (next == p) "Choose two matching free tiles." else null)
        if (next != p) save()
    }

    fun undo() {
        val p = mutable.value.mahjong ?: return
        if (mutable.value.busy || p.moves.isEmpty()) return
        invalidateHint()
        mutable.value = mutable.value.copy(mahjong = p.undo(), selected = null, message = null)
        save()
    }

    fun restart() {
        val p = mutable.value.mahjong ?: return
        if (mutable.value.busy) return
        invalidateHint()
        mutable.value = mutable.value.copy(mahjong = p.copy(moves = emptyList()), selected = null, message = "Deal restarted.")
        save()
    }

    fun hint() {
        val s = mutable.value
        if (s.busy || s.hintBusy) return
        s.hangman?.let { p ->
            if (!p.complete) { mutable.value = s.copy(hangman = p.hint(), message = "A letter was revealed."); save() }
            return
        }
        val p = s.mahjong ?: return
        if (p.complete) return
        val version = revision
        mutable.value = s.copy(hintBusy = true, selected = null, message = "Searching for a route to clear the board…")
        hintJob = viewModelScope.launch {
            try {
                val result = runInterruptible(worker) { MahjongSolver.solve(p) }
                if (version != revision) return@launch
                val pair = result.path.firstOrNull()
                mutable.value = mutable.value.copy(hintBusy = false, highlighted = pair,
                    mahjong = if (pair != null) p.copy(hints = p.hints + 1) else p,
                    message = when (result.verdict) {
                        MahjongVerdict.SOLVED -> "Try the highlighted pair. A clearing route was verified."
                        MahjongVerdict.UNSOLVABLE -> "This position cannot be cleared. Undo a move or restart the deal."
                        MahjongVerdict.LIMIT_REACHED -> "Search limit reached. Solvability is still unknown; try Undo or another pair."
                    })
                if (pair != null) save()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                if (version == revision) mutable.value = mutable.value.copy(hintBusy = false, message = "Couldn't check this position. Try again.")
            }
        }
    }

    private fun invalidateHint() {
        revision++
        hintJob?.cancel()
        mutable.value = mutable.value.copy(hintBusy = false, highlighted = null)
    }

    fun retrySave() = save(true)
    private fun save(confirm: Boolean = false) {
        val s = mutable.value
        val encoded = s.hangman?.let(HangmanSaveCodec::encode) ?: s.mahjong?.let(MahjongSaveCodec::encode) ?: return
        val previous = saveJob
        saveJob = viewModelScope.launch {
            previous?.join()
            try {
                store.save(game.name, encoded)
                if (confirm || mutable.value.message == "Couldn't save progress. Tap Save to retry.")
                    mutable.value = mutable.value.copy(message = "Progress saved.")
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = mutable.value.copy(message = "Couldn't save progress. Tap Save to retry.") }
        }
    }
}
