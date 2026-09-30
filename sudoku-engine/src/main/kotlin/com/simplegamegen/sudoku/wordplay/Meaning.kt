package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import kotlin.random.Random

// ---------------- Word Meaning ----------------

/**
 * One word to explain. [right] phrases capture the meaning, [close] phrases are on the way there,
 * [wrong] phrases are opposites or common mix-ups. The same lists feed the optional embedding check.
 */
data class MeaningEntry(
    val word: String,
    val kind: String,
    val sentence: String,
    val meaning: String,
    val right: List<String>,
    val close: List<String>,
    val wrong: List<String>,
    val level: Int,
)

enum class MeaningVerdict { RIGHT, CLOSE, WRONG, UNSURE }

/** Compares a player's guess with an entry's phrases. Negation ("not tired", "no energy") flips a phrase's side. */
object MeaningJudge {
    private val NEGATORS = setOf("not", "no", "never", "without", "nothing", "lack", "lacking", "hardly", "barely")
    private val FILLER = setOf("a", "an", "the", "to", "of", "is", "are", "be", "being", "someone", "something", "who", "that", "which",
        "it", "its", "they", "them", "their", "you", "your", "and", "or", "very", "really", "sort", "type")

    /** Lower case, contractions expanded, punctuation dropped, light stemming. */
    fun tokens(text: String): List<String> = text.lowercase()
        .replace("n't", " not").replace("’", "'").replace("'s", "")
        .replace(Regex("[^a-z ]"), " ").split(' ').filter { it.isNotBlank() }.map(::stem)

