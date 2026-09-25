package com.simplegamegen.sudoku.data

import com.simplegamegen.sudoku.model.Difficulty
import com.simplegamegen.sudoku.model.Puzzle
import com.simplegamegen.sudoku.model.SandwichClues
import com.simplegamegen.sudoku.model.VariantType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Everything the UI needs to freeze and resume a game mid-play. */
data class PlaySnapshot(
    val puzzle: Puzzle,
    val current: List<Int>,
    val notes: List<Set<Int>>,
    val elapsedSeconds: Int,
    val hintsUsed: Int,
)

class GameRepository(
    private val db: SudokuDatabase,
    private val stats: StatsStore,
) : GameStore {
    override val statRows: Flow<List<StatsStore.StatRow>> = stats.stats

    override suspend fun save(snapshot: PlaySnapshot) = withContext(Dispatchers.IO) {
        val p = snapshot.puzzle
        val sandwichSides = p.sandwich?.let { SaveCodec.sandwichToStrings(it) }
        db.savedGameDao().upsert(
            SavedGameEntity(
                variant = p.variant.name,
                size = p.size,
                boxRows = p.boxRows,
                boxCols = p.boxCols,
                difficulty = p.difficulty.name,
                seed = p.seed,
                givens = SaveCodec.intsToCsv(p.givens.toList()),
                solution = SaveCodec.intsToCsv(p.solution.toList()),
                current = SaveCodec.intsToCsv(snapshot.current),
                notes = SaveCodec.notesToString(snapshot.notes),
                regions = p.regions?.toList()?.let { SaveCodec.intsToCsv(it) },
                cages = p.cages?.let { SaveCodec.cagesToString(it) },
                thermos = p.thermos?.let { SaveCodec.thermosToString(it) },
                dots = p.dots?.let { SaveCodec.dotsToString(it) },
                arrows = p.arrows?.let { SaveCodec.arrowsToString(it) },
                sandwichTop = sandwichSides?.get(0),
                sandwichBottom = sandwichSides?.get(1),
                sandwichLeft = sandwichSides?.get(2),
                sandwichRight = sandwichSides?.get(3),
                elapsedSeconds = snapshot.elapsedSeconds,
                hintsUsed = snapshot.hintsUsed,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Null when there is no save or it fails validation. */
    override suspend fun load(): PlaySnapshot? = withContext(Dispatchers.IO) {
        val e = db.savedGameDao().get() ?: return@withContext null
        try {
            val variant = VariantType.valueOf(e.variant)
            val difficulty = Difficulty.valueOf(e.difficulty)
            require(e.size in listOf(4, 6, 9))
            val cells = e.size * e.size
            val givens = SaveCodec.csvToInts(e.givens, cells) ?: return@withContext null
            val solution = SaveCodec.csvToInts(e.solution, cells) ?: return@withContext null
            if (solution.any { it !in 1..e.size }) return@withContext null
            val current = SaveCodec.csvToInts(e.current, cells) ?: return@withContext null
            val notes = SaveCodec.stringToNotes(e.notes, cells, e.size) ?: return@withContext null
            val regions = e.regions?.let { SaveCodec.csvToInts(it, cells)?.toIntArray() }
            val cages = e.cages?.let { SaveCodec.stringToCages(it) }
            val thermos = e.thermos?.let { SaveCodec.stringToThermos(it) }
            val dots = e.dots?.let { SaveCodec.stringToDots(it) }
            val arrows = e.arrows?.let { SaveCodec.stringToArrows(it) }
            val sandwich = if (e.sandwichTop != null) {
                SandwichClues(
                    SaveCodec.stringToSandwichSide(e.sandwichTop, e.size) ?: return@withContext null,
                    SaveCodec.stringToSandwichSide(e.sandwichBottom, e.size) ?: return@withContext null,
                    SaveCodec.stringToSandwichSide(e.sandwichLeft, e.size) ?: return@withContext null,
                    SaveCodec.stringToSandwichSide(e.sandwichRight, e.size) ?: return@withContext null,
                )
            } else {
                null
            }
            val puzzle = Puzzle(
                size = e.size, boxRows = e.boxRows, boxCols = e.boxCols,
                givens = givens.toIntArray(),
                solution = solution.toIntArray(),
                variant = variant, difficulty = difficulty, seed = e.seed,
                regions = regions, cages = cages, thermos = thermos,
                dots = dots, arrows = arrows, sandwich = sandwich,
            )
            // Validate the variant extras actually constrain (throws on corrupt).
            com.simplegamegen.sudoku.constraints.Constraints.forVariant(
                e.size, e.boxRows, e.boxCols, variant, regions, cages,
                thermos, dots, arrows, sandwich,
            )
            PlaySnapshot(puzzle, current, notes, e.elapsedSeconds, e.hintsUsed)
                .takeIf(SnapshotValidation::isValid)
        } catch (t: Exception) {
            null
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        db.savedGameDao().clear()
    }

    override suspend fun hasSave(): Boolean = withContext(Dispatchers.IO) {
        db.savedGameDao().get() != null
    }

    override suspend fun recordStart(variant: VariantType, size: Int, difficulty: Difficulty) {
        withContext(Dispatchers.IO) { stats.recordStart(variant, size, difficulty) }
    }

    override suspend fun recordWin(variant: VariantType, size: Int, difficulty: Difficulty, elapsedSec: Long) {
        withContext(Dispatchers.IO) { stats.recordWin(variant, size, difficulty, elapsedSec) }
    }
}
