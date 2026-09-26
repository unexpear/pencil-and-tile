package com.simplegamegen.sudoku.ui.tutorial

import com.simplegamegen.sudoku.ui.GameId

/** A letter grid: '#' is a block, '.' an empty square, letters are shown. */
internal fun letterScene(rows: List<String>, corners: Map<String, String> = emptyMap(), extra: List<Item> = emptyList(),
    height: Float = rows.size.toFloat(), choices: List<String> = emptyList(), maxUnit: Int = 60): Scene {
    val items = gridItems(rows.size, rows[0].length) { r, c ->
        val ch = rows[r][c]
        if (ch == '#') Cell(fill = Fill.BLOCK) else Cell(text = if (ch == '.') "" else ch.toString(), corner = corners[cellId(r, c)] ?: "")
    }
    return Scene(rows[0].length.toFloat(), height, items + extra, choices = choices, maxUnit = maxUnit)
}

private fun Scene.fill(vararg entries: Pair<String, String>, tone: Tone = Tone.ENTERED): Scene =
    entries.fold(this) { s, (id, t) -> s.text(id, t, tone) }

internal object WordTutorials {
    val crossword: Tutorial get() {
        val corners = mapOf("r0c0" to "1", "r0c2" to "2", "r2c0" to "3")
        val start = letterScene(listOf("...", ".#.", "..."), corners, maxUnit = 80)
            .copy(caption = "Across: 1 The star we orbit · 3 Joins two ideas\nDown: 1 Salty water · 2 Say yes with your head")
        val across = listOf("r0c0", "r0c1", "r0c2")
        val s1 = start.map(across) { it.copy(tone = Tone.SELECTED) }.choices("SUN", "SKY", "SEA")
        val s2 = start.fill("r0c0" to "S", "r0c1" to "U", "r0c2" to "N").choices("NOD", "NET", "NAP")
        val s3 = s2.fill("r1c2" to "O", "r2c2" to "D").choices("SEA", "SKI", "SUN")
        val s4 = s3.fill("r1c0" to "E", "r2c0" to "A").choices("AID", "AND", "ADD")
        val s5 = s4.fill("r2c1" to "N", tone = Tone.GOOD).allTone(Tone.GOOD).choices()
        return Tutorial(
            GameId.CROSSWORD,
            "Fill the grid from Across and Down clues.",
            rules = listOf(
                "Each numbered clue has an answer that fills the squares from its number, across or down.",
                "Answers that cross share letters, so every solved word helps the ones crossing it.",
                "Tap a clue or a square to choose an answer, then type the whole word.",
                "Check marks wrong letters; Reveal shows one letter when you're stuck.",
            ),
            tips = listOf(
                "Start with the clues you're sure of, then use the crossing letters.",
                "The number in brackets after a clue in the game is the answer length.",
            ),
            steps = listOf(
                Step("Numbers mark where answers start. 1 Across and 1 Down both start in the top-left square.", start),
                Step("1 Across: 'The star our planet travels around' (3 letters). Tap its first square.", start, tap = setOf("r0c0"),
                    after = s1, then = "The whole answer is highlighted. Now type it."),
                Step("Which word fits 1 Across?", s1, pick = "SUN", after = s2, then = "The N it gives starts 2 Down.",
                    help = "The star our planet travels around."),
                Step("2 Down starts with that N: 'Move your head to say yes' (3).", s2, pick = "NOD", after = s3),
                Step("1 Down reads S _ _: 'Salty water that covers most of the Earth' (3).", s3, pick = "SEA", after = s4),
                Step("3 Across now reads A _ D: 'A word that joins two ideas' (3). All three choices fit the letters, so let the clue decide.",
                    s4, pick = "AND", after = s5, then = "Solved! Crossing letters narrow the options; the clue settles them."),
            ),
        )
    }

    val wordSearch: Tutorial get() {
        val rows = listOf("SUNQT", "MOONR", "XLAKE", "STARE", "BPIWK")
        val start = letterScene(rows, maxUnit = 60).copy(caption = "Find: SUN · MOON · LAKE · STAR · TREE")
        val s1 = start.tone(Tone.SELECTED, "r0c0")
        val s2 = start.map("r0c0", "r0c1", "r0c2") { it.copy(tone = Tone.FOUND) }.copy(caption = "Find: SUN ✓ · MOON · LAKE · STAR · TREE")
        val s3 = s2.tone(Tone.SELECTED, "r0c4")
        val tree = listOf("r0c4", "r1c4", "r2c4", "r3c4")
        val s4 = s2.map(tree) { it.copy(tone = Tone.FOUND) }.copy(caption = "Find: SUN ✓ · MOON · LAKE · STAR · TREE ✓")
        return Tutorial(
            GameId.WORD_SEARCH,
            "Find the listed words hidden in the letters.",
            rules = listOf(
                "Every word in the list is hidden in the grid in a straight line.",
                "Easy words run right or down; harder levels add diagonals and then all eight directions, including backwards.",
                "Tap a word's first letter, then its last letter. Tapping the other way round works too.",
                "Found words stay highlighted and are ticked off the list. Hint shows where a word starts.",
            ),
            tips = listOf("Scan for an unusual letter in a word (like K or Q) rather than a common one.", "Words can share letters where they cross."),
            steps = listOf(
                Step("Find each listed word. Words run in straight lines through the grid.", start),
                Step("SUN is in the top row. Tap its first letter, S.", start, tap = setOf("r0c0"), after = s1),
                Step("Now tap its last letter, N.", s1, tap = setOf("r0c2"), after = s2, then = "Found! The word is ticked off."),
                Step("TREE runs down the right edge. Tap its first letter.", s2, tap = setOf("r0c4"), after = s3, help = "Top-right corner."),
                Step("Tap its last letter.", s3, tap = setOf("r3c4"), after = s4,
                    then = "Found. Try MOON, LAKE and STAR in the real game, where words can also run diagonally and backwards."),
            ),
        )
    }

