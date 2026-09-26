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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
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
import com.simplegamegen.sudoku.logic.LogicLevel
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.HintButton
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.ToolButton
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.wordplay.IntentKind
import com.simplegamegen.sudoku.wordplay.Reward
import com.simplegamegen.sudoku.wordplay.Tile
import com.simplegamegen.sudoku.wordplay.TileKind
import com.simplegamegen.sudoku.wordplay.Wordsworn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val WordswornSetup: (PuzzleFactory) -> PlaySetup<Wordsworn> = { factory ->
    PlaySetup(
        rules = "Battle a run of monsters with words. Tap tiles from your hand to spell a word, then Attack: the tiles' power is " +
            "added up, and long words get a bonus. Rare letters hit harder. After your word the monster does what its sign shows: " +
            "hit you, guard itself, or drain your health. Shield tiles block, heal tiles mend, a double tile doubles the damage and a " +
            "wild tile can be any letter. Swap trades up to three tiles but uses your turn. After each win, choose a reward. Beat " +
            "the Word Eater at the end to finish the run.",
        settingTitle = "Difficulty", settings = LevelNames,
        describe = { level -> LogicLevel.entries[level].let { "${Wordsworn.fightsOf(it)} fights · start with ${Wordsworn.startHp(it)} health" } },
        settingOf = { it.level.ordinal }, inProgress = { !it.over },
        subtitle = { "${it.level.label} · fight ${it.fight + 1} of ${it.fights}" },
        create = { level -> { factory.custom("wordsworn:$level", { seed -> Wordsworn.start(seed, LogicLevel.entries[level]) }) { it.seed.toString() } } },
        identity = { it.seed.toString() },
        outcome = { g -> when { g.won -> Outcome(Result.WON, g.hp.toLong()); g.lost -> Outcome(Result.LOST, g.fight.toLong()); else -> null } },
    )
}

private val Paper = Color(0xFFFFF6E0)
private const val BLOOD_KEY = "wordsworn:blood"
private val HurtRed = Color(0xFFE5484D)
private val HealGreen = Color(0xFF43A047)
private val ShieldBlue = Color(0xFF4F8EF7)
private val Gold = Color(0xFFFFC93C)

