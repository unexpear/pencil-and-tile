package com.simplegamegen.sudoku.ui.i18n

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.simplegamegen.sudoku.data.Language
import java.util.Locale
import androidx.compose.material3.Text as MaterialText

/** Translations bundled in assets/i18n/strings.tsv, one column per language. English needs none. */
object I18n {
    private val catalogs = HashMap<Language, Catalog?>()
    private var table: String? = null

    /** The catalog on screen, for text built outside composition (screen-reader descriptions). */
    @Volatile var active: Catalog? = null

    /** The language to show for a setting: "System default" follows the device when it's a supported language. */
    fun resolve(setting: Language, device: Locale = Locale.getDefault()): Language = when (setting) {
        Language.SYSTEM -> Language.entries.firstOrNull { it != Language.SYSTEM && it.tag == device.language } ?: Language.EN
        else -> setting
    }

    fun locale(language: Language): Locale = when (language) {
        Language.ZH -> Locale.SIMPLIFIED_CHINESE
        Language.JA -> Locale.JAPANESE
        Language.ES -> Locale("es")
        Language.DE -> Locale.GERMAN
        else -> Locale.ENGLISH
    }

    fun catalog(context: Context, language: Language): Catalog? = synchronized(catalogs) {
        if (language == Language.EN || language == Language.SYSTEM) return null
        catalogs.getOrPut(language) {
            val text = table ?: runCatching { context.assets.open("i18n/strings.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull()
                ?.also { table = it } ?: return null
            Catalog(Catalog.parseColumn(text, language.tag))
        }
    }
}

/** The catalog for the language on screen; null means English. */
val LocalCatalog = staticCompositionLocalOf<Catalog?> { null }

/** The language on screen, for number and date formatting and language-specific content. */
val LocalLanguage = staticCompositionLocalOf { Language.EN }

/** Translates [text] into the language on screen. */
fun Catalog?.tr(text: String): String = this?.translate(text) ?: Catalog.stripContext(text)

@Composable
fun tr(text: String): String = LocalCatalog.current.tr(text)

/** Translates a screen-reader description; usable inside semantics blocks, which aren't composable. */
fun say(text: String): String = I18n.active.tr(text)

/** Japanese wraps between phrases rather than inside a word (Android 13 and later). */
private val JapaneseBreaks = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Strict, LineBreak.WordBreak.Phrase)
private val Locales = HashMap<Language, LocaleList>()

/**
 * Tags text with the language on screen: Chinese and Japanese draw some shared characters differently,
 * Japanese needs the tag to wrap between phrases, and long German and Spanish words are hyphenated.
 */
@Composable
fun localized(style: TextStyle): TextStyle {
    val language = LocalLanguage.current
    if (language == Language.EN || language == Language.SYSTEM) return style
    val locales = Locales.getOrPut(language) { LocaleList(I18n.locale(language).toLanguageTag()) }
    return when (language) {
        Language.JA -> style.copy(localeList = locales, lineBreak = JapaneseBreaks)
        Language.DE, Language.ES -> style.copy(localeList = locales, hyphens = Hyphens.Auto)
        else -> style.copy(localeList = locales)
    }
}

/** Material Text that shows its string in the chosen language. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = MaterialText(
    tr(text), modifier, color,
    // Material3 1.4 inserts autoSize (TextAutoSize) before fontSize. Null keeps the old size.
    autoSize = null,
    fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration, textAlign,
    lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout,
    localized(style),
)
