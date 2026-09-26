package com.simplegamegen.sudoku.ui.blot

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.ArcadeStore
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.components.GameScaffold
import com.simplegamegen.sudoku.ui.components.HeaderAction
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.SectionTitle
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val BLOT_THEME_EDIT_ROUTE = "blot_theme_edit?id={id}&from={from}"
fun blotThemeEditRoute(id: String? = null, from: String? = null) = "blot_theme_edit?id=${id.orEmpty()}&from=${from.orEmpty()}"

private val SampleRows = listOf("VUM#D", "A#ZRI", "KEL.F")
private val SampleWord = listOf(0, 1, 2)

/**
 * A small board in a theme's look with its creature. Each time [play] changes it acts out a turn: the word VUM is
 * traced, fills in stroke order, the creature reacts, and the celebration plays.
 */
@Composable
fun BlotPreview(theme: BlotTheme, modifier: Modifier = Modifier, side: Dp = 34.dp, play: Int = 0) {
    val look = LocalGameLook.current
    val tc = theme.colors.colors(look.dark)
    val motion = theme.motion
    val markPicture = rememberAsset(theme, AssetSlot.MARK)
    val tilePicture = rememberAsset(theme, AssetSlot.TILE)
    val boardPicture = rememberAsset(theme, AssetSlot.BOARD)
    val partyPicture = rememberAsset(theme, AssetSlot.PARTICLE)
    val fills = remember { SampleWord.map { Animatable(0f) } }
    val trail = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    val party = remember { Animatable(0f) }
    var cheering by remember { mutableStateOf(false) }
    LaunchedEffect(play, theme.motion) {
        if (play == 0) return@LaunchedEffect
        fills.forEach { it.snapTo(0f) }; trail.snapTo(0f); party.snapTo(0f); cheering = false
        trail.animateTo(1f, tween((500 * motion.pace.factor).toInt()))
        fills.forEachIndexed { n, a ->
            launch {
                delay((n * motion.stroke * motion.pace.factor).toLong())
                if (motion.fill == FillStyle.SPLASH || motion.fill == FillStyle.SPIN)
                    a.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f / (motion.pace.factor * motion.pace.factor)))
                else a.animateTo(1f, tween((420 * motion.pace.factor).toInt(), easing = FastOutSlowInEasing))
            }
        }
        trail.snapTo(0f)
        launch { hop.animateTo(1f, tween(130)); hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 420f)) }
        delay((SampleWord.size * motion.stroke * motion.pace.factor + 600).toLong())
        cheering = true
        party.animateTo(1f, tween((2400 * motion.pace.factor).toInt(), easing = LinearEasing))
        cheering = false
        delay(500)
        fills.forEach { it.snapTo(0f) }
    }
    val loop = rememberInfiniteTransition(label = "preview")
    val phase by if (theme.markMovesWhileIdle()) loop.animateFloat(0f, 1f, infiniteRepeatable(tween((3200 * motion.pace.factor).toInt(), easing = LinearEasing)), label = "sea")
        else remember { mutableStateOf(0f) }
    Surface(color = tc.paper, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Box {
            if (boardPicture != null) Image(boardPicture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize(), alpha = 0.9f)
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Mascot(theme, tc, happy = cheering, modifier = Modifier.size(side * 2.2f), bounce = hop.value)
                Box {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SampleRows.forEachIndexed { r, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                row.forEachIndexed { k, ch ->
                                    val i = r * 5 + k
                                    val progress = if (ch == '#') 1f else if (r == 0 && k in SampleWord) fills[k].value else 0f
                                    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                                        if (progress < 1f) {
                                            val shape = tileShape(theme.tile, side)
                                            Box(Modifier.fillMaxSize().clip(shape).background(tc.tile, shape), contentAlignment = Alignment.Center) {
                                                if (tilePicture != null) Image(tilePicture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                Box(Modifier.fillMaxSize().border(1.dp, tc.mark.copy(alpha = 0.22f), shape))
                                                if (ch != '.' && ch != '#') androidx.compose.material3.Text(ch.toString(), fontSize = (side.value * 0.46f).sp,
                                                    fontWeight = FontWeight.Bold, color = tc.letter.copy(alpha = 1f - progress.coerceIn(0f, 1f)))
                                            }
                                        }
                                        if (progress > 0f) Canvas(Modifier.fillMaxSize()) {
                                            drawMark(theme.mark, motion.fill, Rect(Offset.Zero, size), tc, i, progress, phase, markPicture)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (trail.value > 0f) Canvas(Modifier.matchParentSize()) {
                        val step = (side + 2.dp).toPx()
                        val shown = SampleWord.take(1 + (trail.value * (SampleWord.size - 1)).toInt().coerceAtMost(SampleWord.size - 1))
                        drawTrail(motion.trail, shown.map { k -> Offset(k * step + side.toPx() / 2, side.toPx() / 2) }, side.toPx() * 0.16f, tc.accent)
                    }
                    if (party.value in 0.001f..0.999f) Canvas(Modifier.matchParentSize()) {
                        drawParty(motion.party, motion.partyAmount, party.value, 7, side.toPx(), tc, partyPicture)
                    }
                }
            }
        }
    }
}

/** Every Blotwords theme: pick the one to play in, or make and edit your own. */
@Composable
fun BlotThemesScreen(nav: NavController, store: ArcadeStore) {
    val look = LocalGameLook.current
    val c = look.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var themes by remember { mutableStateOf(BlotThemes.builtIns) }
    var chosen by remember { mutableStateOf(BlotThemes.ink.id) }
    LaunchedEffect(Unit) {
        themes = BlotThemes.all(store); chosen = BlotThemes.chosen(store).id
        // Pictures added to a theme that was never saved are cleared away here.
        BlotAssets.sweep(context, themes)
    }
    GameScaffold(title = "Blotwords themes", onBack = { nav.popBackStack() }, game = GameId.BLOTWORDS,
        actions = { HeaderAction(GameIcons.Plus, "New theme") { nav.navigate(blotThemeEditRoute()) } }) {
        Text("A theme changes how Blotwords looks and moves: the board, the squares, the creature and the celebrations, with your own pictures if you like. The words and rules stay the same.",
            style = MaterialTheme.typography.bodyMedium, color = c.muted)
        themes.forEach { t ->
            val inUse = t.id == chosen
            var play by remember { mutableIntStateOf(0) }
            Surface(onClick = { chosen = t.id; play++; scope.launch { BlotThemes.choose(store, t.id) } },
                color = c.surface, shape = MaterialTheme.shapes.large,
                border = BorderStroke(if (inUse) 2.dp else 1.dp, if (inUse) c.accent else c.outline),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = say(if (inUse) "${t.name}, in use" else "${t.name}, tap to use") }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f))
                        if (inUse) Text("In use", style = MaterialTheme.typography.labelLarge, color = c.accent)
                    }
                    BlotPreview(t, Modifier.fillMaxWidth(), side = 30.dp, play = play)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!t.builtIn) OutlinedButton(onClick = { nav.navigate(blotThemeEditRoute(id = t.id)) }) { Text("Edit") }
                        OutlinedButton(onClick = { nav.navigate(blotThemeEditRoute(from = t.id)) }) { Text("Copy and change") }
                    }
                }
            }
        }
        Button(onClick = { nav.navigate(blotThemeEditRoute()) }, modifier = Modifier.fillMaxWidth()) { Text("Make a new theme") }
    }
}

