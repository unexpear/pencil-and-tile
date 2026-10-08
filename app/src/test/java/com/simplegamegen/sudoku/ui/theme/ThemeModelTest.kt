package com.simplegamegen.sudoku.ui.theme

import com.simplegamegen.sudoku.ui.GameId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ThemeModelTest {
    private fun custom(overrides: Map<ThemeToken, Long> = mapOf(ThemeToken.TABLE to 0xFF123456, ThemeToken.CARD_BACK to 0xFFABCDEF)) =
        CustomTheme("custom-1", "Night felt", "playful", overrides, cornerRadius = 7, homeLayout = HomeLayout.LIST,
            coloredTiles = false, gameHeaders = true)

    @Test fun `built-in themes define every color in both modes and have unique ids`() {
        assertEquals(BuiltInThemes.all.size, BuiltInThemes.all.map { it.id }.toSet().size)
        BuiltInThemes.all.forEach { spec ->
            ThemeToken.entries.forEach { token -> spec.light[token]; spec.dark[token] }
            assertEquals(GAME_COUNT, spec.gameColors.size)
            assertEquals(GameId.entries.size, spec.gameColors.size, "theme colors follow GameId order")
        }
        assertEquals(GameId.entries.size, GAME_COUNT)
        assertEquals(GameId.MASTERMIND.ordinal + 1, GameId.BATTLESHIP.ordinal)
        assertEquals(GameId.BATTLESHIP.ordinal + 1, GameId.LETTERFALL.ordinal)
        assertEquals(GameId.LETTERFALL.ordinal + 1, GameId.MANCALA.ordinal)
        assertEquals(GameId.MANCALA.ordinal + 1, GameId.FIVE_ROW.ordinal)
        assertEquals(GameId.FIVE_ROW.ordinal + 1, GameId.WORD_LADDER.ordinal)
        assertEquals(GameId.WORD_LADDER.ordinal + 1, GameId.HONEYCOMB.ordinal)
        assertEquals(GameId.HONEYCOMB.ordinal + 1, GameId.LETTER_DRAW.ordinal)
        assertEquals(GameId.LETTER_DRAW.ordinal + 1, GameId.BRIDGES.ordinal)
        assertEquals(GameId.BRIDGES.ordinal + 1, GameId.SLITHERLINK.ordinal)
        assertEquals(GameId.SLITHERLINK.ordinal + 1, GameId.TOWERS.ordinal)
        assertEquals(GameId.TOWERS.ordinal + 1, GameId.LIGHTS.ordinal)
        assertEquals(GameId.LIGHTS.ordinal + 1, GameId.CHESS.ordinal)
        assertEquals(GameId.CHESS.ordinal + 1, GameId.FREECELL.ordinal)
        assertEquals(GameId.FREECELL.ordinal + 1, GameId.SLIDING_BLOCKS.ordinal)
        assertEquals(GameId.SLIDING_BLOCKS.ordinal + 1, GameId.GO.ordinal)
        assertEquals(GameId.GO.ordinal + 1, GameId.KLOTSKI.ordinal)
        assertEquals(GameId.KLOTSKI.ordinal + 1, GameId.YACHT.ordinal)
        assertEquals(GameId.YACHT.ordinal + 1, GameId.SHUT_BOX.ordinal)
        assertEquals(GameId.SHUT_BOX.ordinal + 1, GameId.TEN_THOUSAND.ordinal)
        assertEquals(GameId.TEN_THOUSAND.ordinal + 1, GameId.SHIP_CREW.ordinal)
        assertEquals(GameId.SHIP_CREW.ordinal + 1, GameId.SHOGI.ordinal)
        assertEquals(GameId.SHOGI.ordinal + 1, GameId.HEX.ordinal)
        assertEquals(GameId.HEX.ordinal + 1, GameId.RPS.ordinal)
        assertEquals(GameId.RPS.ordinal + 1, GameId.TIC_TAC_TOE.ordinal)
        assertEquals(GameId.TIC_TAC_TOE.ordinal + 1, GameId.TRID.ordinal)
        assertEquals(GameId.TRID.ordinal + 1, GameId.RAUMSCHACH.ordinal)
        assertEquals(GameId.RAUMSCHACH.ordinal + 1, GameId.LOG_THROW.ordinal)
        assertEquals(GameId.LOG_THROW.ordinal, GAME_COUNT - 1)
        assertSame(BuiltInThemes.table, BuiltInThemes.default)
    }

    @Test fun `share codes round-trip every custom setting`() {
        val theme = custom()
        val code = ThemeCodec.encode(theme)
        assertTrue(code.startsWith("SGT1|Night felt|playful|7|LIST|0|1|"))
        assertEquals(theme.copy(id = "other"), ThemeCodec.decode(code, "other"))
        assertEquals(theme, ThemeCodec.decodeStored(ThemeCodec.encodeStored(theme)))
    }

    @Test fun `resolving applies overrides to both modes and keeps the base surfaces`() {
        val spec = custom().resolve()
        val base = BuiltInThemes.playful
        assertFalse(spec.builtIn)
        assertEquals(0xFF123456, spec.light[ThemeToken.TABLE]); assertEquals(0xFF123456, spec.dark[ThemeToken.TABLE])
        assertEquals(base.light[ThemeToken.BACKGROUND], spec.light[ThemeToken.BACKGROUND])
        assertEquals(base.dark[ThemeToken.BACKGROUND], spec.dark[ThemeToken.BACKGROUND])
        assertEquals(HomeLayout.LIST, spec.homeLayout); assertEquals(7, spec.cornerRadius)
    }

    @Test fun `invalid codes and unsafe themes are rejected`() {
        val good = ThemeCodec.encode(custom())
        listOf(
            "", "hello", good.replace("SGT1", "SGT2"), good.replace("|playful|", "|nope|"),
            good.replace("|7|", "|99|"), good.replace("|LIST|", "|GRIDDY|"), good.replace("TABLE=", "BACKGROUND="),
            good.replace("FF123456", "12345"), good.replace("Night felt", ""), good + "|extra", "x".repeat(3000),
        ).forEach { assertNull(ThemeCodec.decode(it, "id"), it) }
        assertThrows(IllegalArgumentException::class.java) { custom().copy(name = "bad|name") }
        assertThrows(IllegalArgumentException::class.java) { custom(mapOf(ThemeToken.SURFACE to 0xFF000000)) }
    }

    @Test fun `unknown selected ids and bad stored lines fall back safely`() {
        val settings = AppearanceSettings(selectedId = "missing", customThemes = listOf(custom()))
        assertEquals(BuiltInThemes.default, settings.selected)
        assertEquals(BuiltInThemes.all.size + 1, settings.themes.size)
        assertNull(ThemeCodec.decodeStored("no tab here"))
        assertNull(ThemeCodec.decodeStored("\tSGT1|x|table|1|GRID|1|0|"))
    }
}
