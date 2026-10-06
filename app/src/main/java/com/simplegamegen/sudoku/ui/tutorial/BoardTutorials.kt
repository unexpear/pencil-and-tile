package com.simplegamegen.sudoku.ui.tutorial

import com.simplegamegen.sudoku.duels.KeyPegs
import com.simplegamegen.sudoku.duels.Mancala
import com.simplegamegen.sudoku.duels.Mastermind
import com.simplegamegen.sudoku.ui.GameId

internal object BoardTutorials {
    val mines: Tutorial get() {
        val values = listOf("M111", "111M", "0011", "0000")
        fun scene(open: Set<String>, flags: Set<String> = emptySet(), choices: List<String> = listOf("Reveal", "Flag")): Scene {
            val items = gridItems(4, 4) { r, c ->
                val id = cellId(r, c)
                when {
                    id in flags -> Cell(fill = Fill.COVER, mark = Mark.FLAG)
                    id in open -> Cell(fill = Fill.OPEN, text = values[r][c].takeIf { it in '1'..'8' }?.toString() ?: "")
                    else -> Cell(fill = Fill.COVER)
                }
            }
            return Scene(4f, 4f, items, choices = choices, maxUnit = 64)
        }
        val opened = setOf("r1c0", "r1c1", "r1c2", "r2c0", "r2c1", "r2c2", "r2c3", "r3c0", "r3c1", "r3c2", "r3c3")
        val chorded = opened + setOf("r0c1", "r0c2", "r0c3")
        return Tutorial(
            GameId.MINES,
            "Open every safe square using the number clues.",
            rules = listOf(
                "Some squares hide mines. Open every square that doesn't have one to win; opening a mine loses.",
                "An open square's number counts the mines in the eight squares around it. An empty square has none, and its neighbours open automatically.",
                "Your first square and its neighbours are always safe.",
                "Flag mode marks squares you think are mines. Tapping an open number whose flags are all placed opens its other neighbours (chording).",
            ),
            tips = listOf(
                "A number that touches exactly as many covered squares as its value: they're all mines.",
                "A number whose mines are all flagged: its other neighbours are safe.",
                "Wrong flags make chording dangerous. Hint uses only what's visible.",
            ),
            steps = listOf(
                Step("Numbers count the mines touching a square, including diagonally. Your first tap is always safe.", scene(emptySet(), choices = emptyList())),
                Step("Tap the bottom-left square.", scene(emptySet()), tap = setOf("r3c0"), after = scene(opened),
                    then = "An empty area opens by itself, bordered by numbers."),
                Step("The 1 at the right end of the third row touches only one covered square, so that square must be a mine. Switch to Flag.",
                    scene(opened), pick = "Flag", after = scene(opened)),
                Step("Flag the mine.", scene(opened), tap = setOf("r1c3"), after = scene(opened, setOf("r1c3")),
                    help = "It's the covered square at the right end of the second row."),
                Step("The 1 just left of the flag now has its mine, so its other covered neighbours are safe. Switch back to Reveal.",
                    scene(opened, setOf("r1c3")), pick = "Reveal", after = scene(opened, setOf("r1c3"))),
                Step("Tap that 1 to open all its other neighbours at once (chording).", scene(opened, setOf("r1c3")), tap = setOf("r1c2"),
                    after = scene(chorded, setOf("r1c3")), help = "The 1 in the second row, third square."),
                Step("The 1 at the start of the second row now touches just one covered square, the corner: the last mine. Every safe square is open, so you win!",
                    scene(chorded, setOf("r1c3", "r0c0"), emptyList())),
            ),
        )
    }

    val checkers: Tutorial get() {
        fun scene(me: String, king: Boolean = false, foe: String? = null, selected: String? = null, targets: Set<String> = emptySet()): Scene {
            val items = gridItems(5, 5) { r, c ->
                val id = cellId(r, c)
                val dark = (r + c) % 2 == 1
                val piece = when (id) { me -> Piece(PieceKind.CHECKER, 1, king); foe -> Piece(PieceKind.CHECKER, -1); else -> null }
                Cell(fill = if (dark) Fill.DARK else Fill.LIGHT, piece = piece, mark = if (id in targets) Mark.CIRCLE else Mark.NONE)
            }.map { if (it.id == selected) it.copy(tone = Tone.SELECTED) else it }
            return Scene(5f, 5f, items, Backdrop.BOARD, maxUnit = 60)
        }
        return Tutorial(
            GameId.CHECKERS,
            "English draughts: jump and capture the computer's pieces.",
            rules = listOf(
                "You play the dark pieces from the bottom and move first. Pieces move diagonally on dark squares.",
                "Men move one square diagonally forward. Capture by jumping over an opponent's piece to the empty square beyond.",
                "Captures are compulsory, and a multi-jump must be finished.",
                "A man that reaches the far row is crowned King; that ends the turn. Kings move and capture backwards too, one square at a time.",
                "You win when the computer has no pieces or no legal move. Repeated positions or 40 moves each without progress is a draw.",
            ),
            tips = listOf("Keep your back row filled as long as you can to stop enemy Kings.", "Trade pieces when you're ahead."),
            steps = listOf(
                Step("Your dark pieces move diagonally forward (up the board) on the dark squares.", scene("r4c1")),
                Step("Tap your piece.", scene("r4c1"), tap = setOf("r4c1"), after = scene("r4c1", selected = "r4c1", targets = setOf("r3c0", "r3c2"))),
                Step("The circles show where it can go. Move to the right-hand one.", scene("r4c1", selected = "r4c1", targets = setOf("r3c0", "r3c2")),
                    tap = setOf("r3c2"), after = scene("r3c2", foe = "r2c3"), then = "The computer answers by moving next to you."),
                Step("Its piece has an empty square behind it, so you can jump it. Captures are compulsory. Tap your piece.",
                    scene("r3c2", foe = "r2c3"), tap = setOf("r3c2"), after = scene("r3c2", foe = "r2c3", selected = "r3c2", targets = setOf("r1c4"))),
                Step("Jump to the square beyond it.", scene("r3c2", foe = "r2c3", selected = "r3c2", targets = setOf("r1c4")), tap = setOf("r1c4"),
                    after = scene("r1c4"), then = "Captured!"),
                Step("Reaching the far row crowns your piece. Tap it.", scene("r1c4"), tap = setOf("r1c4"),
                    after = scene("r1c4", selected = "r1c4", targets = setOf("r0c3"))),
                Step("Move into the top row.", scene("r1c4", selected = "r1c4", targets = setOf("r0c3")), tap = setOf("r0c3"),
                    after = scene("r0c3", king = true), then = "Crowned King! Kings can also move backwards."),
            ),
        )
    }

