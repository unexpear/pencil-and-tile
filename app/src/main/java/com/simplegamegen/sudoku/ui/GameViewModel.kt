package com.simplegamegen.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.constraints.Constraint
import com.simplegamegen.sudoku.constraints.Constraints
import com.simplegamegen.sudoku.data.GameStore
import com.simplegamegen.sudoku.data.PlaySnapshot
import com.simplegamegen.sudoku.data.SnapshotValidation
import com.simplegamegen.sudoku.data.StatsStore
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.model.ArrowShaft
import com.simplegamegen.sudoku.model.Board
import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.KropkiDot
import com.simplegamegen.sudoku.model.Puzzle
import com.simplegamegen.sudoku.model.SandwichClues
import com.simplegamegen.sudoku.model.VariantType
import com.simplegamegen.sudoku.solver.HintSolver
import com.simplegamegen.sudoku.solver.Hint
import com.simplegamegen.sudoku.validation.Validator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class GameUiState(
    val size: Int = 9,
    val difficulty: Difficulty = Difficulty.EASY,
    val variant: VariantType = VariantType.CLASSIC,
    val current: List<Int> = List(81) { 0 },
    val fixed: List<Boolean> = List(81) { false },
    val notes: List<Set<Int>> = List(81) { emptySet() },
    val notesMode: Boolean = false,
    val regions: List<Int>? = null,
    val cageIndex: List<Int>? = null,
    val cageSums: List<Int>? = null,
    val thermos: List<List<Int>>? = null,
    val dots: List<KropkiDot>? = null,
    val arrows: List<ArrowShaft>? = null,
    val sandwich: SandwichClues? = null,
    val selected: Int? = null,
    val conflicts: Set<Int> = emptySet(),
    val won: Boolean = false,
    val generating: Boolean = false,
    val seed: Long = 0L,
    val elapsedSeconds: Int = 0,
    val hintsUsed: Int = 0,
    val hintBusy: Boolean = false,
    val canUndo: Boolean = false,
    val hasSave: Boolean = false,
    val hasGame: Boolean = false,
    val message: String? = null,
)