    fun stem(w: String): String = when {
        w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
        w.length > 4 && w.endsWith("ied") -> w.dropLast(3) + "y"
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && w.endsWith("ed") -> w.dropLast(2)
        w.length > 4 && w.endsWith("ly") -> w.dropLast(2)
        w.length > 3 && w.endsWith("es") && !w.endsWith("ses") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    /** Where [phrase] occurs in [guess] as an in-order run allowing short gaps (filler words don't count), or null. */
    fun find(guess: List<String>, phrase: List<String>): IntRange? {
        val core = phrase.filter { it !in FILLER }.ifEmpty { phrase }
        if (core.isEmpty() || guess.isEmpty()) return null
        val spots = guess.indices.filter { guess[it] !in FILLER || core.size == phrase.size && guess[it] in core }
        for (s in spots.indices) {
            if (guess[spots[s]] != core[0]) continue
            var at = s; var ok = true
            for (k in 1 until core.size) {
                val next = (at + 1..minOf(at + 3, spots.size - 1)).firstOrNull { guess[spots[it]] == core[k] }
                if (next == null) { ok = false; break }
                at = next
            }
            if (ok) return spots[s]..spots[at]
        }
        return null
    }

    private fun negated(guess: List<String>, at: Int) = (maxOf(0, at - 3) until at).any { guess[it] in NEGATORS }

    fun judge(entry: MeaningEntry, guess: String): MeaningVerdict {
        val g = tokens(guess)
        if (g.isEmpty()) return MeaningVerdict.WRONG
        // The word itself (or a guess that only repeats it) explains nothing.
        val raw = guess.lowercase().replace(Regex("[^a-z ]"), " ").split(' ').filter { it.isNotBlank() }
        val self = entry.word.lowercase()
        if (raw.all { it.startsWith(self) || it in FILLER }) return MeaningVerdict.WRONG
        var right = 0; var close = 0; var wrong = 0
        val covered = mutableListOf<IntRange>()
        // Right phrases first, so a mix-up word inside one ("mean" in "well meaning") doesn't count against it.
        fun scan(phrases: List<String>, onPlain: () -> Unit, onNegated: () -> Unit, claim: Boolean) = phrases.forEach { p ->
            val words = tokens(p)
            val span = find(g, words) ?: return@forEach
            if (!claim && covered.any { span.first >= it.first && span.last <= it.last }) return@forEach
            if (negated(g, span.first) && words.none { it in NEGATORS }) onNegated() else onPlain()
            if (claim) covered += span
        }
        scan(entry.right, { right++ }, { wrong++ }, claim = true)
        scan(entry.close, { close++ }, { wrong++ }, claim = false)
        scan(entry.wrong, { wrong++ }, { close++ }, claim = false)
        return when {
            right > 0 && wrong == 0 -> MeaningVerdict.RIGHT
            right > 0 || (close > 0 && wrong == 0) -> MeaningVerdict.CLOSE
            wrong > 0 -> MeaningVerdict.WRONG
            else -> MeaningVerdict.UNSURE
        }
    }
}

/**
 * A round of Word Meaning: [entries] are the words, [answers] what the player typed for each,
 * [verdicts] the judge's call (or the player's own, when [selfJudged]). [tries] counts guesses on the
 * current word: a close answer gets one more try.
 */
data class MeaningGame(
    val level: LogicLevel,
    val seed: Long,
    val words: List<String>,
    val index: Int = 0,
    val answers: List<String> = emptyList(),
    val verdicts: List<MeaningVerdict> = emptyList(),
    val selfJudged: Set<Int> = emptySet(),
    val tries: Int = 0,
    val hints: Set<Int> = emptySet(),
) {
    init {
        require(words.isNotEmpty() && words.all { MeaningBank.entry(it) != null })
        require(answers.size == verdicts.size && answers.size <= words.size && index in 0..words.size)
        require(selfJudged.all { it < verdicts.size } && hints.all { it < words.size } && tries in 0..1)
    }

    val entry: MeaningEntry? get() = words.getOrNull(index)?.let(MeaningBank::entry)
    val complete: Boolean get() = index >= words.size
    val score: Int get() = verdicts.withIndex().sumOf { (i, v) -> when (v) { MeaningVerdict.RIGHT -> if (i in hints) 1 else 2; MeaningVerdict.CLOSE -> 1; else -> 0 }.toInt() }
    val best: Int get() = words.size * 2

    /** Judges [guess] for the current word. A close answer on the first try asks for another go. */
    fun answer(guess: String, check: (MeaningEntry, String) -> MeaningVerdict = MeaningJudge::judge): Pair<MeaningGame, MeaningVerdict> {
        val e = entry ?: return this to MeaningVerdict.UNSURE
        if (guess.isBlank()) return this to MeaningVerdict.UNSURE
        val v = check(e, guess).let { if (it == MeaningVerdict.UNSURE) MeaningVerdict.WRONG else it }
        if (v == MeaningVerdict.CLOSE && tries == 0) return copy(tries = 1) to v
        return record(guess.trim(), v) to v
    }

    private fun record(guess: String, v: MeaningVerdict) = copy(answers = answers + guess, verdicts = verdicts + v, tries = 0)

    /** Moves to the next word after the verdict has been shown. */
    fun next(): MeaningGame = if (answers.size > index) copy(index = index + 1) else this

    /** The player says their last answer was right after all. */
    fun claimRight(): MeaningGame? {
        val last = verdicts.lastIndex
        if (last < 0 || last != index || verdicts[last] == MeaningVerdict.RIGHT) return null
        return copy(verdicts = verdicts.toMutableList().also { it[last] = MeaningVerdict.RIGHT }, selfJudged = selfJudged + last)
    }

    /** Gives up on the current word, showing its meaning. */
    fun reveal(): MeaningGame? = if (complete || answers.size > index) null else record("", MeaningVerdict.WRONG)

    /** Shows one right phrase as a nudge; a hinted word scores 1 instead of 2. */
    fun hint(): MeaningGame? = if (complete || index in hints || answers.size > index) null else copy(hints = hints + index)

    companion object {
        const val ROUND = 8

        fun generate(seed: Long, level: LogicLevel): MeaningGame {
            val random = Random(seed)
            val pool = MeaningBank.entries.filter { it.level == level.ordinal }.ifEmpty { MeaningBank.entries }
            return MeaningGame(level, seed, pool.shuffled(random).take(ROUND).map { it.word })
        }
    }
}

object MeaningCodec {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("|", "\\p")
    private fun unesc(s: String) = s.replace("\\p", "|").replace("\\n", "\n").replace("\\\\", "\\")

    fun encode(g: MeaningGame) = listOf("1", g.level.name, g.seed.toString(), g.words.joinToString(","), g.index.toString(),
        g.answers.joinToString("|", transform = ::esc), g.verdicts.joinToString(",") { it.name },
        g.selfJudged.sorted().joinToString(","), g.tries.toString(), g.hints.sorted().joinToString(",")).joinToString("\n")

    fun decode(text: String): MeaningGame? = try {
        val l = text.split('\n'); require(l.size == 10 && l[0] == "1")
        fun ints(s: String) = if (s.isEmpty()) emptySet() else s.split(',').map { it.toInt() }.toSet()
        val verdicts = if (l[6].isEmpty()) emptyList() else l[6].split(',').map { MeaningVerdict.valueOf(it) }
        val answers = if (verdicts.isEmpty()) emptyList() else l[5].split("|").map(::unesc)
        MeaningGame(LogicLevel.valueOf(l[1]), l[2].toLong(), l[3].split(','), l[4].toInt(), answers, verdicts, ints(l[7]), l[8].toInt(), ints(l[9]))
    } catch (_: IllegalArgumentException) { null }
}