    val reversi: Tutorial get() {
        fun scene(black: Set<String>, white: Set<String>, targets: Set<String> = emptySet()): Scene {
            val items = gridItems(4, 4) { r, c ->
                val id = cellId(r, c)
                Cell(fill = Fill.OPEN, piece = when (id) { in black -> Piece(PieceKind.DISC, 1); in white -> Piece(PieceKind.DISC, -1); else -> null },
                    mark = if (id in targets) Mark.CIRCLE else Mark.NONE)
            }
            return Scene(4f, 4f, items, Backdrop.TABLE, maxUnit = 64)
        }
        val b0 = setOf("r1c2", "r2c1"); val w0 = setOf("r1c1", "r2c2")
        val b1 = b0 + setOf("r0c1", "r1c1"); val w1 = setOf("r2c2")
        val b2 = setOf("r0c1", "r1c1", "r2c1"); val w2 = setOf("r0c2", "r1c2", "r2c2")
        val b3 = b2 + setOf("r0c2", "r0c3", "r1c2"); val w3 = setOf("r2c2")
        return Tutorial(
            GameId.REVERSI,
            "Trap your opponent's discs to flip them to your colour.",
            rules = listOf(
                "You play Black and move first on an 8×8 board.",
                "Place a disc so that one or more straight lines (across, down or diagonal) of White discs are trapped between it and another Black disc.",
                "Every trapped disc flips to Black. One move can flip several lines.",
                "If you have no legal move, your turn is skipped automatically. The game ends when neither side can move; most discs wins.",
                "Marked squares show your legal moves.",
            ),
            tips = listOf("Corners can never be flipped back; they're valuable.", "Squares next to an empty corner often give it away.",
                "Having more moves available matters more than having more discs early on."),
            steps = listOf(
                Step("You're Black. Circles show your legal moves: each traps white discs in a line.", scene(b0, w0, setOf("r0c1", "r1c0", "r2c3", "r3c2"))),
                Step("Tap the circle above the top-left white disc.", scene(b0, w0, setOf("r0c1", "r1c0", "r2c3", "r3c2")), tap = setOf("r0c1"),
                    after = scene(b1, w1), then = "The white disc between your two black discs flipped."),
                Step("White replies in the top row and flips one of yours back.", scene(b2, w2)),
                Step("One move can flip several lines. Tap the top-right corner.", scene(b2, w2, setOf("r0c3")), tap = setOf("r0c3"),
                    after = scene(b3, w3), then = "Two lines flipped at once, and a corner disc can never be flipped back."),
            ),
        )
    }

    val dots: Tutorial get() {
        val gap = 1.2f; val o = 0.3f
        fun h(r: Int, c: Int) = "h$r$c"
        fun v(r: Int, c: Int) = "v$r$c"
        fun scene(drawn: Map<String, Int>, owners: Map<String, String> = emptyMap(), fresh: String? = null): Scene {
            val dots = (0..2).flatMap { r -> (0..2).map { c -> Item("", o + c * gap - 0.15f, o + r * gap - 0.15f, 0.3f, 0.3f, Dot) } }
            val hs = (0..2).flatMap { r -> (0..1).map { c -> Item(h(r, c), o + c * gap + 0.15f, o + r * gap - 0.15f, 0.9f, 0.3f, Edge(drawn[h(r, c)] ?: 0), if (h(r, c) == fresh) Tone.SELECTED else Tone.PLAIN) } }
            val vs = (0..1).flatMap { r -> (0..2).map { c -> Item(v(r, c), o + c * gap - 0.15f, o + r * gap + 0.15f, 0.3f, 0.9f, Edge(drawn[v(r, c)] ?: 0), if (v(r, c) == fresh) Tone.SELECTED else Tone.PLAIN) } }
            val boxes = owners.map { (box, who) -> Item("", o + (box[1].digitToInt()) * gap + 0.15f, o + (box[0].digitToInt()) * gap + 0.15f, 0.9f, 0.9f, Label(who, 0.3f, bold = true)) }
            return Scene(3f, 3f, boxes + hs + vs + dots, maxUnit = 90)
        }
        val start = mapOf(h(0, 0) to 1, v(0, 0) to -1, h(1, 0) to 1, h(0, 1) to -1, v(1, 0) to 1, h(2, 1) to -1)
        val s1 = start + (v(0, 1) to 1)
        val s2 = s1 + (v(1, 2) to 1)
        return Tutorial(
            GameId.DOTS,
            "Draw lines between dots; complete boxes to claim them.",
            rules = listOf(
                "Take turns drawing one line between two neighbouring dots.",
                "Drawing the fourth side of a box claims it, and you move again.",
                "When every line is drawn, the player with more boxes wins.",
                "In a game, tap one dot and then its neighbor. Turn off Tap two dots to tap lines instead. In this tutorial, tap the missing line.",
            ),
            tips = listOf(
                "Avoid drawing the third side of a box: your opponent will take it.",
                "Late in the game every move gives something away; give away the smallest chain.",
                "Stronger players sometimes decline the last two boxes of a chain to keep control.",
            ),
            steps = listOf(
                Step("Draw lines between neighbouring dots. Close a box's fourth side to claim it and move again.", scene(start)),
                Step("The top-left box has three sides. Draw its missing side.", scene(start), tap = setOf(v(0, 1)),
                    after = scene(s1, mapOf("00" to "You"), fresh = v(0, 1)), then = "The box is yours, and you move again.",
                    help = "The missing side is the right-hand side of the top-left box."),
                Step("Don't draw a third side on any box, or your opponent takes it. Only one line is safe now: find it.", scene(s1, mapOf("00" to "You")),
                    tap = setOf(v(1, 2)), after = scene(s2, mapOf("00" to "You"), fresh = v(1, 2)),
                    then = "Safe: every box has at most two sides, so the computer must give something away.",
                    help = "Count the sides of each box. Every line except the far right of the bottom-right box makes a third side."),
            ),
        )
    }

