package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import kotlin.random.Random

// ---------------- Wordsworn ----------------
//
// A run of word battles. Spell a word from the letter tiles in your hand to strike the monster; rare letters
// hit harder and long words get a bonus. Then the monster does what it showed it would. Between fights, pick a
// reward that grows your deck. Win the last fight to finish the run.

/** What a tile does besides its letter. */
enum class TileKind(val label: String) {
    PLAIN("Plain"),
    /** Blocks damage equal to its power this turn. */
    SHIELD("Shield"),
    /** Heals its power. */
    HEAL("Heal"),
    /** Doubles the word's damage. */
    DOUBLE("Double"),
    /** Can be any letter. */
    WILD("Wild"),
}

data class Tile(val letter: Char, val power: Int, val kind: TileKind = TileKind.PLAIN) {
    init { require((letter in 'A'..'Z' || (kind == TileKind.WILD && letter == '?')) && power in 0..20) }
}

/** What a monster will do on its next turn. */
enum class IntentKind { HIT, GUARD, DRAIN }

data class Intent(val kind: IntentKind, val amount: Int)

/** The monsters, easiest first; the last is the final boss. All original, all friendly-looking. */
enum class Monster(val label: String, val hp: Int, val pattern: List<Intent>) {
    TYPO_IMP("Typo Imp", 18, listOf(Intent(IntentKind.HIT, 4), Intent(IntentKind.HIT, 5), Intent(IntentKind.GUARD, 4))),
    DUST_BUNNY("Dust Bunny", 22, listOf(Intent(IntentKind.HIT, 3), Intent(IntentKind.HIT, 3), Intent(IntentKind.HIT, 7))),
    INKBLOT_SLIME("Inkblot Slime", 28, listOf(Intent(IntentKind.GUARD, 6), Intent(IntentKind.HIT, 7), Intent(IntentKind.HIT, 5))),
    SPELLING_BEE("Spelling Bee", 30, listOf(Intent(IntentKind.HIT, 6), Intent(IntentKind.HIT, 6), Intent(IntentKind.DRAIN, 5))),
    GRAMMAR_GREMLIN("Grammar Gremlin", 36, listOf(Intent(IntentKind.HIT, 8), Intent(IntentKind.GUARD, 8), Intent(IntentKind.HIT, 9))),
    BOOKWORM("Bookworm", 42, listOf(Intent(IntentKind.DRAIN, 6), Intent(IntentKind.HIT, 9), Intent(IntentKind.GUARD, 7), Intent(IntentKind.HIT, 10))),
    PAPER_DRAGON("Paper Dragon", 50, listOf(Intent(IntentKind.HIT, 11), Intent(IntentKind.GUARD, 10), Intent(IntentKind.HIT, 12))),
    WORD_EATER("Word Eater", 70, listOf(Intent(IntentKind.HIT, 10), Intent(IntentKind.DRAIN, 8), Intent(IntentKind.GUARD, 12), Intent(IntentKind.HIT, 15))),
}

/** A reward offered after a fight. */
sealed interface Reward {
    data class Upgrade(val letter: Char, val by: Int) : Reward
    data class NewTile(val tile: Tile) : Reward
    data class Rest(val amount: Int) : Reward
}

/** The last thing that happened, for the screen to show. */
data class Blow(val word: String = "", val damage: Int = 0, val blocked: Int = 0, val healed: Int = 0, val taken: Int = 0, val enemyGuard: Int = 0)

