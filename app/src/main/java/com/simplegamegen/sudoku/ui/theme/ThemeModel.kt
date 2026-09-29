package com.simplegamegen.sudoku.ui.theme

/**
 * Pure theme model (no Compose types) so themes can be stored, shared and
 * unit-tested. Colors are ARGB longs. Add a built-in theme by adding one
 * [ThemeSpec] to [BuiltInThemes.all]; players build [CustomTheme]s in the app.
 */
enum class ThemeToken(val label: String, val editable: Boolean = false) {
    BACKGROUND("Background"),
    SURFACE("Surface"),
    SURFACE_ALT("Raised surface"),
    ON_BACKGROUND("Text"),
    MUTED("Secondary text"),
    OUTLINE("Outline"),
    ACCENT("Accent", editable = true),
    ON_ACCENT("Text on accent"),
    TOP_BAR("Header", editable = true),
    ON_TOP_BAR("Text on header"),
    TABLE("Game table", editable = true),
    TABLE_INSET("Table inset"),
    ON_TABLE("Text on table"),
    PIECE_FACE("Tile and domino face", editable = true),
    PIECE_EDGE("Tile edge"),
    TILE_BACK("Mahjong tile back", editable = true),
    CARD_BACK("Card back", editable = true),
    CARD_BACK_PATTERN("Card back pattern"),
    BOARD_LIGHT("Board light squares", editable = true),
    BOARD_DARK("Board dark squares", editable = true),
    PLAYER_ONE("Your checkers", editable = true),
    PLAYER_TWO("Computer checkers", editable = true),
    HIGHLIGHT("Selection", editable = true),
    DANGER("Error"),
    SUCCESS("Success"),
}

enum class HomeLayout { GRID, LIST }
enum class DarkMode { SYSTEM, LIGHT, DARK }

class ThemeColors(values: Map<ThemeToken, Long>) {
    private val values: Map<ThemeToken, Long> = values.toMap()
    init { require(ThemeToken.entries.all { it in this.values }) { "Theme is missing colors" } }
    operator fun get(token: ThemeToken): Long = values.getValue(token)
    fun with(overrides: Map<ThemeToken, Long>) = ThemeColors(values + overrides)
    override fun equals(other: Any?) = other is ThemeColors && other.values == values
    override fun hashCode() = values.hashCode()
}

/** Number of home-screen games; [ThemeSpec.gameColors] follows `GameId` order. */
const val GAME_COUNT = 41

data class ThemeSpec(
    val id: String,
    val name: String,
    val description: String,
    val light: ThemeColors,
    val dark: ThemeColors,
    val gameColors: List<Long>,
    val cornerRadius: Int,
    val homeLayout: HomeLayout,
    val coloredTiles: Boolean,
    val gameHeaders: Boolean,
    val builtIn: Boolean = true,
) {
    init {
        require(gameColors.size == GAME_COUNT)
        require(cornerRadius in 0..MAX_RADIUS)
    }
    fun colors(dark: Boolean) = if (dark) this.dark else light

    companion object { const val MAX_RADIUS = 28 }
}

/** A player-made theme: a built-in base plus color and layout choices. */
data class CustomTheme(
    val id: String,
    val name: String,
    val baseId: String,
    val overrides: Map<ThemeToken, Long> = emptyMap(),
    val cornerRadius: Int,
    val homeLayout: HomeLayout,
    val coloredTiles: Boolean,
    val gameHeaders: Boolean,
) {
    init {
        require(name.isNotBlank() && name.length <= MAX_NAME && name.none { it == '|' || it == '\n' || it == '\t' })
        require(overrides.keys.all { it.editable })
        require(cornerRadius in 0..ThemeSpec.MAX_RADIUS)
    }

    fun resolve(): ThemeSpec {
        val base = BuiltInThemes.byId(baseId) ?: BuiltInThemes.default
        return base.copy(
            id = id, name = name, description = "Based on ${base.name}",
            light = base.light.with(overrides), dark = base.dark.with(overrides),
            cornerRadius = cornerRadius, homeLayout = homeLayout,
            coloredTiles = coloredTiles, gameHeaders = gameHeaders, builtIn = false,
        )
    }

    companion object {
        const val MAX_NAME = 32

        fun from(base: ThemeSpec, id: String, name: String) = CustomTheme(
            id = id, name = name, baseId = if (base.builtIn) base.id else BuiltInThemes.default.id,
            cornerRadius = base.cornerRadius, homeLayout = base.homeLayout,
            coloredTiles = base.coloredTiles, gameHeaders = base.gameHeaders,
        )
    }
}

