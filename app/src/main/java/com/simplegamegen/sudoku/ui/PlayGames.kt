package com.simplegamegen.sudoku.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.simplegamegen.sudoku.grids.GridCodec
import com.simplegamegen.sudoku.grids.GridGame
import com.simplegamegen.sudoku.ui.screens.GRID_RECORD
import com.simplegamegen.sudoku.ui.screens.GRID_ROUTE
import com.simplegamegen.sudoku.ui.screens.GridScreen
import com.simplegamegen.sudoku.data.ArcadeStore
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.cards.MemoryCodec
import com.simplegamegen.sudoku.cards.MemoryGame
import com.simplegamegen.sudoku.cards.PyramidCodec
import com.simplegamegen.sudoku.cards.PyramidGame
import com.simplegamegen.sudoku.cards.SpiderCodec
import com.simplegamegen.sudoku.cards.SpiderGame
import com.simplegamegen.sudoku.arcade.Game2048
import com.simplegamegen.sudoku.arcade.Game2048Codec
import com.simplegamegen.sudoku.arcade.TetrasCodec
import com.simplegamegen.sudoku.arcade.TetrasGame
import com.simplegamegen.sudoku.duels.DotsAi
import com.simplegamegen.sudoku.duels.DotsCodec
import com.simplegamegen.sudoku.duels.DotsGame
import com.simplegamegen.sudoku.duels.MagnetAi
import com.simplegamegen.sudoku.duels.MagnetCodec
import com.simplegamegen.sudoku.duels.MagnetGame
import com.simplegamegen.sudoku.duels.SproutsAi
import com.simplegamegen.sudoku.duels.SproutsCodec
import com.simplegamegen.sudoku.duels.SproutsGame
import com.simplegamegen.sudoku.ui.screens.AcrosticScreen
import com.simplegamegen.sudoku.ui.screens.BlotwordsScreen
import com.simplegamegen.sudoku.wordplay.BlotCodec
import com.simplegamegen.sudoku.wordplay.Blotwords
import com.simplegamegen.sudoku.ui.screens.DotsScreen
import com.simplegamegen.sudoku.ui.screens.MagnetScreen
import com.simplegamegen.sudoku.ui.screens.SproutsScreen
import com.simplegamegen.sudoku.ui.screens.Game2048Screen
import com.simplegamegen.sudoku.ui.screens.TetrasScreen
import com.simplegamegen.sudoku.ui.screens.MemoryScreen
import com.simplegamegen.sudoku.ui.screens.PyramidScreen
import com.simplegamegen.sudoku.ui.screens.SpiderScreen
import com.simplegamegen.sudoku.ui.screens.CryptogramScreen
import com.simplegamegen.sudoku.ui.screens.ScrambleScreen
import com.simplegamegen.sudoku.grids.HitoriCodec
import com.simplegamegen.sudoku.grids.HitoriGame
import com.simplegamegen.sudoku.grids.Nonogram
import com.simplegamegen.sudoku.grids.NonogramCodec
import com.simplegamegen.sudoku.ui.screens.CodeCrackerScreen
import com.simplegamegen.sudoku.ui.screens.DropquoteScreen
import com.simplegamegen.sudoku.ui.screens.HitoriScreen
import com.simplegamegen.sudoku.ui.screens.NonogramScreen
import com.simplegamegen.sudoku.wordplay.AcrosticCodec
import com.simplegamegen.sudoku.wordplay.CodeCracker
import com.simplegamegen.sudoku.wordplay.CodeCrackerCodec
import com.simplegamegen.sudoku.wordplay.Dropquote
import com.simplegamegen.sudoku.wordplay.DropquoteCodec
import com.simplegamegen.sudoku.wordplay.AcrosticGame
import com.simplegamegen.sudoku.wordplay.Cryptogram
import com.simplegamegen.sudoku.wordplay.CryptogramCodec
import com.simplegamegen.sudoku.wordplay.ScrambleCodec
import com.simplegamegen.sudoku.wordplay.ScrambleGame
import com.simplegamegen.sudoku.wordplay.FiveLetters
import com.simplegamegen.sudoku.wordplay.MeaningCodec
import com.simplegamegen.sudoku.wordplay.MeaningGame
import com.simplegamegen.sudoku.ui.screens.MeaningScreen
import com.simplegamegen.sudoku.wordplay.FiveLettersCodec
import com.simplegamegen.sudoku.wordplay.ThreadsCodec
import com.simplegamegen.sudoku.wordplay.ThreadsGame
import com.simplegamegen.sudoku.ui.screens.FiveLettersScreen
import com.simplegamegen.sudoku.ui.screens.ThreadsScreen