private fun kindColor(kind: TileKind): Color = when (kind) {
    TileKind.PLAIN -> Color.Transparent
    TileKind.SHIELD -> ShieldBlue
    TileKind.HEAL -> HealGreen
    TileKind.DOUBLE -> Gold
    TileKind.WILD -> Color(0xFFB266FF)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordswornScreen(nav: NavController, vm: PlayViewModel<Wordsworn>, factory: PuzzleFactory, store: ArcadeStore) {
    val c = LocalGameLook.current.colors
    val scope = rememberCoroutineScope()
    var picks by remember { mutableStateOf(listOf<Int>()) }
    var wild by remember { mutableStateOf("") }
    var askWild by remember { mutableStateOf(false) }
    var swapping by remember { mutableStateOf(false) }
    var hintPicks by remember { mutableStateOf(listOf<Int>()) }
    var thinking by remember { mutableStateOf(false) }

    // Effects, driven by what happened last.
    val shot = remember { Animatable(0f) }        // letters flying to the monster
    val impact = remember { Animatable(0f) }      // burst and damage number
    val hurt = remember { Animatable(0f) }        // monster flash
    val lunge = remember { Animatable(0f) }       // monster attacking
    val ouch = remember { Animatable(0f) }        // player hit
    val glow = remember { Animatable(0f) }        // shield, heal or guard glow
    val poof = remember { Animatable(0f) }        // monster defeated
    var flying by remember { mutableStateOf("") }
    val bite = remember { Animatable(0f) }        // jaws snapping at the player
    // Blood can be swapped for ink splats; the choice is remembered.
    var blood by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { blood = store.load(BLOOD_KEY) != "0" }
    // Splats left on the dungeon floor: where, which shape, and when (so older ones fade).
    var splats by remember { mutableStateOf(listOf<Triple<Float, Int, Int>>()) }

    PlayShell(nav, vm, GameId.WORDSWORN, remember(factory) { WordswornSetup(factory) }, undoable = false, tools = { g, s ->
        HintButton(GameId.WORDSWORN, enabled = !g.over && !g.choosing && !s.busy && !thinking) {
            thinking = true
            scope.launch {
                val best = withContext(Dispatchers.Default) { g.bestWord() }
                thinking = false
                if (best == null) { vm.say("No word fits. Try Swap."); return@launch }
                val (word, p, _) = best
                hintPicks = p
                vm.play("Try ${word} for ${g.preview(p).damage} damage.") { it.hinted() }
            }
        }
        ToolButton(GameIcons.Restart, if (swapping) "Cancel swap" else "Swap (${g.swaps})", enabled = !g.over && !g.choosing && !s.busy && (g.swaps > 0 || swapping)) {
            swapping = !swapping; picks = emptyList(); wild = ""
        }
    }) { g, s ->
        // Play the turn's effects whenever a new blow lands.
        LaunchedEffect(g.last, g.fight) {
            val b = g.last ?: return@LaunchedEffect
            if (b.word.isNotEmpty()) {
                flying = b.word
                shot.snapTo(0f); shot.animateTo(1f, tween(380, easing = FastOutSlowInEasing)); flying = ""
                launch { hurt.snapTo(1f); hurt.animateTo(0f, tween(500)) }
                impact.snapTo(0f); launch { impact.animateTo(1f, tween(900)) }
                if (b.damage > 0) splats = (splats + Triple(0.25f + 0.5f * Random(g.turn * 31 + g.fight).nextFloat(), g.turn * 7 + g.fight, g.turn)).takeLast(6)
                if (b.blocked > 0 || b.healed > 0) launch { glow.snapTo(1f); glow.animateTo(0f, tween(900)) }
                delay(420)
            }
            if (g.choosing || g.won) { poof.snapTo(0f); poof.animateTo(1f, tween(900)); return@LaunchedEffect }
            if (b.taken > 0) {
                launch { bite.snapTo(0f); bite.animateTo(1f, tween(560, easing = LinearEasing)) }
                lunge.snapTo(0f); lunge.animateTo(1f, tween(180)); launch { lunge.animateTo(0f, spring(dampingRatio = 0.4f)) }
                ouch.snapTo(1f); ouch.animateTo(0f, tween(600))
            } else if (b.enemyGuard > 0) { glow.snapTo(1f); glow.animateTo(0f, tween(900)) }
        }
        LaunchedEffect(g.fight) { poof.snapTo(0f); splats = emptyList() }
        val splatColor = if (blood) Blood else InkSplat
        LaunchedEffect(g.hand, g.fight) { picks = emptyList(); wild = ""; hintPicks = emptyList() }

        val loop = rememberInfiniteTransition(label = "idle")
        val phase by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase")

        Box(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- The monster's stage: a torch-lit dungeon ----
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
            Canvas(Modifier.matchParentSize()) {
                drawDungeon(phase)
                // Splats on the floor fade over a few turns.
                for ((x, seed, born) in splats) {
                    val age = (g.turn - born).coerceAtLeast(0)
                    drawSplat(Offset(size.width * x, size.height * 0.9f), size.minDimension * 0.05f, seed, splatColor, (1f - age / 4f).coerceIn(0f, 1f))
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text(say(g.monster.label), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFF3E9D2), modifier = Modifier.weight(1f))
                    Surface(onClick = { blood = !blood; scope.launch { store.save(BLOOD_KEY, if (blood) "1" else "0") } }, color = Color(0x33FFFFFF),
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.semantics { contentDescription = say(if (blood) "Splatter: blood. Tap for ink." else "Splatter: ink. Tap for blood.") }) {
                        Canvas(Modifier.padding(6.dp).size(14.dp)) { drawCircle(if (blood) Blood else InkSplat, size.minDimension / 2) }
                    }
                    Box(Modifier.width(6.dp))
                    IntentSign(g)
                }
                Bar(g.monsterHp, g.monsterMax, HurtRed, if (g.monsterGuard > 0) "${g.monsterHp}/${g.monsterMax} · 🛡 ${g.monsterGuard}" else "${g.monsterHp}/${g.monsterMax}")
                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    val shake = sin(hurt.value * 30).toFloat() * 10f * hurt.value
                    if (poof.value < 1f) Canvas(Modifier.size(170.dp).graphicsLayer {
                        translationX = shake * density
                        translationY = lunge.value * 26f * density
                        val grow = 1f + lunge.value * 0.12f - poof.value * 0.6f
                        scaleX = grow; scaleY = grow
                        alpha = 1f - poof.value
                    }) {
                        drawMonster(g.monster, Rect(Offset.Zero, size), hurt.value, phase)
                        // Guard glow.
                        if (g.monsterGuard > 0 || (glow.value > 0f && g.last?.enemyGuard ?: 0 > 0)) {
                            drawCircle(ShieldBlue.copy(alpha = 0.18f + 0.3f * glow.value), size.minDimension * 0.46f, center, style = Stroke(size.minDimension * 0.03f))
                        }
                    }
                    // Impact: starburst, paper scraps and the damage number.
                    val hit = g.last?.damage ?: 0
                    if (impact.value in 0.001f..0.999f && (g.last?.word ?: "").isNotEmpty()) Canvas(Modifier.size(200.dp)) {
                        val t = impact.value
                        val r = size.minDimension * (0.12f + 0.3f * t)
                        for (k in 0 until 10) {
                            val a = 2 * PI * k / 10 + 0.3
                            val start = Offset(center.x + (cos(a) * r * 0.5f).toFloat(), center.y + (sin(a) * r * 0.5f).toFloat())
                            val end = Offset(center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat())
                            drawLine(Gold.copy(alpha = 1f - t), start, end, size.minDimension * 0.02f, cap = StrokeCap.Round)
                        }
                        if (hit > 0) drawDroplets(center, t, hit * 13 + g.turn, splatColor, size.minDimension * 0.6f)
                        val rnd = Random(hit * 7 + g.turn)
                        repeat(14) {
                            val a = rnd.nextDouble(0.0, 2 * PI)
                            val d = size.minDimension * (0.1f + 0.4f * t) * (0.6f + 0.4f * rnd.nextFloat())
                            val at = Offset(center.x + (cos(a) * d).toFloat(), center.y + (sin(a) * d).toFloat() + t * t * size.minDimension * 0.2f)
                            rotate(t * 360f * (if (it % 2 == 0) 1 else -1), at) {
                                drawRect(Color.White.copy(alpha = 1f - t), Offset(at.x - 5f, at.y - 3f), Size(10f, 6f))
                                drawRect(Color(0xFFD9CBB0).copy(alpha = 1f - t), Offset(at.x - 5f, at.y - 3f), Size(10f, 6f), style = Stroke(1.5f))
                            }
                        }
                    }
                    if (impact.value in 0.001f..0.999f && (g.last?.word ?: "").isNotEmpty()) {
                        androidx.compose.material3.Text(if (hit > 0) "−$hit" else say("Blocked"), fontSize = 30.sp, fontWeight = FontWeight.Black, color = HurtRed,
                            modifier = Modifier.graphicsLayer { translationY = -impact.value * 60f * density; alpha = 1f - impact.value; scaleX = 1f + (1f - impact.value) * 0.4f; scaleY = scaleX })
                    }
                    // Defeat: the monster bursts into paper scraps.
                    if (poof.value in 0.001f..0.999f) Canvas(Modifier.size(200.dp)) {
                        val t = poof.value
                        val rnd = Random(g.fight + 11)
                        repeat(24) {
                            val a = rnd.nextDouble(0.0, 2 * PI)
                            val d = size.minDimension * 0.5f * t * (0.4f + 0.6f * rnd.nextFloat())
                            val at = Offset(center.x + (cos(a) * d).toFloat(), center.y + (sin(a) * d).toFloat() + t * t * 40f)
                            rotate(t * 720f, at) { drawRect(listOf(Color.White, Gold, Color(0xFFD9CBB0))[it % 3].copy(alpha = 1f - t), Offset(at.x - 6f, at.y - 4f), Size(12f, 8f)) }
                        }
                    }
                    // Letters flying up from the tray.
                    if (flying.isNotEmpty()) Row(Modifier.graphicsLayer { translationY = (1f - shot.value) * 150f * density; alpha = 1f - shot.value * 0.6f
                        scaleX = 1f - shot.value * 0.5f; scaleY = scaleX }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        flying.forEach { ch -> SmallTile(ch.toString()) }
                    }
                }
            }
        }

        // ---- The player ----
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().graphicsLayer { translationX = sin(ouch.value * 28).toFloat() * 8f * ouch.value * density },
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You", style = MaterialTheme.typography.titleSmall, color = c.text)
                    g.last?.let { b ->
                        if (b.taken > 0) InfoChip("−${b.taken}")
                        if (b.healed > 0) InfoChip("+${b.healed}")
                        if (b.blocked > 0) InfoChip("🛡 ${b.blocked}")
                    }
                }
                Bar(g.hp, g.maxHp, HealGreen, "${g.hp}/${g.maxHp}")
            }
            // Shield bubble and heal sparkles around the health bar.
            if (glow.value > 0f && ((g.last?.blocked ?: 0) > 0 || (g.last?.healed ?: 0) > 0)) Canvas(Modifier.matchParentSize()) {
                val b = g.last!!
                if (b.blocked > 0) drawRoundRect(ShieldBlue.copy(alpha = 0.5f * glow.value), Offset.Zero, size, androidx.compose.ui.geometry.CornerRadius(20f), style = Stroke(6f))
                if (b.healed > 0) repeat(8) { k ->
                    val x = size.width * (0.1f + 0.8f * k / 7f)
                    val y = size.height * (1f - (1f - glow.value)) - k % 3 * 6f
                    drawCircle(HealGreen.copy(alpha = glow.value), 4f + k % 2 * 2f, Offset(x, y))
                }
            }
        }
        }
        // The monster's bite: jaws snap shut over the scene and leave splatter behind.
        if (bite.value in 0.001f..0.999f || ouch.value > 0f) Canvas(Modifier.matchParentSize()) {
            if (bite.value in 0.001f..0.999f) drawBite(bite.value, Color(0xFF7A1F2B))
            if (ouch.value > 0f) for (k in 0..2) drawSplat(Offset(size.width * (0.25f + 0.25f * k), size.height * (0.72f + 0.06f * (k % 2))),
                size.minDimension * 0.04f, g.turn * 5 + k, splatColor, ouch.value)
        }
        }
        // Red edges when the monster lands a hit.
        if (ouch.value > 0f) Box(Modifier.fillMaxWidth().height(6.dp).background(HurtRed.copy(alpha = ouch.value), RoundedCornerShape(3.dp)))

        when {
            g.won -> WinBanner("Run complete! The Word Eater is beaten.")
            g.lost -> Surface(color = c.danger.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Text("Defeated in fight ${g.fight + 1}. Try another run!", Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, color = c.text)
            }
            g.choosing -> RewardPicker(g) { i -> vm.play("On to fight ${g.fight + 2}!") { it.choose(i) } }
            else -> {
                // ---- The word tray ----
                val word = if (picks.isEmpty()) "" else g.spell(picks, wild.padEnd(picks.count { g.deck[g.hand[it]].kind == TileKind.WILD }, '?')) ?: ""
                val preview = if (picks.size >= 2 && !swapping) g.preview(picks) else null
                Surface(color = c.surfaceAlt, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Text(if (swapping) say("Pick up to 3 tiles to swap") else word.ifEmpty { say("Tap tiles to spell a word") },
                                fontSize = if (word.isEmpty() || swapping) 15.sp else 24.sp, fontWeight = FontWeight.Bold,
                                color = if (word.isEmpty() || swapping) c.muted else c.text, modifier = Modifier.weight(1f))
                            if (picks.isNotEmpty()) Surface(onClick = { picks = emptyList(); wild = "" }, color = c.surface, shape = RoundedCornerShape(10.dp)) {
                                Text("Clear", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = c.text)
                            }
                        }
                        preview?.let { p ->
                            Text(buildList {
                                add(say("Deals ${p.damage}"))
                                if (p.blocked > 0) add(say("blocks ${p.blocked}"))
                                if (p.healed > 0) add(say("heals ${p.healed}"))
                            }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = c.accent)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (swapping) Surface(onClick = {
                                g.swap(picks)?.let { next -> swapping = false; vm.play("Swapped ${picks.size} tiles.") { next } }
                            }, enabled = picks.isNotEmpty() && !s.busy, color = c.accent, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text("Swap these", Modifier.padding(12.dp), color = c.onAccent, fontWeight = FontWeight.Bold)
                            } else Surface(onClick = {
                                val need = picks.count { g.deck[g.hand[it]].kind == TileKind.WILD }
                                if (need > wild.length) { askWild = true; return@Surface }
                                val problem = g.problem(picks, wild)
                                if (problem != null) { vm.say(problem); return@Surface }
                                val next = g.play(picks, wild) ?: return@Surface
                                val w = g.spell(picks, wild)!!
                                vm.play(if (next.choosing) "${w}! ${g.monster.label} is beaten." else if (next.won) "${w}! You won the run!" else null) { next }
                            }, enabled = picks.size >= 2 && !s.busy, color = HurtRed, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                                Text("Attack", Modifier.padding(12.dp), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                if (askWild) {
                    Text("Wild tile: pick its letter", style = MaterialTheme.typography.bodyMedium, color = c.text)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ('A'..'Z').forEach { ch ->
                            Surface(onClick = { wild += ch; askWild = false }, color = c.surface, shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(38.dp).semantics { contentDescription = say("Letter $ch") }) {
                                Box(contentAlignment = Alignment.Center) { androidx.compose.material3.Text(ch.toString(), fontWeight = FontWeight.Bold, color = c.text) }
                            }
                        }
                    }
                }
                // ---- The hand ----
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val side = min((maxWidth - 6.dp * 7) / 8, 56.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        g.hand.forEachIndexed { i, idx ->
                            val tile = g.deck[idx]
                            HandTile(tile, side, picked = i in picks, hinted = i in hintPicks, key = "${g.fight}:${g.turn}:$idx") {
                                picks = when {
                                    i in picks -> { val at = picks.indexOf(i); if (tile.kind == TileKind.WILD) wild = ""; picks.subList(0, at) }
                                    swapping && picks.size >= 3 -> picks
                                    else -> picks + i
                                }
                            }
                        }
                    }
                }
                Text("Draw pile ${g.draw.size} · used ${g.discard.size}", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
    }
}

/** What the monster will do next, as a sign. */
@Composable
private fun IntentSign(g: Wordsworn) {
    val i = g.intent
    val (label, color) = when (i.kind) {
        IntentKind.HIT -> "Hits ${i.amount}" to HurtRed
        IntentKind.GUARD -> "Guards ${i.amount}" to ShieldBlue
        IntentKind.DRAIN -> "Drains ${i.amount}" to HealGreen
    }
    if (g.over || g.choosing) return
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, color)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(16.dp)) {
                when (i.kind) {
                    IntentKind.HIT -> { // a little sword
                        drawLine(color, Offset(size.width * 0.2f, size.height * 0.8f), Offset(size.width * 0.85f, size.height * 0.15f), size.width * 0.14f, cap = StrokeCap.Round)
                        drawLine(color, Offset(size.width * 0.15f, size.height * 0.55f), Offset(size.width * 0.45f, size.height * 0.85f), size.width * 0.12f, cap = StrokeCap.Round)
                    }
                    IntentKind.GUARD -> drawCircle(color, size.minDimension * 0.4f, center, style = Stroke(size.width * 0.14f))
                    IntentKind.DRAIN -> drawCircle(color, size.minDimension * 0.3f, center)
                }
            }
            Box(Modifier.width(6.dp))
            androidx.compose.material3.Text(say(label), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Bar(value: Int, max: Int, color: Color, label: String) {
    val shown = remember { Animatable(value.toFloat()) }
    LaunchedEffect(value) { shown.animateTo(value.toFloat(), tween(450)) }
    Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x22000000))) {
        Box(Modifier.fillMaxWidth((shown.value / max.coerceAtLeast(1)).coerceIn(0f, 1f)).height(20.dp)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color))))
        androidx.compose.material3.Text(label, Modifier.align(Alignment.Center), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1A14))
    }
}

