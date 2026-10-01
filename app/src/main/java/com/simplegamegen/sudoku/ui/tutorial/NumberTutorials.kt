package com.simplegamegen.sudoku.ui.tutorial

import com.simplegamegen.sudoku.ui.GameId

/** A digit grid from [rows] ('.' empty) with thick walls from [regions] and optional dashed cages. */
internal fun digitScene(
    rows: List<String>,
    regions: List<String>,
    cages: List<String>? = null,
    corners: Map<String, String> = emptyMap(),
    choices: List<String> = emptyList(),
    solidCages: Boolean = false,
): Scene {
    val n = rows.size
    val items = gridItems(n, rows[0].length) { r, c ->
        val ch = rows[r][c]
        var walls = regionSides(regions, r, c)
        var cage = 0
        if (cages != null) { if (solidCages) walls = walls or regionSides(cages, r, c) else cage = regionSides(cages, r, c, outer = true) }
        Cell(text = if (ch == '.') "" else ch.toString(), corner = corners[cellId(r, c)] ?: "", walls = walls, cage = cage)
    }
    return Scene(rows[0].length.toFloat(), n.toFloat(), items, choices = choices, maxUnit = 64)
}

/** Box ids for [n]×[n] grids with [bh]×[bw] boxes. */
internal fun boxes(n: Int, bh: Int, bw: Int): List<String> =
    (0 until n).map { r -> (0 until n).map { c -> 'a' + (r / bh) * (n / bw) + c / bw }.joinToString("") }

private val Digits4 = listOf("1", "2", "3", "4")

internal object NumberTutorials {
    val sudoku: Tutorial get() {
        val start = digitScene(listOf("123.", ".4.2", "2.4.", ".3.1"), boxes(4, 2, 2), choices = Digits4)
        val s1 = start.tone(Tone.SELECTED, "r0c3")
        val s2 = start.text("r0c3", "4")
        val s3 = s2.tone(Tone.SELECTED, "r2c1")
        val s4 = s2.text("r2c1", "1")
        val s5 = s4.tone(Tone.SELECTED, "r3c0")
        val s6 = s4.text("r3c0", "4")
        val solved = digitScene(listOf("1234", "3412", "2143", "4321"), boxes(4, 2, 2)).allTone(Tone.GOOD)
        return Tutorial(
            GameId.SUDOKU,
            "Place digits so no row, column or box repeats one.",
            rules = listOf(
                "Fill every empty square with a digit: 1–9 on a 9×9 board, 1–6 on 6×6 and 1–4 on 4×4.",
                "Each row, each column and each outlined box must contain every digit exactly once.",
                "The starting digits are fixed and can't be changed.",
                "Every puzzle has exactly one solution, so it can always be solved by logic.",
                "Variants add one extra rule: Diagonal X (both long diagonals hold each digit once), Jigsaw (odd-shaped boxes), " +
                    "Killer (cages add up to their totals), Thermo (digits rise from the bulb), Kropki (a white dot joins consecutive " +
                    "digits, a black dot joins a digit and its double), Arrow (digits on an arrow add up to its circle) and Sandwich " +
                    "(the total between 1 and 9 in a row or column is shown outside).",
            ),
            tips = listOf(
                "Look for a row, column or box that is missing only one or two digits.",
                "Switch on Notes to pencil in candidates; placing a digit clears it from the notes around it.",
                "Hint shows the next logical step; Undo takes back any move.",
            ),
            steps = listOf(
                Step("This is a small 4×4 Sudoku. Every row, column and 2×2 box needs 1, 2, 3 and 4 exactly once. " +
                    "The thick lines mark the boxes.", start),
                Step("The top row already has 1, 2 and 3. Tap its empty square.", start, tap = setOf("r0c3"),
                    then = "Selected. Now choose its digit.", after = s1),
                Step("Which digit is missing from the top row?", s1, pick = "4", then = "Right: 4 completes the row.", after = s2,
                    help = "The row has 1, 2 and 3, so only one digit is left."),
                Step("The second column has 2, 4 and 3. Tap its empty square.", s2, tap = setOf("r2c1"), after = s3,
                    help = "Look down the second column from the left."),
                Step("Which digit completes that column?", s3, pick = "1", then = "Yes: 1 is the only digit left for that column.", after = s4),
                Step("The bottom-left box now holds 2, 1 and 3. Tap its empty square.", s4, tap = setOf("r3c0"), after = s5,
                    help = "The bottom-left box is the lower two rows of the first two columns."),
                Step("Which digit finishes the box?", s5, pick = "4", then = "Well done. That's all Sudoku is: one careful deduction at a time.", after = s6),
                Step("Here is the finished grid. Bigger boards work the same way with 3×3 or 2×3 boxes, and every square has exactly one answer.", solved),
            ),
        )
    }

