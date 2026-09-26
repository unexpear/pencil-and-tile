package com.simplegamegen.sudoku.wordplay

import kotlin.random.Random

// ---------------- Blotwords ----------------
//
// Ink every square of a letter grid. Writing a command word (read in a straight row or column,
// forwards or backwards) inks its letters, then its effect happens straight away. Inked squares
// drop out of the way, so the letters on either side of them become neighbours. Knot squares join
// letters (a word may run through them and turn a corner on one) and wild squares stand for any letter.

const val INK = '#'
const val BLANK = '.'
/** A knot: a word may pass through any number of them, and turn a corner on one. Writing never inks it. */
const val KNOT = '+'
/** A wild square: it stands for any letter in a word. */
const val WILD = '?'
// A sealed square is its letter in lower case: inking it breaks the seal (it becomes upper case) instead.

/** What a command word lets you do once it's written. */
enum class BlotEffect(val effect: String) {
    ONE("Ink any one more square."),
    PAIR("Ink two squares that touch."),
    ALIKE("Pick a letter: every square showing it is inked."),
    WRITE("Write any letter in a blank square."),
    DIAG("Pick a square: it and every square on its rising diagonal are inked."),
    MEND("Pick an inked square: it comes back as it started. Or pick a letter: it gets a seal."),
}

/** A command word and what it does. */
data class BlotWord(val effect: BlotEffect, val text: String) {
    companion object {
        /** Letters worth writing with a WRITE word: only command words are ever read. */
        fun lettersOf(words: List<BlotWord>): List<Char> = words.flatMap { it.text.toList() }.distinct().sorted()
    }
}

/** The command words, one per effect. */
data class BlotLexicon(val one: String, val pair: String, val alike: String, val write: String, val diag: String = "GOBA", val mend: String = "MIPA") {
    fun text(e: BlotEffect): String = when (e) {
        BlotEffect.ONE -> one; BlotEffect.PAIR -> pair; BlotEffect.ALIKE -> alike; BlotEffect.WRITE -> write; BlotEffect.DIAG -> diag
        BlotEffect.MEND -> mend
    }

    fun word(e: BlotEffect) = BlotWord(e, text(e))
    fun words(effects: List<BlotEffect> = BlotEffect.entries): List<BlotWord> = effects.map(::word)

    companion object {
        val INK = BlotLexicon("VUM", "DRIF", "ZUV", "KEL")
    }
}

/**
 * The ways to play: a trail of small puzzles that introduce the words one by one, then four levels. [knots] and
 * [wilds] are how often filler squares are knots and word letters are wild.
 */
enum class BlotTier(val label: String, val width: Int, val height: Int, val effects: List<BlotEffect>, val blankChance: Double,
    val maxPreInked: Int, val decoy: Double, val knots: Double = 0.0, val wilds: Double = 0.0, val seals: Double = 0.0,
    /** Boards with an uneven outline and holes, rather than full rectangles. */
    val shaped: Boolean = false) {
    DISCOVER("Discover", 4, 4, BlotEffect.entries, 0.0, 0, 0.0),
    EASY("Easy", 5, 5, listOf(BlotEffect.ONE), 0.0, 5, 0.35),
    MEDIUM("Medium", 5, 5, listOf(BlotEffect.ONE, BlotEffect.PAIR), 0.06, 5, 0.5, shaped = true),
    HARD("Hard", 6, 6, listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.ALIKE, BlotEffect.DIAG), 0.08, 6, 0.6, knots = 0.14, seals = 0.3, shaped = true),
    EXPERT("Expert", 6, 6, BlotEffect.entries - BlotEffect.MEND, 0.1, 6, 0.7, knots = 0.16, wilds = 0.06, seals = 0.35, shaped = true),
}

/** One whole turn: write [word] along [path] (in reading order), then use its effect. */
data class BlotMove(val word: BlotWord, val path: List<Int>, val targets: List<Int> = emptyList(), val letter: Char? = null)

/**
 * A Blotwords game. [cells] holds letters, [BLANK] or [INK]; [pending] is the word just written
 * whose effect is still to be used; [step] is the Discover trail step, or -1; [theme] names the look it's drawn in.
 */
