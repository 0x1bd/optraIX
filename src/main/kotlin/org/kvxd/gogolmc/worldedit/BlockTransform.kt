package org.kvxd.gogolmc.worldedit

import org.kvxd.gogolmc.block.BlockKind
import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.FlipDirection
import org.kvxd.gogolmc.block.property.WireSide

object BlockTransform {

    fun rotate90(state: Int): Int {
        val type = Blocks.typeOf(state)
        if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) {
            return BlockStates.wireState(
                north = wireSide(state, WireDirection.West),
                south = wireSide(state, WireDirection.East),
                east = wireSide(state, WireDirection.North),
                west = wireSide(state, WireDirection.South),
                power = BlockStates.wirePower[state].toInt(),
            )
        }
        var result = state
        type.property("facing")?.let { property ->
            result = type.withValue(result, property, rotateFacingName(type.value(state, property)))
        }
        type.property("axis")?.let { property ->
            val value = type.value(state, property)
            val rotated = when (value) {
                "x" -> "z"
                "z" -> "x"
                else -> value
            }
            result = type.withValue(result, property, rotated)
        }
        type.property("rotation")?.let { property ->
            val value = type.value(state, property).toInt()
            result = type.withValue(result, property, ((value + 4) and 15).toString())
        }
        return result
    }

    fun flip(state: Int, direction: FlipDirection): Int {
        val type = Blocks.typeOf(state)
        if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) {
            val power = BlockStates.wirePower[state].toInt()
            return if (direction == FlipDirection.FlipX) {
                BlockStates.wireState(
                    north = wireSide(state, WireDirection.North),
                    south = wireSide(state, WireDirection.South),
                    east = wireSide(state, WireDirection.West),
                    west = wireSide(state, WireDirection.East),
                    power = power,
                )
            } else {
                BlockStates.wireState(
                    north = wireSide(state, WireDirection.South),
                    south = wireSide(state, WireDirection.North),
                    east = wireSide(state, WireDirection.East),
                    west = wireSide(state, WireDirection.West),
                    power = power,
                )
            }
        }
        var result = state
        type.property("facing")?.let { property ->
            val value = type.value(state, property)
            val flipped = when (direction) {
                FlipDirection.FlipX -> when (value) {
                    "east" -> "west"
                    "west" -> "east"
                    else -> value
                }
                FlipDirection.FlipZ -> when (value) {
                    "north" -> "south"
                    "south" -> "north"
                    else -> value
                }
            }
            result = type.withValue(result, property, flipped)
        }
        type.property("rotation")?.let { property ->
            val value = type.value(state, property).toInt()
            val flipped = when (direction) {
                FlipDirection.FlipX -> (24 - value) and 15
                FlipDirection.FlipZ -> (16 - value) and 15
            }
            result = type.withValue(result, property, flipped.toString())
        }
        return result
    }

    private enum class WireDirection { North, South, East, West }

    private fun wireSide(state: Int, direction: WireDirection): WireSide = when (direction) {
        WireDirection.North -> WireSide.entries[BlockStates.wireNorth[state].toInt()]
        WireDirection.South -> WireSide.entries[BlockStates.wireSouth[state].toInt()]
        WireDirection.East -> WireSide.entries[BlockStates.wireEast[state].toInt()]
        WireDirection.West -> WireSide.entries[BlockStates.wireWest[state].toInt()]
    }

    private fun rotateFacingName(value: String): String = when (value) {
        "north" -> "east"
        "east" -> "south"
        "south" -> "west"
        "west" -> "north"
        else -> value
    }
}
