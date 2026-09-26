package com.simplegamegen.sudoku.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.ArcadeStore
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.data.WordswornProgress
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.wordplay.Act
import com.simplegamegen.sudoku.wordplay.Card
import com.simplegamegen.sudoku.wordplay.Edge
import com.simplegamegen.sudoku.wordplay.Goods
import com.simplegamegen.sudoku.wordplay.Hero
import com.simplegamegen.sudoku.wordplay.Item
import com.simplegamegen.sudoku.wordplay.ItemKind
import com.simplegamegen.sudoku.wordplay.Keepsake
import com.simplegamegen.sudoku.wordplay.Offer
import com.simplegamegen.sudoku.wordplay.Path as RoutePath
import com.simplegamegen.sudoku.wordplay.Piece
import com.simplegamegen.sudoku.wordplay.Special
import com.simplegamegen.sudoku.wordplay.Splay
import com.simplegamegen.sudoku.wordplay.Step
import com.simplegamegen.sudoku.wordplay.Wordsworn
import com.simplegamegen.sudoku.wordplay.WordswornTwist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import java.io.IOException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val WordswornSetup: (PuzzleFactory) -> PlaySetup<Wordsworn> = { factory ->
    PlaySetup(
        rules = "Fight your way through the books with words. Each turn you get four letter cards, a wild letter (unused, it gives " +
            "1 ink) and the monster's weak-spot vowel. Spell a word, then splay it left or right: only the icons on that edge of " +
            "each card count, as hits, blocks and ink. The top card also does what it says, then it's worn out for the rest of " +
            "the fight: splayed right the first letter is on top, splayed left the last. Hits minus the monster's block hurt it; " +
            "blocks stop its attack. Every monster shows its actions in order and has two stages. Hexes stay until spent or " +
            "cleared; core abilities and enemy rules explain their effects. Ink pays for items; stars power core abilities and pay the shop. " +
            "Play one guardian and one boss in each of three books. After each fight choose rewards, then visit the shop. " +
            "The shop keeps its stock between fights, refills bought cards, and lets you refresh one row or column per visit. " +
            "Blots have no icons; when on top they pass the ability and fatigue to the next letter. Old saved runs keep the earlier rules.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level -> "3 books · 6 fights" + if (level == 0) " · 25 health, 2 wilds" else " · 20 health" },
        settingOf = { it.level.ordinal }, inProgress = { !it.over },
        subtitle = { g -> if (g.hero == null) "${g.level.label} · choose your hero" else "${g.level.label} · book ${g.book + 1} · fight ${g.fight + 1} of ${g.fights}" },
        create = { level -> { factory.custom("wordsworn:$level", { seed -> Wordsworn.start(seed, LogicLevel.entries[level]) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> when { g.won -> Outcome(Result.WON, g.hp.toLong()); g.lost -> Outcome(Result.LOST, g.fight.toLong()); else -> null } },
    )
}

private const val BLOOD_KEY = "wordsworn:blood"
private val HurtRed = Color(0xFFE5484D)
private val HealGreen = Color(0xFF43A047)
private val Gold = Color(0xFFFFC93C)
private val CardPaper = Color(0xFFFBF3DF)
private val CardInk = Color(0xFF26324F)
private val VowelTeal = Color(0xFF2A9D8F)
private val WildPurple = Color(0xFF8E5BD1)
private val Parchment = listOf(Color(0xFFF7EACB), Color(0xFFEBD6A6))
private val Brown = Color(0xFF3B2A16)

private val BookNames = listOf("The Dusty Stacks", "The Blotted Garden", "The Word Eater's Tower")

private fun heroBlurb(h: Hero) = when (h) {
    Hero.KNIGHT -> "Sturdy: blocks, hits and short words."
    Hero.WITCH -> "Hexes that wear monsters down, and ink to spare."
    Hero.BARD -> "Long words, vowels and extra cards."
}

private fun stepText(s: Step): String = when (s) {
    is Step.Cards -> if (s.add) "Pick 1 of ${s.offer} new cards to add to your deck." else "Pick 1 of ${s.offer} new cards to replace one in your deck."
    is Step.Items -> "Pick 1 of ${s.offer} items."
    is Step.Keepsakes -> if (s.boss) "Pick a rare keepsake." else "Pick a keepsake."
    Step.Upgrade -> "Upgrade a card."
    Step.CoreUpgrade -> "Upgrade your core item or your wild card."
    is Step.Heal -> "Heal ${s.n}."
    is Step.Hurt -> "Lose ${s.n} health."
    is Step.Stars -> if (s.n == 1) "Gain 1 star." else "Gain ${s.n} stars."
    is Step.Blots -> if (s.n == 1) "Add a blot to your deck." else "Add ${s.n} blots to your deck."
    else -> ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordswornScreen(nav: NavController, vm: PlayViewModel<Wordsworn>, factory: PuzzleFactory, store: ArcadeStore) {
    val c = LocalGameLook.current.colors
    val scope = rememberCoroutineScope()
    val progress = remember(store) { WordswornProgress(store) }
    var unlocked by remember { mutableStateOf(emptySet<Hero>()) }
    var progressLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(progress) {
        try { unlocked = progress.unlocked() } catch (_: IOException) { vm.say("Could not load earned core abilities. Reopen the game to retry.") }
        progressLoaded = true
    }
    var pieces by remember { mutableStateOf(listOf<Piece>()) }
    var splay by remember { mutableStateOf(Splay.RIGHT) }
    var askWild by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf(false) }
    /** The hand card last touched, so its ability can be read. */
    var looking by remember { mutableIntStateOf(-1) }
    var seenBook by remember { mutableIntStateOf(-1) }

    // Effects, driven by what happened last.
    val shot = remember { Animatable(0f) }
    val impact = remember { Animatable(0f) }
    val hurt = remember { Animatable(0f) }
    val lunge = remember { Animatable(0f) }
    val ouch = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val poof = remember { Animatable(0f) }
    val flipFx = remember { Animatable(0f) }
    val bite = remember { Animatable(0f) }
    var flying by remember { mutableStateOf("") }
    var blood by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { blood = store.load(BLOOD_KEY) != "0" }
    var splats by remember { mutableStateOf(listOf<Triple<Float, Int, Int>>()) }

    PlayShell(nav, vm, GameId.WORDSWORN, remember(factory) { WordswornSetup(factory) }, undoable = false, tools = { g, s ->
        HintButton(GameId.WORDSWORN, label = if (thinking) "Thinking…" else "Hint", enabled = g.fighting && !s.busy && !thinking) {
            thinking = true
            scope.launch {
                val best = withContext(Dispatchers.Default) { g.bestPlay() }
                thinking = false
                if (vm.state.value.game !== g || vm.state.value.busy) return@launch
                if (best == null) { vm.say("No word fits. Try Pass."); return@launch }
                pieces = best.first; splay = best.second
                val p = g.preview(best.first, best.second)
                vm.play(say(if (best.second == Splay.RIGHT) "Try ${p.word}, splayed right." else "Try ${p.word}, splayed left.")) { it.hinted() }
            }
        }
        ToolButton(GameIcons.Restart, "Pass", enabled = g.fighting && !s.busy) {
            g.pass()?.let { next -> vm.play(say("You pass this word.")) { next } }
        }
        if (g.currentRules) ToolButton(GameIcons.Draw, "Inspect", enabled = g.hero != null && !s.busy) { inspecting = true }
    }) { g, s ->
        if (inspecting) RunInspector(g) { inspecting = false }
        LaunchedEffect(g.won, g.hero, progressLoaded) {
            if (progressLoaded && g.won && g.hero !in unlocked) {
                try {
                    val saved = withContext(NonCancellable) { progress.recordVictory(g) }
                    if (saved) unlocked = unlocked + g.hero!!
                } catch (_: IOException) { vm.say("The run is saved, but the core unlock could not be saved. Reopen this victory to retry.") }
            }
        }
        LaunchedEffect(g.last, g.fight) {
            val b = g.last ?: return@LaunchedEffect
            if (b.word.isNotEmpty()) {
                flying = b.word
                shot.snapTo(0f); shot.animateTo(1f, tween(380, easing = FastOutSlowInEasing)); flying = ""
                launch { hurt.snapTo(1f); hurt.animateTo(0f, tween(500)) }
                impact.snapTo(0f); launch { impact.animateTo(1f, tween(900)) }
                if (b.damage > 0) splats = (splats + Triple(0.25f + 0.5f * Random(g.turn * 31 + g.fight).nextFloat(), g.turn * 7 + g.fight, g.turn)).takeLast(6)
                if (b.blocks > 0) launch { glow.snapTo(1f); glow.animateTo(0f, tween(900)) }
                delay(420)
            }
            if (b.flipped && !b.beaten) launch { flipFx.snapTo(1f); flipFx.animateTo(0f, tween(1400)) }
            if (b.beaten) { poof.snapTo(0f); poof.animateTo(1f, tween(900)); return@LaunchedEffect }
            if (b.taken > 0) {
                launch { bite.snapTo(0f); bite.animateTo(1f, tween(560, easing = LinearEasing)) }
                lunge.snapTo(0f); lunge.animateTo(1f, tween(180)); launch { lunge.animateTo(0f, spring(dampingRatio = 0.4f)) }
                ouch.snapTo(1f); ouch.animateTo(0f, tween(600))
            }
        }
        LaunchedEffect(g.fight, g.hero) { poof.snapTo(0f); splats = emptyList() }
        LaunchedEffect(g.hand, g.fight, g.offer) { pieces = emptyList(); askWild = false; looking = -1 }
        val splatColor = if (blood) Blood else InkSplat
        val loop = rememberInfiniteTransition(label = "idle")
        val phase by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase")

        when (val o = g.offer) {
            Offer.Heroes -> HeroPicker(g, phase, unlocked, onTwist = { index -> vm.play { it.toggleTwist(index) } }) { k -> vm.play(say("${Hero.entries[k % 3].label} sets out!")) { it.choose(k) } }
            null -> Box(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // A new book opens with its cover.
                    if (!g.currentRules) Text("Earlier rules · this saved run keeps its original gameplay", style = MaterialTheme.typography.bodySmall)
                    if (g.twists.isNotEmpty()) Text("Modifiers ${g.twistScore}: ${g.twists.joinToString { it.label }}", style = MaterialTheme.typography.bodySmall)
                    if (g.fighting && g.fight % g.fightsPerBook == 0 && g.turn == 0 && seenBook != g.book) BookCover(g.book, phase) { seenBook = g.book }
                    Stage(g, phase, blood, splatColor, splats, shot.value, impact.value, hurt.value, lunge.value, poof.value, flipFx.value, flying,
                        onBlood = { blood = !blood; scope.launch { store.save(BLOOD_KEY, if (blood) "1" else "0") } })
                    val preview = if (pieces.size >= 2 && g.fighting) g.preview(pieces, splay) else null
                    HeroPanel(g, phase, preview?.hits, preview?.blocks, ouch.value, glow.value)
                    when {
                        g.won -> WinBanner(say("The Word Eater is beaten. Your story is complete!") + if (g.currentRules && g.hero in unlocked) " Alternate core abilities unlocked for ${g.hero?.label}." else "")
                        g.lost -> Surface(color = c.danger.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                            Text("Defeated in fight ${g.fight + 1}. Try another run!", Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, color = c.text)
                        }
                        else -> {
                            Belongings(g) { k -> g.useItem(k)?.let { next -> vm.play(say("${g.items[k].kind.label}!")) { next } } ?: g.itemProblem(k)?.let { vm.say(it) } }
                            WordTray(g, pieces, splay, busy = s.busy, onSplay = { splay = it },
                                onRemove = { at -> pieces = pieces.filterIndexed { i, _ -> i != at } },
                                onClear = { pieces = emptyList() },
                                onPlay = {
                                    val problem = g.problem(pieces)
                                    if (problem != null) vm.say(problem)
                                    else g.play(pieces, splay)?.let { next ->
                                        val w = g.spell(pieces)
                                        vm.play(when {
                                            next.won -> say("$w! You won the run!")
                                            next.offer != null -> say("$w! ${g.monster.label} is beaten.")
                                            next.last?.flipped == true -> say("$w! ${g.monster.label} is stunned and turns nastier.")
                                            else -> null
                                        }) { next }
                                    }
                                })
                            if (askWild) LetterPicker { ch -> pieces = pieces + Piece.Wild(ch); askWild = false }
                            HandRow(g, pieces, looking,
                                onCard = { i ->
                                    looking = i
                                    val at = pieces.indexOf(Piece.Hand(i))
                                    pieces = if (at >= 0) pieces.filterIndexed { k, _ -> k != at } else pieces + Piece.Hand(i)
                                },
                                onWild = { if (pieces.count { it is Piece.Wild } < g.wilds) askWild = !askWild else vm.say(say("No wild letters left this turn.")) },
                                onVowel = {
                                    val at = pieces.indexOf(Piece.Vowel)
                                    if (at >= 0) pieces = pieces.filterIndexed { k, _ -> k != at }
                                    else if (!g.vowelWorn) pieces = pieces + Piece.Vowel
                                })
                            Text("Draw pile ${g.draw.size} · discards ${g.discard.size} · worn out ${g.worn.size}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                }
                // The monster's bite over the scene.
                if (bite.value in 0.001f..0.999f || ouch.value > 0f) Canvas(Modifier.matchParentSize()) {
                    if (bite.value in 0.001f..0.999f) drawBite(bite.value, Color(0xFF7A1F2B))
                    if (ouch.value > 0f) for (k in 0..2) drawSplat(Offset(size.width * (0.25f + 0.25f * k), size.height * (0.5f + 0.06f * (k % 2))),
                        size.minDimension * 0.04f, g.turn * 5 + k, splatColor, ouch.value)
                }
            }
            is Offer.Paths -> PathPicker(g, o.paths) { k -> vm.play { it.choose(k) } }
            is Offer.Cards -> CardOffer(g, o.cards, o.add) { k -> vm.play { it.choose(k) } }
            is Offer.Replace -> DeckPicker(g, "Choose a card for ${o.card.letter} to replace", "Keep my deck", mark = null) { k -> vm.play { it.choose(k) } }
            is Offer.Items -> ItemOffer(o.kinds, canSkip = !g.requiredChoice) { k -> vm.play { it.choose(k) } }
            is Offer.Keepsakes -> KeepsakeOffer(o.sides, canSkip = !g.requiredChoice) { k -> vm.play { it.choose(k) } }
            Offer.Upgrade -> DeckPicker(g, "Choose a card to upgrade: +1 hit, +1 block and a stronger ability", "Skip", mark = { it.upgraded }) { k -> vm.play { it.choose(k) } }
            Offer.CoreUpgrade -> CoreUpgradeOffer(g) { k -> vm.play { it.choose(k) } }
            is Offer.Shop -> ShopView(g, o, onRefresh = { group -> vm.play(say("Shop stock refreshed.")) { it.refreshShop(group) } }) { k -> if (k == -1) vm.play(say("On to the next fight!")) { it.choose(-1) } else g.buy(k)?.let { next -> vm.play { next } } ?: vm.say(say("You can't buy that now.")) }
        }
    }
}

// ---------------- The fight ----------------

/** The monster's stage: the book's scene, its name, health, counters, actions and the monster itself. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Stage(g: Wordsworn, phase: Float, blood: Boolean, splatColor: Color, splats: List<Triple<Float, Int, Int>>, shot: Float, impact: Float,
    hurt: Float, lunge: Float, poof: Float, flipFx: Float, flying: String, onBlood: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
        Canvas(Modifier.matchParentSize()) {
            drawBookScene(g.book, phase)
            for ((x, seed, born) in splats) {
                val age = (g.turn - born).coerceAtLeast(0)
                drawSplat(Offset(size.width * x, size.height * 0.9f), size.minDimension * 0.05f, seed, splatColor, (1f - age / 4f).coerceIn(0f, 1f))
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text(say(g.monster.label), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFF3E9D2))
                    androidx.compose.material3.Text(say(if (g.monster.boss) "Boss · stage ${g.stage} of 2" else "Stage ${g.stage} of 2"), fontSize = 12.sp, color = Color(0xFFD9CBB0))
                }
                // The weak spot: a vowel you can use every turn.
                if (!g.over) Surface(color = if (g.vowelWorn) Color(0x55000000) else VowelTeal, shape = CircleShape, border = BorderStroke(2.dp, Color(0xFFE0F2EF)),
                    modifier = Modifier.size(34.dp).semantics { contentDescription = say("Weak spot: ${g.monster.weak}") }) {
                    Box(contentAlignment = Alignment.Center) { androidx.compose.material3.Text(g.monster.weak.toString(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White) }
                }
                Box(Modifier.width(8.dp))
                Surface(onClick = onBlood, color = Color(0x33FFFFFF), shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.semantics { contentDescription = say(if (blood) "Splatter: blood. Tap for ink." else "Splatter: ink. Tap for blood.") }) {
                    Canvas(Modifier.padding(6.dp).size(14.dp)) { drawCircle(if (blood) Blood else InkSplat, size.minDimension / 2) }
                }
            }
            Bar(g.monsterHp.coerceAtLeast(0), g.monsterMax, HurtRed, heart = false, label = "${g.monsterHp.coerceAtLeast(0)}/${g.monsterMax}")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (g.monsterStars > 0) Counter(WsIcon.STAR, g.monsterStars, say("Monster stars: ${g.monsterStars}, each adds 1 to its attacks"), light = true)
                if (g.monsterHexes > 0) Counter(WsIcon.HEX, g.monsterHexes, say("Hexes on the monster: ${g.monsterHexes}"), light = true)
                if (g.monster.special != Special.NONE) androidx.compose.material3.Text(say(g.monster.special.text), fontSize = 11.sp, fontStyle = FontStyle.Italic,
                    color = Color(0xFFE8DCC0), modifier = Modifier.weight(1f))
            }
            if (!g.over) {
                Text(say(g.enemyStatusRule), fontSize = 11.sp, color = Color(0xFFE8DCC0))
                ActionList(g)
            }
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                val shake = sin(hurt * 30).toFloat() * 10f * hurt + sin(flipFx * 40).toFloat() * 8f * flipFx
                if (poof < 1f) Canvas(Modifier.size(160.dp).graphicsLayer {
                    translationX = shake * density
                    translationY = lunge * 26f * density
                    val grow = 1f + lunge * 0.12f - poof * 0.6f + flipFx * 0.1f
                    scaleX = grow; scaleY = grow
                    alpha = 1f - poof
                }) { drawMonster(g.monster, Rect(Offset.Zero, size), hurt, phase, angry = g.stage == 2) }
                val clash = g.last
                if (impact in 0.001f..0.999f && clash != null && clash.word.isNotEmpty()) {
                    Canvas(Modifier.size(200.dp)) {
                        val t = impact
                        val r = size.minDimension * (0.12f + 0.3f * t)
                        for (k in 0 until 10) {
                            val a = 2 * PI * k / 10 + 0.3
                            drawLine(Gold.copy(alpha = 1f - t), Offset(center.x + (cos(a) * r * 0.5f).toFloat(), center.y + (sin(a) * r * 0.5f).toFloat()),
                                Offset(center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat()), size.minDimension * 0.02f, cap = StrokeCap.Round)
                        }
                        if (clash.damage > 0) drawDroplets(center, t, clash.damage * 13 + g.turn, splatColor, size.minDimension * 0.6f)
                    }
                    androidx.compose.material3.Text(if (clash.damage > 0) "−${clash.damage}" else say("Blocked"), fontSize = 30.sp, fontWeight = FontWeight.Black, color = HurtRed,
                        modifier = Modifier.graphicsLayer { translationY = -impact * 60f * density; alpha = 1f - impact; scaleX = 1f + (1f - impact) * 0.4f; scaleY = scaleX })
                }
                if (flipFx > 0.01f) androidx.compose.material3.Text(say("Stage 2!"), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Gold,
                    modifier = Modifier.graphicsLayer { alpha = flipFx; translationY = -(1f - flipFx) * 40f * density })
                if (poof in 0.001f..0.999f) Canvas(Modifier.size(200.dp)) {
                    val rnd = Random(g.fight + 11)
                    repeat(24) {
                        val a = rnd.nextDouble(0.0, 2 * PI)
                        val d = size.minDimension * 0.5f * poof * (0.4f + 0.6f * rnd.nextFloat())
                        val at = Offset(center.x + (cos(a) * d).toFloat(), center.y + (sin(a) * d).toFloat() + poof * poof * 40f)
                        rotate(poof * 720f, at) { drawRect(listOf(Color.White, Gold, Color(0xFFD9CBB0))[it % 3].copy(alpha = 1f - poof), Offset(at.x - 6f, at.y - 4f), Size(12f, 8f)) }
                    }
                }
                if (flying.isNotEmpty()) Row(Modifier.graphicsLayer { translationY = (1f - shot) * 150f * density; alpha = 1f - shot * 0.6f
                    scaleX = 1f - shot * 0.5f; scaleY = scaleX }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    flying.forEach { ch -> MiniCard(ch) }
                }
            }
        }
    }
}

/** The monster's actions in order, with a pointer at the one it takes this turn. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionList(g: Wordsworn) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        g.acts.forEachIndexed { i, a ->
            val now = i == g.act % g.acts.size && !g.stunned
            val color = actColor(a)
            Surface(color = if (now) Color(0xFFFFF4D6) else Color(0x33FFFFFF), shape = RoundedCornerShape(8.dp),
                border = BorderStroke(if (now) 2.dp else 1.dp, if (now) Gold else Color(0x55FFFFFF)),
                modifier = Modifier.semantics { contentDescription = say(if (now) "Next: ${a.describe(g.attackOf(a) - a.attack)}" else a.describe(g.attackOf(a) - a.attack)) }) {
                Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (now) androidx.compose.material3.Text("▶ ", fontSize = 11.sp, color = color, fontWeight = FontWeight.Black)
                    androidx.compose.material3.Text(say(a.describe(g.attackOf(a) - a.attack)), fontSize = 12.sp, fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                        color = if (now) Brown else Color(0xFFEDE3CC))
                }
            }
        }
    }
}

private fun actColor(a: Act) = when {
    a.attack > 0 -> WsHit
    a.block > 0 -> WsBlock
    a.hexes > 0 -> WsHex
    else -> WsStar
}

/** The hero: portrait, health, ink, stars and hexes, and what the word being built would make. */
@Composable
private fun HeroPanel(g: Wordsworn, phase: Float, hits: Int?, blocks: Int?, ouch: Float, glow: Float) {
    val hero = g.hero ?: return
    val c = LocalGameLook.current.colors
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().graphicsLayer { translationX = sin(ouch * 28).toFloat() * 8f * ouch * density },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(56.dp)) { drawHero(hero, phase) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(hero.label, style = MaterialTheme.typography.titleSmall, color = c.text)
                    Counter(WsIcon.INK, g.ink, say("Ink: ${g.ink}"))
                    Counter(WsIcon.STAR, g.stars, say("Stars: ${g.stars}"))
                    if (g.hexes > 0) Counter(WsIcon.HEX, g.hexes, say("Your hexes: ${g.hexes}"))
                }
                Bar(g.hp.coerceAtLeast(0), g.maxHp, HealGreen, heart = true, label = "${g.hp.coerceAtLeast(0)}/${g.maxHp}")
            }
            // What the word would do: hits and blocks, with the prep bonuses counted in.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Counter(WsIcon.HIT, hits ?: g.prep.hits, say("Hits: ${hits ?: g.prep.hits}"))
                Counter(WsIcon.BLOCK, blocks ?: g.prep.blocks, say("Blocks: ${blocks ?: g.prep.blocks}"))
            }
        }
        if (glow > 0f && (g.last?.blocks ?: 0) > 0) Canvas(Modifier.matchParentSize()) {
            drawRoundRect(WsBlock.copy(alpha = 0.5f * glow), Offset.Zero, size, CornerRadius(20f), style = Stroke(6f))
        }
    }
}

/** A drawn icon and a number. */
@Composable
private fun Counter(icon: WsIcon, n: Int, describe: String, light: Boolean = false) {
    val c = LocalGameLook.current.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = describe }) {
        Canvas(Modifier.size(18.dp)) { drawWsIcon(icon, center, size.minDimension * 0.45f) }
        androidx.compose.material3.Text(n.toString(), fontWeight = FontWeight.Black, fontSize = 15.sp, color = if (light) Color(0xFFF3E9D2) else c.text,
            modifier = Modifier.padding(start = 2.dp))
    }
}