    val hangman: Tutorial get() {
        fun scene(shown: String, wrong: String, done: Boolean = false): Scene {
            val slots = "RIVER".mapIndexed { i, ch ->
                Item("s$i", i.toFloat(), 1f, look = Cell(text = if (ch in shown) ch.toString() else ""), tone = if (done) Tone.GOOD else if (ch in shown) Tone.ENTERED else Tone.PLAIN)
            }
            val labels = listOf(
                Item("", 0f, 0f, 5f, 0.7f, Label("Theme: Nature · 5 letters", size = 0.34f)),
                Item("", 0f, 2.3f, 5f, 0.7f, Label(if (wrong.isEmpty()) "Wrong guesses: none" else "Wrong guesses: $wrong (${wrong.length} of 8)", size = 0.32f)),
            )
            return Scene(5f, 3f, labels + slots, choices = listOf("A", "E", "I", "O", "R", "V"), maxUnit = 60)
        }
        return Tutorial(
            GameId.HANGMAN,
            "Guess the hidden word one letter at a time.",
            rules = listOf(
                "Guess letters one at a time. Correct letters appear in every place they occur.",
                "A wrong guess adds a part to the drawing. Easy allows 8 wrong guesses, Medium 7, Hard 6 and Expert 5.",
                "Guess the whole word before you run out to win.",
                "Hint reveals a letter; each game shows its theme.",
            ),
            tips = listOf("Start with common vowels (E, A, O, I) and consonants (R, S, T, N).", "Use the theme and the word length to narrow it down."),
            steps = listOf(
                Step("A hidden word with 5 letters. Each correct guess fills every matching square; wrong guesses are counted.", scene("", "").copy(choices = emptyList())),
                Step("Vowels are a good start. Guess E.", scene("", ""), pick = "E", after = scene("E", ""), then = "E is the fourth letter."),
                Step("Try A.", scene("E", ""), pick = "A", after = scene("E", "A"),
                    then = "No A. That wrong guess adds to the drawing; you can make 8 on Easy."),
                Step("Guess R.", scene("E", "A"), pick = "R", after = scene("ER", "A"), then = "Two Rs: R _ _ E R."),
                Step("R _ _ E R, a Nature word. What flows between two banks? Guess its second letter.", scene("ER", "A"), pick = "I",
                    after = scene("ERI", "A"), help = "It's a RIVER."),
                Step("Finish the word.", scene("ERI", "A"), pick = "V", after = scene("ERIV", "A", done = true), then = "RIVER, solved with one wrong guess."),
            ),
        )
    }

    val cryptogram: Tutorial get() {
        val coded = "KQBB XMRQ"; val plain = "WELL DONE"
        fun scene(known: Set<Char>, selected: Char? = null, done: Boolean = false): Scene {
            var x = 0f
            val items = coded.mapIndexedNotNull { i, ch ->
                if (ch == ' ') { x += 0.6f; null } else {
                    val p = plain[i]
                    Item("p$i", x, 0f, 1f, 1.4f, look = Cell(text = if (p in known) p.toString() else "", sub = ch.toString()),
                        tone = when { done -> Tone.GOOD; ch == selected -> Tone.SELECTED; p in known -> Tone.ENTERED; else -> Tone.PLAIN }).also { x += 1f }
                }
            }
            return Scene(x, 1.4f, items, maxUnit = 56)
        }
        fun ids(ch: Char) = coded.indices.filter { coded[it] == ch }.map { "p$it" }.toSet()
        return Tutorial(
            GameId.CRYPTOGRAM,
            "Crack a letter-swap code to reveal a saying.",
            rules = listOf(
                "A saying has been written in code: every letter is replaced by another, the same way throughout.",
                "No letter stands for itself.",
                "Tap a coded letter, then type the letter you think it stands for. Every copy of it updates.",
                "A plain letter can only be used once; typing it elsewhere moves it. Easier levels give some letters.",
            ),
            tips = listOf("One-letter words are usually A or I.", "Look for common patterns: THE, AND, double letters like LL or EE, and endings like -ING."),
            steps = listOf(
                Step("This saying is in code. The small letter under each square is the coded one; you fill in the real letter above it.", scene(emptySet())),
                Step("The coded letter Q appears three times, and E is the most common letter in English. Tap a Q.", scene(emptySet()),
                    tap = ids('Q'), after = scene(emptySet(), 'Q')),
                Step("Type the letter you think Q stands for.", scene(emptySet(), 'Q').choices("E", "T", "A"), pick = "E",
                    after = scene(setOf('E')), then = "Every Q now shows E."),
                Step("The first word is now _ E B B. A double letter after E is often LL (WELL, BELL, TELL). Tap a B.",
                    scene(setOf('E')), tap = ids('B'), after = scene(setOf('E'), 'B')),
                Step("What does B stand for?", scene(setOf('E'), 'B').choices("L", "S", "T"), pick = "L", after = scene(setOf('E', 'L'))),
                Step("_ E L L  _ _ _ E. Tap K, the first coded letter.", scene(setOf('E', 'L')), tap = ids('K'), after = scene(setOf('E', 'L'), 'K')),
                Step("Which letter makes a common word, and a famous saying, with _ E L L _ _ _ E?", scene(setOf('E', 'L'), 'K').choices("W", "B", "T"), pick = "W",
                    after = scene(setOf('E', 'L', 'W')), then = "WELL. The rest follows: DONE."),
                Step("\"Well done is better than well said.\" Crack the full sayings in the game; Check marks wrong letters and Hint reveals one.",
                    scene(plain.toSet(), done = true)),
            ),
        )
    }

