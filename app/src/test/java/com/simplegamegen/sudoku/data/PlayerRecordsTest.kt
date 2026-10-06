package com.simplegamegen.sudoku.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerRecordsTest {
    @Test fun `sessions start, tick and finish once`() {
        var r = PlayerRecords().start("HITORI", "7", "Hard", 2, now = 100)
        r = r.tick("HITORI", "7", 5, now = 105).tick("HITORI", "other", 50, now = 106)
        assertEquals(5, r.sessions.getValue("HITORI").seconds)
        val done = r.finish("HITORI", "7", Outcome(Result.WON), now = 200)
        assertTrue(done.sessions.isEmpty())
        assertEquals(1, done.history.size)
        with(done.history.single()) { assertEquals("Hard", level); assertEquals(5, seconds); assertTrue(timed); assertEquals(Result.WON, result) }
        assertEquals(PlayerRecords.STARTING_HINTS + 3, done.wallet, "a Hard win earns 3 hints")
        assertSame(done, done.finish("HITORI", "7", Outcome(Result.WON), now = 300), "a finished game is only recorded once")
    }

    @Test fun `ensure keeps the running session and replaces a different game`() {
        val r = PlayerRecords().start("MINES", "a", "Easy", 0, 1).tick("MINES", "a", 9, 2)
        assertSame(r, r.ensure("MINES", "a", "Easy", 0, 3))
        assertEquals(0, r.ensure("MINES", "b", "Easy", 0, 3).sessions.getValue("MINES").seconds)
    }

    @Test fun `timed choice follows the per-game override, then the default`() {
        var r = PlayerRecords()
        assertTrue(r.timedFor("G2048"))
        r = r.copy(settings = r.settings.copy(timedByDefault = false))
        assertFalse(r.timedFor("G2048"))
        r = r.copy(timedChoice = mapOf("G2048" to true))
        assertTrue(r.start("G2048", "1", "Easy", 0, 0).sessions.getValue("G2048").timed)
        assertFalse(r.start("TETRAS", "1", "Easy", 0, 0).sessions.getValue("TETRAS").timed)
    }

    @Test fun `earned hints cost one and stop at zero`() {
        var r = PlayerRecords(wallet = 1).copy(settings = PlayerSettings(earnHints = true)).start("KAKURO", "k", "Easy", 0, 0)
        r = r.spendHint("KAKURO")!!
        assertEquals(0, r.wallet)
        assertEquals(1, r.sessions.getValue("KAKURO").hints)
        assertNull(r.spendHint("KAKURO"))
        val free = r.copy(settings = r.settings.copy(earnHints = false)).spendHint("KAKURO")!!
        assertEquals(0, free.wallet, "free hints don't touch the wallet")
        assertEquals(2, free.sessions.getValue("KAKURO").hints)
    }

    @Test fun `tutorial reward is paid once per game`() {
        val r = PlayerRecords(wallet = 0).rewardTutorial("SUDOKU").rewardTutorial("SUDOKU").rewardTutorial("MINES")
        assertEquals(2, r.wallet)
    }

    @Test fun `best times count only timed wins and scores respect their direction`() {
        var r = PlayerRecords()
        fun play(game: String, key: String, level: String, seconds: Long, timed: Boolean, result: Result, score: Long? = null) {
            r = r.copy(timedChoice = r.timedChoice + (game to timed)).start(game, key, level, 0, 0).clock(game, key, seconds, 0)
                .finish(game, key, Outcome(result, score), 0)
        }
        play("SUDOKU", "1", "Easy", 300, true, Result.WON)
        play("SUDOKU", "2", "Easy", 200, false, Result.WON)
        play("SUDOKU", "3", "Easy", 100, true, Result.LOST)
        play("SUDOKU", "4", "Hard", 900, true, Result.WON)
        assertEquals(mapOf("Easy" to 300L, "Hard" to 900L), r.bestTimes("SUDOKU"))
        play("MEMORY", "m1", "4x4", 10, true, Result.WON, 20)
        play("MEMORY", "m2", "4x4", 10, true, Result.WON, 14)
        assertEquals(mapOf("4x4" to 14L), r.bestScores("MEMORY", lowerIsBetter = true))
        play("TETRAS", "t1", "Easy", 10, true, Result.FINISHED, 500)
        play("TETRAS", "t2", "Easy", 10, true, Result.FINISHED, 1200)
        assertEquals(mapOf("Easy" to 1200L), r.bestScores("TETRAS", lowerIsBetter = false))
        assertEquals(1 to 2, r.streaks("SUDOKU"), "newest first: the Hard win, then a loss, then two wins")
    }

    @Test fun `clearing started and finished games keeps the account`() {
        var r = PlayerRecords(wallet = 4, timedChoice = mapOf("DOTS" to false), tutorialsRewarded = setOf("SUDOKU"))
            .copy(settings = PlayerSettings(name = "Ana", language = Language.DE, earnHints = true))
        r = r.start("DOTS", "1", "Easy", 0, 1).finish("DOTS", "1", Outcome(Result.WON), 2)
        r = r.start("MINES", "2", "Hard", 2, 3)
        val cleared = r.clearStartedAndFinished()
        assertTrue(cleared.sessions.isEmpty())
        assertTrue(cleared.history.isEmpty())
        assertEquals(r.settings, cleared.settings)
        assertEquals(r.wallet, cleared.wallet)
        assertEquals(r.timedChoice, cleared.timedChoice)
        assertEquals(r.tutorialsRewarded, cleared.tutorialsRewarded)
        assertEquals(cleared, PlayerCodec.decode(PlayerCodec.encode(cleared)))
    }

    @Test fun `codec round-trips and survives damage`() {
        var r = PlayerRecords(wallet = 7, timedChoice = mapOf("DOTS" to false), tutorialsRewarded = setOf("SUDOKU", "MINES"))
            .copy(settings = PlayerSettings(name = "Ana\tB", language = Language.JA, timedByDefault = false, showTimer = false, earnHints = true, keepScreenOn = true, offerTutorials = false))
        r = r.start("DOTS", "9", "Easy · 3×3", 0, 10).tick("DOTS", "9", 3, 11)
        r = r.start("MEMORY", "5", "Hard", 2, 20).finish("MEMORY", "5", Outcome(Result.WON, 18), 30)
        val decoded = PlayerCodec.decode(PlayerCodec.encode(r))
        assertEquals(r.copy(settings = r.settings.copy(name = "Ana B")), decoded)
        val damaged = PlayerCodec.encode(r).replace("\nW\t", "\nW\tnot-a-number\t") + "\nH\tbroken"
        val partial = PlayerCodec.decode(damaged)
        assertEquals(PlayerRecords.STARTING_HINTS, partial.wallet)
        assertEquals(1, partial.history.size)
        assertEquals(PlayerRecords(), PlayerCodec.decode("P0\nW\t5"))
        assertEquals(PlayerRecords(), PlayerCodec.decode(null))
    }

    private class MemoryStorage(var text: String? = null) : PlayerStorage {
        var writes = 0
        override suspend fun read(): String? = text
        override suspend fun write(text: String) { this.text = text; writes++ }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `service replays early changes onto loaded records and batches ticks`() = runTest {
        val stored = PlayerRecords(wallet = 9)
        val storage = MemoryStorage(PlayerCodec.encode(stored))
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val service = PlayerService(storage, scope, now = { 1000 }, batchMs = 10_000)
        service.update { r, now -> r.start("MINES", "x", "Easy", 0, now) }
        scope.advanceUntilIdle()
        assertEquals(9, service.records.value.wallet, "the stored wallet wasn't overwritten")
        assertNotNull(service.records.value.sessions["MINES"], "the early change was replayed")
        val writes = storage.writes
        repeat(5) { service.update(urgent = false) { r, now -> r.tick("MINES", "x", 1, now) } }
        scope.testScheduler.advanceTimeBy(5_000); scope.testScheduler.runCurrent()
        assertEquals(writes, storage.writes, "ticks wait for the batch")
        scope.advanceUntilIdle()
        assertEquals(writes + 1, storage.writes)
        assertEquals(5, PlayerCodec.decode(storage.text).sessions.getValue("MINES").seconds)
        var ran = false
        service.update { r, _ -> r.copy(wallet = 0, settings = r.settings.copy(earnHints = true)) }
        service.useHint("MINES") { ran = true }
        assertFalse(ran)
        assertTrue(service.outOfHints.value)
    }
}
