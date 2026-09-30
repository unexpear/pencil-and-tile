package com.simplegamegen.sudoku.wordplay

import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Turn [start] into [target] by changing one letter at a time. Every step has to
 * be a real word of the same length. [par] is the shortest route. You may take
 * a few extra steps, up to [limit]. Words are English.
 */
data class WordLadder(
    val setting: Int,
    val seed: Long,
    val start: String,
    val target: String,
    val trail: List<String>,
    val par: Int,
    val limit: Int,
) {
    init {
        require(setting in NAMES.indices)
        require(start.length == target.length && start != target && par >= 1 && limit >= par && limit <= 20)
        require(trail.isNotEmpty() && trail.first() == start && trail.size <= limit + 1)
        require(trail.all { it.length == start.length && Lexicon.isWord(it) } && Lexicon.isWord(target))
        for (i in 1 until trail.size) require(changes(trail[i - 1], trail[i]) == 1)
    }

    val current: String get() = trail.last()
    val steps: Int get() = trail.size - 1
    val left: Int get() = limit - steps
    val won: Boolean get() = current == target
    val lost: Boolean get() = !won && steps >= limit
    val over: Boolean get() = won || lost

    /** The word made by writing [letter] at [index], when that word is real. */
    fun replacement(index: Int, letter: Char): String? {
        if (over || index !in current.indices) return null
        if (current[index] == letter) return null
        val chars = current.toCharArray()
        chars[index] = letter
        val word = String(chars)
        return word.takeIf { Lexicon.isWord(it) }
    }

    /** Steps to [word] when it differs by one letter and is a real word. */
    fun play(word: String): WordLadder? {
        if (over) return null
        val next = word.trim().uppercase()
        if (next.length != current.length || changes(current, next) != 1 || !Lexicon.isWord(next)) return null
        return copy(trail = trail + next)
    }

    /** Plays the next word on a shortest route from here to the target. */
    fun hint(): WordLadder? {
        val step = LadderWords.nextOnPath(current, target) ?: return null
        return play(step)
    }

    companion object {
        val NAMES = listOf("Easy", "Medium", "Hard", "Expert")
        private val LENGTHS = intArrayOf(3, 4, 4, 5)
        private val DISTANCES = intArrayOf(3, 4, 6, 5)
        private val SLACK = intArrayOf(4, 3, 2, 1)

        fun changes(a: String, b: String): Int {
            if (a.length != b.length) return Int.MAX_VALUE
            var n = 0
            for (i in a.indices) if (a[i] != b[i]) n++
            return n
        }

        fun encode(g: WordLadder) = listOf(
            "1",
            g.setting.toString(),
            g.seed.toString(),
            g.start,
            g.target,
            g.par.toString(),
            g.limit.toString(),
            g.trail.joinToString(","),
        ).joinToString("\n")

        fun decode(text: String): WordLadder? = try {
            val lines = text.split('\n')
            require(lines.size == 8 && lines[0] == "1")
            WordLadder(
                lines[1].toInt(),
                lines[2].toLong(),
                lines[3],
                lines[4],
                lines[7].split(','),
                lines[5].toInt(),
                lines[6].toInt(),
            )
        } catch (_: IllegalArgumentException) { null }

        fun start(seed: Long, setting: Int): WordLadder {
            require(setting in NAMES.indices)
            val random = Random(seed)
            val length = LENGTHS[setting]
            val want = DISTANCES[setting]
            val slack = SLACK[setting]
            val ends = Lexicon.common.filter { it.length == length && LadderWords.neighbors(it).isNotEmpty() }
            require(ends.isNotEmpty()) { "no ladder words of length $length" }
            repeat(50) {
                val from = ends.random(random)
                val choices = LadderWords.commonAt(from, want)
                if (choices.isNotEmpty()) {
                    return WordLadder(setting, seed, from, choices.random(random), listOf(from), want, want + slack)
                }
            }
            val from = ends.random(random)
            val to = (2 downTo 1).firstNotNullOf { dist -> LadderWords.commonAt(from, dist).firstOrNull() }
            val dist = LadderWords.distance(from, to)
            return WordLadder(setting, seed, from, to, listOf(from), dist, dist + slack)
        }
    }
}

internal object LadderWords {
    private val graphs = HashMap<Int, Map<String, List<String>>>()

    fun neighbors(word: String): List<String> = synchronized(graphs) {
        graphs.getOrPut(word.length) { build(word.length) }[word].orEmpty()
    }

    fun distance(from: String, to: String): Int {
        if (from == to) return 0
        val prev = route(from, to) ?: return -1
        var steps = 0
        var cursor = to
        while (cursor != from) {
            cursor = prev[cursor] ?: return -1
            steps++
        }
        return steps
    }

    /** The word after [from] on a shortest route to [to]. */
    fun nextOnPath(from: String, to: String): String? {
        if (from == to) return null
        val prev = route(from, to) ?: return null
        var cursor = to
        var before = prev[cursor] ?: return null
        while (before != from) {
            cursor = before
            before = prev[cursor] ?: return null
        }
        return cursor
    }

    fun commonAt(from: String, dist: Int): List<String> {
        val found = mutableListOf<String>()
        val seen = HashSet<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(from to 0)
        seen.add(from)
        while (queue.isNotEmpty()) {
            val (word, away) = queue.removeFirst()
            if (away == dist) {
                if (word in Lexicon.common) found += word
                continue
            }
            for (next in neighbors(word)) if (seen.add(next)) queue.add(next to away + 1)
        }
        return found
    }

    private fun route(from: String, to: String): Map<String, String>? {
        val prev = HashMap<String, String>()
        val queue = ArrayDeque<String>()
        queue.add(from)
        prev[from] = from
        while (queue.isNotEmpty()) {
            val word = queue.removeFirst()
            for (next in neighbors(word)) {
                if (prev.containsKey(next)) continue
                prev[next] = word
                if (next == to) return prev
                queue.add(next)
            }
        }
        return null
    }

    private fun build(length: Int): Map<String, List<String>> {
        val buckets = HashMap<String, MutableList<String>>()
        for (word in Lexicon.all) {
            if (word.length != length) continue
            val chars = word.toCharArray()
            for (i in chars.indices) {
                val saved = chars[i]
                chars[i] = '*'
                buckets.getOrPut(String(chars)) { ArrayList(2) }.add(word)
                chars[i] = saved
            }
        }
        val near = HashMap<String, MutableList<String>>()
        for (group in buckets.values) {
            if (group.size < 2) continue
            for (word in group) {
                val list = near.getOrPut(word) { ArrayList() }
                for (other in group) if (other != word) list.add(other)
            }
        }
        return near
    }
}
