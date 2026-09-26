package com.simplegamegen.sudoku.ui.blot

import androidx.compose.ui.graphics.Color
import com.simplegamegen.sudoku.data.ArcadeStore
import org.json.JSONArray
import org.json.JSONObject

// ---------------- Blotwords theme engine: the parts a theme is built from ----------------

/** How a filled square is drawn when there is no picture for it. */
enum class BlotMark(val label: String) { BLOCK("Ink blocks"), BLOT("Ink blots"), WAVES("Sea water"), STAMP("Stamps") }

/** The shape of a letter square. */
enum class BlotTile(val label: String) { ROUNDED("Rounded"), SQUARE("Square corners"), BUBBLE("Bubbles") }

/** The drawn creature, when there is no picture for it. */
enum class BlotMascot(val label: String) { OCTOPUS("Purple octopus"), MERMAID("Mermaid"), NONE("No creature") }

/** How a square fills in when it's inked. */
enum class FillStyle(val label: String) { SPLASH("Splash"), RISE("Rise"), STAMP("Stamp"), FADE("Fade"), SPIN("Spin"), DROP("Drop") }

/** How fast the animations run. */
enum class Pace(val label: String, val factor: Float) { SLOW("Slow", 1.6f), NORMAL("Normal", 1f), FAST("Fast", 0.6f) }

/** What the creature does while waiting. */
enum class IdleStyle(val label: String) { BOB("Bob"), SWAY("Sway"), BOUNCE("Bounce"), STILL("Still") }

/** What the creature does when a word is written. */
enum class ReactStyle(val label: String) { HOP("Hop"), SPIN("Spin"), WIGGLE("Wiggle"), NONE("Nothing") }

/** What floats up when the grid is done. */
enum class PartyStyle(val label: String) { DROPS("Ink drops"), BUBBLES("Bubbles"), CONFETTI("Confetti"), STARS("Stars"), HEARTS("Hearts"), PICTURE("My picture"), NONE("Nothing") }

/** How a word being traced is drawn. */
enum class TrailStyle(val label: String) { LINE("Ink line"), DOTS("Dots"), NONE("None") }

/** The pictures a theme can use in place of the drawn parts. */
enum class AssetSlot(val label: String) {
    CREATURE("Creature"), CREATURE_HAPPY("Creature when happy"), MARK("Filled square"), TILE("Letter square"),
    BOARD("Board background"), PARTICLE("Celebration piece"),
}

/** All the moving parts of a theme. */
data class BlotMotion(
    val fill: FillStyle = FillStyle.SPLASH,
    val pace: Pace = Pace.NORMAL,
    /** Milliseconds between one letter's square and the next, so a word fills like a pen stroke. */
    val stroke: Int = 75,
    val idle: IdleStyle = IdleStyle.BOB,
    val react: ReactStyle = ReactStyle.HOP,
    val party: PartyStyle = PartyStyle.DROPS,
    /** How many pieces float up when the grid is done. */
    val partyAmount: Int = 18,
    val trail: TrailStyle = TrailStyle.LINE,
) {
    init { require(stroke in 0..300 && partyAmount in 0..60) }
}

/** Colors for one look. */
data class BlotColors(val paper: Color, val tile: Color, val letter: Color, val mark: Color, val detail: Color, val accent: Color)

/** A hand-picked color scheme, light and dark, so every theme someone makes still looks good. */
data class BlotScheme(val id: String, val label: String, val light: BlotColors, val dark: BlotColors) {
    fun colors(dark: Boolean) = if (dark) this.dark else light
}

object BlotSchemes {
    private fun c(p: Long, t: Long, l: Long, m: Long, d: Long, a: Long) = BlotColors(Color(p), Color(t), Color(l), Color(m), Color(d), Color(a))

