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

internal val Water = Color(0xFF1E88E5)
internal val WaterDeep = Color(0xFF1565C0)
internal val Tray = Color(0xFF0E3A5D)
internal val Fog = Color(0xFF08243F)
internal val Hull = Color(0xFFECEFF1)
internal val SunkHull = Color(0xFF8D6E63)
internal val HitRed = Color(0xFFE53935)
internal val MissInk = Color(0xFFF7F4EF)
internal val PreviewOk = Color(0xFF43A047)
internal val PreviewBad = Color(0xFFEF5350)
internal val LabelInk = Color(0xFFF7F4EF)

internal const val Top = 0.22f
internal const val Peak = 1.15f

private val SeaBehind = BoardView("Behind", yaw = 0f, pitch = 68f, distance = 1.38f)
private val SeaCorner = BoardView("Corner", yaw = 28f, pitch = 60f, distance = 1.5f)
private val SeaTop = BoardView("Top", yaw = 0f, pitch = 90f, distance = 1.68f)

internal val PlaceLines = listOf(
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

internal class FleetMeshes(val ships: Map<ShipKind, Mesh>, val miss: Mesh, val hit: Mesh)

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
        BattleshipColumns(
            g, s, c, vm, setup, camera, meshes, full,
            onDifficulty = { picked = it; difficulty = true },
            setFull = { full = it },
        )
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
