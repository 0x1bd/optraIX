package org.kvxd.gogolmc.redstone.mchprs

import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.BlockFace
import org.kvxd.gogolmc.block.BlockKind
import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.property.WireSide
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.World

object Wire {

    fun north(state: Int): WireSide = WireSide.entries[BlockStates.wireNorth[state].toInt()]

    fun south(state: Int): WireSide = WireSide.entries[BlockStates.wireSouth[state].toInt()]

    fun east(state: Int): WireSide = WireSide.entries[BlockStates.wireEast[state].toInt()]

    fun west(state: Int): WireSide = WireSide.entries[BlockStates.wireWest[state].toInt()]

    fun power(state: Int): Int = BlockStates.wirePower[state].toInt()

    fun make(north: WireSide, south: WireSide, east: WireSide, west: WireSide, power: Int): Int =
        BlockStates.wireState(north, south, east, west, power)

    fun makeCross(power: Int): Int =
        make(WireSide.Side, WireSide.Side, WireSide.Side, WireSide.Side, power)

    fun getStateForPlacement(world: World, pos: BlockPos): Int {
        var wire = make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, calculatePower(world, pos))
        wire = getRegulatedSides(wire, world, pos)
        if (isDot(wire)) wire = makeCross(power(wire))
        return wire
    }

    fun onNeighborChanged(state: Int, world: World, pos: BlockPos, side: BlockFace): Int {
        val oldState = state
        var wire = state
        val newSide: WireSide
        when (side) {
            BlockFace.Top -> return wire
            BlockFace.Bottom -> return getRegulatedSides(wire, world, pos)
            BlockFace.North -> {
                val value = getSide(world, pos, BlockDirection.South)
                wire = make(north(wire), value, east(wire), west(wire), power(wire))
                newSide = value
            }
            BlockFace.South -> {
                val value = getSide(world, pos, BlockDirection.North)
                wire = make(value, south(wire), east(wire), west(wire), power(wire))
                newSide = value
            }
            BlockFace.East -> {
                val value = getSide(world, pos, BlockDirection.West)
                wire = make(north(wire), south(wire), east(wire), value, power(wire))
                newSide = value
            }
            BlockFace.West -> {
                val value = getSide(world, pos, BlockDirection.East)
                wire = make(north(wire), south(wire), value, west(wire), power(wire))
                newSide = value
            }
        }
        wire = getRegulatedSides(wire, world, pos)
        if (isCross(oldState) && newSide.isNone) return oldState
        if (!isDot(oldState) && isDot(wire)) wire = makeCross(power(wire))
        return wire
    }

    fun onNeighborUpdated(state: Int, world: World, pos: BlockPos) {
        val newPower = calculatePower(world, pos)
        if (power(state) != newPower) {
            world.setBlock(pos, BlockStates.wireWithPower(state, newPower))
            MchprsRedstone.stats.wireUpdates++
            WireTurbo.updateSurroundingNeighbors(world, pos)
        }
    }

    fun canConnectTo(state: Int, side: BlockDirection): Boolean {
        if (BlockStates.pressurePlatePowered(state) != null) return true

        return when (BlockStates.kindOf(state)) {
            BlockKind.RedstoneWire,
            BlockKind.Comparator,
            BlockKind.RedstoneTorch,
            BlockKind.RedstoneBlock,
            BlockKind.RedstoneWallTorch,
            BlockKind.TripwireHook,
            BlockKind.StoneButton,
            BlockKind.Target,
            BlockKind.Lever -> true
            BlockKind.Repeater -> {
                val facing = BlockStates.directionOf(state)
                facing == side || facing == side.opposite()
            }
            BlockKind.Observer -> BlockStates.facingOf(state) == side.blockFacing()
            else -> false
        }
    }

    private fun canConnectDiagonalTo(state: Int): Boolean =
        BlockStates.kindOf(state) == BlockKind.RedstoneWire

    fun getCurrentSide(state: Int, side: BlockDirection): WireSide = when (side) {
        BlockDirection.North -> north(state)
        BlockDirection.South -> south(state)
        BlockDirection.East -> east(state)
        BlockDirection.West -> west(state)
    }

    fun getSide(world: World, pos: BlockPos, side: BlockDirection): WireSide {
        val neighborPos = pos.offset(side.blockFace())
        val neighbor = world.getBlock(neighborPos)

        if (canConnectTo(neighbor, side)) return WireSide.Side

        val upPos = pos.offset(BlockFace.Top)
        val up = world.getBlock(upPos)

        return if (!BlockStates.isSolid(up) &&
            canConnectDiagonalTo(world.getBlock(neighborPos.offset(BlockFace.Top)))
        ) {
            WireSide.Up
        } else if (!BlockStates.isSolid(neighbor) &&
            canConnectDiagonalTo(world.getBlock(neighborPos.offset(BlockFace.Bottom)))
        ) {
            WireSide.Side
        } else {
            WireSide.None
        }
    }

    private fun getAllSides(state: Int, world: World, pos: BlockPos): Int = make(
        getSide(world, pos, BlockDirection.North),
        getSide(world, pos, BlockDirection.South),
        getSide(world, pos, BlockDirection.East),
        getSide(world, pos, BlockDirection.West),
        power(state),
    )

    fun getRegulatedSides(state: Int, world: World, pos: BlockPos): Int {
        val all = getAllSides(state, world, pos)
        if (isDot(state) && isDot(all)) return all

        var north = north(all)
        var south = south(all)
        var east = east(all)
        var west = west(all)

        val northNone = north.isNone
        val southNone = south.isNone
        val eastNone = east.isNone
        val westNone = west.isNone
        val northSouthNone = northNone && southNone
        val eastWestNone = eastNone && westNone

        if (northNone && eastWestNone) north = WireSide.Side
        if (southNone && eastWestNone) south = WireSide.Side
        if (eastNone && northSouthNone) east = WireSide.Side
        if (westNone && northSouthNone) west = WireSide.Side

        return make(north, south, east, west, power(all))
    }

    fun isDot(state: Int): Boolean =
        north(state).isNone && south(state).isNone && east(state).isNone && west(state).isNone

    fun isCross(state: Int): Boolean =
        north(state) == WireSide.Side && south(state) == WireSide.Side &&
            east(state) == WireSide.Side && west(state) == WireSide.Side

    private fun maxWirePower(wirePower: Int, world: World, pos: BlockPos): Int {
        val state = world.getBlock(pos)
        return if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) {
            maxOf(wirePower, power(state))
        } else {
            wirePower
        }
    }

    fun calculatePower(world: World, pos: BlockPos): Int {
        var blockPower = 0
        var wirePower = 0

        val upPos = pos.offset(BlockFace.Top)
        val upBlock = world.getBlock(upPos)

        for (side in BlockFace.All) {
            val neighborPos = pos.offset(side)
            wirePower = maxWirePower(wirePower, world, neighborPos)
            val neighbor = world.getBlock(neighborPos)
            blockPower = maxOf(
                blockPower,
                MchprsRedstone.getRedstonePowerNoDust(neighbor, world, neighborPos, side),
            )
            if (side.isHorizontal) {
                if (!BlockStates.isSolid(upBlock) && !BlockStates.isTransparent(neighbor)) {
                    wirePower = maxWirePower(wirePower, world, neighborPos.offset(BlockFace.Top))
                }
                if (!BlockStates.isSolid(neighbor)) {
                    wirePower = maxWirePower(wirePower, world, neighborPos.offset(BlockFace.Bottom))
                }
            }
        }

        return maxOf(blockPower, maxOf(0, wirePower - 1))
    }
}
