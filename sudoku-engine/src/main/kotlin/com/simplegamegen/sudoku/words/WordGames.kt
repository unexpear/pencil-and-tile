package com.simplegamegen.sudoku.words

import kotlin.math.abs
import kotlin.random.Random
import com.simplegamegen.sudoku.solver.checkpoint

enum class WordGame { CROSSWORD, WORD_SEARCH }

enum class WordDifficulty(
    val label: String,
    val searchSize: Int,
    val searchWords: Int,
    val crosswordSize: Int,
    val crosswordMinimum: Int,
    val crosswordTarget: Int,
    val answerLengths: IntRange,
) {
    EASY("Easy", 8, 6, 7, 4, 5, 3..5),
    MEDIUM("Medium", 9, 8, 9, 6, 8, 4..7),
    HARD("Hard", 10, 10, 11, 8, 10, 5..8),
    EXPERT("Expert", 12, 12, 12, 10, 12, 6..10);

    val searchDirections: List<Pair<Int, Int>> get() = when (this) {
        EASY -> listOf(0 to 1, 1 to 0)
        MEDIUM -> listOf(0 to 1, 1 to 0, 1 to 1)
        HARD, EXPERT -> listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
    }

    fun description(game: WordGame): String = if (game == WordGame.CROSSWORD) {
        "$crosswordSize×$crosswordSize · $crosswordMinimum–$crosswordTarget clues · ${answerLengths.first}–${answerLengths.last} letter answers"
    } else {
        "$searchSize×$searchSize · $searchWords words · " + when (this) {
            EASY -> "right and down"
            MEDIUM -> "right, down and down-right diagonals"
            HARD, EXPERT -> "all directions, including backward"
        }
    }
}

data class WordEntry(val answer: String, val clue: String, val cells: List<Int>)

data class WordPuzzle(
    val game: WordGame,
    val size: Int,
    val title: String,
    val letters: String,
    val entries: List<WordEntry>,
    val difficulty: WordDifficulty? = null,
) {
    init {
        require(size in 4..12 && letters.length == size * size)
        require(letters.all { it in 'A'..'Z' || (game == WordGame.CROSSWORD && it == '#') })
        require(entries.isNotEmpty() && entries.map { it.answer }.distinct().size == entries.size)
        require(title.isNotBlank() && '\n' !in title)
        entries.forEach { entry ->
            require(entry.answer.length >= 2 && entry.answer.all { it in 'A'..'Z' })
            require(entry.clue.isNotBlank() && entry.clue.none { it == '|' || it == '\n' })
            require(entry.cells.size == entry.answer.length)
            require(entry.cells.all { it in letters.indices })
            require(line(size, entry.cells.first(), entry.cells.last()) == entry.cells)
            require(entry.cells.map { letters[it] }.joinToString("") == entry.answer)
            if (game == WordGame.CROSSWORD) require(entry.cells[1] - entry.cells[0] in listOf(1, size))
        }
        if (game == WordGame.CROSSWORD) {
            require(entries.flatMap { it.cells }.toSet() == letters.indices.filter { letters[it] != '#' }.toSet())
        }
    }

    fun number(entry: WordEntry): Int = entries.map { it.cells.first() }.distinct().sorted().indexOf(entry.cells.first()) + 1
    fun direction(entry: WordEntry): String = if (entry.cells[1] - entry.cells[0] == 1) "Across" else "Down"
}

/** A straight selection in any of eight directions, with no wrapping at edges. */
fun line(size: Int, start: Int, end: Int): List<Int> {
    if (size <= 0 || start !in 0 until size * size || end !in 0 until size * size) return emptyList()
    val dr = end / size - start / size
    val dc = end % size - start % size
    if (dr != 0 && dc != 0 && abs(dr) != abs(dc)) return emptyList()
    val length = maxOf(abs(dr), abs(dc))
    return (0..length).map { (start / size + it * dr.compareTo(0)) * size + start % size + it * dc.compareTo(0) }
}

