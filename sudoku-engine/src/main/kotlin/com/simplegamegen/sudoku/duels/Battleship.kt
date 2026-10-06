package com.simplegamegen.sudoku.duels

import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

/** A standard ship. [length] is how many squares it occupies, in a straight line. */
enum class ShipKind(val length: Int, val title: String) {
    CARRIER(5, "Carrier"),
    BATTLESHIP(4, "Battleship"),
    CRUISER(3, "Cruiser"),
    SUBMARINE(3, "Submarine"),
    DESTROYER(2, "Destroyer"),
}

/**
 * One ship on a 10×10 grid. [row] and [col] are the bow (the end toward the top or the left).
 * [horizontal] ships run to the right; vertical ships run downward. Ships may touch, but their
 * squares cannot overlap, and a ship cannot leave the board or bend.
 */
data class Ship(val kind: ShipKind, val row: Int, val col: Int, val horizontal: Boolean) {
    val cells: List<Int> = List(kind.length) { i ->
        (row + if (horizontal) 0 else i) * Battleship.SIZE + col + if (horizontal) i else 0
    }

    init {
        require(row in 0 until Battleship.SIZE && col in 0 until Battleship.SIZE) { "bow off the board" }
        val endRow = row + if (horizontal) 0 else kind.length - 1
        val endCol = col + if (horizontal) kind.length - 1 else 0
        require(endRow in 0 until Battleship.SIZE && endCol in 0 until Battleship.SIZE) { "ship leaves the board" }
    }
}

/** What a shot found. [ship] is set only when this shot sinks that ship. */
enum class Mark { MISS, HIT, SUNK }

data class Shot(val cell: Int, val mark: Mark, val ship: ShipKind?) {
    init {
        require(cell in 0 until Battleship.CELLS)
        require((mark == Mark.SUNK) == (ship != null))
    }
}

/**
 * Classic Battleship, human against the computer. Each side has the standard fleet.
 * You place your ships first (the computer's fleet is already hidden), then you fire first.
 * [setting] is the computer's strength. Squares are `row * 10 + column`, row 0 at the top.
 */