    val killer: Tutorial get() {
        val cages = listOf("ABBC", "ADDC", "EFFG", "EFHG")
        val corners = mapOf("r0c0" to "4", "r0c1" to "5", "r0c3" to "6", "r1c1" to "5", "r2c0" to "6", "r2c1" to "8", "r2c3" to "4", "r3c2" to "2")
        val start = digitScene(listOf("....", "....", "....", "...."), boxes(4, 2, 2), cages, corners, choices = Digits4)
        val s1 = start.tone(Tone.SELECTED, "r3c2")
        val s2 = start.text("r3c2", "2")
        val pairs = s2.copy(choices = listOf("1 + 3", "2 + 2", "1 + 4"))
        val s3 = s2.cell("r0c0") { it.copy(notes = "1 3") }.cell("r1c0") { it.copy(notes = "1 3") }
        val s4 = s3.copy(choices = Digits4).tone(Tone.SELECTED, "r3c0")
        val s5 = s3.copy(choices = Digits4).text("r3c0", "4").text("r2c0", "2")
        val solved = digitScene(listOf("1234", "3412", "2143", "4321"), boxes(4, 2, 2), cages, corners).allTone(Tone.GOOD)
        return Tutorial(
            GameId.KILLER,
            "Sudoku with no starting digits: dashed cages add up to their totals.",
            rules = listOf(
                "Normal Sudoku rules apply: each row, column and 3×3 box holds 1–9 exactly once.",
                "Dashed cages show a total in their corner. The digits inside a cage add up to that total.",
                "A digit can't repeat inside a cage.",
                "There are no starting digits; the cage totals give everything you need.",
            ),
            tips = listOf(
                "Single-square cages give their digit straight away.",
                "Some totals have only one set of digits: two squares making 3 must be 1 + 2, and making 17 must be 8 + 9.",
                "Each row, column and box of a 9×9 grid adds up to 45; compare that with the cages inside it.",
            ),
            steps = listOf(
                Step("Killer Sudoku starts empty. Each dashed cage shows a total, and its digits add up to it without repeating. " +
                    "Rows, columns and boxes still hold each digit once.", start),
                Step("A one-square cage is free: its total is its digit. Tap the cage marked 2.", start, tap = setOf("r3c2"), after = s1,
                    help = "It's in the bottom row, third from the left."),
                Step("What goes in a one-square cage with total 2?", s1, pick = "2", then = "Exactly.", after = s2),
                Step("The top-left cage needs 4 from two different digits. Which pair works?", pairs, pick = "1 + 3",
                    then = "Right: 2 + 2 would repeat a digit, and 1 + 4 makes 5. Both squares get notes 1 and 3.", after = s3,
                    help = "Digits can't repeat inside a cage."),
                Step("The bottom-left cage makes 6 from two squares, which on a 4×4 can only be 2 + 4. The bottom row already has a 2, " +
                    "so tap its corner square.", s3.copy(choices = Digits4), tap = setOf("r3c0"), after = s4,
                    help = "Look at the cage in the bottom-left corner."),
                Step("Which digit goes in the corner?", s4, pick = "4", then = "Yes, and the square above it must be 2.", after = s5),
                Step("Keep going the same way. On 9×9 boards, remember that every row, column and box adds up to 45.", solved),
            ),
        )
    }

