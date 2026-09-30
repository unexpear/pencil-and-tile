package com.simplegamegen.sudoku.wordplay

import java.io.Closeable
import java.util.Locale

/**
 * One dictionary sense from the generated English lexicon.
 *
 * Definitions and [exampleSource] `oewn` come from Open English WordNet (CC BY 4.0, from Princeton
 * WordNet). [exampleSource] `tatoeba_cc0` is a short public-domain sentence used only when that sense
 * has no WordNet example; it contains the lemma, and it is not pinned to this sense the way Word
 * Meaning's reviewed clues are. Extra Word Meaning words use a WordNet example, or a Tatoeba
 * sentence only when the word has one meaning.
 */
data class LexiconSense(
    val pos: String,
    val definition: String,
    val example: String? = null,
    val exampleSource: String? = null,
)

/**
 * Read-only lookup over the English lexicon database.
 * [lookup] is empty when the lemma is absent or has no senses. A playable word with no definition
 * still returns an empty list; [playable] is ENABLE membership in the shipped letter-game list.
 */
interface EnglishLexicon : Closeable {
    fun lookup(lemma: String): List<LexiconSense>
    fun playable(lemma: String): Boolean
    override fun close() {}
}

/** A thin query surface so Android SQLite and tests can share the lookup SQL. */
interface LexiconQuery : Closeable {
    fun query(sql: String, vararg args: String): List<Map<String, String?>>
}

/**
 * Casefolded lemma key shared with the database: lower case, underscores as spaces.
 * Letter games pass the upper-case form from [Lexicon]; both hit the same row.
 */
internal fun lexiconLemma(raw: String): String =
    raw.trim().replace('_', ' ').replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

class SqlEnglishLexicon(private val db: LexiconQuery) : EnglishLexicon {
    override fun lookup(lemma: String): List<LexiconSense> {
        val key = lexiconLemma(lemma)
        if (key.isEmpty() || key.none { it.isLetter() }) return emptyList()
        return db.query(
            """
            SELECT s.pos AS pos, s.definition AS definition, e.sentence AS sentence, e.source AS source
            FROM words w
            JOIN senses s ON s.word_id = w.id
            LEFT JOIN examples e ON e.sense_id = s.id
            WHERE w.lemma = ?
            ORDER BY s.id
            """.trimIndent(),
            key,
        ).mapNotNull { row ->
            val definition = row["definition"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val example = row["sentence"]?.takeIf { it.isNotBlank() }
            LexiconSense(
                pos = row["pos"].orEmpty(),
                definition = definition,
                example = example,
                exampleSource = if (example == null) null else row["source"]?.takeIf { it.isNotBlank() },
            )
        }
    }

    override fun playable(lemma: String): Boolean {
        val key = lexiconLemma(lemma)
        if (key.isEmpty() || key.none { it.isLetter() }) return false
        val flag = db.query("SELECT playable AS playable FROM words WHERE lemma = ?", key)
            .firstOrNull()?.get("playable")
        return flag == "1"
    }

    override fun close() = db.close()
}