    val scramble: Tutorial get() {
        val jumble = "NIRA"
        fun scene(typed: String): Scene {
            val slots = (0 until 4).map { i -> Item("a$i", i + 0.5f, 0f, look = Cell(text = typed.getOrNull(i)?.toString() ?: ""),
                tone = if (typed.length == 4) Tone.GOOD else if (i < typed.length) Tone.ENTERED else Tone.PLAIN) }
            val used = mutableListOf<Int>()
            typed.forEach { ch -> jumble.indices.first { it !in used && jumble[it] == ch }.let { used += it } }
            val tiles = jumble.mapIndexed { i, ch -> Item("t$i", i + 0.5f, 1.6f, look = Letter(ch.toString(), used = i in used)) }
            return Scene(5f, 2.6f, slots + tiles, maxUnit = 60)
        }
        return Tutorial(
            GameId.SCRAMBLE,
            "Unjumble words by tapping their letters in order.",
            rules = listOf(
                "Each round has 8 jumbled words. Tap the tiles in order to spell the word.",
                "Any real word from the app's word list that uses all the letters counts.",
                "Back removes the last letter; Clear starts again.",
                "Shuffle mixes the tiles, Hint places the next letter, and Skip moves on (skipped words come back at the end).",
            ),
            tips = listOf("Look for common pairs like TH, CH, ING or ER.", "Shuffling often shows the word in a new light."),
            steps = listOf(
                Step("NIRA is a jumbled word for something that falls from clouds. Tap the tiles in order to spell it: start with R.", scene(""),
                    tap = setOf("t2"), after = scene("R")),
                Step("Next, A.", scene("R"), tap = setOf("t3"), after = scene("RA")),
                Step("Then I.", scene("RA"), tap = setOf("t1"), after = scene("RAI")),
                Step("And N to finish.", scene("RAI"), tap = setOf("t0"), after = scene("RAIN"), then = "RAIN, correct! The next word appears in the game."),
            ),
        )
    }

    val acrostic: Tutorial get() {
        val answers = listOf("SNOW", "UP", "NEST")
        fun scene(solved: Int): Scene {
            val items = answers.flatMapIndexed { r, word ->
                word.mapIndexed { c, ch ->
                    Item(cellId(r, c), c.toFloat(), r.toFloat(), look = Cell(text = if (r < solved) ch.toString() else ""),
                        tone = when { r < solved && solved == answers.size -> Tone.GOOD; c == 0 -> Tone.FOUND; r < solved -> Tone.ENTERED; else -> Tone.PLAIN })
                }
            }
            return Scene(4f, 3f, items, maxUnit = 60)
        }
        return Tutorial(
            GameId.ACROSTIC,
            "Answer clues whose first letters spell a hidden word.",
            rules = listOf(
                "Answer every clue; the number of squares shows the answer length.",
                "Read down the first letters of all the answers to spell a hidden word.",
                "Check marks answers with wrong letters; Hint fills in one letter.",
            ),
            tips = listOf("Once you've guessed the hidden word, its letters tell you how each answer starts."),
            steps = listOf(
                Step("Each row is the answer to a clue. The highlighted first column, read down, spells a hidden word.", scene(0)),
                Step("Clue 1: Frozen flakes that fall in winter (4).", scene(0).choices("SNOW", "HAIL", "RAIN"), pick = "SNOW", after = scene(1)),
                Step("Clue 2: The opposite of down (2).", scene(1).choices("IN", "UP", "ON"), pick = "UP", after = scene(2)),
                Step("Clue 3: A bird builds one for its eggs (4).", scene(2).choices("NEST", "TREE", "WING"), pick = "NEST", after = scene(3),
                    then = "Read down the first letters: S, U, N. The hidden word is SUN!"),
            ),
        )
    }

