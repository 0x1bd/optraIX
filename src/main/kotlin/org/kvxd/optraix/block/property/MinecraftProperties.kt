package org.kvxd.optraix.block.property

import org.kvxd.optraix.mcdata.v1_20_4.Face
import org.kvxd.optraix.mcdata.v1_20_4.Facing
import org.kvxd.optraix.mcdata.v1_20_4.Facing2
import org.kvxd.optraix.mcdata.v1_20_4.Facing3
import org.kvxd.optraix.mcdata.v1_20_4.Half
import org.kvxd.optraix.mcdata.v1_20_4.Mode
import org.kvxd.optraix.mcdata.v1_20_4.North2
import org.kvxd.optraix.mcdata.v1_20_4.Type3

typealias BlockDirection = Facing
typealias BlockFacing = Facing3
typealias ComparatorMode = Mode
typealias HopperFacing = Facing2
typealias Instrument = org.kvxd.optraix.mcdata.v1_20_4.Instrument
typealias LeverFace = Face
typealias SlabType = Type3
typealias TrapdoorHalf = Half
typealias WireSide = North2

fun BlockDirection.opposite(): BlockDirection = when (this) {
    BlockDirection.North -> BlockDirection.South
    BlockDirection.South -> BlockDirection.North
    BlockDirection.East -> BlockDirection.West
    BlockDirection.West -> BlockDirection.East
}

fun BlockDirection.blockFace(): BlockFace = when (this) {
    BlockDirection.North -> BlockFace.North
    BlockDirection.South -> BlockFace.South
    BlockDirection.East -> BlockFace.East
    BlockDirection.West -> BlockFace.West
}

fun BlockDirection.blockFacing(): BlockFacing = when (this) {
    BlockDirection.North -> BlockFacing.North
    BlockDirection.South -> BlockFacing.South
    BlockDirection.East -> BlockFacing.East
    BlockDirection.West -> BlockFacing.West
}

fun BlockDirection.rotate(): BlockDirection = when (this) {
    BlockDirection.North -> BlockDirection.East
    BlockDirection.East -> BlockDirection.South
    BlockDirection.South -> BlockDirection.West
    BlockDirection.West -> BlockDirection.North
}

fun BlockDirection.rotateCcw(): BlockDirection = when (this) {
    BlockDirection.North -> BlockDirection.West
    BlockDirection.West -> BlockDirection.South
    BlockDirection.South -> BlockDirection.East
    BlockDirection.East -> BlockDirection.North
}

fun BlockDirection.flip(axisX: Boolean): BlockDirection = when {
    axisX -> when (this) {
        BlockDirection.East -> BlockDirection.West
        BlockDirection.West -> BlockDirection.East
        else -> this
    }

    else -> when (this) {
        BlockDirection.North -> BlockDirection.South
        BlockDirection.South -> BlockDirection.North
        else -> this
    }
}

fun BlockFacing.rotate(): BlockFacing = when (this) {
    BlockFacing.North -> BlockFacing.East
    BlockFacing.East -> BlockFacing.South
    BlockFacing.South -> BlockFacing.West
    BlockFacing.West -> BlockFacing.North
    else -> this
}

fun BlockFacing.rotateCcw(): BlockFacing = when (this) {
    BlockFacing.North -> BlockFacing.West
    BlockFacing.West -> BlockFacing.South
    BlockFacing.South -> BlockFacing.East
    BlockFacing.East -> BlockFacing.North
    else -> this
}

fun BlockFacing.flip(axisX: Boolean): BlockFacing = when {
    axisX -> when (this) {
        BlockFacing.East -> BlockFacing.West
        BlockFacing.West -> BlockFacing.East
        else -> this
    }

    else -> when (this) {
        BlockFacing.North -> BlockFacing.South
        BlockFacing.South -> BlockFacing.North
        else -> this
    }
}

fun ComparatorMode.toggle(): ComparatorMode =
    if (this == ComparatorMode.Compare) ComparatorMode.Subtract else ComparatorMode.Compare

fun HopperFacing.rotate(): HopperFacing = when (this) {
    HopperFacing.North -> HopperFacing.East
    HopperFacing.East -> HopperFacing.South
    HopperFacing.South -> HopperFacing.West
    HopperFacing.West -> HopperFacing.North
    HopperFacing.Down -> HopperFacing.Down
}

fun HopperFacing.flip(axisX: Boolean): HopperFacing = when {
    axisX -> when (this) {
        HopperFacing.East -> HopperFacing.West
        HopperFacing.West -> HopperFacing.East
        else -> this
    }

    else -> when (this) {
        HopperFacing.North -> HopperFacing.South
        HopperFacing.South -> HopperFacing.North
        else -> this
    }
}

val WireSide.isNone: Boolean
    get() = this == WireSide.None