    val samurai: Tutorial get() {
        // Each outer grid is 3×3 boxes; the centre grid overlaps one corner box of each.
        val grids = listOf("tl" to (0f to 0f), "tr" to (4f to 0f), "bl" to (0f to 4f), "br" to (4f to 4f), "c" to (2f to 2f))
        val items = grids.flatMap { (g, pos) ->
            (0 until 9).map { k ->
                val r = k / 3; val c = k % 3
                var walls = 0
                if (r == 0) walls = walls or 1; if (c == 2) walls = walls or 2; if (r == 2) walls = walls or 4; if (c == 0) walls = walls or 8
                Item("$g$k", pos.first + c, pos.second + r, look = Cell(walls = walls), describe = "Grid box")
            }
        }
        val sharedPos = listOf(2f to 2f, 4f to 2f, 2f to 4f, 4f to 4f)
        val shared = sharedPos.mapIndexed { i, (x, y) -> Item("s$i", x, y, look = Cell(walls = 15, text = "✦"), tone = Tone.FOUND, describe = "Shared corner box") }
        val map = Scene(7f, 7f, items + shared, maxUnit = 44)
        val sharedIds = shared.map { it.id }.toSet()
        val centre = (0 until 9).map { "c$it" }.toSet() + sharedIds
        val row = digitScene(listOf("53.678912"), listOf("aaabbbccc"), choices = (1..9).map { it.toString() })
        return Tutorial(
            GameId.SAMURAI,
            "Five Sudoku grids joined at their corners.",
            rules = listOf(
                "The board is five 9×9 Sudoku grids: one in the centre and one on each corner.",
                "Each grid follows normal Sudoku rules: every row, column and 3×3 box holds 1–9 once.",
                "The centre grid shares each of its corner boxes with an outer grid. Digits there count for both grids.",
                "Rows and columns don't continue from one grid into another; only the shared boxes link them.",
            ),
            tips = listOf(
                "Shared corner boxes are the key: a digit found in one grid often unlocks its neighbour.",
                "Tap a grid on the map, or use the X-shaped picker, to play it full size.",
                "Notes, Check and Hint work just like in Sudoku.",
            ),
            steps = listOf(
                Step("This map shows the five grids, each drawn as its 3×3 boxes. The starred boxes belong to two grids at once.", map),
                Step("Tap one of the shared corner boxes.", map, tap = sharedIds, after = map.map(sharedIds) { it.copy(tone = Tone.GOOD) },
                    then = "A digit in this box counts for both grids, so solving one grid helps the next."),
                Step("In the game, you tap a grid on the map to open it full size. Tap the centre grid.", map, tap = centre,
                    after = map.map(centre) { it.copy(tone = Tone.SELECTED) }, then = "The centre grid touches all four others."),
                Step("Inside each grid it's normal Sudoku. This row of the centre grid is missing one digit. Tap its empty square.",
                    row, tap = setOf("r0c2"), after = row.tone(Tone.SELECTED, "r0c2")),
                Step("Which digit is missing from 5 3 _ 6 7 8 9 1 2?", row.tone(Tone.SELECTED, "r0c2"), pick = "4",
                    then = "Correct. Work across the grids and use the shared boxes to pass digits between them.",
                    after = row.text("r0c2", "4")),
            ),
        )
    }