/** Items (tap to use, paid in ink) and keepsakes (tap to read). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Belongings(g: Wordsworn, onItem: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    var reading by remember { mutableStateOf<String?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        g.items.forEachIndexed { k, item ->
            val ready = g.itemProblem(k) == null
            val spent = k in g.usedItems || k in g.spentItems
            Surface(onClick = { onItem(k) }, color = if (ready) Color(0xFFFFF8E6) else c.surface, shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, if (ready) WsInk else c.muted.copy(alpha = 0.4f)),
                modifier = Modifier.graphicsLayer { alpha = if (spent) 0.45f else 1f }
                    .semantics { contentDescription = say("${item.kind.label}, costs ${item.cost} ink: ${itemText(item)}") }) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Text(say(item.kind.label), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (ready) Brown else c.muted)
                        Box(Modifier.width(4.dp))
                        Canvas(Modifier.size(12.dp)) { drawWsIcon(WsIcon.INK, center, size.minDimension * 0.45f) }
                        androidx.compose.material3.Text(item.cost.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WsInk)
                    }
                    androidx.compose.material3.Text(say(itemText(item)), fontSize = 10.sp, color = if (ready) Color(0xFF6B5B45) else c.muted)
                }
            }
        }
        g.keepsakes.forEach { k ->
            Surface(onClick = { reading = if (reading == k.text) null else k.text }, color = Color(0xFFFFF1D0), shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, WsStar), modifier = Modifier.semantics { contentDescription = say("${k.label}: ${k.text}") }) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(12.dp)) { drawWsIcon(WsIcon.STAR, center, size.minDimension * 0.45f) }
                    androidx.compose.material3.Text(say(k.label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brown, modifier = Modifier.padding(start = 3.dp))
                }
            }
        }
    }
    reading?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
}

private fun itemText(i: Item): String = when (i.kind) {
    ItemKind.OATH_SHIELD -> if (i.upgraded) "+5 blocks" else "+3 blocks"
    ItemKind.CAULDRON -> if (i.upgraded) "Give the monster 3 hexes" else "Give the monster 2 hexes"
    ItemKind.LUTE -> "Draw a card now"
    else -> i.kind.text
}

/** The word being built, splayed the chosen way, with the splay switch and the Play button. */
@Composable
private fun WordTray(g: Wordsworn, pieces: List<Piece>, splay: Splay, busy: Boolean, onSplay: (Splay) -> Unit, onRemove: (Int) -> Unit,
    onClear: () -> Unit, onPlay: () -> Unit) {
    val preview = if (pieces.size >= 2) g.preview(pieces, splay) else null
    val previewHeight = 100.dp * LocalDensity.current.fontScale
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(Parchment))
        .border(1.5.dp, Color(0xFFB08A4E), RoundedCornerShape(14.dp))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // The splay switch: which edge of each card counts.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in Splay.entries) {
                    val on = s == splay
                    Surface(onClick = { onSplay(s) }, color = if (on) (if (s == Splay.LEFT) WsBlock else WsHit) else Color(0xFFFFF8E6),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFB08A4E)), modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.Text(say(if (s == Splay.LEFT) "◀ Left icons" else "Right icons ▶"), Modifier.padding(8.dp), textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) Color.White else Brown)
                    }
                }
            }
            // Keep the hand below stationary while cards, validation and abilities appear.
            Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.CenterStart) {
                if (pieces.isEmpty()) androidx.compose.material3.Text(say("Tap cards below to spell a word"), fontSize = 15.sp, color = Color(0xFF7A6547))
                else SplayedWord(g, pieces, splay, onRemove)
            }
            Column(Modifier.fillMaxWidth().height(previewHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              if (pieces.isNotEmpty()) Text(g.spell(pieces), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Brown)
              preview?.let { p ->
                val c = LocalGameLook.current.colors
                val guard = if (p.blockedByMonster > 0) say(" (the monster blocks ${p.blockedByMonster})") else ""
                Text(buildList {
                    add(say("Deals ${p.damage}") + guard)
                    if (p.blocks > 0) add(say("blocks ${p.blocks}"))
                    if (p.ink > 0) add(say("+${p.ink} ink"))
                    if (p.hexDamage > 0) add(say("gives ${p.hexDamage} hexes"))
                }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF9A2A1E))
                if (p.top != null && p.power.isNotEmpty()) Text("${p.top} ${say("on top")}: ${say(p.power)}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5A4630))
                if (g.problem(pieces) == "Not in the word list.") Text("Not in the word list.", style = MaterialTheme.typography.bodySmall, color = c.danger)
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onClear, enabled = pieces.isNotEmpty() && !busy, color = Color(0xFFFFF8E6), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFB08A4E))) {
                    Text("Clear", Modifier.padding(12.dp), color = Brown)
                }
                val ready = pieces.size >= 2 && !busy
                Surface(onClick = onPlay, enabled = ready, color = Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.background(Brush.verticalGradient(if (ready) listOf(Color(0xFFE5484D), Color(0xFFA8232B)) else listOf(Color(0xFFB9A58A), Color(0xFF9C876B))))
                        .border(1.5.dp, Color(0xFF5E1217).copy(alpha = if (ready) 0.8f else 0.3f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("Play", Modifier.padding(12.dp), color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/** The word's cards laid over each other: only the counted edge of each card under the top one shows. */
@Composable
private fun SplayedWord(g: Wordsworn, pieces: List<Piece>, splay: Splay, onRemove: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = pieces.size
        val cardW = min(70.dp, maxWidth / 2.4f)
        val peek = ((maxWidth - cardW) / (n - 1).coerceAtLeast(1)).coerceAtMost(cardW * 0.42f)
        val top = g.topOf(pieces, splay)
        Box(Modifier.height(cardW * 1.4f).width(cardW + peek * (n - 1))) {
            // Splayed right the first card lies on top, so the cards are laid from the last; splayed left, from the first.
            val order = if (splay == Splay.RIGHT) pieces.indices.reversed() else pieces.indices
            for (i in order) {
                val p = pieces[i]
                Box(Modifier.offset(x = peek * i)) {
                    PieceCard(g, p, cardW, splay, isTop = p == top, onTap = { onRemove(i) })
                }
            }
        }
    }
}

@Composable
private fun PieceCard(g: Wordsworn, p: Piece, width: Dp, splay: Splay, isTop: Boolean, onTap: () -> Unit) {
    when (p) {
        is Piece.Hand -> LetterCard(g.deck[g.hand[p.index]], width, left = splay == Splay.LEFT || isTop, right = splay == Splay.RIGHT || isTop, top = isTop, onTap = onTap)
        is Piece.Wild -> SpecialCard(p.letter.toString(), WildPurple, width, top = false, onTap = onTap)
        Piece.Vowel -> SpecialCard(g.monster.weak.toString(), VowelTeal, width, top = isTop, onTap = onTap)
    }
}

/** A letter card: cream paper, an ink border, the letter, hit/block/ink icons down each edge and a gold frame when it's on top. */
@Composable
private fun LetterCard(card: Card, width: Dp, left: Boolean = true, right: Boolean = true, top: Boolean = false, picked: Boolean = false,
    dim: Boolean = false, onTap: (() -> Unit)? = null) {
    val h = width * 1.4f
    // Card-face lettering follows the card geometry; the accessible description scales normally.
    val letterSize = with(LocalDensity.current) { (width * 0.40f).toSp() }
    val mod = Modifier.size(width, h).graphicsLayer { alpha = if (dim) 0.4f else 1f; translationY = if (picked) -8f * density else 0f }
        .let { m -> if (onTap != null) m.clickable(role = Role.Button) { onTap() } else m }
        .semantics { contentDescription = say(cardDescription(card)) }
    Box(mod) {
        Canvas(Modifier.matchParentSize()) {
            val r = CornerRadius(size.width * 0.1f)
            if (top || picked) drawRoundRect(Gold.copy(alpha = if (top) 0.9f else 0.5f), Offset(-5f, -5f), Size(size.width + 10f, size.height + 10f), CornerRadius(size.width * 0.14f))
            drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(3f, 4f), size, r)
            val paper = if (card.blot) Color(0xFFD9D4CC) else CardPaper
            drawRoundRect(Brush.verticalGradient(listOf(paper, paper.copy(red = paper.red * 0.95f, green = paper.green * 0.93f, blue = paper.blue * 0.88f))), Offset.Zero, size, r)
            drawRoundRect(CardInk, Offset.Zero, size, r, style = Stroke(size.width * 0.03f))
            drawRoundRect(CardInk.copy(alpha = 0.5f), Offset(size.width * 0.06f, size.width * 0.06f), Size(size.width * 0.88f, size.height - size.width * 0.12f),
                CornerRadius(size.width * 0.06f), style = Stroke(size.width * 0.012f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
            if (card.blot) drawSplat(Offset(size.width * 0.5f, size.height * 0.55f), size.width * 0.25f, card.letter.code, InkSplat, 0.7f)
            val iconR = size.width * 0.055f
            if (left) drawEdgeIcons(card.left, size.width * 0.14f, size.height * 0.12f, iconR, size.height * 0.54f)
            if (right) drawEdgeIcons(card.right, size.width * 0.86f, size.height * 0.12f, iconR, size.height * 0.54f)
            if (card.upgraded) drawWsIcon(WsIcon.STAR, Offset(size.width * 0.5f, size.height * 0.9f), size.width * 0.07f)
        }
        androidx.compose.material3.Text(card.letter.toString(), Modifier.align(Alignment.Center).padding(bottom = h * 0.12f), fontSize = letterSize,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = if (card.blot) Color(0xFF55505A) else Color(0xFF1E1A14))
        if (width >= 60.dp && card.text.isNotEmpty()) androidx.compose.material3.Text(say(if (card.blot) "Blot" else card.text), Modifier.align(Alignment.BottomCenter).padding(horizontal = 4.dp, vertical = h * 0.08f),
            fontSize = 8.sp, lineHeight = 9.sp, textAlign = TextAlign.Center, color = Color(0xFF4A3B28), maxLines = 3)
    }
}

private fun DrawScope.drawEdgeIcons(e: Edge, x: Float, y0: Float, preferredRadius: Float, availableHeight: Float) {
    val count = e.hits + e.blocks + e.ink
    val r = minOf(preferredRadius, availableHeight / (2.3f * count.coerceAtLeast(1)))
    var y = y0
    repeat(e.hits) { drawWsIcon(WsIcon.HIT, Offset(x, y), r); y += r * 2.3f }
    repeat(e.blocks) { drawWsIcon(WsIcon.BLOCK, Offset(x, y), r); y += r * 2.3f }
    repeat(e.ink) { drawWsIcon(WsIcon.INK, Offset(x, y), r); y += r * 2.3f }
}

private fun cardDescription(c: Card): String = if (c.blot) "${c.letter}, a blot" else
    "${c.letter}: left ${edgeWords(c.left)}, right ${edgeWords(c.right)}; on top: ${c.text}" + if (c.upgraded) ", upgraded" else ""

private fun edgeWords(e: Edge) = buildList {
    if (e.hits > 0) add(if (e.hits == 1) "1 hit" else "${e.hits} hits")
    if (e.blocks > 0) add(if (e.blocks == 1) "1 block" else "${e.blocks} blocks")
    if (e.ink > 0) add("${e.ink} ink")
}.ifEmpty { listOf("nothing") }.joinToString(" and ")

/** The wild card and the weak-spot vowel: no icons, a coloured frame. */
@Composable
private fun SpecialCard(letter: String, color: Color, width: Dp, top: Boolean, note: String? = null, dim: Boolean = false, onTap: () -> Unit) {
    val h = width * 1.4f
    Box(Modifier.size(width, h).graphicsLayer { alpha = if (dim) 0.4f else 1f }.clickable(role = Role.Button) { onTap() }) {
        Canvas(Modifier.matchParentSize()) {
            val r = CornerRadius(size.width * 0.1f)
            if (top) drawRoundRect(Gold.copy(alpha = 0.9f), Offset(-5f, -5f), Size(size.width + 10f, size.height + 10f), CornerRadius(size.width * 0.14f))
            drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(3f, 4f), size, r)
            drawRoundRect(CardPaper, Offset.Zero, size, r)
            drawRoundRect(color, Offset.Zero, size, r, style = Stroke(size.width * 0.06f))
            drawRoundRect(color.copy(alpha = 0.6f), Offset(size.width * 0.1f, size.width * 0.1f), Size(size.width * 0.8f, size.height - size.width * 0.2f),
                CornerRadius(size.width * 0.06f), style = Stroke(size.width * 0.02f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
        }
        androidx.compose.material3.Text(letter, Modifier.align(Alignment.Center).padding(bottom = if (note != null) h * 0.12f else 0.dp), fontSize = (width.value * 0.46f).sp,
            fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = color)
        if (note != null && width >= 50.dp) androidx.compose.material3.Text(note, Modifier.align(Alignment.BottomCenter).padding(horizontal = 3.dp, vertical = h * 0.07f),
            fontSize = 8.sp, lineHeight = 9.sp, textAlign = TextAlign.Center, color = color, maxLines = 2)
    }
}

/** The hand: four letter cards, then the wild card and the weak-spot vowel. */
@Composable
private fun HandRow(g: Wordsworn, pieces: List<Piece>, looking: Int, onCard: (Int) -> Unit, onWild: () -> Unit, onVowel: () -> Unit) {
    val c = LocalGameLook.current.colors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val slots = g.hand.size + 2
        val w = min((maxWidth - 6.dp * (slots - 1)) / slots, 76.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            g.hand.forEachIndexed { i, idx ->
                LetterCard(g.deck[idx], w, picked = Piece.Hand(i) in pieces, onTap = { onCard(i) })
            }
            val wildsUsed = pieces.count { it is Piece.Wild }
            SpecialCard("?", WildPurple, w, top = false, note = say(if (g.wilds > 1) "${g.wilds - wildsUsed} left" else "Unused: 1 ink"),
                dim = wildsUsed >= g.wilds) { onWild() }
            SpecialCard(g.monster.weak.toString(), VowelTeal, w, top = false, note = say(if (g.vowelWorn) "Worn out" else "Weak spot"),
                dim = g.vowelWorn || Piece.Vowel in pieces) { onVowel() }
        }
    }
    // The last card touched, spelled out.
    g.hand.getOrNull(looking)?.let { idx -> Text(cardDescription(g.deck[idx]), style = MaterialTheme.typography.bodySmall, color = c.muted) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LetterPicker(onPick: (Char) -> Unit) {
    val c = LocalGameLook.current.colors
    Text("Wild card: pick its letter", style = MaterialTheme.typography.bodyMedium, color = c.text)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ('A'..'Z').forEach { ch ->
            Surface(onClick = { onPick(ch) }, color = c.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, WildPurple.copy(alpha = 0.5f)),
                modifier = Modifier.size(38.dp).semantics { contentDescription = say("Letter $ch") }) {
                Box(contentAlignment = Alignment.Center) { androidx.compose.material3.Text(ch.toString(), fontWeight = FontWeight.Bold, color = c.text) }
            }
        }
    }
}

@Composable
private fun MiniCard(letter: Char) {
    Box(Modifier.size(24.dp, 32.dp).background(CardPaper, RoundedCornerShape(4.dp)).border(1.dp, CardInk, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(letter.toString(), fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, fontSize = 14.sp, color = Color(0xFF1E1A14))
    }
}

/** A health bar with a heart (yours) or a skull (the monster's), a glossy fill and a trailing flash when it drops. */
@Composable
private fun Bar(value: Int, max: Int, color: Color, heart: Boolean, label: String) {
    val shown = remember { Animatable(value.toFloat()) }
    val trail = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) {
        launch { shown.animateTo(value.toFloat(), tween(300)) }
        delay(350); trail.animateTo(value.toFloat(), tween(500))
    }
    val m = max.coerceAtLeast(1).toFloat()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            if (heart) {
                val p = Path()
                p.moveTo(w * 0.5f, w * 0.9f)
                p.cubicTo(w * -0.1f, w * 0.45f, w * 0.15f, w * -0.05f, w * 0.5f, w * 0.28f)
                p.cubicTo(w * 0.85f, w * -0.05f, w * 1.1f, w * 0.45f, w * 0.5f, w * 0.9f)
                drawPath(p, color); drawPath(p, Color(0xFF1E1526), style = Stroke(w * 0.07f))
                drawCircle(Color.White.copy(alpha = 0.5f), w * 0.08f, Offset(w * 0.3f, w * 0.3f))
            } else {
                drawCircle(Color(0xFFF1EADB), w * 0.36f, Offset(w * 0.5f, w * 0.42f))
                drawRoundRect(Color(0xFFF1EADB), Offset(w * 0.3f, w * 0.55f), Size(w * 0.4f, w * 0.3f), CornerRadius(w * 0.06f))
                drawCircle(Color(0xFF1E1526), w * 0.1f, Offset(w * 0.36f, w * 0.44f))
                drawCircle(Color(0xFF1E1526), w * 0.1f, Offset(w * 0.64f, w * 0.44f))
                for (k in 0..2) drawLine(Color(0xFF1E1526), Offset(w * (0.38f + k * 0.12f), w * 0.72f), Offset(w * (0.38f + k * 0.12f), w * 0.84f), w * 0.05f)
            }
        }
        Box(Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33000000))
            .border(1.5.dp, Color(0xFF1E1526).copy(alpha = 0.6f), RoundedCornerShape(10.dp))) {
            Box(Modifier.fillMaxWidth((trail.value / m).coerceIn(0f, 1f)).height(20.dp).background(Color.White.copy(alpha = 0.7f)))
            Box(Modifier.fillMaxWidth((shown.value / m).coerceIn(0f, 1f)).height(20.dp)
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.8f), color, color.copy(red = color.red * 0.7f, green = color.green * 0.7f, blue = color.blue * 0.7f)))))
            Box(Modifier.fillMaxWidth().height(7.dp).padding(horizontal = 6.dp).align(Alignment.TopCenter).offset(y = 2.dp)
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(4.dp)))
            androidx.compose.material3.Text(label, Modifier.align(Alignment.Center), fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E1A14))
        }
    }
}

