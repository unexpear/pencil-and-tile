package com.simplegamegen.sudoku.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplegamegen.sudoku.data.ArcadeStore
import com.simplegamegen.sudoku.grids.DraftCheck
import com.simplegamegen.sudoku.grids.DraftVerdict
import com.simplegamegen.sudoku.grids.GridCodec
import com.simplegamegen.sudoku.grids.GridDraft
import com.simplegamegen.sudoku.grids.GridDraftCodec
import com.simplegamegen.sudoku.grids.GridGame
import com.simplegamegen.sudoku.grids.GridGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** What a tap on the grid does in the editor. */
enum class EditTool(val label: String) { REGIONS("Regions"), DIGITS("Digits"), CAGES("Cages"), MARKS("Odd/even"), GROUPS("Groups"), RULES("Rules") }

data class EditorState(
    val drafts: List<GridDraft> = emptyList(),
    /** Each saved grid's check result, filled in the background (null while checking). */
    val statuses: List<DraftVerdict?> = emptyList(),
    val loaded: Boolean = false,
    /** The draft on the editing screen and its position in [drafts] (-1 while unsaved). */
    val draft: GridDraft? = null,
    val index: Int = -1,
    val dirty: Boolean = false,
    val tool: EditTool = EditTool.REGIONS,
    val region: Int = 0,
    val selected: Int? = null,
    /** Squares picked for a new cage or extra group. */
    val picked: List<Int> = emptyList(),
    val check: DraftCheck? = null,
    val checking: Boolean = false,
    val working: Boolean = false,
    val canUndo: Boolean = false,
    val message: String? = null,
)

