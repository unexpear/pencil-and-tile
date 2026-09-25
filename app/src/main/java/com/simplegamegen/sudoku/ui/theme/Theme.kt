package com.simplegamegen.sudoku.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplegamegen.sudoku.ui.GameId

/** Resolved theme colors for drawing game surfaces and pieces. */
@Immutable
class GamePalette(private val c: ThemeColors) {
    private fun col(t: ThemeToken) = Color(c[t])
    val background = col(ThemeToken.BACKGROUND)
    val surface = col(ThemeToken.SURFACE)
    val surfaceAlt = col(ThemeToken.SURFACE_ALT)
    val text = col(ThemeToken.ON_BACKGROUND)
    val muted = col(ThemeToken.MUTED)
    val outline = col(ThemeToken.OUTLINE)
    val accent = col(ThemeToken.ACCENT)
    val onAccent = col(ThemeToken.ON_ACCENT)
    val topBar = col(ThemeToken.TOP_BAR)
    val onTopBar = col(ThemeToken.ON_TOP_BAR)
    val table = col(ThemeToken.TABLE)
    val tableInset = col(ThemeToken.TABLE_INSET)
    val onTable = col(ThemeToken.ON_TABLE)
    val pieceFace = col(ThemeToken.PIECE_FACE)
    val pieceEdge = col(ThemeToken.PIECE_EDGE)
    val tileBack = col(ThemeToken.TILE_BACK)
    val cardBack = col(ThemeToken.CARD_BACK)
    val cardBackPattern = col(ThemeToken.CARD_BACK_PATTERN)
    val boardLight = col(ThemeToken.BOARD_LIGHT)
    val boardDark = col(ThemeToken.BOARD_DARK)
    val playerOne = col(ThemeToken.PLAYER_ONE)
    val playerTwo = col(ThemeToken.PLAYER_TWO)
    val highlight = col(ThemeToken.HIGHLIGHT)
    val danger = col(ThemeToken.DANGER)
    val success = col(ThemeToken.SUCCESS)
    /** Soft accent wash for selected rows, chips and cells. */
    val accentSoft = accent.copy(alpha = 0.16f).compositeOver(surface)
    val highlightSoft = highlight.copy(alpha = 0.28f).compositeOver(surface)
}

@Immutable
data class GameLook(val spec: ThemeSpec, val dark: Boolean) {
    val colors = GamePalette(spec.colors(dark))
    val radius: Dp = spec.cornerRadius.dp
    fun gameColor(id: GameId): Color = Color(spec.gameColors[id.ordinal])
    fun headerColor(id: GameId?): Color = if (spec.gameHeaders && id != null) gameColor(id) else colors.topBar
    fun onHeader(id: GameId?): Color = if (spec.gameHeaders && id != null) readableOn(gameColor(id)) else colors.onTopBar
}

private val DarkInk = Color(0xFF1C1B1A)

/** White or near-black, whichever has the higher contrast ratio against [color]. */
fun readableOn(color: Color): Color {
    val l = color.luminance()
    val onWhite = 1.05f / (l + 0.05f)
    val onDark = (l + 0.05f) / (DarkInk.luminance() + 0.05f)
    return if (onDark > onWhite) DarkInk else Color.White
}

val LocalGameLook = staticCompositionLocalOf { GameLook(BuiltInThemes.default, dark = false) }

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun GameTheme(spec: ThemeSpec, dark: Boolean, content: @Composable () -> Unit) {
    val look = remember(spec, dark) { GameLook(spec, dark) }
    val p = look.colors
    val container = p.accentSoft
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = container, onPrimaryContainer = p.text,
        secondary = p.highlight, onSecondary = readableOn(p.highlight), secondaryContainer = container, onSecondaryContainer = p.text,
        tertiary = p.highlight, onTertiary = readableOn(p.highlight), tertiaryContainer = p.highlightSoft, onTertiaryContainer = p.text,
        background = p.background, onBackground = p.text, surface = p.background, onSurface = p.text,
        surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.muted, outline = p.outline, outlineVariant = p.outline,
        surfaceContainerLowest = p.background, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surfaceAlt,
        error = p.danger, onError = readableOn(p.danger),
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = container, onPrimaryContainer = p.text,
        secondary = p.highlight, onSecondary = readableOn(p.highlight), secondaryContainer = container, onSecondaryContainer = p.text,
        tertiary = p.highlight, onTertiary = readableOn(p.highlight), tertiaryContainer = p.highlightSoft, onTertiaryContainer = p.text,
        background = p.background, onBackground = p.text, surface = p.background, onSurface = p.text,
        surfaceVariant = p.surfaceAlt, onSurfaceVariant = p.muted, outline = p.outline, outlineVariant = p.outline,
        surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface, surfaceContainer = p.surface,
        surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surfaceAlt,
        error = p.danger, onError = readableOn(p.danger),
    )
    val r = look.radius
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(r * 0.4f), small = RoundedCornerShape(r * 0.7f),
        medium = RoundedCornerShape(r), large = RoundedCornerShape(r * 1.3f), extraLarge = RoundedCornerShape(r * 1.8f),
    )
    CompositionLocalProvider(LocalGameLook provides look) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = AppTypography, content = content)
    }
}