data class Blotwords(
    val tier: BlotTier,
    val seed: Long,
    val width: Int,
    val words: List<BlotWord>,
    val start: String,
    val cells: String = start,
    val pending: BlotWord? = null,
    val written: List<BlotWord> = emptyList(),
    val hints: Int = 0,
    val step: Int = -1,
    /** A known way to finish from [start], kept so hints are instant while the player follows it. */
    val plan: List<BlotMove> = emptyList(),
    val theme: String = "ink",
    /** The squares of each word written so far, in reading order, so the board can show them joined up. */
    val strokes: List<List<Int>> = emptyList(),
    /** Squares that aren't part of the board at all. They count as inked, so words read straight across them. */
    val holes: Set<Int> = emptySet(),
) {
    init {
        require(width > 0 && start.length % width == 0 && cells.length == start.length)
        require(cells.all { it == INK || it == BLANK || it == KNOT || it == WILD || it in 'A'..'Z' || it in 'a'..'z' } && words.isNotEmpty() && hints >= 0)
        require(holes.all { start[it] == INK })
        require(theme.none { it == '\n' })
    }

    val height: Int get() = cells.length / width
    val solved: Boolean get() = pending == null && cells.all { it == INK }
    /** No effect waiting and no word to write, yet squares remain. */
    val stuck: Boolean get() = !solved && pending == null && Blots.placements(cells, width, words).isEmpty()
    val over: Boolean get() = solved
    val left: Int get() = cells.count { it != INK }

    /** Where a word can be written along [path] (its letters in reading order) right now, with the knots it runs through. */
    fun placementAt(path: List<Int>): Blots.Placement? {
        if (pending != null || path.size < 2) return null
        val all = Blots.routes(cells, width, words)
        all.firstOrNull { it.path == path }?.let { return it }
        // A word that reads the same both ways is kept once; written from the other end it's the same writing.
        return all.firstOrNull { it.word.text == it.word.text.reversed() && it.path == path.reversed() }
            ?.let { it.copy(path = path, route = it.route.reversed()) }
    }

    /** The word [path] spells, if it is a legal place to write one right now. */
    fun wordAt(path: List<Int>): BlotWord? = placementAt(path)?.word

    /** Writes the word along [path]; its effect then waits unless the grid is already full. */
    fun write(path: List<Int>): Blotwords? {
        val placed = placementAt(path) ?: return null
        val next = Blots.inked(cells, path)
        return copy(cells = next, pending = if (next.all { it == INK }) null else placed.word, written = written + placed.word,
            strokes = strokes + listOf(placed.route))
    }

    /** ONE: ink one square. PAIR: ink two touching squares. ALIKE: ink every square with the letter at [a]. */
    fun use(a: Int, b: Int? = null): Blotwords? {
        val next = when (pending?.effect) {
            BlotEffect.ONE -> if (b == null && cells[a] != INK) Blots.inked(cells, listOf(a)) else null
            BlotEffect.PAIR -> if (b != null && Blots.touching(cells, width, a, b)) Blots.inked(cells, listOf(a, b)) else null
            BlotEffect.ALIKE -> if (b == null && Blots.alikeable(cells[a])) Blots.alike(cells, cells[a].uppercaseChar()) else null
            BlotEffect.DIAG -> if (b == null && cells[a] != INK) Blots.diagonal(cells, width, a) else null
            BlotEffect.MEND -> if (b == null) Blots.mend(cells, start, a) else null
            else -> null
        } ?: return null
        return copy(cells = next, pending = null)
    }

    /** WRITE: put [letter] in the blank square [at]. */
    fun fill(at: Int, letter: Char): Blotwords? {
        if (pending?.effect != BlotEffect.WRITE || cells[at] != BLANK || letter !in 'A'..'Z') return null
        return copy(cells = cells.substring(0, at) + letter + cells.substring(at + 1), pending = null)
    }

    fun restart(): Blotwords = copy(cells = start, pending = null, strokes = emptyList())

    /**
     * The next move toward a finish from here; null when the position can't be finished (or the search ran
     * out of time). With an effect waiting, only that effect's choices are tried, and the path is empty.
     */
    fun nextMove(budget: Int = 200_000): BlotMove? {
        if (solved) return null
        planned()?.let { return it }
        return when (val r = BlotSolver.solve(cells, width, words, budget, pending, start)) {
            is BlotSolver.Result.Solved -> r.moves.firstOrNull()
            else -> null
        }
    }

    /** The plan's next turn when the game still matches the plan. */
    private fun planned(): BlotMove? {
        var c = start
        for (m in plan) {
            val written = Blots.inked(c, m.path)
            if (pending == null && cells == c) return m
            if (pending == m.word && cells == written) return m.copy(path = emptyList())
            c = Blots.applyEffect(written, width, m.word, m.targets, m.letter, start)
        }
        return null
    }

    /** True when the solver proves this position can't be finished. */
    fun deadEnd(budget: Int = 200_000): Boolean = !solved && BlotSolver.solve(cells, width, words, budget, pending, start) is BlotSolver.Result.Dead

    fun hinted(): Blotwords = copy(hints = hints + 1)
}

/** Grid helpers shared by the game, solver and generator. */
object Blots {
    /** Inks [path]: sealed squares lose their seal instead. */
    fun inked(cells: String, path: List<Int>): String {
        val a = cells.toCharArray(); path.forEach { a[it] = if (a[it] in 'a'..'z') a[it].uppercaseChar() else INK }; return String(a)
    }

    /** Inks every square showing [letter]; sealed ones lose their seal instead. */
    fun alike(cells: String, letter: Char): String = cells.map { if (it == letter) INK else if (it.isLowerCase() && it.uppercaseChar() == letter) letter else it }.joinToString("")

    /** Squares ALIKE can pick: letters (sealed or not), and knots or wilds (which all go together). */
    fun alikeable(ch: Char): Boolean = ch in 'A'..'Z' || ch in 'a'..'z' || ch == KNOT || ch == WILD

    /** True for a square still under its seal. */
    fun sealed(ch: Char): Boolean = ch in 'a'..'z'

    /** MEND at [at]: an inked square comes back as it was in [start]; a plain letter gets a seal. Null when neither applies. */
    fun mend(cells: String, start: String?, at: Int): String? {
        val now = cells[at]
        val back = when {
            now == INK && start != null && start[at] != INK -> start[at]
            now in 'A'..'Z' -> now.lowercaseChar()
            else -> return null
        }
        return cells.substring(0, at) + back + cells.substring(at + 1)
    }