    val sprouts: Tutorial get() {
        fun spot(id: String, x: Float, y: Float, left: Int, tone: Tone = Tone.PLAIN) =
            Item(id, x, y, 0.6f, 0.6f, Cell(fill = Fill.NONE, piece = Piece(PieceKind.SPOT, label = if (left == 0) "" else "$left")),
                if (left == 0) Tone.DIM else tone)
        fun scene(a: Tone = Tone.PLAIN, b: Tone = Tone.PLAIN, joined: Boolean = false, loop: Boolean = false, waypoint: Boolean = false,
            choices: List<String> = emptyList()): Scene {
            val items = mutableListOf(
                spot("a", 0.4f, 1.2f, if (joined) 2 else 3, a),
                spot("b", 3.4f, 1.2f, if (loop) 0 else if (joined) 2 else 3, b),
            )
            val links = mutableListOf<Link>()
            if (joined) { items += spot("c", 1.9f, 1.2f, 1); links += Link("a", "b", fresh = !loop) }
            if (waypoint || loop) items += Item("w", 3.4f, 2.5f, 0.6f, 0.6f, Cell(fill = Fill.NONE, mark = if (loop) Mark.NONE else Mark.CIRCLE))
            if (loop) { items += spot("d", 3.4f, 2.5f, 1); links += Link("b", "b", bend = -1f, fresh = true) }
            return Scene(4.4f, 3.4f, items, Backdrop.TABLE, links, choices = choices, maxUnit = 80)
        }
        return Tutorial(
            GameId.SPROUTS,
            "Join spots with lines; the last player able to move wins.",
            rules = listOf(
                "Take turns drawing a line between two spots, or from a spot back to itself (a loop).",
                "A new spot sprouts in the middle of every line you draw.",
                "Lines may not cross each other or pass through a spot.",
                "A spot can have at most three line ends; a full spot is out of play.",
                "Whoever draws the last possible line wins.",
                "In the game: tap two spots (or one spot twice for a loop), optionally tap a point to steer the line, then Draw line.",
            ),
            tips = listOf("Count the moves left: each spot has 3 lives, and every move uses 2 and adds 1.", "Loops can wall off spots from your opponent."),
            steps = listOf(
                Step("Numbers show how many more line ends each spot can take (3 at the start).", scene()),
                Step("Tap the left spot.", scene(), tap = setOf("a"), after = scene(a = Tone.SELECTED)),
                Step("Tap the right spot to join them.", scene(a = Tone.SELECTED), tap = setOf("b"), after = scene(a = Tone.SELECTED, b = Tone.SELECTED, choices = listOf("Draw line"))),
                Step("Draw the line.", scene(a = Tone.SELECTED, b = Tone.SELECTED, choices = listOf("Draw line")), pick = "Draw line", after = scene(joined = true),
                    then = "A new spot sprouted in the middle. It already has two line ends, so it has 1 left."),
                Step("Now a loop: tap the right spot…", scene(joined = true), tap = setOf("b"), after = scene(b = Tone.SELECTED, joined = true)),
                Step("…and the same spot again.", scene(b = Tone.SELECTED, joined = true), tap = setOf("b"),
                    after = scene(b = Tone.SELECTED, joined = true, waypoint = true), then = "A loop needs a waypoint to go around."),
                Step("Tap the marked point below it to steer the loop.", scene(b = Tone.SELECTED, joined = true, waypoint = true), tap = setOf("w"),
                    after = scene(b = Tone.SELECTED, joined = true, waypoint = true, choices = listOf("Draw line"))),
                Step("Draw it.", scene(b = Tone.SELECTED, joined = true, waypoint = true, choices = listOf("Draw line")), pick = "Draw line",
                    after = scene(joined = true, loop = true), then = "The loop used two of the right spot's ends: it's full and greys out."),
            ),
        )
    }

    val magnets: Tutorial get() {
        fun stone(id: String, x: Float, y: Float, player: Int) = Item(id, x, y, 0.7f, 0.7f, Cell(fill = Fill.NONE, piece = Piece(PieceKind.STONE, player)))
        fun target(id: String, x: Float, y: Float) = Item(id, x, y, 0.7f, 0.7f, Cell(fill = Fill.NONE, mark = Mark.CIRCLE))
        fun hands(me: Int, cpu: Int) = "You: $me left · Computer: $cpu left"
        val base = listOf(stone("foe", 1.1f, 1.3f, -1), stone("mine", 2.4f, 2.6f, 1))
        val start = Scene(4f, 4f, base + target("far", 2.7f, 0.9f), Backdrop.TABLE, ring = true, maxUnit = 80, caption = hands(7, 7))
        val s1 = Scene(4f, 4f, base + stone("placed", 2.7f, 0.9f, 1), Backdrop.TABLE, ring = true, maxUnit = 80, caption = hands(6, 7))
        val s2 = s1.plus(target("near", 1.55f, 1.85f))
        val s3 = Scene(4f, 4f, listOf(stone("mine", 2.4f, 2.6f, 1), stone("placed", 2.7f, 0.9f, 1)), Backdrop.TABLE, ring = true, maxUnit = 80, caption = hands(7, 7))
        return Tutorial(
            GameId.MAGNETS,
            "Place magnetic stones without letting them snap together.",
            rules = listOf(
                "You and the computer each have 8 magnetic stones. Take turns placing one inside the ring.",
                "If your stone lands within the pull of another stone, they snap together and you take the whole cluster back into your hand.",
                "Stones must be placed inside the ring.",
                "The first player to place all their stones wins.",
                "In the game, tap once to preview a spot (pull circles show) and tap again to place.",
            ),
            tips = listOf("Space runs out quickly; leave awkward gaps for your opponent.", "Stones near the edge of the ring have fewer neighbours to worry about."),
            steps = listOf(
                Step("Place stones inside the ring. Land too close to another stone and they snap together into your hand.", start),
                Step("Place a stone in the open space at the top right.", start, tap = setOf("far"), after = s1, then = "Safe: one stone fewer in your hand."),
                Step("Now see a snap: place a stone right next to the computer's stone.", s2, tap = setOf("near"), after = s3,
                    then = "Snap! Both stones jumped into your hand, so you now have more to place. Keep your distance."),
            ),
        )
    }

    val connectFour: Tutorial get() {
        fun scene(rows: List<String>, mark: String? = null, good: Set<String> = emptySet()): Scene {
            val items = gridItems(rows.size, rows[0].length) { r, c ->
                val id = cellId(r, c)
                Cell(
                    fill = Fill.OPEN,
                    piece = when (rows[r][c]) {
                        'Y' -> Piece(PieceKind.COUNTER, 1)
                        'C' -> Piece(PieceKind.COUNTER, -1)
                        else -> null
                    },
                    mark = if (id == mark) Mark.CIRCLE else Mark.NONE,
                )
            }
            val base = Scene(rows[0].length.toFloat(), rows.size.toFloat(), items, Backdrop.BOARD, maxUnit = 64)
            return if (good.isEmpty()) base else base.tone(Tone.GOOD, *good.toTypedArray())
        }
        val column = (0 until 4).map { cellId(it, 2) }.toSet()
        val empty = listOf(".....", ".....", ".....", ".....")
        val dropped = listOf(".....", ".....", ".....", "..Y..")
        val answered = listOf(".....", ".....", ".....", "..YC.")
        val threat = listOf(".....", ".....", ".....", "YY.YC")
        val won = listOf(".....", ".....", ".....", "YYYYC")
        return Tutorial(
            GameId.CONNECT_FOUR,
            "Drop discs and line up four in a row.",
            rules = listOf(
                "Take turns dropping one disc into a column. It falls to the lowest empty space.",
                "Line up four of your discs across, down or diagonally to win.",
                "The board has 7 columns and 6 rows. Filling it with no line of four is a draw.",
                "You play the red discs and move first. In a game, tap a column to drop.",
            ),
            tips = listOf(
                "The middle columns are part of more lines of four, so they're useful early.",
                "If you can win in two columns at once, the other player can stop only one.",
            ),
            steps = listOf(
                Step("Discs fall straight down to the lowest empty space in the column you choose.", scene(empty)),
                Step("Tap the middle column.", scene(empty, mark = "r3c2"), tap = column,
                    after = scene(dropped), then = "Your disc landed at the bottom.",
                    help = "The middle column, where the circle is."),
                Step("The computer drops in a different column. Four in a row can also run down or on a diagonal.", scene(answered)),
                Step("Three of yours sit on the bottom, with one gap. Drop there to make four across.", scene(threat, mark = "r3c2"),
                    tap = column, after = scene(won, good = (0..3).map { cellId(3, it) }.toSet()),
                    then = "Four in a row. You win!", help = "The empty column between your discs."),
            ),
        )
    }

