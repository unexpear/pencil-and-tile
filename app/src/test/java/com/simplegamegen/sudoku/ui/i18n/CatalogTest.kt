package com.simplegamegen.sudoku.ui.i18n

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CatalogTest {
    private val de = Catalog(Catalog.parse("""
        # comment
        Start	Los
        Easy	Leicht
        Hard	Schwer
        {0} of {1} solved	{0} von {1} gelöst
        New to {0}? Try the guided tutorial	Neu bei {0}? Probier das Tutorial
        {0} hints	{0} Tipps
        {0} hint	{0} Tipp
        Step {0} of {1}	Schritt {0} von {1}
        @result:Draw	Unentschieden
        Draw line	Linie ziehen
        First line\nsecond	Erste Zeile\nzweite
    """.trimIndent()))

    private val ja = Catalog(Catalog.parse("""
        @sep:, 	、
        Row {0}	{0}行目
        column {0}	{0}列目
        Row {0}, column {1}	{0}行{1}列
        shaded	黒
        Easy	かんたん
        {0} of {1} solved	{1}問中{0}問正解
        Step {0} of {1}	ステップ {0}/{1}
    """.trimIndent()))

    @Test fun `exact keys and untouched text`() {
        assertEquals("Los", de.translate("Start"))
        assertEquals("Unknown words", de.translate("Unknown words"))
        assertEquals("12:04", de.translate("12:04"))
    }

    @Test fun `placeholders can be reordered and their values translated`() {
        assertEquals("3 von 8 gelöst", de.translate("3 of 8 solved"))
        assertEquals("8問中3問正解", ja.translate("3 of 8 solved"))
        assertEquals("ステップ 2/7", ja.translate("Step 2 of 7"))
        assertEquals("Neu bei Leicht? Probier das Tutorial", de.translate("New to Easy? Try the guided tutorial"))
    }

    @Test fun `singular and plural are separate keys`() {
        assertEquals("1 Tipp", de.translate("1 hint"))
        assertEquals("4 Tipps", de.translate("4 hints"))
    }

    @Test fun `joined parts are translated one by one`() {
        assertEquals("Leicht · 3 von 8 gelöst", de.translate("Easy · 3 of 8 solved"))
        assertEquals("Schwer\nLeicht", de.translate("Hard\nEasy"))
        assertEquals("Erste Zeile\nzweite", de.translate("First line\nsecond"))
    }

    @Test fun `a placeholder never swallows a joined part`() {
        assertEquals("3行4列", ja.translate("Row 3, column 4"))
        assertEquals("3行目、4列目、黒", ja.translate("Row 3, column 4, shaded"))
        assertEquals("Row 3, column 4, shaded", null.tr("Row 3, column 4, shaded"))
    }

    @Test fun `context keys tell apart words spelled the same`() {
        assertEquals("Unentschieden", de.translate("@result:Draw"))
        assertEquals("Draw", Catalog.stripContext("@result:Draw"))
        assertEquals("Draw", ja.translate("@result:Draw"), "missing translations fall back to the English word")
        assertEquals("Draw", null.tr("@result:Draw"))
    }
}
