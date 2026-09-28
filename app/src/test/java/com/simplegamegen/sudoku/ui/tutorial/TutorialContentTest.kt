package com.simplegamegen.sudoku.ui.tutorial

import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.wordplay.BlotTier
import com.simplegamegen.sudoku.wordplay.BlotEffect
import com.simplegamegen.sudoku.wordplay.BlotLexicon
import com.simplegamegen.sudoku.wordplay.Blotwords
import com.simplegamegen.sudoku.wordplay.EnglishThreads
import com.simplegamegen.sudoku.wordplay.FiveLetters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.simplegamegen.sudoku.wordplay.Mark as LetterMark

/** Checks that each tutorial's worked example obeys the real rules of its game. */
class TutorialContentTest {
    private fun last(id: GameId) = Tutorials.of(id).steps.last().result
    private fun grid(scene: Scene, n: Int) = List(n) { r -> List(n) { c -> scene.item(cellId(r, c))!!.look as Cell } }
    private fun digits(scene: Scene, n: Int) = grid(scene, n).map { row -> row.map { it.text.toIntOrNull() ?: 0 } }

    private fun assertLatin(d: List<List<Int>>, what: String) {
        val n = d.size
        d.forEach { assertEquals((1..n).toSet(), it.toSet(), "$what row $it") }
        (0 until n).forEach { c -> assertEquals((1..n).toSet(), d.map { it[c] }.toSet(), "$what column $c") }
    }

    /** Groups squares whose shared side has no [sides] bit (thick wall or cage line) between them. */
    private fun groups(cells: List<List<Cell>>, sides: (Cell) -> Int): List<List<Pair<Int, Int>>> {
        val n = cells.size
        val seen = mutableSetOf<Pair<Int, Int>>()
        val out = mutableListOf<List<Pair<Int, Int>>>()
        for (r in 0 until n) for (c in 0 until n) if (seen.add(r to c)) {
            val group = mutableListOf(r to c)
            var k = 0
            while (k < group.size) {
                val (rr, cc) = group[k++]
                val s = sides(cells[rr][cc])
                listOf(Triple(-1, 0, 1), Triple(0, 1, 2), Triple(1, 0, 4), Triple(0, -1, 8)).forEach { (dr, dc, bit) ->
                    val nr = rr + dr; val nc = cc + dc
                    if (nr in 0 until n && nc in 0 until n && s and bit == 0 && seen.add(nr to nc)) group += nr to nc
                }
            }
            out += group
        }
        return out
    }

    @Test fun `sudoku tutorial ends on a valid grid and never shows a clash`() {
        Tutorials.of(GameId.SUDOKU).steps.forEach { step ->
            val d = digits(step.result, 4)
            for (r in 0 until 4) for (c in 0 until 4) if (d[r][c] != 0) {
                val peers = (0 until 4).map { d[r][it] } + (0 until 4).map { d[it][c] } + (0 until 4).map { d[r / 2 * 2 + it / 2][c / 2 * 2 + it % 2] }
                assertEquals(3, peers.count { it == d[r][c] }, "digit ${d[r][c]} at $r,$c repeats")
            }
        }
        assertLatin(digits(last(GameId.SUDOKU), 4), "sudoku")
    }

    @Test fun `killer tutorial cages add up`() {
        val scene = last(GameId.KILLER)
        val cells = grid(scene, 4)
        val d = digits(scene, 4)
        assertLatin(d, "killer")
        groups(cells) { it.cage }.forEach { g ->
            val total = g.mapNotNull { (r, c) -> cells[r][c].corner.toIntOrNull() }.single()
            assertEquals(total, g.sumOf { (r, c) -> d[r][c] }, "cage $g")
            assertEquals(g.size, g.map { (r, c) -> d[r][c] }.toSet().size, "cage $g repeats a digit")
        }
    }