    /**
     * An uneven outline for a [width] × [height] board: bites out of some corners and now and then a hole inside,
     * keeping the rest in one piece. Returns the squares that aren't part of it.
     */
    fun shape(width: Int, height: Int, random: Random): Set<Int> {
        if (width < 4 || height < 3) return emptySet()
        val out = HashSet<Int>()
        for ((cr, cc) in listOf(0 to 0, 0 to width - 1, height - 1 to 0, height - 1 to width - 1)) {
            if (random.nextDouble() < 0.4) continue
            val bh = 1 + random.nextInt(if (height >= 5) 2 else 1); val bw = 1 + random.nextInt(if (width >= 5) 2 else 1)
            for (r in 0 until bh) for (c in 0 until bw) {
                if (r == bh - 1 && c == bw - 1 && bh + bw > 2 && random.nextBoolean()) continue
                out += (if (cr == 0) r else height - 1 - r) * width + (if (cc == 0) c else width - 1 - c)
            }
        }
        if (width >= 5 && height >= 5 && random.nextDouble() < 0.5) out += (1 + random.nextInt(height - 2)) * width + 1 + random.nextInt(width - 2)
        // Keep at least three quarters of the board, all in one piece.
        if (out.size > width * height / 4) return emptySet()
        val keep = (0 until width * height).filter { it !in out }
        val seen = HashSet<Int>(); val todo = ArrayDeque(listOf(keep.first()))
        while (todo.isNotEmpty()) {
            val i = todo.removeFirst(); if (!seen.add(i)) continue
            val r = i / width; val c = i % width
            for ((dr, dc) in listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)) {
                val nr = r + dr; val nc = c + dc
                if (nr in 0 until height && nc in 0 until width && nr * width + nc !in out) todo += nr * width + nc
            }
        }
        return if (seen.size == keep.size) out else emptySet()
    }

    /** The squares on [at]'s rising diagonal (running from bottom left to top right). */
    fun diagonalOf(width: Int, height: Int, at: Int): List<Int> {
        val sum = at / width + at % width
        return (0 until height).mapNotNull { r -> (sum - r).takeIf { it in 0 until width }?.let { r * width + it } }
    }

    fun diagonal(cells: String, width: Int, at: Int): String = inked(cells, diagonalOf(width, cells.length / width, at))

    /** A command word written along [path] (its letters, in reading order), running through the squares of [route]. */
    data class Placement(val word: BlotWord, val path: List<Int>, val route: List<Int>)

    private fun spells(ch: Char, want: Char) = ch.uppercaseChar() == want && ch != BLANK || (ch == WILD && want in 'A'..'Z')

    /**
     * Every place a command word can be written now. A word runs straight across inked squares; knots join its
     * letters too, and on a knot it may turn a corner. It can't turn on a letter or run through one it doesn't use.
     */
    fun routes(cells: String, width: Int, words: List<BlotWord>): List<Placement> {
        val h = cells.length / width
        val out = ArrayList<Placement>()
        val left = cells.count { it != INK }
        val hasBlank = cells.indexOf(BLANK) >= 0
        val dirs = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
        for (w in words) {
            val t = w.text
            val seen = HashSet<List<Int>>()
            fun walk(at: Int, d: Int, idx: Int, path: List<Int>, route: List<Int>) {
                var r = at / width; var c = at % width
                while (true) {
                    r += dirs[d].first; c += dirs[d].second
                    if (r !in 0 until h || c !in 0 until width) return
                    val i = r * width + c
                    val ch = cells[i]
                    when {
                        ch == INK -> continue
                        ch == KNOT -> {
                            if (i in route) return
                            val via = route + i
                            // Carry straight on, or turn a corner here.
                            walk(i, d, idx, path, via)
                            walk(i, (d + 1) % 4, idx, path, via); walk(i, (d + 3) % 4, idx, path, via)
                            return
                        }
                        spells(ch, t[idx]) -> {
                            val p = path + i; val rt = route + i
                            if (idx + 1 == t.length) {
                                // A palindrome is the same writing read either way: keep it once.
                                if (seen.add(p) && seen.add(p.reversed()) && usable(cells, width, w, p, left, hasBlank)) out += Placement(w, p, rt)
                            } else walk(i, d, idx + 1, p, rt)
                            return
                        }
                        else -> return
                    }
                }
            }
            for (s in cells.indices) if (spells(cells[s], t[0])) for (d in 0..3) walk(s, d, 1, listOf(s), listOf(s))
        }
        return out
    }

    /** Squares still showing, row by row and column by column, so inked squares drop out of the way. */
    fun lines(cells: String, width: Int): List<IntArray> {
        val h = cells.length / width
        val out = ArrayList<IntArray>(width + h)
        for (r in 0 until h) out += (0 until width).map { r * width + it }.filter { cells[it] != INK }.toIntArray()
        for (c in 0 until width) out += (0 until h).map { it * width + c }.filter { cells[it] != INK }.toIntArray()
        return out
    }

    /** [path] runs straight along one row or column, one way, with only inked squares between its letters. */
    fun inLine(cells: String, width: Int, path: List<Int>): Boolean {
        if (path.any { cells[it] == INK } || path.toSet().size != path.size) return false
        val rows = path.map { it / width }; val cols = path.map { it % width }
        val step = when {
            rows.toSet().size == 1 -> 1
            cols.toSet().size == 1 -> width
            else -> return false
        }
        val forward = path[1] > path[0]
        for (i in 1 until path.size) {
            val (lo, hi) = if (forward) path[i - 1] to path[i] else path[i] to path[i - 1]
            if (hi <= lo) return false
            var k = lo + step
            while (k < hi) { if (cells[k] != INK) return false; k += step }
        }
        return true
    }

    /** Two showing squares in one row or column with only inked squares between them. */
    fun touching(cells: String, width: Int, a: Int, b: Int): Boolean =
        a != b && cells[a] != INK && cells[b] != INK && (a / width == b / width || a % width == b % width) && inLine(cells, width, listOf(minOf(a, b), maxOf(a, b)))

    /** A word may only be written when its effect can then be used (or nothing is left to ink). */
    fun effectPossible(after: String, width: Int, word: BlotWord): Boolean {
        if (after.all { it == INK }) return true
        return when (word.effect) {
            BlotEffect.ONE -> true
            BlotEffect.PAIR -> lines(after, width).any { it.size >= 2 }
            BlotEffect.ALIKE -> after.any { alikeable(it) }
            BlotEffect.WRITE -> after.any { it == BLANK }
            BlotEffect.DIAG -> true
            // The word's own letters were just inked, so there's always one to bring back.
            BlotEffect.MEND -> true
        }
    }

    /** Every place a command word can be written now, as paths in reading order. */
    fun placements(cells: String, width: Int, words: List<BlotWord>): List<Pair<BlotWord, List<Int>>> =
        routes(cells, width, words).map { it.word to it.path }

    /** [effectPossible] without rebuilding the grid in the common cases. */
    private fun usable(cells: String, width: Int, w: BlotWord, path: List<Int>, left: Int, hasBlank: Boolean): Boolean = when {
        left == path.size && path.none { sealed(cells[it]) } -> true
        w.effect == BlotEffect.ONE || w.effect == BlotEffect.DIAG || w.effect == BlotEffect.MEND -> true
        w.effect == BlotEffect.WRITE -> hasBlank
        w.effect == BlotEffect.PAIR && left - path.size > 4 -> true
        else -> effectPossible(inked(cells, path), width, w)
    }

    /** The effect choices after [word] was written, each as (targets, letter). */
    fun effects(cells: String, width: Int, word: BlotWord, words: List<BlotWord>, start: String? = null): List<Pair<List<Int>, Char?>> {
        if (cells.all { it == INK }) return listOf(emptyList<Int>() to null)
        return when (word.effect) {
            BlotEffect.ONE -> cells.indices.filter { cells[it] != INK }.map { listOf(it) to null }
            BlotEffect.PAIR -> lines(cells, width).flatMap { l -> (1 until l.size).map { listOf(l[it - 1], l[it]) to null } }
            BlotEffect.ALIKE -> cells.filter { alikeable(it) }.map { it.uppercaseChar() }.toSet().sorted()
                .map { ch -> listOf(cells.indexOfFirst { it.uppercaseChar() == ch }) to null }
            BlotEffect.WRITE -> cells.indices.filter { cells[it] == BLANK }.flatMap { i -> BlotWord.lettersOf(words).map { listOf(i) to it } }
            // One choice per diagonal that still has something showing.
            BlotEffect.DIAG -> cells.indices.filter { cells[it] != INK }.distinctBy { it / width + it % width }.map { listOf(it) to null }
            BlotEffect.MEND -> cells.indices.filter { mend(cells, start, it) != null }.map { listOf(it) to null }
        }
    }

    fun applyEffect(cells: String, width: Int, word: BlotWord, targets: List<Int>, letter: Char?, start: String? = null): String = when {
        targets.isEmpty() -> cells
        word.effect == BlotEffect.MEND -> mend(cells, start, targets[0]) ?: cells
        word.effect == BlotEffect.ALIKE -> alike(cells, cells[targets[0]].uppercaseChar())
        word.effect == BlotEffect.DIAG -> diagonal(cells, width, targets[0])
        word.effect == BlotEffect.WRITE -> cells.substring(0, targets[0]) + letter!! + cells.substring(targets[0] + 1)
        else -> inked(cells, targets)
    }

    fun play(cells: String, width: Int, move: BlotMove): String = applyEffect(inked(cells, move.path), width, move.word, move.targets, move.letter)
}