    val kenken: Tutorial get() {
        val cages = listOf("AAB", "CDB", "CEE")
        val corners = mapOf("r0c0" to "3+", "r0c2" to "6×", "r1c0" to "1−", "r1c1" to "1", "r2c1" to "3÷")
        val digits = listOf("1", "2", "3")
        val start = digitScene(listOf("...", "...", "..."), listOf("aaa", "aaa", "aaa"), cages, corners, choices = digits, solidCages = true)
        val s1 = start.tone(Tone.SELECTED, "r1c1")
        val s2 = start.text("r1c1", "1")
        val s3 = s2.tone(Tone.SELECTED, "r2c1")
        val s4 = s2.text("r2c1", "3").text("r2c2", "1")
        val s5 = s4.tone(Tone.SELECTED, "r2c0")
        val s6 = s4.text("r2c0", "2").text("r1c0", "3")
        val solved = digitScene(listOf("123", "312", "231"), listOf("aaa", "aaa", "aaa"), cages, corners, solidCages = true).allTone(Tone.GOOD)
        return Tutorial(
            GameId.KENKEN,
            "Latin squares with arithmetic cages.",
            rules = listOf(
                "Fill an N×N grid with 1 to N so each row and column holds every digit once (4×4 to 7×7 by level).",
                "Bold cages show a target and an operation (+ − × ÷). The digits in the cage must make the target with it.",
                "For − and ÷ cages (always two squares), take the larger digit first: 3 − 1 = 2, 6 ÷ 2 = 3.",
                "Digits may repeat inside a cage as long as they're in different rows and columns.",
                "A one-square cage simply gives its digit. Easy puzzles use only + and −.",
            ),
            tips = listOf(
                "Start with single squares and cages with only one possible combination.",
                "A 2-square × cage with a prime target (like 5× on a 5×5) must include 1.",
                "Notes help to track the few pairs a cage allows.",
            ),
            steps = listOf(
                Step("Fill 1, 2 and 3 so each row and column has each once. Every bold cage must make its target with its operation.", start),
                Step("A one-square cage gives its answer. Tap the cage marked 1.", start, tap = setOf("r1c1"), after = s1,
                    help = "It's the middle square."),
                Step("What goes there?", s1, pick = "1", after = s2, then = "Right."),
                Step("The 3÷ cage needs two digits where one divided by the other is 3: only 3 and 1. The middle column already has " +
                    "a 1, so its bottom square must be 3. Tap it.", s2, tap = setOf("r2c1"), after = s3, help = "The bottom-middle square."),
                Step("Enter the digit.", s3, pick = "3", after = s4, then = "Good: that leaves 1 for the other square of the cage."),
                Step("The bottom row now has 3 and 1. Tap the square that still needs a digit.", s4, tap = setOf("r2c0"), after = s5),
                Step("What completes the bottom row?", s5, pick = "2", after = s6,
                    then = "Yes, and the 1− cage makes the square above it 3, since 3 − 2 = 1."),
                Step("The rest follows: 6× needs 3 and 2 in the right column, and 3+ is 1 + 2. That's Calcudoku: arithmetic plus Sudoku logic.", solved),
            ),
        )
    }

    val kakuro: Tutorial get() {
        val digits = (1..9).map { it.toString() }
        val items = listOf(
            Item("r0c0", 0f, 0f, look = Cell(fill = Fill.BLOCK)),
            Item("r0c1", 1f, 0f, look = Clue(down = 4)), Item("r0c2", 2f, 0f, look = Clue(down = 6)),
            Item("r1c0", 0f, 1f, look = Clue(across = 3)), Item("r1c1", 1f, 1f, look = Cell()), Item("r1c2", 2f, 1f, look = Cell()),
            Item("r2c0", 0f, 2f, look = Clue(across = 7)), Item("r2c1", 1f, 2f, look = Cell()), Item("r2c2", 2f, 2f, look = Cell()),
        )
        val start = Scene(3f, 3f, items, choices = digits, maxUnit = 72)
        val s1 = start.tone(Tone.SELECTED, "r1c1")
        val s2 = start.text("r1c1", "1")
        val s3 = s2.tone(Tone.SELECTED, "r1c2")
        val s4 = s2.text("r1c2", "2")
        val s5 = s4.tone(Tone.SELECTED, "r2c1")
        val s6 = s4.text("r2c1", "3").text("r2c2", "4")
        return Tutorial(
            GameId.KAKURO,
            "Cross sums: runs of squares add up to their clues.",
            rules = listOf(
                "Put a digit from 1 to 9 in every white square.",
                "Each run of white squares across or down adds up to the clue at its start. The number above the slash is the across total; below it, the down total.",
                "A digit can't repeat within one run.",
                "Every puzzle has exactly one solution.",
            ),
            tips = listOf(
                "Some totals have only one set of digits: 3 in two squares is 1 + 2; 4 in two is 1 + 3; 16 in two is 7 + 9.",
                "Where an across run and a down run cross, the digit must fit both.",
            ),
            steps = listOf(
                Step("Each white run adds up to its clue. Across totals sit above the slash in the square to the left of a run, down totals below the slash above it.", start),
                Step("The top run must make 3 from two different digits, so it's 1 and 2. Tap the first square of that run.", start,
                    tap = setOf("r1c1"), after = s1),
                Step("Its down run makes 4. If this square were 2, the square below would also need 2, which can't repeat in a run. So which digit?",
                    s1, pick = "1", after = s2, then = "Right: 1 here and 3 below it makes 4.", help = "Try each of 1 and 2 against the down total 4."),
                Step("Tap the other square of the top run.", s2, tap = setOf("r1c2"), after = s3),
                Step("1 + ? = 3", s3, pick = "2", after = s4, then = "Good."),
                Step("The down total 4 now needs 3 under the 1. Tap that square.", s4, tap = setOf("r2c1"), after = s5),
                Step("Enter it.", s5, pick = "3", after = s6, then = "And 6 − 2 = 4 fills the last square. Check: 3 + 4 = 7 matches the bottom clue."),
            ),
        )
    }

