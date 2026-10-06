package com.simplegamegen.sudoku.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.simplegamegen.sudoku.generator.Generator
import com.simplegamegen.sudoku.logic.LogicGenerator
import com.simplegamegen.sudoku.logic.LogicKind
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.logic.LogicPuzzle
import com.simplegamegen.sudoku.logic.LogicVerifier
import com.simplegamegen.sudoku.mahjong.*
import com.simplegamegen.sudoku.model.*
import com.simplegamegen.sudoku.validation.PuzzleVerifier
import com.simplegamegen.sudoku.words.*
import com.simplegamegen.sudoku.tabletop.TableGame
import com.simplegamegen.sudoku.tabletop.TableMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import kotlin.random.Random

interface PuzzleLedger {
    suspend fun nextSeed(): Long
    suspend fun recent(key: String): List<String>
    suspend fun remember(key: String, fingerprint: String, window: Int = 32): Boolean
}

interface ArcadeStore {
    suspend fun load(key: String): String?
    suspend fun save(key: String, encoded: String)

    /**
     * Removes saved games and finished-game progress (trail, daily, discovered words, earned heroes).
     * Leaves the player's own grids, Blotwords themes and the Wordsworn blood display choice.
     */
    suspend fun clearPlaySaves() {}
}

class CollectionStore(private val data: DataStore<Preferences>) : PuzzleLedger, ArcadeStore {
    override suspend fun nextSeed(): Long {
        var seed = 0L
        data.edit { prefs ->
            val key = longPreferencesKey("generation_sequence")
            seed = (prefs[key] ?: Random.nextLong()) + 1
            prefs[key] = seed
        }
        return seed
    }
    override suspend fun recent(key: String): List<String> = data.data.first()[stringPreferencesKey("recent:$key")]
        ?.split('|')?.filter(String::isNotEmpty).orEmpty()
    override suspend fun remember(key: String, fingerprint: String, window: Int): Boolean {
        require(window in 1..32 && '|' !in fingerprint)
        var accepted = false
        data.edit { prefs ->
            val k = stringPreferencesKey("recent:$key")
            val old = prefs[k]?.split('|')?.filter(String::isNotEmpty).orEmpty()
            if (fingerprint !in old) {
                prefs[k] = (old + fingerprint).takeLast(window).joinToString("|")
                accepted = true
            }
        }
        return accepted
    }
    override suspend fun load(key: String): String? = data.data.first()[stringPreferencesKey("save:$key")]
    override suspend fun save(key: String, encoded: String) { data.edit { it[stringPreferencesKey("save:$key")] = encoded } }

    override suspend fun clearPlaySaves() {
        data.edit { prefs ->
            val drop = prefs.asMap().keys.filter { key ->
                val name = key.name
                name.startsWith(SAVE_PREFIX) && name.removePrefix(SAVE_PREFIX) !in KEPT_SAVES
            }
            drop.forEach { prefs.remove(it) }
        }
    }

    companion object {
        private const val SAVE_PREFIX = "save:"

        /**
         * Collection keys that are not a started or finished game.
         * Cleared keys include `PLAY_*`, `TABLE_*`, `LOGIC_*`, `HANGMAN`, `MAHJONG`,
         * `PLAY_SUDOKU_GRID`, `blotwords:trail`, `blotwords:known`, `blotwords:daily`
         * and `wordsworn:core-unlock:*`. `recent:*` and `generation_sequence` stay.
         */
        val KEPT_SAVES = setOf("GRID_DRAFTS", "blotwords:themes", "blotwords:theme", "wordsworn:blood")
    }
}

/** An open-ended sequence, with proof checks and a bounded persistent repeat window. */
class PuzzleFactory(private val ledger: PuzzleLedger, private val worker: CoroutineDispatcher = Dispatchers.Default) {
    suspend fun table(game: TableGame, setting: Int): TableMatch {
        require(setting in game.settings.indices)
        // Competitive board games intentionally retain their standard opening.
        if (game == TableGame.CHECKERS || game == TableGame.REVERSI)
            return TableMatch.create(game, setting, ledger.nextSeed())
        return fresh("table:$game:$setting", generate = { TableMatch.create(game, setting, it) },
            verify = { it.moves.isEmpty() }, fingerprint = {
                val content = if (game == TableGame.DOMINOES) listOf(it.layout.take(7).sorted(), it.layout.drop(7).take(7).sorted(), it.layout.drop(14)).flatten() else it.layout
                hash(content.joinToString(","))
            })
    }