/** Single-line share code: `SGT1|name|base|radius|layout|tiles|headers|TOKEN=AARRGGBB,...`. */
object ThemeCodec {
    private const val VERSION = "SGT1"

    fun encode(t: CustomTheme): String = listOf(
        VERSION, t.name, t.baseId, t.cornerRadius.toString(), t.homeLayout.name,
        if (t.coloredTiles) "1" else "0", if (t.gameHeaders) "1" else "0",
        t.overrides.entries.sortedBy { it.key.ordinal }
            .joinToString(",") { "${it.key.name}=${(it.value and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0')}" },
    ).joinToString("|")

    /** Returns null for anything that is not a valid, complete share code. */
    fun decode(code: String, id: String): CustomTheme? = try {
        val text = code.trim()
        require(text.length <= 2_000)
        val parts = text.split('|')
        require(parts.size == 8 && parts[0] == VERSION)
        require(BuiltInThemes.byId(parts[2]) != null)
        require(parts[5] in listOf("0", "1") && parts[6] in listOf("0", "1"))
        val overrides = if (parts[7].isEmpty()) emptyMap() else parts[7].split(',').associate { entry ->
            val kv = entry.split('=')
            require(kv.size == 2 && kv[1].length == 8)
            ThemeToken.valueOf(kv[0]) to kv[1].toLong(16)
        }
        CustomTheme(
            id = id, name = parts[1].trim(), baseId = parts[2], overrides = overrides,
            cornerRadius = parts[3].toInt(), homeLayout = HomeLayout.valueOf(parts[4]),
            coloredTiles = parts[5] == "1", gameHeaders = parts[6] == "1",
        )
    } catch (_: IllegalArgumentException) { null }

    /** Stored form keeps the local id in front of the share code. */
    fun encodeStored(t: CustomTheme) = "${t.id}\t${encode(t)}"
    fun decodeStored(line: String): CustomTheme? {
        val tab = line.indexOf('\t')
        if (tab <= 0) return null
        return decode(line.substring(tab + 1), line.substring(0, tab))
    }
}

object BuiltInThemes {
    private fun colors(vararg pairs: Pair<ThemeToken, Long>) = ThemeColors(pairs.toMap())

