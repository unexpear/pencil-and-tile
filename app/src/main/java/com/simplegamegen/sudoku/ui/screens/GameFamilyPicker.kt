package com.simplegamegen.sudoku.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.components.OptionGroup

/** Switches between separate engines in a family; ordinary variants keep their existing picker. */
@Composable
internal fun GameFamilyPicker(nav: NavController, game: GameId) {
    val family = game.parent ?: game
    val choices = when (family) {
        GameId.SUDOKU -> listOf(GameId.SUDOKU, GameId.SAMURAI)
        GameId.SOLITAIRE -> listOf(GameId.SOLITAIRE, GameId.SPIDER, GameId.PYRAMID)
        else -> return
    }
    val selected = if (game == GameId.KILLER) GameId.SUDOKU else game
    OptionGroup("Game", choices, selected, {
        if (it == GameId.SOLITAIRE) "Klondike" else it.title
    }) { target ->
        if (target != selected) {
            val current = nav.currentDestination?.id
            nav.navigate(target.route) {
                if (current != null) popUpTo(current) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}
