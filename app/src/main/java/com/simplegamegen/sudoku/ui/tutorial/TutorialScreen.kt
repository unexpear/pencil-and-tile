package com.simplegamegen.sudoku.ui.tutorial

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.simplegamegen.sudoku.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.tabletop.SolitaireState
import com.simplegamegen.sudoku.ui.GameGroup
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.LocalPlayer
import com.simplegamegen.sudoku.ui.assets.CardSlot
import com.simplegamegen.sudoku.ui.assets.CheckerPiece
import com.simplegamegen.sudoku.ui.assets.DominoTile
import com.simplegamegen.sudoku.ui.assets.GameArt
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.MahjongTile
import com.simplegamegen.sudoku.ui.assets.PlayingCard
import com.simplegamegen.sudoku.ui.assets.drawCardBack
import com.simplegamegen.sudoku.ui.assets.drawDisc
import com.simplegamegen.sudoku.ui.assets.drawFlag
import com.simplegamegen.sudoku.ui.assets.drawMine
import com.simplegamegen.sudoku.ui.assets.mahjongName
import com.simplegamegen.sudoku.ui.assets.mineNumberColor
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.SectionTitle
import com.simplegamegen.sudoku.ui.components.SegmentedTabs
import com.simplegamegen.sudoku.ui.theme.GamePalette
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import com.simplegamegen.sudoku.ui.i18n.say

/** Route for a game's tutorial; [hub] means it was opened from the How to play list. */
fun tutorialRoute(id: GameId, hub: Boolean = false) = "tutorial/${id.name}?hub=$hub"

/**
 * A game's optional tutorial: guided play through its own small scenes, plus its rules.
 * Nothing here is required; the back arrow or "Skip to the game" leaves at any time.
 */
