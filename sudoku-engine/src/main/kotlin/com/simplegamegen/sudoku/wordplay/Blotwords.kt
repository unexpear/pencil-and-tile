package com.simplegamegen.sudoku.wordplay

import kotlin.random.Random

// ---------------- Blotwords ----------------
//
// Ink every square of a letter grid. Writing a command word (read in a straight row or column,
// forwards or backwards) inks its letters, then its effect happens straight away. Inked squares
// drop out of the way, so the letters on either side of them become neighbours.

const val INK = '#'
const val BLANK = '.'

/** What a command word lets you do once it's written. */
enum class BlotEffect(val effect: String) {
    ONE("Ink any one more square."),
    PAIR("Ink two squares that touch."),
    ALIKE("Pick a letter: every square showing it is inked."),
    WRITE("Write any letter in a blank square."),
}

/** A command word and what it does. */
data class BlotWord(val effect: BlotEffect, val text: String) {
    companion object {
        /** Letters worth writing with a WRITE word: only command words are ever read. */
        fun lettersOf(words: List<BlotWord>): List<Char> = words.flatMap { it.text.toList() }.distinct().sorted()
    }
}

/** The four command words, one per effect. */
data class BlotLexicon(val one: String, val pair: String, val alike: String, val write: String) {
    fun text(e: BlotEffect): String = when (e) {
        BlotEffect.ONE -> one; BlotEffect.PAIR -> pair; BlotEffect.ALIKE -> alike; BlotEffect.WRITE -> write
    }

    fun word(e: BlotEffect) = BlotWord(e, text(e))
    fun words(effects: List<BlotEffect> = BlotEffect.entries): List<BlotWord> = effects.map(::word)

    companion object {
        val INK = BlotLexicon("VUM", "DRIF", "ZUV", "KEL")
    }
}

/** The ways to play: a trail of small puzzles that introduce the words one by one, then four levels. */
enum class BlotTier(val label: String, val width: Int, val height: Int, val effects: List<BlotEffect>, val blankChance: Double,
    val maxPreInked: Int, val decoy: Double) {
    DISCOVER("Discover", 4, 4, BlotEffect.entries, 0.0, 0, 0.0),
    EASY("Easy", 5, 5, listOf(BlotEffect.ONE), 0.0, 5, 0.35),
    MEDIUM("Medium", 5, 5, listOf(BlotEffect.ONE, BlotEffect.PAIR), 0.06, 5, 0.5),
    HARD("Hard", 6, 6, listOf(BlotEffect.ONE, BlotEffect.PAIR, BlotEffect.ALIKE), 0.08, 6, 0.6),
    EXPERT("Expert", 6, 6, BlotEffect.entries, 0.1, 6, 0.7),
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
) {
    init {
        require(width > 0 && start.length % width == 0 && cells.length == start.length)
        require(cells.all { it == INK || it == BLANK || it in 'A'..'Z' } && words.isNotEmpty() && hints >= 0)
        require(theme.none { it == '\n' })
    }

    val height: Int get() = cells.length / width
    val solved: Boolean get() = pending == null && cells.all { it == INK }
    /** No effect waiting and no word to write, yet squares remain. */
    val stuck: Boolean get() = !solved && pending == null && Blots.placements(cells, width, words).isEmpty()
    val over: Boolean get() = solved
    val left: Int get() = cells.count { it != INK }

    /** The word [path] spells, if it is a legal place to write one right now. */
    fun wordAt(path: List<Int>): BlotWord? {
        if (pending != null || path.size < 2) return null
        val word = words.firstOrNull { w -> w.text.length == path.size && path.indices.all { cells[path[it]] == w.text[it] } } ?: return null
        return if (Blots.inLine(cells, width, path) && Blots.effectPossible(Blots.inked(cells, path), width, word)) word else null
    }

    /** Writes the word along [path]; its effect then waits unless the grid is already full. */
    fun write(path: List<Int>): Blotwords? {
        val word = wordAt(path) ?: return null
        val next = Blots.inked(cells, path)
        return copy(cells = next, pending = if (next.all { it == INK }) null else word, written = written + word)
    }

    /** ONE: ink one square. PAIR: ink two touching squares. ALIKE: ink every square with the letter at [a]. */
    fun use(a: Int, b: Int? = null): Blotwords? {
        val next = when (pending?.effect) {
            BlotEffect.ONE -> if (b == null && cells[a] != INK) Blots.inked(cells, listOf(a)) else null
            BlotEffect.PAIR -> if (b != null && Blots.touching(cells, width, a, b)) Blots.inked(cells, listOf(a, b)) else null
            BlotEffect.ALIKE -> if (b == null && cells[a] in 'A'..'Z') Blots.alike(cells, cells[a]) else null
            else -> null
        } ?: return null
        return copy(cells = next, pending = null)
    }

    /** WRITE: put [letter] in the blank square [at]. */
    fun fill(at: Int, letter: Char): Blotwords? {
        if (pending?.effect != BlotEffect.WRITE || cells[at] != BLANK || letter !in 'A'..'Z') return null
        return copy(cells = cells.substring(0, at) + letter + cells.substring(at + 1), pending = null)
    }

    fun restart(): Blotwords = copy(cells = start, pending = null)

    /**
     * The next move toward a finish from here; null when the position can't be finished (or the search ran
     * out of time). With an effect waiting, only that effect's choices are tried, and the path is empty.
     */
    fun nextMove(budget: Int = 200_000): BlotMove? {
        if (solved) return null
        planned()?.let { return it }
        return when (val r = BlotSolver.solve(cells, width, words, budget, pending)) {
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
            c = Blots.applyEffect(written, m.word, m.targets, m.letter)
        }
        return null
    }

    /** True when the solver proves this position can't be finished. */
    fun deadEnd(budget: Int = 200_000): Boolean = !solved && BlotSolver.solve(cells, width, words, budget, pending) is BlotSolver.Result.Dead

    fun hinted(): Blotwords = copy(hints = hints + 1)
}