/** Depth-first search over whole turns, remembering positions already shown to be dead ends. */
object BlotSolver {
    sealed interface Result {
        data class Solved(val moves: List<BlotMove>) : Result
        data object Dead : Result
        data object Unknown : Result
    }

    private class OutOfBudget : RuntimeException(null, null, false, false)

    fun solve(cells: String, width: Int, words: List<BlotWord>, budget: Int = 200_000, pending: BlotWord? = null, start: String? = null): Result {
        val dead = HashSet<String>()
        // MEND can bring squares back, so a position can come round again: never revisit one on the way.
        val onPath = HashSet<String>()
        var nodes = 0
        val origin = start ?: cells
        fun dfs(c: String): List<BlotMove>? {
            if (c.all { it == INK }) return emptyList()
            if (c in dead || c in onPath) return null
            if (++nodes > budget) throw OutOfBudget()
            onPath += c
            try {
                for ((w, path) in Blots.placements(c, width, words)) {
                    val after = Blots.inked(c, path)
                    for ((targets, letter) in ordered(after, width, w, words, origin)) {
                        val rest = dfs(Blots.applyEffect(after, width, w, targets, letter, origin))
                        if (rest != null) return listOf(BlotMove(w, path, targets, letter)) + rest
                    }
                }
            } finally { onPath -= c }
            dead += c
            return null
        }
        return try {
            if (pending != null) {
                // The word is already written: only its effect's choices are open.
                for ((targets, letter) in ordered(cells, width, pending, words, origin)) {
                    val rest = dfs(Blots.applyEffect(cells, width, pending, targets, letter, origin))
                    if (rest != null) return Result.Solved(listOf(BlotMove(pending, emptyList(), targets, letter)) + rest)
                }
                Result.Dead
            } else dfs(cells)?.let { Result.Solved(it) } ?: Result.Dead
        } catch (_: OutOfBudget) { Result.Unknown }
    }