// ---------------- Between fights ----------------

/** A pulp paperback cover for each book: its scene, a bold title and a tagline. Tap to begin. */
@Composable
private fun BookCover(book: Int, phase: Float, onBegin: () -> Unit) {
    val appear = remember(book) { Animatable(0f) }
    LaunchedEffect(book) { appear.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxWidth().height(150.dp).graphicsLayer { rotationX = (1f - appear.value) * 70f; alpha = appear.value; cameraDistance = 14f * density }
        .clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onBegin() }
        .border(3.dp, Color(0xFF1E1526), RoundedCornerShape(8.dp))) {
        Canvas(Modifier.matchParentSize()) {
            drawBookScene(book, phase)
            // The paperback's worn spine and a strip for the title.
            drawRect(Color(0x55000000), Offset.Zero, Size(size.width * 0.05f, size.height))
            drawRect(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)), Offset.Zero, Size(size.width, size.height * 0.55f))
        }
        Column(Modifier.padding(start = 24.dp, top = 12.dp, end = 12.dp)) {
            androidx.compose.material3.Text(say("Book ${book + 1}"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFE08A), letterSpacing = 3.sp)
            androidx.compose.material3.Text(say(BookNames[book]), fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif,
                color = Color(0xFFFFD166), lineHeight = 28.sp)
            androidx.compose.material3.Text(say("Tap to turn the page"), fontSize = 12.sp, fontStyle = FontStyle.Italic, color = Color(0xFFF3E9D2))
        }
    }
}