    val codeCracker: Tutorial get() {
        val grid = listOf("SUN", "E#O", "AND")
        val code = mapOf('S' to 1, 'U' to 2, 'N' to 3, 'E' to 4, 'O' to 5, 'A' to 6, 'D' to 7)
        fun scene(known: Set<Char>, selected: Int? = null, done: Boolean = false): Scene {
            val items = gridItems(3, 3) { r, c ->
                val ch = grid[r][c]
                if (ch == '#') Cell(fill = Fill.BLOCK) else Cell(text = if (ch in known) ch.toString() else "", corner = code.getValue(ch).toString())
            }.map { item ->
                val ch = grid[item.id[1].digitToInt()][item.id[3].digitToInt()]
                item.copy(tone = when { done && ch != '#' -> Tone.GOOD; ch != '#' && code[ch] == selected -> Tone.SELECTED; ch in known && ch !in "SN" -> Tone.ENTERED; else -> Tone.PLAIN })
            }
            return Scene(3f, 3f, items, maxUnit = 80)
        }
        val given = setOf('S', 'N')
        return Tutorial(
            GameId.CODE_CRACKER,
            "A clue-free crossword where numbers stand for letters.",
            rules = listOf(
                "Every square holds a number. The same number always stands for the same letter, and each letter has its own number.",
                "Every run of two or more squares, across or down, is a real word.",
                "A few letters are given to start (more on easier levels).",
                "Choose a number (tap a square or the key below the grid), then type its letter. All squares with that number fill in.",
            ),
            tips = listOf("Short words and common endings (-ED, -ING, -S) are great footholds.", "The A–Z keyboard dims letters you've already used."),
            steps = listOf(
                Step("Numbers stand for letters: 1 is S and 3 is N here. Every row and column of letters is a word.", scene(given)),
                Step("The top row reads S ? N. Tap the square numbered 2.", scene(given), tap = setOf("r0c1"), after = scene(given, 2)),
                Step("Which letter makes a word from S ? N?", scene(given, 2).choices("U", "I", "O"), pick = "U", after = scene(given + 'U'),
                    then = "SUN works. (SIN would too, but check it against the other words as you go.)"),
                Step("The bottom row is ? N ?. A N D is a strong guess. Tap the square numbered 6.", scene(given + 'U'), tap = setOf("r2c0"),
                    after = scene(given + 'U', 6)),
                Step("What letter is 6?", scene(given + 'U', 6).choices("A", "E", "O"), pick = "A", after = scene(given + setOf('U', 'A')),
                    then = "Now 7 is D, so the columns read S E A and N O D, giving 4 = E and 5 = O."),
                Step("Solved. In the game, every square with the same number fills in at once.", scene(code.keys, done = true)),
            ),
        )
    }

    val dropquote: Tutorial get() {
        val rows = listOf("WELL", "DONE")
        val pools = (0 until 4).map { c -> rows.map { it[c] }.sorted() }
        fun scene(filled: List<String>, selected: String? = null, done: Boolean = false): Scene {
            val placed = filled.map { id -> rows[id[1].digitToInt()][id[3].digitToInt()] to id[3].digitToInt() }
            val poolItems = pools.flatMapIndexed { c, letters ->
                val usedCount = mutableMapOf<Char, Int>()
                placed.filter { it.second == c }.forEach { usedCount.merge(it.first, 1, Int::plus) }
                val seen = mutableMapOf<Char, Int>()
                letters.mapIndexed { k, ch ->
                    val n = seen.merge(ch, 1, Int::plus)!!
                    Item("p$c$k", c.toFloat(), k * 0.8f, h = 0.8f, look = Letter(ch.toString(), used = n <= (usedCount[ch] ?: 0), raised = false))
                }
            }
            val cells = gridItems(2, 4, 0f, 1.9f) { r, c -> Cell(text = if (cellId(r, c) in filled) rows[r][c].toString() else "") }
                .map { it.copy(tone = when { done -> Tone.GOOD; it.id == selected -> Tone.SELECTED; it.id in filled -> Tone.ENTERED; else -> Tone.PLAIN }) }
            return Scene(4f, 3.9f, poolItems + cells, maxUnit = 64)
        }
        return Tutorial(
            GameId.DROPQUOTE,
            "Drop each column's letters into place to reveal a saying.",
            rules = listOf(
                "A saying is hidden in the grid, read left to right, line by line. Black squares separate words.",
                "The letters above each column belong in that column, in a different order.",
                "Tap a square, then one of its column's letters to drop it in. Used letters fade.",
                "Words never break across lines. Check marks wrong letters; Hint places one.",
            ),
            tips = listOf("Short words (A, I, THE, OF) fill in fast.", "Columns with only one letter left give a free square."),
            steps = listOf(
                Step("The letters above each column fall into that column, in some order, to spell a saying. This one has two short words.", scene(emptyList())),
                Step("The first word is WELL. Tap its first square.", scene(emptyList()), tap = setOf("r0c0"), after = scene(emptyList(), "r0c0")),
                Step("Its column holds D and W. Tap the W to drop it in.", scene(emptyList(), "r0c0"), tap = setOf("p01"),
                    after = scene(listOf("r0c0"), "r0c1"), help = "The W is the lower letter above the first column.",
                    then = "The selection moves on to the next square."),
                Step("The second column has E and O. WELL needs E: tap it.", scene(listOf("r0c0"), "r0c1"), tap = setOf("p10"),
                    after = scene(listOf("r0c0", "r0c1"), "r0c2")),
                Step("Drop L into the third square.", scene(listOf("r0c0", "r0c1"), "r0c2"), tap = setOf("p20"),
                    after = scene(listOf("r0c0", "r0c1", "r0c2"), "r0c3")),
                Step("Finish WELL with the L above the last column.", scene(listOf("r0c0", "r0c1", "r0c2"), "r0c3"), tap = setOf("p31"),
                    after = scene(listOf("r0c0", "r0c1", "r0c2", "r0c3")), then = "The letters left over spell the second word."),
                Step("\"WELL DONE\": the remaining letters fall into place. Real dropquotes are longer sayings with more lines.",
                    scene(gridItems(2, 4) { _, _ -> Cell() }.map { it.id }, done = true)),
            ),
        )
    }