object WordPuzzles {
    // Original clues; no external dictionaries, licensed puzzle feeds or network calls.
    internal val clues = listOf(
        "PLANET" to "A world that travels around a star",
        "STAR" to "A distant sun seen in the night sky",
        "MOON" to "Earth's natural satellite",
        "COMET" to "An icy space visitor with a glowing tail",
        "ORBIT" to "The path of a satellite around a planet",
        "SPACE" to "The vast region beyond Earth's atmosphere",
        "OCEAN" to "A huge body of salt water",
        "RIVER" to "Water flowing toward a lake or sea",
        "RAIN" to "Water drops falling from clouds",
        "SNOW" to "Frozen flakes falling from clouds",
        "CLOUD" to "A visible mass of tiny water drops in the sky",
        "WIND" to "Moving air",
        "TREE" to "A tall plant with a woody trunk",
        "LEAF" to "A flat green part of a plant",
        "ROOT" to "The plant part that takes up water underground",
        "SEED" to "A small beginning from which a plant can grow",
        "ROSE" to "A flower whose stems often have prickles",
        "GRASS" to "The green plants that make up a lawn",
        "APPLE" to "A crunchy fruit often baked into a pie",
        "PEAR" to "A fruit with a narrow top and rounded base",
        "BREAD" to "A baked loaf used for sandwiches",
        "PLATE" to "A flat dish for serving a meal",
        "SPOON" to "A utensil used to eat soup",
        "WATER" to "The liquid that fills lakes and quenches thirst",
        "TIGER" to "A large wild cat with stripes",
        "HORSE" to "An animal ridden with a saddle",
        "OTTER" to "A playful swimming mammal with thick fur",
        "EAGLE" to "A large bird of prey with a hooked beak",
        "ROBIN" to "A small songbird often known for its red breast",
        "SNAIL" to "A slow animal that carries a spiral shell",
        "TRAIN" to "Connected rail cars pulled by an engine",
        "BOAT" to "A small vessel that travels on water",
        "ROAD" to "A route paved for vehicles",
        "WHEEL" to "A round part that turns on an axle",
        "CLOCK" to "An instrument that tells the time",
        "BOOK" to "Bound pages you can read",
        "PAPER" to "A thin sheet used for writing",
        "PENCIL" to "A writing tool with a graphite core",
        "MUSIC" to "Sounds arranged into rhythm and melody",
        "PIANO" to "An instrument with black and white keys",
    ) + WordCatalog.moreClues

    val themes: List<String> = WordCatalog.searches.map { it.first }

    fun wordSearch(seed: Long, theme: Int = 0, difficulty: WordDifficulty = WordDifficulty.EASY): WordPuzzle {
        require(theme in themes.indices)
        val random = Random(seed)
        val size = difficulty.searchSize
        val words = WordCatalog.searches[theme].second
            .filter { it.length <= if (difficulty == WordDifficulty.EASY) 5 else size }
            .shuffled(random).take(difficulty.searchWords).sortedByDescending { it.length }
        check(words.size == difficulty.searchWords)
        repeat(32) {
            checkpoint()
            val grid = CharArray(size * size) { '#' }
            val entries = mutableListOf<WordEntry>()
            val directions = difficulty.searchDirections
            for (word in words) {
                checkpoint()
                val candidates = buildList {
                    for (start in grid.indices) for ((dr, dc) in directions) {
                        val row = start / size + dr * (word.length - 1)
                        val col = start % size + dc * (word.length - 1)
                        if (row !in 0 until size || col !in 0 until size) continue
                        val cells = line(size, start, row * size + col)
                        if (cells.indices.all { grid[cells[it]] == '#' || grid[cells[it]] == word[it] }) add(cells)
                    }
                }
                // Surface placement failures instead of silently omitting a requested word.
                if (candidates.isEmpty()) break
                val cells = candidates.random(random)
                cells.forEachIndexed { i, cell -> grid[cell] = word[i] }
                entries += WordEntry(word, word, cells)
            }
            if (entries.size == words.size) {
                for (cell in grid.indices) if (grid[cell] == '#') grid[cell] = 'A' + random.nextInt(26)
                return WordPuzzle(WordGame.WORD_SEARCH, size, themes[theme], grid.concatToString(), entries, difficulty)
            }
        }
        error("Could not place all words")
    }