    /**
     * Effect choices, most promising first: squares no visible word is using and letters that aren't in any
     * command word, and for WRITE, letters that complete a word.
     */
    fun ordered(after: String, width: Int, w: BlotWord, words: List<BlotWord>, start: String? = null): List<Pair<List<Int>, Char?>> =
        rank(after, width, w, words, start).flatMap { it.value }

    private fun rank(after: String, width: Int, w: BlotWord, words: List<BlotWord>, start: String? = null): Map<Int, List<Pair<List<Int>, Char?>>> {
        val commandLetters = BlotWord.lettersOf(words).toSet()
        val now = Blots.placements(after, width, words)
        val inWords = now.flatMap { it.second }.toSet()
        fun appeal(i: Int) = (if (i in inWords) 2 else 0) + (if (after[i] in commandLetters) 1 else 0)
        return Blots.effects(after, width, w, words, start).groupBy { (t, letter) ->
            when (w.effect) {
                // Bringing back a square that lets a new word be written comes first; sealing a letter comes last.
                BlotEffect.MEND -> when {
                    after[t[0]] != INK -> 2
                    Blots.placements(Blots.applyEffect(after, width, w, t, letter, start), width, words).size > now.size -> 0
                    else -> 1
                }
                BlotEffect.WRITE -> if (Blots.placements(Blots.applyEffect(after, width, w, t, letter), width, words).size > now.size) 0 else 1
                BlotEffect.ALIKE -> if (t.isNotEmpty() && after[t[0]] in commandLetters) 1 else 0
                else -> t.sumOf { appeal(it) }
            }
        }.toSortedMap()
    }

    /**
     * Share of playthroughs by a sensible but unplanned player that ink the whole grid: any word they can
     * see, then effects spent on squares no word is using. Low means many tempting dead ends.
     */
    fun luck(cells: String, width: Int, words: List<BlotWord>, tries: Int, random: Random): Double {
        var wins = 0
        repeat(tries) {
            var c = cells
            while (true) {
                if (c.all { it == INK }) { wins++; break }
                val options = Blots.placements(c, width, words)
                if (options.isEmpty()) break
                val (w, path) = options.random(random)
                val after = Blots.inked(c, path)
                // The most appealing kind of effect, picked at random within that kind.
                val (targets, letter) = rank(after, width, w, words, cells).values.first().random(random)
                c = Blots.applyEffect(after, width, w, targets, letter, cells)
            }
        }
        return wins.toDouble() / tries
    }
}

/**
 * Builds puzzles backwards from a fully inked grid: each step un-inks an effect's squares and then a
 * command word's letters, so the steps read in reverse are a guaranteed way to finish. Squares never
 * reached stay inked from the start.
 */
object BlotGenerator {
    /** Common letters used for filler, minus any that appear in the command words. */
    private const val FILLER = "AOSTNHPBCGYWR"

    fun plainFiller(words: List<BlotWord>): List<Char> {
        val used = BlotWord.lettersOf(words).toSet()
        return FILLER.filter { it !in used }.toList().ifEmpty { ('A'..'Z').filter { it !in used } }
    }

    fun generate(tier: BlotTier, seed: Long, theme: String = "ink"): Blotwords {
        val random = Random(seed)
        val words = BlotLexicon.INK.words(tier.effects)
        var best: Pair<Blotwords, Double>? = null
        repeat(40) {
            val holes = if (tier.shaped) Blots.shape(tier.width, tier.height, random) else emptySet()
            val built = build(tier.width, tier.height, words, tier, random, holes = holes) ?: return@repeat
            val luck = BlotSolver.luck(built.first, tier.width, words, 120, random)
            val (lo, hi) = band(tier)
            val game = Blotwords(tier, seed, tier.width, words, built.first, plan = built.second, theme = theme, holes = holes)
            if (luck in lo..hi) return game
            val miss = if (luck < lo) lo - luck else luck - hi
            if (best == null || miss < best!!.second) best = game to miss
        }
        return best?.first ?: error("No Blotwords grid for $tier")
    }

    /** Wanted share of sensible playthroughs that succeed. */
    fun band(tier: BlotTier): Pair<Double, Double> = when (tier) {
        BlotTier.DISCOVER -> 0.0 to 1.0
        BlotTier.EASY -> 0.3 to 0.9
        BlotTier.MEDIUM -> 0.08 to 0.45
        BlotTier.HARD -> 0.01 to 0.2
        BlotTier.EXPERT -> 0.0 to 0.08
    }

    /** One backwards build: the start grid and the moves that finish it. */
    fun build(width: Int, height: Int, words: List<BlotWord>, tier: BlotTier, random: Random,
        mustUse: Collection<BlotWord> = words, maxPreInked: Int = tier.maxPreInked, knots: Double = tier.knots, wilds: Double = tier.wilds,
        seals: Double = tier.seals, holes: Set<Int> = emptySet()): Pair<String, List<BlotMove>>? {
        repeat(30) {
            val d = Draft(width, height, plainFiller(words), knots, wilds, seals, holes)
            val moves = ArrayList<BlotMove>()
            var guard = 0
            while (guard++ < 60) {
                val order = words.shuffled(random).sortedByDescending { w -> if (w in mustUse && moves.none { it.word == w }) 1 else 0 }
                val move = order.firstNotNullOfOrNull { w -> d.unwind(w, tier, words, random) } ?: break
                moves += move
                if (d.inkedCount() <= maxPreInked && mustUse.all { w -> moves.any { it.word == w } } && (d.inkedCount() == 0 || random.nextDouble() < 0.35)) break
            }
            if (d.inkedCount() > maxPreInked || !mustUse.all { w -> moves.any { it.word == w } } || d.promisesLeft()) return@repeat
            val start = d.startGrid()
            val forward = moves.reversed()
            if (replays(start, width, words, forward)) return start to forward
        }
        return null
    }