    val threads: Tutorial get() {
        val words = listOf("FIG", "FOOT", "PEAR", "SNOW", "HAND", "PLUM", "EYE", "LIME")
        val fruit = setOf("FIG", "PEAR", "PLUM", "LIME")
        fun scene(picked: Set<String>, found: Boolean = false, done: Boolean = false): Scene {
            val left = if (found) words - fruit else words
            val bands = mutableListOf<Item>()
            if (found) bands += Item("band1", 0f, 0f, 9.2f, 1f, Label("Fruits: FIG · PEAR · PLUM · LIME", size = 0.34f, bold = true), tone = Tone.GOOD)
            if (done) bands += Item("band2", 0f, 1.15f, 9.2f, 1f, Label("___BALL: EYE · FOOT · HAND · SNOW", size = 0.34f, bold = true), tone = Tone.FOUND)
            val top = bands.size * 1.15f
            val tiles = if (done) emptyList() else left.mapIndexed { i, wd ->
                Item(wd, (i % 4) * 2.3f, top + (i / 4) * 1.15f, 2.2f, 1f, Cell(text = wd), tone = if (wd in picked) Tone.SELECTED else Tone.PLAIN)
            }
            return Scene(9.2f, 2.3f, bands + tiles, maxUnit = 40)
        }
        return Tutorial(
            GameId.THREADS,
            "Sort sixteen words into four groups of four.",
            rules = listOf(
                "Sixteen words hide four groups. Each group of four shares one thread.",
                "Tap four words, then Submit. A right group locks in and shows its thread; a wrong guess costs a life.",
                "Threads can be categories (Fruits), words that join another word (___BALL, SUN___), hidden words or anagrams.",
                "Some words seem to fit two groups. Only one way sorts all sixteen, so look for the group that can only be made one way.",
                "Easy gives 5 lives, Medium and Hard 4, Expert 3. Hint shows one group's thread.",
            ),
            tips = listOf("Find the group you're surest of first; fewer words make the rest clearer.",
                "If three of four were right, the game tells you, so swap one word, not all four.",
                "Say the words out loud with BALL, FISH or HOUSE after them."),
            steps = listOf(
                Step("A small board: eight words, two threads. Four of these are fruits. Tap FIG.", scene(emptySet()), tap = setOf("FIG"), after = scene(setOf("FIG"))),
                Step("Now tap PEAR.", scene(setOf("FIG")), tap = setOf("PEAR"), after = scene(setOf("FIG", "PEAR"))),
                Step("And PLUM.", scene(setOf("FIG", "PEAR")), tap = setOf("PLUM"), after = scene(setOf("FIG", "PEAR", "PLUM"))),
                Step("LIME is a fruit too. Tap it.", scene(setOf("FIG", "PEAR", "PLUM")), tap = setOf("LIME"), after = scene(fruit)),
                Step("Four picked. Submit them?", scene(fruit).choices("Submit", "Deselect"), pick = "Submit", after = scene(emptySet(), found = true),
                    then = "A group! It locks in above the board."),
                Step("FOOT, HAND and EYE are parts of the body, but SNOW isn't. What links all four?",
                    scene(emptySet(), found = true).choices("Parts of the body", "They go before BALL", "Weather"), pick = "They go before BALL",
                    after = scene(emptySet(), found = true, done = true),
                    then = "FOOTBALL, HANDBALL, EYEBALL, SNOWBALL. In the real game, four threads and a few decoys make you choose carefully.",
                    help = "Try each word with BALL after it."),
            ),
        )
    }

    val fiveLetters: Tutorial get() {
        val answer = "PLANT"
        fun row(r: Int, guess: String?): List<Item> = (0 until 5).map { c ->
            val ch = guess?.get(c)
            val tone = when {
                guess == null -> Tone.PLAIN
                answer[c] == ch -> Tone.GOOD
                ch != null && ch in answer -> Tone.FOUND
                else -> Tone.DIM
            }
            Item(cellId(r, c), c.toFloat(), r.toFloat(), look = Cell(text = ch?.toString() ?: ""), tone = tone,
                describe = ch?.let { "$it, " + when (tone) { Tone.GOOD -> "right spot"; Tone.FOUND -> "in the word, wrong spot"; else -> "not in the word" } } ?: "")
        }
        fun scene(vararg guesses: String): Scene = Scene(5f, 3f, (0 until 3).flatMap { r -> row(r, guesses.getOrNull(r)) }, maxUnit = 56)
        return Tutorial(
            GameId.FIVE_LETTERS,
            "Find the hidden five-letter word in a few guesses.",
            rules = listOf(
                "Type a five-letter word and press Enter. Each letter is then marked.",
                "A filled square with a bar means the letter is in the right spot. A square with a dot means the word has that letter somewhere else. A faded square means the letter isn't in the word.",
                "A letter used twice in your guess is only marked as often as the word really has it.",
                "Easy gives 7 guesses and takes any five letters; Medium and up only take words from the list. Hard and Expert make you reuse letters you've found, and Expert gives 5 guesses.",
                "Hint shows one letter in its place.",
            ),
            tips = listOf("Start with a word full of common letters, like STAIN or CRANE.",
                "Use the keyboard colors to see which letters are still possible."),
            steps = listOf(
                Step("The hidden word here is a secret. Our first guess is STAIN. A is in the right spot, T and N are in the word but elsewhere, and S and I aren't in it.",
                    scene("STAIN")),
                Step("Next guess: CHANT. Which letters are now in the right spot?", scene("STAIN", "CHANT").choices("A, N and T", "C and H", "Only A"), pick = "A, N and T",
                    then = "So the word ends in ANT, and C, H, S and I are out.", help = "Filled squares with a bar are in the right spot."),
                Step("Which word fits _ _ A N T without C, H, S or I?", scene("STAIN", "CHANT").choices("SLANT", "PLANT", "CHANT"), pick = "PLANT",
                    after = scene("STAIN", "CHANT", "PLANT"), then = "PLANT, found in three guesses!",
                    help = "SLANT has an S and CHANT was already tried."),
            ),
        )
    }

