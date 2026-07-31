package org.kvxd.gogolmc.worldedit

import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.BlockFacing

object Directions {

    fun parse(text: String, playerYaw: Float, playerPitch: Float): BlockFacing? = when (text.lowercase()) {
        "n", "north" -> BlockFacing.North
        "s", "south" -> BlockFacing.South
        "e", "east" -> BlockFacing.East
        "w", "west" -> BlockFacing.West
        "u", "up" -> BlockFacing.Up
        "d", "down" -> BlockFacing.Down
        "me", "forward", "f" -> facing(playerYaw, playerPitch)
        "back", "b" -> opposite(facing(playerYaw, playerPitch))
        else -> null
    }

    fun facing(yaw: Float, pitch: Float): BlockFacing = when {
        pitch <= -70.0f -> BlockFacing.Up
        pitch >= 70.0f -> BlockFacing.Down
        else -> horizontal(yaw).blockFacing()
    }

    fun horizontal(yaw: Float): BlockDirection =
        when (Math.floorMod(Math.floor(yaw / 90.0 + 0.5).toInt(), 4)) {
            0 -> BlockDirection.South
            1 -> BlockDirection.West
            2 -> BlockDirection.North
            else -> BlockDirection.East
        }

    fun opposite(facing: BlockFacing): BlockFacing = when (facing) {
        BlockFacing.North -> BlockFacing.South
        BlockFacing.South -> BlockFacing.North
        BlockFacing.East -> BlockFacing.West
        BlockFacing.West -> BlockFacing.East
        BlockFacing.Up -> BlockFacing.Down
        BlockFacing.Down -> BlockFacing.Up
    }
}