@Composable
fun TutorialScreen(nav: NavController, id: GameId, fromHub: Boolean) {
    val tutorial = remember(id) { Tutorials.of(id) }
    val c = LocalGameLook.current.colors
    val player = LocalPlayer.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var done by rememberSaveable { mutableStateOf(false) }
    var showMe by rememberSaveable { mutableStateOf(false) }
    var wrong by rememberSaveable { mutableStateOf<String?>(null) }
    var finished by rememberSaveable { mutableStateOf(false) }
    fun play() {
        if (fromHub) nav.navigate(id.route) { popUpTo(tutorialRoute(id, true)) { inclusive = true } } else nav.popBackStack()
    }
    fun goTo(i: Int) { index = i; done = false; showMe = false; wrong = null; finished = false }
    val step = tutorial.steps[index]
    // Short confirmations move on by themselves; longer explanations wait for Next.
    LaunchedEffect(index, done) {
        if (done && step.then.length <= 60 && index < tutorial.steps.size - 1) { delay(1200); goTo(index + 1) }
    }
    GameScaffold(title = "How to play", subtitle = id.title, game = id, onBack = { nav.popBackStack() }) {
        SegmentedTabs(listOf("Guided play", "Rules"), tab) { tab = it }
        if (tab == 1) {
            RulesList(tutorial)
            OutlinedButton(onClick = { tab = 0 }, modifier = Modifier.fillMaxWidth()) {
                Icon(GameIcons.School, null, Modifier.size(18.dp)); Text("  Try the guided tutorial")
            }
            Button(onClick = ::play, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (fromHub) "Play ${id.title}" else "Back to the game") }
        } else if (finished) {
            Surface(color = c.success.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GameArt(id, Modifier.size(72.dp))
                    Text("You're ready to play ${id.title}!", style = MaterialTheme.typography.titleLarge, color = c.success, textAlign = TextAlign.Center)
                    Text(tutorial.summary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
            Button(onClick = ::play, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Play now") }
            OutlinedButton(onClick = { goTo(0) }, modifier = Modifier.fillMaxWidth()) { Text("Replay the tutorial") }
            OutlinedButton(onClick = { tab = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Read the rules") }
        } else {
            GuidedStep(tutorial, index, step, done, showMe, wrong,
                onTap = { tapped -> if (tapped in step.tap) { done = true; wrong = null } else { wrong = step.help.ifEmpty { MISS }; showMe = true } },
                onPick = { choice -> if (choice == step.pick) { done = true; wrong = null } else { wrong = step.help.ifEmpty { MISS }; showMe = true } },
                onShowMe = { showMe = true },
                onBack = { goTo(index - 1) },
                onNext = {
                    if (index == tutorial.steps.size - 1) { finished = true; player?.update { r, _ -> r.rewardTutorial(id.name) } } else goTo(index + 1)
                },
                onSkip = ::play, skipLabel = if (fromHub) "Skip to the game" else "Skip the tutorial")
        }
    }
}

private const val MISS = "Not quite. The highlighted spot shows where to go."

@Composable
private fun ColumnScope.GuidedStep(
    tutorial: Tutorial, index: Int, step: Step, done: Boolean, showMe: Boolean, wrong: String?,
    onTap: (String) -> Unit, onPick: (String) -> Unit, onShowMe: () -> Unit, onBack: () -> Unit, onNext: () -> Unit,
    onSkip: () -> Unit, skipLabel: String,
) {
    val c = LocalGameLook.current.colors
    val total = tutorial.steps.size
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Step ${index + 1} of $total", style = MaterialTheme.typography.labelLarge, color = c.muted)
        LinearProgressIndicator(progress = { (index + if (done || !step.interactive) 1 else 0) / total.toFloat() },
            modifier = Modifier.weight(1f), color = c.accent, trackColor = c.surfaceAlt)
    }
    Surface(color = if (done && step.then.isNotEmpty()) c.success.copy(alpha = 0.12f) else c.surface, shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (done && step.then.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(GameIcons.Check, null, tint = c.success, modifier = Modifier.size(20.dp))
                    Text(step.then, style = MaterialTheme.typography.bodyLarge, color = c.text)
                }
            } else Text(step.say, style = MaterialTheme.typography.bodyLarge, color = c.text)
            wrong?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger) }
        }
    }
    val scene = if (done) step.result else step.scene
    SceneView(scene, highlight = if (showMe && !done) step.tap else emptySet(), enabled = step.tap.isNotEmpty() && !done, onTap = onTap)
    if (scene.choices.isNotEmpty()) ChoiceRow(scene.choices, enabled = step.pick != null && !done, hinted = if (showMe && !done) step.pick else null, onPick = onPick)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBack, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("Back") }
        if (step.interactive && !done) OutlinedButton(onClick = onShowMe, modifier = Modifier.weight(1f)) { Text("Show me") }
        Button(onClick = onNext, enabled = !step.interactive || done,
            modifier = Modifier.weight(1f)) { Text(if (index == total - 1) "Finish" else "Next") }
    }
    TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(skipLabel) }
}

@Composable
private fun RulesList(t: Tutorial) {
    val c = LocalGameLook.current.colors
    Text(t.summary, style = MaterialTheme.typography.titleMedium)
    SectionTitle("Rules")
    t.rules.forEachIndexed { i, rule ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(24.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.accent)
            }
            Text(rule, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
    if (t.tips.isNotEmpty()) {
        SectionTitle("Tips")
        t.tips.forEach { tip ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(GameIcons.Hint, null, tint = c.highlight, modifier = Modifier.size(20.dp))
                Text(tip, style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(choices: List<String>, enabled: Boolean, hinted: String?, onPick: (String) -> Unit) {
    val c = LocalGameLook.current.colors
    val pulse = pulse()
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { label ->
            Surface(onClick = { onPick(label) }, enabled = enabled, shape = MaterialTheme.shapes.medium, color = c.surface,
                border = BorderStroke(if (label == hinted) 3.dp else 1.dp, if (label == hinted) c.highlight.copy(alpha = pulse) else c.outline),
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = say(label) }) {
                Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (enabled) c.text else c.muted)
                }
            }
        }
    }
}

@Composable
private fun pulse(): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "alpha")
    return a
}

/** Paper and ink shared by the printed-style squares. */
private class Ink(val paper: Color, val ink: Color, val line: Color, val block: Color)

