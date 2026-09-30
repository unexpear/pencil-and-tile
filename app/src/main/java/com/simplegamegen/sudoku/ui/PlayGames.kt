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
import com.simplegamegen.sudoku.cards.FreeCellCodec
import com.simplegamegen.sudoku.cards.FreeCellGame
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
import com.simplegamegen.sudoku.duels.Battleship
import com.simplegamegen.sudoku.duels.BattleshipAi
import com.simplegamegen.sudoku.duels.BattleshipCodec
import com.simplegamegen.sudoku.duels.FiveRow
import com.simplegamegen.sudoku.duels.FiveRowAi
import com.simplegamegen.sudoku.duels.FiveRowCodec
import com.simplegamegen.sudoku.duels.Mancala
import com.simplegamegen.sudoku.duels.MancalaAi
import com.simplegamegen.sudoku.duels.MancalaCodec
import com.simplegamegen.sudoku.tabletop.Chess
import com.simplegamegen.sudoku.tabletop.ChessAi
import com.simplegamegen.sudoku.tabletop.ChessCodec
import com.simplegamegen.sudoku.tabletop.Go
import com.simplegamegen.sudoku.tabletop.GoAi
import com.simplegamegen.sudoku.tabletop.GoCodec
import com.simplegamegen.sudoku.duels.ConnectFour
import com.simplegamegen.sudoku.duels.ConnectFourAi
import com.simplegamegen.sudoku.duels.ConnectFourCodec
import com.simplegamegen.sudoku.duels.Mastermind
import com.simplegamegen.sudoku.duels.MastermindCodec
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
import com.simplegamegen.sudoku.ui.screens.BattleshipScreen
import com.simplegamegen.sudoku.ui.screens.FiveRowScreen
import com.simplegamegen.sudoku.ui.screens.MancalaScreen
import com.simplegamegen.sudoku.grids.BridgesCodec
import com.simplegamegen.sudoku.grids.BridgesGame
import com.simplegamegen.sudoku.grids.LightsCodec
import com.simplegamegen.sudoku.grids.LightsGame
import com.simplegamegen.sudoku.grids.SlidingBlocks
import com.simplegamegen.sudoku.grids.SlidingBlocksCodec
import com.simplegamegen.sudoku.grids.SlitherlinkCodec
import com.simplegamegen.sudoku.grids.SlitherlinkGame
import com.simplegamegen.sudoku.grids.TowersCodec
import com.simplegamegen.sudoku.grids.TowersGame
import com.simplegamegen.sudoku.ui.screens.BridgesScreen
import com.simplegamegen.sudoku.ui.screens.HoneycombScreen
import com.simplegamegen.sudoku.ui.screens.ChessScreen
import com.simplegamegen.sudoku.ui.screens.FreeCellScreen
import com.simplegamegen.sudoku.ui.screens.GoScreen
import com.simplegamegen.sudoku.ui.screens.LightsScreen
import com.simplegamegen.sudoku.ui.screens.SlidingBlocksScreen
import com.simplegamegen.sudoku.ui.screens.SlitherlinkScreen
import com.simplegamegen.sudoku.ui.screens.TowersScreen
import com.simplegamegen.sudoku.ui.screens.LetterDrawScreen
import com.simplegamegen.sudoku.ui.screens.WordLadderScreen
import com.simplegamegen.sudoku.ui.screens.ConnectFourScreen
import com.simplegamegen.sudoku.ui.screens.MastermindScreen
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
import com.simplegamegen.sudoku.ui.screens.LetterfallScreen
import com.simplegamegen.sudoku.wordplay.Letterfall
import com.simplegamegen.sudoku.wordplay.LetterfallCodec
import com.simplegamegen.sudoku.wordplay.Honeycomb
import com.simplegamegen.sudoku.wordplay.HoneycombCodec
import com.simplegamegen.sudoku.wordplay.LetterDraw
import com.simplegamegen.sudoku.wordplay.LetterDrawCodec
import com.simplegamegen.sudoku.wordplay.WordLadder
import com.simplegamegen.sudoku.ui.screens.ThreadsScreen

fun <S> codecOf(encode: (S) -> String, decode: (String) -> S?): GameCodec<S> = object : GameCodec<S> {
    override fun encode(state: S) = encode(state)
    override fun decode(text: String) = decode(text)
}

/** Computer sides for the two-player games; the computer is always player -1. */
object ConnectFourComputer : ComputerPlayer<ConnectFour> {
    override fun needsMove(state: ConnectFour) = !state.over && state.turn == -1
    override fun move(state: ConnectFour) = state.drop(ConnectFourAi.choose(state))!!
    override val pauseMs = 350L
}

object BattleshipComputer : ComputerPlayer<Battleship> {
    override fun needsMove(state: Battleship) = state.awaitingComputer
    override fun move(state: Battleship) = state.receive(BattleshipAi.choose(state))!!
    override val pauseMs = 350L
}

object MancalaComputer : ComputerPlayer<Mancala> {
    override fun needsMove(state: Mancala) = !state.ended && state.turn == -1
    override fun move(state: Mancala) = state.sow(MancalaAi.choose(state))!!
    override val pauseMs = 350L
}