    val table = ThemeSpec(
        id = "table", name = "Game table",
        description = "Navy and amber, green felt, ivory tiles and wooden boards",
        light = colors(
            ThemeToken.BACKGROUND to 0xFFF6F1E7, ThemeToken.SURFACE to 0xFFFFFDF8,
            ThemeToken.SURFACE_ALT to 0xFFEDE4D3, ThemeToken.ON_BACKGROUND to 0xFF2A2418,
            ThemeToken.MUTED to 0xFF6B5B45, ThemeToken.OUTLINE to 0xFFD8CBB2,
            ThemeToken.ACCENT to 0xFF173B55, ThemeToken.ON_ACCENT to 0xFFFFFFFF,
            ThemeToken.TOP_BAR to 0xFF173B55, ThemeToken.ON_TOP_BAR to 0xFFFFFFFF,
            ThemeToken.TABLE to 0xFF2E6B4F, ThemeToken.TABLE_INSET to 0xFF265A42, ThemeToken.ON_TABLE to 0xFFE8F1E9,
            ThemeToken.PIECE_FACE to 0xFFFBF6E9, ThemeToken.PIECE_EDGE to 0xFFC9B98F,
            ThemeToken.TILE_BACK to 0xFF2F7A55, ThemeToken.CARD_BACK to 0xFF8C2F2F, ThemeToken.CARD_BACK_PATTERN to 0xFFB5484A,
            ThemeToken.BOARD_LIGHT to 0xFFE9D8B4, ThemeToken.BOARD_DARK to 0xFF8B5A34,
            ThemeToken.PLAYER_ONE to 0xFF2B2B2B, ThemeToken.PLAYER_TWO to 0xFFB8322C,
            ThemeToken.HIGHLIGHT to 0xFFE9AA45, ThemeToken.DANGER to 0xFFB3261E, ThemeToken.SUCCESS to 0xFF2E7D32,
        ),
        dark = colors(
            ThemeToken.BACKGROUND to 0xFF16140F, ThemeToken.SURFACE to 0xFF221F18,
            ThemeToken.SURFACE_ALT to 0xFF2E2A21, ThemeToken.ON_BACKGROUND to 0xFFF1E9D8,
            ThemeToken.MUTED to 0xFFB9AB91, ThemeToken.OUTLINE to 0xFF4A4234,
            ThemeToken.ACCENT to 0xFFE9AA45, ThemeToken.ON_ACCENT to 0xFF2A1C00,
            ThemeToken.TOP_BAR to 0xFF0F2536, ThemeToken.ON_TOP_BAR to 0xFFF1E9D8,
            ThemeToken.TABLE to 0xFF1F4A37, ThemeToken.TABLE_INSET to 0xFF1A3F2F, ThemeToken.ON_TABLE to 0xFFDCEBDD,
            ThemeToken.PIECE_FACE to 0xFFF1E8D2, ThemeToken.PIECE_EDGE to 0xFFA89A74,
            ThemeToken.TILE_BACK to 0xFF256346, ThemeToken.CARD_BACK to 0xFF7A2828, ThemeToken.CARD_BACK_PATTERN to 0xFF9E3D3F,
            ThemeToken.BOARD_LIGHT to 0xFFCDB88F, ThemeToken.BOARD_DARK to 0xFF6E4527,
            ThemeToken.PLAYER_ONE to 0xFF1E1E1E, ThemeToken.PLAYER_TWO to 0xFFA92C27,
            ThemeToken.HIGHLIGHT to 0xFFF4D58D, ThemeToken.DANGER to 0xFFF2B8B5, ThemeToken.SUCCESS to 0xFF81C995,
        ),
        gameColors = listOf(
            0xFF173B55, 0xFF7B2D3B, 0xFF2C4A6E, 0xFF8A5A1C, 0xFF3D3D5C, 0xFF4A5A3A,
            0xFF3A4450, 0xFF6E2F3F,
            0xFF3F5E2A, 0xFF2F6F73, 0xFF6B4E2E, 0xFF5A4632, 0xFF3E6B5A, 0xFF6A3F5E,
            0xFF2F4E6B, 0xFF5E5230, 0xFF3B5B7A, 0xFF4E6A2F, 0xFF6B3F2A, 0xFF2B2B3A, 0xFF7A4A1E, 0xFF7A2335, 0xFF3F6B6B, 0xFF6B4A7A,
            0xFF5B3A6E, 0xFF2E6B4F, 0xFF24463A, 0xFF8C6A2A, 0xFF7A5C1E, 0xFF4F4A7A,
            0xFF9C2F2F, 0xFF8A4B2A, 0xFF33475B, 0xFF1F5E6E, 0xFF55702E, 0xFF6E3B3B,
            0xFFA5652A, 0xFF2E3F6B, 0xFF1565C0, 0xFF6A1B9A, 0xFF1F6A7A,
        ),
        cornerRadius = 12, homeLayout = HomeLayout.GRID, coloredTiles = true, gameHeaders = false,
    )

