package com.simplegamegen.sudoku.tabletop

data class Domino(val a: Int, val b: Int) { val pips get() = a + b; fun flipped() = Domino(b, a) }
/** Single-hand Draw game: 7 tiles each, draw until playable, entire boneyard available. */
data class DominoState(
    val hands: List<List<Int>>, val boneyard: List<Int>, val chain: List<Domino> = emptyList(),
    override val turn: Int = 1, val passes: Int = 0, override val result: Result = Result.PLAYING,
) : TableState {
    val player get() = if (turn == 1) 0 else 1
    fun placements(): List<Move> = buildList {
        for (id in hands[player]) {
            val tile = tiles[id]
            if (chain.isEmpty()) add(Move(Op.MOVE, id, 0))
            else {
                if (chain.first().a in listOf(tile.a, tile.b)) add(Move(Op.MOVE, id, 0))
                if (chain.last().b in listOf(tile.a, tile.b)) add(Move(Op.MOVE, id, 1))
            }
        }
    }
    override fun legalMoves(): List<Move> {
        if (result != Result.PLAYING) return emptyList()
        return placements().ifEmpty { listOf(Move(if (boneyard.isNotEmpty()) Op.DRAW else Op.PASS)) }
    }
    override fun play(move: Move): DominoState? {
        if (move !in legalMoves()) return null
        if (move.op == Op.DRAW) {
            val next = hands.toMutableList(); next[player] = next[player] + boneyard.first()
            return copy(hands = next, boneyard = boneyard.drop(1), passes = 0)
        }
        if (move.op == Op.PASS) {
            val next = copy(turn = -turn, passes = passes + 1)
            if (next.passes < 2) return next
            val scores = hands.map { hand -> hand.sumOf { tiles[it].pips } }
            return next.copy(result = when { scores[0] < scores[1] -> Result.WON; scores[0] > scores[1] -> Result.LOST; else -> Result.DRAW })
        }
        val tile = tiles[move.from]
        val line = when {
            chain.isEmpty() -> listOf(tile)
            move.to == 0 -> listOf(if (tile.b == chain.first().a) tile else tile.flipped()) + chain
            else -> chain + if (tile.a == chain.last().b) tile else tile.flipped()
        }
        val next = hands.toMutableList(); next[player] = next[player] - move.from
        return copy(hands = next, chain = line, turn = -turn, passes = 0,
            result = if (next[player].isEmpty()) if (turn == 1) Result.WON else Result.LOST else Result.PLAYING)
    }
    companion object {
        val tiles = (0..6).flatMap { a -> (a..6).map { b -> Domino(a, b) } }
        fun deal(order: List<Int>): DominoState {
            require(order.sorted() == (0..27).toList())
            return DominoState(listOf(order.take(7), order.drop(7).take(7)), order.drop(14))
        }
    }
}
