package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.simplegamegen.sudoku.data.Outcome
import com.simplegamegen.sudoku.data.PuzzleFactory
import com.simplegamegen.sudoku.data.Result
import com.simplegamegen.sudoku.duels.Battleship
import com.simplegamegen.sudoku.duels.Mark
import com.simplegamegen.sudoku.duels.Ship
import com.simplegamegen.sudoku.duels.ShipKind
import com.simplegamegen.sudoku.duels.Shot
import com.simplegamegen.sudoku.ui.GameId
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.NewGameSheet
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.components.TablePanel
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.hypot

private val Water = Color(0xFF1565C0)
private val WaterDeep = Color(0xFF0E4C92)
private val Fog = Color(0xFF08243F)
private val Hull = Color(0xFFECEFF1)
private val HullEdge = Color(0xFF546E7A)
private val SunkHull = Color(0xFF8D6E63)
private val SunkEdge = Color(0xFF4E342E)
private val HitRed = Color(0xFFE53935)
private val MissInk = Color(0xFFF7F4EF)
private val PreviewOk = Color(0xFF43A047)
private val PreviewBad = Color(0xFFEF5350)

private val PlaceLines = listOf(
    "Place your Carrier. It covers 5 squares.",
    "Place your Battleship. It covers 4 squares.",
    "Place your Cruiser. It covers 3 squares.",
    "Place your Submarine. It covers 3 squares.",
    "Place your Destroyer. It covers 2 squares.",
)

private val Strength = listOf(
    "Mostly random shots, with a weak follow-up after a hit",
    "After a hit, fires at a neighboring square",
    "Hunts around hits, then fires in a checkerboard",
    "Aims at the squares most likely to hold a remaining ship",
)