data class Battleship(
    val setting: Int,
    val seed: Long,
    val you: List<Ship>,
    val enemy: List<Ship>,
    val salvo: List<Shot> = emptyList(),
    val incoming: List<Shot> = emptyList(),
    val horizontal: Boolean = true,
    val mix: Int = 0,
) {
    init {
        require(setting in NAMES.indices && mix >= 0)
        require(fleetOk(enemy, complete = true))
        require(fleetOk(you, complete = false))
        require(you.map { it.kind } == ShipKind.entries.take(you.size))
        require(shotsOk(enemy, salvo) && shotsOk(you, incoming))
        if (placing) require(salvo.isEmpty() && incoming.isEmpty())
        else {
            val enemyDown = fleetDown(enemy, salvo)
            val youDown = fleetDown(you, incoming)
            require(!enemyDown || !youDown)
            when {
                enemyDown -> require(salvo.size == incoming.size + 1)
                youDown -> require(salvo.size == incoming.size)
                else -> require(salvo.size == incoming.size || salvo.size == incoming.size + 1)
            }
        }
    }

    val placing: Boolean get() = you.size < FLEET
    val nextKind: ShipKind? get() = if (placing) ShipKind.entries[you.size] else null
    val won: Boolean get() = !placing && fleetDown(enemy, salvo)
    val lost: Boolean get() = !placing && fleetDown(you, incoming)
    val over: Boolean get() = won || lost
    val winner: Int get() = when { won -> 1; lost -> -1; else -> 0 }
    val yourTurn: Boolean get() = !placing && !over && salvo.size == incoming.size
    val awaitingComputer: Boolean get() = !placing && !over && salvo.size == incoming.size + 1

    /** True when the next ship can sit with its bow at [row], [col] in the current direction. */
    fun canPlace(row: Int, col: Int, across: Boolean = horizontal): Boolean {
        val kind = nextKind ?: return false
        if (!onBoard(kind, row, col, across)) return false
        val cells = footprint(kind, row, col, across)
        return you.none { ship -> ship.cells.any { it in cells } }
    }

    /** Places the next ship. Null when it would overlap, leave the board, or the battle has started. */
    fun place(row: Int, col: Int): Battleship? {
        if (!canPlace(row, col)) return null
        val kind = nextKind!!
        return copy(you = you + Ship(kind, row, col, horizontal))
    }

    /** Turns the next ship. Null once every ship is placed. */
    fun rotate(): Battleship? = if (placing) copy(horizontal = !horizontal) else null

    /** Puts a complete legal fleet on your grid. Null once the battle has started. */
    fun randomize(): Battleship? {
        if (!placing) return null
        return copy(you = placeFleet(Random(seed + (mix + 1) * 10007L)), mix = mix + 1)
    }

    /** Fires at one hidden enemy square. Null when it is not your turn or the square was already fired at. */
    fun fire(cell: Int): Battleship? {
        if (!yourTurn || cell !in 0 until CELLS || salvo.any { it.cell == cell }) return null
        return copy(salvo = salvo + resolve(enemy, cell, salvo))
    }

    /** Records the computer's shot at your fleet. Null when it is not the computer's turn or the square was used. */
    fun receive(cell: Int): Battleship? {
        if (!awaitingComputer || cell !in 0 until CELLS || incoming.any { it.cell == cell }) return null
        return copy(incoming = incoming + resolve(you, cell, incoming))
    }

    /** Sentence for your latest shot, or null before you have fired. */
    fun yourNews(): String? = salvo.lastOrNull()?.let { news(it, enemy = true) }

    /** Sentence for the computer's latest shot, or null before it has fired. */
    fun theirNews(): String? = incoming.lastOrNull()?.let { news(it, enemy = false) }

    companion object {
        const val SIZE = 10
        const val CELLS = SIZE * SIZE
        const val FLEET = 5
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")

        fun start(seed: Long, setting: Int) = Battleship(setting, seed, emptyList(), placeFleet(Random(seed)))

        /** Every straight placement of [kind], horizontal first. */
        fun placements(kind: ShipKind): List<Ship> = SPANS.getValue(kind.length).map { cells ->
            val horizontal = cells[1] == cells[0] + 1
            Ship(kind, cells[0] / SIZE, cells[0] % SIZE, horizontal)
        }

        internal fun placeFleet(random: Random): List<Ship> {
            repeat(80) {
                val placed = mutableListOf<Ship>()
                val used = mutableSetOf<Int>()
                var ok = true
                for (kind in ShipKind.entries) {
                    val options = placements(kind).filter { ship -> ship.cells.none { it in used } }
                    if (options.isEmpty()) { ok = false; break }
                    val ship = options.random(random)
                    placed += ship
                    used += ship.cells
                }
                if (ok) return placed
            }
            return FALLBACK
        }

        private val FALLBACK = listOf(
            Ship(ShipKind.CARRIER, 0, 0, true),
            Ship(ShipKind.BATTLESHIP, 2, 0, true),
            Ship(ShipKind.CRUISER, 4, 0, true),
            Ship(ShipKind.SUBMARINE, 6, 0, true),
            Ship(ShipKind.DESTROYER, 8, 0, true),
        )

        private val SPANS: Map<Int, List<List<Int>>> = (2..5).associateWith { length ->
            buildList {
                for (r in 0 until SIZE) for (c in 0..SIZE - length) add(List(length) { r * SIZE + c + it })
                for (c in 0 until SIZE) for (r in 0..SIZE - length) add(List(length) { (r + it) * SIZE + c })
            }
        }

        fun onBoard(kind: ShipKind, row: Int, col: Int, horizontal: Boolean): Boolean {
            if (row !in 0 until SIZE || col !in 0 until SIZE) return false
            val endRow = row + if (horizontal) 0 else kind.length - 1
            val endCol = col + if (horizontal) kind.length - 1 else 0
            return endRow in 0 until SIZE && endCol in 0 until SIZE
        }

        fun footprint(kind: ShipKind, row: Int, col: Int, horizontal: Boolean): Set<Int> =
            List(kind.length) { i -> (row + if (horizontal) 0 else i) * SIZE + col + if (horizontal) i else 0 }.toSet()

        private fun fleetOk(ships: List<Ship>, complete: Boolean): Boolean {
            if (ships.size > FLEET || (complete && ships.size != FLEET)) return false
            if (ships.map { it.kind } != ShipKind.entries.take(ships.size)) return false
            val cells = ships.flatMap { it.cells }
            return cells.size == cells.toSet().size
        }

        private fun fleetDown(fleet: List<Ship>, shots: List<Shot>): Boolean {
            if (fleet.size != FLEET) return false
            val cells = shots.map { it.cell }.toSet()
            return fleet.all { ship -> ship.cells.all { it in cells } }
        }

        private fun shotsOk(fleet: List<Ship>, shots: List<Shot>): Boolean {
            if (shots.isNotEmpty() && fleet.size != FLEET) return false
            val seen = mutableSetOf<Int>()
            for ((i, shot) in shots.withIndex()) {
                if (!seen.add(shot.cell)) return false
                val ship = fleet.find { shot.cell in it.cells }
                val sunkNow = ship != null && ship.cells.all { it in seen }
                val mark = when {
                    ship == null -> Mark.MISS
                    sunkNow -> Mark.SUNK
                    else -> Mark.HIT
                }
                if (shot.mark != mark || shot.ship != (if (sunkNow) ship!!.kind else null)) return false
                if (sunkNow && fleet.all { s -> s.cells.all { it in seen } } && i != shots.lastIndex) return false
            }
            return true
        }

        private fun resolve(fleet: List<Ship>, cell: Int, prior: List<Shot>): Shot {
            val seen = prior.map { it.cell }.toSet() + cell
            val ship = fleet.find { cell in it.cells }
            return when {
                ship == null -> Shot(cell, Mark.MISS, null)
                ship.cells.all { it in seen } -> Shot(cell, Mark.SUNK, ship.kind)
                else -> Shot(cell, Mark.HIT, null)
            }
        }

        private fun news(shot: Shot, enemy: Boolean): String = when (shot.mark) {
            Mark.MISS -> if (enemy) "Miss" else "The computer missed"
            Mark.HIT -> if (enemy) "Hit" else "The computer hit a ship"
            Mark.SUNK -> if (enemy) "You sank their ${shot.ship!!.title}" else "The computer sank your ${shot.ship!!.title}"
        }
    }
}

