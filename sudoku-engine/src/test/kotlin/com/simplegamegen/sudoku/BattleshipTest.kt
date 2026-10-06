package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.duels.Battleship
import com.simplegamegen.sudoku.duels.BattleshipAi
import com.simplegamegen.sudoku.duels.BattleshipCodec
import com.simplegamegen.sudoku.duels.Mark
import com.simplegamegen.sudoku.duels.Ship
import com.simplegamegen.sudoku.duels.ShipKind
import com.simplegamegen.sudoku.duels.Shot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BattleshipTest {
    private fun you() = listOf(
        Ship(ShipKind.CARRIER, 0, 0, true),
        Ship(ShipKind.BATTLESHIP, 2, 0, true),
        Ship(ShipKind.CRUISER, 4, 0, true),
        Ship(ShipKind.SUBMARINE, 6, 0, true),
        Ship(ShipKind.DESTROYER, 8, 0, true),
    )

    private fun enemy() = listOf(
        Ship(ShipKind.CARRIER, 0, 5, false),
        Ship(ShipKind.BATTLESHIP, 0, 7, false),
        Ship(ShipKind.CRUISER, 0, 9, false),
        Ship(ShipKind.SUBMARINE, 5, 5, true),
        Ship(ShipKind.DESTROYER, 7, 5, true),
    )

    private fun laid(setting: Int = 0, seed: Long = 7) = Battleship(setting, seed, you(), enemy())

    private fun miss(cell: Int) = Shot(cell, Mark.MISS, null)

    @Test fun `ships stay on the board, may touch, and cannot overlap or bend`() {
        assertEquals(listOf(5, 4, 3, 3, 2), ShipKind.entries.map { it.length })
        assertEquals(10, Battleship.SIZE)
        var game = Battleship.start(4, 1)
        assertTrue(game.placing)
        assertEquals(ShipKind.CARRIER, game.nextKind)
        assertNull(game.fire(0))
        assertNull(game.place(0, 6))
        assertNull(game.place(-1, 0))
        assertNull(game.place(0, 10))
        game = game.place(0, 0)!!
        assertEquals(listOf(0, 1, 2, 3, 4), game.you.single().cells)
        assertNull(game.place(0, 2))
        assertNull(game.place(0, 4))
        val touching = game.place(1, 0)
        assertNotNull(touching)
        assertEquals(ShipKind.BATTLESHIP, touching!!.you[1].kind)
        assertTrue(touching.you[0].cells.none { it in touching.you[1].cells })

        val turned = Battleship.start(4, 0).rotate()!!
        assertFalse(turned.horizontal)
        assertNull(turned.place(6, 0))
        val vertical = turned.place(0, 9)!!
        assertEquals(listOf(9, 19, 29, 39, 49), vertical.you.single().cells)
        assertNull(vertical.place(0, 9))

        assertThrows(IllegalArgumentException::class.java) { Ship(ShipKind.DESTROYER, 0, 9, true) }
        assertThrows(IllegalArgumentException::class.java) { Ship(ShipKind.CARRIER, 9, 0, false) }
    }

    @Test fun `randomize finishes a legal fleet and the same seed hides the same enemy`() {
        val first = Battleship.start(11, 2)
        val again = Battleship.start(11, 2)
        assertEquals(first.enemy, again.enemy)
        assertTrue(first.placing && first.you.isEmpty())
        val rolled = first.randomize()!!
        assertFalse(rolled.placing)
        assertEquals(ShipKind.entries.toList(), rolled.you.map { it.kind })
        val cells = rolled.you.flatMap { it.cells }
        assertEquals(17, cells.size)
        assertEquals(cells.size, cells.toSet().size)
        assertTrue(rolled.you.all { ship -> ship.cells.size == ship.kind.length })
        assertNull(rolled.randomize())
        assertNull(rolled.rotate())
        assertNull(rolled.place(0, 0))
        val other = first.copy(mix = rolled.mix).randomize()!!
        assertNotEquals(rolled.you, other.you)
        repeat(40) { n ->
            val enemy = Battleship.start(n.toLong(), n % 4).enemy
            val occupied = enemy.flatMap { it.cells }
            assertEquals(17, occupied.toSet().size)
            assertEquals(ShipKind.entries.map { it.length }, enemy.map { it.kind.length })
        }
    }

    @Test fun `a shot is a miss, a hit, or the shot that sinks a named ship`() {
        var game = laid()
        assertTrue(game.yourTurn)
        assertNull(game.fire(-1))
        assertNull(game.fire(100))
        val miss = game.fire(99)!!
        assertEquals(Mark.MISS, miss.salvo.single().mark)
        assertNull(miss.salvo.single().ship)
        assertEquals("Miss", miss.yourNews())
        assertTrue(miss.awaitingComputer)
        assertNull(miss.fire(98))
        val reply = miss.receive(99)!!
        assertEquals(Mark.MISS, reply.incoming.single().mark)
        assertEquals("The computer missed", reply.theirNews())
        assertNull(reply.receive(99))
        assertTrue(reply.yourTurn)

        val cruiser = enemy().first { it.kind == ShipKind.CRUISER }.cells
        game = laid()
        var last = game
        cruiser.forEachIndexed { i, cell ->
            if (i > 0) last = last.receive(80 + i)!!
            last = last.fire(cell)!!
            val shot = last.salvo.last()
            if (i < cruiser.lastIndex) {
                assertEquals(Mark.HIT, shot.mark)
                assertNull(shot.ship)
            } else {
                assertEquals(Mark.SUNK, shot.mark)
                assertEquals(ShipKind.CRUISER, shot.ship)
                assertEquals("You sank their Cruiser", last.yourNews())
            }
        }
        assertNull(last.fire(cruiser.first()))
        assertFalse(last.won)
    }

    @Test fun `sinking the enemy fleet wins and sinking yours loses`() {
        var game = laid()
        val targets = game.enemy.flatMap { it.cells }
        val quiet = (0 until Battleship.CELLS).filter { cell -> game.you.none { cell in it.cells } }
        targets.forEachIndexed { i, cell ->
            if (i > 0) game = game.receive(quiet[i])!!
            game = game.fire(cell)!!
        }
        assertTrue(game.won)
        assertFalse(game.lost)
        assertEquals(1, game.winner)
        assertTrue(game.over)
        assertNull(game.fire(99))
        assertNull(game.receive(0))
        assertEquals(game, BattleshipCodec.decode(BattleshipCodec.encode(game)))

        var lost = laid(seed = 3)
        val yours = lost.you.flatMap { it.cells }
        val empty = (0 until Battleship.CELLS).filter { cell -> lost.enemy.none { cell in it.cells } }
        yours.forEachIndexed { i, cell ->
            lost = lost.fire(empty[i])!!
            lost = lost.receive(cell)!!
            if (i == yours.lastIndex) assertEquals(Mark.SUNK, lost.incoming.last().mark)
        }
        assertTrue(lost.lost)
        assertFalse(lost.won)
        assertEquals(-1, lost.winner)
        assertEquals("The computer sank your Destroyer", lost.theirNews())
        assertNull(lost.fire(empty[yours.size]))
        assertEquals(lost, BattleshipCodec.decode(BattleshipCodec.encode(lost)))
    }

    @Test fun `saves round-trip a half-placed fleet and reject illegal shots`() {
        var game = Battleship.start(9, 3).place(1, 1)!!.rotate()!!
        assertEquals(game, BattleshipCodec.decode(BattleshipCodec.encode(game)))
        game = game.randomize()!!.fire(0)!!
        val restored = BattleshipCodec.decode(BattleshipCodec.encode(game))
        assertEquals(game, restored)
        assertEquals(game.enemy, restored!!.enemy)

        assertNull(BattleshipCodec.decode("bad"))
        assertNull(BattleshipCodec.decode(BattleshipCodec.encode(game).replaceFirst("1", "2")))
        val lines = BattleshipCodec.encode(laid()).split('\n').toMutableList()
        lines[7] = "5,MISS,"
        assertNull(BattleshipCodec.decode(lines.joinToString("\n")))
        lines[7] = "99,HIT,"
        assertNull(BattleshipCodec.decode(lines.joinToString("\n")))
        lines[5] = "CARRIER,0,0,1;BATTLESHIP,0,0,1;CRUISER,4,0,1;SUBMARINE,6,0,1;DESTROYER,8,0,1"
        lines[7] = ""
        assertNull(BattleshipCodec.decode(lines.joinToString("\n")))
        assertNull(BattleshipCodec.decode(listOf("1", "0", "1", "1", "0", "", lines[6], "4,HIT,", "").joinToString("\n")))
        assertThrows(IllegalArgumentException::class.java) {
            Battleship(4, 1, you(), enemy())
        }
    }

    @Test fun `every difficulty fires only at a square that is still hidden`() {
        for (setting in 0..3) {
            var game = Battleship.start(20L + setting, setting).randomize()!!
            val empties = (0 until Battleship.CELLS).filter { cell -> game.enemy.none { cell in it.cells } }
            repeat(12) { n ->
                if (game.over) return@repeat
                game = game.fire(empties[n])!!
                if (game.over) return@repeat
                val shot = BattleshipAi.choose(game)
                assertTrue(shot !in game.incoming.map { it.cell }, "setting $setting shot $shot twice")
                assertTrue(shot in 0 until Battleship.CELLS)
                game = game.receive(shot)!!
            }
        }
    }

    @Test fun `medium hunts a neighbor, hard extends a line, and expert prefers the likely square`() {
        val beside = aiTurn(1, listOf(miss(99), miss(98)), listOf(Shot(1, Mark.HIT, null)))
        val medium = BattleshipAi.choose(beside)
        assertTrue(medium in setOf(0, 2, 11), "medium fired at $medium")

        val line = aiTurn(2, listOf(miss(99), miss(98), miss(97)), listOf(Shot(1, Mark.HIT, null), Shot(2, Mark.HIT, null)))
        val hard = BattleshipAi.choose(line)
        assertTrue(hard in setOf(0, 3), "hard fired at $hard")

        val mediumLine = aiTurn(1, listOf(miss(99), miss(98), miss(97)), listOf(Shot(1, Mark.HIT, null), Shot(2, Mark.HIT, null)))
        val extended = BattleshipAi.choose(mediumLine)
        assertTrue(extended in setOf(0, 3), "medium fired at $extended")

        val parity = (0 until 20).map { seed ->
            val game = aiTurn(2, listOf(miss(99)), emptyList(), seed = seed.toLong())
            BattleshipAi.choose(game)
        }
        assertTrue(parity.all { cell -> (cell / 10 + cell % 10) % 2 == 0 }, parity.toString())

        val aimed = aiTurn(3, listOf(miss(99), miss(98), miss(97)), listOf(Shot(1, Mark.HIT, null), Shot(2, Mark.HIT, null)))
        assertEquals(3, BattleshipAi.choose(aimed))

        var neighbors = 0
        repeat(40) { seed ->
            val game = aiTurn(0, listOf(miss(99), miss(98)), listOf(Shot(1, Mark.HIT, null)), seed = seed.toLong())
            if (BattleshipAi.choose(game) in setOf(0, 2, 11)) neighbors++
        }
        assertTrue(neighbors in 1..20, "easy followed up $neighbors times")
    }

    private fun aiTurn(setting: Int, salvo: List<Shot>, incoming: List<Shot>, seed: Long = 7) =
        Battleship(setting, seed, you(), enemy(), salvo, incoming)
}