/** Custom grid editor: saved grids, the draft being edited, background checks and helper tools. */
class GridEditorViewModel(
    private val store: ArcadeStore,
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val random: Random = Random.Default,
) : ViewModel() {
    private val mutable = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = mutable.asStateFlow()
    private val undo = ArrayDeque<GridDraft>()
    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            val saved = runCatching { store.load(KEY) }.getOrNull().orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull(GridDraftCodec::decode)
            mutable.value = mutable.value.copy(drafts = saved, loaded = true)
            recheckAll()
        }
    }

    private var statusJob: Job? = null

    /** Checks every saved grid so the list can show which ones are ready to play or share. */
    private fun recheckAll() {
        statusJob?.cancel()
        val drafts = mutable.value.drafts
        mutable.value = mutable.value.copy(statuses = List(drafts.size) { null })
        statusJob = viewModelScope.launch {
            drafts.forEachIndexed { i, d ->
                val verdict = withTimeoutOrNull(20_000) { runInterruptible(worker) { d.check().verdict } } ?: DraftVerdict.TOO_HARD
                val s = mutable.value
                if (s.drafts == drafts) mutable.value = s.copy(statuses = s.statuses.toMutableList().also { it[i] = verdict })
            }
        }
    }

    fun newDraft(size: Int) = open(GridDraft.blank(size).copy(name = "My ${size}×$size grid"), -1)

    fun edit(index: Int) { mutable.value.drafts.getOrNull(index)?.let { open(it, index) } }

    /** Opens a shared code (layout plus givens) as a new unsaved draft. */
    fun import(code: String): Boolean {
        val (rules, givens, name) = GridCodec.decodePuzzle(code) ?: return false
        open(GridDraft(rules.size, rules.regions, givens.toList(), rules.rules, rules.extras, rules.cages, rules.parity, name.ifBlank { "Imported grid" }), -1)
        return true
    }

    private fun open(draft: GridDraft, index: Int) {
        undo.clear()
        mutable.value = mutable.value.copy(draft = draft, index = index, dirty = index < 0, selected = null, picked = emptyList(),
            check = null, canUndo = false, message = null, region = 0)
        scheduleCheck()
    }

    fun close() { checkJob?.cancel(); mutable.value = mutable.value.copy(draft = null, index = -1, check = null) }

    fun setTool(tool: EditTool) { mutable.value = mutable.value.copy(tool = tool, picked = emptyList(), selected = null) }
    fun setRegion(region: Int) { mutable.value = mutable.value.copy(region = region) }
    fun select(cell: Int?) { mutable.value = mutable.value.copy(selected = cell) }
    fun say(message: String?) { mutable.value = mutable.value.copy(message = message) }

    /** Applies a change to the draft with an undo step, then re-checks it. */
    fun change(transform: (GridDraft) -> GridDraft) {
        val s = mutable.value
        val draft = s.draft ?: return
        val next = transform(draft)
        if (next == draft) return
        undo.addLast(draft); if (undo.size > 200) undo.removeFirst()
        mutable.value = s.copy(draft = next, dirty = true, canUndo = true, message = null)
        scheduleCheck()
    }

    fun undo() {
        val s = mutable.value
        if (undo.isEmpty()) return
        mutable.value = s.copy(draft = undo.removeLast(), dirty = true, canUndo = undo.isNotEmpty())
        scheduleCheck()
    }

    /** Taps in the Cages and Groups tools collect squares; tapping a picked square removes it. */
    fun pick(cell: Int) {
        val s = mutable.value
        mutable.value = s.copy(picked = if (cell in s.picked) s.picked - cell else s.picked + cell)
    }

    fun pickAll(cells: List<Int>) {
        val s = mutable.value
        mutable.value = s.copy(picked = (s.picked + cells).distinct())
    }

    fun clearPicked() { mutable.value = mutable.value.copy(picked = emptyList()) }

    fun makeCage(sum: Int) {
        val picked = mutable.value.picked
        if (picked.isEmpty()) return
        change { it.addCage(picked, sum) }
        clearPicked()
    }

    fun makeGroup() {
        val picked = mutable.value.picked
        if (picked.size < 2) return
        change { it.addExtra(picked) }
        clearPicked()
    }

    private fun scheduleCheck() {
        checkJob?.cancel()
        val draft = mutable.value.draft ?: return
        mutable.value = mutable.value.copy(checking = true)
        checkJob = viewModelScope.launch {
            delay(350)
            val result = withTimeoutOrNull(20_000) { runInterruptible(worker) { draft.check() } }
            if (mutable.value.draft == draft) mutable.value = mutable.value.copy(check = result, checking = false)
        }
    }

    /** Fills in starting digits around the player's own so the grid has one answer. */
    fun autoGivens(level: Int) = work("Starting digits added. The puzzle has one answer.", "Couldn't fill this grid. Check the regions and rules first.") { d ->
        d.autoGivens(random, GridGenerator.givenShare(d.size, level))
    }

    /** Adds givens where answers differ until only one answer is left. */
    fun makeUnique() = work("Added digits until only one answer is left.", "This grid has no answer, so no digits can fix it.") { d -> d.fixUniqueness(random) }

    fun jigsaw() = work("New irregular regions.", "Couldn't make regions for this size.") { d -> d.withJigsaw(random) }

    private fun work(done: String, failed: String, block: (GridDraft) -> GridDraft?) {
        val draft = mutable.value.draft ?: return
        if (mutable.value.working) return
        mutable.value = mutable.value.copy(working = true, message = null)
        viewModelScope.launch {
            val result = try { withTimeoutOrNull(30_000) { runInterruptible(worker) { block(draft) } } } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            mutable.value = mutable.value.copy(working = false)
            if (result == null) say(failed) else { change { result }; say(done) }
        }
    }

    /** Saves the draft into the list (new or in place). */
    fun save(name: String? = null) {
        val s = mutable.value
        val draft = (s.draft ?: return).let { d -> name?.let { d.copy(name = it.take(40)) } ?: d }
        val list = s.drafts.toMutableList()
        val index = if (s.index in list.indices) { list[s.index] = draft; s.index } else { list += draft; list.lastIndex }
        mutable.value = s.copy(drafts = list, draft = draft, index = index, dirty = false,
            message = if (s.check?.playable == true) "Saved. It's ready to play and share." else "Saved as a draft. It can't be played or shared until it has exactly one answer.")
        persist(list)
        recheckAll()
    }

    fun delete(index: Int) {
        val list = mutable.value.drafts.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        mutable.value = mutable.value.copy(drafts = list)
        persist(list)
        recheckAll()
    }

    /** A share code, only for grids already proven to have exactly one answer. */
    fun shareCode(draft: GridDraft, verdict: DraftVerdict?): String? =
        if (verdict != DraftVerdict.UNIQUE) null else draft.toRules()?.let { GridCodec.encodePuzzle(it, draft.givens, draft.name) }

    /** A playable game for the draft, or null when it doesn't have exactly one answer. */
    suspend fun playable(draft: GridDraft): GridGame? = runInterruptible(worker) {
        val rules = draft.toRules() ?: return@runInterruptible null
        GridGame.fromDraft(rules, draft.givens.toIntArray(), draft.name, level = 1, seed = random.nextLong())
    }

    /** Puts [game] in the grid play save so the play screen opens it. */
    suspend fun sendToPlay(game: GridGame) { runCatching { store.save(PLAY_KEY, GridCodec.encode(game)) } }

    private fun persist(list: List<GridDraft>) {
        viewModelScope.launch { runCatching { store.save(KEY, list.joinToString("\n", transform = GridDraftCodec::encode)) } }
    }

    companion object {
        const val KEY = "GRID_DRAFTS"
        /** Save slot of the grid play screen. */
        const val PLAY_KEY = "PLAY_SUDOKU_GRID"
    }
}