fun <S> codecOf(encode: (S) -> String, decode: (String) -> S?): GameCodec<S> = object : GameCodec<S> {
    override fun encode(state: S) = encode(state)
    override fun decode(text: String) = decode(text)
}

/** Computer sides for the two-player games; the computer is always player -1. */
object DotsComputer : ComputerPlayer<DotsGame> {
    override fun needsMove(state: DotsGame) = !state.over && state.turn == -1
    override fun move(state: DotsGame) = state.draw(DotsAi.choose(state))!!
    override val pauseMs = 350L
}

object MagnetComputer : ComputerPlayer<MagnetGame> {
    override fun needsMove(state: MagnetGame) = !state.over && state.turn == -1
    override fun move(state: MagnetGame) = state.place(MagnetAi.choose(state))!!
    override val pauseMs = 600L
}

object SproutsComputer : ComputerPlayer<SproutsGame> {
    override fun needsMove(state: SproutsGame) = !state.over && state.turn == -1
    override fun move(state: SproutsGame) = state.play(SproutsAi.choose(state))!!
    override val pauseMs = 500L
}

/** The save key doubles as the ViewModel key. */
@Composable
private fun <S : Any> playModel(id: GameId, store: ArcadeStore, codec: GameCodec<S>, computer: ComputerPlayer<S>? = null): PlayViewModel<S> =
    viewModel(key = "play_${id.name}", factory = viewModelFactory { initializer { PlayViewModel("PLAY_${id.name}", store, codec, computer) } })