    fun crossword(seed: Long, difficulty: WordDifficulty = WordDifficulty.MEDIUM): WordPuzzle {
        val random = Random(seed)
        val size = difficulty.crosswordSize
        val hand = clues.filter { it.first.length in difficulty.answerLengths }
        val handWords = hand.map { it.first }.toSet()
        val extras = OpenContent.clues.filter { it.first.length in difficulty.answerLengths && it.first !in handWords }
        var best: WordPuzzle? = null
        repeat(32) {
            checkpoint()
            val grid = CharArray(size * size) { '#' }
            val entries = mutableListOf<WordEntry>()
            // The original clues stay in every attempt. A sample of dictionary clues changes the grid.
            val vocabulary = (hand + extras.shuffled(random).take(48)).shuffled(random)
            for ((answer, clue) in vocabulary) {
                checkpoint()
                val candidates = mutableListOf<List<Int>>()
                for (down in listOf(false, true)) for (start in grid.indices) {
                    val row = start / size
                    val col = start % size
                    val dr = if (down) 1 else 0
                    val dc = if (down) 0 else 1
                    val endRow = row + dr * (answer.length - 1)
                    val endCol = col + dc * (answer.length - 1)
                    if (endRow >= size || endCol >= size) continue
                    fun occupied(r: Int, c: Int) = r in 0 until size && c in 0 until size && grid[r * size + c] != '#'
                    if (occupied(row - dr, col - dc) || occupied(endRow + dr, endCol + dc)) continue
                    val cells = line(size, start, endRow * size + endCol)
                    var crossings = 0
                    val valid = cells.indices.all { i ->
                        val cell = cells[i]
                        if (grid[cell] != '#') {
                            crossings++
                            grid[cell] == answer[i] && entries.none { e ->
                                cell in e.cells && (e.cells[1] - e.cells[0] == if (down) size else 1)
                            }
                        } else {
                            !occupied(cell / size - dc, cell % size - dr) &&
                                !occupied(cell / size + dc, cell % size + dr)
                        }
                    }
                    if (valid && (crossings > 0 || entries.isEmpty())) candidates += cells
                }
                if (candidates.isNotEmpty()) {
                    val cells = if (entries.isEmpty()) {
                        // Start near the center to leave space for crossing entries.
                        candidates.minBy { abs(it[it.size / 2] / size - size / 2) + abs(it[it.size / 2] % size - size / 2) }
                    } else candidates.random(random)
                    cells.forEachIndexed { i, cell -> grid[cell] = answer[i] }
                    entries += WordEntry(answer, clue, cells)
                }
                if (entries.size == difficulty.crosswordTarget) break
            }
            val puzzle = WordPuzzle(WordGame.CROSSWORD, size, "Crossword", grid.concatToString(), entries, difficulty)
            if (puzzle.entries.size > (best?.entries?.size ?: 0)) best = puzzle
            if (puzzle.entries.size >= difficulty.crosswordTarget) return puzzle
        }
        return checkNotNull(best).also { check(it.entries.size >= difficulty.crosswordMinimum) }
    }
}

