package com.simplegamegen.sudoku

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.simplegamegen.sudoku.logic.LogicKind
import com.simplegamegen.sudoku.model.VariantType
import com.simplegamegen.sudoku.tabletop.TableGame
import com.simplegamegen.sudoku.ui.AppearanceViewModel
import com.simplegamegen.sudoku.ui.ArcadeGame
import com.simplegamegen.sudoku.ui.ArcadeViewModel
import com.simplegamegen.sudoku.ui.GameViewModel
import com.simplegamegen.sudoku.ui.LogicViewModel
import com.simplegamegen.sudoku.ui.playGames
import com.simplegamegen.sudoku.ui.TableViewModel
import com.simplegamegen.sudoku.ui.WordGameViewModel
import com.simplegamegen.sudoku.ui.components.ProvideSystemBars
import com.simplegamegen.sudoku.ui.components.LocalOpenTutorial
import com.simplegamegen.sudoku.ui.LocalPlayer
import com.simplegamegen.sudoku.ui.i18n.I18n
import com.simplegamegen.sudoku.ui.i18n.LocalCatalog
import com.simplegamegen.sudoku.ui.i18n.LocalLanguage
import androidx.compose.runtime.remember
import com.simplegamegen.sudoku.ui.screens.OutOfHintsDialog
import com.simplegamegen.sudoku.ui.screens.ProfileScreen
import com.simplegamegen.sudoku.ui.screens.GridListScreen
import com.simplegamegen.sudoku.ui.screens.GridEditorScreen
import com.simplegamegen.sudoku.ui.GridEditorViewModel
import com.simplegamegen.sudoku.ui.screens.SettingsScreen
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.tutorial.TutorialHubScreen
import com.simplegamegen.sudoku.ui.tutorial.TutorialScreen
import com.simplegamegen.sudoku.ui.tutorial.tutorialRoute
import androidx.compose.runtime.CompositionLocalProvider
import com.simplegamegen.sudoku.ui.screens.AppearanceScreen
import com.simplegamegen.sudoku.ui.screens.ArcadeScreen
import com.simplegamegen.sudoku.ui.screens.CollectionGuideScreen
import com.simplegamegen.sudoku.ui.screens.GameScreen
import com.simplegamegen.sudoku.ui.screens.HomeScreen
import com.simplegamegen.sudoku.ui.screens.LogicScreen
import com.simplegamegen.sudoku.ui.screens.StatsScreen
import com.simplegamegen.sudoku.ui.screens.SudokuHubScreen
import com.simplegamegen.sudoku.ui.screens.TableScreen
import com.simplegamegen.sudoku.ui.screens.ThemeEditorScreen
import com.simplegamegen.sudoku.ui.screens.WordGameScreen
import com.simplegamegen.sudoku.ui.theme.DarkMode
import com.simplegamegen.sudoku.ui.theme.GameTheme
import com.simplegamegen.sudoku.words.WordGame