    fun replays(start: String, width: Int, words: List<BlotWord>, moves: List<BlotMove>): Boolean {
        var g = Blotwords(BlotTier.DISCOVER, 0, width, words, start)
        for (m in moves) {
            g = g.write(m.path) ?: return false
            if (g.pending != null) g = when (m.word.effect) {
                BlotEffect.WRITE -> g.fill(m.targets[0], m.letter!!)
                BlotEffect.PAIR -> g.use(m.targets[0], m.targets[1])
                else -> g.use(m.targets[0])
            } ?: return false
        }
        return g.solved
    }

    private class Draft(val width: Int, val height: Int, val plain: List<Char>, val knots: Double = 0.0, val wilds: Double = 0.0,
        val seals: Double = 0.0, holes: Set<Int> = emptySet()) {
        val size = width * height
        /** Squares that aren't on the board: always inked, never used. */
        val hole = BooleanArray(size) { it in holes }
        val content = CharArray(size) { '\u0000' }
        val inked = BooleanArray(size) { true }
        val fromWord = BooleanArray(size)
        /** Letters a square must show when it's next un-inked, because MEND going forward brings it back as it started. */
        val promised = CharArray(size)
        /** Squares that kept a promise: they must show that letter right back to the start, so nothing may change it. */
        val locked = BooleanArray(size)

        fun promisesLeft() = (0 until size).any { promised[it] != '\u0000' }

        /** What an un-inked square shows: its promised letter, or filler. */
        private fun showFor(cell: Int, tier: BlotTier, words: List<BlotWord>, random: Random): Char {
            val p = promised[cell]
            if (p != '\u0000') { promised[cell] = '\u0000'; locked[cell] = true; return p }
            return filler(tier, words, random)
        }

        fun inkedCount() = (0 until size).count { inked[it] && !hole[it] }

        fun startGrid(): String = String(CharArray(size) { if (inked[it]) INK else content[it] })

        private fun filler(tier: BlotTier, words: List<BlotWord>, random: Random): Char {
            if (random.nextDouble() < knots) return KNOT
            if (random.nextDouble() < tier.blankChance) return BLANK
            return if (random.nextDouble() < tier.decoy) BlotWord.lettersOf(words).random(random) else plain.random(random)
        }

        private fun lineCells(): List<List<Int>> =
            (0 until height).map { r -> (0 until width).map { r * width + it } } + (0 until width).map { c -> (0 until height).map { it * width + c } }

        /** Squares next to each other once the inked squares between them drop out. */
        private fun touchingPairs(): List<Pair<Int, Int>> = lineCells().flatMap { line ->
            val out = ArrayList<Pair<Int, Int>>()
            for (i in line.indices) if (inked[line[i]] && !hole[line[i]]) {
                for (j in i + 1 until line.size) { if (inked[line[j]] && !hole[line[j]]) { out += line[i] to line[j] }; if (!inked[line[j]]) break }
            }
            out
        }

        /** Undoes one turn that used [word]; returns that turn, or null (leaving the draft as it was) when it can't. */
        fun unwind(word: BlotWord, tier: BlotTier, words: List<BlotWord>, random: Random): BlotMove? {
            val saved = Triple(content.copyOf(), inked.copyOf(), fromWord.copyOf())
            val savedPromises = promised.copyOf(); val savedLocks = locked.copyOf()
            fun restore(): BlotMove? {
                saved.first.copyInto(content); saved.second.copyInto(inked); saved.third.copyInto(fromWord); savedPromises.copyInto(promised)
                savedLocks.copyInto(locked); return null
            }
            val full = (0 until size).all { inked[it] }
            val inkedCells = (0 until size).filter { inked[it] && !hole[it] }
            // Now and then a turn's square was sealed: going forward the seal broke and the letter stayed.
            val showingLetters = (0 until size).filter { !inked[it] && content[it] in 'A'..'Z' && !locked[it] }
            val k = word.text.length
            var letter: Char? = null
            val targets: List<Int> = when (word.effect) {
                BlotEffect.ONE -> {
                    if (showingLetters.isNotEmpty() && random.nextDouble() < seals && inkedCells.size >= k) {
                        val t = showingLetters.random(random); content[t] = content[t].lowercaseChar(); listOf(t)
                    } else {
                        if (inkedCells.size < k + 1) return null
                        val t = inkedCells.random(random); inked[t] = false; content[t] = showFor(t, tier, words, random); listOf(t)
                    }
                }
                BlotEffect.PAIR -> {
                    val pairs = touchingPairs().shuffled(random).firstOrNull() ?: return null
                    listOf(pairs.first, pairs.second).onEach { inked[it] = false; content[it] = showFor(it, tier, words, random) }
                }
                BlotEffect.ALIKE -> {
                    val showing = (0 until size).filter { !inked[it] }.map { content[it] }.toSet()
                    val free = (BlotWord.lettersOf(words) + plain).filter { it !in showing }
                    if (free.isEmpty() || inkedCells.size < k + 2) return null
                    val ch = free.random(random)
                    val picks = inkedCells.filter { promised[it] == '\u0000' || promised[it] == ch }.shuffled(random)
                        .take(1 + random.nextInt(minOf(3, inkedCells.size - k))).ifEmpty { return null }
                    picks.forEach { if (promised[it] != '\u0000') locked[it] = true; inked[it] = false; content[it] = ch; promised[it] = '\u0000' }
                    listOf(picks.first())
                }
                BlotEffect.WRITE -> {
                    if (full) return null
                    val t = (0 until size).filter { !inked[it] && fromWord[it] && content[it] in 'A'..'Z' && !locked[it] }.randomOrNull(random) ?: return null
                    letter = content[t]; content[t] = BLANK; listOf(t)
                }
                BlotEffect.DIAG -> {
                    // Going forward the whole diagonal is inked, so every square on it must be inked now.
                    val diagonals = (0 until width + height - 1).map { sum -> (0 until size).filter { it / width + it % width == sum } }
                        .filter { d -> d.isNotEmpty() && d.all { inked[it] } && inkedCells.size - d.size >= k }
                    val line = diagonals.randomOrNull(random)?.filter { !hole[it] }?.takeIf { it.isNotEmpty() } ?: return null
                    val picks = line.shuffled(random).take(1 + random.nextInt(minOf(3, line.size)))
                    picks.forEach { inked[it] = false; content[it] = showFor(it, tier, words, random) }
                    listOf(picks.first())
                }
                BlotEffect.MEND -> {
                    // Mostly a word's letter that comes back to be used again; now and then a letter that got a seal.
                    val sealedNow = (0 until size).filter { !inked[it] && content[it] in 'a'..'z' }
                    if (sealedNow.isNotEmpty() && random.nextDouble() < 0.15) {
                        val t = sealedNow.random(random); content[t] = content[t].uppercaseChar(); listOf(t)
                    } else {
                        val back = (0 until size).filter { !inked[it] && !hole[it] && content[it] in 'A'..'Z' }
                        val t = (back.filter { fromWord[it] }.ifEmpty { back }).randomOrNull(random) ?: return null
                        promised[t] = content[t]; inked[t] = true; fromWord[t] = false; listOf(t)
                    }
                }
            }
            // The word's letters: inked squares along one row or column with nothing showing between them but knots.
            val spans = lineCells().flatMap { line ->
                val runs = ArrayList<List<Int>>(); var cur = ArrayList<Int>()
                for (i in line) if (inked[i] || content[i] == KNOT) cur.add(i) else { if (cur.count { inked[it] } >= k) runs += cur; cur = ArrayList() }
                if (cur.count { inked[it] } >= k) runs += cur
                runs
            }
            if (spans.isEmpty()) return restore()
            fun fits(p: List<Int>) = p.indices.all { promised[p[it]] == '\u0000' || promised[p[it]] == word.text[it] }
            // A square promised a letter is best used by a word that needs that letter there: look for one first.
            val promisedHere = spans.flatten().filter { promised[it] != '\u0000' && promised[it] in word.text }
            val keep = if (promisedHere.isNotEmpty() && random.nextDouble() < 0.8) {
                spans.flatMap { sp ->
                    val l = sp.filter { inked[it] && !hole[it] }
                    (0..l.size - k).flatMap { a -> listOf(l.subList(a, a + k), l.subList(a, a + k).reversed()) }
                }.filter { p -> fits(p) && p.any { promised[it] != '\u0000' } }.randomOrNull(random)
            } else null
            val path = keep ?: run {
                val span = spans.random(random).filter { inked[it] && !hole[it] }
                if (span.size < k) return restore()
                // Mostly close together, sometimes with gaps the player must ink first.
                val startAt = random.nextInt(span.size - k + 1)
                val chosen = if (random.nextDouble() < 0.6 || span.size == k) span.subList(startAt, startAt + k)
                    else span.shuffled(random).take(k).sortedBy { span.indexOf(it) }
                if (random.nextBoolean()) chosen else chosen.reversed()
            }
            if (!fits(path)) return restore()
            path.forEachIndexed { i, cell ->
                val kept = promised[cell] != '\u0000'
                if (kept) locked[cell] = true
                inked[cell] = false; fromWord[cell] = true; promised[cell] = '\u0000'
                content[cell] = if (!kept && random.nextDouble() < wilds) WILD else word.text[i]
            }
            return BlotMove(word, path, targets, letter)
        }
    }
}