@Composable
private fun Title(text: String, color: Color) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(say(text), fontSize = 26.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, color = Color(0xFFFFD166),
            modifier = Modifier.graphicsLayer { rotationZ = -2f })
    }
}

@Composable
private fun HeroPicker(g: Wordsworn, phase: Float, unlocked: Set<Hero>, onTwist: (Int) -> Unit, onPick: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    var loadouts by remember { mutableStateOf(emptyMap<Hero, Int>()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("Choose your hero", Color(0xFF2E7D6B))
        Hero.entries.forEachIndexed { k, h ->
            val core = Item(h.core)
            val loadout = if (h in unlocked && g.currentRules) loadouts[h] ?: 0 else 0
            Surface(onClick = { onPick(k + 3 * loadout) }, color = Color(0xFFFFF8E6), shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, Color(0xFFB08A4E)),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(72.dp)) { drawHero(h, phase) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        androidx.compose.material3.Text("${h.label} · ${say(h.title)}", fontWeight = FontWeight.Black, fontSize = 17.sp, color = Brown)
                        androidx.compose.material3.Text(say(heroBlurb(h)), fontSize = 13.sp, color = Color(0xFF5A4630))
                        if (g.currentRules) {
                            Text("Health ${(if (g.level == LogicLevel.EASY) 25 else 20) + if (WordswornTwist.RESERVE in g.twists) 5 else 0}", fontSize = 12.sp, color = Brown)
                            h.cores(loadout).forEach { Text(say("${it.label}: ${it.text}"), fontSize = 12.sp, color = Color(0xFF7A6547)) }
                            if (h in unlocked) {
                                androidx.compose.material3.TextButton(onClick = { loadouts = loadouts + (h to (loadout xor 1)) }) { Text("Switch star core") }
                                androidx.compose.material3.TextButton(onClick = { loadouts = loadouts + (h to (loadout xor 2)) }) { Text("Switch hex core") }
                            } else Text("Win all three books with this hero to unlock alternate cores.", fontSize = 11.sp, color = Color(0xFF7A6547))
                        } else androidx.compose.material3.Text(say("Health ${h.maxHp} · ${h.core.label}: ${itemText(core)}"), fontSize = 12.sp, color = Color(0xFF7A6547))
                    }
                }
            }
        }
        Text(if (g.currentRules) "Each hero has their own deck, two core abilities and a starting item." else "Each hero has their own deck and core item.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        if (g.currentRules) {
            Text("Optional rule modifiers · score ${g.twistScore}", style = MaterialTheme.typography.titleMedium)
            Text("Negative scores ease the run; positive scores add challenges. Choose modifiers before tapping a hero.", style = MaterialTheme.typography.bodySmall)
            WordswornTwist.entries.forEachIndexed { i, twist ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = twist in g.twists, onCheckedChange = { onTwist(i) },
                        modifier = Modifier.semantics { contentDescription = twist.label })
                    Column {
                        Text("${twist.label} (${if (twist.difficulty > 0) "+" else ""}${twist.difficulty})")
                        Text(twist.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PathPicker(g: Wordsworn, paths: List<RoutePath>, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title("Choose your path", Color(0xFF4A2E6E))
        paths.forEachIndexed { k, p ->
            val tint = if (k == 0) Color(0xFF2A7F86) else Color(0xFFC98A1E)
            Surface(onClick = { onPick(k) }, color = if (k == 0) Color(0xFFE3F0EE) else Color(0xFFFFF1D0), shape = RoundedCornerShape(18.dp),
                border = BorderStroke(3.dp, tint), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    p.steps.forEach { s ->
                        androidx.compose.material3.Text(say(stepText(s)), fontSize = 16.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center,
                            color = when (s) { is Step.Hurt, is Step.Blots -> Color(0xFFA8232B); else -> tint.copy(red = tint.red * 0.7f, green = tint.green * 0.7f, blue = tint.blue * 0.7f) })
                    }
                    androidx.compose.material3.Text(say(p.story), fontSize = 13.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, color = Color(0xFF4A3B28),
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        StatusLine(g)
    }
}

@Composable
private fun StatusLine(g: Wordsworn) {
    val c = LocalGameLook.current.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Health ${g.hp}/${g.maxHp}", style = MaterialTheme.typography.bodyMedium, color = c.text)
        Counter(WsIcon.STAR, g.stars, say("Stars: ${g.stars}"))
        Text("${g.deck.size} cards · ${g.items.size} items", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

/** New cards to pick from, glowing on a ray burst. */
@Composable
private fun CardOffer(g: Wordsworn, cards: List<Card>, add: Boolean, onPick: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    val loop = rememberInfiniteTransition(label = "rays")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "spin")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Title("Pick a card", Color(0xFF2A7F86))
        Text(if (add) "It joins your deck." else "Then choose a card in your deck for it to replace.", style = MaterialTheme.typography.bodyMedium, color = c.text)
        Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF3A1F5C)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                rotate(spin, center) {
                    for (k in 0 until 16) {
                        val a = k * 2 * PI / 16
                        val p = Path(); p.moveTo(center.x, center.y)
                        p.lineTo(center.x + (cos(a - 0.08) * size.maxDimension).toFloat(), center.y + (sin(a - 0.08) * size.maxDimension).toFloat())
                        p.lineTo(center.x + (cos(a + 0.08) * size.maxDimension).toFloat(), center.y + (sin(a + 0.08) * size.maxDimension).toFloat()); p.close()
                        drawPath(p, Color(0xFF6FA8FF).copy(alpha = 0.18f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cards.forEachIndexed { k, card -> LetterCard(card, 88.dp, onTap = { onPick(k) }) }
            }
        }
        cards.forEach { Text(cardDescription(it), style = MaterialTheme.typography.bodySmall, color = c.muted) }
        if (!g.requiredChoice) SkipButton { onPick(-1) }
        StatusLine(g)
    }
}

@Composable
private fun SkipButton(label: String = "Skip", onTap: () -> Unit) {
    Surface(onClick = onTap, color = HurtRed, shape = RoundedCornerShape(12.dp)) {
        Text(label, Modifier.padding(horizontal = 22.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** The deck, to pick a card from (to replace or upgrade). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckPicker(g: Wordsworn, title: String, skip: String, mark: ((Card) -> Boolean)?, onPick: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            g.deck.forEachIndexed { k, card ->
                val off = card.blot || (mark?.invoke(card) ?: false)
                LetterCard(card, 60.dp, dim = off, onTap = if (off) null else ({ onPick(k) }))
            }
        }
        if (!g.requiredChoice) SkipButton(skip) { onPick(-1) }
    }
}

@Composable
private fun ItemOffer(kinds: List<ItemKind>, canSkip: Boolean = true, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Title("Pick an item", Color(0xFF3E6FA8))
        kinds.forEachIndexed { k, kind -> GoodsCard(say(kind.label), say(kind.text) + if (kind.single) " " + say("(once a fight)") else "", "${kind.cost}", WsIcon.INK) { onPick(k) } }
        if (canSkip) SkipButton { onPick(-1) }
    }
}

@Composable
private fun KeepsakeOffer(sides: List<Keepsake>, canSkip: Boolean = true, onPick: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Title("Pick a keepsake", Color(0xFFC98A1E))
        if (sides.none { it.boss }) Text("Each keepsake card has two sides: keep one.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        sides.chunked(if (sides.none { it.boss }) 2 else 1).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { k ->
                    Box(Modifier.weight(1f)) { GoodsCard(say(k.label), say(k.text), null, WsIcon.STAR) { onPick(sides.indexOf(k)) } }
                }
            }
        }
        if (canSkip) SkipButton { onPick(-1) }
    }
}

@Composable
private fun CoreUpgradeOffer(g: Wordsworn, onPick: (Int) -> Unit) {
    val core = g.items.firstOrNull { it.kind.core }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Title("Upgrade", Color(0xFF4A2E6E))
        if (core != null && !core.upgraded) GoodsCard(say("Upgrade ${core.kind.label}"), say(itemText(core.copy(upgraded = true))), null, WsIcon.INK) { onPick(0) }
        if (!g.wildUpgraded) GoodsCard(say("Upgrade the wild card"), say("Up to two wild letters every turn"), null, WsIcon.STAR) { onPick(1) }
        if ((core == null || core.upgraded) && g.wildUpgraded) SkipButton("Continue") { onPick(0) }
    }
}

/** A parchment card for an item, keepsake or shop good, with an optional price. */
@Composable
private fun GoodsCard(name: String, text: String, price: String?, icon: WsIcon, sold: Boolean = false, onTap: () -> Unit) {
    Surface(onClick = onTap, enabled = !sold, color = Color(0xFFFBF3DF), shape = RoundedCornerShape(12.dp), border = BorderStroke(2.dp, Color(0xFF2A7F86)),
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (sold) 0.4f else 1f }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                androidx.compose.material3.Text(name, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF8A2A1E))
                androidx.compose.material3.Text(text, fontSize = 13.sp, color = Color(0xFF4A3B28))
            }
            if (price != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(16.dp)) { drawWsIcon(icon, center, size.minDimension * 0.45f) }
                androidx.compose.material3.Text(price, fontWeight = FontWeight.Black, fontSize = 16.sp, color = Brown, modifier = Modifier.padding(start = 3.dp))
            }
        }
    }
}