@Composable
private fun ink(): Ink {
    val look = LocalGameLook.current
    val c = look.colors
    return if (look.dark) Ink(c.surfaceAlt, c.text.copy(alpha = 0.92f), c.outline, Color(0xFF0B0B0D))
    else Ink(Color(0xFFFFFEFA), Color(0xFF2A2A2E), Color(0xFFC9C4B8), Color(0xFF2A2A2E))
}

/** Draws a [scene] scaled to the width available; taps on items report their ids. */
@Composable
fun SceneView(scene: Scene, highlight: Set<String> = emptySet(), enabled: Boolean = false, onTap: (String) -> Unit = {}) {
    val look = LocalGameLook.current
    val c = look.colors
    val panel = when (scene.surface) { Backdrop.TABLE -> c.table; Backdrop.BOARD -> c.surfaceAlt; Backdrop.PAPER -> c.surface }
    val alpha = pulse()
    Box(Modifier.fillMaxWidth().background(panel, MaterialTheme.shapes.large)
        .border(1.dp, if (scene.surface == Backdrop.PAPER) c.outline else Color.Transparent, MaterialTheme.shapes.large).padding(14.dp),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val unit = min(maxWidth / scene.width, scene.maxUnit.dp)
            Box(Modifier.size(unit * scene.width, unit * scene.height)) {
                if (scene.ring) Canvas(Modifier.size(unit * scene.width)) {
                    drawCircle(c.tableInset, size.minDimension / 2 * 0.98f)
                    drawCircle(c.onTable.copy(alpha = 0.5f), size.minDimension / 2 * 0.98f, style = Stroke(3f))
                }
                if (scene.links.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
                    val u = unit.toPx()
                    fun centre(id: String) = scene.item(id)?.let { Offset((it.x + it.w / 2) * u, (it.y + it.h / 2) * u) }
                    scene.links.forEach { link ->
                        val a = centre(link.from) ?: return@forEach
                        val b = centre(link.to) ?: return@forEach
                        val path = Path().apply {
                            moveTo(a.x, a.y)
                            if (link.from == link.to) {
                                val dy = -link.bend * 1.7f * u
                                cubicTo(a.x - 1.1f * u, a.y + dy, a.x + 1.1f * u, a.y + dy, a.x, a.y)
                            } else {
                                val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2 - link.bend * u)
                                quadraticTo(mid.x, mid.y, b.x, b.y)
                            }
                        }
                        drawPath(path, if (link.fresh) c.highlight else c.onTable, style = Stroke(u * 0.07f, cap = StrokeCap.Round))
                    }
                }
                scene.items.forEach { item -> ItemView(item, unit, scene.surface, enabled, onTap) }
                // Signs sit on the border between two squares.
                scene.items.filter { it.right.isNotEmpty() || it.below.isNotEmpty() }.forEach { item ->
                    if (item.right.isNotEmpty()) Sign(item.right, unit, item.x + item.w, item.y + item.h / 2)
                    if (item.below.isNotEmpty()) Sign(item.below, unit, item.x + item.w / 2, item.y + item.h)
                }
                scene.items.filter { it.id in highlight }.forEach { item ->
                    Box(Modifier.offset(unit * item.x - 3.dp, unit * item.y - 3.dp).size(unit * item.w + 6.dp, unit * item.h + 6.dp)
                        .border(3.dp, c.highlight.copy(alpha = alpha), RoundedCornerShape(8.dp)))
                }
            }
        }
        if (scene.caption.isNotEmpty()) Text(scene.caption, style = MaterialTheme.typography.bodyMedium,
            color = if (scene.surface == Backdrop.TABLE) c.onTable else c.text, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Sign(text: String, unit: Dp, cx: Float, cy: Float) {
    val c = LocalGameLook.current.colors
    val s = unit * 0.42f
    Box(Modifier.offset(unit * cx - s / 2, unit * cy - s / 2).size(s).background(c.surface, CircleShape).border(1.dp, c.outline, CircleShape),
        contentAlignment = Alignment.Center) {
        Text(text, fontSize = (s.value * 0.62f).sp, fontWeight = FontWeight.Bold, color = c.text, lineHeight = (s.value * 0.7f).sp)
    }
}

private val TileColors = mapOf(2 to 0xFFEEE4DA, 4 to 0xFFEDE0C8, 8 to 0xFFF2B179, 16 to 0xFFF59563, 32 to 0xFFF67C5F, 64 to 0xFFF65E3B,
    128 to 0xFFEDCF72, 256 to 0xFFEDCC61, 512 to 0xFFEDC850, 1024 to 0xFFEDC53F, 2048 to 0xFFEDC22E)
private val BlockColors = listOf(0xFF3FA7D6, 0xFFF2C14E, 0xFF9B5DE5, 0xFF8A8F98, 0xFF59CD90, 0xFFEE6352, 0xFFF79D84)

private fun describe(item: Item): String = item.describe.ifEmpty {
    when (val l = item.look) {
        is Cell -> buildString {
            append(when (l.fill) { Fill.BLOCK -> "Black square"; Fill.SHADED -> "Shaded square"; Fill.COVER -> "Covered square"; else -> "Square" })
            if (l.corner.isNotEmpty()) append(", ${l.corner}")
            if (l.text.isNotEmpty()) append(", ${l.text}")
            if (l.sub.isNotEmpty()) append(", coded ${l.sub}")
            when (l.mark) { Mark.FLAG -> append(", flagged"); Mark.CIRCLE -> append(", circled"); Mark.CROSS -> append(", crossed"); else -> {} }
            l.piece?.let { p ->
                append(when (p.kind) {
                    PieceKind.CHECKER -> if (p.player > 0) ", your ${if (p.king) "king" else "piece"}" else ", computer's piece"
                    PieceKind.DISC -> if (p.player > 0) ", black disc" else ", white disc"
                    PieceKind.STONE -> if (p.player > 0) ", your stone" else ", computer's stone"
                    PieceKind.SPOT -> ", spot" + if (p.label.isNotEmpty()) " with ${p.label} left" else ", full"
                })
            }
        }
        is Clue -> "Clue" + (l.across?.let { ", across $it" } ?: "") + (l.down?.let { ", down $it" } ?: "")
        is Card -> if (l.faceUp) SolitaireState.label(l.card) else "Face-down card"
        is Slot -> "Empty pile"
        is Tile -> if (l.faceUp) mahjongName(l.face) + if (!l.free) ", blocked" else "" else "Face-down tile"
        is Domino -> "Domino ${l.a}–${l.b}"
        is Value -> "Tile ${l.n}"
        is Block -> "Block"
        is Letter -> "Letter ${l.ch}" + if (l.used) ", used" else ""
        is Label -> l.text
        is Edge -> if (l.player == 0) "Line not drawn" else "Drawn line"
        Dot -> "Dot"
    }
}

@Composable
private fun ItemView(item: Item, unit: Dp, surface: Backdrop, enabled: Boolean, onTap: (String) -> Unit) {
    val look = LocalGameLook.current
    val c = look.colors
    val w = unit * item.w; val h = unit * item.h
    val tappable = enabled && item.id.isNotEmpty()
    val base = Modifier.offset(unit * item.x, unit * item.y).size(w, h)
        .then(if (tappable) Modifier.clickable(role = Role.Button) { onTap(item.id) } else Modifier)
        .semantics { contentDescription = say(describe(item)) }
        .then(if (item.tone == Tone.DIM) Modifier.alpha(0.45f) else Modifier)
    when (val l = item.look) {
        is Cell -> CellView(l, item.tone, unit, w, h, surface, base, c)
        is Clue -> {
            val k = ink()
            Box(base.background(k.block).border(0.5.dp, k.line)) {
                Canvas(Modifier.fillMaxSize()) { drawLine(k.paper.copy(alpha = 0.6f), Offset.Zero, Offset(size.width, size.height), strokeWidth = 2f) }
                l.across?.let { Text("$it", Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = 2.dp), color = k.paper, fontSize = (unit.value * 0.26f).sp, fontWeight = FontWeight.Bold) }
                l.down?.let { Text("$it", Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 2.dp), color = k.paper, fontSize = (unit.value * 0.26f).sp, fontWeight = FontWeight.Bold) }
            }
        }
        is Card -> Box(base) { PlayingCard(l.card, width = w, faceUp = l.faceUp, selected = item.tone == Tone.SELECTED) }
        is Slot -> Box(base) { CardSlot(width = w, label = l.label.ifEmpty { null }) }
        is Tile -> Box(base) {
            if (l.faceUp) MahjongTile(l.face, width = w - unit * 0.08f, depth = unit * 0.08f, free = l.free, selected = item.tone == Tone.SELECTED, hinted = item.tone == Tone.GOOD)
            else Canvas(Modifier.fillMaxSize().padding(end = unit * 0.08f, bottom = unit * 0.08f)) { drawCardBack(c.cardBack, c.cardBackPattern) }
        }
        is Domino -> Box(base) { DominoTile(l.a, l.b, horizontal = l.horizontal, unit = if (l.horizontal) w / 2 else w, selected = item.tone == Tone.SELECTED) }
        is Value -> Box(base.padding(unit * 0.05f).background(Color(TileColors[l.n] ?: 0xFF3C3A32), RoundedCornerShape(unit * 0.1f)), contentAlignment = Alignment.Center) {
            Text("${l.n}", fontSize = (unit.value * if (l.n >= 1000) 0.3f else 0.4f).sp, fontWeight = FontWeight.Bold,
                color = if (l.n <= 4) Color(0xFF776E65) else Color.White)
        }
        is Block -> Box(base.padding(1.dp).background(Brush.verticalGradient(listOf(lerp(Color(BlockColors[l.color % BlockColors.size]), Color.White, 0.2f),
            Color(BlockColors[l.color % BlockColors.size]))), RoundedCornerShape(unit * 0.12f)))
        is Letter -> if (l.raised) Box(base.padding(unit * 0.06f).background(if (l.used) c.surfaceAlt else c.accent, RoundedCornerShape(unit * 0.14f)),
            contentAlignment = Alignment.Center) {
            Text(l.ch, fontSize = (unit.value * 0.5f).sp, fontWeight = FontWeight.Bold, color = if (l.used) c.muted else c.onAccent)
        } else Box(base, contentAlignment = Alignment.Center) {
            Text(l.ch, fontSize = (unit.value * 0.44f).sp, fontWeight = FontWeight.Bold, color = if (l.used) c.muted.copy(alpha = 0.35f) else c.text)
        }
        is Label -> Box(base, contentAlignment = if (l.center) Alignment.Center else Alignment.CenterStart) {
            Text(l.text, fontSize = (unit.value * l.size).sp, lineHeight = (unit.value * l.size * 1.15f).sp,
                fontWeight = if (l.bold) FontWeight.Bold else FontWeight.Normal,
                color = if (surface == Backdrop.TABLE) c.onTable else c.text, textAlign = if (l.center) TextAlign.Center else TextAlign.Start)
        }
        is Edge -> Canvas(base) {
            val horizontal = size.width > size.height
            val a = if (horizontal) Offset(0f, size.height / 2) else Offset(size.width / 2, 0f)
            val b = if (horizontal) Offset(size.width, size.height / 2) else Offset(size.width / 2, size.height)
            if (l.player == 0) drawLine(c.outline, a, b, strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            else {
                if (item.tone == Tone.SELECTED) drawLine(c.highlight.copy(alpha = 0.6f), a, b, strokeWidth = size.minDimension * 0.9f, cap = StrokeCap.Round)
                drawLine(if (l.player > 0) c.accent else c.playerTwo, a, b, strokeWidth = size.minDimension * 0.4f, cap = StrokeCap.Round)
            }
        }
        Dot -> Canvas(base) { drawCircle(c.text, size.minDimension / 2 * 0.7f) }
    }
}