object ChessComputer : ComputerPlayer<Chess> {
    override fun needsMove(state: Chess) = !state.ended && state.turn == -1
    override fun move(state: Chess) = state.play(ChessAi.choose(state))!!
    override val pauseMs = 350L
}

object GoComputer : ComputerPlayer<Go> {
    override fun needsMove(state: Go) = !state.ended && state.turn == Go.WHITE
    override fun move(state: Go) = when (val choice = GoAi.choose(state)) {
        Go.PASS -> state.pass()!!
        else -> state.place(choice)!!
    }
    override val pauseMs = 350L
}

object FiveRowComputer : ComputerPlayer<FiveRow> {
    override fun needsMove(state: FiveRow) = !state.over && state.turn == -1
    override fun move(state: FiveRow) = state.place(FiveRowAi.choose(state))!!
    override val pauseMs = 280L
}

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
    composable(GameId.LETTERFALL.route) {
        LetterfallScreen(nav, playModel<Letterfall>(GameId.LETTERFALL, store, codecOf(LetterfallCodec::encode, LetterfallCodec::decode)), factory)
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
    composable(GameId.CONNECT_FOUR.route) {
        ConnectFourScreen(nav, playModel<ConnectFour>(GameId.CONNECT_FOUR, store, codecOf(ConnectFourCodec::encode, ConnectFourCodec::decode), ConnectFourComputer), factory)
    }
    composable(GameId.MASTERMIND.route) {
        MastermindScreen(nav, playModel<Mastermind>(GameId.MASTERMIND, store, codecOf(MastermindCodec::encode, MastermindCodec::decode)), factory)
    }
    composable(GameId.BATTLESHIP.route) {
        BattleshipScreen(nav, playModel<Battleship>(GameId.BATTLESHIP, store, codecOf(BattleshipCodec::encode, BattleshipCodec::decode), BattleshipComputer), factory)
    }
    composable(GameId.MANCALA.route) {
        MancalaScreen(nav, playModel<Mancala>(GameId.MANCALA, store, codecOf(MancalaCodec::encode, MancalaCodec::decode), MancalaComputer), factory)
    }
    composable(GameId.FIVE_ROW.route) {
        FiveRowScreen(nav, playModel<FiveRow>(GameId.FIVE_ROW, store, codecOf(FiveRowCodec::encode, FiveRowCodec::decode), FiveRowComputer), factory)
    }
    composable(GameId.WORD_LADDER.route) {
        WordLadderScreen(nav, playModel<WordLadder>(GameId.WORD_LADDER, store, codecOf(WordLadder.Companion::encode, WordLadder.Companion::decode)), factory)
    }
    composable(GameId.HONEYCOMB.route) {
        HoneycombScreen(nav, playModel<Honeycomb>(GameId.HONEYCOMB, store, codecOf(HoneycombCodec::encode, HoneycombCodec::decode)), factory)
    }
    composable(GameId.LETTER_DRAW.route) {
        LetterDrawScreen(nav, playModel<LetterDraw>(GameId.LETTER_DRAW, store, codecOf(LetterDrawCodec::encode, LetterDrawCodec::decode)), factory)
    }
    composable(GameId.BRIDGES.route) {
        BridgesScreen(nav, playModel<BridgesGame>(GameId.BRIDGES, store, codecOf(BridgesCodec::encode, BridgesCodec::decode)), factory)
    }
    composable(GameId.SLITHERLINK.route) {
        SlitherlinkScreen(nav, playModel<SlitherlinkGame>(GameId.SLITHERLINK, store, codecOf(SlitherlinkCodec::encode, SlitherlinkCodec::decode)), factory)
    }
    composable(GameId.TOWERS.route) {
        TowersScreen(nav, playModel<TowersGame>(GameId.TOWERS, store, codecOf(TowersCodec::encode, TowersCodec::decode)), factory)
    }
    composable(GameId.LIGHTS.route) {
        LightsScreen(nav, playModel<LightsGame>(GameId.LIGHTS, store, codecOf(LightsCodec::encode, LightsCodec::decode)), factory)
    }
    composable(GameId.CHESS.route) {
        ChessScreen(nav, playModel<Chess>(GameId.CHESS, store, codecOf(ChessCodec::encode, ChessCodec::decode), ChessComputer), factory)
    }
    composable(GameId.FREECELL.route) {
        FreeCellScreen(nav, playModel<FreeCellGame>(GameId.FREECELL, store, codecOf(FreeCellCodec::encode, FreeCellCodec::decode)), factory)
    }
    composable(GameId.SLIDING_BLOCKS.route) {
        SlidingBlocksScreen(nav, playModel<SlidingBlocks>(GameId.SLIDING_BLOCKS, store, codecOf(SlidingBlocksCodec::encode, SlidingBlocksCodec::decode)), factory)
    }
    composable(GameId.GO.route) {
        GoScreen(nav, playModel<Go>(GameId.GO, store, codecOf(GoCodec::encode, GoCodec::decode), GoComputer), factory)
    }
}
