package com.simplegamegen.sudoku.wordplay

import com.simplegamegen.sudoku.logic.LogicLevel
import kotlin.random.Random

// ---------------- Wordsworn ----------------
//
// A run of word battles through three books. Each turn you spell a word from four letter cards, a wild letter and the
// monster's weak-spot vowel, then splay it left or right: only the icons on that edge of each card count (hits,
// blocks and ink). The top card (the last letter when splayed left, the first when splayed right) also does what it
// says, then it's worn out for the rest of the fight. The monster shows what it will do; beat both of its stages.
// Between fights, choose a path of rewards and spend stars in the shop.

/** Icons on one edge of a letter card. */
data class Edge(val hits: Int = 0, val blocks: Int = 0, val ink: Int = 0) {
    operator fun plus(o: Edge) = Edge(hits + o.hits, blocks + o.blocks, ink + o.ink)
}

/** Which edge of the word's cards shows: left puts the last letter on top, right the first. */
enum class Splay { LEFT, RIGHT }

/** Optional, independently authored rule modifiers; they can be combined before a run. */
enum class WordswornTwist(val label: String, val text: String, val difficulty: Int) {
    RESERVE("Extra provisions", "Start with 5 more maximum health", -1),
    COMPANION("Pocket alphabet", "One additional permanent wild letter", -1),
    PLATING("Bound in iron", "Every monster blocks 1 additional hit each turn", 1),
    SMUDGES("Smudged opening", "Start with 2 additional blot cards", 1),
}

/** What a card does when it's the top card of a word. */
enum class Power(val scales: Boolean = true) {
    NONE(false), HIT, BLOCK, INK, HEX, STAR, HEAL, DRAW, VOWELS, LONG, SHORT, ALL_IN, SKIP(false), RECALL, PIERCE(false), HEX_HIT(false);

    fun describe(n: Int): String = when (this) {
        NONE -> ""
        HIT -> if (n == 1) "+1 hit" else "+$n hits"
        BLOCK -> if (n == 1) "+1 block" else "+$n blocks"
        INK -> "Gain $n ink"
        HEX -> if (n == 1) "Give the monster 1 hex" else "Give the monster $n hexes"
        STAR -> if (n == 1) "Gain 1 star" else "Gain $n stars"
        HEAL -> "Heal $n"
        DRAW -> if (n == 1) "Draw 1 more card next turn" else "Draw $n more cards next turn"
        VOWELS -> if (n == 1) "+1 hit for each vowel in the word" else "+$n hits for each vowel in the word"
        LONG -> "5 or more letters: +$n hits"
        SHORT -> "3 or fewer letters: +$n blocks"
        ALL_IN -> "Every card in your hand used: +$n hits"
        SKIP -> "The monster skips to its next action"
        RECALL -> if (n == 1) "1 worn-out card comes back" else "$n worn-out cards come back"
        PIERCE -> "Your hits ignore the monster's block"
        HEX_HIT -> "+1 hit for each hex on the monster"
    }
}

/** A letter card. A blot is a penalty card: it hurts if it's left in your hand, and it's thrown away once played. */
data class Card(val letter: Char, val left: Edge, val right: Edge, val power: Power, val amount: Int = 0,
    val upgraded: Boolean = false, val blot: Boolean = false) {
    init { require(letter in 'A'..'Z') }
    fun edge(s: Splay) = if (s == Splay.LEFT) left else right
    val text: String get() = if (blot) BLOT_TEXT else power.describe(amount)
    /** Upgraded: one more hit on the right edge, one more block on the left, and a stronger ability. */
    fun upgrade(): Card = if (upgraded || blot) this else copy(upgraded = true, left = left.copy(blocks = left.blocks + 1),
        right = right.copy(hits = right.hits + 1), amount = if (power.scales) amount + 1 else amount)

    companion object {
        const val BLOT_TEXT = "Blot: left in your hand, it hurts you 2. Played, it's thrown away."
    }
}

/** A piece of a word: a hand card, the wild letter or the monster's weak-spot vowel. */
sealed interface Piece {
    data class Hand(val index: Int) : Piece
    data class Wild(val letter: Char) : Piece
    data object Vowel : Piece
}

enum class ItemKind(val label: String, val cost: Int, val text: String, val single: Boolean = false, val core: Boolean = false) {
    OATH_SHIELD("Oath Shield", 1, "+3 blocks (upgraded: +5)", core = true),
    CAULDRON("Cauldron", 1, "Give the monster 2 hexes (upgraded: 3)", core = true),
    LUTE("Lute", 2, "Draw a card now (upgraded: costs 1 ink)", core = true),
    THROWING_KNIFE("Throwing Knife", 1, "+2 hits"),
    BUCKLER("Buckler", 1, "+2 blocks"),
    NETTLES("Nettle Pouch", 1, "Give the monster 1 hex"),
    QUILL_DART("Quill Dart", 2, "+4 hits"),
    IRON_COVER("Iron Cover", 2, "+4 blocks"),
    DRAUGHT("Healing Draught", 2, "Heal 5", single = true),
    SPARE_NIB("Spare Nib", 2, "One more wild letter this turn"),
    HOURGLASS("Hourglass", 3, "The monster skips to its next action", single = true),
    BOOKMARK("Bookmark", 1, "A worn-out card comes back to your hand", single = true),
    ANTIDOTE("Antidote", 1, "Lose all your hexes"),
    GOLD_LEAF("Gold Leaf", 3, "Gain 2 stars"),
    SEAL("Steadfast Seal", 0, "Spend 1 star: gain 3 blocks this turn", core = true),
    BRAND("Quill Brand", 1, "Spend up to 3 monster hexes: gain 2 hits for each", core = true),
    LENS("Cinder Lens", 1, "The monster loses HP equal to its hexes", core = true),
    PURSE("Moon Purse", 0, "Spend 1 star: gain 2 ink", core = true),
    REFRAIN("Refrain", 1, "Spend 2 monster hexes: draw a card", core = true),
    ENCORE("Encore", 0, "Spend 1 star: return a worn-out card to your hand", core = true),
    BANNER("Banner Stitch", 0, "Spend 1 star: heal 2", core = true),
    MARGIN("Margin Guard", 1, "Spend 2 monster hexes: gain 4 blocks", core = true),
    LEDGER("Night Ledger", 0, "Spend 1 star: draw a card", core = true),
    ASH("Ash Thread", 1, "Spend 2 monster hexes: the monster loses 4 HP", core = true),
    BRIDGE("Bridge Verse", 0, "Spend 1 star: gain 3 hits", core = true),
    CHORUS("Quiet Chorus", 1, "Spend 2 monster hexes: heal 3", core = true),
}

data class Item(val kind: ItemKind, val upgraded: Boolean = false) {
    val cost: Int get() = if (kind == ItemKind.LUTE && upgraded) 1 else kind.cost
}

/** Keepsakes work on their own, all run long. Most come as two sides of one card: you keep the side you pick. */
enum class Keepsake(val label: String, val text: String, val boss: Boolean = false) {
    SHARP_EDGE("Sharp Edge", "+1 hit every turn"),
    STURDY_BOOTS("Sturdy Boots", "+1 block every turn"),
    INKWELL("Inkwell", "Start each fight with 2 ink"),
    LUCKY_STAR("Lucky Star", "1 more star after each fight"),
    HEART_LOCKET("Heart Locket", "+5 most health"),
    SALVE("Salve", "Heal 3 after each fight"),
    THORN_RING("Thorn Ring", "Monsters start each stage with 2 hexes"),
    HEX_MIRROR("Hex Mirror", "When you get hexes, the monster gets 1 too"),
    LONG_SCROLL("Long Scroll", "Words of 6 or more letters: +3 hits"),
    SHORT_FUSE("Short Fuse", "Words of 2 or 3 letters: +2 blocks"),
    VOWEL_CHARM("Vowel Charm", "Using the weak-spot vowel: +2 hits"),
    WILD_FEATHER("Wild Feather", "An unused wild letter gives 1 more ink"),
    BIG_SATCHEL("Big Satchel", "Draw 5 cards each turn", boss = true),
    WAR_DRUM("War Drum", "+2 hits every turn", boss = true),
    SCHOLARS_CROWN("Scholar's Crown", "Top-card abilities work twice", boss = true),
    PHOENIX_QUILL("Phoenix Quill", "Once, when you would fall, heal to 10 instead", boss = true);

    companion object {
        /** The two-sided keepsake cards. */
        val pairs = listOf(SHARP_EDGE to STURDY_BOOTS, INKWELL to LUCKY_STAR, HEART_LOCKET to SALVE, THORN_RING to HEX_MIRROR,
            LONG_SCROLL to SHORT_FUSE, VOWEL_CHARM to WILD_FEATHER)
    }
}

