package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import kotlin.math.pow
import kotlin.random.Random

// ---------------- Lone Letter ----------------
//
// Each round rolls one letter and deals a list of categories. Write an answer for each that starts with the
// letter, before time runs out. Then the answers are compared with the computer players': an answer scores
// only if nobody else wrote the same thing, and every extra word that starts with the letter ("Big Blue Bus")
// scores a bonus point. Three rounds; the highest total wins.

/** One category and every answer it accepts (built by tools/words/build_categories.py from WordNet). */
class LoneCategory(val id: String, val label: String, answers: List<Pair<String, Int>>) {
    /** Answers by their matching key, with their commonness (0 = accepted but never picked by the computer). */
    private val byKey: Map<String, Pair<String, Int>> = HashMap<String, Pair<String, Int>>().also { m ->
        for ((a, s) in answers) m.putIfAbsent(LoneAnswers.key(a), a to s)
    }

    /** Everyday answers by first letter, most obvious first: what the computer players choose from. */
    val picks: Map<Char, List<String>> = answers.filter { it.second > 0 }.sortedByDescending { it.second }
        .groupBy({ it.first[0].uppercaseChar() }, { it.first })

    private val counts: Map<Char, Int> = answers.groupingBy { it.first[0].uppercaseChar() }.eachCount()

    fun accepts(answer: String): Boolean = LoneAnswers.key(answer) in byKey
    fun answersFor(letter: Char): Int = counts[letter] ?: 0
    fun picksFor(letter: Char): List<String> = picks[letter].orEmpty()
}

object LoneCategories {
    val all: List<LoneCategory> by lazy {
        val stream = LoneCategories::class.java.getResourceAsStream("/categories/en.tsv") ?: error("Missing category list")
        stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
                val (id, label, answers) = line.split('\t')
                LoneCategory(id, label, answers.split(',').map { val at = it.lastIndexOf(':'); it.substring(0, at) to it.substring(at + 1).toInt() })
            }.toList()
        }
    }
    private val byId: Map<String, LoneCategory> by lazy { all.associateBy { it.id } }
    operator fun get(id: String): LoneCategory = byId[id] ?: error("Unknown category $id")
}

/** Tidying and comparing answers. */
object LoneAnswers {
    private val articles = listOf("the ", "a ", "an ")

    /** Lower case letters and single spaces, without a leading "the", "a" or "an". */
    fun tidy(answer: String): String {
        var s = answer.lowercase().replace('-', ' ').filter { it in 'a'..'z' || it == ' ' }.trim().replace(Regex(" +"), " ")
        for (a in articles) if (s.startsWith(a) && s.length > a.length) { s = s.removePrefix(a); break }
        return s
    }

    /** The key two answers match on: tidied, with a plural last word made singular ("tomatoes" = "tomato"). */
    fun key(answer: String): String {
        val s = tidy(answer)
        fun single(w: String): String = when {
            w.length <= 3 || w.endsWith("ss") || w.endsWith("us") || w.endsWith("is") -> w
            w.endsWith("ies") -> w.dropLast(3) + "y"
            w.endsWith("oes") || w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes") || w.endsWith("sses") -> w.dropLast(2)
            w.endsWith("s") -> w.dropLast(1)
            else -> w
        }
        val at = s.lastIndexOf(' ')
        return if (at < 0) single(s) else s.substring(0, at + 1) + single(s.substring(at + 1))
    }

    /** Starts with [letter], once any "the", "a" or "an" is set aside. */
    fun startsWith(answer: String, letter: Char): Boolean = tidy(answer).firstOrNull() == letter.lowercaseChar()

    /** Every word is in the dictionary, so a table could fairly count it even though it isn't on the list. */
    fun realWords(answer: String): Boolean {
        val words = tidy(answer).split(' ').filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.all { Lexicon.isWord(it.uppercase()) }
    }
}