/** What the computer can infer from its own shots: misses, hits still afloat, and sunk squares. */
internal data class SeaKnowledge(val misses: Set<Int>, val hits: Set<Int>, val sunk: Set<Int>)

internal fun seaKnowledge(shots: List<Shot>): SeaKnowledge {
    val hits = mutableSetOf<Int>()
    val sunk = mutableSetOf<Int>()
    val misses = mutableSetOf<Int>()
    for (shot in shots) {
        when (shot.mark) {
            Mark.MISS -> misses += shot.cell
            Mark.HIT -> hits += shot.cell
            Mark.SUNK -> {
                val line = coverLine(shot.cell, shot.ship!!.length, hits + shot.cell) ?: listOf(shot.cell)
                sunk += line
                hits -= line.toSet()
            }
        }
    }
    return SeaKnowledge(misses, hits, sunk)
}

internal fun neighborsOf(cell: Int): List<Int> {
    val row = cell / Battleship.SIZE
    val col = cell % Battleship.SIZE
    return buildList {
        if (row > 0) add(cell - Battleship.SIZE)
        if (row < Battleship.SIZE - 1) add(cell + Battleship.SIZE)
        if (col > 0) add(cell - 1)
        if (col < Battleship.SIZE - 1) add(cell + 1)
    }
}

/** A straight run of [length] inside [pool] that contains [cell], preferring a run less tangled with other hits. */
internal fun coverLine(cell: Int, length: Int, pool: Set<Int>): List<Int>? {
    val row = cell / Battleship.SIZE
    val col = cell % Battleship.SIZE
    val options = mutableListOf<List<Int>>()
    for (start in (col - length + 1)..col) {
        if (start < 0 || start + length > Battleship.SIZE) continue
        val line = List(length) { row * Battleship.SIZE + start + it }
        if (line.all { it in pool }) options += line
    }
    for (start in (row - length + 1)..row) {
        if (start < 0 || start + length > Battleship.SIZE) continue
        val line = List(length) { (start + it) * Battleship.SIZE + col }
        if (line.all { it in pool }) options += line
    }
    return options.minByOrNull { line ->
        pool.count { other -> other !in line && neighborsOf(other).any { it in line } }
    }
}

object BattleshipAi {
    private val SPANS: Map<Int, List<List<Int>>> = (2..5).associateWith { length ->
        buildList {
            for (r in 0 until Battleship.SIZE) for (c in 0..Battleship.SIZE - length) add(List(length) { r * Battleship.SIZE + c + it })
            for (c in 0 until Battleship.SIZE) for (r in 0..Battleship.SIZE - length) add(List(length) { (r + it) * Battleship.SIZE + c })
        }
    }

    /** A square the computer has not fired at. Stronger settings use more of what earlier shots revealed. */
    fun choose(g: Battleship): Int {
        checkpoint()
        val used = g.incoming.map { it.cell }.toSet()
        val legal = (0 until Battleship.CELLS).filter { it !in used }
        require(legal.isNotEmpty())
        val random = Random(g.seed * 7919 + g.incoming.size * 104729L + g.setting * 31L)
        val hits = seaKnowledge(g.incoming).hits
        return when (g.setting) {
            0 -> easy(legal, hits, random)
            1 -> medium(hits, legal, random)
            2 -> hard(g.incoming, hits, legal, random)
            else -> expert(g.incoming, legal, random)
        }
    }

