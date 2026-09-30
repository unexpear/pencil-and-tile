package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.FiveRow
import com.simplegamegen.sudoku.duels.FiveRowAi
import com.simplegamegen.sudoku.duels.FiveRowCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FiveRowTest {
    @Test fun `five across wins and a longer line still counts`() {
        var game = FiveRow.start(1, 0)
        val row = FiveRow.SIZE / 2
        for (col in 0 until 4) {
            game = game.place(row * FiveRow.SIZE + col)!!
            game = game.place((row + 1) * FiveRow.SIZE + col)!!
        }
        assertEquals(0, game.winner)
        game = game.place(row * FiveRow.SIZE + 4)!!
        assertEquals(1, game.winner)
        assertTrue(game.over)
        assertEquals(5, game.winningCells().size)
        assertNull(game.place(0))
        assertEquals(game, FiveRowCodec.decode(FiveRowCodec.encode(game)))
    }

    @Test fun `five down or on a diagonal wins, and a full board can draw`() {
        var down = FiveRow.start(2, 0)
        for (row in 0 until 4) {
            down = down.place(row * FiveRow.SIZE + 3)!!
            down = down.place(row * FiveRow.SIZE + 4)!!
        }
        down = down.place(4 * FiveRow.SIZE + 3)!!
        assertEquals(1, down.winner)

        var diagonal = FiveRow.start(3, 0)
        for (i in 0 until 4) {
            diagonal = diagonal.place(i * FiveRow.SIZE + i)!!
            diagonal = diagonal.place(i * FiveRow.SIZE + i + 1)!!
        }
        diagonal = diagonal.place(4 * FiveRow.SIZE + 4)!!
        assertEquals(1, diagonal.winner)
        assertTrue(diagonal.winningCells().contains(4 * FiveRow.SIZE + 4))

        val cells = List(FiveRow.CELLS) { index ->
            val row = index / FiveRow.SIZE
            val col = index % FiveRow.SIZE
            if ((row + 2 * col) % 4 < 2) 1 else -1
        }
        val draw = FiveRow(0, 1, cells, turn = -1, winner = 0)
        assertTrue(draw.draw)
        assertNull(draw.place(0))
    }

    @Test fun `medium takes the win and blocks an open four`() {
        val row = 5
        val cells = MutableList(FiveRow.CELLS) { 0 }
        for (col in 0 until 4) cells[row * FiveRow.SIZE + col] = 1
        for (col in 0 until 3) cells[(row + 1) * FiveRow.SIZE + col] = -1
        val threat = FiveRow(1, 4, cells, turn = -1)
        assertEquals(row * FiveRow.SIZE + 4, FiveRowAi.choose(threat))

        val mine = MutableList(FiveRow.CELLS) { 0 }
        for (col in 0 until 4) {
            mine[row * FiveRow.SIZE + col] = 1
            mine[(row + 1) * FiveRow.SIZE + col] = -1
        }
        val win = FiveRow(1, 4, mine, turn = 1)
        assertEquals(row * FiveRow.SIZE + 4, FiveRowAi.choose(win))
    }

    @Test fun `occupied squares and broken saves are rejected`() {
        val game = FiveRow.start(1, 1).place(FiveRow.CELLS / 2)!!
        assertNull(game.place(FiveRow.CELLS / 2))
        assertNull(game.place(-1))
        assertNull(FiveRowCodec.decode("nope"))
        assertEquals(FiveRow.CELLS / 2, FiveRowAi.choose(FiveRow.start(8, 0)))
    }
}
