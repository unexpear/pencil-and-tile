package com.simplegamegen.sudoku.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.simplegamegen.sudoku.duels.Battleship
import com.simplegamegen.sudoku.duels.Mark
import com.simplegamegen.sudoku.duels.ShipKind
import com.simplegamegen.sudoku.ui.PlaySession
import com.simplegamegen.sudoku.ui.PlayViewModel
import com.simplegamegen.sudoku.ui.components.InfoChip
import com.simplegamegen.sudoku.ui.i18n.Text
import com.simplegamegen.sudoku.ui.theme.GamePalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.BattleshipColumns(
    g: Battleship,
    s: PlaySession<Battleship>,
    c: GamePalette,
    vm: PlayViewModel<Battleship>,
    setup: PlaySetup<Battleship>,
    camera: BoardCamera,
    meshes: FleetMeshes,
    full: Boolean,
    onDifficulty: (Int) -> Unit,
    setFull: (Boolean) -> Unit,
) {
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
            OutlinedButton(onClick = { onDifficulty(g.setting) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Change difficulty") }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = maxWidth.coerceAtMost(560.dp)
        // BoxWithConstraints is a Box, so these have to sit in a column. As direct children
        // they stacked in the top left and the sea covered the placement text.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            SeaHeading("Your fleet", full) { setFull(!full) }
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
            SeaHeading("Enemy waters", full) { setFull(!full) }
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
    }
    if (full) {
        Dialog(onDismissRequest = { setFull(false) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Column(
                Modifier.fillMaxSize().background(c.background).systemBarsPadding().padding(horizontal = 2.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = { setFull(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
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
    }}