enum class LoneMark { EMPTY, WRONG_LETTER, NOT_LISTED, SAME, GOOD }

data class LoneRound(
    val letter: Char,
    val categories: List<String>,
    /** The player's answers, one per category ("" = blank). */
    val mine: List<String>,
    /** Each computer player's answers, filled in when the round is scored. */
    val rivals: List<List<String>> = emptyList(),
    /** Categories where the player counted an answer that isn't on the list. */
    val counted: Set<Int> = emptySet(),
    /** Categories where a hint showed the start of an everyday answer. */
    val hinted: Map<Int, String> = emptyMap(),
) {
    val scored: Boolean get() = rivals.isNotEmpty()
}

data class LoneLetter(
    val level: LogicLevel,
    val seed: Long,
    val rounds: List<LoneRound>,
    /** Seconds used in the current round. */
    val used: Int = 0,
    val hints: Int = 0,
    /** A round has just been scored and stays on show until the player moves on. */
    val reviewing: Boolean = false,
) {
    init {
        require(rounds.isNotEmpty() && used >= 0 && hints >= 0)
        require(rounds.all { it.mine.size == it.categories.size && (it.rivals.isEmpty() || it.rivals.size == rivalCount(level)) })
    }

    val over: Boolean get() = rounds.all { it.scored }
    /** The round on screen: the one just scored while reviewing, otherwise the one being played. */
    val current: Int get() = if (reviewing || over) rounds.indexOfLast { it.scored } else rounds.indexOfFirst { !it.scored }
    /** Answers can be written: a round is open and not waiting on the player to move on. */
    val playing: Boolean get() = !reviewing && !over
    val limit: Int get() = secondsOf(level)
    val timeLeft: Int? get() = if (limit == 0) null else maxOf(0, limit - used)

    fun mark(round: Int, category: Int): LoneMark = markOf(rounds[round], category, -1)
    fun rivalMark(round: Int, rival: Int, category: Int): LoneMark = markOf(rounds[round], category, rival)

    private fun markOf(r: LoneRound, i: Int, who: Int): LoneMark {
        val answer = if (who < 0) r.mine[i] else r.rivals[who][i]
        if (LoneAnswers.tidy(answer).isEmpty()) return LoneMark.EMPTY
        if (!LoneAnswers.startsWith(answer, r.letter)) return LoneMark.WRONG_LETTER
        if (who < 0 && !LoneCategories[r.categories[i]].accepts(answer) && i !in r.counted) return LoneMark.NOT_LISTED
        val key = LoneAnswers.key(answer)
        val others = (listOf(r.mine) + r.rivals).filterIndexed { k, _ -> k != who + 1 }.map { it[i] }
        return if (others.any { it.isNotBlank() && LoneAnswers.key(it) == key }) LoneMark.SAME else LoneMark.GOOD
    }

    /** One point for a good answer, plus one for each further word that starts with the letter. */
    fun points(round: Int, category: Int, rival: Int = -1): Int {
        val r = rounds[round]
        val mark = if (rival < 0) mark(round, category) else rivalMark(round, rival, category)
        if (mark != LoneMark.GOOD) return 0
        val words = LoneAnswers.tidy(if (rival < 0) r.mine[category] else r.rivals[rival][category]).split(' ')
        return 1 + words.drop(1).count { it.firstOrNull() == r.letter.lowercaseChar() && it.length > 1 }
    }

    fun roundScore(round: Int, rival: Int = -1): Int =
        if (!rounds[round].scored) 0 else rounds[round].categories.indices.sumOf { points(round, it, rival) }

    val score: Int get() = rounds.indices.sumOf { roundScore(it) }
    fun rivalScore(rival: Int): Int = rounds.indices.sumOf { roundScore(it, rival) }
    val rivalScores: List<Int> get() = (0 until rivalCount(level)).map { rivalScore(it) }
    val won: Boolean get() = over && score >= (rivalScores.maxOrNull() ?: 0)

    private fun open(): LoneRound? = if (playing) rounds.firstOrNull { !it.scored } else null
    private fun withOpen(change: (LoneRound) -> LoneRound?): LoneLetter? {
        val r = open() ?: return null
        val next = change(r) ?: return null
        return copy(rounds = rounds.map { if (it === r) next else it })
    }

    /** Writes [text] as the answer for [category] in the round being played. */
    fun answer(category: Int, text: String): LoneLetter? = withOpen { r ->
        if (category !in r.categories.indices) null
        else r.copy(mine = r.mine.toMutableList().also { it[category] = clean(text) })
    }

    /** Counts the clock on; the round is scored when time runs out. */
    fun tick(seconds: Int): LoneLetter? {
        if (open() == null || seconds <= 0) return null
        val next = copy(used = used + seconds)
        return if (limit > 0 && next.used >= limit) next.stop() else next
    }

    /** Ends the round: the computer players reveal their answers and everything is scored. */
    fun stop(): LoneLetter? {
        if (!playing) return null
        val index = rounds.indexOfFirst { !it.scored }
        val r = rounds[index]
        val random = Random(seed * 31 + index)
        val rivals = (0 until rivalCount(level)).map { k -> r.categories.map { rivalAnswer(LoneCategories[it], r.letter, k, random) } }
        return copy(rounds = rounds.toMutableList().also { it[index] = r.copy(rivals = rivals) }, used = 0, reviewing = true)
    }

    /** Moves on from a scored round to the next one. */
    fun next(): LoneLetter? = if (reviewing && !over) copy(reviewing = false) else null

    private fun rivalAnswer(category: LoneCategory, letter: Char, rival: Int, random: Random): String {
        val picks = category.picksFor(letter)
        if (picks.isEmpty() || random.nextDouble() > chanceOf(level)) return ""
        // Most reach for the obvious answer; the better players spread out a little to dodge a match.
        val spread = listOf(0.3, 0.45, 0.6, 0.75)[level.ordinal] + rival * 0.05
        val at = (random.nextDouble().pow(2.0) * picks.size * spread).toInt().coerceIn(0, picks.lastIndex)
        return picks[at]
    }

    /** Lets an answer that isn't on the list count after all, when it's made of real words and starts right. */
    fun count(round: Int, category: Int): LoneLetter? {
        val r = rounds.getOrNull(round) ?: return null
        if (!r.scored || category in r.counted || mark(round, category) != LoneMark.NOT_LISTED) return null
        if (!LoneAnswers.realWords(r.mine[category])) return null
        return copy(rounds = rounds.toMutableList().also { it[round] = r.copy(counted = r.counted + category) })
    }

    /** Can [category]'s answer in [round] be counted by the player? */
    fun countable(round: Int, category: Int): Boolean =
        rounds[round].scored && mark(round, category) == LoneMark.NOT_LISTED && LoneAnswers.realWords(rounds[round].mine[category])

    /** Shows the first two letters of an everyday answer for [category] in the round being played. */
    fun hint(category: Int): LoneLetter? {
        val next = withOpen { r ->
            if (category !in r.categories.indices || category in r.hinted) return@withOpen null
            val pick = LoneCategories[r.categories[category]].picksFor(r.letter).firstOrNull() ?: return@withOpen null
            r.copy(hinted = r.hinted + (category to pick.take(2).uppercase()))
        } ?: return null
        return next.copy(hints = hints + 1)
    }

    companion object {
        const val ROUNDS = 3
        val RIVALS = listOf("Pip", "Juno", "Otto")

        /** Q, U, V, X, Y and Z are left out as the classic die does; the easier levels skip the trickiest letters too. */
        fun lettersOf(level: LogicLevel) = listOf("ABCDEFGHLMPRSTW", "ABCDEFGHILMNOPRSTW", "ABCDEFGHIJKLMNOPRSTW", "ABCDEFGHIJKLMNOPRSTW")[level.ordinal]

        fun categoriesOf(level: LogicLevel) = listOf(6, 8, 10, 12)[level.ordinal]
        fun rivalCount(level: LogicLevel) = listOf(2, 2, 3, 3)[level.ordinal]
        /** Seconds per round; 0 = no clock. */
        fun secondsOf(level: LogicLevel) = listOf(0, 180, 150, 120)[level.ordinal]
        /** How often a computer player thinks of an answer at all. */
        fun chanceOf(level: LogicLevel) = listOf(0.45, 0.6, 0.72, 0.82)[level.ordinal]

        /** Answers keep letters, spaces, hyphens and apostrophes, up to 32 characters. */
        fun clean(text: String): String = text.filter { it.isLetter() || it == ' ' || it == '-' || it == '\'' }.take(32)

        fun generate(seed: Long, level: LogicLevel): LoneLetter {
            val random = Random(seed)
            val letters = lettersOf(level).toList().shuffled(random).take(ROUNDS)
            val all = LoneCategories.all
            val dealt = HashSet<String>()
            val rounds = letters.map { letter ->
                // Deal categories where the letter has answers, the easier levels where there are obvious ones
                // (asking less when a letter is tricky); no category comes up twice while there are others to deal.
                val shuffled = all.shuffled(random).filter { it.answersFor(letter) >= 3 }
                val chosen = ArrayList<LoneCategory>()
                for (need in listOf(4, 3, 2, 1)[level.ordinal] downTo 0) {
                    val fits = shuffled.filter { it !in chosen && it.picksFor(letter).size >= need }
                    chosen += (fits.filter { it.id !in dealt } + fits.filter { it.id in dealt }).take(categoriesOf(level) - chosen.size)
                    if (chosen.size == categoriesOf(level)) break
                }
                dealt += chosen.map { it.id }
                LoneRound(letter, chosen.map { it.id }, List(chosen.size) { "" })
            }
            return LoneLetter(level, seed, rounds)
        }
    }
}

