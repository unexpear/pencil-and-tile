package com.simplegamegen.sudoku.data

import com.simplegamegen.sudoku.model.ArrowShaft
import com.simplegamegen.sudoku.model.Cage
import com.simplegamegen.sudoku.model.KropkiDot
import com.simplegamegen.sudoku.model.SandwichClues

/**
 * Hand-rolled save codec (no extra serialization dependency). Every parser
 * returns null on corrupt input so a bad save is dropped, never crashed on.
 */
object SaveCodec {

    fun intsToCsv(values: Collection<Int>): String = values.joinToString(",")

    fun csvToInts(raw: String?, expected: Int): List<Int>? {
        if (raw == null) return null
        val parts = raw.split(",")
        if (parts.size != expected) return null
        val out = parts.map { it.toIntOrNull() ?: return null }
        return out
    }

    fun notesToString(notes: List<Set<Int>>): String = notes
        .mapIndexedNotNull { i, set -> if (set.isEmpty()) null else "$i:${set.sorted().joinToString(",")}" }
        .joinToString(";")

    fun stringToNotes(raw: String?, cells: Int, maxDigit: Int): List<Set<Int>>? {
        val out = List(cells) { emptySet<Int>() }.toMutableList()
        if (raw.isNullOrEmpty()) return out
        for (chunk in raw.split(";")) {
            val kv = chunk.split(":")
            if (kv.size != 2) return null
            val idx = kv[0].toIntOrNull() ?: return null
            if (idx !in 0 until cells) return null
            val vals = kv[1].split(",").map { it.toIntOrNull() ?: return null }
            if (vals.any { it !in 1..maxDigit }) return null
            out[idx] = vals.toSet()
        }
        return out
    }

    fun cagesToString(cages: List<Cage>): String =
        cages.joinToString("|") { "${it.cells.joinToString(",")}:${it.sum}" }

    fun stringToCages(raw: String?): List<Cage>? {
        if (raw == null) return null
        if (raw.isEmpty()) return emptyList()
        return raw.split("|").map { chunk ->
            val kv = chunk.split(":")
            if (kv.size != 2) return null
            val cells = kv[0].split(",").map { it.toIntOrNull() ?: return null }.toIntArray()
            Cage(cells, kv[1].toIntOrNull() ?: return null)
        }
    }

    fun thermosToString(thermos: List<List<Int>>): String =
        thermos.joinToString(";") { it.joinToString(",") }

    fun stringToThermos(raw: String?): List<List<Int>>? {
        if (raw == null) return null
        if (raw.isEmpty()) return emptyList()
        return raw.split(";").map { chunk ->
            chunk.split(",").map { it.toIntOrNull() ?: return null }
        }
    }

    fun dotsToString(dots: List<KropkiDot>): String =
        dots.joinToString(";") { "${it.a},${it.b},${if (it.black) 1 else 0}" }

    fun stringToDots(raw: String?): List<KropkiDot>? {
        if (raw == null) return null
        if (raw.isEmpty()) return emptyList()
        return raw.split(";").map { chunk ->
            val p = chunk.split(",")
            if (p.size != 3) return null
            KropkiDot(
                p[0].toIntOrNull() ?: return null,
                p[1].toIntOrNull() ?: return null,
                (p[2].toIntOrNull() ?: return null) == 1,
            )
        }
    }

    fun arrowsToString(arrows: List<ArrowShaft>): String =
        arrows.joinToString(";") { "${it.circle}:${it.shaft.joinToString(",")}" }

    fun stringToArrows(raw: String?): List<ArrowShaft>? {
        if (raw == null) return null
        if (raw.isEmpty()) return emptyList()
        return raw.split(";").map { chunk ->
            val kv = chunk.split(":")
            if (kv.size != 2) return null
            ArrowShaft(
                kv[0].toIntOrNull() ?: return null,
                kv[1].split(",").map { it.toIntOrNull() ?: return null },
            )
        }
    }

    fun sandwichSideToString(side: IntArray): String = side.joinToString(",")

    fun stringToSandwichSide(raw: String?, size: Int): IntArray? =
        csvToInts(raw, size)?.toIntArray()

    fun sandwichToStrings(clues: SandwichClues): List<String> = listOf(
        sandwichSideToString(clues.top),
        sandwichSideToString(clues.bottom),
        sandwichSideToString(clues.left),
        sandwichSideToString(clues.right),
    )
}