/** The Discover trail: small puzzles that each need the newest word, so solving one shows what it does. */
object BlotTrail {
    /** A trail step: the words in play and the one it teaches, or a square it teaches (knots or wilds). */
    data class Step(val effects: List<BlotEffect>, val newEffect: BlotEffect?, val width: Int, val height: Int,
        val knots: Double = 0.0, val wilds: Double = 0.0, val seals: Double = 0.0, val shaped: Boolean = false) {
        /** What the step is about: an effect's name, or KNOT / WILD / SEAL. */
        val teaches: String get() = newEffect?.name ?: when { seals > 0 -> "SEAL"; knots > 0 -> "KNOT"; else -> "WILD" }
    }

    val steps: List<Step> = listOf(
        Step(listOf(BlotEffect.ONE), BlotEffect.ONE, 4, 1),
        Step(listOf(BlotEffect.ONE), BlotEffect.ONE, 3, 3),
        Step(listOf(BlotEffect.ONE), BlotEffect.ONE, 4, 4),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR), BlotEffect.PAIR, 6, 1),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR), BlotEffect.PAIR, 4, 3),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR), BlotEffect.PAIR, 5, 4),
        Step(listOf(BlotEffect.ONE, BlotEffect.ALIKE), BlotEffect.ALIKE, 3, 3),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.ALIKE), BlotEffect.ALIKE, 4, 4),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.ALIKE), BlotEffect.ALIKE, 5, 4),
        Step(listOf(BlotEffect.ONE, BlotEffect.WRITE), BlotEffect.WRITE, 4, 3),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.WRITE), BlotEffect.WRITE, 4, 4),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.ALIKE, BlotEffect.WRITE), BlotEffect.WRITE, 5, 5),
        Step(listOf(BlotEffect.ONE, BlotEffect.DIAG), BlotEffect.DIAG, 4, 4),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.DIAG), BlotEffect.DIAG, 5, 4, shaped = true),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR), null, 5, 4, knots = 0.35),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.ALIKE), null, 5, 5, knots = 0.3, shaped = true),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR), null, 5, 4, wilds = 0.35),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR), null, 4, 4, seals = 0.5),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.ALIKE), null, 5, 5, seals = 0.35, shaped = true),
        Step(BlotEffect.entries - BlotEffect.MEND, null, 6, 5, knots = 0.15, wilds = 0.12, seals = 0.15, shaped = true),
        Step(listOf(BlotEffect.ONE, BlotEffect.MEND), BlotEffect.MEND, 4, 4),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.MEND), BlotEffect.MEND, 5, 4, shaped = true),
    )

    /** The same puzzle every time for a step: the first build that can't be finished without the new word. */
    fun puzzle(index: Int, theme: String = "ink", lexicon: BlotLexicon = BlotLexicon.INK): Blotwords {
        val i = index.coerceIn(steps.indices)
        val s = steps[i]
        val words = lexicon.words(s.effects)
        val newWord = s.newEffect?.let { lexicon.word(it) }
        val longest = words.maxOf { it.text.length }
        // A one-row step keeps one row but gains room for the word plus what its effect needs.
        val width = if (s.height == 1) maxOf(s.width, newWord!!.text.length + if (s.newEffect == BlotEffect.PAIR) 2 else 1) else maxOf(s.width, longest)
        val height = if (s.height == 1) 1 else maxOf(s.height, minOf(longest, s.height + 1))
        val tier = BlotTier.DISCOVER
        for (attempt in 0 until 400) {
            val random = Random(7_919L * (i + 1) + attempt)
            val holes = if (s.shaped) Blots.shape(width, height, random) else emptySet()
            val built = BlotGenerator.build(width, height, words, tier, random, mustUse = setOfNotNull(newWord), maxPreInked = width * height / 5,
                knots = s.knots, wilds = s.wilds, seals = s.seals, holes = holes) ?: continue
            val start = built.first
            if (start.count { it != INK } < 3) continue
            val needsIt = needs(s, start, width, words, newWord)
            if (needsIt || attempt > 300) return Blotwords(tier, attempt.toLong(), width, words, start, step = i, plan = built.second, theme = theme, holes = holes)
        }
        error("No trail puzzle for step $i")
    }

    /**
     * True when [start] can't be finished without what the step teaches: its new word, or its knots joining
     * letters (as plain blanks they'd join nothing), or its wild squares standing in for letters.
     */
    fun needs(s: Step, start: String, width: Int, words: List<BlotWord>, newWord: BlotWord?): Boolean {
        if (newWord != null) {
            val without = words - newWord
            return without.isEmpty() || BlotSolver.solve(start, width, without, 50_000, start = start) is BlotSolver.Result.Dead
        }
        // A seal step just needs a seal to break: some square must be inked twice.
        if (s.seals > 0) return start.any { it in 'a'..'z' }
        val square = if (s.knots > 0) KNOT else WILD
        if (square !in start) return false
        // Blanks stand for squares that do nothing for words; WRITE is left out so they can't be filled.
        val plain = words.filter { it.effect != BlotEffect.WRITE }
        return BlotSolver.solve(start.replace(square, BLANK), width, plain, 50_000) is BlotSolver.Result.Dead
    }
}

