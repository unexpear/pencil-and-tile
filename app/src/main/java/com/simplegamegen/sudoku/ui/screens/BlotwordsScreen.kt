package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.wordplay.BlotDaily
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.drawscope.rotate
import com.simplegamegen.sudoku.wordplay.WILD
import com.simplegamegen.sudoku.wordplay.KNOT
import androidx.compose.runtime.saveable.rememberSaveable
import com.simplegamegen.sudoku.ui.blot.BlotMap
import androidx.compose.ui.graphics.drawscope.clipRect
import com.simplegamegen.sudoku.ui.blot.BlotTile
import com.simplegamegen.sudoku.ui.blot.drawSpeedLines
import com.simplegamegen.sudoku.ui.blot.TrailStyle
import com.simplegamegen.sudoku.ui.blot.drawInkBridge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import com.simplegamegen.sudoku.ui.blot.drawPaperGrain
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.key
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.simplegamegen.sudoku.ui.blot.BlotColors
import com.simplegamegen.sudoku.ui.blot.BlotMark
import com.simplegamegen.sudoku.ui.blot.BlotTheme
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.ArcadeStore
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.blot.BlotThemes
import com.simplegamegen.sudoku.ui.blot.Mascot
import com.simplegamegen.sudoku.ui.blot.drawMark
import com.simplegamegen.sudoku.ui.blot.AssetSlot
import com.simplegamegen.sudoku.ui.blot.FillStyle
import com.simplegamegen.sudoku.ui.blot.drawParty
import com.simplegamegen.sudoku.ui.blot.drawTrail
import com.simplegamegen.sudoku.ui.blot.markMovesWhileIdle
import com.simplegamegen.sudoku.ui.blot.rememberAsset
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.simplegamegen.sudoku.ui.blot.tileShape
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.wordplay.BLANK
import com.simplegamegen.sudoku.wordplay.BlotEffect
import com.simplegamegen.sudoku.wordplay.BlotGenerator
import com.simplegamegen.sudoku.wordplay.BlotTier
import com.simplegamegen.sudoku.wordplay.BlotTrail
import com.simplegamegen.sudoku.wordplay.BlotWord
import com.simplegamegen.sudoku.wordplay.Blots
import com.simplegamegen.sudoku.wordplay.Blotwords
import com.simplegamegen.sudoku.wordplay.INK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TRAIL_KEY = "blotwords:trail"
private const val KNOWN_KEY = "blotwords:known"
private const val DAILY_KEY = "blotwords:daily"
const val BLOT_THEMES_ROUTE = "blot_themes"

val BlotSettings = listOf("Discover") + LevelNames + "Daily"
private const val DAILY_SETTING = 5

private fun todayKey(d: java.time.LocalDate = java.time.LocalDate.now()) = d.year * 10_000 + d.monthValue * 100 + d.dayOfMonth

