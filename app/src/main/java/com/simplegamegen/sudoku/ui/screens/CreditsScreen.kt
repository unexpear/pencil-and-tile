package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.theme.LocalGameLook

/** A third-party part of the app, what it's used for, and the licence file shipped in assets/licenses. */
private data class Credit(val name: String, val use: String, val license: String, val file: String, val notice: String = "")

/** The app's own licence. */
private val App = Credit("Pencil & Tile", "Free software: you may share and change it under the GNU GPL. Source code: github.com/unexpear/pencil-and-tile",
    "GNU General Public License 3.0 or later", "licenses/gpl-3.0.txt", "© 2026 unexpear and contributors. It comes with no warranty.")

private val Credits = listOf(
    Credit("Open English WordNet", "Definitions, example sentences, synonyms and opposites in Word Meaning; the everyday word lists and Lone Letter's categories",
        "CC BY 4.0, based on Princeton WordNet (WordNet licence)", "licenses/open-english-wordnet.txt",
        "Open English WordNet by the Open English WordNet team; WordNet 3.0 © 2006 by Princeton University."),
    Credit("all-MiniLM-L6-v2", "The on-device model that helps judge Word Meaning answers", "Apache License 2.0",
        "licenses/apache-2.0.txt", "Sentence-transformers model by Nils Reimers and contributors, based on Microsoft MiniLM."),
    Credit("ONNX Runtime", "Runs the on-device model", "MIT License", "licenses/mit-onnxruntime.txt", "© Microsoft Corporation."),
    Credit("AndroidX, Jetpack Compose and Kotlin", "The app's building blocks", "Apache License 2.0", "licenses/apache-2.0.txt",
        "© The Android Open Source Project and JetBrains s.r.o."),
)

@Composable
fun CreditsScreen(nav: NavController) {
    val c = LocalGameLook.current.colors
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    GameScaffold(title = "Credits and licenses", onBack = { nav.popBackStack() }) {
        (listOf(App) + Credits).forEachIndexed { i, credit ->
            if (i == 1) Text("Puzzles, clues, word lists, art and tutorials are made for this app. These parts come from others, with thanks:",
                style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Surface(color = c.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.Text(credit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.text)
                    Text(credit.use, style = MaterialTheme.typography.bodyMedium, color = c.text)
                    Text(credit.license, style = MaterialTheme.typography.bodySmall, color = c.accent)
                    if (credit.notice.isNotEmpty()) androidx.compose.material3.Text(credit.notice, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    TextButton(onClick = { open = if (open == credit.name) null else credit.name }) {
                        Text(if (open == credit.name) "Hide licence text" else "Show licence text")
                    }
                    if (open == credit.name) {
                        val text = remember(credit.file) { runCatching { context.assets.open(credit.file).bufferedReader().use { it.readText() } }.getOrDefault("") }
                        androidx.compose.material3.Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, color = c.text)
                    }
                }
            }
        }
    }
}
