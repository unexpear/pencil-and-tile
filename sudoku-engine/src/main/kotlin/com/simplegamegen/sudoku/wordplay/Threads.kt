package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.solver.checkpoint
import kotlin.random.Random

// ---------------- Common Threads ----------------

/**
 * One way a group of four words can belong together. Every rule can test any word, so a finished
 * puzzle can be checked for a second way to split its sixteen words.
 */
sealed class ThreadRule {
    /** Stable id stored in saves, like "tag:FRUIT" or "before:BALL". */
    abstract val id: String
    /** Common words that are meant to show this rule. */
    abstract val members: Set<String>
    /** True when [word] belongs, generously: anything a player could reasonably argue for counts. */
    abstract fun fits(word: String): Boolean
    /** Harder rules come later in the reveal order and use a darker band. */
    abstract val depth: Int

    /** Plain category, like fruits or chess pieces. [also] holds words that fit but aren't used as members. */
    class Tag(val key: String, val label: String, override val members: Set<String>, val also: Set<String> = emptySet()) : ThreadRule() {
        override val id get() = "tag:$key"
        override fun fits(word: String) = word in members || word in also
        override val depth get() = 0
    }

    /** Words that make a new word with [partner]: before it (FOOT → FOOTBALL) or after it (SUN → SUNFLOWER). */
    class Compound(val partner: String, val partnerFirst: Boolean, override val members: Set<String>, val also: Set<String> = emptySet()) : ThreadRule() {
        override val id get() = (if (partnerFirst) "after:" else "before:") + partner
        override fun fits(word: String) = word in members || word in also
        override val depth get() = 1
    }

    /** Words that contain a shorter word from [hidden] (SCATTER hides CAT); the word itself doesn't count. */
    class Hidden(val key: String, val label: String, val hidden: Set<String>, override val members: Set<String>) : ThreadRule() {
        override val id get() = "hidden:$key"
        override fun fits(word: String) = hidden.any { it != word && it.length < word.length && it in word }
        override val depth get() = 3
    }

    /** Words whose letters rearrange into a word from [targets] (ACT → CAT). */
    class Anagram(val key: String, val label: String, val targets: Set<String>, override val members: Set<String>) : ThreadRule() {
        private val sorted = targets.groupBy { it.toCharArray().sorted().joinToString("") }
        override val id get() = "anagram:$key"
        override fun fits(word: String) = sorted[word.toCharArray().sorted().joinToString("")]?.any { it != word } == true
        override val depth get() = 2
    }
}

/** A language's word material for Common Threads. */
class ThreadsLexicon(val rules: List<ThreadRule>) {
    private val byId = rules.associateBy { it.id }
    fun rule(id: String): ThreadRule? = byId[id]

    init {
        require(byId.size == rules.size) { "duplicate thread rule ids" }
        rules.forEach { r ->
            require(r.members.size >= 4 && r.members.all { w -> w.isNotEmpty() && w.all { it in 'A'..'Z' } }) { r.id }
            require(r.members.all(r::fits)) { "${r.id} rejects one of its own members" }
        }
    }
}

/** One solved or hidden group: its rule and its four words in board order. */
data class Thread(val rule: String, val words: List<String>)

/**
 * Common Threads: sixteen words hide four groups of four. Select four and submit; a right group
 * locks in, a wrong one costs a life. [order] is the board order of the unsolved words.
 */