    val minimal = ThemeSpec(
        id = "minimal", name = "Minimal",
        description = "Quiet neutral surfaces with a single teal accent",
        light = colors(
            ThemeToken.BACKGROUND to 0xFFFAFAF8, ThemeToken.SURFACE to 0xFFFFFFFF,
            ThemeToken.SURFACE_ALT to 0xFFF1F0EC, ThemeToken.ON_BACKGROUND to 0xFF1F2328,
            ThemeToken.MUTED to 0xFF62625D, ThemeToken.OUTLINE to 0xFFE0DED7,
            ThemeToken.ACCENT to 0xFF0F6E56, ThemeToken.ON_ACCENT to 0xFFFFFFFF,
            ThemeToken.TOP_BAR to 0xFFFAFAF8, ThemeToken.ON_TOP_BAR to 0xFF1F2328,
            ThemeToken.TABLE to 0xFFEDEBE5, ThemeToken.TABLE_INSET to 0xFFE2E0D9, ThemeToken.ON_TABLE to 0xFF1F2328,
            ThemeToken.PIECE_FACE to 0xFFFFFFFF, ThemeToken.PIECE_EDGE to 0xFFC9C6BD,
            ThemeToken.TILE_BACK to 0xFF1D9E75, ThemeToken.CARD_BACK to 0xFF0F6E56, ThemeToken.CARD_BACK_PATTERN to 0xFF1D9E75,
            ThemeToken.BOARD_LIGHT to 0xFFF3F1EC, ThemeToken.BOARD_DARK to 0xFF8FB3A6,
            ThemeToken.PLAYER_ONE to 0xFF1F2328, ThemeToken.PLAYER_TWO to 0xFFF6F6F4,
            ThemeToken.HIGHLIGHT to 0xFF1D9E75, ThemeToken.DANGER to 0xFFB3261E, ThemeToken.SUCCESS to 0xFF0F6E56,
        ),
        dark = colors(
            ThemeToken.BACKGROUND to 0xFF121413, ThemeToken.SURFACE to 0xFF1B1E1D,
            ThemeToken.SURFACE_ALT to 0xFF252927, ThemeToken.ON_BACKGROUND to 0xFFE6E8E6,
            ThemeToken.MUTED to 0xFF9EA4A0, ThemeToken.OUTLINE to 0xFF343936,
            ThemeToken.ACCENT to 0xFF5DCAA5, ThemeToken.ON_ACCENT to 0xFF04342C,
            ThemeToken.TOP_BAR to 0xFF121413, ThemeToken.ON_TOP_BAR to 0xFFE6E8E6,
            ThemeToken.TABLE to 0xFF1E2221, ThemeToken.TABLE_INSET to 0xFF262B29, ThemeToken.ON_TABLE to 0xFFE6E8E6,
            ThemeToken.PIECE_FACE to 0xFFF4F4F2, ThemeToken.PIECE_EDGE to 0xFF9A9A94,
            ThemeToken.TILE_BACK to 0xFF1D9E75, ThemeToken.CARD_BACK to 0xFF0F6E56, ThemeToken.CARD_BACK_PATTERN to 0xFF1D9E75,
            ThemeToken.BOARD_LIGHT to 0xFF59605C, ThemeToken.BOARD_DARK to 0xFF2C3330,
            ThemeToken.PLAYER_ONE to 0xFF101211, ThemeToken.PLAYER_TWO to 0xFFF4F4F2,
            ThemeToken.HIGHLIGHT to 0xFF5DCAA5, ThemeToken.DANGER to 0xFFF2B8B5, ThemeToken.SUCCESS to 0xFF5DCAA5,
        ),
        gameColors = List(GAME_COUNT) { 0xFF0F6E56 },
        cornerRadius = 10, homeLayout = HomeLayout.LIST, coloredTiles = false, gameHeaders = false,
    )