/** Routes for every game built on PlayViewModel. */
fun NavGraphBuilder.playGames(nav: NavController, store: ArcadeStore, factory: PuzzleFactory) {
    composable("$GRID_ROUTE?new={new}", arguments = listOf(navArgument("new") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
        val vm: PlayViewModel<GridGame> = viewModel(key = "play_$GRID_RECORD", factory = viewModelFactory {
            initializer { PlayViewModel(GridEditorViewModel.PLAY_KEY, store, codecOf(GridCodec::encode, GridCodec::decode)) }
        })
        GridScreen(nav, vm, factory, entry.arguments?.getString("new"))
    }
    composable(GameId.CRYPTOGRAM.route) {
        CryptogramScreen(nav, playModel<Cryptogram>(GameId.CRYPTOGRAM, store, codecOf(CryptogramCodec::encode, CryptogramCodec::decode)), factory)
    }
    composable(GameId.SCRAMBLE.route) {
        ScrambleScreen(nav, playModel<ScrambleGame>(GameId.SCRAMBLE, store, codecOf(ScrambleCodec::encode, ScrambleCodec::decode)), factory)
    }
    composable(GameId.ACROSTIC.route) {
        AcrosticScreen(nav, playModel<AcrosticGame>(GameId.ACROSTIC, store, codecOf(AcrosticCodec::encode, AcrosticCodec::decode)), factory)
    }
    composable(GameId.CODE_CRACKER.route) {
        CodeCrackerScreen(nav, playModel<CodeCracker>(GameId.CODE_CRACKER, store, codecOf(CodeCrackerCodec::encode, CodeCrackerCodec::decode)), factory)
    }
    composable(GameId.DROPQUOTE.route) {
        DropquoteScreen(nav, playModel<Dropquote>(GameId.DROPQUOTE, store, codecOf(DropquoteCodec::encode, DropquoteCodec::decode)), factory)
    }
    composable(GameId.THREADS.route) {
        ThreadsScreen(nav, playModel<ThreadsGame>(GameId.THREADS, store, codecOf(ThreadsCodec::encode, ThreadsCodec::decode)), factory)
    }
    composable(GameId.FIVE_LETTERS.route) {
        FiveLettersScreen(nav, playModel<FiveLetters>(GameId.FIVE_LETTERS, store, codecOf(FiveLettersCodec::encode, FiveLettersCodec::decode)), factory)
    }
    composable(GameId.BLOTWORDS.route) {
        BlotwordsScreen(nav, playModel<Blotwords>(GameId.BLOTWORDS, store, codecOf(BlotCodec::encode, BlotCodec::decode)), factory, store)
    }
    composable(com.simplegamegen.sudoku.ui.screens.BLOT_THEMES_ROUTE) {
        com.simplegamegen.sudoku.ui.blot.BlotThemesScreen(nav, store)
    }
    composable(com.simplegamegen.sudoku.ui.blot.BLOT_THEME_EDIT_ROUTE, arguments = listOf(
        navArgument("id") { type = NavType.StringType; defaultValue = "" },
        navArgument("from") { type = NavType.StringType; defaultValue = "" },
    )) { entry ->
        com.simplegamegen.sudoku.ui.blot.BlotThemeEditor(nav, store,
            entry.arguments?.getString("id")?.takeIf { it.isNotEmpty() }, entry.arguments?.getString("from")?.takeIf { it.isNotEmpty() })
    }
    composable(GameId.LETTER_SPRAWL.route) {
        com.simplegamegen.sudoku.ui.screens.SprawlScreen(nav, playModel<com.simplegamegen.sudoku.wordplay.Sprawl>(GameId.LETTER_SPRAWL, store,
            codecOf(com.simplegamegen.sudoku.wordplay.SprawlCodec::encode, com.simplegamegen.sudoku.wordplay.SprawlCodec::decode)), factory)
    }
    composable(GameId.WORDSWORN.route) {
        com.simplegamegen.sudoku.ui.screens.WordswornScreen(nav, playModel<com.simplegamegen.sudoku.wordplay.Wordsworn>(GameId.WORDSWORN, store,
            codecOf(com.simplegamegen.sudoku.wordplay.WordswornCodec::encode, com.simplegamegen.sudoku.wordplay.WordswornCodec::decode)), factory, store)
    }
    composable(GameId.LONE_LETTER.route) {
        com.simplegamegen.sudoku.ui.screens.LoneLetterScreen(nav, playModel<com.simplegamegen.sudoku.wordplay.LoneLetter>(GameId.LONE_LETTER, store,
            codecOf(com.simplegamegen.sudoku.wordplay.LoneLetterCodec::encode, com.simplegamegen.sudoku.wordplay.LoneLetterCodec::decode)), factory)
    }
    composable(GameId.WORD_QUILT.route) {
        com.simplegamegen.sudoku.ui.screens.QuiltScreen(nav, playModel<com.simplegamegen.sudoku.wordplay.Quilt>(GameId.WORD_QUILT, store,
            codecOf(com.simplegamegen.sudoku.wordplay.QuiltCodec::encode, com.simplegamegen.sudoku.wordplay.QuiltCodec::decode)), factory)
    }
    composable(GameId.WORD_MEANING.route) {
        MeaningScreen(nav, playModel<MeaningGame>(GameId.WORD_MEANING, store, codecOf(MeaningCodec::encode, MeaningCodec::decode)), factory)
    }
    composable(GameId.NONOGRAM.route) {
        NonogramScreen(nav, playModel<Nonogram>(GameId.NONOGRAM, store, codecOf(NonogramCodec::encode, NonogramCodec::decode)), factory)
    }
    composable(GameId.HITORI.route) {
        HitoriScreen(nav, playModel<HitoriGame>(GameId.HITORI, store, codecOf(HitoriCodec::encode, HitoriCodec::decode)), factory)
    }
    composable(GameId.SPIDER.route) {
        SpiderScreen(nav, playModel<SpiderGame>(GameId.SPIDER, store, codecOf(SpiderCodec::encode, SpiderCodec::decode)), factory)
    }
    composable(GameId.PYRAMID.route) {
        PyramidScreen(nav, playModel<PyramidGame>(GameId.PYRAMID, store, codecOf(PyramidCodec::encode, PyramidCodec::decode)), factory)
    }
    composable(GameId.MEMORY.route) {
        MemoryScreen(nav, playModel<MemoryGame>(GameId.MEMORY, store, codecOf(MemoryCodec::encode, MemoryCodec::decode)), factory)
    }
    composable(GameId.G2048.route) {
        Game2048Screen(nav, playModel<Game2048>(GameId.G2048, store, codecOf(Game2048Codec::encode, Game2048Codec::decode)), factory)
    }
    composable(GameId.TETRAS.route) {
        TetrasScreen(nav, playModel<TetrasGame>(GameId.TETRAS, store, codecOf(TetrasCodec::encode, TetrasCodec::decode)), factory)
    }
    composable(GameId.DOTS.route) {
        DotsScreen(nav, playModel<DotsGame>(GameId.DOTS, store, codecOf(DotsCodec::encode, DotsCodec::decode), DotsComputer), factory)
    }
    composable(GameId.MAGNETS.route) {
        MagnetScreen(nav, playModel<MagnetGame>(GameId.MAGNETS, store, codecOf(MagnetCodec::encode, MagnetCodec::decode), MagnetComputer), factory)
    }
    composable(GameId.SPROUTS.route) {
        SproutsScreen(nav, playModel<SproutsGame>(GameId.SPROUTS, store, codecOf(SproutsCodec::encode, SproutsCodec::decode), SproutsComputer), factory)
    }
}