/** The heroes. Each has a starting deck, a core item and the kinds of abilities its cards grow into. */
enum class Hero(val label: String, val title: String, val maxHp: Int, val starter: String, val core: ItemKind, val extra: ItemKind, val powers: List<Power>) {
    KNIGHT("Sir Serif", "The Knight", 24, "AEIOSTRNLD", ItemKind.OATH_SHIELD, ItemKind.BUCKLER,
        listOf(Power.BLOCK, Power.HIT, Power.SHORT, Power.STAR, Power.HEAL, Power.PIERCE)),
    WITCH("Wren", "The Hexwitch", 20, "AEIOSTRNMC", ItemKind.CAULDRON, ItemKind.NETTLES,
        listOf(Power.HEX, Power.HEX_HIT, Power.INK, Power.HEAL, Power.RECALL, Power.VOWELS)),
    BARD("Lyric", "The Bard", 20, "AEIOUSTRNL", ItemKind.LUTE, ItemKind.THROWING_KNIFE,
        listOf(Power.LONG, Power.VOWELS, Power.DRAW, Power.ALL_IN, Power.STAR, Power.INK)),
    ;
    val coreKinds: List<ItemKind> get() = when (this) {
        KNIGHT -> listOf(ItemKind.SEAL, ItemKind.BRAND)
        WITCH -> listOf(ItemKind.PURSE, ItemKind.LENS)
        BARD -> listOf(ItemKind.ENCORE, ItemKind.REFRAIN)
    }
    val alternateCoreKinds: List<ItemKind> get() = when (this) {
        KNIGHT -> listOf(ItemKind.BANNER, ItemKind.MARGIN)
        WITCH -> listOf(ItemKind.LEDGER, ItemKind.ASH)
        BARD -> listOf(ItemKind.BRIDGE, ItemKind.CHORUS)
    }
    fun cores(loadout: Int): List<ItemKind> = listOf(
        if (loadout and 1 != 0) alternateCoreKinds[0] else coreKinds[0],
        if (loadout and 2 != 0) alternateCoreKinds[1] else coreKinds[1])
}

/** One monster action. Several parts can happen together. */
data class Act(val attack: Int = 0, val block: Int = 0, val heal: Int = 0, val stars: Int = 0, val hexes: Int = 0, val blots: Int = 0) {
    fun scaled(f: Double) = Act((attack * f).toInt(), (block * f).toInt(), (heal * f).toInt(), stars, hexes, blots)
    /** Plain words for the action, joined with " · ". */
    fun describe(extraAttack: Int = 0): String = buildList {
        if (attack > 0) add("Attacks ${attack + extraAttack}")
        if (block > 0) add("Blocks $block")
        if (heal > 0) add("Heals $heal")
        if (stars > 0) add(if (stars == 1) "Gains 1 star" else "Gains $stars stars")
        if (hexes > 0) add(if (hexes == 1) "Gives you 1 hex" else "Gives you $hexes hexes")
        if (blots > 0) add(if (blots == 1) "Adds a blot to your deck" else "Adds $blots blots to your deck")
    }.joinToString(" · ")
}

enum class Special(val text: String) {
    NONE(""),
    STARS("Starts each stage with 2 stars."),
    REGEN("Heals 1 at the end of each turn."),
    NO_HEX("Hexes don't hurt it."),
    LONG_WEAK("Words of 6 or more letters deal 3 more to it."),
    ARMOR("Blocks 1 hit every turn."),
    EAT("Each of its attacks wears out a card from your discard pile."),
}

private fun a(n: Int) = Act(attack = n)

/** The monsters of the three books; the last of each book is its boss. All original, all friendly-looking. */
enum class Monster(val label: String, val book: Int, val boss: Boolean, val weak: Char, val hp1: Int, val acts1: List<Act>,
    val hp2: Int, val acts2: List<Act>, val special: Special = Special.NONE) {
    TYPO_IMP("Typo Imp", 0, false, 'O', 8, listOf(a(3), a(4), Act(attack = 2, block = 3)), 10, listOf(a(5), Act(hexes = 2), a(4))),
    DUST_BUNNY("Dust Bunny", 0, false, 'U', 8, listOf(a(2), a(2), a(5)), 10, listOf(Act(attack = 3, block = 2), a(6)), Special.REGEN),
    PAGE_MITE("Page Mite", 0, false, 'A', 7, listOf(Act(attack = 2, hexes = 1), a(3), Act(blots = 1)), 9, listOf(a(4), Act(hexes = 2), a(3))),
    GRAMMAR_GREMLIN("Grammar Gremlin", 0, true, 'A', 14, listOf(Act(attack = 3, block = 4), a(6), Act(stars = 1)),
        18, listOf(Act(attack = 5, hexes = 1), a(7), Act(block = 5), Act(attack = 3, blots = 1)), Special.STARS),
    INKBLOT_SLIME("Inkblot Slime", 1, false, 'I', 12, listOf(Act(block = 5), a(6), Act(hexes = 2)), 14,
        listOf(a(7), Act(heal = 4), Act(attack = 5, hexes = 1)), Special.NO_HEX),
    SPELLING_BEE("Spelling Bee", 1, false, 'E', 11, listOf(a(4), a(4), a(7)), 13, listOf(Act(attack = 5, hexes = 1), a(8), Act(block = 4)), Special.LONG_WEAK),
    BOOKWORM("Bookworm", 1, false, 'O', 13, listOf(a(5), Act(heal = 3, block = 3), a(6)), 15, listOf(a(8), Act(blots = 1), Act(attack = 6, block = 3)), Special.REGEN),
    PAPER_DRAGON("Paper Dragon", 1, true, 'A', 18, listOf(a(7), Act(block = 5, stars = 1), Act(attack = 5, hexes = 2)),
        22, listOf(a(8), Act(attack = 4, block = 5), Act(hexes = 3), a(8)), Special.ARMOR),
    GLOOM_MOTH("Gloom Moth", 2, false, 'O', 15, listOf(Act(hexes = 3), a(6), Act(attack = 7, block = 3)), 17, listOf(Act(attack = 5, hexes = 2), a(9), Act(heal = 5))),
    QUILL_WRAITH("Quill Wraith", 2, false, 'U', 16, listOf(a(8), Act(block = 7), a(8)), 18, listOf(Act(attack = 6, hexes = 2), a(11), Act(blots = 2)), Special.LONG_WEAK),
    RUST_GOLEM("Rust Golem", 2, false, 'E', 18, listOf(Act(block = 8), a(10), Act(attack = 4, block = 6)), 20, listOf(a(12), Act(block = 8, stars = 1), a(9)), Special.ARMOR),
    WORD_EATER("Word Eater", 2, true, 'E', 24, listOf(a(8), Act(block = 6, stars = 1), Act(attack = 7, hexes = 2), a(10)),
        28, listOf(a(10), Act(attack = 5, blots = 2), Act(block = 7, heal = 4), a(12)), Special.EAT);

    fun hp(stage: Int) = if (stage == 1) hp1 else hp2
    fun acts(stage: Int) = if (stage == 1) acts1 else acts2
}

/** A reward step on a path. */
sealed interface Step {
    /** Pick one of [offer] new cards; [add] puts it in the deck, otherwise it replaces a card you choose. */
    data class Cards(val offer: Int, val add: Boolean) : Step
    data class Items(val offer: Int) : Step
    data class Keepsakes(val boss: Boolean) : Step
    data object Upgrade : Step
    data object CoreUpgrade : Step
    data class Heal(val n: Int) : Step
    data class Hurt(val n: Int) : Step
    data class Stars(val n: Int) : Step
    data class Blots(val n: Int) : Step
}

/** Two paths are offered after a fight; the story line is just for flavour. */
data class Path(val steps: List<Step>, val story: String)

/** What the player is choosing right now, outside a fight. */
sealed interface Offer {
    data object Heroes : Offer
    data class Paths(val paths: List<Path>) : Offer
    data class Cards(val cards: List<Card>, val add: Boolean) : Offer
    /** Choose which deck card the new one replaces. */
    data class Replace(val card: Card, val purchased: Boolean = false) : Offer
    data class Items(val kinds: List<ItemKind>) : Offer
    data class Keepsakes(val sides: List<Keepsake>, val drawn: List<Int> = emptyList()) : Offer
    /** Choose a deck card to upgrade. */
    data object Upgrade : Offer
    /** Upgrade the core item (0) or the wild letter (1). */
    data object CoreUpgrade : Offer
    data class Shop(val goods: List<Goods>, val sold: Set<Int>, val refreshed: Boolean = false) : Offer
}

/** Something for sale in the shop, priced in stars. */
sealed interface Goods {
    val price: Int
    data class ForItem(val kind: ItemKind) : Goods { override val price get() = 2 }
    data class ForKeepsake(val sides: Pair<Keepsake, Keepsake>) : Goods { override val price get() = 3 }
    data object ForUpgrade : Goods { override val price get() = 2 }
    data object ForRest : Goods { override val price get() = 1 }
    data class ForLetter(val card: Card) : Goods { override val price get() = 1 }
    data object Empty : Goods { override val price get() = 0 }
}

/** Bonuses set up during the prep phase, used up when the word is played. */
data class Prep(val hits: Int = 0, val blocks: Int = 0, val wilds: Int = 0)

/** What the last word and the monster's answer did, for the screen to show. */
data class Clash(val word: String = "", val splay: Splay = Splay.RIGHT, val top: Char? = null, val power: String = "",
    val hits: Int = 0, val blockedByMonster: Int = 0, val damage: Int = 0, val hexDamage: Int = 0, val blocks: Int = 0, val ink: Int = 0,
    val action: String = "", val taken: Int = 0, val hexTaken: Int = 0, val flipped: Boolean = false, val beaten: Boolean = false,
    val stunned: Boolean = false, val skipped: Boolean = false)