val BattleshipSetup: (PuzzleFactory) -> PlaySetup<Battleship> = { factory ->
    PlaySetup(
        rules = "Place five ships on your grid: a carrier (5), a battleship (4), a cruiser (3), a submarine (3) and a destroyer (2). " +
            "Ships run in a straight line. They may touch, but they cannot overlap or leave the board. Tap or drag to place the bow, and rotate to turn a ship. " +
            "Randomize places the whole fleet. Then fire at the computer's hidden grid. You shoot first. Squares you already fired at are ignored. " +
            "Sink every enemy ship to win. If the computer sinks yours, you lose.",
        settingTitle = "Computer strength",
        settings = Battleship.NAMES,
        describe = { Strength[it] },
        settingOf = { it.setting },
        inProgress = { !it.over && (it.you.isNotEmpty() || it.salvo.isNotEmpty()) },
        subtitle = {
            val name = Battleship.NAMES[it.setting]
            if (it.placing) "$name · ${it.you.size} of 5 placed" else "$name · ${if (it.salvo.size == 1) "1 shot" else "${it.salvo.size} shots"}"
        },
        create = { i ->
            {
                factory.custom("battleship:$i", { seed -> Battleship.start(seed, i) }) {
                    it.enemy.joinToString(";") { ship -> "${ship.kind.name}:${ship.row},${ship.col},${ship.horizontal}" }
                }
            }
        },
        identity = { it.seed.toString() },
        outcome = { g ->
            when {
                g.won -> Outcome(Result.WON, g.salvo.size.toLong())
                g.lost -> Outcome(Result.LOST)
                else -> null
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BattleshipScreen(nav: NavController, vm: PlayViewModel<Battleship>, factory: PuzzleFactory) {
    val c = LocalGameLook.current.colors
    val setup = remember(factory) { BattleshipSetup(factory) }
    var difficulty by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableIntStateOf(0) }
    PlayShell(nav, vm, GameId.BATTLESHIP, setup) { g, s ->
        val playable = !s.busy && !s.thinking
        if (g.placing) {
            InfoChip("Place your fleet", emphasized = true)
            InfoChip(if (g.horizontal) "Across" else "Down")
        } else {
            DuelStatus(
                g.over, g.winner, if (g.yourTurn) 1 else -1, s.thinking,
                "You ${g.salvo.count { it.mark != Mark.MISS }}",
                "Computer ${g.incoming.count { it.mark != Mark.MISS }}",
            )
        }
        g.yourNews()?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = if (g.won) c.success else c.text) }
        g.theirNews()?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = if (g.lost) c.muted else c.text) }
        if (g.won) Text("You sank the fleet!", style = MaterialTheme.typography.titleMedium, color = c.success)
        if (g.lost) Text("Your fleet was sunk.", style = MaterialTheme.typography.titleMedium, color = c.muted)
        if (g.over && playable) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.start(setup.create(g.setting)) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Rematch") }
                OutlinedButton(onClick = { picked = g.setting; difficulty = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Change difficulty") }
            }
        }
        if (g.placing) {
            Text(PlaceLines[g.you.size], style = MaterialTheme.typography.bodyLarge)
            Text(if (g.horizontal) "Across. Tap a square or drag." else "Down. Tap a square or drag.",
                style = MaterialTheme.typography.bodyMedium, color = c.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShipKind.entries.forEachIndexed { index, kind ->
                    InfoChip("@ship:${kind.title}", emphasized = index == g.you.size)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.play { it.rotate() } }, enabled = playable, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Rotate") }
                OutlinedButton(onClick = { vm.play { it.randomize() } }, enabled = playable, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Randomize") }
            }
            Text("Your fleet", style = MaterialTheme.typography.titleMedium)
            SeaBoard(
                ships = g.you, shots = emptyList(), reveal = true, fog = false, fire = false,
                describe = { ownSquare(g, it) },
                place = if (playable) g else null,
                onPlace = { cell ->
                    val row = cell / Battleship.SIZE
                    val col = cell % Battleship.SIZE
                    if (g.canPlace(row, col)) vm.play { it.place(row, col) }
                    else vm.say("Ships have to stay on the board without overlapping.")
                },
            )
            Text("Enemy waters", style = MaterialTheme.typography.titleMedium)
            Text("Hidden until your fleet is placed.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            SeaBoard(ships = emptyList(), shots = emptyList(), reveal = false, fog = true, fire = false,
                describe = { located(it, "hidden") }, onFire = {})
        } else {
            Text("Enemy waters", style = MaterialTheme.typography.titleMedium)
            if (g.yourTurn && playable) Text("Tap a square to fire.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            val sunk = g.enemy.filter { ship -> ship.cells.all { cell -> g.salvo.any { it.cell == cell && it.mark != Mark.MISS } } }
            SeaBoard(
                ships = sunk, shots = g.salvo, reveal = false, fog = false,
                fire = g.yourTurn && playable,
                describe = { enemySquare(g, it) },
                onFire = { cell -> vm.play { it.fire(cell) } },
            )
            Text("Your fleet", style = MaterialTheme.typography.titleMedium)
            SeaBoard(ships = g.you, shots = g.incoming, reveal = true, fog = false, fire = false,
                describe = { ownSquare(g, it) }, onFire = {})
        }
    }
    if (difficulty) {
        NewGameSheet("Change difficulty", startLabel = "Start", game = GameId.BATTLESHIP, onDismiss = { difficulty = false }, onStart = {
            difficulty = false
            vm.start(setup.create(picked))
        }) {
            OptionGroup("Computer strength", Battleship.NAMES.indices.toList(), picked, { Battleship.NAMES[it] }) { picked = it }
            Text(Strength[picked], style = MaterialTheme.typography.bodyMedium, color = c.muted)
        }
    }
}

@Composable
private fun SeaBoard(
    ships: List<Ship>,
    shots: List<Shot>,
    reveal: Boolean,
    fog: Boolean,
    fire: Boolean,
    describe: (Int) -> String,
    onFire: (Int) -> Unit = {},
    place: Battleship? = null,
    onPlace: (Int) -> Unit = {},
) {
    var ghost by remember { mutableIntStateOf(-1) }
    val placeAt = rememberUpdatedState(onPlace)
    val placing = rememberUpdatedState(place)
    TablePanel {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gutter = 18.dp
            val cell = (maxWidth - gutter) / Battleship.SIZE
            val muted = LocalGameLook.current.colors.muted
            Column {
                Row {
                    Box(Modifier.size(gutter))
                    for (col in 0 until Battleship.SIZE) {
                        Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                            Text("${'A' + col}", style = MaterialTheme.typography.labelSmall, color = muted)
                        }
                    }
                }
                Row {
                    Column {
                        for (row in 1..Battleship.SIZE) {
                            Box(Modifier.size(gutter, cell), contentAlignment = Alignment.Center) {
                                Text("$row", style = MaterialTheme.typography.labelSmall, color = muted)
                            }
                        }
                    }
                    val preview = placing.value?.let { game -> if (ghost >= 0) previewOf(game, ghost) else null }
                    Box(
                        Modifier.size(cell * Battleship.SIZE).clip(RoundedCornerShape(8.dp)).pointerInput(place != null) {
                            if (place == null) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val slop = viewConfiguration.touchSlop
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    ghost = squareAt(change.position, size.width.toFloat())
                                    if (!change.pressed) {
                                        val landed = squareAt(change.position, size.width.toFloat())
                                        ghost = -1
                                        if (landed >= 0) placeAt.value(landed)
                                        break
                                    }
                                    if (hypot(change.position.x - down.position.x, change.position.y - down.position.y) > slop) change.consume()
                                }
                            }
                        },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val unit = size.width / Battleship.SIZE
                            drawWater(unit)
                            if (reveal) drawFleet(ships, shots, unit) else drawSunk(ships, unit)
                            preview?.let { (cells, ok) -> drawPreview(cells, ok, unit) }
                            drawShots(shots, unit)
                            if (fog) drawRect(Fog.copy(alpha = 0.72f))
                        }
                        Column {
                            for (row in 0 until Battleship.SIZE) {
                                Row {
                                    for (col in 0 until Battleship.SIZE) {
                                        val index = row * Battleship.SIZE + col
                                        val aimed = fire && shots.none { it.cell == index }
                                        Box(
                                            Modifier.size(cell)
                                                .then(if (aimed) Modifier.clickable(role = Role.Button) { onFire(index) } else Modifier)
                                                .semantics {
                                                    contentDescription = say(describe(index))
                                                    if (place != null) {
                                                        role = Role.Button
                                                        onClick(say("Place")) { onPlace(index); true }
                                                    }
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun previewOf(g: Battleship, bow: Int): Pair<Set<Int>, Boolean> {
    val row = bow / Battleship.SIZE
    val col = bow % Battleship.SIZE
    val kind = g.nextKind ?: return emptySet<Int>() to false
    if (!Battleship.onBoard(kind, row, col, g.horizontal)) return setOf(bow) to false
    return Battleship.footprint(kind, row, col, g.horizontal) to g.canPlace(row, col)
}

private fun DrawScope.drawWater(cell: Float) {
    for (row in 0 until Battleship.SIZE) for (col in 0 until Battleship.SIZE) {
        drawRect(if ((row + col) % 2 == 0) Water else WaterDeep, Offset(col * cell, row * cell), Size(cell, cell))
    }
    val ink = Color.White.copy(alpha = 0.18f)
    for (i in 0..Battleship.SIZE) {
        drawLine(ink, Offset(i * cell, 0f), Offset(i * cell, cell * Battleship.SIZE), strokeWidth = 1f)
        drawLine(ink, Offset(0f, i * cell), Offset(cell * Battleship.SIZE, i * cell), strokeWidth = 1f)
    }
}

private fun DrawScope.drawFleet(ships: List<Ship>, shots: List<Shot>, cell: Float) {
    val hit = shots.filter { it.mark != Mark.MISS }.map { it.cell }.toSet()
    ships.forEach { ship ->
        val sunk = ship.cells.all { it in hit }
        drawHull(ship, cell, if (sunk) SunkHull else Hull, if (sunk) SunkEdge else HullEdge)
    }
}

private fun DrawScope.drawSunk(ships: List<Ship>, cell: Float) {
    ships.forEach { drawHull(it, cell, SunkHull, SunkEdge) }
}

private fun DrawScope.drawHull(ship: Ship, cell: Float, fill: Color, edge: Color) {
    val inset = cell * 0.18f
    val x = ship.col * cell + if (ship.horizontal) cell * 0.12f else inset
    val y = ship.row * cell + if (ship.horizontal) inset else cell * 0.12f
    val w = if (ship.horizontal) ship.kind.length * cell - cell * 0.24f else cell - inset * 2
    val h = if (ship.horizontal) cell - inset * 2 else ship.kind.length * cell - cell * 0.24f
    drawRoundRect(edge, Offset(x, y + cell * 0.05f), Size(w, h), CornerRadius(cell * 0.16f))
    drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(cell * 0.16f))
}

private fun DrawScope.drawPreview(cells: Set<Int>, ok: Boolean, cell: Float) {
    val color = (if (ok) PreviewOk else PreviewBad).copy(alpha = 0.45f)
    cells.forEach { index ->
        drawRect(color, Offset((index % 10) * cell + 2f, (index / 10) * cell + 2f), Size(cell - 4f, cell - 4f))
    }
}

private fun DrawScope.drawShots(shots: List<Shot>, cell: Float) {
    shots.forEach { shot ->
        val center = Offset((shot.cell % 10 + 0.5f) * cell, (shot.cell / 10 + 0.5f) * cell)
        when (shot.mark) {
            Mark.MISS -> {
                drawCircle(MissInk.copy(alpha = 0.35f), cell * 0.22f, center)
                drawCircle(MissInk, cell * 0.1f, center)
            }
            Mark.HIT, Mark.SUNK -> {
                drawCircle(HitRed, cell * 0.16f, center)
                drawCircle(Color.White, cell * 0.05f, center)
            }
        }
    }
}

private fun squareAt(position: Offset, board: Float): Int {
    if (board <= 0f) return -1
    val cell = board / Battleship.SIZE
    val col = (position.x / cell).toInt()
    val row = (position.y / cell).toInt()
    if (row !in 0 until Battleship.SIZE || col !in 0 until Battleship.SIZE) return -1
    return row * Battleship.SIZE + col
}

private fun located(cell: Int, detail: String) =
    "Row ${cell / Battleship.SIZE + 1}, column ${'A' + cell % Battleship.SIZE}, $detail"

private fun ownSquare(g: Battleship, cell: Int): String {
    val ship = g.you.find { cell in it.cells }
    val shot = g.incoming.find { it.cell == cell }
    val detail = when {
        ship != null && shot?.mark == Mark.SUNK -> "sunk ${ship.kind.title}"
        ship != null && shot?.mark == Mark.HIT -> "hit on your ${ship.kind.title}"
        ship != null -> "your ${ship.kind.title}"
        shot?.mark == Mark.MISS -> "miss"
        else -> "water"
    }
    return located(cell, detail)
}

private fun enemySquare(g: Battleship, cell: Int): String {
    val shot = g.salvo.find { it.cell == cell }
    val sunk = g.enemy.find { ship ->
        cell in ship.cells && ship.cells.all { square -> g.salvo.any { it.cell == square && it.mark != Mark.MISS } }
    }
    val detail = when {
        sunk != null -> "sunk ${sunk.kind.title}"
        shot?.mark == Mark.HIT -> "hit"
        shot?.mark == Mark.MISS -> "miss"
        else -> "fog"
    }
    return located(cell, detail)
}