object BlotCodec {
    private fun word(w: BlotWord) = "${w.effect.name}:${w.text}"
    private fun wordOf(s: String): BlotWord = s.split(':').let { require(it.size == 2); BlotWord(BlotEffect.valueOf(it[0]), it[1]) }

    fun encode(g: Blotwords) = listOf("2", g.tier.name, g.seed.toString(), g.width.toString(), g.words.joinToString(",", transform = ::word),
        g.start, g.cells, g.pending?.let(::word) ?: "", g.written.joinToString(",", transform = ::word), g.hints.toString(), g.step.toString(),
        g.plan.joinToString(";") { m -> "${word(m.word)}:${m.path.joinToString(".")}:${m.targets.joinToString(".")}:${m.letter ?: ""}" },
        g.theme, g.strokes.joinToString(";") { it.joinToString(".") }, g.holes.sorted().joinToString(".")).joinToString("\n")

    fun decode(text: String): Blotwords? = try {
        // Saves from before strokes were kept have 13 lines.
        val l = text.split('\n'); require(l.size in 13..15 && l[0] == "2")
        fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split('.').map { it.toInt() }
        fun words(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map(::wordOf)
        val plan = if (l[11].isEmpty()) emptyList() else l[11].split(';').map { part ->
            val f = part.split(':'); require(f.size == 5)
            BlotMove(BlotWord(BlotEffect.valueOf(f[0]), f[1]), ints(f[2]), ints(f[3]), f[4].firstOrNull())
        }
        Blotwords(BlotTier.valueOf(l[1]), l[2].toLong(), l[3].toInt(), words(l[4]), l[5], l[6],
            l[7].takeIf { it.isNotEmpty() }?.let(::wordOf), words(l[8]), l[9].toInt(), l[10].toInt(), plan, l[12],
            if (l.size < 14 || l[13].isEmpty()) emptyList() else l[13].split(';').map(::ints),
            if (l.size < 15) emptySet() else ints(l[14]).toSet())
    } catch (_: IllegalArgumentException) { null }
}
