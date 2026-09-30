package com.simplegamegen.sudoku.ui.tutorial

import com.simplegamegen.sudoku.ui.GameId

/** Engine card number for [rank] (1–13) of [suit] (0 ♠, 1 ♥, 2 ♦, 3 ♣). */
internal fun card(rank: Int, suit: Int) = suit * 13 + rank - 1

private const val CW = 1f
private const val CH = 1.4f

private fun cardItem(id: String, x: Float, y: Float, rank: Int, suit: Int, faceUp: Boolean = true, tone: Tone = Tone.PLAIN) =
    Item(id, x, y, CW, CH, Card(card(rank, suit), faceUp), tone)

internal object TableTutorials {
    val mahjong: Tutorial get() {
        val red = 31; val north = 30
        fun scene(bFree: Boolean, gone: Set<String> = emptySet(), selected: String? = null): Scene {
            val items = listOf(
                Item("b", 0.2f, 1f, 1f, 1.32f, Tile(red, free = bFree)),
                Item("c", 1.2f, 1f, 1f, 1.32f, Tile(red, free = bFree)),
                Item("d", 2.2f, 1f, 1f, 1.32f, Tile(north)),
                Item("e", 0.7f, 0.55f, 1f, 1.32f, Tile(north)),
            ).filter { it.id !in gone }.map { if (it.id == selected) it.copy(tone = Tone.SELECTED) else it }
            return Scene(3.5f, 2.6f, items, Backdrop.TABLE, maxUnit = 72)
        }
        return Tutorial(
            GameId.MAHJONG,
            "Clear the board by matching pairs of free tiles.",
            rules = listOf(
                "Remove the tiles in matching pairs. Two tiles match when their pictures are identical.",
                "You can only take free tiles: nothing may lie on top, and the left or right side must be open.",
                "Blocked tiles are drawn darker.",
                "Clear every tile to win. If no free pair is left, the game is stuck; use Undo or start again.",
            ),
            tips = listOf(
                "Free tiles that block many others (tall stacks, long rows) are worth clearing early.",
                "When three copies of a tile are free, think about which pair leaves the fourth reachable.",
                "Hint shows a pair from a route that clears the board.",
            ),
            steps = listOf(
                Step("The North wind on top covers both red dragons, so they're blocked (darker). The two North winds are free.", scene(false)),
                Step("Tap the North wind on top.", scene(false), tap = setOf("e"), after = scene(false, selected = "e")),
                Step("Tap the other North wind to match them.", scene(false, selected = "e"), tap = setOf("d"), after = scene(true, setOf("d", "e")),
                    then = "The pair is gone and the red dragons are free: nothing on top and an open side."),
                Step("Tap the left red dragon.", scene(true, setOf("d", "e")), tap = setOf("b"), after = scene(true, setOf("d", "e"), "b")),
                Step("And its partner.", scene(true, setOf("d", "e"), "b"), tap = setOf("c"), after = scene(true, setOf("b", "c", "d", "e")),
                    then = "Board cleared! Real layouts have many layers, so order matters: think before you match."),
            ),
        )
    }