    val wordsworn: Tutorial get() {
        val hand = "CATSRENO"
        val powers = hand.map { com.simplegamegen.sudoku.wordplay.Wordsworn.powerOf(it) }
        fun scene(picked: List<Int> = emptyList(), caption: String = ""): Scene = Scene(8f, 1.3f, hand.mapIndexed { i, ch ->
            Item("t$i", i.toFloat(), 0f, h = 1.3f, look = Cell(text = ch.toString(), sub = powers[i].toString()),
                tone = if (i in picked) Tone.SELECTED else Tone.PLAIN, describe = "$ch, power ${powers[i]}")
        }, maxUnit = 44, caption = caption)
        return Tutorial(
            GameId.WORDSWORN,
            "Battle a run of monsters by spelling words from your letter tiles.",
            rules = listOf(
                "Tap tiles from your hand to spell a word, then tap Attack.",
                "Damage is the tiles' power added up. Words longer than three letters get 25% more for each extra letter.",
                "Common letters have power 1; rarer letters hit harder, up to 10 for Q and Z.",
                "After your word, the monster does what its sign shows: hit you, guard itself (its guard soaks up damage), or drain your health.",
                "Shield tiles block, heal tiles mend you, a double tile doubles the damage, and a wild tile can be any letter.",
                "Swap trades up to three tiles for new ones, but the monster still takes its turn.",
                "After each win, choose a reward: sharpen a letter, add a special tile, or rest. Beat the Word Eater to finish the run.",
                "Hint shows the strongest word your hand can make.",
            ),
            tips = listOf("A long word of plain letters often beats a short word with one rare letter.",
                "When the monster is about to guard, strike hard now; when it's about to hit, a shield tile helps."),
            steps = listOf(
                Step("This is your hand. The small number on each tile is its power. Spell CAT: tap C.", scene(), tap = setOf("t0"), after = scene(listOf(0))),
                Step("Now A.", scene(listOf(0)), tap = setOf("t1"), after = scene(listOf(0, 1))),
                Step("And T.", scene(listOf(0, 1)), tap = setOf("t2"), after = scene(listOf(0, 1, 2), "CAT deals 5"),
                    then = "CAT deals 3 + 1 + 1 = 5 damage."),
                Step("Longer words hit harder. How much would CARTONS deal? Its tiles add up to 9, and seven letters give +100%.",
                    scene(caption = "C3 A1 R1 T1 O1 N1 S1").choices("9", "18", "27"), pick = "18",
                    then = "18 damage: nine, doubled for four extra letters.", help = "Each letter beyond three adds 25%: four extra letters make +100%."),
            ),
        )
    }

    val loneLetter: Tutorial get() {
        // The letter on top, then three categories with their answers.
        val labels = listOf("Fruit", "Animals", "Colours")
        fun scene(answers: List<String>, tone: Tone = Tone.ENTERED): Scene = Scene(6f, 4f, listOf(
            Item("letter", 2.5f, 0f, look = Cell(text = "B"), tone = Tone.SELECTED, describe = "Letter B"),
        ) + labels.indices.flatMap { k ->
            listOf(
                Item("label$k", 0f, k + 1f, w = 3f, look = Cell(text = labels[k]), describe = labels[k]),
                Item(cellId(k, 1), 3f, k + 1f, w = 3f, look = Cell(text = answers[k]), tone = if (answers[k].isEmpty()) Tone.PLAIN else tone,
                    describe = answers[k].ifEmpty { "empty" }),
            )
        }, maxUnit = 56)
        return Tutorial(
            GameId.LONE_LETTER,
            "One letter, many categories: write an answer for each that starts with the letter.",
            rules = listOf(
                "Each round rolls one letter and deals a list of categories.",
                "Write an answer for each category that starts with the letter. A leading \"the\", \"a\" or \"an\" doesn't count.",
                "Tap Done when you're ready; on the harder levels a clock ends the round.",
                "An answer scores 1 point only if no other player wrote the same thing.",
                "Every further word that starts with the letter, like \"Big Blue Bus\", scores a bonus point.",
                "If an answer of real words isn't on our list, you can count it yourself. After three rounds, the highest total wins.",
            ),
            tips = listOf("The first answer that comes to mind comes to everyone's mind. Go for the second one.",
                "Answers of two or three words that all start with the letter score extra."),
            steps = listOf(
                Step("This round's letter is B. Every answer has to start with B.", scene(listOf("", "", ""))),
                Step("Which answer fits Fruit?", scene(listOf("", "", "")).choices("Banana", "Apple"), pick = "Banana",
                    after = scene(listOf("Banana", "", "")), then = "Banana starts with B and it's a fruit: 1 point, if nobody else writes it.",
                    help = "It has to start with B."),
                Step("Pip, a computer player, nearly always writes bear for Animals. Which answer is more likely to score?",
                    scene(listOf("Banana", "", "")).choices("Bear", "Badger"), pick = "Badger", after = scene(listOf("Banana", "Badger", "")),
                    then = "Badger! If you and Pip both wrote bear, neither of you would score.", help = "Answers that match someone else's score nothing."),
                Step("Which answer scores the most for Colours?", scene(listOf("Banana", "Badger", "")).choices("Blue", "Baby blue"), pick = "Baby blue",
                    after = scene(listOf("Banana", "Badger", "Baby blue"), Tone.GOOD),
                    then = "Baby blue: 1 point, plus 1 for the extra word that starts with B.", help = "Count the words that start with B."),
            ),
        )
    }

