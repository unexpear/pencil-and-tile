package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.tabletop.*
import com.simplegamegen.sudoku.tabletop.Result as GameResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TableRulesTest {
    @Test fun `solitaire deals contain all 52 cards and seven correctly covered columns`() {
        repeat(100) { seed -> for (level in 0..1) {
            val s = TableMatch.create(TableGame.SOLITAIRE, level, seed.toLong()).state as SolitaireState
            assertEquals((1..7).toList(), s.columns.map { it.size })
            assertEquals((0..6).toList(), s.hidden)
            assertEquals((0..51).toList(), (s.columns.flatten() + s.stock).sorted())
            val drawn = s.play(Move(Op.DRAW))!!
            assertEquals(if (level == 0) 1 else 3, drawn.waste.size)
            assertEquals(s.stock.take(drawn.drawCount), drawn.waste)
        } }
    }
    @Test fun `stock recycling preserves card order and unavailable draw rejects`() {
        val s = SolitaireState.deal((0..51).toList(), 3)
        var next = s
        repeat(8) { next = next.play(Move(Op.DRAW))!! }
        assertTrue(next.stock.isEmpty())
        next = next.play(Move(Op.DRAW))!!
        assertEquals(s, next)
        assertNull(s.copy(stock = emptyList(), waste = emptyList()).play(Move(Op.DRAW)))
    }
    @Test fun `solitaire enforces suit rank color kings and reveals hidden cards`() {
        val s = SolitaireState(List(7) { emptyList() }, List(7) { 0 }, emptyList(), listOf(0), List(4) { emptyList() }, 1)
        assertNull(s.play(Move(Op.MOVE, 7, 9)))
        val ace = s.play(Move(Op.MOVE, 7, 8))!!
        assertEquals(listOf(0), ace.foundations[0])
        assertNull(s.play(Move(Op.MOVE, 7, 0)))
        assertNotNull(s.copy(waste = listOf(12)).play(Move(Op.MOVE, 7, 0)))
        val columns = listOf(listOf(5, 17, 3), listOf(31)) + List(5) { emptyList<Int>() }
        val stacked = s.copy(columns = columns, hidden = listOf(1, 0, 0, 0, 0, 0, 0), waste = emptyList())
        // Red 5 + black 4 cannot move onto red 6; black 4 can move onto red 5 elsewhere.
        assertNull(stacked.play(Move(Op.MOVE, 0, 1, 2)))
        val movable = stacked.copy(columns = listOf(listOf(5, 17, 3), listOf(5)) + List(5) { emptyList() })
        val moved = movable.play(Move(Op.MOVE, 0, 1, 2))!!
        assertEquals(0, moved.hidden[0])
        assertEquals(listOf(5, 17, 3), moved.columns[1])
        assertNull(stacked.play(Move(Op.MOVE, 0, 1, 4)))
    }
    @Test fun `all mine presets guarantee empty opening neighborhood and exact mine counts`() {
        for (level in 0..3) repeat(30) { seed ->
            val initial = TableMatch.create(TableGame.MINES, level, seed.toLong()).state as MinesState
            val first = if (seed % 2 == 0) 0 else initial.order.size / 2 + initial.preset.width / 2
            val opened = initial.play(Move(Op.REVEAL, first))!!
            assertEquals(initial.preset.mines, opened.mines.size)
            assertFalse(first in opened.mines)
            assertTrue(opened.neighbors(first).none { it in opened.mines })
            assertTrue(opened.revealed.none { it in opened.mines })
            assertEquals(0, opened.number(first))
        }
    }
    @Test fun `mine flags prevent opening but wrong flags can detonate a chord`() {
        val s = MinesState(MinePreset(3, 3, 1), (0..8).toList(), mines = setOf(0), revealed = setOf(4))
        val flagged = s.play(Move(Op.FLAG, 2))!!
        assertNull(flagged.play(Move(Op.REVEAL, 2)))
        val hit = flagged.play(Move(Op.CHORD, 4))!!
        assertEquals(GameResult.LOST, hit.result)
        assertEquals(0, hit.exploded)
        assertNull(hit.play(Move(Op.FLAG, 5)))
        val safe = s.copy(flags = setOf(0)).play(Move(Op.CHORD, 4))!!
        assertEquals(GameResult.WON, safe.result)
    }
    @Test fun `mines deductions agree with reality and do not trust incorrect flags`() {
        for (level in 0..3) repeat(20) { seed ->
            var s = (TableMatch.create(TableGame.MINES, level, seed.toLong()).state as MinesState).play(Move(Op.REVEAL, 0))!!
            repeat(20) {
                val hint = s.deduction()
                if (hint != null) {
                    assertEquals(hint.mine, hint.cell in s.mines)
                    s = s.play(Move(if (hint.mine) Op.FLAG else Op.REVEAL, hint.cell))!!
                }
            }
        }
        val uncertain = MinesState(MinePreset(3, 3, 1), (0..8).toList(), mines = setOf(0), revealed = setOf(4), flags = setOf(2))
        assertNull(uncertain.deduction())
    }
    @Test fun `checkers forces captures and continuation with the same man`() {
        val board = MutableList(64) { 0 }.also { it[40] = 1; it[33] = -1; it[19] = -1; it[46] = 1; it[1] = -1 }
        val s = CheckersState(board)
        assertEquals(listOf(Move(Op.MOVE, 40, 26)), s.legalMoves())
        assertNull(s.play(Move(Op.MOVE, 46, 37)))
        val jump = s.play(s.legalMoves().single())!!
        assertEquals(1, jump.turn); assertEquals(26, jump.forced); assertEquals(0, jump.board[33])
        assertEquals(listOf(Move(Op.MOVE, 26, 12)), jump.legalMoves())
        val end = jump.play(jump.legalMoves().single())!!
        assertEquals(-1, end.turn); assertNull(end.forced); assertEquals(0, end.board[19])
    }
    @Test fun `checkers crowning ends capture turn and kings capture backward`() {
        val board = MutableList(64) { 0 }.also { it[17] = 1; it[10] = -1; it[12] = -1 }
        val crowned = CheckersState(board).play(Move(Op.MOVE, 17, 3))!!
        assertEquals(2, crowned.board[3]); assertEquals(-1, crowned.turn); assertNull(crowned.forced)
        val king = CheckersState(MutableList(64) { 0 }.also { it[26] = 2; it[35] = -1 })
        assertNotNull(king.play(Move(Op.MOVE, 26, 44)))
        assertEquals(GameResult.WON, king.play(Move(Op.MOVE, 26, 44))!!.result)
    }
    @Test fun `checkers repetition and quiet turns draw automatically`() {
        val board = MutableList(64) { 0 }.also { it[1] = 2; it[62] = -2 }
        var s = CheckersState(board)
        repeat(3) {
            for (move in listOf(Move(Op.MOVE, 1, 8), Move(Op.MOVE, 62, 55), Move(Op.MOVE, 8, 1), Move(Op.MOVE, 55, 62))) {
                if (s.result == GameResult.PLAYING) s = s.play(move)!!
            }
        }
        assertEquals(GameResult.DRAW, s.result)
        assertEquals(GameResult.DRAW, CheckersState(board, quiet = 79).play(Move(Op.MOVE, 1, 8))!!.result)
    }
    @Test fun `reversi opening and directional flips are correct`() {
        val s = ReversiState.new()
        assertEquals(setOf(19, 26, 37, 44), s.legalMoves().map { it.from }.toSet())
        assertNull(s.play(Move(Op.MOVE, 0)))
        val next = s.play(Move(Op.MOVE, 19))!!
        assertEquals(4, next.board.count { it == 1 }); assertEquals(1, next.board.count { it == -1 }); assertEquals(-1, next.turn)
    }
    @Test fun `reversi finishes random games and skips only unplayable turns`() {
        var skipped = false
        repeat(40) { seed ->
            val rng = Random(seed); var s = ReversiState.new(); var moves = 0
            while (s.result == GameResult.PLAYING) {
                val next = s.play(s.legalMoves().random(rng))!!
                if (next.result == GameResult.PLAYING && next.turn == s.turn) {
                    skipped = true
                    assertTrue(next.copy(turn = -next.turn).legalMoves().isEmpty())
                }
                s = next; assertTrue(++moves <= 60)
            }
            assertTrue(s.copy(result = GameResult.PLAYING).legalMoves().isEmpty())
            assertTrue(s.copy(result = GameResult.PLAYING, turn = -s.turn).legalMoves().isEmpty())
            assertEquals(when { s.board.sum() > 0 -> GameResult.WON; s.board.sum() < 0 -> GameResult.LOST; else -> GameResult.DRAW }, s.result)
        }
        assertTrue(skipped)
    }
    @Test fun `domino games preserve all tiles match ends and finish`() {
        repeat(100) { seed ->
            val rng = Random(seed); var s = TableMatch.create(TableGame.DOMINOES, 0, seed.toLong()).state as DominoState
            assertEquals(listOf(7, 7), s.hands.map { it.size })
            var moves = 0
            while (s.result == GameResult.PLAYING) {
                s = s.play(s.legalMoves().random(rng))!!
                assertEquals(28, s.hands.sumOf { it.size } + s.boneyard.size + s.chain.size)
                assertTrue(s.chain.zipWithNext().all { (a, b) -> a.b == b.a })
                assertTrue(++moves < 100)
            }
        }
    }
    @Test fun `domino drawing passing and blocked scores obey the selected rules`() {
        val noFit = DominoState(listOf(listOf(0), listOf(1)), emptyList(), listOf(Domino(6, 6)))
        assertEquals(listOf(Move(Op.PASS)), noFit.legalMoves())
        assertEquals(GameResult.WON, noFit.play(Move(Op.PASS))!!.play(Move(Op.PASS))!!.result)
        val drawing = noFit.copy(boneyard = listOf(6))
        assertEquals(listOf(Move(Op.DRAW)), drawing.legalMoves())
        val drawn = drawing.play(Move(Op.DRAW))!!
        assertEquals(1, drawn.turn); assertEquals(listOf(0, 6), drawn.hands[0]); assertTrue(drawn.boneyard.isEmpty())
        assertNull(drawn.play(Move(Op.DRAW)))
    }
    @Test fun `computer choices are legal and domino strategy cannot see hidden opponent tiles`() {
        for (game in listOf(TableGame.CHECKERS, TableGame.REVERSI, TableGame.DOMINOES)) for (level in 0..3) {
            val s = TableMatch.create(game, level, 55).state
            assertTrue(TableAi.choose(s, level, 12) in s.legalMoves())
        }
        val domino = TableMatch.create(TableGame.DOMINOES, 0, 44).state as DominoState
        val changed = domino.copy(hands = listOf(domino.hands[0], domino.boneyard.take(7)), boneyard = domino.hands[1] + domino.boneyard.drop(7))
        for (level in 0..3) assertEquals(TableAi.choose(domino, level, 9), TableAi.choose(changed, level, 9))
    }
    @Test fun `exact layouts and legal histories restore all games and settings`() {
        for (game in TableGame.entries) for (level in game.settings.indices) {
            var match = TableMatch.create(game, level, 99)
            val rng = Random(3)
            repeat(30) { if (match.state.legalMoves().isNotEmpty()) match = match.play(match.state.legalMoves().random(rng))!! }
            val encoded = TableSaveCodec.encode(match)
            assertEquals(match, TableSaveCodec.decode(encoded))
            assertEquals(match.rewind(0), TableMatch.create(game, level, 99))
            assertNull(TableSaveCodec.decode(encoded + ";0,999,999,1"))
        }
        val encoded = TableSaveCodec.encode(TableMatch.create(TableGame.SOLITAIRE, 0, 2))
        val lines = encoded.split('\n').toMutableList(); lines[4] = List(52) { 0 }.joinToString(",")
        assertNull(TableSaveCodec.decode(lines.joinToString("\n")))
        assertNull(TableSaveCodec.decode("bad"))
    }
    @Test fun `capacity totals distinguish rules settings and distinct hangman answers`() {
        assertEquals(36, CollectionGuide.entries.size)
        assertEquals(46, CollectionGuide.entries.sumOf { it.variants })
        assertEquals(330, CollectionGuide.entries.sumOf { it.setups })
        assertTrue(CollectionGuide.hangmanWords > 100)
        println("Distinct Hangman answers: ${CollectionGuide.hangmanWords}")
    }
}