    val futoshiki: Tutorial get() {
        val digits = listOf("1", "2", "3")
        val base = digitScene(listOf("..2", "...", "..."), listOf("aaa", "aaa", "aaa"), choices = digits)
        val start = base.map("r0c0") { it.copy(right = "<", below = "∧") }.map("r1c0") { it.copy(right = ">") }.map("r2c1") { it.copy(right = ">") }
        val s1 = start.tone(Tone.SELECTED, "r0c0")
        val s2 = start.text("r0c0", "1").text("r0c1", "3")
        val s3 = s2.tone(Tone.SELECTED, "r2c1")
        val s4 = s2.text("r2c1", "2").text("r2c2", "1")
        val solved = s4.text("r1c1", "1", Tone.GOOD).text("r1c2", "3", Tone.GOOD).text("r1c0", "2", Tone.GOOD).text("r2c0", "3", Tone.GOOD).allTone(Tone.GOOD)
        return Tutorial(
            GameId.FUTOSHIKI,
            "A Latin square with greater-than signs.",
            rules = listOf(
                "Fill an N×N grid with 1 to N so every row and column holds each digit once (4×4 to 7×7 by level).",
                "A sign between two squares shows which is larger. The open side of < or > faces the larger digit; ∧ and ∨ work the same up and down.",
                "Easier levels start with more digits and signs.",
                "Every puzzle has exactly one solution.",
            ),
            tips = listOf(
                "A square that is smaller than a neighbour can't hold the largest digit, and a larger one can't hold 1.",
                "Chains of signs (a < b < c) pin digits quickly.",
            ),
            steps = listOf(
                Step("Fill 1–3 so each row and column has each digit once. The signs say which neighbour is bigger: the open side faces the larger digit.", start),
                Step("The top row still needs 1 and 3, and its first square is smaller than its second. Tap the first square.", start,
                    tap = setOf("r0c0"), after = s1),
                Step("Which digit goes in the smaller square?", s1, pick = "1", after = s2, then = "Right, so the square beside it is 3."),
                Step("In the bottom row, the middle square is bigger than the right one, so it can't be 1. Its column already has 3. Tap it.",
                    s2, tap = setOf("r2c1"), after = s3, help = "The bottom-middle square has a > sign on its right."),
                Step("Which digit is left for it?", s3, pick = "2", after = s4, then = "Yes, and the square to its right must be smaller: 1."),
                Step("Rows and columns finish the rest. Every sign is satisfied.", solved),
            ),
        )
    }

