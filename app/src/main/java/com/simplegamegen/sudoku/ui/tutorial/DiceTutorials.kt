package com.simplegamegen.sudoku.ui.tutorial

import com.simplegamegen.sudoku.ui.GameId

internal object DiceTutorials {
    val shutBox: Tutorial get() {
        fun tiles(down: Set<Int> = emptySet(), mark: Int? = null) = Scene(5f, 2f, (1..9).map { n ->
            val col = (n - 1) % 5
            val row = if (n <= 5) 0 else 1
            Item("t$n", col.toFloat(), row.toFloat(), look = Cell(text = n.toString(), fill = if (n in down) Fill.SHADED else Fill.PAPER),
                tone = if (n == mark) Tone.SELECTED else Tone.PLAIN, describe = "Shut tile $n")
        }, maxUnit = 56)
        return Tutorial(
            GameId.SHUT_BOX,
            "Flip tiles that add up to the dice.",
            rules = listOf(
                "Nine tiles, 1 through 9. Roll two dice, or one die once every tile still up is 6 or less.",
                "Flip a set of tiles that adds up to the roll. Each tile goes down once.",
                "If nothing adds up, you score the tiles still up. Shutting every tile scores 0. The lower score wins.",
            ),
            tips = listOf("7 and 9 both use a high tile. Taking 9 leaves more small tiles for the next roll."),
            steps = listOf(
                Step("The dice are 6 and 6, so you need tiles that add up to 12.", tiles()),
                Step("9 and 3 add up to 12. Tap 9.", tiles(), tap = setOf("t9"), after = tiles(mark = 9),
                    help = "The tile numbered 9."),
                Step("Tap 3.", tiles(mark = 9), tap = setOf("t3"), after = tiles(down = setOf(9, 3)),
                    then = "Both tiles go down. The box rolls again.", help = "The tile numbered 3."),
            ),
        )
    }

    val tenThousand: Tutorial get() {
        fun dice(faces: List<Int>, held: Set<Int> = emptySet()) = Scene(6f, 1f, faces.mapIndexed { i, face ->
            Item("d$i", i.toFloat(), 0f, look = Cell(text = face.toString(), fill = if (i in held) Fill.SHADED else Fill.PAPER),
                describe = "Die showing $face")
        }, maxUnit = 52)
        val roll = listOf(1, 1, 1, 2, 3, 4)
        return Tutorial(
            GameId.TEN_THOUSAND,
            "Keep scoring dice, then bank or push.",
            rules = listOf(
                "Six dice. A 1 scores 100, a 5 scores 50, and three of a kind scores much more.",
                "Keep a scoring set, then bank the turn or roll what is left. The first bank has to be at least 500.",
                "A roll that scores nothing loses the turn. Reach 10,000 and the other player gets one more turn.",
            ),
            tips = listOf("Three 1s score 1000, not 300. A die is never counted twice."),
            steps = listOf(
                Step("Three 1s are on the table, with 2, 3 and 4.", dice(roll)),
                Step("Tap a 1. Three of them score 1000.", dice(roll), tap = setOf("d0"), after = dice(roll, setOf(0, 1, 2)),
                    then = "1000 this turn. Bank it once the turn reaches 500, or roll the other three.", help = "One of the dice showing 1."),
                Step("The 2, 3 and 4 do not score. Leave them out of the set.", dice(roll, setOf(0, 1, 2)).choices("Score"), pick = "Score",
                    after = dice(listOf(2, 3, 4))),
            ),
        )
    }

    val shipCrew: Tutorial get() {
        fun dice(faces: List<Int>, held: Set<Int> = emptySet()) = Scene(5f, 1f, faces.mapIndexed { i, face ->
            Item("d$i", i.toFloat(), 0f, look = Cell(text = face.toString(), fill = if (i in held) Fill.SHADED else Fill.PAPER),
                describe = "Die showing $face")
        }, maxUnit = 56)
        val roll = listOf(6, 5, 2, 2, 1)
        return Tutorial(
            GameId.SHIP_CREW,
            "Get a 6, then a 5, then a 4. The other two dice are cargo.",
            rules = listOf(
                "Five dice, up to three rolls. A ship is a 6, a captain is a 5, and a crew is a 4.",
                "The captain counts only after the ship, and the crew only after the captain.",
                "The other two dice are cargo once all three are aboard. Five hands. The higher cargo wins.",
            ),
            tips = listOf("A 5 without a 6 is not a captain yet. Roll it again if you still need the ship."),
            steps = listOf(
                Step("You have a ship and a captain. The crew, a 4, is still missing.", dice(roll)),
                Step("Tap the 6 to hold the ship.", dice(roll), tap = setOf("d0"), after = dice(roll, setOf(0)),
                    help = "The die showing 6."),
                Step("Hold the 5 as well, then roll the other three.", dice(roll, setOf(0)), tap = setOf("d1"),
                    after = dice(roll, setOf(0, 1)), then = "A 4 on the next roll puts the crew aboard. The other two dice become cargo."),
            ),
        )
    }
}
