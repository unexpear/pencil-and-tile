package com.simplegamegen.sudoku.tabletop

data class SolitaireState(
    val columns: List<List<Int>>, val hidden: List<Int>, val stock: List<Int>,
    val waste: List<Int>, val foundations: List<List<Int>>, val drawCount: Int,
) : TableState {
    override val turn = 1
    override val result get() = if (foundations.sumOf { it.size } == 52) Result.WON else Result.PLAYING
    fun pile(index: Int): List<Int> = when (index) { in 0..6 -> columns[index]; 7 -> waste; in 8..11 -> foundations[index - 8]; else -> emptyList() }
    override fun legalMoves(): List<Move> = if (result != Result.PLAYING) emptyList() else buildList {
        if (stock.isNotEmpty() || waste.isNotEmpty()) add(Move(Op.DRAW))
        for (from in 0..11) {
            val source = pile(from)
            val max = if (from < 7) source.size - hidden[from] else minOf(1, source.size)
            for (count in 1..max) for (to in (0..6).toList() + (8..11).toList()) {
                val move = Move(Op.MOVE, from, to, count)
                if (canMove(move)) add(move)
            }
        }
    }
    private fun canMove(m: Move): Boolean {
        if (m.op != Op.MOVE || m.from !in 0..11 || m.to !in (0..6).toList() + (8..11).toList() || m.from == m.to) return false
        val source = pile(m.from)
        if (m.count !in 1..source.size || (m.from >= 7 && m.count != 1) || (m.from < 7 && m.count > source.size - hidden[m.from])) return false
        val moving = source.takeLast(m.count)
        if (!moving.zipWithNext().all { (a, b) -> rank(a) == rank(b) + 1 && red(a) != red(b) }) return false
        val first = moving.first(); val target = pile(m.to)
        return if (m.to >= 8) m.count == 1 && suit(first) == m.to - 8 && rank(first) == target.size + 1
        else if (target.isEmpty()) rank(first) == 13
        else rank(target.last()) == rank(first) + 1 && red(target.last()) != red(first)
    }
    override fun play(move: Move): SolitaireState? {
        if (result != Result.PLAYING) return null
        if (move == Move(Op.DRAW)) return when {
            stock.isNotEmpty() -> copy(stock = stock.drop(drawCount), waste = waste + stock.take(drawCount))
            waste.isNotEmpty() -> copy(stock = waste, waste = emptyList())
            else -> null
        }
        if (!canMove(move)) return null
        val piles = ((0..11).map { pile(it) }).toMutableList()
        val moving = piles[move.from].takeLast(move.count)
        piles[move.from] = piles[move.from].dropLast(move.count)
        piles[move.to] = piles[move.to] + moving
        val faceDown = hidden.toMutableList()
        if (move.from < 7) faceDown[move.from] = minOf(faceDown[move.from], (piles[move.from].size - 1).coerceAtLeast(0))
        return copy(columns = piles.take(7), hidden = faceDown, waste = piles[7], foundations = piles.drop(8))
    }
    companion object {
        fun rank(card: Int) = card % 13 + 1
        fun suit(card: Int) = card / 13
        fun red(card: Int) = suit(card) in 1..2
        fun label(card: Int): String = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")[card % 13] + listOf("♠", "♥", "♦", "♣")[suit(card)]
        fun deal(deck: List<Int>, draw: Int): SolitaireState {
            require(deck.sorted() == (0..51).toList() && draw in listOf(1, 3))
            var at = 0
            val columns = (1..7).map { count -> deck.subList(at, at + count).toList().also { at += count } }
            return SolitaireState(columns, (0..6).toList(), deck.drop(28), emptyList(), List(4) { emptyList() }, draw)
        }
    }
}
