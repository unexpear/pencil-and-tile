package com.simplegamegen.sudoku.ui

import com.simplegamegen.sudoku.tabletop.CollectionGuide
import com.simplegamegen.sudoku.ui.theme.GAME_COUNT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClassicsCatalogTest {
    @Test fun `mastermind is the second classics game and the guide totals include it`() {
        val classics = GameId.entries.filter { it.group == GameGroup.CLASSICS && it.parent == null }
        assertEquals(listOf(GameId.CONNECT_FOUR, GameId.MASTERMIND), classics)
        assertEquals("play_MASTERMIND", GameId.MASTERMIND.route)
        assertEquals("PLAY_MASTERMIND", "PLAY_${GameId.MASTERMIND.name}")
        assertEquals(GameId.entries.size, GAME_COUNT)
        assertEquals(39, CollectionGuide.entries.size)
        assertEquals(49, CollectionGuide.entries.sumOf { it.variants })
        assertEquals(343, CollectionGuide.entries.sumOf { it.setups })
        assertEquals(1, CollectionGuide.entries.count { it.title == "Mastermind" })
    }
}