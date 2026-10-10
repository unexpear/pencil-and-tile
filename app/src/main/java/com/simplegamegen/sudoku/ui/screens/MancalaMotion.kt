package com.simplegamegen.sudoku.ui.screens

import com.simplegamegen.sudoku.duels.Mancala
import kotlin.math.PI
import kotlin.math.sin

internal data class StonePose(val x: Float, val y: Float, val z: Float, val tint: Int)

/**
 * One marble. It travels from the start to the landing, then, when a capture or the
 * end-of-game sweep moves it again, from the landing to the store.
 */
internal class StoneFlight(
    val x0: Float,
    val y0: Float,
    val z0: Float,
    val x1: Float,
    val y1: Float,
    val z1: Float,
    val x2: Float,
    val y2: Float,
    val z2: Float,
    val carriesOn: Boolean,
    val tint: Int,
    val start: Float,
    val land: Float,
    val end: Float,
)

internal class StoneAnim(val flights: List<StoneFlight>, val durationMs: Long) {
    fun sample(t: Float): List<StonePose> = flights.map { flight ->
        val spot = flight.at(t)
        StonePose(spot.x, spot.y, spot.z, flight.tint)
    }
}

/**
 * Visual of [before.sow] ending at [after]. Null when [after] is not one sow
 * (a new game, or undo), so the board snaps instead of inventing a path.
 * The rules themselves stay in [Mancala.sow].
 */
internal fun animateSow(before: Mancala, after: Mancala): StoneAnim? {
    val source = before.legalPits().firstOrNull { before.sow(it) == after } ?: return null
    val drops = dropTargets(before, source)
    val leaving = stonesIn(MancalaBoard.hole(source), before.pits[source])
    if (leaving.size != drops.size) return null
    val topFirst = leaving.asReversed()
    val mid = before.pits.toIntArray()
    mid[source] = 0
    val landed = ArrayList<StoneSpot>(drops.size)
    for (dest in drops) {
        mid[dest]++
        landed.add(stonesIn(MancalaBoard.hole(dest), mid[dest]).last())
    }
    val hand = drops.size
    val sowEnd = 0.74f
    data class Body(
        val spot: StoneSpot,
        val tint: Int,
        var flight: StoneFlight?,
    )
    val bodies = HashMap<Int, MutableList<Body>>()
    for (hole in MancalaBoard.holes) {
        val pile = if (hole.index == source) emptyList() else stonesIn(hole, before.pits[hole.index])
        bodies[hole.index] = pile.mapIndexed { slot, spot ->
            Body(spot, marbleTint(hole.index, slot), null)
        }.toMutableList()
    }
    topFirst.forEachIndexed { i, from ->
        val dest = drops[i]
        val to = landed[i]
        val start = if (hand <= 1) 0f else i / (hand - 1f) * sowEnd * 0.55f
        val land = (start + (sowEnd / hand).coerceAtLeast(0.16f)).coerceAtMost(sowEnd)
        val tint = marbleTint(source, leaving.size - 1 - i)
        val flight = StoneFlight(
            from.x, from.y, from.z, to.x, to.y, to.z, to.x, to.y, to.z,
            carriesOn = false, tint = tint, start = start, land = land, end = land,
        )
        bodies.getValue(dest).add(Body(to, tint, flight))
    }
    val captured = !mid.contentEquals(after.pits.toIntArray())
    if (captured) {
        val homeless = ArrayList<Body>()
        for (hole in MancalaBoard.holes) {
            val have = bodies.getValue(hole.index)
            val need = after.pits[hole.index]
            if (have.size > need) {
                homeless.addAll(have.subList(need, have.size))
                bodies[hole.index] = have.subList(0, need).toMutableList()
            }
        }
        for (hole in MancalaBoard.holes) {
            val have = bodies.getValue(hole.index)
            val needSpots = stonesIn(hole, after.pits[hole.index])
            for (spot in needSpots.drop(have.size)) {
                if (homeless.isEmpty()) return null
                val body = homeless.removeAt(homeless.lastIndex)
                val from = body.flight?.let { StoneSpot(it.x1, it.y1, it.z1) } ?: body.spot
                val origin = body.flight
                have.add(
                    Body(
                        spot,
                        body.tint,
                        StoneFlight(
                            x0 = origin?.x0 ?: from.x,
                            y0 = origin?.y0 ?: from.y,
                            z0 = origin?.z0 ?: from.z,
                            x1 = from.x,
                            y1 = from.y,
                            z1 = from.z,
                            x2 = spot.x,
                            y2 = spot.y,
                            z2 = spot.z,
                            carriesOn = true,
                            tint = body.tint,
                            start = origin?.start ?: 0.78f,
                            land = origin?.land ?: 0.78f,
                            end = 1f,
                        ),
                    ),
                )
            }
        }
        if (homeless.isNotEmpty()) return null
    }
    val flights = ArrayList<StoneFlight>(Mancala.STONES)
    for (hole in MancalaBoard.holes) {
        for (body in bodies.getValue(hole.index)) {
            val spot = body.spot
            flights.add(
                body.flight ?: StoneFlight(
                    spot.x, spot.y, spot.z, spot.x, spot.y, spot.z, spot.x, spot.y, spot.z,
                    carriesOn = false, tint = body.tint, start = 0f, land = 0f, end = 0f,
                ),
            )
        }
    }
    if (flights.size != Mancala.STONES) return null
    val duration = (hand * 48L + if (captured) 180L else 0L).coerceIn(340L, 760L)
    return StoneAnim(flights, duration)
}

/** Holes that receive a stone, in sow order. Skips the opponent's store, as [Mancala.sow] does. */
internal fun dropTargets(game: Mancala, pit: Int): List<Int> {
    var hand = game.pits[pit]
    var cursor = pit
    val skip = if (game.turn == 1) Mancala.CPU else Mancala.YOU
    val out = ArrayList<Int>(hand)
    while (hand > 0) {
        cursor = (cursor + 1) % Mancala.HOLES
        if (cursor == skip) continue
        out.add(cursor)
        hand--
    }
    return out
}

private fun StoneFlight.at(t: Float): StoneSpot {
    if (land > start && t < land) {
        if (t <= start) return StoneSpot(x0, y0, z0)
        val u = smooth((t - start) / (land - start))
        return StoneSpot(
            x0 + (x1 - x0) * u,
            y0 + (y1 - y0) * u,
            z0 + (z1 - z0) * u + sin(PI.toFloat() * u) * 0.46f,
        )
    }
    if (carriesOn && end > land && t < end) {
        if (t <= land) return StoneSpot(x1, y1, z1)
        val u = smooth((t - land) / (end - land))
        return StoneSpot(
            x1 + (x2 - x1) * u,
            y1 + (y2 - y1) * u,
            z1 + (z2 - z1) * u + sin(PI.toFloat() * u) * 0.28f,
        )
    }
    return if (carriesOn) StoneSpot(x2, y2, z2) else StoneSpot(x1, y1, z1)
}

private fun smooth(u: Float): Float {
    val t = u.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
