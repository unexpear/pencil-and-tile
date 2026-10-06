package com.simplegamegen.sudoku.ui.assets

import com.simplegamegen.sudoku.duels.ShipKind
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hulls and pegs adapted from Original Battleship Pieces by MZimb (CC BY),
 * Thingiverse thing 5190846. Measured along the hull, that pack is a 5, a 4,
 * two 3s and a 2, which is this game's fleet:
 *
 * | Game ship   | Squares | Source file        |
 * |-------------|---------|--------------------|
 * | Carrier     | 5       | Aircraft_Carrier   |
 * | Battleship  | 4       | Cruiser            |
 * | Cruiser     | 3       | Destroyer          |
 * | Submarine   | 3       | Sub                |
 * | Destroyer   | 2       | Mine_Sweeper       |
 *
 * The shorter pin is a miss; the taller pin is a hit. Each mesh is in squares,
 * with the bow at x = 0 and the hull sitting on z = 0.
 */
internal fun shipMeshAsset(kind: ShipKind): String = when (kind) {
    ShipKind.CARRIER -> "models/battleship/carrier.bmesh"
    ShipKind.BATTLESHIP -> "models/battleship/battleship.bmesh"
    ShipKind.CRUISER -> "models/battleship/cruiser.bmesh"
    ShipKind.SUBMARINE -> "models/battleship/submarine.bmesh"
    ShipKind.DESTROYER -> "models/battleship/destroyer.bmesh"
}

internal const val MissPinAsset = "models/battleship/pin_miss.bmesh"
internal const val HitPinAsset = "models/battleship/pin_hit.bmesh"

/** Little-endian BSM1: vertex count, triangle count, positions, normals, indices. */
internal fun decodeShipMesh(bytes: ByteArray): Mesh {
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val magic = ByteArray(4)
    buf.get(magic)
    require(magic.contentEquals(byteArrayOf('B'.code.toByte(), 'S'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte()))) { "not a ship mesh" }
    val verts = buf.short.toInt() and 0xFFFF
    val tris = buf.short.toInt() and 0xFFFF
    require(verts > 0 && tris > 0)
    val pos = FloatArray(verts * 3) { buf.float }
    val nrm = FloatArray(verts * 3) { buf.float }
    val idx = ShortArray(tris * 3) { buf.short }
    require(buf.remaining() == 0)
    return Mesh(pos, nrm, idx)
}