    val nonogram: Tutorial get() {
        val picture = listOf(".#.#.", "#####", "#####", ".###.", "..#..")
        val rowClues = listOf(listOf(1, 1), listOf(5), listOf(5), listOf(3), listOf(1))
        val colClues = listOf(2, 4, 4, 4, 2)
        val labels = rowClues.flatMapIndexed { r, clue ->
            clue.reversed().mapIndexed { k, n -> Item("", 1f - k, 1f + r, look = Label(n.toString(), bold = true)) }
        } + colClues.mapIndexed { c, n -> Item("", 2f + c, 0f, look = Label(n.toString(), bold = true)) }
        fun scene(state: List<String>, choices: List<String>): Scene {
            val cells = gridItems(5, 5, 2f, 1f) { r, c ->
                when (state[r][c]) { '#' -> Cell(fill = Fill.BLOCK); 'x' -> Cell(mark = Mark.CROSS); else -> Cell() }
            }
            return Scene(7f, 6f, labels + cells, choices = choices, maxUnit = 52)
        }
        val modes = listOf("Fill", "Cross")
        val start = scene(listOf(".....", ".....", ".....", ".....", "....."), modes)
        val s1 = scene(listOf(".....", "#####", ".....", ".....", "....."), modes)
        val s2 = scene(listOf(".....", "#####", "#####", ".....", "....."), modes)
        val s3 = scene(listOf("x...x", "#####", "#####", "x...x", "x...x"), modes)
        val s4 = scene(listOf("x#x#x", "#####", "#####", "x...x", "x...x"), modes)
        val solved = scene(picture.map { it.replace('.', 'x') }, emptyList())
        return Tutorial(
            GameId.NONOGRAM,
            "Paint a hidden picture from number clues.",
            rules = listOf(
                "The numbers beside each row and above each column list its runs of filled squares, in order.",
                "Runs are separated by at least one empty square. A clue of 0 means the whole line is empty.",
                "Fill paints a square; Cross marks one you know is empty. Crosses are only reminders.",
                "Every picture can be solved by logic alone, so it has exactly one answer.",
            ),
            tips = listOf(
                "Start with big clues: a 5 in a 5-square line fills everything, and a 4 always fills the middle three.",
                "Drag along a row or column to paint several squares in one move.",
                "When a line's clue is complete, cross off its remaining squares.",
            ),
            steps = listOf(
                Step("Each clue lists the runs of filled squares in that row or column, in order, with gaps between runs.", start.copy(choices = emptyList())),
                Step("Row 2 says 5, so every square in it is filled. Tap it to paint the row (in the game you can drag across it).",
                    start, tap = (0 until 5).map { cellId(1, it) }.toSet(), after = s1),
                Step("Row 3 is also 5. Paint it too.", s1, tap = (0 until 5).map { cellId(2, it) }.toSet(), after = s2),
                Step("Column 1 says 2, and rows 2 and 3 already fill two squares in it, so its other squares are empty. Switch to Cross mode.",
                    s2, pick = "Cross", after = s2, then = "Cross marks squares you know are empty."),
                Step("Tap the top-left square to cross off column 1.", s2, tap = setOf("r0c0", "r3c0", "r4c0"), after = s3,
                    then = "Column 5 is the same, so its spare squares are crossed too."),
                Step("The top row (1 1) now has three open squares in the middle. Two runs of 1 with a gap only fit as filled, empty, filled. Switch back to Fill.",
                    s3, pick = "Fill", after = s3),
                Step("Paint the second square of the top row.", s3, tap = setOf("r0c1"), after = s4, then = "Its partner and the gap follow."),
                Step("Rows 4 and 5 finish the same way, and the heart appears. In the game, finished rows and columns grey out their clues.", solved),
            ),
        )
    }

