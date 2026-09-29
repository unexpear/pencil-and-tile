package com.simplegamegen.sudoku.ui.words

import com.simplegamegen.sudoku.wordplay.EnglishLexicon
import com.simplegamegen.sudoku.wordplay.Letterfall
import com.simplegamegen.sudoku.wordplay.Lexicon
import com.simplegamegen.sudoku.wordplay.LexiconSense
import com.simplegamegen.sudoku.wordplay.Piece
import com.simplegamegen.sudoku.wordplay.Quilt
import com.simplegamegen.sudoku.wordplay.Sprawl
import com.simplegamegen.sudoku.wordplay.Wordsworn
import java.util.Locale

/** How many senses the card shows. Later senses stay in the database. */
const val WORD_GLANCE_SENSES = 3

/**
 * Dictionary card for one word the letter games already accepted.
 * [senses] is empty when the lexicon has no definition; play is unchanged either way.
 * Membership stays on [Lexicon]. This only reads [EnglishLexicon.lookup].
 */
data class WordGlance(
    val lemma: String,
    val senses: List<LexiconSense>,
    val totalSenses: Int,
    val example: String?,
    val exampleSource: String?,
) {
    val moreSenses: Int get() = (totalSenses - senses.size).coerceAtLeast(0)
}

/** Title case for the card heading. Letter games store the upper-case form. */
fun displayLemma(raw: String): String {
    val text = raw.trim().lowercase(Locale.ROOT)
    return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}

/**
 * Looks [raw] up and keeps the first [limit] senses, plus one example when any sense has one.
 * Blank text is not a word and returns null. An unknown word still returns a glance with no senses.
 */
fun glance(raw: String, senses: List<LexiconSense>, limit: Int = WORD_GLANCE_SENSES): WordGlance? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.none { it.isLetter() }) return null
    val shown = senses.take(limit)
    val sample = shown.firstOrNull { !it.example.isNullOrBlank() } ?: senses.firstOrNull { !it.example.isNullOrBlank() }
    return WordGlance(
        lemma = displayLemma(trimmed),
        senses = shown,
        totalSenses = senses.size,
        example = sample?.example,
        exampleSource = sample?.exampleSource,
    )
}

fun EnglishLexicon.glance(raw: String, limit: Int = WORD_GLANCE_SENSES): WordGlance? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.none { it.isLetter() }) return null
    return glance(raw, lookup(raw), limit)
}

/** The word Sprawl just scored, or null when the path is rejected. */
fun sprawlScoredWord(game: Sprawl, path: List<Int>): String? {
    if (game.problem(path) != null) return null
    return game.wordOf(path)
}

/** The word Letterfall just scored, or null when the path is rejected. */
fun letterfallScoredWord(game: Letterfall, path: List<Int>): String? {
    if (game.problem(path) != null) return null
    return game.wordOf(path)
}

/**
 * Runs that are valid words now and were not this same word before the placement.
 * Order follows the grid: across, then down.
 */
fun quiltWordsJustCompleted(before: Quilt, after: Quilt): List<String> {
    if (before.shape != after.shape || before.width != after.width) return emptyList()
    return after.runs.mapNotNull { run ->
        val word = after.runWord(run) ?: return@mapNotNull null
        if (!Lexicon.isWord(word)) return@mapNotNull null
        if (before.runWord(run) == word) null else word
    }
}

/** The word to show when several runs finish together: the longest, then the first. */
fun primaryGlanceWord(words: List<String>): String? = words.maxWithOrNull(compareBy { it.length })

/**
 * The spell Wordsworn will accept, or null when [Wordsworn.problem] rejects the pieces.
 * Splay changes which card is on top, not whether the spelling is a word.
 */
fun wordswornScoredWord(game: Wordsworn, pieces: List<Piece>): String? {
    if (game.problem(pieces) != null) return null
    val word = game.spell(pieces)
    return word.takeIf { it.any { ch -> ch.isLetter() } }
}
