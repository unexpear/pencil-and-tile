package com.simplegamegen.sudoku.ui

import com.simplegamegen.sudoku.tabletop.CollectionGuide
import com.simplegamegen.sudoku.ui.theme.GAME_COUNT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClassicsCatalogTest {
    @Test fun `battleship is the third classics game and the guide totals include it`() {
        val classics = GameId.entries.filter { it.group == GameGroup.CLASSICS && it.parent == null }
        assertEquals(listOf(GameId.CONNECT_FOUR, GameId.MASTERMIND, GameId.BATTLESHIP, GameId.MANCALA, GameId.FIVE_ROW, GameId.YACHT, GameId.SHUT_BOX, GameId.TEN_THOUSAND, GameId.SHIP_CREW, GameId.HEX, GameId.RPS, GameId.TIC_TAC_TOE), classics)
        assertEquals("play_BATTLESHIP", GameId.BATTLESHIP.route)
        assertEquals("PLAY_BATTLESHIP", "PLAY_${GameId.BATTLESHIP.name}")
        assertEquals("play_RPS", GameId.RPS.route)
        assertEquals("PLAY_RPS", "PLAY_${GameId.RPS.name}")
        assertEquals("play_TIC_TAC_TOE", GameId.TIC_TAC_TOE.route)
        assertEquals("PLAY_TIC_TAC_TOE", "PLAY_${GameId.TIC_TAC_TOE.name}")
        assertEquals(GameId.entries.size, GAME_COUNT)
        assertEquals(65, CollectionGuide.entries.size)
        assertEquals(75, CollectionGuide.entries.sumOf { it.variants })
        assertEquals(474, CollectionGuide.entries.sumOf { it.setups })
        assertEquals(1, CollectionGuide.entries.count { it.title == "Battleship" })
        val entry = CollectionGuide.entries.single { it.title == "Battleship" }
        assertEquals(1, entry.variants)
        assertEquals(4, entry.setups)
    }
}