fun blotwordsSetup(factory: PuzzleFactory, store: ArcadeStore): PlaySetup<Blotwords> = PlaySetup(
    rules = "Ink every square. Write a command word by tapping its letters in a straight line, across or down, forwards or " +
        "backwards. Inked squares are skipped, so the letters on either side of them count as neighbours. The word's letters " +
        "are inked, and then the word does something. Each word does something different: find out by trying, or peek in the " +
        "word list. A word can only be written when what it does can then be used. Knot squares join letters: a word can run " +
        "through any number of them and turn a corner on one, and writing never inks a knot. A ? square stands for any letter. " +
        "A sealed square loses its seal the first time it's inked and needs inking again. Gaps in the board count as ink. " +
        "Discover brings in the words one small puzzle at a time, and every puzzle can be finished.",
    settingTitle = "Mode", settings = BlotSettings,
    describe = { i ->
        when (i) {
            0 -> "Small puzzles, one new word at a time"
            DAILY_SETTING -> "A new puzzle every day, the same for everyone; easy on Mondays, hardest on Sundays"
            else -> BlotTier.entries[i].let { t -> "${t.width}×${t.height} grid · ${t.effects.size} words" }
        }
    },
    settingOf = { if (it.daily != 0) DAILY_SETTING else it.tier.ordinal }, inProgress = { !it.solved },
    subtitle = { g ->
        if (g.daily != 0) "Daily · ${g.daily / 100 % 100}/${g.daily % 100} · ${g.tier.label}"
        else if (g.step >= 0) "Discover · puzzle ${g.step + 1} of ${BlotTrail.steps.size}"
        else if (g.left == 1) "${g.tier.label} · 1 square left" else "${g.tier.label} · ${g.left} squares left"
    },
    create = { i ->
        {
            val theme = BlotThemes.chosen(store).id
            if (i == 0) {
                val step = store.load(TRAIL_KEY)?.toIntOrNull() ?: 0
                withContext(Dispatchers.Default) { BlotTrail.puzzle(step % BlotTrail.steps.size, theme) }
            } else if (i == DAILY_SETTING) {
                val today = java.time.LocalDate.now()
                withContext(Dispatchers.Default) { BlotDaily.puzzle(todayKey(today), today.dayOfWeek.value, theme) }
            } else factory.custom("blot:$i", { seed -> BlotGenerator.generate(BlotTier.entries[i], seed, theme) }) { it.start }
        }
    },
    identity = { if (it.daily != 0) "daily${it.daily}" else if (it.step >= 0) "trail${it.step}" else it.seed.toString() },
    outcome = { g -> if (g.solved) Outcome(Result.WON) else null },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlotwordsScreen(nav: NavController, vm: PlayViewModel<Blotwords>, factory: PuzzleFactory, store: ArcadeStore) {
    val look = LocalGameLook.current
    val c = look.colors
    val scope = rememberCoroutineScope()
    var known by remember { mutableStateOf(emptySet<String>()) }
    // The Discover map: how many trail puzzles are done, whether it's showing, and which one was just solved.
    var trailDone by remember { mutableIntStateOf(0) }
    var showMap by rememberSaveable { mutableStateOf(false) }
    var justSolved by remember { mutableStateOf<Int?>(null) }
    var dailyDone by remember { mutableStateOf(emptySet<Int>()) }
    var theme by remember { mutableStateOf(BlotThemes.ink) }
    LaunchedEffect(Unit) {
        known = store.load(KNOWN_KEY)?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        trailDone = store.load(TRAIL_KEY)?.toIntOrNull() ?: 0
        dailyDone = store.load(DAILY_KEY)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet().orEmpty()
        // The chosen theme dresses every game, including one already under way.
        theme = BlotThemes.chosen(store)
    }
    fun learn(words: Collection<BlotWord>) {
        val next = known + words.map { it.text }
        if (next != known) { known = next; scope.launch { store.save(KNOWN_KEY, next.joinToString(",")) } }
    }
    var path by remember { mutableStateOf(listOf<Int>()) }
    var firstPick by remember { mutableStateOf<Int?>(null) }
    var fillAt by remember { mutableStateOf<Int?>(null) }
    var marked by remember { mutableStateOf(listOf<Int>()) }
    var thinking by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<String?>(null) }
    // The order squares were last inked in, so they fill like a pen stroke; and which square to shake.
    var inkOrder by remember { mutableStateOf(listOf<Int>()) }
    var shake by remember { mutableStateOf(-1 to 0) }
    fun nope(i: Int, message: String) { shake = i to shake.second + 1; vm.say(message) }
    val setup = remember(factory, store) { blotwordsSetup(factory, store) }

    PlayShell(nav, vm, GameId.BLOTWORDS, setup, tools = { g, s ->
        ToolButton(GameIcons.Palette, "Themes", enabled = !s.busy) { nav.navigate(BLOT_THEMES_ROUTE) }
        if (g.step >= 0) ToolButton(GameIcons.Grid, if (showMap) "Puzzle" else "Map", enabled = !s.busy) { showMap = !showMap; if (!showMap) justSolved = null }
        if (g.solved && !showMap) ToolButton(GameIcons.Play, if (g.step >= 0) "Next puzzle" else "Next grid", enabled = !s.busy) {
            // On the Discover trail, the map shows the solved puzzle's land inking over first.
            if (g.step >= 0) showMap = true
            else if (g.daily != 0) vm.say(if (g.daily == todayKey()) "That's today's puzzle done. A new one comes tomorrow!" else "Starting today's puzzle.")
                .also { if (g.daily != todayKey()) vm.start(setup.create(DAILY_SETTING)) }
            else vm.start(setup.create(g.tier.ordinal))
        } else if (!showMap) ToolButton(GameIcons.Restart, "Restart", enabled = !s.busy && g.cells != g.start) { vm.play("Back to the start.") { it.restart() } }
        if (!showMap) HintButton(GameId.BLOTWORDS, enabled = !g.solved && !s.busy && !thinking) {
            thinking = true
            scope.launch {
                val move = withContext(Dispatchers.Default) { g.nextMove() }
                thinking = false
                when {
                    move == null -> vm.say("There's no way to finish from here. Undo a few turns, or restart.")
                    g.pending == null -> {
                        marked = move.path
                        vm.play("Write ${move.word.text} on the marked squares.") { it.hinted() }
                    }
                    else -> {
                        marked = move.targets
                        vm.play(if (move.letter != null) "Write ${move.letter} in the marked blank." else "Use ${move.word.text} on the marked squares.") { it.hinted() }
                    }
                }
            }
        }
    }) { g, s ->
        val tc = theme.colors.colors(look.dark)
        LaunchedEffect(g.cells, g.pending) { path = emptyList(); firstPick = null; fillAt = null; marked = emptyList() }
        val routes = remember(g.cells, g.pending) { if (g.pending != null) emptyList() else Blots.routes(g.cells, g.width, g.words, g.wrap) }
        LaunchedEffect(g.solved) {
            if (!g.solved) return@LaunchedEffect
            learn(g.written)
            if (g.daily != 0 && g.daily !in dailyDone) {
                dailyDone = dailyDone + g.daily
                store.save(DAILY_KEY, dailyDone.sorted().takeLast(400).joinToString(","))
            }
            if (g.step >= 0) {
                val done = store.load(TRAIL_KEY)?.toIntOrNull() ?: 0
                if (g.step + 1 > done) { store.save(TRAIL_KEY, (g.step + 1).toString()); trailDone = g.step + 1; justSolved = g.step }
            }
        }

        if (showMap && g.step >= 0) {
            Text("Discover map", style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(if (trailDone >= BlotTrail.steps.size) "Every puzzle solved. The whole island is inked!" else "Tap a numbered block to play it.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted)
            BlotMap(theme, tc, solved = trailDone.coerceAtMost(BlotTrail.steps.size), total = BlotTrail.steps.size, fresh = justSolved) { step ->
                showMap = false; justSolved = null
                vm.start { withContext(Dispatchers.Default) { BlotTrail.puzzle(step, theme.id) } }
            }
            return@PlayShell
        }

        fun tap(i: Int) {
            if (g.solved || s.busy) return
            val pending = g.pending
            if (pending == null) {
                // Knots only join letters; a word is traced by its letters.
                if (g.cells[i] == INK || g.cells[i] == KNOT) return
                marked = emptyList()
                val grown = if (i in path) path.subList(0, path.indexOf(i)) else path + i
                // Either end may come first: the word is read whichever way spells it.
                fun fits(p: List<Int>) = routes.any { r -> r.path.size >= p.size && (r.path.subList(0, p.size) == p || r.path.takeLast(p.size) == p.reversed()) }
                // A square that can't carry on any word starts a new one from there.
                val next = if (grown.size >= 2 && !fits(grown)) listOf(i) else grown
                val placed = routes.firstOrNull { it.path == next } ?: routes.firstOrNull { it.path == next.reversed() }
                when {
                    placed != null -> {
                        path = emptyList()
                        inkOrder = placed.route
                        val word = placed.word
                        vm.play(if (word.text in known) "${word.text}: ${say(word.effect.effect)}" else "${word.text} is written. What does it do?") { it.write(placed.path) }
                    }
                    listOf(next, next.reversed()).any { p -> g.words.any { w -> w.text.length == p.size && p.indices.all { k -> g.cells[p[k]] == w.text[k] } } } -> {
                        path = emptyList()
                        nope(i, "That word has nothing to work on right now.")
                    }
                    else -> path = next
                }
                return
            }
            val refusal = "${pending.text} doesn't work on that."
            fun apply(next: Blotwords?, order: List<Int>) {
                if (next == null) { nope(i, refusal); return }
                // Spreads out from the tapped square.
                inkOrder = order.sortedBy { kotlin.math.abs(it / g.width - i / g.width) + kotlin.math.abs(it % g.width - i % g.width) }
                vm.play { next }
            }
            when (pending.effect) {
                BlotEffect.ONE -> apply(g.use(i), listOf(i))
                BlotEffect.ALIKE -> apply(g.use(i), g.cells.indices.filter { g.cells[it] == g.cells[i] })
                BlotEffect.DIAG -> apply(g.use(i), Blots.diagonalOf(g.width, g.height, i).filter { g.cells[it] != INK })
                BlotEffect.MEND -> apply(g.use(i), listOf(i))
                BlotEffect.PAIR -> {
                    val a = firstPick
                    when {
                        g.cells[i] == INK -> nope(i, refusal)
                        a == null -> firstPick = i
                        a == i -> firstPick = null
                        else -> { firstPick = null; apply(g.use(a, i), listOf(a, i)) }
                    }
                }
                BlotEffect.WRITE -> if (g.cells[i] == BLANK) fillAt = i else nope(i, refusal)
            }
        }

        // The words: a chip each; tap one to see what it does, or to peek if it's still a mystery.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            g.words.forEach { w ->
                val open = w.text in known
                // Each word is a rubber stamp, set down a little crooked.
                val tilt = remember(w.text) { (Math.floorMod(w.text.hashCode(), 5) - 2) * 1.3f }
                val ink = if (open) tc.accent else c.muted
                Surface(onClick = { detail = if (detail == w.text) null else w.text },
                    color = when { g.pending == w -> tc.accent.copy(alpha = 0.22f); detail == w.text -> tc.accent.copy(alpha = 0.1f); else -> tc.paper },
                    border = BorderStroke(2.dp, ink), shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.graphicsLayer { rotationZ = tilt }
                        .semantics { contentDescription = if (open) "${w.text}: ${say(w.effect.effect)}" else say("${w.text}: not found out yet") }) {
                    Box(Modifier.padding(3.dp).border(1.dp, ink.copy(alpha = 0.6f), RoundedCornerShape(4.dp))) {
                        androidx.compose.material3.Text(if (open) w.text else "${w.text} ?", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, letterSpacing = 2.sp, color = ink, fontSize = 16.sp)
                    }
                }
            }
            if (g.hints > 0) InfoChip(if (g.hints == 1) "1 hint" else "${g.hints} hints", icon = GameIcons.Hint)
            if (g.daily != 0) BlotDaily.streak(dailyDone, java.time.LocalDate.now()).let { n ->
                if (n > 0) InfoChip(if (n == 1) "1 day in a row" else "$n days in a row", emphasized = true)
            }
        }
        g.words.firstOrNull { it.text == detail }?.let { w ->
            if (w.text in known) Text("${w.text}: ${say(w.effect.effect)}", style = MaterialTheme.typography.bodyMedium, color = c.text)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${w.text}: not found out yet", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                TextButton(onClick = { learn(listOf(w)) }) { Text("Show what it does") }
            }
        }

        // The creature talks you through it.
        val saying = when {
            g.solved -> if (g.step >= 0 && g.step == BlotTrail.steps.lastIndex) "Trail complete! Every word is yours." else "All inked!"
            g.pending != null -> g.pending!!.let { w ->
                if (w.text in known) when (w.effect) {
                    BlotEffect.ONE -> "${w.text}: tap any square to ink it."
                    BlotEffect.PAIR -> if (firstPick == null) "${w.text}: tap two squares that touch." else "${w.text}: now tap a square touching the first."
                    BlotEffect.ALIKE -> "${w.text}: tap a letter to ink every square showing it."
                    BlotEffect.WRITE -> "${w.text}: tap a blank square, then pick a letter."
                    BlotEffect.DIAG -> "${w.text}: tap a square to ink its whole rising diagonal."
                    BlotEffect.MEND -> "${w.text}: tap an inked square to bring it back, or a letter to seal it."
                } else "${w.text} is waiting. Tap squares to find out what it does."
            }
            g.stuck -> "No command word can be written now. Undo a few turns, or restart."
            thinking -> "Looking for a way through…"
            path.isEmpty() && g.wrap && g.written.isEmpty() ->
                "The edges join! A word can run off one side and come back on the other."
            path.isEmpty() && KNOT in g.start && g.step >= 0 && g.written.isEmpty() ->
                "New: knots! A word can run through any number of them and turn a corner on one. Writing never inks a knot."
            path.isEmpty() && WILD in g.start && g.step >= 0 && g.written.isEmpty() -> "New: a ? square stands for any letter you need."
            path.isEmpty() && g.start.any { it in 'a'..'z' } && g.step >= 0 && g.written.isEmpty() ->
                "New: sealed squares! The first time one is inked its seal breaks instead. Ink it again to finish it."
            else -> "Tap a word's letters in order, in a straight line."
        }
        val hop = remember { Animatable(0f) }
        LaunchedEffect(g.written.size) {
            if (g.written.isEmpty()) return@LaunchedEffect
            hop.animateTo(1f, tween(130)); hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 420f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mascot(theme, tc, happy = g.solved, modifier = Modifier.size(72.dp), bounce = hop.value)
            val edge = if (g.solved) c.success else tc.accent
            Box(Modifier.weight(1f).drawBehind {
                val tail = 10.dp.toPx()
                val body = Path().apply { addRoundRect(RoundRect(tail, 0f, size.width, size.height, CornerRadius(16.dp.toPx()))) }
                val point = Path().apply {
                    moveTo(tail + 2f, size.height * 0.38f); lineTo(0f, size.height * 0.62f); lineTo(tail + 2f, size.height * 0.66f); close()
                }
                val bubble = Path().apply { op(body, point, PathOperation.Union) }
                drawPath(bubble, tc.paper)
                drawPath(bubble, edge.copy(alpha = 0.16f))
                drawPath(bubble, edge, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
            }.padding(start = 10.dp)) {
                Text(saying, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyMedium,
                    color = if (g.stuck) c.danger else c.text, fontWeight = if (g.solved) FontWeight.Bold else FontWeight.Normal)
            }
        }
        fillAt?.let { at ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BlotWord.lettersOf(g.words).forEach { ch ->
                    Surface(onClick = { g.fill(at, ch)?.let { next -> vm.play { next } } }, color = c.surfaceAlt, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(44.dp).semantics { contentDescription = say("Write $ch") }) {
                        Box(contentAlignment = Alignment.Center) { androidx.compose.material3.Text(ch.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text) }
                    }
                }
            }
        }

        // The board, on the theme's paper (or picture).
        val loop = rememberInfiniteTransition(label = "board")
        val phase by if (theme.markMovesWhileIdle()) loop.animateFloat(0f, 1f, infiniteRepeatable(tween((3200 * theme.motion.pace.factor).toInt(), easing = LinearEasing)), label = "sea")
            else remember { mutableStateOf(0f) }
        val party = remember { Animatable(0f) }
        LaunchedEffect(g.solved) {
            if (g.solved) { party.snapTo(0f); party.animateTo(1f, tween((2400 * theme.motion.pace.factor).toInt(), easing = LinearEasing)) } else party.snapTo(0f)
        }
        val boardPicture = rememberAsset(theme, AssetSlot.BOARD)
        val markPicture = rememberAsset(theme, AssetSlot.MARK)
        val tilePicture = rememberAsset(theme, AssetSlot.TILE)
        val partyPicture = rememberAsset(theme, AssetSlot.PARTICLE)
        Surface(color = tc.paper, shape = RoundedCornerShape(20.dp), shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Box {
                if (boardPicture == null) Canvas(Modifier.matchParentSize()) { drawSpeedLines(tc.mark, 5); drawPaperGrain(tc.mark, 11) }
                if (boardPicture != null) Image(boardPicture, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(), alpha = 0.9f)
                BoxWithConstraints(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                    val side = min((maxWidth - 8.dp) / g.width, if (g.height >= 6) 50.dp else 58.dp)
                    val gap = 3.dp
                    key(g.seed, g.step, g.start) {
                        // A newly written word draws its stroke through its letters; earlier ones stay joined up.
                        val stroke = remember { Animatable(1f) }
                        var seen by remember { mutableIntStateOf(g.strokes.size) }
                        LaunchedEffect(g.strokes.size) {
                            if (g.strokes.size > seen) {
                                stroke.snapTo(0f)
                                stroke.animateTo(1f, tween(((g.strokes.last().size) * theme.motion.stroke * theme.motion.pace.factor + 150).toInt(), easing = LinearEasing))
                            }
                            seen = g.strokes.size
                        }
                        Box {
                            val stepPx = with(LocalDensity.current) { (side + gap).toPx() }
                            // Blocks have a side, so their faces are a little smaller than the square.
                            val depthPx = with(LocalDensity.current) { blockDepth(theme, side).toPx() }
                            val halfPx = with(LocalDensity.current) { side.toPx() / 2 } - depthPx / 2
                            fun centre(c: Int) = Offset((c % g.width) * stepPx + halfPx, (c / g.width) * stepPx + halfPx)
                            // On a board whose edges join: which steps of a route cross the join, and which way it's heading.
                            fun crossing(route: List<Int>): Pair<Set<Int>, Offset> {
                                if (!g.wrap || route.size < 2) return emptySet<Int>() to Offset.Zero
                                val steps = (1 until route.size).map { k ->
                                    Triple(k, Integer.signum(route[k] / g.width - route[k - 1] / g.width), Integer.signum(route[k] % g.width - route[k - 1] % g.width))
                                }
                                // Per axis, the heading that makes the shortest trip round the board; steps against it cross the join.
                                fun heading(size: Int, deltas: List<Int>): Int {
                                    val moves = deltas.filter { it != 0 }
                                    if (moves.isEmpty()) return 0
                                    fun trip(dir: Int) = moves.sumOf { d -> if (Integer.signum(d) == dir) kotlin.math.abs(d) else size - kotlin.math.abs(d) }
                                    return if (trip(1) <= trip(-1)) 1 else -1
                                }
                                val dr = heading(g.height, (1 until route.size).map { route[it] / g.width - route[it - 1] / g.width })
                                val dc = heading(g.width, (1 until route.size).map { route[it] % g.width - route[it - 1] % g.width })
                                val jumps = steps.filter { (_, r, c) -> (r != 0 && r == -dr) || (c != 0 && c == -dc) }.map { it.first }.toSet()
                                return jumps to Offset(dc.toFloat(), dr.toFloat())
                            }
                            val bridge = if (theme.mark == BlotMark.BLOCK && markPicture == null) halfPx * 1.9f else halfPx * 0.62f
                            Canvas(Modifier.matchParentSize()) {
                                g.strokes.forEachIndexed { n, word ->
                                    // Join each unbroken stretch of the word; a square brought back or still sealed breaks the bar there.
                                    val runs = ArrayList<List<Int>>(); var run = ArrayList<Int>()
                                    for (c in word) if (g.cells[c] == INK || g.cells[c] == KNOT) run.add(c) else { runs += run; run = ArrayList() }
                                    runs += run
                                    for (r in runs) if (r.size >= 2 && r.any { g.cells[it] == INK }) {
                                        val (jumps, travel) = crossing(r)
                                        drawInkBridge(if (theme.mark == BlotMark.BLOCK) TrailStyle.LINE else theme.motion.trail, r.map(::centre),
                                            if (n == g.strokes.lastIndex) stroke.value else 1f, bridge, tc.mark, jumps, travel, stepPx * 0.75f)
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                for (r in 0 until g.height) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                    for (k in 0 until g.width) {
                                        val i = r * g.width + k
                                        if (i in g.holes) { Spacer(Modifier.size(side)); continue }
                                        BlotCell(g, i, side, theme, tc, phase, markPicture, tilePicture,
                                            picked = i in path || i == firstPick || i == fillAt, hinted = i in marked,
                                            order = inkOrder.indexOf(i).coerceAtLeast(0), shake = if (shake.first == i) shake.second else 0,
                                            newest = g.strokes.lastOrNull()?.contains(i) == true) { tap(i) }
                                    }
                                }
                            }
                            // The trail while tracing a word.
                            if (path.isNotEmpty()) Canvas(Modifier.matchParentSize()) {
                                // The route of a word this could become, up to the last letter picked.
                                val along = routes.firstNotNullOfOrNull { r ->
                                    when {
                                        r.path.size >= path.size && r.path.subList(0, path.size) == path -> r.route.subList(0, r.route.indexOf(path.last()) + 1)
                                        r.path.size >= path.size && r.path.takeLast(path.size) == path.reversed() ->
                                            r.route.subList(r.route.indexOf(path.last()), r.route.size).reversed()
                                        else -> null
                                    }
                                } ?: path
                                val (jumps, travel) = crossing(along)
                                drawTrail(theme.motion.trail, along.map(::centre), side.toPx() * 0.13f, tc.accent, jumps, travel, stepPx * 0.75f)
                            }
                            // The pen passing through the word just written, fading as its ink settles.
                            if (stroke.value < 1f && g.strokes.isNotEmpty()) Canvas(Modifier.matchParentSize()) {
                                val t = stroke.value
                                val (jumps, travel) = crossing(g.strokes.last())
                                drawInkBridge(TrailStyle.LINE, g.strokes.last().map(::centre), (t * 1.6f).coerceAtMost(1f), side.toPx() * 0.12f,
                                    tc.accent.copy(alpha = (1f - t) * 0.9f), jumps, travel, stepPx * 0.75f)
                            }
                            // Joined edges: dashed ink round the board with arrows that carry on across.
                            if (g.wrap) Canvas(Modifier.matchParentSize()) {
                                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                                val inset = -gap.toPx() * 1.5f
                                drawRoundRect(tc.accent, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset),
                                    androidx.compose.ui.geometry.CornerRadius(12f), style = Stroke(3f, pathEffect = dash))
                                val a = stepPx * 0.14f
                                for (k in 0 until g.height) {
                                    val y = centre(k * g.width).y
                                    for ((x, dir) in listOf(inset to -1f, size.width - inset to 1f)) {
                                        val head = Path().apply { moveTo(x + dir * a, y); lineTo(x - dir * a * 0.2f, y - a); lineTo(x - dir * a * 0.2f, y + a); close() }
                                        drawPath(head, tc.accent)
                                    }
                                }
                                for (k in 0 until g.width) {
                                    val x = centre(k).x
                                    for ((y, dir) in listOf(inset to -1f, size.height - inset to 1f)) {
                                        val head = Path().apply { moveTo(x, y + dir * a); lineTo(x - a, y - dir * a * 0.2f); lineTo(x + a, y - dir * a * 0.2f); close() }
                                        drawPath(head, tc.accent)
                                    }
                                }
                            }
                            // Solved: the theme's pieces float up off the board.
                            if (party.value in 0.001f..0.999f) Canvas(Modifier.matchParentSize()) {
                                drawParty(theme.motion.party, theme.motion.partyAmount, party.value, g.seed.toInt() + g.step, side.toPx(), tc, partyPicture)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A wax seal over a square: a scalloped disc with a ring pressed in it. [crack] (0..1) splits it and drops the halves. */
private fun DrawScope.drawWaxSeal(color: Color, alpha: Float, crack: Float) {
    val r = size.minDimension / 2
    val scallop = Path()
    for (k in 0 until 24) {
        val a = 2 * Math.PI * k / 24
        val rr = r * (if (k % 2 == 0) 1f else 0.9f)
        val p = Offset(center.x + (kotlin.math.cos(a) * rr).toFloat(), center.y + (kotlin.math.sin(a) * rr).toFloat())
        if (k == 0) scallop.moveTo(p.x, p.y) else scallop.lineTo(p.x, p.y)
    }
    scallop.close()
    for (half in listOf(-1f, 1f)) {
        val dx = half * crack * r * 0.6f; val dy = crack * crack * r * 1.4f
        translate(dx, dy) {
            rotate(half * crack * 25f, center) {
                clipRect(if (half < 0) 0f else center.x, 0f, if (half < 0) center.x else size.width, size.height) {
                    drawPath(scallop, color.copy(alpha = 0.28f * alpha * (1f - crack)))
                    drawPath(scallop, color.copy(alpha = 0.9f * alpha * (1f - crack)), style = Stroke(r * 0.07f))
                    drawCircle(color.copy(alpha = 0.7f * alpha * (1f - crack)), r * 0.72f, center, style = Stroke(r * 0.05f))
                }
            }
        }
    }
}

/** How deep a block's side is for [theme]: square and rounded tiles are chunky blocks, bubbles are flat. */
fun blockDepth(theme: BlotTheme, side: Dp): Dp = if (theme.tile == BlotTile.BUBBLE) 0.dp else side * 0.12f

/** One square: its letter tile, or its mark filling in (in stroke order, after [order] others), a lift when picked and a shake when refused. */
@Composable
private fun BlotCell(g: Blotwords, i: Int, side: Dp, theme: BlotTheme, tc: BlotColors, phase: Float, markPicture: ImageBitmap?, tilePicture: ImageBitmap?,
    picked: Boolean, hinted: Boolean, order: Int, shake: Int, newest: Boolean, onTap: () -> Unit) {
    val c = LocalGameLook.current.colors
    val ch = g.cells[i]
    val inked = ch == INK
    val motion = theme.motion
    val fill = remember { Animatable(if (inked) 1f else 0f) }
    LaunchedEffect(inked) {
        // Brought back by MEND: the ink lifts off the square.
        if (!inked) { if (fill.value > 0f) fill.animateTo(0f, tween((500 * motion.pace.factor).toInt())) else fill.snapTo(0f); return@LaunchedEffect }
        if (fill.value >= 1f) return@LaunchedEffect
        delay((order * motion.stroke * motion.pace.factor).toLong())
        // Splashes and spins land on a spring; the rest glide in.
        if (motion.fill == FillStyle.SPLASH || motion.fill == FillStyle.SPIN)
            fill.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f / (motion.pace.factor * motion.pace.factor)))
        else fill.animateTo(1f, tween((420 * motion.pace.factor).toInt(), easing = FastOutSlowInEasing))
    }
    val lift by animateFloatAsState(if (picked) 1.1f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "lift")
    val jiggle = remember { Animatable(0f) }
    LaunchedEffect(shake) {
        if (shake == 0) return@LaunchedEffect
        for (x in listOf(8f, -7f, 5f, -3f, 0f)) jiggle.animateTo(x, tween(45))
    }
    // A seal: shown while the square is sealed; when it breaks it cracks apart and falls away.
    val sealedNow = Blots.sealed(ch)
    val seal = remember { Animatable(if (sealedNow) 1f else 0f) }
    val crack = remember { Animatable(0f) }
    LaunchedEffect(sealedNow) {
        if (sealedNow) { crack.snapTo(0f); seal.snapTo(1f) }
        else if (seal.value > 0f) { crack.animateTo(1f, tween(450)); seal.snapTo(0f); crack.snapTo(0f) }
    }
    val shape = tileShape(theme.tile, side)
    val spoken = "Row ${i / g.width + 1}, column ${i % g.width + 1}, " + when (ch) { INK -> "inked"; BLANK -> "blank"; KNOT -> "knot"; WILD -> "any letter"
        else -> if (sealedNow) "${ch.uppercaseChar()}, sealed" else ch.toString() } +
        (if (picked) ", picked" else "") + (if (hinted) ", hinted" else "")
    Box(Modifier.size(side)
        .graphicsLayer { scaleX = lift; scaleY = lift; translationX = jiggle.value * density }
        // No ripple: the lift is the press feedback, and a ripple would square off round tiles.
        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, enabled = !g.solved && (!inked || g.pending?.effect == BlotEffect.MEND)) { onTap() }
        .semantics { contentDescription = say(spoken) },
        contentAlignment = Alignment.Center) {
        // The letter tile fades as the mark takes over.
        val tileAlpha = (1f - fill.value).coerceIn(0f, 1f)
        val depth = blockDepth(theme, side)
        if (tileAlpha > 0f && depth > 0.dp) Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = tileAlpha }) {
            val d = depth.toPx()
            val face = Size(size.width - d, size.height - d)
            val sideRect = Rect(Offset(d, d), face)
            val edge = tc.mark.copy(alpha = 0.55f)
            drawRoundRect(Color(tc.tile.red * 0.82f, tc.tile.green * 0.8f, tc.tile.blue * 0.78f), sideRect.topLeft, face, CornerRadius(face.width * 0.1f))
            clipRect(sideRect.left, sideRect.top, sideRect.right, sideRect.bottom) {
                for (k in 0..5) {
                    val x = face.width * (0.18f + 0.16f * k)
                    drawLine(edge, Offset(x, face.height), Offset(x + d, face.height + d), 1.5f)
                    val y = face.height * (0.18f + 0.16f * k)
                    drawLine(edge, Offset(face.width, y), Offset(face.width + d, y + d), 1.5f)
                }
            }
            drawRoundRect(edge, sideRect.topLeft, face, CornerRadius(face.width * 0.1f), style = Stroke(1.5f))
        }
        if (tileAlpha > 0f) {
            val bg = when { picked -> tc.accent.copy(alpha = 0.35f); hinted -> c.highlight; else -> tc.tile }
            Box(Modifier.padding(end = depth, bottom = depth).fillMaxSize().graphicsLayer { alpha = tileAlpha }.clip(shape)
                .background(Brush.verticalGradient(listOf(bg, Color(bg.red * 0.95f, bg.green * 0.94f, bg.blue * 0.92f, bg.alpha))), shape),
                contentAlignment = Alignment.Center) {
                if (tilePicture != null) Image(tilePicture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    alpha = if (picked || hinted) 0.55f else 1f)
                Box(Modifier.fillMaxSize().border(if (picked) 2.5.dp else 1.dp, if (picked) tc.accent else tc.mark.copy(alpha = 0.22f), shape))
                val shown = (if (inked) g.start[i] else ch).uppercaseChar().takeIf { it in 'A'..'Z' || it == WILD }
                if (seal.value > 0f) Canvas(Modifier.fillMaxSize(0.84f)) { drawWaxSeal(tc.accent, seal.value, crack.value) }
                // A knot: a figure-of-eight loop of ink, tied in the middle.
                if (ch == KNOT) Canvas(Modifier.fillMaxSize(0.66f)) {
                    val w = size.width
                    for (side in listOf(-1f, 1f)) drawCircle(tc.mark, w * 0.22f, Offset(center.x + side * w * 0.22f, center.y), style = Stroke(w * 0.1f))
                    drawCircle(tc.mark, w * 0.1f, center)
                }
                if (shown != null) {
                    // Ink themes set each letter by hand: a serif face, a touch crooked, pressed into the paper.
                    val press = theme.mark == BlotMark.BLOT || theme.mark == BlotMark.BLOCK
                    val tilt = if (press) (Math.floorMod(i * 37 + shown.code, 7) - 3) * 1.1f else 0f
                    Box(Modifier.graphicsLayer { rotationZ = tilt }, contentAlignment = Alignment.Center) {
                        if (press) androidx.compose.material3.Text(shown.toString(), Modifier.offset(y = 1.dp), fontSize = (side.value * 0.5f).sp,
                            fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = Color.White.copy(alpha = 0.6f))
                        androidx.compose.material3.Text(shown.toString(), fontSize = (side.value * (if (press) 0.5f else 0.46f)).sp,
                            fontWeight = if (press) FontWeight.Black else FontWeight.Bold, fontFamily = if (press) FontFamily.Serif else null, color = tc.letter)
                    }
                }
            }
        }
        if (fill.value > 0f) Canvas(Modifier.fillMaxSize()) {
            drawMark(theme.mark, motion.fill, Rect(Offset.Zero, if (depth > 0.dp) size else size), tc, i, fill.value, phase, markPicture, depth.toPx())
        }
        // On ink blocks the letter still shows through, pale on the ink; the newest word's letters stand out.
        val under = g.start[i].uppercaseChar().takeIf { it in 'A'..'Z' }
        if (inked && under != null && theme.mark == BlotMark.BLOCK && markPicture == null && fill.value > 0.6f)
            Box(Modifier.padding(end = depth, bottom = depth).fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Text(under.toString(), fontSize = (side.value * 0.46f).sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif,
                    color = tc.paper.copy(alpha = (if (newest) 0.95f else 0.4f) * ((fill.value - 0.6f) / 0.4f).coerceIn(0f, 1f)))
            }
    }
}