    val all = listOf(
        BlotScheme("ink", "Paper and ink",
            c(0xFFFBF8F1, 0xFFFFFEFA, 0xFF2A2530, 0xFF3A2A4E, 0xFF7B5BA6, 0xFF7E57C2),
            c(0xFF1C1A22, 0xFF2B2833, 0xFFEDE7F6, 0xFF0D0A13, 0xFF7E57C2, 0xFFB39DDB)),
        BlotScheme("sea", "Sand and sea",
            c(0xFFF4E8CF, 0xFFFFF8EC, 0xFF1D4E5F, 0xFF1F6F8B, 0xFFBDE6F1, 0xFFE8706A),
            c(0xFF0F2A33, 0xFF1B3E49, 0xFFE3F4F8, 0xFF0B5A73, 0xFF7FD0E6, 0xFFFF8A80)),
        BlotScheme("coral", "Coral reef",
            c(0xFFFFF1EC, 0xFFFFFFFF, 0xFF5A2A2A, 0xFFE0605A, 0xFFFFC2B8, 0xFFF08A5D),
            c(0xFF2A1715, 0xFF3A2320, 0xFFFFE5DE, 0xFFB8443F, 0xFFFF9C8F, 0xFFFFAB91)),
        BlotScheme("night", "Night sky",
            c(0xFFE8ECF6, 0xFFFFFFFF, 0xFF1B2240, 0xFF1B2240, 0xFFF2C94C, 0xFF3F51B5),
            c(0xFF0D1226, 0xFF1A2140, 0xFFE8ECF6, 0xFF05081A, 0xFFF2C94C, 0xFF8C9EFF)),
        BlotScheme("meadow", "Meadow",
            c(0xFFF1F6E8, 0xFFFFFFFF, 0xFF2E4A1F, 0xFF4E7D32, 0xFFC5E1A5, 0xFF7CB342),
            c(0xFF162112, 0xFF22311C, 0xFFE6F2DA, 0xFF2F5A1E, 0xFF9CCC65, 0xFFAED581)),
        BlotScheme("berry", "Berry",
            c(0xFFFBEFF6, 0xFFFFFFFF, 0xFF4A1D3A, 0xFF8E2466, 0xFFF48FB1, 0xFFD81B60),
            c(0xFF241220, 0xFF351B2F, 0xFFF8E1EE, 0xFF5E1244, 0xFFF06292, 0xFFF48FB1)),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

/**
 * A Blotwords theme: colors, drawn parts, the player's own pictures ([assets] maps a slot to a file saved by
 * [BlotAssets]) and how everything moves. The words and rules never change.
 */
data class BlotTheme(
    val id: String,
    val name: String,
    val mark: BlotMark,
    val tile: BlotTile,
    val scheme: String,
    val mascot: BlotMascot,
    val motion: BlotMotion = BlotMotion(),
    val assets: Map<AssetSlot, String> = emptyMap(),
    val builtIn: Boolean = false,
) {
    val colors: BlotScheme get() = BlotSchemes.byId(scheme)

    /** Why this theme can't be saved, or null. */
    fun problem(): String? =
        if (name.isBlank() || name.length > MAX_NAME || name.any { it == '\n' || it == '\t' }) "Give the theme a name, up to $MAX_NAME characters." else null

    companion object { const val MAX_NAME = 28 }
}

object BlotThemes {
    val ink = BlotTheme("ink", "Ink", BlotMark.BLOCK, BlotTile.SQUARE, "ink", BlotMascot.OCTOPUS,
        BlotMotion(FillStyle.SPLASH, Pace.NORMAL, 75, IdleStyle.BOB, ReactStyle.HOP, PartyStyle.DROPS, 18, TrailStyle.LINE), builtIn = true)
    val sea = BlotTheme("sea", "Mermaids and the Sea", BlotMark.WAVES, BlotTile.BUBBLE, "sea", BlotMascot.MERMAID,
        BlotMotion(FillStyle.RISE, Pace.NORMAL, 90, IdleStyle.SWAY, ReactStyle.HOP, PartyStyle.BUBBLES, 22, TrailStyle.DOTS), builtIn = true)
    val builtIns = listOf(ink, sea)

    private const val THEMES_KEY = "blotwords:themes"
    private const val CHOSEN_KEY = "blotwords:theme"

    fun toJson(t: BlotTheme): JSONObject = JSONObject().apply {
        put("id", t.id); put("name", t.name); put("mark", t.mark.name); put("tile", t.tile.name)
        put("scheme", t.scheme); put("mascot", t.mascot.name)
        put("motion", JSONObject().apply {
            put("fill", t.motion.fill.name); put("pace", t.motion.pace.name); put("stroke", t.motion.stroke)
            put("idle", t.motion.idle.name); put("react", t.motion.react.name); put("party", t.motion.party.name)
            put("partyAmount", t.motion.partyAmount); put("trail", t.motion.trail.name)
        })
        put("assets", JSONObject().apply { t.assets.forEach { (slot, file) -> put(slot.name, file) } })
    }

    fun fromJson(o: JSONObject): BlotTheme? = runCatching {
        val m = o.optJSONObject("motion") ?: JSONObject()
        val defaults = BlotMotion()
        fun <E : Enum<E>> pick(values: Array<E>, name: String?, fallback: E) = values.firstOrNull { it.name == name } ?: fallback
        val motion = BlotMotion(
            pick(FillStyle.values(), m.optString("fill"), defaults.fill), pick(Pace.values(), m.optString("pace"), defaults.pace),
            m.optInt("stroke", defaults.stroke).coerceIn(0, 300), pick(IdleStyle.values(), m.optString("idle"), defaults.idle),
            pick(ReactStyle.values(), m.optString("react"), defaults.react), pick(PartyStyle.values(), m.optString("party"), defaults.party),
            m.optInt("partyAmount", defaults.partyAmount).coerceIn(0, 60), pick(TrailStyle.values(), m.optString("trail"), defaults.trail))
        val a = o.optJSONObject("assets") ?: JSONObject()
        val assets = AssetSlot.entries.mapNotNull { slot -> a.optString(slot.name).takeIf { it.isNotEmpty() }?.let { slot to it } }.toMap()
        BlotTheme(o.getString("id"), o.getString("name"), BlotMark.valueOf(o.getString("mark")), BlotTile.valueOf(o.getString("tile")),
            o.getString("scheme"), BlotMascot.valueOf(o.getString("mascot")), motion, assets)
            .takeIf { it.problem() == null && it.id.startsWith("custom") }
    }.getOrNull()

    suspend fun custom(store: ArcadeStore): List<BlotTheme> {
        val text = store.load(THEMES_KEY) ?: return emptyList()
        return runCatching { JSONArray(text).let { arr -> (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) } } }.getOrDefault(emptyList())
    }

    private suspend fun write(store: ArcadeStore, themes: List<BlotTheme>) =
        store.save(THEMES_KEY, JSONArray().apply { themes.forEach { put(toJson(it)) } }.toString())

    suspend fun all(store: ArcadeStore): List<BlotTheme> = builtIns + custom(store)

    suspend fun chosen(store: ArcadeStore): BlotTheme {
        val id = store.load(CHOSEN_KEY) ?: return ink
        return all(store).firstOrNull { it.id == id } ?: ink
    }

    suspend fun choose(store: ArcadeStore, id: String) = store.save(CHOSEN_KEY, id)

    suspend fun save(store: ArcadeStore, theme: BlotTheme) {
        require(!theme.builtIn && theme.problem() == null)
        write(store, custom(store).filter { it.id != theme.id } + theme)
    }

    suspend fun delete(store: ArcadeStore, id: String) {
        write(store, custom(store).filter { it.id != id })
        if (store.load(CHOSEN_KEY) == id) choose(store, ink.id)
    }

    /** The look for a game, falling back to Ink if its theme was deleted. */
    fun find(themes: List<BlotTheme>, id: String) = themes.firstOrNull { it.id == id } ?: ink
}