    val quilt: Tutorial get() {
        // Two patches: the left column holds A and T, the right column T and O.
        fun scene(cells: String, caption: String): Scene = Scene(2f, 2f, (0 until 4).map { i ->
            Item(cellId(i / 2, i % 2), (i % 2).toFloat(), (i / 2).toFloat(), look = Cell(text = cells[i].toString().trim(), walls = if (i % 2 == 0) 2 else 8),
                tone = if (cells[i] != ' ') Tone.ENTERED else Tone.PLAIN, describe = cells[i].toString().trim().ifEmpty { "empty" })
        }, maxUnit = 72, caption = caption)
        val patches = "Left patch: A, T · Right patch: T, O"
        return Tutorial(
            GameId.WORD_QUILT,
            "Place each patch's letters so every run across and down is a word.",
            rules = listOf(
                "The grid is sewn from patches. Each patch holds a set of letters, shown in its corner.",
                "Place every patch's letters in its own squares.",
                "Every run of two or more letters, across and down, must be a word.",
                "Tap a square, then one of its patch's letters. Tap a filled square to take its letter back.",
                "A full run that isn't a word is underlined in red. Any arrangement that makes every run a word wins.",
            ),
            tips = listOf("Start with patches whose letters can only go one way, like a Q that needs a U after it.",
                "Short runs of two letters have few choices, so they're a good place to begin."),
            steps = listOf(
                Step("This little quilt has two patches: the left column holds A and T, and the right column holds T and O. Every row and column must end up a word.",
                    scene("    ", patches)),
                Step("Each patch's letters must go in its own squares. Which letter belongs top left, so the top row and left column can both be words?",
                    scene("    ", patches).choices("A", "T"), pick = "A", after = scene("A   ", patches),
                    then = "A: the left patch's other letter, T, goes below it, making AT down.", help = "Try each: T on top gives TA down, which is not a common word here."),
                Step("The left column reads AT. Now the right patch: which letter goes top right?", scene("A T ", patches).choices("T", "O"), pick = "T",
                    after = scene("ATTO", patches), then = "AT across, TO across, AT down and TO down: every run is a word!"),
            ),
        )
    }

    val sprawl: Tutorial get() {
        val grid = listOf("CATS", "ROEN", "IDGL", "PUMB")
        fun scene(picked: Set<String> = emptySet(), found: Set<String> = emptySet()): Scene = Scene(4f, 4f, (0 until 4).flatMap { r ->
            (0 until 4).map { c ->
                val id = cellId(r, c)
                Item(id, c.toFloat(), r.toFloat(), look = Cell(text = grid[r][c].toString()),
                    tone = when (id) { in picked -> Tone.SELECTED; in found -> Tone.GOOD; else -> Tone.PLAIN }, describe = grid[r][c].toString())
            }
        }, maxUnit = 60)
        val cat = setOf("r0c0", "r0c1", "r0c2")
        return Tutorial(
            GameId.LETTER_SPRAWL,
            "Chain touching letters into words. Longer words score more.",
            rules = listOf(
                "Make words from letters that touch: sideways, up, down or diagonally.",
                "Each square can be used only once in a word.",
                "Drag across the letters, or tap them one at a time and press Enter.",
                "The QU square counts as two letters.",
                "Words need at least 3 letters on Easy and Medium, and 4 on Hard and Expert.",
                "Points: 1 for three letters, 2 for four, 3 for five, 5 for six, 8 for seven and 13 for eight or more.",
                "Reach the goal to win. Then keep going, or tap Finish to see the everyday words you missed.",
            ),
            tips = listOf("Look for word endings like -ING, -ED and -S next to words you've already found.",
                "Long words score far more than short ones, so it pays to hunt for them."),
            steps = listOf(
                Step("Words are made of letters that touch. Tap C, the first letter of CAT.", scene(), tap = setOf("r0c0"), after = scene(picked = setOf("r0c0"))),
                Step("Now A, right next to it.", scene(picked = setOf("r0c0")), tap = setOf("r0c1"), after = scene(picked = setOf("r0c0", "r0c1"))),
                Step("And T.", scene(picked = setOf("r0c0", "r0c1")), tap = setOf("r0c2"), after = scene(found = cat), then = "CAT: three letters, 1 point."),
                Step("Letters touch diagonally too. Let's find DOG. Tap D.", scene(found = cat), tap = setOf("r2c1"), after = scene(picked = setOf("r2c1"), found = cat)),
                Step("Now O, just above it.", scene(picked = setOf("r2c1"), found = cat), tap = setOf("r1c1"), after = scene(picked = setOf("r2c1", "r1c1"), found = cat)),
                Step("And G, down and to the right of O.", scene(picked = setOf("r2c1", "r1c1"), found = cat), tap = setOf("r2c2"),
                    after = scene(found = cat + setOf("r2c1", "r1c1", "r2c2")), then = "DOG: up, then diagonally down."),
                Step("How many points is a five-letter word worth?", scene(found = cat + setOf("r2c1", "r1c1", "r2c2")).choices("1", "3", "5"), pick = "3",
                    then = "Three points. Six letters make 5, and eight or more make 13!", help = "Three letters make 1, four make 2, five make 3."),
            ),
        )
    }