@Composable
private fun SmallTile(letter: String) {
    Box(Modifier.size(26.dp).background(Paper, RoundedCornerShape(5.dp)).border(1.dp, Color(0xFFB8A57E), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(letter, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF2A2418))
    }
}

/** A letter tile in the hand: it slides in when drawn, lifts when picked, and glows when hinted. */
@Composable
private fun HandTile(tile: Tile, side: androidx.compose.ui.unit.Dp, picked: Boolean, hinted: Boolean, key: String, onTap: () -> Unit) {
    val arrive = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { arrive.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(picked) { lift.animateTo(if (picked) 1f else 0f, spring(dampingRatio = 0.5f)) }
    val edge = kindColor(tile.kind)
    Box(Modifier.size(side, side * 1.2f)
        .graphicsLayer { translationY = ((1f - arrive.value) * 60f - lift.value * 12f) * density; alpha = arrive.value.coerceIn(0f, 1f) }
        .background(if (picked) Gold.copy(alpha = 0.55f) else if (hinted) Gold.copy(alpha = 0.3f) else Paper, RoundedCornerShape(8.dp))
        .border(if (tile.kind == TileKind.PLAIN) 1.dp else 2.5.dp, if (tile.kind == TileKind.PLAIN) Color(0xFFB8A57E) else edge, RoundedCornerShape(8.dp))
        .clickable(role = Role.Button) { onTap() }
        .semantics { contentDescription = say(if (tile.kind == TileKind.WILD) "Wild tile" else "${tile.letter}, power ${tile.power}" + if (tile.kind != TileKind.PLAIN) ", ${tile.kind.label}" else "") },
        contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(if (tile.kind == TileKind.WILD) "★" else tile.letter.toString(), fontSize = (side.value * 0.5f).sp,
            fontWeight = FontWeight.Black, color = Color(0xFF2A2418))
        androidx.compose.material3.Text(tile.power.toString(), Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp), fontSize = (side.value * 0.2f).sp,
            fontWeight = FontWeight.Bold, color = Color(0xFF7A6A4A))
    }
}

