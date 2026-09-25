package com.simplegamegen.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.ArcadeStore
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.tabletop.*
import com.simplegamegen.sudoku.tabletop.Result as GameResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TableUiState(val match: TableMatch? = null, val busy: Boolean = true, val thinking: Boolean = false,
    val message: String? = null, val hint: Move? = null)

class TableViewModel(val game: TableGame, private val store: ArcadeStore, private val factory: PuzzleFactory,
    private val worker: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
    private val mutable = MutableStateFlow(TableUiState())
    val state: StateFlow<TableUiState> = mutable
    private var computation: Job? = null
    private var writes: Job? = null
    private var revision = 0L
    private val key = "TABLE_${game.name}"
    init {
        viewModelScope.launch {
            try {
                val raw = store.load(key)
                val restored = raw?.let { runInterruptible(worker) { requireNotNull(TableSaveCodec.decode(it)).also { m -> require(m.game == game) } } }
                mutable.value = TableUiState(restored, busy = false)
                computerTurn()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = TableUiState(busy = false, message = "Couldn't restore this game. Start a new one to recover.") }
        }
    }
    private fun invalidate() { revision++; computation?.cancel(); mutable.value = mutable.value.copy(thinking = false, hint = null) }
    fun newGame(setting: Int) {
        if (mutable.value.busy || setting !in game.settings.indices) return
        invalidate(); mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            try {
                val match = factory.table(game, setting)
                mutable.value = TableUiState(match, busy = false); save()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, message = "Couldn't create a game. Please try again."); computerTurn() }
        }
    }
    fun play(move: Move) {
        val s = mutable.value; val match = s.match ?: return
        if (s.busy || match.state.turn != 1 || match.state.result != GameResult.PLAYING) return
        val next = match.play(move) ?: return
        invalidate(); mutable.value = mutable.value.copy(match = next, message = null)
        save(); computerTurn()
    }
    private fun computerTurn() {
        val m = mutable.value.match ?: return
        if (!game.opponent || m.state.turn != -1 || m.state.result != GameResult.PLAYING) return
        val version = revision
        mutable.value = mutable.value.copy(thinking = true)
        computation = viewModelScope.launch {
            try {
                var current = m
                while (current.state.turn == -1 && current.state.result == GameResult.PLAYING) {
                    val snapshot = current
                    val move = runInterruptible(worker) { TableAi.choose(snapshot.state, snapshot.setting, snapshot.seed xor snapshot.moves.size.toLong()) }
                    checkNotNull(move)
                    current = checkNotNull(current.play(move))
                    if (revision != version) return@launch
                    mutable.value = mutable.value.copy(match = current)
                    save()
                }
                mutable.value = mutable.value.copy(thinking = false)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                if (revision == version) mutable.value = mutable.value.copy(thinking = false, message = "Computer move interrupted. Tap Resume computer or Undo.")
            }
        }
    }
    fun resumeComputer() { if (!mutable.value.busy && !mutable.value.thinking) computerTurn() }
    fun undo() {
        val m = mutable.value.match ?: return
        if (mutable.value.busy || m.moves.isEmpty()) return
        invalidate(); mutable.value = mutable.value.copy(busy = true)
        computation = viewModelScope.launch {
            try {
                val previous = runInterruptible(worker) {
                    var n = m.moves.size - 1
                    var previous = m.rewind(n)
                    while (n > 0 && game.opponent && (previous.state.turn == -1 || (previous.state as? CheckersState)?.forced != null)) {
                        previous = m.rewind(--n)
                    }
                    previous
                }
                mutable.value = TableUiState(previous, busy = false); save()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = mutable.value.copy(busy = false, message = "Couldn't undo. Your game is unchanged.") }
        }
    }
    fun restart() {
        val m = mutable.value.match ?: return
        if (mutable.value.busy) return
        invalidate(); mutable.value = TableUiState(m.rewind(0), busy = false); save()
    }
    fun hint() {
        val s = mutable.value; val m = s.match ?: return
        if (s.busy || s.thinking || m.state.turn != 1 || m.state.result != GameResult.PLAYING) return
        val board = m.state
        if (board is MinesState) {
            val deduction = board.deduction()
            mutable.value = s.copy(hint = deduction?.let { Move(if (it.mine) Op.FLAG else Op.REVEAL, it.cell) }, message = when {
                !board.started -> "Choose any opening square; it and its neighbors will be safe."
                deduction == null -> "No move was proved from the visible clues. Flags are not assumed correct; a guess may be needed."
                else -> "Row ${deduction.cell / board.preset.width + 1}, column ${deduction.cell % board.preset.width + 1}: ${if (deduction.mine) "a mine is proved" else "safe to reveal"}."
            })
            return
        }
        val version = revision
        mutable.value = s.copy(thinking = true)
        computation = viewModelScope.launch {
            try {
                val move = runInterruptible(worker) {
                    if (m.state is SolitaireState) m.state.legalMoves().sortedBy { when { it.to >= 8 -> 0; it.op == Op.MOVE -> 1; else -> 2 } }.firstOrNull()
                    else TableAi.choose(m.state, m.setting, m.seed xor m.moves.size.toLong())
                }
                if (version == revision) mutable.value = mutable.value.copy(thinking = false, hint = move,
                    message = if (move == null) "No legal move found. Undo or start a new game." else "Suggested legal move: ${describeMove(m.state, move)}. This is not a guaranteed win.")
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { if (version == revision) mutable.value = mutable.value.copy(thinking = false, message = "Couldn't find a hint. Try again.") }
        }
    }
    fun save(confirm: Boolean = false) {
        val encoded = mutable.value.match?.let(TableSaveCodec::encode) ?: return
        val previous = writes
        writes = viewModelScope.launch {
            previous?.join()
            try {
                store.save(key, encoded)
                if (confirm || mutable.value.message == "Couldn't save. Tap Save to retry.") mutable.value = mutable.value.copy(message = "Progress saved.")
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { mutable.value = mutable.value.copy(message = "Couldn't save. Tap Save to retry.") }
        }
    }
}

fun describeMove(state: TableState, move: Move): String = when (move.op) {
    Op.DRAW -> if (state is SolitaireState) "tap the stock" else "draw a tile"
    Op.PASS -> "pass"
    Op.REVEAL, Op.FLAG, Op.CHORD -> "square ${move.from + 1}"
    Op.MOVE -> when (state) {
        is ReversiState -> "row ${move.from / 8 + 1}, column ${move.from % 8 + 1}"
        is CheckersState -> "row ${move.from / 8 + 1}, column ${move.from % 8 + 1} to row ${move.to / 8 + 1}, column ${move.to % 8 + 1}"
        is DominoState -> DominoState.tiles[move.from].let { "${it.a}|${it.b} to the ${if (move.to == 0) "left" else "right"}" }
        is SolitaireState -> "${SolitaireState.label(state.pile(move.from).takeLast(move.count).first())} to ${if (move.to >= 8) "foundation ${move.to - 7}" else "column ${move.to + 1}"}"
        else -> "move"
    }
}
