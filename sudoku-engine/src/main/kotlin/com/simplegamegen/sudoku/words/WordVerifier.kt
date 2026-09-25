package com.simplegamegen.sudoku.words

/** Checks board geometry independently of the placement algorithm. */
object WordVerifier {
    fun verify(p: WordPuzzle, checkDifficulty: Boolean = true): Boolean {
        if (p.game == WordGame.CROSSWORD) {
            val runs = mutableSetOf<List<Int>>()
            for (down in listOf(false, true)) for (cell in p.letters.indices) {
                if (p.letters[cell] == '#') continue
                val step = if (down) p.size else 1
                val previous = if (down) cell - step else if (cell % p.size > 0) cell - 1 else -1
                if (previous >= 0 && p.letters[previous] != '#') continue
                val run = mutableListOf<Int>()
                var next = cell
                while (next < p.letters.length && p.letters[next] != '#' && (down || next / p.size == cell / p.size)) {
                    run += next; next += step
                }
                if (run.size > 1) runs += run
            }
            if (runs != p.entries.map { it.cells }.toSet()) return false
            val reached = p.entries.first().cells.toMutableSet()
            repeat(p.entries.size) { p.entries.filter { e -> e.cells.any { it in reached } }.forEach { reached.addAll(it.cells) } }
            if (reached != p.entries.flatMap { it.cells }.toSet()) return false
        } else {
            if (p.entries.any { occurrences(p, it.answer).isEmpty() }) return false
        }
        val level = p.difficulty ?: return true
        if (!checkDifficulty) return true
        return if (p.game == WordGame.CROSSWORD) p.size == level.crosswordSize &&
            p.entries.size in level.crosswordMinimum..level.crosswordTarget && p.entries.all { it.answer.length in level.answerLengths }
        else p.size == level.searchSize && p.entries.size == level.searchWords && p.entries.all {
            val a = it.cells[0]; val b = it.cells[1]
            (b / p.size - a / p.size to b % p.size - a % p.size) in level.searchDirections &&
                (level != WordDifficulty.EASY || it.answer.length <= 5)
        }
    }

    fun occurrences(p: WordPuzzle, word: String): List<List<Int>> = buildList {
        for (start in p.letters.indices) for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val row = start / p.size + dr * (word.length - 1)
            val col = start % p.size + dc * (word.length - 1)
            if (row !in 0 until p.size || col !in 0 until p.size) continue
            val cells = line(p.size, start, row * p.size + col)
            if (cells.map { p.letters[it] }.joinToString("") == word) add(cells)
        }
    }
}