data class Wordsworn(
    val level: LogicLevel,
    val seed: Long,
    val fight: Int,
    val monster: Monster,
    val monsterHp: Int,
    val monsterGuard: Int,
    val turn: Int,
    val hp: Int,
    val maxHp: Int,
    /** Every tile the player owns; piles refer to them by index. */
    val deck: List<Tile>,
    val draw: List<Int>,
    val hand: List<Int>,
    val discard: List<Int>,
    val swaps: Int,
    val rewards: List<Reward> = emptyList(),
    val last: Blow? = null,
    /** How many random draws have happened, so every shuffle is repeatable from the seed. */
    val rolls: Int = 0,
    val hints: Int = 0,
) {
    val fights: Int get() = fightsOf(level)
    val won: Boolean get() = fight == fights - 1 && monsterHp <= 0 && rewards.isEmpty()
    val lost: Boolean get() = hp <= 0
    val over: Boolean get() = won || lost
    /** Between fights: the player is choosing a reward. */
    val choosing: Boolean get() = rewards.isNotEmpty()
    val intent: Intent get() = monster.pattern[turn % monster.pattern.size].let { i -> i.copy(amount = scaled(i.amount)) }

    private fun scaled(n: Int) = (n * listOf(0.75, 0.9, 1.0, 1.05)[level.ordinal]).toInt().coerceAtLeast(1)

    /** Why these hand tiles can't be played (with [wild] letters for wild tiles, in order), or null. */
    fun problem(picks: List<Int>, wild: String = ""): String? {
        if (over || choosing) return "Not now."
        if (picks.size < 2) return "Use at least two tiles."
        if (picks.toSet().size != picks.size || picks.any { it !in hand.indices }) return "Each tile once."
        val word = spell(picks, wild) ?: return "Pick a letter for each wild tile."
        if (!Lexicon.isWord(word)) return "Not in the word list."
        return null
    }

    fun spell(picks: List<Int>, wild: String = ""): String? {
        var w = 0
        val sb = StringBuilder()
        for (p in picks) {
            val t = deck[hand[p]]
            if (t.kind == TileKind.WILD) { if (w >= wild.length) return null; sb.append(wild[w++]) } else sb.append(t.letter)
        }
        return sb.toString()
    }

    /** What playing these tiles would do. */
    fun preview(picks: List<Int>): Blow {
        val tiles = picks.map { deck[hand[it]] }
        var damage = tiles.sumOf { it.power }
        // Long words hit harder: +25% for each letter beyond three.
        damage = (damage * (1.0 + 0.25 * (tiles.size - 3).coerceAtLeast(0))).toInt()
        if (tiles.any { it.kind == TileKind.DOUBLE }) damage *= 2
        val block = tiles.filter { it.kind == TileKind.SHIELD }.sumOf { it.power * 2 }
        val heal = tiles.filter { it.kind == TileKind.HEAL }.sumOf { it.power * 2 }
        return Blow(damage = damage, blocked = block, healed = heal)
    }

    /** Plays a word, then the monster takes its turn (unless beaten). */
    fun play(picks: List<Int>, wild: String = ""): Wordsworn? {
        if (problem(picks, wild) != null) return null
        val word = spell(picks, wild)!!
        val p = preview(picks)
        val through = (p.damage - monsterGuard).coerceAtLeast(0)
        val guardLeft = (monsterGuard - p.damage).coerceAtLeast(0)
        val used = picks.map { hand[it] }
        var next = copy(monsterHp = monsterHp - through, monsterGuard = guardLeft, hp = minOf(maxHp, hp + p.healed),
            hand = hand.filterIndexed { i, _ -> i !in picks }, discard = discard + used)
        val blow = p.copy(word = word, damage = through)
        if (next.monsterHp <= 0) return next.copy(monsterHp = 0, last = blow).afterFight()
        return next.monsterTurn(blow).refill()
    }

    /** Trades up to three tiles for new ones; the monster still takes its turn. */
    fun swap(picks: List<Int>): Wordsworn? {
        if (over || choosing || swaps <= 0 || picks.isEmpty() || picks.size > 3 || picks.any { it !in hand.indices }) return null
        val used = picks.map { hand[it] }
        return copy(hand = hand.filterIndexed { i, _ -> i !in picks }, discard = discard + used, swaps = swaps - 1)
            .monsterTurn(Blow()).refill()
    }

    private fun monsterTurn(blow: Blow): Wordsworn {
        val i = intent
        return when (i.kind) {
            IntentKind.HIT -> {
                val taken = (i.amount - blow.blocked).coerceAtLeast(0)
                copy(hp = hp - taken, turn = turn + 1, last = blow.copy(taken = taken))
            }
            IntentKind.GUARD -> copy(monsterGuard = monsterGuard + i.amount, turn = turn + 1, last = blow.copy(enemyGuard = i.amount))
            IntentKind.DRAIN -> {
                val taken = (i.amount - blow.blocked).coerceAtLeast(0)
                copy(hp = hp - taken, monsterHp = minOf(monsterMax, monsterHp + taken), turn = turn + 1, last = blow.copy(taken = taken))
            }
        }
    }

    val monsterMax: Int get() = monsterHpOf(level, monster)

    /** Draws back up to a full hand, shuffling the discards in when the pile runs out. */
    private fun refill(): Wordsworn {
        var g = this
        while (g.hand.size < HAND && (g.draw.isNotEmpty() || g.discard.isNotEmpty())) {
            if (g.draw.isEmpty()) {
                val r = Random(seed * 31 + g.rolls)
                g = g.copy(draw = g.discard.shuffled(r), discard = emptyList(), rolls = g.rolls + 1)
            }
            g = g.copy(hand = g.hand + g.draw.first(), draw = g.draw.drop(1))
        }
        return g
    }

    private fun afterFight(): Wordsworn {
        if (fight == fights - 1) return copy(rewards = emptyList())
        val r = Random(seed * 97 + fight)
        val letters = deck.filter { it.kind != TileKind.WILD }.map { it.letter }.distinct().shuffled(r)
        val special = listOf(TileKind.SHIELD, TileKind.HEAL, TileKind.DOUBLE, TileKind.WILD).random(r)
        val tile = if (special == TileKind.WILD) Tile('?', 0, TileKind.WILD) else Tile("AEIORSTNL".random(r), 2, special)
        return copy(rewards = listOf(Reward.Upgrade(letters.first(), 2), Reward.NewTile(tile), Reward.Rest(maxHp / 3)))
    }

    /** Takes a reward and starts the next fight with a fresh shuffle. */
    fun choose(i: Int): Wordsworn? {
        if (!choosing || i !in rewards.indices) return null
        var deck2 = deck
        var hp2 = hp
        when (val reward = rewards[i]) {
            is Reward.Upgrade -> deck2 = deck.map { if (it.letter == reward.letter && it.kind != TileKind.WILD) it.copy(power = minOf(20, it.power + reward.by)) else it }
            is Reward.NewTile -> deck2 = deck + reward.tile
            is Reward.Rest -> hp2 = minOf(maxHp, hp + reward.amount)
        }
        val next = fight + 1
        val monster = monsterFor(level, next)
        val order = deck2.indices.shuffled(Random(seed * 131 + next))
        return copy(fight = next, monster = monster, monsterHp = monsterHpOf(level, monster), monsterGuard = 0, turn = 0, hp = hp2,
            deck = deck2, draw = order.drop(HAND), hand = order.take(HAND), discard = emptyList(), swaps = swapsOf(level), rewards = emptyList(), last = null)
    }

    /** The strongest word the hand can make now (wild tiles fill in), with its picks and wild letters. */
    fun bestWord(): Triple<String, List<Int>, String>? {
        if (over || choosing) return null
        val tiles = hand.map { deck[it] }
        var best: Triple<String, List<Int>, String>? = null
        var bestScore = -1
        for (word in Lexicon.all) {
            if (word.length < 2 || word.length > tiles.size) continue
            val picks = ArrayList<Int>(); val wild = StringBuilder()
            val free = tiles.indices.toMutableList()
            var ok = true
            for (ch in word) {
                // Prefer the strongest real tile for each letter; a wild tile covers anything.
                val real = free.filter { tiles[it].letter == ch && tiles[it].kind != TileKind.WILD }.maxByOrNull { tiles[it].power }
                val pick = real ?: free.firstOrNull { tiles[it].kind == TileKind.WILD }
                if (pick == null) { ok = false; break }
                if (tiles[pick].kind == TileKind.WILD) wild.append(ch)
                picks += pick; free.remove(pick)
            }
            if (!ok) continue
            val score = preview(picks).let { it.damage + it.blocked + it.healed }
            if (score > bestScore) { bestScore = score; best = Triple(word, picks, wild.toString()) }
        }
        return best
    }

    fun hinted() = copy(hints = hints + 1)

    companion object {
        const val HAND = 8

        fun fightsOf(level: LogicLevel) = listOf(4, 5, 6, 7)[level.ordinal]
        fun swapsOf(level: LogicLevel) = listOf(4, 3, 2, 2)[level.ordinal]
        fun startHp(level: LogicLevel) = listOf(60, 55, 50, 50)[level.ordinal]

        fun monsterHpOf(level: LogicLevel, m: Monster) = (m.hp * listOf(0.75, 0.9, 1.0, 1.05)[level.ordinal]).toInt()

        /** The run's monsters: easier ones first, the Word Eater last. */
        fun monsterFor(level: LogicLevel, fight: Int): Monster {
            val n = fightsOf(level)
            if (fight == n - 1) return Monster.WORD_EATER
            val pool = Monster.entries.dropLast(1)
            return pool[(fight * (pool.size - 1)) / maxOf(1, n - 2)]
        }

        /** Letter power: common letters 1, then up to 8 for Q, X and Z. */
        fun powerOf(ch: Char): Int = when (ch) {
            in "EAIONRSTLU" -> 1
            in "DG" -> 2
            in "BCMP" -> 3
            in "FHVWY" -> 4
            'K' -> 5
            in "JX" -> 8
            in "QZ" -> 10
            else -> 1
        }

        /** The starting deck: 28 tiles shaped like everyday English. */
        private const val STARTER = "EEEAAIIOONNRRSSTTLLUDGCMPBHY"

        fun start(seed: Long, level: LogicLevel): Wordsworn {
            val deck = STARTER.map { Tile(it, powerOf(it)) }
            val order = deck.indices.shuffled(Random(seed))
            val monster = monsterFor(level, 0)
            return Wordsworn(level, seed, 0, monster, monsterHpOf(level, monster), 0, 0, startHp(level), startHp(level), deck,
                order.drop(HAND), order.take(HAND), emptyList(), swapsOf(level))
        }
    }
}

