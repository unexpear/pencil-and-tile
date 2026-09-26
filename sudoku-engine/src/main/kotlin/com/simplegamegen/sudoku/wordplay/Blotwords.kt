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
/** An echo square: it holds no letter, and inking any echo inks every echo on the board. */
const val ECHO = '~'
/** A gap: no square at all. Words read straight across it (as across ink), and nothing can ink it. */
const val GAP = ' '
/** Arrow squares, pointing up, right, down and left. PUSH slides one along, shoving the squares ahead of it. */
const val ARROWS = "\u2191\u2192\u2193\u2190"

/** What a command word lets you do once it's written. */
enum class BlotEffect(val effect: String) {
    ONE("Ink any one more square."),
    PAIR("Ink two squares that touch."),
    ALIKE("Pick a letter: every square showing it is inked."),
    WRITE("Write any letter in a blank square."),
    DIAG("Pick a square: it and every square on its rising diagonal are inked."),
    MEND("Pick an inked square: it comes back as it started. Or pick a letter: it gets a seal."),
    PUSH("Pick an arrow square: it's inked, then slides one space its way, pushing the squares in front of it along to the next gap."),
}

/** A command word and what it does. */
data class BlotWord(val effect: BlotEffect, val text: String) {
    companion object {
        /** Letters worth writing with a WRITE word: only command words are ever read. */
        fun lettersOf(words: List<BlotWord>): List<Char> = words.flatMap { it.text.toList() }.distinct().sorted()
    }
}

/** The command words, one per effect. */
data class BlotLexicon(val one: String, val pair: String, val alike: String, val write: String, val diag: String = "GOBA", val mend: String = "MIPA",
    val push: String = "KOPA") {
    fun text(e: BlotEffect): String = when (e) {
        BlotEffect.ONE -> one; BlotEffect.PAIR -> pair; BlotEffect.ALIKE -> alike; BlotEffect.WRITE -> write; BlotEffect.DIAG -> diag
        BlotEffect.MEND -> mend
        BlotEffect.PUSH -> push
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
    val maxPreInked: Int, val decoy: Double, val knots: Double = 0.0, val wilds: Double = 0.0, val seals: Double = 0.0, val echoes: Double = 0.0,
    /** Boards with an uneven outline and holes, rather than full rectangles. */
    val shaped: Boolean = false) {
    DISCOVER("Discover", 4, 4, BlotEffect.entries, 0.0, 0, 0.0),
    EASY("Easy", 5, 5, listOf(BlotEffect.ONE), 0.0, 5, 0.35),
    MEDIUM("Medium", 5, 5, listOf(BlotEffect.ONE, BlotEffect.PAIR), 0.06, 5, 0.5, shaped = true),
    HARD("Hard", 6, 6, listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.ALIKE, BlotEffect.DIAG), 0.08, 6, 0.6, knots = 0.14, seals = 0.3, echoes = 0.12, shaped = true),
    EXPERT("Expert", 6, 6, BlotEffect.entries - BlotEffect.MEND - BlotEffect.PUSH, 0.1, 6, 0.7, knots = 0.16, wilds = 0.06, seals = 0.35, echoes = 0.15, shaped = true),
}

/**
 * A loose piece: squares (as row and column steps from its first square) with what each shows. It starts off the
 * board and goes into a gap it fits; [home] is where it was cut from, so a hint can put it back there.
 */