@Composable
private fun CellView(l: Cell, tone: Tone, unit: Dp, w: Dp, h: Dp, surface: Backdrop, base: Modifier, c: GamePalette) {
    val k = ink()
    val look = LocalGameLook.current
    val bg = when (l.fill) {
        Fill.PAPER -> k.paper
        Fill.BLOCK, Fill.SHADED -> k.block
        Fill.COVER -> lerp(c.surfaceAlt, c.muted, 0.28f)
        Fill.OPEN -> if (surface == Backdrop.TABLE) c.tableInset else c.surface
        Fill.LIGHT -> c.boardLight
        Fill.DARK -> c.boardDark
        Fill.NONE -> Color.Transparent
    }
    val overlay = when (tone) {
        Tone.SELECTED -> c.highlight.copy(alpha = 0.45f)
        Tone.FOUND -> c.highlight.copy(alpha = 0.35f)
        Tone.GOOD -> c.success.copy(alpha = 0.2f)
        Tone.WRONG -> c.danger.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val line = when (l.fill) {
        Fill.NONE, Fill.LIGHT, Fill.DARK -> Color.Transparent
        Fill.OPEN -> if (surface == Backdrop.TABLE) c.onTable.copy(alpha = 0.3f) else c.outline
        else -> k.line
    }
    Box(base.background(bg).background(overlay).border(0.5.dp, line)) {
        if (l.fill == Fill.COVER) Canvas(Modifier.fillMaxSize()) {
            drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = 3f)
            drawLine(Color.White.copy(alpha = 0.45f), Offset(1f, 0f), Offset(1f, size.height), strokeWidth = 3f)
            drawLine(Color.Black.copy(alpha = 0.2f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 3f)
            drawLine(Color.Black.copy(alpha = 0.2f), Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), strokeWidth = 3f)
        }
        if (l.walls != 0 || l.cage != 0) Canvas(Modifier.fillMaxSize()) {
            val t = 2.5.dp.toPx()
            fun side(bit: Int, dashed: Boolean) {
                val inset = if (dashed) 4.dp.toPx() else t / 2
                val (a, b) = when (bit) {
                    1 -> Offset(inset, inset) to Offset(size.width - inset, inset)
                    2 -> Offset(size.width - inset, inset) to Offset(size.width - inset, size.height - inset)
                    4 -> Offset(inset, size.height - inset) to Offset(size.width - inset, size.height - inset)
                    else -> Offset(inset, inset) to Offset(inset, size.height - inset)
                }
                drawLine(k.ink, a, b, strokeWidth = if (dashed) 1.3.dp.toPx() else t,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null)
            }
            listOf(1, 2, 4, 8).forEach { bit -> if (l.walls and bit != 0) side(bit, false); if (l.cage and bit != 0) side(bit, true) }
        }
        when (l.mark) {
            Mark.CROSS -> Canvas(Modifier.fillMaxSize()) {
                val m = size.minDimension * 0.3f
                drawLine(c.muted, Offset(m, m), Offset(size.width - m, size.height - m), strokeWidth = size.minDimension * 0.07f, cap = StrokeCap.Round)
                drawLine(c.muted, Offset(size.width - m, m), Offset(m, size.height - m), strokeWidth = size.minDimension * 0.07f, cap = StrokeCap.Round)
            }
            Mark.CIRCLE -> Canvas(Modifier.fillMaxSize()) {
                drawCircle(if (l.fill == Fill.NONE && surface == Backdrop.TABLE) c.highlight else c.accent, size.minDimension * 0.38f,
                    style = Stroke(size.minDimension * 0.06f, pathEffect = if (l.text.isEmpty() && l.piece == null) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null))
            }
            Mark.FLAG -> Canvas(Modifier.fillMaxSize().padding(unit * 0.12f)) { drawFlag() }
            Mark.MINE -> Canvas(Modifier.fillMaxSize().padding(unit * 0.12f)) { drawMine() }
            Mark.NONE -> {}
        }
        l.piece?.let { p ->
            val s = min(w, h)
            when (p.kind) {
                PieceKind.CHECKER -> CheckerPiece(p.player, p.king, Modifier.align(Alignment.Center), size = s * 0.84f)
                PieceKind.DISC -> Canvas(Modifier.fillMaxSize().padding(s * 0.06f)) { drawDisc(p.player) }
                PieceKind.STONE -> Canvas(Modifier.fillMaxSize()) {
                    val base = if (p.player > 0) c.playerOne else c.playerTwo
                    drawCircle(base.copy(alpha = 0.12f), size.minDimension * 0.5f)
                    drawCircle(Color.Black.copy(alpha = 0.3f), size.minDimension * 0.3f, center + Offset(0f, size.minDimension * 0.04f))
                    drawCircle(Brush.radialGradient(listOf(lerp(base, Color.White, 0.35f), base), center - Offset(size.minDimension * 0.08f, size.minDimension * 0.08f),
                        size.minDimension * 0.35f), size.minDimension * 0.3f)
                }
                PieceKind.SPOT -> {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(if (tone == Tone.SELECTED) c.highlight else c.onTable, size.minDimension * 0.24f)
                        if (tone == Tone.SELECTED) drawCircle(c.highlight, size.minDimension * 0.42f, style = Stroke(4f))
                    }
                    if (p.label.isNotEmpty()) Text(p.label, Modifier.align(Alignment.TopEnd).offset(x = unit * 0.1f, y = -unit * 0.12f),
                        fontSize = (unit.value * 0.24f).sp, fontWeight = FontWeight.Bold, color = c.onTable)
                }
            }
        }
        if (l.corner.isNotEmpty()) Text(l.corner, Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp),
            fontSize = (unit.value * 0.22f).sp, lineHeight = (unit.value * 0.24f).sp, fontWeight = FontWeight.Bold,
            color = if (l.fill == Fill.BLOCK) k.paper else k.ink.copy(alpha = 0.75f))
        if (l.notes.isNotEmpty()) Text(l.notes, Modifier.align(Alignment.Center), fontSize = (unit.value * 0.24f).sp, color = c.muted)
        if (l.text.isNotEmpty()) {
            val digit = l.text.singleOrNull()?.digitToIntOrNull()
            val color = when {
                l.fill == Fill.SHADED -> k.paper.copy(alpha = 0.6f)
                l.fill == Fill.OPEN && digit != null && surface != Backdrop.BOARD -> mineNumberColor(digit, look.dark)
                tone == Tone.ENTERED -> c.accent
                tone == Tone.WRONG -> c.danger
                else -> k.ink
            }
            Text(l.text, Modifier.align(Alignment.Center).padding(bottom = if (l.sub.isNotEmpty()) unit * 0.18f else 0.dp),
                fontSize = (unit.value * if (l.text.length > 1) 0.3f else 0.5f).sp, fontWeight = FontWeight.Bold, color = color)
        }
        if (l.sub.isNotEmpty()) Text(l.sub, Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp), fontSize = (unit.value * 0.3f).sp, color = c.muted)
    }
}

/** A list of every game's tutorial, grouped like the home screen. */
@Composable
fun TutorialHubScreen(nav: NavController) {
    val c = LocalGameLook.current.colors
    GameScaffold(title = "How to play", subtitle = "Guided tutorials and rules for every game", onBack = { nav.popBackStack() }) {
        Text("Each game has its own short guided tutorial. They're optional: pick any game to learn it, or come back any time.",
            style = MaterialTheme.typography.bodyMedium, color = c.muted)
        GameGroup.entries.forEach { group ->
            SectionTitle(group.title)
            GameId.entries.filter { it.group == group }.forEach { id ->
                val t = remember(id) { Tutorials.of(id) }
                Surface(onClick = { nav.navigate(tutorialRoute(id, hub = true)) }, color = c.surface, shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(52.dp).background(c.surfaceAlt, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) { GameArt(id, Modifier.size(44.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(id.title, style = MaterialTheme.typography.titleMedium)
                            Text(t.summary, style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        Text("${t.steps.size} steps", style = MaterialTheme.typography.labelMedium, color = c.accent, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}