class MainActivity : ComponentActivity() {
    /** Set once the screen has been composed with the saved settings, so the first frame is in the right language. */
    @Volatile private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holdFirstFrame()
        setContent {
            val app = application as SudokuApp
            val appearance: AppearanceViewModel = viewModel(factory = viewModelFactory { initializer { AppearanceViewModel(app.appearance) } })
            val settings by appearance.settings.collectAsStateWithLifecycle()
            val dark = when (settings.darkMode) {
                DarkMode.SYSTEM -> isSystemInDarkTheme()
                DarkMode.LIGHT -> false
                DarkMode.DARK -> true
            }
            GameTheme(settings.selected, dark) {
                ProvideSystemBars({ statusOnDark, navOnDark -> applySystemBars(statusOnDark, navOnDark) }) {
                    val nav = rememberNavController()
                    val vm: GameViewModel = viewModel(factory = viewModelFactory {
                        initializer { GameViewModel(app.repository, generatePuzzle = { size, difficulty, variant, _ -> app.puzzles.sudoku(size, difficulty, variant) }) }
                    })
                    val crossword: WordGameViewModel = viewModel(key = "crossword", factory = viewModelFactory {
                        initializer { WordGameViewModel(WordGame.CROSSWORD, app.wordGames, createPuzzle = app.puzzles::word) }
                    })
                    val wordSearch: WordGameViewModel = viewModel(key = "wordsearch", factory = viewModelFactory {
                        initializer { WordGameViewModel(WordGame.WORD_SEARCH, app.wordGames, createPuzzle = app.puzzles::word) }
                    })
                    val hangman: ArcadeViewModel = viewModel(key = "hangman", factory = viewModelFactory {
                        initializer { ArcadeViewModel(ArcadeGame.HANGMAN, app.collection, app.puzzles) }
                    })
                    val mahjong: ArcadeViewModel = viewModel(key = "mahjong", factory = viewModelFactory {
                        initializer { ArcadeViewModel(ArcadeGame.MAHJONG, app.collection, app.puzzles) }
                    })
                    val tables = TableGame.entries.associateWith { game ->
                        viewModel<TableViewModel>(key = "table_${game.name}", factory = viewModelFactory {
                            initializer { TableViewModel(game, app.collection, app.puzzles) }
                        })
                    }
                    val gridEditor: GridEditorViewModel = viewModel(key = "grid_editor", factory = viewModelFactory {
                        initializer { GridEditorViewModel(app.collection) }
                    })
                    val logic = LogicKind.entries.associateWith { kind ->
                        viewModel<LogicViewModel>(key = "logic_${kind.name}", factory = viewModelFactory {
                            initializer { LogicViewModel(kind, app.collection, app.puzzles) }
                        })
                    }
                    val playerState by app.player.records.collectAsStateWithLifecycle()
                    val outOfHints by app.player.outOfHints.collectAsStateWithLifecycle()
                    LaunchedEffect(playerState.settings.keepScreenOn) {
                        if (playerState.settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    val language = I18n.resolve(playerState.settings.language)
                    val catalog = remember(language) { I18n.catalog(applicationContext, language) }
                    I18n.active = catalog
                    val loaded by app.player.loaded.collectAsStateWithLifecycle()
                    if (loaded) ready = true
                    CompositionLocalProvider(LocalOpenTutorial provides { id -> nav.navigate(tutorialRoute(id)) }, LocalPlayer provides app.player,
                        LocalCatalog provides catalog, LocalLanguage provides language) {
                    if (outOfHints) OutOfHintsDialog(onSettings = { app.player.dismissOutOfHints(); nav.navigate("settings") }, onDismiss = app.player::dismissOutOfHints)
                    NavHost(
                        navController = nav, startDestination = "home",
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    ) {
                        composable("home") { HomeScreen(nav, vm) }
                        composable("sudoku?variant={variant}", arguments = listOf(navArgument("variant") {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        })) { entry ->
                            val variant = entry.arguments?.getString("variant")?.let { v -> VariantType.entries.firstOrNull { it.name == v } }
                            SudokuHubScreen(nav, vm, variant)
                        }
                        composable("game") { GameScreen(nav, vm) }
                        composable("stats") { StatsScreen(nav, vm) }
                        composable("crossword") { WordGameScreen(nav, crossword) }
                        composable("wordsearch") { WordGameScreen(nav, wordSearch) }
                        composable("hangman") { ArcadeScreen(nav, hangman) }
                        composable("mahjong") { ArcadeScreen(nav, mahjong) }
                        TableGame.entries.forEach { game -> composable("table_${game.name}") { TableScreen(nav, tables.getValue(game)) } }
                        LogicKind.entries.forEach { kind -> composable("logic_${kind.name}") { LogicScreen(nav, logic.getValue(kind)) } }
                        playGames(nav, app.collection, app.puzzles)
                        composable("guide") { CollectionGuideScreen(nav) }
                        composable("themes") { AppearanceScreen(nav, appearance) }
                        composable("theme_edit/{id}") { entry ->
                            ThemeEditorScreen(nav, appearance, entry.arguments?.getString("id")?.takeIf { it != "new" })
                        }
                        composable("tutorials") { TutorialHubScreen(nav) }
                        composable("profile") { ProfileScreen(nav) }
                        composable("grids") { GridListScreen(nav, gridEditor) }
                        composable("grid_edit") { GridEditorScreen(nav, gridEditor) }
                        composable("settings") { SettingsScreen(nav) }
                        composable("credits") { com.simplegamegen.sudoku.ui.screens.CreditsScreen(nav) }
                        composable("tutorial/{game}?hub={hub}", arguments = listOf(
                            navArgument("game") { type = NavType.StringType },
                            navArgument("hub") { type = NavType.BoolType; defaultValue = false },
                        )) { entry ->
                            val id = GameId.entries.firstOrNull { it.name == entry.arguments?.getString("game") } ?: GameId.SUDOKU
                            TutorialScreen(nav, id, entry.arguments?.getBoolean("hub") ?: false)
                        }
                    }
                    }
                }
            }
        }
    }

    /** Keeps the launch screen up until the saved language is on screen (at most 1.5 s). */
    private fun holdFirstFrame() {
        val content = findViewById<View>(android.R.id.content)
        content.postDelayed({ ready = true }, 1500)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!ready) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }

    override fun onStop() {
        super.onStop()
        // Timer ticks are saved in batches; make sure the latest ones reach storage.
        (application as SudokuApp).player.flush()
    }

    /** Light icons on dark bars and dark icons on light bars; the bars themselves stay transparent. */
    private fun applySystemBars(statusOnDark: Boolean, navOnDark: Boolean) {
        fun style(onDark: Boolean) = if (onDark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style(statusOnDark), navigationBarStyle = style(navOnDark))
    }
}