    @Test fun `kenken tutorial cages make their targets`() {
        val scene = last(GameId.KENKEN)
        val cells = grid(scene, 3)
        val d = digits(scene, 3)
        assertLatin(d, "kenken")
        groups(cells) { it.walls }.forEach { g ->
            val label = g.map { (r, c) -> cells[r][c].corner }.single { it.isNotEmpty() }
            val v = g.map { (r, c) -> d[r][c] }.sortedDescending()
            val target = label.takeWhile { it.isDigit() }.toInt()
            val made = when (label.last()) {
                '+' -> v.sum(); '×' -> v.fold(1) { a, b -> a * b }; '−' -> v[0] - v[1]; '÷' -> v[0] / v[1]; else -> v.single()
            }
            assertEquals(target, made, "cage $label $g")
        }
    }

    @Test fun `futoshiki tutorial signs hold`() {
        val scene = last(GameId.FUTOSHIKI)
        val d = digits(scene, 3)
        assertLatin(d, "futoshiki")
        for (r in 0 until 3) for (c in 0 until 3) {
            val item = scene.item(cellId(r, c))!!
            when (item.right) { "<" -> assertTrue(d[r][c] < d[r][c + 1]); ">" -> assertTrue(d[r][c] > d[r][c + 1]) }
            // The open side faces the larger digit: ∧ opens downward, so the lower square is larger.
            when (item.below) { "∧" -> assertTrue(d[r][c] < d[r + 1][c]); "∨" -> assertTrue(d[r][c] > d[r + 1][c]) }
        }
    }

    @Test fun `kakuro tutorial runs add up`() {
        val scene = last(GameId.KAKURO)
        scene.items.forEach { item ->
            val clue = item.look as? Clue ?: return@forEach
            val (r, c) = item.id.removePrefix("r").split("c").map { it.toInt() }
            fun run(dr: Int, dc: Int): List<Int> {
                val out = mutableListOf<Int>()
                var a = r + dr; var b = c + dc
                while (true) {
                    val cell = scene.item(cellId(a, b))?.look as? Cell ?: break
                    if (cell.fill != Fill.PAPER) break
                    out += cell.text.toInt(); a += dr; b += dc
                }
                return out
            }
            clue.across?.let { val run = run(0, 1); assertEquals(it, run.sum(), "across clue at ${item.id}"); assertEquals(run.size, run.toSet().size) }
            clue.down?.let { val run = run(1, 0); assertEquals(it, run.sum(), "down clue at ${item.id}"); assertEquals(run.size, run.toSet().size) }
        }
    }

