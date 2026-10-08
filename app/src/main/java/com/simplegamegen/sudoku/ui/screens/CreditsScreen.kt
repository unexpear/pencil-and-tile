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
    Credit("Open English WordNet", "Definitions and example sentences in the English lexicon and in Word Meaning; crossword and acrostic clues; synonyms and opposites for Word Meaning; the everyday word lists and Lone Letter's categories",
        "CC BY 4.0, based on Princeton WordNet (WordNet licence)", "licenses/open-english-wordnet.txt",
        "Open English WordNet by the Open English WordNet team; WordNet 3.0 © 2006 by Princeton University."),
    Credit("ENABLE", "Which words letter games accept, which words the English lexicon marks as playable, and the words Five Letters, word scramble and code cracker draw from",
        "Public domain", "licenses/enable.txt",
        "Enhanced North American Benchmark Lexicon, compiled by Alan Beale."),
    Credit("Tatoeba", "Example sentences in the English lexicon when Open English WordNet has none for that sense, and sayings for Cryptogram and Dropquote",
        "CC0 1.0 (public domain dedication)", "licenses/tatoeba-cc0.txt",
        "English sentences by Tatoeba contributors."),
    Credit("all-MiniLM-L6-v2", "The on-device model that helps judge Word Meaning answers", "Apache License 2.0",
        "licenses/apache-2.0.txt", "Sentence-transformers model by Nils Reimers and contributors, based on Microsoft MiniLM."),
    Credit("ONNX Runtime", "Runs the on-device model", "MIT License", "licenses/mit-onnxruntime.txt", "© Microsoft Corporation."),
    Credit("AndroidX, Jetpack Compose and Kotlin", "The app's building blocks", "Apache License 2.0", "licenses/apache-2.0.txt",
        "© The Android Open Source Project and JetBrains s.r.o."),
    Credit("Original Battleship Pieces", "The Battleship hulls and the hit and miss pins", "Creative Commons Attribution",
        "licenses/battleship-pieces-cc-by.txt",
        "By MZimb, Thingiverse thing 5190846, resized from Mattwall, thing 4244260. Adapted to the classic fleet."),
    Credit("SceneView", "The 3D view in Knife Flip", "Apache License 2.0", "licenses/apache-2.0.txt",
        "© Thomas Gorisse and SceneView contributors. io.github.sceneview:sceneview 4.18.0."),
    Credit("Filament", "Physically based rendering in Knife Flip", "Apache License 2.0", "licenses/apache-2.0.txt",
        "© Google. Filament 1.71.5, used through SceneView."),
    Credit("Kiara Interior", "Lighting and reflections in Knife Flip. The room is modelled; the photo is not the background.", "CC0 1.0 (public domain dedication)",
        "licenses/cc0-1.0.txt", "HDRI by Greg Zaal, Poly Haven. The 1K file is used."),
    Credit("Wood051", "Wood grain on the Knife Flip handles", "CC0 1.0 (public domain dedication)",
        "licenses/cc0-1.0.txt", "By ambientCG (Lennart Demes). Colour, normal and roughness maps. Handles use a 512 px copy."),
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
