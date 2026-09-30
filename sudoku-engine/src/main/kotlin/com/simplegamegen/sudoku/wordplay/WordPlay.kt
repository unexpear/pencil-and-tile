package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.words.OpenContent
import com.simplegamegen.sudoku.words.WordPuzzles
import kotlin.random.Random

private val ALPHABET = ('A'..'Z').joinToString("")

// ---------------- Cryptogram ----------------

/**
 * A substitution-cipher quote. [cipher] maps plain letter index to its cipher letter;
 * [guesses] holds the player's plain letter for each cipher letter, or '_'.
 */
data class Cryptogram(
    val quote: String,
    val author: String,
    val level: LogicLevel,
    val seed: Long,
    val cipher: String,
    val guesses: String = "_".repeat(26),
    val given: String = "",
    val hints: Int = 0,
) {
    init {
        require(quote.isNotBlank() && quote.length <= 200 && author.isNotBlank() && '\n' !in quote + author)
        require(cipher.length == 26 && cipher.toSet() == ALPHABET.toSet() && cipher.indices.none { cipher[it] == 'A' + it })
        require(guesses.length == 26 && guesses.all { it == '_' || it in 'A'..'Z' } && hints >= 0)
        require(given.all { it in 'A'..'Z' } && given.toSet().size == given.length)
        given.forEach { require(guesses[it - 'A'] == plainOf(it)) }
        // A plain letter may be guessed for only one cipher letter.
        require(guesses.filter { it != '_' }.let { it.toSet().size == it.length })
    }

    val encoded: String get() = quote.uppercase().map { if (it in 'A'..'Z') cipher[it - 'A'] else it }.joinToString("")
    /** Cipher letters that appear in the puzzle. */
    val letters: Set<Char> get() = encoded.filter { it in 'A'..'Z' }.toSet()
    fun plainOf(cipherLetter: Char): Char = 'A' + cipher.indexOf(cipherLetter)
    fun guessOf(cipherLetter: Char): Char? = guesses[cipherLetter - 'A'].takeIf { it != '_' }
    val complete: Boolean get() = letters.all { guessOf(it) == plainOf(it) }
    fun mistakes(): Set<Char> = letters.filter { c -> guessOf(c)?.let { it != plainOf(c) } == true }.toSet()

    /** Assigns [plain] to [cipherLetter] (null clears). A plain letter moves off any other cipher letter. */
    fun guess(cipherLetter: Char, plain: Char?): Cryptogram {
        if (complete || cipherLetter !in letters || cipherLetter in given) return this
        if (plain != null && (plain !in 'A'..'Z' || given.any { plainOf(it) == plain })) return this
        val next = guesses.toCharArray()
        if (plain != null) next.indices.filter { next[it] == plain }.forEach { next[it] = '_' }
        next[cipherLetter - 'A'] = plain ?: '_'
        return copy(guesses = String(next))
    }

    /** Reveals [preferred] if unsolved, otherwise the first unsolved letter in reading order. */
    fun hint(preferred: Char?): Cryptogram? {
        if (complete) return null
        val target = preferred?.takeIf { it in letters && guessOf(it) != plainOf(it) }
            ?: encoded.firstOrNull { it in 'A'..'Z' && guessOf(it) != plainOf(it) } ?: return null
        return reveal(target).copy(hints = hints + 1)
    }

    private fun reveal(c: Char): Cryptogram {
        val plain = plainOf(c)
        val next = guesses.toCharArray()
        next.indices.filter { next[it] == plain }.forEach { next[it] = '_' }
        next[c - 'A'] = plain
        return copy(guesses = String(next), given = given + c)
    }

    companion object {
        fun generate(seed: Long, level: LogicLevel): Cryptogram {
            val random = Random(seed)
            val range = when (level) { LogicLevel.EASY -> 12..36; LogicLevel.MEDIUM -> 25..55; LogicLevel.HARD -> 40..80; LogicLevel.EXPERT -> 60..200 }
            val pool = Quotes.forLevel(level, range)
            val (quote, author) = pool.ifEmpty { Quotes.all }.random(random)
            var cipher: String
            do cipher = ALPHABET.toList().shuffled(random).joinToString("") while (cipher.indices.any { cipher[it] == 'A' + it })
            var puzzle = Cryptogram(quote, author, level, seed, cipher)
            val reveals = listOf(3, 1, 0, 0)[level.ordinal]
            puzzle.letters.shuffled(random).take(reveals).forEach { puzzle = puzzle.reveal(it) }
            return puzzle
        }
    }
}

object CryptogramCodec {
    fun encode(c: Cryptogram) = listOf("1", c.quote, c.author, c.level.name, c.seed.toString(), c.cipher, c.guesses, c.given, c.hints.toString()).joinToString("\n")
    fun decode(text: String): Cryptogram? = try {
        val l = text.split('\n'); require(l.size == 9 && l[0] == "1")
        Cryptogram(l[1], l[2], LogicLevel.valueOf(l[3]), l[4].toLong(), l[5], l[6], l[7], l[8].toInt())
    } catch (_: IllegalArgumentException) { null }
}

