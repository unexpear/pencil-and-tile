package com.simplegamegen.sudoku.wordplay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.sql.DriverManager

class EnglishLexiconTest {
    @Test fun `known words have a definition`() {
        for (word in listOf("dog", "DOG", "  Dog  ", "run", "Run")) {
            val senses = lexicon.lookup(word)
            assertTrue(senses.isNotEmpty(), word)
            assertTrue(senses.all { it.definition.isNotBlank() }, word)
        }
        val dog = lexicon.lookup("dog")
        assertTrue(dog.any { it.pos == "noun" })
        assertTrue(dog.any { it.example?.contains("barked") == true && it.exampleSource == "oewn" })
        assertTrue(lexicon.lookup("run").any { it.pos == "verb" })
        assertTrue(lexicon.playable("dog") && lexicon.playable("RUN"))
    }

    @Test fun `a missing word is empty and not playable`() {
        assertTrue(lexicon.lookup("qqqqzzzz").isEmpty())
        assertFalse(lexicon.playable("qqqqzzzz"))
        assertTrue(lexicon.lookup("").isEmpty())
        assertTrue(lexicon.lookup("...").isEmpty())
        assertFalse(lexicon.playable("shit"))
        assertTrue(lexicon.lookup("shit").isEmpty())
    }

    @Test fun `playable matches the shipped ENABLE list`() {
        assertEquals(Lexicon.all.size, count("SELECT COUNT(*) AS n FROM words WHERE playable = 1"))
        listOf("DOG", "RUN", "CAT", "PARIS", "KNICKKNACK", "COCKTAIL", "QZX", "SHIT", "BUTTER").forEach { word ->
            assertEquals(Lexicon.isWord(word), lexicon.playable(word), word)
        }
        val all = Lexicon.all
        val random = java.util.Random(1)
        repeat(40) {
            val word = all[random.nextInt(all.size)]
            assertTrue(lexicon.playable(word), word)
        }
        assertTrue(count("SELECT COUNT(*) AS n FROM words WHERE playable = 0") > 1000)
        val bare = query(
            """
            SELECT lemma AS lemma FROM words w
            WHERE playable = 1 AND NOT EXISTS (SELECT 1 FROM senses s WHERE s.word_id = w.id)
            LIMIT 1
            """.trimIndent(),
        ).single()["lemma"]
        assertNotNull(bare)
        assertTrue(lexicon.playable(bare!!))
        assertTrue(lexicon.lookup(bare).isEmpty(), bare)
    }

    @Test fun `blocked suffix collisions still have definitions and are not playable`() {
        for (word in listOf("butter", "assess", "spicy")) {
            assertTrue(lexicon.lookup(word).any { it.definition.isNotBlank() }, word)
            assertFalse(lexicon.playable(word), word)
        }
    }

    @Test fun `phrases and proper names that the word list plays are stored`() {
        assertTrue(lexicon.lookup("ice cream").isNotEmpty())
        assertEquals(lexicon.lookup("ice cream"), lexicon.lookup("ice_cream"))
        assertFalse(lexicon.playable("ice cream"))
        // "Amazon" is capitalised in OEWN and playable in ENABLE, so the two lists share a row.
        assertTrue(lexicon.lookup("Amazon").isNotEmpty())
        assertTrue(lexicon.playable("AMAZON"))
        // The core 2025 edition has no Paris lemma. ENABLE still marks the word playable.
        assertTrue(lexicon.playable("paris"))
        assertTrue(lexicon.lookup("Paris").isEmpty())
    }

    @Test fun `examples are oewn or a tatoeba sentence that contains the lemma`() {
        val sources = query("SELECT DISTINCT source AS source FROM examples").map { it["source"] }.toSet()
        assertEquals(setOf("oewn", "tatoeba_cc0"), sources)
        assertTrue(count("SELECT COUNT(*) AS n FROM examples WHERE source = 'oewn'") > 0)
        val filled = query(
            """
            SELECT w.lemma AS lemma, e.sentence AS sentence
            FROM examples e
            JOIN senses s ON s.id = e.sense_id
            JOIN words w ON w.id = s.word_id
            WHERE e.source = 'tatoeba_cc0'
            """.trimIndent(),
        )
        assertTrue(filled.size > 100, "tatoeba rows ${filled.size}")
        for (row in filled) {
            val lemma = row["lemma"]!!
            val sentence = row["sentence"]!!
            assertTrue(sentenceHasLemma(sentence, lemma), "$lemma :: $sentence")
        }
    }

    @Test fun `the database is the lean schema and holds the word list`() {
        val tables = query("SELECT name AS name FROM sqlite_master WHERE type = 'table'").map { it["name"] }.toSet()
        assertTrue(tables.containsAll(listOf("words", "senses", "examples", "meta")))
        val indexes = query("SELECT name AS name FROM sqlite_master WHERE type = 'index'").map { it["name"] }.toSet()
        assertTrue("idx_words_lemma" in indexes && "idx_senses_word_id" in indexes)
        assertTrue(count("SELECT COUNT(*) AS n FROM words") >= 100_000)
        assertTrue(count("SELECT COUNT(*) AS n FROM senses") >= 50_000)
        assertEquals("1", query("SELECT value AS value FROM meta WHERE key = 'schema'").single()["value"])
    }

    private class JdbcLexiconQuery(path: String) : LexiconQuery {
        private val connection = DriverManager.getConnection("jdbc:sqlite:file:$path?mode=ro")

        override fun query(sql: String, vararg args: String): List<Map<String, String?>> {
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
                statement.executeQuery().use { result ->
                    val labels = Array(result.metaData.columnCount) { result.metaData.getColumnLabel(it + 1) }
                    val rows = mutableListOf<Map<String, String?>>()
                    while (result.next()) {
                        val row = LinkedHashMap<String, String?>(labels.size)
                        labels.forEachIndexed { index, label -> row[label] = result.getString(index + 1) }
                        rows += row
                    }
                    return rows
                }
            }
        }

        override fun close() = connection.close()
    }

    companion object {
        private val db = JdbcLexiconQuery(lexiconPath())
        val lexicon: EnglishLexicon = SqlEnglishLexicon(db)

        fun rows(sql: String): List<Map<String, String?>> = db.query(sql)

        private fun lexiconPath(): String {
            val path = System.getProperty("lexicon.db") ?: error("lexicon.db is not set; run :sudoku-engine:test")
            check(File(path).isFile) { "Missing lexicon database $path" }
            Class.forName("org.sqlite.JDBC")
            return File(path).absolutePath
        }
    }
}

private fun sentenceHasLemma(sentence: String, lemma: String): Boolean {
    val parts = lemma.lowercase().split(" ").filter { it.isNotEmpty() }
    if (parts.isEmpty()) return false
    val pattern = "(?<![A-Za-z])" + parts.joinToString("\\s+") { Regex.escape(it) } + "(?![A-Za-z])"
    return Regex(pattern).containsMatchIn(sentence.lowercase())
}

private fun query(sql: String): List<Map<String, String?>> = EnglishLexiconTest.rows(sql)

private fun count(sql: String) = query(sql).single()["n"]!!.toInt()