    val mastermind: Tutorial get() {
        val secret = listOf(0, 4, 0, 2)
        val sample = listOf(0, 3, 4, 5)
        val scored = Mastermind.score(secret, sample)
        fun peg(color: Int?): Cell = Cell(fill = Fill.OPEN, piece = color?.let { Piece(PieceKind.PEG, it) })
        fun keyCell(kind: Int): Cell = Cell(fill = Fill.OPEN, piece = when (kind) {
            1 -> Piece(PieceKind.PEG, -1)
            2 -> Piece(PieceKind.PEG, -2)
            else -> null
        })
        fun marks(keys: KeyPegs?): List<Int> = List(4) { i ->
            when {
                keys == null -> 0
                i < keys.black -> 1
                i < keys.black + keys.white -> 2
                else -> 0
            }
        }
        fun board(guess: List<Int?>, keys: KeyPegs? = null, mark: Int? = null): Scene {
            val holes = (0 until 4).map { c ->
                Item(cellId(0, c), 1f + c, 0f, look = peg(guess.getOrNull(c)))
            }
            val keysRow = marks(keys).mapIndexed { c, kind ->
                Item(cellId(1, c), 1.1f + c, 1.2f, 0.72f, 0.72f, look = keyCell(kind))
            }
            val palette = Mastermind.COLOR_NAMES.mapIndexed { i, name ->
                Item("p$i", i.toFloat(), 2.25f, 0.86f, 0.86f, look = peg(i).let { if (i == mark) it.copy(mark = Mark.CIRCLE) else it }, describe = name)
            }
            return Scene(6f, 3.25f, holes + keysRow + palette, Backdrop.BOARD, maxUnit = 48)
        }
        val won = Mastermind.score(secret, secret)
        return Tutorial(
            GameId.MASTERMIND,
            "Break a hidden code of four colored pegs.",
            rules = listOf(
                "The computer hides a code of four pegs. Each peg is one of six colors, and a color may be used more than once.",
                "You have ten guesses. Tap colors to fill the row, then tap Guess.",
                "A filled key means that color is in the right place. An open key means that color is in the code but in the wrong place.",
                "The keys are only those two counts. They do not show which peg earned which key.",
            ),
            tips = listOf(
                "A color that earns no key is not in the code.",
                "On Easy, the code uses red, orange, yellow and green once each.",
            ),
            steps = listOf(
                Step("The computer has hidden four pegs. You choose from six colors, and the same color can appear twice.", board(emptyList())),
                Step("Tap red to place the first peg of a guess.", board(emptyList(), mark = 0), tap = setOf("p0"),
                    after = board(listOf(0)), then = "Red is in the first hole.", help = "The red peg, with the circle."),
                Step("This guess is red, green, blue, purple. One filled key and one open key: one peg is in the right place, and one right color is in the wrong place. The keys do not say which peg.",
                    board(sample, scored)),
                Step("Red, blue, red, yellow matches the code. Tap yellow.", board(listOf(0, 4, 0), mark = 2), tap = setOf("p2"),
                    after = board(secret, won).tone(Tone.GOOD, *Array(4) { cellId(0, it) }),
                    then = "Four filled keys. You broke the code!", help = "The yellow peg."),
            ),
        )
    }

    val battleship: Tutorial get() {
        fun sea(
            ships: Set<String> = emptySet(),
            hits: Set<String> = emptySet(),
            fog: Boolean = false,
            mark: String? = null,
            crosses: Set<String> = emptySet(),
        ): Scene {
            val drawn = gridItems(4, 5) { r, c ->
                val id = cellId(r, c)
                Cell(
                    fill = if (fog && id !in hits && id !in crosses && id !in ships) Fill.SHADED else Fill.OPEN,
                    piece = when (id) {
                        in ships -> Piece(PieceKind.SHIP, 1)
                        in hits -> Piece(PieceKind.SHIP, -1)
                        else -> null
                    },
                    mark = when {
                        id == mark -> Mark.CIRCLE
                        id in crosses -> Mark.CROSS
                        else -> Mark.NONE
                    },
                )
            }
            return Scene(5f, 4f, drawn, Backdrop.BOARD, maxUnit = 56)
        }
        val bow = cellId(1, 1)
        val stern = cellId(1, 2)
        val splash = cellId(0, 3)
        val sunk = (1..3).map { cellId(2, it) }.toSet()
        return Tutorial(
            GameId.BATTLESHIP,
            "Place your fleet, then sink the computer's ships.",
            rules = listOf(
                "Each fleet has a carrier (5), a battleship (4), a cruiser (3), a submarine (3) and a destroyer (2).",
                "Ships sit in a straight line. They may touch, but they cannot overlap or leave the board.",
                "Tap or drag to place the bow. Rotate turns the ship. Randomize places the whole fleet.",
                "You shoot first at hidden water. A shot is a miss, a hit, or the shot that sinks a named ship. Sink every enemy ship to win.",
            ),
            tips = listOf(
                "On Easy the computer mostly fires at random. Higher levels hunt around hits.",
                "When a ship sinks you learn its name, so you can stop firing along that line.",
            ),
            steps = listOf(
                Step("Your grid shows your ships. The computer's grid stays fog until you fire.", sea()),
                Step("Tap the marked square to place a destroyer across two squares.", sea(mark = bow), tap = setOf(bow),
                    after = sea(ships = setOf(bow, stern)), then = "The destroyer is placed. Keep going until every ship is down.",
                    help = "The circled square."),
                Step("Tap fog to fire. This square is empty.", sea(fog = true, mark = splash), tap = setOf(splash),
                    after = sea(fog = true, crosses = setOf(splash)), then = "A miss. Ships stay hidden until you hit them.",
                    help = "The circled square of fog."),
                Step("Three hits in a line sink the cruiser. Sink the rest of the fleet to win.",
                    sea(hits = sunk).tone(Tone.GOOD, *sunk.toTypedArray())),
            ),
        )
    }