    /**
     * Follows a hit a bit more than a third of the time, preferring the open end of a line.
     * With no hit to chase, prefers the checkerboard a destroyer still has to cross.
     */
    private fun easy(legal: List<Int>, hits: Set<Int>, random: Random): Int {
        val open = legal.toSet()
        val ends = lineEnds(hits, open)
        val near = if (ends.isNotEmpty()) ends else hunt(hits, open)
        if (near.isNotEmpty() && random.nextInt(100) < 36) return near.random(random)
        if (hits.isEmpty()) {
            val parity = legal.filter { cell -> ((cell / Battleship.SIZE) + (cell % Battleship.SIZE)) % 2 == 0 }
            if (parity.isNotEmpty() && random.nextInt(100) < 70) return parity.random(random)
        }
        return legal.random(random)
    }

    /** Hunts the end of a known line, otherwise the neighbor most ships can still cover. */
    private fun medium(hits: Set<Int>, legal: List<Int>, random: Random): Int {
        val open = legal.toSet()
        val ends = lineEnds(hits, open)
        if (ends.isNotEmpty()) return ends.random(random)
        val beside = hunt(hits, open)
        if (beside.isNotEmpty()) return beside.random(random)
        val parity = legal.filter { cell -> ((cell / Battleship.SIZE) + (cell % Battleship.SIZE)) % 2 == 0 }
        return (if (parity.isNotEmpty()) parity else legal).random(random)
    }

    /**
     * Finishes a line when the hits already show its direction.
     * Otherwise ranks the checkerboard by how many remaining ships can still sit there.
     */
    private fun hard(shots: List<Shot>, hits: Set<Int>, legal: List<Int>, random: Random): Int {
        val open = legal.toSet()
        val ends = lineEnds(hits, open)
        if (ends.isNotEmpty()) return ends.random(random)
        val beside = hunt(hits, open)
        if (beside.isNotEmpty()) return beside.random(random)
        val parity = legal.filter { cell -> ((cell / Battleship.SIZE) + (cell % Battleship.SIZE)) % 2 == 0 }
        val pool = if (parity.isNotEmpty()) parity else legal
        if (hits.isNotEmpty()) return pool.random(random)
        val scores = density(shots, pool)
        val best = pool.maxOf { scores[it] }
        return pool.filter { scores[it] == best }.random(random)
    }

    /**
     * Counts how many remaining ships can still cover each unfired square.
     * Once hits are known, only placements that explain those hits are counted, and a placement
     * has to be long enough to hold every hit it claims.
     */
    private fun expert(shots: List<Shot>, legal: List<Int>, random: Random): Int {
        val known = seaKnowledge(shots)
        val blocked = known.misses + known.sunk
        val hits = known.hits
        val lengths = remainingLengths(shots)
        val scores = IntArray(Battleship.CELLS)
        val groups = clusters(hits)
        for (length in lengths) {
            checkpoint()
            for (cells in SPANS.getValue(length)) {
                if (cells.any { it in blocked }) continue
                val covered = cells.filter { it in hits }
                if (hits.isNotEmpty() && covered.isEmpty()) continue
                if (covered.size >= 2 && !straight(covered)) continue
                var weight = 1 + covered.size * covered.size * 10
                if (groups.any { group -> group.all { it in cells } }) weight += 25
                for (cell in cells) if (cell !in hits && cell !in blocked) scores[cell] += weight
            }
        }
        val best = legal.maxOf { scores[it] }
        if (best <= 0) return legal.random(random)
        return legal.filter { scores[it] == best }.random(random)
    }

    /** How many remaining ships can still cover each square. Used while there is nothing afloat to chase. */
    private fun density(shots: List<Shot>, pool: List<Int>): IntArray {
        val blocked = seaKnowledge(shots).let { it.misses + it.sunk }
        val scores = IntArray(Battleship.CELLS)
        for (length in remainingLengths(shots)) {
            checkpoint()
            for (cells in SPANS.getValue(length)) {
                if (cells.any { it in blocked }) continue
                val room = cells.count { it !in blocked }
                if (room < length) continue
                for (cell in cells) if (cell !in blocked) scores[cell]++
            }
        }
        if (pool.all { scores[it] == 0 }) pool.forEach { scores[it] = 1 }
        return scores
    }

    private fun hunt(hits: Set<Int>, legal: Set<Int>): List<Int> =
        hits.flatMap { neighborsOf(it) }.filter { it in legal }.distinct()

