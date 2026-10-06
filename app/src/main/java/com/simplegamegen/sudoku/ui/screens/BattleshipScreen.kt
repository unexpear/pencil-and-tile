package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.simplegamegen.sudoku.ui.assets.BoardView
import com.simplegamegen.sudoku.ui.assets.GameIcons
import com.simplegamegen.sudoku.ui.assets.HitPinAsset
import com.simplegamegen.sudoku.ui.assets.Mesh
import com.simplegamegen.sudoku.ui.assets.MissPinAsset
import com.simplegamegen.sudoku.ui.assets.Polished
import com.simplegamegen.sudoku.ui.assets.Pose
import com.simplegamegen.sudoku.ui.assets.TableFrame
import com.simplegamegen.sudoku.ui.assets.coversCell
import com.simplegamegen.sudoku.ui.assets.decodeShipMesh
import com.simplegamegen.sudoku.ui.assets.drawBoardFrame
import com.simplegamegen.sudoku.ui.assets.drawBoardLabel
import com.simplegamegen.sudoku.ui.assets.drawMesh
import com.simplegamegen.sudoku.ui.assets.drawSquareTop
import com.simplegamegen.sudoku.ui.assets.shipMeshAsset
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.components.NewGameSheet
import com.simplegamegen.sudoku.ui.components.OptionGroup
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.i18n.say
import com.simplegamegen.sudoku.ui.theme.LocalGameLook
import kotlin.math.hypot
import kotlin.math.roundToInt

private val Water = Color(0xFF1E88E5)
private val WaterDeep = Color(0xFF1565C0)
private val Tray = Color(0xFF0E3A5D)
private val Fog = Color(0xFF08243F)
private val Hull = Color(0xFFECEFF1)
private val SunkHull = Color(0xFF8D6E63)
private val HitRed = Color(0xFFE53935)
private val MissInk = Color(0xFFF7F4EF)
private val PreviewOk = Color(0xFF43A047)
private val PreviewBad = Color(0xFFEF5350)
private val LabelInk = Color(0xFFF7F4EF)

private const val Top = 0.22f
private const val Peak = 1.15f

private val SeaBehind = BoardView("Behind", yaw = 0f, pitch = 68f, distance = 1.38f)
private val SeaCorner = BoardView("Corner", yaw = 28f, pitch = 60f, distance = 1.5f)
private val SeaTop = BoardView("Top", yaw = 0f, pitch = 90f, distance = 1.68f)

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