// ---------------- Word scramble ----------------

enum class ScrambleStatus { OPEN, SOLVED, REVEALED, SKIPPED }

/** A round of jumbled words. [typed] is the current answer; its first [revealed] letters came from hints. */
data class ScrambleGame(
    val level: LogicLevel,
    val seed: Long,
    val words: List<String>,
    val jumbles: List<String>,
    val status: List<ScrambleStatus> = words.map { ScrambleStatus.OPEN },
    val index: Int = 0,
    val typed: String = "",
    val revealed: List<Int> = words.map { 0 },
    val hints: Int = 0,
    val shuffles: Int = 0,
) {
    init {
        require(words.isNotEmpty() && words.size == jumbles.size && status.size == words.size && revealed.size == words.size)
        words.indices.forEach { i ->
            require(words[i].all { it in 'A'..'Z' } && jumbles[i].toList().sorted() == words[i].toList().sorted())
            require(revealed[i] in 0..words[i].length)
        }
        require(index in words.indices && hints >= 0 && shuffles >= 0)
        require(typed.length <= words[index].length)
        if (status[index] == ScrambleStatus.OPEN) require(typed.startsWith(words[index].take(revealed[index])))
        require(typed.groupingBy { it }.eachCount().all { (ch, n) -> n <= jumbles[index].count { it == ch } })
    }

    val word: String get() = words[index]
    val jumble: String get() = jumbles[index]
    val complete: Boolean get() = status.none { it == ScrambleStatus.OPEN }
    val solved: Int get() = status.count { it == ScrambleStatus.SOLVED }

    /** Jumble positions already used by [typed], matched left to right. */
    fun usedPositions(): Set<Int> {
        val used = mutableSetOf<Int>()
        typed.forEach { ch -> jumble.indices.firstOrNull { it !in used && jumble[it] == ch }?.let { used += it } }
        return used
    }

    fun type(position: Int): ScrambleGame {
        if (complete || status[index] != ScrambleStatus.OPEN || position !in jumble.indices || position in usedPositions()) return this
        return copy(typed = typed + jumble[position])
    }

    fun backspace(): ScrambleGame = if (typed.length > revealed[index]) copy(typed = typed.dropLast(1)) else this
    fun clear(): ScrambleGame = copy(typed = word.take(revealed[index]))

    /** Checks a full answer: the intended word or any real word with the same letters counts. */
    fun submit(): Pair<ScrambleGame, Boolean> {
        if (complete || typed.length != word.length) return this to false
        val ok = typed == word || Lexicon.isWord(typed)
        return (if (ok) mark(ScrambleStatus.SOLVED) else this) to ok
    }

    fun skip(): ScrambleGame = if (complete) this else mark(ScrambleStatus.SKIPPED)

    fun hint(): ScrambleGame {
        if (complete) return this
        val shown = revealed[index] + 1
        val next = copy(revealed = revealed.toMutableList().also { it[index] = shown }, typed = word.take(shown), hints = hints + 1)
        return if (shown == word.length) next.mark(ScrambleStatus.REVEALED) else next
    }

    fun shuffle(): ScrambleGame {
        if (complete) return this
        val jumbled = jumbleOf(word, Random(seed * 31 + shuffles + 1))
        return copy(jumbles = jumbles.toMutableList().also { it[index] = jumbled }, typed = word.take(revealed[index]), shuffles = shuffles + 1)
    }

    private fun mark(s: ScrambleStatus): ScrambleGame {
        val nextStatus = status.toMutableList().also { it[index] = s }
        val nextIndex = (1..words.size).map { (index + it) % words.size }.firstOrNull { nextStatus[it] == ScrambleStatus.OPEN } ?: index
        return copy(status = nextStatus, index = nextIndex, typed = words[nextIndex].take(revealed[nextIndex]).takeIf { nextStatus[nextIndex] == ScrambleStatus.OPEN } ?: "")
    }

    companion object {
        const val ROUND = 8
        fun lengths(level: LogicLevel) = listOf(4..5, 5..6, 6..8, 7..10)[level.ordinal]

        fun jumbleOf(word: String, random: Random): String {
            repeat(50) {
                val j = word.toList().shuffled(random).joinToString("")
                if (j != word && !Lexicon.isWord(j)) return j
            }
            return word.reversed().takeIf { it != word } ?: word.drop(1) + word.first()
        }

        fun generate(seed: Long, level: LogicLevel): ScrambleGame {
            val random = Random(seed)
            val pool = Lexicon.common.filter { it.length in lengths(level) && it.toSet().size > 1 }.sorted()
            val words = pool.shuffled(random).take(ROUND)
            return ScrambleGame(level, seed, words, words.map { jumbleOf(it, random) })
        }
    }
}