    val playful = ThemeSpec(
        id = "playful", name = "Playful",
        description = "Bright game colors, chunky rounded shapes and violet felt",
        light = colors(
            ThemeToken.BACKGROUND to 0xFFFFF7EC, ThemeToken.SURFACE to 0xFFFFFFFF,
            ThemeToken.SURFACE_ALT to 0xFFFDEBD3, ThemeToken.ON_BACKGROUND to 0xFF26215C,
            ThemeToken.MUTED to 0xFF5E578F, ThemeToken.OUTLINE to 0xFFF0D9B8,
            ThemeToken.ACCENT to 0xFFD4537E, ThemeToken.ON_ACCENT to 0xFFFFFFFF,
            ThemeToken.TOP_BAR to 0xFFFFF7EC, ThemeToken.ON_TOP_BAR to 0xFF3C3489,
            ThemeToken.TABLE to 0xFF534AB7, ThemeToken.TABLE_INSET to 0xFF473FA0, ThemeToken.ON_TABLE to 0xFFEEEDFE,
            ThemeToken.PIECE_FACE to 0xFFFFFFFF, ThemeToken.PIECE_EDGE to 0xFFCBC6F0,
            ThemeToken.TILE_BACK to 0xFFEF9F27, ThemeToken.CARD_BACK to 0xFFD85A30, ThemeToken.CARD_BACK_PATTERN to 0xFFF0997B,
            ThemeToken.BOARD_LIGHT to 0xFFFAC775, ThemeToken.BOARD_DARK to 0xFFD85A30,
            ThemeToken.PLAYER_ONE to 0xFF26215C, ThemeToken.PLAYER_TWO to 0xFFFFFFFF,
            ThemeToken.HIGHLIGHT to 0xFFEF9F27, ThemeToken.DANGER to 0xFFE24B4A, ThemeToken.SUCCESS to 0xFF1D9E75,
        ),
        dark = colors(
            ThemeToken.BACKGROUND to 0xFF1A1733, ThemeToken.SURFACE to 0xFF25214A,
            ThemeToken.SURFACE_ALT to 0xFF302B5E, ThemeToken.ON_BACKGROUND to 0xFFEEEDFE,
            ThemeToken.MUTED to 0xFFB7B2EE, ThemeToken.OUTLINE to 0xFF433C8F,
            ThemeToken.ACCENT to 0xFFED93B1, ThemeToken.ON_ACCENT to 0xFF4B1528,
            ThemeToken.TOP_BAR to 0xFF1A1733, ThemeToken.ON_TOP_BAR to 0xFFEEEDFE,
            ThemeToken.TABLE to 0xFF2F2A5C, ThemeToken.TABLE_INSET to 0xFF26215C, ThemeToken.ON_TABLE to 0xFFEEEDFE,
            ThemeToken.PIECE_FACE to 0xFFFBFAFF, ThemeToken.PIECE_EDGE to 0xFF8F88D6,
            ThemeToken.TILE_BACK to 0xFFBA7517, ThemeToken.CARD_BACK to 0xFFB14A26, ThemeToken.CARD_BACK_PATTERN to 0xFFD8805A,
            ThemeToken.BOARD_LIGHT to 0xFFE8B560, ThemeToken.BOARD_DARK to 0xFF993C1D,
            ThemeToken.PLAYER_ONE to 0xFF1A1733, ThemeToken.PLAYER_TWO to 0xFFFFFFFF,
            ThemeToken.HIGHLIGHT to 0xFFFAC775, ThemeToken.DANGER to 0xFFF09595, ThemeToken.SUCCESS to 0xFF5DCAA5,
        ),
        gameColors = listOf(
            0xFF7F77DD, 0xFF534AB7, 0xFF0F6E56, 0xFFEF9F27, 0xFF993C1D, 0xFF3B6D11,
            0xFF26215C, 0xFFE24B4A,
            0xFF378ADD, 0xFF1D9E75, 0xFFD85A30, 0xFF72243E, 0xFF0C447C, 0xFF854F0B,
            0xFF1D9E75, 0xFFBA7517, 0xFF5B4FC8, 0xFF2E9E4F, 0xFFC0502E, 0xFF2C2C54, 0xFFE07A2E, 0xFFC62E4B, 0xFF2E9E9E, 0xFF9A5BC8,
            0xFF639922, 0xFFD4537E, 0xFF26215C, 0xFFFAC775, 0xFF993556, 0xFF5DCAA5,
            0xFFE24B4A, 0xFFBA7517, 0xFF185FA5, 0xFF2A7FBF, 0xFF97C459, 0xFFD85A8A,
            0xFFF0997B, 0xFF3C3489, 0xFF1E88E5, 0xFF8E24AA, 0xFF1D9E9E,
        ),
        cornerRadius = 20, homeLayout = HomeLayout.GRID, coloredTiles = true, gameHeaders = true,
    )

    /** Register new built-in themes here. */
    val all: List<ThemeSpec> = listOf(table, minimal, playful)
    val default: ThemeSpec get() = table
    fun byId(id: String): ThemeSpec? = all.firstOrNull { it.id == id }
}
