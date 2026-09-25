package com.simplegamegen.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.WordGameStore
import com.simplegamegen.sudoku.words.WordGame
import com.simplegamegen.sudoku.words.WordDifficulty
import com.simplegamegen.sudoku.words.WordProgress
import com.simplegamegen.sudoku.words.WordPuzzles
import com.simplegamegen.sudoku.words.WordPuzzle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class WordGameState(
    val progress: WordProgress? = null,
    val busy: Boolean = true,
    val selectedEntry: Int = 0,
    val startCell: Int? = null,
    val message: String? = null,
)

class WordGameViewModel(
    val game: WordGame,
    private val store: WordGameStore,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val createPuzzle: suspend (WordGame, Int, WordDifficulty) -> WordPuzzle = { game, theme, difficulty ->
        val seed = Random.nextLong()
        if (game == WordGame.CROSSWORD) WordPuzzles.crossword(seed, difficulty) else WordPuzzles.wordSearch(seed, theme, difficulty)
    },
) : ViewModel() {
    private val mutable = MutableStateFlow(WordGameState())
    val state: StateFlow<WordGameState> = mutable
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                mutable.value = WordGameState(progress = store.load(game), busy = false)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                mutable.value = WordGameState(busy = false, message = "Couldn't restore this game. You can start a new puzzle.")
            }
        }
    }

    fun newGame(theme: Int = 0, difficulty: WordDifficulty = WordDifficulty.EASY) {
        if (mutable.value.busy || theme !in WordPuzzles.themes.indices) return
        mutable.value = mutable.value.copy(busy = true, message = null, startCell = null)
        viewModelScope.launch {
            try {
                val puzzle = withContext(worker) {
                    createPuzzle(game, theme, difficulty)
                }
                mutable.value = WordGameState(progress = WordProgress(puzzle), busy = false)
                save()
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(busy = false, message = "Couldn't create a puzzle. Please try again.")
            }
        }
    }

    fun selectEntry(index: Int) {
        val s = mutable.value
        if (!s.busy && index in (s.progress?.puzzle?.entries?.indices ?: IntRange.EMPTY))
            mutable.value = s.copy(selectedEntry = index, message = null)
    }

    fun tapCell(cell: Int) {
        val s = mutable.value
        val p = s.progress ?: return
        if (s.busy || cell !in p.puzzle.letters.indices) return
        if (game == WordGame.CROSSWORD) {
            val options = p.puzzle.entries.indices.filter { cell in p.puzzle.entries[it].cells }
            if (options.isNotEmpty()) selectEntry(options[(options.indexOf(s.selectedEntry) + 1) % options.size])
        } else if (!p.complete) {
            if (s.startCell == null) mutable.value = s.copy(startCell = cell, message = "Now tap the last letter.")
            else {
                val updated = p.find(s.startCell, cell)
                mutable.value = s.copy(progress = updated, startCell = null,
                    message = if (updated.found != p.found) "Word found!" else "No new word there. Try another straight line.")
                if (updated != p) save()
            }
        }
    }

    fun answer(text: String) {
        val s = mutable.value
        val p = s.progress ?: return
        if (s.busy || p.complete || game != WordGame.CROSSWORD) return
        mutable.value = s.copy(progress = p.answer(p.puzzle.entries[s.selectedEntry], text), message = null)
        save()
    }

    fun checkAnswer() {
        val s = mutable.value
        val p = s.progress ?: return
        if (s.busy || game != WordGame.CROSSWORD) return
        val entry = p.puzzle.entries[s.selectedEntry]
        val answer = entry.cells.map { p.current[it] }.joinToString("")
        mutable.value = s.copy(message = when {
            answer == entry.answer -> "This answer is correct."
            '_' in answer -> "This answer has empty squares."
            else -> "This answer needs another look."
        })
    }

    fun hint() {
        val s = mutable.value
        val p = s.progress ?: return
        if (s.busy || p.complete) return
        if (game == WordGame.CROSSWORD) {
            val entry = p.puzzle.entries[s.selectedEntry]
            val cell = entry.cells.firstOrNull { p.current[it] != p.puzzle.letters[it] }
            if (cell == null) {
                mutable.value = s.copy(message = "This answer is already correct.")
                return
            }
            val current = p.current.toCharArray().also { it[cell] = p.puzzle.letters[cell] }.concatToString()
            mutable.value = s.copy(progress = p.copy(current = current, hints = p.hints + 1), message = "One letter revealed.")
        } else {
            val entry = p.puzzle.entries.first { it.answer !in p.found }
            mutable.value = s.copy(progress = p.copy(hints = p.hints + 1), startCell = entry.cells.first(),
                message = "${entry.answer} starts at the highlighted square. Tap its last letter.")
        }
        save()
    }

    fun retrySave() = save(confirm = true)

    private fun save(confirm: Boolean = false) {
        val progress = mutable.value.progress ?: return
        val previous = saveJob
        saveJob = viewModelScope.launch {
            previous?.join()
            try {
                store.save(progress)
                if (mutable.value.progress == progress && (confirm || mutable.value.message == "Couldn't save progress. Tap Save to retry.")) {
                    mutable.value = mutable.value.copy(message = "Progress saved.")
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = "Couldn't save progress. Tap Save to retry.")
            }
        }
    }
}