    val mancala: Tutorial get() {
        fun scene(pits: List<Int>, mark: Int? = null): Scene {
            val items = buildList {
                add(Item("cpu", 0f, 0f, 1f, 2f, Cell(text = pits[Mancala.CPU].toString(), fill = Fill.SHADED), describe = "Computer's store"))
                for (i in 0 until 6) {
                    val top = 12 - i
                    val bot = i
                    add(Item("t$i", 1f + i, 0f, look = Cell(text = pits[top].toString(), fill = Fill.OPEN, mark = if (mark == top) Mark.CIRCLE else Mark.NONE)))
                    add(Item("b$i", 1f + i, 1f, look = Cell(
                        text = pits[bot].toString(),
                        fill = Fill.PAPER,
                        mark = if (mark == bot) Mark.CIRCLE else Mark.NONE,
                    )))
                }
                add(Item("you", 7f, 0f, 1f, 2f, Cell(text = pits[Mancala.YOU].toString(), fill = Fill.LIGHT), describe = "Your store"))
            }
            return Scene(8f, 2f, items, Backdrop.BOARD, maxUnit = 44)
        }
        val opening = Mancala.OPENING
        val again = Mancala(0, 1, opening).sow(2)!!.pits
        return Tutorial(
            GameId.MANCALA,
            "Sow stones to the right and capture the pit opposite.",
            rules = listOf(
                "You have six pits of four stones, and a store on the right. The computer's store is on the left.",
                "Tap a pit to sow its stones one at a time to the right. Your store collects a stone. The computer's store is skipped.",
                "Land in your store and you go again. Land in an empty pit on your side and you take that stone plus the pit opposite.",
                "When either side runs out of stones, the other side keeps what is left. Most stones in your store wins.",
            ),
            tips = listOf(
                "A pit that lands in your store is worth more than it looks, because you move again.",
                "Count the stones before you sow. The last stone is the one that captures.",
            ),
            steps = listOf(
                Step("Your pits are the bottom row. Stones move to the right, into the store on the right.", scene(opening)),
                Step("This pit has 4 stones, which reaches your store. Tap it.", scene(opening, mark = 2), tap = setOf("b2"),
                    after = scene(again), then = "The last stone landed in your store, so you go again.",
                    help = "The circled pit, third from the left."),
                Step("The next three pits each gained a stone, and your store has 1.", scene(again)),
            ),
        )
    }

    val fiveRow: Tutorial get() {
        fun scene(rows: List<String>, mark: String? = null, good: Set<String> = emptySet()): Scene {
            val items = gridItems(rows.size, rows[0].length) { r, c ->
                Cell(
                    fill = Fill.OPEN,
                    piece = when (rows[r][c]) {
                        'Y' -> Piece(PieceKind.STONE, 1)
                        'C' -> Piece(PieceKind.STONE, -1)
                        else -> null
                    },
                    mark = if (cellId(r, c) == mark) Mark.CIRCLE else Mark.NONE,
                )
            }
            val base = Scene(rows[0].length.toFloat(), rows.size.toFloat(), items, Backdrop.BOARD, maxUnit = 56)
            return if (good.isEmpty()) base else base.tone(Tone.GOOD, *good.toTypedArray())
        }
        val empty = listOf(".....", ".....", ".....")
        val placed = listOf(".....", "..Y..", ".....")
        val answered = listOf(".....", "..YC.", ".....")
        val threat = listOf(".C...", "YYY.Y", "C.C..")
        val won = listOf(".C...", "YYYYY", "C.C..")
        val line = (0 until 5).map { cellId(1, it) }.toSet()
        return Tutorial(
            GameId.FIVE_ROW,
            "Place stones and line up five.",
            rules = listOf(
                "Take turns placing one stone on an empty square. Stones do not move.",
                "Five or more in a line — across, down or diagonal — wins.",
                "The board is 11×11. Filling it with no line of five is a draw.",
                "You play the dark stones and move first.",
            ),
            tips = listOf(
                "A line of four with both ends open needs two blocks.",
                "Stay near the stones already on the board. Far-away stones rarely connect in time.",
            ),
            steps = listOf(
                Step("Stones stay where you put them. A line can run across, down or diagonally.", scene(empty)),
                Step("Tap the middle square.", scene(empty, mark = "r1c2"), tap = setOf("r1c2"),
                    after = scene(placed), then = "Your stone is down.", help = "The circled square."),
                Step("The computer answers beside it.", scene(answered)),
                Step("Four of yours, with one gap. Tap the gap.", scene(threat, mark = "r1c3"), tap = setOf("r1c3"),
                    after = scene(won, good = line), then = "Five in a row. You win!", help = "The empty square in your line."),
            ),
        )
    }

    val chess: Tutorial get() {
        fun scene(pieces: Map<String, String>, selected: String? = null, targets: Set<String> = emptySet()): Scene {
            val items = gridItems(4, 4) { r, c ->
                val id = cellId(r, c)
                Cell(
                    fill = if ((r + c) % 2 == 0) Fill.LIGHT else Fill.DARK,
                    text = pieces[id].orEmpty(),
                    mark = if (id in targets) Mark.CIRCLE else Mark.NONE,
                )
            }.map { if (it.id == selected) it.copy(tone = Tone.SELECTED) else it }
            return Scene(4f, 4f, items, Backdrop.BOARD, maxUnit = 64)
        }
        val start = mapOf("r0c1" to "♟", "r2c0" to "♙", "r3c1" to "♔")
        val stepped = mapOf("r0c1" to "♟", "r1c0" to "♙", "r3c1" to "♔")
        val capture = mapOf("r1c1" to "♟", "r2c0" to "♙", "r3c1" to "♔")
        val taken = mapOf("r1c1" to "♙", "r3c1" to "♔")
        val check = mapOf("r0c3" to "♚", "r2c3" to "♕", "r3c1" to "♔")
        return Tutorial(
            GameId.CHESS,
            "Standard chess. You play white from the bottom and move first.",
            rules = listOf(
                "Tap a piece, then a highlighted square. Capture by landing on the other piece.",
                "Pawns move one square forward, or two from their starting row, and capture one square diagonally forward.",
                "Check means your king is attacked. You must escape, block, or capture the attacker.",
                "Checkmate wins. Stalemate, when a side has no legal move and is not in check, is a draw. The same position three times, or 50 moves each with no capture and no pawn move, is also a draw. Pawns promote to a queen. Castling and en passant follow the usual rules.",
            ),
            tips = listOf(
                "Develop a piece toward the center before you attack.",
                "Do not leave your king in check.",
            ),
            steps = listOf(
                Step("You play the white pieces from the bottom. Tap your pawn.", scene(start), tap = setOf("r2c0"),
                    after = scene(start, selected = "r2c0", targets = setOf("r1c0")), help = "The white pawn."),
                Step("It can step one square forward. Tap that square.", scene(start, selected = "r2c0", targets = setOf("r1c0")),
                    tap = setOf("r1c0"), after = scene(stepped), then = "The pawn moved forward."),
                Step("A pawn captures one square diagonally. Tap your pawn.", scene(capture), tap = setOf("r2c0"),
                    after = scene(capture, selected = "r2c0", targets = setOf("r1c1")), help = "The white pawn next to the black pawn."),
                Step("Take the black pawn.", scene(capture, selected = "r2c0", targets = setOf("r1c1")), tap = setOf("r1c1"),
                    after = scene(taken), then = "Captured."),
                Step("The white queen looks straight at the black king. That is check.", scene(check)),
            ),
        )
    }

