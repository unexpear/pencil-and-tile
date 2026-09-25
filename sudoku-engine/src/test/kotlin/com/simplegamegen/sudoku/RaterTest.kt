package com.simplegamegen.sudoku

import com.simplegamegen.sudoku.difficulty.Rater
import com.simplegamegen.sudoku.model.Difficulty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RaterTest {

    @Test
    fun `classifies 9x9 clue bands`() {
        assertEquals(Difficulty.EASY, Rater.estimateByClues(9, 48))
        assertEquals(Difficulty.MEDIUM, Rater.estimateByClues(9, 40))
        assertEquals(Difficulty.HARD, Rater.estimateByClues(9, 34))
        assertEquals(Difficulty.EXPERT, Rater.estimateByClues(9, 28))
    }

    @Test
    fun `clamps out-of-band counts`() {
        assertEquals(Difficulty.EASY, Rater.estimateByClues(9, 70))
        assertEquals(Difficulty.EXPERT, Rater.estimateByClues(9, 20))
    }

    @Test
    fun `classifies 6x6 and 4x4`() {
        assertEquals(Difficulty.EASY, Rater.estimateByClues(6, 25))
        assertEquals(Difficulty.EXPERT, Rater.estimateByClues(6, 15))
        assertEquals(Difficulty.EASY, Rater.estimateByClues(4, 11))
        assertEquals(Difficulty.HARD, Rater.estimateByClues(4, 6))
    }
}