object WordswornCodec {
    private fun tile(t: Tile) = "${t.letter}${t.power}${t.kind.name.first()}"
    private fun tileOf(s: String): Tile {
        val kind = TileKind.entries.first { it.name.first() == s.last() }
        return Tile(s.first(), s.substring(1, s.length - 1).toInt(), kind)
    }
    private fun reward(r: Reward) = when (r) {
        is Reward.Upgrade -> "U${r.letter}${r.by}"
        is Reward.NewTile -> "N" + tile(r.tile)
        is Reward.Rest -> "R${r.amount}"
    }
    private fun rewardOf(s: String): Reward = when (s.first()) {
        'U' -> Reward.Upgrade(s[1], s.substring(2).toInt())
        'N' -> Reward.NewTile(tileOf(s.substring(1)))
        else -> Reward.Rest(s.substring(1).toInt())
    }
    private fun ints(l: List<Int>) = l.joinToString(",")
    private fun intsOf(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map { it.toInt() }

    fun encode(g: Wordsworn) = listOf("1", g.level.name, g.seed.toString(), g.fight.toString(), g.monster.name, g.monsterHp.toString(),
        g.monsterGuard.toString(), g.turn.toString(), g.hp.toString(), g.maxHp.toString(), g.deck.joinToString(",", transform = ::tile),
        ints(g.draw), ints(g.hand), ints(g.discard), g.swaps.toString(), g.rewards.joinToString(",", transform = ::reward), g.rolls.toString(),
        g.hints.toString()).joinToString("\n")

    fun decode(text: String): Wordsworn? = try {
        val l = text.split('\n'); require(l.size == 18 && l[0] == "1")
        Wordsworn(LogicLevel.valueOf(l[1]), l[2].toLong(), l[3].toInt(), Monster.valueOf(l[4]), l[5].toInt(), l[6].toInt(), l[7].toInt(),
            l[8].toInt(), l[9].toInt(), l[10].split(',').map(::tileOf), intsOf(l[11]), intsOf(l[12]), intsOf(l[13]), l[14].toInt(),
            if (l[15].isEmpty()) emptyList() else l[15].split(',').map(::rewardOf), rolls = l[16].toInt(), hints = l[17].toInt())
    } catch (_: IllegalArgumentException) { null } catch (_: NoSuchElementException) { null } catch (_: IndexOutOfBoundsException) { null }
}