    val go: Tutorial get() {
        fun scene(stones: Map<String, Int>, mark: String? = null): Scene {
            val items = gridItems(4, 4) { r, c ->
                val id = cellId(r, c)
                Cell(
                    fill = Fill.LIGHT,
                    piece = stones[id]?.let { Piece(PieceKind.STONE, it) },
                    mark = if (id == mark) Mark.CIRCLE else Mark.NONE,
                )
            }
            return Scene(4f, 4f, items, Backdrop.BOARD, maxUnit = 64)
        }
        val white = mapOf("r0c1" to 1, "r1c0" to 1, "r1c1" to -1, "r1c2" to 1)
        val captured = mapOf("r0c1" to 1, "r1c0" to 1, "r1c2" to 1, "r2c1" to 1)
        return Tutorial(
            GameId.GO,
            "Place stones, surround groups, and claim the empty points.",
            rules = listOf(
                "You play black and move first on a 19×19 board. Stones stay where you put them.",
                "A connected group with no empty neighbor is captured and taken off.",
                "You cannot fill your own last liberty, and you cannot repeat an earlier board.",
                "Pass when you are finished. Two passes stop play so groups that cannot live can be marked, then counted. Your score is your stones plus empty regions that touch only your color. White receives 7.5 points.",
            ),
            tips = listOf(
                "Mark groups that cannot live after both players pass. Count removes them. Stones still on the board count for their owner.",
                "Do not fill a point that is already surrounded by your own stones.",
            ),
            steps = listOf(
                Step("The white stone has one empty neighbor. Tap it.", scene(white, mark = "r2c1"), tap = setOf("r2c1"),
                    after = scene(captured), then = "Captured. The white stone comes off.", help = "The empty point under the white stone."),
                Step("Connected stones share their empty neighbors. One empty neighbor left means the group can be taken.", scene(captured)),
                Step("When both players pass, empty regions that touch only your stones count for you. White receives 7.5 points.", scene(captured)),
            ),
        )
    }

    val yacht: Tutorial get() {
        fun dice(faces: List<Int>, held: Set<Int> = emptySet()) = Scene(5f, 1f, faces.mapIndexed { i, face ->
            Item("d$i", i.toFloat(), 0f, look = Cell(text = face.toString(), fill = if (i in held) Fill.SHADED else Fill.PAPER),
                describe = if (i in held) "Die showing $face, held" else "Die showing $face")
        }, maxUnit = 64)
        val start = listOf(4, 4, 4, 2, 1)
        val rolled = listOf(4, 4, 4, 4, 6)
        val holdHelp = "Hold a 4. The 2 and the 1 are the ones to roll again."
        return Tutorial(
            GameId.YACHT,
            "Five dice, up to three rolls, then one box.",
            rules = listOf(
                "Roll five dice up to three times. Tap a die to hold it, then roll the rest.",
                "Score the roll in one open box. Each box is used once. Ones through Sixes add that face, and 63 or more there scores 35 more.",
                "Three and four of a kind add all five dice. A full house scores 25, a small straight 30, a large straight 40, five of a kind scores 50, and Chance adds all five dice.",
                "After five of a kind scores 50, each later one adds 100 and fills the matching upper box when it is open. The higher sheet wins.",
            ),
            tips = listOf("Scoring 0 spends that box and saves a later roll for a better one."),
            steps = listOf(
                Step("Three 4s is a start. You may roll twice more.", dice(start)),
                Step("Tap a 4 to hold it.", dice(start), tap = setOf("d0"), after = dice(start, setOf(0)),
                    then = "That 4 stays.", help = holdHelp),
                Step("Tap a 4 to hold it.", dice(start, setOf(0)), tap = setOf("d1"), after = dice(start, setOf(0, 1)), help = holdHelp),
                Step("Tap a 4 to hold it.", dice(start, setOf(0, 1)), tap = setOf("d2"), after = dice(start, setOf(0, 1, 2)),
                    then = "The 2 and the 1 will roll again.", help = holdHelp),
                Step("Roll the dice you did not hold.", dice(start, setOf(0, 1, 2)).choices("Roll again"), pick = "Roll again", after = dice(rolled)),
                Step("Four 4s. Four of a kind adds every die.", dice(rolled).choices("Four of a kind"), pick = "Four of a kind",
                    then = "22 points. Four of a kind adds all five dice."),
            ),
        )
    }

    val shogi: Tutorial get() {
        fun scene(pieces: Map<String, String>, selected: String? = null, targets: Set<String> = emptySet()): Scene {
            val items = gridItems(3, 3) { r, c ->
                val id = cellId(r, c)
                Cell(
                    fill = if ((r + c) % 2 == 0) Fill.LIGHT else Fill.DARK,
                    text = pieces[id].orEmpty(),
                    mark = if (id in targets) Mark.CIRCLE else Mark.NONE,
                )
            }.map { if (it.id == selected) it.copy(tone = Tone.SELECTED) else it }
            return Scene(3f, 3f, items, Backdrop.BOARD, maxUnit = 72)
        }
        val start = mapOf("r2c1" to "P", "r0c1" to "k")
        return Tutorial(
            GameId.SHOGI,
            "Shogi. You play Sente from the bottom and move first.",
            rules = listOf(
                "Tap one of your pieces, then a highlighted square. Pawns move and capture one square forward.",
                "In the far three rows a piece may promote. A pawn, lance, or knight that could not move again must promote.",
                "Captured pieces go into your hand. Tap one, then an empty square, to drop it.",
                "A side with no legal move loses. The same position four times is a draw, unless one side gave check on every move of that repeat, in which case that side loses.",
            ),
            tips = listOf(
                "A dropped pawn cannot share a file with one of your unpromoted pawns.",
                "A pawn drop that checkmates is not allowed.",
            ),
            steps = listOf(
                Step("You play from the bottom. Tap your pawn.", scene(start), tap = setOf("r2c1"),
                    after = scene(start, selected = "r2c1", targets = setOf("r1c1")), help = "The pawn."),
                Step("It moves one square forward. Tap that square.", scene(start, selected = "r2c1", targets = setOf("r1c1")),
                    tap = setOf("r1c1"), after = scene(mapOf("r1c1" to "P", "r0c1" to "k"))),
                Step("Pieces taken from the board can be dropped back onto an empty square.", scene(mapOf("r1c1" to "P", "r0c1" to "k"))),
            ),
        )
    }