data class ThreadsGame(
    val level: LogicLevel,
    val seed: Long,
    val threads: List<Thread>,
    val order: List<String>,
    val solved: List<Int> = emptyList(),
    val mistakes: Int = 0,
    /** Threads whose connection was shown by a hint. */
    val shown: Set<Int> = emptySet(),
    /** Wrong guesses already made, so the same four never cost twice. */
    val tried: Set<String> = emptySet(),
    val hints: Int = 0,
) {
    init {
        require(threads.size == 4 && threads.all { it.words.size == 4 })
        val all = threads.flatMap { it.words }
        require(all.toSet().size == 16) { "every word appears once" }
        require(order.toSet() == all.toSet() - solved.flatMap { threads[it].words }.toSet() && order.size == order.toSet().size)
        require(solved.all { it in 0..3 } && solved.toSet().size == solved.size)
        require(mistakes in 0..limitOf(level) && shown.all { it in 0..3 } && hints >= 0)
    }

    val limit: Int get() = limitOf(level)
    val complete: Boolean get() = solved.size == 4 && mistakes < limit
    val lost: Boolean get() = mistakes >= limit
    val over: Boolean get() = complete || lost

    /** What a guess did. */
    enum class Verdict { RIGHT, ONE_AWAY, WRONG, REPEATED, INVALID }

    /** Tries the four [picked] words; returns the new state and what happened. */
    fun guess(picked: Set<String>): Pair<ThreadsGame, Verdict> {
        if (over || picked.size != 4 || !order.containsAll(picked)) return this to Verdict.INVALID
        val match = threads.indices.firstOrNull { it !in solved && threads[it].words.toSet() == picked }
        if (match != null) return copy(solved = solved + match, order = order - picked) to Verdict.RIGHT
        val key = picked.sorted().joinToString(",")
        if (key in tried) return this to Verdict.REPEATED
        val close = threads.indices.any { it !in solved && threads[it].words.count(picked::contains) == 3 }
        val next = copy(mistakes = mistakes + 1, tried = tried + key)
        // Out of lives: the remaining threads are laid out so the player can see the answer.
        val ended = if (next.lost) next.copy(solved = solved + threads.indices.filter { it !in solved }, order = emptyList()) else next
        return ended to (if (close) Verdict.ONE_AWAY else Verdict.WRONG)
    }

    /** Mixes the unsolved words. */
    fun shuffle(random: Random): ThreadsGame = copy(order = order.shuffled(random))

    /** Shows the connection of the easiest unsolved thread that isn't shown yet. */
    fun hint(): ThreadsGame? {
        if (over) return null
        val next = threads.indices.filter { it !in solved && it !in shown }.minByOrNull { it } ?: return null
        return copy(shown = shown + next, hints = hints + 1)
    }

    companion object {
        fun limitOf(level: LogicLevel) = listOf(5, 4, 4, 3)[level.ordinal]

        /** Rule kinds each level may use and how many words must fit two groups at once. */
        private fun mixOf(level: LogicLevel): Pair<List<Set<Int>>, IntRange> = when (level) {
            LogicLevel.EASY -> listOf(setOf(0), setOf(0), setOf(0), setOf(0)) to 0..0
            LogicLevel.MEDIUM -> listOf(setOf(0), setOf(0), setOf(0), setOf(1)) to 1..3
            LogicLevel.HARD -> listOf(setOf(0), setOf(0), setOf(1), setOf(1, 2, 3)) to 2..5
            LogicLevel.EXPERT -> listOf(setOf(0), setOf(1), setOf(1, 2, 3), setOf(2, 3)) to 3..8
        }

        /** How many ways the sixteen words split into the four groups (stops counting at 2). */
        fun splits(words: List<String>, rules: List<ThreadRule>): Int {
            val options = words.map { w -> rules.indices.filter { rules[it].fits(w) } }
            if (options.any { it.isEmpty() }) return 0
            val room = IntArray(rules.size) { 4 }
            val order = words.indices.sortedBy { options[it].size }
            var found = 0
            fun place(k: Int) {
                if (found >= 2) return
                if (k == order.size) { found++; return }
                for (g in options[order[k]]) if (room[g] > 0) { room[g]--; place(k + 1); room[g]++ }
            }
            place(0)
            return found
        }

        /** Words among [words] that fit more than one of [rules]: the red herrings. */
        fun overlaps(words: List<String>, rules: List<ThreadRule>) = words.count { w -> rules.count { it.fits(w) } > 1 }

        fun generate(seed: Long, level: LogicLevel, lexicon: ThreadsLexicon = EnglishThreads.lexicon): ThreadsGame {
            val random = Random(seed)
            val (kinds, herrings) = mixOf(level)
            val byDepth = lexicon.rules.groupBy { it.depth }
            repeat(4000) {
                checkpoint()
                val picked = mutableListOf<ThreadRule>()
                for (allowed in kinds) {
                    val pool = allowed.flatMap { byDepth[it].orEmpty() }.filter { r -> r !in picked }
                    picked += pool.randomOrNull(random) ?: return@repeat
                }
                val used = mutableSetOf<String>()
                val groups = picked.map { rule ->
                    // Words that also fit another chosen rule make good red herrings on harder levels.
                    val free = rule.members.filter { it !in used && (level != LogicLevel.EASY || picked.none { o -> o !== rule && o.fits(it) }) }
                    if (free.size < 4) return@repeat
                    val tricky = free.filter { w -> picked.any { it !== rule && it.fits(w) } }.shuffled(random)
                    val plain = (free - tricky.toSet()).shuffled(random)
                    val lean = if (level == LogicLevel.EASY) 0 else random.nextInt(0, minOf(3, tricky.size) + 1)
                    (tricky.take(lean) + plain + tricky.drop(lean)).take(4).also { used += it }
                }
                val words = groups.flatten()
                if (words.size != 16 || overlaps(words, picked) !in herrings || splits(words, picked) != 1) return@repeat
                // Easier threads first so hints and the reveal go from plain to tricky.
                val sorted = picked.indices.sortedBy { picked[it].depth * 10 + groups[it].count { w -> picked.count { r -> r.fits(w) } > 1 } }
                val threads = sorted.map { Thread(picked[it].id, groups[it].sorted()) }
                return ThreadsGame(level, seed, threads, words.shuffled(random))
            }
            error("no Common Threads puzzle for $level")
        }
    }
}

object ThreadsCodec {
    fun encode(g: ThreadsGame) = listOf(
        "1", g.level.name, g.seed.toString(),
        g.threads.joinToString(";") { it.rule + "=" + it.words.joinToString(",") },
        g.order.joinToString(","), g.solved.joinToString(","), g.mistakes.toString(),
        g.shown.sorted().joinToString(","), g.tried.joinToString(";"), g.hints.toString(),
    ).joinToString("\n")

    fun decode(text: String): ThreadsGame? = try {
        val l = text.split('\n'); require(l.size == 10 && l[0] == "1")
        fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map { it.toInt() }
        val threads = l[3].split(';').map { part -> part.substringBefore('=').let { r -> Thread(r, part.substringAfter('=').split(',')) } }
        ThreadsGame(
            LogicLevel.valueOf(l[1]), l[2].toLong(), threads,
            if (l[4].isEmpty()) emptyList() else l[4].split(','), ints(l[5]), l[6].toInt(),
            ints(l[7]).toSet(), if (l[8].isEmpty()) emptySet() else l[8].split(';').toSet(), l[9].toInt(),
        )
    } catch (_: IllegalArgumentException) { null }
}