    private fun lineEnds(hits: Set<Int>, legal: Set<Int>): List<Int> {
        if (hits.isEmpty()) return emptyList()
        val ends = mutableListOf<Int>()
        for (cell in hits) {
            val row = cell / Battleship.SIZE
            val col = cell % Battleship.SIZE
            val across = (col > 0 && cell - 1 in hits) || (col < Battleship.SIZE - 1 && cell + 1 in hits)
            val down = (row > 0 && cell - Battleship.SIZE in hits) || (row < Battleship.SIZE - 1 && cell + Battleship.SIZE in hits)
            if (across) {
                var c = col
                while (c > 0 && row * Battleship.SIZE + (c - 1) in hits) c--
                val left = row * Battleship.SIZE + (c - 1)
                if (c > 0 && left in legal) ends += left
                c = col
                while (c < Battleship.SIZE - 1 && row * Battleship.SIZE + (c + 1) in hits) c++
                val right = row * Battleship.SIZE + (c + 1)
                if (c < Battleship.SIZE - 1 && right in legal) ends += right
            }
            if (down) {
                var r = row
                while (r > 0 && (r - 1) * Battleship.SIZE + col in hits) r--
                val up = (r - 1) * Battleship.SIZE + col
                if (r > 0 && up in legal) ends += up
                r = row
                while (r < Battleship.SIZE - 1 && (r + 1) * Battleship.SIZE + col in hits) r++
                val below = (r + 1) * Battleship.SIZE + col
                if (r < Battleship.SIZE - 1 && below in legal) ends += below
            }
        }
        return ends.distinct()
    }

    private fun remainingLengths(shots: List<Shot>): List<Int> {
        val left = ShipKind.entries.toMutableList()
        for (shot in shots) if (shot.ship != null) check(left.remove(shot.ship))
        return left.map { it.length }
    }

    private fun straight(cells: List<Int>): Boolean {
        val rows = cells.map { it / Battleship.SIZE }.toSet()
        val cols = cells.map { it % Battleship.SIZE }.toSet()
        return rows.size == 1 || cols.size == 1
    }

    private fun clusters(hits: Set<Int>): List<Set<Int>> {
        val left = hits.toMutableSet()
        val groups = mutableListOf<Set<Int>>()
        while (left.isNotEmpty()) {
            val stack = ArrayDeque<Int>()
            val group = mutableSetOf<Int>()
            stack.add(left.first())
            while (stack.isNotEmpty()) {
                val cell = stack.removeLast()
                if (!left.remove(cell)) continue
                group += cell
                neighborsOf(cell).filter { it in left }.forEach { stack.add(it) }
            }
            groups += group
        }
        return groups
    }
}

object BattleshipCodec {
    fun encode(g: Battleship) = listOf(
        "1",
        g.setting.toString(),
        g.seed.toString(),
        if (g.horizontal) "1" else "0",
        g.mix.toString(),
        g.you.joinToString(";") { ship(it) },
        g.enemy.joinToString(";") { ship(it) },
        g.salvo.joinToString(";") { shot(it) },
        g.incoming.joinToString(";") { shot(it) },
    ).joinToString("\n")

    fun decode(text: String): Battleship? = try {
        val lines = text.split('\n')
        require(lines.size == 9 && lines[0] == "1")
        Battleship(
            lines[1].toInt(),
            lines[2].toLong(),
            ships(lines[5]),
            ships(lines[6]),
            shots(lines[7]),
            shots(lines[8]),
            lines[3] == "1",
            lines[4].toInt(),
        )
    } catch (_: IllegalArgumentException) { null }

    private fun ship(s: Ship) = "${s.kind.name},${s.row},${s.col},${if (s.horizontal) 1 else 0}"

    private fun shot(s: Shot) = "${s.cell},${s.mark.name},${s.ship?.name ?: ""}"

    private fun ships(line: String): List<Ship> =
        if (line.isEmpty()) emptyList() else line.split(';').map { part ->
            val bits = part.split(',')
            require(bits.size == 4)
            Ship(ShipKind.valueOf(bits[0]), bits[1].toInt(), bits[2].toInt(), bits[3] == "1")
        }

    private fun shots(line: String): List<Shot> =
        if (line.isEmpty()) emptyList() else line.split(';').map { part ->
            val bits = part.split(',')
            require(bits.size == 3)
            Shot(bits[0].toInt(), Mark.valueOf(bits[1]), bits[2].takeIf { it.isNotEmpty() }?.let { ShipKind.valueOf(it) })
        }
}