private class FleetMeshes(val ships: Map<ShipKind, Mesh>, val miss: Mesh, val hit: Mesh)

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
    var full by rememberSaveable { mutableStateOf(false) }
    val camera = rememberBoardCamera("battleship-camera", listOf(SeaBehind, SeaCorner, SeaTop))
    val meshes = rememberFleetMeshes()
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
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = maxWidth.coerceAtMost(560.dp)
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
                SeaHeading("Your fleet", full) { full = !full }
                SeaBoard(
                    camera, meshes, ships = g.you, shots = emptyList(), reveal = true, fog = false,
                    fire = false, describe = { ownSquare(g, it) },
                    place = if (playable) g else null,
                    onPlace = { cell ->
                        val row = cell / Battleship.SIZE
                        val col = cell % Battleship.SIZE
                        if (g.canPlace(row, col)) vm.play { it.place(row, col) }
                        else vm.say("Ships have to stay on the board without overlapping.")
                    },
                    height = side, controls = true,
                )
                Text("Enemy waters", style = MaterialTheme.typography.titleMedium)
                Text("Hidden until your fleet is placed.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                SeaBoard(
                    camera, meshes, ships = emptyList(), shots = emptyList(), reveal = false, fog = true,
                    fire = false, describe = { located(it, "hidden") }, height = side * 0.72f, controls = false,
                )
            } else {
                val sunk = g.enemy.filter { ship -> ship.cells.all { cell -> g.salvo.any { it.cell == cell && it.mark != Mark.MISS } } }
                SeaHeading("Enemy waters", full) { full = !full }
                if (g.yourTurn && playable) Text("Tap a square to fire.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                SeaBoard(
                    camera, meshes, ships = sunk, shots = g.salvo, reveal = false, fog = false,
                    fire = g.yourTurn && playable, describe = { enemySquare(g, it) },
                    onFire = { cell -> vm.play { it.fire(cell) } },
                    height = side, controls = true,
                )
                Text("Your fleet", style = MaterialTheme.typography.titleMedium)
                SeaBoard(
                    camera, meshes, ships = g.you, shots = g.incoming, reveal = true, fog = false,
                    fire = false, describe = { ownSquare(g, it) }, height = side * 0.86f, controls = false,
                )
            }
        }
        if (full) {
            Dialog(onDismissRequest = { full = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                Column(
                    Modifier.fillMaxSize().background(c.background).systemBarsPadding().padding(horizontal = 2.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedButton(onClick = { full = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Exit full screen", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (g.placing) {
                            SeaBoard(
                                camera, meshes, ships = g.you, shots = emptyList(), reveal = true, fog = false,
                                fire = false, describe = { ownSquare(g, it) },
                                place = if (playable) g else null,
                                onPlace = { cell ->
                                    val row = cell / Battleship.SIZE
                                    val col = cell % Battleship.SIZE
                                    if (g.canPlace(row, col)) vm.play { it.place(row, col) }
                                    else vm.say("Ships have to stay on the board without overlapping.")
                                },
                                controls = true,
                            )
                        } else {
                            val sunk = g.enemy.filter { ship -> ship.cells.all { cell -> g.salvo.any { it.cell == cell && it.mark != Mark.MISS } } }
                            SeaBoard(
                                camera, meshes, ships = sunk, shots = g.salvo, reveal = false, fog = false,
                                fire = g.yourTurn && playable, describe = { enemySquare(g, it) },
                                onFire = { cell -> vm.play { it.fire(cell) } },
                                controls = true,
                            )
                        }
                    }
                }
            }
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
private fun rememberFleetMeshes(): FleetMeshes {
    val context = LocalContext.current
    return remember {
        fun load(name: String) = decodeShipMesh(context.assets.open(name).use { it.readBytes() })
        FleetMeshes(ShipKind.entries.associateWith { load(shipMeshAsset(it)) }, load(MissPinAsset), load(HitPinAsset))
    }
}

@Composable
private fun SeaHeading(title: String, full: Boolean, onFull: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val label = if (full) "Exit full screen" else "Full screen"
        IconButton(onClick = onFull, modifier = Modifier.size(48.dp).semantics { contentDescription = say(label) }) {
            Icon(GameIcons.Fit, contentDescription = null, tint = LocalGameLook.current.colors.text)
        }
    }
}

@Composable
private fun SeaBoard(
    camera: BoardCamera,
    meshes: FleetMeshes,
    ships: List<Ship>,
    shots: List<Shot>,
    reveal: Boolean,
    fog: Boolean,
    fire: Boolean,
    describe: (Int) -> String,
    onFire: (Int) -> Unit = {},
    place: Battleship? = null,
    onPlace: (Int) -> Unit = {},
    height: Dp? = null,
    controls: Boolean = true,
) {
    val labels = rememberTextMeasurer()
    var ghost by remember { mutableIntStateOf(-1) }
    val placeAt = rememberUpdatedState(onPlace)
    val fireAt = rememberUpdatedState(onFire)
    val placing = rememberUpdatedState(place)
    val firing = rememberUpdatedState(fire)
    val free = rememberUpdatedState(camera.free)
    BoardWithViews(camera, Battleship.SIZE, peakZ = Peak, margin = 0.62f, height = height, controls = controls) { frame ->
        val frameNow = rememberUpdatedState(frame)
        Canvas(
            Modifier.fillMaxSize().pointerInput(place != null, fire) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = viewConfiguration.touchSlop
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (event.changes.count { it.pressed } > 1 || free.value) {
                            ghost = -1
                            break
                        }
                        val at = cellAt(frameNow.value, change.position)
                        if (placing.value != null) ghost = at ?: -1
                        if (!change.pressed) {
                            ghost = -1
                            if (at != null && placing.value != null) placeAt.value(at)
                            else if (at != null && firing.value && !moved) fireAt.value(at)
                            break
                        }
                        if (hypot(change.position.x - down.position.x, change.position.y - down.position.y) > slop) {
                            moved = true
                            change.consume()
                        }
                    }
                }
            },
        ) {
            val preview = placing.value?.let { game -> if (ghost >= 0) previewOf(game, ghost) else null }
            drawOcean(frame, labels, ships, shots, meshes, reveal, fog, preview)
        }
        for (row in 0 until Battleship.SIZE) for (col in 0 until Battleship.SIZE) {
            val index = row * Battleship.SIZE + col
            val at = frame.at(col + 0.5f, row + 0.5f, Top)
            val aimed = fire && shots.none { it.cell == index }
            Box(
                Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(1.dp).semantics {
                    contentDescription = say(describe(index))
                    if (place != null || aimed) {
                        role = Role.Button
                        onClick(say(if (place != null) "Place" else "Fire")) {
                            if (place != null) onPlace(index) else onFire(index)
                            true
                        }
                    }
                },
            )
        }
    }
}

private class Ghost(val kind: ShipKind, val row: Int, val col: Int, val horizontal: Boolean, val cells: Set<Int>, val ok: Boolean)