    val hex: Tutorial get() {
        fun scene(n: Int, stones: Map<Pair<Int, Int>, Int>, mark: Pair<Int, Int>? = null, good: Set<Pair<Int, Int>> = emptySet()): Scene {
            val radius = 0.62f
            val cell = 0.8f
            val items = (0 until n).flatMap { row ->
                (0 until n).map { col ->
                    val cx = radius + radius * 1.5f * col
                    val cy = radius * 0.9f + radius * 0.9f * (row + col / 2f)
                    val who = stones[row to col]
                    Item(
                        cellId(row, col),
                        cx - cell / 2f,
                        cy - cell / 2f,
                        cell,
                        cell,
                        Cell(
                            fill = Fill.OPEN,
                            piece = who?.let { Piece(PieceKind.STONE, it) },
                            mark = if (mark == row to col) Mark.CIRCLE else Mark.NONE,
                        ),
                        tone = if (row to col in good) Tone.GOOD else Tone.PLAIN,
                    )
                }
            }
            return Scene(items.maxOf { it.x + it.w } + 0.06f, items.maxOf { it.y + it.h } + 0.06f, items, Backdrop.BOARD, maxUnit = 40)
        }
        val n = 4
        val opening = scene(n, emptyMap(), 0 to 2)
        val placed = scene(n, mapOf(0 to 2 to 1))
        val swapped = scene(n, mapOf(2 to 0 to -1))
        val threat = scene(n, mapOf(0 to 1 to 1, 1 to 1 to 1, 3 to 1 to 1, 0 to 0 to -1, 1 to 3 to -1), 2 to 1)
        val won = scene(n, mapOf(0 to 1 to 1, 1 to 1 to 1, 2 to 1 to 1, 3 to 1 to 1, 0 to 0 to -1, 1 to 3 to -1), good = (0 until n).map { it to 1 }.toSet())
        return Tutorial(
            GameId.HEX,
            "Join your two opposite sides.",
            rules = listOf(
                "Take turns placing one stone on an empty hex. Stones never move or get captured.",
                "Red joins the top and bottom edges. Blue joins the left and right. The edges are tinted to match.",
                "Two hexes touch when they share a side. Each hex has six neighbors, so a square diagonal does not connect.",
                "After the first stone, the second player may swap. The stone flips across the long diagonal and becomes theirs.",
                "You play red against the computer and move first, or pass the phone for two players. A full board always has a winner.",
            ),
            tips = listOf(
                "Swap when the first stone sits near the middle. A corner stone is usually worth leaving.",
                "Two of your stones with a single gap between them are hard to cut off.",
            ),
            steps = listOf(
                Step("Red owns the top and bottom. Blue owns the left and right. Tap a hex to place a stone.", opening),
                Step("Tap the circled hex.", opening, tap = setOf(cellId(0, 2)), after = placed,
                    then = "Red's stone is down. It stays there unless Blue swaps.", help = "The circled hex."),
                Step("Blue may take that stone instead of placing. Swap flips it across the long diagonal.", placed.choices("Swap"),
                    pick = "Swap", after = swapped, then = "The stone moved to the mirror hex and turned blue."),
                Step("Red needs one more stone to join top and bottom. Tap the gap.", threat, tap = setOf(cellId(2, 1)),
                    after = won, then = "Red's chain meets both edges. Red wins!", help = "The empty hex between the red stones."),
            ),
        )
    }

    val rps: Tutorial get() {
        fun scene(
            n: Int,
            pieces: Map<Pair<Int, Int>, Pair<String, Boolean>>,
            mark: Pair<Int, Int>? = null,
            good: Set<Pair<Int, Int>> = emptySet(),
        ): Scene {
            val radius = 0.62f
            val cell = 0.8f
            val items = (0 until n).flatMap { row ->
                (0 until n).map { col ->
                    val cx = radius + radius * 1.5f * col
                    val cy = radius * 0.9f + radius * 0.9f * (row + col / 2f)
                    val piece = pieces[row to col]
                    val letter = piece?.first ?: ""
                    Item(
                        cellId(row, col),
                        cx - cell / 2f,
                        cy - cell / 2f,
                        cell,
                        cell,
                        Cell(
                            fill = Fill.OPEN,
                            text = letter,
                            mark = if (mark == row to col) Mark.CIRCLE else Mark.NONE,
                        ),
                        tone = when {
                            row to col in good -> Tone.GOOD
                            piece?.second == true -> Tone.ENTERED
                            else -> Tone.PLAIN
                        },
                        describe = when (letter) {
                            "R" -> if (piece?.second == true) "your rock" else "blue rock"
                            "P" -> if (piece?.second == true) "your paper" else "blue paper"
                            "S" -> if (piece?.second == true) "your scissors" else "blue scissors"
                            else -> if (mark == row to col) "the circled hex" else "empty hex"
                        },
                    )
                }
            }
            return Scene(items.maxOf { it.x + it.w } + 0.06f, items.maxOf { it.y + it.h } + 0.06f, items, Backdrop.BOARD, maxUnit = 44)
        }
        val yours: (String) -> Pair<String, Boolean> = { it to true }
        val blue: (String) -> Pair<String, Boolean> = { it to false }
        val n = 3
        val camps = scene(n, mapOf(
            0 to 0 to blue("R"), 0 to 1 to blue("P"), 0 to 2 to blue("S"),
            2 to 0 to yours("R"), 2 to 1 to yours("P"), 2 to 2 to yours("S"),
        ))
        val rock = scene(n, mapOf(1 to 1 to blue("S"), 2 to 1 to yours("R")), mark = 1 to 1)
        val captured = scene(n, mapOf(1 to 1 to yours("R")), good = setOf(1 to 1))
        val blocked = scene(n, mapOf(1 to 0 to blue("S"), 2 to 0 to yours("P")), mark = 1 to 1)
        val stepped = scene(n, mapOf(1 to 0 to blue("S"), 1 to 1 to yours("P")))
        val last = scene(n, mapOf(1 to 2 to blue("P"), 1 to 1 to yours("S")), mark = 1 to 2)
        val finish = scene(n, mapOf(1 to 2 to yours("S")), good = setOf(1 to 2))
        return Tutorial(
            GameId.RPS,
            "Capture with rock, paper, and scissors on a hex board.",
            rules = listOf(
                "Each side starts with the same mix of rock, paper, and scissors on opposite sides of the hex board.",
                "On your turn, move one of your pieces one hex in any of the six directions.",
                "Land on an empty hex, or on an enemy your type beats. Paper beats rock, rock beats scissors, and scissors beats paper.",
                "You cannot jump, and you cannot land on your own piece, the same type, or a type that beats you. Landing on an enemy captures it.",
                "Capture every enemy piece, or leave the opponent with no legal move, to win. You play red from the bottom against the computer, or pass the phone.",
            ),
            tips = listOf(
                "Take a capture when the landing hex is not next to a type that beats you.",
                "A piece that cannot move is stuck. Leave the whole side with no move and you win.",
            ),
            steps = listOf(
                Step(
                    "Red starts at the bottom, blue at the top. R is rock, P is paper, and S is scissors.",
                    camps.copy(caption = "Paper beats rock, rock beats scissors, and scissors beats paper."),
                ),
                Step(
                    "Rock beats scissors. Tap the circled hex to capture.",
                    rock, tap = setOf(cellId(1, 1)), after = captured,
                    then = "Rock landed on the scissors and captured it.", help = "The circled hex.",
                ),
                Step(
                    "Scissors beats paper, so paper cannot land there. Tap the empty hex instead.",
                    blocked, tap = setOf(cellId(1, 1)), after = stepped,
                    then = "Paper moved one hex onto an empty cell.", help = "The empty hex beside your paper.",
                ),
                Step(
                    "Scissors beats paper. Tap the last paper to win.",
                    last, tap = setOf(cellId(1, 2)), after = finish,
                    then = "That was the last blue piece. Red wins!", help = "The circled paper.",
                ),
            ),
        )
    }
}