data class Wordsworn(
    val level: LogicLevel,
    val seed: Long,
    val hero: Hero? = null,
    val offer: Offer? = Offer.Heroes,
    val fight: Int = 0,
    val monster: Monster = Monster.TYPO_IMP,
    val stage: Int = 1,
    val monsterHp: Int = 0,
    /** Which of the stage's actions the monster takes this turn. */
    val act: Int = 0,
    /** Just flipped to its second stage: it does nothing this turn. */
    val stunned: Boolean = false,
    val monsterHexes: Int = 0,
    val monsterStars: Int = 0,
    val hp: Int = 0,
    val maxHp: Int = 0,
    val ink: Int = 0,
    val stars: Int = 0,
    val hexes: Int = 0,
    /** Every card the player owns; the piles refer to them by index. */
    val deck: List<Card> = emptyList(),
    val draw: List<Int> = emptyList(),
    val hand: List<Int> = emptyList(),
    val discard: List<Int> = emptyList(),
    /** Top cards played this fight: out until the fight is over. */
    val worn: List<Int> = emptyList(),
    val vowelWorn: Boolean = false,
    val extraDraw: Int = 0,
    val items: List<Item> = emptyList(),
    val usedItems: Set<Int> = emptySet(),
    val spentItems: Set<Int> = emptySet(),
    val prep: Prep = Prep(),
    val keepsakes: List<Keepsake> = emptyList(),
    val wildUpgraded: Boolean = false,
    val phoenixUsed: Boolean = false,
    /** Reward steps still to come after the current offer. */
    val todo: List<Step> = emptyList(),
    /** The shop, kept while a purchase asks for a choice. */
    val shop: Offer.Shop? = null,
    val turn: Int = 0,
    val last: Clash? = null,
    /** Every move since the start: the save is this list, replayed. */
    val log: List<String> = emptyList(),
    val rolls: Int = 0,
    val hints: Int = 0,
    /** Version 2 runs retain their original rules and deterministic replay. */
    val rulesVersion: Int = 3,
    val library: List<Card> = emptyList(),
    val itemStock: List<ItemKind> = emptyList(),
    val keepsakeStock: List<Int> = emptyList(),
    val penaltyStock: List<Char> = "QXZJKVW".toList(),
    val coreLoadout: Int = 0,
    val twists: Set<WordswornTwist> = emptySet(),
) {
    val currentRules: Boolean get() = rulesVersion >= 3
    val requiredChoice: Boolean get() = currentRules && offer != null && offer !is Offer.Shop && offer != Offer.Heroes
    val fightsPerBook: Int get() = if (currentRules) 2 else 3
    val fights: Int get() = schedule(level, seed, rulesVersion).size
    val book: Int get() = monster.book
    val won: Boolean get() = offer == null && fight == fights - 1 && stage == 2 && monsterHp <= 0 && (!currentRules || hp > 0)
    val lost: Boolean get() = hero != null && hp <= 0
    val over: Boolean get() = won || lost
    val fighting: Boolean get() = !over && offer == null
    val monsterMax: Int get() = scale(monster.hp(stage))
    val acts: List<Act> get() = monster.acts(stage).map { it.scaled(factor) }
    /** What the monster will do at the end of this turn. */
    val intent: Act get() = acts[act % acts.size]
    val wilds: Int get() = permanentWilds + prep.wilds
    val permanentWilds: Int get() = (if (wildUpgraded || currentRules && level == LogicLevel.EASY) 2 else 1) +
        if (WordswornTwist.COMPANION in twists) 1 else 0
    val twistScore: Int get() = twists.sumOf { it.difficulty }
    val handSize: Int get() = if (Keepsake.BIG_SATCHEL in keepsakes) 5 else 4
    private val factor: Double get() = factorOf(level)
    private fun scale(n: Int) = (n * factor).toInt().coerceAtLeast(1)

    /** An authored enemy ability, not an inherent effect of a hex counter. */
    val enemyStatusRule: String get() = if (!currentRules) "Hexes hurt each turn and fade by one. Stars add to attacks."
        else "Stars add to this monster's attacks." + if (monster.acts1.any { it.hexes > 0 } || monster.acts2.any { it.hexes > 0 })
            " Its attacks also gain 1 hit for each hex on you." else ""
    fun attackOf(a: Act): Int = if (a.attack == 0) 0 else a.attack + monsterStars +
        if (currentRules && (monster.acts1.any { it.hexes > 0 } || monster.acts2.any { it.hexes > 0 })) hexes else 0

    // ---------------- The word ----------------

    fun spell(pieces: List<Piece>): String = pieces.joinToString("") { p ->
        when (p) {
            is Piece.Hand -> deck[hand[p.index]].letter.toString()
            is Piece.Wild -> p.letter.toString()
            Piece.Vowel -> monster.weak.toString()
        }
    }

    /** Why these pieces can't be played, or null. */
    fun problem(pieces: List<Piece>): String? {
        if (!fighting) return "Not now."
        if (pieces.size < 2) return "Use at least two letters."
        val hands = pieces.filterIsInstance<Piece.Hand>().map { it.index }
        if (hands.toSet().size != hands.size || hands.any { it !in hand.indices }) return "Each card once."
        if (pieces.count { it is Piece.Vowel } > (if (vowelWorn) 0 else 1)) return "The weak-spot vowel is worn out."
        if (pieces.count { it is Piece.Wild } > wilds) return if (wilds == 1) "Only one wild letter." else "Only $wilds wild letters."
        if (pieces.any { it is Piece.Wild && it.letter !in 'A'..'Z' }) return "Pick a letter for the wild card."
        if (!Lexicon.isWord(spell(pieces))) return "Not in the word list."
        return null
    }

    /** The piece whose card is on top: the first when splayed right, the last when left; a wild passes it to the one under it. */
    fun topOf(pieces: List<Piece>, splay: Splay): Piece? {
        val order = if (splay == Splay.RIGHT) pieces else pieces.reversed()
        return order.firstOrNull { it !is Piece.Wild && (!currentRules || it !is Piece.Hand || !deck[hand[it.index]].blot) }
    }

    /** What playing these pieces would do, before the monster answers. */
    fun preview(pieces: List<Piece>, splay: Splay): Clash {
        val word = spell(pieces)
        val cards = pieces.filterIsInstance<Piece.Hand>().map { deck[hand[it.index]] }
        val icons = cards.filter { !it.blot }.fold(Edge()) { e, c -> e + c.edge(splay) }
        var hits = icons.hits + prep.hits
        var blocks = icons.blocks + prep.blocks
        var gain = icons.ink
        val top = topOf(pieces, splay)
        var pierce = false
        var hexGive = 0
        var skip = false
        val topCard = (top as? Piece.Hand)?.let { deck[hand[it.index]] }
        if (top == Piece.Vowel) skip = true
        if (topCard != null && !topCard.blot) {
            val times = if (Keepsake.SCHOLARS_CROWN in keepsakes) 2 else 1
            repeat(times) {
                val n = topCard.amount
                when (topCard.power) {
                    Power.HIT -> hits += n
                    Power.BLOCK -> blocks += n
                    Power.INK -> gain += n
                    Power.HEX -> hexGive += n
                    Power.VOWELS -> hits += n * word.count { it in VOWELS }
                    Power.LONG -> if (word.length >= 5) hits += n
                    Power.SHORT -> if (word.length <= 3) blocks += n
                    Power.ALL_IN -> if (pieces.count { it is Piece.Hand } == hand.size) hits += n
                    Power.SKIP -> skip = true
                    Power.PIERCE -> pierce = true
                    Power.HEX_HIT -> hits += monsterHexes
                    else -> {}
                }
            }
        }
        if (Keepsake.SHARP_EDGE in keepsakes) hits += 1
        if (Keepsake.WAR_DRUM in keepsakes) hits += 2
        if (Keepsake.STURDY_BOOTS in keepsakes) blocks += 1
        if (Keepsake.LONG_SCROLL in keepsakes && word.length >= 6) hits += 3
        if (Keepsake.SHORT_FUSE in keepsakes && word.length <= 3) blocks += 2
        if (Keepsake.VOWEL_CHARM in keepsakes && Piece.Vowel in pieces) hits += 2
        if (monster.special == Special.LONG_WEAK && word.length >= 6) hits += 3
        val unusedWilds = if (currentRules) (permanentWilds - (pieces.count { it is Piece.Wild } - prep.wilds).coerceAtLeast(0)).coerceAtLeast(0)
            else if (pieces.none { it is Piece.Wild }) 1 else 0
        gain += unusedWilds * (1 + if (Keepsake.WILD_FEATHER in keepsakes) 1 else 0)
        // The monster's block this turn (its action shows it), unless the monster is about to skip past it.
        val shown = if (skip) acts[(act + 1) % acts.size] else intent
        val guard = if (pierce || stunned) 0 else shown.block + (if (monster.special == Special.ARMOR) 1 else 0) +
            if (WordswornTwist.PLATING in twists) 1 else 0
        val damage = (hits - guard).coerceAtLeast(0)
        return Clash(word, splay, when (top) { is Piece.Hand -> deck[hand[top.index]].letter; Piece.Vowel -> monster.weak; else -> null },
            topCard?.text ?: if (top == Piece.Vowel) "The monster skips to its next action" else "",
            hits, minOf(hits, guard), damage, hexGive, blocks, gain, skipped = skip)
    }

    /** Plays a word, splayed one way; then the monster answers, and the next hand is drawn. */
    fun play(pieces: List<Piece>, splay: Splay): Wordsworn? {
        if (problem(pieces) != null) return null
        if (currentRules) return playCurrent(pieces, splay)
        val p = preview(pieces, splay)
        val top = topOf(pieces, splay)
        val topCard = (top as? Piece.Hand)?.let { deck[hand[it.index]] }
        var g = this
        val times = if (Keepsake.SCHOLARS_CROWN in keepsakes) 2 else 1
        // The top card's abilities that aren't hits, blocks or ink.
        if (topCard != null && !topCard.blot) repeat(times) {
            when (topCard.power) {
                Power.STAR -> g = g.copy(stars = g.stars + topCard.amount)
                Power.HEAL -> g = g.copy(hp = minOf(g.maxHp, g.hp + topCard.amount))
                Power.DRAW -> g = g.copy(extraDraw = g.extraDraw + topCard.amount)
                Power.RECALL -> g = g.copy(worn = g.worn.dropLast(topCard.amount), discard = g.discard + g.worn.takeLast(topCard.amount))
                else -> {}
            }
        }
        if (p.skipped) g = g.copy(act = (g.act + 1) % acts.size)
        g = g.copy(monsterHexes = g.monsterHexes + p.hexDamage, ink = g.ink + p.ink)
        // Cards leave the hand: the top card is worn out, played blots are thrown away, the rest are discarded.
        val used = pieces.filterIsInstance<Piece.Hand>().map { hand[it.index] }
        val topIndex = (top as? Piece.Hand)?.let { hand[it.index] }
        val thrown = used.filter { deck[it].blot }
        val left = hand.filter { it !in used }
        // Blots left in the hand hurt.
        val blotHurt = left.count { deck[it].blot } * 2
        g = g.copy(hand = emptyList(), worn = g.worn + listOfNotNull(topIndex?.takeIf { it !in thrown }),
            discard = g.discard + used.filter { it != topIndex && it !in thrown } + left,
            vowelWorn = g.vowelWorn || top == Piece.Vowel, prep = Prep(), usedItems = emptySet())
        g = g.dropCards(thrown)
        g = g.strike(p, blotHurt)
        return g.copy(log = log + ("p${if (splay == Splay.LEFT) 'L' else 'R'}" + pieces.joinToString(".") { tokenOf(it) }))
    }

    private fun playCurrent(pieces: List<Piece>, splay: Splay): Wordsworn {
        val p = preview(pieces, splay)
        val top = topOf(pieces, splay)
        val topIndex = (top as? Piece.Hand)?.let { hand[it.index] }
        val card = topIndex?.let { deck[it] }
        var g = this
        repeat(if (Keepsake.SCHOLARS_CROWN in keepsakes) 2 else 1) {
            when (card?.power) {
                Power.STAR -> g = g.copy(stars = g.stars + card.amount)
                Power.HEAL -> g = g.copy(hp = minOf(g.maxHp, g.hp + card.amount))
                Power.DRAW -> g = g.copy(extraDraw = g.extraDraw + card.amount)
                Power.RECALL -> g = g.copy(worn = g.worn.dropLast(card.amount), discard = g.discard + g.worn.takeLast(card.amount))
                else -> {}
            }
        }
        if (p.skipped) g = g.copy(act = (g.act + 1) % acts.size)
        g = g.copy(monsterHexes = g.monsterHexes + p.hexDamage, ink = g.ink + p.ink)
        val played = pieces.filterIsInstance<Piece.Hand>().map { hand[it.index] }
        val thrown = played.filter { deck[it].blot }
        val heldBlots = hand.count { deck[it].blot && it !in played }
        // Resolve the enemy before moving this turn's cards into discard/fatigue.
        g = g.strike(p, heldBlots * 2, drawNext = false)
        if (g.offer == null && !g.won) {
            g = g.copy(hand = emptyList(), worn = g.worn + listOfNotNull(topIndex),
                discard = g.discard + hand.filter { it != topIndex && it !in thrown },
                vowelWorn = g.vowelWorn || top == Piece.Vowel, prep = Prep(), usedItems = emptySet())
        }
        g = g.copy(penaltyStock = g.penaltyStock + thrown.map { deck[it].letter }).dropCards(thrown)
        if (g.fighting) g = g.drawHand()
        return g.copy(log = log + (if (pieces.isEmpty()) "x" else "p${if (splay == Splay.LEFT) 'L' else 'R'}" + pieces.joinToString(".") { tokenOf(it) }))
    }

    /** No word this turn: the hand is discarded (blots still hurt) and the monster acts. The wild letter gives its ink. */
    fun pass(): Wordsworn? {
        if (!fighting) return null
        if (currentRules) return playCurrent(emptyList(), Splay.RIGHT)
        val blotHurt = hand.count { deck[it].blot } * 2
        val ink = (if (currentRules) permanentWilds else 1) * (1 + if (Keepsake.WILD_FEATHER in keepsakes) 1 else 0)
        val g = copy(hand = emptyList(), discard = discard + hand, prep = Prep(), usedItems = emptySet(), ink = this.ink + ink)
        return g.strike(Clash(ink = ink, blocks = prep.blocks), blotHurt).copy(log = log + "x")
    }

    /** Hits land, hexes bite, then the monster takes its turn and the next hand is drawn. */
    private fun strike(p: Clash, blotHurt: Int, drawNext: Boolean = true): Wordsworn {
        var g = this
        var clash = p
        // Damage, flipping to the second stage when the first runs out; extra damage carries over.
        var hpLeft = g.monsterHp - p.damage
        if (hpLeft <= 0 && g.stage == 1) {
            g = g.flip(-hpLeft); clash = clash.copy(flipped = true)
            hpLeft = g.monsterHp
        } else g = g.copy(monsterHp = hpLeft)
        if (g.stage == 2 && g.monsterHp <= 0) return g.beaten(clash.copy(beaten = true))
        // Hexes on the monster hurt it, then fade by one.
        if (!currentRules && g.monsterHexes > 0) {
            val bite = if (monster.special == Special.NO_HEX) 0 else g.monsterHexes
            g = g.copy(monsterHp = g.monsterHp - bite, monsterHexes = g.monsterHexes - 1)
            clash = clash.copy(hexDamage = bite)
            if (g.monsterHp <= 0) {
                if (g.stage == 1) { g = g.flip(-g.monsterHp); clash = clash.copy(flipped = true) }
                else return g.beaten(clash.copy(beaten = true))
            }
        }
        // The monster's turn (it rests if it has just flipped).
        if (!g.stunned) {
            val a = g.intent
            val attack = g.attackOf(a)
            val taken = (attack - p.blocks).coerceAtLeast(0)
            val mirror = if (a.hexes > 0 && Keepsake.HEX_MIRROR in keepsakes) 1 else 0
            g = g.copy(hp = g.hp - taken, hexes = g.hexes + a.hexes, monsterStars = g.monsterStars + a.stars,
                monsterHp = minOf(g.monsterMax, g.monsterHp + a.heal), monsterHexes = g.monsterHexes + mirror)
            repeat(a.blots) { g = g.addBlot() }
            if (attack > 0 && monster.special == Special.EAT && g.discard.isNotEmpty()) {
                val eaten = g.discard[g.roll().nextInt(g.discard.size)]
                g = g.copy(discard = g.discard - eaten, worn = g.worn + eaten, rolls = g.rolls + 1)
            }
            clash = clash.copy(action = a.describe(g.monsterStars - a.stars), taken = taken)
            g = g.copy(act = (g.act + 1) % g.acts.size)
        } else clash = clash.copy(stunned = true)
        g = g.copy(stunned = false)
        if (monster.special == Special.REGEN) g = g.copy(monsterHp = minOf(g.monsterMax, g.monsterHp + 1))
        // Hexes on the player hurt, then fade by one; so do blots left in the hand.
        val hexBite = if (currentRules) 0 else g.hexes
        g = g.copy(hp = g.hp - hexBite - blotHurt, hexes = if (currentRules) g.hexes else (g.hexes - 1).coerceAtLeast(0))
        clash = clash.copy(hexTaken = hexBite + blotHurt)
        if (g.hp <= 0 && Keepsake.PHOENIX_QUILL in keepsakes && !g.phoenixUsed) g = g.copy(hp = 10, phoenixUsed = true)
        g = g.copy(turn = g.turn + 1, last = clash)
        return if (drawNext) g.drawHand() else g
    }

    private fun flip(carry: Int): Wordsworn {
        val hp = scale(monster.hp2) - carry
        return copy(stage = 2, monsterHp = hp, act = 0, stunned = true,
            monsterStars = if (currentRules && monster.special != Special.STARS) monsterStars else startStars(),
            monsterHexes = monsterHexes + if (Keepsake.THORN_RING in keepsakes) 2 else 0)
    }

    private fun startStars() = if (monster.special == Special.STARS) 2 else 0

    // ---------------- Items ----------------

    /** Why item [k] can't be used now, or null. */
    fun itemProblem(k: Int): String? {
        val it = items.getOrNull(k) ?: return "No such item."
        if (!fighting) return "Not now."
        if (k in usedItems) return "Each item works once a turn."
        if (k in spentItems) return "Used up for this fight."
        if (ink < it.cost) return "Not enough ink."
        if (it.kind == ItemKind.BOOKMARK && worn.isEmpty()) return "No card is worn out yet."
        if (it.kind == ItemKind.LUTE && draw.isEmpty() && discard.isEmpty()) return "No cards left to draw."
        if (it.kind == ItemKind.ANTIDOTE && hexes == 0) return "You have no hexes."
        if (it.kind in listOf(ItemKind.SEAL, ItemKind.PURSE, ItemKind.ENCORE) && stars < 1) return "You need 1 star."
        if (it.kind in listOf(ItemKind.BRAND, ItemKind.LENS) && monsterHexes == 0) return "The monster has no hexes."
        if (it.kind == ItemKind.LENS && monster.special == Special.NO_HEX) return "This monster is immune to hex damage."
        if (it.kind == ItemKind.REFRAIN && monsterHexes < 2) return "The monster needs 2 hexes."
        if (it.kind == ItemKind.REFRAIN && draw.isEmpty() && discard.isEmpty()) return "No cards left to draw."
        if (it.kind == ItemKind.ENCORE && worn.isEmpty()) return "No card is worn out yet."
        if (it.kind in listOf(ItemKind.BANNER, ItemKind.LEDGER, ItemKind.BRIDGE) && stars < 1) return "You need 1 star."
        if (it.kind in listOf(ItemKind.MARGIN, ItemKind.ASH, ItemKind.CHORUS) && monsterHexes < 2) return "The monster needs 2 hexes."
        if (it.kind == ItemKind.LEDGER && draw.isEmpty() && discard.isEmpty()) return "No cards left to draw."
        if (it.kind in listOf(ItemKind.BANNER, ItemKind.CHORUS) && hp >= maxHp) return "Your health is full."
        if (it.kind == ItemKind.ASH && monster.special == Special.NO_HEX) return "This monster is immune to hex damage."
        return null
    }

    fun useItem(k: Int): Wordsworn? {
        if (itemProblem(k) != null) return null
        val item = items[k]
        var g = copy(ink = ink - item.cost, usedItems = usedItems + k, spentItems = if (item.kind.single) spentItems + k else spentItems)
        g = when (item.kind) {
            ItemKind.OATH_SHIELD -> g.copy(prep = g.prep.copy(blocks = g.prep.blocks + if (item.upgraded) 5 else 3))
            ItemKind.CAULDRON -> g.copy(monsterHexes = g.monsterHexes + if (item.upgraded) 3 else 2)
            ItemKind.LUTE -> g.drawCards(1)
            ItemKind.THROWING_KNIFE -> g.copy(prep = g.prep.copy(hits = g.prep.hits + 2))
            ItemKind.BUCKLER -> g.copy(prep = g.prep.copy(blocks = g.prep.blocks + 2))
            ItemKind.NETTLES -> g.copy(monsterHexes = g.monsterHexes + 1)
            ItemKind.QUILL_DART -> g.copy(prep = g.prep.copy(hits = g.prep.hits + 4))
            ItemKind.IRON_COVER -> g.copy(prep = g.prep.copy(blocks = g.prep.blocks + 4))
            ItemKind.DRAUGHT -> g.copy(hp = minOf(g.maxHp, g.hp + 5))
            ItemKind.SPARE_NIB -> g.copy(prep = g.prep.copy(wilds = g.prep.wilds + 1))
            ItemKind.HOURGLASS -> g.copy(act = (g.act + 1) % g.acts.size)
            ItemKind.BOOKMARK -> g.copy(worn = g.worn.dropLast(1), hand = g.hand + g.worn.last())
            ItemKind.ANTIDOTE -> g.copy(hexes = 0)
            ItemKind.GOLD_LEAF -> g.copy(stars = g.stars + 2)
            ItemKind.SEAL -> g.copy(stars = g.stars - 1, prep = g.prep.copy(blocks = g.prep.blocks + 3))
            ItemKind.BRAND -> minOf(3, g.monsterHexes).let { n -> g.copy(monsterHexes = g.monsterHexes - n, prep = g.prep.copy(hits = g.prep.hits + 2 * n)) }
            ItemKind.LENS -> g.loseMonsterHp(g.monsterHexes)
            ItemKind.PURSE -> g.copy(stars = g.stars - 1, ink = g.ink + 2)
            ItemKind.REFRAIN -> g.copy(monsterHexes = g.monsterHexes - 2).drawCards(1)
            ItemKind.ENCORE -> g.copy(stars = g.stars - 1, worn = g.worn.dropLast(1), hand = g.hand + g.worn.last())
            ItemKind.BANNER -> g.copy(stars = g.stars - 1, hp = minOf(g.maxHp, g.hp + 2))
            ItemKind.MARGIN -> g.copy(monsterHexes = g.monsterHexes - 2, prep = g.prep.copy(blocks = g.prep.blocks + 4))
            ItemKind.LEDGER -> g.copy(stars = g.stars - 1).drawCards(1)
            ItemKind.ASH -> g.copy(monsterHexes = g.monsterHexes - 2).loseMonsterHp(4)
            ItemKind.BRIDGE -> g.copy(stars = g.stars - 1, prep = g.prep.copy(hits = g.prep.hits + 3))
            ItemKind.CHORUS -> g.copy(monsterHexes = g.monsterHexes - 2, hp = minOf(g.maxHp, g.hp + 3))
        }
        return g.copy(log = log + "i$k")
    }

    /** Direct HP loss bypasses block and can end a battle during preparation. */
    private fun loseMonsterHp(n: Int): Wordsworn {
        var g = copy(monsterHp = monsterHp - n)
        var clash = Clash(damage = n)
        if (g.stage == 1 && g.monsterHp <= 0) {
            g = g.flip(-g.monsterHp)
            clash = clash.copy(flipped = true, stunned = true)
        }
        return if (g.stage == 2 && g.monsterHp <= 0) g.beaten(clash.copy(beaten = true)) else g.copy(last = clash)
    }

    // ---------------- Cards and piles ----------------

    private fun roll() = Random(seed * 1_000_003L + rolls * 7_919L + 17)

    /** Draws [n] cards into the hand, shuffling the discards in when the pile runs out. */
    private fun drawCards(n: Int): Wordsworn {
        var g = this
        repeat(n) {
            if (g.draw.isEmpty()) {
                if (g.discard.isEmpty()) return g
                g = g.copy(draw = g.discard.shuffled(g.roll()), discard = emptyList(), rolls = g.rolls + 1)
            }
            g = g.copy(hand = g.hand + g.draw.first(), draw = g.draw.drop(1))
        }
        return g
    }

    private fun drawHand(): Wordsworn = if (over) this else copy(extraDraw = 0).drawCards(handSize + extraDraw)

    private fun addBlot(): Wordsworn {
        if (currentRules) {
            val letter = penaltyStock.firstOrNull() ?: return this
            return copy(deck = deck + Card(letter, Edge(), Edge(), Power.NONE, blot = true),
                discard = discard + deck.size, penaltyStock = penaltyStock.drop(1))
        }
        val r = roll()
        val letter = BLOT_LETTERS[r.nextInt(BLOT_LETTERS.length)]
        return copy(deck = deck + Card(letter, Edge(), Edge(), Power.NONE, blot = true), discard = discard + deck.size, rolls = rolls + 1)
    }

    /** Takes cards out of the deck for good, renumbering the piles. */
    private fun dropCards(gone: List<Int>): Wordsworn {
        if (gone.isEmpty()) return this
        val keep = deck.indices.filter { it !in gone }
        val at = keep.withIndex().associate { (n, i) -> i to n }
        fun fix(l: List<Int>) = l.mapNotNull { at[it] }
        return copy(deck = keep.map { deck[it] }, draw = fix(draw), hand = fix(hand), discard = fix(discard), worn = fix(worn))
    }

    // ---------------- Between fights ----------------

    private fun beaten(clash: Clash): Wordsworn {
        val g = copy(monsterHp = 0, last = clash, turn = turn + 1)
        if (fight == fights - 1) return g.copy(offer = null, hand = emptyList())
        val bonus = (if (currentRules) 0 else if (monster.boss) 3 else 2) + if (Keepsake.LUCKY_STAR in keepsakes) 1 else 0
        val heal = (if (currentRules) 0 else restOf(level)) + if (Keepsake.SALVE in keepsakes) 3 else 0
        // Everything goes back into one pile; ink and hexes are gone, stars stay to spend.
        return g.copy(stars = stars + bonus, hp = minOf(maxHp, hp + heal), ink = 0, hexes = 0, monsterHexes = 0, monsterStars = 0,
            draw = deck.indices.toList(), hand = emptyList(), discard = emptyList(), worn = emptyList(), vowelWorn = false,
            extraDraw = 0, usedItems = emptySet(), spentItems = emptySet(), prep = Prep(),
            shop = if (currentRules) g.shop?.copy(refreshed = false) else g.shop, offer = Offer.Paths(pathsFor(monster)))
    }

    private fun pathsFor(m: Monster): List<Path> {
        if (currentRules) return currentPaths(m)
        val r = Random(seed * 131 + fight)
        if (m.boss) return listOf(
            Path(listOf(Step.Cards(3, add = true), Step.Keepsakes(boss = true)), "Among the boss's hoard, something rare still glows."),
            Path(listOf(Step.Cards(3, add = true), Step.CoreUpgrade, Step.Heal(6)), "You rest by the fire and sharpen your favourite tools."),
        )
        val sets = listOf(
            listOf(Path(listOf(Step.Cards(2, add = false), Step.Items(2)), "You search the lair and find a tidy stash of gear."),
                Path(listOf(Step.Cards(2, add = false), Step.Keepsakes(boss = false), Step.Hurt(3)), "You follow the monster's trail into a thorny thicket.")),
            listOf(Path(listOf(Step.Cards(3, add = false), Step.Heal(5)), "A quiet library nook: time to read and rest."),
                Path(listOf(Step.Upgrade, Step.Stars(2)), "A friendly printer offers to reset one of your letters in fine type.")),
            listOf(Path(listOf(Step.Items(2), Step.Heal(4)), "A travelling pedlar waves you over."),
                Path(listOf(Step.Keepsakes(boss = false), Step.Blots(1)), "An old chest, sealed with a smudge of ink.")),
            listOf(Path(listOf(Step.Cards(2, add = false), Step.Upgrade), "You copy out a page of fresh letters."),
                Path(listOf(Step.Stars(3), Step.Heal(3)), "The townsfolk cheer and press gold stars into your hands.")),
        )
        return sets[r.nextInt(sets.size)]
    }

    /** Rewards belong to the encountered enemy; all paths grow the deck in the appropriate way. */
    private fun currentPaths(m: Monster): List<Path> = if (m.boss) listOf(
        Path(listOf(Step.Cards(3, true), Step.Keepsakes(true)), "A rare treasure waits beyond the ruined gate."),
        Path(listOf(Step.Cards(2, true), Step.Keepsakes(true), Step.Heal(5)), "You shelter among the shelves and study your prize."),
    ) else when (m.ordinal % 3) {
        0 -> listOf(Path(listOf(Step.Cards(3, false), Step.Items(2)), "The creature guarded a workshop full of useful tools."),
            Path(listOf(Step.Cards(2, false), Step.Keepsakes(false), Step.Hurt(3)), "An enchanted trinket waits behind the brambles."))
        1 -> listOf(Path(listOf(Step.Cards(3, false), Step.Heal(5)), "A quiet reading room offers a moment of rest."),
            Path(listOf(Step.Cards(2, false), Step.Upgrade, Step.Stars(2)), "A printer rewards you for rescuing a damaged manuscript."))
        else -> listOf(Path(listOf(Step.Cards(2, false), Step.Keepsakes(false), Step.Blots(1)), "A sealed drawer contains a gift and a stain."),
            Path(listOf(Step.Cards(3, false), Step.Stars(3)), "The librarians return your borrowed books with a reward."))
    }

    /** Takes option [k] of the current offer (-1 skips, where skipping is allowed). */
    fun choose(k: Int): Wordsworn? {
        val g = when (val o = offer ?: return null) {
            Offer.Heroes -> if (currentRules && k in 0..11) copy(coreLoadout = k / 3).begin(Hero.entries[k % 3])
                else if (!currentRules) Hero.entries.getOrNull(k)?.let { begin(it) } else null
            is Offer.Paths -> o.paths.getOrNull(k)?.let { copy(todo = it.steps).next() }
            is Offer.Cards -> when {
                k == -1 -> if (requiredChoice) null else returnCards(o.cards).next()
                k !in o.cards.indices -> null
                o.add -> returnCards(o.cards.filterIndexed { i, _ -> i != k }).copy(deck = deck + o.cards[k], draw = draw + deck.size).next()
                else -> returnCards(o.cards.filterIndexed { i, _ -> i != k }).copy(offer = Offer.Replace(o.cards[k]))
            }
            is Offer.Replace -> when {
                k == -1 -> if (requiredChoice || o.purchased) null else returnCards(listOf(o.card)).next()
                k !in deck.indices || deck[k].blot -> null
                else -> copy(deck = deck.toMutableList().also { it[k] = o.card }).next()
            }
            is Offer.Items -> when {
                k == -1 -> if (requiredChoice) null else returnItems(o.kinds).next()
                k !in o.kinds.indices -> null
                else -> returnItems(o.kinds.filterIndexed { i, _ -> i != k }).copy(items = items + Item(o.kinds[k])).next()
            }
            is Offer.Keepsakes -> when {
                k == -1 -> if (requiredChoice) null else returnKeepsakes(o.drawn).next()
                k !in o.sides.indices -> null
                else -> returnKeepsakes(o.drawn.filter { o.sides[k] !in listOf(Keepsake.pairs[it].first, Keepsake.pairs[it].second) }).gain(o.sides[k]).next()
            }
            Offer.Upgrade -> when {
                k == -1 -> if (requiredChoice) null else next()
                k !in deck.indices || deck[k].upgraded || deck[k].blot -> null
                else -> copy(deck = deck.toMutableList().also { it[k] = it[k].upgrade() }).next()
            }
            Offer.CoreUpgrade -> when (k) {
                0 -> copy(items = items.map { if (it.kind.core) it.copy(upgraded = true) else it }).next()
                1 -> copy(wildUpgraded = true).next()
                else -> null
            }
            is Offer.Shop -> if (k == -1) nextFight() else null
        } ?: return null
        return g.copy(log = log + "c$k")
    }

    fun toggleTwist(index: Int): Wordsworn? {
        if (!currentRules || offer != Offer.Heroes) return null
        val twist = WordswornTwist.entries.getOrNull(index) ?: return null
        return copy(twists = if (twist in twists) twists - twist else twists + twist, log = log + "t$index")
    }

    /** Buys shop goods [k]; an upgrade or a keepsake then asks which. */
    fun buy(k: Int): Wordsworn? {
        val o = offer as? Offer.Shop ?: return null
        val goods = o.goods.getOrNull(k) ?: return null
        if (currentRules) return buyCurrent(k, goods, o)
        if (k in o.sold || stars < goods.price) return null
        val left = o.copy(sold = o.sold + k)
        val paid = copy(stars = stars - goods.price, offer = left, shop = left)
        val g = when (goods) {
            is Goods.ForItem -> paid.copy(items = items + Item(goods.kind))
            is Goods.ForKeepsake -> paid.copy(offer = Offer.Keepsakes(listOf(goods.sides.first, goods.sides.second)), todo = paid.shopReturn())
            Goods.ForUpgrade -> if (deck.all { it.upgraded || it.blot }) return null else paid.copy(offer = Offer.Upgrade, todo = paid.shopReturn())
            Goods.ForRest -> if (hp >= maxHp) return null else paid.copy(hp = minOf(maxHp, hp + 5))
            is Goods.ForLetter, Goods.Empty -> return null
        }
        return g.copy(log = log + "b$k")
    }

    /** Marks the way back to the shop after a purchase that needs a choice. */
    private fun shopReturn(): List<Step> = listOf(ShopStep)

    private fun gain(k: Keepsake): Wordsworn = copy(keepsakes = keepsakes + k,
        maxHp = if (k == Keepsake.HEART_LOCKET) maxHp + 5 else maxHp, hp = if (k == Keepsake.HEART_LOCKET) hp + 5 else hp)

    /** Moves on to the next reward step, or the shop when they're done. */
    private fun next(): Wordsworn {
        var g = this
        while (g.todo.isNotEmpty()) {
            if (currentRules && g.lost) return g.copy(offer = null, todo = emptyList())
            val s = g.todo.first()
            g = g.copy(todo = g.todo.drop(1))
            val r = Random(seed * 257 + fight * 13 + g.todo.size + g.deck.size)
            when (s) {
                is Step.Cards -> if (!currentRules) return g.copy(offer = Offer.Cards(List(s.offer) { g.newCard(r) }, s.add))
                    else if (g.library.isNotEmpty()) return g.copy(library = g.library.drop(s.offer), offer = Offer.Cards(g.library.take(s.offer), s.add))
                // Nothing left to offer (the player owns them all): the step is skipped.
                is Step.Items -> (if (currentRules) g.itemStock.take(s.offer) else itemPool(g).shuffled(r).take(s.offer)).let {
                    if (it.isNotEmpty()) return g.copy(offer = Offer.Items(it), itemStock = if (currentRules) g.itemStock.drop(s.offer) else g.itemStock)
                }
                is Step.Keepsakes -> if (currentRules && !s.boss) {
                    val taken = g.keepsakeStock.take(2)
                    if (taken.isNotEmpty()) return g.copy(keepsakeStock = g.keepsakeStock.drop(2),
                        offer = Offer.Keepsakes(taken.flatMap { listOf(Keepsake.pairs[it].first, Keepsake.pairs[it].second) }, taken))
                } else (if (s.boss) bossKeepsakes(g).shuffled(r).take(2)
                    else Keepsake.pairs.filter { (x, y) -> x !in g.keepsakes && y !in g.keepsakes }.shuffled(r).take(2).flatMap { listOf(it.first, it.second) })
                    .let { if (it.isNotEmpty()) return g.copy(offer = Offer.Keepsakes(it)) }
                Step.Upgrade -> if (g.deck.any { !it.upgraded && !it.blot }) return g.copy(offer = Offer.Upgrade)
                Step.CoreUpgrade -> return g.copy(offer = Offer.CoreUpgrade)
                is Step.Heal -> g = g.copy(hp = minOf(g.maxHp, g.hp + s.n))
                is Step.Hurt -> g = g.copy(hp = maxOf(if (currentRules) 0 else 1, g.hp - s.n))
                is Step.Stars -> g = g.copy(stars = g.stars + s.n)
                is Step.Blots -> repeat(s.n) { g = g.addBlot() }
                ShopStep -> return g.copy(offer = g.shop ?: g.openShop())
            }
        }
        if (currentRules && g.lost) return g.copy(offer = null, todo = emptyList())
        if (currentRules) return g.prepareShop().let { it.copy(offer = it.shop) }
        return g.openShop().let { g.copy(offer = it, shop = it) }
    }

    private fun openShop(): Offer.Shop {
        val r = Random(seed * 389 + fight)
        val goods = itemPool(this).shuffled(r).take(3).map { Goods.ForItem(it) } +
            Keepsake.pairs.filter { (x, y) -> x !in keepsakes && y !in keepsakes }.shuffled(r).take(1).map { Goods.ForKeepsake(it) } +
            listOf(Goods.ForUpgrade, Goods.ForRest)
        return Offer.Shop(goods, emptySet())
    }

    private fun returnCards(cards: List<Card>) = if (currentRules) copy(library = library + cards) else this
    private fun returnItems(kinds: List<ItemKind>) = if (currentRules) copy(itemStock = itemStock + kinds) else this
    private fun returnKeepsakes(pairs: List<Int>) = if (currentRules) copy(keepsakeStock = keepsakeStock + pairs) else this

    private fun prepareShop(): Wordsworn {
        if (shop != null) return this
        var g = copy(shop = Offer.Shop(List(8) { Goods.Empty } + Goods.ForUpgrade, emptySet()))
        for (slot in 0..7) g = g.refillSlot(slot)
        return g
    }

    private fun refillSlot(slot: Int): Wordsworn {
        val o = shop ?: return this
        var g = this
        val goods = when (slot) {
            in 0..2 -> itemStock.firstOrNull()?.let { g = g.copy(itemStock = itemStock.drop(1)); Goods.ForItem(it) }
            in 3..4 -> keepsakeStock.firstOrNull()?.let { g = g.copy(keepsakeStock = keepsakeStock.drop(1)); Goods.ForKeepsake(Keepsake.pairs[it]) }
            in 5..7 -> library.firstOrNull()?.let { g = g.copy(library = library.drop(1)); Goods.ForLetter(it) }
            else -> null
        } ?: Goods.Empty
        return g.copy(shop = o.copy(goods = o.goods.toMutableList().also { it[slot] = goods }))
    }

    private fun buyCurrent(k: Int, goods: Goods, o: Offer.Shop): Wordsworn? {
        if (goods == Goods.Empty || goods == Goods.ForRest || stars < goods.price) return null
        if (goods == Goods.ForUpgrade && deck.none { !it.blot && !it.upgraded }) return null
        if (goods is Goods.ForLetter && deck.none { !it.blot }) return null
        var g = copy(stars = stars - goods.price, shop = o)
        if (k < 8) g = g.refillSlot(k)
        g = when (goods) {
            is Goods.ForItem -> g.copy(items = g.items + Item(goods.kind), offer = g.shop)
            is Goods.ForKeepsake -> g.copy(offer = Offer.Keepsakes(listOf(goods.sides.first, goods.sides.second)), todo = listOf(ShopStep))
            is Goods.ForLetter -> g.copy(offer = Offer.Replace(goods.card, purchased = true), todo = listOf(ShopStep))
            Goods.ForUpgrade -> g.copy(offer = Offer.Upgrade, todo = listOf(ShopStep))
            else -> return null
        }
        return g.copy(log = log + "b$k")
    }

    /** Once per visit, cycle a physical shop row or column back into its stock. */
    fun refreshShop(group: Int): Wordsworn? {
        val o = offer as? Offer.Shop ?: return null
        if (!currentRules || o.refreshed || group !in 0..5) return null
        val slots = listOf(listOf(0, 1, 2), listOf(3, 4), listOf(5, 6, 7), listOf(0, 3, 5), listOf(1, 6), listOf(2, 4, 7))[group]
        var g = copy(shop = o.copy(refreshed = true))
        for (slot in slots) when (val goods = o.goods[slot]) {
            is Goods.ForItem -> g = g.returnItems(listOf(goods.kind))
            is Goods.ForKeepsake -> g = g.returnKeepsakes(listOf(Keepsake.pairs.indexOf(goods.sides)))
            is Goods.ForLetter -> g = g.returnCards(listOf(goods.card))
            else -> {}
        }
        for (slot in slots) g = g.refillSlot(slot)
        return g.copy(offer = g.shop, log = log + "r$group")
    }

    private fun itemPool(g: Wordsworn) = ItemKind.entries.filter { !it.core && g.items.none { i -> i.kind == it } }
    private fun bossKeepsakes(g: Wordsworn) = Keepsake.entries.filter { it.boss && it !in g.keepsakes }

    /** A new card for this hero: a letter weighted like English, with icons by rarity and one of the hero's abilities. */
    private fun newCard(r: Random): Card {
        val letter = GROWTH[r.nextInt(GROWTH.length)]
        val powers = hero!!.powers + if (currentRules) listOf(Power.HEX, Power.STAR) else emptyList()
        return makeCard(letter, powers[r.nextInt(powers.size)], r)
    }

    private fun nextFight(): Wordsworn {
        val next = fight + 1
        val m = schedule(level, seed, rulesVersion)[next]
        return copy(fight = next, offer = null, todo = emptyList(), shop = if (currentRules) shop else null).meet(m)
    }

    /** Sets up a fight against [m] with a fresh shuffle. */
    private fun meet(m: Monster): Wordsworn {
        val order = deck.indices.shuffled(Random(seed * 131 + fight * 7 + 3))
        val g = copy(monster = m, stage = 1, monsterHp = (m.hp1 * factorOf(level)).toInt().coerceAtLeast(1), act = 0, stunned = false,
            monsterHexes = if (Keepsake.THORN_RING in keepsakes) 2 else 0, monsterStars = if (m.special == Special.STARS) 2 else 0,
            ink = if (Keepsake.INKWELL in keepsakes) 2 else 0, hexes = 0, draw = order, hand = emptyList(), discard = emptyList(),
            worn = emptyList(), vowelWorn = false, extraDraw = 0, usedItems = emptySet(), spentItems = emptySet(), prep = Prep(), turn = 0, last = null)
        return g.drawHand()
    }

    private fun begin(h: Hero): Wordsworn {
        val r = Random(h.ordinal * 7_919L + 11)
        // The starting deck: fixed for each hero, so its first cards feel familiar run after run.
        val cards = h.starter.mapIndexed { i, ch -> makeCard(ch, h.powers[i % h.powers.size], r) }
        val hp = h.maxHp + listOf(6, 0, 0, -2)[level.ordinal]
        var g = copy(hero = h, offer = null, deck = cards, hp = hp, maxHp = hp, items = listOf(Item(h.core), Item(h.extra)), stars = 0)
        if (currentRules) {
            val powers = h.powers + Power.HEX + Power.STAR
            val starting = h.starter.mapIndexed { i, ch -> makeCard(ch, powers[i % powers.size], r) }.toMutableList()
            // Every hero can generate both resources needed by their core abilities.
            starting[0] = starting[0].copy(power = Power.HEX, amount = 2)
            starting[1] = starting[1].copy(power = Power.STAR, amount = 1)
            val cores = h.cores(coreLoadout)
            val health = (if (level == LogicLevel.EASY) 25 else 20) + if (WordswornTwist.RESERVE in twists) 5 else 0
            g = g.copy(deck = starting, hp = health, maxHp = health, items = (cores + h.extra).map { Item(it) },
                library = List(50) { g.newCard(r) }.shuffled(Random(seed)),
                itemStock = itemPool(g).shuffled(Random(seed * 31)),
                keepsakeStock = Keepsake.pairs.indices.shuffled(Random(seed * 37)),
                penaltyStock = penaltyStock.shuffled(Random(seed * 41)))
            g = g.prepareShop()
        }
        if (level == LogicLevel.EXPERT) g = g.addBlot().copy(discard = emptyList())
        if (WordswornTwist.SMUDGES in twists) repeat(2) { g = g.addBlot().copy(discard = emptyList()) }
        return g.meet(schedule(level, seed, rulesVersion)[0])
    }

    // ---------------- Hints ----------------

    /** The strongest word the hand can make now, splayed the better way, or null when nothing fits. */
    fun bestPlay(): Pair<List<Piece>, Splay>? {
        if (!fighting) return null
        val cards = hand.map { deck[it] }
        val counts = IntArray(26)
        cards.forEach { counts[it.letter - 'A']++ }
        val vowel = if (vowelWorn) null else monster.weak
        val longest = cards.size + wilds + if (vowel != null) 1 else 0
        var best: Pair<List<Piece>, Splay>? = null
        var bestScore = Double.NEGATIVE_INFINITY
        val need = IntArray(26)
        var budget = 50_000
        for (word in Lexicon.all) {
            if (word.length < 2 || word.length > longest) continue
            need.fill(0)
            for (ch in word) need[ch - 'A']++
            var missing = 0
            for (i in 0 until 26) if (need[i] > counts[i]) missing += need[i] - counts[i]
            val vowelFills = if (vowel != null && need[vowel - 'A'] > counts[vowel - 'A']) 1 else 0
            if (missing - vowelFills > wilds) continue
            val choices = if (currentRules) assignments(word, cards, vowel) else sequenceOf(assign(word, cards, vowel)).filterNotNull()
            for (pieces in choices) {
                for (splay in Splay.entries) {
                    val s = score(pieces, splay)
                    if (s > bestScore) { bestScore = s; best = pieces to splay }
                }
                if (currentRules && --budget == 0) return best
            }
        }
        return best
    }

    /** Duplicate letters and special letters may choose different top abilities and fatigue costs. */
    private fun assignments(word: String, cards: List<Card>, vowel: Char?): Sequence<List<Piece>> = sequence {
        val used = BooleanArray(cards.size)
        val pieces = mutableListOf<Piece>()
        suspend fun SequenceScope<List<Piece>>.walk(at: Int, vowelLeft: Boolean, wildLeft: Int) {
            if (at == word.length) { yield(pieces.toList()); return }
            val ch = word[at]
            val equivalent = mutableSetOf<Card>()
            for (i in cards.indices) if (!used[i] && cards[i].letter == ch && equivalent.add(cards[i])) {
                used[i] = true; pieces += Piece.Hand(i)
                walk(at + 1, vowelLeft, wildLeft)
                pieces.removeAt(pieces.lastIndex); used[i] = false
            }
            if (vowelLeft && vowel == ch) {
                pieces += Piece.Vowel; walk(at + 1, false, wildLeft); pieces.removeAt(pieces.lastIndex)
            }
            if (wildLeft > 0) {
                pieces += Piece.Wild(ch); walk(at + 1, vowelLeft, wildLeft - 1); pieces.removeAt(pieces.lastIndex)
            }
        }
        walk(0, vowel != null, wilds)
    }

    /** Pieces for [word]: the strongest free card for each letter, then the vowel, then wild letters. */
    private fun assign(word: String, cards: List<Card>, vowel: Char?): List<Piece>? {
        val free = cards.indices.toMutableList()
        var vowelLeft = vowel != null
        var wildLeft = wilds
        return word.map { ch ->
            val card = free.filter { cards[it].letter == ch }.maxByOrNull { cards[it].right.hits + cards[it].left.blocks }
            when {
                card != null -> { free.remove(card); Piece.Hand(card) }
                vowelLeft && ch == vowel -> { vowelLeft = false; Piece.Vowel }
                wildLeft > 0 -> { wildLeft--; Piece.Wild(ch) }
                else -> return null
            }
        }
    }

    /** How good a play looks: damage, damage prevented, and a little for ink, hexes and cards kept. */
    fun score(pieces: List<Piece>, splay: Splay): Double {
        val p = preview(pieces, splay)
        val incoming = if (stunned) 0 else attackOf(if (p.skipped) acts[(act + 1) % acts.size] else intent)
        val saved = minOf(p.blocks, incoming)
        val kill = if (p.damage >= monsterHp) 6.0 else 0.0
        val blots = pieces.count { it is Piece.Hand && deck[hand[it.index]].blot }
        return p.damage + saved * 1.1 + p.ink * 0.6 + p.hexDamage * 1.5 + kill + blots * 2.5 + if (p.skipped && incoming > 0) 1.0 else 0.0
    }

    fun hinted() = if (currentRules && !fighting) this else copy(hints = hints + 1, log = log + "h")

    companion object {
        const val VOWELS = "AEIOU"
        private const val BLOT_LETTERS = "QXZJKVW"
        /** Letters for new cards, weighted like everyday English. */
        private const val GROWTH = "EEEEEAAAAIIIOOOUNNNRRRSSSTTTLLDDGCCMMPPBHHYFWKVJXZ"

        fun factorOf(level: LogicLevel) = listOf(0.8, 1.0, 1.15, 1.25)[level.ordinal]
        fun restOf(level: LogicLevel) = listOf(6, 4, 2, 0)[level.ordinal]
        /** How many books a run has. */
        fun booksOf(level: LogicLevel, rulesVersion: Int = 3) = if (rulesVersion >= 3) 3 else listOf(1, 2, 3, 3)[level.ordinal]

        /** Each book: two of its three monsters, in a shuffled order, then its boss. */
        fun schedule(level: LogicLevel, seed: Long, rulesVersion: Int = 3): List<Monster> = (0 until booksOf(level, rulesVersion)).flatMap { b ->
            val book = Monster.entries.filter { it.book == b }
            book.filter { !it.boss }.shuffled(Random(seed * 71 + b)).take(if (rulesVersion >= 3) 1 else 2) + book.first { it.boss }
        }

        /** Icons by how rare the letter is: hits on the right edge, blocks on the left, now and then some ink or a mix. */
        fun makeCard(letter: Char, power: Power, r: Random): Card {
            val base = when (letter) {
                in "EAIOU" -> 1
                in "NRSTL" -> 2
                in "DGCMPBHY" -> 2 + r.nextInt(2)
                in "FWVK" -> 3
                else -> 4
            }
            var right = Edge(hits = base)
            var left = Edge(blocks = base)
            when (r.nextInt(6)) {
                0 -> if (base > 1) right = Edge(hits = base - 1, ink = 1) else right = Edge(hits = 1, ink = 1)
                1 -> if (base > 1) left = Edge(blocks = base - 1, ink = 1) else left = Edge(blocks = 1, ink = 1)
                2 -> left = Edge(blocks = base, hits = if (base >= 3) 1 else 0)
                else -> {}
            }
            val amount = when (power) {
                Power.HIT, Power.BLOCK -> 2 + r.nextInt(2)
                Power.LONG -> 3 + r.nextInt(2)
                Power.SHORT -> 2 + r.nextInt(2)
                Power.ALL_IN -> 3
                Power.HEX, Power.INK, Power.STAR -> 1 + r.nextInt(2)
                Power.HEAL -> 2 + r.nextInt(2)
                Power.DRAW, Power.RECALL, Power.VOWELS -> 1
                else -> 0
            }
            return Card(letter, left, right, power, amount)
        }

        fun start(seed: Long, level: LogicLevel, rulesVersion: Int = 3): Wordsworn {
            require(rulesVersion in 2..3)
            return Wordsworn(level, seed, rulesVersion = rulesVersion)
        }

        fun tokenOf(p: Piece) = when (p) {
            is Piece.Hand -> "h${p.index}"
            is Piece.Wild -> "w${p.letter}"
            Piece.Vowel -> "v"
        }

        fun pieceOf(s: String): Piece {
            require(s.isNotEmpty())
            return when (s.first()) {
                'h' -> Piece.Hand(s.substring(1).toInt())
                'w' -> { require(s.length == 2); Piece.Wild(s[1]) }
                'v' -> { require(s == "v"); Piece.Vowel }
                else -> throw IllegalArgumentException(s)
            }
        }

        /** Replays one logged move. */
        fun apply(g: Wordsworn, token: String): Wordsworn? = when (token.firstOrNull()) {
            'p' -> if (token.length > 2 && token[1] in "LR") g.play(token.substring(2).split('.').map(::pieceOf), if (token[1] == 'L') Splay.LEFT else Splay.RIGHT) else null
            'x' -> if (token == "x") g.pass() else null
            'i' -> g.useItem(token.substring(1).toInt())
            'c' -> g.choose(token.substring(1).toInt())
            'b' -> g.buy(token.substring(1).toInt())
            'r' -> g.refreshShop(token.substring(1).toInt())
            't' -> g.toggleTwist(token.substring(1).toInt())
            'h' -> if (token == "h" && (!g.currentRules || g.fighting)) g.hinted() else null
            else -> null
        }
    }
}

/** Marks the way back into the shop after a purchase that needed a choice. */
private data object ShopStep : Step

/** A save is the run's level, seed and moves; loading replays them. */
object WordswornCodec {
    fun encode(g: Wordsworn) = listOf(g.rulesVersion.toString(), g.level.name, g.seed.toString(), g.log.joinToString(" ")).joinToString("\n")

    fun decode(text: String): Wordsworn? {
        try {
            val l = text.split('\n'); require(l.size == 4 && l[0] in listOf("2", "3"))
            var g = Wordsworn.start(l[2].toLong(), LogicLevel.valueOf(l[1]), l[0].toInt())
            if (l[3].isNotEmpty()) for (t in l[3].split(' ')) g = Wordsworn.apply(g, t) ?: return null
            return g
        } catch (_: IllegalArgumentException) { return null } catch (_: NoSuchElementException) { return null } catch (_: IndexOutOfBoundsException) { return null }
    }
}