    val hitori: Tutorial get() {
        val numbers = listOf("2224", "2341", "3414", "4123")
        fun scene(shaded: Set<String>, circled: Set<String>): Scene {
            val items = gridItems(4, 4) { r, c ->
                val id = cellId(r, c)
                Cell(text = numbers[r][c].toString(), fill = if (id in shaded) Fill.SHADED else Fill.PAPER, mark = if (id in circled) Mark.CIRCLE else Mark.NONE)
            }
            return Scene(4f, 4f, items, maxUnit = 64)
        }
        val start = scene(emptySet(), emptySet())
        val s1 = scene(setOf("r0c0"), emptySet())
        val s2 = scene(setOf("r0c0", "r0c2"), emptySet())
        val s3 = scene(setOf("r0c0", "r0c2"), setOf("r0c1"))
        val s4 = scene(setOf("r0c0", "r0c2"), setOf("r0c1", "r2c2"))
        val s5 = scene(setOf("r0c0", "r0c2", "r2c3"), setOf("r0c1", "r2c2"))
        return Tutorial(
            GameId.HITORI,
            "Shade repeated numbers without cutting the grid apart.",
            rules = listOf(
                "Shade squares so no number appears more than once in any row or column among the unshaded squares.",
                "Shaded squares may not touch each other side by side (touching at corners is fine).",
                "All unshaded squares must stay connected in one area.",
                "In the game: tap once to shade, twice to circle a square you know stays white, three times to clear.",
            ),
            tips = listOf(
                "In three equal numbers in a row (A A A), the two outer ones are shaded.",
                "In A B A, the middle B stays white: shading it would leave both As white.",
                "Every neighbour of a shaded square stays white; circle it.",
            ),
            steps = listOf(
                Step("Shade squares so no number repeats in a row or column, shaded squares never touch side by side, and the white squares stay connected.", start),
                Step("The top row has three 2s side by side. You can't shade two neighbours, and only one 2 may stay white, so both outer 2s are shaded. Tap the first one.",
                    start, tap = setOf("r0c0"), after = s1),
                Step("Shade the other outer 2.", s1, tap = setOf("r0c2"), after = s2, help = "Third square of the top row."),
                Step("A square next to a shaded square must stay white. Circle the middle 2 to remember that (in the game, tap twice).",
                    s2, tap = setOf("r0c1"), after = s3),
                Step("Row 3 reads 3 4 1 4. The 1 sits between two 4s: if it were shaded, both 4s would have to stay white. Circle the 1.",
                    s3, tap = setOf("r2c2"), after = s4),
                Step("The last column has two 4s. The top one touches a shaded square, so it stays white. Shade the lower 4.",
                    s4, tap = setOf("r2c3"), after = s5,
                    then = "Solved: no repeats, no touching shaded squares, and all white squares connect."),
            ),
        )
    }

    private fun dots(label: String): Scene = Scene(3f, 1.4f, listOf(
        Item("a", 0f, 0f, look = Letter("2"), describe = "2"),
        Item("b", 2f, 0f, look = Letter("1"), describe = "1"),
        Item("note", 0f, 0.9f, 3f, 0.5f, Label(label, 0.4f)),
    ), maxUnit = 56)

    val bridges: Tutorial get() = Tutorial(
        GameId.BRIDGES,
        "Join numbered islands with the right number of bridges.",
        rules = listOf(
            "A number is how many bridges touch that island.",
            "Bridges run straight across or down. One line or two may share a route.",
            "Bridges never cross, and every island ends up in one connected group.",
        ),
        tips = listOf("An island showing 1 has only one bridge to place."),
        steps = listOf(
            Step("Tap an island, then another in the same row or column.", dots("Islands"), tap = setOf("a"),
                after = dots("Islands").tone(Tone.SELECTED, "a")),
            Step("A second tap on the same pair makes a double bridge.", dots("Double")),
            Step("The puzzle is done when every number is satisfied and nothing is left apart.", dots("Linked")),
        ),
    )