data class WordProgress(
    val puzzle: WordPuzzle,
    val current: String = puzzle.letters.map { if (it == '#') '#' else '_' }.joinToString(""),
    val found: Set<String> = emptySet(),
    val hints: Int = 0,
    val foundPaths: Map<String, List<Int>> = emptyMap(),
) {
    init {
        require(hints >= 0 && current.length == puzzle.letters.length)
        require(current.indices.all { i ->
            if (puzzle.letters[i] == '#') current[i] == '#' else current[i] == '_' || current[i] in 'A'..'Z'
        })
        require(found.all { word -> puzzle.entries.any { it.answer == word } })
        foundPaths.forEach { (word, cells) ->
            require(word in found && cells.size == word.length)
            require(cells.all { it in puzzle.letters.indices })
            require(line(puzzle.size, cells.first(), cells.last()) == cells)
            val selected = cells.map { puzzle.letters[it] }.joinToString("")
            require(selected == word || selected.reversed() == word)
        }
    }
    val complete: Boolean get() = if (puzzle.game == WordGame.CROSSWORD) current == puzzle.letters else found.size == puzzle.entries.size

    fun answer(entry: WordEntry, text: String): WordProgress {
        require(puzzle.game == WordGame.CROSSWORD && entry in puzzle.entries)
        val letters = text.uppercase(java.util.Locale.ROOT).filter { it in 'A'..'Z' }.take(entry.cells.size)
        val updated = current.toCharArray()
        entry.cells.forEachIndexed { i, cell -> updated[cell] = letters.getOrElse(i) { '_' } }
        return copy(current = updated.concatToString())
    }

    fun find(start: Int, end: Int): WordProgress {
        require(puzzle.game == WordGame.WORD_SEARCH)
        val cells = line(puzzle.size, start, end)
        val selected = cells.map { puzzle.letters[it] }.joinToString("")
        // Accept any occurrence, including accidental filler matches and reverse selection.
        val match = puzzle.entries.firstOrNull { it.answer == selected || it.answer == selected.reversed() }
        return if (match == null || match.answer in found) this
            else copy(found = found + match.answer, foundPaths = foundPaths + (match.answer to cells))
    }
}

/** Versioned full-board saves: updating a generator does not change existing games. */
object WordSaveCodec {
    fun encode(progress: WordProgress): String = with(progress) {
        (listOf("3", puzzle.game.name, puzzle.size.toString(), puzzle.title, puzzle.letters, current,
            hints.toString(), found.sorted().joinToString(";") { word ->
                "$word:${foundPaths[word]?.joinToString(",").orEmpty()}"
            }, puzzle.difficulty?.name ?: "ORIGINAL") + puzzle.entries.map {
            "${it.answer}|${it.clue}|${it.cells.joinToString(",")}" }
        ).joinToString("\n")
    }

    fun decode(encoded: String): WordProgress? = try {
        require(encoded.length <= 20000)
        val lines = encoded.split('\n')
        require(lines.size in 9..60 && lines[0] in listOf("1", "2", "3"))
        val difficulty = if (lines[0] == "3" && lines[8] != "ORIGINAL") WordDifficulty.valueOf(lines[8]) else null
        val entries = lines.drop(if (lines[0] == "3") 9 else 8).map {
            val fields = it.split('|')
            require(fields.size == 3)
            WordEntry(fields[0], fields[1], fields[2].split(',').map(String::toInt))
        }
        val puzzle = WordPuzzle(WordGame.valueOf(lines[1]), lines[2].toInt(), lines[3], lines[4], entries, difficulty)
        require(WordVerifier.verify(puzzle))
        if (lines[0] == "1") {
            WordProgress(puzzle, lines[5], lines[7].split(',').filter(String::isNotEmpty).toSet(), lines[6].toInt())
        } else {
            val paths = lines[7].split(';').filter(String::isNotEmpty).associate {
                val fields = it.split(':')
                require(fields.size == 2)
                fields[0] to fields[1].split(',').filter(String::isNotEmpty).map(String::toInt)
            }
            WordProgress(puzzle, lines[5], paths.keys, lines[6].toInt(), paths.filterValues { it.isNotEmpty() })
        }
    } catch (_: IllegalArgumentException) { null }
}
