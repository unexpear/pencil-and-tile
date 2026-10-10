package com.simplegamegen.sudoku.ui.screens
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

@Composable
internal fun rememberFleetMeshes(): FleetMeshes {
    val context = LocalContext.current
    return remember {
        fun load(name: String) = decodeShipMesh(context.assets.open(name).use { it.readBytes() })
        FleetMeshes(ShipKind.entries.associateWith { load(shipMeshAsset(it)) }, load(MissPinAsset), load(HitPinAsset))
    }
}

@Composable
internal fun SeaHeading(title: String, full: Boolean, onFull: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val label = if (full) "Exit full screen" else "Full screen"
        IconButton(onClick = onFull, modifier = Modifier.size(48.dp).semantics { contentDescription = say(label) }) {
            Icon(GameIcons.Fit, contentDescription = null, tint = LocalGameLook.current.colors.text)
        }
    }
}

@Composable
internal fun SeaBoard(
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
    val ghostState = remember { mutableIntStateOf(-1) }
    val ghost = ghostState.intValue
    val placeAt = rememberUpdatedState(onPlace)
    val fireAt = rememberUpdatedState(onFire)
    val placing = rememberUpdatedState(place)
    val firing = rememberUpdatedState(fire)
    val free = rememberUpdatedState(camera.free)
    BoardWithViews(camera, Battleship.SIZE, peakZ = Peak, margin = 0.62f, height = height, controls = controls, fitToView = true) { frame ->
        val frameNow = rememberUpdatedState(frame)
        Canvas(
            Modifier.fillMaxSize().pointerInput(place != null, fire) {
                trackSea(frameNow, placing, firing, free, placeAt, fireAt, ghostState)
            },
        ) {
            val preview = placing.value?.let { game -> if (ghost >= 0) previewOf(game, ghost) else null }
            drawOcean(frame, labels, ships, shots, meshes, reveal, fog, preview)
        }
        val density = LocalDensity.current
        for (row in 0 until Battleship.SIZE) for (col in 0 until Battleship.SIZE) {
            val index = row * Battleship.SIZE + col
            val at = frame.at(col + 0.5f, row + 0.5f, Top)
            val aimed = fire && shots.none { it.cell == index }
            val description = say(describe(index))
            var mark = with(density) { Modifier.offset(x = at.x.toDp(), y = at.y.toDp()) }
                .size(1.dp)
                .semantics { contentDescription = description }
            if (place != null || aimed) {
                mark = mark.clickable(role = Role.Button, onClickLabel = say(if (place != null) "Place" else "Fire")) {
                    if (place != null) onPlace(index) else onFire(index)
                }
            }
            Box(mark)
        }
    }
}

private suspend fun PointerInputScope.trackSea(
    frame: State<TableFrame>,
    placing: State<Battleship?>,
    firing: State<Boolean>,
    freeCam: State<Boolean>,
    placeAt: State<(Int) -> Unit>,
    fireAt: State<(Int) -> Unit>,
    ghost: MutableIntState,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (event.changes.count { it.pressed } > 1 || freeCam.value) {
                ghost.intValue = -1
                break
            }
            val at = cellAt(frame.value, change.position)
            if (placing.value != null) ghost.intValue = at ?: -1
            if (!change.pressed) {
                ghost.intValue = -1
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
    for (row in 0 until Battleship.SIZE) {
        val label = "${row + 1}"
        // "10" is wider than the frame. Sit it closer to the squares so the near edge does not clip it.
        val x = if (label.length > 1) -0.12f else -0.4f
        drawBoardLabel(labels, frame, label, x, row + 0.5f, Top, LabelInk)
    }
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

internal fun located(cell: Int, detail: String) =
    "Row ${cell / Battleship.SIZE + 1}, column ${'A' + cell % Battleship.SIZE}, $detail"

internal fun ownSquare(g: Battleship, cell: Int): String {
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

internal fun enemySquare(g: Battleship, cell: Int): String {
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
