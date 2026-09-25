package com.simplegamegen.sudoku.data

/** How a finished game ended. FINISHED is for games without a winner, like a Tetras run. */
enum class Result { WON, LOST, DRAW, FINISHED }

/** A finished game's result and optional score, reported by the game screen. */
data class Outcome(val result: Result, val score: Long? = null)

/** A game in progress. [key] identifies the particular deal or puzzle so a new game starts a new session. */
data class Session(
    val game: String,
    val key: String,
    val level: String,
    val levelIndex: Int,
    val timed: Boolean,
    val startedAt: Long,
    val lastPlayed: Long,
    val seconds: Long = 0,
    val hints: Int = 0,
)

/** One finished game in the history. */
data class Finished(
    val game: String,
    val level: String,
    val levelIndex: Int,
    val result: Result,
    val timed: Boolean,
    val seconds: Long,
    val hints: Int,
    val score: Long?,
    val at: Long,
)

enum class Language(val tag: String, val native: String) {
    SYSTEM("system", "System default"), EN("en", "English"), ZH("zh", "简体中文"), JA("ja", "日本語"), ES("es", "Español"), DE("de", "Deutsch")
}

data class PlayerSettings(
    val name: String = "Player",
    val language: Language = Language.SYSTEM,
    val timedByDefault: Boolean = true,
    val showTimer: Boolean = true,
    /** When on, hints cost one from the hint wallet, which fills up by winning. */
    val earnHints: Boolean = false,
    val keepScreenOn: Boolean = false,
    val offerTutorials: Boolean = true,
)

/** Everything the profile, settings and high-score screens show. Pure data; see [PlayerCodec] for saving. */
data class PlayerRecords(
    val settings: PlayerSettings = PlayerSettings(),
    val sessions: Map<String, Session> = emptyMap(),
    val history: List<Finished> = emptyList(),
    val wallet: Int = STARTING_HINTS,
    /** Per-game timed/untimed choice, overriding the default. */
    val timedChoice: Map<String, Boolean> = emptyMap(),
    val tutorialsRewarded: Set<String> = emptySet(),
) {
    fun timedFor(game: String): Boolean = timedChoice[game] ?: settings.timedByDefault

    /** Starts (or restarts) the session for [game]. */
    fun start(game: String, key: String, level: String, levelIndex: Int, now: Long): PlayerRecords =
        copy(sessions = sessions + (game to Session(game, key, level, levelIndex, timedFor(game), now, now)))

    /** Makes sure the game on screen has a session, e.g. a game saved before records existed. */
    fun ensure(game: String, key: String, level: String, levelIndex: Int, now: Long): PlayerRecords =
        if (sessions[game]?.key == key) this else start(game, key, level, levelIndex, now)

    fun tick(game: String, key: String, seconds: Long, now: Long): PlayerRecords {
        val s = sessions[game]?.takeIf { it.key == key } ?: return this
        return copy(sessions = sessions + (game to s.copy(seconds = s.seconds + seconds, lastPlayed = now)))
    }

    /** Sets the running time directly (Sudoku keeps its own clock). */
    fun clock(game: String, key: String, seconds: Long, now: Long): PlayerRecords {
        val s = sessions[game]?.takeIf { it.key == key } ?: return this
        return if (s.seconds == seconds) this else copy(sessions = sessions + (game to s.copy(seconds = seconds, lastPlayed = now)))
    }

    fun touch(game: String, key: String, now: Long): PlayerRecords {
        val s = sessions[game]?.takeIf { it.key == key } ?: return this
        return copy(sessions = sessions + (game to s.copy(lastPlayed = now)))
    }

    /**
     * Pays for a hint. Returns null when hints must be earned and the wallet is empty.
     * The hint is counted on the game's session either way.
     */
    fun spendHint(game: String): PlayerRecords? {
        if (settings.earnHints && wallet <= 0) return null
        val counted = sessions[game]?.let { copy(sessions = sessions + (game to it.copy(hints = it.hints + 1))) } ?: this
        return if (settings.earnHints) counted.copy(wallet = wallet - 1) else counted
    }

    /** Ends the session for [key] once; later reports of the same finished game are ignored. */
    fun finish(game: String, key: String, outcome: Outcome, now: Long): PlayerRecords {
        val s = sessions[game]?.takeIf { it.key == key } ?: return this
        val done = Finished(game, s.level, s.levelIndex, outcome.result, s.timed, s.seconds, s.hints, outcome.score, now)
        return copy(sessions = sessions - game, history = (listOf(done) + history).take(HISTORY_LIMIT),
            wallet = (wallet + reward(done)).coerceAtMost(WALLET_LIMIT))
    }

    /** One hint for finishing a game's tutorial the first time. */
    fun rewardTutorial(game: String): PlayerRecords =
        if (game in tutorialsRewarded) this else copy(tutorialsRewarded = tutorialsRewarded + game, wallet = (wallet + 1).coerceAtMost(WALLET_LIMIT))

    val wins: List<Finished> get() = history.filter { it.result == Result.WON }
    val secondsPlayed: Long get() = history.sumOf { it.seconds } + sessions.values.sumOf { it.seconds }

    /** Best (lowest) time of timed wins, per game and level. */
    fun bestTimes(game: String): Map<String, Long> = history.filter { it.game == game && it.result == Result.WON && it.timed && it.seconds > 0 }
        .groupBy { it.level }.mapValues { (_, g) -> g.minOf { it.seconds } }

    /** Best score per level; [lowerIsBetter] for move counts. */
    fun bestScores(game: String, lowerIsBetter: Boolean): Map<String, Long> =
        history.filter { it.game == game && it.score != null && (!lowerIsBetter || it.result == Result.WON) }
            .groupBy { it.level }.mapValues { (_, g) -> g.mapNotNull { it.score }.let { if (lowerIsBetter) it.min() else it.max() } }

    /** Current and longest run of consecutive wins in [game] (newest first in history). */
    fun streaks(game: String): Pair<Int, Int> {
        val results = history.filter { it.game == game && it.result != Result.FINISHED }.map { it.result == Result.WON }
        val current = results.takeWhile { it }.size
        var best = 0; var run = 0
        results.forEach { if (it) { run++; best = maxOf(best, run) } else run = 0 }
        return current to best
    }

    fun resetHistory(): PlayerRecords = copy(history = emptyList())

    companion object {
        const val STARTING_HINTS = 3
        const val WALLET_LIMIT = 99
        const val HISTORY_LIMIT = 500

        /** Hints earned: 1 for a win on the easiest level, up to 4 on the hardest. */
        fun reward(f: Finished): Int = if (f.result == Result.WON) 1 + f.levelIndex.coerceIn(0, 3) else 0
    }
}