object LoneLetterCodec {
    // Answers never hold these, so they can separate fields.
    private fun list(items: List<String>) = items.joinToString(";")
    private fun unlist(text: String, size: Int) = if (size == 0) emptyList() else text.split(';').also { require(it.size == size) }

    fun encode(g: LoneLetter): String = (listOf("1", g.level.name, g.seed.toString(), g.used.toString(), g.hints.toString(),
        if (g.reviewing) "1" else "0") + g.rounds.map { r ->
        listOf(r.letter.toString(), r.categories.joinToString(","), list(r.mine), r.rivals.joinToString("/") { list(it) },
            r.counted.sorted().joinToString(","), r.hinted.entries.joinToString(",") { "${it.key}:${it.value}" }).joinToString("|")
    }).joinToString("\n")

    fun decode(text: String): LoneLetter? = try {
        val l = text.split('\n'); require(l.size > 6 && l[0] == "1")
        val rounds = l.drop(6).map { line ->
            val f = line.split('|'); require(f.size == 6 && f[0].length == 1)
            val categories = f[1].split(',')
            categories.forEach { LoneCategories[it] }
            LoneRound(f[0][0], categories, unlist(f[2], categories.size),
                if (f[3].isEmpty()) emptyList() else f[3].split('/').map { unlist(it, categories.size) },
                if (f[4].isEmpty()) emptySet() else f[4].split(',').map { it.toInt() }.toSet(),
                if (f[5].isEmpty()) emptyMap() else f[5].split(',').associate { it.substringBefore(':').toInt() to it.substringAfter(':') })
        }
        LoneLetter(LogicLevel.valueOf(l[1]), l[2].toLong(), rounds, l[3].toInt(), l[4].toInt(), l[5] == "1")
    } catch (_: IllegalArgumentException) { null } catch (_: IllegalStateException) { null } catch (_: IndexOutOfBoundsException) { null }
}