/** The shop: goods on two shelves, each with a hanging price sign in stars. */
@Composable
private fun ShopView(g: Wordsworn, shop: Offer.Shop, onRefresh: (Int) -> Unit, readOnly: Boolean = false, onBuy: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Title("The shop", Color(0xFF2E4A5A))
        StatusLine(g)
        if (g.currentRules) Text("Bought cards refill immediately. Letters replace a card in your deck. Stars also power your core abilities.", style = MaterialTheme.typography.bodySmall)
        if (readOnly) Text("You can buy these after completing a battle and its rewards.", style = MaterialTheme.typography.bodySmall)
        shop.goods.forEachIndexed { k, goods ->
            if (goods == Goods.Empty) return@forEachIndexed
            val (name, text) = when (goods) {
                is Goods.ForItem -> say(goods.kind.label) to say(goods.kind.text)
                is Goods.ForKeepsake -> say("Keepsake: ${goods.sides.first.label} or ${goods.sides.second.label}") to say("${goods.sides.first.text} / ${goods.sides.second.text}")
                Goods.ForUpgrade -> say("Upgrade a card") to say("+1 hit, +1 block and a stronger ability")
                Goods.ForRest -> say("A warm meal") to say("Heal 5")
                is Goods.ForLetter -> "Letter ${goods.card.letter}" to cardDescription(goods.card)
                Goods.Empty -> "" to ""
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (g.currentRules && k < 8) Text(when (k) {
                    0, 1, 2 -> "Items · ${listOf("left", "middle", "right")[k]}"
                    3, 4 -> "Keepsakes · ${if (k == 3) "left" else "right"}"
                    else -> "Letters · ${listOf("left", "middle", "right")[k - 5]}"
                }, style = MaterialTheme.typography.bodySmall)
                GoodsCard(name, text, "${goods.price}", WsIcon.STAR, sold = readOnly || k in shop.sold || g.stars < goods.price) { onBuy(k) }
                // The shelf under it, and the price sign on two little chains.
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    drawRect(Color(0xFF5A4630), Offset(0f, size.height * 0.2f), Size(size.width, size.height * 0.5f))
                    drawRect(Color(0x33000000), Offset(0f, size.height * 0.7f), Size(size.width, size.height * 0.3f))
                }
            }
        }
        if (g.currentRules && !readOnly) {
            Text(if (shop.refreshed) "Free refresh used for this visit." else "Refresh one row or column for free:", style = MaterialTheme.typography.bodySmall)
            if (!shop.refreshed) listOf("Items", "Keepsakes", "Letters", "Left column", "Middle column", "Right column").forEachIndexed { group, label ->
                SkipButton("Refresh $label") { onRefresh(group) }
            }
        }
        if (!readOnly) SkipButton("Leave the shop") { onBuy(-1) }
    }
}