/** Versioned text format for [PlayerRecords]. Unknown or broken lines are skipped rather than losing everything. */
object PlayerCodec {
    private const val VERSION = "P1"

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')

    fun encode(p: PlayerRecords): String = buildList {
        add(VERSION)
        with(p.settings) {
            add(listOf("S", clean(name), language.name, timedByDefault, showTimer, earnHints, keepScreenOn, offerTutorials).joinToString("\t"))
        }
        add("W\t${p.wallet}")
        p.timedChoice.forEach { (g, t) -> add("T\t$g\t$t") }
        if (p.tutorialsRewarded.isNotEmpty()) add("R\t" + p.tutorialsRewarded.sorted().joinToString(","))
        p.sessions.values.forEach { s ->
            add(listOf("A", s.game, clean(s.key), clean(s.level), s.levelIndex, s.timed, s.startedAt, s.lastPlayed, s.seconds, s.hints).joinToString("\t"))
        }
        p.history.forEach { f ->
            add(listOf("H", f.game, clean(f.level), f.levelIndex, f.result.name, f.timed, f.seconds, f.hints, f.score ?: "", f.at).joinToString("\t"))
        }
    }.joinToString("\n")

    fun decode(text: String?): PlayerRecords {
        if (text.isNullOrBlank()) return PlayerRecords()
        val lines = text.split('\n')
        if (lines.first() != VERSION) return PlayerRecords()
        var p = PlayerRecords()
        val sessions = mutableMapOf<String, Session>()
        val history = mutableListOf<Finished>()
        val timed = mutableMapOf<String, Boolean>()
        for (line in lines.drop(1)) {
            val f = line.split('\t')
            try {
                when (f[0]) {
                    "S" -> p = p.copy(settings = PlayerSettings(f[1], Language.valueOf(f[2]), f[3].toBooleanStrict(), f[4].toBooleanStrict(),
                        f[5].toBooleanStrict(), f[6].toBooleanStrict(), f[7].toBooleanStrict()))
                    "W" -> p = p.copy(wallet = f[1].toInt().coerceIn(0, PlayerRecords.WALLET_LIMIT))
                    "T" -> timed[f[1]] = f[2].toBooleanStrict()
                    "R" -> p = p.copy(tutorialsRewarded = f[1].split(',').filter { it.isNotEmpty() }.toSet())
                    "A" -> sessions[f[1]] = Session(f[1], f[2], f[3], f[4].toInt(), f[5].toBooleanStrict(), f[6].toLong(), f[7].toLong(), f[8].toLong(), f[9].toInt())
                    "H" -> history += Finished(f[1], f[2], f[3].toInt(), Result.valueOf(f[4]), f[5].toBooleanStrict(), f[6].toLong(), f[7].toInt(),
                        f[8].takeIf { it.isNotEmpty() }?.toLong(), f[9].toLong())
                }
            } catch (_: IllegalArgumentException) {
                // Skip the damaged line.
            } catch (_: IndexOutOfBoundsException) {
            }
        }
        return p.copy(sessions = sessions, history = history.take(PlayerRecords.HISTORY_LIMIT), timedChoice = timed)
    }
}
