package com.simplegamegen.sudoku.ui.tutorial

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