/** Full play logic: entry, notes, undo, hints, timer, autosave, stats. */
class GameViewModel(
    private val repo: GameStore,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val generatePuzzle: suspend (Int, Difficulty, VariantType, Long) -> Puzzle = { size, difficulty, variant, seed ->
        when (variant) {
            VariantType.CLASSIC -> Generator.generateClassic(size, difficulty, seed)
            VariantType.DIAGONAL_X -> Generator.generateDiagonalX(size, difficulty, seed)
            VariantType.JIGSAW -> Generator.generateJigsaw(size, difficulty, seed)
            VariantType.KILLER -> Generator.generateKiller(size, difficulty, seed)
            VariantType.THERMO -> Generator.generateThermo(size, difficulty, seed)
            VariantType.KROPKI -> Generator.generateKropki(size, difficulty, seed)
            VariantType.ARROW -> Generator.generateArrow(size, difficulty, seed)
            VariantType.SANDWICH -> Generator.generateSandwich(size, difficulty, seed)
        }
    },
    private val findHint: suspend (Board, List<Constraint>, Board) -> Hint? = { board, constraints, solution ->
        HintSolver.nextHint(board, constraints, solution)
    },
) : ViewModel() {

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state

    val statRows: Flow<List<StatsStore.StatRow>>
        get() = repo.statRows

    private var puzzle: Puzzle? = null
    private var board: Board = Board.empty(9)
    private var noteSets: MutableList<MutableSet<Int>> = MutableList(81) { mutableSetOf() }

    private data class UndoEntry(val cells: List<Int>, val notes: List<Set<Int>>)
    private val undoStack = ArrayDeque<UndoEntry>()
    private var winRecorded = false
    private var timerJob: Job? = null
    private var gameJob: Job? = null
    private var hintJob: Job? = null
    private var persistenceJob: Job? = null
    private var gameVersion = 0L
    private var boardRevision = 0L
    private var playing = false
    private var saveEpoch = 0

    init {
        OpenBoards.watch(viewModelScope) { forget() }
        refreshHasSave()
    }

    /** Drops the board without writing it. In-flight saves that have not started are skipped. */
    suspend fun forget() {
        saveEpoch++
        val epoch = saveEpoch
        gameVersion++
        playing = false
        drainChildren(viewModelScope)
        if (epoch != saveEpoch) return
        val keep = _state.value
        puzzle = null
        board = Board.empty(keep.size)
        noteSets = MutableList(keep.size * keep.size) { mutableSetOf() }
        undoStack.clear()
        winRecorded = false
        _state.value = GameUiState(size = keep.size, difficulty = keep.difficulty, variant = keep.variant)
    }

    // ---- game lifecycle ----

    fun newGame(size: Int, difficulty: Difficulty, variant: VariantType) {
        val seed = Random.nextLong()
        val version = beginGameChange()
        gameJob = viewModelScope.launch {
              try {
                  val p = withContext(workerDispatcher) { generatePuzzle(size, difficulty, variant, seed) }
                  if (version != gameVersion) return@launch
                  puzzle = p
                  board = p.toBoard()
                  noteSets = MutableList(p.size * p.size) { mutableSetOf() }
                  undoStack.clear()
                  winRecorded = false
                  publishFresh(p, p.seed)
                  startTimer()
                  persist { repo.recordStart(p.variant, p.size, p.difficulty) }
                  autosave()
            } catch (e: CancellationException) {
                  throw e
            } catch (_: Exception) {
                  if (version == gameVersion) {
                      _state.value = _state.value.copy(generating = false, message = "Couldn't generate this puzzle. Please try again.")
                      startTimer()
                  }
            }
        }
    }

    private fun beginGameChange(): Long {
        autosave()
        timerJob?.cancel()
        gameJob?.cancel()
        invalidateHint()
        gameVersion++
        _state.value = _state.value.copy(generating = true, message = null)
        return gameVersion
    }

    fun loadGame() {
        // Resume the live board without discarding undo history or loading an
        // older checkpoint. A cold start reads the persisted snapshot below.
        if (puzzle != null && !_state.value.won && !_state.value.generating) return
        val version = beginGameChange()
        gameJob = viewModelScope.launch {
            try {
                persistenceJob?.join()
                val snapshot = repo.load()
                val valid = snapshot != null && withContext(workerDispatcher) {
                    SnapshotValidation.isValid(snapshot)
                }
                if (version != gameVersion) return@launch
                if (!valid) {
                    persist {
                        repo.clear()
                        _state.value = _state.value.copy(hasSave = false)
                    }
                    _state.value = _state.value.copy(generating = false, message = "No valid saved game found.")
                    startTimer()
                    return@launch
                }
                val p = snapshot.puzzle
                puzzle = p
                board = Board(p.size, p.boxRows, p.boxCols, snapshot.current.toIntArray())
                noteSets = snapshot.notes.map { it.toMutableSet() }.toMutableList()
                undoStack.clear()
                winRecorded = false
                _state.value = GameUiState(
                    size = p.size, difficulty = p.difficulty, variant = p.variant,
                    current = board.cells.toList(),
                    fixed = p.givens.map { it != 0 },
                    notes = noteSets.map { it.toSet() },
                    regions = p.regions?.toList(),
                    cageIndex = buildCageIndex(p),
                    cageSums = p.cages?.map { it.sum },
                    thermos = p.thermos,
                    dots = p.dots,
                    arrows = p.arrows,
                    sandwich = p.sandwich,
                    generating = false,
                    seed = p.seed,
                    elapsedSeconds = snapshot.elapsedSeconds,
                    hintsUsed = snapshot.hintsUsed,
                    hasGame = true,
                    hasSave = true,
                )
                publish(_state.value.selected)
                startTimer()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (version == gameVersion) {
                    _state.value = _state.value.copy(generating = false, message = "Couldn't load your game. Please try again.")
                    startTimer()
                }
            }
        }
    }

    private fun publishFresh(p: Puzzle, seed: Long) {
        _state.value = GameUiState(
            size = p.size, difficulty = p.difficulty, variant = p.variant,
            current = board.cells.toList(),
            fixed = p.givens.map { it != 0 },
            notes = noteSets.map { it.toSet() },
            regions = p.regions?.toList(),
            cageIndex = buildCageIndex(p),
            cageSums = p.cages?.map { it.sum },
            thermos = p.thermos,
            dots = p.dots,
            arrows = p.arrows,
            sandwich = p.sandwich,
            generating = false,
            seed = seed,
            hasSave = _state.value.hasSave,
            hasGame = true,
        )
        publish(selected = null)
    }

    private fun buildCageIndex(p: Puzzle): List<Int>? = p.cages?.let { cages ->
        IntArray(p.size * p.size) { -1 }.also { map ->
            cages.forEachIndexed { id, cage -> cage.cells.forEach { map[it] = id } }
        }.toList()
    }

    // ---- moves ----

    fun selectCell(index: Int) {
        val s = _state.value
        if (s.generating || !s.hasGame || index !in board.cells.indices) return
        _state.value = s.copy(selected = index)
    }

    fun toggleNotesMode() {
        val s = _state.value
        if (s.generating || !s.hasGame || s.won) return
        _state.value = s.copy(notesMode = !s.notesMode)
    }

    fun inputNumber(n: Int) {
        val s = _state.value
        val sel = s.selected ?: return
        if (s.generating || s.won || s.fixed.getOrNull(sel) == true) return
        if (n !in 1..s.size) return
        if (s.notesMode) {
            if (board.cells[sel] != 0) return
            pushUndo()
            val set = noteSets[sel]
            if (n in set) set.remove(n) else set.add(n)
            publish(sel)
            autosave()
            return
        }
        if (board.cells[sel] == n && noteSets[sel].isEmpty()) return
        pushUndo()
        board.cells[sel] = n
        noteSets[sel].clear()
        cleanPeerNotes(sel, n)
        publish(sel)
        autosave()
    }

    fun erase() {
        val s = _state.value
        val sel = s.selected ?: return
        if (s.generating || s.won || s.fixed.getOrNull(sel) == true) return
        if (board.cells[sel] == 0 && noteSets[sel].isEmpty()) return
        pushUndo()
        board.cells[sel] = 0
        noteSets[sel].clear()
        publish(sel)
        autosave()
    }

    fun undo() {
        if (_state.value.generating || _state.value.won) return
        val entry = undoStack.removeLastOrNull() ?: return
        invalidateHint()
        board = Board(board.size, board.boxRows, board.boxCols, entry.cells.toIntArray())
        noteSets = entry.notes.map { it.toMutableSet() }.toMutableList()
        publish(_state.value.selected)
        autosave()
    }

    private fun pushUndo() {
        invalidateHint()
        undoStack.addLast(UndoEntry(board.cells.toList(), noteSets.map { it.toSet() }))
        if (undoStack.size > 200) undoStack.removeFirst()
        _state.value = _state.value.copy(canUndo = true)
    }

    /** Drop a placed digit from peers' pencil marks (rows/cols/boxes + variant groups). */
    private fun cleanPeerNotes(cell: Int, value: Int) {
        val p = puzzle ?: return
        val size = p.size
        val r = cell / size
        val c = cell % size
        val peers = HashSet<Int>()
        for (cc in 0 until size) peers.add(r * size + cc)
        for (rr in 0 until size) peers.add(rr * size + c)
        if (p.variant != VariantType.JIGSAW) {
            val boxRow0 = (r / p.boxRows) * p.boxRows
            val boxCol0 = (c / p.boxCols) * p.boxCols
            for (rr in boxRow0 until boxRow0 + p.boxRows) {
                for (cc in boxCol0 until boxCol0 + p.boxCols) {
                    peers.add(rr * size + cc)
                }
            }
        }
        p.regions?.let { regions ->
            val id = regions[cell]
            regions.forEachIndexed { i, rid -> if (rid == id) peers.add(i) }
        }
        p.cages?.let {
            _state.value.cageIndex?.let { index ->
                val id = index[cell]
                index.forEachIndexed { i, cid -> if (cid == id) peers.add(i) }
            }
        }
        if (p.variant == VariantType.DIAGONAL_X && (r == c || r + c == size - 1)) {
            if (r == c) for (k in 0 until size) peers.add(k * size + k)
            if (r + c == size - 1) for (k in 0 until size) peers.add(k * size + (size - 1 - k))
        }
        peers.remove(cell)
        for (j in peers) noteSets[j].remove(value)
    }

    // ---- hints ----

    fun requestHint() {
        val s = _state.value
        val p = puzzle ?: return
        if (s.generating || s.won || s.hintBusy) return
        if (s.conflicts.isNotEmpty()) {
            _state.value = s.copy(message = "Fix the red conflicts first, then ask for a hint.")
            return
        }
        _state.value = s.copy(hintBusy = true)
        val version = gameVersion
        val revision = boardRevision
        val constraints = activeConstraints()
        val snapshot = board.copy()
        val solution = p.solutionBoard()
        hintJob = viewModelScope.launch {
            try {
                val hint = withContext(workerDispatcher) { findHint(snapshot, constraints, solution) }
                if (version != gameVersion || revision != boardRevision || _state.value.generating) return@launch
                hintJob = null
                if (hint == null) {
                    _state.value = _state.value.copy(hintBusy = false, message = "No hint available right now.")
                    return@launch
                }
                val sel = hint.cell
                if (sel !in board.cells.indices || board.cells[sel] != 0 ||
                    _state.value.fixed.getOrNull(sel) == true ||
                    !Validator.isValidPlacement(board, sel, hint.value, constraints)
                ) {
                    _state.value = _state.value.copy(hintBusy = false, message = "No hint available right now.")
                    return@launch
                }
                pushUndo()
                board.cells[sel] = hint.value
                noteSets[sel].clear()
                cleanPeerNotes(sel, hint.value)
                _state.value = _state.value.copy(hintsUsed = _state.value.hintsUsed + 1)
                _state.value = _state.value.copy(hintBusy = false, message = hint.explanation)
                publish(sel)
                autosave()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (version == gameVersion && revision == boardRevision) {
                    _state.value = _state.value.copy(hintBusy = false, message = "Couldn't calculate a hint. Please try again.")
                }
            }
        }
    }

    private fun invalidateHint() {
        boardRevision++
        hintJob?.cancel()
        hintJob = null
        _state.value = _state.value.copy(hintBusy = false)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    // ---- internals ----

    private fun activeConstraints() = puzzle?.let { p ->
        Constraints.forVariant(
            p.size, p.boxRows, p.boxCols, p.variant, p.regions, p.cages,
            p.thermos, p.dots, p.arrows, p.sandwich,
        )
    } ?: emptyList()

    private fun publish(selected: Int?) {
        val p = puzzle ?: return
        val conflicts = Validator.findConflicts(board, activeConstraints())
        val won = board.isFull() && conflicts.isEmpty()
        _state.value = _state.value.copy(
            current = board.cells.toList(),
            notes = noteSets.map { it.toSet() },
            conflicts = conflicts,
            won = won,
            selected = selected,
            canUndo = !won && undoStack.isNotEmpty(),
        )
        if (won && !winRecorded) {
            winRecorded = true
            onWin(p)
        }
    }

    private fun onWin(p: Puzzle) {
        timerJob?.cancel()
        val elapsed = _state.value.elapsedSeconds.toLong()
        persist {
            repo.clear()
            _state.value = _state.value.copy(hasSave = false)
            repo.recordWin(p.variant, p.size, p.difficulty, elapsed)
        }
    }

    private fun autosave() {
        val p = puzzle ?: return
        if (_state.value.won || _state.value.generating) return
        val snapshot = PlaySnapshot(
            puzzle = p,
            current = board.cells.toList(),
            notes = noteSets.map { it.toSet() },
            elapsedSeconds = _state.value.elapsedSeconds,
            hintsUsed = _state.value.hintsUsed,
        )
        persist {
            repo.save(snapshot)
            _state.value = _state.value.copy(hasSave = true)
        }
    }

    private fun refreshHasSave() {
        persist {
            val hasSave = repo.hasSave()
            _state.value = _state.value.copy(hasSave = hasSave)
        }
    }

    /** Queue saves, clears and statistics in request order, including across games. */
    private fun persist(action: suspend () -> Unit) {
        val epoch = saveEpoch
        val previous = persistenceJob
        persistenceJob = viewModelScope.launch {
            previous?.join()
            if (epoch != saveEpoch) return@launch
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(message = "Couldn't save game data. Please try again.")
            }
        }
    }

    /** Count only visible play time; checkpoint when leaving or backgrounding. */
    fun setPlaying(active: Boolean) {
        playing = active
        if (active) startTimer() else {
            timerJob?.cancel()
            invalidateHint()
            autosave()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        if (!playing || puzzle == null || _state.value.generating || _state.value.won) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val s = _state.value
                if (!s.generating && !s.won && puzzle != null) {
                    _state.value = s.copy(elapsedSeconds = s.elapsedSeconds + 1)
                    if (_state.value.elapsedSeconds % 5 == 0) autosave()
                }
            }
        }
    }
}