    suspend fun sudoku(size: Int, difficulty: Difficulty, variant: VariantType): Puzzle = fresh(
        "sudoku:$variant:$size:$difficulty",
        generate = { seed -> when (variant) {
            VariantType.CLASSIC -> Generator.generateClassic(size, difficulty, seed)
            VariantType.DIAGONAL_X -> Generator.generateDiagonalX(size, difficulty, seed)
            VariantType.JIGSAW -> Generator.generateJigsaw(size, difficulty, seed)
            VariantType.KILLER -> Generator.generateKiller(size, difficulty, seed)
            VariantType.THERMO -> Generator.generateThermo(size, difficulty, seed)
            VariantType.KROPKI -> Generator.generateKropki(size, difficulty, seed)
            VariantType.ARROW -> Generator.generateArrow(size, difficulty, seed)
            VariantType.SANDWICH -> Generator.generateSandwich(size, difficulty, seed)
        } }, verify = { PuzzleVerifier.verify(it).valid }, fingerprint = { p ->
            hash(listOf(p.variant, p.size, p.givens.joinToString(","), p.regions?.joinToString(","),
                p.cages?.joinToString(";") { "${it.sum}:${it.cells.joinToString(",")}" }, p.thermos, p.dots, p.arrows,
                p.sandwich?.let { listOf(it.top.joinToString(), it.bottom.joinToString(), it.left.joinToString(), it.right.joinToString()) }).joinToString("|"))
        })

    suspend fun word(game: WordGame, theme: Int, difficulty: WordDifficulty): WordPuzzle = fresh(
        "word:$game:${if (game == WordGame.CROSSWORD) 0 else theme}:$difficulty",
        generate = { if (game == WordGame.CROSSWORD) WordPuzzles.crossword(it, difficulty) else WordPuzzles.wordSearch(it, theme, difficulty) },
        verify = { WordVerifier.verify(it) }, fingerprint = { hash(WordSaveCodec.encode(WordProgress(it))) })

    suspend fun hangman(theme: Int, difficulty: WordDifficulty): HangmanPuzzle {
        val key = "hangman:$theme:$difficulty"
        val pool = Hangman.words(theme, difficulty)
        return timed {
            repeat(12) {
                val recent = ledger.recent(key).toSet()
                val puzzle = Hangman.generate(ledger.nextSeed(), theme, difficulty, recent)
                if (ledger.remember(key, puzzle.answer, minOf(32, pool.size - 1).coerceAtLeast(1))) return@timed puzzle
            }
            error("Couldn't reserve a fresh word")
        }
    }

    suspend fun logic(kind: LogicKind, level: LogicLevel): LogicPuzzle = fresh("logic:$kind:$level",
        generate = { LogicGenerator.generate(kind, level, it) },
        verify = { LogicVerifier.verify(it) },
        fingerprint = { p -> hash(listOf(p.givens, p.solution, p.rules.open, p.rules.cages, p.rules.runs).joinToString("|")) })

    /** Any seeded game: fresh seed, off-main-thread generation, verification and recent-repeat avoidance. */
    suspend fun <T> custom(key: String, generate: (Long) -> T, verify: (T) -> Boolean = { true }, fingerprint: (T) -> String): T =
        fresh(key, generate, verify) { hash(fingerprint(it)) }

    suspend fun mahjong(difficulty: WordDifficulty): MahjongDeal = fresh("mahjong:$difficulty",
        generate = { MahjongGenerator.generate(it, difficulty) },
        verify = { MahjongRules.replay(it.tiles, it.solution)?.size == it.tiles.size },
        fingerprint = { hash(it.tiles.joinToString(";") { t -> "${t.x},${t.y},${t.z},${t.face}" }) })

    private suspend fun <T> fresh(key: String, generate: (Long) -> T, verify: (T) -> Boolean, fingerprint: (T) -> String): T = timed {
        repeat(12) {
            val seed = ledger.nextSeed()
            val candidate = try {
                runInterruptible(worker) { generate(seed).also { check(verify(it)) { "Verification failed" } } }
            } catch (e: java.util.concurrent.CancellationException) { throw e
            } catch (_: IllegalStateException) { return@repeat }
            if (ledger.remember(key, fingerprint(candidate))) return@timed candidate
        }
        error("Couldn't produce a fresh verified puzzle")
    }

    private suspend fun <T> timed(block: suspend () -> T): T = try { withTimeout(30_000) { block() } }
        catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw IllegalStateException("Puzzle generation timed out; try again")
        }

    private fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