object ScrambleCodec {
    fun encode(g: ScrambleGame) = listOf("1", g.level.name, g.seed.toString(), g.words.joinToString(","), g.jumbles.joinToString(","),
        g.status.joinToString(",") { it.name }, g.index.toString(), g.typed, g.revealed.joinToString(","), g.hints.toString(), g.shuffles.toString()).joinToString("\n")
    fun decode(text: String): ScrambleGame? = try {
        val l = text.split('\n'); require(l.size == 11 && l[0] == "1")
        ScrambleGame(LogicLevel.valueOf(l[1]), l[2].toLong(), l[3].split(','), l[4].split(','), l[5].split(',').map { ScrambleStatus.valueOf(it) },
            l[6].toInt(), l[7], l[8].split(',').map(String::toInt), l[9].toInt(), l[10].toInt())
    } catch (_: IllegalArgumentException) { null }
}

// ---------------- Acrostic ----------------

data class AcrosticEntry(val answer: String, val clue: String)

/** Clue answers whose first letters, read down, spell [keyword]. [typed] uses '_' for empty squares. */
data class AcrosticGame(
    val level: LogicLevel,
    val seed: Long,
    val keyword: String,
    val entries: List<AcrosticEntry>,
    val typed: List<String> = entries.map { "_".repeat(it.answer.length) },
    val hints: Int = 0,
) {
    init {
        require(keyword.length in 3..10 && keyword.all { it in 'A'..'Z' } && entries.size == keyword.length && typed.size == entries.size)
        entries.forEachIndexed { i, e ->
            require(e.answer.length in 2..12 && e.answer.all { it in 'A'..'Z' } && e.answer[0] == keyword[i])
            require(e.clue.isNotBlank() && '\n' !in e.clue && '|' !in e.clue)
            require(typed[i].length == e.answer.length && typed[i].all { it == '_' || it in 'A'..'Z' })
        }
        require(entries.map { it.answer }.toSet().size == entries.size && hints >= 0)
    }

    val complete: Boolean get() = entries.indices.all { typed[it] == entries[it].answer }
    fun solved(i: Int): Boolean = typed[i] == entries[i].answer
    fun mistakes(): Set<Int> = entries.indices.filter { i -> typed[i].indices.any { typed[i][it] != '_' && typed[i][it] != entries[i].answer[it] } }.toSet()

    fun answer(i: Int, text: String): AcrosticGame {
        if (complete || i !in entries.indices) return this
        val letters = text.uppercase().filter { it in 'A'..'Z' }.take(entries[i].answer.length)
        return copy(typed = typed.toMutableList().also { it[i] = letters.padEnd(entries[i].answer.length, '_') })
    }

    fun hint(preferred: Int?): AcrosticGame? {
        if (complete) return null
        val i = preferred?.takeIf { it in entries.indices && !solved(it) } ?: entries.indices.first { !solved(it) }
        val pos = typed[i].indices.first { typed[i][it] != entries[i].answer[it] }
        val row = typed[i].toCharArray().also { it[pos] = entries[i].answer[pos] }
        return copy(typed = typed.toMutableList().also { it[i] = String(row) }, hints = hints + 1)
    }

    companion object {
        fun answerLengths(level: LogicLevel) = listOf(3..5, 4..6, 5..7, 6..10)[level.ordinal]
        fun keywordLengths(level: LogicLevel) = listOf(4..5, 5..6, 6..7, 7..8)[level.ordinal]

        fun generate(seed: Long, level: LogicLevel): AcrosticGame {
            val random = Random(seed)
            val hand = WordPuzzles.clues.map { (a, c) -> AcrosticEntry(a.uppercase(), c) }
            val handWords = hand.map { it.answer }.toSet()
            val clues = (hand + OpenContent.clues
                .filter { (answer, _) -> answer !in handWords }
                .map { (answer, clue) -> AcrosticEntry(answer, clue) })
                .filter { it.answer.length in answerLengths(level) }
                .shuffled(random)
            val byLetter = clues.groupBy { it.answer[0] }
            val keywords = Lexicon.common.filter { it.length in keywordLengths(level) }.sorted().shuffled(random)
            for (keyword in keywords) {
                val used = mutableSetOf<String>()
                val picked = keyword.map { ch -> byLetter[ch]?.firstOrNull { it.answer !in used && it.answer != keyword }?.also { used += it.answer } }
                if (picked.none { it == null }) return AcrosticGame(level, seed, keyword, picked.filterNotNull())
            }
            error("No acrostic keyword fits the clue bank")
        }
    }
}

object AcrosticCodec {
    fun encode(g: AcrosticGame) = (listOf("1", g.level.name, g.seed.toString(), g.keyword, g.typed.joinToString(","), g.hints.toString()) +
        g.entries.map { "${it.answer}|${it.clue}" }).joinToString("\n")
    fun decode(text: String): AcrosticGame? = try {
        val l = text.split('\n'); require(l.size >= 9 && l[0] == "1")
        val entries = l.drop(6).map { row -> val f = row.split('|'); require(f.size == 2); AcrosticEntry(f[0], f[1]) }
        AcrosticGame(LogicLevel.valueOf(l[1]), l[2].toLong(), l[3], entries, l[4].split(','), l[5].toInt())
    } catch (_: IllegalArgumentException) { null }
}
