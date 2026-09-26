package com.simplegamegen.sudoku.data

import com.simplegamegen.sudoku.wordplay.Hero
import com.simplegamegen.sudoku.wordplay.Wordsworn

/** Separate per-hero records avoid overwriting other heroes' earned options. */
class WordswornProgress(private val store: ArcadeStore) {
    suspend fun unlocked(): Set<Hero> = Hero.entries.filter { store.load(key(it)) == "1" }.toSet()

    suspend fun recordVictory(game: Wordsworn): Boolean {
        val hero = game.hero ?: return false
        if (!game.won || game.book != 2) return false
        store.save(key(hero), "1")
        return true
    }

    private fun key(hero: Hero) = "wordsworn:core-unlock:${hero.name}"
}
