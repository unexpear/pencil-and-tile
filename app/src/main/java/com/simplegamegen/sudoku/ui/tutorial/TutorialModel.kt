package com.simplegamegen.sudoku.ui.tutorial

import com.simplegamegen.sudoku.ui.GameId

/** Background drawn for a square. */
enum class Fill { PAPER, BLOCK, SHADED, COVER, OPEN, LIGHT, DARK, NONE }

/** Emphasis for an item: colours text or background. */
enum class Tone { PLAIN, ENTERED, WRONG, GOOD, SELECTED, FOUND, DIM }

enum class Mark { NONE, CROSS, CIRCLE, FLAG, MINE }

/** What sits under a scene. */
enum class Backdrop { PAPER, TABLE, BOARD }

enum class PieceKind { CHECKER, DISC, COUNTER, STONE, SPOT, PEG }

/** A game piece drawn on a square. [player] 1 is you, -1 the opponent (Reversi: 1 black, -1 white). */
data class Piece(val kind: PieceKind, val player: Int = 1, val king: Boolean = false, val label: String = "")

/** How an item looks. */
sealed interface Look

/**
 * A grid square. [walls] and [cage] are side bitmasks (1 top, 2 right, 4 bottom, 8 left) for thick
 * borders and dashed cage outlines. [corner] is small text top-left, [sub] small text at the bottom.
 */
data class Cell(
    val text: String = "",
    val corner: String = "",
    val sub: String = "",
    val notes: String = "",
    val fill: Fill = Fill.PAPER,
    val mark: Mark = Mark.NONE,
    val walls: Int = 0,
    val cage: Int = 0,
    val piece: Piece? = null,
) : Look

/** A Kakuro clue square: down total below the slash, across total above it. */
data class Clue(val down: Int? = null, val across: Int? = null) : Look

/** A playing card in engine numbering (suit * 13 + rank - 1; suits ♠ ♥ ♦ ♣). */
data class Card(val card: Int, val faceUp: Boolean = true) : Look

data class Slot(val label: String = "") : Look

/** A Mahjong tile; face-down tiles show the themed back (used by Memory). */
data class Tile(val face: Int, val free: Boolean = true, val faceUp: Boolean = true) : Look

data class Domino(val a: Int, val b: Int, val horizontal: Boolean = true) : Look

/** A 2048 tile. */
data class Value(val n: Int) : Look

/** A Tetras block; [color] indexes the piece colours. */
data class Block(val color: Int) : Look

/** A letter tile (Scramble tiles, Dropquote column letters). */
data class Letter(val ch: String, val used: Boolean = false, val raised: Boolean = true) : Look

data class Label(val text: String, val size: Float = 0.42f, val bold: Boolean = false, val center: Boolean = true) : Look

/** A Dots and Boxes line; [player] 0 means not drawn yet. */
data class Edge(val player: Int = 0) : Look

data object Dot : Look

/** A positioned item in scene units; [right] and [below] draw a sign on that border (Futoshiki). */
data class Item(
    val id: String,
    val x: Float,
    val y: Float,
    val w: Float = 1f,
    val h: Float = 1f,
    val look: Look,
    val tone: Tone = Tone.PLAIN,
    val right: String = "",
    val below: String = "",
    val describe: String = "",
)

/** A curve between two items (Sprouts); a link from an item to itself is a loop bulging upward. */
data class Link(val from: String, val to: String, val bend: Float = 0f, val fresh: Boolean = false)

/** One picture in a tutorial, laid out in abstract units that scale to the screen. */
data class Scene(
    val width: Float,
    val height: Float,
    val items: List<Item>,
    val surface: Backdrop = Backdrop.PAPER,
    val links: List<Link> = emptyList(),
    val ring: Boolean = false,
    val choices: List<String> = emptyList(),
    /** Largest size of one unit, in dp. */
    val maxUnit: Int = 52,
    /** Text shown under the picture (clue lists, word lists). */
    val caption: String = "",
) {
    fun item(id: String): Item? = items.firstOrNull { it.id == id }

    fun map(ids: Collection<String>, change: (Item) -> Item): Scene = copy(items = items.map { if (it.id in ids) change(it) else it })
    fun map(vararg ids: String, change: (Item) -> Item): Scene = map(ids.toSet(), change)
    fun tone(tone: Tone, vararg ids: String): Scene = map(*ids) { it.copy(tone = tone) }
    fun look(id: String, look: Look, tone: Tone = Tone.PLAIN): Scene = map(id) { it.copy(look = look, tone = tone) }
    fun remove(vararg ids: String): Scene = copy(items = items.filter { it.id !in ids })
    fun plus(vararg extra: Item): Scene = copy(items = items + extra)
    fun move(id: String, x: Float, y: Float): Scene = map(id) { it.copy(x = x, y = y) }
    fun choices(vararg labels: String): Scene = copy(choices = labels.toList())
    fun link(link: Link): Scene = copy(links = links.map { it.copy(fresh = false) } + link)

    /** Sets a square's text (keeping its other details) with an optional tone. */
    fun text(id: String, text: String, tone: Tone = Tone.ENTERED): Scene =
        map(id) { val c = it.look as Cell; it.copy(look = c.copy(text = text, notes = ""), tone = tone) }

    fun cell(id: String, change: (Cell) -> Cell): Scene = map(id) { it.copy(look = change(it.look as Cell)) }
    fun allTone(tone: Tone, filter: (Item) -> Boolean = { it.look is Cell && (it.look as Cell).fill == Fill.PAPER }): Scene =
        copy(items = items.map { if (filter(it)) it.copy(tone = tone) else it })
}

/**
 * One step of guided play. The player must tap one of [tap] (item ids) or pick the [pick] choice;
 * a step with neither just explains and moves on with Next. [after] is shown once the step is done.
 */
data class Step(
    val say: String,
    val scene: Scene,
    val tap: Set<String> = emptySet(),
    val pick: String? = null,
    val then: String = "",
    val after: Scene? = null,
    /** Extra help shown after a wrong try. */
    val help: String = "",
) {
    val interactive: Boolean get() = tap.isNotEmpty() || pick != null
    val result: Scene get() = after ?: scene
}

data class Tutorial(
    val game: GameId,
    val summary: String,
    val rules: List<String>,
    val tips: List<String>,
    val steps: List<Step>,
)

fun cellId(r: Int, c: Int) = "r${r}c$c"

/** Items for a grid of squares at ([x0], [y0]); [look] returns null to leave a gap. */
fun gridItems(rows: Int, cols: Int, x0: Float = 0f, y0: Float = 0f, look: (Int, Int) -> Look?): List<Item> =
    (0 until rows).flatMap { r -> (0 until cols).mapNotNull { c -> look(r, c)?.let { Item(cellId(r, c), x0 + c, y0 + r, look = it) } } }

/** Side bitmask for a square whose region differs from its neighbour's (region ids from [map]). */
fun regionSides(map: List<String>, r: Int, c: Int, outer: Boolean = true): Int {
    val me = map[r][c]
    fun other(rr: Int, cc: Int) = if (rr !in map.indices || cc !in map[rr].indices) outer else map[rr][cc] != me
    var sides = 0
    if (other(r - 1, c)) sides = sides or 1
    if (other(r, c + 1)) sides = sides or 2
    if (other(r + 1, c)) sides = sides or 4
    if (other(r, c - 1)) sides = sides or 8
    return sides
}
