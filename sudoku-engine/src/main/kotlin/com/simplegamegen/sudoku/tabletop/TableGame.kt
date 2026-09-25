package com.simplegamegen.sudoku.tabletop

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

enum class TableGame(val title: String, val settings: List<String>) {
    SOLITAIRE("Solitaire", listOf("Draw 1", "Draw 3")),
    MINES("Minesweeper", listOf("Easy", "Medium", "Hard", "Expert")),
    CHECKERS("Checkers", listOf("Easy", "Medium", "Hard", "Expert")),
    REVERSI("Reversi", listOf("Easy", "Medium", "Hard", "Expert")),
    DOMINOES("Dominoes", listOf("Easy", "Medium", "Hard", "Expert"));
    val opponent: Boolean get() = this in listOf(CHECKERS, REVERSI, DOMINOES)
}
enum class Result { PLAYING, WON, LOST, DRAW }
enum class Op { MOVE, DRAW, REVEAL, FLAG, CHORD, PASS }
data class Move(val op: Op, val from: Int = 0, val to: Int = 0, val count: Int = 1)
sealed interface TableState {
    val turn: Int
    val result: Result
    fun legalMoves(): List<Move>
    fun play(move: Move): TableState?
}

/** Exact initial permutation and replay-validated actions survive RNG/library changes. */
data class TableMatch private constructor(
    val game: TableGame, val setting: Int, val seed: Long, val layout: List<Int>,
    val moves: List<Move>, val state: TableState,
) {
    fun play(move: Move): TableMatch? = state.play(move)?.let { copy(moves = moves + move, state = it) }
    fun rewind(count: Int): TableMatch = restore(game, setting, seed, layout, moves.take(count))
    companion object {
        fun create(game: TableGame, setting: Int, seed: Long): TableMatch {
            require(setting in game.settings.indices)
            val count = layoutSize(game, setting)
            return restore(game, setting, seed, (0 until count).shuffled(Random(seed)), emptyList())
        }
        fun restore(game: TableGame, setting: Int, seed: Long, layout: List<Int>, moves: List<Move>): TableMatch {
            require(setting in game.settings.indices)
            require(layout.sorted() == (0 until layoutSize(game, setting)).toList())
            var state: TableState = when (game) {
                TableGame.SOLITAIRE -> SolitaireState.deal(layout, if (setting == 0) 1 else 3)
                TableGame.MINES -> MinesState.new(setting, layout)
                TableGame.CHECKERS -> CheckersState.new()
                TableGame.REVERSI -> ReversiState.new()
                TableGame.DOMINOES -> DominoState.deal(layout)
            }
            moves.forEach { checkpoint(); state = requireNotNull(state.play(it)) { "Illegal saved move" } }
            return TableMatch(game, setting, seed, layout.toList(), moves.toList(), state)
        }
        private fun layoutSize(game: TableGame, setting: Int): Int = when (game) {
            TableGame.SOLITAIRE -> 52
            TableGame.DOMINOES -> 28
            TableGame.MINES -> MinesState.presets[setting].let { it.width * it.height }
            else -> 0
        }
    }
}

object TableSaveCodec {
    fun encode(m: TableMatch): String = listOf("1", m.game.name, m.setting.toString(), m.seed.toString(),
        m.layout.joinToString(","), m.moves.joinToString(";") { "${it.op.ordinal},${it.from},${it.to},${it.count}" }).joinToString("\n")
    fun decode(text: String): TableMatch? = try {
        require(text.length <= 2_000_000)
        val lines = text.split('\n'); require(lines.size == 6 && lines[0] == "1")
        val layout = if (lines[4].isEmpty()) emptyList() else lines[4].split(',').map(String::toInt)
        val moves = if (lines[5].isEmpty()) emptyList() else lines[5].split(';').map { row ->
            val n = row.split(',').map(String::toInt)
            require(n.size == 4 && n[0] in Op.entries.indices)
            Move(Op.entries[n[0]], n[1], n[2], n[3])
        }
        require(moves.size <= 100_000)
        TableMatch.restore(TableGame.valueOf(lines[1]), lines[2].toInt(), lines[3].toLong(), layout, moves)
    } catch (_: IllegalArgumentException) { null }
}