    val slitherlink: Tutorial get() = Tutorial(
        GameId.SLITHERLINK,
        "Draw one loop. Each number is how many sides of that square the loop uses.",
        rules = listOf(
            "The loop follows the grid lines and closes on itself.",
            "A number is how many of that square's four sides are part of the loop.",
            "The line does not branch or cross.",
        ),
        tips = listOf("A 0 forbids every side of that square. A 3 forces three sides."),
        steps = listOf(
            Step("Tap a grid line to draw it. Tap it again to erase it.", dots("Lines"), tap = setOf("a"),
                after = dots("Lines").tone(Tone.SELECTED, "a")),
            Step("A 2 means exactly two sides of that square are used.", dots("Two sides")),
            Step("Keep going until there is one closed loop and every number is right.", dots("Loop")),
        ),
    )

    val towers: Tutorial get() = Tutorial(
        GameId.TOWERS,
        "Fill the grid so the side clues match how many towers you can see.",
        rules = listOf(
            "Each row and column holds every height once, from 1 up to the grid size.",
            "Looking in from a clue, you see a tower when it is taller than everything in front of it.",
            "A taller tower hides the shorter ones behind it.",
        ),
        tips = listOf("A clue of 1 means the tallest tower is first. A clue equal to the grid size means the heights rise the whole way."),
        steps = listOf(
            Step("Tap a square to raise its height, from empty through the tallest.", dots("Heights"), tap = setOf("a"),
                after = dots("Heights").tone(Tone.SELECTED, "a")),
            Step("From the left, a clue of 1 means the tallest tower in that row is on the left.", dots("See one")),
            Step("The puzzle is done when every row, column and side clue agrees.", dots("Skyline")),
        ),
    )

    val lights: Tutorial get() = Tutorial(
        GameId.LIGHTS,
        "Place lamps so every white square is lit and no two lamps see each other.",
        rules = listOf(
            "A lamp shines up, down, left and right until a black square blocks it.",
            "Every white square must be lit, by a lamp on it or by a beam.",
            "Two lamps cannot see each other. A number is how many lamps touch that black square.",
        ),
        tips = listOf("A black 0 means no lamp touches it. A black square whose number matches its white neighbors must hold a lamp on each of them."),
        steps = listOf(
            Step("Tap a white square to place a lamp. Tap again to remove it.", dots("Lamp"), tap = setOf("a"),
                after = dots("Lamp").tone(Tone.SELECTED, "a")),
            Step("Light stops at a black square. The number there counts the lamps beside it.", dots("Number")),
            Step("Finish when every white square is lit and every number is right.", dots("Lit")),
        ),
    )

    val slidingBlocks: Tutorial get() = Tutorial(
        GameId.SLIDING_BLOCKS,
        "Slide vehicles so the marked one reaches the exit.",
        rules = listOf(
            "Each vehicle is two or three cells and only slides along its length.",
            "A move slides one vehicle any number of free cells. Vehicles cannot overlap or leave the grid.",
            "The marked target wins when its front reaches the exit on the right of its row.",
        ),
        tips = listOf("Clear blockers off the target's row, then drive it out. Hint plays the next move on a shortest path."),
        steps = listOf(
            Step("Tap a vehicle to select it, or drag it along its length.", dots("Select"), tap = setOf("a"),
                after = dots("Select").tone(Tone.SELECTED, "a")),
            Step("The marked target must reach the exit on the right of its row.", dots("Exit")),
            Step("Clear a path, then slide the target out. Hint plays the next shortest move.", dots("Clear")),
        ),
    )

    val klotski: Tutorial get() = Tutorial(
        GameId.KLOTSKI,
        "Slide the big block down to the opening.",
        rules = listOf(
            "The board is 4 cells wide and 5 tall, with two empty cells.",
            "Slide one block one square at a time, up, down, left or right, into empty space.",
            "The big square wins when it sits on the opening at the bottom. Each square a block slides counts as one move.",
        ),
        tips = listOf("Expert is the classic opening. Hint plays the next shortest step."),
        steps = listOf(
            Step("Tap a block, then an arrow, or drag it into an empty square.", dots("Slide"), tap = setOf("a"),
                after = dots("Slide").tone(Tone.SELECTED, "a")),
            Step("The big square is the one that has to reach the opening.", dots("Big block")),
            Step("Clear a path downward, then slide it onto the opening.", dots("Opening")),
        ),
    )
}