    val blotwords: Tutorial get() {
        val grid = listOf("VUMT", "M#UV")
        fun scene(inked: Set<String> = emptySet(), picked: Set<String> = emptySet()): Scene = Scene(4f, 2f, (0 until 2).flatMap { r ->
            (0 until 4).map { c ->
                val id = cellId(r, c)
                val ch = grid[r][c]
                val ink = ch == '#' || id in inked
                Item(id, c.toFloat(), r.toFloat(), look = Cell(text = if (ink) "" else ch.toString(), fill = if (ink) Fill.BLOCK else Fill.PAPER),
                    tone = if (id in picked) Tone.SELECTED else Tone.PLAIN, describe = if (ink) "inked" else ch.toString())
            }
        }, maxUnit = 60)
        val top = setOf("r0c0", "r0c1", "r0c2")
        return Tutorial(
            GameId.BLOTWORDS,
            "Ink every square by writing invented command words, and find out what each word does.",
            rules = listOf(
                "Ink every square to win.",
                "Write a command word by tapping its letters in order in a straight line: across or down, forwards or backwards.",
                "Inked squares are skipped, so the letters on either side of an inked square count as neighbours.",
                "Writing a word inks its letters. Then the word does something, straight away.",
                "Each command word does something different. Try it and see, or tap Show what it does in the word list.",
                "A word can only be written when what it does can then be used.",
                "Discover brings in the words and special squares one small puzzle at a time. Easy uses VUM; Medium adds DRIF; Hard adds ZUV and GOBA; Expert adds KEL. Daily is a new puzzle every day.",
                "Knots join letters, and a word can turn a corner on one. A ? square is any letter, a sealed square needs inking twice, and gaps in the board count as ink.",
                "Hint shows the next word to write, or where to use it. Undo steps back and Restart goes back to the start.",
            ),
            tips = listOf("Squares that no word can ever reach have to be inked by a word's effect, so save effects for them.",
                "Inking a square can join letters into a new word, or break one apart. Look a turn ahead."),
            steps = listOf(
                Step("The only way to ink squares is to write a command word. VUM reads across the top row. Tap its first letter, V.",
                    scene(), tap = setOf("r0c0"), after = scene(picked = setOf("r0c0"))),
                Step("Now tap U.", scene(picked = setOf("r0c0")), tap = setOf("r0c1"), after = scene(picked = setOf("r0c0", "r0c1"))),
                Step("And M to finish the word.", scene(picked = setOf("r0c0", "r0c1")), tap = setOf("r0c2"), after = scene(inked = top),
                    then = "VUM is written, so its letters are inked."),
                Step("Then the word does something. Working that out is part of the game, but just this once: VUM lets you ink one more square. Tap T.",
                    scene(inked = top), tap = setOf("r0c3"), after = scene(inked = top + "r0c3")),
                Step("Words also read backwards, and inked squares are skipped. In the bottom row, VUM reads from right to left, over the inked square. Tap its V.",
                    scene(inked = top + "r0c3"), tap = setOf("r1c3"), after = scene(inked = top + "r0c3", picked = setOf("r1c3")),
                    help = "Start from the right-hand end of the bottom row."),
                Step("Writing it inks V, U and M, and nothing is left, so VUM has nothing more to do. Every square is inked: solved! Other words do other things. Find out by playing Discover.",
                    scene(inked = setOf("r0c0", "r0c1", "r0c2", "r0c3", "r1c0", "r1c2", "r1c3"))),
            ),
        )
    }

    val wordMeaning: Tutorial get() {
        fun scene(verdict: String = "", tone: Tone = Tone.PLAIN): Scene {
            val items = mutableListOf(
                Item("word", 0f, 0f, 8f, 1f, Label("frugal", size = 0.7f, bold = true, center = false)),
                Item("kind", 0f, 1f, 8f, 0.6f, Label("adjective", size = 0.34f, center = false)),
                Item("sentence", 0f, 1.7f, 8f, 1.3f, Label("My frugal uncle mends his old coat instead of buying a new one.", size = 0.36f, center = false)),
            )
            if (verdict.isNotEmpty()) items += Item("verdict", 0f, 3.2f, 8f, 0.8f, Label(verdict, size = 0.36f, bold = true, center = false), tone = tone)
            return Scene(8f, 4f, items, maxUnit = 44)
        }
        return Tutorial(
            GameId.WORD_MEANING,
            "Explain what a word means in your own words.",
            rules = listOf(
                "Each round has eight words, each shown in a sentence that uses it.",
                "Type what you think the word means. Your own words are fine; you don't need a dictionary definition.",
                "A right answer scores 2, or 1 if you used a hint. A close answer gets one more try and scores 1.",
                "After every word the real meaning is shown. If the judge got it wrong, tap I was right.",
                "Show meaning skips a word you don't know, so you still learn it.",
            ),
            tips = listOf("Read the whole sentence: what the person does tells you a lot about the word.",
                "Say what the word is like, not just an example of it.",
                "Short answers are fine. \"Careful with money\" is as good as a long definition."),
            steps = listOf(
                Step("Here's a word in a sentence. The uncle fixes his old coat rather than buy a new one.", scene()),
                Step("What do you think frugal means?", scene().choices("Careful with money", "Tastes of fruit", "Very rich"), pick = "Careful with money",
                    after = scene("Right! Careful not to waste money.", Tone.GOOD), then = "In the game you type your answer; the judge understands your own words.",
                    help = "Mending an old coat saves money."),
                Step("A close answer, like \"cheap\", gets one more try. What could you add to make it right?",
                    scene("Close: \"cheap\"", Tone.FOUND).choices("Doesn't like wasting money", "Costs very little", "Poor"), pick = "Doesn't like wasting money",
                    after = scene("Right! Frugal people avoid waste.", Tone.GOOD), then = "The judge also understands \"not\" and \"doesn't\", so \"doesn't spend much\" counts too.",
                    help = "Cheap describes prices; frugal describes a person's habits."),
            ),
        )
    }
}