private fun DrawScope.drawOcean(
    frame: TableFrame,
    labels: TextMeasurer,
    ships: List<Ship>,
    shots: List<Shot>,
    meshes: FleetMeshes,
    reveal: Boolean,
    fog: Boolean,
    preview: Ghost?,
) {
    drawBoardFrame(frame, Top, Tray, border = 0.46f)
    val previewCells = preview?.cells ?: emptySet()
    val previewOk = preview?.ok == true
    for (row in 0 until Battleship.SIZE) for (col in 0 until Battleship.SIZE) {
        val index = row * Battleship.SIZE + col
        var color = if ((row + col) % 2 == 0) Water else WaterDeep
        if (index in previewCells) color = lerp(color, if (previewOk) PreviewOk else PreviewBad, 0.55f)
        drawSquareTop(frame, col, row, Top, color)
    }
    for (col in 0 until Battleship.SIZE) drawBoardLabel(labels, frame, "${'A' + col}", col + 0.5f, -0.38f, Top, LabelInk)
    for (row in 0 until Battleship.SIZE) drawBoardLabel(labels, frame, "${row + 1}", -0.4f, row + 0.5f, Top, LabelInk)
    if (fog) {
        veil(frame, Fog.copy(alpha = 0.78f))
        return
    }
    val hit = shots.filter { it.mark != Mark.MISS }.map { it.cell }.toSet()
    val shown = if (reveal) ships else ships.filter { ship -> ship.cells.all { it in hit } }
    val pieces = ArrayList<Pair<Float, () -> Unit>>()
    shown.forEach { ship ->
        val sunk = ship.cells.all { it in hit }
        val mesh = meshes.ships.getValue(ship.kind)
        val depth = frame.depth(shipCenterX(ship), shipCenterY(ship), Top + 0.3f)
        pieces += depth to {
            drawMesh(frame, mesh, shipPose(ship), if (sunk) SunkHull else Hull, Polished)
        }
    }
    preview?.let { ghost ->
        if (ghost.cells.size == ghost.kind.length) {
            val ship = Ship(ghost.kind, ghost.row, ghost.col, ghost.horizontal)
            pieces += frame.depth(shipCenterX(ship), shipCenterY(ship), Top + 0.3f) to {
                drawMesh(frame, meshes.ships.getValue(ghost.kind), shipPose(ship), if (ghost.ok) PreviewOk else PreviewBad, Polished, alpha = 0.62f)
            }
        }
    }
    val covered = shown.flatMap { it.cells }.toSet()
    shots.forEach { shot ->
        val col = shot.cell % Battleship.SIZE
        val row = shot.cell / Battleship.SIZE
        val onShip = shot.cell in covered && shot.mark != Mark.MISS
        val z = Top + if (onShip) 0.42f else 0.02f
        val mesh = if (shot.mark == Mark.MISS) meshes.miss else meshes.hit
        val color = if (shot.mark == Mark.MISS) MissInk else HitRed
        pieces += frame.depth(col + 0.5f, row + 0.5f, z) to {
            drawMesh(frame, mesh, Pose(col + 0.5f, row + 0.5f, z), color, Polished)
        }
    }
    pieces.sortedByDescending { it.first }.forEach { it.second() }
}

private fun shipPose(ship: Ship): Pose {
    val horizontal = ship.horizontal
    val x = if (horizontal) ship.col.toFloat() else ship.col + 0.5f
    val y = if (horizontal) ship.row + 0.5f else ship.row.toFloat()
    return Pose(x, y, Top, fx = if (horizontal) 1f else 0f, fy = if (horizontal) 0f else 1f)
}

private fun shipCenterX(ship: Ship) = if (ship.horizontal) ship.col + ship.kind.length / 2f else ship.col + 0.5f

private fun shipCenterY(ship: Ship) = if (ship.horizontal) ship.row + 0.5f else ship.row + ship.kind.length / 2f

private fun DrawScope.veil(frame: TableFrame, color: Color) {
    val z = Top + 0.05f
    val n = Battleship.SIZE.toFloat()
    val corners = listOf(frame.at(0f, 0f, z), frame.at(n, 0f, z), frame.at(n, n, z), frame.at(0f, n, z))
    val path = Path()
    path.moveTo(corners[0].x, corners[0].y)
    for (i in 1..3) path.lineTo(corners[i].x, corners[i].y)
    path.close()
    drawPath(path, color)
}

private fun cellAt(frame: TableFrame, pos: Offset): Int? {
    var best = -1
    var bestDepth = Float.POSITIVE_INFINITY
    for (row in 0 until Battleship.SIZE) for (col in 0 until Battleship.SIZE) {
        val unit = frame.unitAt(col + 0.5f, row + 0.5f, Top)
        if (!coversCell(frame, pos, col, row, Top, Top + 0.7f, unit * 0.48f)) continue
        val depth = frame.depth(col + 0.5f, row + 0.5f, Top)
        if (depth < bestDepth) {
            bestDepth = depth
            best = row * Battleship.SIZE + col
        }
    }
    return best.takeIf { it >= 0 }
}

private fun previewOf(g: Battleship, bow: Int): Ghost? {
    val kind = g.nextKind ?: return null
    val row = bow / Battleship.SIZE
    val col = bow % Battleship.SIZE
    if (!Battleship.onBoard(kind, row, col, g.horizontal)) return Ghost(kind, row, col, g.horizontal, setOf(bow), false)
    return Ghost(kind, row, col, g.horizontal, Battleship.footprint(kind, row, col, g.horizontal), g.canPlace(row, col))
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