data class BlotPiece(val shape: List<Pair<Int, Int>>, val squares: String, val home: Int) {
    init { require(shape.size == squares.length && shape.first() == (0 to 0)) }
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
    /** The day of a daily puzzle as yyyymmdd, or 0. */
    val daily: Int = 0,
    /** The edges join: a word running off one side comes back on the other. */
    val wrap: Boolean = false,
    /** Loose pieces to put into the board's gaps before any word can be written. */
    val pieces: List<BlotPiece> = emptyList(),
    /** Where each piece's first square sits, or null while it's still off the board. */
    val placed: List<Int?> = pieces.map { null },
) {
    init {
        require(width > 0 && start.length % width == 0 && cells.length == start.length)
        require(cells.all { it == INK || it == GAP || it == BLANK || it == KNOT || it == WILD || it == ECHO || it in ARROWS || it in 'A'..'Z' || it in 'a'..'z' } && words.isNotEmpty() && hints >= 0)
        require(theme.none { it == '\n' })
    }

    val height: Int get() = cells.length / width
    val solved: Boolean get() = settled && pending == null && Blots.done(cells)
    /** Every loose piece is on the board, so they're fixed and words can be written. */
    val settled: Boolean get() = placed.all { it != null }
    /** The places with no square. */
    val holes: Set<Int> get() = cells.indices.filter { cells[it] == GAP }.toSet()
    /** No effect waiting and no word to write, yet squares remain. */
    val stuck: Boolean get() = settled && !solved && pending == null && Blots.placements(cells, width, words, wrap).isEmpty()
    val over: Boolean get() = solved
    val left: Int get() = cells.count { Blots.showing(it) }

    /** Where a word can be written along [path] (its letters in reading order) right now, with the knots it runs through. */
    fun placementAt(path: List<Int>): Blots.Placement? {
        if (!settled || pending != null || path.size < 2) return null
        val all = Blots.routes(cells, width, words, wrap)
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
        return copy(cells = next, pending = if (Blots.done(next)) null else placed.word, written = written + placed.word,
            strokes = strokes + listOf(placed.route))
    }

    /** ONE: ink one square. PAIR: ink two touching squares. ALIKE: ink every square with the letter at [a]. */
    fun use(a: Int, b: Int? = null): Blotwords? {
        val next = when (pending?.effect) {
            BlotEffect.ONE -> if (b == null && Blots.showing(cells[a])) Blots.inked(cells, listOf(a)) else null
            BlotEffect.PAIR -> if (b != null && Blots.touching(cells, width, a, b, wrap)) Blots.inked(cells, listOf(a, b)) else null
            BlotEffect.ALIKE -> if (b == null && Blots.alikeable(cells[a])) Blots.alike(cells, cells[a].uppercaseChar()) else null
            BlotEffect.DIAG -> if (b == null && Blots.showing(cells[a])) Blots.diagonal(cells, width, a) else null
            BlotEffect.MEND -> if (b == null) Blots.mend(cells, start, a) else null
            BlotEffect.PUSH -> if (b == null) Blots.push(cells, width, a) else null
            else -> null
        } ?: return null
        // Pushed squares carry their word's joins with them.
        val moved = (if (pending?.effect == BlotEffect.PUSH) Blots.pushMoves(cells, width, a) else null) ?: emptyMap()
        return copy(cells = next, pending = null, strokes = if (moved.isEmpty()) strokes else strokes.map { w -> w.map { moved[it] ?: it } })
    }

    /** WRITE: put [letter] in the blank square [at]. */
    fun fill(at: Int, letter: Char): Blotwords? {
        if (pending?.effect != BlotEffect.WRITE || cells[at] != BLANK || letter !in 'A'..'Z') return null
        return copy(cells = cells.substring(0, at) + letter + cells.substring(at + 1), pending = null)
    }

    fun restart(): Blotwords = copy(cells = start, pending = null, strokes = emptyList(), placed = pieces.map { null })

    /** The squares piece [k] would cover with its first square at [at], or null when it doesn't fit there. */
    fun footprint(k: Int, at: Int): List<Int>? {
        val p = pieces.getOrNull(k) ?: return null
        val r0 = at / width; val c0 = at % width
        return p.shape.map { (dr, dc) ->
            val r = r0 + dr; val c = c0 + dc
            if (r !in 0 until height || c !in 0 until width || cells[r * width + c] != GAP) return null
            r * width + c
        }
    }

    /** Puts loose piece [k] on the board with its first square at [at]; once the last one is in, they're all fixed. */
    fun place(k: Int, at: Int): Blotwords? {
        if (settled || placed.getOrNull(k) != null) return null
        val squares = footprint(k, at) ?: return null
        val a = cells.toCharArray()
        squares.forEachIndexed { n, i -> a[i] = pieces[k].squares[n] }
        return copy(cells = String(a), placed = placed.toMutableList().also { it[k] = at })
    }

    /** Takes piece [k] back off the board, while the pieces aren't fixed yet. */
    fun lift(k: Int): Blotwords? {
        val at = placed.getOrNull(k) ?: return null
        if (settled) return null
        val r0 = at / width; val c0 = at % width
        val a = cells.toCharArray()
        pieces[k].shape.forEach { (dr, dc) -> a[(r0 + dr) * width + c0 + dc] = GAP }
        return copy(cells = String(a), placed = placed.toMutableList().also { it[k] = null })
    }

    /** The piece covering square [i], if any is placed there. */
    fun pieceAt(i: Int): Int? = placed.indices.firstOrNull { k ->
        val at = placed[k] ?: return@firstOrNull false
        pieces[k].shape.any { (dr, dc) -> (at / width + dr) * width + at % width + dc == i }
    }

    /**
     * The next move toward a finish from here; null when the position can't be finished (or the search ran
     * out of time). With an effect waiting, only that effect's choices are tried, and the path is empty.
     */
    fun nextMove(budget: Int = 200_000): BlotMove? {
        if (solved || !settled) return null
        planned()?.let { return it }
        return when (val r = BlotSolver.solve(cells, width, words, budget, pending, start, wrap)) {
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
    fun deadEnd(budget: Int = 200_000): Boolean = !solved && BlotSolver.solve(cells, width, words, budget, pending, start, wrap) is BlotSolver.Result.Dead

    fun hinted(): Blotwords = copy(hints = hints + 1)
}

/** Grid helpers shared by the game, solver and generator. */
object Blots {
    /** The step (rows, columns) an arrow square points, or null for any other square. */
    fun arrowStep(ch: Char): Pair<Int, Int>? = when (ARROWS.indexOf(ch)) {
        0 -> -1 to 0; 1 -> 0 to 1; 2 -> 1 to 0; 3 -> 0 to -1; else -> null
    }

    /**
     * Where each square goes when the arrow at [at] is used: the arrow and every square in a line ahead of it move one
     * space its way, up to the first gap. Null when the line runs off the board with no gap to move into.
     */
    fun pushMoves(cells: String, width: Int, at: Int): Map<Int, Int>? {
        val (dr, dc) = arrowStep(cells[at])?.takeIf { showing(cells[at]) } ?: return null
        val h = cells.length / width
        val moves = HashMap<Int, Int>()
        var r = at / width; var c = at % width
        while (true) {
            val nr = r + dr; val nc = c + dc
            if (nr !in 0 until h || nc !in 0 until width) return null
            moves[r * width + c] = nr * width + nc
            r = nr; c = nc
            if (cells[r * width + c] == GAP) return moves
        }
    }

    /** PUSH at [at]: the arrow is inked and slides one space, shoving the squares ahead of it into the next gap. */
    fun push(cells: String, width: Int, at: Int): String? {
        val moves = pushMoves(cells, width, at) ?: return null
        val a = cells.toCharArray()
        for ((from, to) in moves) a[to] = cells[from]
        a[at] = GAP
        a[moves.getValue(at)] = INK
        return String(a)
    }

    /** A square still to be inked (not ink, and not a gap where there's no square). */
    fun showing(ch: Char): Boolean = ch != INK && ch != GAP

    /** Nothing left to ink. */
    fun done(cells: String): Boolean = cells.none { showing(it) }

    /** Words and touching squares read straight across ink and gaps alike. */
    private fun clear(ch: Char): Boolean = ch == INK || ch == GAP

    /** Inks [path]: sealed squares lose their seal instead, and inking an echo inks every echo. Gaps stay gaps. */
    fun inked(cells: String, path: List<Int>): String {
        val a = cells.toCharArray()
        val echo = path.any { a[it] == ECHO }
        path.forEach { if (a[it] != GAP) a[it] = if (a[it] in 'a'..'z') a[it].uppercaseChar() else INK }
        if (echo) for (i in a.indices) if (a[i] == ECHO) a[i] = INK
        return String(a)
    }

    /** Inks every square showing [letter]; sealed ones lose their seal instead. */
    fun alike(cells: String, letter: Char): String = cells.map { if (it == letter) INK else if (it.isLowerCase() && it.uppercaseChar() == letter) letter else it }.joinToString("")

    /** Squares ALIKE can pick: letters (sealed or not), and knots or wilds (which all go together). */
    fun alikeable(ch: Char): Boolean = ch in 'A'..'Z' || ch in 'a'..'z' || ch == KNOT || ch == WILD || ch == ECHO

    /** True for a square still under its seal. */
    fun sealed(ch: Char): Boolean = ch in 'a'..'z'

    /** MEND at [at]: an inked square comes back as it was in [start]; a plain letter gets a seal. Null when neither applies. */
    fun mend(cells: String, start: String?, at: Int): String? {
        val now = cells[at]
        val back = when {
            now == INK && start != null && showing(start[at]) -> start[at]
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
    fun routes(cells: String, width: Int, words: List<BlotWord>, wrap: Boolean = false): List<Placement> {
        val h = cells.length / width
        val out = ArrayList<Placement>()
        val left = cells.count { showing(it) }
        val hasBlank = cells.indexOf(BLANK) >= 0
        val dirs = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
        for (w in words) {
            val t = w.text
            val seen = HashSet<List<Int>>()
            fun walk(at: Int, d: Int, idx: Int, path: List<Int>, route: List<Int>) {
                var r = at / width; var c = at % width
                var steps = 0
                while (true) {
                    r += dirs[d].first; c += dirs[d].second
                    if (r !in 0 until h || c !in 0 until width) {
                        // On a board whose edges join, carry on from the other side.
                        if (!wrap) return
                        r = (r + h) % h; c = (c + width) % width
                    }
                    if (++steps > width + h) return
                    val i = r * width + c
                    val ch = cells[i]
                    when {
                        clear(ch) -> continue
                        ch == KNOT -> {
                            if (i in route) return
                            val via = route + i
                            // Carry straight on, or turn a corner here.
                            walk(i, d, idx, path, via)
                            walk(i, (d + 1) % 4, idx, path, via); walk(i, (d + 3) % 4, idx, path, via)
                            return
                        }
                        spells(ch, t[idx]) -> {
                            if (i in path) return
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
        for (r in 0 until h) out += (0 until width).map { r * width + it }.filter { showing(cells[it]) }.toIntArray()
        for (c in 0 until width) out += (0 until h).map { it * width + c }.filter { showing(cells[it]) }.toIntArray()
        return out
    }

    /** [path] runs straight along one row or column, one way, with only inked squares between its letters. */
    fun inLine(cells: String, width: Int, path: List<Int>): Boolean {
        if (path.any { !showing(cells[it]) } || path.toSet().size != path.size) return false
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
            while (k < hi) { if (!clear(cells[k])) return false; k += step }
        }
        return true
    }

    /** Two showing squares in one row or column with only inked squares between them (either way round, when the edges join). */
    fun touching(cells: String, width: Int, a: Int, b: Int, wrap: Boolean = false): Boolean {
        if (a == b || !showing(cells[a]) || !showing(cells[b])) return false
        val sameRow = a / width == b / width
        if (!sameRow && a % width != b % width) return false
        if (inLine(cells, width, listOf(minOf(a, b), maxOf(a, b)))) return true
        if (!wrap) return false
        // The other way round: from the later square off the edge and back to the earlier one.
        val h = cells.length / width
        val line = if (sameRow) (0 until width).map { (a / width) * width + it } else (0 until h).map { it * width + a % width }
        val lo = line.indexOf(minOf(a, b)); val hi = line.indexOf(maxOf(a, b))
        return (line.subList(hi + 1, line.size) + line.subList(0, lo)).all { clear(cells[it]) }
    }

    /** A word may only be written when its effect can then be used (or nothing is left to ink). */
    fun effectPossible(after: String, width: Int, word: BlotWord): Boolean {
        if (done(after)) return true
        return when (word.effect) {
            BlotEffect.ONE -> true
            BlotEffect.PAIR -> lines(after, width).any { it.size >= 2 }
            BlotEffect.ALIKE -> after.any { alikeable(it) }
            BlotEffect.WRITE -> after.any { it == BLANK }
            BlotEffect.DIAG -> true
            // The word's own letters were just inked, so there's always one to bring back.
            BlotEffect.MEND -> true
            BlotEffect.PUSH -> after.indices.any { pushMoves(after, width, it) != null }
        }
    }

    /** Every place a command word can be written now, as paths in reading order. */
    fun placements(cells: String, width: Int, words: List<BlotWord>, wrap: Boolean = false): List<Pair<BlotWord, List<Int>>> =
        routes(cells, width, words, wrap).map { it.word to it.path }

    /** [effectPossible] without rebuilding the grid in the common cases. */
    private fun usable(cells: String, width: Int, w: BlotWord, path: List<Int>, left: Int, hasBlank: Boolean): Boolean = when {
        left == path.size && path.none { sealed(cells[it]) } -> true
        w.effect == BlotEffect.ONE || w.effect == BlotEffect.DIAG || w.effect == BlotEffect.MEND -> true
        w.effect == BlotEffect.WRITE -> hasBlank
        w.effect == BlotEffect.PAIR && left - path.size > 4 -> true
        else -> effectPossible(inked(cells, path), width, w)
    }

    /** The effect choices after [word] was written, each as (targets, letter). */
    fun effects(cells: String, width: Int, word: BlotWord, words: List<BlotWord>, start: String? = null, wrap: Boolean = false): List<Pair<List<Int>, Char?>> {
        if (done(cells)) return listOf(emptyList<Int>() to null)
        return when (word.effect) {
            BlotEffect.ONE -> cells.indices.filter { showing(cells[it]) }.map { listOf(it) to null }
            BlotEffect.PAIR -> lines(cells, width).flatMap { l ->
                (1 until l.size).map { listOf(l[it - 1], l[it]) to null } + if (wrap && l.size >= 3) listOf(listOf(l.last(), l.first()) to null) else emptyList()
            }
            BlotEffect.ALIKE -> cells.filter { alikeable(it) }.map { it.uppercaseChar() }.toSet().sorted()
                .map { ch -> listOf(cells.indexOfFirst { it.uppercaseChar() == ch }) to null }
            BlotEffect.WRITE -> cells.indices.filter { cells[it] == BLANK }.flatMap { i -> BlotWord.lettersOf(words).map { listOf(i) to it } }
            // One choice per diagonal that still has something showing.
            BlotEffect.DIAG -> cells.indices.filter { showing(cells[it]) }.distinctBy { it / width + it % width }.map { listOf(it) to null }
            BlotEffect.MEND -> cells.indices.filter { mend(cells, start, it) != null }.map { listOf(it) to null }
            BlotEffect.PUSH -> cells.indices.filter { pushMoves(cells, width, it) != null }.map { listOf(it) to null }
        }
    }

    fun applyEffect(cells: String, width: Int, word: BlotWord, targets: List<Int>, letter: Char?, start: String? = null): String = when {
        targets.isEmpty() -> cells
        word.effect == BlotEffect.MEND -> mend(cells, start, targets[0]) ?: cells
        word.effect == BlotEffect.PUSH -> push(cells, width, targets[0]) ?: cells
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

    fun solve(cells: String, width: Int, words: List<BlotWord>, budget: Int = 200_000, pending: BlotWord? = null, start: String? = null,
        wrap: Boolean = false): Result {
        val dead = HashSet<String>()
        // MEND can bring squares back, so a position can come round again: never revisit one on the way.
        val onPath = HashSet<String>()
        var nodes = 0
        val origin = start ?: cells
        fun dfs(c: String): List<BlotMove>? {
            if (Blots.done(c)) return emptyList()
            if (c in dead || c in onPath) return null
            if (++nodes > budget) throw OutOfBudget()
            onPath += c
            try {
                for ((w, path) in Blots.placements(c, width, words, wrap)) {
                    val after = Blots.inked(c, path)
                    for ((targets, letter) in ordered(after, width, w, words, origin, wrap)) {
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
                for ((targets, letter) in ordered(cells, width, pending, words, origin, wrap)) {
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
    fun ordered(after: String, width: Int, w: BlotWord, words: List<BlotWord>, start: String? = null, wrap: Boolean = false): List<Pair<List<Int>, Char?>> =
        rank(after, width, w, words, start, wrap).flatMap { it.value }

    private fun rank(after: String, width: Int, w: BlotWord, words: List<BlotWord>, start: String? = null, wrap: Boolean = false): Map<Int, List<Pair<List<Int>, Char?>>> {
        val commandLetters = BlotWord.lettersOf(words).toSet()
        val now = Blots.placements(after, width, words, wrap)
        val inWords = now.flatMap { it.second }.toSet()
        fun appeal(i: Int) = (if (i in inWords) 2 else 0) + (if (after[i] in commandLetters) 1 else 0)
        return Blots.effects(after, width, w, words, start, wrap).groupBy { (t, letter) ->
            when (w.effect) {
                // Bringing back a square that lets a new word be written comes first; sealing a letter comes last.
                BlotEffect.MEND -> when {
                    Blots.showing(after[t[0]]) -> 2
                    Blots.placements(Blots.applyEffect(after, width, w, t, letter, start), width, words, wrap).size > now.size -> 0
                    else -> 1
                }
                BlotEffect.WRITE -> if (Blots.placements(Blots.applyEffect(after, width, w, t, letter), width, words, wrap).size > now.size) 0 else 1
                BlotEffect.ALIKE -> if (t.isNotEmpty() && after[t[0]] in commandLetters) 1 else 0
                else -> t.sumOf { appeal(it) }
            }
        }.toSortedMap()
    }

    /**
     * Share of playthroughs by a sensible but unplanned player that ink the whole grid: any word they can
     * see, then effects spent on squares no word is using. Low means many tempting dead ends.
     */
    fun luck(cells: String, width: Int, words: List<BlotWord>, tries: Int, random: Random, wrap: Boolean = false): Double {
        var wins = 0
        repeat(tries) {
            var c = cells
            while (true) {
                if (Blots.done(c)) { wins++; break }
                val options = Blots.placements(c, width, words, wrap)
                if (options.isEmpty()) break
                val (w, path) = options.random(random)
                val after = Blots.inked(c, path)
                // The most appealing kind of effect, picked at random within that kind.
                val (targets, letter) = rank(after, width, w, words, cells, wrap).values.first().random(random)
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
            // Now and then an expert board's edges join; those stay whole, as holes would read oddly across a join.
            val wrap = tier == BlotTier.EXPERT && random.nextDouble() < 0.2
            val holes = if (tier.shaped && !wrap) Blots.shape(tier.width, tier.height, random) else emptySet()
            val built = build(tier.width, tier.height, words, tier, random, holes = holes, wrap = wrap) ?: return@repeat
            val luck = BlotSolver.luck(built.first, tier.width, words, 120, random, wrap)
            val (lo, hi) = band(tier)
            val game = Blotwords(tier, seed, tier.width, words, built.first, plan = built.second, theme = theme, wrap = wrap)
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
        seals: Double = tier.seals, holes: Set<Int> = emptySet(), wrap: Boolean = false, echoes: Double = tier.echoes): Pair<String, List<BlotMove>>? {
        repeat(30) {
            val d = Draft(width, height, plainFiller(words), knots, wilds, seals, holes, wrap, echoes)
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
            if (replays(start, width, words, forward, wrap)) return start to forward
        }
        return null
    }

    fun replays(start: String, width: Int, words: List<BlotWord>, moves: List<BlotMove>, wrap: Boolean = false): Boolean {
        var g = Blotwords(BlotTier.DISCOVER, 0, width, words, start, wrap = wrap)
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
        val seals: Double = 0.0, holes: Set<Int> = emptySet(), val wrap: Boolean = false, val echoes: Double = 0.0) {
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

        fun startGrid(): String = String(CharArray(size) { if (hole[it]) GAP else if (inked[it]) INK else content[it] })

        private fun filler(tier: BlotTier, words: List<BlotWord>, random: Random): Char {
            if (random.nextDouble() < knots) return KNOT
            if (random.nextDouble() < tier.blankChance) return BLANK
            return if (random.nextDouble() < tier.decoy) BlotWord.lettersOf(words).random(random) else plain.random(random)
        }

        private fun lineCells(): List<List<Int>> =
            (0 until height).map { r -> (0 until width).map { r * width + it } } + (0 until width).map { c -> (0 until height).map { it * width + c } }

        /** A line to scan for runs: when the edges join, start it just after a square that breaks runs, so runs can cross the join. */
        private fun runLine(line: List<Int>, breaks: (Int) -> Boolean): List<Int> {
            if (!wrap) return line
            val at = line.indexOfFirst(breaks)
            return if (at < 0) line else line.subList(at + 1, line.size) + line.subList(0, at + 1)
        }

        /** Squares next to each other once the inked squares between them drop out. */
        private fun touchingPairs(): List<Pair<Int, Int>> = lineCells().map { l -> runLine(l) { !inked[it] } }.flatMap { line ->
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
                BlotEffect.PUSH -> {
                    // Going forward the arrow was inked where it landed, one space past a gap that it left; the squares
                    // ahead of it had each moved one space on into a gap. Undo that: slide them back, the arrow too.
                    val dirs = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)
                    val options = (0 until size).filter { hole[it] }.flatMap { g -> dirs.indices.map { g to it } }.filter { (g, d) ->
                        val r = g / width + dirs[d].first; val c = g % width + dirs[d].second
                        r in 0 until height && c in 0 until width && (r * width + c).let { x -> inked[x] && !hole[x] && promised[x] == '\u0000' && !locked[x] }
                    }
                    val (g, d) = options.randomOrNull(random) ?: return null
                    val (dr, dc) = dirs[d]
                    // The line from the landed arrow up to its last square (the next gap or the edge comes after it).
                    val line = ArrayList<Int>()
                    var r = g / width + dr; var c = g % width + dc
                    while (r in 0 until height && c in 0 until width && !hole[r * width + c]) { line += r * width + c; r += dr; c += dc }
                    // Each place takes what's one further along; the last becomes a gap, and the gap becomes the arrow.
                    val order = listOf(g) + line
                    for (k in 0 until order.size - 1) {
                        val to = order[k]; val from = order[k + 1]
                        content[to] = content[from]; inked[to] = inked[from]; fromWord[to] = fromWord[from]
                        promised[to] = promised[from]; locked[to] = locked[from]; hole[to] = hole[from]
                    }
                    val last = order.last()
                    hole[last] = true; inked[last] = true; content[last] = '\u0000'; fromWord[last] = false; promised[last] = '\u0000'; locked[last] = false
                    content[g] = ARROWS[d]; inked[g] = false; hole[g] = false; fromWord[g] = false
                    if (inkedCells.size < k + 1) return restore()
                    listOf(g)
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
            // Sometimes the square the effect hit was an echo, and one or two more echoes went dark with it. Only one set of
            // echoes is ever showing at a time, or inking one set would ink the other too.
            if (word.effect in listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.DIAG) && targets.isNotEmpty() && random.nextDouble() < echoes &&
                (0 until size).none { !inked[it] && content[it] == ECHO } && !locked[targets[0]] && saved.second[targets[0]] && !inked[targets[0]]) {
                val others = (0 until size).filter { inked[it] && !hole[it] && promised[it] == '\u0000' }.shuffled(random).take(1 + random.nextInt(2))
                if (others.isNotEmpty() && (0 until size).count { inked[it] && !hole[it] } - others.size >= k) {
                    content[targets[0]] = ECHO
                    others.forEach { inked[it] = false; content[it] = ECHO }
                }
            }
            // The word's letters: inked squares along one row or column with nothing showing between them but knots.
            val spans = lineCells().map { l -> runLine(l) { !inked[it] && content[it] != KNOT } }.flatMap { line ->
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

/** Cuts loose pieces out of a finished puzzle's start: small groups of letters that leave gaps where they were. */
object BlotPieces {
    fun cut(start: String, width: Int, count: Int, random: Random): Pair<String, List<BlotPiece>>? {
        val h = start.length / width
        val a = start.toCharArray()
        val pieces = ArrayList<BlotPiece>()
        repeat(count) {
            // A letter with a letter beside or below it, and sometimes one more: a domino or an L of three.
            val seeds = a.indices.filter { a[it] in 'A'..'Z' }.shuffled(random)
            val group = seeds.firstNotNullOfOrNull { i ->
                val r = i / width; val c = i % width
                val next = listOf(r to c + 1, r + 1 to c).filter { (nr, nc) -> nr < h && nc < width && a[nr * width + nc] in 'A'..'Z' }.randomOrNull(random)
                    ?: return@firstNotNullOfOrNull null
                val g = mutableListOf(i, next.first * width + next.second)
                if (random.nextBoolean()) {
                    val (lr, lc) = next
                    listOf(lr to lc + 1, lr + 1 to lc).filter { (nr, nc) -> nr < h && nc < width && a[nr * width + nc] in 'A'..'Z' && nr * width + nc !in g }
                        .randomOrNull(random)?.let { g += it.first * width + it.second }
                }
                g
            } ?: return null
            val first = group.minOf { it }
            val shape = group.sorted().map { (it / width - first / width) to (it % width - first % width) }
            pieces += BlotPiece(shape, group.sorted().map { a[it] }.joinToString(""), first)
            group.forEach { a[it] = GAP }
        }
        return String(a) to pieces
    }
}

/** The daily puzzle: the same for everyone on a day, easy on Mondays and hardest on Sundays. */
object BlotDaily {
    /** The level for a day of the week, Monday = 1 to Sunday = 7. */
    fun tierFor(dayOfWeek: Int): BlotTier = when (dayOfWeek) {
        1, 2 -> BlotTier.EASY
        3, 4 -> BlotTier.MEDIUM
        5, 6 -> BlotTier.HARD
        else -> BlotTier.EXPERT
    }

    /** The puzzle for [day] (yyyymmdd), which fell on [dayOfWeek]. */
    fun puzzle(day: Int, dayOfWeek: Int, theme: String = "ink"): Blotwords =
        BlotGenerator.generate(tierFor(dayOfWeek), day.toLong() * 7_919L + 17, theme).copy(daily = day)

    /** Days in a row ending [today] (or yesterday, if today isn't done yet) found in [solved], given as yyyymmdd. */
    fun streak(solved: Set<Int>, today: java.time.LocalDate): Int {
        fun key(d: java.time.LocalDate) = d.year * 10_000 + d.monthValue * 100 + d.dayOfMonth
        var d = if (key(today) in solved) today else today.minusDays(1)
        var n = 0
        while (key(d) in solved) { n++; d = d.minusDays(1) }
        return n
    }
}

/** The Discover trail: small puzzles that each need the newest word, so solving one shows what it does. */
object BlotTrail {
    /** A trail step: the words in play and the one it teaches, or a square it teaches (knots or wilds). */
    data class Step(val effects: List<BlotEffect>, val newEffect: BlotEffect?, val width: Int, val height: Int,
        val knots: Double = 0.0, val wilds: Double = 0.0, val seals: Double = 0.0, val shaped: Boolean = false, val wrap: Boolean = false,
        val echoes: Double = 0.0,
        /** Spare gaps along the right and bottom edges, for pushing into. */
        val room: Boolean = false,
        /** Loose pieces cut out of the finished puzzle, to be put back before play. */
        val pieces: Int = 0) {
        /** What the step is about: an effect's name, or KNOT / WILD / SEAL / ECHO / WRAP. */
        val teaches: String get() = newEffect?.name ?: when { pieces > 0 -> "PIECES"; wrap -> "WRAP"; echoes > 0 -> "ECHO"; seals > 0 -> "SEAL"; knots > 0 -> "KNOT"; else -> "WILD" }
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
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR), null, 4, 4, echoes = 0.6),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.DIAG), null, 5, 5, echoes = 0.5, shaped = true),
        Step(listOf(BlotEffect.ONE, BlotEffect.PUSH), BlotEffect.PUSH, 4, 3, room = true),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.PUSH), BlotEffect.PUSH, 5, 4, shaped = true, room = true),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR), null, 5, 4, shaped = true, pieces = 1),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.ALIKE), null, 5, 5, shaped = true, pieces = 2),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR), null, 4, 4, wrap = true),
        Step(listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.ALIKE, BlotEffect.DIAG), null, 5, 5, knots = 0.1, seals = 0.15, wrap = true),
    )

    /** The same puzzle every time for a step: the first build that can't be finished without the new word. */
    fun puzzle(index: Int, theme: String = "ink", lexicon: BlotLexicon = BlotLexicon.INK): Blotwords {
        val i = index.coerceIn(steps.indices)
        val s = steps[i]
        val words = lexicon.words(s.effects)
        val newWord = s.newEffect?.let { lexicon.word(it) }
        val longest = words.maxOf { it.text.length }
        // A one-row step keeps one row but gains room for the word plus what its effect needs.
        // Boards with room to push get an extra column and row of gaps around the squares.
        val extra = if (s.room) 1 else 0
        val width = (if (s.height == 1) maxOf(s.width, newWord!!.text.length + if (s.newEffect == BlotEffect.PAIR) 2 else 1) else maxOf(s.width, longest)) + extra
        val height = (if (s.height == 1) 1 else maxOf(s.height, minOf(longest, s.height + 1))) + extra
        val tier = BlotTier.DISCOVER
        for (attempt in 0 until 400) {
            val random = Random(7_919L * (i + 1) + attempt)
            val holes = (if (s.shaped) Blots.shape(width, height, random) else emptySet()) +
                // A margin of gaps along the right edge and bottom row, so there's somewhere to push squares into.
                (if (s.room) (0 until height).map { it * width + width - 1 } + (0 until width).map { (height - 1) * width + it } else emptyList())
            val built = BlotGenerator.build(width, height, words, tier, random, mustUse = setOfNotNull(newWord), maxPreInked = width * height / 5,
                knots = s.knots, wilds = s.wilds, seals = s.seals, holes = holes, wrap = s.wrap, echoes = s.echoes) ?: continue
            val start = built.first
            if (start.count { Blots.showing(it) } < 3) continue
            val needsIt = s.pieces > 0 || needs(s, start, width, words, newWord)
            if (s.pieces > 0) {
                val (cut, pieces) = BlotPieces.cut(start, width, s.pieces, random) ?: continue
                return Blotwords(tier, attempt.toLong(), width, words, cut, step = i, plan = built.second, theme = theme, pieces = pieces)
            }
            if (needsIt || attempt > 300) return Blotwords(tier, attempt.toLong(), width, words, start, step = i, plan = built.second, theme = theme, wrap = s.wrap)
        }
        error("No trail puzzle for step $i")
    }

    /**
     * True when [start] can't be finished without what the step teaches: its new word, or its knots joining
     * letters (as plain blanks they'd join nothing), or its wild squares standing in for letters.
     */
    fun needs(s: Step, start: String, width: Int, words: List<BlotWord>, newWord: BlotWord?): Boolean {
        // A board whose edges join must need a word that runs across the join.
        if (s.wrap) return BlotSolver.solve(start, width, words, 50_000, start = start, wrap = false) is BlotSolver.Result.Dead
        if (newWord != null) {
            val without = words - newWord
            return without.isEmpty() || BlotSolver.solve(start, width, without, 50_000, start = start) is BlotSolver.Result.Dead
        }
        // A seal step just needs a seal to break: some square must be inked twice.
        if (s.seals > 0) return start.any { it in 'a'..'z' }
        // Echoes must matter: as plain blanks, each needing its own inking, the puzzle can't be finished.
        if (s.echoes > 0) return ECHO in start &&
            BlotSolver.solve(start.replace(ECHO, BLANK), width, words.filter { it.effect != BlotEffect.WRITE }, 50_000, start = start) is BlotSolver.Result.Dead
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
        g.theme, g.strokes.joinToString(";") { it.joinToString(".") }, "", g.daily.toString(), if (g.wrap) "1" else "0",
        g.pieces.joinToString(";") { p -> p.shape.joinToString(".") { "${it.first},${it.second}" } + ":" + p.squares.replace(' ', '_') + ":" + p.home },
        g.placed.joinToString(",") { it?.toString() ?: "" }).joinToString("\n")

    fun decode(text: String): Blotwords? = try {
        // Saves from before strokes were kept have 13 lines.
        val l = text.split('\n'); require(l.size in 13..19 && l[0] == "2")
        fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split('.').map { it.toInt() }
        fun words(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map(::wordOf)
        val plan = if (l[11].isEmpty()) emptyList() else l[11].split(';').map { part ->
            val f = part.split(':'); require(f.size == 5)
            BlotMove(BlotWord(BlotEffect.valueOf(f[0]), f[1]), ints(f[2]), ints(f[3]), f[4].firstOrNull())
        }
        // Saves that listed holes separately had ink there; those places are gaps now.
        val holes = if (l.size >= 15) ints(l[14]).toSet() else emptySet()
        fun gapped(g: String) = String(CharArray(g.length) { if (it in holes) GAP else g[it] })
        Blotwords(BlotTier.valueOf(l[1]), l[2].toLong(), l[3].toInt(), words(l[4]), gapped(l[5]), gapped(l[6]),
            l[7].takeIf { it.isNotEmpty() }?.let(::wordOf), words(l[8]), l[9].toInt(), l[10].toInt(), plan, l[12],
            if (l.size < 14 || l[13].isEmpty()) emptyList() else l[13].split(';').map(::ints),
            if (l.size < 16) 0 else l[15].toInt(),
            l.size >= 17 && l[16] == "1",
            if (l.size < 19 || l[17].isEmpty()) emptyList() else l[17].split(';').map { part ->
                val f = part.split(':'); require(f.size == 3)
                BlotPiece(f[0].split('.').map { xy -> xy.split(',').let { it[0].toInt() to it[1].toInt() } }, f[1].replace('_', ' '), f[2].toInt())
            },
            if (l.size < 19 || l[17].isEmpty()) emptyList() else l[18].split(',').map { it.toIntOrNull() })
    } catch (_: IllegalArgumentException) { null }
}
