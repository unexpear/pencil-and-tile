package com.simplegamegen.sudoku.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.simplegamegen.sudoku.data.EnglishLexiconAsset
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.tr
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.words.WordGlance
import com.simplegamegen.sudoku.ui.words.displayLemma
import com.simplegamegen.sudoku.ui.words.glance
import com.simplegamegen.sudoku.wordplay.LexiconSense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.material3.Text as PlainText

/**
 * One shared lookup for the process. [override] is for tests and previews; otherwise this opens
 * [EnglishLexiconAsset.shared] the first time a letter game asks, off the main thread.
 */
@Composable
fun rememberLexiconLookup(override: ((String) -> List<LexiconSense>)? = null): (String) -> List<LexiconSense> {
    val app = LocalContext.current.applicationContext
    val lookup = override ?: remember(app) { { lemma: String -> EnglishLexiconAsset.shared(app).lookup(lemma) } }
    if (override == null) {
        LaunchedEffect(app) { withContext(Dispatchers.IO) { EnglishLexiconAsset.shared(app) } }
    }
    return lookup
}

/**
 * The word on the dictionary card. Cleared when [gameKey] changes (a new puzzle or run)
 * so a saved game doesn't reopen the previous word.
 */
@Composable
fun rememberShownWord(gameKey: Long): MutableState<String?> {
    val shown = rememberSaveable { mutableStateOf<String?>(null) }
    var bound by rememberSaveable { mutableStateOf("") }
    val key = gameKey.toString()
    if (bound != key) {
        bound = key
        shown.value = null
    }
    return shown
}

/**
 * Compact, dismissible dictionary card. It sits in the play column, so the board stays tappable.
 * An unknown word still names itself and says there is no definition.
 */
@Composable
fun WordDefinitionCard(
    lemma: String,
    lookup: (String) -> List<LexiconSense>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLookup by rememberUpdatedState(lookup)
    val loaded by produceState<WordGlance?>(null, lemma) {
        value = withContext(Dispatchers.IO) { glance(lemma, currentLookup(lemma)) }
    }
    val c = LocalGameLook.current.colors
    Surface(
        modifier.fillMaxWidth(),
        color = c.surface,
        contentColor = c.text,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, c.accent.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(start = 14.dp, top = 2.dp, end = 2.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlainText(
                    displayLemma(lemma),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss) {
                    Icon(GameIcons.Close, contentDescription = tr("Dismiss"))
                }
            }
            val card = loaded
            when {
                card == null -> Text("…", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                card.senses.isEmpty() -> Text("No definition", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                else -> {
                    card.senses.forEach { sense ->
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            PlainText(
                                sense.pos.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                                style = MaterialTheme.typography.labelMedium,
                                color = c.accent,
                            )
                            PlainText(
                                sense.definition,
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.text,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (card.moreSenses > 0) {
                        Text(
                            if (card.moreSenses == 1) "1 more sense" else "${card.moreSenses} more senses",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.muted,
                        )
                    }
                    card.example?.let { example ->
                        PlainText(
                            "“$example”",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = c.muted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        if (card.exampleSource == "tatoeba_cc0") "Open English WordNet · Tatoeba" else "Open English WordNet",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.muted,
                    )
                }
            }
        }
    }
}
