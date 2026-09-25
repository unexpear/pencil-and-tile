package com.simplegamegen.sudoku.data

import com.simplegamegen.sudoku.model.ArrowShaft
import com.simplegamegen.sudoku.model.Cage
import com.simplegamegen.sudoku.model.KropkiDot
import com.simplegamegen.sudoku.model.SandwichClues
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SaveCodecTest {

    @Test
    fun `ints round-trip`() {
        val values = listOf(0, 5, 9, 1, 0, 3)
        assertEquals(values, SaveCodec.csvToInts(SaveCodec.intsToCsv(values), 6))
        assertNull(SaveCodec.csvToInts("1,2,3", 6))
        assertNull(SaveCodec.csvToInts("1,x,3", 3))
        assertNull(SaveCodec.csvToInts(null, 3))
    }

    @Test
    fun `notes round-trip`() {
        val notes = List(9) { emptySet<Int>() }.toMutableList()
        notes[0] = setOf(1, 9)
        notes[8] = setOf(5)
        assertEquals(notes.map { it }, SaveCodec.stringToNotes(SaveCodec.notesToString(notes), 9, 9))
        assertEquals(List(9) { emptySet<Int>() }, SaveCodec.stringToNotes("", 9, 9))
        assertNull(SaveCodec.stringToNotes("0:1,10", 9, 9))
        assertNull(SaveCodec.stringToNotes("9:1", 9, 9))
        assertNull(SaveCodec.stringToNotes("bogus", 9, 9))
    }

    @Test
    fun `cages round-trip`() {
        val cages = listOf(Cage(intArrayOf(0, 1, 9), 12), Cage(intArrayOf(2), 4))
        assertEquals(cages, SaveCodec.stringToCages(SaveCodec.cagesToString(cages)))
        assertNull(SaveCodec.stringToCages("0,1"))
        assertNull(SaveCodec.stringToCages(null))
    }

    @Test
    fun `thermos dots arrows sandwich round-trip`() {
        val thermos = listOf(listOf(0, 1, 2), listOf(10, 19))
        assertEquals(thermos, SaveCodec.stringToThermos(SaveCodec.thermosToString(thermos)))

        val dots = listOf(KropkiDot(0, 1, false), KropkiDot(1, 10, true))
        assertEquals(dots, SaveCodec.stringToDots(SaveCodec.dotsToString(dots)))
        assertNull(SaveCodec.stringToDots("0,1"))

        val arrows = listOf(ArrowShaft(0, listOf(1, 2)), ArrowShaft(40, listOf(41)))
        assertEquals(arrows, SaveCodec.stringToArrows(SaveCodec.arrowsToString(arrows)))

        val clues = SandwichClues(
            intArrayOf(1, 0, 3, 0, 0, 0, 0, 0, 0),
            IntArray(9), IntArray(9), IntArray(9),
        )
        val sides = SaveCodec.sandwichToStrings(clues)
        assertArrayEquals(clues.top, SaveCodec.stringToSandwichSide(sides[0], 9))
        assertNull(SaveCodec.stringToSandwichSide("1,2", 9))
    }
}