/**
 * The theme studio: name, colors and drawn parts, the player's own pictures, and every animation, with a
 * preview that acts out a turn.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlotThemeEditor(nav: NavController, store: ArcadeStore, id: String?, from: String?) {
    val look = LocalGameLook.current
    val c = look.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<BlotTheme?>(null) }
    var existing by remember { mutableStateOf(false) }
    var play by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf<AssetSlot?>(null) }
    var pictureError by remember { mutableStateOf(false) }
    LaunchedEffect(id, from) {
        val all = BlotThemes.all(store)
        val editing = all.firstOrNull { it.id == id && !it.builtIn }
        existing = editing != null
        draft = editing ?: (all.firstOrNull { it.id == from } ?: BlotThemes.ink).let { base ->
            base.copy(id = "custom" + System.currentTimeMillis().toString(36),
                name = (if (from != null) say("My ${base.name}") else say("My theme")).take(BlotTheme.MAX_NAME), builtIn = false)
        }
        play++
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val slot = picking ?: return@rememberLauncherForActivityResult
        picking = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = BlotAssets.import(context, uri)
            pictureError = name == null
            if (name != null) draft = draft?.let { d -> d.copy(assets = d.assets + (slot to name)) }
            play++
        }
    }
    val d = draft ?: return
    val problem = d.problem()
    fun update(next: BlotTheme) { draft = next; play++ }
    fun save() {
        scope.launch {
            BlotThemes.save(store, d.copy(name = d.name.trim()))
            BlotThemes.choose(store, d.id)
            nav.popBackStack()
        }
    }
    GameScaffold(title = if (existing) "Edit theme" else "New theme", onBack = { nav.popBackStack() }, game = GameId.BLOTWORDS,
        actions = { HeaderAction(GameIcons.Save, "Save theme", enabled = problem == null) { save() } }) {
        BlotPreview(d, Modifier.fillMaxWidth(), play = play)
        OutlinedButton(onClick = { play++ }, modifier = Modifier.fillMaxWidth()) { Text("Play the animation") }
        OutlinedTextField(value = d.name, onValueChange = { draft = d.copy(name = it.take(BlotTheme.MAX_NAME)) },
            label = { Text("Theme name") }, singleLine = true, isError = problem != null, modifier = Modifier.fillMaxWidth(),
            supportingText = { problem?.let { Text(it) } })

        SectionTitle("Colors")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BlotSchemes.all.forEach { scheme ->
                val sc = scheme.colors(look.dark)
                val on = d.scheme == scheme.id
                Surface(onClick = { update(d.copy(scheme = scheme.id)) }, color = sc.paper, shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(if (on) 2.5.dp else 1.dp, if (on) c.accent else c.outline),
                    modifier = Modifier.semantics { contentDescription = say(if (on) "${scheme.label}, chosen" else scheme.label) }) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf(sc.mark, sc.tile, sc.accent, sc.detail).forEach { col ->
                                Box(Modifier.size(16.dp).background(col, CircleShape).border(1.dp, sc.letter.copy(alpha = 0.2f), CircleShape))
                            }
                        }
                        Text(scheme.label, style = MaterialTheme.typography.labelMedium, color = sc.letter)
                    }
                }
            }
        }

        SectionTitle("Drawn parts")
        OptionGroup("Filled squares", BlotMark.entries, d.mark, { it.label }) { update(d.copy(mark = it)) }
        OptionGroup("Letter squares", BlotTile.entries, d.tile, { it.label }) { update(d.copy(tile = it)) }
        OptionGroup("Creature", BlotMascot.entries, d.mascot, { it.label }) { update(d.copy(mascot = it)) }

        SectionTitle("Your pictures")
        Text("Pictures from your phone replace the drawn parts. They stay on this device.", style = MaterialTheme.typography.bodySmall, color = c.muted)
        if (pictureError) Text("That picture couldn't be opened. Try another one.", style = MaterialTheme.typography.bodySmall, color = c.danger)
        AssetSlot.entries.forEach { slot ->
            val picture = rememberAsset(d, slot)
            Surface(color = c.surface, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, c.outline), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceAlt), contentAlignment = Alignment.Center) {
                        if (picture != null) Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text("—", color = c.muted)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(slot.label, style = MaterialTheme.typography.bodyLarge, color = c.text)
                        Text(if (picture != null) "Your picture" else "Drawn by the theme", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                    TextButton(onClick = {
                        picking = slot
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (picture != null) "Change" else "Add picture") }
                    if (picture != null) TextButton(onClick = { update(d.copy(assets = d.assets - slot)) }) { Text("Remove") }
                }
            }
        }

        SectionTitle("Animation")
        val m = d.motion
        OptionGroup("How squares fill in", FillStyle.entries, m.fill, { it.label }) { update(d.copy(motion = m.copy(fill = it))) }
        OptionGroup("Speed", Pace.entries, m.pace, { it.label }) { update(d.copy(motion = m.copy(pace = it))) }
        Text("Pause between letters: ${m.stroke} ms", style = MaterialTheme.typography.bodyMedium, color = c.text)
        Slider(value = m.stroke.toFloat(), onValueChange = { draft = d.copy(motion = m.copy(stroke = it.toInt())) }, onValueChangeFinished = { play++ },
            valueRange = 0f..200f, steps = 19, modifier = Modifier.semantics { contentDescription = say("Pause between letters") })
        OptionGroup("Creature while waiting", IdleStyle.entries, m.idle, { it.label }) { update(d.copy(motion = m.copy(idle = it))) }
        OptionGroup("Creature when a word is written", ReactStyle.entries, m.react, { it.label }) { update(d.copy(motion = m.copy(react = it))) }
        OptionGroup("Celebration", PartyStyle.entries, m.party, { it.label }) { update(d.copy(motion = m.copy(party = it))) }
        Text("Celebration pieces: ${m.partyAmount}", style = MaterialTheme.typography.bodyMedium, color = c.text)
        Slider(value = m.partyAmount.toFloat(), onValueChange = { draft = d.copy(motion = m.copy(partyAmount = it.toInt())) }, onValueChangeFinished = { play++ },
            valueRange = 0f..40f, steps = 39, modifier = Modifier.semantics { contentDescription = say("Celebration pieces") })
        OptionGroup("Tracing a word", TrailStyle.entries, m.trail, { it.label }) { update(d.copy(motion = m.copy(trail = it))) }

        Button(onClick = ::save, enabled = problem == null, modifier = Modifier.fillMaxWidth()) { Text("Save and use this theme") }
        if (existing) OutlinedButton(onClick = {
            scope.launch { BlotThemes.delete(store, d.id); nav.popBackStack() }
        }, modifier = Modifier.fillMaxWidth()) { Text("Delete this theme", color = c.danger) }
    }
}
