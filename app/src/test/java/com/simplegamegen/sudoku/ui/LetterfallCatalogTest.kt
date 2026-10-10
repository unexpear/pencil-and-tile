package com.simplegamegen.sudoku.ui

import com.simplegamegen.sudoku.tabletop.CollectionGuide
import com.simplegamegen.sudoku.ui.theme.BuiltInThemes
import com.simplegamegen.sudoku.ui.theme.GAME_COUNT
import com.simplegamegen.sudoku.ui.tutorial.Tutorials
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LetterfallCatalogTest {
    @Test fun `theme colors stay aligned with every GameId`() {
        assertEquals(GameId.entries.size, GAME_COUNT)
        assertEquals(GameGroup.WORDS, GameId.LETTERFALL.group)
        BuiltInThemes.all.forEach { spec ->
            assertEquals(GameId.entries.size, spec.gameColors.size, spec.id)
        }
    }

    @Test fun `the collection guide counts Letterfall with the other games`() {
        assertEquals(67, CollectionGuide.entries.size)
        assertEquals(78, CollectionGuide.entries.sumOf { it.variants })
        assertEquals(531, CollectionGuide.entries.sumOf { it.setups })
        val entry = CollectionGuide.entries.single { it.title == GameId.LETTERFALL.title }
        assertEquals(1, entry.variants)
        assertEquals(4, entry.setups)
        assertEquals(GameId.LETTERFALL, Tutorials.of(GameId.LETTERFALL).game)
        assertTrue(Tutorials.of(GameId.LETTERFALL).steps.any { it.interactive })
    }
}