    val solitaire: Tutorial get() {
        val base = Scene(3.6f, 4.2f, listOf(
            Item("f", 0f, 0f, CW, CH, Slot("A")),
            Item("backA", 0f, 1.9f, CW, CH, Card(0, faceUp = false)),
            cardItem("s8", 0f, 2.3f, 8, 0),
            Item("backB", 1.3f, 1.9f, CW, CH, Card(0, faceUp = false)),
            cardItem("h7", 1.3f, 2.3f, 7, 1),
            cardItem("aS", 2.6f, 1.9f, 1, 0),
        ), Backdrop.TABLE, maxUnit = 64)
        val s1 = base.tone(Tone.SELECTED, "aS")
        val s2 = base.move("aS", 0f, 0f)
        val s3 = s2.tone(Tone.SELECTED, "h7")
        val s4 = s2.move("h7", 0f, 2.75f).remove("backB").plus(cardItem("s2", 1.3f, 1.9f, 2, 0))
        val s5 = s4.tone(Tone.SELECTED, "s2")
        val s6 = s4.move("s2", 0f, 0.15f)
        return Tutorial(
            GameId.SOLITAIRE,
            "Klondike: build up by suit, down in alternating colours.",
            rules = listOf(
                "Goal: move all 52 cards to the four foundations, each built up by suit from Ace to King.",
                "In the seven columns, build down in alternating colours: a red 7 on a black 8, a black 6 on that.",
                "You can move a face-up card together with the cards on top of it. When a face-down card is uncovered, it turns face up.",
                "Only a King (with its run) may fill an empty column.",
                "Tap the stock to draw 1 or 3 cards (your choice when starting); the waste can be redealt without limit.",
            ),
            tips = listOf(
                "Uncovering face-down cards is usually more valuable than drawing from the stock.",
                "Don't rush low cards to the foundations if you still need them to build on.",
                "Tap a card, then its destination. Hint suggests a legal move; Undo takes one back.",
            ),
            steps = listOf(
                Step("Build the foundation at the top from Ace to King in one suit. Below, columns build down in alternating colours.", base),
                Step("Aces go straight to a foundation. Tap the A♠.", base, tap = setOf("aS"), after = s1),
                Step("Now tap the empty foundation.", s1, tap = setOf("f"), after = s2),
                Step("The 7♥ is red and one lower than the black 8♠, so it may go on it. Tap the 7♥.", s2, tap = setOf("h7"), after = s3),
                Step("Tap the 8♠ to move it there.", s3, tap = setOf("s8"), after = s4,
                    then = "The card underneath turned face up: it's the 2♠."),
                Step("The 2♠ goes on the A♠. Tap it.", s4, tap = setOf("s2"), after = s5),
                Step("Tap the foundation.", s5, tap = setOf("aS", "f"), after = s6,
                    then = "Nicely done. In the game, tap the stock when you run out of moves."),
            ),
        )
    }

    val spider: Tutorial get() {
        val base = Scene(4.6f, 2.8f, listOf(
            cardItem("s6", 0f, 0f, 6, 0),
            cardItem("s5", 1.2f, 0f, 5, 0), cardItem("s4", 1.2f, 0.45f, 4, 0),
            cardItem("h5", 2.4f, 0f, 5, 1),
            cardItem("c6", 3.6f, 0f, 6, 3),
        ), Backdrop.TABLE, maxUnit = 64)
        val s1 = base.tone(Tone.SELECTED, "s5").tone(Tone.SELECTED, "s4")
        val s2 = base.move("s5", 0f, 0.45f).move("s4", 0f, 0.9f).plus(Item("empty", 1.2f, 0f, CW, CH, Slot()))
        val s3 = s2.tone(Tone.SELECTED, "h5")
        val s4 = s2.move("h5", 3.6f, 0.45f).remove("h5").plus(Item("empty2", 2.4f, 0f, CW, CH, Slot()), cardItem("h5", 3.6f, 0.45f, 5, 1))
        return Tutorial(
            GameId.SPIDER,
            "Two decks; build King-to-Ace runs in one suit.",
            rules = listOf(
                "Two decks are dealt into ten columns. Build runs down from King to Ace.",
                "Any card may be placed on a card one rank higher, whatever its suit.",
                "Only runs of the same suit can be moved together.",
                "A complete King-to-Ace run in one suit leaves the table. Clear all eight runs to win.",
                "The stock deals one card onto every column (no column may be empty). Play with 1, 2 or 4 suits.",
            ),
            tips = listOf("Prefer same-suit moves; mixed runs get stuck.", "An empty column is precious: use it to rearrange runs.",
                "Tap a card to pick it up, then where it goes, or tap it twice to send it to the best spot."),
            steps = listOf(
                Step("Cards go on a card one rank higher. Only same-suit runs move together.", base),
                Step("5♠ 4♠ is a same-suit run, so it moves as one. Tap the 5♠.", base, tap = setOf("s5"), after = s1),
                Step("Put the run on the 6♠.", s1, tap = setOf("s6"), after = s2, then = "Its column is now empty: any card or run can go there."),
                Step("Suits can be mixed too: tap the 5♥.", s2, tap = setOf("h5"), after = s3),
                Step("Place it on the 6♣.", s3, tap = setOf("c6"), after = s4,
                    then = "Allowed, but 6♣ 5♥ is mixed, so the two can't move together later. Same-suit runs from King to Ace leave the table."),
            ),
        )
    }