/** Grid helpers shared by the game, solver and generator. */
object Blots {
    fun inked(cells: String, path: List<Int>): String {
        val a = cells.toCharArray(); path.forEach { a[it] = INK }; return String(a)
    }

    fun alike(cells: String, letter: Char): String = cells.map { if (it == letter) INK else it }.joinToString("")

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
            BlotEffect.ALIKE -> after.any { it in 'A'..'Z' }
            BlotEffect.WRITE -> after.any { it == BLANK }
        }
    }

    /** Every place a command word can be written now, as paths in reading order. */
    fun placements(cells: String, width: Int, words: List<BlotWord>): List<Pair<BlotWord, List<Int>>> {
        val out = ArrayList<Pair<BlotWord, List<Int>>>()
        val left = cells.count { it != INK }
        val hasBlank = cells.indexOf(BLANK) >= 0
        for (line in lines(cells, width)) for (w in words) {
            val t = w.text; val k = t.length
            if (line.size < k) continue
            val palindrome = t == t.reversed()
            for (s in 0..line.size - k) {
                var fwd = true; var back = !palindrome
                for (i in 0 until k) {
                    val ch = cells[line[s + i]]
                    if (ch != t[i]) fwd = false
                    if (ch != t[k - 1 - i]) back = false
                    if (!fwd && !back) break
                }
                if (fwd) { val path = (0 until k).map { line[s + it] }; if (usable(cells, width, w, path, left, hasBlank)) out += w to path }
                if (back) { val path = (0 until k).map { line[s + k - 1 - it] }; if (usable(cells, width, w, path, left, hasBlank)) out += w to path }
            }
        }
        return out
    }

    /** [effectPossible] without rebuilding the grid in the common cases. */
    private fun usable(cells: String, width: Int, w: BlotWord, path: List<Int>, left: Int, hasBlank: Boolean): Boolean = when {
        left == path.size -> true
        w.effect == BlotEffect.ONE -> true
        w.effect == BlotEffect.WRITE -> hasBlank
        w.effect == BlotEffect.PAIR && left - path.size > 4 -> true
        else -> effectPossible(inked(cells, path), width, w)
    }

    /** The effect choices after [word] was written, each as (targets, letter). */
    fun effects(cells: String, width: Int, word: BlotWord, words: List<BlotWord>): List<Pair<List<Int>, Char?>> {
        if (cells.all { it == INK }) return listOf(emptyList<Int>() to null)
        return when (word.effect) {
            BlotEffect.ONE -> cells.indices.filter { cells[it] != INK }.map { listOf(it) to null }
            BlotEffect.PAIR -> lines(cells, width).flatMap { l -> (1 until l.size).map { listOf(l[it - 1], l[it]) to null } }
            BlotEffect.ALIKE -> cells.filter { it in 'A'..'Z' }.toSet().sorted().map { ch -> listOf(cells.indexOf(ch)) to null }
            BlotEffect.WRITE -> cells.indices.filter { cells[it] == BLANK }.flatMap { i -> BlotWord.lettersOf(words).map { listOf(i) to it } }
        }
    }

    fun applyEffect(cells: String, word: BlotWord, targets: List<Int>, letter: Char?): String = when {
        targets.isEmpty() -> cells
        word.effect == BlotEffect.ALIKE -> alike(cells, cells[targets[0]])
        word.effect == BlotEffect.WRITE -> cells.substring(0, targets[0]) + letter!! + cells.substring(targets[0] + 1)
        else -> inked(cells, targets)
    }

    fun play(cells: String, move: BlotMove): String = applyEffect(inked(cells, move.path), move.word, move.targets, move.letter)
}

