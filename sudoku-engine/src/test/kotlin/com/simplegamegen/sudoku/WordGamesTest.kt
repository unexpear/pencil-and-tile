package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.words.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WordGamesTest {
    @Test fun `crosswords contain connected clued runs without stray adjacent letters`() {
        for (difficulty in WordDifficulty.entries) repeat(50) { seed ->
            val puzzle = WordPuzzles.crossword(seed.toLong(), difficulty)
            assertTrue(puzzle.entries.size in difficulty.crosswordMinimum..difficulty.crosswordTarget, "$difficulty seed $seed")
            assertEquals(difficulty.crosswordSize, puzzle.size)
            assertEquals(difficulty, puzzle.difficulty)
            assertTrue(puzzle.entries.all { it.answer.length in difficulty.answerLengths })
            assertEquals(puzzle, WordPuzzles.crossword(seed.toLong(), difficulty))
            assertEquals(puzzle, WordSaveCodec.decode(WordSaveCodec.encode(WordProgress(puzzle)))!!.puzzle)
            val reached = puzzle.entries.first().cells.toMutableSet()
            repeat(puzzle.entries.size) {
                puzzle.entries.filter { e -> e.cells.any { it in reached } }.forEach { reached.addAll(it.cells) }
            }
            assertEquals(puzzle.entries.flatMap { it.cells }.toSet(), reached)
            val runs = mutableSetOf<List<Int>>()
            for (down in listOf(false, true)) for (cell in puzzle.letters.indices) {
                if (puzzle.letters[cell] == '#') continue
                val step = if (down) puzzle.size else 1
                val previous = if (down) cell - step else if (cell % puzzle.size > 0) cell - 1 else -1
                if (previous >= 0 && puzzle.letters[previous] != '#') continue
                val run = mutableListOf<Int>()
                var current = cell
                while (current < puzzle.letters.length && puzzle.letters[current] != '#' &&
                    (down || current / puzzle.size == cell / puzzle.size)) {
                    run += current
                    current += step
                }
                if (run.size > 1) runs += run
            }
            assertEquals(puzzle.entries.map { it.cells }.toSet(), runs, "unclued run at seed $seed")
            assertEquals((1..puzzle.entries.map { it.cells.first() }.distinct().size).toSet(),
                puzzle.entries.map(puzzle::number).toSet())
        }
    }

    @Test fun `all search themes produce solvable boards and accept reversed endpoints`() {
        for (theme in WordPuzzles.themes.indices) repeat(200) { seed ->
            val puzzle = WordPuzzles.wordSearch(seed.toLong(), theme)
            assertEquals(6, puzzle.entries.size)
            assertEquals(puzzle, WordPuzzles.wordSearch(seed.toLong(), theme))
            var progress = WordProgress(puzzle)
            for (entry in puzzle.entries) {
                progress = progress.find(entry.cells.last(), entry.cells.first())
                assertTrue(entry.answer in progress.found)
            }
            assertTrue(progress.complete)
            assertEquals(progress, WordSaveCodec.decode(WordSaveCodec.encode(progress)))
        }
    }

    @Test fun `selections reject bends wrapping and out of range endpoints`() {
        assertEquals(emptyList<Int>(), line(8, 7, 8))
        assertEquals(emptyList<Int>(), line(8, 0, 10))
        assertEquals(emptyList<Int>(), line(8, -1, 5))
        assertEquals(emptyList<Int>(), line(8, 0, 64))
        assertEquals(listOf(0, 9, 18, 27), line(8, 0, 27))
        val progress = WordProgress(WordPuzzles.wordSearch(42))
        assertEquals(progress, progress.find(-1, 64))
    }

    @Test fun `crossword entries share letters and completion requires correct answers`() {
        val puzzle = WordPuzzles.crossword(42)
        var progress = WordProgress(puzzle)
        val first = puzzle.entries.first()
        progress = progress.answer(first, "Z".repeat(first.answer.length))
        assertFalse(progress.complete)
        assertEquals(progress, WordSaveCodec.decode(WordSaveCodec.encode(progress)))
        for (entry in puzzle.entries) progress = progress.answer(entry, entry.answer.lowercase())
        assertTrue(progress.complete)
        assertEquals(progress, WordSaveCodec.decode(WordSaveCodec.encode(progress)))
        assertFalse(progress.answer(first, "").complete)
    }

    @Test fun `corrupt and future saves fail safely`() {
        val valid = WordSaveCodec.encode(WordProgress(WordPuzzles.crossword(12)))
        for (bad in listOf("", "1", valid.replaceFirst("3\n", "4\n"),
            valid.replaceFirst("\n9\n", "\n999\n"), valid + "\nBAD|clue|-1,999", "x".repeat(20001))) {
            assertNull(WordSaveCodec.decode(bad))
        }
    }

    @Test fun `alternate occurrences highlight the selected path and preserve it through saves`() {
        val puzzle = WordPuzzle(WordGame.WORD_SEARCH, 4, "Example", "CATXCATXXXXXXXXX",
            listOf(WordEntry("CAT", "CAT", listOf(0, 1, 2))))
        val found = WordProgress(puzzle).find(6, 4)
        assertEquals(listOf(6, 5, 4), found.foundPaths["CAT"])
        assertEquals(found, WordSaveCodec.decode(WordSaveCodec.encode(found)))
        assertEquals(found, found.find(0, 2))
        val legacy = listOf("1", "WORD_SEARCH", "4", "Example", puzzle.letters,
            "_".repeat(16), "0", "CAT", "CAT|CAT|0,1,2").joinToString("\n")
        assertTrue(WordSaveCodec.decode(legacy)!!.complete)
    }

    @Test fun `every search level respects word count size directions and varied word lists`() {
        for (difficulty in WordDifficulty.entries) for (theme in WordPuzzles.themes.indices) {
            val lists = mutableSetOf<Set<String>>()
            repeat(20) { seed ->
                val puzzle = WordPuzzles.wordSearch(seed.toLong(), theme, difficulty)
                assertEquals(difficulty, puzzle.difficulty)
                assertEquals(difficulty.searchSize, puzzle.size)
                assertEquals(difficulty.searchWords, puzzle.entries.size)
                assertEquals(puzzle, WordPuzzles.wordSearch(seed.toLong(), theme, difficulty))
                for (entry in puzzle.entries) {
                    val a = entry.cells[0]
                    val b = entry.cells[1]
                    val direction = (b / puzzle.size - a / puzzle.size) to (b % puzzle.size - a % puzzle.size)
                    assertTrue(direction in difficulty.searchDirections)
                    if (difficulty == WordDifficulty.EASY) assertTrue(entry.answer.length <= 5)
                }
                var solved = WordProgress(puzzle)
                puzzle.entries.forEach { solved = solved.find(it.cells.first(), it.cells.last()) }
                assertTrue(solved.complete)
                assertEquals(solved, WordSaveCodec.decode(WordSaveCodec.encode(solved)))
                lists += puzzle.entries.map { it.answer }.toSet()
            }
            assertTrue(lists.size > 1, "Theme $theme at $difficulty must vary its word list")
        }
    }

    @Test fun `expanded catalogs contain distinct valid words and original clues`() {
        assertEquals(12, WordPuzzles.themes.size)
        assertEquals(80, WordCatalog.moreClues.size)
        assertEquals(80, WordCatalog.moreClues.map { it.first }.distinct().size)
        WordCatalog.moreClues.forEach { (answer, clue) ->
            assertTrue(answer.all { it in 'A'..'Z' })
            assertTrue(clue.isNotBlank())
        }
        WordCatalog.searches.forEach { (_, words) ->
            assertEquals(20, words.size)
            assertEquals(words.size, words.distinct().size)
            assertTrue(words.all { word -> word.all { it in 'A'..'Z' } && word.length <= 12 })
        }
    }

    @Test fun `version two saves preserve highlights and are marked original`() {
        val legacy = "2\nWORD_SEARCH\n4\nExample\nCATXCATXXXXXXXXX\n________________\n1\nCAT:6,5,4\nCAT|CAT|0,1,2"
        val saved = WordSaveCodec.decode(legacy)!!
        assertNull(saved.puzzle.difficulty)
        assertEquals(listOf(6, 5, 4), saved.foundPaths["CAT"])
        assertEquals(saved, WordSaveCodec.decode(WordSaveCodec.encode(saved)))
    }
}