internal object ArcadeTutorials {
    val g2048: Tutorial get() {
        fun scene(rows: List<List<Int>>, choices: List<String> = listOf("←", "↑", "↓", "→")): Scene {
            val items = gridItems(4, 4) { r, c -> rows[r][c].let { if (it == 0) Cell(fill = Fill.OPEN) else Value(it) } }
            return Scene(4f, 4f, items, Backdrop.BOARD, choices = choices, maxUnit = 64)
        }
        val a = listOf(listOf(2, 2, 0, 0), listOf(4, 0, 0, 4), listOf(0, 0, 0, 0), listOf(2, 0, 0, 0))
        val b = listOf(listOf(4, 0, 0, 0), listOf(8, 0, 0, 0), listOf(2, 0, 0, 0), listOf(2, 0, 0, 0))
        val c = listOf(listOf(0, 0, 0, 2), listOf(4, 0, 0, 0), listOf(8, 0, 0, 0), listOf(4, 0, 0, 0))
        return Tutorial(
            GameId.G2048,
            "Slide tiles to merge equal numbers and build big tiles.",
            rules = listOf(
                "Swipe (or use the arrows) to slide every tile as far as it goes in that direction.",
                "Two tiles with the same number that collide merge into one tile with their sum. A tile merges once per move.",
                "After every move a new 2 (sometimes a 4) appears in an empty square.",
                "Reach the goal tile to win (512 to 8192 by level); the game ends when no move is possible. Undo takes back a move.",
            ),
            tips = listOf("Keep your biggest tile in a corner and build next to it.", "Avoid swiping in the direction that pulls your big tile out of its corner."),
            steps = listOf(
                Step("Every swipe slides all tiles as far as they go. Equal tiles that meet merge into their sum.", scene(a, emptyList())),
                Step("Swipe left: the two 2s merge, and so do the two 4s.", scene(a), pick = "←", after = scene(b, emptyList()),
                    then = "4 and 8! A new 2 appeared in an empty square."),
                Step("The two 2s at the bottom of the left column can merge. Swipe down.", scene(b), pick = "↓", after = scene(c, emptyList()),
                    then = "They merged into a 4 at the bottom. Keep building towards one corner."),
            ),
        )
    }

    val tetras: Tutorial get() {
        fun scene(piece: List<Pair<Int, Int>>, rows: List<String>, choices: List<String> = listOf("◀ Left", "⟳ Rotate", "Right ▶", "⤓ Drop"), label: String = ""): Scene {
            val items = gridItems(7, 6) { r, c ->
                when {
                    (r to c) in piece -> Block(0)
                    rows[r][c] == '#' -> Block(3)
                    else -> Cell(fill = Fill.OPEN)
                }
            } + if (label.isEmpty()) emptyList() else listOf(Item("", 0f, 7.1f, 6f, 0.6f, Label(label, 0.4f, bold = true)))
            return Scene(6f, 7.8f, items, Backdrop.BOARD, choices = choices, maxUnit = 44)
        }
        val floor = listOf("......", "......", "......", "......", "###...", "#####.", "#####.")
        val flat = (1..4).map { 0 to it }
        fun upright(col: Int) = (0..3).map { it to col }
        val cleared = listOf("......", "......", "......", "......", "......", ".....#", "###..#")
        return Tutorial(
            GameId.TETRAS,
            "Fit falling blocks together to clear full rows.",
            rules = listOf(
                "Pieces of four blocks fall one at a time. Move and rotate them before they land.",
                "A row filled from edge to edge disappears and scores; clearing several rows at once scores more.",
                "The game speeds up every 10 rows. It ends when a new piece can't enter the well.",
                "Tap the board to rotate, drag sideways to move, swipe down to drop, or use the buttons. The ghost shows where the piece will land.",
            ),
            tips = listOf("Keep the stack flat and leave one column open for a long piece.", "Watch the next pieces to plan ahead."),
            steps = listOf(
                Step("The bottom rows are full except for the rightmost column: a perfect spot for this long piece.", scene(flat, floor, emptyList())),
                Step("Stand the piece upright: tap Rotate.", scene(flat, floor), pick = "⟳ Rotate", after = scene(upright(3), floor, emptyList())),
                Step("Move it right.", scene(upright(3), floor), pick = "Right ▶", after = scene(upright(4), floor, emptyList())),
                Step("Once more to reach the gap.", scene(upright(4), floor), pick = "Right ▶", after = scene(upright(5), floor, emptyList())),
                Step("Drop it into the gap.", scene(upright(5), floor), pick = "⤓ Drop", after = scene(emptyList(), cleared, emptyList(), "2 rows cleared!"),
                    then = "Both full rows vanished and everything above moved down."),
            ),
        )
    }
}