/** Depth-first search over whole turns, remembering positions already shown to be dead ends. */
object BlotSolver {
    sealed interface Result {
        data class Solved(val moves: List<BlotMove>) : Result
        data object Dead : Result
        data object Unknown : Result
    }

    private class OutOfBudget : RuntimeException(null, null, false, false)

    fun solve(cells: String, width: Int, words: List<BlotWord>, budget: Int = 200_000, pending: BlotWord? = null): Result {
        val dead = HashSet<String>()
        var nodes = 0
        fun dfs(c: String): List<BlotMove>? {
            if (c.all { it == INK }) return emptyList()
            if (c in dead) return null
            if (++nodes > budget) throw OutOfBudget()
            for ((w, path) in Blots.placements(c, width, words)) {
                val after = Blots.inked(c, path)
                for ((targets, letter) in ordered(after, width, w, words)) {
                    val rest = dfs(Blots.applyEffect(after, w, targets, letter))
                    if (rest != null) return listOf(BlotMove(w, path, targets, letter)) + rest
                }
            }
            dead += c
            return null
        }
        return try {
            if (pending != null) {
                // The word is already written: only its effect's choices are open.
                for ((targets, letter) in ordered(cells, width, pending, words)) {
                    val rest = dfs(Blots.applyEffect(cells, pending, targets, letter))
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
    fun ordered(after: String, width: Int, w: BlotWord, words: List<BlotWord>): List<Pair<List<Int>, Char?>> =
        rank(after, width, w, words).flatMap { it.value }

    private fun rank(after: String, width: Int, w: BlotWord, words: List<BlotWord>): Map<Int, List<Pair<List<Int>, Char?>>> {
        val commandLetters = BlotWord.lettersOf(words).toSet()
        val now = Blots.placements(after, width, words)
        val inWords = now.flatMap { it.second }.toSet()
        fun appeal(i: Int) = (if (i in inWords) 2 else 0) + (if (after[i] in commandLetters) 1 else 0)
        return Blots.effects(after, width, w, words).groupBy { (t, letter) ->
            when (w.effect) {
                BlotEffect.WRITE -> if (Blots.placements(Blots.applyEffect(after, w, t, letter), width, words).size > now.size) 0 else 1
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
                val (targets, letter) = rank(after, width, w, words).values.first().random(random)
                c = Blots.applyEffect(after, w, targets, letter)
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
            val built = build(tier.width, tier.height, words, tier, random) ?: return@repeat
            val luck = BlotSolver.luck(built.first, tier.width, words, 120, random)
            val (lo, hi) = band(tier)
            val game = Blotwords(tier, seed, tier.width, words, built.first, plan = built.second, theme = theme)
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
        mustUse: Collection<BlotWord> = words, maxPreInked: Int = tier.maxPreInked): Pair<String, List<BlotMove>>? {
        repeat(30) {
            val d = Draft(width, height, plainFiller(words))
            val moves = ArrayList<BlotMove>()
            var guard = 0
            while (guard++ < 60) {
                val order = words.shuffled(random).sortedByDescending { w -> if (w in mustUse && moves.none { it.word == w }) 1 else 0 }
                val move = order.firstNotNullOfOrNull { w -> d.unwind(w, tier, words, random) } ?: break
                moves += move
                if (d.inkedCount() <= maxPreInked && mustUse.all { w -> moves.any { it.word == w } } && (d.inkedCount() == 0 || random.nextDouble() < 0.35)) break
            }
            if (d.inkedCount() > maxPreInked || !mustUse.all { w -> moves.any { it.word == w } }) return@repeat
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

    private class Draft(val width: Int, val height: Int, val plain: List<Char>) {
        val size = width * height
        val content = CharArray(size) { '?' }
        val inked = BooleanArray(size) { true }
        val fromWord = BooleanArray(size)

        fun inkedCount() = inked.count { it }

        fun startGrid(): String = String(CharArray(size) { if (inked[it]) INK else content[it] })

        private fun filler(tier: BlotTier, words: List<BlotWord>, random: Random): Char {
            if (random.nextDouble() < tier.blankChance) return BLANK
            return if (random.nextDouble() < tier.decoy) BlotWord.lettersOf(words).random(random) else plain.random(random)
        }

        private fun lineCells(): List<List<Int>> =
            (0 until height).map { r -> (0 until width).map { r * width + it } } + (0 until width).map { c -> (0 until height).map { it * width + c } }

        /** Squares next to each other once the inked squares between them drop out. */
        private fun touchingPairs(): List<Pair<Int, Int>> = lineCells().flatMap { line ->
            val out = ArrayList<Pair<Int, Int>>()
            for (i in line.indices) if (inked[line[i]]) {
                for (j in i + 1 until line.size) { if (inked[line[j]]) { out += line[i] to line[j] }; if (!inked[line[j]]) break }
            }
            out
        }

        /** Undoes one turn that used [word]; returns that turn, or null (leaving the draft as it was) when it can't. */
        fun unwind(word: BlotWord, tier: BlotTier, words: List<BlotWord>, random: Random): BlotMove? {
            val saved = Triple(content.copyOf(), inked.copyOf(), fromWord.copyOf())
            fun restore(): BlotMove? { saved.first.copyInto(content); saved.second.copyInto(inked); saved.third.copyInto(fromWord); return null }
            val full = inked.all { it }
            val inkedCells = (0 until size).filter { inked[it] }
            val k = word.text.length
            var letter: Char? = null
            val targets: List<Int> = when (word.effect) {
                BlotEffect.ONE -> {
                    if (inkedCells.size < k + 1) return null
                    val t = inkedCells.random(random); inked[t] = false; content[t] = filler(tier, words, random); listOf(t)
                }
                BlotEffect.PAIR -> {
                    val pairs = touchingPairs().shuffled(random).firstOrNull() ?: return null
                    listOf(pairs.first, pairs.second).onEach { inked[it] = false; content[it] = filler(tier, words, random) }
                }
                BlotEffect.ALIKE -> {
                    val showing = (0 until size).filter { !inked[it] }.map { content[it] }.toSet()
                    val free = (BlotWord.lettersOf(words) + plain).filter { it !in showing }
                    if (free.isEmpty() || inkedCells.size < k + 2) return null
                    val ch = free.random(random)
                    val picks = inkedCells.shuffled(random).take(1 + random.nextInt(minOf(3, inkedCells.size - k)))
                    picks.forEach { inked[it] = false; content[it] = ch }
                    listOf(picks.first())
                }
                BlotEffect.WRITE -> {
                    if (full) return null
                    val t = (0 until size).filter { !inked[it] && fromWord[it] && content[it] in 'A'..'Z' }.randomOrNull(random) ?: return null
                    letter = content[t]; content[t] = BLANK; listOf(t)
                }
            }
            // The word's letters: any cells along one row or column with nothing showing between them.
            val spans = lineCells().flatMap { line ->
                val runs = ArrayList<List<Int>>(); var cur = ArrayList<Int>()
                for (i in line) if (inked[i]) cur.add(i) else { if (cur.size >= k) runs += cur; cur = ArrayList() }
                if (cur.size >= k) runs += cur
                runs
            }
            if (spans.isEmpty()) return restore()
            val span = spans.random(random)
            // Mostly close together, sometimes with gaps the player must ink first.
            val startAt = random.nextInt(span.size - k + 1)
            val chosen = if (random.nextDouble() < 0.6 || span.size == k) span.subList(startAt, startAt + k)
                else span.shuffled(random).take(k).sortedBy { span.indexOf(it) }
            val path = if (random.nextBoolean()) chosen else chosen.reversed()
            path.forEachIndexed { i, cell -> inked[cell] = false; content[cell] = word.text[i]; fromWord[cell] = true }
            return BlotMove(word, path, targets, letter)
        }
    }
}

/** The Discover trail: small puzzles that each need the newest word, so solving one shows what it does. */
object BlotTrail {
    data class Step(val effects: List<BlotEffect>, val newEffect: BlotEffect, val width: Int, val height: Int)

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
        Step(BlotEffect.entries, BlotEffect.WRITE, 5, 5),
    )

    /** The same puzzle every time for a step: the first build that can't be finished without the new word. */
    fun puzzle(index: Int, theme: String = "ink", lexicon: BlotLexicon = BlotLexicon.INK): Blotwords {
        val i = index.coerceIn(steps.indices)
        val s = steps[i]
        val words = lexicon.words(s.effects)
        val newWord = lexicon.word(s.newEffect)
        val longest = words.maxOf { it.text.length }
        // A one-row step keeps one row but gains room for the word plus what its effect needs.
        val width = if (s.height == 1) maxOf(s.width, newWord.text.length + if (s.newEffect == BlotEffect.PAIR) 2 else 1) else maxOf(s.width, longest)
        val height = if (s.height == 1) 1 else maxOf(s.height, minOf(longest, s.height + 1))
        val tier = BlotTier.DISCOVER
        for (attempt in 0 until 400) {
            val random = Random(7_919L * (i + 1) + attempt)
            val built = BlotGenerator.build(width, height, words, tier, random, mustUse = setOf(newWord), maxPreInked = width * height / 5)
                ?: continue
            val start = built.first
            if (start.count { it != INK } < 3) continue
            val without = words - newWord
            val needsIt = without.isEmpty() || BlotSolver.solve(start, width, without, 50_000) is BlotSolver.Result.Dead
            if (needsIt || attempt > 300) return Blotwords(tier, attempt.toLong(), width, words, start, step = i, plan = built.second, theme = theme)
        }
        error("No trail puzzle for step $i")
    }
}

object BlotCodec {
    private fun word(w: BlotWord) = "${w.effect.name}:${w.text}"
    private fun wordOf(s: String): BlotWord = s.split(':').let { require(it.size == 2); BlotWord(BlotEffect.valueOf(it[0]), it[1]) }

    fun encode(g: Blotwords) = listOf("2", g.tier.name, g.seed.toString(), g.width.toString(), g.words.joinToString(",", transform = ::word),
        g.start, g.cells, g.pending?.let(::word) ?: "", g.written.joinToString(",", transform = ::word), g.hints.toString(), g.step.toString(),
        g.plan.joinToString(";") { m -> "${word(m.word)}:${m.path.joinToString(".")}:${m.targets.joinToString(".")}:${m.letter ?: ""}" },
        g.theme).joinToString("\n")

    fun decode(text: String): Blotwords? = try {
        val l = text.split('\n'); require(l.size == 13 && l[0] == "2")
        fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split('.').map { it.toInt() }
        fun words(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map(::wordOf)
        val plan = if (l[11].isEmpty()) emptyList() else l[11].split(';').map { part ->
            val f = part.split(':'); require(f.size == 5)
            BlotMove(BlotWord(BlotEffect.valueOf(f[0]), f[1]), ints(f[2]), ints(f[3]), f[4].firstOrNull())
        }
        Blotwords(BlotTier.valueOf(l[1]), l[2].toLong(), l[3].toInt(), words(l[4]), l[5], l[6],
            l[7].takeIf { it.isNotEmpty() }?.let(::wordOf), words(l[8]), l[9].toInt(), l[10].toInt(), plan, l[12])
    } catch (_: IllegalArgumentException) { null }
}
