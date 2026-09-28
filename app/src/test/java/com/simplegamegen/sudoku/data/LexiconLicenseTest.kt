package com.simplegamegen.sudoku.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The lexicon credits are files the app shows; this does not need a device. */
class LexiconLicenseTest {
    @Test fun `license files and the credits screen name the lexicon sources`() {
        val licenses = File("src/main/assets/licenses")
        assertTrue(licenses.isDirectory, licenses.absolutePath)
        val enable = File(licenses, "enable.txt").readText()
        val tatoeba = File(licenses, "tatoeba-cc0.txt").readText()
        val wordnet = File(licenses, "open-english-wordnet.txt").readText()
        assertTrue(enable.contains("public domain") && enable.contains("ENABLE"))
        assertTrue(tatoeba.contains("CC0") && tatoeba.contains("Tatoeba"))
        assertTrue(wordnet.contains("Open English WordNet") && wordnet.contains("lexicon"))
        val credits = File("src/main/java/com/simplegamegen/sudoku/ui/screens/CreditsScreen.kt").readText()
        assertTrue(credits.contains("\"ENABLE\""))
        assertTrue(credits.contains("\"Tatoeba\""))
        assertTrue(credits.contains("licenses/enable.txt"))
        assertTrue(credits.contains("licenses/tatoeba-cc0.txt"))
    }
}