    val pyramid: Tutorial get() {
        val all = listOf(
            cardItem("s10", 1.1f, 0f, 10, 0),
            cardItem("h3", 0.55f, 0.7f, 3, 1), cardItem("cK", 1.65f, 0.7f, 13, 3),
            cardItem("d5", 0f, 1.4f, 5, 2), cardItem("c8", 1.1f, 1.4f, 8, 3), cardItem("h6", 2.2f, 1.4f, 6, 1),
            Item("stock", 3.7f, 0f, CW, CH, Card(0, faceUp = false)),
            cardItem("c7", 3.7f, 1.4f, 7, 3),
        )
        fun scene(gone: Set<String>, selected: String? = null) =
            Scene(4.7f, 2.9f, all.filter { it.id !in gone }.map { if (it.id == selected) it.copy(tone = Tone.SELECTED) else it }, Backdrop.TABLE, maxUnit = 60)
        return Tutorial(
            GameId.PYRAMID,
            "Remove pairs of uncovered cards that add up to 13.",
            rules = listOf(
                "Remove pairs of uncovered cards whose values add up to 13. Ace is 1, Jack 11, Queen 12.",
                "Kings are worth 13 and are removed on their own.",
                "A card is uncovered when no card from the row below overlaps it.",
                "Turn stock cards onto the waste; the top waste card can pair with a pyramid card or another waste card.",
                "Clear the whole pyramid to win. You choose how many passes through the stock you get.",
            ),
            tips = listOf("Plan ahead: remove cards that free the most others.", "Don't waste a stock card that the pyramid will need later."),
            steps = listOf(
                Step("Pair uncovered cards that add up to 13. Only the bottom row and the waste card are uncovered now.", scene(emptySet())),
                Step("5 + 8 = 13. Tap the 5♦.", scene(emptySet()), tap = setOf("d5"), after = scene(emptySet(), "d5")),
                Step("Tap the 8♣ to remove the pair.", scene(emptySet(), "d5"), tap = setOf("c8"), after = scene(setOf("d5", "c8")),
                    then = "The 3♥ is uncovered now."),
                Step("The 6♥ pairs with the 7♣ on the waste. Tap the 6♥.", scene(setOf("d5", "c8")), tap = setOf("h6"),
                    after = scene(setOf("d5", "c8"), "h6")),
                Step("Tap the 7♣.", scene(setOf("d5", "c8"), "h6"), tap = setOf("c7"), after = scene(setOf("d5", "c8", "h6", "c7"))),
                Step("Kings are 13 on their own. Tap the K♣.", scene(setOf("d5", "c8", "h6", "c7")), tap = setOf("cK"),
                    after = scene(setOf("d5", "c8", "h6", "c7", "cK"))),
                Step("3 + 10 = 13. Tap the 3♥.", scene(setOf("d5", "c8", "h6", "c7", "cK")), tap = setOf("h3"),
                    after = scene(setOf("d5", "c8", "h6", "c7", "cK"), "h3")),
                Step("And the 10♠ to clear the pyramid.", scene(setOf("d5", "c8", "h6", "c7", "cK"), "h3"), tap = setOf("s10"),
                    after = scene(setOf("d5", "c8", "h6", "c7", "cK", "h3", "s10")), then = "Pyramid cleared!"),
            ),
        )
    }

    val dominoes: Tutorial get() {
        val label = Item("", 0f, 1.5f, 6.6f, 0.5f, Label("Your hand", size = 0.3f))
        fun scene(placed: Boolean, selected: Boolean = false, choices: List<String> = listOf("Left", "Right")): Scene {
            val chain = listOf(Item("c64", 1.3f, 0.2f, 2f, 1f, Domino(6, 4))) + if (placed) listOf(Item("c42", 3.3f, 0.2f, 2f, 1f, Domino(4, 2))) else emptyList()
            val hand = listOfNotNull(
                if (!placed) Item("d42", 0f, 2.2f, 2f, 1f, Domino(4, 2), if (selected) Tone.SELECTED else Tone.PLAIN) else null,
                Item("d55", 2.3f, 2.2f, 2f, 1f, Domino(5, 5)),
                Item("d13", 4.6f, 2.2f, 2f, 1f, Domino(1, 3)),
            )
            return Scene(6.6f, 3.4f, chain + label + hand, Backdrop.TABLE, choices = choices, maxUnit = 52)
        }
        return Tutorial(
            GameId.DOMINOES,
            "Match tiles to the ends of the chain; empty your hand first.",
            rules = listOf(
                "Double-six Draw dominoes against the computer; each player starts with seven tiles and you lead.",
                "On your turn, play a tile whose number matches either open end of the chain.",
                "If nothing fits, draw from the boneyard until a tile fits or it's empty; then you pass.",
                "Empty your hand to win. If both players are stuck, the lower total of pips in hand wins (equal is a draw).",
                "Select a tile, then Left or Right to choose the end.",
            ),
            tips = listOf("Get rid of heavy tiles (lots of pips) early in case the game blocks.", "Keep a variety of numbers so you can answer either end."),
            steps = listOf(
                Step("The chain's open ends are 6 on the left and 4 on the right. Your tiles are below.", scene(false, choices = emptyList())),
                Step("Your 4|2 matches the 4 on the right. Tap it.", scene(false), tap = setOf("d42"), after = scene(false, true)),
                Step("Place it on the matching end.", scene(false, true), pick = "Right", after = scene(true, choices = emptyList()),
                    then = "The chain now ends in 6 and 2.", help = "The 4 is on the right end of the chain."),
                Step("Suppose it's your turn again: ends 6 and 2. Neither 5|5 nor 1|3 fits. What happens?",
                    scene(true, choices = listOf("Draw from the boneyard", "Play 5|5 anyway")), pick = "Draw from the boneyard",
                    then = "Right: you draw until a tile fits (or pass when the boneyard is empty). The game does this for you."),
            ),
        )
    }