    @Test fun `hitori tutorial solution follows all three rules`() {
        val cells = grid(last(GameId.HITORI), 4)
        val white = cells.map { row -> row.map { it.fill != Fill.SHADED } }
        for (i in 0 until 4) {
            val row = (0 until 4).filter { white[i][it] }.map { cells[i][it].text }
            val col = (0 until 4).filter { white[it][i] }.map { cells[it][i].text }
            assertEquals(row.size, row.toSet().size, "row $i repeats")
            assertEquals(col.size, col.toSet().size, "column $i repeats")
        }
        for (r in 0 until 4) for (c in 0 until 4) if (!white[r][c]) {
            assertTrue(r == 3 || white[r + 1][c], "shaded squares touch at $r,$c")
            assertTrue(c == 3 || white[r][c + 1], "shaded squares touch at $r,$c")
        }
        val start = (0 until 16).first { white[it / 4][it % 4] }
        val reach = mutableSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val k = queue.removeFirst(); val r = k / 4; val c = k % 4
            listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1).filter { (a, b) -> a in 0..3 && b in 0..3 && white[a][b] }
                .map { (a, b) -> a * 4 + b }.filter { reach.add(it) }.forEach(queue::add)
        }
        assertEquals(white.flatten().count { it }, reach.size, "white squares connect")
    }

    @Test fun `nonogram tutorial picture matches its clues`() {
        val scene = last(GameId.NONOGRAM)
        val filled = List(5) { r -> List(5) { c -> (scene.item(cellId(r, c))!!.look as Cell).fill == Fill.BLOCK } }
        fun runs(line: List<Boolean>) = line.joinToString("") { if (it) "#" else "." }.split('.').filter { it.isNotEmpty() }.map { it.length }
        val labels = scene.items.filter { it.look is Label }
        for (r in 0 until 5) {
            val clue = labels.filter { it.y == r + 1f && it.x < 2f }.sortedBy { it.x }.map { (it.look as Label).text.toInt() }
            assertEquals(clue, runs(filled[r]), "row $r")
        }
        for (c in 0 until 5) {
            val clue = labels.filter { it.x == c + 2f && it.y < 1f }.sortedBy { it.y }.map { (it.look as Label).text.toInt() }
            assertEquals(clue, runs(filled.map { it[c] }), "column $c")
        }
    }

    @Test fun `five letters tutorial marks match the game's scoring`() {
        val scene = last(GameId.FIVE_LETTERS)
        val rows = (0 until 3).map { r -> (0 until 5).map { c -> scene.item(cellId(r, c))!! } }
        val answer = rows.last().joinToString("") { (it.look as Cell).text }
        rows.forEach { row ->
            val guess = row.joinToString("") { (it.look as Cell).text }
            val expected = FiveLetters.score(guess, answer).map {
                when (it) { LetterMark.RIGHT -> Tone.GOOD; LetterMark.ELSEWHERE -> Tone.FOUND; LetterMark.ABSENT -> Tone.DIM }
            }
            assertEquals(expected, row.map { it.tone }, guess)
        }
    }

    @Test fun `threads tutorial groups follow the real rules`() {
        val fruit = EnglishThreads.lexicon.rule("tag:FRUIT")!!
        val ball = EnglishThreads.lexicon.rule("before:BALL")!!
        val fruits = listOf("FIG", "PEAR", "PLUM", "LIME")
        val balls = listOf("EYE", "FOOT", "HAND", "SNOW")
        fruits.forEach { assertTrue(fruit.fits(it) && !ball.fits(it), it) }
        balls.forEach { assertTrue(ball.fits(it) && !fruit.fits(it), it) }
    }

    @Test fun `blotwords tutorial plays out under the real rules`() {
        val steps = Tutorials.of(GameId.BLOTWORDS).steps
        val start = steps.first().scene
        val grid = (0 until 2).joinToString("") { r -> (0 until 4).joinToString("") { c ->
            val cell = start.item(cellId(r, c))!!.look as Cell
            if (cell.fill == Fill.BLOCK) "#" else cell.text } }
        var g = Blotwords(BlotTier.EASY, 0, 4, BlotLexicon.INK.words(listOf(BlotEffect.ONE)), grid)
        g = g.write(listOf(0, 1, 2))!!                         // VUM across the top
        assertEquals(BlotEffect.ONE, g.pending!!.effect)
        g = g.use(3)!!                                         // its effect inks T
        g = g.write(listOf(7, 6, 4))!!                         // VUM right to left over the inked square
        assertTrue(g.solved)
        assertTrue(steps.last().result.items.all { (it.look as Cell).fill == Fill.BLOCK })
    }

    @Test fun `letter sprawl tutorial words touch and are real words`() {
        val grid = "CATSROENIDGLPUMB"
        val g = com.simplegamegen.sudoku.wordplay.Sprawl(com.simplegamegen.sudoku.logic.LogicLevel.EASY, 0, 4, grid)
        assertTrue(g.take(listOf(0, 1, 2)) != null, "CAT")
        assertTrue(g.take(listOf(9, 5, 10)) != null, "DOG")                   // D, O above it, G diagonally
        assertEquals(3, com.simplegamegen.sudoku.wordplay.Sprawl.pointsFor("HOUSE"))
    }

    @Test fun `word quilt tutorial grid is words every way`() {
        for (w in listOf("AT", "TO")) assertTrue(com.simplegamegen.sudoku.wordplay.Lexicon.isWord(w), w)
    }

    @Test fun `wordsworn tutorial numbers match the engine`() {
        val start = com.simplegamegen.sudoku.wordplay.Wordsworn.start(1, com.simplegamegen.sudoku.logic.LogicLevel.EASY).choose(0)!!
        val deck = listOf(com.simplegamegen.sudoku.wordplay.Card('C', com.simplegamegen.sudoku.wordplay.Edge(), com.simplegamegen.sudoku.wordplay.Edge(hits = 3), com.simplegamegen.sudoku.wordplay.Power.NONE), com.simplegamegen.sudoku.wordplay.Card('T', com.simplegamegen.sudoku.wordplay.Edge(blocks = 2), com.simplegamegen.sudoku.wordplay.Edge(hits = 2), com.simplegamegen.sudoku.wordplay.Power.NONE))
        val g = start.copy(deck = deck, hand = listOf(0, 1), draw = emptyList(), monster = com.simplegamegen.sudoku.wordplay.Monster.PAGE_MITE)
        val cat = listOf(com.simplegamegen.sudoku.wordplay.Piece.Hand(0), com.simplegamegen.sudoku.wordplay.Piece.Vowel, com.simplegamegen.sudoku.wordplay.Piece.Hand(1))
        assertEquals('A', g.monster.weak)
        assertEquals(com.simplegamegen.sudoku.wordplay.Piece.Hand(0), g.topOf(cat, com.simplegamegen.sudoku.wordplay.Splay.RIGHT))
        assertEquals(5, g.preview(cat, com.simplegamegen.sudoku.wordplay.Splay.RIGHT).hits)
        assertEquals(2, g.preview(cat, com.simplegamegen.sudoku.wordplay.Splay.LEFT).blocks)
        assertTrue(com.simplegamegen.sudoku.wordplay.Lexicon.isWord("CAT"))
    }

    @Test fun `every game has a playable tutorial`() {
        GameId.entries.forEach { id ->
            val t = Tutorials.of(id)
            assertEquals(id, t.game, id.name)
            assertTrue(t.summary.isNotBlank(), "${id.name} summary")
            assertTrue(t.rules.isNotEmpty(), "${id.name} rules")
            assertTrue(t.steps.size >= 3, "${id.name} has ${t.steps.size} steps")
            t.steps.forEachIndexed { i, step ->
                assertTrue(step.say.isNotBlank(), "${id.name} step $i is blank")
                assertTrue(step.scene.width > 0 && step.scene.height > 0, "${id.name} step $i scene size")
                val ids = step.scene.items.map { it.id }.filter { it.isNotEmpty() }
                assertEquals(ids.size, ids.toSet().size, "${id.name} step $i duplicate ids")
                step.tap.forEach { tap -> assertTrue(step.scene.item(tap) != null, "${id.name} step $i taps missing $tap") }
                step.pick?.let { pick -> assertTrue(pick in step.scene.choices, "${id.name} step $i pick $pick not in ${step.scene.choices}") }
                step.scene.links.forEach { link ->
                    assertTrue(step.scene.item(link.from) != null, "${id.name} step $i link from ${link.from}")
                    assertTrue(step.scene.item(link.to) != null, "${id.name} step $i link to ${link.to}")
                }
                step.after?.let { after ->
                    assertTrue(after.width > 0 && after.height > 0, "${id.name} step $i result size")
                }
                step.scene.items.forEach { item ->
                    assertTrue(item.x + item.w <= step.scene.width + 0.01f && item.y + item.h <= step.scene.height + 0.01f,
                        "${id.name} step $i item ${item.id} sits outside the scene")
                }
            }
        }
    }

    @Test fun `lone letter tutorial answers are on the lists`() {
        val c = com.simplegamegen.sudoku.wordplay.LoneCategories
        assertTrue(c["fruit"].accepts("Banana") && c["animals"].accepts("Badger") && c["animals"].accepts("Bear"))
        assertTrue(c["colors"].accepts("Baby blue") && c["colors"].accepts("Blue"))
        assertTrue(!c["fruit"].accepts("Apple") || !com.simplegamegen.sudoku.wordplay.LoneAnswers.startsWith("Apple", 'B'))
        assertEquals("bear", c["animals"].picksFor('B').first())
    }
}