/** Three reward cards that flip in after a win. */
@Composable
private fun RewardPicker(g: Wordsworn, onPick: (Int) -> Unit) {
    val c = LocalGameLook.current.colors
    Text("${say(g.monster.label)} is beaten! Choose a reward:", style = MaterialTheme.typography.titleMedium, color = c.text)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        g.rewards.forEachIndexed { i, r ->
            val flip = remember(g.fight, i) { Animatable(0f) }
            LaunchedEffect(g.fight, i) { delay(i * 150L); flip.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
            val (title, detail, color) = when (r) {
                is Reward.Upgrade -> Triple(say("Sharpen ${r.letter}"), say("Every ${r.letter} tile gets +${r.by} power"), Gold)
                is Reward.NewTile -> Triple(say("New ${r.tile.kind.label.lowercase()} tile"), if (r.tile.kind == TileKind.WILD) say("A wild tile: any letter") else say("${r.tile.letter}, power ${r.tile.power}"), kindColor(r.tile.kind))
                is Reward.Rest -> Triple(say("Rest"), say("Heal ${r.amount} health"), HealGreen)
            }
            Surface(onClick = { onPick(i) }, color = Paper, shape = RoundedCornerShape(14.dp), border = BorderStroke(2.dp, color),
                modifier = Modifier.weight(1f).graphicsLayer { rotationY = (1f - flip.value) * 90f; alpha = flip.value; cameraDistance = 12f * density }) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2A2418))
                    androidx.compose.material3.Text(detail, fontSize = 12.sp, color = Color(0xFF6B5B45))
                }
            }
        }
    }
}