    val memory: Tutorial get() {
        val faces = listOf(31, 0, 27, 27, 31, 0)
        fun scene(up: Set<Int>, matched: Set<Int> = emptySet()): Scene {
            val items = faces.mapIndexed { i, face ->
                Item("m$i", (i % 3) * 1.25f, (i / 3) * 1.55f, 1f, 1.32f, Tile(face, faceUp = i in up || i in matched), if (i in matched) Tone.GOOD else Tone.PLAIN)
            }
            return Scene(3.5f, 2.9f, items, Backdrop.TABLE, maxUnit = 72)
        }
        return Tutorial(
            GameId.MEMORY,
            "Turn over two tiles at a time and find every pair.",
            rules = listOf(
                "All tiles start face down. Turn over two per turn.",
                "If they show the same picture, they stay face up. If not, both turn back over after a moment.",
                "Find every pair in as few turns as you can. Boards range from 6 to 15 pairs.",
            ),
            tips = listOf("Turn over a tile you haven't seen before rather than one you know.", "Say the pictures to yourself; it helps you remember them."),
            steps = listOf(
                Step("Six tiles hide three pairs of Mahjong pictures.", scene(emptySet())),
                Step("Turn over the top-left tile.", scene(emptySet()), tap = setOf("m0"), after = scene(setOf(0)), then = "A red dragon."),
                Step("Turn over another one: try the top-middle tile.", scene(setOf(0)), tap = setOf("m1"), after = scene(setOf(0, 1)),
                    then = "A one of dots: not a match, so both turn back. Remember where they were!"),
                Step("Turn over the bottom-middle tile.", scene(emptySet()), tap = setOf("m4"), after = scene(setOf(4)),
                    then = "Another red dragon, and you know where the first one is."),
                Step("Tap the tile that matches it.", scene(setOf(4)), tap = setOf("m0"), after = scene(emptySet(), setOf(0, 4)),
                    then = "A pair! It stays face up. Keep going until every pair is found."),
            ),
        )
    }

    val freecell: Tutorial get() {
        val dealt = Scene(
            4.2f, 1.6f,
            listOf(
                cardItem("ace", 0f, 0f, 1, 0),
                cardItem("six", 1.4f, 0f, 6, 0),
                cardItem("five", 2.8f, 0f, 5, 1),
            ),
            Backdrop.TABLE, maxUnit = 72,
        )
        return Tutorial(
            GameId.FREECELL,
            "Four free cells; build Ace to King by suit.",
            rules = listOf(
                "All 52 cards are dealt face up into eight columns (four of seven, four of six).",
                "Four free cells hold one card each. Four foundations build Ace to King in one suit.",
                "On the tableau, place a card on the opposite color and one rank higher. Empty columns take any card or built pile.",
                "A built descending alternating pile may move together when you have enough free cells and empty columns.",
                "Clear all four foundations to win. Not every deal can be won.",
            ),
            tips = listOf(
                "Park a blocker in a free cell, then free a column.",
                "Build a foundation when the card is free and nothing lower is buried.",
            ),
            steps = listOf(
                Step("Every card is face up. Tap the ace.", dealt, tap = setOf("ace"),
                    after = dealt.tone(Tone.SELECTED, "ace"), help = "The ace of spades."),
                Step("An ace starts its foundation.", dealt.tone(Tone.GOOD, "ace"), then = "That suit can now build up to the king."),
                Step("A red five can sit on a black six, one rank higher.", dealt.tone(Tone.SELECTED, "five")),
            ),
        )
    }
}
