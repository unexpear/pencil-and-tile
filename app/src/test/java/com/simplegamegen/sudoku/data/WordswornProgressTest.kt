package com.simplegamegen.sudoku.data

import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.wordplay.Hero
import com.simplegamegen.sudoku.wordplay.Monster
import com.simplegamegen.sudoku.wordplay.Wordsworn
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WordswornProgressTest {
    private class Memory : ArcadeStore {
        val entries = mutableMapOf<String, String>()
        override suspend fun load(key: String) = entries[key]
        override suspend fun save(key: String, encoded: String) { entries[key] = encoded }
    }
    private fun victory(hero: Hero) = Wordsworn.start(1, LogicLevel.MEDIUM).choose(hero.ordinal)!!
        .copy(fight = 5, monster = Monster.WORD_EATER, stage = 2, monsterHp = 0, hp = 1, offer = null)

    @Test fun `victories unlock independently and survive another repository instance`() = runTest {
        val store = Memory()
        val progress = WordswornProgress(store)
        assertTrue(progress.recordVictory(victory(Hero.KNIGHT)))
        assertTrue(progress.recordVictory(victory(Hero.WITCH)))
        assertTrue(progress.recordVictory(victory(Hero.KNIGHT)))
        assertEquals(setOf(Hero.KNIGHT, Hero.WITCH), WordswornProgress(store).unlocked())
        assertEquals(2, store.entries.size)
    }

    @Test fun `losing and unfinished runs do not unlock cores`() = runTest {
        val progress = WordswornProgress(Memory())
        assertFalse(progress.recordVictory(victory(Hero.KNIGHT).copy(hp = 0)))
        assertFalse(progress.recordVictory(victory(Hero.KNIGHT).copy(monsterHp = 1)))
        assertFalse(progress.recordVictory(Wordsworn.start(1, LogicLevel.EASY)))
        assertTrue(progress.unlocked().isEmpty())
    }
}