@Composable
private fun RunInspector(g: Wordsworn, onClose: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    androidx.compose.material3.AlertDialog(onDismissRequest = onClose,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Back to game") } },
        title = { Row {
            listOf("Deck", "Enemies", "Shop").forEachIndexed { i, label ->
                androidx.compose.material3.TextButton(onClick = { page = i }) { Text(if (page == i) "• $label" else label) }
            }
        } },
        text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (page) {
                0 -> {
                    Text("${g.deck.size} cards · ${g.library.size} in the library", fontWeight = FontWeight.Bold)
                    g.deck.forEachIndexed { i, card ->
                        val location = when (i) { in g.hand -> "In hand"; in g.worn -> "Worn out"; in g.discard -> "Discarded"; else -> "Draw pile" }
                        Text("$location · ${cardDescription(card)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                1 -> {
                    val boss = Wordsworn.schedule(g.level, g.seed, g.rulesVersion)[g.book * g.fightsPerBook + g.fightsPerBook - 1]
                    listOf(g.monster, boss).distinct().forEach { monster ->
                        Text(monster.label + if (monster.boss) " · book boss" else "", fontWeight = FontWeight.Bold)
                        if (monster.special != Special.NONE) Text(monster.special.text)
                        for (stage in 1..2) {
                            val health = (monster.hp(stage) * Wordsworn.factorOf(g.level)).toInt().coerceAtLeast(1)
                            Text("Stage $stage · $health health")
                            Text(monster.acts(stage).joinToString(" → ") { it.scaled(Wordsworn.factorOf(g.level)).describe() }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("These action values are before stars, hex effects and optional modifiers.", style = MaterialTheme.typography.bodySmall)
                }
                2 -> g.shop?.let { ShopView(g, it, onRefresh = {}, readOnly = true, onBuy = {}) }
            }
        } })
}
